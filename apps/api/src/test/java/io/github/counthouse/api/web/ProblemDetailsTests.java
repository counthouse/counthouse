package io.github.counthouse.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.counthouse.api.ApiTest;
import io.github.counthouse.contract.MeApi;
import io.github.counthouse.contract.model.Me;
import io.github.counthouse.contract.model.Role;
import io.github.counthouse.contract.model.Tenant;
import io.github.counthouse.contract.model.User;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

@ApiTest
@ExtendWith(OutputCaptureExtension.class)
@Import({
    ProblemDetailsTests.MeController.class,
    ProblemDetailsTests.ProbeController.class,
    ProblemDetailsTests.ProbeFilter.class
})
class ProblemDetailsTests {

    static final String SECRET = "secret-value-from-the-failure";

    @Autowired
    RestTestClient client;

    @Autowired
    MeController me;

    @Autowired
    MeterRegistry meters;

    @LocalServerPort
    int port;

    @AfterEach
    void reset() {
        me.answer(MeController::example);
    }

    @Test
    void successStaysJson() {
        client.get().uri("/me").exchange().expectStatus().isOk().expectHeader().contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void successIsNeverLabelledAsAProblem() {
        client.get()
                .uri("/me")
                .accept(MediaType.APPLICATION_PROBLEM_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.NOT_ACCEPTABLE)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void operationWithOnlyAnErrorMediaTypeAcceptsJsonClients() {
        client.get()
                .uri("/probe/no-content")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    void unknownPathIsANotFoundProblem(CapturedOutput output) {
        client.get()
                .uri("/nope")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .doesNotHaveJsonPath()
                .jsonPath("$.title")
                .isEqualTo("Not Found")
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.detail")
                .isEqualTo("No endpoint GET /nope.");
        assertThat(output.getAll()).doesNotContain("/nope");
    }

    @Test
    void htmlClientsGetAProblemToo() {
        client.get()
                .uri("/nope")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void wrongMethodIsAProblemWithAllow() {
        client.post()
                .uri("/me")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
                .expectHeader()
                .valueEquals("Allow", "GET")
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Method Not Allowed");
    }

    @Test
    void unacceptableMediaTypeIsAProblem() {
        client.get()
                .uri("/me")
                .accept(MediaType.APPLICATION_XML)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.NOT_ACCEPTABLE)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void controllerFailureIsAServerErrorWithoutItsMessage(CapturedOutput output) {
        me.answer(() -> {
            throw new IllegalStateException(SECRET);
        });

        String body = client.get()
                .uri("/me")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).doesNotContain(SECRET).contains("\"title\":\"Internal Server Error\"");
        assertThat(output.getAll().split("java.lang.IllegalStateException: " + SECRET, -1))
                .as("stack traces logged")
                .hasSize(2);
        assertThat(meters.get("http.server.requests")
                        .tag("uri", "/me")
                        .tag("status", "500")
                        .tag("exception", "IllegalStateException")
                        .timer()
                        .count())
                .isPositive();
    }

    @Test
    void filterFailureIsAServerErrorWithoutItsMessage(CapturedOutput output) {
        String body = client.get()
                .uri("/me")
                .header("X-Probe", "throw")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).doesNotContain(SECRET);
        assertThat(output.getAll().split("java.lang.IllegalStateException: " + SECRET, -1))
                .as("stack traces logged")
                .hasSize(2);
    }

    @Test
    void statusSentByAFilterKeepsItsStatus() {
        client.get()
                .uri("/me")
                .header("X-Probe", "send-error-409")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Conflict");
    }

    @Test
    void unknownStatusSentByAFilterBecomesAServerError() {
        client.get()
                .uri("/me")
                .header("X-Probe", "send-error-499")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void errorPathRequestedDirectlyIsNotFound() {
        for (HttpMethod method : new HttpMethod[] {HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE}) {
            client.method(method).uri("/error").exchange().expectStatus().isNotFound();
        }
    }

    @Test
    void actuatorErrorsAreProblems() {
        client.post()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /**
     * Requests that are not valid HTTP never reach the application: Tomcat answers them with its own 400 page,
     * which must not show the server version, the exception or Tomcat's report.
     */
    @ParameterizedTest
    @ValueSource(strings = {"GET /me%zz HTTP/1.1", "GET /me/%2F HTTP/1.1", "GET /me HTTP/1.1 junk"})
    void requestsTomcatRejectsRevealNothing(String requestLine) throws IOException {
        assertThat(raw(requestLine))
                .startsWith("HTTP/1.1 400 ")
                .doesNotContainIgnoringCase("tomcat")
                .doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("description");
    }

    @Test
    void headHasNoBody() {
        client.head()
                .uri("/nope")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .isEmpty();
    }

    private String raw(String requestLine) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write((requestLine + "\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @TestComponent
    @RestController
    static class MeController implements MeApi {

        // A field, not set from the test directly: the bean is a proxy (the interface is @Validated).
        private volatile Supplier<ResponseEntity<Me>> answer = MeController::example;

        void answer(Supplier<ResponseEntity<Me>> answer) {
            this.answer = answer;
        }

        @Override
        public ResponseEntity<Me> getMe() {
            return answer.get();
        }

        static ResponseEntity<Me> example() {
            var user = new User(UUID.fromString("019a0000-0000-7000-8000-000000000001"), Role.ADMIN);
            var tenant = new Tenant(
                    UUID.fromString("019a0000-0000-7000-8000-000000000002"), "Example", "America/Montevideo", "es");
            return ResponseEntity.ok(new Me(user, tenant));
        }
    }

    /** An operation whose only listed media type is the error one, as the generator writes a 204 operation. */
    @TestComponent
    @RestController
    static class ProbeController {

        @GetMapping(value = "/probe/no-content", produces = "application/problem+json")
        ResponseEntity<Void> noContent() {
            return ResponseEntity.noContent().build();
        }
    }

    @TestComponent
    static class ProbeFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String probe = request.getHeader("X-Probe");
            if ("throw".equals(probe)) {
                throw new IllegalStateException(SECRET);
            }
            if (probe != null && probe.startsWith("send-error-")) {
                response.sendError(Integer.parseInt(probe.substring("send-error-".length())));
                return;
            }
            chain.doFilter(request, response);
        }
    }
}

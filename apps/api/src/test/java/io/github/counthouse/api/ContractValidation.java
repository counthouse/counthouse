package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.schema.SchemaValidator;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.boot.resttestclient.autoconfigure.RestTestClientBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks every response a {@code RestTestClient} receives against the contract, whether or not the test reads the
 * body. A response of a contract operation must match that operation; any other error response must be the
 * contract's {@code Problem} with the response's status. Used through {@link ApiTest}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContractValidation {

    static final String CONTRACT =
            Path.of("../../contract/openapi.yaml").toAbsolutePath().normalize().toString();

    /** The validator's keys for a request the contract has no operation for. */
    private static final Set<String> NOT_AN_OPERATION =
            Set.of("validation.request.path.missing", "validation.request.operation.notAllowed");

    private final OpenApiInteractionValidator operations =
            OpenApiInteractionValidator.createForSpecificationUrl(CONTRACT).build();
    private final OpenAPI contract;
    private final SchemaValidator schemas;

    ContractValidation() {
        var options = new ParseOptions();
        options.setResolve(true);
        contract = new OpenAPIV3Parser().read(CONTRACT, null, options);
        schemas = new SchemaValidator(contract, new MessageResolver());
    }

    @Bean
    RestTestClientBuilderCustomizer validateEveryResponse() {
        return builder -> builder.requestInterceptor(this::exchange);
    }

    private ClientHttpResponse exchange(HttpRequest request, byte[] requestBody, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, requestBody);
        byte[] body = response.getBody().readAllBytes();
        check(request, response.getStatusCode(), response.getHeaders(), new String(body, StandardCharsets.UTF_8));
        return new Buffered(response, body);
    }

    private void check(HttpRequest request, HttpStatusCode status, HttpHeaders headers, String body) {
        String exchange = request.getMethod() + " " + request.getURI().getRawPath();
        boolean errorBody = status.isError() && request.getMethod() != HttpMethod.HEAD;
        var response = SimpleResponse.Builder.status(status.value()).withBody(body.isEmpty() ? null : body);
        headers.forEach(response::withHeader);
        ValidationReport report = operations.validateResponse(
                request.getURI().getRawPath(),
                Request.Method.valueOf(request.getMethod().name()),
                response.build());

        if (errorBody) {
            assertThat(body).as("%s: problem body", exchange).isNotEmpty();
            assertThat(JsonMapper.shared().readTree(body).path("status").asInt())
                    .as("%s: status in the body", exchange)
                    .isEqualTo(status.value());
        }
        if (report.getMessages().stream().noneMatch(message -> NOT_AN_OPERATION.contains(message.getKey()))) {
            if (!body.isEmpty()) {
                assertThat(headers.getContentType())
                        .as("%s: content type", exchange)
                        .isNotNull();
            }
            assertThat(report.getMessages())
                    .as("%s: against the contract", exchange)
                    .isEmpty();
        } else if (errorBody) {
            assertThat(headers.getContentType())
                    .as("%s: content type", exchange)
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            var problem = contract.getComponents().getSchemas().get("Problem");
            assertThat(schemas.validate(body, problem, "response.body").getMessages())
                    .as("%s: against the Problem schema", exchange)
                    .isEmpty();
        }
    }

    private record Buffered(ClientHttpResponse response, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return response.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            response.close();
        }
    }
}

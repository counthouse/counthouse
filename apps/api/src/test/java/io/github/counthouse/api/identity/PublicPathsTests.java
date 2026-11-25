package io.github.counthouse.api.identity;

import io.github.counthouse.api.ApiTest;
import io.github.counthouse.api.TestKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/** What answers without a token: the health check and the protected resource metadata, and nothing else. */
@ApiTest
class PublicPathsTests {

    @Autowired
    RestTestClient client;

    @Test
    void healthCheckNeedsNoToken() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    /** RFC 9728 metadata, which Spring Security always serves; its values come from configuration. */
    @Test
    void resourceMetadataStatesWhatIsTrue() {
        client.get()
                .uri("/.well-known/oauth-protected-resource")
                .header("Host", "evil.example")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.resource")
                .isEqualTo("https://api.example.test")
                .jsonPath("$.authorization_servers[0]")
                .isEqualTo(TestKeycloak.get().issuer())
                .jsonPath("$.tls_client_certificate_bound_access_tokens")
                .isEqualTo(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/nope", "/error", "/actuator", "/actuator/info", "/actuator/health/liveness"})
    void everyOtherPathNeedsAToken(String path) {
        client.get()
                .uri(path)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueEquals("WWW-Authenticate", "Bearer");
    }
}

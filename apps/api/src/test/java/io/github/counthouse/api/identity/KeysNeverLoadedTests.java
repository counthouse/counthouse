package io.github.counthouse.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.counthouse.api.ApiTest;
import io.github.counthouse.api.TestKeycloak;
import io.github.counthouse.api.TestcontainersConfiguration.MigratorDatabase;
import io.github.counthouse.api.identity.KeySetStub.Mode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Keycloak unavailable since the API started: no token can be checked, so every request with one is a 503, whether
 * Keycloak fails, hangs, or the rate limit holds the next fetch back; a valid token is never answered 401.
 */
@ApiTest
class KeysNeverLoadedTests {

    static final KeySetStub KEYS = new KeySetStub(Mode.HANGING);

    // A registrar of the test's own: Spring applies the shared Keycloak registrar after @DynamicPropertySource.
    @TestConfiguration(proxyBeanMethods = false)
    static class Keys {

        @Bean
        DynamicPropertyRegistrar keySetStub() {
            return registry -> {
                registry.add("counthouse.auth.jwk-set-uri", KEYS::uri);
                registry.add("counthouse.auth.keys.cache-time-to-live", () -> "2s");
                registry.add("counthouse.auth.keys.refresh-interval", () -> "1s");
                registry.add("counthouse.auth.keys.outage-window", () -> "4s");
                registry.add("counthouse.auth.keys.timeout", () -> "500ms");
            };
        }
    }

    @Autowired
    RestTestClient client;

    @Autowired
    MigratorDatabase migrator;

    @Test
    void answersServiceUnavailableUntilKeycloakIsBack() {
        TestKeycloak keycloak = TestKeycloak.get();
        UUID tenant = migrator.insertTenant("Clinic");
        String subject = UUID.randomUUID().toString();
        migrator.insertStaff(tenant, keycloak.issuer(), subject);
        String token = keycloak.sign(keycloak.claims(tenant, subject).build());

        long start = System.nanoTime();
        assertThat(status(token)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(status(token)).as("held back by the rate limit").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        KEYS.mode(Mode.DOWN);
        for (int i = 0; i < 8; i++) {
            assertThat(status(token)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            sleep(250);
        }

        KEYS.mode(Mode.UP);
        KeycloakOutageTests.awaitStatus(() -> status(token), HttpStatus.OK, Duration.ofSeconds(5));
        assertThat(KEYS.fetches()).isGreaterThan(1);
    }

    private HttpStatusCode status(String bearer) {
        return client.get()
                .uri("/me")
                .headers(h -> h.setBearerAuth(bearer))
                .exchange()
                .returnResult(String.class)
                .getStatus();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}

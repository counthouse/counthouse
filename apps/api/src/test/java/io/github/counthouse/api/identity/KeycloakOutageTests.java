package io.github.counthouse.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.counthouse.api.ApiTest;
import io.github.counthouse.api.TestKeycloak;
import io.github.counthouse.api.TestcontainersConfiguration.MigratorDatabase;
import io.github.counthouse.api.identity.KeySetStub.Mode;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Keycloak going down after the API has its keys: valid tokens keep passing for the outage window, a token signed
 * with a key the API does not have is rejected, and only then is the answer 503. The key set endpoint is a stub in
 * front of the test Keycloak, with the key timings shortened.
 */
@ApiTest
class KeycloakOutageTests {

    static final KeySetStub KEYS = new KeySetStub(Mode.UP);
    static final Duration OUTAGE_WINDOW = Duration.ofSeconds(4);

    // A registrar of the test's own: Spring applies the shared Keycloak registrar after @DynamicPropertySource.
    @TestConfiguration(proxyBeanMethods = false)
    static class Keys {

        @Bean
        DynamicPropertyRegistrar keySetStub() {
            return registry -> {
                registry.add("counthouse.auth.jwk-set-uri", KEYS::uri);
                registry.add("counthouse.auth.keys.cache-time-to-live", () -> "2s");
                registry.add("counthouse.auth.keys.refresh-interval", () -> "1s");
                registry.add("counthouse.auth.keys.outage-window", () -> OUTAGE_WINDOW.toString());
                registry.add("counthouse.auth.keys.timeout", () -> "500ms");
            };
        }
    }

    @Autowired
    RestTestClient client;

    @Autowired
    MigratorDatabase migrator;

    final TestKeycloak keycloak = TestKeycloak.get();
    String token;

    @BeforeEach
    void keysLoaded() {
        UUID tenant = migrator.insertTenant("Clinic");
        String subject = UUID.randomUUID().toString();
        migrator.insertStaff(tenant, keycloak.issuer(), subject);
        token = keycloak.sign(keycloak.claims(tenant, subject).build());
        KEYS.mode(Mode.UP);
        awaitStatus(() -> status(token), HttpStatus.OK, Duration.ofSeconds(5));
    }

    @Test
    void acceptsValidTokensThroughTheOutageWindowThenAnswersServiceUnavailable() throws InterruptedException {
        Thread.sleep(2_100); // the next request fetches the keys: the outage window starts there
        assertThat(status(token)).isEqualTo(HttpStatus.OK);
        KEYS.mode(Mode.DOWN);
        long down = System.nanoTime();

        assertThat(status(token)).isEqualTo(HttpStatus.OK);
        assertThat(status(unknownKey())).isEqualTo(HttpStatus.UNAUTHORIZED);
        Thread.sleep(2_500); // the cache has expired and its refresh fails
        assertThat(status(token)).isEqualTo(HttpStatus.OK);
        assertThat(status(unknownKey())).isEqualTo(HttpStatus.UNAUTHORIZED);

        awaitStatus(() -> status(token), HttpStatus.SERVICE_UNAVAILABLE, Duration.ofSeconds(10));
        assertThat(Duration.ofNanos(System.nanoTime() - down)).isGreaterThanOrEqualTo(OUTAGE_WINDOW.minusSeconds(1));

        KEYS.mode(Mode.UP);
        awaitStatus(() -> status(token), HttpStatus.OK, Duration.ofSeconds(5));
    }

    /** The window starts when a fetch first fails, not at the last fetch, which can be long ago on an idle API. */
    @Test
    void acceptsValidTokensWhenKeycloakGoesDownWhileTheApiIsIdle() throws InterruptedException {
        Thread.sleep(OUTAGE_WINDOW.toMillis() + 500);
        KEYS.mode(Mode.DOWN);

        assertThat(status(token)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownKeyIdsReachKeycloakAtMostOncePerRefreshInterval() throws InterruptedException {
        var key = TestKeycloak.newKey();
        var tokens = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> keycloak.sign(
                        keycloak.claims(UUID.randomUUID(), "anyone").build(),
                        header -> header.keyID(UUID.randomUUID().toString()),
                        key))
                .toList();
        Thread.sleep(1_100); // a fresh rate-limit interval: the loop below stays inside it
        int before = KEYS.fetches();
        long start = System.nanoTime();
        for (String unknown : tokens) {
            assertThat(status(unknown)).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        // Nimbus' rate limit lets two fetches through per interval.
        assertThat(KEYS.fetches() - before).isLessThanOrEqualTo(2);
    }

    private String unknownKey() {
        return keycloak.sign(
                keycloak.claims(UUID.randomUUID(), "anyone").build(),
                header -> header.keyID(UUID.randomUUID().toString()),
                TestKeycloak.newKey());
    }

    private HttpStatusCode status(String bearer) {
        return client.get()
                .uri("/me")
                .headers(h -> h.setBearerAuth(bearer))
                .exchange()
                .returnResult(String.class)
                .getStatus();
    }

    static void awaitStatus(Supplier<HttpStatusCode> request, HttpStatus expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpStatusCode last;
        do {
            last = request.get();
            if (last.equals(expected)) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Expected " + expected + " within " + timeout + ", last was " + last);
    }
}

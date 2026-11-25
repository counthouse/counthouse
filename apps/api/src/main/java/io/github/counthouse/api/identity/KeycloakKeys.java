package io.github.counthouse.api.identity;

import com.nimbusds.jose.jwk.source.CachingJWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSetBasedJWKSource;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RateLimitedJWKSetSource;
import com.nimbusds.jose.jwk.source.URLBasedJWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.MalformedURLException;
import java.net.URI;

/**
 * Keycloak's signing keys, cached. Spring's default key source fetches again for every token with an unknown key id,
 * with 30-second timeouts, so any anonymous request could make the API call Keycloak. Here a fetch happens at most
 * once per refresh interval, times out within seconds, and the last keys stay in use through an outage.
 */
final class KeycloakKeys {

    private static final int MAX_KEY_SET_BYTES = 50 * 1024;

    private KeycloakKeys() {}

    static JWKSource<SecurityContext> create(URI jwkSetUri, AuthProperties.Keys keys) {
        int timeout = Math.toIntExact(keys.timeout().toMillis());
        JWKSetSource<SecurityContext> source;
        try {
            source = new URLBasedJWKSetSource<>(
                    jwkSetUri.toURL(), new DefaultResourceRetriever(timeout, timeout, MAX_KEY_SET_BYTES));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("counthouse.auth.jwk-set-uri is not a URL", e);
        }
        source = new RateLimitedJWKSetSource<>(source, keys.refreshInterval().toMillis(), null);
        source = new CachingJWKSetSource<>(source, keys.cacheTimeToLive().toMillis(), timeout, null);
        source = new LastKnownKeys(source, keys.outageWindow().toMillis());
        return new JWKSetBasedJWKSource<>(source);
    }
}

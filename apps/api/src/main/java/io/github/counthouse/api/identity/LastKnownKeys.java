package io.github.counthouse.api.identity;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sits above the cache and answers with the last key set it saw when the cache cannot refresh, because Keycloak
 * failed or the rate limit held the fetch back, for the outage window counted from the first failure. A token whose
 * key is in that set still verifies, a token whose key is not is rejected as invalid, and only when no set is held,
 * or the window has passed, does the failure reach the caller, as a dependency failure. The window starts at the
 * first failure, not at the last fetch, so an API that was idle when Keycloak went down still accepts the tokens
 * issued before. Above the cache, the old set is never cached again, so the window ends on time. Nimbus'
 * outage-tolerant source cannot do this: it sits below the rate limit, so a refused refresh never reaches it, and
 * below the cache, so the cache keeps the old set for another lifetime.
 */
final class LastKnownKeys implements JWKSetSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(LastKnownKeys.class);

    private record Fetched(JWKSet keys, long at) {}

    private record Failing(long since) {}

    private final JWKSetSource<SecurityContext> source;
    private final long outageWindowMillis;
    private volatile @Nullable Fetched last;
    private volatile @Nullable Failing failing;

    LastKnownKeys(JWKSetSource<SecurityContext> source, long outageWindowMillis) {
        this.source = source;
        this.outageWindowMillis = outageWindowMillis;
    }

    @Override
    public JWKSet getJWKSet(JWKSetCacheRefreshEvaluator refreshEvaluator, long currentTime, SecurityContext context)
            throws KeySourceException {
        try {
            JWKSet keys = source.getJWKSet(refreshEvaluator, currentTime, context);
            Fetched known = last;
            // The cache returns the same set until it fetches a new one; a thread that read an older set before the
            // fetch must not put it back.
            if (known == null || (known.keys() != keys && currentTime >= known.at())) {
                last = new Fetched(keys, currentTime);
            }
            failing = null;
            return keys;
        } catch (KeySourceException e) {
            if (!(e instanceof RateLimitReachedException)) {
                log.warn("Could not fetch Keycloak's keys: {}", e.getMessage());
            }
            Failing since = failing;
            if (since == null) {
                since = new Failing(currentTime);
                failing = since;
            }
            Fetched known = last;
            if (known != null && currentTime - since.since() < outageWindowMillis) {
                return known.keys();
            }
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}

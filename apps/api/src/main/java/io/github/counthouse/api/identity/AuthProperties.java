package io.github.counthouse.api.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where the API finds Keycloak. {@code issuer} is Keycloak's public URL, as it appears in tokens; {@code jwkSetUri}
 * is its internal URL on the server's network, so the keys never come through the public tunnel.
 *
 * @param resource the API's public URL, published as its RFC 9728 resource identifier
 */
@ConfigurationProperties("counthouse.auth")
@Validated
record AuthProperties(
        @NotNull URI issuer,
        @NotNull URI jwkSetUri,
        @NotBlank String clientId,
        @NotNull URI resource,
        @DefaultValue @Valid Keys keys) {

    /**
     * How the API keeps Keycloak's keys. Access tokens live 5 minutes, and the outage window starts at the first
     * failed fetch, which comes after Keycloak went down, so a window longer than 5 minutes covers every token
     * Keycloak issued before it went down.
     *
     * @param cacheTimeToLive how long a fetched key set is used before it is fetched again
     * @param refreshInterval the shortest time between two fetches, whatever the tokens ask for
     * @param outageWindow how long after the first failed fetch the last keys are still used
     * @param timeout the connect and read timeout of a fetch
     */
    record Keys(
            @DefaultValue("5m") Duration cacheTimeToLive,
            @DefaultValue("30s") Duration refreshInterval,
            @DefaultValue("10m") Duration outageWindow,
            @DefaultValue("2s") Duration timeout) {}
}

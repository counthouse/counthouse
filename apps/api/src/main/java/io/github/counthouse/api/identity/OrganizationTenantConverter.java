package io.github.counthouse.api.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Reads the tenant from the token's {@code organization} claim. Each tenant's Keycloak organization has the tenant id
 * as its alias, which Keycloak does not let anyone change, so the claim is a list holding that alias.
 */
final class OrganizationTenantConverter implements Converter<Jwt, TokenAuthentication> {

    @Override
    public TokenAuthentication convert(Jwt jwt) {
        // Keycloak always sends a list; Spring would turn a lone string or an object into one.
        if (!(jwt.getClaim("organization") instanceof List<?> organizations)
                || organizations.size() != 1
                || !(organizations.getFirst() instanceof String alias)) {
            throw new InvalidBearerTokenException("The token must name exactly one organization");
        }
        UUID tenantId = canonicalUuid(alias);
        if (jwt.getIssuer() == null || jwt.getSubject() == null) {
            throw new InvalidBearerTokenException("The token has no issuer or subject");
        }
        return new TokenAuthentication(tenantId, jwt.getIssuer().toString(), jwt.getSubject());
    }

    // UUID.fromString accepts other spellings of the same id ("1-1-1-1-1", upper case), and Keycloak treats each
    // spelling as another organization, so only the canonical form is a tenant.
    private static UUID canonicalUuid(String alias) {
        try {
            UUID id = UUID.fromString(alias);
            if (id.toString().equals(alias)) {
                return id;
            }
        } catch (IllegalArgumentException e) {
            // Not a UUID: rejected below.
        }
        throw new InvalidBearerTokenException("The organization is not a tenant id");
    }
}

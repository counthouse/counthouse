package io.github.counthouse.api.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * A token that passed validation, reduced to what the login lookup needs: its tenant, issuer and subject. The token
 * itself is not kept.
 */
final class TokenAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final UUID tenantId;
    private final String issuer;
    private final String subject;

    TokenAuthentication(UUID tenantId, String issuer, String subject) {
        super(List.of());
        this.tenantId = tenantId;
        this.issuer = issuer;
        this.subject = subject;
        setAuthenticated(true);
    }

    UUID tenantId() {
        return tenantId;
    }

    String issuer() {
        return issuer;
    }

    String subject() {
        return subject;
    }

    @Override
    public Object getPrincipal() {
        return subject;
    }

    @Override
    public Object getCredentials() {
        return "";
    }
}

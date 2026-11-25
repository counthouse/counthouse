package io.github.counthouse.api.identity;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** A request whose token passed and whose login belongs to an active staff member. */
final class ActorAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final Actor actor;

    ActorAuthentication(Actor actor) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name())));
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public Actor getPrincipal() {
        return actor;
    }

    @Override
    public Object getCredentials() {
        return "";
    }
}

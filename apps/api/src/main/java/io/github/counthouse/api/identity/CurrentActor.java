package io.github.counthouse.api.identity;

import org.springframework.security.core.context.SecurityContextHolder;

/** The {@link Actor} of the current request. */
public final class CurrentActor {

    private CurrentActor() {}

    /** Throws outside an authenticated request: no code may act without a staff member. */
    public static Actor get() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof ActorAuthentication authentication) {
            return authentication.getPrincipal();
        }
        throw new IllegalStateException("No staff member: the request is not authenticated");
    }
}

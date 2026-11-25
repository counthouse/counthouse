package io.github.counthouse.api.identity;

import io.github.counthouse.api.tenancy.TenantScope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs the rest of the request in the token's tenant and replaces the token with the staff member its login belongs
 * to. A token whose login is unknown in that tenant, or whose staff member is inactive, is answered by the entry point;
 * nothing is thrown, since an exception from a filter becomes a 500. It skips the error dispatch: the error controller
 * needs no tenant, and a second lookup after a database failure would hide the 500.
 */
final class ActorFilter extends OncePerRequestFilter {

    private final Logins logins;
    private final AuthenticationEntryPoint entryPoint;
    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

    ActorFilter(Logins logins, AuthenticationEntryPoint entryPoint) {
        this.logins = logins;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(contexts.getContext().getAuthentication() instanceof TokenAuthentication token)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TenantScope.run(token.tenantId(), () -> lookUpAndContinue(token, request, response, chain));
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void lookUpAndContinue(
            TokenAuthentication token, HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        {
            Optional<Actor> actor = logins.findActivePerson(token.issuer(), token.subject());
            if (actor.isEmpty()) {
                contexts.clearContext();
                entryPoint.commence(request, response, new InvalidBearerTokenException("No active login"));
                return;
            }
            var context = contexts.createEmptyContext();
            context.setAuthentication(new ActorAuthentication(actor.get()));
            contexts.setContext(context);
            chain.doFilter(request, response);
        }
    }
}

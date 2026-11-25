package io.github.counthouse.api.identity;

import com.nimbusds.jose.KeySourceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Answers a request that did not authenticate. The status is sent with {@code sendError}, so the error controller
 * writes the problem as for any other error outside Spring MVC. The {@code WWW-Authenticate} header never carries a
 * description: Spring's would repeat why the token failed, such as its expiry time.
 */
final class BearerTokenProblems implements AuthenticationEntryPoint, AuthenticationFailureHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        // A token in the header is always the only one (query and form tokens are off), so every OAuth 2.0 error
        // here is about the token itself, malformed ones included (RFC 6750, section 3.1).
        if (exception instanceof OAuth2AuthenticationException) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /**
     * A token that could not be checked because Keycloak's keys are unavailable is a 503: the client may retry, and
     * must not take it for a rejected token. Any other failure of the authentication itself is rethrown and becomes
     * a 500.
     */
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (exception instanceof AuthenticationServiceException) {
            if (causedByUnavailableKeys(exception)) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
            throw exception;
        }
        commence(request, response, exception);
    }

    private static boolean causedByUnavailableKeys(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof KeySourceException) {
                return true;
            }
        }
        return false;
    }
}

package io.github.counthouse.api.identity;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * How a request is authenticated, in order:
 *
 * <ol>
 *   <li>The bearer token is an RFC 9068 access token that Keycloak issued for this API to the backoffice's client,
 *       signed with one of Keycloak's keys ({@link #jwtDecoder}).
 *   <li>Its {@code organization} claim names the tenant ({@link OrganizationTenantConverter}).
 *   <li>The rest of the request runs in that tenant's scope, as the active staff member the token's login belongs to
 *       ({@link ActorFilter}).
 * </ol>
 *
 * Anything else is answered by {@link BearerTokenProblems}: 401, or 503 when Keycloak's keys are unavailable. Only the
 * error dispatch, the health check and the protected resource metadata are open without a token.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfiguration {

    static final String AUDIENCE = "counthouse-api";

    @Bean
    SecurityFilterChain api(HttpSecurity http, AuthProperties auth, JwtDecoder jwtDecoder, Logins logins) {
        var problems = new BearerTokenProblems();
        http.authorizeHttpRequests(requests -> requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt ->
                                jwt.decoder(jwtDecoder).jwtAuthenticationConverter(new OrganizationTenantConverter()))
                        .authenticationEntryPoint(problems)
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                // Spring takes the resource from the request's Host header and claims
                                // certificate-bound tokens; both are set from what is true here.
                                builder -> builder.resource(auth.resource().toString())
                                        .authorizationServer(auth.issuer().toString())
                                        .tlsClientCertificateBoundAccessTokens(false)))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(problems);
                                return filter;
                            }
                        }))
                .addFilterAfter(new ActorFilter(logins, problems), BearerTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems))
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens only: no cookie authenticates a request, so there is nothing to forge.
                .csrf(csrf -> csrf.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable());
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties auth) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(KeycloakKeys.create(auth.jwkSetUri(), auth.keys()))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // The client id must be set: without it, a token issued to any client passes.
        decoder.setJwtValidator(JwtValidators.createAtJwtValidator()
                .issuer(auth.issuer().toString())
                .audience(AUDIENCE)
                .clientId(auth.clientId())
                .build());
        return decoder;
    }
}

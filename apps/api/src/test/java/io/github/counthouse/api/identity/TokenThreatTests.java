package io.github.counthouse.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.github.counthouse.api.ApiTest;
import io.github.counthouse.api.TestKeycloak;
import io.github.counthouse.api.TestcontainersConfiguration.MigratorDatabase;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Each way a request could pass as a staff member it is not, by name. Every case runs on a real server against the
 * Keycloak started from the repository's realm file, and every response is checked against the contract.
 */
@ApiTest
@ExtendWith(OutputCaptureExtension.class)
class TokenThreatTests {

    static final String PASSWORD = "a-test-password";

    @Autowired
    RestTestClient client;

    @Autowired
    MigratorDatabase migrator;

    final TestKeycloak keycloak = TestKeycloak.get();

    UUID tenant;
    String subject;
    UUID staffMember;

    @BeforeEach
    void staffMemberWithALogin() {
        tenant = migrator.insertTenant("Clinic");
        subject = UUID.randomUUID().toString();
        staffMember = migrator.insertStaff(tenant, keycloak.issuer(), subject);
    }

    @Test
    void acceptsATokenKeycloakIssuedToTheBackoffice() {
        String organization = keycloak.createOrganization(tenant);
        String user = keycloak.createUser(PASSWORD, organization);
        UUID person = migrator.insertStaff(tenant, keycloak.issuer(), user);

        client.get()
                .uri("/me")
                .headers(h -> h.setBearerAuth(keycloak.signIn(user, PASSWORD, "organization")))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.user.id")
                .isEqualTo(person.toString())
                .jsonPath("$.user.role")
                .isEqualTo("ADMIN")
                .jsonPath("$.tenant.id")
                .isEqualTo(tenant.toString())
                .jsonPath("$.tenant.name")
                .isEqualTo("Clinic");
    }

    @Test
    void acceptsATokenSignedWithAPublishedKey() {
        assertAccepted(keycloak.sign(valid().build()));
    }

    @Test
    void rejectsARequestWithoutAToken() {
        client.get()
                .uri("/me")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueEquals("WWW-Authenticate", "Bearer");
    }

    @Test
    void rejectsAMalformedAuthorizationHeader() {
        assertRejected("not a token");
    }

    @Test
    void rejectsAPlainJwtInsteadOfAnAccessToken() {
        assertRejected(keycloak.sign(valid().build(), header -> header.type(JOSEObjectType.JWT)));
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        assertRejected(keycloak.sign(
                valid().issuer("https://evil.example/realms/counthouse").build()));
    }

    @Test
    void rejectsATokenForAnotherAudience() {
        assertRejected(keycloak.sign(valid().audience("another-api").build()));
    }

    @Test
    void rejectsATokenWithoutAClientId() {
        assertRejected(keycloak.sign(valid().claim("client_id", null).build()));
    }

    @Test
    void rejectsATokenIssuedForAnotherClient() {
        assertRejected(
                keycloak.sign(valid().claim("client_id", "another-client").build()));
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant past = Instant.now().minusSeconds(600);
        assertRejected(keycloak.sign(valid().issueTime(Date.from(past))
                .expirationTime(Date.from(past.plusSeconds(300)))
                .build()));
    }

    @Test
    void rejectsATokenSignedWithAnUnpublishedKey() {
        assertRejected(keycloak.sign(valid().build(), UnaryOperator.identity(), TestKeycloak.newKey()));
    }

    @Test
    void rejectsAnUnsignedToken() {
        assertRejected(new PlainJWT(valid().build()).serialize());
    }

    @Test
    void rejectsATokenSignedWithTheSharedSecretAlgorithm() throws Exception {
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .type(new JOSEObjectType("at+jwt"))
                        .build(),
                valid().build());
        jwt.sign(new MACSigner("a-secret-of-at-least-thirty-two-bytes!"));
        assertRejected(jwt.serialize());
    }

    @Test
    void rejectsATokenWithoutAnOrganization() {
        assertRejected(keycloak.sign(valid().claim("organization", null).build()));
    }

    @Test
    void rejectsATokenWithTwoOrganizations() {
        UUID other = migrator.insertTenant("Other clinic");
        assertRejected(keycloak.sign(valid().claim("organization", List.of(tenant.toString(), other.toString()))
                .build()));
    }

    @Test
    void rejectsAnOrganizationThatIsNotAList() {
        assertRejected(
                keycloak.sign(valid().claim("organization", tenant.toString()).build()));
    }

    @Test
    void rejectsAnOrganizationThatIsNotATenantId() {
        assertRejected(
                keycloak.sign(valid().claim("organization", List.of("clinic")).build()));
    }

    @Test
    void rejectsAnotherSpellingOfATenantId() {
        assertRejected(keycloak.sign(
                valid().claim("organization", List.of(tenant.toString().toUpperCase()))
                        .build()));
    }

    @Test
    void rejectsAnOrganizationWithAttributes() {
        assertRejected(keycloak.sign(
                valid().claim("organization", java.util.Map.of(tenant.toString(), java.util.Map.of("id", "x")))
                        .build()));
    }

    @Test
    void rejectsAnUnknownLogin() {
        assertRejected(keycloak.sign(
                keycloak.claims(tenant, UUID.randomUUID().toString()).build()));
    }

    @Test
    void rejectsAnInactiveStaffMember() {
        migrator.setActive(tenant, staffMember, false);
        assertRejected(keycloak.sign(valid().build()));
    }

    @Test
    void rejectsALoginPresentedForAnotherTenant() {
        UUID other = migrator.insertTenant("Other clinic");
        assertRejected(keycloak.sign(keycloak.claims(other, subject).build()));
    }

    @Test
    void rejectsATenantWithoutARow() {
        assertRejected(keycloak.sign(keycloak.claims(UUID.randomUUID(), subject).build()));
    }

    @Test
    void keepsTokensAndSubjectsOutOfTheLogs(CapturedOutput output) {
        String token = keycloak.sign(valid().audience("another-api").build());
        assertRejected(token);
        assertAccepted(keycloak.sign(valid().build()));

        assertThat(output.getAll())
                .doesNotContain(token)
                .doesNotContain(subject)
                .doesNotContain("aud claim");
    }

    private JWTClaimsSet.Builder valid() {
        return keycloak.claims(tenant, subject);
    }

    private void assertAccepted(String token) {
        client.get()
                .uri("/me")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.user.id")
                .isEqualTo(staffMember.toString());
    }

    private void assertRejected(String token) {
        client.get()
                .uri("/me")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader()
                .valueEquals("WWW-Authenticate", "Bearer error=\"invalid_token\"");
    }
}

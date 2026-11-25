package io.github.counthouse.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * One Keycloak for the whole test run, started from the repository's realm file.
 *
 * <p>Before any test changes it, the realm is exported as imported ({@link #realmAsImported()}), so the realm-shape
 * test sees the shipped settings whatever ran first. Then, for tests only, the backoffice's client gets the password
 * grant, so tests can sign in without a browser, and the realm gets a second signing key whose private half the tests
 * hold. Keycloak publishes that key but keeps signing with its own, as when a new key is added before it is used, so
 * {@link #sign} can issue a token that differs from a real one only in the claim under test.
 */
public final class TestKeycloak {

    public static final String REALM = "counthouse";
    public static final String CLIENT_ID = "bff";
    public static final Path REALM_FILE =
            Path.of("../../infra/keycloak/realm.json").toAbsolutePath().normalize();

    private static final String ADMIN = "admin";
    private static final JsonMapper JSON = JsonMapper.shared();

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>(PinnedImage.of("keycloak"))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(REALM_FILE), "/opt/keycloak/data/import/" + REALM + ".json")
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(8080)
            // Keycloak can take over a minute to start on a loaded machine; Testcontainers waits one by default.
            .waitingFor(Wait.forHttp("/realms/" + REALM).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));

    private static final TestKeycloak INSTANCE = new TestKeycloak();

    private final String url;
    private final RestClient http;
    private final JsonNode realmAsImported;
    private final String clientSecret;
    private final RSAKey testKey;

    private TestKeycloak() {
        CONTAINER.start();
        url = "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(8080);
        http = RestClient.create(url);
        realmAsImported = JSON.readTree(admin().post()
                .uri("/admin/realms/{realm}/partial-export?exportClients=true&exportGroupsAndRoles=true", REALM)
                .retrieve()
                .body(String.class));
        String client = clientUuid();
        allowPasswordGrant(client);
        clientSecret = JSON.readTree(admin().get()
                        .uri("/admin/realms/{realm}/clients/{id}/client-secret", REALM, client)
                        .retrieve()
                        .body(String.class))
                .path("value")
                .asString();
        testKey = addTestSigningKey();
    }

    public static TestKeycloak get() {
        return INSTANCE;
    }

    /** Keycloak's public URL in tests, as it appears in the tokens' {@code iss}. */
    public String issuer() {
        return url + "/realms/" + REALM;
    }

    public String jwkSetUri() {
        return issuer() + "/protocol/openid-connect/certs";
    }

    /** The realm as the file imported it, before the tests changed anything. */
    public JsonNode realmAsImported() {
        return realmAsImported;
    }

    /** Creates the organization of a tenant, whose alias is the tenant id. */
    public String createOrganization(UUID tenantId) {
        var location = admin().post()
                .uri("/admin/realms/{realm}/organizations", REALM)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Tenant " + tenantId, "alias", tenantId.toString(), "domains", List.of()))
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        return lastSegment(location.getPath());
    }

    /** Creates a user with a password and returns its id, the {@code sub} of its tokens. */
    public String createUser(String password, String... organizationIds) {
        var location = admin().post()
                .uri("/admin/realms/{realm}/users", REALM)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username",
                        UUID.randomUUID().toString(),
                        "enabled",
                        true,
                        "credentials",
                        List.of(Map.of("type", "password", "value", password, "temporary", false))))
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        String userId = lastSegment(location.getPath());
        for (String organizationId : organizationIds) {
            admin().post()
                    .uri("/admin/realms/{realm}/organizations/{org}/members", REALM, organizationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("\"" + userId + "\"")
                    .retrieve()
                    .toBodilessEntity();
        }
        return userId;
    }

    /** Signs in with the password grant, as the backoffice's client, and returns the access token. */
    public String signIn(String userId, String password, String scope) {
        String username = JSON.readTree(admin().get()
                        .uri("/admin/realms/{realm}/users/{id}", REALM, userId)
                        .retrieve()
                        .body(String.class))
                .path("username")
                .asString();
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", CLIENT_ID);
        form.add("client_secret", clientSecret);
        form.add("username", username);
        form.add("password", password);
        form.add("scope", scope);
        return JSON.readTree(http.post()
                        .uri("/realms/{realm}/protocol/openid-connect/token", REALM)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class))
                .path("access_token")
                .asString();
    }

    /** The claims of a token Keycloak would issue to the backoffice for this subject in this tenant. */
    public JWTClaimsSet.Builder claims(UUID tenantId, String subject) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer())
                .audience("counthouse-api")
                .subject(subject)
                .claim("client_id", CLIENT_ID)
                .claim("azp", CLIENT_ID)
                .claim("organization", List.of(tenantId.toString()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString());
    }

    /** Signs claims with the test key Keycloak publishes, with an {@code at+jwt} header unless changed. */
    public String sign(JWTClaimsSet claims, UnaryOperator<JWSHeader.Builder> header) {
        return sign(claims, header, testKey);
    }

    public String sign(JWTClaimsSet claims) {
        return sign(claims, UnaryOperator.identity());
    }

    /** Signs claims with any key, with the test key's id unless the header changes it. */
    public String sign(JWTClaimsSet claims, UnaryOperator<JWSHeader.Builder> header, RSAKey key) {
        var builder = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("at+jwt"))
                .keyID(testKey.getKeyID());
        var jwt = new SignedJWT(header.apply(builder).build(), claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    public static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private RestClient admin() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", ADMIN);
        form.add("password", ADMIN);
        String token = JSON.readTree(http.post()
                        .uri("/realms/master/protocol/openid-connect/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class))
                .path("access_token")
                .asString();
        return http.mutate().defaultHeader("Authorization", "Bearer " + token).build();
    }

    private String clientUuid() {
        return JSON.readTree(admin().get()
                        .uri("/admin/realms/{realm}/clients?clientId={clientId}", REALM, CLIENT_ID)
                        .retrieve()
                        .body(String.class))
                .path(0)
                .path("id")
                .asString();
    }

    private void allowPasswordGrant(String client) {
        var representation = (ObjectNode) JSON.readTree(admin().get()
                .uri("/admin/realms/{realm}/clients/{id}", REALM, client)
                .retrieve()
                .body(String.class));
        representation.put("directAccessGrantsEnabled", true);
        admin().put()
                .uri("/admin/realms/{realm}/clients/{id}", REALM, client)
                .contentType(MediaType.APPLICATION_JSON)
                .body(JSON.writeValueAsString(representation))
                .retrieve()
                .toBodilessEntity();
    }

    // Priority 1, below the generated key's 100: published, never used by Keycloak to sign.
    private RSAKey addTestSigningKey() {
        RSAKey key = newKey();
        String realmId = realmAsImported.path("id").asString();
        String pem;
        try {
            // Keycloak takes the PKCS#8 key as bare base64, without PEM armour.
            pem = Base64.getEncoder().encodeToString(key.toPrivateKey().getEncoded());
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        var location = admin().post()
                .uri("/admin/realms/{realm}/components", REALM)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name",
                        "test-signing-key",
                        "providerId",
                        "rsa",
                        "providerType",
                        "org.keycloak.keys.KeyProvider",
                        "parentId",
                        realmId,
                        "config",
                        Map.of(
                                "privateKey", List.of(pem),
                                "priority", List.of("1"),
                                "active", List.of("true"),
                                "enabled", List.of("true"),
                                "algorithm", List.of("RS256"))))
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        String componentId = lastSegment(location.getPath());
        for (JsonNode published : JSON.readTree(admin().get()
                        .uri("/admin/realms/{realm}/keys", REALM)
                        .retrieve()
                        .body(String.class))
                .path("keys")) {
            if (componentId.equals(published.path("providerId").asString())) {
                return new RSAKey.Builder(key)
                        .keyID(published.path("kid").asString())
                        .build();
            }
        }
        throw new IllegalStateException("Keycloak did not publish the test signing key");
    }

    /** The realm file as committed. */
    public static JsonNode realmFile() {
        try {
            return JSON.readTree(Files.readString(REALM_FILE));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}

package io.github.counthouse.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Keycloak for the whole test run, started from the repository's realm file.
 *
 * <p>The realm is exported once at start ({@link #realmAsImported()}), so the realm-shape test sees the shipped
 * settings whatever ran first.
 */
public final class TestKeycloak {

    public static final String REALM = "counthouse";
    public static final String CLIENT_ID = "bff";
    public static final Path REALM_FILE =
            Path.of("../../infra/keycloak/realm.json").toAbsolutePath().normalize();

    private static final String ADMIN = "admin";
    private static final JsonMapper JSON = JsonMapper.shared();

    // Bumped by hand, like PostgreSQL: Dependabot does not read image names in Java.
    private static final GenericContainer<?> CONTAINER = new GenericContainer<>(
                    DockerImageName.parse(
                            "quay.io/keycloak/keycloak:26.8.2@sha256:7e66a96e4739239070aeaf72e4c5decf0f7f682cfc725b9f7ea2b336eaba38b3"))
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

    private TestKeycloak() {
        CONTAINER.start();
        url = "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(8080);
        http = RestClient.create(url);
        realmAsImported = JSON.readTree(admin().post()
                .uri("/admin/realms/{realm}/partial-export?exportClients=true&exportGroupsAndRoles=true", REALM)
                .retrieve()
                .body(String.class));
    }

    public static TestKeycloak get() {
        return INSTANCE;
    }

    /** Keycloak's public URL in tests, as it appears in the tokens' {@code iss}. */
    public String issuer() {
        return url + "/realms/" + REALM;
    }

    /** The realm as the file imported it, before the tests changed anything. */
    public JsonNode realmAsImported() {
        return realmAsImported;
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

    /** The realm file as committed. */
    public static JsonNode realmFile() {
        try {
            return JSON.readTree(Files.readString(REALM_FILE));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

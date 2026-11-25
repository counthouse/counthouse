package io.github.counthouse.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.counthouse.api.TestKeycloak;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The realm as Keycloak imported it from {@code infra/keycloak/realm.json}, read through the Admin API before any test
 * changed it. Each rule closes a way to get a token, or a credential, that the design does not plan for. Keycloak's
 * built-in clients are in the export even though the file does not list them, so they are checked too.
 */
class RealmShapeTests {

    static final Set<String> BUILT_IN_CLIENTS_OFF =
            Set.of("admin-cli", "account", "account-console", "security-admin-console");

    final JsonNode realm = TestKeycloak.get().realmAsImported();

    @Test
    void noClientSignsInWithAPasswordOrWithoutASecret() {
        assertThat(enabledClients())
                .filteredOn(client -> client.path("directAccessGrantsEnabled").asBoolean()
                        || client.path("implicitFlowEnabled").asBoolean()
                        || client.path("publicClient").asBoolean())
                .extracting(client -> client.path("clientId").asString())
                .isEmpty();
    }

    @Test
    void noClientUsesAnotherGrant() {
        assertThat(enabledClients())
                .filteredOn(client -> client.path("serviceAccountsEnabled").asBoolean()
                        || attribute(client, "oauth2.device.authorization.grant.enabled")
                        || attribute(client, "oidc.ciba.grant.enabled")
                        || attribute(client, "standard.token.exchange.enabled"))
                .extracting(client -> client.path("clientId").asString())
                .isEmpty();
    }

    @Test
    void noRedirectUriHasAWildcard() {
        List<String> wildcards = new ArrayList<>();
        for (JsonNode client : enabledClients()) {
            for (JsonNode uri : client.path("redirectUris")) {
                if (uri.asString().contains("*")) {
                    wildcards.add(client.path("clientId").asString() + " " + uri.asString());
                }
            }
        }
        assertThat(wildcards).isEmpty();
    }

    @Test
    void builtInClientsAreOff() {
        assertThat(enabledClients())
                .extracting(client -> client.path("clientId").asString())
                .doesNotContainAnyElementsOf(BUILT_IN_CLIENTS_OFF);
    }

    /**
     * Keycloak puts {@code offline_access} in every realm's default role and puts it back on import, so no client
     * may offer the scope that turns the role into an offline token.
     */
    @Test
    void noClientOffersOfflineTokens() {
        assertThat(enabledClients())
                .filteredOn(client -> contains(client.path("defaultClientScopes"), "offline_access")
                        || contains(client.path("optionalClientScopes"), "offline_access"))
                .extracting(client -> client.path("clientId").asString())
                .isEmpty();
    }

    @Test
    void theDefaultRoleGrantsNoAccountManagement() {
        JsonNode defaultRole = role(realm.path("defaultRole").path("name").asString());
        assertThat(defaultRole.path("composites").path("client").isEmpty())
                .as("client roles such as manage-account in %s", defaultRole)
                .isTrue();
    }

    @Test
    void onlyPasskeyRegistrationIsARequiredActionUsersCanStart() {
        assertThat(realm.path("requiredActions"))
                .filteredOn(action -> action.path("enabled").asBoolean())
                .extracting(action -> action.path("alias").asString())
                .containsExactly("webauthn-register-passwordless");
    }

    @Test
    void theOrganizationClaimHoldsOnlyAliases() {
        JsonNode mappers = clientScope("organization").path("protocolMappers");
        assertThat(mappers).hasSize(1);
        JsonNode config = mappers.path(0).path("config");
        assertThat(mappers.path(0).path("protocolMapper").asString()).isEqualTo("oidc-organization-membership-mapper");
        assertThat(config.path("addOrganizationId").asBoolean()).isFalse();
        assertThat(config.path("addOrganizationAttributes").asBoolean()).isFalse();
        assertThat(config.path("multivalued").asBoolean()).isTrue();
    }

    @Test
    void theBackofficeGetsAccessTokensForThisApi() {
        JsonNode bff = client(TestKeycloak.CLIENT_ID);
        assertThat(bff.path("enabled").asBoolean()).isTrue();
        assertThat(bff.path("standardFlowEnabled").asBoolean()).isTrue();
        assertThat(bff.path("fullScopeAllowed").asBoolean()).isFalse();
        assertThat(attributeValue(bff, "access.token.header.type.rfc9068")).isEqualTo("true");
        assertThat(attributeValue(bff, "pkce.code.challenge.method")).isEqualTo("S256");
        assertThat(bff.path("defaultClientScopes"))
                .extracting(JsonNode::asString)
                .containsExactly("basic");
        assertThat(bff.path("optionalClientScopes"))
                .extracting(JsonNode::asString)
                .containsExactly("organization");
        assertThat(bff.path("protocolMappers"))
                .extracting(mapper -> mapper.path("protocolMapper").asString()
                        + " "
                        + mapper.path("config").path("included.custom.audience").asString()
                        + mapper.path("config").path("claim.value").asString())
                .containsExactlyInAnyOrder(
                        "oidc-audience-mapper counthouse-api", "oidc-hardcoded-claim-mapper " + TestKeycloak.CLIENT_ID);
    }

    /** The keys' outage window covers the tokens issued before an outage only if they live 5 minutes. */
    @Test
    void accessTokensLiveFiveMinutes() {
        assertThat(realm.path("accessTokenLifespan").asInt()).isEqualTo(300);
        assertThat(attributeValue(client(TestKeycloak.CLIENT_ID), "access.token.lifespan"))
                .isEmpty();
    }

    @Test
    void theFileHoldsNoKeysOrSecrets() {
        JsonNode file = TestKeycloak.realmFile();
        assertThat(file.path("components").has("org.keycloak.keys.KeyProvider"))
                .as("key providers")
                .isFalse();
        assertThat(file.findValues("secret")).as("client secrets").isEmpty();
        assertThat(file.findValues("privateKey")).as("private keys").isEmpty();
        assertThat(file.findValues("credentials")).as("credentials").isEmpty();
        assertThat(file.path("users").isMissingNode()).as("users").isTrue();
    }

    private List<JsonNode> enabledClients() {
        List<JsonNode> clients = new ArrayList<>();
        realm.path("clients").forEach(client -> {
            if (client.path("enabled").asBoolean()) {
                clients.add(client);
            }
        });
        return clients;
    }

    private JsonNode client(String clientId) {
        for (JsonNode client : realm.path("clients")) {
            if (clientId.equals(client.path("clientId").asString())) {
                return client;
            }
        }
        throw new AssertionError("No client " + clientId);
    }

    private JsonNode clientScope(String name) {
        for (JsonNode scope : realm.path("clientScopes")) {
            if (name.equals(scope.path("name").asString())) {
                return scope;
            }
        }
        throw new AssertionError("No client scope " + name);
    }

    private JsonNode role(String name) {
        for (JsonNode role : realm.path("roles").path("realm")) {
            if (name.equals(role.path("name").asString())) {
                return role;
            }
        }
        throw new AssertionError("No realm role " + name);
    }

    private static boolean attribute(JsonNode client, String name) {
        return "true".equals(attributeValue(client, name));
    }

    private static String attributeValue(JsonNode client, String name) {
        return client.path("attributes").path(name).asString();
    }

    private static boolean contains(JsonNode values, String value) {
        for (JsonNode element : values) {
            if (value.equals(element.asString())) {
                return true;
            }
        }
        return false;
    }
}

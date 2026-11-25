package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.counthouse.api.TestcontainersConfiguration.MigratorDatabase;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checks over every operation in the contract, so an operation added later is covered without a test of its own:
 * each needs a token and a login in the token's tenant, none is public unless allowed below, and each registers how to
 * check that another tenant cannot reach its rows, or why it reaches none.
 */
@ApiTest
class ContractSecurityTests {

    /** Operations the contract may mark public ({@code security: []}), by operation id. */
    static final Set<String> PUBLIC_OPERATIONS = Set.of();

    /**
     * How to check tenant isolation, by operation id. Every operation in the contract has a case here or an
     * exemption below: a case seeds rows in one tenant and calls the operation with another tenant's valid token,
     * which must answer 404 for one of those rows by id, or a body without their ids.
     */
    static final Map<String, IsolationCase> ISOLATION_CASES = Map.of();

    /** Operations that reach no tenant rows but the caller's own, with the reason. */
    static final Map<String, String> ISOLATION_EXEMPT =
            Map.of("getMe", "answers only with the caller's own staff member and tenant");

    interface IsolationCase {

        /** Seeds rows of a tenant and returns the request to send and the ids that must not reach another tenant. */
        Seeded seed(MigratorDatabase migrator, UUID tenantId);

        /** {@code byId}: the request names one of the rows, so the answer must be 404. */
        record Seeded(
                HttpMethod method, String path, @Nullable String jsonBody, boolean byId, Set<String> ids) {}
    }

    static final OpenAPI CONTRACT = parse(ContractValidation.CONTRACT);

    @Autowired
    RestTestClient client;

    @Autowired
    MigratorDatabase migrator;

    final TestKeycloak keycloak = TestKeycloak.get();
    UUID tenantA;
    UUID tenantB;
    String tokenOfB;

    @BeforeEach
    void twoTenants() {
        tenantA = migrator.insertTenant("Clinic A");
        tenantB = migrator.insertTenant("Clinic B");
        String subject = UUID.randomUUID().toString();
        migrator.insertStaff(tenantB, keycloak.issuer(), subject);
        tokenOfB = keycloak.sign(keycloak.claims(tenantB, subject).build());
    }

    @Test
    void everyOperationNeedsAToken() {
        for (Call call : calls(CONTRACT)) {
            client.method(call.method())
                    .uri(call.path())
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }
    }

    @Test
    void everyOperationNeedsALoginInTheTokensTenant() {
        String subjectOfA = UUID.randomUUID().toString();
        migrator.insertStaff(tenantA, keycloak.issuer(), subjectOfA);
        String unknownLogin = keycloak.sign(
                keycloak.claims(tenantB, UUID.randomUUID().toString()).build());
        String loginOfAnotherTenant =
                keycloak.sign(keycloak.claims(tenantB, subjectOfA).build());

        for (Call call : calls(CONTRACT)) {
            for (String token : List.of(unknownLogin, loginOfAnotherTenant)) {
                client.method(call.method())
                        .uri(call.path())
                        .headers(h -> h.setBearerAuth(token))
                        .exchange()
                        .expectStatus()
                        .isUnauthorized();
            }
        }
    }

    @Test
    void noOperationIsPublicUnlessAllowed() {
        assertThat(publicOperations(CONTRACT)).isSubsetOf(PUBLIC_OPERATIONS);
    }

    @Test
    void anotherTenantCannotReachRows() {
        assertThat(operationsWithoutAnIsolationCase(CONTRACT)).isEmpty();

        ISOLATION_CASES.forEach((operationId, isolationCase) -> {
            var seeded = isolationCase.seed(migrator, tenantA);
            var request = client.method(seeded.method())
                    .uri(seeded.path())
                    .headers(h -> h.setBearerAuth(tokenOfB))
                    .contentType(MediaType.APPLICATION_JSON);
            var result = (seeded.jsonBody() == null ? request : request.body(seeded.jsonBody()))
                    .exchange()
                    .returnResult(String.class);
            if (seeded.byId()) {
                assertThat(result.getStatus()).as(operationId).isEqualTo(HttpStatus.NOT_FOUND);
            } else {
                assertThat(result.getResponseBody()).as(operationId).doesNotContain(seeded.ids());
            }
        });
    }

    /** The checks above find what they look for, on a contract made to break them. */
    @Test
    void checksFindPublicAndUnregisteredOperations() {
        OpenAPI broken =
                new OpenAPIV3Parser().readContents("""
                openapi: 3.0.3
                info: {title: t, version: "1"}
                security: [{bearerAuth: []}]
                paths:
                  /things:
                    get:
                      operationId: listThings
                      responses:
                        "200":
                          description: ok
                          content:
                            application/json:
                              schema: {type: object, properties: {items: {type: array, items: {type: string}}}}
                  /things/{id}:
                    get:
                      operationId: getThing
                      parameters: [{name: id, in: path, required: true, schema: {type: string}}]
                      responses: {"200": {description: ok}}
                  /status:
                    get:
                      operationId: getStatus
                      security: []
                      responses: {"200": {description: ok}}
                components:
                  securitySchemes:
                    bearerAuth: {type: http, scheme: bearer}
                """, null, resolveFully()).getOpenAPI();

        assertThat(publicOperations(broken)).containsExactly("getStatus");
        assertThat(operationsWithoutAnIsolationCase(broken))
                .containsExactlyInAnyOrder("listThings", "getThing", "getStatus");
        assertThat(calls(broken)).extracting(Call::path).contains("/things/" + PLACEHOLDER_ID);
    }

    static final UUID PLACEHOLDER_ID = UUID.fromString("019a0000-0000-7000-8000-00000000abcd");

    record Call(String operationId, HttpMethod method, String path) {}

    static List<Call> calls(OpenAPI contract) {
        List<Call> calls = new ArrayList<>();
        contract.getPaths()
                .forEach((path, item) -> item.readOperationsMap()
                        .forEach((method, operation) -> calls.add(new Call(
                                operation.getOperationId(),
                                HttpMethod.valueOf(method.name()),
                                path.replaceAll("\\{[^}]+}", PLACEHOLDER_ID.toString())))));
        return calls;
    }

    static List<String> publicOperations(OpenAPI contract) {
        List<String> open = new ArrayList<>();
        forEachOperation(contract, (path, operation) -> {
            if (operation.getSecurity() != null && operation.getSecurity().isEmpty()) {
                open.add(operation.getOperationId());
            }
        });
        return open;
    }

    static List<String> operationsWithoutAnIsolationCase(OpenAPI contract) {
        List<String> missing = new ArrayList<>();
        forEachOperation(contract, (path, operation) -> {
            String id = operation.getOperationId();
            if (!ISOLATION_CASES.containsKey(id) && !ISOLATION_EXEMPT.containsKey(id)) {
                missing.add(id);
            }
        });
        return missing;
    }

    private static void forEachOperation(OpenAPI contract, java.util.function.BiConsumer<String, Operation> action) {
        contract.getPaths().forEach((path, item) -> {
            for (Operation operation : item.readOperationsMap().values()) {
                action.accept(path, operation);
            }
        });
    }

    private static OpenAPI parse(String location) {
        return new OpenAPIV3Parser().read(location, null, resolveFully());
    }

    private static ParseOptions resolveFully() {
        var options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        return options;
    }
}

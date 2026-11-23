package io.github.counthouse.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.counthouse.api.tenancy.TenantScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Row-level security as the API's role sees it. Runs outside test transactions, on a pool of one connection so that
 * reuse of a connection is certain.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
class RowLevelSecurityTests {

    private static final String NOT_SET = "app.tenant_id is not set";

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    TestcontainersConfiguration.MigratorDatabase migrator;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void seedTwoTenants() {
        tenantA = migrator.insertTenant("Clinic A");
        tenantB = migrator.insertTenant("Clinic B");
        migrator.insertProbe(tenantA, "a");
        migrator.insertProbe(tenantB, "b");
    }

    @Test
    void apiRoleIsNeitherOwnerNorSuperuserNorBypassesRls() {
        var flags = jdbc.sql("""
                        select r.rolsuper, r.rolbypassrls, pg_has_role(current_user, t.tableowner, 'member')
                        from pg_roles r, pg_tables t
                        where r.rolname = current_user and t.tablename = 'rls_probe'
                        """)
                .query((rs, n) -> List.of(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)))
                .single();

        assertThat(flags).containsOnly(false);
    }

    @Test
    void aTenantSeesOnlyItsOwnRows() {
        assertThat(asTenant(tenantA, "select distinct tenant_id from rls_probe"))
                .containsExactly(tenantA);
    }

    @Test
    void aTenantSeesOnlyItsOwnTenantRow() {
        assertThat(asTenant(tenantA, "select id from tenants")).containsExactly(tenantA);
    }

    @Test
    void queryOutsideTransactionFails() {
        assertThatThrownBy(this::countProbes).rootCause().hasMessageContaining(NOT_SET);
    }

    @Test
    void tenantDoesNotOutliveItsTransaction() {
        TenantScope.run(tenantA, () -> transaction.execute(status -> countProbes()));

        assertThatThrownBy(this::countProbes).rootCause().hasMessageContaining(NOT_SET);
    }

    @Test
    void reusedConnectionDoesNotKeepTheTenant() {
        int first = TenantScope.call(tenantA, () -> transaction.execute(status -> backendPid()));

        assertThatThrownBy(this::countProbes).rootCause().hasMessageContaining(NOT_SET);
        assertThat(backendPid()).isEqualTo(first);
    }

    @Test
    void sessionLevelTenantIsClearedOnTheNextCheckout() {
        // What an injected subquery could do: set the tenant for the whole session.
        int first = TenantScope.call(
                tenantA,
                () -> transaction.execute(status -> {
                    jdbc.sql("select set_config('app.tenant_id', ?, false)")
                            .param(tenantB.toString())
                            .query(String.class)
                            .single();
                    return backendPid();
                }));

        assertThatThrownBy(this::countProbes).rootCause().hasMessageContaining(NOT_SET);
        assertThatThrownBy(this::countTenants).rootCause().hasMessageContaining(NOT_SET);
        assertThat(backendPid()).isEqualTo(first);
    }

    @Test
    void insertIntoAnotherTenantIsRejected() {
        assertThatThrownBy(() -> TenantScope.run(
                        tenantA,
                        () -> transaction.executeWithoutResult(
                                status -> jdbc.sql("insert into rls_probe (tenant_id, label) values (?, 'intruder')")
                                        .param(tenantB)
                                        .update())))
                .rootCause()
                .hasMessageContaining("violates row-level security policy");
    }

    @Test
    void movingARowToAnotherTenantIsRejected() {
        assertThatThrownBy(() -> TenantScope.run(
                        tenantA,
                        () -> transaction.executeWithoutResult(
                                status -> jdbc.sql("update rls_probe set tenant_id = ? where tenant_id = ?")
                                        .params(tenantB, tenantA)
                                        .update())))
                .rootCause()
                .hasMessageContaining("violates row-level security policy");
    }

    @Test
    void apiRoleCannotDeleteWithoutAGrant() {
        assertThatThrownBy(() -> TenantScope.run(
                        tenantA,
                        () -> transaction.executeWithoutResult(
                                status -> jdbc.sql("delete from rls_probe where tenant_id = ?")
                                        .param(tenantA)
                                        .update())))
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @Test
    void apiRoleCannotTurnRowLevelSecurityOff() {
        assertThatThrownBy(() -> jdbc.sql("alter table rls_probe disable row level security")
                        .update())
                .rootCause()
                .hasMessageContaining("must be owner");
        assertThatThrownBy(() -> TenantScope.run(
                        tenantA,
                        () -> transaction.executeWithoutResult(status -> {
                            jdbc.sql("set local row_security = off").update();
                            countProbes();
                        })))
                .rootCause()
                .hasMessageContaining("row-level security");
    }

    @Test
    void ownerWithoutTenantCannotReadTenantData() {
        assertThatThrownBy(() -> migrator.jdbc()
                        .sql("select count(*) from rls_probe")
                        .query(Long.class)
                        .single())
                .rootCause()
                .hasMessageContaining(NOT_SET);
    }

    @Test
    void apiRoleCannotCreateTemporaryTables() {
        assertThatThrownBy(() -> TenantScope.run(
                        tenantA,
                        () -> transaction.executeWithoutResult(
                                status -> jdbc.sql("create temp table rls_probe (id uuid, tenant_id uuid, label text)")
                                        .update())))
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @Test
    void foreignKeyCannotPointAtAnotherTenantsRow() {
        UUID otherTenantsProbe = TenantScope.call(
                tenantB,
                () -> transaction.execute(status ->
                        jdbc.sql("select id from rls_probe").query(UUID.class).single()));

        assertThatThrownBy(() -> TenantScope.run(
                        tenantA,
                        () -> transaction.executeWithoutResult(
                                status -> jdbc.sql("insert into rls_probe_child (tenant_id, probe_id) values (?, ?)")
                                        .params(tenantA, otherTenantsProbe)
                                        .update())))
                .rootCause()
                .hasMessageContaining("violates foreign key constraint");
    }

    @Test
    void btreeGistIsInstalled() {
        assertThat(jdbc.sql("select count(*) from pg_extension where extname = 'btree_gist'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    private List<UUID> asTenant(UUID tenantId, String sql) {
        return TenantScope.call(
                tenantId,
                () -> transaction.execute(
                        status -> jdbc.sql(sql).query(UUID.class).list()));
    }

    private long countProbes() {
        return jdbc.sql("select count(*) from rls_probe").query(Long.class).single();
    }

    private long countTenants() {
        return jdbc.sql("select count(*) from tenants").query(Long.class).single();
    }

    private int backendPid() {
        return jdbc.sql("select pg_backend_pid()").query(Integer.class).single();
    }
}

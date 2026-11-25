package io.github.counthouse.api;

import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL with the production role split: Flyway runs as the migrator, the API as a role that owns nothing. Not a
 * {@code @ServiceConnection}, which would hand the container's superuser to both. Tests that start a transaction enter
 * a {@code TenantScope} first; the transaction manager refuses one without it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    PostgreSQLContainer postgresContainer() {
        // Same image as the migrations job in ci.yml; the workflows job checks they match.
        return new PostgreSQLContainer(DockerImageName.parse(
                                "postgres:18.7@sha256:f5cbc081c25690710f3fc6f4c61a7565382bae01ca0d0f2028a53d7926b85a20")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("counthouse")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("db/roles.sql"), "/docker-entrypoint-initdb.d/roles.sql");
    }

    @Bean
    DynamicPropertyRegistrar databaseRoles(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "counthouse_app");
            registry.add("spring.datasource.password", () -> "app");
            registry.add("spring.flyway.user", () -> "counthouse_migrator");
            registry.add("spring.flyway.password", () -> "migrator");
            registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/test-migration");
        };
    }

    @Bean
    MigratorDatabase migratorDatabase(PostgreSQLContainer postgres) {
        return new MigratorDatabase(JdbcClient.create(
                new SingleConnectionDataSource(postgres.getJdbcUrl(), "counthouse_migrator", "migrator", true)));
    }

    /** A connection as the migrator, which owns the tables, provisions tenants and seeds test data. */
    public record MigratorDatabase(JdbcClient jdbc) {

        public UUID insertTenant(String name) {
            return jdbc.sql("""
                            insert into tenants (name, time_zone, locale)
                            values (?, 'Europe/Berlin', 'es')
                            returning id
                            """).param(name).query(UUID.class).single();
        }

        public UUID insertStaffMember(UUID tenantId, String role, boolean active) {
            return asTenant(
                    tenantId,
                    () -> jdbc.sql("""
                            insert into staff_members (tenant_id, display_name, role, active)
                            values (?, 'Staff member', ?, ?)
                            returning id
                            """)
                            .params(tenantId, role, active)
                            .query(UUID.class)
                            .single());
        }

        public UUID insertLogin(UUID tenantId, String issuer, String subject, UUID staffMemberId) {
            return asTenant(
                    tenantId,
                    () -> jdbc.sql("""
                            insert into logins (tenant_id, issuer, subject, kind, staff_member_id)
                            values (?, ?, ?, 'PERSON', ?)
                            returning id
                            """)
                            .params(tenantId, issuer, subject, staffMemberId)
                            .query(UUID.class)
                            .single());
        }

        /** A tenant with an active admin whose login is the given Keycloak subject; returns the staff member's id. */
        public UUID insertStaff(UUID tenantId, String issuer, String subject) {
            UUID staffMemberId = insertStaffMember(tenantId, "ADMIN", true);
            insertLogin(tenantId, issuer, subject, staffMemberId);
            return staffMemberId;
        }

        public void setActive(UUID tenantId, UUID staffMemberId, boolean active) {
            asTenant(
                    tenantId,
                    () -> jdbc.sql("update staff_members set active = ? where id = ?")
                            .params(active, staffMemberId)
                            .update());
        }

        // Tenant tables are forced, so the owner also needs a tenant.
        public <T> T asTenant(UUID tenantId, java.util.function.Supplier<T> work) {
            setTenant(tenantId.toString());
            try {
                return work.get();
            } finally {
                setTenant("");
            }
        }

        /** Inserts a probe row; rls_probe is forced, so the owner also needs a tenant. */
        public void insertProbe(UUID tenantId, String label) {
            setTenant(tenantId.toString());
            try {
                jdbc.sql("insert into rls_probe (tenant_id, label) values (?, ?)")
                        .params(tenantId, label)
                        .update();
            } finally {
                setTenant("");
            }
        }

        private void setTenant(String tenant) {
            jdbc.sql("select set_config('app.tenant_id', ?, false)")
                    .param(tenant)
                    .query(String.class)
                    .single();
        }
    }
}

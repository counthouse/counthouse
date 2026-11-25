package io.github.counthouse.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.counthouse.api.TestKeycloak;
import io.github.counthouse.api.TestcontainersConfiguration;
import io.github.counthouse.api.TestcontainersConfiguration.MigratorDatabase;
import io.github.counthouse.api.tenancy.TenantScope;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Hibernate scopes the login lookup to the current tenant by itself ({@code @TenantId}), above row-level security, so
 * its SQL names the tenant for both tables.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        properties =
                "spring.jpa.properties.hibernate.session_factory.statement_inspector=io.github.counthouse.api.identity.TenantIdTests$Statements")
class TenantIdTests {

    public static class Statements implements StatementInspector {

        private static final long serialVersionUID = 1L;

        static final List<String> SEEN = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            SEEN.add(sql);
            return sql;
        }
    }

    @Autowired
    Logins logins;

    @Autowired
    MigratorDatabase migrator;

    @Test
    void theLoginLookupIsScopedToTheTenantByHibernate() {
        UUID tenant = migrator.insertTenant("Clinic");
        String subject = UUID.randomUUID().toString();
        UUID staffMember = migrator.insertStaff(tenant, TestKeycloak.get().issuer(), subject);
        Statements.SEEN.clear();

        var actor = TenantScope.call(
                tenant, () -> logins.findActivePerson(TestKeycloak.get().issuer(), subject));

        assertThat(actor).map(Actor::staffMemberId).contains(staffMember);
        assertThat(Statements.SEEN)
                .filteredOn(sql -> sql.contains("from logins"))
                .singleElement()
                .satisfies(sql -> assertThat(java.util.regex.Pattern.compile("\\w+\\.tenant_id\\s*=\\s*\\?")
                                .matcher(sql)
                                .results())
                        .as("tenant restrictions, one per table, in %s", sql)
                        .hasSize(2));
    }
}

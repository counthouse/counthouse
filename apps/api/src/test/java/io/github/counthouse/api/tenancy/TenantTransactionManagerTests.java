package io.github.counthouse.api.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.counthouse.api.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.transaction.default-timeout=7s")
class TenantTransactionManagerTests {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    EntityManager entityManager;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    DataSource dataSource;

    @Autowired
    TestcontainersConfiguration.MigratorDatabase migrator;

    @Autowired
    MeterRegistry meters;

    @Test
    void isTheOnlyTransactionManager() {
        assertThat(transactionManager).isInstanceOf(TenantTransactionManager.class);
    }

    @Test
    void transactionWithoutScopeIsRefusedBeforeTakingAConnection() {
        var acquired = meters.get("hikaricp.connections.acquire").timer();
        long before = acquired.count();

        assertThatThrownBy(() -> transaction.execute(status -> 1))
                .isInstanceOf(CannotCreateTransactionException.class)
                .hasMessageContaining("No tenant");
        assertThat(acquired.count()).isEqualTo(before);
    }

    @Test
    void tenantSettingEndsWithTheTransaction() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ((TenantTransactionManager) transactionManager)
                    .setTenant(connection, UUID.randomUUID().toString());
            connection.commit();
            connection.setAutoCommit(true);
            try (var result =
                    connection.createStatement().executeQuery("select current_setting('app.tenant_id', true)")) {
                result.next();
                assertThat(result.getString(1)).isNullOrEmpty();
            }
        }
    }

    @Test
    void jpaQueriesSeeTheTenant() {
        UUID tenant = migrator.insertTenant("Clinic JPA");

        Object seen = TenantScope.call(
                tenant,
                () -> transaction.execute(status -> entityManager
                        .createNativeQuery("select id from tenants")
                        .getSingleResult()));

        assertThat(seen).isEqualTo(tenant);
    }

    @Test
    void bootsTransactionPropertiesStillApply() {
        assertThat(((TenantTransactionManager) transactionManager).getDefaultTimeout())
                .isEqualTo(7);
    }

    @Test
    void failureWhileSettingTheTenantLeavesNothingBound() throws Exception {
        var failing = new TenantTransactionManager(entityManagerFactory) {
            private static final long serialVersionUID = 1L;

            @Override
            void setTenant(Connection connection, String tenant) throws SQLException {
                throw new SQLException("boom");
            }
        };
        failing.setDataSource(dataSource);
        failing.afterPropertiesSet();
        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();

        assertThatThrownBy(() ->
                        TenantScope.run(UUID.randomUUID(), () -> new TransactionTemplate(failing).execute(status -> 1)))
                .isInstanceOf(CannotCreateTransactionException.class);
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        assertThat(pool.getActiveConnections()).isZero();
    }
}

package io.github.counthouse.api.tenancy;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sets {@code app.tenant_id} for each new transaction, from {@link TenantScope}, and refuses a transaction outside a
 * tenant's scope before it takes a connection. The value is
 * transaction-local, so it ends with the transaction.
 */
class TenantTransactionManager extends JpaTransactionManager {

    private static final long serialVersionUID = 1L;

    TenantTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        if (!TenantScope.TENANT.isBound()) {
            throw new CannotCreateTransactionException("No tenant: run the work in TenantScope");
        }
        super.doBegin(transaction, definition);
        try {
            setTenant(
                    DataSourceUtils.getConnection(obtainDataSource()),
                    TenantScope.TENANT.get().toString());
        } catch (SQLException | RuntimeException e) {
            abortBegin(transaction);
            throw new CannotCreateTransactionException("Could not set the tenant of the transaction", e);
        }
    }

    void setTenant(Connection connection, String tenant) throws SQLException {
        try (var statement = connection.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenant);
            statement.execute();
        }
    }

    // super.doBegin has started the transaction and bound its resources to the thread; the caller never sees a status
    // for it, so roll it back and release it here.
    private void abortBegin(Object transaction) {
        var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        try {
            if (holder != null && holder.getEntityManager().getTransaction().isActive()) {
                holder.getEntityManager().getTransaction().rollback();
            }
        } finally {
            doCleanupAfterCompletion(transaction);
        }
    }

    private DataSource obtainDataSource() {
        var dataSource = getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("No DataSource to set the tenant on");
        }
        return dataSource;
    }
}

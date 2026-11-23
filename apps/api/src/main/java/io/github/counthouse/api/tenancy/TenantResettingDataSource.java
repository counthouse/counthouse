package io.github.counthouse.api.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Clears {@code app.tenant_id} on every connection taken from the pool, so a session-level value left on a connection
 * by a mistake never reaches a query outside a transaction. It is not an injection defence: injected SQL can set any
 * tenant inside its own transaction.
 */
class TenantResettingDataSource extends DelegatingDataSource {

    TenantResettingDataSource(DataSource pool) {
        super(pool);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return reset(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return reset(super.getConnection(username, password));
    }

    private static Connection reset(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("reset app.tenant_id");
            return connection;
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }
}

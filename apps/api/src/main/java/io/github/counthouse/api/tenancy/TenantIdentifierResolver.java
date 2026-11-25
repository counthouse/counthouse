package io.github.counthouse.api.tenancy;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Gives Hibernate the tenant of {@link TenantScope}, so entities with {@code @TenantId} are filtered and stamped with
 * it, above row-level security. Hibernate also opens sessions outside any request, such as when Spring Data checks
 * queries at startup, so outside a scope it answers the nil UUID, which no tenant has. A query run there still fails:
 * the transaction manager refuses it, and without a transaction the database's policies raise.
 */
class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    static final UUID NO_TENANT = new UUID(0, 0);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantScope.TENANT.isBound() ? TenantScope.TENANT.get() : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}

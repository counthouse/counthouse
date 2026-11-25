package io.github.counthouse.api.tenancy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the tenant of the current {@link TenantScope}. */
@Service
public class Tenants {

    private final TenantRows rows;

    Tenants(TenantRows rows) {
        this.rows = rows;
    }

    /** The current tenant; its row is visible only as that tenant, so a missing row is a provisioning error. */
    @Transactional(readOnly = true)
    public TenantSettings current() {
        TenantRow row = rows.findById(TenantScope.TENANT.get());
        if (row == null) {
            throw new IllegalStateException("The current tenant has no row");
        }
        return row.toSettings();
    }
}

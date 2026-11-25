package io.github.counthouse.api.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** A tenant as the API reads it; tenants are created by provisioning, never by the API. */
@Entity
@Table(name = "tenants")
@Immutable
class TenantRow {

    @Id
    private UUID id;

    private String name;

    @Column(name = "time_zone")
    private String timeZone;

    private String locale;

    protected TenantRow() {}

    TenantSettings toSettings() {
        return new TenantSettings(id, name, timeZone, locale);
    }
}

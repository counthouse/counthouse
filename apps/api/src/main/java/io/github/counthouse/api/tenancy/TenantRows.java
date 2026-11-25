package io.github.counthouse.api.tenancy;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.Repository;

interface TenantRows extends Repository<TenantRow, UUID> {

    @Nullable
    TenantRow findById(UUID id);
}

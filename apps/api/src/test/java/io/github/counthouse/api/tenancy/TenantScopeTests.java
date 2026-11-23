package io.github.counthouse.api.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class TenantScopeTests {

    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();

    @Test
    void switchingToAnotherTenantIsRefused() {
        assertThatThrownBy(() -> TenantScope.run(tenantA, () -> TenantScope.run(tenantB, () -> {})))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reenteringTheSameTenantIsAllowed() {
        assertThat(TenantScope.call(tenantA, () -> TenantScope.call(tenantA, TenantScope.TENANT::get)))
                .isEqualTo(tenantA);
    }

    @Test
    void aMissingTenantIsRejected() {
        assertThatThrownBy(() -> TenantScope.run(null, () -> {})).isInstanceOf(NullPointerException.class);
    }

    @Test
    void anotherThreadDoesNotSeeTheScope() {
        boolean bound = TenantScope.call(
                tenantA,
                () -> CompletableFuture.supplyAsync(TenantScope.TENANT::isBound).join());

        assertThat(bound).isFalse();
    }
}

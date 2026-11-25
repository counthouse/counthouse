package io.github.counthouse.api.tenancy;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The tenant that transactions started inside {@link #run} or {@link #call} belong to.
 *
 * <p>A scope cannot switch to another tenant: a transaction joined from inside would keep the outer tenant, so
 * switching is refused instead. A transaction needs a tenant: the transaction manager refuses to start one without it.
 * Executor and {@code @Async} threads do not inherit the scope, so work handed to them fails closed unless it enters
 * the scope again.
 */
public final class TenantScope {

    static final ScopedValue<UUID> TENANT = ScopedValue.newInstance();

    private TenantScope() {}

    public static <X extends Exception> void run(UUID tenantId, Work<X> work) throws X {
        requireSameOrUnbound(tenantId);
        ScopedValue.where(TENANT, tenantId).call(() -> {
            work.run();
            return null;
        });
    }

    public static <T> T call(UUID tenantId, Supplier<T> work) {
        requireSameOrUnbound(tenantId);
        return ScopedValue.where(TENANT, tenantId).call(work::get);
    }

    /** Work that may throw a checked exception, such as the rest of a servlet filter chain. */
    @FunctionalInterface
    public interface Work<X extends Exception> {
        void run() throws X;
    }

    private static void requireSameOrUnbound(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (TENANT.isBound() && !TENANT.get().equals(tenantId)) {
            throw new IllegalStateException("Already in the scope of another tenant");
        }
    }
}

package io.github.counthouse.api.tenancy;

import java.util.UUID;

/** A tenant's settings, as provisioning wrote them. */
public record TenantSettings(UUID id, String name, String timeZone, String locale) {}

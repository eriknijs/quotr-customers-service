package com.quotr.customers.tenant;

import java.util.UUID;

/**
 * The caller's tenant context for the current request, resolved by {@link TenantContextFilter}
 * against {@code quotr-tenant-service} before any controller runs (ADR-0001). Application code
 * depends only on this interface, never on the access token's own subject claim.
 */
public interface TenantResolver {

    UUID currentTenantId();

    UUID currentTenantMemberId();
}

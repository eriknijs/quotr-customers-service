package com.quotr.customers.tenant;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link TenantResolver} backed by a {@link ThreadLocal} that {@link TenantContextFilter}
 * populates for the lifetime of one request and always clears afterward, regardless of outcome.
 * A thread-local (rather than a Spring request-scoped bean) needs no assumption about request-
 * attribute propagation being configured -- it only relies on the servlet container invoking the
 * filter and the controller on the same thread, true for the standard synchronous MVC dispatch
 * this service uses.
 */
@Component
public class RequestTenantContext implements TenantResolver {

    private final ThreadLocal<UUID> tenantId = new ThreadLocal<>();
    private final ThreadLocal<UUID> tenantMemberId = new ThreadLocal<>();

    void set(UUID resolvedTenantId, UUID resolvedTenantMemberId) {
        tenantId.set(resolvedTenantId);
        tenantMemberId.set(resolvedTenantMemberId);
    }

    void clear() {
        tenantId.remove();
        tenantMemberId.remove();
    }

    @Override
    public UUID currentTenantId() {
        UUID value = tenantId.get();
        if (value == null) {
            throw new IllegalStateException("No tenant context has been resolved for the current request");
        }
        return value;
    }

    @Override
    public UUID currentTenantMemberId() {
        UUID value = tenantMemberId.get();
        if (value == null) {
            throw new IllegalStateException("No tenant context has been resolved for the current request");
        }
        return value;
    }
}

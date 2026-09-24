package com.quotr.customers.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quotr.customers.api.model.ErrorResponse;
import com.quotr.tenant.generated.api.TenantsApi;
import com.quotr.tenant.generated.model.TenantDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the caller's tenant context against {@code quotr-tenant-service} before any
 * controller runs (ADR-0001). Registered in {@code SecurityConfig} after JWT authentication has
 * populated the {@link SecurityContextHolder}, so the caller's bearer token is available to
 * forward (see {@code TenantApiClientConfig}'s interceptor).
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantsApi tenantsApi;
    private final RequestTenantContext tenantContext;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(TenantsApi tenantsApi, RequestTenantContext tenantContext, ObjectMapper objectMapper) {
        this.tenantsApi = tenantsApi;
        this.tenantContext = tenantContext;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            // No authenticated caller on this request (a permitAll path such as
            // /actuator/health, or an unauthenticated request Spring Security will reject
            // downstream) -- there is no bearer token to resolve a tenant with, and nothing
            // in this filter's own responsibility to enforce here either way.
            filterChain.doFilter(request, response);
            return;
        }

        TenantDTO tenant;
        try {
            tenant = tenantsApi.getTenant();
        } catch (HttpClientErrorException.NotFound exception) {
            // No tenant for this caller is an authorization failure from a tenant-scoped
            // resource server's perspective, not a missing-resource condition -- the
            // controller must never see this request.
            writeError(response, HttpStatus.FORBIDDEN, "NO_TENANT", "The authenticated caller has no tenant.");
            return;
        } catch (RestClientException exception) {
            // Unreachable, timed out, or any other non-404 failure (including a 5xx from
            // quotr-tenant-service itself) is a technical failure, not a functional "no
            // tenant" outcome -- mapped uniformly to 503 regardless of the specific cause.
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE, "TENANT_SERVICE_UNAVAILABLE",
                    "Tenant resolution is temporarily unavailable.");
            return;
        }

        try {
            tenantContext.set(tenant.getTenantId(), tenant.getTenantMemberId());
            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        ErrorResponse error = new ErrorResponse();
        error.setCode(code);
        error.setMessage(message);
        error.setDetails(List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}

package com.quotr.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quotr.customers.api.ServiceOnlyApi;
import com.quotr.customers.api.ServiceOnlyApiController;
import com.quotr.customers.persistence.CustomerEntity;
import com.quotr.customers.persistence.CustomerRepository;
import com.quotr.tenant.generated.api.TenantsApi;
import com.quotr.tenant.generated.model.TenantDTO;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ServiceOnlyApiSecurityIntegrationTest {

    private static final String SERVICE_TOKEN = "service-token";
    private static final String USER_TOKEN = "user-token";
    private static final String PATH = "/api/v1/tenants/{tenantId}/customers/{customerId}";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quotr_customers_service_only_test")
            .withUsername("quotr")
            .withPassword("quotr");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CustomerRepository repository;

    @MockBean
    TenantsApi tenantsApi;

    @MockBean
    JwtDecoder jwtDecoder;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenantId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private UUID customerId;
    private UUID deletedCustomerId;
    private UUID otherTenantCustomerId;

    @BeforeEach
    void setUp() {
        Jwt serviceJwt = jwt(SERVICE_TOKEN, "svc-quotes", Map.of("role", "service", "service", "quotes"));
        Jwt userJwt = jwt(USER_TOKEN, memberId.toString(), Map.of());
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (SERVICE_TOKEN.equals(token)) {
                return serviceJwt;
            }
            if (USER_TOKEN.equals(token)) {
                return userJwt;
            }
            throw new BadJwtException("invalid");
        });
        when(tenantsApi.getTenant()).thenReturn(new TenantDTO(memberId).tenantId(tenantId));

        repository.deleteAll();
        customerId = save(tenantId, "Acme Ltd", false);
        deletedCustomerId = save(tenantId, "Gone Ltd", true);
        otherTenantCustomerId = save(otherTenantId, "Other Ltd", false);
    }

    private UUID save(UUID tenant, String name, boolean deleted) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenant);
        entity.setTenantMemberId(memberId);
        entity.setName(name);
        entity.setEmail("customer@example.test");
        if (deleted) {
            entity.setDeletedAt(Instant.now());
        }
        return repository.save(entity).getId();
    }

    private static Jwt jwt(String token, String subject, Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void everyServiceOnlyOperationCarriesTheServiceAuthorizationCheck() {
        List<Method> operations = Arrays.asList(ServiceOnlyApi.class.getDeclaredMethods());
        assertThat(operations).isNotEmpty();
        for (Method operation : operations) {
            Method implementation;
            try {
                implementation = ServiceOnlyApiController.class.getMethod(operation.getName(), operation.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new AssertionError("ServiceOnlyApi." + operation.getName() + " has no implementing method", e);
            }
            PreAuthorize check = implementation.getAnnotation(PreAuthorize.class);
            assertThat(check)
                    .as("ServiceOnlyApiController.%s must carry @PreAuthorize", operation.getName())
                    .isNotNull();
            assertThat(check.value()).isEqualTo("hasRole('SERVICE')");
        }
    }

    @Test
    void serviceCallerReadsACustomerOfTheTenantWithoutATenantBeingResolvedForIt() throws Exception {
        mockMvc.perform(get(PATH, tenantId, customerId).header("Authorization", bearer(SERVICE_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId.toString()))
                .andExpect(jsonPath("$.name").value("Acme Ltd"))
                .andExpect(jsonPath("$.email").value("customer@example.test"));

        verifyNoInteractions(tenantsApi);
    }

    @Test
    void serviceCallerGetsNotFoundForACustomerOfAnotherTenant() throws Exception {
        mockMvc.perform(get(PATH, tenantId, otherTenantCustomerId).header("Authorization", bearer(SERVICE_TOKEN)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH, otherTenantId, customerId).header("Authorization", bearer(SERVICE_TOKEN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceCallerGetsNotFoundForAnUnknownCustomer() throws Exception {
        mockMvc.perform(get(PATH, tenantId, UUID.randomUUID()).header("Authorization", bearer(SERVICE_TOKEN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceCallerGetsNotFoundForASoftDeletedCustomer() throws Exception {
        mockMvc.perform(get(PATH, tenantId, deletedCustomerId).header("Authorization", bearer(SERVICE_TOKEN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userTokenIsRefusedEvenForTheUsersOwnTenantAndCustomer() throws Exception {
        mockMvc.perform(get(PATH, tenantId, customerId).header("Authorization", bearer(USER_TOKEN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH, otherTenantId, otherTenantCustomerId).header("Authorization", bearer(USER_TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noTokenOrInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(PATH, tenantId, customerId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH, tenantId, customerId).header("Authorization", bearer("garbage")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endUserTenantResolutionStaysInPlaceForOrdinaryOperations() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", customerId).header("Authorization", bearer(USER_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Ltd"));

        verify(tenantsApi).getTenant();
    }

    @Test
    void serviceRoleDoesNotResolveATenantForOrdinaryOperationsEither() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(
                get("/api/v1/customers/{id}", customerId).header("Authorization", bearer(SERVICE_TOKEN))))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant context");

        verifyNoInteractions(tenantsApi);
    }
}

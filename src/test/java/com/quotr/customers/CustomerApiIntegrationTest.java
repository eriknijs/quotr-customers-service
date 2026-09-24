package com.quotr.customers;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quotr.customers.persistence.CustomerRepository;
import com.quotr.tenant.generated.api.TenantsApi;
import com.quotr.tenant.generated.model.TenantDTO;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CustomerApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quotr_customers_api_test")
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

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @BeforeEach
    void stubTenantResolutionFromTheCallersOwnBearerToken() {
        // Every existing "owner" fixture is already a UUID-formatted JWT subject, so the
        // member id can reuse it directly (keeping $.ownerUserId assertions unchanged); the
        // tenant id is a distinct, deterministic derivation so two different subjects land in
        // two different tenants, preserving this suite's existing isolation semantics.
        when(tenantsApi.getTenant()).thenAnswer(invocation -> {
            String subject = currentJwtSubject();
            return new TenantDTO(UUID.fromString(subject))
                    .tenantId(UUID.nameUUIDFromBytes(("tenant:" + subject).getBytes(StandardCharsets.UTF_8)));
        });
    }

    private static String currentJwtSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ((JwtAuthenticationToken) authentication).getToken().getSubject();
    }

    @Test
    void createListSearchUpdateAndSoftDeleteUseGeneratedContractSurfaceAndResolvedTenant() throws Exception {
        String owner = "11111111-1111-1111-1111-111111111111";
        String otherOwner = "22222222-2222-2222-2222-222222222222";

        String customerId = createCustomer(owner, "Acme Ltd", "ops@acme.example", "Main Street 1", "1000 AA", "Amsterdam", "NL");
        createCustomer(otherOwner, "Other Owner", "other@example.com", "Hidden Street 9", "3000 CC", "Rotterdam", "NL");

        mockMvc.perform(get("/api/v1/customers/{customerId}", customerId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.ownerUserId").value(owner))
                .andExpect(jsonPath("$.name").value("Acme Ltd"))
                .andExpect(jsonPath("$.address.streetAddress").value("Main Street 1"));

        mockMvc.perform(get("/api/v1/customers").with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andExpect(jsonPath("$.customers[0].id").value(customerId))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/v1/customers?q=amster&page=0&size=10").with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andExpect(jsonPath("$.customers[0].id").value(customerId));

        mockMvc.perform(put("/api/v1/customers/{customerId}", customerId)
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Acme Updated",
                                  "email":"new@acme.example",
                                  "phoneNumber":"+31 20 1234567",
                                  "address":{"streetAddress":"Updated Street 2","postalCode":"1000 BB","city":"Utrecht","country":"NL"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Updated"))
                .andExpect(jsonPath("$.address.city").value("Utrecht"));

        mockMvc.perform(get("/api/v1/customers/{customerId}", customerId).with(jwt().jwt(jwt -> jwt.subject(otherOwner))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/customers/{customerId}", customerId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/customers/{customerId}", customerId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerListSearchSuppliesTenantScopedActiveAddressMatchesForDelegatedQuoteUse() throws Exception {
        String owner = "33333333-3333-3333-3333-333333333333";
        String otherOwner = "44444444-4444-4444-4444-444444444444";

        String matchingCustomerId = createCustomer(
                owner,
                "Address Backed Worksite",
                "worksite@example.com",
                "Orchid Lane 42",
                "ZX9 7QA",
                "Silverton",
                "Neverland");
        createCustomer(
                otherOwner,
                "Other Owner Same Address",
                "other-address@example.com",
                "Orchid Lane 42",
                "ZX9 7QA",
                "Silverton",
                "Neverland");
        String deletedCustomerId = createCustomer(
                owner,
                "Deleted Address Backed Worksite",
                "deleted-address@example.com",
                "Deleted Orchid Lane 99",
                "ZX9 7QA",
                "Silverton",
                "Neverland");
        mockMvc.perform(delete("/api/v1/customers/{customerId}", deletedCustomerId).with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isNoContent());

        assertSingleAddressSearchMatch(owner, "oRcHiD", matchingCustomerId);
        assertSingleAddressSearchMatch(owner, "zx9", matchingCustomerId);
        assertSingleAddressSearchMatch(owner, "silver", matchingCustomerId);
        assertSingleAddressSearchMatch(owner, "never", matchingCustomerId);
    }

    @Test
    void rejectsIncompleteAddressThroughApiValidation() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .with(jwt().jwt(jwt -> jwt.subject("11111111-1111-1111-1111-111111111111")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Incomplete",
                                  "address":{"streetAddress":"Main Street 1","city":"Amsterdam","country":"NL"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUSTOMER_VALIDATION_FAILED"));
    }

    private void assertSingleAddressSearchMatch(String owner, String query, String expectedCustomerId) throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .param("q", query)
                        .param("page", "0")
                        .param("size", "10")
                        .with(jwt().jwt(jwt -> jwt.subject(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers", hasSize(1)))
                .andExpect(jsonPath("$.customers[0].id").value(expectedCustomerId))
                .andExpect(jsonPath("$.customers[0].address.streetAddress").value("Orchid Lane 42"))
                .andExpect(jsonPath("$.customers[0].address.postalCode").value("ZX9 7QA"))
                .andExpect(jsonPath("$.customers[0].address.city").value("Silverton"))
                .andExpect(jsonPath("$.customers[0].address.country").value("Neverland"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    private String createCustomer(String owner, String name, String email, String streetAddress, String postalCode,
                                  String city, String country) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "email":"%s",
                                  "phoneNumber":"+31 20 000 0000",
                                  "address":{"streetAddress":"%s","postalCode":"%s","city":"%s","country":"%s"}
                                }
                                """.formatted(name, email, streetAddress, postalCode, city, country)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}

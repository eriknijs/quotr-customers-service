package com.quotr.customers;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quotr.customers.persistence.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createListSearchUpdateAndSoftDeleteUseGeneratedContractSurfaceAndAuthenticatedOwner() throws Exception {
        String owner = "11111111-1111-1111-1111-111111111111";
        String otherOwner = "22222222-2222-2222-2222-222222222222";

        String customerId = createCustomer(owner, "Acme Ltd", "ops@acme.example", "Main Street 1", "Amsterdam");
        createCustomer(otherOwner, "Other Owner", "other@example.com", "Hidden Street 9", "Rotterdam");

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

    private String createCustomer(String owner, String name, String email, String streetAddress, String city) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .with(jwt().jwt(jwt -> jwt.subject(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "email":"%s",
                                  "phoneNumber":"+31 20 000 0000",
                                  "address":{"streetAddress":"%s","postalCode":"1000 AA","city":"%s","country":"NL"}
                                }
                                """.formatted(name, email, streetAddress, city)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}

package com.quotr.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quotr.customers.domain.Address;
import com.quotr.customers.domain.Customer;
import com.quotr.customers.domain.CustomerChange;
import com.quotr.customers.domain.CustomerNotFoundException;
import com.quotr.customers.domain.CustomerValidationException;
import com.quotr.customers.persistence.CustomerRepository;
import com.quotr.customers.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CustomerServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quotr_customers_test")
            .withUsername("quotr")
            .withPassword("quotr");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CustomerService service;

    @Autowired
    CustomerRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsPersistsAndReadsOwnedCustomerWithCompleteAddress() {
        Customer created = service.create("owner-a", new CustomerChange(
                "Acme Ltd", "ops@acme.example", "+31 20 000 0000",
                new Address("Main Street 1", null, "1000 AA", "Amsterdam", "NL")));

        Customer found = service.get("owner-a", created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.ownerId()).isEqualTo("owner-a");
        assertThat(found.name()).isEqualTo("Acme Ltd");
        assertThat(found.email()).isEqualTo("ops@acme.example");
        assertThat(found.phoneNumber()).isEqualTo("+31 20 000 0000");
        assertThat(found.address().city()).isEqualTo("Amsterdam");
        assertThat(repository.findById(created.id())).isPresent();
    }

    @Test
    void enforcesOwnerIsolationForReadListSearchUpdateAndDelete() {
        Customer ownerCustomer = service.create("owner-a", new CustomerChange("Alpha Customer", "alpha@example.com", null, null));
        Customer otherCustomer = service.create("owner-b", new CustomerChange("Beta Customer", "beta@example.com", null, null));

        assertThatThrownBy(() -> service.get("owner-b", ownerCustomer.id())).isInstanceOf(CustomerNotFoundException.class);
        assertThat(service.list("owner-a")).extracting(Customer::id).containsExactly(ownerCustomer.id());
        assertThat(service.search("owner-a", "Beta")).isEmpty();

        assertThatThrownBy(() -> service.update("owner-a", otherCustomer.id(), new CustomerChange("Changed", null, null, null)))
                .isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> service.softDelete("owner-a", otherCustomer.id()))
                .isInstanceOf(CustomerNotFoundException.class);
        assertThat(service.get("owner-b", otherCustomer.id()).name()).isEqualTo("Beta Customer");
    }

    @Test
    void rejectsMissingNameAndIncompleteAddress() {
        assertThatThrownBy(() -> service.create("owner-a", new CustomerChange(" ", null, null, null)))
                .isInstanceOf(CustomerValidationException.class)
                .hasMessageContaining("name");

        assertThatThrownBy(() -> service.create("owner-a", new CustomerChange(
                "Incomplete", null, null, new Address("Main Street 1", null, null, "Amsterdam", "NL"))))
                .isInstanceOf(CustomerValidationException.class)
                .hasMessageContaining("Address");
    }

    @Test
    void updateReplacesSupportedCustomerFields() {
        Customer created = service.create("owner-a", new CustomerChange("Old Name", "old@example.com", "123", null));

        Customer updated = service.update("owner-a", created.id(), new CustomerChange(
                "New Name", "new@example.com", "456", new Address("New Street", null, "2000 BB", "Rotterdam", "NL")));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.email()).isEqualTo("new@example.com");
        assertThat(updated.phoneNumber()).isEqualTo("456");
        assertThat(updated.address().addressLine1()).isEqualTo("New Street");
        assertThat(service.get("owner-a", created.id()).name()).isEqualTo("New Name");
    }

    @Test
    void softDeletedCustomersAreExcludedFromNormalDetailListAndSearch() {
        Customer kept = service.create("owner-a", new CustomerChange("Kept Customer", "kept@example.com", null, null));
        Customer deleted = service.create("owner-a", new CustomerChange("Deleted Customer", "deleted@example.com", null, null));

        service.softDelete("owner-a", deleted.id());

        assertThatThrownBy(() -> service.get("owner-a", deleted.id())).isInstanceOf(CustomerNotFoundException.class);
        assertThat(service.list("owner-a")).extracting(Customer::id).contains(kept.id()).doesNotContain(deleted.id());
        assertThat(service.search("owner-a", "Deleted")).isEmpty();
        assertThat(repository.findById(deleted.id())).get().extracting("deletedAt").isNotNull();
    }

    @Test
    void searchIsCaseInsensitivePartialAndDoesNotMatchPhoneNumber() {
        Customer nameMatch = service.create("owner-a", new CustomerChange("Northwind Traders", "contact@northwind.example", "+31-SEARCH-ME", null));
        Customer emailMatch = service.create("owner-a", new CustomerChange("Email Match", "hello@contoso.example", "+31-000", null));
        Customer addressMatch = service.create("owner-a", new CustomerChange(
                "Address Match", null, "+31-111", new Address("Canal Road 5", null, "1010 ZZ", "Utrecht", "NL")));
        Customer phoneOnly = service.create("owner-a", new CustomerChange("Phone Only", null, "needle-phone", null));

        assertThat(service.search("owner-a", "wind")).extracting(Customer::id).containsExactly(nameMatch.id());
        assertThat(service.search("owner-a", "CONTOSO")).extracting(Customer::id).containsExactly(emailMatch.id());
        assertThat(service.search("owner-a", "canal")).extracting(Customer::id).containsExactly(addressMatch.id());
        assertThat(service.search("owner-a", "1010")).extracting(Customer::id).containsExactly(addressMatch.id());
        assertThat(service.search("owner-a", "trecht")).extracting(Customer::id).containsExactly(addressMatch.id());
        assertThat(service.search("owner-a", "nl")).extracting(Customer::id).containsExactly(addressMatch.id());
        assertThat(service.search("owner-a", "needle-phone")).extracting(Customer::id).doesNotContain(phoneOnly.id());
    }
}

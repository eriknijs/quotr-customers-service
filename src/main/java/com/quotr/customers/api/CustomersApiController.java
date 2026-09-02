package com.quotr.customers.api;

import com.quotr.customers.api.model.Customer;
import com.quotr.customers.api.model.CustomerPageResponse;
import com.quotr.customers.api.model.CustomerWriteRequest;
import com.quotr.customers.mapping.CustomerTransportMapper;
import com.quotr.customers.security.AuthenticatedOwnerProvider;
import com.quotr.customers.service.CustomerService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomersApiController implements CustomersApi {

    private static final Sort CUSTOMER_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("createdAt"));

    private final CustomerService customerService;
    private final AuthenticatedOwnerProvider ownerProvider;
    private final CustomerTransportMapper transportMapper;

    public CustomersApiController(CustomerService customerService,
                                  AuthenticatedOwnerProvider ownerProvider,
                                  CustomerTransportMapper transportMapper) {
        this.customerService = customerService;
        this.ownerProvider = ownerProvider;
        this.transportMapper = transportMapper;
    }

    @Override
    public ResponseEntity<Customer> createCustomer(@Valid CustomerWriteRequest customerWriteRequest) {
        com.quotr.customers.domain.Customer created = customerService.create(
                ownerProvider.currentOwnerId(), transportMapper.toChange(customerWriteRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(transportMapper.toTransport(created));
    }

    @Override
    public ResponseEntity<Void> deleteCustomer(UUID customerId) {
        customerService.softDelete(ownerProvider.currentOwnerId(), customerId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Customer> getCustomer(UUID customerId) {
        return ResponseEntity.ok(transportMapper.toTransport(
                customerService.get(ownerProvider.currentOwnerId(), customerId)));
    }

    @Override
    public ResponseEntity<CustomerPageResponse> listCustomers(String q, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size, CUSTOMER_SORT);
        return ResponseEntity.ok(transportMapper.toPageResponse(
                customerService.listOrSearch(ownerProvider.currentOwnerId(), q, pageable)));
    }

    @Override
    public ResponseEntity<Customer> updateCustomer(UUID customerId, @Valid CustomerWriteRequest customerWriteRequest) {
        return ResponseEntity.ok(transportMapper.toTransport(
                customerService.update(ownerProvider.currentOwnerId(), customerId, transportMapper.toChange(customerWriteRequest))));
    }
}

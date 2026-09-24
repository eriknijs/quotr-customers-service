package com.quotr.customers.api;

import com.quotr.customers.api.model.Customer;
import com.quotr.customers.api.model.CustomerPageResponse;
import com.quotr.customers.api.model.CustomerWriteRequest;
import com.quotr.customers.mapping.CustomerTransportMapper;
import com.quotr.customers.service.CustomerService;
import com.quotr.customers.tenant.TenantResolver;
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
    private final TenantResolver tenantResolver;
    private final CustomerTransportMapper transportMapper;

    public CustomersApiController(CustomerService customerService,
                                  TenantResolver tenantResolver,
                                  CustomerTransportMapper transportMapper) {
        this.customerService = customerService;
        this.tenantResolver = tenantResolver;
        this.transportMapper = transportMapper;
    }

    @Override
    public ResponseEntity<Customer> createCustomer(@Valid CustomerWriteRequest customerWriteRequest) {
        com.quotr.customers.domain.Customer created = customerService.create(
                tenantResolver.currentTenantId(), tenantResolver.currentTenantMemberId(),
                transportMapper.toChange(customerWriteRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(transportMapper.toTransport(created));
    }

    @Override
    public ResponseEntity<Void> deleteCustomer(UUID customerId) {
        customerService.softDelete(tenantResolver.currentTenantId(), tenantResolver.currentTenantMemberId(), customerId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Customer> getCustomer(UUID customerId) {
        return ResponseEntity.ok(transportMapper.toTransport(
                customerService.get(tenantResolver.currentTenantId(), customerId)));
    }

    @Override
    public ResponseEntity<CustomerPageResponse> listCustomers(String q, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size, CUSTOMER_SORT);
        return ResponseEntity.ok(transportMapper.toPageResponse(
                customerService.listOrSearch(tenantResolver.currentTenantId(), q, pageable)));
    }

    @Override
    public ResponseEntity<Customer> updateCustomer(UUID customerId, @Valid CustomerWriteRequest customerWriteRequest) {
        return ResponseEntity.ok(transportMapper.toTransport(
                customerService.update(tenantResolver.currentTenantId(), tenantResolver.currentTenantMemberId(),
                        customerId, transportMapper.toChange(customerWriteRequest))));
    }
}

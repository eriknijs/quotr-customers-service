package com.quotr.customers.api;

import com.quotr.customers.api.model.Customer;
import com.quotr.customers.mapping.CustomerTransportMapper;
import com.quotr.customers.service.CustomerService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceOnlyApiController implements ServiceOnlyApi {

    private final CustomerService customerService;
    private final CustomerTransportMapper transportMapper;

    public ServiceOnlyApiController(CustomerService customerService, CustomerTransportMapper transportMapper) {
        this.customerService = customerService;
        this.transportMapper = transportMapper;
    }

    @Override
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Customer> getCustomerByTenant(UUID tenantId, UUID customerId) {
        return ResponseEntity.ok(transportMapper.toTransport(customerService.get(tenantId, customerId)));
    }
}

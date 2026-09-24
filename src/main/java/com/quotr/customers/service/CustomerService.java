package com.quotr.customers.service;

import com.quotr.customers.domain.Address;
import com.quotr.customers.domain.Customer;
import com.quotr.customers.domain.CustomerChange;
import com.quotr.customers.domain.CustomerNotFoundException;
import com.quotr.customers.domain.CustomerValidationException;
import com.quotr.customers.mapping.CustomerEntityMapper;
import com.quotr.customers.persistence.CustomerEntity;
import com.quotr.customers.persistence.CustomerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final CustomerEntityMapper mapper;

    public CustomerService(CustomerRepository repository, CustomerEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public Customer create(UUID tenantId, UUID tenantMemberId, CustomerChange change) {
        validate(change);
        CustomerEntity entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(requireTenant(tenantId));
        entity.setTenantMemberId(requireTenantMember(tenantMemberId));
        apply(entity, change);
        return mapper.toDomain(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Customer get(UUID tenantId, UUID customerId) {
        return mapper.toDomain(findActiveOwned(tenantId, customerId));
    }

    @Transactional(readOnly = true)
    public List<Customer> list(UUID tenantId) {
        return repository.findByTenantIdAndDeletedAtIsNullOrderByNameAscCreatedAtAsc(requireTenant(tenantId))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Customer> list(UUID tenantId, Pageable pageable) {
        return repository.findByTenantIdAndDeletedAtIsNull(requireTenant(tenantId), pageable)
                .map(mapper::toDomain);
    }

    @Transactional(readOnly = true)
    public List<Customer> search(UUID tenantId, String query) {
        if (!StringUtils.hasText(query)) {
            return list(tenantId);
        }
        return repository.searchActiveOwned(requireTenant(tenantId), query.trim())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Customer> listOrSearch(UUID tenantId, String query, Pageable pageable) {
        if (!StringUtils.hasText(query)) {
            return list(tenantId, pageable);
        }
        return repository.searchActiveOwned(requireTenant(tenantId), query.trim(), pageable)
                .map(mapper::toDomain);
    }

    @Transactional
    public Customer update(UUID tenantId, UUID tenantMemberId, UUID customerId, CustomerChange change) {
        validate(change);
        CustomerEntity entity = findActiveOwned(tenantId, customerId);
        entity.setTenantMemberId(requireTenantMember(tenantMemberId));
        apply(entity, change);
        return mapper.toDomain(repository.save(entity));
    }

    @Transactional
    public void softDelete(UUID tenantId, UUID tenantMemberId, UUID customerId) {
        CustomerEntity entity = findActiveOwned(tenantId, customerId);
        entity.setTenantMemberId(requireTenantMember(tenantMemberId));
        entity.setDeletedAt(Instant.now());
        repository.save(entity);
    }

    private CustomerEntity findActiveOwned(UUID tenantId, UUID customerId) {
        return repository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, requireTenant(tenantId))
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private static UUID requireTenant(UUID tenantId) {
        return Objects.requireNonNull(tenantId, "Tenant identity is required");
    }

    private static UUID requireTenantMember(UUID tenantMemberId) {
        return Objects.requireNonNull(tenantMemberId, "Tenant member identity is required");
    }

    private static void validate(CustomerChange change) {
        if (change == null || !StringUtils.hasText(change.name())) {
            throw new CustomerValidationException("Customer name is required");
        }
        validateAddress(change.address());
    }

    private static void validateAddress(Address address) {
        if (address == null) {
            return;
        }
        boolean anyAddressValue = StringUtils.hasText(address.addressLine1())
                || StringUtils.hasText(address.addressLine2())
                || StringUtils.hasText(address.postalCode())
                || StringUtils.hasText(address.city())
                || StringUtils.hasText(address.country());
        if (!anyAddressValue) {
            return;
        }
        if (!StringUtils.hasText(address.addressLine1())
                || !StringUtils.hasText(address.postalCode())
                || !StringUtils.hasText(address.city())
                || !StringUtils.hasText(address.country())) {
            throw new CustomerValidationException("Address must be complete when provided");
        }
    }

    private static void apply(CustomerEntity entity, CustomerChange change) {
        entity.setName(change.name().trim());
        entity.setEmail(trimToNull(change.email()));
        entity.setPhoneNumber(trimToNull(change.phoneNumber()));
        Address address = change.address();
        if (address == null || (!StringUtils.hasText(address.addressLine1())
                && !StringUtils.hasText(address.addressLine2())
                && !StringUtils.hasText(address.postalCode())
                && !StringUtils.hasText(address.city())
                && !StringUtils.hasText(address.country()))) {
            entity.setAddressLine1(null);
            entity.setAddressLine2(null);
            entity.setPostalCode(null);
            entity.setCity(null);
            entity.setCountry(null);
        } else {
            entity.setAddressLine1(trimToNull(address.addressLine1()));
            entity.setAddressLine2(trimToNull(address.addressLine2()));
            entity.setPostalCode(trimToNull(address.postalCode()));
            entity.setCity(trimToNull(address.city()));
            entity.setCountry(trimToNull(address.country()));
        }
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

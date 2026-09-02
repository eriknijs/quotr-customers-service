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
    public Customer create(String ownerId, CustomerChange change) {
        validate(change);
        CustomerEntity entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(requireOwner(ownerId));
        apply(entity, change);
        return mapper.toDomain(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Customer get(String ownerId, UUID customerId) {
        return mapper.toDomain(findActiveOwned(ownerId, customerId));
    }

    @Transactional(readOnly = true)
    public List<Customer> list(String ownerId) {
        return repository.findByOwnerIdAndDeletedAtIsNullOrderByNameAscCreatedAtAsc(requireOwner(ownerId))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Customer> list(String ownerId, Pageable pageable) {
        return repository.findByOwnerIdAndDeletedAtIsNull(requireOwner(ownerId), pageable)
                .map(mapper::toDomain);
    }

    @Transactional(readOnly = true)
    public List<Customer> search(String ownerId, String query) {
        if (!StringUtils.hasText(query)) {
            return list(ownerId);
        }
        return repository.searchActiveOwned(requireOwner(ownerId), query.trim())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Customer> listOrSearch(String ownerId, String query, Pageable pageable) {
        if (!StringUtils.hasText(query)) {
            return list(ownerId, pageable);
        }
        return repository.searchActiveOwned(requireOwner(ownerId), query.trim(), pageable)
                .map(mapper::toDomain);
    }

    @Transactional
    public Customer update(String ownerId, UUID customerId, CustomerChange change) {
        validate(change);
        CustomerEntity entity = findActiveOwned(ownerId, customerId);
        apply(entity, change);
        return mapper.toDomain(repository.save(entity));
    }

    @Transactional
    public void softDelete(String ownerId, UUID customerId) {
        CustomerEntity entity = findActiveOwned(ownerId, customerId);
        entity.setDeletedAt(Instant.now());
        repository.save(entity);
    }

    private CustomerEntity findActiveOwned(String ownerId, UUID customerId) {
        return repository.findByIdAndOwnerIdAndDeletedAtIsNull(customerId, requireOwner(ownerId))
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private static String requireOwner(String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            throw new CustomerValidationException("Owner identity is required");
        }
        return ownerId.trim();
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

package com.quotr.customers.mapping;

import com.quotr.customers.api.model.CustomerAddress;
import com.quotr.customers.api.model.CustomerPageResponse;
import com.quotr.customers.api.model.CustomerWriteRequest;
import com.quotr.customers.api.model.PageMetadata;
import com.quotr.customers.domain.Address;
import com.quotr.customers.domain.Customer;
import com.quotr.customers.domain.CustomerChange;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerTransportMapper {

    @Mapping(target = "address", source = "address")
    CustomerChange toChange(CustomerWriteRequest request);

    @Mapping(target = "addressLine1", source = "streetAddress")
    @Mapping(target = "addressLine2", ignore = true)
    Address toDomain(CustomerAddress address);

    @Mapping(target = "ownerUserId", expression = "java(ownerUserId(customer.ownerId()))")
    com.quotr.customers.api.model.Customer toTransport(Customer customer);

    List<com.quotr.customers.api.model.Customer> toTransportList(List<Customer> customers);

    @Mapping(target = "streetAddress", source = "addressLine1")
    CustomerAddress toTransport(Address address);

    default CustomerPageResponse toPageResponse(Page<Customer> page) {
        CustomerPageResponse response = new CustomerPageResponse();
        response.setCustomers(toTransportList(page.getContent()));
        response.setPage(toPageMetadata(page));
        return response;
    }

    default PageMetadata toPageMetadata(Page<?> page) {
        PageMetadata metadata = new PageMetadata();
        metadata.setPage(page.getNumber());
        metadata.setSize(page.getSize());
        metadata.setTotalElements(page.getTotalElements());
        metadata.setTotalPages(page.getTotalPages());
        return metadata;
    }

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    default UUID ownerUserId(String ownerId) {
        if (ownerId == null) {
            return null;
        }
        try {
            return UUID.fromString(ownerId);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(ownerId.getBytes(StandardCharsets.UTF_8));
        }
    }
}

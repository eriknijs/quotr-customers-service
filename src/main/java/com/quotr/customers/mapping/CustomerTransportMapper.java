package com.quotr.customers.mapping;

import com.quotr.customers.api.model.CustomerAddress;
import com.quotr.customers.api.model.CustomerPageResponse;
import com.quotr.customers.api.model.CustomerWriteRequest;
import com.quotr.customers.api.model.PageMetadata;
import com.quotr.customers.domain.Address;
import com.quotr.customers.domain.Customer;
import com.quotr.customers.domain.CustomerChange;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

    // The public contract field is still named ownerUserId (see quotr-customers-schema) --
    // changing that is a separate, separately-versioned contract change this ADR does not
    // require. Its value now comes from the tenant-membership id (ADR-0001), not the old
    // access-token-derived pseudo-UUID: both are opaque UUIDs identifying "who" to a client,
    // and the field was never documented as being derived from the bearer token specifically.
    @Mapping(target = "ownerUserId", source = "tenantMemberId")
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
}

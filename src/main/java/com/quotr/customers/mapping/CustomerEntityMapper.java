package com.quotr.customers.mapping;

import com.quotr.customers.domain.Address;
import com.quotr.customers.domain.Customer;
import com.quotr.customers.persistence.CustomerEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerEntityMapper {

    @Mapping(target = "address", expression = "java(toAddress(entity))")
    Customer toDomain(CustomerEntity entity);

    default Address toAddress(CustomerEntity entity) {
        boolean hasAddress = entity.getAddressLine1() != null
                || entity.getAddressLine2() != null
                || entity.getPostalCode() != null
                || entity.getCity() != null
                || entity.getCountry() != null;
        if (!hasAddress) {
            return null;
        }
        return new Address(
                entity.getAddressLine1(),
                entity.getAddressLine2(),
                entity.getPostalCode(),
                entity.getCity(),
                entity.getCountry());
    }
}

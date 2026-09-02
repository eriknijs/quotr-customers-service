package com.quotr.customers.domain;

public record Address(
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String country) {
}

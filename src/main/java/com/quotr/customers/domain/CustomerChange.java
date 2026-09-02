package com.quotr.customers.domain;

public record CustomerChange(
        String name,
        String email,
        String phoneNumber,
        Address address) {
}

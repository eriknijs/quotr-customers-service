package com.quotr.customers.domain;

import java.time.Instant;
import java.util.UUID;

public record Customer(
        UUID id,
        UUID tenantId,
        UUID tenantMemberId,
        String name,
        String email,
        String phoneNumber,
        Address address,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public boolean active() {
        return deletedAt == null;
    }
}

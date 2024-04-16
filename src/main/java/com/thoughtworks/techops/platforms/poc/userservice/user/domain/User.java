package com.thoughtworks.techops.platforms.poc.userservice.user.domain;

import java.util.Optional;

public record User(
        String id,
        Integer version,
        Optional<Integer> employeeId,
        Optional<String> role
) {
    public User(String id, Integer employeeId) {
        this(id, 0, Optional.of(employeeId), Optional.empty());
    }
}

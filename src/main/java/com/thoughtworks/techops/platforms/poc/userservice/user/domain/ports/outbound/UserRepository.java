package com.thoughtworks.techops.platforms.poc.userservice.user.domain.ports.outbound;

import com.thoughtworks.techops.platforms.poc.userservice.user.domain.User;
import reactor.core.publisher.Mono;

public interface UserRepository {
    Mono<User> getById(String userId);

    Mono<User> updateOrCreate(User user);
}



package com.thoughtworks.techops.platforms.poc.userservice.user.adapter.repository;

import com.thoughtworks.techops.platforms.poc.userservice.UserServiceApplication;
import com.thoughtworks.techops.platforms.poc.userservice.common.config.DatabaseConfig;
import com.thoughtworks.techops.platforms.poc.userservice.user.domain.User;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = UserServiceApplication.class
)
@Testcontainers
class UserRepositoryImplTest {
    private static final Logger logger = LoggerFactory.getLogger(UserRepositoryImplTest.class);
    @Container
    private static final PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:12"))
                    .withReuse(true);

    @Autowired
    private UserRepositoryImpl userRepositoryImpl;

    @Autowired
    private DatabaseClient databaseClient;

    private final String userId = "user-1";

    @DynamicPropertySource
    public static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        String host = postgreSQLContainer.getHost();
        Integer port = postgreSQLContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
        String databaseName = postgreSQLContainer.getDatabaseName();
        String r2dbcUrl = String.format("r2dbc:postgresql://%s:%d/%s?schema=%s",
                host, port, databaseName, DatabaseConfig.SCHEMA);
        registry.add("spring.r2dbc.url", () -> r2dbcUrl);
        registry.add("spring.r2dbc.username", postgreSQLContainer::getUsername);
        registry.add("spring.r2dbc.password", postgreSQLContainer::getPassword);

        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.flyway.user", postgreSQLContainer::getUsername);
        registry.add("spring.flyway.password", postgreSQLContainer::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        String deleteQuery = String.format("DELETE FROM %s.user", DatabaseConfig.SCHEMA);
        long numberOfRowsDeleted = databaseClient.sql(deleteQuery).fetch().rowsUpdated().block();
        logger.info("before test, the number of rows deleted in 'user' table: {}", numberOfRowsDeleted);
    }

    @Test
    void getNonExistentUserShouldReturnEmptyMono() {
        StepVerifier.create(userRepositoryImpl.getById("non-existent-user-id"))
                .expectComplete()
                .verify();
    }

    @Test
    void insertAndGetUser() {
        User userToBeSaved = new User(userId, 12345);
        Predicate<User> userMatchPredicate = user ->
                user.id().equals(userToBeSaved.id()) && user.employeeId().equals(userToBeSaved.employeeId());
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userToBeSaved))
                .expectNextMatches(userMatchPredicate)
                .expectComplete()
                .verify();
        StepVerifier.create(userRepositoryImpl.getById(userToBeSaved.id()))
                .expectNextMatches(userMatchPredicate)
                .expectComplete()
                .verify();
    }
}


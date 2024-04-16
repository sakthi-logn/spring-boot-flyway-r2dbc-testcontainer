
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

    @Test
    void insertAndUpdateUser() {
        User userToBeSaved = new User(userId, 12345);
        Predicate<User> userMatchPredicate = user ->
                user.id().equals(userToBeSaved.id()) && user.role().isEmpty();
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userToBeSaved))
                .expectNextMatches(userMatchPredicate)
                .expectComplete()
                .verify();
        Optional<String> newRole = Optional.of("new-role");
        User userV2 = new User(userToBeSaved.id(), userToBeSaved.version(), userToBeSaved.employeeId(), newRole);
        Predicate<User> newRoleMatch = user ->
                user.id().equals(userToBeSaved.id()) && user.role().equals(newRole);
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userV2))
                .expectNextMatches(newRoleMatch)
                .expectComplete()
                .verify();
        StepVerifier.create(userRepositoryImpl.getById(userToBeSaved.id()))
                .expectNextMatches(newRoleMatch)
                .expectComplete()
                .verify();
    }

    @Test
    void incrementVersionOnEveryUpdate() {
        User userToBeSaved = new User(userId, 12345);
        Predicate<User> initialVersionMatch = user ->
                user.id().equals(userToBeSaved.id()) && user.role().isEmpty() && user.version() == 0;
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userToBeSaved))
                .expectNextMatches(initialVersionMatch)
                .expectComplete()
                .verify();
        logger.info("insert successful");
        User userFromDb = userRepositoryImpl.getById(userToBeSaved.id()).block();
        logger.info("version of userFromDb is '{}'", userFromDb.version());
        Optional<String> newRole = Optional.of("new-role");
        User userV2 = new User(userFromDb.id(), userFromDb.version(), userFromDb.employeeId(), newRole);
        logger.info("version of userV2 is '{}'", userV2.version());
        Predicate<User> updatedVersionMatch = user ->
                user.id().equals(userToBeSaved.id()) && user.role().equals(newRole) && user.version() == 1;
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userV2))
                .expectNextMatches(updatedVersionMatch)
                .expectComplete()
                .verify();
        logger.info("1st update successful");
        User userFromDb2 = userRepositoryImpl.getById(userToBeSaved.id()).block();
        logger.info("version of userFromDb2 is '{}'", userFromDb2.version());
        StepVerifier.create(userRepositoryImpl.getById(userToBeSaved.id()))
                .expectNextMatches(updatedVersionMatch)
                .expectComplete()
                .verify();
        logger.info("get after 1st update successful");
    }

    @Test
    void updateOperationShouldSupportOptimisticLocking() {
        User userToBeSaved = new User(userId, 12345);
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userToBeSaved))
                .expectNextCount(1)
                .expectComplete()
                .verify();
        logger.info("insert successful");
        User userFromDb = userRepositoryImpl.getById(userToBeSaved.id()).block();
        logger.info("version of userFromDb is '{}'", userFromDb.version());
        Optional<String> newRole = Optional.of("new-role");
        User userWithRoleUpdated = new User(userFromDb.id(), userFromDb.version(), userFromDb.employeeId(), newRole);
        logger.info("initial version of user after insert is '{}'", userWithRoleUpdated.version());
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userWithRoleUpdated))
                .expectNextCount(1)
                .expectComplete()
                .verify();
        logger.info("user updated for the first time after insert");
        User userFromDbAfterFirstUpdate = userRepositoryImpl.getById(userToBeSaved.id()).block();
        logger.info("version of userFromDbAfterFirstUpdate is '{}'", userFromDbAfterFirstUpdate.version());
        User userUpdateOnOlderVersion = new User(userWithRoleUpdated.id(), userWithRoleUpdated.version(), userWithRoleUpdated.employeeId(), Optional.of("new-role-2"));
        logger.info("about to update user on top of version '{}'", userUpdateOnOlderVersion.version());
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userUpdateOnOlderVersion))
                .expectErrorMatches(throwable -> {
                    logger.info("Throwable error message is '{}'", throwable.getMessage());
                    String expectedErrorMessage =
                            String.format("The version of user '%s' provided for update is '%d'. But the latest version of user '%s' is '%d'. Please update on top of this version.",
                                    userUpdateOnOlderVersion.id(), userUpdateOnOlderVersion.version(), userUpdateOnOlderVersion.id(), userFromDbAfterFirstUpdate.version());
                    return throwable instanceof OptimisticLockingFailureException &&
                            throwable.getMessage() != null && throwable.getMessage().equals(expectedErrorMessage);
                })
                .verify();
        logger.info("update with older version is failing as expected");
        User userUpdateOnLatestVersion = new User(userUpdateOnOlderVersion.id(), userUpdateOnOlderVersion.version() + 1, userUpdateOnOlderVersion.employeeId(), userUpdateOnOlderVersion.role());
        StepVerifier.create(userRepositoryImpl.updateOrCreate(userUpdateOnLatestVersion))
                .expectNextCount(1)
                .expectComplete()
                .verify();
        logger.info("update on top of correct version is successful as expected. No optimistic lock exceptions");
        User userFromDbAfterAllUpdates = userRepositoryImpl.getById(userToBeSaved.id()).block();
        logger.info("version of userFromDbAfterAllUpdates is '{}'", userFromDbAfterAllUpdates.version());
    }
}


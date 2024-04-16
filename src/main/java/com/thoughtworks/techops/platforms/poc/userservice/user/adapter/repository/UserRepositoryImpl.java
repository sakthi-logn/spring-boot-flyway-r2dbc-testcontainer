
package com.thoughtworks.techops.platforms.poc.userservice.user.adapter.repository;

import com.thoughtworks.techops.platforms.poc.userservice.common.config.DatabaseConfig;
import com.thoughtworks.techops.platforms.poc.userservice.user.domain.User;
import com.thoughtworks.techops.platforms.poc.userservice.user.domain.ports.outbound.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Repository
@Transactional
public class UserRepositoryImpl implements UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(UserRepositoryImpl.class);
    private static final String SCHEMA = DatabaseConfig.SCHEMA;
    private final DatabaseClient databaseClient;
    private final RowToUserConverter rowToUserConverter;

    public UserRepositoryImpl(DatabaseClient databaseClient, RowToUserConverter rowToUserConverter) {
        this.databaseClient = databaseClient;
        this.rowToUserConverter = rowToUserConverter;
    }

    @Override
    public Mono<User> getById(String userId) {
        return databaseClient.sql("select * from " + SCHEMA + ".user where id = :userId")
                .bind("userId", userId)
                .map((row, rowMetadata)->rowToUserConverter.convert(row))
                .first();
    }

    @Override
    public Mono<User> updateOrCreate(User user) {
        Mono<User> optimisticLockExceptionIfUserExists = getById(user.id()).flatMap(userFromDb -> {
            String errorMessage = String.format(
                    "The version of user '%s' provided for update is '%d'. " +
                            "But the latest version of user '%s' is '%d'. " +
                            "Please update on top of this version.",
                    user.id(), user.version(), user.id(), userFromDb.version());
            return Mono.error(new OptimisticLockingFailureException(errorMessage));
        });
        return update(user)
                .flatMap(numberOfRowsUpdated -> {
                    if (numberOfRowsUpdated > 0) {
                        logger.info("user with id '{}' updated successfully on top of version '{}'",
                                user.id(), user.version());
                        return getById(user.id());
                    } else {
                        logger.info("user with id '{}' and version '{}' does not exist for update", user.id(), user.version());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(optimisticLockExceptionIfUserExists)
                .switchIfEmpty(insert(user).then(getById(user.id())));
    }

    private Mono<Long> update(User user) {
        String updateQuery = String.format("""
                update %s.user
                set version=:versionAfterUpdate, employee_id=:employeeId, job_profile=:role
                where id=:id and version=:currentVersion
                """, SCHEMA);
        GenericExecuteSpec spec = databaseClient.sql(updateQuery);
        spec = bindPropertiesForUpdate(spec, user);
        return spec.fetch().rowsUpdated().map(Object::toString).map(Long::parseLong);
    }

    private Mono<Void> insert(User user) {
        String insertQuery = String.format("""
                insert into %s.user (id, version, employee_id, job_profile)
                values
                (:id,:version,:employeeId,:role)
                """, SCHEMA);
        GenericExecuteSpec spec = databaseClient.sql(insertQuery);
        spec = bindPropertiesForInsert(spec, user);
        return spec.then();
    }

    private GenericExecuteSpec bindPropertiesForUpdate(GenericExecuteSpec spec, User user) {
        GenericExecuteSpec updatedSpec = bindPropertiesUsedInSql(spec, user);
        updatedSpec = bindProperty(updatedSpec, "currentVersion", Integer.class, user.version());
        updatedSpec = bindProperty(updatedSpec, "versionAfterUpdate", Integer.class, user.version() + 1);
        return updatedSpec;
    }

    private GenericExecuteSpec bindPropertiesForInsert(GenericExecuteSpec spec, User user) {
        GenericExecuteSpec updatedSpec = bindPropertiesUsedInSql(spec, user);
        updatedSpec = bindProperty(updatedSpec, "version", Integer.class, user.version());
        return updatedSpec;
    }

    private GenericExecuteSpec bindPropertiesUsedInSql(GenericExecuteSpec spec, User user) {
        GenericExecuteSpec updatedSpec = spec;
        updatedSpec = bindProperty(updatedSpec, "id", String.class, user.id());
        updatedSpec = bindProperty(updatedSpec, "employeeId", Integer.class, user.employeeId().orElse(null));
        updatedSpec = bindProperty(updatedSpec, "role", String.class, user.role().orElse(null));
        return updatedSpec;
    }

    private <T> GenericExecuteSpec bindProperty(GenericExecuteSpec inputSpec, String propertyName, Class<T> propertyClass, T propertyValue) {
        if (propertyValue != null) {
            return inputSpec.bind(propertyName, propertyValue);
        } else {
            return inputSpec.bindNull(propertyName, propertyClass);
        }
    }
}


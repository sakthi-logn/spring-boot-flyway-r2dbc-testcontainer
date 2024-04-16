package com.thoughtworks.techops.platforms.poc.userservice.user.adapter.repository;

import com.thoughtworks.techops.platforms.poc.userservice.user.domain.User;
import io.r2dbc.spi.Row;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class RowToUserConverter implements Converter<Row, User> {
    private static final Logger logger = LoggerFactory.getLogger(RowToUserConverter.class);

    @Override
    public User convert(Row source) {
        return new User(
                id(source),
                version(source),
                employeeId(source),
                role(source)
        );
    }

    private String id(Row source) {
        String id = source.get("id", String.class);
        if (id == null) {
            throw new NullPointerException("id is missing for User in DB");
        }
        return id;
    }

    private int version(Row source) {
        String version = source.get("version", String.class);
        if (version == null) {
            throw new NullPointerException("version is missing for User '" + id(source) + "' in DB");
        }
        return Integer.parseInt(version);
    }

    private Optional<String> role(Row source) {
        return Optional.ofNullable(source.get("job_profile", String.class));
    }

    private Optional<Integer> employeeId(Row source) {
        Optional<String> employeeIdStrOpt = Optional.ofNullable(source.get("employee_id", String.class));
        return employeeIdStrOpt.map(Integer::parseInt);
    }
}



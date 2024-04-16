-- flyway is configured to create schema 'service_one'

CREATE TABLE service_one."user" (
    id text NOT NULL,
    "version" int4 DEFAULT 0 NOT NULL,
    employee_id int4 NULL,
    job_profile text NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
    CONSTRAINT user_pk PRIMARY KEY (id)
);
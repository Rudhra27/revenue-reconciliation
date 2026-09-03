--liquibase formatted sql

--changeset reconciler:0002-create-app-user
--comment: application accounts; email is citext so uniqueness is case-insensitive
CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         CITEXT      NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE app_user;

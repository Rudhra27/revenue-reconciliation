--liquibase formatted sql

--changeset reconciler:0001-enable-extensions
--comment: citext gives us case-insensitive email uniqueness; pgcrypto provides gen_random_uuid() for id defaults
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
--rollback DROP EXTENSION IF EXISTS pgcrypto;
--rollback DROP EXTENSION IF EXISTS citext;

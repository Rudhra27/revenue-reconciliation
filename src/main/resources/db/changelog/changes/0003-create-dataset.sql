--liquibase formatted sql

--changeset reconciler:0003-create-dataset
--comment: a dataset is one orders file + one payments file that get reconciled together
CREATE TABLE dataset (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'CREATED'
               CHECK (status IN ('CREATED', 'ORDERS_LOADED', 'PAYMENTS_LOADED', 'RECONCILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dataset_user ON dataset (user_id, created_at DESC);
--rollback DROP TABLE dataset;

--liquibase formatted sql

--changeset reconciler:0004-create-order-row
--comment: normalised rows from an uploaded orders.csv, plus the untouched original in raw_json
CREATE TABLE order_row (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id         UUID           NOT NULL REFERENCES dataset (id) ON DELETE CASCADE,
    user_id            UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    source_line_no     INT            NOT NULL,
    raw_json           JSONB          NOT NULL,
    order_id           TEXT           NOT NULL,
    order_date         TIMESTAMPTZ,
    customer_email     TEXT,
    currency           TEXT,
    gross_amount       NUMERIC(12, 2),
    discount           NUMERIC(12, 2),
    net_amount         NUMERIC(12, 2) NOT NULL,
    status             TEXT,
    data_quality_flags TEXT[]         NOT NULL DEFAULT '{}',
    is_duplicate_of    UUID REFERENCES order_row (id) ON DELETE SET NULL
);
CREATE INDEX idx_order_row_dataset ON order_row (dataset_id);
CREATE INDEX idx_order_row_match ON order_row (dataset_id, order_id);
--rollback DROP TABLE order_row;

--changeset reconciler:0004-create-payment-row
--comment: normalised rows from an uploaded payments.csv; order_reference is the trimmed/upper key, order_reference_raw keeps what the file actually had
CREATE TABLE payment_row (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id          UUID           NOT NULL REFERENCES dataset (id) ON DELETE CASCADE,
    user_id             UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    source_line_no      INT            NOT NULL,
    raw_json            JSONB          NOT NULL,
    transaction_ref     TEXT           NOT NULL,
    processed_at        TIMESTAMPTZ,
    order_reference     TEXT,
    order_reference_raw TEXT,
    currency            TEXT,
    amount              NUMERIC(12, 2) NOT NULL,
    fee                 NUMERIC(12, 2),
    net_settled         NUMERIC(12, 2),
    type                TEXT,
    status              TEXT,
    data_quality_flags  TEXT[]         NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_payment_row_dataset ON payment_row (dataset_id);
CREATE INDEX idx_payment_row_ref ON payment_row (dataset_id, order_reference);
--rollback DROP TABLE payment_row;

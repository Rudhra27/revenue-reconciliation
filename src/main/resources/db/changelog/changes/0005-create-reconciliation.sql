--liquibase formatted sql

--changeset reconciler:0005-create-reconciliation-result
--comment: one run per dataset (replaced on re-run); headline figures kept as columns
CREATE TABLE reconciliation_result (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id        UUID           NOT NULL REFERENCES dataset (id) ON DELETE CASCADE,
    user_id           UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    engine_version    TEXT           NOT NULL,
    as_of             TIMESTAMPTZ    NOT NULL,
    total_orders      INT            NOT NULL,
    total_payments    INT            NOT NULL,
    matched_orders    INT            NOT NULL,
    discrepancy_count INT            NOT NULL,
    value_reconciled  NUMERIC(14, 2) NOT NULL,
    value_in_dispute  NUMERIC(14, 2) NOT NULL,
    money_at_risk     NUMERIC(14, 2) NOT NULL
);
CREATE UNIQUE INDEX idx_reconciliation_dataset ON reconciliation_result (dataset_id);
--rollback DROP TABLE reconciliation_result;

--changeset reconciler:0005-create-discrepancy
--comment: one row per finding; detail holds the numbers behind it for the drill-down and the LLM
CREATE TABLE discrepancy (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID           NOT NULL REFERENCES reconciliation_result (id) ON DELETE CASCADE,
    dataset_id      UUID           NOT NULL REFERENCES dataset (id) ON DELETE CASCADE,
    user_id         UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type            TEXT           NOT NULL,
    subtype         TEXT,
    severity        TEXT           NOT NULL,
    direction       TEXT           NOT NULL,
    order_id        TEXT,
    order_row_id    UUID,
    payment_row_ids UUID[]         NOT NULL DEFAULT '{}',
    currency        TEXT,
    amount_impact   NUMERIC(14, 2) NOT NULL,
    detail          JSONB          NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_discrepancy_dataset ON discrepancy (dataset_id);
CREATE INDEX idx_discrepancy_dataset_type ON discrepancy (dataset_id, type);
--rollback DROP TABLE discrepancy;

--liquibase formatted sql

--changeset reconciler:0007-create-llm-explanation
--comment: cached plain-language explanations; input_hash lets us reuse one when nothing relevant changed
CREATE TABLE llm_explanation (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id         UUID        NOT NULL REFERENCES dataset (id) ON DELETE CASCADE,
    discrepancy_id     UUID        REFERENCES discrepancy (id) ON DELETE CASCADE,
    user_id            UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    scope              TEXT        NOT NULL CHECK (scope IN ('SINGLE', 'SUMMARY')),
    input_hash         TEXT        NOT NULL,
    status             TEXT        NOT NULL CHECK (status IN ('OK', 'INVALID', 'FAILED')),
    model              TEXT,
    temperature        NUMERIC(3, 2),
    prompt_version     TEXT,
    summary            TEXT,
    likely_cause       TEXT,
    recommended_action TEXT,
    confidence         TEXT,
    raw_response       TEXT,
    error              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_explanation_lookup ON llm_explanation (discrepancy_id, input_hash);
CREATE INDEX idx_llm_explanation_summary ON llm_explanation (dataset_id, scope, input_hash);
--rollback DROP TABLE llm_explanation;

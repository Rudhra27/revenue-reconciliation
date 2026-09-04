--liquibase formatted sql

--changeset reconciler:0006-discrepancy-search-text
--comment: denormalised order id + transaction refs for the drill-down free-text search
ALTER TABLE discrepancy ADD COLUMN search_text TEXT;
--rollback ALTER TABLE discrepancy DROP COLUMN search_text;

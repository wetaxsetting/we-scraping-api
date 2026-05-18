-- V136: Ensure OVERSEAS_PAYMENT_KEY_SEQ table exists.
-- V135 was recorded as applied in flyway_schema_history on some local DBs before
-- the table was actually created (stale history entry from a failed/incomplete run).
-- Flyway skips V135 on those DBs, so Hibernate validate fails with "missing table".
-- This idempotent migration guarantees the table exists regardless of V135 state.
--
-- Mirrors INTERNAL_TRANSFER_KEY_SEQ (V85) — same UPSERT pattern,
-- independent per-(tenant, day) sequence so overseas and internal-transfer key
-- spaces never collide.
--
-- Written by: OverseasPaymentKeyGenerator (REQUIRES_NEW propagation).
-- Not present in JEX DEV/PROD — refactor-local R&D only.

CREATE TABLE IF NOT EXISTS OVERSEAS_PAYMENT_KEY_SEQ (
    BIZ_KEY   VARCHAR(20)              NOT NULL,
    KEY_DT    DATE                     NOT NULL,
    LAST_SEQ  INTEGER                  NOT NULL,
    REG_DTM   TIMESTAMP WITH TIME ZONE NOT NULL,
    MOD_DTM   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT PK_OVERSEAS_PAYMENT_KEY_SEQ PRIMARY KEY (BIZ_KEY, KEY_DT)
);

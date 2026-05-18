-- V135: Key-sequence helper table for the overseas-payment batch key generator.
-- Mirrors INTERNAL_TRANSFER_KEY_SEQ (created in V85) — same UPSERT pattern,
-- different table name so the two generators' per-(tenant, day) sequences
-- are independent.
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

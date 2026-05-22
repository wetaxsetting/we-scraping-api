-- =====================================================================
-- V200 — CARD_TRNS_RCV_PAY_INFM table
-- =====================================================================
-- Card-link master row. Mirrors legacy DEV {@code wecmsvndev.card_trns_rcv_pay_infm}
-- column-for-column (11 columns, 3-key PK on {@code BIZ_KEY, CARD_SEQ_NO,
-- RCV_PAY_SEQ_NO}). One row per linked card-transaction; the child
-- expenses / receipts live on {@code BUSA_DOC_RCV_PAY_HIS} (V13 + V23).
--
-- Powers the "Link Card Payments with the Expenses" popup
-- (legacy `card_0001_002_c001` create + `card_0001_002_u001` unlink).
--
-- All columns NOT NULL per DEV; audit fields populated by the service.
-- Schema verified via postgres_dev MCP 2026-05-21.
-- =====================================================================

CREATE TABLE IF NOT EXISTS CARD_TRNS_RCV_PAY_INFM (
    BIZ_KEY         VARCHAR(20)      NOT NULL,
    BANK_CD         VARCHAR(20)      NOT NULL,
    CARD_NO         VARCHAR(100)     NOT NULL,
    TRSC_DT         VARCHAR(8)       NOT NULL,
    CARD_SEQ_NO     VARCHAR(20)      NOT NULL,
    RCV_PAY_SEQ_NO  NUMERIC(5, 0)    NOT NULL,
    RCV_PAY_AMT     NUMERIC(20, 6)   NOT NULL,
    REG_ID          VARCHAR(100)     NOT NULL,
    REG_DTM         VARCHAR(14)      NOT NULL,
    MOD_ID          VARCHAR(100)     NOT NULL,
    MOD_DTM         VARCHAR(14)      NOT NULL,
    CONSTRAINT pk_card_trns_rcv_pay_infm
        PRIMARY KEY (BIZ_KEY, CARD_SEQ_NO, RCV_PAY_SEQ_NO)
);

-- Helper index — every list query on the Card Transactions page joins
-- this on {@code (BIZ_KEY, CARD_SEQ_NO)} to surface the linked-state
-- chip; default PK index already covers this prefix but call out the
-- access pattern explicitly so future readers know it's intentional.
CREATE INDEX IF NOT EXISTS ix_card_trns_rcv_pay_infm_biz_card
    ON CARD_TRNS_RCV_PAY_INFM (BIZ_KEY, CARD_SEQ_NO);

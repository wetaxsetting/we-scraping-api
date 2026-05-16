-- =====================================================================
-- V158 — BUSI_MATCH_LOG schema (Smart Organizer / bank↔payment match).
--
-- The table is the canonical audit log of bank-transaction ↔ payment
-- match operations. One row per (bank transaction, payment transaction)
-- pair; the `LOCKED_YN='Y'` flag is what prevents duplicate matches
-- (same `PAY_TRNS_ID` cannot be matched to two different bank rows
-- simultaneously). Unmatch flips `LOCKED_YN` back to 'N' instead of
-- deleting — preserves the match history.
--
-- Column-for-column parity with JEX DEV `busi_match_log`
-- (16 columns, all VARCHAR or CHAR(1)). PK on (BIZ_KEY, MATCH_LOG_SEQ).
-- `MATCH_LOG_SEQ` is per-tenant MAX+1 allocated in Java (no new
-- wb_get_next_val branch — Sprint 2+ writes use service-side
-- generators per CLAUDE.md "Key-generation rule").
--
-- Two helper indexes added beyond DEV:
--   * IX_BUSI_MATCH_LOG_BIZ_BANK   — drives "find match row by
--                                    BANK_TRNS_SHORT_ID" (used by
--                                    MatchService.create dup-check and
--                                    MatchService.undo).
--   * IX_BUSI_MATCH_LOG_BIZ_PAY    — drives "is this payment already
--                                    matched?" guard.
--
-- Idempotent: `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT
-- EXISTS`.
-- =====================================================================

CREATE TABLE IF NOT EXISTS BUSI_MATCH_LOG (
    BIZ_KEY             VARCHAR(20)  NOT NULL,
    MATCH_LOG_SEQ       VARCHAR(20)  NOT NULL,
    BANK_TRNS_SHORT_ID  VARCHAR(20)  NOT NULL,
    BANK_TRNS_FULL_ID   VARCHAR(100) NOT NULL,
    PAY_TRNS_ID         VARCHAR(50)  NOT NULL,
    MATCH_TYPE_CD       CHAR(1)      NOT NULL,
    MEMO                VARCHAR(500),
    MATCHED_AT          VARCHAR(14)  NOT NULL,
    MATCHED_BY          VARCHAR(100) NOT NULL,
    LAST_MATCHED_AT     VARCHAR(14),
    LAST_MATCHED_BY     VARCHAR(100),
    LOCKED_YN           CHAR(1)      NOT NULL DEFAULT 'N',
    REG_ID              VARCHAR(100) NOT NULL,
    REG_DTM             VARCHAR(14)  NOT NULL,
    MOD_ID              VARCHAR(100) NOT NULL,
    MOD_DTM             VARCHAR(14)  NOT NULL,
    CONSTRAINT PK_BUSI_MATCH_LOG PRIMARY KEY (BIZ_KEY, MATCH_LOG_SEQ)
);

CREATE INDEX IF NOT EXISTS IX_BUSI_MATCH_LOG_BIZ_BANK
    ON BUSI_MATCH_LOG (BIZ_KEY, BANK_TRNS_SHORT_ID);

CREATE INDEX IF NOT EXISTS IX_BUSI_MATCH_LOG_BIZ_PAY
    ON BUSI_MATCH_LOG (BIZ_KEY, PAY_TRNS_ID);

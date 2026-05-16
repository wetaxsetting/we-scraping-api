-- ============================================================================
-- V156 — Real Bank Transaction Sync: align ACCT_TRNS_HSTR with the dedup +
-- locking semantics the legacy `fnsh_0001_001_c001_act.jsp` pipeline relies on.
--
-- Replaces the stub AccountService.syncTransactions(...) with the real
-- NXISAS scrape → ACCT_TRNS_HSTR insert path. The legacy JSP uses two
-- columns + one boolean we need locally:
--
--   * BANK_TRNS_FULL_ID  — SHA-256(bankCd | trscDt | trscTm | amt |
--                                  inoutDv | rmrk | rmrk2). 64 hex chars.
--                          Already added by V23 (Sprint 1 schema parity).
--   * BANK_TRNS_SHORT_ID — first 4 hex chars of FULL_ID, prefixed with
--                          trscDt for human-readable cross-reference.
--                          Already added by V23.
--   * LOCKED_YN          — 'Y' marks a row whose remarks have been
--                          edited by the user (legacy "locked" semantic);
--                          the sync re-scrape MUST NOT overwrite rmrk /
--                          rmrk2 / link_match_yn on locked rows. Legacy
--                          schema carries this column; refactor missed it
--                          in V13 / V23 — add it now.
--
-- Also adds the dedup lookup index that the orchestrator's
-- "transaction already exists" check hits per scraped row:
--     WHERE biz_key = ? AND bank_trns_full_id = ? AND locked_yn = 'N'
--
-- Refactor-only deviation from DEV: legacy `LOCKED_YN` is CHAR(1) NOT
-- NULL DEFAULT 'N'. We add the same with `CREATE COLUMN IF NOT EXISTS`
-- so re-running this migration is a no-op when the column already
-- landed via a future schema-parity audit. Idempotent.
-- ============================================================================

ALTER TABLE ACCT_TRNS_HSTR
    ADD COLUMN IF NOT EXISTS LOCKED_YN CHAR(1) NOT NULL DEFAULT 'N';

-- Backfill any pre-existing rows that landed before the column existed
-- (defensive — `ADD COLUMN ... DEFAULT 'N'` already populates, but
--  PG versions differ on whether the default applies to existing rows
--  during a single-statement ADD COLUMN).
UPDATE ACCT_TRNS_HSTR
   SET LOCKED_YN = 'N'
 WHERE LOCKED_YN IS NULL;

-- Dedup lookup index — the orchestrator queries
--   WHERE biz_key = ? AND bank_trns_full_id = ?
-- once per scraped row to decide INSERT vs SKIP. Without this index the
-- check is a sequential scan over the entire tenant's history (millions
-- of rows after a few months of sync). The legacy DEV schema carries
-- a B-tree on the same column pair; refactor adds it locally.
CREATE INDEX IF NOT EXISTS ix_acct_trns_hstr_biz_full_id
    ON ACCT_TRNS_HSTR (BIZ_KEY, BANK_TRNS_FULL_ID);

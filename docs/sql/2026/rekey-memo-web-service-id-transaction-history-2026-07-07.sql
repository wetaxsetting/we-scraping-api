-- WeCMS Manual Database Change
-- Date: 2026-07-07
-- Source: migrated from Claude Code (AI)/sql/memo_web_svc_id_transaction_history_20260707.sql
-- Related record: docs/audits/governance/web-svc-id-route-migration-audit.md
-- Type: DDL+DML
-- Execution: MANUAL
-- Target environments: Per-environment, only where the legacy JEX screens are being retired (R2 decision, 2026-07-07)
-- Executor: DBA TEAM
-- Rollback: Original script creates backup tables; reversible by updating WEB_SVC_ID back to the legacy ids
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- Manual one-time data migration — re-scope Transaction History memos.
--
-- Purpose : MemoScope.TRANSACTION_HISTORY_VND / _FCY changed from the legacy
--           JEX screen ids ('fnsh_0002_001' / 'fnsh_0006_001') to the modern
--           refactor routes:
--               fnsh_0002_001  ->  /banking/transaction-history/vnd
--               fnsh_0006_001  ->  /banking/transaction-history/fcy
--           MEMO_INFM / MEMO_INFM_FILE rows written by the live JEX system
--           under the old ids must be re-keyed, or the refactor Transaction
--           History screen (which now reads/writes the new WEB_SVC_ID) will
--           not see the ~3,342 (VND) / ~3,341 (FCY) existing legacy memos.
--
-- Decision : R2 APPROVED (2026-07-07). Migration authorised by the PO. This
--           re-scopes the shared JEX DB, so the LIVE legacy fnsh_0002_001 /
--           fnsh_0006_001 screens will NO LONGER find these memos after the
--           migration (they query the old ids). Run this ONLY per-environment
--           where the legacy screens are being retired / are not in use.
--
-- Scope   : Transaction History VND + FCY only. Card Transactions
--           ('card_0001_001' -> route) is a separate script
--           (memo_web_svc_id_card_transactions_20260702.sql). Every other
--           WEB_SVC_ID is left untouched.
--
-- Execution: DBA-run BY HAND against the refactor DB. NOT a Flyway migration
--            (Flyway is disabled in this project; schema/data changes are
--            applied out-of-band). Do NOT add to db/migration/.
--
-- Safety   : WEB_SVC_ID is part of the MEMO_INFM PK (BIZ_KEY, WEB_SVC_ID,
--            SEQ_NO). The UPDATE is safe ONLY while no row already exists under
--            a NEW value for the same (BIZ_KEY, SEQ_NO). The pre-check below
--            aborts the whole transaction if that ever holds. Idempotent:
--            re-running after it is applied updates 0 rows.
--
-- Run order: 0) backup  ->  1) pre-count  ->  2) collision pre-check  ->
--            3) UPDATE memo_infm  ->  4) UPDATE memo_infm_file  ->
--            5) COMMIT  ->  6) post-check.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- STEP 0 — BACKUP (run FIRST, outside the transaction; DBA keeps this table
--          until the migration is verified good in the target environment).
--          Drop it once verified:  DROP TABLE memo_infm_bk_20260707;
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS memo_infm_bk_20260707 AS
SELECT * FROM memo_infm
 WHERE web_svc_id IN ('fnsh_0002_001', 'fnsh_0006_001');

CREATE TABLE IF NOT EXISTS memo_infm_file_bk_20260707 AS
SELECT * FROM memo_infm_file
 WHERE web_svc_id IN ('fnsh_0002_001', 'fnsh_0006_001');

-- ---------------------------------------------------------------------------
-- STEP 1 — PRE-COUNT (record the before-state; compare against STEP 6).
-- ---------------------------------------------------------------------------
SELECT web_svc_id, count(*) AS rows_before
  FROM memo_infm
 WHERE web_svc_id IN ('fnsh_0002_001', 'fnsh_0006_001',
                      '/banking/transaction-history/vnd',
                      '/banking/transaction-history/fcy')
 GROUP BY web_svc_id
 ORDER BY web_svc_id;

BEGIN;

-- ---------------------------------------------------------------------------
-- STEP 2 — COLLISION PRE-CHECK. Abort if any legacy row would collide with an
--          already-existing row under the target route on (BIZ_KEY, SEQ_NO).
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    -- VND
    IF EXISTS (
        SELECT 1
        FROM memo_infm old
        JOIN memo_infm neu
          ON  neu.biz_key   = old.biz_key
          AND neu.seq_no     = old.seq_no
          AND neu.web_svc_id = '/banking/transaction-history/vnd'
        WHERE old.web_svc_id = 'fnsh_0002_001'
    ) THEN
        RAISE EXCEPTION 'MEMO_INFM VND re-scope aborted: PK collision under /banking/transaction-history/vnd — reconcile SEQ_NO manually first.';
    END IF;

    -- FCY
    IF EXISTS (
        SELECT 1
        FROM memo_infm old
        JOIN memo_infm neu
          ON  neu.biz_key   = old.biz_key
          AND neu.seq_no     = old.seq_no
          AND neu.web_svc_id = '/banking/transaction-history/fcy'
        WHERE old.web_svc_id = 'fnsh_0006_001'
    ) THEN
        RAISE EXCEPTION 'MEMO_INFM FCY re-scope aborted: PK collision under /banking/transaction-history/fcy — reconcile SEQ_NO manually first.';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- STEP 3 — RE-SCOPE MEMO_INFM.
-- ---------------------------------------------------------------------------
UPDATE memo_infm
   SET web_svc_id = '/banking/transaction-history/vnd'
 WHERE web_svc_id = 'fnsh_0002_001';

UPDATE memo_infm
   SET web_svc_id = '/banking/transaction-history/fcy'
 WHERE web_svc_id = 'fnsh_0006_001';

-- ---------------------------------------------------------------------------
-- STEP 4 — RE-SCOPE MEMO_INFM_FILE (attachments; typically 0 rows for these
--          screens, kept for parity with the Card script and completeness).
-- ---------------------------------------------------------------------------
UPDATE memo_infm_file
   SET web_svc_id = '/banking/transaction-history/vnd'
 WHERE web_svc_id = 'fnsh_0002_001';

UPDATE memo_infm_file
   SET web_svc_id = '/banking/transaction-history/fcy'
 WHERE web_svc_id = 'fnsh_0006_001';

-- ---------------------------------------------------------------------------
-- STEP 5 — COMMIT.
-- ---------------------------------------------------------------------------
COMMIT;

-- ---------------------------------------------------------------------------
-- STEP 6 — POST-CHECK (run after commit). Expect: the two legacy ids show 0
--          rows and the two routes carry the migrated counts. Compare against
--          STEP 1 (rows_before[fnsh_0002_001] == rows_after[.../vnd], etc.).
-- ---------------------------------------------------------------------------
-- SELECT web_svc_id, count(*) AS rows_after
--   FROM memo_infm
--  WHERE web_svc_id IN ('fnsh_0002_001', 'fnsh_0006_001',
--                       '/banking/transaction-history/vnd',
--                       '/banking/transaction-history/fcy')
--  GROUP BY web_svc_id
--  ORDER BY web_svc_id;   -- expect only the two route values remain
--
-- Reconciliation (must return 0 rows — nothing left on the legacy ids):
-- SELECT count(*) FROM memo_infm
--  WHERE web_svc_id IN ('fnsh_0002_001', 'fnsh_0006_001');
--
-- Clean up the backups once verified good:
-- DROP TABLE memo_infm_bk_20260707;
-- DROP TABLE memo_infm_file_bk_20260707;

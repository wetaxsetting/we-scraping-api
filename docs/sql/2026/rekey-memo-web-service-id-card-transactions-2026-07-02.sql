-- WeCMS Manual Database Change
-- Date: 2026-07-02
-- Source: migrated from Claude Code (AI)/sql/memo_web_svc_id_card_transactions_20260702.sql
-- Related record: docs/audits/governance/web-svc-id-route-migration-audit.md
-- Type: DML
-- Execution: MANUAL
-- Target environments: Refactor DB
-- Executor: DBA TEAM
-- Rollback: Reversible by updating WEB_SVC_ID back to 'card_0001_001' for the same rows; the original script carries a pre-check that aborts on collision
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- Manual one-time data migration — re-scope Card Transactions memos.
--
-- Purpose : MemoScope.CARD_TRANSACTIONS changed from the legacy JEX screen id
--           'card_0001_001' to the modern refactor route
--           '/payment/corporate-card/card-transactions'. MEMO_INFM /
--           MEMO_INFM_FILE rows written under the old value must be re-keyed
--           or they become orphaned (the refactor now reads/writes the new
--           WEB_SVC_ID).
--
-- Scope   : Card Transactions only. Every other WEB_SVC_ID is left untouched.
--           (Transaction History VND/FCY are NOT wired to memo yet — no rows.)
--
-- Execution: DBA-run BY HAND against the refactor DB. This is NOT a Flyway
--            migration (Flyway is disabled in this project; schema/data changes
--            are applied out-of-band). Do NOT add to db/migration/.
--
-- Safety   : WEB_SVC_ID is part of the MEMO_INFM PK (BIZ_KEY, WEB_SVC_ID,
--            SEQ_NO) and UK (…, REF_NO). This UPDATE is safe ONLY while no row
--            already exists under the NEW value for the same (BIZ_KEY, SEQ_NO)
--            — verified 0 rows under the new scope at authoring time
--            (2026-07-02). The pre-check below aborts if that ever changes.
--            Idempotent: re-running after it is applied updates 0 rows.
--
-- Run order: MEMO_INFM first, then MEMO_INFM_FILE (both keyed by WEB_SVC_ID;
--            no cross-table FK on WEB_SVC_ID, but keep parent-first for clarity).
-- ============================================================================

BEGIN;

-- Pre-check: abort if any Card Transactions memo already exists under the new
-- scope with a SEQ_NO that would collide with a row being migrated.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM memo_infm old
        JOIN memo_infm neu
          ON  neu.biz_key    = old.biz_key
          AND neu.seq_no      = old.seq_no
          AND neu.web_svc_id  = '/payment/corporate-card/card-transactions'
        WHERE old.web_svc_id = 'card_0001_001'
    ) THEN
        RAISE EXCEPTION 'MEMO_INFM re-scope aborted: PK collision under the new WEB_SVC_ID — reconcile SEQ_NO manually first.';
    END IF;
END $$;

UPDATE memo_infm
   SET web_svc_id = '/payment/corporate-card/card-transactions'
 WHERE web_svc_id = 'card_0001_001';

UPDATE memo_infm_file
   SET web_svc_id = '/payment/corporate-card/card-transactions'
 WHERE web_svc_id = 'card_0001_001';

COMMIT;

-- Verify (run after commit):
--   SELECT web_svc_id, count(*) FROM memo_infm
--    WHERE web_svc_id IN ('card_0001_001','/payment/corporate-card/card-transactions')
--    GROUP BY web_svc_id;   -- expect only the new value remains

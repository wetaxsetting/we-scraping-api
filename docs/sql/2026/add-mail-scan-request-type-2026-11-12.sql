-- WeCMS Manual Database Change
-- Date: 2026-11-12
-- Related record: docs/features/addon-service/weinvoice/weinvoice-mailbox-connections-2026-11-12.md
-- Type: DDL (CHECK constraint replaced on an existing table)
-- Execution: MANUAL
-- Target environments: the WeInvoice database (the we-scraping engine database) of every environment
--                      where WeInvoice is enabled (LOCAL / SIT / UAT / PROD). NOT the WeCMS database.
-- Executor: DBA OR DEV TEAM — environment dependent
-- Transaction requirement: one transaction (BEGIN ... COMMIT below); the constraint swap is atomic.
-- Rollback: section 4. Only possible while no row has rqst_type = 'MAIL_SCAN' — otherwise
--           ROLLBACK REQUIRES DBA REVIEW (the old constraint would reject existing rows).
--
-- Why: POST /api/v1/weinvoice/mailboxes/{id}/scan queues an engine request of type MAIL_SCAN,
-- which the V1 constraint winv_engn_rqst_chk01 rejects. V1__winv_schema.sql is left unchanged
-- (already applied — applied migrations are never edited); a new environment runs V1, V2 and
-- this script in that order.
-- Replace `weinvoice` if the environment uses another WECMS_WEINVOICE_APP_SCHEMA.

-- 1. PRE-CHECK
--    Expected before the change: one row whose definition does NOT contain 'MAIL_SCAN'.
SELECT conname, pg_get_constraintdef(oid) AS definition,
       pg_get_constraintdef(oid) LIKE '%MAIL_SCAN%' AS has_mail_scan
  FROM pg_constraint
 WHERE conrelid = 'weinvoice.winv_engn_rqst'::regclass
   AND conname = 'winv_engn_rqst_chk01';

-- 2. CHANGE
BEGIN;

ALTER TABLE weinvoice.winv_engn_rqst DROP CONSTRAINT IF EXISTS winv_engn_rqst_chk01;
ALTER TABLE weinvoice.winv_engn_rqst
	ADD CONSTRAINT winv_engn_rqst_chk01
	CHECK ((rqst_type = ANY (ARRAY['SYNC', 'FETCH_XML', 'FETCH_PDF', 'LOGIN_TEST', 'IMPORT', 'TEST_DATA', 'MAIL_SCAN'])));

COMMENT ON COLUMN weinvoice.winv_engn_rqst.rqst_prmt IS 'Request parameters. SYNC: {direction, from_month, to_month}; FETCH_XML/FETCH_PDF: {invoice_ids, lookup_code?, company_code?}; LOGIN_TEST: {}; IMPORT: {upload_id}; TEST_DATA: {}; MAIL_SCAN: {mailbox_id}.';

COMMIT;

-- 3. POST-CHECK
--    Expected after the change: one row, has_mail_scan = t.
SELECT conname, pg_get_constraintdef(oid) AS definition,
       pg_get_constraintdef(oid) LIKE '%MAIL_SCAN%' AS has_mail_scan
  FROM pg_constraint
 WHERE conrelid = 'weinvoice.winv_engn_rqst'::regclass
   AND conname = 'winv_engn_rqst_chk01';

-- 4. ROLLBACK / RECOVERY NOTES
--    First check: SELECT count(*) FROM weinvoice.winv_engn_rqst WHERE rqst_type = 'MAIL_SCAN';
--    0 rows → the V1 definition can be restored:
--      BEGIN;
--      ALTER TABLE weinvoice.winv_engn_rqst DROP CONSTRAINT winv_engn_rqst_chk01;
--      ALTER TABLE weinvoice.winv_engn_rqst ADD CONSTRAINT winv_engn_rqst_chk01
--        CHECK ((rqst_type = ANY (ARRAY['SYNC', 'FETCH_XML', 'FETCH_PDF', 'LOGIN_TEST', 'IMPORT', 'TEST_DATA'])));
--      COMMIT;
--    > 0 rows → ROLLBACK REQUIRES DBA REVIEW (deleting request history is a data decision).

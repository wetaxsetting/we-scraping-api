-- WeCMS Manual Database Change
-- Date: 2026-07-04
-- Source: migrated from Claude Code (AI)/sql/BUSA_DOC_FILE_20260704.sql
-- Related record: docs/audits/legacy-parity/card-transaction-link-reference-legacy-audit.md
-- Type: DDL
-- Execution: MANUAL
-- Target environments: Refactor DB (172.168.5.172/wecmsdevdb) and each environment DB before deploy
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
-- MIGRATION REVIEW REQUIRED — this script creates a new table and/or DB function that has no counterpart in src/main/resources/db/migration/. No migration was created and no V-number was assigned during the archive migration; the placement decision is open.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- BUSA_DOC_FILE — receipt/e-invoice attachments (audit G3/G5) — DBA prerequisites
-- =============================================================================
-- Feature      : Payment > Card Transactions > Link Reference > New Receipt / Receipt tab
-- Consumed by   : (future) BusaDocFileRepository + createReceipt upload + Receipt-tab
--                 file count/dropdown + secure download endpoint. See audit §13.
-- Date          : 2026-07-04
-- Executed by   : DBA, BY HAND, against the refactor DB (172.168.5.172:5432/wecmsdevdb)
--                 and each env DB before deploy. Flyway is disabled in wecms-api
--                 (ddl-auto=validate; schema is external / pre-migrated).
-- Source of truth: UAT legacy wb_get_next_val + JEX DEV schema (verified 2026-07-04).
-- Inventory     : busa_doc_file added to the 142-table baseline 2026-07-04 (PO decision).
--
-- FEATURE CODE STAYS BLOCKED until this script is reviewed + applied.
--
-- RUN ORDER
--   Step 1 (schema) then Step 2 (wb_get_next_val branch). Both idempotent.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Step 1 — BUSA_DOC_FILE table (column-for-column with JEX DEV; PK 3-col).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS busa_doc_file (
    biz_key      varchar(20)  NOT NULL,
    doc_id       varchar(50)  NOT NULL,
    file_id      varchar(100) NOT NULL,
    org_file_nm  varchar(500) NULL,
    save_file_nm varchar(500) NULL,
    file_size    varchar(100) NULL,
    reg_id       varchar(100) NULL,
    reg_dtm      varchar(14)  NULL,
    CONSTRAINT busa_doc_file_pk PRIMARY KEY (biz_key, doc_id, file_id)
);

-- Helper index beyond DEV (allowed) — the Receipt-tab batch file lookup keys on (biz_key, doc_id).
CREATE INDEX IF NOT EXISTS ix_busa_doc_file_biz_doc ON busa_doc_file (biz_key, doc_id);

-- -----------------------------------------------------------------------------
-- Step 2 — wb_get_next_val branch for busa_doc_file.file_id.
--   Verbatim from UAT legacy wb_get_next_val:
--     when lower($1)='busa_doc_file' and lower($2)='file_id'
--          and $3=biz_key and $4=doc_id  ->  per-(biz_key, doc_id) MAX+1
--
--   select coalesce(max(file_id::numeric), 0) + 1
--   from busa_doc_file
--   where biz_key = $3 and doc_id = $4;
--
--   => file_id is a per-document sequence: 1, 2, 3, ...
--
--   Concurrency: the sequence rule stays legacy max+1 (this SQL is unchanged).
--   The race where two files for the same DOC_ID read the same max+1 is handled
--   in APPLICATION code by a one-shot retry on the PK conflict (re-allocate +
--   re-insert once) — NOT in this function. See audit §13.2 / §13.5. No
--   stored-value or normal-behavior change here.
--
-- PostgreSQL has no "ALTER FUNCTION ADD CASE" — the DBA splices the WHEN branch
-- below into the existing wb_get_next_val CASE (immediately before the trailing
-- ELSE), then re-creates the whole function via CREATE OR REPLACE FUNCTION.
-- (Mirror the delivery used for the other centralised branches — V140.)
-- Idempotent: CREATE OR REPLACE replaces the body; the branch is a pure read.
--
-- BRANCH TO SPLICE (params: $1 table, $2 field, $3 biz_key, $4 doc_id):
-- -----------------------------------------------------------------------------
--     when lower($1) = 'busa_doc_file' and lower($2) = 'file_id'
--          and coalesce(trim($3), '') <> '' and coalesce(trim($4), '') <> '' then
--         return (
--             select cast(coalesce(max(file_id::numeric), 0) + 1 as text)
--             from busa_doc_file
--             where biz_key = $3
--               and doc_id  = $4
--         );
-- -----------------------------------------------------------------------------
--
-- VERIFY (read-only; returns '1' for a fresh doc):
--     SELECT wb_get_next_val('busa_doc_file', 'file_id', 'VN_VNM0004_000001',
--                            '<some_doc_id>', NULL, NULL, NULL, NULL);
--
-- NOTE — the busa_doc_detl.disp_ord branch (used elsewhere; per (biz_key, doc_id)
--   COUNT(*)+1) already exists in the refactor function (V140 #43); no action here.
-- =============================================================================

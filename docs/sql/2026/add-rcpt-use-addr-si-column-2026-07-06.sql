-- WeCMS Manual Database Change
-- Date: 2026-07-06
-- Source: migrated from Claude Code (AI)/sql/RCPT_USE_ADDR_SI_20260706.sql
-- Related record: TBD
-- Type: DDL
-- Execution: MANUAL
-- Target environments: JEX (WECMS DEV / WECMS_BATCH DEV) per the original script header
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — dropping a column is destructive; no rollback documented in the original script
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- WECMS — Add column RCPT_USE_ADDR_SI
-- File        : RCPT_USE_ADDR_SI_20260706.sql
-- Date        : 2026-07-06
-- System      : JEX (WECMS DEV / WECMS_BATCH DEV)
-- DB          : PostgreSQL
-- Author      : Claude Code (AI) — reviewed & executed by DBA
--
-- Purpose:
--   Add RCPT_USE_ADDR_SI VARCHAR(35) to the three firm-banking / pay-file tables.
--   RCPT_USE_ADDR_SI is a SIBLING of the existing RCPT_USE_ADDR column
--   (RCPT_USE_ADDR is VARCHAR(300), the recipient/beneficiary address used for
--   overseas remittance — redmine #42564). RCPT_USE_ADDR_SI is a shorter
--   35-character companion field. It does NOT replace or modify RCPT_USE_ADDR.
--
--   Nullable, no default — matches the existing RCPT_USE_ADDR / REMIT_CCY_CD
--   convention on these tables.
--
-- Scope / safety:
--   * DDL only. No data migration, no changes to existing columns/keys/indexes.
--   * Idempotent — uses ADD COLUMN IF NOT EXISTS (PostgreSQL 9.6+), safe to re-run.
--   * Leaves all existing rows and columns untouched (new column is NULL for
--     existing rows).
--
-- Execution:
--   * Run BY A DBA, by hand, per environment during deployment
--     (DEV already applied → UAT → PROD, after UAT verification).
--   * This script is NOT auto-applied by any application on startup.
--   * Run order: standalone — no dependencies; the three statements are
--     independent and may run in any order.
--
-- Related (do NOT apply from this script — tracked separately):
--   * Java entities updated:  TbPayFileData / TbFirmFileData / TbFirmFileRslt.
--   * IDO XML (C001/U001/R001) updated in Jex Studio (DAO -> IDO).
--   * Batch fixed-width bank-file layout (Deposit_Data): NOT changed — pending
--     bank file-format spec / PO decision.
-- =============================================================================

-- 1. TB_PAY_FILE_DATA — sibling of RCPT_USE_ADDR (VARCHAR 300) on this table
ALTER TABLE TB_PAY_FILE_DATA
    ADD COLUMN IF NOT EXISTS RCPT_USE_ADDR_SI VARCHAR(35);

COMMENT ON COLUMN TB_PAY_FILE_DATA.RCPT_USE_ADDR_SI
    IS 'Recipient use address (short, 35). Sibling of RCPT_USE_ADDR (VARCHAR 300); does not replace it.';

-- 2. TB_FIRM_FILE_DATA — sibling of RCPT_USE_ADDR (VARCHAR 300) on this table
ALTER TABLE TB_FIRM_FILE_DATA
    ADD COLUMN IF NOT EXISTS RCPT_USE_ADDR_SI VARCHAR(35);

COMMENT ON COLUMN TB_FIRM_FILE_DATA.RCPT_USE_ADDR_SI
    IS 'Recipient use address (short, 35). Sibling of RCPT_USE_ADDR (VARCHAR 300); does not replace it.';

-- 3. TB_FIRM_FILE_RSLT — sibling of RCPT_USE_ADDR (VARCHAR 300) on this table
ALTER TABLE TB_FIRM_FILE_RSLT
    ADD COLUMN IF NOT EXISTS RCPT_USE_ADDR_SI VARCHAR(35);

COMMENT ON COLUMN TB_FIRM_FILE_RSLT.RCPT_USE_ADDR_SI
    IS 'Recipient use address (short, 35). Sibling of RCPT_USE_ADDR (VARCHAR 300); does not replace it.';

-- =============================================================================
-- Verification (optional — run after applying):
--   SELECT table_name, column_name, data_type, character_maximum_length, is_nullable
--   FROM information_schema.columns
--   WHERE column_name = 'rcpt_use_addr_si'
--     AND table_name IN ('tb_pay_file_data','tb_firm_file_data','tb_firm_file_rslt')
--   ORDER BY table_name;
-- Expected: 3 rows, character varying, length 35, is_nullable = YES.
-- =============================================================================

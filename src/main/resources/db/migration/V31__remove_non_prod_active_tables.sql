-- ============================================================================
-- V31__remove_non_prod_active_tables.sql
--
-- Drops 6 local tables that are NOT in the PROD Active Table List defined in
-- wecms-api/CLAUDE.md §"PROD DB Table Scope for Refactoring".
--
-- The PROD audit (run on the live wecms.vn database via pg_stat_user_tables)
-- confirmed these tables are not populated in production. They were created
-- locally by V13/V14 because the live JEX DEV schema still carries them, but
-- WeCMS does not actually use them — they are WABOOKS legacy / batch-history
-- artifacts. PROD's daily-financial / daily-cash reports use a different
-- table set (DAILY_FINCL_REPT_DAILY_SUMR_DETL, DAILY_CASH_REPT_DAILY_SUMR_DETL)
-- which Sprint 2 will migrate when the report features land.
--
-- Tables dropped (and the migration that introduced them):
--   1. busi_cond_daily_sumr        (V14 — Sprint 1 reports schema)
--   2. busi_cond_daily_sumr_detl   (V14)
--   3. sell_his_daily_sumr         (V14)
--   4. sell_his_daily_sumr_detl    (V14)
--   5. bat_call_his                (V14)
--   6. acct_trns_rcv_pay_evid_file (V13 — Sprint 1 banking schema)
--
-- Java side (same MR):
--   - Removed entity BusiCondDailySumr / BusiCondDailySumrId
--   - Removed repository BusiCondDailySumrRepository
--   - ReportService.dailyCash() converted to Sprint-1 stub for the per-currency
--     summary section (account ledger continues to read ACCT_MSTR — PROD active).
--
-- Idempotent: every DROP uses IF EXISTS.
--
-- Notes on `wb_get_next_val` (V27):
--   The function still has WHEN branches for `acct_trns_rcv_pay_evid_file.file_id`
--   and `busi_cond_daily_sumr_detl.dtl_seq_no`. PL/pgSQL is late-binding, so
--   dropping the tables does not break the function definition; the branches
--   simply error if invoked. Sprint 1 endpoints do not call wb_get_next_val
--   (audit 2026-04-29, CLAUDE.md §415), so leaving the dead branches in place
--   is harmless. They will be removed when the function is next regenerated.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Drop FOREIGN KEY constraints created in V24 that reference / are
--    declared on the to-be-dropped tables. DROP TABLE … CASCADE would also
--    drop them, but we drop them explicitly first so the migration is
--    self-documenting and order-independent.
-- ----------------------------------------------------------------------------
ALTER TABLE IF EXISTS BUSI_COND_DAILY_SUMR
    DROP CONSTRAINT IF EXISTS busi_cond_daily_sumr_fk01;

ALTER TABLE IF EXISTS SELL_HIS_DAILY_SUMR
    DROP CONSTRAINT IF EXISTS sell_his_daily_sumr_fk01;

ALTER TABLE IF EXISTS ACCT_TRNS_RCV_PAY_EVID_FILE
    DROP CONSTRAINT IF EXISTS acct_trns_rcv_pay_evid_file_fk01;

-- ----------------------------------------------------------------------------
-- 2. Drop the 6 tables. CASCADE picks up any leftover dependent objects
--    (CHECK constraints, indexes, dependent FKs).
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS BUSI_COND_DAILY_SUMR_DETL   CASCADE;
DROP TABLE IF EXISTS BUSI_COND_DAILY_SUMR        CASCADE;
DROP TABLE IF EXISTS SELL_HIS_DAILY_SUMR_DETL    CASCADE;
DROP TABLE IF EXISTS SELL_HIS_DAILY_SUMR         CASCADE;
DROP TABLE IF EXISTS BAT_CALL_HIS                CASCADE;
DROP TABLE IF EXISTS ACCT_TRNS_RCV_PAY_EVID_FILE CASCADE;

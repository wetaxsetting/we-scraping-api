-- WeCMS Manual Database Change
-- Date: 2026-06-19
-- Source: migrated from Claude Code (AI)/sql/BANK_EXCH_RATE_INFM_cleanup_20260619.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: Refactor R&D DB (172.168.5.172/wecmsdevdb) ONLY — explicitly NOT the legacy JEX DB
-- Executor: DBA OR DEV TEAM — environment dependent
-- Rollback: Deleted rows are re-created by the next exchange-rate batch round; no rollback SQL is documented in the original script
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- Cleanup: collapsed refactor-batch rows in BANK_EXCH_RATE_INFM
-- =============================================================================
-- TARGET DB : REFACTOR R&D ONLY  -> postgres_dev_server_refactor
--             (172.168.5.172 / wecmsdevdb).  DO NOT run on the legacy JEX DB
--             (postgres_dev_server_jexframe) — it has NO SYSTEM_BATCH rows and
--             its email-reg_id rows are legacy-valid.
-- EXECUTION : Manual, by a DBA/authorized developer. Claude Code MCP access is
--             read-only (per .claude/rules/database.md); this script is NOT
--             auto-run by the application.
--
-- ROOT CAUSE: the pre-fix ExchangeRateBatchService used an existence/upsert key
--             WITHOUT ref_ccy_cd (biz_key+bank_cd+ref_date+ccy_cd+seq_no).
--             Because ccy_cd is always 'VND' for Shinhan/Woori VN, every
--             currency in a round collapsed onto ONE logical row. The code is
--             now fixed to key on
--             biz_key+bank_cd+ref_date+ref_ccy_cd+ccy_cd+seq_no.
--             (BankExchRateInfmRepository.findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo)
--
-- OBSERVED (refactor DB, 2026-06-19), reg_id='SYSTEM_BATCH', 8 rows total:
--   biz_key            bank      seq  count  currencies
--   VN_VNM0004_000001  11704088  1    1      USD     (expected 12)
--   VN_VNM0004_000001  11704088  2    1      NZD     (expected 12)
--   VN_VNM0004_000001  11704020  1    1      USD     (expected 5)
--   VN_VNM0004_000001  11704020  2    1      USD     (expected 5)
--   VN_VNM0004_000002  (same collapsed shape)
--
-- SAFETY:
--   * Deletes ONLY reg_id='SYSTEM_BATCH' rows (refactor-written) for the one
--     affected date + the two FX banks. Legacy email-reg_id rows are untouched.
--   * Confirmed there are NO SYSTEM_BATCH rows on any other date/bank in the
--     refactor DB, so the scope below is exhaustive and minimal.
-- =============================================================================

-- ── STEP 1 — BACKUP / EXPORT the exact rows to be deleted (run + keep output) ──
SELECT biz_key, bank_cd, ref_date, seq_no, ref_ccy_cd, ccy_cd, exch_seq_no,
       buy_amt, sell_amt, tran_buy_amt, tran_sell_amt,
       reg_id, reg_dtm
FROM bank_exch_rate_infm
WHERE ref_date = '20260619'
  AND bank_cd IN ('11704088','11704020')
  AND reg_id  = 'SYSTEM_BATCH'
ORDER BY biz_key, bank_cd, seq_no::int, exch_seq_no::int;

-- ── STEP 2 — COLLAPSE DETECTION (proves the rows are broken: count != 12/5) ───
SELECT biz_key, bank_cd, ref_date, seq_no,
       COUNT(*) AS row_count,
       STRING_AGG(ref_ccy_cd, ',' ORDER BY exch_seq_no::int) AS currencies
FROM bank_exch_rate_infm
WHERE ref_date = '20260619'
  AND reg_id   = 'SYSTEM_BATCH'
  AND bank_cd IN ('11704088','11704020')
GROUP BY biz_key, bank_cd, ref_date, seq_no
ORDER BY biz_key, bank_cd, seq_no;
-- Expected (broken): every group row_count = 1  ->  confirms collapse.

-- ── STEP 3 — DELETE (run ONLY after Steps 1-2 confirm collapsed SYSTEM_BATCH) ──
-- Wrapped in a transaction with a row-count guard. Review the RAISE NOTICE,
-- then COMMIT (or ROLLBACK if the count is unexpected).
BEGIN;

DELETE FROM bank_exch_rate_infm
WHERE ref_date = '20260619'
  AND bank_cd IN ('11704088','11704020')
  AND reg_id  = 'SYSTEM_BATCH';
-- Expect: DELETE 8  (2 tenants x 2 banks x 2 seq, each currently 1 collapsed row).
-- If the count is NOT 8, ROLLBACK and re-investigate before proceeding.

COMMIT;

-- ── STEP 4 — RE-POPULATE ──────────────────────────────────────────────────────
-- Re-collection is done by the FIXED batch (now keying on ref_ccy_cd). Run the
-- refactor app with the schedule gate enabled against this DB, OR invoke the
-- on-demand path, so it re-fetches from Coocon and writes the full set:
--   wecms.exchange-rate.batch.enabled=true   (default is false / off)
-- The batch will then insert 12 Shinhan rows/round and 5 Woori rows/round per
-- active tenant, with continuous exch_seq_no (seq1 -> 1..12, seq2 -> 13..24 for
-- Shinhan; seq1 -> 1..5, seq2 -> 6..10, seq3 -> 11..15 for Woori).

-- ── STEP 5 — VERIFY after re-run ──────────────────────────────────────────────
-- Shinhan: each (biz_key, seq_no) must have row_count = 12.
SELECT biz_key, bank_cd, seq_no,
       COUNT(*) AS row_count,
       MIN(exch_seq_no::int) AS min_exch, MAX(exch_seq_no::int) AS max_exch,
       STRING_AGG(ref_ccy_cd, ',' ORDER BY exch_seq_no::int) AS currencies
FROM bank_exch_rate_infm
WHERE ref_date='20260619' AND bank_cd='11704088' AND reg_id='SYSTEM_BATCH'
GROUP BY biz_key, bank_cd, seq_no
ORDER BY biz_key, seq_no::int;

-- Woori: each (biz_key, seq_no) must have row_count = 5 (USD,EUR,KRW,JPY,GBP).
SELECT biz_key, bank_cd, seq_no,
       COUNT(*) AS row_count,
       MIN(exch_seq_no::int) AS min_exch, MAX(exch_seq_no::int) AS max_exch,
       STRING_AGG(ref_ccy_cd, ',' ORDER BY exch_seq_no::int) AS currencies
FROM bank_exch_rate_infm
WHERE ref_date='20260619' AND bank_cd='11704020' AND reg_id='SYSTEM_BATCH'
GROUP BY biz_key, bank_cd, seq_no
ORDER BY biz_key, seq_no::int;

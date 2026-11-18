-- WeCMS Manual Database Change
-- Date: 2026-08-24
-- Related record: ../../bugs/einvoice-scrape-currency-legacy-clamp-2026-08-24.md
-- Type: DML
-- Execution: MANUAL
-- Target environments: SIT (shared dev DB 172.168.5.172/wecmsdevdb).
--                      UAT / PROD require a FRESH COUNT + separate decision — see §0.
-- Executor: DBA TEAM
-- Transaction requirement: single transaction. Run the pre-check, SAVE ITS OUTPUT
--                          (needed for rollback), then the UPDATE, then the post-check.
-- Rollback: reversible ONLY with the doc_id list captured by pre-check 2 — see §4.
--           No data is destroyed (NULL -> 'VND'); nothing is deleted.
--
-- ---------------------------------------------------------------------------
-- WHY
-- ---------------------------------------------------------------------------
-- The refactor's e-invoice detail scrape wrote BUSA_DOC_MSTR.TRCO_TRSC_CCY_CD
-- without the legacy currency resolution in
--   WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c002_act.jsp
--     :239-260  (clamp)      + :605-611 / :414-419  (master write)
--
-- Legacy resolves the engine currency in three branches BEFORE any write:
--   * K   e-Invoice (einv_testResults='1' OR invoiceSymbol starts with 'K'):
--         blank -> (totalAmt has no ',' AND >= 1000) ? 'VND' : '999';
--         non-blank and not USD/VND -> '999'.
--   * POS e-Invoice (einv_testResults='2'):  blank -> 'VND'  (ticket #38058).
--   * C   e-Invoice (anything else):         untouched.
-- The master then defaults to 'VND' and is overridden only when the clamped
-- value is not CcyCd.NONE ('999'), so '999' never lands on the master.
--
-- The refactor applied none of this, so:
--   * POS invoices (the GDT detail payload carries NO `currency` field for POS)
--     were left NULL instead of legacy's 'VND'  -> the "Total ()" bucket in the
--     Expenses / Sales e-Invoices footer.
--   * A K invoice reporting a foreign code (KRW) was written verbatim and
--     violated the legacy CHECK constraint busa_doc_mstr_chk07
--     (VND | USD | 999 | 998) -> WBE_DB on POST /addon/einvoices/scrape-detail-results.
--
-- The application fix is deployed in code (EInvoiceDetailScrapeService
-- .resolveMasterCcy + the phase-1 clamp in EInvoiceScrapeService), so NEW scrapes
-- are correct. This script repairs only the rows already written wrong.
--
-- ---------------------------------------------------------------------------
-- WHAT THIS SCRIPT DOES *NOT* DO
-- ---------------------------------------------------------------------------
--   * No DDL. No schema, index, constraint or DB-function change is required.
--   * It does NOT alter busa_doc_mstr_chk07. That constraint is legacy JEX DDL
--     and is CORRECT — do not widen it to admit KRW/KHR. Legacy folds foreign
--     currencies to VND; widening it would diverge from the JEX schema and break
--     forward data-migration safety.
--   * It does NOT touch phase-1-only rows (never detail-scraped: 107 POS + 38 K).
--     Legacy leaves those NULL too until their detail is scraped. They will be
--     stamped by the application on the next Update over their date range.
--   * It does NOT touch C invoices with a blank currency (24 rows). Legacy also
--     stores NULL for those (CcyCd.fromValue("") returns null at c002:608, which
--     overwrites the VND default). Changing that needs a PO decision — see the
--     OPEN QUESTION in the related bug record.
--   * It does NOT touch any row carrying an FX rate. Verified 2026-08-24: none of
--     the 304 target rows has einv_rate set, so no genuinely foreign-currency
--     invoice is in scope. The guard stays in the predicate as defence in depth.
--
-- ===========================================================================
-- 0. ENVIRONMENT GUARD  (read before running anywhere except the dev DB)
-- ===========================================================================
-- Verified on 172.168.5.172/wecmsdevdb, 2026-08-24: exactly ONE tenant is
-- affected, VN_VNM0004_000001 (POS 407 NULL / 300 enriched; K 42 NULL / 4
-- enriched; issu_dt 20260101..20260820).
--
-- On the live JEX DB (UAT / PROD) legacy-written rows ALSO carry NULL in this
-- column (78 POS + 825 K on JEX DEV) — those are legacy's own gaps, predating
-- ticket #38058 or never detail-scraped, and they are OUT OF SCOPE. The
-- biz_key filter below is what keeps this script off them. Do NOT remove it,
-- and do NOT run this on UAT/PROD until a fresh count has been taken there and
-- the affected biz_key list confirmed with the PO.

-- ===========================================================================
-- 1. PRE-CHECK   (run both; save the output of pre-check 2)
-- ===========================================================================

-- 1.1 Expected: einv_testresults '2' -> 300, '1' -> 4.  Total 304.
--     If the numbers differ materially, STOP and re-confirm scope.
SELECT einv_testresults,
       count(*)                                    AS rows_to_update,
       count(*) FILTER (WHERE einv_rate IS NOT NULL
                          AND einv_rate <> 0)      AS rows_with_fx_rate_must_be_zero,
       min(issu_dt)                                AS earliest_issu_dt,
       max(issu_dt)                                AS latest_issu_dt
  FROM busa_doc_mstr
 WHERE biz_key = 'VN_VNM0004_000001'
   AND einv_testresults IN ('1','2')
   AND trco_trsc_ccy_cd IS NULL
   AND (einv_rate IS NULL OR einv_rate = 0)
   AND (einv_paymenttype     IS NOT NULL
     OR einv_title           IS NOT NULL
     OR einv_totalamtinletter IS NOT NULL)
 GROUP BY 1
 ORDER BY 1;

-- 1.2 ROLLBACK KEY — save this result set to a file before running section 2.
--     It is the ONLY way to reverse the UPDATE (see §4).
SELECT biz_key, doc_id
  FROM busa_doc_mstr
 WHERE biz_key = 'VN_VNM0004_000001'
   AND einv_testresults IN ('1','2')
   AND trco_trsc_ccy_cd IS NULL
   AND (einv_rate IS NULL OR einv_rate = 0)
   AND (einv_paymenttype     IS NOT NULL
     OR einv_title           IS NOT NULL
     OR einv_totalamtinletter IS NOT NULL)
 ORDER BY doc_id;

-- ===========================================================================
-- 2. CHANGE
-- ===========================================================================
-- Applies the legacy resolution result ('VND') to detail-enriched K and POS
-- rows that the refactor left NULL. Predicate is identical to pre-check 1.2.
BEGIN;

UPDATE busa_doc_mstr
   SET trco_trsc_ccy_cd = 'VND'
 WHERE biz_key = 'VN_VNM0004_000001'
   AND einv_testresults IN ('1','2')          -- K + POS only; C stays legacy-NULL
   AND trco_trsc_ccy_cd IS NULL
   AND (einv_rate IS NULL OR einv_rate = 0)   -- never touch a genuine FX invoice
   AND (einv_paymenttype     IS NOT NULL      -- detail-scraped only
     OR einv_title           IS NOT NULL
     OR einv_totalamtinletter IS NOT NULL);
-- Expected: UPDATE 304

-- Deliberately NOT updated here: MOD_ID / MOD_DTM. This is a data-repair of a
-- value the application should already have written; preserving the original
-- audit stamps keeps the row attributable to the scrape that created it. If the
-- DBA process requires an audit trail on corrective DML, record it in the
-- related bug record's Database Execution Status table instead.

COMMIT;

-- ===========================================================================
-- 3. POST-CHECK
-- ===========================================================================

-- 3.1 Expected: zero rows returned (no enriched K/POS row left NULL).
SELECT einv_testresults, count(*) AS still_null_must_be_empty
  FROM busa_doc_mstr
 WHERE biz_key = 'VN_VNM0004_000001'
   AND einv_testresults IN ('1','2')
   AND trco_trsc_ccy_cd IS NULL
   AND (einv_rate IS NULL OR einv_rate = 0)
   AND (einv_paymenttype     IS NOT NULL
     OR einv_title           IS NOT NULL
     OR einv_totalamtinletter IS NOT NULL)
 GROUP BY 1;

-- 3.2 Constraint sanity — chk07 admits only VND | USD | 999 | 998.
--     Expected: zero rows.
SELECT doc_id, trco_trsc_ccy_cd
  FROM busa_doc_mstr
 WHERE biz_key = 'VN_VNM0004_000001'
   AND trco_trsc_ccy_cd IS NOT NULL
   AND trco_trsc_ccy_cd NOT IN ('VND','USD','999','998');

-- 3.3 Distribution after the fix. Remaining NULLs are the intentional
--     out-of-scope sets: phase-1-only rows (107 POS + 38 K) and blank-currency
--     C invoices (24) — all legacy-faithful.
SELECT einv_testresults, trco_trsc_ccy_cd, count(*)
  FROM busa_doc_mstr
 WHERE biz_key = 'VN_VNM0004_000001'
 GROUP BY 1,2
 ORDER BY 1,3 DESC;

-- 3.4 UI verification (Add-on Service > Expenses e-Invoices, Last 30 Days):
--     the "Total ()" footer row must disappear and its 9 transactions fold into
--     Total (VND) -> 52 transaction(s), 140.406.067.

-- ===========================================================================
-- 4. ROLLBACK / RECOVERY NOTES
-- ===========================================================================
-- Non-destructive: NULL -> 'VND' on a nullable column. Nothing is deleted and
-- no other column is written.
--
-- The section-2 predicate is NOT self-reversing (after the UPDATE the rows no
-- longer match `trco_trsc_ccy_cd IS NULL`), so rollback REQUIRES the doc_id list
-- saved from pre-check 1.2:
--
--   BEGIN;
--   UPDATE busa_doc_mstr
--      SET trco_trsc_ccy_cd = NULL
--    WHERE biz_key = 'VN_VNM0004_000001'
--      AND doc_id IN ( <the doc_id list saved from pre-check 1.2> );
--   COMMIT;
--
-- If that list was not saved, rollback is best-effort only and REQUIRES DBA
-- REVIEW — the updated rows become indistinguishable from rows the application
-- legitimately stamped 'VND'.
--
-- Re-running this script after a rollback is safe and idempotent: the predicate
-- matches only rows that are still NULL.

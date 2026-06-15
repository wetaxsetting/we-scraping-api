-- V181: Fix USD overseas payment amounts stored in wrong column
--
-- 3 supported currencies and their storage columns:
--   VND → TOT_PAY_KHR_AMT (VN-tenant legacy alias) + TOT_PAY_VND_AMT
--   USD → TOT_PAY_USD_AMT
--   KRW → TOT_PAY_KHR_AMT (no dedicated KRW column; shares KHR column)
--
-- Root cause: OverseasPaymentService.requestApproval() previously wrote
--   TOT_PAY_KHR_AMT = actual_amount  (for ALL currencies, including USD)
--   TOT_PAY_USD_AMT = 0              (hardcoded, regardless of currency)
--
-- VND and KRW rows were unaffected because the approval box reads TOT_PAY_KHR_AMT
-- for non-USD — those amounts were already in the right column.
-- Only USD rows are broken: the amount is in the wrong column (KHR) instead of USD.
--
-- The write-path fix in "improve edit pending" corrected new rows. This migration
-- corrects existing USD rows in the DB.
--
-- Targets EXPT_RESOL_MSTR rows that:
--   1. Have a matching OVRS_PYMN_SUMR row with CCY_CD = 'USD'
--   2. Have TOT_PAY_KHR_AMT > 0  (amount landed in the wrong column)
--   3. Have TOT_PAY_USD_AMT = 0 or NULL  (the correct column is empty)
--   4. Have APPR_DV = '34'  (overseas payment type code)

UPDATE expt_resol_mstr erm
SET
    tot_pay_usd_amt = erm.tot_pay_khr_amt,
    tot_pay_khr_amt = 0,
    tot_pay_vnd_amt = 0
WHERE erm.appr_dv = '34'
  AND erm.tot_pay_khr_amt > 0
  AND (erm.tot_pay_usd_amt IS NULL OR erm.tot_pay_usd_amt = 0)
  AND EXISTS (
      SELECT 1
      FROM ovrs_pymn_sumr ops
      WHERE ops.biz_key    = erm.biz_key
        AND ops.appr_seq_no = erm.appr_seq_no
        AND ops.ccy_cd      = 'USD'
  );

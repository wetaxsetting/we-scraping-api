-- V248: Overseas Banking Match (OVR-G1) Phase A — schema preparation.
--
-- Adds the legacy-compatible per-line match IDENTITY (OVRS_PYMN_DETL_NO) and
-- match-STATE columns to OVRS_PYMN_DTLS, per OVR-G3 Option A (keep the table,
-- keep the PK (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ), add the columns additively).
--
-- Decision + source grounding:
--   docs/features/overseas-payment/overseas-detail-table-key-decision.md (OVR-G3 → Option A)
--   docs/features/overseas-payment/overseas-detail-no-generation-audit.md
--     · legacy OVRS_PYMN_SUMR_DETL.OVRS_PYMN_DETL_NO is VARCHAR(15) on JEX DEV
--     · plain numeric string, per-tenant running counter (wb_get_next_val(... BIZ_KEY))
--     · generated at upload/detail-insert (before approval); opaque equality key in match
--   docs/features/overseas-payment/overseas-banking-match-OVR-G1-plan.md
--
-- Phase A = schema/entity/generator only. NO candidate-search / match / unmatch /
-- unmatch-all / controller / frontend / dispatch changes. No change to
-- ACCT_TRNS_HSTR or BUSI_MATCH_LOG (their OVRS_PYMN_NO / OVRS_PYMN_DETL_NO /
-- PAY_TRNS_ID columns already exist; widths VARCHAR(20) / VARCHAR(50) accommodate
-- the VARCHAR(15) identifier with room to spare).
--
-- Idempotent: ADD COLUMN IF NOT EXISTS + CREATE UNIQUE INDEX IF NOT EXISTS +
-- a one-time NULL-guarded backfill.

-- 1. Match identity + state columns (additive). Widths mirror legacy DEV
--    (OVRS_PYMN_DETL_NO 15) and the OVRS_PYMN_DTLS audit convention
--    (REG_ID 100 / REG_DTM 14 → MOD_ID 100 / MOD_DTM 14).
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS OVRS_PYMN_DETL_NO   VARCHAR(15);
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS LINK_MATCH_YN       VARCHAR(1) NOT NULL DEFAULT 'N';
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS ACCT_SEQ_NO         VARCHAR(20);
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS BANK_TRNS_SHORT_ID  VARCHAR(20);
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS BANK_TRNS_FULL_ID   VARCHAR(100);
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS MOD_ID              VARCHAR(100);
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS MOD_DTM             VARCHAR(14);

-- 2. Backfill OVRS_PYMN_DETL_NO for existing rows — deterministic, per tenant,
--    ordered by (OVRS_PYMN_NO, TRN_SEQ), plain numeric strings (1..N per BIZ_KEY).
--    NULL-guarded so re-runs are no-ops. First-run numbers every row (all NULL);
--    the Java generator's MAX+1 continues from this ceiling for new uploads.
WITH numbered AS (
    SELECT biz_key,
           ovrs_pymn_no,
           trn_seq,
           ROW_NUMBER() OVER (PARTITION BY biz_key ORDER BY ovrs_pymn_no, trn_seq) AS rn
      FROM OVRS_PYMN_DTLS
     WHERE OVRS_PYMN_DETL_NO IS NULL
)
UPDATE OVRS_PYMN_DTLS t
   SET OVRS_PYMN_DETL_NO = numbered.rn::text
  FROM numbered
 WHERE t.biz_key      = numbered.biz_key
   AND t.ovrs_pymn_no = numbered.ovrs_pymn_no
   AND t.trn_seq      = numbered.trn_seq;

-- 3. Per-tenant uniqueness on the match identity. Legacy uses OVRS_PYMN_DETL_NO
--    ALONE as PAY_TRNS_ID and the ACCT_TRNS_HSTR reverse stamp, so it must be
--    unique within BIZ_KEY (not merely within the batch). Partial index allows
--    NULLs for any future pre-backfill window.
CREATE UNIQUE INDEX IF NOT EXISTS ux_ovrs_pymn_dtls_biz_detl_no
    ON OVRS_PYMN_DTLS (BIZ_KEY, OVRS_PYMN_DETL_NO)
    WHERE OVRS_PYMN_DETL_NO IS NOT NULL;

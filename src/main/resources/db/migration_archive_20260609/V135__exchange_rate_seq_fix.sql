-- V89: Add covering index for the DISTINCT ON (ccy_cd) query in ExchangeRateService.
--
-- The existing IDX_BANK_EXCH_RATE_INFM_BIZ_DATE covers the WHERE clause
-- (biz_key, ref_date, bank_cd), but the DISTINCT ON query additionally orders
-- by (ccy_cd ASC, exch_seq_no DESC).  Without a covering index PostgreSQL must
-- sort the filtered rows before de-duplicating, which is fine for the current
-- 15-currency seed but adds latency as data volume grows.
--
-- This index lets PostgreSQL resolve the full query
--   WHERE biz_key=? AND bank_cd=? AND ref_date=?
--   ORDER BY ccy_cd ASC, exch_seq_no DESC
-- from a single index scan with no additional sort step.

CREATE INDEX IF NOT EXISTS IDX_BANK_EXCH_RATE_INFM_DISTINCT_SEQ
    ON BANK_EXCH_RATE_INFM (BIZ_KEY, BANK_CD, REF_DATE, CCY_CD ASC, EXCH_SEQ_NO DESC);

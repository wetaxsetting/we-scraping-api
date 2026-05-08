-- ============================================================================
-- V60__firm_banking_history_view_and_fns.sql
--
-- Settings > Firm Banking History — installs the parity-critical helper
-- functions and the unified read-only view that backs the screen.
--
-- Strategy (mirrors V44's nickname-formatting precedent): port the
-- legacy PL/pgSQL functions verbatim from DEV `wecmsvndev.pg_proc`.
-- Read-only legacy SQL helpers stay as DB functions; new logic and
-- write-side procedures stay in Java.
--
-- Functions installed:
--   1. kr_to_vn_time(varchar)              — KR-side YYYYMMDDHH24MISS → VN-side
--   2. wb_get_napas_bank_nm(varchar)       — 1-arg overload, looks up
--                                            short bank name; depends on
--                                            tb_firm_bank_code (V59) and
--                                            two hard-coded mappings
--                                            (970424=Shinhan VN, 970457=Woori VN)
--
-- The 3-arg wb_get_napas_bank_nm overload depends on a different table
-- (napas_bank_info) which is not migrated locally and is not used by the
-- Firm Banking History screen. It is intentionally NOT ported here —
-- track as a deferred follow-up if a future feature needs it.
--
-- View installed:
--   V_FIRM_BANKING_HISTORY — five-leg UNION ALL mirroring legacy
--                            IDO.TB_FIRM_TRAN_R002.xml byte-for-byte.
--                            Backed by the @Immutable JPA entity
--                            FirmBankingHistoryView; one repository,
--                            one query path, one EXPLAIN to read.
--
-- Reference:
--   - docs/refactor-wecms/database/settings/firm-banking-history-schema.md
--   - WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.TB_FIRM_TRAN_R002.xml (legacy)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. kr_to_vn_time(varchar) — KR → VN local time
--    Ported verbatim from wecmsvndev.kr_to_vn_time on 2026-05-08.
--    Subtracts 9 hours (KR is UTC+9, VN is UTC+7 → -2h; the legacy
--    implementation actually subtracts 9h, matching the DEV row
--    samples — keep the legacy behaviour to preserve display parity).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION kr_to_vn_time(kr_time_str character varying)
RETURNS character varying
LANGUAGE plpgsql
AS $function$
DECLARE
    vn_time_str VARCHAR;
BEGIN
    vn_time_str := TO_CHAR(
        TO_TIMESTAMP(kr_time_str, 'YYYYMMDDHH24MISS') - INTERVAL '9 hours',
        'YYYYMMDDHH24MISS'
    );
    RETURN vn_time_str;
END;
$function$;

-- ---------------------------------------------------------------------------
-- 2. wb_get_napas_bank_nm(varchar) — 1-arg overload
--    Ported verbatim from wecmsvndev.wb_get_napas_bank_nm on 2026-05-08.
--    Two hard-coded mappings (970424 = Shinhan VN, 970457 = Woori VN);
--    everything else falls through to TB_FIRM_BANK_CODE.eng_abrvat_nm.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION wb_get_napas_bank_nm(_bank_cd character varying)
RETURNS character varying
LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN CASE
        WHEN _bank_cd IS NULL OR _bank_cd = '' THEN NULL
        WHEN _bank_cd = '970424' THEN 'Shinhan VN'
        WHEN _bank_cd = '970457' THEN 'Woori VN'
        ELSE (
            SELECT eng_abrvat_nm
              FROM tb_firm_bank_code
             WHERE bank_cd = _bank_cd
             LIMIT 1
        )
    END;
END;
$function$;

-- ---------------------------------------------------------------------------
-- 3. V_FIRM_BANKING_HISTORY — five-leg unified history view
--
--    Mirrors IDO.TB_FIRM_TRAN_R002.xml's <sql> byte-for-byte modulo:
--      - The outer "WHERE 1=1 ??" dynamic predicate placeholder moves
--        out of the view body; the repository applies BIZ_KEY +
--        TARN_DTM + SVC_TYPE + SEQ_NO predicates to the view directly.
--      - Per-leg `WHERE BIZ_KEY = :bizKey` is REMOVED from the view
--        body so the outer query's BIZ_KEY predicate can be pushed
--        down into each UNION leg's plan by Postgres.
--      - The two cosmetic typos in the legacy IDO (`SVN_NM` aliased
--        for two of five legs) are corrected to `SVC_NM`.
--
--    Columns mirror the legacy IDO's <out> section:
--      TARN_DTM, SVC_TYPE, SVC_NM, SEQ_NO, DEP_BANK_CD, RSLT_CD,
--      RSLT_MSG, JOB, REQ_DATA, BIZ_KEY, VAN_ORG_CD.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW V_FIRM_BANKING_HISTORY AS
-- Leg F: Firm Banking Transaction (TB_FIRM_TRAN)
SELECT
    kr_to_vn_time(TFT.TRAN_DT || TFT.TRAN_TM)                                AS TARN_DTM,
    'F'::VARCHAR(1)                                                          AS SVC_TYPE,
    TFT.TRAN_TP::VARCHAR(50)                                                 AS SVC_NM,
    TFT.TRAN_SEQ::VARCHAR(50)                                                AS SEQ_NO,
    (COALESCE(wb_get_napas_bank_nm(TFT.OUT_BANK_CD), '') || ' - ' ||
     TFT.OUT_BANK_CD)::VARCHAR(200)                                          AS DEP_BANK_CD,
    TFT.RS_CD::VARCHAR(20)                                                   AS RSLT_CD,
    TFT.RS_CD::VARCHAR(2000)                                                 AS RSLT_MSG,
    TFT.TRAN_TP::VARCHAR(5000)                                               AS JOB,
    NULL::VARCHAR(1000)                                                      AS REQ_DATA,
    TFT.BIZ_KEY                                                              AS BIZ_KEY,
    TFT.VAN_ORG_CD                                                           AS VAN_ORG_CD
FROM TB_FIRM_TRAN TFT

UNION ALL

-- Leg G: Vietnam GDT Inquiry — e-Invoice Sales / Expense (NXSIAS)
SELECT
    NMTLH.REG_DTM                                                            AS TARN_DTM,
    'G'::VARCHAR(1)                                                          AS SVC_TYPE,
    (CASE WHEN NMTLH.NXISAS_TYPE = 'E-INVOICE_SALES_INQUIRY'
          THEN 'Sale E-Invoice Inquiry'
          ELSE 'Expense E-Invoice Inquiry'
     END)::VARCHAR(50)                                                       AS SVC_NM,
    NMTLH.UUID::VARCHAR(50)                                                  AS SEQ_NO,
    NMTLH.NXISAS_MODULE::VARCHAR(200)                                        AS DEP_BANK_CD,
    NMTLH.NXISAS_ERR_CODE::VARCHAR(20)                                       AS RSLT_CD,
    NMTLH.NXISAS_ERR_CONT::VARCHAR(2000)                                     AS RSLT_MSG,
    NMTLH.NXISAS_JOB::VARCHAR(5000)                                          AS JOB,
    NMTLH.REQ_DATA                                                           AS REQ_DATA,
    NMTLH.BIZ_KEY                                                            AS BIZ_KEY,
    NULL::VARCHAR(20)                                                        AS VAN_ORG_CD
FROM NXSIAS_MODULE_TRAN_LOG_HIS NMTLH
WHERE NMTLH.NXISAS_TYPE IN ('E-INVOICE_SALES_INQUIRY', 'E-INVOICE_EXPENSE_INQUIRY')

UNION ALL

-- Leg B: Vietnam Business Inquiry (NXSIAS, business-registration)
SELECT
    NMTLH.REG_DTM                                                            AS TARN_DTM,
    'B'::VARCHAR(1)                                                          AS SVC_TYPE,
    'Vietnam Business Inquiry'::VARCHAR(50)                                  AS SVC_NM,
    NMTLH.UUID::VARCHAR(50)                                                  AS SEQ_NO,
    NMTLH.NXISAS_MODULE::VARCHAR(200)                                        AS DEP_BANK_CD,
    NMTLH.NXISAS_ERR_CODE::VARCHAR(20)                                       AS RSLT_CD,
    NMTLH.NXISAS_ERR_CONT::VARCHAR(2000)                                     AS RSLT_MSG,
    NMTLH.NXISAS_JOB::VARCHAR(5000)                                          AS JOB,
    NMTLH.REQ_DATA                                                           AS REQ_DATA,
    NMTLH.BIZ_KEY                                                            AS BIZ_KEY,
    NULL::VARCHAR(20)                                                        AS VAN_ORG_CD
FROM NXSIAS_MODULE_TRAN_LOG_HIS NMTLH
WHERE NMTLH.NXISAS_JOB IN ('BUSSINESS_REGISTRATION_DETAIL', 'BUSSINESS_REGISTRATION_LIST')

UNION ALL

-- Leg S (NAPAS): Funds Transfer Scraping (NAPAS_REMIT_TRNS_HSTR)
-- wb_get_bank_nm(varchar, varchar) was installed in V44; safe to reuse.
SELECT
    NRTH.REG_DTM                                                             AS TARN_DTM,
    'S'::VARCHAR(1)                                                          AS SVC_TYPE,
    'Funds Transfer (Scraping)'::VARCHAR(50)                                 AS SVC_NM,
    NRTH.SEQ_NO::VARCHAR(50)                                                 AS SEQ_NO,
    (COALESCE(wb_get_bank_nm(NRTH.OUT_BANK_CODE, 's'), '') || ' - ' ||
     NRTH.OUT_BANK_CODE)::VARCHAR(200)                                       AS DEP_BANK_CD,
    NRTH.TRNS_STTS::VARCHAR(20)                                              AS RSLT_CD,
    NRTH.TRNS_MSG::VARCHAR(2000)                                             AS RSLT_MSG,
    'TRANSFER'::VARCHAR(5000)                                                AS JOB,
    NULL::VARCHAR(1000)                                                      AS REQ_DATA,
    NRTH.BIZ_KEY                                                             AS BIZ_KEY,
    NULL::VARCHAR(20)                                                        AS VAN_ORG_CD
FROM NAPAS_REMIT_TRNS_HSTR NRTH

UNION ALL

-- Leg S (EXT_TRAN): Scraping Balance / Transaction / Card (EXT_TRAN_CALL_HIS)
SELECT
    ETH.REG_DTM                                                              AS TARN_DTM,
    'S'::VARCHAR(1)                                                          AS SVC_TYPE,
    (CASE
        WHEN ETH.SVC_ID = 'BALANCE_INQUIRY'          THEN 'Scraping Balance'
        WHEN ETH.SVC_ID = 'REAL_TIME_ONLINE_INQUIRY' THEN 'Scraping Transaction'
        WHEN ETH.SVC_ID = 'CARD_INQUIRY'             THEN 'Scraping Card Transaction'
     END)::VARCHAR(50)                                                       AS SVC_NM,
    ETH.SEQ_NO::VARCHAR(50)                                                  AS SEQ_NO,
    (COALESCE(wb_get_bank_nm(ETH.BANK_CD, 's'), '') || ' - ' ||
     ETH.BANK_CD)::VARCHAR(200)                                              AS DEP_BANK_CD,
    ETH.RSLT_CD::VARCHAR(20)                                                 AS RSLT_CD,
    ETH.ERR_CONT::VARCHAR(2000)                                              AS RSLT_MSG,
    (CASE
        WHEN ETH.SVC_ID = 'BALANCE_INQUIRY'          THEN 'BALANCE'
        WHEN ETH.SVC_ID = 'REAL_TIME_ONLINE_INQUIRY' THEN 'TRANSACTION'
        WHEN ETH.SVC_ID = 'CARD_INQUIRY'             THEN 'CARD_USING'
     END)::VARCHAR(5000)                                                     AS JOB,
    NULL::VARCHAR(1000)                                                      AS REQ_DATA,
    ETH.BIZ_KEY                                                              AS BIZ_KEY,
    NULL::VARCHAR(20)                                                        AS VAN_ORG_CD
FROM EXT_TRAN_CALL_HIS ETH
WHERE ETH.SVC_ID IN ('BALANCE_INQUIRY', 'REAL_TIME_ONLINE_INQUIRY', 'CARD_INQUIRY');

COMMENT ON VIEW V_FIRM_BANKING_HISTORY IS
    'Settings > Firm Banking History — five-leg unified read shape mirroring legacy IDO.TB_FIRM_TRAN_R002. Read-only; backed by FirmBankingHistoryView (@Immutable). Tenant scoping is the caller''s responsibility (BIZ_KEY predicate on the outer SELECT).';

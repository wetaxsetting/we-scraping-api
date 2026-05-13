-- ============================================================================
-- V102__firm_banking_history_view.sql
--
-- Creates the V_FIRM_BANKING_HISTORY view and its helper functions on the
-- shared dev DB. This is equivalent to V60 on the local DB but numbered
-- V102 to avoid collision with V84–V99 from other branches.
--
-- Idempotent: uses CREATE OR REPLACE for functions and the view.
--
-- Depends on:
--   - TB_FIRM_TRAN              (exists on shared DB from other migrations)
--   - NXSIAS_MODULE_TRAN_LOG_HIS (created by V101)
--   - NAPAS_REMIT_TRNS_HSTR     (exists on shared DB)
--   - EXT_TRAN_CALL_HIS         (exists on shared DB)
--   - TB_FIRM_BANK_CODE         (exists on shared DB)
--   - wb_get_bank_nm()          (must exist; see V44 on local DB)
-- ============================================================================

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

-- ============================================================================
-- V64__firm_banking_settings_view.sql
--
-- Settings > Firm Banking Settings — installs V_FIRM_BANKING_SETTING,
-- the read-only join view that backs the list endpoint.
--
-- Replicates legacy IDO.TB_FIRM_CONF_R004 (the IDO used by
-- comm_0220_001_r001_act.jsp) byte-for-byte:
--
--   - LEFT JOIN ACCT_MSTR ON (BIZ_KEY, WDRL_ACT_NO = ACCT_NO)
--     The account ledger lookup is by account number ONLY (no bank
--     constraint), exactly as legacy. A firm-conf row with no matching
--     acct_mstr row still appears in the list with nulls for ccy_cd /
--     bal / expi_dt.
--
--   - LEFT JOIN TB_FIRM_BANK_CODE ON (ANT_DIV_CD='NAPAS', BANK_CD = OUT_BANK_CD)
--     Adds bank_nm / bank_eng_nm for the list display. Legacy JEX
--     enriches BANK_NM frontend-side; folding it into the view here
--     simplifies the refactor and keeps the wire shape stable.
--     COALESCE(bank_nm, bank_eng_nm) prefers the localized name when
--     present (DEV has both filled for 970424/970457).
--
-- Read-only and immutable from Hibernate's perspective; backed by
-- @Immutable @Entity FirmBankingSettingView.
--
-- CREATE OR REPLACE VIEW is idempotent.
-- ============================================================================

CREATE OR REPLACE VIEW V_FIRM_BANKING_SETTING AS
SELECT
    TFC.BIZ_KEY                                 AS BIZ_KEY,
    TFC.OUT_BANK_CD                             AS OUT_BANK_CD,
    COALESCE(TFBC.BANK_NM, TFBC.BANK_ENG_NM)    AS BANK_NM,
    AM.CCY_CD                                   AS CCY_CD,
    TFC.BANK_PRTN_ID                            AS BANK_PRTN_ID,
    TFC.VAN_ORG_CD                              AS VAN_ORG_CD,
    TFC.ORG_STAT                                AS ORG_STAT,
    TFC.WDRL_ACT_NO                             AS WDRL_ACT_NO,
    TFC.FILE_TYPE                               AS FILE_TYPE,
    TFC.BAT_TRAN_BRANCH_DIV                     AS BAT_TRAN_BRANCH_DIV,
    TFC.BAT_TRAN_BRANCH_LIMIT                   AS BAT_TRAN_BRANCH_LIMIT,
    TFC.REG_DTTM                                AS REG_DTTM,
    TFC.START_DTTM                              AS START_DTTM,
    TFC.END_DTTM                                AS END_DTTM,
    TFC.MOD_DTTM                                AS MOD_DTTM,
    TFC.LAST_EDTR                               AS LAST_EDTR,
    AM.BAL                                      AS BAL,
    AM.EXPI_DT                                  AS EXPI_DT
FROM TB_FIRM_CONF TFC
LEFT JOIN ACCT_MSTR AM
    ON  TFC.BIZ_KEY     = AM.BIZ_KEY
    AND TFC.WDRL_ACT_NO = AM.ACCT_NO
LEFT JOIN TB_FIRM_BANK_CODE TFBC
    ON  TFBC.ANT_DIV_CD = 'NAPAS'
    AND TFBC.BANK_CD    = TFC.OUT_BANK_CD;

COMMENT ON VIEW V_FIRM_BANKING_SETTING IS
    'Settings > Firm Banking Settings list-view shape mirroring legacy IDO.TB_FIRM_CONF_R004. LEFT JOIN to ACCT_MSTR on (biz_key, wdrl_act_no=acct_no) and to TB_FIRM_BANK_CODE on (NAPAS, bank_cd=out_bank_cd). Read-only; backed by @Immutable FirmBankingSettingView. Tenant scoping is the caller''s responsibility (BIZ_KEY predicate enforced in repo).';

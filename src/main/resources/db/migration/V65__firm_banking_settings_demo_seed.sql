-- ============================================================================
-- V65__firm_banking_settings_demo_seed.sql
--
-- Settings > Firm Banking Settings — demo seed for local R&D.
--
-- Three TB_FIRM_CONF rows for tenant VN_VNM0004_000001 representing
-- the three banks confirmed by DEV verification on 2026-05-09 (OQ-8):
--   - 970424 Shinhan      (file_type='SHB')
--   - 970436 Vietcombank  (file_type='VCB')   ← Phase 1 V8 update;
--                                              file_type derivation
--                                              behavior for unmapped
--                                              codes is OQ-8a
--   - 970457 Woori        (file_type='WRB')
--
-- Account numbers chosen to overlap with existing ACCT_MSTR rows from
-- V13/V22 so the LEFT JOIN populates ccy_cd / bal in the list view.
--
-- Six AUDIT_LOGS_INFM rows seeded — one Create + one Update for each
-- of two firm-conf rows, EN locale only (the demo seed does not need
-- the full three-language fan-out the future writer will produce; the
-- audit-read endpoint filters by lang_cd, so seeding only EN exercises
-- the EN call path. Other locales return empty until the audit-log
-- writer is wired in a later MR).
--
-- Idempotent: INSERT … ON CONFLICT DO NOTHING.
--
-- Reference: docs/refactor-wecms/database/firm-banking-settings-schema.md §6
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. TB_FIRM_BANK_CODE — ensure the Vietcombank row exists.
--    Shinhan + Woori already seeded in V61 (firm-banking-history). We need
--    the VCB row so the list view's bank_nm JOIN populates for VCB rows.
-- ---------------------------------------------------------------------------
INSERT INTO TB_FIRM_BANK_CODE
    (BANK_CD, ANT_DIV_CD, CITAD_BANK_CD, BANK_NM, BANK_ENG_NM, ENG_ABRVAT_NM, REG_DTTM)
VALUES
    ('970436', 'NAPAS', '203',
     N'Ngân hàng TMCP Ngoại Thương Việt Nam',
     'Joint Stock Commercial Bank for Foreign Trade of Vietnam',
     'VCB',
     '20260509000000')
ON CONFLICT (BANK_CD) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. TB_FIRM_CONF — three demo rows for the configured tenant.
--    Account numbers (700005285280, 700022964690, 100912150384) are present
--    in ACCT_MSTR (V13/V22 seed) so the LEFT JOIN populates ccy_cd / bal.
-- ---------------------------------------------------------------------------
INSERT INTO TB_FIRM_CONF
    (BIZ_KEY, OUT_BANK_CD, BANK_PRTN_ID, VAN_ORG_CD, ORG_STAT, WDRL_ACT_NO,
     FILE_TYPE, BAT_TRAN_BRANCH_DIV, BAT_TRAN_BRANCH_LIMIT,
     REG_DTTM, START_DTTM, END_DTTM, MOD_DTTM, LAST_EDTR)
VALUES
    -- Shinhan binding
    ('VN_VNM0004_000001', '970424', 'WEBC1', 'GV00000001', '1',
     '700005285280', 'SHB', 'Y', 2000,
     '20260101090000', '20260101090000', '20260101090000', '20260415120000',
     'demo_user'),
    -- Vietcombank binding (the OQ-8 finding from DEV)
    ('VN_VNM0004_000001', '970436', 'WEBC2', 'GV00000002', '1',
     '700022964690', 'VCB', 'Y', 2000,
     '20260201100000', '20260201100000', '20260201100000', '20260201100000',
     'demo_user'),
    -- Woori binding
    ('VN_VNM0004_000001', '970457', 'WEBC3', 'GV00000003', '1',
     '100912150384', 'WRB', 'Y', 2000,
     '20260301110000', '20260301110000', '20260301110000', '20260402115348',
     'lamine')
ON CONFLICT (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. AUDIT_LOGS_INFM — six demo rows demonstrating the audit-read
--    endpoint's filter (REF_NO='TB_FIRM_CONF', WSVC_ID='comm_0220_001',
--    LANG_CD=caller-locale).
-- ---------------------------------------------------------------------------
INSERT INTO AUDIT_LOGS_INFM
    (BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD,
     REF_NO, REF_NO1, REF_NO2,
     AUDIT_LOGS_ACTV, AUDIT_LOGS_ACTV_DTL,
     REG_ID, REG_NM, REG_DTM)
VALUES
    -- Shinhan create
    ('VN_VNM0004_000001', 1, 'comm_0220_001', 'EN',
     'TB_FIRM_CONF', '970424', '700005285280',
     'Firm Banking Details', 'Register new firmbanking',
     'demo_user', 'demo_user', '20260101090000'),
    -- Woori create
    ('VN_VNM0004_000001', 2, 'comm_0220_001', 'EN',
     'TB_FIRM_CONF', '970457', '100912150384',
     'Firm Banking Details', 'Register new firmbanking',
     'demo_user', 'demo_user', '20260301110000'),
    -- Woori update (latest activity for that row)
    ('VN_VNM0004_000001', 3, 'comm_0220_001', 'EN',
     'TB_FIRM_CONF', '970457', '100912150384',
     'Firm Banking Details', 'Update firmbanking details',
     'lamine@gmail.com', 'lamine', '20260402115348')
ON CONFLICT (BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD) DO NOTHING;

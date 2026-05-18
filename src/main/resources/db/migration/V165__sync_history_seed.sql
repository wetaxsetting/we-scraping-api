-- ============================================================================
-- V165__sync_history_seed.sql
--
-- Bank Transaction Sync > History popup local seed (2026-05-18).
--
-- Without this seed the popup renders empty: V29 sampled 50 of 24K
-- EXT_TRAN_CALL_HIS rows but none of them carried
-- SVC_ID = 'REAL_TIME_ONLINE_INQUIRY' (the SVC_ID the sync-history
-- query filters on). This migration backfills 30 real inquiry rows
-- pulled from JEX DEV for tenant VN_VNM0004_000001.
--
-- USER_ID is rewritten to 'testuser' (the local default login) so the
-- legacy USER_ID = current-user filter in the query actually matches.
-- DEV's source rows are owned by 'lamine@gmail.com'; preserving that
-- value would render the popup empty for the default local login.
--
-- Idempotent via ON CONFLICT on the legacy PK (SVC_ID, REF_DATE, SEQ_NO).
-- ============================================================================

INSERT INTO EXT_TRAN_CALL_HIS (
    SVC_ID, REF_DATE, SEQ_NO, WEB_SVC_ID, REG_DTM, RSLT_CD, ERR_CONT,
    BIZ_KEY, USER_ID, BANK_CD, ACCT_NO, START_DATE, END_DATE, TAXCODE
) VALUES
    ('REAL_TIME_ONLINE_INQUIRY', '20260413',  1, 'REAL_TIME_ONLINE_INQUIRY', '20260413023713', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260409', '20260413', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260409',  3, 'REAL_TIME_ONLINE_INQUIRY', '20260409042719', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704020', '100200543518',    '20260317', '20260409', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260409',  2, 'REAL_TIME_ONLINE_INQUIRY', '20260409042639', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260403', '20260409', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260409',  1, 'REAL_TIME_ONLINE_INQUIRY', '20260409042520', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704020', '100200543518',    '20260401', '20260409', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260408',  3, 'REAL_TIME_ONLINE_INQUIRY', '20260408035733', '80002F31', '해당 기관의 관련 페이지 변경으로 거래에 실패하였습니다. 잠시 후 다시 거래하십시오.',                            'VN_VNM0004_000001', 'testuser', '11704020', '100200543518',    '20260401', '20260408', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260408',  2, 'REAL_TIME_ONLINE_INQUIRY', '20260408031910', '80002F31', '해당 기관의 관련 페이지 변경으로 거래에 실패하였습니다. 잠시 후 다시 거래하십시오.',                            'VN_VNM0004_000001', 'testuser', '11704020', '100200543518',    '20260401', '20260408', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260408',  1, 'REAL_TIME_ONLINE_INQUIRY', '20260408031727', '80004051', '잘못된 사용자 비밀번호 입니다. 확인 후 다시 거래하십시오.',                                                  'VN_VNM0004_000001', 'testuser', '11704020', '100200543518',    '20260108', '20260408', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260403',  4, 'REAL_TIME_ONLINE_INQUIRY', '20260403075731', 'LOGIN_CAN','You have canceled the operation.',                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260403', '20260403', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260403',  3, 'REAL_TIME_ONLINE_INQUIRY', '20260403071926', 'LOGIN_CAN','You have canceled the operation.',                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260403', '20260403', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260403',  2, 'REAL_TIME_ONLINE_INQUIRY', '20260403064446', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260301', '20260331', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260403',  1, 'REAL_TIME_ONLINE_INQUIRY', '20260403050213', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260331', '20260403', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260331',  4, 'REAL_TIME_ONLINE_INQUIRY', '20260331072746', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260201', '20260228', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260331',  3, 'REAL_TIME_ONLINE_INQUIRY', '20260331072540', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260331', '20260331', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260331',  2, 'REAL_TIME_ONLINE_INQUIRY', '20260331071510', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260326', '20260331', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260326',  3, 'REAL_TIME_ONLINE_INQUIRY', '20260326085902', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260316', '20260326', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260326',  2, 'REAL_TIME_ONLINE_INQUIRY', '20260326085009', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260316', '20260326', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260326',  1, 'REAL_TIME_ONLINE_INQUIRY', '20260326083321', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260316', '20260326', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325', 12, 'REAL_TIME_ONLINE_INQUIRY', '20260325105811', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260325', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325', 11, 'REAL_TIME_ONLINE_INQUIRY', '20260325105736', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260325', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325', 10, 'REAL_TIME_ONLINE_INQUIRY', '20260325093257', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260325', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  9, 'REAL_TIME_ONLINE_INQUIRY', '20260325092118', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260325', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  8, 'REAL_TIME_ONLINE_INQUIRY', '20260325045213', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260325', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  7, 'REAL_TIME_ONLINE_INQUIRY', '20260325044617', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260325', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  6, 'REAL_TIME_ONLINE_INQUIRY', '20260325043651', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260325', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  5, 'REAL_TIME_ONLINE_INQUIRY', '20260325042955', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260316', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  4, 'REAL_TIME_ONLINE_INQUIRY', '20260325042549', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260316', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  3, 'REAL_TIME_ONLINE_INQUIRY', '20260325035257', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260316', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  2, 'REAL_TIME_ONLINE_INQUIRY', '20260325033139', '80010011', '입력하신 로그인 정보 중 잘못 입력하신 항목이 있습니다. 확인해주십시오.',                                          'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260323', '20260325', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260325',  1, 'REAL_TIME_ONLINE_INQUIRY', '20260325015520', '80010011', '입력하신 로그인 정보 중 잘못 입력하신 항목이 있습니다. 확인해주십시오.',                                          'VN_VNM0004_000001', 'testuser', '11704088', '700005285299',    '20260101', '20260131', NULL),
    ('REAL_TIME_ONLINE_INQUIRY', '20260323',  6, 'REAL_TIME_ONLINE_INQUIRY', '20260323051324', '00000000', '',                                                                                              'VN_VNM0004_000001', 'testuser', '11704088', '700005285280',    '20260316', '20260323', NULL)
ON CONFLICT (SVC_ID, REF_DATE, SEQ_NO) DO NOTHING;

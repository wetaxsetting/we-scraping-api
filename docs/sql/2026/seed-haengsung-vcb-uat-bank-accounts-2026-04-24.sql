-- WeCMS Manual Database Change
-- Date: 2026-04-24
-- Source: migrated from Claude Code (AI)/sql/HAENGSUNG_VCB_UAT_SEED_20260424.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: UAT ONLY — the original script states DO NOT run on DEV or PROD
-- Executor: DBA TEAM
-- Rollback: Original script instructs rollback of the single transaction if the trailing verification queries do not return 5 rows each
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ========================================================================
-- HAENGSUNG_VCB_UAT_SEED_20260424.sql
-- Purpose : Seed VCB + Vikki bank accounts for Haengsung to enable
--           UAT testing of VCB firm-banking on test-wecms.haengsung.com
-- Target  : UAT database ONLY. Do NOT run on DEV or PROD.
-- Tenant  : Haengsung Electronics Vietnam (UAT biz_key = VN_VNM0004_000001)
-- prn_seq : Last value for this biz_key on UAT = 33 -> new rows use 34..38
-- Author  : Generated for Sokny Ran (songchanmoni@kosign.com.kh)
-- Date    : 2026-04-24
-- ========================================================================
--
-- Owner identifiers used inline below (confirmed from an existing UAT
-- acct_mstr row for biz_key=VN_VNM0004_000001 on 2026-04-24):
--   * reg_id / mod_id       = 'Haengsung_UAT@gmail.com'
--   * acct_auth_info.user_id = 'Haengsung_UAT@gmail.com'
-- Change only if the Haengsung UAT tester logs in with a different email.
--
-- Run as a single transaction; rollback if the verification queries at
-- the bottom don't return 5 rows each.
-- ========================================================================

BEGIN;

-- ------------------------------------------------------------------------
-- 1) acct_mstr -- 5 rows
-- ------------------------------------------------------------------------
INSERT INTO acct_mstr
    (biz_key, bank_cd, acct_no, acct_nm, ccy_cd,
     acct_type_cd, acct_cust_type, actv_yn, hand_reg_yn, remit_out_acct_use_yn,
     prn_seq,
     reg_id,                          reg_dtm,
     mod_id,                          mod_dtm)
VALUES
-- (1) Vietcombank VND -- Haengsung company account
('VN_VNM0004_000001', '11704004', '0071001098924',
 'CONG TY TNHH HAENGSUNG ELECTRONICS VIET NAM', 'VND',
 '03', 'C', 'Y', 'Y', 'N',
 34,
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS')),

-- (2) Vietcombank USD -- Haengsung company account
('VN_VNM0004_000001', '11704004', '0071371098911',
 'HAENGSUNG ELECTRONICS VIETNAM CO.,LTD', 'USD',
 '04', 'C', 'Y', 'Y', 'N',
 35,
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS')),

-- (3) Vietcombank VND -- H2H account
('VN_VNM0004_000001', '11704004', '0681000006680',
 'TK VND H2H', 'VND',
 '03', 'C', 'Y', 'Y', 'N',
 36,
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS')),

-- (4) Vikki Bank (DongA) VND -- individual beneficiary
('VN_VNM0004_000001', '11704208', '0129837294',
 'NGUYEN VAN NAPAS', 'VND',
 '03', 'C', 'Y', 'Y', 'N',
 37,
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS')),

-- (5) Vietcombank USD -- H2H account
('VN_VNM0004_000001', '11704004', '0681370008069',
 'TK USD H2H', 'USD',
 '04', 'C', 'Y', 'Y', 'N',
 38,
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'));

-- ------------------------------------------------------------------------
-- 2) acct_auth_info -- 5 rows (1:1 with acct_mstr above; auth_type='F')
-- ------------------------------------------------------------------------
INSERT INTO acct_auth_info
    (biz_key, bank_cd, acct_no, user_id, auth_type, use_yn,
     reg_id,                          reg_dtm,
     mod_id,                          mod_dtm)
VALUES
('VN_VNM0004_000001', '11704004', '0071001098924',
 'Haengsung_UAT@gmail.com', 'F', 'Y',
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS')),

('VN_VNM0004_000001', '11704004', '0071371098911',
 'Haengsung_UAT@gmail.com', 'F', 'Y',
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS')),

('VN_VNM0004_000001', '11704004', '0681000006680',
 'Haengsung_UAT@gmail.com', 'F', 'Y',
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS')),

('VN_VNM0004_000001', '11704208', '0129837294',
 'Haengsung_UAT@gmail.com', 'F', 'Y',
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS')),

('VN_VNM0004_000001', '11704004', '0681370008069',
 'Haengsung_UAT@gmail.com', 'F', 'Y',
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'),
 'Haengsung_UAT@gmail.com', to_char((now() AT TIME ZONE 'UTC'),'YYYYMMDDHH24MISS'));

-- ------------------------------------------------------------------------
-- 3) Verification -- both queries MUST return 5 rows before COMMIT
-- ------------------------------------------------------------------------
SELECT bank_cd, acct_no, acct_nm, ccy_cd, prn_seq, actv_yn
  FROM acct_mstr
 WHERE biz_key = 'VN_VNM0004_000001'
   AND bank_cd IN ('11704004','11704208')
   AND acct_no IN ('0071001098924','0071371098911','0681000006680',
                   '0129837294','0681370008069')
 ORDER BY prn_seq;

SELECT bank_cd, acct_no, user_id, auth_type, use_yn
  FROM acct_auth_info
 WHERE biz_key = 'VN_VNM0004_000001'
   AND bank_cd IN ('11704004','11704208')
   AND acct_no IN ('0071001098924','0071371098911','0681000006680',
                   '0129837294','0681370008069')
 ORDER BY bank_cd, acct_no;

COMMIT;
-- If either verification query returned fewer than 5 rows, run
--   ROLLBACK;
-- instead of COMMIT and investigate duplicates or constraint failures.

-- ========================================================================
-- Rollback helper (run separately if you need to undo this seed)
-- ========================================================================
-- BEGIN;
-- DELETE FROM acct_auth_info
--  WHERE biz_key = 'VN_VNM0004_000001'
--    AND bank_cd IN ('11704004','11704208')
--    AND acct_no IN ('0071001098924','0071371098911','0681000006680',
--                    '0129837294','0681370008069');
-- DELETE FROM acct_mstr
--  WHERE biz_key = 'VN_VNM0004_000001'
--    AND bank_cd IN ('11704004','11704208')
--    AND acct_no IN ('0071001098924','0071371098911','0681000006680',
--                    '0129837294','0681370008069');
-- COMMIT;

-- WeCMS Manual Database Change
-- Date: 2026-04-17
-- Source: migrated from Claude Code (AI)/sql/setup_uat_account_nu_apartment_20260417.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: UAT (https://test.wecms.vn/) per the original script header
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- Setup UAT Account: NU APARTMENT & HOTELS (HCM)
-- UAT: NuApartment_UAT@gmail.com / WeCMS@7777
-- Based on the same pattern as the CLT (Cyberlogitec) UAT setup.
--
-- Target server : UAT (https://test.wecms.vn/)
-- Prepared by   : RAN SOKNY
-- Prepared date : 2026-04-17
-- Ticket        : [NU APARTMENT] Set up UAT account
-- ============================================================================


-- Step 1: Get next biz_key
SELECT wb_get_next_val('BUSI_INFM', 'biz_key', 'VN', 'VNM0004', '', '', '', '') AS NEXT_VAL;


-- Step 2: Insert BUSI_INFM (replace biz_key with result from Step 1)
INSERT INTO BUSI_INFM (
    BIZ_KEY, CMPN_NM, CMPN_ENM, TAX_ID, RPPR_NM,
    TEL_NO, FXNO, EML, RPRS_BSUN_POST_ADRS, RPRS_BSUN_DTL_ADRS,
    RPRS_BSUN_CITY, RPRS_BSUN_ST, CMPN_LOGO, JOIN_DT, FST_JOIN_NM,
    FST_JOIN_TEL, FST_USE_DATE, LST_PAY_DATE, USE_DATE_FROM, USE_DATE_TO,
    BUSI_STTS, NTNL_CD, BANK_CD, ACCT_NO, OVERDUE_YN,
    CANC_DATE, FREE_USE_DATE_FROM, FREE_USE_DATE_TO, CHNL_ID, WIZ_RUN_YN,
    PRDT_TYPE, REG_ID, REG_DTM, MOD_ID, MOD_DTM,
    OWAC_NM, CMPN_QR_CD_IMG
) VALUES (
    'VN_VNM0004_000015'  -- << UPDATE with actual next val from Step 1
  , 'CÔNG TY TNHH NU APARTMENT & HOTELS (HCM)'
  , 'NU APARTMENT & HOTELS'
  , '0318575627'
  , 'THÁI ĐÌNH LÂN'
  , '+0084318575627'
  , ''
  , 'nuapartment.000@gmail.com'
  , '245 Lý Tự Trọng, Phường Bến Thành, TP Hồ Chí Minh, Việt Nam'
  , 'null'
  , 'Ho Chi Minh'
  , 'Viet Nam'
  , ''
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , 'THÁI ĐÌNH LÂN'
  , '+0084318575627'
  , ''
  , ''
  , ''
  , ''
  , 'N'
  , 'VN'
  , ''
  , ''
  , coalesce('', 'N')
  , ''
  , ''
  , ''
  , 'VNM0004'
  , 'N'
  , 'F'                  -- PRDT_TYPE: F = WeCMS Default (change to 'H' if Haengsung tier is needed)
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , ''
  , ''
);


-- Step 3: Get next empl_no (use the biz_key from Step 1)
SELECT wb_get_next_val('EMPL_INFM', 'empl_no', 'VN_VNM0004_000015', '', '', '', '', '') AS NEXT_VAL;


-- Step 4: Insert EMPL_INFM (replace biz_key & empl_no with actual values)
INSERT INTO EMPL_INFM (
    BIZ_KEY, EMPL_NO, JBCL_NM, DEPT_NM, CLPH_NO,
    EML, CMPN_JOIN_DATE, CMPN_FIRE_DATE, EMPL_RESI_ST, EMPL_RESI_CITY,
    EMPL_RESI_DTL_ADRS, EMPL_RESI_POST_ADRS, USER_IMG, NATN_ID, BANK_CD,
    ACCT_NO, MAPP_SEUP_YN, MAPP_NOTI_YN, PAYROLL_PAGE_REG_YN, WABOOKS_EMP_MNG_REG_YN,
    EMPL_NO_AUTO_GEN_YN, REG_ID, REG_DTM, MOD_ID, MOD_DTM,
    TEL_NO, R_EMPL_NO, GENDER_CD, BIRTH_DATE, NATN_NUM_CD,
    CITIZEN_NATN_NUM_CD, EMPL_IDEN_TYPE, EMER_CLPH, ENG_FST_NM, ENG_LST_NM,
    NAT_FST_NM, NAT_LST_NM, RESI_YN, MARI_YN, CHILD_DEP,
    EFIL_DEPT_NM, ACTV_YN
) VALUES (
    'VN_VNM0004_000015'  -- << UPDATE with actual biz_key
  , '20260417_001'        -- << UPDATE with actual next empl_no from Step 3
  , ''
  , ''
  , encrypt('+0084318575627')
  , 'NuApartment_UAT@gmail.com'
  , ''
  , ''
  , ''
  , ''
  , ''
  , ''
  , ''
  , ''
  , ''
  , ''
  , 'N'
  , 'N'
  , 'N'
  , 'Y'
  , coalesce('Y','N')
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , encrypt('+0084318575627')
  , '20260417_001'        -- << same as EMPL_NO
  , ''
  , ''
  , ''
  , ''
  , ''
  , encrypt('')
  , 'THAI'
  , 'DINH LAN'
  , 'THÁI'
  , 'ĐÌNH LÂN'
  , ''
  , ''
  , NULL
  , ''
  , 'Y'
);


-- Step 5: Insert USER_INFM
INSERT INTO USER_INFM (
    USER_ID, USER_NM, CLPH_NO, EML, USER_PW,
    ACTV_YN, LOCK_YN, PW_CHG_REQ_SEQ_NO, REG_ID, REG_DTM,
    MOD_ID, MOD_DTM, DEL_YN
) VALUES (
    'NuApartment_UAT@gmail.com'
  , 'THÁI ĐÌNH LÂN'
  , encrypt('+0084318575627')
  , 'NuApartment_UAT@gmail.com'
  , hash('WeCMS@7777')
  , 'Y'
  , 'N'
  , NULL
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , 'N'
);


-- Step 6: Insert BUSI_ADMN_INFM
INSERT INTO BUSI_ADMN_INFM (
    BIZ_KEY, USER_ID, USER_NM, REG_ID, REG_DTM, MOD_ID, MOD_DTM
) VALUES (
    'VN_VNM0004_000015'  -- << UPDATE with actual biz_key
  , 'NuApartment_UAT@gmail.com'
  , 'THÁI ĐÌNH LÂN'
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
);


-- Step 7: Insert USER_BUSI_CONN_INFM
INSERT INTO USER_BUSI_CONN_INFM (
    USER_ID, BIZ_KEY, EMPL_NO, USER_IMG, REG_ID, REG_DTM, MOD_ID, MOD_DTM, DEL_YN
) VALUES (
    'NuApartment_UAT@gmail.com'
  , 'VN_VNM0004_000015'  -- << UPDATE with actual biz_key
  , '20260417_001'        -- << UPDATE with actual empl_no from Step 3
  , ''
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , 'SYSTEM'
  , to_char(timezone('Asia/Ho_Chi_Minh', now()), 'YYYYMMDDHH24MISS')
  , 'N'
);

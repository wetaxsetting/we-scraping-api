-- WeCMS Manual Database Change
-- Date: 2026-08-13
-- Source: migrated from Claude Code (AI)/sql/fnif_0002_001_hansungbolt_uat_accounts_batch4_20260813.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: UAT (test.wecms.vn) ONLY — the original script states NOT DEV, NOT PROD
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — sectioned transactions; no rollback script documented
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =====================================================================================
-- [HANSUNGBOLT] UAT — Shinhan firm-banking test accounts, BATCH 4 (gap fill)
-- Closes the gaps between the SHINHAN test-data matrix and what is in ACCT_MSTR.
--
-- Target DB   : UAT (test.wecms.vn)          -- NOT DEV, NOT PROD
-- BIZ_KEY     : VN_VNM0004_000020            -- HANSUNGBOLT (PRDT_TYPE = 'F')
-- Author      : Claude Code (AI) for Sonny
-- Date        : 2026-08-13
-- Run by      : DBA, by hand. NOT a Flyway migration.
-- Run order   : batch 3 script FIRST, then Section 0 -> 1 -> 2 -> 3 here.
--
-- Batch history:
--   batch 1  4 Shinhan HANSUNGBOLT VINA accounts (Withdrawal)   PRN_SEQ ..10   DONE
--   batch 2  4 domestic beneficiaries (Deposit - Domestic)       PRN_SEQ 11..14 DONE
--   batch 3  1 Kookmin + 3 payroll (Payroll / Overseas)          PRN_SEQ 15..18 PENDING
--            -> fnif_0002_001_hansungbolt_uat_accounts_batch3_20260813.sql
--   batch 4  this file — 2 missing Internal-Transfer accounts + 1 name fix
--
-- WHAT THIS FILE ADDS (both were absent from every earlier request):
--   ShinhanBank  CONG TY TNHH WEBCASH VIET NAM  700071155641  VND
--   ShinhanBank  CONG TY TNHH WEBCASH VIET NAM  700005285299  USD
--
-- WHAT THIS FILE CORRECTS:
--   NAPAS00ATPB — inserted in batch 2 with ACCT_NM 'ATPB'; the matrix says 'NAPAS'.
--
-- STILL NOT COVERED BY ANY SQL — see Section 4:
--   The Deposit-Overseas row's BIC / Beneficiary Address / Town Name. ACCT_MSTR has
--   no columns for them; they belong in Contacts (TRCO_INFM + TRCO_OVRS_INFM).
--
-- NOTHING TO DO for the matrix's Napas Code / Citad Code columns — already seeded:
--   NAPAS (napas_bank_info):  Shinhan 970424 · Vietin 970415 · BIDV 970418
--                             Vietcombank 970436 · KEBHana-HN 970467 · KB-HN 970462
--   CITAD (citad_bank_cd_lookup): KEBHana 01626001 (the matrix's "626") ·
--                             Shinhan 79616001 · Vietin 01201001 · BIDV 01202001 ·
--                             Vietcombank 01203001 · Kookmin 01666001
--   BIC (swift_bic_mst): CZNBKRSEXXX present, town SEOUL, country KR.
--
-- Column rules (unchanged, from legacy behaviour):
--   ACCT_TYPE_CD  VND -> '03' · USD -> '04'   (wabooks/enums/AcctTypeCd.java)
--   HAND_REG_YN   'Y' — these are WEBCASH VIET NAM's accounts, not HANSUNGBOLT's.
--                 They must NOT carry HANSUNGBOLT's Shinhan BANK_CERT_SEQ; the
--                 scraper would try to fetch accounts the company cannot access.
--                 Consequence: HAND_REG_YN='Y' keeps them out of the Transfer
--                 screen's "My Accounts" tab (trns_0001_005_r001_act.jsp:105 forces
--                 HAND_REG_YN='N'). That is correct — "My Accounts" means the
--                 company's own accounts. See the note in Section 1.
--   REG_DTM       UTC yyyyMMddHHmmss — the JEX DB session zone is Asia/Seoul, so
--                 plain NOW() would store a time 9 hours ahead.
--   PRN_SEQ       computed as max+1 per BIZ_KEY (the wb_get_next_val rule), so this
--                 file is safe to run whether or not batch 3 has landed.
-- =====================================================================================


-- =====================================================================================
-- SECTION 0 — PRE-FLIGHT CHECKS
-- =====================================================================================

-- 0.1 Where PRN_SEQ currently stands. 14 = batch 3 not yet run; 18 = batch 3 done.
SELECT COALESCE(MAX(prn_seq), 0) AS current_max_prn_seq,
       COUNT(*)                  AS account_count
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020';

-- 0.2 The 2 accounts must NOT already exist. Expect 0 rows.
SELECT bank_cd, acct_no, acct_nm, ccy_cd, acct_type_cd, prn_seq
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('700071155641','700005285299');

-- 0.3 Full matrix reconciliation — every account the SHINHAN sheet lists, and
--     whether it is in ACCT_MSTR yet. Run this before and after.
--     Expect after batch 3 + batch 4: all 14 rows with in_acct_mstr = 'Y'.
WITH matrix (acct_type, bank_cd, acct_no, acct_nm, ccy_cd) AS (
    VALUES ('Withdrawal',        '11704088', '700006294670', 'CONG TY CP HANSUNGBOLT VINA',    'VND'),
           ('Withdrawal',        '11704088', '700012464956', 'CONG TY CP HANSUNGBOLT VINA',    'VND'),
           ('Withdrawal',        '11704088', '700006294689', 'CONG TY CP HANSUNGBOLT VINA',    'USD'),
           ('Withdrawal',        '11704088', '700006294696', 'CONG TY CP HANSUNGBOLT VINA',    'USD'),
           ('Internal Transfer', '11704088', '700071155641', 'CONG TY TNHH WEBCASH VIET NAM',  'VND'),
           ('Internal Transfer', '11704088', '700005285299', 'CONG TY TNHH WEBCASH VIET NAM',  'USD'),
           ('Domestic',          '11704080', 'NAPAS00ATPB',  'NAPAS',                          'VND'),
           ('Domestic',          '11704001', '0421404333',   'TRAN THI NGOC MINH',             'VND'),
           ('Domestic',          '11704008', '17902455',     'NGUYEN YEN CHAU',                'VND'),
           ('Domestic',          '11704004', '107206577',    'LEE YEONG SEOK',                 'USD'),
           ('Payroll',           '11704088', '700071130135', 'TRAN THUY LIEN',                 'VND'),
           ('Payroll',           '11704004', '0421456789',   'TRAN NGOC HANG',                 'VND'),
           ('Payroll',           '11704088', '700071130142', 'NGUYEN THI HANH',                'VND'),
           ('Overseas',          '11704013', '00606711008879','UIB INSURANCE BROKERS CO., LTD','USD')
)
SELECT x.acct_type,
       x.bank_cd,
       x.acct_no,
       x.acct_nm                             AS expected_acct_nm,
       m.acct_nm                             AS actual_acct_nm,
       CASE WHEN m.acct_no IS NULL THEN 'N' ELSE 'Y' END AS in_acct_mstr,
       CASE WHEN m.acct_no IS NOT NULL AND COALESCE(m.acct_nm,'') <> x.acct_nm
            THEN 'NAME MISMATCH' END         AS flag,
       m.ccy_cd, m.acct_type_cd, m.prn_seq
FROM   matrix x
LEFT   JOIN acct_mstr m
       ON  m.biz_key = 'VN_VNM0004_000020'
       AND m.bank_cd = x.bank_cd
       AND m.acct_no = x.acct_no
ORDER  BY x.acct_type, x.acct_no;


-- =====================================================================================
-- SECTION 1 — INSERT INTO ACCT_MSTR   (the 2 missing Internal-Transfer accounts)
-- =====================================================================================
-- These are CONG TY TNHH WEBCASH VIET NAM's accounts at Shinhan, used as
-- same-bank (Shinhan -> Shinhan) transfer destinations in the test matrix.
-- "Internal Transfer" in this sheet means same-bank, not own-account: Shinhan's API
-- splits Internal (same bank) from Domestic (other bank via NAPAS/CITAD).
-- They are therefore beneficiary accounts -> HAND_REG_YN='Y', BANK_CERT_SEQ NULL.
--
-- If the testers instead expect them under the Transfer screen's "My Accounts" tab,
-- that tab hard-filters HAND_REG_YN='N' and would need these rows flipped — but
-- that would also attach them to HANSUNGBOLT's Shinhan credential and break the
-- scraper. Raise it before changing; do not flip it silently.
-- -------------------------------------------------------------------------------------

BEGIN;

WITH base AS (
    SELECT 'VN_VNM0004_000020'::varchar(20)          AS biz_key,
           'Hansungbolt_UAT@gmail.com'::varchar(100) AS reg_user,
           to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS') AS now_utc,
           (SELECT COALESCE(MAX(m.prn_seq), 0)
              FROM acct_mstr m
             WHERE m.biz_key = 'VN_VNM0004_000020') AS prn_base
),
v (ord, bank_cd, acct_no, ccy_cd, acct_type_cd, acct_nm) AS (
    VALUES
      (1, '11704088', '700071155641', 'VND', '03', 'CONG TY TNHH WEBCASH VIET NAM'),
      (2, '11704088', '700005285299', 'USD', '04', 'CONG TY TNHH WEBCASH VIET NAM')
)
INSERT INTO acct_mstr (
    biz_key, bank_cd, acct_no, acct_type_cd, acct_dv_cd, ccy_cd,
    agmt_amt, corc_bf_bal, bal, real_amt, pyat_amt,
    acct_nick_nm, acct_nm, cont,
    hand_reg_yn, actv_yn, acct_cust_type, remit_out_acct_use_yn,
    bank_cert_seq, prn_seq,
    reg_id, reg_dtm, mod_id, mod_dtm
)
SELECT base.biz_key,
       v.bank_cd,
       v.acct_no,
       v.acct_type_cd::bpchar,
       NULL::bpchar,                       -- ACCT_DV_CD
       v.ccy_cd::bpchar,
       0, 0, 0, 0, 0,
       NULL,                               -- ACCT_NICK_NM
       v.acct_nm,
       NULL,                               -- CONT
       'Y',                                -- HAND_REG_YN
       'Y',                                -- ACTV_YN
       'C',                                -- ACCT_CUST_TYPE
       'N',                                -- REMIT_OUT_ACCT_USE_YN
       NULL,                               -- BANK_CERT_SEQ
       base.prn_base + v.ord,              -- PRN_SEQ = max+1, max+2
       base.reg_user, base.now_utc, base.reg_user, base.now_utc
FROM   base
CROSS  JOIN v
ORDER  BY v.ord;
-- Expect: INSERT 0 2

COMMIT;


-- ---------------------------------------------------------------------------
-- 1B. NAME FIX — batch 2 stored ACCT_NM 'ATPB'; the matrix says 'NAPAS'.
--     Run only if check 0.3 flagged NAME MISMATCH on NAPAS00ATPB.
--     ACCT_NM is not part of the PK, so a plain UPDATE is safe.
-- ---------------------------------------------------------------------------

BEGIN;

UPDATE acct_mstr
SET    acct_nm = 'NAPAS',
       mod_id  = 'Hansungbolt_UAT@gmail.com',
       mod_dtm = to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')
WHERE  biz_key = 'VN_VNM0004_000020'
AND    bank_cd = '11704080'
AND    acct_no = 'NAPAS00ATPB'
AND    COALESCE(acct_nm, '') <> 'NAPAS';
-- Expect: UPDATE 1

COMMIT;


-- =====================================================================================
-- SECTION 2 — INSERT INTO ACCT_AUTH_INFO   (6 rows = 2 accounts x 3 users)
-- =====================================================================================
-- AUTH_TYPE 'V' only, USE_YN 'Y' — same basis as batches 2 and 3. All 3 users are
-- admins and already have full access; these rows only make the state explicit.
-- -------------------------------------------------------------------------------------

BEGIN;

WITH p AS (
    SELECT 'VN_VNM0004_000020'::varchar(20)  AS biz_key,
           'Hansungbolt_UAT@gmail.com'::varchar(100) AS reg_user
),
tgt_acct (bank_cd, acct_no) AS (
    VALUES ('11704088', '700071155641'),     -- CONG TY TNHH WEBCASH VIET NAM / VND
           ('11704088', '700005285299')      -- CONG TY TNHH WEBCASH VIET NAM / USD
),
tgt_user (user_id) AS (
    VALUES ('Hansungbolt_UAT@gmail.com'),    -- Kim Do Joon
           ('joonyjoo@hanmail.net'),         -- Kim Do Joon
           ('ok7308@hansungbolt.com')        -- Ok Kyeong Hwan
)
INSERT INTO acct_auth_info (
    biz_key, bank_cd, acct_no, user_id, auth_type,
    use_yn, reg_id, reg_dtm, mod_id, mod_dtm
)
SELECT p.biz_key, a.bank_cd, a.acct_no, u.user_id,
       'V', 'Y', p.reg_user,
       to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
       p.reg_user,
       to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')
FROM   p
CROSS  JOIN tgt_acct a
CROSS  JOIN tgt_user u
ON CONFLICT (biz_key, bank_cd, acct_no, user_id, auth_type) DO NOTHING;
-- Expect: INSERT 0 6

COMMIT;


-- =====================================================================================
-- SECTION 3 — POST-RUN VERIFICATION
-- =====================================================================================

-- 3.1 Re-run the matrix reconciliation from 0.3. After batch 3 + batch 4 every row
--     should read in_acct_mstr = 'Y' with no NAME MISMATCH flag.

-- 3.2 The 2 new accounts + their grants.
SELECT m.prn_seq, m.bank_cd, m.acct_no, m.acct_nm, m.ccy_cd, m.acct_type_cd,
       m.hand_reg_yn, m.actv_yn, m.bank_cert_seq,
       (SELECT COUNT(*) FROM acct_auth_info a
         WHERE a.biz_key = m.biz_key AND a.bank_cd = m.bank_cd
           AND a.acct_no = m.acct_no AND a.auth_type = 'V' AND a.use_yn = 'Y') AS v_grants
FROM   acct_mstr m
WHERE  m.biz_key = 'VN_VNM0004_000020'
AND    m.acct_no IN ('700071155641','700005285299')
ORDER  BY m.prn_seq;
-- Expect: 2 rows, v_grants = 3 each

-- 3.3 Full account list. Expect 20 rows once batch 3 and batch 4 have both run.
SELECT prn_seq, bank_cd, acct_no, acct_nm, ccy_cd, acct_type_cd, hand_reg_yn, bank_cert_seq
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
ORDER  BY prn_seq;


-- =====================================================================================
-- SECTION 4 — NOT DONE BY SQL: the Deposit-Overseas beneficiary details
-- =====================================================================================
-- Matrix row:
--   Deposit - Overseas · KOOKMIN BANK · UIB INSURANCE BROKERS CO., LTD
--   · 00606711008879 · USD · BIC CZNBKRSEXXX · Address KOREA · Town SEOUL
--
-- ACCT_MSTR holds none of BIC / Beneficiary Address / Town Name — the table has no
-- such columns. Batch 3 stores only the bank, number, name and currency.
-- Overseas beneficiary detail lives in Contacts:
--   TRCO_INFM       (contact master, supplies TRCO_KEY)
--   TRCO_OVRS_INFM  PK (TRCO_KEY, BIZ_KEY, TRCO_OVRS_KEY)
--                   BENE_NAME · BIC_CODE · BENE_ACCT_NO · BENE_ADDR ·
--                   BENE_TOWN_NM · BENE_BANK_CODE · BENE_BANK_NAME
--   TRCO_OVRS_KEY = max(TRCO_OVRS_KEY::numeric)+1 per BIZ_KEY
--                   (wb_get_next_val 'trco_ovrs_infm'/'trco_ovrs_key')
--
-- RECOMMENDATION: register this one through the UI (Contacts -> Overseas) rather
-- than by SQL. The screen's BIC dropdown is fed from SWIFT_BIC_MST, where
-- CZNBKRSEXXX already exists with TOWN 'SEOUL' / COUNTRY 'KR', so the Town field
-- auto-fills correctly and the contact is linked to a proper TRCO_INFM parent.
-- Doing it by hand means inventing a TRCO_KEY and a contact master row, which is
-- easy to get wrong and hard to unpick.
-- Ask if you want the SQL anyway and I will write it against TRCO_INFM.
-- =====================================================================================


-- =====================================================================================
-- SECTION 5 — ROLLBACK (batch 4 only)
-- =====================================================================================
/*
BEGIN;

DELETE FROM acct_auth_info
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('700071155641','700005285299');

DELETE FROM acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('700071155641','700005285299');

-- revert the name fix
UPDATE acct_mstr
SET    acct_nm = 'ATPB',
       mod_id  = 'Hansungbolt_UAT@gmail.com',
       mod_dtm = to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')
WHERE  biz_key = 'VN_VNM0004_000020'
AND    bank_cd = '11704080'
AND    acct_no = 'NAPAS00ATPB';

COMMIT;
*/

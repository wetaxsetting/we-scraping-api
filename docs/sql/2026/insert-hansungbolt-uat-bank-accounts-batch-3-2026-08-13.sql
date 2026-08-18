-- WeCMS Manual Database Change
-- Date: 2026-08-13
-- Source: migrated from Claude Code (AI)/sql/fnif_0002_001_hansungbolt_uat_accounts_batch3_20260813.sql
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
-- [HANSUNGBOLT] UAT — Shinhan firm-banking test accounts, BATCH 3
-- Insert 4 accounts into ACCT_MSTR + grant ACCT_AUTH_INFO to the 3 UAT users
--
-- Target DB   : UAT (test.wecms.vn)          -- NOT DEV, NOT PROD
-- BIZ_KEY     : VN_VNM0004_000020            -- HANSUNGBOLT (PRDT_TYPE = 'F')
-- Author      : Claude Code (AI) for Sonny
-- Date        : 2026-08-13
-- Run by      : DBA, by hand. NOT a Flyway migration; nothing auto-applies this.
-- Run order   : Section 0 -> Section 1 -> Section 2 -> Section 3
--               Section 1 before Section 2 (FK acct_auth_info -> acct_mstr).
--
-- Previous batches (see fnif_0002_001_hansungbolt_uat_shinhan_20260812.sql):
--   batch 1 — 4 Shinhan company accounts        PRN_SEQ  ..10   DONE
--   batch 2 — 4 beneficiary accounts            PRN_SEQ 11..14  DONE
--   batch 3 — this file                         PRN_SEQ 15..18
--
-- Accounts in this batch (all BENEFICIARY accounts — none belong to HANSUNGBOLT):
--   KOOKMIN BANK  UIB INSURANCE BROKERS CO., LTD  00606711008879  USD
--   ShinhanBank   TRAN THUY LIEN                  700071130135    VND
--   Vietcombank   TRAN NGOC HANG                  0421456789      VND
--   ShinhanBank   NGUYEN THI HANH                 700071130142    VND
--
-- Bank codes resolved from BANK_INFM_V2 (the BEFORE-INSERT trigger
-- reject_not_existing_bank_cd() rejects anything not in that view):
--   11704013  Kookmin(Vietnam)   — one code covers both KB Hanoi and KB HCM branches
--   11704088  Shinhan(Vietnam)
--   11704004  Vietcom Bank
--
-- Column rules (unchanged from the earlier batches, all from legacy behaviour):
--   ACCT_TYPE_CD  VND -> '03' (local-ccy current) · USD -> '04' (FCY current)
--                 per wabooks/enums/AcctTypeCd.java
--   ACCT_DV_CD    NULL — CHECK acct_mstr_chk02 only constrains it for type 01/02
--   HAND_REG_YN   'Y' — manual registration, no bank credential. Correct even for
--                 the two ShinhanBank rows: those accounts belong to individuals,
--                 not to HANSUNGBOLT, so they must never be attached to the
--                 company's Shinhan BANK_CERT_SEQ or the scraper will try to fetch
--                 them and fail.
--   REG_DTM       UTC yyyyMMddHHmmss (DateTimeUtils.utcNow). The JEX DB session zone
--                 is Asia/Seoul, so timezone('utc', now()) is required — plain NOW()
--                 would store a time 9 hours ahead.
--   PRN_SEQ       15..18, continuing from the current max of 14
--                 (same rule as wb_get_next_val('acct_mstr','prn_seq',biz_key))
--
-- ⚠ TWO THINGS TO CONFIRM BEFORE RUNNING — see Section 0.5 and the note in Section 2.
-- =====================================================================================


-- =====================================================================================
-- SECTION 0 — PRE-FLIGHT CHECKS
-- =====================================================================================

-- 0.1 Confirm max(PRN_SEQ) is still 14. Section 1 hardcodes 15..18 on that basis.
--     If this returns anything else, shift the four PRN_SEQ literals accordingly.
SELECT COALESCE(MAX(prn_seq), 0) AS current_max_prn_seq
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020';
-- Expect: 14

-- 0.2 The 4 accounts must NOT already exist (PK = biz_key + bank_cd + acct_no).
--     Expect 0 rows.
SELECT bank_cd, acct_no, acct_nm, ccy_cd, acct_type_cd, prn_seq
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('00606711008879','700071130135','0421456789','700071130142');

-- 0.3 The bank codes must exist in BANK_INFM_V2. Expect 3 rows.
SELECT bank_cd, bank_nm
FROM   bank_infm_v2
WHERE  bank_cd IN ('11704013','11704088','11704004')
ORDER  BY bank_cd;

-- 0.4 Current state of the company's accounts, for context before adding 4 more.
SELECT prn_seq, bank_cd, acct_no, acct_nm, ccy_cd, acct_type_cd, hand_reg_yn, bank_cert_seq
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
ORDER  BY prn_seq;

-- 0.5 ⚠ CONFIRM THE BANK FOR '0421456789' BEFORE RUNNING SECTION 1.
--     The request lists it under Vietcombank, but the number shares the '0421'
--     prefix with the VietinBank account from batch 2 (0421404333 / TRAN THI NGOC
--     MINH), and Vietcombank account numbers on this tenant look nothing like it
--     (e.g. 0441000688210, 1051996380, 1063895376).
--     BANK_CD is part of the primary key, so getting it wrong means DELETE +
--     re-INSERT rather than a simple UPDATE.
--     This query shows the number-shape precedent per bank — check it, then either
--     keep '11704004' in Section 1 or change that one row to '11704001' (Vietin).
SELECT bank_cd, acct_no, acct_nm
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    bank_cd IN ('11704004','11704001')
ORDER  BY bank_cd, acct_no;


-- =====================================================================================
-- SECTION 1 — INSERT INTO ACCT_MSTR   (4 rows, PRN_SEQ 15..18)
-- =====================================================================================

BEGIN;

INSERT INTO acct_mstr (
    biz_key, bank_cd, acct_no, acct_type_cd, acct_dv_cd, ccy_cd,
    agmt_amt, corc_bf_bal, bal, real_amt, pyat_amt,
    acct_nick_nm, acct_nm, cont,
    hand_reg_yn, actv_yn, acct_cust_type, remit_out_acct_use_yn,
    bank_cert_seq, prn_seq,
    reg_id, reg_dtm, mod_id, mod_dtm
) VALUES
-- KOOKMIN BANK / UIB INSURANCE BROKERS CO., LTD / USD
('VN_VNM0004_000020', '11704013', '00606711008879', '04', NULL, 'USD',
 0, 0, 0, 0, 0,
 NULL, 'UIB INSURANCE BROKERS CO., LTD', NULL,
 'Y', 'Y', 'C', 'N',
 NULL, 15,
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')),

-- ShinhanBank / TRAN THUY LIEN / VND
('VN_VNM0004_000020', '11704088', '700071130135', '03', NULL, 'VND',
 0, 0, 0, 0, 0,
 NULL, 'TRAN THUY LIEN', NULL,
 'Y', 'Y', 'C', 'N',
 NULL, 16,
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')),

-- Vietcombank / TRAN NGOC HANG / VND        <<< see check 0.5 before running
('VN_VNM0004_000020', '11704004', '0421456789', '03', NULL, 'VND',
 0, 0, 0, 0, 0,
 NULL, 'TRAN NGOC HANG', NULL,
 'Y', 'Y', 'C', 'N',
 NULL, 17,
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')),

-- ShinhanBank / NGUYEN THI HANH / VND
('VN_VNM0004_000020', '11704088', '700071130142', '03', NULL, 'VND',
 0, 0, 0, 0, 0,
 NULL, 'NGUYEN THI HANH', NULL,
 'Y', 'Y', 'C', 'N',
 NULL, 18,
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'));
-- Expect: INSERT 0 4

COMMIT;


-- =====================================================================================
-- SECTION 2 — INSERT INTO ACCT_AUTH_INFO   (12 rows = 4 accounts x 3 users)
-- =====================================================================================
-- AUTH_TYPE 'V' only, same reasoning as batch 2:
--   - no query in the system requires 'W'; every consumer treats V and W as an OR,
--     never an AND (trns_0001_001_r001_act.jsp:87,166 · pymn_0007_002_r002_act.jsp:77,123
--     · comm_1001_001_act.jsp:51), so a 'W' row grants nothing a 'V' row has not;
--   - Kookmin (11704013) is NOT on the withdraw whitelist, so the permission screen
--     would reject 'W' there anyway (comm_0183_002_u001_act.jsp:160-192, WBE0452);
--   - these are beneficiary accounts — money goes TO them, never FROM them.
--
-- All 3 users are ADMINS (verified on UAT 2026-08-12), so they already have full
-- access without any row here. These 12 rows are bookkeeping — they make the
-- permission state explicit but change nothing the users can do.
--
-- ⚠ SIDE EFFECT ON THE TRANSFER SCREEN. trns_0001_001_r001_act.jsp lists withdrawal
--   accounts as: VND + ACTV_YN='Y' + ACCT_TYPE_CD in ('03','05') + bank in
--   (Shinhan, IBK, Vietcom, Vietin, Woori) + a V-or-W grant. Three rows from this
--   batch match that filter — TRAN THUY LIEN, NGUYEN THI HANH (both Shinhan) and
--   TRAN NGOC HANG (Vietcom). They will appear in the withdrawal-account dropdown
--   next to HANSUNGBOLT's four real Shinhan accounts, which testers may pick by
--   mistake. Together with batch 2's Vietin account that is 4 beneficiary accounts
--   in the source list. Kookmin is unaffected (not whitelisted, and USD).
--   If that is a problem, the correct home for these is Contacts
--   (TRCO_INFM / TRCO_ACCT_INFO) — check 3.3 shows you the resulting list.
--
-- ON CONFLICT DO NOTHING makes this re-runnable; it never overwrites a live grant.
-- -------------------------------------------------------------------------------------

BEGIN;

WITH p AS (
    SELECT 'VN_VNM0004_000020'::varchar(20)  AS biz_key,
           'Hansungbolt_UAT@gmail.com'::varchar(100) AS reg_user
),
tgt_acct (bank_cd, acct_no) AS (
    VALUES ('11704013', '00606711008879'),   -- KOOKMIN BANK / UIB INSURANCE BROKERS CO., LTD / USD
           ('11704088', '700071130135'),     -- ShinhanBank  / TRAN THUY LIEN                 / VND
           ('11704004', '0421456789'),       -- Vietcombank  / TRAN NGOC HANG                 / VND
           ('11704088', '700071130142')      -- ShinhanBank  / NGUYEN THI HANH                / VND
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
SELECT p.biz_key,
       a.bank_cd,
       a.acct_no,
       u.user_id,
       'V',                                                     -- AUTH_TYPE
       'Y',                                                     -- USE_YN
       p.reg_user,
       to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),      -- UTC, not NOW()
       p.reg_user,
       to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')
FROM   p
CROSS  JOIN tgt_acct a
CROSS  JOIN tgt_user u
ON CONFLICT (biz_key, bank_cd, acct_no, user_id, auth_type) DO NOTHING;
-- Expect: INSERT 0 12

COMMIT;


-- =====================================================================================
-- SECTION 3 — POST-RUN VERIFICATION
-- =====================================================================================

-- 3.1 The 4 new accounts. Check CCY_CD / ACCT_TYPE_CD / PRN_SEQ 15..18.
SELECT prn_seq, bank_cd, acct_no, acct_nm, ccy_cd, acct_type_cd, acct_dv_cd,
       hand_reg_yn, actv_yn, acct_cust_type, bank_cert_seq, reg_dtm
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('00606711008879','700071130135','0421456789','700071130142')
ORDER  BY prn_seq;
-- Expect: 4 rows

-- 3.2 Grants. Expect 4 rows, each auth_type='V', use_yn='Y', users=3.
SELECT bank_cd, acct_no, auth_type, use_yn, COUNT(*) AS users
FROM   acct_auth_info
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('00606711008879','700071130135','0421456789','700071130142')
GROUP  BY bank_cd, acct_no, auth_type, use_yn
ORDER  BY bank_cd, acct_no;

-- 3.3 The Transfer screen's withdrawal-account list as a tester will see it
--     (trns_0001_001_r001_act.jsp). HANSUNGBOLT's 2 real Shinhan VND accounts
--     (700006294670, 700012464956) must be here; everything else in the result is
--     a beneficiary account showing up as a selectable source — see the warning
--     in Section 2.
SELECT m.prn_seq, m.bank_cd, m.acct_no, m.acct_nm, m.ccy_cd, m.hand_reg_yn
FROM   acct_mstr m
WHERE  m.biz_key      = 'VN_VNM0004_000020'
AND    m.ccy_cd       = 'VND'
AND    m.actv_yn      = 'Y'
AND    m.acct_type_cd IN ('03','05')
AND    m.bank_cd      IN ('11704088','11704003','11704004','11704001','11704020')
AND    EXISTS (SELECT 1 FROM acct_auth_info a
                WHERE a.biz_key = m.biz_key
                  AND a.bank_cd = m.bank_cd
                  AND a.acct_no = m.acct_no
                  AND a.user_id = 'Hansungbolt_UAT@gmail.com'
                  AND a.use_yn  = 'Y'
                  AND a.auth_type IN ('V','W'))
ORDER  BY m.prn_seq;

-- 3.4 Full account list for the company after this batch. Expect 18 rows.
SELECT prn_seq, bank_cd, acct_no, acct_nm, ccy_cd, acct_type_cd, hand_reg_yn, bank_cert_seq
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
ORDER  BY prn_seq;


-- =====================================================================================
-- SECTION 4 — ROLLBACK (batch 3 only)
-- =====================================================================================
-- Children first, then ACCT_MSTR (FK acct_auth_info -> acct_mstr).
-- Deleting from ACCT_MSTR fires acct_mstr_log(), which also cleans the matching
-- DAILY_CASH_REPT_DAILY_SUMR_DETL rows for today.
/*
BEGIN;

DELETE FROM acct_auth_info
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('00606711008879','700071130135','0421456789','700071130142');

DELETE FROM acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('00606711008879','700071130135','0421456789','700071130142');

COMMIT;
*/

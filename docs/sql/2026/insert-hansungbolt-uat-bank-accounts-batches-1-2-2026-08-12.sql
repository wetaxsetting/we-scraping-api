-- WeCMS Manual Database Change
-- Date: 2026-08-12
-- Source: migrated from Claude Code (AI)/sql/fnif_0002_001_hansungbolt_uat_shinhan_20260812.sql
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
-- [HANSUNGBOLT] UAT prep for Shinhan firm-banking testing
-- Insert bank accounts into ACCT_MSTR + grant per-user ACCT_AUTH_INFO
--
-- Target DB   : UAT (test.wecms.vn)          -- NOT DEV, NOT PROD
-- BIZ_KEY     : VN_VNM0004_000020            -- HANSUNGBOLT (PRDT_TYPE = 'F')
-- Author      : Claude Code (AI) for Sonny
-- Date        : 2026-08-12
-- Rev         : 2026-08-12 r2 — the 4 Shinhan accounts are ALREADY INSERTED;
--                               current max(PRN_SEQ) for this BIZ_KEY = 10.
--                               Section 1A is now verify-only; 1B starts at PRN_SEQ 11.
--               2026-08-12 r3 — Section 2 rewritten against the real registration
--                               logic in fnsh_0101_001_c001_act.jsp: ACCT_AUTH_INFO
--                               fans out to ALL users of the BIZ_KEY, not the admin
--                               alone, and 'V'/'W' are now split (r2 wrongly granted
--                               'W' on KEBHana/BIDV, which the UI rejects).
--               2026-08-12 r4 — Section 2 re-verified against JEX DEV data. The
--                               fan-out writes a row per user but USE_YN='Y' for the
--                               registering user ONLY, and admins need no row at all.
--                               r3 granted USE_YN='Y' to every user — over-granting.
--                               Section 2 is now 2.0 admin check / 2A optional legacy
--                               fan-out / 2B targeted tester grant.
--               2026-08-12 r5 — Shinhan ACCT_AUTH_INFO is DONE. Section 2 narrowed to
--                               the 4 remaining accounts (KEBHana / Vietin / BIDV /
--                               Vietcombank) x the 3 UAT users, AUTH_TYPE 'V' only.
--                               The optional legacy fan-out block was dropped.
-- Run by      : DBA, by hand. This is NOT a Flyway migration and must not be
--               auto-applied by any application on startup.
-- Run order   : Section 0 (verify) -> Section 1B (optional) -> Section 2 -> Section 3
--               Any ACCT_MSTR row must exist before Section 2 grants it
--               (FK acct_auth_info -> acct_mstr).
--
-- Legacy source of truth for the values used here:
--   fnif_0002_003_c001_act.jsp  (online/bank-registered account  -> HAND_REG_YN='N', BANK_CERT_SEQ set)
--   fnif_0002_004_c001_act.jsp  (manual "수기등록" account        -> HAND_REG_YN='Y', BANK_CERT_SEQ null)
--   fnif_0003_001_u001_act.jsp  (account permission grant        -> ACCT_AUTH_INFO upsert)
--   wabooks/enums/AcctTypeCd.java : 03 = local-ccy current (VND), 04 = FCY current (USD)
--   wabooks/enums/AuthType.java   : V = View, W = Withdraw, F = Full
--   DB function wb_get_next_val('acct_mstr','prn_seq',biz_key) = max(PRN_SEQ)+1 scoped by BIZ_KEY
-- =====================================================================================


-- =====================================================================================
-- SECTION 0 — PRE-FLIGHT CHECKS  (run all, read the results, then continue)
-- =====================================================================================

-- 0.1 Confirm max(PRN_SEQ) is still 10. Section 1B hardcodes 11..14 on that basis.
--     If this returns anything other than 10, shift the four PRN_SEQ literals in 1B.
SELECT COALESCE(MAX(prn_seq), 0) AS current_max_prn_seq
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020';
-- Expect: 10

-- 0.2 The 4 remaining accounts must NOT already exist (PK = biz_key + bank_cd + acct_no).
--     Expect 0 rows.
SELECT bank_cd, acct_no, ccy_cd, acct_type_cd, hand_reg_yn, actv_yn, prn_seq
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('NAPAS00ATPB','0421404333','17902455','107206577');

-- 0.3 The bank codes must exist in BANK_INFM_V2, or the BEFORE-INSERT trigger
--     reject_not_existing_bank_cd() raises "The bank code % does not exist".
--     Expect 4 rows (KEBHana / Vietin / BIDV / Vietcombank).
SELECT bank_cd, bank_nm
FROM   bank_infm_v2
WHERE  bank_cd IN ('11704080','11704001','11704008','11704004')
ORDER  BY bank_cd;

-- 0.4 All users of the company. Expected here: the 3 HANSUNGBOLT UAT users
--     (Hansungbolt_UAT@gmail.com / joonyjoo@hanmail.net / ok7308@hansungbolt.com).
--     Check 2.0 is the one that decides whether they need a permission row.
SELECT c.user_id, u.user_nm, u.actv_yn, u.lock_yn, c.del_yn
FROM   user_busi_conn_infm c
JOIN   user_infm u ON u.user_id = c.user_id
WHERE  c.biz_key = 'VN_VNM0004_000020'
AND    c.del_yn  = 'N'
ORDER  BY c.user_id;


-- =====================================================================================
-- SECTION 1A — ALREADY DONE (the 4 Shinhan accounts). VERIFY ONLY — do not re-insert.
-- =====================================================================================

-- 1A.1 Confirm the inserted rows carry the values firm banking depends on.
--      Wanted: CCY_CD VND->ACCT_TYPE_CD 03, USD->04; ACTV_YN 'Y';
--              HAND_REG_YN 'N'; BANK_CERT_SEQ = the Shinhan Login Credential seq.
SELECT prn_seq, bank_cd, acct_no, acct_nm, ccy_cd, acct_type_cd, acct_dv_cd,
       hand_reg_yn, actv_yn, acct_cust_type, bank_cert_seq, reg_id, reg_dtm
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('700006294670','700012464956','700006294689','700006294696')
ORDER  BY prn_seq;

-- 1A.2 The Shinhan Login Credential (Settings -> Login Credentials) for this company.
--      Compare SEQ_NO against BANK_CERT_SEQ above.
SELECT seq_no, bank_cd, cert_info_dv, acct_list_inq_stts, lst_acct_list_inq_dt
FROM   bank_cert_info
WHERE  biz_key = 'VN_VNM0004_000020'
AND    bank_cd = '11704088'
ORDER  BY seq_no;

-- 1A.3 CORRECTIVE UPDATE — run ONLY if 1A.1 showed HAND_REG_YN='Y' or a NULL
--      BANK_CERT_SEQ while 1A.2 returned a credential.
--      Why it matters: trns_0001_005_r001_act.jsp forces HAND_REG_YN='N', so a
--      manually-registered account never appears in the Transfer screen's
--      "My Accounts" tab, and a NULL BANK_CERT_SEQ breaks balance/transaction
--      scraping for the account.
--      If 1A.2 returned NO credential: register the Shinhan credential in the UI
--      first, then run this — do not set HAND_REG_YN='N' with a NULL credential.
/*
BEGIN;

UPDATE acct_mstr m
SET    hand_reg_yn   = 'N',
       bank_cert_seq = (SELECT c.seq_no
                          FROM bank_cert_info c
                         WHERE c.biz_key = m.biz_key
                           AND c.bank_cd = '11704088'
                         ORDER BY c.seq_no
                         LIMIT 1),
       mod_id        = 'Hansungbolt_UAT@gmail.com',                      -- confirm this is the operator to record as MOD_ID
       mod_dtm       = to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')
WHERE  m.biz_key = 'VN_VNM0004_000020'
AND    m.acct_no IN ('700006294670','700012464956','700006294689','700006294696');
-- Expect: UPDATE 4

COMMIT;
*/


-- =====================================================================================
-- SECTION 1B — OPTIONAL: the 4 third-party / beneficiary accounts
-- =====================================================================================
-- ATPB, TRAN THI NGOC MINH, NGUYEN YEN CHAU, LEE YEONG SEOK are NOT HANSUNGBOLT's
-- own accounts. In WeCMS, counterparty accounts belong in Contacts (TRCO_INFM +
-- TRCO_ACCT_INFO), not in ACCT_MSTR — and KEBHana (11704080) and BIDV (11704008)
-- are not in the Transfer screen's allowed-bank whitelist
-- (trns_0001_001_r001_act.jsp), so putting them in ACCT_MSTR will not make them
-- selectable as transfer sources anyway.
--
-- >>> If they are meant to be transfer RECIPIENTS, SKIP this section entirely and
--     register them under Contacts instead. <<<
--
-- Kept because it was explicitly requested. Registered the way the legacy manual-
-- registration screen does it: HAND_REG_YN='Y', no bank credential.
--
-- PRN_SEQ 11..14 continues from the current max of 10 (same rule as wb_get_next_val).
-- REG_DTM/MOD_DTM are UTC yyyyMMddHHmmss (DateTimeUtils.utcNow) — the JEX DB session
-- zone is Asia/Seoul, so timezone('utc', now()) is required.
-- EDIT the 8 'Hansungbolt_UAT@gmail.com' literals (REG_ID / MOD_ID) before running.
-- -------------------------------------------------------------------------------------

BEGIN;

INSERT INTO acct_mstr (
    biz_key, bank_cd, acct_no, acct_type_cd, acct_dv_cd, ccy_cd,
    agmt_amt, corc_bf_bal, bal, real_amt, pyat_amt,
    acct_nick_nm, acct_nm, cont,
    hand_reg_yn, actv_yn, acct_cust_type, remit_out_acct_use_yn,
    bank_cert_seq, prn_seq,
    reg_id, reg_dtm, mod_id, mod_dtm
) VALUES
-- KEBHanaBank-HN / ATPB / VND
('VN_VNM0004_000020', '11704080', 'NAPAS00ATPB', '03', NULL, 'VND',
 0, 0, 0, 0, 0,
 NULL, 'ATPB', NULL,
 'Y', 'Y', 'C', 'N',
 NULL, 11,
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')),

-- VietinBank / TRAN THI NGOC MINH / VND
('VN_VNM0004_000020', '11704001', '0421404333', '03', NULL, 'VND',
 0, 0, 0, 0, 0,
 NULL, 'TRAN THI NGOC MINH', NULL,
 'Y', 'Y', 'C', 'N',
 NULL, 12,
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')),

-- BIDV / NGUYEN YEN CHAU / VND
('VN_VNM0004_000020', '11704008', '17902455', '03', NULL, 'VND',
 0, 0, 0, 0, 0,
 NULL, 'NGUYEN YEN CHAU', NULL,
 'Y', 'Y', 'C', 'N',
 NULL, 13,
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')),

-- Vietcombank / LEE YEONG SEOK / USD
('VN_VNM0004_000020', '11704004', '107206577', '04', NULL, 'USD',
 0, 0, 0, 0, 0,
 NULL, 'LEE YEONG SEOK', NULL,
 'Y', 'Y', 'C', 'N',
 NULL, 14,
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
 'Hansungbolt_UAT@gmail.com', to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'));
-- Expect: INSERT 0 4

COMMIT;


-- =====================================================================================
-- SECTION 2 — INSERT INTO ACCT_AUTH_INFO  (per-user account permission)
-- =====================================================================================
-- "Is it inserted for the Admin only, or for all users?" — BOTH answers are partly
-- right, and the distinction decides how much of this section you need to run.
--
--   ROWS are written for ALL users of the company.
--   ACCESS is granted to ONE user only — the one who ran the registration.
--
-- Verified two ways: by reading the code, and against real data on the JEX DEV DB
-- (BIZ_KEY VN_VNM0004_000001 has 100 rows in USER_BUSI_CONN_INFM; every scraped
--  account carries ~96-100 AUTH_TYPE='V' rows and exactly ONE at USE_YN='Y').
--
-- 1. THE SCRAPE-AND-SAVE PATH — fnsh_0101_001_c001_act.jsp
--    ("온라인 계좌정보 등록처리[전체사용자용]" = online account registration FOR ALL USERS)
--    Yes, it does write ACCT_AUTH_INFO. Its own spec comment, lines 267-273:
--
--        USER_ID : USER_BUSI_CONN_INFM 테이블을 BIZ_KEY로 조회하여 나온 모든 USER_ID.
--        USE_YN  : USER_ID가 현재 로그인한 사용자의 ID와 일치하면 'Y', 그 외에는 'N'.
--
--      -> USER_ID  : EVERY user_id in USER_BUSI_CONN_INFM for the BIZ_KEY. The lookup
--                    is `and biz_key = ?` and nothing else (lines 274-277) — no DEL_YN,
--                    no ACTV_YN, no LOCK_YN. Deleted users get a row too.
--      -> USE_YN   : 'Y' for the logged-in user who performed the registration (in
--                    practice the admin), 'N' for everyone else (lines 290-294).
--      -> AUTH_TYPE: never set here -> AcctAuthInfoDAO.create() default 'V'
--                    (AcctAuthInfoDAO.java:49). This path NEVER creates a 'W' row.
--      -> REG_ID/MOD_ID = logged-in user; REG_DTM = MOD_DTM = utcNow()
--                    (AcctAuthInfoDAO.java:37-43).
--
--    So the USE_YN='N' rows are inert — functionally identical to having no row.
--    Effective access after a scrape = the registering user, plus every admin.
--
-- 2. ADMINS NEVER NEED A ROW. isAdmin() is simply "has a row in BUSI_ADMN_INFM"
--    (UserLoginManager.java:57-59), and admins skip the permission filter entirely
--    (trns_0001_001_r001_act.jsp:165-167). The permission screen's own read,
--    IDO.ACCT_AUTH_INFO_R004.xml, returns USE_YN='Y' for any BUSI_ADMN_INFM member
--    and then drops admins from the list altogether (`AND T.ADMIN_YN != 'Y'`).
--    >>> If your UAT testers log in as admins, you can SKIP this whole section. <<<
--
-- 3. THE PERMISSION UI does not depend on these rows either. ACCT_AUTH_INFO_R004
--    builds its user list from USER_INFM LEFT JOIN USER_BUSI_CONN_INFM on BIZ_KEY,
--    and derives USE_YN from an EXISTS against ACCT_AUTH_INFO. A user with no row
--    simply renders as unticked. So the pre-created 'N' rows buy nothing — which is
--    why granting a specific tester (2B) is enough on its own.
--
-- 4. 'W' ROWS come only from the Bank Account Setting permission screen
--    (comm_0183_002_u001_act.jsp), where an admin ticks Withdrawal Permission.
--    That screen REFUSES 'W' unless the bank is on this whitelist (lines 160-192,
--    else WBE0452):
--        11704004 Vietcom · 11704003 IBK · 11704088 Shinhan · 11704020 Woori · 11704001 Vietin
--    KEBHana (11704080) and BIDV (11704008) are NOT on it — never write 'W' for them.
--
-- 5. WHY THIS MANUAL INSERT IS NEEDED AT ALL: in fnsh_0101_001_c001 the branch for
--    "account already exists in ACCT_MSTR" (lines 373-388) builds the AcctAuthInfo
--    objects but never calls create(). The accounts here were inserted by hand, so
--    re-running the UI registration over them writes NO permission rows.
--
-- Effect of the two AUTH_TYPEs at read time:
--   'V' -> account appears in Account Inquiry / Transaction History / bank filters
--   'W' -> plus selectable on Transfer / payment screens
--          (trns_0001_001_r001_act.jsp:166 accepts (V and USE_YN='Y') OR (W and USE_YN='Y'))
-- -------------------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- 2.0 FIRST: are the 3 users admins? This decides whether you run 2B at all.
--     ADMIN_YN='Y' -> that user already has full access; needs nothing below.
--     ADMIN_YN='N' -> that user needs the 2B grant.
-- ---------------------------------------------------------------------------
SELECT c.user_id,
       u.user_nm,
       CASE WHEN EXISTS (SELECT 1 FROM busi_admn_infm a
                          WHERE a.biz_key = c.biz_key
                            AND a.user_id = c.user_id)
            THEN 'Y' ELSE 'N' END AS admin_yn,
       u.actv_yn,
       u.lock_yn,
       c.del_yn
FROM   user_busi_conn_infm c
JOIN   user_infm u ON u.user_id = c.user_id
WHERE  c.biz_key = 'VN_VNM0004_000020'
AND    c.user_id IN ('Hansungbolt_UAT@gmail.com',
                     'joonyjoo@hanmail.net',
                     'ok7308@hansungbolt.com')
ORDER  BY c.user_id;
--
-- RESULT ON UAT, 2026-08-12 — all 3 users are ADMINS, active, unlocked, not deleted:
--   Hansungbolt_UAT@gmail.com  Kim Do Joon      ADMIN_YN=Y  ACTV=Y  LOCK=N  DEL=N
--   joonyjoo@hanmail.net       Kim Do Joon      ADMIN_YN=Y  ACTV=Y  LOCK=N  DEL=N
--   ok7308@hansungbolt.com     Ok Kyeong Hwan   ADMIN_YN=Y  ACTV=Y  LOCK=N  DEL=N
--
-- Being admins, they already have full access to every account without any
-- ACCT_AUTH_INFO row (UserLoginManager.java:57-59 + trns_0001_001_r001_act.jsp:165-167,
-- and IDO.ACCT_AUTH_INFO_R004 returns USE_YN='Y' for any BUSI_ADMN_INFM member).
-- Section 2.2 is therefore OPTIONAL — the rows are redundant but harmless, and they
-- make the permission state explicit in the table. Proceeding by decision (Sonny).


-- ---------------------------------------------------------------------------
-- 2.1 PRE-CHECK — the 4 accounts must already exist in ACCT_MSTR.
--     ACCT_AUTH_INFO has FK (biz_key, bank_cd, acct_no) -> ACCT_MSTR, so 2.2
--     fails with a foreign-key violation if Section 1B was never run.
--     Expect 4 rows. Any account missing here -> run Section 1B first.
-- ---------------------------------------------------------------------------
SELECT m.bank_cd, m.acct_no, m.acct_nm, m.ccy_cd, m.acct_type_cd, m.prn_seq,
       (SELECT count(*) FROM acct_auth_info a
         WHERE a.biz_key = m.biz_key AND a.bank_cd = m.bank_cd
           AND a.acct_no = m.acct_no)                       AS existing_auth_rows
FROM   acct_mstr m
WHERE  m.biz_key = 'VN_VNM0004_000020'
AND    m.acct_no IN ('NAPAS00ATPB','0421404333','17902455','107206577')
ORDER  BY m.prn_seq;


-- ---------------------------------------------------------------------------
-- 2.2 GRANT — the 4 remaining accounts x the 3 HANSUNGBOLT UAT users.
--     Shinhan is already done, so it is deliberately NOT in this list.
--
--     AUTH_TYPE 'V' only, for three reasons:
--
--       1. NO QUERY IN THE SYSTEM REQUIRES 'W'. Every consumer treats V and W as an
--          OR, never an AND:
--            trns_0001_001_r001_act.jsp:87,166  (Transfer withdrawal-account list)
--            pymn_0007_002_r002_act.jsp:77,123  (Payment, Shinhan-only)
--              -> AND ((USE_YN_V='Y' AND AUTH_TYPE_V='V') OR (USE_YN_W='Y' AND AUTH_TYPE_W='W'))
--            comm_1001_001_act.jsp:51           -> AND AAI.AUTH_TYPE IN ('V','W','F')
--          A 'W' row therefore grants nothing a 'V' row has not already granted.
--          'W' is intent/reporting metadata (it drives the separate "Withdrawal
--          Permission" column in IDO.ACCT_MSTR_R012), not an extra access gate.
--
--       2. THE UI REFUSES 'W' ON 2 OF THESE 4 BANKS. comm_0183_002_u001_act.jsp:160-192
--          whitelists Vietcom 11704004 / IBK 11704003 / Shinhan 11704088 /
--          Woori 11704020 / Vietin 11704001 and throws WBE0452 otherwise.
--          KEBHana (11704080) and BIDV (11704008) are not on it, so writing 'W' for
--          them by hand creates a state the application itself can never produce.
--
--       3. SEMANTICS. 'W' means "may withdraw FROM this account". These four are
--          counterparty/beneficiary accounts — money goes TO them, never FROM them.
--
--     See 2.4 if you want the 'W' rows anyway (possible for Vietin + Vietcombank only).
--
--     USE_YN='Y' so the accounts are usable immediately, matching what an admin
--     ticking View Permission in Bank Account Setting would produce (minus the
--     AUDIT_LOGS_INFM rows that screen also writes).
--
--     ON CONFLICT DO NOTHING makes it safe to re-run; it will not overwrite an
--     existing grant. If a row already sits at USE_YN='N', use 2.3 to flip it.
-- ---------------------------------------------------------------------------

BEGIN;

WITH p AS (
    SELECT 'VN_VNM0004_000020'::varchar(20)  AS biz_key,
           'Hansungbolt_UAT@gmail.com'::varchar(100) AS reg_user   -- operator recorded as REG_ID / MOD_ID
),
tgt_acct (bank_cd, acct_no) AS (
    VALUES ('11704080', 'NAPAS00ATPB'),      -- KEBHanaBank-HN  / ATPB               / VND
           ('11704001', '0421404333'),       -- VietinBank      / TRAN THI NGOC MINH / VND
           ('11704008', '17902455'),         -- BIDV            / NGUYEN YEN CHAU    / VND
           ('11704004', '107206577')         -- Vietcombank     / LEE YEONG SEOK     / USD
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
       'V',                                                        -- AUTH_TYPE
       'Y',                                                        -- USE_YN
       p.reg_user,
       to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),         -- UTC, not NOW()
       p.reg_user,
       to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')
FROM   p
CROSS  JOIN tgt_acct a
CROSS  JOIN tgt_user u
ON CONFLICT (biz_key, bank_cd, acct_no, user_id, auth_type) DO NOTHING;
-- Expect: INSERT 0 12   (4 accounts x 3 users)

COMMIT;


-- 2.3 Run ONLY if 2.1 showed existing_auth_rows > 0 and 3.2 shows any of the 4
--     accounts still at USE_YN='N' for these users — ON CONFLICT DO NOTHING
--     leaves pre-existing rows untouched, so 2.2 would not have switched them on.
--     Keep both IN-lists; they are what stops this touching Shinhan or other users.
/*
BEGIN;

UPDATE acct_auth_info
SET    use_yn  = 'Y',
       mod_id  = 'Hansungbolt_UAT@gmail.com',
       mod_dtm = to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')
WHERE  biz_key   = 'VN_VNM0004_000020'
AND    user_id   IN ('Hansungbolt_UAT@gmail.com',
                     'joonyjoo@hanmail.net',
                     'ok7308@hansungbolt.com')
AND    acct_no   IN ('NAPAS00ATPB','0421404333','17902455','107206577')
AND    auth_type = 'V'
AND    use_yn    = 'N';

COMMIT;
*/


-- 2.4 OPTIONAL — 'W' (Withdrawal Permission) rows.
--     Only if you want the Bank Account Setting screen's "Withdrawal Permission"
--     column populated for these accounts. It grants no access the 'V' rows in 2.2
--     have not already granted (see reason 1 above).
--
--     LIMITED TO VIETINBANK + VIETCOMBANK — the only two of these four banks on the
--     withdraw whitelist. Do NOT add KEBHana (11704080) or BIDV (11704008): the UI
--     rejects 'W' there with WBE0452, so a hand-written row would be unreachable
--     state that no screen could ever have created or can now remove.
--     6 rows = 2 accounts x 3 users.
/*
BEGIN;

WITH p AS (
    SELECT 'VN_VNM0004_000020'::varchar(20)  AS biz_key,
           'Hansungbolt_UAT@gmail.com'::varchar(100) AS reg_user
),
tgt_acct (bank_cd, acct_no) AS (
    VALUES ('11704001', '0421404333'),       -- VietinBank  / TRAN THI NGOC MINH / VND
           ('11704004', '107206577')         -- Vietcombank / LEE YEONG SEOK     / USD
),
tgt_user (user_id) AS (
    VALUES ('Hansungbolt_UAT@gmail.com'),
           ('joonyjoo@hanmail.net'),
           ('ok7308@hansungbolt.com')
)
INSERT INTO acct_auth_info (
    biz_key, bank_cd, acct_no, user_id, auth_type,
    use_yn, reg_id, reg_dtm, mod_id, mod_dtm
)
SELECT p.biz_key, a.bank_cd, a.acct_no, u.user_id,
       'W', 'Y', p.reg_user,
       to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS'),
       p.reg_user,
       to_char(timezone('utc', now()), 'YYYYMMDDHH24MISS')
FROM   p
CROSS  JOIN tgt_acct a
CROSS  JOIN tgt_user u
ON CONFLICT (biz_key, bank_cd, acct_no, user_id, auth_type) DO NOTHING;
-- Expect: INSERT 0 6

COMMIT;
*/


-- =====================================================================================
-- SECTION 3 — POST-RUN VERIFICATION
-- =====================================================================================

-- 3.1 All accounts for the company, in display order.
SELECT prn_seq, bank_cd, acct_no, acct_nm, ccy_cd, acct_type_cd,
       hand_reg_yn, actv_yn, bank_cert_seq, reg_dtm
FROM   acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
ORDER  BY prn_seq;

-- 3.2 Grant counts per account, split by USE_YN. Only the USE_YN='Y' count grants
--     anything — 'N' rows are inert.
--     After 2.2, expect the 4 KEBHana/Vietin/BIDV/Vietcombank accounts to show
--     AUTH_TYPE='V', USE_YN='Y', users=3, and no 'W' rows.
--     The 4 Shinhan accounts show whatever was already set up earlier.
--     Admins may show no rows at all and still have full access.
SELECT a.bank_cd, a.acct_no, a.auth_type, a.use_yn, COUNT(*) AS users
FROM   acct_auth_info a
WHERE  a.biz_key = 'VN_VNM0004_000020'
AND    a.acct_no IN ('700006294670','700012464956','700006294689','700006294696',
                     'NAPAS00ATPB','0421404333','17902455','107206577')
GROUP  BY a.bank_cd, a.acct_no, a.auth_type, a.use_yn
ORDER  BY a.bank_cd, a.acct_no, a.auth_type;

-- 3.3 Simulate the Transfer screen's withdrawal-account list for one tester
--     (trns_0001_001_r001_act.jsp: VND, ACTV_YN='Y', ACCT_TYPE_CD in 03/05,
--      bank in the whitelist, and a V-or-W grant).
--     The 2 Shinhan VND accounts (700006294670, 700012464956) must appear.
--
--     HEADS-UP after running 2.2: the withdrawal list accepts a 'V' grant as well
--     as 'W', and VietinBank (11704001) IS on the allowed-bank whitelist. So
--     0421404333 / TRAN THI NGOC MINH — a beneficiary account — will now also show
--     up here as a selectable withdrawal source. KEBHana and BIDV will not (not
--     whitelisted), and the Vietcombank one is USD so the VND-only screen skips it.
--     If that Vietin entry confuses testers, either drop its 'V' grant or move the
--     account to Contacts (TRCO_INFM / TRCO_ACCT_INFO) where it belongs.
SELECT m.bank_cd, m.acct_no, m.ccy_cd, m.acct_type_cd
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
                  AND a.user_id = 'Hansungbolt_UAT@gmail.com'   -- swap for another tester if you prefer
                  AND a.use_yn  = 'Y'
                  AND a.auth_type IN ('V','W'))
ORDER  BY m.acct_no;

-- 3.4 Simulate the Transfer screen's "My Accounts" tab (trns_0001_005_r001_act.jsp:
--     ACTV_YN='Y', HAND_REG_YN='N', VND). Confirms 1A.3 was applied if it was needed.
SELECT m.bank_cd, m.acct_no, m.ccy_cd, m.hand_reg_yn
FROM   acct_mstr m
WHERE  m.biz_key     = 'VN_VNM0004_000020'
AND    m.actv_yn     = 'Y'
AND    m.hand_reg_yn = 'N'
AND    m.ccy_cd      = 'VND'
ORDER  BY m.acct_no;


-- =====================================================================================
-- SECTION 4 — ROLLBACK (only if the data must be removed again)
-- =====================================================================================
-- Run children first, then ACCT_MSTR (FK acct_auth_info -> acct_mstr).
-- Deleting from ACCT_MSTR also fires acct_mstr_log(), which cleans the matching
-- DAILY_CASH_REPT_DAILY_SUMR_DETL rows for today.
-- The acct_no lists below are scoped to the 1B beneficiary accounts only —
-- add the 4 Shinhan account numbers if those must go too.
/*
BEGIN;

DELETE FROM acct_auth_info
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('NAPAS00ATPB','0421404333','17902455','107206577');

DELETE FROM acct_mstr
WHERE  biz_key = 'VN_VNM0004_000020'
AND    acct_no IN ('NAPAS00ATPB','0421404333','17902455','107206577');

COMMIT;
*/

-- WeCMS Manual Database Change
-- Date: 2026-11-13
-- Related record: docs/features/addon-service/weinvoice/weinvoice-self-signup-2026-11-13.md
-- Type: DML (INSERT into a new table; the WeCMS source row is only READ)
-- Execution: MANUAL — two databases, two steps (see below)
-- Target environments: the environment where the user signs in to WeInvoice (developer request: lamine@gmail.com)
-- Executor: DBA OR DEV TEAM — environment dependent
-- Transaction requirement: step 2 is a single INSERT (atomic on its own)
-- Rollback: DELETE FROM weinvoice.winv_user_infm WHERE user_id = '<id>' AND reg_src = 'WECMS';
--           (login then falls back to the unchanged WeCMS row). The WeCMS row is never modified.
--
-- Prerequisite: src/main/resources/db/weinvoice/V4__winv_user.sql applied on the WeInvoice (engine) database.
--
-- WHY TWO STEPS: USER_INFM lives in the WeCMS database (WECMS_DB_URL) and winv_user_infm in the WeInvoice
-- database (WECMS_WEINVOICE_DB_URL). Step 1 READS the WeCMS row and PRINTS the INSERT for step 2 — copy that
-- one text cell and run it on the WeInvoice database. The password hash is copied as-is, so the same password
-- keeps working (the backend verifies the legacy SHA-256 hash). Do not paste the generated text anywhere else:
-- it contains the password hash.
--
-- The user id (owner of the user's winv_* data) is kept exactly as in USER_INFM, so the companies, tags and
-- mailboxes the user already set up stay theirs.

-- ===========================================================================
-- STEP 1 — run on the **WeCMS** database
-- ===========================================================================

-- 1a. PRE-CHECK: the source row (expect exactly 1 row, del_yn = 'N').
SELECT u.user_id, u.user_nm, u.eml, u.actv_yn, u.lock_yn, u.del_yn
  FROM user_infm u
 WHERE lower(u.user_id) = lower('lamine@gmail.com');

-- 1b. Generate the INSERT for step 2 (one row, one column). Company name = the user's first active company.
SELECT format(
         'INSERT INTO weinvoice.winv_user_infm (user_id, user_nm, eml, clph_no, cmpn_nm, user_pw, lang_cd, actv_yn, lock_yn, del_yn, fail_cnt, reg_src, reg_dtm, mod_dtm) '
         || 'VALUES (%L, %L, %L, %L, %L, %L, NULL, %L, %L, %L, 0, ''WECMS'', %L, %L) ON CONFLICT DO NOTHING;',
         u.user_id, u.user_nm, coalesce(nullif(u.eml, ''), u.user_id), left(u.clph_no, 30),
         (SELECT b.cmpn_nm
            FROM user_busi_conn_infm c JOIN busi_infm b ON b.biz_key = c.biz_key
           WHERE c.user_id = u.user_id AND c.del_yn = 'N'
           ORDER BY c.biz_key LIMIT 1),
         u.user_pw, u.actv_yn, u.lock_yn, u.del_yn,
         to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS'),
         to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS')) AS insert_for_step_2
  FROM user_infm u
 WHERE lower(u.user_id) = lower('lamine@gmail.com')
   AND u.del_yn = 'N';

-- ===========================================================================
-- STEP 2 — run on the **WeInvoice (engine)** database
-- ===========================================================================

-- 2a. PRE-CHECK: expect 0 rows (not moved yet).
SELECT user_id, reg_src FROM weinvoice.winv_user_infm WHERE lower(user_id) = lower('lamine@gmail.com');

-- 2b. CHANGE: paste and run the text produced by 1b here.

-- 2c. POST-CHECK: expect 1 row with reg_src = 'WECMS'.
SELECT user_id, user_nm, eml, cmpn_nm, actv_yn, lock_yn, del_yn, reg_src, reg_dtm
  FROM weinvoice.winv_user_infm
 WHERE lower(user_id) = lower('lamine@gmail.com');

-- ===========================================================================
-- 4. ROLLBACK / RECOVERY NOTES
-- ===========================================================================
-- Run on the WeInvoice database:
--   DELETE FROM weinvoice.winv_user_infm WHERE lower(user_id) = lower('lamine@gmail.com') AND reg_src = 'WECMS';
-- Login then authenticates against the WeCMS row again (unchanged). No WeInvoice data (owner_id) is affected.

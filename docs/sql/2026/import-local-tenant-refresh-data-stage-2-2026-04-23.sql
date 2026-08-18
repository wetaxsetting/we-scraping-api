-- WeCMS Manual Database Change
-- Date: 2026-04-23
-- Source: migrated from Claude Code (AI)/sql/refresh_local_VN_VNM0004_2_apply_to_local_20260423.sql
-- Related record: TBD
-- Type: DDL+DML
-- Execution: MANUAL
-- Target environments: Shared WeCMS dev DB (172.168.5.172:5432/wecmsdevdb)
-- Executor: DEV TEAM
-- Rollback: Original script backs the current rows up into _bkp_<ts> tables and wraps everything in one transaction
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- Stage 2 / 2 — Refresh local DB with WeCMS-scope data exported from DEV
-- Target:    172.168.5.172:5432/wecmsdevdb (shared WeCMS dev DB server)
-- Scope:     BIZ_KEY LIKE 'VN_VNM0004_%' + all user_ids connected to those biz_keys
-- Baseline:  VN_VNM0004_000001
-- Generated: 2026-04-23
--
-- WHAT THIS SCRIPT DOES
--   1. Backs up the current WeCMS-scope rows into _bkp_<ts> tables (in place, same DB)
--   2. Deletes the current WeCMS-scope rows in the correct FK-respecting order
--   3. Re-imports the 6 CSVs produced by Stage 1
--   4. Verifies row counts
--   5. All wrapped in a single transaction — ROLLBACK by closing psql if verify fails
--
-- PRE-FLIGHT
--   - Stage 1 must have produced 6 CSVs; set :import_dir to their directory.
--   - Local DB must already contain the lookup rows referenced by busi_infm FKs:
--       * natn_infm.ntnl_cd = 'VN'
--       * chnl_infm.chnl_id = 'VNM0004'
--     If those are missing, the INSERT into busi_infm will fail — resolve separately.
--   - Adjust :local_schema if the local DB uses a schema other than 'public'.
--
-- HOW TO RUN
--   psql "host=172.168.5.172 port=5432 dbname=wecmsdevdb user=wecmsdev_owner" \
--        -v import_dir='C:/WECMS PROJECT/Claude Code (AI)/sql/_export_VN_VNM0004_20260423' \
--        -v local_schema=public \
--        -f refresh_local_VN_VNM0004_2_apply_to_local_20260423.sql
--
-- SAFETY
--   - Touches ONLY rows where BIZ_KEY starts with 'VN_VNM0004_' (plus related
--     user_infm / user_login_his rows joined via user_id from that scope).
--   - Non-WeCMS tenant data is untouched.
--   - Backup tables are kept — drop them manually once you're satisfied.
-- =============================================================================

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';

-- Switch to local schema (edit :local_schema at invocation if different)
SELECT set_config('search_path', :'local_schema' || ', public', false);

BEGIN;

-- ---------------------------------------------------------------------------
-- Step 0 — Pre-flight: confirm FK lookup rows exist
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM natn_infm WHERE ntnl_cd = 'VN') THEN
    RAISE EXCEPTION 'natn_infm.ntnl_cd = ''VN'' is missing — seed natn_infm first';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM chnl_infm WHERE chnl_id = 'VNM0004') THEN
    RAISE EXCEPTION 'chnl_infm.chnl_id = ''VNM0004'' is missing — seed chnl_infm first';
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Step 1 — Backup current WeCMS-scope rows into _bkp_<ts> tables
--          (same DB, same schema, so no cross-DB move needed)
-- ---------------------------------------------------------------------------
\set ts `date +%Y%m%d_%H%M%S`
\echo ''
\echo '==> Backing up current WeCMS-scope rows to *_bkp_:ts tables'

CREATE TABLE busi_infm_bkp_:ts AS
  SELECT * FROM busi_infm WHERE biz_key LIKE 'VN_VNM0004_%';

CREATE TABLE empl_infm_bkp_:ts AS
  SELECT * FROM empl_infm WHERE biz_key LIKE 'VN_VNM0004_%';

CREATE TABLE busi_admn_infm_bkp_:ts AS
  SELECT * FROM busi_admn_infm WHERE biz_key LIKE 'VN_VNM0004_%';

CREATE TABLE user_busi_conn_infm_bkp_:ts AS
  SELECT * FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%';

-- Capture the user_id set BEFORE we delete any of the scope tables.
CREATE TEMP TABLE _wecms_user_ids ON COMMIT PRESERVE ROWS AS
  SELECT DISTINCT user_id FROM (
    SELECT user_id FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%'
    UNION
    SELECT user_id FROM busi_admn_infm      WHERE biz_key LIKE 'VN_VNM0004_%'
  ) x;

CREATE TABLE user_infm_bkp_:ts AS
  SELECT * FROM user_infm
  WHERE user_id IN (SELECT user_id FROM _wecms_user_ids);

CREATE TABLE user_login_his_bkp_:ts AS
  SELECT * FROM user_login_his
  WHERE user_id IN (SELECT user_id FROM _wecms_user_ids);

\echo '    backup tables created:'
SELECT 'busi_infm_bkp_:ts'           AS bkp, COUNT(*) FROM busi_infm_bkp_:ts
UNION ALL SELECT 'empl_infm_bkp_:ts',           COUNT(*) FROM empl_infm_bkp_:ts
UNION ALL SELECT 'busi_admn_infm_bkp_:ts',      COUNT(*) FROM busi_admn_infm_bkp_:ts
UNION ALL SELECT 'user_busi_conn_infm_bkp_:ts', COUNT(*) FROM user_busi_conn_infm_bkp_:ts
UNION ALL SELECT 'user_infm_bkp_:ts',           COUNT(*) FROM user_infm_bkp_:ts
UNION ALL SELECT 'user_login_his_bkp_:ts',      COUNT(*) FROM user_login_his_bkp_:ts;

-- ---------------------------------------------------------------------------
-- Step 2 — Delete current WeCMS-scope rows in FK-respecting order
--          (children first, parents last)
-- ---------------------------------------------------------------------------
\echo ''
\echo '==> Deleting WeCMS-scope rows'

DELETE FROM user_login_his
 WHERE user_id IN (SELECT user_id FROM _wecms_user_ids);

DELETE FROM busi_admn_infm      WHERE biz_key LIKE 'VN_VNM0004_%';
DELETE FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%';
DELETE FROM empl_infm           WHERE biz_key LIKE 'VN_VNM0004_%';

-- user_infm must be deleted after conn + admn (they FK to user_id)
DELETE FROM user_infm
 WHERE user_id IN (SELECT user_id FROM _wecms_user_ids);

DELETE FROM busi_infm WHERE biz_key LIKE 'VN_VNM0004_%';

-- ---------------------------------------------------------------------------
-- Step 3 — Import the 6 CSVs in parent-first order
--          (CSVs are authoritative; column order matches table definition)
-- ---------------------------------------------------------------------------
\echo ''
\echo '==> Importing CSVs from :import_dir'

\copy busi_infm           FROM :'import_dir'/busi_infm.csv           WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy user_infm           FROM :'import_dir'/user_infm.csv           WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy empl_infm           FROM :'import_dir'/empl_infm.csv           WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy user_busi_conn_infm FROM :'import_dir'/user_busi_conn_infm.csv WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy busi_admn_infm      FROM :'import_dir'/busi_admn_infm.csv      WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy user_login_his      FROM :'import_dir'/user_login_his.csv      WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- ---------------------------------------------------------------------------
-- Step 4 — Verify — compare local counts against the Stage-1 DEV counts
--          Expected (from DEV inspection, 2026-04-23):
--            busi_infm            : 14
--            empl_infm            : 14
--            busi_admn_infm       : 20
--            user_busi_conn_infm  : 122
--            user_infm (scope)    : 122
--            user_login_his       : 13827
-- ---------------------------------------------------------------------------
\echo ''
\echo '==> Verifying row counts (expect: 14 / 14 / 20 / 122 / 122 / 13827)'

SELECT 'busi_infm'           AS tbl, COUNT(*)::bigint AS rows, 14::bigint    AS expected FROM busi_infm           WHERE biz_key LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'empl_infm',           COUNT(*), 14           FROM empl_infm           WHERE biz_key LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'busi_admn_infm',      COUNT(*), 20           FROM busi_admn_infm      WHERE biz_key LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'user_busi_conn_infm', COUNT(*), 122          FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'user_infm (scope)',   COUNT(DISTINCT u.user_id), 122
          FROM user_infm u
          WHERE u.user_id IN (SELECT user_id FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%')
             OR u.user_id IN (SELECT user_id FROM busi_admn_infm      WHERE biz_key LIKE 'VN_VNM0004_%')
UNION ALL SELECT 'user_login_his',      COUNT(*), 13827
          FROM user_login_his h
          WHERE h.user_id IN (SELECT user_id FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%')
ORDER BY 1;

-- Non-WeCMS sanity check — must be zero changes to other tenants
\echo ''
\echo '==> Non-WeCMS rows (should match your pre-refresh counts exactly)'
SELECT 'busi_infm NON-WeCMS'           AS tbl, COUNT(*) FROM busi_infm           WHERE biz_key NOT LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'empl_infm NON-WeCMS',           COUNT(*) FROM empl_infm           WHERE biz_key NOT LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'user_busi_conn_infm NON-WeCMS', COUNT(*) FROM user_busi_conn_infm WHERE biz_key NOT LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'busi_admn_infm NON-WeCMS',      COUNT(*) FROM busi_admn_infm      WHERE biz_key NOT LIKE 'VN_VNM0004_%'
ORDER BY 1;

-- ---------------------------------------------------------------------------
-- Step 5 — Commit (review the output above FIRST)
--          If counts don't match, press Ctrl+C before the COMMIT line runs, or
--          comment out the COMMIT and run ROLLBACK manually.
-- ---------------------------------------------------------------------------
\echo ''
\echo '==> About to COMMIT. Ctrl+C now to ROLLBACK if counts look wrong.'
\prompt 'Press ENTER to COMMIT, Ctrl+C to abort: ' confirm

COMMIT;

\echo ''
\echo '==> Refresh complete. Backup tables (_bkp_:ts) retained — drop manually after validation:'
\echo '       DROP TABLE busi_infm_bkp_:ts, empl_infm_bkp_:ts, busi_admn_infm_bkp_:ts,'
\echo '                  user_busi_conn_infm_bkp_:ts, user_infm_bkp_:ts, user_login_his_bkp_:ts;'

-- WeCMS Manual Database Change
-- Date: 2026-04-23
-- Source: migrated from Claude Code (AI)/sql/refresh_local_VN_VNM0004_1_export_from_dev_20260423.sql
-- Related record: TBD
-- Type: QUERY (read-only export — stage 1 of a two-stage procedure)
-- Execution: MANUAL
-- Target environments: JEX DEV database — READ-ONLY use
-- Executor: DEV TEAM
-- Rollback: Not applicable — the script performs no write
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- Stage 1 / 2 — Export WeCMS-scope data from JEX DEV DB
-- Target:    JEX DEV database (wecmsvndev schema) — READ-ONLY use
-- Scope:     BIZ_KEY LIKE 'VN_VNM0004_%' (WeCMS-Vietnam tenant only)
-- Baseline:  VN_VNM0004_000001 is the root company for local development
-- Output:    6 CSV files under the directory passed in :export_dir
-- Generated: 2026-04-23
--
-- HOW TO RUN
--   psql "host=10.254.242.254 port=54311 dbname=wecmsvndevdb user=<readonly> sslmode=prefer" \
--        -v export_dir='C:/WECMS PROJECT/Claude Code (AI)/sql/_export_VN_VNM0004_20260423' \
--        -f refresh_local_VN_VNM0004_1_export_from_dev_20260423.sql
--
--   The export directory must exist and be writable BEFORE running.
--
-- SAFETY
--   - No INSERT / UPDATE / DELETE / DDL. SELECT-only via \copy TO.
--   - No PROD. DEV host only.
-- =============================================================================

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
SET search_path = wecmsvndev, public;

\echo ''
\echo '==> Counts in DEV for VN_VNM0004_% (sanity check before export)'
SELECT 'busi_infm'           AS tbl, COUNT(*) FROM busi_infm           WHERE biz_key LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'empl_infm',           COUNT(*) FROM empl_infm           WHERE biz_key LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'busi_admn_infm',      COUNT(*) FROM busi_admn_infm      WHERE biz_key LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'user_busi_conn_infm', COUNT(*) FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%'
UNION ALL SELECT 'user_infm',           COUNT(DISTINCT u.user_id) FROM user_infm u
          WHERE u.user_id IN (SELECT user_id FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%')
             OR u.user_id IN (SELECT user_id FROM busi_admn_infm      WHERE biz_key LIKE 'VN_VNM0004_%')
UNION ALL SELECT 'user_login_his',      COUNT(*) FROM user_login_his h
          WHERE h.user_id IN (SELECT user_id FROM user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%')
ORDER BY 1;

\echo ''
\echo '==> Exporting 6 CSV files to :export_dir'
\echo ''

-- 1) busi_infm — parent root
\echo '  [1/6] busi_infm'
\copy (SELECT * FROM wecmsvndev.busi_infm WHERE biz_key LIKE 'VN_VNM0004_%' ORDER BY biz_key) TO :'export_dir'/busi_infm.csv WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- 2) user_infm — all users that are connected to any VN_VNM0004_% company
--    (user_infm.biz_key itself is NULL in DEV; tenant link lives in user_busi_conn_infm + busi_admn_infm)
\echo '  [2/6] user_infm'
\copy (SELECT * FROM wecmsvndev.user_infm WHERE user_id IN ( SELECT user_id FROM wecmsvndev.user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%' UNION SELECT user_id FROM wecmsvndev.busi_admn_infm WHERE biz_key LIKE 'VN_VNM0004_%' ) ORDER BY user_id) TO :'export_dir'/user_infm.csv WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- 3) empl_infm — child of busi_infm
\echo '  [3/6] empl_infm'
\copy (SELECT * FROM wecmsvndev.empl_infm WHERE biz_key LIKE 'VN_VNM0004_%' ORDER BY biz_key, empl_no) TO :'export_dir'/empl_infm.csv WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- 4) user_busi_conn_infm — child of busi_infm + user_infm
\echo '  [4/6] user_busi_conn_infm'
\copy (SELECT * FROM wecmsvndev.user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%' ORDER BY biz_key, user_id) TO :'export_dir'/user_busi_conn_infm.csv WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- 5) busi_admn_infm — child of busi_infm + user_infm
\echo '  [5/6] busi_admn_infm'
\copy (SELECT * FROM wecmsvndev.busi_admn_infm WHERE biz_key LIKE 'VN_VNM0004_%' ORDER BY biz_key, user_id) TO :'export_dir'/busi_admn_infm.csv WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- 6) user_login_his — child of user_infm (no biz_key column; filter by user_id)
\echo '  [6/6] user_login_his'
\copy (SELECT * FROM wecmsvndev.user_login_his WHERE user_id IN ( SELECT user_id FROM wecmsvndev.user_busi_conn_infm WHERE biz_key LIKE 'VN_VNM0004_%' ) ORDER BY user_id, reg_dtm) TO :'export_dir'/user_login_his.csv WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

\echo ''
\echo '==> Export complete. Move these 6 CSVs to the local dev machine,'
\echo '    then run refresh_local_VN_VNM0004_2_apply_to_local_20260423.sql against localhost:5434/wecms_dev.'

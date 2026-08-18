-- WeCMS Manual Database Change
-- Date: 2026-07-30
-- Source: migrated from Claude Code (AI)/sql/tb_firm_bat_api_tran_idempotency_index_20260730.sql
-- Related record: docs/deployment/wecms-batch-activation-runbook.md
-- Type: DDL
-- Execution: MANUAL
-- Target environments: TBD — original script does not name the environments
-- Executor: DBA TEAM
-- Rollback: DROP INDEX reverses the change
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- tb_firm_bat_api_tran_idempotency_index_20260730.sql
-- Covering index for the date-proof 00302 idempotency guard (FIX D).
-- =============================================================================
--
-- WHY
--   BulkExecutionInlineTxService.issueOne probes TB_FIRM_BAT_API_TRAN before every
--   00302 to answer "has this file's execution instruction already been sent?".
--   As of FIX D (2026-07-30) that probe is
--
--       TbFirmBatApiTranRepository.existsByBizKeyAndFirmFileNmAndApiTranTp
--         -> WHERE biz_key = ? AND firm_file_nm = ? AND api_tran_tp = ?
--
--   with NO tran_dt predicate, because the audit row's TRAN_DT is the callback
--   clock while the file row's TRAN_DT is the build clock — two invocations
--   straddling ICT midnight probed different keyspaces and re-sent 00302 to the
--   bank. (FIRM_FILE_NM embeds its own TRAN_DT, so dropping the column cannot
--   alias across dates. Full argument in the repository method's Javadoc.)
--
--   TB_FIRM_BAT_API_TRAN has exactly ONE index, on both DBs:
--       pk_tb_firm_bat_api_tran
--         (tran_dt, biz_key, tran_seq, api_tran_tp, out_bank_cd, bank_prtn_id)
--   It LEADS WITH tran_dt and is unusable for the new predicate; firm_file_nm is
--   not indexed at all. The probe is therefore a sequential scan today.
--
-- VERIFIED READ-ONLY 2026-07-30
--   JEX DEV      (10.254.242.254:54311/wecmsvndevdb): 1,304 rows / 39 tran_dt / 360 kB
--   refactor DEV (172.168.5.172:5432/wecmsdevdb):         0 rows /  0 tran_dt /  16 kB
--   Both: pg_indexes returns pk_tb_firm_bat_api_tran and nothing else.
--
-- URGENCY — LOW, AND DELIBERATELY NOT BUNDLED WITH THE CODE FIX
--   At 360 kB and once-per-bulk-file, the scan is not a live problem; the code fix
--   is correct without this index and must not wait for it. This script exists so
--   the growth path is closed before the table is large, and so a future second
--   caller on a hot path has an index to rely on. Apply it at the next
--   maintenance window, not as a hotfix.
--
-- EXECUTION MODEL  (IMPORTANT)
--   * RUN BY A DBA, BY HAND. NOT a Flyway migration — Flyway is disabled in
--     wecms-api and ddl-auto = validate in every profile
--     (project_flyway_disabled_ddl_validate), so the schema is external.
--   * This is the ONLY DDL in this script: one additive CREATE INDEX. It creates
--     no table, alters no column, drops nothing. Adding an index beyond the legacy
--     set is explicitly permitted by the Legacy Schema Parity Rule ("extra
--     performance indexes are allowed even when DEV has none") and changes no
--     query result — only the plan.
--   * Idempotent: IF NOT EXISTS. Safe to re-run.
--   * CONCURRENTLY, so it takes no ACCESS EXCLUSIVE lock and cannot block the
--     dispatch path. CONCURRENTLY CANNOT RUN INSIDE A TRANSACTION BLOCK — run the
--     statement on its own, with autocommit on. Do not wrap it in BEGIN/COMMIT.
--   * SHARED-TABLE NOTE (UAT/PROD): the refactor points at the live JEX DB
--     (project_uat_prod_live_jex_db_target), so this index lands on a table the
--     live JEX firm-banking batch also writes. An index is behaviour-neutral for
--     legacy — it adds only per-INSERT maintenance cost on a low-volume table —
--     but it IS a change to a shared object, so it needs the same DBA sign-off as
--     any other UAT/PROD DDL.
--   * RUN ORDER: none. This script has no dependencies and nothing depends on it.
--     It may be applied before or after the FIX D code deploy, in any environment,
--     independently.
--
-- ROLLBACK
--   DROP INDEX CONCURRENTLY IF EXISTS ix_tb_firm_bat_api_tran_biz_file_tp;
--   Dropping it restores the sequential scan. Nothing breaks.
-- =============================================================================


-- #############################################################################
-- SECTION 0 — PRE-CHECKS.  SELECT-ONLY.  RUN FIRST, READ, THEN DECIDE.
-- #############################################################################

-- 0.1  Current indexes. Expected: only pk_tb_firm_bat_api_tran.
--      If ix_tb_firm_bat_api_tran_biz_file_tp is already listed, STOP — done.
SELECT indexname, indexdef
  FROM pg_indexes
 WHERE tablename = 'tb_firm_bat_api_tran'
 ORDER BY indexname;

-- 0.2  Size the table, so the decision is made on data and not on habit.
SELECT count(*)                                                   AS rows_total,
       count(DISTINCT tran_dt)                                    AS distinct_tran_dt,
       count(DISTINCT firm_file_nm)                               AS distinct_file_nm,
       pg_size_pretty(pg_total_relation_size('tb_firm_bat_api_tran')) AS total_size
  FROM tb_firm_bat_api_tran;

-- 0.3  Selectivity of the new key. rows_per_key should be ~1-2 (one row per API
--      leg per file). A large value means FIRM_FILE_NM is far less unique in this
--      environment than on DEV — report it before proceeding, because the guard's
--      breadth assumption is stated in the repository Javadoc and would need
--      revisiting.
SELECT round(count(*)::numeric
             / greatest(count(DISTINCT (biz_key, firm_file_nm, api_tran_tp)), 1), 2)
         AS rows_per_key
  FROM tb_firm_bat_api_tran;

-- 0.4  The plan the guard gets TODAY (no ANALYZE — this does not execute).
--      Expect "Seq Scan on tb_firm_bat_api_tran". Substitute a real triple.
EXPLAIN SELECT 1
  FROM tb_firm_bat_api_tran
 WHERE biz_key      = 'VN_VNM0004_000001'
   AND firm_file_nm = 'S_GV00000001_SHB_001_20260730'
   AND api_tran_tp  = '00302';


-- #############################################################################
-- SECTION 1 — THE INDEX.  RUN OUTSIDE A TRANSACTION BLOCK (autocommit ON).
-- #############################################################################
--
-- Column order = the predicate's own order of selectivity:
--   biz_key       tenant scope, always supplied
--   firm_file_nm  the discriminating column
--   api_tran_tp   the leg (00301 / 00302 / 01301 / 01302)
-- All three are equality predicates, so any order works for the lookup; this one
-- also serves the plain (biz_key, firm_file_nm) prefix used by the reconciliation
-- queries in docs/deployment/bulk-file-reconciliation-procedure.md §3.2.
--
-- Not UNIQUE. The refactor writes at most one 00302 per file, but legacy JEX rows
-- in this shared table are not guaranteed to honour that (DEV already holds a
-- FIRM_FILE_NM duplicated across two FILE_SEQ_NOs), and a unique index would turn
-- a historical data quirk into an INSERT failure on the money path.

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_tb_firm_bat_api_tran_biz_file_tp
    ON tb_firm_bat_api_tran (biz_key, firm_file_nm, api_tran_tp);


-- #############################################################################
-- SECTION 2 — POST-CREATE VERIFICATION.  SELECT-ONLY.
-- #############################################################################

-- 2.1  The index exists and is VALID. indisvalid = false means CONCURRENTLY
--      failed part-way; DROP INDEX CONCURRENTLY and re-run section 1.
SELECT c.relname AS index_name, i.indisvalid, i.indisready,
       pg_size_pretty(pg_relation_size(c.oid)) AS index_size
  FROM pg_class c
  JOIN pg_index i ON i.indexrelid = c.oid
 WHERE c.relname = 'ix_tb_firm_bat_api_tran_biz_file_tp';

-- 2.2  The guard's plan now. Expect an Index Scan / Bitmap Index Scan using
--      ix_tb_firm_bat_api_tran_biz_file_tp.
--      NOTE: on a table this small the planner may STILL choose a Seq Scan and be
--      right to. That is not a failure — re-check on a grown table.
EXPLAIN SELECT 1
  FROM tb_firm_bat_api_tran
 WHERE biz_key      = 'VN_VNM0004_000001'
   AND firm_file_nm = 'S_GV00000001_SHB_001_20260730'
   AND api_tran_tp  = '00302';

-- 2.3  The PK is untouched — parity with the legacy index set is preserved and
--      this script only ADDED. Expect pk_tb_firm_bat_api_tran plus the new index.
SELECT indexname, indexdef
  FROM pg_indexes
 WHERE tablename = 'tb_firm_bat_api_tran'
 ORDER BY indexname;
-- =============================================================================

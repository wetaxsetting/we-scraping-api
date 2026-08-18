-- WeCMS Manual Database Change
-- Date: 2026-07-30
-- Source: migrated from Claude Code (AI)/sql/tb_firm_file_mst_stranded_sweep_index_20260730.sql
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
-- tb_firm_file_mst_stranded_sweep_index_20260730.sql
-- Covering index for the date-independent stranded-firm-file sweep (FIX H).
-- =============================================================================
--
-- WHY
--   StuckPayFileMonitorService's firm-file sweep runs every 5 minutes and is the
--   ONLY detection for BULK_EXECUTION_NOT_ISSUED / BULK_EXECUTION_UNCONFIRMED —
--   two states with no automatic retry that resolve only by human action
--   (docs/deployment/bulk-file-reconciliation-procedure.md).
--
--   As of FIX H (2026-07-30) its query is
--
--       TbFirmFileMstRepository.findStrandedBulkExecutionFiles
--         -> WHERE proc_step = ? AND proc_stat = ?
--              AND proc_dttm IS NOT NULL
--              AND proc_dttm <  ?        -- age threshold (stuck for >= 15 min)
--              AND proc_dttm >= ?        -- retention floor (default now - 7 days)
--            ORDER BY proc_dttm ASC, tran_dt, biz_key, file_seq_no, firm_file_nm
--
--   The previous version carried `tran_dt = <today ICT>` and used the PK's leading
--   column for a range scan. It also meant a file stranded at 23:59 vanished from
--   the sweep at 00:00 and was never reported again — permanently invisible in a
--   state nothing re-drives. Legacy tolerated the same day-scope only because
--   BATCH2 re-sent 00302 every 60 seconds; that re-drive was removed (K9 Option A,
--   declined), so the scope had to go.
--
--   TB_FIRM_FILE_MST has exactly ONE index, on both DBs:
--       pk_tb_firm_file_mst (tran_dt, biz_key, file_seq_no, firm_file_nm)
--   No column of the new predicate is indexed, so the sweep is a sequential scan.
--
-- VERIFIED READ-ONLY 2026-07-30
--   JEX DEV      (10.254.242.254:54311/wecmsvndevdb): 122 rows / 88 kB,
--                6 rows at proc_step='1' AND proc_stat='6',
--                tran_dt range 20250113..20260717
--   refactor DEV (172.168.5.172:5432/wecmsdevdb):       0 rows / 8 kB
--   Both: pg_indexes returns pk_tb_firm_file_mst and nothing else.
--
-- URGENCY — LOW, AND DELIBERATELY NOT BUNDLED WITH THE CODE FIX
--   122 rows scanned every 5 minutes is free, and the sweep is off entirely unless
--   wecms.dispatch.stuck-monitor.enabled=true. The correctness fix (no midnight
--   blind spot) must not wait on a DDL window. Apply this before the table reaches
--   the low thousands of rows. AT CURRENT SIZE THE PLANNER WILL STILL CHOOSE A SEQ
--   SCAN AFTER THIS INDEX EXISTS, AND IT IS RIGHT TO — that is not a failed apply.
--
-- EXECUTION MODEL  (IMPORTANT)
--   * RUN BY A DBA, BY HAND. NOT a Flyway migration — Flyway is disabled in
--     wecms-api and ddl-auto = validate in every profile
--     (project_flyway_disabled_ddl_validate), so the schema is external.
--   * This is the ONLY DDL here: one additive CREATE INDEX. No table, column or
--     constraint is created, altered or dropped. Adding an index beyond the legacy
--     set is explicitly permitted by the Legacy Schema Parity Rule ("extra
--     performance indexes are allowed even when DEV has none") and changes no query
--     result — only the plan.
--   * Idempotent: IF NOT EXISTS. Safe to re-run.
--   * CONCURRENTLY, so it takes no ACCESS EXCLUSIVE lock and cannot block a
--     dispatch. CONCURRENTLY CANNOT RUN INSIDE A TRANSACTION BLOCK — run the
--     statement on its own with autocommit ON. Do not wrap it in BEGIN/COMMIT.
--   * SHARED-TABLE NOTE (UAT/PROD): the refactor points at the live JEX DB
--     (project_uat_prod_live_jex_db_target), so this index lands on a table the live
--     JEX firm-banking batch also writes. An index is behaviour-neutral for legacy —
--     it adds only per-INSERT maintenance on a low-volume table — but it IS a change
--     to a shared object and needs the same DBA sign-off as any other UAT/PROD DDL.
--   * RUN ORDER: none. No dependencies, nothing depends on it. It may be applied
--     before or after the FIX H code deploy, in any environment, independently. It
--     is also independent of
--     tb_firm_bat_api_tran_idempotency_index_20260730.sql (FIX D) — the two touch
--     different tables and may be applied in either order or separately.
--
-- ROLLBACK
--   DROP INDEX CONCURRENTLY IF EXISTS ix_tb_firm_file_mst_step_stat_procdttm;
--   Restores the sequential scan. Nothing breaks.
-- =============================================================================


-- #############################################################################
-- SECTION 0 — PRE-CHECKS.  SELECT-ONLY.  RUN FIRST, READ, THEN DECIDE.
-- #############################################################################

-- 0.1  Current indexes. Expected: only pk_tb_firm_file_mst.
SELECT indexname, indexdef
  FROM pg_indexes
 WHERE tablename = 'tb_firm_file_mst'
 ORDER BY indexname;

-- 0.2  Size, and how much of the table the sweep's state predicate actually keeps.
--      A high stranded_1_6 count is itself worth reporting — it means files are
--      accumulating in a state that only a human resolves.
SELECT count(*)                                                       AS rows_total,
       count(*) FILTER (WHERE proc_step = '1' AND proc_stat = '6')     AS stranded_1_6,
       count(*) FILTER (WHERE proc_dttm IS NULL)                       AS null_proc_dttm,
       min(tran_dt)                                                    AS min_tran_dt,
       max(tran_dt)                                                    AS max_tran_dt,
       pg_size_pretty(pg_total_relation_size('tb_firm_file_mst'))       AS total_size
  FROM tb_firm_file_mst;

-- 0.3  The plan the sweep gets TODAY (no ANALYZE — this does not execute).
--      Expect "Seq Scan on tb_firm_file_mst". Substitute real 14-char bounds.
EXPLAIN SELECT *
  FROM tb_firm_file_mst
 WHERE proc_step = '1'
   AND proc_stat = '6'
   AND proc_dttm IS NOT NULL
   AND proc_dttm <  '20260730120000'
   AND proc_dttm >= '20260723120000'
 ORDER BY proc_dttm ASC, tran_dt ASC, biz_key ASC, file_seq_no ASC, firm_file_nm ASC;


-- #############################################################################
-- SECTION 1 — THE INDEX.  RUN OUTSIDE A TRANSACTION BLOCK (autocommit ON).
-- #############################################################################
--
-- Column order:
--   proc_step, proc_stat  equality, and jointly very selective (the sweep wants one
--                         narrow state out of the whole lifecycle)
--   proc_dttm             the range bound AND the leading ORDER BY key, so it comes
--                         last and lets the index satisfy both the range and the
--                         sort without a separate sort step
--
-- PARTIAL, on the sweep's exact state. The monitor only ever asks about
-- ('1','6'), so indexing the rest of the lifecycle would be dead weight on every
-- INSERT and UPDATE of a table the live JEX batch also writes.
--   >>> If a future caller passes different proc_step/proc_stat values, this
--   >>> partial index will NOT serve it and the query silently reverts to a seq
--   >>> scan. Widen the index (drop the WHERE clause) at that point rather than
--   >>> assuming it still applies.
-- `proc_dttm IS NOT NULL` is folded into the predicate too: the query requires it,
-- and it keeps the never-processed rows out of the index entirely.

CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_tb_firm_file_mst_step_stat_procdttm
    ON tb_firm_file_mst (proc_step, proc_stat, proc_dttm)
 WHERE proc_step = '1' AND proc_stat = '6' AND proc_dttm IS NOT NULL;


-- #############################################################################
-- SECTION 2 — POST-CREATE VERIFICATION.  SELECT-ONLY.
-- #############################################################################

-- 2.1  The index exists and is VALID. indisvalid = false means CONCURRENTLY failed
--      part-way; DROP INDEX CONCURRENTLY and re-run section 1.
SELECT c.relname AS index_name, i.indisvalid, i.indisready,
       pg_size_pretty(pg_relation_size(c.oid)) AS index_size
  FROM pg_class c
  JOIN pg_index i ON i.indexrelid = c.oid
 WHERE c.relname = 'ix_tb_firm_file_mst_step_stat_procdttm';

-- 2.2  Re-run the section 0.3 EXPLAIN. Expect an Index Scan on the new index, and
--      NO separate Sort node (the index supplies proc_dttm order).
--      A Seq Scan here on a small table is CORRECT, not a failure — see the
--      urgency note in the header. Re-check once the table is larger.

-- 2.3  The PK is untouched — this script only ADDED. Expect pk_tb_firm_file_mst
--      plus the new index.
SELECT indexname, indexdef
  FROM pg_indexes
 WHERE tablename = 'tb_firm_file_mst'
 ORDER BY indexname;
-- =============================================================================

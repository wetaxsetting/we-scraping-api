-- WeCMS Manual Database Change
-- Date: 2026-07-29
-- Source: migrated from Claude Code (AI)/sql/tb_batch_schedule_info_vcb_rollback_20260729.sql
-- Related record: docs/deployment/wecms-batch-schedule-seed-review.md
-- Type: DML
-- Execution: MANUAL
-- Target environments: Refactor DEV DB (reverses the VCB tuple applied 2026-07-28)
-- Executor: DBA TEAM
-- Rollback: Re-inserting the VCB tuple reverses this delete; the seed script no longer contains it
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- ROLLBACK — remove the refactor-only VCB (970436) TB_BATCH_SCHEDULE_INFO row
-- =============================================================================
--
-- File:     tb_batch_schedule_info_vcb_rollback_20260729.sql
-- Date:     2026-07-29
-- Purpose:  Restore EXACT legacy parity for audit findings R-1 and R-2 by
--           deleting the one INFO row that exists in the refactor DB but has no
--           counterpart in legacy JEX.
-- Author:   Claude Code (AI) — audit deliverable
-- Run by:   DBA, BY HAND. This is NOT a Flyway migration and MUST NOT be placed
--           under src/main/resources/db/migration/. Flyway is disabled in
--           wecms-api (ddl-auto=validate); the schema is external and
--           pre-migrated. See root CLAUDE.md §"Manual DDL execution policy".
--
-- Reverses: Claude Code (AI)/sql/tb_batch_schedule_seed_refactor.sql SECTION 1,
--           VCB tuple (applied to the refactor DEV DB 2026-07-28 18:02:09,
--           last_edtr='DBA_MANUAL'). That tuple has since been commented out in
--           the seed script, so re-running the seed will not reintroduce it.
--
-- -----------------------------------------------------------------------------
-- WHY (legacy evidence, re-verified 2026-07-29)
-- -----------------------------------------------------------------------------
-- Legacy JEX DEV tb_batch_schedule_info holds exactly 4 rows:
--     FILE_DEPOSIT_TRANSFER_BATCH1 | 970424 | Y | 000000 | 235959
--     FILE_DEPOSIT_TRANSFER_BATCH1 | 970457 | Y | 000000 | 235959
--     NAPAS_REAL_TIME_BATCH        | 970424 | Y | 000000 | 235959
--     NAPAS_REAL_TIME_BATCH        | 970457 | Y | 000000 | 235959
-- There is NO 970436 row, in any environment or script that could be found:
--   * No Flyway migration inserts it — V95__tb_batch_schedule_dtl.sql and
--     V96__tb_batch_schedule_info.sql are DDL only (0 INSERT statements).
--   * No legacy SQL / XML / properties / deployment script inserts it. The only
--     legacy hits for TB_BATCH_SCHEDULE anywhere under WECMS DEV/,
--     WECMS_BATCH DEV/, DEV ADMIN/, SOURCE_CODE_ADMIN/, SOURCE_CODED_OSMC/ are
--     the two IDO query definitions
--     (IDO.TB_BATCH_SCHEDULE_INFO_R001.xml, IDO.TB_BATCH_SCHEDULE_DTL_R001.xml).
--   * SVN r36822 (soknyran, 2026-07-16 12:05:39 +0700, redmine 43078) — the VCB
--     file-transfer switch — committed ONLY PymnHelper.java. `svn log -r
--     36795:36825 -v` contains no .sql / schedule / DB file in the entire range.
--   * No runtime code depends on the row: the dispatch package contains no
--     hard-coded 970436, and OperatingHoursService looks the bank up generically.
--
-- Because no legacy row exists, there is likewise NO legacy ANT_WHERE_USE_YN
-- value for VCB — neither 'Y' nor 'N'. R-2 is therefore NOT APPLICABLE rather
-- than an open choice. Do not reintroduce either value as a "default".
--
-- -----------------------------------------------------------------------------
-- EFFECT AFTER RUNNING (intended, legacy-identical)
-- -----------------------------------------------------------------------------
-- A VCB pay-file reaching the dispatch-time gate resolves to NO_CRITERIA and is
-- FAILED, exactly as legacy does:
--     refactor  OperatingHoursService.java:168-170   -> NO_CRITERIA
--               BulkReceptionService.java:246-263    -> PROC_STAT=FAIL
--                                                       + "대량이체 Batch 수행 기준 없음" alert
--     legacy    FILE_DEPOSIT_TRANSFER_BATCH1.java:284-291
--               -> exeErrYN=true; exeYN=false;
--                  vrfi_msg="No data to perform bulk transfer batch.
--                            Please check TB_BATCH_SCHEDULE_INFO."
--
-- ==> VCB dispatch WILL FAIL until the business explicitly decides to introduce
--     a VCB schedule. That is the legacy behaviour and it is preserved on
--     purpose. It is NOT a regression introduced by this script.
--
-- -----------------------------------------------------------------------------
-- SCOPE — what this script must NOT touch
-- -----------------------------------------------------------------------------
--   * 970424 (Shinhan) INFO rows            — leave untouched
--   * 970457 (Woori)   INFO rows            — leave untouched
--   * tb_batch_schedule_dtl (ALL 12 rows)   — leave untouched
--   * tb_firm_conf                          — leave untouched
--   * Any Java code                         — no production change is required
--
-- -----------------------------------------------------------------------------
-- RUN ORDER / DEPENDENCIES
-- -----------------------------------------------------------------------------
--   * Standalone. No dependency on any other script.
--   * Safe to run before or after tb_batch_schedule_seed_refactor.sql, but the
--     seed's VCB tuple is now commented out, so ordering no longer matters.
--   * Idempotent: a second run deletes 0 rows.
--   * ENVIRONMENT: intended for the refactor DEV DB
--     (172.168.5.172:5432/wecmsdevdb). DO NOT run against UAT or PROD without
--     first running the section-0 verification below in that environment and
--     obtaining PO (Mr. Jung) sign-off — UAT/PROD contents were NOT readable at
--     audit time and may legitimately differ.
--   * Do NOT run against the live JEX DBs. They have no 970436 row to delete.
-- =============================================================================


-- #############################################################################
-- SECTION 0 — PRE-CHECK (read-only). Run this FIRST and read the output.
-- #############################################################################
--
-- Expect exactly one row: NAPAS_REAL_TIME_BATCH | 970436 | N | 000000 | 235959.
-- If it returns 0 rows, the rollback is already done — STOP, run nothing else.
-- If ant_where_use_yn is 'Y' rather than 'N', someone changed it after
-- 2026-07-28; STOP and re-confirm with the PO before deleting.

SELECT batch_id, out_bank_cd, ant_where_use_yn,
       batch_start_tm, batch_end_tm, reg_dttm, last_edtr
FROM   tb_batch_schedule_info
WHERE  batch_id    = 'NAPAS_REAL_TIME_BATCH'
  AND  out_bank_cd = '970436';


-- #############################################################################
-- SECTION 1 — THE ROLLBACK
-- #############################################################################

BEGIN;

DELETE FROM tb_batch_schedule_info
WHERE  batch_id    = 'NAPAS_REAL_TIME_BATCH'
  AND  out_bank_cd = '970436';

-- Guard: exactly one row must have been removed. Anything else means the
-- pre-check assumption no longer holds — the transaction aborts and nothing is
-- committed. (Raise-on-mismatch, so a fat-fingered WHERE cannot silently wipe
-- the Shinhan / Woori rows.)
DO $$
DECLARE
    remaining integer;
    total     integer;
BEGIN
    SELECT count(*) INTO remaining
    FROM   tb_batch_schedule_info
    WHERE  out_bank_cd = '970436';

    SELECT count(*) INTO total
    FROM   tb_batch_schedule_info;

    IF remaining <> 0 THEN
        RAISE EXCEPTION 'ROLLBACK ABORTED: % VCB (970436) INFO row(s) still present', remaining;
    END IF;

    IF total < 2 THEN
        RAISE EXCEPTION 'ROLLBACK ABORTED: only % INFO row(s) left — Shinhan/Woori appear to have been deleted', total;
    END IF;

    RAISE NOTICE 'VCB INFO row removed. % INFO row(s) remain (expect Shinhan 970424 + Woori 970457).', total;
END $$;

COMMIT;


-- #############################################################################
-- SECTION 2 — POST-CHECK (read-only). Run after COMMIT.
-- #############################################################################
--
-- Expect: 970424 and 970457 only, no 970436 — i.e. the same bank set legacy has.

SELECT batch_id, out_bank_cd, ant_where_use_yn, batch_start_tm, batch_end_tm
FROM   tb_batch_schedule_info
ORDER  BY batch_id, out_bank_cd;

-- DTL must be UNCHANGED — 12 rows across 970424 / 970457 only.
SELECT batch_id, out_bank_cd, trim(ant_div_cd) AS ant_div_cd,
       batch_start_tm, batch_end_tm
FROM   tb_batch_schedule_dtl
ORDER  BY batch_id, out_bank_cd, ant_div_cd;

-- Sanity: no VCB row anywhere in either schedule table.
SELECT 'INFO' AS tbl, count(*) AS vcb_rows FROM tb_batch_schedule_info WHERE out_bank_cd = '970436'
UNION ALL
SELECT 'DTL',        count(*)              FROM tb_batch_schedule_dtl  WHERE out_bank_cd = '970436';
-- Both counts MUST be 0.

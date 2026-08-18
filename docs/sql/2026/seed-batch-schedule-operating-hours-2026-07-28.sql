-- WeCMS Manual Database Change
-- Date: 2026-07-28
-- Source: migrated from Claude Code (AI)/sql/tb_batch_schedule_seed_refactor.sql
-- Related record: docs/deployment/wecms-batch-schedule-seed-review.md
-- Type: DML
-- Execution: MANUAL
-- Target environments: Applied on the refactor DEV DB; still to run on SIT, UAT and PROD (per the original script status block)
-- Executor: DBA TEAM
-- Rollback: Reversible by deleting the seeded INFO/DTL rows — see the VCB rollback script for the established pattern
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- tb_batch_schedule_seed_refactor.sql
-- Operating-hours master data for the WeCMS refactor firm-banking dispatch gate.
-- Seeds TB_BATCH_SCHEDULE_INFO + TB_BATCH_SCHEDULE_DTL.
-- =============================================================================
--
-- Companion review document (READ FIRST — it carries the evidence and the open
-- questions this script defers):
--   wecms-api/docs/deployment/wecms-batch-schedule-seed-review.md
--
-- Purpose
--   `OperatingHoursService` gates EVERY firm-banking dispatch on these two
--   tables. A missing TB_BATCH_SCHEDULE_INFO row resolves to NO_CRITERIA, which
--   FAILS the payment. Enabling `wecms.dispatch.realtime-dispatch` or
--   `wecms.dispatch.auto-receive` before this script runs would fail 100% of
--   transfers. This script closes blocker C-2 of
--   wecms-batch-infrastructure-readiness-audit.md.
--
-- STATUS  (verified read-only 2026-07-28, second pass)
--   * ALREADY APPLIED on the refactor DEV DB (172.168.5.172/wecmsdevdb):
--     3 INFO + 12 DTL rows present, last_edtr='DBA_MANUAL',
--     reg_dttm='20260728180209'. Re-running is safe (DO NOTHING) and is a no-op.
--     STILL TO RUN on: SIT, UAT, PROD.
--   * Open questions closed by querying the live JEX DEV DB (read-only):
--       OQ-1 CLOSED — TB_FIRM_CONF holds EXACTLY 3 withdrawing banks:
--                     970424 (9 rows), 970436 (2), 970457 (4), all ORG_STAT='1'.
--                     No unknown fourth bank. Section 1 covers all three.
--                     Note 970436 IS a configured firm-banking bank in legacy yet
--                     has NO legacy schedule row -> the VCB gap is real.
--       OQ-3 CLOSED — legacy Shinhan SWIFT really is 090000-234500. The value in
--                     section 2 is correct; the reviewed spec's 22:00 is not what
--                     legacy runs. No change needed.
--       OQ-7 CLOSED — the JEX frame DB timezone is Asia/Seoul (UTC+9), so legacy
--                     evaluates these windows in KST, not ICT. Handled in CODE,
--                     not here (see the note below). The refactor DB is
--                     Asia/Phnom_Penh (UTC+7 = ICT), so no data change is implied.
--       OQ-5 CONFIRMED as a live failure, and partially fixed in code -> see 4.2.
--   * Two code fixes shipped alongside this script (wecms-api):
--       1. OperatingHoursService no longer hardcodes the evaluation zone; it reads
--          `wecms.dispatch.operating-hours.zone` (default Asia/Ho_Chi_Minh). No
--          data impact — the windows below are unchanged.
--       2. New AntDivChannel canonicalises ANT_DIV_CD before the realtime DTL
--          lookup.
--          CORRECTED 2026-08-03 — the original note here ("a 1-char 'N'/'C' now
--          resolves to the 'NAPAS'/'CITAD' rows") described a case that does not
--          exist. TB_PAY_TARGET.ANT_DIV_CD holds AntDivCd.getValue(), which is
--          *already* 'NAPAS'/'CITAD' in 5-char form (69 of 101 DEV rows); there
--          are ZERO 'N' or 'C' rows. Section 3 was always effective for the
--          realtime channels that actually occur. Canonicalisation changes
--          nothing here. The values it does expand — 'E'->EBANK, 'S'->SWIFT,
--          'V'->VCB — have NO realtime DTL row in section 3 and still fail
--          closed, which matches legacy.
--          DO NOT add an EBANK/SWIFT/VCB row under NAPAS_REAL_TIME_BATCH to
--          "complete" section 3: because canonical already maps to those names,
--          such a row would silently flip the refactor from fail-closed to
--          dispatch while legacy still fails. That is an OPEN PO ROUTING
--          DECISION — see batch-schedule-operating-hours-audit.md §12b.
--     Full record: wecms-api/docs/deployment/wecms-batch-schedule-seed-review.md §17
--
-- Execution model  (IMPORTANT — READ BEFORE RUNNING)
--   * RUN BY A DBA, BY HAND. This is NOT a Flyway migration and is NOT applied
--     by the application at startup. Flyway is disabled in wecms-api and
--     ddl-auto = validate in every profile (project_flyway_disabled_ddl_validate),
--     so the schema is external / pre-migrated.
--     Precedent: Claude Code (AI)/sql/cutoff-config_20260709.sql.
--   * DATA ONLY. This script contains NO DDL. It creates nothing, alters
--     nothing, drops nothing. The two tables must already exist (they do —
--     legacy-table-inventory.md:133-134).
--   * PostgreSQL. Wrapped in ONE transaction — any failure rolls everything back.
--   * Idempotent — safe to re-run. `ON CONFLICT DO NOTHING` means a re-run NEVER
--     overwrites a row that live JEX or a DBA already owns.
--   * Run once per environment (local / dev / sit / uat / prod).
--   * Section 0 (pre-checks) is SELECT-only. RUN IT FIRST, READ THE OUTPUT, and
--     STOP if anything is unexpected. Do not run sections 1-3 blind.
--
-- >>> SHARED-TABLE WARNING (UAT / PROD) <<<
--   Neither table has a BIZ_KEY column. The PK is (BATCH_ID, OUT_BANK_CD) /
--   (BATCH_ID, OUT_BANK_CD, ANT_DIV_CD) — these windows are GLOBAL, shared by
--   every tenant AND by the live JEX batch when the refactor points at the live
--   JEX DB (project_uat_prod_live_jex_db_target). Writing a row here changes
--   legacy JEX batch behaviour. On UAT/PROD, run section 0 first: if live JEX
--   rows already exist, KEEP THEM (that is what DO NOTHING guarantees) and do
--   not attempt to "correct" them without a PO decision.
--
-- Column facts (verified against the DDL, not assumed)
--   V96__tb_batch_schedule_info.sql
--     batch_id         varchar(50) NOT NULL   \  PK
--     out_bank_cd      varchar(15) NOT NULL   /
--     ant_where_use_yn bpchar(1)   NULL DEFAULT 'N'
--     batch_start_tm   bpchar(6)   NOT NULL   -- HHmmss, exactly 6 digits
--     batch_end_tm     bpchar(6)   NOT NULL   -- HHmmss, exactly 6 digits
--     reg_dttm         varchar(14) NULL       -- yyyyMMddHHmmss
--     mod_dttm         varchar(14) NULL
--     last_edtr        varchar(20) NULL
--   V95__tb_batch_schedule_dtl.sql
--     batch_id         varchar(50) NOT NULL   \
--     out_bank_cd      varchar(15) NOT NULL    > PK
--     ant_div_cd       bpchar(5)   NOT NULL   /
--     batch_start_tm   bpchar(6)   NOT NULL
--     batch_end_tm     bpchar(6)   NOT NULL
--     reg_dttm         varchar(14) NULL
--     last_edtr        varchar(20) NULL
--     (NOTE: DTL has NO mod_dttm column. INFO does.)
--
--   * reg_dttm / mod_dttm are nullable varchar(14) with no CHECK constraint, so
--     the legacy '' (empty string) is technically valid. This script writes a
--     REAL DB-computed timestamp instead — to_char(now(),'YYYYMMDDHH24MISS') —
--     because it is a valid value, it is not invented by hand, and it makes the
--     seeded rows traceable. No application code reads these columns
--     (OperatingHoursService reads only *_TM and ANT_WHERE_USE_YN).
--   * last_edtr = 'DBA_MANUAL' (10 chars, fits varchar(20)).
--   * ant_div_cd is bpchar(5): PostgreSQL blank-pads on INSERT, so 'VCB' is
--     stored as 'VCB  '. Trailing spaces in the legacy INSERT are cosmetic and
--     are NOT required here. bpchar equality ignores trailing blanks, and
--     bpchar->text casting strips them, so the lookup matches either way.
--   * No key generation. Both PKs are natural composites — wb_get_next_val is
--     NOT involved (project_wb_get_next_val_funnel does not apply).
--
-- Row-selection rule applied here
--   Only rows PROVEN REQUIRED by refactor code are executable. Every row whose
--   necessity or window is unconfirmed is left as COMMENTED-OUT SQL in section 4
--   with the reason. No window, channel code, bank code or timestamp is invented.
-- =============================================================================


-- #############################################################################
-- SECTION 0 — PRE-CHECKS.  SELECT-ONLY.  RUN FIRST, READ, THEN DECIDE.
--             Do not proceed to section 1 until every check is understood.
-- #############################################################################

-- 0.1  What is already there? (expected: 0 rows on a fresh refactor DB)
SELECT 'INFO' AS tbl, batch_id, out_bank_cd, ant_where_use_yn,
       batch_start_tm, batch_end_tm, reg_dttm, mod_dttm, last_edtr
  FROM tb_batch_schedule_info
 ORDER BY batch_id, out_bank_cd;

SELECT 'DTL' AS tbl, batch_id, out_bank_cd, ant_div_cd,
       batch_start_tm, batch_end_tm, reg_dttm, last_edtr
  FROM tb_batch_schedule_dtl
 ORDER BY batch_id, out_bank_cd, ant_div_cd;

-- 0.2  Row counts (blocker C-2 is "both are 0").
SELECT (SELECT count(*) FROM tb_batch_schedule_info) AS info_rows,
       (SELECT count(*) FROM tb_batch_schedule_dtl)  AS dtl_rows;

-- 0.3  PK collisions with what this script would insert.
--      Any row returned here will be PRESERVED (DO NOTHING) — review it.
SELECT i.batch_id, i.out_bank_cd, i.batch_start_tm, i.batch_end_tm, i.last_edtr
  FROM tb_batch_schedule_info i
 WHERE (i.batch_id, i.out_bank_cd) IN (
        ('NAPAS_REAL_TIME_BATCH','970424'),
        ('NAPAS_REAL_TIME_BATCH','970457'),
        ('NAPAS_REAL_TIME_BATCH','970436'));

SELECT d.batch_id, d.out_bank_cd, d.ant_div_cd, d.batch_start_tm, d.batch_end_tm, d.last_edtr
  FROM tb_batch_schedule_dtl d
 WHERE (d.batch_id, d.out_bank_cd, rtrim(d.ant_div_cd)) IN (
        ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','NAPAS'),
        ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','CITAD'),
        ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','EBANK'),
        ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','SWIFT'),
        ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','NAPAS'),
        ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','CITAD'),
        ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','VCB'),
        ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','SWIFT'),
        ('NAPAS_REAL_TIME_BATCH','970424','NAPAS'),
        ('NAPAS_REAL_TIME_BATCH','970424','CITAD'),
        ('NAPAS_REAL_TIME_BATCH','970457','NAPAS'),
        ('NAPAS_REAL_TIME_BATCH','970457','CITAD'));

-- 0.4  *** THE MOST IMPORTANT PRE-CHECK ***
--      Which withdrawing banks does this environment actually dispatch from?
--      Every DISTINCT out_bank_cd in TB_FIRM_CONF needs an INFO row, or its
--      dispatch resolves to NO_CRITERIA and FAILS. If this returns a bank code
--      that is not 970424 / 970457 / 970436, STOP — an extra INFO row is needed
--      and its window is a PO/bank question (see review doc OQ-1).
SELECT c.out_bank_cd,
       count(*)                                             AS firm_conf_rows,
       min(c.org_stat)                                      AS min_org_stat,
       (SELECT count(*) FROM tb_batch_schedule_info i
         WHERE i.batch_id = 'NAPAS_REAL_TIME_BATCH'
           AND i.out_bank_cd = c.out_bank_cd)               AS has_info_row
  FROM tb_firm_conf c
 GROUP BY c.out_bank_cd
 ORDER BY c.out_bank_cd;

-- 0.5  Data hygiene on any pre-existing rows: bad time format / unknown channel.
--      (Expected: 0 rows. Non-zero means legacy data needs review before seeding.)
SELECT 'bad_time_info' AS issue, batch_id, out_bank_cd, batch_start_tm, batch_end_tm
  FROM tb_batch_schedule_info
 WHERE batch_start_tm !~ '^[0-9]{6}$' OR batch_end_tm !~ '^[0-9]{6}$'
UNION ALL
SELECT 'bad_time_dtl', batch_id, out_bank_cd || '/' || rtrim(ant_div_cd),
       batch_start_tm, batch_end_tm
  FROM tb_batch_schedule_dtl
 WHERE batch_start_tm !~ '^[0-9]{6}$' OR batch_end_tm !~ '^[0-9]{6}$';

-- 0.6  Inverted windows (start >= end) — such a row can never resolve to 'Y'.
SELECT 'inverted_info' AS issue, batch_id, out_bank_cd, batch_start_tm, batch_end_tm
  FROM tb_batch_schedule_info WHERE batch_start_tm >= batch_end_tm
UNION ALL
SELECT 'inverted_dtl', batch_id, out_bank_cd || '/' || rtrim(ant_div_cd),
       batch_start_tm, batch_end_tm
  FROM tb_batch_schedule_dtl WHERE batch_start_tm >= batch_end_tm;

-- 0.7  Channel values outside the 5 codes the refactor can ever query, and
--      trailing-space anomalies. `ant_div_cd` is bpchar(5) so EVERY value is
--      blank-padded — this check compares the TRIMMED value against the
--      BulkReceptionService.CHANNEL_BY_ANT_DIV value set plus the realtime
--      TB_PAY_TARGET.ANT_DIV_CD domain.
SELECT batch_id, out_bank_cd, '[' || ant_div_cd || ']' AS raw_padded,
       rtrim(ant_div_cd) AS trimmed, length(ant_div_cd) AS stored_len
  FROM tb_batch_schedule_dtl
 WHERE rtrim(ant_div_cd) NOT IN
       ('EBANK','VCB','CITAD','NAPAS','SWIFT','INTER','N','C','S')
 ORDER BY batch_id, out_bank_cd, ant_div_cd;

-- 0.8  DTL rows whose parent INFO row is absent. Not an FK violation (there is
--      no FK) but such a DTL row is unreachable: the INFO lookup fails first.
SELECT d.batch_id, d.out_bank_cd, rtrim(d.ant_div_cd) AS ant_div_cd
  FROM tb_batch_schedule_dtl d
 WHERE NOT EXISTS (SELECT 1 FROM tb_batch_schedule_info i
                    WHERE i.batch_id    = 'NAPAS_REAL_TIME_BATCH'
                      AND i.out_bank_cd = d.out_bank_cd)
 ORDER BY 1, 2, 3;

-- 0.9  Overlapping / redundant windows within one (batch_id, out_bank_cd).
--      Overlap is NOT an error — the refactor evaluates each channel
--      independently and takes any-N -> SKIP, else any-E -> CUTOFF. This is
--      informational: it shows which channels can disagree at a given moment.
SELECT a.batch_id, a.out_bank_cd,
       rtrim(a.ant_div_cd) AS chan_a, a.batch_start_tm || '-' || a.batch_end_tm AS win_a,
       rtrim(b.ant_div_cd) AS chan_b, b.batch_start_tm || '-' || b.batch_end_tm AS win_b
  FROM tb_batch_schedule_dtl a
  JOIN tb_batch_schedule_dtl b
    ON  a.batch_id    = b.batch_id
    AND a.out_bank_cd = b.out_bank_cd
    AND a.ant_div_cd  < b.ant_div_cd
   AND a.batch_start_tm <= b.batch_end_tm
   AND b.batch_start_tm <= a.batch_end_tm
 ORDER BY 1, 2, 3, 5;

-- 0.10 Confirm the dispatch flags are still OFF. This seed must land while
--      dispatch is disabled. Flags live in host OS env, NOT in the DB, so this
--      cannot be checked in SQL — verify on the app host instead:
--          env | grep -E 'WECMS_DISPATCH_|WECMS_EFS_ENABLED|WECMS_SFTP_ENABLED'
--      Expected: recipient-inquiry / realtime-dispatch / auto-receive / efs /
--      sftp all false. See infrastructure-readiness-audit C-1.


-- #############################################################################
-- SECTION 1 — TB_BATCH_SCHEDULE_INFO  (per withdrawing bank, 24h window)
-- #############################################################################
--
-- WHY ONLY 'NAPAS_REAL_TIME_BATCH' APPEARS HERE:
--   BOTH gates read the INFO table with the SAME key. Verified:
--     OperatingHoursService.java:83   evaluateRealtime -> BATCH_ID_NAPAS_REAL_TIME
--     OperatingHoursService.java:129  evaluateBulk     -> BATCH_ID_NAPAS_REAL_TIME
--   No call site anywhere passes BATCH_ID_FILE_DEPOSIT_TRANSFER_1 to
--   infoRepository. This is faithful legacy parity:
--     FILE_DEPOSIT_TRANSFER_BATCH1.java:272  idoInBSIR001.put("BATCH_ID","NAPAS_REAL_TIME_BATCH")
--     NAPAS_REAL_TIME_BATCH.java:113         same literal
--   => The legacy ('FILE_DEPOSIT_TRANSFER_BATCH1', ...) INFO rows are DEAD DATA.
--      They are NOT inserted. See section 4.1.
--
-- WINDOW: 000000-235959 (24h) is the legacy value and is deliberate. The real
--   per-channel restriction lives in the DTL table; INFO is the bank-level
--   on/off + the ANT_WHERE_USE_YN switch that decides whether DTL is consulted.
--
-- ANT_WHERE_USE_YN semantics (OperatingHoursService.java:97 and :142):
--   'Y' -> consult the per-channel DTL table.
--   anything else -> DISPATCH immediately once the INFO window is open.

BEGIN;

INSERT INTO tb_batch_schedule_info (
    batch_id, out_bank_cd, ant_where_use_yn,
    batch_start_tm, batch_end_tm, reg_dttm, mod_dttm, last_edtr)
VALUES
    -- Shinhan Bank. ant_where_use_yn='Y' -> per-channel DTL applies.
    -- Legacy-confirmed row. Reached by: realtime (VND API path) AND bulk (file path).
    -- Bank code source: BulkTransferConstants.java:21 BANK_CD_SHINHAN = "970424".
    ('NAPAS_REAL_TIME_BATCH', '970424', 'Y', '000000', '235959',
     to_char(now(),'YYYYMMDDHH24MISS'), NULL, 'DBA_MANUAL'),

    -- Woori Bank. ant_where_use_yn='Y' -> per-channel DTL applies.
    -- Legacy-confirmed row. Reached by: realtime AND bulk.
    -- Bank code source: BulkTransferConstants.java:23 BANK_CD_WOORI = "970457".
    ('NAPAS_REAL_TIME_BATCH', '970457', 'Y', '000000', '235959',
     to_char(now(),'YYYYMMDDHH24MISS'), NULL, 'DBA_MANUAL')

    -- =====================================================================
    -- WITHDRAWN 2026-07-29 — the VCB (970436) tuple below is COMMENTED OUT.
    -- =====================================================================
    -- DECISION: exact legacy parity. Legacy JEX has NO 970436 INFO row in any
    -- environment or script, therefore the refactor must not have one either.
    -- The row this tuple created was applied to the refactor DEV DB on
    -- 2026-07-28 and has been rolled back by
    --   Claude Code (AI)/sql/tb_batch_schedule_info_vcb_rollback_20260729.sql
    --
    -- Re-verified 2026-07-29: no Flyway migration inserts it (V95/V96 are DDL
    -- only); no legacy SQL/XML/properties/deployment script inserts it; SVN
    -- r36822 (the 2026-07-16 VCB file-transfer switch) committed PymnHelper.java
    -- ONLY and no schedule script; no runtime code depends on the row.
    --
    -- CONSEQUENCE, ACCEPTED ON PURPOSE: VCB file dispatch resolves to
    -- NO_CRITERIA and FAILS — byte-for-byte the legacy outcome
    -- (FILE_DEPOSIT_TRANSFER_BATCH1.java:284-291). VCB dispatch will keep
    -- failing until the business explicitly decides to introduce a VCB
    -- schedule. Do NOT uncomment this tuple to "make VCB work" — that is a PO
    -- (Mr. Jung) decision, and reviving it needs a fresh ANT_WHERE_USE_YN
    -- decision too, because legacy supplies no value to copy (finding R-2 is
    -- NOT APPLICABLE, not an open choice between 'Y' and 'N').
    --
    -- Full record: wecms-api/docs/references/support-matrix.md §11a R-1/R-2.
    -- ---------------------------------------------------------------------
    -- Vietcombank (VCB). NOT present in the legacy INSERT supplied for review.
    -- Historical rationale for having proposed it, retained for the record:
    --   ExpenseDispatchExecutor.java:143-157 — ALL VCB (970436), VND *and* USD,
    --   is forced onto the FILE path (`isVcbFileTransfer`), which calls
    --   autoReceiveRegistrar (:317) -> BulkReceptionService.receive ->
    --   evaluateBulk('970436', ...). VendorDispatchExecutor mirrors this.
    --   With no INFO row, OperatingHoursService.java:131-133 returns NO_CRITERIA
    --   and BulkReceptionService.java:254-269 marks the pay-file FAIL.
    --   Corroborated by memory project_vcb_file_transfer_switch (JEX moved VCB
    --   off the Transfer API to File-based for VND+USD).
    --
    -- ant_where_use_yn = 'N' — DELIBERATE, and the one value in this script that
    -- is a judgement call. Rationale (see review doc OQ-2):
    --   * VCB has no per-channel DTL rows in legacy, so with 'Y' every
    --     recognised channel would pass anyway (BATCH1 missing-DTL = pass,
    --     OperatingHoursService.java:158-160) — 'Y' and 'N' are equivalent for
    --     every recognised channel.
    --   * They differ in exactly one case, and it is a real one: VCB USD sets
    --     ANT_DIV_CD = null (ExpenseDispatchExecutor.java:281-283). A VCB
    --     USD-only file therefore has an EMPTY presentChannels set, which with
    --     'Y' hits OperatingHoursService.java:147-149 -> SKIP: the file is left
    --     WAIT forever with NO alert and NO failure. 'N' short-circuits at :142
    --     and dispatches.
    --   * 'N' is also the table DEFAULT (V96 DDL), so it is the least surprising
    --     value for a bank with no channel rows.
    -- >>> If section 0.1 shows the live JEX DB already carries a 970436 row,
    -- >>> DO NOTHING preserves it — that legacy row wins over this one.
    --
    -- WITHDRAWN 2026-07-29 — tuple disabled, see the block above the VCB comment:
    -- ('NAPAS_REAL_TIME_BATCH', '970436', 'N', '000000', '235959',
    --  to_char(now(),'YYYYMMDDHH24MISS'), NULL, 'DBA_MANUAL')

ON CONFLICT (batch_id, out_bank_cd) DO NOTHING;


-- #############################################################################
-- SECTION 2 — TB_BATCH_SCHEDULE_DTL, batch_id = 'FILE_DEPOSIT_TRANSFER_BATCH1'
--             Per-channel windows for the BULK / FILE dispatch path (00301).
-- #############################################################################
--
-- KEY: the bulk gate reads INFO under 'NAPAS_REAL_TIME_BATCH' (section 1) but
--      DTL under 'FILE_DEPOSIT_TRANSFER_BATCH1'. This split-key behaviour is
--      NOT a bug — it is verbatim legacy:
--        OperatingHoursService.java:156   BATCH_ID_FILE_DEPOSIT_TRANSFER_1
--        FILE_DEPOSIT_TRANSFER_BATCH1.java:801  operTimeChk() same literal
--
-- CHANNEL CODES: the 5-char channel NAMES, not the 1-char storage codes.
--      BulkReceptionService.java:145-146 maps the file's 1-char
--      TB_PAY_FILE_DATA.ANT_DIV_CD (bpchar(1), V105 DDL:18) to the DTL key:
--          E->EBANK   V->VCB   C->CITAD   N->NAPAS   S->SWIFT
--      confirmed against the legacy IDO.TB_PAY_FILE_DATA_R005.xml CASE
--      expression and FILE_DEPOSIT_TRANSFER_BATCH1.java:299-397.
--      A null/unmapped code is legacy ETC and is NOT gated.
--
-- MISSING-ROW SEMANTICS: for THIS batch_id a missing DTL row means PASS
--      (OperatingHoursService.java:158-160, legacy operTimeChk :814-817
--      `exeYNE = "Y"`). So gaps here are benign — they never fail a payment.
--      Precedence across the channels present in one file: any 'N' -> SKIP,
--      else any 'E' -> CUTOFF, else DISPATCH (:152-169).

INSERT INTO tb_batch_schedule_dtl (
    batch_id, out_bank_cd, ant_div_cd,
    batch_start_tm, batch_end_tm, reg_dttm, last_edtr)
VALUES
    -- ---- Shinhan 970424 -------------------------------------------------
    -- NAPAS 00:05:00-23:45:00. Matches the reviewed spec exactly.
    ('FILE_DEPOSIT_TRANSFER_BATCH1', '970424', 'NAPAS', '000500', '234500',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),
    -- CITAD 09:00:00-15:30:00 = the spec's "CITAD file" window. Correct for
    -- this table: FILE_DEPOSIT_TRANSFER_BATCH1 IS the file path. The spec's
    -- separate "CITAD API 09:00-16:30" is NOT representable here and does NOT
    -- belong here — it is enforced by CutoffValidator/CutoffConfigProvider
    -- (TB_CUTOFF_CONFIG rail SHINHAN/CITAD_API). See review doc section 6.
    ('FILE_DEPOSIT_TRANSFER_BATCH1', '970424', 'CITAD', '090000', '153000',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),
    -- EBANK 09:00:00-15:30:00. Reachable: Shinhan USD sets ANT_DIV_CD='E'
    -- (ExpenseDispatchExecutor.java:277-278 / VendorDispatchExecutor:284-285).
    ('FILE_DEPOSIT_TRANSFER_BATCH1', '970424', 'EBANK', '090000', '153000',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),
    -- SWIFT 09:00:00-23:45:00 — LEGACY VALUE, KEPT VERBATIM.
    -- >>> CONFLICT WITH THE REVIEWED SPEC, which states Shinhan SWIFT
    -- >>> 09:00-22:00 (220000). Legacy says 234500. Legacy wins in this script
    -- >>> (legacy-first rule). This is OQ-3 in the review doc and needs a PO /
    -- >>> bank confirmation. If 22:00 is correct, change ONLY this one value.
    -- Reachable: OverseasDispatchExecutor.java:107 ANT_DIV_CD_SWIFT="S".
    ('FILE_DEPOSIT_TRANSFER_BATCH1', '970424', 'SWIFT', '090000', '234500',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),

    -- ---- Woori 970457 ---------------------------------------------------
    -- NAPAS 00:15:00-23:55:00. Matches the reviewed spec.
    ('FILE_DEPOSIT_TRANSFER_BATCH1', '970457', 'NAPAS', '001500', '235500',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),
    -- CITAD 08:30:00-17:40:00. Matches the reviewed spec.
    ('FILE_DEPOSIT_TRANSFER_BATCH1', '970457', 'CITAD', '083000', '174000',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),
    -- VCB 08:30:00-17:40:00 — the row that looks wrong but is CORRECT.
    -- 'VCB' here is a DESTINATION CHANNEL NAME, not the withdrawing bank. It is
    -- legitimately paired with Woori because Woori USD remittances are routed
    -- over the VCB channel: ExpenseDispatchExecutor.java:279-280 and
    -- VendorDispatchExecutor.java:286-287 set ANT_DIV_CD='V' when
    -- out_bank_cd == wooriBankCd and the line currency is USD. 'V' then maps to
    -- 'VCB' via CHANNEL_BY_ANT_DIV. Legacy twin: operTimeChk(bank,"VCB") at
    -- FILE_DEPOSIT_TRANSFER_BATCH1.java:321. Trailing spaces in the legacy
    -- 'VCB  ' literal are bpchar(5) padding only and are not needed.
    ('FILE_DEPOSIT_TRANSFER_BATCH1', '970457', 'VCB', '083000', '174000',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),
    -- SWIFT 08:30:00-19:10:00. Matches the reviewed spec.
    ('FILE_DEPOSIT_TRANSFER_BATCH1', '970457', 'SWIFT', '083000', '191000',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL')

ON CONFLICT (batch_id, out_bank_cd, ant_div_cd) DO NOTHING;


-- #############################################################################
-- SECTION 3 — TB_BATCH_SCHEDULE_DTL, batch_id = 'NAPAS_REAL_TIME_BATCH'
--             Per-network windows for the REALTIME dispatch path (202/204).
-- #############################################################################
--
-- >>> THESE ROWS ARE THE HIGH-RISK ONES. <<<
--      For THIS batch_id a MISSING DTL row means NO_CRITERIA -> the payment is
--      marked FAIL and an ops alert fires (OperatingHoursService.java:104-107,
--      legacy NAPAS_REAL_TIME_BATCH.java:155-160). This is the OPPOSITE of the
--      BATCH1 semantics in section 2, and it is genuine legacy asymmetry that
--      must be preserved, not tidied up.
--
-- KEY: the raw TB_PAY_TARGET.ANT_DIV_CD value, passed through untouched
--      (RealtimeDispatchService.java:154). That column is bpchar(5) NOT NULL
--      (V109 DDL:14), so the realtime domain is WIDER than the bulk domain:
--        'INTER' -> DTL lookup SKIPPED entirely (OperatingHoursService.java:97)
--        'NAPAS' / 'CITAD'  -> the two rows below
--        'N' / 'C' / 'S'    -> 1-char aliases, ACCEPTED BY VALIDATION but NOT
--                              SEEDED. See section 4.2 — this is a live hazard.
--      SWIFT is not seeded for this batch_id because SWIFT never reaches the
--      realtime path: OverseasDispatchExecutor is file-only (it calls
--      autoReceiveRegistrar at :322 and :409, never realtimeDispatchRegistrar).
--      Payroll likewise is file-only (PayrollDispatchExecutor.java:237).

INSERT INTO tb_batch_schedule_dtl (
    batch_id, out_bank_cd, ant_div_cd,
    batch_start_tm, batch_end_tm, reg_dttm, last_edtr)
VALUES
    -- ---- Shinhan 970424 -------------------------------------------------
    -- NAPAS 00:05:00-23:45:00 (same window as the file path).
    ('NAPAS_REAL_TIME_BATCH', '970424', 'NAPAS', '000500', '234500',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),
    -- CITAD 09:00:00-15:30:00 — legacy value kept verbatim.
    -- NOTE: this is the realtime/API path, and the reviewed spec says the
    -- Shinhan CITAD *API* cut-off is 16:30, not 15:30. The 16:30 rule IS
    -- implemented, but in the OTHER system: CutoffValidator.java:204 selects
    -- the SHINHAN/CITAD_API window for VND->VND. Changing this row to 163000
    -- would double-apply that rule at dispatch time. Legacy value retained;
    -- flagged as OQ-4 in the review doc.
    ('NAPAS_REAL_TIME_BATCH', '970424', 'CITAD', '090000', '153000',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),

    -- ---- Woori 970457 ---------------------------------------------------
    -- NAPAS 00:15:00-23:55:00.
    ('NAPAS_REAL_TIME_BATCH', '970457', 'NAPAS', '001500', '235500',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL'),
    -- CITAD 08:30:00-17:40:00.
    ('NAPAS_REAL_TIME_BATCH', '970457', 'CITAD', '083000', '174000',
     to_char(now(),'YYYYMMDDHH24MISS'), 'DBA_MANUAL')

    -- NO VCB (970436) ROWS FOR THIS batch_id. VCB never uses the realtime path
    -- (ExpenseDispatchExecutor.java:145-157 forces all VCB onto the file path),
    -- and the section 1 VCB row carries ant_where_use_yn='N', so the DTL table
    -- is never consulted for 970436 at all.

ON CONFLICT (batch_id, out_bank_cd, ant_div_cd) DO NOTHING;

COMMIT;


-- #############################################################################
-- SECTION 4 — ROWS DELIBERATELY *NOT* INSERTED.  COMMENTED, NOT EXECUTABLE.
--             Each needs a decision before it may be enabled.
-- #############################################################################

-- ---------------------------------------------------------------------------
-- 4.1  Legacy INFO rows for batch_id = 'FILE_DEPOSIT_TRANSFER_BATCH1' — DEAD.
--      Supplied in the legacy INSERT under review, but no refactor code path
--      and no legacy code path reads the INFO table under this key (both batches
--      look INFO up as 'NAPAS_REAL_TIME_BATCH'). Inserting them is harmless but
--      misleading: a future reader would assume they gate the file path when
--      they gate nothing. EXCLUDED for clarity. Enable only if a future change
--      makes evaluateBulk use its own INFO key.
--
--   INSERT INTO tb_batch_schedule_info (batch_id, out_bank_cd, ant_where_use_yn,
--          batch_start_tm, batch_end_tm, reg_dttm, mod_dttm, last_edtr)
--   VALUES ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','Y','000000','235959',
--           to_char(now(),'YYYYMMDDHH24MISS'), NULL, 'DBA_MANUAL'),
--          ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','Y','000000','235959',
--           to_char(now(),'YYYYMMDDHH24MISS'), NULL, 'DBA_MANUAL')
--   ON CONFLICT (batch_id, out_bank_cd) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4.2  *** OPEN HAZARD — 1-char ANT_DIV_CD aliases on the REALTIME path. ***
--      TransferLineInput.java:32-33 validates ant_div_cd against
--          ^(N|NAPAS|INTER|C|CITAD|S|SWIFT)?$
--      so a client may legitimately submit 'N' or 'C'. That value is carried
--      into SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD (bpchar(5)) and then verbatim into
--      TB_PAY_TARGET.ANT_DIV_CD by resolveAntDivCd (TransferDispatchExecutor
--      .java:387-393, InternalTransferDispatchExecutor:439-445,
--      ExpenseDispatchExecutor:374-381). The realtime DTL lookup then searches
--      for ant_div_cd='N', finds nothing, and FAILS THE PAYMENT with
--      "실시간지급이체 Batch 수행 기준 없음".
--      The same applies to any raw code HAENGSUNG's ERP import passes through:
--      ErpPaymentRefreshMasterImporter.java:273-275 mapAntDivCd() is a plain
--      trim, and its own Javadoc records an UNRESOLVED 'T' value (M-3/M-12).
--
--      This hazard is INHERITED FROM LEGACY, not introduced by the refactor —
--      legacy has the identical two-row seed and the identical fail path.
--
--      >>> CONFIRMED ON LIVE LEGACY DATA (read-only, JEX DEV, 2026-07-28) <<<
--          tb_pay_target.ant_div_cd distribution:
--            CITAD 39 | NAPAS 30 | INTER 28 | 'E' 2 | 'S' 2
--          The four 1-char rows, in detail:
--            (970424,'E') x2  PROC_STAT='3'  FAIL      2025-09-18 17:11:52
--            (970424,'S') x2  PROC_STAT='9'  stranded  2025-09-19 09:52:30
--          i.e. this has ALREADY failed two payments and stranded two more.
--
--      >>> RESOLVED IN CODE (2026-07-28) — option (b) was implemented. <<<
--          New AntDivChannel.canonical(...) is applied in
--          OperatingHoursService.evaluateRealtime BEFORE the DTL lookup, so:
--            'N' -> NAPAS   and   'C' -> CITAD   now hit the rows seeded in
--          section 3, and bpchar padding is stripped. NO DATA ROWS NEEDED for
--          the N/C aliases — the block below is therefore NO LONGER REQUIRED
--          and is retained only as historical context. Do not run it.
--
--      >>> STILL OPEN (PO routing decision, NOT a window decision) <<<
--          'S' canonicalises to SWIFT and 'E' to EBANK, and neither has a
--          NAPAS_REAL_TIME_BATCH row — so those still fail. That is arguably
--          CORRECT: SWIFT/EBANK are file-path channels, and a SWIFT payment on
--          the NAPAS realtime API rail is bad routing, not a bad window. The
--          refactor's own API boundary (TransferLineInput @Pattern) already
--          rejects 'E' and 'V', so the live exposure is SWIFT-on-realtime plus
--          the HAENGSUNG raw-passthrough path. Adding EBANK/SWIFT rows here
--          would ALLOW dispatches legacy refused — get PO sign-off first.
--
--   INSERT INTO tb_batch_schedule_dtl (batch_id, out_bank_cd, ant_div_cd,
--          batch_start_tm, batch_end_tm, reg_dttm, last_edtr)
--   VALUES ('NAPAS_REAL_TIME_BATCH','970424','N','000500','234500',
--           to_char(now(),'YYYYMMDDHH24MISS'),'DBA_MANUAL'),  -- = 970424 NAPAS
--          ('NAPAS_REAL_TIME_BATCH','970424','C','090000','153000',
--           to_char(now(),'YYYYMMDDHH24MISS'),'DBA_MANUAL'),  -- = 970424 CITAD
--          ('NAPAS_REAL_TIME_BATCH','970457','N','001500','235500',
--           to_char(now(),'YYYYMMDDHH24MISS'),'DBA_MANUAL'),  -- = 970457 NAPAS
--          ('NAPAS_REAL_TIME_BATCH','970457','C','083000','174000',
--           to_char(now(),'YYYYMMDDHH24MISS'),'DBA_MANUAL')   -- = 970457 CITAD
--   ON CONFLICT (batch_id, out_bank_cd, ant_div_cd) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4.3  VCB per-channel DTL rows — NOT REQUIRED, and NOT derivable.
--      The reviewed spec lists VCB NAPAS 00:05-23:45, VCB CITAD-small
--      08:00-15:30 and VCB CITAD-large 08:00-16:30. None of these belong in
--      TB_BATCH_SCHEDULE_DTL:
--        * The section 1 VCB row is ant_where_use_yn='N', so DTL is never read
--          for 970436.
--        * TB_BATCH_SCHEDULE_DTL has NO amount column. Its PK is
--          (batch_id, out_bank_cd, ant_div_cd) — there is exactly ONE CITAD row
--          per bank and it is structurally INCAPABLE of expressing an
--          amount-dependent window. Do not attempt to encode the >=500M tier
--          here (e.g. a fake 'CITADL' channel) — the refactor would never query
--          it, because CHANNEL_BY_ANT_DIV only ever produces the 5 real names.
--        * The amount tier IS already implemented, in the OTHER system:
--          CutoffValidator.java:208-210 selects the VCB CITAD_LARGE window when
--          ctx.hasTxnAtOrAbove500M(), with the threshold read from
--          TB_CUTOFF_PARAM 'large-txn-threshold' (CutoffConfigProvider:59,
--          :187-206). Required VCB rails are declared at
--          CutoffConfigProvider.java:69 as NAPAS + CITAD + CITAD_LARGE.
--      => VCB windows are a TB_CUTOFF_CONFIG concern. Verify them with
--         Claude Code (AI)/sql/cutoff-config_20260709.sql, NOT with this script.

-- ---------------------------------------------------------------------------
-- 4.4  Any bank code returned by pre-check 0.4 other than 970424/970457/970436.
--      If TB_FIRM_CONF references a further withdrawing bank, that bank NEEDS an
--      INFO row or its dispatch fails. Its window is unknown and must NOT be
--      guessed — obtain it from the bank / PO first (review doc OQ-1).


-- #############################################################################
-- SECTION 5 — POST-INSERT VERIFICATION.  SELECT-ONLY.  RUN AFTER COMMIT.
-- #############################################################################

-- 5.1  Expected: info_rows >= 3, dtl_rows >= 12 (higher if legacy rows existed).
SELECT (SELECT count(*) FROM tb_batch_schedule_info) AS info_rows,
       (SELECT count(*) FROM tb_batch_schedule_dtl)  AS dtl_rows;

-- 5.2  Every row this script intended, with a PRESENT/MISSING verdict.
--      Any 'MISSING' means the INSERT was blocked — investigate before enabling.
WITH expected(tbl, batch_id, out_bank_cd, ant_div_cd) AS (VALUES
    ('INFO','NAPAS_REAL_TIME_BATCH',       '970424', NULL),
    ('INFO','NAPAS_REAL_TIME_BATCH',       '970457', NULL),
    ('INFO','NAPAS_REAL_TIME_BATCH',       '970436', NULL),
    ('DTL','FILE_DEPOSIT_TRANSFER_BATCH1', '970424', 'NAPAS'),
    ('DTL','FILE_DEPOSIT_TRANSFER_BATCH1', '970424', 'CITAD'),
    ('DTL','FILE_DEPOSIT_TRANSFER_BATCH1', '970424', 'EBANK'),
    ('DTL','FILE_DEPOSIT_TRANSFER_BATCH1', '970424', 'SWIFT'),
    ('DTL','FILE_DEPOSIT_TRANSFER_BATCH1', '970457', 'NAPAS'),
    ('DTL','FILE_DEPOSIT_TRANSFER_BATCH1', '970457', 'CITAD'),
    ('DTL','FILE_DEPOSIT_TRANSFER_BATCH1', '970457', 'VCB'),
    ('DTL','FILE_DEPOSIT_TRANSFER_BATCH1', '970457', 'SWIFT'),
    ('DTL','NAPAS_REAL_TIME_BATCH',        '970424', 'NAPAS'),
    ('DTL','NAPAS_REAL_TIME_BATCH',        '970424', 'CITAD'),
    ('DTL','NAPAS_REAL_TIME_BATCH',        '970457', 'NAPAS'),
    ('DTL','NAPAS_REAL_TIME_BATCH',        '970457', 'CITAD'))
SELECT e.tbl, e.batch_id, e.out_bank_cd, coalesce(e.ant_div_cd,'-') AS ant_div_cd,
       CASE WHEN e.tbl = 'INFO' THEN
                 CASE WHEN EXISTS (SELECT 1 FROM tb_batch_schedule_info i
                                    WHERE i.batch_id = e.batch_id
                                      AND i.out_bank_cd = e.out_bank_cd)
                      THEN 'PRESENT' ELSE 'MISSING' END
            ELSE CASE WHEN EXISTS (SELECT 1 FROM tb_batch_schedule_dtl d
                                    WHERE d.batch_id = e.batch_id
                                      AND d.out_bank_cd = e.out_bank_cd
                                      AND rtrim(d.ant_div_cd) = e.ant_div_cd)
                      THEN 'PRESENT' ELSE 'MISSING' END
       END AS verdict
  FROM expected e
 ORDER BY e.tbl DESC, e.batch_id, e.out_bank_cd, e.ant_div_cd NULLS FIRST;

-- 5.3  Re-run the hygiene checks 0.5 / 0.6 / 0.7 / 0.8. All must return 0 rows.

-- 5.4  Every dispatching bank now has an INFO row. `has_info_row` must be 1
--      for EVERY row returned (re-run of pre-check 0.4).
SELECT c.out_bank_cd,
       (SELECT count(*) FROM tb_batch_schedule_info i
         WHERE i.batch_id = 'NAPAS_REAL_TIME_BATCH'
           AND i.out_bank_cd = c.out_bank_cd) AS has_info_row
  FROM tb_firm_conf c
 GROUP BY c.out_bank_cd
 ORDER BY c.out_bank_cd;

-- 5.5  BEHAVIOURAL DRY-RUN — replay OperatingHoursService's EXE_DIV logic in
--      SQL for the current ICT wall-clock, WITHOUT dispatching anything.
--      This is the single most useful check: it shows exactly what the gate
--      would decide right now for every seeded key.
--      EXE_DIV (OperatingHoursService.java:187-195, legacy INFO_R001 CASE):
--        start > now -> 'N' (SKIP) | end < now -> 'E' (CUTOFF) | else 'Y'.
--      Times are compared as 6-char HHmmss strings, in Asia/Ho_Chi_Minh —
--      the refactor's documented deviation from legacy's DB-server NOW().
WITH t AS (SELECT to_char(now() AT TIME ZONE 'Asia/Ho_Chi_Minh','HH24MISS') AS hms)
SELECT 'INFO' AS level, i.batch_id, i.out_bank_cd, '-' AS ant_div_cd,
       i.batch_start_tm, i.batch_end_tm, i.ant_where_use_yn, t.hms AS now_ict,
       CASE WHEN i.batch_start_tm > t.hms THEN 'N (SKIP)'
            WHEN i.batch_end_tm   < t.hms THEN 'E (CUTOFF)'
            ELSE 'Y (open)' END AS exe_div
  FROM tb_batch_schedule_info i CROSS JOIN t
UNION ALL
SELECT 'DTL', d.batch_id, d.out_bank_cd, rtrim(d.ant_div_cd),
       d.batch_start_tm, d.batch_end_tm, '-', t.hms,
       CASE WHEN d.batch_start_tm > t.hms THEN 'N (SKIP)'
            WHEN d.batch_end_tm   < t.hms THEN 'E (CUTOFF)'
            ELSE 'Y (open)' END
  FROM tb_batch_schedule_dtl d CROSS JOIN t
 ORDER BY 1 DESC, 2, 3, 4;
--      Interpreting 5.5:
--        * Every INFO row should read 'Y (open)' at any hour — the windows are
--          24h. An INFO row reading N or E is a data error.
--        * DTL rows legitimately read N before their start and E after their
--          end. Run 5.5 inside a channel's window to confirm it opens.


-- #############################################################################
-- SECTION 6 — RE-ALIGN VARIANT (OPT-IN ONLY).  DO NOT RUN BY DEFAULT.
-- #############################################################################
--
-- Sections 1-3 use ON CONFLICT DO NOTHING so that a pre-existing live-JEX row is
-- never overwritten. That is the correct default on any shared DB
-- (project_sys_scr_option_seed_strategy: leave legacy rows untouched).
--
-- If, and only if, the PO/DBA explicitly decides that the windows in this script
-- must REPLACE the values currently in the table, use the DO UPDATE form below
-- instead of the corresponding DO NOTHING clause. It is PK-compatible with both
-- tables. It rewrites windows for the listed keys — on UAT/PROD that changes
-- live JEX batch behaviour, so capture a before-image (section 7) first.
--
--   -- for tb_batch_schedule_info, replacing the section 1 clause:
--   ON CONFLICT (batch_id, out_bank_cd) DO UPDATE SET
--         ant_where_use_yn = EXCLUDED.ant_where_use_yn,
--         batch_start_tm   = EXCLUDED.batch_start_tm,
--         batch_end_tm     = EXCLUDED.batch_end_tm,
--         mod_dttm         = to_char(now(),'YYYYMMDDHH24MISS'),
--         last_edtr        = 'DBA_MANUAL';
--
--   -- for tb_batch_schedule_dtl, replacing the section 2 / 3 clauses.
--   -- NOTE: DTL has no mod_dttm column — do NOT add one.
--   ON CONFLICT (batch_id, out_bank_cd, ant_div_cd) DO UPDATE SET
--         batch_start_tm = EXCLUDED.batch_start_tm,
--         batch_end_tm   = EXCLUDED.batch_end_tm,
--         reg_dttm       = to_char(now(),'YYYYMMDDHH24MISS'),
--         last_edtr      = 'DBA_MANUAL';


-- #############################################################################
-- SECTION 7 — BEFORE-IMAGE + ROLLBACK
-- #############################################################################
--
-- This script performs NO deletes and NO updates in its default form, so the
-- only rows it can add are the ones section 5.2 lists. Rollback is therefore a
-- targeted delete of exactly the keys THIS script inserted.
--
-- 7.1  BEFORE running, capture a before-image so a re-align can be undone.
--      (Run OUTSIDE the transaction. Creating a backup table is DDL — get DBA
--      sign-off, or export to CSV with \copy instead.)
--
--   \copy (SELECT * FROM tb_batch_schedule_info) TO 'tb_batch_schedule_info_before.csv' CSV HEADER
--   \copy (SELECT * FROM tb_batch_schedule_dtl)  TO 'tb_batch_schedule_dtl_before.csv'  CSV HEADER
--
-- 7.2  Rollback. Only run if the seed must be withdrawn. Deletes ONLY the keys
--      stamped by this script (last_edtr = 'DBA_MANUAL'), so a legacy JEX row
--      that DO NOTHING preserved is left alone. Review the SELECT first.
--
--   BEGIN;
--   -- inspect what would go:
--   SELECT * FROM tb_batch_schedule_dtl
--    WHERE last_edtr = 'DBA_MANUAL'
--      AND (batch_id, out_bank_cd, rtrim(ant_div_cd)) IN (
--           ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','NAPAS'),
--           ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','CITAD'),
--           ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','EBANK'),
--           ('FILE_DEPOSIT_TRANSFER_BATCH1','970424','SWIFT'),
--           ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','NAPAS'),
--           ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','CITAD'),
--           ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','VCB'),
--           ('FILE_DEPOSIT_TRANSFER_BATCH1','970457','SWIFT'),
--           ('NAPAS_REAL_TIME_BATCH','970424','NAPAS'),
--           ('NAPAS_REAL_TIME_BATCH','970424','CITAD'),
--           ('NAPAS_REAL_TIME_BATCH','970457','NAPAS'),
--           ('NAPAS_REAL_TIME_BATCH','970457','CITAD'));
--   -- then the same predicate as DELETE, plus:
--   SELECT * FROM tb_batch_schedule_info
--    WHERE last_edtr = 'DBA_MANUAL'
--      AND (batch_id, out_bank_cd) IN (
--           ('NAPAS_REAL_TIME_BATCH','970424'),
--           ('NAPAS_REAL_TIME_BATCH','970457'),
--           ('NAPAS_REAL_TIME_BATCH','970436'));
--   -- COMMIT;  -- or ROLLBACK;
--
--      >>> Deleting the INFO rows returns the gate to NO_CRITERIA, i.e. every
--      >>> dispatch FAILS again. Only roll back while the dispatch flags are OFF.
--
-- 7.3  Preferred rollback when dispatch is already live: do NOT delete. Instead
--      close the window (start '235959', end '000000' -> permanently 'N'/SKIP,
--      which leaves rows WAIT rather than FAIL), or use the section 6 DO UPDATE
--      form to restore the before-image values.
-- =============================================================================

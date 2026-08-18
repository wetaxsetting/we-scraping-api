-- WeCMS Manual Database Change
-- Date: 2026-06-25
-- Source: migrated from Claude Code (AI)/sql/bltb_0001_003_u001_overseas_unable_to_pending_20260625.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: PROD — the original script states DBA executes by hand on PROD
-- Executor: DBA TEAM
-- Rollback: Original script requires an explicit transaction gated on the STEP 0/1/2 SELECT results; revert value is the pre-change APPRVR_STTS captured by those SELECTs
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- PROD DATA FIX (MANUAL, DBA-RUN) — Overseas Payment approval stuck at "Unable"
-- Service        : bltb_0001_003_u001 (Approve/Reject action) + comm_0195_001 (Approval Details)
-- Symptom        : Approver #2 shows "Unable" (APPRVR_STTS='W' / "is not the approval order yet")
--                  after Approver #1 approved. Buttons hidden, so reject is impossible.
-- Root cause      : Approve action did not advance the next approver from 'W' (WAITING) to
--                  'P' (PENDING) because the ERP/Oracle (HS_PROD) status write was interleaved
--                  in the same action and the Oracle connection was down.
-- Goal           : Set the single stuck approver row from 'W' -> 'P' so approver #2 can Reject
--                  in WeCMS (the rest self-heals when the normal Reject action runs).
-- Author         : Claude Code (AI) — investigation only
-- Date           : 2026-06-25
-- IMPORTANT      : DO NOT auto-run. DBA executes by hand on PROD, inside an explicit
--                  transaction, AFTER STEP 0/1/2 SELECTs confirm the state. NOT Flyway.
-- Tables         : EXPT_RESOL_APPRVR_INFM (the only row changed), EXPT_RESOL_MSTR (read only)
-- Status codes   : P=Pending  A=Approve  R=Reject  W=Waiting("Unable")  S=Suspended  C/I=progress
-- Appr type code : 00=Pending  01=Approve  02=Reject  03=Partially-approve
-- =============================================================================

-- -----------------------------------------------------------------------------
-- INPUTS — fill these in from the Approval Box row before running anything.
--   :BIZ_KEY    = tenant/company key of Haengsung Electronics Vietnam
--   :DOC_NO     = document number shown in Approval Box, e.g. 'VO_260624_006'
-- (APPR_SEQ_NO is resolved from DOC_NO in STEP 0.)
-- -----------------------------------------------------------------------------


-- =============================================================================
-- STEP 0 — Identify the approval request by document number (READ-ONLY)
-- Confirm exactly ONE row, APPR_DV='34' (Oversea Payment), and capture APPR_SEQ_NO.
-- =============================================================================
SELECT  m.BIZ_KEY,
        m.APPR_SEQ_NO,
        m.DOC_NO,
        m.APPR_DV,            -- expect '34' = Oversea Payment
        m.APPR_TYPE_CD,       -- expect '00' (Pending) or '03' (Partially) — NOT 01/02
        m.PAY_END_YN,         -- expect 'N' (approval not finalized)
        m.PAY_END_DTM
FROM    EXPT_RESOL_MSTR m
WHERE   m.BIZ_KEY = :BIZ_KEY
  AND   m.DOC_NO  = :DOC_NO          -- e.g. 'VO_260624_006'
LIMIT 50;


-- =============================================================================
-- STEP 1 — Inspect the full approver line for that APPR_SEQ_NO (READ-ONLY)
-- Substitute the APPR_SEQ_NO captured in STEP 0.
-- Expect: line 1 = 'A' (approved, has APPR_DTM); line 2 = 'W' (the stuck row, APPR_DTM null).
-- =============================================================================
SELECT  a.BIZ_KEY,
        a.APPR_SEQ_NO,
        a.APPRVR_LINE_NO,
        a.APPRVR_ID,
        a.APPRVR_NM,
        a.APPRVR_STTS,        -- A on line 1, W on line 2 (the one to fix)
        a.APPR_DTM,
        a.READ_YN,
        a.REG_DTM,
        a.MOD_ID,
        a.MOD_DTM
FROM    EXPT_RESOL_APPRVR_INFM a
WHERE   a.BIZ_KEY     = :BIZ_KEY
  AND   a.APPR_SEQ_NO = :APPR_SEQ_NO
ORDER BY a.APPRVR_LINE_NO ASC;


-- =============================================================================
-- STEP 2 — Pre-flight count + guard checks (READ-ONLY). All must hold before UPDATE.
-- =============================================================================
-- 2a. Status tally (mirrors EXPT_RESOL_APPRVR_INFM_R001_CNT used by the action).
SELECT  count(*)                                          AS tot_cnt,        -- expect 2
        sum(CASE WHEN APPRVR_STTS = 'A' THEN 1 ELSE 0 END) AS tot_appr_cnt,  -- expect 1
        sum(CASE WHEN APPRVR_STTS = 'R' THEN 1 ELSE 0 END) AS tot_rjt_cnt,   -- expect 0
        sum(CASE WHEN APPRVR_STTS = 'W' THEN 1 ELSE 0 END) AS tot_wait_cnt,  -- expect 1 (the stuck one)
        sum(CASE WHEN APPRVR_STTS = 'P' THEN 1 ELSE 0 END) AS tot_pend_cnt   -- expect 0 (nobody can act now)
FROM    EXPT_RESOL_APPRVR_INFM
WHERE   BIZ_KEY = :BIZ_KEY
  AND   APPR_SEQ_NO = :APPR_SEQ_NO;

-- 2b. Confirm the EXACT row to change is the LOWEST-line WAITING row — this is precisely
--     what the application's "advance next approver" step selects (W, order by line, limit 1).
--     This SELECT must return exactly ONE row; that row is the only one the UPDATE touches.
SELECT  BIZ_KEY, APPR_SEQ_NO, APPRVR_LINE_NO, APPRVR_ID, APPRVR_NM, APPRVR_STTS
FROM    EXPT_RESOL_APPRVR_INFM
WHERE   BIZ_KEY = :BIZ_KEY
  AND   APPR_SEQ_NO = :APPR_SEQ_NO
  AND   APPRVR_STTS = 'W'
ORDER BY APPRVR_LINE_NO ASC
LIMIT 1;

-- 2c. (Optional) ERP mapping state for this overseas payment — informational only,
--     tells you whether ERP already has a file row to resubmit (NOT changed by this fix).
--     OVRS_PYMN_SUMR links to the approval via APPR_SEQ_NO.
SELECT  s.BIZ_KEY, s.OVRS_PYMN_NO, s.APPR_SEQ_NO,
        s.ERP_FILE_DATE, s.ERP_FILE_NUM
FROM    OVRS_PYMN_SUMR s
WHERE   s.BIZ_KEY = :BIZ_KEY
  AND   s.APPR_SEQ_NO = :APPR_SEQ_NO
LIMIT 50;


-- =============================================================================
-- STEP 3 — THE FIX (DO NOT RUN until STEP 0/1/2 confirm the expected state).
-- Run inside an explicit transaction; verify the rowcount = 1; then COMMIT.
-- This reproduces exactly what bltb_0001_003_u001_act.jsp line 252 does:
--   rowERAI.setApprvrStts(ApprvrStts.PENDING);  // 'W' -> 'P'
-- Only ONE row is touched. No master/ERP/notification rows are changed here —
-- the normal Reject action will write those when approver #2 rejects.
-- =============================================================================
/*  -- UNCOMMENT TO EXECUTE (DBA, on PROD, after backup of the row)

BEGIN;

    UPDATE EXPT_RESOL_APPRVR_INFM
       SET APPRVR_STTS = 'P',                     -- 'W' (Unable) -> 'P' (Pending)
           MOD_ID      = 'DATAFIX',               -- or the DBA/operator id used for manual fixes
           MOD_DTM     = to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS')  -- legacy DTM14 (UTC)
     WHERE BIZ_KEY     = :BIZ_KEY
       AND APPR_SEQ_NO = :APPR_SEQ_NO
       AND APPRVR_STTS = 'W'                       -- guard: never touch A/R/P/S rows
       AND APPRVR_LINE_NO = (                      -- guard: only the lowest-line WAITING row
             SELECT MIN(APPRVR_LINE_NO)
             FROM   EXPT_RESOL_APPRVR_INFM
             WHERE  BIZ_KEY = :BIZ_KEY
               AND  APPR_SEQ_NO = :APPR_SEQ_NO
               AND  APPRVR_STTS = 'W'
           );

    -- EXPECT: UPDATE 1.  If 0 or >1, ROLLBACK and re-investigate — do NOT proceed.

COMMIT;   -- only after confirming exactly 1 row affected

*/

-- =============================================================================
-- STEP 4 — POST-FIX verification (READ-ONLY). Re-run STEP 1.
-- Expect: line 1 = 'A', line 2 = 'P'. EXPT_RESOL_MSTR unchanged (still PAY_END_YN='N').
-- Then have approver #2 open Approval Details (comm_0195_001) and perform Reject normally.
-- The Reject action will: set master APPR_TYPE_CD='02', PAY_END_YN='Y', write the ERP
-- "REJECTED"=32 update (now that Oracle is back up), and create comment/notification.
-- =============================================================================

-- =============================================================================
-- ROLLBACK NOTE: to undo before approver #2 acts, set the same row APPRVR_STTS back to 'W'
-- (same WHERE keys). After approver #2 has rejected, do NOT roll back via SQL — the master
-- and ERP rows will already reflect the rejection.
-- =============================================================================

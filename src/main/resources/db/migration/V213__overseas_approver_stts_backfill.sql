-- Back-fill EXPT_RESOL_APPRVR_INFM.APPRVR_STTS for already-approved /
-- already-rejected Overseas batches.
--
-- Bug history: OverseasPaymentService.actOnApproval used to flip
-- OVRS_PYMN_SUMR.APPR_STTS to 'C'/'X' without ever touching the
-- per-approver row's APPRVR_STTS. The list query reads "approved fraction"
-- as COUNT(WHERE apprvr_stts='A'), so rows stuck at 'P'/'W' rendered as
-- "Approved 0/1" even though the master said approved.
--
-- The service is fixed going forward (mirrors ApprovalBoxService.applyApprove
-- — caller's row → 'A', countByStatus, promote next waiting, master to
-- 'C' or 'I'). This migration corrects local rows that were approved
-- before the fix landed so the Scheduled Payment list renders the
-- correct fraction without a re-approve.
--
-- Idempotent: only touches rows whose master is in a terminal state
-- AND whose APPRVR_STTS is still 'P'/'W' (i.e. clearly stranded).
-- Re-running after a fresh approve has no effect.

-- Approved masters → flip every still-active approver row to 'A'.
-- Single-approver chains (the common case in the screenshot) settle
-- correctly; multi-approver chains are rare in R&D — accept the
-- coarse-grained fix here since we have no per-row APPR_DTM history
-- to reconstruct the original chain order.
UPDATE EXPT_RESOL_APPRVR_INFM ai
SET    APPRVR_STTS = 'A',
       APPR_DTM    = COALESCE(ai.APPR_DTM, em.MOD_DTM, em.REG_DTM),
       MOD_DTM     = COALESCE(em.MOD_DTM, em.REG_DTM),
       MOD_ID      = COALESCE(em.MOD_ID,  em.REG_ID)
FROM   EXPT_RESOL_MSTR em
JOIN   OVRS_PYMN_SUMR  ops
  ON   ops.BIZ_KEY = em.BIZ_KEY AND ops.APPR_SEQ_NO = em.APPR_SEQ_NO
WHERE  ai.BIZ_KEY = em.BIZ_KEY
  AND  ai.APPR_SEQ_NO = em.APPR_SEQ_NO
  AND  em.APPR_TYPE_CD = '01'        -- master approved
  AND  ops.APPR_STTS   IN ('C','A','01')
  AND  ai.APPRVR_STTS  IN ('P','W'); -- only stranded rows

-- Rejected masters → flip every still-active approver row to 'R'.
UPDATE EXPT_RESOL_APPRVR_INFM ai
SET    APPRVR_STTS = 'R',
       APPR_DTM    = COALESCE(ai.APPR_DTM, em.MOD_DTM, em.REG_DTM),
       MOD_DTM     = COALESCE(em.MOD_DTM, em.REG_DTM),
       MOD_ID      = COALESCE(em.MOD_ID,  em.REG_ID)
FROM   EXPT_RESOL_MSTR em
JOIN   OVRS_PYMN_SUMR  ops
  ON   ops.BIZ_KEY = em.BIZ_KEY AND ops.APPR_SEQ_NO = em.APPR_SEQ_NO
WHERE  ai.BIZ_KEY = em.BIZ_KEY
  AND  ai.APPR_SEQ_NO = em.APPR_SEQ_NO
  AND  em.APPR_TYPE_CD = '02'        -- master rejected
  AND  ops.APPR_STTS   IN ('X','R','02')
  AND  ai.APPRVR_STTS  IN ('P','W'); -- only stranded rows

-- V151: Add APPR_STTS to OVRS_PYMN_SUMR (local-only Phase A bridge).
--
-- DEV's OVRS_PYMN_SUMR has no APPR_STTS column — approval status lives in
-- EXPT_RESOL_MSTR (joined via APPR_SEQ_NO). For Phase A the refactor does
-- not yet write EXPT_RESOL_MSTR rows, so the approval-action endpoint stamps
-- APPR_STTS directly onto OVRS_PYMN_SUMR. The list SQL uses
-- COALESCE(ops.appr_stts, erm.appr_sts) so once EXPT_RESOL_MSTR is ported
-- both columns are read with the real table winning.
--
-- Retire this column when EXPT_RESOL_MSTR writes are ported and tested.

-- Guard: table is created in V156; skip on fresh DBs, apply on DBs that had
-- an earlier draft migration create the table before V156.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ovrs_pymn_sumr') THEN
        ALTER TABLE ovrs_pymn_sumr
            ADD COLUMN IF NOT EXISTS appr_stts VARCHAR(1);
    END IF;
END $$;

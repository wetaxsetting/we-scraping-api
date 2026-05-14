-- ----------------------------------------------------------------------------
-- V120 — Add APPR_STTS column to SCDUL_PYMN_SUMR (Internal Transfer master).
-- ----------------------------------------------------------------------------
-- Carries the per-batch approval outcome — the state set by the
-- Approve / Reject buttons on `<ApprovalDetailsDialog>` (legacy
-- `comm_0195_001` lines 916-919 → `bltb_0001_003_u001`). The value is
-- the legacy `APPRVR_STTS` code applied to the entire batch:
--
--   NULL — initial state (after Request Approval was submitted but
--          before any approver has acted)
--   'P'  — pending (current approver awaiting action)
--   'I'  — in progress (multi-approver chain mid-flight)
--   'C'  — approved (final approver accepted)
--   'X'  — rejected (any approver rejected)
--
-- The refactor's Phase A endpoint (`POST /{id}/approval-action`) stamps
-- 'C' on Approve and 'X' on Reject directly on the master, mirroring the
-- legacy single-step `comm_0193_001` behaviour. The full per-approver
-- chain (EXPT_RESOL_APPRVR_INFM state machine) lands when that table is
-- ported; until then the master-level flag is the single source of
-- truth the frontend list / detail views read.
--
-- Reference: legacy DEV `SCDUL_PYMN_SUMR.APPR_STTS` is `VARCHAR(1)` —
-- this migration mirrors that shape column-for-column per the
-- "Legacy Schema Parity Rule" in CLAUDE.md.
--
-- Idempotent via `ADD COLUMN IF NOT EXISTS`.
-- ----------------------------------------------------------------------------

ALTER TABLE SCDUL_PYMN_SUMR
    ADD COLUMN IF NOT EXISTS APPR_STTS VARCHAR(1);

-- Helper index — list-page status filter (`appr_type` query param maps
-- to APPR_STTS via the page's `APPR_TYPE_OPTIONS` table). Composite
-- with BIZ_KEY because every list query is tenant-scoped.
CREATE INDEX IF NOT EXISTS IX_SCDUL_PYMN_SUMR_BIZ_APPR_STTS
    ON SCDUL_PYMN_SUMR (BIZ_KEY, APPR_STTS);

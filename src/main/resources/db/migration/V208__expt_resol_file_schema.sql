-- ============================================================================
-- V208 — EXPT_RESOL_FILE  (Approval Request attachments)
--
-- Mirrors legacy DEV column-for-column. Surfaces the per-approval-request
-- attachment list on the "Approval Details" popup (currently only the
-- batch's original Excel renders — user-supplied attachments go nowhere).
--
-- Wire path that consumes this table:
--   POST /api/v1/payment/internal-transfer/{id}/approval  (multipart)
--     → service streams each file to
--         <storage-root>/<biz_key>/approval-attachments/<appr_seq_no>/<save_file_nm>
--       and inserts one EXPT_RESOL_FILE row per file
--   GET  /api/v1/payment/internal-transfer/{id}             (detail)
--     → response includes attachments[]   (org_file_nm, file_size, file_id)
--   GET  /api/v1/payment/internal-transfer/{id}/approval/{apprSeqNo}/attachments/{fileId}
--     → streams the binary back
--
-- Legacy PROD table reference (confirmed via DEV pg_constraint, 2026-05-28):
--   PK   (biz_key, appr_seq_no, file_id)
--   FK01 (biz_key, appr_seq_no) → expt_resol_mstr(biz_key, appr_seq_no)
--
-- Constraint scope:
--   • PK carried over verbatim.
--   • FK to EXPT_RESOL_MSTR — deferred. The refactor's `requestApproval`
--     path does not (yet) insert into EXPT_RESOL_MSTR; the master row is
--     stamped directly onto SCDUL_PYMN_SUMR (Internal Transfer flow). The
--     legacy FK would block our insert. Apply once the EXPT_RESOL_MSTR
--     insert lands in a follow-up MR (tracked alongside the Approval
--     Box → Internal Transfer master-row port).
--
-- Helper index added beyond DEV (extra-indexes allowed by the parity rule):
--   ix_expt_resol_file_biz_appr — covers the detail-page join + the
--   download-endpoint lookup at (biz_key, appr_seq_no).
-- ============================================================================

CREATE TABLE IF NOT EXISTS EXPT_RESOL_FILE (
    BIZ_KEY        VARCHAR(20)   NOT NULL,
    APPR_SEQ_NO    VARCHAR(15)   NOT NULL,
    FILE_ID        VARCHAR(100)  NOT NULL,
    ORG_FILE_NM    VARCHAR(500)  NOT NULL,
    SAVE_FILE_NM   VARCHAR(500)  NOT NULL,
    NOTE           VARCHAR(255),
    REG_ID         VARCHAR(100)  NOT NULL,
    REG_DTM        VARCHAR(14)   NOT NULL,
    REG_NM         VARCHAR(100)  NOT NULL,
    FILE_SIZE      VARCHAR(100),
    CONSTRAINT EXPT_RESOL_FILE_PK
        PRIMARY KEY (BIZ_KEY, APPR_SEQ_NO, FILE_ID)
);

CREATE INDEX IF NOT EXISTS ix_expt_resol_file_biz_appr
    ON EXPT_RESOL_FILE (BIZ_KEY, APPR_SEQ_NO);

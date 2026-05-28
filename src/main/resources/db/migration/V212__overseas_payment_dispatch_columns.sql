-- V212: Add dispatch shadow-key + currency columns to OVRS_PYMN_SUMR / OVRS_PYMN_DTLS.
--
-- Phase 1 dispatch wiring for Overseas Payment. The SCDUL_PYMN family
-- (Internal Transfer / Vendor / Expense / Payroll) already stamps these
-- columns onto its summary + detail rows via TransferDispatchExecutor;
-- Overseas needs the same so the Result-detail IDO query can join
-- OVRS_PYMN_SUMR ⋈ TB_PAY_FILE_MST and OVRS_PYMN_DTLS ⋈ TB_PAY_FILE_DATA.
--
-- Mirrors the JEX DEV OVRS_PYMN_SUMR / OVRS_PYMN_DTLS column set:
--   - TRNF_DT       — transfer datetime (legacy Overseas uses TRNF_DT
--                     instead of TRNF_DTM; varchar(14) yyyyMMddHHmmss)
--   - WDRL_CCY_CD   — withdrawal-account currency (distinct from CCY_CD
--                     which is the TRANSFER currency)
--   - PAY_TRAN_DT   — file-batch transfer date (shadow key, varchar(8) yyyyMMdd)
--   - PAY_FILE_SEQ_NO — file-batch sequence (shadow key, joins TB_PAY_FILE_MST)
--   - PAY_TRAN_SEQ  — per-detail TB_PAY_FILE_DATA.SEQ as varchar (legacy
--                     parity: same column the SCDUL_PYMN File path uses)
--
-- All columns are nullable so existing seed rows stay valid.
-- Idempotent via ADD COLUMN IF NOT EXISTS.

-- ─────────────────────────────────────────────────────────────────────────────
-- OVRS_PYMN_SUMR — master shadow keys
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE OVRS_PYMN_SUMR ADD COLUMN IF NOT EXISTS TRNF_DT          VARCHAR(14);
ALTER TABLE OVRS_PYMN_SUMR ADD COLUMN IF NOT EXISTS WDRL_CCY_CD      VARCHAR(3);
ALTER TABLE OVRS_PYMN_SUMR ADD COLUMN IF NOT EXISTS PAY_TRAN_DT      VARCHAR(8);
ALTER TABLE OVRS_PYMN_SUMR ADD COLUMN IF NOT EXISTS PAY_FILE_SEQ_NO  VARCHAR(7);

-- ─────────────────────────────────────────────────────────────────────────────
-- OVRS_PYMN_DTLS — per-line shadow keys
--
-- Mirrors SCDUL_PYMN_SUMR_DETL.PAY_TRAN_DT + PAY_TRAN_SEQ. The
-- PAY_TRAN_SEQ column stores the matching TB_PAY_FILE_DATA.SEQ cast to
-- varchar, joining the per-leg bank-side rows back to the line.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS PAY_TRAN_DT      VARCHAR(8);
ALTER TABLE OVRS_PYMN_DTLS ADD COLUMN IF NOT EXISTS PAY_TRAN_SEQ     VARCHAR(10);

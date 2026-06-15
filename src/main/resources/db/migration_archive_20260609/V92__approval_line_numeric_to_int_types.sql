-- ============================================================================
-- V92__approval_line_numeric_to_int_types.sql
--
-- Settings > Approval Line hotfix (2026-05-13). Same root cause and same
-- remedy as the V35 → V36 hotfix for `SysScrOptionWecms.dispOrd` and the
-- V89 hotfix for `TrcoAddrInfm` / `TrcoAcctInfo.dispOrd`:
--
--   Hibernate 6's `ddl-auto: validate` rejects PostgreSQL NUMERIC(p,0)
--   against the Java `Long` / `Integer` mappings — it expects `BIGINT`
--   for Long and `INTEGER` for Integer. The established refactor
--   convention (see wecms-api/CLAUDE.md "Legacy Schema Parity Rule"
--   exceptions) is to alter the local column to the Hibernate-friendly
--   integer type and accept the small deviation from DEV's NUMERIC.
--
-- Columns altered:
--
--   1) APPR_LINE_INFM.APPR_LINE_NO         NUMERIC(15,0) → BIGINT
--   2) APPR_LINE_INFM_DETL.APPR_LINE_NO    NUMERIC(15,0) → BIGINT
--   3) APPR_LINE_INFM_DETL.APPRVR_DISP_ORD NUMERIC(1,0)  → INTEGER
--
-- `USING <col>::bigint|integer` makes the lossless cast explicit. Safe —
-- the only values in either table on a developer's local DB are the ones
-- the refactor's service writes (well within both ranges). The 15-digit
-- DEV cap fits inside BIGINT (max ≈ 9.2e18 — many orders of magnitude
-- larger). The 1-digit cap on DISP_ORD is replaced by a service-layer
-- guard (`ApprovalLineConstants.MAX_APPROVERS = 5`) + bean validation +
-- the WBE_APPROVAL_LINE_TOO_MANY_APPROVERS error code; the DB-level
-- NUMERIC(1,0) was defence-in-depth only.
--
-- Idempotent — wrapped in DO blocks that check `data_type` /
-- `numeric_precision` before altering. Re-running this migration on an
-- already-converted DB is a no-op.
--
-- See V35 → V36 and V89 for prior art.
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appr_line_infm'
          AND column_name = 'appr_line_no'
          AND data_type = 'numeric'
    ) THEN
        ALTER TABLE APPR_LINE_INFM
            ALTER COLUMN APPR_LINE_NO TYPE BIGINT
            USING APPR_LINE_NO::bigint;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appr_line_infm_detl'
          AND column_name = 'appr_line_no'
          AND data_type = 'numeric'
    ) THEN
        ALTER TABLE APPR_LINE_INFM_DETL
            ALTER COLUMN APPR_LINE_NO TYPE BIGINT
            USING APPR_LINE_NO::bigint;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'appr_line_infm_detl'
          AND column_name = 'apprvr_disp_ord'
          AND data_type = 'numeric'
    ) THEN
        ALTER TABLE APPR_LINE_INFM_DETL
            ALTER COLUMN APPRVR_DISP_ORD TYPE INTEGER
            USING APPRVR_DISP_ORD::integer;
    END IF;
END $$;

-- ============================================================================
-- V36__view_setting_disp_ord_revert_to_integer.sql
--
-- Hotfix for V35: revert SYS_SCR_OPTION_WECMS / USER_SCR_OPTION_WECMS
-- `disp_ord` from NUMERIC(10,0) back to INTEGER NOT NULL DEFAULT 0.
--
-- Why: V35 over-shot strict DEV parity by aligning `disp_ord` to DEV's
-- NUMERIC(10,0). Hibernate's `validate` mode then rejects the column
-- because Java `Long` maps to BIGINT, not NUMERIC:
--
--   Schema-validation: wrong column type encountered in column [disp_ord]
--   in table [sys_scr_option_wecms]; found [numeric (Types#NUMERIC)],
--   but expecting [bigint (Types#BIGINT)]
--
-- The V34 migration had documented `disp_ord` as a refactor-only
-- deviation precisely because aligning to NUMERIC(10) "would force the
-- entity field to BigDecimal/Long with no run-time benefit." V35
-- ignored that — V36 restores the V34 stance.
--
-- Decision (locked 2026-05-02): keep `disp_ord` as INTEGER on local;
-- entity stays `Integer dispOrd`. The 0..99 DISP_ORD domain is well
-- within INTEGER range; functionally equivalent to DEV's NUMERIC(10,0).
-- This deviation is permanent — document in the entity Javadoc.
--
-- All current rows have a small integer-valued disp_ord (0..99 range
-- per the View Setting design), so the NUMERIC→INTEGER cast is safe.
--
-- Idempotent: ALTER COLUMN TYPE is a no-op when the column is already
-- INTEGER; SET DEFAULT is a no-op when the default is already 0.
-- ============================================================================

ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN DISP_ORD TYPE INTEGER USING DISP_ORD::integer;
ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN DISP_ORD SET DEFAULT 0;

ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN DISP_ORD TYPE INTEGER USING DISP_ORD::integer;
ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN DISP_ORD SET DEFAULT 0;

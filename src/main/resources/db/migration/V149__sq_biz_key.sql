-- ============================================================================
-- V149__sq_biz_key.sql
--
-- Legacy JEX sequence ported from UAT (source of truth). Schema prefix
-- `wecmsuat_real.` stripped; all sequence options preserved verbatim
-- (INCREMENT / MINVALUE / MAXVALUE / START / CACHE / NO CYCLE). No COMMENT in
-- the source DDL.
--
-- Usage status (verified 2026-06-09): NOT referenced by any retained
-- wb_get_next_val branch (V140's busi_infm/biz_key branch uses the
-- {ntnl}_{chnl}_{NNNNNN} MAX+1 FORMULA, NOT this sequence; the only UAT branch
-- that draws from sq_biz_key is cstm_ldgr_infm/biz_key, which is NOT ported
-- and whose table is not in V1–V139). NOT referenced by any Spring Java
-- (BusinessKeyGeneratorService is inert and routes through the formula branch).
-- NOT referenced by any native SQL. Reference-only today.
--
-- ⚠️ APPLY CONFLICT (shared DB, verified 2026-06-09): sq_biz_key ALREADY
-- EXISTS on 172.168.5.172/wecmsdevdb (created by archived V11 as
-- `START WITH 1000`). This verbatim DDL uses `START 1`. Consequences:
--   * A plain CREATE will FAIL on the shared DB ("relation already exists").
--   * The START divergence (1 vs 1000) is immaterial today (last_value is
--     NULL — nothing has ever drawn from it), but the two definitions differ.
-- Resolution (START value / IF NOT EXISTS / drop+recreate) is left to an
-- explicit decision — options NOT changed here per the per-sequence rules.
-- ============================================================================

-- sq_biz_key definition

-- DROP SEQUENCE sq_biz_key;

CREATE SEQUENCE sq_biz_key
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;

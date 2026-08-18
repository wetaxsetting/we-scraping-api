-- WeCMS Manual Database Change
-- Date: 2026-06-12
-- Source: migrated from Claude Code (AI)/sql/tb_van_api_info_seed_20260612.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: Fresh refactor / LOCAL DB. The original script states the real rows ALREADY EXIST on the live JEX DB and must NOT be overwritten
-- Executor: DBA TEAM
-- Rollback: Reversible by deleting the seeded row for the VAN_ORG_CD; legacy rows are protected by ON CONFLICT DO NOTHING
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
-- TEMPLATE — this script carries placeholders that a DBA must fill in before execution. Do not run it as-is.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- TB_VAN_API_INFO — per-VAN AES/HMAC API key seed (TEMPLATE — DBA-run, manual)
-- Service: firm-banking bulk dispatch (00301/00302/01301/01302) + 203 result-check
-- Created: 2026-06-12
-- =============================================================================
--
-- PURPOSE
--   VanApiKeyService.getApiKey(van_org_cd) resolves the per-VAN secret used by
--   EfsCryptoService to build/verify EV (AES-256-CBC) + VV (HMAC-SHA256). Until a
--   row exists for a given VAN_ORG_CD, every EFS leg for that org throws (B2).
--
-- WHO RUNS THIS / WHERE
--   * Executed BY HAND by a DBA — NOT by the application, NOT by Flyway
--     (Flyway is disabled; ddl-auto=validate). The table itself is created by
--     V111__tb_van_api_info.sql.
--   * LIVE JEX DB (the UAT/PROD refactor target): the real rows ALREADY EXIST on
--     the live database — DO NOT run the UPDATE/overwrite here; the INSERT below
--     is ON CONFLICT DO NOTHING precisely so legacy rows stay untouched.
--   * FRESH refactor / LOCAL DB: run this after replacing the placeholders with the
--     real keys obtained out-of-band (secure channel). NEVER commit real keys.
--
-- REQUIRED VAN_ORG_CD VALUES (verified against JEX DEV 2026-06-12)
--   GV00000001, GV00000006, GV00000007
--   (must match TB_FIRM_CONF.VAN_ORG_CD for active tenants AND the envelope `ID`
--    the gateway sends. Add any additional active-tenant VAN_ORG_CD the same way.)
--
-- SECURITY
--   The values below are PLACEHOLDERS. Replace '<REAL_API_KEY_*>' with the real
--   per-VAN key from the secure channel immediately before execution, in a session
--   that is not logged to shell history / source control. Do not paste real keys
--   into this file in the repo.
--
-- RUN ORDER / DEPENDENCIES
--   1. V111__tb_van_api_info.sql (table) must already be applied.
--   2. TB_FIRM_CONF rows for the tenants must exist (VAN_ORG_CD source).
--   3. Then run this seed. Idempotent: ON CONFLICT (van_org_cd) DO NOTHING.
-- =============================================================================

INSERT INTO tb_van_api_info (van_org_cd, api_key) VALUES
    ('GV00000001', '<REAL_API_KEY_GV00000001>'),
    ('GV00000006', '<REAL_API_KEY_GV00000006>'),
    ('GV00000007', '<REAL_API_KEY_GV00000007>')
ON CONFLICT (van_org_cd) DO NOTHING;

-- Verify (codes only — never SELECT the api_key into a shared log):
--   SELECT van_org_cd FROM tb_van_api_info ORDER BY van_org_cd;

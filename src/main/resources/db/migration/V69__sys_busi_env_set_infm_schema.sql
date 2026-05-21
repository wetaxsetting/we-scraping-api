-- ============================================================================
-- V69__sys_busi_env_set_infm_schema.sql
--
-- Creates the two PROD-active env-settings tables and seeds the SYS
-- defaults that the refactor consumes today. Both tables exist in the
-- "PROD Active Table List" (see wecms-api/CLAUDE.md) so they are
-- in scope for the refactor.
--
-- Why now: Firm Banking Settings panel exposes two automation toggles
-- (Auto Match `00079`, Auto Transfer `00078`) that legacy stores in
-- BUSI_ENV_SET_INFM, seeded from SYS_ENV_SET_INFM by the legacy
-- DB function `wb_init_busi_data` at tenant-creation time. The
-- refactor implements the SYS->BUSI copy in Java
-- (TenantEnvironmentSettingsBootstrapService); this migration provides
-- the underlying tables + the SYS rows the copy reads from.
--
-- Schema parity with DEV (queried 2026-05-10 via postgres MCP):
--
--   SYS_ENV_SET_INFM
--     env_set_inf  VARCHAR(5)    NOT NULL  PK
--     env_set_cont VARCHAR(1000) NULL
--
--   BUSI_ENV_SET_INFM
--     biz_key      VARCHAR(20)   NOT NULL  PK part 1
--     env_set_inf  VARCHAR(5)    NOT NULL  PK part 2
--     env_set_cont VARCHAR(1000) NULL
--     mod_id       VARCHAR(100)  NULL
--     mod_dtm      VARCHAR(14)   NULL      yyyyMMddHHmmss UTC
--     FK busi_env_set_infm_fk01 -> busi_infm(biz_key)
--     FK busi_env_set_infm_fk02 -> sys_env_set_infm(env_set_inf)
--
-- Note: column is MOD_ID (not MOD_USER) per DEV. No REG_ID/REG_DTM
-- columns on either table - mod_id/mod_dtm carry the only audit
-- footprint the legacy schema defines.
--
-- Seed scope: 10 SYS rows. The two new codes Firm Banking Settings
-- needs (00078, 00079) plus the 8 codes V25's wb_get_next_val
-- conditionally references (00055-00058 doc-id prefix/format/seq;
-- 00070-00073 doc-no toggle/prefix/format/seq). Real DEV defaults
-- copied verbatim. Adding more SYS codes later is just another
-- Flyway migration -- the bootstrap service reads whatever is in
-- the table at run time. Within the 30-row data-migration cap
-- documented in CLAUDE.md.
-- ============================================================================

CREATE TABLE IF NOT EXISTS sys_env_set_infm (
    env_set_inf  VARCHAR(5)    NOT NULL,
    env_set_cont VARCHAR(1000),
    CONSTRAINT sys_env_set_infm_pk PRIMARY KEY (env_set_inf)
);

CREATE TABLE IF NOT EXISTS busi_env_set_infm (
    biz_key      VARCHAR(20)   NOT NULL,
    env_set_inf  VARCHAR(5)    NOT NULL,
    env_set_cont VARCHAR(1000),
    mod_id       VARCHAR(100),
    mod_dtm      VARCHAR(14),
    CONSTRAINT busi_env_set_infm_pk PRIMARY KEY (biz_key, env_set_inf),
    CONSTRAINT busi_env_set_infm_fk01 FOREIGN KEY (biz_key)
        REFERENCES busi_infm (biz_key),
    CONSTRAINT busi_env_set_infm_fk02 FOREIGN KEY (env_set_inf)
        REFERENCES sys_env_set_infm (env_set_inf)
);

-- SYS seed: defaults verbatim from wecmsvndev.sys_env_set_infm
-- (queried 2026-05-10). Idempotent.
INSERT INTO sys_env_set_infm (env_set_inf, env_set_cont) VALUES
    ('00055', 'N'),
    ('00056', 'IV-'),
    ('00057', 'YYYY'),
    ('00058', '0001'),
    ('00070', 'N'),
    ('00071', 'VO'),
    ('00072', 'YYMMDD'),
    ('00073', '001'),
    ('00078', 'N'),
    ('00079', 'N')
ON CONFLICT (env_set_inf) DO NOTHING;

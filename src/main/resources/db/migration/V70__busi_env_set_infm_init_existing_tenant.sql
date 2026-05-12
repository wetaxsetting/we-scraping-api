-- ============================================================================
-- V70__busi_env_set_infm_init_existing_tenant.sql
--
-- One-shot backfill that mirrors `wb_init_busi_data`'s env-set leg
-- for the only tenant the refactor scope contains today
-- (VN_VNM0004_000001, pinned via wecms.tenant.biz-key).
--
-- Why this is a Flyway data migration and not a runtime bootstrap
-- call: CompanyBootstrapService only fires for NEW tenants. The
-- single tenant in scope was created before V69 existed, so the
-- TenantEnvironmentSettingsBootstrapService never ran for it.
-- This migration runs the same SYS->BUSI copy once so the toggles
-- panel finds rows without waiting for a hypothetical re-bootstrap.
--
-- Idempotent: WHERE NOT EXISTS guard mirrors the legacy
-- wb_init_busi_data SQL verbatim, so re-runs after a developer
-- has flipped a toggle preserve the customised value (per the
-- "Preserve existing BUSI_ENV_SET_INFM values if already
-- customized by tenant" requirement).
--
-- mod_id = 'SYSTEM_BOOTSTRAP' matches CompanyBootstrapService's
-- audit footprint for system-written rows. mod_dtm is the
-- migration-time UTC timestamp formatted as legacy yyyyMMddHHmmss
-- (VARCHAR(14)) - matches AuditLogService.REG_DTM_FORMAT and the
-- runtime path's TenantEnvironmentSettingsBootstrapService.
-- ============================================================================

INSERT INTO busi_env_set_infm (biz_key, env_set_inf, env_set_cont, mod_id, mod_dtm)
SELECT
    'VN_VNM0004_000001',
    sesi.env_set_inf,
    sesi.env_set_cont,
    'SYSTEM_BOOTSTRAP',
    to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS')
FROM sys_env_set_infm sesi
WHERE NOT EXISTS (
    SELECT 1
      FROM busi_env_set_infm besi
     WHERE besi.biz_key     = 'VN_VNM0004_000001'
       AND besi.env_set_inf = sesi.env_set_inf
);

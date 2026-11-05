-- WeCMS Manual Database Change
-- Date: 2026-11-02
-- Related record: docs/features/addon-service/weinvoice/weinvoice-api-implementation-2026-11-02.md
-- Type: DDL (GRANT only)
-- Execution: MANUAL, with psql variables:
--   psql -v engine_schema=public -v app_role=weinvoice_app -v engine_role=weinvoice_engine \
--        -f grant-weinvoice-schema-access-2026-11-02.sql <weinvoice database>
--   engine_schema = WECMS_WEINVOICE_ENGINE_SCHEMA (where the engine tables are)
--   app_role      = the login of WECMS_WEINVOICE_DB_USERNAME (this backend)
--   engine_role   = the login the we-scraping engine uses (DATABASE_URL of the engine)
-- Target environments: the WeInvoice database (the we-scraping engine database) of each environment
--                      where WeInvoice is enabled. NOT the WeCMS database.
-- Executor: DBA TEAM (role / privilege change)
-- Transaction requirement: one transaction (BEGIN ... COMMIT below).
-- Rollback: REVOKE the same privileges (section 4). No data is touched.
--
-- Skip this script when the backend and the engine log in as the same role that owns both
-- schemas (single-role local setup): that role already holds every privilege granted here.
--
-- Run AFTER src/main/resources/db/weinvoice/V1__winv_schema.sql. Replace `weinvoice` if the
-- environment uses another WECMS_WEINVOICE_APP_SCHEMA.
--
-- Least privilege:
--   * backend: reads the engine tables (it never writes them) and owns the weinvoice tables;
--   * engine : reads the weinvoice tables and writes only what it reports back — request
--              progress, upload outcomes, connection status after a login test, sync coverage.

-- 1. PRE-CHECK (expected before the change: f, f, f, f)
SELECT has_schema_privilege(:'app_role', :'engine_schema', 'USAGE')                       AS app_reads_engine_schema,
       has_table_privilege(:'app_role', format('%I.invoices', :'engine_schema'), 'SELECT') AS app_reads_invoices,
       has_table_privilege(:'engine_role', 'weinvoice.winv_engn_rqst', 'UPDATE')            AS engine_updates_requests,
       has_table_privilege(:'engine_role', 'weinvoice.winv_sync_cvrg', 'INSERT')            AS engine_writes_coverage;

-- 2. CHANGE
BEGIN;

-- Backend -> engine tables: read only, including tables the engine creates later.
GRANT USAGE ON SCHEMA :"engine_schema" TO :"app_role";
GRANT SELECT ON ALL TABLES IN SCHEMA :"engine_schema" TO :"app_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"engine_role" IN SCHEMA :"engine_schema" GRANT SELECT ON TABLES TO :"app_role";

-- Backend -> weinvoice tables: full use.
GRANT USAGE ON SCHEMA weinvoice TO :"app_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA weinvoice TO :"app_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA weinvoice TO :"app_role";

-- Engine -> weinvoice tables: read everything, write its reports only.
GRANT USAGE ON SCHEMA weinvoice TO :"engine_role";
GRANT SELECT ON ALL TABLES IN SCHEMA weinvoice TO :"engine_role";
GRANT UPDATE ON weinvoice.winv_engn_rqst, weinvoice.winv_upld_file, weinvoice.winv_cmpn_infm TO :"engine_role";
GRANT INSERT, UPDATE ON weinvoice.winv_sync_cvrg TO :"engine_role";

COMMIT;

-- 3. POST-CHECK (expected after the change: t, t, t, t, and f for the last column)
SELECT has_schema_privilege(:'app_role', :'engine_schema', 'USAGE')                       AS app_reads_engine_schema,
       has_table_privilege(:'app_role', format('%I.invoices', :'engine_schema'), 'SELECT') AS app_reads_invoices,
       has_table_privilege(:'engine_role', 'weinvoice.winv_engn_rqst', 'UPDATE')            AS engine_updates_requests,
       has_table_privilege(:'engine_role', 'weinvoice.winv_sync_cvrg', 'INSERT')            AS engine_writes_coverage,
       has_table_privilege(:'app_role', format('%I.invoices', :'engine_schema'), 'UPDATE') AS app_writes_invoices_must_be_false;

-- 4. ROLLBACK / RECOVERY NOTES
-- Privileges only; revoking them makes WeInvoice answer WBE_DB on every call:
--   REVOKE INSERT, UPDATE ON weinvoice.winv_sync_cvrg FROM :"engine_role";
--   REVOKE UPDATE ON weinvoice.winv_engn_rqst, weinvoice.winv_upld_file, weinvoice.winv_cmpn_infm FROM :"engine_role";
--   REVOKE SELECT ON ALL TABLES IN SCHEMA weinvoice FROM :"engine_role";
--   REVOKE USAGE ON SCHEMA weinvoice FROM :"engine_role";
--   REVOKE USAGE, SELECT ON ALL SEQUENCES IN SCHEMA weinvoice FROM :"app_role";
--   REVOKE SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA weinvoice FROM :"app_role";
--   REVOKE USAGE ON SCHEMA weinvoice FROM :"app_role";
--   ALTER DEFAULT PRIVILEGES FOR ROLE :"engine_role" IN SCHEMA :"engine_schema" REVOKE SELECT ON TABLES FROM :"app_role";
--   REVOKE SELECT ON ALL TABLES IN SCHEMA :"engine_schema" FROM :"app_role";
--   REVOKE USAGE ON SCHEMA :"engine_schema" FROM :"app_role";

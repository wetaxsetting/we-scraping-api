-- WeCMS Manual Database Change
-- Date: 2026-04-01
-- Source: migrated from Claude Code (AI)/sql/setup_readonly_user_20260401.sql
-- Related record: .claude/rules/database.md (workspace rules governing this read-only access)
-- Type: DDL (role + grants)
-- Execution: MANUAL
-- Target environments: DEV database ONLY, run as superuser/admin (per the original script header)
-- Executor: DBA TEAM
-- Rollback: Reversible with REVOKE + DROP ROLE claude_readonly
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================
-- WeCMS PostgreSQL Read-Only User for Claude Code AI
-- Run this script on DEV database ONLY as superuser/admin
-- Date: 2026-04-01
-- ============================================================

-- ============================================================
-- STEP 1: Create the read-only role
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'claude_readonly') THEN
        CREATE ROLE claude_readonly WITH
            LOGIN
            PASSWORD 'CHANGE_ME_TO_SECURE_PASSWORD'
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            NOREPLICATION
            CONNECTION LIMIT 3;             -- Max 3 concurrent connections
        RAISE NOTICE 'Role claude_readonly created.';
    ELSE
        RAISE NOTICE 'Role claude_readonly already exists.';
    END IF;
END
$$;

-- ============================================================
-- STEP 2: Grant CONNECT to DEV database only
-- ============================================================
-- DEV database: 10.254.242.254:54311/wecmsvndevdb
GRANT CONNECT ON DATABASE wecmsvndevdb TO claude_readonly;

-- ============================================================
-- STEP 3: Grant USAGE on schemas
-- ============================================================
GRANT USAGE ON SCHEMA public TO claude_readonly;
GRANT USAGE ON SCHEMA information_schema TO claude_readonly;

-- ============================================================
-- STEP 4: Grant SELECT on all existing tables and views
-- ============================================================
GRANT SELECT ON ALL TABLES IN SCHEMA public TO claude_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA information_schema TO claude_readonly;

-- ============================================================
-- STEP 5: Grant SELECT on all future tables automatically
-- ============================================================
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT ON TABLES TO claude_readonly;

-- ============================================================
-- STEP 6: Grant SELECT on sequences (read current values only)
-- ============================================================
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO claude_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT ON SEQUENCES TO claude_readonly;

-- ============================================================
-- STEP 7: Allow reading pg_catalog (system catalog)
-- ============================================================
-- pg_catalog is readable by default in PostgreSQL, but being explicit:
GRANT USAGE ON SCHEMA pg_catalog TO claude_readonly;

-- ============================================================
-- STEP 8: Explicitly REVOKE all write operations
-- ============================================================
REVOKE INSERT, UPDATE, DELETE, TRUNCATE
    ON ALL TABLES IN SCHEMA public
    FROM claude_readonly;

REVOKE CREATE ON SCHEMA public FROM claude_readonly;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE INSERT, UPDATE, DELETE, TRUNCATE
    ON TABLES FROM claude_readonly;

-- ============================================================
-- STEP 9: Prevent dangerous operations at role level
-- ============================================================
-- Already set by NOSUPERUSER, NOCREATEDB, NOCREATEROLE above
-- Additionally, ensure no GRANT privilege:
REVOKE ALL ON DATABASE wecmsvndevdb FROM claude_readonly;
GRANT CONNECT ON DATABASE wecmsvndevdb TO claude_readonly;

-- ============================================================
-- STEP 10: Set statement timeout (prevent long-running queries)
-- ============================================================
ALTER ROLE claude_readonly SET statement_timeout = '30s';
ALTER ROLE claude_readonly SET lock_timeout = '5s';
ALTER ROLE claude_readonly SET idle_in_transaction_session_timeout = '60s';

-- ============================================================
-- VERIFICATION QUERIES
-- ============================================================

-- 1. Check role attributes
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolconnlimit
FROM pg_roles
WHERE rolname = 'claude_readonly';

-- 2. Check granted privileges (should show SELECT only)
SELECT grantee, table_schema, table_name, privilege_type
FROM information_schema.role_table_grants
WHERE grantee = 'claude_readonly'
  AND privilege_type != 'SELECT'
ORDER BY table_schema, table_name;
-- ^ This query should return ZERO rows (no non-SELECT privileges)

-- 3. Check schema privileges
SELECT nspname, has_schema_privilege('claude_readonly', nspname, 'USAGE') AS has_usage,
       has_schema_privilege('claude_readonly', nspname, 'CREATE') AS has_create
FROM pg_namespace
WHERE nspname IN ('public', 'information_schema', 'pg_catalog');
-- ^ CREATE should be false for all schemas

-- ============================================================
-- TEST QUERIES (run as claude_readonly to verify)
-- ============================================================

-- SHOULD WORK:
-- SELECT * FROM information_schema.tables WHERE table_schema = 'public' LIMIT 10;
-- SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'SCDUL_PYMN_SUMR' LIMIT 50;
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'SCDUL_PYMN_SUMR';
-- SELECT conname, contype, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'SCDUL_PYMN_SUMR'::regclass;
-- SELECT tgname, tgtype, tgisinternal FROM pg_trigger WHERE tgrelid = 'SCDUL_PYMN_SUMR'::regclass;
-- SELECT proname, prosrc FROM pg_proc WHERE pronamespace = 'public'::regnamespace LIMIT 10;
-- SELECT * FROM SCDUL_PYMN_SUMR LIMIT 5;

-- SHOULD FAIL:
-- INSERT INTO any_table VALUES (...);       → Permission denied
-- UPDATE any_table SET col = 'val';         → Permission denied
-- DELETE FROM any_table;                    → Permission denied
-- TRUNCATE any_table;                       → Permission denied
-- ALTER TABLE any_table ADD COLUMN x TEXT;  → Permission denied
-- DROP TABLE any_table;                     → Permission denied
-- CREATE TABLE test (id INT);              → Permission denied
-- GRANT SELECT ON any_table TO PUBLIC;      → Permission denied

-- ============================================================
-- TO REMOVE THIS USER LATER:
-- REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM claude_readonly;
-- REVOKE ALL PRIVILEGES ON DATABASE wecmsvndevdb FROM claude_readonly;
-- DROP ROLE claude_readonly;
-- ============================================================

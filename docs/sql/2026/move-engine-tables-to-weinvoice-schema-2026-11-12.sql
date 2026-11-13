-- WeCMS Manual Database Change
-- Date: 2026-11-12
-- Related record: ../../issues/weinvoice-engine-tables-into-weinvoice-schema-2026-11-12.md
-- Type: DDL
-- Execution: MANUAL — psql / DBeaver connected to database wetax_scraping AS ROLE wetax_scraping (owner)
-- Target environments: engine DB wetax_scraping (shared by every engine machine and the WeInvoice backend)
-- Executor: DEV TEAM (developer)
-- Transaction requirement: section 2 in ONE transaction; every engine process and the backend STOPPED first
-- Rollback: section 4 (non-destructive: moves the tables back, resets search_path)
--
-- Moves the 17 engine tables public -> weinvoice (indexes, constraints, owned sequences and grants move with
-- them; no data is copied) and makes weinvoice the database default search_path so engine code, which names
-- tables unqualified, keeps working on every machine without an .env change.

-- ============================================================================================================
-- 1. PRE-CHECK  (all four must match before section 2)
-- ============================================================================================================

-- 1a. Expect: wetax_scraping | wetax_scraping
SELECT current_user, current_database();

-- 1b. Expect: 0 rows. Any row = an engine process / backend / tool still connected -> stop it first.
SELECT pid, usename, application_name, client_addr, state, backend_start
  FROM pg_stat_activity
 WHERE datname = current_database() AND pid <> pg_backend_pid();

-- 1c. Expect: 17 (the engine tables are in public) and 0 (none already in weinvoice)
SELECT count(*) FILTER (WHERE schemaname = 'public')    AS in_public,
       count(*) FILTER (WHERE schemaname = 'weinvoice') AS in_weinvoice
  FROM pg_tables
 WHERE tablename IN ('companies', 'einvoice_providers', 'email_files', 'email_links', 'email_messages',
                     'gdt_login_blocks', 'gdt_sessions', 'invoice_details', 'invoice_pdf', 'invoice_xml', 'invoices',
                     'job_run_companies', 'job_runs', 'partners', 'sync_cursors', 'task_attempts', 'tasks');

-- 1d. Expect: 0 rows — no table / index / sequence name of the moving set already exists in weinvoice
WITH moving AS (
    SELECT c.oid, c.relname
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public' AND c.relkind = 'r'
       AND c.relname IN ('companies', 'einvoice_providers', 'email_files', 'email_links', 'email_messages',
                         'gdt_login_blocks', 'gdt_sessions', 'invoice_details', 'invoice_pdf', 'invoice_xml',
                         'invoices', 'job_run_companies', 'job_runs', 'partners', 'sync_cursors', 'task_attempts',
                         'tasks')
), names AS (
    SELECT relname FROM moving
    UNION SELECT i.relname FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid
           WHERE x.indrelid IN (SELECT oid FROM moving)
    UNION SELECT s.relname FROM pg_depend d JOIN pg_class s ON s.oid = d.objid AND s.relkind = 'S'
           WHERE d.refobjid IN (SELECT oid FROM moving) AND d.deptype IN ('a', 'i')
)
SELECT relname FROM names
 WHERE relname IN (SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'weinvoice');

-- 1e. Row counts before (keep the output, compare with 3c)
SELECT 'invoices' t, count(*) FROM public.invoices
UNION ALL SELECT 'invoice_xml', count(*) FROM public.invoice_xml
UNION ALL SELECT 'invoice_pdf', count(*) FROM public.invoice_pdf
UNION ALL SELECT 'invoice_details', count(*) FROM public.invoice_details
UNION ALL SELECT 'tasks', count(*) FROM public.tasks
UNION ALL SELECT 'companies', count(*) FROM public.companies;

-- ============================================================================================================
-- 2. CHANGE  (one transaction; fails fast instead of waiting if a table is still locked)
-- ============================================================================================================
BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE public.companies          SET SCHEMA weinvoice;
ALTER TABLE public.einvoice_providers SET SCHEMA weinvoice;
ALTER TABLE public.email_files        SET SCHEMA weinvoice;
ALTER TABLE public.email_links        SET SCHEMA weinvoice;
ALTER TABLE public.email_messages     SET SCHEMA weinvoice;
ALTER TABLE public.gdt_login_blocks   SET SCHEMA weinvoice;
ALTER TABLE public.gdt_sessions       SET SCHEMA weinvoice;
ALTER TABLE public.invoice_details    SET SCHEMA weinvoice;
ALTER TABLE public.invoice_pdf        SET SCHEMA weinvoice;
ALTER TABLE public.invoice_xml        SET SCHEMA weinvoice;
ALTER TABLE public.invoices           SET SCHEMA weinvoice;
ALTER TABLE public.job_run_companies  SET SCHEMA weinvoice;
ALTER TABLE public.job_runs           SET SCHEMA weinvoice;
ALTER TABLE public.partners           SET SCHEMA weinvoice;
ALTER TABLE public.sync_cursors       SET SCHEMA weinvoice;
ALTER TABLE public.task_attempts      SET SCHEMA weinvoice;
ALTER TABLE public.tasks              SET SCHEMA weinvoice;

-- Default for every NEW connection to this database (the engine names tables unqualified).
ALTER DATABASE wetax_scraping SET search_path = weinvoice;

COMMIT;

-- ============================================================================================================
-- 3. POST-CHECK  (DISCONNECT and run from a NEW connection — the old one keeps the old search_path)
-- ============================================================================================================

-- 3a. Expect: weinvoice
SHOW search_path;

-- 3b. Expect: in_public 0, in_weinvoice 28; and sequences_left_in_public 0
SELECT count(*) FILTER (WHERE schemaname = 'public')    AS in_public,
       count(*) FILTER (WHERE schemaname = 'weinvoice') AS in_weinvoice
  FROM pg_tables WHERE schemaname IN ('public', 'weinvoice');
SELECT count(*) AS sequences_left_in_public FROM information_schema.sequences WHERE sequence_schema = 'public';

-- 3c. Unqualified names resolve to weinvoice; counts equal 1e
SELECT to_regclass('invoices')::text AS resolves_to;          -- expect weinvoice.invoices
SELECT 'invoices' t, count(*) FROM invoices
UNION ALL SELECT 'invoice_xml', count(*) FROM invoice_xml
UNION ALL SELECT 'invoice_pdf', count(*) FROM invoice_pdf
UNION ALL SELECT 'invoice_details', count(*) FROM invoice_details
UNION ALL SELECT 'tasks', count(*) FROM tasks
UNION ALL SELECT 'companies', count(*) FROM companies;

-- 3d. Sequences still feed the moved tables (expect weinvoice.invoices_id_seq etc.)
SELECT pg_get_serial_sequence('weinvoice.invoices', 'id') AS invoices_seq,
       pg_get_serial_sequence('weinvoice.tasks', 'id')    AS tasks_seq;

-- ============================================================================================================
-- 4. ROLLBACK / RECOVERY  (only if needed; stop every engine process + backend first, same as section 2)
-- ============================================================================================================
-- BEGIN;
-- SET LOCAL lock_timeout = '10s';
-- ALTER TABLE weinvoice.companies          SET SCHEMA public;
-- ALTER TABLE weinvoice.einvoice_providers SET SCHEMA public;
-- ALTER TABLE weinvoice.email_files        SET SCHEMA public;
-- ALTER TABLE weinvoice.email_links        SET SCHEMA public;
-- ALTER TABLE weinvoice.email_messages     SET SCHEMA public;
-- ALTER TABLE weinvoice.gdt_login_blocks   SET SCHEMA public;
-- ALTER TABLE weinvoice.gdt_sessions       SET SCHEMA public;
-- ALTER TABLE weinvoice.invoice_details    SET SCHEMA public;
-- ALTER TABLE weinvoice.invoice_pdf        SET SCHEMA public;
-- ALTER TABLE weinvoice.invoice_xml        SET SCHEMA public;
-- ALTER TABLE weinvoice.invoices           SET SCHEMA public;
-- ALTER TABLE weinvoice.job_run_companies  SET SCHEMA public;
-- ALTER TABLE weinvoice.job_runs           SET SCHEMA public;
-- ALTER TABLE weinvoice.partners           SET SCHEMA public;
-- ALTER TABLE weinvoice.sync_cursors       SET SCHEMA public;
-- ALTER TABLE weinvoice.task_attempts      SET SCHEMA public;
-- ALTER TABLE weinvoice.tasks              SET SCHEMA public;
-- ALTER DATABASE wetax_scraping RESET search_path;
-- COMMIT;
-- Then set WECMS_WEINVOICE_ENGINE_SCHEMA=public on each api host again.

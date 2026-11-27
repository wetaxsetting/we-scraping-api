-- WeCMS Manual Database Change
-- Date: 2026-11-12
-- Related record: docs/features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md
-- Type: DDL (three new indexes on existing engine tables; no data change)
-- Execution: MANUAL — OPTIONAL. The notification endpoints work without these indexes; they only make the
--            30-day time filters cheap once the engine tables grow.
-- Target environments: the WeInvoice database (the we-scraping engine database) of every environment
--                      where WeInvoice is enabled (LOCAL / SIT / UAT / PROD). NOT the WeCMS database.
-- Executor: DBA OR DEV TEAM — environment dependent
-- Transaction requirement: NONE — and NOT ALLOWED. CREATE INDEX CONCURRENTLY cannot run inside a
--                          transaction block: run each statement on its own (psql autocommit; do not wrap
--                          in BEGIN/COMMIT, do not run with `psql -1` / --single-transaction, and in a GUI
--                          client switch auto-commit ON). CONCURRENTLY does not block the engine's writes.
-- Rollback: section 4 (DROP INDEX CONCURRENTLY — an index holds no data, so dropping it loses nothing).
--
-- Why: GET /api/v1/weinvoice/notifications and /notifications/unread-count (polled every 60 s per open tab)
-- aggregate the last 30 days of
--   invoices.first_seen_at  per company_mst   (new invoices from GDT)
--   invoice_xml.fetched_at                    (XML downloaded)
--   invoice_pdf.fetched_at                    (original PDF obtained)
-- The engine schema (we-scraping db/schema.sql) has no index on any of these columns, so each call scans.
--
-- Schema: the engine tables live in `weinvoice` since 2026-11-12
-- (docs/sql/2026/move-engine-tables-to-weinvoice-schema-2026-11-12.sql). If an environment still keeps them
-- elsewhere (WECMS_WEINVOICE_ENGINE_SCHEMA), replace `weinvoice` below.
-- Index build time grows with table size; on a large invoices table expect minutes, not seconds.
-- If a CONCURRENTLY build is interrupted it leaves an INVALID index: the post-check shows indisvalid = f;
-- drop it (section 4) and run the statement again.

-- 1. PRE-CHECK
--    Expected before the change: 0 rows (none of the three indexes exists yet).
SELECT schemaname, tablename, indexname
  FROM pg_indexes
 WHERE schemaname = 'weinvoice'
   AND indexname IN ('invoices_company_first_seen', 'invoice_xml_fetched_at', 'invoice_pdf_fetched_at');

--    Context (row counts, to estimate the build time; nothing is compared against them):
SELECT 'invoices' AS tbl, count(*) FROM weinvoice.invoices
UNION ALL SELECT 'invoice_xml', count(*) FROM weinvoice.invoice_xml
UNION ALL SELECT 'invoice_pdf', count(*) FROM weinvoice.invoice_pdf;

-- 2. CHANGE — run each statement separately, outside any transaction.
CREATE INDEX CONCURRENTLY IF NOT EXISTS invoices_company_first_seen
	ON weinvoice.invoices (company_mst, first_seen_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS invoice_xml_fetched_at
	ON weinvoice.invoice_xml (fetched_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS invoice_pdf_fetched_at
	ON weinvoice.invoice_pdf (fetched_at);

-- 3. POST-CHECK
--    Expected after the change: 3 rows, every one with indisvalid = t and indisready = t.
SELECT c.relname AS indexname, t.relname AS tablename, i.indisvalid, i.indisready,
       pg_get_indexdef(i.indexrelid) AS definition
  FROM pg_index i
  JOIN pg_class c ON c.oid = i.indexrelid
  JOIN pg_class t ON t.oid = i.indrelid
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'weinvoice'
   AND c.relname IN ('invoices_company_first_seen', 'invoice_xml_fetched_at', 'invoice_pdf_fetched_at')
 ORDER BY c.relname;

-- 4. ROLLBACK / RECOVERY NOTES
--    Indexes hold no data; dropping them only makes the notification queries slower again.
--    Run each statement separately, outside any transaction:
--      DROP INDEX CONCURRENTLY IF EXISTS weinvoice.invoices_company_first_seen;
--      DROP INDEX CONCURRENTLY IF EXISTS weinvoice.invoice_xml_fetched_at;
--      DROP INDEX CONCURRENTLY IF EXISTS weinvoice.invoice_pdf_fetched_at;

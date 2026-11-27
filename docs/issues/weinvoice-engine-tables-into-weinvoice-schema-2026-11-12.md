# WeInvoice: Move the Engine Tables into the `weinvoice` Schema

**Date:** 2026-11-12
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** COMPLETE
**Investigation Status:** COMPLETE · **Approval Status:** NOT REQUIRED (developer request 2026-11-12: "giúp t setup lần lượt phần này") · **Implementation Status:** COMPLETE (script + docs) · **Test Status:** see Verification
**Repository:** wecms-api (`we-scraping-api`)

## Problem

The engine DB `wetax_scraping` holds the engine tables in `public` (17 tables) and the WeInvoice tables in `weinvoice`
(11 `winv_*` tables). The developer wants one schema, `weinvoice`.

## Current Behavior (read-only, 2026-11-12)

- PostgreSQL 16.13, database `wetax_scraping`, owner `wetax_scraping`. Every one of the 28 tables is owned by
  `wetax_scraping`; that role is not superuser / createrole.
- `public`: `companies, einvoice_providers, email_files, email_links, email_messages, gdt_login_blocks, gdt_sessions,
  invoice_details, invoice_pdf, invoice_xml, invoices, job_run_companies, job_runs, partners, sync_cursors,
  task_attempts, tasks`. 8 sequences, all owned by a column of these tables (`deptype a`) → they move with them.
- No views, no functions, no extensions in either schema; no foreign key between the two schemas.
- Engine code (Python) names tables **unqualified** and runs with the default `search_path` (`"$user", public`).
  `gdt_login_guard._ensure` runs `CREATE TABLE IF NOT EXISTS gdt_login_blocks` on use; `init-db` runs `db/schema.sql`.
- Backend names them `{eng}.<table>` / `{app}.winv_*` (`WeInvoiceDb`), from `WECMS_WEINVOICE_ENGINE_SCHEMA` /
  `_APP_SCHEMA`; nothing else hard-codes `public`.

## Expected Behavior

All 28 tables in `weinvoice`; engine (every machine, old or new code) and backend keep working.

## Decision

- `ALTER TABLE public.<t> SET SCHEMA weinvoice` for the 17 tables (indexes, constraints, owned sequences, grants move
  with the table; no data copy).
- `ALTER DATABASE wetax_scraping SET search_path = weinvoice` (owner may do this). Every **new** connection of every
  machine then resolves unqualified names in `weinvoice` — no `.env` change on any engine machine, and an engine still
  on old code cannot recreate empty tables in `public`.
- Backend: `WECMS_WEINVOICE_ENGINE_SCHEMA=weinvoice` on each api host, then restart.
- Connections opened before the change keep the old `search_path` → every engine process and the api must be stopped
  before and restarted after.

## Database Impact

MANUAL DBA SCRIPT — [move-engine-tables-to-weinvoice-schema-2026-11-12.sql](../sql/2026/move-engine-tables-to-weinvoice-schema-2026-11-12.sql)
(DDL; no data change; rollback included and non-destructive).

## Database Execution Status

| Environment | Required | Executor | Status | Executed Date | Verification |
|---|---|---|---|---|---|
| Engine DB `wetax_scraping` (192.168.60.121) | Yes | DEV TEAM (developer) | COMPLETE | 2026-11-12 | new connection: `search_path` = `weinvoice`; `weinvoice` 28 tables, `public` 0; 12 sequences in `weinvoice`; counts unchanged (invoices 92,316, invoice_xml 30,100, tasks 122,471); `pg_db_role_setting` = `search_path=weinvoice` |

## API Impact

None (same endpoints). Configuration only: `WECMS_WEINVOICE_ENGINE_SCHEMA=weinvoice`.

## Operational Runbook

1. Stop on **every** machine: Task Scheduler tasks (Worker XML, Worker PDF, Sync Lists, Mail, WeInvoice API,
   WeInvoice Worker, "GDT Invoice Job"), any running `invoice_job.py` / `weinvoice_worker.py` / `internal_api.py`, and
   the backend (`bootRun`).
2. Run the script section 1 (PRE-CHECK); `pg_stat_activity` must show no other session of this database.
3. Run section 2 (CHANGE) — one transaction.
4. Run section 3 (POST-CHECK) **from a new connection**.
5. Set `WECMS_WEINVOICE_ENGINE_SCHEMA=weinvoice` in the backend `.env` of each api host.
6. Start the backend, then the engine tasks. Check `python -X utf8 web\invoice_job.py status` and one invoice list
   on the web.

## Verification

### Automated Tests

- Script dry run against a copy: see the test-result record once run.

### Manual Verification

- Environment: engine DB, 2026-11-12. Pre-check: 1a/1c/1d as expected; 1b first showed the .13 backend (11 JDBC
  connections) and one .13 python process → both stopped, then only the developer's DBeaver remained.
- Change: run by the developer from DBeaver as one `DO $$ … $$` block (the 17 `ALTER TABLE … SET SCHEMA` + `ALTER
  DATABASE … SET search_path`; atomic, same statements as section 2) because DBeaver's auto-commit mode made the
  `BEGIN … COMMIT` form hard to follow.
- Post-check: PASS (values in the execution table above), read from a fresh psycopg2 connection.
- Note: DBeaver's SQL editor sets its own active schema (`public`), so an unqualified `invoices` failed there with
  42P01 — a DBeaver setting, not the database. Pick schema `weinvoice` in the DBeaver toolbar.
- Backend on .13 with `WECMS_WEINVOICE_ENGINE_SCHEMA=weinvoice`, engine tasks re-enabled: done by the developer
  2026-11-12 ("xong rồi") — web list + `invoice_job.py status` checked by the developer, not observed here.

## Known Limitations / Follow-up

- Tools that connect with their own `search_path` (a DBeaver connection profile, another role with a role-level
  setting) must use `weinvoice.` or set their own path.
- Engine `.env` and code need no change; the engine CLAUDE.md is updated.

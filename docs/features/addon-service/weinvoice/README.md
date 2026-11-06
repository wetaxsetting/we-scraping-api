# WeInvoice (backend)

| | |
|---|---|
| JEX counterpart | none — refactor-native product (UI Spec v1.0, PO WeCMS, 30/10/2026) |
| Refactor status | P1 API implemented 2026-11-02; database scripts not yet applied; engine worker for the request queue not yet built |
| Code | `src/main/java/com/kosign/wecms/domain/weinvoice/` |
| Contract | [interface-specification.md](interface-specification.md) |
| Test scenarios | [test-scenarios.md](test-scenarios.md) |
| Implementation record | [weinvoice-api-implementation-2026-11-02.md](weinvoice-api-implementation-2026-11-02.md) |

## What it is

WeInvoice gathers a customer's purchase and sales e-invoices: list and XML from the GDT portal, the
original PDF from the provider portals, invoices received by mail, and manual uploads. The collecting
is done by the **we-scraping engine** (Python, separate repository), which writes its own PostgreSQL
tables. This backend:

- serves those tables to the WeInvoice screens (`/api/v1/weinvoice/**`, JWT; rows owned by the signed-in user's `USER_ID`, not the WeCMS `BIZ_KEY`);
- keeps the WeInvoice data the engine does not own — managed tax codes and their GDT account, tags,
  uploaded files, manual lookup codes, user preferences, the provider catalogue;
- asks the engine for work through a request table instead of calling it;
- serves WeCMS's "Update" button through the Partner API (`/api/partner/v1/**`, `X-Api-Key`), in the
  exact row shapes WeCMS already ingests from Coocon.

## Data and connections

```
PostgreSQL — WeInvoice database (= engine database)        PostgreSQL — WeCMS database
  <engine schema>  invoices, invoice_xml, invoice_pdf,       (spring.datasource, JPA)
                   invoice_details, tasks, email_*, …        users, tenants, menus …
                   ← engine writes · backend reads only
  weinvoice        winv_* tables
                   ← backend writes · engine reads requests, writes reports
```

- The WeInvoice connection is a private Hikari pool inside `WeInvoiceDb`, **not** a Spring
  `DataSource` bean (a second bean would re-point the auto-configured JDBC templates, JPA transactions
  and the health check). WeInvoice code uses `WeInvoiceDb.tx()`, never `@Transactional`.
- SQL names tables `{eng}.…` / `{app}.…`; the schema names come from configuration and are validated
  as plain identifiers at startup.
- Schema-of-record: [`src/main/resources/db/weinvoice/V1__winv_schema.sql`](../../../../src/main/resources/db/weinvoice/V1__winv_schema.sql)
  (11 tables, 4 sequences). Seed + grants:
  [`docs/sql/2026/seed-weinvoice-provider-catalog-2026-11-02.sql`](../../../sql/2026/seed-weinvoice-provider-catalog-2026-11-02.sql),
  [`docs/sql/2026/grant-weinvoice-schema-access-2026-11-02.sql`](../../../sql/2026/grant-weinvoice-schema-access-2026-11-02.sql).
- Files: XML / PDF / mail files are read from `WECMS_WEINVOICE_ENGINE_OUTPUT_DIR` (paths in the engine
  tables are relative to it); uploads are written under `WECMS_WEINVOICE_STORAGE_ROOT`, which the
  engine must also see.

## Configuration (`wecms.weinvoice.*`, all bare `${VAR}`)

| Variable | Meaning |
|---|---|
| `WECMS_WEINVOICE_ENABLED` | Master switch; `false` → no pool, every endpoint answers `WBE3000` |
| `WECMS_WEINVOICE_DB_URL` / `_USERNAME` / `_PASSWORD` / `_MAX_POOL_SIZE` | WeInvoice database |
| `WECMS_WEINVOICE_ENGINE_SCHEMA` / `_APP_SCHEMA` | Engine tables (e.g. `public`) / WeInvoice tables (`weinvoice`) |
| `WECMS_WEINVOICE_ENGINE_OUTPUT_DIR` / `_STORAGE_ROOT` | Engine files / upload root |
| `WECMS_WEINVOICE_SECRET_KEY` | Base64 of 32 bytes — AES-256-GCM key of the stored GDT passwords; the engine needs the same value |
| `WECMS_WEINVOICE_PARTNER_API_KEYS` | Comma-separated keys accepted from WeCMS; empty = Partner API closed |
| `WECMS_WEINVOICE_MAIL_DOMAIN` | Domain of the per-MST receiving address `hd-<mst>@<domain>` |
| `WECMS_WEINVOICE_MAX_COMPANIES` | Tax codes per user account (prototype: 5) |
| `WECMS_WEINVOICE_TEST_DATA_ENABLED` | Enables `POST /test-data/load` (dev / SIT) |

All five `.env.*.example` templates carry the block. A local `.env.local` written before 2026-11-02
must get these lines too, or startup fails on the unresolved booleans.

## Engine contract (`weinvoice.winv_engn_rqst`)

The backend inserts rows with `rqst_stts = 'QUEUED'`. An engine worker:

1. claims the oldest queued row (`FOR UPDATE SKIP LOCKED`), sets `RUNNING` and `strt_dtm`;
2. reports `step_cd` and `prgs_data` while it works, and stops at its next checkpoint when
   `cncl_yn = 'Y'` (status `CANCELLED`);
3. ends with `DONE` / `PARTIAL` / `FAILED`, `rslt_data`, `err_msg`, `end_dtm`, and `job_run_id` when a
   `job_runs` row served the request.

| `rqst_type` | `rqst_prmt` | Engine work |
|---|---|---|
| ~~`LOGIN_TEST`~~ | — | **Not used since 2026-11-06.** The account is verified synchronously: the backend calls the engine's internal API `POST /internal/gdt/login-test` (we-scraping `web/internal_api.py`, header `X-Api-Key`, settings `WECMS_WEINVOICE_SCRAPING_URL` / `_API_KEY`) and stores the row `CONNECTED` only when GDT accepts it. The engine reads `CONNECTED` rows of `winv_cmpn_infm` (decrypting `gdt_pw_enc` with `WEINVOICE_SECRET_KEY` = this backend's `WECMS_WEINVOICE_SECRET_KEY`) and registers them in its `companies` table before each sync (`weinvoice_bridge.py`). [Issue record](../../../issues/weinvoice-gdt-login-test-synchronous-2026-11-06.md) |
| `SYNC` | `{direction, from_month, to_month}` | List + XML for the period; progress `{month, listed, xml_done, xml_total}`, steps `login · captcha · list · xml · done`; result `{new, updated, xml}`; write `winv_sync_cvrg` per month and list type |
| `FETCH_XML` | `{invoice_ids}` | Move those invoices' xml tasks to the front of the queue |
| `FETCH_PDF` | `{invoice_ids, lookup_code?, lookup_url?, company_code?}` | Run the provider adapter now (steps `open · submit · download · done`) |
| `IMPORT` | `{upload_id[, file_nos, company_mst]}` or `{invoice_id, file_id}` | Parse the stored files, apply the dedupe rules (UI Spec 3.2), write `rslt_*` of `winv_upld_file` |
| `TEST_DATA` | `{}` | Load the acceptance TestData folder |

GDT password format: `"v1:" + base64(iv[12] ‖ ciphertext ‖ tag[16])`, AES-256-GCM, key = base64-decoded
`WECMS_WEINVOICE_SECRET_KEY` (Python: `AESGCM(key).decrypt(raw[:12], raw[12:], None)`).

## Access

- Every WeInvoice endpoint requires a WeCMS JWT. Every WeInvoice row is owned by the signed-in user
  (`owner_id` = `USER_INFM.USER_ID`, UI Spec 02 "one account manages several MSTs"): each user adds,
  changes and removes their own tax codes, with no WeCMS admin gate. Sub-accounts and the 8-permission
  model are P2 (UI Spec 3.10). Linking tax codes to a WeCMS `BIZ_KEY` is WeCMS's concern (Partner API,
  by `tax_id`). Changed 2026-11-05: [issue record](../../../issues/weinvoice-company-ownership-by-user-2026-11-05.md).
- A working MST must be an active connection of the signed-in user; invoices of other MSTs read as not found.
- Partner API: `X-Api-Key` against `WECMS_WEINVOICE_PARTNER_API_KEYS`, constant-time compare, fails
  closed.

## Known limitations

See the implementation record, §"Known Limitations / Follow-up".

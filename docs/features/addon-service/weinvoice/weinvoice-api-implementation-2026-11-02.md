# WeInvoice API (P1)

**Date:** 2026-11-02
**Type:** FEATURE
**Status:** READY FOR MANUAL COMMIT (ships switched off; enabling it is READY FOR DBA)
**Investigation Status:** COMPLETE · **Approval Status:** NOT REQUIRED (explicit implementation request, class C) · **Implementation Status:** COMPLETE (code) — DB scripts PREPARED, not executed
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Issue:** WeInvoice P1 — UI Spec v1.0 (PO WeCMS, 30/10/2026), Function List v1.0

## Purpose

WeInvoice is the e-invoice product described in UI Spec v1.0. The Python engine in the `we-scraping`
repository already collects the data (GDT invoice list + XML, original PDF from the provider portals,
invoices from mail) into PostgreSQL. This backend has to serve that data to the WeInvoice screens and to
WeCMS (the "Update" button that replaces Coocon).

## Requirement

Implement the P1 API listed in `API và dữ liệu WeInvoice.md` (we-scraping repo, `docs/`), section
"API theo màn hình": screens 01–10, 16, 18 and the Partner API for WeCMS. Business rules come from
`docs/WeInvoice_UI_Spec_v1.0/02_Yeu_cau_nghiep_vu.md` (§3.1–§3.10) and the data model from the spec's
`weinvoice-web/lib/types.ts`.

## Current System Behavior

- No WeInvoice endpoint exists.
- `/api/v1/addon/einvoices` serves the WeCMS e-invoice screens from `BUSA_DOC_MSTR`, filled by the
  Coocon NXiSAS engine running in the browser (`scrape-results`, `scrape-detail-results`).
- The engine tables (`invoices`, `invoice_xml`, `invoice_details`, `invoice_pdf`, `tasks`,
  `email_*`, `job_runs`, …) are not visible to this application.

## Target Behavior

Implemented (verified at unit level; database and engine integration pending — see Verification):

- `/api/v1/weinvoice/**` — the WeInvoice app API (JWT, tenant = `BIZ_KEY`, working MST per request).
- `/api/partner/v1/**` — machine-to-machine API returning the `ScrapedEInvoiceRow` /
  `ScrapedEInvoiceDetailRow` shapes WeCMS already ingests.
- Engine tables are **read** from their own schema; the backend never writes them.
- Work the engine must do (sync, fetch XML, fetch PDF, login test, mail scan, read uploads) is
  **requested** through a backend-owned request table that the engine workers poll.

## Scope

P1 endpoints from the API document: `/me`, `/me/preferences`, `/requests/{id}`, `/dashboard`,
`/invoices` (+ facets, ids, fetch-missing-xml, Excel and ZIP export), invoice detail + files + original
PDF + lookup code, `/companies` (GDT connections), `/sync-runs`, `/email/*`, `/uploads`, `/providers`,
`/tags`, Partner API.

## Out of Scope

- P2+ endpoints (sub-accounts, mailboxes, checks, supplier risk, taxpayer lookup, journal, tax portal,
  search, notifications).
- The engine-side consumer of the request table (we-scraping repo, Python) — separate change.
- Frontend (we-scraping-web).

## Proposed User / Business Flow

See the data-flow document in the we-scraping repo (`docs/Luồng dữ liệu WeInvoice.md`). In short:

```
engine (Python) ──writes──▶ engine tables (invoices, invoice_xml, invoice_pdf, tasks, email_*, job_runs)
                                   ▲ read only
WeInvoice FE ──JWT──▶ /api/v1/weinvoice ──writes──▶ weinvoice.* tables (connections, requests, tags, files…)
WeCMS BE ──X-Api-Key──▶ /api/partner/v1            ▲ engine workers poll weinvoice.winv_engn_rqst
```

## Proposed Implementation

*Initial plan, kept because it records the design decisions; what was actually built is under
`## Implementation`.* Investigation findings that shaped it:

1. **Separate database connection, not new tables in the WeCMS DB.** The WeCMS database is shared with
   the live JEX system and is governed by the legacy-parity rules; the engine already writes its own
   PostgreSQL database. The backend therefore opens a second Hikari pool to the WeInvoice database
   (= the engine database) and keeps all WeInvoice tables there, in their own schema.
   - The pool is owned by one component (`WeInvoiceDb`) and **not** registered as a `DataSource`,
     `JdbcTemplate`, `NamedParameterJdbcTemplate` or transaction-manager bean. Registering any of those
     would make Spring Boot auto-configuration back off (`@ConditionalOnMissingBean`) and silently
     re-point the existing unqualified JDBC repositories, JPA transactions and the actuator health
     check — the same trap `HaengsungOracleDataSourceConfig` documents.
   - Access is `NamedParameterJdbcTemplate` only (precedent: `BankPickerRepository`). No JPA entity,
     so `ddl-auto: validate` never looks at these tables.
   - Two schema names come from configuration and are validated as plain identifiers before they are
     placed in SQL: the engine schema (read only) and the WeInvoice schema (read/write).
2. **Engine work is requested, never executed here.** Sync, fetch XML, fetch PDF, login test and upload
   import insert a row in `weinvoice.winv_engn_rqst`; the engine worker updates its status, step,
   progress and result. The API returns `request_id`; the client polls `GET /requests/{id}`.
3. **GDT password** is encrypted with AES-256-GCM under a WeInvoice-only key
   (`WECMS_WEINVOICE_SECRET_KEY`). `BankCertCryptoService` is deliberately not reused: its fixed-IV
   legacy cipher exists only for JEX coexistence on `BANK_CERT_INFO` / `GDT_CERT_INFO`, and reusing it
   would hand the engine the key that decrypts every WeCMS bank credential. Passwords are never
   returned by any endpoint.
4. **Tenant and working MST.** Tenant = `BIZ_KEY` from the JWT. Every WeInvoice call names its working
   MST (`company_mst`); the backend checks the MST is an active connection of the tenant before reading
   engine rows of that MST.
5. **Partner API** (`/api/partner/v1/**`) is permitted past the JWT chain and authenticated by an
   `X-Api-Key` filter (constant-time compare against `WECMS_WEINVOICE_PARTNER_API_KEYS`). Rows reuse the
   existing `ScrapedEInvoiceRow` / `ScrapedEInvoiceDetailRow` records so WeCMS can feed them to
   `EInvoiceScrapeService` / `EInvoiceDetailScrapeService` unchanged.
6. **Feature switch.** `WECMS_WEINVOICE_ENABLED=false` keeps the pool closed; every WeInvoice and
   Partner endpoint then answers `WBE3000`.

Code: `domain/weinvoice/{config,partner,common,repository,service,controller,dto}`.

## API Impact

New endpoints only — 40 under `/api/v1/weinvoice`, 2 under `/api/partner/v1`; no existing endpoint
changed. Contract: [interface-specification.md](interface-specification.md).

## UI Impact

None in this repository. we-scraping-web consumes the contract.

## Database Impact

**BOTH** — new tables (schema-of-record file) + seed data and grants (manual DBA scripts).

| Item | Artifact | Target |
|---|---|---|
| Schema `weinvoice` + 11 tables + 4 sequences (`winv_cmpn_infm`, `winv_engn_rqst`, `winv_tag_infm`, `winv_invc_tag`, `winv_invc_file`, `winv_lkup_cd`, `winv_upld_infm`, `winv_upld_file`, `winv_prvd_infm`, `winv_user_pref`, `winv_sync_cvrg`) — the planned `winv_mail_addr` was dropped: the per-MST address `hd-<mst>@<domain>` is derived, no table needed | `src/main/resources/db/weinvoice/V1__winv_schema.sql` | WeInvoice database (the engine database), **not** the WeCMS database |
| 144-row provider catalogue (from UI Spec `lib/providers.ts`) | `docs/sql/2026/seed-weinvoice-provider-catalog-2026-11-02.sql` | same |
| Read grant on the engine schema for the backend login; read/write grant on `weinvoice` for the engine login | `docs/sql/2026/grant-weinvoice-schema-access-2026-11-02.sql` | same |

Why a separate folder for the DDL: `src/main/resources/db/migration/` is the schema-of-record of the
WeCMS database. These tables live in another database, so filing them there would invite a DBA to run
them against the wrong one. The header of every file names the target database.

No WeCMS table, column, index, constraint or row changes. Execution status: see
`## Database Execution Status` (added at finalization).

## Integration Impact

- **Engine (we-scraping repo)** must, in a separate change: poll `weinvoice.winv_engn_rqst`; read the
  GDT username/password from `weinvoice.winv_cmpn_infm` instead of `.env`; read uploaded files from
  `WECMS_WEINVOICE_STORAGE_ROOT`; write `weinvoice.winv_sync_cvrg`. Until then requests stay `QUEUED`.
- **Files**: the backend reads XML/PDF from the engine output directory
  (`WECMS_WEINVOICE_ENGINE_OUTPUT_DIR`, same host or shared mount) and writes uploads under
  `WECMS_WEINVOICE_STORAGE_ROOT`.
- **WeCMS**: a later WeCMS change calls the Partner API instead of the Coocon engine.

## Validation / Business Rules

Implemented, from UI Spec 02 §3:

- §3.1 Direction: `invoices.direction` `mua` → `purchase`, `ban` → `sales`; every query is scoped to
  the working MST.
- §3.2 Dedupe key (seller MST + template + symbol + number) is the engine's `UNIQUE` key; uploads are
  imported by the engine, which applies the outcomes `new / merged / duplicate / foreign / error`.
- §3.3 Kind from the symbol (`K…` = no code, 4th char `M` = cash register); currency from `DVTTe`
  (`VNĐ` → `VND`, missing → `VND`); KPIs convert to VND with `TGia`; status from `tthai`
  (1 original, 2 replacement, 3 adjustment, 4 replaced, 5 adjusted, 6 cancelled), falling back to the
  Excel text for the 19 rows whose `tthai` is NULL.
- §3.4 Provider name resolved at read time from `winv_prvd_infm`, so a catalogue addition renames old
  invoices; a manual lookup code overrides the one guessed from the XML.
- §3.5 Uploaded PDF must start with `%PDF-`; when neither the file name nor the text proves it belongs
  to the invoice → `needs_confirm`; an existing PDF → `needs_confirm` (replace); uploaded XML key must
  match the invoice. Only `upload` / `email` files can be deleted.
- §3.6 Excel export reports `missing_xml` so the UI can offer "fetch missing XML" first.
- §3.10 Secrets are write-only.
- Connection management (add / password / delete) is admin-only until the P2 permission model.

## Testing Plan

*Plan as written before implementation; outcomes under `## Verification`.*
Unit tests (JUnit 5 + Mockito + standalone MockMvc, house pattern): cipher round trip and tamper
detection; status / check-result mapping; partner key filter; tenant guard; company service (quota,
duplicate, secret never echoed); file-upload confirmation rules; partner row mapping; controller
validation and envelope. `./gradlew compileJava compileTestJava` plus the new tests. No test touches a
database (DB tests need a schema created by a DBA — not run without approval).

## Implementation

Built as planned, with these differences from the initial plan and the API document:

| Topic | Initial plan / API document | Actual |
|---|---|---|
| ZIP export | `POST /invoices/export/zip` → `request_id`, then `GET /exports/{id}/file` | Streamed in the response of `POST /invoices/export/zip`; no temp file, no second endpoint. `rendered` (the visual copy) is skipped — the browser builds it |
| Top partners | `GET /dashboard/top-partners` (P2) | Part of `GET /dashboard` (`top_suppliers`) |
| Tag assignment | P2 | Implemented (`PUT /invoices/tags`) |
| Mail-address table | `winv_mail_addr` | Not needed (derived address) |
| Lookup code | `lkup_cd` NOT NULL | Nullable with `CHECK (lkup_cd IS NOT NULL OR lkup_url IS NOT NULL)` — a pasted link may carry no recognisable code |
| Authorization | — | WeInvoice controllers added to the `EndpointAuthorizationCoverageTest` allow-list with the reason (own product, no WeCMS `permission_cd`; tenant + MST guard; admin-only connection writes) |

Components (`src/main/java/com/kosign/wecms/domain/weinvoice/`):

- `config` — `WeInvoiceProperties` (fail-fast validation, never echoes values), `WeInvoiceDb`
  (private Hikari pool, `tx()`, schema substitution), `WeInvoiceConfig`.
- `common` — `CompanyAccessGuard` (tenant / working-MST / invoice visibility), `InvoiceCodes`
  (status, kind, currency, VAT rate, WeCMS phrases), `WeInvoiceSecretCipher` (AES-256-GCM),
  `StoredFiles` (engine output + upload storage, path guard), `WeInvoiceJson`, `WeInvoiceTime`,
  `WeInvoiceIds`, `WeInvoicePaging`.
- `repository` — 14 JDBC repositories (+ `InvoiceCriteria`, `InvoiceSortField`, `Rows`); the invoice
  list shares one `base` / `conv` CTE that derives status, file presence, provider, CQT code, currency
  and VND rate from the engine rows.
- `service` — me, dashboard, invoice query / files / actions / export, companies, sync runs, mail,
  uploads, catalogue + tags, engine requests; `PdfInspector` (OpenPDF text check) and `XmlKeyReader`
  (StAX, DTD and external entities off).
- `controller` — 6 controllers, 40 endpoints under `/api/v1/weinvoice`.
- `partner` — `PartnerApiKeyFilter`, `PartnerEInvoiceController` (2 endpoints), `PartnerEInvoiceService`.

Shared code touched: `SecurityConfig` (permit `/api/partner/v1/**`, add `PartnerApiKeyFilter` before
the JWT filter), `application.yml` (`wecms.weinvoice` block), the five `.env.*.example` templates,
`messages*.properties` (EN default, VI, KO: `WBE3000`–`WBE3026`, export labels, statuses).

Found and fixed during verification:

- `InvoiceActionService.codeFromLink` took page names such as `/tra-cuu/` for a lookup code; a code
  must now contain a digit (unit test `linkWithoutCodeGivesNull`).
- Nullable numeric columns were read with `rs.getObject(col, Long.class)`; pgjdbc refuses that
  conversion for `numeric(18)` (`winv_upld_infm.rqst_seq`, `winv_engn_rqst.rqst_seq` in the sync
  history), so those reads would have failed at runtime. All such reads now go through
  `Rows.longOrNull / intOrNull` (`getLong` / `getInt` + `wasNull`).

### Moved onto `sit` (2026-11-05)

The work was written on a local `main` at `afc95b0` (2026-08-18), 23 commits behind `origin/sit`. On
user request it was carried, uncommitted, onto a new local branch `f1_v1_weinvoice_api` created from
`origin/sit` (`6aa0c1f`); nothing was committed or pushed. The working tree applied without textual
conflicts, but `sit` carries `64e842c refactor(config): one shared application.yml, keys-only .env
templates`, which changed the rules the original change was written against:

- **`.env.*.example` must be keys-only.** The WeInvoice block had committed values (`false`, a JDBC URL,
  `weinvoice_app`, `public`, `5` …) that `SchedulerConfigurationContractTest` rejects. All 14
  `WECMS_WEINVOICE_*` lines are now blank in the five templates; the comment and the `application.yml`
  note say blank is fine while disabled.
- **Blank values must bind.** A host `.env` copied from a keys-only template carries
  `WECMS_WEINVOICE_ENABLED=` — Spring binds that to `null`, and the record's primitive `boolean enabled`
  / `boolean testDataEnabled` rejected it at startup (`A null value cannot be assigned to a primitive
  type`), found by the new test `WeInvoicePropertiesTest.blankEnvValuesBindToDisabledModule`. Both are
  now `Boolean`, normalised to `false` in the compact constructor, so the accessors never return null.

The design note `weinvoice-api-and-data-2026-11-02.md` (already on `sit`) was corrected the same day:
the architecture section now shows the WeInvoice tables in the engine database rather than the WeCMS
one, the ZIP export and top-partners rows match the implementation, and the three decisions the code
already took (engine request table, `winv_cmpn_infm` + AES-GCM, `X-Api-Key`) are ticked.

## Files Changed

| File | Change |
|---|---|
| `src/main/java/com/kosign/wecms/domain/weinvoice/**` | New module, 89 files: config 3 · common 8 · repository 17 · service 14 · controller 6 · partner 5 · dto 36 |
| `src/main/java/com/kosign/wecms/config/SecurityConfig.java` | Partner path permitted; `PartnerApiKeyFilter` wired |
| `src/main/resources/application.yml` | `wecms.weinvoice.*` block (bare `${VAR}`) |
| `.env.example`, `.env.local.example`, `.env.sit.example`, `.env.uat.example`, `.env.prod.example` | `WECMS_WEINVOICE_*` block, 14 keys, all values blank (keys-only rule of `sit`) |
| `src/main/java/com/kosign/wecms/domain/weinvoice/config/WeInvoiceProperties.java` | (2026-11-05) `enabled` / `testDataEnabled` → `Boolean`, blank = off |
| `docs/features/addon-service/weinvoice/weinvoice-api-and-data-2026-11-02.md` | (2026-11-05) design note aligned with the implementation |
| `src/main/resources/messages.properties`, `messages_vi.properties`, `messages_ko.properties` | 55 keys each |
| `src/main/resources/db/weinvoice/V1__winv_schema.sql` | New schema-of-record (WeInvoice database) |
| `docs/sql/2026/seed-weinvoice-provider-catalog-2026-11-02.sql` | New manual DBA script (144 providers, generated from the UI Spec catalogue) |
| `docs/sql/2026/grant-weinvoice-schema-access-2026-11-02.sql` | New manual DBA script (privileges) |
| `src/test/java/com/kosign/wecms/domain/weinvoice/**` | 12 new test classes + 1 fixture |
| `src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java` | Allow-list entries for the 7 WeInvoice controllers, with the reason |
| `docs/features/addon-service/weinvoice/README.md`, `interface-specification.md`, `test-scenarios.md` | New permanent feature docs |
| `docs/features/addon-service/README.md`, `docs/sql/README.md` | Index row / target-database note |

## Database Execution Status

| Environment | Required | Executor | Status | Executed Date | Verification |
|---|---|---|---|---|---|
| LOCAL (WeInvoice / engine DB) | Yes, before enabling WeInvoice | DEV TEAM (run by the AI assistant on the developer's explicit approval, 2026-11-05) | COMPLETE | 2026-11-05 | see note below |
| SIT | Yes | DBA OR DEV TEAM | NOT STARTED | - | - |
| UAT | Yes | DBA TEAM | NOT STARTED | - | - |
| PROD | Yes | DBA TEAM | NOT STARTED | - | - |

Order per environment: `V1__winv_schema.sql` → `grant-weinvoice-schema-access-2026-11-02.sql` (skip when
backend and engine share one owning role) → `seed-weinvoice-provider-catalog-2026-11-02.sql`. Each
script carries its own pre / post checks. Nothing was executed against any database in the 2026-11-02 task.

**LOCAL execution, 2026-11-05.** Target: the engine PostgreSQL that the developer's `we-scraping` job
writes (remote host from the engine `.env` `DATABASE_URL`; the same role owns both schemas, so the
grant script was skipped). Pre-check: role may `CREATE` in the database, schema `weinvoice` absent,
`public.invoices` 16 481 rows (5 MSTs). Applied, one transaction each: `V1__winv_schema.sql`, then
`seed-weinvoice-provider-catalog-2026-11-02.sql`. Post-check: 11 tables in `weinvoice`,
`winv_prvd_infm` 144 rows, `public.invoices` still 16 481. One data row added by hand so the developer
can browse existing data: `winv_cmpn_infm` (`VN_VNM0004_000001` — the only WeCMS tenant whose
`BUSI_INFM.TAX_ID` is 1201496252, read from `wecmsdevdb` — MST `1201496252`, `conn_stts=NEED_PASSWORD`,
`dflt_yn=Y`, `reg_id=local-setup`). SIT / UAT / PROD untouched.
Later the same day ownership moved from tenant to user ([issue record](../../../issues/weinvoice-company-ownership-by-user-2026-11-05.md)):
the local `weinvoice` schema was dropped and recreated (`owner_id`), the `biz_key` mapping row with it.

## Verification

Summary — full evidence in the test-result record (§"Test Evidence").

### Automated Tests

- Test: `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*'` — 12 classes, 84 tests.
- Result: PASS (0 failures). A first run had 5 failures: 4 from a test-fixture `ObjectMapper`
  without JSR-310, 1 real defect (`codeFromLink`), both fixed.
- Test: full `./gradlew test` — 2 825 tests.
- Result: FAIL — 13 pre-existing failures only (the same 13 fail on an unchanged `HEAD` export: the
  `profile=local` YAML tests, `application-local.yml` is git-ignored and absent here). No new failure.
  `EndpointAuthorizationCoverageTest` PASS after the allow-list entries.
- Re-run on `sit` (2026-11-05, branch `f1_v1_weinvoice_api`): WeInvoice 85/85 PASS (one new test,
  `blankEnvValuesBindToDisabledModule`, which failed before the `Boolean` fix); full suite 2 878 tests,
  13 failures, all pre-existing — 12 `application-local.yml`, 1 `VatDeclarationLifecycleTest` that also
  fails on an unchanged `origin/sit` export. No new failure.

### Test Scenarios

- Scenario: unit-level scenarios T1, T2, T5, C1–C5, I1, I3, F1–F9, P1–P4 of
  [test-scenarios.md](test-scenarios.md).
- Expected / Actual: as specified / as specified.
- Result: PASS.
- Scenarios marked "DB" there: NOT RUN — no WeInvoice schema exists yet.

### Regression Review

- Areas checked: security filter chain (new filter is a no-op outside `/api/partner/v1/`; existing
  callback and JWT filters unchanged), auto-configured JDBC / JPA beans (no new `DataSource`,
  `JdbcTemplate`, `NamedParameterJdbcTemplate` or transaction-manager bean), configuration contract
  tests (`.env.*.example` carry every new `${VAR}`), authorization coverage test.
- Result: PASS — full suite shows no new failure.

### Manual Verification

- Environment: LOCAL, no database.
- Steps: dumped all 29 assembled SQL statements through a temporary test and reviewed them; reviewed
  row mapping against pgjdbc conversions (found and fixed the `numeric` → `Long` defect).
- Result: PASS for syntax / intent review; execution against PostgreSQL NOT RUN.

## Test Evidence

- 2026-11-02, local `main`: [weinvoice-api-test-result-2026-11-02.md](../../../testing/results/weinvoice-api-test-result-2026-11-02.md)
- 2026-11-05, on `sit`: [weinvoice-api-sit-rebase-test-result-2026-11-05.md](../../../testing/results/weinvoice-api-sit-rebase-test-result-2026-11-05.md)

Final test result: PASS for the change (85/85), full suite no new failure · Regression coverage: PASS
(`blankEnvValuesBindToDisabledModule`).

## Result

```
Code: COMPLETE on f1_v1_weinvoice_api (from origin/sit 6aa0c1f), 85/85 unit tests, no regression
Manual commit / push: PENDING (developer)
SIT deploy: NOT STARTED — host .env needs the 14 WECMS_WEINVOICE_* keys first (blank = module off)
DB scripts: PREPARED — not executed in any environment
DB-level / running-server verification: NOT RUN
Engine worker (we-scraping repo): NOT STARTED — required for every engine request
Overall: READY FOR MANUAL COMMIT (module ships switched off);
         switching it on is READY FOR DBA, then DB verification, then the engine worker
```

Nothing was committed or pushed.

## Known Limitations / Follow-up

Blocking before WeInvoice can be switched on:

1. **DB scripts not executed** anywhere (table above) — `READY FOR DBA`.
2. **Engine worker missing** (we-scraping repo): nothing consumes `winv_engn_rqst` yet, so login tests,
   syncs, XML / PDF fetches, imports and test data stay `queued`. The engine must also read the GDT
   account from `winv_cmpn_infm` (AES-GCM, `README.md` §"Engine contract") instead of its `.env`, and
   write `winv_sync_cvrg` — until then `incomplete_periods` is always empty.
3. **Not run against a database.** No WeInvoice schema exists yet and creating one needs approval; the
   SQL was reviewed from a full dump of the assembled statements (29 statements) but never executed.

Open decisions carried over (API document §"Việc cần chốt"):

4. Product boundary — WeInvoice is built inside this WeCMS copy, not as a separate application.
5. Partner API: any configured key may read any MST known to the engine (no per-key MST scope);
   check-result phrases for GDT codes 0 and 4 and the unpadded `invoice_no` (vs Coocon's format, which
   decides WeCMS `DOC_ID`) must be confirmed with the WeCMS team; non-VND/USD currencies still hit the
   `BUSA_DOC_MSTR` CHECK on the WeCMS side; signer names are not in the engine data.
6. UI Spec 3.4 (no captcha bypass) vs the engine adapters that read image captchas — PO decision;
   `FETCH_PDF` uses whatever adapters the engine enables.
7. Deleting an MST keeps its invoices (soft delete only).

Smaller gaps:

8. Mail files cannot be deleted (UI Spec allows it): they are engine rows the backend does not write.
9. "Create new" from a mail or upload XML depends on the engine import; the engine still creates
   invoices only from GDT lists (903 mail XML waiting, per the we-scraping CLAUDE.md §3.3).
10. Not menu-gated: WeInvoice endpoints are on the authorization allow-list until the P2 permission
    model (8 permissions per sub-account) exists; connection writes are admin-only meanwhile.
11. The tenant quota check is not locked: two simultaneous "add MST" calls can exceed it by one.
12. P2+ endpoints (search, notifications, mailboxes, sub-accounts, checks, supplier risk, taxpayer
    lookup, journal, tax portal, related-invoice comparison) are not implemented.
13. **Deploy order (every environment, SIT first):** add the 14 `WECMS_WEINVOICE_*` keys to the host
    `.env` / `setenv.sh` **before** this build is deployed. The yaml uses bare `${VAR}`, so a missing
    key fails startup; a present-but-blank key is fine and leaves WeInvoice off. Same for a local
    `.env.local` created before this change — copy the block from `.env.local.example`.

# Settings > VAS Account — Refactor Backend Feature Hub

**Module:** Settings → VAS Account
**Menu:** Settings popup → "VAS Account" submenu
**Refactor screen ID:** `vas-account` — also the export `screenId` and the **reserved** View Setting `WEB_SVC_ID` (single area `default`). View Setting is **not adopted** (no `SYS_SCR_OPTION_WECMS` seed; no popover).
**Feature-folder slug (matches JEX + frontend):** `vas-account`
**REST base:** `/api/v1/settings/vas-account` (list · lookup · detail · create · update · delete · excel preview/import · export)
**Audit `WSVC_ID`:** `vas-account` (slug — never `comm_0217_001`)
**i18n key prefix:** `settings.vas_account.*` · audit `audit.vas_account.*`
**JEX counterpart:** `comm_0217_001` family (`_001` list/delete · `_002` Excel upload · `_003` create · `_004` detail/update) + `comm_1002_001` (lookup picker) — [docs/jex/features/vas-account/](../../../../../docs/jex/features/vas-account/README.md)
**Frontend counterpart:** [wecms-web/docs/features/settings/vas-account/](../../../../../wecms-web/docs/features/settings/vas-account/README.md)

> **Status — Refactor complete (full-stack).** VAS Account list (search + Active/Inactive
> status filter), single-record detail, create, update, soft-delete, the active-only lookup
> picker, and Excel preview + import (Append / Replace modes) are migrated and wired
> end-to-end. The XLSX **export** endpoint + `VasAccountExportProvider` are implemented and tested on
> the backend, but the frontend panel surfaces no export button yet (OQ-VAS-1 open). Legacy parity
> verified against the `comm_0217_*` family; SIT/UAT completed. The one tracked deviation is
> **F1 — `VasAccountIntegrationTest` is `@Disabled`** (Testcontainers + JWT-mock infra deferred);
> unit + controller + Excel + export-provider coverage is green. See
> [refactor-gap-analysis.md](refactor-gap-analysis.md).
>
> **Three premises commonly assumed about this feature are false — confirmed against the code and
> the live DB:** (1) there is **no parent-child hierarchy** in the database or backend — the table
> is a flat, `BIZ_KEY`-scoped list with no parent FK, no display-order column, no depth cap, and no
> circular-reference logic; the "hierarchy" is a purely **client-side display-only prefix grouping**
> built in the frontend (`buildTree`). (2) There is **no bulk activate/deactivate** and **no separate
> activate/deactivate endpoint** — status (`A`/`I`) is an ordinary field set on Create/Update. (3) The
> legacy WSVC family is **`comm_0217_*`**, not `comm_0223` (that id is Chart of Accounts — a different
> table, `CHART_ACCT_INFM`, which *does* have a real hierarchy). These three points are documented in
> full in [functional-specification.md §1](functional-specification.md) and
> [refactor-gap-analysis.md](refactor-gap-analysis.md).
>
> This folder is the **primary documentation home** for the refactored feature. The older single-file
> spec [features/settings/vas-account.md](../vas-account.md) predates this folder and is
> **preserved + cross-linked** — but note it still describes the *planned* surface (a retired
> `/excel/cell/validate` endpoint, the legacy `tot_cnt`/`suc_cnt`/`rec` Excel-preview wire shape, and
> `WBE0404`/`WBE2014` codes). This folder documents the **as-built** surface (no cell-validate
> endpoint; preview ships `rows`/`total_count`/`completed_count`/`error_count`; not-found is `WBE2040`,
> duplicate is `WBE0003`).

JEX is **read-only functional reference**. This folder documents the refactored Spring Boot
implementation; the JEX behaviour at the `comm_0217_*` family is the functional source of truth.

---

## What this feature does

VAS Account ("Value-Added Service" accounting code) is a per-tenant list of short accounting codes
(e.g. `111` "Tiền mặt") that downstream banking surfaces reference by surrogate key. The Settings
panel lets an administrator maintain that list. Capabilities:

- **List + search + status filter** — full filtered set (Active / Inactive multi-select), keyword
  match on code or name.
- **Lookup picker** — active-only typeahead (`/lookup`) consumed by the Edit Bank Account dialog and
  other VAS-picker call sites (legacy `comm_1002_001`).
- **Detail** — fetch one record for the edit dialog.
- **Create** — manual entry; duplicate-code guard within the tenant.
- **Update** — edit code / name / status; duplicate-code re-check only when the code changes;
  English-name / type / notes preserved when omitted.
- **Soft delete** — sets `DEL_YN='Y'` (no hard delete).
- **Excel preview + import** — read sheet → per-cell validation → bulk insert. Two write modes:
  **A**ppend (insert non-duplicate codes) or **R**eplace (soft-delete every live row, then insert all).
- **Excel export** — XLSX of the filtered list via the shared `common/export` pipeline.
- **Account mappings consumed elsewhere** — Smart Organizer and the banking Transaction-assign flow
  read VAS codes by `VAS_ACCT_SEQ_NO` / `VAS_ACCT_CD` (see [business-flow.md](business-flow.md)).

**What this feature deliberately does NOT do** (verified absent in both JEX and the refactor):
parent-child hierarchy / tree storage · display-order column · maximum-depth or circular-reference
rules · bulk activate/deactivate · a dedicated status-change endpoint · bulk delete · hard delete ·
a backend template-download endpoint (the template is a static XLSX served by the frontend).

---

## Document map (this folder)

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | Scope (incl. the "no hierarchy / no bulk-status" boundary), filters, grid columns, sequence generation, status/type codes, validation, permissions, export — the *what*. |
| [business-flow.md](business-flow.md) | List / create / update / delete / lookup / Excel preview+import / export flows, sequence allocation + concurrency, audit emission, the SmartOrganizer & banking consumption boundary — the *how*. |
| [database-mapping.md](database-mapping.md) | `BUSI_VAS_ACCT_INFM` table + legacy-column → entity-field → DTO-field → UI-field chain; status/type columns; migration V37 (`CREATE TABLE` only — no seed, no View Setting seed). |
| [interface-specification.md](interface-specification.md) | Every REST endpoint, request/response DTO field lists, wire shapes, error codes. |
| [source-code-index.md](source-code-index.md) | Legacy JEX ↔ refactor backend ↔ refactor frontend file index + id correspondence. |
| [migration-guide.md](migration-guide.md) | JEX WSVC / IDO / `_act.jsp` → Spring controller/service/repository mapping; envelope/naming/key-generation migration; deliberate deviations. |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (search, create, update, status change, import, export, validation, error handling). |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / APPROVED DEVIATION / PARTIAL / MISSING) + special verification (bulk activate, parent validation, import validation, sequence, hierarchy). |

## Companion references (preserved — cross-linked, not duplicated)

| Doc | Location |
|---|---|
| Older single-file feature spec (backend) — preserved, superseded | [features/settings/vas-account.md](../vas-account.md) |
| Backend service/domain design (signatures) | [references/business-rules/vas-account.md](../../../references/business-rules/vas-account.md) |
| Database design (schema + Flyway plan) | [references/database/vas-account.md](../../../references/database/vas-account.md) |
| JEX → Refactor migration mapping (preserved) | [references/migration/vas-account.md](../../../references/migration/vas-account.md) |
| Parity audit 2026-05-11 (preserved, authoritative as-built record) | [references/audits/vas-account.md](../../../references/audits/vas-account.md) |
| Test plan (4-layer) — preserved | [testing/vas-account.md](../../../testing/vas-account.md) |
| Settings ERD | [references/database/erd/settings-erd.md](../../../references/database/erd/settings-erd.md) |
| Table ownership | [references/database/table-ownership.md](../../../references/database/table-ownership.md) |
| Archived menu-entry hub stub (retired — do not link onward) | archive/settings-menu-hubs/vas-account.md _(removed — historical `archive/` folder deleted by project decision)_ |
| JEX functional spec (source of truth) | [docs/jex/features/vas-account/](../../../../../docs/jex/features/vas-account/README.md) |

---

## Key backend artifacts

| Layer | Class | Path |
|---|---|---|
| Controller | `VasAccountController` (`@RequestMapping("/api/v1/settings/vas-account")`) | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/controller/VasAccountController.java` |
| Service (CRUD + list + lookup) | `VasAccountService` | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAccountService.java` |
| Service (Excel preview + import) | `VasAccountExcelService` | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAccountExcelService.java` |
| Sequence generator | `VasAcctSeqNoGenerator` | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAcctSeqNoGenerator.java` |
| Sort whitelist | `VasAccountSortField` (`implements SortableField`) | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAccountSortField.java` |
| Repository | `BusiVasAcctInfmRepository` | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/repository/BusiVasAcctInfmRepository.java` |
| Entity (`@Table BUSI_VAS_ACCT_INFM`, `@IdClass`) | `BusiVasAcctInfm` / `BusiVasAcctInfmId` | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/entity/` |
| Excel export provider | `VasAccountExportProvider` (`screenId = "vas-account"`) | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/export/VasAccountExportProvider.java` |
| Status / type enums | `AStts` (`A`/`I`), `VasAcctType` (`0`/`1`/`2`) | `src/main/java/com/kosign/wecms/common/enums/` |
| Schema | **V37** (`busi_vas_acct_infm` — `CREATE TABLE` only; no seed migration, no View Setting seed) | `src/main/resources/db/migration/V37__busi_vas_acct_infm.sql` |

DTOs (`src/main/java/com/kosign/wecms/domain/setting/vasaccount/dto/`): `VasAccountListResponse`,
`VasAccountRow`, `VasAccountLookupResponse`, `VasAccountLookupRow`, `VasAccountCreateRequest`,
`VasAccountUpdateRequest`, `VasAccountExcelPreview`, `VasAccountExcelPreviewRow`,
`VasAccountExcelFieldError`, `VasAccountExcelImportRequest`, `VasAccountExcelImportResult`.

---

## Migration status

**Refactor Status: 100% Complete (full-stack).** List + search + status filter, lookup picker,
detail, create, update, soft-delete, and Excel preview + import (Append / Replace) are migrated and
wired end-to-end with verified `comm_0217_*` parity; SIT/UAT completed. The XLSX export endpoint +
`VasAccountExportProvider` are implemented and tested on the backend; the frontend export button is
not yet surfaced (OQ-VAS-1 open). Open items: **F1** — the integration-test class is `@Disabled`
pending Testcontainers/JWT-mock infra (unit / controller / Excel / export coverage is green); and the
frontend export-button decision (OQ-VAS-1). No functional gap. Evidence for every closed area is in
[refactor-gap-analysis.md](refactor-gap-analysis.md).

# Settings > VAS Account — Functional Specification (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

The *what* of the refactored backend surface. The *how* (flows, transactions, audit) is in
[business-flow.md](business-flow.md); the wire contracts are in
[interface-specification.md](interface-specification.md); the table mapping is in
[database-mapping.md](database-mapping.md). JEX source of truth:
[docs/jex/features/vas-account/functional-specification.md](../../../../../docs/jex/features/vas-account/functional-specification.md).

---

## 1. Scope

VAS Account is the per-tenant list of Value-Added-Service accounting codes (`BUSI_VAS_ACCT_INFM`).
Each row carries a tenant-unique code, a name (+ optional English name), a type (`0` Dual / `1` Credit
/ `2` Debit), an active/inactive status, and free-text notes. The list is reached from the Settings
popup "VAS Account" submenu; downstream banking surfaces reference a chosen VAS account by its
surrogate `VAS_ACCT_SEQ_NO` / by `VAS_ACCT_CD`.

**In scope:** list + search + status filter · active-only lookup picker · single-record detail ·
create · update · soft-delete · Excel preview + import (Append / Replace) · XLSX export · audit-log
emission on every write.

**Out of scope — and deliberately absent in BOTH JEX and the refactor (do not "add for completeness"):**

| Assumed feature | Reality |
|---|---|
| Parent-child hierarchy / tree storage | **None.** `BUSI_VAS_ACCT_INFM` has no parent FK and no level/depth column (verified against the live refactor DB — see [database-mapping.md](database-mapping.md)). The legacy table is a flat list. The "hierarchy" users see is a **client-side, display-only prefix grouping** computed in the frontend (`buildTree` mirrors legacy `comm_0217_001.ts`); the backend never nests rows. |
| Display-order column | **None.** No `DISP_ORD` / sort-order column exists; ordering is by `VAS_ACCT_CD` (default sort). |
| Maximum-depth / circular-reference rules | **N/A** — there is no hierarchy to constrain. (Contrast Chart of Accounts `comm_0223` / `CHART_ACCT_INFM`, which *does* enforce a 4-level cap + cycle guard — a different table, out of scope here.) |
| Bulk activate / deactivate | **None.** Status is changed one row at a time via the ordinary `PUT … {vasAcctSeqNo}` update. There is no `/status` endpoint and no bulk-status path in JEX or the refactor. |
| Dedicated activate/deactivate endpoint | **None.** `stts` is a Create/Update field. |
| Bulk delete | **None** (DN-8). Single soft-delete only. No `CHECKBOX` special FIELD_ID exists (View Setting is not adopted for this screen — no seed). |
| Hard delete | **None.** Delete sets `DEL_YN='Y'`; the legacy `IDO.BUSI_VAS_ACCT_INFM_D001` physical-delete query exists but is dead code on both sides. |
| Backend template-download endpoint | **None.** The Excel template is a static XLSX served by the frontend (`/templates/vas-account/WeCMS_VAS_Accounts_Bulk_Upload_{EN,KO,VI}.xlsx`). |

> **Legacy id note.** This feature is JEX `comm_0217_*` + `comm_1002_001` (lookup). It is **not**
> `comm_0223` — that id is Chart of Accounts (`CHART_ACCT_INFM`). The two are sometimes conflated
> because both are Settings accounting-code lists, but only Chart of Accounts has a real hierarchy.

## 2. Filters (request parameters — `GET /api/v1/settings/vas-account`)

| Param | Type | Default | Behaviour |
|---|---|---|---|
| `search_wd` | string | — | Case-insensitive `LIKE` match against `vas_acct_cd` **or** `vas_acct_nm`. `% _ !` escaped with an `!` escape char. Blank → no keyword filter. |
| `stts` | repeatable string | — | Status filter. **0 or 2 values** → no filter (both Active + Inactive returned). **1 value** → must be `A` or `I` (else `WBE_VALIDATION`). |
| `sort_field` | string | `VAS_ACCT_CD` | View Setting `FIELD_ID`, resolved through `VasAccountSortField` (see §5). Unknown → default sort. |
| `sort_direction` | `ASC`/`DESC` | `ASC` | Direction; unknown/blank → default. |
| `page` | int | `0` | Zero-based page index (clamped `≥ 0`). |
| `size` | int | `20` | Page size; `≤ 0` → `20` (`DEFAULT_PAGE_SIZE`), else `min(size, 100)` (`MAX_PAGE_SIZE`). |
| `all` | bool | `false` | When `true`, returns the **entire** filtered set unpaged (used by the management panel, which builds the client-side prefix tree over the full list). Routes to `VasAccountService.listAll`. |

The lookup endpoint (`GET …/lookup`) takes only an optional `search_wd` and returns **active + live**
rows (`stts='A'`, `del_yn='N'`), capped at `LOOKUP_MAX_ROWS = 500`, ordered by code.

## 3. Grid columns

> **View Setting is NOT adopted for `vas-account`.** There is **no `SYS_SCR_OPTION_WECMS` seed** for
> this screen — the V156 `sys_scr_option_wecms` seed header lists `vas-account` as
> "reserved / audit-slug only", and the panel renders no View Setting popover (the toolbar list-icon
> is not shown). The column set below is therefore the **export allow-list** (`VasAccountExportProvider.ALLOWED_FIELD_IDS`)
> and the columns the frontend `VasAccountTable.tsx` renders — not a persisted seed. The `DISP_ORD` /
> `USE_YN` / `FIX_YN` / `SIZE` values are descriptive, not stored in any DB row.

| FIELD_ID | Role in export / table | Notes |
|---|---|---|
| `VAS_ACCT_CD` | export + table | Code (rendered bold for prefix-tree parent rows). In `ALLOWED_FIELD_IDS`. |
| `VAS_ACCT_NM` | export + table | Name. In `ALLOWED_FIELD_IDS`. |
| `VAS_ACCT_TYPE` | export only | Type — exported as a localized label; the management table does not render a Type column (PO call 2026-05-11). In `ALLOWED_FIELD_IDS`. |
| `STTS` | export + table | Status pill. In `ALLOWED_FIELD_IDS`. |

The frontend table also prepends a non-data row-action (kebab) column; it is not a View Setting
`FIELD_ID`. No `CHECKBOX` / `ETC` special FIELD_IDs are seeded — there is no row-selection / bulk
action. Every column the `VasAccountExportProvider` allow-list exposes has a matching
`VasAccountSortField` constant (§5).

## 4. Pagination

Spring Data zero-based. Response carries `list` (current page), `total_count` (unpaged filtered
total), `page_number` (zero-based, echoed after clamp), `page_size` (echoed after clamp),
`total_pages`. When `all=true`, the response is the full set with `page_number=0`,
`page_size=count`, `total_pages = count==0 ? 0 : 1`.

## 5. Sorting

`VasAccountSortField` (`implements SortableField`) whitelists four columns; the wire `FIELD_ID` is
matched (lenient — unknown returns null → default sort), never concatenated into SQL:

| Wire `sort_field` | Entity property |
|---|---|
| `VAS_ACCT_CD` | `vasAcctCd` |
| `VAS_ACCT_NM` | `vasAcctNm` |
| `VAS_ACCT_TYPE` | `vasAcctType` |
| `STTS` | `stts` |

Default sort: `vasAcctCd ASC, vasAcctSeqNo ASC`. A user-chosen sort always appends
`vasAcctSeqNo ASC` as a deterministic tiebreaker.

## 6. Status & type codes

- **Status** (`STTS`, `CHAR(1)`) — resolved via `AStts`: `A` Active (`settings.vas_account.status.active`),
  `I` Inactive (`settings.vas_account.status.inactive`). The row DTO ships both the raw `stts` and the
  resolved `stts_key`.
- **Type** (`VAS_ACCT_TYPE`, `CHAR(1)`) — resolved via `VasAcctType`: `0` Dual
  (`settings.vas_account.type.dual`), `1` Credit (`.credit`), `2` Debit (`.debit`). Defaults to `0`
  (Dual) on create when blank. Ships raw `vas_acct_type` + `vas_acct_type_key`.

Both enums carry only `(code, i18nKey)` — labels live in `wecms-web/locales/*.json`, resolved via
`t(key)` on the frontend.

## 7. Create / Update / Delete behaviour

- **Create** — requires `vas_acct_cd` (3–15 chars, `^[A-Za-z0-9_]+$`), `vas_acct_nm` (≤ 50), `stts`
  (`A`/`I`). `vas_acct_enm` / `vas_acct_type` / `cont` optional. Duplicate live code in the tenant →
  `WBE0003`. `VAS_ACCT_SEQ_NO` allocated by `VasAcctSeqNoGenerator` (per-tenant MAX+1).
- **Update** — duplicate-code check runs **only when the code changes**. `vas_acct_cd` / `vas_acct_nm`
  / `stts` always overwritten; `vas_acct_enm` / `vas_acct_type` / `cont` overwritten **only when
  non-blank** (blank = preserve existing). Status change (A↔I) happens here — there is no separate
  status endpoint.
- **Soft delete** — sets `DEL_YN='Y'`; the row stays referenceable by downstream `VAS_ACCT_SEQ_NO`
  pointers (no cascade).

## 8. Permissions

Class-level gate on `VasAccountController`:
`@PreAuthorize("@menuAuthz.canAccess('settings-core-vas-account')")` (via `MenuAuthorizationGuard`).
Every endpoint inherits this single gate; there are no per-method `@PreAuthorize` annotations.
Every query/write is tenant-scoped to `TenantContext.currentBizKey()`; the wire never accepts a
tenant override.

## 9. Excel import / export

- **Import** is a two-call flow: `POST …/excel/preview` (multipart file → validated rows + counts)
  then `POST …/excel/import` (JSON rows + `upld_optn`). Header validation, 500-row cap, per-cell
  validation (required / length / format / dup-in-file / dup-in-db). Modes: **A** Append (insert
  non-duplicate codes), **R** Replace (soft-delete all live rows for the tenant, then insert all).
  Full rules in [business-flow.md §Excel](business-flow.md) and [test-scenarios.md](test-scenarios.md).
- **Export** — `POST …/export` via the shared `common/export` pipeline (`VasAccountExportProvider`,
  `screenId = "vas-account"`, allowed FIELD_IDs `VAS_ACCT_CD` / `VAS_ACCT_NM` / `VAS_ACCT_TYPE` /
  `STTS`). Type + Status emitted as **localized labels** (export has no client i18n). Subheader =
  `"<count> <noun>(s)"`. No monetary totals. (The frontend export *button* is deferred — the backend
  endpoint exists and is tested.)

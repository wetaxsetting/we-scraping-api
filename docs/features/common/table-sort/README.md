# Table Sort — Backend Feature Hub

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

**Status:** Working — common primitives implemented; per-endpoint adoption is PARTIAL (one whitelist enum per list endpoint, rolled out screen-by-screen).
**Domain / slug:** `common` / `table-sort` (global, cross-cutting).
**Backend package:** `com.kosign.wecms.common.web` (primitives) + a per-domain `*SortField` enum next to each list service.

Table sort is a **cross-cutting query-param contract — it has no endpoint of its own.** Every list
endpoint honours the same two optional query params — `sortField` (a View Setting `FIELD_ID`) and
`sortDirection` (`ASC` / `DESC`) — backed by two `common/web/` primitives plus one per-domain
whitelist enum. The wire string is **never** concatenated into SQL; it is matched against a typed
enum and, when unknown / blank, the service falls back to the endpoint's legacy default `ORDER BY`.
Sort is always **DB-side `ORDER BY`** — never a frontend `Array.sort()`, never a runtime SQL string.

## The contract (one-line summary)

| Param | Source | Validator | On unknown / blank |
|---|---|---|---|
| `sortField` | View Setting `FIELD_ID` (uppercase, e.g. `BANK`, `BALANCE`) | per-domain `*SortField.parse(...)` | resolves to null → default `ORDER BY` |
| `sortDirection` | `ASC` / `DESC` (case-insensitive) | `common/web/SortDirection.parse(...)` | resolves to null → default `ORDER BY` |

Both are `@RequestParam(required = false)` with **no `@Pattern`** — the enum-`parse(...)` step in the
service is the validator. Special FIELD_IDs (`CHECKBOX` / `ETC`) are never in any whitelist.

## Documents

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | Wire `sortField`/`sortDirection` → service parse → `Sort.by(entityProperty).and(DEFAULT_SORT)` → repo → DB `ORDER BY` |
| [functional-specification.md](functional-specification.md) | Whitelist-enum contract, lenient parse → default fallback, special-field exclusion, `SORT_YN`↔whitelist agreement, default-order parity |
| [database-mapping.md](database-mapping.md) | No own table; `FIELD_ID` → `entityProperty()` → DB column mapping; per-domain enum examples |
| [interface-specification.md](interface-specification.md) | The two query params on every list endpoint + the per-domain whitelist enums + default `ORDER BY` per endpoint |
| [migration-guide.md](migration-guide.md) | Legacy `SortHelper` → `common/web/` primitives + per-domain enums |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix (DB-side sort = MATCH; raw-string concat = FORBIDDEN by design; per-endpoint adoption = PARTIAL) |
| [source-code-index.md](source-code-index.md) | `common/web/` map + confirmed per-domain enums + frontend `useTableSort` / `<TableHeaderCell>` + tests |
| [test-scenarios.md](test-scenarios.md) | Cycle, unknown-field → default, special-field inert, SQL-injection-safe |

## Related

- Category hub: [`../README.md`](../README.md)
- Frontend twin: [`../../../../../wecms-web/docs/features/common/table-sort/README.md`](../../../../../wecms-web/docs/features/common/table-sort/README.md)
- View Setting sibling (FIELD_ID alignment): [`../view-setting/README.md`](../view-setting/README.md)
- Excel export sibling (shares the same FIELD_ID + sort contract): [`../excel-export/README.md`](../excel-export/README.md)
- Audit deliverables: [`../../../audits/common-source-index.md`](../../../audits/common-source-index.md)
- Implementation rules: `wecms-api/CLAUDE.md` §"Common table sort — `common/web/`" + §"Screen Option (View Setting)"

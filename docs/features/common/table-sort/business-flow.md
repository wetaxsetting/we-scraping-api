# Table Sort — Business Flow

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

There is **no `table-sort` endpoint**. The flow below runs inside every list endpoint that has
adopted the contract; Account Inquiry (`GET /api/v1/banking/accounts`) is the reference.

## End-to-end flow

```
Frontend list page
  useTableSort() cycle → sortParams { sortField: "BALANCE", sortDirection: "DESC" }  (null in default state)
        │  query: ?sortField=BALANCE&sortDirection=DESC   (both keys omitted when default)
        ▼
Controller  (e.g. AccountController#listAccounts)
  @RequestParam(required=false) sortField        ← passed through verbatim, NO @Pattern
  @RequestParam(required=false) sortDirection     ← passed through verbatim
        ▼
Service  (e.g. AccountService.list(filter, sortField, sortDirection, ...))
  AccountSortField.parse(sortField)   → enum constant | null   (lenient — unknown ⇒ null)
  SortDirection.parse(sortDirection)  → ASC | DESC | null       (lenient — blank/unknown ⇒ null)
  buildSort(field, direction):
      if (field == null || direction == null) return DEFAULT_SORT;          // legacy default ORDER BY
      Sort.Order primary = asc/desc(field.entityProperty());
      return Sort.by(primary).and(DEFAULT_SORT);                            // user key + stable tiebreaker
        ▼
Repository  (Spring Data finder accepting a Sort)
  findByBizKeyAndActvYn(bizKey, "N", sort)  → emits  ... ORDER BY <entityProperty> DESC, prn_seq ASC, ...
        ▼
PostgreSQL  — DB-side ORDER BY (never frontend Array.sort)
```

## Per-step detail

| Step | What happens | Source of truth |
|---|---|---|
| 1. Cycle | Frontend `useTableSort` owns `default → ASC → DESC → default`; emits `sortParams` (null on default) | `wecms-web/hooks/useTableSort.ts` |
| 2. Wire | Two optional query keys; both omitted in default state | `AccountController#listAccounts:98-99` |
| 3. Pass-through | Controller forwards the raw strings, no validation | `AccountController#listAccounts:106` |
| 4. Parse | `*SortField.parse(sortField)` + `SortDirection.parse(sortDirection)` — lenient, null on miss | `AccountSortField.java:53-62`, `SortDirection.java:21-30` |
| 5. Build `Sort` | `buildSort(...)` → `Sort.by(primary).and(DEFAULT_SORT)`, or `DEFAULT_SORT` when either is null | `AccountService.java:581-589` |
| 6. Query | Repository finder takes the `Sort`; Spring Data validates the property against the entity metamodel and emits `ORDER BY` | `AcctMstrRepository#findByBizKeyAndActvYn(..., Sort)` |
| 7. DB | PostgreSQL returns rows already ordered | — |

## Why "always weave in DEFAULT_SORT"

A user-chosen primary key alone is not deterministic when rows tie on that column — the order would
drift across requests and break pagination + snapshot tests. `Sort.by(primary).and(DEFAULT_SORT)`
appends the legacy default order as a stable secondary key. For Account Inquiry:
`DEFAULT_SORT = Sort.by(asc("prnSeq"), asc("bankCd"), asc("acctNo"))` (`AccountService.java:131-132`),
matching legacy `SortHelper#86`'s `prn_seq:asc`; NULL `prnSeq` rows sort last under PostgreSQL's
default ASC NULLS-LAST.

## Default state (no user sort)

When `sortField` / `sortDirection` are absent (or unknown), `buildSort` returns `DEFAULT_SORT` —
the endpoint's legacy default `ORDER BY`. No exception, no 4xx, no echo of user input into a query.
The export path mirrors this through `applyDefaultSortIfNeeded(...)` (`AccountService.java:594-599`)
so the XLSX export and the on-screen list share one ordering.

# Table Sort — Migration Guide (Legacy JEX → Refactor)

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

## Legacy JEX shape

In JEX, list-grid sorting was driven by the helper **`SortHelper`**
(`WECMS DEV/WECMS/src/wabooks/helper/SortHelper.java`) — cited as the source-of-truth for the
Account Inquiry default order at `SortHelper#86` (legacy default `prn_seq:asc` for `fnsh_0001_001`).
The JSP grid emitted `data-sort` attributes per header column (e.g.
`comm_0184_001_view.jsp:155-172` for Contacts), and the IDO query layer composed the `ORDER BY`.

> ⚠️ The legacy `SortHelper` is referenced in `wecms-api/CLAUDE.md` §"Common table sort" and in
> `AccountSortField` / `AccountService` Javadoc as a file:line citation only. Its full internals were
> not re-read this pass; treat `SortHelper#86` as the cited default-order pointer, not a transcribed
> algorithm.

## Refactor shape

| Legacy concept | Refactor replacement |
|---|---|
| `SortHelper` grid-sort helper | `common/web/SortDirection` + `common/web/SortableField` primitives |
| JSP `data-sort` header attributes | View Setting `FIELD_ID` keys (same uppercase identifiers) + frontend `<TableHeaderCell>` |
| IDO `ORDER BY` composition from a sort token | per-domain `*SortField` whitelist enum → `Sort.by(entityProperty).and(DEFAULT_SORT)` |
| Per-grid legacy default sort (`prn_seq:asc`) | per-endpoint `DEFAULT_SORT` constant reproducing the legacy default |
| Legacy WSVC grid ids (`fnsh_0001_001`, `comm_0184_001`) | refactor route slugs + semantic `WEB_SVC_ID` (`banking-account-inquiry-vnd`, `contacts`) |

## What changed (and why)

1. **One global direction enum.** `SortDirection` lives in `common/web/` and is shared across every
   domain. No per-domain direction enum.
2. **Whitelist, not string composition.** The legacy IDO layer could compose an `ORDER BY` from a
   sort token; the refactor forbids concatenating the wire string into SQL. The wire `sortField`
   passes through `*SortField.parse(...)` → `entityProperty()` and Spring Data validates the property
   against the entity metamodel. There is no third path.
3. **Lenient degradation.** Legacy behaviour on a bad sort token was IDO-specific; the refactor
   standardises on "unknown ⇒ default `ORDER BY`, never a 4xx".
4. **FIELD_ID alignment with View Setting.** The sort key and the column-visibility key are the same
   `FIELD_ID`, so a single seed (`SYS_SCR_OPTION_WECMS`) governs both visibility (`USE_YN`) and
   sortability (`SORT_YN`). This is new structure the legacy JSP did not have.
5. **DB-side only.** Sort is always `ORDER BY`; the refactor never reorders rows in Java or on the
   frontend.

## Per-endpoint adoption (not a big-bang)

Adopting the contract on a new endpoint is purely additive — no change to `common/web/`:

1. Write a new `*SortField` enum (one constant per sortable `FIELD_ID`, mapped to its entity
   property).
2. Add the two `@RequestParam(required=false)` params to the controller.
3. Build the `Sort` in the service (`parse` → `Sort.by(primary).and(DEFAULT_SORT)`) and call the
   repository's `Sort`-accepting finder.
4. Ensure the View Setting seed's `SORT_YN` agrees with the new enum (flip `'N'`→`'Y'` in the
   screen's existing seed migration where a backing field now exists).

## Migration-traceability (legacy WSVC ↔ refactor)

| Legacy WSVC / grid | Refactor endpoint | Whitelist enum |
|---|---|---|
| `fnsh_0001_001` (Account Inquiry) | `GET /api/v1/banking/accounts` | `AccountSortField` |
| `comm_0184_001` (Contacts) | `GET /api/v1/settings/contacts` | `ContactSortField` |
| `comm_0223_001` (Chart of Accounts) | `GET /api/v1/settings/chart-of-accounts` | `ChartOfAccountsSortField` |
| e-Invoice listing | `GET /api/v1/addon/einvoices` | `EInvoiceSortField` |
| operation status | `GET /api/v1/operation-status` | `OperationStatusSortField` |

Legacy WSVC ids appear only as traceability prose / file:line citations per the identifier-naming
rule — never as a Java field, REST URI segment, package, or class name.

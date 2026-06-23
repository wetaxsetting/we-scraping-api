# Transaction History (VND) — Functional Specification (Backend)

The *what* of the refactored backend surface. The *how* is in
[business-flow.md](business-flow.md); the wire contracts are in
[interface-specification.md](interface-specification.md). JEX source of truth:
[docs/jex/features/transaction-history-vnd/functional-specification.md](../../../../../../docs/jex/features/transaction-history-vnd/functional-specification.md).

---

## 1. Scope

Per-account inquiry of demand-deposit (current/checking) credit/debit transactions for one VND bank
account, with an operational confirmation + classification workflow and XLSX export. Currency-agnostic
endpoint — VND vs FCY is decided by the originating account, not by the service.

In scope (implemented): list, summary, detail, confirm/undo, classify (reporting account / purpose /
VAS / contact), pagination, totals, View Setting, export.
Out of scope (owned elsewhere): bank synchronization (Account Inquiry), payment-matching execution
(Smart Organizer).

## 2. Filters (request parameters)

| Filter | Param | Values | Applied |
|---|---|---|---|
| Date range — from | `from` | `yyyyMMdd` (optional) | DB-side (`TRSC_DT BETWEEN`) |
| Date range — to | `to` | `yyyyMMdd` (optional) | DB-side |
| Transaction type | `type` | `DEPOSIT` / `WITHDRAW` (omit = all) | in-memory (`INOUT_DV`) |
| Confirmation status | `cnfm_yn` | `Y` / `N` (omit = all) | in-memory (`CNFM_YN`) |
| Keyword | `keyword` | substring (optional) | in-memory (`RMRK` / `BANK_REF_NO`) |

Validation: `from ≤ to` (when both present); unknown `type`/`cnfm_yn` degrade to "no filter" rather
than erroring. Account scope comes from the `{acctSeqNo}` path segment (composite `BANK_CD_ACCT_NO`);
tenant scope from `BIZ_KEY`.

## 3. Grid columns (View Setting `banking-transaction-history`)

The seed (V155 → realigned V169, contact/confirmed-by added V171) registers the column set; the
frontend renders the visible/ordered subset. Column keys (`FIELD_ID`, uppercase, stable):

| FIELD_ID | Meaning | Source | Format |
|---|---|---|---|
| `CHECKBOX` | row-selection (special, `DISP_ORD=0`, locked) | — | special |
| `TRSC_DTM` | transaction date + time | `TRSC_DT` + `TRSC_TM` | datetime |
| `RMRK` | remark / description | `RMRK` | text |
| `DEPOSIT_AMT` | deposit amount | `TRSC_AMT` when IN | amount |
| `WITHDRAW_AMT` | withdrawal amount | `TRSC_AMT` when OUT | amount |
| `BAL` | running balance | `BAL` | amount |
| `VAS_ACCT_CD` | VAS account | `ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_CD` | text |
| `RCV_PAY_USAG_CD` / `PURPOSE` | purpose code | `RCV_PAY_USAG_CD` | text |
| `RCV_PAY_TRCO_NM` / `CONTACT_NM` | contact (counterparty) | `RCV_PAY_TRCO_NM` | text |
| `CONF_YN` | confirmation flag | `CNFM_YN` | text |
| `CONFIRMED_BY` | confirmed-by name | `CNFM_BY_USER_NM` | text |
| `BANK_REF_NO` | bank reference | `BANK_REF_NO` | text |

> The View Setting seed went through several alignment passes (V155 initial 12-field set → V167
> unlock `TRSC_DTM` → V169 realign to the legacy on-screen table → V171 add `CONTACT_NM` /
> `CONFIRMED_BY` → V181 defensive `TRSC_DTM` unlock in the user table). The current state is the
> authoritative seed; see [database-mapping.md](database-mapping.md) and the migration headers.

## 4. Pagination

Server returns the full filtered set with `TOTAL_COUNT`; **pagination is client-side**
(`usePagination`, page sizes 10 / 20 / 50 / 100, default 10) — parity with the legacy page which
also paged the in-memory result set. There is no `page`/`size` wire parameter on the list endpoint.
See [refactor-gap-analysis.md](refactor-gap-analysis.md#pagination) for the parity note.

## 5. Sorting

- **Default order:** most-recent-first (`TRSC_DT DESC, TRSC_TM DESC`) — DB-side, matches legacy.
- **Per-column sort:** the View Setting carries a `SORT_YN` flag and the grid infrastructure is
  ready, but per-column header sorting is **not yet wired** (frontend headers render
  `sortable={false}`). This is a documented PARTIAL — see
  [refactor-gap-analysis.md](refactor-gap-analysis.md#sorting). When wired, it will route through the
  shared `useTableSort` + a backend `TransactionSortField` whitelist, exactly as Account Inquiry does.

## 6. Totals

`TransactionListResponse` carries `DEPOSIT_TOTAL` and `WITHDRAW_TOTAL` (sum across the filtered set);
`TransactionSummaryResponse` adds `confirmedCount` / `unconfirmedCount`. The footer count + currency
totals render from these. Export's backend-computed subheader uses the same numbers.

## 7. Confirmation & classification workflow

- Confirm / Undo: toggles `CNFM_YN`; confirmed rows stay white, unconfirmed rows tinted yellow on the
  grid; the per-row action button flips Confirm ↔ Undo.
- Classify: assign/clear Reporting Account, Purpose, VAS Account, Contact — each validated against its
  source table; idempotent clears.

## 8. Permissions

Every endpoint is JWT-protected and gated by the menu-authorization guard for the Transaction-History
menu codes (`@menuAuthz` over the VND + FCY inquire-transactions permission codes), stacked on the
global `/api/v1/**` authentication. An unauthorized user receives the standard `WBE1003` /
401 envelope. Tenant isolation is enforced by `BIZ_KEY` on every query — the export must never cross
tenants.

## 9. Export

XLSX via the shared `common/export` pipeline (`POST /api/v1/banking/accounts/transactions/export`).
Backend is the single producer (Apache POI); the frontend ships filter + column-spec context and
saves the blob. Columns come from the live View Setting ∩ the provider allow-list; hidden / special
columns never leak. Five columns are currently value-stubs (see
[business-flow.md §7](business-flow.md#7-export-generation-flow)).

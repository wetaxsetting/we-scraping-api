# Table Sort — Test Scenarios

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

Scenarios are written against the contract; the reference endpoint is
`GET /api/v1/banking/accounts` (`AccountSortField`). They apply to every adopting endpoint by
substituting its whitelist enum + allowed FIELD_IDs.

## Cycle (default → ASC → DESC → default)

| # | Request | Expected ORDER BY |
|---|---|---|
| C1 | (no sort params) | `DEFAULT_SORT` = `prn_seq ASC, bank_cd ASC, acct_no ASC` |
| C2 | `?sortField=BALANCE&sortDirection=ASC` | `bal ASC` + `DEFAULT_SORT` tiebreaker |
| C3 | `?sortField=BALANCE&sortDirection=DESC` | `bal DESC` + `DEFAULT_SORT` tiebreaker |
| C4 | (sort cleared by 3rd click → no params) | back to `DEFAULT_SORT` |

The cycle itself is owned by the frontend `useTableSort` hook; the backend only sees C1–C3 wire
states. C4 is wire-identical to C1.

## Unknown / blank field → default

| # | Request | Expected |
|---|---|---|
| U1 | `?sortField=NONSENSE&sortDirection=DESC` | not in whitelist → null → `DEFAULT_SORT` (HTTP 200) |
| U2 | `?sortField=BALANCE&sortDirection=UP` | direction unknown → null → `DEFAULT_SORT` |
| U3 | `?sortField=BALANCE` (no direction) | direction null → `DEFAULT_SORT` (both must be non-null) |
| U4 | `?sortField=&sortDirection=` | blank → null → `DEFAULT_SORT` |
| U5 | `?sortField=balance&sortDirection=desc` | case-insensitive → `bal DESC` (accepted) |

**No 4xx in any of U1–U4** — the contract degrades gracefully for stale clients.

## Special-field inert

| # | Request | Expected |
|---|---|---|
| S1 | `?sortField=CHECKBOX&sortDirection=ASC` | `CHECKBOX` not in `AccountSortField` → `DEFAULT_SORT` |
| S2 | `?sortField=ETC&sortDirection=DESC` | `ETC` not in any whitelist → `DEFAULT_SORT` |
| S3 | `?sortField=VAS_ACCOUNT&sortDirection=ASC` | no `AccountSortField` constant (V41 `SORT_YN='N'`) → `DEFAULT_SORT` |

## SQL-injection-safe

| # | Request | Expected |
|---|---|---|
| I1 | `?sortField=BALANCE; DROP TABLE acct_mstr--&sortDirection=ASC` | unknown enum name → null → `DEFAULT_SORT`; the wire string is never concatenated into SQL |
| I2 | `?sortDirection=ASC) UNION SELECT ...` | unknown direction → null → `DEFAULT_SORT` |
| I3 | `?sortField=bal` (entity property, not FIELD_ID) | not a FIELD_ID enum name → null → `DEFAULT_SORT` (the property is never accepted directly off the wire) |

The whitelist enum + `Sort.by(entityProperty())` is the only path from wire to column — Spring Data
validates the property against the entity metamodel, so even a valid-looking entity property passed
as `sortField` is rejected (I3) because it is not a whitelist **constant name**.

## Determinism / pagination

| # | Scenario | Expected |
|---|---|---|
| D1 | Two rows tie on `BALANCE` | secondary `DEFAULT_SORT` keys (`prn_seq, bank_cd, acct_no`) keep a stable order across requests |
| D2 | Page 1 then page 2 with the same sort | no row appears twice / drops out (stable total order) |
| D3 | Export (unpaged) with `?sortField=BALANCE&sortDirection=DESC` | XLSX rows in the same order as the on-screen list (`applyDefaultSortIfNeeded`) |

## Existing automated coverage (verified file list)

| Test | Covers |
|---|---|
| `…/payment/vendorpayment/service/VendorPaymentSortFieldTest.java` | enum parse + whitelist membership |
| `…/payment/internaltransfer/service/InternalTransferSortFieldTest.java` | enum parse + whitelist membership |
| `…/payment/cardtransactions/service/CardTrnsHstrSortFieldTest.java` | enum parse + whitelist membership |
| `…/setting/vasaccount/service/VasAccountSortFieldTest.java` | enum parse + whitelist membership |
| `…/common/web/RequestResponseLoggingFilterTest.java` | `common/web/` filter (adjacent) |
| service tests (`ContactsServiceTest`, `FirmBankingHistoryServiceTest`, `ErpTrnsHistoryServiceTest`, `EInvoiceServiceTest`) | sort applied end-to-end through the service |

## Gaps

- No dedicated `AccountSortFieldTest` was found in this pass (Account Inquiry sort is exercised
  indirectly via `AccountService` paths). ⚠️ confirm before claiming full coverage for the reference
  endpoint.
- SQL-injection scenarios (I1–I3) are guaranteed by construction (no string concat) rather than by an
  explicit negative test — add one per endpoint when hardening.

# Table Sort — Interface Specification

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

Table sort exposes **no endpoint of its own**. It adds two optional query params to every list
endpoint that has adopted the contract.

## The two query params (every adopting list endpoint)

| Param | Type | Required | Validation | Example |
|---|---|---|---|---|
| `sortField` | string (View Setting `FIELD_ID`, uppercase) | no | per-domain `*SortField.parse(...)` — **no `@Pattern`** | `BALANCE` |
| `sortDirection` | string (`ASC` / `DESC`, case-insensitive) | no | `common/web/SortDirection.parse(...)` — **no `@Pattern`** | `DESC` |

Both omitted ⇒ default `ORDER BY`. Either unknown / blank ⇒ default `ORDER BY` (no 4xx).

### Controller wiring (reference — `AccountController#listAccounts:94-106`)

```java
@GetMapping("/accounts")
public ApiResponse<AccountListResponse> listAccounts(
        @RequestParam(value = "currency",      required = false) String currency,
        @RequestParam(value = "bankCd",        required = false) String bankCd,
        @RequestParam(value = "keyword",       required = false) String keyword,
        @RequestParam(value = "sortField",     required = false) String sortField,
        @RequestParam(value = "sortDirection", required = false) String sortDirection,
        ...) {
    return ApiResponse.ok(accountService.list(filter, sortField, sortDirection, asOfDate, page, size));
}
```

Controllers stay thin: forward the raw strings to the service. No regex, no `@Pattern` on these two
params — the service's enum-`parse(...)` is the validator.

## Per-domain whitelist enums + allowed FIELD_IDs + default ORDER BY

Each list endpoint registers one whitelist enum next to its service. The table below is the verified
contract for the enums confirmed in this pass.

| Endpoint | Whitelist enum | Allowed `sortField` (FIELD_IDs) | Default `ORDER BY` |
|---|---|---|---|
| `GET /api/v1/banking/accounts` | `AccountSortField` | `BANK`, `ACCOUNT_NICKNAME`, `BALANCE`, `AVAILABLE_BALANCE`, `LAST_UPDATE` | `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` (legacy `fnsh_0001_001` / `SortHelper#86`; NULL `prnSeq` last) |
| `GET /api/v1/settings/contacts` | `ContactSortField` | `HAND_TRCO_KEY`, `TRCO_CMNM`, `TRCO_TYPE`, `TRCO_TAX_ID` | per `ContactsService` (legacy `comm_0184_001`) ⚠️ exact default not re-verified this pass |
| `GET /api/v1/settings/chart-of-accounts` | `ChartOfAccountsSortField` | `CHART_ACCT_CD`, `CHART_ACCT_NM_EN`, `STTS`, `MOD_DTM` | `CHART_ACCT_CD ASC` (legacy `CHART_ACCT_INFM_R001`) |
| `GET /api/v1/addon/einvoices` | `EInvoiceSortField` | `DATE_TIME`, `INVOICE_NO`, `BUYER`, `CURRENCY`, `TOTAL`, `STATUS`, `TAX`, `PRETAX` | `ISSU_DT DESC, REG_DTM DESC` (per `EInvoiceSortField` Javadoc) |
| `GET /api/v1/operation-status` | `OperationStatusSortField` | `COMPANY`, `ADDRESS`, `OPERATION_STATUS`, `LAST_UPDATE` | per `OperationStatusService` ⚠️ exact default not re-verified this pass |

> Additional whitelist enums exist in the tree (28 total `*SortField.java` files as of 2026-06-23 —
> payment scheduled/result variants, card transactions, exchange rate, firm-banking history, VAS
> account, etc.). They follow the identical pattern; see `source-code-index.md` for the full list.
> Their allowed FIELD_IDs / defaults are not transcribed here — read the enum + its service.

## Common primitives (the only shared types)

| Type | File | Surface |
|---|---|---|
| `SortDirection` | `common/web/SortDirection.java` | enum `{ASC, DESC}` + `static SortDirection parse(String)` (null on blank/unknown) |
| `SortableField` | `common/web/SortableField.java` | interface — `String entityProperty()` |

There is exactly **one** `SortDirection` for the whole product — never a per-domain
`AccountSortDirection` / `PaymentSortDirection`.

## Error behaviour

There is no sort-specific error code. Unknown / blank inputs degrade silently to the default
`ORDER BY`. Throwing `IllegalArgumentException` / `BusinessException` on an unknown `sortField` is a
**forbidden pattern** (caught at review) — a stale frontend bundle must never surface a 4xx for a
sort param.

## Authz

Sort inherits the host endpoint's authorization. The two params add no new auth surface; whatever
JWT + `@menuAuthz` gate guards the list endpoint also guards its sorted variant.

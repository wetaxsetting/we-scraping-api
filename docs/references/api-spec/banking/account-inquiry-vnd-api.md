# Banking > Account Inquiry (VND) — Backend API Design

**Refactor screen ID:** `banking-account-inquiry-vnd`
**Feature spec:** [../../features/banking/account-inquiry-vnd.md](../../../features/banking/current-savings-vnd/account-inquiry/README.md)
**JEX reference:** `fnsh_0001_001_r001` (read-only — see WSVC.fnsh_0001_001_r001.xml)

---

## Endpoints

| Method | Path | Auth | Returns | Used by |
|---|---|---|---|---|
| GET | `/api/v1/banking/accounts` | Protected (JWT) | `ApiResponse<AccountListResponse>` | List render, sort, refresh |
| POST | `/api/v1/banking/accounts/export` | Protected (JWT) | XLSX byte stream | Excel button |
| GET | `/api/v1/banking/accounts/{acctSeqNo}/transactions` | Protected (JWT) | `ApiResponse<TransactionListResponse>` | Transaction History page (sibling — not part of this screen) |

Controller: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java`
Service: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java`
Repository: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java`
Entity: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java`

---

## `GET /api/v1/banking/accounts`

### Request params (query string)

| Param | Type | Required | Allowed values | Default | Notes |
|---|---|---|---|---|---|
| `currency` | string | no | `VND`, `FCY`, `ALL`, or any literal currency code (`USD`, `KRW`, …) | `ALL` | `VND` page sends `VND`. `FCY` page sends `FCY` (= "all non-VND"). |
| `bankCd` | string | no | Legacy `BANK_CD` (e.g. `11704001`) | (no filter) | Exact match. Mirrors JEX `BANK_CD` input. |
| `keyword` | string | no | Free text | (no filter) | Case-insensitive partial match against the **formatted** account nickname (output of `wb_format_acct_nick_nm` — the same string the user sees in the table cell). Mirrors JEX `SRCH_WD` exactly: `T.ACCT_NICK_NM ILIKE '%' || ? || '%'` where `T.ACCT_NICK_NM` is the inner-subquery alias for the formatted value. `%` / `_` / `!` in user input are escaped server-side and the JPQL uses `ESCAPE '!'`. |
| `sortField` | string | no | View Setting FIELD_ID — whitelisted via `AccountSortField` enum | (default sort) | Unknown values fall back to default sort |
| `sortDirection` | string | no | `ASC`, `DESC` | `ASC` | Case-insensitive |
| `page` | int | no | `0..N` (zero-based) | `0` | Spring Data convention. Frontend converts to one-based for display. |
| `size` | int | no | `1..100` | `10` | Clamped to `AccountService.MAX_PAGE_SIZE = 100`. `<= 0` falls back to default. Matches the frontend `PAGE_SIZE_OPTIONS[0]` so dropdown selector and initial fetch agree. |

Default sort: `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` (NULLs last on `PRN_SEQ` per PostgreSQL default).

### Response — `AccountListResponse`

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "..." },
  "data": {
    "accounts": [ /* AccountRow[] — page slice */ ],
    "total_count": 42,
    "balance": "1234567890",
    "available_bal": "1234500000",
    "totals_by_ccy": [
      { "ccy_cd": "VND", "count": 42, "balance": "1234567890", "available_bal": "1234500000" }
    ],
    "page_number": 0,
    "page_size": 10,
    "total_pages": 5,
    "banks_available": [
      { "bank_cd": "11704001", "bank_nm": "Vietin Bank" },
      { "bank_cd": "11804001", "bank_nm": "BIDV Bank" },
      { "bank_cd": "33802001", "bank_nm": "Agribank" }
    ]
  }
}
```

Pagination semantics:
- `accounts` is the rows on the requested page (length ≤ `page_size`). Export endpoint returns the full filtered list (`Pageable.unpaged()`).
- `total_count` is the unpaged filtered total — drives the "TOTAL ACCOUNTS" pill and the pagination chrome. Does **not** collapse to the page slice.
- `balance` / `available_bal` / `totals_by_ccy` are aggregated over the full filtered result via a separate JPQL aggregation query (`AcctMstrRepository.aggregateByCcy`) — they stay stable across pagination and never re-compute from the page slice.
- `page_number` is zero-based per Spring Data convention.
- `banks_available` is the canonical bank list for the dropdown — distinct `BANK_CD`s scoped to tenant + currency only. **It is intentionally NOT narrowed by `bankCd` or `keyword`** so the dropdown option set stays stable as the user filters (selecting one bank never removes the others). Names resolved via `BankCdResolver` reading `BANK_INFM.RPRS_BANK_NM`. Sourced from `AcctMstrRepository.findDistinctBankCds`.

### `AccountRow` fields

JSON keys are lowercase snake_case via the global Jackson `SNAKE_CASE` naming strategy. Java fields are camelCase; the wire names below mirror the legacy JEX columns 1:1, just lowercased.

| Field | Type | Source | Notes |
|---|---|---|---|
| `acct_seq_no` | string | synthetic — `BANK_CD + "_" + ACCT_NO` | Stable client-side row key |
| `bank_cd` | string | `ACCT_MSTR.BANK_CD` | |
| `bank_nm` | string | resolved bank name | Service joins/looks up the bank's display name |
| `acct_no` | string | `ACCT_MSTR.ACCT_NO` | |
| `acct_type_cd` | string | `ACCT_MSTR.ACCT_TYPE_CD` | `01`=Current, `02`=Savings, … |
| `acct_type_nm` | string | resolved | "Current Account" / "Savings Account" / "Fixed Deposit" |
| `ccy_cd` | string | `ACCT_MSTR.CCY_CD` | Always `VND` for this screen's rows |
| `acct_nick_nm` | string | `ACCT_MSTR.ACCT_NICK_NM` | User-assigned nickname |
| `bal` | string | `ACCT_MSTR.BAL` | Stringified to preserve precision; client formats |
| `real_amt` | string | `ACCT_MSTR.REAL_AMT` | Available balance |
| `acct_bal_inq_stts` | string | `ACCT_MSTR.ACCT_BAL_INQ_STTS` | Success codes: `00000000`, `42110000` |
| `acct_bal_upd_dtm` | string | `ACCT_MSTR.ACCT_BAL_UPD_DTM` | Legacy `YYYYMMDDHHmmss` format |

---

## `POST /api/v1/banking/accounts/export`

### Request body — `ExcelExportRequest` shape

```json
{
  "screenId": "banking-account-inquiry-vnd",
  "currency": "VND",
  "filters": {
    "bankName": "VCB",
    "keyword": "saigon"
  },
  "sort": { "sortField": "BANK", "sortDirection": "ASC" },
  "columns": [ /* ACCOUNT_INQUIRY_EXPORT_COLUMNS */ ],
  "filename": "account_inquiry_2026-05-03",
  "sheetTitle": "Account Inquiry (VND)"
}
```

### Response

- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="account_inquiry_2026-05-03.xlsx"`
- Body: streamed XLSX (Apache POI SXSSF — backend never buffers full sheets in memory)

### Server-side flow

1. `ExportableScreenRegistry` looks up the provider for `screenId`. Unknown ID → `WBE2010` ("Excel export is not configured for this screen").
2. `AccountInquiryVndExportProvider` (subclass of `AccountInquiryExportProvider`) sets `defaultCurrency = "VND"`.
3. Provider re-invokes `AccountService.list(currency, sortField, sortDirection)` — list and export share **one** query path. No drift.
4. Bank-name + keyword filters applied in-memory (matches frontend filtering semantics).
5. Subheader composed: `Total Accounts: N · Total Balance: …₫ · Available: …₫`.
6. `ExcelWorkbookWriter` streams rows to the response.

---

## `BIZ_KEY` logic (tenant isolation)

- `JwtAuthenticationFilter` populates `TenantContext.currentBizKey()` from the JWT claim.
- `AccountService.list()` reads `TenantContext.currentBizKey()` and passes it to every repository call.
- `AcctMstrRepository.findByBizKeyAndCcyCdAndActvYn(bizKey, ccyCd, actvYn, sort)` — `BIZ_KEY` is the **first** discriminator on the composite key `(BIZ_KEY, BANK_CD, ACCT_NO)`, so the index is fully exploited.
- `BIZ_KEY` is **never** accepted from request body or query — it is derived server-side from the authenticated principal. Refactor config rejects `%` wildcards (see `TenantProperties`).

Single-tenant R&D: pinned to `BIZ_KEY = VN_VNM0004_000001` via `wecms.tenant.biz-key`.

---

## Filtering (server-side, Sprint 2 parity work)

All filters compose into a single JPQL `findFiltered` query in `AcctMstrRepository`. Optional predicates use the `(:param IS NULL OR …)` pattern so one query handles every combination without a Specification graph.

| Filter | Where applied | Why |
|---|---|---|
| `BIZ_KEY` | Repository (JPQL `WHERE`) | Index-friendly; non-negotiable tenant scope. First column of the composite PK `(BIZ_KEY, BANK_CD, ACCT_NO)`. |
| `ACTV_YN = 'Y'` | Repository (JPQL `WHERE`) | Soft-delete equivalent — closed accounts stay in the table. |
| `CCY_CD = ?` | Repository (when `currency=VND` or a literal CCY) | Single-currency exact match. |
| `CCY_CD <> ?` | Repository (when `currency=FCY`) | "All non-VND" exclusion. |
| `BANK_CD = ?` | Repository (when `bankCd` provided) | Exact-match — mirrors JEX `BANK_CD`. |
| `LOWER(COALESCE(wb_format_acct_nick_nm(...), '')) LIKE ? ESCAPE '!'` | Repository (when `keyword` provided) | Matched against the **formatted** nickname (output of the migrated `wb_format_acct_nick_nm` PL/pgSQL function). Mirrors JEX `FnshHelper#fnsh_0001_001_r001` line 470. The service lower-cases + escapes `%`/`_`/`!` + wraps with `%…%` before binding. |

The list endpoint and the Excel export both call `AccountService.list(filter, …)` — list uses a `PageRequest` and export uses `Pageable.unpaged()` so they share one filter chain and one query path. No duplicate filter logic, no in-memory pass on the export.

Sort is whitelisted via `AccountSortField` enum — unknown values fall back to default sort, never propagated to SQL as raw input.

Per-currency totals + scalar `BALANCE`/`AVAILABLE_BAL` come from a sibling aggregation query `AcctMstrRepository.aggregateByCcy` running the **same WHERE chain** without pagination. The two queries are guaranteed to agree because they share parameter bindings, not because they share rows in memory.

---

## Error handling

| Condition | Code | Behavior |
|---|---|---|
| No JWT / expired JWT | (403/401) | `JwtAuthenticationEntryPoint` returns 401 envelope |
| Unknown `screenId` on export | `WBE2010` | "Excel export is not configured for this screen" |
| Invalid path params (`webSvcId` / `dymAreaId` regex) | `WBE2002` | `@Pattern` violation → `ConstraintViolationException` → handler |
| Unexpected runtime | `WBE9999` | `GlobalExceptionHandler` maps generic `Exception` |
| Bean validation failure on request body | `WBE2002` | `MethodArgumentNotValidException` → handler |

`ApiResponse<T>` envelope is enforced by the controller advice — controllers throw `BusinessException("WBExxxx", args…)` and never construct envelopes themselves.

`ConstraintViolationException` registered in `GlobalExceptionHandler` (added in V32 cutover MR).

---

## Cross-references

- Feature spec: [../../features/banking/account-inquiry-vnd.md](../../../features/banking/current-savings-vnd/account-inquiry/README.md)
- Frontend page: [../../frontend/banking/account-inquiry-vnd-page.md](../../../../../wecms-web/docs/features/banking/current-savings-vnd/account-inquiry/README.md)
- DB design: [../../database/banking/account-inquiry-vnd-db.md](../../database/banking/account-inquiry-vnd-db.md)
- Cross-feature API mapping: [../api-mapping.md](../api-mapping.md)
- Service-mapping (JEX → Spring): [../../migration/service-mapping.md](../../migration/service-mapping.md)

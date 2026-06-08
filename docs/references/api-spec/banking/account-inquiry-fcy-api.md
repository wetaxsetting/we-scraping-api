# Banking > Account Inquiry (FCY) — Backend API Design

**Refactor screen ID:** `banking-account-inquiry-fcy`
**Feature spec:** [../../features/banking/account-inquiry-fcy.md](../../../features/banking/current-savings-fcy/account-inquiry.md)
**JEX reference:**
- Live FCY page in JEX is **`fnsh_0005_001` (USD-only)** — separate WSVC, separate view JSP, separate TS driver. The action JSP (`fnsh_0005_001_r001_act.jsp`) invokes `FnshHelper.fnsh_0001_001_r001(..., CcyCd.USD)`; the TS driver hardcodes `CCY_CD: "USD"`.
- The legacy VND page is `fnsh_0001_001_r001`, which calls the same helper with `CcyCd.VND`. Both share `FnshHelper.fnsh_0001_001_r001` but with distinct `(ccy_cd, acct_type_cd IN (...))` predicates.
- **There is no legacy KRW account-list page.** The refactor's "all non-VND" semantic is a deliberate enhancement, not a port — see [`account-inquiry-fcy.md`](../../../features/banking/current-savings-fcy/account-inquiry.md) → "Refactor enhancement".

The refactor consolidates the JEX twin pair onto **one endpoint** (`GET /api/v1/banking/accounts`) with a `currency` query param distinguishing them. **One export controller endpoint** (`POST /api/v1/banking/accounts/export`) with **per-screen `ExportRowProvider` strategies** distinguishes the two via `screenId`.

---

## Endpoints

| Method | Path | Auth | Returns | Used by |
|---|---|---|---|---|
| GET | `/api/v1/banking/accounts` | Protected (JWT) | `ApiResponse<AccountListResponse>` | List render, sort, filter, pagination, refresh — same controller method as VND |
| POST | `/api/v1/banking/accounts/export` | Protected (JWT) | XLSX byte stream | Excel button — `screenId="banking-account-inquiry-fcy"` routes to `AccountInquiryFcyExportProvider` |
| GET | `/api/v1/banking/accounts/{acctSeqNo}/transactions` | Protected (JWT) | `ApiResponse<TransactionListResponse>` | Transaction History page (sibling — not part of this screen) |

Controller: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java` *(shared with VND)*
Service: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java` *(shared with VND)*
Repository: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java` *(shared with VND)*
Entity: `wecms-api/src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java` *(shared with VND)*
**FCY-only export provider:** `wecms-api/src/main/java/com/kosign/wecms/domain/banking/export/AccountInquiryFcyExportProvider.java`

---

## `GET /api/v1/banking/accounts` (FCY mode)

### Request params (query string)

Same shape as the VND page; FCY-specific values called out.

| Param | Type | Required | Allowed values | Default | Notes |
|---|---|---|---|---|---|
| `currency` | string | no | `VND`, `FCY`, `ALL`, or any literal currency code (`USD`, `KRW`, `EUR`, …) | `ALL` | **FCY page sends `FCY`** when the in-page currency dropdown is on "All Currency". Sends a concrete CCY (`USD`, `KRW`, …) when the user has narrowed via the dropdown. |
| `bankCd` | string | no | Legacy `BANK_CD` (e.g. `11704001`) | (no filter) | Exact match. Mirrors JEX `BANK_CD` input. |
| `keyword` | string | no | Free text | (no filter) | Case-insensitive partial match against the formatted account nickname (output of `wb_format_acct_nick_nm`). Mirrors JEX `SRCH_WD` exactly. `%`/`_`/`!` escaped server-side; JPQL uses `ESCAPE '!'`. |
| `sortField` | string | no | View Setting FIELD_ID — whitelisted via `AccountSortField` enum | (default sort) | Unknown values fall back to default sort. Whitelist: `BANK`, `ACCOUNT_NICKNAME`, `BALANCE`, `AVAILABLE_BALANCE`, `LAST_UPDATE`. |
| `sortDirection` | string | no | `ASC`, `DESC` | `ASC` | Case-insensitive |
| `page` | int | no | `0..N` (zero-based) | `0` | Spring Data convention |
| `size` | int | no | `1..100` | `10` | Clamped to `MAX_PAGE_SIZE = 100` |

Default sort (when `sortField` / `sortDirection` are absent or unknown): `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` (NULLs last on `PRN_SEQ` per PostgreSQL default). Same as VND — the legacy `fnsh_0001_001` ordering applies regardless of currency.

### Currency resolution (`AccountService#resolve`)

| Wire `currency` | Repository call | Effective WHERE clause |
|---|---|---|
| `VND` | `findByBizKeyAndCcyCdAndActvYn(bizKey, "VND", "Y", sort)` | `WHERE BIZ_KEY=? AND CCY_CD='VND' AND ACTV_YN='Y'` |
| **`FCY`** | **`findByBizKeyAndCcyCdNotAndActvYn(bizKey, "VND", "Y", sort)`** | **`WHERE BIZ_KEY=? AND CCY_CD <> 'VND' AND ACTV_YN='Y'`** |
| `ALL` | `findByBizKeyAndActvYn(bizKey, "Y", sort)` | `WHERE BIZ_KEY=? AND ACTV_YN='Y'` |
| literal CCY (`USD`, `KRW`, `EUR`, …) | `findByBizKeyAndCcyCdAndActvYn(bizKey, <CCY>, "Y", sort)` | `WHERE BIZ_KEY=? AND CCY_CD=<CCY> AND ACTV_YN='Y'` |

Filtered variants (`findFiltered`) follow the same currency-predicate selection inside a single JPQL with optional `(:bankCd IS NULL OR …)` / keyword predicates composed in.

### Response — `AccountListResponse`

Same shape as VND. The FCY-relevant fields:

```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "..." },
  "REC": {
    "ACCOUNTS": [ /* AccountRow[] — page slice, mixed CCYs when wire=FCY */ ],
    "TOTAL_COUNT": 7,
    "BALANCE": "...",
    "AVAILABLE_BAL": "...",
    "TOTALS_BY_CCY": [
      { "CCY_CD": "USD", "TOTAL_COUNT": 4, "BALANCE": "12931.38",       "AVAILABLE_BAL": "12931.38" },
      { "CCY_CD": "KRW", "TOTAL_COUNT": 2, "BALANCE": "0",              "AVAILABLE_BAL": "0" },
      { "CCY_CD": "EUR", "TOTAL_COUNT": 1, "BALANCE": "1234.56",        "AVAILABLE_BAL": "1234.56" }
    ],
    "PAGE_NUMBER": 0,
    "PAGE_SIZE": 10,
    "TOTAL_PAGES": 1,
    "BANKS_AVAILABLE": [
      { "BANK_CD": "11704001", "BANK_NM": "Vietin Bank" },
      { "BANK_CD": "11804001", "BANK_NM": "BIDV Bank" }
    ]
  }
}
```

Pagination + aggregation semantics (identical to VND):
- `ACCOUNTS` is the rows on the requested page (length ≤ `PAGE_SIZE`). Export bypasses pagination via `Pageable.unpaged()`.
- `TOTAL_COUNT` is the unpaged filtered total — drives the pagination chrome and the cumulative count.
- `BALANCE` / `AVAILABLE_BAL` / `TOTALS_BY_CCY` come from a sibling aggregation query (`AcctMstrRepository.aggregateByCcy`) running the **same WHERE chain** without pagination. Stable across pages.
- `BANKS_AVAILABLE` is the canonical bank list for the dropdown — distinct `BANK_CD`s scoped to tenant + currency only. **Not narrowed by `bankCd` or `keyword`** so the option set stays stable as the user filters.

#### `TOTALS_BY_CCY` semantics on the FCY page

- **Wire `currency=FCY`** ⇒ one row per non-VND CCY found in the filtered result (typically `USD`, `KRW`, possibly `EUR` / `JPY`). The frontend renders one summary tile per row.
- **Wire `currency=<CCY>` (concrete narrow)** ⇒ a single row in the selected CCY. Frontend renders a single tile.
- Mixed-currency arithmetic (sum of USD + KRW + …) is **never** computed server-side — there is no `BALANCE` line that aggregates across CCYs. The scalar `BALANCE` / `AVAILABLE_BAL` fields are present for legacy-callers parity but the FCY frontend does not consume them when `TOTALS_BY_CCY` is non-empty.

### `AccountRow` fields

> **Casing corrected 2026-06-06 (source-verified).** JSON keys are **lowercase
> `snake_case`**, emitted by the global Jackson `SNAKE_CASE` strategy
> ([`JacksonConfig`](../../../../src/main/java/com/kosign/wecms/config/JacksonConfig.java)).
> `AccountRow.java` carries **no** `@JsonProperty` — the earlier
> "UPPER_SNAKE via `@JsonProperty`" claim was wrong. The wire field names mirror
> the legacy DB columns **1:1 but lowercased** (`acct_seq_no`, `bank_cd`,
> `acct_no`, `ccy_cd`, `bal`, `real_amt`, …). Same shape as the VND page
> ([account-inquiry-vnd-api.md](account-inquiry-vnd-api.md)). The field names in
> the table below are shown in their legacy UPPER form for traceability — read
> each as its lowercase snake_case wire equivalent. See
> [field-casing-parity-audit-2026-06-06.md](../../../audits/field-casing-parity-audit-2026-06-06.md).

| Field | Type | Source | Notes |
|---|---|---|---|
| `ACCT_SEQ_NO` | string | synthetic — `BANK_CD + "_" + ACCT_NO` | Stable client-side row key |
| `BANK_CD` | string | `ACCT_MSTR.BANK_CD` | |
| `BANK_NM` | string | resolved bank name via `BankCdResolver` | Reads `BANK_INFM.RPRS_BANK_NM` |
| `ACCT_NO` | string | `ACCT_MSTR.ACCT_NO` | |
| `ACCT_TYPE_CD` | string | `ACCT_MSTR.ACCT_TYPE_CD` | Per legacy `wabooks.enums.AcctTypeCd`: `02` DEPOSIT, `03` CURRENT_KHR, `04` CURRENT_USD, `05` SAVINGS_KHR, `06` SAVINGS_USD, `07` CURRENT_KRW, `08` SAVINGS_KRW. `01` (LOAN) never appears on this screen. |
| `ACCT_TYPE_NM` | string | `BankCdResolver.resolveAcctTypeNm` | `Fixed` / `Current` / `Foreign Current` / `Savings` / `Foreign Savings` / `Current Accounts` / `Saving Accounts`. Legacy English labels for `07`/`08` kept verbatim; Korean labels for `04`/`06` translated. |
| `CCY_CD` | string | `ACCT_MSTR.CCY_CD` | **One of `USD`, `KRW`, `EUR`, `JPY`, …** — never `VND` on this screen |
| `ACCT_NICK_NM` | string | `ACCT_MSTR.ACCT_NICK_NM` (raw) + `wb_format_acct_nick_nm` (display) | Read via `AcctMstr.formattedAcctNickNm` Hibernate `@Formula`. Frontend matches `keyword` against this value. |
| `BAL` | string | `ACCT_MSTR.BAL` | Stringified to preserve precision; client formats with `formatAmount(value, row.CCY_CD)` |
| `REAL_AMT` | string | `ACCT_MSTR.REAL_AMT` | Available balance — formatted per row CCY |
| `ACCT_BAL_INQ_STTS` | string | `ACCT_MSTR.ACCT_BAL_INQ_STTS` | Success codes: `00000000`, `42110000` |
| `ACCT_BAL_INQ_MSG` | string | `ACCT_MSTR.ACCT_BAL_INQ_MSG` | Tooltip when status indicates an error |
| `ACCT_BAL_UPD_DTM` | string | `ACCT_MSTR.ACCT_BAL_UPD_DTM` | Legacy `YYYYMMDDHHmmss` format |

---

## `POST /api/v1/banking/accounts/export` (FCY mode)

### Request body — `ExcelExportRequest`

```json
{
  "screenId": "banking-account-inquiry-fcy",
  "dymAreaId": "default",
  "currency": "FCY",
  "filters": {
    "bankName": "VCB",
    "keyword": "saigon"
  },
  "sort": { "field": "BANK", "direction": "ASC" },
  "columns": [
    { "fieldId": "BANK",              "header": "Bank",              "format": "TEXT",     "widthChars": 40 },
    { "fieldId": "ACCOUNT_NICKNAME",  "header": "Account / Nickname","format": "TEXT" },
    { "fieldId": "BALANCE",           "header": "Balance",           "format": "AMOUNT", "align": "RIGHT" },
    { "fieldId": "AVAILABLE_BALANCE", "header": "Available Balance", "format": "AMOUNT", "align": "RIGHT" },
    { "fieldId": "LAST_UPDATE",       "header": "Last Update",       "format": "DATETIME" }
  ],
  "title":    "Account Inquiry (FCY)",
  "fileName": "Account Inquiry (FCY)_03052026"
}
```

### Response

- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename*=UTF-8''Account%20Inquiry%20(FCY)_03052026.xlsx`
- Body: streamed XLSX (Apache POI SXSSF — backend never buffers full sheets in memory)

### Server-side flow

1. `ExportableScreenRegistry` resolves `screenId="banking-account-inquiry-fcy"` to `AccountInquiryFcyExportProvider`. Unknown ID → `WBE2010`.
2. `AccountInquiryFcyExportProvider` (subclass of `AccountInquiryExportProvider`) sets `defaultCurrency = "FCY"`. The page's `currency` field (concrete CCY when narrowed) flows through unchanged.
3. Provider re-invokes `AccountService.list(currency, sortField, sortDirection)` — list and export share **one** query path. No drift.
4. Bank-name + keyword filters applied in-memory (matches frontend filtering semantics).
5. **Multi-line subheader** composed from `AccountListResponse.totalsByCcy()`. Each line is `<count> account(s)  ·  Balance: <bal>  ·  Available Balance: <avail>` formatted via `AmountFormatter.forDisplay(value, ccyCd)` so the symbol + locale match the page UI 1:1. Empty result degrades to `"%d account(s)"` count-only string.
6. `ExcelWorkbookWriter` streams rows to the response. POI applies `#,##0.##` to `AMOUNT` data cells at cell level — provider hands raw decimal, never pre-formatted strings.

#### Subheader examples

| Wire `currency` | Subheader rendered |
|---|---|
| `FCY` (multi-CCY result) | `4 account(s)  ·  Balance: $12,931.38  ·  Available Balance: $12,931.38`<br>`2 account(s)  ·  Balance: ₩0  ·  Available Balance: ₩0`<br>`1 account(s)  ·  Balance: €1,234.56  ·  Available Balance: €1,234.56` |
| `USD` (concrete narrow) | `4 account(s)  ·  Balance: $12,931.38  ·  Available Balance: $12,931.38` |
| `KRW` (concrete narrow) | `2 account(s)  ·  Balance: ₩0  ·  Available Balance: ₩0` |
| empty result | `0 account(s)` |

The FCY subheader contract diverges from VND only by emitting **one line per CCY** instead of a single line. The single-line case (concrete CCY narrow) reads identically to a VND export — same template, same formatter.

---

## `BIZ_KEY` logic (tenant isolation)

Identical to VND:
- `JwtAuthenticationFilter` populates `TenantContext.currentBizKey()` from the JWT claim.
- `AccountService.list()` reads `TenantContext.currentBizKey()` and passes it to every repository call.
- `AcctMstrRepository.findByBizKey…` — `BIZ_KEY` is the **first** discriminator on the composite key `(BIZ_KEY, BANK_CD, ACCT_NO)`, so the index is fully exploited.
- `BIZ_KEY` is **never** accepted from request body or query — it is derived server-side from the authenticated principal. Refactor config rejects `%` wildcards (see `TenantProperties`).

Single-tenant R&D: pinned to `BIZ_KEY = VN_VNM0004_000001` via `wecms.tenant.biz-key`.

---

## Filtering (server-side)

All filters compose into a single JPQL `findFiltered` query in `AcctMstrRepository`. Optional predicates use the `(:param IS NULL OR …)` pattern.

| Filter | Where applied | Why |
|---|---|---|
| `BIZ_KEY` | Repository (JPQL `WHERE`) | Index-friendly; non-negotiable tenant scope. First column of the composite PK. |
| `ACTV_YN = 'Y'` | Repository (JPQL `WHERE`) | Soft-delete equivalent — closed accounts stay in the table. |
| **`CCY_CD <> 'VND'`** | Repository (when `currency=FCY`) | **"All non-VND" exclusion — the FCY-distinguishing predicate.** |
| `CCY_CD = ?` | Repository (when `currency=USD/KRW/EUR/…`) | Single-CCY exact match — used when the user narrows via the in-page dropdown. |
| `BANK_CD = ?` | Repository (when `bankCd` provided) | Exact-match — mirrors JEX `BANK_CD`. |
| `LOWER(COALESCE(wb_format_acct_nick_nm(...), '')) LIKE ? ESCAPE '!'` | Repository (when `keyword` provided) | Matched against the **formatted** nickname. Mirrors JEX `FnshHelper#fnsh_0001_001_r001` line 470. |

The list endpoint and the Excel export both call `AccountService.list(filter, …)` — list uses a `PageRequest` and export uses `Pageable.unpaged()` so they share one filter chain and one query path. No duplicate filter logic.

Sort is whitelisted via `AccountSortField` enum — unknown values fall back to default sort, never propagated to SQL as raw input. Same whitelist as VND (no FCY-specific sort columns).

Per-currency totals + scalar `BALANCE`/`AVAILABLE_BAL` come from a sibling aggregation query running the **same WHERE chain** without pagination. The two queries agree because they share parameter bindings, not because they share rows in memory.

---

## Error handling

Same error envelope as VND:

| Condition | Code | Behavior |
|---|---|---|
| No JWT / expired JWT | (403/401) | `JwtAuthenticationEntryPoint` returns 401 envelope |
| Unknown `screenId` on export | `WBE2010` | "Excel export is not configured for this screen" |
| Invalid path / body validation | `WBE_VALIDATION` | `MethodArgumentNotValidException` / `ConstraintViolationException` → handler |
| Export exceeds 50,000-row cap | `WBE2011` | `MAX_ROWS` cap |
| Export fails before bytes flushed | `WBE2012` | Generic export failure |
| No exportable columns after intersection | `WBE2013` | All requested columns dropped by the allow-list / View Setting visibility |
| Unexpected runtime | `WBE9999` | `GlobalExceptionHandler` maps generic `Exception` |

`ApiResponse<T>` envelope is enforced by the controller advice — controllers throw `BusinessException("WBExxxx", args…)` and never construct envelopes themselves.

---

## TODO / open questions

- [x] **FCY-specific seed migration for `SYS_SCR_OPTION_WECMS`** — shipped as V43 (CHECKBOX + 6 columns mirroring VND).
- [x] **Backend allow-list for the FCY export provider** — `AccountInquiryFcyExportProvider` inherits the parent's `ALLOWED_FIELD_IDS` set (`BANK`, `ACCOUNT_NICKNAME`, `BALANCE`, `AVAILABLE_BALANCE`, `VAS_ACCOUNT`, `LAST_UPDATE`) — verified against [AccountInquiryExportProvider.java#L63-L70](../../../../src/main/java/com/kosign/wecms/domain/banking/export/AccountInquiryExportProvider.java#L63-L70).
- [x] **Account-type label mapping** — `BankCdResolver.ACCT_TYPES` covers `02–08` per the legacy `AcctTypeCd` enum. Frontend chip uses the full backend label (`USD • Foreign Current`, `KRW • Current Accounts`).
- [x] **Bank dropdown narrow behaviour** — confirmed: `BANKS_AVAILABLE` is scoped to `(tenant, wireCurrency)`, so picking USD/KRW re-scopes the bank list to that CCY. This is the agreed product behaviour.
- [ ] **Plan 1 — Frontend formatter parity** — `lib/currency.ts` only special-cases USD; EUR/KRW/JPY render with vi-VN separators while the backend uses en-US. On-screen tile and Excel subheader will print different numbers for these CCYs until aligned.
- [ ] **Plan 3 — `ACCT_TYPE_CD` predicate** — refactor relies solely on `ccy_cd <> 'VND'`; legacy applies an additional `acct_type_cd IN (...)` filter. Add the predicate (`{02,04,06,07,08}` for FCY, derived from CCY for concrete narrow) in a follow-up MR.
- [ ] **Concrete-CCY pass-through validation** — the page sends arbitrary CCY codes (`USD`, `KRW`, `EUR`, …). Confirm `AccountService#resolveFilter` treats unknown codes safely (single-CCY narrow returning empty result, not 4xx).
- [ ] **`HAND_REG_YN` / `ACCT_CUST_TYPE` filter** — held pending PO confirmation (mirrors VND TODO).
- [ ] **Cross-check `IDO.ACCT_MSTR_R001_CNT.xml` per-CCY total semantics** — legacy emits separate `TOT_USD_*`, `TOT_KHR_*` columns; refactor uses the row-set `TOTALS_BY_CCY`. Verify rounding + inclusion rules match under FCY mode.
- [ ] **Sort whitelist coverage on FCY** — `AccountSortField` is shared with VND. Confirm the whitelist is sufficient for FCY screens (no FCY-specific sortable fields needed).

---

## Cross-references

- Feature spec: [../../features/banking/account-inquiry-fcy.md](../../../features/banking/current-savings-fcy/account-inquiry.md)
- Frontend page: [../../frontend/banking/account-inquiry-fcy-page.md](../../../../../wecms-web/docs/features/banking/current-savings-fcy/account-inquiry.md)
- DB design: [../../database/banking/account-inquiry-fcy-db.md](../../database/banking/account-inquiry-fcy-db.md)
- VND twin (read-only reference): [./account-inquiry-vnd-api.md](account-inquiry-vnd-api.md)
- Cross-feature API mapping: [../api-mapping.md](../api-mapping.md)
- Service-mapping (JEX → Spring): [../../migration/service-mapping.md](../../migration/service-mapping.md)

# Daily Financial (HS) — Interface Specification

> The three REST endpoints, verified against `ReportController`, the DTO records, and the
> frontend callers in `wecms-web/lib/api/banking.ts`. Wire shape is lowercase snake_case (global
> Jackson `SNAKE_CASE`); envelope is the standard `ApiResponse<T>` (`{status, data}`; errors add
> `timestamp`/`path`/`trace_id`). Binary download bypasses the envelope (documented exception).

---

## 1. Legacy request/response (reference)

### 1.1 Read — `rept_0017_001_r001` (POST `.jct`)

```jsonc
// request (JexData input)
{ "PERIOD_DATE": "20251020",           // required (ValidationHelper, r001_act.jsp:19-23)
  "ACCT_NO": "ALL" }                    // required — "ALL" or JSON [{BANK_CD, ACCT_NO}, …]
// response
{ "REC1": [ { "ROW_TYPE": "...", "CCY_CD": "...", "BANK_ACCT": "...", "RCPT_NM": "...",
              "DESC_EN": "...", "DESC_KO": "...", "OPEN_BAL": "...", "RCPT": "...",
              "PYMN": "...", "CLOSE_BAL": "...", "TRAN_SEQ": "...", "TRAN_TM": "...",
              "TRAN_DT": "...", "BANK_CD": "...", "ACCT_TYPE_ORD": "...", "ACTUAL_BAL": "...",
              "RMRK": "...", "ACCT_NO": "...", "ACCT_SEQ_NO": "...", "MEMO": "...",
              "PYMN_CD": "...", "REPT_CD": "..." } ] }
```

### 1.2 Save — `rept_0017_001_u001`

```jsonc
{ "ACCT_SEQ_NO": "…", "FIELD_ID": "MEMO|REPT_CD|PYMN_CD|DESC_NM_EN|DESC_NM_KO", "FIELD_VAL": "…" }
// response: empty output; row-not-found → WBE0404
```

### 1.3 Excel — `GET rept_0017_001_r002_excel.act?PERIOD_DATE=…&ACCT_NO=…`

Streams `WeCMS_Daily_Financial_Report_{EN|KO}.xlsx` (locale from session language).

---

## 2. Refactor endpoint 1 — read report

### `GET /api/v1/banking/reports/daily-financial-hs`

| Aspect | Value |
|---|---|
| Auth | JWT + `@PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial-hs')")` |
| Query params | `asOf` (optional, `yyyyMMdd`; blank → today) · `accountFilter` (optional, default `"ALL"`; else JSON array string `[{"BANK_CD":"…","ACCT_NO":"…"},…]`) |
| Returns | `ApiResponse<DailyFinancialHsReport>` — HTTP 200 |
| Frontend caller | `fetchDailyFinancialHs(params)` — `wecms-web/lib/api/banking.ts` (~L1004) |

| Wire param | Maps to legacy | Notes |
|---|---|---|
| `asOf` | `PERIOD_DATE` | legacy required; refactor optional-with-default |
| `accountFilter` | `ACCT_NO` | identical value contract (`ALL` / JSON array) |

**Response `data` (`DailyFinancialHsReport`):**

```jsonc
{
  "as_of_dt": "20260702",
  "account_filter": "ALL",
  "rows": [ /* LedgerRow[] in DB order */ ]
}
```

**`LedgerRow` fields (22)** — Java record `DailyFinancialHsReport.LedgerRow`; amounts are wire
strings (raw numeric, no separators) with `null` preserved:

| Wire field | Type | Legacy REC1 twin | Notes |
|---|---|---|---|
| `row_type` | string | `ROW_TYPE` | `HEADER` \| `BEFORE_BALANCE` \| `TRANSACTION` \| `ACCOUNT_TOTAL` \| `CURRENCY_TOTAL` |
| `bank_account` | string | `BANK_ACCT` | bank label on HEADER (may embed `Due date:` / `BRANCH` text); empty on TRANSACTION |
| `currency` | string | `CCY_CD` | `VND` / `USD` / `KRW` / … |
| `rept_cd` / `pymn_cd` | string\|null | `REPT_CD` / `PYMN_CD` | user-editable |
| `description_en` / `description_ko` | string\|null | `DESC_EN` / `DESC_KO` | user-editable |
| `customer_supplier` | string\|null | `RCPT_NM` | read-only display (function-derived) |
| `before_balance` / `receipt` / `payment` / `balance` | string\|null | `OPEN_BAL` / `RCPT` / `PYMN` / `CLOSE_BAL` | null ⇒ blank cell |
| `balance_company_actual` | string\|null | `ACTUAL_BAL` | populated on `CURRENCY_TOTAL` only → Memo column |
| `account_number` | string | `ACCT_NO` | |
| `account_type_order` | int | `ACCT_TYPE_ORD` | `999990/999991/999992` on CURRENCY_TOTAL rows |
| `acct_seq_no` | string | `ACCT_SEQ_NO` | PK half for the PATCH endpoint; `""` on non-persistable rows |
| `transaction_date` / `transaction_time` / `transaction_seq` | string\|null / string\|null / int\|null | `TRAN_DT` / `TRAN_TM` / `TRAN_SEQ` | null on header/total rows |
| `memo` | string\|null | `MEMO` | user-editable |
| `bank_cd` | string | `BANK_CD` | projection alias `bank_cd_out` |
| `bank_name_sort` | string | — | sort key surfaced by the function |

---

## 3. Refactor endpoint 2 — per-field save

### `PATCH /api/v1/banking/reports/daily-financial-hs/entries/{acctSeqNo}`

| Aspect | Value |
|---|---|
| Auth | same `@menuAuthz` gate |
| Path param | `acctSeqNo` — `REPT_TRNS_HSTR.ACCT_SEQ_NO` of the target row |
| Body | `DailyFinancialHsEntryUpdate` (`@Valid`) |
| Returns | `ApiResponse<Void>` — HTTP 200, `data: null` |
| Frontend caller | `updateDailyFinancialHsEntry(acctSeqNo, patch)` — `lib/api/banking.ts` (~L1022); void-tolerant unwrap (checks `status.error` only) |

**Body (`DailyFinancialHsEntryUpdate`):** all fields optional; `null` = unchanged, `""` = clear.

```jsonc
{ "rept_cd": "RC_NEW",        // @Size(max=20)
  "pymn_cd": null,            // @Size(max=20)
  "desc_nm_en": "Updated",    // @Size(max=255)
  "desc_nm_ko": null,         // @Size(max=255)
  "memo": "" }                // @Size(max=500)
```

**Errors:**

| Condition | Code | HTTP |
|---|---|---|
| Row not found in tenant | `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND` | 200 (business envelope) |
| `@Size` violation | `WBE_VALIDATION` | 200 |
| No/invalid JWT | `WBE1009` | 401 |
| Grant/product-type denied | `WBE1003` | 403 |

---

## 4. Refactor endpoint 3 — Excel download

### `GET /api/v1/banking/reports/daily-financial-hs/report`

| Aspect | Value |
|---|---|
| Auth | same `@menuAuthz` gate |
| Query params | `asOf` (optional) · `accountFilter` (optional, default `ALL`) · `lang` (optional, `EN`/`KO`/`VI`, default EN) |
| Returns | `ResponseEntity<ByteArrayResource>` — binary XLSX (envelope exception §3 of the REST conventions) |
| Content-Type | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| Content-Disposition | RFC 5987 `attachment; filename*=UTF-8''WeCMS_Daily_Financial_Report_{EN\|KO\|VI}.xlsx` |
| Frontend caller | `downloadDailyFinancialHsReport({asOf, accountFilter, lang})` — `lib/api/banking.ts` (~L1062) via shared `downloadFile` (blob; **never** routed through `unwrap()`) |

### Excel export structure (verified in `DailyFinancialHsDownloadService` + its 4 tests)

- **Template-populate** — per-locale classpath templates
  [templates/reports/daily-financial-hs/](../../../../../src/main/resources/templates/reports/daily-financial-hs/);
  data area starts at row index 5.
- **A1:L1 title is template-owned** — Java never writes it. EN `Daily Financial Report` · KO
  `일 일 자 금 수 지 현 황` · VI `Báo cáo tài chính hằng ngày` (EN/VI localized on PO request
  2026-07-02; legacy templates carry the Korean title in all languages — do not "fix" back).
- **Date slots:** A2 (centred) + L3 (right). EN/VI `M/d/yyyy` · KO `yyyy년 M월 d일`.
- **12 columns, always** (View Setting never filters the export): Bank · Currency · Receipt Code ·
  Payment Code · Description (EN) · Description (KO) · Customer/Supplier · Before Balance ·
  Receipt · Payment · Balance · Memo. Widths `30/10/20/20/25/25/30/18/18/18/18/30 × 256`.
- **Merges:** Bank+Currency vertical merge per account bounded to
  `[first data row .. ACCOUNT_TOTAL]` (never absorbing CURRENCY_TOTAL — the multi-currency
  overlap fix); SUB TOTAL merges C:F (cyan); TOTAL merges A:F (grey);
  `balance_company_actual` lands in the Memo column of the TOTAL row.
- **Number formats:** `#,##0` for VND/KRW/JPY, `#,##0.00` otherwise; numeric cells carry real
  numbers (re-summable in Excel), parse failures degrade to text.
- **Audit:** none — no `AUDIT_LOGS_INFM`, no `ERP_TRNS_HSTR` row (see
  [functional-specification.md §7](functional-specification.md#7-audit--logging)).

---

## 5. Error envelope example

```json
{
  "status": { "error": true, "code": "WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND",
              "message": "Daily Financial entry not found." },
  "timestamp": "2026-07-02T14:30:45Z",
  "path": "/api/v1/banking/reports/daily-financial-hs/entries/UNKNOWN",
  "trace_id": "20260702143045PT…DEVL"
}
```

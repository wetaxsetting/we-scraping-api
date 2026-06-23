# Transaction History (FCY) — Interface Specification (Backend)

> **Casing migrated 2026-06-07.** Wire JSON is now **lowercase snake_case**
> (shared with the VND twin) — the Transaction DTOs no longer carry UPPER_SNAKE
> `@JsonProperty`. UPPER tokens below are historical; read each as its lowercase
> snake_case form. See
> [field-casing-parity-audit-2026-06-06.md](../../../../audits/field-casing-parity-audit-2026-06-06.md).

Every REST endpoint, request/response shape, and error code for the refactored FCY transaction
inquiry. All endpoints live on `AccountController` (`@RequestMapping("/api/v1/banking")`) and are
**shared with the VND twin** — FCY is the same wire contract over an FCY `acctSeqNo`.

> **Wire-casing note.** The Transaction DTOs — `TransactionRow`, `TransactionListResponse`,
> `TransactionDetailResponse`, `TransactionSummaryResponse` — follow the global Jackson `SNAKE_CASE`
> strategy and carry **no** explicit `@JsonProperty` annotations; the UPPER_SNAKE `@JsonProperty`
> holdovers were removed on 2026-06-07. The **mutation request bodies** (`TransactionConfirmRequest`,
> `…ReportingAccountRequest`, `…PurposeRequest`, `…VasAccountRequest`, `…ContactRequest`) likewise use
> lowercase snake_case. UPPER tokens in the tables below are historical labels; read each as its
> lowercase snake_case form.

All endpoints carry `@PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")`.

---

## 1. List transactions

```
GET /api/v1/banking/accounts/{acctSeqNo}/transactions
    ?from={yyyyMMdd}&to={yyyyMMdd}&type={DEPOSIT|WITHDRAW|ALL}&cnfm_yn={Y|N|ALL}&keyword={text}
→ ApiResponse<TransactionListResponse>
```

- `acctSeqNo` = `{bankCd}_{acctNo}`; for FCY it resolves an FCY account whose `ACCT_MSTR.CCY_CD` is a
  non-VND currency. The service restricts rows to that currency.
- All query params optional (`from`/`to` both-or-neither).

**`TransactionListResponse`** (tokens shown UPPER for legacy traceability; wire is lowercase snake_case):

| Wire field | Type | Notes |
|---|---|---|
| `ROWS` | `TransactionRow[]` | the filtered rows |
| `TOTAL_COUNT` | int | filtered row count |
| `DEPOSIT_TOTAL` | string | Σ `TRSC_AMT` where `INOUT_DV='1'`, `AmountFormatter.wire` |
| `WITHDRAW_TOTAL` | string | Σ `TRSC_AMT` where `INOUT_DV='2'` |

**`TransactionRow`** (22 fields; tokens shown UPPER for legacy traceability, wire is lowercase snake_case): `ACCT_SEQ_NO`, `TRSC_DT`, `TRSC_TM`, `INOUT_DV`,
`DEPOSIT_AMT`, `WITHDRAW_AMT`, `BAL`, `RMRK`, `CONF_YN`, `CONFIRMED_BY`, `CONFIRMED_DTM`,
`LINK_MATCH_YN`, `CHART_ACCT_CD`, `CHART_ACCT_SEQ_NO`, `USE_USAG_CD`, `VAS_ACCT_CD`, `RCV_PAY_TRCO_KEY`,
`RCV_PAY_TRCO_NM`, `BANK_TRNS_SHORT_ID`, `PAY_ORG_FILE_NM`, `MATCHED_AT`, `MATCHED_BY` (all string).

---

## 2. Transaction summary

```
GET /api/v1/banking/accounts/{acctSeqNo}/transactions/summary
    ?from&to&type&cnfm_yn&keyword
→ ApiResponse<TransactionSummaryResponse>
```

**`TransactionSummaryResponse`**: `TOTAL_COUNT` (int), `CONFIRMED_COUNT` (int), `UNCONFIRMED_COUNT`
(int), `DEPOSIT_TOTAL` (string), `WITHDRAW_TOTAL` (string).

---

## 3. Transaction detail

```
GET /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}
→ ApiResponse<TransactionDetailResponse>
```

`trnsSeqNo` = the row's `ACCT_SEQ_NO`. Detail additionally returns `CCY_CD` and the un-merged `RMRK2`.

**`TransactionDetailResponse`** (18 fields; tokens shown UPPER for legacy traceability, wire is lowercase snake_case): `ACCT_SEQ_NO`, `TRSC_DT`, `TRSC_TM`,
`INOUT_DV`, `DEPOSIT_AMT`, `WITHDRAW_AMT`, `BAL`, `RMRK`, `RMRK2`, `BANK_REF_NO`, **`CCY_CD`**,
`CONF_YN`, `RCV_PAY_TRCO_KEY`, `RCV_PAY_TRCO_NM`, `RCV_PAY_USAG_CD`, `VAS_ACCT_CD`, `CONFIRMED_BY_ID`,
`CONFIRMED_BY_NM`, `CONFIRMED_DTM`.

---

## 4. Confirm / undo-confirm

```
PUT    /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm   (optional body)
DELETE /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm
→ ApiResponse<TransactionDetailResponse>   (refreshed detail)
```

Optional **`TransactionConfirmRequest`** (lowercase snake_case): `rcv_pay_trco_key` (≤20),
`rcv_pay_trco_nm` (≤200), `rcv_pay_usag_cd` (≤20), `vas_acct_cd` (≤50) — all optional.

Errors: `WBE_TRANSACTION_ALREADY_CONFIRMED` (confirm), `WBE_TRANSACTION_NOT_CONFIRMED` (undo).

---

## 5. Classification setters / clears

All return refreshed `ApiResponse<TransactionDetailResponse>`. Request bodies are lowercase snake_case.

| Method + URI | Body | Validation error |
|---|---|---|
| `PATCH/DELETE .../{trnsSeqNo}/reporting-account` | `{ chart_acct_seq_no, chart_acct_cd }` (both `@NotBlank`) | `WBE_TRANSACTION_CHART_ACCOUNT_NOT_FOUND` / `…_CODE_MISMATCH` |
| `PATCH/DELETE .../{trnsSeqNo}/purpose` | `{ use_usag_cd }` (`@NotBlank` ≤20) | `WBE_TRANSACTION_PURPOSE_NOT_FOUND` |
| `PATCH/DELETE .../{trnsSeqNo}/vas-account` | `{ vas_acct_cd }` (`@NotBlank` ≤50) | `WBE_TRANSACTION_VAS_ACCOUNT_NOT_FOUND` |
| `PATCH/DELETE .../{trnsSeqNo}/contact` | `{ trco_key }` (`@NotBlank` ≤20) | `WBE_TRANSACTION_CONTACT_NOT_FOUND` |

---

## 6. Match / unmatch (Smart Organizer)

```
GET    .../{trnsSeqNo}/match-candidates?from&to&keyword → ApiResponse<MatchCandidateListResponse>
POST   .../{trnsSeqNo}/match   (body CreateMatchRequest) → ApiResponse<MatchResultResponse>
DELETE .../{trnsSeqNo}/match                             → ApiResponse<MatchResultResponse>
```

Delegated to `MatchService`; `requireBankRow(...)` enforces `AccountAccessGuard.assertCanView`. R11
scope fix (account-scoped Smart Organizer) — CLOSED, see
[../../audits/smart-organizer-r11-verification.md](../../../../audits/smart-organizer-r11-verification.md).

---

## 7. Export

```
POST /api/v1/banking/accounts/transactions/export
Content-Type: application/json   Body: ExcelExportRequest
→ HTTP 200, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (XLSX)
```

`ExcelExportRequest`: `screen_id` (`banking-transaction-history`), `dym_area_id` (`default`),
`file_name`, `title`, `filters` (must include `acctSeqNo`; plus `from`/`to`/`type`/`cnfm_yn`/`keyword`),
`columns`, `sort`. Provider `TransactionHistoryExportProvider` reuses `transactionService.list(...)`.

---

## 8. Error codes

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE2002` | 200 | Bad `acctSeqNo` (missing `_` separator) |
| `WBE1003` | **403** | Per-account authorization denied (`AccountAccessGuard`, #29) |
| `WBE_TRANSACTION_NOT_FOUND` | 200 | `trnsSeqNo` not in the account |
| `WBE_TRANSACTION_ALREADY_CONFIRMED` | 200 | Confirm on an already-confirmed row |
| `WBE_TRANSACTION_NOT_CONFIRMED` | 200 | Undo on a non-confirmed row |
| `WBE_TRANSACTION_CHART_ACCOUNT_NOT_FOUND` | 200 | Reporting-account validation failed |
| `WBE_TRANSACTION_CHART_ACCOUNT_CODE_MISMATCH` | 200 | `(seq, code)` pair mismatch |
| `WBE_TRANSACTION_PURPOSE_NOT_FOUND` | 200 | Purpose validation failed |
| `WBE_TRANSACTION_VAS_ACCOUNT_NOT_FOUND` | 200 | VAS validation failed |
| `WBE_TRANSACTION_CONTACT_NOT_FOUND` | 200 | Contact validation failed |
| `WBE_VALIDATION` | 200 | Bean-validation failure on a request body |
| `WBE2010`/`WBE2011`/`WBE2013` | 200 | Export: unknown screen / >50k rows / no exportable columns |

Business outcomes return HTTP 200 with `status.error=true`; `AccessDeniedException` (#29) is the one
that maps to **403**. All errors carry `timestamp` / `path` / `trace_id`.

# Transaction History (VND) — Interface Specification (Backend)

> **Casing migrated 2026-06-07.** The Transaction History DTOs
> (`TransactionRow`, `TransactionListResponse`, `TransactionDetailResponse`,
> `TransactionSummaryResponse`) no longer carry UPPER_SNAKE `@JsonProperty` —
> wire JSON is now **lowercase snake_case** via the global Jackson SNAKE_CASE
> strategy (`acct_seq_no`, `trsc_dt`, `deposit_amt`, `conf_yn`, `rows`,
> `total_count`, …). UPPER tokens in the tables below are historical; read each
> as its lowercase snake_case form. See
> [field-casing-parity-audit-2026-06-06.md](../../../../audits/historical/field-casing-parity-audit-2026-06-06.md).

Every REST endpoint, request/response shape, DTO field, and error code for the refactored
Transaction History (VND) backend. All endpoints live on `AccountController`
(`@RequestMapping("/api/v1/banking")`), are JWT-protected, and are gated by the menu-authorization
guard for the VND/FCY inquire-transactions permission codes. Wire JSON is the standard
`{ status, data }` envelope; property names follow the global Jackson `SNAKE_CASE` strategy with no
per-DTO `@JsonProperty` exceptions (migrated 2026-06-07).

> **Wire-casing note.** `TransactionRow` / `TransactionListResponse` / `TransactionDetailResponse`
> follow the global Jackson `SNAKE_CASE` strategy and carry **no** explicit `@JsonProperty`
> annotations — the UPPER_SNAKE `@JsonProperty` holdovers were removed on 2026-06-07. Wire fields are
> lowercase snake_case (e.g. `trsc_dt`, `deposit_amt`, `conf_yn`). UPPER tokens in the tables below are
> historical labels; read each as its lowercase snake_case form. See
> [field-casing-parity-audit-2026-06-06.md](../../../../audits/historical/field-casing-parity-audit-2026-06-06.md).

---

## 1. List transactions

```
GET /api/v1/banking/accounts/{acctSeqNo}/transactions
      ?from={yyyyMMdd}&to={yyyyMMdd}&type={DEPOSIT|WITHDRAW}&cnfm_yn={Y|N}&keyword={text}
```

- **Path:** `acctSeqNo` — composite `BANK_CD_ACCT_NO`.
- **Query (all optional):** `from`, `to`, `type`, `cnfm_yn`, `keyword`.
- **Returns:** `ApiResponse<TransactionListResponse>`.

`TransactionListResponse`:

| Wire | Type | Meaning |
|---|---|---|
| `ROWS` | `TransactionRow[]` | the transaction rows (most-recent-first) |
| `TOTAL_COUNT` | int | count after filtering |
| `DEPOSIT_TOTAL` | string | sum of deposit amounts (wire-decimal) |
| `WITHDRAW_TOTAL` | string | sum of withdrawal amounts (wire-decimal) |

`TransactionRow`:

| Wire | Type | Meaning |
|---|---|---|
| `ACCT_SEQ_NO` | string | transaction id (used for mutations) |
| `TRSC_DT` | string | `yyyyMMdd` |
| `TRSC_TM` | string | `HHmmss` |
| `INOUT_DV` | string | `1` IN / `2` OUT |
| `DEPOSIT_AMT` | string? | deposit amount (null if OUT) |
| `WITHDRAW_AMT` | string? | withdrawal amount (null if IN) |
| `BAL` | string | running balance |
| `RMRK` | string | remark / description |
| `CONF_YN` | string | `Y` / `N` |
| `CONFIRMED_BY` | string? | confirmer name |
| `CONFIRMED_DTM` | string? | `yyyyMMddHHmmss` |
| `LINK_MATCH_YN` | string? | `Y` if Smart-Organizer-matched |
| `CHART_ACCT_CD` / `CHART_ACCT_SEQ_NO` | string? | reporting account |
| `USE_USAG_CD` | string? | purpose code |
| `VAS_ACCT_CD` | string? | VAS account code |
| `RCV_PAY_TRCO_KEY` / `RCV_PAY_TRCO_NM` | string? | contact key + name |

## 2. Transaction summary

```
GET /api/v1/banking/accounts/{acctSeqNo}/transactions/summary?from&to&type&cnfm_yn&keyword
```

Returns `ApiResponse<TransactionSummaryResponse>`:

| Field | Type | Meaning |
|---|---|---|
| `totalCount` | int | total rows (pre status-tab filter) |
| `confirmedCount` | int | confirmed rows |
| `unconfirmedCount` | int | unconfirmed rows |
| `depositTotal` | string | deposit total |
| `withdrawalTotal` | string | withdrawal total |

Drives the All / Confirmed / Unconfirmed tab counters from a single call.

## 3. Transaction detail

```
GET /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}
```

Returns `ApiResponse<TransactionDetailResponse>`:

| Wire | Meaning |
|---|---|
| `ACCT_SEQ_NO`, `TRSC_DT`, `TRSC_TM`, `INOUT_DV` | identity + when/direction |
| `DEPOSIT_AMT`, `WITHDRAW_AMT`, `BAL` | amounts |
| `RMRK`, `RMRK2`, `BANK_REF_NO`, `CCY_CD` | description + reference + currency |
| `CONF_YN` | confirmation flag |
| `RCV_PAY_TRCO_KEY`, `RCV_PAY_TRCO_NM`, `RCV_PAY_USAG_CD`, `VAS_ACCT_CD` | classification |
| `CONFIRMED_BY_ID`, `CONFIRMED_BY_NM`, `CONFIRMED_DTM` | confirmation metadata |

## 4. Confirm / undo-confirm

```
PUT    /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm
DELETE /api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/confirm
```

- **PUT** body (optional `TransactionConfirmRequest`): `{ rcvPayTrcoKey?, rcvPayTrcoNm?, rcvPayUsagCd?, vasAcctCd? }` → `ApiResponse<TransactionDetailResponse>` (refreshed). Error if already confirmed.
- **DELETE** → `ApiResponse<Void>`. Error if not confirmed.

## 5. Classification setters / clears

Each `PATCH` assigns, each `DELETE` clears (idempotent); all return `ApiResponse<TransactionDetailResponse>`.

| Operation | Method + path | Body |
|---|---|---|
| Reporting account | `PATCH .../reporting-account` | `{ chart_acct_cd, chart_acct_seq_no }` |
| Reporting account (clear) | `DELETE .../reporting-account` | — |
| Purpose | `PATCH .../purpose` | `{ use_usag_cd }` |
| Purpose (clear) | `DELETE .../purpose` | — |
| VAS account | `PATCH .../vas-account` | `{ vas_acct_cd }` |
| VAS account (clear) | `DELETE .../vas-account` | — |
| Contact | `PATCH .../contact` | `{ trco_key }` |
| Contact (clear) | `DELETE .../contact` | — |

Request DTOs: `TransactionReportingAccountRequest`, `TransactionPurposeRequest`,
`TransactionVasAccountRequest`, `TransactionContactRequest`.

## 6. Export

```
POST /api/v1/banking/accounts/transactions/export
Content-Type: application/json
Body: ExcelExportRequest { screenId: "banking-transaction-history", dymAreaId, fileName, title, filters, sort, columns }
→ 200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (XLSX stream)
```

`filters` carries the same keys as the list endpoint **plus mandatory `acctSeqNo`**. Provider:
`TransactionHistoryExportProvider` (allow-list of 12 FIELD_IDs; 5 are value-stubs — see
[business-flow.md §7](business-flow.md#7-export-generation-flow)).

## 7. Error codes

| Code | When |
|---|---|
| `WBE_VALIDATION` | bad path/param (bad `acctSeqNo` shape, invalid date) |
| `WBE1003` / 401 | unauthorized / unauthenticated |
| transaction-already-confirmed | PUT confirm on an already-confirmed txn |
| transaction-not-confirmed | DELETE confirm on an unconfirmed txn |
| transaction / account not found | unknown `acctSeqNo` / `trnsSeqNo` in tenant |
| classification-choice-invalid | PATCH with a picker value not in the source table |
| `WBE2010` | export `screenId` unknown |
| `WBE2011` | export exceeds 50,000-row cap |
| `WBE2013` | export has no exportable columns after View Setting ∩ allow-list |
| `WBE9999` | unexpected server error |

> The exact `WBExxxx` constants for the confirm/classify business errors live in
> `messages.properties`; this table names the behaviour. Confirm against the controller/service when
> wiring a client. All errors use the standard `{ status, timestamp, path, trace_id }` envelope.

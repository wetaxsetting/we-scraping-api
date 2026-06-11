# Account Inquiry (VND) — Interface Specification (Refactor Backend)

**Screen ID:** `banking-account-inquiry-vnd` · **JEX:** `fnsh_0001_001` · **Hub:** [README.md](README.md)

Every REST endpoint the screen (and its sibling account-detail surface) consumes. Wire JSON is
lowercase snake_case (global Jackson `SNAKE_CASE`); the envelope is `{ status, data }` (+ `timestamp`/`path`/`trace_id`
on errors). Companion (original design): [references/api-spec/banking/account-inquiry-vnd-api.md](../../../references/api-spec/banking/account-inquiry-vnd-api.md).

Controller: `domain/banking/controller/AccountController.java`.

---

## 1. Endpoint catalogue

### Account Inquiry / list

| Method | Path | Auth (`@menuAuthz`) | Returns |
|---|---|---|---|
| GET | `/api/v1/banking/accounts` | `canAccessAccountInquiry()` | `ApiResponse<AccountListResponse>` |
| POST | `/api/v1/banking/accounts/export` | `canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')` | XLSX byte stream |
| GET | `/api/v1/banking/accounts/by-bank?out_bank_cd=` | (same as export) | `ApiResponse<AccountLookupListResponse>` |

### Update balance / Account sync / Transaction sync

| Method | Path | Returns |
|---|---|---|
| POST | `/api/v1/banking/accounts/transactions-sync` | `ApiResponse<TransactionsSyncResponse>` |
| POST | `/api/v1/banking/accounts/transactions-sync/{sessionId}/otp` | `ApiResponse<TransactionsSyncResponse>` |
| POST | `/api/v1/banking/accounts/transactions-sync/{sessionId}/captcha` | `ApiResponse<TransactionsSyncResponse>` |
| POST | `/api/v1/banking/accounts/transactions-sync/{sessionId}/qna` | `ApiResponse<TransactionsSyncResponse>` |
| GET | `/api/v1/banking/sync-history?from_dt=&to_dt=&currency=&bank_cd=&result=` | `ApiResponse<SyncHistoryListResponse>` (`SyncHistoryController`) |

> "Update balance" is delivered by the sync orchestrator (select accounts → real-time inquiry refreshes
> `BAL`/`REAL_AMT`/status). A dedicated `POST /api/v1/banking/own-account-transfer/balance-refresh` exists
> for the transfer composer (out of this screen's scope; same `applyOwnTransferBalanceRefresh` write path).

### Account detail / transaction drill-down (sibling — Transaction History)

| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/banking/accounts/{acctSeqNo}/transactions?from=&to=&type=&cnfm_yn=&keyword=` | `ApiResponse<TransactionListResponse>` |
| GET | `/api/v1/banking/accounts/{acctSeqNo}/transactions/summary` | `ApiResponse<TransactionSummaryResponse>` |
| GET | `/api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}` | `ApiResponse<TransactionDetailResponse>` |
| POST | `/api/v1/banking/accounts/transactions/export` | XLSX byte stream |

*(plus transaction confirm/match/purpose/contact/reporting-account/vas-account mutation endpoints — see
the Transaction History feature docs; they are not part of the Account Inquiry list screen.)*

---

## 2. `GET /api/v1/banking/accounts`

### Request (query)

| Param | Type | Default | Notes |
|---|---|---|---|
| `currency` | string | `ALL` | VND page sends `VND`. (`FCY` = non-VND; literal CCY allowed.) |
| `bankCd` | string | — | exact `BANK_CD` |
| `keyword` | string | — | partial match on formatted nickname |
| `sortField` | string | — | View Setting `FIELD_ID` (whitelisted via `AccountSortField`) |
| `sortDirection` | string | `ASC` | `ASC`/`DESC` (case-insensitive) |
| `page` | int | `0` | zero-based |
| `size` | int | `10` | clamped to `100`; `≤0` → default |

### Response — `AccountListResponse`

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "accounts": [ /* AccountRow[] — page slice */ ],
    "total_count": 42,
    "balance": "19886030287",
    "available_bal": "8417734439",
    "totals_by_ccy": [ { "ccy_cd": "VND", "count": 42, "balance": "19886030287", "available_bal": "8417734439" } ],
    "page_number": 0,
    "page_size": 10,
    "total_pages": 5,
    "banks_available": [ { "bank_cd": "11704001", "bank_nm": "Vietin Bank" } ]
  }
}
```

### `AccountRow` fields

| Field | Source | Notes |
|---|---|---|
| `acct_seq_no` | `BANK_CD + "_" + ACCT_NO` | synthetic row key |
| `bank_cd` | `ACCT_MSTR.BANK_CD` | legacy code |
| `bank_nm` | `BankCdResolver` (`BANK_INFM.RPRS_BANK_NM`) | display name |
| `acct_no` | `ACCT_MSTR.ACCT_NO` | |
| `acct_type_cd` | `ACCT_MSTR.ACCT_TYPE_CD` | `02`/`03`/`05` for VND scope |
| `acct_type_nm` | `BankCdResolver.resolveAcctTypeNm` | English fallback label |
| `acct_type_key` | `AcctTypeCd.i18nKey()` | `bank.acct_type.NN` or null |
| `ccy_cd` | `CCY_CD` (trimmed) | `VND` |
| `acct_nick_nm` | `formattedAcctNickNm` (`@Formula`) | formatted nickname |
| `bal` | `BAL` | wire decimal (no separators) |
| `real_amt` | `REAL_AMT` | available balance, wire decimal |
| `acct_bal_inq_stts` | `ACCT_BAL_INQ_STTS` | success: `00000000`, `42110000` |
| `acct_bal_upd_dtm` | **`ACCT_BAL_LST_INQ_DTM`** (HR-1) | `yyyyMMddHHmmss` — "Last Update" source |
| `hand_reg_yn` | `HAND_REG_YN` (coerced) | `Y`/`N` — manual registration |
| `cert_conn_yn` | derived (`BANK_CERT_INFO`) | `Y`/`N` — bank-credential connection |

---

## 3. `POST /api/v1/banking/accounts/transactions-sync`

### Request — `TransactionsSyncRequest`

```json
{ "accounts": [ { "bank_cd": "11704001", "acct_no": "0123456789" } ],
  "from_dt": "2026-06-01", "to_dt": "2026-06-06", "auto": true }
```

### Response — `TransactionsSyncResponse`

`status ∈ { OTP_REQUIRED, CAPTCHA_REQUIRED, QNA_REQUIRED, COMPLETED, ERROR }`.

| Field | When | Notes |
|---|---|---|
| `session_id` | non-terminal | resume token for the challenge endpoints |
| `status` | always | state machine value |
| `accounts_synced` / `debit_count` / `credit_count` / `credit_total` / `debit_total` / `last_update` | `COMPLETED` | totals (`*_total` wire decimal; `last_update` `yyyyMMddHHmmss`) |
| `challenge_prompt` / `qna_questions` / `captcha_image` / `captcha_image_format` / `login_id` | `*_REQUIRED` | challenge payload |
| `error_code` / `error_message` | `ERROR` | |

Challenge resume bodies: `{sessionId}/otp` + `{sessionId}/captcha` → `SubmitChallengeRequest`;
`{sessionId}/qna` → `SubmitQnaRequest`.

---

## 4. `POST /api/v1/banking/accounts/export`

Shared `ExcelExportRequest` shape — `screenId="banking-account-inquiry-vnd"`, `currency`, `filters{bankName,keyword}`,
`sort{sortField,sortDirection}`, `columns[]`, `title`, `fileName`. Streams
`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` with an RFC-5987 `Content-Disposition`.
Provider `AccountInquiryVndExportProvider` re-runs `AccountService.list(...)` (`Pageable.unpaged()`) and
computes the count + totals subheader server-side. See the backend Excel-export rule in
[wecms-api/CLAUDE.md](../../../../CLAUDE.md) and [references/api-spec/banking/account-inquiry-vnd-api.md](../../../references/api-spec/banking/account-inquiry-vnd-api.md).

---

## 5. Error codes

| Code | Meaning |
|---|---|
| `WBI0000` | Success |
| `WBE2010` | Excel export not configured for screen (unknown `screenId`) |
| `WBE2011` | Excel export exceeds 50,000-row cap |
| `WBE2013` | No exportable columns after View Setting / allow-list intersection |
| `WBE2050` | No bank credential for the requested bank (sync) |
| `WBE2060` | Sync session not found / expired |
| `WBE2093` | Bank has no NXiSAS/gateway config |
| `WBE2095` | Bank / gateway rejected the request (raw code echoed) |
| `WBE9001` | Wrong resume verb on a challenge session |
| `WBE_BANK_ACCOUNT_NOT_FOUND` | Account not in tenant |
| `WBE_VALIDATION` | Bean-validation failure (bad param/body) |
| `WBE9999` | Unexpected server error |

All errors flow through `GlobalExceptionHandler` → standard envelope with `trace_id`. `BusinessException` →
HTTP 200 (frontend reads `status.error`); client mistakes → 4xx; server faults → 5xx.

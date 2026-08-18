# Account Inquiry (FCY) — Interface Specification (Backend REST)

> Every endpoint, request/response shape, DTO field list, currency resolution, and error codes.
> Wire convention: lowercase snake_case, envelope `{ status, data }` (errors add `timestamp/path/trace_id`).
> Original API design note: [references/api-spec/banking/account-inquiry-fcy-api.md](../../../../references/api-spec/banking/account-inquiry-fcy-api.md).

FCY shares the `AccountController` endpoint set with VND; the `currency` query param selects the scope.

## 1. List / search — FCY account list

```
GET /api/v1/banking/accounts
    ?currency=FCY            # or USD | KRW | EUR | JPY | … (concrete narrow)
    &bankCd=11704001         # optional, legacy BANK_CD
    &keyword=woori           # optional, formatted-nickname LIKE
    &sortField=BALANCE       # optional, View Setting FIELD_ID
    &sortDirection=DESC      # optional, ASC | DESC
    &page=0                  # zero-based (Spring Data)
    &size=10                 # clamped to MAX_PAGE_SIZE=100
Auth: @PreAuthorize canAccessAccountInquiry → resolves FCY to banking-fcy-update-inquire
→ ApiResponse<AccountListResponse>
```

### Currency resolution

| Wire `currency` | Backend predicate | Account types |
|---|---|---|
| `FCY` (or blank on this screen) | `CCY_CD <> 'VND'` | `FCY_TYPE_CODES {02,04,06,07,08}` |
| `USD` | `CCY_CD = 'USD'` | `{02,04,06}` |
| `KRW` | `CCY_CD = 'KRW'` | `{02,07,08}` |
| `EUR` / `JPY` / other | `CCY_CD = <CCY>` | (no type restriction) |
| `VND` | `CCY_CD = 'VND'` | `{02,03,05}` (VND screen) |
| `ALL` | (no currency filter) | — (legacy fallback) |

### `AccountListResponse`

```jsonc
{
  "accounts": [ AccountRow, … ],   // page slice
  "total_count": 7,                // full filtered count
  "balance": "12931.38",           // cross-currency scalar (use totals_by_ccy on FCY)
  "available_bal": "12931.38",
  "totals_by_ccy": [               // one per distinct CCY in the filtered set
    { "ccy_cd": "USD", "count": 4, "balance": "12931.38", "available_bal": "12931.38" },
    { "ccy_cd": "KRW", "count": 2, "balance": "0", "available_bal": "0" }
  ],
  "page_number": 0,
  "page_size": 10,
  "total_pages": 1,
  "banks_available": [ { "bank_cd": "11704001", "bank_nm": "Woori Bank" }, … ]
}
```

### `AccountRow`

| Field | Type | Notes |
|---|---|---|
| `acct_seq_no` | string | synthetic `BANK_CD + "_" + ACCT_NO` (row key) |
| `bank_cd` / `bank_nm` | string | legacy code + resolved display name |
| `acct_no` | string | |
| `acct_type_cd` / `acct_type_nm` / `acct_type_key` | string | code + English label + i18n key (`bank.acct_type.*`) |
| `ccy_cd` | string | `USD` / `KRW` / `EUR` / … (never `VND` on FCY) |
| `acct_nick_nm` | string | formatted nickname |
| `bal` / `real_amt` | string | decimal-as-string (precision-safe), formatted per row CCY |
| `acct_bal_inq_stts` | string | `00000000` / `42110000` / error code |
| `acct_bal_upd_dtm` | string | `YYYYMMDDHHmmss`; value sourced from `ACCT_HIS_LST_INQ_DT` (FD-only fallback `ACCT_BAL_LST_INQ_DTM`) — FCY-A02 |
| `hand_reg_yn` | `"Y"`/`"N"` | manual-registration flag |
| `cert_conn_yn` | `"Y"`/`"N"` | bank credential connected (derived from `BANK_CERT_INFO`) |

## 2. Excel export

```
POST /api/v1/banking/accounts/export
Body: ExcelExportRequest { screenId:"banking-account-inquiry-fcy", currency, filters, sort, columns, fileName, title }
Auth: canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')
→ 200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (XLSX stream)
   Content-Disposition: attachment; filename*=UTF-8''<percent-encoded>.xlsx
```

Routed to `AccountInquiryFcyExportProvider` (`defaultCurrency=FCY`). Allowed FIELD_IDs: `BANK,
ACCOUNT_NICKNAME, BALANCE, AVAILABLE_BALANCE, VAS_ACCOUNT, LAST_UPDATE`. Subheader = one line per
`totals_by_ccy` group (`<count> account(s) · Balance: … · Available Balance: …`). Empty result →
count-only `"%d account(s)"`.

## 3. Update / transaction sync

```
POST /api/v1/banking/accounts/transactions-sync
Body: TransactionsSyncRequest {
  accounts: [ { bank_cd, acct_no }, … ],
  from_dt?: "YYYY-MM-DD", to_dt?: "YYYY-MM-DD", auto?: bool
}
→ ApiResponse<TransactionsSyncResponse>
```

Challenge resume (carry the returned `session_id`):

```
POST /api/v1/banking/accounts/transactions-sync/{sessionId}/qna      { question, hidden_value, answer }
POST /api/v1/banking/accounts/transactions-sync/{sessionId}/otp      { value }
POST /api/v1/banking/accounts/transactions-sync/{sessionId}/captcha  { value }
```

### `TransactionsSyncResponse` (discriminated by `status`)

`status ∈ { COMPLETED, OTP_REQUIRED, QNA_REQUIRED, CAPTCHA_REQUIRED, ERROR }`. `@JsonInclude(NON_NULL)`
drops unused fields.

| Field | Present when | Notes |
|---|---|---|
| `session_id` | always (challenges) | UUID; pass back to the resume endpoint |
| `accounts_synced` / `debit_count` / `credit_count` | COMPLETED | counts |
| `credit_total` / `debit_total` / `last_update` | COMPLETED | totals + new last-update |
| `challenge_prompt` / `qna_questions` / `captcha_image` / `captcha_image_format` / `login_id` | *_REQUIRED | challenge details |
| `error_code` / `error_message` | ERROR | |

## 4. Account detail / transactions (balance popup + drill-down)

```
GET /api/v1/banking/accounts/{acctSeqNo}/transactions
    ?from=YYYYMMDD&to=YYYYMMDD&type=DEPOSIT|WITHDRAW|ALL&cnfm_yn=Y|N|ALL&keyword=
→ ApiResponse<TransactionListResponse>   { rows[], deposit_total, withdraw_total, total_count }

GET /api/v1/banking/accounts/{acctSeqNo}/transactions/summary
→ ApiResponse<TransactionSummaryResponse>
```

The FCY balance-click popup calls the first with `from=to=selectedDate`; "View Details" deep-links to
the per-account FCY Transaction History page.

## 5. Error codes (subset relevant to FCY sync/export)

| Code | Meaning |
|---|---|
| `WBE2010` | Excel export not configured for screen (unknown `screenId`) |
| `WBE2011` | Excel export exceeds 50,000-row cap |
| `WBE2013` | Excel export has no exportable columns after View Setting / allow-list intersection |
| `WBE2050` | No credential for the bank (sync) |
| `WBE2060` | Sync session not found / expired |
| `WBE2093` | Bank lacks NxisasBankConfig |
| `WBE2095` | Bank / gateway rejection (raw code in args) |
| `WBE9001` | Wrong resume verb for the session's challenge type |
| `WBE_BANK_ACCOUNT_NOT_FOUND` | Account not in this tenant |

> Wire fields vs. legacy `fnsh_0005_001_r001`: the refactor drops the raw cert payload
> (`CERT_INFO_*`) in favour of a derived `cert_conn_yn` boolean (security-positive), and renames
> `ACCT_BAL_LST_INQ_DTM` on the wire to `acct_bal_upd_dtm`. Full field-by-field comparison:
> [audits/account-inquiry-fcy-parity-audit.md §6](../../../../audits/legacy-parity/account-inquiry-fcy-parity-audit.md).

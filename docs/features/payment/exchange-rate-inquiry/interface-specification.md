# Exchange Rate Inquiry — Interface Specification

REST contract for the three endpoints on
[`ExchangeRateController`](../../../../src/main/java/com/kosign/wecms/domain/banking/controller/ExchangeRateController.java).
Base path `/api/v1/banking` (lives in `domain.banking`). All endpoints require a Bearer JWT
and are gated by `@PreAuthorize("@menuAuthz.canAccess('payment-overseas-exchange')")`.

Wire format is lowercase snake_case (global Jackson `SNAKE_CASE`); Java record fields are
camelCase. Envelope is the standard `ApiResponse<T>` (`status` + `data`).

---

## 1. `GET /api/v1/banking/exchange-rates`

List rate rows for the authenticated tenant + available rounds.
`ExchangeRateController.list` → `ExchangeRateService.list`.

### Query parameters

| Param | Type | Required | Default | Constraint |
|---|---|---|---|---|
| `ref_date` | string | No | today (Asia/Ho_Chi_Minh) | `@Pattern("^[0-9]{8}$")` — YYYYMMDD |
| `bank_cd` | string | No | `11704088` (Shinhan) | `@Size(max = 20)` |
| `seq_no` | string | No | latest (DISTINCT ON) | `@Pattern("^[0-9]{1,2}$")` — "1" morning / "2" afternoon |
| `sort_field` | string | No | alphabetical by currency | `CURRENCY` / `CASH_BUY` / `CASH_SELL` / `TT_BUY` / `TT_SELL` (enum-parsed, lenient) |
| `sort_direction` | string | No | ASC | `ASC` / `DESC` (enum-parsed, lenient) |

### Response — `ApiResponse<ExchangeRateListResponse>`

```jsonc
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "ref_date": "20250702",          // resolved or fallback date (never null)
    "bank_cd": "11704088",
    "bank_nm": "Shinhan Bank",       // from BANK_INFM (never null)
    "rates": [
      {
        "ccy_cd": "VND",             // counter currency (VND for Shinhan/Woori VN)
        "ref_ccy_cd": "USD",         // foreign currency — the display discriminator
        "basic_rate": 24350.00,      // nullable
        "basic_tran": 24350.00,      // nullable
        "buy_amt": 24200.00,         // Cash Buy — nullable
        "sell_amt": 24500.00,        // Cash Sell — nullable
        "tran_buy_amt": 24250.00,    // T/T Buy — nullable
        "tran_sell_amt": 24450.00,   // T/T Sell — nullable
        "stand_point": "1",          // nullable
        "last_update_date": "20250425",      // nullable
        "last_update_time": "105100",        // nullable
        "exch_rate_reg_dtm": "20250425105100" // nullable
      }
      // … one entry per counter-currency, ordered by ccy_cd / ref_ccy_cd
    ],
    "rounds": [
      { "seq_no": "2", "last_update_time": "143000" },
      { "seq_no": "1", "last_update_time": "083000" }
    ]
  }
}
```

```jsonc
// Empty result (no data, explicit ref_date, no fallback)
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": { "ref_date": "20250513", "bank_cd": "11704088", "bank_nm": "Shinhan Bank", "rates": [], "rounds": [] }
}
```

### Response DTO fields

`ExchangeRateListResponse` (record):

| Wire field | Java field | Type | Null? |
|---|---|---|---|
| `ref_date` | `refDate` | String | never |
| `bank_cd` | `bankCd` | String | never |
| `bank_nm` | `bankNm` | String | never |
| `rates` | `rates` | `List<ExchangeRateRow>` | never (may be empty) |
| `rounds` | `rounds` | `List<ExchangeRateRoundItem>` | never (may be empty) |

`ExchangeRateRow` (record): `ccy_cd`, `ref_ccy_cd`, `basic_rate`, `basic_tran`, `buy_amt`,
`sell_amt`, `tran_buy_amt`, `tran_sell_amt`, `stand_point`, `last_update_date`,
`last_update_time`, `exch_rate_reg_dtm`. `ccy_cd` / `ref_ccy_cd` non-null; all amount and
metadata fields nullable.

`ExchangeRateRoundItem` (record): `seq_no`, `last_update_time`.

## 2. `GET /api/v1/banking/exchange-rates/banks`

Bank codes with rate data for the tenant. `ExchangeRateController.listBanks`.

```jsonc
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": [
    { "bank_cd": "11704088", "bank_nm": "Shinhan Bank" },
    { "bank_cd": "11704020", "bank_nm": "Woori Bank" }
  ]
}
```

`ExchangeRateBankItem` (record): `bank_cd`, `bank_nm`. Tenant-scoped; may return `[]` (the
frontend then uses its static list).

## 3. `GET /api/v1/banking/exchange-rates/currencies`

All distinct display-currency codes for the tenant. `ExchangeRateController.listCurrencies`.

```jsonc
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": ["AUD", "CAD", "EUR", "GBP", "HKD", "JPY", "KRW", "SGD", "USD"]
}
```

`data` is `List<String>` (the foreign side normalised by a CASE expression).

## 4. Errors

| Code | HTTP | When |
|---|---|---|
| `WBE_VALIDATION` | 200 (envelope) | `ref_date` not 8 digits, `bank_cd` > 20 chars, `seq_no` not 1–2 digits (`ConstraintViolationException`) |
| `WBE1009` | 401 | missing / invalid JWT (`JwtAuthenticationEntryPoint`) |
| `WBE1003` | 403 | authenticated but lacks `payment-overseas-exchange` access (`@PreAuthorize` → `AccessDeniedException`) |
| `WBE9999` | 500 | unexpected server fault |

Every error envelope carries `timestamp`, `path`, `trace_id` (`X-Trace-Id` header) per the
project error contract.

## 5. Field null-safety

- `ref_date`, `bank_cd`, `bank_nm` — always present.
- All amount fields (`basic_rate`, `basic_tran`, `buy_amt`, `sell_amt`, `tran_buy_amt`,
  `tran_sell_amt`) — nullable.
- `stand_point`, `last_update_date`, `last_update_time`, `exch_rate_reg_dtm` — nullable.
- `rates` / `rounds` — never null, may be empty.

## 6. OpenAPI / Springdoc

No `@Tag` / `@Operation` annotations are present (see the controller Javadoc). Springdoc is
**intentionally not wired** project-wide — an **APPROVED DEVIATION**, not a gap. See
[refactor-gap-analysis.md](refactor-gap-analysis.md) §B-4.

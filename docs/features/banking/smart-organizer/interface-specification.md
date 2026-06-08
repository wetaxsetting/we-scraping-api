# Smart Organizer — Interface Specification (Backend REST)

Every REST endpoint of the refactored Smart Organizer, with request params, request/response shapes,
DTO field lists, and error codes.

> **Wire casing.** Global Jackson `SNAKE_CASE` strategy (CTO directive 2026-05-08) — every JSON field
> is lowercase `snake_case` on the wire; Java DTO fields are camelCase. Field tables below give the
> **wire** name. Standard envelope: `ApiResponse<T>` (`{ "result": ..., "data": <T> }`).

Base paths:
- `/api/v1/banking/smart-organizer` — eligibility + confirm (`SmartOrganizerController`)
- `/api/v1/banking/smart-organizer/rules` — rule CRUD + reorder (`OrganizerRuleController`)

Both require `@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')`.

---

## 1. Search / list eligible transactions

```
GET /api/v1/banking/smart-organizer/eligible
```

**Query params**

| Wire | Type | Req | Default | Notes |
|---|---|---|---|---|
| `bank_cd` | string | ✅ | — | account scope (R11) |
| `acct_no` | string | ✅ | — | account scope (R11) |
| `from_dt` | string `yyyyMMdd` | ✅ | — | inclusive |
| `to_dt` | string `yyyyMMdd` | ✅ | — | inclusive |
| `q` | string | — | — | remark substring (`ILIKE`) |
| `sort_field` | string | — | `TRSC_DT` | whitelist (see functional-spec §4) |
| `sort_dir` | string | — | `DESC` | `ASC`/`DESC` |
| `page` | int | — | `1` | 1-based |
| `size` | int | — | `15` | clamped ≤ 100 |

**Response** `ApiResponse<EligibleTransactionListResponse>`

```
EligibleTransactionListResponse {
  total_count : long
  rows        : EligibleTransactionRow[]
}

EligibleTransactionRow {
  acct_seq_no          : string
  bank_cd              : string
  bank_nm              : string        // R07 resolved
  acct_no              : string
  acct_nick_nm         : string        // R07 resolved
  trsc_dt              : string         // yyyyMMdd
  trsc_tm              : string         // HHmmss
  inout_dv             : string         // "1"=in/RCV, "2"=out/PAY
  in_amt               : decimal|null   // R06
  out_amt              : decimal|null   // R06
  bal                  : decimal        // R06/R07
  ccy_cd               : string         // R06
  rmrk                 : string
  matched_rmrk_seq_no  : long|null      // winning rule (R01)
  chart_acct_cd        : string|null
  chart_acct_seq_no    : string|null
  chart_acct_nm        : string|null    // R07 resolved
  chart_acct_level_nm  : string|null    // R07 (leaf-name only)
  use_usag_cd          : string|null
  disp_usag_nm         : string|null    // R07 resolved
  vas_acct_seq_no      : long|null
  disp_vas_acct_nm     : string|null    // R07 resolved
  trco_key             : string|null
  disp_trco_nm         : string|null    // R07 resolved
}
```

## 2. Confirm reviewed transactions

```
POST /api/v1/banking/smart-organizer/confirm
```

**Body** `ConfirmRequest`

```
ConfirmRequest {
  bank_cd : string   // @NotBlank ≤20 — scope (R11)
  acct_no : string   // @NotBlank ≤100 — scope (R11)
  rows    : Row[]    // @NotEmpty
}
Row {
  acct_seq_no       : string   // @NotBlank ≤20 — the transaction to confirm
  use_usag_cd       : string?  // ≤20  — purpose (optional)
  vas_acct_seq_no   : long?    //       — VAS account (optional)
  trco_key          : string?  // ≤20  — contact (optional)
  chart_acct_cd     : string?  // ≤20  — reporting account (optional)
  chart_acct_seq_no : string?  // ≤10  — reporting account surrogate (optional)
}
```

**Response** `ApiResponse<ConfirmResponse>`

```
ConfirmResponse { confirmed_count : int }
```

**Errors:** `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND` (404, missing or wrong-account row),
`WBE_SMART_ORGANIZER_ALREADY_CONFIRMED` (409), `WBE_SMART_ORGANIZER_USAGE_NOT_FOUND` /
`…_VAS_NOT_FOUND` / `…_CONTACT_NOT_FOUND` (404), `WBE9999` (sequence failure), `WBE1009` (no tenant),
`403` (no `V` grant). Transactional — any row failing aborts the whole batch.

## 3. List Organizer Settings rules

```
GET /api/v1/banking/smart-organizer/rules
```

**Response** `ApiResponse<OrganizerRuleListResponse>`

```
OrganizerRuleListResponse {
  total_count : long
  rows        : OrganizerRuleRow[]
}
OrganizerRuleRow {
  rmrk_seq_no       : long
  rcv_pay_dv        : string    // "RCV" | "PAY"
  rmrk              : string
  match_type        : string    // "C" | "E"
  disp_ord          : int
  trco_key          : string|null
  use_usag_cd       : string|null
  vas_acct_seq_no   : long|null
  chart_acct_seq_no : string|null
  chart_acct_cd     : string|null
  reg_id            : string|null
  reg_dtm           : string|null   // yyyyMMddHHmmss
}
```

## 4. Create a rule

```
POST /api/v1/banking/smart-organizer/rules
```

**Body** `OrganizerRuleSaveRequest`

```
OrganizerRuleSaveRequest {
  rmrk              : string   // @NotBlank ≤500
  match_type        : string   // @Pattern ^[CE]$
  rcv_pay_dv        : string   // @Pattern ^(RCV|PAY)$
  disp_ord          : int?     // auto MAX+1 when null
  trco_key          : string?  // ≤20
  use_usag_cd       : string?  // ≤20
  vas_acct_seq_no   : long?
  chart_acct_seq_no : string?  // ≤10
  chart_acct_cd     : string?  // ≤20
}
```

**Response** `ApiResponse<OrganizerRuleRow>` (the created rule).

## 5. Update a rule

```
PUT /api/v1/banking/smart-organizer/rules/{rmrk_seq_no}
```

`{rmrk_seq_no}` = `@Positive Long` path var. **Body** = `OrganizerRuleSaveRequest`. **Response** =
`ApiResponse<OrganizerRuleRow>`. `disp_ord` is only overwritten when non-null; `REG_*` preserved.
Missing rule → `WBE_SMART_ORGANIZER_RULE_NOT_FOUND`.

## 6. Delete a rule

```
DELETE /api/v1/banking/smart-organizer/rules/{rmrk_seq_no}
```

Hard delete, tenant-scoped. **Response** `ApiResponse<Void>`. Missing rule →
`WBE_SMART_ORGANIZER_RULE_NOT_FOUND`.

## 7. Reorder rules

```
POST /api/v1/banking/smart-organizer/rules/reorder
```

**Body** `OrganizerRuleReorderRequest`

```
OrganizerRuleReorderRequest { rows : Entry[] }   // @NotEmpty
Entry { rmrk_seq_no : long (@Positive), disp_ord : int (@Positive) }
```

**Response** `ApiResponse<Void>`. Duplicate `rmrk_seq_no` →
`WBE_SMART_ORGANIZER_RULE_DUPLICATE_REORDER` (400); unknown id →
`WBE_SMART_ORGANIZER_RULE_NOT_FOUND` (404).

## 8. Export

**Not implemented.** Smart Organizer has no dedicated XLSX/CSV export endpoint. (Transaction History —
the parent screen — owns the shared `common/export` XLSX pipeline; an organizer export was never part
of legacy `fnsh_0200_001` and is not in scope.) See [refactor-gap-analysis.md](refactor-gap-analysis.md).

## 9. Error code registry

| Code | Message (en) |
|---|---|
| `WBE_SMART_ORGANIZER_RULE_NOT_FOUND` | Smart Organizer rule not found. |
| `WBE_SMART_ORGANIZER_RULE_DUPLICATE_REORDER` | Reorder request contains duplicate rules. |
| `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND` | Transaction not found in the current tenant. |
| `WBE_SMART_ORGANIZER_ALREADY_CONFIRMED` | This transaction is already confirmed. |
| `WBE_SMART_ORGANIZER_USAGE_NOT_FOUND` | The selected purpose was not found in the current tenant. |
| `WBE_SMART_ORGANIZER_VAS_NOT_FOUND` | The selected VAS account was not found in the current tenant. |
| `WBE_SMART_ORGANIZER_CONTACT_NOT_FOUND` | The selected contact was not found in the current tenant. |

Defined in `src/main/resources/messages.properties:633-639` (+ `messages_en.properties`).

---

Frontend consumption of these endpoints:
[wecms-web/docs/features/banking/smart-organizer/api-consumption.md](../../../../../wecms-web/docs/features/banking/smart-organizer/api-consumption.md).

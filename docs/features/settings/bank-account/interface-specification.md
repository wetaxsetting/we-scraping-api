# Bank Account — Interface Specification (Backend REST contract)

All endpoints under base path `/api/v1/settings/bank-account`. Wire format is lowercase snake_case
(global Jackson `SNAKE_CASE`); envelope is `{ status, data, timestamp?, path?, trace_id? }`.
Controller: [SettingsBankAccountController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/controller/SettingsBankAccountController.java).

## Authorization (all endpoints)

```java
@PreAuthorize("@menuAuthz.canAccess('settings-core-bank-account')")   // class-level
```

Every endpoint (read and write) requires a valid JWT **and** the `settings-core-bank-account` menu grant
(`USER_MENU_AUTH_INFM`; admins bypass the per-user ∩ but not the tenant's product tree). Missing/invalid
JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`.

Tenant scope (`BIZ_KEY`), actor (`USER_ID`), and admin flag come from `TenantContext` (signed JWT) — never
from a path / body / query param.

---

## 1. List

```
GET /api/v1/settings/bank-account?sortField=BANK&sortDirection=ASC
```
`sortField` / `sortDirection` both optional (View Setting `FIELD_ID` + `ASC`/`DESC`); unknown/blank →
default ORDER BY `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC`.

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "items": [
      {
        "bank_cd": "11704088",
        "bank_name": "Shinhan VN",
        "acct_no": "100000000001",
        "currency": "VND",
        "nickname": "기업(1015)",
        "description": "Operating account",
        "account_type_cd": "03",
        "maturity_date": null,
        "vas_acct_seq_no": 333,
        "vas_acct_cd": "333",
        "vas_acct_nm": "Test",
        "vas_account_bank": "Test(VND)",
        "prn_seq": 1,
        "bal": "19886030287",
        "real_amt": "8417734439",
        "last_modified_by": "김세명",
        "last_modified_at": "20240920101500",
        "view_permission_label": "김세명 + 3 more",
        "withdrawal_permission_label": null
      }
    ]
  }
}
```

## 2. Update one row

```
PATCH /api/v1/settings/bank-account/{bankCd}/{acctNo}
{ "nickname": "Operating", "description": "...", "account_type_cd": "06",
  "maturity_date": "2025-09-23",
  "vas_acct_seq_no": 333, "vas_acct_cd": "333", "vas_acct_nm": "Test" }
→ 200, data: null
```
- Every field optional (null = unchanged); all-null body → `WBE_VALIDATION`.
- `maturity_date` empty string = clear (saving → current).
- `vas_acct_seq_no <= 0` (or `0`) = clear the mapping; positive = set after FK validation. The `vas_acct_cd`
  / `vas_acct_nm` echoes are overwritten with the authoritative `BUSI_VAS_ACCT_INFM` values.
- errors: `WBE_BANK_ACCOUNT_NOT_FOUND` · `WBE_BANK_ACCOUNT_INVALID_TYPE` · `WBE_BANK_ACCOUNT_INVALID_DATE`
  · `WBE_BANK_ACCOUNT_INVALID_VAS_ACCT` · `WBE_VALIDATION`

## 3. Bulk register

```
POST /api/v1/settings/bank-account/register
{ "bank_cd": "11704088",
  "items": [
    { "acct_no": "100000000099", "currency": "VND", "account_type_cd": "03",
      "nickname": "New", "description": "...", "acct_nm": "WEBCASH VN" }
  ] }
→ 200, data: { "inserted": 1, "skipped": 0 }
```
- Duplicate `(bank_cd, acct_no)` rows are silently skipped (counted in `skipped`).
- errors: `WBE_BANK_ACCOUNT_INVALID_TYPE` · `WBE_VALIDATION` (empty items / blank required fields)

## 4. Permission roster

```
GET /api/v1/settings/bank-account/{bankCd}/{acctNo}/permissions?type=VIEW
→ data: BankAccountPermissionResponse
{
  "kind": "VIEW",
  "account_label": "11704088 / 100000000001 (VND)",
  "members": [
    { "user_id": "u1", "user_nm": "김세명", "user_type": "USER",
      "phone_no": "•••••1234", "is_selected": true, "is_locked": false, "lock_reason": null },
    { "user_id": "u2", "user_nm": "Arthur", "user_type": "USER",
      "phone_no": null, "is_selected": false, "is_locked": false, "lock_reason": null }
  ],
  "total_selected": 1
}
```
- `type` query param ∈ `{ VIEW, WITHDRAWAL }`; `FULL_ACCESS` or any other value → `WBE_VALIDATION`.
- Admins are **not** listed (implicitly authorized; legacy `ADMIN_YN != 'Y'`).
- A non-admin caller's self-row is `is_locked=true, lock_reason="SELF"` when currently selected.
- errors: `WBE_BANK_ACCOUNT_NOT_FOUND` · `WBE_VALIDATION`

## 5. Permission replace

```
PUT /api/v1/settings/bank-account/{bankCd}/{acctNo}/permissions
{ "kind": "WITHDRAWAL", "user_ids": ["u1", "u2"] }
→ 200, data: null
```
- `user_ids` is the full desired set for the kind; service diffs grant/revoke, writes one audit event.
- Empty `user_ids` = revoke-all for the kind (allowed even on a non-withdrawal-bank).
- errors: `WBE_VALIDATION` (bad `kind` / `FULL_ACCESS`) · `WBE_BANK_ACCOUNT_NOT_FOUND` ·
  `WBE0452` (Withdrawal grant on a non-whitelisted bank) · `WBE_USER_NOT_IN_TENANT` ·
  `WBE_CANNOT_REVOKE_SELF`

## 6. Audit logs

```
GET /api/v1/settings/bank-account/{bankCd}/{acctNo}/audit-logs
→ data: BankAccountAuditResponse
{
  "items": [
    { "audit_logs_seq_no": 102, "audit_logs_actv": "Bank Account",
      "audit_logs_actv_dtl": "Edit bank account", "reg_id": "u1",
      "reg_nm": "김세명", "reg_dtm": "20240920101500" }
  ]
}
```
- Latest-first; reads both `WSVC_ID='bank-account'` (refactor) and `'comm_0183_001'` (legacy) so the
  popup surfaces live-JEX rows on a shared DB.
- Empty history → `{ "items": [] }` (never an error).
- Activity / detail strings are pre-localized at write time in the caller's locale.

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_VALIDATION` | 200/400 | all-null update body; unknown permission `kind`; bean validation |
| `WBE_BANK_ACCOUNT_NOT_FOUND` | 200 | account not in tenant |
| `WBE_BANK_ACCOUNT_INVALID_TYPE` | 200 | account-type code outside 02..08 |
| `WBE_BANK_ACCOUNT_INVALID_DATE` | 200 | maturity not `yyyy-MM-dd`-shaped |
| `WBE_BANK_ACCOUNT_INVALID_VAS_ACCT` | 200 | VAS FK missing / inactive / wrong tenant |
| `WBE_USER_NOT_IN_TENANT` | 200 | permission want-id has no active tenant link |
| `WBE_CANNOT_REVOKE_SELF` | 200 | non-admin caller tried to revoke their own access |
| `WBE0452` | 200 | Withdrawal grant on a non-whitelisted bank |
| `WBE1009` | 401 | JWT missing / invalid |
| `WBE1003` | 403 | authenticated but lacks `settings-core-bank-account` |
| `WBE9999` | 500 | Unexpected server error |

> `WBE_ADMIN_REQUIRED` exists in `messages.properties` ("Admin user must remain granted.") but is **not
> thrown** by the current code — admins are excluded from the roster, so there is no "admin must remain
> selected" guard to fire. Documented as a dormant message in [refactor-gap-analysis.md](refactor-gap-analysis.md).

Frontend consumption of these endpoints:
[wecms-web/docs/features/settings/bank-account/api-consumption.md](../../../../../wecms-web/docs/features/settings/bank-account/api-consumption.md).

# Login Credentials — Interface Specification (Backend REST contract)

All endpoints under base path **`/api/v1/login-credentials`** (NOT `/settings/...`). Wire format is
lowercase snake_case (global Jackson `SNAKE_CASE`); envelope is
`{ status, data, timestamp?, path?, trace_id? }`. Controller:
[LoginCredentialsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/controller/LoginCredentialsController.java).

## Authorization (all endpoints)

```java
@PreAuthorize("@menuAuthz.canAccess('settings-core-login-credentials')")   // class-level
```

Every endpoint (read and write) requires a valid JWT **and** the `settings-core-login-credentials`
menu grant. Missing/invalid JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`.
Tenant scope rides on the JWT (`TenantContext.currentBizKey()`); no endpoint accepts a `bizKey`.

---

## 1. List

```
GET /api/v1/login-credentials
```
```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "items": [
      {
        "type": "BANK",
        "bank_cd": "11704088",
        "institution": "Vietcombank",
        "modified_by": "admin",
        "modified_at": "20260601101500",
        "accounts": [
          { "seq_no": 12, "login_id": "alice", "nxisas_gb_seq_no": 0, "login_type": "Corporate" }
        ],
        "bank_accounts": [
          { "bank_cd": "11704088", "acct_no": "00500044787814", "acct_nick_nm": "Main VND" }
        ]
      },
      {
        "type": "GDT", "bank_cd": null, "institution": "GDT",
        "modified_by": "admin", "modified_at": "20260601101500",
        "accounts": [ { "seq_no": 1, "login_id": "0312345678", "nxisas_gb_seq_no": null, "login_type": null } ],
        "bank_accounts": []
      }
    ]
  }
}
```
Password ciphertext is never returned.

## 2. Bank picker

```
GET /api/v1/login-credentials/banks?search=viet
→ data: { "items": [ { "bank_cd": "11704088", "bank_nm": "Vietcombank", "bank_img": "/banklogo/VCB.png" } ] }
```
`search` optional (case-insensitive substring on bank name); blank/omitted returns the full
scraping-capable list.

## 3. Per-bank form info

```
GET /api/v1/login-credentials/banks/{bankCd}/form-info
→ data: {
    "bank_cd": "11704088",
    "sub_types": [
      { "bank_cd": "11704088", "seq_no": 0, "seq_nm": "Corporate Banking",
        "logo_url": "/banklogo/VCB_{LANG_CODE}.png", "id_label": "User ID",
        "password_label": "Password", "confirm_label": "Confirm Password", "extra_label": "" }
    ]
  }
```
Empty `sub_types` for unknown banks (frontend falls back to a single default form). The
`{LANG_CODE}` placeholder is substituted EN/KO/VI on the frontend.

## 4. Create bank credential

```
POST /api/v1/login-credentials/bank
{ "bank_cd": "11704088", "login_id": "alice", "password": "secret",
  "nxisas_gb_seq_no": 0, "extra": null, "login_type": "Corporate" }
→ 200, data: { "seq_no": 12 }
errors: WBE_VALIDATION (blank bank_cd/login_id/password) · WBE9999
```
Find-or-create dedup on `(bank_cd, login_id, nxisas_gb_seq_no)`; password encrypted before INSERT.

## 5. Atomic save (credential + accounts)

```
POST /api/v1/login-credentials/bank/register
{ "bank_cd": "11704088", "login_id": "alice", "password": "secret",
  "nxisas_gb_seq_no": 0, "extra": null, "login_type": "Corporate",
  "accounts": [
    { "account_no": "00500044787814", "currency": "VND", "acct_type_cd": "03",
      "account_nickname": "Main VND", "account_details": null, "acct_nm": "CONG TY ...",
      "bal": "19886030287", "maturity_date": null, "vas_acct_cd": null, "vas_acct_nm": null }
  ] }
→ 200, data: { "seq_no": 12 }
errors: WBE_BANK_ACCOUNT_REGISTER_EMPTY (empty accounts) · WBE_VALIDATION · WBE9999
```
One transaction: `BANK_CERT_INFO` + `ACCT_MSTR` (upsert) + `ACCT_AUTH_INFO` (fan-out on insert) +
audit. No saved account ⇒ no credential. (`accounts[]` mirrors `RegisterAccountsRequest.AccountEntry`;
field set is the banking register contract.)

## 6. Update bank credential

```
PUT /api/v1/login-credentials/bank/{seqNo}
{ "password": "new-secret", "old_password": "secret",
  "login_id": null, "extra": null, "login_type": null, "nxisas_gb_seq_no": null }
→ 200, data: null
errors: WBE_VALIDATION (all-null patch) · WBE2091 (old_password mismatch) · WBE2090 (not found)
```
All fields optional (null = unchanged). `old_password` guard is optional but, when present, must match
the decrypted stored password.

## 7. Verify new bank password (no DB write)

```
POST /api/v1/login-credentials/bank/{seqNo}/verify-update
{ "password": "new-secret" }
→ 200, data: ApiScrapeSessionResponse   { session_id, status, challenge_prompt, accounts, error_code, error_message }
errors: WBE2090 (not found) · (Coocon outcome carried in the scrape session, never persisted)
```
Runs an inline Coocon LOGIN with the stored bank context + the typed new password. On `COMPLETED` the
frontend follows up with §6 (PUT). On `ERROR` / unsatisfied challenge the existing row is untouched.

## 8. Create GDT credential

```
POST /api/v1/login-credentials/gdt
{ "login_id": "0312345678", "password": "secret", "extra": null }
→ 200, data: { "seq_no": 1 }
errors: WBE_LOGIN_CREDENTIAL_GDT_EXISTS (already one) · WBE_LOGIN_CREDENTIAL_GDT_VERIFY_FAILED (verify on, no COMPLETED) · WBE_VALIDATION
```

## 9. Update GDT credential

```
PUT /api/v1/login-credentials/gdt/{seqNo}
{ "login_id": null, "password": "new-secret", "extra": null }
→ 200, data: null
errors: WBE_VALIDATION (all-null) · WBE2090 (not found) · WBE_LOGIN_CREDENTIAL_GDT_VERIFY_FAILED (re-verify on credential change)
```

## 10. GDT engine config

```
GET /api/v1/login-credentials/gdt/engine-config
→ data: { "api_id": "NTS_NTS001_00001", "scrapping_module": "GLOBAL/VNM/PUBLIC_GDT",
          "scrapping_class": "COMMON", "bank_cd": "VIETNAM_GDT" }
```
Code constants (no DB) for the client-side GDT local-engine login test; replaces the legacy
JexAdmin/OSMC `nxisas_getModuleInfo` round-trip.

## 11. Delete

```
DELETE /api/v1/login-credentials/{type}/{seqNo}      type ∈ {bank, gdt}
→ 200, data: null
errors: WBE2092 (unknown type) · WBE2090 (not found)
```
Hard-delete the credential row; accounts are NOT cascade-deleted. Audit row written.

## 12. Reveal

```
POST /api/v1/login-credentials/{type}/{seqNo}/reveal
{ "user_password": "secret" }
→ 200, data: { "login_id": "alice", "password": "secret" }
errors: WBE2092 (unknown type) · WBE2090 (not found) · WBE2091 (confirmation failed)
```
The body password is the **credential's own** password. The only path that returns the decrypted
password.

## 13. Audit logs

```
GET /api/v1/login-credentials/{type}/{seqNo}/audit-logs
→ data: {
    "items": [
      { "audit_logs_seq_no": 9001, "audit_logs_actv": "Login Credentials",
        "audit_logs_actv_dtl": "Update login credential", "reg_id": "admin",
        "reg_nm": "Administrator", "reg_dtm": "20260601101500" }
    ]
  }
errors: WBE2092 (unknown type)
```
Latest-first, pre-localized to the caller's `Accept-Language`. Reads `WSVC_ID IN
('login-credentials', 'comm_0218_001', 'comm_0218_004')` (coexistence union).

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE2090` | 200 | Credential not found in tenant |
| `WBE2091` | 200 | Password confirmation failed (reveal / update `old_password`) |
| `WBE2092` | 200 | Unknown credential type in path |
| `WBE_LOGIN_CREDENTIAL_GDT_EXISTS` | 200 | A GDT credential already exists for this company |
| `WBE_LOGIN_CREDENTIAL_GDT_VERIFY_FAILED` | 200 | GDT verify-before-save did not reach COMPLETED |
| `WBE_BANK_ACCOUNT_REGISTER_EMPTY` | 200 | Atomic save with zero accounts |
| `WBE_VALIDATION` | 200/400 | Bean validation / all-null update patch |
| `WBE1009` | 401 | JWT missing / invalid |
| `WBE1003` | 403 | Authenticated but lacks `settings-core-login-credentials` |
| `WBE9999` | 500 | Unexpected server error |

Frontend consumption:
[wecms-web/docs/features/settings/login-credentials/api-consumption.md](../../../../../wecms-web/docs/features/settings/login-credentials/api-consumption.md).

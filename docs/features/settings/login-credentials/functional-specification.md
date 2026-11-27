# Login Credentials — Functional Specification (Backend)

The implemented behaviour of the Login Credentials backend. JEX behaviour reference (read-only): the
`comm_0218_*` WSVC family + `comm_0218_nxisas.ts` + `fnsh_0101_001_c001`. Deep design narrative + OQ
log: [../login-credentials.md](../login-credentials.md).

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Maps to JEX | Audit |
|---|---|---|---|---|---|
| 1 | List (bank groups ∪ GDT) | GET | `/api/v1/login-credentials` | `BANK_CERT_INFO_R007` UNION | No |
| 2 | Bank picker | GET | `/api/v1/login-credentials/banks[?search=]` | `nxisas_acctList_r002` | No |
| 3 | Per-bank form info | GET | `/api/v1/login-credentials/banks/{bankCd}/form-info` | `nxisas_getCertFormInfo` | No |
| 4 | Create bank credential | POST | `/api/v1/login-credentials/bank` | `comm_0218_003_c001` | Yes (on real insert) |
| 5 | Atomic save (credential + accounts) | POST | `/api/v1/login-credentials/bank/register` | `fnsh_0101_001_c001` | Yes (credential insert) |
| 6 | Update bank credential | PUT | `/api/v1/login-credentials/bank/{seqNo}` | `comm_0218_004_u001` | Yes |
| 7 | Verify new password (no write) | POST | `/api/v1/login-credentials/bank/{seqNo}/verify-update` | `comm_0218_nxisas` LOGIN | No |
| 8 | Create GDT credential | POST | `/api/v1/login-credentials/gdt` | `comm_0218_007_c001` (create branch) | Yes |
| 9 | Update GDT credential | PUT | `/api/v1/login-credentials/gdt/{seqNo}` | `comm_0218_007_c001` (update branch) | Yes |
| 10 | GDT engine config | GET | `/api/v1/login-credentials/gdt/engine-config` | `nxisas_getModuleInfo` (JexAdmin/OSMC) | No |
| 11 | Delete | DELETE | `/api/v1/login-credentials/{type}/{seqNo}` | `comm_0218_001_d001` | Yes |
| 12 | Reveal | POST | `/api/v1/login-credentials/{type}/{seqNo}/reveal` | `comm_0218_008_r001` | No |
| 13 | Audit logs | GET | `/api/v1/login-credentials/{type}/{seqNo}/audit-logs` | `comm_0218_005` | No |

`{type}` ∈ `bank` | `gdt` (case-insensitive). The two scraping endpoints (`/api/v1/banking/scraping/verify`
+ `/{sessionId}/otp`) used by the Add flow live on the **banking** surface, not under this base path.

**Not ported (DEAD LEGACY / out of scope):** see [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 2. Field contract

### 2.1 List row (`LoginCredentialGroup`)

| Wire field (snake) | Type | Notes |
|---|---|---|
| `type` | `"BANK"` / `"GDT"` | row kind |
| `bank_cd` | string \| null | bank code (null for GDT) |
| `institution` | string | bank display name (registry lookup) or literal `"GDT"` |
| `modified_by` | string \| null | latest `MOD_ID` across the group |
| `modified_at` | string \| null | latest `MOD_DTM` (`yyyyMMddHHmmss`) |
| `accounts` | `LoginCredentialAccount[]` | one per credential row: `seq_no`, `login_id`, `nxisas_gb_seq_no`, `login_type` |
| `bank_accounts` | `LoginCredentialBankAccount[]` | linked accounts (`bank_cd`, `acct_no`, `acct_nick_nm`); empty for GDT |

**Password ciphertext is never on the list wire.**

### 2.2 Create bank request (`BankCredentialCreateRequest`)

| Field | Required | Length | Rule |
|---|---|---|---|
| `bank_cd` | yes | ≤20 | `@NotBlank @Size(20)` |
| `login_id` | yes | ≤2000 | `@NotBlank @Size(2000)` |
| `password` | yes | ≤2000 | `@NotBlank @Size(2000)` — server encrypts before INSERT |
| `nxisas_gb_seq_no` | no | — | sub-type radio value; null → `0` (Corporate) |
| `extra` | no | ≤2000 | `@Size(2000)` → `CERT_INFO_DETL_CON2` (stored plain) |
| `login_type` | no | ≤2000 | `@Size(2000)` → `CERT_INFO_DETL_CON3` (stored plain) |

`bank/register` (`BankCredentialRegisterRequest`) adds `accounts: AccountEntry[]` (`@NotEmpty @Valid`)
and projects the credential fields onto the create DTO (`toCreateRequest()`).

### 2.3 Update bank request (`BankCredentialUpdateRequest`)

All five edit fields optional (null = leave unchanged): `login_id`, `password`, `extra`, `login_type`,
`nxisas_gb_seq_no`. Plus optional `old_password` (≤2000) — when present, decrypt guard → `WBE2091` on
mismatch (legacy `comm_0218_004_u001_act.jsp:55-57` parity). All-null patch → `WBE_VALIDATION`.

### 2.4 GDT request (`GdtCredentialRequest` / `GdtCredentialUpdateRequest`)

Create: `login_id` + `password` required (`@NotBlank @Size(2000)`), `extra` optional. Update: all three
optional; all-null → `WBE_VALIDATION`.

### 2.5 Reveal request (`RevealRequest`)

`user_password` (`@NotBlank @Size(2000)`) — the **credential's own** password, confirmed against the
decrypted stored value.

### 2.6 Verify-update request (`VerifyBankPasswordRequest`)

`password` (`@NotBlank @Size(2000)`) — only the new password; the service resolves the rest of the
bank context from the stored row and runs an inline Coocon verify (no DB write).

---

## 3. Business rules

### 3.1 Cert-crypto coexistence

`CERT_INFO_DETL_CON` (bank + GDT) is written and read by
[BankCertCryptoService](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCertCryptoService.java):

- **AES-256-CBC, all-zero IV, PKCS5, base64**, key = raw `SHA-256(seed)`, seed
  `wecms.crypto.legacy-cert-seed` (default `"wabooksvn"`). Byte-identical to legacy
  `public.encrypt()` / `public.decrypt()` — reads every existing JEX ciphertext and writes ciphertext
  legacy `decrypt()` reads back unchanged (two-way coexistence on a shared DB).
- `encrypt(null) → null`. `decrypt(...)` is **tolerant** (echoes non-base64 / undecryptable input
  verbatim — correct for the plaintext `CON2`/`CON3` slots). `decryptStrict(...)` is the **password**
  path — returns `null` (never echoes) when the value is not genuine legacy ciphertext, so a row
  written by the retired AES-GCM cipher is never forwarded to the bank as a password.
- The scheme is fixed-key / zero-IV CBC (deterministic — equal plaintexts yield equal ciphertexts);
  migration-safety over a stronger AEAD is the overriding requirement. Any change must be a PO/DBA
  re-encryption migration that cuts the live JEX system off these columns first.

### 3.2 Bank credential dedup (find-or-create)

A bank credential is uniquely identified within a tenant by
`(BANK_CD, CERT_INFO_CON, NXISAS_GB_SEQ_NO)`. Re-registering the same login (requester then approver,
or a retried save) **reuses** the existing `SEQ_NO` instead of minting a duplicate (legacy
`fnsh_0101_001_c001:166-194`); no audit row on the reuse branch, and the stored password is **not**
overwritten (password changes flow through Edit only). A concurrent-insert race surfaces as
`DataIntegrityViolationException` → re-read once → reuse the sibling row, else retry the MAX+1
allocation once.

### 3.3 GDT one-per-company + client-side verification

- **One per company:** `createGdt` blocks a second create with `WBE_LOGIN_CREDENTIAL_GDT_EXISTS`
  (legacy `comm_0218_003_c001` `WBE0003`). Edits go through `updateGdt`.
- **Verification is client-side (local NXiSAS.exe engine):** legacy parity — the Save action IS the
  login test, run in the browser against the locally-installed engine (`bankCd="VIETNAM_GDT"`, module
  `GLOBAL/VNM/PUBLIC_GDT`, class `COMMON` — served by `GET /gdt/engine-config`). The frontend POSTs
  `/gdt` only after a successful local-engine login, so `createGdt` / `updateGdt` are pure
  `GDT_CERT_INFO` upserts. **There is no server-side GDT verify.** The former flag-gated
  `verifyGdtCredentials` path (`wecms.login-credentials.gdt-verify-enabled` →
  `BankScrapingService.verify("VIETNAM_GDT")`) was **removed 2026-07-13** — it wrongly threw `WBE2093`
  because `VIETNAM_GDT` has no `NxisasBankConfig` entry (and correctly must not — GDT is not a Coocon
  bank). See `Claude Code (AI)/notes/gdt-login-credentials-wbe2093-fix.md`.

### 3.4 Atomic credential-plus-accounts save (`/bank/register`)

Legacy `fnsh_0101_001_c001` "all-on-Save" parity: reject empty accounts **before** any write →
`createBank` (find-or-create + audit) → `AccountRegistrationService.register` (per-account
`ACCT_MSTR` upsert + `ACCT_AUTH_INFO` fan-out on insert) — all in **one `@Transactional`**. A
registration failure rolls the whole transaction back. **No saved account ⇒ no `BANK_CERT_INFO`.**

### 3.5 Update password guard

`updateBank` with `old_password` set decrypts the stored ciphertext (`decryptStrict`) and rejects a
mismatch with `WBE2091` — same outcome the legacy WHERE-clause `decrypt(cert_info_detl_con) = ?`
produced (zero rows affected). The frontend also re-verifies the new password against Coocon
(`verify-update`) before firing the PUT — both guards must pass.

### 3.6 Delete — account rows preserved, credential pointer cleared

Delete removes the `BANK_CERT_INFO` / `GDT_CERT_INFO` row. Linked `ACCT_MSTR` **rows are preserved** (a
credential and its accounts are independent concepts) — but their `BANK_CERT_SEQ` **is set to `NULL`**
in the same transaction, so the pointer never dangles. `AccountService` surfaces those accounts as
"disconnected" until a credential is re-registered, and re-picking them in the Account Picker rewrites
`BANK_CERT_SEQ` to the new seq.

The GDT branch does not touch `ACCT_MSTR` at all — `GDT_CERT_INFO` owns no account linkage.

Deliberate divergence from legacy `comm_0218_001_d001_act.jsp` (which leaves the pointer dangling),
approved 2026-08-19:
[../../../bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md](../../../bugs/bank-credential-delete-leaves-dangling-acct-linkage-2026-08-19.md).

### 3.7 Reveal re-auth

`/reveal` decrypts and returns `(login_id, password)` **only after** the user re-enters the
credential's own password and it matches the decrypted value (`WBE2091` on mismatch / undecryptable).
This is the **only** path on which the password ciphertext is decrypted onto the wire.

---

## 4. Authorization

- Controller class-level: `@PreAuthorize("@menuAuthz.canAccess('settings-core-login-credentials')")`
  — every endpoint (read and write) requires the `settings-core-login-credentials` menu grant resolved
  from `USER_MENU_AUTH_INFM` (admins bypass the per-user ∩ but not the product scope). F-3 server-side
  menu-authorization model.
- Missing/invalid JWT → 401 `WBE1009` (via `JwtAuthenticationEntryPoint`); authenticated-but-
  unauthorized → 403 `WBE1003`.
- Path/body validation via `@Valid` on request records; `{type}` is normalized server-side
  (`bank`/`gdt`, else `WBE2092`).

---

## 5. Audit-log expectations

Every Create / Update / Delete writes to `AUDIT_LOGS_INFM` via the shared `AuditLogService`:

| Column | Value |
|---|---|
| `WSVC_ID` | `login-credentials` (slug — never `comm_0218_*` for writes) |
| `REF_NO` | `BANK_CERT_INFO` (bank) / `GDT_CERT_INFO` (gdt) |
| `REF_NO1` | `SEQ_NO` (`seqNo.toPlainString()`) |
| `REF_NO2` | `NULL` (single-column SEQ_NO PK) |
| activity | `audit.login_credentials.activity` |
| detail | `audit.login_credentials.{create,update,delete}.detail` |

List / reveal / audit-logs / banks / form-info write **no** audit row. Append runs
`@Transactional(REQUIRES_NEW)` and fans out one row per supported locale (mirrors legacy
`comm_0218_001_d001_act.jsp:49-87`).

**Read side (coexistence):** the audit-history popup reads `WSVC_ID IN ('login-credentials',
'comm_0218_001', 'comm_0218_004')` so rows the live JEX system wrote on a shared DB also surface
(read-only union; writes still use the slug).

---

## 6. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE2090` | 200 (business) | Credential not found in tenant (merges "not found" + "wrong tenant") |
| `WBE2091` | 200 | Password confirmation failed (reveal re-auth, or `old_password` guard on update) |
| `WBE2092` | 200 | Unknown credential `type` in path (not `bank`/`gdt`) |
| `WBE_LOGIN_CREDENTIAL_GDT_EXISTS` | 200 | A GDT credential already exists for the company (one-per-company) |
| `WBE_LOGIN_CREDENTIAL_GDT_VERIFY_FAILED` | 200 | GDT verify-before-save did not reach COMPLETED |
| `WBE_BANK_ACCOUNT_REGISTER_EMPTY` | 200 | Atomic save with zero accounts |
| `WBE_VALIDATION` | 200/400 | Bean-validation failure or all-null update patch |
| `WBE1009` / `WBE1003` | 401 / 403 | JWT missing-invalid / lacks the menu grant (shared) |
| `WBE9999` | 500 | Unexpected server error (shared) |

The Coocon scrape errors (`WBE_NXISAS`, OTP/challenge codes) surface on the **banking scraping**
endpoints, not this base path; the frontend maps them in the Add chain. Full request/response shapes:
[interface-specification.md](interface-specification.md).

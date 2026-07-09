# Login Credentials — Test Scenarios (Backend)

SIT/UAT scenarios + coverage map. Frontend scenarios:
[wecms-web/docs/features/settings/login-credentials/test-scenarios.md](../../../../../wecms-web/docs/features/settings/login-credentials/test-scenarios.md).

---

## 1. SIT / UAT scenarios

### List

| # | Scenario | Expected |
|---|---|---|
| L1 | List with bank + GDT credentials | one group per `BANK_CD` (accounts + linked `bank_accounts`) + one `GDT` group; no password ciphertext on the wire |
| L2 | Two credentials at the same bank, same login id, different sub-type | both surface as sibling `accounts[]` under one group, disambiguated by `login_type` |
| L3 | Tenant with no credentials | `items: []` |
| L4 | `modified_at` / `modified_by` | reflect the latest `MOD_DTM` / `MOD_ID` across the group |

### Bank picker / form info

| # | Scenario | Expected |
|---|---|---|
| P1 | `GET /banks` no search | all scraping-capable banks (`BANK_DETL_INFO_CD '9'='Y'`), `BANK_CD '11740099'` excluded, ordered by name |
| P2 | `GET /banks?search=viet` | case-insensitive name substring match |
| P3 | `GET /banks/{bankCd}/form-info` known bank | one `sub_types` entry per `SEQ_NO` with localised labels + `{LANG_CODE}` logo placeholder |
| P4 | form-info unknown bank | empty `sub_types` |

### Create (bank)

| # | Scenario | Expected |
|---|---|---|
| C1 | Valid `POST /bank` | 200; row persisted; `CERT_INFO_DETL_CON` = legacy-cipher ciphertext; `nxisas_gb_seq_no` null → `0`; audit (CREATE) written |
| C2 | Re-create same `(bank_cd, login_id, gb_seq)` | existing `SEQ_NO` reused; password NOT overwritten; **no** audit row |
| C3 | Missing `bank_cd` / `login_id` / `password` | `WBE_VALIDATION` |
| C4 | Concurrent create race (PK collision) | one-shot re-read/retry; same login reuses sibling row, else retried MAX+1 |
| C5 | Atomic `POST /bank/register`, ≥1 account | 200; `BANK_CERT_INFO` + `ACCT_MSTR` (upsert) + `ACCT_AUTH_INFO` (fan-out) all in one tx; audit written |
| C6 | Atomic save with empty accounts | `WBE_BANK_ACCOUNT_REGISTER_EMPTY`; **no** `BANK_CERT_INFO` written |
| C7 | Atomic save, account registration fails | whole tx rolls back — no credential, no accounts |
| C8 | `ACCT_AUTH_INFO` fan-out on new account | VIEW for every active tenant user (`USE_YN='Y'` registrant, `'N'` others); WITHDRAW only on the 5 withdrawal banks; not re-fanned on account UPDATE |

### Update (bank)

| # | Scenario | Expected |
|---|---|---|
| U1 | `PUT /bank/{seqNo}` change password | 200; new ciphertext stored; audit (UPDATE) written |
| U2 | All-null patch | `WBE_VALIDATION` |
| U3 | `old_password` matches stored | update proceeds |
| U4 | `old_password` mismatch | `WBE2091`; row untouched |
| U5 | `PUT` unknown / cross-tenant `seqNo` | `WBE2090` |
| U6 | `POST /bank/{seqNo}/verify-update` COMPLETED | scrape session returned; **no DB write**; frontend then PUTs |
| U7 | `verify-update` ERROR / challenge | scrape session returned; existing row preserved |

### GDT

| # | Scenario | Expected |
|---|---|---|
| G1 | `POST /gdt` first credential | 200; persisted; audit (CREATE) |
| G2 | `POST /gdt` second credential | `WBE_LOGIN_CREDENTIAL_GDT_EXISTS` |
| G3 | GDT verify-before-save flag ON, COMPLETED | persists |
| G4 | GDT verify-before-save flag ON, not COMPLETED | `WBE_LOGIN_CREDENTIAL_GDT_VERIFY_FAILED`; not persisted |
| G5 | GDT verify-before-save flag OFF (default) | persists without the login test (R&D) |
| G6 | `PUT /gdt/{seqNo}` change login id or password | re-verify (effective values); apply; audit (UPDATE) |
| G7 | `PUT /gdt/{seqNo}` extra-only | applied; **not** re-verified |
| G8 | `PUT /gdt` all-null | `WBE_VALIDATION` |
| G9 | `GET /gdt/engine-config` | constants `NTS_NTS001_00001` / `GLOBAL/VNM/PUBLIC_GDT` / `COMMON` / `VIETNAM_GDT` |

### Delete

| # | Scenario | Expected |
|---|---|---|
| D1 | Delete bank credential | 200; `BANK_CERT_INFO` row removed; `ACCT_MSTR` rows **preserved**; audit (DELETE) `REF_NO=BANK_CERT_INFO` |
| D2 | Delete GDT credential | 200; `GDT_CERT_INFO` row removed; audit `REF_NO=GDT_CERT_INFO` |
| D3 | Delete unknown / cross-tenant `seqNo` | `WBE2090` |
| D4 | Delete unknown `type` | `WBE2092` |

### Reveal

| # | Scenario | Expected |
|---|---|---|
| R1 | `POST /{type}/{seqNo}/reveal` correct credential password | 200; `{ login_id, password }` (decrypted) |
| R2 | Reveal wrong password | `WBE2091` |
| R3 | Reveal undecryptable / null ciphertext | `WBE2091` (decryptStrict null → mismatch) |
| R4 | Reveal unknown `seqNo` / `type` | `WBE2090` / `WBE2092` |
| R5 | Reveal is the only path returning the password | every other endpoint returns login id only |

### Audit logs

| # | Scenario | Expected |
|---|---|---|
| A1 | `GET /{type}/{seqNo}/audit-logs` | rows latest-first, pre-localized; `REF_NO2 IS NULL` matched |
| A2 | Rows written by the live JEX system (shared DB) | surface too — read union `WSVC_ID IN ('login-credentials','comm_0218_001','comm_0218_004')` |
| A3 | Audit `WSVC_ID` on refactor writes | always `login-credentials` (slug) |

### Cert crypto (coexistence)

| # | Scenario | Expected |
|---|---|---|
| X1 | Encrypt then `decrypt` round-trip | original plaintext returned |
| X2 | Decrypt a legacy JEX `CERT_INFO_DETL_CON` | succeeds (byte-identical pgcrypto AES-256-CBC) |
| X3 | Refactor-written ciphertext read by legacy `decrypt()` | matches (verify via SQL on a shared DB) |
| X4 | `decryptStrict` on a non-ciphertext / AES-GCM blob | returns `null` (never echoes) |
| X5 | `decrypt` (tolerant) on a plaintext `CON2`/`CON3` value | echoes the input verbatim |

### Authorization

| # | Scenario | Expected |
|---|---|---|
| Z1 | User with `settings-core-login-credentials` | all operations succeed |
| Z2 | Authenticated user without the grant | 403 `WBE1003` |
| Z3 | No / invalid JWT | 401 `WBE1009` |
| Z4 | Cross-tenant `seqNo` | `WBE2090` (never leaks tenant existence) |

---

## 2. Coverage notes

- **No dedicated `LoginCredentialsServiceTest` / `LoginCredentialsControllerTest` on disk** in this
  pass — the slice leans on the banking-tree tests for its reused collaborators:
  - `BankCertCryptoService` round-trip + legacy-compat decrypt (banking `bankcredentials` tests).
  - `BankScrapingService` verify outcomes (banking `nxisas` tests).
  - `AccountRegistrationService` upsert + `ACCT_AUTH_INFO` fan-out (banking tests).
  Add `LoginCredentialsServiceTest` covering the bank/GDT branch split, dedup find-or-create, the
  `WBE2091` guards (reveal + update), GDT one-per-company, and the audit fan-out — and a
  `@WebMvcTest` controller test asserting the lowercase snake_case wire shape + `{type}` normalization.
- **Cert-crypto cross-system check (X3)** can only be confirmed against a shared JEX DB — run the
  refactor `encrypt`, then `SELECT public.decrypt(cert_info_detl_con)` on the same row (read-only MCP)
  and compare. Cannot be exercised headless against the legacy DB function from a unit test.
- **GDT verify-before-save (G3/G4)** and **bank verify-update (U6/U7)** need a reachable Coocon
  gateway on a licensed Windows origin — SIT live-gateway pass, not headless.

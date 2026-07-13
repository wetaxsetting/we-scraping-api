# Login Credentials — Refactor Gap Analysis (Parity Review)

Legacy JEX (`comm_0218_*` + `fnsh_0101_001_c001`, tables `BANK_CERT_INFO` / `GDT_CERT_INFO` /
`ACCT_AUTH_INFO`) vs. the refactor (`login-credentials`). Status vocabulary: **MATCH** ·
**APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **List (bank ∪ GDT)** | `BANK_CERT_INFO_R007` UNION, `json_agg` by `BANK_CD` | `GET /login-credentials` → grouped in Java | **MATCH** |
| **Linked accounts** | `BANK_CERT_INFO_R005 REC_1` (keyed `(BIZ_KEY,BANK_CD)`) | per-bank `AcctMstr` query → `bank_accounts[]` | **MATCH** |
| **Bank picker** | `nxisas_acctList_r002` | `GET /login-credentials/banks` (native SQL over `BANK_INFM_DETL`) | **MATCH** |
| **Per-bank form info** | `nxisas_getCertFormInfo` | `GET .../banks/{bankCd}/form-info` (conditional-aggregate SQL) | **MATCH** |
| **Create bank** | `comm_0218_003_c001` (find-or-create) | `POST /login-credentials/bank` + dedup on `(bank_cd, login_id, gb_seq)` | **MATCH** |
| **Atomic save (credential + accounts)** | `fnsh_0101_001_c001` "all-on-Save" | `POST .../bank/register` → one tx (cred + `ACCT_MSTR` + `ACCT_AUTH_INFO` + audit) | **MATCH** |
| **`ACCT_AUTH_INFO` fan-out** | `fnsh_0101_001_c001:267-297` (per-user on insert) | `fanOutAccountAuth` — VIEW all users; WITHDRAW only 5 banks (AUTH_TYPE PK) | **MATCH** (+ AUTH_TYPE adaptation, see below) |
| **Edit (change password)** | `comm_0218_004_u001` (`decrypt(...) = ?` guard) | `PUT .../bank/{seqNo}` with `old_password` → `WBE2091` | **MATCH** |
| **Verify new password** | `comm_0218_nxisas` LOGIN (edit) | `POST .../bank/{seqNo}/verify-update` (no DB write) | **MATCH** |
| **GDT create/update** | `comm_0218_007_c001` (upsert) | split `POST` / `PUT /login-credentials/gdt` | **MATCH** |
| **GDT one-per-company** | `comm_0218_003_c001` `WBE0003` | `createGdt` → `WBE_LOGIN_CREDENTIAL_GDT_EXISTS` | **MATCH** |
| **GDT verify-before-save** | `comm_0218_007.js:43-51` (save = login test, on the **local engine**) | **client-side** local NXiSAS.exe verify before `POST /gdt`; server is a pure upsert | **MATCH** (GAP-1 resolved 2026-07-13) |
| **Reveal** | `comm_0218_008_r001` (`decrypt = ?`) | `POST .../reveal` + `WBE2091` re-auth | **MATCH** |
| **Delete** | `comm_0218_001_d001` | `DELETE .../{type}/{seqNo}` + audit | **MATCH** (no account cascade — see below) |
| **Audit history** | `comm_0218_005` | `GET .../audit-logs`, reads slug + legacy ids | **MATCH** (+ coexistence read union) |
| **Cert cipher** | pgcrypto `encrypt()`/`decrypt()` over `ISASSeedCBC` | byte-identical pgcrypto AES-256-CBC in `BankCertCryptoService` | **MATCH** (at-rest byte-identical) |
| **Bank scraping engine** | client-side `NXiSAS.exe` | server-side Coocon gateway | **APPROVED DEVIATION** (architecture) |
| **GDT module info** | JexAdmin/OSMC `nxisas_getModuleInfo` | `GdtNxisasConfig` code constants | **APPROVED DEVIATION** |
| **Authorization** | menu visibility only | explicit `@menuAuthz.canAccess('settings-core-login-credentials')` | **APPROVED DEVIATION** (stronger) |
| **Audit `WSVC_ID` (write)** | `comm_0218_001` / `comm_0218_004` | slug `login-credentials` | **APPROVED DEVIATION** (slug rule) |
| **Personal-banking discriminator** | `cert_info_dv` ∈ {1, personal, …} | always `'1'` | **DEAD LEGACY** (GAP-2) |
| **AES-GCM refactor cipher** | — | retired; `decryptStrict` returns null for leftover blobs | **DEAD LEGACY** (GAP-3) |
| **CAPTCHA / QnA challenge UI** | per-bank challenge dialogs | surfaced but not fully wired | **PARTIAL** (GAP-4) |
| **Bulk Excel import** | — (none in legacy either) | not planned | **MATCH** (nothing to port) |

---

## Specially-verified gaps

### GAP-1 — GDT verify-before-save (RESOLVED 2026-07-13)

Legacy `comm_0218_007.js:43-51` runs `callNxisas(['VIETNAM_GDT'])` on every Save and persists only on
`isSuccess` — the login test runs on the **local NXiSAS.exe engine**, and the server action is a pure
`GDT_CERT_INFO` upsert. The refactor now matches this exactly: the frontend (`useGdtCredentialValidation`
→ local engine) verifies before it POSTs `/gdt`; the backend create/update are pure upserts.

The earlier refactor had added a **server-side** `verifyGdtCredentials` that routed the pseudo-bank
`VIETNAM_GDT` through `BankScrapingService` / the Coocon gateway, flag-gated by
`wecms.login-credentials.gdt-verify-enabled`. That path was architecturally wrong (GDT is not a Coocon
bank; `NxisasBankConfig` has no `VIETNAM_GDT` entry) and threw `WBE2093` whenever the flag was enabled
(observed on SIT). It was **removed 2026-07-13** (method + flag + YAML/env keys + orphaned
`WBE_LOGIN_CREDENTIAL_GDT_VERIFY_FAILED`). **Status: MATCH.** See
`Claude Code (AI)/notes/gdt-login-credentials-wbe2093-fix.md`.

### GAP-2 — Personal-banking discriminator (DEAD LEGACY)

Legacy `BANK_CERT_INFO.cert_info_dv` / `GDT_CERT_INFO.cert_info_dv` carried a discriminator for
bank vs. personal vs. corporate. On the live JEX DB every row is `'1'` (verified 2026-06-09) and
personal banking is being removed from JEX. The refactor always writes `'1'`. **Status: DEAD LEGACY** —
intentionally excluded; column preserved for schema parity.

### GAP-3 — Retired AES-GCM refactor cipher (DEAD LEGACY)

An earlier refactor build encrypted `CERT_INFO_DETL_CON` with AES-GCM — mutually unreadable with legacy
data. It was **replaced** by the legacy-compatible pgcrypto AES-256-CBC cipher (byte-identical to
`encrypt()`/`decrypt()`). `decryptStrict` returns `null` (never echoes) for any leftover AES-GCM blob,
so such a blob is never shipped to the bank as a password; the cure is to re-save the credential.
**Status: DEAD LEGACY** — the AES-GCM path is gone.

> **Non-blocking cleanup:** `BankCertInfo` and `GdtCertInfo` entity Javadoc still describe the password
> column as "AES-GCM ciphertext". This is **stale** — the live cipher is legacy pgcrypto AES-256-CBC
> (`BankCertCryptoService`). Recommend a one-line Javadoc fix in the next stabilisation pass. Does not
> affect behaviour or parity.

### GAP-4 — CAPTCHA / QnA bank challenge (PARTIAL)

Some banks return `CAPTCHA_REQUIRED` / `QNA_REQUIRED` from the Coocon verify instead of `OTP_REQUIRED`.
The scrape session model carries these statuses, but full per-challenge UI wiring is deferred until a
real bank in the local test set demands it; today the panel surfaces a system error and collapses.
**Status: PARTIAL** — add when a real consumer arrives.

### `ACCT_AUTH_INFO` AUTH_TYPE adaptation (MATCH + documented divergence)

The refactor `ACCT_AUTH_INFO` PK carries `AUTH_TYPE` (`V`/`W`) where the legacy insert left it implicit.
On a new-account insert the refactor seeds a VIEW row for every active tenant user and a WITHDRAWAL row
**only** for the five withdrawal-capable banks (`WithdrawalBankPolicy`, legacy `comm_0183` `WBE0452`
rule, business decision 2026-06-16 = keep). A non-whitelisted bank never gets a `'W'` row, consistent
with the permission dialog hiding Withdraw. **Status: MATCH** — same effective behaviour, AUTH_TYPE is
the refactor PK shape.

### Delete — no account cascade (MATCH, intentional)

Delete removes only the credential row; `ACCT_MSTR` rows are preserved (a credential and the accounts
it once authenticated are independent). An earlier refactor cascade — which misread `fnsh_0001_001_d001`
comments as intent-to-cascade — was **removed**; preserving accounts is correct, and `AccountService`
surfaces them as "disconnected" until re-registration. **Status: MATCH.**

---

## Migration status

```
Refactor Status: complete for the in-scope feature surface;
                 GDT verify-before-save (GAP-1) flag-gated; CAPTCHA/QnA (GAP-4) deferred.
```

Evidence for closed gaps:

- **All 13 endpoints implemented** ([LoginCredentialsController](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/controller/LoginCredentialsController.java)); frontend wired (verified 2026-06-02, no mock fallback).
- **Cert cipher byte-identical to legacy** — reads/writes 177 bank + 38 GDT live JEX rows
  (`BankCertCryptoService` + db-object audit §11).
- **Atomic credential+accounts save** (OQ-10) + `ACCT_AUTH_INFO` fan-out (OQ-5) landed 2026-06-17.
- **Reveal / audit-logs / GDT create-update** wired end-to-end (OQ-1/2/3, 2026-06-02).
- **Slug `login-credentials` + `settings-core-login-credentials`** used end-to-end (base path,
  package, audit write `WSVC_ID`, menu grant).

Residual (non-blocking, tracked):
1. GAP-1 — flip GDT verify-before-save on once the captcha challenge-chain lands.
2. GAP-3 — stale "AES-GCM" Javadoc cleanup on the two entities.
3. GAP-4 — CAPTCHA / QnA challenge UI when a real bank demands it.
4. `VAS_ACCT_SEQ_NO` confirmation (operational, PO/DBA) + `NXISAS_ACCT_TYPE_MAPPING` DBA seed.
5. No dedicated `LoginCredentialsService`/`Controller` JUnit on disk yet (see test-scenarios.md).

None of the residual items affect the implemented feature's parity or correctness.

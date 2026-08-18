# Login Credentials — Legacy-vs-Refactor Audit

- **System:** Refactor (Spring Boot `wecms-api` + Next.js `wecms-web`)
- **Feature:** Settings → **Login Credentials** (bank certificate + GDT certificate management) — slug `login-credentials`
- **Date:** 2026-06-17 (consolidated 2026-06-23 from the feature parity review)
- **DB verified against:** JEX DEV (`wecmsvndevdb`) via read-only `postgres_dev_server_jexframe` MCP
- **Legacy source:** JEX `comm_0218_*` (list / create / edit / reveal / delete / audit / GDT) + `fnsh_0101_001_c001` ("all-on-Save" credential+accounts), tables `BANK_CERT_INFO` / `GDT_CERT_INFO` / `ACCT_AUTH_INFO`
- **Scope:** confirm the refactor Login-Credentials feature is byte-compatible with the live JEX cert data (read + write the same encrypted blobs), reproduces the legacy save / reveal / audit flows, and identify residual gaps.

> **Companion audits (distinct scope — do not conflate):**
> - **Password / login coexistence** (USER_INFM SHA-256, bootstrap removal) → [login-legacy-compatibility-2026-06-10.md](login-legacy-compatibility-2026-06-10.md). That audit covers *authentication*; **this** audit covers the *Settings → Login Credentials feature* (bank/GDT certificates).
> - **Feature parity matrix (live, per-row):** [../features/settings/login-credentials/refactor-gap-analysis.md](../../features/settings/login-credentials/refactor-gap-analysis.md) — this audit consolidates it; that file stays the authoritative per-row matrix.
> - **Deep design narrative + OQ log:** [../features/settings/login-credentials.md](../../features/settings/login-credentials.md).

## Executive Summary

The refactor Login-Credentials feature is **legacy-compatible and complete for its in-scope surface.** All 13
endpoints are implemented (`LoginCredentialsController`, wire base path `/api/v1/login-credentials`) and the
frontend is wired (verified 2026-06-02, no mock fallback). The decisive compatibility property — **the
certificate cipher is byte-identical to the legacy pgcrypto `encrypt()`/`decrypt()`** — was verified: the refactor
reads and writes the **177 bank + 38 GDT** live JEX credential rows cross-system (see §7).

Two residual items remain, neither a parity blocker: **GDT verify-before-save is flag-gated off** (GAP-1, awaits a
reachable gateway + the GDT captcha challenge-chain) and **CAPTCHA/QnA bank-challenge UI is deferred** (GAP-4).
Two cleanup items are documentation/Javadoc only (GAP-3 stale "AES-GCM" entity Javadoc; the personal-banking
discriminator GAP-2 is dead legacy).

## 1. Feature scope

Settings → Login Credentials manages the bank-scraping and GDT (tax-authority) certificates a tenant uses:
list (bank ∪ GDT grouped by bank), view linked accounts, register a bank credential (atomic credential + accounts
+ `ACCT_AUTH_INFO` fan-out + audit), edit/change-password (with old-password guard), verify, reveal (re-auth),
delete, GDT create/update, and audit history. Tenant-scoped by `BIZ_KEY`; gated by
`@menuAuthz.canAccess('settings-core-login-credentials')`.

## 2. Endpoints (13) — MATCH

All implemented on `LoginCredentialsController` (base `/api/v1/login-credentials` — note: **not** under
`/settings/`). List · banks picker · per-bank form-info · create bank · atomic register · edit (change password) ·
verify-update · GDT create · GDT update · reveal · delete · audit-logs (+ the per-bank linked-accounts read).
Frontend client `lib/api/loginCredentials.ts` consumes them 1:1 (verified, no mock fallback).

## 3. Save / reveal / edit flows — MATCH

- **Atomic save** (`POST .../bank/register`) mirrors legacy `fnsh_0101_001_c001` "all-on-Save": one transaction
  writes the credential + `ACCT_MSTR` + `ACCT_AUTH_INFO` + audit (OQ-10, landed 2026-06-17).
- **Edit / change password** (`PUT .../bank/{seqNo}`) reproduces the legacy `decrypt(...) = ?` old-password guard
  → `WBE2091`.
- **Reveal** (`POST .../reveal`) reproduces `comm_0218_008_r001` with a `WBE2091` re-auth gate.

## 4. `ACCT_AUTH_INFO` fan-out — MATCH (+ AUTH_TYPE adaptation)

On a new-account insert the refactor seeds a **VIEW** row for every active tenant user and a **WITHDRAWAL** row
only for the five withdrawal-capable banks (`WithdrawalBankPolicy`, legacy `comm_0183` `WBE0452` rule, business
decision 2026-06-16 = keep). The refactor `ACCT_AUTH_INFO` PK carries `AUTH_TYPE` (`V`/`W`) where the legacy insert
left it implicit — same effective behaviour, AUTH_TYPE is the refactor PK shape.

## 5. Delete — MATCH (no account cascade, intentional)

Delete removes only the credential row; `ACCT_MSTR` rows are preserved (a credential and the accounts it once
authenticated are independent). An earlier refactor cascade was removed; `AccountService` surfaces preserved
accounts as "disconnected" until re-registration.

## 6. GDT (tax authority) — MATCH, except verify-before-save (GAP-1)

GDT create/update split into `POST` / `PUT /login-credentials/gdt`; one-per-company guard
(`createGdt` → `WBE_LOGIN_CREDENTIAL_GDT_EXISTS`, legacy `comm_0218_003_c001` `WBE0003`). **Verify-before-save**
is reproduced server-side (`verifyGdtCredentials` via the shared Coocon gateway, pseudo-bank `VIETNAM_GDT`) but
ships **flag-gated off** — see GAP-1.

## 7. Certificate cipher — MATCH (at-rest byte-identical) — *the critical compatibility property*

- Legacy stores the credential password in `BANK_CERT_INFO` / `GDT_CERT_INFO` via pgcrypto `encrypt()`/`decrypt()`
  over the `ISASSeedCBC` scheme: **AES-256-CBC, zero IV, PKCS5 padding, base64 output, key = `sha256('wabooksvn')`**.
- The refactor `BankCertCryptoService` reproduces this **byte-for-byte** — encrypt/decrypt output is identical to
  the legacy DB functions, so the refactor and the live JEX system read and write the **same** ciphertext.
- **Verified cross-system:** the refactor reads/writes the **177 bank + 38 GDT** live JEX credential rows
  (cert cipher byte-identical; the dual-read audit-log path also reads the legacy `comm_0218_001/004` `WSVC_ID`s
  alongside the slug).
- The earlier refactor **AES-GCM** cipher (mutually unreadable with legacy data) was **retired**; `decryptStrict`
  returns `null` for any leftover AES-GCM blob so it is never shipped to the bank as a password (cure: re-save).
  See GAP-3.

## 8. Authorization — APPROVED DEVIATION (stronger)

Legacy gated by menu visibility only; the refactor adds explicit `@menuAuthz.canAccess('settings-core-login-credentials')`
on the controller. Audit `WSVC_ID` on write is the slug `login-credentials` (legacy wrote `comm_0218_001`/`comm_0218_004`);
the audit-log read path unions the slug with the legacy ids for coexistence.

## 9. Bank scraping engine — APPROVED DEVIATION (architecture)

Legacy scraping ran client-side via `NXiSAS.exe`; the refactor uses the **server-side Coocon smart-gateway**. GDT
module info that legacy read from JexAdmin/OSMC `nxisas_getModuleInfo` is held as `GdtNxisasConfig` code constants.
These are deliberate architecture deviations, not gaps.

## 10. Gaps & residual items

| # | Severity | Gap | Status | Action / owner |
|---|---|---|---|---|
| **GAP-1** | 🟡 PARTIAL | GDT verify-before-save (`verifyGdtCredentials`) ships **flag-gated off** (`wecms.login-credentials.gdt-verify-enabled=false`) — local R&D has no reachable gateway; full enablement awaits the GDT captcha challenge-chain | Code present, parity restored when the flag flips + captcha chain lands | backend + PO |
| **GAP-2** | ℹ️ DEAD LEGACY | `cert_info_dv` personal/corporate discriminator — every live row is `'1'`; personal banking being removed from JEX; refactor always writes `'1'` | Intentionally excluded; column preserved for schema parity | — |
| **GAP-3** | 🟢 CLEANUP **[CODE-TREE]** | `BankCertInfo` / `GdtCertInfo` entity Javadoc still says "AES-GCM ciphertext" — stale; the live cipher is legacy pgcrypto AES-256-CBC (`BankCertCryptoService`) | One-line Javadoc fix; no behaviour impact | dev (next stabilisation) |
| **GAP-4** | 🟡 PARTIAL | CAPTCHA / QnA bank-challenge UI — scrape-session model carries `CAPTCHA_REQUIRED`/`QNA_REQUIRED`, but per-challenge UI is deferred until a real test-set bank demands it | Add when a real consumer arrives | backend + frontend |

Also tracked (operational, non-blocking): `VAS_ACCT_SEQ_NO` confirmation (PO/DBA), `NXISAS_ACCT_TYPE_MAPPING` DBA
seed, and no dedicated `LoginCredentialsService`/`Controller` JUnit on disk yet (see the feature
`test-scenarios.md`).

## 11. DB-object inventory (tables touched)

| Table | Role | Baseline |
|---|---|---|
| `BANK_CERT_INFO` | Bank scraping credential (encrypted password via §7 cipher); discriminator `cert_info_dv='1'` | legacy (in inventory) |
| `GDT_CERT_INFO` | GDT (tax-authority) credential (same cipher) | legacy (in inventory) |
| `ACCT_AUTH_INFO` | Per-(account,user) VIEW/WITHDRAW authority fan-out (`AUTH_TYPE` PK, §4) | legacy (in inventory) |
| `ACCT_MSTR` | Bank accounts registered alongside a credential (atomic save §3); preserved on delete (§5) | legacy (in inventory) |
| `BANK_INFM_DETL` | Bank picker + per-bank form-info source | legacy (in inventory) |
| `AUDIT_LOGS_INFM` | Audit history; write `WSVC_ID='login-credentials'` (slug), read unions legacy `comm_0218_*` ids | legacy (in inventory) |

All tables are in the legacy baseline — see [../references/database/legacy-table-inventory.md](../../references/database/legacy-table-inventory.md).
No new table is introduced by this feature; the cipher is a Java service over existing columns (no DDL).

## Bottom line

The Settings → Login Credentials feature is **legacy-compatible and in-scope-complete.** The certificate cipher is
byte-identical to legacy pgcrypto (§7) — the refactor and the live JEX system authenticate the same 177 bank + 38
GDT credentials against the same DB. Residuals are **GAP-1** (GDT verify-before-save, flag-gated, awaiting the
captcha chain) and **GAP-4** (CAPTCHA/QnA UI, deferred); **GAP-3** is a stale-Javadoc cleanup and **GAP-2** is dead
legacy. Per-row parity detail stays in
[../features/settings/login-credentials/refactor-gap-analysis.md](../../features/settings/login-credentials/refactor-gap-analysis.md);
password/login coexistence is the separate [login-legacy-compatibility-2026-06-10.md](login-legacy-compatibility-2026-06-10.md).

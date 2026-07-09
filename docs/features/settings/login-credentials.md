# Settings > Login Credentials — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/login-credentials/`

## Feature Overview
- **Purpose:** Per-tenant master list of firm-banking login credentials the WeCMS gateway uses to scrape balances / transactions, plus a single GDT (Vietnam General Department of Taxation) credential for the e-invoice / tax portal. The only Settings panel that drives a live external system (Coocon/NXiSAS gateway): it runs a verify-before-save state machine, handles 2-factor challenges, and persists only after the gateway confirms the credentials work.
- **Business role:** Tenant administrators (holding the `settings-core-login-credentials` menu grant) add/edit/reveal/delete bank and GDT credentials and view per-row audit history.

## Menu / Route Mapping
- **Menu / panel:** Settings → Login Credentials
- **Menu permission ID (slug):** `settings-core-login-credentials` (verified against `LoginCredentialsController` class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-login-credentials')")`)
- **Wire base path:** `/api/v1/login-credentials` — **NOT** `/api/v1/settings/...` (verified against `@RequestMapping`)
- **Frontend mount / route:** Settings modal panel — no standalone route (`<LoginCredentialsPanel>` + stacked Add / Registered popup chains)
- **Legacy JEX counterpart:** WSVC family `comm_0218_*` (`_001` list/audit, `_003_c001` add bank, `_004_u001` edit password, `_007_c001` GDT upsert, `_008_r001` reveal, `_nxisas` Coocon orchestration) + `fnsh_0101_001_c001` (account save). Tables `BANK_CERT_INFO`, `GDT_CERT_INFO`, `ACCT_AUTH_INFO`, `ACCT_MSTR`, `AUDIT_LOGS_INFM`, `BANK_INFM_DETL` — legacy ids for traceability only.

## Current Status
- ✅ **Implemented:** Full-stack, verified no mock fallback (13 endpoints). Bank credential CRUD; **atomic credential+accounts Save** (`/bank/register` → `BANK_CERT_INFO` + `ACCT_MSTR` + `ACCT_AUTH_INFO` + audit in one transaction; no save ⇒ no credential). Coocon verify-before-save + OTP follow-up. **GDT** create/update (one per company). Password **`/reveal`** (decrypt only after re-auth). Delete (audit-logged). **`/audit-logs`** read (live, latest-first, localized). **Cert crypto = legacy pgcrypto** AES-256-CBC / zero IV / PKCS5 / base64, key `SHA-256('wabooksvn')` — byte-identical to legacy `encrypt()`/`decrypt()`, enabling true two-way coexistence on a shared DB. Audit writes use `WSVC_ID='login-credentials'` (slug); audit-history popup reads slug **plus** legacy `comm_0218_001`/`comm_0218_004`.
- 🟡 **Partially implemented:** GDT server-side verify-before-save ships **flag-gated off** (`wecms.login-credentials.gdt-verify-enabled`) pending the GDT captcha challenge-chain — legacy `comm_0218_007.js` always ran the login test on Save. `VAS_ACCT_SEQ_NO` left null on account registration (popup collects free-text VAS code/name; PO/DBA confirmation pending — operational, not code).
- ⚪ **Deprecated / Legacy / Not ported:** CAPTCHA / QnA bank-challenge branches surface but full UI wiring deferred until a real test-set bank demands them. Bulk import from Excel not planned (would bypass verify-before-save). Stale "AES-GCM" Javadoc on `BankCertInfo`/`GdtCertInfo` entities is a known doc-cleanup item (the cipher is legacy pgcrypto AES-256-CBC, not AES-GCM).

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [login-credentials/README.md](login-credentials/README.md) |
| Business flow | [login-credentials/business-flow.md](login-credentials/business-flow.md) |
| Database mapping | [login-credentials/database-mapping.md](login-credentials/database-mapping.md) |
| Functional specification | [login-credentials/functional-specification.md](login-credentials/functional-specification.md) |
| Interface specification | [login-credentials/interface-specification.md](login-credentials/interface-specification.md) |
| Migration guide | [login-credentials/migration-guide.md](login-credentials/migration-guide.md) |
| Refactor gap analysis | [login-credentials/refactor-gap-analysis.md](login-credentials/refactor-gap-analysis.md) |
| Source code index | [login-credentials/source-code-index.md](login-credentials/source-code-index.md) |
| Test scenarios | [login-credentials/test-scenarios.md](login-credentials/test-scenarios.md) |

## Historical Design Notes (preserved)
- **Verify-before-save is the core design guarantee.** A bank credential row lands in `BANK_CERT_INFO` only after Coocon returns `COMPLETED`; the plaintext password lives only in the in-flight scrape session and transient React state (never localStorage/sessionStorage). The earlier `/banking/scraping/verify` spike created no `BANK_CERT_INFO` row during the scrape — the atomic `/bank/register` Save is the canonical persist path.
- **Cert-crypto coexistence (decisive correction).** The refactor originally used a refactor-only AES-GCM cipher (mutually unreadable with legacy data). It was replaced with the byte-identical legacy pgcrypto cipher so the refactor reads every existing JEX `CERT_INFO_DETL_CON` (177 bank + 38 GDT DEV rows) and writes ciphertext live JEX `decrypt()` reads back. `decryptStrict` returns `null` (rather than echoing) on a foreign blob, so a row written by the retired cipher is never shipped to the bank as a password.
- **`WBE2091` old-password guard** on password edit mirrors legacy `comm_0218_004_u001_act.jsp:55-57` (`decrypt(cert_info_detl_con) = ?`); the frontend also re-verifies the new password against Coocon before the PUT.
- **[Legacy JEX]** Legacy `comm_0218_007.js` always ran the GDT login test on Save; the refactor's GDT verify is gated off until the captcha challenge-chain is wired.
- **[Not ported]** The transitional dual REST surface (`/banking/credentials` generic CRUD vs `/login-credentials/bank`) was consolidated — the canonical surface is `/api/v1/login-credentials`; the old standalone `/banking/credentials` spike route was removed.

# Settings > Login Credentials — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — backend (1 controller + 1 service + 3 repositories + 1 entity locally [GDT] reusing the banking `BankCertInfo` entity + 1 crypto service + 20 DTOs) and frontend wired (verified 2026-06-02, no mock fallback). Bank + GDT credential CRUD, password `/reveal`, and `/audit-logs` all call live endpoints. Account registration (`ACCT_MSTR` + `ACCT_AUTH_INFO` fan-out) + atomic credential-plus-accounts Save landed 2026-06-17.
**Module / menu:** Settings → **Login Credentials** (Settings modal panel — **no standalone route**)
**Refactor slug:** `login-credentials` (matches the JEX-traceability, backend, and frontend counterparts)
**Wire base path:** **`/api/v1/login-credentials`** (NOT `/settings/...`) — list · `/banks` · `/banks/{bankCd}/form-info` · `/bank` (create) · `/bank/register` (atomic save) · `/bank/{seqNo}` (update) · `/bank/{seqNo}/verify-update` · `/gdt` (create) · `/gdt/{seqNo}` (update) · `/gdt/engine-config` · `/{type}/{seqNo}` (delete) · `/{type}/{seqNo}/reveal` · `/{type}/{seqNo}/audit-logs`
**Menu permission slug:** `settings-core-login-credentials`

> This folder is the **primary documentation home** for Login Credentials on the backend. It
> consolidates and indexes the rich material that previously lived in a single flat file and in the
> audit tree; those files are **preserved and cross-linked** (see
> [§ Related & preserved docs](#related--preserved-docs)), not deleted. The JEX-side functional spec
> is the read-only source of truth for legacy behaviour.

---

## What this feature is

Login Credentials is the per-tenant master list of **firm-banking login credentials** the WeCMS
gateway uses to scrape balances / transactions on the tenant's behalf, plus a single **GDT**
(Vietnam General Department of Taxation) credential for the e-invoice / tax portal. A bank credential
is a `(BANK_CD, login_id, password)` triple persisted to `BANK_CERT_INFO`; the GDT credential is a
`(login_id, password)` pair persisted to `GDT_CERT_INFO`. **The password ciphertext never leaves the
server** except through the explicit `/reveal` re-auth path.

A tenant administrator can:

- **List** all credentials grouped by institution — one row per `BANK_CD` (with its accounts +
  linked bank accounts) plus one `GDT` row when configured.
- **Add a bank credential** through the Figma dialog chain: pick institution → pick bank → enter
  login id / password → backend Coocon **verify-before-save** (`POST /api/v1/banking/scraping/verify`)
  → OTP/CAPTCHA/QnA challenge follow-ups if the bank requires them → pick accounts → **atomic Save**
  (`POST /api/v1/login-credentials/bank/register`) writes `BANK_CERT_INFO` + `ACCT_MSTR` +
  `ACCT_AUTH_INFO` (+ audit) in one transaction.
- **Add a GDT credential** — `POST /api/v1/login-credentials/gdt` (one per company; server-side
  verify-before-save flag-gated, default off).
- **Edit** a bank credential — `verify-update` (no DB write) proves the new password works at the
  bank, then `PUT /api/v1/login-credentials/bank/{seqNo}` persists it with an optional
  `old_password` guard.
- **Reveal** a stored password — `POST /api/v1/login-credentials/{type}/{seqNo}/reveal` decrypts and
  returns the credential **only after** the user re-enters the credential's own password.
- **Delete** a credential — `DELETE /api/v1/login-credentials/{type}/{seqNo}` (audit-logged;
  accounts are NOT cascade-deleted).
- **Audit Logs** — `GET /api/v1/login-credentials/{type}/{seqNo}/audit-logs` returns per-row
  history, latest-first, already localized.

The panel is the **only Settings submenu that drives a live external system** (Coocon/NXiSAS
gateway). Every other Settings panel reads/writes its own DB rows; this one stands up a multi-step
state machine that crosses the gateway boundary, handles 2-factor challenges, and persists only after
the gateway confirms the credentials work.

---

## Cert-crypto coexistence (read this first)

`BANK_CERT_INFO.CERT_INFO_DETL_CON` and `GDT_CERT_INFO.CERT_INFO_DETL_CON` store the password
ciphertext in the **legacy pgcrypto-compatible** format produced by
[BankCertCryptoService](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCertCryptoService.java):

- **Cipher:** AES-256-CBC, **all-zero IV**, PKCS5 padding, base64-encoded.
- **Key:** raw 32-byte `SHA-256("wabooksvn")` (config slot `wecms.crypto.legacy-cert-seed`, default
  `"wabooksvn"`).
- **Byte-identical** to the legacy DB functions `public.encrypt(txt)` / `public.decrypt(txt)`, so the
  refactor reads every existing JEX `CERT_INFO_DETL_CON` (177 bank + 38 GDT rows on DEV) and writes
  ciphertext the live JEX `decrypt()` reads back unchanged — **true two-way coexistence on a shared
  DB.** This intentionally **replaced** the earlier refactor-only AES-GCM cipher (mutually unreadable
  with legacy data). Some entity Javadoc still says "AES-GCM" — **stale**, flagged in
  [refactor-gap-analysis.md](refactor-gap-analysis.md).

The service exposes `encrypt` (write), `decrypt` (tolerant — echoes non-ciphertext verbatim, used for
the plaintext `CON2`/`CON3` slots) and `decryptStrict` (password path — returns `null` rather than
echoing a foreign blob, so a row written by the retired AES-GCM cipher is never shipped to the bank as
a password).

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 13 — see [interface-specification.md](interface-specification.md) |
| Authority | `@PreAuthorize("@menuAuthz.canAccess('settings-core-login-credentials')")` — class-level on the controller (F-3 menu-auth model) |
| Tenant scope | `TenantContext.currentBizKey()` — `bizKey` is the leading PK column; never from a request param |
| Tables | `BANK_CERT_INFO`, `GDT_CERT_INFO` (credentials), `ACCT_MSTR` + `ACCT_AUTH_INFO` (atomic-save fan-out), `AUDIT_LOGS_INFM` (audit), `BANK_INFM_DETL` (bank picker + form-info, read-only) — all in the legacy inventory |
| Cert crypto | legacy pgcrypto AES-256-CBC, zero IV, PKCS5, base64, key `SHA-256('wabooksvn')` — byte-identical to legacy `encrypt()`/`decrypt()` |
| PK generation | `wb_get_next_val` via `LegacyFunctionRepository.nextBankCertSeqNo` / `nextGdtCertSeqNo` / `nextAcctMstrPrnSeq` (per-tenant MAX+1) |
| GDT verify-before-save | server-side via the shared Coocon gateway (pseudo-bank `VIETNAM_GDT`), flag `wecms.login-credentials.gdt-verify-enabled` (default **off**) |
| Bank scraping | server-side Coocon/NXiSAS gateway (`BankScrapingService`); credentials persist only after a `COMPLETED` verify |
| Audit | Create / Update / Delete write `AUDIT_LOGS_INFM` with `WSVC_ID = 'login-credentials'` (slug); `REF_NO = 'BANK_CERT_INFO'` / `'GDT_CERT_INFO'`, `REF_NO1 = SEQ_NO`. Reads (list / reveal / audit-logs / form-info) do not audit. The audit-history popup READS the slug **plus** legacy `comm_0218_001` / `comm_0218_004` (coexistence). |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`). Coocon/NXiSAS gateway DTOs keep vendor casing — NOT this slice's contract. |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.logincredentials
├── controller/LoginCredentialsController.java     13 endpoints; class-level @menuAuthz gate
├── service/LoginCredentialsService.java           list/CRUD/verify-update/reveal/audit; bank ∪ GDT;
│                                                   audit fan-out; GDT verify-before-save
├── repository/
│   ├── GdtCertInfoRepository.java                 JPA over GDT_CERT_INFO (find by bizKey / seqNo)
│   ├── BankPickerRepository.java                  JdbcTemplate over BANK_INFM_DETL (banks picker)
│   └── BankFormInfoRepository.java                JdbcTemplate over BANK_INFM_DETL (per-bank form info)
├── entity/
│   ├── GdtCertInfo.java                           @Table(GDT_CERT_INFO), @IdClass
│   └── GdtCertInfoId.java                         composite PK { bizKey, seqNo }
├── config/GdtNxisasConfig.java                    GDT local-engine module/class code constants
└── dto/                                           20 records (see source-code-index.md)
```

Cross-package collaborators (reused, not re-implemented):
[BankCertInfo](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/entity/BankCertInfo.java)
+ [BankCertInfoRepository](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/repository/BankCertInfoRepository.java)
+ [BankCertCryptoService](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCertCryptoService.java) (banking),
[AcctAuthInfo](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfo.java) (bank-account),
[AccountRegistrationService](../../../../src/main/java/com/kosign/wecms/domain/banking/service/AccountRegistrationService.java) + [BankScrapingService](../../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/BankScrapingService.java) (banking),
[AuditLogService](../../../../src/main/java/com/kosign/wecms/common/auditlog/service/AuditLogService.java) + [AuditLogEntryRepository](../../../../src/main/java/com/kosign/wecms/common/auditlog/repository/AuditLogEntryRepository.java) (common).

---

## Legacy & Migration Traceability

**Legacy JEX counterpart:** WSVC family `comm_0218_*` — `comm_0218_001` (list/audit), `comm_0218_003_c001` (Add bank), `comm_0218_004_u001` (Edit password, `decrypt(...)` guard), `comm_0218_007_c001` (GDT upsert), `comm_0218_008_r001` (reveal re-auth), `comm_0218_nxisas` (Coocon LOGIN + ACCT_LIST orchestration), and `fnsh_0101_001_c001` (Select Bank Accounts save → `ACCT_MSTR` + `ACCT_AUTH_INFO`). Tables `BANK_CERT_INFO`, `GDT_CERT_INFO`, `ACCT_AUTH_INFO`, `ACCT_MSTR`, `AUDIT_LOGS_INFM`, `BANK_INFM_DETL`.

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow per operation: list → add-bank chain (verify → challenge → atomic save) → edit (verify-update → PUT) → GDT create/update → reveal → delete → audit-logs |
| [functional-specification.md](functional-specification.md) | Operations matrix, field-by-field validation, cert-crypto coexistence, GDT one-per-company + verify-before-save, reveal re-auth, audit expectations, error surface |
| [database-mapping.md](database-mapping.md) | `BANK_CERT_INFO` / `GDT_CERT_INFO` / `ACCT_AUTH_INFO` (+ `AUDIT_LOGS_INFM` read) columns → entity field → DTO field → UI field, from the real entity code |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes per endpoint (lowercase snake_case), error-code registry, authorization, wire envelope |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (`_act.jsp` / IDO / `comm_0218_nxisas.ts` / entity / DAO) became its Spring counterpart, incl. dead legacy flows |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (list/detail/create/update/delete/reveal/audit-log/GDT-verify, both bank + GDT) + coverage notes |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with status + evidence |

---

## Related & preserved docs

These existing docs are **preserved** and remain authoritative for their slice. This README is the
entry point; deep detail lives here:

- **Detailed phase-by-phase implementation spec** (OQ-1…OQ-11, wire contract, Coocon orchestration):
  [../login-credentials.md](../login-credentials.md) — the original ~440-line flat spec, kept as the
  deep design narrative.
- **Legacy-vs-refactor audit (cert cipher §7 / db-object §11, GDT verify-before-save):**
  [docs/audits/login-credentials-legacy-vs-refactor-audit.md](../../../audits/login-credentials-legacy-vs-refactor-audit.md)
- **Login legacy-compatibility audit (password coexistence):**
  [audits/login-legacy-compatibility-2026-06-10.md](../../../audits/login-legacy-compatibility-2026-06-10.md)
- **Archived settings-menu-hub note:** archive/settings-menu-hubs/login-credentials.md _(removed — historical `archive/` folder deleted by project decision)_
- **Frontend feature folder:** [wecms-web/docs/features/settings/login-credentials/](../../../../../wecms-web/docs/features/settings/login-credentials/README.md)
- **Legacy table inventory (baseline):** [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)

---

## Open / residual items

1. **Stale "AES-GCM" Javadoc** on `BankCertInfo` and `GdtCertInfo` entities — the cipher is legacy
   pgcrypto AES-256-CBC, not AES-GCM (see the cert-crypto note above + the audit). Non-blocking
   doc/comment cleanup; tracked in [refactor-gap-analysis.md](refactor-gap-analysis.md).
2. **GDT server-side verify-before-save** ships flag-gated **off** (`wecms.login-credentials.gdt-verify-enabled`)
   pending the GDT captcha challenge-chain; legacy `comm_0218_007.js` always ran the login test on Save.
3. **`VAS_ACCT_SEQ_NO`** is left null on account registration (the popup collects free-text VAS
   code/name, not a `BUSI_VAS_ACCT_INFM` seq) — PO/DBA confirmation pending (operational, not code).
4. **CAPTCHA / QnA bank challenge** branches surface but full UI wiring is deferred until a real bank
   in the local test set demands them.

None of the residual items affect the implemented feature's parity or correctness.

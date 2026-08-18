# Login Credentials — Migration Guide (JEX → Spring)

How each JEX layer became its Spring counterpart. Deep narrative + OQ log:
[../login-credentials.md](../login-credentials.md). Legacy-vs-refactor audit:
[docs/audits/login-credentials-legacy-vs-refactor-audit.md](../../../audits/legacy-parity/login-credentials-legacy-vs-refactor-audit.md).

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen | `comm_0218_001_view.jsp` + `comm_0218_nxisas.ts` | `<LoginCredentialsPanel>` (Settings modal panel — no route) |
| List read | `IDO.BANK_CERT_INFO_R007` (UNION bank + GDT, `json_agg`) | `GET /login-credentials` → `LoginCredentialsService.list` (two per-tenant queries, grouped in Java) |
| Linked accounts | `IDO.BANK_CERT_INFO_R005 REC_1` | per-`(BIZ_KEY, BANK_CD)` `AcctMstr` query → `bank_accounts[]` |
| Bank picker | `nxisas_acctList_r002` → `BANK_INFM_DETL_R004` | `GET /login-credentials/banks` → `BankPickerRepository` (native SQL) |
| Per-bank form info | `nxisas_getCertFormInfo` (R006 + R005) | `GET /login-credentials/banks/{bankCd}/form-info` → `BankFormInfoRepository` (conditional-aggregate SQL) |
| Verify (LOGIN + ACCT_LIST) | `comm_0218_nxisas` → `_v2nxisas.callNxisas` | `POST /api/v1/banking/scraping/verify` (banking surface) → `BankScrapingService` |
| Create bank | `comm_0218_003_c001` | `POST /login-credentials/bank` → `service.createBank` (find-or-create + audit) |
| Atomic save (credential + accounts) | `fnsh_0101_001_c001` | `POST /login-credentials/bank/register` → `service.createBankWithAccounts` (one tx) |
| Account upsert + auth fan-out | `fnsh_0101_001_c001:265/359/267-297` | `AccountRegistrationService.register` + `fanOutAccountAuth` |
| Edit (change password) | `comm_0218_004_u001` (decrypt guard `:55-57`) | `PUT /login-credentials/bank/{seqNo}` → `service.updateBank` (`old_password` → `WBE2091`) |
| Verify new password | `comm_0218_nxisas` LOGIN (edit) | `POST /login-credentials/bank/{seqNo}/verify-update` → `service.verifyUpdate` (no DB write) |
| GDT upsert | `comm_0218_007_c001` (+ `comm_0218_007.js:43-51` login test) | `POST` / `PUT /login-credentials/gdt` → `createGdt` / `updateGdt` (verify-before-save flag) |
| GDT engine module info | `nxisas_getModuleInfo` (JexAdmin/OSMC `WABOOKSOSMCAPIUtil.callApi`) | `GET /login-credentials/gdt/engine-config` → `GdtNxisasConfig` code constants |
| Reveal | `comm_0218_008_r001` (`decrypt(cert_info_detl_con) = ?`) | `POST /login-credentials/{type}/{seqNo}/reveal` → `service.reveal` |
| Delete | `comm_0218_001_d001` (audit fan-out) | `DELETE /login-credentials/{type}/{seqNo}` → `service.delete` |
| Audit history | `comm_0218_005` | `GET /login-credentials/{type}/{seqNo}/audit-logs` → `service.auditLogs` |
| Cert crypto | `public.encrypt()` / `public.decrypt()` (pgcrypto AES-256-CBC over `ISASSeedCBC` payload) | `BankCertCryptoService` — **byte-identical** legacy pgcrypto AES-256-CBC (zero IV, PKCS5, base64, key `SHA-256('wabooksvn')`) |
| Entity / DAO (bank) | `wabooks.entity.BankCertInfo` + `BankCertInfoDAO` | `BankCertInfo` (reused, banking package) + `BankCertInfoRepository` |
| Entity (GDT) | `wabooks.entity.GdtCertInfo` | `GdtCertInfo` + `GdtCertInfoRepository` |
| Key-gen | `SeqHelper` → `wb_get_next_val` | `LegacyFunctionRepository.nextBankCertSeqNo` / `nextGdtCertSeqNo` (V140 #23/#26) |

---

## Identifier translation (slug, not WSVC id)

Every refactor identifier surface carries the kebab slug **`login-credentials`** — REST base path,
Java package (`…domain.setting.logincredentials`), class names (`LoginCredentialsController`,
`LoginCredentialsService`), i18n keys (`audit.login_credentials.*`), log tags, and the audit
**write** `WSVC_ID`. The menu permission code is the route-aligned slug
**`settings-core-login-credentials`**. Legacy WSVC ids (`comm_0218_*`) appear only in (1) JEX file:line
citations, (2) migration prose, and (3) the audit-history **read** union (`WSVC_ID IN
('login-credentials','comm_0218_001','comm_0218_004')`) — a coexistence read so the popup surfaces rows
the live JEX system wrote on a shared DB. Writes use the slug only.

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| List query | 90-line native UNION (`R007`) | two per-tenant queries grouped in Java |
| Cert cipher | `ISASSeedCBC` payload wrapped by pgcrypto `encrypt()` | direct legacy-compatible pgcrypto AES-256-CBC in `BankCertCryptoService` (byte-identical at rest) |
| Bank scraping | client-side `NXiSAS.exe` engine | **server-side Coocon gateway** (`BankScrapingService`) |
| GDT login test | client-side `callNxisas(['VIETNAM_GDT'])` on Save | **server-side** verify-before-save (flag-gated, default off); client still runs the local-engine test using `gdt/engine-config` |
| GDT module info | JexAdmin/OSMC `nxisas_getModuleInfo` round-trip | fixed code constants in `GdtNxisasConfig` |
| Create vs. save | one path | split: primitive `createBank` + atomic `bank/register` (credential + accounts, one tx) |
| Edit | one update | two-phase: `verify-update` (no write) then `PUT` (with `old_password` guard) |
| Authorization | menu visibility only | explicit `@menuAuthz.canAccess('settings-core-login-credentials')` (F-3) |
| Audit `WSVC_ID` (write) | `comm_0218_001` / `comm_0218_004` | slug `login-credentials` (legacy ids kept on the read side only) |

---

## Dead / out-of-scope legacy flows (not ported)

| Legacy flow | Disposition |
|---|---|
| `NXiSAS.exe` client-side scraping engine | **DEAD LEGACY** for the bank flow — replaced by the server-side Coocon gateway. The local engine survives only for the **GDT** login test (e-invoice path). |
| JexAdmin / OSMC `nxisas_getModuleInfo` runtime lookup | **DEAD LEGACY** — replaced by `GdtNxisasConfig` code constants (no JexAdmin/OSMC, no YAML). |
| Personal-banking credential discriminator | **DEAD LEGACY** — JEX `cert_info_dv` is now always `'1'`; personal banking is being removed from JEX. The refactor only writes `'1'`. |
| Legacy AES-GCM refactor cipher | **RETIRED** — replaced by the legacy-compatible pgcrypto cipher; `decryptStrict` returns null for any leftover AES-GCM blob so it is never shipped to the bank. |
| Bulk import from Excel | **Not planned** — credentials are entered through the Coocon-verified flow; bulk upload would bypass verify-before-save. |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification + evidence.

---

## Schema / key-gen migrations

| Item | Where |
|---|---|
| `BANK_CERT_INFO` / `GDT_CERT_INFO` / `ACCT_AUTH_INFO` schema | legacy tables, column-for-column DEV parity (Legacy Schema Parity Rule); in the inventory |
| `wb_get_next_val('bank_cert_info','seq_no',…)` | V140 #23 (per-tenant MAX+1), in the funnel |
| `wb_get_next_val('gdt_cert_info','seq_no',…)` | V140 #26 (per-tenant MAX+1), in the funnel |
| `NXISAS_ACCT_TYPE_MAPPING` seed (account-type auto-map) | manual DBA SQL `Claude Code (AI)/sql/nxisas_acct_type_mapping_20260617.sql` (off-inventory → PO baseline sign-off pending) |

No Flyway is auto-run (`ddl-auto: validate`); the schema is treated as external / pre-migrated.

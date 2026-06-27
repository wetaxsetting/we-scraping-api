# Own Account Transfer — Current Implementation Audit (Refactor vs Legacy Business Rules)

> **⚠️ SUPERSEDED crypto note (2026-06-24).** "AES-GCM decrypt server-side" below is **stale** — the cert
> cipher is now legacy-compatible **pgcrypto AES-256-CBC** (`BankCertCryptoService`). The AES-GCM build was
> retired. See [../../security/remaining-security-risk-register.md](../../security/remaining-security-risk-register.md) (R-1 / F-08).

> **System:** Refactor (Spring Boot + Next.js)
> **Audited against:** [own-account-transfer-legacy-bank-business-rules.md](own-account-transfer-legacy-bank-business-rules.md) (legacy source of truth)
> **Date:** 2026-06-01
> **Method:** Read-only inspection of `wecms-web/` + `wecms-api/`. **No implementation code modified** (audit/check only, per task rule #9).
> **Scope:** Banking > Own Account Transfer (`trns_0001_001` source picker + `trns_0001_007` transfer popup + NXiSAS local-engine flow).

---

## 1. Summary Verdict

The refactored Own Account Transfer is **substantially implemented and architecturally faithful** to the legacy flow, and goes **beyond Path A** — the composer + scraping-result execution surface, the NXiSAS bundle loader, the cert harness (Phase B), and the three engine 2nd-cert bridges (QNA / OTP / Confirm) have all landed. The NXiSAS local `.exe` boundary is correct: the browser bundle scrapes, Spring is strictly post-scrape.

**Verdict: PARTIAL MATCH — core flow correct; several per-bank business rules are simplified to global behaviour.**

The gaps are **not** in plumbing (engine, cert, audit, idempotency, tenant scope — all solid). They are in the **per-bank field/validation matrix** that the legacy `trns_0001_007.ts` `switch(bank_cd)` blocks encode. The refactor collapses several per-bank rules into a single global rule:

| Area | Legacy | Refactor | Severity |
|---|---|---|---|
| Remark byte-limit | Per-bank, per-direction (35–210 **bytes**) | Global 210 bytes both fields | **Medium** |
| Fee radios | Disabled/forced per bank (IBK forces key `N`/val `2`; Shinhan/VIETIN/Woori feeYN=N; only Vietcom active) | Always two active radios for every bank | **Medium** |
| Remark UI shape | `module`/`module4` = single sender remark tied to fee radio; `module2`/`module3` = independent appl/benf | Always sender(required)+recipient(optional) for every bank | **Low–Medium** |
| Empty-remark → COMP_NM | Auto-fill company name for Vietcom + Shinhan | Not implemented | **Low** |
| Vietcom DIGIBIZ job switch | `getModuleInfo().CLASS=="DIGIBIZ"` → non-ML jobs | Hardcoded CORPORATE / always `*_ML` | **Low** (deferred, documented) |
| Pre-transfer NAPAS validation | `fnReadNapasBankCode` (`trns_0001_001_r003`) runs **before** scrape | Validated server-side **after** scrape at intake | **Low–Medium** (sequencing) |
| IBK inline acct-pwd | From `fnif_0002_006` popup | Field disabled; empty `acctNumPWD` sent | **Low** (IBK `isPwdRequired=false`) |

Per-bank rules that the refactor **does** get right: NXiSAS job-type mapping per bank + within/inter, VIETIN ML payload mutations, Woori `nonLogin=false` session handling, account-password editable set (Shinhan/Woori for the 5 active banks), Shinhan Thu Thiem branch constant, idempotency, exact-VND amount tolerance, canonical `NAPAS_REMIT_TRNS_HSTR` write.

---

## 2. Files Inspected

### Frontend (`wecms-web/`)
| File | Role |
|---|---|
| `app/(dashboard)/banking/own-account-transfer/page.tsx` | Route (thin) |
| `components/banking/own-account-transfer/OwnAccountTransferClient.tsx` | Client wrapper |
| `components/banking/own-account-transfer/OwnAccountTransferPage.tsx` | Main source-picker page (filter, table, View Setting, engine banner, dialogs) |
| `components/banking/own-account-transfer/OwnAccountTransferComposerDialog.tsx` | Composer (Step-1 form → engine scrape → POST result → Update Balance) |
| `components/banking/own-account-transfer/TransferConfirmationDialog.tsx` | Engine confirm 2nd-cert popup (legacy `_confirm_transfer_template`) |
| `components/banking/own-account-transfer/OtpAuthDialog.tsx` | Engine OTP 2nd-cert popup |
| `components/banking/own-account-transfer/bank-profiles.ts` | Per-bank NXiSAS job/payload builders |
| `components/banking/own-account-transfer/types.ts` | In-memory camelCase types |
| `components/banking/own-account-transfer/mappers.ts` | Wire ↔ in-memory boundary |
| `components/banking/own-account-transfer/source-accounts-columns.tsx` | View Setting column registry |
| `components/banking/own-account-transfer/SourceAccountsTable*.tsx` | Table + footer |
| `components/banking/own-account-transfer/hooks/use-own-account-transfer.ts` | Read-side data hook + validation + password matrix |
| `components/banking/own-account-transfer/hooks/use-nxisas-engine.ts` | NXiSAS bundle loader / probe / `runScraping` facade |
| `components/banking/own-account-transfer/Engine*Dialog.tsx`, `EngineStatusBanner.tsx` | Engine not-installed / unsupported-OS / status chrome |
| `components/banking/own-account-transfer/preview/NxisasSecondaryAuthPreview.tsx` | DEV `?preview=nxisas` modal preview |
| `lib/nxisas/jexjs-adapter.ts` | JEX-runtime shim + Spring REST routing for bundle AJAX + `hasSavedBankCert` |
| `lib/nxisas/qna-bridge.ts`, `confirm-bridge.ts`, `otp-bridge.ts` | MutationObserver bridges from hidden engine popups → React dialogs |
| `lib/api/banking.ts` | `fetchOwnAccountSources` / `fetchOwnAccountTargets` / `postBalanceRefresh` / `postOwnAccountTransferScrapingResult` / `postEngineConnectionCheck` |
| `lib/types/api.ts` | OAT wire types (`ApiOwnAccountSourceRow` @2944, target, scraping-result request, cert fields) |

### Backend (`wecms-api/`)
| File | Role |
|---|---|
| `domain/banking/ownaccounttransfer/controller/OwnAccountTransferController.java` | REST surface (8 endpoints incl. composer + 4 bundle-facing nxisas/*) |
| `domain/banking/ownaccounttransfer/service/OwnAccountTransferService.java` | source-accounts / balance-refresh / engine-check / napas-info / target-accounts / scraping-result |
| `domain/banking/ownaccounttransfer/service/OatNxisasService.java` | cert-acct-list / decrypt(passthrough) / logging |
| `domain/banking/ownaccounttransfer/OwnAccountTransferConstants.java` | allow-lists, transfer-type codes, success code |
| `domain/banking/ownaccounttransfer/OwnAccountTransferAuditActions.java` | audit action keys |
| `domain/banking/ownaccounttransfer/dto/**` | request/response DTOs |
| `src/main/resources/messages.properties` | `WBE_OAT_*` / `WBE_OWN_ACCOUNT_TRANSFER_*` error codes (verified present @627–645) |
| `db/migration/V195__...idempotency.sql`, `V199__...view_setting_seed.sql` | OAT idempotency index + View Setting seed |
| `src/test/.../OwnAccountTransferServiceTest.java`, `OwnAccountTransferControllerTest.java` | Backend tests |

---

## 3. Business-Rule Checklist (per legacy §3–§13)

Legend: ✅ Implemented correctly · 🟡 Partial · 🟠 Implemented differently · ❌ Missing · ❔ Unable to verify

### 3.1 Main page / source picker (legacy §3)
| Rule | Status | Evidence |
|---|---|---|
| Allowed banks = exactly the 5 (Shinhan/IBK/Vietcom/VIETIN/Woori) | ✅ | `OwnAccountTransferConstants.ALLOWED_BANK_CDS_DEFAULT`; server ships `allowed_bank_cds` |
| Product-type H → VIETIN only | ✅ | `ALLOWED_BANK_CDS_HAENGSUNG = [11704001]`; service branches on `PrdtType.H` |
| Source list = active VND `03`/`05` accounts | ✅ | `findOwnTransferSourceRows` + HAENGSUNG saving gate; `CCY_VND` |
| Per-row Transfer button = admin OR W-grant | ✅ | `canTransfer` computed server-side from `ACCT_AUTH_INFO`; table disables button when engine not ready |
| Bank filter coerces unsupported → "all" (OQ-3) | ✅ | `listSourceAccounts` coercion |
| NXiSAS opened on page mount | ✅ | `useNxisasEngine` probe on page; `EngineStatusBanner` |
| Account-list loading sequence (onload → bank list → account list) | ✅ | `useOwnAccountTransfer` reload + `usePagination` |
| Sort (default→ASC→DESC) DB-side, whitelist | ✅ | `useTableSort` + `OwnAccountTransferSortField` whitelist |

### 3.2 Transfer popup / composer (legacy §4, §6, §8)
| Rule | Status | Evidence |
|---|---|---|
| Source-account select resets dependent fields | ✅ | `useEffect` keyed on `sourceKey` wipes target/amount/pwd/remarks (legacy `:392-412`) |
| From-Bank dropdown = any transferable VND account | ✅ | `availableSources` filter `canTransfer && ccyCd==='VND'` |
| Same-account prevention | ✅ | `validateDraft` self-transfer guard + target list excludes source row server-side |
| Amount masked numeric (vi-VN dot grouping) | ✅ | `formatVndDigits` / `stripToDigits` |
| Amount required + positive integer | ✅ | `validateDraft` |
| Amount ≤ available balance | 🟠 | Refactor **adds** a hard client guard (`amount > source.available`); legacy does **not** hard-block in Next handler (legacy §9 Open-Q #9). Stricter than legacy — acceptable but a behavioural divergence to note. |
| Account-password field editable set | ✅ | For the 5 active banks: enabled+required = Shinhan + Woori (matches legacy `inputPassBank`). `OAT_BANK_PASSWORD_REQUIRED` also lists KEBHana (dormant, harmless). |
| Account-password required set | ✅ | `isPasswordRequiredFor` → Shinhan/Woori (active). Vietcom/IBK/VIETIN not required. |
| IBK / KEBHana password via `fnif_0002_006/007` popup | 🟠/❌ | IBK field disabled, empty `acctNumPWD` sent (IBK `isPwdRequired=false` so low impact). KEBHana not in allow-list. Inline-popup password collection not ported. |
| Update Balance → engine BALANCE → `balance-refresh` → reload | ✅ | `handleUpdateBalance`; Woori `nonLogin:false` applied |
| Fee radios (Sender/Recipient) | 🟠 | Both radios always active for all banks. Legacy disables/forces per bank (see §3.4). |
| **Remark max-length per bank + direction (bytes)** | ❌ | Global 210-byte clamp on both fields (`clampUtf8Bytes(...,210)`, `validateDraft` 210). Legacy: Vietcom 195/180, IBK 35/200, Woori 100/210, Shinhan 200, VIETIN 210 (`:1258-1316`). |
| Remark module shape (single vs appl/benf) | 🟡 | Always sender(req)+recipient(opt). Legacy switches `module`/`module4` (single, fee-tied) vs `module2`/`module3` (appl/benf, fee disabled). |
| Empty-remark → COMP_NM (Vietcom/Shinhan) | ❌ | Not implemented (`:568-572`). |
| NBSP sanitize on remark | ✅ | `sanitizeRemark` (legacy `common.app.sanitize`) |
| Next-button enable = valid + engine ready | ✅ | `nextEnabled` |
| "Next" runs engine immediately (no in-app review step) | ✅ | `handleConfirm` → `buildNxisasOrgObj` → `engine.runScraping` (legacy `#next_step` → `fnTransferConfirmation`) |
| Reset/cancel reloads main list | ✅ | success path `onRecorded()`→`data.reload()`; close resets state |

### 3.3 Confirmation flow (legacy §5)
| Rule | Status | Evidence |
|---|---|---|
| No separate confirmation popup; confirm = engine `_confirm_transfer_template` | ✅ | `TransferConfirmationDialog` mirrors legacy template; surfaced via `confirm-bridge` event, not an in-app step |
| OTP / Q&A are engine-prompted, not HTML fields | ✅ | `OtpAuthDialog`/`QnaDialog` mounted only on engine bridge events |
| Per-bank `show*Area` visibility flags honoured | ✅ | `TransferConfirmationData.show*` flags drive section rendering |
| Final save → `NAPAS_REMIT_TRNS_HSTR` on engine success | ✅ | `recordScrapingResult` → `insertHistoryRow`; `TRNS_STLM_YN='N'` |
| Success → close + reload list | ✅ | `handleConfirm` success branch |
| Failure → keep dialog open for retry, preserve idempotency key | ✅ | retry-on-error contract honoured; same key replays to `already_recorded` |

### 3.4 Bank-specific matrix (legacy §6–§8)
| Bank rule | Status | Evidence |
|---|---|---|
| NXiSAS job type per bank + within/inter | ✅ | `bank-profiles.ts` (Shinhan NAPAS=`TRANSFER` special, IBK/Woori `TRANSFER`/`_INTERNAL`, Vietcom/VIETIN `*_ML`) |
| Shinhan Thu Thiem branch `79616016` + acceptName on NAPAS | ✅ | `shinhan` profile |
| VIETIN ML payload deletes (`feePaymentType`/`transType`/`remitBankCode`) | ✅ | `vietin` profile internal + NAPAS branches |
| Woori `nonLogin=false` + memo split | ✅ | `buildNxisasOrgObj` + `woori` profile |
| IBK memo=appl, memo2=benf | ✅ | `ibk` profile |
| Vietcom DIGIBIZ → non-ML | ❌ | Hardcoded CORPORATE/`*_ML`; documented deferral in `bank-profiles.ts` |
| IBK forced fee key `N`/val `2` | ❌ | Fee always from radio; no IBK forcing |
| Product-type H Woori `100↔DDA` rewrite | ➖ N/A | Under refactor, H tenants get VIETIN only — Woori never reaches an H tenant, so rule is unreachable. Note if Woori is ever added to H allow-list. |
| Module/Class per bank (NXISAS_MODULE/CLASS) | ✅ | `BANK_NXISAS_PROFILES` in `jexjs-adapter.ts`, `_isOsmc=false` sync branch |

### 3.5 NXiSAS local `.exe` (legacy §11) — see §5 below
### 3.6 Process/service IDs (legacy §10, §13)
| Legacy service | Refactor endpoint | Status |
|---|---|---|
| `trns_0001_001_r001` (source list) | `GET /source-accounts` | ✅ |
| `trns_0001_001_u001` (balance update) | `POST /balance-refresh` | ✅ |
| `trns_0001_001_r003` (NAPAS validate) | `GET /napas-bank-info/{cd}` | 🟡 exists but called server-side at intake, not pre-scrape (see §6) |
| `trns_0001_005_r001` (My Accounts) | `GET /target-accounts` | ✅ |
| `trns_0002_001_c001` (final save) | `POST /scraping-result` → `NAPAS_REMIT_TRNS_HSTR` | ✅ |
| `nxisas_acctList_r003` | `POST /nxisas/cert-acct-list` | ✅ (Phase B) |
| `nxisas_getModuleInfo` | (static `BANK_NXISAS_PROFILES`, `_isOsmc=false`) | 🟠 replaced by static table; endpoint `null` in `ENDPOINT_MAP` |
| `nxisas_decrypt` | `POST /nxisas/decrypt` (passthrough) | 🟠 pass-through (Coocon SEED-CBC not bundled; VND plaintext) |
| `nxisas_logging` | `POST /nxisas/logging` → `NXSIAS_MODULE_TRAN_LOG_HIS` | ✅ |
| `comm_0500_001_r001` (FX rate) | — | ➖ VND-only scope; FX not needed |
| `comm_0140/0141` (left-menu lists) | — | ➖ page chrome, out of OAT core |

---

## 4. Implemented Correctly (highlights)

- **NXiSAS local-engine boundary** — bundle loads from `/public/nxisas/`, scrapes browser-side; Spring is strictly post-scrape. `OwnAccountTransferService` never invokes the engine.
- **Per-bank job/payload profiles** — `bank-profiles.ts` ports the legacy `fnTransferConfirmation` switch verbatim, including VIETIN ML deletes and the Shinhan NAPAS `TRANSFER` (non-ML) special case.
- **Cert harness (Phase B)** — source-account DTO surfaces cert fields; `hasSavedBankCert` pre-flight; `cert-acct-list` fallback endpoint; AES-GCM decrypt server-side.
- **Engine 2nd-cert bridges** — QNA / OTP / Confirm popups mirrored into React via MutationObserver bridges; bundle continuation (`_v2nxisas.execute`) runs unchanged.
- **Idempotency + tampering guard** — `OAT_IDEMPOTENCY_KEY` partial-unique; replay returns `already_recorded=true`; bank-tx-id mismatch → `WBE_OAT_IDEMPOTENCY_CONFLICT`.
- **Tenant scope + permission** — every read/write constrains `BIZ_KEY`; non-admin W-grant re-validated at balance-refresh and scraping-result.
- **Audit everywhere (OQ-4)** — list, balance, engine-check, napas, scraping-OK/REJECT all write `AUDIT_LOGS_INFM` with slug `own-account-transfer`.
- **Exact-VND amount tolerance**, canonical `NAPAS_REMIT_TRNS_HSTR` write, seq_no race retry.
- **Engine status UX** — not-installed / unsupported-OS / load-error / timeout surfaces with retry, audited via `engine-connection-check`.

---

## 5. NXiSAS Local `.exe` Integration Status

**Correct.** The refactor uses the **local executable flow**, not a backend-API substitute for scraping.

| Concern | Required | Refactor | Status |
|---|---|---|---|
| `CooconiSASNX.init/open/execute` (scrape, login, OTP/QNA prompting, on-device cert) | MUST stay local `.exe` | `useNxisasEngine` loads the verbatim vendor bundle; `runScraping` → `_v2nxisas.callNxisas` | ✅ |
| `getCertInfo` (`nxisas_acctList_r003`) | May be backend | `POST /nxisas/cert-acct-list` + pre-populated on source row | ✅ |
| `getModuleInfo` | May be backend | Replaced by static `BANK_NXISAS_PROFILES` + `_isOsmc=false` (no AJAX) | 🟠 different-but-valid; Vietcom DIGIBIZ class switch not surfaced |
| `nxisas_decrypt` | Backend (key server-side) | `POST /nxisas/decrypt` **pass-through** (SEED-CBC not bundled) | 🟠 pass-through — fine for VND plaintext; real decrypt deferred |
| `nxisas_logging` | Backend (audit) | `POST /nxisas/logging` → one row per terminal job | ✅ |
| Windows-only / unsupported-OS gate | Required | `isWindows()` → `unsupported_os` + dialog | ✅ |

**Notes / risks on NXiSAS:**
- `nxisas_decrypt` is a no-op pass-through. Safe only while VND OAT payloads arrive plaintext (Vietin/Vietcom). A bank that ships SEED-CBC-encrypted output would silently produce garbage — documented in `jexjs-adapter.ts` and `OatNxisasService`. ❔ unverified for Shinhan/IBK/Woori payload encryption.
- `getModuleInfo` is a static map; the legacy runtime lookup that returns Vietcom `CLASS=="DIGIBIZ"` (which switches jobs to non-ML) is not reproduced → Vietcom always runs `*_ML`. If any tenant uses Vietcom DIGIBIZ, the wrong job kind is dispatched.

---

## 6. Popup & Confirmation Flow Status

| Surface | Status | Notes |
|---|---|---|
| Composer dialog (Step-1 form) | ✅ | `closeOnOutsideClick/Escape=false`; Cancel/X/success-path close only; sticky-submit gating |
| Transfer Confirmation popup | ✅ | `TransferConfirmationDialog` = single source of UI truth (real flow + `?preview=nxisas`); section/visibility flags from confirm-bridge |
| OTP popup | ✅ | `OtpAuthDialog` via otp-bridge |
| Security-Question (QNA) popup | ✅ | shared `<QnaDialog>` via qna-bridge; select-box click delegate ported in `jexjs-adapter` |
| Data passed main→composer | ✅ | sessionStorage replaced by React prop `source` row |
| Callback back to main page | ✅ | `onRecorded` → `data.reload()` |
| **Pre-transfer NAPAS validation popup/guard** | 🟡 | Legacy validates NAPAS code (`WBE1143`) before scraping; refactor relies on server-side intake validation **after** the engine ran. Sequencing divergence (see Risks). |
| stackLevel correctness | ✅ | engine modals at `stackLevel=4` above composer L1 |

---

## 7. Validation / Build / Test Commands

Per task rule #10 — run available checks if safe. Results recorded below (commands executed against `wecms-web/` and `wecms-api/`; the JEX `WECMS_STATIC` tree was **not** touched, per memory `reference_jex_static_tsc_pitfall.md`).

Commands run on 2026-06-01 (the JEX `WECMS_STATIC` tree was **not** touched, per memory `reference_jex_static_tsc_pitfall.md`):

| Check | Command | Result |
|---|---|---|
| Frontend type-check | `npx tsc --noEmit` (in `wecms-web/`) | ✅ **PASS** — exit 0, no errors |
| Frontend lint | `npm run lint` (in `wecms-web/`) | ⚠️ 25 errors / 58 warnings **— all in pre-existing unrelated files** (`lib/i18n/LocaleProvider.tsx`, `lib/api/mappers.ts`, `public/assets/settings/_write_icons.js`, `lib/nxisas/nxisas_cardTranList.ts` warnings). **Zero errors in any `own-account-transfer/` or OAT `nxisas` bridge/adapter file** (verified by filtered grep). |
| Backend tests | `./gradlew test --tests "*OwnAccountTransfer*"` (Java 21 — Temurin 21.0.10 confirmed on PATH) | ❌ **FAIL at `compileTestJava`** — could not run. Causes below. |

**Backend test-compile failures (4 errors — finding, not blocker):**

1. `OwnAccountTransferControllerTest.java:73` — `new OwnAccountTransferController(service)` passes **1** arg; the production controller now requires **2** (`OwnAccountTransferService, OatNxisasService`). The test predates the `OatNxisasService` (bundle-facing nxisas endpoints) addition.
2. `OwnAccountTransferControllerTest.java:92` — `new SourceAccountRow(...)` passes **14** args; the production record now has **18** (the Phase B cert fields `bank_cert_seq`/`cert_info_con`/`cert_info_detl_con`/`cert_info_detl_con2`/`nxisas_gb_seq_no` were added). The test predates the cert-field DTO extension.
3–4. `InternalTransferSortFieldTest.java:42,54` — `TRNF_YN` symbol not found (**unrelated** payment/internal-transfer feature, pre-existing).

**Conclusion on tests:** the OAT test suite **no longer compiles against current production code** — it was not updated when `OatNxisasService` and the Phase B cert fields landed (the same composer/cert MR that this audit shows shipped). Because `compileTestJava` fails for the whole module, no OAT test could execute. This is an audit finding (stale tests), recorded in §8 Risks #8. **Not fixed — audit-only task.**

---

## 8. Risks

1. **Remark byte-limits are global, not per-bank.** A 210-byte memo accepted by the UI can exceed IBK's 35-byte sender limit or Vietcom's 180-byte inter-bank limit → the bank IB site rejects mid-scrape, surfacing as a generic engine error after the user already committed. **Medium.**
2. **Fee radios always active.** For IBK the legacy forces key `N`/val `2` ("Sender pays") and disables the radios; the refactor lets the user pick "Recipient pays", which IBK's IB site will reject or mis-handle. Shinhan/VIETIN/Woori have `feeYN=N` (fee not user-selectable) yet the refactor presents an active choice. **Medium.**
3. **NAPAS validation happens after the scrape.** If the engine transfer succeeds but the NAPAS code is invalid/unknown, `scraping-result` rejects (`WBE_OAT_TARGET_NAPAS_UNKNOWN`) **after** money may have moved. Low real-world risk (targets are own accounts with pre-resolved codes), but the legacy ordering (validate first) is safer. **Low–Medium.**
4. **`nxisas_decrypt` pass-through** — silent data corruption if a supported bank ever returns SEED-CBC-encrypted output. **Low (unverified).**
5. **Vietcom DIGIBIZ job switch missing** — wrong NXiSAS job kind for DIGIBIZ-class Vietcom accounts. **Low** (depends on tenant Vietcom variant).
6. **Empty-remark → COMP_NM default missing** — Vietcom/Shinhan transfers with a blank remark won't auto-fill the company name; the bank statement memo differs from legacy. **Low.**
7. **Amount-vs-balance hard block is stricter than legacy** — could block a legitimate transfer where the displayed available balance is stale (legacy intentionally left this unguarded so the engine/bank is the authority). **Low.**
8. **OAT backend test suite is stale and does not compile.** `OwnAccountTransferControllerTest` calls the controller constructor with 1 arg (now 2) and builds `SourceAccountRow` with 14 fields (now 18). The tests were not updated when `OatNxisasService` + Phase B cert fields landed, so `./gradlew compileTestJava` fails and **no OAT test runs** — the feature currently has zero executable regression coverage at the controller level. **Medium** (quality/coverage risk; fix = update the two test fixtures to match the current controller + `SourceAccountRow` signatures).

---

## 9. Recommended Fixes (exact files / functions)

> **No code changes made.** These are recommendations only — implement on PO approval.

1. **Per-bank, per-direction remark byte-limits.**
   - Add a bank→{withinSender, withinRecipient, interSender, interRecipient} byte-limit table (new constant block in `components/banking/own-account-transfer/bank-profiles.ts` or a sibling `bank-remark-limits.ts`).
   - Consume in `OwnAccountTransferComposerDialog.tsx` `Step1Form` (`clampUtf8Bytes(..., limit)` + the `rightSlot` byte counter `/210` → `/${limit}`) and in `use-own-account-transfer.ts` `validateDraft` (replace the hard-coded `> 210`).
   - Direction = `currentSource.bankCd === target.bankCd` (within vs inter).
2. **Per-bank fee-radio enablement + forced values.**
   - Add a `feePolicy` to each profile in `bank-profiles.ts` (`{ radiosEnabled, forcedBearer? }`): Vietcom enabled; IBK forced `B`-equivalent key `N`/val `2`; Shinhan/VIETIN/Woori disabled (feeYN=N).
   - Consume in `Step1Form` transaction-fee section (disable radios / pin value) and ensure the profile builders already emit the right `feePaymentType` (VIETIN ML delete already handled).
3. **Vietcom DIGIBIZ job switch.**
   - Surface `CLASS` from `getModuleInfo` (re-enable the `nxisas_getModuleInfo` route in `jexjs-adapter.ts ENDPOINT_MAP`, or thread a `bankClass` flag) and branch `vietcom` profile in `bank-profiles.ts` to non-ML when DIGIBIZ.
4. **Pre-transfer NAPAS validation.**
   - In `OwnAccountTransferComposerDialog.handleConfirm`, before `engine.runScraping`, call a new `data.validateNapas(target.napasBankCd)` wrapping `GET /napas-bank-info/{cd}` and abort with `WBE1143`-equivalent on miss (NAPAS legs only).
5. **Empty-remark → COMP_NM default** (Vietcom/Shinhan).
   - In `handleConfirm` draft build, when `remarkForSender`/`remarkForRecipient` is blank and source bank ∈ {Vietcom, Shinhan}, default to the company name (from `userStorage`/`/auth/me`).
6. **Remark UI shape per module** (optional / lower priority) — switch the composer's remark section between single-remark (module/module4) and appl/benf (module2/module3) per bank, matching legacy `loadDataReloadTrnsDetlINFO`.
7. **IBK acct-pwd source** (optional) — if IBK ever needs the saved acct-pwd, port the `fnif_0002_006` value into the IBK profile's `acctNumPWD` (currently empty).
8. **Re-examine the amount-vs-balance hard block** — confirm with PO whether to keep the stricter refactor guard or match legacy's unguarded behaviour.

---

## 10. Conclusion

The refactor is **further along than the legacy-rules audit assumed** (that audit was written before the composer/execution MR landed). The engine boundary, cert harness, idempotency, audit, and per-bank job-payload mapping are correct and faithful. The remaining gaps are all in the **per-bank UI/validation matrix** — remark byte-limits, fee-radio enablement, the DIGIBIZ job switch, the empty-remark default, and pre-transfer NAPAS validation. None block the happy path for Shinhan/Vietcom/VIETIN/Woori same-bank/NAPAS VND transfers when cert material is configured, but they will cause **bank-side rejections** for IBK (fee + 35-byte sender remark) and for over-length remarks on any bank.

**Priority order:** (1) remark byte-limits, (2) fee-radio policy, (3) pre-transfer NAPAS validation, then the lower-severity items.

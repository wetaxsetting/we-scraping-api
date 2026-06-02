# NXISAS VCB Scraping Spike — Audit

**Date:** 2026-05-13
**Status:** Backend wiring complete, frontend wiring complete (spike-grade), live VCB gateway untested
**Owner:** Sonny / Arthur
**Tags:** `System: Refactor`, `Module: Banking`, `Feature: bank-credentials + bank-scraping`

---

## Goal

Prove end-to-end that the refactor stack (Spring Boot 3.5.13 + Next.js 16) can drive the legacy Coocon NXISAS scraping engine for one bank (VCB) — credential save → trigger account-list inquiry → handle one OTP round-trip from the browser → return the gateway's accounts.

Decision the spike was meant to inform: should Option A (in-process gateway client) be the rollout path for the remaining ~10 banks, or should we pivot to Option C (sidecar)?

## What I expected vs. what I found

The original plan (docs/wecms-api-and-wecms-web-are-lively-fairy.md (Needs Confirmation)) assumed `isas1.0.jar` was a vendor binary containing the scraping engine. Open questions Q1–Q6 focused on its method surface, thread safety, JDK compatibility, and licence terms.

**The JAR is not the engine.** It contains 16 entries, all under `com.coocon.securty.util.*` — only the SEED-CBC cipher (`ISASSeedCBC`) and a Base64 codec. No `IsasThread`, no `IsasJob`, no `kr.co.coocon.ibasews.*`.

The actual scraping "engine" is plain Java source the JEX project already owns at [WECMS DEV/WECMS/src/kr/co/coocon/ibasews/](../../../../WECMS%20DEV/WECMS/src/kr/co/coocon/ibasews/) (~85 source files: `NxisasRestClient`, `NxisasThread`, the `NxisasJob` enum, and ~80 DTOs). It boils down to a single HTTPS POST per job to the remote Webcash smart-gateway at [`https://sgw.webcashvietnam.com/domestic/finance/scrap/smart_gateway.jsp`](https://sgw.webcashvietnam.com) carrying a JSON envelope with an `Auth_key`. See [NxisasRestClient.java:44](../../../../WECMS%20DEV/WECMS/src/kr/co/coocon/ibasews/NxisasRestClient.java#L44).

| Open question | Answer |
|---|---|
| Q1 — JAR public API | Moot. JAR holds only SEED-CBC + Base64. The "engine" is Coocon source we already control. |
| Q2 — OTP transport model | Stateless HTTPS. Login result carries `CallBackfunc: "setOTP"` + `req: <prompt>`. Follow-up POST is an OTP job carrying `{ res: <user code> }` to the same URL. Server-side session id (`trSeq`) ties the two together. |
| Q3 — Thread safety | Trivially yes. Each job is an independent POST; no shared in-process state. |
| Q4 — Cert encryption key source | Legacy uses `ISASSeedCBC` (SEED-CBC) wrapped by a PostgreSQL `encrypt(?)` function. Refactor switched to JCE AES-GCM (`BankCertCryptoService`). Key is `wecms.crypto.cert-key` (Base64, 16/24/32 raw bytes). No JAR licence concern. |
| Q5 — Licence | Moot. No vendor JAR is embedded. The `isas1.0.jar` SEED cipher is intentionally not ported either — JCE AES-GCM replaces it. |
| Q6 — JDK compatibility | Moot. All ported sources are plain Java 8-style Lombok DTOs; compile cleanly under Java 21. |

**New blocker that replaces them:** real `coocon.smart_api_key` + Webcash IP allowlist + a VCB sandbox login are needed before the `@Tag("live")` integration test can run. The application config carries the legacy public fallback `fxAeocJbuweNTgv5FBns` (per JEX source line 40) which is **not** expected to authenticate against the production gateway.

## What landed

### Backend (`wecms-api`)

Migrations:

- `V86__bank_cert_info.sql` (Needs Confirmation — source file not found) — `BANK_CERT_INFO`, composite PK `(BIZ_KEY, SEQ_NO)`, 16 columns mirroring legacy column-for-column.
- `V87__nxsias_module_tran_log_his.sql` (Needs Confirmation — source file not found) — `NXSIAS_MODULE_TRAN_LOG_HIS`, composite PK `(UUID, REG_DTM)`, one row per outbound gateway call.

Both follow the Legacy Schema Parity Rule — `VARCHAR(14)` audit columns, `BIZ_KEY VARCHAR(20)`, `NUMERIC(10,0)` for seq columns. Neither has been applied yet — Flyway runs on first `bootRun`.

Java packages:

- `com.kosign.wecms.domain.banking.bankcredentials` — entity, repository, AES-GCM crypto service, service, DTOs, controller. Five endpoints: `GET / POST / PUT / DELETE /api/v1/banking/credentials`, plus the internal `loadForScraping(seqNo)` for the scraping flow.
- `com.kosign.wecms.domain.banking.nxisas.gateway.dto` — ported 11 minimal DTOs from `kr.co.coocon.ibasews.cmo.*` (login + OTP + acct-list happy path only). Wire shape preserved verbatim via `@JsonProperty`.
- `com.kosign.wecms.domain.banking.nxisas` — `NxisasGatewayProperties`, `NxisasGatewayClient` (Spring `RestClient`, ports `NxisasRestClient.java`), `NxisasConfig` (with `@EnableScheduling` for registry eviction).
- `com.kosign.wecms.domain.banking.nxisas.audit` — `NxsiasModuleTranLogHis` entity + `NxisasAuditLogger` (`REQUIRES_NEW`, one row per gateway call).
- `com.kosign.wecms.domain.banking.nxisas.session` — `NxisasScrapeSession`, `NxisasSessionRegistry` (in-memory `ConcurrentHashMap`, 5-min idle eviction via `@Scheduled`).
- `BankScrapingService` + `BankScrapingController` — `POST /api/v1/banking/scraping/account-list` and `POST /api/v1/banking/scraping/{sessionId}/otp`.

Error codes added to `messages.properties`: `WBE2050` (cred not found), `WBE2051` (cred missing bank_cd), `WBE2060` (session expired), `WBE2061` (gateway error), `WBE2062` (gateway not configured).

Config added to `application-local.yml`:

```yaml
wecms:
  nxisas:
    url: ${WECMS_NXISAS_URL:https://sgw.webcashvietnam.com/.../smart_gateway.jsp}
    api-key: ${WECMS_NXISAS_API_KEY:fxAeocJbuweNTgv5FBns}  # legacy fallback
    timeout-seconds: 60
    session-idle-seconds: 300
  crypto:
    cert-key: ${WECMS_CRYPTO_CERT_KEY:<base64 32-byte key>}
```

### Frontend (`wecms-web`)

- [lib/api/bankCredentials.ts](../../../../wecms-web/lib/api/bankCredentials.ts) — typed Axios wrappers + wire types matching the snake_case API contract.
- [app/(dashboard)/banking/credentials/](../../../../wecms-web/app/(dashboard)/banking/credentials/) — `page.tsx`, `CredentialsClient.tsx`, `AddCredentialDialog.tsx`, `OtpDialog.tsx`, `ScrapeResultDialog.tsx`.
- Locale keys added to `locales/en.json` only (KO/VI deferred — spike English-only).

### Tests

None landed. The spike's verification plan calls for `BankCertCryptoServiceTest` (AES-GCM round-trip) and a `@Tag("live")` `NxisasGatewayClientLiveTest` that hits the real Webcash gateway. Both are written in the plan (Needs Confirmation) but not implemented in this spike pass.

## What's intentionally NOT in the spike

- Persisting scraped accounts into `ACCT_MSTR` / `ACCT_TRNS_HSTR`. The 30+ column legacy entity ↔ 8-field gateway mapping is its own feature. Spike returns gateway data to the frontend dialog only.
- Captcha and QnA challenge round-trips. Status codes are surfaced as `CAPTCHA_REQUIRED` / `QNA_REQUIRED` but `submitOtp` is the only resume path wired.
- The other ~10 banks. Each needs a NAPAS code, an NXISAS `module` string, and a test login.
- Scheduled / batch scraping. Spike is on-demand only.
- Multi-instance session registry. Single Spring Boot process for now.
- Migration of legacy `ISASSeedCBC` ciphertexts. `BANK_CERT_INFO` starts empty on the refactor DB.
- KO / VI locale entries for the spike page. English only.
- Sidebar / Header entry for `/banking/credentials`. Reachable by direct URL only — the spike does not promote the page into the production menu.
- View Setting (§13) and Excel export (§13b) integration on the credentials list. The page is small admin CRUD, not a banking-inquiry list.
- `DataTableShell` adoption (§7a). Spike uses a plain `<table>`.
- `useDialog()` error routing. Spike uses inline error banners.

These are all deferred to the follow-up feature MR once Option A is approved.

## Verification status

| Check | Status | Notes |
|---|---|---|
| Flyway V86/V87 apply | ⏳ Untested | Will run on first `bootRun`. SQL was reviewed by hand against legacy entity files for column-for-column parity. |
| AES-GCM round-trip unit test | ❌ Not written | Code is in place; smoke test belongs in the follow-up MR. |
| Credential CRUD via Postman | ⏳ Untested | Backend compiles; not exercised. |
| WireMock-stubbed acct-list happy path | ❌ Not written | Plan called for it; deferred to follow-up. |
| Live VCB gateway integration | ❌ Blocked | Needs real `smart_api_key` + IP allowlist + VCB test login from Sonny. |
| Next.js E2E flow | ❌ Not exercised | Pages render; not clicked through. |
| Multi-tenant isolation | ⏳ Untested | Session registry rejects cross-tenant lookups by design (`registry.find(sessionId, bizKey, userId)`). Verify manually with two logins. |
| Wrong OTP → ERROR | ⏳ Untested | Service rejects non-`OK`/`0000` `Result_cd` and discards the session. Verify with a deliberately wrong code once live. |

## Architectural recommendation

**Continue with Option A** for the remaining banks.

Rationale:

- The "wrap a vendor JAR" risk that motivated weighing Option C never materialised — there is no JAR to wrap. The "gateway client" is a 150-line `RestClient` calling a documented HTTPS endpoint we already have working source code for.
- Thread safety (Q3) is trivial — sessions are stateless on the wire; in-process state lives only in `NxisasSessionRegistry` and is keyed `(bizKey, userId)`.
- Licence (Q5) is moot — we are not redistributing vendor code.
- Adding a new bank is: (1) a NAPAS code in the credentials dialog, (2) one `module` string passed to `NxisasGatewayClient.login(...)`, (3) a test login, (4) optionally a captcha / QnA branch in `BankScrapingService` if that bank uses one.

Option C (sidecar) was the contingency for "JAR won't load under Java 21". Since there is no JAR, the sidecar adds operational complexity for no benefit.

## Concrete next steps (in order)

1. **Get gateway access from Sonny:** real `smart_api_key`, Webcash IP allowlist for the dev laptop, VCB sandbox login. This is the only remaining blocker for end-to-end verification.
2. **Boot the refactor stack** (`./gradlew bootRun` + `npm run dev`) once V86/V87 apply cleanly. Validate the CRUD path via Postman.
3. **Write the AES-GCM unit test** + a WireMock-stubbed `NxisasGatewayClient` integration test. Add to CI.
4. **Exercise the live VCB flow** end-to-end through the Next.js page. Confirm the OTP round-trip closes.
5. **Promote `/banking/credentials` to a real feature MR** — add the sidebar entry, switch error banners to `useDialog()`, port the table to `DataTableShell`, add KO/VI locale entries, and add a basic View Setting + Excel export wiring per the project rules.
6. **Add the captcha + QnA resume paths** in `BankScrapingService` — currently only OTP is plumbed.
7. **Map the gateway's `NxisasAcctList` → `ACCT_MSTR` upsert** so scraped data persists into the refactor DB rather than living only in the dialog.
8. **Roll out to the next bank.** Most likely Shinhan or Woori (both in the firm-banking-settings allow-list already).

## Open follow-up questions for Sonny / PO

- Which Webcash `Auth_key` should the refactor use against the dev / UAT / prod gateways? Is there a per-environment key, or a shared dev key?
- Is the legacy `ISASSeedCBC` payload migration desired (decrypt legacy → re-encrypt AES-GCM), or does each tenant re-enter credentials on the refactor?
- For HAENGSUNG (`PRDT_TYPE = 'H'`), do any of the banks behave differently? The spike was written for the default WeCMS product type.
- Where should the captcha image / QnA prompt assets live on the wire? Legacy ships an image URL in the `req` field — confirm the refactor frontend can `<img src>` it cross-origin.

## Files added or modified

**Backend** ([wecms-api/](../../../)):

```
src/main/java/com/kosign/wecms/domain/banking/bankcredentials/
  entity/BankCertInfo.java
  entity/BankCertInfoId.java
  repository/BankCertInfoRepository.java
  service/BankCertCryptoService.java
  service/BankCredentialService.java
  controller/BankCredentialController.java
  dto/BankCredentialCreateRequest.java
  dto/BankCredentialUpdateRequest.java
  dto/BankCredentialRow.java
  dto/BankCredentialListResponse.java

src/main/java/com/kosign/wecms/domain/banking/nxisas/
  NxisasConfig.java
  NxisasGatewayClient.java
  NxisasGatewayProperties.java
  BankScrapingService.java
  audit/NxisasAuditLogger.java
  audit/NxsiasModuleTranLogHisRepository.java
  audit/entity/NxsiasModuleTranLogHis.java
  audit/entity/NxsiasModuleTranLogHisId.java
  controller/BankScrapingController.java
  dto/ScrapeSessionResponse.java
  dto/ScrapedAccount.java
  dto/StartScrapeRequest.java
  dto/SubmitChallengeRequest.java
  gateway/dto/NxisasApiIn.java
  gateway/dto/NxisasApiOut.java
  gateway/dto/NxisasCommon.java
  gateway/dto/NxisasJob.java
  gateway/dto/NxisasOutput.java
  gateway/dto/NxisasResult.java
  gateway/dto/NxisasThread.java
  gateway/dto/NxisasLoginJobInput.java
  gateway/dto/NxisasLoginJobResult.java
  gateway/dto/NxisasOTPJobInput.java
  gateway/dto/NxisasOTPJobResult.java
  gateway/dto/NxisasAcctListJobInput.java
  gateway/dto/NxisasAcctListJobResult.java
  gateway/dto/NxisasAcctListEntry.java
  session/NxisasScrapeSession.java
  session/NxisasSessionRegistry.java
  session/ScrapeChallengeKind.java

src/main/resources/db/migration/
  V86__bank_cert_info.sql
  V87__nxsias_module_tran_log_his.sql

src/main/resources/
  messages.properties        (WBE2050–WBE2062)
  application-local.yml      (wecms.nxisas.*, wecms.crypto.cert-key)
```

**Frontend** ([wecms-web/](../../../../wecms-web/)):

```
lib/api/bankCredentials.ts
app/(dashboard)/banking/credentials/page.tsx
app/(dashboard)/banking/credentials/CredentialsClient.tsx
app/(dashboard)/banking/credentials/AddCredentialDialog.tsx
app/(dashboard)/banking/credentials/OtpDialog.tsx
app/(dashboard)/banking/credentials/ScrapeResultDialog.tsx
locales/en.json (banking.credentials.*)
```

**Docs:**

```
docs/wecms-api-and-wecms-web-are-lively-fairy.md   (plan)
docs/refactor-wecms/audits/migration-audit/nxisas-vcb-spike.md               (this file)
```

**Reference (read-only, not modified):**

```
WECMS DEV/WECMS/lib/isas1.0.jar
WECMS DEV/WECMS/src/kr/co/coocon/ibasews/**/*.java
WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/*_act.jsp
WECMS DEV/WECMS/src/wabooks/entity/BankCertInfo.java
WECMS DEV/WECMS/src/wabooks/entity/NxsiasModuleTranLogHis.java
```

## Sign-off

This audit was produced by Claude Code in a single session on 2026-05-13. Code has not been executed against a live PostgreSQL or against the Webcash smart-gateway. The next dev to pick this up should start with the unit-test gap (item 3 in "Concrete next steps") before exercising the live flow.

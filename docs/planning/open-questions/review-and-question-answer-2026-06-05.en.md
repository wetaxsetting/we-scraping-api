# WeCMS Refactor — Complete Review & Question Response (2026-06-05)

**Prepared for:** Vietnam Development Team · KOSIGN Team · Project Management
**Subject:** Item-by-item, source-based response to the whole-project review (`review-and-question-2026-06-05`)
**Verification basis:** `wecms-api` backend source code as of 2026-06-08, cross-checked against the legacy JEX system as the functional source of truth.
**Format:** One dedicated section per finding (PR-001 → PR-058). No items grouped, none skipped.

---

## Executive Summary

> **Scope disclaimer.** This document is a source-code review and implementation assessment. Final production readiness, security approval, infrastructure approval, legal compliance approval, and business acceptance remain subject to the responsible stakeholders (PO, Security, DevOps, Legal, Management). Where a finding is labelled a "production blocker," it identifies an item that must be **resolved or formally signed off** before production — it is not a substitute for those stakeholders' own sign-off.

The WeCMS refactor backend is a **local R&D build**. It is **suitable for continued SIT** (development database, no live bank integration executed) but is **not ready for Production**.

Since the review was written on 2026-06-05, several findings have been closed or materially advanced by work completed 2026-06-06 / 2026-06-07. Two of the most severe items in the original review — an apparent wrong e-Invoice document-type mapping (PR-036) and stale-documentation items (PR-037, PR-038) — are confirmed resolved, and authorization enforcement (PR-007) has been substantially implemented (now partially resolved, see below).

**Resolved since the original review:** PR-036, PR-037, PR-038. **Substantially advanced but not fully closed:** PR-007 (menu authorization now enforced on business endpoints; residual un-annotated controllers remain — see its section).

**Items requiring resolution or sign-off before production** (severity in each item's section):
1. Real-money dispatch path: the in-refactor dispatch engine exists but its live legs are skeleton/mock-wired/unprovisioned, and reconciliation is not implemented — PR-019, PR-013, PR-014.
2. No optimistic locking; race-prone money-key allocation; idempotency/concurrency of the dispatch callback/result path not yet assessed — PR-021, PR-020, PR-029.
3. No server-side token revocation — PR-006.
4. No login auto-lock / brute-force protection — PR-008.
5. No integration tests over money flows — PR-039.
6. No defined production environment / topology / CI-CD / secrets — PR-043, PR-044.
7. No legal/regulatory sign-off for credential storage + scraping — PR-052.

(Per the risk legend, the strictly production-blocking (P0) items above are PR-019, PR-006, PR-008, PR-039; PR-013/PR-014 are P0 integrity gaps; PR-044/PR-052 are P1/owner-sign-off items listed here because they gate a production launch even though their severity is not P0.)

**Status labels used:** RESOLVED · PARTIALLY RESOLVED · OPEN · PO DECISION REQUIRED · DEVOPS CONFIRMATION REQUIRED · SECURITY CONFIRMATION REQUIRED · LEGAL CONFIRMATION REQUIRED · NOT APPLICABLE.
**Risk levels:** P0 (blocks production) · P1 (architecture/safety, do soon) · P2 (clarification / non-blocking). Resolved items show their original level as `N/A (was Pn)`.

---

# PR-001

## Question from Vietnam Team
Is the refactor's end goal a full JEX replacement or coexistence? CLAUDE.md says "no coexistence code" yet keeps `wb_get_next_val`, `sq_trco_key` "for JEX compat at cutover" — so is there a cutover or not?

## Answer
This is a genuine direction question that the code cannot answer on its own. The backend is built as a clean rewrite (no JEX runtime patterns are reproduced), yet a small number of legacy key-generation helpers are deliberately retained so that keys created by the refactor remain interoperable with the live system at a future cutover. Retaining those helpers only makes sense under a coexistence/cutover plan; keeping them while declaring "no coexistence" is contradictory at the strategy level. The decision must be made by the Product Owner because it governs the whole technical roadmap (key generators, data migration, cutover sequencing).

## Current Status
PO DECISION REQUIRED

## Risk Level
P1

## Evidence
* File: common/legacy/LegacyFunctionRepository.java
* Class: LegacyFunctionRepository
* Method: nextTrcoKey / nextApprLineNo / nextChartAcctSeqNo
* Line(s): retained legacy `wb_get_next_val` branches (see also migrations V113 / V91 / V95)

## Why It Matters
The replacement-vs-coexistence choice determines whether legacy generators must be preserved or replaced, whether two systems run in parallel, and how data is cut over. An undecided direction blocks the key-generation fix (PR-029) and the migration plan (PR-025).

## Recommended Action
PO to confirm: full replacement with a one-time cutover, or coexistence. Document the decision and align PR-025 and PR-029 to it.

---

# PR-002

## Question from Vietnam Team
What is the MVP scope for go-live? There are 42 controllers but many are "Backend implemented, frontend not verified". What is the Definition of "Done" for a feature (BE+FE+test+doc)?

## Answer
Confirmed: many features are backend-implemented but their matching frontend has not been verified, and there is no agreed Definition of Done. This is a planning/governance gap, not a code defect. "Backend implemented" must not be read as "feature complete".

## Current Status
PO DECISION REQUIRED

## Risk Level
P1 (planning/governance gap; not itself a production-blocking code defect)

## Evidence
* File: (project feature-status records / controller tree)
* Class: 46 `@RestController` classes under src/main/java (independently counted 2026-06-08)
* Method: n/a
* Line(s): n/a

## Why It Matters
Without a Definition of Done, features look complete on the backend but may have no working UI or tests, so release planning cannot be trusted.

## Recommended Action
PO to define the MVP feature list and a Definition of Done (backend + frontend + tests + docs), then re-classify every feature against it.

---

# PR-003

## Question from Vietnam Team
Multi-company: CLAUDE.md states "one user = one company" is the main flow, chooser is only a fallback. Is any real customer using multi-company? If not, should it be removed entirely to shrink the bug surface?

## Answer
Confirmed in the login flow: one-user-one-company is the primary path (a single active link auto-resolves and issues a JWT with no chooser). The multi-company chooser is a retained safe fallback for users who happen to have two or more active links; it is not a documented business requirement. Whether any real customer uses multi-company is a business fact only the PO can confirm.

## Current Status
PO DECISION REQUIRED

## Risk Level
P2

## Evidence
* File: domain/auth/service/AuthService.java
* Class: AuthService
* Method: login / buildChooserResponse
* Line(s): 162-189 (single-link auto-pick vs multi-link chooser), 324-361 (chooser builder)

## Why It Matters
A retained branch that no customer uses still carries maintenance cost and can hide bugs.

## Recommended Action
PO to confirm whether multi-company is in use. If not, schedule removal of the chooser branch to shrink the surface.

---

# PR-004

## Question from Vietnam Team
14 legacy PRDT_TYPE codes but only 3 modeled (F/M/H). When are the remaining product variants ported (Lumens readonly, HAENGSUNG masking…)? Which customer is waiting?

## Answer
Partly resolved as a fact: the WeCMS product-type set is exactly F / M / H. The other legacy letters are not WeCMS product types — they round-trip verbatim but are intentionally not modelled. Per-product behaviours (Lumens read-only overrides, HAENGSUNG payment-table layout and account masking, hidden expense column, daily-financial-HS gating) are deferred and port with each feature. The open part — which customer is waiting on each deferred behaviour — is a PO scheduling question.

## Current Status
PO DECISION REQUIRED

## Risk Level
P2

## Evidence
* File: common/enums/PrdtType.java
* Class: PrdtType
* Method: fromCode
* Line(s): enum constants F / M / H only; other letters resolve to empty (fail-safe)

## Why It Matters
Without knowing which customer needs which per-product behaviour, the deferred gates cannot be scheduled.

## Recommended Action
PO to name the customer driving each deferred per-product behaviour so the relevant feature MR can be scheduled.

---

# PR-005

## Question from Vietnam Team
Any SLA/KPI per module (e.g. Payment dispatch must finish within cutoff)?

## Answer
No measurable SLA/KPI thresholds are defined anywhere in the codebase. This is a business definition that must be supplied before "pass/fail" can be measured for operations.

## Current Status
PO DECISION REQUIRED

## Risk Level
P2

## Evidence
* File: n/a (no SLA/KPI configuration present)
* Class: n/a
* Method: n/a
* Line(s): n/a

## Why It Matters
With no measurable thresholds, operational success/failure cannot be objectively defined or monitored.

## Recommended Action
PO to define per-module SLA/KPI targets (e.g. dispatch cutoff completion time).

---

# PR-006

## Question from Vietnam Team
JWT stateless, no refresh token, no blacklist/revocation. When firing an employee / locking a user, the token stays alive until expires-in (3600s). For a real money-movement system, is this acceptable? → need refresh + revocation before go-live?

## Answer
Confirmed and still valid. Tokens are stateless with no server-side revocation, and logout performs no server-side invalidation — it returns success and relies on the client discarding the token. A locked or terminated user's existing token therefore stays fully valid until natural expiry (default ~3600s). This is acceptable for local R&D but not for a money-movement system in production.

## Current Status
OPEN

## Risk Level
P0

## Evidence
* File: domain/auth/controller/AuthController.java ; common/security/JwtAuthenticationFilter.java
* Class: AuthController ; JwtAuthenticationFilter
* Method: logout() ; doFilterInternal()
* Line(s): AuthController.java:59-62 (no-op logout); JwtAuthenticationFilter.java:34-96 (no revocation/blacklist check)

## Why It Matters
A stolen or stale token, or the token of a just-fired user, remains usable for up to the full token lifetime, with no way to cut it off.

## Recommended Action
Before any non-local deployment, add refresh-token + server-side revocation (e.g. a revocation/blacklist store checked in the JWT filter), or shorten token lifetime with an explicit revocation list. Security/PO to set the requirement.

---

# PR-007

## Question from Vietnam Team
`@PreAuthorize` is on no endpoint — authGrCd (ADMIN/USER) is only "advisory". Anyone can call admin endpoints. When is authorization enforced? This is a serious hole.

## Answer
PARTIALLY RESOLVED — and the reviewer's original concern was accurate at the time. Substantial authorization work landed on 2026-06-06, after the review: method security is now enabled (`@EnableMethodSecurity`), and the business endpoints that were previously ungated are now gated by a fail-closed menu-authorization guard combined with role checks. The guard denies unknown codes, codes outside the tenant's product tree, and codes the user has not been granted; an admin bypasses the per-user grant but never the product-tree gate. A coverage test enforces annotation on non-allow-listed handlers.

However, coverage is **not blanket**. An independent count on 2026-06-08 found **46** `@RestController` classes, of which **14 carry no `@PreAuthorize` at all**. Several are legitimately public/allow-listed (login, navigation, country reference, the global exception handler). But several others are not yet justified in a documented allow-list and warrant individual review — notably the bank-dispatch **callback** controllers `BulkExecutionResultController` and `BulkReceptionResultController` (which are skeletons today and whose external-facing security topology — allowlist + IP whitelist — is explicitly marked pending, TODO B7), plus `BankCredentialController`, `BankScrapingController`, `SyncHistoryController`, `OrganizerRuleController`, `EnvironmentSettingsController`, `BlockedCharsRuleController`, `ScreenOptionController`, `NotificationController`. A bank/VAN callback cannot present a user JWT, so the eventual auth model for the callback endpoints (currently behind the blanket `authenticated()` rule, with no `@menuAuthz`) is an open design point, not a closed one.

## Current Status
PARTIALLY RESOLVED (residual: un-annotated controllers + callback-endpoint auth model)

## Risk Level
P1 (the previously-reported "anyone can call admin endpoints" hole is closed for the business endpoints; the residual is the un-annotated set + the unresolved callback auth model). Original level: N/A (was P0).

## Evidence
* File: config/SecurityConfig.java ; domain/navigation/service/MenuAuthorizationGuard.java ; src/test/.../EndpointAuthorizationCoverageTest.java ; domain/payment/dispatch/callback/controller/BulkExecutionResultController.java
* Class: SecurityConfig ; MenuAuthorizationGuard ; EndpointAuthorizationCoverageTest ; BulkExecutionResultController / BulkReceptionResultController
* Method: (annotation @EnableMethodSecurity) ; canAccess / canAccessAny / canAccessAccountInquiry / canAccessEInvoice ; executionResult / receptionResult
* Line(s): SecurityConfig.java:22 (@EnableMethodSecurity), :44-46 (permitAll list = login/logout/health only, everything else `authenticated()`); MenuAuthorizationGuard.java:66-106 (fail-closed rule); BulkExecutionResultController.java:20-22 (TODO B7 — security allowlist + IP whitelist pending) ; independently counted 46 `@RestController` classes / 14 without `@PreAuthorize` (the earlier "30 of 43" figure was from project documentation, not a code count, and is not used here)

## Why It Matters
The serious hole the reviewer flagged — authenticated non-admin users reaching admin/business endpoints — is closed for the gated business endpoints. The remaining concern is narrower but real: the un-annotated set must be confirmed as an intentional, documented allow-list, and the dispatch callback endpoints (which will ingest bank results and flip payment status) need an explicit authentication model before any real-money environment.

## Recommended Action
Confirm each of the 14 un-annotated controllers is either intentionally allow-listed (document it) or add the missing `@PreAuthorize`. Decide and implement the authentication model for the `/api/v1/payment/dispatch/callbacks/*` endpoints (mutual TLS / IP allowlist / signed envelope) before they leave skeleton state. Housekeeping: delete the stale "advisory" comment in JwtAuthenticationFilter.java:52-55.

---

# PR-008

## Question from Vietnam Team
Auto-lock after 5 failures not implemented; no rate-limiting/brute-force protection. For a banking login screen, mandatory before production?

## Answer
Confirmed and still valid. Failed login attempts are recorded as FAIL rows, but there is no failed-attempt counter, no 5-failure auto-lock, and no rate-limiting/brute-force protection. The login screen is therefore open to unlimited password guessing.

## Current Status
OPEN

## Risk Level
P0

## Evidence
* File: domain/auth/service/AuthService.java
* Class: AuthService
* Method: login (records FAIL) ; (auto-lock counter not present)
* Line(s): 82-83 (deferred note), 119-140 (FAIL rows recorded but no counter/lock)

## Why It Matters
Unlimited password attempts on a banking login is a serious security exposure.

## Recommended Action
Implement the 5-failure auto-lock counter in AuthService.login (the data already exists in USER_LOGIN_HIS) and add brute-force/rate-limiting before any non-local deployment.

---

# PR-009

## Question from Vietnam Team
Account-level permissions (ACCT_AUTH_INFO V/W grant) exist in data, but do the transfer endpoints actually enforce W-grant on every path, or only some (own-account-transfer checks; what about the other payment modules)?

## Answer
Partially confirmed. An account-access guard (`AccountAccessGuard`) is present on the own-account-transfer path, and menu-level authorization is now enforced on the payment endpoints (PR-007). Whether that guard enforces the ACCT_AUTH_INFO **W (withdraw)** grant specifically — versus a read/view grant or only menu access — was **not line-verified** in this review, and account-level W-grant enforcement on the withdrawal account for the other payment modules (internal/vendor/expense/payroll/overseas) was likewise not verified. Both should be audited explicitly.

## Current Status
OPEN

## Risk Level
P1

## Evidence
* File: domain/banking/service/AccountAccessGuard.java
* Class: AccountAccessGuard
* Method: (account-access check — exact grant type not line-verified)
* Line(s): guard present on own-account transfer; the W-grant semantics and the other payment modules are unverified (inference from class role, not confirmed against source)

## Why It Matters
Menu authorization controls which screen a user can open; account-level W-grant controls whether they may withdraw from a specific account. Missing the latter on a payment path would be a missing authorization check.

## Recommended Action
Audit every payment module for account-level W-grant enforcement on the withdrawal account, not just menu authorization.

---

# PR-010

## Question from Vietnam Team
Reveal credential (login-credentials, contacts) returns the password in plaintext over the wire. What protects it (HTTPS only? audit? rate-limit?) — is who-revealed-what logged?

## Answer
Confirmed that the reveal action decrypts and returns plaintext, and that an audit-log endpoint exists for login credentials. Transport restriction (TLS-only), rate-limiting, and the completeness of per-reveal auditing were not confirmed in this review and require a security confirmation.

## Current Status
SECURITY CONFIRMATION REQUIRED

## Risk Level
P1

## Evidence
* File: domain/setting/logincredentials/controller/LoginCredentialsController.java
* Class: LoginCredentialsController
* Method: reveal ; auditLogs
* Line(s): `/reveal` + `/audit-logs` endpoints present (menu-gated at class level)

## Why It Matters
Returning sensitive plaintext without guaranteed audit and rate-limit makes bank-credential leaks untraceable.

## Recommended Action
Security to confirm every reveal is audited (who/what/when) and rate-limited, and that the endpoint is reachable over TLS only.

---

# PR-011

## Question from Vietnam Team
Where is the AES-GCM key (BankCertCryptoService) stored, how is it rotated? After backup/restore, can old credentials still be decrypted?

## Answer
Key storage and rotation are not documented in the codebase, and decryptability after backup/restore is unverified. This is an operational/security ownership question.

## Current Status
SECURITY CONFIRMATION REQUIRED

## Risk Level
P2

## Evidence
* File: (bank certificate crypto service)
* Class: BankCertCryptoService
* Method: encrypt/decrypt
* Line(s): key source not present in repository (externally supplied)

## Why It Matters
Losing or unmanaged rotation of the encryption key means losing access to all encrypted credentials.

## Recommended Action
Security/DevOps to define key custody, rotation, and a key-management system (e.g. KMS/Vault) before any real credentials are stored.

---

# PR-012

## Question from Vietnam Team
How is WECMS_JWT_SECRET managed across environments? Vault or env var?

## Answer
The JWT secret is supplied through an environment variable with fail-fast validation (the application refuses to start with a missing/too-short secret). No managed secret store is in place. This is acceptable for local R&D but not ideal for production.

## Current Status
DEVOPS CONFIRMATION REQUIRED

## Risk Level
P2

## Evidence
* File: common/security/JwtTokenProvider.java ; config/JwtProperties.java
* Class: JwtTokenProvider ; JwtProperties
* Method: (constructor fail-fast on missing/short secret)
* Line(s): secret read from env `WECMS_JWT_SECRET`

## Why It Matters
A plain environment-variable secret is harder to rotate and easier to leak than a managed secret.

## Recommended Action
DevOps to move the secret to a managed secret store per environment before deployment.

---

# PR-013

## Question from Vietnam Team
The NXiSAS engine runs in the user's browser (own-account-transfer), Spring only "trusts and stores the result". How is a client forging a "transfer succeeded" result prevented? scraping-result has idempotency, but is there a signature / reconciliation against the bank?

## Answer
Confirmed: the browser engine posts the scraping result back to the backend, which applies idempotency, amount-tolerance, and ownership checks before storing it — but there is no back-reconciliation against the actual bank statement and no cryptographic signature of the result. Trust ultimately sits at the client. This is a concrete technical/security integrity gap (not merely a decision point); the *acceptable mitigation* is a PO/architecture decision, but the gap itself is open regardless.

## Current Status
OPEN (+ PO/architecture decision)

## Risk Level
P0

## Evidence
* File: domain/banking (scraping-result handling) ; domain/banking/nxisas
* Class: (scraping-result service)
* Method: scraping-result intake (idempotency + amount tolerance + ownership)
* Line(s): guards present; no statement reconciliation present

## Why It Matters
A tampered client could report a fake "success" with no server-side reconciliation against the bank to detect it.

## Recommended Action
PO/architecture to require server-side reconciliation against bank statements (and/or signed results) before trusting a client-reported success.

---

# PR-014

## Question from Vietnam Team
When the Coocon gateway times out / dies mid-flow (LOGIN+TRANSACTION done but BALANCE lost), is the DB consistent? Is there a reconcile mechanism?

## Answer
Confirmed: no reconcile/repair mechanism was found for a gateway failure mid-flow. A half-completed scrape can leave balance/transaction state inconsistent with no automatic recovery.

## Current Status
OPEN

## Risk Level
P0

## Evidence
* File: domain/banking (integration paths)
* Class: (no reconcile job)
* Method: n/a
* Line(s): no reconciliation routine present

## Why It Matters
Without a reconcile mechanism, partial failures leave inconsistent data that no process corrects.

## Recommended Action
Add a reconcile/repair routine for interrupted flows; PO to define the consistency SLA.

---

# PR-015

## Question from Vietnam Team
Scraping session stored in-memory NxisasSessionRegistry → multi-instance / restart loses the session. Is single-instance-only acceptable? What about horizontal scale?

## Answer
Confirmed. The scraping challenge session is held in an in-memory concurrent map, so a restart or a second instance loses the in-flight OTP/QNA/CAPTCHA session. There is no shared session store. This is fine for a single instance but breaks under restart or horizontal scaling.

## Current Status
OPEN

## Risk Level
P1

## Evidence
* File: domain/banking/nxisas/session/NxisasSessionRegistry.java
* Class: NxisasSessionRegistry
* Method: (session map field)
* Line(s): 26-28 (`ConcurrentHashMap<UUID, NxisasScrapeSession>`)

## Why It Matters
Restart or multi-pod deployment loses the user's challenge session mid-flow, breaking the scrape.

## Recommended Action
Decide topology (PR-044). If more than one instance is used, move the session to a shared store (e.g. Redis).

---

# PR-016

## Question from Vietnam Team
Where are NXISAS's Coocon credentials, gateway endpoint, and sandbox-vs-production environment configured, and who provisions them?

## Answer
The gateway endpoint and key are configured per environment via application properties (distinct DEV/SIT and UAT/PROD hosts), but the provisioning owner and the formal sandbox-vs-production separation are not documented. This is a DevOps/PO ownership question.

## Current Status
DEVOPS CONFIRMATION REQUIRED

## Risk Level
P1

## Evidence
* File: application*.yml (gateway integration properties)
* Class: (gateway config properties)
* Method: n/a
* Line(s): `wecms.coocon.smart-api-*` style configuration

## Why It Matters
Unclear configuration ownership makes it unsafe to separate test from production environments.

## Recommended Action
DevOps/PO to document who provisions gateway credentials and how sandbox and production are kept separate.

---

# PR-017

## Question from Vietnam Team
OSMC→GDT (inquire/update): what is GDT's rate limit? update re-polls the entire cache and may burst — any throttle/batch-size cap?

## Answer
Confirmed: no throttle or batch-size cap was found on the GDT update re-poll path. An unbounded re-poll could exceed the provider's rate limit.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: domain/einvoice ; domain/operation (GDT inquire/update)
* Class: (GDT integration service)
* Method: inquire / update
* Line(s): no throttle/batch cap present

## Why It Matters
An unbounded re-poll can exceed GDT's rate limit and get blocked or time out.

## Recommended Action
Confirm GDT's rate limit and add a batch-size cap / throttle on the update path.

---

# PR-018

## Question from Vietnam Team
On-demand FX fetch from Shinhan: if the Shinhan API is down, what does the user see? There is a latest-date fallback (seen), but is there a "stale data" warning?

## Answer
Confirmed: a latest-available-date fallback exists when the requested date has no data, but the returned rate is not labelled "stale". A user may treat an old rate as current.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: domain/banking/service/ExchangeRateService.java
* Class: ExchangeRateService
* Method: (rate lookup with fallback)
* Line(s): 97-100 (falls back to latest available date; no stale label)

## Why It Matters
An unlabelled fallback rate can be mistaken for the current rate.

## Recommended Action
Add a "stale rate" indicator on the frontend (and a flag on the wire) when the fallback is used.

---

# PR-019

## Question from Vietnam Team
Is the real dispatch path (TB_PAY_TARGET / TB_PAY_FILE_*) connected to a real bank yet, or does it only write shadow tables? Who confirms the money actually moves?

## Answer
Corrected after direct source re-verification. It is **not** accurate to say the refactor only writes shadow tables or that the bank-send half is entirely deferred to the legacy JEX daemon. A substantial in-refactor dispatch engine exists under `domain/payment/dispatch/`, comprising:

- **VAN/EFS client seam** — `van/client/EfsApiClient` (interface), `van/client/EfsProperties`, `van/crypto/EfsCryptoService`; the active bean is `MockEfsApiClient`.
- **SFTP batch seam** — `sftp/SftpClient`, `sftp/SftpProperties`; active bean `MockSftpClient`.
- **Realtime dispatch service** — `service/RealtimeTransferService` (the EFS 202/204 realtime path).
- **Bulk reception service** — `service/BulkReceptionService`.
- **Result-check scheduler** — `scheduler/ResultCheckJob` + `service/ResultCheckService` (+ `service/DispatchResultResolver`).
- **Fixed-length file codecs** — `file/FixedLengthCodec`, `file/DepositHeader`, `DepositData`, `DepositTrailer`.
- **Firm-banking dispatch entities** — `TB_FIRM_TRAN`, `TB_FIRM_FILE_MST/DATA/RSLT`, `TB_FIRM_BAT_API_TRAN`, `TB_BATCH_SCHEDULE_INFO/DTL`, plus `TB_PAY_TARGET` / `TB_PAY_FILE_*`.
- **Bank-result callback controllers** — `callback/controller/BulkExecutionResultController` (01302) and `BulkReceptionResultController`, with `callback/service/*` and the `EfsCallbackEnvelope` DTO.

The reason **no live bank transfer is executed in the current local build** is that the live legs are skeleton or mock-wired, not that the architecture is absent: `RealtimeTransferService.transfer()` is a skeleton that throws "not implemented" (TODO B1/B2); the active `MockEfsApiClient.post()` throws `UnsupportedOperationException` so a live EFS path "can never be exercised by accident"; the callback controllers are skeletons ("No business processing yet"); and the EFS gateway base URL + per-VAN API key are unprovisioned (EfsApiClient TODO B1/B2). Separately, the legacy-style path `InternalTransferDispatchService.dispatch()` writes `TB_PAY_TARGET` / `TB_PAY_FILE_*` rows with `PROC_STAT='9'` (pending) and its Javadoc states the bank-network status flip is "handled by the live JEX dispatch daemon."

**Conflict to flag:** these two descriptions are in tension — `InternalTransferDispatchService` defers the bank leg to the external JEX daemon, while the newer `dispatch/` engine builds an in-refactor EFS/SFTP send path with its own callbacks. Which path is authoritative for go-live needs Backend-Lead clarification.

## Current Status
OPEN

## Risk Level
P0

## Evidence
* File: domain/payment/dispatch/service/RealtimeTransferService.java ; domain/payment/dispatch/van/client/MockEfsApiClient.java ; domain/payment/dispatch/van/client/EfsApiClient.java ; domain/payment/dispatch/callback/controller/BulkExecutionResultController.java ; domain/payment/internaltransfer/service/InternalTransferDispatchService.java ; domain/payment/dispatch/service/TransferDispatchExecutor.java
* Class: RealtimeTransferService ; MockEfsApiClient ; EfsApiClient ; BulkExecutionResultController ; InternalTransferDispatchService ; TransferDispatchExecutor
* Method: transfer (skeleton, throws "not implemented") ; post (throws UnsupportedOperationException) ; executionResult (skeleton) ; dispatch
* Line(s): RealtimeTransferService.java:17 + :27-32 (SKELETON ONLY, TODO B1/B2, not implemented); MockEfsApiClient.java:23-34 (throws UnsupportedOperationException — live EFS blocked on B1/B2); EfsApiClient.java:13-16 (base URL + API key unprovisioned); BulkExecutionResultController.java:17-22 (SKELETON, security allowlist/IP-whitelist pending TODO B7); InternalTransferDispatchService.java (class Javadoc — "bank-network integration … handled by the live JEX dispatch daemon")

## Why It Matters
The original wording would have told stakeholders the send-to-bank capability does not exist in the refactor at all; in fact it is being built (VAN realtime + SFTP batch + result callbacks + scheduler) but is mock/skeleton/unprovisioned. The accurate risk is: (a) the live path is not yet functional or proven, and (b) there is an unresolved architectural question of whether dispatch is owned by the refactor's EFS engine or delegated to the JEX daemon. Both must be settled before real money flows.

## Recommended Action
Backend Lead to confirm the authoritative dispatch path (refactor EFS engine vs. JEX daemon) and reconcile the conflicting `InternalTransferDispatchService` comment. Complete and provision the chosen live leg (EFS B1/B2 URL+key, or daemon contract) with a settlement-confirmation/reconciliation path before enabling real money. Keep real money strictly out of SIT/UAT until the live path is functional and verified.

---

# PR-020

## Question from Vietnam Team
Dispatch idempotency: if the user double-clicks or retries, is double-transfer prevented? rerun only allows transient RS_CD + "today" — safe enough?

## Answer
Additional guards exist that were not visible at the time of the original review — though a residual gap remains, and the analysis below is scope-limited. On the **internal-transfer** path, duplicate submission is guarded at the document-state level: an already-transferred master is rejected (WBE2064), an already-in-approval master is rejected (WBE2062), final-state approvals are rejected (WBE2095), and re-run is limited to transient result codes on the same day. There is no explicit per-request idempotency key, so protection depends on document state plus the concurrency control discussed in PR-021.

**Scope limitation:** this assessment covers the internal-transfer service only. The newer `domain/payment/dispatch/` engine — in particular the bank-result intake path (`BulkReceptionService`, `BulkExecutionResultService`, `ResultCheckService`/`ResultCheckJob`, and the callback controllers) — was **not** assessed for idempotency. Bank callbacks can be retried or duplicated by the VAN, so that path needs its own idempotency review before it leaves skeleton state.

## Current Status
OPEN

## Risk Level
P0

## Evidence
* File: domain/payment/internaltransfer/service/InternalTransferDispatchService.java ; InternalTransferService.java ; (not assessed: domain/payment/dispatch/callback/service/*, domain/payment/dispatch/service/ResultCheckService.java)
* Class: InternalTransferDispatchService ; InternalTransferService ; (unassessed: BulkReceptionService / BulkExecutionResultService / ResultCheckService)
* Method: dispatch ; rerun ; requestApproval ; actOnApproval
* Line(s): InternalTransferDispatchService.java:98-100 (WBE2064); InternalTransferService.java:643-658 (rerun today + RS_CD), 812-814 (WBE2062), 1109-1112 (WBE2095)

## Why It Matters
A true concurrent double-submit on the internal-transfer path is bounded by document state and the key-allocation race (PR-021), not by an idempotency key — so under concurrency the guarantee is incomplete. The callback/result path's idempotency is currently unknown.

## Recommended Action
Backend Lead to confirm state-based guards are sufficient on the internal-transfer path, or add an explicit idempotency key per dispatch; and separately assess idempotency on the `domain/payment/dispatch/` callback/result-check path. Resolve together with PR-021.

---

# PR-021

## Question from Vietnam Team
Concurrency: two users approving/dispatching one batch, or confirming one transaction at once — is there an optimistic lock (@Version)? I see many MAX+1 key allocations with "retry once" — enough under concurrent load?

## Answer
Confirmed and still valid. Independently re-verified on 2026-06-08: there is no JPA optimistic locking anywhere in `src/main/java` (a tree-wide search for `@Version` returns **0**; no pessimistic `@Lock` either). Money keys are allocated with MAX+1 and a single retry. One path is serialized safely by a PostgreSQL row lock (the scheduled-payment number uses ON CONFLICT … DO UPDATE), but the transfer sequence and approval sequence remain race-prone — a second concurrent caller is caught by one retry, a third can fail or collide.

**Scope limitation:** this assessment covers the key-allocation paths in the internal-transfer/dispatch executor. The `domain/payment/dispatch/` callback and result-check path (`BulkReceptionService`, `ResultCheckService`/`ResultCheckJob`, callback services) was **not** assessed for concurrency — concurrent bank callbacks updating the same dispatch row should be reviewed once that path leaves skeleton state.

## Current Status
OPEN

## Risk Level
P0

## Evidence
* File: domain/payment/dispatch/service/DispatchKeyGenerator.java ; domain/payment/internaltransfer/service/InternalTransferKeyGenerator.java ; InternalTransferService.java
* Class: DispatchKeyGenerator ; InternalTransferKeyGenerator ; InternalTransferService
* Method: nextTranSeq ; nextScdulPymnNo ; allocateApprSeqNo / requestApproval
* Line(s): DispatchKeyGenerator.java:39-53 (race-prone MAX+1); InternalTransferKeyGenerator.java:76-87 (safe ON CONFLICT lock); InternalTransferService.java:828-836 (one retry), 935-942 (MAX+1 across two tables); `@Version` count = 0 across `src/main/java` (re-verified 2026-06-08); callback/result path not assessed

## Why It Matters
Two concurrent requests can double-dispatch, double-confirm, or collide on a key — unacceptable for money movement. The callback/result path's concurrency behaviour is currently unknown.

## Recommended Action
Add version-based optimistic locking on payment master/summary entities and/or database sequences for money keys, and assess the dispatch callback/result-check path for concurrent updates. Depends on the topology decision (PR-044).

---

# PR-022

## Question from Vietnam Team
CutoffValidator blocks off-hours/weekends — where do the per-network (SWIFT/NAPAS) cutoff rules come from, are they configurable, do they account for VN holidays?

## Answer
Confirmed: the cutoff validator blocks weekends and applies per-network time windows, but the rules are hardcoded in Java and there is no Vietnam-holiday calendar or external configuration. It may wrongly allow or block on holidays.

## Current Status
PO DECISION REQUIRED

## Risk Level
P1

## Evidence
* File: domain/payment/internaltransfer/service/CutoffValidator.java
* Class: CutoffValidator
* Method: (validate)
* Line(s): 71 (weekend check), 87-99 (hardcoded time windows); no holiday calendar / config source

## Why It Matters
Hardcoded cutoff rules with no holiday calendar can permit or block dispatch incorrectly on VN public holidays.

## Recommended Action
PO to provide per-network cutoff rules and a VN holiday calendar; make them configurable.

---

# PR-023

## Question from Vietnam Team
Approval workflow (EXPT_RESOL_*): if an approver is removed/locked mid-flow, how does the chain handle it? Can it re-assign?

## Answer
Confirmed: there is no defined rule for an approver who is removed or locked mid-flow. The approval chain can stall with no reassignment path.

## Current Status
PO DECISION REQUIRED

## Risk Level
P1

## Evidence
* File: domain/payment (approval-resolution flow, EXPT_RESOL_*)
* Class: (approval service)
* Method: approval action
* Line(s): no disabled-approver handling present

## Why It Matters
An invalidated approver can deadlock the approval workflow.

## Recommended Action
PO to define the disabled-approver policy (skip, reassign, or escalate) and have it implemented.

---

# PR-024

## Question from Vietnam Team
Vendor/Expense/Payroll are 1:1 copies of Internal Transfer. Tech debt: merge into one generic service keyed by SCDUL_PYMN_TYPE_CD, or keep separate for per-module customization?

## Answer
Confirmed: Vendor, Expense, and Payroll payment services are near-duplicates differing mainly by payment-type code and audit tag. Whether to consolidate is a design trade-off (DRY vs per-module flexibility) for the Backend Lead/PO.

## Current Status
PO DECISION REQUIRED

## Risk Level
P2

## Evidence
* File: domain/payment/{vendorpayment,expensepayment,payrollpayment}/service/*
* Class: VendorPaymentService ; ExpensePaymentService ; PayrollPaymentService
* Method: (parallel implementations)
* Line(s): near-identical service bodies differing by SCDUL_PYMN_TYPE_CD + audit slug

## Why It Matters
Copy-paste means one bug must be fixed in four places; consolidation reduces per-module flexibility — a real trade-off.

## Recommended Action
Backend Lead/PO to decide: consolidate into a generic service keyed by payment type, or keep separate.

---

# PR-025

## Question from Vietnam Team
The "LOCAL mirror DEV column-for-column" + "max 30 rows/table" rule — but this is local R&D. When going to real UAT/PROD, how is real data migrated? Is there an ETL plan from JEX PROD?

## Answer
Confirmed: the current seed is lightweight local development only (limited rows, single tenant). There is no ETL/migration plan or tooling to migrate real production data from the live system to UAT/PROD.

## Current Status
PO DECISION REQUIRED

## Risk Level
P1

## Evidence
* File: src/main/resources/db/migration (seed migrations)
* Class: n/a (Flyway seed)
* Method: n/a
* Line(s): seeds limited to single tenant / capped rows

## Why It Matters
A development seed is not a cutover; without ETL tooling there is no way to bring real production data across.

## Recommended Action
PO/Backend to define a real data-migration plan and tooling before any cutover (aligned with PR-001).

---

# PR-026

## Question from Vietnam Team
Flyway is at ~V187 with many "renumbered, stale-header" migrations and back-and-forth reverts (V35→V36, V37→V38…). Is the migration history clean enough to rebuild from scratch on a new environment? Has flyway clean + migrate been tested on an empty DB?

## Answer
Confirmed concern: the migration history is long, with several corrective/renumbered/reverted steps. A clean rebuild from an empty database has not been confirmed in this review. The canonical schema-provisioning path for a fresh environment should be confirmed and tested.

## Current Status
OPEN

## Risk Level
P1

## Evidence
* File: src/main/resources/db/migration (V1 … V243)
* Class: n/a (Flyway migrations)
* Method: n/a
* Line(s): multiple corrective/reverted migrations (e.g. V35→V36, V37→V38)

## Why It Matters
A long, repeatedly-patched chain raises doubt that it replays cleanly; a fresh environment could fail to build the schema.

## Recommended Action
Confirm and test the canonical schema-provisioning path on an empty database; document the supported rebuild procedure.

---

# PR-027

## Question from Vietnam Team
ddl-auto: validate — entities map only a column subset. Are we sure no NOT NULL column is missing a DEFAULT on a Java INSERT (runtime risk)?

## Answer
Confirmed risk: the schema validates against entities that intentionally map only a subset of columns. Untested insert paths could hit a NOT-NULL column with no default at runtime.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: (entities under domain/**/entity ; schema migrations)
* Class: various entities (subset column mapping)
* Method: n/a
* Line(s): entity field subset vs full DB schema

## Why It Matters
A NOT-NULL column without a default, hit by a Java insert that does not set it, causes a runtime constraint violation.

## Recommended Action
Add insert-path tests against the production database engine to surface any missing-default constraint violations (see PR-040).

---

# PR-028

## Question from Vietnam Team
Legacy *_HIS (audit/history) tables use triggers — refactor "defers triggers". So those tables' history/audit trail is currently empty — any compliance requirement?

## Answer
Confirmed: legacy history/audit triggers are deferred, so trigger-driven history tables are not populated by the refactor's writes. Whether a table-level audit trail is legally required is a compliance question.

## Current Status
LEGAL CONFIRMATION REQUIRED

## Risk Level
P2

## Evidence
* File: src/main/resources/db/migration (triggers deferred)
* Class: n/a
* Method: n/a
* Line(s): history triggers documented as deferred in the schema strategy

## Why It Matters
If compliance requires a table-level audit trail, the deferred triggers mean it is currently missing.

## Recommended Action
Legal/Compliance to confirm whether table-level audit history is required; if so, port the relevant triggers.

---

# PR-029

## Question from Vietnam Team
`wb_get_next_val` (legacy PL/pgSQL) is still used for TRCO_KEY, APPR_LINE_NO, CHART_ACCT_SEQ_NO, USE_USAG_CD, RMRK_SEQ_NO… per-tenant MAX+1, not a sequence → race under load. When is it replaced by a Java generator/sequence?

## Answer
Confirmed: several keys are still allocated by the legacy per-tenant MAX+1 helper, guarded by a unique constraint plus a single retry. This is not atomic under burst concurrency and can still throw or hand out duplicate keys under load. Replacement timing depends on the coexistence decision (PR-001).

## Current Status
OPEN

## Risk Level
P1

## Evidence
* File: common/legacy/LegacyFunctionRepository.java
* Class: LegacyFunctionRepository
* Method: nextTrcoKey / nextApprLineNo / nextChartAcctSeqNo
* Line(s): MAX+1 branches via `wb_get_next_val` (migrations V113 / V91 / V95)

## Why It Matters
A non-atomic per-tenant MAX+1 can collide under concurrent load, throwing errors or duplicating keys.

## Recommended Action
Replace with a Java generator or database sequence under the same concurrency track as PR-021; sequencing depends on PR-001.

---

# PR-030

## Question from Vietnam Team
Some places use native query + LATERAL JOIN (smart-organizer eligible) — tested at real volume? Indexes sufficient?

## Answer
Confirmed: the Smart Organizer eligible-list uses a native query with lateral joins. It has not been benchmarked at real data volume, and index sufficiency is unverified.

## Current Status
OPEN

## Risk Level
P1

## Evidence
* File: domain/banking/smartorganizer/service/SmartOrganizerService.java
* Class: SmartOrganizerService
* Method: listEligible
* Line(s): 106-120 (native query construction)

## Why It Matters
An unbenchmarked native query may scan large tables and be slow on real data.

## Recommended Action
Benchmark the query at production-scale volume and review/add indexes.

---

# PR-031

## Question from Vietnam Team
Some filters run in-memory after fetch (transaction type/keyword, account by-bank). For accounts with many transactions, should they be pushed to the DB?

## Answer
Largely resolved for the account list path; one related path unverified. Source confirms the account list endpoint now performs **server-side** filtering and pagination — bank code and keyword are passed into the repository query (`AcctMstr.findFiltered`) and the per-currency totals are computed in the database (`aggregateByCcy`), not in memory. The account-by-bank/keyword case the review cited is therefore resolved for that page. Whether any other list page (or the Excel export provider) still filters in memory was **not verified in this review** — no in-memory export filter was confirmed against source here, so it is left as an explicitly unverified item rather than asserted.

## Current Status
RESOLVED (account list path) · OPEN/unverified (other list pages + export provider)

## Risk Level
P2

## Evidence
* File: domain/banking/service/AccountService.java
* Class: AccountService
* Method: list (findFiltered + aggregateByCcy)
* Line(s): 199-231 (server-side `acctRepo.findFiltered(...)` with bankCd + keywordPattern; `aggregateByCcy(...)` totals computed in DB)

## Why It Matters
Server-side filtering avoids loading large result sets into memory. The account list page no longer has this problem; the status of other pages/export is simply unknown until checked.

## Recommended Action
No action needed for the account list. Separately verify (do not assume) whether other list pages or the Excel export provider filter in memory, and push those to the database if confirmed. Non-blocking.

---

# PR-032

## Question from Vietnam Team
No SpringDoc/OpenAPI — what does the frontend rely on for the contract? Should it be added to generate types for wecms-web?

## Answer
Confirmed: there is no SpringDoc/OpenAPI dependency. The frontend-backend contract is maintained only by manual full-stack discipline.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: build.gradle
* Class: n/a
* Method: n/a
* Line(s): no springdoc/openapi dependency present

## Why It Matters
A contract enforced only by human discipline lets DTO/type drift go undetected until runtime.

## Recommended Action
Add SpringDoc and generate TypeScript types for the frontend (also addresses PR-055).

---

# PR-033

## Question from Vietnam Team
STUB reports: /reports/daily-cash (empty summary), /reports/daily-financial-hs (hardcoded), /transfers/own-accounts (no execution). Are these in the MVP? Are customers mistaking them for "real"?

## Answer
Confirmed stubs. The daily-cash per-currency summary returns an empty list (the account ledger itself is real); the daily-financial-HS report returns a fixed hardcoded dataset; and own-account transfer has a read-only accounts list with no execution endpoint. These screens can render numbers that look real but are empty or fixed.

## Current Status
OPEN

## Risk Level
P1 (conditional P0 — becomes P0 only if any of these screens is in the go-live MVP)

## Evidence
* File: domain/banking/service/ReportService.java ; domain/banking/controller/TransferController.java
* Class: ReportService ; TransferController
* Method: dailyCash ; dailyFinancialHs ; (own-accounts list)
* Line(s): ReportService.java:97 (empty summary), 440-456 (hardcoded HS rows); TransferController.java:24-25 (execution intentionally not implemented)

## Why It Matters
A customer could make a decision on data that is empty or fixed, believing it is real. The severity depends entirely on MVP membership: if these screens ship at go-live, this is a P0 correctness issue; if they are excluded, it is a P1 housekeeping/labelling item.

## Recommended Action
PO to confirm whether these screens are in the go-live MVP. If yes, treat as P0 and complete or remove them; if no, ensure the UI clearly marks stub screens as not-real until completed.

---

# PR-034

## Question from Vietnam Team
e-Invoice status filter is still client-side (IR-4, wb_get_invoice_status not ported). Correct business rule, or wrong filter?

## Answer
Confirmed: invoice status is returned raw and filtered on the frontend; the legacy status-resolution function is not ported (IR-4 deferred). Status-based paging and counts can therefore be inaccurate.

## Current Status
OPEN

## Risk Level
P1

## Evidence
* File: domain/einvoice/service/EInvoiceService.java
* Class: EInvoiceService
* Method: toRow
* Line(s): 327-328 (raw status returned), 51-53 (IR-4 deferred note)

## Why It Matters
With status filtered only on the frontend, server-side paging and counts based on status may be wrong.

## Recommended Action
Decide whether status filtering/counting must be server-side; if so, port the legacy status-resolution logic.

---

# PR-035

## Question from Vietnam Team
Operation-status inquire-dialog Save is a stub (no upsert endpoint). How does FE handle it?

## Answer
Partly resolved. A create/persist endpoint for operation status exists and saves to the database. Whether the inquire-dialog Save action on the frontend is wired to this endpoint should be confirmed; the backend persistence is not a stub.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: domain/operation/controller/OperationStatusController.java ; domain/operation/service/OperationStatusService.java
* Class: OperationStatusController ; OperationStatusService
* Method: create ; create (repo.save)
* Line(s): OperationStatusController.java:100-104 (POST create); OperationStatusService.java:405 (repo.save)

## Why It Matters
If the inquire-dialog Save is not wired to the persist endpoint, a user may believe data was saved when it was not.

## Recommended Action
Confirm the frontend inquire-dialog Save is wired to the persist endpoint.

---

# PR-036

## Question from Vietnam Team
e-Invoice resolveDocType: code actually maps EXPENSE → 'B', CLAUDE.md says 'E'. Which is the correct business rule? If wrong, Bill vs Expense queries the wrong DOC_TYPE → wrong invoice data.

## Answer
RESOLVED. The reviewer correctly identified a code/documentation contradiction (the code maps one way, a documentation note said another) and rightly asked which was authoritative — that was the correct thing to flag. Current source verification shows the runtime code maps EXPENSE/BILL → DOC_TYPE 'B', which matches legacy behaviour: the legacy e-Invoice Sales/Expense screen treats DOC_TYPE 'B' as the Bill/Expense table and validates that the type is only INVOICE ('I') or BILL ('B'). The legacy enum value 'E' belongs to a different document type ("Expense" in a bookkeeping sense) that this screen does not use. So the runtime code is correct; the stale documentation note was the source of confusion, and it (not the code) should be corrected. No data is queried against the wrong DOC_TYPE.

## Current Status
RESOLVED

## Risk Level
N/A (was P0)

## Evidence
* File: domain/einvoice/service/EInvoiceService.java ; WECMS DEV/.../einv_0001_001_r001_act.jsp ; WECMS DEV/.../enums/BusaDocType.java
* Class: EInvoiceService ; (legacy action) ; BusaDocType
* Method: resolveDocType ; (legacy validateInput/DOC_TYPE switch) ; (enum values)
* Line(s): EInvoiceService.java:205-221 (EXPENSE/BILL→'B'); einv_0001_001_r001_act.jsp:270-284 (DOC_TYPE 'B' = table2 Bill/Expense; only INVOICE+BILL accepted); BusaDocType.java:7-10 (INVOICE 'I', BILL 'B', EXPENSE 'E', PAYROLLBOOK 'S')

## Why It Matters
The Expense screen queries the correct DOC_TYPE. The reviewer's question was valid and the answer is reassuring: the runtime behaviour is right; only a stale documentation note needs updating.

## Recommended Action
Documentation-only: correct the stale "EXPENSE→'E'" note in the internal feature notes so it matches the verified runtime behaviour. No code change.

---

# PR-037

## Question from Vietnam Team
Smart Organizer doc still says "discovery scaffold" but the code is fully implemented — stale doc, needs sync.

## Answer
RESOLVED. Smart Organizer is fully implemented — eligible-list and confirm endpoints, full service logic, native queries, and master-data resolution. The "discovery scaffold" wording was stale documentation, not the real state.

## Current Status
RESOLVED

## Risk Level
P2

## Evidence
* File: domain/banking/smartorganizer/controller/SmartOrganizerController.java ; service/SmartOrganizerService.java
* Class: SmartOrganizerController ; SmartOrganizerService
* Method: listEligible ; confirm
* Line(s): SmartOrganizerController.java:41-63 (endpoints); SmartOrganizerService.java:106-120 (query + master-data lookups)

## Why It Matters
Stale docs cause readers to misjudge the feature's true state.

## Recommended Action
Documentation-only: update the Smart Organizer feature doc to "implemented".

---

# PR-038

## Question from Vietnam Team
/transfers/history is deprecated but still present — has FE moved to /internal-transfer/transfer-history? When is it removed?

## Answer
RESOLVED. The deprecated `/api/v1/banking/transfers/history` endpoint (and its DTOs/service) was deleted on 2026-06-07 after the frontend cutover. Only the current `/api/v1/banking/internal-transfer/transfer-history` endpoint remains.

## Current Status
RESOLVED

## Risk Level
P2

## Evidence
* File: domain/banking/transferhistory/controller/TransferHistoryController.java ; domain/banking/controller/TransferController.java
* Class: TransferHistoryController ; TransferController
* Method: (transfer-history) ; (class-level documentation of removal)
* Line(s): TransferHistoryController.java:28-29 (sole endpoint; old one deleted); TransferController.java:16-19 (removal note)

## Why It Matters
Two same-function endpoints invite calling the wrong (legacy) one; the duplicate is now gone.

## Recommended Action
None.

---

# PR-039

## Question from Vietnam Team
What is the current coverage? Do the real money flows (dispatch, approval, match) have integration tests, or only Mockito unit tests?

## Answer
Confirmed gap: integration-test coverage over the real money flows (dispatch, approval, matching) could not be confirmed; the automated tests are predominantly unit-level with mocking. Coverage of the money flows is effectively unknown.

## Current Status
OPEN

## Risk Level
P0

## Evidence
* File: src/test/java (test tree)
* Class: (Mockito-based unit tests)
* Method: n/a
* Line(s): no money-flow integration tests evidenced

## Why It Matters
Unknown coverage of real money flows is high risk for a money-movement system.

## Recommended Action
Add integration tests for dispatch, approval, and matching before exposing real money.

---

# PR-040

## Question from Vietnam Team
No Testcontainers → tests run on H2/mock, but prod is PostgreSQL + native query + @Formula + DB function. Do the tests catch DB-specific bugs?

## Answer
Confirmed: there is no Testcontainers dependency. Tests run with mocking/in-memory, while production uses PostgreSQL with native queries, computed columns, and DB functions, so a class of PostgreSQL-only bugs is invisible to the tests.

## Current Status
OPEN

## Risk Level
P1

## Evidence
* File: build.gradle
* Class: n/a
* Method: n/a
* Line(s): no testcontainers dependency present

## Why It Matters
Mocks cannot exercise database-specific behaviour, so PostgreSQL-only defects go uncaught.

## Recommended Action
Add Testcontainers (PostgreSQL) for native-query / computed-column / DB-function features.

---

# PR-041

## Question from Vietnam Team
Any FE↔BE E2E tests? Any contract tests (FE wire type ↔ DTO)?

## Answer
Confirmed: no frontend-backend end-to-end or contract tests were evidenced. Contract drift would only surface at runtime.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: src/test/java (test tree)
* Class: n/a
* Method: n/a
* Line(s): no E2E / contract tests evidenced

## Why It Matters
Without contract tests, wire-type vs DTO drift breaks only at runtime.

## Recommended Action
Add contract tests (pairs with PR-032/PR-055).

---

# PR-042

## Question from Vietnam Team
Tests for i18n (4 locales), for multi-tenant isolation (guaranteeing no biz_key leak)?

## Answer
Confirmed: no multi-language or multi-tenant isolation tests were evidenced. Cross-tenant leakage risk is not covered by tests.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: src/test/java (test tree)
* Class: n/a
* Method: n/a
* Line(s): no i18n / tenant-isolation tests evidenced

## Why It Matters
Missing isolation tests mean a cross-tenant (BIZ_KEY) data leak could go uncaught.

## Recommended Action
Add tenant-isolation tests guaranteeing no BIZ_KEY leak, plus i18n coverage for the supported locales.

---

# PR-043

## Question from Vietnam Team
CLAUDE.md says "local-only, no DEV/UAT/PROD deploy". So what is the plan to go to real production? Infra (k8s/VM), CI/CD, secret management?

## Answer
Confirmed: the project is local/development-only with no defined production environment, CI/CD pipeline, or secret-management approach. There is no hardened path to run for real customers.

## Current Status
DEVOPS CONFIRMATION REQUIRED

## Risk Level
P0

## Evidence
* File: (project scope / application profiles)
* Class: n/a
* Method: n/a
* Line(s): dev/prod profiles reserved/unused

## Why It Matters
A local R&D PoC with no environment beyond the laptop has no defined, hardened path to production.

## Recommended Action
PO/DevOps to define target infrastructure (k8s/VM), CI/CD, and secret management.

---

# PR-044

## Question from Vietnam Team
Single instance or cluster? (affects in-memory scraping session + MAX+1 keys).

## Answer
Confirmed: deployment topology (single instance vs cluster) is undecided. This decision drives the fixes for the in-memory scraping session (PR-015) and the concurrency/key-allocation issues (PR-021, PR-029).

## Current Status
DEVOPS CONFIRMATION REQUIRED

## Risk Level
P1

## Evidence
* File: (deployment configuration)
* Class: n/a
* Method: n/a
* Line(s): topology undecided

## Why It Matters
PR-015, PR-021, and PR-029 cannot choose a fix direction until topology is known.

## Recommended Action
PO/DevOps to decide single-instance vs cluster; this unblocks the concurrency/session fixes.

---

# PR-045

## Question from Vietnam Team
DB backup/restore, DR plan, RPO/RTO for a financial system?

## Answer
Confirmed: no backup/restore procedure, disaster-recovery plan, or RPO/RTO targets are defined.

## Current Status
DEVOPS CONFIRMATION REQUIRED

## Risk Level
P2

## Evidence
* File: (operations plan)
* Class: n/a
* Method: n/a
* Line(s): no DR plan present

## Why It Matters
Without DR targets, data loss has no guaranteed recovery path.

## Recommended Action
DevOps to define backup/restore, DR plan, and RPO/RTO for a financial system.

---

# PR-046

## Question from Vietnam Team
File storage (logo, attachment, Excel batch) writes to local filesystem (C:/WECMS PROJECT/...). What does production use (S3/NFS)? How is it shared across instances?

## Answer
Confirmed: file uploads/exports write to the local filesystem via a configurable storage root that defaults to a local path. With more than one instance, files written by one instance are not visible to others.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: domain/payment/internaltransfer/service/InternalTransferStorageProperties.java (and peer storage-properties classes)
* Class: InternalTransferStorageProperties ; OverseasPaymentStorageProperties
* Method: (storage-root binding)
* Line(s): `wecms.*.storage-root` default local path

## Why It Matters
Files written by one instance are invisible to others, so uploads/downloads break with more than one pod.

## Recommended Action
Move file storage to shared storage (object store / NFS) for multi-instance deployment.

---

# PR-047

## Question from Vietnam Team
XLSX export caps at 50,000 rows via POI SXSSF (streaming, good). But update operation-status, scraping sync — is there a request time limit/timeout (synchronous, seconds/credential)?

## Answer
Confirmed concern: long synchronous operations (operation-status update, scraping sync) have no confirmed request-timeout cap and may hit gateway/load-balancer timeouts or hold connections open. (The XLSX export's 50,000-row streaming cap is acknowledged as good.)

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: domain/operation (update) ; domain/banking/nxisas (sync)
* Class: (operation-status update service) ; (scraping sync service)
* Method: update ; sync
* Line(s): synchronous long-running calls; no explicit timeout cap verified

## Why It Matters
Long synchronous requests can hit gateway/LB timeouts and hang connections.

## Recommended Action
Add request timeouts; consider asynchronous processing for long operations.

---

# PR-048

## Question from Vietnam Team
Expected number of tenants? Users/tenant? Transactions/day? → to size indexes + connection pool.

## Answer
No load figures (tenant count, users per tenant, transactions per day) are defined. Infrastructure and indexes cannot be sized without them. This is a business/PO input.

## Current Status
PO DECISION REQUIRED

## Risk Level
P2

## Evidence
* File: n/a
* Class: n/a
* Method: n/a
* Line(s): no load figures present

## Why It Matters
Without load numbers, infrastructure and index sizing cannot be done.

## Recommended Action
PO to provide expected tenant/user/transaction volumes.

---

# PR-049

## Question from Vietnam Team
There's trace_id (good) but is there centralized logging/APM (ELK, Grafana)? When a user reports an error with a trace_id, where do ops grep?

## Answer
Confirmed: a request trace identifier is generated and returned in the error envelope and response header, but there is no centralized logging/APM stack to search it in production.

## Current Status
DEVOPS CONFIRMATION REQUIRED

## Risk Level
P1

## Evidence
* File: common/exception/TraceIdFilter.java ; application.yml (MDC pattern)
* Class: TraceIdFilter
* Method: (trace id generation + MDC)
* Line(s): trace id present and logged; no centralized log/APM stack

## Why It Matters
Without central logs, a reported trace_id is hard to trace in production.

## Recommended Action
DevOps to stand up centralized logging/APM (e.g. ELK/Grafana).

---

# PR-050

## Question from Vietnam Team
Is there alerting when dispatch fails, when Coocon is down, when FX can't be fetched?

## Answer
Confirmed: there is no alerting for dispatch failures, gateway outages, or FX fetch failures. Ops would learn of problems from users, not monitors.

## Current Status
DEVOPS CONFIRMATION REQUIRED

## Risk Level
P2

## Evidence
* File: (operations stack)
* Class: n/a
* Method: n/a
* Line(s): no alerting present

## Why It Matters
Without alerts, operations learn of failures from angry users rather than monitoring.

## Recommended Action
DevOps to add operational alerting on dispatch failure, gateway down, and FX fetch failure.

---

# PR-051

## Question from Vietnam Team
Audit log (AUDIT_LOGS_INFM) "never throws, swallows to WARN" — so if audit fails silently, is it monitored? For financial compliance, losing audit is a legal risk.

## Answer
Confirmed: the audit-log writer runs in its own transaction (REQUIRES_NEW), catches any runtime exception, logs it at WARN, and never re-throws — a deliberate trade-off so a failed audit does not roll back the business write. The consequence is that a missed audit row is silent unless WARN-level logs are monitored.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: common/auditlog/service/AuditLogService.java
* Class: AuditLogService
* Method: append
* Line(s): 119 ("never throws"), 125 (REQUIRES_NEW), 137-143 (catch → WARN → swallow)

## Why It Matters
A silently lost audit row is a compliance gap for a financial system if it is not monitored.

## Recommended Action
Add monitoring/alerting on audit-append WARN logs so silent audit loss is detected (depends on PR-049/PR-050).

---

# PR-052

## Question from Vietnam Team
Storing bank credentials + scraping on behalf of the user → is there customer legal consent and compliance with VN/State Bank regulations?

## Answer
Confirmed exposure: bank credentials are stored encrypted (AES-GCM) and the system scrapes on the user's behalf — a regulated activity in Vietnam. Customer consent and State Bank / regulatory compliance sign-off are legal/business matters, not code, and must be confirmed.

## Current Status
LEGAL CONFIRMATION REQUIRED

## Risk Level
P1

## Evidence
* File: domain/setting/logincredentials ; (bank certificate crypto service)
* Class: LoginCredentialsService ; BankCertCryptoService
* Method: (store / encrypt)
* Line(s): credentials stored encrypted; scraping acts as the user

## Why It Matters
Storing credentials and scraping as the user is regulated in Vietnam; without consent and compliance sign-off this is a legal exposure.

## Recommended Action
Legal/PO to obtain customer consent and State Bank / regulatory compliance sign-off before production.

---

# PR-053

## Question from Vietnam Team
PII (name, phone, tax ID, address) — any data-retention / right-to-erasure requirement? Soft-delete currently keeps data forever.

## Answer
Confirmed: soft-delete (DEL_YN) retains personal data indefinitely; no retention or right-to-erasure rule is defined. Whether this violates PII regulations is a legal question.

## Current Status
LEGAL CONFIRMATION REQUIRED

## Risk Level
P2

## Evidence
* File: (entities using soft-delete DEL_YN across domains)
* Class: various entities
* Method: soft-delete
* Line(s): rows retained after delete

## Why It Matters
Keeping PII forever may violate right-to-erasure / data-retention requirements.

## Recommended Action
Legal/PO to define PII retention and right-to-erasure handling.

---

# PR-054

## Question from Vietnam Team
Does e-Invoice/GDT have a retention constraint under VN tax law (e.g. 10 years)?

## Answer
No e-Invoice/GDT retention period is defined. Vietnamese tax law may impose a minimum retention; this must be confirmed legally.

## Current Status
LEGAL CONFIRMATION REQUIRED

## Risk Level
P2

## Evidence
* File: n/a (no retention policy defined)
* Class: n/a
* Method: n/a
* Line(s): n/a

## Why It Matters
An unclear minimum retention under tax law is a compliance risk.

## Recommended Action
Legal to define the minimum e-Invoice/GDT retention period under VN tax law.

---

# PR-055

## Question from Vietnam Team
The "full-stack ownership — DTO change ⇒ FE wire type changes in the same MR" rule. Is there an automatic drift check, or is it manual? (hint: generate TS types from OpenAPI).

## Answer
Confirmed: contract consistency is enforced only by the manual full-stack-ownership rule; there is no automatic drift detection.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: (project rule, no tooling)
* Class: n/a
* Method: n/a
* Line(s): no OpenAPI/type-generation present (see PR-032)

## Why It Matters
Manual-only enforcement means contract drift is not detected automatically.

## Recommended Action
Generate frontend types from an API spec (OpenAPI) to detect drift automatically (pairs with PR-032).

---

# PR-056

## Question from Vietnam Team
FE static-UI uses mockData; how do we know which features are truly wired vs still mocked? (CLAUDE.md's "frontend not verified" table is quite large).

## Answer
Confirmed: many features are backend-implemented but frontend-unverified, and the wired-vs-mock status is not tracked in one place. True release status is therefore hard to know.

## Current Status
OPEN

## Risk Level
P2

## Evidence
* File: (feature-status records)
* Class: n/a
* Method: n/a
* Line(s): multiple "frontend not verified" entries

## Why It Matters
Without a clear wired-vs-mock matrix, true feature status for release planning is uncertain.

## Recommended Action
Produce a wired-vs-mock matrix per feature for release planning (relates to PR-002).

---

# PR-057

## Question from Vietnam Team
Who is the PO/business owner to close the OQs still hanging in the docs (chart-of-accounts OQ-1/OQ-3, contacts OQ, e-invoice IR-4…)?

## Answer
Several open questions have no closing owner. This is a process/ownership matter for the PO.

## Current Status
PO DECISION REQUIRED

## Risk Level
P2

## Evidence
* File: (open-question records in feature docs)
* Class: n/a
* Method: n/a
* Line(s): OQs open more than one sprint

## Why It Matters
Open questions with no owner block implementation and linger across sprints.

## Recommended Action
PO to assign an owner to close each outstanding open question.

---

# PR-058

## Question from Vietnam Team
Code/security review cadence? Is there anyone who knows the original JEX business logic to verify parity?

## Answer
No designated parity verifier (someone who knows the legacy JEX business logic) is identified, and review cadence is not defined. Business divergence from the legacy system could go uncaught.

## Current Status
PO DECISION REQUIRED

## Risk Level
P2

## Evidence
* File: n/a (process/ownership)
* Class: n/a
* Method: n/a
* Line(s): no parity verifier identified

## Why It Matters
Without a parity verifier, divergence from the legacy system's business logic can go undetected.

## Recommended Action
PO to assign a JEX-parity verifier and define a code/security review cadence.

---

## Final Recommendation

**Ready for continued SIT.** The backend is appropriate for ongoing System Integration Testing against the development database. Menu authorization is now enforced on the business endpoints (PR-007, partially resolved — residual un-annotated controllers remain), and the previously suspected e-Invoice document-type defect is confirmed correct (PR-036). No live bank integration must be executed during SIT.

**Not ready for Production.** The in-refactor dispatch engine exists but its live legs are skeleton/mock-wired/unprovisioned and no settlement reconciliation exists (PR-019); concurrency and idempotency controls are incomplete and the dispatch callback/result path is not yet assessed (PR-021, PR-020, PR-029); token revocation (PR-006) and login auto-lock (PR-008) are not implemented; money-flow integration tests are absent (PR-039); and there is no defined production environment or operational tooling (PR-043, PR-044, PR-045, PR-049, PR-050).

**Conditions required before Production:**

1. **Real-money path** — implement and reconcile the bank-send leg (PR-019, PR-013, PR-014).
2. **Concurrency & idempotency** — add optimistic locking / safe key generation and confirm the idempotency model (PR-021, PR-020, PR-029).
3. **Authentication hardening** — server-side token revocation (PR-006) and login auto-lock / brute-force protection (PR-008).
4. **Testing** — integration tests over the money flows and database-specific behaviour (PR-039, PR-040), plus contract and tenant-isolation tests (PR-041, PR-042).
5. **Deployment readiness** — defined environment, topology, CI/CD, secret management, centralized logging, alerting, backups/DR (PR-043, PR-044, PR-012, PR-049, PR-050, PR-045).
6. **Legal/regulatory sign-off** — consent and compliance for credential storage and scraping (PR-052), plus data-retention rules (PR-028, PR-053, PR-054).
7. **Scope confirmation** — Definition of Done and MVP scope (PR-002), and confirmation that all stubbed screens (PR-033) are excluded from the release.

Once items 1–7 are addressed or formally accepted, a Production-readiness re-review should be conducted.

**Resolved since the original review (no longer blockers):** PR-036 (e-Invoice mapping confirmed correct), PR-037 (Smart Organizer implemented), PR-038 (deprecated endpoint deleted). **Substantially advanced (partially resolved):** PR-007 (menu authorization enforced on business endpoints; un-annotated controllers + callback-endpoint auth model remain open).

---

*End of document.*

# Authorization Documentation Drift — Code vs Docs

> Phase-3 truth audit (2026-06-23). **Implementation code is the sole source of truth.** Scope: `@PreAuthorize`,
> `@menuAuthz.canAccess(...)`, role gates, ownership rules, admin-only operations, export/download permissions —
> across auth · banking · payment · settings · common · addon-service, both `wecms-api` and `wecms-web`.
> Findings tagged **[CONFIRMED]** (verified against code) or **[SUSPECTED]** (not fully verified).
> Companion reports: [database-doc-drift.md](database-doc-drift.md) · [api-contract-drift.md](api-contract-drift.md) · [documentation-truth-audit.md](documentation-truth-audit.md).

## Headline

**Feature-level authorization docs MATCH the code in all six modules.** Every controller's class-level / method-level
gate verified equals the slug documented in that feature's `functional-specification.md` / `interface-specification.md`.
The authorization drift that exists is **not** in the feature docs — it is in **stale source-code Javadoc comments**
that claim authz is "deferred / advisory / not wired", contradicting the live annotation directly above/below them,
plus a small number of **intentionally-unauthenticated endpoints** that no leaf doc records as allow-list exceptions.

## A. Confirmed authorization MATCH (no doc drift)

| Module | Gate verified in code | Doc that matches |
|---|---|---|
| auth | `@EnableMethodSecurity` (`config/SecurityConfig.java:24`); `MenuAuthorizationGuard` 4-step rule; `ROLE_*` from `JwtAuthenticationFilter:56-63`; `UserController` allow-listed (no annotation) | `features/auth/authorization/functional-specification.md` |
| banking | Reports gate `banking-reports-daily-cash` / `-cash-flow` / `-daily-financial` (F/M) and `-daily-financial-hs` (H) (`ReportController.java:42-93`); `AccountController` tx sub-resources `canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')`; `TransferHistoryController:54` `banking-internal-transfer-history` | banking reports + transaction-history interface-specs |
| payment | All 8 business controllers class-level `@menuAuthz`; per-tab overrides (`payment-*-scheduled` / `-result`); payroll file-download dual-gate (`PayrollPaymentController.java:349`) | payment interface-specs |
| settings | 12 leaf controllers each match their `settings-core-*` / `settings-firm-banking-*` / `settings-erp-transaction-history` gate; user-management split read (`@userMgmtAccess.canViewUsers()`) vs write (`hasRole('ADMIN')` + `requireAdmin()`) | settings interface-specs |
| common | Global surfaces (View Setting, Navigation, Notification, Excel-export) intentionally un-gated allow-list; tenant from `TenantContext`, never client | `features/common/*/` + `common/README.md` |
| addon-service | e-Invoice `@menuAuthz.canAccessEInvoice(#p0)` (SALES→`addon-einvoices-sales`, EXPENSE→`addon-einvoices-expenses`, fail-closed); export/scrape `canAccessAny(...)`; operation-status `addon-operation-status-inquiry` | `addon-service/authorization-and-product-type.md` + web `menu-route-authorization.md` |

## B. Confirmed drift — stale source-code Javadoc contradicting the live annotation

These are **code-comment defects** (the feature docs are correct). They mislead a reader of the code into thinking
authz is absent when it is live. Fixing belongs in the code tree, but each is a documentation-truth liability.

| # | Location | Stale claim | Reality |
|---|---|---|---|
| AZ-1 | `common/security/JwtAuthenticationFilter.java:53-55` | authority is "advisory (no `@PreAuthorize` / `@EnableMethodSecurity` yet)" | `SecurityConfig.java:24` has `@EnableMethodSecurity`; F-3 method security live | **[CONFIRMED]** |
| AZ-2 | `domain/payment/cardtransactions/.../CardTransactionsController.java:90-96` | "`@PreAuthorize` role gating is **deferred** … same precedent as ChartOfAccounts" | class-level `@PreAuthorize(@menuAuthz...)` present at `:108` | **[CONFIRMED]** |
| AZ-3 | `domain/setting/purposesettings/.../PurposeSettingsController.java:60-63` | "carries no `@PreAuthorize`" | live `@menuAuthz.canAccess('settings-core-purpose')` at `:69` | **[CONFIRMED]** |
| AZ-4 | `domain/setting/vasaccount/.../VasAccountController.java:47-52` | "no `@PreAuthorize` yet" | live annotation at `:72` | **[CONFIRMED]** |
| AZ-5 | `config/SecurityConfig.java:35-38` | encoder "always writes BCrypt … transparent upgrade via `upgradeEncoding()`" | `WecmsPasswordEncoder` writes legacy SHA-256; `upgradeEncoding()` returns `false` (coexistence) | **[CONFIRMED]** |

## C. Confirmed drift — unauthenticated endpoints with no allow-list doc

Real `/api/v1/**` surfaces with **no** `@PreAuthorize` and **no** leaf-doc entry recording them as deliberate exceptions:

| # | Endpoint(s) | Code | Status |
|---|---|---|---|
| AZ-6 | `POST /api/v1/payment/dispatch/callbacks/*` (EFS→WeCMS bulk exec/reception callbacks) | `BulkExecutionResultController.java:23-34`, `BulkReceptionResultController` — no auth; `TODO(B7): IP whitelist pending` at `:20-21`; `CallbackIpWhitelistFilter` exists in package but **not wired** | **[CONFIRMED]** real unauth POST; R&D-acknowledged but undocumented as allow-list exception |
| AZ-7 | `BlockedCharsRuleController.java:40-43` (reference-data lookup) | no authz annotation; acceptable as read-only reference data, but not recorded as an allow-list exception in any doc | **[CONFIRMED]** low risk |

## D. Confirmed drift — root CLAUDE.md authorization narration (not feature docs)

| # | Location | Issue |
|---|---|---|
| AZ-8 | root `wecms-api/CLAUDE.md` Auth-Rules ("JWT filter → WBE1009") | live entry-point + `AuthService.java:101` use `WBE1401` for the unauthenticated path; `jwt-authentication/functional-specification.md:145-148` already documents the correct `WBE1401` and flags CLAUDE.md as stale. **[CONFIRMED]** |
| AZ-9 | `wecms-web/CLAUDE.md` §"Route protection" | describes a client-side `tokenStorage.get()` guard + `?preview=1` escape in `app/(dashboard)/layout.tsx`; actual protection is the `wecms-web/proxy.ts` edge middleware (`layout.tsx:23-27` only rehydrates via `fetchMe()`; no `?preview=1`). The web `login-auth.md:39-40` already states the correct `proxy.ts` model. **[CONFIRMED]** |

## E. Suspected / low-confidence

| # | Item | Why suspected |
|---|---|---|
| AZ-S1 | `TransferController.java:34-35` exposes a second `GET /api/v1/banking/transfers/own-accounts` (gated `banking-internal-transfer-own`) alongside `OwnAccountTransferController` | duplicate read surface; not a security hole (both gated) but a redundant path not noted in docs — **[SUSPECTED redundancy]** |

## Verdict

- **Authorization feature docs: accurate (0 confirmed feature-doc drift).**
- **Authorization-truth liabilities: 5 stale code Javadocs (AZ-1..5), 2 undocumented unauth endpoints (AZ-6/7), 2 stale CLAUDE.md narrations (AZ-8/9), 1 suspected redundancy.**
- Highest risk: **AZ-6** (unauthenticated dispatch callbacks with an unwired IP filter) — a real security item for any non-local deploy, and invisible in the docs.

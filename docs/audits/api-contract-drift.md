# API Contract Documentation Drift — Code vs Docs

> Phase-3 truth audit (2026-06-23). **Implementation code is the sole source of truth.** Scope: each feature's
> `interface-specification.md` (backend) and `api-consumption.md` (frontend) vs actual controllers, `@RequestMapping`
> paths, DTO request/response fields, query params, error codes, and examples — across all six modules.
> Flags: undocumented endpoints · removed endpoints still documented · missing query params · missing request fields ·
> missing response fields · incorrect examples. Tags: **[CONFIRMED]** / **[SUSPECTED]**.
> Companion: [authorization-doc-drift.md](authorization-doc-drift.md) · [database-doc-drift.md](database-doc-drift.md) · [documentation-truth-audit.md](documentation-truth-audit.md).

## Headline

**Feature-level API contracts are strongly accurate.** Across auth, banking, payment, settings, common, and
addon-service, the `interface-specification.md` endpoint sets, paths, methods, DTO fields, sort whitelists, and the
lowercase-snake_case wire convention matched the controllers and the `lib/api/*.ts` frontend clients endpoint-for-endpoint
and param-for-param. No removed/retired endpoint was found still documented as live; the notification-module removal and
the deprecated `/banking/transfers/history` deletion are both correctly reflected. The drift that exists is **minor**:
a handful of stale error-code/endpoint-count citations (mostly in `wecms-api/CLAUDE.md` and code Javadoc, which the
interface-specs already supersede) and a couple of real endpoints that exist in code but have **no leaf doc**.

## A. Confirmed contract MATCH (representative, no drift)

| Module | Verified | Source |
|---|---|---|
| auth | `POST /auth/login` (dual authenticated+chooser shape, sole `@JsonProperty("access_token")`), `GET /auth/me`, `POST /auth/logout`, `POST /auth/change-password`, `GET /api/v1/users` — all paths/DTOs match | `LoginResponse.java`, `AuthService.java:169-357`, `UserController.java` |
| banking | `lib/api/banking.ts` fetchers match `ReportController`/`DailyCashReportController`/`CashFlowReportController`/`AccountController` param-for-param (`year/baseCurrency/usdRate/krwRate/bankFilters`, `asOf/asOfDate/currency/currencyScope`, `activeAccountsOnly/includeInternalTransfers`); cash-flow `quarter=null` parity documented | banking interface-specs |
| payment | corporate-card `interface-specification.md` matches `CardTransactionsController` endpoint-for-endpoint (~30 mappings incl. `/sync-session`, `/period-limits`, `/request-approval` multipart, `/links/*`, `/memos/*`, `/export`); approval-box DOC_NO/APPR_SEQ_NO funnel claims accurate | payment interface-specs |
| settings | All 12 base paths verified; `purpose` correctly `/api/v1/settings/purpose-settings`; `login-credentials` correctly `/api/v1/login-credentials` (NOT under `/settings/`); transaction-history naming triad disclosed | settings interface-specs |
| common | View Setting `GET/PUT/DELETE /screen-options/{webSvcId}/{dymAreaId}` + `/areas`; `GET /navigation`; notification 4 endpoints; `GET /api/v1/common/countries`; per-resource `POST .../export` — all present with documented shapes | common interface-specs |
| addon-service | `lib/api/addon.ts` mirrors backend 1:1 — `fetchEInvoices` params (incl. `status` flow-through/IR-4), `scrape*`, `fetchEInvoiceHistory`, operation-status list/inquire/create/update | addon interface-specs |

## B. Confirmed contract drift

| # | Location | Issue |
|---|---|---|
| AC-1 | `features/settings/contacts/interface-specification.md:175` (+ `lib/api/contacts.ts:34` Javadoc) | cites error code `WBE2040`; actual code throws `WBE_CONTACTS_NOT_FOUND`. (`vas-account` correctly uses `WBE2040`.) **[CONFIRMED]** incorrect-example/error-code |
| AC-2 | `wecms-web/docs/features/auth/login/api-consumption.md:101-102` | lists `all_menus` / `allowed_menu_cds` rows in the login `toStoredUser` mapping table; `LoginResponse.java` carries no menu fields (mapper reads them as `Partial<MeResponseRec>` and defaults). Self-qualified in the Notes column but the table rows are misleading. **[CONFIRMED minor]** |
| AC-3 | `wecms-web/docs/features/banking/reports/daily-financial/{README,component-index,api-consumption}.md` | claim HS report has a "mock fallback"; `DailyFinancialHsClient.tsx:20,31-42` has **no** mock fallback (renders `data?.rows ?? []`). (CMC does keep a fallback; HS does not.) **[CONFIRMED minor]** |

## C. Confirmed — endpoint-count / "stub" claims stale in CLAUDE.md & code Javadoc (interface-specs are correct)

These are not feature-doc contract errors — the `interface-specification.md` is right — but they are contract-truth
liabilities in the surrounding narration:

| # | Location | Stale claim | Reality |
|---|---|---|---|
| AC-4 | `ChartOfAccountsController.java:31` Javadoc ("six"), `ChartAcctInfm.java:19` ("5"), `wecms-api/CLAUDE.md` row ("6") | chart-of-accounts has 6 endpoints | controller has **9** (3 bulk endpoints undocumented in those spots); interface-spec §1 correctly says 9 | **[CONFIRMED]** |
| AC-5 | `wecms-api/CLAUDE.md` contacts row ("six") | contacts has 6 endpoints | actual **17**; interface-spec correct | **[CONFIRMED]** |
| AC-6 | `wecms-web/CLAUDE.md` API table | `/banking/reports/cash-flow` "stub … falls back to mock"; `ApiCashFlowReport` an UPPER holdover | live, no fallback, lowercase; feature docs already correct | **[CONFIRMED]** |
| AC-7 | `wecms-api/CLAUDE.md` / memory | lists `DocNoGenerator` (EXPT_RESOL_MSTR.doc_no) as a Java-side `wb_get_next_val` exception | code funnels DOC_NO via `wb_get_next_val('expt_resol_mstr','doc_no')` (`ApprovalBoxFanoutService.java:160`, `LegacyFunctionRepository.java:430-431`); approval-box docs correct | **[CONFIRMED]** |

## D. Confirmed — endpoints in code with NO leaf documentation (undocumented surface)

| # | Endpoint | Code | Note |
|---|---|---|---|
| AC-8 | `POST /api/v1/payment/dispatch/callbacks/*` | `BulkExecutionResultController`, `BulkReceptionResultController` | EFS callbacks; no leaf doc; also an authz gap (see authorization-doc-drift AZ-6) | **[CONFIRMED]** |
| AC-9 | `BlockedCharsRuleController` (`/api/v1/payment/.../blocked-chars-rule`) | reference-data lookup | intentionally has no leaf folder; noted in `payment/README.md:107` but no interface-spec | **[CONFIRMED low]** |

## E. Stale-but-self-disclosed (NOT counted as drift)

- addon-service `einv_invoicestatus` ships the raw stored value (no `wb_get_invoice_status` port); IR-4 client-side
  status filter is **honestly documented as a deferred gap** in `sales-e-invoices/refactor-gap-analysis.md` and
  `lib/api/addon.ts:64-77` — not claimed as done. **[CONFIRMED accurate]**
- banking HS wire type "still reflects the stub-era shape, bridged by `projectHsRows`" — acknowledged in
  `daily-financial/api-consumption.md:17`, not silent. **[CONFIRMED accurate]**

## Verdict

- **Feature-doc API contracts: accurate** (3 minor confirmed drift: AC-1 error code, AC-2/AC-3 frontend doc minutiae).
- **Surrounding narration (CLAUDE.md + code Javadoc) has 4 stale endpoint-count/stub claims (AC-4..7).**
- **2 real endpoints undocumented (AC-8/9), AC-8 also a security item.**
- No removed endpoint is still documented as live; no missing request/response field found in any feature interface-spec.
- Highest priority: **AC-1** (wrong error code in a shipped interface-spec) and **AC-8** (undocumented unauth callback surface).

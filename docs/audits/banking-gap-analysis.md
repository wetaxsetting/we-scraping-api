# Banking Modules — Gap Analysis

> Consolidated gaps from the 2026-06-23 Banking documentation audit. Each gap carries the project vocabulary — **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING** — plus a gap *kind* (code-exists-doc-missing / doc-exists-stale / doc-exists-feature-removed / backend-followup / frontend-followup / test-missing / PO-decision-required / uncertain-needs-confirmation) and an owner.
> Companion files: [banking-docs-audit.md](banking-docs-audit.md) · [banking-source-index.md](banking-source-index.md).
> "Class" describes the doc-vs-code state being recorded; where a doc was corrected in this pass, the post-fix state is **MATCH** and the row is retained as the audit trail.

## Consolidated gap table

| ID | Module | Gap | Class | Kind | Owner |
|---|---|---|---|---|---|
| AI-1 | Account Inquiry (VND) | `LAST_UPDATE` sort doc'd as mapping to `acctBalLstInqDtm` (HR-1); code `AccountSortField.LAST_UPDATE` maps to **`acctHisLstInqDt`** (FCY-A02 logic applied to both variants). Stale in `functional-specification.md`, `database-mapping.md`, `test-scenarios.md`. **Corrected this pass.** | PARTIAL → MATCH | doc-exists-stale | wecms-api-docs |
| AI-2 | Account Inquiry (VND) | `resolveLastUpdate()` account-type branching (fixed-deposit-only → `ACCT_BAL_LST_INQ_DTM`, else `ACCT_HIS_LST_INQ_DT`) documented for FCY (FCY-A02) but not VND. **Cross-noted this pass.** | PARTIAL → MATCH | doc-exists-stale | wecms-api-docs |
| AI-3 | Account Inquiry (both) | `asOfDate` query param accepted by `AccountController` + `AccountService.list` but absent from the VND `interface-specification.md` endpoint contract. | PARTIAL | code-exists-doc-missing | dev / doc |
| AI-4 | Account Inquiry | `PrdtType H` maturity exclusion applies to aggregates only (not the row list); undocumented. | MISSING | code-exists-doc-missing | dev |
| TH-1 | Transaction History | Export endpoint authz slug set `('banking-vnd-account-inquiry','banking-fcy-update-inquire')` differs from the transaction endpoints' `('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')`. Docs imply export shares the transaction authz. Confirm intentional. | APPROVED DEVIATION | PO-decision-required / backend-followup | PO / dev |
| TH-2 | Transaction History (VND + FCY) | `interface-specification.md` claims `TransactionRow`/`TransactionListResponse`/`TransactionDetailResponse` carry UPPER_SNAKE `@JsonProperty` holdovers — migrated to lowercase snake_case 2026-06-07. **Corrected this pass.** | DEAD LEGACY → MATCH | doc-exists-stale | wecms-api-docs |
| TH-3 | Transaction History | Header per-column sort intentionally not wired (`sortable={false}`) — matches design, not a defect. | MATCH | (none) | — |
| TH-4 | Transaction History | 5 export columns (`TRSC_ID`, `FILE_NM`, `MTCH_DT`, `PURPOSE`, `CONTACT_NM`) emit empty strings pending backend DTO scope. Documented. | PARTIAL | backend-followup | dev |
| SO-1 | Smart Organizer | Feature is implemented full-stack, but the "discovery scaffold" label survives in the preserved legacy specs (`smart-organizer/legacy-spec-vnd.md` + `legacy-spec-fcy.md`, both carry a "primary home moved" banner) and the **wecms-api CLAUDE.md Feature-Status note** ("docs still label discovery scaffold — stale"). The module folder docs are accurate. The CLAUDE.md note is code-tree, not doc-tree — flagged for the maintainer. | DEAD LEGACY | doc-exists-stale | dev (CLAUDE.md) |
| SO-2 | Smart Organizer | Rule drag-reorder: backend `POST /rules/reorder` implemented; frontend drag-save UI wiring deferred. | PARTIAL | frontend-followup | dev |
| SO-3 | Smart Organizer | Receive/Pay direction is user-selected on rules (not auto-derived from `BUSI_USAG_SET_INFM.USAG_GRP_CD`, unpopulated); legacy RCV-only re-keying deferred. Documented. | APPROVED DEVIATION | backend-followup | dev |
| SO-4 | Smart Organizer | No organizer-eligible FCY account in the dev seed → FCY path verified at SQL layer only (single query, no currency branch). | APPROVED DEVIATION | test-missing (data seed) | QA |
| OAT-1 | Own Account Transfer | Frontend WEB README + preserved spec cite route `/banking/own-account-transfer`; canonical route is **`/banking/transfer-own-accounts`** (old path is a redirect, 2026-06-06). **Corrected this pass.** | PARTIAL → MATCH | doc-exists-stale | wecms-web-docs |
| OAT-2 | Own Account Transfer | `interface-specification.md` §1 header says "10 endpoints" but the numbered table shows 9 — `/nxisas/logging` row missing. **Corrected this pass.** | PARTIAL → MATCH | doc-exists-stale | wecms-api-docs |
| OAT-3 | Own Account Transfer | Composer credential-collection (saved bank-cert lookup, in-app password capture) deferred (OQ-FE-1, Phase-B). Documented. | APPROVED DEVIATION | frontend-followup | dev |
| OAT-4 | Own Account Transfer | `wecms.oat.seed-decrypt.enabled` default OFF; per-bank rewrites (Woori `100→DDA` B5, Vietcombank DIGIBIZ B11, CAPTCHA bridge B14) deferred. Documented. | APPROVED DEVIATION | backend-followup | dev |
| OAT-5 | Own Account Transfer | No XLSX export for OAT history (page exposes no Excel icon — matches legacy). | MATCH | (none) | — |
| TXH-1 | Transfer History | `functional-specification.md` + `interface-specification.md` claim "no `@PreAuthorize` (JWT only, OQ-5 resolved)"; controller **has** `@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-history')")` (line 54). The controller's own javadoc (line 32) is also stale (code-tree). Docs **corrected this pass**; controller javadoc flagged for dev. | PARTIAL → MATCH | doc-exists-stale + code-comment | wecms-api-docs / dev |
| TXH-2 | Transfer History | `WBE_TRANSFER_HISTORY_INVALID_PERIOD` present in `messages.properties` but missing from `messages_en.properties` (resolves via fallback; violates "add to both"). | PARTIAL | backend-followup | dev |
| TXH-3 | Transfer History | `V196__transfer_history_helper_index.sql` internal header reads `V181` (stale comment; harmless). | PARTIAL | code-comment | dev |
| TXH-4 | Transfer History | No View Setting popover, no detail route, no export — all intentional legacy parity, documented. | MATCH | (none) | — |
| DC-1 | Daily Cash | README / interface-spec / database-mapping / refactor-parity-verification describe Java live-recompute (`DailyCashRecomputeRepository`) as the active source; code calls `rept_0900_001_rec1..4` **directly** since 2026-06-13 (recompute repo `@Deprecated(forRemoval=true)`). **Corrected this pass.** | PARTIAL → MATCH | doc-exists-stale | wecms-api-docs |
| DC-2 | Daily Cash | Backend `DailyCashReportExportProvider` + `POST .../export` exist but the frontend exports client-side via `lib/dailyCashExport.ts` (`xlsx-js-style`) — diverges from the shared backend-POI export contract (CLAUDE.md §13b). | PARTIAL | frontend-followup | dev |
| DC-3 | Daily Cash | `rec3`/`rec4` (cash sections) need `CASH_REPY_MSTR` data; `LST_COLL_DATE` always null (batch `rept_0100_001_r001` not ported). | PARTIAL | backend-followup (data) | dev / infra |
| DC-4 | Daily Cash | Frontend renders an unwired checkbox column; print via bespoke `lib/dailyCashExport.ts` not shared `lib/print.ts`. | DEAD LEGACY / APPROVED DEVIATION | frontend-followup | dev |
| DF-1 | Daily Financial (HS) | WEB `README.md` + `api-consumption.md` describe HS as a "backend stub / 6-row seed / frontend mock"; backend calls `fn_get_haengsung_daily_financial_report` **live since 2026-06-14**. **Corrected this pass.** | PARTIAL → MATCH | doc-exists-stale | wecms-web-docs |
| DF-2 | Daily Financial | The legacy single-file spec (now `reports/daily-financial/legacy-spec.md`) lists params `referenceDate`/`currency`; the package supersedes it with `asOf`/`currencyScope`. The legacy spec carries a "primary home moved" banner; the authoritative `interface-specification.md` uses the correct params. | PARTIAL → MATCH | doc-exists-stale | wecms-api-docs |
| DF-3 | Daily Financial (HS) | Frontend HS wire types in `lib/types/api.ts` still reflect the stub-era shape; `DailyFinancialHsClient.projectHsRows` bridges. Flip wire types when the backend DTO casing flips. | PARTIAL | frontend-followup | dev |
| DF-4 | Daily Financial (CMC) | `activeAccountsOnly=false` accepted but not honored server-side (always active-only). Documented P1. | DEAD LEGACY | backend-followup | dev |
| DF-5 | Daily Financial (CMC) | `includeInternalTransfers` toggles column display but doesn't exclude from totals; legacy 4-usage-code filter not implemented. Documented P1. | DEAD LEGACY | backend-followup | dev |
| DF-6 | Daily Financial (CMC) | Inline REC2_1 grid (`currentAccounts[].transactions[]`) always empty; the popup serves the drilldown. | APPROVED DEVIATION | backend-followup | dev |
| DF-7 | Daily Financial | No backend `DailyFinancialExportProvider` + `POST .../export`; frontend exports client-side (`xlsx-js-style`) — same divergence class as DC-2. | APPROVED DEVIATION | backend-followup | dev |
| DF-8 | Daily Financial | Frontend `?? mockData` silently masks API errors (failed fetch reads as a valid empty report). | APPROVED DEVIATION | frontend-followup | dev |
| CF-1 | Cash Flow | README / `business-flow.md` / `refactor-gap-analysis.md` state "real Java aggregation" and "`get_cashflow_report` not called"; code **calls `get_cashflow_report(...)` directly** (`CashFlowReportRepository:67`), decision 2026-06-13. The `service/cashflow/*` Java classes are retained but **off the active path**. **Corrected this pass.** | DEAD LEGACY → MATCH | doc-exists-stale | wecms-api-docs |
| CF-2 | Cash Flow | `service/cashflow/*` (PaymentDataAggregator, FxConverter, BalanceCalculator, ChartOfAccountsHierarchy, CashFlowRowAssembler) are unwired; consider `@Deprecated`/comment to prevent accidental reactivation. | DEAD LEGACY | backend-followup (code) | dev |
| CF-3 | Cash Flow | `test-scenarios.md` + `source-code-index.md` partly map to the now-bypassed Java classes; clarify which tests cover the DB-function contract. | PARTIAL | doc-exists-stale | wecms-api-docs |

---

## Cross-cutting findings

### Data-source pivots not reflected in docs (the dominant theme)
Three report modules pivoted from a Java reimplementation/recompute back to **calling the legacy PL/pgSQL functions directly** (decisions dated 2026-06-13/14), but several docs still describe the superseded Java path:
- **Cash Flow** → `get_cashflow_report` (CF-1).
- **Daily Cash** → `rept_0900_001_rec1..4` (DC-1).
- **Daily Financial CMC** → `rept_0015_001_r001_rec1`; **HS** → `fn_get_haengsung_daily_financial_report` (DF-1).

These are the highest-value corrections in this pass — a reader following the stale docs would look for live Java aggregation code that is no longer on the active path. The backend `*Service` javadocs are accurate; the feature docs lagged.

### Frontend XLSX export divergence (DC-2, DF-7)
Daily Cash and Daily Financial export client-side via `xlsx-js-style`, which diverges from the project's shared **backend-POI** export contract (CLAUDE.md §13b — backend is the single XLSX producer). Daily Cash already has a backend provider that is simply unwired on the frontend. Recommend converging both reports onto the shared `useExcelExport` → per-resource `POST .../export` path in their next stabilisation pass.

### Stale claims now corrected vs. flagged-for-dev
Doc-tree stale items (under `wecms-api/docs/` and `wecms-web/docs/`) were corrected in this pass. Two items live in the **code tree** and are out of documentation scope — flagged for a developer:
- TXH-1 — `TransferHistoryController` javadoc (line 32) claiming "JWT-only" contradicts its own `@PreAuthorize` on line 54.
- SO-1 — the wecms-api CLAUDE.md Feature-Status row labelling Smart Organizer a "discovery scaffold (stale, flag for correction)".

### Test coverage snapshot
Backend tests are present for every module (controller + service + sort/export/parity). Consistent **MISSING**: frontend `.test.*` coverage across all Banking screens (same pattern as Payment). Largest single coverage gap; tracked as a dev follow-up, not a doc gap.

---

## Remaining PO / developer decisions

1. **TH-1** — confirm the Transaction History **export** endpoint's authz slug set is intentionally different from the transaction endpoints (PO).
2. **DC-2 / DF-7** — decide whether Daily Cash + Daily Financial converge onto the shared backend-POI XLSX export, or keep the interim client-side path (dev + PO).
3. **DF-4 / DF-5** — Daily Financial CMC `activeAccountsOnly` widening + `includeInternalTransfers` totals semantics (dev; P1, documented).
4. **DC-3** — seed `CASH_REPY_MSTR` (rec3/rec4 cash sections) + port `LST_COLL_DATE` batch source (dev / infra / DBA).
5. **CF-2** — mark/retire the unwired `service/cashflow/*` classes (dev, code-only).
6. **SO-1 / TXH-1** — fix the two stale **code-tree** notes (CLAUDE.md Feature-Status; `TransferHistoryController` javadoc) — out of this doc pass's write boundary.
7. **Frontend test baseline** — establish Banking FE `.test.*` coverage (dev).

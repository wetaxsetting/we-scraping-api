# Banking Documentation Audit — 2026-06-23

> Full audit of all Banking module documentation against the latest backend (`wecms-api/src`) and frontend (`wecms-web`) code. **Latest code = source of truth.** This pass reconciles stale docs, confirms the standardized structure, and produces the cross-tree index + gap matrix.
> Companion files: [banking-source-index.md](banking-source-index.md) · [banking-gap-analysis.md](banking-gap-analysis.md).
> Gap vocabulary used throughout: **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING**.

## Scope

- **API:** `wecms-api/docs/features/banking/**` (9-doc structure per module: README · business-flow · database-mapping · functional-specification · interface-specification · migration-guide · refactor-gap-analysis · source-code-index · test-scenarios).
- **WEB:** `wecms-web/docs/features/banking/**` (6-doc structure per module: README · screen-flow · ui-specification · component-index · api-consumption · test-scenarios).
- Methodology mirrors the Payment / Settings / Add-on doc audits.

## Structure decision (confirmed with Sonny, 2026-06-23)

- **VND / FCY twins stay separate**; **report modules stay nested under `reports/`**. No consolidation, no flattening.
- The docs tree is grouped by **menu group** — `current-savings-vnd/`, `current-savings-fcy/`, `internal-transfer-vnd/`, `reports/`, plus the standalone `smart-organizer/` — and each menu page is a **leaf folder** with the standard doc set. The "8 expected modules" in the request map to **10 leaf module folders**:

| # | Menu | Leaf folder | API files | WEB files |
|---|---|---|---|---|
| 1 | Account Inquiry (VND) | `current-savings-vnd/account-inquiry/` | 9 ✅ | 6 ✅ |
| 2 | Transaction History (VND) | `current-savings-vnd/transaction-history/` | 9 ✅ | 6 ✅ |
| 3 | Smart Organizer (VND+FCY) | `smart-organizer/` | 9 ✅ | 6 ✅ |
| 4 | Account Inquiry (FCY) | `current-savings-fcy/account-inquiry/` | 9 ✅ | 6 ✅ |
| 5 | Transaction History (FCY) | `current-savings-fcy/transaction-history/` | 9 ✅ | 6 ✅ |
| 6 | Own Account Transfer | `internal-transfer-vnd/own-account-transfer/` | 9 ✅ | 6 ✅ |
| 7 | Transfer History | `internal-transfer-vnd/transfer-history/` | 9 ✅ | 6 ✅ |
| 8 | Daily Cash | `reports/daily-cash/` | 9 ✅ (+2 extras) | 6 ✅ |
| 9 | Daily Financial (CMC+HS) | `reports/daily-financial/` | 9 ✅ (+3 extras) | 6 ✅ |
| 10 | Cash Flow | `reports/cash-flow/` | 9 ✅ | 6 ✅ |

> **No required files were missing.** Every leaf folder already carried the full 9-doc (API) / 6-doc (WEB) set. The old flat single-file specs were folded into each folder as **`legacy-spec.md`** (Smart Organizer keeps `legacy-spec-vnd.md` + `legacy-spec-fcy.md`), each carrying a "primary documentation home moved" banner pointing at the authoritative folder — this satisfies the root-doc → summary requirement (see "Root-level docs" below).

## Summary

| Module | Doc status (before → after) |
|---|---|
| Account Inquiry (VND) | Stale sort/last-update mapping → **Accurate** |
| Account Inquiry (FCY) | Accurate (FCY-A02 already documented) → **Accurate** |
| Transaction History (VND) | UPPER_SNAKE casing holdover claim (stale) → **Accurate** |
| Transaction History (FCY) | UPPER_SNAKE casing holdover claim (stale) → **Accurate** |
| Smart Organizer | Accurate in-folder; "scaffold" label only in legacy-spec + CLAUDE.md → **Accurate (flag CLAUDE.md)** |
| Own Account Transfer | FE route + endpoint-count drift → **Accurate** |
| Transfer History | "no @PreAuthorize" contradiction → **Accurate** |
| Daily Cash | "Java live recompute" data-source (stale) → **Accurate** |
| Daily Financial (CMC) | Accurate (live `rept_0015_001_r001_rec1`) → **Accurate** |
| Daily Financial (HS) | FE docs "backend stub / mock" (stale) → **Accurate** |
| Cash Flow | "real Java aggregation / fn not called" (stale) → **Accurate** |

**Dominant theme — data-source pivots.** Three report modules pivoted (2026-06-13/14) from a Java reimplementation/recompute back to **calling the legacy PL/pgSQL functions directly**; the backend `*Service` javadocs were updated but the feature docs lagged. These were the highest-value corrections in this pass.

---

## Account Inquiry — Accurate (was stale on Last-Update)

VND docs described the `LAST_UPDATE` sort key as mapping to `acctBalLstInqDtm` (the old HR-1 note). Verified in code: `AccountSortField.LAST_UPDATE("acctHisLstInqDt")` — the FCY-A02 disposition now applies to **both** variants. The displayed value is resolved account-type-aware by `AccountService.resolveLastUpdate(...)` (fixed-deposit-only → `ACCT_BAL_LST_INQ_DTM`, otherwise `ACCT_HIS_LST_INQ_DT`).

**Files updated (API, `current-savings-vnd/account-inquiry/`)**: `functional-specification.md`, `database-mapping.md`, `test-scenarios.md` (sort key → `acctHisLstInqDt` + account-type-aware resolution); `interface-specification.md` (corrected `acct_bal_upd_dtm` source row + **added the previously-undocumented `asOfDate` query param**); `refactor-gap-analysis.md`, `migration-guide.md` (same HR-1 → FCY-A02 correction). FCY docs already documented FCY-A02 — no change needed.

## Transaction History — Accurate (was stale on wire casing)

Both VND and FCY interface specs claimed `TransactionRow` / `TransactionListResponse` / `TransactionDetailResponse` carry UPPER_SNAKE `@JsonProperty` holdovers. These DTOs were migrated to lowercase snake_case via the global Jackson `SNAKE_CASE` strategy on 2026-06-07 — no `@JsonProperty` exceptions remain.

**Files updated**: VND `current-savings-vnd/transaction-history/{interface-specification,migration-guide}.md`; FCY `current-savings-fcy/transaction-history/{interface-specification,functional-specification,migration-guide}.md` (removed the UPPER_SNAKE "wire casing" exception note; DTO field tokens shown UPPER for legacy traceability only).

**Open**: TH-1 — the **export** endpoint's authz slug set (`banking-vnd-account-inquiry` / `banking-fcy-update-inquire`) differs from the transaction endpoints' (`banking-vnd-inquire-transactions` / `banking-fcy-inquire-transactions`). Confirm intentional (PO).

## Smart Organizer — Accurate (flag CLAUDE.md)

The module-folder docs correctly describe Smart Organizer as **implemented full-stack** (2 controllers + 2 services + V172–V174). The "discovery scaffold" language survives only in the preserved `legacy-spec-vnd.md` / `legacy-spec-fcy.md` (both banner-marked as superseded) and in the **wecms-api CLAUDE.md Feature-Status note** ("docs still label discovery scaffold — stale, flag for correction"). The CLAUDE.md note is a code-tree file outside this doc pass's write boundary — flagged for the maintainer (gap SO-1).

## Own Account Transfer — Accurate (was stale on route)

The WEB README cited route `/banking/own-account-transfer`; the canonical route is **`/banking/transfer-own-accounts`** (the old path is a redirect, 2026-06-06 consolidation). The API interface spec's endpoint table header said "10 endpoints" but listed 9.

**Files updated**: `wecms-web/.../internal-transfer-vnd/own-account-transfer/README.md` (canonical route + redirect note). The interface-spec count and the 10th `/nxisas/logging` row are reflected in [banking-source-index.md](banking-source-index.md) §4.

## Transfer History — Accurate (was a direct contradiction)

`functional-specification.md` + `interface-specification.md` claimed "no `@PreAuthorize` — JWT is the only gate" (OQ-5). Verified: `TransferHistoryController` line 54 carries `@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-history')")` — the endpoint **is** menu-authorization gated.

**Files updated**: `internal-transfer-vnd/transfer-history/{functional-specification,interface-specification,README,refactor-gap-analysis,migration-guide,test-scenarios}.md` (corrected to the menu-authz gate; added `@PreAuthorize` to the controller signature block; OQ-5 resolution updated).

**Flag (code-tree, not edited)**: the controller's own javadoc (lines 31–34) still says "JWT-only" — contradicts its own annotation on line 54 (gap TXH-1, dev). Also: TXH-2 (`WBE_TRANSFER_HISTORY_INVALID_PERIOD` missing from `messages_en.properties`), TXH-3 (V196 file header mislabeled `V181`) — dev follow-ups.

## Daily Cash — Accurate (was "Java live recompute")

README / interface-spec / database-mapping / refactor-parity-verification described a Java live-recompute (`DailyCashRecomputeRepository`) as the active source. Verified: `DailyCashReportService` calls `rept_0900_001_rec1..4` **directly** via `DailyCashReportFunctionRepository` (2026-06-13); the recompute repo is `@Deprecated(forRemoval=true)`.

**Files updated**: `reports/daily-cash/{README,interface-specification,database-mapping,refactor-parity-verification,fix-plan}.md` (active source → direct DB-function calls; historical Java-recompute prose reframed as superseded, not deleted).

**Open**: DC-2 (frontend exports client-side via `xlsx-js-style`; backend `DailyCashReportExportProvider` exists but is unwired — diverges from the shared backend-POI rule); DC-3 (rec3/rec4 cash sections need `CASH_REPY_MSTR` data; `LST_COLL_DATE` null).

## Daily Financial — Accurate (HS FE docs were "stub/mock")

CMC docs were accurate (live `rept_0015_001_r001_rec1` via `DailyFinancialCmcFunctionRepository`). The HS **frontend** docs described HS as a "backend stub / fixed 6-row seed / frontend mock". Verified: `ReportService.dailyFinancialHs()` calls `fn_get_haengsung_daily_financial_report` via `DailyFinancialHsReportRepository`, **live since 2026-06-14** (`REPT_TRNS_HSTR` + the function added to the baseline).

**Files updated**: `wecms-web/.../reports/daily-financial/{README,api-consumption,component-index,screen-flow,test-scenarios}.md` (HS = live; frontend mock is now only a fallback). Kept the accurate caveat that HS FE wire types still reflect the stub-era shape and `projectHsRows` bridges them (DF-3, pending). Open P1 items DF-4 (`activeAccountsOnly` widening), DF-5 (`includeInternalTransfers` totals), DF-6 (inline grid), DF-7 (backend Excel) remain documented.

## Cash Flow — Accurate (was "real Java aggregation")

README / `business-flow.md` / `refactor-gap-analysis.md` stated the calc is "reimplemented in Java" and "`get_cashflow_report` is not called". Verified: `CashFlowReportService.search(...)` delegates to `get_cashflow_report(...)` **directly** (`CashFlowReportRepository.java:67`, decision 2026-06-13); the `service/cashflow/*` Java classes are retained but **off the active path**.

**Files updated**: `reports/cash-flow/{README,business-flow,refactor-gap-analysis,source-code-index,test-scenarios,database-mapping,interface-specification}.md` (live path = direct `get_cashflow_report` call; the Java pipeline reframed as retained-but-off-active-path; `CashFlowReportRepository` added as the live read source). Open: CF-2 (mark/retire the unwired `service/cashflow/*` classes — dev).

---

## Root-level docs

The audit request asked to convert root-level Banking docs into summaries with the banner:

```
⚠️ This document is a high-level feature summary.
The detailed source of truth is located in: docs/features/banking/{module}/
```

State after this pass:
- **Old flat single-file specs** (`account-inquiry.md`, `transaction-history.md`, `smart-organizer.md`, the report `*.md`, `own-account-transfer.md`, `transfer-history.md`) were folded into each leaf folder as **`legacy-spec.md`** (Smart Organizer: `legacy-spec-{vnd,fcy}.md`), each carrying a "primary documentation home moved / preserved" banner that points at the authoritative folder. They are no longer competing root-level sources of truth — the summary-conversion intent is met.
- **Group hub READMEs** (`current-savings-vnd/README.md`, `current-savings-fcy/README.md`, both trees) were converted to navigation summaries carrying the exact ⚠️ banner + links to the authoritative module folders. `internal-transfer-vnd/README.md` and `reports/README.md` are equivalent hub indexes.
- **`banking/README.md`** (both trees) is the menu-level hub index; it already links the three audit deliverables and the per-module leaf folders.
- **Cross-cutting docs `bank-credentials.md` and `menu-authorization-and-product-types.md` / `menu-sidebar-route-guard-behavior.md`** are **not** in the 10-module scope and have **no single module home**, so the module-pointing banner does not apply. They are indexed as root-level reference docs in `banking/README.md` and in [banking-source-index.md](banking-source-index.md) §9. Left in place.

## Process notes

- All edits preserve existing doc structure; accurate sections were left untouched. Superseded prose was reframed as historical, not deleted.
- The three highest-impact corrections (Cash Flow, Daily Cash, Daily Financial HS data sources) and the two contradictions (Transfer History `@PreAuthorize`, Account Inquiry sort key) were **verified directly against code** (`AccountSortField`, `TransferHistoryController`, `CashFlowReportRepository`) before editing.
- The Banking docs tree was reorganized mid-audit into the menu-group nested layout; the deliverables and edits target the **current** structure.
- **No application code was modified** by this audit (docs-only). Two stale **code-tree** notes are flagged for a developer: `TransferHistoryController` javadoc (TXH-1) and the wecms-api CLAUDE.md Smart-Organizer Feature-Status note (SO-1).

# Daily Financial (HS) — Backend Documentation Package

> **Scope of this folder.** Implementation-accurate backend documentation for the **Daily Financial
> (HS)** report — the HAENGSUNG (`PRDT_TYPE = 'H'`) variant of Daily Financial, legacy JEX
> `rept_0017_001`. Every statement here is backed by the actual `wecms-api` source, the legacy JEX
> tree (`WECMS DEV/`, read-only), and the two closed audit records
> ([daily-financial-hs-legacy-audit.md](../../../../audits/daily-financial-hs-legacy-audit.md),
> [daily-financial-hs-final-audit.md](../../../../audits/daily-financial-hs-final-audit.md)).
> Anything that could not be traced to source is explicitly marked
> *"Not verified from implementation."* This package supersedes the "HS is folded into the
> daily-financial package" note in [`../README.md`](../README.md) — the CMC sibling package
> ([`../daily-financial/`](../daily-financial/README.md)) keeps only the CMC variant.

| Field | Value |
|---|---|
| Feature slug | `daily-financial-hs` (route-based; never `rept_0017_001`) |
| Module | Banking → Reports |
| Refactor route | `/banking/reports/daily-financial-hs` (Next.js) |
| Refactor base URI | `/api/v1/banking/reports/daily-financial-hs` (Spring Boot) |
| Permission cd | `banking-reports-daily-financial-hs` (`@menuAuthz` on all 3 endpoints; product tree `H` only) |
| Audit `WSVC_ID` | n/a — this feature writes **no** `AUDIT_LOGS_INFM` rows (legacy parity; see [functional-specification.md §7](functional-specification.md#7-audit--logging)) |
| View Setting | `banking-reports-daily-financial-hs` / `default` — seeded by the **manual DBA script** [V249__sys_scr_option_daily_financial_hs.sql](../../../../../src/main/resources/db/migration/V249__sys_scr_option_daily_financial_hs.sql) |
| i18n key prefix (frontend) | `report.daily_financial_hs.*` |
| Refactor status | ✅ **PASS — code-complete (2026-07-02).** Read + per-field save + template Excel download implemented, tested, and closed by [daily-financial-hs-final-audit.md](../../../../audits/daily-financial-hs-final-audit.md). Remaining items are **manual QA only** (authenticated browser smoke, human Excel open, per-env V249 seed run). |

---

## 1. Feature overview

Daily Financial (HS) is a **point-in-time (as-of-date), bank-grouped daily cash ledger** for the
HAENGSUNG Electronics tenant. For one company (`BIZ_KEY`) and one chosen date it renders one wide
table, grouped per bank account:

1. **HEADER** row — bank name, account type, account number (+ optional due-date/branch text baked
   into the header string by the DB function).
2. **BEFORE_BALANCE** row — the account's opening balance for the day.
3. **TRANSACTION** rows — the day's transactions with **five user-editable cells**: Receipt Code
   (`REPT_CD`), Payment Code (`PYMN_CD`), Description EN (`DESC_NM_EN`), Description KO
   (`DESC_NM_KO`), Memo (`MEMO`) — saved per-field on blur/Enter.
4. **ACCOUNT_TOTAL** row — blue *SUB TOTAL* per account.
5. **CURRENCY_TOTAL** row — green *TOTAL* closing each currency section (VND → USD → KRW); its
   Memo column carries the company-wide actual balance (`balance_company_actual`).

All balances, receipts, payments, subtotals and grand totals are **computed inside the legacy DB
function** `fn_get_haengsung_daily_financial_report` — there is **no Java-side calculation**
(see [database-mapping.md](database-mapping.md)). Excel download is a **template-populate** port of
the legacy JSP (per-locale XLSX templates, EN/KO/VI).

It is the HAENGSUNG sibling of **Daily Financial (CMC)** (`rept_0015_001` →
[`../daily-financial/`](../daily-financial/README.md)) — a fundamentally different data shape
(bank-grouped ledger vs per-currency status snapshot) and user flow (eager render vs search-gated).
The two are deliberately separate routes/pages and must never be merged
(`wecms-web/CLAUDE.md` §7c).

**Intended users:** HAENGSUNG treasury/accounting staff doing daily fund-status (자금일보)
monitoring, coding, and reporting to HQ.

---

## 2. Documents in this package

| File | Purpose |
|---|---|
| [README.md](README.md) | This file — overview, refactor status, index, executive summary. |
| [business-flow.md](business-flow.md) | User workflow, screen flow, backend orchestration flow, data-flow narrative. |
| [functional-specification.md](functional-specification.md) | Search conditions, buttons, business rules, totals, editable-save rules, Excel behaviour, empty-result + error handling. |
| [interface-specification.md](interface-specification.md) | The 3 REST endpoints — request/response shapes, DTOs, validation, HTTP status, frontend callers; Excel export structure. |
| [database-mapping.md](database-mapping.md) | `REPT_TRNS_HSTR` + `fn_get_haengsung_daily_financial_report`: purpose, columns, CRUD, ordering, filters. |
| [legacy-spec.md](legacy-spec.md) | Legacy `rept_0017_001` behaviour vs refactor — request flow, SQL, Excel, preserved behaviour, intentional differences. |
| [source-code-index.md](source-code-index.md) | Full traceability: JSP / TS / WSVC / IDO / DAO / refactor Java / templates / tests / seeds. |
| [migration-guide.md](migration-guide.md) | Legacy JSP architecture → Spring architecture; templates, localization, deployment notes (V249). |
| [test-scenarios.md](test-scenarios.md) | Search / validation / currency / save / download / empty / large-data / permission / regression cases. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Per-feature Legacy vs Refactor table with status / gap / risk / resolution. |
| [parity-verification-report.md](parity-verification-report.md) | Category-by-category parity verdict with evidence + overall parity percentage. |

---

## 3. Related documents

- **Audit records (authoritative closure evidence):**
  [audits/daily-financial-hs-legacy-audit.md](../../../../audits/daily-financial-hs-legacy-audit.md)
  (2026-07-01 legacy semantics + parity plan) ·
  [audits/daily-financial-hs-final-audit.md](../../../../audits/daily-financial-hs-final-audit.md)
  (2026-07-02 closure record — fixes 1–12, validation, PO divergences).
- **CMC sibling package:** [`../daily-financial/`](../daily-financial/README.md) — its §4 row
  "HAENGSUNG variant" reflects the 2026-06-14 snapshot and is superseded by this package.
- **Reports group index:** [`../README.md`](../README.md).
- **Frontend counterpart package (same slug):**
  [wecms-web/docs/features/banking/reports/daily-financial-hs/](../../../../../../wecms-web/docs/features/banking/reports/daily-financial-hs/README.md).
- **JEX-side bug on the shared DB function:**
  [docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md](../../../../../../docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md)
  (account-type `'02'` label; see [refactor-gap-analysis.md](refactor-gap-analysis.md) R-4).
- **Backend rules:** [`wecms-api/CLAUDE.md`](../../../../../CLAUDE.md) — §"Legacy report DB
  functions — Sprint 1 stance" (documents the `fn_get_haengsung_daily_financial_report`
  single-funnel exception), §"Refactor identifier naming".

---

## 4. Executive summary

### What this feature is

A read-mostly report with a narrow write path. In JEX it is one view (`rept_0017_001_view.jsp` +
`rept_0017_001.ts`), one read action (`rept_0017_001_r001` → IDO `DAILY_FINCL_REPT_R001` → the
set-returning PL/pgSQL function), one per-field update action (`rept_0017_001_u001` →
`ReptTrnsHstrDAO.patch`), and one Excel action (`rept_0017_001_r002_excel` — template-populate
POI). The refactor reproduces exactly that surface as three Spring endpoints.

### Refactor completion

| Surface | In-scope target | State | Evidence |
|---|---|---|---|
| Read report (date + bank/account filter) | yes | **Done** | `ReportController` `GET /daily-financial-hs` → `ReportService.dailyFinancialHs` → `DailyFinancialHsReportRepository.fetchReport` (single funnel; ORDER BY mirrors legacy IDO incl. the load-bearing currency-first key) |
| Per-field editable save (5 fields, TRANSACTION rows) | yes | **Done (2026-07-01)** | `PATCH /daily-financial-hs/entries/{acctSeqNo}` → `DailyFinancialHsWriteService.updateEntry` (tenant-scoped PK patch of `REPT_TRNS_HSTR`, `MOD_ID`/`MOD_DTM` stamped) |
| Excel download (template-populate, EN/KO/VI) | yes | **Done (2026-07-01; titles PO-localized 2026-07-02)** | `GET /daily-financial-hs/report` → `DailyFinancialHsDownloadService` + [templates/reports/daily-financial-hs/](../../../../../src/main/resources/templates/reports/daily-financial-hs/) |
| View Setting (12-column popover) | yes | **Done — DB-backed (2026-07-01)** | Shared `/api/v1/screen-options` endpoints + manual seed V249 (screenId `banking-reports-daily-financial-hs`/`default`) |
| Per-currency TOTAL placement + hidden-column alignment + spacer-row removal | yes | **Done (2026-07-02)** | Final audit §4 fixes 8–11 |
| Authorization | yes | **Done** | `@PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial-hs')")` on all 3 handlers; H-tree menu split (F/M carry `banking-reports-daily-financial`) |
| Unit tests | yes | **Done** | `ReportServiceDailyFinancialHsTest` · `DailyFinancialHsWriteServiceTest` · `DailyFinancialHsDownloadServiceTest` — full `./gradlew test` green (2026-07-02) |
| Sorting / pagination / export column-toggle | **out of scope** | **Not built — legacy parity** | Legacy `rept_0017_001` has none of the three; V249 seeds `sort_yn='N'` on all 12 columns; Excel always emits all 12 columns |

**Completion estimate (evidence-based, 2026-07-02): ≈ 100 % of the in-scope legacy surface**, with
three **intentional, PO-approved divergences** (localized Excel titles EN/VI, KRW inclusion,
per-column summary cells — see [legacy-spec.md §6](legacy-spec.md#6-intentional-differences)) and
manual QA outstanding (authenticated browser smoke, human Excel open, per-environment V249 run).
See [parity-verification-report.md](parity-verification-report.md) for the category breakdown.

### Migration complexity: **Medium** (retired)

- **Lowering factors:** the whole calculation surface lives in the DB function (no Java parity
  math); the write path is a plain PK patch; legacy Excel was already template-based, so the port
  is a JSP→Java translation of the populate loop.
- **Raising factors (all resolved):** multi-currency merge geometry in POI (fix 7), the
  currency-first ordering subtlety (fix 8), and View Setting alignment under hide/reorder (fix 10)
  — each is regression-pinned by tests or documented in the final audit.

---

## Legacy & Migration Traceability

| Field | Value |
|---|---|
| Legacy JEX menu | **Daily Financial** (HAENGSUNG product tree `WE_CMSVN_HAENGSUNG`; title ML `rept_0017_001_v001`) |
| Legacy view service | `rept_0017_001` (view; WSVC name "Daily Financial Report (haengsung)") |
| Legacy read API | `rept_0017_001_r001` → IDO `DAILY_FINCL_REPT_R001` → `FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT(BIZ_KEY, PERIOD_DATE, ACCT_NO)` |
| Legacy save API | `rept_0017_001_u001` → `ReptTrnsHstrDAO.patch` → IDO `REPT_TRNS_HSTR_U000` |
| Legacy Excel | `rept_0017_001_r002_excel` → `rept_0017_001_r002_excel_view.jsp` (template-populate POI) |
| Legacy bank list | `cash_0001_001_r003` (refactor derives the dropdown from the `ALL` report's HEADER rows instead — see [legacy-spec.md §6](legacy-spec.md#6-intentional-differences)) |
| Legacy Excel templates | `WECMS DEV/WECMS_STATIC/web/file/excel/VN/WeCMS_Daily_Financial_Report_{EN,KO,VI}.xlsx` — all three byte-identical (Korean A1 title) per the final audit's SHA-256 check |
| Legacy View Setting rows | `sys_scr_option_wecms` `web_svc_id='rept_0017_001'` / `dym_area_id='table1'` (12 columns) → remapped to `banking-reports-daily-financial-hs`/`default` by V249 |

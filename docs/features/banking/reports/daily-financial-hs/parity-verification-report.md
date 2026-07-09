# Daily Financial (HS) — Parity Verification Report

> Category-by-category parity of the refactor against legacy `rept_0017_001`, based ONLY on
> verified implementation: source reads of both trees, the unit-test suite (green 2026-07-02),
> and the closure evidence in
> [audits/daily-financial-hs-final-audit.md](../../../../audits/daily-financial-hs-final-audit.md)
> (incl. SHA-256/CRC template checks and side-by-side screenshots vs
> `dev.wecms.vn/rept_0017_001.act`). Where evidence is manual-only it says so.

---

## 1. Category verdicts

### UI

- **Legacy:** jQuery-rendered bank-grouped ledger; blue SUB TOTAL (`ks-light-blue-background`),
  green TOTAL (`ks-light-green-background`); bilingual KO+EN headers; exactly one `<tr>` per data
  row.
- **Refactor:** `DailyFinancialHsPage` column-driven table; same row taxonomy, subtotal/total
  colouring via WB tokens; bilingual headers from `report.daily_financial_hs.col_ko.*` + `col.*`;
  spacer rows removed (fix 11).
- **Evidence:** side-by-side screenshots across the fix loop (final audit §7); FE `tsc`/eslint
  green. **Remaining gap:** none functional; pixel-level typography is governed by the frontend
  token system, not legacy CSS. **Recommendation:** none.

### Business logic

- **Legacy:** all figures computed in the DB function; only-TRANSACTION editability; Search-only
  refetch; saved-data-only Excel.
- **Refactor:** identical — no Java-side calculation (`ReportService.dailyFinancialHs` is a
  projection); editability gated by row type; same refetch/download semantics.
- **Evidence:** repository/service source; `ReportServiceDailyFinancialHsTest`;
  `DailyFinancialHsWriteServiceTest`. **Gap:** none. **Recommendation:** keep the entity's
  partial column mapping as the structural write-guard.

### SQL

- **Legacy:** IDO `DAILY_FINCL_REPT_R001` — function call + ROW_TYPE/currency WHERE + 5-key ORDER
  BY.
- **Refactor:** same function, same ORDER BY verbatim; WHERE dropped (ROW_TYPE guard redundant;
  currency filter removed by PO decision).
- **Evidence:** `DailyFinancialHsReportRepository.java:49-83` vs `IDO.DAILY_FINCL_REPT_R001.xml:24-64`.
  **Gap:** KRW inclusion (intentional). **Recommendation:** none.

### Exchange rate

- **Legacy & refactor:** no exchange-rate logic exists in this feature on either side — amounts
  stay in native currency. **Gap:** n/a. (Documenting any FX behaviour here would be invention.)

### Calculation

- **Legacy & refactor:** single engine — `fn_get_haengsung_daily_financial_report` computes
  opening balances, per-account SUB TOTALs, per-currency TOTALs, `balance_company_actual`.
- **Evidence:** no aggregation code exists in the refactor service (source-verified); function
  output verified against the live refactor DB (legacy audit §3.1).
- **Gap:** the function **body** is DB-resident and shared with live JEX — its internals
  (incl. the BUG-001 account-type-label CASE) are *not verified from implementation* in this
  package. **Recommendation:** verify BUG-001 status on the refactor DB copy.

### Download (Excel)

- **Legacy:** template-populate POI, 12 columns, saved DB data, locale template + date slots.
- **Refactor:** faithful port + corrected merge bounds (multi-currency overlap) + templates
  shipped on the classpath.
- **Evidence:** `DailyFinancialHsDownloadServiceTest` 4/4 (opens real generated workbooks);
  per-zip-entry CRC diff vs legacy templates. **Gap:** EN/VI A1 titles localized (intentional,
  PO-pinned). **Recommendation:** human Excel open per locale (manual QA), then done.

### Localization

- **Legacy:** ML codes `rept_0017_001_v001–v006`; Korean-only Excel title everywhere.
- **Refactor:** FE keys `report.daily_financial_hs.*` complete in EN/KO/VI; backend
  `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND`; per-locale Excel titles.
- **Evidence:** locale files + templates + tests. **Gap:** Excel title divergence (intentional);
  no Khmer (platform-wide). **Recommendation:** raise cross-system title alignment with the PO
  only if coexistence comparisons matter (final audit §8.4).

### Permission

- **Legacy:** HAENGSUNG product-tree menu; service gating at the JEX layer (WSVC flags — *not
  fully re-verified*).
- **Refactor:** `@menuAuthz.canAccess('banking-reports-daily-financial-hs')` on all three
  handlers; H tree carries the node (F/M carry the CMC cd); JWT tenant scoping; composite-PK
  write isolation.
- **Evidence:** controller annotations; `EndpointAuthorizationCoverageTest` (build-failing);
  navigation seed split documented in `wecms-api/CLAUDE.md`. **Gap:** none known.
  **Recommendation:** none.

### API

- **Legacy:** JexData `.jct` POST envelope (`REC1`, UPPER_SNAKE).
- **Refactor:** 3 slug-based REST endpoints, `ApiResponse<T>`, lowercase snake_case, RFC 5987
  binary download.
- **Evidence:** [interface-specification.md](interface-specification.md); FE callers in
  `lib/api/banking.ts`. **Gap:** by design (CTO wire directive) — field-level mapping is 1:1
  (22 REC1 fields ↔ 22 LedgerRow fields). **Recommendation:** none.

### Database

- **Legacy:** `REPT_TRNS_HSTR` + function.
- **Refactor:** same objects (PO-approved baseline addition); write path updates only the 5
  legacy editable columns; no schema drift, no new tables, no key generation.
- **Evidence:** entity/repository source; [database-mapping.md](database-mapping.md).
  **Gap:** full physical DDL of the refactor copy not re-dumped (*not verified*); V249 is a
  manual per-env step. **Recommendation:** include the function+table check in every env
  provisioning run.

### Excel (artifact integrity)

- **Evidence:** SHA-256 — KO template byte-identical to legacy; EN/VI differ **only** in
  `xl/sharedStrings.xml`; VI diacritics byte-verified (`ằ` = `E1 BA B1`). **Gap:** none beyond
  the pinned title divergence.

---

## 2. Overall parity

| Scope | Parity | Basis |
|---|---|---|
| **In-scope legacy surface (read + save + Excel + View Setting + authz)** | **≈ 100 %** | Every legacy behaviour either reproduced (source/test-verified) or replaced by one of the **3 PO-approved intentional divergences** (Excel titles EN/VI · KRW inclusion · per-column summary cells). No unresolved functional gaps. |
| **Verification confidence** | high for automated surfaces; **manual-QA pending** for the last mile | `./gradlew test` green (10 HS test cases incl. workbook-opening assertions); authenticated browser smoke + human Excel open still to run (final audit §8). |
| **Inherited/DB-side** | 1 open item | BUG-001 (account-type `'02'` label) lives in the shared DB function; refactor-side status unverified. |

**Verdict:** parity **PASS — code-complete** (matching the final audit). The feature is
UAT-ready pending the manual QA items and the per-environment V249 seed run; nothing in this
package's verification contradicts that closure.

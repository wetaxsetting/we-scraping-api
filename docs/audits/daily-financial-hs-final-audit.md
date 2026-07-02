# Daily Financial (HS) — Final Audit

**Feature:** Daily Financial (HS) — HAENGSUNG bank-grouped daily ledger
**Legacy screen:** `rept_0017_001.act` (product type `H`)
**Refactor route:** `/banking/reports/daily-financial-hs` (permission cd `banking-reports-daily-financial-hs`)
**Audit date:** 2026-07-02 · **System:** Refactor (`wecms-api` + `wecms-web`) vs live JEX (`WECMS DEV/`)
**Status:** ✅ **PASS — code-complete** (manual QA items in §8)

Companion doc: [daily-financial-hs-legacy-audit.md](daily-financial-hs-legacy-audit.md) (2026-07-01 legacy behavior
audit + parity plan — the detailed legacy semantics live there; this document is the closure record).
Related: [main-financial-pages-sit-readiness-action-plan.md](../../../docs/audits/main-financial-pages-sit-readiness-action-plan.md)
(workspace-level), [../features/banking/reports/README.md](../features/banking/reports/README.md),
`wecms-web/CLAUDE.md` §7b/§7c/§7e + §13.

---

## 1. Scope

This audit closes the full feature surface:

| Surface | Covered |
|---|---|
| API read/search | `GET /api/v1/banking/reports/daily-financial-hs` (date + bank/account filter) |
| Frontend UI | Bank-grouped ledger table, search card, bank dropdown, rowSpan groups, SUB TOTAL / TOTAL rows |
| Editable save | Per-field auto-save on blur/Enter (TRANSACTION rows only) |
| View Setting | DB-backed column visibility/order via the shared screen-options API |
| Excel download | Template-populate XLSX (EN/KO/VI) |
| Legacy parity gaps | All fixed — itemized in §4 |
| Intentional divergence | PO-approved Excel title localization + KRW inclusion — §6 |

## 2. Legacy sources checked

All under read-only `WECMS DEV/`:

| Artifact | Path / id |
|---|---|
| View JSP | `WECMS/web/view/jex/wabooks/rept/rept_0017_001_view.jsp` |
| Client TS | `WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0017_001.ts` (`fn_renderTableRows`, `fnGetDataRow`, `fn_saveData`) |
| Read action | `rept_0017_001_r001_act.jsp` → IDO `DAILY_FINCL_REPT_R001` |
| Save action | `rept_0017_001_u001_act.jsp` → `ReptTrnsHstrDAO.patch` → IDO `REPT_TRNS_HSTR_U000` |
| Excel action | `rept_0017_001_r002_excel_view.jsp` (template load + runtime population, audited line-by-line) |
| Read IDO | `WECMS_ETC/xml/service/IDO/IDO.DAILY_FINCL_REPT_R001.xml` — **ORDER BY currency-first** (`CASE CURRENCY VND→1, USD→2, KRW→3`), then `ACCOUNT_TYPE_ORDER, BANK_NAME_SORT, ACCOUNT_NUMBER NULLS LAST, TRANSACTION_SEQ`; filters `CURRENCY IN ('VND','USD')` |
| Patch IDO | `IDO.REPT_TRNS_HSTR_U000.xml` — `SET :FIELDS_TO_UPDATE WHERE BIZ_KEY=? AND ACCT_SEQ_NO=?` |
| DB function | `FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT(biz_key, as_of_date, account_filter)` — emits `HEADER/BEFORE_BALANCE/TRANSACTION/ACCOUNT_TOTAL/CURRENCY_TOTAL`; grand totals carry `account_type_order = 999990/999991/999992` (sort to stream end without the IDO's currency key) |
| Excel templates | `WECMS_STATIC/web/file/excel/VN/WeCMS_Daily_Financial_Report_{EN,KO,VI}.xlsx` — **all three byte-identical** (same SHA-256); A1:L1 title is the Korean `일 일 자 금 수 지 현 황` in every language; the JSP never writes A1 |

Key legacy renderer finding (2026-07-02): the legacy tbody receives **exactly one `<tr>` per data row**
(`$tbody.append(tr)` is the only in-loop append) — no spacer/blank rows at any boundary.

## 3. Final implementation summary

### Backend (`wecms-api`)

| Endpoint | Implementation |
|---|---|
| `GET /api/v1/banking/reports/daily-financial-hs` | `ReportController` → `ReportService.dailyFinancialHs` → `DailyFinancialHsReportRepository.fetchReport` (single funnel to the DB function; ORDER BY mirrors the legacy IDO verbatim) → `DailyFinancialHsRow` projection → `DailyFinancialHsReport` DTO. No Java-side calculation. |
| `PATCH /api/v1/banking/reports/daily-financial-hs/entries/{acctSeqNo}` | `DailyFinancialHsWriteService` — tenant-scoped PK `(BIZ_KEY, ACCT_SEQ_NO)` patch of `REPT_TRNS_HSTR` (5 editable columns: `rept_cd/pymn_cd/desc_nm_en/desc_nm_ko/memo`), `MOD_ID`/`MOD_DTM` stamped, `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND` on miss. Partial DTO: null = unchanged, `""` = clear. |
| `GET /api/v1/banking/reports/daily-financial-hs/report?asOf&accountFilter&lang` | `DailyFinancialHsDownloadService` — template-populate (POI `WorkbookFactory`), re-runs the read function so the workbook reflects saved DB data. Always 12 columns, independent of View Setting (legacy parity). |

### Frontend (`wecms-web`)

`app/(dashboard)/banking/reports/daily-financial-hs/page.tsx` →
`components/banking/reports/daily-financial-hs/`: `DailyFinancialHsClient.tsx` (search/save/download
orchestration), `DailyFinancialHsPage.tsx` (column-driven ledger table + View Setting popover),
`projectRows.ts` (row-stream → `BankGroup[]` projection + bank-dropdown derivation), `columns.ts`
(12-column registry, legacy widths), `types.ts`. API fetchers in `lib/api/banking.ts`
(`fetchDailyFinancialHs`, `updateDailyFinancialHsEntry`, `downloadDailyFinancialHsReport`).

### DB / View Setting seed

- Report source table `REPT_TRNS_HSTR` + the DB function are PO-approved baseline additions (see the
  legacy audit §1). 70 seed rows exist on the shared dev DB (`VN_VNM0004_000001`).
- View Setting seed: `db/migration/V249__sys_scr_option_daily_financial_hs.sql` — **manual DBA-run**
  (Flyway is disabled; `ddl-auto=validate`). ScreenId `banking-reports-daily-financial-hs` / `default`,
  12 columns, `ON CONFLICT DO NOTHING`. Until seeded, `GET /screen-options` returns `WBE2001` and the
  frontend falls back to `DEFAULT_VIEW_SETTING_ITEMS` (all 12 visible) — degraded, not broken.

### Excel templates

`src/main/resources/templates/reports/daily-financial-hs/WeCMS_Daily_Financial_Report_{EN,KO,VI}.xlsx`
— copied from legacy; EN/VI A1 titles localized 2026-07-02 (§6). KO remains byte-identical to legacy.
Per-zip-entry CRC diff confirms EN/VI differ from legacy **only** in `xl/sharedStrings.xml`.

### Tests

- `DailyFinancialHsDownloadServiceTest` — 4 tests: data-area population + numeric cells, KO/VI legacy
  filenames, empty-report no-throw, and `templateTitleAndDateSlots_perLocale` (opens the generated
  workbook per locale; asserts A1 title, A2 + L3 date slots, A1:L1/A2:L2 merges intact).
- `DailyFinancialHsWriteServiceTest`, `ReportServiceDailyFinancialHsTest` — save-path + read-path units.

## 4. Important fixes completed

| # | Fix | Date |
|---|---|---|
| 1 | Real API wiring — hardcoded 6-row stub removed; whole report funnels through the DB function | 2026-06-13 |
| 2 | Per-field auto-save on blur/Enter (`rept_0017_001_u001` parity) | 2026-07-01 |
| 3 | No input boxes on BEFORE_BALANCE rows — editable inputs render only on TRANSACTION rows | 2026-07-01 |
| 4 | `WBI0000` void-save success handling (save response no longer misread as error) | 2026-07-01 |
| 5 | DB-backed View Setting via shared `useViewSetting` + `/api/v1/screen-options` (was client-side state) | 2026-07-01 |
| 6 | Excel rewritten to template-populate (was programmatic; legacy loads the template) | 2026-07-01 |
| 7 | Multi-currency Excel merge fix — bank/currency vertical merges bounded to `[first data row .. ACCOUNT_TOTAL]`, never absorbing CURRENCY_TOTAL rows (POI overlap → 500/WBE9999 on multi-ccy download) | 2026-07-01 |
| 8 | Per-currency TOTAL placement — repository ORDER BY now mirrors the legacy IDO's leading currency key; without it all CURRENCY_TOTAL rows (`account_type_order 99999x`) sorted to stream end and the FE collapsed them onto the last group. FE additionally anchors each TOTAL to the last group of **its** currency | 2026-07-02 |
| 9 | TOTAL value mapping — `before_balance/receipt/payment/balance` → their columns, `balance_company_actual` → Memo column | 2026-07-02 |
| 10 | View Setting hidden-column alignment — every row (data, BEFORE_BALANCE, SUB TOTAL, TOTAL) renders strictly one cell per visible column, zero colSpan; label hosts in the cell left of the first numeric column (or shares it); table `w-full` + `minWidth = Σ` visible widths (no blank right strip, scroll preserved) | 2026-07-02 |
| 11 | Removal of artificial blank rows — Figma-derived "-" separator rows deleted; tbody `<tr>` count now exactly matches legacy (`ΣTᵢ + 2A + C`) | 2026-07-02 |
| 12 | Localized Excel titles (PO request — §6) | 2026-07-02 |

## 5. Final behavior

- **Search** by As-of date (defaults to today) + bank/account multi-select (`ALL` or JSON
  `[{BANK_CD, ACCT_NO}]`, verbatim legacy contract); report reloads only on Search (legacy parity).
- **Rows** render in DB order: per account — BEFORE_BALANCE → TRANSACTION× → SUB TOTAL (blue); each
  currency section closes with a green TOTAL. No spacer rows; only the four legacy row types exist.
- **Only TRANSACTION rows are editable** (Receipt/Payment code, Description EN/KO, Memo); saves fire
  per-field on blur/Enter against `REPT_TRNS_HSTR`.
- **TOTAL appears after each currency group** (VND → USD → KRW), values mapped per §4-9.
- **View Setting persists** per user via the shared screen-options API (save/reset/lazy-init); the table
  stays aligned under any hide/reorder combination.
- **Excel always exports all 12 columns** regardless of View Setting (legacy `_r002_excel` has no
  visibility logic — confirmed).

## 6. Intentional divergence from legacy

1. **Excel A1 title localized per language (PO/business request, 2026-07-02 — NOT a parity bug).**
   Legacy's three templates are byte-identical and ship the Korean title `일 일 자 금 수 지 현 황` in
   EN, KO, and VI alike (the legacy JSP never writes A1). By business decision the **refactor
   templates** now carry:
   - EN: `Daily Financial Report`
   - KO: `일 일 자 금 수 지 현 황` (unchanged)
   - VI: `Báo cáo tài chính hằng ngày`

   Only shared string 0 (the A1 cell text) was edited inside the EN/VI xlsx; sheet XML, styles, merges,
   row heights, number formats, and column data are CRC-identical to legacy. Java still never writes A1 —
   the title remains template-owned. The live JEX templates are untouched (read-only tree); aligning the
   legacy system is a separate JEX-side change if the business wants it. **Do not "fix" the EN/VI titles
   back to Korean in a future parity audit** — pinned by `templateTitleAndDateSlots_perLocale`.
2. **KRW included in the report.** The legacy IDO filters `CURRENCY IN ('VND','USD')`; the refactor
   keeps KRW (and any other currency) so the report covers the tenant's full account set, per the
   2026-07-02 requirement ("one TOTAL for VND, USD, KRW depending on returned data"). Documented in the
   repository Javadoc.
3. **Summary-row cell structure.** Row count/order/rowSpan geometry match legacy 1:1, but SUB TOTAL /
   TOTAL rows render one `<td>` per visible column instead of legacy's colSpan merges — deliberately more
   robust under View Setting hide/reorder. Visual output equivalent.

## 7. Validation

| Check | Result |
|---|---|
| `./gradlew compileJava` | ✅ green |
| `./gradlew test` (full suite) | ✅ BUILD SUCCESSFUL (2026-07-02, incl. `EndpointAuthorizationCoverageTest`) |
| `./gradlew test --tests "*DailyFinancialHs*"` | ✅ all pass (read + write + download) |
| `DailyFinancialHsDownloadServiceTest` | ✅ 4/4 — incl. per-locale title/date/merge assertions on real generated workbooks |
| `npx tsc --noEmit` (wecms-web) | ✅ green after every FE change |
| `npx eslint components/banking/reports/daily-financial-hs/` | ✅ clean |
| Template integrity | ✅ SHA-256: KO byte-identical to legacy; EN/VI differ only in `xl/sharedStrings.xml` (per-entry CRC diff); VI diacritics byte-verified (`ằ` = `E1 BA B1`) |
| Browser/manual | ✅ Side-by-side screenshots vs `dev.wecms.vn/rept_0017_001.act` reviewed across the fix loop (TOTAL placement, hidden-column alignment, blank-row removal). Full authenticated smoke on localhost remains a manual QA item (§8). |

## 8. Remaining risks / manual QA

1. **Authenticated browser smoke** — `bootRun` (VPN → shared dev DB) + `npm run dev`, then walk:
   search by date/bank → edit a TRANSACTION field (blur + Enter) → reload to confirm persistence →
   View Setting hide/reorder/save/reset → Excel download in EN/KO/VI.
2. **Excel visual open (EN/KO/VI)** — workbooks are POI/test-verified and CRC-verified; a human open in
   real Excel (fonts render, merges look right, Vietnamese diacritics display) is the last-mile check.
3. **Deployment note — View Setting seed:** `V249__sys_scr_option_daily_financial_hs.sql` is a **manual
   DBA script** (Flyway disabled). It must be run in every environment; until then the popover works off
   client defaults and per-user persistence is unavailable (`WBE2001` fallback). Idempotent
   (`ON CONFLICT DO NOTHING`), legacy rows untouched.
4. **Cross-system Excel title mismatch** — live JEX keeps the Korean title in all languages; the refactor
   is localized. Raise with the PO if both systems must align during coexistence.

---

**Final status: PASS.** All identified legacy-parity gaps are fixed and regression-pinned by tests; the
only open items are the manual QA steps above — no known functional gaps remain.

# Daily Financial (HS) — Source Code Index

> Full traceability for the feature. Paths are repo-relative unless prefixed with a tree name.

---

## 1. Refactor backend (`wecms-api`)

| Layer | File | Responsibility |
|---|---|---|
| Controller | [src/main/java/com/kosign/wecms/domain/banking/controller/ReportController.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/controller/ReportController.java) | `GET /api/v1/banking/reports/daily-financial-hs` (~:105-111) · `PATCH …/entries/{acctSeqNo}` (~:124-131) · `GET …/report` (~:143-152). All three `@PreAuthorize("@menuAuthz.canAccess('banking-reports-daily-financial-hs')")`. Shared with the other report endpoints (CMC etc.). |
| Service (read) | [src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java) | `dailyFinancialHs(asOfDt, accountFilter)` (~:374-414) — tenant resolve, blank-normalise, funnel call, row→DTO projection (`AmountFormatter.wire`, null-preserving). |
| Service (write) | [src/main/java/com/kosign/wecms/domain/banking/service/DailyFinancialHsWriteService.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/service/DailyFinancialHsWriteService.java) | `updateEntry(acctSeqNo, update)` — PK lookup, non-null field apply, NBSP sanitize, `MOD_ID` stamp, `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND`. |
| Service (download) | [src/main/java/com/kosign/wecms/domain/banking/service/DailyFinancialHsDownloadService.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/service/DailyFinancialHsDownloadService.java) | `generate(asOf, accountFilter, lang)` — fresh read, locale template load, date slots A2/L3, 12-column populate, merge geometry, number formats, `GeneratedReport`. |
| DTO (response) | [src/main/java/com/kosign/wecms/domain/banking/dto/DailyFinancialHsReport.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/dto/DailyFinancialHsReport.java) | `(asOfDt, accountFilter, rows)` + nested `LedgerRow` (22 fields, wire snake_case). |
| DTO (request) | [src/main/java/com/kosign/wecms/domain/banking/dto/DailyFinancialHsEntryUpdate.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/dto/DailyFinancialHsEntryUpdate.java) | Partial patch record — `@Size` 20/20/255/255/500; `hasAnyField()`. |
| Repository (read) | [src/main/java/com/kosign/wecms/domain/banking/repository/DailyFinancialHsReportRepository.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/repository/DailyFinancialHsReportRepository.java) | **Single funnel** native query over `fn_get_haengsung_daily_financial_report` with the legacy-mirrored ORDER BY; Javadoc records the KRW divergence. |
| Projection | [src/main/java/com/kosign/wecms/domain/banking/repository/DailyFinancialHsRow.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/repository/DailyFinancialHsRow.java) | 22-getter interface projection (amounts `BigDecimal`, nullable). |
| Repository (write) | [src/main/java/com/kosign/wecms/domain/banking/repository/ReptTrnsHstrRepository.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/repository/ReptTrnsHstrRepository.java) | `findByBizKeyAndAcctSeqNo`. |
| Entity | [src/main/java/com/kosign/wecms/domain/banking/entity/ReptTrnsHstr.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/entity/ReptTrnsHstr.java) + [ReptTrnsHstrId.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/entity/ReptTrnsHstrId.java) | `@Table("REPT_TRNS_HSTR")`, `@IdClass`, PK + the 5 editable columns only; extends `BaseEntity`. |
| Excel templates | [src/main/resources/templates/reports/daily-financial-hs/](../../../../../src/main/resources/templates/reports/daily-financial-hs/) | `WeCMS_Daily_Financial_Report_{EN,KO,VI}.xlsx` — copied from legacy; EN/VI A1 localized 2026-07-02; KO byte-identical to legacy. |
| i18n | [src/main/resources/messages.properties](../../../../../src/main/resources/messages.properties) | `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND=Daily Financial entry not found.` |
| Seed (manual) | [src/main/resources/db/migration/V249__sys_scr_option_daily_financial_hs.sql](../../../../../src/main/resources/db/migration/V249__sys_scr_option_daily_financial_hs.sql) | View Setting seed — **manual DBA script** (Flyway disabled); 12 columns, `sort_yn='N'`, `ON CONFLICT DO NOTHING`; legacy `rept_0017_001` rows untouched. |
| Menu/permission | navigation catalogue seed (`MENU_ITEM`/`PRODUCT_MENU` — H tree carries `banking-reports-daily-financial-hs`; F/M carry `banking-reports-daily-financial`) | See `wecms-api/CLAUDE.md` §`domain/navigation` row (Daily Financial split). |

### Tests

| Test class | Cases | Covers |
|---|---|---|
| [src/test/java/com/kosign/wecms/domain/banking/service/ReportServiceDailyFinancialHsTest.java](../../../../../src/test/java/com/kosign/wecms/domain/banking/service/ReportServiceDailyFinancialHsTest.java) | 3 | funnel call args, row-type order + null-amount preservation, blank date→today / blank filter→ALL |
| [src/test/java/com/kosign/wecms/domain/banking/service/DailyFinancialHsWriteServiceTest.java](../../../../../src/test/java/com/kosign/wecms/domain/banking/service/DailyFinancialHsWriteServiceTest.java) | 3 | only-provided-fields apply + `MOD_ID` stamp + tenant scope · empty-string clears / null leaves · not-found throws, no save |
| [src/test/java/com/kosign/wecms/domain/banking/service/DailyFinancialHsDownloadServiceTest.java](../../../../../src/test/java/com/kosign/wecms/domain/banking/service/DailyFinancialHsDownloadServiceTest.java) | 4 | data-area + numeric cells (multi-currency, 2 trailing CURRENCY_TOTALs — merge-overlap regression) · KO/VI legacy filenames · per-locale A1 title + A2/L3 dates + merges (`templateTitleAndDateSlots_perLocale`) · empty report no-throw |

Frontend tests: **ABSENT** — no test files exist under
`wecms-web/components/banking/reports/daily-financial-hs/`.

---

## 2. Refactor frontend (`wecms-web`) — summary

Full detail in the frontend package's
[component-index.md](../../../../../../wecms-web/docs/features/banking/reports/daily-financial-hs/component-index.md).

| File | Responsibility |
|---|---|
| `app/(dashboard)/banking/reports/daily-financial-hs/page.tsx` | server component; metadata; composes `<DailyFinancialHsClient/>` |
| `components/banking/reports/daily-financial-hs/DailyFinancialHsClient.tsx` | fetch/search/save/download orchestration (158 lines) |
| `components/banking/reports/daily-financial-hs/DailyFinancialHsPage.tsx` | presentational ledger table + View Setting popover (611 lines) |
| `components/banking/reports/daily-financial-hs/projectRows.ts` | `projectHsRows` (row stream → `BankGroup[]`) + `deriveBankOptions` |
| `components/banking/reports/daily-financial-hs/columns.ts` | 12-column `COLUMN_REGISTRY` + `DEFAULT_VIEW_SETTING_ITEMS` fallback |
| `components/banking/reports/daily-financial-hs/types.ts` | `LedgerEntry` / `LedgerTotals` / `BankGroup` / `HsEntryPatch` |
| `lib/api/banking.ts` | `fetchDailyFinancialHs` · `updateDailyFinancialHsEntry` · `downloadDailyFinancialHsReport` |
| `lib/types/api.ts` | `ApiDailyFinancialHsReport` · `ApiHsLedgerRow` |
| `lib/viewSettingScreens.ts` | `SCREEN_IDS.BANKING_REPORTS_DAILY_FINANCIAL_HS` + route map |

---

## 3. Legacy JEX (`WECMS DEV/` — read-only source of truth)

| Artifact | Path |
|---|---|
| View JSP | `WECMS/web/view/jex/wabooks/rept/rept_0017_001_view.jsp` |
| Client TS | `WECMS_STATIC/ts-src/js/jex/wabooks/rept/rept_0017_001.ts` |
| Read action | `WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0017_001_r001_act.jsp` |
| Save action | `WECMS/web/WEB-INF/action/jex/wabooks/rept/rept_0017_001_u001_act.jsp` |
| Excel action | `WECMS/web/view/jex/wabooks/rept/rept_0017_001_r002_excel_view.jsp` |
| WSVC XML | `WECMS_ETC/xml/service/WSVC/WSVC.rept_0017_001[.xml\|_r001.xml\|_u001.xml\|_r002_excel.xml]` |
| IDO XML | `WECMS_ETC/xml/service/IDO/IDO.DAILY_FINCL_REPT_R001.xml` · `IDO.REPT_TRNS_HSTR_U000.xml` |
| Entity / PK / DAO | `WECMS/src/wabooks/entity/ReptTrnsHstr.java` · `ReptTrnsHstrPk.java` · `WECMS/src/wabooks/dao/ReptTrnsHstrDAO.java` |
| Excel templates | `WECMS_STATIC/web/file/excel/VN/WeCMS_Daily_Financial_Report_{EN,KO,VI}.xlsx` |
| DB function | `FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT` — **database-resident only** (no DDL in the tree) |

## 4. Documentation & audit records

| Doc | Role |
|---|---|
| [audits/daily-financial-hs-legacy-audit.md](../../../../audits/legacy-parity/daily-financial-hs-legacy-audit.md) | 2026-07-01 legacy semantics + parity plan (P1–P11, G1/G2, D1/D2) |
| [audits/daily-financial-hs-final-audit.md](../../../../audits/legacy-parity/daily-financial-hs-final-audit.md) | 2026-07-02 closure — fixes 1–12, validation matrix, PO divergences, manual-QA list |
| [docs/bug-reports/BUG-001…](../../../../../../docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md) | JEX-side function bug (account-type `'02'` label) |
| This package | implementation-accurate feature documentation (2026-07-02) |

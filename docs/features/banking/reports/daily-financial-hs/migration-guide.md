# Daily Financial (HS) — Migration Guide

> How the legacy `rept_0017_001` JSP service maps onto the Spring architecture, plus the
> deployment/configuration facts an operator or future maintainer needs. Decision history
> (D1 template-populate, D2 per-field save) is recorded in
> [audits/daily-financial-hs-legacy-audit.md §6](../../../../audits/legacy-parity/daily-financial-hs-legacy-audit.md).

---

## 1. Architecture mapping

| Legacy (JEX JSP) | Refactor (Spring Boot) |
|---|---|
| `rept_0017_001_view.jsp` + `rept_0017_001.ts` (jQuery render) | Next.js route `/banking/reports/daily-financial-hs` (see frontend package) |
| `rept_0017_001_r001_act.jsp` → IDO `DAILY_FINCL_REPT_R001` | `ReportController` → `ReportService.dailyFinancialHs` → `DailyFinancialHsReportRepository.fetchReport` (native query, single funnel) |
| `rept_0017_001_u001_act.jsp` → `ReptTrnsHstrDAO.patch` → IDO `REPT_TRNS_HSTR_U000` | `PATCH /entries/{acctSeqNo}` → `DailyFinancialHsWriteService` → `ReptTrnsHstrRepository` + JPA dirty-checked UPDATE |
| `rept_0017_001_r002_excel_view.jsp` (POI over `/file/excel/VN/` templates) | `GET /report` → `DailyFinancialHsDownloadService` (POI over classpath templates) |
| `JexData` `REC1` envelope, UPPER_SNAKE fields | `ApiResponse<DailyFinancialHsReport>`, lowercase snake_case wire (global Jackson) |
| WSVC ids (`rept_0017_001*`) | kebab slug `daily-financial-hs`; permission cd `banking-reports-daily-financial-hs`; REST URIs, package/class names, i18n prefix all slug-based (no legacy ids in any identifier surface) |
| `ValidationHelper.validateRequired(PERIOD_DATE, ACCT_NO)` | optional params with server-side defaults (today / `ALL`) + Bean Validation on the save DTO |
| ML codes `rept_0017_001_v001–v006` | frontend i18n keys `report.daily_financial_hs.*` (EN/KO/VI) |
| `WBE0404` (row not found) | `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND` (new code — JEX ML codes never reused verbatim) |

**What was deliberately NOT ported:** the JEX envelope (`COMMON_HEAD`/`REC`), `IDODynamic`
mechanics, the `cash_0001_001_r003` bank-list service (dropdown derived from the `ALL` report's
HEADER rows), and the legacy IDO's `CURRENCY IN ('VND','USD')` filter (KRW inclusion,
PO-approved).

## 2. Database & migration-safety notes

- **No schema change was made.** The write path updates existing `REPT_TRNS_HSTR` rows by PK; no
  inserts, no key generation, no new columns. Structure stays identical to legacy — JEX → refactor
  data migration for this feature is a plain row copy.
- `REPT_TRNS_HSTR` + `fn_get_haengsung_daily_financial_report` were added to the refactor DB
  **out-of-band as a PO-approved baseline addition** (2026-06-14). They are not created by any
  repo script — provisioning a fresh environment means hand-porting both from the JEX DEV DB
  (source of truth) before this feature can serve data.
- The function is the computation engine on **both** sides. Any future change to its body is a
  cross-system change (live JEX + refactor read the same logic) — treat as a JEX-side DB change
  with PO/DBA approval, not a refactor code change. Note the open JEX-side defect
  [BUG-001](../../../../../../docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md)
  (account-type `'02'` label) lives in that function.

## 3. Deployment checklist (per environment)

1. **Verify the function + table exist** in the target DB
   (`fn_get_haengsung_daily_financial_report`, `REPT_TRNS_HSTR`). Both come from the JEX schema;
   on the shared dev DB they are present with 70 seed rows for `VN_VNM0004_000001`.
2. **Run the View Setting seed by hand** —
   [V249__sys_scr_option_daily_financial_hs.sql](../../../../../src/main/resources/db/migration/V249__sys_scr_option_daily_financial_hs.sql).
   Flyway is disabled (`ddl-auto=validate`); the `V249` prefix is file-organization only. The
   script is idempotent (`ON CONFLICT DO NOTHING`), touches no legacy `rept_0017_001` rows, and
   has no ordering dependency beyond the `SYS_SCR_OPTION_WECMS` table existing. **Until it runs**,
   `GET /screen-options/...` returns `WBE2001` and the frontend falls back to
   `DEFAULT_VIEW_SETTING_ITEMS` (all 12 columns visible; per-user persistence unavailable) —
   degraded, not broken.
3. **Menu/permission:** the H product tree must carry the `banking-reports-daily-financial-hs`
   node (navigation catalogue seed) and the user needs the grant (admins bypass the per-user ∩).
   F/M tenants see the CMC variant only.
4. **Templates ship inside the jar** (`src/main/resources/templates/reports/daily-financial-hs/`)
   — no filesystem dependency (unlike legacy, which reads `WECMS_STATIC/web/file/excel/VN/`).
5. No feature flags, no `application*.yml` keys — the feature is always on for authorized H users.

## 4. Localization

- **Backend:** one message key (`WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND`); Excel titles/headers
  are template-owned per locale (EN/KO/VI), dates formatted per locale in Java.
- **Frontend:** `report.daily_financial_hs.*` keys complete in `en/ko/vi.json` (bilingual column
  headers — the table shows KO + EN header lines like legacy). No `km.json` (Khmer not yet
  supported by `wecms-web`).
- **Template maintenance:** the three XLSX files are binary artifacts — edits happen in
  Excel/LibreOffice and are recommitted; `ExcelDownloadTemplateIntegrityTest`-style checks for
  this feature live in `DailyFinancialHsDownloadServiceTest` (per-locale title/date/merge
  assertions). Keep EN/VI title localization intact (PO-pinned).

## 5. Notes for future maintenance

- **Never add a second call site** for `fn_get_haengsung_daily_financial_report` — the repository
  is the documented single funnel.
- **Keep the ORDER BY intact** — dropping the leading currency CASE silently breaks TOTAL
  placement (regression fixed 2026-07-02; see final audit §4 fix 8).
- **Do not map more `REPT_TRNS_HSTR` columns** onto the entity without a PO decision — the
  partial mapping is the guard that the write path can never touch amounts/balances.
- Adding a 13th column = V-script seed row + frontend `COLUMN_REGISTRY` entry + template change +
  download-service populate change — all four in one MR.

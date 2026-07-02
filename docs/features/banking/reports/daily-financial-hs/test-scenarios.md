# Daily Financial (HS) — Test Scenarios

> SIT/UAT scenario catalogue. "Automated" cites the existing unit test; everything else is a
> manual scenario (the final audit's §8 manual-QA items are folded in). Frontend automated tests
> are **ABSENT** — all FE rows are manual until a FE test harness lands.

## 1. Search / read

| # | Scenario | Expected | Automated |
|---|---|---|---|
| S-1 | Open the page (H tenant, granted user) | Report loads eagerly for today + ALL accounts; rows grouped per bank; VND section first | — |
| S-2 | Blank `asOf` / blank `accountFilter` on the API | Backend defaults to today / `ALL` | ✅ `ReportServiceDailyFinancialHsTest` |
| S-3 | Change date + bank selection **without** clicking Search | No refetch (state-only; legacy parity) | — |
| S-4 | Click Search with a past date | Report reloads for that date; `as_of_dt` echoes it | — |
| S-5 | Bank multi-select → subset of accounts | `accountFilter` = JSON `[{BANK_CD, ACCT_NO}]`; only those accounts render | — |
| S-6 | Row order | Per account: BEFORE_BALANCE → TRANSACTION× → SUB TOTAL; each currency closes with TOTAL; VND → USD → KRW | ✅ order preserved (`ReportServiceDailyFinancialHsTest`) |
| S-7 | Null amounts | Blank cells, never "0" | ✅ null preservation asserted |

## 2. Validation

| # | Scenario | Expected |
|---|---|---|
| V-1 | Save body field over max length (e.g. `memo` > 500) | HTTP 200 `WBE_VALIDATION` |
| V-2 | `asOf` not `yyyyMMdd` (e.g. `2026-07-02`) | DB function raises → HTTP 500 `WBE9999` (no Java-side guard — known behaviour, see [functional-specification.md §5](functional-specification.md#5-validation)) |
| V-3 | Malformed `accountFilter` JSON | Function-side error → `WBE9999` |

## 3. Editable save

| # | Scenario | Expected | Automated |
|---|---|---|---|
| E-1 | Edit Receipt Code on a TRANSACTION row, blur | `PATCH /entries/{acctSeqNo}` fires once; only `REPT_CD` changes; `MOD_ID`/`MOD_DTM` stamped | ✅ `DailyFinancialHsWriteServiceTest` |
| E-2 | Press Enter instead of blur | Same single save | — (FE) |
| E-3 | Blur without changing the value | No request (change-tracked) | — (FE) |
| E-4 | Clear a field (empty string) | Column cleared in DB (`""` semantics) | ✅ |
| E-5 | Omitted fields (`null`) | Untouched in DB | ✅ |
| E-6 | Save against unknown `acctSeqNo` (or another tenant's row) | `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND`; nothing saved; error dialog on FE | ✅ throw + no-save asserted |
| E-7 | BEFORE_BALANCE row | No input boxes render; nothing savable (`acct_seq_no=""`) | — (FE; fix 3) |
| E-8 | Edit → reload (re-Search) | Persisted value returns from the DB; subtotals reflect nothing (codes/descriptions don't affect amounts) | manual QA item 1 |
| E-9 | Value containing non-breaking spaces | NBSP stripped before store (sanitize port) | — |

## 4. Currency

| # | Scenario | Expected |
|---|---|---|
| C-1 | Tenant with VND + USD + KRW accounts | Three currency sections, each closed by its own green TOTAL immediately after its last account (not all TOTALs at report end) — regression pinned by fix 8 |
| C-2 | KRW data present | KRW **included** (intentional divergence from legacy's VND/USD filter) |
| C-3 | TOTAL row values | `before_balance/receipt/payment/balance` under their columns; `balance_company_actual` in the Memo column |

## 5. Excel download

| # | Scenario | Expected | Automated |
|---|---|---|---|
| X-1 | Download with `lang=EN/KO/VI` | Filename `WeCMS_Daily_Financial_Report_{LANG}.xlsx`; A1 title per locale (EN `Daily Financial Report`, KO `일 일 자 금 수 지 현 황`, VI `Báo cáo tài chính hằng ngày`); A2 + L3 dates per locale | ✅ `DailyFinancialHsDownloadServiceTest` |
| X-2 | Multi-currency report (≥2 trailing CURRENCY_TOTAL rows) | No POI merge-overlap 500; bank/ccy merges bounded to each account's SUB TOTAL | ✅ regression case |
| X-3 | Workbook content | 12 columns always (View Setting ignored); SUB TOTAL cyan C:F merge; TOTAL grey A:F merge; VND/KRW `#,##0`, USD `#,##0.00`; numeric cells re-summable | ✅ population + numeric asserted |
| X-4 | Download after saving an edit (no re-Search) | Saved value **is** in the workbook (backend re-reads the DB) | manual QA item 1 |
| X-5 | Empty report (no rows for the date/filter) | Template-only workbook, no exception | ✅ `emptyReportDoesNotThrow` |
| X-6 | Human open in real Excel (EN/KO/VI) | Fonts render, merges look right, Vietnamese diacritics display | manual QA item 2 |

## 6. Empty & large data

| # | Scenario | Expected |
|---|---|---|
| D-1 | Date with no transactions | `rows: []` → FE empty state; download still works (X-5) |
| D-2 | Account with 0 transactions | HEADER + BEFORE_BALANCE + SUB TOTAL only (no input boxes) |
| D-3 | Large day (hundreds of transactions) | Single unpaginated response (no pagination by design); report viewport scrolls; **no row cap exists** — watch response size in SIT if HAENGSUNG volumes grow |

## 7. Permissions

| # | Scenario | Expected |
|---|---|---|
| P-1 | No JWT / revoked JWT | 401 `WBE1009` on all 3 endpoints |
| P-2 | Authenticated F/M-tenant user calls any HS endpoint directly | 403 `WBE1003` (`@menuAuthz` — permission cd only in the H tree) |
| P-3 | H tenant, user without the menu grant (non-admin) | 403 `WBE1003`; menu node absent from `/navigation` |
| P-4 | Cross-tenant save attempt (valid `acctSeqNo` of another BIZ_KEY) | `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND` (composite-PK scope) — same envelope as not-found |

## 8. View Setting

| # | Scenario | Expected |
|---|---|---|
| VS-1 | Hide a column, Save, reload | Persisted per user (`USER_SCR_OPTION_WECMS`); every row (incl. SUB TOTAL / TOTAL) renders one cell per visible column, alignment intact (fix 10) |
| VS-2 | Reorder columns | Table order follows `disp_ord`; totals stay under their columns |
| VS-3 | Reset | System defaults return (12 visible) |
| VS-4 | Unseeded environment (V249 not yet run) | `WBE2001` from screen-options; FE falls back to `DEFAULT_VIEW_SETTING_ITEMS` — degraded (no persistence), not broken |
| VS-5 | Download with columns hidden | Excel still has all 12 columns (by design) |

## 9. Regression pins

- Currency-first ORDER BY (TOTAL placement) — repository query + fix 8.
- Merge-overlap on multi-currency download — `DailyFinancialHsDownloadServiceTest` regression case.
- Template titles (EN/VI localized, KO legacy) — `templateTitleAndDateSlots_perLocale`; **do not
  revert titles to Korean**.
- No spacer rows — tbody `<tr>` count `ΣTᵢ + 2A + C` (fix 11).
- Full suite `./gradlew test` green as of 2026-07-02 (incl. `EndpointAuthorizationCoverageTest`,
  which fails the build if an HS handler loses its `@menuAuthz` annotation).

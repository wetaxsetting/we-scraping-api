# WeInvoice Invoice Screens v1.2 (API) — Test Result

**Date:** 2026-11-27
**Type:** TEST RESULT
**Status:** PASS
**Repository:** we-scraping-api
**Related Record:** [WeInvoice — Invoice Screens per UI Spec v1.2 (parts A–D) — API](../../features/addon-service/weinvoice/weinvoice-invoice-screen-v1-2-2026-11-27.md)

## Scope

List row extras (fee, trade discount, PO, due date, items, tax rates, related), accent-insensitive / item / PO search,
totals per currency, detail referenced-by + value still in force, Excel and CQT-list columns.

## Environment

LOCAL (Windows, JDK 21 from `~/.gradle/jdks`, `--offline`); read-only connection to the shared engine DB for the SQL check.

## Code / Build Under Test

Branch: `sit` · Base commit: `e7ae224` · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `InvoiceExtraSqlTest` (new) | PASS | 6 tests — search pattern (accents, case, `đ`, regex escaping), PO / due / fee SQL, related key, due-date parsing, detail fee + value still in force |
| `InvoiceWorkbookTest` | PASS | layout updated (25 summary columns, no "₫"); new `excel_v12_feeLineForeignCurrencyAndPo` (USD decimals `#,##0.00`, fee line with VAT 0, PO) |
| `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` | PASS | 244 tests, 0 failures |
| `./gradlew test --offline` | PASS except 1 pre-existing | 3,045 run, 1 failed, 22 skipped |

## Database Verification (read-only, temporary test removed)

| Check | Result | Evidence |
|---|---|---|
| Totals bar per currency | PASS | 3700860461 purchase Oct–Nov 2026: VND 3,901 · KRW 6 (→ 6,573,600 VND) · USD 41 (→ 24,254,410 VND) |
| Fee / PO / items / tax rates on rows | PASS | KPI fee 3,647,699 VND; `PO-119963`; items preview + count; rates `8%`, `KCT`, `KKKNT` |
| Related both ways | PASS | 327226 ← 348508 (adjusts, −7,024,500); 303466 ← 303467, 303468; 398715 → 398709 (replaces) |
| Search | PASS | "mi an lien" → 641 invoices (3700860461), "PO-" → 2, "4500" (1201496252, 2023–2026) → 11 |
| Timing | PASS | list 0.1–0.5 s; with keyword ≤ 1.7 s (list + KPIs over 11 months) |

## Regression Tests

Not a bug fix. The changed SQL (keyword, KPIs) is pinned by `InvoiceListServerPagingTest` / `InvoiceRowLookupMappingTest`
(unchanged, passing) and the new `InvoiceExtraSqlTest`.

## Existing Failures

`VatDeclarationLifecycleTest > create — required fields > sync accepts a period whose adjustments exceed its revenue` —
fails on the baseline too (recorded 2026-11-25).

## Not Tested

- The web screens against this API (web side has its own test-result record) — no browser run yet.
- SIT: not deployed.

## Final Result

PASS

## Recommendation

READY FOR MANUAL COMMIT

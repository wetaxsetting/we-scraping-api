# Test Result: WeInvoice Invoice Downloads (UI Spec v1.1)

**Date:** 2026-11-25
**Implementation record:** [weinvoice-invoice-downloads-v1-1-2026-11-25.md](../../features/addon-service/weinvoice/weinvoice-invoice-downloads-v1-1-2026-11-25.md)
**Final Result:** PASS (unit / MockMvc) · manual and DB not run

## Scope

Excel 3 sheets (§3.11), Bảng kê gốc ZIP, bulk PDF job (§3.12), export scope by filter (§3.6).

## Environment

Local Windows, JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`), `./gradlew test --offline`. No PostgreSQL / engine used.

## Code / Build Under Test

Working tree of `sit` (uncommitted), including unrelated uncommitted work already present (unlimited tax codes 2026-11-16).

## Automated Tests

- Full suite: 3 026 tests, 1 failed, 21 skipped.
- `com.kosign.wecms.domain.weinvoice.*`: 229 tests, 0 failures.
- New: `PdfPackBuilderTest` 12/12, `InvoiceWorkbookTest` 4/4, `PdfPackJobServiceTest` 2/2.

## Regression Tests

`WeInvoiceInvoiceControllerTest` 3/3, `WeInvoiceBeanConstructionTest` pass.

## Existing Failures

`VatDeclarationLifecycleTest` › "sync accepts a period whose adjustments exceed its revenue" — fails on clean `origin/sit` too.

## New Failures

None.

## Not Tested

SQL of `PackRepository` against PostgreSQL, engine `FETCH_XML` / `FETCH_PDF` round trip, real provider PDFs, large periods (performance).

## Recommendation

Run on SIT with a real MST before release: each of the 6 PDF modes, Excel with / without selection, Bảng kê gốc of a month with cash-register invoices.

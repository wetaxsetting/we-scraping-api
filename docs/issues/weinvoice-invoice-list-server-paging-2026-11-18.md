# WeInvoice: Server-Side Invoice List Filters and KPIs for Page-at-a-Time Loading

**Date:** 2026-11-18
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** READY FOR MANUAL COMMIT
**Approval Status:** NOT REQUIRED (developer request 2026-11-18 — load the invoice list page by page, newest first)
**Implementation Status:** COMPLETE · **Test Status:** PASS (unit + read-only SQL)
**Repository:** wecms-api (`we-scraping-api`)
**Frontend record:** `we-scraping-web/docs/issues/weinvoice-invoice-list-server-paging-2026-11-18.md` (web repository)

## Problem

The web downloads the whole year of invoices and filters in the browser (slow for large MSTs). Moving to
`GET /invoices` per page needs the server to support every rule the list screen applies today.

## Current Behavior (gaps)

- No "missing XML" mode matching the list card (every invoice without XML, replaced / cancelled left out) or the
  dashboard's coded rows; `pdf=missing` keeps replaced / cancelled.
- `kpis.missing_xml` = coded only, `missing_pdf` and the missing counts keep replaced / cancelled; `sales_count` /
  `purchase_count` follow the direction filter, while the card's "Cả kỳ" line needs both directions of the period.
- Provider filter has no "needs a lookup code" value; facets have no partner list ("Loại trừ MST") and no needs-code count.

## Expected Behavior (additive)

- `missing_xml=all|coded` filter (list, ids, export filter): no XML, not replaced / cancelled (+ symbol not `K` for coded).
- `pdf=missing` leaves replaced / cancelled out (same rule as the web card and the dashboard).
- `provider=needs_code`: provider known, no lookup code / link in the XML and none typed by the user.
- `kpis`: `missing_xml_all` (card), missing counts without replaced / cancelled, `period_sales_count` /
  `period_purchase_count` (MST + period, both directions, replaced / cancelled left out).
- `facets`: `partners[]` (top 500 by count) and `needs_code` count.

## Scope

`InvoiceFilter`, `InvoiceCriteria`, `InvoiceQueryService`, `InvoiceQueryRepository`, `InvoiceKpis`, `InvoiceFacets`,
controller params, tests, interface specification.

## Database Impact

NONE (read-only SQL).

## Implementation

- `InvoiceQueryService`: `xml` accepts `missing_all` / `missing_coded`; `provider` accepts `needs_code`; `list` adds
  the period counts (`InvoiceQueryRepository.periodCounts`, both directions, replaced / cancelled left out).
- `InvoiceQueryRepository.filterWhere`: `missing_all` / `missing_coded` (replaced / cancelled left out, coded = symbol not
  `K`); `pdf=missing` leaves replaced / cancelled out; `needs_code` (`NEEDS_CODE`, same rule as the row flag).
- `kpis`: `missing_xml`, `no_code_missing_xml`, `missing_pdf` leave replaced / cancelled out; new `missing_xml_all`.
- `facets`: `needs_code` count and `partners` (500 most frequent, sorted by name).
- `InvoiceKpis` (+ `missingXmlAll`, `periodSalesCount`, `periodPurchaseCount`, `withPeriodCounts`), `InvoiceFacets`
  (+ `partners`, `needsCode`), controller `GET /invoices` binds `ids`; `InvoiceFilter` docs; interface specification.

## Files Changed

`service/InvoiceQueryService.java`, `repository/InvoiceQueryRepository.java`, `dto/InvoiceKpis.java`, `dto/InvoiceFacets.java`,
`dto/InvoiceFilter.java`, `controller/WeInvoiceInvoiceController.java`, tests `repository/InvoiceListServerPagingTest.java`
(new), `controller/WeInvoiceInvoiceControllerTest.java`, `docs/features/addon-service/weinvoice/interface-specification.md`,
this record, the test-result record.

## Verification

- `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` — PASS (27 classes, 208 tests).
- New SQL run read-only on the engine DB — valid, 0.01–0.28 s on the largest MST.

## Test Evidence

Test result: [WeInvoice Invoice List Server Paging (API) Test Result](../testing/results/weinvoice-invoice-list-server-paging-api-test-result-2026-11-18.md)

Final test result: PASS

## Known Limitations / Follow-up

- Behaviour change for other `GET /invoices` / export callers: `pdf=missing` and the KPI missing counts now leave
  replaced / cancelled invoices out (same rule as the dashboard and the web).
- Each list request runs the CTE for the list, KPIs and period counts; measured fast on the largest MST, but no index
  work was done.

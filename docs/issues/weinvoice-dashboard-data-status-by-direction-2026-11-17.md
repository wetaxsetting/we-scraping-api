# WeInvoice Dashboard: Missing XML / PDF Counted per Direction

**Date:** 2026-11-17
**Type:** ISSUE
**Current Work Classification:** ISSUE (behaviour change decided by the developer after the audit)
**Status:** READY FOR MANUAL COMMIT
**Approval Status:** APPROVED (developer, 2026-11-17: "Tách mua/bán")
**Implementation Status:** COMPLETE · **Test Status:** PASS (unit)
**Repository:** wecms-api (`we-scraping-api`)
**Audit (evidence):** [weinvoice-dashboard-missing-xml-pdf-summary-audit-2026-11-17](../audits/modules/weinvoice-dashboard-missing-xml-pdf-summary-audit-2026-11-17.md)
**Frontend record:** `we-scraping-web/docs/issues/weinvoice-dashboard-data-status-by-direction-2026-11-17.md` (web repository)

## Problem

`GET /dashboard` `data_status.missing_xml` / `missing_pdf` add purchases and sales together, while the dashboard row links
to the purchase list only (audit findings 2–3): e.g. 0318793907 shows 60,232 missing XML, all sales, and the link shows
nothing.

## Expected Behavior

`data_status` also carries the two counts per direction, so the web can show one row per direction, each linking to its
own list. Existing fields unchanged (additive).

## Scope

`DashboardRepository.dataStatus`, `DashboardResponse.DataStatus`, `DashboardService`, tests, interface specification.

## Out of Scope

Web rendering (frontend record); `no_code_no_xml` (stays one figure, links to email).

## Proposed Implementation

Add `missing_xml_purchase`, `missing_xml_sales`, `missing_pdf_purchase`, `missing_pdf_sales` (same rules as the totals,
split by `direction`).

## Database Impact

NONE (read-only query change).

## Implementation

`DashboardRepository.dataStatus` counts `missing_xml_purchase/sales` and `missing_pdf_purchase/sales` with the same rules as
the totals (coded = symbol not `K…`, replaced / cancelled left out), split by `direction`; `DataStatusRow`,
`DashboardResponse.DataStatus` and `DashboardService` carry them. Totals unchanged.

## Files Changed

`repository/DashboardRepository.java`, `dto/DashboardResponse.java`, `service/DashboardService.java`, test
`repository/DashboardRepositoryDataStatusTest.java` (new), `docs/features/addon-service/weinvoice/interface-specification.md`,
this record, the test-result record.

## Verification

- `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` — PASS (26 classes, 204 tests).
- `bootRun` + real `GET /dashboard`: NOT RUN (backend not running on this machine).

## Test Evidence

Test result: [WeInvoice Dashboard Data Status per Direction (API) Test Result](../testing/results/weinvoice-dashboard-data-status-by-direction-api-test-result-2026-11-17.md)

Final test result: PASS

## Known Limitations / Follow-up

Needs a backend restart / deploy; until then the web shows the totals on the purchase row (fallback).

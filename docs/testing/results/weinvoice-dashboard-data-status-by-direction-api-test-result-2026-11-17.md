# WeInvoice Dashboard Data Status per Direction (API) — Test Result

**Date:** 2026-11-17
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api (`we-scraping-api`)
**Related Record:** [weinvoice-dashboard-data-status-by-direction-2026-11-17](../../issues/weinvoice-dashboard-data-status-by-direction-2026-11-17.md)
**Web result:** `we-scraping-web/docs/testing/results/weinvoice-dashboard-data-status-by-direction-web-test-result-2026-11-17.md` (web repository)

## Scope

`GET /dashboard` `data_status` gains `missing_xml_purchase/sales`, `missing_pdf_purchase/sales` (additive).

## Environment

LOCAL (Windows laptop), JDK 21 (Gradle toolchain), `./gradlew test --offline`. No database, no running server.

## Code / Build Under Test

Branch: working tree of `we-scraping-api` · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `DashboardRepositoryDataStatusTest.dataStatusSplitsMissingXmlAndPdfByDirection_sameRulesAsTheTotals` — SQL text (direction filters, coded rule, 4/6 excluded) + row mapping | PASS | |
| `DashboardRepositoryDataStatusTest.serviceCarriesThePerDirectionCountsIntoTheResponse` | PASS | |
| `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` | PASS | 26 classes, 204 tests, 0 failures, 0 errors |

## Database Verification

Read-only SQL on the engine DB during the audit (2026-11-17): the per-direction split of 3603379922 for 2026 was 56 purchase /
227 sales coded invoices without XML (total 283) — the figures the web test uses.

## Existing Failures

Not run: the full suite (13 known failures without `application-local.yml`, see CLAUDE.md); only the WeInvoice package.

## New Failures

None.

## Not Tested

`bootRun` + a real `GET /dashboard` call: **NOT RUN** — backend not running on this machine.

## Final Result

PASS

## Recommendation

READY FOR MANUAL COMMIT

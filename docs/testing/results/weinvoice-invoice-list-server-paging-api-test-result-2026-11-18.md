# WeInvoice Invoice List Server Paging (API) — Test Result

**Date:** 2026-11-18
**Type:** TEST RESULT
**Status:** PASS
**Repository:** wecms-api (`we-scraping-api`)
**Related Record:** [weinvoice-invoice-list-server-paging-2026-11-18](../../issues/weinvoice-invoice-list-server-paging-2026-11-18.md)
**Web result:** `we-scraping-web/docs/testing/results/weinvoice-invoice-list-server-paging-web-test-result-2026-11-18.md` (web repository)

## Scope

`xml=missing_all|missing_coded`, `pdf=missing` without replaced / cancelled, `provider=needs_code`, `ids` on
`GET /invoices`, KPI fields `missing_xml_all` / `period_*_count`, facets `partners` / `needs_code`.

## Environment

LOCAL (Windows laptop), JDK 21, `./gradlew test --offline`; read-only SQL on the shared engine DB (192.168.60.121).

## Code / Build Under Test

Branch: `sit` (working tree, on top of `010c8c9`) · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `InvoiceListServerPagingTest` (new): missing-XML modes / missing PDF fragments; `needs_code` fragment; facets SQL + mapping (partners by name, needs-code count, partner limit); service fills period counts, accepts the new values, rejects others | PASS | 4/4 |
| `WeInvoiceInvoiceControllerTest.listBindsSnakeCaseParamsAndAnswersInTheEnvelope` (+ `ids` binding, new KPI fields in JSON) | PASS | |
| `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` | PASS | 27 classes, 208 tests, 0 failures |

## Database Verification

The generated SQL (filter with `missing_all` + `pdf=missing` + `needs_code`, and the facets' `needs_code` + `partner`
parts) was dumped by a throwaway test (deleted) and run read-only on the engine DB: valid PostgreSQL;
1201496252 purchases 2026 — filter 0.01 s, facets 0.05 s (needs_code 133, 105 partners);
0318793907 sales 2026 — filter 0.08 s, facets 0.28 s (needs_code 2,837, 413 partners).

## Existing Failures

Full suite not run (13 known failures without `application-local.yml`); only the WeInvoice package.

## New Failures

None.

## Not Tested

`bootRun` + real HTTP: NOT RUN (backend not running locally). `periodCounts` SQL ran only through its unit wiring.

## Final Result

PASS

## Recommendation

READY FOR MANUAL COMMIT

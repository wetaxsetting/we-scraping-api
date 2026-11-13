# WeInvoice: Invoice List Row Carries the Lookup Code

**Date:** 2026-11-11
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** READY FOR MANUAL COMMIT
**Investigation Status:** COMPLETE · **Approval Status:** APPROVED (developer, 2026-11-11: "Duyệt, sửa cả hai") · **Implementation Status:** COMPLETE · **Test Status:** PASS (unit)
**Repository:** wecms-api (`we-scraping-api`)
**Evidence:** web audit `we-scraping-web/docs/audits/weinvoice-provider-badge-warning-audit-2026-11-11.md`

## Problem

`InvoiceRow` (`GET /invoices`) carries `needs_lookup_code` but not the code itself, so the list's provider badge
("↗") cannot open the original invoice directly — the web only learns the code after the detail is loaded.

## Expected Behavior

`InvoiceRow` adds `lookup_code`, `lookup_code_company` and `lookup_url`, resolved exactly as the detail's `lookup`
block: a hand-typed code / link (`winv_lkup_cd`) wins over the code / link guessed from the XML
(`invoice_xml.ma_tra_cuu` / `lookup_url`).

## Scope

`dto/InvoiceRow.java`, `repository/InvoiceQueryRepository.java` (list query + row mapper), contract note, tests.

## Out of Scope

Detail endpoint (already has `lookup`); export.

## Database Impact

NONE — reads existing columns.

## API Impact

`GET /invoices` rows: three new nullable fields `lookup_code`, `lookup_code_company`, `lookup_url`. Additive.

## Implementation

- `InvoiceRow`: `lookupCode`, `lookupCodeCompany`, `lookupUrl` (after `needsLookupCode`).
- `InvoiceQueryRepository`: list query also selects `lc.lkup_cd`, `lc.cmpn_cd`, `lc.lkup_url`; `ROW_MAPPER` (now
  package-private for the test) resolves `manual ? hand-typed : XML` exactly like `InvoiceQueryService.lookup`; blank XML
  values become `null`.
- Contract note in `interface-specification.md`.

## Files Changed

`dto/InvoiceRow.java`, `repository/InvoiceQueryRepository.java`, `InvoiceRowLookupMappingTest.java` (new),
`interface-specification.md`, this record, the test-result record.

## Verification

### Automated Tests

- `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` — PASS, 114 tests, 0 failures
  (`InvoiceRowLookupMappingTest` 3: XML code carried + no warning · hand-typed code and company win · no code → warning).

### Regression Review

- `InvoiceRow` is built only by this mapper; `PartnerEInvoiceService` compiles unchanged. Old web clients ignore the new
  fields.

### Manual Verification

- .13 — NOT RUN (needs this build deployed).

## Test Evidence

[WeInvoice List Row Lookup Code — Test Result](../testing/results/weinvoice-list-row-lookup-code-test-result-2026-11-11.md)
— PASS (unit), regression coverage YES.

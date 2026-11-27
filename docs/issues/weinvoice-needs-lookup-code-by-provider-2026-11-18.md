# WeInvoice: "Needs a Lookup Code" Flag by Provider

**Date:** 2026-11-18
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** READY FOR MANUAL COMMIT
**Approval Status:** NOT REQUIRED (developer request 2026-11-18: "sửa luôn cờ cần mã tra cứu theo NCC")
**Implementation Status:** COMPLETE · **Test Status:** PASS (unit + read-only count)
**Repository:** wecms-api (`we-scraping-api`)
**Frontend record:** `we-scraping-web/docs/issues/weinvoice-needs-lookup-code-by-provider-2026-11-18.md` (web repository)

## Problem

The "!" pill / provider filter "Cần mã tra cứu" (`InvoiceRow.needs_lookup_code`, `InvoiceDetail.lookup.needs_code`,
`provider=needs_code`, facets `needs_code`) = provider in the catalogue AND no lookup code / link (XML or typed). After the
engine fix of 18/11 (codes read from TTKhac by meaning) 8,550 invoices still carried it; read-only DB check:

| Group | Invoices | Needs a code? |
|---|---:|---|
| Original PDF already stored (BKAV 3,808, Viettel, Vĩnh Hy, VININVOICE, …) | 4,327 | No — nothing left to fetch |
| Provider looks up without a code: BKAV (DLHDon/@Id), Vĩnh Hy / Minh Khang (DLHDon/@Id), My Software / VININVOICE (MCCQT), Mắt Bão (number) | (all have PDF today) | No |
| WeTax own sales, FPT (code only in mail), VNPT without a seller portal, TS24, EFY, adapter answered `no_code` | ~4,200 | Yes — a typed code / link is what unblocks them |

## Expected Behavior

needs code = provider known AND no code / link (XML or typed) AND **no original PDF yet** AND the provider is **not** one that
looks up without a code.

## Scope

`InvoiceQueryRepository` (row flag, `NEEDS_CODE` SQL), `InvoiceQueryService` (detail `lookup.needs_code`), a provider rule
constant, tests, interface specification.

## Database Impact

NONE (no column added — the provider list is a code constant).

## Implementation

- `common/ProviderLookup` (new): `WITHOUT_CODE` (BKAV 0101360697, Vĩnh Hy 0314743623, Minh Khang 0314209362,
  My Software 0108971656, VININVOICE 0109282176, Mắt Bão 0302712571), `WITHOUT_CODE_SQL`, `needsCode(providerMst, known,
  hasCode, hasPdf)`.
- `InvoiceQueryRepository`: row flag uses `needsCode(…, has_pdf)`; `NEEDS_CODE` SQL adds `NOT c.has_pdf` and the provider
  exclusion (filter `provider=needs_code`, facet `needs_code`).
- `InvoiceQueryService.detail`: `lookup.needs_code` from the same rule; "has PDF" = a `pdf` file in the detail's file list
  (computed once).
- Interface specification: rule documented.

## Files Changed

`common/ProviderLookup.java` (new), `repository/InvoiceQueryRepository.java`, `service/InvoiceQueryService.java`, tests
`common/ProviderLookupTest.java` (new), `repository/InvoiceQueryRepositoryTestAccess.java` (new, test helper),
`repository/InvoiceRowLookupMappingTest.java`, `docs/features/addon-service/weinvoice/interface-specification.md`, this record,
the test-result record.

## Verification

- `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` — PASS (28 classes, 211 tests).
- Read-only count on the engine DB: 8,550 → ~4,200 flagged.

## Test Evidence

Test result: [WeInvoice Needs Lookup Code by Provider (API) Test Result](../testing/results/weinvoice-needs-lookup-code-by-provider-test-result-2026-11-18.md)

Final test result: PASS · Regression coverage: PASS

## Known Limitations / Follow-up

- The provider list is a code constant (no catalogue column); a new provider looked up without a code needs a code change.
- Remaining flags are real needs: WeTax own sales, FPT (code only in mail), VNPT invoices without a seller portal, TS24, EFY.

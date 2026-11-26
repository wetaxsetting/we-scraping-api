# WeInvoice — "Phát hành qua" Empty for Cash-Register Invoices — Fix

**Date:** 2026-11-26
**Type:** BUG FIX
**Current Work Classification:** BUG
**Status:** READY FOR MANUAL COMMIT
**Repository:** we-scraping-api
**Approval Status:** APPROVED (user, 2026-11-26: "Sửa đi" on the audit's recommendation)
**Implementation Status:** COMPLETE
**Test Status:** PASS (1 pre-existing unrelated failure in the full suite)

Evidence: [audit](../audits/modules/weinvoice-provider-empty-for-cash-register-invoices-audit-2026-11-26.md).

## Problem

The invoice list / detail / PDF pack derive the provider only from `invoice_xml.mst_tcgp` / `list_raw.msttcgp`, which
cash-register invoices never carry → "Phát hành qua" shows "—" for 64,938 invoices although the engine knows the provider.

## Scope

- New `common/ProviderSql`: provider = XML header / GDT list → TTKhac `MSTTCGP` → provider of the adapter that fetched
  the PDF → seller's usual provider (per-query CTE `sprv`).
- `InvoiceQueryRepository` (list, KPIs, facets, filters, ids), `InvoiceDetailRepository` + `InvoiceQueryService` (detail),
  `PackRepository` (PDF pack) use it.
- "Needs a lookup code" (`ProviderLookup.needsCode`, `NEEDS_CODE`) keeps using the provider written on the invoice only, so
  the "!" pill and its counts do not change.

## Out of Scope

Engine writes; an "inferred" marker on the web (not requested); lookup-link-host → provider mapping (66 invoices).

## Database Impact

NONE — read-side SQL only.

## Root Cause

Verified in the audit: provider is read only from `invoice_xml.mst_tcgp` / `list_raw.msttcgp`; cash-register XML (QĐ 1233)
has no `<MSTTCGP>` and GDT's MTT list has no `msttcgp`.

## Implementation

`ProviderSql.effective(sellerUsual)` = `COALESCE` of, in order:

1. `XML_PROVIDER` — XML header `<MSTTCGP>`, else GDT list `msttcgp` (unchanged source of truth);
2. TTKhac `MSTTCGP` when it is a 10 / 10-3 digit tax code (WeTax MTT invoices);
3. `PDF_ADAPTER_PROVIDER` — the provider of the engine adapter that fetched the original PDF (`invoice_pdf.source_id`,
   `email:` prefix stripped) via `einvoice_providers.source_id`, plus an inline `ADAPTER_PROVIDERS` map for adapters that
   never appear as an MSTTCGP and so have no `einvoice_providers` row (ipos, sapo, vietinvoice, wetax→1201496252, mmhoadon,
   thoadon, sonphat, gohoadon). Parent MST first when several provider MSTs share one adapter;
4. the seller's usual provider (parent MST, most frequent `XML_PROVIDER` on its other invoices): CTE `sprv` once per list /
   pack query, correlated subquery `SELLER_USUAL_ONE` for the detail.

- List (`InvoiceQueryRepository`): `prvd_mst` = effective provider (column, facets, `provider=` filter, KPIs);
  new `prvd_mst_xml` = provider on the invoice, used by `NEEDS_CODE` and the row's `needs_lookup_code`.
- Detail (`InvoiceDetailRepository` → `DetailRow.effectiveProviderMst`, `InvoiceQueryService`): shown provider falls back to
  the effective one; `lookup.needs_code` still from the provider on the invoice.
- PDF pack (`PackRepository.packChunk`): `providerMst` / `providerName` from the effective provider.

Initial plan vs actual: the adapter map (step 3 second half) was added after the DB check showed iPOS invoices (row 171)
still empty — `einvoice_providers` had no `ipos` row. Engine tables are not written (API is read-only on them).

## Files Changed

- `src/main/java/com/kosign/wecms/domain/weinvoice/common/ProviderSql.java` (new)
- `src/main/java/com/kosign/wecms/domain/weinvoice/repository/InvoiceQueryRepository.java`
- `src/main/java/com/kosign/wecms/domain/weinvoice/repository/InvoiceDetailRepository.java`
- `src/main/java/com/kosign/wecms/domain/weinvoice/repository/PackRepository.java`
- `src/main/java/com/kosign/wecms/domain/weinvoice/service/InvoiceQueryService.java`
- tests: `common/ProviderSqlTest` (new), `InvoiceRowLookupMappingTest`, `InvoiceListServerPagingTest`, `ProviderLookupTest`,
  `InvoiceFileServiceTest`
- docs: this record, the audit, [interface-specification.md](../features/addon-service/weinvoice/interface-specification.md),
  [test-scenarios.md](../features/addon-service/weinvoice/test-scenarios.md) (I7)

## API Impact

No contract change. `InvoiceRow.provider*`, detail `provider*`, facet `providers[]`, `provider=` filter and the PDF pack /
failed-invoice Excel now carry the inferred provider when the invoice has none. `needs_lookup_code` / `provider=needs_code`
unchanged.

## UI Impact

"Phát hành qua" column, provider filter and detail show a provider for cash-register invoices. No web change needed.

## Integration Impact

None. Engine tables read only.

## Verification

### Automated Tests

- `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` — PASS (236 tests).
- `./gradlew test --offline` — 3,037 run, 1 failed (pre-existing `VatDeclarationLifecycleTest`, fails on the baseline too),
  21 skipped.

### Regression Test

Test file: `src/test/java/com/kosign/wecms/domain/weinvoice/common/ProviderSqlTest.java` and
`repository/InvoiceRowLookupMappingTest.inferredProviderIsShownButNeverAsksForACode`.
Behavior protected: provider order XML → TTKhac → PDF adapter (incl. the inline map) → seller's usual; an inferred provider is
shown but never raises "needs a lookup code". The DB-side behaviour is verified read-only (below); there is no embedded DB.

### Manual Verification (read-only, shared engine DB, 2026-11-26)

- List SQL (dumped from `InvoiceQueryRepository.cte`): 1201496252 from 2023-01-01 — 10,450 rows, provider on invoice 7,619 →
  effective 9,582 (0.16 s); 0318793907 — 64,189 rows, 3,884 → 64,174 (0.22 s).
- Detail SQL: 162 → 0101243150 (MISA), 171 → 0104789847 (iPOS, was empty), 644 / 645 → 1201496252 (WeTax),
  1199 → 0105987432 (EasyInvoice).
- PDFs with no provider still: source `email` (plain mail attachment, 234) and `express247` (8, 247Express own portal, no
  provider MST).

## Test Evidence

Test result:
[WeInvoice Provider for Cash-Register Invoices — Test Result](../testing/results/weinvoice-provider-empty-for-cash-register-invoices-test-result-2026-11-26.md)

Final test result: PASS · Regression coverage: PASS

## Result

Code + tests + docs complete. READY FOR MANUAL COMMIT; not deployed (SIT unchecked).

## Known Limitations / Follow-up

- Invoices whose seller never issued an invoice with an MSTTCGP and whose PDF came from a plain mail attachment stay "—".
- Inferred provider has no marker on the web ("suy ra"); not requested.
- New engine adapter without an `einvoice_providers` row → add it to `ProviderSql.ADAPTER_PROVIDERS`.

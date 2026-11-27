# WeInvoice VAT Declaration on Engine Invoices

**Date:** 2026-11-25
**Type:** FEATURE
**Current Work Classification:** FEATURE
**Status:** READY FOR MANUAL COMMIT
**Repository:** we-scraping-api
**Approval Status:** NOT REQUIRED (user, 2026-11-25: "đem module kê khai cũ sang dùng hoá đơn engine đi")
**Implementation Status:** COMPLETE
**Test Status:** PASS (unit + engine SQL read-only) · end-to-end NOT RUN

Frontend counterpart: we-scraping-web
`docs/features/weinvoice/weinvoice-vat-declaration-engine-invoices-2026-11-25.md` (separate repository).

## Purpose

WeInvoice has no VAT return yet: the web route `/tax/vat-return` is a "coming soon" page and the WeInvoice module
(`domain/weinvoice`) has no declaration endpoint. The old WeCMS module `domain/vatdeclaration`
(`/api/v1/addon/vat-declarations`: create / edit / lock a period, indicators [21]–[43], 8% annex, HTKK XML, PDF,
Excel) exists but aggregates invoices from the WeCMS tables (`BUSA_DOC_MSTR`, `BUSA_DOC_DETL`, `EINV_TAX_DETL`).
WeInvoice invoices live in the engine tables (`invoices`, `invoice_details`, `invoice_xml`) reached through the
`WeInvoiceDb` pool, so the old module sees none of them.

## Requirement

Reuse the old VAT declaration module, but take its invoices from the engine (the invoices WeInvoice already shows),
for the MST the user works on.

## Current System Behavior (verified 2026-11-25)

- Declarations: JPA entities `VatDeclaration` (`VAT_DECL`) + `VatDeclarationIndicator` (`VAT_DECL_ITEMS`) on the API's
  main (WeCMS) DB; DDL `docs/sql/2026/create-vat-declaration-tables-2026-08-04.sql` + V252 rename. One active row per
  `(BIZ_KEY, PERIOD_TYPE, PERIOD_VALUE, PERIOD_YEAR, SUPPL_NO)` (partial unique index). Every query is scoped by
  `TenantContext.currentBizKey()`; company name / tax code snapshotted from `BUSI_INFM` at create.
- Business rules live in `VatDeclarationService` (period order, carry-forward [43]→[22] across M/Q, lock / unlock
  chain, period-ended, [21] no-activity, unknown sales rate refused) — reusable as is.
- Invoice source = `VatAnnexInvoiceRepository` (6 native queries over `busa_doc_mstr` / `busa_doc_detl` /
  `einv_tax_detl` / `trco_infm`): `syncBuckets` (base + VAT per direction × rate), `salesVatByRate` (real output
  VAT), `groupTotals` / `findRows` (annex, one row per invoice × rate, 8% reduction = floor(base × 2%)),
  `markProcessed` / `clearProcessed` (lock stamps `busa_doc_mstr.vat_decl_proc_yn/sid`). Header-only invoices get a
  rate derived from tax/base (PO 2026-08-22). Excluded: replaced + cancelled invoices.
- WeInvoice: owner = JWT user id; WeInvoice self-signup users carry the sentinel `bizKey = WEINVOICE` (all share it), so
  `BIZ_KEY` cannot scope their declarations. Access to an MST = active row in `winv_cmpn_infm`
  (`CompanyAccessGuard.requireCompany`). Engine tables are read-only for the API.
- Engine data (DB 2026-11-25): `invoice_xml.parsed.thanh_toan.theo_thue_suat` (per-rate `thanh_tien` / `tien_thue`, from
  XML `THTTLTSuat`) on 34,192 / 37,222 XML; `invoice_details` lines (`thue_suat`, `thanh_tien`, `tien_thue`,
  `tinh_chat` 3 = discount); 63,140 invoices have no line (no XML: hthdon=2, MTT without XML…) → header
  `tien_chua_thue` / `tien_thue` only. Rates seen: 8%, 10%, KCT, KKKNT, 0%, 5%, and a few `KHAC:x%` / `KHAC` / `CTTC`.
  Foreign-currency invoices (USD 428, KRW 11) carry amounts in their currency + `ty_gia`.

## Design (decided 2026-11-25)

- **Keep the module, add a scope.** `VatScope(key, companyName, taxId)`; WeCMS keeps `key = BIZ_KEY`. WeInvoice key =
  `WI:` + first 17 hex of SHA-256(`ownerId|mst`) (20 chars = `BIZ_KEY` width), so the existing tables, unique index,
  chain rules and exporters work unchanged and each (user, MST) has its own book. Company name / tax code come from
  the user's `winv_cmpn_infm` row.
- **Pluggable invoice source.** `VatInvoiceSource` (the 6 operations, taking the declaration). WeCMS path forwards to
  `VatAnnexInvoiceRepository` exactly as today; a declaration whose key starts with `WI:` reads the engine
  (`EngineVatInvoiceSource`, JDBC on `WeInvoiceDb`) for `company_mst = tp_tax_code`.
- **Engine mapping.** direction `ban` = output (`I`), `mua` = input (`B`); period on `ngay_lap`; excluded
  effective `tthai` 4 (replaced) and 6 (cancelled) (`InvoiceCodes.SQL_TTHAI_EFF`). Per (invoice, rate): XML per-rate
  summary → else XML lines grouped by `thue_suat` (discount lines subtracted, missing line VAT = floor(base × rate)) →
  else header with derived rate (same buckets as the WeCMS `HDR_RATE`). Amounts × exchange rate when currency ≠ VND.
  VAT = the invoice's own per-rate VAT for both directions. 8% reduction = floor(base × 2 / 100) as before.
- **Lock marker.** Engine rows are not written: `markProcessed` / `clearProcessed` are no-ops; annex rows report
  `vat_decl_proc_yn` = `Y` when the declaration is locked, its sid as `vat_decl_sid`. Late-invoice detection (KHBS) is
  therefore not available for WeInvoice declarations (follow-up).
- **API.** New `WeInvoiceVatDeclarationController` at `/api/v1/weinvoice/companies/{mst}/vat-declarations` (list, create,
  detail, update, delete, sync, lock, unlock, annex invoices, export xml / pdf / report-excel), same DTOs as the old
  module; MST checked by `CompanyAccessGuard`; allow-listed like the other WeInvoice controllers. The annex Excel export
  (shared `ExcelExportService` screen pipeline) is not exposed for WeInvoice in this change.

## Scope

API side of the design above + tests. Old endpoint `/api/v1/addon/vat-declarations` unchanged.

## Out of Scope

Writing to engine tables; supplementary declarations (KHBS); annex Excel export for WeInvoice; new DDL.

## Database Impact

NONE (no new DDL / DML). **Deployment precondition:** `vat_decl` + `vat_decl_items` must exist on the API's main DB
(existing scripts `docs/sql/2026/create-vat-declaration-tables-2026-08-04.sql` + `V252__vat_decl_rename_and_drop_annex.sql`);
V253 (`busa_doc_mstr` marker) is not needed for WeInvoice. To be checked on SIT.

## Implementation

- `vatdeclaration/service/VatScope.java` (new): book key (`WI:` + 17 hex), `isWeInvoiceKey`.
- `vatdeclaration/service/VatInvoiceSource.java` (new): the 6 invoice operations, taking the declaration.
- `VatDeclarationService`: every public method has a `VatScope` overload; the old signatures delegate with the
  WeCMS tenant scope (same `@Transactional`). `create` snapshots `tp_name` / `tp_tax_code` from the scope when it is a
  WeInvoice book (no `BUSI_INFM`). Invoice calls go through `invoices(decl)`: WeCMS → inner `WecmsInvoices`
  (forwards to `VatAnnexInvoiceRepository` with the same arguments as before), WeInvoice key → `engineInvoices`.
- `weinvoice/repository/EngineVatInvoiceSource.java` (new): engine SQL per the design (summary → lines → header,
  tthai 4/6 excluded, × exchange rate), no-op markers, annex "processed" = declaration locked.
- `weinvoice/controller/WeInvoiceVatDeclarationController.java` (new): 12 endpoints under
  `/api/v1/weinvoice/companies/{mst}/vat-declarations`; scope from `CompanyAccessGuard.requireCompany` + owner.
- `EndpointAuthorizationCoverageTest`: controller added to the WeInvoice allow-list.
- Docs: [interface-specification.md](interface-specification.md) § VAT declaration, [test-scenarios.md](test-scenarios.md) V1–V7.

## Files Changed

`VatDeclarationService.java` · `VatScope.java` (new) · `VatInvoiceSource.java` (new) · `EngineVatInvoiceSource.java`
(new) · `WeInvoiceVatDeclarationController.java` (new) · `EndpointAuthorizationCoverageTest.java` ·
`VatDeclarationWeInvoiceScopeTest.java` (new) · `EngineVatInvoiceSourceTest.java` (new) · this record · the
test-result record · interface-specification.md · test-scenarios.md.

## API Impact

New endpoints above; `/api/v1/addon/vat-declarations` unchanged.

## Verification

### Automated Tests

- Test: `./gradlew test` (3,033 run, 1 pre-existing failure); new `VatDeclarationWeInvoiceScopeTest` 4/4,
  `EngineVatInvoiceSourceTest` 3/3
- Result: PASS (pre-existing failure classified B)

### Test Scenarios

- Scenario: engine SQL read-only on real data (1201496252 10/2026, 0318793907 10/2026, 3600790154 11/2026)
- Expected: sales per rate reconcile with invoice totals; USD converted
- Actual: sales reconcile to the đồng; purchase differences explained by 2 invoices (USD converted, lines without
  XML total); 45,640 invoices in 0.34 s
- Result: PASS

### Regression Review

- Areas checked: WeCMS lifecycle tests (unchanged calls to `VatAnnexInvoiceRepository`), authz coverage, full suite.
- Result: PASS

### Manual Verification

- Environment: LOCAL
- Result: NOT RUN — no API run against a DB with `vat_decl`; verify on SIT.

## Test Evidence

Test result:
[WeInvoice VAT Declaration on Engine Invoices (API) — Test Result](../../../testing/results/weinvoice-vat-declaration-engine-invoices-api-test-result-2026-11-25.md)

Final test result:    PARTIAL (unit + engine SQL PASS, end-to-end NOT RUN)
Regression coverage:  PASS

## Result

READY FOR MANUAL COMMIT. No commit / push performed.

## Known Limitations / Follow-up

- `vat_decl` / `vat_decl_items` must exist on the API's main DB (existing scripts) — check SIT before use.
- No late-invoice detection (KHBS) for WeInvoice books: engine rows carry no "closed into declaration" marker.
- Annex Excel export (shared `ExcelExportService` screen pipeline) not exposed for WeInvoice.
- A sales line with a rate outside 0/5/8/10/KCT/KKKNT (`KHAC:x%`, blank) makes sync refuse the period (WeCMS rule) —
  PO decision needed if such invoices are common.

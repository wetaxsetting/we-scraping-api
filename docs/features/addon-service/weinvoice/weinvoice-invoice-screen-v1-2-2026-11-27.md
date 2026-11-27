# WeInvoice — Invoice Screens per UI Spec v1.2 (parts A–D) — API

**Date:** 2026-11-27
**Type:** FEATURE
**Current Work Classification:** FEATURE
**Status:** READY FOR MANUAL COMMIT
**Repository:** we-scraping-api
**Approval Status:** NOT REQUIRED (user, 2026-11-27: "Start A–D" — implementation requested)
**Implementation Status:** COMPLETE (backend share)
**Test Status:** PASS (1 pre-existing unrelated failure in the full suite)

Web counterpart: `we-scraping-web/docs/features/weinvoice/weinvoice-invoice-screen-v1-2-2026-11-27.md`.
Source: UI Spec v1.2 (PO, 27/11/2026) — engine repo `docs/WeInvoice_UI_Spec_v1.2/00_THAY_DOI_v1.1_v1.2.md` §A–D and
`02_Yeu_cau_nghiep_vu.md` §3.3, 3.6, 3.11, 3.13–3.15. Parts E–G (auto-journal rebuild, chart of accounts, item catalogue)
are out of scope here.

## Requirement (backend share of A–D)

| # | Spec item | Backend need |
|---|---|---|
| A1 | Money shown as currency code, Excel "(₫)" → "(VND)" | Excel headers |
| B3 | Search adds item name (unaccented) and PO number | `keyword` also matches `invoice_details.ten_hhdv` and the PO in XML TTKhac |
| B5–B7 | New list columns: items, currency, rate, pre-tax, tax, **fee**, total, VND total; optional tax rate, trade discount, related invoice, due date, PO | `InvoiceRow` gains `fee`, `items` summary, `vat_rates`, `trade_discount`, `related`, `due_date`, `po_number` |
| B9–B10 | Totals bar by currency; KPIs in VND | `kpis.by_currency[]` (count, pre-tax, tax, fee, total, total VND) |
| B12 | Excel summary adds currency, rate, VND total, fee, PO; detail sheet adds fee lines, line tax, full decimals | `InvoiceWorkbook` |
| B13 | CQT list: trade discount / fee totals from XML | `CqtListWorkbook` |
| C1–C2 | Detail: related invoices both ways + effective value = original + Σ adjustments; fee in the totals | detail `related_list[]`, `effective_total` |
| C4 | Status from `TCHDon` (1 = replace, 2 = adjust) | check current mapping |
| D | XML fields | engine already parses them into `invoice_xml.parsed` (verified 2026-11-27) — read only |

## Current System Behavior (verified 2026-11-27)

- Engine `xml_parser` already stores every v1.2 field in `invoice_xml.parsed`: fees `thanh_toan.phi[] {ten, tien}`
  (5,191 XML), trade discount `thanh_toan.chiet_khau` (156 non-zero), related invoice
  `thong_tin_chung.hoa_don_lien_quan {tinh_chat (TCHDon), ky_hieu_mau, ky_hieu, so_hd, ngay_lap, ghi_chu}` (6,313),
  per-rate totals `theo_thue_suat[]`, line `tien_thue`, `giam_tru_khong_chiu_thue` / `giam_tru_khac`, `so_bang_ke`, and the
  whole TTKhac (`tt_khac`). GDT list: `list_raw.tgtphi` (51 non-zero), `ttcktmai` (5,831).
- PO / due date live in TTKhac under many names: "Số PO" 8,740, "PO" 112, "Số đơn hàng" 80, "RPO" 47, "DO" 47, "PONO",
  "PONumber", "SoPo", "purchaseOrder", "Đơn đặt hàng (số)"…; due date "Thời hạn thanh toán" 141, "DueDate" 86,
  "Hạn thanh toán" 61, "Kỳ hạn thanh toán", "Ngày thanh toán", "PAYMENT_TERM"…
- `InvoiceRow` has currency / rate / pre-tax / tax / total / total VND, no fee, items, PO, due date, related.
  `kpis` totals are VND only, no per-currency rows. `keyword` matches names, MSTs, symbol, number, CQT code — accented,
  no items, no PO. Database has no `unaccent` extension.
- Detail has `related` (one invoice: `replaces` / `adjusts` / `replaced_by` / `adjusted_by`) and `fees[]`.
- Status: GDT `tthai` (own status consistent with XML TCHDon: 256 × 1→2, 392 × 2→3); originals referenced by a
  replacement / adjustment mostly still 1 (engine never updates an invoice row).
- `InvoiceWorkbook` headers "(₫)"; `CqtListWorkbook` trade discount / fee columns not from XML.

## Proposed Implementation

1. `common/InvoiceExtraSql`: SQL for fee (XML fees, else `tgtphi`), trade discount, PO and due date (TTKhac key lists),
   accent folding (`translate` map, no extension needed) and related-invoice key normalisation.
2. List: base CTE adds `fee`, `trade_discount`, `po_no`, `due_raw`; keyword also matches folded item names and the PO;
   per-page extras query (items preview + count, tax rates, related forward / backward); `kpis.by_currency[]`.
3. Detail: `related_list[]` (every invoice naming this one) + `effective_total` (original + Σ adjustments).
4. Excel: "(VND)" headers; summary + currency / rate / VND total / fee / PO; detail sheet fee lines.
   CQT list: discount / fee from XML.

## Implementation

- `common/InvoiceExtraSql` (new): `FEE` (Σ XML fees, else `tgtphi`), `TRADE_DISCOUNT`, `PO` / `DUE` (first non-blank of
  the TTKhac key lists `PO_KEYS` / `DUE_KEYS`), related-invoice key normalisation (`refSymbol` / `refTemplate` /
  `refNumber`), `searchPattern` (POSIX regex from the folded keyword, each letter a class of its cases + accented forms).
  Initial plan used `translate()` folding; replaced because it was ~20× slower on item lines (1.36 s vs 0.06 s) — regex
  runs 0.05–0.17 s on the same set.
- `InvoiceQueryRepository`: base CTE lateral `fe` (fee, trade discount, PO, due); keyword also matches `po_no`, names and
  item names by regex; `kpis.fee`; `currencyTotals` (totals bar per currency); `pageExtras` for the page ids (item preview
  + count, tax rates, related forward via TTHDLQuan TCHDon 1/2 with the original's id when stored, referenced-by list).
- `InvoiceRow` + `fee, trade_discount, po_number, due_date, due_text, items, item_count, vat_rates, related` (`Related`,
  `Ref`); `InvoiceKpis` + `fee, by_currency`; `InvoiceCodes.dueDate` (ISO from `yyyy-mm-dd` / `dd/mm/yyyy`, else null).
- Detail: `InvoiceDetailRepository` reads PO / due; `InvoiceQueryService` adds `fee`, `po_number`, `due_*`,
  `referenced_by`, `effective_total` (original + Σ adjustments).
- `InvoiceWorkbook`: money headers without "(₫)" (amounts stay in the invoice currency — a "(VND)" label would be wrong
  for USD rows), + Loại tiền, Tỷ giá, Phí / lệ phí, Tổng quy đổi (VND), Số PO; detail sheets + Loại tiền and one fee line
  per invoice with fees (no VAT); decimal money cells `#,##0.00`. `CqtListWorkbook` / `PackRepository.cqtRows`: trade
  discount / fee from the XML.
- C4 (status): own status already comes from GDT and matches TCHDon (256 + 392 XML); originals are upgraded by the
  **engine** (`invoice_store.mark_related_original` on every XML save + `invoice_job.py related-status [--apply]` for the
  backlog: 55 originals on 2026-11-27, not applied yet) — the API keeps reading `tthai`.

## Files Changed

`common/InvoiceExtraSql.java` (new), `common/InvoiceCodes.java`, `dto/InvoiceRow.java`, `dto/InvoiceKpis.java`,
`dto/InvoiceDetail.java`, `repository/InvoiceQueryRepository.java`, `repository/InvoiceDetailRepository.java`,
`repository/PackRepository.java`, `service/InvoiceQueryService.java`, `service/InvoiceWorkbook.java`,
`service/CqtListWorkbook.java`; tests `common/InvoiceExtraSqlTest.java` (new), `service/InvoiceQueryServiceTestAccess.java`
(new), `service/InvoiceWorkbookTest.java`, `service/InvoiceFileServiceTest.java`, `repository/InvoiceListServerPagingTest.java`,
`controller/WeInvoiceInvoiceControllerTest.java`; docs: this record, interface-specification.md, the test-result record.

## API Impact

Additive JSON fields only (list rows, `kpis`, detail); `keyword` matches more. Excel column layout changed (§3.11).

## Database Impact

NONE — read-side SQL over engine tables. (The engine change for C4 writes engine tables in the engine repo.)

## Verification

- `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` — 244 tests, 0 failures.
- `./gradlew test --offline` — 3,045 run, 1 failed (pre-existing `VatDeclarationLifecycleTest`), 22 skipped.
- Read-only against the shared engine DB (temporary test, removed): totals per currency (3700860461 Oct–Nov: VND 3,901 ·
  KRW 6 · USD 41 with VND conversion), fees (3.65 M VND), PO (`PO-119963`), items preview + count, tax rates, related both
  ways (original 327226 ← adjustment 348508 at −7,024,500; 398709 ← replacement 398715); search "mi an lien" → 641
  invoices, "PO-" → PO match; list 0.1–0.5 s, keyword over 11 months ≤ 1.7 s.

## Test Evidence

Test result:
[WeInvoice Invoice Screens v1.2 (API) — Test Result](../../../testing/results/weinvoice-invoice-screen-v1-2-api-test-result-2026-11-27.md)

## Known Limitations / Follow-up

- Excel detail: line tax reconciliation to the XML per-rate totals (§3.14 largest remainder) is not done — line tax is
  the XML line tax or rate × amount, as before. Fee line is one line per invoice (sum), not one per fee name.
- Payment due date is only coloured when TTKhac holds a date; terms like "30DAYS" are shown as written.
- C4 backlog fix (`related-status --apply`) waits for a go-ahead on the shared DB.

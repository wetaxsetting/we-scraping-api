# View e-Invoice Details — Backend Refactor Design (wecms-api)

**Module:** Add-on Service · **Feature slug:** `view-e-invoice-details` (shared by Sales & Expenses)
**Java package:** `com.kosign.wecms.domain.einvoice` (existing — extend, do not fork)
**REST base:** `/api/v1/addon` (resource `/einvoices`)
**JEX source of truth (read-only):** [docs/jex/features/view-e-invoice-details.md](../../../../docs/jex/features/view-e-invoice-details.md) — legacy `einv_0002_001_r001` (data) + `einv_0002_002` (`DV_CD=H` print HTML / `DV_CD=P` PDF)
**Frontend counterpart:** [wecms-web/docs/features/addon-service/view-e-invoice-details.md](../../../../wecms-web/docs/features/addon-service/view-e-invoice-details.md)
**Status:** ✅ **Implemented (2026-07-15).** Backend provides the **JSON detail endpoint only** —
`EInvoiceDetailService` + `EInvoiceDetailResponse`; `BusaDocMstr.trcoRprsTlph` mapped; `WBE2019` added.

> **Print + PDF moved to the client (2026-07-15).** For 100% UI parity, Print and PDF are
> generated in `wecms-web` from the modal's own `<InvoiceDocument>` DOM (`lib/print`
> `printElement` + `lib/pdf` `downloadElementAsPdf` via html2canvas-pro + jsPDF). The former
> server-rendered `GET /einvoices/{doc_id}/pdf` + `/print` endpoints, `EInvoicePdfService`,
> the `openhtmltopdf-pdfbox` + `zxing` dependencies, and the embedded `fonts/einvoice.ttf`
> were **removed** — a server template can't reproduce the React/Tailwind modal (openhtmltopdf
> 1.0.x has no flexbox), so it would be the duplicate layout the requirement forbids. Bonus:
> Vietnamese renders via the browser font, so no embedded TTF is needed. See the frontend
> counterpart doc.

> **Goal.** Give the shared "View e-Invoice Details" modal a real data source: a
> single-invoice detail endpoint. Reuse the entities/repositories
> already wired into `EInvoiceDetailExportService`. **No new tables** — all four
> (`busa_doc_mstr`, `busa_doc_detl`, `einv_fee_detl`, `einv_tax_detl`) are in the
> legacy-table-inventory baseline.

---

## 1. What already exists (do not rebuild)

Per the current-state audit, `domain/einvoice` already ships list, both XLSX exports,
GDT scrape (phase 1/2), and history. Entities `BusaDocMstr`, `BusaDocDetl`, `EinvFeeDetl`,
`EinvTaxDetl` and their repositories exist. The detail data-assembly already lives inside
`EInvoiceDetailExportService` (master + `detlRepo.findByBizKeyAndDocIdOrderByDispOrdAsc` +
`feeRepo...`), built for Excel. **This design surfaces that same data as JSON + PDF.**

`EInvoiceService.resolveDocType` already maps `SALES`/`I`→`'I'`, `EXPENSE`/`BILL`→`'B'`,
else `WBE2002` — reuse it.

---

## 2. Detail endpoint (JSON)

```
GET /api/v1/addon/einvoices/{doc_id}?type=SALES|EXPENSE
→ ApiResponse<EInvoiceDetailResponse>
```

- `{doc_id}` — path, the `DOC_ID`. `type` — query, resolved via `resolveDocType`.
- Tenant from `TenantContext.currentBizKey()` — never a client `biz_key`.
- `@PreAuthorize("@menuAuthz.canAccessEInvoice(#type)")` — same gate as the list.

**Service logic (mirror legacy `einv_0002_001_r001_act.jsp`):**

1. `docType = resolveDocType(type)` — reject `EXPENSE`(`E`)/`PAYROLLBOOK`(`S`) with `WBE2002`.
2. Load master: `BusaDocMstrRepository` by `(BIZ_KEY, DOC_ID)` **AND `DOC_TYPE=docType`
   AND `HAND_REG_YN='N'`** (legacy `:127-134` — hand-registered docs are out of scope for
   this popup). Not found → `WBE2014`-style not-found envelope (new code or reuse).
3. Load `REC`/`REC2`/`REC3` via the three repos ordered by `DISP_ORD`.
4. **Resolve seller/buyer server-side** (do the legacy `ISSU_*`/`TRCO_*` swap here so the
   frontend receives explicit `seller`/`buyer` objects — cleaner than shipping raw
   ISSU/TRCO + a client swap; see §4).
5. Assemble `EInvoiceDetailResponse`.

---

## 3. Response DTO (lowercase snake_case wire)

Java records, camelCase fields → global Jackson `SNAKE_CASE`. Amounts stay raw decimal
strings/`BigDecimal` (frontend formats via `lib/currency.ts`, matching the list); dates/times
stay raw legacy strings (frontend formats). Proposed shape:

```jsonc
{
  "doc_id": "…", "doc_type": "I",           // I=sale, B=bill
  "invoice_title": "HOÁ ĐƠN GIÁ TRỊ GIA TĂNG",
  "numeric_symbol": "1", "invoice_symbol": "C24TWV", "hand_doc_id": "1231",
  "invoice_status": "…", "mccqt": "…", "qrcode": "…",
  "original_invoice": "…", "issu_dt": "20241014",
  "currency_cd": "VND", "rate": "…",
  "list_number": "…", "list_date": "…", "payment_type": "…",
  "test_result": "…",                        // drives the einv_testresul==2 variant
  "seller": { "name": "…", "tax_id": "…", "address": "…", "phone": "…",
              "bank_acc": "…", "bank_name": "…",
              "sign_by": "…", "sign_date": "…", "sign_time": "…" },
  "buyer":  { "name": "…", "full_name": "…", "tax_id": "…", "address": "…",
              "bank_acc": "…", "bank_name": "…", "citizen_id": "…", "phone": "…" },
  "items": [ { "disp_ord": 1, "item_nm": "…", "product_name": "…", "item_spec": "…",
               "item_qunt": "…", "item_unit_amt": "…", "dc_amt": "…",
               "tax_rate": "…", "sply_amt": "…" } ],
  "fees":  [ { "disp_ord": 1, "fee_name": "…", "fee": "…" } ],
  "taxes": [ { "tax_rate": "…", "amt": "…", "tax_amt": "…" } ],
  "totals": { "sply_amt_sum": "…", "tax_amt_sum": "…", "totl_fee_amt": "…",
              "totl_dis_amt": "…", "totl_amt": "…", "total_amt_in_letter": "…" },
  "con_doc_id": "…"                          // linked expense doc (unlink context; optional)
}
```

DTOs: `EInvoiceDetailResponse`, `EInvoicePartyDto` (seller/buyer), `EInvoiceItemRow`,
`EInvoiceFeeRow`, `EInvoiceTaxRow`, `EInvoiceTotals`.

---

## 4. Seller / buyer resolution (carry legacy semantics exactly)

Two distinct sources — reproduce both (JEX audit §5):

- **Company identity swaps by `doc_type`.** `ISSU_*` = own company, `TRCO_*` = counterpart.
  - `doc_type='I'` (sale): `seller` ← `ISSU_*`, `buyer` ← `TRCO_*`.
  - `doc_type='B'` (bill): `seller` ← `TRCO_*`, `buyer` ← `ISSU_*`.
- **Bank + sign are dedicated columns, NOT swapped.** `seller.bank_acc`/`bank_name` ←
  `einv_seller*`; `buyer.bank_acc`/`bank_name` ← `einv_buyer*`; `seller.sign_*` ←
  `einv_seller_sign*`. `buyer.full_name` ← `einv_buyerfullname`, `buyer.citizen_id` ←
  `einv_buyercitizenid`.
- `TRCO_BSUN_ADRS` = concat of the four `TRCO_BSUN_*` parts (legacy IDO does this) — do it
  in Java or a `@Subselect`; do not add a DB view.
- Guard the legacy NPE spots (`einv_0002_001_r001_act.jsp:198-200` dereferences
  tax-info/currency with no null check).

---

## 5. Print + PDF — client-side, no server endpoint

**There is no server PDF/print endpoint.** Print and PDF are produced in `wecms-web` from the
modal's own rendered `<InvoiceDocument>` DOM, so the modal, Print, and PDF are byte-for-byte
identical (100% UI parity — the explicit requirement, 2026-07-15):

- **Print** → `lib/print.ts` `printElement(node)` clones the invoice DOM into a hidden iframe
  **with the app's own stylesheets** and prints it (native browser print).
- **PDF** → `lib/pdf.ts` `downloadElementAsPdf(node, fileName)` raster-captures the same DOM
  via **html2canvas-pro** and lays it into a multi-page A4 **jsPDF** → direct `<doc_id>.pdf`
  download. Vietnamese renders via the browser's Times New Roman (no embedded TTF).

The former server-rendered `openhtmltopdf` template (`EInvoicePdfService`, the `/pdf` + `/print`
endpoints, the `openhtmltopdf-pdfbox` + `zxing` deps, and `fonts/einvoice.ttf`) was removed —
it could not reproduce the React/Tailwind modal and would be a divergent duplicate layout. See
the frontend counterpart doc §2.3–2.4.

---

## 6. Error codes

| Code | When |
|---|---|
| `WBE2002` | `type` not `SALES`/`EXPENSE` (or resolves to E/S) |
| (not-found) | `DOC_ID` absent for tenant / `HAND_REG_YN='Y'` — reuse a not-found envelope |
| `WBE_VALIDATION` | missing/blank `doc_id` or `type` |
| `WBE9999` | unexpected server error (logged with trace id) |

Add any new code to `messages.properties` before throwing.

---

## 7. Tables (all in baseline — no PO approval)

`busa_doc_mstr` (51), `busa_doc_detl` (48), `einv_fee_detl` (76), `einv_tax_detl` (78) —
`wecms-api/docs/references/database/legacy-table-inventory.md`. Read-only; no migration.

---

## 8. Open questions

- **OQ-PDF / Print — RESOLVED 2026-07-15 (client-side).** Print + PDF render from the modal's
  own DOM in `wecms-web` (§5) for 100% parity; the server PDF/print layer was removed. No
  server PDF/print endpoint.
- **OQ-CON — RESOLVED 2026-07-15: no Unlink.** The refactor has no Unlink feature, so the
  modal shows no Unlink button and the legacy linked-expense `con_doc_id` is **not surfaced**
  — the field was removed from `EInvoiceDetailResponse` (and the wire / mapper / TS types) so
  no dead unlink plumbing remains. Reviving it would need a fresh PO decision + the
  `einv_0002_001_u001` write-path.
- **OQ-TEST** — confirm the `einv_testresul==="2"` variant is still needed (GDT test-invoice
  rendering). `test_result` ships in the DTO so the frontend can branch.

---

## Changelog
### v2.0.0 — 2026-07-15
- Print + PDF moved fully **client-side** (modal-DOM parity). Removed `GET /einvoices/{doc_id}/pdf`
  + `/print`, `EInvoicePdfService`, `openhtmltopdf-pdfbox` + `zxing` deps, and
  `fonts/einvoice.ttf`. Backend now exposes only the JSON detail endpoint.

### v1.1.0 — 2026-07-15
- OQ-PDF/Print resolved: added `GET /einvoices/{doc_id}/print` (server-rendered HTML) sharing
  the openhtmltopdf template; Print and PDF now use one source. _(Superseded by v2.0.0.)_
- OQ-CON resolved: no Unlink; `con_doc_id` removed end-to-end.

### v1.0.0 — 2026-07-15
- Initial backend refactor design (proposed) for the shared detail endpoint + PDF.

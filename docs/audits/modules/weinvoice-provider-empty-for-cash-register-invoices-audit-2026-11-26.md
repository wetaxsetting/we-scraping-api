# WeInvoice — "Phát hành qua" Empty for Cash-Register Invoices — Audit

**Date:** 2026-11-26
**Type:** AUDIT / INVESTIGATION
**Current Work Classification:** AUDIT
**Finding Classification:** BUG (display gap)
**Status:** COMPLETE
**Repository:** we-scraping-api
**Investigation Status:** COMPLETE
**Approval Status:** APPROVED (user, 2026-11-26 — "Sửa đi")
**Implementation Status:** see [fix record](../../bugs/weinvoice-provider-empty-for-cash-register-invoices-fix-2026-11-26.md)

## Observed Behavior

User screenshot (SIT `we-scraping.wetax.com.vn/invoices/purchase`, MST 1201496252, year 2026, filter Trạng thái = Thay thế,
5 invoices): the "Phát hành qua" column shows "—" for the four cash-register (MTT, `1C26M…`) invoices; only the no-code
invoice 2K26TAF-153 shows "MISA". Two of the four (1C26MSC 159433 / 159631) also have no PDF.

## Expected Behavior

The provider the invoice was issued through is shown whenever the system knows it.

## Scope

How the API derives `provider` for the invoice list / detail, and what the engine knows for these invoices.

## Initial Evidence (engine DB, read-only, 2026-11-26)

| Invoice | Seller | `invoice_xml.mst_tcgp` / `list_raw.msttcgp` | What else the data says | PDF |
|---|---|---|---|---|
| 1C26MSU-115 | SEOU 0317415726 | null / null | `lookup_url` `0317415726hd.easyinvoice.com.vn` (EasyInvoice) | `email:easyinvoice` |
| 1C26MYY-5509 | 0319247114 | null / null | TTKhac `Fkey`; PDF fetched by the iPOS adapter | `email:ipos` |
| 1C26MSC-159433, 159631 | LY T… 0318793907 | null / null | TTKhac `MSTTCGP = 1201496252` (WeTax, Webcash) + `PortalLink wetax.com.vn/tra-cuu?lookUpCode=` (empty code) | none (`dead/no_code`) |
| 2K26TAF-153 | BV 0302159861 | 0101243150 / 0101243150 | MISA | `misa_meinvoice` |

Cash-register XML has no `<MSTTCGP>` header element; the engine already routes such invoices by lookup host / TTKhac /
the seller's usual provider (`invoice_job.pick_adapter`).

## Investigation Plan

1. Read how `InvoiceQueryRepository` / `InvoiceQueryService` / `ProviderLookup` resolve `provider`.
2. Compare with the engine's routing and with the PDF `source_id`.

## Backend Trace

- List: `InvoiceQueryRepository` line ~59 — `prvd_mst = NULLIF(COALESCE(NULLIF(x.mst_tcgp,''), i.list_raw->>'msttcgp'),'')`;
  the facet "Phát hành qua" and the provider filter use the same value.
- Detail: `InvoiceQueryService` line ~166 — `firstNonBlank(d.xmlProviderMst(), raw.msttcgp)`.
- Nothing else is consulted: not TTKhac `MSTTCGP`, not the lookup link host, not the adapter that fetched the PDF
  (`invoice_pdf.source_id`), not the seller's usual provider — all of which the engine already uses to route PDFs.

## Root Cause

Finding Classification: **BUG (display gap)** — the provider is derived from the two header fields only, which cash-register
invoices never carry (QĐ 1233: MTT XML has no `<MSTTCGP>`; the GDT list has no `msttcgp` for them). The engine knows the
provider by other means, the API does not use them.

## Scale (engine DB, 2026-11-26)

- 64,938 invoices have no provider: 60,831 are MTT, only 1,798 have an XML. Sales 60,264, purchases 4,674.
- Recoverable by: TTKhac `MSTTCGP` 10 · PDF adapter `source_id` (→ `einvoice_providers.source_id`) another 416 · lookup
  link host another 66 · **seller's most frequent provider on their other invoices: sales 60,264/60,264, purchases
  2,230/4,674**.
- The two LY T… invoices (seller 0318793907) are issued through **WeTax** (TTKhac `MSTTCGP 1201496252`); their XML link has
  an empty `lookUpCode`, so no PDF (`no_code`) — expected, unrelated to this display gap.

## Recommended Implementation

API only, read side (no engine write, no DDL):

1. `prvd_mst` = first non-blank of: `x.mst_tcgp` → `list_raw.msttcgp` → `x.parsed->'tt_khac'->>'MSTTCGP'` →
   provider of the PDF adapter (`einvoice_providers.source_id` = `invoice_pdf.source_id` without `email:`) → seller's usual
   provider (most frequent non-null provider of the same seller MST, parent MST for branches).
2. Same chain in the detail (`InvoiceQueryService`) and in the facet / filter so the column, filter and counts agree.
3. The seller-usual step needs a per-seller lookup; compute it once (CTE grouped by seller over invoices that have a
   provider, or a small materialised view refreshed by the engine) — not a correlated subquery per row on 60k-row lists.
4. Mark an inferred provider (e.g. `provider_inferred = true`) if the PO wants to distinguish "từ XML" vs "suy ra".

## Required Tests

Repository/query-contract test for the fallback chain; service test for the detail; check on SIT with the 5 invoices of
the screenshot (expect EasyInvoice, iPOS, WeTax ×2, MISA).

## Risks

Seller-usual guess can be wrong when a seller switches provider mid-year; showing it as "suy ra" mitigates.

## Affected Repositories

we-scraping-api (derivation). we-scraping-web only if an "inferred" marker is shown.

## Database Impact

NONE (read side only), unless the materialised view option is chosen (then a manual DBA script).

## Result

Investigation COMPLETE · Approval PENDING · Implementation NOT STARTED.

# Sales e-Invoices — Refactor Gap Analysis (Backend)

Snapshot date: 2026-06-04. Status: ✅ **read + export + scrape-intake + history implemented**; the legacy write flow and the live GDT/NXISAS pull are intentionally out of refactor R&D scope. This doc records coverage vs JEX, deliberate divergences, and the residual gaps.

---

## 1. Area / Legacy / Refactor / Status

| Area | Legacy (`einv_0001_001`, `einvType=1`) | Refactor (`com.kosign.wecms.domain.einvoice`, `type=SALES`) | Status |
|---|---|---|---|
| List read | `einv_0001_001_r001` IDO `BUSA_DOC_MSTR_R022` (~80 cols + joins) | `GET /einvoices` → `EInvoiceService#list` → `BusaDocMstrRepository.findFiltered` (16-field row) | ✅ Completed |
| Baseline filters | `PROV_STTS NOT IN ('VD','EE')` + `HAND_REG_YN='N'` + has-line-items + date range | first two + date range applied; **has-line-items predicate not ported** | ⚠️ Partial |
| `DOC_TYPE` resolution | `einvType=1 → 'I'`, `=2 → 'B'`; rejects `E`/`S` | `resolveDocType` `SALES→'I'`, `EXPENSE→'B'`, rejects `E`/`S`→`WBE2002` | ✅ Completed (old "EXPENSE→'E' bug" is stale) |
| Keyword search | View-Setting-driven 16-column OR + `wb_get_translate_text` accent fold | fixed 5-column OR, `ESCAPE '!'`, no accent fold (IR-5) | ⚠️ Partial (deliberate) |
| Invoice status localisation | `wb_get_invoice_status(value, LANG_CD)` | raw `einv_invoicestatus` returned; frontend regex tints (IR-4) | ⚠️ Divergent (deliberate) |
| Pagination + sort | `PageInfo` + `SortHelper` (View-Setting) | server-side `page`/`size` (clamp 100) + `EInvoiceSortField` whitelist | ✅ Completed |
| Multi-currency totals | `TOT_VND_*` / `TOT_USD_*` + per-status totals | `aggregateByCurrency` per-currency (unpaged); **per-status totals not ported** | ⚠️ Partial |
| Excel — list | `einv_0001_001_r002_excel` | `SalesEInvoiceExportProvider` + shared pipeline | ✅ Completed |
| Excel — with details | detail-expansion mode of `r002_excel` | `EInvoiceDetailExportService` | ✅ Completed |
| Scrape intake — phase 1 | `nxisas_einvoice_c001` | `EInvoiceScrapeService` (intake only) | ✅ Completed (intake) |
| Scrape intake — phase 2 | `nxisas_einvoice_c002` (+ trigger side effects) | `EInvoiceDetailScrapeService` + `EInvoiceTrcoAutoRegistrar` | ✅ Completed (intake) |
| GDT inquiry history | `nxisas_einvhis_r001` | `EInvoiceHistoryService#list` | ✅ Completed |
| Re-poll detail history | `nxisas_einvhis_dt_r001` | `EInvoiceHistoryService#detailHistory` (Sales-only) | ✅ Completed |
| Live GDT/NXISAS pull | local NXISAS engine → Coocon gateway → GDT | **not ported** — intake accepts already-fetched data | ⛔ Out of scope (R&D) |
| Issue/replace/adjust/cancel | `einv_0001_001_u001` write flow | — | ⛔ Not ported (read-side feature) |
| `COLOR` / `MEMO` sticker | per-doc memo class | — | ⛔ Not ported (memo feature not in scope) |
| Product gate (`PRDT_TYPE='F'`) | e-Invoice menu gated to WeCMS Default | `PrdtType` models `F`/`M`/`H`; gate not yet applied | ⚠️ Deferred |

---

## 2. Residual items / TODOs

| Item | Severity | Notes |
|---|---|---|
| Has-line-items baseline predicate | medium | Legacy hides invoices with no `BUSA_DOC_DETL` row; refactor list does not. Low practical impact while seeds are detail-complete; add the `EXISTS (… BUSA_DOC_DETL)` predicate to `findFiltered` for full parity. |
| `PROV_STTS=OP` expansion | low | Legacy expands `OP` filter to `IN ('OP','PP','OD')`; refactor has no `provStts` filter param yet, so moot until one is added. |
| Per-status footer totals | low | Legacy returns `TOT_OP_INV_*` etc.; refactor returns per-currency only. Add a second aggregate if PO wants the status breakdown. |
| Keyword accent folding | medium | `wb_get_translate_text` not ported; Vietnamese accent-insensitive search behaves differently. Decide port-to-Flyway vs Java `Collator`/`Normalizer` vs accept divergence. |
| Invoice-status server-side filter (IR-4) | medium | The list has no `invoiceStatus` query param; the frontend post-filters the page slice by regex. Add a server-side filter for correctness across pages. |
| Product-type gate | medium | `PRDT_TYPE='F'` e-Invoice gate deferred; lands with the e-Invoice product-gate MR. |
| Test coverage | medium | `resolveDocType`/scrape/auto-register/export have tests; `list` filters, `history`, `detail-history`, and the detail export lack dedicated tests — see [test-scenarios.md §2](test-scenarios.md#2-coverage-gaps-recommended-additions). |
| KM (Khmer) i18n | low | Error messages + frontend keys cover EN/KO/VI only. |

None of these block the read/export/scrape feature being considered functionally complete for refactor R&D.

---

## 3. Deliberate divergences from JEX

| # | Divergence | Rationale |
|---|---|---|
| D1 | Raw `einv_invoicestatus` (no `wb_get_invoice_status`) | Function not in local Flyway; frontend regex localises + tints (IR-4). |
| D2 | `issu_tm` proxy from `REG_DTM[8..14]` | Legacy `BUSA_DOC_MSTR` has no issue-time column. |
| D3 | 5-column keyword instead of 16-column View-Setting walk | IR-5 — simplified Phase-2 subset; full walk + accent fold deferred. |
| D4 | Multi-currency totals only | Per-status totals not surfaced on this page. |
| D5 | Scrape intake decoupled from the GDT pull | Refactor R&D is local-only; no tax-authority integration. |
| D6 | `INV_TYPE` mapped but never serialised | It is the Commercial/Tax classifier, not the invoice status (IR-7). |
| D7 | `COLOR` / `MEMO` omitted | Memo-sticker concern, not invoice status; deferred until memo functionality is in scope. |

---

## 4. Open questions

None blocking for the backend read/export/scrape scope. Cross-cutting product decisions (server-side status filter resolution path, accent-fold strategy, per-status totals, product-type gate) are tracked as IR-4 / IR-5 follow-ups in the planning doc _(removed — historical `planning/` folder deleted by project decision)_. The legacy write flow (`einv_0001_001_u001`) is a separate future scope decision.

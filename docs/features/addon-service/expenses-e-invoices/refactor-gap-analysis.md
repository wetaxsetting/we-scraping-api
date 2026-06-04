# Expenses e-Invoices — Refactor Gap Analysis (Backend)

Snapshot date: 2026-06-04. Coverage, divergences, and gaps are **the same as Sales** because the backend is shared — see the canonical [Sales refactor-gap-analysis.md](../sales-e-invoices/refactor-gap-analysis.md). Only the Expenses-specific table + open question are below.

---

## 1. Area / Legacy / Refactor / Status (Expenses)

| Area | Legacy (`einv_0001_001`, `einvType=2`) | Refactor (`type=EXPENSE`) | Status |
|---|---|---|---|
| List read | `einv_0001_001_r001` (`DOC_TYPE='B'`) | `GET /einvoices?type=EXPENSE` → shared `EInvoiceService#list` | ✅ Completed |
| `DOC_TYPE` resolution | `einvType=2 → 'B'` | `resolveDocType("EXPENSE")` → `'B'` (old "EXPENSE→'E' blocker" is stale) | ✅ Completed |
| Baseline filters | `PROV_STTS NOT IN ('VD','EE')` + `HAND_REG_YN='N'` + has-line-items + date | first two + date; has-line-items not ported (shared gap) | ⚠️ Partial |
| Keyword (`table2` whitelist) | View-Setting 16-col + accent fold | fixed 5-col OR (shared, IR-5) | ⚠️ Partial (deliberate) |
| Pagination + sort | `PageInfo` + `SortHelper` | shared server-side + `EInvoiceSortField` | ✅ Completed |
| Multi-currency totals | `TOT_VND_*`/`TOT_USD_*` + per-status | per-currency only (shared) | ⚠️ Partial |
| Excel — list | `r002_excel` (Bill cols) | `BillEInvoiceExportProvider` | ✅ Completed |
| Excel — with details | detail-expansion | `EInvoiceDetailExportService` | ✅ Completed |
| Scrape intake (phase 1/2) | `nxisas_einvoice_c001/c002` (Expense) | shared intake; counterparty = seller | ✅ Completed (intake) |
| GDT inquiry history | `nxisas_einvhis_r001` (Expense) | `EInvoiceHistoryService#list` (`GDT_INQUIRY_EXPENSES`) | ✅ Completed |
| Re-poll detail history | `nxisas_einvhis_dt_r001` | **not exposed for Expense** (`type=EXPENSE` → `WBE2002`) | ⛔ Sales-only (verify intentional w/ PO) |
| Live GDT/NXISAS pull | local engine → GDT | not ported (intake accepts fetched data) | ⛔ Out of scope (R&D) |
| Supplier MST compliance flag | `COMPANYSTATUS` join surfaces suspended suppliers | supplier `trco_tax_id` surfaced; status join not on this list (cross-check via Operation Status Inquiry) | ⚠️ Partial |

---

## 2. Residual items / TODOs

All shared gaps from [Sales §2](../sales-e-invoices/refactor-gap-analysis.md#2-residual-items--todos) apply (has-line-items predicate, accent folding, server-side status filter, per-status totals, product gate, test coverage). Expenses-specific:

| Item | Severity | Notes |
|---|---|---|
| Detail-history Sales-only | low | `nxisas_einvhis_dt_r001` is ported for Sales only; Expense returns `WBE2002`. Confirm with PO whether the Expense re-poll history is needed. |
| Supplier-status surfacing | low | The Expenses compliance angle (input-VAT credit on suspended suppliers) is strongest, but the list does not join `OPRT_STTS_INFO`. Cross-checked manually via Operation Status Inquiry today. |
| `BUYER` vs `SUPPLIER` FIELD_ID | low | See §4. |

## 3. Deliberate divergences

All shared with Sales ([§3](../sales-e-invoices/refactor-gap-analysis.md#3-deliberate-divergences-from-jex)). No Expenses-specific divergences beyond the `DOC_TYPE='B'` discriminator and the Sales-only detail-history endpoint.

## 4. Open questions

- **`BUYER` vs `SUPPLIER` FIELD_ID (seed naming).** The View Setting / column key is seeded as `BUYER` (reused from Sales; header localises to "Supplier"). PO may prefer `SUPPLIER` for cross-screen clarity — affects the seed PK row, the frontend column registry key, and the Excel `columnConfig` key. Both spellings are technically valid (`(WEB_SVC_ID, FIELD_ID)` PK, no collision). Not a technical blocker; flag for PO confirmation.
- **Expense re-poll detail-history** — confirm whether it should be ported (currently Sales-only).

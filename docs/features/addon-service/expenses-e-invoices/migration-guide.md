# Expenses e-Invoices — JEX → Spring Migration Guide

The mapping is **identical to Sales** — see the canonical [Sales migration-guide.md](../sales-e-invoices/migration-guide.md). The legacy `einv_0001_001` view+service drives Sales when `einvType=1` and Expenses when `einvType=2`; the refactor branches on `type=SALES|EXPENSE` → `DOC_TYPE='I'|'B'`. Only the deltas are below.

---

## 1. Service mapping (Expenses)

| JEX WSVC / action | Spring endpoint | Service |
|---|---|---|
| `einv_0001_001_r001` (`einvType=2`) | `GET /einvoices?type=EXPENSE` | `EInvoiceService#list` |
| `einv_0001_001_r002_excel` (Bill cols) | `POST /einvoices/export` (`screen_id=addon-expenses-einvoices`) | `BillEInvoiceExportProvider` |
| `nxisas_einvoice_c001/c002` (Expense) | `POST /einvoices/scrape-results` / `scrape-detail-results` (`type=EXPENSE`) | `EInvoiceScrapeService` / `EInvoiceDetailScrapeService` |
| `nxisas_einvhis_r001` (Expense inquiry) | `GET /einvoices/history?type=EXPENSE` | `EInvoiceHistoryService#list` |
| `nxisas_einvhis_dt_r001` | — | **not exposed for Expense** (Sales-only; `type=EXPENSE` → `WBE2002`) |

## 2. `DOC_TYPE` mapping

| Wire `type` | Legacy `DOC_TYPE` | Internal `einvType` | `BusaDocType` |
|---|---|---|---|
| `EXPENSE` | `'B'` | `table2` | `BILL` |

The legacy `einvType=2` slice resolves to `DOC_TYPE='B'`; `resolveDocType` enforces this. (`'E'`/`'S'` rejected with `WBE2002`.)

## 3. Field mapping, behavioural translation, divergences

All identical to [Sales §3–§5](../sales-e-invoices/migration-guide.md#3-wsvc-contract--wire-field-mapping). For Expenses, the same `TRCO_CMNM` / `TRCO_TAX_ID` columns carry the supplier instead of the buyer; the View Setting `BUYER` column header localises to "Supplier".

## 4. Search whitelist

The legacy whitelist is keyed by `dymId` (`table1` Sales / `table2` Expenses). The refactor ships the same fixed 5-column subset (IR-5) for both; verifying whether the legacy `table2` whitelist diverges from `table1` is tracked in the [planning doc](../../../planning/addon/expenses-e-invoices-plan.md) and the [scraping parity audit](../../../audits/sales-expense-einvoices-scraping-parity.md).

## 5. PROD scope & status

Same active-table scope as Sales. Migration status: ✅ list/export/scrape/inquiry-history implemented (Expenses slice); detail-history intentionally Sales-only; write flow + live GDT pull out of scope. See [refactor-gap-analysis.md](refactor-gap-analysis.md).

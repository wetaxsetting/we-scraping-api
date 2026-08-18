# Add-on Service > Expenses e-Invoices — Backend Summary (Root)

> ⚠️ **This document is a high-level feature summary.**
> The detailed source of truth is the module-folder documentation:
> [`docs/features/addon-service/expenses-e-invoices/`](expenses-e-invoices/README.md)
> (which documents the deltas over the shared [Sales backend](sales-e-invoices/README.md)) + the implementation code under `com.kosign.wecms.domain.einvoice`. When this summary and the folder docs/code disagree, the **folder docs and code win**.

**Module:** Add-on Service · **Feature slug:** `expenses-e-invoices`
**Status:** ✅ **Implemented** — the Expenses page is the `type=EXPENSE` slice of the shared e-Invoice backend; list + totals + pagination/sort + both XLSX exports + scrape intake + inquiry history are wired (verified 2026-06-02).
**JEX counterpart (read-only reference):** `einv_0001_001` with `einvType=2` (Expenses / inbound — `DOC_TYPE='B'`).

---

## Purpose

Read-side of the **inbound** e-invoice domain: lists every e-invoice the company has received from its suppliers (`BUSA_DOC_MSTR`, `DOC_TYPE='B'`). Same columns as Sales, but the counterparty is the **supplier** and the dominant compliance driver is **input-VAT credit** — a supplier whose tax registration (MST) is suspended/ceased can invalidate the buyer's input-VAT credit (a direct P&L hit).

## Feature overview

- **Shares 100% of the Sales backend.** The only Expenses-specific Java is `BillEInvoiceExportProvider` (`screenId = addon-expenses-einvoices`, `defaultType = EXPENSE`). One controller, one service, one repository, one entity set, one scrape pipeline.
- Same capabilities as Sales (list, multi-currency totals, both XLSX exports, two-phase scrape, inquiry history), driven by `type=EXPENSE`.
- **Detail (re-poll) history is Sales-only** — `GET .../einvoices/detail-history?type=EXPENSE` returns `WBE2002` (legacy parity; the legacy detail-history action rejects Expense).

## Route / menu / permission mapping

| Concern | Value |
|---|---|
| Frontend route | `/addon/expenses-einvoices` |
| Menu permission code (`@menuAuthz` / nav tree) | `addon-einvoices-expenses` |
| View Setting / export screenId | `addon-expenses-einvoices` |
| REST base · resource | `/api/v1/addon` · `/einvoices` (`type=EXPENSE`) |
| Java package | `com.kosign.wecms.domain.einvoice` (shared with Sales) |
| Product gating | F + M only (HAENGSUNG/H omits Add-on) |

> ⚠️ Menu permission code (`addon-einvoices-expenses`) ≠ View-Setting/export screenId (`addon-expenses-einvoices`) — word order is flipped; do not substitute one for the other.

## Endpoints (summary)

Same endpoint set as [Sales](sales-e-invoices.md#endpoints-summary) with `type=EXPENSE`; full contracts in [expenses-e-invoices/interface-specification.md](expenses-e-invoices/interface-specification.md).

| Operation | Endpoint | Notes |
|---|---|---|
| List | `GET /api/v1/addon/einvoices?type=EXPENSE` | `DOC_TYPE='B'` |
| Excel — list | `POST .../einvoices/export` | `screen_id = addon-expenses-einvoices` |
| Excel — with details | `POST .../einvoices/export-details` | shared `EInvoiceDetailExportService` |
| Scrape phase 1 / 2 | `POST .../einvoices/scrape-results` · `.../scrape-detail-results` | `type=EXPENSE` |
| Inquiry history | `GET .../einvoices/history?type=EXPENSE` | — |
| Detail history | `GET .../einvoices/detail-history?type=EXPENSE` | ❌ rejected (`WBE2002`) — Sales-only |

- **`DOC_TYPE` resolution** — `EInvoiceService.resolveDocType`: `EXPENSE`/`BILL`→`'B'` (`BusaDocType.BILL`); `'E'`/`'S'` rejected with **`WBE2002`**. *(The historical "`EXPENSE → 'E'` blocker / `WBE0001`" claim is stale — corrected 2026-06-23.)*

## Current implementation status

✅ Implemented (shared backend). **Remaining gap:** the invoice-**status** filter is client-side on both Sales and Expenses (backend IR-4 status param not implemented — PO decision pending). See [`docs/audits/addon-service-gap-analysis.md`](../../audits/modules/addon-service-gap-analysis.md).

## Detailed docs (source of truth)

[expenses-e-invoices/](expenses-e-invoices/README.md) (deltas) → canonical shared backend [sales-e-invoices/](sales-e-invoices/README.md). **Frontend counterpart:** [`wecms-web/.../expenses-e-invoices/`](../../../../wecms-web/docs/features/addon-service/expenses-e-invoices/README.md).

## Design history

Original long-form rationale: implementation plan `../../planning/addon/expenses-e-invoices-plan.md` _(removed — historical `planning/` folder deleted by project decision)_ (+ the shared Sales plan); the prior verbose version of this file is preserved in git history.

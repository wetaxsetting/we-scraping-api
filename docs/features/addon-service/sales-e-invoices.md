# Add-on Service > Sales e-Invoices — Backend Summary (Root)

> ⚠️ **This document is a high-level feature summary.**
> The detailed source of truth is the module-folder documentation:
> [`docs/features/addon-service/sales-e-invoices/`](sales-e-invoices/README.md)
> (+ the actual implementation code under `com.kosign.wecms.domain.einvoice`). When this summary and the folder docs/code disagree, the **folder docs and code win**.

**Module:** Add-on Service · **Feature slug:** `sales-e-invoices`
**Status:** ✅ **Implemented** — list + multi-currency totals + server-side pagination/sort + two XLSX exports + two-phase scrape intake + GDT inquiry/detail history; unit tests present; frontend wired (verified 2026-06-02).
**JEX counterpart (read-only reference):** `einv_0001_001` with `einvType=1` (Sales / outbound — `DOC_TYPE='I'`).

---

## Purpose

Read-side of the **outbound** e-invoice domain: lists every e-invoice the company has issued to its customers (`BUSA_DOC_MSTR`, `DOC_TYPE='I'`) so the finance team can inspect, reconcile against bank receipts, export/print for tax filings, and refresh from the GDT. Vietnamese tax law (Decree 123/2020 · Circular 78/2021) requires a signed e-invoice per B2B sale.

## Feature overview

- Tenant-scoped list with required date range, 5-column keyword search (IR-5), server-side pagination + sort, and multi-currency footer totals (one row per currency).
- Two XLSX exports — list view and a line-item-expanded "with details" variant.
- Two-phase scrape intake (NXISAS local engine → `BUSA_DOC_MSTR` + child tables) plus counterparty auto-registration.
- GDT inquiry history + detail (re-poll) history popups.
- **Sales and Expenses share one backend** — same controller/service/repository/entity/scrape pipeline; only the `type` discriminator differs (`SALES`→`'I'`, `EXPENSE`→`'B'`). The canonical backend description lives in this Sales folder; Expenses documents only deltas.

## Route / menu / permission mapping

| Concern | Value |
|---|---|
| Frontend route | `/addon/sales-einvoices` |
| Menu permission code (`@menuAuthz` / nav tree) | `addon-einvoices-sales` |
| View Setting / export screenId | `addon-sales-einvoices` |
| REST base · resource | `/api/v1/addon` · `/einvoices` |
| Java package | `com.kosign.wecms.domain.einvoice` |
| Product gating | F + M only (HAENGSUNG/H omits Add-on) |

> ⚠️ The menu permission code (`addon-einvoices-sales`) and the View-Setting/export screenId (`addon-sales-einvoices`) are **different strings with the word order flipped** — do not substitute one for the other.

## Endpoints (summary)

Full request/response contracts: [sales-e-invoices/interface-specification.md](sales-e-invoices/interface-specification.md).

| Operation | Endpoint | `@PreAuthorize` | Legacy mirror |
|---|---|---|---|
| List | `GET /api/v1/addon/einvoices?type=SALES` | `canAccessEInvoice(#p0)` | `einv_0001_001_r001` |
| Excel — list | `POST .../einvoices/export` | `canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')` | `einv_0001_001_r002_excel` |
| Excel — with details | `POST .../einvoices/export-details` | `canAccessAny(...)` | `einv_0001_001_r002_excel` |
| Scrape phase 1 | `POST .../einvoices/scrape-results` | `canAccessAny(...)` | `nxisas_einvoice_c001` |
| Scrape phase 2 | `POST .../einvoices/scrape-detail-results` | `canAccessAny(...)` | `nxisas_einvoice_c002` |
| Inquiry history | `GET .../einvoices/history?type=SALES` | `canAccessEInvoice(#p0)` | `nxisas_einvhis_r001` |
| Detail history | `GET .../einvoices/detail-history?type=SALES` | `canAccessEInvoice(#p0)` | `nxisas_einvhis_dt_r001` |

- **`DOC_TYPE` resolution** — `EInvoiceService.resolveDocType`: `SALES`→`'I'`, `EXPENSE`/`BILL`→`'B'`; `'E'`/`'S'` rejected with **`WBE2002`**.
- **Backing tables** — `BUSA_DOC_MSTR` (+ `BUSA_DOC_DETL`, `EINV_FEE_DETL`, `EINV_TAX_DETL`, `TRCO_ACTV_HSTR`, `EXT_TRAN_CALL_HIS`).

## Current implementation status

✅ Implemented end-to-end (see Status above). **Remaining gaps** (tracked in the folder gap-analysis + [`docs/audits/addon-service-gap-analysis.md`](../../audits/modules/addon-service-gap-analysis.md)):
- Invoice-**status** filter is applied **client-side** — backend returns the raw `EINV_INVOICESTATUS` and has no server `invoiceStatus` param yet (IR-4; PO decision pending).
- "Has-line-items" baseline predicate not ported.
- No dedicated tests for the history / detail-scrape / detail-export services.

## Detailed docs (source of truth)

[sales-e-invoices/](sales-e-invoices/README.md) — business-flow · functional-specification · database-mapping · interface-specification · source-code-index · migration-guide · test-scenarios · refactor-gap-analysis. **Frontend counterpart:** [`wecms-web/.../sales-e-invoices/`](../../../../wecms-web/docs/features/addon-service/sales-e-invoices/README.md).

## Design history

The original long-form design rationale (IR-1…IR-9 locked rules, JEX field-by-field analysis) lives in the implementation plan `../../planning/addon/sales-e-invoices-plan.md` _(removed — historical `planning/` folder deleted by project decision)_; the prior verbose version of this file is preserved in git history.

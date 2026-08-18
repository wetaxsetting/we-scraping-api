# Expenses e-Invoices — Backend Feature (wecms-api)

**Module:** Add-on Service
**Feature slug:** `expenses-e-invoices`
**Refactor screen ID / View Setting `WEB_SVC_ID`:** `addon-expenses-einvoices`
**REST base:** `/api/v1/addon` (resource `/einvoices`, `type=EXPENSE`)
**Java package:** `com.kosign.wecms.domain.einvoice` (shared with Sales)
**JEX counterpart (read-only reference):** `einv_0001_001` with `einvType=2` (Expenses / inbound — `DOC_TYPE='B'`)
**Status:** ✅ **Implemented** — same backend as Sales; the Expenses page is the `type=EXPENSE` slice. List + totals + pagination/sort + both XLSX exports + scrape intake + history are wired (verified 2026-06-02).

> **Expenses shares 100% of the Sales backend.** There is **no Expenses-specific Java** beyond the `BillEInvoiceExportProvider` subclass (`screenId = "addon-expenses-einvoices"`, `defaultType = "EXPENSE"`). One controller, one service, one repository, one entity set, one scrape pipeline. This folder documents **only the deltas**; the canonical backend description lives in [../sales-e-invoices/](../sales-e-invoices/README.md) and is linked from every doc here.

> This folder **supersedes the status claims** in the older single-file spec [../expenses-e-invoices.md](../expenses-e-invoices.md). That spec correctly notes the page is wired but still flags the `resolveDocType EXPENSE→'E'` code bug as the "single most important blocker" — **that is stale**: `resolveDocType` maps `EXPENSE→'B'` and rejects `'E'`/`'S'` with `WBE2002`. The blocker no longer exists.

---

## What the feature does

Expenses e-Invoices lists every e-invoice the company has **received from its suppliers** (inbound side of `BUSA_DOC_MSTR`, `DOC_TYPE='B'`). Same columns as Sales, but the counterparty is the **supplier** and the dominant compliance driver is **input-VAT credit** — a supplier whose MST is suspended/ceased can invalidate the buyer's input-VAT credit, a direct P&L hit.

Operations are identical to Sales with `type=EXPENSE`:

| Operation | Endpoint | Service |
|---|---|---|
| List | `GET /api/v1/addon/einvoices?type=EXPENSE` | `EInvoiceService#list` |
| Excel — list | `POST .../einvoices/export` (`screen_id=addon-expenses-einvoices`) | `BillEInvoiceExportProvider` |
| Excel — with details | `POST .../einvoices/export-details` | `EInvoiceDetailExportService` |
| Scrape phase 1 / 2 | `POST .../einvoices/scrape-results` / `.../scrape-detail-results` (`type=EXPENSE`) | `EInvoiceScrapeService` / `EInvoiceDetailScrapeService` |
| GDT inquiry history | `GET .../einvoices/history?type=EXPENSE` | `EInvoiceHistoryService#list` |

> **Detail (re-poll) history is Sales-only.** `GET .../einvoices/detail-history?type=EXPENSE` returns `WBE2002`. Expenses has no detail-history endpoint.

---

## Documents in this folder

Each doc states only the Expenses delta and links the Sales canonical section:

| Doc | Focus (delta only) |
|---|---|
| [business-flow.md](business-flow.md) | `DOC_TYPE='B'`; counterparty = seller; `TASK_CD='B003'` / `GRP_CD='B'`; detail-history rejected. |
| [functional-specification.md](functional-specification.md) | `DOC_TYPE='B'` resolution; supplier compliance angle; detail-history Sales-only. |
| [database-mapping.md](database-mapping.md) | Same tables; `DOC_TYPE='B'` rows; `addon-expenses-einvoices` View Setting seed (V52/V54). |
| [interface-specification.md](interface-specification.md) | `type=EXPENSE` request deltas; `screen_id=addon-expenses-einvoices` export; detail-history rejection. |
| [source-code-index.md](source-code-index.md) | Points at the shared package + the `BillEInvoiceExportProvider` subclass. |
| [migration-guide.md](migration-guide.md) | `einv_0001_001` with `einvType=2`; shared mapping. |
| [test-scenarios.md](test-scenarios.md) | Expenses-specific assertions on the shared tests. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Area/Legacy/Refactor/Status table for the Expenses variant. |

---

## Related docs (kept in place — cross-references)

- Design-history feature spec: [../expenses-e-invoices.md](../expenses-e-invoices.md)
- API contract reference: [../../../references/api-spec/addon/expenses-e-invoices-api.md](../../../references/api-spec/addon/expenses-e-invoices-api.md)
- DB design reference: [../../../references/database/addon/expenses-e-invoices-db.md](../../../references/database/addon/expenses-e-invoices-db.md)
- JEX → Spring migration (both variants): [../../../references/migration/sales-and-expense-einvoices.md](../../../references/migration/sales-and-expense-einvoices.md)
- Scraping parity audit (both variants): [../../../audits/sales-expense-einvoices-scraping-parity.md](../../../audits/legacy-parity/sales-expense-einvoices-scraping-parity.md)
- Implementation plan: ../../../planning/addon/expenses-e-invoices-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- **Canonical backend (shared):** [../sales-e-invoices/README.md](../sales-e-invoices/README.md)
- **Frontend counterpart:** [../../../../../wecms-web/docs/features/addon-service/expenses-e-invoices/README.md](../../../../../wecms-web/docs/features/addon-service/expenses-e-invoices/README.md)

**JEX functional source of truth (read-only):** `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml` (the `einvType=2` slice).

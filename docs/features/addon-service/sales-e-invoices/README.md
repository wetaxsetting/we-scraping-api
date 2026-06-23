# Sales e-Invoices — Backend Feature (wecms-api)

**Module:** Add-on Service
**Feature slug:** `sales-e-invoices`
**Refactor screen ID / View Setting `WEB_SVC_ID`:** `addon-sales-einvoices`
**REST base:** `/api/v1/addon` (resource `/einvoices`)
**Java package:** `com.kosign.wecms.domain.einvoice`
**JEX counterpart (read-only reference):** `einv_0001_001` with `einvType=1` (Sales / outbound — `DOC_TYPE='I'`)
**Status:** ✅ **Implemented** — list + multi-currency totals + server-side pagination/sort + two XLSX exports (list & detail-expansion) + scrape-intake (phase 1 + phase 2) + GDT inquiry history + detail-history are all implemented backend-side; unit tests present; frontend wired against live endpoints (verified 2026-06-02).

> This folder is the consolidated, **code-accurate** home for the Sales e-Invoices backend feature. It **supersedes the status claims** in the older single-file spec [../sales-e-invoices.md](../sales-e-invoices.md) (which describes the feature as "static UI only — backend skeleton not wired end-to-end" and reports a `resolveDocType EXPENSE→'E'` code bug — **both are stale**: the list/export/scrape paths are wired, and `resolveDocType` correctly maps `EXPENSE→'B'`). The older spec and the scattered references are kept in place as design-history / deep references and cross-linked below.

> **Sales and Expenses share one backend.** One controller, one service, one repository, one entity set, one scrape pipeline, one export base class — the only difference is the `type` discriminator (`SALES` → `DOC_TYPE='I'`, `EXPENSE` → `DOC_TYPE='B'`). This Sales folder is the canonical backend description; the [expenses-e-invoices/](../expenses-e-invoices/README.md) folder documents only the deltas and links back here.

---

## What the feature does

Sales e-Invoices lists every e-invoice the company has **issued to its customers** (outbound side of `BUSA_DOC_MSTR`, `DOC_TYPE='I'`). Each row carries the issue date, e-invoice serial, buyer (Vietnamese tax code MST + name), currency, total / supply / tax amounts, and the GDT-side invoice status. The page renders multi-currency footer totals (one row per currency seen in the result set) and exports to XLSX (list view, and a line-item-expanded "with details" variant).

Vietnamese tax law (Decree 123/2020 / Circular 78/2021) requires every B2B sale to issue a digitally-signed e-invoice transmitted to the GDT (General Department of Taxation — Tổng cục Thuế). WeCMS is the operational hub where the finance team inspects, reconciles, exports, and refreshes those invoices.

Operations backing the feature (all under the shared `EInvoiceController`):

| Operation | Endpoint | Service | Legacy mirror |
|---|---|---|---|
| List (filter + sort + paginate + multi-ccy totals) | `GET /api/v1/addon/einvoices?type=SALES` | `EInvoiceService#list` | `einv_0001_001_r001` |
| Excel — list view | `POST .../einvoices/export` | `ExcelExportService` + `SalesEInvoiceExportProvider` | `einv_0001_001_r002_excel` (list cols) |
| Excel — with details (line/fee/tax expansion) | `POST .../einvoices/export-details` | `EInvoiceDetailExportService` | `einv_0001_001_r002_excel` (detail expansion) |
| Scrape intake — phase 1 (invoice list) | `POST .../einvoices/scrape-results` | `EInvoiceScrapeService` | `nxisas_einvoice_c001` |
| Scrape intake — phase 2 (invoice detail) | `POST .../einvoices/scrape-detail-results` | `EInvoiceDetailScrapeService` (+ `EInvoiceTrcoAutoRegistrar`) | `nxisas_einvoice_c002` |
| GDT inquiry history | `GET .../einvoices/history?type=SALES` | `EInvoiceHistoryService#list` | `nxisas_einvhis_r001` |
| Detail (re-poll) history | `GET .../einvoices/detail-history?type=SALES` | `EInvoiceHistoryService#detailHistory` | `nxisas_einvhis_dt_r001` |

The scrape-intake endpoints are the WeCMS-side ingestion of GDT data normally driven by the local NXISAS engine; the live GDT/NXISAS bridge itself is out of refactor R&D scope (see [refactor-gap-analysis.md](refactor-gap-analysis.md)).

---

## Documents in this folder

| Doc | Focus |
|---|---|
| [business-flow.md](business-flow.md) | Request → service → repository → DB flow for each operation; baseline-filter chain; scrape upsert + counterparty auto-registration sequence; tenant scope. |
| [functional-specification.md](functional-specification.md) | Business rules: scope, `DOC_TYPE` resolution, baseline filters, keyword whitelist, status handling, pagination, sort, multi-currency totals, scrape branch rules, error codes. |
| [database-mapping.md](database-mapping.md) | `BUSA_DOC_MSTR` + detail tables (`BUSA_DOC_DETL`, `EINV_TAX_DETL`, `EINV_FEE_DETL`) + `TRCO_ACTV_HSTR` + audit table, columns, PK/index, Flyway migrations, View Setting seed. |
| [interface-specification.md](interface-specification.md) | REST contracts — request/response wire shapes (snake_case), validation, error envelopes, examples. |
| [source-code-index.md](source-code-index.md) | Every backend source file with a one-line role + key line references. |
| [migration-guide.md](migration-guide.md) | JEX `einv_0001_001` WSVC/IDO/JSP → Spring mapping; field-by-field; divergences; migration status. |
| [test-scenarios.md](test-scenarios.md) | Existing unit tests + manual / integration scenarios. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Completed coverage vs JEX, remaining gaps, deliberate divergences, the Area/Legacy/Refactor/Status table. |

---

## Related docs (kept in place — cross-references)

**Existing scattered docs (deep reference, preserved):**
- Design-history feature spec: [../sales-e-invoices.md](../sales-e-invoices.md)
- API contract reference: [../../../references/api-spec/addon/sales-e-invoices-api.md](../../../references/api-spec/addon/sales-e-invoices-api.md)
- DB design reference: [../../../references/database/addon/sales-e-invoices-db.md](../../../references/database/addon/sales-e-invoices-db.md)
- JEX → Spring migration (service-level, both variants): [../../../references/migration/sales-and-expense-einvoices.md](../../../references/migration/sales-and-expense-einvoices.md)
- Scraping parity audit (both variants): [../../../audits/sales-expense-einvoices-scraping-parity.md](../../../audits/sales-expense-einvoices-scraping-parity.md)
- Implementation plan (IR-1 … IR-9 locked rules): ../../../planning/addon/sales-e-invoices-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- Archived combined spec: ../../../archive/features/sales-and-expense-einvoices.md _(removed — historical `archive/` folder deleted by project decision)_

**Sister feature (shares this backend):** [../expenses-e-invoices/README.md](../expenses-e-invoices/README.md)

**Frontend counterpart:** [../../../../../wecms-web/docs/features/addon-service/sales-e-invoices/README.md](../../../../../wecms-web/docs/features/addon-service/sales-e-invoices/README.md)

**JEX functional source of truth (read-only):** `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml`, `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp`, `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp`

# Expenses e-Invoices — Backend Source Code Index

**There is no Expenses-specific backend package.** Every source file is the shared `com.kosign.wecms.domain.einvoice` set documented in the canonical [Sales source-code-index.md](../sales-e-invoices/source-code-index.md). The Expenses page is the `type=EXPENSE` / `DOC_TYPE='B'` slice through the same controller, service, repository, entities, scrape pipeline, and export base class.

## The only Expenses-specific file

| File | Role |
|---|---|
| [src/main/java/com/kosign/wecms/domain/einvoice/export/BillEInvoiceExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/export/BillEInvoiceExportProvider.java) | `ExportRowProvider` subclass — `screenId() = "addon-expenses-einvoices"` (L25), `defaultType() = "EXPENSE"` (L31). Inherits all field-mapping / subheader logic from the abstract [EInvoiceExportProvider](../../../../src/main/java/com/kosign/wecms/domain/einvoice/export/EInvoiceExportProvider.java). The Sales twin is `SalesEInvoiceExportProvider`. |

## Everything else (shared — see Sales)

Controller, `EInvoiceService`, `EInvoiceHistoryService`, `EInvoiceScrapeService`, `EInvoiceDetailScrapeService`, `EInvoiceTrcoAutoRegistrar`, `EInvoiceDetailExportService`, `EInvoiceSortField`, `EInvoiceDetailHistorySortField`, `BusaDocMstrRepository` (+ detail repos), `BusaDocMstr` / `BusaDocDetl` / `EinvTaxDetl` / `EinvFeeDetl` / `TrcoActvHstr` entities, and all DTOs are documented in [../sales-e-invoices/source-code-index.md](../sales-e-invoices/source-code-index.md).

**Expenses-relevant behaviour in the shared code:**

- `EInvoiceService.resolveDocType("EXPENSE")` → `'B'` (L205-221).
- `EInvoiceTrcoAutoRegistrar` resolves the **seller** block as the counterparty for `EXPENSE`, writes `TRCO_ACTV_HSTR` with `DV_CD='B'` / `TASK_CD='B003'`, and seeds `PGRO_BSAC_LNKN_INFM` with `GRP_CD='B'`.
- `EInvoiceHistoryService#list` filters `EXT_TRAN_CALL_HIS` on `SVC_ID = GDT_INQUIRY_EXPENSES` for `type=EXPENSE`.
- `EInvoiceHistoryService#detailHistory` rejects `type=EXPENSE` with `WBE2002` (Sales-only).

## Tests

The shared tests (`EInvoiceServiceTest`, `EInvoiceScrapeServiceTest`, `EInvoiceTrcoAutoRegistrarTest`, `EInvoiceExportProviderTest`) exercise the `DOC_TYPE='B'` path too — see [../sales-e-invoices/test-scenarios.md](../sales-e-invoices/test-scenarios.md) and [test-scenarios.md](test-scenarios.md).

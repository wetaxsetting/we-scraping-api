# Expenses e-Invoices — Backend Test Scenarios

The shared `com.kosign.wecms.domain.einvoice` tests exercise the `DOC_TYPE='B'` path too — see the canonical [Sales test-scenarios.md](../sales-e-invoices/test-scenarios.md). Expenses-specific assertions are below.

## 1. Expenses-specific automated assertions

| Test | Expenses assertion |
|---|---|
| `EInvoiceServiceTest` | `resolveDocType("EXPENSE")` → `'B'` and `resolveDocType("BILL")` → `'B'`; `'E'` → `WBE2002`. |
| `EInvoiceTrcoAutoRegistrarTest` | For `EXPENSE`, the **seller** block is the counterparty; `TRCO_ACTV_HSTR` written with `DV_CD='B'` / `TASK_CD='B003'`; `PGRO_BSAC_LNKN_INFM` with `GRP_CD='B'`. |
| `EInvoiceExportProviderTest` | `BillEInvoiceExportProvider.screenId()` = `addon-expenses-einvoices`, `defaultType()` = `EXPENSE`. |

Run: `./gradlew test --tests "*EInvoice*"`.

## 2. Manual / integration scenarios (Expenses)

1. **List** — `GET /einvoices?type=EXPENSE&from=20260101&to=20260131`; verify only `DOC_TYPE='B'` rows, supplier in `trco_cmnm`/`trco_tax_id`, per-currency totals.
2. **Excel — list** — `POST /einvoices/export` with `screen_id=addon-expenses-einvoices`; verify `BillEInvoiceExportProvider` resolves and the `BUYER` column header reads "Supplier" only on the frontend (export header text is frontend-supplied).
3. **Scrape phase-2** — POST `scrape-detail-results` (`type=EXPENSE`); verify the **supplier** is auto-registered (`DV_CD='B'`).
4. **Inquiry history** — `GET /einvoices/history?type=EXPENSE` → `SVC_ID=GDT_INQUIRY_EXPENSES` rows.
5. **Detail-history rejected** — `GET /einvoices/detail-history?type=EXPENSE` → `WBE2002`.
6. **Seed presence** — confirm DEV seed (V52) carries `DOC_TYPE='B'` rows for tenant `VN_VNM0004_000001` so the list is non-empty in demos.

All other scenarios mirror [Sales §3](../sales-e-invoices/test-scenarios.md#3-manual--integration-scenarios) with `type=EXPENSE`.

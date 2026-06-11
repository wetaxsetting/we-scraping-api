# Expenses e-Invoices — Interface Specification (REST)

Identical endpoints and wire shapes to Sales — see the canonical [Sales interface-specification.md](../sales-e-invoices/interface-specification.md). The only differences are the `type=EXPENSE` discriminator, the export `screen_id`, and that detail-history is Sales-only.

---

## Deltas vs Sales

| Endpoint | Expenses delta |
|---|---|
| `GET /api/v1/addon/einvoices` | `type=EXPENSE` (resolves to `DOC_TYPE='B'`). Response shape identical; `trco_cmnm`/`trco_tax_id` carry the supplier. |
| `POST .../einvoices/export` | `ExcelExportRequest.screen_id = "addon-expenses-einvoices"` → resolved by `BillEInvoiceExportProvider`. Filters carry `type=EXPENSE`. |
| `POST .../einvoices/export-details` | `type=EXPENSE` in filters; same `EInvoiceDetailExportService`. |
| `POST .../einvoices/scrape-results` | `type` `@Pattern "SALES\|EXPENSE\|BILL"` → `EXPENSE`. |
| `POST .../einvoices/scrape-detail-results` | counterparty resolved from the seller block. |
| `GET .../einvoices/history` | `type=EXPENSE` → filters `EXT_TRAN_CALL_HIS` on `SVC_ID = GDT_INQUIRY_EXPENSES`. |
| `GET .../einvoices/detail-history` | **`type=EXPENSE` → `WBE2002`** — Sales-only endpoint. |

## Example — list (Expenses)

```
GET /api/v1/addon/einvoices?type=EXPENSE&from=20260101&to=20260131&page=0&size=10
```
```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "rows": [
      {
        "doc_id": "B-2026-000045",
        "disp_doc_id": "2C26SUPP-1101",
        "issu_dt": "20260118",
        "issu_tm": "141005",
        "einv_invoicesymbol": "2C26SUPP",
        "trco_cmnm": "CÔNG TY TNHH NHÀ CUNG CẤP XYZ",
        "trco_tax_id": "0312345678",
        "trco_trsc_ccy_cd": "VND",
        "totl_amt": "5500000",
        "sply_amt_sum": "5000000",
        "tax_amt_sum": "500000",
        "einv_invoicestatus": "Hóa đơn mới",
        "einv_invoicestatus_bef": null,
        "prov_stts": "OP",
        "due_dt": "20260218",
        "over_due_days": "0"
      }
    ],
    "total_count": 64,
    "total_pages": 7,
    "page_number": 0,
    "page_size": 10,
    "totals": [ { "ccy_cd": "VND", "count": 64, "amount": "352000000", "tax_amount": "32000000", "pretax_amount": "320000000" } ]
  }
}
```

All envelope rules, validation, and error codes are identical to [Sales § Common rules](../sales-e-invoices/interface-specification.md#common-rules).

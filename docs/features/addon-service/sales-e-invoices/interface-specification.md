# Sales e-Invoices — Interface Specification (REST)

All endpoints under `@RequestMapping("/api/v1/addon")` in [EInvoiceController.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java). Wire JSON is **lowercase snake_case** (global Jackson `SNAKE_CASE`). Every response uses the standard envelope `{ status: { error, code, message }, data }` (errors add `timestamp`, `path`, `trace_id`). Auth: JWT Bearer on every endpoint; tenant resolved server-side from the token. Full contract detail also in [../../../references/api-spec/addon/sales-e-invoices-api.md](../../../references/api-spec/addon/sales-e-invoices-api.md).

> The same controller serves Sales (`type=SALES`) and Expenses (`type=EXPENSE`) — the only difference is the discriminator and the resolved `DOC_TYPE`. See [Expenses interface-specification.md](../expenses-e-invoices/interface-specification.md) for the EXPENSE deltas.

---

## 1. `GET /api/v1/addon/einvoices` — list

**Query params:**

| Param | Type | Required | Notes |
|---|---|---|---|
| `type` | string | ✅ `@NotBlank` | `SALES` / `EXPENSE` (`BILL` alias accepted; `E`/`S` → `WBE2002`) |
| `from` | string | ✅ `@Pattern ^\d{8}$` | `YYYYMMDD`, applied to `ISSU_DT` |
| `to` | string | ✅ `@Pattern ^\d{8}$` | `YYYYMMDD`; `from ≤ to` |
| `keyword` | string | — | 5-column OR partial match |
| `page` | int | — | zero-based; default `0` |
| `size` | int | — | default `10` (when `<=0`); clamped to `100` |
| `sortField` | string | — | `DATE_TIME` / `INVOICE_NO` / `BUYER` / `CURRENCY` / `TOTAL` / `STATUS` / `TAX` / `PRETAX` |
| `sortDirection` | string | — | `ASC` / `DESC` |

**Response `data` — `EInvoiceListResponse`:**

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "rows": [
      {
        "doc_id": "I-2026-000123",
        "disp_doc_id": "1C26TLM-0405",
        "issu_dt": "20260115",
        "issu_tm": "093215",
        "einv_invoicesymbol": "1C26TLM",
        "trco_cmnm": "CÔNG TY TNHH KHÁCH HÀNG ABC",
        "trco_tax_id": "0301234567",
        "trco_trsc_ccy_cd": "VND",
        "totl_amt": "11000000",
        "sply_amt_sum": "10000000",
        "tax_amt_sum": "1000000",
        "einv_invoicestatus": "Hóa đơn mới",
        "einv_invoicestatus_bef": null,
        "prov_stts": "OP",
        "due_dt": "20260214",
        "over_due_days": "0"
      }
    ],
    "total_count": 128,
    "total_pages": 13,
    "page_number": 0,
    "page_size": 10,
    "totals": [
      { "ccy_cd": "VND", "count": 120, "amount": "1320000000", "tax_amount": "120000000", "pretax_amount": "1200000000" },
      { "ccy_cd": "USD", "count": 8, "amount": "44000", "tax_amount": "4000", "pretax_amount": "40000" }
    ]
  }
}
```

`einv_invoicestatus` is the **raw** GDT locale string (frontend regex-derives the badge tint — IR-4). `issu_tm` is a refactor proxy sliced from `REG_DTM[8..14]` (legacy has no issue-time field). `totals` is computed unpaged so it's stable across pagination.

**Errors:** `WBE2002` (bad `type`), `WBE_VALIDATION` (missing/malformed `from`/`to`).

---

## 2. `POST /api/v1/addon/einvoices/export` — list XLSX

**Request — shared `ExcelExportRequest`:** carries `screen_id` (`addon-sales-einvoices`), `dym_area_id` (`default`), `file_name`, `title`, `columns[]`, `filters` (`type`/`from`/`to`/`keyword`), `sort` (`field`/`direction`).
**Response:** HTTP 200, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename*=UTF-8''…`. Allowed columns: `DATE_TIME`, `INVOICE_NO`, `BUYER`, `CURRENCY`, `TOTAL`, `STATUS`, `TAX`, `PRETAX`. Server-computed per-currency subheader (`"<n> invoice(s) · Total: … · Tax: … · Pre-tax: …"`).
**Errors:** `WBE2010` (unknown screenId), `WBE2011` (>50k rows), `WBE2013` (no exportable columns).

## 3. `POST /api/v1/addon/einvoices/export-details` — detail-expansion XLSX

Same request shape. The workbook expands each invoice into header + per-line (`LINE_TYPE` = `PRODUCT`/`FEE`/`TAX`) rows from `BUSA_DOC_DETL` / `EINV_FEE_DETL` / `EINV_TAX_DETL`. Served by `EInvoiceDetailExportService` (own fixed wide column set; not the per-screen provider registry).

---

## 4. `POST /api/v1/addon/einvoices/scrape-results` — phase-1 intake

**Request — `EInvoiceScrapeRequest`:**
```json
{
  "type": "SALES",
  "from_dt": "20260101",
  "to_dt": "20260131",
  "rslt_cd": "00000000",
  "rslt_msg": "Success",
  "issuer_tax_code": "0309999999",
  "engine_vendor": "COOCON",
  "engine_version": "1.0.0",
  "invoices": [ { "numeric_symbol": "1", "invoice_symbol": "1C26TLM", "invoice_no": "405", "invoice_date": "20260115", "total_amt": "11000000", "...": "..." } ]
}
```
`type` `@Pattern "SALES|EXPENSE|BILL"`, `rslt_cd` `@NotBlank`. **Response — `EInvoiceScrapeResult`:** `{ "saved_count": 5, "updated_count": 2, "skipped_count": 0, "mod_dtm": "20260131101500" }`.

## 5. `POST /api/v1/addon/einvoices/scrape-detail-results` — phase-2 intake

**Request — `EInvoiceDetailScrapeRequest`:** full invoice detail (identity fields + addresses + bank accounts + signatures + QR + nested `products[]` / `fees[]` / `taxes[]`). **Response — `EInvoiceDetailScrapeResult`:** same shape as phase-1 result. Side effects: rewrites detail tables + auto-registers the counterparty (`TRCO_INFM` / `TRCO_ACCT_INFO` / `PGRO_BSAC_LNKN_INFM` / `TRCO_ACTV_HSTR`).

---

## 6. `GET /api/v1/addon/einvoices/history` — GDT inquiry history

**Query params:** `type` (optional `SALES`/`EXPENSE`), `from` (req `YYYYMMDD`), `to` (req `YYYYMMDD`), `status` (optional `success`/`fail`).
**Response — `EInvoiceHistoryResponse`:** `{ "rows": [ { "reg_dtm", "type", "svc_id", "start_date", "end_date", "result", "rslt_cd", "err_message" } ] }`. Reads `EXT_TRAN_CALL_HIS`.

## 7. `GET /api/v1/addon/einvoices/detail-history` — re-poll history (Sales only)

**Query params:** `type` (req — **`SALES` only**; Expense → `WBE2002`), `from` (req `YYYYMMDD`), `to` (req `YYYYMMDD`), `mod_dtm` (req `@Pattern ^\d{14}$`), `sortField` / `sortDirection` (optional — `DOC_ID`/`ISSU_DT`/`TRCO_CMNM`/`TOTL_AMT`/`EINV_INVOICESTATUS`).
**Response — `EInvoiceDetailHistoryResponse`:** `{ "rows": [ { "doc_id", "issu_dt", "trco_cmnm", "totl_amt", "einv_invoice_status", "hand_reg_yn", "disp_doc_id" } ] }`. Lists masters whose `MOD_DTM` matches the given re-poll batch timestamp (`HAND_REG_YN='N'`).

---

## Common rules

- Business outcomes return **HTTP 200** with `status.error = true`; clients read `status.error` / `status.code`.
- `BIZ_KEY` is **never** accepted on the wire — always from the JWT.
- Sort fields resolve through the whitelist enums; unknown/blank → default ORDER BY, never a 4xx.

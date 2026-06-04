# Expenses e-Invoices — Backend Business Flow

The control flow is **identical to Sales** — same controller, service, repository, scrape pipeline, and export base class. See the canonical [Sales business-flow.md](../sales-e-invoices/business-flow.md) for the full diagrams. Only the deltas are below.

---

## Deltas vs Sales

| Step | Sales | Expenses |
|---|---|---|
| `type` discriminator | `SALES` | `EXPENSE` |
| `resolveDocType` result | `'I'` | `'B'` (`BusaDocType.BILL`) |
| Baseline filter | `DOC_TYPE = 'I'` | `DOC_TYPE = 'B'` (every other baseline filter identical — `PROV_STTS NOT IN ('VD','EE')`, `HAND_REG_YN <> 'Y'`, `ISSU_DT BETWEEN`, 5-column keyword) |
| Counterparty column meaning | buyer | supplier (same field `TRCO_CMNM`/`TRCO_TAX_ID`) |
| Phase-2 counterparty resolution | buyer block | **seller block** |
| `TRCO_ACTV_HSTR` activity | `DV_CD='S'`, `TASK_CD='S003'` | `DV_CD='B'`, `TASK_CD='B003'` |
| `PGRO_BSAC_LNKN_INFM` group | `GRP_CD='S'` | `GRP_CD='B'` |
| GDT inquiry-history SVC_ID | `GDT_INQUIRY_SALES` | `GDT_INQUIRY_EXPENSES` |
| Export provider / screenId | `SalesEInvoiceExportProvider` / `addon-sales-einvoices` | `BillEInvoiceExportProvider` / `addon-expenses-einvoices` |
| Detail (re-poll) history | available | **rejected → `WBE2002`** (Sales-only endpoint) |

---

## List flow (Expenses)

```
[Browser: EInvoicesClient type="EXPENSE"]
   │ GET /einvoices?type=EXPENSE&from=&to=&keyword=&page=&size=&sortField=&sortDirection=
   ▼
[EInvoiceService#list] → resolveDocType("EXPENSE") = 'B'
   ▼
[BusaDocMstrRepository#findFiltered(bizKey, 'B', from, to, kwPattern, pageable)]   (same WHERE chain as Sales)
[BusaDocMstrRepository#aggregateByCurrency(bizKey, 'B', from, to, kwPattern)]       (unpaged per-currency totals)
   ▼
EInvoiceListResponse { rows, total_count, total_pages, page_number, page_size, totals }
```

The multi-currency totals, pagination, sort whitelist, and the `issu_tm` proxy behave exactly as in [Sales §1](../sales-e-invoices/business-flow.md#1-list--get-apiv1addoneinvoicestypesales).

## Scrape phase-2 (Expenses) — counterparty = supplier

```
[EInvoiceDetailScrapeService#saveScrapedDetails(request type=EXPENSE)]
 …rewrite BUSA_DOC_DETL / EINV_FEE_DETL / EINV_TAX_DETL…
 EInvoiceTrcoAutoRegistrar.register(invoice, EXPENSE):
    counterparty = seller block (supplier name + tax id + bank accounts)
    create/reuse TRCO_INFM; seed PGRO_BSAC_LNKN_INFM (GRP_CD='B'); upsert TRCO_ACCT_INFO
    write TRCO_ACTV_HSTR (DV_CD='B', TASK_CD='B003')
```

Audit trail tables are the same as Sales ([Sales business-flow §Audit](../sales-e-invoices/business-flow.md#audit-trail-summary)).

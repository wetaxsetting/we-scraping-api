# Sales e-Invoices — JEX → Spring Migration Guide

How the legacy JEX `einv_0001_001` services (`einvType=1`, Sales) map onto the Spring Boot implementation. Narrative migration history (both variants) is in [../../../references/migration/sales-and-expense-einvoices.md](../../../references/migration/sales-and-expense-einvoices.md). JEX is read-only functional reference under `WECMS DEV/`.

---

## 1. Service mapping

| JEX WSVC / action | Spring endpoint | Service method |
|---|---|---|
| `einv_0001_001_r001` (list read) | `GET /api/v1/addon/einvoices?type=SALES` | `EInvoiceService#list` |
| `einv_0001_001_r002_excel` (list cols) | `POST .../einvoices/export` | `SalesEInvoiceExportProvider` + `ExcelExportService` |
| `einv_0001_001_r002_excel` (detail expansion) | `POST .../einvoices/export-details` | `EInvoiceDetailExportService` |
| `nxisas_einvoice_c001` (invoice-list intake) | `POST .../einvoices/scrape-results` | `EInvoiceScrapeService` |
| `nxisas_einvoice_c002` (invoice-detail intake) | `POST .../einvoices/scrape-detail-results` | `EInvoiceDetailScrapeService` + `EInvoiceTrcoAutoRegistrar` |
| `nxisas_einvhis_r001` (inquiry history) | `GET .../einvoices/history?type=SALES` | `EInvoiceHistoryService#list` |
| `nxisas_einvhis_dt_r001` (re-poll history) | `GET .../einvoices/detail-history?type=SALES` | `EInvoiceHistoryService#detailHistory` |
| `einv_0001_001_u001` (issue/replace/adjust/cancel) | — | **not ported** (write flow out of scope) |

## 2. Legacy assets (read-only reference)

| Asset | Path under `WECMS DEV/` |
|---|---|
| WSVC (read) | `WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml` |
| WSVC (page / excel / write) | `WSVC.einv_0001_001.xml`, `WSVC.einv_0001_001_r002_excel.xml`, `WSVC.einv_0001_001_u001.xml` |
| IDO (paged read) | `IDO.BUSA_DOC_MSTR_R022.xml` (~80 cols + joins) |
| IDO (detail tables) | `IDO.EINV_TAX_DETL_*.xml`, `IDO.EINV_FEE_DETL_*.xml` |
| Action JSP (read) | `WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp` |
| View JSP | `WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` |
| TS | `WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts` |
| Search helper | `WECMS/src/wabooks/helper/SearchHelper.java#L89` (View-Setting-driven OR walk) |
| Enums | `WECMS/src/wabooks/enums/BusaDocType.java`, `ProvStts.java` |
| Live DEV URL | `https://dev.wecms.vn/einv_0001_001.act?einvType=1` |

## 3. WSVC contract → wire field mapping

Legacy `WSVC.einv_0001_001_r001.xml` is UPPER_SNAKE; the refactor wire is **lowercase snake_case** (CTO directive 2026-05-08).

| Legacy WSVC field (in) | Refactor wire (in) | Notes |
|---|---|---|
| `DOC_TYPE` (`I`/`B`) | `type` (`SALES`/`EXPENSE`) | controller owns the `type → DOC_TYPE` translation (`resolveDocType`) |
| `FROM_DT` / `TO_DT` | `from` / `to` | required `YYYYMMDD` |
| `SRCH_WD` | `keyword` | 5-column subset (was View-Setting-driven) |
| `PAGE_NO` / `PAGE_PER_CNT` | `page` (zero-based) / `size` (clamp 100) | |
| `einv_invoiceStatus` | (not yet a wire filter) | frontend post-filters by regex (IR-4) |

| Legacy `REC` field (out) | Refactor wire (out) | Notes |
|---|---|---|
| `DOC_ID` / `DISP_DOC_ID` | `doc_id` / `disp_doc_id` | |
| `ISSU_DT` | `issu_dt` | `YYYYMMDD` |
| (none) | `issu_tm` | **refactor-only proxy** from `REG_DTM[8..14]` |
| `einv_invoiceSymbol` | `einv_invoicesymbol` | |
| `TRCO_CMNM` / `TRCO_TAX_ID` | `trco_cmnm` / `trco_tax_id` | |
| `TRCO_TRSC_CCY_CD` | `trco_trsc_ccy_cd` | |
| `TOTL_AMT` / `SPLY_AMT_SUM` / `TAX_AMT_SUM` | `totl_amt` / `sply_amt_sum` / `tax_amt_sum` | raw decimals |
| `einv_invoiceStatus` / `einv_invoicestatus_bef` | `einv_invoicestatus` / `einv_invoicestatus_bef` | raw locale string (no `wb_get_invoice_status`) |
| `PROV_STTS` / `DUE_DT` / `OVER_DUE_DAYS` | `prov_stts` / `due_dt` / `over_due_days` | |
| `TOT_VND_*` / `TOT_USD_*` | `totals[]` (per-currency) | computed unpaged; legacy per-status totals not ported |

The legacy `COLOR` (memo-sticker class), `MEMO`, and `INV_TYPE` (Commercial/Tax classifier) are intentionally **not** on the refactor wire. `INV_TYPE` is mapped on the entity for the scrape path but never serialised (IR-7).

## 4. Behavioural translation

| Legacy behaviour | Refactor behaviour |
|---|---|
| `WHERE biz_key = ?` from session | `TenantContext.currentBizKey()` (JWT) |
| `PROV_STTS NOT IN ('VD','EE')` baseline | identical (`findFiltered`/`aggregateByCurrency`) |
| `HAND_REG_YN = 'N'` baseline | `HAND_REG_YN <> 'Y'` (equivalent, default `'Y'`) |
| `(DOC_ID,BIZ_KEY) IN (… BUSA_DOC_DETL)` "has line items" | **not ported** (gap — [refactor-gap-analysis §2](refactor-gap-analysis.md)) |
| `PROV_STTS=OP → IN ('OP','PP','OD')` expansion | **not ported** (gap) |
| `SearchHelper.getSearchColumnDynamic` (16-col View-Setting walk + `wb_get_translate_text`) | fixed 5-col OR, `ESCAPE '!'`, no accent fold (IR-5) |
| `SortHelper` View-Setting sort | `EInvoiceSortField` whitelist + `ISSU_DT DESC, REG_DTM DESC` default |
| `wb_get_invoice_status(value, LANG_CD)` localisation | **not ported** — raw string returned; frontend regex tints (IR-4) |
| `nxisas_einvoice_c001/c002` GDT pull + intake | intake endpoints only (`scrape-results` / `scrape-detail-results`); the GDT/NXISAS pull is out of R&D scope |
| `einv_0001_001_r002_excel` | shared `common/export/` pipeline (list) + `EInvoiceDetailExportService` (detail) |

## 5. Deliberate divergences

1. **Raw `einv_invoicestatus`** on the wire (no `wb_get_invoice_status` port) — frontend localises + tints via regex.
2. **`issu_tm` proxy** from `REG_DTM[8..14]` — legacy has no issue-time field.
3. **Multi-currency totals only** (no legacy per-status totals).
4. **5-column keyword** instead of the View-Setting-driven 16-column walk.
5. **Counterparty auto-registration** (`EInvoiceTrcoAutoRegistrar`) replicates the legacy phase-2 side effect into Spring services rather than DB triggers.
6. **Scrape intake decoupled from the GDT pull** — refactor accepts already-fetched data; live NXISAS bridge deferred.

## 6. PROD active-table scope

All tables touched (`BUSA_DOC_MSTR`, `BUSA_DOC_DETL`, `EINV_TAX_DETL`, `EINV_FEE_DETL`, `TRCO_ACTV_HSTR`, `EXT_TRAN_CALL_HIS`, `TRCO_INFM`/`TRCO_ACCT_INFO`/`PGRO_BSAC_LNKN_INFM`) are in the PROD Active Table List — in scope per the Refactoring Enforcement Rule.

## 7. Migration status — ✅ list/export/scrape/history implemented

List, both exports, both scrape phases, and both history reads are implemented and frontend-wired. The legacy write flow (`einv_0001_001_u001`) and the live GDT/NXISAS pull are intentionally out of scope. Remaining parity gaps are in [refactor-gap-analysis.md](refactor-gap-analysis.md).

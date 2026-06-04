# Sales e-Invoices — Backend Business Flow

Backend control flow for each operation. Tenant scope (`BIZ_KEY`) is always read from the JWT via `TenantContext.currentBizKey()` — the wire never carries `BIZ_KEY`. Source: [EInvoiceService.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java), [EInvoiceController.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java). Sales = `type=SALES` → `DOC_TYPE='I'`; the identical flow with `type=EXPENSE` → `DOC_TYPE='B'` backs [Expenses e-Invoices](../expenses-e-invoices/README.md).

---

## 1. List — `GET /api/v1/addon/einvoices?type=SALES`

```
[Browser: EInvoicesClient]
   │ GET /einvoices?type=SALES&from=YYYYMMDD&to=YYYYMMDD&keyword=&page=&size=&sortField=&sortDirection=
   ▼
[JwtAuthenticationFilter] → TenantContext.currentBizKey()
   ▼
[EInvoiceController#list]  (validates from/to via @Pattern YYYYMMDD; type @NotBlank)
   ▼
[EInvoiceService#list(type, fromDt, toDt, keyword, page, size, sortField, sortDirection)]
   │ docType   = resolveDocType(type)          → 'I' for SALES   ('B' for EXPENSE/BILL; 'E'/'S' → WBE2002)
   │ validateDateRange(fromDt, toDt)            → both required + valid YYYYMMDD + from ≤ to
   │ safeSize  = size<=0 ? 10 : min(size, 100)   safePage = max(0, page)
   │ Sort      = EInvoiceSortField.parse(sortField) + SortDirection.parse(sortDirection)
   │             → user primary order + DEFAULT_SORT (ISSU_DT DESC, REG_DTM DESC) tiebreaker
   │ kwPattern = buildKeywordPattern(keyword)   (null if blank; '!'-escaped; %…% wrapped)
   ▼
[BusaDocMstrRepository#findFiltered(bizKey, docType, fromDt, toDt, kwPattern, pageable)]   → Page<BusaDocMstr>
[BusaDocMstrRepository#aggregateByCurrency(bizKey, docType, fromDt, toDt, kwPattern)]       → List<EInvoiceCcyAggregate>  (unpaged)
   ▼
[PostgreSQL: BUSA_DOC_MSTR]
   ▼
page.map(toRow) → EInvoiceRow[]   (issu_tm sliced from REG_DTM[8..14]; amounts via wireAmount)
aggregates.map → CurrencyTotal[]  (one per TRCO_TRSC_CCY_CD)
   ▼
EInvoiceListResponse { rows, total_count, total_pages, page_number, page_size, totals }
```

### Baseline filter chain (applied on every list query — IR-2)

`findFiltered` and `aggregateByCurrency` share the same `WHERE` so totals stay consistent with the page:

```sql
WHERE BIZ_KEY = :bizKey
  AND DOC_TYPE = :docType                       -- 'I' Sales / 'B' Expense
  AND PROV_STTS NOT IN ('VD','EE')              -- always: hide cancelled/void + drafts
  AND HAND_REG_YN <> 'Y'                         -- always: hide hand-registered rows
  AND ISSU_DT BETWEEN :fromDt AND :toDt          -- required date range (IR-3)
  AND ( :kwPattern IS NULL OR (                  -- fixed 5-column keyword OR (IR-5)
        DOC_ID LIKE :kw ESCAPE '!' OR TRCO_CMNM LIKE :kw ESCAPE '!'
     OR TRCO_TAX_ID LIKE :kw ESCAPE '!' OR EINV_INVOICESYMBOL LIKE :kw ESCAPE '!'
     OR DISP_DOC_ID LIKE :kw ESCAPE '!' ) )
```

- **Multi-currency totals** are computed by the unpaged sibling `aggregateByCurrency` (`GROUP BY TRCO_TRSC_CCY_CD`) so the footer is stable across pagination (IR-6).
- **Export reuses this exact path** with `Pageable.unpaged()` (see §2).
- Legacy parity gaps vs `einv_0001_001_r001_act.jsp`: the `(DOC_ID,BIZ_KEY) IN (SELECT … BUSA_DOC_DETL)` "has line items" predicate and the `PROV_STTS=OP → IN ('OP','PP','OD')` expansion are **not** replicated — see [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 2. Excel — list view — `POST .../einvoices/export`

Shared common XLSX pipeline. The thin controller forwards `ExcelExportRequest` to `ExcelExportService.exportTo(response, request)`; the registry resolves `SalesEInvoiceExportProvider` by `screenId = "addon-sales-einvoices"`.

```
[ExcelExportService] resolve provider → intersect requested columns with allowedFieldIds ∩ persisted View Setting USE_YN
   ▼
[EInvoiceExportProvider#fetch]  (base class; Sales subclass binds screenId + defaultType="SALES")
   │ filters → type / from / to / keyword ; sort = request.sort()
   │ data = EInvoiceService.list(type, from, to, keyword, Pageable.unpaged(), sortField, sortDirection)
   │ rows → FIELD_ID map: DATE_TIME(issuDt+issuTm) · INVOICE_NO(einvInvoicesymbol) ·
   │        BUYER(trcoCmnm + trcoTaxId) · CURRENCY · TOTAL(raw decimal) · STATUS · TAX · PRETAX
   │ subHeader = "<n> invoice(s) · Total: … · Tax: … · Pre-tax: …"  per currency (AmountFormatter.forDisplay)
   ▼
[ExcelWorkbookWriter] (Apache POI SXSSF) → streams XLSX, RFC 5987 UTF-8 Content-Disposition
```

Export and list draw from **one query path**, so filter + sort behaviour matches the screen exactly.

---

## 3. Excel — with details — `POST .../einvoices/export-details`

`EInvoiceDetailExportService.exportTo` — the line-item-expanded variant (legacy `einv_0001_001_r002_excel` detail mode).

```
data = EInvoiceService.list(type, from, to, keyword, Pageable.unpaged(), sort)      -- same filtered master set
for each invoice:
   load BUSA_DOC_DETL (line items) + EINV_FEE_DETL (fees) + EINV_TAX_DETL (tax breakdown)
   emit one INVOICE header row + one row per PRODUCT / FEE / TAX line (LINE_TYPE discriminator)
→ stream XLSX
```

Does **not** route through the per-screen `ExportRowProvider` registry — it owns its own fixed wide column set (invoice + counterparty + per-line columns).

---

## 4. Scrape intake — phase 1 — `POST .../einvoices/scrape-results`

WeCMS-side ingestion of the GDT `INVOICE_LIST` page (normally produced by the local NXISAS engine). Mirrors `nxisas_einvoice_c001`.

```
[EInvoiceScrapeService#saveScrapedInvoices(request)]   (@Transactional)
 1. resolve docType from request.type()
 2. for each ScrapedEInvoiceRow:
      recompose DOC_ID from (numericSymbol, invoiceSymbol, invoiceNo, …)
      upsert BUSA_DOC_MSTR by (BIZ_KEY, DOC_ID)   (findById ?? new)
      derive EINV_CHECKRESULTS / EINV_INVOICESTATUS via the legacy branch sets:
         checkResult ∈ CHECK_RESULT_BRANCH_A/B   → coded check phrase
         invoiceStatus ∈ VOID_STATUS_BRANCH_A/B  → replaced / cancelled flags
      stamp audit; save; savedCount / updatedCount / skippedCount++
 3. INSERT one EXT_TRAN_CALL_HIS audit row for the batch
 4. return { saved_count, updated_count, skipped_count, mod_dtm }
```

Phase 1 writes the master row only — line/fee/tax detail arrives in phase 2.

---

## 5. Scrape intake — phase 2 — `POST .../einvoices/scrape-detail-results`

Per-invoice `INVOICE_DETAIL` ingestion + counterparty auto-registration. Mirrors `nxisas_einvoice_c002`.

```
[EInvoiceDetailScrapeService#saveScrapedDetails(request)]   (@Transactional)
 1. locate the phase-1 BUSA_DOC_MSTR by recomposed DOC_ID  (skip if absent)
 2. enrich master with detail-only columns (title, addresses, bank accounts, signatures, QR, rate, …)
 3. delete + re-insert BUSA_DOC_DETL (products), EINV_FEE_DETL (fees), EINV_TAX_DETL (tax breakdown)
 4. EInvoiceTrcoAutoRegistrar.register(invoice, type):
      counterparty = (SALES → buyer block) | (EXPENSE → seller block)
      if counterparty has a tax id and no existing TRCO_INFM link:
          create TRCO_INFM  (TRCO_KEY via wb_get_next_val('trco_infm','trco_key'))
          seed PGRO_BSAC_LNKN_INFM (USER_ID='ALL', GRP_CD='S' Sales / 'B' Expense)
          upsert TRCO_ACCT_INFO bank accounts (dedup on BANK_NM/ACCT_NO)
      else if no tax id:  TRCO_KEY = ETC_TRCO_KEY ("99999999999999999999")
      write TRCO_ACTV_HSTR activity row (DV_CD 'S'/'B', TASK_CD 'S003'/'B003', ACTV_AMT, RSLT_CD 'C')
 5. return { saved_count, updated_count, skipped_count, mod_dtm }
```

---

## 6. GDT inquiry history — `GET .../einvoices/history?type=SALES`

```
[EInvoiceHistoryService#list(type, fromDt, toDt, status)]
   │ reads EXT_TRAN_CALL_HIS where SVC_ID ∈ {GDT_INQUIRY_SALES | GDT_INQUIRY_EXPENSES}
   │ success filter (status=success/fail) on RSLT_CD ('00000000'/'1' = success)
   │ normalizeErrCont → extract Output.ErrorMessage from the JSON ERR_CONT blob
   ▼
EInvoiceHistoryResponse { rows[] }   (reg_dtm, type, svc_id, start_date, end_date, result, rslt_cd, err_message)
```

## 7. Detail (re-poll) history — `GET .../einvoices/detail-history?type=SALES`

Sales-only (Expense rejected → `WBE2002`). Lists master rows whose `MOD_DTM` matches a given re-poll batch timestamp.

```
[EInvoiceHistoryService#detailHistory(type, fromDt, toDt, modDtm, sortField, sortDirection)]
   │ docType = resolveDocType(type)  (must be 'I')
   │ sort via EInvoiceDetailHistorySortField (default ISSU_DT DESC)
   ▼
[BusaDocMstrRepository#findDetailHistory(bizKey, docType, fromDt, toDt, modDtm, sort)]
   │ WHERE docType, bizKey, ISSU_DT BETWEEN, MOD_DTM = :modDtm, HAND_REG_YN = 'N'
   ▼
EInvoiceDetailHistoryResponse { rows[] }
```

---

## Audit trail summary

| Table | Written by | Purpose |
|---|---|---|
| `EXT_TRAN_CALL_HIS` | scrape phase-1 (1/batch); read by inquiry history | GDT scrape-call audit (`SVC_ID = GDT_INQUIRY_SALES`/`…EXPENSES`, `RSLT_CD`, `ERR_CONT`) |
| `TRCO_ACTV_HSTR` | scrape phase-2 (1/invoice via auto-registrar) | Counterparty activity log (`DV_CD`, `TASK_CD`, `ACTV_AMT`) |
| `TRCO_INFM` / `TRCO_ACCT_INFO` / `PGRO_BSAC_LNKN_INFM` | scrape phase-2 (auto-registrar, create/upsert) | Auto-registered trading-company master + bank accounts + group link |

All tenant-scoped via `TenantContext.currentBizKey()`.

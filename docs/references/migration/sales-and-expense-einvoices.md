# Migration Mapping — Sales and Expense e-Invoices

**Companion doc to:** [docs/refactor-wecms/features/sales-and-expense-einvoices.md](../../features/addon-service/README.md).

**Refactor slugs:** `sales-einvoices` (outbound, `BusaDocType.INVOICE = 'I'`) + `expense-einvoices` (inbound, `BusaDocType.BILL = 'B'`).

**Legacy WSVC ids being replaced:** `einv_0001_000`, `einv_0001_001`, `einv_0001_001_r001`, `einv_0001_001_u001`, `einv_0001_001_r002_excel`, `nxisas_einvoice`, `nxisas_einvoice_c001`, `nxisas_einvoice_c002`, `nxisas_einvhis`, `nxisas_einvhis_r001`, `nxisas_einvhis_dt_r001`, `nxisas_getCertFormInfo`, `nxisas_getModuleInfo`, `nxisas_encrypt`, `nxisas_decrypt`, `nxisas_logging`.

This document maps every JEX artifact → its refactor counterpart, **service by service** and **field by field**. JEX is read-only reference — file paths are citations into `WECMS DEV/`.

---

## 1. Service-level mapping

| Legacy WSVC id | Legacy file | Refactor controller | Refactor service(s) | Refactor REST URI | Slug |
|---|---|---|---|---|---|
| `einv_0001_000` (view shim) | `WECMS/web/view/jex/wabooks/einv/einv_0001_000_view.jsp` | n/a (frontend route `/addon/sales-einvoices`) | n/a | n/a | `sales-einvoices` |
| `einv_0001_001` (shared list view) | `WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` | n/a (frontend `<EInvoicesPage>`) | n/a | n/a | both |
| `einv_0001_001_r001` (list read) | `WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp` | `SalesEinvoiceController` + `ExpenseEinvoiceController` | `EinvoiceListService` + `EinvoiceTotalsService` | `GET /api/v1/addon/sales-einvoices` + `GET /api/v1/addon/expense-einvoices` | both |
| `einv_0001_001_u001` (currency edit) | `WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_u001_act.jsp` | `SalesEinvoiceController` + `ExpenseEinvoiceController` | `EinvoiceCurrencyUpdateService` | `PUT /api/v1/addon/sales-einvoices/{doc_id}/currency` + `…/expense-einvoices/{doc_id}/currency` | both |
| `einv_0001_001_r002_excel` (excel) | `WECMS/web/view/jex/wabooks/einv/einv_0001_001_r002_excel_view.jsp` | `SalesEinvoiceController` + `ExpenseEinvoiceController` | `EinvoiceExportProvider` (via shared `ExcelExportService`) | `POST /api/v1/addon/sales-einvoices/export` + `…/expense-einvoices/export` | both |
| `nxisas_einvoice` (scrape popup view) | `WECMS/web/view/jex/wabooks/einv/nxisas_einvoice_view.jsp` | n/a (frontend `<NxisasScrapeDialog>`) | n/a | n/a | shared |
| `nxisas_einvoice_c001` (ingest) | `WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c001_act.jsp` | `EinvoiceScrapeController` | `EinvoiceScrapeIngestService` | `POST /api/v1/addon/einvoices/scrape/ingest` | shared |
| `nxisas_einvoice_c002` (enrich) | `WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c002_act.jsp` | `EinvoiceScrapeController` | `EinvoiceScrapeEnrichService` | `POST /api/v1/addon/einvoices/scrape/enrich` | shared |
| `nxisas_einvhis` (history popup view) | `WECMS/web/view/jex/wabooks/einv/nxisas_einvhis_view.jsp` | n/a (frontend dialog) | n/a | n/a | shared |
| `nxisas_einvhis_r001` (history list) | `WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvhis_r001_act.jsp` | `EinvoiceScrapeController` | `EinvoiceScrapeHistoryService` (subset of ingest service or a thin reader) | `GET /api/v1/addon/einvoices/scrape/history` | shared |
| `nxisas_einvhis_dt_r001` (history detail) | `WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvhis_dt_r001_act.jsp` | `EinvoiceScrapeController` | same | `GET /api/v1/addon/einvoices/scrape/history/{reg_dtm}` | shared |
| `nxisas_getCertFormInfo` | `WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_getCertFormInfo_act.jsp` | `EinvoiceScrapeController` | `GdtCertFormInfoService` | `GET /api/v1/addon/einvoices/scrape/cert-form/{bank_cd}` | shared |
| `nxisas_getModuleInfo` | `WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_getModuleInfo_act.jsp` | `EinvoiceScrapeController` | `GdtModuleInfoService` | `GET /api/v1/addon/einvoices/scrape/module-info/{api_id}` | shared |
| `nxisas_encrypt` | `WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_encrypt_act.jsp` | (server-side dropped — not needed) | refactor performs SEED-CBC on server boundary only | n/a | shared |
| `nxisas_decrypt` | `WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_decrypt_act.jsp` | (server-side internal) | `IsasSeedCbcDecoder` (called inline by ingest + enrich services) | n/a | shared |
| `nxisas_logging` | `WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_logging_act.jsp` | (folded into ingest + enrich) | `NxisasAuditLogger` (reused from `domain/banking/nxisas/audit/`) | n/a | shared |

Why `nxisas_encrypt` drops: legacy uses it to bounce a re-encrypted payload back to the browser when the engine forwards a previously-decrypted chunk. The refactor architecture never round-trips an encrypted blob through the browser — the engine produces the encrypted blob, the browser posts it once to `/scrape/ingest`, and the server decrypts there. No re-encryption boundary exists.

Why `nxisas_decrypt` drops as a public endpoint: same reason. Decryption stays server-internal via `IsasSeedCbcDecoder` (no public route surface).

Why `nxisas_logging` drops as a public endpoint: in JEX the browser POSTs the raw `_threadArray` JSON to `nxisas_logging.act` after every scrape attempt. The refactor's `EinvoiceScrapeIngestService` writes the equivalent rows directly (via the reused `NxisasAuditLogger`) inside the `/scrape/ingest` transaction — the logging is not a separate browser call.

---

## 2. WSVC → DTO field mapping

### 2.1 `einv_0001_001_r001` (LIST) → `EinvoiceListRequest` / `EinvoiceListResponse`

JEX wire keys are UPPER_SNAKE (from `WSVC.einv_0001_001_r001.xml`). Refactor wire is lowercase snake_case (CTO directive — memory `project_api_naming_lowercase.md`).

**Input:**

| Legacy field (`WSVC` in) | Refactor wire (lowercase snake) | Java DTO field | Notes |
|---|---|---|---|
| `BIZ_KEY` | (dropped) | — | sourced from JWT via `TenantContext.currentBizKey()` |
| `DOC_TYPE` | (dropped) | — | controller pins `'I'` (Sales) or `'B'` (Expenses) |
| `FROM_DT` | `from_dt` | `String fromDt` | yyyyMMdd |
| `TO_DT` | `to_dt` | `String toDt` | yyyyMMdd |
| `SRCH_WD` | `search` | `String search` | matched against issu_dt + doc_id + trco_cmnm + cont (via `wb_get_translate_text`) + numeric on totl_amt / tax_amt_sum / sply_amt_sum |
| `einv_invoiceStatus` | `invoice_status` | `String invoiceStatus` | optional; localized Vietnamese (OQ-3 in feature doc proposes coded enum) |
| `einv_testResults` | `test_results` | `String testResults` | `"all"` / `""` / `"1"` / `"2"`; default `"all"` |
| `TRCO_TRSC_CCY_CD` | `currency` | `String currency` | VND / USD / 998 / 999 |
| `PROV_STTS` | `prov_stts` | `String provStts` | dashboard bucket selector |
| `PAGE_NO` | `page_no` | `Integer pageNo` | 1-indexed |
| `PAGE_PER_CNT` | `page_per_cnt` | `Integer pagePerCnt` | 15 / 20 / 50 / 100 |
| (new) | `sort_field` | `String sortField` | View Setting `FIELD_ID` |
| (new) | `sort_direction` | `String sortDirection` | `ASC` / `DESC` |

**Output (top-level):**

| Legacy field (`WSVC` out) | Refactor wire | Java DTO field | Source |
|---|---|---|---|
| `END_DATE` | `end_date` | `String endDate` | `EXT_TRAN_CALL_HIS_R004` max(`END_DATE`) for `SVC_ID = 'GDT_INQUIRY_SALES'` or `'GDT_INQUIRY_EXPENSES'` |
| `GDT_CERT_SEQ` | `gdt_cert_seq` | `String gdtCertSeq` | `GDT_CERT_INFO` by `BIZ_KEY` |
| `TOT_CNT` | `totals.tot_cnt` | nested | `BUSA_DOC_MSTR_R022_CNT` |
| `TOT_VND_CNT`, `TOT_VND_AMT`, `TOT_VND_SPLY_AMT`, `TOT_VND_TAX_AMT` | `totals.by_currency.VND.*` | `Map<String,CurrencyTotals>` keyed by ccy_cd | refactor pivots from per-currency columns into a map (legacy is column-per-currency) |
| `TOT_USD_CNT`, `TOT_USD_AMT`, `TOT_USD_SPLY_AMT`, `TOT_USD_TAX_AMT` | `totals.by_currency.USD.*` | same | same |
| `TOT_INV_CNT`, `TOT_INV_LOC_CCY_CD_AMT`, `TOT_INV_USD_AMT` | `totals.buckets.all.{cnt,loc_amt,usd_amt}` | nested | dashboard bucket |
| `TOT_OP_INV_*` | `totals.buckets.open.*` | nested | dashboard bucket |
| `TOT_OD_INV_*` | `totals.buckets.overdue.*` | nested | dashboard bucket |
| `TOT_PA_INV_*` | `totals.buckets.paid.*` | nested | dashboard bucket |
| `REC[]` | `records[]` | `List<EinvoiceRow>` | rows |

**Output (`REC` row → `EinvoiceRow`):**

| Legacy `REC.*` | Refactor wire | Java field | Source column on `BUSA_DOC_MSTR` |
|---|---|---|---|
| `DOC_ID` | `doc_id` | `String docId` | DOC_ID |
| `ISSU_DT` | `issu_dt` | `String issuDt` | ISSU_DT (yyyyMMdd) |
| `TRCO_CMNM` | `trco_cmnm` | `String trcoCmnm` | TRCO_CMNM |
| `TRCO_CMENM` | `trco_cmenm` | `String trcoCmenm` | TRCO_CMENM |
| `TRCO_TRSC_CCY_CD` | `trco_trsc_ccy_cd` | `String trcoTrscCcyCd` | TRCO_TRSC_CCY_CD |
| `TOTL_AMT` | `totl_amt` | `BigDecimal totlAmt` (wire string) | TOTL_AMT |
| `SPLY_AMT_SUM` | `sply_amt_sum` | `BigDecimal splyAmtSum` | SPLY_AMT_SUM |
| `TAX_AMT_SUM` | `tax_amt_sum` | `BigDecimal taxAmtSum` | TAX_AMT_SUM |
| `TOTL_DIS_AMT` | `totl_dis_amt` | `BigDecimal totlDisAmt` | TOTL_DIS_AMT |
| `BUSA_USAG_CD` | `busa_usag_cd` | `String busaUsagCd` | BUSA_USAG_CD |
| `USAG_NM` | `usag_nm` | `String usagNm` | join to `USAG_INFM_LANG_DETL` |
| `PROV_STTS` | `prov_stts` | `String provStts` | PROV_STTS |
| `PAY_DUE_CD` | `pay_due_cd` | `String payDueCd` | PAY_DUE_CD |
| `DUE_DT` | `due_dt` | `String dueDt` | DUE_DT |
| `OVER_DUE_DAYS` | `over_due_days` | `Integer overDueDays` | derived `today - due_dt` |
| `PRJT_NO` | `prjt_no` | `String prjtNo` | PRJT_NO |
| `PRJT_NM` | `prjt_nm` | `String prjtNm` | (join — prjt master) |
| `REF_NO` | `ref_no` | `String refNo` | REF_NO |
| `REG_ID` | `reg_id` | `String regId` | REG_ID |
| `REG_DTM` | `reg_dtm` | `String regDtm` | REG_DTM |
| `EML_TRF_DTM` | `eml_trf_dtm` | `String emlTrfDtm` | EML_TRF_DTM |
| `RCV_YN` | `rcv_yn` | `String rcvYn` | RCV_YN |
| `NOTE` | `note` | `String note` | NOTE |
| `COLOR` | `color` | `String color` | COLOR |
| `MEMO` | `memo` | `String memo` | MEMO |
| `TRCO_KEY` | `trco_key` | `String trcoKey` | TRCO_KEY |
| `RCV_PAY_WAY_CD` | `rcv_pay_way_cd` | `String rcvPayWayCd` | RCV_PAY_WAY_CD |
| `DOC_TITLE` | `doc_title` | `String docTitle` | DOC_TITLE |
| `REG_NM` | `reg_nm` | `String regNm` | join to USER_INFM |
| `MOD_DTM` | `mod_dtm` | `String modDtm` | MOD_DTM |
| `MOD_NM` | `mod_nm` | `String modNm` | join to USER_INFM |
| `APLY_EXCH_RATE` | `aply_exch_rate` | `BigDecimal aplyExchRate` | APLY_EXCH_RATE |
| `INV_TYPE` | `inv_type` | `String invType` | INV_TYPE |
| `APPR_SEQ_NO` | `appr_seq_no` | `String apprSeqNo` | APPR_SEQ_NO |
| `CONT` | `cont` | `String cont` | CONT |
| `einv_invoiceStatus` | `einv_invoicestatus` | `String einvInvoicestatus` | EINV_INVOICESTATUS |
| `einv_numericSymbol` | `einv_numericsymbol` | `String einvNumericsymbol` | EINV_NUMERICSYMBOL |
| `einv_invoiceSymbol` | `einv_invoicesymbol` | `String einvInvoicesymbol` | EINV_INVOICESYMBOL |
| `einv_invoicestatus_bef` | `einv_invoicestatus_bef` | `String einvInvoicestatusBef` | EINV_INVOICESTATUS_BEF |
| `HAND_DOC_ID` | `hand_doc_id` | `String handDocId` | HAND_DOC_ID |
| `TRCO_TAX_ID` | `trco_tax_id` | `String trcoTaxId` | TRCO_TAX_ID |
| `DOC_TYPE` | `doc_type` | `String docType` | DOC_TYPE — `'I'` or `'B'` (returned for client sanity; controller-pinned) |
| `COMPANYSTATUS` | `companystatus` | `String companystatus` | join via NXISAS `companyList` snapshot (free-form Vietnamese) |
| `RCV_SND_YN` | `rcv_snd_yn` | `String rcvSndYn` | derived for Bill: SND_YN; for Invoice: RCV_YN |
| `READ_YN` | `read_yn` | `String readYn` | READ_YN |
| `DISP_DOC_ID` | `disp_doc_id` | `String dispDocId` | DISP_DOC_ID |
| `KHR_SPLY_AMT`, `KHR_TAX_AMT`, `TOT_KHR_AMT`, `BAL` | dropped | — | not used by refactor UI |
| `BLTB_CD`, `POST_SEQ_NO` | dropped | — | bulletin-board cross-link not used |
| `HAND_REG_YN` | dropped | — | always `'N'` due to query guard |
| `TRCO_PAYM_TERM`, `RCV_PAY_NOTE`, `DOC_NO`, `CON_DOC_ID`, `TRCO_NICK_NM`, `USER_NM` | optional (defer) | — | revisit when the detail screen is in scope |

### 2.2 `einv_0001_001_u001` (UPDATE currency) → `EinvoiceCurrencyUpdateRequest`

| Legacy field | Refactor wire | Java field |
|---|---|---|
| `DOC_ID` | (path param) | `String docId` |
| `TRCO_TRSC_CCY_CD` | `trco_trsc_ccy_cd` | `String trcoTrscCcyCd` |

Response: 204 No Content.

### 2.3 `nxisas_einvoice_c001` (SCRAPE INGEST) → `EinvoiceScrapeIngestRequest`

| Legacy field | Refactor wire | Java field | Notes |
|---|---|---|---|
| `EINV_TYPE` | `einv_type` | `String einvType` | `"1"` / `"2"` |
| `NXISAS_UID` | `nxisas_uid` | `String nxisasUid` | ≥ 30 chars |
| `NXISAS_ACTION` | `nxisas_action` | `String nxisasAction` | ≥ 30 chars |
| `FROM_DT` | `from_dt` | `String fromDt` | yyyyMMdd |
| `TO_DT` | `to_dt` | `String toDt` | yyyyMMdd |
| `REC_IN[].ENC_STR` | `rec_in[].enc_str` | `String encStr` | Coocon-encrypted JSON |
| `REC_IN[].einv_testResults` | `rec_in[].einv_test_results` | `String einvTestResults` | per-batch test results discriminator |

**Output → `EinvoiceScrapeIngestResponse`:**

| Legacy field | Refactor wire | Java field |
|---|---|---|
| `MOD_DTM` | `mod_dtm` | `String modDtm` |
| `NXISAS_ERR_CODE` | `nxisas_err_code` | `String nxisasErrCode` |
| `NXISAS_ERR_CONT` | `nxisas_err_cont` | `String nxisasErrCont` |
| `REC[].DOC_ID` | `records[].doc_id` | `String docId` |
| `REC[].HAND_DOC_ID` | `records[].hand_doc_id` | `String handDocId` |
| `REC[].einv_invoiceSymbol` | `records[].einv_invoicesymbol` | `String einvInvoicesymbol` |
| `REC[].einv_testResults` | `records[].einv_test_results` | `String einvTestResults` |
| `REC[].einv_sellerTaxCode` | `records[].einv_seller_tax_code` | `String einvSellerTaxCode` |
| `REC[].einv_sellerName` | `records[].einv_seller_name` | `String einvSellerName` (OQ-10 — legacy populates from `numericSymbol`, likely wrong) |

### 2.4 `nxisas_einvoice_c002` (SCRAPE ENRICH) → `EinvoiceScrapeEnrichRequest`

| Legacy field | Refactor wire | Java field |
|---|---|---|
| `NXISAS_UID` | `nxisas_uid` | `String nxisasUid` |
| `NXISAS_ACTION` | `nxisas_action` | `String nxisasAction` |
| `MOD_DTM` | `mod_dtm` | `String modDtm` |
| (legacy reads c001-stored EncStr from session/cache) | `detail_enc_str` | `String detailEncStr` | refactor receives the per-invoice detail blob explicitly (no server-side cache) |
| (derived from doc_id in legacy) | (path param) | `String docId` |
| `EINV_TYPE` | `einv_type` | `String einvType` |

Output: 204 No Content.

### 2.5 `nxisas_einvhis_r001` → `ScrapeHistoryResponse`

| Legacy field | Refactor wire | Java field | Source |
|---|---|---|---|
| `FROM_DT`, `TO_DT` (input) | (query params) | `String fromDt`, `String toDt` | yyyyMMdd |
| `STTS` (input) | `stts` | `String stts` | `""` / `"0"` / `"1"` |
| `SVC_ID` (input, optional) | `svc_id` | `String svcId` | defaults to `GDT_INQUIRY%` LIKE |
| `REC[].REG_DTM` | `records[].reg_dtm` | normalised via `DateTimeUtils.normalizeResult` |
| `REC[].SVC_ID` | `records[].svc_id` | |
| `REC[].WEB_SVC_ID` | `records[].web_svc_id` | |
| `REC[].RSLT_CD` | `records[].rslt_cd` | |
| `REC[].ERR_CONT` | `records[].err_cont` | post-processed: if JSON, extract `Output.ErrorMessage` |
| `REC[].START_DATE`, `END_DATE` | `records[].start_date`, `end_date` | |

---

## 3. IDO → JPA repository mapping

| Legacy IDO | Legacy purpose | Refactor equivalent |
|---|---|---|
| `BUSA_DOC_MSTR_R022` | Paged list query (~80-col SELECT incl. usage join, exch rate, color, memo, project, approval, contracted-party joins) | `BusaDocMstrRepository.findEinvoiceList(...)` — Spring Data + JPQL or `Specification` over `BusaDocMstr` entity. Joins as `@ManyToOne`/`@OneToOne` to `UsagInfm`, `TrcoInfm`, etc. |
| `BUSA_DOC_MSTR_R022_CNT` | Total count | derived from the same `Page<T>` total, plus per-currency totals via a second projection query |
| `BUSA_DOC_MSTR_R024` / `_R025` | Excel detail query (joined with `BUSA_DOC_DETL`, `EINV_FEE_DETL`, `EINV_TAX_DETL`) | `BusaDocMstrRepository.findEinvoiceExportDetails(...)` returning a JPQL projection that joins the three tables |
| `BUSA_DOC_MSTR_C002` | Batch insert (one row per scraped invoice) | `BusaDocMstrRepository.saveAll(...)` with `@SqlBatchSize` hint; idempotent via PK conflict on `(BIZ_KEY, DOC_ID)` |
| `EXT_TRAN_CALL_HIS_R004` | Latest successful scrape end_date | `ExtTranCallHisRepository.findLatestEndDate(bizKey, svcId, webSvcId, rsltCd)` |
| `EXT_TRAN_CALL_HIS_C001` | Insert per-call history | `ExtTranCallHisRepository.save(...)` |
| `BANK_INFM_DETL_R006` / `_R005` | Per-bank cert form fields | `BankInfmDetlRepository.findCertFormFields(bankCd, langCd)` |
| `GDT_CERT_INFO_R001` (read by code at `einv_0001_001_r001_act.jsp:292`) | GDT cert seq by `BIZ_KEY` | `GdtCertInfoRepository.findByBizKey(...)` |

The custom IDO XMLs themselves (e.g. `IDO.BUSA_DOC_MSTR_R022.xml`) are **not ported** — Spring Data JPA + JPQL replaces them.

---

## 4. JEX helper / DAO → refactor service mapping

| Legacy class | Legacy purpose | Refactor equivalent |
|---|---|---|
| `BusaDocMstrDAO` | DAO over `BUSA_DOC_MSTR` (CRUD + `getTotInfo2`) | `BusaDocMstrRepository` + `EinvoiceTotalsService` |
| `BusaDocMstrTotInfo` (bean) | Per-currency totals carrier | `EinvoiceTotals` DTO + `CurrencyTotals` nested record |
| `ExtTranCallHisDAO` | DAO + `R004` reader | `ExtTranCallHisRepository` |
| `GdtCertInfoDAO` | DAO | `GdtCertInfoRepository` |
| `SearchHelper.getSearchColumnDynamic` | Builds the `SRCH_WD` OR-clauses from View Setting visible columns | `EinvoiceSearchClauseBuilder` — given the user's saved View Setting + `search` text, build a JPA `Specification` |
| `SortHelper.getSortDynamic` | Builds ORDER BY from request sort + service-defined default | replaced by Spring Data `Sort` + the `EinvoiceSortField` whitelist (see `common/web/`) |
| `ValidationHelper.validateRequired / validateDate8 / validatePeriod` | Reusable validators | `EinvoiceFilterValidator` (Jakarta Bean Validation + custom date / period checks) |
| `ValidationHelper.validateBusaDocMstr` | Loads + tenant-scopes `BUSA_DOC_MSTR` for an update | inlined into `EinvoiceCurrencyUpdateService.update` — throws `WBE_EINV_NOT_FOUND` |
| `BusaDocType` enum | `INVOICE='I'`, `BILL='B'`, ... | reused as a refactor enum (only `INVOICE` + `BILL` exposed) — see `common/enums/` policy |
| `ProvStts` enum | `OP / PP / OD / VD / EE / PA / ...` | new enum `EinvoiceProvStts` in `domain/addon/einvoice/`, two consumers (`EinvoiceListService` filter, `EinvoiceScrapeIngestService` write) trigger extraction |
| `CcyCd` enum | currency code | reused (refactor already ports it) |
| `LangCd` enum | language code | reused (already in `common/enums/`) |
| `IdoUtils.mergeDynamic / getDynamic / executeListWithTxn / executeWithTxn` | JEX IDO helper layer | not ported — Spring Data + `@Transactional` replace it |
| `IsasHelper.insertNxisasModuleTranLogHis` | Writes `NXSIAS_MODULE_TRAN_LOG_HIS` | reused via `NxisasAuditLogger` from `domain/banking/nxisas/audit/` |
| `WABOOKSOSMCAPIUtil.callApi(_apiId)` | OSMC API config lookup | replaced by `NxisasApiModuleRepository` (refactor-local lookup table) or a `Map<String,String>` config — OQ-5 |
| `WABOOKSOSMCCurrencyUtil.formatAmountWithCurrency` | Format VND / USD amounts in legacy Excel | reused via `AmountFormatter.forDisplay(value, ccyCd)` from `domain/banking/service/` (already in the refactor per `wecms-api/CLAUDE.md`) |
| `WABOOKSOSMCNumberUtil.normalizeQtyComma` | Format quantity / exchange-rate strings | inlined into `EinvoiceExportProvider` via existing `NumberFormat` helpers; not extracted as a class |
| `SysTrcoInfmDisNmDAO.getNormalizeDisplayNameBySystem` | Resolve standardized counterparty nickname | reused via the `contacts` feature's existing `SysTrcoInfmDisNmRepository` (already ported per V84+) |
| `com.coocon.securty.util.ISASSeedCBC` | Coocon SEED-CBC encrypt / decrypt | wrapped as `IsasSeedCbcDecoder` in `domain/addon/einvoice/gateway/`; the JAR is vendored to `wecms-api/libs/` (OQ-5) |

---

## 5. Frontend JS / TS → React component mapping

| Legacy frontend file | Legacy purpose | Refactor equivalent |
|---|---|---|
| `WECMS_STATIC/web/js/jex/wabooks/einv/einv_0001_000.js` | Sales shim (`einvType=1`) — defers to `einv_0001_001.js` | n/a — frontend route `/addon/sales-einvoices` |
| `WECMS_STATIC/web/js/jex/wabooks/einv/einv_0001_001.js` (compiled from `einv_0001_001.ts`) | Shared list page logic (~1500+ lines): event handlers, table rendering, dashboard, status filter, date filter, currency filter, footer totals, simple credit/debit confirmation layer | split across `wecms-web/src/features/einvoices/`: `<EInvoicesPage>` + `<EInvoiceToolbar>` + `<EInvoiceTable>` + `<EInvoiceFooter>` + `useEInvoiceList`, `useEInvoiceTotals` |
| `WECMS_STATIC/web/js/jex/wabooks/einv/einv_0001_001_r002_excel.js` | Excel button click handler | folded into `<ExcelDropdown>` + `useExcelExport` hook |
| `WECMS_STATIC/web/js/jex/wabooks/einv/nxisas_einvoice.js` | NXISAS scrape popup driver — `_dataJson` decoded, manages date range, threads progress, calls `nxisas_einvoice_c001` then `c002` per record | `<NxisasScrapeDialog>` + `useScrapeBatch` hook |
| `WECMS_STATIC/web/js/jex/wabooks/einv/nxisas_einvhis.js` | Inquiry history popup | `<ScrapeHistoryDialog>` + `useScrapeHistory` hook |
| `WECMS_STATIC/web/js/jex/wabooks/einv/nxisas_einvhis_dt.js` | Per-call detail popup | `<ScrapeHistoryDetailDialog>` |
| `WECMS_STATIC/web/nxisas/js/nxisas_v2.js` | OSMC NXISAS wrapper (~3000+ lines): dialogs for OTP / CAPTCHA / QNA / password, cancel handlers, progress bar, encrypted batch POST | `features/einvoices/components/nxisas/{NxisasScrapeDialog, NxisasOtpDialog, NxisasCaptchaDialog, NxisasQnaDialog, NxisasInstallBanner}.tsx` + `features/einvoices/lib/nxisasBridge.ts` |
| `WECMS_STATIC/web/nxisas/js/isasscaping.js` | Coocon engine SDK: WebSocket connection, callback handlers (`OPEN`, `RUN`, `CANCEL`, `CANCELALL`, `CERTLIST`, `ONSTATUSCHANGED`, `ONCOMPLETED`), Coocon license, version check | `features/einvoices/lib/nxisasBridge.ts` (TypeScript reimplementation — modern WebSocket, no SWF) |
| `WECMS_STATIC/web/nxisas/js/platform.js` | Platform detection (IE / Chrome / Firefox / Safari) | dropped — Next.js targets modern browsers |
| `WECMS_STATIC/web/nxisas/js/swfobject.js` | Flash plugin loader | dropped — no SWF dependency |
| `WECMS_STATIC/web/nxisas/js/web_socket.js` + `websocket.swf` | IE WebSocket polyfill | dropped — native `WebSocket` everywhere |
| `WECMS_STATIC/web/nxisas/js/json2.js`, `json3.min.js` | JSON polyfills | dropped — ES2015+ native `JSON` |
| `WECMS_STATIC/web/nxisas/js/jquery-*.min.js` | jQuery | dropped — React owns DOM |
| `WECMS_STATIC/web/nxisas/js/nxisas.js`, `nxisas_api.js`, `nxisas_api_v2.js` | Legacy v1 API wrappers | dropped — refactor uses `nxisas_v2.js` semantics only |
| `WECMS_STATIC/web/nxisas/js/nxisas_v2_u000.js`, `isasscaping_u000.js`, `isasscaping.js20200506` | Older variants of the same bridge | dropped |

---

## 6. Database column → JPA entity field mapping (subset)

Full `BUSA_DOC_MSTR` is 100 cols (V15 + V23 in Flyway). The refactor entity ships **only the fields the e-Invoice listing + scrape services touch**. The remaining 60+ cols stay in the DB schema but un-mapped in the entity (this is allowed per the "Legacy Schema Parity Rule" in `wecms-api/CLAUDE.md` — DB matches DEV column-for-column; entity subsets are deliberate).

Sprint subset for `BusaDocMstr` entity:

```java
@Entity
@Table(name = "BUSA_DOC_MSTR")
public class BusaDocMstr extends BaseEntity {
    @Id @Column(name = "BIZ_KEY")            private String bizKey;
    @Id @Column(name = "DOC_ID")             private String docId;
    @Column(name = "DOC_TYPE")               private String docType;             // 'I' or 'B'
    @Column(name = "DISP_DOC_ID")            private String dispDocId;
    @Column(name = "ISSU_DT")                private String issuDt;
    @Column(name = "TRCO_KEY")               private String trcoKey;
    @Column(name = "TRCO_CMNM")              private String trcoCmnm;
    @Column(name = "TRCO_CMENM")             private String trcoCmenm;
    @Column(name = "TRCO_TAX_ID")            private String trcoTaxId;
    @Column(name = "TRCO_TRSC_CCY_CD")       private String trcoTrscCcyCd;
    @Column(name = "ISSU_TAX_ID")            private String issuTaxId;
    @Column(name = "ISSU_CMNM")              private String issuCmnm;
    @Column(name = "TOTL_AMT")               private BigDecimal totlAmt;
    @Column(name = "SPLY_AMT_SUM")           private BigDecimal splyAmtSum;
    @Column(name = "TAX_AMT_SUM")            private BigDecimal taxAmtSum;
    @Column(name = "TOTL_DIS_AMT")           private BigDecimal totlDisAmt;
    @Column(name = "BUSA_USAG_CD")           private String busaUsagCd;
    @Column(name = "PROV_STTS")              private String provStts;
    @Column(name = "PAY_DUE_CD")             private String payDueCd;
    @Column(name = "DUE_DT")                 private String dueDt;
    @Column(name = "PRJT_NO")                private String prjtNo;
    @Column(name = "REF_NO")                 private String refNo;
    @Column(name = "EML_TRF_DTM")            private String emlTrfDtm;
    @Column(name = "RCV_YN")                 private String rcvYn;
    @Column(name = "READ_YN")                private String readYn;
    @Column(name = "NOTE")                   private String note;
    @Column(name = "COLOR")                  private String color;
    @Column(name = "MEMO")                   private String memo;
    @Column(name = "RCV_PAY_WAY_CD")         private String rcvPayWayCd;
    @Column(name = "DOC_TITLE")              private String docTitle;
    @Column(name = "APLY_EXCH_RATE")         private BigDecimal aplyExchRate;
    @Column(name = "INV_TYPE")               private String invType;
    @Column(name = "APPR_SEQ_NO")            private String apprSeqNo;
    @Column(name = "CONT")                   private String cont;
    @Column(name = "HAND_DOC_ID")            private String handDocId;
    @Column(name = "HAND_REG_YN")            private String handRegYn;
    @Column(name = "EINV_INVOICESTATUS")     private String einvInvoicestatus;
    @Column(name = "EINV_INVOICESTATUS_BEF") private String einvInvoicestatusBef;
    @Column(name = "EINV_NUMERICSYMBOL")     private String einvNumericsymbol;
    @Column(name = "EINV_INVOICESYMBOL")     private String einvInvoicesymbol;
    @Column(name = "EINV_INVOICEINFO")       private String einvInvoiceinfo;
    @Column(name = "EINV_CHECKRESULTS")      private String einvCheckresults;
    @Column(name = "EINV_TESTRESULTS")       private String einvTestresults;
    @Column(name = "COMPANYSTATUS")          private String companystatus;
    // BaseEntity adds REG_ID / REG_DTM / MOD_ID / MOD_DTM
}
```

The remaining `BUSA_DOC_MSTR` columns (KHR_*, TOT_KHR_AMT, BAL, KSERIAL_*, ... ~60 cols) stay un-mapped this MR. Future detail / line-item / KH cross-border features map them as needed.

`BusaDocDetl`, `EinvFeeDetl`, `EinvTaxDetl`, `GdtCertInfo`, `ExtTranCallHis` entities follow the same Sprint-subset pattern — map every column the e-Invoice flow reads / writes, leave the rest un-mapped.

---

## 7. Error code mapping

Legacy JEX uses `WbMessageCode` (e.g. `WBE0001` for "required field missing"). Refactor introduces new `WBE_EINV_*` codes — never reuse the legacy code verbatim.

| Legacy code | Legacy meaning | Refactor code | New message key in `messages.properties` |
|---|---|---|---|
| `WBE0001` (DOC_TYPE blank) | required field | `WBE_VALIDATION` | already exists |
| `WBE0001` (TRCO_TRSC_CCY_CD blank or `NONE`) | required field / invalid currency | `WBE_VALIDATION` + `WBE_EINV_INVALID_CURRENCY` | new key `WBE_EINV_INVALID_CURRENCY=Invalid currency code.` |
| (no legacy code — silent skip on `42110000`) | "no data in range" Coocon code | (not an error) | n/a — surfaced as `nxisas_err_code=42110000` on the success envelope |
| `WBC1140` (transfer cancel) | NXISAS cancel during transfer | n/a (transfer flow not in scope) | n/a |
| `WBC1152` (generic cancel) | NXISAS user cancelled | `WBI_EINV_SCRAPE_CANCELLED` | new key |
| (legacy validates period via `ValidationHelper.validatePeriod`) | from > to / range too wide | `WBE_EINV_INVALID_PERIOD` | new key `WBE_EINV_INVALID_PERIOD=Invalid date range.` |
| (legacy returns null `BUSA_DOC_MSTR` row) | not found / wrong tenant | `WBE_EINV_NOT_FOUND` | new key |
| (legacy crashes on decrypt null) | `ISASSeedCBC.decrypt` returned null | `WBE_EINV_DECRYPT_FAILED` | new key |
| (legacy crashes on JSON parse) | scrape response JSON mis-shaped | `WBE_EINV_ENRICH_PARSE` | new key |

---

## 8. Audit WSVC_ID mapping

Per the slug-based rule (workspace CLAUDE.md "Cross-Tree Rules → Refactor identifier naming") and the 2026-05-09 override that retired the audit-DB-constant exception bucket, the new refactor writes audit rows with the **slug**, not the legacy WSVC id.

| Operation | Legacy `AUDIT_LOGS_INFM.WSVC_ID` | Refactor `AUDIT_LOGS_INFM.WSVC_ID` |
|---|---|---|
| List Sales | (legacy doesn't audit list) | `sales-einvoices` |
| List Expenses | (legacy doesn't audit list) | `expense-einvoices` |
| Currency update Sales | (legacy uses no audit table; uses `EXT_TRAN_CALL_HIS` for cross-system calls only) | `sales-einvoices` (ACT `UPDATE`) |
| Currency update Expenses | same | `expense-einvoices` |
| Scrape ingest | `nxisas_einvoice_c001` (in `EXT_TRAN_CALL_HIS.WEB_SVC_ID`) | `sales-einvoices` or `expense-einvoices` (ACT `SCRAPE_INGEST`), **plus** a row in `EXT_TRAN_CALL_HIS.WEB_SVC_ID = sales-einvoices` / `expense-einvoices` (slug, not the legacy WSVC id — same retirement decision) |
| Scrape enrich | `nxisas_einvoice_c002` | `sales-einvoices` or `expense-einvoices` (ACT `SCRAPE_ENRICH`) |
| Excel export | `einv_0001_001_r002_excel` | `sales-einvoices` / `expense-einvoices` (ACT `EXPORT`) |

`EXT_TRAN_CALL_HIS.SVC_ID` keeps the legacy `GDT_INQUIRY_SALES` / `GDT_INQUIRY_EXPENSES` literal — those are Coocon-side service identifiers that the gateway emits, not refactor identifiers.

---

## 9. View Setting screen-id mapping

| Screen | View Setting `WEB_SVC_ID` | `DYM_AREA_ID` |
|---|---|---|
| Sales list | `sales-einvoices` | `default` |
| Expense list | `expense-einvoices` | `default` |

(Defined by the V{N} View Setting seed migration — see feature doc §5.3 for the column set.)

The legacy code uses `data-jex-ml="einv_0001_001_ml001"` style IDs for column headers via Jex Studio's Multi-Language registry. Those ML codes are **not** ported — the refactor uses i18n JSON keys under `sales_einvoices.column.*` and `expense_einvoices.column.*` (FE-10 in the feature doc).

---

## 10. Things NOT migrated

| JEX surface | Reason |
|---|---|
| `BUSA_DOC_MSTR_HIS` audit triggers | Refactor uses service-layer `AuditLogService` instead of DB triggers (per the audit pattern from firm-banking-settings / contacts) |
| `BUSA_DOC_DETL_HIS`, `EINV_FEE_DETL_HIS`, `EINV_TAX_DETL_HIS` | Same — audit at service layer |
| Legacy `comm_0192_007` Approval popup (`einv_0001_001_view.jsp:412`) | Handled by the `approval-line` feature (already shipped) — link out, not embed |
| Simple Credit/Debit Confirmation layer popup (`einv_0001_001_view.jsp:919-1221`) | Belongs to the `bank-matching-receipt` feature (not yet started). The e-Invoice screen will surface a "Receipt match" action but the actual popup lives in the bank-matching feature folder. |
| Dashboard band for `PRDT_TYPE in ('C','D')` (HAENGSUNG-style) | Deferred — OQ-2 in feature doc |
| `inc_wabooks_nxisas_v2.jsp` (JSP include that pulls all `nxisas/*.js`) | Replaced by `wecms-web/src/features/einvoices/lib/nxisasBridge.ts` (browser-side WebSocket adapter) |
| `WABOOKSOSMCAPIUtil.callApi` round-trip to OSMC API endpoint | Replaced by a refactor-local lookup table or Spring config (OQ-5) |
| `wb_get_invoice_status(value, LANG_CD)` DB function | Refactor keeps the localized `EINV_INVOICESTATUS` string on the row (legacy parity), with OQ-3 proposing a coded enum |
| `wb_get_translate_text(...)` in the search OR-clause | Replaced by a JPA `Specification` using `LOWER(...) LIKE LOWER(...)` — no DB function dependency |
| Legacy `einv_0001_001_u000_view.jsp` (alt Sales focus_menu variant) | Folded into the single Sales route — the `focus_menu` distinction is a JEX LNB artifact, not a behaviour |
| Older NXISAS variants (`isasscaping_u000.js`, `isasscaping.js20200506`, `nxisas_v2_u000.js`, `nxisas.js`, `nxisas_api.js`, `nxisas_api_v2.js`) | Pre-2023 ancestor files — only `nxisas_v2.js` + `isasscaping.js` (the current `2023.4.7.0` version) port to the refactor |

---

## 11. Open questions echoed from feature doc

OQ-1 through OQ-10 are listed in the feature doc §9.2. Each OQ resolution updates this migration doc — when OQ-3 lands (coded vs localized invoice status) the `einv_invoicestatus` row in §2.1 gets a refactor-side enum mapping; when OQ-5 lands (Coocon JAR vs reimpl) the `IsasSeedCbcDecoder` entry in §4 may move; when OQ-2 lands (dashboard yes/no) the totals.buckets nested object in §2.1 may be removed.

---

## Appendix A — File-line audit trail

Every behavioural claim in the feature doc traces to one of these:

- `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_000_view.jsp` lines 25-28 (Sales shim)
- `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` lines 14-23 (einvType + gubun), 52-54 (NXISAS include), 329-357 (LNB branch), 362-466 (toolbar), 467-670 (dashboard), 673-780 (filter row), 808-911 (table shell), 919-1221 (credit-debit popup)
- `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_u000_view.jsp` line 25 (focus_menu = sale_einv_001)
- `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_r002_excel_view.jsp` lines 109-184 (validate + dynamic), 368-748 (Sales template rows), 765-1330 (Expenses template rows), 570-575 (rounding rule)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp` lines 53-71 (validate), 75-167 (dynamic), 171-198 (END_DATE lookup), 209-234 (IDO exec), 286-329 (REC normalisation)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_u001_act.jsp` lines 28-33 (validate), 74-94 (update)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c001_act.jsp` lines 32-46 (validate), 100-323 (decrypt + persist loop), 187 (Sales VD rule), 207-209 (Expenses extra checkResults), 249 (Expenses VD rule)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c002_act.jsp` lines 81-85 (validate), 87-97 (normalizeAcctNo), 99-112 (getCompanyNickNm), 114-125 (createTrcoRspbInfm), 140+ (main flow)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvhis_r001_act.jsp` lines 33-42 (validate), 44-75 (dynamic), 77-97 (normalize), 123-142 (exec)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_getCertFormInfo_act.jsp` lines 49-107 (R006 → R005 join)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_getModuleInfo_act.jsp` lines 47-63 (callApi → MODULE/CLASS extract)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_encrypt_act.jsp` lines 53-78 (encrypt loop)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_decrypt_act.jsp` lines 50-104 (decrypt loop)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_logging_act.jsp` lines 29-235 (runLogging recursion + svcId resolution), 237-247 (safeGet helpers), 249-285 (buildExtTranCallHis), 290-339 (top-level driver)
- `WECMS DEV/WECMS_STATIC/web/nxisas/js/isasscaping.js` lines 4 (license), 9-30 (Coocon URLs), 95-160 (connect), 167-300 (callback handlers), 225-300 (ONCOMPLETED)
- `WECMS DEV/WECMS_STATIC/web/nxisas/js/nxisas_v2.js` lines 29 (isSaleExpense), 32-66 (init), 77-133 (cancel handler), 137-200+ (save handler)
- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts` lines 14-91 (interfaces), 104-117 (init), 119-243 (controller signature), 244-499 (event handlers)

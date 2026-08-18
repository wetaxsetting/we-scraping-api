# Sales e-Invoices — Backend Source Code Index


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

Every backend source file for the feature, package `com.kosign.wecms.domain.einvoice`. Paths are relative to the `wecms-api/` repo root. **All files are shared with [Expenses e-Invoices](../expenses-e-invoices/README.md)** — the only divergence is the `type` discriminator and the two per-screen export-provider subclasses.

## Controller
| File | Role |
|---|---|
| [src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) | `@RequestMapping("/api/v1/addon")`. 7 endpoints: `GET /einvoices` (list, L106-131), `POST /einvoices/export` (list XLSX, L146-151), `POST /einvoices/export-details` (detail-expansion XLSX, L166-171), `POST /einvoices/scrape-results` (phase-1 intake, L187-191), `POST /einvoices/scrape-detail-results` (phase-2 intake, L206-210), `GET /einvoices/history` (GDT inquiry audit, L227-242), `GET /einvoices/detail-history` (re-poll history, Sales-only, L263-286). Thin — delegates to the service / `ExcelExportService`. |

## Services
| File | Role |
|---|---|
| [.../service/EInvoiceService.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java) | Core list logic. `list(...)` (L109-123 clamp/validate/sort → L142-181 Pageable overload reused by export), `resolveDocType` (L205-221: `SALES→'I'`, `EXPENSE`/`BILL`→`'B'`, rejects `E`/`S`→`WBE2002`), `validateDateRange` (L227-242), `buildKeywordPattern` (L291-303, `!`-escape), `toRow` (L309-333, `issuTm` sliced from `REG_DTM[8..14]`), `wireAmount` (L335-338). Constants `MAX_PAGE_SIZE=100`, `DEFAULT_PAGE_SIZE=10`, `DEFAULT_SORT = ISSU_DT DESC, REG_DTM DESC`. |
| [.../service/EInvoiceHistoryService.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceHistoryService.java) | `list` (L61-84) reads `EXT_TRAN_CALL_HIS` (GDT scrape audit; SVC_ID `GDT_INQUIRY_SALES`/`GDT_INQUIRY_EXPENSES`; success on RSLT_CD `00000000`/`1`). `detailHistory` (L108-136) → `BusaDocMstrRepository.findDetailHistory` (Sales-only, rejects Expense → `WBE2002`). `normalizeErrCont` (L189-206) extracts `Output.ErrorMessage` from the JSON `ERR_CONT` blob. |
| [.../service/EInvoiceScrapeService.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceScrapeService.java) | `saveScrapedInvoices(request)` — phase-1 `INVOICE_LIST` upsert into `BUSA_DOC_MSTR` by `(BIZ_KEY, DOC_ID)`; applies legacy `checkResult` / `invoiceStatus` branch rules (`CHECK_RESULT_BRANCH_A/B`, `VOID_STATUS_BRANCH_A/B` sets, L75-98); `EXPENSE_ISSU_TAX_ID="99999999999999999999"` (L63); writes an `EXT_TRAN_CALL_HIS` audit row. Returns `{savedCount, updatedCount, skippedCount, modDtm}`. |
| [.../service/EInvoiceDetailScrapeService.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailScrapeService.java) | `saveScrapedDetails(request)` — phase-2 `INVOICE_DETAIL`; locates the phase-1 master by recomposed `DOC_ID`, enriches master columns, deletes+re-writes `BUSA_DOC_DETL` / `EINV_FEE_DETL` / `EINV_TAX_DETL`, then calls `EInvoiceTrcoAutoRegistrar`. |
| [.../service/EInvoiceTrcoAutoRegistrar.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceTrcoAutoRegistrar.java) | Per-invoice counterparty auto-registration: resolves counterparty by type (SALES=buyer, EXPENSE=seller), creates/reuses `TRCO_INFM`, seeds `PGRO_BSAC_LNKN_INFM` group link (`USER_ID='ALL'`, `GRP_CD='S'`/`'B'`), upserts `TRCO_ACCT_INFO` bank accounts, writes a `TRCO_ACTV_HSTR` activity row; returns the resolved `TRCO_KEY` (or `ETC_TRCO_KEY="99999999999999999999"`, L79, when the invoice has no tax id). |
| [.../service/EInvoiceDetailExportService.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailExportService.java) | "Download Excel with Details" — `exportTo(response, request)`. Reuses `EInvoiceService.list` unpaged, expands each invoice into `LINE_TYPE` rows per `BUSA_DOC_DETL` / `EINV_FEE_DETL` / `EINV_TAX_DETL`, streams XLSX. Refactor port of legacy `einv_0001_001_r002_excel` detail expansion. |
| [.../service/EInvoiceSortField.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceSortField.java) | List sort whitelist (L25-57): `DATE_TIME→issuDt`, `INVOICE_NO→einvInvoicesymbol`, `BUYER→trcoCmnm`, `CURRENCY→trcoTrscCcyCd`, `TOTAL→totlAmt`, `STATUS→einvInvoicestatus`, `TAX→taxAmtSum`, `PRETAX→splyAmtSum`. Lenient `parse()` → null on unknown. |
| [.../service/EInvoiceDetailHistorySortField.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailHistorySortField.java) | Detail-history sort whitelist (L20-56): `DOC_ID→docId`, `ISSU_DT→issuDt`, `TRCO_CMNM→trcoCmnm`, `TOTL_AMT→totlAmt`, `EINV_INVOICESTATUS→einvInvoicestatus`. Default `ISSU_DT DESC`. |

## Repository
| File | Role |
|---|---|
| [.../repository/BusaDocMstrRepository.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/repository/BusaDocMstrRepository.java) | `JpaRepository<BusaDocMstr, BusaDocMstrId>`. `findFiltered` (L53-73, JPQL — baseline filters + 5-col keyword), `aggregateByCurrency` (L75-102, JPQL `GROUP BY trcoTrscCcyCd`), `findLinkableEInvoices` (L137-176, native — card-link), `findLinkableReceipts` (L195-236, native), `findLinkableByDocIds` (L245-259), `findDetailHistory` (L276-290, JPQL — detail-history). |
| [.../repository/BusaDocDetlRepository.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/repository/BusaDocDetlRepository.java) | `JpaRepository<BusaDocDetl, BusaDocDetlId>` — line items; written by the phase-2 scrape, read by the detail export. |
| [.../repository/EinvTaxDetlRepository.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/repository/EinvTaxDetlRepository.java) | `JpaRepository<EinvTaxDetl, EinvTaxDetlId>` — tax-rate breakdown. |
| [.../repository/EinvFeeDetlRepository.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/repository/EinvFeeDetlRepository.java) | `JpaRepository<EinvFeeDetl, EinvFeeDetlId>` — fee breakdown. |
| [.../repository/TrcoActvHstrRepository.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/repository/TrcoActvHstrRepository.java) | `JpaRepository<TrcoActvHstr, TrcoActvHstrId>` — counterparty activity rows written during auto-registration. |
| [.../repository/EInvoiceCcyAggregate.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/repository/EInvoiceCcyAggregate.java) | Projection record `{ ccyCd, count, totalAmount, taxAmount, pretaxAmount }` for the multi-currency totals query. |

## Entities
| File | Role |
|---|---|
| [.../entity/BusaDocMstr.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstr.java) | `@Table("BUSA_DOC_MSTR")`, `@IdClass(BusaDocMstrId)`. PK `(BIZ_KEY, DOC_ID)`. Maps the list subset + master-enrichment columns (see [database-mapping.md](database-mapping.md)). |
| [.../entity/BusaDocMstrId.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstrId.java) | Serializable composite key `(bizKey, docId)`. |
| [.../entity/BusaDocDetl.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocDetl.java) · [BusaDocDetlId.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocDetlId.java) | `@Table("BUSA_DOC_DETL")`, PK `(BIZ_KEY, DOC_ID, DISP_ORD)`. Line items. |
| [.../entity/EinvTaxDetl.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/EinvTaxDetl.java) · [EinvTaxDetlId.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/EinvTaxDetlId.java) | `@Table("EINV_TAX_DETL")`, PK `(BIZ_KEY, DOC_ID, DISP_ORD)`. Per-rate tax breakdown. |
| [.../entity/EinvFeeDetl.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/EinvFeeDetl.java) · [EinvFeeDetlId.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/EinvFeeDetlId.java) | `@Table("EINV_FEE_DETL")`, PK `(BIZ_KEY, DOC_ID, DISP_ORD)`. Fee breakdown. |
| [.../entity/TrcoActvHstr.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/TrcoActvHstr.java) · [TrcoActvHstrId.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/TrcoActvHstrId.java) | `@Table("TRCO_ACTV_HSTR")`, PK `(BIZ_KEY, ACTV_SEQ)`. Counterparty activity log written during phase-2 auto-registration. |

## DTOs
| File | Wire shape (lowercase snake_case via global Jackson) |
|---|---|
| [.../dto/EInvoiceListResponse.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceListResponse.java) | `rows[], total_count, total_pages, page_number, page_size, totals[]` + nested `CurrencyTotal{ ccy_cd, count, amount, tax_amount, pretax_amount }` |
| [.../dto/EInvoiceRow.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceRow.java) | `doc_id, disp_doc_id, issu_dt, issu_tm, einv_invoicesymbol, trco_cmnm, trco_tax_id, trco_trsc_ccy_cd, totl_amt, sply_amt_sum, tax_amt_sum, einv_invoicestatus, einv_invoicestatus_bef, prov_stts, due_dt, over_due_days` |
| [.../dto/EInvoiceHistoryResponse.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceHistoryResponse.java) · [EInvoiceHistoryRow.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceHistoryRow.java) | `rows[]`; row = `reg_dtm, type, svc_id, start_date, end_date, result, rslt_cd, err_message` |
| [.../dto/EInvoiceDetailHistoryResponse.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceDetailHistoryResponse.java) · [EInvoiceDetailHistoryRow.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceDetailHistoryRow.java) | `rows[]`; row = `doc_id, issu_dt, trco_cmnm, totl_amt, einv_invoice_status, hand_reg_yn, disp_doc_id` |
| [.../dto/EInvoiceScrapeRequest.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceScrapeRequest.java) · [ScrapedEInvoiceRow.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/ScrapedEInvoiceRow.java) · [EInvoiceScrapeResult.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceScrapeResult.java) | Phase-1 intake — request `{type, from_dt, to_dt, rslt_cd, rslt_msg, issuer_tax_code, engine_vendor, engine_version, invoices[]}`; result `{saved_count, updated_count, skipped_count, mod_dtm}` |
| [.../dto/EInvoiceDetailScrapeRequest.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceDetailScrapeRequest.java) · [ScrapedEInvoiceDetailRow.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/ScrapedEInvoiceDetailRow.java) · [EInvoiceDetailScrapeResult.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceDetailScrapeResult.java) | Phase-2 intake — full invoice detail incl. nested `products[]`/`fees[]`/`taxes[]`; result `{saved_count, updated_count, skipped_count, mod_dtm}` |

## Export
| File | Role |
|---|---|
| [.../export/EInvoiceExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/export/EInvoiceExportProvider.java) | Abstract base `ExportRowProvider`. Allowed FIELD_IDs `{DATE_TIME, INVOICE_NO, BUYER, CURRENCY, TOTAL, STATUS, TAX, PRETAX}` (L56-65). `fetch` reuses `EInvoiceService.list` unpaged (L87-114); `composeSubHeader` per-CCY via `AmountFormatter.forDisplay` (L135-153); `toFieldMap` (L165-176). |
| [.../export/SalesEInvoiceExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/export/SalesEInvoiceExportProvider.java) | `screenId() = "addon-sales-einvoices"` (L24), `defaultType() = "SALES"` (L29). |
| [.../export/BillEInvoiceExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/export/BillEInvoiceExportProvider.java) | Expenses twin — `screenId() = "addon-expenses-einvoices"` (L25), `defaultType() = "EXPENSE"` (L31). |

## Shared dependencies (other packages)
| File | Role |
|---|---|
| `common/export/*` | Shared XLSX pipeline (`ExcelExportService`, `ExcelExportRequest`, `ExcelWorkbookWriter`, `ExportableScreenRegistry`, `FieldIdAlias`). |
| `common/web/SortDirection.java`, `SortableField.java` | Common-sort primitives consumed by `EInvoiceSortField`. |
| `domain/banking/synchistory` `ExtTranCallHis*` | `EXT_TRAN_CALL_HIS` reader/writer used by scrape audit + inquiry history. |
| `common/enums/PrdtType.java` | Product gate (`F` = WeCMS Default; e-Invoice product gate deferred). |
| `domain/payment/cardtransactions/dto/LinkableEInvoiceRow.java`, `LinkableEInvoiceListResponse.java` | Consume `BusaDocMstrRepository.findLinkable*` for the card-link popup (separate feature — out of scope here). |

## Tests
| File | Role |
|---|---|
| [src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceServiceTest.java) | `resolveDocType` mapping, date-range validation, pagination clamp, sort fallback, multi-currency totals. |
| [.../service/EInvoiceScrapeServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceScrapeServiceTest.java) | Phase-1 upsert + legacy branch rules. |
| [.../service/EInvoiceTrcoAutoRegistrarTest.java](../../../../src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceTrcoAutoRegistrarTest.java) | Counterparty auto-registration (TRCO reuse / create, ETC fallback). |
| [.../export/EInvoiceExportProviderTest.java](../../../../src/test/java/com/kosign/wecms/domain/einvoice/export/EInvoiceExportProviderTest.java) | Export field map + subheader. |

## Migrations
`src/main/resources/db/migration/` — V15 (master schema subset), V23 (legacy parity, +80 cols on `BUSA_DOC_MSTR`), V17/V22/V50/V51/V53 (master seed), V238 (`BUSA_DOC_DETL`/`EINV_TAX_DETL`/`EINV_FEE_DETL` detail schema), V239 (`TRCO_ACTV_HSTR` schema), V45 (View Setting seed for addon einvoices), V49 (sales prod alignment), V52 (expenses demo seed), V54 (expenses prod alignment). See [database-mapping.md §4](database-mapping.md#4-flyway-migrations).

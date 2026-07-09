# Add-on Service — Validated Source-Code Index

**Audit date:** 2026-06-23
**Scope:** Sales e-Invoices · Expenses e-Invoices · Operation Status Inquiry
**Rule:** every reference below was opened and verified against the working tree on 2026-06-23. Code is the source of truth.

> **Module map.** The Add-on Service comprises **three menu screens backed by two Spring domains**:
> `domain/einvoice` serves **both** Sales (`type=SALES` → `DOC_TYPE='I'`) and Expenses (`type=EXPENSE`/`BILL` → `DOC_TYPE='B'`); `domain/operation` serves Operation Status Inquiry. No third backend domain exists for Add-on Service today.

---

## 1. e-Invoice domain (Sales + Expenses) — `wecms-api/src/main/java/com/kosign/wecms/domain/einvoice/`

### Controller

| Class | File:line | Notes |
|---|---|---|
| `EInvoiceController` | [controller/EInvoiceController.java](../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) | `@RequestMapping("/api/v1/addon")`. 7 handlers (below). Sales/Expenses share every endpoint via the `type` discriminator. |

**Endpoints (verified EInvoiceController.java:107-294):**

| Method | URI | Handler | `@PreAuthorize` | Request | Response |
|---|---|---|---|---|---|
| GET | `/einvoices` | `list` (L107-133) | `@menuAuthz.canAccessEInvoice(#p0)` | `type`, `from`, `to`, `keyword?`, `page?`, `size?`, `sortField?`, `sortDirection?` | `ApiResponse<EInvoiceListResponse>` |
| POST | `/einvoices/export` | `exportEInvoices` (L148-154) | `@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')` | `ExcelExportRequest` | XLSX stream |
| POST | `/einvoices/export-details` | `exportEInvoiceDetails` (L169-175) | `canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')` | `ExcelExportRequest` | XLSX stream (line-item expansion) |
| POST | `/einvoices/scrape-results` | `saveScrapeResults` (L191-196) | `canAccessAny(...)` | `EInvoiceScrapeRequest` | `ApiResponse<EInvoiceScrapeResult>` |
| POST | `/einvoices/scrape-detail-results` | `saveScrapeDetailResults` (L211-216) | `canAccessAny(...)` | `EInvoiceDetailScrapeRequest` | `ApiResponse<EInvoiceDetailScrapeResult>` |
| GET | `/einvoices/history` | `history` (L233-249) | `@menuAuthz.canAccessEInvoice(#p0)` | `type?`, `from`, `to`, `status?` | `ApiResponse<EInvoiceHistoryResponse>` |
| GET | `/einvoices/detail-history` | `detailHistory` (L270-294) | `@menuAuthz.canAccessEInvoice(#p0)` | `type`, `from`, `to`, `mod_dtm`, `sortField?`, `sortDirection?` | `ApiResponse<EInvoiceDetailHistoryResponse>` |

> **Date format guard** — `from`/`to` carry `@Pattern("\\d{8}")` (YYYYMMDD); `mod_dtm` carries `@Pattern("\\d{14}")`. Class is `@Validated`.

### Services

| Class | File:line | Key facts |
|---|---|---|
| `EInvoiceService` | [service/EInvoiceService.java](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java) | `resolveDocType` **L205-221**: `SALES`/`I`→`'I'`; `EXPENSE`/`BILL`/`B`→`'B'`; else `WBE2002`. `MAX_PAGE_SIZE=100`, `DEFAULT_PAGE_SIZE=10`. Default sort `ISSU_DT DESC, REG_DTM DESC`. IR-4: returns raw `EINV_INVOICESTATUS` (no `wb_get_invoice_status` port). |
| `EInvoiceScrapeService` | [service/EInvoiceScrapeService.java](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceScrapeService.java) | Phase-1 scrape intake → `BUSA_DOC_MSTR` + `EXT_TRAN_CALL_HIS` audit. |
| `EInvoiceDetailScrapeService` | [service/EInvoiceDetailScrapeService.java](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailScrapeService.java) | Phase-2 detail intake → `BUSA_DOC_DETL` / `EINV_FEE_DETL` / `EINV_TAX_DETL`. |
| `EInvoiceHistoryService` | [service/EInvoiceHistoryService.java](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceHistoryService.java) | `/history` (`EXT_TRAN_CALL_HIS`) + `/detail-history` (`BUSA_DOC_MSTR` by batch `MOD_DTM`). |
| `EInvoiceDetailExportService` | [service/EInvoiceDetailExportService.java](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailExportService.java) | Backs `/export-details` (legacy `einv_0001_001_r002_excel`). |
| `EInvoiceTrcoAutoRegistrar` | [service/EInvoiceTrcoAutoRegistrar.java](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceTrcoAutoRegistrar.java) | Counterparty auto-registration during scrape intake. |
| `EInvoiceSortField` | [service/EInvoiceSortField.java](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceSortField.java) | Whitelist: `DATE_TIME`, `INVOICE_NO`, `BUYER`, `CURRENCY`, `TOTAL`, `STATUS`, `TAX`, `PRETAX`. |
| `EInvoiceDetailHistorySortField` | [service/EInvoiceDetailHistorySortField.java](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceDetailHistorySortField.java) | Detail-history sort whitelist. |

### Repositories

| Interface | File | Tables / notable queries |
|---|---|---|
| `BusaDocMstrRepository` | [repository/BusaDocMstrRepository.java](../../src/main/java/com/kosign/wecms/domain/einvoice/repository/BusaDocMstrRepository.java) | `BUSA_DOC_MSTR`. `findFiltered` + `aggregateByCurrency` (shared WHERE chain, IR-2); `findDetailHistory`; plus card-link queries `findLinkableEInvoices` (`DOC_TYPE='B'`), `findLinkableReceipts` (`DOC_TYPE='E'`), `findLinkableByDocIds`. |
| `BusaDocDetlRepository` | [repository/BusaDocDetlRepository.java](../../src/main/java/com/kosign/wecms/domain/einvoice/repository/BusaDocDetlRepository.java) | `BUSA_DOC_DETL`. |
| `EinvFeeDetlRepository` | [repository/EinvFeeDetlRepository.java](../../src/main/java/com/kosign/wecms/domain/einvoice/repository/EinvFeeDetlRepository.java) | `EINV_FEE_DETL`. |
| `EinvTaxDetlRepository` | [repository/EinvTaxDetlRepository.java](../../src/main/java/com/kosign/wecms/domain/einvoice/repository/EinvTaxDetlRepository.java) | `EINV_TAX_DETL`. |
| `TrcoActvHstrRepository` | [repository/TrcoActvHstrRepository.java](../../src/main/java/com/kosign/wecms/domain/einvoice/repository/TrcoActvHstrRepository.java) | `TRCO_ACTV_HSTR` (auto-register). |
| `EInvoiceCcyAggregate` | [repository/EInvoiceCcyAggregate.java](../../src/main/java/com/kosign/wecms/domain/einvoice/repository/EInvoiceCcyAggregate.java) | Projection record for per-currency totals. |

### Entities (all `@Table` mirror legacy DDL)

`BusaDocMstr` (+`BusaDocMstrId`), `BusaDocDetl` (+`BusaDocDetlId`), `EinvFeeDetl` (+`EinvFeeDetlId`), `EinvTaxDetl` (+`EinvTaxDetlId`), `TrcoActvHstr` (+`TrcoActvHstrId`) — all under [entity/](../../src/main/java/com/kosign/wecms/domain/einvoice/entity/).

### DTOs — [dto/](../../src/main/java/com/kosign/wecms/domain/einvoice/dto/)

`EInvoiceListResponse` (+ nested `CurrencyTotal`), `EInvoiceRow` (16 fields, verified), `EInvoiceScrapeRequest`/`Result`, `EInvoiceDetailScrapeRequest`/`Result`, `EInvoiceHistoryRequest`(via params)/`EInvoiceHistoryResponse`/`EInvoiceHistoryRow`, `EInvoiceDetailHistoryResponse`/`EInvoiceDetailHistoryRow`, `ScrapedEInvoiceRow`, `ScrapedEInvoiceDetailRow`.

### Export providers — [export/](../../src/main/java/com/kosign/wecms/domain/einvoice/export/)

| Provider | `screenId()` | `defaultType()` |
|---|---|---|
| `SalesEInvoiceExportProvider` | `addon-sales-einvoices` | `SALES` |
| `BillEInvoiceExportProvider` | `addon-expenses-einvoices` | `EXPENSE` |
| `EInvoiceExportProvider` (abstract base) | — | shared `composeSubHeader` (per-CCY group) |

### Tests — `wecms-api/src/test/java/com/kosign/wecms/domain/einvoice/`

`service/EInvoiceServiceTest`, `service/EInvoiceScrapeServiceTest`, `service/EInvoiceTrcoAutoRegistrarTest`, `export/EInvoiceExportProviderTest`. **No dedicated tests** for `EInvoiceHistoryService`, `EInvoiceDetailScrapeService`, `EInvoiceDetailExportService`.

---

## 2. Operation Status domain — `wecms-api/src/main/java/com/kosign/wecms/domain/operation/`

### Controller

| Class | File:line | Notes |
|---|---|---|
| `OperationStatusController` | [controller/OperationStatusController.java](../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java) | `@RequestMapping("/api/v1/addon")`; **class-level** `@PreAuthorize("@menuAuthz.canAccess('addon-operation-status-inquiry')")`. |

**Endpoints (verified L63-136):**

| Method | URI | Handler | Request | Response |
|---|---|---|---|---|
| GET | `/operation-status` | `list` (L63-73) | `q?`, `companyStatus?`, `sortField?`, `sortDirection?`, `page?`, `size?` | `ApiResponse<OperationStatusListResponse>` |
| POST | `/operation-status/export` | `exportOperationStatus` (L84-89) | `ExcelExportRequest` | XLSX stream |
| POST | `/operation-status` | `create` (L100-104) — **Save** | `CreateOperationStatusRequest` | `ApiResponse<OperationStatusRow>` |
| POST | `/operation-status/inquire` | `inquire` (L114-118) | `InquireOperationStatusRequest` | `ApiResponse<InquireOperationStatusResponse>` |
| POST | `/operation-status/update` | `bulkUpdate` (L131-136) | `UpdateOperationStatusRequest?` (optional body) | `ApiResponse<UpdateOperationStatusResponse>` |

### Service

| Class | File:line | Key facts |
|---|---|---|
| `OperationStatusService` | [service/OperationStatusService.java](../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) | `list` (L113-173); `inquire` (L297-353, **live `OsmcGdtClient` bridge** + `EXT_TRAN_CALL_HIS` + `NXSIAS_MODULE_TRAN_LOG_HIS` audit); `create` (L382-413, persist + `(BIZ_KEY,TAXCODE)` dup guard); `bulkUpdate` (L433-547, re-poll + upsert, per-row counts). `resolveStatusCd` (L267-275) maps GDT Vietnamese text → 3-state code. Default sort `SEARCHDATETIME DESC, TAXCODE ASC`. |
| `OperationStatusSortField` | [service/OperationStatusSortField.java](../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusSortField.java) | Whitelist: `COMPANY`, `ADDRESS`, `OPERATION_STATUS`, `LAST_UPDATE`. |
| `OperationStatusFilter` | [service/OperationStatusFilter.java](../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusFilter.java) | `(keyword, companyStatus)` record. |
| `OsmcGdtClient` | `domain/operation/client/OsmcGdtClient.java` | GDT business-registration bridge (RestTemplate; `wecms.osmc.*` config). |

### Repository / Entity / DTOs / Export

| Kind | Item | Notes |
|---|---|---|
| Repository | `OprtSttsInfoRepository` ([repository/](../../src/main/java/com/kosign/wecms/domain/operation/repository/OprtSttsInfoRepository.java)) | `OPRT_STTS_INFO`; `findFiltered`, `findAllByBizKey`. |
| Entity | `OprtSttsInfo` (+`OprtSttsInfoId`) ([entity/](../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfo.java)) | `@Table("OPRT_STTS_INFO")`, composite PK `(BIZ_KEY, TAXCODE)`. |
| DTO | `OperationStatusListResponse`, `OperationStatusRow`, `CreateOperationStatusRequest`, `InquireOperationStatusRequest`, `InquireOperationStatusResponse`, `UpdateOperationStatusRequest`, `UpdateOperationStatusResponse` ([dto/](../../src/main/java/com/kosign/wecms/domain/operation/dto/)) | — |
| Export | `OperationStatusExportProvider` ([export/](../../src/main/java/com/kosign/wecms/domain/operation/export/OperationStatusExportProvider.java)) | `screenId()='addon-operation-status'`; allowed `COMPANY`,`ADDRESS`,`OPERATION_STATUS`,`LAST_UPDATE`. |
| Cross-domain audit | `ExtTranCallHisRepository`/`ExtTranCallHis` (`domain/banking/synchistory`), `NxsiasModuleTranLogHisRepository`/`NxsiasModuleTranLogHis` (`domain/banking/nxisas/audit`) | Written by inquire/update. |

### Tests

`service/OperationStatusServiceTest` (`wecms-api/src/test/java/com/kosign/wecms/domain/operation/service/`). No controller/integration test for the create/inquire/update endpoints beyond the service unit test.

---

## 3. Authorization — two distinct identifier namespaces (do not conflate)

| Concern | Sales | Expenses | Operation Status | Source |
|---|---|---|---|---|
| **Menu permission_cd** (runtime `@menuAuthz`, `MENU_ITEM.PERMISSION_CD`) | `addon-einvoices-sales` | `addon-einvoices-expenses` | `addon-operation-status-inquiry` | [MenuAuthorizationGuard.java:141-165](../../src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java#L141-L165); V243 nav seed |
| **View Setting / export screenId** | `addon-sales-einvoices` | `addon-expenses-einvoices` | `addon-operation-status` | export providers; V45/V49/V54/V56 seeds |

> ⚠️ Note the **word-order flip** between the two namespaces (`einvoices-sales` vs `sales-einvoices`). The guard (`@menuAuthz`) validates against `MENU_ITEM.PERMISSION_CD` + `PRODUCT_MENU` for the tenant's product, then admin-bypass, then per-user `USER_MENU_AUTH_INFM` grant.

**Guard methods used by Add-on (`MenuAuthorizationGuard`, bean `@menuAuthz`):**
- `canAccessEInvoice(type)` — `SALES`→`addon-einvoices-sales`; `EXPENSE`/`EXPENSES`→`addon-einvoices-expenses`; unknown → **fails closed**.
- `canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')` — on body-discriminated export/scrape endpoints.
- `canAccess('addon-operation-status-inquiry')` — class-level on the whole operation-status controller.

---

## 4. Frontend — `wecms-web/`

### Routes (App Router)

| Screen | Page | Route slug |
|---|---|---|
| Sales e-Invoices | [app/(dashboard)/addon/sales-einvoices/page.tsx](../../../wecms-web/app/(dashboard)/addon/sales-einvoices/page.tsx) | `/addon/sales-einvoices` |
| Expenses e-Invoices | [app/(dashboard)/addon/expenses-einvoices/page.tsx](../../../wecms-web/app/(dashboard)/addon/expenses-einvoices/page.tsx) | `/addon/expenses-einvoices` |
| Operation Status | [app/(dashboard)/addon/operation-status/page.tsx](../../../wecms-web/app/(dashboard)/addon/operation-status/page.tsx) | `/addon/operation-status` |

### Components — `wecms-web/components/addon/`

- **sales-einvoices/** (shared by Sales + Expenses): `SalesEInvoicesPage`, `EInvoicesPage`, `EInvoicesClient` (state owner + status post-filter), `EInvoiceTable`, `EInvoiceTableColumns`, `EInvoiceTableFooter`, `SalesEInvoiceActionBar`, `InvoiceStatusFilter`, `EInvoiceStatusText`, `EInvoiceHistoryDialog`, `EInvoiceDetailHistoryDialog`, `UpdateEInvoiceModal`, `useEInvoiceScrape.ts`.
- **expenses-einvoices/**: `ExpensesEInvoicesPage` (wraps `EInvoicesClient type="EXPENSE"`).
- **operation-status/**: `OperationStatusPage`, `OperationStatusClient`, `OperationStatusActionBar`, `InquireOperationStatusDialog`.

### API client — [lib/api/addon.ts](../../../wecms-web/lib/api/addon.ts)

`fetchEInvoices`, `scrapeEInvoices`, `scrapeEInvoiceDetails`, `fetchEInvoiceHistory`, `fetchEInvoiceDetailHistory`, `inquireOperationStatus`, `createOperationStatus`, `updateOperationStatus`, `fetchOperationStatus`. All unwrap `ApiResponse<T>` and throw `ApiCallError` (no mock fallback). Invoice-status filter is **client-side** (`status` param flows through but backend ignores it — IR-4).
Supporting: [lib/api/einvoiceStatus.ts](../../../wecms-web/lib/api/einvoiceStatus.ts) (`matchInvoiceStatus` regex — documented IR-4 BLOCKER), [lib/nxisas/einvoiceScrape.ts](../../../wecms-web/lib/nxisas/einvoiceScrape.ts) (local NXiSAS engine driver).

### Wire types — [lib/types/api.ts](../../../wecms-web/lib/types/api.ts)

`ApiEInvoiceListResponse`, `ApiEInvoiceScrapeRequest`/`Result`, `ApiEInvoiceScrapeDetailRequest`/`Result`, `ApiEInvoiceHistoryResponse`, `ApiEInvoiceDetailHistoryResponse`, `ApiOperationStatusListResponse`, `ApiOperationStatusRow` — lowercase snake_case per the wire contract.

---

## 5. Database / migrations (View Setting seeds + schema)

> All addon View-Setting + demo-seed migrations were archived in the 2026-06-09 renumber and **removed with `db/archive/` on 2026-06-23 (approved decision)** — not applied (the project reuses the existing legacy DB). Verify against [legacy-table-inventory.md](../references/database/legacy-table-inventory.md) before any new entity.

| Migration | Purpose |
|---|---|
| V15 / V240 / V241 | e-Invoice schema (`BUSA_DOC_MSTR` subset; `BUSA_DOC_DETL`; `TRCO_ACTV_HSTR`) |
| V16 | Operation status schema (`OPRT_STTS_INFO`, `EXT_TRAN_CALL_HIS`) |
| V45 / V49 / V54 | View Setting seeds for `addon-sales-einvoices` / `addon-expenses-einvoices` (CHECKBOX + 8 cols; PROD alignment) |
| V56 / V58 | View Setting seed for `addon-operation-status` (CHECKBOX + COMPANY/ADDRESS/OPERATION_STATUS/LAST_UPDATE; ADDRESS sortable) |
| V52 / V55 / V57 | Demo seeds (expenses e-invoices / operation status) |
| V157 / V158 (live) | `MENU_AUTH_ADMN_SYS` route-slug mapping + refactor seed (`addon-sales-einvoices`, `addon-expenses-einvoices`, `addon-operation-status`) |
| V243 (nav seed) | `MENU_ITEM` / `PRODUCT_MENU` permission_cds (`addon-einvoices-sales`, `addon-einvoices-expenses`, `addon-operation-status-inquiry`); F + M trees, **H omits Add-on** |

**Backing tables (legacy DDL):** `BUSA_DOC_MSTR`, `BUSA_DOC_DETL`, `EINV_FEE_DETL`, `EINV_TAX_DETL`, `TRCO_ACTV_HSTR`, `OPRT_STTS_INFO`, `EXT_TRAN_CALL_HIS`, `NXSIAS_MODULE_TRAN_LOG_HIS` — all present in the legacy table inventory.

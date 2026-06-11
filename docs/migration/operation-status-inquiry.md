# Operation Status Inquiry — Migration Guide (JEX → Spring Boot + Next.js)

**Slug:** `operation-status-inquiry` · **Refactor screen id:** `addon-operation-status`
**JEX counterpart:** `aosv_0001_*` (Add-on Service)
**Status:** Backend + frontend wired end-to-end. The former Inquire-popup **Save/persist** gap
(legacy `aosv_0001_002_c001`) was closed on **2026-06-03** — see §1 + §8.

> **Consolidated feature docs:** see [../features/addon-service/operation-status-inquiry/](../features/addon-service/operation-status-inquiry/) — especially [migration-guide.md](../features/addon-service/operation-status-inquiry/migration-guide.md). This file remains the narrative migration history.

This guide maps the legacy JEX implementation to the Spring Boot backend and Next.js frontend,
service-by-service and field-by-field. It is the migration-traceability bucket for legacy WSVC ids
(see `wecms-api/CLAUDE.md` → "Refactor identifier naming"). Legacy source-of-truth analysis:
[`docs/jex/features/operation-status-inquiry/`](../../../docs/jex/features/operation-status-inquiry/README.md).
Parity / gap detail: [operation-status-inquiry-parity.md](../audits/operation-status-inquiry-parity.md).

---

## 1. Service mapping (legacy WSVC → Spring endpoint)

| Legacy WSVC | Verb + path (refactor) | Controller method | Service method |
|---|---|---|---|
| `aosv_0001_001_r001` (list read) | `GET /api/v1/addon/operation-status` | `OperationStatusController#list` | `OperationStatusService#list` |
| `aosv_0001_001_u001` (bulk re-poll) | `POST /api/v1/addon/operation-status/update` | `#update` | `#bulkUpdate` |
| `aosv_0001_002_r001` (live lookup) | `POST /api/v1/addon/operation-status/inquire` | `#inquire` | `#inquire` |
| `aosv_0001_002_c001` (save) | `POST /api/v1/addon/operation-status` | `#create` | `#create` |
| (refactor-only) Excel export | `POST /api/v1/addon/operation-status/export` | `#export` | `OperationStatusExportProvider` |

Controllers live in `com.kosign.wecms.domain.operation`. Base path `/api/v1/addon`.

> **Wire-naming note.** Legacy WSVC ids (`aosv_0001_001_r001`, …) appear in Spring code **only** as
> Javadoc/migration citations and as the pinned `EXT_TRAN_CALL_HIS.WEB_SVC_ID` audit sentinel
> (`OperationStatusService.EXT_WEB_SVC_ID_R = "aosv_0001_001_r001"`). The View Setting screen id and
> the export `screenId` use the slug `addon-operation-status`. New REST URIs, packages, classes, and
> DTOs are slug-based per the identifier-naming rule.

---

## 2. Request/response shape translation

Legacy: `COMMON_HEAD` + UPPER_SNAKE. Refactor: `{status,data}` envelope + lowercase `snake_case`
(global Jackson `SNAKE_CASE`). The legacy field *meaning* is preserved; the casing flips.

### List read

| Legacy (`aosv_0001_001_r001`) | Refactor `GET /operation-status` |
|---|---|
| input `PAGE_NO` / `PAGE_PER_CNT` | query `page` (0-based) / `size` (clamped to 100, default 10) |
| input `SRCH_WD` | query `q` |
| input `COMPANYSTATUS` | query `companyStatus` |
| (sort via `SortHelper`) | query `sortField` / `sortDirection` (whitelist `OperationStatusSortField`) |
| output `TOT_CNT` | `data.total_count` (+ `page_number`, `page_size`, `total_pages`) |
| output `REC[]` | `data.rows[]` |

Row fields (legacy → wire):

| Legacy `REC.*` | Refactor wire (`OperationStatusRow`) |
|---|---|
| `TAXCODE` | `taxcode` |
| `COMPANYNAME` | `company_nm` |
| `COMPANYSTATUS` | `company_status` (raw Vietnamese, preserved) |
| (derived) | `status_cd` — refactor-only 3-state enum |
| `OFFICEADD` | `office_add` |
| `SEARCHDATETIME` | `last_update` |
| `REPRESENTATIVEFIRSTNAME` | (on `InquireOperationStatusResponse`, not on list row) |

### Update / Inquire

| Legacy | Refactor |
|---|---|
| `aosv_0001_001_u001` input `REC_IN:[{TAXCODE}]` | `POST /update` body `{ tax_codes: [...] }` (null/empty ⇒ all rows) → `{ total_attempted, updated, failed }` |
| `aosv_0001_002_r001` input `{TAXCODE}` | `POST /inquire` body `{ tax_code }` (`@Pattern ^\d{10}(-\d{3})?$`) → `{ tax_code, company_nm, company_status, status_cd, office_add, representative_first_name, search_datetime }` |

### Status code derivation (refactor addition)

`OperationStatusService.resolveStatusCd(...)` collapses the raw Vietnamese string into a 3-state
enum carried alongside the raw value:

| `status_cd` | Match on raw `COMPANYSTATUS` |
|---|---|
| `active_certificate_issued` | contains "đang hoạt động" |
| `suspended` | contains "ngừng" / "tạm" |
| `not_registered` | anything else (backend default) |

This replaces the legacy 12-string front-end legend (`aosv_0001_001.ts:58-72`). The raw string stays
on the wire so a richer mapping can land without a wire migration.

---

## 3. Error-code translation

| Legacy | Refactor | Meaning |
|---|---|---|
| `WSE9999` (bad tax-code format) | bean-validation `@Pattern` → `WBE_VALIDATION` | tax code not 10/13 digits |
| `WBE1152` (GDT no data) | `WBE_OSMC_NO_RESULT` | GDT returned nothing for the tax code |
| `WBE9999` (GDT API fault) | `WBE_OSMC_LOOKUP_FAILED` | scraping call failed |
| `WBE0003` (duplicate TAXCODE on save) | `WBE_OPERATION_STATUS_DUPLICATE` | persist duplicate guard on `(BIZ_KEY, TAXCODE)` (added 2026-06-03) |

Add new codes to `messages.properties` before throwing (`BusinessException`).

---

## 4. Persistence mapping

| Legacy table | Refactor entity / module | Notes |
|---|---|---|
| `OPRT_STTS_INFO` | `domain.operation.entity.OprtSttsInfo` (+ `OprtSttsInfoId`), `@IdClass`, PK `(BIZ_KEY, TAXCODE)` | Flyway **V16**; columns mirror legacy column-for-column |
| `EXT_TRAN_CALL_HIS` | reused from `banking.synchistory` (`ExtTranCallHisRepository`) | Flyway V16; `SEQ_NO` via `findMaxSeqNoForDay+1` (Java generator, not `wb_get_next_val`) |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | reused from `banking.nxisas.audit` | per-poll outcome log |

Column → JPA property → wire mapping:

| Legacy column | JPA property (`OprtSttsInfo`) | Wire field |
|---|---|---|
| `BIZ_KEY` | `bizKey` (id) | — (tenant from JWT, never on wire) |
| `TAXCODE` | `taxcode` (id) | `taxcode` |
| `COMPANYNAME` | `companyNm` | `company_nm` |
| `COMPANYSTATUS` | `companyStatus` | `company_status` |
| `OFFICEADD` | `officeAdd` | `office_add` |
| `SEARCHDATETIME` | `searchDatetime` (14) | `last_update` / `search_datetime` |
| `REPRESENTATIVEFIRSTNAME` | `representativeFirstName` | `representative_first_name` |
| `REG_ID/REG_DTM/MOD_ID/MOD_DTM` | `BaseEntity` audit | — |

Tenant scope: every repository call constrains `BIZ_KEY = TenantContext.currentBizKey()`; the wire
never accepts `BIZ_KEY` (legacy read it from the JEX session).

---

## 5. OSMC / GDT bridge mapping

| Legacy | Refactor |
|---|---|
| `WABOOKSOSMCAPIUtil.callApi("TRCO_TRCO001_00003", body, false)` | `OsmcGdtClient.lookup(taxCodes, uniqueKey)` (RestTemplate, 10s/60s) |
| `SVC_ID="BI09"` | `OperationStatusService.EXT_SVC_ID = "BI09"` + `OsmcProperties.apiId` |
| `NXISAS_CLASS/MODULE/JOB` constants | pinned constants in `OperationStatusService` (`COMMON` / `GLOBAL/VNM/PUBLIC_MASOTHUE` / `BUSSINESS_REGISTRATION_LIST`) |
| gateway URL (inline) | `wecms.osmc.url` in `application.yml` (Coocon smart-gateway) |

The refactor parses **both** legacy response shapes (`RespData[]` wrapper and flat
`Result.registrationDetail`) and accepts `"0000"` + `"00000000"` success codes. See memory
`reference_nxisas_gateway_urls` for the DEV/SIT/UAT/PROD gateway hosts.

---

## 6. Frontend mapping (JEX TS → Next.js)

| Legacy | Refactor |
|---|---|
| `aosv_0001_001_view.jsp` + `aosv_0001_001.ts` | route `/addon/operation-status` → `<OperationStatusClient>` → `<OperationStatusPage>` |
| `aosv_0001_002_view.jsp` + `aosv_0001_002.ts` | `<InquireOperationStatusDialog>` |
| `jexjs.plugin("ajax", "aosv_0001_001_r001")` | `fetchOperationStatus(...)` (`lib/api/addon.ts`) via `useApiData` |
| Update button → `updateStatus()` | `updateOperationStatus(targets)` → `POST /update` |
| status legend `ts:58-72` | `mapOperationCompanyRow` + `coerceOpStatus` (`lib/api/mappers.ts`) |
| client-side Excel (`ts:366-397`) | `useExcelExport()` → `POST /export` |
| client-side print (`ts:326-362`) | shared `printReport` |

Full JEX `_view.jsp` → Next.js route mapping:
[`wecms-web/docs/references/page-mapping/operation-status-inquiry.md`](../../../wecms-web/docs/references/page-mapping/operation-status-inquiry.md).

---

## 7. Recommended Spring package structure (as built)

```
com.kosign.wecms.domain.operation
├── controller/OperationStatusController.java     GET list · POST export/inquire/update
├── service/
│   ├── OperationStatusService.java               list / inquire / bulkUpdate
│   ├── OperationStatusFilter.java                (keyword, companyStatus)
│   └── OperationStatusSortField.java             SortableField whitelist
├── repository/OprtSttsInfoRepository.java        findFiltered / findAllByBizKey
├── entity/OprtSttsInfo.java (+ OprtSttsInfoId.java)
├── dto/  InquireOperationStatusRequest/Response · UpdateOperationStatusRequest/Response
│        · OperationStatusListResponse · OperationStatusRow
├── client/OsmcGdtClient.java · OsmcProperties.java · OsmcConfig.java
└── export/OperationStatusExportProvider.java
```

---

## 8. Save/persist path — DONE (2026-06-03)

The read/update/inquire/export slices were already done; the Save/persist path is now complete:

1. **Backend** — `POST /api/v1/addon/operation-status` (`OperationStatusController#create` →
   `OperationStatusService#create`) mirrors `aosv_0001_002_c001`: `CreateOperationStatusRequest`
   bean-validates the required set, `existsById((BIZ_KEY,TAXCODE))` duplicate guard →
   `BusinessException("WBE_OPERATION_STATUS_DUPLICATE")`, else `OprtSttsInfoRepository.save(...)`
   with `stampAudit`. Tenant from `TenantContext`. A concurrent-insert race
   (`DataIntegrityViolationException`) maps to the same duplicate error. Returns the persisted row
   as `OperationStatusRow` (same wire shape as the list rows).
2. **Frontend** — `createOperationStatus` (`lib/api/addon.ts`) + `InquireOperationStatusDialog.handleSave`:
   sends the inquired row (`tax_code`, `company_nm`, `company_status`, `office_add`, `search_datetime`,
   `representative_first_name`), shows a success dialog, calls `onSaved?.()` (parent refetch), and
   keeps the popup open on a duplicate/error so the user can adjust.
3. **Tests** — `OperationStatusServiceTest`: persist + audit + tenant scoping, duplicate guard
   (no save), concurrent-insert race → duplicate. See
   [test scenarios](../../../docs/testing/operation-status-inquiry.md) §Save.

No Flyway migration was needed (the table exists from V16). The View Setting popover remains
intentionally omitted (Figma has no list-view icon for this page) — unchanged.

---

## 9. Decisions / divergences to preserve

- **3-state `status_cd`** is a refactor-only addition; legacy WSVC has no such field. Raw
  `company_status` stays authoritative for filter/sort.
- **`EXT_WEB_SVC_ID_R = "aosv_0001_001_r001"`** is a deliberate legacy-id retention in the audit
  column only, justified in-code for cross-system audit alignment — not a violation of the slug rule.
- **Status-default divergence:** backend unknown → `not_registered`; frontend `coerceOpStatus`
  unknown → `active_certificate_issued`. Low impact (backend supplies `status_cd`); flag for
  alignment.
- **Inquire is read-only** in both systems; persistence is a separate action (Save / bulk Update).

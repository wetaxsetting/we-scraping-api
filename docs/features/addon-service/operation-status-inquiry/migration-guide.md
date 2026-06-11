# Operation Status Inquiry — JEX → Spring Migration Guide

How the legacy JEX `aosv_0001_*` services map onto the Spring Boot implementation. Narrative migration history also in [../../../migration/operation-status-inquiry.md](../../../migration/operation-status-inquiry.md). JEX is read-only functional reference under `WECMS DEV/`.

---

## 1. Service mapping

| JEX WSVC / action | Spring endpoint | Service method |
|---|---|---|
| `aosv_0001_001_r001` (list read) | `GET /api/v1/addon/operation-status` | `OperationStatusService#list` |
| `aosv_0001_002_r001` (single inquire) | `POST .../operation-status/inquire` | `#inquire` |
| `aosv_0001_002_c001` (save) | `POST .../operation-status` | `#create` |
| `aosv_0001_001_u001` (bulk update) | `POST .../operation-status/update` | `#bulkUpdate` |
| (no JEX twin) | `POST .../operation-status/export` | `ExcelExportService` + `OperationStatusExportProvider` |
| `aosv_0001_002_d001` (delete) | — | **not ported** (no delete on the refactor page) |

## 2. Legacy assets (read-only reference)

| Asset | Path under `WECMS DEV/` |
|---|---|
| WSVC (read) | `WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml` |
| WSVC (page / popup) | `WSVC.aosv_0001_001.xml`, `WSVC.aosv_0001_002.xml` |
| WSVC (update / inquire / save) | `WSVC.aosv_0001_001_u001.xml`, `WSVC.aosv_0001_002_r001.xml`, `WSVC.aosv_0001_002_c001.xml` |
| IDO | `IDO.OPRT_STTS_INFO_R001.xml`, `_R001_CNT.xml`, `_C001.xml`, `_U001.xml`, `_D001.xml` |
| View JSP | `WECMS/web/view/jex/wabooks/aosv/aosv_0001_001_view.jsp` |
| Action JSP | `.../action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp`, `_001_u001_act.jsp`, `_002_r001_act.jsp`, `_002_c001_act.jsp` |
| TS | `WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts` |
| Legacy entity / DAO | `WECMS/src/wabooks/entity/OprtSttsInfo.java`, `dao/OprtSttsInfoDAO.java` |

## 3. WSVC contract → wire field mapping

Legacy `WSVC.aosv_0001_001_r001.xml` is UPPER_SNAKE; the refactor wire is **lowercase snake_case** (CTO directive 2026-05-08, global Jackson `SNAKE_CASE`).

| Legacy WSVC field | Refactor wire field | Notes |
|---|---|---|
| `PAGE_NO` | `page` | zero-based (was 1-based input) |
| `PAGE_PER_CNT` | `size` | clamped to 100 |
| `SRCH_WD` | `q` | OR on company name + tax code |
| `COMPANYSTATUS` (in) | `companyStatus` | partial match, raw Vietnamese |
| `TOT_CNT` | `total_count` | |
| `TAXCODE` | `taxcode` | PK part |
| `COMPANYNAME` | `company_nm` | |
| `COMPANYSTATUS` (out) | `company_status` | raw; refactor adds derived `status_cd` (no legacy equivalent) |
| `OFFICEADD` | `office_add` | |
| `SEARCHDATETIME` | `last_update` (list) / `search_datetime` (inquire) | `yyyyMMddHHmmss` verbatim |
| `REPRESENTATIVEFIRSTNAME` | `representative_first_name` (inquire/create only) | not surfaced on the list row |

The legacy WSVC never exposes `BIZ_KEY` / `REG_*` / `MOD_*` on the wire; the refactor keeps that contract (tenant from JWT, audit columns server-managed).

## 4. Behavioural translation

| Legacy behaviour | Refactor behaviour |
|---|---|
| `WHERE biz_key = ?` from `SessionManager.getLoginUser` | `TenantContext.currentBizKey()` (JWT) |
| `companystatus ILIKE '%'||?||'%'` | `buildLikePattern` + `findFiltered` JPQL `ESCAPE '!'` |
| `SRCH_WD` OR on 2 columns (fixed whitelist) | identical 2-column OR |
| `SortHelper.getSortDynamic("aosv_0001_001_r001")` (View-Setting-driven) | `OperationStatusSortField` whitelist + `SEARCHDATETIME DESC, TAXCODE ASC` default |
| `wb_get_operat_stts(value, LANG_CD)` localisation | **not ported** — commented out in legacy too; raw string returned, frontend tints via regex; refactor adds the 3-state `status_cd` |
| `SeqHelper` next-val for `EXT_TRAN_CALL_HIS.SEQ_NO` | `ExtTranCallHisRepository.findMaxSeqNoForDay(...) + 1` (Java-side, no `wb_get_next_val` branch added) |
| Inquire/Update call NXISAS/OSMC bridge | `OsmcGdtClient` (RestTemplate) — same bridge, env-config-driven host |
| Save required-field set (`aosv_0001_002_c001`) | bean validation on `CreateOperationStatusRequest`; duplicate guard `WBE0003 → WBE_OPERATION_STATUS_DUPLICATE` |

## 5. Deliberate divergences

1. **3-state `status_cd`** added to the wire alongside the raw `company_status` — refactor-only; collapses the legacy 12-string TS legend for badge styling. Raw string stays authoritative for filter/sort/export.
2. **Excel export** is a refactor addition (no JEX counterpart) via the shared `common/export/` pipeline.
3. **Server-side pagination + sort whitelist** replace the legacy `PageInfo` / View-Setting `SortHelper` mechanics with the common refactor contracts.
4. **No localisation helper** (`wb_get_operat_stts`) — not in local Flyway, commented out in legacy.
5. **No delete** — legacy `aosv_0001_002_d001` IDO exists; the refactor page exposes none.

## 6. PROD active-table scope

All tables touched (`OPRT_STTS_INFO`, `EXT_TRAN_CALL_HIS`, `NXSIAS_MODULE_TRAN_LOG_HIS`) are in the PROD Active Table List — in scope per the Refactoring Enforcement Rule.

## 7. Migration status — ✅ complete

List, inquire, save, bulk update, and export are all implemented and frontend-wired. Remaining items are documented in [refactor-gap-analysis.md](refactor-gap-analysis.md).

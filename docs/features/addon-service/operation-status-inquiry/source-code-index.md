# Operation Status Inquiry — Backend Source Code Index

Every backend source file for the feature, package `com.kosign.wecms.domain.operation`. Paths are relative to the `wecms-api/` repo root.

## Controller
| File | Role |
|---|---|
| [src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java](../../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java) | `@RequestMapping("/api/v1/addon")`. 5 endpoints: `GET /operation-status` (list), `POST /operation-status/export`, `POST /operation-status` (create/save), `POST /operation-status/inquire`, `POST /operation-status/update`. Thin — delegates to the service / `ExcelExportService`. |

## Service
| File | Role |
|---|---|
| [.../service/OperationStatusService.java](../../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) | Core business logic. `list` (filter/sort/paginate + `resolveStatusCd`), `inquire` (GDT lookup + audit), `create` (save + duplicate guard), `bulkUpdate` (re-poll + upsert). Constants `MAX_PAGE_SIZE=100`, `DEFAULT_PAGE_SIZE=10`, audit sentinels `BI09` / `aosv_0001_001_r001` / NXISAS class·module·job. Helpers: `buildLikePattern`, `buildSort`, `toRow`, `resolveStatusCd`, `stampAudit`, `writeNxisasAudit`, `generateUniqueKey`. |
| [.../service/OperationStatusFilter.java](../../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusFilter.java) | Record `{ keyword, companyStatus }` + `empty()` factory. |
| [.../service/OperationStatusSortField.java](../../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusSortField.java) | Sort whitelist enum: `COMPANY→companyname`, `ADDRESS→officeadd`, `OPERATION_STATUS→companystatus`, `LAST_UPDATE→searchdatetime`. Lenient `parse()` → null on unknown. |

## Repository
| File | Role |
|---|---|
| [.../repository/OprtSttsInfoRepository.java](../../../../src/main/java/com/kosign/wecms/domain/operation/repository/OprtSttsInfoRepository.java) | `JpaRepository<OprtSttsInfo, OprtSttsInfoId>`. `findAllByBizKey` (bulk-update source) + `findFiltered(bizKey, companyStatusPattern, keywordPattern, Pageable)` JPQL with `(:param IS NULL OR LIKE … ESCAPE '!')` predicates. |

## Entity
| File | Role |
|---|---|
| [.../entity/OprtSttsInfo.java](../../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfo.java) | `@Table("OPRT_STTS_INFO")`, `@IdClass(OprtSttsInfoId)`. 11 columns (see [database-mapping.md](database-mapping.md)). |
| [.../entity/OprtSttsInfoId.java](../../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfoId.java) | Serializable composite key `(bizKey, taxcode)`. |

## DTOs
| File | Wire shape |
|---|---|
| [.../dto/OperationStatusRow.java](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/OperationStatusRow.java) | `taxcode, company_nm, company_status, status_cd, office_add, last_update` |
| [.../dto/OperationStatusListResponse.java](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/OperationStatusListResponse.java) | `rows[], total_count, page_number, page_size, total_pages` |
| [.../dto/InquireOperationStatusRequest.java](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/InquireOperationStatusRequest.java) | `tax_code` (`^\d{10}(-\d{3})?$`) |
| [.../dto/InquireOperationStatusResponse.java](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/InquireOperationStatusResponse.java) | `tax_code, company_nm, company_status, status_cd, office_add, representative_first_name, search_datetime` |
| [.../dto/CreateOperationStatusRequest.java](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/CreateOperationStatusRequest.java) | `tax_code, company_nm, company_status, office_add, search_datetime, representative_first_name` |
| [.../dto/UpdateOperationStatusRequest.java](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/UpdateOperationStatusRequest.java) | `tax_codes[]` (optional) |
| [.../dto/UpdateOperationStatusResponse.java](../../../../src/main/java/com/kosign/wecms/domain/operation/dto/UpdateOperationStatusResponse.java) | `total_attempted, updated, failed` |

## Export
| File | Role |
|---|---|
| [.../export/OperationStatusExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/operation/export/OperationStatusExportProvider.java) | `ExportRowProvider` for `screenId = "addon-operation-status"`. Reuses `OperationStatusService.list(... Pageable.unpaged())`. Allowed FIELD_IDs `{COMPANY, ADDRESS, OPERATION_STATUS, LAST_UPDATE}`; COMPANY cell = `"name / taxCode"`; OPERATION_STATUS emits raw Vietnamese; subheader `"<n> compan(y|ies) · As of <date>"`. |

## OSMC / GDT bridge client
| File | Role |
|---|---|
| [.../client/OsmcGdtClient.java](../../../../src/main/java/com/kosign/wecms/domain/operation/client/OsmcGdtClient.java) | `lookup(taxCodes, uniqueKey)` → `OsmcGdtResult` (error code/message + `OsmcGdtRegistration[]` + `OsmcGdtSearchOutcome[]`). Parses both legacy response shapes (`RespData[]` wrapper and flat `Result.registrationDetail`). |
| [.../client/OsmcProperties.java](../../../../src/main/java/com/kosign/wecms/domain/operation/client/OsmcProperties.java) | `@ConfigurationProperties("wecms.osmc")` — gateway URL, API id (`BI09` default), timeouts. |
| [.../client/OsmcConfig.java](../../../../src/main/java/com/kosign/wecms/domain/operation/client/OsmcConfig.java) | RestTemplate / client bean wiring. |

## Audit dependencies (shared, other packages)
| File | Role |
|---|---|
| `domain/banking/synchistory/repository/ExtTranCallHisRepository.java` + `entity/ExtTranCallHis.java` | `EXT_TRAN_CALL_HIS` writer (`findMaxSeqNoForDay`). |
| `domain/banking/nxisas/audit/NxsiasModuleTranLogHisRepository.java` + `entity/NxsiasModuleTranLogHis.java` | `NXSIAS_MODULE_TRAN_LOG_HIS` writer. |
| `common/export/*` | Shared Excel pipeline (`ExcelExportService`, `ExcelExportRequest`, `ExcelWorkbookWriter`, `ExportableScreenRegistry`). |
| `common/web/SortDirection.java`, `SortableField.java` | Common-sort primitives. |

## Tests
| File | Role |
|---|---|
| [src/test/java/com/kosign/wecms/domain/operation/service/OperationStatusServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/operation/service/OperationStatusServiceTest.java) | Mockito unit tests for the `create` (Save) path — see [test-scenarios.md](test-scenarios.md). |

## Migrations
`src/main/resources/db/migration/` — V16 (schema), V17 (seed), V56 (View Setting seed), V57 (demo seed), V58 (ADDRESS sort patch). See [database-mapping.md §4](database-mapping.md#4-flyway-migrations).

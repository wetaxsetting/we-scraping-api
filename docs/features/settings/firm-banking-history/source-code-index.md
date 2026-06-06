# Firm Banking History — Source Code Index

Legacy ↔ refactor file map for the whole feature (backend + frontend). Parent: [README.md](README.md).
Legacy-only index (read-only): [docs/jex/.../source-code-index.md](../../../../../docs/jex/features/firm-banking-history/source-code-index.md).

---

## 1. Legacy JEX sources (read-only reference)

| Layer | Path | Key anchors |
|---|---|---|
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0221_001_view.jsp` | title 94; date filter 101–126; svc-type list 130–185 (H gate 164/174); search box 188–192; table cols 213–234 |
| Action JSP (read) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/api/comm/comm_0221_001_r001_act.jsp` | `validateInput` 29–38; `getDynamic` 40–64; `getRows` 66–77; `setResult` 120–127 |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts` | `SERVICE_NAME_MAP` 40–52; `loadinitializeDate` 108; `loadData` 238–404; `fnSelect` 405–428; `fnDetails` 429–637; `resultMap` 459–478 |
| WSVC XML (API) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0221_001_r001.xml` | `ext` auth flags 24–27; `<in>` 32–39; `<out>` 40–43; `<subRule>` 44–57 |
| IDO XML (5-leg union) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.TB_FIRM_TRAN_R002.xml` | union legs 24–129; `<in>` 131–138; `<out>` 139–151 |
| DICTIONARY_VAN | `WECMS DEV/WECMS_ETC/xml/code/DICTIONARY_VAN/CD_*.xml` | 69 files keyed by `RS_CD` (F-leg result-message dictionary) |
| Enum (F-leg) | `WECMS DEV/WECMS/src/wabooks/enums/TranTp.java` | codes 201–204 |

---

## 2. Refactor backend (`wecms-api`)

Package root: `com.kosign.wecms.domain.setting.firmbankinghistory`

| Layer | File | Notes |
|---|---|---|
| Controller | [controller/FirmBankingHistoryController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/controller/FirmBankingHistoryController.java) | `GET` list + `POST /export`; `@PreAuthorize("hasAnyRole('ADMIN','USER')")` |
| Service | [service/FirmBankingHistoryService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistoryService.java) | `list()` + `listAll()`; validation, paging, tenant scope, `toRow()`; `DEFAULT_PAGE_SIZE=15`, `MAX_PAGE_SIZE=100`, `ALLOWED_SVC_TYPES={F,S,G,B}` |
| Sort whitelist | [service/FirmBankingHistorySortField.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistorySortField.java) | 6 FIELD_IDs → entity props; lenient `parse()` |
| Service-name resolver | [service/FirmBankingServiceNameResolver.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingServiceNameResolver.java) | `(svcType, svcNm) → fb_history.service.* key` |
| Repository | [repository/FirmBankingHistoryRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/repository/FirmBankingHistoryRepository.java) | single JPQL `search()` over the view |
| Entity | [entity/FirmBankingHistoryView.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/entity/FirmBankingHistoryView.java) | `@Immutable @Table(V_FIRM_BANKING_HISTORY)` |
| Entity id | [entity/FirmBankingHistoryViewId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/entity/FirmBankingHistoryViewId.java) | composite `(bizKey, tarnDtm, svcType, seqNo)` |
| DTO — row | [dto/FirmBankingHistoryRow.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/dto/FirmBankingHistoryRow.java) | 12-field record |
| DTO — response | [dto/FirmBankingHistoryListResponse.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/dto/FirmBankingHistoryListResponse.java) | `list`, `total_count`, `page_number`, `page_size`, `total_pages` |
| Export provider | [export/FirmBankingHistoryExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/export/FirmBankingHistoryExportProvider.java) | screenId `firm-banking-history`; 7 allowed FIELD_IDs |
| Enum — F-leg codes | [common/enums/TranTp.java](../../../../src/main/java/com/kosign/wecms/common/enums/TranTp.java) | 201/202/203 → key; 204 → null |
| Enum — leg discriminator | [common/enums/FirmBankingSvcType.java](../../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) | F/S/G/B → service-type key |

### Flyway migrations

| Version | File |
|---|---|
| V59 | [V59__firm_banking_history_source_tables.sql](../../../../src/main/resources/db/migration/V59__firm_banking_history_source_tables.sql) |
| V60 | [V60__firm_banking_history_view_and_fns.sql](../../../../src/main/resources/db/migration/V60__firm_banking_history_view_and_fns.sql) |
| V61 | [V61__firm_banking_history_demo_seed.sql](../../../../src/main/resources/db/migration/V61__firm_banking_history_demo_seed.sql) |
| V165 | [V165__firm_banking_history_view_setting_seed.sql](../../../../src/main/resources/db/migration/V165__firm_banking_history_view_setting_seed.sql) |

### Tests

| Test | File | Coverage |
|---|---|---|
| Service | [src/test/.../service/FirmBankingHistoryServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistoryServiceTest.java) | date/period/svc_type/keyword validation, paging clamp, sort whitelist, tenant scope |
| Resolver | [src/test/.../service/FirmBankingServiceNameResolverTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingServiceNameResolverTest.java) | TranTp code set + all four legs |
| Controller | [src/test/.../controller/FirmBankingHistoryControllerTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/controller/FirmBankingHistoryControllerTest.java) | snake_case wire shape, param binding, validation enveloping |
| Export provider | [src/test/.../export/FirmBankingHistoryExportProviderTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/export/FirmBankingHistoryExportProviderTest.java) | registry contract, filter/sort forwarding, row mapping |

---

## 3. Refactor frontend (`wecms-web`)

| Layer | File |
|---|---|
| Panel | `components/settings/firm-banking-history/FirmBankingHistoryPanel.tsx` |
| Filters | `components/settings/firm-banking-history/FirmBankingHistoryFilters.tsx` |
| Table | `components/settings/firm-banking-history/FirmBankingHistoryTable.tsx` |
| Detail popover | `components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx` |
| Types | `components/settings/firm-banking-history/types.ts` |
| API client | `lib/api/firmBankingHistory.ts` |
| Mapper | `lib/api/mappers.ts` (`mapFirmBankingHistoryRow`) |
| Wire types | `lib/types/api.ts` (`ApiFirmBankingHistoryRow`, `ApiFirmBankingHistoryListResponse`) |
| VAN dictionary | `lib/i18n/dictionaryVan.ts` + `dictionaryVan.data.ts` |
| Scraping result codes | `lib/i18n/scrapingResultCodes.ts` + `scrapingResultCodes.data.ts` |
| Mount | `components/settings/SettingsModal.tsx` (panel `firm_banking_history`) + `SettingsNav.tsx` (perm `settings-firm-banking-history`) |
| i18n | `locales/{en,ko,vi}.json` — `settings.firm_banking_history.*`, `fb_history.service.*`, `fb_history.result.*` |

Full frontend detail: [wecms-web component-index](../../../../../wecms-web/docs/features/settings/firm-banking-history/component-index.md).

---

## 4. Cross-module / shared dependencies

| Dependency | Used for |
|---|---|
| `common/export/` (`ExcelExportService`, `ExcelWorkbookWriter`, `ExportableScreenRegistry`) | XLSX export pipeline |
| `common/web/` (`SortDirection`, `SortableField`) | sort whitelist plumbing |
| `common/security/JwtAuthenticationFilter` + `TenantContext` | tenant scope + authority |
| `common/exception/GlobalExceptionHandler` + `TraceIdFilter` | error envelope + `trace_id` |
| `domain/setting/screenoption` (View Setting) | export column intersection (seed only; no popover on this screen) |
| `wb_get_bank_nm` (V44) | S-leg bank-name resolution |

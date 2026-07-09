# ERP Transaction History — Source Code Index

Legacy ↔ refactor file map. JEX paths are **read-only reference**. Frontend mirror:
[wecms-web/docs/features/settings/transaction-history/component-index.md](../../../../../wecms-web/docs/features/settings/transaction-history/component-index.md).

---

## Refactor — backend (`wecms-api`)

Package `com.kosign.wecms.domain.setting.erptransactionhistory`.

| Layer | File |
|---|---|
| Controller | [controller/ErpTrnsHistoryController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryController.java) |
| Read service | [service/ErpTrnsHistoryService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryService.java) |
| Sort whitelist | [service/ErpTrnsHistorySortField.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistorySortField.java) |
| File resolver | [service/ErpTrnsHistoryFileResolver.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryFileResolver.java) |
| Storage config | [service/ErpTrnsHistoryStorageProperties.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryStorageProperties.java) · [service/ErpTrnsHistoryConfig.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryConfig.java) |
| Audit writer (inert) | [service/ErpTrnsHistoryWriter.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryWriter.java) |
| Repository | [repository/ErpTrnsHstrRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/repository/ErpTrnsHstrRepository.java) |
| Entity | [entity/ErpTrnsHstr.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/entity/ErpTrnsHstr.java) |
| Composite PK | [entity/ErpTrnsHstrId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/entity/ErpTrnsHstrId.java) |
| DTOs | [dto/ErpTrnsHistoryListResponse.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/dto/ErpTrnsHistoryListResponse.java) · [dto/ErpTrnsHistoryRow.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/dto/ErpTrnsHistoryRow.java) · [dto/ErpTrnsHistoryProjection.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/dto/ErpTrnsHistoryProjection.java) · [dto/ErpTrnsHistoryCounts.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/dto/ErpTrnsHistoryCounts.java) |
| Export provider | [export/ErpTrnsHistoryExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/export/ErpTrnsHistoryExportProvider.java) |
| Flyway (schema) | [db/migration/V54__erp_trns_hstr.sql](../../../../src/main/resources/db/migration/V54__erp_trns_hstr.sql) |
| Messages | [messages.properties](../../../../src/main/resources/messages.properties) — `WBE2014` (82), `WBE2015` (83), `WBE9001` (91), `settings.erp_transaction_history.direction.*` (358-360), `…type.NN` (363+) |
| Tests | [service/ErpTrnsHistoryServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryServiceTest.java) (33 `@Test`) · [controller/ErpTrnsHistoryControllerTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryControllerTest.java) (9 `@Test`) · [export/ErpTrnsHistoryExportProviderTest.java](../../../../src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/export/ErpTrnsHistoryExportProviderTest.java) (6 `@Test`) |

Validation is service-side (`ErpTrnsHistoryService` private methods) — no standalone validator class.
The download path streams via the controller's `InputStreamResource`; the export path runs through the
shared `common/export/ExcelExportService`.

> **JUnit coverage now exists** for `erptransactionhistory` in `src/test/` (confirmed this pass):
> [`ErpTrnsHistoryServiceTest`](../../../../src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryServiceTest.java)
> (33 `@Test`), [`ErpTrnsHistoryControllerTest`](../../../../src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/controller/ErpTrnsHistoryControllerTest.java)
> (9 `@Test`), and [`ErpTrnsHistoryExportProviderTest`](../../../../src/test/java/com/kosign/wecms/domain/setting/erptransactionhistory/export/ErpTrnsHistoryExportProviderTest.java)
> (6 `@Test`) — 48 tests total, mirroring the `purpose`/`contacts` test shape. The coverage map in
> [test-scenarios.md §2](test-scenarios.md) is now **existing coverage**, not scenarios to author.

---

## Refactor — frontend (`wecms-web`)

Folder `components/settings/erp-transaction-history/`.

| Layer | File |
|---|---|
| Panel | [components/settings/erp-transaction-history/ErpTransactionHistoryPanel.tsx](../../../../../wecms-web/components/settings/erp-transaction-history/ErpTransactionHistoryPanel.tsx) |
| Tabs | [ErpTransactionHistoryTabs.tsx](../../../../../wecms-web/components/settings/erp-transaction-history/ErpTransactionHistoryTabs.tsx) |
| Filters | [ErpTransactionHistoryFilters.tsx](../../../../../wecms-web/components/settings/erp-transaction-history/ErpTransactionHistoryFilters.tsx) |
| Table | [ErpTransactionHistoryTable.tsx](../../../../../wecms-web/components/settings/erp-transaction-history/ErpTransactionHistoryTable.tsx) |
| In-memory types | [types.ts](../../../../../wecms-web/components/settings/erp-transaction-history/types.ts) |
| API client | [lib/api/erpTransactionHistory.ts](../../../../../wecms-web/lib/api/erpTransactionHistory.ts) — `fetchErpTransactionHistory`, `getErpTransactionHistoryDownloadUrl` |
| Wire types | [lib/types/api.ts](../../../../../wecms-web/lib/types/api.ts) — `ApiErpTransactionHistoryRow`, `ApiErpTransactionHistoryListResponse` |
| Mapper | [lib/api/mappers.ts](../../../../../wecms-web/lib/api/mappers.ts) — `mapErpTransactionHistoryRow` |
| Download helper | [lib/file.ts](../../../../../wecms-web/lib/file.ts) — `downloadFile(url, filename)` (authenticated XHR + blob) |
| Sort hook | [hooks/useTableSort.ts](../../../../../wecms-web/hooks/useTableSort.ts) |

> No `mockData.ts` was found in the live `erp-transaction-history/` folder — the panel is **fully
> wired** to the live API via `useApiData`, with no mock fallback (the flat WEB doc's planned
> `mockData.ts` rename was superseded by the API wiring).

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Path |
|---|---|
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0219_001_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0219_001.ts` |
| List action | `…/web/WEB-INF/action/jex/wabooks/comm/comm_0219_001_r001_act.jsp` |
| Upload action (not ported) | `…/comm_0219_001_c001_act.jsp` |
| Download view | `…/web/view/jex/wabooks/api/comm/comm_0219_001_r002_view.jsp` |
| IDO | `…/xml/service/IDO/IDO.ERP_TRNS_HSTR_R001.xml` · `…_R001_CNT.xml` · `…_C001.xml` · `…_U001.xml` · `…_D001.xml` |
| WSVC | `…/xml/service/WSVC/WSVC.comm_0219_001_r001.xml` · `…_r002.xml` · `…_c001.xml` |
| Entity / PK | `WECMS DEV/WECMS/src/wabooks/entity/ErpTrnsHstr.java` |
| DAO | `WECMS DEV/WECMS/src/wabooks/dao/ErpTrnsHstrDAO.java` |
| Enums | `…/src/wabooks/enums/ErpType.java` · `ErpTypeCd.java` |
| Batch writers (not ported) | `…/src/batch/jex/wabooks/erp/ERP_0100_001.java` (+ `_002/_003/_004`) · `…/helper/ErpDataRefreshHelper.java` · `…/helper/ErpTrnsHisHelper.java` |

Full legacy analysis + the 17-code label table: [../transaction-history.md](../transaction-history.md).

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/settings/transaction-history/`
- Preserved flat spec: [../transaction-history.md](../transaction-history.md)
- Frontend feature folder: [wecms-web/docs/features/settings/transaction-history/](../../../../../wecms-web/docs/features/settings/transaction-history/README.md)
- Table baseline: [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)

# Purpose — Source Code Index

Legacy ↔ refactor file map for the Purpose feature. JEX paths are **read-only reference**. Frontend
mirror: [wecms-web/docs/features/settings/purpose/component-index.md](../../../../../wecms-web/docs/features/settings/purpose/component-index.md).

---

## Refactor — backend (`wecms-api`)

| Layer | File |
|---|---|
| Controller | [controller/PurposeSettingsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsController.java) |
| Service | [service/PurposeSettingsService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/service/PurposeSettingsService.java) |
| Excel service | [service/PurposeExcelService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/service/PurposeExcelService.java) |
| Repository (main) | [repository/PurposeSettingsRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/repository/PurposeSettingsRepository.java) |
| Repository (in-use view) | [repository/PurposeInUseRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/repository/PurposeInUseRepository.java) |
| Entity | [entity/BusiUsagSet.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/entity/BusiUsagSet.java) |
| Composite PK | [entity/BusiUsagSetId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/entity/BusiUsagSetId.java) |
| In-use projection | [entity/PurposeInUseRow.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/entity/PurposeInUseRow.java) |
| DTOs | dto/PurposeSettingListResponse · PurposeSettingRow · PurposeSettingDetail · PurposeSettingCreateRequest · PurposeSettingUpdateRequest · PurposeReorderRequest · PurposeReorderItem · PurposeExcelPreview · PurposeExcelPreviewRow · PurposeExcelFieldError · PurposeExcelImportRequest · PurposeExcelImportResult — all under [dto/](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/dto/) |
| Flyway | `V80` (BUSI_USAG_SET_INFM schema) · `V81` (v_purpose_in_use view) · `V82` (wb_get_next_val branch) — [db/migration/](../../../../src/main/resources/db/migration/) |
| Tests | [PurposeSettingsServiceTest](../../../../src/test/java/com/kosign/wecms/domain/setting/purposesettings/service/PurposeSettingsServiceTest.java) · [PurposeExcelServiceTest](../../../../src/test/java/com/kosign/wecms/domain/setting/purposesettings/service/PurposeExcelServiceTest.java) · [PurposeSettingsControllerTest](../../../../src/test/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsControllerTest.java) |

Validation is Bean Validation on the record DTOs + service-layer guards (no standalone validator class); the in-use guard rides the `v_purpose_in_use` view + `PurposeInUseRepository`.

---

## Refactor — frontend (`wecms-web`)

| Layer | File |
|---|---|
| Panel | [components/settings/purpose/PurposePanel.tsx](../../../../../wecms-web/components/settings/purpose/PurposePanel.tsx) |
| Tree table | [components/settings/purpose/PurposeTable.tsx](../../../../../wecms-web/components/settings/purpose/PurposeTable.tsx) · [PurposeRow.tsx](../../../../../wecms-web/components/settings/purpose/PurposeRow.tsx) · [PurposeTableColumns.tsx](../../../../../wecms-web/components/settings/purpose/PurposeTableColumns.tsx) |
| Inline add/edit | [components/settings/purpose/PurposeFormRow.tsx](../../../../../wecms-web/components/settings/purpose/PurposeFormRow.tsx) · [ParentPurposePicker.tsx](../../../../../wecms-web/components/settings/purpose/ParentPurposePicker.tsx) |
| Row actions / delete | [components/settings/purpose/RowActions.tsx](../../../../../wecms-web/components/settings/purpose/RowActions.tsx) |
| Excel upload | [components/settings/purpose/ExcelUploadDialog.tsx](../../../../../wecms-web/components/settings/purpose/ExcelUploadDialog.tsx) (wraps the shared [`<ExcelUploadDialog>`](../../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx)) |
| API client + wire types + mappers | [lib/api/purposeSettings.ts](../../../../../wecms-web/lib/api/purposeSettings.ts) · [components/settings/purpose/types.ts](../../../../../wecms-web/components/settings/purpose/types.ts) |
| Mock fallback | [components/settings/purpose/mockData.ts](../../../../../wecms-web/components/settings/purpose/mockData.ts) |

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Path |
|---|---|
| View JSP (main) | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0187_001_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0187_001.ts` |
| List action | `…/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_r001_act.jsp` |
| Name-search action | `…/comm_0187_001_r002_act.jsp` |
| Create action | `…/comm_0187_001_c001_act.jsp` |
| Toggle DISP_YN | `…/comm_0187_001_u001_act.jsp` |
| Bulk update + soft-delete (REC / REC2) | `…/comm_0187_001_u002_act.jsp` |
| Reset-to-system (JEX-only, not ported) | `…/comm_0187_001_u003_act.jsp` |
| Reorder | `…/comm_0187_001_u004_act.jsp` |
| ERP account-mapping (separate feature) | `…/comm_0187_002_*_act.jsp` |
| Deleted/unused list + restore + permanent delete (JEX-only, not ported) | `…/comm_0187_003_r001_act.jsp` · `…/comm_0187_003_u001_act.jsp` |
| Excel import | `…/comm_0187_004_*_act.jsp` |
| IDO | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_USAG_SET_INFM_R001.xml` · `_C001` · `_D001` (+ `_R007` count) |
| WSVC | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0187_001*.xml` |
| Entity / PK | `WECMS DEV/WECMS/src/wabooks/entity/BusiUsagSetInfm.java` · `BusiUsagSetInfmPk.java` |
| DAO | `WECMS DEV/WECMS/src/wabooks/dao/BusiUsagSetInfmDAO.java` |

Full legacy analysis: [docs/jex/features/purpose/source-code-index.md](../../../../../docs/jex/features/purpose/source-code-index.md).

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/settings/purpose/`
- Preserved flat spec: [../purpose.md](../purpose.md)
- Business rules / DB schema / migration / audit: see [README.md § Related & preserved docs](README.md#related--preserved-docs)
- Frontend feature folder: [wecms-web/docs/features/settings/purpose/](../../../../../wecms-web/docs/features/settings/purpose/README.md)
- JEX legacy spec set: [docs/jex/features/purpose/](../../../../../docs/jex/features/purpose/README.md)

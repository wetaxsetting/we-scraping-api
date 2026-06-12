# Settings > VAS Account — Source Code Index

Legacy JEX ↔ refactor backend ↔ refactor frontend file index, plus id correspondence. JEX source of
truth: [docs/jex/features/vas-account/source-code-index.md](../../../../../docs/jex/features/vas-account/source-code-index.md).

---

## 1. Legacy JEX (read-only reference)

| Layer | File |
|---|---|
| View JSP — list | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0217_001_view.jsp` |
| View JSP — Excel upload popup | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0217_002_view.jsp` |
| View JSP — add | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0217_003_view.jsp` |
| View JSP — edit | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0217_004_view.jsp` |
| Action — list/search | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_001_r001_act.jsp` |
| Action — soft delete | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_001_d001_act.jsp` |
| Action — Excel parse+validate | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_002_r001_act.jsp` |
| Action — Excel per-cell validate | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_002_r002_act.jsp` *(NOT ported — see migration-guide)* |
| Action — Excel import save | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_002_c001_act.jsp` |
| Action — create | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_003_c001_act.jsp` |
| Action — detail | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_004_r001_act.jsp` |
| Action — update | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0217_004_u001_act.jsp` |
| Action — lookup picker | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_1002_001_act.jsp` |
| IDO — read all | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_R001.xml` |
| IDO — lookup (`DISP_VAS_ACCT_NM`) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_R002.xml` |
| IDO — insert | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_C001.xml` |
| IDO — full update | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_U001.xml` |
| IDO — DEL_YN update (soft delete) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_U002.xml` |
| IDO — physical delete (dead code) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSI_VAS_ACCT_INFM_D001.xml` |
| WSVC — `comm_0217_001` + r001/d001, `comm_0217_002` + r001/r002/c001, `comm_0217_003` + c001, `comm_0217_004` + r001/u001, `comm_1002_001` | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0217_*.xml`, `WSVC.comm_1002_001.xml` |
| Entity / PK | `WECMS DEV/WECMS/src/wabooks/entity/BusiVasAcctInfm.java`, `BusiVasAcctInfmPk.java` |
| DAO | `WECMS DEV/WECMS/src/wabooks/dao/BusiVasAcctInfmDAO.java` |
| Enums | `WECMS DEV/WECMS/src/wabooks/enums/VasAcctType.java`, `AStts.java` |
| Sequence helper | `WECMS DEV/WECMS/src/wabooks/helper/SeqHelper.java` |
| Frontend (legacy) | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0217_001..004.ts` |

## 2. Refactor backend (`wecms-api`)

| Layer | File |
|---|---|
| Controller | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/controller/VasAccountController.java` |
| Service (CRUD/list/lookup) | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAccountService.java` |
| Service (Excel) | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAccountExcelService.java` |
| Sequence generator | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAcctSeqNoGenerator.java` |
| Sort whitelist | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/service/VasAccountSortField.java` |
| Repository | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/repository/BusiVasAcctInfmRepository.java` |
| Entity / PK | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/entity/BusiVasAcctInfm.java`, `BusiVasAcctInfmId.java` |
| Export provider | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/export/VasAccountExportProvider.java` |
| DTOs | `src/main/java/com/kosign/wecms/domain/setting/vasaccount/dto/` — `VasAccountListResponse`, `VasAccountRow`, `VasAccountLookupResponse`, `VasAccountLookupRow`, `VasAccountCreateRequest`, `VasAccountUpdateRequest`, `VasAccountExcelPreview`, `VasAccountExcelPreviewRow`, `VasAccountExcelFieldError`, `VasAccountExcelImportRequest`, `VasAccountExcelImportResult` |
| Enums | `src/main/java/com/kosign/wecms/common/enums/AStts.java`, `VasAcctType.java` |
| Auth guard | `src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java` (`@Component("menuAuthz")`) |
| Migrations | `src/main/resources/db/migration/V77__*` (schema), `V78__*` (seed), `V79__*` (View Setting seed) |
| i18n | `src/main/resources/messages.properties`, `messages_en.properties` (+ partial ko/vi/km) — `settings.vas_account.*`, `audit.vas_account.*`, `WBE0003`/`WBE2040`/`WBE2041`/`WBE2042`/`WBE2043` |
| Tests | `src/test/java/com/kosign/wecms/domain/setting/vasaccount/` — `service/VasAccountServiceTest`, `service/VasAccountExcelServiceTest`, `service/VasAccountExportProviderTest`, `service/VasAccountSortFieldTest`, `controller/VasAccountControllerTest`, `VasAccountIntegrationTest` (`@Disabled` — F1) |

## 3. Refactor frontend (`wecms-web`)

| Surface | File |
|---|---|
| Panel (composition root) | `components/settings/vas-account/VasAccountPanel.tsx` |
| Table | `components/settings/vas-account/VasAccountTable.tsx` (+ `VasAccountTableColumns.tsx`) |
| Add/Edit dialog | `components/settings/vas-account/AddVasAccountDialog.tsx` |
| Excel upload wrapper | `components/settings/vas-account/ExcelUploadDialog.tsx` (wraps shared `components/shared/excel-upload/ExcelUploadDialog.tsx`) |
| Row menu | `components/settings/vas-account/VasAccountRowMenu.tsx` |
| Hooks | `components/settings/vas-account/useVasAccount.ts` |
| Mappers + tree builder | `components/settings/vas-account/mappers.ts` |
| In-memory types | `components/settings/vas-account/types.ts` |
| API client | `lib/api/vasAccount.ts` |
| Wire types | `lib/types/api.ts` (`ApiVasAccount*`) |
| Picker consumer | `components/settings/bank-account/VasAccountPicker.tsx` |
| Nav wiring | `components/settings/SettingsNav.tsx`, `SettingsModal.tsx` |
| View Setting registry | `lib/viewSettingScreens.ts` (`SETTINGS_VAS_ACCOUNT = "vas-account"`) |

## 4. ID correspondence

| Surface | Value |
|---|---|
| Feature slug (all trees) | `vas-account` |
| REST base | `/api/v1/settings/vas-account` |
| Audit `WSVC_ID` | `vas-account` |
| View Setting `WEB_SVC_ID` / area | `vas-account` / `default` |
| i18n prefix | `settings.vas_account.*` · `audit.vas_account.*` |
| Menu auth code | `settings-core-vas-account` |
| Legacy WSVC family (citations only) | `comm_0217_001/002/003/004` + `comm_1002_001` |
| Legacy table | `BUSI_VAS_ACCT_INFM` |

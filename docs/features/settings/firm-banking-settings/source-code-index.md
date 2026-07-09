# Firm Banking Settings — Source Code Index

Legacy ↔ refactor file map for the Firm Banking Settings feature. JEX paths are **read-only reference**.
Frontend mirror: [wecms-web/docs/features/settings/firm-banking-settings/component-index.md](../../../../../wecms-web/docs/features/settings/firm-banking-settings/component-index.md).

---

## Refactor — backend (`wecms-api`)

| Layer | File |
|---|---|
| Controller | [controller/FirmBankingSettingsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java) |
| Service | [service/FirmBankingSettingsService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/service/FirmBankingSettingsService.java) |
| Repository (CRUD) | [repository/FirmBankingSettingRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/repository/FirmBankingSettingRepository.java) |
| Repository (list view) | [repository/FirmBankingSettingViewRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/repository/FirmBankingSettingViewRepository.java) |
| Repository (bank dictionary) | [repository/TbFirmBankCodeRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/repository/TbFirmBankCodeRepository.java) |
| Entity (core) | [entity/TbFirmConf.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmConf.java) · PK [entity/TbFirmConfId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmConfId.java) |
| Entity (dictionary) | [entity/TbFirmBankCode.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmBankCode.java) · PK [entity/TbFirmBankCodeId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmBankCodeId.java) |
| Entity (`@Subselect` list view) | [entity/FirmBankingSettingView.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/FirmBankingSettingView.java) — `@Immutable`, replaces `V_FIRM_BANKING_SETTING` |
| DTOs | [dto/FirmBankingSettingListResponse.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingListResponse.java) · [FirmBankingSettingRow.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingRow.java) · [FirmBankingSettingDetail.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingDetail.java) · [FirmBankingSettingCreateRequest.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingCreateRequest.java) · [FirmBankingSettingUpdateRequest.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingUpdateRequest.java) · [FirmBankingAuditEntry.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingAuditEntry.java) · [FirmBankingAuditListResponse.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingAuditListResponse.java) |
| Shared audit writer | `common.auditlog.AuditLogService` (+ `AuditLogEntryRepository` for the detail-audit read) |
| Tests | `src/test/java/.../firmbankingsettings/service/FirmBankingSettingsServiceTest.java` · `.../controller/FirmBankingSettingsControllerTest.java` |

Validation is Bean Validation on the record DTOs (`@NotBlank` / `@Size` / `@Pattern`) + service-layer
guards (bank scope V8, referential V10, duplicate V12, tenant). Key generation: **none** — the composite
PK is client-supplied (no `wb_get_next_val` branch for `TB_FIRM_CONF`).

---

## Refactor — frontend (`wecms-web`)

| Layer | File |
|---|---|
| Panel | [components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx) |
| Linked-accounts list | [FirmBankingLinkedAccounts.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingLinkedAccounts.tsx) · [FirmBankingAccountItem.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingAccountItem.tsx) · [BankLogo.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/BankLogo.tsx) |
| Automation toggles | [FirmBankingAutomationSettings.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingAutomationSettings.tsx) (Environment Settings — separate feature) |
| Detail dialog (+ audit panel) | [FirmBankingDetailsDialog.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx) |
| Create dialog | [NewFirmBankingDialog.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/NewFirmBankingDialog.tsx) |
| Edit dialog | [EditFirmBankingDetailsDialog.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/EditFirmBankingDetailsDialog.tsx) |
| Row action menu | [FirmBankingRowMenu.tsx](../../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingRowMenu.tsx) |
| API client | [lib/api/firmBankingSettings.ts](../../../../../wecms-web/lib/api/firmBankingSettings.ts) |
| Wire types | [lib/types/api.ts](../../../../../wecms-web/lib/types/api.ts) (`ApiFirmBankingSettingRow` / `Detail` / `AuditEntry` / `*ListResponse`) |
| Mappers | [lib/api/mappers.ts](../../../../../wecms-web/lib/api/mappers.ts) (`mapFirmBankingSettingRow`, `mapFirmBankingAuditEntry`, `mapAccountLookupOption`) |
| In-memory types + mock | [components/settings/firm-banking-settings/types.ts](../../../../../wecms-web/components/settings/firm-banking-settings/types.ts) · `mockData.ts` |

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Legacy WSVC id | Notes |
|---|---|---|
| List action | `comm_0220_001_r001` | LEFT JOIN `ACCT_MSTR` for `CCY_CD`/`BAL`/`EXPI_DT`; full tenant list |
| Delete action | `comm_0220_001_d001` | hard delete by PK; no audit write |
| Create action | `comm_0220_002_c001` | validate → set `FILE_TYPE` from `OUT_BANK_CD` → insert → write audit (3 locales) |
| Detail + audit action | `comm_0220_003_r001` | entity row + audit `REC` filtered by `(BIZ_KEY, REF_NO='TB_FIRM_CONF', REF_NO1, REF_NO2, WSVC_ID, LANG_CD)` |
| Update action | `comm_0220_003_u001` | validate → load by PK → copy editable fields → set `LAST_EDTR` → update → write audit |
| View JSP | `comm_0220_001_view.jsp` (+ `002` / `003`) | under `WECMS DEV/WECMS/web/view/jex/wabooks/comm/` |
| Entity / DAO | `TbFirmConf.java` / `TbFirmConfDAO.java` | under `WECMS DEV/WECMS/src/wabooks/{entity,dao}/` |
| WSVC / IDO XML | `WSVC.comm_0220_*.xml` · `IDO.TB_FIRM_CONF_*.xml`, `TB_FIRM_BANK_CODE_R001`, `AUDIT_LOGS_INFM_C001` | under `WECMS DEV/WECMS_ETC/xml/service/` |

> Legacy WSVC ids appear here only as JEX traceability citations. Every refactor identifier surface uses
> the slug `firm-banking-settings` (route, package, class, DTO, audit `WSVC_ID`) — never `comm_0220_001`.

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/settings/firm-banking-settings/`
- Preserved flat spec: [../firm-banking-settings.md](../firm-banking-settings.md)
- Sibling feature: [../firm-banking-history/](../firm-banking-history/README.md)
- Frontend feature folder: [wecms-web/docs/features/settings/firm-banking-settings/](../../../../../wecms-web/docs/features/settings/firm-banking-settings/README.md)

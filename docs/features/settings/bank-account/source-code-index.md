# Bank Account — Source Code Index

Legacy ↔ refactor file map for the Bank Account feature. JEX paths are **read-only reference**. Frontend
mirror: [wecms-web/docs/features/settings/bank-account/component-index.md](../../../../../wecms-web/docs/features/settings/bank-account/component-index.md).

---

## Refactor — backend (`wecms-api`)

| Layer | File |
|---|---|
| Controller | [controller/SettingsBankAccountController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/controller/SettingsBankAccountController.java) |
| Service (list / update / register / audit-logs) | [service/SettingsBankAccountService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/SettingsBankAccountService.java) |
| Service (permissions) | [service/BankAccountPermissionService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionService.java) |
| Sort whitelist | [service/BankAccountSortField.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountSortField.java) |
| Filter record (wire-reserved) | [service/BankAccountFilter.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountFilter.java) |
| Withdrawal-bank policy | [service/WithdrawalBankPolicy.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/WithdrawalBankPolicy.java) |
| Repository (permissions) | [repository/AcctAuthInfoRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/repository/AcctAuthInfoRepository.java) |
| Entity (permission) | [entity/AcctAuthInfo.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfo.java) |
| Composite PK | [entity/AcctAuthInfoId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfoId.java) |
| Enum (V/W/F) | [entity/BankAccountAuthType.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/BankAccountAuthType.java) |
| DTOs | [dto/BankAccountRow.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/dto/BankAccountRow.java) · `BankAccountListResponse` · `BankAccountUpdateRequest` · `BankAccountRegisterRequest` · `BankAccountRegisterResponse` · `BankAccountPermissionMember` · `BankAccountPermissionResponse` · `BankAccountPermissionUpdateRequest` · `BankAccountAuditRow` · `BankAccountAuditResponse` — all under [dto/](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/dto/) |
| Master entity (reused) | [domain/banking/entity/AcctMstr.java](../../../../src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java) · [repository/AcctMstrRepository.java](../../../../src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java) |
| Bank-name resolver (reused) | [domain/banking/service/BankCdResolver.java](../../../../src/main/java/com/kosign/wecms/domain/banking/service/BankCdResolver.java) |
| VAS repo (reused) | [domain/setting/vasaccount/repository/BusiVasAcctInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/vasaccount/repository/BusiVasAcctInfmRepository.java) |
| Audit (shared) | `common/auditlog/service/AuditLogService` · `common/auditlog/repository/AuditLogEntryRepository` |
| Flyway | [V1__acct_auth_info.sql](../../../../src/main/resources/db/migration/V1__acct_auth_info.sql) (ACCT_AUTH_INFO) · `V13`/`V23` (ACCT_MSTR, banking-owned) |
| i18n | `messages.properties` — `audit.bank_account.*`, `WBE_BANK_ACCOUNT_*`, `WBE0452`, `WBE_USER_NOT_IN_TENANT`, `WBE_CANNOT_REVOKE_SELF`, `WBE_ADMIN_REQUIRED` (dormant) |

Validation is Bean Validation on the record DTOs (`@Size` / `@NotBlank` / `@NotEmpty` / `@NotNull` /
`@Pattern`) plus service-layer guards; there is no standalone validator class.

---

## Refactor — frontend (`wecms-web`)

| Layer | File |
|---|---|
| Panel | [components/settings/bank-account/BankAccountPanel.tsx](../../../../../wecms-web/components/settings/bank-account/BankAccountPanel.tsx) |
| Table + columns | [components/settings/bank-account/BankAccountTable.tsx](../../../../../wecms-web/components/settings/bank-account/BankAccountTable.tsx) · [columns.ts](../../../../../wecms-web/components/settings/bank-account/columns.ts) |
| Bank logo cell | [components/settings/bank-account/BankBrandLogo.tsx](../../../../../wecms-web/components/settings/bank-account/BankBrandLogo.tsx) |
| Edit dialog | [components/settings/bank-account/EditBankAccountDialog.tsx](../../../../../wecms-web/components/settings/bank-account/EditBankAccountDialog.tsx) |
| Permission dialog | [components/settings/bank-account/PermissionDialog.tsx](../../../../../wecms-web/components/settings/bank-account/PermissionDialog.tsx) |
| VAS picker | [components/settings/bank-account/VasAccountPicker.tsx](../../../../../wecms-web/components/settings/bank-account/VasAccountPicker.tsx) |
| Withdrawal policy (FE mirror) | [components/settings/bank-account/withdrawalPolicy.ts](../../../../../wecms-web/components/settings/bank-account/withdrawalPolicy.ts) |
| Types | [components/settings/bank-account/types.ts](../../../../../wecms-web/components/settings/bank-account/types.ts) |
| API client | [lib/api/bankAccount.ts](../../../../../wecms-web/lib/api/bankAccount.ts) |
| Mappers | [lib/api/mappers.ts](../../../../../wecms-web/lib/api/mappers.ts) (`mapBankAccountRow` / `mapBankAccountList` / `mapBankAccountAuditList`) |
| Wire types | [lib/types/api.ts](../../../../../wecms-web/lib/types/api.ts) (`ApiBankAccountRow`, `ApiBankAccountPermission*`, `ApiBankAccountAudit*`) |
| View Setting screen id | [lib/viewSettingScreens.ts](../../../../../wecms-web/lib/viewSettingScreens.ts) (`SCREEN_IDS.SETTINGS_BANK_ACCOUNT = "bank-account"`) |

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Path |
|---|---|
| View JSP (list) | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0183_001_view.jsp` |
| View JSP (permissions) | `…/comm_0183_002_view.jsp` |
| View JSP (detail/edit) | `…/comm_0183_003_view.jsp` |
| View JSP (audit logs) | `…/comm_0183_004_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0183_001.ts` (`AUTH_TYPE` codes at `comm_0183_001.ts:633`; withdrawal-bank gate `:591-606`; VAS columns `:772/782`) |
| List action | `…/web/WEB-INF/action/jex/wabooks/comm/comm_0183_001_r001_act.jsp` |
| Permission read / save | `…/comm_0183_002_r001_act.jsp` · `…/comm_0183_002_u001_act.jsp` (withdrawal gate `:160-193` → `WBE0452`) |
| Update | `…/api/comm/comm_0183_003_u001_act.jsp` (`:103-145` patch; `:154-191` audit loop) |
| Audit logs | `…/api/comm/comm_0183_004_r001_act.jsp` |
| Register (Add-chain) | `…/comm_0218_003_c001_act.jsp` + `select_bank_accounts_popup` |
| IDO | `IDO.ACCT_MSTR_R012.xml` (list, `REC_1`/`REC_2` permission counts, `:41` `WB_GET_USER_NM`, `:123` REC_2 withdrawal filter) · `IDO.ACCT_AUTH_INFO_R004.xml` (`:80` `ADMIN_YN != 'Y'`) |
| Entity / PK | `WECMS DEV/WECMS/src/wabooks/entity/AcctMstr.java` · `AcctAuthInfo.java` · `AcctAuthInfoPk.java` |
| DAO | `WECMS DEV/WECMS/src/wabooks/dao/AcctAuthInfoDAO.java` |
| Enum | `WECMS DEV/WECMS/src/wabooks/enums/AuthType.java` |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/settings/bank-account/`
- Preserved flat spec (backend): [../bank-account.md](../bank-account.md)
- Frontend feature folder: [wecms-web/docs/features/settings/bank-account/](../../../../../wecms-web/docs/features/settings/bank-account/README.md)
- Preserved flat spec (frontend): [wecms-web/docs/features/settings/bank-account.md](../../../../../wecms-web/docs/features/settings/bank-account.md)

# Login Credentials — Source Code Index

Legacy ↔ refactor file map for the Login Credentials feature. JEX paths are **read-only reference**.
Frontend mirror:
[wecms-web/docs/features/settings/login-credentials/component-index.md](../../../../../wecms-web/docs/features/settings/login-credentials/component-index.md).

---

## Refactor — backend (`wecms-api`)

### Owned by `domain/setting/logincredentials`

| Layer | File |
|---|---|
| Controller | [controller/LoginCredentialsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/controller/LoginCredentialsController.java) — 13 endpoints |
| Service | [service/LoginCredentialsService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/service/LoginCredentialsService.java) — list/CRUD/verify-update/reveal/audit; bank ∪ GDT |
| Repository (GDT JPA) | [repository/GdtCertInfoRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/repository/GdtCertInfoRepository.java) |
| Repository (bank picker, JDBC) | [repository/BankPickerRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/repository/BankPickerRepository.java) |
| Repository (form-info, JDBC) | [repository/BankFormInfoRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/repository/BankFormInfoRepository.java) |
| Entity (GDT) | [entity/GdtCertInfo.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/entity/GdtCertInfo.java) |
| Composite PK (GDT) | [entity/GdtCertInfoId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/entity/GdtCertInfoId.java) |
| GDT engine config constants | [config/GdtNxisasConfig.java](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/config/GdtNxisasConfig.java) |

**DTOs** (all under [dto/](../../../../src/main/java/com/kosign/wecms/domain/setting/logincredentials/dto/) — 20 records):

| DTO | Role |
|---|---|
| `LoginCredentialListResponse` · `LoginCredentialGroup` · `LoginCredentialAccount` · `LoginCredentialBankAccount` | List response shapes |
| `BankCredentialCreateRequest` · `BankCredentialRegisterRequest` · `BankCredentialUpdateRequest` | Bank create / atomic-save / update |
| `VerifyBankPasswordRequest` | verify-update body (new password only) |
| `GdtCredentialRequest` · `GdtCredentialUpdateRequest` | GDT create / update |
| `GdtEngineConfigResponse` | GDT engine config |
| `CredentialCreateResponse` | `{ seq_no }` returned by every create |
| `RevealRequest` · `RevealResponse` | reveal re-auth + decrypted credential |
| `LoginCredentialAuditResponse` · `LoginCredentialAuditRow` | audit-history popup |
| `BankPickerResponse` · `BankPickerRow` | bank picker |
| `BankFormInfoResponse` · `BankFormInfoSubType` | per-bank form info |

### Reused cross-package collaborators (not re-implemented)

| Concern | File | Package |
|---|---|---|
| Bank credentials entity | [BankCertInfo.java](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/entity/BankCertInfo.java) · [BankCertInfoId.java](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/entity/BankCertInfoId.java) | `banking/bankcredentials` |
| Bank credentials repository | [BankCertInfoRepository.java](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/repository/BankCertInfoRepository.java) | `banking/bankcredentials` |
| **Cert crypto (legacy pgcrypto AES-256-CBC)** | [BankCertCryptoService.java](../../../../src/main/java/com/kosign/wecms/domain/banking/bankcredentials/service/BankCertCryptoService.java) | `banking/bankcredentials` |
| Coocon scrape verify (bank + GDT verify-before-save) | [BankScrapingService.java](../../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/BankScrapingService.java) | `banking/nxisas` |
| Account registration + `ACCT_AUTH_INFO` fan-out | [AccountRegistrationService.java](../../../../src/main/java/com/kosign/wecms/domain/banking/service/AccountRegistrationService.java) | `banking` |
| `ACCT_AUTH_INFO` entity | [AcctAuthInfo.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfo.java) | `setting/bankaccount` |
| Withdrawal-bank whitelist | [WithdrawalBankPolicy.java](../../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/WithdrawalBankPolicy.java) | `setting/bankaccount` |
| Bank name registry | [BankInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/banking/repository/BankInfmRepository.java) | `banking` |
| Linked accounts | [AcctMstrRepository.java](../../../../src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java) | `banking` |
| Audit write | [AuditLogService.java](../../../../src/main/java/com/kosign/wecms/common/auditlog/service/AuditLogService.java) | `common/auditlog` |
| Audit read | [AuditLogEntryRepository.java](../../../../src/main/java/com/kosign/wecms/common/auditlog/repository/AuditLogEntryRepository.java) | `common/auditlog` |
| SEQ_NO / PRN_SEQ funnel | [LegacyFunctionRepository.java](../../../../src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java) (`nextBankCertSeqNo` / `nextGdtCertSeqNo` / `nextAcctMstrPrnSeq`) | `common/legacy` |

### Schema / config

- `BANK_CERT_INFO`, `GDT_CERT_INFO`, `ACCT_AUTH_INFO`, `ACCT_MSTR`, `AUDIT_LOGS_INFM`, `BANK_INFM_DETL`
  — legacy tables, in [legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md).
- `wb_get_next_val` branches `bank_cert_info.seq_no` (V140 #23) + `gdt_cert_info.seq_no` (V140 #26) —
  in the funnel.
- Config: `wecms.crypto.legacy-cert-seed` (default `wabooksvn`). (`wecms.login-credentials.gdt-verify-enabled`
  was **removed 2026-07-13** — GDT is verified client-side by the local NXiSAS engine; no server-side verify.)

### Tests

No dedicated `LoginCredentialsServiceTest` / `LoginCredentialsControllerTest` on disk in this pass —
see [test-scenarios.md §2](test-scenarios.md). `BankCertCryptoService`, `BankScrapingService`, and
`AccountRegistrationService` carry their own coverage in the banking tree.

---

## Refactor — frontend (`wecms-web`)

| Layer | File |
|---|---|
| Panel | [components/settings/login-credentials/LoginCredentialsPanel.tsx](../../../../../wecms-web/components/settings/login-credentials/LoginCredentialsPanel.tsx) |
| Table | [components/settings/login-credentials/LoginCredentialsTable.tsx](../../../../../wecms-web/components/settings/login-credentials/LoginCredentialsTable.tsx) |
| Row kebab menu | [components/settings/login-credentials/LoginCredentialsRowMenu.tsx](../../../../../wecms-web/components/settings/login-credentials/LoginCredentialsRowMenu.tsx) |
| Login id/pw popover | [components/settings/login-credentials/LoginIdPwPopover.tsx](../../../../../wecms-web/components/settings/login-credentials/LoginIdPwPopover.tsx) |
| Types | [components/settings/login-credentials/types.ts](../../../../../wecms-web/components/settings/login-credentials/types.ts) |
| Registered-credential builder | [components/settings/login-credentials/registeredCredentials.ts](../../../../../wecms-web/components/settings/login-credentials/registeredCredentials.ts) |
| Audit-log row types | [components/settings/login-credentials/auditLogs.ts](../../../../../wecms-web/components/settings/login-credentials/auditLogs.ts) |
| GDT validation hook | [components/settings/login-credentials/useGdtCredentialValidation.ts](../../../../../wecms-web/components/settings/login-credentials/useGdtCredentialValidation.ts) |
| Shared popups | [components/shared/popups/](../../../../../wecms-web/components/shared/popups/) — `SelectLoginInstitutionDialog`, `SelectBankDialog`, `LoginIdPasswordDialog`, `SelectBankAccountsDialog`, `RegisteredLoginCredentialsDialog`, `EditLoginIdPasswordDialog`, `RevealPasswordConfirmDialog`, `GdtCredentialsDialog`, `AuditLogsDialog` |
| API client | [lib/api/loginCredentials.ts](../../../../../wecms-web/lib/api/loginCredentials.ts) |
| Wire types | [lib/types/api.ts](../../../../../wecms-web/lib/types/api.ts) (`ApiLoginCredential*` family) |

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Path |
|---|---|
| NXiSAS orchestration (LOGIN + ACCT_LIST + form-info) | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0218_nxisas.ts` |
| Add credential — create action | `…/web/WEB-INF/action/jex/wabooks/comm/comm_0218_003_c001_act.jsp` |
| Change password — update action (decrypt guard `:55-57`) | `…/comm_0218_004_u001_act.jsp` |
| GDT credential — typescript (login test) | `…/comm/comm_0218_007.js` |
| Reveal re-auth | `comm_0218_008_r001_act.jsp` |
| Delete action (audit fan-out `:49-87`) | `comm_0218_001_d001_act.jsp` |
| Select-bank-accounts save → `ACCT_MSTR` + `ACCT_AUTH_INFO` | `fnsh_0101_001_c001` |
| Entity / DAO | `WECMS DEV/WECMS/src/wabooks/entity/BankCertInfo.java` · `dao/BankCertInfoDAO.java` |
| IDO list | `WECMS_ETC/xml/service/IDO/IDO.BANK_CERT_INFO_R007.xml` · `_R005` (linked accounts) |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/settings/login-credentials/`
- Preserved flat spec: [../login-credentials.md](../login-credentials.md)
- Legacy-vs-refactor audit: [docs/audits/login-credentials-legacy-vs-refactor-audit.md](../../../audits/login-credentials-legacy-vs-refactor-audit.md)
- Frontend feature folder: [wecms-web/docs/features/settings/login-credentials/](../../../../../wecms-web/docs/features/settings/login-credentials/README.md)

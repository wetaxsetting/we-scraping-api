# Own Account Transfer — Source Code Index (Legacy ↔ Refactor)

Complete file map for the feature across legacy JEX, refactor backend, and refactor frontend. Legacy paths
are **read-only reference**. The legacy-only deep index (every JSP/TS/IDO/WSVC) is in
[docs/jex/features/own-account-transfer/source-code-index.md](../../../../../../docs/jex/features/own-account-transfer/source-code-index.md).

---

## 1. Legacy JEX (read-only, `WECMS DEV/`)

| Concern | Legacy file |
|---|---|
| Main page (list) | `WECMS/web/view/jex/wabooks/trns/trns_0001_001_view.jsp` |
| List logic (live) | `WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0001_001_v001.ts` |
| Transfer wizard (canonical scrape spec) | `WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0001_001.ts` |
| Transfer form popup | `WECMS/web/view/jex/wabooks/trns/trns_0001_007_view.jsp` + `…_STATIC/…/trns_0001_007.ts` |
| Source account list action | `…/action/jex/wabooks/trns/trns_0001_001_r001_act.jsp` |
| NAPAS validation action | `…/trns/trns_0001_001_r003_act.jsp` |
| Balance update action | `…/trns/trns_0001_001_u001_act.jsp` |
| My-account (own recipient) | `…/trns/trns_0001_005_r001_act.jsp` (+ `trns_0001_005_view.jsp`) |
| Transfer-history INSERT | `…/action/jex/wabooks/trns/trns_0002_001_c001_act.jsp` |
| Engine transport | `WECMS_STATIC/web/nxisas/js/isasscaping.js`, `nxisas_v2.js` |
| SEED-CBC decrypt / encrypt | `…/action/jex/wabooks/nxisas/nxisas_decrypt_act.jsp` / `nxisas_encrypt_act.jsp` |
| Engine logging | `…/nxisas/nxisas_logging_act.jsp` |
| Cert lookup | `…/nxisas/nxisas_acctList_r003_act.jsp` |
| Local executable | `WECMS_STATIC/web/nxisas/file/NXiSAS.exe` |
| IDO/WSVC | `WECMS_ETC/xml/service/WSVC/WSVC.trns_0001_001_r001\|r003\|u001.xml`, `WSVC.trns_0001_005_r001.xml`, `WSVC.trns_0002_001_c001.xml`; `IDO.ACCT_MSTR_R015\|R002_CNT\|R008\|U000.xml`, `IDO.NAPAS_BANK_INFO_R001.xml`, `IDO.NAPAS_REMIT_TRNS_HSTR_C001.xml` |

## 2. Refactor backend (`wecms-api/src/main/java/com/kosign/wecms/domain/banking/ownaccounttransfer/`)

| Layer | File | Legacy mapped |
|---|---|---|
| Controller | `controller/OwnAccountTransferController.java` (10 endpoints) | `trns_0001_001_*` + `nxisas_*` actions |
| Service | `service/OwnAccountTransferService.java` (~970 lines) | `r001` / `u001` / `r003` / `r005` / `c001` |
| Service | `service/OatNxisasService.java` | `nxisas_acctList_r003` + `nxisas_logging` + decrypt orchestration |
| Service | `service/SeedCbcDecryptService.java` | `nxisas_decrypt_act.jsp:56-101` (ISASSeedCBC via legacy `isas1.0.jar`) |
| Sort whitelist | `service/OwnAccountTransferSortField.java` | `SortHelper` |
| Config | `config/OwnAccountTransferConfig.java`, `config/OatSeedDecryptProperties.java` | — (`wecms.oat.seed-decrypt.enabled`) |
| Request DTOs | `dto/request/{BalanceRefreshRequest, ScrapingResultIntakeRequest, EngineConnectionCheckRequest, NxisasCertAcctListRequest, NxisasDecryptRequest, NxisasLoggingRequest}.java` | WSVC inputs |
| Response DTOs | `dto/response/{SourceAccountListResponse, SourceAccountRow, TargetAccountListResponse, TargetAccountRow, NapasBankInfoRow, BalanceRefreshResponse, ScrapingResultResponse, NxisasCertAcctListResponse, NxisasDecryptResponse}.java` | WSVC outputs |
| Entities (shared) | `banking/entity/{AcctMstr, NapasRemitTrnsHstr, NapasBankInfo}.java` | `ACCT_MSTR`, `NAPAS_REMIT_TRNS_HSTR`, `NAPAS_BANK_INFO` |
| Repositories (shared) | `banking/repository/{AcctMstrRepository, NapasRemitTrnsHstrRepository, NapasBankInfoRepository}.java`, `setting/bankaccount/repository/AcctAuthInfoRepository.java` | IDO reads/writes |
| Migrations | `resources/db/migration/V195__napas_remit_trns_hstr_oat_idempotency.sql`, `V156__sys_scr_option_wecms.sql` | — | *(historical — DROPPED; the OAT idempotency guard was removed entirely. Base table: `V69__napas_remit_trns_hstr.sql`)*
| Tests | `src/test/java/.../ownaccounttransfer/{controller/OwnAccountTransferControllerTest, service/OwnAccountTransferServiceTest, service/OatNxisasServiceExtTranLogTest, service/OatNxisasServiceSeedFlagTest, service/SeedCbcDecryptServiceTest}.java` | — |

## 3. Refactor frontend (`wecms-web/`)

| Layer | File |
|---|---|
| Route | `app/(dashboard)/banking/own-account-transfer/page.tsx` |
| Page / client | `components/banking/own-account-transfer/{OwnAccountTransferClient, OwnAccountTransferPage}.tsx` |
| Grid | `components/banking/own-account-transfer/{SourceAccountsTable, SourceAccountsTableFooter}.tsx` + `source-accounts-columns.tsx` |
| Composer & dialogs | `OwnAccountTransferComposerDialog.tsx`, `TransferConfirmationDialog.tsx`, `OtpAuthDialog.tsx`, `EngineStatusBanner.tsx`, `EngineNotInstalledDialog.tsx`, `EngineUnsupportedOsDialog.tsx`, `preview/NxisasSecondaryAuthPreview.tsx` |
| Hooks | `components/banking/own-account-transfer/hooks/{use-own-account-transfer, use-nxisas-engine}.ts` |
| Types / mappers / profiles | `types.ts`, `mappers.ts`, `bank-profiles.ts` |
| API client | `lib/api/banking.ts` (OAT functions) |
| NXiSAS adapters | `lib/nxisas/{jexjs-adapter, confirm-bridge, otp-bridge, qna-bridge, system-message-bridge}.ts` |
| Wire types | `lib/types/api.ts` (`ApiOwnAccount*`, `ApiBalanceRefresh*`, `ApiNapasBankInfoRow`, `ApiEngineConnectionCheckRequest`) |
| Menu / nav | `components/layout/Sidebar.tsx`, `lib/menuAuth.ts`, `lib/viewSettingScreens.ts` |
| Vendor bundle | `public/nxisas/` (verbatim Coocon JS — never hand-edited) |

Frontend doc home: [wecms-web/docs/features/banking/internal-transfer-vnd/own-account-transfer/component-index.md](../../../../../../wecms-web/docs/features/banking/internal-transfer-vnd/own-account-transfer/component-index.md).

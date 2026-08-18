# Smart Organizer — Source Code Index


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

Legacy JEX (`fnsh_0200_*`) ↔ refactor backend ↔ refactor frontend file index.

---

## 1. Legacy JEX (`fnsh_0200_001` page + `fnsh_0200_002` Organizer Settings popup)

All under `WECMS DEV/`. Read-only reference.

| Layer | File |
|---|---|
| Page view JSP (eligibility list) | `WECMS/web/view/jex/wabooks/fnsh/fnsh_0200_001_view.jsp` |
| Organizer Settings popup JSP | `WECMS/web/view/jex/wabooks/fnsh/fnsh_0200_002_view.jsp` |
| Read eligible transactions | `WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0200_001_r001_act.jsp` |
| Confirm transactions | `WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0200_001_c001_act.jsp` |
| List rules | `WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0200_002_r001_act.jsp` |
| Create/update/delete rules | `WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0200_002_c001_act.jsp` |
| Reorder rules (`DISP_ORD`) | `WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0200_002_u001_act.jsp` |
| TS client (page) | `WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0200_001.ts` |
| TS client (settings popup) | `WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0200_002.ts` |
| Launch points (history screens) | `…/fnsh_0002_001_osmc_VN.ts`, `…/fnsh_0006_001_osmc_VN.ts` |
| Grid IDO (eligibility + matched-rule JSON) | `WECMS_ETC/xml/service/IDO/IDO.ACCT_TRNS_HSTR_R028.xml` |
| Rule list IDO | `WECMS_ETC/xml/service/IDO/IDO.RMRK_ORG_INFM_R002.xml` |
| Rule insert IDO | `WECMS_ETC/xml/service/IDO/IDO.RMRK_ORG_INFM_C001.xml` |
| Rule update IDO | `WECMS_ETC/xml/service/IDO/IDO.RMRK_ORG_INFM_U001.xml` |
| WSVC contracts | `WECMS_ETC/xml/service/WSVC/WSVC.fnsh_0200_001*.xml`, `WSVC.fnsh_0200_002*.xml` |

> **The matching engine lives in `IDO.ACCT_TRNS_HSTR_R028.xml` (a correlated sub-query + `JSON_BUILD_OBJECT`),
> not in Java.** The refactor re-expresses it as a `LATERAL` join (see migration-guide).

## 2. Refactor backend (`wecms-api/`)

All under `src/main/java/com/kosign/wecms/`.

| Layer | Class | Path |
|---|---|---|
| Controller (eligible + confirm) | `SmartOrganizerController` | `domain/banking/smartorganizer/controller/SmartOrganizerController.java` |
| Controller (rule CRUD + reorder) | `OrganizerRuleController` | `domain/banking/smartorganizer/controller/OrganizerRuleController.java` |
| Service (eligible + confirm) | `SmartOrganizerService` | `domain/banking/smartorganizer/service/SmartOrganizerService.java` |
| Service (rule CRUD) | `OrganizerRuleService` | `domain/banking/smartorganizer/service/OrganizerRuleService.java` |
| Per-account authz guard (R11) | `AccountAccessGuard` | `domain/banking/service/AccountAccessGuard.java` |
| Bank/acct-type resolver (R07) | `BankCdResolver` | `domain/banking/service/BankCdResolver.java` |
| Sequence allocator | `LegacyFunctionRepository` | `common/legacy/LegacyFunctionRepository.java` |
| Entity (rule) | `RmrkOrgInfm` (+`RmrkOrgInfmId`) | `domain/banking/smartorganizer/entity/` |
| Entity (transaction source) | `AcctTrnsHstr` (+`AcctTrnsHstrId`) | `domain/banking/entity/` |
| Entity (confirmation) | `AcctTrnsRcvPayInfm` (+id) | `domain/banking/entity/` |
| Repo (rule) | `RmrkOrgInfmRepository` | `domain/banking/smartorganizer/repository/` |
| Repo (confirmation) | `AcctTrnsRcvPayInfmRepository` | `domain/banking/repository/` |
| Repo (transaction) | `AcctTrnsHstrRepository` | `domain/banking/repository/` |
| Repo (currency/nickname) | `AcctMstrRepository` | `domain/banking/repository/` |
| Repo (per-user authority, R11) | `AcctAuthInfoRepository` | `domain/setting/bankaccount/repository/` |
| Lookup repos (R05/R07) | `ChartOfAccountRepository`, `PurposeSettingsRepository`, `BusiVasAcctInfmRepository`, `TrcoInfmRepository` | various settings packages |
| DTO (list) | `EligibleTransactionListResponse`, `EligibleTransactionRow` | `domain/banking/smartorganizer/dto/` |
| DTO (confirm) | `ConfirmRequest` (+`Row`), `ConfirmResponse` | `domain/banking/smartorganizer/dto/` |
| DTO (rules) | `OrganizerRuleListResponse`, `OrganizerRuleRow`, `OrganizerRuleSaveRequest`, `OrganizerRuleReorderRequest` (+`Entry`) | `domain/banking/smartorganizer/dto/` |
| Sort whitelist | `SmartOrganizerSortField` | `domain/banking/smartorganizer/dto/` |
| Flyway | `V85__rmrk_org_infm.sql`, `V140__wb_get_next_val.sql` (carries the `rmrk_seq_no` branch), `V156__sys_scr_option_wecms.sql` (View Setting seed — manual DBA script) — *previously cited as V172/V173/V174, renumbered by the [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md)* | `src/main/resources/db/migration/` |
| Error messages | `messages.properties:633-639`, `messages_en.properties:199-202` | `src/main/resources/` |

## 3. Refactor frontend (`wecms-web/`)

| Layer | File |
|---|---|
| Route | `app/(dashboard)/banking/smart-organizer/page.tsx` |
| Page component | `components/banking/smart-organizer/SmartOrganizerPage.tsx` |
| Organizer Settings dialog | `components/banking/smart-organizer/OrganizerSettingsDialog.tsx` |
| Launch button (from history) | `components/banking/transaction-history/SmartOrganizerButton.tsx` |
| Payment-match dialog (legacy `comm_0196_001`) | `components/banking/transaction-history/SmartOrganizerDialog.tsx` |
| API client | `lib/api/smartOrganizer.ts` |
| Wire types | `lib/types/api.ts` (+ types in `lib/api/smartOrganizer.ts`) |
| i18n | `locales/{en,ko,vi}.json` — `smart_organizer_page.*`, `organizer_settings.*`, `txn_history.smart_organizer` |

Frontend detail:
[wecms-web/docs/features/banking/smart-organizer/component-index.md](../../../../../wecms-web/docs/features/banking/smart-organizer/component-index.md).

## 4. ID correspondence

| Surface | Value |
|---|---|
| Legacy JEX screens | `fnsh_0200_001` (list/confirm) + `fnsh_0200_002` (rule CRUD) |
| Refactor feature slug | `smart-organizer` |
| Next.js route | `/banking/smart-organizer?account={bankCd}_{acctNo}` |
| REST base | `/api/v1/banking/smart-organizer` (+ `/rules`) |
| Menu permission | `banking-vnd-inquire-transactions` OR `banking-fcy-inquire-transactions` |
| View Setting `WEB_SVC_ID` | `smart-organizer` (V174) |

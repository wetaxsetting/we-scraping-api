# Bank Account — Migration Guide (JEX → Spring)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

How each JEX layer became its Spring counterpart. Legacy artifacts are **read-only reference** under
`WECMS DEV/`. Frontend mirror:
[wecms-web/docs/features/settings/bank-account/](../../../../../wecms-web/docs/features/settings/bank-account/README.md).

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen (list) | `comm_0183_001_view.jsp` + `comm_0183_001.ts` | `<BankAccountPanel>` (Settings modal panel — no route) |
| Screen (permission popups) | `comm_0183_002_view.jsp` | `<PermissionDialog>` (View / Withdrawal) |
| Screen (detail/edit) | `comm_0183_003_view.jsp` | `<EditBankAccountDialog>` |
| Screen (audit logs) | `comm_0183_004_view.jsp` | Audit Logs popup (shared `AuditLogDialog`) |
| List read | `comm_0183_001_r001` (`IDO.ACCT_MSTR_R012`) | `GET /bank-account` → `SettingsBankAccountService.list` → `AcctMstrRepository.findByBizKeyAndActvYn` |
| Per-row permission count | `IDO.ACCT_MSTR_R012` `REC_1`/`REC_2` (`NOT EXISTS BUSI_ADMN_INFM`) | `AcctAuthInfoRepository.countActiveByAccount` + `findFirstActiveUserName` |
| Modifier name | `WB_GET_USER_NM(MOD_ID)` (`IDO.ACCT_MSTR_R012.xml:41`) | `SettingsBankAccountService.resolveModifierNames` (USER_INFM by id) |
| Bank name | `wb_get_bank_nm(_,'s')` (short) | `BankCdResolver.resolveBankNm` (shared) |
| Update | `comm_0183_003_u001_act.jsp` | `PATCH /{bankCd}/{acctNo}` → `service.update` |
| Register (Add-chain) | `comm_0218_003_c001` + `select_bank_accounts_popup` | `POST /register` → `service.register` (+ `AccountRegistrationService`) |
| Permission roster | `comm_0183_002_r001` (`IDO.ACCT_AUTH_INFO_R004`) | `GET .../permissions` → `BankAccountPermissionService.list` |
| Permission save | `comm_0183_002_u001_act.jsp` | `PUT .../permissions` → `BankAccountPermissionService.replace` |
| Permission revoke | `comm_0220_*` revoke path (`USE_YN='N'`) | soft-revoke inside `replace` |
| Withdrawal-bank gate | `comm_0183_002_u001_act.jsp:160-193` `grpBankCd` → `WBE0452` | `WithdrawalBankPolicy.isWithdrawalAllowed` → `WBE0452` |
| Audit logs | `comm_0183_004_r001_act.jsp` | `GET .../audit-logs` → `service.auditLogs` (reads `AUDIT_LOGS_INFM`) |
| Entity (master) | `wabooks.entity.AcctMstr` | `domain/banking/entity/AcctMstr` (reused) |
| Entity (permission) | `wabooks.entity.AcctAuthInfo` + `AcctAuthInfoPk` | `AcctAuthInfo` + `AcctAuthInfoId` (`@IdClass`) |
| DAO | `AcctAuthInfoDAO` | Spring Data `AcctAuthInfoRepository` |
| Enum | `wabooks.enums.AuthType` (V/W/F) | `BankAccountAuthType` (V/W/F; `code()` + `wireName()` + `i18nKey()`) |

---

## Identifier translation (slug, not WSVC id)

Every refactor identifier surface carries the kebab slug **`bank-account`** — REST URI, Java package
(`…domain.setting.bankaccount`), class names (`SettingsBankAccountController`,
`SettingsBankAccountService`, `BankAccountPermissionService`), DTO records, i18n keys
(`audit.bank_account.*`), and the audit `WSVC_ID`. The menu permission code is the route-aligned slug
**`settings-core-bank-account`**. Legacy WSVC ids (`comm_0183_001`, …) appear only in JEX file:line
citations, migration prose, **and** as the second `WSVC_ID` the audit-logs popup *reads* (slug +
`comm_0183_001`) for shared-DB coexistence — writes always use the slug.

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| List scope | `ACTV_YN='Y'` | `ACTV_YN='Y'` (G11 = follow legacy) |
| Edit | bulk `comm_0183_003_u001` (REC) | single-row `PATCH /{bankCd}/{acctNo}` |
| Permissions | `comm_0183_002` popup (View / Withdrawal) | replace-semantics `PUT` + per-kind roster `GET`, soft-revoke |
| Admin in roster | excluded (`ADMIN_YN != 'Y'`) | excluded — same |
| Withdrawal banks | 5-bank `grpBankCd` whitelist → `WBE0452` | `WithdrawalBankPolicy` → `WBE0452` (kept, 2026-06-16) |
| Audit `WSVC_ID` (write) | `comm_0183_001` | slug `bank-account` |
| Audit `WSVC_ID` (read) | `comm_0183_001` | reads **both** slug + `comm_0183_001` (coexistence) |
| Account create | n/a (scrape-only) | `register` JSON bulk insert |
| VAS-name composite | full join | `"VAS_ACCT_NM(CCY)"` echo (richer join deferred) |
| Authorization | menu visibility only | explicit `@menuAuthz.canAccess('settings-core-bank-account')` (F-3) |

---

## Dropped / dead legacy flows (not ported)

| Legacy flow | Disposition |
|---|---|
| Single-account de-registration / `ACTV_YN` flip | **DEAD LEGACY** — no `DELETE` endpoint; the refactor never de-registers on this screen. |
| `AUTH_TYPE='F'` Full-Access surface | **PARTIAL** — the code carries `FULL_ACCESS` for legacy DEV-data compatibility but exposes only View / Withdrawal; a Full-Access tab is unbuilt. |
| Per-account detail GET | **APPROVED DEVIATION** — the list row carries every maintenance field; the edit dialog seeds from the row, so no detail fetch is wired. |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the classification + evidence.

---

## Schema migration (Flyway)

| Version | Purpose |
|---|---|
| `V1__acct_auth_info.sql` | `CREATE TABLE ACCT_AUTH_INFO` (10 cols, PK `(BIZ_KEY, BANK_CD, ACCT_NO, USER_ID, AUTH_TYPE)`, FK `acct_auth_info_fk01 → ACCT_MSTR`, `USE_YN`/`AUTH_TYPE` `bpchar(1)`) |
| (banking) `V13` + `V23` | `ACCT_MSTR` schema + column-parity (owned by `domain/banking`) — the master table this feature reads / patches |

> The `AcctAuthInfo` entity Javadoc still references "V245" / "V127" as the creating migration — **stale**
> after the 2026-06-09 renumber; the live file is `V1__acct_auth_info.sql`. No key-generation function
> (`wb_get_next_val`) is involved — both tables use natural composite keys.

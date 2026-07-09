# Settings Source Index — 2026-06-23

> Code ↔ docs ↔ tests index across all Settings submenus.
> Companion files: [settings-docs-audit.md](settings-docs-audit.md) · [settings-gap-analysis.md](settings-gap-analysis.md).
> Backend package root: `com.kosign.wecms.domain.setting.*` (plus `domain.banking.bankcredentials` / `domain.payment` for adjacent items).
> Wire base paths and key tables are summarised from the `wecms-api/CLAUDE.md` Feature-Status table.

## Module index

| Module | Backend package | Wire base path | Primary table(s) | API docs | WEB docs |
|---|---|---|---|---|---|
| Purpose | `setting.purposesettings` | `/api/v1/settings/purpose-settings` | `BUSI_USAG_SET_INFM`, `v_purpose_in_use` (@Subselect) | [purpose/](../features/settings/purpose/README.md) | `wecms-web/.../settings/purpose/` |
| VAS Account | `setting.vasaccount` | `/api/v1/settings/vas-account` | `BUSI_VAS_ACCT_INFM` | [vas-account/](../features/settings/vas-account/README.md) | `wecms-web/.../settings/vas-account/` |
| Contacts | `setting.contacts` | `/api/v1/settings/contacts` | `TRCO_INFM`, `TRCO_ADDR_INFM`, `TRCO_ACCT_INFO`, `TRCO_OVRS_INFM`, `PGRO_BSAC_LNKN_INFM`, `TRCO_INFO_FILE` | [contacts/](../features/settings/contacts/README.md) | `wecms-web/.../settings/contacts/` |
| Approval Line | `setting.approvalline` | `/api/v1/settings/approval-line` | `APPR_LINE_INFM`, `APPR_LINE_INFM_DETL` | [approval-line/](../features/settings/approval-line/README.md) | `wecms-web/.../settings/approval-line/` |
| Chart of Accounts | `setting.chartofaccounts` | `/api/v1/settings/chart-of-accounts` | `CHART_ACCT_INFM` | [chart-of-accounts/](../features/settings/chart-of-accounts/README.md) | `wecms-web/.../settings/chart-of-accounts/` |
| Bank Account | `setting.bankaccount` | `/api/v1/settings/bank-account` | `ACCT_MSTR`, `BANK_INFM` | [bank-account/](../features/settings/bank-account/README.md) | `wecms-web/.../settings/bank-account/` |
| Login Credentials | `setting.logincredentials` | `/api/v1/login-credentials` | `BANK_CERT_INFO`, `GDT_CERT_INFO`, `ACCT_AUTH_INFO`, `AUDIT_LOGS_INFM` | [login-credentials/](../features/settings/login-credentials/README.md) | `wecms-web/.../settings/login-credentials/` |
| Preferences | `setting.preferences` | `/api/v1/settings/preferences` | `BUSI_INFM`, `BUSI_ENV_SET_INFM` | [preferences/](../features/settings/preferences/README.md) | `wecms-web/.../settings/preferences/` |
| Transaction History (ERP) | `setting.erptransactionhistory` | `/api/v1/settings/erp-transaction-history` | `ERP_TRNS_HSTR` | [transaction-history/](../features/settings/transaction-history/README.md) | `wecms-web/.../settings/transaction-history/` |
| Firm Banking Settings | `setting.firmbankingsettings` | `/api/v1/settings/firm-banking-settings` | `TB_FIRM_CONF` (+ V_FIRM_BANKING_SETTING @Subselect) | [firm-banking-settings/](../features/settings/firm-banking-settings/README.md) | `wecms-web/.../settings/firm-banking-settings/` |
| Firm Banking History | `setting.firmbankinghistory` | `/api/v1/settings/firm-banking-history` (+ `/export`) | `TB_FIRM_TRAN`, `TB_FIRM_*` (V_FIRM_BANKING_HISTORY @Subselect) | [firm-banking-history/](../features/settings/firm-banking-history/README.md) | `wecms-web/.../settings/firm-banking-history/` |
| User Management | `setting.users` | `/api/v1/settings/users` | `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_ADMN_INFM`, `USER_MENU_AUTH_INFM` | [user-management/](../features/settings/user-management/README.md) | `wecms-web/.../settings/user-management/` |

## Shared / cross-cutting infrastructure consumed by Settings

| Concern | Code | Notes |
|---|---|---|
| Key generation | `common.legacy.LegacyFunctionRepository` → `wb_get_next_val` | Funnel for `trco_key`, `appr_line_no`, `chart_acct_seq_no`, etc. (single-executor rule) |
| Audit logging | `common.auditlog` writer → `AUDIT_LOGS_INFM` | `WSVC_ID` uses route slug (`firm-banking-settings`, `contacts`, `chart-of-accounts`). Approval Line writes none (parity) |
| View Setting | `domain.setting` ScreenOptionController | Adopted by Contacts; NOT adopted by Approval Line / Chart of Accounts / ERP Transaction History (legacy panels lack the toolbar list-icon) |
| Excel export | `common.export` (Apache POI) | Firm Banking History + ERP Transaction History providers |
| Passwords | `common.security.WecmsPasswordEncoder` (legacy SHA-256) | User Management + Change Password write paths |

## Tests (presence per CLAUDE.md Feature-Status)

| Module | Unit / integration tests |
|---|---|
| Purpose | ✅ (+ tests) |
| VAS Account | ✅ unit + integration |
| Approval Line | ✅ (+ tests) |
| Chart of Accounts | ✅ (validator + service) |
| Firm Banking Settings | ✅ (+ unit tests) |
| Firm Banking History | ✅ (+ unit tests; export provider) |
| User Management | ✅ `UserManagementAuthorizationTest` + `UserManagementAccessPolicyTest` |
| Environment Settings | ✅ (+ tests) |
| Contacts / Bank Account / Login Credentials / Preferences / ERP Transaction History | present per module; coverage detail in each module's `test-scenarios.md` |

## Open code-tree follow-ups (not docs)

- ~~**S-1:** create or repoint `docs/audits/login-credentials-legacy-vs-refactor-audit.md`~~ → ✅ **RESOLVED 2026-06-23** — created [login-credentials-legacy-vs-refactor-audit.md](login-credentials-legacy-vs-refactor-audit.md) (Option B; the 2026-06-10 doc is a different, auth-password audit). See [settings-gap-analysis.md](settings-gap-analysis.md) S-1.
- Chart of Accounts OQ-1 / OQ-3 PO decisions (gating + parent-cleanup branch).
- User Management OQ-DOC-UM-1..5 (audit-log on mutation, action-level entitlement, status edit, pw-history, phone CC).

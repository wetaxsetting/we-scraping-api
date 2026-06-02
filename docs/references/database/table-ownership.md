# Table Ownership

**Generated:** 2026-06-01 (ERD Phase 2).

Authoritative map of **which module owns each table**, its Java domain package, and its primary feature doc. "Owner" = the module whose service write-path is responsible for the table. Tables read across modules (e.g. `BANK_INFM`, `TRCO_INFM`, `CHART_ACCT_INFM`) are owned by one module and consumed by others — the **Consumed by** column lists cross-module readers.

Ownership rule: a table has exactly **one** owning module. Shared reference/infrastructure tables are owned by **Shared / Common**.

## Banking — `domain/banking` · ERD [erd/banking-erd.md](erd/banking-erd.md)

| Table | Sub-module / package | Primary feature doc | Consumed by |
|---|---|---|---|
| `ACCT_MSTR` | `banking` | [features/banking](../../features/banking/current-savings-vnd/account-inquiry.md) | Settings (auth, firm-conf), Payment, reports |
| `ACCT_TRNS_HSTR` | `banking` | [features/banking transaction-history](../../features/banking/current-savings-vnd/transaction-history.md) | Add-on (doc link), matching |
| `ACCT_TRNS_RCV_PAY_INFM` | `banking` | [transaction-history](../../features/banking/current-savings-vnd/transaction-history.md) | Contacts in-use guard |
| `BUSA_DOC_RCV_PAY_HIS` | `banking` | [transaction-history](../../features/banking/current-savings-vnd/transaction-history.md) | Add-on e-invoice, card link |
| `NAPAS_REMIT_TRNS_HSTR` | `banking` | [own-account-transfer](../../features/banking/own-account-transfer/transfer-history.md) | — |
| `BANK_EXCH_RATE_INFM` | `banking` | [exchange-rate-inquiry](../../features/payment/exchange-rate-inquiry.md) | Payment (FX) |
| `RMRK_ORG_INFM` | `banking/smartorganizer` | [smart-organizer](../../features/banking/current-savings-vnd/smart-organizer.md) | — |
| `MEMO_INFM` | `banking` (polymorphic) | — | every page (polymorphic memo) |
| `BUSI_MATCH_LOG` | `banking` (matching) | [smart-organizer](../../features/banking/current-savings-vnd/smart-organizer.md) | — |
| `DAILY_CASH_REPT_DAILY_SUMR_DETL` | `banking` (reports) | [reports/daily-cash](../../features/banking/reports/daily-cash.md) | — |

## Payment — `domain/payment` · ERD [erd/payment-erd.md](erd/payment-erd.md)

| Table | Sub-module / package | Primary feature doc | Consumed by |
|---|---|---|---|
| `SCDUL_PYMN_SUMR` | `payment/internaltransfer` | [internal-transfer](../../features/payment/internal-transfer/README.md) | Vendor/Expense/Payroll (shared) |
| `SCDUL_PYMN_SUMR_DETL` | `payment/internaltransfer` | [internal-transfer](../../features/payment/internal-transfer/README.md) | — |
| `INTERNAL_TRANSFER_KEY_SEQ` | `payment/internaltransfer` | [internal-transfer](../../features/payment/internal-transfer/README.md) | — (refactor helper) |
| `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` | `payment/dispatch` | [internal-transfer](../../features/payment/internal-transfer/README.md) | — |
| `OVRS_PYMN_SUMR` | `payment/overseas` | [overseas-payment](../../features/payment/overseas-payment/README.md) | — |
| `OVRS_PYMN_MSTR` | `payment/overseas` | [overseas-payment](../../features/payment/overseas-payment/README.md) | — |
| `OVRS_PYMN_DTLS` | `payment/overseas` | [overseas-payment](../../features/payment/overseas-payment/README.md) | — |
| `OVERSEAS_PAYMENT_KEY_SEQ` | `payment/overseas` | [overseas-payment](../../features/payment/overseas-payment/README.md) | — (refactor helper) |
| `EXPT_RESOL_MSTR` | `payment/approvalbox` | [approval-box](../../features/payment/approval-box/) | All payment + card (APPR_SEQ_NO) |
| `EXPT_RESOL_APPRVR_INFM` | `payment/approvalbox` | [approval-box](../../features/payment/approval-box/) | — |
| `EXPT_RESOL_FILE` | `payment/approvalbox` | [approval-box](../../features/payment/approval-box/) | — |
| `TB_PAY_TARGET` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | All scheduled-payment flows |
| `TB_PAY_FILE_MST` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | — |
| `TB_PAY_FILE_DATA` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | — |
| `TB_BATCH_SCHEDULE_INFO` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | — |
| `TB_BATCH_SCHEDULE_DTL` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | — |
| `TB_FIRM_BAT_API_TRAN` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | — |
| `TB_FIRM_FILE_MST` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | — |
| `TB_FIRM_FILE_DATA` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | — |
| `TB_FIRM_FILE_RSLT` | `payment/dispatch` | [firm-banking-dispatch-schema](payment/firm-banking-dispatch-schema.md) | — |
| `BLOCKED_CHARS_RULE` | `payment/validation` | [internal-transfer](../../features/payment/internal-transfer/README.md) | All payment Excel-upload validators |

## Add-on Service — `domain/einvoice` · `domain/operation` · `domain/payment/cardtransactions` · ERD [erd/addon-service-erd.md](erd/addon-service-erd.md)

| Table | Sub-module / package | Primary feature doc | Consumed by |
|---|---|---|---|
| `BUSA_DOC_MSTR` | `einvoice` | [sales-e-invoices](../../features/addon-service/sales-e-invoices.md) / [expenses-e-invoices](../../features/addon-service/expenses-e-invoices.md) | Banking (rcv/pay link) |
| `EINV_FEE_DETL` | `einvoice` | [sales-e-invoices](../../features/addon-service/sales-e-invoices.md) | — (schema-only, deferred) |
| `EINV_TAX_DETL` | `einvoice` | [sales-e-invoices](../../features/addon-service/sales-e-invoices.md) | — (schema-only, deferred) |
| `OPRT_STTS_INFO` | `operation` | [operation-status-inquiry](../../features/addon-service/operation-status-inquiry.md) | e-invoice status badge |
| `EXT_TRAN_CALL_HIS` | `operation` | [operation-status-inquiry](../../features/addon-service/operation-status-inquiry.md) | — |
| `CARD_MSTR` | `payment/cardtransactions` | [card-transactions](../../features/payment/card-transactions.md) | — |
| `CARD_TRNS_HSTR` | `payment/cardtransactions` | [card-transactions](../../features/payment/card-transactions.md) | Approval box, COA |
| `CARD_TRNS_RCV_PAY_INFM` | `payment/cardtransactions` | [card-transactions](../../features/payment/card-transactions.md) | — |

> Card Transactions lives in the `payment/cardtransactions` Java package but is presented as an **Add-on / Corporate Card** feature; the ERD groups it with Add-on Service per the menu taxonomy.

## Settings — `domain/setting` · ERD [erd/settings-erd.md](erd/settings-erd.md)

| Table | Sub-module / package | Primary feature doc | Consumed by |
|---|---|---|---|
| `ACCT_AUTH_INFO` | `setting/bankaccount` | [bank-account](../../features/settings/bank-account.md) | Banking (auth checks) |
| `CHART_ACCT_INFM` | `setting/chartofaccounts` | [chart-of-accounts](../../features/settings/chart-of-accounts.md) | Payment, card, organizer (GL) |
| `TRCO_INFM` | `setting/contacts` | [contacts](../../features/settings/contacts.md) | Banking, Payment (payee) |
| `TRCO_ADDR_INFM` | `setting/contacts` | [contacts](../../features/settings/contacts.md) | — |
| `TRCO_ACCT_INFO` | `setting/contacts` | [contacts](../../features/settings/contacts.md) | — |
| `TRCO_OVRS_INFM` | `setting/contacts` | [contacts](../../features/settings/contacts.md) | Overseas payment |
| `PGRO_BSAC_LNKN_INFM` | `setting/contacts` | [contacts](../../features/settings/contacts.md) | — |
| `TRCO_INFO_FILE` | `setting/contacts` | [contacts](../../features/settings/contacts.md) | — |
| `APPR_LINE_INFM` | `setting/approvalline` | [approval-line](../../features/settings/approval-line.md) | Approval box |
| `APPR_LINE_INFM_DETL` | `setting/approvalline` | [approval-line](../../features/settings/approval-line.md) | — |
| `TB_FIRM_CONF` | `setting/firmbankingsettings` | [firm-banking-settings](../../features/settings/firm-banking-settings.md) | Payment dispatch |
| `TB_FIRM_TRAN` | `setting/firmbankinghistory` | [firm-banking-history](../../features/settings/firm-banking-history.md) | — |
| `TB_FIRM_BANK_CODE` | `setting/firmbankinghistory` | [firm-banking-history](../../features/settings/firm-banking-history.md) | Contacts (bank-name resolve), Payment |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | `setting/firmbankinghistory` (+ `banking/nxisas/audit`) | [firm-banking-history](../../features/settings/firm-banking-history.md) | Banking scraping |
| `ERP_TRNS_HSTR` | `setting/erptransactionhistory` | [transaction-history](../../features/settings/transaction-history.md) | — |
| `BUSI_VAS_ACCT_INFM` | `setting/vasaccount` | [vas-account](../../features/settings/vas-account.md) | Banking (rcv/pay), organizer |
| `USAG_INFM` | `setting/purposesettings` | [purpose](../../features/settings/purpose.md) | Banking (rcv/pay usage) |
| `USAG_INFM_LANG_DETL` | `setting/purposesettings` | [purpose](../../features/settings/purpose.md) | — |
| `BUSI_USAG_SET_INFM` | `setting/purposesettings` | [purpose](../../features/settings/purpose.md) | — |
| `SYS_ENV_SET_INFM` | `setting/environmentsettings` | [preferences](../../features/settings/preferences.md) | — |
| `BUSI_ENV_SET_INFM` | `setting/environmentsettings` | [preferences](../../features/settings/preferences.md) | — |
| `BANK_CERT_INFO` | `setting/logincredentials` | [login-credentials](../../features/settings/login-credentials.md) | Banking scraping |
| `GDT_CERT_INFO` | `setting/logincredentials` | [login-credentials](../../features/settings/login-credentials.md) | Tax (GDT) |

## Shared / Common — `domain/auth` · `domain/common` · `common/*` · ERD [erd/common-erd.md](erd/common-erd.md)

| Table | Sub-module / package | Primary feature doc | Consumed by |
|---|---|---|---|
| `BUSI_INFM` | `auth` | [auth/login-auth](../../features/auth/login-auth.md) | every module (tenant hub) |
| `USER_INFM` | `auth` | [auth/login-auth](../../features/auth/login-auth.md) | every module (user resolve) |
| `USER_BUSI_CONN_INFM` | `auth` | [auth/login-auth](../../features/auth/login-auth.md) | Approval line (tenant guard) |
| `USER_LOGIN_HIS` | `auth` | [auth/login-auth](../../features/auth/login-auth.md) | — |
| `USER_PW_CHG_HIS` | `auth` | [change-password](../../features/settings/change-password.md) | — |
| `EMPL_INFM` | `auth` (bootstrap) | [auth/login-auth](../../features/auth/login-auth.md) | Payroll |
| `BUSI_ADMN_INFM` | `auth` | [auth/login-auth](../../features/auth/login-auth.md) | authority resolution |
| `BANK_INFM` | `common` (reference) | — | Banking, Payment, Settings, Contacts |
| `BANK_INFM_DETL` | `common` (reference) | — | bank-name / nickname resolution |
| `NAPAS_BANK_INFO` | `common` (reference) | — | Banking remittance, Contacts |
| `NATN_INFM` | `common/country` | — | Company / contacts (country picker) |
| `AUDIT_LOGS_INFM` | `common/auditlog` | [security/audit](../../../../docs/security/) | every audited write-path |
| `SYS_SCR_OPTION_WECMS` | `setting` (View Setting, global) | [common/view-setting](../../features/common/view-setting.md) | every list page |
| `USER_SCR_OPTION_WECMS` | `setting` (View Setting, global) | [common/view-setting](../../features/common/view-setting.md) | every list page |
| `USER_MENU_AUTH_INFM` | `common` (menu auth) | — | menu / permission resolution |
| `shedlock` | `common` (scheduler infra) | — | all `@Scheduled` jobs |

## Related

- ERD set: [erd/README.md](erd/README.md) · Coverage: [database-coverage-matrix.md](database-coverage-matrix.md)
- Open questions: [planning/open-questions/database-follow-up.md](../../planning/open-questions/database-follow-up.md)
- PROD active-table scope: `wecms-api/CLAUDE.md` § "PROD DB Table Scope for Refactoring"

# Database Coverage Matrix

**Generated:** 2026-06-01 (ERD Phase 2) · audited from Flyway migrations `V1`–`V237`, JPA `@Table` entities, repository interfaces, and the ERD set under [`erd/`](erd/).

Tracks, per live table: which Flyway migration creates it, whether a JPA **entity** and a Spring Data **repository** exist, and which **ERD** documents it. Use this to spot tables that are schema-only (no code) or have entities without repositories.

**Legend:** ✅ present · — absent · 🔭 deferred/not-yet-built (table exists in schema but no consumer)

## Summary

| Module | Live tables | With entity | With repository | In ERD |
|---|---|---|---|---|
| Banking | 10 | 10 | 10 | ✅ [banking-erd](erd/banking-erd.md) |
| Payment | 21 | 21 | 14 | ✅ [payment-erd](erd/payment-erd.md) |
| Add-on Service | 8 | 6 | 6 | ✅ [addon-service-erd](erd/addon-service-erd.md) |
| Settings | 24 | 23 | 23 | ✅ [settings-erd](erd/settings-erd.md) |
| Shared / Common | 16 | 14 | 15 | ✅ [common-erd](erd/common-erd.md) |
| **Total** | **79 tables** + 3 DB views | 74 | 68 | — |

> Table count is 79 distinct live tables here (the project-wide count of **80** in [full-project-erd.md](erd/full-project-erd.md) counts `NXSIAS_MODULE_TRAN_LOG_HIS` once under Settings; it is also written by Banking flows). Three DB **views** (`V_FIRM_BANKING_HISTORY`, `V_FIRM_BANKING_SETTING`, `V_PURPOSE_IN_USE`) are mapped as read-only entities and listed separately below.

## Banking — [erd/banking-erd.md](erd/banking-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `ACCT_MSTR` | V13 | ✅ | ✅ AcctMstrRepository |
| `ACCT_TRNS_HSTR` | V13 | ✅ | ✅ AcctTrnsHstrRepository |
| `ACCT_TRNS_RCV_PAY_INFM` | V13 | ✅ | ✅ AcctTrnsRcvPayInfmRepository |
| `BUSA_DOC_RCV_PAY_HIS` | V13 | ✅ | ✅ BusaDocRcvPayHisRepository |
| `NAPAS_REMIT_TRNS_HSTR` | V13 | ✅ | ✅ NapasRemitTrnsHstrRepository (+ TransferHistoryRepository) |
| `BANK_EXCH_RATE_INFM` | V127 | ✅ | ✅ BankExchRateInfmRepository |
| `RMRK_ORG_INFM` | V172 | ✅ | ✅ RmrkOrgInfmRepository |
| `MEMO_INFM` | V202 | ✅ | ✅ MemoInfmRepository |
| `BUSI_MATCH_LOG` | V170 | ✅ | ✅ BusiMatchLogRepository |
| `DAILY_CASH_REPT_DAILY_SUMR_DETL` | V125 | ✅ | ✅ DailyCashReptDailySumrDetlRepository |

## Payment — [erd/payment-erd.md](erd/payment-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `SCDUL_PYMN_SUMR` | V131 | ✅ | ✅ ScdulPymnSumrRepository |
| `SCDUL_PYMN_SUMR_DETL` | V131 | ✅ | ✅ ScdulPymnSumrDetlRepository |
| `INTERNAL_TRANSFER_KEY_SEQ` | V131 | ✅ | ✅ InternalTransferKeySeqRepository |
| `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` | V188 | ✅ | — (no dedicated repo; via DispatchCountRepository) |
| `OVRS_PYMN_SUMR` | V156 | ✅ | ✅ OvrsPymnSumrRepository |
| `OVRS_PYMN_MSTR` | V156 | ✅ | ✅ OvrsPymnMstrRepository (+ OverseasPaymentRepository) |
| `OVRS_PYMN_DTLS` | V138 | ✅ | ✅ OvrsPymnDtlsRepository |
| `OVERSEAS_PAYMENT_KEY_SEQ` | V158 | ✅ | ✅ OverseasPaymentKeySeqRepository |
| `EXPT_RESOL_MSTR` | V109 | ✅ | ✅ ExptResolMstrRepository |
| `EXPT_RESOL_APPRVR_INFM` | V109 | ✅ | ✅ ExptResolApprvrInfmRepository (+ OverseasApproverRepository) |
| `EXPT_RESOL_FILE` | V211 | ✅ | ✅ ExptResolFileRepository |
| `TB_PAY_TARGET` | V148 | ✅ | ✅ TbPayTargetRepository |
| `TB_PAY_FILE_MST` | V148 | ✅ | ✅ TbPayFileMstRepository |
| `TB_PAY_FILE_DATA` | V148 | ✅ | ✅ TbPayFileDataRepository |
| `TB_BATCH_SCHEDULE_INFO` | V189 | ✅ | — 🔭 |
| `TB_BATCH_SCHEDULE_DTL` | V189 | ✅ | — 🔭 |
| `TB_FIRM_BAT_API_TRAN` | V189 | ✅ | — 🔭 |
| `TB_FIRM_FILE_MST` | V189 | ✅ | — 🔭 |
| `TB_FIRM_FILE_DATA` | V189 | ✅ | — 🔭 |
| `TB_FIRM_FILE_RSLT` | V189 | ✅ | — 🔭 |
| `BLOCKED_CHARS_RULE` | V149 | ✅ | ✅ BlockedCharsRuleRepository |

> The six `TB_BATCH_SCHEDULE_*` / `TB_FIRM_BAT_API_TRAN` / `TB_FIRM_FILE_*` tables have entities (schema parity) but **no repository** — the dispatch write-path that would consume them is not yet ported. See [database-follow-up.md](../../planning/open-questions/database-follow-up.md) context and the dispatch schema doc.

## Add-on Service — [erd/addon-service-erd.md](erd/addon-service-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `BUSA_DOC_MSTR` | V15 | ✅ | ✅ BusaDocMstrRepository |
| `EINV_FEE_DETL` | V15 | — 🔭 | — 🔭 |
| `EINV_TAX_DETL` | V15 | — 🔭 | — 🔭 |
| `OPRT_STTS_INFO` | V16 | ✅ | ✅ OprtSttsInfoRepository |
| `EXT_TRAN_CALL_HIS` | V16 | ✅ | ✅ ExtTranCallHisRepository |
| `CARD_MSTR` | V192 | ✅ | ✅ CardMstrRepository |
| `CARD_TRNS_HSTR` | V192 (redef. V184) | ✅ | ✅ CardTrnsHstrRepository |
| `CARD_TRNS_RCV_PAY_INFM` | V200 | ✅ | ✅ CardTrnsRcvPayInfmRepository |

> `EINV_FEE_DETL` / `EINV_TAX_DETL` are schema-only — declared in V15 for the future e-invoice detail page; the listing feature does not map them. No entity, no repository.

## Settings — [erd/settings-erd.md](erd/settings-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `ACCT_AUTH_INFO` | V153 | ✅ | ✅ AcctAuthInfoRepository |
| `CHART_ACCT_INFM` | V94 | ✅ | ✅ ChartOfAccountRepository |
| `TRCO_INFM` | V112 | ✅ | ✅ TrcoInfmRepository |
| `TRCO_ADDR_INFM` | V112 | ✅ | ✅ TrcoAddrInfmRepository |
| `TRCO_ACCT_INFO` | V112 | ✅ | ✅ TrcoAcctInfoRepository |
| `TRCO_OVRS_INFM` | V112 | ✅ | ✅ TrcoOvrsInfmRepository |
| `PGRO_BSAC_LNKN_INFM` | V112 | ✅ | ✅ PgroBsacLnknInfmRepository |
| `TRCO_INFO_FILE` | V139 | ✅ | ✅ TrcoInfoFileRepository |
| `APPR_LINE_INFM` | V90 | ✅ | ✅ ApprovalLineRepository |
| `APPR_LINE_INFM_DETL` | V90 | ✅ | ✅ ApprovalLineApproverRepository |
| `TB_FIRM_CONF` | V62 | ✅ | ✅ FirmBankingSettingRepository |
| `TB_FIRM_TRAN` | V59 | ✅ | ✅ TbFirmTranRepository |
| `TB_FIRM_BANK_CODE` | V59 | ✅ | ✅ TbFirmBankCodeRepository |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | V59 / V101 | ✅ | ✅ NxsiasModuleTranLogHisRepository |
| `ERP_TRNS_HSTR` | V71 | ✅ | ✅ ErpTrnsHstrRepository |
| `BUSI_VAS_ACCT_INFM` | V77 | ✅ | ✅ BusiVasAcctInfmRepository |
| `USAG_INFM` | V201 | ✅ | ✅ UsagInfmRepository |
| `USAG_INFM_LANG_DETL` | V201 | — | — (read via UsagInfm join) |
| `BUSI_USAG_SET_INFM` | V80 | ✅ | ✅ PurposeSettingsRepository |
| `SYS_ENV_SET_INFM` | V69 | ✅ | ✅ SysEnvSetInfmRepository |
| `BUSI_ENV_SET_INFM` | V69 | ✅ | ✅ BusiEnvSetInfmRepository |
| `BANK_CERT_INFO` | V100 | ✅ | ✅ BankCertInfoRepository |
| `GDT_CERT_INFO` | V128 | ✅ | ✅ GdtCertInfoRepository |

## Shared / Common — [erd/common-erd.md](erd/common-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `BUSI_INFM` | V1 | ✅ | ✅ BusiInfmRepository |
| `USER_INFM` | V1 | ✅ | ✅ UserInfmRepository |
| `USER_BUSI_CONN_INFM` | V1 | ✅ | ✅ UserBusiConnInfmRepository |
| `USER_LOGIN_HIS` | V1 | ✅ | ✅ UserLoginHisRepository |
| `USER_PW_CHG_HIS` | V215 | ✅ | ✅ UserPwChgHisRepository |
| `EMPL_INFM` | V11 | ✅ | ✅ EmplInfmRepository |
| `BUSI_ADMN_INFM` | V11 | ✅ | ✅ BusiAdmnInfmRepository |
| `BANK_INFM` | V20 | ✅ | ✅ BankInfmRepository (+ BankPickerRepository, BankFormInfoRepository) |
| `BANK_INFM_DETL` | V44 | — | ✅ BankInfmDetlPeriodLimitRepository (projection; no `@Table` entity) |
| `NAPAS_BANK_INFO` | V68 | ✅ | ✅ NapasBankInfoRepository |
| `NATN_INFM` | V205 | ✅ | ✅ NatnInfmRepository |
| `AUDIT_LOGS_INFM` | V63 | ✅ | ✅ AuditLogEntryRepository (+ AuditLogSeqNoRepository) |
| `SYS_SCR_OPTION_WECMS` | V30 | ✅ | ✅ SysScrOptionWecmsRepository |
| `USER_SCR_OPTION_WECMS` | V30 | ✅ | ✅ UserScrOptionWecmsRepository |
| `USER_MENU_AUTH_INFM` | V126 | ✅ | ✅ UserMenuAuthInfmRepository |
| `shedlock` | V210 | — | — (ShedLock library-managed) |

## DB views (read-only entities)

| View | Backing tables | Entity | Repository |
|---|---|---|---|
| `V_FIRM_BANKING_HISTORY` | `TB_FIRM_TRAN` + `NXSIAS_MODULE_TRAN_LOG_HIS` (unified) | ✅ | ✅ FirmBankingHistoryRepository |
| `V_FIRM_BANKING_SETTING` | `TB_FIRM_CONF` + `TB_FIRM_BANK_CODE` + `ACCT_MSTR` | ✅ | ✅ FirmBankingSettingViewRepository |
| `V_PURPOSE_IN_USE` | purpose/usage reference join | ✅ | ✅ PurposeInUseRepository |

## Coverage gaps (snapshot)

1. **Schema-only, no entity (5):** `EINV_FEE_DETL`, `EINV_TAX_DETL` (e-invoice detail, deferred), `USAG_INFM_LANG_DETL` (read via join), `BANK_INFM_DETL` (projection repo only), `shedlock` (library).
2. **Entity but no repository (6 + 1):** `TB_BATCH_SCHEDULE_INFO`, `TB_BATCH_SCHEDULE_DTL`, `TB_FIRM_BAT_API_TRAN`, `TB_FIRM_FILE_MST`, `TB_FIRM_FILE_DATA`, `TB_FIRM_FILE_RSLT` (dispatch write-path not ported), and `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` (accessed via projection).
3. **PROD-active tables not yet migrated** (no schema/entity, absent from all ERDs) — the `*_HIS` history twins, `BUSA_DOC_DETL(_HIS)`, `EXPT_RESOL_APPR_LINE`, `EXPT_RESOL_DETL`, `EXPT_RESOL_PAY_EVID_FILE`, `DAILY_FINCL_REPT_*`, `CCY_INFM`, `CHNL_INFM`, `BASE_INFM`, `BLTB_*`, `PUSH_DEVICE_*`, `*_MDPH`/`*_MDTR`. Full list in [erd/full-project-erd.md § Missing FK/index documentation](erd/full-project-erd.md#missing-fk--index-documentation-gaps-to-note).
4. **Removed in V31** (excluded everywhere): `BUSI_COND_DAILY_SUMR(_DETL)`, `SELL_HIS_DAILY_SUMR(_DETL)`, `BAT_CALL_HIS`, `ACCT_TRNS_RCV_PAY_EVID_FILE`.

## Related

- ERD set: [erd/README.md](erd/README.md) · Table ownership: [table-ownership.md](table-ownership.md)
- Open questions: [planning/open-questions/database-follow-up.md](../../planning/open-questions/database-follow-up.md)
- DB reference root: [README.md](README.md)

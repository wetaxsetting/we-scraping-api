# Database Coverage Matrix

**Generated:** 2026-06-01 (ERD Phase 2) · **Migration column regenerated from disk 2026-08-17.**

> ⚠️ The original matrix was audited against the pre-rebaseline set (`V1`–`V237`). The
> [2026-06-09 rebaseline](flyway-migration-baseline.md) reassigned every number to a strict
> one-table-per-migration scheme, so the Migration column has been **recomputed from
> `src/main/resources/db/migration/`** — each row now names the migration that actually creates that
> table today. Rows reading **DROPPED** or **no refactor DDL migration** are accurate as written: no
> current migration creates them. Entity/Repository columns are unchanged from the 2026-06-01 audit.

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
| `ACCT_MSTR` | V2 | ✅ | ✅ AcctMstrRepository |
| `ACCT_TRNS_HSTR` | V3 | ✅ | ✅ AcctTrnsHstrRepository |
| `ACCT_TRNS_RCV_PAY_INFM` | V4 | ✅ | ✅ AcctTrnsRcvPayInfmRepository |
| `BUSA_DOC_RCV_PAY_HIS` | V25 | ✅ | ✅ BusaDocRcvPayHisRepository |
| `NAPAS_REMIT_TRNS_HSTR` | V69 | ✅ | ✅ NapasRemitTrnsHstrRepository (+ TransferHistoryRepository) |
| `BANK_EXCH_RATE_INFM` | V10 | ✅ | ✅ BankExchRateInfmRepository |
| `RMRK_ORG_INFM` | V85 | ✅ | ✅ RmrkOrgInfmRepository |
| `MEMO_INFM` | V65 | ✅ | ✅ MemoInfmRepository |
| `BUSI_MATCH_LOG` | V33 | ✅ | ✅ BusiMatchLogRepository |
| `DAILY_CASH_REPT_DAILY_SUMR_DETL` | V45 | ✅ | ✅ DailyCashReptDailySumrDetlRepository |

## Payment — [erd/payment-erd.md](erd/payment-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `SCDUL_PYMN_SUMR` | V86 | ✅ | ✅ ScdulPymnSumrRepository |
| `SCDUL_PYMN_SUMR_DETL` | V87 | ✅ | ✅ ScdulPymnSumrDetlRepository |
| `INTERNAL_TRANSFER_KEY_SEQ` | — **DROPPED** (historical `V131`) | ❌ | ❌ — no entity or repository on disk; `SCDUL_PYMN_NO` now funnels through `wb_get_next_val` |
| `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` | — (archived V188) | ❌ removed 2026-06-10 | — (legacy API-path bridge; never used by Spring) |
| `OVRS_PYMN_SUMR` | V75 | ✅ | ✅ OvrsPymnSumrRepository |
| `OVRS_PYMN_MSTR` | V78 | ✅ | ✅ OvrsPymnMstrRepository (+ OverseasPaymentRepository) |
| `OVRS_PYMN_DTLS` | — **no refactor DDL migration** (schema is external/live-JEX; referenced by `V140` + `V248`) | ✅ | ✅ OvrsPymnDtlsRepository |
| `OVERSEAS_PAYMENT_KEY_SEQ` | — **DROPPED** (historical `V158`) | ❌ | ❌ — no entity or repository on disk |
| `EXPT_RESOL_MSTR` | V59 | ✅ | ✅ ExptResolMstrRepository |
| `EXPT_RESOL_APPRVR_INFM` | V56 | ✅ | ✅ ExptResolApprvrInfmRepository (+ OverseasApproverRepository) |
| `EXPT_RESOL_FILE` | V58 | ✅ | ✅ ExptResolFileRepository |
| `TB_PAY_TARGET` | V109 | ✅ | ✅ TbPayTargetRepository |
| `TB_PAY_FILE_MST` | V107 | ✅ | ✅ TbPayFileMstRepository |
| `TB_PAY_FILE_DATA` | V105 | ✅ | ✅ TbPayFileDataRepository |
| `TB_BATCH_SCHEDULE_INFO` | V96 | ✅ | — 🔭 |
| `TB_BATCH_SCHEDULE_DTL` | V95 | ✅ | — 🔭 |
| `TB_FIRM_BAT_API_TRAN` | V99 | ✅ | — 🔭 |
| `TB_FIRM_FILE_MST` | V102 | ✅ | — 🔭 |
| `TB_FIRM_FILE_DATA` | V101 | ✅ | — 🔭 |
| `TB_FIRM_FILE_RSLT` | V103 | ✅ | — 🔭 |
| `BLOCKED_CHARS_RULE` | V14 | ✅ | ✅ BlockedCharsRuleRepository |

> The six `TB_BATCH_SCHEDULE_*` / `TB_FIRM_BAT_API_TRAN` / `TB_FIRM_FILE_*` tables have entities (schema parity) but **no repository** — the dispatch write-path that would consume them is not yet ported. See database-follow-up.md _(removed — historical `planning/` folder deleted by project decision)_ context and the dispatch schema doc.

## Add-on Service — [erd/addon-service-erd.md](erd/addon-service-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `BUSA_DOC_MSTR` | V23 | ✅ | ✅ BusaDocMstrRepository |
| `EINV_FEE_DETL` | V48 | — 🔭 | — 🔭 |
| `EINV_TAX_DETL` | V50 | — 🔭 | — 🔭 |
| `OPRT_STTS_INFO` | V74 | ✅ | ✅ OprtSttsInfoRepository |
| `EXT_TRAN_CALL_HIS` | V61 | ✅ | ✅ ExtTranCallHisRepository |
| `CARD_MSTR` | V38 | ✅ | ✅ CardMstrRepository |
| `CARD_TRNS_HSTR` | V192 (redef. V184) | ✅ | ✅ CardTrnsHstrRepository |
| `CARD_TRNS_RCV_PAY_INFM` | V40 | ✅ | ✅ CardTrnsRcvPayInfmRepository |

> `EINV_FEE_DETL` / `EINV_TAX_DETL` are schema-only — declared in V15 for the future e-invoice detail page; the listing feature does not map them. No entity, no repository.

## Settings — [erd/settings-erd.md](erd/settings-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `ACCT_AUTH_INFO` | V1 | ✅ | ✅ AcctAuthInfoRepository |
| `CHART_ACCT_INFM` | V42 | ✅ | ✅ ChartOfAccountRepository |
| `TRCO_INFM` | V119 | ✅ | ✅ TrcoInfmRepository |
| `TRCO_ADDR_INFM` | V114 | ✅ | ✅ TrcoAddrInfmRepository |
| `TRCO_ACCT_INFO` | V112 | ✅ | ✅ TrcoAcctInfoRepository |
| `TRCO_OVRS_INFM` | V121 | ✅ | ✅ TrcoOvrsInfmRepository |
| `PGRO_BSAC_LNKN_INFM` | V82 | ✅ | ✅ PgroBsacLnknInfmRepository |
| `TRCO_INFO_FILE` | V116 | ✅ | ✅ TrcoInfoFileRepository |
| `APPR_LINE_INFM` | V6 | ✅ | ✅ ApprovalLineRepository |
| `APPR_LINE_INFM_DETL` | V7 | ✅ | ✅ ApprovalLineApproverRepository |
| `TB_FIRM_CONF` | V100 | ✅ | ✅ FirmBankingSettingRepository |
| `TB_FIRM_TRAN` | V104 | ✅ | ✅ TbFirmTranRepository |
| `TB_FIRM_BANK_CODE` | V97 | ✅ | ✅ TbFirmBankCodeRepository |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | V59 / V101 | ✅ | ✅ NxsiasModuleTranLogHisRepository |
| `ERP_TRNS_HSTR` | V54 | ✅ | ✅ ErpTrnsHstrRepository |
| `BUSI_VAS_ACCT_INFM` | V37 | ✅ | ✅ BusiVasAcctInfmRepository |
| `USAG_INFM` | V122 | ✅ | ✅ UsagInfmRepository |
| `USAG_INFM_LANG_DETL` | V123 | — | — (read via UsagInfm join) |
| `BUSI_USAG_SET_INFM` | V35 | ✅ | ✅ PurposeSettingsRepository |
| `SYS_ENV_SET_INFM` | V92 | ✅ | ✅ SysEnvSetInfmRepository |
| `BUSI_ENV_SET_INFM` | V30 | ✅ | ✅ BusiEnvSetInfmRepository |
| `BANK_CERT_INFO` | V9 | ✅ | ✅ BankCertInfoRepository |
| `GDT_CERT_INFO` | V62 | ✅ | ✅ GdtCertInfoRepository |

## Shared / Common — [erd/common-erd.md](erd/common-erd.md)

| Table | Migration | Entity | Repository |
|---|---|---|---|
| `BUSI_INFM` | V32 | ✅ | ✅ BusiInfmRepository |
| `USER_INFM` | V128 | ✅ | ✅ UserInfmRepository |
| `USER_BUSI_CONN_INFM` | V125 | ✅ | ✅ UserBusiConnInfmRepository |
| `USER_LOGIN_HIS` | V129 | ✅ | ✅ UserLoginHisRepository |
| `USER_PW_CHG_HIS` | V132 | ✅ | ✅ UserPwChgHisRepository |
| `EMPL_INFM` | V52 | ✅ | ✅ EmplInfmRepository |
| `BUSI_ADMN_INFM` | V27 | ✅ | ✅ BusiAdmnInfmRepository |
| `BANK_INFM` | V11 | ✅ | ✅ BankInfmRepository (+ BankPickerRepository, BankFormInfoRepository) |
| `BANK_INFM_DETL` | V12 | — | ✅ BankInfmDetlPeriodLimitRepository (projection; no `@Table` entity) |
| `NAPAS_BANK_INFO` | V67 | ✅ | ✅ NapasBankInfoRepository |
| `NATN_INFM` | V70 | ✅ | ✅ NatnInfmRepository |
| `AUDIT_LOGS_INFM` | V8 | ✅ | ✅ AuditLogEntryRepository (+ AuditLogSeqNoRepository) |
| `SYS_SCR_OPTION_WECMS` | V93 | ✅ | ✅ SysScrOptionWecmsRepository |
| `USER_SCR_OPTION_WECMS` | V133 | ✅ | ✅ UserScrOptionWecmsRepository |
| `USER_MENU_AUTH_INFM` | V131 | ✅ | ✅ UserMenuAuthInfmRepository |
| `shedlock` | V210 | — | — (ShedLock library-managed) |

## DB views (read-only entities)

| View | Backing tables | Entity | Repository |
|---|---|---|---|
| `V_FIRM_BANKING_HISTORY` | `TB_FIRM_TRAN` + `NXSIAS_MODULE_TRAN_LOG_HIS` (unified) | ✅ | ✅ FirmBankingHistoryRepository |
| `V_FIRM_BANKING_SETTING` | `TB_FIRM_CONF` + `TB_FIRM_BANK_CODE` + `ACCT_MSTR` | ✅ | ✅ FirmBankingSettingViewRepository |
| `V_PURPOSE_IN_USE` | purpose/usage reference join | ✅ | ✅ PurposeInUseRepository |

## Coverage gaps (snapshot)

1. **Schema-only, no entity (5):** `EINV_FEE_DETL`, `EINV_TAX_DETL` (e-invoice detail, deferred), `USAG_INFM_LANG_DETL` (read via join), `BANK_INFM_DETL` (projection repo only), `shedlock` (library).
2. **Entity but no repository (6):** `TB_BATCH_SCHEDULE_INFO`, `TB_BATCH_SCHEDULE_DTL`, `TB_FIRM_BAT_API_TRAN`, `TB_FIRM_FILE_MST`, `TB_FIRM_FILE_DATA`, `TB_FIRM_FILE_RSLT` (dispatch write-path not ported). (`SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` was here previously but its entity was **removed 2026-06-10** — never used by Spring.)
3. **PROD-active tables not yet migrated** (no schema/entity, absent from all ERDs) — the `*_HIS` history twins, `BUSA_DOC_DETL(_HIS)`, `EXPT_RESOL_APPR_LINE`, `EXPT_RESOL_DETL`, `EXPT_RESOL_PAY_EVID_FILE`, `DAILY_FINCL_REPT_*`, `CCY_INFM`, `CHNL_INFM`, `BASE_INFM`, `BLTB_*`, `PUSH_DEVICE_*`, `*_MDPH`/`*_MDTR`. Full list in [erd/full-project-erd.md § Missing FK/index documentation](erd/full-project-erd.md#missing-fk--index-documentation-gaps-to-note).
4. **Removed in V31** (excluded everywhere): `BUSI_COND_DAILY_SUMR(_DETL)`, `SELL_HIS_DAILY_SUMR(_DETL)`, `BAT_CALL_HIS`, `ACCT_TRNS_RCV_PAY_EVID_FILE`.

## Related

- ERD set: [erd/README.md](erd/README.md) · Table ownership: [table-ownership.md](table-ownership.md)
- Open questions: planning/open-questions/database-follow-up.md _(removed — historical `planning/` folder deleted by project decision)_
- DB reference root: [README.md](README.md)

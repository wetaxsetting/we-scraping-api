# WeCMS-API — Full-Project ERD

**Diagram source:** [`diagrams/full-project-erd.mmd`](diagrams/full-project-erd.mmd)
**Generated:** 2026-06-01 · audited from Flyway migrations `V1`–`V237` (`src/main/resources/db/migration`), JPA entities under `src/main/java/com/kosign/wecms/domain/**/entity`, repositories, and existing schema docs under [`../`](../).

This is the cross-module map. Per-module attribute detail lives in:
[banking-erd.md](banking-erd.md) · [payment-erd.md](payment-erd.md) · [addon-service-erd.md](addon-service-erd.md) · [settings-erd.md](settings-erd.md).

## How to read these ERDs

- **Tenancy:** nearly every table carries `BIZ_KEY` and is scoped to one tenant. `BUSI_INFM` is the hub; `BIZ_KEY` is the spine that connects every module.
- **FK vs Logical:** the legacy JEX schema declares very few foreign keys. Where a column clearly references another table but **no `FOREIGN KEY` exists in SQL**, the relationship is labelled **Logical — no FK found** and enforced in the service layer. Declared FKs are noted explicitly.
- **Audit columns:** business tables use `REG_ID/REG_DTM/MOD_ID/MOD_DTM` as `VARCHAR(14)` (`yyyyMMddHHmmss`) for DEV parity; dispatch tables use `*_DTTM`; refactor-only helper tables use `TIMESTAMPTZ`.
- **The DB schema is UPPER_SNAKE** (legacy parity); the JSON wire is lowercase snake_case. See `wecms-api/CLAUDE.md`.

## Module inventory (80 live tables)

| Module | Count | Tables |
|---|---|---|
| **Banking** | 10 | ACCT_MSTR · ACCT_TRNS_HSTR · ACCT_TRNS_RCV_PAY_INFM · BUSA_DOC_RCV_PAY_HIS · NAPAS_REMIT_TRNS_HSTR · BANK_EXCH_RATE_INFM · RMRK_ORG_INFM · MEMO_INFM · BUSI_MATCH_LOG · DAILY_CASH_REPT_DAILY_SUMR_DETL |
| **Payment** | 21 | SCDUL_PYMN_SUMR · SCDUL_PYMN_SUMR_DETL · INTERNAL_TRANSFER_KEY_SEQ · SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN · OVRS_PYMN_SUMR · OVRS_PYMN_MSTR · OVRS_PYMN_DTLS · OVERSEAS_PAYMENT_KEY_SEQ · EXPT_RESOL_MSTR · EXPT_RESOL_APPRVR_INFM · EXPT_RESOL_FILE · TB_PAY_TARGET · TB_PAY_FILE_MST · TB_PAY_FILE_DATA · TB_BATCH_SCHEDULE_INFO · TB_BATCH_SCHEDULE_DTL · TB_FIRM_BAT_API_TRAN · TB_FIRM_FILE_MST · TB_FIRM_FILE_DATA · TB_FIRM_FILE_RSLT · BLOCKED_CHARS_RULE |
| **Add-on Service** | 8 | BUSA_DOC_MSTR · EINV_FEE_DETL · EINV_TAX_DETL · OPRT_STTS_INFO · EXT_TRAN_CALL_HIS · CARD_MSTR · CARD_TRNS_HSTR · CARD_TRNS_RCV_PAY_INFM |
| **Settings** | 24 | ACCT_AUTH_INFO · CHART_ACCT_INFM · TRCO_INFM · TRCO_ADDR_INFM · TRCO_ACCT_INFO · TRCO_OVRS_INFM · PGRO_BSAC_LNKN_INFM · TRCO_INFO_FILE · APPR_LINE_INFM · APPR_LINE_INFM_DETL · TB_FIRM_CONF · TB_FIRM_TRAN · TB_FIRM_BANK_CODE · NXSIAS_MODULE_TRAN_LOG_HIS · ERP_TRNS_HSTR · BUSI_VAS_ACCT_INFM · USAG_INFM · USAG_INFM_LANG_DETL · BUSI_USAG_SET_INFM · SYS_ENV_SET_INFM · BUSI_ENV_SET_INFM · BANK_CERT_INFO · GDT_CERT_INFO |
| **Shared / Common** | 16 | BUSI_INFM · USER_INFM · USER_BUSI_CONN_INFM · USER_LOGIN_HIS · USER_PW_CHG_HIS · EMPL_INFM · BUSI_ADMN_INFM · BANK_INFM · BANK_INFM_DETL · NAPAS_BANK_INFO · NATN_INFM · AUDIT_LOGS_INFM · SYS_SCR_OPTION_WECMS · USER_SCR_OPTION_WECMS · USER_MENU_AUTH_INFM · shedlock |

> `NXSIAS_MODULE_TRAN_LOG_HIS` is counted once (Settings → firm-banking-history) but is also written by Banking scraping flows.

## Shared / Common tables

These tables back authentication, tenancy, reference data, audit, and the global View-Setting / menu features. They are consumed by every module.

| Table | Source | Purpose | PK |
|---|---|---|---|
| `BUSI_INFM` | V1 (+V8) | Company / tenant master — the multi-tenant hub | `BIZ_KEY` |
| `USER_INFM` | V1 | User master (login credentials) | `USER_ID` |
| `USER_BUSI_CONN_INFM` | V1 | User ↔ company linkage | `(USER_ID, BIZ_KEY)` |
| `USER_LOGIN_HIS` | V1 (+V28) | Login attempt audit | `(USER_ID, REG_DTM, REG_UID)` |
| `USER_PW_CHG_HIS` | V215 | Password-change history | `(USER_ID, REG_SEQ_NO)` |
| `EMPL_INFM` | V11 | Employee master | `(BIZ_KEY, EMPL_NO)` |
| `BUSI_ADMN_INFM` | V11 | Per-tenant admin authority source | `(BIZ_KEY, USER_ID)` |
| `BANK_INFM` | V20 | Vietnamese bank registry (display names) | `BANK_CD` |
| `BANK_INFM_DETL` | V44 | Bank detail / short-name info | `(BANK_CD, BANK_DETL_INFO_CD, START_DATE)` |
| `NAPAS_BANK_INFO` | V68 | NAPAS code → internal bank-code lookup | `NAPAS_BANK_CD` |
| `NATN_INFM` | V205 | Country master | `NTNL_CD` |
| `AUDIT_LOGS_INFM` | V63 | Cross-cutting audit log (one row per locale) | `(BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD)` |
| `SYS_SCR_OPTION_WECMS` | V30 (+V32-V42) | View-Setting system defaults | `(WEB_SVC_ID, FIELD_ID)` |
| `USER_SCR_OPTION_WECMS` | V30 (+V32) | View-Setting per-user overrides | `(BIZ_KEY, USER_ID, WEB_SVC_ID, FIELD_ID)` |
| `USER_MENU_AUTH_INFM` | V126 | Per-user menu entitlements | `(BIZ_KEY, USER_ID, MENU_AUTH_CD)` |
| `shedlock` | V210 | ShedLock distributed scheduler lock | `name` |

### Shared/Common declared FKs
- `USER_BUSI_CONN_INFM` → `USER_INFM` (`FK_UBCI_USER`), → `BUSI_INFM` (`FK_UBCI_BUSI`)
- `BUSI_ADMN_INFM` → `USER_INFM`, → `BUSI_INFM`
- `EMPL_INFM` → `BUSI_INFM`

All other shared relationships (`USER_LOGIN_HIS`/`USER_PW_CHG_HIS`/`USER_MENU_AUTH_INFM` → `USER_INFM`; `BUSI_INFM.NTNL_CD` → `NATN_INFM`; `BANK_INFM_DETL`/`NAPAS_BANK_INFO` → `BANK_INFM`; `USER_SCR_OPTION_WECMS` → `SYS_SCR_OPTION_WECMS`) are **Logical — no FK found**.

## Cross-module relationships

Every module anchors on `BUSI_INFM` via `BIZ_KEY` (Payment's `SCDUL_PYMN_SUMR`/`EXPT_RESOL_MSTR` are the only ones with a **declared** FK to `BUSI_INFM`; the rest are logical). The notable cross-module **logical** links:

| From (module) | To (module) | Via | Kind |
|---|---|---|---|
| `BANK_INFM` (shared) | `ACCT_MSTR` / `ACCT_TRNS_HSTR` / `BANK_EXCH_RATE_INFM` (banking) | `BANK_CD` | Logical |
| `BANK_INFM` / `NAPAS_BANK_INFO` (shared) | `NAPAS_REMIT_TRNS_HSTR` (banking) | `OUT_BANK_CODE` / `IN_NAPAS_BANK_CODE` | Logical |
| `TRCO_INFM` (settings) | `ACCT_TRNS_RCV_PAY_INFM` (banking), `SCDUL_PYMN_SUMR_DETL` (payment) | `TRCO_KEY` | Logical |
| `CHART_ACCT_INFM` (settings) | `SCDUL_PYMN_SUMR_DETL` (payment), `CARD_TRNS_HSTR` (add-on), `RMRK_ORG_INFM` (banking) | `CHART_ACCT_SEQ_NO` | Logical |
| `EXPT_RESOL_MSTR` (payment) | `SCDUL_PYMN_SUMR` / `OVRS_PYMN_*` (payment), `CARD_TRNS_HSTR` (add-on) | `APPR_SEQ_NO` | Logical |
| `BUSI_VAS_ACCT_INFM` (settings) | `ACCT_TRNS_RCV_PAY_INFM` (banking), `RMRK_ORG_INFM` (banking) | `VAS_ACCT_SEQ_NO` / `VAS_ACCT_CD` | Logical |
| `USAG_INFM` (settings) | `RMRK_ORG_INFM` (banking), `ACCT_TRNS_RCV_PAY_INFM` (banking) | `USE_USAG_CD` | Logical |
| `BUSA_DOC_MSTR` (add-on) | `BUSA_DOC_RCV_PAY_HIS` (banking) | `DOC_ID` | Logical |
| `TB_FIRM_BANK_CODE` (settings) | `TB_FIRM_CONF` (settings), dispatch `OUT_BANK_CD` (payment) | `BANK_CD` | Logical |
| `ACCT_MSTR` (banking) | `ACCT_AUTH_INFO` (settings), `TB_FIRM_CONF` (settings) | `BANK_CD, ACCT_NO` / `WDRL_ACT_NO` | Logical |

## Declared foreign keys (whole project)

The complete set of `FOREIGN KEY` constraints that actually exist in the schema:

| Child | Parent | Constraint / note |
|---|---|---|
| `USER_BUSI_CONN_INFM` | `USER_INFM`, `BUSI_INFM` | `FK_UBCI_USER`, `FK_UBCI_BUSI` |
| `BUSI_ADMN_INFM` | `USER_INFM`, `BUSI_INFM` | V11 |
| `EMPL_INFM` | `BUSI_INFM` | `fk_empl_infm_busi` |
| `SCDUL_PYMN_SUMR` | `BUSI_INFM` | V131 |
| `SCDUL_PYMN_SUMR_DETL` | `SCDUL_PYMN_SUMR` | V131 |
| `EXPT_RESOL_MSTR` | `BUSI_INFM` | V109 |
| `TRCO_ADDR_INFM` | `TRCO_INFM` | V112 (deferrable) |
| `TRCO_ACCT_INFO` | `TRCO_INFM` | `trco_acct_info_fk01` (deferrable) |
| `TRCO_INFO_FILE` | `TRCO_INFM` | V139 |
| `PGRO_BSAC_LNKN_INFM` | `TRCO_INFM`, `BUSI_INFM` | `pgro_bsac_lnkn_infm_fk01/fk02` |
| `BUSI_ENV_SET_INFM` | `BUSI_INFM`, `SYS_ENV_SET_INFM` | V69 |

Everything else is a **logical** relationship enforced by application code.

## Needs Confirmation

Items flagged by the source audit that a maintainer should verify (do not treat as settled):

1. **`NXSIAS_MODULE_TRAN_LOG_HIS` dual definition.** Created by both V59 (PK `(BIZ_KEY, REG_DTM, UUID)`) and V101 (PK `(UUID, REG_DTM)`), both `CREATE TABLE IF NOT EXISTS`. On a fresh DB V59 wins (lower version). Confirm the intended PK shape and consolidate.
2. **`BANK_EXCH_RATE_INFM.BIZ_KEY` width.** Declared `VARCHAR(50)` while every other banking table uses `VARCHAR(20)`. Confirm intentional vs migration drift.
3. **`SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN.SCDUL_PYMN_NO` width.** V188 declares `VARCHAR(10)` but the parent `SCDUL_PYMN_SUMR.SCDUL_PYMN_NO` is `VARCHAR(20)` (V131). Confirm the bridge should inherit the parent width.
4. **`TB_FIRM_FILE_DATA` PK omits `BIZ_KEY`.** Intentional DEV design (column kept as non-PK). Confirm no multi-tenant isolation gap for this gateway table.
5. **`BUSA_DOC_RCV_PAY_HIS.ACCT_SEQ_NO`** — does it logically reference `ACCT_TRNS_HSTR.ACCT_SEQ_NO`, or is it independent? Added post-CREATE; relationship unverified.
6. **`AUDIT_LOGS_INFM.AUDIT_LOGS_SEQ_NO` precision.** Migration `NUMERIC` vs entity `BigDecimal` — confirm `NUMERIC(n,0)` bound.

## Missing FK / index documentation (gaps to note)

- **No declared FKs across Banking and Add-on Service** — referential integrity is entirely application-enforced. This is legacy parity, not a defect, but it means orphan rows are possible if a service path skips validation.
- **Dispatch `TB_FIRM_*` chain** (V189) ships with no helper indexes beyond PKs; high-volume lookups on `(BIZ_KEY, TRAN_DT)` may want indexes if these become hot.
- **`SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN`** (V188) has no declared index beyond its PK.
- **PROD active tables not yet migrated** (no entity/table in the refactor schema, so absent from these ERDs): the `*_HIS` history twins (`BUSA_DOC_MSTR_HIS`, `OVRS_PYMN_*_HIS`, `SCDUL_PYMN_SUMR_*_HIS`, `TB_PAY_*_HIS`, …), `BUSA_DOC_DETL(_HIS)`, `EXPT_RESOL_APPR_LINE`, `EXPT_RESOL_DETL`, `EXPT_RESOL_PAY_EVID_FILE`, `DAILY_FINCL_REPT_*`, `CCY_INFM`, `CHNL_INFM`, `BASE_INFM`, board/bulletin (`BLTB_*`), push (`PUSH_DEVICE_*`), and the `*_MDPH` / `*_MDTR` mirror tables. They are in scope per `CLAUDE.md` but not yet built — document them here when their migration lands.
- **Removed from schema (V31):** `BUSI_COND_DAILY_SUMR(_DETL)`, `SELL_HIS_DAILY_SUMR(_DETL)`, `BAT_CALL_HIS`, `ACCT_TRNS_RCV_PAY_EVID_FILE` — dropped as not-in-PROD-active-list; intentionally excluded from all ERDs above.

## Related

- ERD folder index: [README.md](README.md)
- DB reference root: [`../README.md`](../README.md) · Flyway notes: [`../flyway/`](../flyway/) · Indexing: [`../indexing/`](../indexing/) · Table mapping: [`../table-mapping/`](../table-mapping/)
- JEX ERD reference: [`../../../../jex/architecture/`](../../../../../docs/jex/architecture/)

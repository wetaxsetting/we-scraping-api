# Internal Transfer — Database Mapping (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

> **Hub:** [README.md](README.md) · **Schema baseline:** [legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md) · **Schema intent:** [database/payment/internal-transfer-schema.md](../../../references/database/payment/internal-transfer-schema.md)

Source-code-verified 2026-06-23. All entity mappings target legacy JEX column names/types as they exist in the live `wecmsvndev` schema (DDL is the source of truth; `ddl-auto=validate`). All tables below are in the legacy table inventory.

---

## 1. Tables in scope

| Table | Role | PK (verified) | Access |
|---|---|---|---|
| `SCDUL_PYMN_SUMR` | Batch master — one row per uploaded file = one Scheduled Transfer (`SCDUL_PYMN_TYPE_CD='03'`) | `(biz_key, scdul_pymn_no)` | read / write |
| `SCDUL_PYMN_SUMR_DETL` | Batch line — one row per recipient/amount line | `(biz_key, scdul_pymn_no, scdul_pymn_detl_no)` | read / write |
| `EXPT_RESOL_MSTR` | Approval-request master — linked via `SCDUL_PYMN_SUMR.APPR_SEQ_NO` | `(biz_key, appr_seq_no)` | read / write (shared w/ Approval Box) |
| `EXPT_RESOL_APPRVR_INFM` | Approver chain | per chain row | read / write |
| `EXPT_RESOL_DETL` | Resolution detail row | — | write (request) |
| `EXPT_RESOL_APPR_LINE` | Approval-line print snapshot | — | write (request, env-gated) |
| `EXPT_RESOL_FILE` | Approval-request attachments | `(biz_key, appr_seq_no, file_id)` | read / write |
| `EXPT_RESOL_PAY_EVID_FILE` | Payment-evidence attachments (combined-attachment UNION, GAP-ATT-1) | — | read |
| `TB_PAY_TARGET` | API-path dispatch target (VND / Woori USD→VND) | `(tran_dt, biz_key, tran_seq)` | write (dispatch) / read (result) |
| `TB_PAY_FILE_MST` | File-path dispatch master | `(tran_dt, biz_key, file_seq_no)` | write (dispatch) / read (result) |
| `TB_PAY_FILE_DATA` | File-path dispatch line | `(tran_dt, biz_key, file_seq_no, seq)` | write (dispatch) / read (result) |
| `TB_FIRM_TRAN` | Firm-banking transaction (result resolution) | — | read (result) |
| `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` | Join detail → `TB_PAY_TARGET` (note `SCDU_`, not `SCDUL_`) | `(biz_key, scdul_pymn_no, scdul_pymn_detl_no)` | write (dispatch) |
| `ACCT_MSTR` | Withdrawal account; `WDRL_CCY_CD` fallback (issue #42761) | — | read |
| `BANK_INFM` | Bank registry (`OUT_BANK_NM` / `TAR_BANK_NM`) | — | read |
| `CHART_ACCT_INFM` | Reporting account (ERP mapping) | `(biz_key, chart_acct_seq_no)` | read |
| `ERP_TRNS_HSTR` | ERP audit-history — upload/delete rows | — | write (best-effort) |
| `BLTB_*` | Bulletin / notification on request-approval | — | write (best-effort) |
| `AUDIT_LOGS_INFM` | Refactor audit log (`WSVC_ID='internal-transfer'`, 17 chars ≤ `VARCHAR(20)`) | `(biz_key, audit_logs_seq_no, lang_cd)` | write |

---

## 2. Entity ↔ table ↔ column

### `ScdulPymnSumr` → `SCDUL_PYMN_SUMR`

| Java field | Column | Type | Notes |
|---|---|---|---|
| `bizKey` (`@Id`) | `BIZ_KEY` | `VARCHAR(20)` | tenant |
| `scdulPymnNo` (`@Id`) | `SCDUL_PYMN_NO` | `VARCHAR(10)` | **entity length=10** (DEV parity); minted via `wb_get_next_val` |
| `scdulPymnTypeCd` | `SCDUL_PYMN_TYPE_CD` | `VARCHAR(2)` | always `'03'` for Internal Transfer |
| `outBankCd` | `OUT_BANK_CD` | `VARCHAR(15)` | withdrawal bank (normalised NAPAS code) |
| `wdrlActNo` | `WDRL_ACT_NO` | `VARCHAR(100)` | withdrawal account |
| `ccyCd` / `wdrlCcyCd` | `CCY_CD` / `WDRL_CCY_CD` | `VARCHAR(3)` | withdrawal / transfer currency |
| `titl` | `TITL` | `VARCHAR(100)` | batch title |
| `totalTranAmt` | `TOTAL_TRAN_AMT` | `NUMERIC(20,6)` | Σ line amounts |
| `totTrnsCnt` | `TOT_TRNS_CNT` | `NUMERIC(10,0)` | line count |
| `apprSeqNo` | `APPR_SEQ_NO` | — | null until request-approval; FK→`EXPT_RESOL_MSTR` |
| `trnfYn` / `trnfDtm` | `TRNF_YN` / `TRNF_DTM` | `CHAR(1)` / `VARCHAR(14)` | `'N'` draft → `'Y'` after dispatch |
| `orgFileNm` / `saveFileNm` / `fileSize` | `ORG_FILE_NM` / `SAVE_FILE_NM` / `FILE_SIZE` | `VARCHAR(500)` / `VARCHAR(500)` / `VARCHAR(100)` | 3 separate file columns (no `FULL_PATH`) |
| audit (`regId/regDtm/modId/modDtm`) | `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM` | `VARCHAR(14)` | legacy `yyyyMMddHHmmss` string shape (not `BaseEntity` `OffsetDateTime`) |

> **No `APPR_STTS` / `TRNF_STTS` / `DRAFT_USER_NM` / `DRAFT_USER_ID` columns on `SCDUL_PYMN_SUMR`** — these are computed/joined from `EXPT_RESOL_MSTR` + `USER_INFM` on read.

### `ScdulPymnSumrDetl` → `SCDUL_PYMN_SUMR_DETL`

PK `(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)`. Key columns: `FINC_MDA_NO` (recipient account), `TAR_BANK_CD`, `ANT_DIV_CD` (`VARCHAR(5)` — N/NAPAS/INTER/S/SWIFT/C/CITAD), `TRAN_AMT` (`NUMERIC(20,6)`), `TRNF_CCY_CD`/`DEPO_CCY_CD`, `RCPT_NM`, `TRAN_USAG`, `NOTE` (`VARCHAR(200)`, not 255), `DOC_NO`, `CHART_ACCT_SEQ_NO`/`CHART_ACCT_CD` (`VARCHAR(20)`), dispatch link columns `PAY_TRAN_DT`/`PAY_TRAN_SEQ` (API path) + `PAY_FILE_SEQ_NO`/`PAY_SEQ` (file path), banking-match read fields `LINK_MATCH_YN`/`ACCT_SEQ_NO`/`BANK_TRNS_FULL_ID`.

### `ExptResolFile` → `EXPT_RESOL_FILE` / `ExptResolPayEvidFile` → `EXPT_RESOL_PAY_EVID_FILE`

Approval-request attachment rows. The two are UNION-ed for the combined attachment list (GAP-ATT-1). `FILE_ID` via the `wb_get_next_val` funnel per `(biz_key, appr_seq_no)`.

---

## 3. Amount precision

`SCDUL_PYMN_SUMR.TOTAL_TRAN_AMT` and `SCDUL_PYMN_SUMR_DETL.TRAN_AMT` are `NUMERIC(20,6)` (6 decimals). On dispatch the executor rounds **6→2** into `TB_PAY_TARGET` / `TB_PAY_FILE_DATA` `TRAN_AMT` which are `NUMERIC(23,2)`.

## 4. Dispatch PK column order

`tran_dt` is first (not `biz_key`): `TB_PAY_TARGET(tran_dt, biz_key, tran_seq)`, `TB_PAY_FILE_MST(tran_dt, biz_key, file_seq_no)`, `TB_PAY_FILE_DATA(tran_dt, biz_key, file_seq_no, seq)`.

## 5. Conn / linkage

Only ONE conn table exists in DEV: `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` (note `SCDU_`). File-path linkage is encoded directly on `SCDUL_PYMN_SUMR_DETL.PAY_FILE_SEQ_NO`/`PAY_SEQ` + `SCDUL_PYMN_SUMR.PAY_FILE_SEQ_NO` (no second `SCDUL_PYMN_PAY_FILE_MST_CONN` table).

## 6. Result-detail join strategy

- **API-path lines:** JOIN via `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN(tran_dt, tran_seq)` → `TB_PAY_TARGET`.
- **File-path lines:** JOIN via `SCDUL_PYMN_SUMR_DETL.PAY_FILE_SEQ_NO + PAY_SEQ` → `TB_PAY_FILE_DATA`.

`DispatchResultResolver` hides API-vs-File behind one façade.

## 7. Key-gen branches

See [business-flow.md §5](business-flow.md). Funnel-only via `wb_get_next_val` (`project_wb_get_next_val_funnel.md`): `scdul_pymn_no`, `scdul_pymn_detl_no`, `sq_appr_seq_no` (global), `expt_resol_file.file_id`.

## 8. Relevant migrations

Schema is external / pre-migrated (`project_flyway_disabled_ddl_validate.md`) — no Flyway run. Entity mappings track the V85 column set (`ScdulPymnSumr` Javadoc). View Setting seeds for the screen ids live in the `V156__sys_scr_option_wecms.sql` manual DBA script family.

## 9. Cross-links

[business-flow.md](business-flow.md) · [interface-specification.md](interface-specification.md) · [functional-specification.md](functional-specification.md) · [source-code-index.md](source-code-index.md)

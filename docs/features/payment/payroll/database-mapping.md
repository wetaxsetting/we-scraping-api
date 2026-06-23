# Payroll Payment — Database Mapping

> Backend feature doc. Hub: [README.md](README.md). Legacy JEX DDL is the schema source of truth; Payroll shares legacy tables with the other batch-payment families (no Payroll-only table).

## Tables touched

| Table | Role | Inventory |
|---|---|---|
| `SCDUL_PYMN_SUMR` | Batch master (shared; `SCDUL_PYMN_TYPE_CD="00"` = Payroll) | legacy |
| `SCDUL_PYMN_SUMR_DETL` | Per-payee line | legacy |
| `EXPT_RESOL_MSTR` | Approval master — `DOC_NO`, `TITL`, `DRAFT_USER_NM`, **`APPR_FUND_TP`** (Payroll Type), `APPR_TYPE_CD` (status source) | legacy |
| `EXPT_RESOL_APPRVR_INFM` | Approver chain | legacy |
| `EXPT_RESOL_DETL` | Resolution detail (amount/ccy) | legacy |
| `EXPT_RESOL_FILE` | Request-approval attachments | legacy |
| `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA` | Dispatch file-route targets (Payroll never writes `TB_PAY_TARGET`) | legacy |
| `ERP_TRNS_HSTR` | ERP transaction history (upload/delete/download audit) | legacy |
| `AUDIT_LOGS_INFM` | Audit (`WSVC_ID="payroll-payment"`) | legacy |
| `BLTB_*` / `NOTI_*` / `USER_NEW_POST_INFM` / `COMNT_INFM` | Approval notification fan-out | legacy |

## Entity ↔ table ↔ column (key columns)

### `ScdulPymnSumr` → `SCDUL_PYMN_SUMR`

| Entity field | Column | Notes |
|---|---|---|
| `bizKey` + `scdulPymnNo` | `BIZ_KEY` + `SCDUL_PYMN_NO` | composite PK (`ScdulPymnSumrId`) |
| `scdulPymnTypeCd` | `SCDUL_PYMN_TYPE_CD` | `"00"` for Payroll |
| `outBankCd` / `wdrlActNo` / `ccyCd` / `wdrlCcyCd` | `OUT_BANK_CD` / `WDRL_ACT_NO` / `CCY_CD` / `WDRL_CCY_CD` | withdrawal account |
| `titl` | `TITL` | batch title |
| `totTrnsCnt` / `totalTranAmt` | `TOT_TRNS_CNT` / `TOTAL_TRAN_AMT` | counts/totals |
| `apprSeqNo` | `APPR_SEQ_NO` | FK → `EXPT_RESOL_MSTR.APPR_SEQ_NO`; null until requested |
| `trnfYn` / `trnfDtm` | `TRNF_YN` / `TRNF_DTM` | dispatch marker |
| `orgFileNm` / `saveFileNm` / `fileSize` | `ORG_FILE_NM` / `SAVE_FILE_NM` / `FILE_SIZE` | uploaded Excel |
| `linkMatchYn` / `acctSeqNo` / `bankTrnsShortId` / `bankTrnsFullId` | `LINK_MATCH_YN` / `ACCT_SEQ_NO` / `BANK_TRNS_SHORT_ID` / `BANK_TRNS_FULL_ID` | banking-match (Payroll populates these) |
| `erpFileNum` | `ERP_FILE_NUM` | legacy varchar(8) ERP file seq — **not** overloaded with `DOC_NO` |
| — | `APPR_STTS` | **does not exist** — status derived via `wb_get_appr_stts` |

### `ScdulPymnSumrDetl` → `SCDUL_PYMN_SUMR_DETL`

PK `(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)` (`ScdulPymnSumrDetlId`). Key columns: `TRAN_AMT`, `RCPT_NM`, `TAR_BANK_CD`, `FINC_MDA_NO`, `TRAN_USAG`, `ANT_DIV_CD`, `CHART_ACCT_CD` / `CHART_ACCT_SEQ_NO` (Reporting Account), `TRCO_KEY`, `DOC_NO`, `NOTE`, `DEPO_CCY_CD`, `TRNF_CCY_CD`.

### `ExptResolMstr` → `EXPT_RESOL_MSTR` (Payroll-relevant)

`APPR_SEQ_NO` (PK with `BIZ_KEY`), `APPR_DV="21"` (Payroll), `APPR_STS`/`APPR_TYPE_CD` (status), `DOC_NO`, `TITL`, `DRAFT_USER_*`, **`APPR_FUND_TP`** (`"0"`=Salary / `"1"`=Bonus / `"9"`=Other), `PAY_DATE`, `DEL_YN`, display flags.

## Key generation (all via `wb_get_next_val`)

| Key | Source |
|---|---|
| `SCDUL_PYMN_NO` | `InternalTransferKeyGenerator.nextScdulPymnNo` (one-shot retry on MAX+1 race) |
| `SCDUL_PYMN_DETL_NO` | `legacyFunctions.nextScdulPymnDetlNo` (seed once per doc, increment in Java) |
| `APPR_SEQ_NO` | `legacyFunctions.nextApprSeqNo` (global `sq_appr_seq_no`) |
| `DOC_NO` | `DocNoGenerator.nextDocNo` |

## Migrations (DBA-run, not Flyway-applied)

| Migration | Effect |
|---|---|
| `V144__payroll_payment_view_setting_seed.sql` | View Setting seed for the 4 Payroll screens |
| `V146__scdul_pymn_sumr_chk01_allow_payroll.sql` | Relax `scdul_pymn_sumr_chk01` CHECK to admit `'00'` (re-assert if the CHECK is ever re-touched) |
| `V232__payroll_payment_scheduled_view_setting_legacy.sql` | Scheduled list → legacy `pymn_0005_001` column parity + Payroll Type column |

> No Payroll-only table is introduced — every object above is in the legacy inventory. Status is never persisted; it is computed from `EXPT_RESOL_MSTR` via the legacy DB function.

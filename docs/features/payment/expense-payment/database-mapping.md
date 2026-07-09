# Expense Payment — Database Mapping (API)

> **Backend doc.** Source of truth = legacy JEX DDL (per workspace CLAUDE.md) + entity mappings under `domain/payment/**`. Verified 2026-06-23.
> Tables below are all on the [legacy-table-inventory](../../../references/database/legacy-table-inventory.md) baseline; Expense creates **no new table** — it shares the physical tables with Internal Transfer / Vendor / Payroll.

## Tables touched

| Table | Role | Entity |
|---|---|---|
| `SCDUL_PYMN_SUMR` | Batch master (shared across Internal/Vendor/Expense/Payroll, isolated by `SCDUL_PYMN_TYPE_CD`) | `ScdulPymnSumr` (`internaltransfer.entity`) |
| `SCDUL_PYMN_SUMR_DETL` | Per-line transactions | `ScdulPymnSumrDetl` (+ `ScdulPymnSumrDetlId`) |
| `EXPT_RESOL_MSTR` | Approval master (`DOC_NO`, `TITL`, `APPR_TYPE_CD`, `DRAFT_USER_NM`, `APPR_DV`) | `ExptResolMstr` (`approvalbox.entity`) |
| `EXPT_RESOL_APPRVR_INFM` | Approver chain | `ExptResolApprvrInfm` |
| `EXPT_RESOL_FILE` / `EXPT_RESOL_PAY_EVID_FILE` | Approval / payment-evidence attachments | shared approval entities |
| `TB_PAY_TARGET` | Dispatch target rows — **VND** path | `TbPayTarget` (+ `TbPayTargetId`) (`dispatch.entity`) |
| `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA` | Dispatch file master + data — **non-VND** path | `TbPayFileMst` / `TbPayFileData` (+ `*Id`) |
| `TB_FIRM_CONF` | Firm-banking config — real `BANK_PRTN_ID` source | `TbFirmConf` (+ `TbFirmConfId`) (`firmbankingsettings.entity`) |
| `ERP_TRNS_HISTORY` | ERP audit (type `03` scheduled, `05` result) | written via `ErpTrnsHistoryWriter` |
| `AUDIT_LOGS_INFM` | Mutating-action audit (`WSVC_ID = "expense-payment"`) | written via `AuditLogService` |

## Discriminator & isolation

`SCDUL_PYMN_SUMR.SCDUL_PYMN_TYPE_CD`:

| Value | Module |
|---|---|
| `"00"` | Payroll |
| `"02"` | **Expense** (`ScdulPymnTypeCd.EXPENSE.code()`) |
| `"03"` | Internal Transfer |
| (Vendor) | per Vendor module |

Every `ExpensePaymentService` / `ExpensePaymentResultService` repository call passes `ScdulPymnTypeCd.EXPENSE.code()`, so the modules never see each other's rows.

## Key columns — `SCDUL_PYMN_SUMR` (Expense usage)

| Column | Meaning | Notes |
|---|---|---|
| `SCDUL_PYMN_NO` | Batch PK (per-tenant) | generated via `InternalTransferKeyGenerator.nextScdulPymnNo` |
| `BIZ_KEY` | Tenant | JWT-bound `TenantContext.currentBizKey()` |
| `SCDUL_PYMN_TYPE_CD` | Discriminator | `"02"` |
| `APPR_SEQ_NO` | FK → `EXPT_RESOL_MSTR.APPR_SEQ_NO` | set at request-approval; null on draft |
| `OUT_BANK_CD` / `WDRL_ACT_NO` / `WDRL_CCY_CD` | Withdrawal source | drives `TB_FIRM_CONF` lookup |
| `CCY_CD` | Batch currency | footer totals grouped by this |
| `TOTAL_TRAN_AMT` / `TOT_TRNS_CNT` | Totals | |
| `TRNF_YN` / `TRNF_DTM` | Dispatch marker / datetime | stamped `Y` on dispatch |
| `ORG_FILE_NM` / `FILE_SIZE` | Original Excel | drives `GET /{id}/file` |
| `TITL` | Upload subject (File-Name source) | distinct from `EXPT_RESOL_MSTR.TITL` |
| — | **No `APPR_STTS` column** | status derived via `wb_get_appr_stts` |

## Key generation (funnel — all through `wb_get_next_val`)

| Key | Generator | `wb_get_next_val` arg |
|---|---|---|
| `SCDUL_PYMN_NO` | `InternalTransferKeyGenerator.nextScdulPymnNo` (reused verbatim — shared physical table) | per-tenant `MAX+1` |
| `SCDUL_PYMN_DETL_NO` | detail numbering in `createDraft` | `('scdul_pymn_sumr_detl','scdul_pymn_detl_no')` (V140 #38) |
| `APPR_SEQ_NO` | `requestApproval` | global `('sq_appr_seq_no')` (V140 #1 — shared across all Payment modules) |
| `TB_PAY_FILE_DATA` seq | `ExpenseDispatchExecutor` | `legacyFunctions.nextTbPayFileDataSeq` |

Per `project_wb_get_next_val_funnel.md` — single-executor rule; no Hibernate sequence generators.

## PK / FK summary

- `SCDUL_PYMN_SUMR` PK = `(BIZ_KEY, SCDUL_PYMN_NO)` (+ type discriminator on every query).
- `SCDUL_PYMN_SUMR_DETL` PK = `(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)`.
- `SCDUL_PYMN_SUMR.APPR_SEQ_NO` → `EXPT_RESOL_MSTR.APPR_SEQ_NO` (resolves `DOC_NO` / `TITL` / `DRAFT_USER_NM`).
- `TB_FIRM_CONF` PK = `(BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)` (`TbFirmConfId`).

## Migrations / seeds

- **No DDL** is owned by Expense (all tables pre-exist in legacy / inventory).
- `V143__expense_payment_view_setting_seed.sql` — View Setting column seed for the 4 Expense screen ids.
- Key-gen sequences seeded via the shared `wb_get_next_val` funnel migrations (V140, V155).

---

Last verified: 2026-06-23 (reconciled against latest code). See [business-flow.md](./business-flow.md) for how each table is written.

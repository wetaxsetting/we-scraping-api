# Overseas Payment — Migration Guide

> Legacy JEX overseas WSVC → Spring controller/service mapping, approved deviations, dead legacy. Hub: [README.md](README.md). Verified 2026-06-23.

## WSVC → controller/service map

| Legacy (JEX) | Type | → Refactor |
|---|---|---|
| `pymn_0007_001_r001_act.jsp` (+ `getDynamicOvrs()`; `IDO.OVRS_PYMN_SUMR_R003` totals) | Action JSP + IDO | `OverseasPaymentService.list` (`GET /scheduled`) + `OverseasPaymentRepository.findScheduled/countScheduled/sumByCurrency` |
| `pymn_0007_001` approval-dialog load | Action JSP + IDO | `getDetail` (`GET /scheduled/{id}/details`) |
| `pymn_0007_001_u001` | Action JSP | `saveReportingAccount` (`PATCH …/reporting-account`) |
| `pymn_0007_001_c001_act.jsp:100-456` (type `S` dispatch) | Action JSP + helper | `dispatch` + `OverseasDispatchExecutor` + `CutoffValidator` |
| `pymn_0007_001` batch-upload parse / save | Action JSP + `PymnHelper.importDataFromExcel*` | `OverseasPaymentExcelService.preview` / `submit` (`POST /excel-preview`, `/excel-upload`) |
| `comm_0194_001 → comm_0192_001_c005` (`OVRS_PYMN_TYPE='S'`) | Approval submit | `requestApproval` (`POST /{id}/request-approval`, multipart) |
| `comm_0192_001_c005` (`OVRS_PYMN_TYPE='M'`, `createExptResolDetlForOverseas` + `updateOvrsPymnMstrData`) | Approval submit (multi) | `requestApprovalMulti` (`POST /request-approval-multi`) |
| `comm_0195_001` edit-mode | Approval Details popup | `changeApprovalLine` (`PATCH /{id}/approval-line`) |
| `comm_0195_001` footer Approve/Reject → `bltb_0001_003_u001` | Approval Details popup | `actOnApproval` (`POST /{id}/approval-action`) |
| `comm_0192_004_d001` | Approval cleanup | `removeApprovalLine` (`DELETE /{id}/approval-line`) |
| `pymn_0008_001_r001_act.jsp` (`getDynamicStatusFilter`; `IDO.OVRS_PYMN_SUMR_R005`/`_R005_CNT`) | Action JSP + IDO | `listResult` (`GET /result`) + `OverseasPaymentRepository.findResult/countResult/sumResultByCurrency` |
| `pymn_0008_002_r003` (Result export 'W' audit) | Action JSP | `exportResult` (`GET /result/export`) + `OverseasPaymentResultExcelWriter` + `ErpTrnsHistoryWriter` |
| `comm_1001_001` | WSVC | `listWithdrawalAccounts` (`GET /withdrawal-accounts`) → shared `WithdrawalAccountListService` |
| `R006`/`R007` (M-mode header + line) | IDO | `resolveLegacyMmodeOrThrow` + `findMmodeDetailRows` |

## Approved deviations

- **Wire format** lowercase snake_case (`project_api_naming_lowercase.md`); DB column names unchanged.
- **Slug identifiers** — refactor uses kebab-case slugs (`overseas-payment`, screen IDs, authz `payment-overseas-*`) not legacy WSVC ids; legacy ids kept only as file:line citations.
- **No `APPR_STTS` column** — status derived from `EXPT_RESOL_MSTR.APPR_TYPE_CD` (`project_scdul_pymn_sumr_appr_stts_remap.md`); removed `updateApprStts`/`writeBackApprStts`.
- **Key generation** — `OVRS_PYMN_NO`/`APPR_SEQ_NO`/`DOC_NO` all funnel through `wb_get_next_val` (no Hibernate sequence/identity); date-prefixed `OVRS_PYMN_NO` counter-table approach retired (now plain MAX+1).
- **Per-tenant Excel storage** — files now land under `<storage-root>/<biz_key>/overseas-payment/`; `resolveForRead` falls back to the legacy flat root for backward-compat.
- **Single controller, `/result` singular** — one `OverseasPaymentController` (not a scheduled/result split, not `/results`).
- **Result Detail reuses `GET /scheduled/{id}/details`** — no separate result-detail read endpoint; the detail DTO carries per-line `proc_stat`/result fields.
- **M-mode detail fully rendered** — `resolveLegacyMmodeOrThrow` returns real remittance line(s) with `legacy_mmode=false` instead of throwing `WBE2045` (which crashed the popup for ~57% of DEV overseas approvals).

## Dead legacy
- No re-run port (consistent with the SCDUL family — Internal Transfer `pymn_0010_002.ts` re-run was never ported; overseas has no re-run surface).
- Date-prefixed `OVRS_PYMN_NO` generator + `overseasPaymentClock` bean — removed (V140 #10 / key-gen migration).

## Migration safety
No DDL changes to `OVRS_PYMN_*`. Entities map column-for-column to the legacy JEX DDL; status/key-gen are derived/funnelled so refactor rows are byte-compatible with live JEX data. Run against the live JEX DB in all envs per `project_uat_prod_live_jex_db_target.md`.

## Cross-links
- [source-code-index.md](source-code-index.md) · [refactor-gap-analysis.md](refactor-gap-analysis.md) · [database-mapping.md](database-mapping.md)

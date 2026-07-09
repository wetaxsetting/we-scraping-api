# Internal Transfer — Business Flow (Backend)

> **WEB counterpart:** [wecms-web/.../internal-transfer/screen-flow.md](../../../../../wecms-web/docs/features/payment/internal-transfer/screen-flow.md) · **Hub:** [README.md](README.md)

Source-code-verified 2026-06-23. End-to-end business flow for both sub-modules — **Scheduled Transfer** (`pymn_0009_*`) and **Transfer Result** (`pymn_0010_*`) — across the three layers (Legacy JEX / Spring / React). This is the consolidated narrative; the four per-flow docs carry the granular field-level detail and are cross-linked at each step:

| Per-flow doc | Covers |
|---|---|
| [upload-create-flow.md](upload-create-flow.md) | Flow 1 — Excel parse → preview → save draft |
| [scheduled-transfer-flow.md](scheduled-transfer-flow.md) | Flow 2 — list / detail / edit / delete / dispatch |
| [transfer-result-flow.md](transfer-result-flow.md) | Flow 3 — result list / per-line detail / export |
| [approval-flow.md](approval-flow.md) | Flow 4 — request / approve / reject / withdraw / change-line |

---

## 1. Layer responsibilities

| Concern | Legacy JEX | Spring (`wecms-api`) | React (`wecms-web`) |
|---|---|---|---|
| Excel parse + per-cell validation | `pymn_0009_002_r001` + `PymnHelper.importDataFromExcelForInternalTransfer` | `InternalTransferExcelParser` (real Apache POI) | `<ExcelUploadDialog>` preview with red error cells |
| Save draft | `pymn_0009_002_c001` + `PymnHelper.uploadPaymentExcel` | `InternalTransferService.createDraft` | `InternalTransferUploadDialog` |
| Scheduled list / filter | `pymn_0009_001_r001` + `PymnHelper.pymn_0001_001_r001("03")` | `ScdulPymnSumrRepository.search` | `<PaymentScheduledBatchListPage>` |
| Scheduled detail | `pymn_0009_003_r001` (IDOs `SCDUL_PYMN_SUMR_R012`/`_R013`) | `InternalTransferService.detail` | `<PaymentDetailPage>` |
| Edit reporting account | `pymn_0009_002_u002` | `updateReportingAccount` | line "Reporting Account" picker |
| Delete draft | `pymn_0009_001_d001` | `delete` | per-row Delete chip |
| Request approval | `comm_0194_001` → `comm_0192_001_c005` | `requestApproval` | `<RequestApprovalDialog>` |
| Approve / reject / withdraw / change-line | `comm_0195_001` → `bltb_0001_003_u001` | `actOnApproval` / `withdrawApproval` / `changeApprovalLine` → `ApprovalBoxService` / `ApprovalBoxFanoutService` | `<ApprovalDetailsDialog>` |
| Dispatch ("Transfer") | `pymn_0009_001_c001` + `validateTransferTiming` | `InternalTransferDispatchService` + `InternalTransferDispatchExecutor` + `CutoffValidator` | per-row Transfer chip |
| Result list / detail | `pymn_0010_001` / `pymn_0010_002` (IDO `SCDUL_PYMN_SUMR_R006`) | `InternalTransferResultService` + `DispatchResultResolver` | `<PaymentResultBatchListPage>` / `<ResultDetailView>` |
| Result export | client-side XLSX | `InternalTransferResultExportProvider` (shared `ExcelExportService`) | `useExcelExport` |

---

## 2. Scheduled Transfer sub-module — end-to-end

### 2.1 Upload / create (Flow 1)

```
Scheduled Transfer list → "Excel Upload"
  → GET /withdrawal-accounts        (firm-banking-capable accounts; legacy comm_1001_001)
  → POST /uploads/parse             (multipart file_data + bank_cd → InternalTransferExcelParser)
       returns ParseExcelResponse(totCnt, sucCnt, errCnt, lines[] with err_fld / err_fld_caus)
  → preview with per-cell red highlights
  → POST /  (multipart request JSON + file_data)
       InternalTransferService.createDraft:
         - mint SCDUL_PYMN_NO via the wb_get_next_val funnel
         - insert SCDUL_PYMN_SUMR (SCDUL_PYMN_TYPE_CD='03', TRNF_YN='N')
         - one SCDUL_PYMN_SUMR_DETL per line
         - store Excel on disk + copy to ERP_TRNS_HSTR (TRNS_TYPE_CD='16')
         - AUDIT_LOGS_INFM (WSVC_ID='internal-transfer')
  → list re-fetches; new draft row (no APPR_SEQ_NO, TRNF_YN='N')
```

Detail + field-level rules: [upload-create-flow.md](upload-create-flow.md).

### 2.2 Edit reporting account (Flow 2)

`PATCH /{id}/lines/{lineId}/reporting-account` → `updateReportingAccount`: loads master (`WBE2060`), rejects if `TRNF_YN='Y'` (`WBE2064`), loads line (`WBE2061`), sets `CHART_ACCT_SEQ_NO`/`CHART_ACCT_CD`. (`CHART_ACCT_INFM` existence re-check deferred — see open gaps.)

### 2.3 Request approval (Flow 4)

`POST /{id}/request-approval` (multipart `request` + zero-or-more `attachments`) → `requestApproval`:
- Idempotency: `APPR_SEQ_NO` already set → `WBE2062`.
- Allocate `APPR_SEQ_NO` via the **global** `wb_get_next_val('sq_appr_seq_no')` funnel (shared across all Payment modules).
- Insert `EXPT_RESOL_MSTR` (`APPR_DV='35'` Internal Transfer, `APPR_STS='2'`, `CONT`, `PAY_DATE` = Scheduled Date, `DOC_NO` = `VO_YYMMDD_NNN`, amount split into `TOT_PAY_{VND,USD,KHR}_AMT`, `APPR_TYPE_CD='00'`).
- Insert one `EXPT_RESOL_APPRVR_INFM` per approver (line 1 `P` active, rest `W`).
- `EXPT_RESOL_DETL` + `EXPT_RESOL_APPR_LINE` snapshot; stream attachments → `EXPT_RESOL_FILE`; BLTB_* notification fan-out.
- Stamp `SCDUL_PYMN_SUMR.APPR_SEQ_NO` only (no `APPR_STTS` column).

Detail: [approval-flow.md](approval-flow.md).

### 2.4 Approve / reject / withdraw / change-line (Flow 4)

- **Approve / Reject** — `POST /{id}/approval-action` `{action:"APPROVE"|"REJECT"}` → `actOnApproval`. Guards: no `APPR_SEQ_NO` → `WBE2126`; already final (derived status ∈ {C,R}) → `WBE2127`. Delegates the chain to `ApprovalBoxService.approve/reject` (advances `EXPT_RESOL_APPRVR_INFM`, sets `EXPT_RESOL_MSTR.APPR_TYPE_CD`). No write-back to `SCDUL_PYMN_SUMR`.
- **Withdraw** — `DELETE /{id}/approval` → `withdrawApproval` → `ApprovalBoxFanoutService` deletes the `EXPT_RESOL_*` rows, then clears `SCDUL_PYMN_SUMR.APPR_SEQ_NO` → batch returns to Draft.
- **Change approval line** — `PUT /{id}/approval-line` `{appr_line_no}` → `changeApprovalLine` → rebuilds the chain, resets master to Pending.

### 2.5 Dispatch / Transfer (Flow 2)

```
Approved batch (APPR_SEQ_NO set, TRNF_YN='N') → "Transfer"
  → POST /{id}/dispatch  { trnf_ccy_cd }
       InternalTransferDispatchService.dispatch:
         - load master (WBE2060); reject if TRNF_YN='Y' (WBE2064); reject if no APPR_SEQ_NO (WBE2063)
         - CutoffValidator.validate(outBankCd, network)  (NAPAS / CITAD / SWIFT cut-off gate)
         - InternalTransferDispatchExecutor.execute(master, lines, trnfCcyCd):
              VND / API path  → one TB_PAY_TARGET per line + stamp PAY_TRAN_DT/PAY_TRAN_SEQ
              non-VND / File path → TB_PAY_FILE_MST + N TB_PAY_FILE_DATA + stamp PAY_FILE_SEQ_NO
         - stamp TRNF_YN='Y', TRNF_DTM=now; AUDIT_LOGS_INFM dispatch row
```

Routing rule (legacy `PymnHelper.pymn_0009_001_c001:2419-2790`): API path when `(TRNF_CCY_CD='VND' && !isShinhanUsdToVnd) || isWooriUsdToVnd`; else File path. R&D note: no local bank daemon flips `PROC_STAT` after the insert.

### 2.6 Delete draft (Flow 2)

`DELETE /{id}` → `delete`: load master (`WBE2060`); **reject if `APPR_SEQ_NO` set** (`WBE2062` — batch in/after approval); delete lines then master; best-effort ERP-history "deleted" row.

---

## 3. Transfer Result sub-module — end-to-end

Read-only (`R`-flag-only per the JEX permission matrix). GET endpoints + an export only.

```
Transfer Result list
  → GET /results?from_date=&to_date=&srch_wd=&trnf_stts=
       InternalTransferResultService.list → ScdulPymnSumrRepository.searchDispatched
         - hard-filtered TRNF_YN='Y'  (only dispatched batches)
         - date window constrains TRNF_DTM (NOT REG_DTM)
         - trnf_stts forwarded as procStts: 'C' Completed / 'P' In Progress (route-aware; unknown/blank → no filter)
         - default sort TRNF_DTM DESC, SCDUL_PYMN_NO DESC
         - per-batch (succ, err) counts via DispatchCountRepository
  → click row → GET /results/{id}
       detail → DispatchResultResolver.resolve(master, lines):
         per-line PROC_STAT / RS_CD / RS_MSG / PROC_DTTM / RSLT_DTTM from TB_PAY_*/TB_FIRM_TRAN
         header tab counts [success, failed, inProgress] via countByStatus
  → POST /results/export  (shared ExcelExportService + InternalTransferResultExportProvider)
```

Detail: [transfer-result-flow.md](transfer-result-flow.md).

---

## 4. Status derivation (no stored APPR_STTS column)

`SCDUL_PYMN_SUMR` has **no `APPR_STTS` column** in the legacy JEX schema. The refactor never stores a denormalized approval status. Per-batch approval status is **derived on read** by calling the legacy DB function `wb_get_appr_stts(...)` over `EXPT_RESOL_MSTR.APPR_TYPE_CD` (joined by `(BIZ_KEY, APPR_SEQ_NO)`) via `LegacyFunctionRepository`:

| `EXPT_RESOL_MSTR.APPR_TYPE_CD` | Derived status | Note |
|---|---|---|
| (null / no APPR_SEQ_NO) | Draft | not yet requested |
| `00` | `P` Pending | |
| `01` | `C` Approved | |
| `02` | `R` Rejected | **legacy code R, not X** |
| `03` | `I` In Progress | |

The Scheduled list `appr_type` filter maps `00`=Not-Requested · `01`=Approved (derived `C` OR `TRNF_YN='Y'`) · `02`=Rejected (`R`) · `03`=In-Progress (`I`) · `04`=Pending (`P`). The "already final" guard in `actOnApproval` uses the `C`/`R` constants.

---

## 5. Key generation (wb_get_next_val funnel)

All key generation funnels through `LegacyFunctionRepository` → the legacy DB function `wb_get_next_val` (single-executor rule, `project_wb_get_next_val_funnel.md`):

| Key | Funnel call | Format |
|---|---|---|
| `SCDUL_PYMN_NO` | `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no',biz_key)` via `InternalTransferKeyGenerator` | per-tenant plain int (column `VARCHAR(10)`) |
| `SCDUL_PYMN_DETL_NO` | `wb_get_next_val('scdul_pymn_sumr_detl','scdul_pymn_detl_no',biz_key)` seeded once + incremented in Java across the batch | |
| `APPR_SEQ_NO` | `wb_get_next_val('sq_appr_seq_no')` (global, shared across Payment modules) | |
| `EXPT_RESOL_FILE.FILE_ID` | `wb_get_next_val` per `(biz_key, appr_seq_no)` MAX+1 + incremented in Java | |
| `DOC_NO` | `DocNoGenerator.nextDocNo` | `VO_YYMMDD_NNN` |

The earlier refactor-only date-prefixed `<yyyyMMdd><6-seq>` counter-table key was **removed 2026-06-09 (OD-2 reversal)** — the refactor now mints the same value the live JEX DB would. `createDraft` retries once on `DataIntegrityViolationException` (the funnel `MAX+1` has no row lock).

---

## 6. Re-run = DEAD LEGACY

Legacy `pymn_0010_002.ts:441-472` had a per-line "Re-run" affordance. It was **never ported** — no backend endpoint/service/error-code, no frontend client. Re-run was deprecated cross-module by the PO (`project_payment_result_sit_ready.md`). The controller carries only a placeholder comment block. See [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 7. Error codes (this feature)

| Code | Where | Meaning |
|---|---|---|
| `WBE2060` | detail / dispatch / delete / update / result detail | batch missing or not in tenant |
| `WBE2061` | updateReportingAccount | line missing |
| `WBE2062` | requestApproval / delete | already requested (`APPR_SEQ_NO` set) — blocks re-request + delete |
| `WBE2063` | dispatch | not approved (no `APPR_SEQ_NO`) |
| `WBE2064` | dispatch / updateReportingAccount | already transferred (`TRNF_YN='Y'`) |
| `WBE2069` | approval attachment download | attachment row/file missing |
| `WBE2126` | approval-action / withdraw / change-line | no `APPR_SEQ_NO` |
| `WBE2127` | approval-action | already final (derived status ∈ {C,R}) |
| `WBE2018` / `WBE2017` | parse | header mismatch / IO-format error |
| `WBE2120` / `WBE2121` | parse | mixed currency / mixed network |
| `WBE2070` / `WBE2015` | create / file download | file-write failure / file missing behind batch |

---

## 8. Cross-links

- Database: [database-mapping.md](database-mapping.md) · Functional spec: [functional-specification.md](functional-specification.md) · Endpoints: [interface-specification.md](interface-specification.md)
- Migration: [migration-guide.md](migration-guide.md) · Gaps: [refactor-gap-analysis.md](refactor-gap-analysis.md) · Code map: [source-code-index.md](source-code-index.md) · Tests: [test-scenarios.md](test-scenarios.md)

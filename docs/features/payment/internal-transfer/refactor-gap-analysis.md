# Internal Transfer — Refactor Gap Analysis

Source-code-evidence only. Each row is decided by reading the actual legacy JEX artifacts under `WECMS DEV/` and the refactor code under `wecms-api`/`wecms-web`. No inference from prior documentation.

---

## Focus question (2026-06-08): "Re-submit after reject"

**Did legacy Internal Transfer let a user re-submit a transfer after it was rejected?**

### Legacy artifacts traced

| Artifact | Path | What it shows |
|---|---|---|
| Approval Details popup view | `WECMS/web/view/jex/wabooks/comm/comm_0195_001_view.jsp` | The approve/reject/edit/delete UI host. |
| Approval Details popup TS | `WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0195_001.ts` | Approve = `updateApproval("A")`, Reject = `updateApproval("R")` → `bltb_0001_003_u001`. Edit = `editApproval` → `comm_0192_013_u002`. Delete = `deleteApproval`. **No `resubmit` / `re-request` / `reApprove` function exists** (verified by grep across the comm/pymn/bltb TS + action trees — zero matches). |
| Approve/Reject backend | `WECMS/web/WEB-INF/action/jex/wabooks/bltb/bltb_0001_003_u001_act.jsp` | The reject mutation. |
| Request-Approval list TS | `WECMS_STATIC/ts-src/js/jex/wabooks/pymn/pymn_0009_001.ts` | Gates the "Request Approval" + Delete affordances. |
| Request-Approval submit | `WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_001_c005_act.jsp` (entry `comm_0194_001`) | Approval submission. Grep for reject/resubmit/APPR_SEQ_NO re-entry → no match in `comm_0194_001.ts`. |
| Delete-draft backend | `WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_d001_act.jsp` | Delete blocked once `APPR_SEQ_NO` is set (baseline §3 / §5.5). |

### Evidence — what reject actually does (`bltb_0001_003_u001_act.jsp`)

1. **Other approvers suspended** — `bltb_0001_003_u001_act.jsp:236-248`: on `ApprvrStts.REJECT`, every `EXPT_RESOL_APPRVR_INFM` row in the chain is set to `ApprvrStts.SUSPENDED`.
2. **Master moved to terminal REJECT** — `:319-320` / `:370`: `EXPT_RESOL_MSTR.APPR_TYPE_CD = ApprTypeCd.REJECT` (`02`).
3. **Resolution finalised (closed)** — `:272-276`: `PAY_END_YN = YES`, `PAY_END_DTM`, `PAY_END_NM` stamped. The approval document is closed, not reopened.
4. **A REJECT comment is written** — `:405-406`: `BltbComntInfm` with `ComntTaskCd.REJECT`.
5. **The source batch is NOT reset** — the reject branch never touches `SCDUL_PYMN_SUMR`: it does **not** clear `APPR_SEQ_NO`, does **not** reset `APPR_STTS`, does **not** delete the `EXPT_RESOL_MSTR` row. The batch keeps pointing at the now-rejected resolution.

### Evidence — the rejected batch cannot re-enter approval

- **Popup hides Edit + Delete on a rejected document** — `comm_0195_001.ts:169-176`: when the status label is `Reject` (or `Approve`), the `.btn_edit` and `.btn_del` menu entries are hidden (only Print remains). Additionally `:909-911`: when `APPR_TYPE_CD != "00"`, `.btn_del` is hidden outright.
- **List disables "Request Approval" for any row that already carries an `APPR_SEQ_NO`** — `pymn_0009_001.ts:716-719` and `:1715-1717`: `#req_approv` starts enabled and is `.addClass("disable")` the moment a selected row's `appr_seq_no != ""`. A rejected batch retains its `APPR_SEQ_NO`, so it can never be re-selected for a new approval request.
- **List hides the row Delete icon when `APPR_SEQ_NO` is set** — `pymn_0009_001.ts:272` (`if (!val.APPR_SEQ_NO && …)`), and `pymn_0009_001_d001_act.jsp` rejects the delete server-side when `APPR_SEQ_NO` is present (baseline §5.5).

Net: once an Internal Transfer batch is rejected, it is **terminal** — the resolution is closed (`APPR_TYPE_CD=02`, `PAY_END_YN=Y`), and the batch is frozen (cannot be re-requested, cannot be edited in-place, cannot be deleted). To proceed, the requestor uploads a **new** batch from scratch (Flow 1).

### Answers to the four questions

| # | Question | Legacy answer (source-evidenced) |
|---|---|---|
| 1 | Could a rejected Internal Transfer be re-submitted? | **No.** No re-submit function exists in `comm_0195_001.ts`; the rejected batch keeps `APPR_SEQ_NO` so it can't re-enter the request flow. |
| 2 | Was a new approval request generated (after reject)? | **No.** "Request Approval" is disabled for rows with an `APPR_SEQ_NO` (`pymn_0009_001.ts:716-719`); reject does not clear it. |
| 3 | Was approval-line modification allowed after rejection? | **No.** The Edit (approval-line change, `editApproval` → `comm_0192_013_u002`) menu entry is hidden once status is Reject (`comm_0195_001.ts:169-176`). Edit is available only in Pending/Draft. |
| 4 | Was document status reset (after reject)? | **No.** Reject sets `EXPT_RESOL_MSTR.APPR_TYPE_CD=REJECT` + `PAY_END_YN=YES` (terminal); `SCDUL_PYMN_SUMR.APPR_SEQ_NO`/`APPR_STTS` are untouched. No reset to draft. |

### Refactor behaviour (for comparison)

- `wecms-api`: `InternalTransferService.actOnApproval` delegates reject to `ApprovalBoxService.reject` (master `APPR_TYPE_CD=02`, chain `W→S`), mirrors `SCDUL_PYMN_SUMR.APPR_STTS='X'`, and **does not** clear `APPR_SEQ_NO` or reset the batch — same terminal outcome as legacy. There is **no** re-submit / re-request endpoint.
- `wecms-web`: `ScheduledTransferPage.tsx` renders a kebab **"Re-submit"** entry that opens a warning placeholder (`approval.action.resubmit_not_implemented`) — a refactor-only UI affordance with **no legacy counterpart**; it performs nothing.

### Classification

**Legacy did NOT support re-submit after reject → INTENTIONAL MATCH.**

The refactor reproduces the legacy terminal-on-reject behaviour exactly (no re-submit, no new approval request, no post-reject approval-line edit, no status reset). This is **not a parity gap**. The only refactor-only addition is the inert "Re-submit" kebab placeholder, which has no legacy basis; recommended follow-up is to **remove the placeholder** (or keep it explicitly as future-scope), since legacy parity does not require it.

---

## Area-by-area matrix (all four flows)

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| Upload / parse Excel | `pymn_0009_002_r001` + `PymnHelper.importDataFromExcelForInternalTransfer` | `InternalTransferExcelParser` (real POI) | **MATCH** |
| Save draft | `pymn_0009_002_c001` + `PymnHelper.uploadPaymentExcel` | `InternalTransferService.createDraft` | **MATCH** |
| Scheduled list / filter | `pymn_0009_001_r001` | `ScdulPymnSumrRepository.search` | **MATCH** |
| Scheduled detail | `pymn_0009_003_r001` | `InternalTransferService.detail` | **MATCH** |
| Edit reporting account | `pymn_0009_002_u002` | `updateReportingAccount` | **MATCH** (CHART_ACCT existence re-check deferred) |
| Delete draft | `pymn_0009_001_d001` | `delete` | **MATCH** |
| Dispatch / Transfer | `pymn_0009_001_c001` + `validateTransferTiming` | `InternalTransferDispatchService` + `InternalTransferDispatchExecutor` + `CutoffValidator` | **MATCH** (no local bank daemon flips `PROC_STAT` — environmental) |
| Re-run failed line | `pymn_0010_002.ts:441-472` | `rerun` | **MATCH** |
| Transfer result list / detail | `pymn_0010_001` / `pymn_0010_002` (IDO `SCDUL_PYMN_SUMR_R006`) | `InternalTransferResultService` + `DispatchResultResolver` | **MATCH** |
| Request approval (+ attachments) | `comm_0194_001` → `comm_0192_001_c005` | `requestApproval` (`EXPT_RESOL_*` fan-out + `EXPT_RESOL_FILE`) | **MATCH** |
| Approve / Reject | `comm_0195_001` → `bltb_0001_003_u001` | `actOnApproval` → `ApprovalBoxService` | **MATCH** |
| Withdraw approval | `comm_0195_001` kebab Delete | `withdrawApproval` → `ApprovalBoxFanoutService` | **MATCH** |
| Change approval line | `comm_0195_001` Edit (`comm_0192_013_u002`) | `changeApprovalLine` → `ApprovalBoxFanoutService` | **MATCH** |
| **Re-submit after reject** | **Not supported** (this analysis) | Not supported (inert kebab placeholder only) | **MATCH (intentional)** |

**Internal Transfer refactor status: parity COMPLETE (MATCH).** The single "Re-submit after reject" item previously listed as an open gap is reclassified here as an **intentional match** — legacy never supported it.

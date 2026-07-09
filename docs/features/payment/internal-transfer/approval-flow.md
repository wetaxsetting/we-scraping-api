# Flow 4 — Approval / Approval Box

Source-code-verified 2026-06-23. Backend authority: `InternalTransferService` (`requestApproval` / `actOnApproval` / `withdrawApproval` / `changeApprovalLine`) delegating to `ApprovalBoxService` + `ApprovalBoxFanoutService`.

> **Status-column note (2026-06-10):** `SCDUL_PYMN_SUMR` has **no `APPR_STTS` column** in the legacy JEX schema. The refactor removed the denormalized mirror column; per-batch approval status is **derived on read** by calling the legacy DB function `wb_get_appr_stts(...)` over `EXPT_RESOL_MSTR` (joined by `(BIZ_KEY, APPR_SEQ_NO)`) via `LegacyFunctionRepository`. Any phrase below that says "stamp/clear `SCDUL_PYMN_SUMR.APPR_STTS`" is stale — only `APPR_SEQ_NO` is written/cleared on the master; `APPR_TYPE_CD` on `EXPT_RESOL_MSTR` is the stored source of truth. Frontend detail: [wecms-web/.../internal-transfer/approval-flow.md](../../../../../wecms-web/docs/features/payment/internal-transfer/approval-flow.md). Cross-feature: [../approval-box/README.md](../approval-box/README.md).

---

## Purpose

The approval lifecycle for a scheduled internal-transfer batch: submit it for approval (writes the `EXPT_RESOL_*` approval rows + attachments), track approval status, approve/reject from the Approval Details popup (or from the cross-feature Approval Box), withdraw an in-flight request, or change the approver line. A batch must be Approved before Flow 2's dispatch is allowed.

---

## User Actions

| Action | Endpoint | Service |
|---|---|---|
| **Submit** (Request Approval, + attachments) | `POST .../{id}/request-approval` (multipart) | `InternalTransferService.requestApproval` |
| Preview next document no. | `GET .../next-doc-no` | `previewNextDocNo` (`DocNoGenerator`) |
| **View approval status** | derived on read via `wb_get_appr_stts(...)` over `EXPT_RESOL_MSTR.APPR_TYPE_CD` + the `EXPT_RESOL_APPRVR_INFM` chain | `detail` (Flow 2) + `GET /approval-box/{apprSeqNo}/approvers` |
| **Approve** / **Reject** | `POST .../{id}/approval-action` (`{action:"APPROVE"|"REJECT"}`) | `actOnApproval` → `ApprovalBoxService.approve/reject` |
| **Open Approval Details** | client opens the shared `ApprovalDetailsDialog` from the Approval-Ref cell | (frontend) |
| **Withdraw** (kebab Delete) | `DELETE .../{id}/approval` | `withdrawApproval` → `ApprovalBoxFanoutService.withdrawApproval` |
| **Change approval line** (kebab Edit) | `PUT .../{id}/approval-line` (`{appr_line_no}`) | `changeApprovalLine` → `ApprovalBoxFanoutService.changeApprovalLine` |
| Download approval attachment | `GET .../{id}/approval/{apprSeqNo}/attachments/{fileId}` | `resolveApprovalAttachmentDownload` |

All gated by `@menuAuthz.canAccess('payment-internal-transfer-scheduled')`.

---

## Backend / API involvement

### Request approval (`requestApproval`)
`@Transactional`, multipart (`request` JSON + zero-or-more `attachments`). Idempotency guard: if `APPR_SEQ_NO` already set → `WBE2062`. Validates `approvers` non-empty. Allocates `APPR_SEQ_NO` via the single `wb_get_next_val` funnel (`allocateApprSeqNo` → `LegacyFunctionRepository.nextApprSeqNo` → the GLOBAL `sq_appr_seq_no` sequence shared across every Payment module; one retry on `DataIntegrityViolationException`). `stampApproval(...)`:
1. Stamp `SCDUL_PYMN_SUMR`: `APPR_SEQ_NO` + (optionally) refreshed `TITL` / withdrawal account (`OUT_BANK_CD` normalised, `WDRL_ACT_NO`, `WDRL_CCY_CD`). **No `APPR_STTS` write** (column does not exist).
2. Insert `EXPT_RESOL_MSTR`: `APPR_DV=ApprDv.INTERNAL_TRANSFER.code()` (`35`), `APPR_STS="2"` (In Progress, legacy `comm_0192_001_c005` parity), draft date/time/user, draft site = company, `TITL`=postTitle, **`CONT`=postCont (Approval Content)**, `DOC_NO`=`DocNoGenerator.nextDocNo` (`VO_YYMMDD_NNN`), `DOC_NM`=`ApprDv.INTERNAL_TRANSFER.docNm()`, amount split into `TOT_PAY_{VND,USD,KHR}_AMT`, **`PAY_DATE`=req.payDate (Scheduled Date)**, display flags from `BUSI_ENV_SET_INFM` via `ApprovalDisplayFlagsResolver`, `DEL_YN='N'`, `PAY_END_YN='N'`, `APPR_TYPE_CD='00'`.
3. **`DOC_NO` is NOT back-stamped onto `SCDUL_PYMN_SUMR`** — its canonical home is `EXPT_RESOL_MSTR.DOC_NO`. (`ERP_FILE_NUM` is the legacy 8-digit ERP file sequence and is left untouched; overloading it with the doc_no overflowed the `varchar(8)` column.)
4. Insert one `EXPT_RESOL_APPRVR_INFM` per approver: line 1 → `APPRVR_STTS='P'` (active), rest → `'W'` (waiting); `APPRVR_LINE_NO`, `APPR_LINE_NO`, `READ_YN='N'`.
5. `approvalBoxFanoutService.writeResolutionDetail(...)` (EXPT_RESOL_DETL, F-1) + `writeApprovalLineSnapshot(...)` (EXPT_RESOL_APPR_LINE print-header, env-gated, F-2).
6. `persistApprovalAttachments`: stream each file to `<storage-root>/<bizKey>/approval-attachments/<apprSeqNo>/` + insert one `EXPT_RESOL_FILE` row (`FILE_ID` via the `wb_get_next_val` funnel, per `(biz_key, appr_seq_no)` MAX+1, incremented in Java across the batch).
7. `approvalNotificationService.notifyRequestApproval(...)` — BLTB_* bulletin + per-approver notification fan-out.
Audit `request_approval.detail`. Returns `RequestApprovalResponse(apprSeqNo, docNo)`.

### Approve / Reject (`actOnApproval`)
`@Transactional`. Guard 1: no `APPR_SEQ_NO` → `WBE2126`. Guard 2: already final (derived `wb_get_appr_stts` ∈ {`C` Approved, `R` Rejected}) → `WBE2127`. **Delegates the chain logic to `ApprovalBoxService.approve(apprSeqNo)` / `.reject(apprSeqNo)`** — those advance `EXPT_RESOL_APPRVR_INFM` (current `P→A`/`R`, promote next `W→P` on approve, cascade on reject) and set `EXPT_RESOL_MSTR.APPR_TYPE_CD` (the source of truth). **No write-back to `SCDUL_PYMN_SUMR`** — the list/detail pages re-derive status from `EXPT_RESOL_MSTR` on read. Audit `approve.detail` / `reject.detail`.

### Withdraw (`withdrawApproval`)
`@Transactional`. No `APPR_SEQ_NO` → `WBE2126`. `ApprovalBoxFanoutService.withdrawApproval(apprSeqNo)` deletes `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM` (Pending/In-Progress guard). Then clears `SCDUL_PYMN_SUMR.APPR_SEQ_NO` only → batch returns to Draft (`ERP_FILE_NUM` is intentionally NOT cleared; there is no `APPR_STTS` column to clear). Audit `withdraw_approval.detail`.

### Change approval line (`changeApprovalLine`)
`@Transactional`. No `APPR_SEQ_NO` → `WBE2126`. `ApprovalBoxFanoutService.changeApprovalLine(apprSeqNo, apprLineNo)` rebuilds the chain from the picked approval-line template (line 1 `P`, rest `W`) and resets `EXPT_RESOL_MSTR` back to Pending (the source of truth). **No `SCDUL_PYMN_SUMR` write-back.** Audit `change_approval_line.detail`.

### Status & code model
- **Per-batch approval status is derived on read** via `wb_get_appr_stts(...)` over `EXPT_RESOL_MSTR.APPR_TYPE_CD` (`'00'→P` Pending · `'01'→C` Approved · `'02'→R` Rejected · `'03'→I` In Progress; null = Draft). `SCDUL_PYMN_SUMR` has no stored `APPR_STTS` column. `APPR_STTS_APPROVED="C"` / `APPR_STTS_REJECTED="R"` constants in `InternalTransferService` gate the approve/reject "already final" check.
- `EXPT_RESOL_APPRVR_INFM.APPRVR_STTS`: `P`/`A`/`R`/`W`/`S` (`ScheduledTransferApprover.apprvrStts`, `apprDtm` timestamp).

---

## Special verification (source-evidenced)

1. **Approval Details integration — both directions:**
   - *Internal Transfer → Approval Details:* `ScheduledTransferPage.handleApprovalRefClick` → `fetchScheduledTransferDetail(id)` then `fetchApprovalBoxApprovers(detail.appr_seq_no)` → opens `ApprovalDetailsDialog` with `content=detail.cont`, `scheduledDate=row.scheduleDate`, `attachments=detail.attachments`, `approvers=chainToDialogApprovers(chain)`.
   - *Approval Box → Internal Transfer details:* Approval Box page's `pickDetailLoader('internal_transfer')` calls `fetchScheduledTransferDetail(documentNo)` to render the per-line transactions. (Approval Box currency/file-name for the row come from `ScdulPymnSumrRepository.findCcyCdByBizKeyAndApprSeqNos` / `findOrgFileNmByBizKeyAndApprSeqNos`.)
2. **Scheduled Date:** source `EXPT_RESOL_MSTR.PAY_DATE` (set from `req.payDate` at request-approval); surfaced on the list via `buildPayDateMap`, on the detail as `payDate`, and into the dialog as `scheduledDate`. NOT `SCDUL_PYMN_SUMR.PAY_TRAN_DT` (the dispatch date).
3. **Approval Content + attachment:** content = `EXPT_RESOL_MSTR.CONT` (from `req.postCont`); null/blank when empty → dialog renders the empty-state. Attachments = `EXPT_RESOL_FILE` rows → `ApprovalAttachmentRow[]` on the detail; each downloads via `GET .../approval/{apprSeqNo}/attachments/{fileId}`. The batch's source Excel is intentionally NOT shown as an approval attachment (it's the Flow-1 file card).
4. **Approval line rendering:** chain from `EXPT_RESOL_APPRVR_INFM` (`findChain`, ordered `APPRVR_LINE_NO ASC`); per-approver `apprvrNm` / `apprvrStts` (P/A/R/W/S) / `apprDtm` timestamp. Frontend `isCurrentUserActiveApprover` gates Approve/Reject to the first-pending approver.
5. **File attachment handling:** upload (multipart on request-approval → `EXPT_RESOL_FILE` + disk); download (`resolveApprovalAttachmentDownload`, tenant + apprSeqNo + fileId cross-checks, `WBE2069` when missing); Approval Details display (attachment chips with per-file download).

---

## Database / persistence

| Table | Write |
|---|---|
| `SCDUL_PYMN_SUMR` | `APPR_SEQ_NO` (+ optional title / withdrawal account) stamp on request; cleared (`APPR_SEQ_NO=null`) on withdraw. **No `APPR_STTS` column** — status is derived on read, never written here; `ERP_FILE_NUM` left untouched. |
| `EXPT_RESOL_MSTR` | insert on request (incl. `APPR_STS="2"`, `CONT`, `PAY_DATE`, `DOC_NO`, `DOC_NM`); `APPR_TYPE_CD` transition via `ApprovalBoxService` on approve/reject; reset to Pending on change-line; deleted on withdraw |
| `EXPT_RESOL_DETL` / `EXPT_RESOL_APPR_LINE` | resolution-detail row + print-header snapshot on request (`ApprovalBoxFanoutService`) |
| `EXPT_RESOL_APPRVR_INFM` | chain insert on request; `APPRVR_STTS` advance on approve/reject; rebuilt on change-line; deleted on withdraw |
| `EXPT_RESOL_FILE` | attachment rows on request (`FILE_ID` via `wb_get_next_val` funnel) |
| `BLTB_*` | bulletin post + per-approver notification rows on request (`ApprovalNotificationService`) |
| `AUDIT_LOGS_INFM` | request/approve/reject/withdraw/change-line audit rows |
| filesystem | attachments under `<storage-root>/<bizKey>/approval-attachments/<apprSeqNo>/` |

---

## Legacy mapping

| Legacy | Refactor |
|---|---|
| `comm_0194_001` → `comm_0192_001_c005` (request approval) | `POST .../{id}/request-approval` → `requestApproval` |
| `comm_0195_001` footer Approve/Reject → `bltb_0001_003_u001` | `POST .../{id}/approval-action` → `actOnApproval` → `ApprovalBoxService` |
| `comm_0195_001` kebab Delete (withdraw) | `DELETE .../{id}/approval` → `ApprovalBoxFanoutService.withdrawApproval` |
| `comm_0195_001` edit-mode (change line) | `PUT .../{id}/approval-line` → `ApprovalBoxFanoutService.changeApprovalLine` |
| `EXPT_RESOL_MSTR` / `EXPT_RESOL_APPRVR_INFM` / `EXPT_RESOL_FILE` | same tables (shared with Approval Box) |
| `SYS_DOC_ID_GEN` doc-no | `DocNoGenerator.nextDocNo` (`VO_YYMMDD_NNN`) |

---

## Legacy parity status

**MATCH** — request-approval fan-out (master + chain + attachments + notification), approve/reject via the shared approver-chain engine, withdraw, and change-line are all implemented against the real `EXPT_RESOL_*` tables.

## Refactor status

**Implemented.** (Supersedes the baseline "Phase A stamps a synthesised APPR_SEQ_NO only" note — the full `EXPT_RESOL_*` fan-out is live.)

## Open gaps (source-evidenced)

- **Re-submit after reject — NOT a gap (intentional match).** Legacy Internal Transfer never supported re-submitting a rejected batch: reject sets `EXPT_RESOL_MSTR.APPR_TYPE_CD=REJECT` + `PAY_END_YN=YES` (terminal) and never clears `SCDUL_PYMN_SUMR.APPR_SEQ_NO`, so the legacy list disables "Request Approval" and hides Delete/Edit for it (`bltb_0001_003_u001_act.jsp:236-376`, `comm_0195_001.ts:169-176`, `pymn_0009_001.ts:716-719`). The refactor reproduces this terminal-on-reject behaviour. The frontend kebab "Re-submit" placeholder is a refactor-only inert affordance with no legacy basis (recommend removal). Full source trace: [refactor-gap-analysis.md](refactor-gap-analysis.md).
- `RequestApprovalDialog`'s own **submit** path on the list page surfaces a "coming soon" path in one wiring (`onSubmitted` comment) — but the live `submitInternalTransferApproval` client + `POST .../request-approval` backend are fully implemented; the Approval-Ref-click → `ApprovalDetailsDialog` approve/reject/withdraw/edit path is fully wired.
- Approve/reject locale keys live under `payment.approval_details.*` / `approval.action.*` (present).

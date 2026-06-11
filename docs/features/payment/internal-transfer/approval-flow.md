# Flow 4 — Approval / Approval Box

Source-code-verified 2026-06-08. Backend authority: `InternalTransferService` (`requestApproval` / `actOnApproval` / `withdrawApproval` / `changeApprovalLine`) delegating to `ApprovalBoxService` + `ApprovalBoxFanoutService`. Frontend detail: [wecms-web/.../internal-transfer/approval-flow.md](../../../../../wecms-web/docs/features/payment/internal-transfer/approval-flow.md). Cross-feature: [../approval-box/README.md](../approval-box/README.md).

---

## Purpose

The approval lifecycle for a scheduled internal-transfer batch: submit it for approval (writes the `EXPT_RESOL_*` approval rows + attachments), track approval status, approve/reject from the Approval Details popup (or from the cross-feature Approval Box), withdraw an in-flight request, or change the approver line. A batch must be Approved before Flow 2's dispatch is allowed.

---

## User Actions

| Action | Endpoint | Service |
|---|---|---|
| **Submit** (Request Approval, + attachments) | `POST .../{id}/request-approval` (multipart) | `InternalTransferService.requestApproval` |
| Preview next document no. | `GET .../next-doc-no` | `previewNextDocNo` (`DocNoGenerator`) |
| **View approval status** | rendered from `SCDUL_PYMN_SUMR.APPR_STTS` + `EXPT_RESOL_APPRVR_INFM` chain | `detail` (Flow 2) + `GET /approval-box/{apprSeqNo}/approvers` |
| **Approve** / **Reject** | `POST .../{id}/approval-action` (`{action:"APPROVE"|"REJECT"}`) | `actOnApproval` → `ApprovalBoxService.approve/reject` |
| **Open Approval Details** | client opens the shared `ApprovalDetailsDialog` from the Approval-Ref cell | (frontend) |
| **Withdraw** (kebab Delete) | `DELETE .../{id}/approval` | `withdrawApproval` → `ApprovalBoxFanoutService.withdrawApproval` |
| **Change approval line** (kebab Edit) | `PUT .../{id}/approval-line` (`{appr_line_no}`) | `changeApprovalLine` → `ApprovalBoxFanoutService.changeApprovalLine` |
| Download approval attachment | `GET .../{id}/approval/{apprSeqNo}/attachments/{fileId}` | `resolveApprovalAttachmentDownload` |

All gated by `@menuAuthz.canAccess('payment-internal-transfer-scheduled')`.

---

## Backend / API involvement

### Request approval (`requestApproval`)
`@Transactional`, multipart (`request` JSON + zero-or-more `attachments`). Idempotency guard: if `APPR_SEQ_NO` already set → `WBE2062`. Validates `approvers` non-empty. Allocates `APPR_SEQ_NO` via per-tenant MAX+1 over both `EXPT_RESOL_MSTR` and `SCDUL_PYMN_SUMR` (`allocateApprSeqNo`, one retry on `DataIntegrityViolationException`). `stampApproval(...)`:
1. Stamp `SCDUL_PYMN_SUMR`: `APPR_SEQ_NO`, `APPR_STTS='P'` (Pending), optional title.
2. Insert `EXPT_RESOL_MSTR`: `APPR_DV=ApprDv.INTERNAL_TRANSFER.code()` (`35`), draft date/time/user, `TITL`=postTitle, **`CONT`=postCont (Approval Content)**, `DOC_NO`=`DocNoGenerator.nextDocNo` (`VO_YYMMDD_NNN`), amount in the currency-specific column, **`PAY_DATE`=req.payDate (Scheduled Date)**, `APPR_TYPE_CD='00'`.
3. Write `DOC_NO` back to `SCDUL_PYMN_SUMR.ERP_FILE_NUM`.
4. Insert one `EXPT_RESOL_APPRVR_INFM` per approver: line 1 → `APPRVR_STTS='P'` (active), rest → `'W'` (waiting); `APPRVR_LINE_NO`, `APPR_LINE_NO`.
5. `persistApprovalAttachments`: stream each file to `<storage-root>/<bizKey>/approval-attachments/<apprSeqNo>/` + insert one `EXPT_RESOL_FILE` row (`fileId` 1..N).
6. `notificationService.notifyApprovalRequested(...)` (best-effort).
Audit `request_approval.detail`. Returns `RequestApprovalResponse(apprSeqNo, docNo)`.

### Approve / Reject (`actOnApproval`)
`@Transactional`. Guard: no `APPR_SEQ_NO` → `WBE2094`; already final (`APPR_STTS ∈ {C,X}`) → `WBE2095`. **Delegates the chain logic to `ApprovalBoxService.approve(apprSeqNo)` / `.reject(apprSeqNo)`** — those advance `EXPT_RESOL_APPRVR_INFM` (current `P→A`/`R`, promote next `W→P` on approve, cascade `W→S` on reject), set the master `APPR_TYPE_CD`, and write back to `SCDUL_PYMN_SUMR.APPR_STTS`. The returned `apprStts` (`P/I/C/R`) is mapped to the `SCDUL_PYMN_SUMR` convention (`mapApprStts`: `C`/`I`/`P`/`X`) and stamped on the master. Audit `approve.detail` / `reject.detail`.

### Withdraw (`withdrawApproval`)
`@Transactional`. No `APPR_SEQ_NO` → `WBE2094`. `ApprovalBoxFanoutService.withdrawApproval(apprSeqNo)` deletes `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM` (Pending/In-Progress guard). Then clears `SCDUL_PYMN_SUMR.APPR_SEQ_NO`/`APPR_STTS`/`ERP_FILE_NUM` → batch returns to Draft. Audit `withdraw_approval.detail`.

### Change approval line (`changeApprovalLine`)
`@Transactional`. No `APPR_SEQ_NO` → `WBE2094`. `ApprovalBoxFanoutService.changeApprovalLine(apprSeqNo, apprLineNo)` rebuilds the chain from the picked approval-line template (line 1 `P`, rest `W`), resets master to Pending. Mirrors `SCDUL_PYMN_SUMR.APPR_STTS='P'`. Audit `change_approval_line.detail`.

### Status & code model
- `SCDUL_PYMN_SUMR.APPR_STTS`: `P` Pending · `I` In Progress · `C` Approved · `X` Rejected (null = Draft). `APPR_STTS_*` constants in `InternalTransferService`.
- `EXPT_RESOL_APPRVR_INFM.APPRVR_STTS`: `P`/`A`/`R`/`W`/`S` (`ScheduledTransferApprover.apprvrStts`, `apprDtm` timestamp).
- The `ApprovalBoxService` returns `P/I/C/R`; `mapApprStts` translates Rejected `R→X` for the `SCDUL_PYMN_SUMR` column.

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
| `SCDUL_PYMN_SUMR` | `APPR_SEQ_NO`/`APPR_STTS`/`ERP_FILE_NUM` stamp on request; `APPR_STTS` on approve/reject; cleared on withdraw |
| `EXPT_RESOL_MSTR` | insert on request (incl. `CONT`, `PAY_DATE`, `DOC_NO`); `APPR_TYPE_CD` transition via `ApprovalBoxService`; deleted on withdraw |
| `EXPT_RESOL_APPRVR_INFM` | chain insert on request; `APPRVR_STTS` advance on approve/reject; rebuilt on change-line; deleted on withdraw |
| `EXPT_RESOL_FILE` | attachment rows on request |
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

# Notification → Approval Details Popup — Legacy vs Refactor Parity Audit

> **System:** Refactor (wecms-web, with wecms-api implications) · **Type:** read-only parity audit
> **Scope:** the Approval Details popup opened by clicking an approval notification in the header bell.
> **Date:** 2026-06-14 · **No code changed.**
> Compares legacy `comm_0195_001.act` (opened from `comm_0192_007.ts fn_readOneCommNoti`) with the new `NotificationApprovalDetailDialog` → shared `ApprovalDetailsDialog`.

---

## 0. Verdict (read this first)

- **Full legacy parity?** ❌ **No.** The notification-opened dialog reproduces the *navigation* behavior (opens on the current page, no redirect, reopens for read rows, handles deleted) and the approval **header / status / approver chain / content**, but it is **missing the transaction-detail rows, attachments, and the Approve/Reject (and Edit/Withdraw) actions** that legacy `comm_0195_001` shows.
- **Safe for SIT?** ⚠️ **Partially.** The *click behavior* is safe and correct to ship. The *popup content* is a read-only subset — an approver can see who/what/status but **cannot review the payment line items they are approving, nor act on the approval**. That is a material gap against the notification's primary purpose. Ship the navigation fix; do **not** call the Approval Details popup "complete."
- **Follow-up required before complete:** (1) load transaction rows by `APPR_SEQ_NO`, (2) wire Approve/Reject (+ Edit/Withdraw) using the existing approval-box action endpoints, (3) load the attachment list. (1) and (3) need a small **read-only** backend addition (no DB schema change); (2) is frontend-only. Details in §4.

---

## 1. Legacy `comm_0195_001` popup behavior

### 1.1 Files

| Artifact | Path |
|---|---|
| Opener | [comm_0192_007.ts](../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0192_007.ts) `fn_readOneCommNoti` (opens `comm_0195_001.act` via `_v2common.app.openLayerPopup` with `{ POST_SEQ_NO, APPR_DV }`) |
| View | [comm_0195_001_view.jsp](../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0195_001_view.jsp) |
| TS | [comm_0195_001.ts](../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0195_001.ts) (2343 lines) |

### 1.2 APIs called

| Purpose | Service id | Key param | Notes |
|---|---|---|---|
| **Master detail load** | `comm_0193_001_r001` | **`POST_SEQ_NO`** (`comm_0195_001.ts:492`) | The single load. Returns `APPR_SEQ_NO`, `APPR_DV`, `REG_ID`, `APPR_YN`, approval-line/approvers, **transaction rows**, attachments, content — all in one response. |
| Attachment thumb / download / upload | `comm_0192_013_r002` / `comm_0192_013_c001` / `comm_0192_013_u002` | file keys | `comm_0195_001.ts:87,363,1099` |
| Comment add | `bltb_0001_003_u001` | `POST_SEQ_NO` | `comm_0195_001.ts:1157` |
| Withdraw / delete request | `comm_0192_004_d001` | `POST_SEQ_NO` | `comm_0195_001.ts:1352` |
| Re-submit / edit (per kind) | `pymn_000{1,3,5,7,9}_001_c001` | per kind | `comm_0195_001.ts:1267-1318` (APPR_DV → API map) |
| Change approval line | `comm_0222_003.act` (layer) | `APPR_LINE_NO` | `comm_0195_001.ts:431` |

> The approve/reject submit reuses the approval action services (`comm_0192_010_u001/u002` family, same ones `comm_0192_007.ts fn_oneApprove/fn_rejectAppro` call). The **button visibility** is decided in `comm_0195_001` from the `comm_0193_001_r001` response (see §1.5).

### 1.3 Request parameters (entry from notification)
`fn_readOneCommNoti` parses `NOTI_JSON` → `{ POST_SEQ_NO, APPR_DV }` and opens the layer popup with those. The popup then loads `comm_0193_001_r001` **by `POST_SEQ_NO`** (not `APPR_SEQ_NO`). `APPR_SEQ_NO` arrives *in the response* (`comm_0195_001.ts:517`).

### 1.4 What the popup shows (response-driven)
- **Approval header** — type label (`APPR_DV`), document no., scheduled date, total amount, withdrawal bank/account.
- **Approval line / approvers** — per-approver chips with `APPRVR_STTS` (P/A/R/W/S) + acted date; active approver = `_user_id === APPRVR_ID && APPRVR_STTS == "P"` (`comm_0195_001.ts:528`).
- **Transaction detail rows** — rendered per `APPR_DV` variant (card `:1491`, overseas `:1521`, payroll `:1603`, …) from the `comm_0193_001_r001` response (inline, no second fetch keyed off a batch id).
- **Attachments** — file cards with thumb/download (`comm_0192_013_*`).
- **Content / comments** — approval content note + comment thread (`bltb_0001_003_u001` to add).
- **Approve / Reject buttons** — see §1.5.
- **Edit / Withdraw (kebab)** — requestor- and status-gated (see §1.5).

### 1.5 Status / role-based button visibility (`comm_0195_001.ts:915-946`)
- **Approve/Reject:** shown only when the backend says the current user may act — `data.APPR_YN` (`:928`) i.e. the current user is the active (first-pending) approver and status is in-progress.
- **Edit/Withdraw:** shown when `data.REG_ID === _user_id` (requestor) **or** `_isAdmin` (`:915,922-923`), and status still pending/in-progress.
- **Deleted/withdrawn:** a withdrawn approval has its bulletin row removed; the notification path shows the "deleted" notice (`WBI1098`) and reloads instead of opening.

---

## 2. Refactor existing Approval Details components & APIs

### 2.1 Components
| Component | Role |
|---|---|
| [`ApprovalDetailsDialog`](../../../wecms-web/components/payment/shared/ApprovalDetailsDialog.tsx) | **Shared presentational** full-screen dialog (legacy `comm_0195_001` twin). `kind`-driven; renders header / status / approvers / content / **transactions (caller-supplied)** / attachments; optional `onApprove`/`onReject`/`onMenuAction` + edit-mode. Fetches nothing itself. |
| [`ApprovalRefDetailDialog`](../../../wecms-web/components/payment/corporate-card/ApprovalRefDetailDialog.tsx) | Card entry point. Fetches master via `fetchApprovalBoxList(srchWd)`, **card transactions via `fetchCardTransactionsByApproval(apprSeqNo)`**, computes the active-approver gate, wires Approve/Reject/Withdraw/Edit. **This is the working reference for a complete, action-wired notification dialog.** |
| [`NotificationApprovalDetailDialog`](../../../wecms-web/components/layout/notifications/NotificationApprovalDetailDialog.tsx) | The current notification entry point. Fetches master via `fetchApprovalBoxList(srchWd: apprSeqNo)`, derives `kind` from `appr_dv`, renders the shared dialog with `transactions={[]}`, no actions. |

### 2.2 APIs (wecms-web `lib/api/`)
| Function | Key | Returns | Usable from a notification? |
|---|---|---|---|
| `fetchApprovalBoxList(q)` | `srchWd` (free-text) | master rows (header/status/**approvers**/content/total/doc_no/`appr_dv`) — **no transactions** | ✅ (current dialog uses it) |
| `fetchApprovalBoxApprovers(apprSeqNo)` | `apprSeqNo` | approver chain | ✅ (approvers already on the list row) |
| `approveApprovalBoxRow(apprSeqNo)` / `rejectApprovalBoxRow(apprSeqNo)` | `apprSeqNo` | — | ✅ (exists, generic) |
| `withdrawApprovalBoxRow(apprSeqNo)` / `changeApprovalBoxApprovalLine(...)` | `apprSeqNo` | — | ✅ (exists, generic) |
| `downloadApprovalBoxAttachment(apprSeqNo, fileId, fileType)` | `apprSeqNo`+`fileId` | binary | ⚠️ download only — **no list-by-apprSeqNo** |
| `fetchCardTransactionsByApproval(apprSeqNo)` | **`apprSeqNo`** | card tx rows | ✅ **card only** |
| `fetchScheduledTransferDetail` / `fetchVendorPaymentDetail` / `fetchExpensePaymentDetail` / `fetchPayrollPaymentDetail` | **`scdulPymnNo` (batch id)** | per-kind detail incl. transactions + attachments | ❌ keyed by **batch id**, which a notification does not carry |
| `fetchOverseasPaymentDetail(id)` | **batch `id`** | overseas detail | ❌ same |

### 2.3 The structural gap
A notification's `noti_json` carries `APPR_SEQ_NO` (and `POST_SEQ_NO`), but **not** the per-kind batch id (`scdul_pymn_no` / `ovrs_pymn_no`). The approval-box master row (`ApiApprovalBoxRow`) also does **not** expose a batch id. So the existing per-kind transaction/attachment fetchers (keyed by batch id) are **unreachable from a notification** for the 5 non-card kinds. Only `card` has a fetcher keyed by `APPR_SEQ_NO`.

---

## 3. Legacy vs current notification-opened dialog

| Capability | Legacy `comm_0195_001` | Current `NotificationApprovalDetailDialog` | Status | Severity | Required for SIT parity? |
|---|---|---|---|---|---|
| Opens on current page, no redirect | ✅ layer popup | ✅ full-screen portal dialog | **MATCH** | — | — |
| Reopen for already-read row | ✅ | ✅ (open runs regardless of read state) | **MATCH** | — | — |
| Deleted/withdrawn handling | ✅ `WBI1098` then reload | ✅ deleted → warning + refresh; not-found → safe message | **MATCH** | — | — |
| Approval header (type/doc no/date/total) | ✅ | ✅ (from master row) | **MATCH** | — | — |
| Approval line / approver chips (P/A/R/W/S + dates) | ✅ | ✅ (from `row.approvers`) | **MATCH** | — | — |
| Approval content note | ✅ | ✅ (`row.cont`) | **MATCH** | — | — |
| Status badge | ✅ | ✅ | **MATCH** | — | — |
| **Transaction detail rows** | ✅ inline from `comm_0193_001_r001` | ❌ `transactions={[]}` (empty state) | **MISSING** | **HIGH** | **Yes** — an approver must see the lines they approve |
| **Attachments** (list + download) | ✅ | ❌ no list passed (download fn exists) | **MISSING** | **MEDIUM** | Recommended |
| **Approve / Reject buttons** (when active approver) | ✅ `APPR_YN` gated | ❌ not rendered | **MISSING** | **HIGH** | **Yes** — the notification's purpose is to act |
| **Edit / Withdraw** (requestor/admin) | ✅ `REG_ID`/`_isAdmin` gated | ❌ not rendered | **MISSING** | **MEDIUM** | For requestor notifications |
| Comment thread (add/view) | ✅ `bltb_0001_003_u001` | ❌ not rendered (content shown, thread not) | **MISSING** | **LOW** | Optional |
| Change approval line | ✅ | ❌ | **MISSING** | **LOW** | Optional |

**Summary:** navigation + identity + approver/header/content reach parity; the **action surface (approve/reject/edit/withdraw)** and the **payment line items + attachments** do not.

---

## 4. Implementation recommendation

> Constraint honoured: **no DB schema changes.** The backend additions below are read-only queries over existing tables (the same data `comm_0193_001_r001` already returns).

### 4.1 Transaction rows (HIGH) — minimal fix
The clean fix is **one generic read endpoint keyed by `APPR_SEQ_NO`**, mirroring the legacy `comm_0193_001_r001` transaction projection, so the notification dialog (and any future "by approval" caller) can load lines without a batch id:

- **Backend (read-only, no schema change):** `GET /api/v1/payment/approval-box/{apprSeqNo}/transactions` → normalized per-line rows (deposit bank, amount, recipient, remarks, reporting account, …) resolved from the per-kind summary-detail tables via `APPR_SEQ_NO` (the link the backend already has: `EXPT_RESOL_MSTR.APPR_SEQ_NO` → the source batch). Shape it to the existing `RequestApprovalTransaction` contract.
- **Frontend:** add `fetchApprovalTransactionsByApproval(apprSeqNo)`; in `NotificationApprovalDetailDialog`, fetch it and pass real `transactions`. For `kind === "card"`, reuse the existing `fetchCardTransactionsByApproval(apprSeqNo)` instead (already keyed by `APPR_SEQ_NO`).

*Alternative (no new endpoint):* expose the batch id (`scdul_pymn_no` / `ovrs_pymn_no`) on the approval-box master DTO, then call the existing per-kind `fetch*Detail(scdulPymnNo)` per `kind`. Reuses existing endpoints but adds a field to the approval-box response + 6-way per-kind plumbing on the client. The generic endpoint is cleaner and is the recommendation.

### 4.2 Approve / Reject + Edit / Withdraw (HIGH / MEDIUM) — frontend-only
All endpoints already exist (`approveApprovalBoxRow`, `rejectApprovalBoxRow`, `withdrawApprovalBoxRow`, `changeApprovalBoxApprovalLine`). **Reuse the exact pattern already implemented in `ApprovalRefDetailDialog`:**
- compute the active-approver gate (`firstPendingApproverId` / `isCurrentUserActiveApprover` exported from `ApprovalDetailsDialog`, or `row.approvers.find(a => a.apprvr_stts === "P").apprvr_id === currentUserId`),
- pass `onApprove`/`onReject` only when `canAct`,
- pass `onMenuAction` (edit/withdraw) when requestor (`row.draft_user_id === currentUserId`) and in-progress,
- on success, call the dialog's `onClose` which already triggers the notification `refresh()`.

No backend work.

### 4.3 Attachments (MEDIUM)
Either have the §4.1 generic endpoint also return attachment metadata (`fileId`, name, size, fileType), or add `GET /api/v1/payment/approval-box/{apprSeqNo}/attachments` (list). Then pass `attachments` to the shared dialog and wire `onDownload` to the existing `downloadApprovalBoxAttachment(apprSeqNo, fileId, fileType)`. Read-only, no schema change.

### 4.4 Components/APIs to add or reuse — checklist
- **Add (frontend):** `fetchApprovalTransactionsByApproval(apprSeqNo)` (+ optional `fetchApprovalBoxAttachments(apprSeqNo)`).
- **Add (backend, read-only):** `GET …/approval-box/{apprSeqNo}/transactions` (+ optional `…/attachments`).
- **Reuse (frontend, no new code):** `fetchCardTransactionsByApproval`, `approveApprovalBoxRow`, `rejectApprovalBoxRow`, `withdrawApprovalBoxRow`, `changeApprovalBoxApprovalLine`, the active-approver gate from `ApprovalRefDetailDialog`/`ApprovalDetailsDialog`, `downloadApprovalBoxAttachment`.
- **No DB schema changes.**

---

## 5. Final result

- **Is notification-click Approval Details at full legacy parity?** **No.** Navigation, identity, header, approver chain, status, and content match; **transaction line items, attachments, and the Approve/Reject + Edit/Withdraw actions are missing.**
- **Is it safe for SIT?** **The click/navigation behavior is safe** (opens on the current page, no redirect to Approval Box, reopens for read rows, handles deleted/withdrawn). **The popup itself is a read-only partial** — not safe to present as the finished Approval Details experience, because an approver can neither review the payment lines nor act on the approval from it.
- **Follow-up required before calling it complete:**
  1. **(HIGH)** Load transaction rows by `APPR_SEQ_NO` — generic read-only endpoint (§4.1) for the 5 non-card kinds; reuse `fetchCardTransactionsByApproval` for card.
  2. **(HIGH)** Render Approve/Reject (active-approver gated) — frontend-only, reuse existing endpoints + the `ApprovalRefDetailDialog` gate (§4.2).
  3. **(MEDIUM)** Attachments list + download (§4.3); Edit/Withdraw for requestor (§4.2).
  4. **(LOW)** Comment thread / change-approval-line — optional for parity.

Until items 1–2 land, the notification → Approval Details popup should be treated as **"opens correctly, content incomplete."** No code or DB schema was changed by this audit.

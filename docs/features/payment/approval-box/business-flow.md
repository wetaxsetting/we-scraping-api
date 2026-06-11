# Approval Box — Business Flow (Backend)

Traces the full lifecycle the audit prompt asks for:

```
Approval Request → Approval Box Search → Approval Detail View
→ Approval Action → Reject Action → Status Update → Persistence → UI Refresh
```

All identity (`bizKey`, `userId`, `isAdmin`) is read from `TenantContext` (populated by `JwtAuthenticationFilter` from the signed JWT) — never from request input. Every endpoint is gated by `@PreAuthorize("@menuAuthz.canAccess('payment-approval-box')")` on `ApprovalBoxController`.

---

## 0. Approval Request (fan-out — write side, sibling modules)

Approval Box does not create approvals; the sibling Payment modules do, at submission time, via the shared `ApprovalBoxFanoutService.fanout(...)`:

1. Build an `EXPT_RESOL_MSTR` row: `APPR_TYPE_CD = "00"` (Pending), `APPR_DV` = the payment type code (`21`/`31`/`32`/`33`/`34`/`35`), draft date/time/user from `TenantContext`, `DOC_NM = ApprDv.slug()`, amount stored in the currency-specific column (USD → `TOT_PAY_USD_AMT`; VND/blank → `TOT_PAY_KHR_AMT` + `TOT_PAY_VND_AMT`), `DEL_YN = "N"`.
2. `DOC_NO` is allocated by `nextDocNo(bizKey, now)` → `VO_<yyMMdd>_<NNN>` (per-tenant, per-day sequence; the caller's supplied doc number is **overridden**).
3. Insert one `EXPT_RESOL_APPRVR_INFM` row per approver in line order: line 1 → `APPRVR_STTS = "P"` (Pending, their turn), lines 2..N → `"W"` (Waiting).
4. Fire `notificationService.notifyApprovalRequested(...)` to the whole chain (best-effort, isolated transaction).

`ApprDv` codes: `21` Payroll · `31` Expense · `32` Vendor · `33` Corporate Card · `34` Overseas · `35` Internal Transfer.

---

## 1. Approval Box Search (`GET /api/v1/payment/approval-box`)

`ApprovalBoxService.list(...)` — `@Transactional(readOnly=true)`:

1. Validate `from_date` / `to_date` (`YYYYMMDD`, 8 chars, parseable) and the period (`from ≤ to`) → `WBE_VALIDATION` on failure.
2. Validate `appr_type_cd` ∈ {`00`,`01`,`02`,`03`} or null (= all tabs).
3. Build the keyword pattern from `srch_wd` (escape `% _ !`, wrap `%…%`, lowercase). Blank → null (no keyword predicate).
4. Clamp page size to `[1..100]`, default 15; clamp page to `≥ 0`. Build `Sort` from the sort whitelist (`ApprovalBoxSortField`) + direction, defaulting to `REG_DTM DESC, APPR_SEQ_NO DESC`.
5. `masterRepository.search(...)` returns a `Page<ApprovalBoxProjection>`. The JPQL filters on `bizKey`, `delYn='N'`, `apprDv IN ('21','31','32','33','34','35')`, `draftDate BETWEEN :fromDate AND :toDate`, optional `apprTypeCd`, optional keyword `LIKE` over `docNm|titl|docNo`, and the **row-visibility** predicate: `isAdmin = TRUE OR regId = :userId OR EXISTS(approver row with apprvrId = :userId)`. Two correlated subqueries supply the approver total count and the approved (`'A'`) count.
6. `masterRepository.countTabs(...)` runs the same scope **minus** the `apprTypeCd` predicate, so all four chips reflect one date/keyword/visibility combination at once (`pending`, `inProgress`, `approved`, `rejected`). Null → `ApprovalBoxTabCounts.empty()`.
7. `mapProjections(...)` batch-fetches the currency (`COALESCE(OVRS_PYMN_SUMR.CCY_CD, SCDUL_PYMN_SUMR.CCY_CD)`, default `VND`) and the file name (`COALESCE(SCDUL_PYMN_SUMR.ORG_FILE_NM, OVRS_PYMN_SUMR.ORG_FILE_NM)`) for the page slice, loads each row's approver chain, picks the amount column by currency, and derives the wire `appr_stts` from `APPR_TYPE_CD` via `deriveApprStts` (`00→P`, `03→I`, `01→C`, `02→R`).
8. Assemble `ApprovalBoxListResponse(list, totalCount, tabCounts, pageNumber, pageSize, totalPages)`.

Empty search → empty `list`, `totalCount=0`, `totalPages=0`, all tab counts 0.

---

## 2. Approval Detail View

There is no single "detail" endpoint on this controller. The frontend opens the Approval Details dialog from the Document-No link and assembles it from:

- `GET .../{apprSeqNo}/approvers` → `ApprovalBoxService.approvers(...)` returns the ordered chain (`ApprovalBoxApprover[]` — line no, id, name, status, action timestamp) for the dialog's approver column.
- the row already in the list slice (title, content, currency, total amount, scheduled date, requestor, file name, status).
- the per-line transactions, fetched lazily by the frontend from the originating module's own detail endpoint (Internal Transfer / Vendor / Expense / Payroll / Overseas / Card), dispatched by `typeKey`.

The legacy detail/approve/reject popup `comm_0195_001` is what the approver chain + approve/reject footer descend from.

---

## 3. Approval Action (`POST .../{apprSeqNo}/approve`)

`ApprovalBoxService.approve(...)` — `@Transactional`:

1. `loadForAction`: validate `apprSeqNo`; resolve `bizKey`/`userId` (null → `WBE9999`); load the master by `(bizKey, apprSeqNo)` else `WBE2050`; reject if master is already finalised (`APPR_TYPE_CD ∈ {01,02}`) → `WBE2053`; load the caller's approver row by `(bizKey, apprSeqNo, userId)` else `WBE2051`; require that row's `APPRVR_STTS = 'P'` (it is the caller's turn) else `WBE2052`.
2. `applyApprove`: flip the caller's approver row `P → A`, stamp `APPR_DTM` + `MOD_ID`/`MOD_DTM` (UTC `yyyyMMddHHmmss`), save.
3. Count remaining `P` and `W` approver rows. **If both are 0** → master `APPR_TYPE_CD = '01'` (Approved). **Else** → master `APPR_TYPE_CD = '03'` (In Progress) and `promoteNextWaiting` flips the first `W` row to `P`.
4. Save the master.
5. **Source write-back:** `APPR_STTS = 'C'` if fully approved, else `'I'`, routed to `OVRS_PYMN_SUMR` when `APPR_DV = '34'` (Overseas) else `SCDUL_PYMN_SUMR` (`updateApprStts`).
6. Recompute `totalCount` (chain size) and `approvedCount` (count of `'A'`).
7. **Audit:** `auditLogService.append(...)` with `WSVC_ID = "approval-box"`, ref `EXPT_RESOL_MSTR`/`apprSeqNo`, detail key `audit.approval_box.approve.detail` (one row per locale, `REQUIRES_NEW`).
8. **Notification:** `notifyApprovalDecision(true, ...)` to the resolved recipients (chain members in {P,A,R} + business admins, minus the actor and the requester) — best-effort, isolated.
9. Return `ApprovalBoxActionResponse(apprSeqNo, deriveApprStts(apprTypeCd), approvedCount, totalCount)`.

No `DOC_NO` is generated on approve — the document number is allocated only at fan-out time.

---

## 4. Reject Action (`POST .../{apprSeqNo}/reject`)

`ApprovalBoxService.reject(...)` — `@Transactional`. Same load/guards as approve, then `applyReject`:

1. Caller's approver row `P → R`, stamp `APPR_DTM` + mod columns.
2. `approverRepository.stopDownstream(...)` — single UPDATE flips every `W` row to `S` (Stopped). Downstream approvers can no longer act.
3. Master `APPR_TYPE_CD = '02'` (Rejected).
4. Source write-back `APPR_STTS = 'X'`.
5. Recompute counts, audit (`audit.approval_box.reject.detail`), `notifyApprovalDecision(false, ...)`, return the action response (`appr_stts = "R"`).

---

## 5. Status Update — the two-level state model

| Level | Column | Values | Set by |
|---|---|---|---|
| Document (tab discriminator) | `EXPT_RESOL_MSTR.APPR_TYPE_CD` | `00` Pending · `03` In Progress · `01` Approved · `02` Rejected | fan-out (`00`), approve (`01`/`03`), reject (`02`), withdraw/change-line (`00`) |
| Per-approver | `EXPT_RESOL_APPRVR_INFM.APPRVR_STTS` | `P` Pending(turn) · `A` Approved · `R` Rejected · `W` Waiting · `S` Stopped | fan-out (`P`/`W`), approve (`A`, promote next `W→P`), reject (`R`, `W→S`) |

The wire `appr_stts` shown to the user is derived from `APPR_TYPE_CD` (`deriveApprStts`: `00→P`, `03→I`, `01→C`, `02→R`) — **not** from the legacy `APPR_STS` column, which is an internal field (every live DEV row carries `'2'`).

### Withdraw (`DELETE .../{apprSeqNo}/withdraw`)
`withdraw(...)` → `fanoutService.withdrawApproval(...)`: guards the master is Pending/In Progress (`APPR_TYPE_CD ∈ {00,03}`, else `WBE_VALIDATION`), deletes all approver rows then the master; then `clearSourceApprStamp(...)` nulls the source batch's `APPR_SEQ_NO`/`APPR_STTS` (returns the batch to Draft), routed to OVRS/SCDUL by `APPR_DV`; audit `audit.approval_box.withdraw.detail`.

### Change approval line (`PUT .../{apprSeqNo}/approval-line`)
`changeApprovalLine(apprSeqNo, apprLineNo)` → `fanoutService.changeApprovalLine(...)`: guards Pending/In Progress, loads the picked approval-line template, deletes the existing chain and rebuilds it (line 1 → `P`, rest → `W`), resets master `APPR_TYPE_CD = '00'`; audit `audit.approval_box.change_approval_line.detail`.

### Bulk approve (`POST .../bulk-approve`)
`bulkApprove(List<apprSeqNos>)` (not `@Transactional` — each row approves independently): validates 1..100 ids (`WBE2054`), loops calling `approve(...)` per id, collecting per-row failures into `Failure(apprSeqNo, code, message)`. Returns `{ successes[], failures[] }`. (See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the in-bean self-invocation note.)

---

## 6. Persistence

| Table | Write |
|---|---|
| `EXPT_RESOL_MSTR` | `APPR_TYPE_CD`, `MOD_ID`/`MOD_DTM` on approve/reject; full row on fan-out; deleted on withdraw |
| `EXPT_RESOL_APPRVR_INFM` | `APPRVR_STTS` + `APPR_DTM` + mod cols on approve/reject; `W→S` cascade on reject; full chain insert on fan-out; deleted/rebuilt on withdraw/change-line |
| `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` | `APPR_STTS` write-back (`C`/`I`/`X`) on approve/reject; `APPR_SEQ_NO` + `APPR_STTS` nulled on withdraw |
| `AUDIT_LOGS_INFM` | one row per locale per mutation, `WSVC_ID = "approval-box"`, via `AuditLogService` (`REQUIRES_NEW`) |

Notification rows are written best-effort by `NotificationService` in an isolated transaction so a notification failure never rolls back the approval.

---

## 7. UI Refresh

The backend returns the post-mutation status + counts in `ApprovalBoxActionResponse` so the dialog can reflect the new state immediately, but the canonical refresh is a re-fetch: after a successful approve/reject/withdraw/change-line the frontend closes the dialog and re-runs `GET /api/v1/payment/approval-box` with the current filters, which re-derives the tab counts and the row's status. There is no server push; the list is the source of truth on refresh. (Frontend mechanics — stable-data latch, run-id cancellation — are in the [frontend api-consumption doc](../../../../../wecms-web/docs/features/payment/approval-box/api-consumption.md).)

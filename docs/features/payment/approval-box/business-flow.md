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
2. `DOC_NO` is allocated through the single key-gen funnel `LegacyFunctionRepository.nextDocNo` → `wb_get_next_val('expt_resol_mstr','doc_no',…)` (V140 branch #39, env-set-driven) → `VO_<yyMMdd>_<NNN>` (per-tenant, per-day sequence; the caller's supplied doc number is **overridden**). `APPR_SEQ_NO` is allocated through the same funnel via `LegacyFunctionRepository.nextApprSeqNo` → the global `sq_appr_seq_no` sequence (V140 branch #1, V148).
3. Insert one `EXPT_RESOL_APPRVR_INFM` row per approver in line order: line 1 → `APPRVR_STTS = "P"` (Pending, their turn), lines 2..N → `"W"` (Waiting).
4. Fire the lifecycle notification via `ApprovalNotificationService` (BLTB tables — `BLTB_COMNT_INFM` / `BLTB_NOTI_INFM` / `BLTB_NOTI_CONT_INFM`) to the whole chain (best-effort, isolated transaction). There is no refactor-only `NOTIFICATION` table — it was removed 2026-06-10 (memory `project_notification_module_removed.md`).

`ApprDv` codes: `21` Payroll · `31` Expense · `32` Vendor · `33` Corporate Card · `34` Overseas · `35` Internal Transfer.

---

## 1. Approval Box Search (`GET /api/v1/payment/approval-box`)

`ApprovalBoxService.list(...)` — `@Transactional(readOnly=true)`:

1. Validate `from_date` / `to_date` (`YYYYMMDD`, 8 chars, parseable) and the period (`from ≤ to`) → `WBE_VALIDATION` on failure.
2. Validate `appr_type_cd` ∈ {`00`,`01`,`02`,`03`} or null (= all tabs).
3. Build the keyword pattern from `srch_wd` (escape `% _ !`, wrap `%…%`, lowercase). Blank → null (no keyword predicate).
4. Clamp page size to `[1..100]`, default 15; clamp page to `≥ 0`. Build `Sort` from the sort whitelist (`ApprovalBoxSortField`) + direction, defaulting to `REG_DTM DESC, APPR_SEQ_NO DESC`.
5. `masterRepository.search(...)` returns a `Page<ApprovalBoxProjection>`. The JPQL filters on `bizKey`, `delYn='N'`, `apprDv IN ('21','31','32','33','34','35')`, `draftDate BETWEEN :fromDate AND :toDate`, optional `apprTypeCd`, optional keyword `LIKE` over `docNm|titl|docNo`, and the **row-visibility** predicate (see [§8.3](#83-uploader-visibility-flow)): `isAdmin = TRUE OR regId = :userId OR EXISTS(approver) OR EXISTS(SCDUL uploader) OR EXISTS(OVRS uploader) OR EXISTS(CARD uploader)`. Two correlated subqueries supply the approver total count and the approved (`'A'`) count.
6. `masterRepository.countTabs(...)` runs the same scope **minus** the `apprTypeCd` predicate (identical visibility predicate), so all four chips reflect one date/keyword/visibility combination at once (`pending`, `inProgress`, `approved`, `rejected`). Null → `ApprovalBoxTabCounts.empty()`.
7. `mapProjections(...)` batch-fetches, for the page slice: the currency (`OVRS_PYMN_SUMR.CCY_CD` ∪ `SCDUL_PYMN_SUMR.CCY_CD` ∪ `CARD_TRNS_HSTR.CCY_CD`, default `VND`), the file name (`COALESCE(SCDUL_PYMN_SUMR.ORG_FILE_NM, OVRS_PYMN_SUMR.ORG_FILE_NM)`), and the card amount basis (`SUM(CARD_TRNS_HSTR.TRSC_AMT)` keyed by `apprSeqNo`). It then loads each row's approver chain, resolves the amount via the shared `resolveAmount` (card-aware — see [§8.2](#82-card-payment-amount-flow)), and derives the wire `appr_stts` from `APPR_TYPE_CD` via `deriveApprStts` (`00→P`, `03→I`, `01→C`, `02→R`).
8. Compute the **footer totals** (`computeFooterTotals` — see [§8.1](#81-footer-aggregate-flow)): document count = sum of the four tab counts (tab-unfiltered), and per-currency USD/VND/KRW amount sums over the full tab-unfiltered result set.
9. Assemble `ApprovalBoxListResponse(list, totalCount, tabCounts, footerTotals, pageNumber, pageSize, totalPages)`.

Empty search → empty `list`, `totalCount=0`, `totalPages=0`, all tab counts 0, `footerTotals` zeroed.

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
5. **No source write-back.** `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` have no `APPR_STTS` column in the legacy JEX schema, so nothing is mirrored onto the batch on approve (the legacy `updateApprStts` mirror was removed 2026-06-10). The Overseas / Scheduled list+detail pages derive status on read from the joined `EXPT_RESOL_MSTR.APPR_TYPE_CD`.
6. Recompute `totalCount` (chain size) and `approvedCount` (count of `'A'`).
7. **Audit:** `auditLogService.append(...)` with `WSVC_ID = "approval-box"`, ref `EXPT_RESOL_MSTR`/`apprSeqNo`, detail key `audit.approval_box.approve.detail` (one row per locale, `REQUIRES_NEW`).
8. **Notification:** `approvalNotificationService.notifyApprovalAction(apprSeqNo, apprDv, comntTaskCd)` — `comntTaskCd = "R003"` (COMPLETE) when fully approved, `"R004"` (APPROVED step) otherwise — writes a `BLTB_COMNT_INFM` remark + `BLTB_NOTI_INFM`/`BLTB_NOTI_CONT_INFM` to admins + acted approvers. Best-effort, isolated.
9. Return `ApprovalBoxActionResponse(apprSeqNo, deriveApprStts(apprTypeCd), approvedCount, totalCount)`.

> On full approval the master also stamps `PAY_END_YN='Y'` + `PAY_END_DTM` + `PAY_END_NM` (legacy `comm_0192_010_u001` parity).

No `DOC_NO` is generated on approve — the document number is allocated only at fan-out time.

---

## 4. Reject Action (`POST .../{apprSeqNo}/reject`)

`ApprovalBoxService.reject(...)` — `@Transactional`. Same load/guards as approve, then `applyReject`:

1. Caller's approver row `P → R`, stamp `APPR_DTM` + mod columns.
2. `approverRepository.stopDownstream(...)` — single UPDATE flips every `W` row to `S` (Stopped). Downstream approvers can no longer act.
3. Master `APPR_TYPE_CD = '02'` (Rejected).
4. No source write-back (same reason as approve §3.5 — the batch tables carry no `APPR_STTS` column).
5. Recompute counts, audit (`audit.approval_box.reject.detail`), `approvalNotificationService.notifyApprovalAction(apprSeqNo, apprDv, "R002")` (REJECT remark + BLTB notification), return the action response (`appr_stts = "R"`). On the rejecting vote the master also stamps `PAY_END_*` when every approver has now voted (legacy `comm_0192_010_u002` parity).

---

## 5. Status Update — the two-level state model

| Level | Column | Values | Set by |
|---|---|---|---|
| Document (tab discriminator) | `EXPT_RESOL_MSTR.APPR_TYPE_CD` | `00` Pending · `03` In Progress · `01` Approved · `02` Rejected | fan-out (`00`), approve (`01`/`03`), reject (`02`), withdraw/change-line (`00`) |
| Per-approver | `EXPT_RESOL_APPRVR_INFM.APPRVR_STTS` | `P` Pending(turn) · `A` Approved · `R` Rejected · `W` Waiting · `S` Stopped | fan-out (`P`/`W`), approve (`A`, promote next `W→P`), reject (`R`, `W→S`) |

The wire `appr_stts` shown to the user is derived from `APPR_TYPE_CD` (`deriveApprStts`: `00→P`, `03→I`, `01→C`, `02→R`) — **not** from the legacy `APPR_STS` column, which is an internal field (every live DEV row carries `'2'`).

### Withdraw (`DELETE .../{apprSeqNo}/withdraw`)
`withdraw(...)` → `fanoutService.withdrawApproval(...)`: guards the master is Pending/In Progress (`APPR_TYPE_CD ∈ {00,03}`, else `WBE_VALIDATION`), deletes all approver rows then the master; then `clearApprSeqNo(...)` nulls the source batch's `APPR_SEQ_NO` (returns the batch to Draft — `apprSeqNo IS NULL` reads as Draft on the source list), routed to OVRS/SCDUL by `APPR_DV`; audit `audit.approval_box.withdraw.detail`.

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
| `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` | `APPR_SEQ_NO` nulled on withdraw only (`clearApprSeqNo`). **No `APPR_STTS` write** — the column does not exist in the legacy JEX schema; status is derived on read from `EXPT_RESOL_MSTR.APPR_TYPE_CD`. |
| `AUDIT_LOGS_INFM` | one row per locale per mutation, `WSVC_ID = "approval-box"`, via `AuditLogService` (`REQUIRES_NEW`) |

These tables are **read-only** on the Approval Box list path (`CARD_TRNS_HSTR`, `SCDUL_PYMN_SUMR`, `OVRS_PYMN_SUMR` supply currency / file name / card amount / uploader visibility — see §8). The list endpoint writes nothing.

Notification/bulletin rows are written best-effort by `ApprovalNotificationService` (legacy `BLTB_*` tables) in an isolated transaction so a notification failure never rolls back the approval.

---

## 7. UI Refresh

The backend returns the post-mutation status + counts in `ApprovalBoxActionResponse` so the dialog can reflect the new state immediately, but the canonical refresh is a re-fetch: after a successful approve/reject/withdraw/change-line the frontend closes the dialog and re-runs `GET /api/v1/payment/approval-box` with the current filters, which re-derives the tab counts, the footer totals, and the row's status. There is no server push; the list is the source of truth on refresh. (Frontend mechanics — stable-data latch, run-id cancellation — are in the [frontend api-consumption doc](../../../../../wecms-web/docs/features/payment/approval-box/api-consumption.md).)

---

## 8. List-page aggregate + parity flows (2026-06-16)

These three flows back the main-page footer, the card amount, and non-admin visibility. All three are **read-only** and resolved inside the `GET /approval-box` path. Legacy parity references: `IDO.EXPT_RESOL_MSTR_R003.xml`, `IDO.EXPT_RESOL_MSTR_R003_CNT.xml`, `resl_0001_001_r001_act.jsp:116-162`. See [refactor-gap-analysis.md §G1/G3/G5](refactor-gap-analysis.md#post-sit-verification--g1g5-findings-2026-06-16).

### 8.1 Footer aggregate flow (G1 + G4)

Legacy `resl_0001_001` renders a footer bar — `N document(s) · Total USD · Total VND · Total KRW` — summed over the **tab-unfiltered** full result set (the count IDO is fed the no-tab dynamic). The refactor reproduces this server-side:

1. `computeFooterTotals(bizKey, userId, isAdmin, fromDt, toDt, keywordPattern, footerDocumentCount)` runs `ExptResolMstrRepository.findAmountBasisForTotals(...)` — the **same WHERE as `countTabs`** (no `apprTypeCd` predicate, full visibility predicate), unpaged — returning `(apprSeqNo, apprDv, totPayKhrAmt, totPayUsdAmt, totPayVndAmt)` per row.
2. It batch-fetches the currency map (OVRS ∪ SCDUL ∪ CARD `CCY_CD`) and the card-amount map (`SUM(CARD_TRNS_HSTR.TRSC_AMT)`) for those `apprSeqNo`s.
3. For each row it resolves currency (`resolveCurrency`) + amount (`resolveAmount`, card-aware) — the **identical helpers** `toRow` uses, so the footer never drifts from the on-screen rows — and buckets the amount into USD / VND / KRW (other currencies fall outside the three rendered buckets, as in legacy).
4. **Document count** = `pending + inProgress + approved + rejected` (the four tab counts). `APPR_TYPE_CD` is NOT NULL ∈ {00,01,02,03} on DEV, so this equals the tab-unfiltered row count with no extra query.
5. Returned as `ApprovalBoxFooterTotals` on `footer_totals`. `total_count` (tab-filtered) is untouched and still drives pagination.

### 8.2 Card payment amount flow (G5)

For `APPR_DV='33'` (Corporate Card) the `EXPT_RESOL_MSTR` amount columns are not reliably populated (the two newest DEV card rows carry `TOT_PAY_KHR_AMT = 0`). Legacy `IDO.EXPT_RESOL_MSTR_R003.xml:62-65` and `_R003_CNT.xml:49-52` therefore use `SUM(CARD_TRNS_HSTR.TRSC_AMT)` for card rows. The refactor mirrors this in `resolveAmount`:

```
APPR_DV='33'  → cardAmtByApprSeqNo.get(apprSeqNo)   (SUM(CARD_TRNS_HSTR.TRSC_AMT), KHR fallback if absent)
USD           → TOT_PAY_USD_AMT (zero-aware) else TOT_PAY_KHR_AMT
VND           → TOT_PAY_VND_AMT (zero-aware) else TOT_PAY_KHR_AMT
otherwise     → TOT_PAY_KHR_AMT
```

The card sum is batch-fetched once per page (and once for the footer basis) via `cardTrnsHstrRepository.sumTrscAmtByBizKeyAndApprSeqNos` — no N+1. The same `resolveAmount` output feeds the row, the footer (§8.1), and the Excel export, so all three agree.

### 8.3 Uploader visibility flow (G3)

A non-admin sees a document when they are the requester, an assigned approver, **or the uploader of the underlying payment batch**. The upload event (which stamps `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` / `CARD_TRNS_HSTR.REG_ID`) can be a different user than the approval requester (`EXPT_RESOL_MSTR.REG_ID`) — confirmed on live DEV (~36/287 documents diverge). The visibility predicate, applied identically in `search`, `countTabs`, and `findAmountBasisForTotals`:

```
:isAdmin = TRUE
  OR erm.regId = :userId                                              -- requester
  OR EXISTS (ExptResolApprvrInfm a   WHERE … AND a.apprvrId = :userId) -- assigned approver
  OR EXISTS (ScdulPymnSumr sps       WHERE … AND sps.regId  = :userId) -- scheduled-batch uploader
  OR EXISTS (OvrsPymnSumr  ops       WHERE … AND ops.regId  = :userId) -- overseas-batch uploader
  OR EXISTS (CardTrnsHstr  cth       WHERE … AND cth.regId  = :userId) -- card-transaction uploader
```

Admin (`isAdmin = TRUE`) short-circuits the whole predicate and sees every row in the tenant. Because list rows, tab counts, and footer totals share this one predicate, an uploader sees a consistent row set, chip counts, and footer across the page.

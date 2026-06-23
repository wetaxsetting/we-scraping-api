# Approval Line — Refactor Gap Analysis (Final Parity Review)

Legacy (`comm_0222_*` under `WECMS DEV/`) vs the implemented refactor. Status vocabulary:
**MATCH** (behaviour identical) · **APPROVED DEVIATION** (intentional, signed-off) · **PARTIAL** ·
**MISSING**. JEX-side audit: [docs/jex/features/approval-line/refactor-audit.md](../../../../../docs/jex/features/approval-line/refactor-audit.md).

---

## Refactor Status: 100% Complete

The Approval Line refactor is **functionally complete and at parity** with the legacy line-configuration
feature, with the documented deviations below (all intentional and signed off). Backend + frontend are
both wired end-to-end (no mock fallback), the 12-case service test is green, and SIT/UAT is verified.
All three tracked gaps (AL-GAP-01 / AL-GAP-02 / AL-GAP-03) are **RESOLVED**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Search** | `BIZ_KEY` + name ILIKE; sort; no pagination (`comm_0222_001_r001`) | `findByBizKey(+Sort)` / `…ApprLineNmContainingIgnoreCase`; whitelist sort; load-all | **MATCH** |
| **Create** | master + ≤5 detail rows, one tx (`comm_0222_002_c001`) | `ApprovalLineService.create`, one `@Transactional` | **MATCH** |
| **Read detail** | line + ordered chain (`comm_0222_003_r001`) | `ApprovalLineService.detail` | **MATCH** |
| **Update** | HashSet diff-sync + `i+1` renumber (`comm_0222_003_u001`) | identical diff-sync, one tx | **MATCH** |
| **Delete** | detail-then-master, hard, no in-use guard (`comm_0222_001_d001`) | identical | **MATCH** |
| **Approver management** | add/remove inside popup | add (`AddApproverPopover`) / remove in dialog | **MATCH** |
| **Reordering (AL-GAP-01)** | jQuery-UI `.sortable()`, submit in DOM order | native HTML5 DnD, submit in array order | **MATCH** (see below) |
| **Display-order persistence** | `disp_ord = i+1` on update | `disp_ord = i+1` on **create + update** | **APPROVED DEVIATION** (OQ-4 — stricter on Create) |
| **Approval-sequence persistence** | single `APPRVR_DISP_ORD` (= order = sequence) | same single column, server-assigned | **MATCH** |
| **Validation — 5 cap** | `WBW1164` at action layer | bean `@Size(max=5)` + service guard → `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` | **MATCH** (code re-coded per naming rule) |
| **Validation — required name** | `validateRequired` | `WBE_APPROVAL_LINE_NAME_BLANK` | **MATCH** |
| **Validation — non-empty list** | `validateRecord` | `WBE_APPROVAL_LINE_EMPTY_APPROVERS` | **MATCH** |
| **Validation — duplicate approver** | implicit (HashSet + PK) | explicit `WBE_APPROVAL_LINE_DUP_APPROVER` | **APPROVED DEVIATION** (fail-fast) |
| **Validation — approver in tenant** | none (trusts picker) | `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` | **APPROVED DEVIATION** (added defence) |
| **Workflow integration / reusability** | line attachable to payments | line attachable; consumers snapshot approvers | **MATCH** (consumer-side, out of scope) |
| **`user_img_url`** | `szon_…act` URL or placeholder png | slug `/api/v1/users/{id}/image` or `null` | **APPROVED DEVIATION** (OQ-1; streaming endpoint deferred) |
| **Audit logging** | none | none | **MATCH** (OQ-6 — parity, deliberate divergence from sibling features) |
| **Authorization** | `login` + `NORM_USER_ONLY_YN` + `WTA_USER_ALLOW_YN=N`, no admin gate | JWT + `@menuAuthz.canAccess('settings-core-approval-line')`, no admin gate | **MATCH** (menu-grant gate is the refactor-wide F-3 mechanism) |
| **Pagination** | disabled / load-all | load-all | **MATCH** |
| **View Setting** | no list-icon toolbar | not adopted | **MATCH** |

No row is **PARTIAL** or **MISSING**.

---

## Closed-gap evidence

### AL-GAP-01 — Drag-and-drop reorder
**RESOLVED.** Implemented as native HTML5 drag-and-drop on the approver rows of `<ApprovalLineDialog>`
(`draggable` `<tr>` + `onDragStart`/`onDragOver`/`onDrop`/`onDragEnd`, `dragIndex` state,
`GripVertical` visual handle, `opacity-50` while dragging). The dragged array order is the
`approvers[]` payload order; the backend re-numbers `apprvr_disp_ord` `1..N` from it on save, and the
read path returns approvers ordered `APPRVR_DISP_ORD ASC` — so **order persists across save/reopen**
(SIT case O4). This matches the legacy jQuery-UI `.sortable()` contract (submit in DOM order, server
renumbers). Evidence: [wecms-web/docs/features/settings/approval-line/ui-specification.md §Drag-and-drop](../../../../../wecms-web/docs/features/settings/approval-line/ui-specification.md#drag-and-drop-reorder) + service `update`/`create` `apprvrDispOrd = i + 1`.

### AL-GAP-02 — Test coverage
**RESOLVED.** `ApprovalLineServiceTest` ships 12 Mockito cases covering create (happy + 5 guards),
diff-sync update (insert/update/delete/resequence), update guards, and delete (order + not-found) — all
green. See [test-scenarios.md §Automated coverage map](test-scenarios.md#automated-coverage-map-approvallineservicetest--12-cases-all-green).
Residual: no controller/integration (`@SpringBootTest`/MockMvc) test yet — non-blocking, listed below.

### AL-GAP-03 — Auth gate
**RESOLVED.** The controller carries class-level
`@PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")` (F-3 rollout 2026-06-06); the
entitlement is registered in the menu catalogue; the frontend Settings nav slug is
`settings-core-approval-line`. The endpoint is covered by the Phase-F `EndpointAuthorizationCoverageTest`
guard (no un-annotated business handler may ship).

---

## Authorization parity

Live code: class-level `@menuAuthz` gate (above) — **authoritative**. The `ApprovalLineController`
class-level Javadoc now fully documents this gate (it already references the F-3 `@menuAuthz` rollout
and no longer reads "OQ-5 no `@PreAuthorize`"). One **stale** note remains and is flagged for
non-blocking cleanup (doc, not code):

1. `wecms-api/CLAUDE.md` Feature-Status row still reads "OQ-5 no `@PreAuthorize` admin gate" (and still
   cites the schema as "V90" + the key-gen branch as "V91" — both stale; real schema is V6 / V7 and the
   `wb_get_next_val` branch lives in the consolidated V140).

This predates F-3. Recommend updating the CLAUDE.md row to reference the live `@menuAuthz` gate and the
correct migration numbers. This is a doc cleanup only — the runtime gate is correct.

---

## Test coverage (residual, non-blocking)

| Item | Status |
|---|---|
| Service unit tests (12 cases) | ✅ present, green |
| Controller / MockMvc integration test | ⏳ not present — recommended next stabilisation pass (assert envelope shape, `@menuAuthz` 403, path-var validation) |

---

## Open questions — final status

| OQ | Topic | Status |
|---|---|---|
| OQ-1 | `user_img_url` endpoint shape | Resolved — slug `/api/v1/users/{id}/image`; streaming endpoint itself deferred (FE tolerates null/404) |
| OQ-2 | Missing JEX feature doc | Resolved — `docs/jex/features/approval-line/` (10-file set) now exists |
| OQ-3 | Pagination | Resolved — no pagination (load-all parity) |
| OQ-4 | `disp_ord` normalisation on Create | Resolved — normalise on Create + Update (APPROVED DEVIATION) |
| OQ-5 | Admin-only access | Resolved — no admin gate; menu-grant gate via `@menuAuthz` (live, supersedes the "no `@PreAuthorize`" note) |
| OQ-6 | Audit on refactor side | Resolved — **no audit** (parity; deliberate divergence from `firm-banking-settings`/`contacts`) |
| OQ-7 | `appr_line_no` generator | Resolved — reuse the `appr_line_infm.appr_line_no` `wb_get_next_val` branch (consolidated V140 function) |

No open questions block completion. The only follow-ups are the two stale-comment cleanups and the
optional controller integration test.

---

## Conflicts with other docs (for cleanup, not blocking)

- `docs/jex/business-rules/approval.md` cites approval-line config under `comm_0194_*`/`comm_0195_*` and describes "parallel and sequential" flows. The live config screen is `comm_0222_*`, and the data model has only `APPRVR_DISP_ORD` (sequential only — no parallel flag). JEX-side doc cleanup, out of scope for this refactor.
- The "OQ-5 no `@PreAuthorize`" notes (above) — controller Javadoc + `wecms-api/CLAUDE.md`.

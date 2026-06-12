# Approval Line — Business Flow

End-to-end request flow for each operation, backend-first. JEX file:line citations point at the
read-only source of truth under `WECMS DEV/`. Frontend mirror:
[wecms-web/docs/features/settings/approval-line/screen-flow.md](../../../../../wecms-web/docs/features/settings/approval-line/screen-flow.md).

---

## High-level lifecycle

```
User opens Settings → Approval Line panel
        │
        ▼
  List (GET) ──────────────► grid of lines, each row carries its approver chain inline
        │
        ├── "+ Add Approval line" ─► Add dialog ─► pick 1–5 approvers ─► drag-reorder ─► Save (POST)
        │
        ├── row "Edit" ───────────► Edit dialog ─► GET detail ─► add/remove/drag-reorder ─► Save (PUT, diff-sync)
        │
        └── row "Delete" ─────────► confirm ─► Delete (DELETE, hard delete master + detail)
        │
        ▼
  Line is now available to attach to a payment / transfer request (consumer features, out of scope)
```

The required workflow the PRD calls out —
`Create → Add Approvers → Reorder → Validation → Save → available for reuse` — maps onto the
**Create** and **Update** flows below; "available for reuse" is the consumer side (Payment / Internal
Transfer / etc.), which snapshots the approver list at request-creation time and does **not** hold a
live FK back to `APPR_LINE_INFM` (this is why Delete needs no in-use guard — see
[§ Delete](#delete-flow)).

---

## List flow (GET `/api/v1/settings/approval-line`)

```
ApprovalLineController.list(q, sort_field, sort_dir)
  → ApprovalLineService.list(...)            @Transactional(readOnly = true)
      1. requireBizKey()                       — TenantContext.currentBizKey()
      2. buildSort(ApprovalLineSortField.parse(sortField), SortDirection.parse(sortDir))
      3. q blank? lineRepo.findByBizKey(bizKey, sort)
                : lineRepo.findByBizKeyAndApprLineNmContainingIgnoreCase(bizKey, q.trim(), sort)
      4. batch-load approvers for the page's appr_line_no set
           → approverRepo.findAllByBizKeyAndApprLineNoInOrderByApprLineNoAscApprvrDispOrdAsc
      5. resolveImageUrls(bizKey, all apprvr_ids)  — one USER_BUSI_CONN_INFM lookup
      6. group approvers per line, map each to ApprovalLineRow (approvers ordered disp_ord ASC)
  → ApiResponse.ok(new ApprovalLineListResponse(rows.size(), rows))
```

- **Tenant filter** is always `BIZ_KEY = currentBizKey()` (legacy `comm_0222_001_r001_act.jsp:36-49`); the wire never carries `biz_key`.
- **Name search** (`q`) is an optional case-insensitive `LIKE` (`LOWER(...) LIKE LOWER('%'||?||'%')`), mirroring the legacy ILIKE clause. Empty/blank → no clause.
- **Default sort** `REG_DTM DESC, APPR_LINE_NO DESC`; whitelisted fields `REG_DTM` / `APPR_LINE_NM` / `MOD_DTM` (see [interface-specification.md §Sort](interface-specification.md#sort)).
- **No pagination** — legacy `comm_0222_001_r001_act.jsp:52-62` comments out the `PageInfo` branch ("load all data"). The refactor returns the full tenant set; `total_count = rows.size()`. The frontend filters by name client-side.
- **Inline approver chain** — legacy aggregates approvers via `json_agg(... ORDER BY APPRVR_DISP_ORD ASC)` in `IDO.APPR_LINE_INFM_DETL_R002.xml`; the refactor reproduces the same nested shape by batch-loading the detail rows and grouping them in Java.

---

## Detail flow (GET `/api/v1/settings/approval-line/{appr_line_no}`)

```
ApprovalLineController.detail(apprLineNo)            @Positive
  → ApprovalLineService.detail(apprLineNo)            @Transactional(readOnly = true)
      1. requireBizKey()
      2. lineRepo.findById(new ApprovalLineId(bizKey, apprLineNo))
            → empty → BusinessException(WBE_APPROVAL_LINE_NOT_FOUND)
      3. approverRepo.findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, apprLineNo)
      4. resolveImageUrls(...) → toRow(...)
  → ApiResponse.ok(row)
```

Feeds the Edit dialog's pre-fill (the panel row only carries the primary approver + a count chip, so
the dialog fetches the full chain on open). Mirrors legacy `comm_0222_003_r001_act.jsp`.

---

## Create flow (POST `/api/v1/settings/approval-line`)

```
ApprovalLineController.create(@Valid ApprovalLineCreateRequest)
  → ApprovalLineService.create(req)                   @Transactional
      1. requireBizKey() · requireUserId() · utcNow14()
      2. requireApprLineNm(req.apprLineNm())           — trim; blank → WBE_APPROVAL_LINE_NAME_BLANK
      3. validateApproverList(req.approvers())
            empty       → WBE_APPROVAL_LINE_EMPTY_APPROVERS
            count > 5   → WBE_APPROVAL_LINE_TOO_MANY_APPROVERS
            dup id      → WBE_APPROVAL_LINE_DUP_APPROVER({id})
      4. verifyTenantLinks(bizKey, approvers)
            any id not active in USER_BUSI_CONN_INFM → WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT({id})
      5. apprLineNo = parseApprLineNo(legacyFnRepo.nextApprLineNo(bizKey))   — wb_get_next_val MAX+1
      6. save master (ApprovalLine) with reg/mod id+dtm from JWT user + now
      7. for (i, approver): apprvr_disp_ord = i + 1; posi/dept coalesced to ""; save detail row
  → return detail(apprLineNo)                          — re-read, returns the full row
```

Validation order matters: name → list shape → tenant links → key gen → persist. The 5-approver cap
mirrors legacy `comm_0222_002_c001_act.jsp:90` (`WBW1164`). `apprvr_posi` / `apprvr_dept` are written
as `""` (never null) when absent, matching legacy `comm_0222_003_u001_act.jsp:144-148`.

---

## Update flow (PUT `/api/v1/settings/approval-line/{appr_line_no}`) — diff-sync

This is the only complex flow. It reproduces legacy `comm_0222_003_u001_act.jsp:104-194` exactly,
inside one `@Transactional`.

```
ApprovalLineController.update(apprLineNo, @Valid ApprovalLineUpdateRequest)
  → ApprovalLineService.update(apprLineNo, req)        @Transactional
      1. same validation prelude as Create (name, list shape, tenant links)
      2. master = lineRepo.findById(new ApprovalLineId(bizKey, apprLineNo))
            → empty → WBE_APPROVAL_LINE_NOT_FOUND
         master.apprLineNm = trimmed name; master.modId/modDtm set   (dirty-checked on flush)
      3. existing = approverRepo.findAllBy…OrderByApprvrDispOrdAsc(bizKey, apprLineNo)
         existingById = Map<apprvrId, row>;  incomingIds = Set<apprvrId>
      4. for (i, incoming):
            dispOrd = i + 1                              ← re-number from array position
            existingById has id ? reuse managed row (UPDATE) : new row with reg id/dtm (INSERT)
            always set dispOrd, nm, posi/dept (coalesced ""), modId/modDtm → approverRepo.save(row)
      5. DELETE pass: every existingById entry whose id ∉ incomingIds
            → approverRepo.deleteById(new ApprovalLineApproverId(bizKey, apprLineNo, id))
  → return detail(apprLineNo)
```

**Set-difference by `apprvr_id`:** survivors are UPDATEd in place, net-new ids INSERTed, dropped ids
DELETEd. `apprvr_disp_ord` is fully re-derived from the request-array index (`i + 1`) — the client
never sends `apprvr_disp_ord`, so **reordering is expressed purely by the order of the `approvers`
array**. The drag-and-drop UI in the dialog reorders that array; the diff-sync re-numbers from it.

---

## Reorder (drag-and-drop) — where it lives

There is **no dedicated reorder endpoint**. Reorder is a frontend dialog affordance (native HTML5
drag-and-drop on the approver rows — see
[wecms-web/docs/features/settings/approval-line/ui-specification.md](../../../../../wecms-web/docs/features/settings/approval-line/ui-specification.md#drag-and-drop-reorder)).
The dragged order is the `approvers[]` array order in the Create/Update payload; the backend
re-numbers `apprvr_disp_ord` `1..N` from that array on save. Legacy used jQuery-UI `.sortable()` in
both popups (`comm_0222_002.ts:295-359`, `comm_0222_003.ts:489-517`) and submitted approvers in the
post-drag DOM order — the refactor matches this contract exactly (the wire shape is the order; the
server assigns `disp_ord`). This closes **AL-GAP-01** (see [refactor-gap-analysis.md](refactor-gap-analysis.md#al-gap-01-drag-and-drop-reorder)).

---

## Delete flow (DELETE `/api/v1/settings/approval-line/{appr_line_no}`)

```
ApprovalLineController.delete(apprLineNo)
  → ApprovalLineService.delete(apprLineNo)             @Transactional
      1. requireBizKey()
      2. lineRepo.existsById(new ApprovalLineId(bizKey, apprLineNo))
            → false → WBE_APPROVAL_LINE_NOT_FOUND
      3. approverRepo.deleteAllByBizKeyAndApprLineNo(bizKey, apprLineNo)   — bulk delete-by-FK (children)
      4. lineRepo.deleteById(new ApprovalLineId(bizKey, apprLineNo))      — master
  → ApiResponse.ok(null)
```

- **Hard delete** (no `DEL_YN` column anywhere in this feature). Children first, then master — mirrors legacy `comm_0222_001_d001_act.jsp:102-108`.
- **No in-use guard** — legacy has none (`comm_0222_001_d001_act.jsp` goes straight from the not-found check to deletion). Safe because consumer features snapshot the approver list at request time; deleting a line does not retroactively break an in-flight payment.
- **Not found vs wrong tenant** collapse into one `WBE_APPROVAL_LINE_NOT_FOUND` — tenant scope is implicit, so a row owned by another tenant is indistinguishable from "no row".

---

## Transaction & audit boundaries

- Reads (`list`, `detail`) → `@Transactional(readOnly = true)`. Mutations (`create`, `update`, `delete`) → `@Transactional`.
- **No audit-log writes.** `ApprovalLineService` has no `AuditLogService` dependency and writes no `AUDIT_LOGS_INFM` rows. This is **legacy parity** — none of the five `comm_0222_*` action JSPs import `AuditLogsInfmDAO` or write an audit row either (OQ-6, deliberate divergence from `firm-banking-settings` / `contacts`). See [functional-specification.md §Audit](functional-specification.md#audit--permissions).

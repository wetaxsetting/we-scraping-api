# Approval Line — Migration Guide (JEX → Spring + Next.js)

How each legacy JEX layer became its refactor counterpart. The legacy code under `WECMS DEV/` is the
read-only functional source of truth. Full side-by-side mapping:
[references/migration/approval-line.md](../../../references/migration/approval-line.md) ·
add-flow deep dive: [references/migration/jex-analysis/approval-line-add-flow-analysis.md](../../../references/migration/jex-analysis/approval-line-add-flow-analysis.md).

---

## Service-by-service mapping

| Legacy WSVC | Legacy action JSP | Refactor endpoint | Refactor handler |
|---|---|---|---|
| `comm_0222_001_r001` | `comm_0222_001_r001_act.jsp` | `GET /api/v1/settings/approval-line` | `ApprovalLineController.list` → `ApprovalLineService.list` |
| `comm_0222_003_r001` | `comm_0222_003_r001_act.jsp` | `GET …/{appr_line_no}` | `ApprovalLineController.detail` → `ApprovalLineService.detail` |
| `comm_0222_002_c001` | `comm_0222_002_c001_act.jsp` | `POST …` | `ApprovalLineController.create` → `ApprovalLineService.create` |
| `comm_0222_003_u001` | `comm_0222_003_u001_act.jsp` | `PUT …/{appr_line_no}` | `ApprovalLineController.update` → `ApprovalLineService.update` |
| `comm_0222_001_d001` | `comm_0222_001_d001_act.jsp` | `DELETE …/{appr_line_no}` | `ApprovalLineController.delete` → `ApprovalLineService.delete` |
| `comm_0192_001_r001` (shared directory picker) | `comm_0192_001_r001_act.jsp` | `GET /api/v1/users` (separate feature) | `fetchTenantUsers` (frontend) |

---

## Layer-by-layer translation

### View JSP → Next.js dialog/panel
- `comm_0222_001_view.jsp` (list) → `<ApprovalLinePanel>` + `<ApprovalLineTable>`.
- `comm_0222_002_view.jsp` (create popup) + `comm_0222_003_view.jsp` (edit popup) → **one** `<ApprovalLineDialog>` with `mode: "add" | "update"`.

### `_act.jsp` → Spring service
- The JEX `idoCon.beginTransaction()` … `commitAndCloseQuietly` wrap → `@Transactional` on the service method.
- `JexData` / `IDODynamic` / `getInput()/getOutput()` envelope → typed Java records + `ApiResponse`. No JEX patterns ported into Java (per the refactor rule).

### IDO XML → repository
- `IDO.APPR_LINE_INFM_DETL_R002.xml` `json_agg(... ORDER BY APPRVR_DISP_ORD ASC)` correlated subquery → batch-load detail rows ordered `APPRVR_DISP_ORD ASC` + group in Java (`ApprovalLineApproverRepository.findAllByBizKeyAndApprLineNoInOrderByApprLineNoAscApprvrDispOrdAsc`). Same nested response shape on the wire.

### WSVC XML → security + envelope
- `<login>Y` → JWT guard (everything outside `/api/v1/auth/*` authenticated).
- `<NORM_USER_ONLY_YN>Y` + `<WTA_USER_ALLOW_YN>N` → the `JwtAuthenticationFilter` already excludes WTA users from this app surface; menu access enforced by `@PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")` (F-3, 2026-06-06).
- `<mntLogYn>Y` is a framework maintenance-log flag, **not** an app `AUDIT_LOGS_INFM` write — the refactor writes no audit rows (parity; the JSPs don't either).

### Entity / DAO → JPA
- `ApprLineInfm` / `ApprLineInfmDetl` (JEX) → `ApprovalLine` (slug-named master) / `ApprLineInfmDetl` (detail keeps the legacy name) — JPA entities with legacy column names preserved in `@Column`.
- `SeqHelper.getNextVal(util, idoCon, ApprLineInfm.class, "appr_line_no")` → `LegacyFunctionRepository.nextApprLineNo(bizKey)` → `wb_get_next_val('appr_line_infm','appr_line_no', biz_key)` (branch in the consolidated V140 `wb_get_next_val` function, copied verbatim from DEV).

---

## Behavioural translations (decisions)

| Concern | Legacy | Refactor | Rationale |
|---|---|---|---|
| Error codes | JEX `WbMessageCode` (`WBW1164`, `WBE0404`) | `WBE_APPROVAL_LINE_*` (slug-based) | naming rule — translate, never reuse JEX code verbatim |
| `apprvr_disp_ord` on Create | trusts client order | re-numbers `1..N` from array position | OQ-4 — consistency with Update; stricter is safe |
| `apprvr_disp_ord` on Update | re-numbers `i+1` | re-numbers `1..N` (same) | parity |
| Duplicate approver | implicit (HashSet + PK) | explicit fail-fast `WBE_APPROVAL_LINE_DUP_APPROVER` | clearer error than a generic DB violation |
| Approver-in-tenant guard | none (trusts picker) | explicit `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` | refactor adds defence — picker is a separate request |
| Pagination | disabled ("load all") | no pagination (load-all) | parity; tenants have tens of lines |
| Audit | none | none | parity (OQ-6) — deliberate divergence from `firm-banking-settings`/`contacts` |
| In-use delete guard | none | none | parity — consumers snapshot approvers |
| `user_img_url` | `szon_0012_003_r002.act?USER_ID=...` (or `../img/img_thumb.png`) | slug `/api/v1/users/{id}/image` or `null` | OQ-1 — never propagate legacy `_act.jsp` URLs; FE renders `<Avatar>` fallback on null |
| Reorder | jQuery-UI `.sortable()` in both popups | native HTML5 drag-and-drop in one dialog | AL-GAP-01 resolved; same wire contract (order = array order, server assigns `disp_ord`) |
| Two popups (Add/Edit) | `comm_0222_002` + `comm_0222_003` | one `<ApprovalLineDialog>` with `mode` | subsumed |

---

## Dropped / not-ported legacy

- **Approval Box execution flow** — owned by other tables; lands with each consumer feature, not here.
- **`comm_0192_001` directory picker** — replaced by the shared `/api/v1/users` endpoint (`fetchTenantUsers`).
- **Excel import/export, bulk delete, server-side pagination, View Setting** — not in the live `comm_0222_*` UI; excluded.

---

## Identifier traceability

Per the naming rule, every new surface uses the kebab-case slug `approval-line`; legacy WSVC ids
appear only as JEX file:line citations and in this migration prose. The audit `WSVC_ID` bucket is
N/A here (no audit rows written). Frontend View Setting `screenId` would be `settings-core-approval-line`
**if** adopted — but View Setting is not adopted for this screen.

See also: [docs/jex/features/approval-line/migration-guide.md](../../../../../docs/jex/features/approval-line/migration-guide.md) (JEX-side view).

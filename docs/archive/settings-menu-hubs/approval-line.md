# Approval Line

> Canonical Settings-menu entry for **Settings → Approval Line**. Detailed design lives in the
> linked docs below.

## Overview
Approval Line (결재라인) defines reusable ordered approver chains applied to payment/approval
workflows. Supports list, detail, create, update, delete. Slug: `approval-line`. Mirrors live JEX
`comm_0222_*`.

- Backend design: [../business-rules/approval-line.md](../../references/business-rules/approval-line.md)
- Feature spec: [../features/approval/approval-line.md](../../features/settings/approval-line.md)
- JEX → Spring mapping: [../migration/approval-line.md](../../references/migration/approval-line.md)
- JEX add-flow analysis: [../migration/jex-analysis/approval-line-add-flow-analysis.md](../../references/migration/jex-analysis/approval-line-add-flow-analysis.md)
- Task plan: [../archive/planning/phase-1/approval-line-plan.md](../../archive/planning/phase-1/approval-line-plan.md)

## Related APIs
Base URI `/api/v1/settings/approval-line` (JWT, snake_case wire):
- `GET /` — list (query `q?`, `sort_field? = REG_DTM|APPR_LINE_NM|MOD_DTM`, `sort_dir? = ASC|DESC`)
- `GET /{apprLineNo}` — detail
- `POST /` — create → returns created `ApprovalLineRow`
- `PUT /{apprLineNo}` — update
- `DELETE /{apprLineNo}` — delete

## Database Tables
- Approval-line header + ordered approver lines (composite tenant-scoped keys). See backend doc for
  the entity/table breakdown.

## Business Rules
- Tenant-scoped; approvers must belong to the same tenant.
- Ordered approver list: no duplicate approver, bounded maximum count, non-empty.

## Validation
- `appr_line_nm` non-blank.
- Approver list: non-empty, no duplicates, within max size, all in-tenant.

## Error Cases
- `WBE_APPROVAL_LINE_NOT_FOUND`
- `WBE_APPROVAL_LINE_NAME_BLANK`
- `WBE_APPROVAL_LINE_EMPTY_APPROVERS`
- `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`
- `WBE_APPROVAL_LINE_DUP_APPROVER`
- `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT`
- Plus `WBE_VALIDATION`, `WBE_DB`, `WBE9999`.

## Notes
Web/UI: [wecms-web/docs/features/settings/approval-line.md](../../../../wecms-web/docs/features/settings/approval-line.md).

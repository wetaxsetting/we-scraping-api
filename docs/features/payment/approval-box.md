# Payment > Approval Box — API (Refactor)

**Module:** Payment → Approval Management · **Screen:** Approval Box
**Backend package:** `com.kosign.wecms.domain.payment.approvalbox` · **JEX:** `pymn_0001_001` (centralized approval inbox)
**Status:** Implemented. Single screen (no Scheduled/Result split — it's the cross-feature inbox consumed by every Payment write flow).

> Screen-level hub. The detailed multi-doc design set is preserved in [`approval-box/`](approval-box/)
> (discovery, backend, frontend, API flow, DB impact, migration). This file is the single-screen entry
> the menu points at; deep detail lives in the folder.

## Overview
Centralized approval inbox listing every pending Payment write request (internal-transfer / vendor /
expense / payroll / overseas) with approval status, scheduled date, requestor, and approver chain.
Supports approve / reject and hover cards. Gates dispatch of firm-banking transfers.

## Controllers
- `ApprovalBoxController` — list pending items, item detail, approve / reject. See [approval-box/api-flow.md](approval-box/api-flow.md), [approval-box/design-backend.md](approval-box/design-backend.md).

## Services
- `ApprovalBoxService` — inbox query (cross-feature), approve/reject state transitions, audit via shared `AuditLogService`.

## DTOs
- `ApprovalBoxRow` / `ApprovalBoxListResponse` / `ApprovalActionRequest` (lowercase snake_case wire).

## Repositories
- Over the approval tables `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM`. See [approval-box/database-impact.md](approval-box/database-impact.md).

## Database Tables
- `EXPT_RESOL_MSTR` (approval master), `EXPT_RESOL_APPRVR_INFM` (approver chain). Joins the per-family scheduled tables (`SCDUL_PYMN_SUMR`, `OVRS_PYMN_*`) for context.

## Business Rules
- Tenant-scoped by `BIZ_KEY`. Approver must be in the item's approval line; sequential vs parallel per the approval rules. Consumed by **every** Payment family before dispatch.
- See [approval-box/discovery.md](approval-box/discovery.md) for the full behavior analysis.

## Validation
- Approver authorization checked; action (approve/reject) validated against current state.

## Error Handling
- Standard envelope; `WBE_VALIDATION` / `WBE9999`; approval-specific codes — see [approval-box/api-flow.md](approval-box/api-flow.md).

## Related APIs
- Approve / reject + inbox list under `domain.payment.approvalbox`. Migration: [approval-box/migration-plan.md](approval-box/migration-plan.md).

## Related Docs
- Detailed design set: [approval-box/](approval-box/) (discovery · design-backend · design-frontend · api-flow · database-impact · migration-plan)
- Web counterpart: [approval-box.md](../../../../wecms-web/docs/features/payment/approval-box.md)
- Cross-sibling index: [_payment-module-overview.md](_payment-module-overview.md)
- Consumed by: [internal-transfer/README.md](internal-transfer/README.md), [vendor-payment/README.md](vendor-payment/README.md), [expense-payment/README.md](expense-payment/README.md), [payroll/README.md](payroll/README.md), [overseas-payment/README.md](overseas-payment/README.md)
- JEX reference: [screen-flows/payments.md](../../../../docs/jex/screen-flows/payments.md) · [business-rules/approval.md](../../../../docs/jex/business-rules/approval.md)

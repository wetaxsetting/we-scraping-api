# Internal Transfer > Scheduled Transfer Details — API (Refactor)

**Module:** Payment → Internal Transfer · **Screen:** Scheduled Transfer Details
**Backend package:** `com.kosign.wecms.domain.payment.internaltransfer` · **JEX:** `pymn_0009_001`
**Status:** Locked baseline (Phase A backend + partial frontend).

> Screen-level doc. Shared family design (package layout, shared infra, sibling table) lives in the
> feature hub [README.md](README.md) and the cross-sibling index `_payment-module-overview.md`.

## Overview
Drill-down detail of one scheduled transfer batch/row — line items, amounts, withdrawal account, and the approver chain.

## Controllers
- `InternalTransferController` (scheduled flow) — `@RequestMapping("/api/v1/payment/internal-transfer")`. Detail served by the same controller `GET …/{id}` / `…/{id}/details` path.

## Services
- `InternalTransferService` — list/create/validate scheduled batches, Excel parse, request-approval, dispatch; `@Transactional`; audit via shared `AuditLogService`.

## DTOs
- `Scheduled*DetailResponse` (lowercase snake_case wire).

## Repositories
- Shared `Scdul*Repository` over the SCDUL family.

## Database Tables
- SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL (discriminated by SCDUL_PYMN_TYPE_CD = "03"), dispatch tables TB_PAY_TARGET / TB_PAY_FILE_MST / TB_PAY_FILE_DATA; approval tables EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM (via Approval Box).

## Business Rules
- Tenant-scoped by `BIZ_KEY`; create routes through Approval Box before dispatch.
- Discriminated by `SCDUL_PYMN_TYPE_CD = "03"` on the shared tables.

## Validation
- Excel rows validated per-row; required payee/amount/date; blocked-char rule (BLOCKED_CHARS_RULE).

## Error Handling
- Standard envelope `{ status, data, timestamp?, path?, trace_id? }`; `WBE_VALIDATION` / `WBE9999`. Family-specific codes: confirm against the `internaltransfer` service.

## Related APIs
- `GET/POST /api/v1/payment/internal-transfer` (+ `/{id}` detail)

## Related Docs
- Feature hub: [README.md](README.md)
- Sibling screens: [scheduled-transfer.md](scheduled-transfer.md) · [transfer-result.md](transfer-result.md) · [transfer-result-details.md](transfer-result-details.md)
- Web counterpart: [scheduled-transfer-details.md](../../../../../wecms-web/docs/features/payment/internal-transfer/scheduled-transfer-details.md)
- Cross-sibling index: [_payment-module-overview.md](../_payment-module-overview.md)
- Business rules: [internal-transfer.md](../../../references/business-rules/internal-transfer.md)
- DB schema: [internal-transfer-schema.md](../../../references/database/payment/internal-transfer-schema.md)
- Migration: [internal-transfer-mapping.md](../../../references/migration/internal-transfer-mapping.md)
- Parity audit: [internal-transfer-parity-checklist.md](../../../references/audits/internal-transfer-parity-checklist.md)
- JEX reference: [screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md) · [business-rules/payment.md](../../../../../docs/jex/business-rules/payment.md)

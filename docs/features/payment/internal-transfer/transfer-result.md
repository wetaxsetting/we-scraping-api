# Internal Transfer > Transfer Result — API (Refactor)

**Module:** Payment → Internal Transfer · **Screen:** Transfer Result
**Backend package:** `com.kosign.wecms.domain.payment.internaltransfer` · **JEX:** `pymn_0010_001`
**Status:** Locked baseline (Phase A backend + partial frontend).

> Screen-level doc. Shared family design (package layout, shared infra, sibling table) lives in the
> feature hub [README.md](README.md) and the cross-sibling index `_payment-module-overview.md`.

## Overview
Executed internal transfer results with status filter (Incomplete / Processing / Success / Failed).

## Controllers
- `InternalTransferResultController` (result flow) — `@RequestMapping("/api/v1/payment/internal-transfer/results")` (mirrors Internal Transfer two-controller split).

## Services
- `InternalTransferService` — read executed results, status mapping; `@Transactional`; audit via shared `AuditLogService`.

## DTOs
- `*ResultRow` / `*ResultListResponse` (lowercase snake_case wire).

## Repositories
- Shared `Scdul*Repository` over the SCDUL family.

## Database Tables
- SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL (discriminated by SCDUL_PYMN_TYPE_CD = "03"), dispatch tables TB_PAY_TARGET / TB_PAY_FILE_MST / TB_PAY_FILE_DATA; approval tables EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM (via Approval Box).

## Business Rules
- Tenant-scoped by `BIZ_KEY`; read-only; status derived from dispatch + gateway result.
- Discriminated by `SCDUL_PYMN_TYPE_CD = "03"` on the shared tables.

## Validation
- Filter params validated; date range from ≤ to; pagination clamped.

## Error Handling
- Standard envelope `{ status, data, timestamp?, path?, trace_id? }`; `WBE_VALIDATION` / `WBE9999`. Family-specific codes: confirm against the `internaltransfer` service.

## Related APIs
- `GET /api/v1/payment/internal-transfer/results` (+ `/{id}` detail)

## Related Docs
- Feature hub: [README.md](README.md)
- Sibling screens: [scheduled-transfer.md](scheduled-transfer.md) · [scheduled-transfer-details.md](scheduled-transfer-details.md) · [transfer-result-details.md](transfer-result-details.md)
- Web counterpart: [transfer-result.md](../../../../../wecms-web/docs/features/payment/internal-transfer/transfer-result.md)
- Cross-sibling index: [_payment-module-overview.md](../_payment-module-overview.md)
- Business rules: [internal-transfer.md](../../../references/business-rules/internal-transfer.md)
- DB schema: [internal-transfer-schema.md](../../../references/database/payment/internal-transfer-schema.md)
- Migration: [internal-transfer-mapping.md](../../../references/migration/internal-transfer-mapping.md)
- Parity audit: [internal-transfer-parity-checklist.md](../../../references/audits/internal-transfer-parity-checklist.md)
- JEX reference: [screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md) · [business-rules/payment.md](../../../../../docs/jex/business-rules/payment.md)

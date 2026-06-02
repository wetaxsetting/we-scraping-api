# Vendor Payment > Payment Result Details — API (Refactor)

**Module:** Payment → Vendor Payment · **Screen:** Payment Result Details
**Backend package:** `com.kosign.wecms.domain.payment.vendorpayment` · **JEX:** `pymn_0002_001`
**Status:** Implemented in code; documented as stub (backfill pending).

> Screen-level doc. Shared family design (package layout, shared infra, sibling table) lives in the
> feature hub [README.md](README.md) and the cross-sibling index `_payment-module-overview.md`.

## Overview
Per-transaction result detail (Success / Failed + failure reason) for one executed vendor payment item.

## Controllers
- `VendorPaymentResultController` (result flow) — `@RequestMapping("/api/v1/payment/vendor-payment/results")` (mirrors Internal Transfer two-controller split). Detail served by the same controller `GET …/{id}` / `…/{id}/details` path.

## Services
- `VendorPaymentService` — read executed results, status mapping; `@Transactional`; audit via shared `AuditLogService`.

## DTOs
- `*ResultDetailResponse` (lowercase snake_case wire).

## Repositories
- Shared `Scdul*Repository` over the SCDUL family.

## Database Tables
- SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL (discriminated by SCDUL_PYMN_TYPE_CD = "01"), dispatch tables TB_PAY_TARGET / TB_PAY_FILE_MST / TB_PAY_FILE_DATA; approval tables EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM (via Approval Box).

## Business Rules
- Tenant-scoped by `BIZ_KEY`; read-only; status derived from dispatch + gateway result.
- Discriminated by `SCDUL_PYMN_TYPE_CD = "01"` on the shared tables.

## Validation
- Filter params validated; date range from ≤ to; pagination clamped.

## Error Handling
- Standard envelope `{ status, data, timestamp?, path?, trace_id? }`; `WBE_VALIDATION` / `WBE9999`. Family-specific codes: confirm against the `vendorpayment` service.

## Related APIs
- `GET /api/v1/payment/vendor-payment/results` (+ `/{id}` detail)

## Related Docs
- Feature hub: [README.md](README.md)
- Sibling screens: [scheduled-payment.md](scheduled-payment.md) · [scheduled-payment-details.md](scheduled-payment-details.md) · [payment-result.md](payment-result.md)
- Web counterpart: [payment-result-details.md](../../../../../wecms-web/docs/features/payment/vendor-payment/payment-result-details.md)
- Cross-sibling index: [_payment-module-overview.md](../_payment-module-overview.md)
- Business rules: [internal-transfer.md](../../../references/business-rules/internal-transfer.md) (shared baseline)
- DB schema: [internal-transfer-schema.md](../../../references/database/payment/internal-transfer-schema.md) (shared SCDUL baseline)
- JEX reference: [screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md) · [business-rules/payment.md](../../../../../docs/jex/business-rules/payment.md)

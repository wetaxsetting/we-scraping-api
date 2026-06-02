# Overseas Payment > Payment Result Details — API (Refactor)

**Module:** Payment → Overseas Payment · **Screen:** Payment Result Details
**Backend package:** `com.kosign.wecms.domain.payment.overseas` · **JEX:** `pymn_0008_001`
**Status:** Documented at hub level 2026-06-01; deepen to baseline parity in a follow-up.

> Screen-level doc. Shared family design (package layout, shared infra, sibling table) lives in the
> feature hub [README.md](README.md) and the cross-sibling index `_payment-module-overview.md`.

## Overview
Per-transaction result detail (Success / Failed + failure reason) for one executed overseas payment item.

## Controllers
- `OverseasPaymentResultController` (result flow) — `@RequestMapping("/api/v1/payment/overseas-payment/results")` (mirrors Internal Transfer two-controller split). Detail served by the same controller `GET …/{id}` / `…/{id}/details` path.

## Services
- `OverseasPaymentService` — read executed results, status mapping; `@Transactional`; audit via shared `AuditLogService`.

## DTOs
- `*ResultDetailResponse` (lowercase snake_case wire).

## Repositories
- `OvrsPymn*Repository` over the OVRS family.

## Database Tables
- OVRS_PYMN_MSTR / OVRS_PYMN_SUMR / OVRS_PYMN_DTLS (separate family — not the shared SCDUL flow); approval tables EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM (via Approval Box).

## Business Rules
- Tenant-scoped by `BIZ_KEY`; read-only; status derived from dispatch + gateway result.
- Uses the dedicated `getDynamicOvrs()` read path, not `SCDUL_PYMN_TYPE_CD`.

## Validation
- Filter params validated; date range from ≤ to; pagination clamped.

## Error Handling
- Standard envelope `{ status, data, timestamp?, path?, trace_id? }`; `WBE_VALIDATION` / `WBE9999`. Family-specific codes: confirm against the `overseas` service.

## Related APIs
- `GET /api/v1/payment/overseas-payment/results` (+ `/{id}` detail)

## Related Docs
- Feature hub: [README.md](README.md)
- Sibling screens: [scheduled-payment.md](scheduled-payment.md) · [scheduled-payment-details.md](scheduled-payment-details.md) · [payment-result.md](payment-result.md)
- Web counterpart: [payment-result-details.md](../../../../../wecms-web/docs/features/payment/overseas-payment/payment-result-details.md)
- Cross-sibling index: [_payment-module-overview.md](../_payment-module-overview.md)
- JEX reference: [screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md) · [business-rules/payment.md](../../../../../docs/jex/business-rules/payment.md)

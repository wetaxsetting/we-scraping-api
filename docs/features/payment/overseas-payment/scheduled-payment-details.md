# Overseas Payment > Scheduled Payment Details — API (Refactor)

**Module:** Payment → Overseas Payment · **Screen:** Scheduled Payment Details
**Backend package:** `com.kosign.wecms.domain.payment.overseas` · **JEX:** `pymn_0007_001`
**Status:** Documented at hub level 2026-06-01; deepen to baseline parity in a follow-up.

> Screen-level doc. Shared family design (package layout, shared infra, sibling table) lives in the
> feature hub [README.md](README.md) and the cross-sibling index `_payment-module-overview.md`.

## Overview
Drill-down detail of one scheduled payment batch/row — line items, amounts, withdrawal account, and the approver chain.

## Controllers
- `OverseasPaymentController` (scheduled flow) — `@RequestMapping("/api/v1/payment/overseas-payment")`. Detail served by the same controller `GET …/{id}` / `…/{id}/details` path.

## Services
- `OverseasPaymentService` — list/create/validate scheduled batches, Excel parse, request-approval, dispatch; `@Transactional`; audit via shared `AuditLogService`.

## DTOs
- `Scheduled*DetailResponse` (lowercase snake_case wire).

## Repositories
- `OvrsPymn*Repository` over the OVRS family.

## Database Tables
- OVRS_PYMN_MSTR / OVRS_PYMN_SUMR / OVRS_PYMN_DTLS (separate family — not the shared SCDUL flow); approval tables EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM (via Approval Box).

## Business Rules
- Tenant-scoped by `BIZ_KEY`; create routes through Approval Box before dispatch.
- Uses the dedicated `getDynamicOvrs()` read path, not `SCDUL_PYMN_TYPE_CD`.

## Validation
- Excel rows validated per-row; required payee/amount/date; blocked-char rule (BLOCKED_CHARS_RULE).

## Error Handling
- Standard envelope `{ status, data, timestamp?, path?, trace_id? }`; `WBE_VALIDATION` / `WBE9999`. Family-specific codes: confirm against the `overseas` service.

## Related APIs
- `GET/POST /api/v1/payment/overseas-payment` (+ `/{id}` detail)

## Related Docs
- Feature hub: [README.md](README.md)
- Sibling screens: [scheduled-payment.md](scheduled-payment.md) · [payment-result.md](payment-result.md) · [payment-result-details.md](payment-result-details.md)
- Web counterpart: [scheduled-payment-details.md](../../../../../wecms-web/docs/features/payment/overseas-payment/scheduled-payment-details.md)
- Cross-sibling index: [_payment-module-overview.md](../_payment-module-overview.md)
- JEX reference: [screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md) · [business-rules/payment.md](../../../../../docs/jex/business-rules/payment.md)

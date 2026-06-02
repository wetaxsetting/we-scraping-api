# Payroll > Scheduled Payment — API (Refactor)

**Module:** Payment → Payroll · **Screen:** Scheduled Payment
**Backend package:** `com.kosign.wecms.domain.payment.payrollpayment` · **JEX:** `pymn_0005_001`
**Status:** Implemented in code; documented as stub (audit WSVC_ID payroll-payment).

> Screen-level doc. Shared family design (package layout, shared infra, sibling table) lives in the
> feature hub [README.md](README.md) and the cross-sibling index `_payment-module-overview.md`.

## Overview
List + create scheduled payroll batches (Excel upload, request approval, transfer).

## Controllers
- `PayrollController` (scheduled flow) — `@RequestMapping("/api/v1/payment/payroll")`.

## Services
- `PayrollService` — list/create/validate scheduled batches, Excel parse, request-approval, dispatch; `@Transactional`; audit via shared `AuditLogService`.

## DTOs
- `Scheduled*Row` / `Scheduled*ListResponse` / `*CreateRequest` (lowercase snake_case wire).

## Repositories
- Shared `Scdul*Repository` over the SCDUL family.

## Database Tables
- SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL (discriminated by SCDUL_PYMN_TYPE_CD = "00"), dispatch tables TB_PAY_TARGET / TB_PAY_FILE_MST / TB_PAY_FILE_DATA; approval tables EXPT_RESOL_MSTR + EXPT_RESOL_APPRVR_INFM (via Approval Box).

## Business Rules
- Tenant-scoped by `BIZ_KEY`; create routes through Approval Box before dispatch.
- Discriminated by `SCDUL_PYMN_TYPE_CD = "00"` on the shared tables.

## Validation
- Excel rows validated per-row; required payee/amount/date; blocked-char rule (BLOCKED_CHARS_RULE).

## Error Handling
- Standard envelope `{ status, data, timestamp?, path?, trace_id? }`; `WBE_VALIDATION` / `WBE9999`. Family-specific codes: confirm against the `payrollpayment` service.

## Related APIs
- `GET/POST /api/v1/payment/payroll` (+ `/{id}` detail)

## Related Docs
- Feature hub: [README.md](README.md)
- Sibling screens: [scheduled-payment-details.md](scheduled-payment-details.md) · [payment-result.md](payment-result.md) · [payment-result-details.md](payment-result-details.md)
- Web counterpart: [scheduled-payment.md](../../../../../wecms-web/docs/features/payment/payroll/scheduled-payment.md)
- Cross-sibling index: [_payment-module-overview.md](../_payment-module-overview.md)
- Business rules: [internal-transfer.md](../../../references/business-rules/internal-transfer.md) (shared baseline)
- DB schema: [internal-transfer-schema.md](../../../references/database/payment/internal-transfer-schema.md) (shared SCDUL baseline)
- JEX reference: [screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md) · [business-rules/payment.md](../../../../../docs/jex/business-rules/payment.md)

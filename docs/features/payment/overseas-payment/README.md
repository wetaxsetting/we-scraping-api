# Overseas Payment — Refactor Feature Spec

## Screens (this feature)

- [scheduled-payment.md](scheduled-payment.md)
- [scheduled-payment-details.md](scheduled-payment-details.md)
- [payment-result.md](payment-result.md)
- [payment-result-details.md](payment-result-details.md)


| Field | Value |
|---|---|
| Slug | `overseas-payment` |
| Module | Payment → Overseas Payment |
| JEX counterparts | `pymn_0007_*` (Overseas Scheduled Payment) + `pymn_0008_*` (Overseas Payment Result) — label confirmed at [comm_0190_003_view.jsp:608](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L608); `getDynamicOvrs()` branch at [pymn_0007_001_r001_act.jsp:198](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_001_r001_act.jsp#L198) |
| Backend package | [`com.kosign.wecms.domain.payment.overseas`](../../../../src/main/java/com/kosign/wecms/domain/payment/overseas) |
| Frontend folder | [`components/payment/overseas-payment/`](../../../../../wecms-web/components/payment/overseas-payment) |
| Routes | `/payment/overseas-payment/{scheduled-payment, payment-result, exchange-rate-inquiry}` |
| Status | **Implemented in code; documented 2026-06-01** (previously a gap — no feature doc). This hub indexes the family; deepen per-section detail in a follow-up MR. |

> **Separate table family.** Unlike Internal Transfer / Vendor / Expense / Payroll (which share
> `SCDUL_PYMN_SUMR(_DETL)` discriminated by `SCDUL_PYMN_TYPE_CD`), Overseas Payment uses its **own
> tables** `OVRS_PYMN_MSTR` / `OVRS_PYMN_SUMR` / `OVRS_PYMN_DTLS` and a dedicated `getDynamicOvrs()`
> read path — see [_payment-module-overview.md](../_payment-module-overview.md) §1.

## Overview
Overseas (foreign) remittance payments with beneficiary/FX detail. Mirrors the sibling payment shape
(Excel upload → request approval → transfer) but on the overseas table family, and pairs with the
**Exchange Rate Inquiry** screen for FX lookup.

## Menu sections (family hub)
This hub documents all four Overseas Payment menu items as sections:

### 1. Scheduled Payment (`pymn_0007_001`)
List + create scheduled overseas payments (Excel upload, request-approval, transfer). Source: `OVRS_PYMN_MSTR`/`OVRS_PYMN_SUMR`.

### 2. Scheduled Payment Details
Drill-down detail of a scheduled overseas payment row (beneficiary, FX, amount, approver chain). Source: `OVRS_PYMN_DTLS`.

### 3. Payment Result (`pymn_0008_001`)
Executed overseas-payment results with status (Incomplete / Processing / Success / Failed).

### 4. Payment Result Details
Per-transaction result detail with beneficiary info and failure reason.

## Related APIs
- Base URI (proposed/confirm): `/api/v1/payment/overseas-payment` (Scheduled) + `…/results` (Result), following the Internal Transfer two-controller pattern. **Confirm exact routes against `domain.payment.overseas` controllers.**

## Database Tables
- `OVRS_PYMN_MSTR` — overseas payment master
- `OVRS_PYMN_SUMR` — summary (scheduled batch)
- `OVRS_PYMN_DTLS` — per-transaction detail
- Shared approval tables `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM` (via Approval Box).

## Business Rules
- Tenant-scoped by `BIZ_KEY`; approval routed through [approval-box/](../approval-box).
- FX handling pairs with [exchange-rate-inquiry.md](../exchange-rate-inquiry.md).
- Does **not** use `SCDUL_PYMN_TYPE_CD`; separate read path (`getDynamicOvrs()`).

## Validation
- Beneficiary + FX + amount required; Excel rows validated per-row. **Confirm against code.**

## Error Cases
- Standard envelope; `WBE_VALIDATION` / `WBE9999`. **Confirm overseas-specific codes against the service.**

## Cross-links
- Cross-sibling index: [_payment-module-overview.md](../_payment-module-overview.md)
- FX lookup: [exchange-rate-inquiry.md](../exchange-rate-inquiry.md)
- Approval flow: [approval-box/discovery.md](../approval-box/discovery.md)
- Web/UI: [`../../../../wecms-web/docs/features/payment/overseas-payment.md`](../../../../../wecms-web/docs/features/payment/overseas-payment/README.md)
- JEX reference: [`../../../../docs/jex/screen-flows/payments.md`](../../../../../docs/jex/screen-flows/payments.md) · [`../../../../docs/jex/business-rules/payment.md`](../../../../../docs/jex/business-rules/payment.md)

## Notes
Created to close the Overseas Payment gap. Sections 1–4 are intentionally hub-level; a follow-up MR
should expand them to internal-transfer parity (endpoints, DTOs, field maps) and confirm the
`(proposed/confirm)` items against `domain.payment.overseas`.

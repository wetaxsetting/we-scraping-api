# Payroll Payment — Refactor Backend Feature Spec

> **Frontend counterpart:** [wecms-web/docs/features/payment/payroll/README.md](../../../../../wecms-web/docs/features/payment/payroll/README.md). API and WEB are refactored together — a contract change on one side has a matching change on the other.

| Field | Value |
|---|---|
| System | Refactor (Spring Boot backend) |
| Module | Payment → Payroll |
| Slug | `payroll` (frontend route base) / `payroll-payment` (backend package + audit `WSVC_ID` + REST base) |
| REST base | **`/api/v1/payment/payroll-payment`** (note `-payment`; **not** `/payroll`) |
| Backend package | `com.kosign.wecms.domain.payment.payrollpayment` |
| Discriminator | `SCDUL_PYMN_TYPE_CD = "00"` — `ScdulPymnTypeCd.PAYROLL.code()` ([pymn_0005_001_r001_act.jsp:173](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0005_001_r001_act.jsp#L173)) |
| Audit `WSVC_ID` | `payroll-payment` — `static final String` at [PayrollPaymentService.java:96](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentService.java#L96) |
| Status | **Fully implemented** — ~18 endpoints (Scheduled + Result + upload/parse/create + approval flow + dispatch + banking match + export + file download), documented retroactively against the latest code. |

## Sub-module map

| Sub-module | REST | Authz |
|---|---|---|
| **Scheduled Payment** | `GET /` · `GET /{id}` (+ write actions) | `payment-payroll-scheduled` |
| **Payment Result** | `GET /results` · `GET /results/{id}` · `POST /results/export` · banking match | `payment-payroll-result` |

Payroll, Vendor, Expense and Internal Transfer share the physical `SCDUL_PYMN_SUMR`(+ `_DETL`) table; the `SCDUL_PYMN_TYPE_CD = "00"` discriminator stamped by the service layer isolates Payroll. Payroll reuses the Internal Transfer wire DTOs (`internaltransfer.dto.*`) one-for-one.

## Companion docs

| Doc | Scope |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end Legacy / Spring / React flow — upload → request approval → approve/reject → dispatch → result inquiry → banking match |
| [database-mapping.md](database-mapping.md) | Entity ↔ table ↔ column, PK/FK, key generation, migrations |
| [functional-specification.md](functional-specification.md) | Filters, status tabs + counts, columns (incl. `APPR_FUND_TP` Payroll Type), actions, validation, business rules |
| [interface-specification.md](interface-specification.md) | Every endpoint — URL, method, `@PreAuthorize`, request/response DTO fields, query params, errors |
| [migration-guide.md](migration-guide.md) | Legacy `pymn_0005_*` / `pymn_0006_*` → Spring controller/service, approved deviations, dead legacy |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Per-gap classification (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) |
| [source-code-index.md](source-code-index.md) | Controller / services / repos / entities / DTOs + FE routes / components / hooks / `lib/api/payrollPayment.ts` + tests |
| [test-scenarios.md](test-scenarios.md) | Backend test coverage + the frontend test gap |

## Detail-page column projection — corrected

Two **different** subsets of the canonical column factories are projected via View Setting; neither is a bespoke layout:

- **Scheduled detail** (`pymn_0005_003`) = **5-column subset** of the canonical `scheduledDetailColumns` (Deposit Bank, Recipient Name, Transfer Amount, Remarks, Reporting Account).
- **Payment Result detail** (`pymn_0006_002`) = **8-column subset** of the canonical `resultDetailColumns` (Date/Time, Deposit Bank, Recipient Name, Transfer Amount, Remarks, Results, Result Date/Time, Reporting Account).

> **Stale-claim removal (2026-06-23).** The earlier README's "5-column Payroll-specific layout (not the canonical set)" claim for the Result detail, and the "STUB / backfill pending" labels, were **incorrect** and are removed. The Result detail consumes the canonical `resultDetailColumns` projected to an 8-column View-Setting subset; the feature is fully implemented.

## Payroll-only column — Payroll Type (`APPR_FUND_TP`)

Both Payroll list pages add a Payroll-only **Payroll Type** column sourced from `EXPT_RESOL_MSTR.APPR_FUND_TP` (joined by `APPR_SEQ_NO`): `"0"` → Salary, `"1"` → Bonus, else blank (legacy `pymn_0005_001.ts` / `pymn_0006_001.ts`). It is written at Request Approval time (`stampApproval`, mandatory for `APPR_DV=21`) and read back on the Scheduled + Result lists.

## Endpoint summary (~18)

Base `/api/v1/payment/payroll-payment` — full table in [interface-specification.md](interface-specification.md).

| Group | Endpoints |
|---|---|
| Scheduled read | `GET /` · `GET /{id}` |
| Result read | `GET /results` · `GET /results/{id}` · `POST /results/export` |
| Upload | `GET /withdrawal-accounts` · `POST /uploads/parse` · `POST /` (create draft) |
| Approval flow | `POST /{id}/request-approval` (multipart) · `POST /{id}/approval-action` · `DELETE /{id}/approval` · `PUT /{id}/approval-line` |
| Line edit | `PATCH /{id}/lines/{lineId}/reporting-account` |
| Dispatch | `POST /{id}/dispatch` |
| Banking match | `GET /{id}/match/candidates` · `POST /{id}/match` · `DELETE /{id}/match` |
| File | `GET /{id}/file` (shared by Scheduled + Result; either menu) · `DELETE /{id}` (delete draft) |

## Notes

- `SCDUL_PYMN_TYPE_CD = "00"` — the `scdul_pymn_sumr_chk01` CHECK was relaxed in `V146__scdul_pymn_sumr_chk01_allow_payroll.sql` to admit `'00'`; do not tighten without re-allowing all siblings. *(historical — FOLDED INTO BASE DDL; the CHECK now ships inside `V86__scdul_pymn_sumr.sql`)*
- Status is **never** stored on `SCDUL_PYMN_SUMR` (no `APPR_STTS` column in legacy JEX) — it is derived via `wb_get_appr_stts(...)` through `LegacyFunctionRepository`. Rejected = `R` (legacy code, not `X`).
- Slug asymmetry (frontend `payroll`, backend/audit `payroll-payment`) is intentional and confirmed acceptable.

## Legacy & Migration Traceability

- **JEX counterparts:** `pymn_0005_*` (Scheduled), `pymn_0006_*` (Result); detail pages `pymn_0005_003` / `pymn_0006_002`.
- **Sub-module list / detail WSVC mapping:** Scheduled Payment = `pymn_0005_001` / `pymn_0005_003` ; Payment Result = `pymn_0006_001` / `pymn_0006_002`.

Last verified against code: 2026-06-23

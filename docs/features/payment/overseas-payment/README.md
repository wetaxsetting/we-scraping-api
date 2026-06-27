# Overseas Payment — Refactor Feature Spec (Backend)

> **Backend feature hub.** Web/UI counterpart → [`wecms-web/docs/features/payment/overseas-payment/README.md`](../../../../../wecms-web/docs/features/payment/overseas-payment/README.md). Source of truth = the latest implementation code; this hub + the 8 companion docs were re-normalized against `OverseasPaymentController` + the `Overseas*` DTOs/entities on **2026-06-23**.

| Field | Value |
|---|---|
| Slug | `overseas-payment` |
| Module | Payment → Overseas Payment |
| Backend controller | [`OverseasPaymentController`](../../../../src/main/java/com/kosign/wecms/domain/payment/controller/OverseasPaymentController.java) — base `/api/v1/payment/overseas-payment` (**single controller**, not a two-controller split) |
| Backend service | [`OverseasPaymentService`](../../../../src/main/java/com/kosign/wecms/domain/payment/service/OverseasPaymentService.java) + helper package [`domain/payment/overseas/**`](../../../../src/main/java/com/kosign/wecms/domain/payment/overseas) (dispatch, key-gen, file/storage, result-detail export) |
| Frontend folder | [`components/payment/overseas-payment/`](../../../../../wecms-web/components/payment/overseas-payment) |
| Routes | `/payment/overseas-payment/{scheduled-payment, payment-result, exchange-rate-inquiry}` |
| Authorization | Class-level `@PreAuthorize("@menuAuthz.canAccess('payment-overseas-request')")`. The Result list/detail/export endpoints **override** to `payment-overseas-result`. Exchange Rate Inquiry is a **separate** controller gated by `payment-overseas-exchange` (documented in [exchange-rate-inquiry.md](../exchange-rate-inquiry.md), not here). |
| Status | **Implemented full-stack** (backend + frontend wired to live endpoints; no mock fallback). |

> **Separate table family.** Unlike Internal Transfer / Vendor / Expense / Payroll (which share
> `SCDUL_PYMN_SUMR(_DETL)` discriminated by `SCDUL_PYMN_TYPE_CD`), Overseas Payment uses its **own**
> tables `OVRS_PYMN_MSTR` / `OVRS_PYMN_SUMR` / `OVRS_PYMN_SUMR_DETL` and a dedicated `getDynamicOvrs()`
> read path — see [payment hub README](../README.md) §1 and [database-mapping.md](database-mapping.md).

## Overview

Overseas (foreign) remittance payments with beneficiary + FX detail. Mirrors the sibling payment shape
(Excel upload → request approval → transfer/dispatch → result inquiry) but on the overseas table family,
and pairs with the **Exchange Rate Inquiry** screen for FX lookup. The list and result reads **UNION**
`OVRS_PYMN_SUMR` (Excel-batch "E"-mode) with `OVRS_PYMN_MSTR` (manual/individual "M"-mode) and LEFT JOIN
`EXPT_RESOL_MSTR` for approval state.

> **No `APPR_STTS` column.** `OVRS_PYMN_SUMR` has no stored approval-status column (JEX parity). Status is
> **derived** from `EXPT_RESOL_MSTR.APPR_TYPE_CD` (`mapApprTypeCd`: `01`→C, `02`→X, `03`→I, else P) —
> see memory `project_scdul_pymn_sumr_appr_stts_remap.md` (overseas variant fixed 2026-06-10).

## Sub-module map

| Sub-module | JEX WSVC | Scope | Key endpoints |
|---|---|---|---|
| **Scheduled Payment** (+ Details) | `pymn_0007_001` | List + create scheduled overseas payments (Excel preview/upload, request-approval multipart, change/remove approval line, approve/reject, dispatch, delete draft, reporting-account, withdrawal-accounts, file download). Source `OVRS_PYMN_SUMR` (E) ∪ `OVRS_PYMN_MSTR` (M); per-line detail `OVRS_PYMN_SUMR_DETL`. | `GET /scheduled`, `GET /scheduled/{id}`, `GET /scheduled/{id}/details`, write endpoints |
| **Payment Result** (+ Details) | `pymn_0008_001` | Executed overseas-payment results with `proc_stat` CSV status filter (Completed `0,3` / In-Progress `6`); derived `trnf_stts` ∈ {C,F,P}; clickable Approval Ref (`appr_seq_no`, F4b); list + per-batch Excel export. | `GET /result`, `GET /result/{id}`, `GET /result/export`, `POST /result/{id}/export` |
| **Exchange Rate Inquiry** | `EXCH_0001_001` | FX rate matrix lookup. **Separate controller** `ExchangeRateController` (`/api/v1/banking/exchange-rates`), gated by `payment-overseas-exchange`. | → [exchange-rate-inquiry.md](../exchange-rate-inquiry.md) (authored separately) |

## Companion docs (this folder)

| Doc | Contents |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end Legacy / Spring / React flow for every action (excel preview/upload, request-approval, change/remove line, approve/reject, dispatch, delete, reporting-account, withdrawal-accounts, file download, result inquiry, exports) |
| [database-mapping.md](database-mapping.md) | `OVRS_PYMN_MSTR` / `OVRS_PYMN_SUMR` / `OVRS_PYMN_SUMR_DETL` / `EXPT_RESOL_MSTR`: entity ↔ table ↔ column, PK/FK, key-gen |
| [functional-specification.md](functional-specification.md) | Filters, status tabs + counts, columns, multi-currency totals, actions, validation, rules, M-mode behavior |
| [interface-specification.md](interface-specification.md) | Every endpoint — URL, method, `@PreAuthorize`, request/response DTO fields, query params, errors, the two export paths |
| [migration-guide.md](migration-guide.md) | Legacy overseas WSVC → controller/service mapping, approved deviations, dead legacy |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Gap vocabulary table; the M-mode full per-line rendering PO/DBA decision |
| [source-code-index.md](source-code-index.md) | Complete Legacy → Backend → Frontend file map |
| [test-scenarios.md](test-scenarios.md) | Backend test inventory + frontend coverage gap |

## Endpoint summary

All under base `/api/v1/payment/overseas-payment` (single controller). Full detail in [interface-specification.md](interface-specification.md).

| Method | Path | Handler | Authz |
|---|---|---|---|
| GET | `/scheduled` | `listScheduled` | request |
| GET | `/scheduled/{id}` | `getScheduledById` | request |
| GET | `/scheduled/{id}/details` | `getScheduledDetail` | request |
| GET | `/scheduled/{id}/file` | `downloadFile` | request |
| POST | `/excel-preview` | `excelPreview` | request |
| POST | `/excel-upload` | `excelUpload` | request |
| POST | `/{id}/request-approval` | `requestApproval` (multipart) | request |
| POST | `/request-approval-multi` | `requestApprovalMulti` (M-type) | request |
| PATCH | `/{id}/approval-line` | `changeApprovalLine` | request |
| DELETE | `/{id}/approval-line` | `removeApprovalLine` | request |
| DELETE | `/{id}` | `deleteDraftBatch` | request |
| POST | `/{id}/approval-action` | `approvalAction` | request |
| POST | `/{id}/dispatch` | `dispatch` | request |
| PATCH | `/{id}/details/{ovrsPymnDetlNo}/reporting-account` | `saveReportingAccount` | request |
| GET | `/withdrawal-accounts` | `listWithdrawalAccounts` | request |
| GET | `/result` | `listResult` | **result** |
| GET | `/result/{id}` | `getResultById` | **result** |
| GET | `/result/export` | `exportResult` (list XLSX) | **result** |
| POST | `/result/{id}/export` | `exportResultDetail` (per-batch XLSX) | **result** |

> Banking-match endpoints (per-line candidates / match / unmatch / unmatch-all) for the Result Detail screen are consumed by the frontend (`lib/api/payment.ts`); see [interface-specification.md](interface-specification.md) §"Banking match".

## Legacy & Migration Traceability

- **JEX counterparts:** `pymn_0007_*` (Overseas Scheduled Payment) + `pymn_0008_*` (Overseas Payment Result) — label at [comm_0190_003_view.jsp:608](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L608); `getDynamicOvrs()` branch at [pymn_0007_001_r001_act.jsp:198](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_001_r001_act.jsp#L198)

## Cross-links

- Cross-sibling index: [payment hub README](../README.md) · [payment-source-index.md](../../../audits/payment-source-index.md) · [payment-gap-analysis.md](../../../audits/payment-gap-analysis.md)
- FX lookup: [exchange-rate-inquiry.md](../exchange-rate-inquiry.md) (authored separately)
- Approval flow: [approval-box/](../approval-box/)
- Web/UI hub: [`wecms-web/.../overseas-payment/README.md`](../../../../../wecms-web/docs/features/payment/overseas-payment/README.md)
- JEX reference: [`docs/jex/screen-flows/payments.md`](../../../../../docs/jex/screen-flows/payments.md) · [`docs/jex/business-rules/payment.md`](../../../../../docs/jex/business-rules/payment.md)

# Vendor Payment — Refactor Gap Analysis (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

Area-by-area comparison of the legacy JEX `pymn_0001_*` / `pymn_0002_*` behaviour against the refactor. Status vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

> Re-audited 2026-06-23 against the latest code ([VendorPaymentController.java](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/controller/VendorPaymentController.java) + the two services + `lib/api/vendorPayment.ts`). Companion: [payment-gap-analysis.md](../../../audits/modules/payment-gap-analysis.md) (rows G-VEND-1/2).

| Area | Legacy (JEX) | Refactor (Spring) | Status |
|---|---|---|---|
| **Scheduled list** | `pymn_0001_001_r001`; date/keyword filter, `SCDUL_PYMN_TYPE_CD='01'`, sort | `GET /` → `VendorPaymentService.list` → `ScdulPymnSumrRepository.search` (`'01'`, `appr_type` server-side via `wb_get_appr_stts`, `VendorPaymentSortField` whitelist) | **MATCH** |
| **Scheduled detail** | `pymn_0001_002_r001` | `GET /{id}` (numeric or `DOC_NO`); master + lines + attachments | **MATCH** |
| **Status derivation** | `wb_get_appr_stts` over `EXPT_RESOL_MSTR` | same — funneled via `LegacyFunctionRepository`; no `APPR_STTS` column on `SCDUL_PYMN_SUMR`; `C`/`R` not `X` | **MATCH** |
| **Excel parse** | `pymn_0001_002_r001` | `POST /uploads/parse` → `VendorPaymentExcelParser` (POI + per-cell validation, blocked-chars) | **MATCH** |
| **Draft create** | `pymn_0001_002_c001` | `POST /` (multipart); keys via funnel; ERP `02` audit; `vendor-payment/` storage | **MATCH** |
| **Request approval** | `comm_0192_001_c005` (JSON) | `POST /{id}/request-approval` **multipart** (attachments → `EXPT_RESOL_FILE`) via shared fan-out | **APPROVED DEVIATION** (D7 — multipart attachment parity) |
| **Approve / reject** | `bltb_0001_003_u001` | `POST /{id}/approval-action` → shared Approval-Box; no source write-back | **MATCH** |
| **Withdraw** | (Approval Details popup) | `DELETE /{id}/approval` → shared `withdrawApproval`; nulls `APPR_SEQ_NO` | **MATCH** |
| **Change approval-line** | (Approval Details popup) | `PUT /{id}/approval-line` → shared rebuild | **MATCH** |
| **Reporting-account** | `pymn_0009_002_u002` | `PATCH /{id}/lines/{lineId}/reporting-account`; `WBE2064` once dispatched | **MATCH** |
| **Banking Match** | `comm_0197_001_*` / `comm_0197_002_u001` | 4 endpoints → `DomesticPaymentLineMatchService` (`VENDOR.code()`) | **MATCH** |
| **Dispatch (Transfer)** | `pymn_0001_001_c001` | `POST /{id}/dispatch` → `VendorDispatchExecutor` (cut-off + approval gated; flag-gated inert by default) | **MATCH** (SIT-pending live dispatch — environmental, see batch memo) |
| **Result list** | `pymn_0002_001_r001`; `TRNF_YN` filter | `GET /results`; route-aware `PROC_STAT` Completed `C` / In Progress `P` (C2); footer totals **unfiltered** by status (R008) | **APPROVED DEVIATION** (D4/D5 — route-aware status + unfiltered footer = legacy parity) |
| **Result Approval Ref (F4b)** | `ERP_FILE_NUM` | `EXPT_RESOL_MSTR.DOC_NO` resolved by `APPR_SEQ_NO`; clickable | **APPROVED DEVIATION** (D3 — canonical home) |
| **Result detail** | `pymn_0002_002_r001` | `GET /results/{id}`; canonical 11-col table; `countByStatus` `[success,failed,inProgress]` | **MATCH** |
| **Result export** | client-side / ERP export | `POST /results/export` → `VendorPaymentResultExportProvider` (screenId `payment-vendor-payment-result`, ERP `04`) | **MATCH** |
| **File download** | `pymn_0001_003` | `GET /{id}/file`; dispatched download writes 'W' / `VENDOR_PAYMENT_RESULT` (ERP `04`) audit (`REQUIRES_NEW`) | **MATCH** |
| **Footer totals (Result)** | grid-summed | `searchDispatchedCurrencyTotals` (status-unfiltered) | **MATCH** (R008) |
| **Totals footer (Scheduled)** | rendered | rendered (`paginationWithTotals`) — Vendor renders it, unlike Internal Transfer | **MATCH** |
| **APPROVER (`APPRVR_NM`) sort** | sortable | View Setting seeds `SORT_YN='Y'` + glyph renders, but **no `VendorPaymentSortField` constant** → backend falls back to default ORDER BY (EXPT_RESOL_APPRVR_INFM join blocker) | **PARTIAL** (G-VEND-2; documented, backend-followup) |
| **Re-run (C3)** | `pymn_0010_002.ts` | no endpoint/service/client/UI either side | **DEAD LEGACY** (PO-deprecated; confirmed absent) |
| **Authorization** | menu auth | `payment-vendor-scheduled` / `payment-vendor-result` (two namespaces vs View-Setting screenIds `payment-vendor-payment-*`) | **MATCH** |
| **Tests** | — | 6 backend test files (Result parity, footer totals, download 'W' audit, sort field, request-approval attachments, dispatch executor) | **PARTIAL** — see below |

---

## Stale-claim corrections (this audit)

- "Controller is a stub / Phase A read-only / 13 methods" → **~21 mappings, full-stack**. The controller class Javadoc still reads "Phase A scope" / `authenticated()` — that comment is stale; the live gate is `@menuAuthz`. (Code-comment cleanup, not a functional gap.)
- "API client exposes only `requestVendorApproval`" → the full `lib/api/vendorPayment.ts` surface (16+ functions incl. 4 Banking Match) is shipped. Its file-level Javadoc also still says "Phase A" — stale comment.
- "View Setting seeds in V141/V142/V150/V160" → those files **do not exist**; the 49 vendor rows are in `V156__sys_scr_option_wecms.sql` (manual DBA SQL).
- "Re-run / C3 path" → **DEAD LEGACY**, confirmed absent.

---

## Open gaps (gap vocabulary)

| ID | Gap | Class | Owner |
|---|---|---|---|
| G-VEND-1 | No tests for the Scheduled-list `appr_type` filter, `createDraft` key-race retry, the Banking Match endpoints, or the reporting-account PATCH; no FE component tests | **MISSING** (test-missing) | dev |
| G-VEND-2 | APPROVER (`APPRVR_NM`) sort renders a glyph but the backend falls back to default ORDER BY (no whitelist constant; `EXPT_RESOL_APPRVR_INFM` join blocker) | **PARTIAL** (backend-followup) | dev |
| — | Live firm-banking dispatch flips `PROC_STAT` only with the bank daemon up; Result counts degrade to empty when dispatch tables are unreachable | **PARTIAL** (environmental, not a code gap) | infra |
| — | Stale "Phase A" Javadoc on `VendorPaymentController` + `vendorPayment.ts` | code-comment-cleanup (out of doc scope) | dev |

---

## Refactor status: Complete (full-stack)

All functional areas are **MATCH** or **APPROVED DEVIATION** (each a documented legacy-parity choice). Re-run is **DEAD LEGACY**. The only true gaps are test coverage (G-VEND-1, MISSING) and the deferred APPROVER sort (G-VEND-2, PARTIAL) — neither blocks SIT/UAT of the implemented behaviour.

# Vendor Payment — Migration Guide (JEX → Spring)

Maps the legacy JEX `pymn_0001_*` (Scheduled) / `pymn_0002_*` (Result) implementation to the refactor Spring Boot implementation. JEX paths are read-only reference under `WECMS DEV/`. Companion: migration docs _(removed — historical `migration/` folder deleted by project decision)_ · [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 1. Component mapping

| Legacy (JEX, read-only) | Refactor (slug-based) |
|---|---|
| Menu `pymn_0001_001` (Scheduled list) | Route `/payment/vendor-payment/scheduled-payment`; View Setting `payment-vendor-payment-scheduled` |
| Menu `pymn_0001_002` (Scheduled detail) | Route `/payment/vendor-payment/scheduled-payment/[id]`; View Setting `…-scheduled-detail` |
| Menu `pymn_0002_001` (Result list) | Route `/payment/vendor-payment/payment-result`; View Setting `payment-vendor-payment-result` |
| Menu `pymn_0002_002` (Result detail) | Route `/payment/vendor-payment/payment-result/[id]`; View Setting `…-result-detail` |
| `pymn_0001_001_r001` (list WSVC) | `GET /api/v1/payment/vendor-payment` → `VendorPaymentController#list` → `VendorPaymentService#list` |
| `pymn_0001_002_r001` (detail / parse) | `GET .../{id}` (detail) + `POST .../uploads/parse` → `VendorPaymentExcelParser` |
| `pymn_0001_002_c001` (save draft) | `POST .../` (multipart) → `VendorPaymentService#createDraft` |
| `comm_0192_001_c005` (approval fan-out) | `POST .../{id}/request-approval` (multipart) → shared `ApprovalBoxFanoutService#fanout` |
| `bltb_0001_003_u001` (approve/reject) | `POST .../{id}/approval-action` → shared Approval-Box approve/reject |
| `pymn_0001_001_c001` (transfer) | `POST .../{id}/dispatch` → `VendorDispatchExecutor` |
| `pymn_0001_001_d001` (delete draft) | `DELETE .../{id}` → `VendorPaymentService#delete` |
| `comm_0197_001_*` / `comm_0197_002_u001` (banking match) | `…/lines/{lineId}/match[/candidates]` + `…/matches` → `DomesticPaymentLineMatchService` |
| `comm_1001_001` (withdrawal-account picker) | `GET .../withdrawal-accounts` → shared `WithdrawalAccountListService` |
| `pymn_0002_001_r001` (result list) | `GET .../results` → `VendorPaymentResultService#list` |
| `pymn_0002_002_r001` (result detail) | `GET .../results/{id}` → `VendorPaymentResultService#detail` |
| Result XLSX export | `POST .../results/export` → `ExcelExportService` + `VendorPaymentResultExportProvider` |
| `pymn_0001_003` (file download) | `GET .../{id}/file` |
| `ScdulPymnSumr*` / `ExptResolMstr*` JEX entities | shared `internaltransfer/entity/*` + `payment/approvalbox/entity/*` |

---

## 2. Function / helper translations

| Legacy helper | Refactor replacement |
|---|---|
| `wb_get_appr_stts(...)` | **Kept** — funneled through `LegacyFunctionRepository` (status derived on read; `SCDUL_PYMN_SUMR` has no `APPR_STTS` column). `C` approved, `R` rejected (not `X`). |
| `SCDUL_PYMN_NO` seq | `InternalTransferKeyGenerator` → `wb_get_next_val` (date-prefixed, OD-2 exception) |
| `APPR_SEQ_NO` seq | `LegacyFunctionRepository.nextApprSeqNo` → `sq_appr_seq_no` |
| `DOC_NO` gen | `DocNoGenerator` → `VO_YYMMDD_NNN` (env-set-driven, Java-side exception) |
| `wb_get_bank_name_any_code` | shared `LegacyFunctionRepository` |
| Sort helper | `VendorPaymentSortField` / `VendorPaymentResultSortField` whitelists + Spring Data `Sort` |

---

## 3. Approved deviations

| # | Legacy | Refactor | Why |
|---|---|---|---|
| D1 | Two separate JSP modules (Scheduled + Result) | **single** `VendorPaymentController` covers both | one slug-scoped controller; Result endpoints override authz |
| D2 | `APPR_STTS` read off the batch | derived via `wb_get_appr_stts` over `EXPT_RESOL_MSTR` | the column does not exist in legacy JEX; status is computed on read |
| D3 | Approval Ref from `ERP_FILE_NUM` | `EXPT_RESOL_MSTR.DOC_NO` (resolved by `APPR_SEQ_NO`) | canonical home; `ERP_FILE_NUM` null in DEV (F4a/C1) |
| D4 | Result status `TRNF_YN` `Y`/`N` | route-aware `PROC_STAT` Completed `C` / In Progress `P` (C2) | legacy result status is route-aware, not a flat flag |
| D5 | Result footer filtered with the grid | footer totals computed **unfiltered** by status (R008) | footer stays stable while the list narrows |
| D6 | Per-module upload folders implicit | `<root>/<biz_key>/vendor-payment/` with read-fallback to `internal-transfer/` | per-module storage split with backward-read |
| D7 | request-approval JSON only | request-approval **multipart** (attachments → `EXPT_RESOL_FILE`) | M-1 attachment parity |

All deviations preserve forward data-migration safety (no schema drift from legacy).

---

## 4. Re-run (C3) — DEAD LEGACY

The legacy "Re-run" action (`pymn_0010_002.ts`) is **PO-deprecated and intentionally not ported**. Confirmed **absent** on both sides — no endpoint, service, client, or UI for it. Earlier docs that listed a "Re-run" / C3 path were corrected; it is dead legacy and must not be re-introduced without a fresh PO decision. (Same disposition as Internal Transfer's Re-run — `project_payment_result_sit_ready.md`.)

---

## 5. Tab / status-code mapping

| Scheduled approval filter | `appr_type` | Result transfer filter | `trnf_stts` |
|---|---|---|---|
| Not Requested | `00` (no `APPR_SEQ_NO`) | Completed | `C` |
| Pending | `04` | In Progress | `P` |
| In Progress | `03` | | |
| Approved | `01` | | |
| Rejected | `02` | | |

---

## 6. Migration status

✅ **Complete (full-stack).** Both sub-modules implemented end-to-end: Scheduled list/detail, Excel parse + draft create (multipart), request-approval (multipart + attachments), approve/reject/withdraw/change-line (shared Approval-Box), reporting-account PATCH, Banking Match ×4, dispatch (flag-gated), Result list (route-aware C/P + unfiltered footer), Result detail (per-line status + counts), Result export, file download ('W' audit). See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the area-by-area verdict. The only open items are test-coverage gaps (G-VEND-1) and the deferred APPROVER sort (G-VEND-2 — PARTIAL).

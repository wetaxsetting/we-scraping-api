# Vendor Payment — Test Scenarios (Backend)

Existing automated tests + the manual / SIT-UAT matrix. Six test files exist under `src/test/java/com/kosign/wecms/domain/payment/vendorpayment/service/`.

---

## 1. Existing automated tests

| File | Asserts |
|---|---|
| `VendorPaymentResultParityTest` | Result list/detail mirror legacy `pymn_0002_*` behaviour |
| `VendorPaymentResultFooterTotalsTest` | Result footer per-currency totals computed **unfiltered** by `trnf_stts` (R008 parity) |
| `VendorPaymentResultDownloadAuditTest` | A dispatched-batch (`trnf_yn='Y'`) file download writes the ERP 'W' / `VENDOR_PAYMENT_RESULT` (code `04`) audit row; writer is `REQUIRES_NEW` + swallows failures |
| `VendorPaymentSortFieldTest` | `VendorPaymentSortField` whitelist resolution + default fallback |
| `VendorPaymentRequestApprovalAttachmentTest` | Multipart request-approval persists attachments to `EXPT_RESOL_FILE` (and the no-file case) |
| `VendorDispatchExecutorTest` | Dispatch executor flow |

---

## 2. Scheduled list / filtering

| # | Scenario | Expected |
|---|---|---|
| S1 | List with valid `from_date`/`to_date` | Vendor rows only (`SCDUL_PYMN_TYPE_CD='01'`); totals footer rendered |
| S2 | `from_date` > `to_date` | `WBE_VALIDATION` |
| S3 | `appr_type=04` (Pending) | only Pending rows (server-side via `wb_get_appr_stts`) |
| S4 | `appr_type=00` (Not Requested) | only drafts with no `APPR_SEQ_NO` |
| S5 | Sort by an unknown field | default ORDER BY (no error) |
| S6 | Sort by APPROVER | glyph renders client-side but backend falls back to default ORDER BY (G-VEND-2, PARTIAL) |
| S7 | Detail by numeric `scdul_pymn_no` AND by `DOC_NO` | both resolve to the same batch |
| S8 | Detail unknown / wrong tenant | `WBE2060` |

## 3. Upload → create → approval

| # | Scenario | Expected |
|---|---|---|
| U1 | Parse a valid Excel | `ParseExcelResponse` with per-line validation flags |
| U2 | Parse with blocked chars | row flagged invalid (shared `SpecialCharValidator`) |
| U3 | Create draft | `SCDUL_PYMN_SUMR`(`'01'`) + lines inserted; keys via funnel; ERP `02` audit; file stored under `vendor-payment/` |
| U4 | Request approval (multipart, with attachments) | fan-out `EXPT_RESOL_MSTR`(`APPR_DV='32'`) + chain + `EXPT_RESOL_FILE` rows; `APPR_SEQ_NO` stamped |
| U5 | Request approval, no attachments | fan-out succeeds; no `EXPT_RESOL_FILE` rows |
| U6 | Delete a batch already in approval | `WBE2062` |

## 4. Approve / reject / withdraw / change-line

| # | Scenario | Expected |
|---|---|---|
| A1 | Approve (your turn, not last) | caller `P→A`; master `03`; next `W→P`; no source `APPR_STTS` write-back |
| A2 | Approve (last approver) | master `01`; status derives as `C` on read |
| A3 | Reject | caller `P→R`; downstream `W→S`; master `02`; status derives as `R` (not `X`) |
| W1 | Withdraw a Pending/In-Progress batch | master + chain deleted; `APPR_SEQ_NO` nulled (back to Draft) |
| W2 | Withdraw a finalised batch | `WBE_VALIDATION` |
| C1 | Change approval-line | chain rebuilt (line 1 `P`, rest `W`); master `00` |

## 5. Banking Match

| # | Scenario | Expected |
|---|---|---|
| BM1 | Candidates for a line | domestic-path candidate list (`VENDOR.code()`) |
| BM2 | Match a line | line matched to a bank transaction |
| BM3 | Unmatch one / unmatch all | match cleared |

## 6. Reporting-account / dispatch

| # | Scenario | Expected |
|---|---|---|
| RA1 | PATCH reporting-account on a draft line | persists `CHART_ACCT_*` |
| RA2 | PATCH once dispatched | `WBE2064` |
| DP1 | Dispatch an approved batch (cut-off open, flag on) | `TRNF_YN='Y'`; `TB_PAY_*`/`TB_FIRM_*` writes |
| DP2 | Dispatch past cut-off | cut-off guard error |
| DP3 | Dispatch a non-approved batch | approval gate error |

## 7. Result list / detail / export / download

| # | Scenario | Expected |
|---|---|---|
| R1 | Result list, `trnf_stts=C` | only Completed batches (route-aware `PROC_STAT`); footer totals **unchanged** vs unfiltered |
| R2 | Result list, `trnf_stts=P` | only In-Progress batches; footer unfiltered |
| R3 | Result list, neither/both checked | no status filter |
| R4 | Result row Approval Ref | `DOC_NO` from `EXPT_RESOL_MSTR` (not `ERP_FILE_NUM`); clickable when `appr_seq_no` present (F4b) |
| R5 | Result detail | canonical 11-col table; header counts `[success, failed, inProgress]` (`countByStatus`) |
| R6 | Export | XLSX, screenId `payment-vendor-payment-result`, ERP `04` |
| R7 | Download dispatched file | streams Excel + writes 'W' audit (ERP `04`) |
| R8 | Download with no file | `WBE2069` |

## 8. Authorization

| # | Scenario | Expected |
|---|---|---|
| Z1 | Scheduled endpoints without `payment-vendor-scheduled` | 403 |
| Z2 | Result endpoints without `payment-vendor-result` | 403 |
| Z3 | No JWT | 401 |

## 9. Coverage gaps (MISSING — G-VEND-1)

No automated test for: the Scheduled-list `appr_type` server filter (S3/S4), the `createDraft` key-race retry path, the Banking Match endpoints (BM1–BM3), or the reporting-account PATCH (RA1/RA2). No frontend (component) tests for any Vendor page.

## 10. SIT / UAT

Both sub-modules are SIT/UAT-ready (`project_payment_result_sit_ready.md` / `project_payment_result_followup_f2export_overseas_f4b.md`). The one open SIT item is a smoke of the Completed/In-Progress filter against real `TB_PAY_*` data once a local bank daemon flips `PROC_STAT` (environmental). Cross-link run records under [../../../testing/](../../../testing/) when added.

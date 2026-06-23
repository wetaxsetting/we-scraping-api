# Vendor Payment — Business Flow (Backend)

> **Frontend counterpart:** [wecms-web/docs/features/payment/vendor-payment/screen-flow.md](../../../../../wecms-web/docs/features/payment/vendor-payment/screen-flow.md).

Traces the full lifecycle for both sub-modules:

```
Scheduled Payment:  Excel upload → parse → create draft → request approval (multipart)
                    → approve / reject / withdraw / change approval-line
                    → Banking Match (per line) → dispatch (Transfer)
Payment Result:     result inquiry (route-aware C/P filter) → result detail (per-line status)
                    → export (XLSX) / original-file download ('W' audit)
```

All identity (`bizKey`, `userId`, `isAdmin`) is read from `TenantContext` (populated by `JwtAuthenticationFilter` from the signed JWT) — never from request input. Both sub-modules live behind one controller, `VendorPaymentController`. The Scheduled flow is gated by `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-scheduled')")` (class level); the Result endpoints override to `'payment-vendor-result'`.

Vendor reuses the shared `internaltransfer.dto.*` / `internaltransfer.entity.*` types and `payment/approvalbox/`, `payment/match/`, `payment/common/`, `payment/dispatch/`, `payment/export/` infrastructure verbatim. The only Vendor-specific behaviour is the `SCDUL_PYMN_TYPE_CD = '01'` discriminator (applied at every repository call via `ScdulPymnTypeCd.VENDOR.code()`), the `vendor-payment` audit `WSVC_ID`, the `vendor-payment/` storage subfolder, and the ERP type codes (`02` Scheduled, `04` Result).

---

## 0. Legacy reference (JEX)

| Step | Legacy JEX |
|---|---|
| Scheduled list | `pymn_0001_001` (menu) + `pymn_0001_001_r001` (WSVC) |
| Scheduled detail | `pymn_0001_002` + `pymn_0001_002_r001` |
| Excel upload / parse / save | `pymn_0001_002_r001` (parse) + `pymn_0001_002_c001` (save) |
| Request approval | `comm_0192_001_c005` (approval fan-out) |
| Approve / reject | `bltb_0001_003_u001` |
| Dispatch (Transfer) | `pymn_0001_001_c001` |
| Banking Match | `comm_0197_001_*` / `comm_0197_002_u001` (domestic path) |
| Result list | `pymn_0002_001` + `pymn_0002_001_r001` |
| Result detail | `pymn_0002_002` + `pymn_0002_002_r001` |
| Withdrawal-account picker | `comm_1001_001` |

The legacy "Re-run" action (`pymn_0010_002.ts`) is **DEAD LEGACY** — PO-deprecated; no refactor endpoint, service, or client exists (confirmed absent — see [migration-guide.md §Re-run](migration-guide.md), [refactor-gap-analysis.md](refactor-gap-analysis.md)).

---

## 1. Scheduled Payment — list (`GET /api/v1/payment/vendor-payment`)

`VendorPaymentService.list(...)` — `@Transactional(readOnly=true)`:

1. Resolve `bizKey` from `TenantContext`; validate `from_date` / `to_date` (`YYYYMMDD`).
2. `ScdulPymnSumrRepository.search(...)` filtered by `bizKey`, `SCDUL_PYMN_TYPE_CD = '01'` (Vendor discriminator), `REG_DTM` date window, optional keyword, optional **approval-status filter** (`appr_type` → `00` Not Requested / `04` Pending / `03` In Progress / `01` Approved / `02` Rejected), sort whitelist (`VendorPaymentSortField`) + direction, paging.
3. **Status derivation.** `SCDUL_PYMN_SUMR` has **no `APPR_STTS` column** in the legacy JEX schema. Approval status is derived per row via the legacy DB function `wb_get_appr_stts(...)` through `LegacyFunctionRepository` (codes: `C` approved, `R` rejected — **not** `X`), over the `EXPT_RESOL_MSTR` join. The `appr_type` filter predicate is also applied server-side through the same function.
4. The list response carries the totals footer (`paginationWithTotals` — **rendered** for Vendor, unlike Internal Transfer).

## 2. Scheduled Payment — detail (`GET .../{id}`)

`VendorPaymentService.detail(id)` — accepts either the internal numeric `scdul_pymn_no` OR the user-visible `DOC_NO` (`VO_YYMMDD_NNN`); returns master fields (withdrawal bank/account, total, requestor, **Approval Ref = `EXPT_RESOL_MSTR.DOC_NO`**) + per-line list (line order by `SCDUL_PYMN_DETL_NO`) with the per-line chart-of-accounts (reporting-account) assignment + approval attachments (`EXPT_RESOL_FILE` ∪ `EXPT_RESOL_PAY_EVID_FILE`). `WBE2060` when the batch is missing / wrong tenant.

## 3. Excel upload → draft create

1. **Withdrawal accounts** (`GET .../withdrawal-accounts`) — firm-banking-capable source accounts (shared `WithdrawalAccountListService`, legacy `comm_1001_001`).
2. **Parse** (`POST .../uploads/parse`, multipart `file_data` + `bank_cd`) — `VendorPaymentExcelParser` walks the workbook (Apache POI) and validates each row (blocked-chars rules via the shared `SpecialCharValidator`); returns `ParseExcelResponse` (parsed lines + per-line validation flags). Vendor reuses the Internal Transfer template format.
3. **Create draft** (`POST .../`, multipart `request` JSON + optional `file_data`) — `VendorPaymentService.createDraft(...)` inserts `SCDUL_PYMN_SUMR` (`SCDUL_PYMN_TYPE_CD='01'`) + one `SCDUL_PYMN_SUMR_DETL` per line; persists the original Excel under `<root>/<biz_key>/vendor-payment/<save_file_nm>`. **Key generation:** `SCDUL_PYMN_NO` via `InternalTransferKeyGenerator` (date-prefixed, funnels `wb_get_next_val`, OD-2 exception); `SCDUL_PYMN_DETL_NO` seeded once via `nextScdulPymnDetlNo` then incremented in Java. Writes an ERP audit row (`TRNS_TYPE_CD = '02'`, Vendor Scheduled Payment).

## 4. Request approval (`POST .../{id}/request-approval`, **multipart**)

`VendorPaymentService.requestApproval(id, req, attachments)` — JSON `request` part + optional `attachments` parts:

1. Delegate the approval fan-out to the shared `ApprovalBoxFanoutService.fanout(...)` — inserts `EXPT_RESOL_MSTR` (`APPR_DV='32'` Vendor, `APPR_TYPE_CD='00'` Pending), allocates `DOC_NO` (`DocNoGenerator` → `VO_YYMMDD_NNN`) + `APPR_SEQ_NO` (`LegacyFunctionRepository.nextApprSeqNo` → global `sq_appr_seq_no`), inserts one `EXPT_RESOL_APPRVR_INFM` per approver (line 1 `P`, rest `W`).
2. Persist attachments into `EXPT_RESOL_FILE` via `ApprovalAttachmentWriter` (one row per file; a no-file submit omits the parts).
3. Stamp the source batch's `APPR_SEQ_NO` so the Scheduled list/detail derive the approval status. Fire best-effort notifications (`ApprovalNotificationService`, BLTB tables).
4. Audit `WSVC_ID = "vendor-payment"`.

`WBE2062` when the batch is already in approval (`APPR_SEQ_NO` set) and a delete/re-submit is attempted.

## 5. Approve / reject / withdraw / change approval-line

These all route through the shared Approval-Box services (so behaviour is identical across all payment modules — see [approval-box/business-flow.md §3–5](../approval-box/business-flow.md)):

| Action | Endpoint | Effect |
|---|---|---|
| Approve / reject | `POST .../{id}/approval-action` | caller's approver row `P→A` / `P→R`; master `APPR_TYPE_CD` → `01`/`03`/`02`; promote next `W→P` (approve) or `W→S` cascade (reject). No source `APPR_STTS` write-back (column absent). |
| Withdraw | `DELETE .../{id}/approval` | `ApprovalBoxFanoutService.withdrawApproval` — guards Pending/In-Progress; deletes master + chain; nulls source `APPR_SEQ_NO` (back to Draft). |
| Change approval-line | `PUT .../{id}/approval-line` | rebuilds the chain from the picked template (line 1 `P`, rest `W`); resets master to `00`. |

Status is two-level (`EXPT_RESOL_MSTR.APPR_TYPE_CD` document-level + `EXPT_RESOL_APPRVR_INFM.APPRVR_STTS` per-approver) and derived on read — there is no `APPR_STTS` column on `SCDUL_PYMN_SUMR`.

## 6. Banking Match (per line)

`DomesticPaymentLineMatchService`, always called with `ScdulPymnTypeCd.VENDOR.code()`:

| Action | Endpoint | Legacy |
|---|---|---|
| Candidates | `GET .../{id}/lines/{lineId}/match/candidates` | `comm_0197_001_r001` |
| Match | `POST .../{id}/lines/{lineId}/match` | `comm_0197_001_c001` |
| Unmatch one | `DELETE .../{id}/lines/{lineId}/match` | `comm_0197_002_u001` |
| Unmatch all | `DELETE .../{id}/matches` | `comm_0197_001_u001` |

## 7. Dispatch / Transfer (`POST .../{id}/dispatch`)

`VendorPaymentService.dispatch(id, req)` → `VendorDispatchExecutor` — the production-grade firm-banking dispatch flow (flag-gated inert by default; writes `TB_FIRM_*` / `TB_PAY_*` when enabled). Cut-off-time guarded (`CutoffValidator`) and approval-gated (only an approved batch dispatches). Stamps `TRNF_YN='Y'`. See the shared dispatch infrastructure ([payment hub README §8](../README.md), batch refactor memory).

## 8. Payment Result — list (`GET .../results`)

`VendorPaymentResultService.list(...)` — `@Transactional(readOnly=true)`:

1. `ScdulPymnSumrRepository.searchDispatched(...)` — only dispatched batches the user may see, partitioned by `SCDUL_PYMN_TYPE_CD='01'`.
2. **Status filter (route-aware, C2).** `trnf_stts` → Completed `C` / In Progress `P`, applied as a route-aware `PROC_STAT` EXISTS inside `searchDispatched` — **not** the old `TRNF_YN`-shaped `Y`/`N`. `null` / both / neither = no filter. The frontend maps the Completed / In-Progress checkboxes to `C`/`P`.
3. **Footer totals (R008 parity).** Per-currency totals come from `searchDispatchedCurrencyTotals(...)` — computed **unfiltered by status** so the footer stays stable while the row list narrows.
4. Each row carries the **Approval Ref** (`DOC_NO`) + **Approval Title** (`TITL`) resolved by `APPR_SEQ_NO` from `EXPT_RESOL_MSTR` (batched, no N+1). `DOC_NO`'s canonical home is `EXPT_RESOL_MSTR.DOC_NO`, **never** `SCDUL_PYMN_SUMR.ERP_FILE_NUM` (null in DEV).

## 9. Payment Result — detail (`GET .../results/{id}`)

`VendorPaymentResultService.detail(id)` — master header + canonical 11-column per-line result table; per-line status from the dispatch-result resolver (`proc_stat` / `rs_cd`). Header tab counts `[success, failed, inProgress]` derived server-side via `countByStatus(...)` (shared rule with the frontend `deriveResultStatus.ts`). Carries the clickable Approval Ref (F4b — `appr_seq_no` + `DOC_NO`).

## 10. Result export / original-file download

- **Export** (`POST .../results/export`) — shared `ExcelExportService` + `VendorPaymentResultExportProvider` (`screenId = "payment-vendor-payment-result"`, `erpResultTrnsTypeCd = "04"`).
- **Original-file download** (`GET .../{id}/file`) — streams the stored Excel (RFC 5987 filename). For a **dispatched** batch (`trnf_yn='Y'`) it writes an ERP `VENDOR_PAYMENT_RESULT` ('W', code `04`) audit row (F2 'W' audit; the writer is `REQUIRES_NEW` and swallows failures). `WBE2069` when no file is behind the batch.

## 11. Persistence summary

| Table | Write |
|---|---|
| `SCDUL_PYMN_SUMR` (`SCDUL_PYMN_TYPE_CD='01'`) | full row on create; `APPR_SEQ_NO` stamped on request-approval / nulled on withdraw; `TRNF_YN='Y'` on dispatch |
| `SCDUL_PYMN_SUMR_DETL` | one row per line on create; reporting-account on PATCH; match columns on Banking Match |
| `EXPT_RESOL_MSTR` / `EXPT_RESOL_APPRVR_INFM` | fan-out on request-approval; `APPR_TYPE_CD` / `APPRVR_STTS` on approve/reject; deleted on withdraw |
| `EXPT_RESOL_FILE` | attachment rows on request-approval |
| `TB_FIRM_*` / `TB_PAY_*` | dispatch (flag-gated) |
| `AUDIT_LOGS_INFM` | every mutation, `WSVC_ID = "vendor-payment"`; result download 'W' (ERP `04`) |

**No `APPR_STTS` write-back** anywhere on `SCDUL_PYMN_SUMR` — the column does not exist in the legacy JEX schema; status is always derived on read.

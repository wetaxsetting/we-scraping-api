# Payment Excel Upload → Create Batch — Cross-Module Legacy Verification

- **Scope:** the *Excel upload → create batch* path in **all five** payment modules — Vendor, Expense, Payroll, Overseas, Internal Transfer.
- **Legacy WSVC:** `pymn_0001_002_{r001,c001}` · `pymn_0003_002_{r001,c001}` · `pymn_0005_002_{r001,c001}` · `pymn_0007_003_{r001,c001}` · `pymn_0009_002_{r001,c001}`
- **System pair:** Legacy JEX (`WECMS DEV/`, source of truth) ⇄ Refactor (`wecms-api/` + `wecms-web/`)
- **Date:** 2026-07-25
- **Trigger:** partner business-integration matrix **row #24** ("Upload Excel → tạo lô", rated *"Low — partial/different … parsed server-side with Apache POI (legacy parses on client). Behavior-equivalent, different mechanism."*).
- **Status:** **CLOSED** — the row #24 rationale is corrected, and 5 divergences found by this pass are **implemented + verified** (full backend suite green, 1930 tests, 0 failures).
- **Method:** Analyze legacy end-to-end → compare against refactor code (not against the earlier audit docs) → state impact → approval → implement → verify.

> **Relationship to the per-module audits.** The four module audits dated 2026-06-29
> ([vendor](vendor-payment-excel-upload-audit.md), [expense](expense-payment-excel-upload-audit.md),
> [payroll](payroll-payment-excel-upload-audit.md), [internal-transfer](internal-transfer-excel-upload-audit.md))
> plus [overseas](overseas-payment-excel-upload-audit.md) remain the per-module records.
> This document is a **cross-module verification of the current code** — it re-checked
> their closed claims rather than trusting them, which is how F1 (a wrong "at parity"
> claim in the payroll audit) was found.

---

## 1. The row-#24 premise was wrong — legacy also parses server-side

The partner note (and an earlier answer of ours that accepted it) says legacy parses the workbook **in the browser**. It does not.

Legacy is a **two-step server-side flow**, the same shape as the refactor:

| Step | Legacy | What it does |
|---|---|---|
| 1. Parse / preview | `PymnHelper.importDataFromExcel` → `readExcel` ([`PymnHelper.java:153`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java#L153), [`:303`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java#L303)) | Opens the file with Apache POI `WorkbookFactory`, validates the header row (row index 3), walks the 10 columns from row 4, resolves bank / contact / deposit / ChartAcct, returns rows + per-cell errors + `TOT_CNT` / `SUC_CNT` / `ERR_CNT`. |
| 2. Create batch | `PymnHelper.uploadPaymentExcel` ([`:1301`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java#L1301)) | Receives **the file again** *plus* the reviewed `REC` rows; writes `SCDUL_PYMN_SUMR` + `SCDUL_PYMN_SUMR_DETL`, stores the file on the batch, writes `ERP_TRNS_HSTR`. |

Payroll and Overseas do not use `PymnHelper` — they inline their own copies of both steps in the JSP action files — but the mechanism is identical.

**Corrected verdict for row #24:** *implemented, same mechanism* — both sides parse server-side with Apache POI in a parse→create flow. The earlier "different mechanism / legacy parses on client" rationale should be dropped. The genuine (and much narrower) difference is **what step 2 receives**: legacy re-posts file + rows and re-validates every row; the refactor posts file + already-parsed rows and re-validates a subset (§4 R1).

## 2. Module coverage — all five implemented

| Legacy | Module | Refactor parse | Refactor create |
|---|---|---|---|
| `pymn_0001_002` | Vendor | `VendorPaymentExcelParser` | `VendorPaymentService.doCreateDraft` |
| `pymn_0003_002` | Expense | `ExpensePaymentExcelParser` | `ExpensePaymentService.doCreateDraft` |
| `pymn_0005_002` | Payroll | `PayrollPaymentExcelParser` | `PayrollPaymentService.doCreateDraft` |
| `pymn_0007_003` | Overseas | `OverseasPaymentExcelService.preview` | `OverseasPaymentExcelService.submit` |
| `pymn_0009_002` | Internal Transfer | `InternalTransferExcelParser` | `InternalTransferService.doCreateDraft` |

> Overseas lives in `domain/payment/service/` (not an `overseas*` sub-package) and is named
> `…ExcelService` rather than `…ExcelParser`, which is why a package-shaped search suggests it
> is missing. It is fully implemented.
>
> **`pymn_0007_002` is NOT the Excel path** — it is the manual single-transaction Overseas
> screen. Mis-citing it is the root cause of F2 and F3 below.

Verified directly in code as at parity: header validation, VND/USD whitelist, single-currency and single-network guards, ChartAcct lookup, CITAD→6-digit `BANK_CD` resolution, `CITAD_BANK_CD` (`"000"` NAPAS / 3-digit CITAD), `SCDUL_PYMN_TYPE_CD`, `TRNF_YN='N'`, `wb_get_next_val` key funnel, `ERP_TRNS_HSTR` type codes, and payroll's four specifics (remarks → `WDRL_ACCT_RMRK`, forced CITAD, VCB-VND/USD block, 1000-row cap).

## 3. Findings — all fixed 2026-07-25

| # | Sev | Finding | Fix |
|---|---|---|---|
| **F1** | **MEDIUM** | **Payroll `WDRL_CCY_CD` held the transfer currency.** Legacy [`pymn_0005_002_c001_act.jsp:78,84`](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0005_002_c001_act.jsp#L78) writes `CCY_CD` = first row's `TRNF_CCY_CD` and `WDRL_CCY_CD` = `input.CCY_CD` (the account's own currency). The refactor set **both** from `req.ccyCd()`, and the payroll frontend sends `ccy_cd = trnf_ccy_cd = batch currency`. Same bug class as vendor GAP-4 / expense G4 / internal G3 — those were fixed, payroll was missed, and the payroll audit's I4 wrongly listed it as "at parity". | `PayrollPaymentService.resolveWithdrawalCcyCd(...)` resolves `ACCT_MSTR.CCY_CD` by `(biz_key, bank_cd, acct_no)`, falling back to the transfer currency + a `warn` log. Mirrors vendor. |
| **F2** | **MEDIUM** | **Overseas `WDRL_CCY_CD` was never set at upload.** Legacy [`pymn_0007_003_c001_act.jsp:108`](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_003_c001_act.jsp#L108) sets it from `input.CCY_CD`. `OverseasPaymentExcelService.submit` never called `setWdrlCcyCd`, and the upload DTO has no such field — the column stayed **NULL** on every uploaded batch until an unrelated edit (`OverseasPaymentService:1590`) happened to populate it. | Same server-side `ACCT_MSTR` resolution, from `req.wdrl_bank_cd()` + `req.wdrl_acct_no()`. No DTO/frontend change needed. |
| **F3** | LOW/MED | **Overseas `TRNF_DT` was stamped at draft creation.** The code cited `pymn_0007_002_c001:148` — the **manual** screen, not the Excel-batch path. Legacy `pymn_0007_003_c001` sets `TRNF_YN='N'` and leaves `TRNF_DT` unset until dispatch. Ironically the same file had *already* corrected `OVRS_PYMN_TYPE_CD` for exactly this wrong-source reason; the sibling field was missed. | Stamp removed. **Safety verified:** every `trnf_dt`-filtered query also requires `trnf_yn='Y'` (result list + `sumResultByCurrency`), and the scheduled/draft list filters on `reg_dtm` — so drafts are unaffected. Dispatch still stamps `TRNF_YN='Y'` + `TRNF_DT`. |
| **F4** | LOW | **Uploaded workbook was optional at save.** Legacy requires it ([`pymn_0001_002_c001_act.jsp:54-55`](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0001_002_c001_act.jsp#L54), overseas `FULL_PATH:48`) and both the file metadata and the `ERP_TRNS_HSTR` row live inside `if (file != null)`. A fileless request therefore produced a batch with a null **File Name** and **no ERP audit row** — a state legacy cannot produce. | New shared `PaymentUploadFileGuard.requireUploadedFile(...)` called at the entry of all five create paths → `WBE_VALIDATION` (`"file_data: required"`), mirroring legacy `validateRequired`/`validateFile`. Kept as a service-side business error, not a `required=true` multipart part, so the failure returns the standard `ApiResponse` envelope instead of a framework `MissingServletRequestPartException` → `WBE9999`. |
| **F5** | LOW | **Mixed CITAD/NAPAS guard ran only at parse.** Legacy calls `validateUploadedPayments` as the *first* statement of `uploadPaymentExcel` ([`:1305`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java#L1305) → [`:1262`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java#L1262), `WBE1191`), i.e. **again at save**. | `uploadConsistencyValidator.assertSingleNetwork(...)` re-asserted in Vendor / Expense / Internal `doCreateDraft` (reusing the component the parsers already use). **Not** added to Payroll — legacy payroll has no such check and force-sets CITAD; **not** to Overseas — no CITAD/NAPAS dimension. |

### 3.1 Benign difference (recorded, not changed)

- **Row cap.** Legacy `readExcel` caps at row **index** 500 → ≈497 data rows ([`:316-317`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java#L316)); Vendor / Expense / Internal read 500 data rows (`FIRST_DATA_ROW + MAX_ROWS - 1`). Both truncate silently; the refactor is marginally more permissive. Payroll's cap is 1000 on both sides. No change — tightening would drop rows a user supplied.

## 4. Remaining open item

**R1 — save-side row re-validation (pre-existing, still partly open).** Legacy `uploadPaymentExcel` re-runs `ckDepositNo` / `ckContactCode` / `ckRecName` at save and throws `WBE0404` ([`:1369-1441`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java#L1369)). Current refactor state:

- Blocked-character re-validation at save: **implemented** in all five (`PaymentBlockedCharGuard`).
- Single-network re-validation at save: **implemented** by F5 above.
- Contact-identity re-derivation from `HAND_TRCO_KEY` at save: **implemented for Expense only** (`ExpensePaymentService`, audit G5; covered by `ExpensePaymentCreateDraftTest`).
- Deposit-account (`ckDepositNo`) and NAPAS recipient-name (`ckRecName`) re-validation at save: **not implemented** in any module. Note `ckRecName`'s legacy bank inquiry is itself a silent no-op when the inquiry returns null, so today's observable behaviour often matches.

This was deferred by the 2026-06-29 audits (vendor GAP-2/GAP-5, expense G5, internal G7) and stays deferred: it duplicates parse-time validation, needs contact/deposit repositories wired into three more services, and warrants its own test matrix. It is a **hardening** item against direct API calls, not a UI-visible defect. Recommend a dedicated MR.

## 5. Files changed (2026-07-25)

**Backend (`wecms-api`)**

| File | Change |
|---|---|
| `domain/payment/common/PaymentUploadFileGuard.java` | **New** — shared "workbook mandatory at save" guard (F4). |
| `domain/payment/payrollpayment/service/PayrollPaymentService.java` | F1 (`resolveWithdrawalCcyCd` + `AcctMstrRepository`), F4. |
| `domain/payment/service/OverseasPaymentExcelService.java` | F2 (`resolveWithdrawalCcyCd` + `AcctMstrRepository`), F3 (`TRNF_DT` stamp removed), F4. |
| `domain/payment/vendorpayment/service/VendorPaymentService.java` | F4, F5. |
| `domain/payment/expensepayment/service/ExpensePaymentService.java` | F4, F5. |
| `domain/payment/internaltransfer/service/InternalTransferService.java` | F4, F5. |

**Tests**

| File | Change |
|---|---|
| `PayrollPaymentUploadSaveTest` | New `AcctMstrRepository` mock + **2 new tests** locking F1 (account ccy ≠ transfer ccy, and the fallback); real workbook + `@TempDir` storage for F4. |
| `ExpensePaymentCreateDraftTest`, `InternalTransferCreateDraftTest` | Real workbook + `@TempDir` storage + `UploadConsistencyValidator` mock (F4/F5). |

**Frontend (`wecms-web`)** — **no change required.** F1/F2 are resolved server-side from `ACCT_MSTR`, so no DTO or payload change; F4 is already satisfied (every upload dialog's `handleUpload(file: File, …)` is non-nullable and each `create*` has exactly one call site, the dialog). Overseas sends `file: File` unconditionally.

## 6. Verification

```
./gradlew compileJava   → BUILD SUCCESSFUL
./gradlew test          → BUILD SUCCESSFUL — 1930 tests, 0 failures, 20 skipped
```

Legacy behaviour was re-read from source for every finding; no JEX file was modified (read-only tree).

## 7. Tables touched

All in the [legacy table inventory](../references/database/legacy-table-inventory.md): `SCDUL_PYMN_SUMR`, `SCDUL_PYMN_SUMR_DETL`, `OVRS_PYMN_SUMR`, `OVRS_PYMN_SUMR_DETL`, `ACCT_MSTR` (read-only lookup), `TB_FIRM_BANK_CODE` (read-only), `ERP_TRNS_HSTR`, `AUDIT_LOGS_INFM`. **No DDL, no schema change, no new table** — every fix writes existing legacy columns, which is the point: F1/F2 populate columns legacy always populated and the refactor left wrong or NULL, improving JEX → refactor data-migration parity.

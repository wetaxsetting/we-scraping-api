# Payroll Payment — Refactor Feature Spec (STUB)

## Screens (this feature)

- [scheduled-payment.md](scheduled-payment.md)
- [scheduled-payment-details.md](scheduled-payment-details.md)
- [payment-result.md](payment-result.md)
- [payment-result-details.md](payment-result-details.md)


| Field | Value |
|---|---|
| System | Refactor (Spring Boot + Next.js) |
| Slug | `payroll` (frontend folder + route base) / `payroll-payment` (backend package + audit `WSVC_ID`) — the frontend prefers the shorter slug; the backend tags itself with `-payment` for symmetry with Vendor/Expense. |
| Module | Payment → Payroll |
| JEX counterparts | `pymn_0005_*` (Payroll Scheduled) + `pymn_0006_*` (Payroll Result) — labels confirmed at [comm_0190_003_view.jsp:588](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L588) (Scheduled) and [comm_0190_003_view.jsp:597-599](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp) (Result). |
| Discriminator | `SCDUL_PYMN_TYPE_CD = "00"` — verified at [pymn_0005_001_r001_act.jsp:173](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0005_001_r001_act.jsp#L173) + refactor enum `ScdulPymnTypeCd.PAYROLL.code()`. |
| Audit `WSVC_ID` | `payroll-payment` — declared as a `static final String` at [PayrollPaymentService.java:94](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentService.java#L94). |
| REST URI base | `/api/v1/payment/payroll-payment` |
| Frontend route base | `/payment/payroll` |
| Status | **Implementation status: documented retroactively against existing code as of 2026-05-22.** Backend + frontend pages exist; this stub preserves the implementation in the docs surface so it isn't lost. A full-spec follow-up MR is needed to bring this doc to parity with [internal-transfer.md](../internal-transfer/README.md). |
| Baseline | [internal-transfer.md](../internal-transfer/README.md) — Payroll mirrors this shape; see [_payment-module-overview.md](../_payment-module-overview.md). |

> **Special note.** Payroll uses `SCDUL_PYMN_TYPE_CD = "00"`. The `scdul_pymn_sumr_chk01` CHECK constraint was relaxed in [V146__scdul_pymn_sumr_chk01_allow_payroll.sql](../../../../src/main/resources/db/migration/V146__scdul_pymn_sumr_chk01_allow_payroll.sql) to admit `'00'`. Don't tighten the CHECK without re-allowing all four siblings.

---

## 1. Files in use

### 1.1 Backend ([`com.kosign.wecms.domain.payment.payrollpayment`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment))

- **Controller:** [`PayrollPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/controller/PayrollPaymentController.java) — full write-action endpoint set (delete, request-approval, approval-action, dispatch, parse, create, file-download). Discriminator `SCDUL_PYMN_TYPE_CD = '00'` is pinned at L53.
- **Services:**
  - [`PayrollPaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentService.java) — `WSVC_ID = "payroll-payment"` at L94; `ScdulPymnTypeCd.PAYROLL.code() = "00"` discriminator at every repository call.
  - `PayrollPaymentResultService.java` — Result-side read service.
  - `PayrollPaymentExcelParser.java` — Apache POI walk (uses `PymnType.PAYROLL_PAYMENT("PAYR")` for blocked-chars lookup).
  - `PayrollPaymentSortField.java` + `PayrollPaymentResultSortField.java` — sort whitelists.
- **Export:** [`PayrollPaymentResultExportProvider.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/export/PayrollPaymentResultExportProvider.java) — implements `ExportRowProvider`.
- **Shared infrastructure consumed** — same as other siblings per [_payment-module-overview.md §2](../_payment-module-overview.md).

### 1.2 Frontend ([`components/payment/payroll/`](../../../../../wecms-web/components/payment/payroll))

| File | Role |
|---|---|
| [`ScheduledPaymentPage.tsx`](../../../../../wecms-web/components/payment/payroll/ScheduledPaymentPage.tsx) | Scheduled list (composes `<PaymentScheduledBatchListPage>`). Exports as `PayrollScheduledPaymentPage`. |
| [`ScheduledPaymentDetailPage.tsx`](../../../../../wecms-web/components/payment/payroll/ScheduledPaymentDetailPage.tsx) | Scheduled detail (composes `<PaymentDetailPage>`). |
| [`PaymentResultPage.tsx`](../../../../../wecms-web/components/payment/payroll/PaymentResultPage.tsx) | Result list (composes `<PaymentResultBatchListPage>`). |
| [`PaymentResultDetailPage.tsx`](../../../../../wecms-web/components/payment/payroll/PaymentResultDetailPage.tsx) | Result detail (composes `<ResultDetailView>` with a **Payroll-specific 5-column variant** per Figma — NOT the canonical 11-column `resultDetailColumns`). |
| [`PayrollPaymentUploadDialog.tsx`](../../../../../wecms-web/components/payment/payroll/PayrollPaymentUploadDialog.tsx) | Thin wrapper over `<PaymentExcelUploadDialog>` — Payroll-specific columns (employee id + salary breakdown). |
| `types.ts` | Local component types. |

### 1.3 Routes ([`app/(dashboard)/payment/payroll/`](../../../../../wecms-web/app/(dashboard)/payment/payroll))

| Route | page.tsx export | Imported component |
|---|---|---|
| `scheduled-payment/page.tsx` | `PaymentPayrollScheduledPage` | `PayrollScheduledPaymentPage` |
| `scheduled-payment/[id]/page.tsx` | `PaymentPayrollScheduledDetailRoute` | `PayrollScheduledPaymentDetailPage` |
| `payment-result/page.tsx` | `PaymentPayrollResultPage` | `PayrollPaymentResultPage` |
| `payment-result/[id]/page.tsx` | `PaymentPayrollResultDetailRoute` | `PayrollPaymentResultDetailPage` |

### 1.4 API client + SCREEN_IDS + Flyway

- API client: [`lib/api/payrollPayment.ts`](../../../../../wecms-web/lib/api/payrollPayment.ts) (payroll approval endpoints; broader surface lands in follow-up MR).
- SCREEN_IDS — 4 constants in [`lib/viewSettingScreens.ts`](../../../../../wecms-web/lib/viewSettingScreens.ts):
  - `PAYMENT_PAYROLL_PAYMENT_SCHEDULED` = `"payment-payroll-payment-scheduled"`
  - `PAYMENT_PAYROLL_PAYMENT_SCHEDULED_DETAIL` = `"payment-payroll-payment-scheduled-detail"`
  - `PAYMENT_PAYROLL_PAYMENT_RESULT` = `"payment-payroll-payment-result"`
  - `PAYMENT_PAYROLL_PAYMENT_RESULT_DETAIL` = `"payment-payroll-payment-result-detail"`
- View Setting seed: [V144__payroll_payment_view_setting_seed.sql](../../../../src/main/resources/db/migration/V144__payroll_payment_view_setting_seed.sql).
- CHECK relaxation: [V146__scdul_pymn_sumr_chk01_allow_payroll.sql](../../../../src/main/resources/db/migration/V146__scdul_pymn_sumr_chk01_allow_payroll.sql) — must be re-asserted by any future migration that touches `scdul_pymn_sumr_chk01`.

---

## 2. Divergence from the Internal Transfer baseline

| Concern | Internal Transfer | Payroll |
|---|---|---|
| Controller split | Two controllers (Scheduled + Result) | **Single** `PayrollPaymentController` covers both |
| Subroute names | `internal-transfer/scheduled-transfer` + `internal-transfer/transfer-result` | `payroll/scheduled-payment` + `payroll/payment-result` (note: frontend route base is `/payroll`, not `/payroll-payment`) |
| Result list totals footer | **Omitted** | **Rendered** (single-currency typically, but the shared `<PaymentResultBatchListPage>` `totals` prop is still passed) |
| **Result detail table** | 11-column canonical `resultDetailColumns` (Date/Time, Deposit Bank, Recipient Name, Transfer Amount, Remarks, Results, Result Date/Time, Reporting Account, Description, Document No., Banking Transaction ID) | **5-column Payroll-specific layout** per Figma — narrower row, employee-centric columns. This is the most prominent UX divergence among the four siblings. Backed by a Payroll-specific column array in `<PayrollPaymentResultDetailPage>` rather than the default `resultDetailColumns`. |
| Upload-dialog column set | recipient acct, name, bank, network, amount, description, doc no, note | Payroll-specific — employee id + salary breakdown columns (gross, deductions, net, etc.); exact list: follow-up MR documents from `<PayrollPaymentUploadDialog>`'s `COLUMNS` array. |
| Discriminator value | `"03"` | **`"00"`** (and the `scdul_pymn_sumr_chk01` CHECK constraint had to be relaxed in V146 to admit it) |
| Audit `WSVC_ID` | `internal-transfer` | `payroll-payment` |
| Key-sequence helper table | `INTERNAL_TRANSFER_KEY_SEQ` | Payroll maintains its own per-(tenant, day) sequence — table name TBD-by-code-inspection |
| Slug asymmetry | matches across all surfaces (`internal-transfer`) | Frontend `payroll`, backend `payroll-payment`, audit `payroll-payment` — the only sibling with a slug asymmetry. Confirmed acceptable; documented here so a future audit doesn't try to "fix" it. |
| Payroll-period concept | n/a | Payroll has a **payroll-period selector** above the file pane in the upload dialog (the per-feature extra-step content), distinct from Internal Transfer's withdrawal-account picker. |

Per [_payment-module-overview.md §2 + §3](../_payment-module-overview.md), Payroll consumes the shared infrastructure verbatim.

---

## 2b. Scheduled Payment View Setting — legacy column parity (V232, 2026-05-31)

Payroll Scheduled (`payment-payroll-payment-scheduled`) was the last Scheduled sibling on the pre-V217 column set. V232 brings it to legacy `pymn_0005_001` parity, mirroring Internal Transfer (V217) **plus** the Payroll-only `APPR_FUND_TP` (Payroll Type) column.

**Column set (CHECKBOX + 11 data + ETC):**

| disp_ord | FIELD_ID | Label | size | sort_yn | Refactor source | Sortable? |
|---|---|---|---|---|---|---|
| 0 | CHECKBOX | Checkbox | 40px | N | — | — |
| 1 | REG_DTM | Date/Time | 105px | Y | `ScdulPymnSumr.regDtm` | ✅ |
| 2 | FILE_NM | File Name | 290px | Y | `ScdulPymnSumr.orgFileNm` | ✅ |
| 3 | TOT_TRNS_CNT | No. of Transactions | 150px | Y | `ScdulPymnSumr.totTrnsCnt` | ✅ |
| 4 | TOTAL_TRAN_AMT | Total Transfer Amount | 200px | Y | `ScdulPymnSumr.totalTranAmt` | ✅ |
| 5 | PAY_DATE | Scheduled Date | 150px | Y | `ScdulPymnSumr.payTranDt` (`extraColumn`) | ✅ |
| 6 | APPR_STTS | Approval Status | 170px | Y | `ScdulPymnSumr.apprStts` | ✅ |
| 7 | APPR_FUND_TP | Payroll Type | 150px | Y | `ExptResolMstr.apprFundTp` (`extraColumnAfterStatus`) | ⚠️ display-only |
| 8 | DRAFT_USER_NM | Requestor | 100px | Y | `ScdulPymnSumr.regId` | ✅ |
| 9 | APPRVR_NM | Approver | 175px | Y | approval-line join | ⚠️ display-only |
| 10 | APPR_DOC_NO | Approval Ref. | 150px | Y | `ScdulPymnSumr.erpFileNum` | ✅ |
| 11 | TITL | Approval Title | 250px | Y | `ScdulPymnSumr.titl` | ✅ |
| 99 | ETC | (action) | 150px | N | — | — |

- **`APPR_FUND_TP` (Payroll Type)** value semantics: `"0"`→Salary, `"1"`→Bonus, else blank (legacy [pymn_0005_001.ts:1054-1064](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/pymn/pymn_0005_001.ts)). It and `PAY_DATE` both join from `EXPT_RESOL_MSTR` on `APPR_SEQ_NO` in legacy ([IDO.SCDUL_PYMN_SUMR_R002.xml:98-100](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.SCDUL_PYMN_SUMR_R002.xml)); the refactor sources Scheduled Date from the entity's own `PAY_TRAN_DT` (sibling convention) and Payroll Type from `EXPT_RESOL_MSTR.APPR_FUND_TP` (mapped in `PayrollPaymentService.toListRow`, one fetch shared with the Approval Ref `DOC_NO` resolution).
- **Sort whitelist:** `PayrollPaymentSortField` realigned to the V232 FIELD_IDs (mirrors `InternalTransferSortField`). `APPRVR_NM` + `APPR_FUND_TP` carry `SORT_YN='Y'` for legacy parity but are **not** in the whitelist / frontend `SORTABLE_COLUMNS` — both project from joins the single-table paged query can't sort on, so no glyph renders (same deferral as the sibling Approver column).
- **Shared component:** `<PaymentScheduledBatchListPage>` gained one additive optional prop `extraColumnAfterStatus` (rendered between Approval Status and Requestor) for the Payroll Type column. Siblings unaffected.
- **Wire:** `apprFundTp` added to the shared `ScheduledTransferRow` record (backward-compatible constructor → siblings pass `null`) and `appr_fund_tp` to the frontend `ApiScheduledTransferRow`.

Touched files: [V232 migration](../../../../src/main/resources/db/migration/V232__payroll_payment_scheduled_view_setting_legacy.sql), `PayrollPaymentSortField.java`, `PayrollPaymentService.java`, `ScheduledTransferRow.java`; `lib/types/api.ts`, `components/payment/shared/PaymentScheduledBatchListPage.tsx`, `components/payment/payroll/ScheduledPaymentPage.tsx` + `types.ts`, `locales/{en,ko,vi}.json`.

---

## 3. Open documentation gaps (backfill in a follow-up MR)

Same gap list as [vendor-payment.md §3](../vendor-payment/README.md) — full §1–§12 spec + 5 companion docs are not yet written.

Payroll-specific pieces to backfill with priority:

- [ ] The **5-column result-detail layout** — Figma spec citation + the actual `columns` array passed to `<ResultDetailView>` in `<PayrollPaymentResultDetailPage>`.
- [ ] The **payroll-period selector** UX — Figma + the `extraStep` content in `<PayrollPaymentUploadDialog>`.
- [ ] Payroll-specific employee/salary validation rules (likely strictest of the four — employee id must exist in HR master, gross ≥ net, etc.).
- [ ] Why `SCDUL_PYMN_TYPE_CD = '00'` instead of `'04'` or a higher number — historical reason from the JEX legacy enum order. Mention it in the per-feature spec so reviewers don't misread it as accidental.

---

## 4. Verification ground truth

When backfilling:

1. [`PayrollPaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/controller/PayrollPaymentController.java) — REST surface.
2. [`PayrollPaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentService.java) — business logic + audit-key channel.
3. `<PayrollScheduledPaymentPage>` + `<PayrollPaymentResultDetailPage>` + `<PayrollPaymentUploadDialog>` — UX (the Result detail's 5-column variant especially).
4. JEX `pymn_0005_*` + `pymn_0006_*` actions + WSVC XML.
5. Figma (Payroll-specific frames) — for the 5-column result-detail table + the payroll-period selector. **Note:** Figma MCP is currently disconnected; cite Publishing (AI) handoffs under `c:/WeCMS/Publishing (AI)/` when they exist, or flag the UX claim as unverified pending Figma access (see Open Questions in [internal-transfer.md §12](../internal-transfer/README.md)).

---

Last verified: 2026-05-22

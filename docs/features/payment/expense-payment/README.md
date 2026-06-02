# Expense Payment — Refactor Feature Spec (STUB)

## Screens (this feature)

- [scheduled-payment.md](scheduled-payment.md)
- [scheduled-payment-details.md](scheduled-payment-details.md)
- [payment-result.md](payment-result.md)
- [payment-result-details.md](payment-result-details.md)


| Field | Value |
|---|---|
| System | Refactor (Spring Boot + Next.js) |
| Slug | `expense-payment` |
| Module | Payment → Expense Payment |
| JEX counterparts | `pymn_0003_*` (Expense Payment Scheduled) + `pymn_0004_*` (Expense Payment Result) — labels confirmed at [comm_0190_003_view.jsp:569](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L569) (Scheduled) and [comm_0190_003_view.jsp:578-579](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp) (Result). |
| Discriminator | `SCDUL_PYMN_TYPE_CD = "02"` — verified at [pymn_0003_001_r001_act.jsp:68](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0003_001_r001_act.jsp#L68) + refactor enum `ScdulPymnTypeCd.EXPENSE.code()`. |
| Audit `WSVC_ID` | `expense-payment` — declared as a `static final String` at [ExpensePaymentService.java:109](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java#L109). |
| REST URI base | `/api/v1/payment/expense-payment` |
| Frontend route base | `/payment/expense-payment` |
| Status | **Implementation status: documented retroactively against existing code as of 2026-05-22.** Backend + frontend pages exist; this stub preserves the implementation in the docs surface so it isn't lost. A full-spec follow-up MR is needed to bring this doc to parity with [internal-transfer.md](../internal-transfer/README.md). |
| Baseline | [internal-transfer.md](../internal-transfer/README.md) — Expense mirrors this shape; see [_payment-module-overview.md](../_payment-module-overview.md). |

---

## 1. Files in use

### 1.1 Backend ([`com.kosign.wecms.domain.payment.expensepayment`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment))

- **Controller:** [`ExpensePaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java) — same endpoint surface as Vendor (single controller covers Scheduled + Result + dispatch + approval + parse + create + delete + file-download + withdrawal-accounts). The `SCDUL_PYMN_TYPE_CD = '02'` discriminator is pinned at L64.
- **Services:**
  - [`ExpensePaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java) — `WSVC_ID = "expense-payment"` at L109; `ScdulPymnTypeCd.EXPENSE.code() = "02"` discriminator applied at every repository call.
  - `ExpensePaymentResultService.java` — Result-side read service.
  - `ExpensePaymentExcelParser.java` — Apache POI walk + per-cell validation (uses `PymnType.EXPENSE_PAYMENT("EXPN")` for `BLOCKED_CHARS_RULE` lookup).
  - `ExpensePaymentSortField.java` + `ExpensePaymentResultSortField.java` — sort whitelists.
- **Export:** [`ExpensePaymentResultExportProvider.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/export/ExpensePaymentResultExportProvider.java) — implements `ExportRowProvider`.
- **Shared infrastructure consumed** (do not fork — see [_payment-module-overview.md §2](../_payment-module-overview.md)):
  - Master/detail entities + dispatch entities + `SpecialCharValidator` + `ApprovalBoxService` + `AuditLogService` — same set as Internal Transfer / Vendor.

### 1.2 Frontend ([`components/payment/expense-payment/`](../../../../../wecms-web/components/payment/expense-payment))

| File | Role |
|---|---|
| [`ScheduledPaymentPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/ScheduledPaymentPage.tsx) | Scheduled list (composes `<PaymentScheduledBatchListPage>`). Exports as `ExpenseScheduledPaymentPage`. |
| [`ScheduledPaymentDetailPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/ScheduledPaymentDetailPage.tsx) | Scheduled detail (composes `<PaymentDetailPage>`). |
| [`PaymentResultPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/PaymentResultPage.tsx) | Result list (composes `<PaymentResultBatchListPage>` — totals footer renders, unlike Internal Transfer). |
| [`PaymentResultDetailPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/PaymentResultDetailPage.tsx) | Result detail (composes `<ResultDetailView>` with the canonical 11-column `resultDetailColumns`). |
| [`ExpensePaymentUploadDialog.tsx`](../../../../../wecms-web/components/payment/expense-payment/ExpensePaymentUploadDialog.tsx) | Thin wrapper over `<PaymentExcelUploadDialog>`. Supplies the Expense-specific column set (expense-account + description + reporting-account-centric) + parse/upload handlers. |
| `types.ts` | Local component types. |

### 1.3 Routes ([`app/(dashboard)/payment/expense-payment/`](../../../../../wecms-web/app/(dashboard)/payment/expense-payment))

| Route | page.tsx export | Imported component |
|---|---|---|
| `scheduled-payment/page.tsx` | `PaymentExpenseScheduledPage` | `ExpenseScheduledPaymentPage` |
| `scheduled-payment/[id]/page.tsx` | `PaymentExpenseScheduledDetailRoute` | `ExpenseScheduledPaymentDetailPage` |
| `payment-result/page.tsx` | `PaymentExpenseResultPage` | `ExpensePaymentResultPage` |
| `payment-result/[id]/page.tsx` | `PaymentExpenseResultDetailRoute` | `ExpensePaymentResultDetailPage` |

### 1.4 API client + SCREEN_IDS + locale entries

- API client: [`lib/api/expensePayment.ts`](../../../../../wecms-web/lib/api/expensePayment.ts) (currently exposes `requestExpenseApproval`; broader surface lands in follow-up MR).
- SCREEN_IDS — 4 constants in [`lib/viewSettingScreens.ts`](../../../../../wecms-web/lib/viewSettingScreens.ts):
  - `PAYMENT_EXPENSE_PAYMENT_SCHEDULED` = `"payment-expense-payment-scheduled"`
  - `PAYMENT_EXPENSE_PAYMENT_SCHEDULED_DETAIL` = `"payment-expense-payment-scheduled-detail"`
  - `PAYMENT_EXPENSE_PAYMENT_RESULT` = `"payment-expense-payment-result"`
  - `PAYMENT_EXPENSE_PAYMENT_RESULT_DETAIL` = `"payment-expense-payment-result-detail"`
- View Setting seed: [V143__expense_payment_view_setting_seed.sql](../../../../src/main/resources/db/migration/V143__expense_payment_view_setting_seed.sql).

---

## 2. Divergence from the Internal Transfer baseline

| Concern | Internal Transfer | Expense Payment |
|---|---|---|
| Controller split | Two controllers (Scheduled + Result) | **Single** `ExpensePaymentController` covers both |
| Subroute names | `internal-transfer/scheduled-transfer` + `internal-transfer/transfer-result` | `expense-payment/scheduled-payment` + `expense-payment/payment-result` |
| Result list totals footer | **Omitted** | **Rendered** |
| Upload-dialog column set | recipient acct, name, bank, network, amount, description, doc no, note | Expense-specific — column set centred on expense account + description + reporting account (exact list: follow-up MR documents from `<ExpensePaymentUploadDialog>`'s `COLUMNS` array) |
| Discriminator value | `"03"` | `"02"` |
| Audit `WSVC_ID` | `internal-transfer` | `expense-payment` |
| Key-sequence helper table | `INTERNAL_TRANSFER_KEY_SEQ` | Expense maintains its own per-(tenant, day) sequence — table name TBD-by-code-inspection |
| Approval-line semantics | Standard | Expense may carry per-line reporting-account binding semantics that Vendor doesn't (needs PO confirmation) |

Per [_payment-module-overview.md §2 + §3](../_payment-module-overview.md), Expense consumes the shared infrastructure verbatim.

---

## 3. Open documentation gaps (backfill in a follow-up MR)

Same gap list as [vendor-payment.md §3](../vendor-payment/README.md) — Sections 1–12 of the per-feature spec are not yet written; companion docs (`migration/expense-payment-mapping.md`, `backend/expense-payment.md`, `frontend/expense-payment.md`, `database/payment/expense-payment-schema.md`, `audits/expense-payment-parity-checklist.md`) are not yet created.

Most important pieces to backfill first:

- [ ] Section 3 — Screen inventory with JEX → refactor file:line citations for every `_r/_c/_u/_d` action under `pymn_0003_*` + `pymn_0004_*`.
- [ ] Section 6 — Validation rules — verify Expense's per-line **reporting-account requirement** at save time (Vendor allows late-bind via PATCH; Expense may require it on Save).
- [ ] Section 7 — State machine — confirm Expense uses the same `APPR_STTS` / `TRNF_YN` / `PROC_STAT` codes as Internal Transfer.

---

## 4. Verification ground truth

When backfilling:

1. [`ExpensePaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java) — REST surface.
2. [`ExpensePaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java) — business logic + audit-key channel.
3. `<ExpenseScheduledPaymentPage>` + `<ExpensePaymentResultPage>` + `<ExpensePaymentUploadDialog>` — UX.
4. JEX `pymn_0003_*` + `pymn_0004_*` actions + WSVC XML.

---

Last verified: 2026-05-22

# Expense Payment — Refactor Feature Spec

> **Backend feature hub.** Frontend counterpart: [wecms-web/docs/features/payment/expense-payment/README.md](../../../../../wecms-web/docs/features/payment/expense-payment/README.md).

## Sub-module map

| Sub-module | Screens |
|---|---|
| **Scheduled Payment** | Scheduled Payment + Scheduled Payment Details |
| **Payment Result** | Payment Result + Payment Result Details |

## Documentation set (this feature)

| Doc | Purpose |
|---|---|
| [business-flow.md](./business-flow.md) | Legacy/Spring/React end-to-end (upload → approval → dispatch → result) |
| [database-mapping.md](./database-mapping.md) | Tables, entities, PK/FK, key generation |
| [functional-specification.md](./functional-specification.md) | Filters, status tabs/counts, columns, actions, rules, validation |
| [interface-specification.md](./interface-specification.md) | All 20 endpoints (URL, authz, DTOs, errors) |
| [migration-guide.md](./migration-guide.md) | Legacy WSVC → controller/service map, approved deviations, dead legacy |
| [refactor-gap-analysis.md](./refactor-gap-analysis.md) | MATCH / APPROVED DEVIATION / PARTIAL / DEAD LEGACY / MISSING |
| [source-code-index.md](./source-code-index.md) | Backend + frontend file inventory + tests |
| [test-scenarios.md](./test-scenarios.md) | BE + FE test inventory + missing coverage |
| [dispatch-sit-uat-deployment-checklist.md](../../../deployment/expense-payment-dispatch-sit-uat-checklist.md) | Firm-banking transmission flags + SIT/UAT provisioning (G3) |

## Screens (this feature)

The four screens are sections of this hub (no separate per-screen files). File-level detail is in §1.2
(frontend) + §1.3 (routes) below; one-line summaries:

| Screen | JEX WSVC | Scope | Status |
|---|---|---|---|
| Scheduled Payment | `pymn_0003_001` | List + create scheduled expense payment batches (Excel parse/upload, request approval, approve/reject, withdraw/change approval line, transfer/dispatch, per-line reporting-account + bank match, delete, file download). | **Implemented full-stack** (backend + Next.js) |
| Scheduled Payment Details | `pymn_0003_002` | Drill-down detail of one scheduled expense payment batch — master fields, line items (chart-account breadcrumb), requester, approval attachments (EXPT_RESOL_FILE ∪ EXPT_RESOL_PAY_EVID_FILE), approver chain. | **Implemented full-stack** |
| Payment Result | `pymn_0004_001` | Executed expense-payment results with route-aware PROC_STAT status filter (Completed `C` / In Progress `P`); renders the totals footer (unlike Internal Transfer); F2 download 'W' ERP audit, F4b clickable Approval Ref, Excel export. | **Implemented full-stack** |
| Payment Result Details | `pymn_0004_002` | Per-transaction result detail (canonical 11-column `resultDetailColumns`) + header tab counts (All/Success/Failed/In Progress) derived from per-line statuses. | **Implemented full-stack** |

> **Status note (2026-06-23 audit):** the original "STUB / Phase A read-only / doc stub" framing in this
> file was **stale**. The backend controller, both services, the export provider, the Expense-specific
> dispatch executor, and the full Next.js page/component/API-client set are all implemented and
> test-covered. This README has been reconciled against the latest code; remaining gaps are classified
> in §3.


| Field | Value |
|---|---|
| System | Refactor (Spring Boot + Next.js) |
| Slug | `expense-payment` |
| Module | Payment → Expense Payment |
| Discriminator | `SCDUL_PYMN_TYPE_CD = "02"` — verified at [pymn_0003_001_r001_act.jsp:68](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0003_001_r001_act.jsp#L68) + refactor enum `ScdulPymnTypeCd.EXPENSE.code()`. |
| Audit `WSVC_ID` | `expense-payment` — declared as a `static final String` at [ExpensePaymentService.java:109](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java#L109). |
| REST URI base | `/api/v1/payment/expense-payment` |
| Frontend route base | `/payment/expense-payment` |
| Status | **Implemented full-stack** (backend + Next.js), reconciled against the latest code 2026-06-23. Endpoint surface, services, and frontend wiring are documented in §1 below. |
| Baseline | [internal-transfer/README.md](../internal-transfer/README.md) — Expense mirrors this shape; see [payment hub README](../README.md). |

---

## 1. Files in use

### 1.1 Backend ([`com.kosign.wecms.domain.payment.expensepayment`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment))

- **Controller:** [`ExpensePaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java) — a single controller covers the **full** surface (Scheduled list/detail + Result list/detail/export + create/parse/withdrawal-accounts + delete + request-approval + approval-action + withdraw-approval + change-approval-line + reporting-account PATCH + dispatch + file-download + per-line banking match). Class-level `@PreAuthorize("@menuAuthz.canAccess('payment-expense-scheduled')")` at [ExpensePaymentController.java:89](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java#L89); Result endpoints add `@PreAuthorize("@menuAuthz.canAccess('payment-expense-result')")`. The `SCDUL_PYMN_TYPE_CD = '02'` discriminator is supplied via `ScdulPymnTypeCd.EXPENSE.code()`.
- **Services:**
  - [`ExpensePaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java) — Scheduled list/detail + all write actions (create, delete, request-approval, approval-action, withdraw, change-approval-line, reporting-account, dispatch, file download). `WSVC_ID = "expense-payment"` at [L113](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java#L113); `ScdulPymnTypeCd.EXPENSE.code() = "02"` discriminator applied at every repository call.
  - [`ExpensePaymentResultService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentResultService.java) — Result list (route-aware PROC_STAT filter + footer currency totals) + detail (per-line dispatch-result resolve + header tab counts via `countByStatus`).
  - [`ExpensePaymentExcelParser.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentExcelParser.java) — Apache POI walk + per-cell validation.
  - [`ExpenseDispatchExecutor.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpenseDispatchExecutor.java) — **Expense-specific** dispatch executor (a deliberate isolated copy of the shared `TransferDispatchExecutor` carrying the G2 legacy-parity fixes — `BankChnlDiv='T'`, per-out-bank TRSC_CCY_CD/ANT_DIV_CD, real `BANK_PRTN_ID` from `TB_FIRM_CONF` → `WBE2089` when absent, VCB USD→other-bank reject `WBE2088`). VND→`TB_PAY_TARGET`; non-VND→`TB_PAY_FILE_MST`+`TB_PAY_FILE_DATA`, `PROC_STAT='9'`.
  - `ExpensePaymentSortField.java` + `ExpensePaymentResultSortField.java` — sort whitelists.
  - Supporting: `ExpensePaymentConfig`, `ExpensePaymentFileResolver`, `ExpensePaymentStorageProperties`.
- **Export:** [`ExpensePaymentResultExportProvider.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/export/ExpensePaymentResultExportProvider.java) — extends `PaymentResultExportProvider`; `screenId() = "payment-expense-payment-result"`, ERP result `TRNS_TYPE_CD = "05"` (`ErpTypeCd.EXPENSE_PAYMENT_RESULT`).
- **Shared infrastructure consumed** (do not fork — see [payment hub README §2](../README.md)):
  - Master/detail entities (`ScdulPymnSumr` / `ScdulPymnSumrDetl`, shared with Internal Transfer/Vendor) + approval entities (`ExptResolMstr` / `ExptResolApprvrInfm`) + dispatch entities/repos (`TbPayTarget` / `TbPayFileMst` / `TbPayFileData` / `TbFirmConf`) + `ApprovalBoxService` / `ApprovalBoxFanoutService` / `ApprovalDisplayFlagsResolver` / `ApprovalAttachmentWriter` / `ApprovalAttachmentLoader` + `DomesticPaymentLineMatchService` + `LegacyFunctionRepository` (key gen via `wb_get_next_val`) + `DocNoGenerator` + `CutoffValidator` + `ErpTrnsHistoryWriter` + `AuditLogService`.

#### REST endpoint surface (verified against `ExpensePaymentController.java`)

Base URI `/api/v1/payment/expense-payment`. **MATCH** with the frontend client [`lib/api/expensePayment.ts`](../../../../../wecms-web/lib/api/expensePayment.ts).

| Method | Path | Service call | Legacy WSVC |
|---|---|---|---|
| GET | `/` | `service.list` (Scheduled) | `pymn_0003_001_r001` |
| GET | `/{id}` | `service.detail` | `pymn_0003_002_r001` |
| GET | `/results` | `resultService.list` | `pymn_0004_001_r001` |
| GET | `/results/{id}` | `resultService.detail` | `pymn_0004_002_r001` |
| POST | `/results/export` | `excelExportService.exportTo` | — (refactor XLSX) |
| DELETE | `/{id}` | `service.delete` | `pymn_0003_001_d001` |
| POST | `/{id}/request-approval` (multipart) | `service.requestApproval` | `comm_0192_001_c005` |
| POST | `/{id}/approval-action` | `service.actOnApproval` | `bltb_0001_003_u001` |
| DELETE | `/{id}/approval` | `service.withdrawApproval` | — |
| PUT | `/{id}/approval-line` | `service.changeApprovalLine` | — |
| PATCH | `/{id}/lines/{lineId}/reporting-account` | `service.updateReportingAccount` | — |
| POST | `/{id}/dispatch` | `service.dispatch` | `pymn_0003_001_c001` |
| GET | `/withdrawal-accounts` | `service.listWithdrawalAccounts` | `comm_1001_001` |
| POST | `/uploads/parse` (multipart) | `service.parseExcel` | `pymn_0003_002_r001` |
| POST | `/` (multipart) | `service.createDraft` | `pymn_0003_002_c001` |
| GET | `/{id}/file` | `service.resolveDownload` | `pymn_0001_003` download |
| GET | `/{id}/lines/{lineId}/match/candidates` | `lineMatchService.listCandidates` | `comm_0197_001_r001` |
| POST | `/{id}/lines/{lineId}/match` | `lineMatchService.match` | `comm_0197_001_c001` |
| DELETE | `/{id}/lines/{lineId}/match` | `lineMatchService.unmatch` | `comm_0197_002_u001` |
| DELETE | `/{id}/matches` | `lineMatchService.unmatchAll` | `comm_0197_001_u001` |

### 1.2 Frontend ([`components/payment/expense-payment/`](../../../../../wecms-web/components/payment/expense-payment))

| File | Role |
|---|---|
| [`ScheduledPaymentPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/ScheduledPaymentPage.tsx) | Scheduled list (composes `<PaymentScheduledBatchListPage>`). Exports as `ExpenseScheduledPaymentPage`. |
| [`ScheduledPaymentDetailPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/ScheduledPaymentDetailPage.tsx) | Scheduled detail (composes `<PaymentDetailPage>`). |
| [`PaymentResultPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/PaymentResultPage.tsx) | Result list (composes `<PaymentResultBatchListPage>` — totals footer renders, unlike Internal Transfer). |
| [`PaymentResultDetailPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/PaymentResultDetailPage.tsx) | Result detail (composes `<ResultDetailView>` with the canonical 11-column `resultDetailColumns`). |
| [`ExpensePaymentUploadDialog.tsx`](../../../../../wecms-web/components/payment/expense-payment/ExpensePaymentUploadDialog.tsx) | Thin wrapper over `<PaymentExcelUploadDialog>`. Supplies the Expense-specific column set (expense-account + description + reporting-account-centric) + parse/upload handlers. |
| `types.ts` | Local component types. |

### 1.3 Routes ([`app/(dashboard)/payment/expense-payment/`](<../../../../../wecms-web/app/(dashboard)/payment/expense-payment>))

| Route | page.tsx export | Imported component |
|---|---|---|
| `scheduled-payment/page.tsx` | `PaymentExpenseScheduledPage` | `ExpenseScheduledPaymentPage` |
| `scheduled-payment/[id]/page.tsx` | `PaymentExpenseScheduledDetailRoute` | `ExpenseScheduledPaymentDetailPage` |
| `payment-result/page.tsx` | `PaymentExpenseResultPage` | `ExpensePaymentResultPage` |
| `payment-result/[id]/page.tsx` | `PaymentExpenseResultDetailRoute` | `ExpensePaymentResultDetailPage` |

### 1.4 API client + SCREEN_IDS + locale entries

- API client: [`lib/api/expensePayment.ts`](../../../../../wecms-web/lib/api/expensePayment.ts) — **full surface implemented** (not a stub): `fetchExpenseScheduledList`, `fetchExpenseWithdrawalAccounts`, `parseExpensePaymentExcel`, `createExpensePayment`, `fetchExpensePaymentDetail`, `fetchExpensePaymentResultDetail`, `fetchExpensePaymentResultList`, `deleteExpensePayment`, `updateExpenseReportingAccount`, `get/match/unmatch/unmatchAll` ExpensePaymentLine, `dispatchExpensePayment`, `requestExpenseApproval`, `actOnExpenseApproval`, `withdrawExpenseApproval`, `changeExpenseApprovalLine`, `downloadOriginalExpenseExcel`. Reuses the shared `lib/types/api.ts` wire DTOs (physical `SCDUL_PYMN_SUMR` table is shared with Internal Transfer/Vendor). **Stale comment note:** the file's own header Javadoc still references "Phase A / Vendor" copy-paste artifacts (`'01' (Vendor) vs '03' (Internal)`) — code is correct, the comment is inaccurate; not a behavior issue.
- SCREEN_IDS — 4 constants in [`lib/viewSettingScreens.ts`](../../../../../wecms-web/lib/viewSettingScreens.ts):
  - `PAYMENT_EXPENSE_PAYMENT_SCHEDULED` = `"payment-expense-payment-scheduled"`
  - `PAYMENT_EXPENSE_PAYMENT_SCHEDULED_DETAIL` = `"payment-expense-payment-scheduled-detail"`
  - `PAYMENT_EXPENSE_PAYMENT_RESULT` = `"payment-expense-payment-result"`
  - `PAYMENT_EXPENSE_PAYMENT_RESULT_DETAIL` = `"payment-expense-payment-result-detail"`
- View Setting seed: [V156__sys_scr_option_wecms.sql](../../../../src/main/resources/db/migration/V156__sys_scr_option_wecms.sql).

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
| Key-sequence | `scdul_pymn_no` via `InternalTransferKeyGenerator` → `wb_get_next_val` funnel (per-tenant `MAX+1`, V140 #36). ⚠️ *The former `*_KEY_SEQ` date-prefixed counter was **retired 2026-06-09**.* | Expense **reuses** `InternalTransferKeyGenerator.nextScdulPymnNo` verbatim (shared physical table). `APPR_SEQ_NO` via the global `wb_get_next_val('sq_appr_seq_no')` funnel; detail numbering via `wb_get_next_val('scdul_pymn_sumr_detl','scdul_pymn_detl_no')`. |
| Dispatch executor | shared `TransferDispatchExecutor` (Vendor/Payroll) / dedicated `InternalTransferDispatchExecutor` | **Dedicated `ExpenseDispatchExecutor`** (isolated copy carrying the G2 legacy-parity fixes; shared executor left untouched for Vendor/Payroll) |

Per [payment hub README §2 + §3](../README.md), Expense consumes the shared infrastructure verbatim.

---

## 3. Gaps (classified — 2026-06-23 audit)

The code is implemented full-stack; the gaps below are doc-completeness and known parity items, not missing implementation.

- **PARTIAL (doc completeness):** companion docs (`migration/expense-payment-mapping.md`, `database/payment/expense-payment-schema.md`, `audits/expense-payment-parity-checklist.md`) are not yet created. The endpoint table in §1.1 + the per-screen frontend docs under `wecms-web/docs/features/payment/expense-payment/` cover the surface for now.
- **DEAD LEGACY (code comment, not behavior):** `lib/api/expensePayment.ts` header Javadoc + several method docstrings carry copy-pasted "Vendor / Phase A / `'01'` vs `'03'`" artifacts; `ExpensePaymentResultService` class Javadoc says `SCDUL_PYMN_TYPE_CD = '01'`. Both are stale comments — the code passes `ScdulPymnTypeCd.EXPENSE.code() = "02"` everywhere. (Comments only — DO NOT edit code in a docs pass; flag for a code-comment cleanup MR.)
- **MATCH (verified):** `APPR_STTS` is **not** stored on `SCDUL_PYMN_SUMR` (no such column in legacy JEX) — derived via `wb_get_appr_stts(...)` over `EXPT_RESOL_MSTR.APPR_TYPE_CD`; rejected = `R` not `X`. Same model as Internal Transfer / the other payment modules.
- **MATCH (verified):** dispatch G1 gate requires `EXPT_RESOL_MSTR.APPR_TYPE_CD = '01'` (fully approved) before bank-dispatch tables are written — `WBE2063` otherwise (legacy parity, `PymnHelper.pymn_0001_001_c001:2146`).
- **APPROVED DEVIATION:** reporting-account is **late-bind via PATCH** (`/{id}/lines/{lineId}/reporting-account`), same as Vendor — the earlier "Expense may require it on Save" open question is resolved as no special requirement; the create path does not mandate it.
- **PARTIAL (dispatch, flag-gated by design):** the `ExpenseDispatchExecutor` writes pending DB rows unconditionally but all real bank hand-offs (EFS/VAN, SFTP, 00301 receive, VND 202 realtime) are `@ConditionalOnProperty`-gated **off by default** in every profile. See [dispatch-sit-uat-deployment-checklist.md](../../../deployment/expense-payment-dispatch-sit-uat-checklist.md) — verified accurate against the dispatch code this pass.

---

## 4. Verification ground truth

Source-of-truth files:

1. [`ExpensePaymentController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java) — REST surface (full table in §1.1).
2. [`ExpensePaymentService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java) + [`ExpensePaymentResultService.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentResultService.java) — business logic + audit-key channel.
3. [`ExpenseDispatchExecutor.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpenseDispatchExecutor.java) — dispatch (TB_PAY_* writes).
4. `<ExpenseScheduledPaymentPage>` (exported from [`ScheduledPaymentPage.tsx`](../../../../../wecms-web/components/payment/expense-payment/ScheduledPaymentPage.tsx)) + `<ExpensePaymentResultPage>` + `<ExpensePaymentUploadDialog>` — UX.
5. JEX `pymn_0003_*` + `pymn_0004_*` actions + WSVC XML.

### Tests (`wecms-api/src/test/.../expensepayment/`)

- `ExpenseDispatchExecutorTest`, `ExpensePaymentDispatchApprovalGateTest`, `ExpensePaymentResultDownloadAuditTest`, `ExpensePaymentResultFooterTotalsTest`, `ExpensePaymentResultParityTest`.
- **MISSING coverage:** no test exercises Scheduled create/parse, request-approval, approval-action, withdraw/change-approval-line, reporting-account PATCH, or the per-line banking-match endpoints. No frontend tests for the Expense pages.

---

## Legacy & Migration Traceability

- **Sub-module WSVC mapping:** Scheduled Payment = `pymn_0003_*` ; Payment Result = `pymn_0004_*`.
- **JEX counterparts:** `pymn_0003_*` (Expense Payment Scheduled) + `pymn_0004_*` (Expense Payment Result) — labels confirmed at [comm_0190_003_view.jsp:569](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L569) (Scheduled) and [comm_0190_003_view.jsp:578-579](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp) (Result).

---

Last verified: 2026-06-23 (reconciled against latest code)

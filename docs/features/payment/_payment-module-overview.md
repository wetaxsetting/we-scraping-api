# Payment Module — Cross-Sibling Overview

| Field | Value |
|---|---|
| Module | `payment` |
| Siblings | `internal-transfer` (locked baseline), `vendor-payment`, `expense-payment`, `payroll` |
| Related but separate | `overseas-payment` (different table set — `OVRS_PYMN_*`), `corporate-card`, `approval-box` (cross-feature) |
| Shared DB tables | `SCDUL_PYMN_SUMR`, `SCDUL_PYMN_SUMR_DETL` (discriminated by `SCDUL_PYMN_TYPE_CD`), plus dispatch tables `TB_PAY_TARGET`, `TB_PAY_FILE_MST`, `TB_PAY_FILE_DATA`, conn table `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN`, approval tables `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM`, and `BLOCKED_CHARS_RULE` |
| Companion docs | [internal-transfer.md](internal-transfer/README.md) (baseline) · [vendor-payment.md](vendor-payment/README.md) · [expense-payment.md](expense-payment/README.md) · [payroll.md](payroll/README.md) |
| Status | Internal Transfer is **the locked baseline** (Phase A backend + Phase A frontend partial — see its feature doc). Vendor / Expense / Payroll are implemented in code (controllers, services, exports, frontend pages) but **documented only as stubs** until a follow-up retroactive-documentation pass fills them in. |

> **Rule.** When porting a new payment sub-module, mirror Internal Transfer's shape — backend package layout, frontend folder layout, route shape, audit-key channel — and reuse the shared infrastructure indexed in §2 + §3. Diverge only where the table-discriminator, column set, or Figma layout legitimately differs. See §5 ("How to add a new payment sub-module") for the 10-step procedure.

This doc is the **cross-sibling index**. The per-feature specs at [internal-transfer.md](internal-transfer/README.md) / [vendor-payment.md](vendor-payment/README.md) / [expense-payment.md](expense-payment/README.md) / [payroll.md](payroll/README.md) own the per-sibling rules; this doc owns the cross-cutting invariants + the sibling table.

---

## 1. Sibling matrix

The four Payment sub-modules + the related Overseas / Corporate Card surfaces:

| Slug | Status | `SCDUL_PYMN_TYPE_CD` | Audit `WSVC_ID` | Backend package | Frontend folder | Routes (under `/payment/`) | Has doc? |
|---|---|---|---|---|---|---|---|
| [internal-transfer](internal-transfer/README.md) | ✅ Locked baseline (Phase A) | `"03"` | `internal-transfer` | [`com.kosign.wecms.domain.payment.internaltransfer`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/) | [`components/payment/internal-transfer`](../../../../wecms-web/components/payment/internal-transfer/) | `internal-transfer/{scheduled-transfer,transfer-result}` | **YES** — full set (feature + migration + backend + frontend + database + audit checklist) |
| [vendor-payment](vendor-payment/README.md) | 🟡 Implemented, doc stub | `"01"` | `vendor-payment` | [`com.kosign.wecms.domain.payment.vendorpayment`](../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/) | [`components/payment/vendor-payment`](../../../../wecms-web/components/payment/vendor-payment/) | `vendor-payment/{scheduled-payment,payment-result}` | Stub only — see [vendor-payment.md](vendor-payment/README.md) |
| [expense-payment](expense-payment/README.md) | 🟡 Implemented, doc stub | `"02"` | `expense-payment` | [`com.kosign.wecms.domain.payment.expensepayment`](../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/) | [`components/payment/expense-payment`](../../../../wecms-web/components/payment/expense-payment/) | `expense-payment/{scheduled-payment,payment-result}` | Stub only — see [expense-payment.md](expense-payment/README.md) |
| [payroll](payroll/README.md) | 🟡 Implemented, doc stub | `"00"` | `payroll-payment` | [`com.kosign.wecms.domain.payment.payrollpayment`](../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/) | [`components/payment/payroll`](../../../../wecms-web/components/payment/payroll/) | `payroll/{scheduled-payment,payment-result}` | Stub only — see [payroll.md](payroll/README.md) |
| overseas-payment *(separate domain)* | 🟡 Implemented, separate doc | n/a — separate table family | `overseas-payment` | [`com.kosign.wecms.domain.payment.overseas`](../../../src/main/java/com/kosign/wecms/domain/payment/overseas/) + top-level `controller/`, `dto/`, `entity/`, `service/`, `repository/` | [`components/payment/overseas-payment`](../../../../wecms-web/components/payment/overseas-payment/) | `overseas-payment/{scheduled-payment,payment-result,exchange-rate-inquiry}` | Out of scope for this overview — uses `OVRS_PYMN_MSTR`/`OVRS_PYMN_SUMR`/`OVRS_PYMN_DTLS`. [pymn_0007_001_r001_act.jsp:198](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_001_r001_act.jsp#L198) routes to `getDynamicOvrs()` instead of the shared SCDUL flow. |
| approval-box *(cross-feature)* | 🟡 Implemented | — | `approval-box` | [`com.kosign.wecms.domain.payment.approvalbox`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/) | [`components/payment/approval-box`](../../../../wecms-web/components/payment/approval-box/) | `approval-box` | Not a sibling — consumed by every Payment write flow. |
| corporate-card *(separate surface)* | 🟡 Implemented | — | `card-transactions` | [`com.kosign.wecms.domain.payment.cardtransactions`](../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/) | [`components/payment/corporate-card`](../../../../wecms-web/components/payment/corporate-card/) | `corporate-card/card-transactions` | Not a payment-batch sibling — different domain (card transactions). |

Discriminator citations (DEV JEX):

- `pymn_0001` → Vendor Payment (label confirmed at [comm_0190_003_view.jsp:550](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L550))
- `pymn_0003` → Expense Payment (label at [comm_0190_003_view.jsp:569](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L569))
- `pymn_0005` → Payroll (label at [comm_0190_003_view.jsp:588](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L588))
- `pymn_0007` → Overseas Payment (label at [comm_0190_003_view.jsp:608](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L608))
- `pymn_0009` → Internal Transfer (label at [comm_0190_003_view.jsp:532](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp#L532))
- `pymn_0010` → Internal Transfer Result (label at [comm_0190_003_view.jsp:540-541](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp))

The refactor enum is [`ScdulPymnTypeCd`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/ScdulPymnTypeCd.java) (it lives under `internaltransfer/service/` for historical reasons — the baseline was the first sibling to ship). All four sibling services import it.

---

## 2. Shared backend infrastructure index

The shared infrastructure under [`com.kosign.wecms.domain.payment.*`](../../../src/main/java/com/kosign/wecms/domain/payment/) — never fork these when adding a sibling.

### 2.1 `payment/dispatch/` — bank dispatch primitives

Mirrors `PymnHelper.pymn_0009_001_c001` routing — TB_PAY_TARGET (API path) vs TB_PAY_FILE_MST + TB_PAY_FILE_DATA (File path).

| Class | Path | Role |
|---|---|---|
| `TransferDispatchExecutor` | [`payment/dispatch/service/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/) | Shared dispatch executor (Phase B target). |
| `DispatchKeyGenerator` | [`payment/dispatch/service/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/) | `nextTranSeq(bizKey, tranDt)` + `nextFileSeqNo(bizKey, tranDt)`. |
| `TbPayTarget` + `Id` | [`payment/dispatch/entity/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) | `@Table("TB_PAY_TARGET")` PK `(tran_dt, biz_key, tran_seq)`. |
| `TbPayFileMst` + `Id` | [`payment/dispatch/entity/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) | `@Table("TB_PAY_FILE_MST")` PK `(tran_dt, biz_key, file_seq_no)`. |
| `TbPayFileData` + `Id` | [`payment/dispatch/entity/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) | `@Table("TB_PAY_FILE_DATA")` PK `(tran_dt, biz_key, file_seq_no, seq)`. |
| `ScduPymnSumrDetlPayTargetConn` + `Id` | [`payment/dispatch/entity/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) | API-path conn table (note `SCDU_` spelling, preserved from DEV). |
| `TbFirmBatApiTran`, `TbFirmFileMst`, `TbFirmFileData`, `TbFirmFileRslt`, `TbBatchScheduleInfo`, `TbBatchScheduleDtl` | [`payment/dispatch/entity/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) | Longer-running firm-banking dispatch entities. |
| `TbPayTargetRepository`, `TbPayFileMstRepository`, `TbPayFileDataRepository` | [`payment/dispatch/repository/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/repository/) | Spring Data repositories. |
| `DispatchCountRepository` + `DispatchCountRow` | [`payment/dispatch/`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/) | Projection-backed read of "how many lines per `proc_stat`". |

### 2.2 `payment/validation/` — Excel-cell validation primitives

| Class | Path | Role |
|---|---|---|
| `FieldCd` enum | [`payment/validation/FieldCd.java`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/FieldCd.java) | `RMKS` / `RCPT` / `MEMO` / `BENA` / `BEAD` — discriminator for `BLOCKED_CHARS_RULE`. |
| `PymnType` enum | [`payment/validation/PymnType.java`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/PymnType.java) | `INTR` / `VEND` / `EXPN` / `PAYR` / `OVER` — per-payment-type discriminator for blocked-chars lookup. (Distinct from `ScdulPymnTypeCd`.) |
| `SpecialCharValidator` | [`payment/validation/SpecialCharValidator.java`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/SpecialCharValidator.java) | Per-`(PymnType, FieldCd)` blocked-chars enforcement. Consumed during Excel parse + create-draft. |
| `BlockedCharsRule` entity + `BlockedCharsRuleRepository` | [`payment/validation/entity/`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/entity/) + [`payment/validation/repository/`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/repository/) | Schema in V149. |

> **Cut-off-time** validation is currently Internal-Transfer-specific ([`CutoffValidator`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/CutoffValidator.java) under `internaltransfer/service/`). If Vendor / Expense / Payroll need cut-offs, lift to `payment/shared/cutoff/`.

### 2.3 `payment/approvalbox/` — cross-sibling approval flow

| Class | Path | Role |
|---|---|---|
| `ApprovalBoxController` | [`payment/approvalbox/controller/`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/controller/) | List / approve / reject / bulk approve. |
| `ApprovalBoxService` | [`payment/approvalbox/service/ApprovalBoxService.java`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/service/ApprovalBoxService.java) | Owns `WSVC_ID = "approval-box"` audit channel. Every sibling's `POST /{id}/request-approval` + `/{id}/approval-action` delegates here. |
| `ApprovalBoxFanoutService` | [`payment/approvalbox/service/`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/service/) | Notification fanout side-effects. |
| `ApprDv` enum | [`payment/approvalbox/`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/) | Approval-domain discriminator — has `INTERNAL_TRANSFER` / `VENDOR_PAYMENT` / `EXPENSE_PAYMENT` / `PAYROLL_PAYMENT` constants. |
| `ApprTypeCd`, `ApprvrStts`, `ApprovalBoxSortField` | [`payment/approvalbox/`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/) | Supporting enums. |
| `ExptResolMstr` + `Id`, `ExptResolApprvrInfm` + `Id` | [`payment/approvalbox/entity/`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/entity/) | Schema in V109. |
| 9 DTOs | [`payment/approvalbox/dto/`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/dto/) | `ApprovalBoxListResponse`, `ApprovalBoxRow`, `ApprovalBoxProjection`, `ApprovalBoxApprover`, `ApprovalBoxActionResponse`, `ApprovalBoxBulkApproveRequest`, `ApprovalBoxBulkApproveResponse`, `ApprovalBoxTabCounts`. |
| `ApprovalBoxExportProvider` | [`payment/approvalbox/export/`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/export/) | XLSX export. |

### 2.4 `payment/export/` — cross-sibling result export

| Class | Path | Role |
|---|---|---|
| `PaymentResultExportProvider` | [`payment/export/PaymentResultExportProvider.java`](../../../src/main/java/com/kosign/wecms/domain/payment/export/PaymentResultExportProvider.java) | Cross-sibling base/utility class consumed by per-sibling `*ResultExportProvider` implementations. |

---

## 3. Shared frontend shells + dialogs index

Every Payment sub-module composes the same shells under [`components/payment/shared/`](../../../../wecms-web/components/payment/shared/) — never fork. Add behaviour via props.

| Shell / dialog | Path | Owns | Per-sibling overrides |
|---|---|---|---|
| `<PaymentScheduledBatchListPage>` | [`PaymentScheduledBatchListPage.tsx`](../../../../wecms-web/components/payment/shared/PaymentScheduledBatchListPage.tsx) | Scheduled batch list chrome (toolbar, search, period dropdown, approval-status dropdown, table + sortable headers, sticky pagination, View Setting popover). Generic over `Row extends PaymentScheduledBatchRow`. | `title`, `rows`, `totals`, `rowDetailHref`, callbacks, View Setting items, pagination, sort, period, approval-status filter. |
| `<PaymentResultBatchListPage>` | [`PaymentResultBatchListPage.tsx`](../../../../wecms-web/components/payment/shared/PaymentResultBatchListPage.tsx) | Result batch list chrome (read-only variant). | Same shape minus mutating callbacks; adds `statusFilter` + `extraColumns`. **Internal Transfer omits `totals`** (paginationOnly); Vendor / Expense / Payroll pass `totals`. |
| `<PaymentDetailPage>` | [`PaymentDetailPage.tsx`](../../../../wecms-web/components/payment/shared/PaymentDetailPage.tsx) | Detail page chrome (back link, master summary, file card, scrollable line-item table). Generic over `Row extends { id: string }`. | `listTitle`, `subtitle`, `backHref`, `summary`, `file`, `columns`, `rows`, `minWidth`, `aboveTable`, `onExcelExport`. |
| `<ResultDetailView>` | [`ResultDetailView.tsx`](../../../../wecms-web/components/payment/shared/ResultDetailView.tsx) | Result detail chrome (read-only twin of `<PaymentDetailPage>`). | Default 11-column `resultDetailColumns`. **Payroll passes a 5-column variant** per Figma. |
| `<PaymentExcelUploadDialog>` | [`PaymentExcelUploadDialog.tsx`](../../../../wecms-web/components/payment/shared/PaymentExcelUploadDialog.tsx) | Payment-module Excel-upload wrapper (state, error funnel, success flow, `stackLevel=1`). | Per-sibling `columns`, `onParse`, `onUpload`, `strings`, optional `onDownload`, optional `extraStep`. |
| `<RequestApprovalDialog>` | [`RequestApprovalDialog.tsx`](../../../../wecms-web/components/payment/shared/RequestApprovalDialog.tsx) | Approval-request confirmation dialog. | `kind`, `selectedRows`, handlers. |
| `<ApprovalDetailsDialog>` | [`ApprovalDetailsDialog.tsx`](../../../../wecms-web/components/payment/shared/ApprovalDetailsDialog.tsx) | Approval-details (approve/reject) dialog. | `kind`, `status`, `documentNo`, approvers list, attachments. |
| `<WithdrawalAccountDropdown>` | [`WithdrawalAccountDropdown.tsx`](../../../../wecms-web/components/payment/shared/WithdrawalAccountDropdown.tsx) | Withdrawal-account picker. | `accounts`, `value`, `onChange`, `ccyFilter`. |
| `<PaymentPeriodDropdown>` | [`PaymentPeriodDropdown.tsx`](../../../../wecms-web/components/payment/shared/PaymentPeriodDropdown.tsx) | "Last 7 days / This month / Custom" period picker. | (shared verbatim) |
| `<PaymentResultBadge>` | [`PaymentResultBadge.tsx`](../../../../wecms-web/components/payment/shared/PaymentResultBadge.tsx) | Result-status pill resolved from `proc_stat_key`. | (shared verbatim) |
| `<FileCard>`, `<DetailHeader>`, `<DetailSummary>`, `<DetailStatusTabs>` | [`components/payment/shared/`](../../../../wecms-web/components/payment/shared/) | Detail page primitives. | (shared verbatim) |
| `detailColumns.tsx` `resultDetailColumns` | [`detailColumns.tsx`](../../../../wecms-web/components/payment/shared/detailColumns.tsx) | Canonical 11-column result-detail column set. | Payroll overrides with a 5-column set. |
| `deriveResultStatus.ts` | [`deriveResultStatus.ts`](../../../../wecms-web/components/payment/shared/deriveResultStatus.ts) | Pure function — maps `(proc_stat, rs_cd)` → status pill key. | (shared verbatim) |
| `mockHelpers.ts`, `types.ts` | [`components/payment/shared/`](../../../../wecms-web/components/payment/shared/) | Shared mock-data helpers + cross-sibling row types. | (shared verbatim) |

---

## 4. Shared cross-cutting conventions

These apply to every Payment sub-module without exception.

### 4.1 Identifier rule

| Surface | Token used | Forbidden |
|---|---|---|
| REST URI | `/api/v1/payment/<slug>` (e.g. `/api/v1/payment/internal-transfer`) | `/api/v1/pymn_0009_001` |
| Java package | `com.kosign.wecms.domain.payment.<sibling>` (e.g. `payment.internaltransfer`) | `com.kosign.wecms.domain.pymn_0009` |
| Audit `WSVC_ID` | refactor slug (`internal-transfer`, `vendor-payment`, `expense-payment`, `payroll-payment`) | any `pymn_xxxx_xxx` value |
| View Setting `screenId` | `payment-<sibling>-{scheduled,result}[-detail]` | `pymn_xxxx_xxx` |
| i18n key prefix | `<sibling>.*` snake (`internal_transfer.*`) + shared `payment.appr_stts.*` / `payment.proc_stat.*` | `pymn_xxxx_xxx` |
| Log MDC tag | refactor slug | `pymn_xxxx_xxx` |

Legacy `pymn_xxxx_xxx` ids appear **only** in: (1) JEX file:line citations in Javadoc / migration prose, (2) the "JEX counterpart" column of migration-traceability tables. Workspace rule: [CLAUDE.md § "Cross-Tree Rules → Refactor identifier naming"](../../../../CLAUDE.md).

### 4.2 Schema invariants

- All four siblings share `SCDUL_PYMN_SUMR` + `SCDUL_PYMN_SUMR_DETL`, discriminated by `SCDUL_PYMN_TYPE_CD`. CHECK constraint `scdul_pymn_sumr_chk01` allows all four codes (`'00'`, `'01'`, `'02'`, `'03'`) — relaxation landed in [V146__scdul_pymn_sumr_chk01_allow_payroll.sql](../../../src/main/resources/db/migration/V146__scdul_pymn_sumr_chk01_allow_payroll.sql).
- `SCDUL_PYMN_NO` is `VARCHAR(20)` in the refactor (DEV is 10) — refactor-only widening to fit the 14-char `YYYYMMDD<seq:6>` Java generator. Documented in [database/payment/internal-transfer-schema.md §7](../../references/database/payment/internal-transfer-schema.md).
- Each sibling has its **own** per-(tenant, day) key-sequence helper table (e.g. `INTERNAL_TRANSFER_KEY_SEQ`) so day-rolls and seq exhaustion are independent.
- Dispatch tables (`TB_PAY_TARGET`, `TB_PAY_FILE_MST`, `TB_PAY_FILE_DATA`, `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN`) are shared infrastructure — written via `TransferDispatchExecutor` in `payment/dispatch/`.

### 4.3 Error code allocation

Internal Transfer reserved `WBE2060`–`WBE2068`. Sibling-specific error codes follow the same `WBE2xxx` range — each sibling's stub doc names its allocations as it lands.

### 4.4 Cross-feature audit channel

Audit rows for the approval flow itself (request, approve, reject) carry `WSVC_ID = "approval-box"`, not the sibling slug — because the operation belongs to the shared `ApprovalBoxService`. The sibling-owned rows (create, update, delete, dispatch) carry the sibling's own `WSVC_ID`.

### 4.5 Typography

Inherited from the workspace [CLAUDE.md "Global Typography"](../../../../CLAUDE.md) — no inline `text-[Npx]`, `font-[N]`, `tracking-[…]`, or `style={{ fontFamily/fontSize/fontWeight }}` allowed. Use the workspace Tailwind tokens (`text-page-title`, `text-section-title`, `text-base`, etc.).

---

## 5. How to add a new payment sub-module (10 steps)

Distilled from the Internal Transfer baseline. The memory note `project_payment_module_reference` says **mirror Internal Transfer for Vendor / Expense / Payroll Scheduled flows**.

1. **Pick the slug** — kebab-case (`vendor-payment`, `expense-payment`, `payroll`). Confirm the `SCDUL_PYMN_TYPE_CD` value against the legacy JEX `_act.jsp` (cite file:line).
2. **JEX discovery** — locate the four screen-id families:
   - Scheduled list — `_r001` / `_c001` (execute / dispatch) / `_d001` (delete) actions
   - Upload popup — `_r001` (parse) / `_c001` (save) / `_u002` (update reporting account)
   - Scheduled detail — `_r001`
   - Result list + Result detail — `_r001`
   Plus any per-sibling sort metadata in WSVC XML.
3. **Cross-tree feature spec** — copy [internal-transfer.md](internal-transfer/README.md) shape to `docs/refactor-wecms/features/payment/<slug>.md`. Stamp the discriminator + audit `WSVC_ID` + JEX counterpart screen-ids.
4. **DB schema** — confirm the master + detail tables already exist (`SCDUL_PYMN_SUMR` + `_DETL`). Add a sibling-specific key-sequence helper table `{SIBLING_UPPER}_KEY_SEQ` in a new Flyway migration. If the `scdul_pymn_sumr_chk01` CHECK needs relaxing, ship a corrective migration (mirror V146).
5. **Backend package skeleton** — create `com.kosign.wecms.domain.payment.<sibling>` with `controller/`, `service/`, `export/` subfolders. Declare `static final String WSVC_ID = "<slug>"` at the top of the service. Wire the discriminator: `repo.findByBizKeyAndScdulPymnTypeCd(bizKey, ScdulPymnTypeCd.<CONST>.code(), …)`.
6. **DTOs + sort whitelist enum** — sibling-specific DTOs (`*Row`, `*ListResponse`, `*DetailResponse`, `Create*Request`, `*LineInput`, `*ResultLine`, `Update*Request`, `Dispatch*Request`, `Parse*Response`, `Parsed*Line`) under the sibling's `dto/`. Per-sibling sort enum implementing `SortableField`.
7. **Excel parser** — `*ExcelParser.java` mirrors `PymnHelper.importDataFromExcelFor<Type>`; consumes `SpecialCharValidator` with the sibling's `PymnType` constant.
8. **Frontend folder + routes** — create [`components/payment/<slug>/`](../../../../wecms-web/components/payment/) with the same five files (`ScheduledPaymentPage`, `ScheduledPaymentDetailPage`, `PaymentResultPage`, `PaymentResultDetailPage`, `<Sibling>UploadDialog`, plus `types.ts`). Add the four routes under [`app/(dashboard)/payment/<slug>/`](../../../../wecms-web/app/(dashboard)/payment/). Use Internal Transfer's `transfer-result` → `payment-result` rename convention for the sibling subroute (Vendor / Expense / Payroll all use `scheduled-payment` + `payment-result`).
9. **API client + locale entries + SCREEN_IDS** — sibling-specific API client at [`lib/api/<siblingCamelCase>.ts`](../../../../wecms-web/lib/api/) (e.g. `vendorPayment.ts`); 4 new `SCREEN_IDS.PAYMENT_<SIBLING>_*` constants + matching `SCREEN_ID_TO_ROUTE` entries; locale entries under the sibling's i18n prefix in `en.json` / `ko.json` / `vi.json` (no `km.json`).
10. **Audit checklist + cross-link** — add a parity-checklist file under `docs/refactor-wecms/audits/<slug>-parity-checklist.md` (mirror [internal-transfer-parity-checklist.md](../../references/audits/internal-transfer-parity-checklist.md)). Update §1 of this overview to flip the sibling's status row from 🟡 stub → ✅ full doc.

The shared infrastructure in §2 + §3 **does not change**. Adding a new sibling is purely additive.

---

## 6. Cross-link to Overseas Payment

Overseas Payment is **not** a sibling — it lives under [`com.kosign.wecms.domain.payment.overseas`](../../../src/main/java/com/kosign/wecms/domain/payment/overseas/) (plus the top-level `payment/{controller,dto,entity,repository,service}/` files for `OverseasPaymentController` / `OverseasPaymentService`) and uses the `OVRS_PYMN_MSTR` / `OVRS_PYMN_SUMR` / `OVRS_PYMN_DTLS` table family. There is no `SCDUL_PYMN_TYPE_CD` discriminator on the Overseas side ([pymn_0007_001_r001_act.jsp:198](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_001_r001_act.jsp#L198) routes to `getDynamicOvrs()` instead of `pymn_0001_001_r001`).

Overseas does share the `payment/validation/` blocked-chars validator via `PymnType.OVERSEAS_PAYMENT("OVER")`, and the `payment/approvalbox/` approval flow. But the master/detail schema is independent.

Per-feature spec lives separately (under `docs/refactor-wecms/features/payment/` if/when retroactively documented — not in scope for this overview).

---

Last verified: 2026-05-22

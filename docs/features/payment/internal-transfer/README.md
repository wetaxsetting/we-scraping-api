# Internal Transfer — Refactor Feature Spec (Backend)

> **WEB counterpart:** [wecms-web/.../internal-transfer/README.md](../../../../../wecms-web/docs/features/payment/internal-transfer/README.md)
>
> **Status:** Implemented — parity COMPLETE (MATCH). Source-code-verified 2026-06-23. Backend in [com.kosign.wecms.domain.payment.internaltransfer](../../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer).

## Sub-modules

| Sub-module | JEX WSVC | Refactor base | Scope |
|---|---|---|---|
| **Scheduled Transfer** | `pymn_0009_*` | `/api/v1/payment/internal-transfer` | upload/create, edit, delete, request-approval, approve/reject/withdraw/change-line, dispatch (R/U/D/T) |
| **Transfer Result** | `pymn_0010_*` | `/api/v1/payment/internal-transfer/results` | executed-result list + per-line detail + XLSX export (R only) |

## Standard docs (this folder)

| Doc | Content |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow per sub-module (Legacy/Spring/React), status derivation, key-gen |
| [database-mapping.md](database-mapping.md) | Tables, entity↔column, PK/FK, key-gen branches |
| [functional-specification.md](functional-specification.md) | Filters, status tabs+counts, columns, actions, validation, rules |
| [interface-specification.md](interface-specification.md) | Every REST endpoint — URL, method, authz, DTOs, params, errors |
| [migration-guide.md](migration-guide.md) | Legacy WSVC/IDO/act.jsp → controller/service, deviations, dead legacy |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix (MATCH / DEAD LEGACY / …) |
| [source-code-index.md](source-code-index.md) | Complete Legacy → Backend → Frontend file map |
| [test-scenarios.md](test-scenarios.md) | BE test inventory + scenarios + missing coverage |

### Endpoint summary

| Method · Path | Op | Authz screenId |
|---|---|---|
| `GET /` | Scheduled list | `payment-internal-transfer-scheduled` |
| `GET /withdrawal-accounts` | Withdrawal picker | scheduled |
| `GET /{id}` | Scheduled detail | scheduled |
| `GET /{id}/file` | Original Excel download | scheduled |
| `GET /{id}/approval/{apprSeqNo}/attachments/{fileId}` | Attachment download | scheduled |
| `GET /next-doc-no` | Doc-no preview | scheduled |
| `POST /uploads/parse` | Excel parse | scheduled |
| `POST /` | Create draft | scheduled |
| `PATCH /{id}/lines/{lineId}/reporting-account` | Edit reporting account | scheduled |
| `POST /{id}/dispatch` | Transfer (T) | scheduled |
| `DELETE /{id}` | Delete draft | scheduled |
| `POST /{id}/request-approval` | Request approval | scheduled |
| `DELETE /{id}/approval` | Withdraw | scheduled |
| `PUT /{id}/approval-line` | Change approval line | scheduled |
| `POST /{id}/approval-action` | Approve / Reject | scheduled |
| `GET/POST/DELETE /{id}/lines/{lineId}/match*` · `DELETE /{id}/matches` | Banking match | scheduled |
| `GET /results` | Result list | `payment-internal-transfer-result` |
| `GET /results/{id}` | Result detail | result |
| `POST /results/export` | XLSX export | result |

> _DEAD LEGACY:_ no `rerun` endpoint (re-run never ported — PO-deprecated).

---

> ## Functional-flow docs (this folder) — source-code-verified 2026-06-23
>
> Internal Transfer is also documented as **four distinct functional flows**, each traced from the actual controllers / services / repositories / DTOs / entities (backend) and pages / components / hooks / API clients / types (frontend). These predate the standard docs above and are folded into [business-flow.md](business-flow.md); they remain as the granular per-flow reference. Source code is the authority.
>
> | Doc | Flow |
> |---|---|
> | [upload-create-flow.md](upload-create-flow.md) | **Flow 1** — Upload / Create Internal Transfer (Excel parse → preview → save draft) |
> | [scheduled-transfer-flow.md](scheduled-transfer-flow.md) | **Flow 2** — Scheduled Transfer (search / detail / edit / delete / dispatch) |
> | [transfer-result-flow.md](transfer-result-flow.md) | **Flow 3** — Transfer Result (executed-result list + per-line detail + export) |
> | [approval-flow.md](approval-flow.md) | **Flow 4** — Approval / Approval Box (request → status → approve / reject / withdraw / change-line) |
>
> **Status correction (verified against current code, supersedes the "Phase A stub" notes below):** the Excel parser is a **real Apache POI parser** (`InternalTransferExcelParser`), not a stub; dispatch **writes the real `TB_PAY_*` rows** via `InternalTransferDispatchExecutor`; request-approval performs the **real `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM` fan-out** and approve/reject delegate to `ApprovalBoxService` (advancing the approver chain). The frontend `lib/api/internalTransfer.ts` Javadoc still calls several of these "stub" — that comment is stale; the wire calls are live. The four flow docs reflect the live behaviour.
>
> The rich, source-cited reference content below (screen inventory, data model, validation, state machine, cut-off table) remains valid and is cross-linked from the flow docs.

## Screens (this feature)

The four screens are sections of this hub (no separate per-screen files). Full per-screen detail is in
§3 "Screen inventory (JEX → refactor)" below; one-line summaries:

| Screen | JEX WSVC | Scope | Status |
|---|---|---|---|
| Scheduled Transfer | `pymn_0009_001` | List + create scheduled internal transfer batches (Excel upload, request approval, transfer). | Implemented (backend + frontend live-wired) |
| Scheduled Transfer Details | `pymn_0009_001` (view `pymn_0009_003`) | Drill-down detail of one scheduled batch — line items, amounts, withdrawal account, approver chain. | Implemented (backend + frontend live-wired) |
| Transfer Result | `pymn_0010_001` | Executed internal-transfer results with status filter (Incomplete / Processing / Success / Failed). | Implemented (backend + frontend live-wired) |
| Transfer Result Details | `pymn_0010_001` (view `pymn_0010_002`) | Per-transaction result detail (Success / Failed + failure reason) for one executed item. | Implemented (backend + frontend live-wired) |


| Field | Value |
|---|---|
| System | Refactor (Spring Boot + Next.js) |
| Slug | `internal-transfer` |
| Module | Payment → Internal Transfer |
| JEX counterparts | `pymn_0009_001`, `pymn_0009_002`, `pymn_0009_003`, `pymn_0010_001`, `pymn_0010_002` |
| JEX doc | `docs/jex/features/internal-transfer.md` *(to be authored — JEX spec; Needs Confirmation)* (pair this slug — JEX spec to be authored alongside this refactor port) |
| Refactor module | `payment` |
| REST URI base | `/api/v1/payment/internal-transfer` |
| Frontend route base | `/payment/internal-transfer` |
| View Setting WEB_SVC_IDs | `payment-internal-transfer-scheduled` (Scheduled Transfer list), `payment-internal-transfer-result` (Transfer Result list), `payment-internal-transfer-scheduled-detail`, `payment-internal-transfer-result-detail` |
| Audit `WSVC_ID` (refactor slug) | `internal-transfer` (17 chars — fits `AUDIT_LOGS_INFM.WSVC_ID VARCHAR(20)`) |
| Status | **Implemented — documenting as locked baseline** (last verified 2026-05-21). Backend lands in [com.kosign.wecms.domain.payment.internaltransfer](../../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer); frontend in [components/payment/internal-transfer/](../../../../../wecms-web/components/payment/internal-transfer). Sibling Payment sub-modules (vendor-payment / expense-payment / payroll) mirror this design — see [payment hub README](../README.md). |
| Sprint | Sprint 2 (Payment module — Internal Transfer first; locked baseline for Vendor / Expense / Payroll ports) |
| Author | Claude Code (R&D planning, 2026-05-13; Phase A backend + frontend Phase A; refreshed 2026-05-21 against as-implemented code) |
| Companion docs | [payment hub README](../README.md) (cross-sibling index) · [migration/internal-transfer-mapping.md](../../../references/migration/internal-transfer-mapping.md) · [database/payment/internal-transfer-schema.md](../../../references/database/payment/internal-transfer-schema.md) · [backend/internal-transfer.md](../../../references/business-rules/internal-transfer.md) · [frontend/internal-transfer.md](../../../../../wecms-web/docs/features/payment/internal-transfer/README.md) · [audits/internal-transfer-parity-checklist.md](../../../audits/legacy-parity/internal-transfer-parity-checklist.md) |

> **Identifier rule.** Every refactor surface — Next.js route, Spring REST URI, Java package, DTO names, frontend feature folder, i18n key prefix, log tag, View Setting `screenId`, and `AUDIT_LOGS_INFM.WSVC_ID` — uses the kebab-case slug `internal-transfer`. Legacy JEX WSVC ids (`pymn_0009_001`, `pymn_0009_002_c001`, …) appear in this doc **only** as JEX file:line citations or in migration-traceability prose. See workspace [CLAUDE.md](../../../../CLAUDE.md) § Cross-Tree Rules and [wecms-api/CLAUDE.md](../../../../CLAUDE.md) § "Refactor identifier naming".

---

## 1. Overview & business goal

Internal Transfer is the Payment-module feature for **company-initiated batch fund movements between corporate bank accounts**. A user uploads an Excel of payment lines (counterparty bank/account/amount/usage/etc.), the system validates and persists each line, an approval workflow runs against the resulting batch, and the approved batch is dispatched to the bank (API path or File path depending on routing rules) for real-time or scheduled execution. The user later returns to the Transfer Result screen to see which lines succeeded, failed, or remain pending — including the response code/message from the bank for each line.

The refactor ports the **full UX surface** (upload → preview → save draft → request approval → execute transfer → view result) but reuses backend-side behavioural primitives from the existing JEX system:

- Cut-off-time validation per bank/transfer-type before "Transfer" is allowed.
- Routing decision (TB_PAY_TARGET API path vs TB_PAY_FILE_MST file path) driven by `OUT_BANK_CD` + withdrawal/deposit currency.
- Audit-history capture into `AUDIT_LOGS_INFM` with slug `internal-transfer` for every write.

Refactor R&D stays **local-only** — there is no real bank integration in this tree. The downstream `TB_PAY_TARGET` / `TB_PAY_FILE_MST` records are written but never picked up by a bank API; verification happens via DB inspection.

## 2. User roles & permissions

Sourced from JEX permission matrix [WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp:530-547](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0190_003_view.jsp):

| WSVC | Label in matrix | Permission flags | Refactor meaning |
|---|---|---|---|
| `pymn_0009_001` | "Scheduled Payment" (Internal Transfer group) | **R / U / D / T** | List/Create/Edit/Delete/Execute transfer (`T`) on Scheduled Transfer screen |
| `pymn_0010_001` | "Transfer Result" | **R** only | Read-only list of past transfer results |

The `T` (Transfer) capability is the per-row "Execute transfer" action on the Scheduled Transfer list — it triggers the bank dispatch (formerly `pymn_0009_001_c001`). Refactor surfaces:

| Capability | Backend endpoint | Frontend action |
|---|---|---|
| R | `GET /api/v1/payment/internal-transfer` and `GET /api/v1/payment/internal-transfer/{id}` and `GET /api/v1/payment/internal-transfer/results` and `GET /api/v1/payment/internal-transfer/results/{id}` | Open list / detail pages |
| U | `PATCH /api/v1/payment/internal-transfer/{id}/lines/{lineId}/reporting-account` | Update Reporting Account on a draft line |
| D | `DELETE /api/v1/payment/internal-transfer/{id}` | Delete a draft batch before approval |
| T | `POST /api/v1/payment/internal-transfer/{id}/dispatch` | Execute the Transfer — actual bank dispatch (TB_PAY_TARGET / TB_PAY_FILE_MST insert) |

Authority resolution is the standard WeCMS multi-tenant scope:

- `TenantContext.currentBizKey()` scopes every query.
- Approval-driven access (a batch with `APPR_SEQ_NO` set is locked from delete) is enforced server-side, not frontend.
- `ADMIN` vs `USER` (from `BUSI_ADMN_INFM`) is the standard `ROLE_ADMIN`/`ROLE_USER` mapping; no Internal-Transfer-specific role split.

## 3. Screen inventory (JEX → refactor)

| JEX WSVC | Purpose | Refactor surface |
|---|---|---|
| `pymn_0009_001` (view) | **Scheduled Transfer** — main list page (Payment > Internal Transfer > Scheduled Transfer) | Implemented at [components/payment/internal-transfer/ScheduledTransferPage.tsx](../../../../../wecms-web/components/payment/internal-transfer/ScheduledTransferPage.tsx) (wrapped by [`app/(dashboard)/payment/internal-transfer/scheduled-transfer/page.tsx`](<../../../../../wecms-web/app/(dashboard)/payment/internal-transfer/scheduled-transfer/page.tsx>)). Composes [`<PaymentScheduledBatchListPage>`](../../../../../wecms-web/components/payment/shared/PaymentScheduledBatchListPage.tsx). |
| `pymn_0009_002` (view) | **Upload Internal Transfer** popup — Excel upload + preview with per-cell error highlights + Save Draft | Implemented as [components/payment/internal-transfer/InternalTransferUploadDialog.tsx](../../../../../wecms-web/components/payment/internal-transfer/InternalTransferUploadDialog.tsx) — thin wrapper over the **Payment-module** shared [`<PaymentExcelUploadDialog>`](../../../../../wecms-web/components/payment/shared/PaymentExcelUploadDialog.tsx) (which in turn wraps the app-wide [`<ExcelUploadDialog>`](../../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx)). |
| `pymn_0009_003` (view) | **Scheduled Transfer Detail** — drill-down on a batch (one row per line, with bank, recipient, amount, ERP linkage, note) | Implemented at [components/payment/internal-transfer/ScheduledTransferDetailPage.tsx](../../../../../wecms-web/components/payment/internal-transfer/ScheduledTransferDetailPage.tsx) (wrapped by [`app/(dashboard)/payment/internal-transfer/scheduled-transfer/[id]/page.tsx`](<../../../../../wecms-web/app/(dashboard)/payment/internal-transfer/scheduled-transfer/[id]/page.tsx>)). Composes [`<PaymentDetailPage>`](../../../../../wecms-web/components/payment/shared/PaymentDetailPage.tsx). |
| `pymn_0010_001` (view) | **Transfer Result** — main list page (Payment > Internal Transfer > Transfer Result) | Implemented at [components/payment/internal-transfer/TransferResultPage.tsx](../../../../../wecms-web/components/payment/internal-transfer/TransferResultPage.tsx) (wrapped by [`app/(dashboard)/payment/internal-transfer/transfer-result/page.tsx`](<../../../../../wecms-web/app/(dashboard)/payment/internal-transfer/transfer-result/page.tsx>)). Composes [`<PaymentResultBatchListPage>`](../../../../../wecms-web/components/payment/shared/PaymentResultBatchListPage.tsx). |
| `pymn_0010_002` (view) | **Transfer Result Detail** — drill-down with per-line `PROC_STAT` + `RS_CD` + `RS_MSG` + `PROC_DTTM` | Implemented at [components/payment/internal-transfer/TransferResultDetailPage.tsx](../../../../../wecms-web/components/payment/internal-transfer/TransferResultDetailPage.tsx) (wrapped by [`app/(dashboard)/payment/internal-transfer/transfer-result/[id]/page.tsx`](<../../../../../wecms-web/app/(dashboard)/payment/internal-transfer/transfer-result/[id]/page.tsx>)). Composes [`<ResultDetailView>`](../../../../../wecms-web/components/payment/shared/ResultDetailView.tsx). |

The Scheduled Transfer list page is **shared chrome** through [`<PaymentScheduledBatchListPage>`](../../../../../wecms-web/components/payment/shared/PaymentScheduledBatchListPage.tsx); the Transfer Result list through [`<PaymentResultBatchListPage>`](../../../../../wecms-web/components/payment/shared/PaymentResultBatchListPage.tsx). Detail pages compose [`<PaymentDetailPage>`](../../../../../wecms-web/components/payment/shared/PaymentDetailPage.tsx) / [`<ResultDetailView>`](../../../../../wecms-web/components/payment/shared/ResultDetailView.tsx). See § 8.

Read-only references (act-level — verified by direct read of each JSP, 2026-05-13):

| JEX action JSP | File:line | Role |
|---|---|---|
| `pymn_0009_001_r001_act.jsp` | [pymn_0009_001_r001_act.jsp:21-64](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_r001_act.jsp) | List read — validates `FROM_DT`/`TO_DT`, delegates to `PymnHelper.pymn_0001_001_r001(scdulPymnTypeCd="03")` |
| `pymn_0009_001_c001_act.jsp` | [pymn_0009_001_c001_act.jsp:44-110](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_c001_act.jsp) | **Execute transfer** — validates `SCDUL_PYMN_NO` + `TRNF_CCY_CD`, calls `PymnHelper.validateTransferTiming` then `PymnHelper.pymn_0009_001_c001` (routes to TB_PAY_TARGET or TB_PAY_FILE_MST) |
| `pymn_0009_001_d001_act.jsp` | [pymn_0009_001_d001_act.jsp:36-115](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_d001_act.jsp) | Delete draft batch — rejects when `APPR_SEQ_NO` is set; writes ERP_TRNS_HSTR delete row via `ErpTrnsHisHelper.erpTrnsHisDeleter` if `getFullPath()` non-null |
| `pymn_0009_002_r001_act.jsp` | [pymn_0009_002_r001_act.jsp:23-79](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_002_r001_act.jsp) | Excel parse + per-line validation; delegates to `PymnHelper.importDataFromExcelForInternalTransfer(uploadedFile, fileName, util, result, idoCon, loginUser, bankCode, PymnType.INTERNAL_TRANSFER.getValue())`; returns rows with `ERR_FLD` / `ERR_FLD_CAUS` |
| `pymn_0009_002_c001_act.jsp` | [pymn_0009_002_c001_act.jsp:22-82](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_002_c001_act.jsp) | Save draft batch (SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL) — delegates to `PymnHelper.uploadPaymentExcel(..., "pymn_0009_002_c001")` |
| `pymn_0009_002_u002_act.jsp` | [pymn_0009_002_u002_act.jsp:30-102](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_002_u002_act.jsp) | Update one line's Reporting Account (`CHART_ACCT_*`) — validates `(BIZ_KEY, CHART_ACCT_SEQ_NO, DEL_YN='N')` exists in `CHART_ACCT_INFM` via `CHART_ACCT_INFM_R001`, then delegates to `PymnHelper.pymn_0001_002_u002` |
| `pymn_0009_003_r001_act.jsp` | [pymn_0009_003_r001_act.jsp:33-141](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_003_r001_act.jsp) | Read draft batch master + lines (IDOs `SCDUL_PYMN_SUMR_R012` + `SCDUL_PYMN_SUMR_R013`); copies `TRNF_CCY_CD` → `CCY_CD` per line; emits `FILE_URL` pointing at `pymn_0009_003_r002.act` |
| `pymn_0010_001_r001_act.jsp` | [pymn_0010_001_r001_act.jsp:28-72](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0010_001_r001_act.jsp) | Result list read — validates `FROM_DT`/`TO_DT`, delegates to `PymnHelper.pymn_0002_001_r001(ScdulPymnTypeCd.INTERNAL.getValue())` |
| `pymn_0010_002_r001_act.jsp` | [pymn_0010_002_r001_act.jsp:30-73](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0010_002_r001_act.jsp) | Result detail — validates `SCDUL_PYMN_NO`, delegates to `PymnHelper.pymn_0002_002_r001(ScdulPymnTypeCd.INTERNAL.getValue())` |

Helper / DAO / entity references (read-only — JEX tree):

- `PymnHelper.pymn_0001_001_r001` — [PymnHelper.java:1923-2400](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) — list query for Scheduled Transfer / Vendor Scheduled / Expense Scheduled (parameterised by `SCDUL_PYMN_TYPE_CD`).
- `PymnHelper.pymn_0009_001_c001` — [PymnHelper.java:2419-2790](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) — execute-transfer routing (TB_PAY_TARGET vs TB_PAY_FILE_MST), Woori USD→VND and Shinhan USD→VND special-case logic. Routing source verified inline:
  - `isWooriUsdToVnd = isWooriBank && TRNF_CCY_CD="USD" && wdrlCcyCd=USD && depoCcyCd="VND"` (line 2486-2489).
  - `isShinhanUsdToVnd = SHINHAN && wdrlCcyCd=USD && depoCcyCd="VND"` (line 2492-2494).
  - API path: `if ((TRNF_CCY_CD="VND" && !isShinhanUsdToVnd) || isWooriUsdToVnd)` → TB_PAY_TARGET insert (line 2509-2570). Else file path (TB_PAY_FILE_MST + N TB_PAY_FILE_DATA).
  - WDRL_CCY_CD NULL-fallback: legacy issue #42761 — line 2475-2483 reads `wdrlCcyCd` from `SCDUL_PYMN_SUMR`; if null, falls back to `ACCT_MSTR.CCY_CD` looked up by `(biz_key, acct_no=WDRL_ACT_NO)`.
- `PymnHelper.validateTransferTiming` — [PymnHelper.java:3091-3181](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) — cut-off-time gate per NAPAS / CITAD / SWIFT.
- `PymnHelper.getCutoffConfig` — [PymnHelper.java:3187-3216](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) — per-bank cut-off table.
- `PymnHelper.uploadPaymentExcel` — [PymnHelper.java:1300-1470](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) — save draft from validated Excel rows.
- `PymnHelper.importDataFromExcelForInternalTransfer` — [PymnHelper.java:163-300](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) — Excel parse + per-line validation.
- `PymnHelper.pymn_0002_001_r001` / `pymn_0002_002_r001` — [PymnHelper.java:1859-1922 / 2939-3050](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) — Result list / Result detail.
- `PymnHelper.pymn_0001_002_u002` — [PymnHelper.java:3054-3090](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) — Update Reporting Account on a draft line.
- Entities: `ScdulPymnSumr` (PK `BIZ_KEY + SCDUL_PYMN_NO`) — [ScdulPymnSumr.java:45-134](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ScdulPymnSumr.java); `ScdulPymnSumrDetl` (PK `BIZ_KEY + SCDUL_PYMN_NO + SCDUL_PYMN_DETL_NO`) — [ScdulPymnSumrDetl.java:35-153](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ScdulPymnSumrDetl.java); plus `TbPayTarget`, `TbPayFileMst`, `TbPayFileData` on the dispatch side.

## 4. Data model (in-scope tables — verified against DEV `wecmsvndev` schema, 2026-05-13)

All tables below are in the PROD Active Table List ([api CLAUDE.md § "Active table list"](../../../../CLAUDE.md)). Schema parity rule applies — see [database/payment/internal-transfer-schema.md](../../../references/database/payment/internal-transfer-schema.md) for column-by-column DDL intent.

| Table | Role | PK (verified from DEV `pg_constraint`) |
|---|---|---|
| `SCDUL_PYMN_SUMR` | Batch master — one row per uploaded file = one Scheduled Transfer | `(biz_key, scdul_pymn_no)` |
| `SCDUL_PYMN_SUMR_DETL` | Batch line — one row per recipient/amount line within a batch | `(biz_key, scdul_pymn_no, scdul_pymn_detl_no)` |
| `SCDUL_PYMN_SUMR_HIS` | Audit-history twin of SCDUL_PYMN_SUMR (`_HIS` adds `hist_mod_dtm`, `hist_mod_uid`, `stts`) | full master PK + `hist_mod_dtm` |
| `SCDUL_PYMN_SUMR_DETL_HIS` | Audit-history twin of SCDUL_PYMN_SUMR_DETL | full detail PK + `hist_mod_dtm` |
| `TB_PAY_TARGET` | API-path dispatch target (one row per detail line when TRNF_CCY_CD=VND or Woori USD→VND) | `(tran_dt, biz_key, tran_seq)` |
| `TB_PAY_FILE_MST` | File-path dispatch master (USD→USD, Shinhan USD→VND, VND→USD Shinhan) | `(tran_dt, biz_key, file_seq_no)` |
| `TB_PAY_FILE_DATA` | File-path dispatch line | `(tran_dt, biz_key, file_seq_no, seq)` |
| `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` | Join — links a detail line to its TB_PAY_TARGET row | `(biz_key, scdul_pymn_no, scdul_pymn_detl_no)` ; columns `tran_dt`, `tran_seq` (nullable, FK to TB_PAY_TARGET) |
| `EXPT_RESOL_MSTR` | Approval-request master — linked via `SCDUL_PYMN_SUMR.APPR_SEQ_NO` | `(biz_key, appr_seq_no)` (read-only here) |
| `ACCT_MSTR` | Withdrawal account (`OUT_BANK_CD` + `WDRL_ACT_NO`) — used to resolve `WDRL_CCY_CD` fallback for legacy rows (issue #42761) | (read-only here) |
| `BANK_INFM` | Bank registry — resolves `OUT_BANK_NM` / `TAR_BANK_NM` | (read-only here) |
| `CHART_ACCT_INFM` | Reporting account (ERP mapping) — referenced in 0009_002_u002 and detail rows | `(biz_key, chart_acct_seq_no)` (read-only here) |
| `ERP_TRNS_HSTR` | ERP audit-history — delete writes a row via `ErpTrnsHisHelper` (mirrors `pymn_0009_001_d001_act.jsp:95-98`) | (write-only here on delete) |
| `AUDIT_LOGS_INFM` | Refactor-side audit log — every mutating write goes here with `WSVC_ID = 'internal-transfer'` (`VARCHAR(20)` — 17 chars fits) | `(biz_key, audit_logs_seq_no, lang_cd)` |

> ### Schema reconciliations vs. prior drafts (resolved 2026-05-13)
>
> Direct `information_schema` queries against DEV revealed corrections to the previously drafted spec:
>
> - **`SCDUL_PYMN_NO` is `VARCHAR(10)` in DEV**, and the refactor entity now maps it at `length=10` (`ScdulPymnSumr.scdulPymnNo`) — DEV parity. **Update (2026-06-09 OD-2 reversal):** the earlier refactor-only date-prefixed 14-char generator (`YYYYMMDD<seq:6>`) that would have needed `VARCHAR(20)` was **removed**; `InternalTransferKeyGenerator` now funnels through `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no',biz_key)` and mints the same plain per-tenant int the live JEX DB does (1-3 chars). The controller path-param regex stays permissive (`1..30` digits) only to accept legacy/seed values; the column itself is DEV-parity 10.
> - **There is only ONE conn table** in DEV: `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` (note `SCDU_` not `SCDUL_`). It links detail → `TB_PAY_TARGET` via nullable `(tran_dt, tran_seq)`. The previously-drafted second table `SCDUL_PYMN_PAY_FILE_MST_CONN` **does not exist** — the file-path linkage is encoded directly on `SCDUL_PYMN_SUMR_DETL.pay_file_seq_no + pay_seq` columns and on `SCDUL_PYMN_SUMR.pay_file_seq_no` master pointer.
> - **No `FULL_PATH` / `FILE_INFO` columns on `SCDUL_PYMN_SUMR`.** Three separate file columns: `org_file_nm VARCHAR(500)`, `save_file_nm VARCHAR(500)`, `file_size VARCHAR(100)`. The legacy entity `ScdulPymnSumr.getFullPath()` builds an in-app path from `save_file_nm` plus a server-side prefix; refactor follows the ERP Transaction History precedent (see [docs/refactor-wecms/database/payment/internal-transfer-schema.md § 8 File storage](../../../references/database/payment/internal-transfer-schema.md)).
> - **No `APPR_STTS` / `TRNF_STTS` / `DRAFT_USER_NM` / `DRAFT_USER_ID` on `SCDUL_PYMN_SUMR`** — these are computed/joined columns produced by the list IDO from `EXPT_RESOL_MSTR` and `USER_INFM`. The refactor list service must JOIN explicitly.
> - **`total_tran_amt` is `NUMERIC(20, 6)` NOT NULL** on `SCDUL_PYMN_SUMR` — 6 decimal places. `tran_amt` on `SCDUL_PYMN_SUMR_DETL` is also `NUMERIC(20, 6)`, but on `TB_PAY_TARGET` / `TB_PAY_FILE_DATA` it's `NUMERIC(23, 2)`. The dispatch rounds 6→2 decimals.
> - **PK column order matters** for dispatch tables: `TB_PAY_TARGET (tran_dt, biz_key, tran_seq)`, `TB_PAY_FILE_MST (tran_dt, biz_key, file_seq_no)`, `TB_PAY_FILE_DATA (tran_dt, biz_key, file_seq_no, seq)` — `tran_dt` first, not `biz_key`.

## 5. End-to-end user flows

### 5.1 Create a draft batch (upload Excel)

```
User on Scheduled Transfer list
   → click "Excel Upload" (toolbar)
   → <ExcelUploadDialog> opens
     1. User downloads the Internal Transfer Excel template
     2. User selects the populated file
     3. Frontend POSTs multipart to POST /api/v1/payment/internal-transfer/uploads/parse
        (backend mirrors pymn_0009_002_r001 — parse + per-line validation;
         returns rows with err_fld / err_fld_caus per cell)
     4. Dialog previews rows with red highlights on error cells
     5. User enters batch Title (TITL) + selects withdrawal account
        (BANK_CD + ACCT_NO + CCY_CD + TRNF_CCY_CD)
     6. User clicks Save Draft
        → POST /api/v1/payment/internal-transfer
          (backend mirrors pymn_0009_002_c001 — persist SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL,
           original Excel saved under wecms.internal-transfer.storage-root,
           org_file_nm / save_file_nm / file_size columns stamped)
   → Dialog closes, list re-fetches; new batch row appears with APPR_STTS = "DRAFT", TRNF_YN = "N"
```

### 5.2 Edit Reporting Account on a draft line

```
User clicks into a draft batch
   → /payment/internal-transfer/scheduled-transfer/[id]
   → Detail page lists every line with the assigned Reporting Account (CHART_ACCT_CD / CHART_ACCT_NM)
   → User clicks "Reporting Account" on a row
   → A picker shows CHART_ACCT_INFM rows for the tenant (DEL_YN='N')
   → User selects an account
     → PATCH /api/v1/payment/internal-transfer/{id}/lines/{lineId}/reporting-account
       (backend mirrors pymn_0009_002_u002 — looks up CHART_ACCT_INFM_R001 to validate, then SCDUL_PYMN_SUMR_DETL update)
```

### 5.3 Request approval

The approval workflow is **not Internal-Transfer-specific** — it goes through the cross-feature Approval Box (parallel refactor feature under `payment/approval-box`). The Scheduled Transfer detail page renders a "Request Approval" CTA that submits the batch to the approval engine; once `APPR_SEQ_NO` is stamped on `SCDUL_PYMN_SUMR`, the Delete capability is disabled (mirrors [pymn_0009_001_d001_act.jsp:90-92](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_d001_act.jsp)).

This refactor scope does **not** re-implement the approval engine — it consumes the cross-module endpoint planned in `docs/refactor-wecms/features/payment/approval-box/`. The Internal Transfer feature only writes `APPR_SEQ_NO` once the approval engine returns the new resolution id. See OQ-5.

### 5.4 Execute transfer (the "T" capability)

```
On an approved batch (APPR_STTS = "01" (approved), TRNF_YN = "N")
   → User clicks "Transfer" on Scheduled Transfer detail
   → Confirmation dialog ("Are you sure?" — variant: confirmation)
   → POST /api/v1/payment/internal-transfer/{id}/dispatch
     Body: { trnfCcyCd: "VND" | "USD" }
     Backend steps (mirrors pymn_0009_001_c001_act.jsp:44-110 + PymnHelper.pymn_0009_001_c001:2419-2790):
       1. Reload SCDUL_PYMN_SUMR via ScdulPymnSumrPk(loginUser).copyFrom(input);
          reject WBE0404 if missing OR if TRNF_YN == "Y"
       2. Verify linked EXPT_RESOL_MSTR (joined on appr_seq_no) has APPR_TYPE_CD == "01" (approved)
       3. PymnHelper.validateTransferTiming — bank cut-off gate per NAPAS/CITAD/SWIFT
          (see PymnHelper.java:3091-3216 and § 7 cut-off-time table)
       4. Compute routing flags isWooriUsdToVnd / isShinhanUsdToVnd from
          OUT_BANK_CD + WDRL_CCY_CD (with ACCT_MSTR fallback) + DEPO_CCY_CD (from first detail row)
       5. If (TRNF_CCY_CD == "VND" AND NOT isShinhanUsdToVnd) OR isWooriUsdToVnd:
            → API path — insert one TB_PAY_TARGET row per line:
                tran_dt   = DateTimeUtils.utcNow().toLocalDate()
                biz_key   = loginUser.bizKey
                tran_seq  = next per-(tran_dt, biz_key) sequence (via the wb_get_next_val funnel)
                proc_stat = legacy transfer-pending code stamped by InternalTransferDispatchExecutor
                            (the bank daemon later flips it to '0' success / '3' fail; not run locally)
                ant_div_cd = (OUT_BANK_CD == TAR_BANK_CD) ? "INTER" : detail.ANT_DIV_CD.trim()
                trsc_ccy_cd = isWooriUsdToVnd ? "USD" : "VND"
              And update SCDUL_PYMN_SUMR_DETL with pay_tran_dt + pay_tran_seq.
              And insert SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN row linking the two.
          Else:
            → File path — insert TB_PAY_FILE_MST + N TB_PAY_FILE_DATA rows
              And update SCDUL_PYMN_SUMR_DETL with pay_file_seq_no + pay_seq.
              And SCDUL_PYMN_SUMR.pay_file_seq_no is stamped to the master file_seq_no.
       6. Stamp SCDUL_PYMN_SUMR.TRNF_YN = "Y", TRNF_DTM = utcNow(), MOD_DTM = utcNow(), MOD_USER
       7. Write AUDIT_LOGS_INFM row with WSVC_ID = 'internal-transfer', op = 'dispatch'
          (ref_no = scdulPymnNo, ref_no1 = trnfCcyCd, ref_no2 = routing: "api"|"file")
   → On success, frontend shows success dialog and re-fetches list
```

R&D scope note: no real bank picks up TB_PAY_TARGET / TB_PAY_FILE_MST in the refactor tree. The dispatch endpoint persists the rows and stops there; the Transfer Result screen will show `PROC_STAT` per the seed data.

### 5.5 Delete a draft batch

```
On a draft (no APPR_SEQ_NO)
   → DELETE /api/v1/payment/internal-transfer/{id}
   Backend (mirrors pymn_0009_001_d001_act.jsp:36-115):
     1. Load SCDUL_PYMN_SUMR, reject WBE0404 if missing
     2. Reject WBE1063 if APPR_SEQ_NO is set
     3. If SCDUL_PYMN_SUMR.save_file_nm present, write an ERP_TRNS_HSTR row
        via ErpTrnsHisHelper.erpTrnsHisDeleter(util, result, idoCon, loginUser,
           fileInfo, srcFile, ErpTypeCd.INTERNAL_TRANSFER_PAYMENT)
     4. DELETE FROM SCDUL_PYMN_SUMR_DETL WHERE biz_key=? AND scdul_pymn_no=?
     5. DELETE FROM SCDUL_PYMN_SUMR WHERE PK
     6. AUDIT_LOGS_INFM write { ref_no=scdulPymnNo }
```

### 5.6 Transfer Result inquiry

```
User opens /payment/internal-transfer/transfer-result
   → GET /api/v1/payment/internal-transfer/results?fromDt=&toDt=&trnfStts=&srchWd=
   → List shows one row per dispatched batch with TOT_TRNS_CNT / TOT_TRNS_SUCC_CNT / TOT_TRNS_ERROR_CNT
   → Click a row → /payment/internal-transfer/transfer-result/[id]
   → GET /api/v1/payment/internal-transfer/results/{id}
     Backend joins SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL with the dispatch tables to surface
     per-line PROC_STAT + RS_CD + RS_MSG + PROC_DTTM + RSLT_DTTM + BANK_TRNS_SHORT_ID.
     Joining strategy:
       - API path lines: JOIN via SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN(tran_dt, tran_seq) → TB_PAY_TARGET
       - File path lines: JOIN via SCDUL_PYMN_SUMR_DETL.pay_file_seq_no + pay_seq → TB_PAY_FILE_DATA
```

## 6. Validation rules per field

Sources cited for each rule: WSVC XML (field id + length), `_act.jsp` validate block, helper logic.

### 6.1 Save draft (`POST /api/v1/payment/internal-transfer`)

Mirrors `pymn_0009_002_c001` ([WSVC.pymn_0009_002_c001.xml:32-66](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_002_c001.xml) + [pymn_0009_002_c001_act.jsp:22-39](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_002_c001_act.jsp)):

| Wire field | Type | Length | Required | Source rule | Notes |
|---|---|---|---|---|---|
| `titl` | string | 100 | ✅ | `validateRequired(TITL)` | Batch title shown on list (DB `SCDUL_PYMN_SUMR.titl VARCHAR(100) NOT NULL`) |
| `file_data` | multipart file | — | ✅ | `validateRequired + validateFile(FILE_DATA)` | Original Excel uploaded by user |
| `bank_cd` | string | 20 | ✅ | `validateRequired(BANK_CD)` | Withdrawal bank code (lookup `BANK_INFM`); DB stores as `out_bank_cd VARCHAR(15)` |
| `acct_no` | string | 100 | ✅ | `validateRequired(ACCT_NO)` | Withdrawal account number → `wdrl_act_no` |
| `ccy_cd` | string | 3 | ✅ | `validateRequired(CCY_CD)` | Withdrawal currency (matches `ACCT_MSTR.CCY_CD`) |
| `trnf_ccy_cd` | string | 3 | ✅ | `validateRequired(TRNF_CCY_CD)` | Transfer currency (drives routing) |
| `lines[].finc_mda_no` | string | 100 | ✅ per line | `validateRequired(FINC_MDA_NO)` in REC loop | Recipient bank account number |
| `lines[].rcpt_nm` | string | 100 | ⚠ recommended | not server-validated but rejected later by bank | Recipient name (DB `VARCHAR(100)`) |
| `lines[].tran_amt` | decimal | NUMERIC(20,6) | ⚠ | parsed via `NumberUtils.createBigDecimal` in `pymn_0009_001_c001` | Amount; ≤ 0 fails the bank dispatch |
| `lines[].tar_bank_cd` | string | 15 | ⚠ | resolved against `BANK_INFM` | Counterparty bank code |
| `lines[].ant_div_cd` | string | 5 | ⚠ | `AntDivCd.fromValue` ([PymnHelper.java:2528](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)) | `N` / `NAPAS` / `INTER` / `S` / `SWIFT` / `C` / `CITAD` (DB `VARCHAR(5)`) |
| `lines[].tran_usag` | string | 200 | ⚠ | per-row Excel preview validation | Free-text transfer purpose |
| `lines[].doc_no` | string | 50 | optional | — | ERP document number |
| `lines[].note` | string | 200 | optional | DB length is 200 (NOT 255 — corrected from earlier drafts) | Per-line note |
| `lines[].chart_acct_seq_no`, `lines[].chart_acct_cd` | string | 20 / 20 | optional | optional ERP mapping at save time | Can also be set later via `PATCH … /reporting-account` |

Constraints derived from JEX behaviour (not in WSVC):

- The same `SCDUL_PYMN_NO` PK must be generated server-side; legacy uses `wb_get_next_val('scdul_pymn_sumr', 'scdul_pymn_no', BIZ_KEY)`. Refactor implements a Java-side generator per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) § "Key-generation rule for migrated legacy tables". **The column is `VARCHAR(10)` — the generator must produce ≤ 10 chars.** See [database/payment/internal-transfer-schema.md § Key generation](../../../references/database/payment/internal-transfer-schema.md) for the strategy and the open question **BLOCKER-A (was OQ-3)**.
- `SCDUL_PYMN_TYPE_CD = "03"` is the discriminator for Internal Transfer ([pymn_0009_001_r001_act.jsp:61](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_r001_act.jsp)). DB column is `VARCHAR(2)` NOT NULL.
- Withdrawal account must belong to the same tenant (`ACCT_MSTR.BIZ_KEY = currentBizKey`).

### 6.2 Excel parse (`POST /api/v1/payment/internal-transfer/uploads/parse`)

Mirrors `pymn_0009_002_r001` ([WSVC.pymn_0009_002_r001.xml:32-72](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_002_r001.xml) + [pymn_0009_002_r001_act.jsp:23-79](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_002_r001_act.jsp)):

| Wire field | Required | Notes |
|---|---|---|
| `file_data` (multipart) | ✅ | Excel template |
| `bank_cd` | ✅ | Withdrawal bank — drives per-row counterparty validation |

Response carries `tot_cnt` / `suc_cnt` / `err_cnt` plus `lines[]` with `err_fld` (comma-separated) and `err_fld_caus` (cause string) per row. Per-line validation logic lives in `PymnHelper.importDataFromExcelForInternalTransfer` ([PymnHelper.java:163+](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)) — accepts `PymnType.INTERNAL_TRANSFER.getValue()` as the type discriminator.

### 6.3 Update Reporting Account (`PATCH .../lines/{lineId}/reporting-account`)

Mirrors `pymn_0009_002_u002` ([WSVC.pymn_0009_002_u002.xml:32-43](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_002_u002.xml) + [pymn_0009_002_u002_act.jsp:30-57](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_002_u002_act.jsp)):

| Wire field | Length | Required |
|---|---|---|
| `scdul_pymn_no` (path) | 10 (DB) | ✅ |
| `scdul_pymn_detl_no` (path) | 20 | ✅ |
| `chart_acct_seq_no` | 20 (DB; WSVC declares 10) | ✅ |
| `chart_acct_cd` | 20 | ✅ |

Server validates that `CHART_ACCT_INFM` row exists for `(BIZ_KEY, CHART_ACCT_SEQ_NO, DEL_YN='N')` ([pymn_0009_002_u002_act.jsp:38-56](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_002_u002_act.jsp)). On mismatch → `WBE0404`.

> **Note:** WSVC declares `CHART_ACCT_SEQ_NO length="10"` but the DEV column is `VARCHAR(20)` on `chart_acct_infm` (also `VARCHAR(20)` on `scdul_pymn_sumr_detl.chart_acct_seq_no`). The refactor `@Size(max=20)` uses the DB length, not the WSVC value — matches the Firm Banking Settings precedent ([firm-banking-settings-mapping.md §4](../../../references/migration/firm-banking-settings-mapping.md) where WSVC length anomalies are documented).

### 6.4 Dispatch / Execute (`POST .../{id}/dispatch`)

Mirrors `pymn_0009_001_c001` ([WSVC.pymn_0009_001_c001.xml:32-37](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_001_c001.xml) + [pymn_0009_001_c001_act.jsp:44-110](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_c001_act.jsp)):

| Wire field | Length | Required | Source |
|---|---|---|---|
| `scdul_pymn_no` (path) | 10 (DB) | ✅ | `validateRequired(SCDUL_PYMN_NO)` |
| `trnf_ccy_cd` (body) | 3 | ✅ | `validateRequired(TRNF_CCY_CD)` + `validateCcyCd(TRNF_CCY_CD)` |

Server-side preconditions:

- `SCDUL_PYMN_SUMR` row exists (else `WBE0404`).
- `TRNF_YN != "Y"` (else `WBE0404`).
- Linked `EXPT_RESOL_MSTR` exists and `APPR_TYPE_CD == "01"` (else `WBE0404`).
- Cut-off-time gate via `PymnHelper.validateTransferTiming` — see § 7.

### 6.5 Delete (`DELETE .../{id}`)

Mirrors `pymn_0009_001_d001` ([WSVC.pymn_0009_001_d001.xml:32-34](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_001_d001.xml) + [pymn_0009_001_d001_act.jsp:36-92](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_d001_act.jsp)):

- `scdul_pymn_no` required.
- Reject `WBE0404` when batch missing.
- Reject `WBE1063` when `APPR_SEQ_NO` is non-blank (batch already in / past approval).

### 6.6 List read filters (`GET /api/v1/payment/internal-transfer`)

Mirrors `pymn_0009_001_r001` ([WSVC.pymn_0009_001_r001.xml:32-39](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_001_r001.xml)):

| Wire param | Type | Required | Validation |
|---|---|---|---|
| `fromDt` | YYYYMMDD | ✅ | `validateRequired(FROM_DT)` |
| `toDt` | YYYYMMDD | ✅ | `validateRequired(TO_DT)` |
| `srchWd` | string | optional | free-text against TITL / DOC_NO |
| `apprType` (`APPR_TYPE_CD`) | string | optional | filter by approval status |
| `pageNo` | int | optional | default 1 |
| `pageSize` (legacy `PAGE_PER_CNT`) | int | optional | default 10 (matches Pagination shared rule §4) |
| `sortField` / `sortDirection` | string | optional | wire-side `FIELD_ID` per § 13a sort whitelist |

Same shape for `pymn_0010_001_r001` plus `trnfStts` (`TRNF_STTS`) and the Completed / In Progress toggle.

## 7. State machine & domain codes

Three legacy code columns drive the entire workflow.

### 7.1 `SCDUL_PYMN_TYPE_CD` (constant for Internal Transfer)

Always `"03"` for this feature ([pymn_0009_001_r001_act.jsp:61](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_r001_act.jsp); see `wabooks.enums.ScdulPymnTypeCd.INTERNAL.getValue()`). DB column is `VARCHAR(2)` NOT NULL. Other Payment modules use other codes (Vendor `01`, Expense `02`, …) but Internal Transfer is single-valued and the refactor doesn't expose this in the wire contract.

### 7.2 `APPR_STTS` / `APPR_DV` — approval flow

The DEV `expt_resol_mstr.appr_sts` is `VARCHAR(2)` NOT NULL and `appr_dv` is `VARCHAR(2)` NOT NULL. List IDO joins `SCDUL_PYMN_SUMR.APPR_SEQ_NO` → `EXPT_RESOL_MSTR.APPR_SEQ_NO` and surfaces a computed `APPR_STTS` column. Refactor surface — `apprSttsKey` i18n key per the Domain enum policy:

| Code | Meaning (placeholder — see OQ-1) | i18n key |
|---|---|---|
| `00` | Draft (not yet submitted) | `payment.appr_stts.draft` |
| `01` | Approved | `payment.appr_stts.approved` |
| `02` | Rejected | `payment.appr_stts.rejected` |
| `03` | In progress | `payment.appr_stts.in_progress` |
| `04` | Pending | `payment.appr_stts.pending` |
| `99` | Cancelled | `payment.appr_stts.cancelled` |

Codes inferred from `comm_0190_003_view.jsp` permission matrix labels. **OQ-1 in § 12** — codes must be verified against the live `wabooks.enums.ApprDv.java` / `ApprSts.java` before the backend MR; the table above is a working draft.

### 7.3 `TRNF_YN` / `PROC_STAT` — dispatch state

`TRNF_YN` on `SCDUL_PYMN_SUMR` is the boolean "was the batch dispatched" flag (`CHAR(1)` with default `'N'`) — `Y` after `pymn_0009_001_c001`, `N` while still a draft.

`PROC_STAT` is the **per-line** dispatch status on `TB_PAY_TARGET` (API path, `CHAR(1)` default `'9'`) / `TB_PAY_FILE_DATA` (File path — see `proc_rslt_div CHAR(1) default 'N'`). Values from `wabooks.enums.ProcStat`:

| Code | Meaning | Refactor i18n key |
|---|---|---|
| `00` | Pending | `payment.proc_stat.pending` |
| `10` | In progress | `payment.proc_stat.in_progress` |
| `20` | Success | `payment.proc_stat.success` |
| `30` | Failed | `payment.proc_stat.failed` |
| `40` | Cancelled | `payment.proc_stat.cancelled` |
| `50` | Transfer pending | `payment.proc_stat.transfer_pending` |

The `TRANSFER_PENDING` value (`"50"`) is the one written by `PymnHelper.pymn_0009_001_c001` at dispatch time ([PymnHelper.java:2537](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)).

> **Note:** DEV's `tb_pay_target.proc_stat` column is `CHAR(1)` with default `'9'`, but `wabooks.enums.ProcStat` uses two-digit codes (`"50"`, etc.). Need to verify whether the actual stored value is `'5'` (truncated) or whether the enum maps via a coercion layer — see OQ-1.

### 7.4 `ANT_DIV_CD` — transfer network

Drives the cut-off-time table (`validateTransferTiming`):

| Code | Network | Description |
|---|---|---|
| `N` / `NAPAS` | NAPAS | Real-time interbank in VN; runs 7 days a week |
| `S` / `SWIFT` | SWIFT | International wire; runs Mon-Fri |
| `C` / `CITAD` | CITAD | High-value interbank in VN; runs Mon-Fri |
| `INTER` | Internal (intra-bank) | Same `OUT_BANK_CD == TAR_BANK_CD`; treated as NAPAS in `validateTransferTiming` |

DB column on `scdul_pymn_sumr_detl.ant_div_cd` is `VARCHAR(5)`; on `tb_pay_target.ant_div_cd` is `CHAR(5)`; on `tb_pay_file_data.ant_div_cd` is `CHAR(1)`. The legacy code path `AntDivCd.fromValue(rows.getString("ANT_DIV_CD").trim())` is sensitive to trailing spaces on the `CHAR` columns ([PymnHelper.java:2528](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)). Refactor entity reads must `.trim()` before parsing.

### 7.5 Cut-off table (Woori + Shinhan only — others bypass)

Verified against [PymnHelper.java:3187-3216](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java):

| Bank | Network | Start | End (cut-off) |
|---|---|---|---|
| Shinhan | NAPAS | 00:05 | 23:45 |
| Woori | NAPAS | 00:15 | 23:55 |
| Shinhan | CITAD | 09:00 | 15:30 |
| Woori | CITAD | 08:30 | 17:40 |
| Shinhan | SWIFT | 09:00 | 22:00 |
| Woori | SWIFT | 08:30 | 19:10 |

Off-hours throws `WBW1167` (before start time) or `WBW1168` (past cut-off); weekend on non-NAPAS throws `WBW1162`. Shinhan past-cutoff is logged-only (warning, not blocking) per [PymnHelper.java:3169-3173](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java). Time zone is `gmtNowVanCenter()` — Vietnam (UTC+7).

### 7.6 Routing decision (which dispatch table)

From [PymnHelper.java:2509](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java):

```
if ( (TRNF_CCY_CD == "VND" AND NOT isShinhanUsdToVnd) OR isWooriUsdToVnd ) {
    → API path — insert TB_PAY_TARGET (one row per line)
} else {
    → File path — insert TB_PAY_FILE_MST + N TB_PAY_FILE_DATA
}

isWooriUsdToVnd   = OUT_BANK_CD == WOORI   AND TRNF_CCY_CD == "USD" AND WDRL_CCY_CD == USD AND DEPO_CCY_CD == "VND"
isShinhanUsdToVnd = OUT_BANK_CD == SHINHAN AND WDRL_CCY_CD == USD AND DEPO_CCY_CD == "VND"
```

`WDRL_CCY_CD` may be NULL on legacy rows (issue #42761); fallback resolves via `ACCT_MSTR.CCY_CD` ([PymnHelper.java:2475-2483](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)).

Refactor keeps the same routing rule verbatim; the constants `WOORI_BANK_CD` / `SHINHAN_BANK_CD` are loaded from configuration (`wecms.internal-transfer.routing.woori-bank-cd` / `.shinhan-bank-cd`) so test setups can override without code change.

## 8. Non-functional

### 8.1 Frontend route map

| Route | Component | Notes |
|---|---|---|
| `/payment/internal-transfer/scheduled-transfer` | `<ScheduledTransferPage>` composing `<PaymentScheduledBatchListPage>` | List, View Setting `payment-internal-transfer-scheduled` |
| `/payment/internal-transfer/scheduled-transfer/[id]` | `<ScheduledTransferDetailPage>` composing `<PaymentDetailPage>` | Detail + Request Approval + Transfer CTAs |
| `/payment/internal-transfer/transfer-result` | `<TransferResultPage>` composing `<PaymentResultBatchListPage>` | Read-only list, View Setting `payment-internal-transfer-result` |
| `/payment/internal-transfer/transfer-result/[id]` | `<TransferResultDetailPage>` composing `<ResultDetailView>` | Per-line `proc_stat` / `rs_cd` / `rs_msg` |

Reusable frontend primitives consumed (do not duplicate):

- `<PaymentScheduledBatchListPage>` / `<PaymentResultBatchListPage>` / `<PaymentDetailPage>` / `<ResultDetailView>` (in `components/payment/shared/`).
- `<ExcelUploadDialog>` (in `components/shared/excel-upload/`).
- `<PageActionButtons>`, `<Pagination>`, `<SearchInput>`, `<AppShell>`, `<Header>`, `<Sidebar>`, `<PageHeader>` (in `components/layout/` and `components/shared/`).
- View Setting popover via `SCREEN_IDS` registry — extend [lib/viewSettingScreens.ts](../../../../../wecms-web/lib/viewSettingScreens.ts) with four constants:
  - `SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_SCHEDULED = 'payment-internal-transfer-scheduled'`
  - `SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_RESULT = 'payment-internal-transfer-result'`
  - `SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_SCHEDULED_DETAIL = 'payment-internal-transfer-scheduled-detail'`
  - `SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_RESULT_DETAIL = 'payment-internal-transfer-result-detail'`

### 8.2 Reusable backend primitives consumed (do not duplicate)

- `TenantContext` — every query scopes on `currentBizKey()`.
- `common/web` sort whitelist enum per [api CLAUDE.md § "Common table sort"](../../../../CLAUDE.md). Four whitelists: `InternalTransferListSortField`, `InternalTransferDetailSortField`, `InternalTransferResultListSortField`, `InternalTransferResultDetailSortField`.
- `common/export` Excel pipeline + per-screen `ExportRowProvider` ([api CLAUDE.md § Excel export](../../../../CLAUDE.md)) — four providers, one per list/detail screen.
- `domain/setting/screenoption` (View Setting) backend — seed only, no controller change.
- `common/enums` — extend `CcyCd`, `AntDivCd` if needed; reuse existing enums.
- `AuditLogService.append(...)` — never open-code the 3-language `AUDIT_LOGS_INFM` insert.

### 8.3 Internationalisation

Locale entries per `wecms-web` rules (EN + KO + VI only — KM deferred):

| Key prefix | Coverage |
|---|---|
| `internal_transfer.title` | Scheduled Transfer page title |
| `internal_transfer.detail.title` | Detail page title |
| `internal_transfer.result.title` | Transfer Result page title |
| `internal_transfer.result_detail.title` | Result Detail page title |
| `internal_transfer.column.*` | Table column headers (date_time, file_name, no_of_transactions, total_transfer_amount, approval_status, requestor, approver, approval_ref, approval_title, …) |
| `internal_transfer.action.*` | Toolbar / row CTAs (request_approval, transfer, delete, download, retry, …) |
| `internal_transfer.dialog.*` | Upload / Save Draft / Transfer confirmation dialogs |
| `internal_transfer.error.*` | Per-error mapping (cut-off, weekend, approval-locked, etc.) |
| `payment.appr_stts.*` | Reused from approval-box feature (do not duplicate per module) |
| `payment.proc_stat.*` | Reused across all Payment modules |

Backend `messages.properties` strings: `WBExxxx` codes only — labels stay on the wire as the i18n **key** alongside the legacy code (per [wecms-api/CLAUDE.md § Domain enum keys](../../../../CLAUDE.md)).

### 8.4 Time zone

All bank cut-off calculations run in **Vietnam local time (UTC+7)** via the existing helper that mirrors `DateTimeUtils.gmtNowVanCenter()` — refactor implementation lives in `common/time/VietnamClock` (new helper). Wire datetimes are stored on disk as 14-char `VARCHAR` (`reg_dtm`, `mod_dtm`, `trnf_dtm`, `proc_dttm`, `rslt_dttm` are all `VARCHAR(14)` in the legacy schema — `yyyyMMddHHmmss`); UTC-vs-Vietnam conversion happens at the entity boundary.

### 8.5 Currency

`CCY_CD` values are constrained to the `wabooks.enums.CcyCd` set — primarily `VND` and `USD` for Internal Transfer in production. Amounts use `BigDecimal` end-to-end; on the wire they are raw decimal strings. **`SCDUL_PYMN_SUMR.total_tran_amt` and `SCDUL_PYMN_SUMR_DETL.tran_amt` are `NUMERIC(20, 6)` (6 decimal places); `TB_PAY_TARGET.tran_amt` and `TB_PAY_FILE_DATA.tran_amt` are `NUMERIC(23, 2)` (2 decimal places)** — entity scale must match; the dispatch service rounds 6→2.

The Excel export pipeline formats via [`AmountFormatter.forDisplay`](../../../../src/main/java/com/kosign/wecms/domain/banking/service/AmountFormatter.java) per [api CLAUDE.md § Excel export](../../../../CLAUDE.md).

### 8.6 File upload limits

| Constraint | Value | Source |
|---|---|---|
| Max Excel upload size | 5 MB | New config `wecms.internal-transfer.upload.max-bytes` (OQ-2 — JEX side has no documented hard cap; verify against tomcat / nginx / `multipart.max-file-size`) |
| Allowed extensions | `.xlsx`, `.xls` | Mirrors JEX `JexUtils.readFile(input, "FILE_DATA")` MIME accept list |
| Storage location | `wecms.internal-transfer.storage-root` (default `C:/WECMS PROJECT/File Data/internal-transfer` on Windows, `/var/wecms/file-data/internal-transfer` on POSIX) | Mirrors ERP Transaction History storage root pattern |
| Retention | Forever (mirrors `ERP_TRNS_HSTR` — delete only happens via the explicit batch delete flow which emits the audit row) | — |
| File metadata storage | Three columns: `org_file_nm VARCHAR(500)`, `save_file_nm VARCHAR(500)`, `file_size VARCHAR(100)` on `SCDUL_PYMN_SUMR` | DEV `information_schema` (verified 2026-05-13) |

### 8.7 Audit logging

Every mutating endpoint writes one row into `AUDIT_LOGS_INFM` via `AuditLogService.append(...)`. DEV column layout (verified 2026-05-13): PK `(biz_key, audit_logs_seq_no, lang_cd)`; `wsvc_id VARCHAR(20) NOT NULL`; `ref_no` / `ref_no1` `VARCHAR(50) NOT NULL`; `ref_no2..5 VARCHAR(50)` nullable; `audit_logs_actv_dtl text NOT NULL`; `audit_logs_actv VARCHAR(50)` nullable.

| Op | WSVC_ID | audit_logs_actv | ref_no / ref_no1 / ref_no2 | Payload (`audit_logs_actv_dtl`, JSON) |
|---|---|---|---|---|
| Save draft | `internal-transfer` | `create` | `scdul_pymn_no` / — / — | `{ titl, bank_cd, acct_no, line_count, total_amount }` |
| Update reporting account | `internal-transfer` | `update` | `scdul_pymn_no` / `scdul_pymn_detl_no` / — | `{ before_chart_acct_cd, after_chart_acct_cd }` |
| Dispatch (Transfer) | `internal-transfer` | `dispatch` | `scdul_pymn_no` / `trnf_ccy_cd` / `routing` (`api`/`file`) | `{ line_count, total_amount }` |
| Delete draft | `internal-transfer` | `delete` | `scdul_pymn_no` / — / — | `{}` |

`WSVC_ID = 'internal-transfer'` (slug, 17 chars; fits `VARCHAR(20)`) per workspace CLAUDE.md identifier rule and [wecms-api/CLAUDE.md § Refactor identifier naming](../../../../CLAUDE.md).

## 9. Error codes

All errors use the standard `BusinessException("WBExxxx")` flow → global handler → `{ status: "error", code, message, traceId }`. New codes must land in `messages.properties` before the throw.

| Code | Trigger | Thrown by |
|---|---|---|
| `WBE0404` | Resource missing (SCDUL_PYMN_SUMR, EXPT_RESOL_MSTR, CHART_ACCT_INFM row) | Dispatch, Reporting Account update, Delete |
| `WBE0404` | `TRNF_YN == 'Y'` on dispatch (already dispatched) | Dispatch service |
| `WBE0404` | `EXPT_RESOL_MSTR.APPR_TYPE_CD != '01'` (not approved) | Dispatch service |
| `WBE1063` | `APPR_SEQ_NO` set on delete | Delete service |
| `WBW1162` | Weekend dispatch attempt on CITAD or SWIFT | `validateTransferTiming` |
| `WBW1167` | Before bank's start time | `validateTransferTiming` |
| `WBW1168` | Past bank's cut-off time (non-Shinhan banks only) | `validateTransferTiming` |
| `WBE2015` (precedent reused from ERP Transaction History) | Stored Excel file missing on disk during download | File download endpoint |

Refactor adds no new `WBExxxx` codes — all reused from JEX. Net-new business rules use the existing error-code vocabulary.

## 10. Out of scope (R&D)

- Real bank dispatch — TB_PAY_TARGET / TB_PAY_FILE_MST rows are written but never consumed by a downstream system.
- Approval workflow internals — consumed from the parallel `payment/approval-box` feature (see OQ-5).
- The `pymn_0009_003_r002` and `pymn_0010_002_r002` Excel-download legacy endpoints (both `actUseYn=N` view-only templates) are **NOT** ported as separate endpoints — Excel export goes through the shared XLSX pipeline (`POST /api/v1/payment/internal-transfer/{id}/export` and `POST /api/v1/payment/internal-transfer/results/{id}/export`) per [wecms-api/CLAUDE.md § Excel export](../../../../CLAUDE.md).
- Cards transfer, Overseas transfer, and Vendor / Expense / Payroll scheduled-payment variants — separate feature MRs per the Payment module split.
- HAENGSUNG `PRDT_TYPE='H'` per-product overrides — port lands with the HAENGSUNG payment-table-2 layout feature, not here.
- ERP status update on dispatch (`ErpStatusUpdateHelper.updateErpStatus` — legacy `pymn_0009_001_c001_act.jsp:100-103` is commented out today). Refactor does not port.

## 11. Cross-tree traceability

| Surface | Backend (`wecms-api`) | Frontend (`wecms-web`) | DB seed |
|---|---|---|---|
| REST URI | `/api/v1/payment/internal-transfer` | `lib/api/internalTransfer.ts` (to add) | — |
| Java package | `com.kosign.wecms.domain.payment.internaltransfer` | — | — |
| Frontend feature folder | — | `components/payment/internal-transfer/` (already scaffolded) | — |
| View Setting `WEB_SVC_ID` | `payment-internal-transfer-scheduled`, `-result`, `-scheduled-detail`, `-result-detail` | `SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_*` | `SYS_SCR_OPTION_WECMS` seed in Flyway |
| Audit `WSVC_ID` | `internal-transfer` | n/a (token-bound) | `AUDIT_LOGS_INFM` row per write |
| i18n prefix | `internal_transfer.*` (frontend), `payment.appr_stts.*` / `payment.proc_stat.*` (shared) | `locales/en.json`, `locales/ko.json`, `locales/vi.json` | — |

## 12. Risk register

Items requiring PO (Mr. Jung) / lead (Sonny) confirmation. Each item flagged **BLOCKER**, **CLARIFY**, or **NOTE**.

- **BLOCKER-A** (was OQ-3) — `SCDUL_PYMN_NO` key generation. ⚠️ **The 2026-05-14 resolution below is superseded — re-verified against the tree 2026-08-17.**
  - **Current state:** the column is `scdul_pymn_no varchar(10) NOT NULL` in [`V86__scdul_pymn_sumr.sql`](../../../../src/main/resources/db/migration/V86__scdul_pymn_sumr.sql) — **DEV-parity width, not widened**. Key generation now routes through the `wb_get_next_val` funnel (`LegacyFunctionRepository.nextScdulPymnNo` → branch #36 in [`V140__wb_get_next_val.sql`](../../../../src/main/resources/db/migration/V140__wb_get_next_val.sql)), which returns a **per-tenant plain-int `MAX+1`**, not a date-prefixed key.
  - **Historical (2026-05-14, no longer true):** `V131__internal_transfer_schema.sql` widened the column to `VARCHAR(20)` to fit a Java-side `InternalTransferKeyGenerator` emitting 14 chars (`YYYYMMDD<seq:6>`) per (tenant, Vietnam-local day), with `REQUIRES_NEW` + `INSERT … ON CONFLICT DO UPDATE … RETURNING` concurrency. Both the widening and the date-prefixed format were dropped — the widening by the [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), the format when the key was funneled.
- **BLOCKER-B** — `proc_stat` column width. DEV `tb_pay_target.proc_stat` is `CHAR(1)` with default `'9'`, but `wabooks.enums.ProcStat` uses two-character codes (`"50"` = TRANSFER_PENDING). Refactor `TB_PAY_TARGET` ships in [V109__tb_pay_target.sql](../../../../src/main/resources/db/migration/V109__tb_pay_target.sql) / `V189__payment_dispatch_tables_schema.sql`; confirm the as-shipped column width on the next parity sweep. Internal Transfer's Phase A dispatch service stamps `TRNF_YN='Y'` + audit row but does NOT yet insert TB_PAY_TARGET rows (deferred per backend §15.6), so the runtime impact is zero today. *(historical — split across the current per-table dispatch migrations `V101`-`V110` (`tb_firm_*` / `tb_pay_*`))*
- **CLARIFY-1** (was OQ-1) — exact `APPR_STTS` code → label mapping. The matrix labels ("Draft", "Pending", "In Progress", "Approved", "Rejected") need a verified `wabooks.enums.ApprSts.java getCode()` value per entry. § 7.2's table is a placeholder.
- **CLARIFY-2** (was OQ-2) — Excel upload size cap. JEX defers to servlet container; refactor sets `multipart.max-file-size`. Need PO confirmation (5 MB / 10 MB / other).
- **CLARIFY-3** (was OQ-4) — **WSVC scope deviation.** Verified file listing (2026-05-13):
  - `pymn_0009_002`: only `r001` (parse), `c001` (save), `u002` (update reporting account). **No `c002`** in the source tree. The original brief's hypothesis that there is a two-step parse-then-confirm RPC is **wrong** — frontend holds parsed rows in memory and submits everything in one `c001` call.
  - `pymn_0009_003`: only `r001` act, plus `r002` view-only excel template (`actUseYn=N`).
  - `pymn_0010_002`: only `r001` act, plus `r002` view-only excel template (`actUseYn=N`).
  Migration mapping § 1 reflects the actual files. Confirm before Prompt 2.
- **CLARIFY-4** (was OQ-5) — Approval workflow contract. Internal Transfer's "Request Approval" CTA submits to the approval engine; we need the wire shape Approval Box exposes. If `payment/approval-box` is not ready when this MR opens, the refactor stubs the CTA behind a feature flag.
- **CLARIFY-5** (was OQ-6) — Cut-off-time table source of truth. Hard-coded in `PymnHelper.getCutoffConfig`. Recommendation: configuration-driven via `wecms.internal-transfer.cutoff.<bank>.<network>` properties so QA can flex it for local tests. **No new `BANK_CUTOFF_INFM` table** — that would violate PROD Active Table List rule.
- **CLARIFY-6** (was OQ-7) — `AntDivCd` "INTER" normalisation. Legacy auto-sets `ANT_DIV_CD` to `INTER` when `OUT_BANK_CD == TAR_BANK_CD` ([PymnHelper.java:2525-2528](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)). Refactor preserves the same coercion in the dispatch path (yes — confirm parity).
- **CLARIFY-7** (was OQ-8) — Multi-currency totals on Transfer Result. WSVC `TOT_REC` ships `TOTL_AMT` per `CCY_CD`. Frontend `<PaymentResultBatchListPage>` rule says **every** Payment Result variant except Internal Transfer renders the totals footer; Internal Transfer is the exception (Figma 1:11730 shows no totals row). Confirm the rule still holds for the refactor or whether we converge.
- **NOTE-1** — `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` is the actual conn table name (note `SCDU_` not `SCDUL_`). Refactor entity/repo names must use the actual DB name verbatim; the corresponding refactor Java entity `ScduPymnSumrDetlPayTargetConn` would be unusually-spelled, so a documentation comment is needed to explain the typo-by-convention.
- **NOTE-2** — There is **no separate file-path conn table** in DEV. File-path linkage is via `SCDUL_PYMN_SUMR_DETL.pay_file_seq_no + pay_seq` direct columns, joined to `TB_PAY_FILE_MST.file_seq_no` and `TB_PAY_FILE_DATA(file_seq_no, seq)`. Migration mapping doc + schema doc both updated to reflect this.
- **NOTE-3** — `appr_seq_no` literal-tab oddity in `WSVC.pymn_0009_001_r001.xml:68` (the WSVC `id="APPR_SEQ_NO\t"` includes a tab character). Refactor cleans this up — wire field is `appr_seq_no` exactly, no trailing whitespace. JEX consumers will continue to see the legacy field via the JEX endpoint.

---

## 13. Sibling parity — where Vendor / Expense / Payroll diverge

Internal Transfer is the **locked baseline** for the Payment module. The three sibling sub-modules (`vendor-payment`, `expense-payment`, `payroll`) port the same shape — backend package layout under `domain/payment/<sibling>/{controller, service, export}`, frontend folder under `components/payment/<sibling>/`, four routes (Scheduled list + detail + Result list + detail), and a per-sibling upload dialog wrapping the shared [`<PaymentExcelUploadDialog>`](../../../../../wecms-web/components/payment/shared/PaymentExcelUploadDialog.tsx). See [payment hub README](../README.md) for the full sibling index + the "how to add a new payment sub-module" checklist.

Verified divergences from this baseline (sourced from sibling controllers / services / Excel parsers / Figma handoffs):

| Concern | Internal Transfer | Vendor | Expense | Payroll | Citation |
|---|---|---|---|---|---|
| `SCDUL_PYMN_TYPE_CD` discriminator | `"03"` (INTERNAL) | `"01"` (VENDOR) | `"02"` (EXPENSE) | `"00"` (PAYROLL) | [ScdulPymnTypeCd.java:1-46](../../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/ScdulPymnTypeCd.java) (refactor enum) + [WECMS DEV/WECMS/src/wabooks/enums/ScdulPymnTypeCd.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/enums/ScdulPymnTypeCd.java) |
| Audit `WSVC_ID` | `internal-transfer` | `vendor-payment` | `expense-payment` | `payroll-payment` | [InternalTransferService.java:113](../../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferService.java#L113) and sibling services (each declares its own `WSVC_ID` constant) |
| Backend package | `payment.internaltransfer` | `payment.vendorpayment` | `payment.expensepayment` | `payment.payrollpayment` | (filesystem) |
| Frontend folder | `components/payment/internal-transfer` | `components/payment/vendor-payment` | `components/payment/expense-payment` | `components/payment/payroll` | (filesystem) |
| Route base | `/payment/internal-transfer/{scheduled-transfer,transfer-result}` | `/payment/vendor-payment/{scheduled-payment,payment-result}` | `/payment/expense-payment/{scheduled-payment,payment-result}` | `/payment/payroll/{scheduled-payment,payment-result}` | (filesystem) |
| List endpoint route | `GET /api/v1/payment/internal-transfer` | `GET /api/v1/payment/vendor-payment` | `GET /api/v1/payment/expense-payment` | `GET /api/v1/payment/payroll-payment` | sibling controllers |
| Result route shape | dedicated `TransferResultController` at `/api/v1/payment/internal-transfer/results` | results endpoints folded into the main `VendorPaymentController` (e.g. `GET /results`, `GET /results/{id}`, `POST /results/export`) | same as Vendor (single controller carries both flows) | same as Vendor (single controller carries both flows) | [InternalTransferController.java](../../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java) vs sibling controllers |
| Upload-dialog column set | 8 cols — recipient acct, name, bank, network, amount, description, doc no, note | recipient bank account + vendor code | expense account + description + reporting account | employee id + salary breakdown (5-col layout per Figma) | each sibling's `<*UploadDialog>` |
| Result list footer | **`paginationOnly`** — no totals footer (Figma 1:11730 — only Result variant in the module that omits it) | totals footer rendered | totals footer rendered | totals footer rendered | [`<PaymentResultBatchListPage>`](../../../../../wecms-web/components/payment/shared/PaymentResultBatchListPage.tsx) prop `totals` |
| Detail page transaction table | 11-column canonical `resultDetailColumns` from [detailColumns.tsx](../../../../../wecms-web/components/payment/shared/detailColumns.tsx) | per-Vendor column set | per-Expense column set | **5-column** payroll-specific layout (per Figma — narrower table than the canonical 11) | Figma + per-sibling Detail page components |
| Result Detail Excel export | `POST /api/v1/payment/internal-transfer/results/export` via [`InternalTransferResultExportProvider`](../../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/export) | `POST /api/v1/payment/vendor-payment/results/export` via [`VendorPaymentResultExportProvider`](../../../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/export) | `POST /api/v1/payment/expense-payment/results/export` via [`ExpensePaymentResultExportProvider`](../../../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/export) | `POST /api/v1/payment/payroll-payment/results/export` via [`PayrollPaymentResultExportProvider`](../../../../src/main/java/com/kosign/wecms/domain/payment/payrollpayment/export) | per-sibling `export/` folder |

The shared infrastructure under [`payment/dispatch/`](../../../../src/main/java/com/kosign/wecms/domain/payment/dispatch), [`payment/validation/`](../../../../src/main/java/com/kosign/wecms/domain/payment/validation), [`payment/approvalbox/`](../../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox), and [`payment/export/`](../../../../src/main/java/com/kosign/wecms/domain/payment/export) is **identical across all four siblings** — see [payment hub README](../README.md) §"Shared backend infrastructure index". Per-feature stubs for the three siblings live at [vendor-payment/README.md](../vendor-payment/README.md), [expense-payment/README.md](../expense-payment/README.md), [payroll/README.md](../payroll/README.md).

> Out of scope for this section: **Overseas Payment** (`payment/overseas/` + `app/(dashboard)/payment/overseas-payment/`) lives in a separate domain that uses the `OVRS_PYMN_MSTR` / `OVRS_PYMN_SUMR` / `OVRS_PYMN_DTLS` table family — NOT `SCDUL_PYMN_SUMR` — and has no `SCDUL_PYMN_TYPE_CD` discriminator ([pymn_0007_001_r001_act.jsp:198](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_001_r001_act.jsp#L198) routes to `getDynamicOvrs()` instead). Cross-linked from [payment hub README](../README.md) but not documented as a sibling here.

---

Last verified: 2026-05-21

End of feature spec.

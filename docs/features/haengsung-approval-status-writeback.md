# HAENGSUNG Approval-Status Write-back — Backend Feature Spec (Follow-on / Phase 3)

**Slug:** `haengsung-approval-status-writeback` · **Domain:** Payment · **Status:** Deferred follow-on (Phase 3 of the HAENGSUNG ERP integration) · **Product type:** `H` only · **Last updated:** 2026-06-28

> **Docs only — no Java written.** This is a **follow-on** to [haengsung-payment-refresh.md](haengsung-payment-refresh.md) (Phase 1 = import only). This flow is **not part of Phase 1** and is **not a Phase 1 blocker**. Legacy source of truth: `WECMS DEV/WECMS/src/wabooks/helper/ErpStatusUpdateHelper.java`.

## 1. Purpose

When a HAENGSUNG payment batch (imported via Refresh) moves through the **WeCMS approval lifecycle**, this flow writes the corresponding **status back to the Haengsung Oracle ERP** so the ERP can see the batch is pending / approved / rejected / in processing. It is the second of the three HAENGSUNG↔Oracle write-back surfaces ([refresh spec §20](haengsung-payment-refresh.md#20-which-batch-imports-which-writes-status-back)).

Direction: **WeCMS → Oracle**. It does **not** import data and does **not** create WeCMS rows.

## 2. Trigger point

Invoked from the **approval action flow** — when a user (or the approval engine) sets a payment batch to pending / approve / reject / processing. In legacy this is called from the approval `_act.jsp` path via `ErpStatusUpdateHelper.updateErpStatus(util, idoCon, loginUser, scdulPymnNoOrOvrsPymnNo, wecmsStatus, pymnType)` where `pymnType ∈ {SCDUL, OVRS}`. It is **not a batch** and **not the import** — it fires synchronously on the approval state change.

## 3. WeCMS tables read (keys only)

It reads only to resolve the Oracle `ERP_FILE_*` keys for the batch; it writes nothing to WeCMS:

- Scheduled: `SCDUL_PYMN_SUMR` (legacy IDO `SCDUL_PYMN_SUMR_R017`) + `SCDUL_PYMN_SUMR_DETL` (`SCDUL_PYMN_SUMR_DETL_R005`).
- Overseas: `OVRS_PYMN_SUMR` (`OVRS_PYMN_SUMR_R011`) + `OVRS_PYMN_SUMR_DETL` (`OVRS_PYMN_SUMR_DETL_R005`).

The read returns `ERP_FILE_DATE`, `ERP_FILE_NUM` (master) and `ERP_FILE_SEQ` (detail). If those are blank (batch not from ERP), the helper returns without writing — non-HAENGSUNG / non-ERP batches are skipped.

## 4. Oracle tables written

- `TB_TRNS_MAST` (legacy IDO `TB_TRNS_MAST_U000`) — keyed by `FILE_DATE` + `FILE_NUM`.
- `TB_TRNS_DETAIL` (`TB_TRNS_DETAIL_U000`) — keyed by `FILE_DATE` + `FILE_NUM` + `FILE_SEQ`, **only on rejection**.

## 5. Status mapping (WeCMS → ERP `LAST_STATUS`)

| WeCMS status | ERP `LAST_STATUS` |
|---|---|
| `PENDING` | `10` |
| `APPROVED` | `20` |
| `REJECTED` | `32` |
| `PROCESSING` | `51` |
| (any other) | no-op (null map → return) |

## 6. Columns updated

**`TB_TRNS_MAST`** (always): `LAST_STATUS` = mapped code. **On `REJECTED` (`32`) only**, additionally: `IHB_UDT_USR='WEBCASH'`, `IHB_UDT_DT=now(VN)`, `END_GB='1'`, `END_DATETIME=now(VN)`, `BUL_CNT=TOT_TRNS_CNT`, `BUL_AMT=TOTAL_TRAN_AMT` (the rejected batch is closed as "unable").

**`TB_TRNS_DETAIL`** (only when `REJECTED`): `PROC_STS='XXXX'`, `END_GB='3'` (송금의뢰취소 / request-cancelled), `IHB_UDT_DT=now(VN)`, `TR_DATE=today`.

> Note the lifecycle boundary: this flow writes `LAST_STATUS` (10/20/32/51) and reject-time `PROC_STS='XXXX'` — columns the **import never touches**. Confirmed against the status-flow workbook ([refresh spec §15](haengsung-payment-refresh.md#15-status-flow-confirmed-by-the-status-workbook)).

## 7. Why this is NOT Phase 1 Refresh import

- The import creates a **draft** with `LAST_STATUS` left at `'00'`; approval is a **separate, later** lifecycle stage that doesn't exist at import time.
- It writes **different columns** (`LAST_STATUS`/`PROC_STS`), not the receive-flag columns the import owns.
- The import does not call it and does not depend on it (re-Refresh stays idempotent via DETAIL `end_gb='0'` + upsert dedup — [refresh spec §25.3](haengsung-payment-refresh.md#253-what-breaks-if-phase-1-ships-without-them)).

**What breaks if omitted (Phase 1):** Oracle `LAST_STATUS` stays `'00'` after a WeCMS approve/reject/processing — the ERP can't see approval progress, and a rejected batch isn't marked `PROC_STS='XXXX'`/`END_GB='3'` in Oracle. WeCMS approval itself works; only Oracle visibility is stale. Acceptable for Phase 1 R&D.

## 8. When to implement

**Phase 3 — only after PO approval**, and ideally once the refactor's HAENGSUNG payment **approval flow** is wired end-to-end (so there is an approval state change to hook). Implement as a clean Spring service triggered from the approval action, reusing the same Phase 1 Oracle datasource + write path; do not re-port `_act.jsp`/`JexData` patterns.

## 9. Cross-references
- Parent / Phase 1: [haengsung-payment-refresh.md](haengsung-payment-refresh.md) (§9A phase plan, §20–§23 write-back surfaces).
- Sibling follow-ons: [haengsung-dispatch-result-writeback.md](haengsung-dispatch-result-writeback.md).
- Legacy source: `WECMS DEV/WECMS/src/wabooks/helper/ErpStatusUpdateHelper.java`.

---

## 10. Final Legacy + Refactor Parity Verification (2026-06-28)

Verification of **both** approval entry points — (1) Create / Request Approval, (2) Approval Details popup Approve / Reject — to confirm the HAENGSUNG Oracle status write-back fires for **every** entry point, not just one popup. Verified against legacy source, not assumptions.

### 10.1 Legacy flow (source of truth)

`ErpStatusUpdateHelper.updateErpStatus(util, idoCon, loginUser, scdulPymnNo|ovrsPymnNo, wecmsStatus, pymnType)`:
- **HAENGSUNG-gated** — `getBusinessInfo` selects `WHERE biz_key=? AND prdt_type='H' AND del_yn='N'`; returns null (skips) otherwise (`ErpStatusUpdateHelper.java:117`).
- **Status map** (`mapWecmsStatusToErpCode`, `:98-111`): `PENDING→10`, `APPROVED→20`, `REJECTED→32`, `PROCESSING→51`, else null → skip.
- **Resolves the batch** by `pymnType` (`SCDUL`→`SCDUL_PYMN_SUMR_R017` / `OVRS`→`OVRS_PYMN_SUMR_R011`); skips when `ERP_FILE_DATE`/`ERP_FILE_NUM` are blank (non-ERP batch).
- **Master** (`updateTbTrnsMastStatus`, `:176`): `LAST_STATUS = <code>`; when `'32'` also `IHB_UDT_USR='WEBCASH'`, `IHB_UDT_DT=now(VN)`, `END_GB='1'`, `END_DATETIME`, `BUL_CNT=TOT_TRNS_CNT`, `BUL_AMT=TOTAL_TRAN_AMT`.
- **Detail on REJECTED** (`updateTbTrnsDetailProcStatus(...,"XXXX","3")`, `:255`): `PROC_STS='XXXX'`, `END_GB='3'`, `IHB_UDT_DT=now(VN)`, `TR_DATE=today`.

**Callers (which action emits which status):**

| Legacy action | Status | Entry point |
|---|---|---|
| `comm_0192_001_c005_act.jsp:724` | `PENDING` (10) | **Request / Create Approval** — shared approval-create action (all payment types via `pymnType`) |
| `bltb_0001_003_u001_act.jsp:315` | `APPROVED` (20) | **Approve** — shared approval-inbox action |
| `bltb_0001_003_u001_act.jsp:229,357` | `REJECTED` (32) | **Reject** — shared approval-inbox action |
| `bltb_0001_003_u001_act.jsp:227` | `PENDING` (10) | re-request / resubmit |
| `pymn_0001/0003/0005/0007/0009_001_c001_act.jsp` | `PROCESSING` (51) | **Dispatch / Transfer** — per-module |

**Shared across all payment types:** Vendor (`pymn_0003`), Expense, Payroll (`pymn_0001/0005`), Overseas (`pymn_0007`, `OVRS`), Internal Transfer (`pymn_0009`) all funnel through the **one** `ErpStatusUpdateHelper`; approval Request/Approve/Reject are **centralized** in `comm_0192` + `bltb_0001_003` (not per-module). Dispatch (`PROCESSING`) is per-module.

### 10.2 Refactor flow

- **Event:** `PaymentApprovalStatusChangedEvent(bizKey, apprSeqNo, status)`; enum `PaymentApprovalStatus{PENDING,APPROVED,REJECTED,PROCESSING}`.
- **Listener:** `HaengsungApprovalStatusWriteBackService.onApprovalStatusChanged` — `@TransactionalEventListener(AFTER_COMMIT)`, `@ConditionalOnProperty("haengsung.oracle.enabled"="true")` (Oracle off → no bean → skipped), H-only (`prdt_type='H' && del_yn!='Y'`), non-ERP skip. Mapping `lastStatusCode` 10/20/32/51 is byte-identical to legacy `mapWecmsStatusToErpCode`.
- **Oracle SQL** (`HaengsungErpOracleRepository`): `updateMasterApprovalStatus` (`LAST_STATUS=?`), `updateMasterRejected` (`LAST_STATUS='32'`+close fields), `markDetailRejected` (`PROC_STS='XXXX'`,`END_GB='3'`,`TR_DATE`) — all match legacy.

**Publish sites (which refactor flow emits which status):**

| Refactor flow | Publisher | Status | Coverage |
|---|---|---|---|
| Approve (popup `approveApprovalBoxRow` → `ApprovalBoxController.approve`; **and** per-module `actOnApproval`) | `ApprovalBoxService.approve():416-418` (on final `'01'`) | `APPROVED` (20) | ✅ all types |
| Reject (popup `rejectApprovalBoxRow`; and per-module `actOnApproval`) | `ApprovalBoxService.reject():460-462` | `REJECTED` (32) | ✅ all types |
| Dispatch | per-module `*PaymentService` (Vendor:1111, Expense:1123, Payroll:1042, Overseas:1320) | `PROCESSING` (51) | ✅ |
| Request / Create Approval | `ApprovalBoxFanoutService.fanout():235-237` | `PENDING` (10) | ⚠️ **only `CardTransactionsService` calls `fanout(...)`** |

### 10.3 Create / Request Approval verification

- **Frontend:** the shared `RequestApprovalDialog` submits per-module — `requestVendorApproval` / `requestExpenseApproval` / `requestPayrollApproval` / `requestOverseasApproval` / `requestInternalTransferApproval` → `POST /api/v1/payment/{module}/{id}/request-approval`. Uses the existing shared request-approval API; **no separate HAENGSUNG frontend logic needed.** ✅
- **Backend:** each per-module `requestApproval(...)` builds `EXPT_RESOL_MSTR` + approvers and calls `ApprovalBoxFanoutService.writeResolutionDetail` + `writeApprovalLineSnapshot` — but does **NOT** call `fanout(...)` and does **NOT** publish `PaymentApprovalStatusChangedEvent(PENDING)`. The only `PaymentApprovalStatus.PENDING` literal in the codebase is inside `fanout()`, whose only caller is `CardTransactionsService:1605`.
- **Result:** for HAENGSUNG's actual payment types (Vendor / Expense / Payroll / Overseas, + Internal), **the Request-Approval `PENDING→LAST_STATUS='10'` write-back is NOT emitted** — Oracle goes `00 → 20/32/51`, skipping `10`. Legacy `comm_0192_001_c005` writes `10` for all types. **→ MISMATCH (see 10.5).**

### 10.4 Approval Details popup verification

- **Frontend:** `CommonApprovalDetailsDialog` calls `approveApprovalBoxRow(apprSeqNo)` / `rejectApprovalBoxRow(apprSeqNo)` → `POST /api/v1/payment/approval-box/{apprSeqNo}/{approve|reject}` → `ApprovalBoxController` → `ApprovalBoxService.approve/reject`. Shared approval API; **no separate HAENGSUNG frontend logic needed.** ✅
- **Backend:** `approve()`/`reject()` are `@Transactional` and publish `APPROVED`/`REJECTED` within the tx (APPROVED only on final `'01'`); the `AFTER_COMMIT` listener writes Oracle `LAST_STATUS='20'`/`'32'` (+reject detail `'XXXX'`/`'3'`). Per-module `actOnApproval` also routes through `ApprovalBoxService.approve/reject`, so Approve/Reject are covered from **both** entry points. **→ FULL PARITY.** ✅

### 10.5 Match / mismatch table

| Mapping | Legacy emits from | Refactor emits from | LAST_STATUS | Match? |
|---|---|---|---|---|
| Request / Pending → `10` | `comm_0192_001_c005` (all types) | `fanout()` — **CardTransactions only** | `10` | ❌ **MISSING for Vendor/Expense/Payroll/Overseas/Internal** |
| Approved → `20` | `bltb_0001_003_u001:315` | `ApprovalBoxService.approve` | `20` | ✅ |
| Rejected → `32` | `bltb_0001_003_u001:229,357` | `ApprovalBoxService.reject` | `32` | ✅ |
| Rejected detail `PROC_STS='XXXX'`,`END_GB='3'` | `updateTbTrnsDetailProcStatus` | `markDetailRejected` | — | ✅ |
| Processing/Dispatch → `51` | `pymn_*_c001` | per-module `*PaymentService` dispatch | `51` | ✅ |
| HAENGSUNG-only gate | `prdt_type='H' && del_yn='N'` | `isHaengsung` (same) | — | ✅ |
| AFTER-commit / no-XA, non-ERP skip | (synchronous in-tx) | `@TransactionalEventListener(AFTER_COMMIT)` + skip | — | ✅ (equivalent) |

### 10.6 Is an API change needed?

**Yes — one backend gap (Medium).** The Create / Request Approval flow must publish `PaymentApprovalStatusChangedEvent(..., PENDING)` for the four core HAENGSUNG payment modules (+Internal Transfer), mirroring what `CardTransactionsService`/`fanout()` already does. Recommended minimal fix (per module, no contract change): at the end of each `*PaymentService.requestApproval(...)` (Vendor / Expense / Payroll / Overseas / Internal), publish the `PENDING` event with `(bizKey, apprSeqNo)` — or route those `requestApproval` methods through `ApprovalBoxFanoutService.fanout(...)` so the existing `:235-237` publish fires. The `AFTER_COMMIT` listener, mapping, and Oracle SQL already exist and are correct — **only the publish call is missing.** No new endpoint, no DTO change, no migration.

> **Implemented 2026-06-29.** The PENDING publish was added to each module's `requestApproval` — `VendorPaymentService`, `ExpensePaymentService`, `PayrollPaymentService`, `OverseasPaymentService` (single AF1 path) — right after `APPR_SEQ_NO` is resolved, mirroring the existing PROCESSING/`fanout` pattern. **Internal Transfer is intentionally excluded** (WeCMS-originated, blank `ERP_FILE_*` → listener's non-ERP guard skips it — a dead no-op; matches legacy which also no-ops on blank `ERP_FILE_*`). **Overseas M-mode (`requestApprovalMulti`) excluded** (the HAENGSUNG refresh imports overseas only as single AF1 batches; M-mode `apprSeqNo` lives on `OVRS_PYMN_MSTR`, not the `OVRS_PYMN_SUMR` the listener resolves). No frontend / Oracle-SQL / endpoint change. Tests: per-module request-approval tests assert the PENDING publish; the listener's APPROVED/REJECTED + non-HAENGSUNG-skip + Oracle-disabled tests remain green.

### 10.7 Is a Web change needed?

**No.** Both flows already use the shared approval APIs:
- Request Approval → per-module `request-approval` endpoint (shared `RequestApprovalDialog`).
- Approve / Reject → `approval-box/{apprSeqNo}/{approve|reject}` (shared `CommonApprovalDetailsDialog`).

The frontend needs **no HAENGSUNG-specific logic** — the PENDING gap is purely a backend publish that the existing request-approval call will trigger once added. No `wecms-web` change.

### 10.8 Final conclusion

| Entry point | Approve/Reject | Pending (on request) | FE change | BE change |
|---|---|---|---|---|
| **Approval Details popup** | ✅ full parity (20/32 + reject detail) | n/a | none | none |
| **Create / Request Approval** | n/a | ❌ `10` not emitted for Vendor/Expense/Payroll/Overseas/Internal (only CardTransactions) | none | **publish `PENDING` in each module's `requestApproval`** |

**Approve / Reject is fully wired and at parity from every entry point (popup + per-module action).** The **one real missing hook** is the `PENDING` write-back on Create / Request Approval for the four core HAENGSUNG payment modules — terminal states (20/32/51) are unaffected, so this only skips the intermediate "requested/pending" Oracle status that legacy writes. **Backend-only one-line-per-module fix; reported for approval before implementation. No frontend change required.**

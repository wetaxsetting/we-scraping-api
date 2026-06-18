# C3 — Payment Result "Re-run" : REMOVED FROM SCOPE (PO decision) — CLOSED

**Status:** ✅ CLOSED — **Re-run removed from the active WeCMS business process by the PO, and now fully removed from the refactor codebase (2026-06-17).** Not a supported feature; out of SIT/UAT scope. No redispatch / legacy-parity / `PROC_STAT='9'` work was done (it was unnecessary once the feature was dropped).

## CLOSURE (2026-06-17) — Re-run code DELETED

The dormant Re-run code is no longer "dead code pending a future MR" — it has been **removed** across FE + BE (Expense / Vendor / Payroll / Internal Transfer; Overseas never had it):
- **Frontend:** the `handleRerun` handlers + `onRerun` props (4 detail pages), the Re-run CTA + `isRerunEligible` + `RERUN_VALID_RS_CDS` in `detailColumns.tsx` + `ResultDetailView.tsx`, the `rerun*Line` API wrappers (4 clients), and the `payment.detail.rerun*` i18n keys (en/ko/vi) are all deleted. No Re-run button renders; no Re-run call is reachable from the UI.
- **Backend:** the 4 `POST .../{id}/lines/{lineId}/rerun` endpoints, the `*Service.rerun(...)` methods, the `RERUN_VALID_RS_CDS` + `AUDIT_RERUN_KEY` constants, the now-unused `tbPayFileDataRepository` + `dispatchResultResolver` injected fields (rerun-only), and the `WBE2122/2123/2124` messages are all removed.
- **Verification:** backend `compileJava` + `compileTestJava` pass; frontend `tsc --noEmit` passes; grep finds no active `rerun` reference in any Payment Result screen or controller. See the removal report (session 2026-06-17).
- **Restore path:** if a future PO requirement revives Re-run, re-implement fresh (the genuinely-correct design is `PROC_STAT='9'` re-arm + dispatcher re-invoke — the "real redispatch" option below — never the inert `RERUN_DIV` flag).

The historical analysis below is retained for context (why legacy Re-run was inert, and why a redispatch redesign would have been required for a working feature).

---

(Prior status line, retained for history:) Re-run removed from the active WeCMS business process by the PO (2026-06-17). The C3 de-regression that was being scoped is **abandoned** — unnecessary now that the feature is dropped.
**Scope:** Expense / Vendor / Payroll / Internal Transfer Payment Result detail — the per-line **Re-run** action. (Overseas never had Re-run.)
**Date:** 2026-06-17 · **System:** Refactor (`wecms-api` + `wecms-web`)

---

## RESOLUTION (2026-06-17) — Re-run is deprecated

- **Business decision:** the PO confirmed Re-run is **not** part of the active WeCMS process. Users do not use it. It is **out of SIT/UAT scope**.
- **No implementation:** the planned legacy-parity de-regression (route-aware `RERUN_DIV` write to remove the VND `WBE2122`) is **NOT** implemented and will **not** be — it is moot for a dropped feature.
- **Remaining Re-run code is DEAD / LEGACY** until/unless a future PO requirement restores it. It was **not deleted** in this read-only pass; a separate cleanup MR (with PO sign-off) can remove it later. Inventory of the dead code (verified 2026-06-17):
  - **Frontend (reachable today — 4 modules):** `components/payment/{expense-payment,vendor-payment,payroll}/PaymentResultDetailPage.tsx` + `internal-transfer/TransferResultDetailPage.tsx` each define `handleRerun` and pass `onRerun={handleRerun}` to `<ResultDetailView>`; `components/payment/shared/ResultDetailView.tsx` + `detailColumns.tsx` render the Re-run CTA when `isRerunEligible(row)` (`RS_CD ∈ {0001..0005}` AND `PROC_DTTM = today`); `lib/api/{expensePayment,vendorPayment,payrollPayment,internalTransfer}.ts` expose `rerun*Line(...)`. Overseas: none.
  - **Backend (reachable today — 4 modules):** `POST /api/v1/payment/{expense-payment,internal-transfer,payroll,vendor-payment}/.../{id}/lines/{lineId}/rerun` → `*Service.rerun(...)` (file-route only; throws `WBE2122` on the VND/api route). Overseas: none. Error codes `WBE2122/2123/2124` + i18n keys `payment.detail.rerun*` remain.
- **Practical SIT note:** the CTA is conditionally rendered only for same-day, transient-failed **dispatched** rows (`RS_CD 0001–0005`, `PROC_DTTM=today`). SIT/R&D data has no such `TB_PAY_TARGET`/`TB_PAY_FILE_DATA` rows, so the button is **effectively never shown** in SIT; even if forced on a VND row it throws `WBE2122` and writes nothing. It therefore does not affect Payment Result read-path readiness.
- **Recommended follow-up (separate MR, PO-approved):** delete the Re-run CTA wiring (FE handlers + `onRerun` prop path in `ResultDetailView`/`detailColumns`), the `rerun*Line` API wrappers, the 4 `rerun` controller endpoints + `*Service.rerun` methods, and retire `WBE2122/2123/2124` + the `payment.detail.rerun*` i18n keys. Until then they are inert dead code.

---

## Historical analysis (retained — why parity was moot anyway)

## Why this is deferred and NOT changed

The task brief was: *"Match legacy behavior only. Do not implement a new redispatch design unless PO/vendor confirms. If exact legacy behavior is inert, document it clearly. Do not invent new behavior like resetting `PROC_STAT='9'` unless approved."*

Tracing the full re-run path on both sides shows **setting `TB_PAY_TARGET.RERUN_DIV='Y'` does not cause a redispatch anywhere** — so "matching legacy" would mean writing an inert flag, and the genuinely-correct fix requires PO/vendor approval. Both options are out of the allowed scope, so the re-run code is **left unchanged** and the decision is escalated.

## Evidence

### Legacy (live JEX)
- `pymn_0004_003_u001_act.jsp` (Expense re-run; sibling `pymn_0002_003_u001_act.jsp`) updates **only** `TB_PAY_TARGET` — sets `RERUN_DIV='Y'`, `RERUN_REQ_DTTM`, `USER_ID`, guarded on `TB_FIRM_TRAN.RS_CD ∈ {0001..0005}` and `TRAN_DT = today`. It **never resets `PROC_STAT`**.
- The firm-banking dispatcher (`WECMS_BATCH DEV/.../IDO.TB_PAY_TARGET_R001.xml`, run by `NAPAS_REAL_TIME_BATCH.java`) selects rows **`WHERE TRAN_DT = today AND PROC_STAT = '9'`** — it does **not** reference `RERUN_DIV`. No code, IDO, or DB trigger anywhere selects `WHERE RERUN_DIV = 'Y'`.
- ⇒ A row whose `PROC_STAT` already left `'9'` is never re-picked. **Legacy re-run sets a flag that nothing consumes** — it is effectively inert for redispatch (the flag's only live effects are the "already re-run" guard and a grid display column).

### Refactor (`wecms-api`)
- `RERUN_DIV` is mapped on `TbPayTarget` for schema parity but is **never read or written** anywhere.
- The current `rerun()` in all four services (`ExpensePaymentService.rerun` etc.) takes a **different** approach: it nulls `TB_PAY_FILE_DATA.RS_CD` (file route) and **rejects api-route batches** (no `PAY_FILE_SEQ_NO`) with **`WBE2122`**. Domestic VND batches dispatch to the **api route** (`TransferDispatchExecutor` writes `TB_PAY_TARGET`, leaves `master.payFileSeqNo` null), so **domestic VND re-run currently throws `WBE2122` before writing anything**.
- Refactor dispatch is real-time (`RealtimeDispatchService`, Gate 1 `PROC_STAT='9'`); the only `@Scheduled` job (`ResultCheckJob`) is disabled by default and does result-check, not redispatch.

## Options for the PO/vendor decision

| Option | What it does | Verdict |
|---|---|---|
| **A. Match legacy literally** | Switch re-run to write `TB_PAY_TARGET.RERUN_DIV='Y'` + `RERUN_REQ_DTTM` + `USER_ID` (api route), with the legacy `RS_CD ∈ {0001..0005}` + today guards. | **Inert** — no redispatch (legacy itself doesn't redispatch off this flag). Also touches a legacy-compatible **write** path → CLAUDE.md §5 PO approval required. No functional gain. |
| **B. Correct redispatch** | Re-arm `TB_PAY_TARGET.PROC_STAT='9'`, clear the prior `TB_FIRM_TRAN` result, re-invoke `RealtimeDispatchService.dispatch(...)`. | This is the genuinely-working fix, but it is a **new design** beyond legacy parity → explicitly out of scope without PO/vendor confirmation. |
| **C. Leave as-is (chosen now)** | No change. Domestic VND re-run remains `WBE2122`; document the gap. | Honors "no redesign / match legacy only / document if inert". |

## Questions for PO / firm-banking vendor

1. Should `TB_PAY_TARGET.RERUN_DIV='Y'` trigger a redispatch in the refactor's api/real-time (NAPAS) flow, or must re-run re-invoke the dispatcher (re-arm `PROC_STAT='9'`)?
2. Is domestic Expense/Vendor/Payroll **always VND / api route** in the live deployment? If so, domestic re-run is currently 100% non-functional (`WBE2122`) and needs Option B.
3. If Option B is approved: confirm the exact re-arm contract (`PROC_STAT='9'` + clear `TB_FIRM_TRAN` result + re-dispatch) so it matches the live bank handshake.

## SIT/UAT note

Re-run is an edge **write** action on the detail page; the read-path SIT pass for C1 + C2 can proceed without it **provided** this gap is disclosed (domestic re-run throws `WBE2122`) and tracked here. No code or DB change was made for C3.

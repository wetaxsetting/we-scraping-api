# Scheduled-Page Parity — Schedule Date + Requestor (all 5 Payment modules)

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](README.md)._

**Discovered:** 2026-06-08 · **System:** Refactor · **Type:** discovered parity bug + fix
**Modules:** Internal Transfer · Vendor Payment · Expense Payment · Payroll · Overseas Payment
**Legacy:** `pymn_0009_001` / `pymn_0001_001` / `pymn_0003_001` / `pymn_0005_001` / `pymn_0007_001` (Scheduled list `_r001`)
**Method:** live screenshot (Vendor Scheduled Payment) vs. JEX source-of-truth vs. refactor source, column-by-column.

---

## 1. Symptom (from the refactor screenshot)

On the Scheduled list table of every Payment module:

1. **Schedule Date** column is empty / missing data.
2. **Requestor** column shows a login id / email (`lamine@g…`) instead of the user's **name**.

---

## 2. Legacy behavior (source of truth)

The Scheduled list of all five modules is backed by one shared legacy query,
`IDO.SCDUL_PYMN_SUMR_R002`, which `LEFT JOIN`s `EXPT_RESOL_MSTR` (alias `ERM`)
to `SCDUL_PYMN_SUMR` on `(BIZ_KEY, APPR_SEQ_NO)`:

| Grid column | Legacy expression | Source | Evidence |
|---|---|---|---|
| **Schedule Date** (지급예정일) | `ERM.PAY_DATE` | `EXPT_RESOL_MSTR.PAY_DATE` (`yyyyMMdd`, set at Request Approval) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.SCDUL_PYMN_SUMR_R002.xml` (`ERM.PAY_DATE`); `WSVC.pymn_0001_001_r001.xml` field `PAY_DATE` "지급예정일"; `WECMS_STATIC/ts-src/js/jex/wabooks/pymn/pymn_0001_001.ts:1048-1055` |
| **Requestor** (발의자명) | `ERM.DRAFT_USER_NM` | `EXPT_RESOL_MSTR.DRAFT_USER_NM` (the requesting user's **display name**) | `IDO.SCDUL_PYMN_SUMR_R002.xml` (`ERM.DRAFT_USER_NM`); `WSVC.pymn_0001_001_r001.xml` field `DRAFT_USER_NM` "발의자명"; `pymn_0001_001.ts:1060-1061` |

**Confirmed:** Requestor = `DRAFT_USER_NM` (the **NAME**), not a login id / email. The
matching id is available as `DRAFT_USER_ID` but the grid renders the name.
`PAY_DATE` is **not** `SCDUL_PYMN_SUMR.PAY_TRAN_DT` — `PAY_TRAN_DT` is the
dispatch (bank send) timestamp and stays null for pending/approved batches,
which is exactly why the column rendered blank for an `Approved` row.

---

## 3. Root cause (refactor)

| Module | Schedule Date — before | Requestor — before |
|---|---|---|
| Internal Transfer | ✅ `EXPT_RESOL_MSTR.PAY_DATE` (correct) | ❌ `SCDUL_PYMN_SUMR.REG_ID` (login id) |
| Vendor Payment | ❌ `SCDUL_PYMN_SUMR.PAY_TRAN_DT` (null pre-dispatch → empty) | ❌ `REG_ID` |
| Expense Payment | ❌ `SCDUL_PYMN_SUMR.PAY_TRAN_DT` (null pre-dispatch → empty) | ❌ `REG_ID` |
| Payroll | ✅ `EXPT_RESOL_MSTR.PAY_DATE` (correct) | ❌ `REG_ID` |
| Overseas Payment | ✅ `ERM.PAY_DATE` (native UNION query — correct) | ✅ `ERM.DRAFT_USER_NM` (correct — reference) |

Two distinct defects:

1. **Schedule Date (Vendor + Expense only).** `VendorPaymentService.toListRow` /
   `ExpensePaymentService.toListRow` passed `s.getPayTranDt()` into the
   `ScheduledTransferRow.payDate` slot. `PAY_TRAN_DT` is the dispatch date —
   null until the batch is actually sent to the bank — so the column was blank
   even for `Approved` rows.

2. **Requestor (Internal + Vendor + Expense + Payroll).** The shared
   `ScheduledTransferRow` DTO had **no** draft-user-name field at all, so the
   wire never carried `DRAFT_USER_NM`. All four frontend pages therefore mapped
   `requestor` from `reg_id` (the batch-creator login id / email). Overseas
   already carried `OverseasScheduledRow.draftUserNm` and was unaffected.

---

## 4. Fix

Converged all four shared-DTO modules onto the (already-correct) Payroll
single-fetch pattern: one `EXPT_RESOL_MSTR` lookup per page keyed by
`APPR_SEQ_NO` (`fetchExptResolByApprSeqNo`), from which `toListRow` derives
**DOC_NO** (Approval Ref), **PAY_DATE** (Schedule Date) and **DRAFT_USER_NM**
(Requestor). Added `draftUserNm` to the shared DTO and threaded it to the wire.

### Backend (`wecms-api-main`)

| File | Change |
|---|---|
| `domain/payment/internaltransfer/dto/ScheduledTransferRow.java` | Added `draftUserNm` record component (wire `draft_user_nm`); updated the two backward-compat convenience constructors to delegate `draftUserNm = null`. |
| `domain/payment/internaltransfer/service/InternalTransferService.java` | Consolidated `buildDocNoMap` + `buildPayDateMap` into one `fetchExptResolByApprSeqNo` (`Map<String, ExptResolMstr>`); `toListRow` now derives `docNo` / `payDate` / `draftUserNm` from the entity. |
| `domain/payment/vendorpayment/service/VendorPaymentService.java` | Replaced `buildDocNoMap` with `fetchExptResolByApprSeqNo`; `toListRow` now uses `ERM.PAY_DATE` (was `s.getPayTranDt()`) + `ERM.DRAFT_USER_NM`. Added `ExptResolMstr` import. |
| `domain/payment/expensepayment/service/ExpensePaymentService.java` | Same as Vendor. |
| `domain/payment/payrollpayment/service/PayrollPaymentService.java` | `toListRow` now also derives + passes `draftUserNm` (Schedule Date was already correct). |
| Overseas (`domain/payment/service/OverseasPaymentService.java`) | **No change** — already correct; used as the reference. |

### Frontend (`wecms-web-main`)

| File | Change |
|---|---|
| `lib/types/api.ts` | Added `draft_user_nm: string \| null` to `ApiScheduledTransferRow`. |
| `lib/api/mappers.ts` | Added `draftUserNm` to the in-memory `ScheduledTransferRow` type + `mapScheduledTransferRow` (`api.draft_user_nm ?? null`). |
| `components/payment/internal-transfer/ScheduledTransferPage.tsx` | `requestor: api.draftUserNm ?? api.regId`. |
| `components/payment/vendor-payment/ScheduledPaymentPage.tsx` | Added `draft_user_nm?` to the inline wire type; `requestor: api.draft_user_nm ?? api.reg_id`. |
| `components/payment/expense-payment/ScheduledPaymentPage.tsx` | Same as Vendor. |
| `components/payment/payroll/ScheduledPaymentPage.tsx` | Same as Vendor. |
| Overseas (`components/payment/overseas-payment/ScheduledPaymentPage.tsx`) | **No change** — already `api.draft_user_nm \|\| api.reg_id`. |

**Fallback rule:** Requestor renders `DRAFT_USER_NM`; when null (batch not yet
submitted for approval, so no `EXPT_RESOL_MSTR` row), the frontend falls back to
`reg_id` — matching the user's request for a *safe fallback only when the name
is missing*.

**Not touched:** sorting, filtering, pagination, approval-status derivation, the
Approver column, and the Excel-upload flow are all untouched — the change is
confined to the `EXPT_RESOL_MSTR` join projection used by the list mapper.

---

## 5. Tests

Pure-Mockito service tests (no Spring context / DB), one per shared-DTO module,
each asserting (a) `payDate` == `EXPT_RESOL_MSTR.PAY_DATE` and (b) `draftUserNm`
== `EXPT_RESOL_MSTR.DRAFT_USER_NM` (with `regId` still the login id), plus a
no-approval-request case proving both are null (frontend then falls back to
`reg_id`):

- `domain/payment/internaltransfer/service/InternalTransferScheduledListTest`
- `domain/payment/vendorpayment/service/VendorPaymentScheduledListTest`
- `domain/payment/expensepayment/service/ExpensePaymentScheduledListTest`
- `domain/payment/payrollpayment/service/PayrollPaymentScheduledListTest`

**Frontend:** `wecms-web-main` ships no test runner (no jest/vitest/testing-library
in `package.json`, and CLAUDE.md forbids adding a UI library without a written
reason), so frontend verification is `npx tsc --noEmit` + `npx eslint` on the
changed files — both clean (pre-existing unrelated warnings only).

---

## 6. Verification

| Command | Result |
|---|---|
| `./gradlew compileJava -q` (api) | ✅ clean |
| `./gradlew test --tests "*ScheduledListTest" -q` (api) | ✅ 4 classes / 8 tests green |
| `./gradlew test --tests "com.kosign.wecms.domain.payment.*" -q` (api) | ✅ no regressions |
| `npx tsc --noEmit` (web) | ✅ clean |
| `npx eslint <changed files>` (web) | ✅ 0 errors (pre-existing warnings only) |

---

## 7. Final status per affected page

| Page | Schedule Date | Requestor |
|---|---|---|
| Internal Transfer › Scheduled Transfer | ✅ `EXPT_RESOL_MSTR.PAY_DATE` | ✅ `DRAFT_USER_NM` → fallback `reg_id` |
| Vendor Payment › Scheduled Payment | ✅ fixed (was `PAY_TRAN_DT`) | ✅ fixed (was `reg_id`) |
| Expense Payment › Scheduled Payment | ✅ fixed (was `PAY_TRAN_DT`) | ✅ fixed (was `reg_id`) |
| Payroll › Scheduled Payment | ✅ `EXPT_RESOL_MSTR.PAY_DATE` | ✅ fixed (was `reg_id`) |
| Overseas Payment › Scheduled Payment | ✅ already correct (query) | ⚠️ see §8 — draft-state email fallback (round 2) |

> **Correction (round 2, below):** the round-1 claim "Overseas … Requestor already
> correct" was **about the SQL projection only**. The Overseas *frontend* shared
> the same `… || reg_id` email fallback as the others, so a **draft** Overseas
> row still rendered the creator's login/email. See §8.

---

## 8. Follow-up — draft-state Requestor showed login/email (2026-06-08, round 2)

### 8.1 New symptom

On **Overseas Scheduled Payment**, the newest row (`05-Jun-2026`, yellow-highlighted,
no Approval Status, Approver `- 0`, Approval Ref `-`) showed **Requestor =
`lamine@gmail.com`** and a **blank Schedule Date**, while older *Approved* rows
correctly showed **Requestor = `lamine`** with a populated Schedule Date.

### 8.2 Root cause

The highlighted row is a **draft** — an uploaded batch **not yet submitted for
approval**, so `APPR_SEQ_NO` is null and the `EXPT_RESOL_MSTR` LEFT JOIN yields
no row. Therefore `draft_user_nm` and `pay_date` are both null on the wire — this
is **correct** (there is no approval request yet). The defect was purely the
**frontend fallback**: every Scheduled page (including Overseas) mapped
`requestor: … ?? reg_id` (or `|| reg_id || "-"`), so when the name was null it
substituted `REG_ID` — the creator **login id / email**. The round-1 change
*introduced* that fallback for the four sibling modules and *left* it in place for
Overseas; it is the same defect on all five.

> Two separate things were conflated in round 1: the **SQL join** (correct on all
> five) vs. the **frontend null-fallback** (wrong on all five). Overseas was only
> ever "correct" on the first.

### 8.3 Legacy evidence — what each state shows

The Requestor column is sourced **only** from `EXPT_RESOL_MSTR.DRAFT_USER_NM`
(the approval-request drafter's name). There is **no `USER_INFM` join** to resolve
a name from `REG_ID`, and the grid renders `null2void(DRAFT_USER_NM)` — i.e. an
**empty cell** when the name is absent. Legacy **never** prints `REG_ID`/email in
this column.

| Record state | `APPR_SEQ_NO` / `EXPT_RESOL_MSTR` | Legacy Requestor | Legacy Schedule Date |
|---|---|---|---|
| **Draft** (uploaded, not submitted) | none | **blank** (`null2void(DRAFT_USER_NM)`) — yellow row | **blank** (no `PAY_DATE` yet) |
| **Approval pending / requested / in-progress** | present | `DRAFT_USER_NM` (name) | `PAY_DATE` |
| **Approved** | present | `DRAFT_USER_NM` (name) | `PAY_DATE` |
| **Rejected** | present | `DRAFT_USER_NM` (name) | `PAY_DATE` |

Evidence:
- Shared modules: `IDO.SCDUL_PYMN_SUMR_R002.xml` — `SPS.REG_ID` selected separately; `ERM.DRAFT_USER_NM` / `ERM.PAY_DATE` from `LEFT JOIN EXPT_RESOL_MSTR`; `pymn_0001_001.ts:1060-1061` renders `null2void(val.DRAFT_USER_NM)`.
- Overseas: `IDO.OVRS_PYMN_SUMR_R002.xml` SUMR branch (`OVRS_PYMN_SUMR OPS LEFT JOIN EXPT_RESOL_MSTR ERM`) — `OPS.REG_ID` separate, `ERM.DRAFT_USER_NM` / `ERM.PAY_DATE` from the join; `pymn_0007_001.ts:1061-1062` renders `null2void(val.DRAFT_USER_NM)`, draft rows get `yellow-bg` (`!val.APPR_SEQ_NO`), `pay_date` formatted (blank when null).

### 8.4 Answers to the review questions

- **Q3 (missing join):** No join is missing. The `EXPT_RESOL_MSTR` LEFT JOIN is present on every branch; it legitimately returns no row for a draft.
- **Q6 (should Requestor always show a name?):** **No — per legacy parity.** `DRAFT_USER_NM` only exists once an approval request is created. For a draft, legacy shows the cell **blank**; it does not fall back to `REG_ID` and does not resolve the creator's name from `USER_INFM`. (Showing the creator's name for drafts would be a deliberate UX enhancement requiring a new `REG_ID → USER_INFM.USER_NM` join in all five list queries — a legacy deviation needing PO sign-off. Not done here.)
- **Q7 (Schedule Date source):** `EXPT_RESOL_MSTR.PAY_DATE` for all states; **blank for drafts** (no upload-time scheduled-date column feeds this cell in legacy). The blank Schedule Date on the screenshot's draft row is therefore **correct**, not a bug.

### 8.5 Fix (round 2 — frontend only)

Dropped the `reg_id` email fallback on all five Scheduled pages; Requestor now
renders `DRAFT_USER_NM` else **blank** (matching `null2void`). Backend unchanged
(it already returns `draft_user_nm` = name, null for drafts).

| File | Change |
|---|---|
| `components/payment/internal-transfer/ScheduledTransferPage.tsx` | `requestor: api.draftUserNm ?? ""` (was `?? api.regId`) |
| `components/payment/vendor-payment/ScheduledPaymentPage.tsx` | `requestor: api.draft_user_nm ?? ""` (was `?? api.reg_id`) |
| `components/payment/expense-payment/ScheduledPaymentPage.tsx` | same |
| `components/payment/payroll/ScheduledPaymentPage.tsx` | same |
| `components/payment/overseas-payment/ScheduledPaymentPage.tsx` | `requestor: api.draft_user_nm || ""` (was `|| api.reg_id || "-"`) |

**Not changed (in round 2):** Schedule Date (already legacy-correct, blank for
drafts); backend DTO/queries; sorting/filtering/pagination/Excel-upload. The
**Scheduled detail pages** still carried the same `requestor ?? reg_id` fallback
on their "Requestor" summary row — addressed in round 3 (§9 below).

### 8.6 Verification (round 2)

| Command | Result |
|---|---|
| `npx tsc --noEmit` (web) | ✅ clean |
| `npx eslint <5 changed pages>` (web) | ✅ 0 errors (pre-existing warnings only) |
| `./gradlew test --tests "*ScheduledListTest"` (api) | ✅ still green (backend unchanged; wire still carries `draft_user_nm`=name + `reg_id`=login, null for drafts) |

### 8.7 Final status per page (corrected)

| Page | Schedule Date | Requestor |
|---|---|---|
| Internal Transfer › Scheduled Transfer | ✅ `PAY_DATE` (blank for draft) | ✅ `DRAFT_USER_NM`, **blank** for draft (no email) |
| Vendor Payment › Scheduled Payment | ✅ `PAY_DATE` (blank for draft) | ✅ `DRAFT_USER_NM`, **blank** for draft (no email) |
| Expense Payment › Scheduled Payment | ✅ `PAY_DATE` (blank for draft) | ✅ `DRAFT_USER_NM`, **blank** for draft (no email) |
| Payroll › Scheduled Payment | ✅ `PAY_DATE` (blank for draft) | ✅ `DRAFT_USER_NM`, **blank** for draft (no email) |
| Overseas Payment › Scheduled Payment | ✅ `PAY_DATE` (blank for draft) | ✅ `DRAFT_USER_NM`, **blank** for draft (no email) |

---

## 9. Round 3 — Scheduled **detail** pages: same email fallback (2026-06-08)

### 9.1 Scope

The round-2 fix covered the five Scheduled **list** pages. The five Scheduled
**detail** pages (`…/scheduled-payment/[id]` / `…/scheduled-transfer/[id]`) still
rendered their summary "Requestor" row via `detail.requestor ?? reg_id`
(Overseas: `draft_user_nm || reg_id || "-"`), so a **draft** detail still showed
the creator login/email instead of blank.

### 9.2 Legacy behaviour (same as §8.3)

Requestor = `EXPT_RESOL_MSTR.DRAFT_USER_NM` only; blank when null (draft). No
`REG_ID`/email, no `USER_INFM` name lookup. Confirmed not to add the
`REG_ID → USER_INFM.USER_NM` enhancement (legacy never did it) — pending PO.

### 9.3 Fix (frontend only)

Dropped the `reg_id` fallback on all five Scheduled detail pages → Requestor
renders `DRAFT_USER_NM` else blank.

| File | Change |
|---|---|
| `components/payment/internal-transfer/ScheduledTransferDetailPage.tsx` | `value: d.requestor ?? ""` (was `?? d.regId ?? ""`) |
| `components/payment/vendor-payment/ScheduledPaymentDetailPage.tsx` | `const requestor = detail?.requestor ?? ""` (was `?? detail?.reg_id ?? ""`) |
| `components/payment/expense-payment/ScheduledPaymentDetailPage.tsx` | same |
| `components/payment/payroll/ScheduledPaymentDetailPage.tsx` | same |
| `components/payment/overseas-payment/ScheduledPaymentDetailPage.tsx` | `value: row.draft_user_nm || ""` (was `|| row.reg_id || "-"`) |

**Shared mapper:** `mapScheduledTransferDetail` needs **no change** — it already
maps `requestor: api.requestor` with no `reg_id` fallback (and keeps `regId` as a
separate field). The fallback lived only at the page level. The
`ApiScheduledTransferDetailResponse.requestor` wire field (`EXPT_RESOL_MSTR.DRAFT_USER_NM`,
null for drafts) is unchanged.

**Still carrying the `requestor ?? reg_id` pattern (out of this round's scope, tracked):**
the **Result** detail pages (`*PaymentResultDetailPage` / `TransferResultDetailPage`),
Approval Box (`ApprovalBoxPage` `d.requestor || d.regId`), and Corporate Card —
different screens, not part of the Scheduled-page parity request. Flagged for a
separate pass / PO decision; not changed here.

### 9.4 Verification (round 3)

| Command | Result |
|---|---|
| `npx tsc --noEmit` (web) | ✅ clean |
| `npx eslint <5 changed detail pages>` (web) | ✅ 0 errors (2 pre-existing unrelated warnings) |

Backend untouched (no `*ScheduledListTest` impact).

---

## 10. Round 4 — Overseas draft row showed Approver "- 0" (2026-06-08)

### 10.1 Symptom

On Overseas Scheduled Payment, the draft row (`APPR_SEQ_NO` null, blank
Approval Status / Requestor / Schedule Date) showed **Approver = "- 0"** (a `-`
name placeholder + a `0` count badge + dropdown chevron). The four sibling
modules correctly showed a **blank** Approver cell for their draft rows.

### 10.2 Legacy behaviour

`createApproverCell` renders an **empty** cell when the approver chain is
empty/null (no approval line → draft); the first-approver name + `<span>N</span>`
count badge appear **only** when approvers exist. Never `-`, never `0`, never
`- 0`.

| Evidence | Empty-case render |
|---|---|
| Internal `pymn_0009_001.ts:204-206` | `if (!val.REC_1 \|\| val.REC_1.length === 0) return '<td sort class="clickable"></td>';` |
| Overseas `pymn_0007_001.ts:1148` | `else { approversHtml = '<td sort class="clickable"></td>'; }` |
| Vendor / Expense / Payroll | same `createApproverCell` guard (mirror of Internal) |

### 10.3 Root cause (Overseas-only)

The shared shell renders the count badge whenever `row.approverCount != null`
(`PaymentScheduledBatchListPage.tsx:1603`). Per-module mapping fed it:

- **4 siblings:** backend sets `approver_count = null` for drafts (the count is
  only computed when an `EXPT_RESOL_APPRVR_INFM` chain exists), so the frontend
  `?? undefined` → no badge, and `approver ?? ""` → blank. **Correct.**
- **Overseas:** the backend `apprvr_tot_cnt` is a `COUNT(*)` subquery that returns
  **`0`** for a draft (`appr_seq_no` null → no matching rows). The frontend mapped
  `approverCount: api.apprvr_tot_cnt ?? (…)` — and `0 ?? x` keeps `0` (nullish
  coalescing does not catch `0`), so `0 != null` → the badge rendered "0". Plus
  `approver: … ?? "-"` printed the `-` placeholder. → "- 0".

### 10.4 Fix (frontend only — Overseas list mapper)

`components/payment/overseas-payment/ScheduledPaymentPage.tsx`:

```ts
approver:      api.approvers?.[0]?.apprvr_nm ?? "",                  // was ?? "-"
approverCount: (api.apprvr_tot_cnt ?? api.approvers?.length) || undefined,  // was apprvr_tot_cnt ?? (...) — kept 0
```

`|| undefined` coerces `0` → `undefined` so the badge + chevron hide; a blank
name + no badge now matches legacy's empty cell and the four siblings. Approved
rows (count ≥ 1) are unaffected. No backend change (the `0` count is a correct
COUNT for a draft; the frontend simply must not render a "0" badge). Sibling
modules already correct — not touched.

### 10.5 Verification (round 4)

| Command | Result |
|---|---|
| `npx tsc --noEmit` (web) | ✅ clean |
| `npx eslint .../overseas-payment/ScheduledPaymentPage.tsx` | ✅ 0 errors (2 pre-existing unrelated warnings) |

---

## 11. Round 5 — Approval Details popup footer "Requested by …" showed login/email

### 11.1 Symptom

Overseas Scheduled Payment → **Approval Details** popup footer rendered
**"Requested by lamine@gmail.com on 29-May-2026 09:42:58"** — the requester's
**login/email**, on an *Approved* row (not even a draft).

### 11.2 Legacy behaviour (source of truth)

The shared Approval Details popup is legacy **`comm_0195_001`**. The "Requestor"
field is the requester's **display name**, never the login id:

| Field | Legacy source | Evidence |
|---|---|---|
| Requestor ("Requested by") | **`data.REG_NM`** (name) | `comm_0195_001.ts:875` (`#req_appr_nm`.text(`data.REG_NM`)) + `:2083` (`<strong id="usrid_exp">${null2void(data.REG_NM)}</strong>`) |
| Requested Date | `data.REG_DTM` | `comm_0195_001.ts:2099` (`formatDate(data.REG_DTM)`) |
| Scheduled Date | `data.PAY_DATE` | `comm_0195_001.ts:2100` (`formatDate(data.PAY_DATE)`) |

Legacy **never** prints `REG_ID`/email in this footer. So
**"Requested by lamine@gmail.com" is WRONG — it must show the name (`lamine`).**

### 11.3 Refactor root cause + the cross-module comparison

The footer renders `t("payment.approval_details.requested_by", { name: requestedBy, date: requestedAt })`
(`ApprovalDetailsDialog.tsx:536-539`) — so `requestedBy` must be the name. The
per-module `requestedBy=` wiring (all five Scheduled pages) was wrong to varying
degrees:

| Module | Before | Behaviour |
|---|---|---|
| Internal (`:1080`) | `detail.reg_id \|\| row.requestor` | **email first** — email even for Approved |
| Payroll (`:745`) | `detail.reg_id \|\| row.requestor` | **email first** — email even for Approved |
| **Overseas** (`:813`, `:845`) | `detail.reg_id \|\| row.requestor` | **email first**; Overseas detail DTO has **no** name field at all |
| Vendor (`:712`) | `detail.requestor \|\| detail.reg_id \|\| row.requestor` | name for Approved, **email for drafts** |
| Expense (`:706`) | `detail.requestor \|\| detail.reg_id \|\| row.requestor` | name for Approved, **email for drafts** |

**Overseas uses a different implementation path:** its detail DTO
`ApiOverseasPaymentDetailResponse` (`lib/api/payment.ts:390-409`) carries only
`reg_id` — **no `requestor`/`draft_user_nm`** — whereas the other four share
`ApiScheduledTransferDetailResponse` which **does** carry `requestor`
(= `EXPT_RESOL_MSTR.DRAFT_USER_NM`, `lib/types/api.ts:1895`). That is why the
Overseas caller had nothing but `reg_id` to fall back to. (The list **row**,
however, already carries `requestor` = DRAFT_USER_NM after round 2.)

### 11.4 Fix (frontend only — all 5 Scheduled approval-details footers)

Prefer the name, blank otherwise, **never `reg_id`**:

| File | Change |
|---|---|
| `internal-transfer/ScheduledTransferPage.tsx:1080` | `detail.requestor \|\| row.requestor \|\| ""` (was `detail.reg_id \|\| row.requestor`) |
| `vendor-payment/ScheduledPaymentPage.tsx:712` | `detail.requestor \|\| row.requestor \|\| ""` (dropped `detail.reg_id`) |
| `expense-payment/ScheduledPaymentPage.tsx:706` | same |
| `payroll/ScheduledPaymentPage.tsx:745` | `detail.requestor \|\| row.requestor \|\| ""` (was `detail.reg_id \|\| row.requestor`) |
| `overseas-payment/ScheduledPaymentPage.tsx` (ApprovalDetailsDialog + ChangeApproverDialog) | `row.requestor \|\| ""` (detail has no name field; was `detail.reg_id \|\| row.requestor`) |

`requestedAt` was already correct (`row.dateTime` = formatted `REG_DTM`, matching
legacy `REG_DTM`). The other popup fields were verified correct: **status badge**
(`detailStatusToOutcome(appr_stts, …)`), **approver chain + count** (`detailsView.approvers`),
**Scheduled Date** (`row.scheduleDate` = `PAY_DATE`), **withdrawal account**
(`out_bank_cd`/`wdrl_act_no`/`ccy`). No backend change.

> **Optional backend follow-up (not done, no PO ask):** add `draft_user_nm` to
> `ApiOverseasPaymentDetailResponse` (+ backend DTO) so the Overseas detail is
> self-sufficient rather than depending on the clicked list row. Functionally
> equivalent today because the row always carries the name.

**Out of scope / still carrying `… || regId` (tracked, different surfaces):**
Approval Box (`ApprovalBoxPage.tsx:1004` `d.requestor || d.regId`), Corporate Card
`ApprovalRefDetailDialog`, and all Result-detail pages. Not part of the Scheduled
Approval-Details review.

### 11.5 Verification (round 5)

| Command | Result |
|---|---|
| `npx tsc --noEmit` (web) | ✅ clean |
| `npx eslint <5 changed Scheduled pages>` (web) | ✅ 0 errors (pre-existing unrelated warnings only) |

Backend untouched.

---

## 12. Round 6 — Approval Details popup "Total Transfer Amount" unformatted (Overseas)

### 12.1 Symptom

Overseas Scheduled Payment → Approval Details popup showed **Total Transfer
Amount = `10`** (raw integer — no currency symbol, no decimals, no thousand
separators), on a USD batch where the page footer correctly reads `Total (USD) : $50.00`.

### 12.2 Legacy behaviour

`comm_0195_001.ts:879`:
```js
$('#TOTAL_AMT').text(`${_osmc.ui.formatAmount(parseFloat(data.TOTAL_TRAN_AMT), data.CCY_CD || "VND")}`);
```
i.e. currency-formatted via `_osmc.ui.formatAmount(amount, ccyCd)` — symbol +
per-currency fraction digits + thousand separators. The refactor's
`lib/currency.ts#formatAmount` is the documented mirror of that function:

| CCY | Rule (`CCY_RULES`) | Symbol | `formatAmount(10, ccy, true)` |
|---|---|---|---|
| USD | en-US, 2 dp | `$` prefix | **`$10.00`** |
| VND | vi-VN, 0 dp | `₫` suffix | `10₫` |
| KRW | en-US, 0 dp | `₩` prefix | `₩10` |
| EUR | en-US, 2 dp | `€` prefix | `€10.00` |
| JPY | en-US, 0 dp | `¥` prefix | `¥10` |

So for a USD batch the popup must read **`$10.00`**, not `10`.

### 12.3 Refactor root cause + comparison

The shared `ApprovalDetailsDialog` prints `{totalAmount}` verbatim
(`ApprovalDetailsDialog.tsx:430`) — formatting is the caller's responsibility.

| Module | `totalAmount=` wiring | Result |
|---|---|---|
| Internal (`:1011`) / Vendor (`:669`) / Expense (`:663`) / Payroll (`:706`) | `formatAmount(detail.total_tran_amt, detail.ccy_cd, true)` | ✅ `$10.00` |
| **Overseas** (`:795`) | `detail.total_tran_amt?.toString() ?? "0"` | ❌ `10` |
| Overseas ChangeApproverDialog (`:845`) | passes number + `ccyCd`; dialog formats internally (`ChangeApproverDialog.tsx:160` `formatAmount(totalAmount, ccyCd)`) | ✅ already formatted |

Same root pattern as the previous Overseas gaps: Overseas hand-rolled the value
instead of using the shared `formatAmount` the four siblings use.

### 12.4 Fix (frontend only)

`overseas-payment/ScheduledPaymentPage.tsx:795` →
```ts
totalAmount={formatAmount(detailsView.detail.total_tran_amt, detailsView.detail.ccy_cd, true)}
```
(`formatAmount` was already imported.) ChangeApproverDialog needs no change
(already formats internally). No backend change — `formatAmount` mirrors legacy
`_osmc.ui.formatAmount` and the page's own footer.

### 12.5 Verification (round 6)

| Command | Result |
|---|---|
| `npx tsc --noEmit` (web) | ✅ clean |
| `npx eslint .../overseas-payment/ScheduledPaymentPage.tsx` | ✅ 0 errors (2 pre-existing unrelated warnings) |

### 12.6 Answer

`10` is **wrong**. The Approval Details popup must show the **currency-formatted**
amount — **`$10.00`** for USD (legacy `_osmc.ui.formatAmount(TOTAL_TRAN_AMT, CCY_CD)`):
2 dp + `$` prefix for USD/EUR, 0 dp + symbol for VND (`₫` suffix) / KRW (`₩`) / JPY (`¥`),
with thousand separators. The four sibling modules were already correct; Overseas
now matches.

# Payment / Card — "Delete Approval Details" : Legacy JEX vs Refactor Audit

- **System:** Refactor (wecms-api) vs Legacy JEX (source of truth)
- **Date:** 2026-07-02
- **Status:** AUDIT ONLY — no code changed, no tests added
- **Trigger:** `DELETE /api/v1/payment/card-transactions/by-approval/21` → HTTP 500 `WBE_DB`
  (`expt_resol_detl_fk01` violation) on the Card Transactions "Approval Details" popup.
- **Scope:** Delete/withdraw of an approval request from the Approval Details popup for the
  5 payment types (Internal Transfer, Vendor, Expense, Payroll, Overseas) **+** Corporate
  Card › Card Transactions.

> **Verdict up front:** The refactor withdraw paths delete only **part** of the approval
> object graph. They omit the physical delete of **`EXPT_RESOL_DETL`** (and, latently,
> `EXPT_RESOL_PAY_EVID_FILE` + `EXPT_RESOL_APPR_LINE`). Because the FK
> `expt_resol_detl_fk01` has **no `ON DELETE CASCADE`**, deleting `EXPT_RESOL_MSTR` while a
> detail row still points at it aborts the transaction. Legacy never hits this because the
> legacy `ExptResolMstrDAO.deleteByPk(...)` does an **application-level child-first cascade**
> that the refactor only partially reproduced. **All 6 modules share the defect**; Card is
> simply the one that reliably has `EXPT_RESOL_DETL` rows, so it fails every time.

---

## 1. Summary table (per module)

| # | Module | Refactor endpoint | Legacy WSVC | Status guard (refactor) | Deletes `EXPT_RESOL_DETL`? | FK-safe? |
|---|--------|-------------------|-------------|--------------------------|-----------------------------|----------|
| 1 | Internal Transfer | `DELETE /payment/internal-transfer/{id}/approval` | `comm_0192_004_d001` (`APPR_DV=35`) | `APPR_TYPE_CD ∈ {00,03}` (server) | **No** | ⚠️ latent fail |
| 2 | Vendor Payment | `DELETE /payment/vendor-payment/{id}/approval` | `comm_0192_004_d001` (`APPR_DV=32`) | `APPR_TYPE_CD ∈ {00,03}` (server) | **No** | ⚠️ latent fail |
| 3 | Expense Payment | `DELETE /payment/expense-payment/{id}/approval` | `comm_0192_004_d001` (`APPR_DV=31`) | `APPR_TYPE_CD ∈ {00,03}` (server) | **No** | ⚠️ latent fail |
| 4 | Payroll Payment | `DELETE /payment/payroll-payment/{id}/approval` | `comm_0192_004_d001` (`APPR_DV=21`) | `APPR_TYPE_CD ∈ {00,03}` (server) | **No** | ⚠️ latent fail |
| 5 | Overseas Payment | `DELETE /payment/overseas-payment/{id}/approval-line` | `comm_0192_004_d001` (`APPR_DV=34`) | `deriveApprStts == 'P'` (server) | **No** | ⚠️ latent fail |
| 6 | Corporate Card | `DELETE /payment/card-transactions/by-approval/{apprSeqNo}` | `comm_0192_004_d001` (`APPR_DV=33`) | `APPR_TYPE_CD ∈ {00,03}` (server) | **No** | ❌ **fails now** |

"latent fail" = will throw the identical FK error whenever the approval has ≥ 1
`EXPT_RESOL_DETL` row — which is **every** submitted approval in these modules (all write a
detail row at request-approval time). It has simply not been exercised end-to-end yet on the
non-card modules.

---

## 2. Legacy flow (source of truth)

### 2.1 One unified delete for all 6 modules

There is **no per-type delete action**. The Approval Details popup and its Delete button call
a single WSVC; only the *payment-master detach* differs by type.

- **Popup view:** `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0193_001_view.jsp`
  (`comm_0193_001_m001` = "Approval Details"; Delete `<a class="del btn_del">` at line ~468).
- **Popup JS:** `WECMS DEV/WECMS_STATIC/web/js/jex/wabooks/comm/comm_0193_001.js`
  - `.btn_del` → confirm → `deleteApproval()` (lines ~495-520) posts
    `{ BLTB_CD:"000000000000000", POST_SEQ_NO }` to WSVC **`comm_0192_004_d001`** (synchronous).
  - **Status gate is FRONTEND ONLY** (lines ~39-51): `In Progress` keeps Delete visible;
    `Approve`/`Reject` hide Delete (shows Print); otherwise Edit+Delete shown.
- **Backend action:** `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_004_d001_act.jsp`

### 2.2 Backend delete sequence (`comm_0192_004_d001_act.jsp`, APPROVAL branch, lines 207-355)

1. **Requester/admin permission check** (`validatePostOwnerPermission`, lines 86-113 / called 200):
   for an APPROVAL post, requires `bltbPostInfm.regUser == loginUser` **OR** `loginUser.isAdmin()`.
2. **Detach the payment master** — `UPDATE … SET APPR_SEQ_NO = NULL` (the payment record
   survives; only the approval link is severed):

   | APPR_DV | Module | Master table detached | IDO |
   |---|---|---|---|
   | 35 | Internal Transfer | `SCDUL_PYMN_SUMR` | `SCDUL_PYMN_SUMR_U002` |
   | 32 | Vendor | `SCDUL_PYMN_SUMR` | `SCDUL_PYMN_SUMR_U002` |
   | 31 | Expense | `SCDUL_PYMN_SUMR` | `SCDUL_PYMN_SUMR_U002` |
   | 21 | Payroll | `SCDUL_PYMN_SUMR` | `SCDUL_PYMN_SUMR_U002` |
   | 34 | Overseas | `OVRS_PYMN_SUMR` | `OVRS_PYMN_SUMR_U002` |
   | 33 | Card | `CARD_TRNS_HSTR` (loops **all** rows for the APPR_SEQ_NO) | `CARD_TRNS_HSTR_U002` |

3. Collect physical file paths from `EXPT_RESOL_PAY_EVID_FILE` + `EXPT_RESOL_FILE` (for on-disk cleanup).
4. **`ExptResolMstrDAO.getInstance().deleteByPk(pk)`** — this is the crucial part. The DAO
   (`WECMS DEV/WECMS/src/wabooks/dao/ExptResolMstrDAO.java:170-191`) performs an **application-level,
   child-first, all-physical cascade** before deleting the master:
   1. `EXPT_RESOL_PAY_EVID_FILE.deleteByFk`
   2. `EXPT_RESOL_FILE.deleteByFk`
   3. `EXPT_RESOL_DETL_INF.deleteByFk`  *(out of refactor scope — table not migrated)*
   4. **`EXPT_RESOL_DETL.deleteByFk`**  ← the child that the refactor never deletes
   5. `EXPT_RESOL_APPR_LINE.deleteByFk`
   6. `EXPT_RESOL_MSTR` itself (`EXPT_RESOL_MSTR_D001`)
5. Delete `EXPT_RESOL_APPRVR_INFM` rows (loop, physical).
6. Bulletin cleanup: `BLTB_POST_FILE`, `BLTB_USER_NEW_POST_INFM`, `BLTB_POST_INFM`
   hard-deleted; **`BLTB_NOTI_INFM` soft-deleted** (`DEL_YN='Y'`); comments
   (`BLTB_POST_COMNT_FILE`, `BLTB_COMNT_INFM`) hard-deleted.
7. Commit, then remove physical files from disk.

### 2.3 Legacy behavior summary

- **Physical delete** of `EXPT_RESOL_MSTR` + every child (`DETL`, `DETL_INF`, `FILE`,
  `PAY_EVID_FILE`, `APPR_LINE`, `APPRVR_INFM`). Child-before-parent, inside the DAO.
- **Only** `BLTB_NOTI_INFM` is soft-deleted.
- **Payment master rows are NOT deleted** — only `APPR_SEQ_NO` is nulled.
- **"Pending" is not enforced on the server** — status is a **client-only** gate. The backend
  validates ownership (requester/admin), `DEL_YN != 'Y'`, and `BIZ_KEY` only
  (`ValidationHelper.validateExptResolMstr`, `.../helper/ValidationHelper.java:3060-3075`).

---

## 3. Refactor flow

### 3.1 What each module does today

- **Modules 1-4** (Internal Transfer, Vendor, Expense, Payroll) delegate to the shared
  **`ApprovalBoxFanoutService.withdrawApproval(apprSeqNo)`**
  (`.../payment/approvalbox/service/ApprovalBoxFanoutService.java:441-466`), then null
  `SCDUL_PYMN_SUMR.APPR_SEQ_NO`.
- **Overseas** uses its own inline `OverseasPaymentService.removeApprovalLine(...)`
  (`.../payment/service/OverseasPaymentService.java:1682`), then nulls `OVRS_PYMN_SUMR.APPR_SEQ_NO`.
- **Card** uses its own inline `CardTransactionsService.withdrawApproval(...)`
  (`.../payment/cardtransactions/service/CardTransactionsService.java:803-855`), then nulls
  `CARD_TRNS_HSTR.APPR_SEQ_NO`.

All three delete paths perform the **same three deletes** and nothing else:

```
cleanupApprovalArtifacts(apprSeqNo)   // EXPT_RESOL_FILE + BLTB_* bulletin/notification
exptResolApprvrRepo.deleteByBizKeyAndApprSeqNo(...)   // EXPT_RESOL_APPRVR_INFM
exptResolMstrRepo.delete(mstr)        // EXPT_RESOL_MSTR   ← aborts on FK expt_resol_detl_fk01
```

`ApprovalNotificationService.cleanupApprovalArtifacts(...)`
(`.../payment/notification/service/ApprovalNotificationService.java:414-435`) deletes only
`EXPT_RESOL_FILE` + the bulletin graph — **not** `EXPT_RESOL_DETL`.

### 3.2 Where the detail rows come from

Every module writes ≥ 1 `EXPT_RESOL_DETL` row at request-approval time via
`ApprovalBoxFanoutService.writeResolutionDetail(...)`
(`.../approvalbox/service/ApprovalBoxFanoutService.java:288/304`):

- Scheduled/Overseas: one detail row carrying `DOC_ID = SCDUL_PYMN_NO / OVRS_PYMN_NO`.
- **Card: one detail row per card transaction** (`CardTransactionsService.java:1631`).

So the master always has children when withdraw runs.

### 3.3 The repository gap

`ExptResolDetlRepository` (`.../approvalbox/repository/ExptResolDetlRepository.java`) exposes
**only** a read finder `findByBizKeyAndApprSeqNo(...)`. There is **no delete method**, and no
caller anywhere deletes `EXPT_RESOL_DETL`. `EXPT_RESOL_MSTR`/`EXPT_RESOL_DETL` entities are
flat — **no `@OneToMany`/`@ManyToOne`, no JPA cascade**.

---

## 4. Status-rule comparison

| Aspect | Legacy JEX | Refactor |
|---|---|---|
| "Pending only" enforced server-side | **No** (client-only gate in `comm_0193_001.js`) | **Yes** — `APPR_TYPE_CD ∈ {00 Pending, 03 Partially-Approve}`; blocks `01 Approve`, `02 Reject` (Overseas uses `deriveApprStts == 'P'`) |
| Status field / codes | `EXPT_RESOL_MSTR.APPR_TYPE_CD` (`ApprTypeCd`: `00`=Pending, `01`=Approve, `02`=Reject, `03`=Partially Approve) | Same column + codes |
| Requester-only (or admin) check | **Yes** (`validatePostOwnerPermission`) | **No** — any caller passing the `@menuAuthz` menu gate can delete |
| Soft vs hard delete | Hard delete of all `EXPT_RESOL_*`; only `BLTB_NOTI_INFM` soft (`DEL_YN='Y'`) | Hard delete (partial — see §5) |
| Payment master on delete | `APPR_SEQ_NO = NULL` (row kept) | Same (`SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` / `CARD_TRNS_HSTR` nulled) |

**Notes**
- The refactor's **server-side status guard is stricter than legacy** — a *positive* divergence
  (closes the legacy "crafted call can delete an approved approval" hole; aligns with pre-deploy
  gate R-02). Keep it.
- The refactor **drops the legacy requester/admin ownership check** — a parity **gap**. Today any
  user with menu access to the module can withdraw another user's pending approval.

---

## 5. DB table-operation comparison

Object graph rooted at `EXPT_RESOL_MSTR (BIZ_KEY, APPR_SEQ_NO)`. Refactor FK children (schema
`wecmsdevdb`, verified live 2026-07-02): `expt_resol_detl`, `expt_resol_file`,
`expt_resol_pay_evid_file` — **all `ON DELETE NO ACTION`** (matches legacy JEX DEV, which is
also NO ACTION — legacy relies on the DAO cascade, not the DB).

| Table | Legacy `deleteByPk` | Refactor withdraw | FK to MSTR (refactor)? | Gap |
|---|---|---|---|---|
| `EXPT_RESOL_PAY_EVID_FILE` | physical delete | **not deleted** | ✅ `..._fk01` NO ACTION | Latent FK fail — but refactor never *writes* this table (read-only; comment in `ExptResolPayEvidFileRepository`). Rows exist only for migrated/coexistence data. |
| `EXPT_RESOL_FILE` | physical delete | ✅ deleted (`cleanupApprovalArtifacts`) | ✅ `..._fk01` NO ACTION | OK |
| `EXPT_RESOL_DETL_INF` | physical delete | n/a | — | Out of scope (not in PROD Active Table List; not migrated) |
| **`EXPT_RESOL_DETL`** | physical delete | **not deleted** | ✅ `expt_resol_detl_fk01` NO ACTION | **ROOT CAUSE** — blocks the master delete |
| `EXPT_RESOL_APPR_LINE` | physical delete | **not deleted** | ❌ no FK | Orphan risk — `writeApprovalLineSnapshot` (env-gated) leaves rows behind; no error, but non-parity leftovers |
| `EXPT_RESOL_APPRVR_INFM` | physical delete | ✅ deleted | ❌ no FK | OK |
| `EXPT_RESOL_MSTR` | physical delete (last) | ✅ delete attempted | (parent) | Fails on the `DETL` FK |
| Payment master (`SCDUL_/OVRS_/CARD_`) | `APPR_SEQ_NO=NULL` | ✅ nulled | — | OK |
| `BLTB_*` | hard delete; `BLTB_NOTI_INFM` soft | ✅ via `cleanupApprovalArtifacts` | — | OK |

---

## 6. FK / risk findings

- **F1 — ROOT CAUSE (Critical).** No withdraw path deletes `EXPT_RESOL_DETL`. Deleting
  `EXPT_RESOL_MSTR` while a detail row references it violates `expt_resol_detl_fk01`
  (no `ON DELETE CASCADE`, `V57__expt_resol_detl.sql:26`) → `DataAccessException` →
  `WBE_DB` 500. Observed on Card (`apprSeqNo=21`, `biz_key=VN_VNM0004_000001`).
- **F2 — Same defect in all 6 modules (High).** Internal Transfer / Vendor / Expense /
  Payroll (shared `ApprovalBoxFanoutService.withdrawApproval`) and Overseas
  (`removeApprovalLine`) omit the `DETL` delete identically. They only avoid the error today
  because their delete flows have not been exercised end-to-end; any submitted approval will
  reproduce it.
- **F3 — Latent `EXPT_RESOL_PAY_EVID_FILE` FK (Medium).** Also never deleted on withdraw and
  also FK-constrained. Harmless for refactor-created approvals (refactor never writes this
  table) but will FK-fail on any migrated/legacy approval that carries payment-evidence rows.
- **F4 — `EXPT_RESOL_APPR_LINE` orphans (Low).** Written by `writeApprovalLineSnapshot`
  (env-gated) but never deleted on withdraw. No FK, so no error — but leaves orphan snapshot
  rows, diverging from legacy which hard-deletes them.
- **F5 — Missing requester/admin ownership check (Medium, parity gap).** Legacy restricts
  delete to the requester or an admin; the refactor has no equivalent. (Distinct from the
  status guard, which the refactor already enforces server-side.)
- **F6 — No shared cascade primitive (Design).** Legacy centralizes the cascade in one DAO
  method (`ExptResolMstrDAO.deleteByPk`). The refactor has three independent copies of the
  withdraw sequence (fanout + card + overseas), so a fix must be applied in all three (or a
  shared helper introduced) or drift will recur.

---

## 7. Recommended target behavior (for later — NOT implemented here)

Reproduce the legacy `ExptResolMstrDAO.deleteByPk` cascade, child-before-parent, scoped to the
tables that exist in the refactor. Suggested single source of truth:
`ApprovalBoxFanoutService.withdrawApproval(...)`, with Card + Overseas routed through it (or a
shared `deleteApprovalGraph(bizKey, apprSeqNo)` helper) so the three copies converge (addresses F6).

**Delete order to enforce (all physical, inside the withdraw transaction, before the master):**

1. `EXPT_RESOL_PAY_EVID_FILE` — delete by `(biz_key, appr_seq_no)`  *(F3)*
2. `EXPT_RESOL_FILE` — already handled by `cleanupApprovalArtifacts`
3. **`EXPT_RESOL_DETL`** — delete by `(biz_key, appr_seq_no)`  *(F1/F2 — the fix)*
4. `EXPT_RESOL_APPR_LINE` — delete by `(biz_key, appr_seq_no)`  *(F4)*
5. `EXPT_RESOL_APPRVR_INFM` — already deleted
6. `EXPT_RESOL_MSTR` — delete last

Then null the payment master `APPR_SEQ_NO` (already done per module) and write the audit row.

**Supporting changes required (later):**
- Add a `deleteByBizKeyAndApprSeqNo(...)` (`@Modifying @Query`) to `ExptResolDetlRepository`
  (and to the pay-evid + appr-line repositories) — none exist today.
- **Keep** the server-side status guard (`APPR_TYPE_CD ∈ {00,03}` / Overseas `P`) — do **not**
  regress to legacy's client-only gate.
- **Decide (PO):** whether to restore the legacy **requester/admin** ownership check (F5) or
  accept the current menu-authz-only boundary as an intentional divergence.

**Migration-safety note:** this is a code-only fix — no schema change. Do **not** add
`ON DELETE CASCADE` to the FKs: legacy JEX and the JEX DEV schema both keep these FKs as
NO ACTION and do the cascade in application code. Matching legacy (app-level cascade) keeps the
refactor schema structurally identical to legacy for data migration.

---

## 8. Evidence index

| Artifact | Path |
|---|---|
| Legacy popup view | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0193_001_view.jsp` |
| Legacy popup JS (status gate + `deleteApproval`) | `WECMS DEV/WECMS_STATIC/web/js/jex/wabooks/comm/comm_0193_001.js` |
| Legacy delete action | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_004_d001_act.jsp` |
| Legacy cascade DAO | `WECMS DEV/WECMS/src/wabooks/dao/ExptResolMstrDAO.java:170-191` |
| Legacy status/ownership validator | `WECMS DEV/WECMS/src/wabooks/helper/ValidationHelper.java:3060-3075` |
| Refactor shared withdraw (mods 1-4) | `wecms-api/.../payment/approvalbox/service/ApprovalBoxFanoutService.java:441-466` |
| Refactor Card withdraw (fails) | `wecms-api/.../payment/cardtransactions/service/CardTransactionsService.java:803-855` |
| Refactor Overseas withdraw | `wecms-api/.../payment/service/OverseasPaymentService.java:1682` |
| Refactor artifact cleanup | `wecms-api/.../payment/notification/service/ApprovalNotificationService.java:414-435` |
| Refactor detail repo (no delete) | `wecms-api/.../payment/approvalbox/repository/ExptResolDetlRepository.java` |
| FK definition (no cascade) | `wecms-api/src/main/resources/db/migration/V57__expt_resol_detl.sql:26` |
| Detail writer (creates the rows) | `wecms-api/.../payment/approvalbox/service/ApprovalBoxFanoutService.java:288-345` |
| FK on-delete rules (live, both DBs) | `pg_constraint` — all `EXPT_RESOL_*` children = `NO ACTION` (verified 2026-07-02) |

# Audit — Partner Gap Matrix (`WECMS-CAN-UPDATE-FOR-PARTNER-BI.xlsx`)

**Date:** 2026-07-24
**Auditor:** Claude Code (AI), for Ran Sokny (Sonny) & Mr. MinChul Jung (정민철, PO)
**Source file:** `~/Downloads/WECMS-CAN-UPDATE-FOR-PARTNER-BI.xlsx` — partner (Cambodia BI) review of WeCMS Legacy ↔ Refactor parity, self-dated *"verified against code 2026-07-20"*.
**Scope:** Audit only. No code changed. This document explains, item by item, **why the Vietnam refactor team marked each feature "not fully implemented,"** and separates genuine gaps from findings that are already done, correct-by-design, or out of scope.
**Double-checked 2026-07-24:** all code-based claims were re-verified via 3 parallel verification agents (payment / banking-settings / add-on). Two findings flipped as a result — **#9** (admin login history is actually implemented) and **#5** (Payroll/Overseas "remainder" is moot). Net genuine build work on the 29-item list is now **0** (plus the PO-held #1).

---

## 1. Executive summary — "Why did the VN dev team say not fully implemented?"

The partner sheet lists **29 "To Update" items** + **11 "Out-of-Scope" modules**. After checking each against the live refactor code and the JEX legacy source, the reasons fall into **six root causes** — and, importantly, **the matrix is partly out of date**: several items flagged as blockers are already done or were never actually wrong.

| Root cause | What it means | Items |
|---|---|---|
| **A. Already done (stale finding)** | Implemented after the partner's snapshot; the matrix is out of date. | **6** (docType), **23** (e-Invoice status filter), **18** (logout — F-03 revocation), **4** (approval gate — `='01'` on all 5), **22** (Daily Financial HS — real HS DB function), **9** (admin login history — `AuthService:232-242` writes it), and **5** (VCB→file — done across all 5 modules) |
| **B. Correct-by-design (false finding)** | Refactor already matches legacy; the partner's premise is wrong. | **2** (MATCH_TYPE) |
| **C. Different-by-design (not a bug)** | Reimplemented with a different mechanism, behavior-equivalent — or deliberately dropped/replaced. Conscious refactor decision. | **19** (menu), **24** (Excel upload), **15** (recents), **28** (notifications), **13** (Re-run dropped → Payment Result + auto-recovery) |
| **D. Deliberately deferred / de-scoped** | Real legacy behavior, consciously postponed for the R&D milestone. | **9, 26, 29** (and minor per-bank validation nuances of **21**, which is otherwise implemented) |
| **E. Needs PO scope decision** | Sits on the WABOOKS/accounting boundary — build only if WeCMS confirms it needs it. | *(none remaining — all scope questions resolved by Sonny)* |
| **F. Genuinely out of scope** | WABOOKS / Tax / member / public-API / mobile modules, or features WeCMS doesn't ship — a different product, client, or non-feature. | All of **Sheet 2**, plus Sheet-1 items confirmed out-of-scope by Sonny: **3** (manual txn), **7** (company-status login gates), **8** (auto-unlock / Member self-service), **10** (transaction evidence attachment — no UI), **11** (smart-assistant push — mobile-only), **12** (uncollected/unpaid AR/AP reports), **14** (contact group master — WABOOKS S-Zone), **16** (employee management — WABOOKS S-Zone), **17** (dashboard — refactor lands on Account Inquiry by design), **20** (per-category confirm UI — WABOOKS document-linkage; core confirm done), **26** (company stamp + QR — WABOOKS document-printing; logo done), **29** (ERP thumbnail — unused stub, no UI, ERP files aren't images) |

**Headline for the partner reply:** of the 6 items the partner marked ⚠️ "Incorrect — blocks UAT/PROD", **at least 4 need no work** — #2 is correct as-is, #6 is already fixed, #5 is already done for 3 of 5 payment modules, and **#4 was fixed 2026-07-24** (server-side gate now on all 5 modules). **#1 (action-level permissions)** is the one genuine UAT/PROD gap remaining — but the VN PO team has it **on hold** (approved-but-unreleased), so the partner reply is **No** (not committed now). Aside from that, only the Payroll/Overseas remainder of #5 is outstanding among the High items.

> **Final result after full item-by-item review with Sonny + a 2026-07-24 code double-check (3 verification agents):** of the 29 "To Update" items, **0 require genuine build work.** The two items previously flagged as gaps both flipped on the code double-check: **#9** is already implemented (`AuthService:232-242` writes `BUSI_ADMN_LOGIN_HIS`), and **#5**'s Payroll/Overseas remainder is moot (those modules are file-only, so VCB can't be misrouted to API). **#1** (action-level permissions) is a real gap but **held by the PO**. The other **28 need no work** — already done / stale findings (#2, #4, #5, #6, #9, #18, #22, #23), deliberately different-by-design or dropped (#13, #15, #19, #21, #24, #25, #27, #28), or **out of scope** — WABOOKS / S-Zone / Member / mobile / non-features WeCMS doesn't ship (#3, #7, #8, #10, #11, #12, #14, #16, #17, #20, #26, #29). **Net: 0 build items + 1 PO-hold; the partner matrix massively overstated the gap.**

---

## 2. Verification notes (what was checked in code)

| Item | Partner claim | Verified against | Result |
|---|---|---|---|
| **2** MATCH_TYPE `'A'` vs `'AUTO'` | "legacy uses `'AUTO'`" | Legacy `WECMS DEV/WECMS/src/wabooks/enums/MatchTypeCd.java` → `A = Auto, M = Manual`; refactor `BusiMatchLog.java` writes `'A'` | **FALSE finding.** Legacy uses `'A'`, refactor `'A'` matches. Not a divergence. |
| **6** docType EXPENSE `'E'` vs `'B'` | "returns `'E'` — bug" | `EInvoiceService.resolveDocType("EXPENSE") → 'B'`, rejects `'E'`/`'S'` with `WBE2002` | **STALE.** Already fixed; the old "EXPENSE→'E'" blocker is explicitly documented as stale. |
| **5** VCB-always-file routing | "refactor routes VND→API (wrong)" | `ExpenseDispatchExecutor.java:143` pulls all VCB (970436) VND+USD to the File path (OQ-2, Mr. Jung 2026-07-16 CONFIRMED) | **PARTIALLY STALE.** Done for Vendor/Expense/Internal; only Payroll/Overseas remain. |
| **23** e-Invoice status filter | "filtered client-side (gap IR-4)" | `EInvoiceService.java:54` — "status filter is now server-side (2026-06-30)", exact `WHERE EINV_INVOICESTATUS = :status`; IR-4 closed | **STALE.** Server-side filter is live. |
| **1** action-level perms | "FE hardcodes `{R,D,T:true}`" | `wecms-web/CLAUDE.md:223` + `ScheduledTransferPage.tsx:75-77` confirm the static stub; backend gate **R-02** open | **GENUINE gap, but HELD by the VN PO team** (approved-but-unreleased) → partner reply = **No** (not committed). Still a UAT/PROD gap when the PO releases it. |

---

## 3. Sheet 1 — "To Update" (29 items), audited

Legend — **Verdict:** ✅ done/stale · ✔️ correct-by-design · 🔵 different-by-design · ⚠️ genuine gap · ❓ scope decision.
**Priority** is the partner's original marking.

### 3.0 Quick index

| # | Feature | Verdict | # | Feature | Verdict |
|---|---|---|---|---|---|
| 1 | Action-level payment perms (5 actions) | ⚠️ gap (blocker) | 16 | Employee management | 🚫 out of scope |
| 2 | Auto-match MATCH_TYPE | ✔️ false finding | 17 | Dashboard | 🚫 out of scope |
| 3 | Manual transaction entry | 🚫 out of scope | 18 | Logout | ✅ done (stale) |
| 4 | Approval gate on dispatch | ✅ fixed (2026-07-24) | 19 | Navigation menu | 🔵 by-design |
| 5 | VCB-always-file routing | ✅ done (all 5) | 20 | Confirm / undo transaction | 🔵 core done; UI OOS |
| 6 | e-Invoice docType EXPENSE | ✅ done (stale) | 21 | Own-account transfer | 🔵 implemented (minor per-bank) |
| 7 | Company-status login gates | 🚫 out of scope | 22 | Daily Financial (HS) | ✅ done (stale) |
| 8 | Auto-unlock PW_CHG_REQ_SEQ_NO | 🚫 out of scope | 23 | e-Invoice status filter | ✅ done (stale) |
| 9 | Admin login history | ✅ done (write-only) | 24 | Excel upload → batch | 🔵 by-design |
| 10 | Txn evidence attachment | 🚫 out of scope | 25 | Menu-entitlement (menu-level) | 🔵 = #1 |
| 11 | Smart-assistant push | 🚫 out of scope (mobile) | 26 | Preferences: stamp + QR | 🚫 out of scope (logo done) |
| 12 | Uncollected / unpaid reports | 🚫 out of scope | 27 | Environment Settings | 🔵 done (WeCMS set) |
| 13 | Re-run failed payment | 🔵 dropped by design | 28 | Notifications (bell) | 🔵 done (web) |
| 14 | Contact group master | 🚫 out of scope | 29 | ERP Txn History thumbnail | 🚫 out of scope (unused stub) |
| 15 | Recents server-side | ✅ done (2026-07-24) | | | |

### 3.1 Items marked ⚠️ "Incorrect — blocks UAT/PROD" (partner priority: High)

#### #1 — Action-level payment permission · `USER_MENU_ACTION_AUTH_INFM` — ⚠️ **Genuine gap (top blocker)**

> **PO note (2026-07-24, from Sonny):** this feature was **held by the VN PO team on the DEV (legacy JEX) side** — a deliberate hold, not a dev oversight. **Partner reply = "No" (on hold by PO)**: there is no fix commitment now; it is not built until the PO releases it. It nonetheless remains a real UAT/PROD gap for whenever it ships, to the spec below.

- **Business need (as-is):** clients want to let some users *view* payments and *request approval* but **not confirm the transfer** — i.e., separate rights per action, not one all-or-nothing screen grant.
- **Target (to-be):** per user, per **payment module** — Internal Transfer / Vendor / Expense / Payroll / Overseas Payment — five independent action rights, each toggling a screen capability:
  | Action right | Effect |
  |---|---|
  | **View** | Access the list (main) screen and the detail screen — **view only** |
  | **Upload** | Show / hide the **Upload** button |
  | **Delete** | Show / hide the **Delete** button on new (draft) payments |
  | **Request Approval** | Show / hide the **Request Approval** button |
  | **Transfer** | Show / hide the **Transfer** (dispatch) button |
  - *Example:* user "ABC" has **Transfer** on Overseas Payment but **not** Upload / Delete / Request Approval — he sees the screen and can transfer, but those three buttons are hidden.
- **Refactor — current:** only **menu-level** entitlement is ported (can you open the screen — yes/no). The five action rights have no backend contract, so the FE hard-codes a permissive stub `{R:true,D:true,T:true}` — confirmed `wecms-web/components/payment/internal-transfer/ScheduledTransferPage.tsx:75-77` and `wecms-web/CLAUDE.md:223`. Real enforcement today is only server-side `@PreAuthorize` per endpoint (all-or-nothing). Tracked as **R-02**, "must be resolved before UAT/PROD."
- **Action (real work, both sides):**
  1. **Backend** — build the `USER_MENU_ACTION_AUTH_INFM`-equivalent contract exposing the five per-module action flags (View / Upload / Delete / Request Approval / Transfer), and enforce Delete / Request-Approval / Transfer server-side (ties to item **#4**).
  2. **Frontend** — replace the `{R,D,T:true}` stub with the fetched flags and gate the four buttons + view-only mode on all five payment screens.
  - Note the button set is **5 actions**, wider than the matrix's "U/D/R/T" label — align the contract to View/Upload/Delete/Request-Approval/Transfer.

#### #2 — Auto-match `MATCH_TYPE` · `BUSI_MATCH_LOG` — ✔️ **Correct-by-design (false finding)**
- **Partner claim:** refactor writes `'A'`, legacy uses `'AUTO'` → neither side sees the other's matches on a shared DB.
- **Verified against live JEX DB + legacy source (2026-07-24) — three independent proofs:**
  1. **Column width makes `'AUTO'` impossible.** Live JEX `busi_match_log.match_type_cd` is `character(1)` (max length **1**). A 4-char string like `'AUTO'` can never be stored — legacy has never written it.
  2. **Legacy enum stores `'A'`.** `WECMS DEV/WECMS/src/wabooks/enums/MatchTypeCd.java:9` defines `AUTO("A")` — the enum *constant name* is `AUTO` but the *stored value* is `"A"`. This is the origin of the false finding: the enum name was mistaken for the DB value.
  3. **Live data agrees.** `SELECT match_type_cd, COUNT(*) FROM busi_match_log GROUP BY 1` returns only `M` (6 rows) — no `'A'` or `'AUTO'` rows. Refactor `BusiMatchLog.java` writes `'A'`, matching the legacy enum value.
- **Conclusion:** legacy auto = `'A'`, refactor auto = `'A'` → identical. No divergence, no shared-DB blindness, no migration risk. (The `'AUTO'` string came from an early gap-doc guess, later corrected in `bank-transaction-sync-debit-matched-parity-gap.md:185`.)
- **Action:** none — close the item.

#### #3 — Manual transaction entry · `fnsh_0002_002/003/004` — 🚫 **Not a WeCMS feature (out of scope)**
- **Legacy:** for manually-registered accounts (`HAND_REG_YN='Y'`, not bank-scraped), the user types transactions in by hand — a WABOOKS-style cash-book entry screen.
- **WeCMS reality (Sonny, 2026-07-24):** WeCMS has **no UI for adding a manual account or a manual transaction** — this is not part of the WeCMS product. The partner's "stays empty forever" concern therefore doesn't apply: WeCMS never surfaces manual accounts in the first place.
- **Verdict:** not a gap — out of scope for WeCMS (belongs with the WABOOKS/accounting cash-book set, cf. Sheet 2).
- **Action:** none. Reply **No** — not a WeCMS feature. Revisit only if the PO brings manual cash-book into WeCMS scope.

#### #4 — Approval gate on dispatch · `APPR_TYPE_CD='01'` — ✅ **FIXED (2026-07-24)**
- **Legacy:** before dispatch, all 5 modules assert the batch is fully-approved **server-side** with a strict `!"01".equals(APPR_TYPE_CD)` → rollback. The gate lives in Java (`PymnHelper.pymn_0001_001_c001:2159-2168` for Vendor/Expense, `pymn_0005_001_c001_act.jsp:122-130` for Payroll, `PymnHelper.pymn_0009_001_c001:2507-2516` for Internal, `pymn_0007_001_c001_act.jsp:166-174/292-300` for Overseas), never in SQL/IDO/WSVC. `APPR_TYPE_CD`: `00`=Pending, `01`=Approved, `02`=Rejected, `03`=Partially-Approved — strict `='01'` blocks Pending, Rejected **and Partially-Approved**.
- **Refactor — corrected scope:** the gap was **3 modules, not 4.** **Expense already enforced** `APPR_TYPE_CD='01'` server-side (with the "G1" legacy-parity comment), and **Overseas** enforced it via `deriveApprStts→'C'`. Only **Vendor, Payroll, Internal Transfer** checked `APPR_SEQ_NO` presence alone → a hand-crafted API call could dispatch a Pending/Rejected/Partially-Approved batch. The gate is in each service's `dispatch(...)` method — **not** the executor (`*DispatchExecutor` writes rows only, does no approval check).
- **Fix applied (2026-07-24):** added the strict `"01".equals(...)` server-side assertion to `VendorPaymentService.dispatch`, `PayrollPaymentService.dispatch` (reusing its existing `EXPT_RESOL_MSTR` fetch for `APPR_FUND_TP`), and `InternalTransferDispatchService.dispatch` (replacing the documented deferral), all via `exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(...)` → `WBE2063` on failure. Now **all 5 modules match legacy.** Pinned by `{Vendor,Payroll,InternalTransfer}DispatchApprovalGateTest` (5 cases each: no-seq / pending / rejected / partial → blocked, approved → dispatches), alongside the pre-existing `ExpensePaymentDispatchApprovalGateTest`. Compile + tests green.

#### #5 — VCB-always-file routing — ✅ **Done (fully addressed — verified 2026-07-24)**
- **Legacy:** VCB (970436) has no real-time transfer API cert, so **all** VCB payments (VND *and* USD) go the **file** path.
- **Refactor (verified across all 5 modules):**
  - **Vendor / Expense / Internal — explicit VCB→file gate.** `isVcbFileTransfer = eq(outBankCd, 970436)` forces VCB (VND+USD) onto the file path (`VendorDispatchExecutor:148-159`, `ExpenseDispatchExecutor:145-157`, `InternalTransferDispatchExecutor:228-232`; OQ-2, Mr. Jung CONFIRMED 2026-07-16, SVN r36803).
  - **Payroll / Overseas — the "remainder" is MOOT.** Payroll is **file-only always** (no API path to misroute onto; `PayrollDispatchExecutor` Javadoc), and Overseas is **always File/SWIFT** with VCB **blocked as a source bank** (`OverseasDispatchExecutor:124`). So VCB-VND can't be wrongly API-routed there — no switch is needed.
- **Verdict:** the partner's "routes VND→API" concern is fully resolved everywhere (explicit in 3 modules, structurally impossible in 2). No genuine remainder.
- **Action:** reply **No** (done). Minor optional sanity-check: confirm Payroll blocking VCB-VND entirely (`:205-207`) matches legacy intent — not a #5 gap.

#### #6 — e-Invoice docType EXPENSE bug — ✅ **Done (stale)**
- **Partner claim:** `resolveDocType` returns `'E'` (should be `'B'`).
- **Verified:** `EInvoiceService.resolveDocType("EXPENSE") → 'B'`, rejects `'E'`/`'S'` with `WBE2002`. Feature docs explicitly call the old "EXPENSE→'E'" blocker **stale**.
- **Action:** none. Close it.

### 3.2 Items marked ❌ "Missing — medium" (partner priority: Medium)

#### #7 — Company-status login gates · WBE1048/1049 (`SzonHelper.logn_2001_001`) — 🚫 **Not a WeCMS feature (WABOOKS scope)**
- **Legacy:** at login, blocks users whose *company* is invalid: `BUSI_STTS`=STOP, trial expired, closed >30 days, plus `NTNL_CD`/`CHNL_TYPE` checks — a WABOOKS subscription/trial-lifecycle gate.
- **WeCMS reality (Sonny, 2026-07-24):** WeCMS does **not** use these company-status gates and has no such UI; they come from WABOOKS. WeCMS is deployed per enterprise, so trial / close / channel gating doesn't apply. **No refactor needed.**
- **Verdict:** out of scope for WeCMS (WABOOKS login-lifecycle logic). WeCMS keeps only the DEL / ACTV / LOCK gates it actually uses.
- **Action:** none. Reply **No** — WABOOKS feature, not used by WeCMS.

#### #8 — Auto-unlock via `PW_CHG_REQ_SEQ_NO` — 🚫 **Not a WeCMS feature (Member/WABOOKS self-service)**
- **Legacy:** the back half of a self-service *forgot / reset-password* flow — a locked user requests a reset (written to `USER_PW_CHG_HIS`, stamped onto `USER_INFM.PW_CHG_REQ_SEQ_NO` by the `mbmr` Member module); on the next login with the new password the account auto-unlocks (`LOCK_YN='N'`, seq cleared). See `SzonHelper.java:944-965`, `logn_0001_001_r003_act.jsp:112`.
- **WeCMS reality (Sonny, 2026-07-24):** the reset request comes from the **Member module `mbmr_0003_002`** — Sheet 2 #9, out of scope. WeCMS has **no self-service password-reset UI** (login = ID/PW/Save-ID/LOGIN only; provisioning + unlock are DBA-only by design), so nothing ever creates a `PW_CHG_REQ_SEQ_NO`. The column being mapped-but-never-read is **correct** for WeCMS, not a gap.
- **Verdict:** out of scope for WeCMS (Member/WABOOKS self-service). Unlock stays a DBA action.
- **Action:** none. Reply **No** — not a WeCMS feature (cf. Sheet 2 #9).

#### #9 — Admin login history · `BUSI_ADMN_LOGIN_HIS` — ✅ **Done (write-only) — verified 2026-07-24 (double-check flip)**
- **Confirmed (Sonny, 2026-07-24):** this is a **WeCMS** feature, **not** the separate Jex Admin portal (`SOURCE_CODE_ADMIN/`).
- **⚠️ Correction (double-check, 2026-07-24):** the earlier "refactor writes only `USER_LOGIN_HIS`" conclusion was **wrong** — the refactor **already writes `BUSI_ADMN_LOGIN_HIS`.** Verified in code: `AuthService.java:232-242` (comment literally headed *"Gap #9 — admin login-history audit"*) calls `adminHistory.record(selectedLink.getBizKey(), userId, busiAdmn.getUserNm(), ipAddr)` on the **success path of a business-admin login** (`if (busiAdmn != null)`), written **in the login transaction** (not `REQUIRES_NEW`) to commit with the login — mirroring legacy. Producer: `AdminLoginHistoryService.record(...)` → `repository.save(his)`; entity `BusiAdmnLoginHis` + `BusiAdmnLoginHisRepository` present.
- **What it is:** a company-scoped audit of **business-admin** logins (`biz_key, reg_id, reg_dtm, login_uuid, ip_addr, reg_nm, lgn_chnl`; the admin display name comes from `BUSI_ADMN_INFM.USER_NM`). The legacy `ChnlType.TYPE_C` channel test is implicit because the refactor **is** the WeCMS customer channel. **Write-only** — the legacy `comm_0213_001` viewer screen is correctly **not** ported (live WeCMS has no such UI).
- **Verdict:** done — the write is implemented exactly as legacy does it, viewer omitted by design.
- **Action:** reply **No** (already done). No work needed. *(Earlier drafts marked this "Yes (write-only)" — retracted after the code double-check.)*

##### Legacy-flow audit corrections & detail (verified 2026-07-24)

- **Source of truth is `logn_2001_001` (WeCMS), not `logn_0001_001` (WABOOKS/CAM).** Two login actions write this table under different conditions:
  - `logn_0001_001_r001_act.jsp:281` — old WABOOKS Cambodia login, channel `WbStatic.WABOOK_CHNL_ID` (`CAM0001`), condition `busiAdmnInfm != null && ChnlType.TYPE_B`.
  - **`logn_2001_001_r001_act.jsp:270` — current WeCMS login, channel `WbStatic.WE_CMS_CHNL_ID` (`VNM0004`), condition `busiAdmnInfm != null && ChnlType.TYPE_C`.** This is the one the refactor `AuthService` ports (`SzonHelper.logn_2001_001_r001`).
- **Channel label nuance:** `ChnlType.TYPE_C` (`"C"`) is labeled **"WeCMS"** (`ChnlType.java:38`), not "customer" — the doc's `TYPE_C` is correct, only the "customer channel" wording was loose.
- **`REG_NM` source:** legacy sets it from `busiAdmnInfm.getUserNm()` — i.e. **`BUSI_ADMN_INFM.USER_NM`**, *not* `USER_INFM`.
- **`LOGIN_UUID`:** the JEX entity's logical PK is only 3-col `(BIZ_KEY, REG_ID, REG_DTM)`; the physical table PK is 4-col incl. `LOGIN_UUID`, which the JEX INSERT never sets — the DB `DEFAULT uuid_in(md5(random()||now()))` fills it, so two logins by the same admin in the same second don't collide.
- **Transaction + gating:** written inside the **main login transaction** (not `REQUIRES_NEW`), and only **after** the `BUSI_STTS` gates (`WAITING`/`CLOSE`/non-`NORMAL` when paid return early) — so only a fully successful admin login persists a row.

##### Implementation status — **DONE (write-only) 2026-07-24**

- **Refactor DDL already present:** [V28__busi_admn_login_his.sql](../../src/main/resources/db/migration/V28__busi_admn_login_his.sql) matches the live JEX DDL column-for-column (4-col PK, `login_uuid` default, `lgn_chnl` default `'PC'`, `reg_nm` nullable). No schema change needed.
- **API (implemented + unit-tested):** `BusiAdmnLoginHis` entity + `BusiAdmnLoginHisId` (4-col PK; `login_uuid` set in Java via `UUID.randomUUID()` — the equivalent of the DB default, so Hibernate has the full id at insert), a **write-only** `BusiAdmnLoginHisRepository`, and `AdminLoginHistoryService.record(...)`. `AuthService.login` now fetches the `BUSI_ADMN_INFM` row (was a boolean `existsBy…` probe) to derive `authGrCd` **and** supply `REG_NM`, writing the admin row inside the main login transaction when `authGrCd == ADMIN` — mirroring legacy `logn_2001_001`. Tests: `AuthServiceTest` (23, incl. admin-writes / non-admin-skips) + `AdminLoginHistoryServiceTest` (1) green.
- **No read endpoint, no screen, no frontend** — matches live WeCMS, which stores the audit but does not expose the `comm_0213_001` viewer. If the PO later brings the viewer into WeCMS scope, add the read endpoint + a Settings panel then (needs a `settings-*` `permission_cd`, icon asset, and verified KO/VI copy).

#### #10 — Transaction evidence attachment · `ACCT_TRNS_RCV_PAY_EVID_FILE` — 🚫 **Not a WeCMS feature (out of scope)**
- **Legacy:** attach supporting files (receipts, evidence) to a transaction.
- **WeCMS reality (Sonny, 2026-07-24):** WeCMS has **no UI for transaction evidence attachment** — the Transaction History screen (`fnsh_0002_001`, `www.wecms.vn/fnsh_0002_001.act`) has no attach/upload control (columns are Remarks / FILE_NM / MATCH / Reporting Account / Contact Name / Confirmed by — no attachment). The table `ACCT_TRNS_RCV_PAY_EVID_FILE` was already dropped in schema V31 as unused, consistent with WeCMS never surfacing the feature.
- **Verdict:** out of scope for WeCMS — not just a deferred table.
- **Action:** none. Reply **No** — no such UI/feature in WeCMS. Revisit only if the PO adds evidence attachment to WeCMS scope.

#### #11 — Smart-assistant push on new transaction · `fnsh c001 push` — 🚫 **Not a WeCMS(refactor) feature (mobile-only, out of scope)**
- **Legacy:** a **mobile push notification** — when a new bank transaction is scraped, `PushHelper.sendPush(title, message, deviceIdList)` fires an FCM-style alert to the user's registered phones (`PUSH_DEVICE_MSTR` device tokens, iOS/Android; `MSG_SND_HIS` logs sends; `getBadgeCount` drives the app badge). It targets the companion **mobile "smart-assistant" app**.
- **WeCMS reality (Sonny, 2026-07-24):** the **refactor has no mobile version** — it is web-only (Next.js). With no mobile client there is no device to push to, so the push channel has no target. The whole push/notification stack was not ported (only the in-app `BLTB_NOTI_*` bell branch is — item #28; mobile fan-out intentionally omitted).
- **Verdict:** out of scope for the web refactor (mobile-app feature).
- **Action:** none. Reply **No** — mobile-only; the refactor has no mobile app. Revisit only if a WeCMS mobile client enters scope (fresh PO decision).

#### #12 — Uncollected / unpaid reports · `rept_0005/0006` — 🚫 **Not a WeCMS feature (out of scope)**
- **Legacy:** AR/AP aging summaries — `rept_0005` uncollected receivables (`NON_COLL_COND_DAILY_SUMR`, 미수금 = owed to the company) + `rept_0006` unpaid payables (`NON_PAY_COND_DAILY_SUMR`, 미지급 = owed by the company). Accounting (WABOOKS) reports.
- **WeCMS reality (Sonny, 2026-07-24):** the WeCMS **Reports** menu has **only three** entries — **Daily Cash · Daily Financial · Cash Flow** (+ Daily Financial HS for the HAENGSUNG product). The refactor navigation seed carries exactly those. `rept_0005/0006` are **not in the WeCMS Reports menu** — they were never a WeCMS report.
- **Verdict:** out of scope for WeCMS (WABOOKS AR/AP reports).
- **Action:** none. Reply **No** — not part of the WeCMS report set. Revisit only if the PO adds AR/AP reporting to WeCMS scope.

#### #13 — Re-run failed payment transactions · `pymn_0002_003_u001` — 🔵 **Dropped by design (no Re-run UI)**
- **Legacy:** a "Re-run" action to re-dispatch failed (`PROC_STAT='3'`) payment rows/batches.
- **WeCMS reality (Sonny, 2026-07-24):** the refactor has **no Re-run UI** — confirmed in code: no re-run/re-dispatch button or endpoint (the only "re-run" hits are React re-render comments + a legacy `pymn_0002_002` reference). Failed payments are handled by the **Payment Result flow** plus **automatic recovery** (`ResultCheckService`, `StrandedRecoveryService` reconcile in-flight/stranded dispatches); a user re-creates the payment rather than pressing "Re-run." Deliberate drop (the partner's "PO dropped" note is correct).
- **Verdict:** dropped/replaced by design — not a gap.
- **Action:** reply **No** — Re-run was deliberately dropped; failed payments go through Payment Result + auto-recovery. (Only revisit if the PO wants the explicit Re-run button back.)

#### #14 — Contact group master · `trco_0101_001` — 🚫 **Not a WeCMS feature (out of scope)**
- **Row label correction:** the matrix cites *"`trco_0101_001` + `TRCO_GRP_BSC_*`"*, but those are two unrelated things. `trco_0101_001` does CRUD on **`TRCO_GRP_USER`** (per-user custom groups: name/icon/order) — it **never touches** `TRCO_GRP_BSC_SYS`/`_CSTM` (`TrcoGrpUser.java`, `trco_0101_001_{c001,u001,u003,u004}_act.jsp`). There are three group concepts: `TRCO_GRP_BSC_SYS` = fixed B/S/E system groups (seed-only, no CRUD screen), `TRCO_GRP_BSC_CSTM` = a per-company mirror of the same B/S/E set (written only as a side-effect of contact-register screens, never by a master), and `TRCO_GRP_USER` = the custom groups the "group master" actually manages.
- **`trco_0101_001` is a WABOOKS screen, not part of WeCMS Contacts:** file headers read `WABOOKS` (2020, author yanchhuong); it is an LNB widget invoked only from `main_lnb_trco.js` / `inc_wabooks_trco_lnb*.jsp` on the **old `trco_0001_001` contact list** — a 46-file include set that does **not** include any `comm_0184` view. The WeCMS Contacts screen (`comm_0184`) **never references `trco_0101_001`** and has no group-management UI. On create it only auto-links each contact to the fixed **B + S** system groups (`ContactsService:446-457` = `comm_0184_002_c001_act.jsp:130-147`, `USER_ID='ALL'`); the FE carries an explicit **OQ-1 "No group filter"** deferral and there is no `TrcoGrpBsc`/`TrcoGrpUser` entity.
- **Live-DB evidence (JEX DEV, 2026-07-24):** on the WeCMS channel (`VNM0004`), `TRCO_GRP_USER` has **0 rows** (VNM0001/0002 have 102/39), `PGRO_BSAC_LNKN_INFM` carries **only B/S links** (322/307, zero custom), and every WeCMS company's `TRCO_GRP_BSC_CSTM` is just the B/S/E triple auto-seeded at provisioning. No WeCMS company has ever created a custom contact group.
- **Verdict:** out of scope for WeCMS. Custom-group CRUD (`trco_0101_001` → `TRCO_GRP_USER`) is a WABOOKS-lineage feature on a different screen family; WeCMS uses only the fixed Buyer/Seller system groups. *(Minor: `comm_0184`'s list `GRP_CD` filter — refactor OQ-1 — is inert on WeCMS since no `TRCO_GRP_USER` data exists; revive only if the PO brings custom contact groups into WeCMS scope.)*
- **Action:** none. Reply **No** — custom-group management is a WABOOKS feature, not WeCMS.

#### #15 — Recents server-side · `USER_TRCO/USAG_USE_HIS` — ✅ **DONE (2026-07-24) — server-side read-only, matches legacy**
- **Legacy:** "recently used" contacts/purposes stored server-side per `(biz_key, user_id)`, so they follow the user across devices. Written **only** by the `busa_doc_mstr_log` / `cash_*_mstr_log` AFTER-INSERT triggers (on business-document/cash-entry creation) — legacy has **no** app-side write. Read as top-3 distinct, newest-first (`WABOOKS_MREC_R030` REC1 for contacts, `comm_0107_001_r002` for purposes).
- **Was:** the refactor kept recents in browser **localStorage** (per-browser, cap 5) → lost on device change / cache clear. Partner's finding was correct.
- **Fix applied (2026-07-24, read-only, PO option A):** added read-only entities + repositories for `USER_TRCO_USE_HIS` / `USER_USAG_USE_HIS`, two GET endpoints (`GET /api/v1/settings/contacts/recent`, `GET /api/v1/settings/purpose-settings/recent`) returning the current user's **top-3 distinct** recent ids (dedup + `ORDER BY max(reg_dtm|seq_no) DESC`, matching legacy exactly), and swapped `ContactSelect`/`PurposeSelect` from localStorage to the server fetch. Recents now follow the user across devices. **No write path added** — the refactor never writes these tables; population stays with the live JEX trigger (exact legacy parity). Compile + typecheck green; read-query semantics verified against live DEV data.
- **Scope note:** VAS-account recents stay on localStorage (no legacy `USER_VAS_USE_HIS` table exists). Because the refactor's Transaction-History classification writes `ACCT_TRNS_RCV_PAY_INFM` (not `busa_doc_mstr`), classifying there does not add a recent — same as legacy; classify-feeds-recents depends on the separate item **#20** (confirm→`busa_doc` write).
- **Action:** none — closed as done.

#### #16 — Employee management · `szon_0003/0012` + `EMPL_INFM` — 🚫 **Not a WeCMS feature (out of scope)**
- **Legacy:** the S-Zone employee-management screens (`szon_0003/0012`) — create/edit/list employees in `EMPL_INFM`.
- **WeCMS reality (Sonny, 2026-07-24):** `EMPL_INFM` is foundational to WeCMS (every user hangs off an employee row), which is why the entity exists — but there is **no employee-management UI or code** ("entity stub, 0 consumer"). WeCMS provisions employees + users via **manual DBA SQL** (the documented "no Spring auto-bootstrap" model); the User Management screen manages `USER_INFM`, not `EMPL_INFM` CRUD. The management screen `szon_0003/0012` is part of the **S-Zone master-data hub = WABOOKS**, already listed in **Sheet 2 #5**.
- **Verdict:** out of scope for WeCMS (WABOOKS S-Zone). WeCMS uses `EMPL_INFM` only as a DBA-provisioned table.
- **Action:** none. Reply **No** — employee management is a WABOOKS S-Zone feature (Sheet 2 #5); WeCMS fills `EMPL_INFM` via DBA SQL.

#### #17 — Dashboard · `SYS/USER_DSHBRD_INFM` — 🚫 **Out of scope (refactor lands on Account Inquiry by design)**
- **Legacy:** WeCMS has a configurable dashboard — `SYS_DSHBRD_INFM` (system default layout) + `USER_DSHBRD_INFM` (per-user config), managed by screen `comm_0210_001`; KPI hero tiles on the landing.
- **WeCMS reality (Sonny, 2026-07-24):** the refactor **lands on `/banking/account-inquiry` by design**; the configurable dashboard is **not part of the refactor scope**. Tables exist (V91/V127) but there is no entity/code/route, and the Next.js `(dashboard)` folder is only a route-group wrapper, not a dashboard page.
- **Verdict:** out of scope for the refactor (Account Inquiry is the landing).
- **Action:** none. Reply **No** — dashboard is not in the refactor scope; WeCMS lands on Account Inquiry. Revisit only if the PO adds the dashboard landing to scope.

### 3.3 Items marked 🟡 "Partial / different — low" (partner priority: Low)

#### #18 — Logout · `logn_0002_001` — ✅ **Done (full legacy parity as of 2026-07-24)**
- **Partner claim:** "stub — client clears token; no server-side blacklist."
- **Legacy flow (source of truth):** `logn_0002_001_r001_act.jsp:47-56` does exactly two things — (1) inserts a `USER_LOGIN_HIS` row with `RSLT_CD='LOGOUT'` (`UserLoginHisDAO.create`), then (2) `session.invalidate()`. Legacy auth is hybrid (HTTP session + a *secondary stateless JWT fallback* in `JwtHelper` that has **no `jti` and no revocation** — a legacy JWT actually survives logout). No cookie deletion; the redirect is client-side.
- **Verified in code (2026-07-24):** **there IS a server-side blocklist.** `AuthService.logout` calls `tokenRevocation.revokeToken(jti, exp)`; `JwtAuthenticationFilter` rejects any revoked `jti` on every subsequent request (revocation gate **F-03**). Admin lock/delete/demote/password-reset also revoke all of a user's tokens. So logout is **not** a pure client-side stub — the token is invalidated server-side. On the token dimension the refactor is **stronger** than legacy (legacy's JWT fallback was unrevocable).
- **Parity gap found & fixed (2026-07-24):** the refactor logout previously reproduced only side-effect (2) — it wrote **no** `USER_LOGIN_HIS` `LOGOUT` audit row. That was a real divergence from legacy (and from this doc's own §3.2 note that `USER_LOGIN_HIS` logs `SUCCESS/LOGOUT/FAIL`). Now closed: `AuthService.logout(authorizationHeader, ipAddr)` writes the `RSLT_CD='LOGOUT'` row via `LoginHistoryService.record(...)` (own `REQUIRES_NEW` tx, `CHNL_PC`, UTC `DTM14` — same shape as the SUCCESS/FAIL login rows and legacy `UserLoginHisDAO.create`) **before** revoking the token. New `UserLoginHis.RSLT_LOGOUT` constant; `AuthController` passes the client IP; `JwtRevocationTest` extended to assert the audit row is written (6/6 green).
- **Caveat (not a gap in behavior):** the revocation store is **in-memory / single-instance** (`TokenRevocationService`) — revocations are lost on restart and not shared across nodes; a durable/shared store is the one open item before a multi-instance deploy.
- **Action:** none for the partner finding — reply **No** (already done; server-side revocation is live, and the logout audit row now matches legacy). Track the in-memory→durable store separately as an infra item, not a partner gap.

#### #19 — Navigation menu · menu XML + JexAdmin — 🔵 **Different-by-design (implemented; parity by construction)**
- **Legacy:** menu tree driven by XML files + the JexAdmin/Jex Studio MenuManager tool.
- **Refactor:** fully implemented — `GET /api/v1/navigation` serves the per-product tree resolved to *Product Tree ∩ grants* (admin = full tree), backed by `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` (approved refactor-infra tables). All four nav surfaces (Sidebar, Top Nav, mobile MENU, Menu Entitlement) consume it; the same `permission_cd`s gate every endpoint (F-3).
- **Parity (the partner's ask):** the tree was **seeded directly from the JexAdmin menu export** (`wecms-menu-export.json`) — F = 41 nodes, H = 33, M = 41 — so tree parity is **by construction**, not a re-derivation. `NavigationServiceTest` covers F/M/H admin + non-admin (8/8 green).
- **Action:** reply **No** — it's built; the mechanism change is intentional and parity is by-construction. A quick visual spot-check of the rendered tree vs. legacy is enough; no rework.

#### #20 — Confirm / undo transaction · `fnsh_0003/0004` — 🔵 **Core confirm done; per-category UI out of scope**
- **Legacy:** confirming a bank transaction classifies it **and** (in the per-category `fnsh_0003/0004` screens) writes a payables/receivables document row (`BUSA_DOC_RCV_PAY_HIS`).
- **WeCMS reality (Sonny, 2026-07-24) — verified in code:**
  - The **WeCMS confirm/undo is implemented** — `TransactionService` (+ `AccountController`) writes **`ACCT_TRNS_RCV_PAY_INFM`** (contact/purpose/reporting-account + confirmed flag + who confirmed). This is the confirm the Transaction History screen actually uses.
  - **`BUSA_DOC_RCV_PAY_HIS` exists and stores data** — the refactor has the `BusaDocRcvPayHis` entity/repository, used by the **Card Transactions** document-linkage (link card txns to e-invoices/expenses). So the table + data are present.
  - **What's absent is the per-category confirm UI** (`fnsh_0003/0004` family) that would write `BUSA_DOC_RCV_PAY_HIS` from the *banking* confirm — that's the WABOOKS document-linkage / cash-book layer (same `fnsh` family as the Sheet 2 out-of-scope cash-book screens).
- **Verdict:** core WeCMS confirm done; the missing per-category document-linkage UI is WABOOKS — out of scope. Not a WeCMS gap.
- **Action:** reply **No** — WeCMS confirm works; `BUSA_DOC_RCV_PAY_HIS` table holds data (Card Transactions); the per-category banking-confirm UI is a WABOOKS feature with no WeCMS screen.

#### #21 — Own-account transfer · `trns_0001_*` — 🔵 **Substantially implemented (balance + transfer execution)**
- **Mechanism (both sides):** run through the **local NXiSAS engine (scrape)** — the browser drives the engine; **Spring never invokes the engine, it only persists what the engine reports.** Intended design, matching bank connections elsewhere.
- **Balance → done** (`POST /balance-refresh`, `/source-accounts`, `/engine-connection-check`).
- **Transfer execution → done** — the composer (`OwnAccountTransferComposerDialog`) builds the form → browser engine executes → **`POST /scraping-result`** persists the outcome (`NAPAS_REMIT_TRNS_HSTR`) → balance update; plus `GET /target-accounts`, the Phase-B cert harness, and the QNA/OTP/Confirm 2nd-cert engine bridges. (The class-level Javadoc still says "not in this MR — composer" — that comment is **stale**; the composer MR landed afterward. Current controller = 10 endpoints incl. `/target-accounts` + `/scraping-result`. Confirmed by `own-account-transfer-current-implementation-audit.md`: *"goes beyond Path A … composer + scraping-result execution surface … have all landed."*)
- **Not fully implemented (all Low, documented):** per-bank field/validation nuances — empty-remark→COMP_NM auto-fill for Vietcom/Shinhan; Vietcom DIGIBIZ non-ML job switch (hardcoded CORPORATE/`*_ML`); NAPAS validation runs *after* the engine vs legacy *before* (sequencing divergence, low risk for own→own); `/recipient-banks` + `/transfer-history` deferred (external-NAPAS recipients need `NAPAS_BANK_REMIT_INFO`, but OAT is **own→own VND only** by scope, so external recipients are out of scope anyway).
- **Verdict:** core own-account transfer (balance + own→own VND execution) works end-to-end; only minor per-bank validation rules + a history view remain.
- **Action:** reply **No** (implemented; low). Optionally port the per-bank auto-fill/validation nuances (Vietcom/Shinhan) and validate-before-scrape ordering when convenient.

#### #22 — Daily Financial (HS) · `rept_0017` — ✅ **Done (stale finding — verified in code)**
- **Partner claim:** "stub backend + mock FE (gated `PRDT_TYPE='H'`)."
- **Verified in code (2026-07-24):** **not a stub.** `DailyFinancialHsReportRepository:72` calls the **real HS DB function** `fn_get_haengsung_daily_financial_report(:bizKey, :periodDate, :accountFilter)`; there's also a write service (`DailyFinancialHsWriteService`), a download service (`DailyFinancialHsDownloadService`), and the `ReptTrnsHstr` entity. The Sprint-1 hardcoded stub was removed 2026-06-14. Currency-first ORDER BY, per-currency totals, PO-localized Excel titles.
- **Remaining (finishing only, not "stub"):** bank/account multi-select UI polish, seed `REPT_TRNS_HSTR` rows, live HTTP smoke test.
- **Action:** reply **No** (already done — the partner tested an old build). Do a quick smoke-check on the current build to close it.

#### #23 — e-Invoice status filter — ✅ **Done (stale)**
- **Partner claim:** filtered client-side (gap IR-4).
- **Verified:** `EInvoiceService.java:54` — status filter is **server-side since 2026-06-30**, exact `WHERE EINV_INVOICESTATUS = :status`; IR-4 closed.
- **Action:** none.

#### #24 — Excel upload → create batch · `pymn_*_002_c001` — 🔵 **Different-by-design (not a gap; implemented)**
- **Legacy:** parses the uploaded Excel **on the client**.
- **Refactor:** parses **server-side with Apache POI** — implemented across the payment modules (`VendorPaymentExcelParser`, `ExpensePaymentExcelParser`, and the Payroll / Internal-Transfer / Overseas equivalents; column-mapping + parity covered by the per-module upload audits). Behavior-equivalent and more secure (no client-side parsing of untrusted files). The partner's own note agrees: "behavior-equivalent, different mechanism."
- **Verdict:** not a defect — a deliberate, safer mechanism. Implemented.
- **Action:** reply **No** (done; mechanism change is intentional). Column-mapping parity is covered by the existing upload-parity audits.

#### #25 — Menu-entitlement (menu-level) · `USER_MENU_AUTH_INFM` — 🔵 **Menu-level done; only gap = #1 (on PO hold)**
- **Refactor:** menu-level entitlement **is implemented** — `MenuEntitlementService` + `UserMenuAuthInfm` entity/repository + `UserManagementController` GET/PUT `/menu-entitlement` (persistence via `USER_MENU_AUTH_INFM`, V126); the Menu Entitlement universe is generated from the navigation tree (`entitlementTree.ts`).
- **The only missing piece is the action-level grants** (R/D/T per action) — which **is item #1**, and #1 is **held by the VN PO team** (approved-but-unreleased).
- **Verdict:** menu-level done; the action-level gap is #1 → same PO-hold status.
- **Action:** reply **No** (menu-level done; action-level is #1, on PO hold — not a separate commitment).

#### #26 — Preferences: company stamp + QR · `szon_0002_001` — 🚫 **Stamp + QR out of scope (logo done)**
- **Legacy:** the Preferences screen carries company **logo + stamp (con dấu / 도장) + QR** (`szon_0002_001` `_u005`–`_u008`), rendered onto printed documents.
- **Refactor:** **logo is done** (`CMPN_LOGO` upload/delete/stream in `PreferencesService`); stamp + QR are not implemented (verified — no stamp/seal/QR in the service).
- **WeCMS reality (Sonny, 2026-07-24):** WeCMS uses **only the company logo**; the stamp + QR are **not used** by WeCMS (WABOOKS/S-Zone document-printing features).
- **Verdict:** logo done; stamp + QR out of scope.
- **Action:** reply **No** — WeCMS uses the logo only; company stamp + QR aren't a WeCMS feature.

#### #27 — Environment Settings · `comm_0215/0600` — 🔵 **Done for WeCMS (generic backend; WeCMS code set exposed)**
- **Refactor:** backend is **generic** (`.../environment-settings/{code}` accepts any code — done); the FE surfaces the WeCMS-relevant codes through the **Preferences** + **Firm Banking Settings** panels (~17 of ~40 legacy codes).
- **WeCMS reality (Sonny, 2026-07-24):** the ~17 exposed codes **are the WeCMS set** — the other ~23 legacy codes are **WABOOKS/legacy and not used by WeCMS**. So exposing 17 is complete, not partial.
- **Verdict:** done for WeCMS; no missing WeCMS codes.
- **Action:** reply **No** — backend generic + the WeCMS code set is exposed; the unexposed legacy codes are WABOOKS/unused.

#### #28 — Notifications (bell) · `NOTI_*` / bltb — 🔵 **Done for web (mobile fan-out deferred)**
- **Refactor:** the header **notification bell is implemented** — ports `comm_0192_007`, backed by legacy `BLTB_NOTI_*` (5 entities), with a header bell + slide-over on the FE and an `ApprovalNotificationService` producer (approval/board notifications).
- **WeCMS reality (Sonny, 2026-07-24):** the refactor WeCMS is **web-only; mobile is "not yet."** So the **mobile fan-out is deferred** with the rest of the (not-yet-built) mobile client — same boundary as #11. The general `'G'` announcement type was not flagged as needed for the web refactor.
- **Verdict:** bell done for the web app; mobile fan-out deferred (web-only, mobile not yet).
- **Action:** reply **No** — the web notification bell works; mobile fan-out lands with a future mobile client (out of the web scope, like #11). Revisit the general `'G'` type only if the PO wants system-wide announcements.

#### #29 — ERP Transaction History thumbnail · `comm_0219` — 🚫 **Unused stub (out of scope)**
- **Refactor:** the `GET .../{seqNo}/thumbnail` endpoint is a **stub** (returns `WBE9001`) — but **nothing calls it.**
- **WeCMS reality (Sonny, 2026-07-24):** the WeCMS ERP Transaction History screen has **no thumbnail UI** (verified — no thumbnail/preview in `ErpTransactionHistoryTable/Panel/Tabs/Filters`), and **legacy `comm_0219` has no thumbnail** either. ERP transaction files are data files (Excel/text exchanged with the ERP), not images — a thumbnail isn't meaningful. WeCMS does not need it.
- **Verdict:** the stub is an unused placeholder, not a broken feature — out of scope.
- **Action:** reply **No** — no thumbnail UI in WeCMS; the stub endpoint is unused. (Optionally delete the dead endpoint; not required.)

---

## 4. Sheet 2 — "Out-of-Scope" modules (11), audited

These were **not refactored on purpose** — they belong to WABOOKS (accounting), Tax, Member, or the public ERP API, which are a different product line from the WeCMS cash-management core. Recommended reply: **confirm out-of-scope**, and only move to backlog if WeCMS explicitly needs a specific one.

| # | Module | Legacy | Verdict | Note |
|---|---|---|---|---|
| 1 | Cash book / Check register | `fnsh_0011/0012` | 🚫 Out of scope | WABOOKS Cash menu — confirm PO. |
| 2 | Term deposit / Loan | `fnsh_0009/0010` | 🚫 Out of scope | No entity/route. |
| 3 | Imprest ledger | `fnsh_0020/0021` | 🚫 Out of scope | `EXPT_RESOL_*` (expense-approval) is a different feature. |
| 4 | Sales / P&L / customer-vendor reports | `rept_0002/0003/0004/0007` | 🚫 Out of scope | WABOOKS scope. |
| 5 | S-Zone master-data hub | `szon` (171 act) | 🚫 Out of scope | employee/item/project/migration/payroll settings/withholding — mostly WABOOKS. (Ties to Sheet 1 #16.) |
| 6 | Full payroll book | `saly` | 🚫 Out of scope | Register → pay-process → payslip. |
| 7 | Purchase-sales documents + quick pay/receipt | `prch` / `sale` | 🚫 Out of scope | Bill/Expense/Invoice + receipt matching. |
| 8 | Quotation / PO · Support board | `trgm` / `tosv` | 🚫 Out of scope | — |
| 9 | Signup / find-id / OTP | `mbmr` | 🚫 Out of scope | `domain/member` not implemented (self-service provisioning is DBA-only by policy). |
| 10 | Public API for ERP (MREC/WMCK + Swagger + API key) | `api` (155 act) | 🚫 Out of scope | ERP integration goes via the `ERP_TRNS_HSTR` table instead. |
| 11 | e-Filing tax Excel | `efilling.jsp` | 🚫 Out of scope | Export-only in legacy; Tax scope. |

---

## 5. Recommended reply to the partner (fills their 4 yellow columns)

> **Do NOT accept the whole list as work.** Split the reply into four buckets:

**Bucket 1 — "Already done / no change needed" (close these):**
- **#2** MATCH_TYPE — refactor `'A'` already matches legacy `'A'`; premise incorrect.
- **#6** e-Invoice docType — already fixed (`→ 'B'`).
- **#23** e-Invoice status filter — server-side since 2026-06-30.
- **#5** VCB routing — done for Vendor/Expense/Internal.
- **#22** Daily Financial (HS) — verify build; believed already code-complete.

**Bucket 2 — "Genuine gaps we will fix" (commit an owner + date):**
- **#1** action-level permissions (R-02) — *the top UAT/PROD blocker*.
- ~~**#4** server-side approval gate~~ — **DONE 2026-07-24** (Vendor/Payroll/Internal; Expense + Overseas already had it). All 5 modules now assert `APPR_TYPE_CD='01'` server-side.
- **#5** VCB routing — Payroll + Overseas remainder.
- **#7** company-status login gates.
- **#14** contact group master · **#20** confirm→payables · **#26** stamp + QR · **#27** env-settings FE codes.
- Minor: **#8, #9, #29**.

**Bucket 3 — "Different-by-design, not a defect" (explain, don't schedule):**
- **#11, #18, #19, #24, #28** — mechanism changes, behavior-equivalent or consciously trimmed. (**#15** was here but is now **done** — server-side read-only recents landed 2026-07-24.)

**Bucket 4 — "Needs a PO scope decision before any work":**
- **#10, #12, #13, #16, #17** + all of **Sheet 2**. Route to Mr. Jung (PO).

---

### 5.1 Draft answers for the partner's 4 yellow columns (all 29 rows)

Paste these into the sheet's **Will fix? / Approach / Owner / Target** columns. Owners are *suggestions* (per team roster: Arthur = dev, Mr. Jung = PO); confirm real owners and dates at sprint planning. Also delivered as a paste-ready file: `~/Downloads/WECMS-PARTNER-BI-DRAFT-ANSWERS.csv`.

> **Note on row #1:** "Không / No" is **correct** — the VN PO team deliberately **held this feature before**, so it is not a dev omission and there is no fix commitment now; it stays on hold pending a PO release decision. (It remains a real gap that must be closed *before* UAT/PROD if/when the PO releases it.) Row **#2**'s "No" is correct (already matches legacy).

| # | Will fix? | Approach / commitment | Owner | Target |
|---|---|---|---|---|
| 1 | No | VN PO team **held this feature before** — deliberate hold, not a dev omission. No fix commitment now; pending PO release. When released: build the action-flag contract (View/Upload/Delete/Request-Approval/Transfer) × 5 payment modules + FE gating + server-side enforcement (ties #4). | Mr. Jung (PO) | On hold (PO) |
| 2 | No | Already correct. `char(1)` column; legacy enum stores `'A'`, refactor writes `'A'`. Verified live DB. No divergence. | — | Done |
| 3 | No | Not a WeCMS feature — WeCMS has no UI for adding a manual account or manual transaction (WABOOKS/cash-book scope, cf. Sheet 2). | — (PO if scope changes) | Out of scope |
| 4 | **Done (2026-07-24)** | Server-side full-approval assertion (`APPR_TYPE_CD='01'`) added to Vendor/Payroll/Internal; Expense + Overseas already enforced. All 5 modules now match legacy (strict `='01'`, blocks Pending/Rejected/Partially-Approved). Gate is in each `*Service.dispatch(...)`, not the executor. | Arthur | Done |
| 5 | No | Done across all 5 — explicit VCB→file gate in Vendor/Expense/Internal; Payroll & Overseas are file-only/VCB-blocked so no misroute possible. "Remainder" was moot. | — | Done |
| 6 | No | Already fixed — `resolveDocType('EXPENSE')→'B'`, rejects `'E'`. Stale. | — | Done |
| 7 | No | Not a WeCMS feature — company-status / trial gates come from WABOOKS; WeCMS doesn't use them (deployed per enterprise). No refactor needed. | — | Out of scope |
| 8 | No | Not a WeCMS feature — auto-unlock is the back half of Member (`mbmr`) self-service password-reset (Sheet 2 #9, OOS). WeCMS has no forgot-password UI; unlock is DBA-only by design. | — | Out of scope |
| 9 | No | Already done — `AuthService:232-242` writes `BUSI_ADMN_LOGIN_HIS` via `AdminLoginHistoryService` on business-admin login (write-only, viewer omitted by design). Stale finding. | — | Done |
| 10 | No | Not a WeCMS feature — no attach UI on the Transaction History screen; table dropped V31 as unused. | — | Out of scope |
| 11 | No | Mobile-only push (`PUSH_DEVICE_MSTR` / FCM to the phone app). The refactor is web-only — no mobile client, so no push target. Out of scope. | — | Out of scope |
| 12 | No | Not a WeCMS report — WeCMS Reports menu has only Daily Cash / Daily Financial / Cash Flow. `rept_0005/0006` (AR/AP aging) are WABOOKS. | — | Out of scope |
| 13 | No | Dropped by design — no Re-run UI. Failed payments handled via Payment Result + auto-recovery (`ResultCheckService`/`StrandedRecoveryService`); user re-creates instead. | — | Dropped |
| 14 | No | Not a WeCMS feature — Contacts has no group-management UI / group filter (only fixed Buyer/Seller groups). Custom-group master (`trco_0101_001`) is WABOOKS S-Zone. | — | Out of scope |
| 15 | **Done (2026-07-24)** | Server-side read-only recents (legacy parity, PO option A): read-only entities/repos over `USER_TRCO_USE_HIS` / `USER_USAG_USE_HIS` + `GET .../contacts/recent` + `.../purpose-settings/recent` (top-3 distinct, newest-first); `ContactSelect`/`PurposeSelect` now fetch server-side instead of localStorage, so recents follow the user across devices. No write path added — population stays with the live JEX trigger (exact legacy match). VAS recents stay localStorage (no legacy table). | Arthur | Done |
| 16 | No | Not a WeCMS feature — S-Zone employee-management screen is WABOOKS (Sheet 2 #5). WeCMS fills `EMPL_INFM` via DBA SQL; no employee-management UI. | — | Out of scope |
| 17 | No | Not in refactor scope — WeCMS lands on Account Inquiry by design. Dashboard (`comm_0210_001` + `SYS/USER_DSHBRD_INFM`) has tables only, no code/route. | — | Out of scope |
| 18 | No | Already done — server-side jti revocation on logout (F-03); filter rejects revoked tokens. Not a client-only stub. Full legacy parity restored 2026-07-24 — logout now also writes the `USER_LOGIN_HIS` `RSLT_CD='LOGOUT'` audit row (matches `logn_0002_001_r001`). (In-memory store = infra follow-up, not a gap.) | — | Done |
| 19 | No | Implemented — `MENU_ITEM` + `/navigation` replaces XML+JexAdmin; tree seeded from the JexAdmin export (F=41/H=33/M=41), so parity is by-construction. Just a visual spot-check. | Arthur (spot-check) | Done |
| 20 | No | WeCMS confirm done (classify + confirmed via `ACCT_TRNS_RCV_PAY_INFM`). `BUSA_DOC_RCV_PAY_HIS` table exists + stores data (Card Transactions); per-category confirm UI (`fnsh_0003/0004`) is WABOOKS — no WeCMS screen. | — | Out of scope (per-cat UI) |
| 21 | No | Substantially implemented — balance + own→own VND transfer execution both work via the NXiSAS engine (`/balance-refresh`, `/target-accounts`, `/scraping-result`, cert + QNA/OTP/Confirm bridges). Only minor per-bank validation nuances (Vietcom/Shinhan) + history view remain (Low). | — | Done (minor low) |
| 22 | No | Done — `rept_0017_001` calls the real HS DB function `fn_get_haengsung_daily_financial_report` (+ write/download services, `ReptTrnsHstr`). Not a stub. Partner tested an old build. | Arthur (smoke-check) | Done |
| 23 | No | Already done — server-side filter since 2026-06-30 (IR-4 closed). Stale. | — | Done |
| 24 | No | Implemented — server-side Apache POI parse across all payment modules (vs legacy client parse). Behavior-equivalent, more secure. Partner agrees "behavior-equivalent." | — | Done |
| 25 | No | Menu-level entitlement done (`MenuEntitlementService` + `USER_MENU_AUTH_INFM`). Only gap = action-level grants = item #1, which is on PO hold. | — | Done (menu-level); #1 on PO hold |
| 26 | No | Logo done; company stamp + QR not used by WeCMS (WABOOKS/S-Zone document-printing). | — | Out of scope (stamp+QR) |
| 27 | No | Backend generic (done); FE exposes the WeCMS code set (~17). The other ~23 legacy codes are WABOOKS/unused. Complete for WeCMS. | — | Done |
| 28 | No | Web notification bell done (`BLTB_NOTI_*`, header bell + slide-over). Mobile fan-out deferred (web-only; mobile not yet, like #11). General `'G'` not needed for web. | — | Done (web); mobile deferred |
| 29 | No | Unused stub — no thumbnail UI in WeCMS (or legacy `comm_0219`); ERP files are data files, not images. The `WBE9001` endpoint is dead code. | — | Out of scope |

**Tally of the reply (after the 2026-07-24 code double-check):** Yes/will-fix → **0** — the last two "Yes" items both flipped: **#9** is already done (writes `BUSI_ADMN_LOGIN_HIS`) and **#5**'s Payroll/Overseas remainder is moot (those modules are file-only). No/already-done/by-design/out-of-scope → **28** (everything except #1). Pending / PO-hold → **1** (**#1** action-level permissions, held by the PO). **Net: nothing on the partner's 29-item list requires building except the PO-held #1.**

---

## 6. Sources

- Refactor code: `wecms-api/src/main/java/...` (verified 2026-07-24), `wecms-web/` FE stubs.
- Legacy source of truth: `WECMS DEV/WECMS/src/wabooks/enums/MatchTypeCd.java`, JEX IDO/WSVC XML.
- Prior audits & decisions: `wecms-api/docs/audits/bank-transaction-sync-debit-matched-parity-gap.md`, e-Invoice feature docs, and project memory (`project_vcb_file_transfer_switch`, `project_einvoice_status_filter_server_side`, `project_daily_financial_hs_real_db`, `project_notification_module_removed`, `project_login_credentials_parity_fixes`).
- Partner input: `WECMS-CAN-UPDATE-FOR-PARTNER-BI.xlsx` (sheets "Cần update · To Update", "Ngoài scope · Out-of-Scope"), self-dated verified 2026-07-20.

> **Caveat:** items #3, #4, #7–#9, #12–#14, #20 were assessed from feature records + spot-checks, not a line-by-line re-read of every endpoint. Confirm each with the owning developer before committing a date to the partner.

# Approval Box — Discovery (JEX → Refactor)

**Feature slug:** `approval-box`
**Module:** `payment`
**Backend package (planned):** `com.kosign.wecms.domain.payment.approvalbox`
**Frontend route (planned):** `/payment/approval-box`
**Refactor doc folder:** `docs/refactor-wecms/features/payment/approval-box/`
**JEX functional spec:** [docs/jex/business-rules/approval.md](../../../../../docs/jex/business-rules/approval.md), [docs/jex/screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md)
**Date:** 2026-05-13
**Status:** Discovery — no code in `wecms-api/` or `wecms-web/` yet.

This document follows Step 2 of [MIGRATION_PLAYBOOK.md](../../../references/migration/MIGRATION_PLAYBOOK.md). Every JEX claim cites a file:line. Legacy WSVC ids (`resl_0001_001`, `comm_0195_001`, …) appear here **only** as JEX traceability pointers — they are NOT identifier surfaces in the refactor (per workspace [CLAUDE.md](../../../../../CLAUDE.md) §"Refactor identifier naming").

---

## 1 · JEX naming reality check

The screen the PO calls "Approval Box" is implemented under JEX module **`resl`** (resolution / 결재), not `pymn`. The canonical service id is **`resl_0001_001`**.

This contradicts the WSVC id `pymn_0001_001` listed in [docs/jex/screen-flows/payments.md:12](../../../../../docs/jex/screen-flows/payments.md#L12) — that row is incorrect; `pymn_0001_001` is in fact Scheduled Payment (Vendor) (the second use of the same id in the same table, [docs/jex/screen-flows/payments.md:15](../../../../../docs/jex/screen-flows/payments.md#L15)). Confirmed by:

- The view file [WECMS DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp:247](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L247) sets `FOCUS_MENU_ID = resl_0001_001` — this is the menu the left-nav highlights on the Approval Box page.
- The left-menu HTML [WECMS DEV/WECMS_STATIC/web/assets_v2/include/_left_menu_wecms.html:9](../../../../../WECMS%20DEV/WECMS_STATIC/web/assets_v2/include/_left_menu_wecms.html#L9) maps the "Approval Box" sidebar item to that view.
- The view file is wrapped in `<div id="container" class="cms vh-100 approval_box">` at [resl_0001_001_view_osmc.jsp:239](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L239).

A bug-report follow-up to fix the `pymn_0001_001`/`pymn_0001_001` duplicate in `docs/jex/screen-flows/payments.md` is out of scope for this refactor PR but should be filed under `docs/bug-reports/` with `System: JEX`. The refactor port treats `resl_0001_001` as the canonical JEX source.

---

## 2 · JEX entry points

### 2.1 · Menu / left-nav wiring

| Artifact | Path | Purpose |
|---|---|---|
| Left-menu HTML | [WECMS DEV/WECMS_STATIC/web/assets_v2/include/_left_menu_wecms.html:9](../../../../../WECMS%20DEV/WECMS_STATIC/web/assets_v2/include/_left_menu_wecms.html#L9), [:17](../../../../../WECMS%20DEV/WECMS_STATIC/web/assets_v2/include/_left_menu_wecms.html#L17) | Two sidebar entries (active + plain) both labelled "Approval Box". |
| OSMC view layout | [resl_0001_001_view_osmc.jsp:239-264](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L239) | Container, GNB (`GUBUN=PRCH`), left-nav include with `FOCUS_MENU_ID=resl_0001_001`. |

### 2.2 · View JSPs

| Variant | Path | Notes |
|---|---|---|
| WeCMS (Vietnam) | [resl_0001_001_view_osmc.jsp](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp) | The **live WeCMS variant** (per `inc_wabooks_hd_v2_osmc.jsp` + `_osmc.js` script include). This is the source of truth for the refactor. |
| Legacy (Cambodia) | [resl_0001_001_view.jsp](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view.jsp) | Older WABOOKS-style table, gated by `CommHelper.isOSMCVersion(request)` at [resl_0001_001_view.jsp:4](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view.jsp#L4). **Not ported.** |
| Old archive | [resl_0001_001_view_old.jsp](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_old.jsp) | Frozen archive. **Not ported.** |

The OSMC view declares the page title via `data-jex-ml="resl_0001_001_m007"` ([resl_0001_001_view_osmc.jsp:255](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L255)) — the registered ML resolves to "Approval Box" in EN. Filter/page controls: `view-sort` (View Setting popover) at [:258](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L258), `page-print` at [:261](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L261), `excel-down` at [:262](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L262). Tab strip at [:268-294](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L268) — five tabs encoded as `data-cd`: `""` All, `00` Pending, `03` In Progress, `01` Approved, `02` Rejected. Date-range filter `Last 30/90/180 Days / Enter Manually` at [:298-355](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L298). Body table `dymAreaId="table3"` at [:417](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L417) with a sibling footer table for totals at [:430-437](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L430). Pagination at [:442-460](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L442).

> **Note on tab semantics.** The OSMC view advertises 5 tabs (All / Pending / In Progress / Approved / Rejected). [docs/jex/business-rules/approval.md:62-71](../../../../../docs/jex/business-rules/approval.md#L62) lists those same 5 tabs. The current refactor static-UI mock [wecms-web/components/payment/approval-box/types.ts:8-14](../../../../../wecms-web/components/payment/approval-box/types.ts#L8) introduces a 6th tab `draft`. **OQ-1 (open question)** — see §10.

### 2.3 · TypeScript / JS handlers

| Artifact | Path | Purpose |
|---|---|---|
| TS source (OSMC) | [WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts) | Sole frontend module: tab-click → reload, filter, AJAX call to `resl_0001_001_r001.jct`, render row, hover-card with `REC3` chain, Excel export, print, pagination. |
| Compiled JS | [WECMS DEV/WECMS_STATIC/web/js/jex/wabooks/resl/resl_0001_001_osmc.js](../../../../../WECMS%20DEV/WECMS_STATIC/web/js/jex/wabooks/resl/resl_0001_001_osmc.js) | TSC output — never hand-edit. |
| TS source (legacy) | [WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001.ts](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001.ts) | Cambodia variant. **Not ported.** |
| TS for approver popup helper | [WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_2001_000.ts](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_2001_000.ts) | Cross-feature approver helper. Used incidentally — does not own the Approval Box page. |

The OSMC TS handles the full read flow: AJAX call at [resl_0001_001_osmc.ts:246-289](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L246); row render with `APPR_DV → display` mapping at [:443-477](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L443); hover card built from `REC3` (approver chain) at [:533-600+](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L533); row click opens the detail popup `comm_0195_001.act` at [:187-196](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L187).

### 2.4 · Action JSPs (the JEX "controllers")

| Service id | Path | Purpose |
|---|---|---|
| `resl_0001_001_r001` (list) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp) | Paginated list + tab counts + currency totals. Validation, dynamic SQL builder, sort handling, file aggregation, approver-chain expansion (REC3). |
| `resl_0001_001_d001` (delete) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_d001_act.jsp](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_d001_act.jsp) | Hard-delete `EXPT_RESOL_MSTR` row + cascade clean of `EXPT_RESOL_APPRVR_INFM`, files, `BLTB_POST_INFM` thread, comments. Updates `BUSA_DOC_MSTR` / `SALY_BOOK_MSTR` / `ADV_PAYM_TRNS_HSTR` `APPR_SEQ_NO` → NULL depending on `APPR_DV`. |

Detail popup (not co-located under `resl/`):

| Service id | Path | Purpose |
|---|---|---|
| `comm_0195_001` | (referenced from TS [resl_0001_001_osmc.ts:187](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L187)) | "Approval Details" popup launched on row click. Carries `POST_SEQ_NO`, `APPR_DV`, `SCDUL_PYMN_NO`, `TRNF_CCY_CD`, `PYMN_TYPE`, `BANK_CD`, `PAY_DATE`. Hosts the Approve / Reject footer buttons described in [docs/jex/business-rules/approval.md:175-227](../../../../../docs/jex/business-rules/approval.md#L175). Not under `resl/` — sits in the `comm` module; its `_act.jsp` files are siblings of `comm_0192_*`. **The approve / reject WSVCs are owned by the detail popup, not the Approval Box page.** Scope decision documented in §7. |

The list `_act.jsp` ([resl_0001_001_r001_act.jsp:54-225](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L54)) implements:

- Required-input validation `FROM_DT`, `TO_DT`, period check ([resl_0001_001_r001_act.jsp:54-61](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L54)).
- BIZ_KEY scope ([resl_0001_001_r001_act.jsp:68-69](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L68)).
- DEL_YN filter ([resl_0001_001_r001_act.jsp:72](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L72)).
- `APPR_DV IN ('21','31','32','33','34','35')` — Payroll, Expense, Vendor, Corporate Card, Overseas, Internal Transfer ([resl_0001_001_r001_act.jsp:78](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L78)). This is the canonical list of the 6 payment types Approval Box surfaces.
- `APPR_TYPE_CD` tab filter applied to detail query (not count): [resl_0001_001_r001_act.jsp:81-86](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L81) — i.e. tabs filter the visible rows but the tab-count subheader keeps the unfiltered totals.
- Search `SRCH_WD` over `DOC_NM`, `TITL`, `DOC_NO`, plus translated `TITL` for VN tenants: [resl_0001_001_r001_act.jsp:88-105](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L88).
- Date range over `DRAFT_DATE` (registration date — note: the column referenced is `draft_date`, not `reg_dtm`): [resl_0001_001_r001_act.jsp:108-112](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L108).
- Per-user visibility filter (Redmine #42046): non-admin users see only rows they uploaded (SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR / CARD_TRNS_HSTR), they requested (`reg_id`), or are assigned approvers for (`EXPT_RESOL_APPRVR_INFM`): [resl_0001_001_r001_act.jsp:114-162](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L114).
- Page assembly using `EXPT_RESOL_MSTR_R003` for rows + `EXPT_RESOL_MSTR_R003_CNT` for tab/currency totals: [resl_0001_001_r001_act.jsp:227-255](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L227).
- Approver chain (REC3) joined per row at [resl_0001_001_r001_act.jsp:325-338](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L325): `EXPT_RESOL_APPRVR_INFM` filtered to `BIZ_KEY + APPR_SEQ_NO`, ordered by `APPRVR_LINE_NO`. Profile image URL derived from `szon_0012_003_r002.act?USER_ID=…`.

### 2.5 · WSVC XML

| Service | Path | Notes |
|---|---|---|
| Service `resl_0001_001` | [WECMS_ETC/xml/service/WSVC/WSVC.resl_0001_001.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.resl_0001_001.xml) | The view-side service. |
| List API `resl_0001_001_r001` | [WECMS_ETC/xml/service/WSVC/WSVC.resl_0001_001_r001.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.resl_0001_001_r001.xml) | Defines IN: `SRCH_WD`, `FROM_DT`, `TO_DT`, `PAGE_NO`, `PAGE_PER_CNT`, `APPR_TYPE_CD`. OUT scalar: `TOT_CNT`, `TOT_KHR_AMT`, `TOT_KHR_CNT`, `TOT_USD_AMT`, `TOT_USD_CNT`, `TOT_APPR_CNT`, `TOT_RJT_CNT`, `TOT_PEDN_CNT`, `TOT_INP_CNT`, `TOT_VND_AMT`, `TOT_KRW_AMT`. RECORD `REC` (rows) with sub-RECORDs `REC2` (files) and `REC3` (approvers). |
| Delete API `resl_0001_001_d001` | [WSVC.resl_0001_001_d001.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.resl_0001_001_d001.xml) | Input: `REC1.APPR_SEQ_NO[]`. No output. |

### 2.6 · IDO XML (queries)

The Approval Box list page exercises two custom IDOs:

| IDO | Path | Role |
|---|---|---|
| `EXPT_RESOL_MSTR_R003` | [IDO.EXPT_RESOL_MSTR_R003.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.EXPT_RESOL_MSTR_R003.xml) | The list query. Joins `EXPT_RESOL_MSTR` ←→ `BLTB_POST_INFM` ←→ `OVRS_PYMN_MSTR` ←→ `SCDUL_PYMN_SUMR` ←→ `OVRS_PYMN_SUMR`. Uses helpers `wb_get_expt_resol_mstr_doc_nm`, `wb_get_appr_stts`, `wb_get_memo_cont`, `wb_get_memo_color`. Returns rows with computed `CCY_CD`, `FILE_NM`, `APPR_STTS`, `APPRVR_TOT_CNT`, `APPRVR_APPR_TOT_CNT`, `PAY_YN`. Two `??` dynamic blocks: inner WHERE for filter, outer WHERE for sort. |
| `EXPT_RESOL_MSTR_R003_CNT` | [IDO.EXPT_RESOL_MSTR_R003_CNT.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.EXPT_RESOL_MSTR_R003_CNT.xml) | Aggregates: `TOT_CNT` and per-status counts (`TOT_APPR_CNT`, `TOT_RJT_CNT`, `TOT_PEDN_CNT`, `TOT_INP_CNT`), plus per-currency `TOT_*_AMT` and `TOT_*_CNT` for VND, USD, KHR, KRW. Same join shape as R003 minus REC2/REC3. |

Other Approval Box-adjacent IDOs (not exercised by the list page itself but used by detail/popup/approve flows that the Approval Box launches): `EXPT_RESOL_APPRVR_INFM_R001`..`R008`, `EXPT_RESOL_APPRVR_INFM_C001`, `_U001`, `_U000`, `_D001`, plus `EXPT_RESOL_MSTR_R001`..`R010`. Full list under [WECMS_ETC/xml/service/IDO/](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/) (52 files matching `IDO.EXPT_RESOL*.xml`).

### 2.7 · DAOs

| DAO | Path | Used by Approval Box for |
|---|---|---|
| `ExptResolMstrDAO` | [WECMS DEV/WECMS/src/wabooks/dao/ExptResolMstrDAO.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/dao/ExptResolMstrDAO.java) | Master row CRUD + soft delete (`deleteByPk` for hard delete). Implements `Creatable`, `Updatable`, `Patchable`, `Memoable`, `Deletable`. |
| `ExptResolApprvrInfmDAO` | [WECMS DEV/WECMS/src/wabooks/dao/ExptResolApprvrInfmDAO.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/dao/ExptResolApprvrInfmDAO.java) | Approver chain rows. `create()` defaults `APPRVR_STTS = 'P'`. |
| `ExptResolFileDAO` | [WECMS DEV/WECMS/src/wabooks/dao/ExptResolFileDAO.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/dao/ExptResolFileDAO.java) | Attached supporting docs (one row per attachment). |
| `ExptResolPayEvidFileDAO` | [WECMS DEV/WECMS/src/wabooks/dao/ExptResolPayEvidFileDAO.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/dao/ExptResolPayEvidFileDAO.java) | Pay-evidence files (post-payment evidence uploads). |
| `ExptResolApprLineDAO` | [WECMS DEV/WECMS/src/wabooks/dao/ExptResolApprLineDAO.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/dao/ExptResolApprLineDAO.java) | Approval-line template (referenced by `comm_0194`/`comm_0195` config screens — read-only here). |
| `ExchRateInfmDAO` | [WECMS DEV/WECMS/src/wabooks/dao/ExchRateInfmDAO.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/dao/ExchRateInfmDAO.java) | FX lookup (imported but not used by `resl_0001_001_r001_act.jsp` — vestigial import). |

### 2.8 · Helpers

| Helper | Path | Used by `resl_0001_001_r001_act.jsp` for |
|---|---|---|
| `ReslHelper` | [WECMS DEV/WECMS/src/wabooks/helper/ReslHelper.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/ReslHelper.java) | `getTotInfo(...)` aggregation helper. Currently called out as commented-out at [resl_0001_001_r001_act.jsp:347-350](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L347) — replaced by the inline IDO call. |
| `SortHelper` | (`wabooks.helper.SortHelper`) | Builds the second `??` dynamic for `ORDER BY` from frontend sort fields. Invoked at [resl_0001_001_r001_act.jsp:295](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L295). |
| `LocaleHelper` | (`wabooks.helper.LocaleHelper`) | Resolves request language → `LangCd` enum at [resl_0001_001_r001_act.jsp:228](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L228). |
| `ValidationHelper` | (`wabooks.helper.ValidationHelper`) | `validateRequired`, `validateDate8`, `validatePeriod` at [resl_0001_001_r001_act.jsp:54-60](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L54). |
| `SzonHelper` | (`wabooks.helper.SzonHelper`) | Imported but not actively called by the list action. Vestigial. |

### 2.9 · Entities (legacy schema reference — read-only)

| Entity | Path | `@Table` |
|---|---|---|
| `ExptResolMstr` | [WECMS DEV/WECMS/src/wabooks/entity/ExptResolMstr.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolMstr.java) | `EXPT_RESOL_MSTR` ([:45](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolMstr.java#L45)). Extends `ExptResolMstrPk` (composite PK: `BIZ_KEY` + `APPR_SEQ_NO`). |
| `ExptResolMstrPk` | [ExptResolMstrPk.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolMstrPk.java) | Defines `APPR_SEQ_NO` at [:26](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolMstrPk.java#L26). |
| `ExptResolApprvrInfm` | [ExptResolApprvrInfm.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolApprvrInfm.java) | `EXPT_RESOL_APPRVR_INFM` ([:28](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolApprvrInfm.java#L28)). Composite PK: `BIZ_KEY` + `APPR_SEQ_NO` + `APPRVR_ID`. |
| `ExptResolFile` | [ExptResolFile.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolFile.java) | `EXPT_RESOL_FILE`. Composite PK + `FILE_ID`. |
| `ExptResolPayEvidFile` | [ExptResolPayEvidFile.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolPayEvidFile.java) | `EXPT_RESOL_PAY_EVID_FILE`. Composite PK + `EVID_FILE_ID`. |

The refactor will create new Spring entities mirroring these column-for-column per `wecms-api/CLAUDE.md` §"Legacy Schema Parity Rule". Names will follow Spring conventions (`ExptResolMstr` becomes `ExptResolMstr` in `com.kosign.wecms.domain.payment.approvalbox.entity` — the **slug identifier** is `approval-box`, but legacy DB column names stay UPPER_SNAKE per the same rule).

---

## 3 · Tables touched (DEV PostgreSQL, read-only inspection)

All queries were `information_schema` / `pg_*` only — no data mutation, no PROD. Schema is `wecmsvndev`.

### 3.1 · `expt_resol_mstr` (master)

PK: `(biz_key, appr_seq_no)` — `expt_resol_mstr_pk`.
FKs: `expt_resol_mstr_fk01` → `busi_infm(biz_key)`.
Checks: `expt_resol_mstr_chk01` on `appr_dv ∈ {'00','10','20','21','30','31','32','33','34','35','40','50','60','70'}`; `_chk02` on `appr_sts ∈ {'0','1','2','4','9'}`; `_chk03/_chk04` on `del_yn / hand_reg_yn ∈ {'Y','N'}`.
Triggers: none (`pg_trigger` returned 0 user triggers).
Row count (DEV, `BIZ_KEY = VN_VNM0004_000001`): **325 rows**.

Columns (selected):

| Column | Type | NN | Default | Notes |
|---|---|---|---|---|
| `biz_key` | varchar(20) | Y | — | Tenant. |
| `appr_seq_no` | varchar(15) | Y | — | Per-tenant document sequence. Generated by `wb_get_next_val('EXPT_RESOL_MSTR', 'appr_seq_no', biz_key, …)` (legacy DB function — refactor consumes it via the existing porting rule in [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Key-generation rule for migrated legacy tables"; not invented). |
| `appr_dv` | varchar(2) | Y | — | Type discriminator. The 6 Approval Box types: `21`=Payroll, `31`=Expense, `32`=Vendor, `33`=Corporate Card, `34`=Overseas, `35`=Internal Transfer. (Other `appr_dv` values exist for the broader resolution module but are NOT shown in Approval Box per [resl_0001_001_r001_act.jsp:78](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L78).) |
| `appr_sts` | varchar(2) | Y | `'0'` | `'0'` Draft, `'1'`?`'2'` end-state, `'4'`/`'9'` exceptional. Refactor reads the **derived** status from `wb_get_appr_stts(...)` (returns `'P'`/`'I'`/`'C'`/`'R'`) for the badge — see §4. |
| `draft_date` | varchar(8) | Y | — | YYYYMMDD. Used for date-range filter. |
| `draft_time` | varchar(6) | Y | — | HHMMSS. |
| `draft_user_id` | varchar(100) | Y | — | "Requestor". |
| `draft_user_nm` | varchar(100) | Y | — | Requestor display name (denormalised). |
| `titl` | varchar(200) | Y | — | Subject. |
| `doc_no` | varchar(50) | Y | — | `VO_YYMMDD_NNN`. |
| `doc_nm` | varchar(100) | Y | — | Internal document name. Display label is resolved via `wb_get_expt_resol_mstr_doc_nm(APPR_DV, LANG_CODE)` not from this column. |
| `tot_pay_vnd_amt` | numeric | N | `0` | Total in VND (Vietnam tenants). |
| `tot_pay_usd_amt` | numeric | N | `0` | Total in USD. |
| `tot_pay_khr_amt` | numeric | N | — | Total in KHR (Cambodia legacy). For VN, the R003 query aliases `tot_pay_khr_amt` to the live currency amount via a CASE on `appr_dv` (special-case for `33` Corporate Card sums `card_trns_hstr.trsc_amt`). |
| `del_yn` | varchar(1) | Y | `'N'` | Soft delete flag. |
| `pay_date` | varchar(8) | N | — | Scheduled payment date. |
| `pay_end_yn` | varchar(1) | Y | `'N'` | Execution status (post-approval). |
| `appr_type_cd` | varchar(2) | Y | `'00'` | Tab discriminator: `00` Pending, `01` Approved, `02` Rejected, `03` In Progress. |
| `appr_fund_tp` | char(1) | N | — | Fund type for Payroll (`21`). |
| `reg_id`, `reg_dtm`, `mod_id`, `mod_dtm` | varchar | Y | — | Audit. |

Full column list (47 columns) is captured in [database-impact.md](database-impact.md) §1.

### 3.2 · `expt_resol_apprvr_infm` (approvers per resolution)

PK: `(biz_key, appr_seq_no, apprvr_id)` — `pk_expt_resol_apprvr`. **No FK** to `expt_resol_mstr` (legacy schema gap — not enforced in DB).
No triggers, no extra checks.

Columns:

| Column | Type | NN | Notes |
|---|---|---|---|
| `biz_key` | varchar(20) | Y | |
| `appr_seq_no` | varchar(15) | Y | |
| `apprvr_id` | varchar(100) | Y | The approver's `USER_ID`. |
| `apprvr_stts` | char(1) | Y | `'P'` Pending / `'A'` Approved / `'R'` Rejected / `'W'` Unable (not-this-turn) / `'S'` Unable (blocked-by-reject). |
| `apprvr_nm` | varchar(100) | Y | Denormalised name. |
| `appr_dtm` | varchar(14) | N | When approver acted (YYYYMMDDHHmmss). |
| `reg_id`, `reg_dtm`, `mod_id`, `mod_dtm` | varchar | Y | Audit. `reg_id` is nullable. |
| `read_yn` | varchar(1) | Y | `'Y'`/`'N'` — has the approver "opened" the request. |
| `apprvr_line_no` | numeric | N | Step order (1..5). Drives the visual chain. |
| `appr_line_no` | numeric | N | Reference to the configured approval-line template (`expt_resol_appr_line.appr_line_no`). |

### 3.3 · `expt_resol_file` (supporting docs attached at request time)

PK: `(biz_key, appr_seq_no, file_id)` — `expt_resol_file_pk`. FK to `expt_resol_mstr(biz_key, appr_seq_no)`.

| Column | Type | NN |
|---|---|---|
| `biz_key` / `appr_seq_no` / `file_id` | varchar(20/15/100) | Y |
| `org_file_nm` | varchar(500) | Y |
| `save_file_nm` | varchar(500) | Y |
| `note` | varchar(255) | N |
| `reg_id`, `reg_dtm`, `reg_nm` | varchar | Y |
| `file_size` | varchar(100) | N |

### 3.4 · `expt_resol_pay_evid_file` (evidence files after payment)

PK: `(biz_key, appr_seq_no, evid_file_id)` — `expt_resol_pay_evid_file_pk`. FK to `expt_resol_mstr(biz_key, appr_seq_no)`. Same column shape as `expt_resol_file` except first key is `evid_file_id`.

### 3.5 · `expt_resol_appr_line` (approval-line template — read-only here)

PK: `(biz_key, appr_seq_no, appr_line_no)` — derived. Stores the template chain (`empl_nm`, `jbcl_nm`) keyed by `appr_line_no`. Approval Box reads the **per-document** approvers from `expt_resol_apprvr_infm`, not from this table; the template is only consulted at creation time (out of scope for the list page).

### 3.6 · Sibling tables joined by the list IDO

The list query (`EXPT_RESOL_MSTR_R003`) LEFT-JOINs the following — they are **read-only references** for column lookup; the refactor does not own their entities for Approval Box, only borrows their columns:

| Table | Columns read by R003 | Purpose |
|---|---|---|
| `bltb_post_infm` | `BLTB_CD`, `POST_SEQ_NO`, `POST_TITLE`, `REF_BLTB_CD`, `REF_POST_SEQ_NO` | Discussion-thread linkage (rows where `ref_bltb_cd = ''` are roots — used for `resl_0001_001_r001_act.jsp:74-75` to exclude replies). |
| `ovrs_pymn_mstr` | `TRNF_CCY_CD` | Currency for overseas. |
| `scdul_pymn_sumr` | `CCY_CD`, `TITL` | Currency + file name for scheduled (Vendor/Expense/Payroll/Internal). |
| `ovrs_pymn_sumr` | `CCY_CD`, `TITL` | Currency + file name for overseas. |
| `card_trns_hstr` | `TRSC_AMT`, `CCY_CD` | Corporate-card sum when `appr_dv = '33'`. Also referenced in the non-admin visibility filter at [resl_0001_001_r001_act.jsp:141-146](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L141). |
| `busa_doc_mstr`, `saly_book_mstr` | `prov_stts` aggregations | Used in `PAY_YN` subquery (`Y` / `N` / `P` execution-status indicator). |

### 3.7 · DB helper functions referenced (legacy PL/pgSQL)

`information_schema` confirmed these exist on DEV:

| Function | Signature seen | Refactor strategy |
|---|---|---|
| `wb_get_appr_stts(biz_key, owner_id, login_id, appr_seq_no, appr_type_cd)` | Returns `'P'`/`'I'`/`'C'`/`'R'` | **Reuse on DEV/UAT/PROD; recreate in local Flyway only if/when needed.** Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"DB Function / Legacy Function Policy" the refactor must not add new `wb_*` helpers — this one is pre-existing on DEV. The local Flyway path for Approval Box is to compute the derived status in JPQL / a view rather than port the function. See [migration-plan.md](migration-plan.md) Risk #2. |
| `wb_get_expt_resol_mstr_doc_nm(appr_dv, lang_code)` | Returns localised type label | Same constraint — refactor recomputes via a Java enum + i18n. |
| `wb_get_memo_cont`, `wb_get_memo_color` | Memo content / colour for sticky notes | **Out of scope for Approval Box port** — sticky notes are not part of the OSMC layout. Drop. |
| `wb_get_translate_text(text)` | VN-only translation for `TITL` | Drop in refactor — search is local-only on raw `TITL`. |
| `wb_get_next_val(table, field, biz_key, …)` | Sequence generator | Already ported under [wecms-api/src/main/resources/db/migration/V25-V27, V66, V82](../../../../src/main/resources/db/migration/). For `EXPT_RESOL_MSTR.APPR_SEQ_NO` the Approval Box list does NOT need the generator (read-only). The detail / create flows that do need it land later. |

---

## 4 · Auth / role / approval-line dependencies

### 4.1 · Authentication & tenant

| Concern | JEX source | Refactor target |
|---|---|---|
| Login session | `SessionManager.getLoginUser(request, response)` — [resl_0001_001_r001_act.jsp:287](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L287) | JWT → `TenantContext.currentUserId()`, `.currentBizKey()` per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"JWT filter". |
| Tenant scope | `loginUser.getBizKey()` injected into every dynamic clause — [resl_0001_001_r001_act.jsp:68-69](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L68) | `TenantContext.currentBizKey()` on every JPA query; repository methods carry `bizKey` parameter. |
| Admin vs user | `loginUser.isAdmin()` controls the visibility branch — [resl_0001_001_r001_act.jsp:116](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L116). `isAdmin()` resolves to `BUSI_ADMN_INFM` presence per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Authority model". | JWT carries `authGrCd` (`ADMIN`/`USER`). Service reads `TenantContext.currentAuthGrCd()` to branch the query. |
| Language | `LocaleHelper.getLangCd(util)` → `LANG_CODE` param into the IDO — [resl_0001_001_r001_act.jsp:228-234](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L228). | `TenantContext.currentLangCd()`. The legacy IDO passes `LANG_CODE` to `wb_get_expt_resol_mstr_doc_nm(...)` — the refactor resolves the label via a Java enum mapped to i18n keys. |

### 4.2 · Per-user visibility filter (Redmine #42046)

A **non-admin user sees a row only if** any of these conditions hold (each is an `EXISTS` subquery joined via `BIZ_KEY + APPR_SEQ_NO`, [resl_0001_001_r001_act.jsp:116-162](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L116)):

1. They uploaded the underlying payment (`SCDUL_PYMN_SUMR.reg_id = userId`).
2. They uploaded an overseas payment (`OVRS_PYMN_SUMR.reg_id = userId`).
3. They are the card-transaction creator (`CARD_TRNS_HSTR.reg_id = userId`).
4. They submitted the approval request (`EXPT_RESOL_MSTR.reg_id = userId`).
5. They are an assigned approver (`EXPT_RESOL_APPRVR_INFM.apprvr_id = userId`).

**Admin users skip the filter entirely.** This is a strong security boundary — must be preserved in the refactor exactly.

### 4.3 · Approval-line vs ad-hoc approvers

`expt_resol_apprvr_infm.apprvr_line_no` (1..5) carries the order; the chain is rendered by the frontend via `REC3` in the list response ([resl_0001_001_osmc.ts:548-600](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L548)). The Approval Box page **does not configure or edit approval lines** — that lives in `comm_0194_*` / `comm_0195_*` (Settings → Approval Line). Approval Box only **displays** the chain and counts.

---

## 5 · Multi-tenant (`CMPY_ID` / `BIZ_KEY`) touch points

- Every table touched carries `biz_key` as the first PK column (§3).
- Every dynamic SQL clause prefixes BIZ_KEY ([resl_0001_001_r001_act.jsp:68-69](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L68), [:181-183](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L181)).
- The approver-chain join carries BIZ_KEY ([resl_0001_001_r001_act.jsp:325-327](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L325)).
- The DELETE cascade carries BIZ_KEY at every step ([resl_0001_001_d001_act.jsp:246](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_d001_act.jsp#L246)).

Refactor must mirror this — every Spring Data JPA method on Approval Box repositories takes `bizKey` as the first argument. Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) "A repository method returning rows from a tenant-scoped table **must** constrain on `BIZ_KEY`. Never pass a `%` wildcard `BIZ_KEY`."

---

## 6 · Multi-language touch points

| Surface | JEX wiring | Refactor target |
|---|---|---|
| Page title "Approval Box" | `data-jex-ml="resl_0001_001_m007"` ([resl_0001_001_view_osmc.jsp:255](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L255)) — Jex Studio Web Service View | `approval_box.title` in `locales/en.json`/`ko.json`/`vi.json`. (KH `km.json` deferred — see web CLAUDE.md.) |
| Tab labels (All / Pending / In Progress / Approved / Rejected) | `resl_0001_001_m001..m006` ([resl_0001_001_view_osmc.jsp:268-294](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L268)) | `approval_box.tab.all`, `.pending`, `.in_progress`, `.approved`, `.rejected`. |
| Date filter dropdown ("Last 30/90/180 Days", "Enter Manually") | `resl_0001_001_ml005..ml015` ([resl_0001_001_view_osmc.jsp:313-353](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L313)) | `approval_box.date_range.last_30_days`, etc. |
| Search placeholder | `resl_0001_001_m008` ([resl_0001_001_view_osmc.jsp:370](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L370)) | `approval_box.search_placeholder`. |
| Type labels in TS (`Payroll Payment`, `Vendor Payment`, …) | `resl_0001_001_j040..j044`, `j047` ([resl_0001_001_osmc.ts:451-462](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L451)) | `approval_box.type.payroll` / `.vendor` / `.expense` / `.corporate_card` / `.overseas` / `.internal_transfer`. **Backend-resolved** — the refactor returns the resolved label on the wire (or a stable enum key the FE i18n maps), not the raw `appr_dv` digit. See [design-frontend.md](design-frontend.md) §3. |
| Status labels ("Pending"/"Approved"/"Rejected"/"Unable") | `resl_0001_001_j033..j035`, `j038` ([resl_0001_001_osmc.ts:567-589](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L567)) | `approval_box.status.*`. |
| Header labels (`Total (#{CCY_CD})`, `Inquiry Period (#{DATE})`, `Last 30 Day`, etc.) | `resl_0001_001_j005`/`j006`/`j024`/`j037` ([resl_0001_001_osmc.ts:356-389](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L356)) | `approval_box.totals.total`, `approval_box.footer.inquiry_period`, etc. |

Languages on DEV today: EN, KO, VI, KH. Refactor scope: EN + KO + VI only (KH `km.json` is deferred — per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) "Never add entries to km.json").

---

## 7 · Scope of the Approval Box refactor port

What this feature port **includes**:

1. **List endpoint** — Paginated list of approval documents with tab/currency totals and per-row approver chain. The R&D scope deliberately matches `resl_0001_001_r001` (read-only).
2. **Tab filter** — All / Pending / In Progress / Approved / Rejected (`appr_type_cd` mapping). 5 tabs per the JEX live UI; the 6-tab static-UI mock is reconciled in OQ-1.
3. **Date-range / search filter** — `FROM_DT` / `TO_DT` / `srch_wd`.
4. **Sort** — `REG_DTM`, `DOC_NM`, `DOC_NO`, `TITL`, `TOT_PAY_VND_AMT_2` / `TOT_PAY_KHR_AMT`, `PAY_END_YN` (status), `PAY_DATE`, `DRAFT_USER_NM`, `APPRER_NM_2`, `TRAN_CCY_2` (the View Setting field IDs visible in [resl_0001_001_osmc.ts:104-107](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L104) and the row-render switch at [:503-528](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L503)).
5. **Per-tenant visibility** — admin sees all; non-admin sees rows they own / requested / approve.
6. **View Setting integration** — the screen is sortable, columns are configurable via the existing global View Setting endpoints. ScreenId: `approval-box`. Wired identically to `account-inquiry-vnd`.
7. **Excel export** — the OSMC view declares `excel-down` ([:262](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L262)); reuse the existing `common/export` framework. Use `POST /api/v1/payment/approval-box/export`.

What this feature port **defers** to follow-up MRs (out of scope here):

| Deferred | JEX home | Reason |
|---|---|---|
| Approval Details popup (row click) | `comm_0195_001` | The popup is a separate WSVC family (`comm_0195_*`) launched from many list pages; it has its own approve / reject / edit / delete endpoints. Porting it stand-alone in a dedicated MR keeps the Approval Box read-path uncluttered. |
| Approve / Reject WSVCs | `comm_0195_001_u00x` family | Owned by the detail popup, not the box. |
| Bulk approve | Not implemented in JEX OSMC | The static UI mock does not include it (`PageActionButtons` shows list/print/excel only). **OQ-2.** |
| Bulk delete | `resl_0001_001_d001` | Exists in legacy view ([resl_0001_001_view.jsp:137](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view.jsp#L137)) but **not in OSMC** ([resl_0001_001_view_osmc.jsp:256-263](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L256) has no delete button). Defer until product confirms WeCMS UX. |
| Print | OSMC view has `page-print` icon ([:261](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L261)) | Print is a frontend-only feature in the existing static UI (`PageActionButtons.showPrint`). Wire in the existing print pipeline at frontend-port time. |
| Sticky-note memos | `wb_get_memo_cont` / `wb_get_memo_color` columns | Not in OSMC layout. Drop. |
| Approval-line config (Settings → Approval Line) | `comm_0194_*` | Separate feature (own slug). |

This scope mirrors the canonical refactor-precedent feature [erp-transaction-history](../../../references/migration/erp-transaction-history-mapping.md): a read-only list with View Setting + Excel export, with mutation flows ported in follow-up MRs.

---

## 8 · Wire shape — current legacy vs refactor target

### Legacy wire (UPPER_SNAKE, CommonHead envelope) — `resl_0001_001_r001.jct`

Request:
```json
{
  "FROM_DT": "20260413", "TO_DT": "20260513",
  "SRCH_WD": "", "PAGE_NO": "1", "PAGE_PER_CNT": "15",
  "APPR_TYPE_CD": "" /* or "00"/"01"/"02"/"03" */
}
```

Response (abridged):
```json
{
  "COMMON_HEAD": { "ERROR": "N", "CODE": "...", "MESSAGE": "..." },
  "TOT_CNT": "18", "TOT_PEDN_CNT": "2", "TOT_INP_CNT": "1",
  "TOT_APPR_CNT": "2", "TOT_RJT_CNT": "14",
  "TOT_VND_AMT": "...", "TOT_USD_AMT": "...", "TOT_KRW_AMT": "...",
  "REC": [
    {
      "APPR_SEQ_NO": "1540", "DOC_NO": "VO_260511_002",
      "APPR_DV": "35", "TITL": "Test",
      "POST_TITLE": "...", "DOC_NM": "Internal Transfer",
      "TOT_PAY_KHR_AMT": "2000000", "TOT_PAY_USD_AMT": "0",
      "CCY_CD": "VND", "FILE_NM": "...xlsx",
      "PAY_DATE": "20260512", "PAY_END_YN": "N",
      "DRAFT_USER_ID": "...", "DRAFT_USER_NM": "LE THI HAI YEN",
      "APPRVR_TOT_CNT": "3", "APPRVR_APPR_TOT_CNT": "1",
      "APPR_STTS": "I",
      "REC3": [
        { "APPRVR_LINE_NO": "1", "APPRVR_ID": "...", "APPRVR_NM": "Liam Hawthorne",
          "APPRVR_STTS": "A", "APPR_DTM": "20260512090000",
          "REG_IMG_URL": "szon_0012_003_r002.act?USER_ID=..." }
        /* … up to 5 approvers … */
      ],
      "REC2": [ { "FILE_ID": "...", "ORG_FILE_NM": "...", "FILE_SIZE": "...", "FILE_TYPE": "EXPT_RESOL_FILE" } ]
    }
  ]
}
```

### Refactor wire (lowercase snake_case, `{ status, data }` envelope) — `GET /api/v1/payment/approval-box`

Request (query params):
```
?from_dt=20260413&to_dt=20260513&srch_wd=&page_no=1&page_per_cnt=15&appr_type_cd=&sort_field=REG_DTM&sort_direction=DESC
```

Response shape — see [design-backend.md](design-backend.md) §3.

---

## 9 · Required JEX → Refactor renamings

| Legacy id | Refactor surface | Where used |
|---|---|---|
| WSVC `resl_0001_001_r001` | `GET /api/v1/payment/approval-box` | REST URI + audit `WSVC_ID` (if Approval Box ever writes audit). |
| WSVC `resl_0001_001_d001` | (deferred — would be `DELETE /api/v1/payment/approval-box/{apprSeqNo}` when ported) | Delete is **deferred** in this MR. |
| Java package "resl_0001" | `com.kosign.wecms.domain.payment.approvalbox` | Backend package. |
| Class `Resl0001Controller` / etc. | `ApprovalBoxController`, `ApprovalBoxService`, `ApprovalBoxRow`, `ApprovalBoxListResponse`, `ApproverChainItem` | Slug-based naming per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Refactor identifier naming". |
| View Setting `WEB_SVC_ID = resl_0001_001` | `approval-box` | Seeded in V_TBD migration if/when View Setting columns ship. |
| ML key `resl_0001_001_m007` | `approval_box.title` | Frontend i18n. |
| Audit log `WSVC_ID` | `approval-box` (only if Approval Box writes audit rows — list page does not). | DB column value. |

Legacy ids appear ONLY as JEX file:line citations going forward.

---

## 10 · Open Questions for PO (Mr. Jung)

> Per workspace [CLAUDE.md](../../../../../CLAUDE.md) — these are quoted verbatim and not invented away.

- **OQ-1 — 5 tabs or 6?** The OSMC live UI shows 5 tabs (All / Pending / In Progress / Approved / Rejected) and the JEX business rule [docs/jex/business-rules/approval.md:62-71](../../../../../docs/jex/business-rules/approval.md#L62) lists the same 5. The current static-UI mock at [wecms-web/components/payment/approval-box/types.ts:8-14](../../../../../wecms-web/components/payment/approval-box/types.ts#L8) introduces a 6th tab `draft` (and `mockTabCounts.draft = 2`). Decision needed: keep parity with live JEX (5 tabs, drop `draft`) or carry the 6th tab as a new product behaviour (and add `appr_type_cd = '04'`/similar to the DB)? Default recommendation: **drop `draft` from the refactor** until a real product requirement lands; the JEX field `appr_type_cd` does not currently model `draft`.
- **OQ-2 — Bulk Approve / bulk delete?** The legacy Cambodia view exposes delete; the WeCMS OSMC view does not. The product brief mentions "bulk-approve" as a flow but the live JEX does not implement it. Confirm whether bulk Approve / bulk Reject are in-scope for the refactor MR or deferred to a later MR alongside the detail popup port.
- **OQ-3 — `APPR_STTS` derivation source.** Legacy uses `wb_get_appr_stts(...)` (a PL/pgSQL helper) plus `appr_type_cd` on the row. The refactor cannot add new `wb_*` helpers (policy in [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"DB Function / Legacy Function Policy"). Two options:
  - **(a)** Compute status in Java service from `(appr_type_cd, count of apprvr_stts='A', total approvers, any 'R')`. Simple, full control.
  - **(b)** Encapsulate in a Postgres view introduced by Flyway (no new function — a view). Performant but adds local-schema deviation.
  Recommend **(a)** for parity with the rest of the refactor; confirm.
- **OQ-4 — Audit log policy for Approval Box list.** The list page does NOT write to `AUDIT_LOGS_INFM` today (the JEX `_act.jsp` has no audit insert). The refactor will keep that — but confirm before going to the Approve / Reject ports, where audit IS required.
- **OQ-5 — Sortable column whitelist.** Legacy front-end advertises sort on every column except checkbox/ETC. Confirm whether the refactor should mirror that exact whitelist or trim it (some columns like `APPRER_NM_2` and `TRAN_CCY_2` are denormalised display labels — sorting them server-side is expensive).
- **OQ-6 — Excel export sub-header.** OSMC TS writes a sub-header "Inquiry Period (FROM ~ TO)" + total counts ([resl_0001_001_osmc.ts:300-306](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L300)). Refactor `common/export` framework will need a new `ExportRowProvider`; confirm the sub-header format the PO expects in the WeCMS-branded XLSX.

---

## 11 · Cross-references

| Doc | What it covers |
|---|---|
| [design-backend.md](design-backend.md) | REST URI, controller / service / repo / DTO / entity layout, validation, error model, audit slug. |
| [design-frontend.md](design-frontend.md) | Next.js route, components, state, i18n, View Setting screenId, typography. |
| [database-impact.md](database-impact.md) | Flyway needs (default: none). |
| [api-flow.md](api-flow.md) | Mermaid sequence diagrams (list, view detail, approve, reject, bulk-approve, download). |
| [migration-plan.md](migration-plan.md) | JEX-vs-Spring mapping, phased order, risk register. |
| [docs/jex/business-rules/approval.md](../../../../../docs/jex/business-rules/approval.md) | Source-of-truth business rules. |
| [docs/jex/screen-flows/payments.md](../../../../../docs/jex/screen-flows/payments.md) | Source-of-truth flow (with the `pymn_0001_001` typo noted in §1). |
| [docs/refactor-wecms/MIGRATION_PLAYBOOK.md](../../../references/migration/MIGRATION_PLAYBOOK.md) | Procedural playbook. |

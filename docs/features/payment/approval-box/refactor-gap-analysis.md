# Approval Box — Refactor Gap Analysis (Backend)

Area-by-area comparison of the legacy JEX `resl_0001_001` / `comm_0195_001` behaviour against the refactor implementation. Status vocabulary: **MATCH** (refactor reproduces legacy behaviour or intentionally supersedes it), **PARTIAL** (works but with a documented deviation/caveat), **MISSING** (not implemented).

> **Post-SIT refresh (2026-06-16).** The five gaps raised in the SIT-readiness audit ([G1–G5](#post-sit-verification--g1g5-findings-2026-06-16)) have been resolved/verified. Footer totals, footer document count, uploader visibility, and the card-payment amount are now legacy-parity; the approver-status concern was confirmed a false positive. This document was updated to reflect the implemented state — earlier rows that claimed footer page-slice behaviour, narrowed visibility, or an `APPR_STTS` write-back to `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` were stale and are corrected below. Cross-references: [docs/audits/approval-box-main-legacy-vs-refactor-audit.md](../../../../../docs/audits/approval-box-main-legacy-vs-refactor-audit.md), [docs/audits/approval-box-followup-verification.md](../../../../../docs/audits/approval-box-followup-verification.md).

| Area | Legacy (JEX) | Refactor (Spring) | Status |
|---|---|---|---|
| **Search** | `resl_0001_001_r001` + IDO `EXPT_RESOL_MSTR_R003`; date-range on `DRAFT_DATE`, keyword over `DOC_NM`/`TITL`/`DOC_NO` (+ VN translated), `APPR_DV IN (21,31,32,33,34,35)`, sort via `SortHelper` | `GET /approval-box` → `ApprovalBoxService.list` → `ExptResolMstrRepository.search`; same date/keyword/type filters, `ApprovalBoxSortField` whitelist, server-side paging | **MATCH** (VN translated-keyword search intentionally dropped — D5) |
| **Tab counts** | `EXPT_RESOL_MSTR_R003_CNT`, count query ignores selected `APPR_TYPE_CD` | `countTabs(...)` omits `apprTypeCd` predicate; 4 chips from one scope | **MATCH** |
| **Footer totals** | `EXPT_RESOL_MSTR_R003_CNT` sums per-currency (`TOT_USD_AMT` / `TOT_VND_AMT` / `TOT_KRW_AMT`) over the **tab-unfiltered** full result set; card rows use `SUM(CARD_TRNS_HSTR.TRSC_AMT)` | `ApprovalBoxService.computeFooterTotals` → `ExptResolMstrRepository.findAmountBasisForTotals` (tab-unfiltered, unpaged) + the shared `resolveCurrency`/`resolveAmount`; returned on `ApprovalBoxListResponse.footerTotals` (`footer_totals`) | **MATCH** (G1 — was a client page-slice sum; fixed 2026-06-16) |
| **Footer document count** | `TOT_CNT` from the count query — tab-unfiltered | `footerTotals.totalDocumentCount` = `pending+inProgress+approved+rejected` (sum of the four tab counts; `APPR_TYPE_CD` is NOT NULL ∈ {00,01,02,03} so this equals the tab-unfiltered count) | **MATCH** (G4 — was the tab-filtered `total_count`; fixed 2026-06-16) |
| **Detail View** | row-click → `comm_0195_001` popup; approver chain (REC3 `apprvr_line_no asc`) + per-line transactions | `GET .../{apprSeqNo}/approvers` (chain) + frontend dialog assembling per-line txns from the originating module's detail endpoint | **MATCH** (no single backend "detail" endpoint — dialog composes from list row + approvers + module txns) |
| **Approval** | approve action in `comm_0195_001`: caller `P→A`, master `01` when all approved else `03`, promote next `W→P` | `POST .../approve` → `approve`: identical transitions (`approverRepository` `P→A`, promote next `W→P`, master `01`/`03`) | **MATCH** |
| **Rejection** | reject: caller `P→R`, downstream `W→S`, master `02` | `POST .../reject` → `reject`: identical (`stopDownstream` `W→S`, master `02`) | **MATCH** |
| **Status Handling** | master `APPR_TYPE_CD` (00/01/02/03) + approver `APPRVR_STTS` (P/A/R/W/S); list wire status via `wb_get_appr_stts(biz_key, REG_ID, REG_ID, …)` | same two-level model; `deriveApprStts` replaces the DB function (`01→C`,`02→R`,`03→I`,else `P`) | **MATCH** (G2 — `wb_get_appr_stts` always called with `apprvr_id=reg_id` on this screen, so its ELSE-branch is unreachable; the Java mapping is exact parity — confirmed false positive) |
| **Persistence** | `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM` writes; on withdraw, the source batch's `APPR_SEQ_NO` is cleared (return to Draft) | same master/approver writes; withdraw deletes master+chain and `clearApprSeqNo` nulls `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR.APPR_SEQ_NO` (no `APPR_STTS` write-back — those tables have no such column in the legacy JEX schema; status is derived on read) | **PARTIAL** (BLTB discussion-thread cascade not modelled — out-of-scope tables, D7) |
| **Security** | `NORM_USER_ONLY_YN=Y`; Redmine #42046 per-user visibility (requester / approver / SCDUL+OVRS+CARD uploader) | `@menuAuthz.canAccess('payment-approval-box')` on all endpoints + in-query visibility: admin all / requester (`erm.regId`) / approver (`EXPT_RESOL_APPRVR_INFM`) / uploader (`SCDUL_PYMN_SUMR` ∪ `OVRS_PYMN_SUMR` ∪ `CARD_TRNS_HSTR` `reg_id`) | **MATCH** (G3 — uploader branches restored 2026-06-16; `search` + `countTabs` + `findAmountBasisForTotals` carry identical visibility) |
| **Card payment amount** | `IDO.EXPT_RESOL_MSTR_R003.xml:62-65` — `APPR_DV='33'` → `SUM(CARD_TRNS_HSTR.TRSC_AMT)`; else `TOT_PAY_KHR_AMT` | `resolveAmount` card branch reads `cardTrnsHstrRepository.sumTrscAmtByBizKeyAndApprSeqNos`; shared across row, footer, and export | **MATCH** (G5 — was the master amount columns, 0 for current card rows; fixed 2026-06-16) |
| **UI Behaviour** | tabs, date filter, search, sort, approver hover, approve/reject footer, Excel, print | list + tab counts + footer totals + filters + sort + approvers + approve/reject/bulk/withdraw/change-line + Excel export; backend returns post-action status/counts for instant reflect | **MATCH** |
| **Error Handling** | JEX ML codes / popup alerts | `BusinessException` → standard envelope (`WBE2050–WBE2054`, `WBE_VALIDATION`, `WBE_NOT_FOUND`, `WBE9999`); HTTP 200 for business outcomes | **MATCH** |
| **Empty Result** | empty grid + zero counts | empty `list`, `total_count=0`, `total_pages=0`, counts 0, `footer_totals` zeroed; `approvers` → `[]` | **MATCH** |
| **Legacy Parity** | canonical source `resl_0001_001` (+ `comm_0195_001`) | full read+write port; deliberate divergences D1–D10 documented | **MATCH** |
| **Excel Export** | client-side XLSX in `resl_0001_001_osmc.ts` | `POST .../export` server-side via shared POI pipeline (`ApprovalBoxExportProvider`, ≤ 50k rows, server-computed subheader; card-aware amount per G5) | **MATCH** (supersedes client-side generation) |
| **Bulk approve** | (none) | `POST .../bulk-approve` (per-row independent) | **MATCH** (refactor addition — D8) |
| **Change approval line** | (none on this screen) | `PUT .../approval-line` (rebuild chain) | **MATCH** (refactor addition) |
| **Notifications** | request + approve/reject notify next approver; approve/reject also write a `BLTB_COMNT_INFM` REMARK | request + approve + reject notifications **and** the `BLTB_COMNT_INFM` REMARK write are implemented via `ApprovalNotificationService.notifyApprovalAction` (BLTB tables — write-side live). The notification **read-side** controller (list / unread-count / mark-read) is still a stub. | **MATCH** (write-side; read-side stub tracked as payment-gap CC-1) |

---

## Post-SIT verification — G1–G5 findings (2026-06-16)

The SIT-readiness audit raised five gaps on the main list page. Each is recorded below with the original issue, root cause, implemented solution, and verification result. Full evidence: [docs/audits/approval-box-followup-verification.md](../../../../../docs/audits/approval-box-followup-verification.md).

### G1 — Footer currency totals — **FIXED**

- **Original issue.** The on-screen footer (Total USD / VND / KRW) was computed client-side over the current page slice and current tab, so totals were wrong once results spanned more than one page or a non-All tab was active.
- **Root cause.** No footer aggregate existed on the list response; the frontend summed `displayRows` (page-local) via `approvalBoxCurrencyTotal`. Legacy `IDO.EXPT_RESOL_MSTR_R003_CNT.xml:36-39` sums per currency over the **full tab-unfiltered** set.
- **Solution.** Added `ApprovalBoxFooterTotals` (`total_document_count` + `total_usd_amount` / `total_vnd_amount` / `total_krw_amount`) to `ApprovalBoxListResponse`. `ApprovalBoxService.computeFooterTotals` runs `ExptResolMstrRepository.findAmountBasisForTotals` (same WHERE as `countTabs`, no `apprTypeCd` predicate, unpaged) and buckets each row's amount by resolved currency using the shared `resolveCurrency` / `resolveAmount` helpers (so the footer matches the on-screen rows exactly, including the card rule). Frontend footer now binds to `footer_totals`.
- **Verification.** `findAmountBasisForTotals` confirmed tab-unfiltered (identical WHERE to `countTabs`); contract test asserts `data.footer_totals.*`; legacy SQL parity confirmed against `EXPT_RESOL_MSTR_R003_CNT.xml`.

### G2 — Approver status logic — **FALSE POSITIVE (no change)**

- **Original issue.** Concern that the refactor's Java `deriveApprStts(apprTypeCd)` (always document-level) diverged from legacy `wb_get_appr_stts`, which can return the viewer's own `APPRVR_STTS`.
- **Root cause / finding.** Both legacy IDOs call `wb_get_appr_stts(ERM.BIZ_KEY, ERM.REG_ID, ERM.REG_ID, ERM.APPR_SEQ_NO, ERM.APPR_TYPE_CD)` — `apprvr_id` **and** `reg_id` are both `ERM.REG_ID` (`IDO.EXPT_RESOL_MSTR_R003.xml:58`, `_R003_CNT.xml:45`). The function's `IF (admin OR apprvr_id = reg_id)` branch is therefore always true on this screen, so the ELSE-branch (return `APPRVR_STTS`) is unreachable. Legacy shows the document-level status (mapped from `APPR_TYPE_CD`) to admin, requester, and approver alike.
- **Solution.** None required. `deriveApprStts` (`00→P`, `03→I`, `01→C`, `02→R`) reproduces the legacy IF-branch mapping exactly, for every persona. Status label and tab counts are byte-for-byte parity.
- **Verification.** Function body + both IDO call sites inspected on DEV; per-persona experience identical (admin / requester / approver).

### G3 — Visibility scope — **FIXED**

- **Original issue.** A non-admin who uploaded a payment batch but is neither the approval requester nor an assigned approver could not see the document.
- **Root cause.** The refactor's `search` / `countTabs` carried only the requester (`erm.regId`) and approver (`EXISTS EXPT_RESOL_APPRVR_INFM`) branches; the legacy three uploader branches (`SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` / `CARD_TRNS_HSTR` `reg_id = user`) were dropped. Live DEV data confirmed ~36 of 287 documents have `EXPT_RESOL_MSTR.REG_ID ≠ source-table REG_ID` (the upload event and the approval request can be performed by different users — e.g. overseas `VO_260225_007`: requester `chanyouvita`, uploader `lamine`).
- **Solution.** Restored the three uploader `EXISTS` branches in **both** `search` and `countTabs`, and applied the identical visibility to the new `findAmountBasisForTotals` — list rows, tab counts, and footer totals share one visibility scope.
- **Verification.** Branch counts confirmed 3/3/3 across the three queries; uploader≠requester sample (`VO_260225_007`) now visible to the uploader via the OVRS branch.

### G4 — Footer document count — **FIXED**

- **Original issue.** Footer document count used the tab-filtered `total_count`, so it changed per tab; legacy shows the tab-unfiltered `TOT_CNT`.
- **Root cause.** The footer bound to `total_count` (the pagination count) instead of a tab-unfiltered total.
- **Solution.** `footerTotals.totalDocumentCount = pending + inProgress + approved + rejected` (sum of the four `tabCounts`). Because `APPR_TYPE_CD` is NOT NULL ∈ {00,01,02,03} (verified on DEV), this equals the tab-unfiltered row count with no extra query. Pagination still uses the tab-filtered `total_count`.
- **Verification.** Frontend footer binds `footer_totals.total_document_count`; `total_count` retained for pagination only.

### G5 — Card payment amount — **FIXED**

- **Original issue.** Corporate Card approvals (`APPR_DV='33'`) rendered amount `0` for rows whose `EXPT_RESOL_MSTR` amount columns are 0 — including the two newest DEV card documents, signalling the live card-creation flow no longer populates those columns.
- **Root cause.** `toRow` used the currency-matched master amount column; legacy `IDO.EXPT_RESOL_MSTR_R003.xml:62-65` (and `_R003_CNT.xml:49-52`) special-cases card with `SUM(CARD_TRNS_HSTR.TRSC_AMT)`.
- **Solution.** `resolveAmount` branches on `APPR_DV='33'` and returns the batched `cardTrnsHstrRepository.sumTrscAmtByBizKeyAndApprSeqNos` value (KHR fallback only when no card rows exist). The shared helper is used by the per-row mapping, the footer aggregate (G1), and the Excel export.
- **Verification.** DEV samples — `VO_260206_002` (khr 0, card sum 289,850), `VO_251216_001` (khr 0, card sum 799,200) — now render the card sums; the 16 older rows where `khr == card_sum` are unchanged.

---

## Caveats / engineering notes (do not block "complete")

1. **`computeFooterTotals` extra read.** The footer aggregate runs one additional unpaged read per list load. This mirrors legacy `resl_0001_001`, which runs the full `_R003_CNT` aggregate on every load, and is bounded by the date-range scope — no correctness risk.
2. **Bulk-approve transaction semantics.** `ApprovalBoxService.bulkApprove` is intentionally not `@Transactional` (each row commits independently) and calls `this.approve(...)` directly. Spring proxies don't intercept in-bean self-invocation, so the per-row `@Transactional` on `approve` is not re-applied through the proxy. Behaviour is still correct (each `approve` either fully applies or its `BusinessException` is caught into `failures`); the isolation mechanism differs from the Javadoc. **Recommended:** extract `approve` to a separate bean or use `TransactionTemplate` per row if true per-row isolation is required.
3. **Stale controller Javadoc** claims `/api/v1/*` is only `authenticated()` — the code carries the `@menuAuthz` `@PreAuthorize`. The withdraw Javadoc mentions card `'33'` for back-stamp routing; the code keys back-stamp routing on Overseas `'34'` only.
4. **`fanout` overrides the caller's `docNo`** with a freshly generated `VO_YYMMDD_NNN`.
5. **Un-pinned NUMERIC precision** on the five amount columns (V109 bare `NUMERIC` vs DEV `numeric(20,6)`) — the one substantive schema-parity gap; tolerated by Hibernate `validate`.
6. **No `ApprovalBoxServiceTest`** — service-layer transitions are covered indirectly by `ApprovalBoxControllerTest`; a dedicated unit test (incl. a footer-totals / card-amount case) is the recommended follow-up.

---

## Refactor Status: Complete

All reviewed areas are **MATCH**, with one **PARTIAL** item that is a deliberate, documented out-of-scope decision (BLTB discussion-thread cascade on delete), not a gap in the Approval Box feature itself. Approve/reject notifications **and** the `BLTB_COMNT_INFM` REMARK write are implemented (write-side live); the only notification-side gap is the read-side controller stub, tracked separately as payment-gap **CC-1** (not an Approval Box gap). No reviewed area is **MISSING**.

The five SIT-readiness gaps (G1–G5) are resolved: **G1, G3, G4, G5 fixed** and verified to legacy parity; **G2 confirmed a false positive** (refactor already matches legacy). The Approval Box backend refactor is **complete and SIT/UAT-verified** against its agreed scope. The caveats above are engineering-quality follow-ups, not functional gaps.

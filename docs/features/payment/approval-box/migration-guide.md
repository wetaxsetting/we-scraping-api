# Approval Box — Migration Guide (JEX → Spring)

Maps the legacy JEX `resl` / `comm_0195_001` implementation to the refactor Spring Boot implementation. JEX paths are read-only reference under `WECMS DEV/`. Deep discovery + the original phased plan live in [discovery.md](discovery.md) and [migration-plan.md](migration-plan.md); this guide is the consolidated component-by-component mapping for the **completed** feature.

---

## 1. Component mapping

| Legacy (JEX, read-only) | Refactor (slug-based) |
|---|---|
| Menu `resl_0001_001` | Route `/payment/approval-box`; View Setting `WEB_SVC_ID = "approval-box"` |
| List action `resl_0001_001_r001_act.jsp` + WSVC `resl_0001_001_r001` | `GET /api/v1/payment/approval-box` → `ApprovalBoxController#list` → `ApprovalBoxService#list` |
| IDO `EXPT_RESOL_MSTR_R003` (row query) | `ExptResolMstrRepository.search(...)` (JPQL constructor projection → `ApprovalBoxProjection`) |
| IDO `EXPT_RESOL_MSTR_R003_CNT` (tab + currency totals) | `ExptResolMstrRepository.countTabs(...)` (tab counts) + `ApprovalBoxExportProvider` (export currency totals) |
| REC3 approver query (`ExptResolApprvrInfmDAO`, `order by apprvr_line_no asc`) | `ExptResolApprvrInfmRepository.findChain(...)` → `GET .../{apprSeqNo}/approvers` |
| Detail popup `comm_0195_001` (approve/reject host) | Frontend Approval Details dialog + `POST .../{apprSeqNo}/approve` / `.../reject` |
| Delete action `resl_0001_001_d001_act.jsp` + WSVC `resl_0001_001_d001` | `DELETE .../{apprSeqNo}/withdraw` → `ApprovalBoxService#withdraw` → `ApprovalBoxFanoutService#withdrawApproval` |
| Client XLSX in `resl_0001_001_osmc.ts` | `POST .../export` → `ExcelExportService` + `ApprovalBoxExportProvider` |
| (no legacy equivalent) | `POST .../bulk-approve`, `PUT .../approval-line` (refactor additions) |
| `ExptResolMstr` / `ExptResolApprvrInfm` JEX entities | `entity/ExptResolMstr` / `ExptResolApprvrInfm` |
| `ExptResolMstrDAO`, `ExptResolApprvrInfmDAO` | `ExptResolMstrRepository`, `ExptResolApprvrInfmRepository` |

---

## 2. Function / helper translations

| Legacy helper (DB function or `wb_*`) | Refactor replacement |
|---|---|
| `wb_get_appr_stts(...)` | Java `ApprovalBoxService.deriveApprStts(apprTypeCd)` — `01→C`, `02→R`, `03→I`, else `P` |
| `wb_get_expt_resol_mstr_doc_nm(appr_dv, lang)` | `ApprDv` enum slug + frontend i18n on `type_label` |
| `wb_get_memo_cont` / `wb_get_memo_color` | Dropped (sticky-note memos out of scope) |
| `wb_get_translate_text(TITL)` (VN translated keyword search) | Dropped (out of scope; keyword matches `docNm`/`titl`/`docNo` only) |
| `SortHelper.getSortDynamic("resl_0001_001_r001")` | `ApprovalBoxSortField` whitelist + Spring Data `Sort` |
| Doc-no `SeqHelper`/`SYS_DOC_ID_GEN` | `ApprovalBoxFanoutService.nextDocNo` → `findMaxDocNoSeqByBizKeyAndPrefix` (`VO_YYMMDD_NNN`) |

No new `wb_*` DB functions were ported — all reimplemented in Java.

---

## 3. Field-by-field (list row)

| Legacy IDO `EXPT_RESOL_MSTR_R003` output | Wire field | Source in refactor |
|---|---|---|
| `APPR_SEQ_NO` | `appr_seq_no` | `ExptResolMstr.apprSeqNo` |
| `DOC_NO` | `doc_no` | `ExptResolMstr.docNo` |
| `TITL` | `title` | `ExptResolMstr.titl` |
| `DOC_NM = wb_get_expt_resol_mstr_doc_nm(...)` | `type_label` | `ApprDv.slug()` (frontend localises) |
| `APPR_DV` | `appr_dv` | `ExptResolMstr.apprDv` |
| `CONT` | `cont` | `ExptResolMstr.cont` |
| `CCY_CD = COALESCE(OPM.TRNF_CCY_CD, SPS.CCY_CD, OPS.CCY_CD, …)` | `currency` | `COALESCE(OVRS_PYMN_SUMR.CCY_CD, SCDUL_PYMN_SUMR.CCY_CD)`, default `VND` |
| `TOT_PAY_KHR_AMT` (CASE for card `SUM(TRSC_AMT)`) | `tot_pay_amt` (string) | amount column picked by currency (USD→`totPayUsdAmt`, VND→`totPayVndAmt`, else `totPayKhrAmt`) |
| `FILE_NM = COALESCE(SPS.TITL, OPS.TITL)` | `file_name` | **`COALESCE(SCDUL_PYMN_SUMR.ORG_FILE_NM, OVRS_PYMN_SUMR.ORG_FILE_NM)`** — refactor moved file name to `ORG_FILE_NM` (parity fix) |
| `PAY_DATE` | `pay_date` | `ExptResolMstr.payDate` |
| `DRAFT_USER_ID` / `_NM` | `draft_user_id` / `_nm` | `ExptResolMstr.draftUserId` / `draftUserNm` |
| `REG_DTM` | `reg_dtm` | `ExptResolMstr.regDtm` |
| `APPR_STTS = wb_get_appr_stts(...)` | `appr_stts` | `deriveApprStts(apprTypeCd)` |
| approver `OBJ` json (`APPRVR_TOT_CNT`, `APPRVR_APPR_TOT_CNT`) | `apprvr_tot_cnt` / `apprvr_appr_tot_cnt` | correlated COUNT subqueries in `search` |
| `PAY_END_YN` | `pay_end_yn` | `ExptResolMstr.payEndYn` |

---

## 4. Tab-code mapping

| Tab | Legacy `data-cd` (`APPR_TYPE_CD`) | Wire `appr_type_cd` |
|---|---|---|
| All | `""` | (omitted) |
| Pending | `00` | `00` |
| In Progress | `03` | `03` |
| Approved | `01` | `01` |
| Rejected | `02` | `02` |

Legacy tab counts ignore the selected tab (the action JSP suppresses the `APPR_TYPE_CD` clause for the count query) — the refactor mirrors this via `countTabs` omitting the `apprTypeCd` predicate.

---

## 5. Deliberate divergences

| # | Legacy | Refactor | Why |
|---|---|---|---|
| D1 | KHR/USD amount split across columns | single `tot_pay_amt` + `currency` on the wire | clean wire contract; amount column picked server-side |
| D2 | type label from DB function | `ApprDv` slug + frontend i18n | label localisation moves to the client |
| D3 | status from `wb_get_appr_stts` | Java `deriveApprStts` | no DB-function dependency |
| D4 | sticky-note memos (`wb_get_memo_*`) | dropped | out of scope |
| D5 | VN translated keyword search (`wb_get_translate_text`) | dropped | out of scope |
| D6 | file name from `SPS.TITL` | `ORG_FILE_NM` | refactor stores the file name in `ORG_FILE_NM` |
| D7 | hard-delete cascade across BLTB threads/files on delete | withdraw deletes master + chain, returns source to Draft | refactor doesn't model the discussion-board thread; out-of-scope tables excluded by the PROD active-list rule |
| D8 | no bulk approve | `POST .../bulk-approve` | refactor addition (per-row independent approve) |
| D9 | approve/reject owned by `comm_0195_001` popup | dedicated `approve`/`reject` endpoints | clean REST surface |
| D10 | doc number from `SYS_DOC_ID_GEN` | `VO_YYMMDD_NNN` via `nextDocNo` | per-tenant per-day Java generator; caller doc-no overridden |

---

## 6. Out-of-scope (excluded by the PROD active-list / scope rules)

- ERP synchronization on approval.
- Discussion-board thread (`BLTB_*`) creation/deletion that legacy delete cascades over.
- Approval **comment** notification creation (request/approve/reject notifications ARE implemented).
- Uploader-visibility branch beyond the implemented requester/approver/admin visibility.
- Sticky-note memos; VN translated keyword search.

---

## 7. Migration status

✅ **Complete.** Read path (list, tab counts, **footer totals**, filters, sort, approver chain), mutation path (approve, reject, bulk-approve, withdraw, change-approval-line), withdraw's source `APPR_SEQ_NO` clear (no `APPR_STTS` write-back — the column does not exist in the legacy JEX schema; status is derived on read), audit, notifications, and Excel export are all implemented and verified against the legacy behaviour (SIT/UAT done, incl. the 2026-06-16 G1–G5 verification). The only documented behaviour deltas are the deliberate divergences above and the post-Phase-1 parity fixes in [parity-fixes-2026-06.md](parity-fixes-2026-06.md). See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the area-by-area verdict + the G1–G5 findings.

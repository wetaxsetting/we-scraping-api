# Transfer Result (Internal Transfer) — F-1 / F-2 / F-3 SIT-Readiness Audit & Fixes

**System:** Refactor (wecms-api + wecms-web)
**Feature:** Payment › Internal Transfer › Transfer Result (list + detail)
**Legacy counterpart:** `pymn_0010_001` (list) / `pymn_0010_002` (detail), both delegating to `PymnHelper.pymn_0002_001_r001` / `pymn_0002_002_r001` with `SCDUL_PYMN_TYPE_CD='03'`
**Date:** 2026-06-16
**Method:** Read-only legacy/refactor source trace + read-only `postgres_dev_server_jexframe` (JEX DEV) data verification. No DB schema, migration, data, or backfill changes.

This audit raised three independent parity findings on the read path. F-1 and F-3 are fixed (code-only, read-path-only); F-2 is left as a PO decision.

---

## F-1 — Date filter / sort / display column → FIXED ✅

### Root cause
Legacy `SCDUL_PYMN_SUMR_R007` / `_R007_CNT` / `_R008` filter the Transfer Result list on `LEFT(sps.trnf_dtm, 8) BETWEEN ? AND ?` (TRNF_DTM = dispatch/execution datetime) and the IDO aliases `T.TRNF_DTM AS REG_DTM` on output. The refactor mistook that output alias for a genuine `REG_DTM` filter and constrained, sorted, and displayed `REG_DTM` (batch creation). DEV proof: all 23 dispatched type-03 batches have `reg_dtm <> trnf_dtm`; batch `SCDUL_PYMN_NO=275` (reg `2025-12-18`, trnf `2026-01-07`) appears in a January search under legacy but vanished under the refactor; a Jan-2026 window showed 4 rows / 400,000 (legacy) vs 3 / 300,000 (refactor).

### Implemented fix
- `ScdulPymnSumrRepository.searchDispatched` date predicate `s.regDtm` → `s.trnfDtm` (kept `s.trnfYn='Y'`).
- `InternalTransferResultService.pageable` default + tie-break sort `regDtm` → `trnfDtm`.
- `InternalTransferResultService.toResultRow` displayed Date/Time `s.getRegDtm()` → `s.getTrnfDtm()` (wire field name `reg_dtm` unchanged — mirrors the legacy `TRNF_DTM AS REG_DTM` alias).
- `InternalTransferResultSortField.TRAN_DT` `"regDtm"` → `"trnfDtm"`.
- The sibling `search` query (Scheduled-Transfer list, legacy `pymn_0009_001`) **left on `regDtm`** — that is correct legacy parity.

### Status: PASS — filter, sort, and displayed Date/Time all use TRNF_DTM, matching legacy.

---

## F-3 — "Approval Title" column source → FIXED ✅

### Root cause
Legacy list `SCDUL_PYMN_SUMR_R007` sources the **Approval Title** column from `ERM.TITL` (`EXPT_RESOL_MSTR`, joined by `APPR_SEQ_NO`) and the File Name column from `SPS.TITL AS FILE_NM`. The refactor sourced Approval Title from `SCDUL_PYMN_SUMR.TITL`. The DTO Javadoc had wrongly assumed `SPS.TITL` was "the approval-request title"; DEV data shows `SPS.TITL` is the upload subject (≈ file base) while the real approval title lives in `ERM.TITL`.

DEV proof (type-03, n=55): `org_file_nm_blank = 0` (File Name column never blank — original migration-risk hypothesis disproved), and `sps_titl <> erm_titl` for **53 of 53** approved rows (e.g. `SPS.TITL="WeCMS_Internal_Transfer_upload_EN"` vs `ERM.TITL="Dev Team"`). So the refactor displayed a different Approval Title than legacy on 100% of approved rows. No data defect — `ERM.TITL` is fully populated; the refactor simply wasn't reading it. **No migration/backfill required.**

### Save-flow verification (not assumed)
Traced the refactor write paths before choosing the COALESCE direction:
- **Create/upload** (`InternalTransferService:514,526`): `SPS.TITL = req.titl()`; `ORG_FILE_NM = uploaded file name`.
- **requestApproval / stampApproval** (`InternalTransferService:1039,1084`): overwrites `SPS.TITL = postTitle` **and** writes `ERM.TITL = postTitle` — or `ERM.TITL = ""` (empty string, **never SQL NULL**) when no post title is supplied.

So legacy rows carry the real title in `ERM.TITL` (`SPS.TITL` = file base); refactor rows carry the post title in both, with `ERM.TITL` possibly blank. Correct direction = **`ERM.TITL` first, `SPS.TITL` fallback**, and the fallback must be **blank-aware** (null OR blank) because the refactor writes `""`, not NULL.

### Implemented fix (LIST read path only)
- `InternalTransferResultService.list` builds `buildApprovalMstrMap(...)` returning `Map<String, ExptResolMstr>` (replacing the `DOC_NO`-only `buildDocNoMap`), reusing the **single existing** `ExptResolMstrRepository.findByBizKeyAndApprSeqNoIn` lookup that already supplied `DOC_NO`.
- `toResultRow` sets the wire `titl` (Approval Title) = `COALESCE(NULLIF(ERM.TITL,''), SPS.TITL)`: `ermTitl` when non-blank, else `s.getTitl()`. Covers legacy rows (→ ERM.TITL), refactor blank-title rows (→ SPS.TITL), and no-approval-row rows (→ SPS.TITL).
- **File Name unchanged** — still `ORG_FILE_NM` (`s.getOrgFileNm()`).
- **DOC_NO unchanged** — still `ERM.DOC_NO`; a null `DOC_NO` still surfaces as a null Approval Ref. (the prior `.filter(m -> m.getDocNo() != null)` only affected map membership, not the per-row null result).

### DETAIL page — intentionally NOT changed (evidence-based)
Legacy detail `IDO.SCDUL_PYMN_SUMR_R005.xml:52` selects **`SPS.TITL`** (the ERM join there supplies only DRAFT_USER / DOC_NO / APPR_FUND_TP — **not** the title). The refactor detail already returns `master.getTitl()` (= `SPS.TITL`), which **already matches legacy**. Changing the detail to `ERM.TITL` would *introduce* a parity defect, so `TransferResultDetailResponse` / `detail()` are left unchanged. The list and detail legitimately use different title sources in legacy.

### Expected outcome
| Screen | Legacy source | Refactor (after fix) | Match |
|---|---|---|---|
| List — Approval Title | `EXPT_RESOL_MSTR.TITL` | `COALESCE(ERM.TITL, SPS.TITL)` | ✅ |
| Detail — title | `SCDUL_PYMN_SUMR.TITL` | `SCDUL_PYMN_SUMR.TITL` (unchanged) | ✅ |

### Status: PASS — list Approval Title now `COALESCE(ERM.TITL, SPS.TITL)`; detail already parity-correct on `SPS.TITL`.

---

## F-2 — Withdrawal bank name on detail header → OPEN (PO decision)

### Finding (unchanged — no code modified)
Legacy detail header (`SCDUL_PYMN_SUMR_R005.xml:50`) renders the withdrawal bank via `wb_get_napas_bank_nm(SPS.OUT_BANK_CD)` → e.g. **"Shinhan VN" / "Woori VN"**. The refactor (`TransferLineDetailMapper.resolveBankName`) resolves via `BANK_INFM` then `wb_get_napas_bank_nm_or_citad_bank_nm(code,'en')` → e.g. **"ShinhanBank" / "WooriBank"**. All 23 DEV rows mismatch on label form; the bank identity is correct (cosmetic difference). Note legacy is internally inconsistent — its *deposit* rows already use the `..._or_citad_...` function ("ShinhanBank"), only the *withdrawal header* uses `wb_get_napas_bank_nm`.

### Decision required (NOT actioned)
- **Option A — strict legacy parity:** route the withdrawal header through `wb_get_napas_bank_nm(OUT_BANK_CD)` so it renders "Shinhan VN" / "Woori VN" exactly as legacy. ~1-line change in the detail path.
- **Option B — accept current unified naming:** keep "ShinhanBank" / "WooriBank" (consistent withdrawal + deposit labels in the refactor).

### Status: NEEDS PO DECISION — left unchanged this pass.

---

## Files changed (F-1 + F-3; F-2 untouched)

**Production (`wecms-api/src/main/java/.../payment/internaltransfer/`):**
- `repository/ScdulPymnSumrRepository.java` — F-1 date predicate + Javadoc.
- `service/InternalTransferResultService.java` — F-1 sort/display; F-3 `buildApprovalMstrMap` + `toResultRow` Approval Title from `ERM.TITL`.
- `service/InternalTransferResultSortField.java` — F-1 `TRAN_DT → trnfDtm`.

**Tests:**
- `service/InternalTransferResultServiceTest.java` — F-1 (display/sort/tie-break/totals) + F-3 (`approval_title_comes_from_expt_resol_mstr_titl_not_sps_titl`, `approval_title_is_null_when_batch_has_no_approval_row`).
- `repository/ScdulPymnSumrSearchDispatchedDateFilterTest.java` — F-1 query-text guard (new).
- `service/InternalTransferSortFieldTest.java` — F-1 `TRAN_DT → trnfDtm` assertion.

No frontend change (wire fields `reg_dtm` / `titl` / `file_nm` / `doc_no` unchanged). No schema / migration / data / backfill. Sibling Vendor/Expense/Payroll services and the shared `TransferResultRow` DTO untouched.

## Test results (2026-06-16)

`./gradlew compileJava test --tests "*InternalTransferResultServiceTest" --tests "*TransferResultControllerTest" --tests "*ScdulPymnSumrSearchDispatchedDateFilterTest" --tests "*ScdulPymnSumrSearchStatusFilterTest" --tests "*InternalTransferSortFieldTest"` → **BUILD SUCCESSFUL**.

| Test class | tests | failures | errors | skipped |
|---|---|---|---|---|
| InternalTransferResultServiceTest | 7 | 0 | 0 | 0 |
| TransferResultControllerTest | 4 | 0 | 0 | 0 |
| ScdulPymnSumrSearchDispatchedDateFilterTest | 3 | 0 | 0 | 0 |
| ScdulPymnSumrSearchStatusFilterTest | 2 | 0 | 0 | 0 |
| InternalTransferSortFieldTest | 6 | 0 | 0 | 0 |
| **Total** | **22** | **0** | **0** | **0** |

F-3 list tests: `legacy_row_approval_title_comes_from_erm_titl_not_sps_titl`, `refactor_row_falls_back_to_sps_titl_when_erm_titl_blank`, `falls_back_to_sps_titl_when_no_approval_row`.

## Final status

| Item | Verdict | Code change | Migration/backfill |
|---|---|---|---|
| F-1 date filter/sort/display | **PASS** (fixed) | Yes | No |
| F-3 File Name column | **PASS** (no change needed) | No | No |
| F-3 Approval Title column | **PASS** (fixed → `ERM.TITL`) | Yes | No |
| F-2 withdrawal bank label | **NEEDS PO DECISION** | Deferred | No |

**SIT:** ready (F-1 + F-3 fixed and tested). **UAT:** ready pending the F-2 Option A/B decision (cosmetic withdrawal-bank label). No commit made.

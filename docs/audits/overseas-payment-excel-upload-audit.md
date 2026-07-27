# Overseas Payment — Excel Upload / Refresh Legacy Parity Audit

> **System:** Refactor (wecms-api + wecms-web) vs Legacy JEX
> **Feature:** Payment → Overseas Payment → Scheduled Payment → Excel Upload / Refresh
> **Date:** 2026-06-29 (audit) · fixes applied + verified 2026-06-29
> **Status:** Fixes implemented and verified (see §0). H2 closed by PO. WBW1160 message + UI parity confirmed.
> **Method:** Analyze → Compare with Legacy → Explain Root Cause → Explain Impact → Document → Approve → Implement → Verify
>
> **Follow-up 2026-07-25 — two further divergences found and fixed** by the cross-module
> pass in [payment-excel-upload-crossmodule-verification.md](payment-excel-upload-crossmodule-verification.md) §3:
> **F2** `OVRS_PYMN_SUMR.WDRL_CCY_CD` was never set on the upload path (legacy
> `pymn_0007_003_c001_act.jsp:108` sets it from `input.CCY_CD`) — now resolved from
> `ACCT_MSTR`; **F3** `TRNF_DT` was stamped at draft creation citing `pymn_0007_002_c001`
> (the **manual** screen, not this Excel path) — stamp removed, legacy leaves it unset
> until dispatch. Plus **F4**, the workbook is now mandatory at save (legacy
> `validateRequired(FULL_PATH)`). Both F2/F3 stem from citing the `pymn_0007_002_*`
> manual-entry family instead of `pymn_0007_003_*` — the same wrong-source mistake this
> audit already corrected once for `OVRS_PYMN_TYPE_CD`.

---

## 0. Implementation Status (2026-06-29) — fixes applied, verified

The approved Priority-1 + Priority-2 fixes were implemented and verified. **H2 mixed-currency policy
is CLOSED (Mr. Jung, 2026-06-29): keep legacy behavior — single currency per upload, mixed rejected
(`WBW1160`), no `MULTI` persisted.** The shipped legacy-parity block is the final behaviour — see
[../planning/open-questions/overseas-payment-mixed-currency-h2.md](../planning/open-questions/overseas-payment-mixed-currency-h2.md).

| Gap | Fix shipped | Where |
|---|---|---|
| **H1** | Frontend now calls backend `POST /excel-preview` (passing the selected withdrawal `bank_cd`); client-side `xlsx` parse + duplicate contact lookup removed. The backend's full legacy-parity validator is now the live path. | `OverseasExcelUploadModal.tsx`, `previewOverseasPaymentExcel(file, bankCd)` in `lib/api/payment.ts` |
| **H2** ✅ closed | Single-currency rule enforced — preview flags mixed currency, `doSubmit` rejects `"MULTI"` + multi-currency payloads, FE never sends `"MULTI"`. **PO decision 2026-06-29: keep legacy behavior — final.** **Message parity (2026-06-29):** the mixed-currency error now uses the **verbatim legacy WBW1160 text** ("The file is containing multiple currencies. Please check again.") via the i18n message system (`WBW1160` key in `messages.properties`) — preview resolves it for the inline Transfer-Currency cell error, `doSubmit` throws `BusinessException("WBW1160")`. | `OverseasPaymentExcelService.preview` + `doSubmit`; `messages.properties` |
| **H2-UI** ✅ verified parity | Legacy `pymn_0007_003` shows WBW1160 as an **inline Currency-cell error + hover tooltip** (`err_click` red highlight), NOT a popup — modal stays open, preview grid stays, file retained, Upload disabled while errors exist. The refactor shared `ExcelUploadDialog` already renders this exact mechanism (cell red-tint + `Tooltip`, `uploadDisabled` on `errorCount>0`); the overseas modal maps the backend `currency` error onto the Transfer Currency cell. **No FE change needed** — display behavior was already at parity; only the message text was fixed. | (verification only) |
| **H3** | `ERP_TRNS_HSTR` upload row written at save (`recordUploadedFile("13", …)`, ERP_TYPE='E', legacy `OVERSEAS_PAYMENT_REQUEST`). | `OverseasPaymentExcelService.doSubmit` |
| **M1** | Memo cap aligned 200 → **500** (legacy `r001:386`; `OVRS_PYMN_SUMR_DETL.MEMO` is VARCHAR(500)). | `OverseasPaymentExcelService.preview` |
| **M2** | Reporting account validated against `CHART_ACCT_INFM` (`DEL_YN='N'`) in preview; `CHART_ACCT_SEQ_NO` resolved + persisted on save. | `OverseasPaymentExcelService.preview` + `doSubmit` |
| **M3** | Special-character validation now runs in the live path (preview is called with the selected `bank_cd`). | preview wiring (FE → `/excel-preview`) |
| **M4** | Withdrawal-account dropdown currency-filtered by the parsed file currency; preview re-runs when the selected account changes (new optional `reparseSignal` on the payment-only `PaymentExcelUploadDialog`). | `OverseasExcelUploadModal.tsx`, `PaymentExcelUploadDialog.tsx` |
| **M5** | Header-title validation (EN/KO/VI `startsWith`) added to `validateHeader` (`WBE2042`). | `OverseasPaymentExcelService.validateHeader` |
| **M6 / L1** | Currency-3-char, amount>0, and max-500-rows are enforced server-side and now reached via the live preview call. | preview (already present) + FE wiring |

| **L2** ✅ fixed | `TRCO_KEY` now resolved from the beneficiary code at save (legacy `r001:138` → `c001:158`) instead of `""`. Confirmed divergent: legacy persists the resolved internal `TRCO_KEY`. | `OverseasPaymentExcelService.doSubmit` |
| **L3** ✅ fixed | `OVRS_PYMN_TYPE_CD` now written as **NULL** (was `"01"`). Confirmed against live JEX 2026-06-29: all 34 `OVRS_PYMN_SUMR` rows are NULL; the Excel-batch `c001` never sets it. The `"01"` came from the manual `pymn_0007_002` path. | `OverseasPaymentExcelService.doSubmit` |

**Still open (recommendation only — not implemented):** **L4** — preview per-cell error messages are English string literals (e.g. "currency must be 3 characters") rather than localized i18n keys. This is the established refactor pattern for *all* payment preview field errors (not overseas-specific), so changing only overseas would be inconsistent. Recommend a separate cross-payment MR if localized preview errors are required. Low priority; no data/migration impact. **Exception (2026-06-29):** the mixed-currency error is now the one overseas preview error routed through the i18n message system (`WBW1160`) — done to match the verbatim legacy WBW1160 text per the explicit WBW1160-parity request.

**Verification:** `./gradlew test --tests "*Overseas*"` → BUILD SUCCESSFUL · `npx tsc --noEmit` → 0 errors · ESLint on the 3 edited FE files → 0 problems (repo-wide baseline lint errors are pre-existing, in untouched files).

---

## 0a. Template Replacement Re-check (2026-06-29) — **PASS** (no code change)

Re-audit of the three replaced templates under `wecms-web/public/templates/overseas-payment/` against
the parser (`OverseasPaymentExcelService`) and the download path. Templates parsed via the `xlsx`
library; the backend `validateHeader` rule (cell `startsWith` one of EN/KO/VI per column) was simulated
against the **actual Java constants** extracted from the source. The three refactor templates were also
compared cell-for-cell against the legacy templates (`WECMS DEV/WECMS_STATIC/web/file/excel/VN/`,
read-only).

| # | Check | Result |
|---|---|---|
| 1 | Files exist with exact frontend filenames (`WeCMS_Overseas_Payment_upload_{EN,KO,VI}.xlsx`) | ✅ Pass — all 3 present (34,267 / 34,324 / 34,333 bytes) |
| 2 | `OverseasExcelUploadModal.tsx` download path matches (`/templates/overseas-payment/WeCMS_Overseas_Payment_upload_${locale}.xlsx`) | ✅ Pass — `locale` ∈ {EN,KO,VI} maps 1:1 to the filenames |
| 3 | Sheet index 0 readable | ✅ Pass — sheet[0] = `OverseasRequest` (sheet[1] = `NOTE`, ignored by the parser) |
| 4 | Header row = Excel row 3 / parser index 2 (`HEADER_ROW_INDEX=2`) | ✅ Pass — headers on row index 2 (Excel row 3) |
| 5 | First data row = Excel row 4 / parser index 3 (`FIRST_DATA_ROW_INDEX=3`) | ✅ Pass — first data/sample row on index 3 (Excel row 4) |
| 6 | Column count exactly 6 | ✅ Pass — header row has exactly 6 populated cells (A–F); G–J empty |
| 7 | Column order (Beneficiary · Transfer Currency · Transfer Amount · Fee Payer · Memo · Reporting Account) | ✅ Pass — exact order in all 3 files |
| 8 | Header text passes backend `validateHeader` EN/KO/VI `startsWith` | ✅ Pass — 18/18 cells (6 × 3 langs) OK, incl. Vietnamese diacritics, byte-matched to the Java constants |
| 9 | Required columns match legacy (5 marked ` *`; Reporting Account optional) | ✅ Pass — Beneficiary/Currency/Amount/Fee Payer/Memo carry ` *`; Reporting Account has no marker |
| 10 | No extra hidden/shifted columns break parsing | ✅ Pass — `A1:J1000` dimension is cosmetic (pre-formatted range); parser reads fixed indexes 0–5; no shift |
| 11 | Sample/hint rows don't break parsing | ✅ Pass — one sample row at row 4 (`V002 / USD / 10 / Shared / Request from Global`) parses as an ordinary data row (no crash). See note. |
| 12 | Backend `*Overseas*` tests | ✅ Pass — `./gradlew test --tests "*Overseas*"` → BUILD SUCCESSFUL |

**Legacy comparison:** the three refactor templates are **structurally byte-identical to the legacy
templates** — same sheets (`OverseasRequest` + `NOTE`), same `A1:J1000` dimension, same title row 1,
blank row 2, 6-column header row 3, and the **same sample row at row 4** (`V002 / USD / 10 / Shared /
Request from Global`). The dimension extending to column J and the row-4 sample are **carried over
verbatim from legacy**, not new drift.

**Note (not a defect):** the template ships a sample row at the first data row (row 4). The parser
treats it as a real row — beneficiary code `V002` resolves via the contact lookup (an error row if
`V002` is not a registered overseas beneficiary in the tenant, a valid row otherwise). This is
identical to legacy behaviour; the user is expected to overwrite/remove the sample. It does **not**
break parsing.

**Differences found:** none. The replaced templates are at full parity with both the parser
expectations and the legacy templates. **No code change made.**

---

## 1. Summary

The Overseas Payment Excel Upload flow lets a user download a 6-column Excel template, fill in
beneficiary code / currency / amount / fee-payer / memo / reporting-account, upload it, preview
parsed rows with per-cell error highlighting, pick a withdrawal account, and save the batch into
`OVRS_PYMN_SUMR` + `OVRS_PYMN_SUMR_DETL`. The legacy implementation is WSVC `pymn_0007_003`
(`_r001` read/validate, `_c001` save); the refactor is `OverseasPaymentController` +
`OverseasPaymentExcelService` (API) and `OverseasExcelUploadModal` (Web).

**Overall verdict:** the refactor reproduces the *happy-path shape* (same template, same 6 columns,
same two target tables, same key-generation funnel, same withdrawal-account picker) but **the
validation surface diverges materially** because the **frontend parses and validates the Excel
entirely client-side and never calls the backend's `/excel-preview` endpoint**. The backend's
server-side parser/validator (which is a close legacy port) is effectively **orphaned** at preview
time, so most legacy validation rules (currency length, mixed-currency block, amount > 0, fee-payer
normalization, memo length, special-character rules, reporting-account lookup, header-title check,
max-rows) are **not enforced** in the live FE path. Two further behavioral gaps exist on the save
side: **ERP history is not written at upload** (legacy writes it), and the **memo length cap differs
(200 vs 500)**.

**Gap count:** 3 High, 6 Medium, 4 Low (see §10).
**Backend tests:** `./gradlew test --tests "*Overseas*"` → **BUILD SUCCESSFUL** (baseline, no changes made).

---

## 2. Legacy Code Trace (JEX — source of truth)

### 2.1 Screens & WSVC services

| Artifact | Path | WSVC / role |
|---|---|---|
| List view | `WECMS DEV/WECMS/web/view/jex/wabooks/pymn/pymn_0007_001_view.jsp` | `pymn_0007_001` — Overseas Scheduled Payment list; Excel-upload button at line 774-775 |
| Upload popup view | `WECMS DEV/WECMS/web/view/jex/wabooks/pymn/pymn_0007_003_view.jsp` | `pymn_0007_003` — upload dialog (Step1 download / Step2 browse / Step3 withdrawal account / preview / upload) |
| Read+validate action | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_003_r001_act.jsp` | `pymn_0007_003_r001` — parse + validate Excel rows |
| Save action | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_003_c001_act.jsp` | `pymn_0007_003_c001` — persist summary + details + ERP history |
| Refresh action | (referenced) | `pymn_0007_003_c002` — ERP data refresh (HAENGSUNG path) |
| Popup JS | `WECMS DEV/WECMS_STATIC/web/js/jex/wabooks/pymn/pymn_0007_003.js` | template download, preview render, withdrawal account load, save |

### 2.2 Excel template (legacy)

- Folder: `WECMS DEV/WECMS_STATIC/web/file/excel/VN/`
- Download files: `WeCMS_Overseas_Payment_upload_{EN|KO|VI}.xlsx` (also `_download_*` and legacy `.xls` variants present)
- Download mechanism: `pymn_0007_003.js:154-157` builds
  `file/excel/VN/WeCMS_Overseas_Payment_upload_<LANG>.xlsx?<timestamp>`
- Structure: header row at index **2** (Excel row 3); data starts at index **3** (Excel row 4); **6 columns**; max **500** data rows (`pymn_0007_003_r001_act.jsp:298`).

### 2.3 Parse + validation (`pymn_0007_003_r001_act.jsp`)

POI: `wb.getSheetAt(0)`, header validated only at row index 2, data loop `for (i = 3; i <= rowCount; i++)`,
500-row cap. Per-row validation (`:307-403`):

| Col | Field | Rule | Error code |
|---|---|---|---|
| 0 | Beneficiary code | required; lookup `TrcoInfm` where `trco_type='O'`, `trco_stts NOT IN ('C')`, `hand_trco_key=?` | `WBW0001` / `WBE1189` |
| 1 | Transfer currency | required; **exactly 3 chars**; **single currency per file** (`WBW1160` if mixed); uppercased | `WBW0001` / `WBE0001` / `WBW1160` |
| 2 | Transfer amount | required; `checkValidAmountOnlyPositive_` (> 0) | `WBW0001` / `WBE0000` |
| 3 | Fee payer | required (raw value stored; normalization deferred to save) | `WBW0001` |
| 4 | Memo | required; VN tone replacement; **special-char validation** when bank code set (`SpecialCharValidator`, Redmine #42761); **max 500** chars | `WBW0001` / `WBE0000` |
| 5 | Reporting account | optional; if present, lookup `ChartAcctInfm` (`chart_acct_cd=?`, `del_yn='N'`) | `WBE0404` |

Beneficiary details (`getOverseasContactInfo` → `TrcoOvrsInfm`): cross-fills BNFC_NM, BIC_CODE,
BNFC_ACCT_NO, BNFC_ADDR, BNFC_BANK_NM, BNFC_BANK_CD; each blank-after-resolve adds `WBW1159`.
BNFC_NM + BNFC_ADDR also pass VN-tone + special-char validation.

Header-title validation (`:242-288`): each of the 6 header cells must `startsWith` one of the
EN/KO/VI header titles (`HEADER_TITLE_EN/KO/VI`) else `WBE0001`. File-extension check `.xls/.xlsx`
in JS (`pymn_0007_003.js:110`, `WBE1041`).

### 2.4 Preview / counters / upload-enable (`pymn_0007_003.js`)

Errors serialized to `ERR_FLD` / `ERR_FLD_CAUS` (pipe-delimited); preview highlights error cells with
class `err_click` + tooltip. Counters: `clear_cnt` / `err_cnt` / `TOT_CNT`. **Upload button enabled
only when `clear_cnt > 0 && err_cnt === 0`.**

### 2.5 Withdrawal bank/account (`pymn_0007_003.js:341-387`)

WSVC `comm_1001_001` with input `{ CCY_CD: <file currency> }` → rows `BANK_CD / BANK_NM / ACCT_NO /
ACCT_TYPE_CD / CCY_CD`. Display `"{BANK_NM} {ACCT_NO} ({type} {CCY_CD})"`. **First valid account
auto-selected.** Changing bank/account **re-validates** the file against the new bank's special-char
rules.

### 2.6 Save (`pymn_0007_003_c001_act.jsp:74-184`)

Inputs validated: `TITL`, `FULL_PATH` (file exists), `REC` (non-empty), `BANK_CD`, `ACCT_NO`, `CCY_CD`.
Flow: group rows by currency (skip `ERR_FLD` rows) → create `OVRS_PYMN_SUMR` (per currency group) →
**write `ERP_TRNS_HSTR` via `ErpTrnsHisHelper` (ErpTypeCd.OVERSEAS_PAYMENT_REQUEST)** → create
`OVRS_PYMN_SUMR_DETL` rows → update summary `TOTAL_TRAN_AMT`. Transactional
(`beginTransaction`/`commitAndCloseQuietly`).

Tables written: `OVRS_PYMN_SUMR` (INSERT + UPDATE), `OVRS_PYMN_SUMR_DETL` (INSERT), `ERP_TRNS_HSTR`
(INSERT). IDOs: `OVRS_PYMN_SUMR_C001`/`_U001`, `OVRS_PYMN_SUMR_DETL_C001`.

---

## 3. Refactor Code Trace

### 3.1 API (wecms-api)

| Component | File |
|---|---|
| Controller | `domain/payment/controller/OverseasPaymentController.java` |
| Parser/validator/save | `domain/payment/service/OverseasPaymentExcelService.java` |
| Key generator | `domain/payment/overseas/service/OverseasPaymentKeyGenerator.java` (→ `LegacyFunctionRepository.nextOvrsPymnNo` → `wb_get_next_val`) |
| Entities | `OvrsPymnSumr.java`, `OvrsPymnSumrDetl.java` |
| Repositories | `OvrsPymnSumrRepository.java`, `OvrsPymnDtlsRepository` |
| Special-char | `domain/payment/validation/SpecialCharValidator.java` |
| ERP history writer | `domain/setting/erptransactionhistory/service/ErpTrnsHistoryWriter.java` |

Endpoints:
- `POST /api/v1/payment/overseas-payment/excel-preview` (multipart `file` + optional `bank_cd`) → `OverseasPaymentExcelPreview` — **server-side parse + full validation** (`OverseasPaymentExcelService.preview`, `:125-247`).
- `POST /api/v1/payment/overseas-payment/excel-upload` (multipart `file` + JSON `data`) → `{ ovrs_pymn_no }` — save (`submit`/`doSubmit`, `:431-597`).
- `GET /api/v1/payment/overseas-payment/withdrawal-accounts?ccy_cd=` → withdrawal account rows.

Backend preview validation mirrors legacy closely: currency required + 3-char; amount required + > 0;
fee-payer required + `normalizeFeePayerCd` (B/O/S); memo required + **≤ 200** (`:182`); beneficiary
resolution against `TrcoOvrsInfm`; special-char validation for BNFC_NM/BNFC_ADDR/MEMO when `bank_cd`
present (`:204-216`); `MAX_ROWS=500` (`WBE2041`); header column-count ≥ 6 (`WBE2042`).

Save (`doSubmit`): key via `wb_get_next_val`, `OVRS_PYMN_TYPE_CD="01"`, server-recomputed totals,
write `OVRS_PYMN_SUMR` → file → `OVRS_PYMN_SUMR_DETL` (detail seq via `nextOvrsPymnDetlNo`). One-shot
retry on key race (`WBE_SCHEDULED_PAYMENT_KEY_RACE`).

### 3.2 Web (wecms-web)

| Component | File |
|---|---|
| List page | `app/(dashboard)/payment/overseas-payment/scheduled-payment/page.tsx` → `components/payment/overseas-payment/ScheduledPaymentPage.tsx` |
| Upload modal | `components/payment/overseas-payment/OverseasExcelUploadModal.tsx` |
| Shared upload shell | `components/payment/shared/PaymentExcelUploadDialog.tsx` → `components/shared/excel-upload/ExcelUploadDialog.tsx` |
| Withdrawal dropdown | `components/payment/shared/WithdrawalAccountDropdown.tsx` |
| Refresh hook | `hooks/useHaengsungRefresh.ts` |
| API client | `lib/api/payment.ts` (`submitOverseasPaymentExcel`, `fetchOverseasWithdrawalAccounts`, `haengsungPaymentRefresh`) |
| Templates | `public/templates/overseas-payment/WeCMS_Overseas_Payment_upload_{EN|KO|VI}.xlsx` |

**Critical:** `OverseasExcelUploadModal.handleParse` (`:70-151`) parses the workbook **client-side**
with the `xlsx` library, skips rows 0-2, reads 6 columns, resolves beneficiary details via
`fetchContactsByBeneficiaryCodes`, and builds the preview. Comment at `:71`:
"Parse Excel client-side — no backend call needed." **The backend `/excel-preview` endpoint is never
called.** On upload it posts to `/excel-upload` with `ccy_cd = (single currency) || "MULTI"` (`:165`).

Refresh: HAENGSUNG-only `POST /api/v1/payments/haengsung/refresh` (`payment_type:"OVERSEAS"`); not an
Excel path.

---

## 4. Template Audit

| Aspect | Legacy | Refactor | Match |
|---|---|---|---|
| Filenames (EN/KO/VI) | `WeCMS_Overseas_Payment_upload_{EN|KO|VI}.xlsx` | identical | ✅ |
| Location | `WECMS_STATIC/web/file/excel/VN/` | `wecms-web/public/templates/overseas-payment/` | ✅ (relocated, expected) |
| Download path | `file/excel/VN/...xlsx?<ts>` (lang via `LANG.toUpperCase()`) | `/templates/overseas-payment/...${locale}.xlsx` | ✅ |
| Sheet | first sheet | first sheet | ✅ |
| Header row | index 2 (row 3) | skips rows 0-2, data from index 3 | ✅ |
| First data row | index 3 (row 4) | index 3 | ✅ |
| Column order | 0 Beneficiary · 1 Currency · 2 Amount · 3 Fee Payer · 4 Memo · 5 Reporting Acct | identical (FE `cell(row,0..5)`; API `COL_*`) | ✅ |
| Column count | 6 | 6 | ✅ |
| Locale variants | EN/KO/VI (+ legacy `.xls`) | EN/KO/VI `.xlsx` | ✅ |

> The refactor ships only the 3 `_upload_*.xlsx`; the legacy `_download_*` and `.xls` siblings are not
> carried. No functional gap (the upload flow uses `_upload_*` only). **Recommend** a binary diff of
> the header-title cells EN/KO/VI to confirm wording parity (the legacy header-title validator depends
> on exact prefixes; the refactor no longer validates header titles — see §5).

---

## 5. Excel Read & Validation Audit

Legacy = `pymn_0007_003_r001_act.jsp` (+ `.js`). Refactor-API = `OverseasPaymentExcelService.preview`.
Refactor-Web = `OverseasExcelUploadModal.handleParse`. **The live web path uses Refactor-Web only.**

| Rule | Legacy | Refactor-API (preview) | Refactor-Web (live) | Match |
|---|---|---|---|---|
| File extension `.xls/.xlsx` | JS check (`WBE1041`) | n/a (POI) | shared dialog `xlsx` only | ⚠️ Partial |
| Max upload rows | 500 cap | 500 (`WBE2041`) | **none** | ❌ Gap (Web) |
| Header title validation (EN/KO/VI) | yes (`startsWith`, `WBE0001`) | column-count ≥ 6 only | **none** | ❌ Gap |
| Required: currency/amount/feePayer/beneficiary/memo | yes | yes | yes (5 required) | ✅ |
| Currency = 3 chars | yes (`WBE0001`) | yes | **none** | ❌ Gap (Web) |
| **Single currency per file** | yes (`WBW1160`) | not enforced | **none** (sends `"MULTI"`) | ❌ Gap |
| Amount > 0 | yes | yes | **none** (only "required") | ❌ Gap (Web) |
| Fee-payer normalize B/O/S | at save | at preview + save | **none** (raw passthrough) | ⚠️ Web defers to API |
| Memo max length | **500** | **200** | none | ❌ Gap (cap value) |
| Memo special-char (bank rules) | yes (when bank set) | yes (when `bank_cd`) | **none** | ❌ Gap |
| Beneficiary lookup (`TrcoInfm` O / not-cancelled) | yes (`WBE1189`) | yes (`findActiveOverseas...`) | via `fetchContactsByBeneficiaryCodes` | ⚠️ Verify filter parity |
| Beneficiary details cross-fill + blank check | `WBW1159` x6 | yes ("... is missing") | yes (`contact_not_found` per field) | ✅ shape; ⚠️ messages differ |
| BNFC_NM / BNFC_ADDR special-char | yes | yes (when `bank_cd`) | **none** | ❌ Gap |
| Reporting account → `ChartAcctInfm` lookup | yes (`WBE0404`) | **not validated** | **not validated** (`error:false`) | ❌ Gap |
| Duplicate row / recipient / account / SWIFT | **not present in legacy** | not present | not present | ✅ (N/A) |
| Error highlighting + counters | `ERR_FLD` + class | per-cell `OverseasPaymentExcelFieldError` | per-cell `errorByField` | ✅ |
| Upload-enable: completed>0 && error==0 | yes | n/a | yes (shared dialog) | ✅ |

> The task's checklist items for SWIFT/BIC, intermediary bank, intermediary SWIFT, beneficiary
> address/country, exchange-rate validation **do not exist in the legacy Excel-upload flow** — the
> legacy template carries only 6 columns and resolves all beneficiary/bank/BIC data from the contact
> master (`TrcoOvrsInfm`). There is no per-row SWIFT/intermediary/country/exchange-rate validation to
> port. The refactor correctly omits them too. Documented here so the absence is not mistaken for a gap.

---

## 6. Withdrawal Bank / Account Audit

| Aspect | Legacy (`comm_1001_001`) | Refactor (`/withdrawal-accounts`) | Match |
|---|---|---|---|
| Source service | `comm_1001_001` (shared) | `WithdrawalAccountListService.list(ccyCd)` | ✅ |
| **Currency filter** | input `{ CCY_CD: <file currency> }` | endpoint supports `ccy_cd`, but FE calls `fetchOverseasWithdrawalAccounts()` **without** it | ❌ Gap |
| Row shape | BANK_CD/BANK_NM/ACCT_NO/ACCT_TYPE_CD/CCY_CD | `bank_cd/bank_nm/acct_no/ccy_cd/acct_type_cd/acct_nick_nm?` | ✅ |
| Display format | `"{BANK_NM} {ACCT_NO} ({type} {CCY})"` | `"{bank_nm} {acct_no} ({type} {ccy})"` | ✅ |
| Default/auto-select | first valid account | first account (`getWithdrawalAccountKey(list[0])`) | ✅ |
| Re-validate on bank change | yes (special-char re-run) | **none** (no preview call at all) | ❌ Gap |
| Acct-type label map | 03/04/07 Current, 05/06/08 Savings | identical map in `WithdrawalAccountDropdown` | ✅ |

Root cause of the currency-filter gap: the FE loads withdrawal accounts on modal mount **before** a file
is parsed (`useEffect` at `OverseasExcelUploadModal.tsx:35-45`), so no currency is known yet; it never
re-queries after parse. Legacy loads the dropdown *after* parse using the file's currency.

---

## 7. Overseas Business Rule Audit

| Rule | Legacy | Refactor | Match |
|---|---|---|---|
| Beneficiary must be registered overseas contact (`trco_type='O'`, not cancelled) | yes | API: `findActiveOverseasByBizKeyAndHandTrcoKey`; Web: contact map | ⚠️ Verify status/type filter parity |
| Local vs overseas recipient | overseas-only (type O) | overseas-only | ✅ |
| SWIFT/BIC, bank name, account, address sourced from contact master | yes (`TrcoOvrsInfm`) | yes (`TrcoOvrsInfm` / contact API) | ✅ |
| **Single currency per upload batch** | enforced (`WBW1160`) | **not enforced** (`ccy_cd="MULTI"` allowed) | ❌ Gap |
| Fee payer ∈ {B, O, S} | normalized at save | normalized at preview+save | ✅ (at save) |
| Foreign-currency / FX handling | none at upload (FX at dispatch) | none at upload | ✅ |
| Overseas approval / request creation | separate `EXPT_RESOL_*` flow (post-upload) | separate `requestApproval` flow | ✅ (out of upload scope) |
| Beneficiary country / intermediary bank validation | **not in upload flow** | not present | ✅ (N/A) |

---

## 8. Upload Save — DB Audit

Tables on save: legacy → `OVRS_PYMN_SUMR`, `OVRS_PYMN_SUMR_DETL`, `ERP_TRNS_HSTR`. Refactor →
`OVRS_PYMN_SUMR`, `OVRS_PYMN_SUMR_DETL` only.

### 8.1 `OVRS_PYMN_SUMR`

| Column | Legacy | Refactor | Match |
|---|---|---|---|
| BIZ_KEY | login bizKey | `TenantContext.currentBizKey()` | ✅ |
| OVRS_PYMN_NO | auto key | `wb_get_next_val` (`nextOvrsPymnNo`) | ✅ |
| OVRS_PYMN_TYPE_CD | (excel type) | `"01"` | ⚠️ Verify legacy value |
| TITL | filename | `req.file_nm()` | ✅ |
| CCY_CD | per-currency group | `req.ccy_cd()` (may be `"MULTI"`) | ❌ Gap (mixed) |
| TOTAL_TRAN_AMT | sum (post-update) | server-recomputed | ✅ |
| TOT_TRNS_CNT | group count | `req.rows().size()` | ✅ |
| OUT_BANK_CD | NAPAS code | `outBankCdNormalizer.normalize(wdrl_bank_cd)` | ✅ |
| WDRL_ACT_NO | withdrawal acct | `req.wdrl_acct_no()` | ✅ |
| WDRL_CCY_CD | withdrawal ccy | set | ✅ |
| TRNF_DT | upload ts | `nowDtm` | ✅ |
| TRNF_YN | "N" | `"N"` | ✅ |
| ORG_FILE_NM / SAVE_FILE_NM / FILE_SIZE | file info | set | ✅ |
| REG_*/MOD_* | audit | audit | ✅ |

### 8.2 `OVRS_PYMN_SUMR_DETL`

| Column | Legacy | Refactor | Match |
|---|---|---|---|
| BIZ_KEY / OVRS_PYMN_NO / OVRS_PYMN_DETL_NO | set | set (`nextOvrsPymnDetlNo`) | ✅ |
| TRNF_CCY_CD | per row | `src.ccy_cd()` | ✅ |
| TRNF_AMT | per row | `src.trnf_amt()` | ✅ |
| FEE_PAYER_CD | B/O/S | `normalizeFeePayerCd` | ✅ |
| MEMO | memo | `nullToEmpty(src.memo())` | ✅ |
| BNFC_NM / BNFC_BANK_NAME / BNFC_BANK_CD / BNFC_BANK_BIC / BNFC_ACCT_NO / BNFC_ADDR | from contact | from row | ✅ |
| TRCO_KEY | beneficiary key | `""` (excel = contact-less detail) | ⚠️ Divergence |
| HAND_TRCO_KEY | beneficiary code | `src.bnfc_code()` | ✅ |
| CHART_ACCT_CD / CHART_ACCT_SEQ_NO | from reporting acct lookup | `nullIfBlank` (no lookup, seq not resolved) | ❌ Gap |
| REG_*/MOD_* | audit | audit | ✅ |

### 8.3 `ERP_TRNS_HSTR`

| Aspect | Legacy | Refactor | Match |
|---|---|---|---|
| Written at **upload** | **yes** (`ErpTrnsHisHelper`, `OVERSEAS_PAYMENT_REQUEST`) | **no** — `erpHistoryWriter.recordExportedFile` runs only on result export (`OverseasPaymentExcelService.java:720`) | ❌ Gap |

### 8.4 `AUDIT_LOGS_INFM`

Legacy upload path writes no audit log; refactor writes audit only on `requestApproval`/approve/reject/
dispatch (`wsvc_id="overseas-payment"` slug). **Parity** on the upload step itself (neither logs upload).

---

## 9. Legacy vs Refactor Comparison (consolidated)

| Area | Legacy | Refactor | Match |
|---|---|---|---|
| Template (files/columns/rows) | 6-col, EN/KO/VI | identical | ✅ |
| Parser | server-side (`_r001` POI) | **API server-side (orphaned) + Web client-side `xlsx`** | ⚠️ |
| Where validation runs | server (`_r001`) | **client (FE) for live path**; server preview unused | ❌ |
| Header-title validation | yes | no | ❌ |
| Currency 3-char | yes | API yes / Web no | ❌ |
| Single-currency-per-file | yes (`WBW1160`) | no (`MULTI`) | ❌ |
| Amount > 0 | yes | API yes / Web no | ❌ |
| Memo length cap | 500 | 200 | ❌ |
| Special-char (memo/name/addr) | yes | API yes (needs bank_cd) / Web no | ❌ |
| Reporting-acct lookup | yes (`WBE0404`) | no | ❌ |
| Max rows 500 | yes | API yes / Web no | ❌ |
| Beneficiary resolution | `TrcoOvrsInfm` | `TrcoOvrsInfm` | ✅ |
| Preview + counters + upload-enable | yes | yes | ✅ |
| Withdrawal account picker | currency-filtered, re-validate on change | not currency-filtered, no re-validate | ❌ |
| Save tables | SUMR + DETL + ERP_TRNS_HSTR | SUMR + DETL | ❌ (ERP) |
| Key generation | legacy seq | `wb_get_next_val` funnel | ✅ |
| Approval linkage | `EXPT_RESOL_*` post-upload | `EXPT_RESOL_*` post-upload | ✅ |
| Refresh | `c002` ERP refresh | HAENGSUNG `/haengsung/refresh` | ✅ (scope) |

---

## 10. Gaps / Bugs Found

### HIGH

**H1 — Frontend bypasses backend validation entirely (orphaned `/excel-preview`).**
- **Severity:** High
- **Root cause:** `OverseasExcelUploadModal.handleParse` parses + validates client-side with `xlsx` and never calls `POST /api/v1/payment/overseas-payment/excel-preview`. The backend's full legacy-parity validator (`OverseasPaymentExcelService.preview`) is dead at preview time. FE only checks 5 required fields + contact resolution.
- **Impact:** Mixed currency, 4-char currency, zero/negative amount, over-length memo, invalid special characters, invalid reporting account, and >500 rows all pass the FE preview and reach `/excel-upload`. Save-side defenses are weaker than legacy, so bad/divergent data can persist. Highest data-migration & parity risk.
- **Legacy ref:** `pymn_0007_003_r001_act.jsp:242-403`
- **Refactor ref:** `OverseasExcelUploadModal.tsx:70-151` vs `OverseasPaymentExcelService.java:125-247`
- **Recommended fix (for approval):** Switch the FE to call `/excel-preview` (multipart file + selected `bank_cd`) and render the returned `OverseasPaymentExcelPreview`, OR fully port every legacy rule into the client parser. Calling the backend is the lower-drift option and reuses the legacy-parity validator already written.

**H2 — Single-currency-per-upload rule (`WBW1160`) not enforced; refactor allowed `"MULTI"`.** ✅ FIXED + CLOSED.
- **Severity:** High
- **Root cause:** Legacy blocks mixed currencies at validation and groups by currency at save (one summary per currency). FE computed `ccy_cd = ccyCodes.length===1 ? ccyCodes[0] : "MULTI"` and the backend save accepted it.
- **Impact:** A mixed-currency file produced a single `OVRS_PYMN_SUMR` row with `CCY_CD="MULTI"` — a value legacy never writes. Breaks downstream FX/dispatch assumptions and JEX→refactor migration (legacy has no `MULTI` summaries).
- **Legacy ref:** `pymn_0007_003_r001_act.jsp:333-342` (validation, `WBW1160`); the `.js` renders it as an inline Currency-cell error + tooltip (`pymn_0007_003.js:179-243`), **not** a popup. `pymn_0007_003_c001_act.jsp:79-94` (per-currency grouping).
- **Resolution (PO decision 2026-06-29: keep legacy behavior):**
  - **Preview** flags the 2nd+ distinct currency on the Transfer Currency cell with the **verbatim legacy WBW1160 text** resolved via `MessageResolver.resolve("WBW1160")` (`messages.properties` key added). FE renders it as the cell red-tint + hover tooltip (shared `ExcelUploadDialog`) — display mechanism already at legacy parity, **no FE change**.
  - **doSubmit** throws `BusinessException("WBW1160")` for both the `"MULTI"` sentinel and any multi-currency payload (defence-in-depth).
  - FE never sends `"MULTI"`. Upload button stays disabled while the error exists; modal/grid/file all stay.
- **Final-behaviour OQ:** [../planning/open-questions/overseas-payment-mixed-currency-h2.md](../planning/open-questions/overseas-payment-mixed-currency-h2.md) (CLOSED — keep legacy).

**H3 — ERP history (`ERP_TRNS_HSTR`) not written at upload.**
- **Severity:** High
- **Root cause:** Legacy writes an `ERP_TRNS_HSTR` row (`OVERSEAS_PAYMENT_REQUEST`) inside the save transaction. Refactor `doSubmit` writes no ERP history; `erpHistoryWriter.recordExportedFile` only runs on result export (`OverseasPaymentExcelService.java:720`).
- **Impact:** ERP upload-audit trail missing for overseas batches; parity break with `ERP_0100_*` reporting and HAENGSUNG ERP reconciliation; data-migration mismatch.
- **Legacy ref:** `pymn_0007_003_c001_act.jsp:135-136`
- **Refactor ref:** `OverseasPaymentExcelService.java` `doSubmit` (`:459-597`) — no ERP write
- **Recommended fix:** Add the upload-time `ERP_TRNS_HSTR` write in `doSubmit` mirroring `ErpTrnsHisHelper` semantics. Confirm column set against legacy IDO before implementing.

### MEDIUM

**M1 — Memo length cap differs: legacy 500 vs refactor 200.**
- Root cause: `OverseasPaymentExcelService.java:182` caps at 200 with comment "Legacy parity"; legacy `pymn_0007_003_r001_act.jsp:386` caps at **500**.
- Impact: Valid 201–500-char memos rejected by API; also a column-width / migration concern (verify `OVRS_PYMN_SUMR_DETL.MEMO` length in JEX DDL).
- Fix: Align cap to legacy (500) after confirming DDL, or escalate the intended value to PO.

**M2 — Reporting account not validated against `ChartAcctInfm`.**
- Root cause: Legacy `getChartAcctInfm` validates `chart_acct_cd` (`del_yn='N'`, `WBE0404`) and resolves `CHART_ACCT_SEQ_NO`. Refactor preview/save store the raw value (`nullIfBlank`) with no lookup; FE marks reporting-account `error:false` always.
- Impact: Invalid/stale chart-account codes persist; `CHART_ACCT_SEQ_NO` not populated → downstream report linkage breaks.
- Fix: Add chart-account lookup in preview (or save) and populate the seq number.

**M3 — Special-character validation not applied in live FE path.**
- Root cause: Backend validates BNFC_NM/BNFC_ADDR/MEMO via `SpecialCharValidator` only in `preview` and only when `bank_cd` is supplied (`:204-216`); FE never calls preview and `doSubmit` does not re-run it.
- Impact: Bank-specific forbidden characters (Redmine #42761) reach the saved batch and the bank dispatch file → potential bank rejection.
- Fix: Trigger special-char validation in the live path (call preview with the selected withdrawal `bank_cd`, or re-run at save).

**M4 — Withdrawal-account dropdown not currency-filtered + no re-validate on change.**
- Root cause: FE loads `fetchOverseasWithdrawalAccounts()` on mount with no `ccy_cd` and never reloads after parse; legacy loads `comm_1001_001` with the file currency and re-validates on account change.
- Impact: User can select a withdrawal account whose currency mismatches the file; special-char rules of the chosen bank are not applied. (Couples with H1/M3.)
- Fix: Pass parsed file currency to the dropdown query; re-run preview when the account changes.

**M5 — Header-title validation removed.**
- Root cause: Legacy validates the 6 header cells against EN/KO/VI titles (`WBE0001`); refactor checks only column count ≥ 6 (API) and nothing (FE).
- Impact: A wrong/old template or shifted columns parses silently with misaligned data.
- Fix: Re-add header-title verification (API preview is the natural home).

**M6 — Currency 3-char + amount>0 not enforced on FE.**
- Root cause: FE only does presence checks; legacy/API enforce 3-char currency and positive amount.
- Impact: Malformed currency codes / non-positive amounts pass FE preview. Largely mitigated if H1 is fixed (calling backend preview).
- Fix: Resolved by H1, or add the two checks client-side.

### LOW

**L1 — Max-rows (500) not enforced on FE.** ✅ Resolved by H1 — the FE now calls the backend preview, which enforces `WBE2041` at 500.

**L2 — `TRCO_KEY` stored as `""` on excel-upload details.** ✅ **Confirmed divergent and FIXED (2026-06-29).** Legacy `getOverseasContactInfo` (`r001:138`) resolves the contact and stores `TRCO_KEY`; `c001:158` persists it on the detail row. The refactor was writing `""`. `doSubmit` now re-resolves `TRCO_KEY` from the beneficiary code (cached per code) and persists it, falling back to `""` only when unresolved.

**L3 — `OVRS_PYMN_TYPE_CD` value parity.** ✅ **Confirmed divergent and FIXED (2026-06-29).** Live JEX check: all 34 `OVRS_PYMN_SUMR` rows have `ovrs_pymn_type_cd = NULL`; the Excel-batch `c001` never sets it and the legacy `OvrsPymnSumr(WbUser)` constructor doesn't default it. The refactor's `"01"` was sourced from the manual `pymn_0007_002` path. `doSubmit` now writes `null`.

**L4 — Preview error messages are English literals, not localized.** Confirmed but **not implemented** — this is the established refactor pattern for all payment preview field errors (not overseas-specific). Recommend a separate cross-payment MR if localized preview errors are wanted. Low; no data/migration impact.

---

## 11. Recommended Fix Plan (pending approval — do NOT implement yet)

Ordered by parity/migration risk. Each requires the "analyze → state impact → approve" gate per the
refactor DB rules before coding.

1. **H1 + M3 + M5 + M6 + L1 together — make the FE use the backend `/excel-preview`.** This single change
   re-activates the legacy-parity validator (currency 3-char, amount>0, fee-payer, special-char,
   header column count, max-rows) and is the lowest-drift fix. Pass the selected withdrawal `bank_cd`
   so special-char rules run. Add header-title validation to `preview` (M5).
2. **H2 — decide mixed-currency policy with PO.** Either reinstate `WBW1160` single-currency block
   (closest to legacy) or implement per-currency grouping at save. No `"MULTI"` summary should persist
   without an explicit PO decision.
3. **H3 — add upload-time `ERP_TRNS_HSTR` write in `doSubmit`** mirroring `ErpTrnsHisHelper`
   (`OVERSEAS_PAYMENT_REQUEST`); confirm columns against legacy IDO.
4. **M1 — align memo cap to 500** after confirming `OVRS_PYMN_SUMR_DETL.MEMO` length in JEX DDL.
5. **M2 — add reporting-account `ChartAcctInfm` lookup** + populate `CHART_ACCT_SEQ_NO`.
6. **M4 — currency-filter the withdrawal dropdown** and re-validate on account change.
7. **L2/L3/L4 — verify** `TRCO_KEY`, `OVRS_PYMN_TYPE_CD`, and beneficiary message i18n against legacy;
   fix only if confirmed divergent.

All API + Web changes are paired (contract changes ship together per workspace full-stack rule). No DB
schema change is expected; M1 only needs DDL *confirmation*, not modification.

---

## 12. Verification Commands

```bash
# Backend Overseas tests (run for this audit — baseline, no code changed)
cd "c:/WECMS PROJECT/wecms-api"
./gradlew test --tests "*Overseas*"
# → BUILD SUCCESSFUL (2026-06-29)

# Frontend (run after any FE change is approved)
cd "c:/WECMS PROJECT/wecms-web"
npm run type-check
npm run lint
```

> No frontend changes were made in this audit, so `npm run type-check` / `npm run lint` were **not**
> run (nothing to verify). Run them once a fix MR is approved and implemented.

---

## Appendix — Source Index

**Legacy (read-only):**
- `WECMS DEV/WECMS/web/view/jex/wabooks/pymn/pymn_0007_001_view.jsp`
- `WECMS DEV/WECMS/web/view/jex/wabooks/pymn/pymn_0007_003_view.jsp`
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_003_r001_act.jsp`
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0007_003_c001_act.jsp`
- `WECMS DEV/WECMS_STATIC/web/js/jex/wabooks/pymn/pymn_0007_003.js`
- IDOs: `OVRS_PYMN_SUMR_C001/_U001`, `OVRS_PYMN_SUMR_DETL_C001`

**Refactor — API:**
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/controller/OverseasPaymentController.java`
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/service/OverseasPaymentExcelService.java`
- `wecms-api/src/main/java/com/kosign/wecms/domain/payment/overseas/service/OverseasPaymentKeyGenerator.java`
- entities `OvrsPymnSumr.java`, `OvrsPymnSumrDetl.java`; repo `OvrsPymnSumrRepository.java`

**Refactor — Web:**
- `wecms-web/components/payment/overseas-payment/OverseasExcelUploadModal.tsx`
- `wecms-web/components/payment/overseas-payment/ScheduledPaymentPage.tsx`
- `wecms-web/components/payment/shared/WithdrawalAccountDropdown.tsx`
- `wecms-web/lib/api/payment.ts`
- `wecms-web/public/templates/overseas-payment/WeCMS_Overseas_Payment_upload_{EN|KO|VI}.xlsx`

**Related docs:** `wecms-api/docs/features/payment/overseas-payment/`,
`wecms-web/docs/features/payment/overseas-payment/`,
`wecms-api/docs/audits/vendor-payment-excel-upload-audit.md` (domestic parallel).

---

## Recipient Name / Lookup Recipient Legacy Parity Audit

> Added 2026-06-30. Focused parity audit of how the **Recipient (Beneficiary) Name** is populated for Overseas Payment and whether the **Lookup Recipient** API (legacy `ckRecName`) participates, including the **HAENGSUNG Refresh** path. Evidence-based; **no runtime logic changed**. Overseas stores the recipient as `BNFC_NM` (Beneficiary Name) on `OVRS_PYMN_SUMR_DETL`, not `RCPT_NM`.

### 1. Legacy behavior summary

Overseas Payment resolves the beneficiary from a **mandatory overseas contact** (Beneficiary Code, col 0 → `TrcoInfm` where `trco_type='O'`, `trco_stts NOT IN ('C')`; `pymn_0007_003_r001_act.jsp:307-403`). `getOverseasContactInfo` → `TrcoOvrsInfm` cross-fills `BNFC_NM` (beneficiary name) along with `BIC_CODE`, `BNFC_ACCT_NO`, `BNFC_ADDR`, `BNFC_BANK_NM`, `BNFC_BANK_CD` (`:141-143`). `BNFC_NM` + `BNFC_ADDR` pass VN-tone replacement + `SpecialCharValidator`; any blank-after-resolve adds `WBW1159`. There is **no domestic NAPAS/CITAD network split and no `ckRecName` live inquiry** — the beneficiary name comes from the stored overseas-contact profile.

### 2. Refactor behavior summary

`OverseasPaymentExcelService.preview` resolves the beneficiary from the overseas contact at parse and forwards the beneficiary fields on the wire; `doSubmit` persists `OVRS_PYMN_SUMR_DETL.BNFC_NM` (+ `BNFC_BANK_NM` / `BNFC_BANK_CD` / `BNFC_BANK_BIC` / `BNFC_ACCT_NO` / `BNFC_ADDR`) **from the row** (audit §2.3 + §8.2). No live `ckRecName`-style bank inquiry — at parity with legacy. **HAENGSUNG Refresh** sources the beneficiary name from Oracle `TB_TRNS_DETAIL.ACET_NM` (tone-stripped) → `BNFC_NM` (Overseas routed by `FILE_GB='AF1'`; [`ErpPaymentRefreshMasterImporter.java:216`](../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/haengsung/service/ErpPaymentRefreshMasterImporter.java)), flag-gated OFF (`HAENGSUNG_ORACLE_ENABLED=false`).

### 3. Recipient (Beneficiary) Name source comparison

| Source | Legacy | Refactor | Match |
|---|---|---|---|
| Contact / saved recipient (`TRCO_OVRS_INFM.BNFC_NM` via beneficiary code) | ✅ **the source** | ✅ **the source** | ✅ |
| Bank Account / account master (`ACCT_MSTR`) | ❌ never | ❌ never | ✅ |
| Lookup Recipient API (`ckRecName` / NAPAS inquiry) | ❌ never (no domestic network) | ❌ never | ✅ |
| EFS / VAN / WeCMS Batch | ❌ not the source (resolved pre-dispatch; SWIFT) | ❌ not the source | ✅ |
| Manual Excel cell | ❌ Excel carries the *code*, not the name | ❌ name resolved from contact | ✅ |
| HAENGSUNG ERP Refresh (`TB_TRNS_DETAIL.ACET_NM`) | n/a (legacy ERP import) | ✅ implemented, flag OFF | ⚠️ (env) |

### 4. Lookup Recipient condition

| Network | Legacy | Refactor |
|---|---|---|
| NAPAS | n/a (overseas/SWIFT) | n/a |
| CITAD | n/a (overseas/SWIFT) | n/a |

Overseas has **no Lookup Recipient condition** — there is no domestic depositor inquiry; the beneficiary name is read from the overseas-contact profile (`TRCO_OVRS_INFM`). Blank-after-resolve → `WBW1159` (legacy).

### 5. Contact vs Bank Account source rules

**Contact-based only (overseas contact) — never `ACCT_MSTR`, never a typed name.** The Excel cell holds the *beneficiary code*; the beneficiary name and bank/BIC/account details are pulled from `TRCO_OVRS_INFM`. Both systems agree. (Refactor divergence noted in §8.2 is `TRCO_KEY=""` on the detail row — a key-linkage difference, **not** a beneficiary-name source difference.)

### 6. EFS / VAN / WeCMS Batch dependency status

**None for the beneficiary name.** The name is resolved from the overseas contact at parse, before any dispatch. The SWIFT/firm-banking dispatch path (code-complete, flags OFF, mock clients throw) carries the resolved `BNFC_NM` forward; it does not source it. The HAENGSUNG Refresh alternative sources it from Oracle and is independent of EFS/VAN.

### 7. Gap list

- **No gap** in the beneficiary-name *source* (overseas contact, parity) or Lookup Recipient (never invoked, parity).
- Related, **not** a recipient-name gap: `OVRS_PYMN_SUMR_DETL.TRCO_KEY=""` on excel-uploaded details (§8.2) and the `ERP_TRNS_HSTR`-on-upload / chart-acct-seq gaps (§8) — tracked separately in §10.

### 8. Final verdict: **PASS**

Beneficiary (Recipient) Name source and Lookup Recipient behavior are at legacy parity — both resolve `BNFC_NM` from the overseas contact and neither performs a live recipient inquiry. No code change recommended for the recipient-name behavior specifically.

### 9. Environment / verifiability note

The Excel-Upload beneficiary path is **verifiable in the current environment** — beneficiary name resolution is a DB read of `TRCO_OVRS_INFM` with no EFS/VAN/WeCMS Batch dependency. The HAENGSUNG Refresh beneficiary path is implemented but requires the HAENGSUNG Oracle connection (`HAENGSUNG_ORACLE_ENABLED=false`) to verify end-to-end; the standard Excel-Upload path is unaffected and verifiable now.

---

### 10. Lead Engineer disposition (2026-06-30, documentation-only)

**Decision: no runtime code changed — ✅ no action required.** Code-verified 2026-06-30: the beneficiary name resolves from the overseas contact (`resolveBeneficiary(...)` → `TRCO_OVRS_INFM`) at preview ([`OverseasPaymentExcelService.java:260,300`](../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/service/OverseasPaymentExcelService.java)), is forwarded unchanged by the frontend, and is saved exactly once at `:701` (`setBnfcNm(nullToEmpty(src.bnfc_nm()))`) with no later overwrite. `BNFC_NM VARCHAR(100) NOT NULL` is honored via `nullToEmpty` on both the save path and the HAENGSUNG importer (`ErpPaymentRefreshMasterImporter.java:216`). No live `ckRecName`-style inquiry exists or is needed (no domestic network). **No EFS/VAN dependency** for the beneficiary name — verifiable today; the HAENGSUNG Refresh path is implemented but flag-gated OFF (`HAENGSUNG_ORACLE_ENABLED=false`). **No runtime files were changed in this audit.**

---

## HAENGSUNG ERP Refresh Integration Note

Final product behavior after the HAENGSUNG ERP Refresh feature implementation.

| Payment Type | Standard WeCMS | HAENGSUNG |
|--------------|---------------|-----------|
| Internal Transfer | Excel Upload | Excel Upload |
| Vendor Payment | Excel Upload | Refresh (ERP Import) |
| Expense Payment | Excel Upload | Refresh (ERP Import) |
| Payroll | Excel Upload | Refresh (ERP Import) |
| Overseas Payment | Excel Upload | Refresh (ERP Import) |

- These audits verify the Excel Upload implementation and Excel templates.
- Standard WeCMS continues using Excel Upload for all payment modules.
- For the HAENGSUNG product type, the Refresh button replaces the Excel Upload button only for:
  - Vendor Payment
  - Expense Payment
  - Payroll
  - Overseas Payment
- Refresh imports payment requests directly from the Oracle ERP interface tables instead of Excel files.
- Internal Transfer is NOT part of the ERP Refresh integration and continues using Excel Upload for both Standard WeCMS and HAENGSUNG product types.
- Therefore, all Excel Upload audit results remain valid whenever the Excel Upload workflow is used.

See [haengsung-payment-refresh.md](../features/haengsung-payment-refresh.md) for the ERP Refresh design + Oracle mapping, and [payment/README.md → "Payment import strategy"](../features/payment/README.md#payment-import-strategy--standard-wecms-vs-haengsung) for the module-level matrix.

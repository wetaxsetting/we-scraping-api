# Audit — Vendor Payment › Scheduled Payment › Excel Upload / Refresh (Legacy Parity)

- **System pair:** Legacy JEX (`WECMS DEV/`) ⇄ Refactor (`wecms-api/` + `wecms-web/`)
- **Feature slug:** `vendor-payment` (Scheduled Payment upload)
- **Legacy WSVC ids:** `pymn_0001_001` (list), `pymn_0001_002` (upload popup), `pymn_0001_002_r001` (parse/validate), `pymn_0001_002_c001` (save), `comm_1001_001` (withdrawal-account picker)
- **Date:** 2026-06-29
- **Status:** **PARTIALLY IMPLEMENTED + FINAL PARITY VERIFIED (2026-06-29)** — GAP-1, GAP-3, GAP-4, GAP-6, GAP-8 fixed, re-audited against legacy, and verified (full backend suite green). GAP-2, GAP-5, GAP-7 deferred per instruction (documented only). See §11 for the final parity verification.
- **Scope rule followed:** Analyze → Compare with Legacy → Root Cause → Impact → MD → Approval → Implement approved gaps only → Final parity verification (report-only).

---

## 0. Implementation Status (2026-06-29)

| Gap | Severity | Status | Legacy re-verified | Changed files |
|---|---|---|---|---|
| GAP-6 | MEDIUM | ✅ **FIXED** | `comm_1001_001_act.jsp:31,51` (3 banks + V/W/F) | `WithdrawalAccountConstants.java`, `WithdrawalAccountListService.java`, `WithdrawalAccountListServiceTest.java` |
| GAP-1 | HIGH | ✅ **FIXED** | `PymnHelper.java:1360-1364,1468` | `VendorPaymentService.java`, `TbFirmBankCodeRepository.java` |
| GAP-8 | HIGH | ✅ **FIXED** | `PymnHelper.java:1232-1257,1450-1451` (`getBankCode`) | `VendorPaymentService.java`, `TbFirmBankCodeRepository.java` |
| GAP-4 | MEDIUM | ✅ **FIXED** | `comm_1001_001_act.jsp` (`data-ccycd`=`ACCT_MSTR.CCY_CD`) | `VendorPaymentService.java` |
| GAP-3 | LOW→reachable after GAP-6 | ✅ **FIXED** | `PymnHelper.java:118,1454-1465` (`VCB_BANK_CD="970436"`) | `VendorPaymentExcelParser.java`, `VendorPaymentExcelParserTest.java` (new) |
| GAP-2 | MEDIUM | 🔶 **CONDITIONAL GAP** — pending bank-inquiry socket + PO policy (see §14) | `PymnHelper.java:861-1040,1426` | — |
| GAP-5 | — | ✅ **NOT A GAP** — refactor functionally equivalent to legacy (see §14) | `pymn_0001_002.ts:145,154-162,462` | — |
| GAP-7a | LOW | ✅ **FIXED 2026-06-29 (web-only)** — preview Recipient Name + Contact Name columns added (see §17) | `pymn_0001_002_view.jsp:167,174` | `VendorPaymentUploadDialog.tsx`, `locales/{en,ko,vi}.json` |
| GAP-7b | — | ✅ **NOT A GAP** — legacy also silently caps at 500 (see §14) | `PymnHelper.java:315-317,326` | — |

> **Status reconciled 2026-06-29 by the §14 re-audit.** The earlier "GAP-5 / GAP-7 = MEDIUM/LOW deferred gaps" rows were **overstated** — see §14.3. GAP-5 and GAP-7b are **not gaps**; GAP-7a is a cosmetic UI-only difference; only GAP-2 remains a (conditional) gap. Sections §8/§9/§10 below predate the re-audit; **where they disagree with §14, §14 wins.**

**Build/verification:** `./gradlew compileJava compileTestJava` clean; `./gradlew test --tests WithdrawalAccountListServiceTest --tests com.kosign.wecms.domain.payment.vendorpayment.*` → **BUILD SUCCESSFUL** (exit 0).

**Implementation notes / decisions:**

- **GAP-6 confirmed, not just suspected.** Direct read of `comm_1001_001_act.jsp` line 31 = `bank_cd in ('11704088', '11704020', '11704004')` and line 51 = `AUTH_TYPE IN ('V', 'W', 'F')`. Bank `11704004` = **Vietcom Bank (VCB)** (confirmed via `BANK_INFM`). The refactor's 2-bank + V/W set was a genuine parity defect — VCB withdrawal accounts and `F`-granted accounts were hidden from the picker. The fix is in the **shared** `WithdrawalAccountListService` (legacy `comm_1001_001` is module-agnostic), so Internal Transfer / Expense / Payroll / Overseas inherit the same corrected list — this is intended legacy parity, not scope creep.
- **GAP-1 + GAP-8 resolved at the save path** (`VendorPaymentService.doCreateDraft`), mirroring legacy `PymnHelper.uploadPaymentExcel` which resolves `getBankCode` + `CITAD_BANK_CD` at insert time — **not** at parse/preview. This keeps the upload **preview** showing the user-typed code (also legacy-correct: the legacy `pymn_0001_002_r001` read step shows raw cell values) while persisting legacy-shaped codes. CITAD `TAR_BANK_CD` now = the 6-digit head-office `BANK_CD` (`bank_branch_div='1'`); `CITAD_BANK_CD` = `"000"` (NAPAS) / 3-char (CITAD). A new repository finder `findFirstByAntDivCdAndCitadBankCdAndBankStasAndBankBranchDiv` was added because the existing `findFirstByAntDivCdAndCitadBankCd` does **not** filter on `bank_branch_div` (its Javadoc was misleading) and would return an arbitrary branch row.
- **GAP-4 resolved server-side** from `ACCT_MSTR.CCY_CD` of the selected `(BIZ_KEY, BANK_CD, ACCT_NO)` rather than adding a wire field — this avoids touching the **shared** `CreateScheduledTransferRequest` DTO + the web, and is the most legacy-faithful source (legacy reads the dropdown's `data-ccycd`, which is the account's own currency). Falls back to the transfer currency only if the account row can't be resolved.
- **GAP-3 fixed at parse time** (`VendorPaymentExcelParser`), mirroring the sibling `InternalTransferExcelParser` rather than legacy's save-time placement — a deliberate consistency choice. Legacy `PymnHelper.uploadPaymentExcel:1460-1465` blocks at save; the refactor's established pattern blocks per-cell at parse so the error shows in the upload **preview** before save (also matching legacy's "block at upload so the user sees the error before Transfer" intent) and leaves no orphan file. The dispatch-time re-check (`VendorDispatchExecutor` → `WBE2088`, legacy `pymn_0001_001_c001`) remains the authoritative guard — both layers now exist for Vendor, same as Internal Transfer. Verified `OutBankCdNormalizer.normalize("11704004") = "970436"` and a NAPAS VCB recipient resolves to `970436` too, so the guard fires correctly (not inert). Constant `VCB_BANK_CD = "970436"` matches legacy `PymnHelper.java:118` + the sibling parser.

> **Source-of-truth note.** Refactor findings are cited from disk (file:line). Legacy findings are traced from `WECMS DEV/` JSP/TS/Java. Two legacy facts in §6 (withdrawal bank set + `AUTH_TYPE`) came back from the legacy code trace in a form that **conflicts with the refactor's own legacy-parity Javadoc**; both are flagged as **VERIFY** items, not confirmed gaps, until the exact `comm_1001_001_act.jsp` lines are re-read directly.

---

## 1. Summary

The Vendor Payment Excel upload was refactored as a **thin Vendor wrapper over the Internal Transfer baseline**: Vendor and Internal Transfer share the physical `SCDUL_PYMN_SUMR` / `SCDUL_PYMN_SUMR_DETL` tables (discriminated by `SCDUL_PYMN_TYPE_CD = '01'` for Vendor) and reuse the same DTOs, shared dialog shell, and key generators. The **one structural difference** from Internal Transfer — an extra **Contact Code (`HAND_TRCO_KEY`)** column at index 7 — is implemented end-to-end (template, parser, preview, save).

Overall the refactor reproduces the legacy flow faithfully for the common path:

- ✅ Template download (byte-identical JEX `.xlsx`, per-locale EN/KO/VI).
- ✅ Header validation (row 3, 10 columns, EN/KO/VI tolerant).
- ✅ File-extension / file-shape validation.
- ✅ Per-row validation: Bank Code → `TB_FIRM_BANK_CODE`; Deposit No. → `TRCO_ACCT_INFO`; Contact Code → `TRCO_INFM`; Reporting Account → `CHART_ACCT_INFM`; Transfer Amount; Transfer Currency; special-char.
- ✅ Cross-row consistency (single currency / VND-USD whitelist / single network).
- ✅ Completed/Error counts + per-cell red highlight preview.
- ✅ Save to `SCDUL_PYMN_SUMR` (+ detail), default status values (`TRNF_YN='N'`, no `APPR_SEQ_NO`), ERP-history + audit-log writes.

**Gaps found (detail in §9; verdicts reconciled by the §14 re-audit):** the initial pass listed 8 items. After implementation + the §14 legacy re-audit: **5 fixed** (GAP-1/3/4/6/8), **1 conditional** (GAP-2 — NAPAS recipient verify/persist, blocked on the bank-inquiry socket + PO policy), **1 UI-only difference** (GAP-7a — preview omits Recipient/Contact-Name columns), and **2 not gaps** (GAP-5 partial-upload — legacy also blocks upload when `err_cnt > 0`; GAP-7b 500-row cap — legacy also silently caps at 500). The earlier "partial-upload-of-valid-rows blocked" and "refactor-only 500-row cap" framings were **overstated** — corrected in §14.3.

No gap blocks the happy-path NAPAS-VND flow. The HIGH items are **data-migration / dispatch-parity** risks on **CITAD** rows.

---

## 2. Legacy Code Trace

| # | Concern | Legacy file / WSVC | Notes |
|---|---|---|---|
| List screen | `pymn_0001_001` | `WECMS/web/view/jex/wabooks/pymn/pymn_0001_001_view.jsp` + `pymn_0001_001_r001_act.jsp` | Filtered by `SCDUL_PYMN_TYPE_CD='01'` (Vendor). |
| Template download | — | `WECMS_STATIC/ts-src/js/jex/wabooks/pymn/pymn_0001_002.ts:277-280` (`fnDownloadExcelForm`) | URL `file/excel/VN/WeCMS_Vendor_Payment_upload_{LANG}.xlsx?{ts}`; files under `WECMS_STATIC/web/file/excel/VN/` (EN/KO/VI). |
| Upload popup | `pymn_0001_002` | `pymn_0001_002_view.jsp` | Download link, file browser, withdrawal-account dropdown, preview table, Completed/Error counters. |
| Parse / validate | `pymn_0001_002_r001` | `pymn_0001_002_r001_act.jsp` → `PymnHelper.importDataFromExcel()` / `readExcel()` (`src/wabooks/helper/PymnHelper.java:303-607`) | Returns `TOT_CNT` / `SUC_CNT` / `ERR_CNT` + `REC` rows with `ERR_FLD` / `ERR_FLD_CAUS`. |
| Save | `pymn_0001_002_c001` | `pymn_0001_002_c001_act.jsp` → `PymnHelper.uploadPaymentExcel()` (`PymnHelper.java:1301-1472`) | INSERT `SCDUL_PYMN_SUMR` + N× `SCDUL_PYMN_SUMR_DETL`; update header total; ERP history. |
| Withdrawal accounts | `comm_1001_001` | `comm_1001_001_act.jsp` (IDO `ACCT_MSTR_R011`) | Tenant + bank-set + `acct_type_cd IN ('03'..'08')` + `ACTV_YN='Y'` + `ACCT_AUTH_INFO` grant + `TB_FIRM_CONF` (`ORG_STAT='1'`). |

**Legacy data-side column order (template row 3 / `readExcel` cell indices 0–9):**

0 Bank Code* · 1 Deposit No.* · 2 Bank (auto) · 3 Transfer Amount* · 4 Transfer Currency* · 5 Remarks (200) · 6 Reporting Account · 7 **Contact Code\*** · 8 Description · 9 Document No.

**Legacy validations of note:**
- Header: `validDefualtHeader()` (`PymnHelper.java:175-219`) — EN/KO/VI, `WBE0001` on mismatch.
- Extension: `.xls`/`.xlsx` only (`pymn_0001_002.ts:217-223`, `WBE1041`).
- Bank code: 3-char→CITAD, 6-char→NAPAS, against `TB_FIRM_BANK_CODE` (`bank_stas='1'`).
- Currency: VND/USD only; mixed currency → `WBW1160`.
- Contact Code: `ckContactCode()` against contact master.
- Deposit/recipient: NAPAS→`ckRecName()` (live bank lookup); CITAD→`getfincMdaOwnerNmFromAcctInfo()` (`TRCO_ACCT_INFO`).
- Reporting Account: optional, `CHART_ACCT_INFM` (`del_yn='N'`, `stts='A'`).
- **VCB-USD restriction:** `PymnHelper.java:1454-1462` — VCB out-bank + USD to other banks → reject.
- **Save sets** `CITAD_BANK_CD = "000"` (NAPAS) or 3-char (CITAD); `TAR_BANK_CD` = 6-digit resolved code (`getBankCode()`); `WDRL_CCY_CD` = withdrawal-account currency (separate from transfer `CCY_CD`).

---

## 3. Refactor Code Trace

### Web (`wecms-web/`)

| Concern | File |
|---|---|
| List page + upload trigger | `components/payment/vendor-payment/ScheduledPaymentPage.tsx` (`openUpload`, `VendorPaymentUploadDialog` at L541) |
| Vendor upload dialog | `components/payment/vendor-payment/VendorPaymentUploadDialog.tsx` |
| Shared Payment upload wrapper | `components/payment/shared/PaymentExcelUploadDialog.tsx` |
| Shared upload shell (UI) | `components/shared/excel-upload/ExcelUploadDialog.tsx` |
| Withdrawal dropdown | `WithdrawalAccountDropdown` inside `VendorPaymentUploadDialog.tsx:299` |
| API client | `lib/api/vendorPayment.ts` (`parseVendorPaymentExcel`, `createVendorPayment`, `fetchVendorWithdrawalAccounts`, `downloadOriginalVendorExcel`) |

- **Template:** static byte-copy of JEX `.xlsx` under `public/templates/vendor-payment/WeCMS_Vendor_Payment_upload_{EN,KO,VI}.xlsx`; KM→EN fallback (`VendorPaymentUploadDialog.tsx:41-45,104-112`).
- **Columns (10):** `ANT_DIV_CD, FINC_MDA_NO, TAR_BANK_NM, TRAN_AMT, TRNF_CCY_CD, TRAN_USAG, CHART_ACCT_CD, HAND_TRCO_KEY, NOTE, DOC_NO` (`VendorPaymentUploadDialog.tsx:122-133`) — index order matches legacy.
- **Parse:** `handleParse` → `parseVendorPaymentExcel(file, bankCd)`; `completedCount = suc_cnt`, `errorCount = err_cnt` (`VendorPaymentUploadDialog.tsx:135-146`).
- **Save:** `handleUpload` filters error-free lines, derives batch currency from first line's `trnf_ccy_cd`, posts `createVendorPayment` (`VendorPaymentUploadDialog.tsx:148-177`).
- **Upload button disable:** `!selectedFile || !parsed || errorCount > 0 || isBusy` (`PaymentExcelUploadDialog.tsx:250`).

### API (`wecms-api/`)

| Concern | File |
|---|---|
| Controller | `domain/payment/vendorpayment/controller/VendorPaymentController.java` (`/uploads/parse` L332, `POST` create L343, `/withdrawal-accounts` L323, `/{id}/file` L363) |
| Service | `domain/payment/vendorpayment/service/VendorPaymentService.java` (`parseExcel` L379, `createDraft`/`doCreateDraft` L412/434, `resolveDownload` L587, `listWithdrawalAccounts` L363) |
| Parser | `domain/payment/vendorpayment/service/VendorPaymentExcelParser.java` |
| Withdrawal service | `domain/payment/common/WithdrawalAccountListService.java` + `WithdrawalAccountConstants.java` |
| Cross-row consistency | `domain/payment/common/UploadConsistencyValidator.java` (invoked `VendorPaymentExcelParser.java:227-230`) |
| DTOs | `internaltransfer/dto/CreateScheduledTransferRequest.java`, `ParseExcelResponse.java`, `ParsedTransferLine.java` |
| Entity | `internaltransfer/entity/ScdulPymnSumr.java` (+ `ScdulPymnSumrDetl`) |

- Endpoints `@PreAuthorize("@menuAuthz.canAccess('payment-vendor-scheduled')")`.
- Parser POI walk: header validate (`validateHeaderRow` L246), rows 4..(4+500), early-break on all-critical-blank (L200), per-row validation (`parseRow` L275).
- Save (`doCreateDraft`): `SCDUL_PYMN_TYPE_CD='01'`, `OUT_BANK_CD = OutBankCdNormalizer.normalize(bankCd)`, `TRNF_YN='N'`, `ERP_PROC_CMPL_YN='N'`, header total summed in Java, file persisted, detail rows numbered via `wb_get_next_val` funnel, ERP-history (`TRNS_TYPE_CD='02'`) + audit (`WSVC_ID='vendor-payment'`).

---

## 4. Template Audit

| Item | Legacy | Refactor | Match? | Note |
|---|---|---|---|---|
| File name | `WeCMS_Vendor_Payment_upload_{LANG}.xlsx` | same (byte-copy in `public/templates/vendor-payment/`) | ✅ | |
| Locales | EN / KO / VI | EN / KO / VI (+ KM→EN) | ✅ | JEX has no KM either |
| Download mechanism | anchor href to `WECMS_STATIC` path | `<a download>` to Next public asset | ✅ (functional) | refactor serves a copy, not the live JEX path |
| Header row | row 3 | row 3 (`HEADER_ROW=3`) | ✅ | |
| First data row | row 4 | row 4 (`FIRST_DATA_ROW=4`) | ✅ | |
| Column count / order | 10 (Contact Code at idx 7) | 10 (idx 7 `HAND_TRCO_KEY`) | ✅ | |
| Header text | EN/KO/VI literals | EN/KO/VI literals, `startsWith` match | ✅ | `HEADERS_EN/KO/VI` L127-147 |
| Row cap | **500, silent** (`PymnHelper.java:315-317,326`) + early-break on blank | 500 (`MAX_ROWS`) + early-break | ✅ | **Parity (GAP-7b = NOT a gap).** Legacy *also* silently caps at 500 (`if (rowCount > 500) rowCount = 500`) with no user notice — the earlier "refactor-only cap / silent truncation" note was wrong. See §14. |

---

## 5. Excel Read & Validation Audit

| Validation | Legacy | Refactor | Match? |
|---|---|---|---|
| Extension `.xls/.xlsx` | TS `WBE1041` | API `WBE_UNSUPPORTED_MEDIA_TYPE` (`VendorPaymentService.java:387`) + web `accept` (`PaymentExcelUploadDialog.tsx:266`) | ✅ |
| File present / non-empty | yes | `WBE_VALIDATION` (`VendorPaymentService.java:381`) | ✅ |
| Header validation | `validDefualtHeader` | `validateHeaderRow` → `WBE2018` | ✅ |
| Bank Code required + len 3/6 + lookup | `TB_FIRM_BANK_CODE` | `parseRow` L341-372 (`findByAntDivCdAndBankCd` / `findFirstByAntDivCdAndCitadBankCd`) | ✅ |
| Deposit No. required | yes | L288 (`FINC_MDA_NO`) | ✅ |
| Deposit registered to contact | `ckDepositNo` / `TRCO_ACCT_INFO` | L404-455 (`findByBizKeyAndTrcoKeyAndAcctNo` + bank-identity match) | ✅ (refactor adds cross-variant NAPAS↔CITAD identity match) |
| Contact Code required + ≤20 + lookup | `ckContactCode` / `TRCO_INFM` | L308-334 (`findActiveByBizKeyAndHandTrcoKey`) | ✅ |
| Transfer Amount required + numeric ≥ 0 | yes | L485-500 | ✅ |
| Transfer Currency required | yes | L502-507 | ✅ |
| Currency whitelist VND/USD | `WBW1160`/whitelist | `UploadConsistencyValidator.assertCurrencyWhitelist` | ✅ |
| Single currency per file | `WBW1160` | `assertSingleCurrency` → `WBE2120` | ✅ |
| Single network per file | `WBE1191` | `assertSingleNetwork` → `WBE2121` | ✅ |
| Remarks special-char (≤200) | `SpecialCharValidator` | `specialCharValidator.validate(...REMARKS)` L554-558 | ✅ (refactor no-op until `BLOCKED_CHARS_RULE` seeded for Vendor) |
| Reporting Account lookup (optional) | `CHART_ACCT_INFM` `del_yn='N'/stts='A'` | L520-546 (`findByBizKeyAndChartAcctCdAndDelYn`) | ✅ |
| Recipient name — CITAD | `getfincMdaOwnerNmFromAcctInfo` | cross-fill `FINC_MDA_OWNER_NM` L441-444 | ✅ |
| Recipient name — NAPAS | `ckRecName` (live bank API, errors on mismatch) | `firmBankingInquiryService.inquiryAccount` returns empty → **no-op** L466-483 | ❌ **GAP-2** |
| Duplicate / end-of-data break | all-key-blank → break | all-critical-blank → break L200-203 | ✅ |
| VCB-USD cross-bank restriction | reject (`PymnHelper:1454-1462`) | **absent** | ⚠️ **GAP-3** (unreachable — see §9) |
| Error/Completed counts | `SUC_CNT`/`ERR_CNT`/`TOT_CNT` | `sucCnt`/`errCnt`/`totCnt` (`VendorPaymentExcelParser.java:235-239`) | ✅ |
| Per-cell error marking | `ERR_FLD` / `ERR_FLD_CAUS` (pipe-delimited) | `errFld`/`errFldCaus` → red cell tint + tooltip | ✅ |

---

## 6. Withdrawal Bank / Account No. Audit

Legacy `comm_1001_001` (module-agnostic; shared by all payment types). Refactor: `WithdrawalAccountListService.list()` + `WithdrawalAccountConstants`.

| Filter | Legacy (`comm_1001_001_act.jsp`) | Refactor | Match? |
|---|---|---|---|
| Tenant `biz_key` | yes | `requireTenant()` | ✅ |
| `ACTV_YN='Y'` | yes | repo query | ✅ |
| `acct_type_cd IN ('03'..'08')` | yes | repo query (web label map `03-08` `VendorPaymentUploadDialog.tsx:49-56`) | ✅ |
| Bank set | trace reports `IN ('11704088','11704020','11704004')` (**3**) | `WITHDRAWAL_BANK_CDS = ('11704088','11704020')` (**2**) | ❌ **GAP-6 — VERIFY** |
| `ACCT_AUTH_INFO` grant types | trace reports `AUTH_TYPE IN ('V','W','F')` | intersect `('V','W')` only | ❌ **GAP-6 — VERIFY** |
| `TB_FIRM_CONF` (`ORG_STAT='1'`) | required | required (G3); `firm-banking-fallback` flag relaxes only on local R&D DB | ✅ |
| Admin bypass | none | none (documented) | ✅ |
| Sort | `bank_cd, acct_no` | repo order-preserving | ✅ |
| Display format | `{BANK_NM} {ACCT_NO} ({TYPE} {CCY})` | `accountDisplay` (`VendorPaymentUploadDialog.tsx:60-62`) | ✅ |

> **GAP-6 is a direct conflict** between the legacy trace and the refactor's own legacy-parity Javadoc (which claims legacy is 2 banks + V/W). It must be resolved by re-reading `comm_1001_001_act.jsp:23-65` verbatim before any change. If the legacy trace is correct, the refactor is **missing bank `11704004` and `AUTH_TYPE='F'`**, which would hide legitimate withdrawal accounts from the picker.

**Withdrawal account condition in the flow:** Save is blocked on the web side when no account is selected (`!selectedAccount` throws; `VendorPaymentUploadDialog.tsx:150-152`). The selected account's `bank_cd` is passed to parse (`bankCd`, used for special-char rule scoping) and to save (`OUT_BANK_CD` / `WDRL_ACT_NO`).

---

## 7. Upload Save DB Audit

Both write `SCDUL_PYMN_SUMR` (master) + `SCDUL_PYMN_SUMR_DETL` (N rows).

### `SCDUL_PYMN_SUMR` (master)

| Column | Legacy save | Refactor save (`doCreateDraft`) | Gap |
|---|---|---|---|
| `BIZ_KEY` | tenant | tenant | — |
| `SCDUL_PYMN_NO` | `SeqHelper.getNextVal` | `InternalTransferKeyGenerator.nextScdulPymnNo` | key-format differs (documented OD-2) — §9 GAP-9 |
| `SCDUL_PYMN_TYPE_CD` | Vendor | `'01'` | — |
| `OUT_BANK_CD` | NAPAS code | `OutBankCdNormalizer.normalize(bankCd)` | — |
| `WDRL_ACT_NO` | input | `req.acctNo()` | — |
| `CCY_CD` (transfer) | transfer ccy | `req.ccyCd()` | — |
| `WDRL_CCY_CD` | **withdrawal-account ccy** | `req.ccyCd()` (= transfer ccy) | ❌ **GAP-4** |
| `TITL` | input | file name (sans ext) | ⚠️ acceptable; legacy uses subject input |
| `TOTAL_TRAN_AMT` | Σ amounts | Σ amounts (Java) | — |
| `TOT_TRNS_CNT` | detail count | line count | — |
| `TRNF_YN` | `N` | `N` | — |
| `ERP_PROC_CMPL_YN` | `N` | `N` | — |
| `APPR_SEQ_NO` | null (draft) | null | — |
| `ORG_FILE_NM`/`SAVE_FILE_NM`/`FILE_SIZE` | set | set | — |
| `REG_*`/`MOD_*` | login + now | login + now (UTC `yyyyMMddHHmmss`) | — |

### `SCDUL_PYMN_SUMR_DETL` (detail)

| Column | Legacy save | Refactor save | Gap |
|---|---|---|---|
| `SCDUL_PYMN_DETL_NO` | seq | `wb_get_next_val` funnel (+Java increment) | — |
| `TRAN_AMT` | col 3 | `line.tranAmt` | — |
| `TRNF_CCY_CD` | col 4 | `line.trnfCcyCd` (default batch) | — |
| `ANT_DIV_CD` | CITAD/NAPAS by len | `line.antDivCd` | — |
| `RCPT_NM` | NAPAS bank lookup **or** CITAD acct owner | **CITAD only**; NAPAS null | ❌ **GAP-2** |
| `TAR_BANK_CD` | 6-digit resolved (`getBankCode`) | raw input (3 for CITAD / 6 for NAPAS) | ❌ **GAP-8** (CITAD) |
| `CITAD_BANK_CD` | `"000"` (NAPAS) / 3-char (CITAD) | **null** (Phase 2 — `VendorPaymentExcelParser.java:582`) | ❌ **GAP-1** |
| `FINC_MDA_NO` | col 1 | `line.fincMdaNo` | — |
| `TRAN_USAG` | col 5 | `line.tranUsag` | — |
| `TRCO_KEY` | contact lookup | cross-filled | — |
| `HAND_TRCO_KEY` | col 7 | `line.handTrcoKey` | — |
| `TRCO_CMNM`/`TRCO_CMENM` | contact lookup | cross-filled | — |
| `CHART_ACCT_CD`/`CHART_ACCT_SEQ_NO` | col 6 + lookup | resolved + seq | — |
| `DOC_NO` | col 9 | `line.docNo` | — |
| `NOTE` | col 8 | `line.note` | — |
| `DEPO_CCY_CD` | (per acct) | `line.depoCcyCd` | — |
| `REG_*`/`MOD_*` | login + now | login + now | — |

**Side-effects on save (both):** header total update (refactor sums in Java pre-insert), ERP Transaction History row (`TRNS_TYPE_CD='02'` Vendor Scheduled Payment). Refactor additionally writes an `AUDIT_LOGS_INFM` row (`WSVC_ID='vendor-payment'`) — acceptable refactor behavior.

**Approval linkage at upload:** neither side links approval at upload time. Approval is a separate **Request Approval** step (`comm_0192_001_c005` ⇄ `requestApproval`). Correct parity.

---

## 8. Legacy vs Refactor Comparison Table

| Area | Legacy behavior | Refactor behavior | Match? | Gap / Risk |
|---|---|---|---|---|
| Template columns | 10, Contact Code idx 7 | 10, idx 7 | ✅ | — |
| Template file name | `WeCMS_Vendor_Payment_upload_{LANG}.xlsx` | same (byte-copy) | ✅ | refactor serves a copy |
| Header names/order | EN/KO/VI row 3 | EN/KO/VI row 3 | ✅ | — |
| Required fields | BankCode, Deposit, Amount, Currency, Contact | same | ✅ | — |
| Vendor/recipient validation | Contact Code → `TRCO_INFM` | same | ✅ | — |
| Bank Code validation | `TB_FIRM_BANK_CODE`, 3/6 | same | ✅ | — |
| Deposit/Account No. validation | `TRCO_ACCT_INFO` per contact | same (+cross-variant match) | ✅ | — |
| Bank name handling | auto from code | auto from code | ✅ | — |
| Transfer Amount validation | required, numeric | required, numeric ≥0 | ✅ | — |
| Transfer Currency validation | VND/USD, single | VND/USD, single | ✅ | — |
| Withdrawal Bank/Account condition | bank-set + auth + firm-conf | mostly same | ⚠️ | **GAP-6** bank-set/AUTH_TYPE (VERIFY) |
| Recipient name (NAPAS) | live bank-API verify + persist | inert (no verify, RCPT_NM null) | ❌ | **GAP-2** |
| `CITAD_BANK_CD` saved | `"000"`/3-char | null | ❌ | **GAP-1** (HIGH) |
| `TAR_BANK_CD` (CITAD) | 6-digit resolved | 3-char raw | ❌ | **GAP-8** (HIGH) |
| `WDRL_CCY_CD` | withdrawal acct ccy | transfer ccy | ❌ | **GAP-4** |
| Row success/error count | `SUC/ERR/TOT_CNT` | `suc/err/totCnt` | ✅ | — |
| Upload button state | upload **only proceeds when `err_cnt <= 0`** (save click gated, `pymn_0001_002.ts:145`; subset path commented out) | disabled if **any** error | ✅ | **GAP-5 = NOT a gap** (functionally equivalent; see §14). Earlier "valid-only upload allowed" claim was incorrect. |
| DB save table/columns | `SCDUL_PYMN_SUMR(_DETL)` | same | ✅ | minus GAP-1/2/4/8 cols |
| Default status values | `TRNF_YN='N'`, ERP `N`, no appr | same | ✅ | — |
| Approval status/requestor/approver | not set at upload | not set at upload | ✅ | — |
| Error message behavior | per-cell red + tooltip | per-cell red + tooltip | ✅ | — |
| Popup close/reset behavior | close clears; re-select re-parses | close clears; re-select re-parses (`PaymentExcelUploadDialog.tsx:164-183`) | ✅ | — |
| VCB-USD cross-bank guard | reject | absent | ⚠️ | **GAP-3** (unreachable) |
| Preview columns | incl. Recipient Name + Contact Name | template 10 only | 🎨 | **GAP-7a = UI-only difference** (data on wire; optional design decision; see §14) |
| Row cap | **500, silent** | 500, silent | ✅ | **GAP-7b = NOT a gap** (both silently cap at 500; see §14) |

---

## 9. Gaps / Bugs Found

### GAP-1 — `CITAD_BANK_CD` never persisted (HIGH) — ✅ FIXED 2026-06-29
- **Where:** `VendorPaymentExcelParser.java:582` sets `citadBankCd = null` (commented "Phase 2"); `doCreateDraft` writes it through verbatim.
- **Legacy:** `PymnHelper.uploadPaymentExcel` sets `CITAD_BANK_CD = "000"` (NAPAS) or the 3-char CITAD code.
- **Root cause:** Vendor parser reuses the Internal Transfer line shape where the CITAD bank-code resolver was deferred.
- **Impact:** CITAD detail rows save with `CITAD_BANK_CD = NULL`. Risks downstream **dispatch file generation** (CITAD routing) and **JEX→refactor data-migration drift** for CITAD payments. NAPAS-only tenants unaffected.

### GAP-8 — CITAD `TAR_BANK_CD` stored as 3-char raw, not 6-digit (HIGH) — ✅ FIXED 2026-06-29
- **Where:** `VendorPaymentExcelParser.java:360,568` — `tarBankCd = resolvedBankCd` (trimmed Excel input).
- **Legacy:** stores the 6-digit resolved code via `getBankCode()`.
- **Root cause:** same deferred CITAD resolver as GAP-1.
- **Impact:** For CITAD rows, `SCDUL_PYMN_SUMR_DETL.TAR_BANK_CD` differs in format from legacy → dispatch + migration parity risk. Tightly coupled with GAP-1; fix together.

### GAP-2 — NAPAS recipient-name not verified or persisted — 🔶 CONDITIONAL GAP (refined by §14, 2026-06-29)
- **Where:** `VendorPaymentExcelParser.java:487-501` — `firmBankingInquiryService.inquiryAccount(...)` returns empty until the socket call is wired, so the comparison never runs; `RCPT_NM` left null for NAPAS rows (only CITAD cross-fills, L418,441-444).
- **Legacy (re-verified):** `ckRecName()` runs a **live** bank inquiry **whenever `TB_FIRM_CONF` config exists** (`PymnHelper.java:874-876,964-965`), persists the bank-returned `FINC_MDA_OWNER_NM` into `RCPT_NM` (`:1011`), and **errors on mismatch** (`:1013,1016`); save mode hard-rejects with `WBE0404` (`:1023-1033`). **When config is missing it does NOT silently skip — it flags `WBE0001 "Recipient Name error"` (`:1037-1038`).** ⚠️ The earlier "matches a legacy 'no firm-banking config → skip' branch" note (and the matching in-code comment) was **incorrect** — corrected in §14.1.
- **Root cause:** the firm-banking inquiry socket (`WB_00000100`) is not yet implemented in the refactor, so no live verify can run; the config-missing flag behavior is also not reproduced.
- **Impact:** NAPAS recipient names are unvalidated and `RCPT_NM` is null for NAPAS → detail/result screens render "—". Conditional on `TB_FIRM_CONF` data + the bank socket — neither available in R&D.
- **Verdict:** **Conditional gap.** Needs the socket port **and** a PO decision on the config-missing policy. See §14 GAP-2 for the full Q&A.

### GAP-4 — `WDRL_CCY_CD` set to transfer currency, not withdrawal-account currency (MEDIUM) — ✅ FIXED 2026-06-29
- **Where:** web sends `ccy_cd = trnf_ccy_cd = batchCcyCd` (`VendorPaymentUploadDialog.tsx:164-174`); service `master.setWdrlCcyCd(req.ccyCd())` (`VendorPaymentService.java:454`).
- **Legacy:** `WDRL_CCY_CD` = the chosen withdrawal account's currency (independent of transfer currency).
- **Root cause:** the create request has only one `ccy_cd` field; the selected account's `ccy_cd` is available client-side but not forwarded as a distinct withdrawal currency.
- **Impact:** `WDRL_CCY_CD` is wrong whenever the withdrawal account currency ≠ transfer currency. Low frequency, but a data-parity/migration discrepancy.

### GAP-5 — Partial upload of valid rows — ✅ NOT A GAP (corrected by §14, 2026-06-29)
- **Superseded.** This entry's original premise was **wrong** and is retained only for traceability.
- **Corrected legacy behavior:** legacy uploads **only when there are zero error rows** — the save click is gated on `err_cnt <= 0` (`pymn_0001_002.ts:145`); with errors present the click is a no-op. `fnSaveVendorPayment` submits `IMPORTED_SUCCESS_DATA` (`:462`) but is only reachable at zero errors. The only subset-upload path (`:154-162`) is **commented out**. The earlier claim — "completed tab uploads valid rows / drops errored ones" — read the button-enable logic (`:376-401`) without the click gate, and is incorrect.
- **Refactor:** `uploadDisabled = … || errorCount > 0` (`PaymentExcelUploadDialog.tsx:250`) → upload only when zero errors. **Functionally equivalent to legacy.**
- **Verdict:** **Not a gap.** No implementation. (Optional, PO-only UX nicety: legacy shows a `WBC1183` pre-upload confirmation the refactor lacks — not a behavior gap.) Full detail: §14 GAP-5.

### GAP-6 — Withdrawal bank-set + `AUTH_TYPE` narrower than legacy (MEDIUM) — ✅ FIXED 2026-06-29
- **Where (was):** `WithdrawalAccountConstants.java` (`'11704088','11704020'`) + `WithdrawalAccountListService.java` (V/W only).
- **Legacy (CONFIRMED by direct read):** `comm_1001_001_act.jsp:31` → `bank_cd in ('11704088', '11704020', '11704004')`; `comm_1001_001_act.jsp:51` → `AAI.AUTH_TYPE IN ('V', 'W', 'F')`. Bank `11704004` = Vietcom Bank.
- **Root cause:** the refactor's parity Javadoc was wrong; the constant + filter were narrower than legacy.
- **Fix:** added `11704004` to `WITHDRAWAL_BANK_CDS`, added `AUTH_TYPE_FILE = "F"` to the grant intersection, corrected the Javadoc, and updated the parity test to lock the 3-bank + V/W/F behavior. Shared `comm_1001_001` semantics → applies to all five payment modules (intended).
- **Impact resolved:** VCB withdrawal accounts and `F`-granted accounts now appear in the picker, matching legacy.

### GAP-3 — VCB-USD cross-bank restriction absent (LOW → reachable after GAP-6) — ✅ FIXED 2026-06-29
- **Was:** no equivalent of `PymnHelper.java:1454-1465` in the Vendor parser. Unreachable before GAP-6; reachable once GAP-6 re-added VCB (`11704004`) to the withdrawal picker.
- **Legacy (re-read):** `PymnHelper.java:118` `VCB_BANK_CD = "970436"`; `:1460-1465` rejects when `OUT_BANK_CD == VCB && TRNF_CCY_CD == "USD" && resolved TAR_BANK_CD != VCB`.
- **Root cause:** the Vendor parser never carried the VCB-USD guard its sibling Internal Transfer parser already had.
- **Fix:** added the guard to `VendorPaymentExcelParser` (parse time, per-cell `FieldError` on `TRNF_CCY_CD`), mirroring `InternalTransferExcelParser` exactly — `VCB_BANK_CD="970436"`, `withdrawalIsVcb = VCB.equals(normalize(bankCd))`, compared against the row's `resolvedBankCd`. New `OutBankCdNormalizer` collaborator injected. The dispatch-time `WBE2088` guard is unchanged.
- **Verified:** `OutBankCdNormalizer.normalize("11704004") = "970436"` (DB-confirmed) so the guard is live, not inert. 3 new unit tests (reject / allow / non-VCB) pass.
- **Note on placement:** legacy blocks at save; the refactor blocks at parse for parity with the sibling module + better UX (error in the upload preview, no orphan file). Net behavior matches legacy: a VCB-out USD batch to a non-VCB target cannot be uploaded.

### GAP-7a — Preview omits Recipient Name + Contact Name columns — 🎨 UI-ONLY DIFFERENCE
- **Where:** `VendorPaymentUploadDialog.tsx:122-133` renders 10 template columns; the resolved `rcpt_nm` / `trco_cmnm` are **on the wire** but not shown as preview columns.
- **Legacy:** preview shows 12 columns incl. Recipient Name (`pymn_0001_002_view.jsp:167`) + Contact name (`:174`).
- **Verdict:** **UI-only difference** — data already resolved server-side; display-only. Optional design/PO decision to add the two read-only columns. See §14 GAP-7.

### GAP-7b — 500-row cap — ✅ NOT A GAP (corrected by §14, 2026-06-29)
- **Superseded.** The earlier "refactor adds a hard cap; legacy breaks only on blank row; surface a truncation notice" observation was **wrong**.
- **Corrected:** legacy *also* silently caps at 500 — `if (rowCount > 500) rowCount = 500;` (`PymnHelper.java:315-317`, mirrored `:661-663`) then `for i=4..rowCount` — with no user notice. The refactor's `MAX_ROWS = 500` (`VendorPaymentExcelParser.java:118,205`) is exact parity.
- **Verdict:** **Not a gap.** No action. (Immaterial numeric nuance: legacy caps at absolute row index 500 ≈ 497 data rows; refactor caps 500 data rows — both silent.)

---

## 10. Fix Plan — Status

### Implemented 2026-06-29 (this MR)

1. **GAP-6 (DONE).** Confirmed against `comm_1001_001_act.jsp` (3 banks + V/W/F). Added VCB `11704004` + `AUTH_TYPE='F'`; corrected Javadoc + parity test.
2. **GAP-1 + GAP-8 (DONE).** Legacy-shaped `TAR_BANK_CD` (6-digit head-office `BANK_CD`) + `CITAD_BANK_CD` (`"000"`/3-char) resolved in `VendorPaymentService.doCreateDraft`, mirroring `PymnHelper.uploadPaymentExcel`/`getBankCode`. New head-office finder added to `TbFirmBankCodeRepository`.
3. **GAP-4 (DONE).** `WDRL_CCY_CD` resolved from `ACCT_MSTR.CCY_CD` of the selected withdrawal account (server-side; no wire/DTO/web change), falling back to the transfer currency only when the account can't be resolved.
4. **GAP-3 (DONE).** VCB-USD cross-bank guard added to `VendorPaymentExcelParser` (parse time, per-cell error on `TRNF_CCY_CD`), mirroring the sibling Internal Transfer parser; `VCB_BANK_CD="970436"`. 3 new unit tests.

### Remaining (deferred per instruction — recommended next steps)

5. **GAP-2 — Conditional gap (deferred).** Live NAPAS recipient verify + `RCPT_NM` persistence is blocked on the firm-banking inquiry socket (`WB_00000100`) and `TB_FIRM_CONF` data, neither available in R&D. When the socket lands, bring to PO: (a) reproduce the live verify + persistence, and (b) decide the config-missing policy — legacy flags every NAPAS row (`PymnHelper.java:1037-1038`), which is likely undesirable for coexistence, so a PO call is required, not a blind port. Correct the misleading in-code comment (§14.1) in the same change.
6. **GAP-7a — UI-only difference (optional).** Design/PO decision on adding the two read-only preview columns (Recipient Name, Contact name). Web-only; data already on the wire. Not a parity blocker.

> **GAP-5 and GAP-7b are NOT gaps** (the §14 re-audit corrected the earlier framing) — no fix required. See §14.0 / §14.3.

Each remaining item must follow the workspace rule: re-read the exact legacy lines, state API+Web + data-migration impact, get approval before editing, and not touch unrelated completed items (per `feedback_preserve_existing_code`).

## 10a. Changed Files + Verification

**Changed files — GAP-6 / GAP-1 / GAP-8 / GAP-4 (earlier MR):**

| File | Change | Gap |
|---|---|---|
| `wecms-api/.../payment/common/WithdrawalAccountConstants.java` | `WITHDRAWAL_BANK_CDS` → 3 banks (+VCB); add `AUTH_TYPE_FILE="F"`; Javadoc | GAP-6 |
| `wecms-api/.../payment/common/WithdrawalAccountListService.java` | grant intersection accepts V/W/**F**; Javadoc G1/G2 | GAP-6 |
| `wecms-api/.../payment/common/WithdrawalAccountListServiceTest.java` | parity lock → 3-bank set + V/W/F (adds VCB+F-grant case) | GAP-6 |
| `wecms-api/.../setting/firmbankingsettings/repository/TbFirmBankCodeRepository.java` | add `findFirstByAntDivCdAndCitadBankCdAndBankStasAndBankBranchDiv` (head-office lookup) | GAP-1/8 |
| `wecms-api/.../payment/vendorpayment/service/VendorPaymentService.java` | `resolveDetailBankCodes` + `resolveWithdrawalCcyCd`; 2 new fields (`firmBankCodeRepo`, `acctMstrRepository`) | GAP-1/8/4 |

**Changed files — GAP-3 (this MR):**

| File | Change | Gap |
|---|---|---|
| `wecms-api/.../payment/vendorpayment/service/VendorPaymentExcelParser.java` | add `VCB_BANK_CD="970436"` + `OutBankCdNormalizer` field; precompute `withdrawalIsVcb` in `parse()`; per-cell VCB-USD-to-other-bank guard in `parseRow` (FieldError on `TRNF_CCY_CD`) | GAP-3 |
| `wecms-api/.../payment/vendorpayment/service/VendorPaymentExcelParserTest.java` (new) | 3 tests: VCB+USD+other-bank→flagged · VCB+USD+VCB→allowed · non-VCB withdrawal unaffected | GAP-3 |

No `wecms-web/` changes (all fixes are backend-only / server-resolved). No DB schema / migration / `messages.properties` changes (`WBE2088` already existed and stays the dispatch-time guard; the parse path uses a per-cell cause string like its sibling).

**Verification:**
- `./gradlew compileJava compileTestJava` → clean (no errors).
- GAP-3: `./gradlew test --tests "…VendorPaymentExcelParserTest" --tests "…InternalTransferExcelParserTest" --tests "…WithdrawalAccountListServiceTest" --tests "…vendorpayment.*" --rerun` → **BUILD SUCCESSFUL** (exit 0). `VendorPaymentExcelParserTest`: `tests=3 failures=0 errors=0` (confirmed in `build/test-results`).
- Legacy re-read before each edit: `comm_1001_001_act.jsp:31,51`; `PymnHelper.java:118` (`VCB_BANK_CD`), `:1232-1257` (`getBankCode`), `:1360-1364` (ANT_DIV_CD/citadBankCd), `:1450-1451`+`:1468` (detail insert), `:1454-1465` (VCB-USD).
- DB cross-check (read-only): `napas_bank_info` row `bank_cd=11704004 → napas_bank_cd=970436` confirms `OUT_BANK_CD` for VCB = `970436`, so the guard is live (not inert). `TB_FIRM_BANK_CODE` CITAD `citad_bank_cd` maps to many `bank_cd` rows with a single `bank_branch_div='1'` head office (GAP-1/8 head-office finder rationale).

---

## 11. Verification Commands

```bash
# Refactor — confirm the cited lines before/after any change
sed -n '560,590p'  "wecms-api/src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentExcelParser.java"   # GAP-1/8 citad/tarBank
sed -n '440,500p'  "wecms-api/src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentExcelParser.java"   # GAP-2 NAPAS recipient
sed -n '444,470p'  "wecms-api/src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentService.java"        # GAP-4 WDRL_CCY_CD
grep -n "WITHDRAWAL_BANK_CDS\|AUTH_TYPE_" "wecms-api/src/main/java/com/kosign/wecms/domain/payment/common/WithdrawalAccountConstants.java"  # GAP-6
grep -n "uploadDisabled\|errorCount" "wecms-web/components/payment/shared/PaymentExcelUploadDialog.tsx"                              # GAP-5

# Legacy — resolve the GAP-6 conflict directly (read-only)
sed -n '20,70p'  "WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_1001_001_act.jsp"
sed -n '1301,1472p' "WECMS DEV/WECMS/src/wabooks/helper/PymnHelper.java"   # save: CITAD_BANK_CD / TAR_BANK_CD / WDRL_CCY_CD / VCB-USD
sed -n '376,401p' "WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/pymn/pymn_0001_002.ts"   # GAP-5 upload button enable logic

# DB schema parity (read-only JEX DEV via MCP postgres_dev_server_jexframe)
#   SELECT column_name,data_type,character_maximum_length FROM information_schema.columns
#   WHERE table_name='scdul_pymn_sumr_detl' ORDER BY ordinal_position;
```

> All commands are read-only inspection. No build/test is run as part of this audit. Implementation + `./gradlew test` / `npm run build` belong to the approved fix MR, not this audit.

---

## 12. Final Parity Verification (2026-06-29, report-only)

Read-only re-audit of the implemented fixes against legacy. **No code changed in this pass.**

### 12.1 Gap-by-gap parity confirmation

| Gap | Legacy reference | Refactor implementation | Verdict |
|---|---|---|---|
| **GAP-1** `CITAD_BANK_CD` | `PymnHelper.java:1360-1364` (`"000"` NAPAS / 3-char CITAD), `:1468` | `VendorPaymentService.resolveDetailBankCodes` → `"000"` / 3-char, wired in `doCreateDraft` detail loop | ✅ Resolved |
| **GAP-8** `TAR_BANK_CD` | `getBankCode :1232-1257` (CITAD → 6-digit head-office `BANK_CD` `bank_stas='1' AND bank_branch_div='1'`; NAPAS → as-is), `:1450-1451` | `resolveDetailBankCodes` via `findFirstByAntDivCdAndCitadBankCdAndBankStasAndBankBranchDiv` / typed NAPAS code | ✅ Resolved |
| **GAP-4** `WDRL_CCY_CD` | `comm_1001_001` `data-ccycd` = `ACCT_MSTR.CCY_CD`, persisted verbatim | `resolveWithdrawalCcyCd` looks up `ACCT_MSTR.CCY_CD` by selected `(BIZ_KEY, BANK_CD, ACCT_NO)`; transfer-ccy fallback only on miss | ✅ Resolved |
| **GAP-6** withdrawal picker | `comm_1001_001_act.jsp:31` (3 banks: `11704088/11704020/11704004`), `:51` (`AUTH_TYPE IN ('V','W','F')`) | `WITHDRAWAL_BANK_CDS` = 3 banks; `WithdrawalAccountListService` intersects V/W/F | ✅ Resolved |
| **GAP-3** VCB-USD cross-bank | `PymnHelper.java:118` (`VCB_BANK_CD="970436"`), `:1460-1465` | `VendorPaymentExcelParser` parse-time per-cell guard (`withdrawalIsVcb && USD && tar≠VCB`); dispatch-time `WBE2088` unchanged | ✅ Resolved |

VCB guard verified **live, not inert**: `napas_bank_info.bank_cd=11704004 → napas_bank_cd=970436`, so VCB `OUT_BANK_CD` and a NAPAS VCB target both resolve to `970436`.

### 12.2 Side-effect scan

- **Vendor-only logic changes:** `VendorPaymentService`, `VendorPaymentExcelParser` (+ new test). No change to Expense / Payroll / Overseas service or parser code.
- **Shared change (intentional, GAP-6):** `WithdrawalAccountConstants` + `WithdrawalAccountListService` are the module-agnostic `comm_1001_001` implementation shared by all five payment modules. The 3-bank + V/W/F correction applies uniformly — **intended legacy parity**, not an accidental side effect. Full payment + firm-banking suites pass; the other four modules inherit the corrected (broader, legacy-correct) list with no regressions.
- **Additive-only repo change:** new derived finder `findFirstByAntDivCdAndCitadBankCdAndBankStasAndBankBranchDiv`; no existing method altered.
- **New collaborator:** `VendorPaymentExcelParser` injects the existing shared `OutBankCdNormalizer` `@Component` — the normalizer itself is unchanged.
- **Out-of-scope pre-existing changeset:** the working tree also contains a separate **Internal Transfer Excel-upload** changeset (`internaltransfer/dto/*`, `InternalTransferExcelParser`, `InternalTransferService`, `InternalTransferExcelParserTest`, new `InternalTransferCreateDraftTest`, `internal-transfer-excel-upload-audit.md`). **NOT produced by this Vendor work**; its only shared-DTO contract change is a legacy-aligned validation tightening (`TransferLineInput` Description `@Size 255→200`). Vendor reuses these DTOs but did not author the change.

### 12.3 API contract

**No API contract change from this Vendor work.** No controller / DTO / endpoint / wire field added/removed/renamed by the Vendor fixes. GAP-1/8/4 reuse existing `ParsedTransferLine` / `TransferLineInput` fields; GAP-4 resolves server-side; GAP-6 is internal filter logic; GAP-3 emits the existing `err_fld` / `err_fld_caus` shape. No `wecms-web/` change required or made.

### 12.4 Database schema / migration

**None required.** All target columns (`WDRL_CCY_CD`, `TAR_BANK_CD`, `CITAD_BANK_CD`) and lookup tables (`TB_FIRM_BANK_CODE`, `NAPAS_BANK_INFO`, `ACCT_MSTR`, `ACCT_AUTH_INFO`) already exist in the baseline. No DDL, no Flyway migration, no `messages.properties` change (`WBE2088` pre-existed).

### 12.5 Test results

`./gradlew test --rerun` → **BUILD SUCCESSFUL**. **196 classes · 1412 tests · 0 failures · 0 errors** (20 skipped — pre-existing, unrelated). Parity locks exercised: `VendorPaymentExcelParserTest` (3/3, GAP-3), `WithdrawalAccountListServiceTest` (GAP-6, 3-bank + V/W/F), `InternalTransferExcelParserTest` (sibling, unaffected), full `vendorpayment.*` + `firmbankingsettings.*`.

### 12.6 Duplicated-rule / code-quality review

- **No duplicate validation within one path.** The VCB-USD rule is two intentional **layers** (parse-time preview guard + dispatch-time `WBE2088`), mirroring legacy's upload + transfer-`c001` double check.
- **No dead code / unreachable branches introduced.** `resolveDetailBankCodes` head-office-miss branch and `resolveWithdrawalCcyCd` fallback are both reachable, defensive, logged, and never persist null/empty.
- **Non-blocking observations (no action this pass):**
  1. **`VCB_BANK_CD = "970436"` duplicated** as private constants in both parsers + `@Value` defaults in dispatch executors (values consistent; could be lifted to one shared constant). Note `TransactionService.VCB_BANK_CD="11704004"` is the *internal* BANK_CD — a different semantic; do not merge. Pre-existing pattern.
  2. **No dedicated createDraft unit test for GAP-1/8/4 save-path** (`resolveDetailBankCodes` / `resolveWithdrawalCcyCd`); the IT side has `InternalTransferCreateDraftTest`. Recommended in a future MR. Deferred.
  3. **`WithdrawalAccountListService.java` tracked as binary by git** (pre-existing non-UTF-8/BOM artifact). Compiles cleanly; cosmetic only.

### 12.7 Risk assessment

| Area | Risk | Rationale |
|---|---|---|
| Vendor upload happy path (NAPAS/VND) | **Very low** | Unchanged; covered by existing + new tests |
| Vendor CITAD save (GAP-1/8) | **Low** | Mirrors legacy `getBankCode`; migration drift closed. Residual: no dedicated createDraft unit test (obs. #2) |
| Cross-module withdrawal picker (GAP-6) | **Low** | Shared but legacy-faithful + broader (not narrower); full suite green. Re-confirm in SIT that VCB / `F`-grant accounts surface |
| VCB-USD guard (GAP-3) | **Low** | Parse + dispatch layers; verified non-inert; UX-positive |
| API / DB | **None** | No contract, schema, or migration change from this work |

### 12.8 Final parity status

**PASS — Vendor Payment › Scheduled Payment › Excel Upload / Refresh is at legacy parity for all approved technical gaps (GAP-1, GAP-3, GAP-4, GAP-6, GAP-8).** Following the §14 re-audit, the remaining items are: **GAP-2** (conditional gap — live NAPAS recipient verify/persist, blocked on the bank-inquiry socket + a PO policy decision) and **GAP-7a** (optional UI-only difference — preview Recipient/Contact-Name columns). **GAP-5 and GAP-7b are not gaps** (legacy also blocks upload when `err_cnt > 0`, and legacy also silently caps at 500). No new features or behavior changes were introduced in this verification pass.

---

## 13. Template Compatibility Re-Verification (2026-06-29, report-only)

Triggered by a replacement of the shipped Vendor templates at
`wecms-web/public/templates/vendor-payment/WeCMS_Vendor_Payment_upload_{EN,KO,VI}.xlsx`.
Inspected the actual `.xlsx` XML (sheet1 + sharedStrings) of all three new files against `VendorPaymentExcelParser` expectations. **No code changed.**

| Check | Expected (parser) | New template (EN/KO/VI) | Result |
|---|---|---|---|
| Data sheet index | `getSheetAt(0)` | sheet 0 = "Vendor Payments"; sheet 1 = "NOTE" (bank-code reference) | ✅ |
| Header row | `HEADER_ROW=3` (POI, 0-based) | headers at XML `r="4"` (= POI row 3) | ✅ |
| Data start row | `FIRST_DATA_ROW=4` (POI) | sample data at XML `r="5"` (= POI row 4) | ✅ |
| Column count / order | 10: Bank Code · Deposit No. · Bank · Transfer Amount · Transfer Currency · Remarks · Reporting Account · **Contact Code** · Description · Document No. | identical 10 columns, same order (Contact Code at index 7) in all 3 locales | ✅ |
| EN headers | `HEADERS_EN` | exact match (`Bank Code *` … `Document No.`) | ✅ |
| KO headers | `HEADERS_KO` | exact match (`은행코드 *` … `문서번호`) | ✅ |
| VI headers | `HEADERS_VI` | exact match (`Mã ngân hàng *` … `Số chứng từ`) | ✅ |
| Required columns (`*`) | Bank Code, Deposit No., Transfer Amount, Transfer Currency, Contact Code | all present; none added / removed / renamed | ✅ |
| Header validation | `startsWith` EN ∥ KO ∥ VI per cell | passes (exact equality ⇒ prefix match trivially holds) | ✅ |

**Observation (not a compatibility break):** each new template ships one pre-filled **sample data row** at row 5 (`970424` / `700071152854` / `ShinhanBank` / `V001` / `VEHICLE FEE` / …). This is the legacy JEX template convention; the parser reads it as a normal data row, so on upload it validates against the uploading tenant (contact `V001`, bank `970424`, etc.) and is flagged or accepted accordingly — the user replaces it with real data. Layout rows 1 (title) and 3 (per-column descriptions) are correctly ignored by the parser. No parser change is warranted.

**Verification run:** `VendorPaymentExcelParserTest` → 3/3 pass (`tests=3 failures=0 errors=0`).

**Conclusion:** **No code change required — template replacement only. Vendor Payment Excel Upload is still fully compatible with the parser (headers, row positions, column count/order, and EN/KO/VI validation all match).** The §11/§12 final parity status is unchanged.

---

## 14. Remaining Gaps — Legacy Re-Audit (2026-06-29, report-only)

Focused re-read of the legacy source for the three deferred gaps. **No code changed.** The prior audit's claims were NOT assumed correct — each behavior was re-verified against exact legacy lines. Legacy files read: `pymn_0001_002_view.jsp`, `pymn_0001_002.ts`, `pymn_0001_002_r001_act.jsp`, `pymn_0001_002_c001_act.jsp`, `PymnHelper.java` (`readExcel`, `uploadPaymentExcel`, `ckRecName`, `getfincMdaOwnerNmFromAcctInfo`), `IDO.TB_FIRM_CONF_R002`.

### 14.0 Summary table

| Gap | Legacy behavior (cited) | Refactor behavior (cited) | Verdict | Recommendation |
|---|---|---|---|---|
| **GAP-2** NAPAS recipient verify / `RCPT_NM` | `ckRecName` runs a **live** bank inquiry when `TB_FIRM_CONF` config exists (`PymnHelper.java:874-876,964-965`); persists the bank-returned `FINC_MDA_OWNER_NM` into `RCPT_NM` (`:1011`); flags `WBE0001` on mismatch (`:1013,1016`) AND **flags `WBE0001` when config is missing** (`:1037-1038`). Save mode hard-rejects mismatch/blank with `WBE0404` (`:1023-1033`). | NAPAS inquiry wired (`VendorPaymentExcelParser.java:487-501`) but `firmBankingInquiryService.inquiryAccount` returns empty (socket unwired) → `.ifPresent` never fires → **no verify, no flag**; `RCPT_NM` left null for NAPAS (CITAD cross-fills from `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`, `:418,441-444`). | **Conditional gap** | Gated on `TB_FIRM_CONF` data + the bank inquiry socket (`WB_00000100`), neither available in R&D. Port the socket, then PO-decide the config-missing policy. Also fix the misleading in-code comment (see 14.1). |
| **GAP-5** partial upload of valid rows | Upload **only proceeds when `err_cnt <= 0`** — the save click is gated at `pymn_0001_002.ts:145`; with errors present the click is a no-op. `fnSaveVendorPayment` submits `IMPORTED_SUCCESS_DATA` (`:462`) but is only reachable at zero errors, so it equals "all rows". No live subset-upload path (the only one, `:154-162`, is **commented out**). A confirmation `WBC1183` is shown before upload (`:146`). | `uploadDisabled = … || errorCount > 0` (`PaymentExcelUploadDialog.tsx:250`) → Upload only when zero errors. `handleUpload` filters error-free lines but is unreachable while errors exist. | **Not a gap** | No action on the "partial upload" premise — refactor is functionally equivalent. Optional minor UX: legacy shows a `WBC1183` pre-upload confirmation; the refactor uploads directly. PO-optional. |
| **GAP-7a** preview columns | Preview shows **12 columns** incl. **Recipient Name** (`pymn_0001_002_view.jsp:167`, `m013`) and **Contact name** (`:174`, `m017`); both resolved server-side. | Preview shows **10 columns** (`VendorPaymentUploadDialog.tsx:122-133`) — no Recipient Name, no Contact name. The data (`rcpt_nm`, `trco_cmnm`) IS on the wire, just not displayed. | **UI-only difference** | Cosmetic; data already resolved. PO/design decision whether to add the two read-only preview columns. |
| **GAP-7b** 500-row cap | Silent cap at 500 (`PymnHelper.java:315-317` + `:326`): `if (rowCount > 500) rowCount = 500;` then `for i=4..rowCount` — **no error/warning**. | `MAX_ROWS = 500` (`VendorPaymentExcelParser.java:118`); `lastRow = min(getLastRowNum, FIRST_DATA_ROW+MAX_ROWS-1)` (`:205`) — also silent. | **Not a gap** | None. Both silently cap ~500. (Minor numeric nuance: legacy caps at absolute row index 500 ≈ 497 data rows; refactor caps 500 data rows. Immaterial; both silent.) |

### GAP-2 — NAPAS recipient verification / RCPT_NM

1. **Does legacy always call live bank recipient-name inquiry?** For NAPAS rows, yes — whenever a `TB_FIRM_CONF` config row exists for the tenant: `ckRecName` calls `FIRM_PROC.inquiryAccount(inputJSON)` (`PymnHelper.java:964-965`). Invoked from both the parse/preview path (`readExcel`, `:519`) and the save path (`uploadPaymentExcel`, `:1426`). CITAD uses `getfincMdaOwnerNmFromAcctInfo` instead (`:522,1067`), no live inquiry.
2. **Does legacy skip inquiry if firm-banking config/socket is missing?** **No.** When `TB_FIRM_CONF_R002` returns `null` (no config), legacy does **not** skip — it flags the row: `errors.put("RCPT_NM", new WbException(util, WbMessageCode.WBE0001, "Recipient Name error"))` (`:1037-1038`). (This contradicts the prior refactor code comment — see 14.1.)
3. **What exact function validates recipient name?** `PymnHelper.ckRecName(...)` (`:861-1040`) for NAPAS; `PymnHelper.getfincMdaOwnerNmFromAcctInfo(...)` (`:1067`) for CITAD.
4. **What exact DB/API source provides RCPT_NM?** Config from `TB_FIRM_CONF` via `IDO.TB_FIRM_CONF_R002` (`:874-876`); the recipient name itself from the **bank API** `FIRM_PROC.inquiryAccount` response field `FINC_MDA_OWNER_NM` (`:965,975`); the local comparison name from `getfincMdaOwnerNm` over `TRCO_ACCT_INFO` (`:967`).
5. **Is RCPT_NM required before save?** Effectively yes — in save mode (`read=false`) a blank/mismatched bank name throws `WBE0404 "Recipient Name"` and rolls back (`:1023-1033`).
6. **What value is saved for NAPAS RCPT_NM?** The bank-returned `FINC_MDA_OWNER_NM` (set into `recItem.RCPT_NM` at `:1011`), only when it matches the locally-stored owner name.
7. **Does refactor match, partially match, or differ?** **Differs (conditionally).** The refactor wires the NAPAS inquiry (`VendorPaymentExcelParser.java:487-501`) but the socket returns empty, so it never verifies, never flags, and leaves `RCPT_NM` null for NAPAS. It does not reproduce legacy's config-missing flag either. CITAD cross-fill matches legacy.
8. **Final verdict:** **Conditional gap** — behavior depends on `TB_FIRM_CONF` data and the bank inquiry socket (`WB_00000100`), neither available in R&D. Needs the socket port + a PO decision on the config-missing policy before parity can be claimed.

### GAP-5 — Partial upload of valid rows

1. **When some rows error and some are valid, is Upload enabled?** The button can appear **enabled** on the "Completed" tab even with errors (`pymn_0001_002.ts:386-392`, `fnShowSuccessData:522`), but the actual save click is gated: `if (err_cnt <= 0) { … fnSaveVendorPayment() }` (`:145-151`) — with errors present, clicking is a **no-op**.
2. **Does legacy upload only valid rows or all rows?** It submits `IMPORTED_SUCCESS_DATA` (error-free rows, `:462`), but that path is only reachable when `err_cnt === 0`, at which point all rows are valid — so in practice it uploads **all rows, only when there are zero errors**.
3. **Controlled by tab / completed count / error count / selected rows?** By **error count** at the click gate (`:145`); the tab toggles only affect the button's visual enabled state, not the actual save condition.
4. **What happens to invalid rows?** They block the upload — the user must fix/remove them until `err_cnt === 0`. No subset is ever submitted (the subset-upload path at `:154-162` is commented out).
5. **Does legacy show a confirmation before uploading?** Yes — `WBC1183` confirmation dialog precedes `fnSaveVendorPayment` (`:146`).
6. **Does refactor match, partially match, or differ?** **Matches** functionally — `uploadDisabled = errorCount > 0` (`PaymentExcelUploadDialog.tsx:250`) means upload only when zero errors, same net rule.
7. **Final verdict:** **Not a gap.** The prior audit's "legacy allows uploading only valid rows when errors exist" was **incorrect** (it read the button-enable logic without the `err_cnt <= 0` click gate). Only minor UX nuance remains: legacy shows a `WBC1183` pre-upload confirmation the refactor lacks — PO-optional, not a behavior gap.

### GAP-7 — Preview columns / 500-row cap

1. **Exact columns in legacy preview?** 12, in order (`pymn_0001_002_view.jsp:165-176`): Bank Code · Deposit No. · **Recipient Name** · Bank · Transfer amount · Transfer Currency · Remarks (200) · Reporting Account · Contact Code · **Contact name** · Description · Document No.
2. **Does legacy display resolved Recipient Name?** Yes — column 3 (`m013`), populated from the bank inquiry (NAPAS, `:1011`) / `TRCO_ACCT_INFO` (CITAD, `:562,637`).
3. **Does legacy display resolved Contact Name?** Yes — column 10 (`m017` = `TRCO_CMNM`), populated by `ckContactCode` (`:1217-1218`).
4. **Does legacy have a max upload row cap?** Yes.
5. **Exact limit + error behavior?** **500**, silent truncation — `if (rowCount > 500) rowCount = 500;` (`PymnHelper.java:315-317`, mirrored `:661-663`); rows beyond 500 are simply not read. No error, no warning.
6. **Does refactor match, partially match, or differ?** **Preview columns differ** — refactor shows 10 columns (`VendorPaymentUploadDialog.tsx:122-133`), omitting Recipient Name + Contact name (though `rcpt_nm` / `trco_cmnm` are on the wire). **500-cap matches** (`VendorPaymentExcelParser.java:118,205`), also silent.
7. **Final verdict:** Preview columns → **UI-only difference** (data resolved server-side; display-only). 500-row cap → **Not a gap** (exact silent-cap parity).

### 14.1 Documentation defect found (no behavior change)

`VendorPaymentExcelParser.java` (NAPAS recipient block, ~`:461-466` comments) states the inquiry no-op is *"matching legacy's 'no firm-banking config' silent-skip (PymnHelper.ckRecName line 1037 'contactInfo == null')."* The actual legacy at `:1037-1038` does **not** silent-skip — it flags `WBE0001 "Recipient Name error"`. The comment misdescribes legacy and should be corrected when GAP-2 is next touched. Report-only; not fixed in this pass.

### 14.2 Recommended next actions

- **GAP-2 — Conditional gap.** Defer until the firm-banking inquiry socket (`WB_00000100`) is ported. Then bring to PO: (a) reproduce legacy's live verify + `RCPT_NM` persistence for NAPAS; (b) decide the config-missing policy (legacy flags every NAPAS row — likely undesirable in R&D/coexistence, so a PO call is required, not a blind port). Correct the misleading comment (14.1) in the same change.
- **GAP-5 — Not a gap.** No implementation needed. Optionally add a pre-upload confirmation dialog (`WBC1183` equivalent) for full UX parity — PO-optional.
- **GAP-7a — UI-only difference.** Design/PO decision on whether to add the two read-only preview columns (Recipient Name, Contact name). Low effort (web-only; data already on the wire).
- **GAP-7b — Not a gap.** No action.

**Net:** of the three "remaining" gaps, **GAP-5 and GAP-7b are not gaps** (prior audit overstated them), **GAP-7a is a cosmetic UI-only difference**, and **GAP-2 is a conditional gap** blocked on the bank socket + a PO policy decision. No code changed in this re-audit.

### 14.3 Correction note (supersedes earlier §1/§8/§9/§10 wording)

The §14 re-read corrected four claims from the initial audit pass. Where any earlier section disagrees, **§14 is authoritative**:

1. **Prior audit overstated GAP-5 and GAP-7b** — both are now classified **Not a gap**, not deferred MEDIUM/LOW gaps.
2. **Legacy also blocks upload when `err_cnt > 0`** — the save click is gated at `pymn_0001_002.ts:145`; legacy never uploads a subset of valid rows while errors exist (the subset path at `:154-162` is commented out). The refactor's all-or-nothing gate is functionally equivalent. (Corrects: "legacy uploads valid rows when error rows exist" / "GAP-5 is a business/UX gap".)
3. **Legacy also caps Excel parsing at 500 rows, silently** — `PymnHelper.java:315-317,326`. The 500-row cap is **not** a refactor-only issue. (Corrects: "refactor adds a hard 500-row cap / silent truncation past 500 not surfaced".)
4. **Legacy does NOT silently skip the NAPAS inquiry when config is missing** — it flags `WBE0001 "Recipient Name error"` (`PymnHelper.java:1037-1038`). GAP-2 remains **conditional** only because the live bank inquiry requires the firm-banking socket (`WB_00000100`) + `TB_FIRM_CONF` config, which are unavailable in R&D — and the config-missing policy needs a PO decision. (Corrects: "legacy silently skips NAPAS inquiry when config is missing"; the misleading in-code comment is noted in §14.1.)

No code was modified in this re-audit or in applying these documentation corrections.

---

## 15. GAP-2 — Firm Banking Inquiry Call-Chain Trace (2026-06-29, report-only)

Goal: determine whether `FirmBankingInquiryService.inquiryAccount(...)` is connected to a live bank inquiry or still stubbed/disabled. **No code changed.**

### 15.1 Full call chain (refactor)

```
VendorPaymentExcelParser.parseRow                       VendorPaymentExcelParser.java:487-501
  guard: NAPAS && resolvedBankCd && fincMdaNo && contactOwnerNmForCheck != null
  → FirmBankingInquiryService.inquiryAccount(napasBankCd=resolvedBankCd, acctNo=fincMdaNo)
                                                        common/firmbanking/FirmBankingInquiryService.java:141
      ├─ [GATE 1] @Value("${wecms.dispatch.recipient-inquiry:false}")  → if false: return Optional.empty()   (:152-156)
      ├─ [GATE 2] lookupFirmConfig() → FirmBankingSettingRepository.findFirstByBizKeyAndOrgStatOrderByRegDttmAsc(bizKey,"1")
      │            (TB_FIRM_CONF, ORG_STAT='1') → if empty: return Optional.empty()                           (:162-166)
      ├─ build RecipientLookupCommand from the TB_FIRM_CONF row (VAN_ORG_CD, OUT_BANK_CD, BANK_PRTN_ID, WDRL_ACT_NO) (:178-185)
      └─ DepositorInquiryService.inquire(bizKey, cmd)   domain/payment/dispatch/recipient/DepositorInquiryService.java:68
            ├─ allocate TRAN_SEQ (LegacyFunctionRepository.nextTbFirmTranSeq, global firm_seq_no)            (:78)
            ├─ TB_FIRM_TRAN C001 audit row @ PROC_STAT='6'                                                    (:79)
            ├─ RecipientLookupService.lookup(cmd)        domain/payment/dispatch/recipient/RecipientLookupService.java:75
            │     ├─ build Efs201Request                                                                       (:82)
            │     ├─ EfsApiClient.send(EFS_201, requestEnvelope)  → REST POST to the EFS/VAN gateway           (:98)
            │     │     (base-url = wecms.dispatch.efs.base-url; WGVN_EFS_201 = REST port of legacy WB_00000100 socket)
            │     ├─ on RC≠0000 → BusinessException WBE_EFS_GATEWAY_ERROR                                       (:103-105)
            │     └─ on RC=0000 → decrypt + VV-verify → Efs201Response.ownerName + antDivCd
            └─ TB_FIRM_TRAN U002 audit (SUCC/FAIL) → return Optional<String> ownerName                         (:90-117)
  → .ifPresent(bankOwnerNm -> if !equalsIgnoreCase(contactOwnerNm) add FieldError("RCPT_NM", "…mismatch…"))   (:495-501)
```

### 15.2 Outbound target + config

| Item | Finding |
|---|---|
| Implementation class | `FirmBankingInquiryService` (concrete `@Service`, **not** a stub) → `DepositorInquiryService` → `RecipientLookupService` → `EfsApiClient` |
| Final outbound target | **REST EFS / VAN gateway** (`EfsApiClient.send(EFS_201, …)`), endpoint `wecms.dispatch.efs.base-url`. WGVN_EFS_201 is the REST refactor of the legacy `WB_00000100` fixed-width socket — the legacy `127.0.0.1:11000 socket/wecms_batch` protocol is deliberately **not** ported. |
| Calls WeCMS Batch socket directly? | **No** — REST gateway, not the legacy socket. |
| Master gate | `wecms.dispatch.recipient-inquiry` — Java default `false` (`FirmBankingInquiryService.java:107-108`); wired to env `WECMS_DISPATCH_RECIPIENT_INQUIRY` in `application-local/sit/prod/uat.yml`. |
| Gateway enable | `wecms.dispatch.efs.enabled` = `${WECMS_EFS_ENABLED}`; `…efs.base-url` = `${WECMS_EFS_BASE_URL}`; `…efs.partner-id` = `${WECMS_EFS_PARTNER_ID}`. |
| Per-tenant gate | active `TB_FIRM_CONF` row (`ORG_STAT='1'`) required. |
| VAN keys | EFS API keys (provisioning item "B2") required for the crypto/VV path. |
| Why empty today | In R&D the env vars are unset → master gate `false` → returns `Optional.empty()` **before any lookup**. Even if enabled, requires an active `TB_FIRM_CONF` row + a provisioned EFS gateway + VAN keys. |

### 15.3 Legacy comparison (`PymnHelper.ckRecName`, `WB_00000100`, `TB_FIRM_CONF`)

| Aspect | Legacy | Refactor |
|---|---|---|
| Config gate | `TB_FIRM_CONF_R002` by BIZ_KEY (`PymnHelper.java:874-876`) | `TB_FIRM_CONF` via `FirmBankingSettingRepository` (GATE 2) — **matches** |
| Bank call | `FIRM_PROC.inquiryAccount` → `WB_00000100` socket, `127.0.0.1:11000` (`:964-965`) | `EfsApiClient` REST `WGVN_EFS_201` — **functionally equivalent, different transport (REST vs socket)** |
| When config exists | live inquiry; on match persists bank name to `RCPT_NM` (`:1011`); on mismatch flags `WBE0001` (`:1013,1016`); save mode rejects `WBE0404` (`:1023-1033`) | live inquiry **only if master gate ON**; on mismatch flags `FieldError("RCPT_NM")`; **does not persist** the bank name to `RCPT_NM` for NAPAS (see 15.4) |
| When config missing | flags `WBE0001 "Recipient Name error"` (`:1037-1038`) — does **not** silently skip | returns empty → **no flag** (silent skip). **Divergence** (also misdescribed by the in-code comment — §14.1) |
| Value saved to RCPT_NM (NAPAS) | bank-returned `FINC_MDA_OWNER_NM` (`:1011`) | **null** — NAPAS never persists it; CITAD cross-fills from `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` (`VendorPaymentExcelParser.java:441-444`) |

### 15.4 Secondary finding — NAPAS `RCPT_NM` persistence is a code gap, independent of the socket

The Vendor parser's NAPAS block (`VendorPaymentExcelParser.java:495-501`) consumes the inquiry result **only to flag a mismatch** — it never assigns `resolvedRcptNm = bankOwnerNm`. So **even once the socket is enabled and returns a name, the refactor will not persist `RCPT_NM` for NAPAS rows**, whereas legacy persists it (`PymnHelper.java:1011`). This is a real (latent) parser code gap that does not depend on the gateway; it surfaces the moment GAP-2 is switched on. Record only — do not fix in this pass.

### 15.5 GAP-2 answers

- **Is the refactor call path implemented?** Yes — full chain `FirmBankingInquiryService → DepositorInquiryService → RecipientLookupService → EfsApiClient`, with `TB_FIRM_TRAN` audit. Not a stub/placeholder.
- **Connected to WeCMS Batch?** No — it is the **REST EFS/VAN gateway** (WGVN_EFS_201), the refactor of the legacy `WB_00000100` batch socket.
- **Connected to live bank inquiry?** Wired to one, but **not active** — gated off by config and unprovisioned in R&D.
- **Why empty today?** Master gate `wecms.dispatch.recipient-inquiry` is `false` (env unset); additionally needs active `TB_FIRM_CONF` + EFS gateway URL + VAN keys.
- **Bug / integration gap / config / PO decision?** Not a Vendor-Payment code bug for the inquiry path itself — it is a **Firm-Banking integration + configuration** matter, plus (a) the latent NAPAS `RCPT_NM` persistence code gap (15.4) and (b) a **PO policy decision** on the config-missing behavior (legacy flags every NAPAS row; likely undesirable for coexistence).

### 15.6 GAP-2 final verdict

**Implemented but disabled by config (and external gateway unavailable).** The call path is real and complete down to the REST EFS gateway; it returns `Optional.empty()` because `wecms.dispatch.recipient-inquiry=false` (and EFS/VAN not provisioned) in R&D. Enabling true legacy parity requires: (1) provision the EFS gateway + VAN keys and flip the gate (Firm-Banking integration, out of Vendor-Payment scope); (2) implement NAPAS `RCPT_NM` persistence in the parser (15.4); (3) a **PO policy decision** on config-missing behavior. **Remains a conditional gap — deferred. No code change in this audit.**

---

## 16. Final UI Parity Verification — GAP-7a (2026-06-29, report-only)

UI-only verification of the Excel-upload **preview** table. **No code changed.**

- Legacy: `pymn_0001_002_view.jsp:147-190` (preview `#TABLE_FORM`), `pymn_0001_002.ts:283-366` (row render + error cells).
- Refactor: `VendorPaymentUploadDialog.tsx:122-133` (column config) + `:232-261` (cell mapping); shared shell `components/shared/excel-upload/ExcelUploadDialog.tsx`; preview wire `lib/types/api.ts ApiParsedTransferLine:2210-2232`.

### 16.1 Preview column comparison

| # | Legacy Column (`pymn_0001_002_view.jsp`) | Refactor Column (`VendorPaymentUploadDialog.tsx`) | Match |
|---|---|---|---|
| 1 | Bank Code (`m035`) | `ANT_DIV_CD` — Bank Code | ✅ |
| 2 | Deposit No. (`m012`) | `FINC_MDA_NO` — Deposit No. | ✅ |
| 3 | **Recipient Name (`m013`)** | — *(not rendered)* | ❌ (data on wire: `rcpt_nm`) |
| 4 | Bank (`m011`) | `TAR_BANK_NM` — Bank | ✅ |
| 5 | Transfer amount (`m014`) | `TRAN_AMT` | ✅ |
| 6 | Transfer Currency (`m036`) | `TRNF_CCY_CD` | ✅ |
| 7 | Remarks (200 letters) (`m015`) | `TRAN_USAG` | ✅ |
| 8 | Reporting Account (`m031`) | `CHART_ACCT_CD` | ✅ |
| 9 | Contact Code (`m016`) | `HAND_TRCO_KEY` | ✅ |
| 10 | **Contact name (`m017`)** | — *(not rendered)* | ❌ (data on wire: `trco_cmnm`) |
| 11 | Description (`m018`) | `NOTE` | ✅ |
| 12 | Document No. (`m019`) | `DOC_NO` | ✅ |

10 of 12 columns match. The two missing columns are **Recipient Name** (legacy position 3) and **Contact name** (legacy position 10). Column **order** otherwise matches; to restore parity, Recipient Name inserts between Deposit No. and Bank, and Contact name between Contact Code and Description.

### 16.2 Recipient Name

- Displayed in legacy? **Yes** — column 3 (`m013`), populated from the bank inquiry (NAPAS, `PymnHelper.java:1011`) / `TRCO_ACCT_INFO` (CITAD).
- Available from the parser? **Yes** — `ParsedTransferLine.rcptNm` (CITAD cross-fill today; NAPAS pending GAP-2).
- Returned by the API? **Yes** — `ApiParsedTransferLine.rcpt_nm` (`lib/types/api.ts:2213`).
- Only hidden by the UI? **Yes** — the wire field exists; the `columns` array simply omits it.

### 16.3 Contact Name

- Displayed in legacy? **Yes** — column 10 (`m017` = `TRCO_CMNM`), populated by `ckContactCode` (`PymnHelper.java:1217-1218`).
- Available from the parser? **Yes** — `ParsedTransferLine.trcoCmnm` (cross-filled on contact-code resolution for both NAPAS + CITAD).
- Returned by the API? **Yes** — `ApiParsedTransferLine.trco_cmnm` (`lib/types/api.ts:2226`; `trco_cmenm` at `:2227`).
- Only hidden by the UI? **Yes** — wire field present; not in the `columns` array.

### 16.4 Column position (exact legacy order)

`pymn_0001_002_view.jsp:165-176`: Bank Code · Deposit No. · **Recipient Name** · Bank · Transfer amount · Transfer Currency · Remarks (200) · Reporting Account · Contact Code · **Contact name** · Description · Document No.

### 16.5 Error display

| Behaviour | Legacy | Refactor | Match |
|---|---|---|---|
| Red error cell | `.err_click` class, bg `#f7ddde` (`pymn_0001_002.ts:309`) | per-cell `error` tint in shared `ExcelUploadDialog` | ✅ (equivalent) |
| Tooltip on hover | custom tooltip with `ERR_FLD_CAUS` message (`:312-363`) | `errorMessage` tooltip on cell (`VendorPaymentUploadDialog.tsx:241-245`) | ✅ |
| Error message source | `ERR_FLD` / `ERR_FLD_CAUS` pipe arrays | `err_fld` / `err_fld_caus` pipe arrays | ✅ |
| Completed / Error counters | `#clearData` / `#errData` (`:368-370`) | `completedCount` / `errorCount` chips | ✅ |
| Row split tabs (All / Completed / Error) | 3 tbodies, tab toggle (`:178-188`) | single combined preview (no tabs) | ⚠️ minor UI difference (out of GAP-7a scope) |

### 16.6 Hidden data confirmation

Recipient Name (`rcpt_nm`) and Contact Name (`trco_cmnm`) are **already present in the preview payload** (`ApiParsedTransferLine`) and only need rendering. Therefore, to add them:

> **No backend change required. No API change required. No parser change required. UI rendering only.**

(Caveat: `rcpt_nm` is currently populated for CITAD rows only; NAPAS values await GAP-2's inquiry being enabled. `trco_cmnm` is populated for all valid rows. Rendering the columns is still correct now — NAPAS Recipient Name would simply show blank until GAP-2 is on, matching the data state.)

### 16.7 GAP-7a final verdict

**UI-only enhancement.** Legacy displays 12 preview columns; the refactor renders 10, omitting Recipient Name and Contact name — both of which are already resolved server-side and carried on the preview wire (`ApiParsedTransferLine.rcpt_nm` / `trco_cmnm`). Restoring parity is a pure `wecms-web` rendering change (add the two columns at legacy positions 3 and 10); no backend, API, parser, DB, or template change. It is **not** a parity bug in data, and legacy does not intentionally differ — the refactor simply hasn't rendered the two read-only columns yet. Recommend a design/PO sign-off, then a web-only change. **Deferred per instruction — no code change in this audit.**

### 16.8 Recipient Name source by network: NAPAS vs CITAD

Focused re-read of the legacy `RCPT_NM` source for each network, on both the parse/preview (`r001`) and save (`c001`) paths, with refactor comparison. **No code changed.**

**Legacy dispatch (parse/preview, `readExcel`):** `PymnHelper.java:516-567` — the network is decided by the resolved `ANT_DIV_CD` (`cellValue_`): NAPAS → `ckRecName` (`:519`); CITAD → `getfincMdaOwnerNmFromAcctInfo` (`:522`).

| Aspect | NAPAS | CITAD |
|---|---|---|
| Live recipient inquiry? | **Yes** — `ckRecName` (`PymnHelper.java:519,861`) calls `FIRM_PROC.inquiryAccount` → `WB_00000100` socket (`:964-965`) | **No** — DB read only |
| Function | `ckRecName(...)` (`:861-1040`) | `getfincMdaOwnerNmFromAcctInfo(...)` (`:1067-1092`) |
| Uses `TB_FIRM_CONF`? | **Yes** — `TB_FIRM_CONF_R002` by BIZ_KEY (`:874-876`) | No |
| Source table / column | bank API response field `FINC_MDA_OWNER_NM` (compared vs local `getfincMdaOwnerNm`, `:967`) | `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` by `(BIZ_KEY, TRCO_KEY, ACCT_NO=Deposit No.)` (`:1075-1090`) |
| If config / owner missing | `TB_FIRM_CONF` null → flags `WBE0001 "Recipient Name error"` (`:1037-1038`) | owner-name blank → flags `WBE0001 "Recipient Name error"` (`:564-565`) |
| Preview `Recipient Name` value | bank-returned `FINC_MDA_OWNER_NM` (`:1011`) | `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` (`:562`) |
| Saved `SCDUL_PYMN_SUMR_DETL.RCPT_NM` | recItem `RCPT_NM` carried into save via `copyFrom(recItem)` (`:1448`); re-validated at save by `ckRecName(read=false)` → `WBE0404` on mismatch (`:1426,1023-1033`) | recItem `RCPT_NM` via `copyFrom` (`:1448`); re-derived + re-checked by `getfincMdaOwnerNmFromAcctInfo` → `WBE0404` if blank (`:1429-1434`) |
| Compares vs Excel/contact name? | Yes — bank name vs local stored owner (`getfincMdaOwnerNm`), mismatch → `WBE0001`/`WBE0404` | Validated against the contact's registered deposit account (`ckDepositNo` → `TRCO_ACCT_INFO_R004`, `:1139-1157`); the owner name itself is not cross-compared |

**Legacy answers — NAPAS:** (1) yes, live inquiry; (2) `ckRecName`; (3) yes, `TB_FIRM_CONF`; (4) missing config → `WBE0001` row flag (not skip); (5) preview shows the bank-returned name; (6) saves the bank-returned name; (7) yes, compares vs local owner; (8) mismatch/lookup-fail → `WBE0001` at preview, `WBE0404` at save.

**Legacy answers — CITAD:** (1) no live inquiry; (2) reads the DB account-owner name; (3) `getfincMdaOwnerNmFromAcctInfo`; (4) `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` keyed by `(BIZ_KEY, TRCO_KEY, ACCT_NO)`; (5)/(6) preview + save use `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`; (7) validates Deposit No. against the contact (`ckDepositNo`); (8) blank owner → `WBE0001` (preview) / `WBE0404` (save).

**Refactor comparison** (`VendorPaymentExcelParser.java`, `VendorPaymentService.java`, `ParsedTransferLine`, `ApiParsedTransferLine`):

| Aspect | Legacy | Refactor | Match |
|---|---|---|---|
| CITAD source | `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` (`:1067-1090`) | `match.getFincMdaOwnerNm()` (matched `TRCO_ACCT_INFO` row) → `resolvedRcptNm` (`VendorPaymentExcelParser.java:462-464`), set on the line (`:608`), persisted `detl.setRcptNm(line.rcptNm())` (`VendorPaymentService.java:525`) | ✅ matches |
| CITAD blank-owner handling | flags `WBE0001` (`:564-565`) | no flag — `resolvedRcptNm` stays null, no `FieldError` added | ⚠️ minor divergence |
| NAPAS inquiry | live `ckRecName` (`:519`) | wired `firmBankingInquiryService.inquiryAccount(...)` but master-gated off (`recipient-inquiry=false`) → empty (§15) | 🔶 conditional (gated off) |
| NAPAS `RCPT_NM` persistence | bank-returned name persisted (`:1011`) | **not persisted** — block only flags a mismatch (`:495-501`), never assigns `resolvedRcptNm` for NAPAS (§15.4) | ❌ latent code gap |
| Preview wire fields | n/a | `ApiParsedTransferLine.rcpt_nm` (`lib/types/api.ts:2213`) + `trco_cmnm` (`:2226`) already present | ✅ data on wire |

**Refactor answers:**
1. **Match NAPAS?** Partially — inquiry is wired but gated off (§15), and even when enabled the parser does not persist the bank name to `RCPT_NM` (latent gap §15.4). NAPAS does **not** fully match yet.
2. **Match CITAD?** **Yes** — refactor reads `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` and persists it, same as legacy. (Minor nuance: refactor does not flag a blank CITAD owner with `WBE0001`.)
3. **`rcpt_nm` already populated for CITAD?** **Yes** (`VendorPaymentExcelParser.java:462-464` → `:608` → wire `rcpt_nm`).
4. **`rcpt_nm` empty for NAPAS only because inquiry is disabled?** Mostly — primary cause is the master gate (`recipient-inquiry=false`); **plus** the secondary code gap (§15.4): even with the inquiry on, the NAPAS branch only flags a mismatch and never assigns `RCPT_NM`. Both must be addressed for NAPAS parity.
5. **GAP-7a safe before GAP-2?** **Yes.** Recipient Name renders correctly for CITAD rows now; NAPAS rows show blank until GAP-2 is enabled (and §15.4 persistence added) — mirroring the current data state. Contact name (`trco_cmnm`) renders for all valid rows regardless of network.

**Final verdict (16.8):**
- **NAPAS** = live recipient inquiry via `ckRecName` → `WB_00000100` (REST `WGVN_EFS_201` in the refactor), gated on `TB_FIRM_CONF` + the inquiry config → **conditional** (refactor gated off + latent persistence gap §15.4).
- **CITAD** = DB read of the contact's registered account-owner name (`TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`) → refactor **matches** legacy.
- **GAP-7a UI**: the Recipient Name column **can be added now** (web-only); it populates for CITAD immediately and stays **blank for NAPAS until GAP-2 is enabled** (plus the §15.4 NAPAS-persistence fix). No backend/API/parser/DB/template change required to render it.

---

## 17. GAP-7a — IMPLEMENTED (2026-06-29, web-only)

GAP-7a is now **fixed**. The Excel-upload preview renders the two legacy columns that were missing; both read display-only values already present on the parse wire.

### 17.1 What changed (wecms-web only)

| File | Change |
|---|---|
| `wecms-web/components/payment/vendor-payment/VendorPaymentUploadDialog.tsx` | `columns`: 10 → **12** — added `RCPT_NM` (Recipient Name, after Deposit No., 180px) and `TRCO_CMNM` (Contact Name, after Contact Code, 150px), preserving legacy order. `toUploadRow.cells`: added `RCPT_NM ← line.rcpt_nm` and `TRCO_CMNM ← line.trco_cmnm`, both routed through the existing `cell()` helper so red-cell + tooltip behavior is preserved (a parser `RCPT_NM` error highlights the new cell, matching legacy). |
| `wecms-web/locales/en.json` · `ko.json` · `vi.json` | Added `vendor_payment.upload.col.recipient_name` + `vendor_payment.upload.col.contact_name` with verified translations (EN "Recipient Name"/"Contact Name"; KO "수취인명"/"거래처명"; VI "Tên người nhận"/"Tên liên hệ"). |

Final preview column order (matches legacy `pymn_0001_002_view.jsp:165-176`): Bank Code · Deposit No. · **Recipient Name** · Bank · Transfer Amount · Transfer Currency · Remarks · Reporting Account · Contact Code · **Contact Name** · Description · Document No.

### 17.2 Scope guardrails honored

- **Web-only.** No change to `wecms-api`, parser, DTO, validation, save logic, DB, templates, or the API contract. Both new columns read fields **already** on `ApiParsedTransferLine` (`rcpt_nm`, `trco_cmnm`).
- Upload **payload** unchanged (`parsedToWire` / `createVendorPayment` untouched); upload **button** logic unchanged.
- The 10 original template columns are intact and unmodified.
- No effect on Payroll / Expense / Internal Transfer / Overseas — the change is confined to `VendorPaymentUploadDialog.tsx` (its own column config; it does not modify the shared `PaymentExcelUploadDialog` or `ExcelUploadDialog`).
- **GAP-2 untouched.** NAPAS `rcpt_nm` is not populated by this change; NAPAS Recipient Name renders **blank** until GAP-2 (firm-banking recipient inquiry + §15.4 persistence) is separately enabled — expected. CITAD Recipient Name populates today; Contact Name populates for all valid rows.

### 17.3 Verification

- `npx tsc --noEmit` → **exit 0** (type-check clean).
- `npm run lint` → the changed files (`VendorPaymentUploadDialog.tsx`, the three locale JSONs) are **lint-clean**; all reported lint problems are pre-existing in unrelated files (`LocaleProvider.tsx`, `lib/nxisas/*`, `public/assets/settings/_write_icons.js`).
- All three locale files validate as JSON.

**GAP-7a status: ✅ FIXED (web-only).** Remaining open item for this feature is **GAP-2** only (conditional — NAPAS live inquiry gated by config/EFS/VAN; NAPAS `rcpt_nm` stays blank until GAP-2 is implemented/enabled).

---

## 18. GAP-2 — Completability Investigation (2026-06-29, report-only)

Final determination of whether GAP-2 can be completed in the current environment. **No code changed.**

### 18.1 Environment configuration (all profiles)

| Key | local (`.env.local`) | sit (`.env.sit`) | uat (`.env.uat.example`) | prod (`.env.prod.example`) |
|---|---|---|---|---|
| `WECMS_DISPATCH_RECIPIENT_INQUIRY` | `false` | `false` | `false` | `false` |
| `WECMS_EFS_ENABLED` | `false` | `false` | `false` | `false` |
| `WECMS_EFS_BASE_URL` | `https://dev-van-gw.webcashvietnam.com` | `https://dev-van-gw.webcashvietnam.com` | *(empty)* | *(empty)* |
| `WECMS_EFS_PARTNER_ID` | *(empty)* | *(empty)* | *(empty)* | *(empty)* |
| VAN API key | from `TB_VAN_API_INFO` (B2), DB-provisioned per VAN — **not present in R&D** | same | same | same |
| `TB_FIRM_CONF` (active `ORG_STAT='1'`) | not seeded for R&D tenants | unverified / not seeded | env-owned | env-owned |

Java defaults: `@Value("${wecms.dispatch.recipient-inquiry:false}")` and `EfsProperties.enabled=false`. **The recipient inquiry is OFF in every environment.**

### 18.2 EFS / WGVN endpoint availability

| Env | EFS reachable for inquiry? |
|---|---|
| Local | **No** — `EFS_ENABLED=false`, no partner-id, no VAN key. Base-url is set to the DEV gateway but the client is disabled (`MockSftpClient`/mock path active). |
| DEV | No dedicated profile; local config targets `dev-van-gw.webcashvietnam.com` but disabled. |
| SIT | **No** — `EFS_ENABLED=false`, no partner-id, no VAN key. |
| UAT | **No** — `EFS_ENABLED=false`, base-url empty. |
| PROD | **No** — `EFS_ENABLED=false`, base-url empty. |

The EFS / VAN gateway (`*-van-gw.webcashvietnam.com`) is an **external system** (Webcash Vietnam / Coocon VAN center). It is not enabled or fully provisioned (partner-id + `TB_VAN_API_INFO` key) in any shipped configuration.

### 18.3 Post-inquiry code path — does a successful inquiry populate `RCPT_NM`?

Traced the chain assuming inquiry enabled + EFS reachable + `TB_FIRM_CONF` present:

| Stage | NAPAS | CITAD |
|---|---|---|
| Parser resolve | inquiry result consumed **only to flag a mismatch**; `resolvedRcptNm` **never assigned** | `resolvedRcptNm = match.getFincMdaOwnerNm()` (`VendorPaymentExcelParser.java:462-464`) |
| Line build (`:605-608`) | `resolvedRcptNm` (= **null** for NAPAS) → `ParsedTransferLine.rcptNm` | `resolvedRcptNm` → `rcptNm` ✅ |
| Wire DTO | `ApiParsedTransferLine.rcpt_nm` (null for NAPAS) | populated ✅ |
| Service persist | `VendorPaymentService.java:525` `detl.setRcptNm(line.rcptNm())` | persists ✅ |
| Entity | `ScdulPymnSumrDetl.RCPT_NM` | ✅ |
| Preview (GAP-7a) | Recipient Name cell renders blank | renders the name ✅ |

**Missing assignment — exact location:** `VendorPaymentExcelParser.java:493-503`. The NAPAS block:

```java
firmBankingInquiryService.inquiryAccount(resolvedBankCd, fincMdaNo)
    .ifPresent(bankOwnerNm -> {
        String fromBank = bankOwnerNm.trim();
        if (!fromBank.isEmpty() && !fromBank.equalsIgnoreCase(contactOwnerNm)) {
            errors.add(new FieldError("RCPT_NM", "Recipient name does not match the bank's record: " + fromBank));
        }
    });
```

It compares the bank-returned name but **never assigns** it to `resolvedRcptNm`, whereas legacy persists it (`PymnHelper.java:1011 recItem.put("RCPT_NM", rcptNm)`). The whole downstream chain (DTO → service → entity → preview) already handles `rcpt_nm` correctly (proven by the working CITAD path); the **only** missing link for NAPAS is this assignment. Closing it requires a small parser change (capture the `Optional` result outside the lambda — `resolvedRcptNm` cannot be assigned inside the lambda because it must be effectively final — then set `resolvedRcptNm` on success, subject to the same special-char validation the CITAD path already runs at `:596-599`).

### 18.4 Final conclusion

GAP-2 has **two** blockers, so it cannot be closed by configuration alone:

1. **External infrastructure (primary).** The live depositor-name inquiry requires the external EFS / VAN gateway (`*.webcashvietnam.com`) plus a provisioned `WECMS_EFS_PARTNER_ID` and a per-VAN API key in `TB_VAN_API_INFO` (B2). All of these are **disabled / unprovisioned in every environment** (local, sit, uat, prod). This is Firm-Banking integration that depends on an **external system outside WeCMS**.
2. **Small backend code fix (secondary).** Even once EFS is enabled, the refactor would still leave NAPAS `RCPT_NM` blank because of the missing assignment at `VendorPaymentExcelParser.java:493-503`. A small, well-scoped parser change is required for full legacy parity.

**Selected verdict:** **GAP-2 cannot be completed until Firm Banking (EFS/VAN) integration is available — it depends on external infrastructure outside WeCMS — and additionally needs a small backend code fix (`VendorPaymentExcelParser.java:493-503`) once EFS is enabled.** It is therefore **not** closeable by config only, and **not** a refactor-only fix that can be finished now.

This **refines** (does not contradict) the §14/§15 "conditional gap" verdict with concrete evidence: confirmed all-environment disablement + the exact missing-assignment line. No PO action changes; GAP-2 remains deferred, blocked on Firm-Banking integration. No code modified in this investigation.

---

## Recipient Name / Lookup Recipient Legacy Parity Audit

> Added 2026-06-30. Focused parity audit of how **Recipient Name** (`RCPT_NM`) is populated for Vendor Payment and how the **Lookup Recipient** API (legacy `ckRecName`) behaves, including the **HAENGSUNG Refresh** path. Evidence-based; **no runtime logic changed**. Consolidates the recipient-name findings already developed in §5, §14 (GAP-2), §15, and §18.

### 1. Legacy behavior summary

Vendor Payment resolves the recipient against a **mandatory contact** (Contact Code `HAND_TRCO_KEY`, col 7 → `ckContactCode` `PymnHelper.java:500`), then derives `RCPT_NM` by **network**:

- **NAPAS** → **live Lookup Recipient inquiry** `ckRecName(...)` ([`PymnHelper.java:860-1040`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)), invoked at parse (`readExcel :517-519`) and re-run at save (`uploadPaymentExcel :1426`). It calls `FIRM_PROC.inquiryAccount(inputJSON)` (`:965`) — a depositor-name inquiry over the firm-banking/VAN socket (`WB_00000100`), configured from `TB_FIRM_CONF` via `IDO.TB_FIRM_CONF_R002` (`:874-876`). The bank-returned `FINC_MDA_OWNER_NM` is compared to the contact's stored owner name (`getfincMdaOwnerNm`, `:967`); on match it is persisted to `RCPT_NM` (`:1011`); on mismatch → `WBE0001` at preview / `WBE0404` at save (`:1023-1033`). **When `TB_FIRM_CONF` is missing it does NOT skip — it flags `WBE0001 "Recipient Name error"`** (`:1037-1038`).
- **CITAD** → `getfincMdaOwnerNmFromAcctInfo(...)` (`:1067-1092`) reads `RCPT_NM` from the contact's `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` (`:562`). No live inquiry.

### 2. Refactor behavior summary

- **CITAD** — at parity: `VendorPaymentExcelParser` cross-fills `resolvedRcptNm` from `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` ([`VendorPaymentExcelParser.java:462-465`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentExcelParser.java)) and forwards it (`:608`) so `RCPT_NM` persists.
- **NAPAS** — the Lookup Recipient call chain **is implemented, not a placeholder**: `firmBankingInquiryService.inquiryAccount(...)` ([`VendorPaymentExcelParser.java:487-504`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentExcelParser.java)) → `FirmBankingInquiryService` → `DepositorInquiryService` → `RecipientLookupService` → `EfsApiClient` (REST `WGVN_EFS_201`, the modern replacement for `WB_00000100`). It is **gated OFF**: master flag `wecms.dispatch.recipient-inquiry` (default `false`) + per-tenant active `TB_FIRM_CONF`. With the flag off it returns `Optional.empty()` → **silent no-op** (no verify, no `RCPT_NM`). Two divergences from legacy: (a) the config-missing case silently skips instead of flagging `WBE0001` (§14.1 / §15.3); (b) even with the inquiry enabled the NAPAS branch only flags a mismatch and **never assigns the bank name to `RCPT_NM`** — a latent code gap (§15.4 / §18.3). Tracked as **GAP-2**.
- **HAENGSUNG Refresh** — for the HAENGSUNG product the Excel Upload is replaced by ERP Refresh, which sources the recipient name from Oracle `TB_TRNS_DETAIL.ACET_NM` (Vietnamese-tone-stripped) → `RCPT_NM` ([`ErpPaymentRefreshMasterImporter.java:140`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/haengsung/service/ErpPaymentRefreshMasterImporter.java)), no contact/inquiry. This path is flag-gated OFF (`HAENGSUNG_ORACLE_ENABLED=false`).

### 3. Recipient Name source comparison

| Source | Legacy | Refactor | Match |
|---|---|---|---|
| Contact / saved recipient (`TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`) | ✅ CITAD | ✅ CITAD | ✅ |
| Bank Account / account master (`ACCT_MSTR`) | ❌ never | ❌ never | ✅ |
| Lookup Recipient API (`ckRecName` / NAPAS inquiry) | ✅ NAPAS (live, persisted) | ⚠️ NAPAS wired but **gated off** + latent no-persist (GAP-2) | ❌ |
| EFS / VAN / WeCMS Batch | ✅ NAPAS inquiry rides the firm-banking socket | ⚠️ same chain (`EfsApiClient`), not connected | ❌ (env) |
| Manual Excel cell | ❌ no Recipient Name column | ❌ no Recipient Name column | ✅ |
| HAENGSUNG ERP Refresh (`TB_TRNS_DETAIL.ACET_NM`) | n/a (legacy ERP import) | ✅ implemented, flag OFF | ⚠️ (env) |

### 4. Lookup Recipient condition

| Network | Legacy | Refactor |
|---|---|---|
| NAPAS | **always** when a `TB_FIRM_CONF` row exists; config missing → `WBE0001` (never skip) | only when `wecms.dispatch.recipient-inquiry=true` **and** active `TB_FIRM_CONF`; otherwise silent no-op |
| CITAD | never (DB read of `TRCO_ACCT_INFO`) | never (DB read of `TRCO_ACCT_INFO`) |

### 5. Contact vs Bank Account source rules

**Contact-based only — never `ACCT_MSTR`.** A valid Contact Code is mandatory. CITAD takes the recipient name from the contact's stored account-owner name; NAPAS verifies a *live* bank name against that same contact-stored name and (in legacy) persists the bank-confirmed name. Manual entry is not accepted.

### 6. EFS / VAN / WeCMS Batch dependency status

**Direct dependency for the NAPAS leg.** The Lookup Recipient inquiry is the depositor-name check that, in the refactor, runs through `EfsApiClient` (`WGVN_EFS_201`). It is inert today: `wecms.dispatch.recipient-inquiry=false` and `wecms.dispatch.efs.enabled=false`, with `MockEfsApiClient` throwing if invoked while disabled, and no EFS/VAN provisioning or `TB_VAN_API_INFO` key in local/SIT. The dispatch engine itself is **code-complete** (per memory `project_batch_refactor_real_impl`). CITAD recipient resolution has **no** EFS/VAN dependency.

### 7. Gap list

- **GAP-2 (NAPAS, conditional + latent code gap):** Lookup Recipient inquiry is gated off (EFS/VAN not connected), and even when enabled the NAPAS branch does not persist the bank-returned name to `RCPT_NM` (§15.4). NAPAS `RCPT_NM` is currently null → detail/result render "—".
- **Config-missing policy divergence (PO decision):** legacy flags every NAPAS row `WBE0001` when `TB_FIRM_CONF` is absent; refactor silently skips. The in-code comment misdescribes legacy as "silent-skip" (§14.1) — correct when GAP-2 is next touched.
- CITAD recipient name: **no gap** (parity).

### 8. Final verdict: **CONDITIONAL PASS**

CITAD recipient-name resolution is at full parity. The NAPAS Lookup Recipient path is implemented end-to-end in code but is **gated off + carries a latent persistence gap (GAP-2)** and a config-missing policy divergence requiring a PO decision. Not a PASS until GAP-2 is closed; not a FAIL because the chain exists and CITAD works.

### 9. Environment / verifiability note

The NAPAS Recipient Name / Lookup Recipient behavior **cannot be fully verified in the current environment** — the code is implemented but EFS/VAN/WeCMS Batch is **not connected** (`recipient-inquiry` + `efs.enabled` both `false`, mock EFS client throws, no VAN credentials). CITAD recipient resolution **is** verifiable now (pure DB read of `TRCO_ACCT_INFO`). The HAENGSUNG Refresh recipient path is implemented but requires the HAENGSUNG Oracle connection (`HAENGSUNG_ORACLE_ENABLED=false`) to verify end-to-end.

---

### 10. Lead Engineer disposition (2026-06-30, documentation-only)

**Decision: option (a) — hold. No runtime code changed. The Vendor/Expense NAPAS `RCPT_NM` write-back patch is retained as a *prepared* fix for the future EFS/VAN enablement MR.**

- **NAPAS `RCPT_NM` write-back — ⚠ LATENT MISSING IMPLEMENTATION, BLOCKED BY EFS/VAN FEATURE FLAG.** Code-verified 2026-06-30: the NAPAS block at [`VendorPaymentExcelParser.java:487-504`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/vendorpayment/service/VendorPaymentExcelParser.java) consumes the inquiry result **only** for a mismatch `FieldError` and **never assigns `resolvedRcptNm`** (CITAD-only at `:464`). So even with the flag ON, NAPAS `RCPT_NM` persists null. **Unreachable today** — `wecms.dispatch.recipient-inquiry=false` and `wecms.dispatch.efs.enabled=false` (mock `MockEfsApiClient` throws), so `inquiryAccount(...)` returns `Optional.empty()` and the block is a no-op. **Not user-reproducible** in DEV/SIT/UAT while gated; would surface in **PROD only after EFS is enabled** without the fix.
  - **Prepared fix (DO NOT APPLY until the EFS/VAN MR):** convert the `.ifPresent(...)` at `:493-503` to an explicit `Optional` unwrap and, on a successful **match**, set `resolvedRcptNm = fromBank` — mirroring legacy `PymnHelper.ckRecName:1011`. A lambda can't reassign the non-final `resolvedRcptNm`, so the unwrap is required. Ship with a parser unit test that mocks `FirmBankingInquiryService.inquiryAccount(...)` returning a name (asserts persist-on-match + error-on-mismatch). This is **inert today** (returns empty while gated) and must land **before** EFS is enabled in any environment.
- **Config-missing behavior — 🔶 PO DECISION REQUIRED BEFORE ENABLING INQUIRY.** Legacy flags `WBE0001 "Recipient Name error"` on every NAPAS row when `TB_FIRM_CONF` is absent (`PymnHelper.java:1037-1038`); the refactor silently skips ([`FirmBankingInquiryService.java:162-166`](../../../../wecms-api/src/main/java/com/kosign/wecms/common/firmbanking/FirmBankingInquiryService.java)). Blindly porting "flag every row" is likely wrong for coexistence/R&D — Mr. Jung (PO) must rule on the policy **before** the inquiry flag is switched on, in the same EFS/VAN MR.
- **CITAD `RCPT_NM` (contact `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`) — ✅ no action** (full parity, verifiable today).
- **Preview→Save flow, persistence, entity mapping — ✅ no action** (code-verified: single assignment `VendorPaymentService.java:525`, no overwrite, `RCPT_NM VARCHAR(100)` nullable, wire field forwarded unchanged).

> **Classification (Lead Engineer):** NAPAS write-back = **⚠ Missing implementation**, currently masked by **⚠ Feature flag disabled** + **⚠ External dependency (EFS/VAN/WeCMS Batch)**. Config-missing = **PO decision**. Everything else = **✅ No action required**. **No runtime files were changed in this audit.**

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

See [haengsung-payment-refresh.md](../../features/integrations/haengsung/payment-refresh.md) for the ERP Refresh design + Oracle mapping, and [payment/README.md → "Payment import strategy"](../../features/payment/README.md#payment-import-strategy--standard-wecms-vs-haengsung) for the module-level matrix.

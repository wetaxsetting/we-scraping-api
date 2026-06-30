# Payroll Payment — Scheduled Payment — Excel Upload / Refresh — Legacy Parity Audit

- **Feature:** Payment → Payroll Payment → Scheduled Payment → Excel Upload / Refresh
- **Slug:** `payroll-payment`
- **Legacy WSVC:** `pymn_0005_001` (list), `pymn_0005_002` (upload popup), `pymn_0005_002_r001` (parse/preview), `pymn_0005_002_c001` (save), `pymn_0005_002_c002` (Refresh → ERP data pull), `pymn_0005_003` (detail preview), `comm_1001_001` (withdrawal-account dropdown). **NOT `pymn_0008_*`** — that family is the Payroll *Result* screen (read-only, post-dispatch), with no upload/parse/save.
- **System tags:** JEX (source of truth) ↔ Refactor (`wecms-api` + `wecms-web`)
- **Date:** 2026-06-29
- **Status:** **CLOSED — all gaps dispositioned 2026-06-29.** Fixes implemented & verified (G1, G2, G3 force-CITAD, G4, G5, G6, G10); G7 reclassified **By Design / Closed** (HAENGSUNG-only Refresh is approved product scope, not a parity defect); G8 **Accepted / Closed** (stricter-than-legacy VND/USD whitelist); G9 **Closed** (G3's force-CITAD already restores save-path parity). No remaining open / PO-blocker items on the Payroll Excel Upload parity path. See **§13 Implementation Result → §13.1 Final Disposition**. The audit body (§1–§12) is preserved as the original pre-fix record.
- **Method:** Analyze → Compare with legacy → Root cause → Impact → (this document) → approval → implement.

> **Scope note:** Covers the *Excel Upload* path (download template → parse/preview → save draft batch) plus the **Refresh** button on the Scheduled Payment list. Downstream Dispatch, Approval, Result, and matching are referenced only where the upload save-path feeds them. Every table touched is in the legacy-table-inventory / PROD Active Table List (`SCDUL_PYMN_SUMR`, `SCDUL_PYMN_SUMR_DETL`, `TB_FIRM_BANK_CODE`, `NAPAS_BANK_INFO`, `CHART_ACCT_INFM`, `ACCT_MSTR`, `ERP_TRNS_HSTR`, `AUDIT_LOGS_INFM`).

> **Sibling audits:** This is the Payroll counterpart of [`expense-payment-excel-upload-audit.md`](expense-payment-excel-upload-audit.md) and [`internal-transfer-excel-upload-audit.md`](internal-transfer-excel-upload-audit.md). All three share the physical `SCDUL_PYMN_SUMR(_DETL)` tables and the refactor shares parser/DTO/save scaffolding across the `payment` domain. **Payroll is materially different from the others in the legacy system:** it does **not** use `PymnHelper` at all — its parse and save are *self-contained, duplicated* copies inlined in the JSP action files. Payroll's template is **8 columns** (no Contact Code, no Note, no Document No.), it stores Remarks in a **different column** (`WDRL_ACCT_RMRK`), it **hardcodes the CITAD network** at save, and it carries a **payroll-only VCB-VND prohibition**. These four facts are the source of this audit's most severe findings.

---

## 1. Summary

The refactor reproduces the legacy Payroll Excel Upload flow **faithfully at the template + parse/preview layer**. `PayrollPaymentExcelParser` is a real implementation (Apache POI), not a stub: 8-column header validation, header row index 3, data from row 4, single-currency-per-file guard, CITAD/NAPAS length resolution, ChartAcct lookup, positive-amount check, and special-char validation all mirror legacy `pymn_0005_002_r001_act.jsp`. The three template files (`WeCMS_Payroll_Payment_upload_{EN,KO,VI}.xlsx`) are **byte-for-byte content-identical** to the JEX originals (`WeCMS_Payroll_upload_{EN,KO,VI}.xlsx`); only the file *name* on the path differs.

The real divergences are concentrated in the **save path** and in **payroll-specific legacy rules the refactor reuses generic scaffolding for** rather than reproducing. They matter for **legacy data-migration parity** and for a **PO-mandated money-movement guard**, not the preview UX:

| # | Severity | Gap |
|---|---|---|
| **G1** | **HIGH** | **Remarks persisted to the wrong column.** Legacy payroll stores Remarks (col 6) in `SCDUL_PYMN_SUMR_DETL.WDRL_ACCT_RMRK` (`pymn_0005_002_r001_act.jsp:341,357,623`). The refactor stores it in `TRAN_USAG` (`PayrollPaymentExcelParser.java:227,359`; `PayrollPaymentService.java:492`). The refactor parser docstring (lines 55-56) admits this divergence but labels it "for parity" — it is the opposite of parity. Every refactor payroll row leaves `WDRL_ACCT_RMRK` null and fills `TRAN_USAG`; every legacy row does the reverse. |
| **G2** | **HIGH** | **`TAR_BANK_CD` not resolved to the 6-digit `BANK_CD`.** Legacy save calls `getBankCode(util, idoCon, "CITAD", recItem.TAR_BANK_CD)` → resolves the 3-digit CITAD code to the 6-digit internal `BANK_CD` (`pymn_0005_002_c001_act.jsp:126`, impl `:162-185`). The refactor persists `line.tarBankCd()` = the **typed** 3-digit code (`PayrollPaymentExcelParser.java` resolver sets `resolvedBankCd = trimmed`; `PayrollPaymentService.java` detail write). Same class of bug as Expense G2. |
| **G3** | **HIGH** | **`ANT_DIV_CD` network divergence.** Legacy **hardcodes `AntDivCd.CITAD`** on every payroll detail row (`pymn_0005_002_c001_act.jsp:118,125`) — payroll is effectively CITAD-only (a 6-digit NAPAS code passed to the CITAD `getBankCode` lookup resolves to empty). The refactor stores the parsed network (`"NAPAS"` for 6-digit codes). Result: refactor payroll rows can carry `ANT_DIV_CD="NAPAS"`, which legacy never produces — affecting dispatch routing and data migration. |
| **G4** | **HIGH** | **Payroll VCB prohibition missing.** Legacy save blocks, at upload, **VCB-as-source + VND entirely** ("Payroll does not support VCB VND transfer.") and **VCB + USD to a non-VCB target** ("VCB does not support USD transfer to other banks.") — `pymn_0005_002_c001_act.jsp:128-142`, `WBE0000`, PO mapping 2026-06-16. The refactor payroll path enforces **neither**: the parser defines no `VCB_BANK_CD` constant (unlike vendor/expense/internal parsers), and `dispatch()` runs only `CutoffCheck` window/timing logic (`PayrollPaymentService.java:1028-1035`), not the absolute VCB-VND block. |
| **G5** | **HIGH** | **Row cap silently truncates large payroll files.** Refactor `MAX_ROWS = 500` with `lastRow = min(getLastRowNum(), FIRST_DATA_ROW + MAX_ROWS - 1)` (`PayrollPaymentExcelParser.java:82,131`) — rows 501+ are **silently dropped**. Legacy DOM cap is **1000** and the SAX (`.xlsx`) path has **no cap** (`pymn_0005_002_r001_act.jsp:510-512`). Payroll files routinely exceed 500 employees → silent under-payment. |
| **G6** | MEDIUM | **Blank-row handling differs.** Refactor **breaks** the row loop on the first all-blank row (cols A,C,D,E,F,G) — `PayrollPaymentExcelParser.java:139-148`. Legacy **skips** (continue/return) the blank row and keeps processing subsequent rows (`pymn_0005_002_r001_act.jsp:255-260,533-537`). A blank separator row mid-file → refactor drops everything after it. |
| **G7** | MEDIUM | **Refresh semantics differ.** Legacy list Refresh (`pymn_0005_001` `#btnRefresh` → `pymn_0005_002_c002` → `ErpDataRefreshHelper.refreshErpData(..., "PAYROLL")`) is a **generic ERP data pull**, available to all tenants, coexisting with Excel Upload. The refactor Refresh is the **HAENGSUNG Oracle sync** (`POST /api/v1/payments/haengsung/refresh`, `payment_type=PAYROLL`), gated to **product type `H` only**, and it **replaces** the Excel Upload button (mutually exclusive). Non-`H` tenants lose the legacy ERP-refresh capability. |
| **G8** | LOW | **Currency whitelist timing.** Refactor enforces a `{VND, USD}` whitelist at parse (`UploadConsistencyValidator`, `WBE2120`). Legacy has **no** explicit parse-time whitelist (relies on `CcyCd.fromValue` at save). Net effect similar; error point/timing differs. |
| **G9** | LOW | **Single-network guard.** Refactor blocks mixed NAPAS/CITAD at parse (`WBE2121`). Legacy has no such parse guard (and forces CITAD at save anyway — see G3). Different enforcement model. |
| **G10** | LOW / VERIFY | **Withdrawal-account dropdown.** Refactor FE calls `fetchPayrollWithdrawalAccounts()` with **no `ccy_cd` filter** and auto-selects the **first** account; batch currency is taken from the first Excel row. Legacy `comm_1001_001` currency-filters the firm-banking accounts and auto-selects the account matching the parsed file currency, re-running parse on re-select. Delegated to the shared `WithdrawalAccountListService`; confirm filter parity. |
| **I1** | INFO | `note` / `doc_no` are hard-nulled in the web `parsedToWire` (`PayrollPaymentUploadDialog.tsx:250-251`) and null in the parser. Payroll's 8-column template has **no** Note / Document No. columns, so legacy never stores them either → **null on both sides = parity** (not a bug). |
| **I2** | INFO | `trco_key` / `hand_trco_key` / `trco_cmnm` / `trco_cmenm` are dropped on the web save and null in the parser. Payroll has no Contact Code column; legacy stores nothing → **parity** (null both sides). |
| **I3** | INFO | **Audit log is refactor-only.** Refactor writes `AUDIT_LOGS_INFM` with `WSVC_ID = "payroll-payment"` (slug, `PayrollPaymentService.java:98,528`). Legacy payroll upload writes **no** audit row (only ERP history). This is an additive refactor cross-cutting standard (CLAUDE.md slug rule) — no migration risk. |
| **I4** | INFO | **At parity:** `SCDUL_PYMN_TYPE_CD = "00"`, ERP `ERP_TRNS_HSTR` `TRNS_TYPE_CD = "12"` (`PAYROLL_SCHEDULED_PAYMENT`), `TRNF_YN = "N"`, single-currency-per-file guard, ChartAcct lookup, positive-amount check, no save-side re-validation (legacy also has none), key generation via `wb_get_next_val`, `OUT_BANK_CD` normalized from the withdrawal bank, `CCY_CD` = first-row transfer currency, `WDRL_CCY_CD` = withdrawal-account currency. |
| **T1** | MEDIUM (test) | **No tests** for `PayrollPaymentExcelParser` (header/column/CITAD-NAPAS/guards) or `createDraft`/`doCreateDraft` (master/detail mapping, type code, ERP, audit, key-race). Existing payroll tests cover only dispatch / result / approval-attachment. |

Everything outside G1–G10 is at parity. Note that `CITAD_BANK_CD` being null in the refactor (parser "Phase 2" positional null) is **not** a gap here: legacy payroll save never sets `CITAD_BANK_CD` either, so both are null.

---

## 2. Legacy Code Trace (`pymn_0005_*`)

> **Premise correction.** The Payroll upload flow does **not** live in `pymn_0008_*` or in `PymnHelper`. `pymn_0008_*` is the Payroll **Result** family (read-only, post-dispatch, `TRNF_YN='Y'`). `PymnHelper.importDataFromExcel` / `uploadPaymentExcel` (`PymnHelper.java:153, 1301-1340`) branch only VENDOR/EXPENSE/INTERNAL — **no payroll branch**; `PymnHelper.HEADER_TITLE_*` is the 10/9-column vendor/internal layout, unused by payroll. Payroll's parse and save are **inlined and self-contained** in `pymn_0005_002_r001_act.jsp` and `pymn_0005_002_c001_act.jsp`.

### 2.1 Screen + popup
- List page: `pymn_0005_001_view.jsp` + `pymn_0005_001.ts` — Scheduled Payroll Payment list (`SCDUL_PYMN_TYPE_CD='00'`); "Excel Upload" opens popup `pymn_0005_002`; Refresh button `#btnRefresh` (`pymn_0005_001.ts:164-183`).
- Upload popup: `pymn_0005_002_view.jsp` + `pymn_0005_002.ts` — 8-column preview, withdrawal-account dropdown (from `comm_1001_001`), Download template, Browse, Upload. Preview detail rows from `pymn_0005_003`.
- File uploader: `jexjs.plugin<JexSingleFileUploader>("upload","pymn_0005_002_r001",{ name:"FILE_DATA" })` (`pymn_0005_002.ts:70,103,213`).

### 2.2 Template download
- Static assets: `WECMS DEV/WECMS_STATIC/web/file/excel/VN/WeCMS_Payroll_upload_{EN,KO,VI}.xlsx`.
- `pymn_0005_002.ts:275-279` `fnDownloadExcelForm`: `excelUrl = "file/excel/VN/WeCMS_Payroll_upload_" + LANG.toUpperCase() + ".xlsx?" + ts`. `LANG = jexjs.getLang()` (`:74`). **VN path hardcoded** (no KH branch for the upload template).
- Header row layout (xlsx, spreadsheet row 4 = 0-based 3): merged title `A1:H2` ("Payroll"); hint row 3; header row 4 = `Bank Code*` · `Deposit Bank ` · `Deposit Account No.*` · `Recipient Name *` · `Transfer Amount *` · `Transfer Currency *` · `Remarks (max 150 letters)` · `Reporting Account`; sample data row 5.

### 2.3 Parse / preview — `pymn_0005_002_r001_act.jsp`
- `FILE_DATA` required + is-file (`:95-98`); `BANK_CD` (withdrawal bank) into static `bankCode` (`:768`). Dispatch: `.xlsx` → SAX `processSaxXlsxFile` (`:405`); else DOM `readExcelWithDOM` (`:493`). Identical column logic both paths.
- **Header row index 3 (0-based)** (SAX `:182`, DOM `:443`); **data from row 4** (`:194,519`).
- **Header titles (8 cols, verbatim `:60-92`):**

  | Col | EN | KO | VI |
  |---|---|---|---|
  | 0 | `Bank Code*` / `Bank Code(or Bank Name) *` | `은행코드 *` | `Mã ngân hàng *` |
  | 1 | `Deposit Bank` / `Deposit Bank *` | `입금은행` | `Ngân hàng nhận tiền` |
  | 2 | `Deposit Account No.*` / `Deposit No.*` | `입금 계좌번호 *` | `Số tài khoản nhận tiền*` |
  | 3 | `Recipient Name *` | `수신자명 *` | `Tên người nhận *` |
  | 4 | `Transfer Amount *` | `이체 금액 *` | `Số tiền chuyển khoản  *` |
  | 5 | `Transfer Currency *` | `이체통화 *` | `Loại tiền chuyển *` |
  | 6 | `Remarks (max 150 letters)` | `적요 (최대 150자)` | `Nội dung giao dịch (tối đa 150 ký tự)` |
  | 7 | `Reporting Account` | `보고서 계정` | `Tài khoản báo cáo` |

  Validation: `cellTitle.startsWith(validTitle)` against any locale variant (`:236-250` SAX, `:470-489` DOM); mismatch / `<8` header cells → `WBE0001` + `"Header Title(" + cellTitle + ")"`.
- Row loop from row 4; **DOM cap = 1000** (`:510-512`); **SAX no cap**. Blank-row skip when cols 0,2,3,4,5,6 all empty → **continue** (DOM) / **return** per-row (SAX), `:255-260,533-537` — no hard stop.
- **Column map (8 cols):**

  | Col | Field | Validation | Error |
  |---|---|---|---|
  | 0 | `TAR_BANK_CD` (+ `ANT_DIV_CD`) | required; len 3 → CITAD, len 6 → NAPAS, else `""`; `getListTbFirmBankCode(antDivCd, code)` count ≥1 | empty → `WBE1013`; not found → `WBE0001` |
  | 1 | `TAR_BANK_NM` (Deposit Bank) | `checkValidStringInMax(required, 100)` | `WBE0001` |
  | 2 | `FINC_MDA_NO` (Account No.) | `checkValidStringInMax(required, 100)` | `WBE0001` |
  | 3 | `RCPT_NM` (Recipient Name) | VN tone-strip (`StringUtils.replaceTone`); `SpecialCharValidator(..., PAYROLL_PAYMENT, RECIPIENT_NAME, ...)`; `checkValidStringInMax(required, 100)` | special-char → `WBE0000`; else `WBE0001` |
  | 4 | `TRAN_AMT` | `checkValidAmountOnlyPositive_(required)` (strictly positive) | `WBE0001` |
  | 5 | `TRNF_CCY_CD` | `checkValidStringInMax(required, 3)`; **single-currency-per-file** consistency vs first non-empty | length → `WBE0001`; inconsistency → `WBE0001` + "Currency must be consistent…" |
  | 6 | **`WDRL_ACCT_RMRK`** (Remarks) | VN tone-strip; `SpecialCharValidator(..., REMARKS, ...)`; `checkValidStringInMax(optional, 150)` | special-char → `WBE0000`; length → `WBE0001` |
  | 7 | `CHART_ACCT_CD` (Reporting Account) | optional; `getChartAcctInfm` (`CHART_ACCT_INFM`, `BIZ_KEY` + code + `DEL_YN='N'`) → fills `CHART_ACCT_CD/SEQ_NO/NM_*` | not found → `WBE0001` + "Reporting Account error" |

  **No** explicit VND/USD whitelist at parse. **No** employee-id / contact-code / salary-type / salary-date / note / doc-no columns.
- `getListTbFirmBankCode` (`:707-730`): CITAD → `ant_div_cd='CITAD' AND citad_bank_cd=? AND bank_stas='1' AND bank_branch_div='1'`; NAPAS → `ant_div_cd='NAPAS' AND bank_cd=?` (note DOM omits `bank_stas`). Both expect `CNT≥1`.
- Output REC[] per row: `ANT_DIV_CD, TAR_BANK_CD, TAR_BANK_NM, FINC_MDA_NO, RCPT_NM, TRAN_AMT, TRNF_CCY_CD, WDRL_ACCT_RMRK, CHART_ACCT_CD, CHART_ACCT_SEQ_NO, CHART_ACCT_NM_*`. Counts `TOT_CNT/SUC_CNT/ERR_CNT`; errors packed `ERR_FLD` / `ERR_FLD_CAUS` (pipe-joined via `DatImpWizHelper.putError`).

### 2.4 Save — `pymn_0005_002_c001_act.jsp` (inline `uploadPaymentExcel`, `:58-160`)
Triggered by `pymn_0005_002.ts:462-494` `fnsavePayrollPayment` → POST `pymn_0005_002_c001` with `TITL` (filename − ext), `CCY_CD`, `BANK_CD`, `ACCT_NO` (from selected withdrawal account), `REC` = success rows only. Required-input check `:45-55` (`TITL, FILE_DATA, BANK_CD, ACCT_NO, CCY_CD`; last three per Redmine #42761).
- **Master `SCDUL_PYMN_SUMR`** (`:63-103`): `copyFrom(input)` (`TITL`, `TRNF_DT`); `BIZ_KEY`; `CCY_CD` = first-REC `TRNF_CCY_CD` (fallback VND, `:67-78`); `OUT_BANK_CD` = `NapasBankInfo.napas_bank_cd` resolved from `BANK_CD` (`:81-82`); `WDRL_ACT_NO` = `ACCT_NO` (`:83`); `WDRL_CCY_CD` = `CcyCd.fromValue(CCY_CD)` (`:84`); `TOTAL_TRAN_AMT` = 0 then sum (`:86,156`); `TOT_TRNS_CNT` = `REC.size()` (`:87`); **`SCDUL_PYMN_TYPE_CD = ScdulPymnTypeCd.PAYROLL = "00"`** (`:95`); `TRNF_YN = NOT_YET_TRANFER` (`:98`); `FILE_INFO` (`:61,99`); `REG_USER`/`REG_DTM = utcNow` (`:100-101`). Created via `ScdulPymnSumrDAO.create` (key generated inside the DAO → `wb_get_next_val`).
- **ERP history** (`:111`): `ErpTrnsHisHelper.erpTrnsHisUploader(..., ErpTypeCd.PAYROLL_SCHEDULED_PAYMENT)` = **`"12"`**.
- **Detail `SCDUL_PYMN_SUMR_DETL`** per REC (`:115-154`): `copyFrom(recItem)` (carries `WDRL_ACCT_RMRK`, `FINC_MDA_NO`, `RCPT_NM`, `TRAN_AMT`); **`ANT_DIV_CD = AntDivCd.CITAD` hardcoded** (`:125`); **`TAR_BANK_CD = getBankCode(util, idoCon, "CITAD", recItem.TAR_BANK_CD)`** → 6-digit `BANK_CD` (`:126`, impl `:162-185`); `CHART_ACCT_SEQ_NO`, `CHART_ACCT_CD`, `TRNF_CCY_CD = CcyCd.fromValue(...)` (`:146-148`); `REG_USER`/`REG_DTM`/`MOD_DTM = utcNow` (`:144-149`). **Does not set `CITAD_BANK_CD`** (null). Created via `ScdulPymnSumrDetlDAO.create`.
- **VCB guard** (`:128-142`, PO 2026-06-16): `if OUT_BANK_CD=="970436"` → VND → `WBE0000 "Payroll does not support VCB VND transfer."`; else if `TAR_BANK_CD != "970436"` (USD to non-VCB) → `WBE0000 "VCB does not support USD transfer to other banks."`. Throws `WbRollbackException` (rolls back the whole upload).
- **Audit log:** **none** (no `AUDIT_LOGS_INFM` write anywhere in the payroll save path — confirmed by grep).
- **Save-side re-validation:** **none** (trusts the already-validated success rows; only the VCB guard runs at save).
- **Approval linkage:** none at save — approval is a separate later step from the list (`#req_approv` → `comm_0194_001` `APPR_DV="21"` Payroll).

### 2.5 Refresh — `pymn_0005_002_c002_act.jsp`
`pymn_0005_001.ts:164-183` `#btnRefresh` → `fnManualRefresh("PAYROLL", periodDate)` (`:1638-1665`) → POST `pymn_0005_002_c002` `{PAYMENT_TYPE, PERIOD_DATE}`. Backend (`:80` "Payroll Pymn > Refresh Get Data > Save Payments") validates `PAYMENT_TYPE ∈ {VENDOR,EXPENSE,PAYROLL,OVERSEAS,ALL}` (`:61-71`) + `PERIOD_DATE` (8-char or `D8-D8`), then `ErpDataRefreshHelper.refreshErpData(util, idoCon, loginUser, PERIOD_DATE, "PAYROLL")` (`:110`). This is a **generic ERP-integration data pull** for the period, not an Excel upload. Available to all tenants; coexists with Excel Upload on the list.

### 2.6 Withdrawal Bank / Account dropdown — `comm_1001_001`
`pymn_0005_002.ts:538-599` ("Withdraw Bank/Account (FirmBanking) TextBox") queries `ACCT_MSTR` joined to firm-banking, filtered by `CCY_CD` and active firm-banking registration. Re-selecting an account re-runs the upload parse (`:115-134`); the parsed-file currency auto-selects the matching withdrawal currency (`:238-245`). Account is mandatory (Redmine #42761).

### 2.7 Payroll-specific legacy business rules
1. **Separate, duplicated code** — payroll does not share `PymnHelper`; the 8-column layout is independent of vendor/internal layouts.
2. **No employee master** — recipients identified only by `RCPT_NM` + `FINC_MDA_NO` (bank/account). No employee lookup, no duplicate-employee/account detection; the same person/account may appear multiple times → multiple detail rows.
3. **Salary type (`APPR_FUND_TP`)** is set at **approval**, not upload (`"0"`=Salary / `"1"`=Bonus, displayed on the list `pymn_0005_001.ts:1068-1078`; approval `APPR_DV="21"`). The upload never sets it.
4. **Grouping** — one file = one master + N detail rows; grouping is by file/batch, not by employee.
5. **Currency** — single-currency-per-file (parse); **VCB blocks at save** (G4); master `CCY_CD`/`WDRL_CCY_CD` from first row / selected account.
6. **Network forced to CITAD** at save (G3); `TAR_BANK_CD` resolved to 6-digit (G2).

---

## 3. Refactor Code Trace

### 3.1 Web (`wecms-web`)
- **List page:** `app/(dashboard)/payment/payroll/scheduled-payment/page.tsx` → `components/payment/payroll/ScheduledPaymentPage.tsx` (shared `PaymentScheduledBatchListPage`). View Setting `SCREEN_IDS.PAYMENT_PAYROLL_PAYMENT_SCHEDULED`; extra cols Scheduled Date + Payroll Type.
- **Excel Upload vs Refresh (mutually exclusive):** `PaymentScheduledBatchListPage.tsx:571` `isHaengsung = isProductType(userStorage.get(),"H")`; `:692` `showRefresh = isHaengsung && onRefresh`; `:1418-1422` renders `<RefreshButton>` when `showRefresh` **else** `<ExcelUploadButton>`. Non-`H` tenants see Upload; `H` tenants see Refresh.
- **Upload dialog:** `components/payment/payroll/PayrollPaymentUploadDialog.tsx` over shared `PaymentExcelUploadDialog` → `ExcelUploadDialog`.
  - Template map (`:38-42`): `EN/KO/VI → /templates/payroll/WeCMS_Payroll_Payment_upload_{EN,KO,VI}.xlsx`; locale fallback EN (`:99`). All three present on disk.
  - Accepted: `.xlsx,.xls,…sheet,…ms-excel` (`PaymentExcelUploadDialog.tsx:266`).
  - Parse: `parsePayrollPaymentExcel(file, bankCd)` → `POST /api/v1/payment/payroll-payment/uploads/parse` (multipart `file_data` + `bank_cd`). `bankCd = selectedAccount?.bank_cd ?? accounts[0]?.bank_cd ?? ""` (`:121`).
  - Preview: **8 columns** (`:109-118`) — Bank Code (`ANT_DIV_CD`, populated from `line.tar_bank_cd`), Bank, Deposit Account No., Recipient Name, Transfer Amount, Transfer Currency, Remarks (`TRAN_USAG`), Reporting Account.
  - **Upload-enable:** `uploadDisabled = !selectedFile || !parsed || errorCount>0 || isBusy` (`PaymentExcelUploadDialog.tsx:250`) → enabled only when parsed and `errorCount===0`. Matches legacy (`pymn_0005_002.ts:374-406` disables while `err_cnt>0`).
  - **`parsedToWire`** (`:239-255`): forwards `finc_mda_no, ant_div_cd, tar_bank_cd, rcpt_nm, tran_amt, trnf_ccy_cd, tran_usag, chart_acct_cd, chart_acct_seq_no, citad_bank_cd, depo_ccy_cd`; **hard-nulls `note` and `doc_no`** (`:250-251`); **omits** `trco_key/hand_trco_key/trco_cmnm/trco_cmenm`. (All consistent with payroll's 8-column template — see I1/I2.)
  - **Save:** `createPayrollPayment(file, request)` → `POST /api/v1/payment/payroll-payment` (multipart `file_data` + JSON `request`). Master `titl` = filename − ext; `bank_cd`/`acct_no` = selected account; `ccy_cd` = `trnf_ccy_cd` = first success row's currency; `wdrl_ccy_cd` **not sent** (backend falls back to `ccy_cd`).
- **Refresh:** `useHaengsungRefresh({ paymentType:"PAYROLL", ... })` → `POST /api/v1/payments/haengsung/refresh` `{payment_type:"PAYROLL", period_date}`. Product-`H` gated; success/error dialog. No JEX-style `c002` ERP refresh.
- **`lib/payment/payrollType.ts`** is only a Payroll-Type **label resolver** for the Approval Details popup (`appr_fund_tp`), not part of upload.

### 3.2 API (`wecms-api`)
- **Controller** `PayrollPaymentController` (`/api/v1/payment/payroll-payment`, class `@PreAuthorize canAccess('payment-payroll-scheduled')`): `GET /withdrawal-accounts` (`:322-326`), `POST /uploads/parse` (`:328-334`), `POST /` save (`:336-341`), `GET /{id}/file` (`:359-380`), `GET /next-doc-no` (`:127-130`). **No template-download and no Refresh endpoint here** (Refresh = shared HAENGSUNG controller). Upload DTOs are **shared from `internaltransfer.dto`**.
- **Parser** `PayrollPaymentExcelParser` (POI, `@Transactional(readOnly=true)`): `HEADER_ROW=3, FIRST_DATA_ROW=4, MAX_ROWS=500` (`:80-82`); `HEADERS_{EN,KO,VI}` 8-prefix arrays matched via per-column `startsWith` any locale (`:89-103`); header/`<8`/no-match → `WBE2018`; POI IO error → `WBE2017`. Row walk `lastRow = min(getLastRowNum(), FIRST_DATA_ROW+MAX_ROWS-1)` (`:131`); **break** on all-blank cols 0,2,3,4,5,6 (`:139-148`).
  - Column map: 0 → `tarBankCd`(+`antDivCd`,`tarBankNm`); 1 → `tarBankNm` (display); 2 → `fincMdaNo` (req); 3 → `rcptNm` (req + special-char); 4 → `tranAmt` (req; rejects `signum()<0`, **allows 0**); 5 → `trnfCcyCd` (req, upper); 6 → `tranUsag` (Remarks, special-char, **no length cap in parser**); 7 → `chartAcctCd` (≤20, tenant ChartAcct lookup).
  - Bank: len 6 → NAPAS lookup; len 3 → CITAD lookup; else error. `resolvedBankCd = trimmed` (**typed code, not normalized**); `CITAD_BANK_CD` output hard-null ("Phase 2"). `trcoKey/handTrcoKey/docNo/note/trcoCmnm/trcoCmenm` = null (no columns).
  - Post-parse guards: `assertSingleCurrency`/`assertCurrencyWhitelist{VND,USD}` → `WBE2120`; `assertSingleNetwork` → `WBE2121`.
  - **No** VN tone-strip; **no** recipient DB lookup; **no** duplicate detection. Response `ParseExcelResponse(totCnt, sucCnt, errCnt, rows[])`; per-cell errors pipe-packed `errFld`/`errFldCaus`.
- **Save** `PayrollPaymentService.createDraft` → `doCreateDraft` (`:393-538`, `transactionalRetry.runOnce` w/ one-shot retry on key race): key via `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no')`.
  - Master (`:425-461`): `SCDUL_PYMN_TYPE_CD = PAYROLL("00")`; `OUT_BANK_CD = OutBankCdNormalizer.normalize(bankCd)`; `WDRL_ACT_NO = acctNo`; `CCY_CD = ccyCd`; `WDRL_CCY_CD = ccyCd`; `TITL`; `TRNF_YN="N"`; `ERP_PROC_CMPL_YN="N"`; `TOT_TRNS_CNT`; `TOTAL_TRAN_AMT = Σ`; file fields; `REG_*/MOD_*` UTC. `saveAndFlush` before side effects.
  - Detail (`:481-508`): writes `TRAN_AMT, TRNF_CCY_CD, ANT_DIV_CD` (**parsed**, not forced CITAD), `TAR_BANK_CD = line.tarBankCd()` (**typed**, not resolved), `FINC_MDA_NO`, **`TRAN_USAG = emptyToNull(line.tranUsag())`** (`:492`), `CHART_ACCT_CD/SEQ_NO`, `DEPO_CCY_CD`, `REG_*/MOD_*`. **Null for payroll:** `TRCO_KEY, HAND_TRCO_KEY, DOC_NO, NOTE, CITAD_BANK_CD, TRCO_CMNM, TRCO_CMENM`, **`WDRL_ACCT_RMRK`** (never written — see G1). No save-side re-validation; DTO bean-validation `@DecimalMin("0.000001")` rejects zero amounts the parser allowed.
  - ERP (`:510-525`): `recordUploadedFile(TRNS_TYPE="12")` when file present; non-blocking.
  - Audit (`:527-533`): `auditLogService.append(... wsvcId="payroll-payment", refNo="SCDUL_PYMN_SUMR", refNo1=scdulPymnNo ...)`.
  - **No VCB guard at save.** `dispatch()` (`:1009-1035`) runs only `CutoffCheck` (Shinhan VND→VND window, VCB CITAD ≥500M timing tier) — not the absolute VCB-VND prohibition.
- **Refresh** = `HaengsungPaymentRefreshController` `POST /api/v1/payments/haengsung/refresh`; `payment_type=PAYROLL` → Oracle `FILE_GB='AC2'`; flag `haengsung.oracle.enabled`; product-`H` guard.

---

## 4. Template Audit

Method: unzipped each `.xlsx`, extracted `sharedStrings` + `sheet1` rows 1–6 for all three locales, both trees.

| Aspect | JEX (`…/file/excel/VN/WeCMS_Payroll_upload_{L}.xlsx`) | Refactor (`…/public/templates/payroll/WeCMS_Payroll_Payment_upload_{L}.xlsx`) | Match |
|---|---|---|---|
| Sheets | `["Payroll","NOTE"]` | `["Payroll","NOTE"]` | ✅ |
| Title (merged `A1:H2`) | EN `Payroll` / KO `급여 지급` / VI `Thanh toán lương` | identical | ✅ |
| Hint row (3) | identical strings per locale | identical | ✅ |
| Header row (4) | `Bank Code*` · `Deposit Bank ` · `Deposit Account No.*` · `Recipient Name *` · `Transfer Amount *` · `Transfer Currency *` · `Remarks (max 150 letters)` · `Reporting Account` | identical | ✅ |
| Sample row (5) | `616 · ShinhanBank · 700071152854 · NNT dang hoat dong · 3000000 · VND · Webcash VN chi luong thang 12_3 · 2004002` | identical | ✅ |
| Column order / count | 8 | 8 | ✅ |
| **Filename on path** | `WeCMS_Payroll_upload_{EN,KO,VI}.xlsx` | `WeCMS_Payroll_Payment_upload_{EN,KO,VI}.xlsx` | ⚠️ cosmetic only — each system serves its own file; content identical |

**Parser expectation vs template:** both parsers read the header at 0-based row 3 (sheet row 4) and match by prefix; legacy full titles and refactor 8-prefixes both match the template header strings. **Template layer = full parity.** Sample bank code `616` is a 3-digit **CITAD** code (consistent with legacy's CITAD-only save, G3).

---

## 5. Excel Read & Validation Audit

| Rule | Legacy (`pymn_0005_002_r001`) | Refactor (`PayrollPaymentExcelParser`) | Match / Gap |
|---|---|---|---|
| File extension | `.xlsx`→SAX, else DOM | POI `WorkbookFactory` (xlsx/xls) | ✅ |
| Header row index | 3 (0-based) | `HEADER_ROW=3` | ✅ |
| Header titles | full titles, `startsWith`, per locale | 8 prefixes, `startsWith`, per locale | ✅ |
| Header error | `WBE0001` | `WBE2018` | ⚠️ different code (cosmetic; both reject) |
| Max rows | DOM 1000 / SAX uncapped | **500 (silent `min`)** | ❌ **G5** |
| Blank-row handling | skip & continue | **break** | ❌ **G6** |
| Bank code col 0 | len 3 CITAD / len 6 NAPAS; `getListTbFirmBankCode` ≥1 | same length logic; `findBy…` | ✅ logic |
| Bank-code resolution (stored) | save resolves to 6-digit `BANK_CD` | parser keeps typed code | ❌ **G2** (manifest at save) |
| Account no. col 2 | required, ≤100 | required | ✅ |
| Recipient name col 3 | **VN tone-strip** + special-char + ≤100 | special-char only (no tone-strip), required | ⚠️ tone-strip missing (see note) |
| Amount col 4 | strictly positive | `signum()<0` rejected (**allows 0**); DTO `@DecimalMin` rejects 0 at save | ✅ net (0 blocked at save) |
| Currency col 5 | required, len 3; single-currency/file | required, upper; `assertSingleCurrency` `WBE2120` | ✅ |
| Currency whitelist | none at parse (CcyCd.fromValue at save) | `{VND,USD}` at parse `WBE2120` | ⚠️ **G8** |
| Single-network | none at parse (CITAD forced at save) | `assertSingleNetwork` `WBE2121` | ⚠️ **G9** |
| Remarks col 6 → field | `WDRL_ACCT_RMRK`; tone-strip + special-char; ≤150 | `TRAN_USAG`; special-char; **no length cap in parser** | ❌ **G1** (field) + ⚠️ length cap |
| Reporting Account col 7 | optional; `getChartAcctInfm` | optional; `chartAcctRepo` lookup | ✅ |
| Employee / contact / duplicate | none | none | ✅ |
| Error packing | `ERR_FLD`/`ERR_FLD_CAUS` pipe | `errFld`/`errFldCaus` pipe | ✅ |
| Counts | `TOT/SUC/ERR_CNT` | `totCnt/sucCnt/errCnt` | ✅ |

**Recipient-name tone-strip note:** legacy applies `StringUtils.replaceTone` to `RCPT_NM` (and Remarks) before storing; the refactor parser does not. If the refactor stores accented Vietnamese names where legacy stored ASCII-folded ones, that is a (minor) data divergence and a potential bank-rejection risk. Treated here as a sub-item of the recipient-name handling rather than a standalone numbered gap — **verify and confirm** with the team whether tone-strip is required for payroll recipient names.

---

## 6. Withdrawal Bank / Account Audit

| Aspect | Legacy `comm_1001_001` | Refactor | Match / Gap |
|---|---|---|---|
| Source | `ACCT_MSTR` ⋈ firm-banking | `GET /withdrawal-accounts` (`WithdrawalAccountListService`) | ✅ delegate |
| Currency filter | filtered by `CCY_CD` | FE calls **without `ccy_cd`** (shows all) | ⚠️ **G10** |
| Firm-banking filter | active firm-banking only | backend responsibility | ✅ (verify) |
| Default selection | account matching parsed-file currency | **first** account | ⚠️ **G10** |
| Re-select behavior | re-runs parse (currency re-check) | parse uses selected account `bank_cd` | ⚠️ partial |
| Mandatory | yes (Redmine #42761) | yes (`!selectedAccount` disables Save) | ✅ |

---

## 7. Payroll Business Rule Audit

| Rule | Legacy | Refactor | Match / Gap |
|---|---|---|---|
| Salary type (`APPR_FUND_TP`) | set at approval, not upload | same (label resolver only) | ✅ |
| Payroll schedule | `TRNF_DT` via `copyFrom`; list date range | master schedule | ✅ (upload-level) |
| Employee info | none (recipient = name + account) | none | ✅ |
| Grouping | one file = one master + N detail | same | ✅ |
| Employee account/bank lookup | none (no employee master) | none | ✅ |
| Amount validation | strictly positive | 0 allowed at parse, blocked at save | ✅ net |
| Duplicate employee/account | none | none | ✅ |
| Same employee multiple payments | allowed (separate rows) | allowed | ✅ |
| Network policy | **CITAD forced** at save | parsed network preserved (NAPAS possible) | ❌ **G3** |
| Currency restriction | single/file + **VCB-VND prohibited**, VCB-USD only→VCB | single/file + whitelist; **no VCB block** | ❌ **G4** |
| Approval process | `APPR_DV="21"`, separate step | matching `appr_fund_tp` flow | ✅ |
| Request creation | separate from upload | separate | ✅ |

---

## 8. Upload Save — DB Audit

### `SCDUL_PYMN_SUMR` (master)
| Column | Legacy | Refactor | Match |
|---|---|---|---|
| `SCDUL_PYMN_TYPE_CD` | `"00"` | `"00"` | ✅ |
| `OUT_BANK_CD` | `NapasBankInfo.napas_bank_cd` | `OutBankCdNormalizer.normalize` | ✅ |
| `WDRL_ACT_NO` | `ACCT_NO` | `req.acctNo()` | ✅ |
| `CCY_CD` | first-row `TRNF_CCY_CD` (fb VND) | `req.ccyCd()` (= first-row ccy from FE) | ✅ |
| `WDRL_CCY_CD` | `CcyCd.fromValue(CCY_CD)` | `req.ccyCd()` | ✅ (FE sends acct ccy via ccyCd) |
| `TITL` | filename − ext | filename − ext | ✅ |
| `TRNF_YN` | `"N"` | `"N"` | ✅ |
| `TOT_TRNS_CNT` | `REC.size()` | line count | ✅ |
| `TOTAL_TRAN_AMT` | Σ | Σ | ✅ |
| file fields | `FILE_INFO` | org/save/size | ✅ |
| `REG_*/MOD_*` | utcNow | utcNow | ✅ |

### `SCDUL_PYMN_SUMR_DETL` (detail, per row)
| Column | Legacy | Refactor | Match / Gap |
|---|---|---|---|
| `TRAN_AMT` | `copyFrom` | `line.tranAmt()` | ✅ |
| `TRNF_CCY_CD` | `CcyCd.fromValue` | `emptyToDefault(...)` | ✅ |
| **`ANT_DIV_CD`** | **`CITAD` (hardcoded)** | parsed (`NAPAS`/`CITAD`) | ❌ **G3** |
| **`TAR_BANK_CD`** | **`getBankCode` → 6-digit** | typed code (3-digit) | ❌ **G2** |
| `FINC_MDA_NO` | `copyFrom` | `line.fincMdaNo()` | ✅ |
| **Remarks** | **`WDRL_ACCT_RMRK`** | **`TRAN_USAG`** | ❌ **G1** |
| `CHART_ACCT_CD` / `SEQ_NO` | set | set | ✅ |
| `CITAD_BANK_CD` | not set (null) | null ("Phase 2") | ✅ (both null) |
| `DEPO_CCY_CD` | not set | `line.depoCcyCd()` | ⚠️ minor (legacy null) |
| `TRCO_KEY/HAND_TRCO_KEY/TRCO_CM*` | null | null | ✅ |
| `DOC_NO/NOTE` | null (no columns) | null | ✅ |
| `REG_*/MOD_*` | utcNow | utcNow | ✅ |

### `ERP_TRNS_HSTR` / `AUDIT_LOGS_INFM`
| | Legacy | Refactor | Match |
|---|---|---|---|
| ERP `TRNS_TYPE_CD` | `"12"` (`PAYROLL_SCHEDULED_PAYMENT`) | `"12"` | ✅ |
| ERP write condition | on upload (file present) | on upload (file present), non-blocking | ✅ |
| Audit log | **none** | `AUDIT_LOGS_INFM` w/ `WSVC_ID="payroll-payment"` | ⚠️ **I3** (refactor-only, additive) |

---

## 9. Legacy vs Refactor — Comparison Matrix

| Area | Legacy | Refactor | Match / Gap |
|---|---|---|---|
| Template | `WeCMS_Payroll_upload_{L}.xlsx`, 8 col | identical content, `_Payment_` in name | ✅ (filename cosmetic) |
| Parser | inline JSP, real | `PayrollPaymentExcelParser`, real | ✅ |
| Header validation | row 3, prefix, `WBE0001` | row 3, prefix, `WBE2018` | ✅ (code differs) |
| Max rows | 1000 / uncapped | **500 silent** | ❌ G5 |
| Blank row | skip & continue | **break** | ❌ G6 |
| Required fields | bank/acct/recipient/amount/ccy | same | ✅ |
| Remarks target column | `WDRL_ACCT_RMRK` | `TRAN_USAG` | ❌ G1 |
| Bank-code stored | 6-digit resolved | typed | ❌ G2 |
| Network | CITAD forced | parsed | ❌ G3 |
| VCB-VND rule | blocked at save | **absent** | ❌ G4 |
| Currency whitelist | save-time | parse-time | ⚠️ G8 |
| Single-network guard | none (forced CITAD) | parse-time | ⚠️ G9 |
| Preview | 8 col | 8 col | ✅ |
| Upload-enable | err_cnt=0 | errorCount=0 | ✅ |
| Save master | as §8 | as §8 | ✅ |
| Save type code | `"00"` | `"00"` | ✅ |
| ERP history | `"12"` | `"12"` | ✅ |
| Audit log | none | slug write | ⚠️ I3 |
| Approval | separate `APPR_DV="21"` | separate | ✅ |
| Refresh | ERP pull (all tenants) | HAENGSUNG sync (`H` only) | ❌ G7 |
| Withdrawal dropdown | currency-filtered, ccy-match default | unfiltered, first default | ⚠️ G10 |
| Save re-validation | none | none | ✅ |
| Key generation | `wb_get_next_val` | `wb_get_next_val` | ✅ |
| Tests | n/a | parser/save untested | ⚠️ T1 |

---

## 10. Gaps / Bugs Found

> **Historical (pre-fix) record.** The findings and "Recommended fix" lines below are the original audit as written before implementation — including the `G7 → PO decision`, `G8 → accept`, and `G9 → resolve with G3` recommendations. They are **superseded by §13 Implementation Result + §13.1 Final Disposition**, which is the authoritative current status. G7 is **By Design / Closed**, G8 is **Accepted / Closed**, and G9 is **Closed** — none is an open blocker or pending PO decision.

### G1 — Remarks persisted to `TRAN_USAG` instead of `WDRL_ACCT_RMRK` (HIGH)
- **Root cause:** Refactor reuses the shared transfer parser/DTO/save (Remarks → `TRAN_USAG`) instead of payroll's own legacy mapping. `PayrollPaymentExcelParser.java:55-56` documents the choice but mislabels it "for parity".
- **Impact:** Refactor payroll rows fill `TRAN_USAG`, legacy rows fill `WDRL_ACCT_RMRK`. After JEX→refactor migration the column populated for the "remarks" differs by origin → split data, broken downstream display/dispatch that reads `WDRL_ACCT_RMRK`. Direct violation of the legacy-first migration-safety rule.
- **Legacy ref:** `pymn_0005_002_r001_act.jsp:341,357,623`; `_c001_act.jsp` `copyFrom(recItem)`. **Refactor ref:** `PayrollPaymentExcelParser.java:227,359`; `PayrollPaymentService.java:492`.
- **Recommended fix:** map payroll Remarks to `WDRL_ACCT_RMRK` on parse + save (parser output field, DTO field, detail write, and the web preview/`parsedToWire`). Keep `TRAN_USAG` null to match legacy. **Coordinated API+WEB change.**

### G2 — `TAR_BANK_CD` stored as typed code, not resolved 6-digit `BANK_CD` (HIGH)
- **Root cause:** Refactor resolver returns `resolvedBankCd = trimmed`; save writes it verbatim. Legacy `getBankCode("CITAD", code)` resolves to the 6-digit `BANK_CD`.
- **Impact:** CITAD rows persist a 3-digit value where legacy persists 6-digit → migration mismatch and dispatch/bank-routing ambiguity.
- **Legacy ref:** `pymn_0005_002_c001_act.jsp:126,162-185`. **Refactor ref:** `PayrollPaymentExcelParser` bank resolver; `PayrollPaymentService.java` detail `setTarBankCd`.
- **Recommended fix:** resolve CITAD 3-digit → 6-digit `BANK_CD` via `TbFirmBankCodeRepository` before persisting `TAR_BANK_CD` (mirror legacy). Decide jointly with G3.

### G3 — `ANT_DIV_CD` network not forced to CITAD (HIGH)
- **Root cause:** Refactor preserves the parsed network; legacy hardcodes `CITAD` for every payroll detail row.
- **Impact:** Refactor can write `ANT_DIV_CD="NAPAS"` (for 6-digit codes), a state legacy never produces → dispatch route divergence + migration drift. Legacy effectively treats payroll as CITAD-only.
- **Legacy ref:** `pymn_0005_002_c001_act.jsp:118,125`. **Refactor ref:** `PayrollPaymentService.java` detail `setAntDivCd`.
- **Recommended fix:** **PO decision required** — either (a) force CITAD on payroll save to match legacy exactly, or (b) confirm NAPAS payroll is intentionally supported in the refactor. Until decided, this is structural drift; default to matching legacy (force CITAD).

### G4 — Payroll VCB prohibition not enforced (HIGH)
- **Root cause:** Refactor payroll parser/save has no VCB rule; `dispatch()` only runs `CutoffCheck` (timing tiers), not the absolute prohibition.
- **Impact:** A VCB-source VND payroll, or VCB-USD to a non-VCB target, is **accepted** by the refactor where legacy blocks it at upload (PO mapping 2026-06-16) — a real money-movement risk.
- **Legacy ref:** `pymn_0005_002_c001_act.jsp:128-142` (`WBE0000`). **Refactor ref:** `PayrollPaymentService.java:1028-1035` (cutoff only); no `VCB_BANK_CD` in the payroll parser.
- **Recommended fix:** add the VCB-VND / VCB-USD-to-non-VCB guard to the payroll upload save (preferred, matches legacy timing) and/or dispatch. Depends on G2 (needs resolved 6-digit `TAR_BANK_CD` to compare `970436`).

### G5 — `MAX_ROWS=500` silently truncates (HIGH)
- **Root cause:** `lastRow = min(getLastRowNum(), FIRST_DATA_ROW+MAX_ROWS-1)` drops rows 501+ with no error.
- **Impact:** Payroll files (often hundreds of employees) over 500 rows silently under-pay; legacy processes up to 1000 (DOM) / unlimited (xlsx SAX).
- **Legacy ref:** `pymn_0005_002_r001_act.jsp:510-512` (1000) + uncapped SAX. **Refactor ref:** `PayrollPaymentExcelParser.java:82,131`.
- **Recommended fix:** raise the cap to match legacy (≥1000) and/or **error** on exceedance instead of silently truncating. Apply consistently across the shared parsers (also affects vendor/expense/internal).

### G6 — Blank-row `break` vs legacy skip-and-continue (MEDIUM)
- **Root cause:** Refactor `break`s on the first all-blank row; legacy `continue`s.
- **Impact:** A blank separator row mid-file silently drops all subsequent rows in the refactor.
- **Legacy ref:** `pymn_0005_002_r001_act.jsp:255-260,533-537`. **Refactor ref:** `PayrollPaymentExcelParser.java:139-148`.
- **Recommended fix:** change `break` to skip-and-continue to match legacy (shared parser change — verify cross-impact).

### G7 — Refresh semantics divergence (MEDIUM)
- **Root cause:** Legacy Refresh = generic `ErpDataRefreshHelper.refreshErpData` ERP pull for all tenants; refactor reimplemented Refresh as HAENGSUNG-only Oracle sync gated to product `H`, replacing the Upload button.
- **Impact:** Non-`H` tenants lose the legacy ERP-refresh; for `H` tenants the button replaces Upload (UX divergence). For the Haengsung reference deployment the mapping is acceptable; for generic tenants there is no equivalent.
- **Legacy ref:** `pymn_0005_001.ts:164-183,1638-1665`; `pymn_0005_002_c002_act.jsp:61-71,110`. **Refactor ref:** `PaymentScheduledBatchListPage.tsx:571,692,1418-1422`; `useHaengsungRefresh`; `HaengsungPaymentRefreshController`.
- **Recommended fix:** PO decision — document HAENGSUNG-only Refresh as intended scope (likely, given the reference deployment) and confirm no generic-tenant ERP-refresh requirement. Treat as documented divergence unless a generic refresh is required.

### G8 — Currency whitelist enforced at parse, not save (LOW)
- Refactor `{VND,USD}` at parse (`WBE2120`); legacy at save (`CcyCd.fromValue`). Net effect similar; different timing/code. **Fix:** accept as benign, or align error point if strict parity required.

### G9 — Single-network guard only in refactor (LOW)
- Refactor blocks mixed NAPAS/CITAD at parse (`WBE2121`); legacy has no parse guard (forces CITAD at save). Resolve together with G3. **Fix:** if G3 forces CITAD, the refactor guard becomes moot or should align.

### G10 — Withdrawal dropdown filter/default divergence (LOW / VERIFY)
- Refactor FE fetches accounts without `ccy_cd` and auto-selects the first; legacy currency-filters and auto-selects the file-currency match. **Fix/verify:** pass `ccy_cd` filter and align default selection; confirm `WithdrawalAccountListService` parity (shared with internal-transfer V1).

### T1 — No parser / save unit tests (MEDIUM, test debt)
- Add tests for `PayrollPaymentExcelParser` (header, 8-column map, CITAD/NAPAS, guards, max-rows, blank-row) and `doCreateDraft` (master/detail mapping incl. G1–G4, type `"00"`, ERP `"12"`, audit slug, key-race).

---

## 11. Recommended Fix Plan (pending approval — no code changed)

> **Historical (pre-fix) plan.** This is the original proposed plan as written before approval. Implementation outcome and final status are in §13 / §13.1. Tier-3 item "G7 Confirm HAENGSUNG-only Refresh scope (PO)" is **resolved — By Design / Closed** (HAENGSUNG-only Refresh is approved product scope); G8/G9 are **Closed**. Do not read this section as the current to-do list.

**Tier 1 — data-migration & money-safety (do first, coordinated API+WEB):**
1. **G1** Remarks → `WDRL_ACCT_RMRK` (parser output, DTO, detail write, web preview + `parsedToWire`).
2. **G2 + G3 + G9** Bank/network: resolve `TAR_BANK_CD` to 6-digit and **force `ANT_DIV_CD=CITAD`** on payroll save to match legacy — **PO confirmation on CITAD-only policy first**.
3. **G4** Add VCB-VND / VCB-USD-to-non-VCB guard to payroll upload save (after G2).
4. **G5** Raise/align row cap and error (don't silently truncate) — assess shared-parser impact.

**Tier 2 — behavior parity:**
5. **G6** Blank-row skip-and-continue.
6. **Recipient-name tone-strip** (verify requirement) — add `replaceTone` to payroll `RCPT_NM`/Remarks if confirmed.
7. **G10** Withdrawal dropdown currency filter + default selection.

**Tier 3 — documented divergences / decisions:**
8. **G7** Confirm HAENGSUNG-only Refresh scope (PO).
9. **G8 / I3** Accept as intended (whitelist timing; refactor audit logging) — document.
10. **T1** Add parser + save tests covering all of the above.

**Migration-safety note:** G1–G4 are the load-bearing items — each changes what is written to `SCDUL_PYMN_SUMR_DETL` and must land before any production payroll data is migrated. None require a schema change (all target existing legacy columns); the work is Java/TS mapping + a PO decision on the CITAD-only network policy (G3).

---

## 12. Verification Commands

```bash
# API — payroll tests (baseline; no code changed in this audit)
cd "c:/WECMS PROJECT/wecms-api"
./gradlew test --tests "*Payroll*"

# WEB — only if/when frontend fixes are implemented
cd "c:/WECMS PROJECT/wecms-web"
npm run type-check
npm run lint
```

- **API test run:** executed as a read-only baseline during this audit — **`BUILD SUCCESSFUL in 5s`** (all `*Payroll*` tests green). Note T1: existing payroll tests cover dispatch/result/approval-attachment only — **no** parser or save-draft coverage, so a green run does **not** exercise any gap in this audit.
- **WEB type-check/lint:** not run — this audit changed no frontend code. Run after implementing G1/G10.

---

## 13. Implementation Result (2026-06-29)

**Scope approved (Tier 1 batch):** **G1, G2, G4, G5** + **G3 (force CITAD per legacy parity)**. At the time of this batch, **G6 / G10** were not yet scheduled (landed in batch 2 below) and **G7 / G8 / G9** were not yet dispositioned. Their **final status is in §13.1** — G6 & G10 Fixed, G7 By Design / Closed, G8 Accepted / Closed, G9 Closed. No unrelated payment module (Vendor / Expense / Internal Transfer) was modified.

### What changed

| Gap | Status | Change |
|---|---|---|
| **G1** | ✅ Fixed | **Save:** `PayrollPaymentService.doCreateDraft` now writes Remarks to `SCDUL_PYMN_SUMR_DETL.WDRL_ACCT_RMRK` (`detl.setWdrlActRmrk(...)`) and leaves `TRAN_USAG` null — legacy parity. **Reads:** payroll detail (`detail()` → shared mapper 3-arg overload) and result (`PayrollPaymentResultService`) now source Remarks from `WDRL_ACCT_RMRK`, coalescing to `TRAN_USAG` so legacy-migrated and any pre-fix refactor rows still render. The wire field stays `tran_usag`, so **no `wecms-web` change was needed.** |
| **G2** | ✅ Fixed | `doCreateDraft` resolves the typed CITAD 3-digit code to the 6-digit `BANK_CD` via `TbFirmBankCodeRepository.findFirstByAntDivCdAndCitadBankCd("CITAD", …)` before persisting `TAR_BANK_CD` (legacy `getBankCode` CITAD branch). Registry miss falls back to the typed code (safer than legacy's empty-string result — documented). |
| **G3** | ✅ Fixed | `doCreateDraft` forces `ANT_DIV_CD = "CITAD"` on every detail row (legacy `pymn_0005_002_c001_act.jsp:118,125`). **NAPAS payroll remains unsupported pending a PO decision** — if PO approves NAPAS payroll, relax this + G2 together. |
| **G4** | ✅ Fixed | `doCreateDraft` enforces the VCB prohibitions at upload save: VCB-source + VND → `WBE_PAYROLL_VCB_VND_NOT_SUPPORTED`; VCB-source + USD to a non-VCB resolved target → `WBE_PAYROLL_VCB_USD_OTHER_BANK`. Throws inside the transaction → full rollback (legacy `WbRollbackException` parity). New `VCB_BANK_CD = "970436"` constant. |
| **G5** | ✅ Fixed | `PayrollPaymentExcelParser` cap raised 500 → **1000** (legacy DOM value), and the silent `Math.min(...)` truncation replaced with a **reject** (`WBE_PAYROLL_TOO_MANY_ROWS`) on the (MAX_ROWS+1)-th non-blank data row. Counting only contiguous non-blank rows means trailing blank/formatted rows don't trip a false positive. |
| **G6** | ✅ Fixed (2026-06-29, batch 2) | `PayrollPaymentExcelParser` blank-row handling changed from `break` to `continue` — a mid-file blank separator no longer drops every row after it (legacy `pymn_0005_002_r001_act.jsp:255-260` skips and keeps going). |
| **G10** | ✅ Fixed (2026-06-29, batch 2) | `PayrollPaymentUploadDialog` now currency-filters the withdrawal-account dropdown to the parsed file currency, auto-selects the matching account, and never auto-selects an unrelated-currency account (legacy `pymn_0005_002.ts:238-245`). Frontend-only. |
| **G7** | ✅ By Design / Closed | HAENGSUNG-only Refresh is approved product scope (HAENGSUNG Oracle Integration), not a Payroll-upload parity defect. No code change. See §13.1. |
| **G8** | ✅ Accepted / Closed | Refactor's parse-time `{VND, USD}` whitelist is stricter & safer than legacy's save-time check. No code change. See §13.1. |
| **G9** | ✅ Closed | G3's force-`ANT_DIV_CD=CITAD` on save already restored legacy save-path parity; mixed-network parse guard is no longer a remaining risk. No code change. See §13.1. |

### New error codes (`messages.properties`)
- `WBE_PAYROLL_TOO_MANY_ROWS` — "The payroll file has too many rows. Split it so each upload has at most 1000 employees."
- `WBE_PAYROLL_VCB_VND_NOT_SUPPORTED` — "Payroll does not support VCB VND transfer."
- `WBE_PAYROLL_VCB_USD_OTHER_BANK` — "VCB does not support USD transfer to other banks."

### Files touched (all `wecms-api`)
- `domain/payment/payrollpayment/service/PayrollPaymentExcelParser.java` — G5.
- `domain/payment/payrollpayment/service/PayrollPaymentService.java` — G1 (save + detail read), G2, G3, G4; new `firmBankCodeRepo` field + `VCB_BANK_CD` constant + `resolveCitadBankCd()` / `payrollRemarks()` helpers.
- `domain/payment/payrollpayment/service/PayrollPaymentResultService.java` — G1 (result read).
- `domain/payment/common/TransferLineDetailMapper.java` — **additive** 3-arg `toLineDetail(...)` overload (Remarks source override); the existing 2-arg form delegates to it unchanged, so Vendor/Expense/Internal behaviour is byte-identical.
- `src/main/resources/messages.properties` — 3 new codes.

### Tests added (T1 partially closed)
- `PayrollPaymentExcelParserTest` (4 tests) — G5 reject at 1001 rows, accept at exactly 1000, CITAD happy-path keeps the typed code at parse, blank-row break.
- `PayrollPaymentUploadSaveTest` (4 tests) — G1 (`WDRL_ACCT_RMRK` set, `TRAN_USAG` null) + G2 (6-digit resolve) + G3 (CITAD forced) in one save; G4 VCB-VND blocked, VCB-USD-to-other blocked, VCB-USD-to-VCB allowed.

### Verification
```
cd "c:/WECMS PROJECT/wecms-api" && ./gradlew test --tests "*Payroll*"
→ BUILD SUCCESSFUL — PayrollPaymentExcelParserTest 4/4, PayrollPaymentUploadSaveTest 4/4,
  plus all pre-existing *Payroll* tests, 0 failures / 0 errors.
```
`wecms-web` `type-check` / `lint` not run — no frontend file changed (the G1 wire contract is unchanged; only backend column mapping moved).

### Batch 2 (2026-06-29) — G6 + G10

**Scope approved:** G6 + G10 only. G7 not changed; G8/G9 not changed (not required by the implementation).

**G6 — blank-row handling (backend, parser):**
- `PayrollPaymentExcelParser` parse loop now iterates to `sheet.getLastRowNum()` and **`continue`s** past an all-blank critical-cell row instead of `break`ing. Rows after a mid-file blank separator are parsed again — legacy parity (`pymn_0005_002_r001_act.jsp:255-260` skips and keeps going). The G5 cap still counts only contiguous non-blank rows, so trailing blank/formatted rows don't trip a false positive.
- Test updated: `PayrollPaymentExcelParserTest.skips_blank_row_and_keeps_parsing_later_rows` (was `blank_data_row_breaks_the_loop`) — workbook with data / blank / data now asserts **2** rows parsed.

**G10 — withdrawal account vs. file currency (frontend only):**
- `PayrollPaymentUploadDialog.tsx`:
  - Tracks the parsed file currency (`fileCcyCd`) from the first success row's `trnf_ccy_cd`.
  - **Currency-filters** the withdrawal-account dropdown to accounts whose `ccy_cd` matches the file currency (legacy `comm_1001_001` is currency-filtered). Falls back to the full list only when no account matches, so the picker is never mysteriously empty.
  - **Auto-selects** the matching-currency account after parse; if the current selection's currency differs and a match exists, it switches; if **no** account matches, it **clears** the selection (Save stays disabled) rather than keep an unrelated first account.
- No backend change: with currency-matching enforced, the FE-sent `ccy_cd` (= file currency) already equals the selected account's currency, so the backend's `WDRL_CCY_CD = ccy_cd` fallback stays correct.

**Files touched (batch 2):**
- `wecms-api/.../payrollpayment/service/PayrollPaymentExcelParser.java` — G6.
- `wecms-api/.../payrollpayment/service/PayrollPaymentExcelParserTest.java` — G6 test.
- `wecms-web/components/payment/payroll/PayrollPaymentUploadDialog.tsx` — G10.

**Verification (batch 2):**
```
cd "c:/WECMS PROJECT/wecms-api" && ./gradlew test --tests "*Payroll*"
→ BUILD SUCCESSFUL — PayrollPaymentExcelParserTest 4/4, PayrollPaymentUploadSaveTest 4/4, 0 failures.

cd "c:/WECMS PROJECT/wecms-web" && npx tsc --noEmit   → exit 0 (no "type-check" script; tsc used directly)
                                  && npx eslint components/payment/payroll/PayrollPaymentUploadDialog.tsx → exit 0
```
`npm run lint` over the whole repo still reports 23 pre-existing errors in unrelated files (`lib/i18n/LocaleProvider.tsx`, `lib/nxisas/nxisas_cardTranList.ts`, `public/assets/settings/_write_icons.js`) — none in the changed dialog, which lints clean.

### 13.1 Final Disposition (2026-06-29)

The three remaining "divergence" gaps are reclassified as closed. None is a Payroll Excel Upload parity defect; each is either approved product scope or a deliberate, safer choice. The original findings (§1, §9, §10, §11) are preserved unchanged as the pre-fix record — this subsection is the authoritative current status.

#### G7 — Refresh → **By Design / Closed**

Refresh is intentionally **HAENGSUNG-only** because the current refactor follows the **HAENGSUNG Oracle Integration** scope. Legacy had a generic ERP Refresh (`ErpDataRefreshHelper.refreshErpData`) for all tenants; the refactor Refresh is the **HAENGSUNG Oracle Refresh for Product Type `H` only** (`POST /api/v1/payments/haengsung/refresh`, `payment_type=PAYROLL`). This is an **approved product-scope difference**, not a Payroll Excel Upload parity defect — the Excel Upload path itself is at full parity, and Refresh is a separate, deliberately product-gated feature. No code change.

References (HAENGSUNG Oracle Integration scope):
- [haengsung-payment-refresh.md](../features/haengsung-payment-refresh.md)
- [haengsung-oracle-integration-roadmap.md](../features/haengsung-oracle-integration-roadmap.md)
- [haengsung-oracle-integration-final-audit.md](../features/haengsung-oracle-integration-final-audit.md)

#### G8 — Currency whitelist → **Accepted / Closed**

The refactor validates currency **earlier** than legacy and allows only **VND / USD** (`UploadConsistencyValidator` at parse, `WBE2120`), where legacy relied on `CcyCd.fromValue` at save. This is **stricter and safer** — an invalid currency is rejected at preview rather than at save. No further implementation is required unless the PO specifically requests exact legacy error timing. No code change.

#### G9 — Single-network guard → **Closed**

Tier 1 already restored legacy save-path parity by forcing Payroll save to `ANT_DIV_CD = CITAD` (G3). With the network pinned to CITAD on save, the refactor's mixed-network parse guard (`assertSingleNetwork`, `WBE2121`) is no longer a remaining Payroll save-path risk — it is, at worst, an extra defensive check at preview. No code change.

#### Final status table

| Gap | Disposition | Mechanism |
|---|---|---|
| **G1** | **Fixed** | Remarks persist to `WDRL_ACCT_RMRK` (save + detail/result reads) |
| **G2** | **Fixed** | `TAR_BANK_CD` resolved to 6-digit `BANK_CD` on save |
| **G3** | **Fixed** | `ANT_DIV_CD` forced to `CITAD` on save |
| **G4** | **Fixed** | VCB-VND / VCB-USD-to-non-VCB prohibition enforced at upload save |
| **G5** | **Fixed** | Row cap 1000 + reject (`WBE_PAYROLL_TOO_MANY_ROWS`); no silent truncation |
| **G6** | **Fixed** | Blank-row `break` → `continue` (rows after a mid-file blank are parsed) |
| **G7** | **By Design / Closed** | HAENGSUNG-only Refresh = approved product scope (not a parity defect) |
| **G8** | **Accepted / Closed** | Stricter parse-time VND/USD whitelist (safer than legacy) |
| **G9** | **Closed** | Subsumed by G3 force-CITAD on save |
| **G10** | **Fixed** | Withdrawal dropdown currency-filtered + auto-selects matching account |

**No open / PO-blocker items remain on the Payroll Excel Upload parity path.**

### Follow-ups for the PO (optional, non-blocking)
- **G3 / NAPAS payroll (informational):** the current behaviour is CITAD-only, matching legacy. If a future business need requires NAPAS payroll, that is a new feature request (would relax G2 + G3) — it is **not** an outstanding parity gap.

---

### Appendix — Evidence index
- Templates compared via xlsx XML extraction (sharedStrings + sheet1, rows 1–6), all 6 files — content identical; header row 4 = `Bank Code*` … `Reporting Account`.
- Legacy: `pymn_0005_001.ts`, `pymn_0005_002.ts`, `pymn_0005_002_r001_act.jsp`, `pymn_0005_002_c001_act.jsp` (read firsthand: `:58-160`), `pymn_0005_002_c002_act.jsp`; enums `ScdulPymnTypeCd`(PAYROLL="00"), `PymnType`(PAYR), `ErpTypeCd`(PAYROLL_SCHEDULED_PAYMENT="12").
- Refactor API: `PayrollPaymentController`, `PayrollPaymentExcelParser` (read firsthand: `:78-110,131,227,343-359`), `PayrollPaymentService` (read firsthand: `:492,1009-1035`), `ScdulPymnTypeCd`(PAYROLL="00").
- Refactor WEB: `ScheduledPaymentPage.tsx`, `PayrollPaymentUploadDialog.tsx` (`:38-42,109-118,239-255`), `PaymentExcelUploadDialog.tsx` (`:250,266,571,692,1418-1422`), `lib/api/payrollPayment.ts`, `lib/payment/payrollType.ts`, `hooks/useHaengsungRefresh.ts`.

---

## 14. Payroll Template Replacement Re-check

- **Date:** 2026-06-29 · **Scope:** `wecms-web` public Payroll template files only — inspection only, **no code or template changed**.
- **Trigger:** verify the (re-)placed Payroll Excel templates are still structurally compatible with the frontend download path, the `PayrollPaymentExcelParser` header expectation, and the legacy Payroll template structure recorded in §4.

### 14.1 Files inspected

| # | File | Exists | Size (bytes) |
|---|---|---|---|
| 1 | `wecms-web/public/templates/payroll/WeCMS_Payroll_Payment_upload_EN.xlsx` | ✅ | 35,684 |
| 2 | `wecms-web/public/templates/payroll/WeCMS_Payroll_Payment_upload_KO.xlsx` | ✅ | 36,396 |
| 3 | `wecms-web/public/templates/payroll/WeCMS_Payroll_Payment_upload_VI.xlsx` | ✅ | 36,406 |

Sizes are **unchanged** from the original §4 audit snapshot (EN 35,684 / KO 36,396 / VI 36,406) — the re-placed templates introduced no structural drift.

### 14.2 References checked against

- **Frontend path / filename:** `PayrollPaymentUploadDialog.tsx:38-42` `TEMPLATE_URL` → `/templates/payroll/WeCMS_Payroll_Payment_upload_{EN,KO,VI}.xlsx`.
- **Parser header expectation:** `PayrollPaymentExcelParser.java` — `HEADER_ROW = 3` (0-based; sheet row 4), `FIRST_DATA_ROW = 4` (0-based; sheet row 5), `HEADERS_{EN,KO,VI}` = 8 prefixes, validated positionally per column via `startsWith(EN || KO || VI)` (`:210-227`); any blank / missing / non-matching header column → `WBE2018`.
- **Legacy structure:** §4 Template Audit (8 columns, merged `A1:H2` title, hint row 3, header row 4, sample row 5; sheets `["Payroll","NOTE"]`).

### 14.3 Verification results

| # | Check | Result | Detail |
|---|---|---|---|
| 1 | EN / KO / VI files exist | ✅ Pass | all three present (§14.1) |
| 2 | Frontend paths match exactly | ✅ Pass | filenames === `TEMPLATE_URL` map values, byte-for-byte |
| 3 | Sheet name | ✅ Pass | `["Payroll","NOTE"]` — first sheet `Payroll` (parser reads sheet 1) |
| 4 | Title row | ✅ Pass | merged `A1:H2` — EN `Payroll` / KO `급여 지급` / VI `Thanh toán lương` |
| 5 | Header row index | ✅ Pass | header at sheet row 4 = parser `HEADER_ROW=3` (0-based) |
| 6 | First data row index | ✅ Pass | sample at sheet row 5 = parser `FIRST_DATA_ROW=4` (0-based) |
| 7 | Column count | ✅ Pass | 8 columns (A–H) = `HEADERS_EN.length` (8) |
| 8 | Column order | ✅ Pass | Bank Code · Deposit Bank · Deposit Account No. · Recipient Name · Transfer Amount · Transfer Currency · Remarks · Reporting Account |
| 9 | Required columns | ✅ Pass | `*`-marked Bank Code / Deposit Account No. / Recipient Name / Transfer Amount / Transfer Currency align with the parser's required cells (cols 0,2,3,4,5) |
| 10 | Sample row does not break parsing | ✅ Pass | row 5 `616 / ShinhanBank / 700071152854 / NNT… / 3000000 / VND / … / 2004002` parses without throwing; trailing blank row 6 is skipped (G6 `continue`). Unregistered code/chart in a given tenant would flag the row, not break the parse. |
| 11 | Headers match `PayrollPaymentExcelParser.java` | ✅ Pass | every column's header cell `startsWith` its locale prefix (see §14.4) |
| 12 | No extra / missing / reordered columns | ✅ Pass | exactly 8, in parser order, none added or dropped |
| 13 | New PO template compatible with parser | ✅ Pass | header validation, row index, column set, and sample row all satisfy the parser contract |

### 14.4 Per-locale header comparison (sheet row 4 → parser prefix)

| Col | Parser prefix (EN / KO / VI) | EN template cell | KO template cell | VI template cell | Match |
|---|---|---|---|---|---|
| A | `Bank Code` / `은행코드` / `Mã ngân hàng` | `Bank Code*` | `은행코드 *` | `Mã ngân hàng *` | ✅ |
| B | `Deposit Bank` / `입금은행` / `Ngân hàng nhận tiền` | `Deposit Bank ` | `입금은행` | `Ngân hàng nhận tiền` | ✅ |
| C | `Deposit` / `입금 계좌번호` / `Số tài khoản nhận tiền` | `Deposit Account No.*` | `입금 계좌번호 *` | `Số tài khoản nhận tiền*` | ✅ |
| D | `Recipient Name` / `수신자명` / `Tên người nhận` | `Recipient Name *` | `수신자명 *` | `Tên người nhận *` | ✅ |
| E | `Transfer Amount` / `이체 금액` / `Số tiền chuyển khoản` | `Transfer Amount *` | `이체 금액 *` | `Số tiền chuyển khoản  *` | ✅ |
| F | `Transfer Currency` / `이체통화` / `Loại tiền chuyển` | `Transfer Currency *` | `이체통화 *` | `Loại tiền chuyển *` | ✅ |
| G | `Remarks` / `적요` / `Nội dung giao dịch` | `Remarks (max 150 letters)` | `적요 (최대 150자)` | `Nội dung giao dịch (tối đa 150 ký tự)` | ✅ |
| H | `Reporting Account` / `보고서 계정` / `Tài khoản báo cáo` | `Reporting Account` | `보고서 계정` | `Tài khoản báo cáo` | ✅ |

(The parser accepts EN **or** KO **or** VI prefix per column, so each locale file is also cross-locale tolerant; here every cell matches its own locale prefix.)

### 14.5 Differences found

**None.** All three files match the frontend path, the parser header contract, and the legacy §4 structure. No extra, missing, or reordered columns; no sheet/title/row-index drift; sample row parses.

### 14.6 Conclusion

**PASS (EN, KO, VI).** The re-placed Payroll Payment Excel templates are fully compatible with `PayrollPaymentExcelParser` and the `PayrollPaymentUploadDialog` download path. No code, template, or follow-up change is required.

> **Tenant-data caveat (informational, not a template defect):** the sample row's bank code `616` (CITAD ShinhanBank) and reporting account `2004002` must exist in the uploading tenant's `TB_FIRM_BANK_CODE` / `CHART_ACCT_INFM` to parse error-free. In a tenant lacking those rows the sample line is flagged as a data error (not a parse break) — expected behaviour, identical to legacy. The template ships the sample for illustration only; users replace it with their own rows.

---

## Recipient Name / Lookup Recipient Legacy Parity Audit

> Added 2026-06-30. Focused parity audit of how **Recipient Name** (`RCPT_NM`) is populated for Payroll and whether the **Lookup Recipient** API (legacy `ckRecName`) participates, including the **HAENGSUNG Refresh** path. Evidence-based; **no runtime logic changed**.

### 1. Legacy behavior summary

Payroll is the **only** payment type that carries a **Recipient Name column in the Excel template** (col 3, `Recipient Name *`). The name is **typed by the user** and validated only — there is **no contact resolution, no account-master lookup, and no Lookup Recipient bank inquiry**:

- Parse (`pymn_0005_002_r001_act.jsp`, inline `importDataFromExcel`): col 3 → `RCPT_NM`, **required** (blank → `WBE0001`), with Vietnamese tone-strip (`StringUtils.replaceTone`) + `SpecialCharValidator(..., PAYROLL_PAYMENT, RECIPIENT_NAME, ...)` + `checkValidStringInMax(required, 100)`.
- Payroll has **no employee master** and **no Contact Code** — recipients are identified only by `RCPT_NM` + `FINC_MDA_NO`; the same person/account may legitimately repeat across rows.
- `ckRecName` ([`PymnHelper.java:860-1040`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)) is **NOT** called for Payroll.

### 2. Refactor behavior summary

`PayrollPaymentExcelParser` reads col 3 into `rcptNm` ([`PayrollPaymentExcelParser.java:243`](../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentExcelParser.java)), enforces **required** (`:254-256`), runs `SpecialCharValidator` (`:357-360`), and forwards the trusted cell value to save → `RCPT_NM` (`:374`). No contact, account, or inquiry lookup — at parity with legacy. The one minor divergence already recorded in §5: the **Vietnamese tone-strip is not ported** (special-char validation is, the `replaceTone` normalization is not) — cosmetic, not a source/parity gap. **HAENGSUNG Refresh** sources the name from Oracle `TB_TRNS_DETAIL.ACET_NM` (tone-stripped) → `RCPT_NM` (Payroll routed by `FILE_GB='AC2'`; [`ErpPaymentRefreshMasterImporter.java:140`](../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/haengsung/service/ErpPaymentRefreshMasterImporter.java)), flag-gated OFF (`HAENGSUNG_ORACLE_ENABLED=false`).

### 3. Recipient Name source comparison

| Source | Legacy | Refactor | Match |
|---|---|---|---|
| Contact / saved recipient (`TRCO_*`) | ❌ never | ❌ never | ✅ |
| Bank Account / account master (`ACCT_MSTR`) | ❌ never | ❌ never | ✅ |
| Lookup Recipient API (`ckRecName` / NAPAS inquiry) | ❌ never | ❌ never | ✅ |
| EFS / VAN / WeCMS Batch | ❌ not in this path | ❌ not in this path | ✅ |
| **Manual Excel cell (col 3, required)** | ✅ **the source** | ✅ **the source** | ✅ |
| HAENGSUNG ERP Refresh (`TB_TRNS_DETAIL.ACET_NM`) | n/a (legacy ERP import) | ✅ implemented, flag OFF | ⚠️ (env) |

### 4. Lookup Recipient condition

| Network | Legacy | Refactor |
|---|---|---|
| NAPAS | never | never |
| CITAD | never | never |

Payroll has **no Lookup Recipient condition** — the API is never reached on either side. (Payroll forces the network to CITAD; recipient name is still the typed cell regardless.)

### 5. Contact vs Bank Account source rules

**Neither — manual user input.** Payroll does not consult a contact or the account master for the recipient name. The value is the typed Excel cell, validated for required/special-chars only. Both systems agree.

### 6. EFS / VAN / WeCMS Batch dependency status

**None for recipient name.** Payroll's recipient name is the typed cell, resolved entirely client-/parse-side before any dispatch. The EFS/VAN/WeCMS Batch dispatch engine (code-complete, flags OFF, mock clients throw) carries the value forward but never sources it. The HAENGSUNG Refresh alternative sources it from Oracle and is independent of EFS/VAN.

### 7. Gap list

- **No gap** in the Recipient Name *source* (manual cell, parity) or Lookup Recipient (never called, parity).
- Minor: Vietnamese **tone-strip not ported** for `RCPT_NM`/Remarks (recorded in §5 / fix-plan item 6) — cosmetic normalization, verify-if-required; not a source/parity blocker.

### 8. Final verdict: **PASS**

Recipient Name source (manual Excel cell) and Lookup Recipient behavior (never invoked) are at legacy parity. The only open item is the optional VN tone-strip normalization, which does not affect where the name comes from.

### 9. Environment / verifiability note

The Excel-Upload recipient path is **fully verifiable in the current environment** — it has no EFS/VAN/WeCMS Batch dependency (the name is a typed, validated cell). The HAENGSUNG Refresh recipient path is implemented but requires the HAENGSUNG Oracle connection (`HAENGSUNG_ORACLE_ENABLED=false`) to verify end-to-end; the standard Excel-Upload path is unaffected and verifiable now.

---

### 10. Lead Engineer disposition (2026-06-30, documentation-only)

**Decision: no runtime code changed.** Payroll recipient handling is at parity for the *source* (manual Excel cell → `RCPT_NM`); the one open item is normalization.

- **Vietnamese tone-strip — 🔶 PO CONFIRMATION REQUIRED.** Legacy strips Vietnamese tones from the typed `RCPT_NM` (`StringUtils.replaceTone`); the refactor stores the cell as-typed and validates special-chars only ([`PayrollPaymentExcelParser.java:243,360,374`](../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/payrollpayment/service/PayrollPaymentExcelParser.java)). This is a normalization difference on a free-text display field (not a key), reproducible today in all environments (no EFS/flag dependency). **Tone-strip is lossy for real names**, so whether to port it is a product decision — **awaiting PO (Mr. Jung) confirmation**. If confirmed-required, the fix is a single `removeVietnameseTones(...)` call (the helper already exists in `ErpPaymentRefreshMasterImporter`). Until confirmed, **leave as-is**.
- **Recipient source (manual cell, required), Preview→Save, persistence — ✅ no action** (code-verified: read col 3 `:243` → required `:255` → line `:374` → save once `PayrollPaymentService.java:507`, no overwrite; `RCPT_NM VARCHAR(100)` nullable).
- **HAENGSUNG Refresh `ACET_NM`→`RCPT_NM` (`ErpPaymentRefreshMasterImporter.java:140`, `FILE_GB='AC2'`) — ✅ no action** (implemented; flag-gated OFF `HAENGSUNG_ORACLE_ENABLED=false`; verification awaits Oracle connection).

> **Classification (Lead Engineer):** tone-strip = **⚠ Legacy behavior intentionally not ported (PO confirmation required)**. Everything else = **✅ No action required**. **No runtime files were changed in this audit.**

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

# Expense Payment — Scheduled Payment — Excel Upload / Refresh — Legacy Parity Audit

- **Feature:** Payment → Expense Payment → Scheduled Payment → Excel Upload / Refresh
- **Slug:** `expense-payment`
- **Legacy WSVC:** `pymn_0003_001` (list), `pymn_0003_002` (upload popup), `pymn_0003_002_r001` (parse/preview), `pymn_0003_002_c001` (save), `comm_1001_001` (withdrawal-account dropdown)
- **System tags:** JEX (source of truth) ↔ Refactor (`wecms-api` + `wecms-web`)
- **Date:** 2026-06-29
- **Status:** **Fixes implemented & verified 2026-06-29** (G1–G8 landed; R1 documented as not-a-bug). See §12 Implementation Result. The audit body (§1–§11) is preserved as the pre-fix record.
- **Method:** Analyze → Compare with legacy → Root cause → Impact → (this document) → approval → implement.

> **Scope note:** This audit covers the *Excel Upload* path only (download template → parse/preview → save draft batch) plus the **Refresh** button that appears on the same Scheduled Payment list. Downstream Dispatch, Approval, Result, and payment-line Matching are referenced only where the upload save-path feeds them. Every table touched is in the legacy-table-inventory / PROD Active Table List (`SCDUL_PYMN_SUMR`, `SCDUL_PYMN_SUMR_DETL`, `TB_FIRM_BANK_CODE`, `TRCO_INFM`, `TRCO_ACCT_INFO`, `CHART_ACCT_INFM`, `ACCT_MSTR`, `ERP_TRNS_HSTR`, `AUDIT_LOGS_INFM`).

> **Sibling audit:** This is the Expense-Payment counterpart of [`internal-transfer-excel-upload-audit.md`](internal-transfer-excel-upload-audit.md). Internal Transfer (`pymn_0009_*`) and Expense Payment (`pymn_0003_*`) share the same `SCDUL_PYMN_SUMR(_DETL)` tables, the same shared API DTOs/entities, and the same shared frontend `PaymentExcelUploadDialog`/`ExcelUploadDialog`. The gaps differ because **Expense Payment pays a registered *contact*** (via `HAND_TRCO_KEY` / `TRCO_KEY`), whereas Internal Transfer pays an own account. The contact-identity column set is the source of this audit's most severe finding (G1 below).

---

## 1. Summary

The refactor reproduces the legacy Expense-Payment Excel Upload flow **faithfully at the parse/preview layer** — the `ExpensePaymentExcelParser` is fully implemented (not a stub) and matches legacy `PymnHelper.importDataFromExcel(..., PymnType.EXPENSE_PAYMENT)` closely: 10-column header validation, column order, required fields, currency whitelist, single-currency / single-network guards, Contact-Code lookup against `TRCO_INFM`, deposit-account validation against `TRCO_ACCT_INFO`, ChartAcct lookup, and field-length caps **enforced at preview** (better than the internal-transfer parser).

The template files are at **full parity** — all six (`WeCMS_Expense_Payment_upload_{EN,KO,VI}.xlsx`, JEX vs `wecms-web/public/templates/expense-payment/`) carry an identical **10-column** header row; the only byte difference (EN) is a cosmetic CRLF normalization inside a comment/hint cell, not a column change.

The real divergences are in the **save path** (`ExpensePaymentService.doCreateDraft` ⟷ frontend `parsedToWire` vs legacy `PymnHelper.uploadPaymentExcel(..., "pymn_0003_002_c001")`), and they matter for **legacy data-migration parity and downstream display/dispatch**, not the preview UX:

| # | Severity | Gap |
|---|---|---|
| **G1** | **HIGH** | **Contact identity dropped on save.** The frontend `parsedToWire` never forwards `trco_key` / `hand_trco_key` / `trco_cmnm` / `trco_cmenm`, even though the parse response carries them, the wire type defines them, and the backend save mapper writes them. Result: `SCDUL_PYMN_SUMR_DETL.TRCO_KEY/HAND_TRCO_KEY/TRCO_CMNM/TRCO_CMENM` persist **null** on every refactor Expense row. Legacy persists all four. This is the contact-payment identity — the defining column set of Expense Payment. |
| **G2** | **HIGH** | CITAD rows: `TAR_BANK_CD` saved as the raw **3-digit CITAD code**, not resolved to the 6-digit `BANK_CD`. Legacy `getBankCode` resolves it via `TB_FIRM_BANK_CODE`. (Parser line 360/364/367 all set `resolvedBankCd = trimmed`.) |
| **G3** | **HIGH** | `SCDUL_PYMN_SUMR_DETL.CITAD_BANK_CD` is **always null** in the refactor (parser passes positional `null`, "Phase 2"). Legacy stores the 3-digit CITAD code for CITAD rows and `"000"` for NAPAS rows. |
| **G4** | **MEDIUM** | `SCDUL_PYMN_SUMR.WDRL_CCY_CD` stores the **transfer currency**, not the **withdrawal-account currency**. Legacy stores the account currency (`input.CCY_CD`). |
| **G5** | **MEDIUM** | **No save-side re-validation.** Legacy `pymn_0003_002_c001` re-runs `ckDepositNo` / `ckContactCode` / `ckRecName` at save (→ `WBE0404`); the refactor `createDraft` trusts the frontend-filtered success rows and validates only at parse. Combined with G1, a save request with no contact keys is accepted without re-derivation. |
| **G6** | LOW | VCB-USD → other-bank block enforced at **dispatch** (`ExpenseDispatchExecutor`) but **not at upload save** (legacy enforces at save, `WBE0000`). Net business effect is similar (blocked before money moves) but the enforcement point and error timing differ; depends on G2 (needs resolved 6-digit `TAR_BANK_CD`). |
| **G7** | LOW | NAPAS recipient-name bank verification (`ckRecName` → `FirmBankingInquiryService.inquiryAccount`) is **stubbed** — returns `Optional.empty()` → silent no-op. Legacy also skips silently when the inquiry returns null, so behaviour matches *today*, but the real cross-check is not implemented. |
| **G8** | LOW (doc) | Stale class-level Javadoc: `ExpensePaymentController` (~line 60) and `ExpensePaymentService` (~line 94) describe detail/create/dispatch/request-approval as "Phase B / subsequent MRs"; they are fully implemented. |
| **R1** | INFO | **Refresh** on the Scheduled Payment list is a **HAENGSUNG-only Oracle sync** (`useHaengsungRefresh`, `payment_type="EXPENSE"`, `PRDT_TYPE='H'`). It has **no legacy JEX counterpart** in the standard tenant flow — it is a refactor/HAENGSUNG addition, not a parity item. |
| **V1** | VERIFY | Withdrawal Bank/Account dropdown filter parity vs legacy `comm_1001_001` — delegated to the shared `WithdrawalAccountListService`, not re-read in this audit (same delegate as Internal Transfer's V1). |

Everything else — template files, header text, column order, required-field set, error/complete counts, currency whitelist, single-network guard, ChartAcct lookup, recipient-name CITAD cross-fill, contact + deposit-account lookup at parse, ERP-history write (`TRNS_TYPE="03"`), `SCDUL_PYMN_TYPE_CD="02"`, draft status (`TRNF_YN="N"`) — is at parity.

---

## 2. Legacy Code Trace

### 2.1 Screen + popup
- List page: `pymn_0003_001_view.jsp` (`WECMS DEV/WECMS/web/view/jex/wabooks/pymn/`) — Scheduled Expense Payment list (type `02`); "Excel Upload" button opens popup `pymn_0003_002`.
- Upload popup: `pymn_0003_002_view.jsp` + `pymn_0003_002.ts` (`WECMS_STATIC/ts-src/js/jex/wabooks/pymn/`) — 10-column preview table, withdrawal account dropdown (from `comm_1001_001`), Download template, Browse, Upload.
- File uploader: `jexjs.plugin<JexSingleFileUploader>("upload","pymn_0003_002_r001",{ name:"FILE_DATA" })`.

### 2.2 Template download
- Static assets under `WECMS DEV/WECMS_STATIC/web/file/excel/VN/`:
  - `WeCMS_Expense_Payment_upload_{EN,KO,VI}.xlsx` (upload templates)
  - `WeCMS_Expense_Payment_download_{EN,KO,VI}.xlsx` (separate **download/export** templates — not used by the upload flow)
- `pymn_0003_002.ts:274-277` `fnDownloadExcelForm` sets `#download-excel` `href` = `file/excel/VN/WeCMS_Expense_Payment_upload_{LANG}.xlsx?<ts>`. `LANG = jexjs.getLang()`. VN directory only. No server generation — byte-static files.

### 2.3 Parse / preview — `pymn_0003_002_r001_act.jsp`
- Reads `FILE_DATA` (+ `validateFile`), `BANK_CD` (withdrawal bank), `pymnType = PymnType.EXPENSE_PAYMENT`.
- `PymnHelper.importDataFromExcel(file, fileName, util, result, idoCon, loginUser, bankCode, pymnType)` → `PymnHelper.java:303-602`.
- Header validation: row index **3** (0-based; spreadsheet row 4), prefix-match against `HEADER_TITLE_*` per locale; empty header → `WBE0001`.
- Row loop from row 4, capped at **500**; **early-break** when cols 0,1,3,7,8,9 are all blank.
- **Column map (10 columns, data rows):**

  | Col | Field | Legacy validation |
  |---|---|---|
  | 0 | `TAR_BANK_CD` + `ANT_DIV_CD` | empty → `WBE1013`; len 3 → CITAD, len 6 → NAPAS, else `WBE1013`; `getListTbFirmBankCode` count ≥ 1 else `WBE0001` |
  | 1 | `FINC_MDA_NO` | required; `checkValidStringInMax(...,100)` |
  | 2 | `TAR_BANK_NM` | display-only, from `getBankName` |
  | 3 | `TRAN_AMT` | `checkValidAmountOnlyPositive_` (strictly > 0) |
  | 4 | `TRNF_CCY_CD` | required; len 3; **VND/USD only** (`WBW0001`/`WBE0001`); single currency per file → `WBW1160` |
  | 5 | `TRAN_USAG` (Remarks) | VN tone-strip (`replaceTone`); `SpecialCharValidator` (`WBE0000`); `checkValidStringInMax(...,200)` |
  | 6 | `CHART_ACCT_CD` (Reporting Account) | optional; `getChartAcctInfm` active lookup → fills `CHART_ACCT_CD/SEQ_NO/NM_*`; blank → no error |
  | 7 | `HAND_TRCO_KEY` (**Contact Code**) | required; `ckContactCode` — contact must exist; fills `TRCO_KEY/TRCO_CMNM/TRCO_CMENM`; max 20 |
  | 8 | `NOTE` (Description) | `checkValidStringInMax(...,200)` |
  | 9 | `DOC_NO` (Document No.) | `checkValidStringInMax(...,50)` |
- Recipient name (lines 517-567): NAPAS → `ckRecName` (account-system lookup); CITAD → `getfincMdaOwnerNmFromAcctInfo` (`TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`); VN tone-strip + `SpecialCharValidator` on `RCPT_NM`; CITAD blank → `WBE0001`.
- Output (`PymnHelper.java:599-602`): `TOT_CNT = suc+err`, `SUC_CNT`, `ERR_CNT`, `REC[]` — each row carries `TRAN_AMT, ANT_DIV_CD, RCPT_NM, TAR_BANK_CD, TAR_BANK_NM, FINC_MDA_NO, TRAN_USAG, HAND_TRCO_KEY, TRCO_KEY, DOC_NO, NOTE, CITAD_BANK_CD, TRCO_CMENM, TRCO_CMNM, CHART_ACCT_CD, CHART_ACCT_SEQ_NO, CHART_ACCT_NM_*, TRNF_CCY_CD`; errors packed via `DatImpWizHelper.putError` into `ERR_FLD` / `ERR_FLD_CAUS` pipe arrays.

### 2.4 Save — `pymn_0003_002_c001_act.jsp` → `PymnHelper.uploadPaymentExcel(...,"pymn_0003_002_c001")`
`PymnHelper.java:1301-1484`. Input required: `TITL`, `FILE_DATA`, `BANK_CD`, `ACCT_NO`, `CCY_CD`, `TRNF_CCY_CD`, and each `REC.FINC_MDA_NO`.
- **Master `SCDUL_PYMN_SUMR`** (lines 1311-1347):
  - `SCDUL_PYMN_TYPE_CD = ScdulPymnTypeCd.EXPENSE = "02"`
  - `OUT_BANK_CD = NapasBankInfo.napasBankCd` (resolved from `BANK_CD`)
  - `WDRL_ACT_NO = ACCT_NO`
  - `CCY_CD = TRNF_CCY_CD` (transfer currency)
  - **`WDRL_CCY_CD = CCY_CD`** (the **withdrawal-account currency** — distinct from transfer currency)
  - `TOT_TRNS_CNT = REC.size`, `TOTAL_TRAN_AMT = Σ TRAN_AMT` (re-set after loop), `TRNF_YN = NOT_YET_TRANFER ('N')`, fileInfo, regUser, `REG_DTM = utcNow`.
- **Per-row detail `SCDUL_PYMN_SUMR_DETL`** (lines 1356-1472) — Expense path **does NOT skip** the deposit/contact/recipient re-checks (only `pymn_0009_002_c001` internal transfer skips them):
  - `copyFrom(recItem)` → `TRNF_CCY_CD`, `TRAN_USAG`, `NOTE`, `DOC_NO`, `CHART_ACCT_CD`, `CHART_ACCT_SEQ_NO`
  - `ANT_DIV_CD = CITAD|NAPAS`
  - **`TAR_BANK_CD = getBankCode(util, idoCon, ANT_DIV_CD, recItem.TAR_BANK_CD)`** → CITAD: 3-digit → 6-digit `BANK_CD`; NAPAS: passthrough.
  - **`CITAD_BANK_CD`** = 3-digit code (CITAD) / `"000"` (NAPAS).
  - `FINC_MDA_NO`, `RCPT_NM` (VN tone-strip), **`TRCO_KEY`**, **`HAND_TRCO_KEY`**, **`TRCO_CMNM`**, **`TRCO_CMENM`**
  - **Save-side re-validation:** `ckDepositNo` (count > 0 else `WBE0404`), `ckContactCode` (exists & matches `TRCO_KEY/TRCO_CMNM/TRCO_CMENM` else `WBE0404`), `ckRecName` (NAPAS) / `TRCO_ACCT_INFO` re-fetch (CITAD, non-blank else `WBE0404`).
  - **VCB-USD guard** (lines 1460-1465): `OUT_BANK_CD==VCB('970436') && TRNF_CCY_CD=='USD' && TAR_BANK_CD!=VCB` → `WBE0000`.
  - regUser, `REG_DTM = utcNow`.
- After loop: `TOTAL_TRAN_AMT = Σ`, update master.
- **ERP history** (line 1480): `ErpTrnsHisHelper.erpTrnsHisUploader(..., ErpTypeCd.EXPENSE_SCHEDULED_PAYMENT = "03")`.
- **Audit log:** **none** on this path (legacy `PymnHelper` upload writes no `AUDIT_LOGS_INFM` row).

### 2.5 Withdrawal Bank / Account dropdown — `comm_1001_001`
`WSVC.comm_1001_001.xml` — "Withdraw Bank/Account(FirmBanking) TextBox". Input `CCY_CD`; output `REC[]` (`BANK_CD, NAPAS_BANK_CD, ACCT_NO, ACCT_NICK_NM, BAL, CCY_CD, BANK_NM, ACCT_TYPE_CD, …`). `pymn_0003_002.ts:582-643` filters by `CCY_CD` when provided; account-type map 03/04/07→Current, 05/06/08→Savings; label `${BANK_NM} ${ACCT_NO} (${ACCT_TYPE} ${CCY_CD})`.

---

## 3. Refactor Code Trace

### 3.1 Web (`wecms-web`)
- Page: [`ExpenseScheduledPaymentPage`](../../../../wecms-web/components/payment/expense-payment/) → route [`/payment/expense-payment/scheduled-payment`](<../../../../wecms-web/app/(dashboard)/payment/expense-payment/scheduled-payment/page.tsx>).
- Upload dialog: [`ExpensePaymentUploadDialog.tsx`](../../../../wecms-web/components/payment/expense-payment/ExpensePaymentUploadDialog.tsx) — wraps shared [`PaymentExcelUploadDialog`](../../../../wecms-web/components/payment/shared/PaymentExcelUploadDialog.tsx) → [`ExcelUploadDialog`](../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx).
  - **10 columns** (lines 122-133): `ANT_DIV_CD` (Bank Code), `FINC_MDA_NO` (Deposit No.), `TAR_BANK_NM` (Bank), `TRAN_AMT` (Transfer Amount), `TRNF_CCY_CD` (Transfer Currency), `TRAN_USAG` (Remarks), `CHART_ACCT_CD` (Reporting Account), `HAND_TRCO_KEY` (**Contact Code**), `NOTE` (Description), `DOC_NO` (Document No.).
  - Template URLs (lines 41-45): `/templates/expense-payment/WeCMS_Expense_Payment_upload_{EN,KO,VI}.xlsx`; KM→EN fallback (`TEMPLATE_URL[locale] ?? TEMPLATE_URL.EN`, line 105).
  - Per-cell error mapping (lines 229-242): pipe-split `err_fld`/`err_fld_caus` → `causeByField` map → each `cell()` carries `{ value, error, errorMessage }`.
  - Counters: `completedCount = response.suc_cnt`, `errorCount = response.err_cnt`.
  - **Upload enable condition:** Upload requires `selectedAccount` set (throws otherwise, lines 150-152) AND `errorCount === 0`.
  - Withdrawal dropdown `WithdrawalAccountDropdown` (lines 295-391): `fetchExpenseWithdrawalAccounts()` on mount, auto-selects first; label `${bank_nm} ${acct_no} (${acctTypeLabel} ${ccy_cd})`; account-type map 03/04/07→Current else Savings; **no client-side currency/type filter**.
- **`handleUpload`** (lines 148-174): filters `successLines` (no `err_fld`), maps via `parsedToWire`, builds `ApiCreateScheduledTransferRequest`:
  - `titl = filename (ext stripped)`, `bank_cd/acct_no = selectedAccount`, `ccy_cd = trnf_ccy_cd = batchCcyCd` (`successLines[0].trnf_ccy_cd ?? selectedAccount.ccy_cd ?? "VND"`).
  - **`parsedToWire` (lines 260-281)** forwards: `finc_mda_no, ant_div_cd, rcpt_nm, tar_bank_cd, tran_amt, trnf_ccy_cd, tran_usag, chart_acct_cd, chart_acct_seq_no, note, doc_no, citad_bank_cd, depo_ccy_cd`. **Does NOT forward `trco_key`, `hand_trco_key`, `trco_cmnm`, `trco_cmenm`** (see G1).
- Refresh: [`hooks/useHaengsungRefresh.ts`](../../../../wecms-web/hooks/useHaengsungRefresh.ts) — HAENGSUNG-only (`payment_type="EXPENSE"`); calls `haengsungPaymentRefresh({ payment_type, period_date })`; non-HS tenants render "Excel Upload" instead (see R1).
- API client: [`lib/api/expensePayment.ts`](../../../../wecms-web/lib/api/expensePayment.ts) — `parseExpensePaymentExcel` (POST `/uploads/parse`, multipart `file_data` + `bank_cd`), `createExpensePayment` (POST root, multipart `file_data` + JSON `request`), `fetchExpenseWithdrawalAccounts` (GET `/withdrawal-accounts`).
- Wire types: [`lib/types/api.ts`](../../../../wecms-web/lib/types/api.ts) — `ApiParsedTransferLine` (carries `trco_key/hand_trco_key/trco_cmnm/trco_cmenm`), `ApiTransferLineInput` (**also defines all four**, lines 2106+), `ApiCreateScheduledTransferRequest`, `ApiWithdrawalAccountRow`. Shared with Internal Transfer.

### 3.2 API (`wecms-api`)
- Controller: [`ExpensePaymentController.java`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java) — class `@PreAuthorize("@menuAuthz.canAccess('payment-expense-scheduled')")`.
  - `POST /api/v1/payment/expense-payment/uploads/parse` (`@RequestPart file_data`, `@RequestParam bank_cd`)
  - `POST /api/v1/payment/expense-payment` (multipart `request` JSON + optional `file_data`) — save draft
  - `GET /api/v1/payment/expense-payment/withdrawal-accounts` (optional `ccy_cd`)
  - `GET /api/v1/payment/expense-payment/{id}/file` (download original Excel)
- Parse: `ExpensePaymentService.parseExcel` → validates envelope (tenant, non-empty, `.xls/.xlsx`) → `ExpensePaymentExcelParser.parse(file, bankCd)` (NOT a stub).
- Parser: [`ExpensePaymentExcelParser.java`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentExcelParser.java)
  - `HEADER_ROW=3`, `FIRST_DATA_ROW=4`, `MAX_ROWS=500`; early-break on cols 0,1,3,7,8,9.
  - 10-column header prefix-match against `HEADERS_EN/KO/VI` → `WBE2018` mismatch; format failure → `WBE2017`.
  - Per-row: Deposit No. required; **Contact Code required + `TRCO_INFM` lookup → cross-fill `TRCO_KEY/TRCO_CMNM/TRCO_CMENM`** (max 20); Bank Code len 3/6 + `TB_FIRM_BANK_CODE` lookup; deposit-account validation vs `TRCO_ACCT_INFO` (+ CITAD `RCPT_NM` cross-fill from `FINC_MDA_OWNER_NM`); NAPAS recipient verify via `FirmBankingInquiryService.inquiryAccount` (**stubbed** → no-op, G7); amount > 0; currency required + whitelist + single-currency (`WBE2120`) + single-network (`WBE2121`); Reporting Account `CHART_ACCT_INFM` lookup (max 20); `SpecialCharValidator` on Remarks + `RCPT_NM`; length caps **flagged at parse** (Remarks 200 / Contact 20 / Reporting 20 / Note 200 / Doc 50).
  - **`resolvedBankCd = trimmed`** on every branch (lines 360/364/367) → CITAD stays 3-digit (G2).
  - **`citadBankCd` = positional `null`** (line 575, "Phase 2") (G3).
  - Output `ParsedTransferLine` carries `resolvedTrcoKey`, `resolvedTrcoCmnm`, `resolvedTrcoCmenm`, `handTrcoKey`, resolved chart-acct, `rcptNm`, `trnfCcyCd`.
- Save: `ExpensePaymentService.doCreateDraft` (`ExpensePaymentService.java:414-566`) — `transactionalRetry.runOnce` (one-shot key-race retry).
  - Master (446-476): `SCDUL_PYMN_TYPE_CD = ScdulPymnTypeCd.EXPENSE.code() = "02"`; `OUT_BANK_CD = outBankCdNormalizer.normalize(bank_cd)`; `WDRL_ACT_NO = acct_no`; **`CCY_CD = req.ccyCd()`**; **`WDRL_CCY_CD = req.ccyCd()`** (G4); `TRNF_YN = NOT_TRANSFERRED ('N')`; `ERP_PROC_CMPL_YN='N'`; `TOT_TRNS_CNT`, `TOTAL_TRAN_AMT` (summed); file fields; audit `REG_*`/`MOD_*` (UTC `yyyyMMddHHmmss`).
  - Detail (502-531): maps `TRAN_AMT, TRNF_CCY_CD (default req.trnfCcyCd), ANT_DIV_CD, RCPT_NM, TAR_BANK_CD (=line.tarBankCd, raw — G2), FINC_MDA_NO, TRAN_USAG, TRCO_KEY (=line.trcoKey()), HAND_TRCO_KEY, DOC_NO, NOTE, CITAD_BANK_CD (=line.citadBankCd(), null — G3), TRCO_CMNM, TRCO_CMENM, CHART_ACCT_SEQ_NO, CHART_ACCT_CD, DEPO_CCY_CD`; `SCDUL_PYMN_DETL_NO` seeded via `wb_get_next_val` then incremented in Java.
  - **No save-side re-validation** of contact/deposit/recipient (G5).
  - ERP history: `ERP_TRNS_TYPE_EXPENSE_SCHEDULED_PAYMENT = "03"` (REQUIRES_NEW, non-blocking).
  - Audit log: `AUDIT_LOGS_INFM` with `WSVC_ID = "expense-payment"` (slug), `REF_NO = "SCDUL_PYMN_SUMR"`.
- DTOs/entities (shared with Internal Transfer): `TransferLineInput` (**defines `trcoKey/handTrcoKey/trcoCmnm/trcoCmenm`** at lines 50/53/67/70), `ParsedTransferLine`, `CreateScheduledTransferRequest`, `ScdulPymnSumr(Detl)` (+ `@IdClass`). Key gen via `LegacyFunctionRepository.nextScdulPymnNo`.
- Dispatch (downstream): `ExpenseDispatchExecutor` — isolated Expense engine; non-VND file path includes a VCB USD→other-bank reject (see G6).

---

## 4. Template Audit

Verified by extracting the actual header row (spreadsheet row 4 = parser 0-based `HEADER_ROW=3`) from `xl/worksheets/sheet1.xml` of all six files, in column order.

| Aspect | Legacy (JEX `WECMS_STATIC/.../excel/VN/`) | Refactor (`wecms-web/public/templates/expense-payment/`) | Match? | Note |
|---|---|---|---|---|
| Template files | `WeCMS_Expense_Payment_upload_{EN,KO,VI}.xlsx` | Same 3 files | ✅ | KO + VI **byte-identical**; EN byte-differs |
| EN byte difference | `\n` line-ending in hint cell | `\r\n` in same hint cell | ⚠️ cosmetic | Comment/hint cell only (spreadsheet row 3 helper text); **header labels identical**; no functional effect |
| Column count | 10 (`dimension ref="A1:J…"`) | 10 (`A1:J…`) | ✅ | |
| Header row | spreadsheet row 4 (index 3) | row 4 (`HEADER_ROW=3`) | ✅ | |
| Header text (EN) | `Bank Code *` · `Deposit No.*` · `Bank` · `Transfer Amount *` · `Transfer Currency *` · `Remarks (200 letters)` · `Reporting Account` · `Contact Code *` · `Description` · `Document No.` | **Identical** | ✅ | byte-for-byte across all 10 cols |
| Header text (KO) | `은행코드 *` · `입금 계좌번호 *` · `은행` · `이체 금액 *` · `이체통화 *` · `적요 (200자)` · `보고서 계정` · `거래처 코드 *` · `내용` · `문서번호` | **Identical** | ✅ | |
| Header text (VI) | `Mã ngân hàng *` · `Số tài khoản nhận tiền*` · `Ngân hàng` · `Số tiền chuyển khoản *` · `Lọai tiền chuyển *` · `Nội dung giao dịch (200 ký tự)` · `Tài khoản báo cáo` · `Mã đối tác *` · `Diễn giải` · `Số chứng từ` | **Identical** | ✅ | |
| Parser header expectation | EN/KO/VI prefix-match, row 3, 10 cols | identical `HEADERS_EN/KO/VI` | ✅ | refactor parser matches the template exactly |
| Header validation failure | `WBE0001` | `WBE2018` | ⚠️ code differs | refactor uses its own code namespace (by design) |
| Server-generated template | No (static) | No (static) | ✅ | |
| KM locale | falls back to EN | `TEMPLATE_URL[locale] ?? EN` | ✅ | |
| Separate `_download_` template | Yes (`WeCMS_Expense_Payment_download_*` for export) | Not present in web (upload-only) | ℹ️ | download/export template is a separate JEX file, **not** part of the upload flow; no parity action needed |

**Conclusion:** Template layer is at **full parity**. The earlier suspicion that the EN template lacked Transfer Currency / Reporting Account was a `sharedStrings.xml` dedup-ordering artifact — the actual worksheet header row is 10 identical columns in all six files.

---

## 5. Excel Read & Validation Audit

| Rule | Legacy | Refactor | Match? | Gap |
|---|---|---|---|---|
| Data start row | row 4 | row 4 (`FIRST_DATA_ROW`) | ✅ | |
| Max rows | 500 | 500 (`MAX_ROWS`) | ✅ | |
| Early break | cols 0,1,3,7,8,9 all blank | same | ✅ | |
| Bank Code network derive | len 3→CITAD, 6→NAPAS, else `WBE1013` | len 3→CITAD, 6→NAPAS, else `FLD_ANT_DIV_CD` | ✅ | |
| Bank Code existence | `getListTbFirmBankCode` count ≥ 1 | `TbFirmBankCodeRepository` (`findByAntDivCdAndBankCd` / `…AndCitadBankCd`) | ✅ | |
| Deposit No. required | required, ≤ 100 | required | ✅ | |
| **Contact Code required** | `ckContactCode`, `TRCO_INFM` lookup, fills `TRCO_KEY/CMNM/CMENM` | required, `TrcoInfmRepository` lookup, cross-fills same | ✅ | parse-side parity (but dropped on save — G1) |
| Contact Code length | ≤ 20 | ≤ 20 flagged at parse | ✅ | |
| Deposit account vs contact | `TRCO_ACCT_INFO (biz_key, trco_key, finc_mda_no)` bank-identity match | same lookup + `bankIdentity` match | ✅ | miss → `FLD_FINC_MDA_NO`/`FLD_ANT_DIV_CD` |
| CITAD recipient name | `getfincMdaOwnerNmFromAcctInfo` → `RCPT_NM` | cross-fill `RCPT_NM` from `FINC_MDA_OWNER_NM` | ✅ | |
| NAPAS recipient verify | `ckRecName` account-system lookup | `FirmBankingInquiryService.inquiryAccount` | ⚠️ G7 | refactor service **stubbed** → no-op (legacy also no-op when null) |
| Transfer Amount | strictly > 0 | `signum() < 0` rejected | ⚠️ | legacy strictly > 0; refactor wording is "non-negative" — confirm 0 handling (likely matches via blank/positive checks) |
| Transfer Currency | required, len 3, VND/USD only | required; whitelist via `assertCurrencyWhitelist` | ✅ | per-row vs file-level granularity differs, outcome same |
| Single currency / file | `WBW1160` | `assertSingleCurrency` → `WBE2120` | ✅ | |
| Mixed network / file | save-side `WBE1191` | parse-side `assertSingleNetwork` → `WBE2121` | ✅ | refactor enforces earlier (at parse) |
| Remarks special chars | `SpecialCharValidator` + VN tone strip | `SpecialCharValidator` (no explicit tone strip) | ⚠️ | VN tone-strip not ported; low impact |
| Remarks length | ≤ 200 at preview | ≤ 200 at parse | ✅ | refactor parser flags it (unlike internal-transfer parser) |
| Reporting Account | `getChartAcctInfm` active-only | `findByBizKeyAndChartAcctCdAndDelYn(..,'N')`; ≤ 20 | ✅ | |
| Description (Note) | ≤ 200 at preview | ≤ 200 at parse | ✅ | |
| Document No. | ≤ 50 at preview | ≤ 50 at parse | ✅ | |
| Error/complete counts | `TOT_CNT`,`SUC_CNT`,`ERR_CNT` | `tot_cnt`,`suc_cnt`,`err_cnt` | ✅ | |
| Per-cell error packing | `err_fld`/`err_fld_caus` pipe arrays | same (`buildErrPair`) | ✅ | FE splitter unchanged |

---

## 6. Withdrawal Bank / Account No. Audit

| Aspect | Legacy | Refactor | Match? |
|---|---|---|---|
| Source | `comm_1001_001` (`pymn_0003_002.ts:582`) | `GET /withdrawal-accounts` → `WithdrawalAccountListService.list(ccyCd)` | ⚠️ V1 verify |
| Auto-select first | yes | yes (`useEffect`) | ✅ |
| Account label | bank + acct + type + ccy | `${bank_nm} ${acct_no} (${acctTypeLabel} ${ccy_cd})` | ✅ |
| Account-type label map | 03/04/07→Current, 05/06/08→Savings | identical map | ✅ |
| Drives parse `bank_cd` | withdrawal bank → cut-off + special-char rule | `selectedAccount.bank_cd` passed to parse | ✅ |
| Currency filter | filters `REC[]` by `CCY_CD` when provided | passes optional `ccy_cd`; **no client-side filter** | ⚠️ verify backend filters by `ccy_cd` |
| Save uses account | `BANK_CD`,`ACCT_NO`,`CCY_CD` | `bank_cd`,`acct_no`; **`ccy_cd` overwritten with transfer ccy** | ⚠️ G4 |

**V1 — to verify:** Confirm `WithdrawalAccountListService` filters to firm-banking-enrolled current/savings accounts exactly as `comm_1001_001` (delegated, not re-read; same delegate as Internal Transfer).

---

## 7. Upload Save DB Audit

### `SCDUL_PYMN_SUMR` (master)

| Column | Legacy value | Refactor value | Match / Gap |
|---|---|---|---|
| `BIZ_KEY` | `loginUser.bizKey` | `TenantContext.bizKey` | ✅ |
| `SCDUL_PYMN_NO` | key gen | `keyGenerator.nextScdulPymnNo` | ✅ |
| `SCDUL_PYMN_TYPE_CD` | `'02'` EXPENSE | `'02'` EXPENSE | ✅ |
| `OUT_BANK_CD` | `NapasBankInfo.napasBankCd` | `outBankCdNormalizer.normalize(bank_cd)` | ✅ (verify normalizer == NAPAS lookup) |
| `WDRL_ACT_NO` | `ACCT_NO` | `acct_no` | ✅ |
| `CCY_CD` | `TRNF_CCY_CD` (transfer ccy) | `req.ccyCd()` (= transfer ccy from FE) | ✅ |
| `WDRL_CCY_CD` | `CCY_CD` (**account ccy**) | `req.ccyCd()` (**transfer ccy**) | ❌ **G4** |
| `TOTAL_TRAN_AMT` | Σ after loop | Σ upfront | ✅ |
| `TOT_TRNS_CNT` | `REC.size` | `lines.size` | ✅ |
| `TRNF_YN` | `'N'` | `'N'` | ✅ |
| `ERP_PROC_CMPL_YN` | (default) | `'N'` | ✅ |
| `ORG/SAVE_FILE_NM`, `FILE_SIZE` | fileInfo | sanitised name + size | ✅ |
| `REG_*`/`MOD_*` | regUser/utcNow | userId / UTC `yyyyMMddHHmmss` | ✅ |

### `SCDUL_PYMN_SUMR_DETL` (per line)

| Column | Legacy value | Refactor value | Match / Gap |
|---|---|---|---|
| `SCDUL_PYMN_DETL_NO` | seq | `wb_get_next_val` seed + increment | ✅ |
| `TRAN_AMT` | `TRAN_AMT` | `tran_amt` | ✅ |
| `TRNF_CCY_CD` | `TRNF_CCY_CD` | `trnf_ccy_cd` (default req.trnfCcyCd) | ✅ |
| `ANT_DIV_CD` | CITAD/NAPAS | CITAD/NAPAS | ✅ |
| `RCPT_NM` | CITAD: `TRCO_ACCT_INFO`; NAPAS: bank | resolved at parse, forwarded | ✅ |
| `TAR_BANK_CD` | **`getBankCode` → 6-digit `BANK_CD`** | **raw typed code** (CITAD = 3-digit) | ❌ **G2** |
| `CITAD_BANK_CD` | CITAD: 3-digit; NAPAS: `"000"` | **null** (always) | ❌ **G3** |
| `FINC_MDA_NO` | `FINC_MDA_NO` | `finc_mda_no` | ✅ |
| `TRAN_USAG` | Remarks | Remarks | ✅ |
| **`TRCO_KEY`** | from `ckContactCode` | mapper writes `line.trcoKey()` — but FE sends **null** | ❌ **G1** |
| **`HAND_TRCO_KEY`** | typed contact code | mapper writes `line.handTrcoKey()` — FE sends **null** | ❌ **G1** |
| **`TRCO_CMNM`** | from contact master | mapper writes `line.trcoCmnm()` — FE sends **null** | ❌ **G1** |
| **`TRCO_CMENM`** | from contact master | mapper writes `line.trcoCmenm()` — FE sends **null** | ❌ **G1** |
| `CHART_ACCT_SEQ_NO`/`CD` | from `getChartAcctInfm` | resolved seq + code | ✅ |
| `DEPO_CCY_CD` | (legacy fill) | from parse | ✅ |
| `NOTE`/`DOC_NO` | Note/DocNo | note/doc_no | ✅ |
| `REG_*`/`MOD_*` | regUser/utcNow | userId / UTC | ✅ |

### Other tables
- `ERP_TRNS_HSTR` — both write the uploaded file as an ERP-history row (legacy `ErpTypeCd.EXPENSE_SCHEDULED_PAYMENT = "03"`; refactor `ERP_TRNS_TYPE = "03"`). ✅
- `AUDIT_LOGS_INFM` — refactor writes `WSVC_ID="expense-payment"` (slug, per CLAUDE.md identifier rule). Legacy writes **none** on this path. Acceptable divergence (refactor enhancement).
- VCB-USD guard — legacy blocks at **save** (`WBE0000`); refactor blocks at **dispatch** (`ExpenseDispatchExecutor`), not at upload save (G6).

---

## 8. Legacy vs Refactor Comparison Table

| Area | Legacy behavior | Refactor behavior | Match? | Gap / Risk |
|---|---|---|---|---|
| Template files | `WeCMS_Expense_Payment_upload_{EN,KO,VI}.xlsx` | same (KO/VI byte-identical, EN cosmetic CRLF) | ✅ | — |
| Template columns / headers | 10 cols, row 4, EN/KO/VI | identical 10 cols | ✅ | — |
| Parser | `importDataFromExcel(EXPENSE)` | `ExpensePaymentExcelParser` (real, not stub) | ✅ | — |
| Required fields | BankCode, FINC_MDA_NO, TRAN_AMT, TRNF_CCY_CD, **Contact Code** | same | ✅ | — |
| Bank Code validation | len 3/6 + `TB_FIRM_BANK_CODE` | same | ✅ | — |
| Contact lookup | `ckContactCode` → fills TRCO_* | parser cross-fills TRCO_* | ✅ (parse) | ❌ dropped on save (G1) |
| Deposit-account vs contact | `TRCO_ACCT_INFO` | same | ✅ | — |
| Transfer Currency | VND/USD; single/file | whitelist + single/file | ✅ | granularity differs |
| Field-length caps | flagged at preview | flagged at parse | ✅ | refactor parses caps (parity) |
| Withdrawal Bank/Account | `comm_1001_001` | `WithdrawalAccountListService` | ⚠️ | **V1** verify |
| Success/error counts | TOT/SUC/ERR | tot/suc/err | ✅ | — |
| Upload button state | enabled when file + no errors | disabled when errors / no account / busy | ✅ | — |
| Save table/columns | `SCDUL_PYMN_SUMR(_DETL)` | same | ⚠️ | **G1–G4** column-value drift |
| Contact identity columns | persisted | **null** (FE drops 4 fields) | ❌ | **G1** |
| CITAD bank-code resolution | 6-digit | 3-digit raw | ❌ | **G2** |
| `CITAD_BANK_CD` | 3-digit / "000" | null | ❌ | **G3** |
| `WDRL_CCY_CD` | account ccy | transfer ccy | ❌ | **G4** |
| Save-side re-validation | `ckDepositNo`/`ckContactCode`/`ckRecName` → `WBE0404` | none (trusts FE) | ❌ | **G5** |
| VCB-USD guard | at save (`WBE0000`) | at dispatch | ⚠️ | **G6** (point/timing differs) |
| NAPAS recipient verify | `ckRecName` (no-op if null) | `FirmBankingInquiryService` (stubbed) | ⚠️ | **G7** (matches today, not real) |
| Type/status defaults | `'02'`, `TRNF_YN='N'` | same | ✅ | — |
| ERP history | `"03"` | `"03"` | ✅ | — |
| Audit log | none | `WSVC_ID="expense-payment"` | ➕ | refactor enhancement |
| Refresh button | none (Excel Upload only) | HAENGSUNG Oracle sync (`PRDT_TYPE='H'`) | ℹ️ | **R1** no legacy counterpart |

---

## 9. Gaps / Bugs Found

### G1 — Contact identity (`TRCO_KEY`/`HAND_TRCO_KEY`/`TRCO_CMNM`/`TRCO_CMENM`) dropped on save (HIGH)
- **Root cause:** `ExpensePaymentUploadDialog.parsedToWire` (`ExpensePaymentUploadDialog.tsx:260-281`) maps only 13 fields and **omits all four contact-identity fields**, even though: (a) the parse response `ApiParsedTransferLine` carries them; (b) the wire input type `ApiTransferLineInput` defines them (`lib/types/api.ts:2106+`); (c) the backend DTO `TransferLineInput` defines them (lines 50/53/67/70); and (d) the backend save mapper writes them from `line.trcoKey()` / `handTrcoKey()` / `trcoCmnm()` / `trcoCmenm()`. With the frontend sending `undefined`/null, the backend persists null.
- **Impact:** Every refactor Expense-Payment detail row has `TRCO_KEY = HAND_TRCO_KEY = TRCO_CMNM = TRCO_CMENM = NULL`. Expense Payment **is** a contact payment — the contact key is the recipient identity. This breaks: (1) **legacy → refactor data-migration parity** (legacy persists all four); (2) the detail/result screens that render the contact name/code (will show "—"); (3) any downstream dispatch / matching keyed on the contact. NAPAS *and* CITAD rows are affected. This is the defining Expense-Payment column set, hence HIGH.
- **Legacy ref:** `PymnHelper.java:1387-1422` (writes `TRCO_KEY/HAND_TRCO_KEY/TRCO_CMNM/TRCO_CMENM`). **Refactor ref:** `parsedToWire` omission; `ExpensePaymentService.doCreateDraft` detail map already reads `line.trcoKey()` etc.

### G2 — CITAD `TAR_BANK_CD` not resolved to 6-digit `BANK_CD` (HIGH)
- **Root cause:** `ExpensePaymentExcelParser` sets `resolvedBankCd = trimmed` on every branch (lines 360/364/367). For CITAD that is the 3-digit CITAD code. Legacy `getBankCode` resolves CITAD's 3-digit → the 6-digit `BANK_CD` via `TB_FIRM_BANK_CODE`. NAPAS is passthrough in both.
- **Impact:** `SCDUL_PYMN_SUMR_DETL.TAR_BANK_CD` holds a 3-digit value for CITAD Expense rows vs a 6-digit `BANK_CD` in legacy. Breaks migration parity for the column and any downstream dispatch/matching keyed on a 6-digit bank code (the VCB-USD guard itself assumes the resolved 6-digit code). NAPAS-only tenants unaffected.
- **Same finding as Internal Transfer G1.** **Legacy ref:** `PymnHelper.java:1451` + `getBankCode` (`1232-1257`). **Refactor ref:** `ExpensePaymentExcelParser.java:360-367`.

### G3 — `CITAD_BANK_CD` always null (HIGH)
- **Root cause:** `ParsedTransferLine` is constructed with positional `null` for `citadBankCd` (`ExpensePaymentExcelParser.java:575`, "Phase 2"); the parser never derives it. The save mapper faithfully writes the null.
- **Impact:** `SCDUL_PYMN_SUMR_DETL.CITAD_BANK_CD` is null on **every** refactor row. Legacy sets the 3-digit code (CITAD) / `"000"` (NAPAS). Breaks migration parity on every row, including NAPAS (which should be `"000"`), and starves CITAD-routing logic that reads the column.
- **Same finding as Internal Transfer G2.** **Legacy ref:** `PymnHelper.java:1361/1364/1468`. **Refactor ref:** `ExpensePaymentExcelParser.java:575`.

### G4 — `WDRL_CCY_CD` stores transfer currency, not account currency (MEDIUM)
- **Root cause:** Frontend `handleUpload` sets `ccy_cd = trnf_ccy_cd = batchCcyCd` (transfer currency). Backend `doCreateDraft` sets `master.CCY_CD` and `master.WDRL_CCY_CD = req.ccyCd()`. Legacy keeps them distinct: `CCY_CD = TRNF_CCY_CD`, `WDRL_CCY_CD = CCY_CD` (the withdrawal account's own currency, available on the wire as `selectedAccount.ccy_cd`).
- **Impact:** When the withdrawal-account currency ≠ transfer currency, `WDRL_CCY_CD` is wrong. Structural migration-parity drift; the correct value is already client-side (`ApiWithdrawalAccountRow.ccy_cd`).
- **Same finding as Internal Transfer G3.** **Legacy ref:** `PymnHelper.java:1320`. **Refactor ref:** `ExpensePaymentService.doCreateDraft` master map (~line 446-476).

### G5 — No save-side re-validation (MEDIUM)
- **Root cause:** Legacy Expense save (`pymn_0003_002_c001`, **unlike** internal transfer) re-runs `ckDepositNo` / `ckContactCode` / `ckRecName` and rejects with `WBE0404`. The refactor `createDraft` trusts the frontend-filtered success rows and does no save-side re-validation.
- **Impact:** A crafted/stale save request (e.g. one missing the contact keys, cf. G1) is accepted without re-derivation or rejection. The parse step is the only validation gate; if the FE skips parse or sends edited rows, integrity is not re-checked at save. Trust-boundary regression vs legacy.
- **Legacy ref:** `PymnHelper.java:1370-1441`. **Refactor ref:** `ExpensePaymentService.doCreateDraft` (no re-validation block).

### G6 — VCB-USD → other-bank block enforced at dispatch, not at upload save (LOW)
- **Root cause:** Legacy rejects per row at **save** (`OUT_BANK_CD==VCB && TRNF_CCY_CD=='USD' && TAR_BANK_CD!=VCB` → `WBE0000`). The refactor's equivalent lives in `ExpenseDispatchExecutor` (non-VND file path), i.e. at **dispatch**, not upload. (Compounded by G2: the dispatch check needs the resolved 6-digit `TAR_BANK_CD`.)
- **Impact:** A USD Expense batch from a VCB withdrawal account to a non-VCB target is accepted at upload by the refactor (blocked only later at dispatch), vs blocked immediately at upload by legacy. The user sees the error later in the flow. Net money-movement outcome is similar; UX/timing and the persisted draft differ.
- **Legacy ref:** `PymnHelper.java:1460-1465`. **Refactor ref:** `ExpenseDispatchExecutor` (USD file branch).

### G7 — NAPAS recipient-name bank verification stubbed (LOW)
- **Root cause:** Parser calls `FirmBankingInquiryService.inquiryAccount(bankCd, fincMdaNo)` for NAPAS rows; the service is not yet wired to the bank socket → returns `Optional.empty()` → no-op. Legacy `ckRecName` also silently skips when the inquiry returns null.
- **Impact:** Today the behaviour matches legacy (both no-op). But the real cross-check (bank-returned owner name vs contact's stored `FINC_MDA_OWNER_NM`) is not performed in either parse or save — a name mismatch would not be caught. Track as a known limitation, not a regression.
- **Refactor ref:** `ExpensePaymentExcelParser.java:466-483`.

### G8 — Stale "Phase B deferred" Javadoc (LOW, documentation)
- **Root cause:** `ExpensePaymentController` (~line 60) and `ExpensePaymentService` (~line 94) still describe detail / create / dispatch / request-approval as Phase B / subsequent MRs. They are fully implemented.
- **Impact:** Misleads maintainers/audits. No runtime effect.

### R1 — Refresh button has no legacy counterpart (INFO)
- The "Refresh" on the Scheduled Payment list (`useHaengsungRefresh`, `payment_type="EXPENSE"`) is a **HAENGSUNG Oracle sync**, rendered only for `PRDT_TYPE='H'` tenants; non-HS tenants get "Excel Upload". Legacy JEX has no Refresh on this screen. Out of legacy-parity scope; correctness belongs to the HAENGSUNG sync audits, not this one.

---

## 10. Recommended Fix Plan (NOT yet implemented — awaiting approval)

> Order matters: G2 underpins G3 and G6. G1 is the highest-value, lowest-risk fix (frontend-only). Per `feedback_preserve_existing_code.md`, touch only the broken paths. No schema change is required — every affected column already exists.

1. **G1 (frontend) — forward the contact-identity fields.** In `ExpensePaymentUploadDialog.parsedToWire`, add `trco_key: line.trco_key ?? null`, `hand_trco_key: line.hand_trco_key ?? null`, `trco_cmnm: line.trco_cmnm ?? null`, `trco_cmenm: line.trco_cmenm ?? null`. The wire type and backend already accept them; the backend mapper already writes them. Pure frontend mapper change. **(Highest priority — restores the defining Expense column set.)**

2. **G2 + G3 (parser) — resolve CITAD bank code + populate `citad_bank_cd`.** In `ExpensePaymentExcelParser`, on the CITAD `TbFirmBankCode` hit: set `resolvedBankCd = hit.bankCd` (6-digit) and `citadBankCd = trimmed` (3-digit input). For NAPAS: keep `resolvedBankCd = trimmed`, set `citadBankCd = "000"`. Thread `citadBankCd` into the `ParsedTransferLine` (replace the positional `null` at line 575). The save mapper already writes `line.citadBankCd()`. Mirror the agreed Internal-Transfer fix so both parsers stay aligned.

3. **G4 — `WDRL_CCY_CD` = withdrawal-account currency.** Coordinate FE+BE (pick with PO): either (a) FE sends `ccy_cd = selectedAccount.ccy_cd` and keeps `trnf_ccy_cd = batchCcyCd`, BE maps `CCY_CD = req.trnfCcyCd()` + `WDRL_CCY_CD = req.ccyCd()`; or (b) add an explicit `wdrl_ccy_cd` request field from `selectedAccount.ccy_cd`. Recommend (b) for lower blast radius (the same decision applies to Internal Transfer G3 — keep both consistent).

4. **G5 — save-side re-validation (PO decision).** Decide whether to port the legacy `ckDepositNo`/`ckContactCode`/`ckRecName` re-checks into `doCreateDraft` (defence-in-depth, matches legacy) or to formally accept the parse-only trust boundary. If G1 is fixed, the immediate data-loss risk is removed; G5 is then about robustness vs a stale/crafted client.

5. **G6 — VCB-USD guard at upload (after G2).** Once `tarBankCd` is the 6-digit code, add a parse-side (or save-side) check mirroring legacy: VCB withdrawal + `trnfCcyCd=='USD'` + resolved `tarBankCd != VCB` → row error. Parse-side preferred so the user sees it in preview. Confirm with PO whether dispatch-time enforcement is sufficient.

6. **G7 — document the stub.** Add a clear comment that `FirmBankingInquiryService.inquiryAccount` is a no-op pending the bank socket; do not ship the NAPAS name cross-check to UAT/PROD without it.

7. **G8 — docs.** Rewrite the stale `ExpensePaymentController` / `ExpensePaymentService` class Javadoc to state Phase B/C/D are implemented.

8. **V1 — verify** `WithdrawalAccountListService` filter parity vs `comm_1001_001` and that it honours the `ccy_cd` filter. If it diverges, raise separately.

9. **Tests.** Extend parser unit tests for a CITAD row (assert `TAR_BANK_CD` 6-digit, `CITAD_BANK_CD` 3-digit) and a NAPAS row (`CITAD_BANK_CD="000"`); add a `doCreateDraft` test asserting persisted `TRCO_KEY/HAND_TRCO_KEY/TRCO_CMNM/TRCO_CMENM` are non-null when the request carries them; add a frontend test asserting `parsedToWire` forwards the contact keys.

**Migration-safety note:** G1–G4 are structural drift from legacy. Per the root-CLAUDE legacy-first rule, they should be corrected **toward legacy**, and the fixes are PO-reviewable because they change persisted column values. No DDL is required.

---

## 11. Verification Commands

> Read-only. JEX DEV via `postgres_dev_server_jexframe` MCP; refactor DB via `postgres_dev_server_refactor` MCP. No DML/DDL.

**Backend test run (this audit):**
```bash
cd "c:/WECMS PROJECT/wecms-api"
./gradlew test --tests "*ExpensePayment*"
```
**Result (2026-06-29): `BUILD SUCCESSFUL` — all `*ExpensePayment*` tests pass** (`ExpensePaymentDispatchApprovalGateTest`, `ExpensePaymentRequestApprovalPendingTest`, `ExpensePaymentResultDownloadAuditTest`, `ExpensePaymentResultFooterTotalsTest`, `ExpensePaymentResultParityTest`). Note: the existing suite covers dispatch/approval/result paths — it does **not** assert the upload save-path contact-identity columns (G1) or CITAD resolution (G2/G3); those assertions are part of the §10 fix plan, not the current green baseline.

**Frontend checks** — N/A for this audit (no frontend code was changed). When the G1 fix lands:
```bash
cd "c:/WECMS PROJECT/wecms-web"
npm run type-check
npm run lint
```

**Inspect saved Expense detail rows (refactor DB) after an upload:**
```sql
SELECT scdul_pymn_no, scdul_pymn_detl_no, ant_div_cd,
       tar_bank_cd, citad_bank_cd,
       trco_key, hand_trco_key, trco_cmnm, trco_cmenm,
       trnf_ccy_cd
FROM   scdul_pymn_sumr_detl d
JOIN   scdul_pymn_sumr m USING (biz_key, scdul_pymn_no)
WHERE  m.biz_key = 'VN_VNM0004_000001'
  AND  m.scdul_pymn_type_cd = '02'
ORDER  BY scdul_pymn_no DESC, scdul_pymn_detl_no
LIMIT  20;
-- Pre-fix: trco_key / hand_trco_key / trco_cmnm / trco_cmenm = NULL (G1);
--          CITAD rows tar_bank_cd = 3-digit, citad_bank_cd = NULL (G2/G3).
-- Post-fix: contact keys populated; CITAD tar_bank_cd = 6-digit BANK_CD,
--           citad_bank_cd = 3-digit; NAPAS citad_bank_cd = '000'.
```

**Confirm master currency split:**
```sql
SELECT scdul_pymn_no, ccy_cd, wdrl_ccy_cd, out_bank_cd, wdrl_act_no
FROM   scdul_pymn_sumr
WHERE  biz_key = 'VN_VNM0004_000001' AND scdul_pymn_type_cd = '02'
ORDER  BY scdul_pymn_no DESC LIMIT 10;
-- G4: wdrl_ccy_cd should equal the withdrawal account's own currency,
--     not the transfer currency, when the two differ.
```

---

---

## 12. Implementation Result (2026-06-29)

All approved fixes from §10 are implemented and verified. Legacy behaviour is the source of truth; the Refresh button was left intact; no real NAPAS bank inquiry was added.

### Fixes implemented

| # | Sev | Status | What changed |
|---|---|---|---|
| **G1** | HIGH | ✅ Fixed | `parsedToWire` now forwards `trco_key` / `hand_trco_key` / `trco_cmnm` / `trco_cmenm`. The backend save mapper already persisted them; contact identity is no longer null on `SCDUL_PYMN_SUMR_DETL`. |
| **G2** | HIGH | ✅ Fixed | Parser resolves CITAD `TAR_BANK_CD` to the 6-digit `BANK_CD` (`TbFirmBankCode.getBankCd()`); NAPAS passes through. New `resolvedTarBankCd` var; the raw typed code (`resolvedBankCd`) is still used for the deposit-account match + NAPAS inquiry, so that logic is unaffected. |
| **G3** | HIGH | ✅ Fixed | Parser populates `CITAD_BANK_CD` = 3-digit code (CITAD) / `"000"` (NAPAS); the positional `null` ("Phase 2") was replaced with `resolvedCitadBankCd`. |
| **G4** | MED | ✅ Fixed | Added optional `wdrl_ccy_cd` to the shared request DTO + wire type; the Expense dialog sends `selectedAccount.ccy_cd`; the service maps `WDRL_CCY_CD = req.wdrlCcyCd()` with fallback to `ccy_cd`. **Internal Transfer impact: see below.** |
| **G5** | MED | ✅ Fixed | `doCreateDraft` re-derives `TRCO_KEY`/`TRCO_CMNM`/`TRCO_CMENM` from `HAND_TRCO_KEY` (via `TrcoInfmRepository.findActiveByBizKeyAndHandTrcoKey`) when the request omits the key; an unresolvable contact code is rejected with `WBE_VALIDATION` instead of saving a null identity. A row with no contact code at all is left as-is (upstream parse concern) — not over-rejected. |
| **G6** | LOW | ✅ Fixed | VCB-USD → other-bank guard added at parse (mirrors the sibling Vendor parser exactly): `OutBankCdNormalizer` injected; `withdrawalIsVcb` precomputed; row flagged on `TRNF_CCY_CD` with "VCB does not support USD transfer to other banks." Compared against the resolved 6-digit `TAR_BANK_CD` (depends on G2). Dispatch-time guard kept as defense-in-depth. |
| **G7** | LOW | ✅ Documented | Parser field Javadoc now states `FirmBankingInquiryService.inquiryAccount` is a no-op stub (no `WB_00000100` socket), the NAPAS recipient-name cross-check therefore does not run, behaviour matches legacy when the inquiry is null, and it must not be enabled for UAT/PROD without a working bank inquiry. **No runtime change.** |
| **G8** | LOW | ✅ Fixed | Removed the stale "Phase A / Phase B deferred" class Javadoc on `ExpensePaymentController` + `ExpensePaymentService`; they now describe the implemented surface. **No runtime change.** |
| **R1** | INFO | ✅ Left unchanged | Refresh is a HAENGSUNG-only Oracle sync (`useHaengsungRefresh`, `payment_type="EXPENSE"`, `PRDT_TYPE='H'`) with **no standard legacy JEX counterpart** — not a parity bug. Behaviour preserved; documented here per the task. |

### Files changed

**`wecms-web`:**
- [`components/payment/expense-payment/ExpensePaymentUploadDialog.tsx`](../../../../wecms-web/components/payment/expense-payment/ExpensePaymentUploadDialog.tsx) — `parsedToWire` forwards the 4 contact-identity fields (G1); `handleUpload` sends `wdrl_ccy_cd = selectedAccount.ccy_cd` (G4).
- [`lib/types/api.ts`](../../../../wecms-web/lib/types/api.ts) — `ApiCreateScheduledTransferRequest` gains optional `wdrl_ccy_cd` (G4).

**`wecms-api`:**
- [`domain/payment/expensepayment/service/ExpensePaymentExcelParser.java`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentExcelParser.java) — G2/G3 bank-code resolution, G6 VCB-USD guard (+ `OutBankCdNormalizer` dependency), G7 stub doc.
- [`domain/payment/expensepayment/service/ExpensePaymentService.java`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentService.java) — G4 `WDRL_CCY_CD` mapping, G5 contact re-derivation (+ `TrcoInfmRepository` dependency), G8 Javadoc.
- [`domain/payment/expensepayment/controller/ExpensePaymentController.java`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/controller/ExpensePaymentController.java) — G8 Javadoc.
- [`domain/payment/internaltransfer/dto/CreateScheduledTransferRequest.java`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/dto/CreateScheduledTransferRequest.java) — **shared DTO**: added optional `wdrlCcyCd` component + a 6-arg backward-compatible constructor (G4).
- **Tests added:** `ExpensePaymentExcelParserTest` (4 — NAPAS `"000"`, CITAD 6-digit + 3-digit, VCB-USD flagged, VCB-USD-to-VCB allowed); `ExpensePaymentCreateDraftTest` (4 — contact identity persisted + `WDRL_CCY_CD`, fallback to `ccy_cd`, G5 re-derivation, G5 reject).

### Shared-DTO impact (Internal Transfer)

`CreateScheduledTransferRequest` is shared by Internal Transfer, Vendor, and Expense. The G4 change is **additive and backward-compatible**:
- `wdrlCcyCd` is an **optional, nullable** record component (no `@NotBlank`; `@Pattern` validates only when present).
- A **6-arg convenience constructor** (omitting `wdrlCcyCd`) preserves every existing call site — Internal Transfer's `doCreateDraft` and its `InternalTransferCreateDraftTest` compile and behave **unchanged** (they never send `wdrl_ccy_cd`; their own G3 is tracked in the Internal Transfer audit).
- Jackson always binds the **canonical 7-arg record constructor**, so the wire contract is unaffected; a client that omits `wdrl_ccy_cd` deserializes it as `null`.
- The Internal Transfer **service was not modified** — it still maps `WDRL_CCY_CD = req.ccyCd()`. Only the Expense service consumes the new field. (Aligning Internal Transfer's own G3 to use `wdrl_ccy_cd` is left to its audit so this change stays scoped to Expense.)

### Verification

| Check | Result |
|---|---|
| `./gradlew test --tests "*ExpensePayment*"` | **BUILD SUCCESSFUL** — 28 tests, 0 failures (incl. 8 new: 4 parser + 4 create-draft). |
| Regression `*InternalTransferCreateDraft*`, `*InternalTransferExcelParser*`, `*VendorPayment*` | **BUILD SUCCESSFUL** — shared-DTO change did not break siblings. |
| `wecms-web` `npx tsc --noEmit` | **Exit 0** (no `type-check` npm script exists; ran `tsc --noEmit` directly). |
| `wecms-web` `eslint` (changed files) | **Exit 0**. |

### Remaining PO-review / open items

- **G5 scope decision (informational):** the re-derivation re-checks only the contact identity (the field set legacy `pymn_0003_002_c001` re-validates that the refactor was dropping). The full legacy save-side re-run of `ckDepositNo` / `ckRecName` is **not** re-added — parse-side validation + G5's contact re-derivation cover the data-integrity risk that G1 exposed. Flag for PO if a stricter save-side re-validation of deposit/recipient is desired.
- **G7 (carried):** the NAPAS recipient-name bank inquiry remains a stub — must be implemented before the NAPAS name cross-check can be relied on for UAT/PROD.
- **V1 (carried, unchanged):** `WithdrawalAccountListService` filter parity vs `comm_1001_001` (incl. honouring the `ccy_cd` filter) still delegated, not re-verified in this pass.

### Behaviour intentionally left unchanged

- Refresh button (R1) — HAENGSUNG Oracle sync, kept as-is.
- Dispatch-time VCB-USD guard in `ExpenseDispatchExecutor` — kept as defense-in-depth alongside the new parse-side guard.
- Internal Transfer / Vendor / other payment modules — untouched except the additive shared-DTO field above.

---

---

## 13. Template Replacement Re-check (2026-06-29)

Re-verification of the Expense Payment **upload templates only** after the PO replaced the files. **No code was changed.** Re-checked the on-disk templates against `ExpensePaymentExcelParser.java` (header constants + row offsets) and `ExpensePaymentUploadDialog.tsx` (download URL + preview columns).

### Files checked

| File | Size | New web vs JEX `WECMS_STATIC/.../excel/VN/` |
|---|---|---|
| `wecms-web/public/templates/expense-payment/WeCMS_Expense_Payment_upload_EN.xlsx` | 38 651 B | **differs** — cosmetic only (`\r\n` vs `\n` inside a hint cell; header labels identical) |
| `…/WeCMS_Expense_Payment_upload_KO.xlsx` | 36 581 B | **byte-identical** |
| `…/WeCMS_Expense_Payment_upload_VI.xlsx` | 35 871 B | **byte-identical** |

The replaced files are equivalent to the previously-audited set (same sizes; KO/VI byte-identical to the JEX upload templates; EN differs only by a CRLF normalization in a comment/hint cell). The §4 template findings stand.

### Verification result (extracted from the worksheet XML, in column order)

| Check | Expected (parser / dialog) | New template (all 3 locales) | Match? |
|---|---|---|---|
| Filename / path | dialog `TEMPLATE_URL` = `/templates/expense-payment/WeCMS_Expense_Payment_upload_{EN,KO,VI}.xlsx` (KM→EN) | exact filenames present | ✅ |
| Sheet | parser reads `getSheetAt(0)` (index-based, name-agnostic) | sheet name `"Expense Payments"` (sheet 0) | ✅ (no dependency on the name) |
| Header row index | `HEADER_ROW = 3` (0-based) = spreadsheet row 4 | header labels on spreadsheet row 4 | ✅ |
| Data start row | `FIRST_DATA_ROW = 4` (0-based) = spreadsheet row 5 | data begins row 5 | ✅ |
| Column count | 10 (`HEADERS_*.length`) | `dimension = A1:J…` → 10 cols | ✅ |
| Column order | Bank Code · Deposit No. · Bank · Transfer Amount · Transfer Currency · Remarks · Reporting Account · Contact Code · Description · Document No. | identical order | ✅ |
| EN header text | `HEADERS_EN[]` | exact match (incl. `Deposit No.*`, `Contact Code *`) | ✅ |
| KO header text | `HEADERS_KO[]` | exact match (`은행코드 *` … `문서번호`) | ✅ |
| VI header text | `HEADERS_VI[]` | exact match, incl. the irregular `Lọai tiền chuyển *` and `Số tài khoản nhận tiền*` (no space) | ✅ |
| Required columns | the 5 `*`-marked: Bank Code, Deposit No., Transfer Amount, Transfer Currency, Contact Code | all 5 present + `*`-marked | ✅ |
| Parser header expectation | `validateHeaderRow` prefix-match (`startsWith`) on row 3, 10 cols → `WBE2018` on mismatch | passes for all 3 locales | ✅ |
| Sample/hint row breaks parsing? | hint row (row 3) never read; data parsed from row 5 | hint row ignored; **sample row present at row 5** (`970424 / ShinhanBank / VND / V001 / 2001001 / 1C24TPA-25861`) | ✅ does not break parsing |

### Mismatch

**None.** No template/header/column/offset mismatch against the parser or the dialog.

### Note (informational — not a mismatch, no fix)

The template ships a **sample data row at spreadsheet row 5** — the parser's first data row. Because its critical cells (Bank Code, Deposit No., Amount, Contact Code, Description, Doc No.) are populated, the parser does **not** early-break on it: if a user uploads the template without deleting the sample row, that row is validated like any real line (its demo values — contact `V001`, reporting account `2001001`, deposit `700071152854` — resolve only on a tenant that has the matching DEV demo seed; otherwise it surfaces as a normal preview error). This is **legacy-consistent behaviour** — the legacy JEX template carries the same guide row at the same offset and the legacy parser reads from the same row; the user is expected to overwrite/delete it. It is **not** a parser-template parity defect and requires no change. (If the PO later wants the sample row suppressed, that is a template-content decision, not a parser fix.)

### Conclusion

- The §1 / §4 statement **"Template layer is at full parity" remains TRUE** after the replacement.
- **No code change is required.** Parser and dialog already match the replaced templates exactly.

---

## Recipient Name / Lookup Recipient Legacy Parity Audit

> Added 2026-06-30. Focused parity audit of how **Recipient Name** (`RCPT_NM`) is populated for Expense Payment and how the **Lookup Recipient** API (legacy `ckRecName`) behaves, including the **HAENGSUNG Refresh** path. Evidence-based; **no runtime logic changed**. Builds on §5 and gaps G5/G7.

### 1. Legacy behavior summary

Expense Payment shares the Vendor `readExcel(...)` path ([`PymnHelper.java:303-607`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)), so recipient handling is **identical to Vendor Payment**: a **mandatory contact** (Contact Code `HAND_TRCO_KEY` col 7 → `ckContactCode`) resolves `TRCO_KEY`, then `RCPT_NM` is derived by network:

- **NAPAS** → **live Lookup Recipient inquiry** `ckRecName(...)` ([`PymnHelper.java:860-1040`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)) → `FIRM_PROC.inquiryAccount` (`:965`, socket `WB_00000100`, config from `TB_FIRM_CONF`); bank-returned `FINC_MDA_OWNER_NM` compared to the contact-stored name and persisted on match (`:1011`); mismatch → `WBE0001`/`WBE0404`; config missing → `WBE0001` (not skip).
- **CITAD** → `getfincMdaOwnerNmFromAcctInfo(...)` (`:1067-1092`) reads `RCPT_NM` from `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`.
- Save path (`pymn_0003_002_c001`) **re-runs** `ckDepositNo` / `ckContactCode` / `ckRecName` (→ `WBE0404`) — stricter than Internal Transfer (see G5).

### 2. Refactor behavior summary

- **CITAD** — at parity: `ExpensePaymentExcelParser` cross-fills `RCPT_NM` from `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` ([`ExpensePaymentExcelParser.java:498-500`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentExcelParser.java)) and forwards it (`:637`) so `RCPT_NM` persists.
- **NAPAS** — the Lookup Recipient call is **wired** to `firmBankingInquiryService.inquiryAccount(...)` ([`ExpensePaymentExcelParser.java:523-540`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentExcelParser.java)) → the shared `FirmBankingInquiryService` → `DepositorInquiryService` → `RecipientLookupService` → `EfsApiClient` (`WGVN_EFS_201`). Today it is a **no-op stub / gated off** — `FirmBankingInquiryService.inquiryAccount` returns `Optional.empty()` until `wecms.dispatch.recipient-inquiry=true` and the EFS socket is wired (the explicit audit note at `ExpensePaymentExcelParser.java:166-175`). Legacy also silently skips when the inquiry returns null, so behavior **matches today**, but the real cross-check + NAPAS `RCPT_NM` persistence are not active. Tracked as **G7**.
- **HAENGSUNG Refresh** — recipient name comes from Oracle `TB_TRNS_DETAIL.ACET_NM` (tone-stripped) → `RCPT_NM` (Expense routed by `FILE_NM LIKE 'E%'`; [`ErpPaymentRefreshMasterImporter.java:140`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/haengsung/service/ErpPaymentRefreshMasterImporter.java)). Flag-gated OFF (`HAENGSUNG_ORACLE_ENABLED=false`).

### 3. Recipient Name source comparison

| Source | Legacy | Refactor | Match |
|---|---|---|---|
| Contact / saved recipient (`TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`) | ✅ CITAD | ✅ CITAD | ✅ |
| Bank Account / account master (`ACCT_MSTR`) | ❌ never | ❌ never | ✅ |
| Lookup Recipient API (`ckRecName` / NAPAS inquiry) | ✅ NAPAS (live, persisted) | ⚠️ NAPAS wired but **stubbed / gated off** (G7) | ⚠️ matches today, not real |
| EFS / VAN / WeCMS Batch | ✅ NAPAS inquiry rides the firm-banking socket | ⚠️ same chain (`EfsApiClient`), not connected | ❌ (env) |
| Manual Excel cell | ❌ no Recipient Name column | ❌ no Recipient Name column | ✅ |
| HAENGSUNG ERP Refresh (`TB_TRNS_DETAIL.ACET_NM`) | n/a (legacy ERP import) | ✅ implemented, flag OFF | ⚠️ (env) |

### 4. Lookup Recipient condition

| Network | Legacy | Refactor |
|---|---|---|
| NAPAS | **always** when a `TB_FIRM_CONF` row exists; config missing → `WBE0001` (never skip); re-run at save | only when `wecms.dispatch.recipient-inquiry=true` **and** active `TB_FIRM_CONF`; otherwise silent no-op; no save-side re-run (G5) |
| CITAD | never (DB read of `TRCO_ACCT_INFO`) | never (DB read of `TRCO_ACCT_INFO`) |

### 5. Contact vs Bank Account source rules

**Contact-based only — never `ACCT_MSTR`.** Identical rule to Vendor Payment: a valid Contact Code is mandatory; CITAD reads the contact's stored account-owner name; NAPAS verifies a live bank name against the contact-stored name (and would persist it). No manual entry.

### 6. EFS / VAN / WeCMS Batch dependency status

**Direct dependency for the NAPAS leg.** The NAPAS Lookup Recipient inquiry uses the shared firm-banking chain (`EfsApiClient`, `WGVN_EFS_201`), inert today (`recipient-inquiry=false`, `efs.enabled=false`, `MockEfsApiClient` throws, no VAN credentials in local/SIT). Dispatch engine is code-complete (memory `project_batch_refactor_real_impl`). CITAD has no EFS/VAN dependency.

### 7. Gap list

- **G7 (NAPAS):** recipient-name bank verification + persistence is stubbed/gated off; matches legacy's null-skip *today* but the live cross-check and NAPAS `RCPT_NM` persistence are not active.
  - **Code-level precision (verified 2026-06-30):** the gap is **not only** the gated service — the parser's NAPAS block at [`ExpensePaymentExcelParser.java:523-540`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentExcelParser.java) consumes the inquiry result **only** to add a mismatch `FieldError` and **never assigns `resolvedRcptNm`** (which is set on CITAD only, `:500`). So even if `wecms.dispatch.recipient-inquiry` were enabled, NAPAS `RCPT_NM` would still persist as null. This is the **same latent persistence gap documented for Vendor Payment §15.4** and must be fixed alongside enabling the inquiry for true legacy parity (legacy persists the bank-returned `FINC_MDA_OWNER_NM` to `RCPT_NM`).
- **G5 (save-side re-validation, PO decision):** legacy re-runs `ckDepositNo`/`ckContactCode`/`ckRecName` at save; the refactor trusts parse-side success rows (the G1 contact-identity re-derivation landed, but the full save-side recipient re-run was deliberately not re-added — flag for PO).
- CITAD recipient name: **no gap** (parity).

### 8. Final verdict: **CONDITIONAL PASS**

CITAD recipient-name resolution is at full parity and behavior matches legacy today (both skip NAPAS inquiry when unavailable). It is **conditional** because the NAPAS Lookup Recipient cross-check + persistence (G7) are stubbed/gated off and unverifiable until EFS/VAN is connected, and the save-side re-validation (G5) is a deferred PO decision.

### 9. Environment / verifiability note

The NAPAS Recipient Name / Lookup Recipient behavior **cannot be fully verified in the current environment** — the code path exists but EFS/VAN/WeCMS Batch is **not connected** (`recipient-inquiry` + `efs.enabled` both `false`, mock EFS client throws). CITAD recipient resolution **is** verifiable now (DB read of `TRCO_ACCT_INFO`). The HAENGSUNG Refresh recipient path is implemented but requires the HAENGSUNG Oracle connection (`HAENGSUNG_ORACLE_ENABLED=false`) to verify end-to-end.

---

### 10. Lead Engineer disposition (2026-06-30, documentation-only)

**Decision: option (a) — hold. No runtime code changed. The Vendor/Expense NAPAS `RCPT_NM` write-back patch is retained as a *prepared* fix for the future EFS/VAN enablement MR.**

- **NAPAS `RCPT_NM` write-back — ⚠ LATENT MISSING IMPLEMENTATION, BLOCKED BY EFS/VAN FEATURE FLAG.** Code-verified 2026-06-30: the NAPAS block at [`ExpensePaymentExcelParser.java:523-540`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/expensepayment/service/ExpensePaymentExcelParser.java) consumes the inquiry result **only** for a mismatch `FieldError` and **never assigns `resolvedRcptNm`** (CITAD-only at `:500`) — the same latent gap as Vendor §15.4 (see G7 above). **Unreachable today** — `wecms.dispatch.recipient-inquiry=false` and `wecms.dispatch.efs.enabled=false` (mock `MockEfsApiClient` throws). **Not user-reproducible** in DEV/SIT/UAT while gated; would surface in **PROD only after EFS is enabled** without the fix.
  - **Prepared fix (DO NOT APPLY until the EFS/VAN MR):** convert the `.ifPresent(...)` at `:529-539` to an explicit `Optional` unwrap and, on a successful **match**, set `resolvedRcptNm = fromBank` — mirroring legacy `PymnHelper.ckRecName:1011`. Ship with a parser unit test that mocks `FirmBankingInquiryService.inquiryAccount(...)` returning a name. **Inert today**; must land **before** EFS is enabled in any environment. Apply jointly with the identical Vendor change.
- **Config-missing behavior — 🔶 PO DECISION REQUIRED BEFORE ENABLING INQUIRY.** Legacy flags `WBE0001` per NAPAS row when `TB_FIRM_CONF` is absent (`PymnHelper.java:1037-1038`); the refactor silently skips ([`FirmBankingInquiryService.java:162-166`](../../../../wecms-api/src/main/java/com/kosign/wecms/common/firmbanking/FirmBankingInquiryService.java)). PO (Mr. Jung) must rule on the policy **before** the inquiry flag is switched on, in the same EFS/VAN MR. (Distinct from G5, the save-side re-validation PO item.)
- **CITAD `RCPT_NM` (contact `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM`) — ✅ no action** (full parity, verifiable today).
- **Preview→Save flow, persistence, entity mapping — ✅ no action** (code-verified: single assignment `ExpensePaymentService.java:550`, no overwrite, `RCPT_NM VARCHAR(100)` nullable, wire fields incl. contact triad forwarded unchanged).

> **Classification (Lead Engineer):** NAPAS write-back = **⚠ Missing implementation**, masked by **⚠ Feature flag disabled** + **⚠ External dependency (EFS/VAN/WeCMS Batch)**. Config-missing = **PO decision**. Everything else = **✅ No action required**. **No runtime files were changed in this audit.**

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

---

*End of audit — fixes implemented and verified 2026-06-29; template replacement re-checked 2026-06-29 (full parity, no code change required).*

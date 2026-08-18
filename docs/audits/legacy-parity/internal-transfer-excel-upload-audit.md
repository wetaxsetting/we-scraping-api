# Internal Transfer — Scheduled Transfer — Excel Upload — Legacy Parity Audit


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

- **Feature:** Payment → Internal Transfer → Scheduled Transfer → Excel Upload
- **Slug:** `internal-transfer`
- **Legacy WSVC:** `pymn_0009_002` (upload popup), `pymn_0009_002_r001` (parse/preview), `pymn_0009_002_c001` (save), `pymn_0009_001` (list)
- **System tags:** JEX (source of truth) ↔ Refactor (`wecms-api` + `wecms-web`)
- **Date:** 2026-06-29
- **Status:** **Fixes implemented & verified 2026-06-29** (G1–G6 landed; V1 verified-as-match). See §12 Resolution. Original audit body below is preserved as the pre-fix record.
- **Method:** Analyze → Compare with legacy → Root cause → Impact → (this document) → approval → implement.

> **Scope note:** This audit covers the *Excel Upload* path only (download template → parse/preview → save draft batch). The downstream Transfer/Dispatch, Approval, and Result flows are referenced only where the upload save-path feeds them. Tables touched are all in the PROD Active Table List / legacy-table-inventory (`SCDUL_PYMN_SUMR`, `SCDUL_PYMN_SUMR_DETL`, `TB_FIRM_BANK_CODE`, `ACCT_MSTR`, `CHART_ACCT_INFM`, `ERP_TRNS_HSTR`).

---

## 1. Summary

The refactor reproduces the legacy Excel Upload flow faithfully **at the parse/preview layer** — the discovery report that called `parseExcel` a "Phase A stub returning empty preview" was wrong; it was reading a **stale Javadoc**. The real `InternalTransferExcelParser.parse(...)` is fully implemented and matches legacy `PymnHelper.readExcelFromInternalTransfer` closely (header validation, column order, required fields, currency whitelist, single-currency/single-network guards, ChartAcct validation, RCPT_NM/DEPO_CCY_CD resolution from `ACCT_MSTR`).

The real divergences are in the **save path** (`InternalTransferService.doCreateDraft` vs `PymnHelper.uploadPaymentExcel` for `pymn_0009_002_c001`), and they matter for **data-migration parity and downstream dispatch**, not for the preview UX:

| # | Severity | Gap |
|---|---|---|
| G1 | **HIGH** | CITAD rows: `TAR_BANK_CD` saved as the raw 3-digit CITAD code, **not** resolved to the 6-digit `BANK_CD` (legacy resolves it via `getBankCode`). |
| G2 | **HIGH** | `SCDUL_PYMN_SUMR_DETL.CITAD_BANK_CD` is **always null** in the refactor (parser never computes it). Legacy stores the 3-digit CITAD code for CITAD rows and `"000"` for NAPAS rows. |
| G3 | **MEDIUM** | `SCDUL_PYMN_SUMR.WDRL_CCY_CD` stores the **transfer currency**, not the **withdrawal-account currency**. Legacy stores the account currency. |
| G4 | **MEDIUM** | The **VCB-USD → other-bank** block (legacy `WBE0000` at save) is **not implemented** in the refactor. |
| G5 | LOW | Field-length caps (Remarks 200 / Note 200 / Doc No 50) are **not flagged at preview** in the refactor parser (only the DTO `@Size` caps at save, and Note is `255` not `200`). |
| G6 | LOW (doc) | Stale Javadoc in `InternalTransferService` (lines ~105-107) and `InternalTransferExcelParser` (line ~44) still describe `parseExcel` as a non-parsing local stub. |
| G7 | INFO | Refactor `createDraft` trusts the frontend-filtered "success" rows and does **no save-side re-validation**; legacy re-validates at save (though for internal transfer it skips the deposit/contact/recipient checks). |
| V1 | VERIFY | Withdrawal Bank/Account dropdown filter parity vs legacy `comm_1001_001` (account types 03–08, firm-banking `ORG_STAT='1'`) — delegated to `WithdrawalAccountListService`, not re-read in this audit. |

Everything else — template files, header text, column order, required-field set, error/complete counts, currency whitelist, mixed-network guard, ChartAcct lookup, recipient-name resolution, ERP history write — is at parity.

---

## 2. Legacy Code Trace

### 2.1 Screen + popup
- List page: [`pymn_0009_001_view.jsp`](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/pymn/pymn_0009_001_view.jsp) — Scheduled Transfer list; "Excel Upload" button opens popup `pymn_0009_002`.
- Upload popup: `pymn_0009_002_view.jsp` + `pymn_0009_002.ts` — 9-column preview table, withdrawal account dropdown (from `comm_1001_001`), Download template, Browse, Upload.

### 2.2 Template download
- Static assets under `WECMS DEV/WECMS_STATIC/web/file/excel/VN/`:
  - `WeCMS_Internal_Transfer_upload_{EN,KO,VI}.xlsx`
- Frontend sets `<a>.href` to the per-locale path (KM falls back to EN). No server generation — byte-static files.

### 2.3 Parse / preview — `pymn_0009_002_r001_act.jsp`
- Reads `FILE_DATA` (required + `validateFile`), `BANK_CD` (required = withdrawal bank).
- `PymnHelper.importDataFromExcelForInternalTransfer(file, fileName, util, result, idoCon, loginUser, bankCode, pymnType)` → `readExcelFromInternalTransfer` ([`PymnHelper.java:649-858`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)).
- Header validation: `validDefualtHeaderInternalTransfer` (row index 3, prefix-match against `HEADER_TITLE_INTER_EN/KO/VI`).
- Row loop from row 4, capped at 500; **early-break** when columns 0,1,3,7,8 are all blank.
- Column map (data rows):

  | Col | Field | Legacy validation |
  |---|---|---|
  | 0 | `TAR_BANK_CD` + `ANT_DIV_CD` | length 3 → CITAD, 6 → NAPAS, else `WBE1013`; lookup `getListTbFirmBankCode` count ≥ 1 else `WBE0001` |
  | 1 | `FINC_MDA_NO` | `checkValidStringInMax(...,100)`; `validateBankAccountInAcctMstr` → sets `RCPT_NM`, `DEPO_CCY_CD`; miss → `WBE1194` |
  | 2 | `TAR_BANK_NM` | display-only, from `getBankName` |
  | 3 | `TRAN_AMT` | `checkValidAmountOnlyPositive_` (strictly > 0) |
  | 4 | `TRNF_CCY_CD` | required; len 3; **VND/USD only**; single currency per file (`WBW1160` on mix) |
  | 5 | `TRAN_USAG` (Remarks) | Vietnamese tone-strip; `SpecialCharValidator`; `checkValidStringInMax(...,200)` |
  | 6 | `CHART_ACCT_CD` | `getChartAcctInfm` — active (`STTS='A'`, `DEL_YN='N'`) lookup; else error |
  | 7 | `NOTE` (Description) | `checkValidStringInMax(...,200)` |
  | 8 | `DOC_NO` | `checkValidStringInMax(...,50)` |
- Output: `TOT_CNT = suc+err`, `SUC_CNT`, `ERR_CNT`, `REC[]` (per-row, with `putError` packing `err_fld`/`err_fld_caus`).

### 2.4 Save — `pymn_0009_002_c001_act.jsp` → `PymnHelper.uploadPaymentExcel(...,"pymn_0009_002_c001")`
[`PymnHelper.java:1301-1484`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)
- Input required: `TITL`, `FILE_DATA`, `BANK_CD`, `ACCT_NO`, `CCY_CD`, `TRNF_CCY_CD`, and each `REC.FINC_MDA_NO`.
- `validateUploadedPayments` — mixed CITAD/NAPAS across rows → `WBE1191`.
- **Master `SCDUL_PYMN_SUMR`:**
  - `copyFrom(input)`, `BIZ_KEY`
  - `CCY_CD = TRNF_CCY_CD` (transfer currency)
  - `OUT_BANK_CD = NapasBankInfo.napasBankCd` (resolved from `bank_cd OR napas_bank_cd`)
  - `WDRL_ACT_NO = ACCT_NO`
  - **`WDRL_CCY_CD = CCY_CD`** (withdrawal-account currency — *distinct from transfer currency*)
  - `TOTAL_TRAN_AMT = 0` (re-set after loop), `TOT_TRNS_CNT = REC.size`
  - `SCDUL_PYMN_TYPE_CD = INTERNAL ('03')`, `TRNF_YN = NOT_YET_TRANFER`, fileInfo, regUser, regDtm
- **Per-row detail `SCDUL_PYMN_SUMR_DETL`** (internal transfer path **skips** the deposit/contact/recipient-name re-checks via `if (!"pymn_0009_002_c001".equals(webSvcId))`):
  - `copyFrom(recItem)`
  - **`TAR_BANK_CD = getBankCode(util, idoCon, ANT_DIV_CD, recItem.TAR_BANK_CD)`** → CITAD: resolves 3-digit → 6-digit `BANK_CD` via `TB_FIRM_BANK_CODE_R001`; NAPAS: passthrough.
  - **VCB-USD guard:** `OUT_BANK_CD==VCB && TRNF_CCY_CD=='USD' && TAR_BANK_CD!=VCB` → `WBE0000` "VCB does not support USD transfer to other banks."
  - `ANT_DIV_CD = CITAD|NAPAS`
  - **`CITAD_BANK_CD = citadBankCd`** → CITAD: raw 3-digit code; NAPAS: `"000"`
  - regUser, regDtm
- After loop: `TOTAL_TRAN_AMT = Σ TRAN_AMT`, update master.
- ERP history: `ErpTrnsHisHelper.erpTrnsHisUploader(... INTERNAL_TRANSFER_PAYMENT)`.

`getBankCode` reference: [`PymnHelper.java:1232-1257`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java).

---

## 3. Refactor Code Trace

### 3.1 Web (`wecms-web`)
- Page: [`ScheduledTransferPage.tsx`](../../../../wecms-web/components/payment/internal-transfer/ScheduledTransferPage.tsx) → [`app/(dashboard)/payment/internal-transfer/scheduled-transfer/page.tsx`](<../../../../wecms-web/app/(dashboard)/payment/internal-transfer/scheduled-transfer/page.tsx>).
- Upload dialog: [`InternalTransferUploadDialog.tsx`](../../../../wecms-web/components/payment/internal-transfer/InternalTransferUploadDialog.tsx) — wraps shared [`PaymentExcelUploadDialog`](../../../../wecms-web/components/payment/shared/PaymentExcelUploadDialog.tsx) → [`ExcelUploadDialog`](../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx).
- Template: per-locale static assets `public/templates/internal-transfer/WeCMS_Internal_Transfer_upload_{EN,KO,VI}.xlsx`; `handleDownloadTemplate` uses anchor+click (KM→EN fallback). **Byte-identical to JEX originals.**
- Withdrawal dropdown: inline `WithdrawalAccountDropdown`; fetched via `fetchWithdrawalAccounts()` on mount, auto-selects first.
- Parse: `handleParse` → `parseInternalTransferExcel(file, bankCd)` (`bankCd` = selected withdrawal bank) → maps `ApiParsedTransferLine[]` to preview rows (`toUploadRow`); error cells from pipe-split `err_fld`/`err_fld_caus`.
- Upload: `handleUpload` filters `successLines` (no `err_fld`), maps each via `parsedToWire`, builds `ApiCreateScheduledTransferRequest`:
  - `titl = filename (ext stripped)`
  - `bank_cd = selectedAccount.bank_cd`, `acct_no = selectedAccount.acct_no`
  - **`ccy_cd = batchCcyCd`**, **`trnf_ccy_cd = batchCcyCd`** where `batchCcyCd = successLines[0].trnf_ccy_cd ?? selectedAccount.ccy_cd` (i.e. **transfer currency for both**).
  - `lines = successLines.map(parsedToWire)` — forwards `tar_bank_cd`, `ant_div_cd`, `rcpt_nm`, `citad_bank_cd` (null), `chart_acct_*`, `depo_ccy_cd`, etc.
- API client: [`lib/api/internalTransfer.ts`](../../../../wecms-web/lib/api/internalTransfer.ts) — `parseInternalTransferExcel` (POST `/uploads/parse`, multipart), `createScheduledTransfer` (POST root, multipart), `fetchWithdrawalAccounts` (GET `/withdrawal-accounts`).
- Wire types: [`lib/types/api.ts`](../../../../wecms-web/lib/types/api.ts) — `ApiParsedTransferLine`, `ApiTransferLineInput`, `ApiCreateScheduledTransferRequest`, `ApiWithdrawalAccountRow`.

### 3.2 API (`wecms-api`)
- Controller: [`InternalTransferController.java`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java)
  - `POST /api/v1/payment/internal-transfer/uploads/parse` (`@RequestPart file_data`, `@RequestParam bank_cd`)
  - `POST /api/v1/payment/internal-transfer` (save draft; multipart `file_data` + JSON `request`)
  - `GET /api/v1/payment/internal-transfer/withdrawal-accounts`
- Parse: `InternalTransferService.parseExcel` ([line ~1324](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferService.java)) → validates envelope (tenant, non-empty, `.xls/.xlsx`, bank_cd present) → **`excelParser.parse(file, bankCd)`** (NOT a stub).
- Parser: [`InternalTransferExcelParser.java`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferExcelParser.java)
  - Header row 3 prefix-match against `HEADERS_EN/KO/VI` (→ `WBE2018` on mismatch).
  - Rows from row 4, cap 500, early-break on cols 0,1,3,7,8 all blank.
  - Per-row validation mirrors legacy (see §5 table). Cross-row: `assertSingleCurrency` (→ `WBE2120`), `assertCurrencyWhitelist`, `assertSingleNetwork` (→ `WBE2121`).
  - Bank code resolution via `TbFirmBankCodeRepository` (NAPAS by `bank_cd`, CITAD by `citad_bank_cd`); `displayName` → `ENG_ABRVAT_NM`/`BANK_NM`/code.
  - **`resolvedBankCd = trimmed`** (the raw typed code — see G1).
  - `ParsedTransferLine.citadBankCd` constructed as **`null`** (see G2).
  - RCPT_NM + DEPO_CCY_CD resolved from `ACCT_MSTR` via `findFirstByBizKeyAndAcctNo` (legacy parity).
  - ChartAcct resolved via `findByBizKeyAndChartAcctCdAndDelYn(...,'N')`.
- Save: `InternalTransferService.doCreateDraft` ([line ~521](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferService.java)) — one-shot retry on key race.
  - Master: `OUT_BANK_CD = outBankCdNormalizer.normalize(bank_cd)`, `WDRL_ACT_NO = acct_no`, **`CCY_CD = req.ccyCd()`**, **`WDRL_CCY_CD = req.ccyCd()`**, `TITL`, `TRNF_YN = NOT_TRANSFERRED`, `ERP_PROC_CMPL_YN='N'`, `TOT_TRNS_CNT`, `TOTAL_TRAN_AMT` (summed upfront), file fields, audit.
  - Detail (per line): `TRAN_AMT`, `TRNF_CCY_CD` (default to `req.trnfCcyCd`), `ANT_DIV_CD`, `RCPT_NM`, **`TAR_BANK_CD = line.tarBankCd`** (raw), `FINC_MDA_NO`, `TRAN_USAG`, `TRCO_KEY/HAND_TRCO_KEY`, `DOC_NO`, `NOTE`, **`CITAD_BANK_CD = line.citadBankCd`** (always null), `TRCO_CMNM/CMENM`, `CHART_ACCT_SEQ_NO/CD`, `DEPO_CCY_CD`, audit.
  - Detail numbering via `wb_get_next_val` funnel (`nextScdulPymnDetlNo`), seed-once + Java increment.
  - ERP history write: `erpHistoryWriter.recordUploadedFile(TRNS_TYPE_CD="16", ...)` (REQUIRES_NEW, swallows errors).
  - Audit log append (`WSVC_ID="internal-transfer"`, `REF_NO="SCDUL_PYMN_SUMR"`).
- DTOs: [`ParseExcelResponse`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/dto/ParseExcelResponse.java), [`ParsedTransferLine`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/dto/ParsedTransferLine.java), [`CreateScheduledTransferRequest`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/dto/CreateScheduledTransferRequest.java), [`TransferLineInput`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/dto/TransferLineInput.java).
- Entities: `ScdulPymnSumr`, `ScdulPymnSumrDetl` (+ `@IdClass`).

---

## 4. Template Audit

| Aspect | Legacy | Refactor | Match? | Note |
|---|---|---|---|---|
| Template files | `WeCMS_Internal_Transfer_upload_{EN,KO,VI}.xlsx` in `WECMS_STATIC/.../excel/VN/` | Same 3 files copied verbatim into `wecms-web/public/templates/internal-transfer/` | ✅ | "byte-identical" per dialog comment |
| KM locale | falls back to EN (no KM template) | `TEMPLATE_URL[locale] ?? EN` | ✅ | |
| Download mechanism | `<a>.href` + browser stream | hidden `<a download>` + click | ✅ | |
| Header row | row index 3 | row index 3 (`HEADER_ROW=3`) | ✅ | |
| Header text (EN) | `Bank Code *`, `Deposit No.*`, `Bank`, `Transfer Amount *`, `Transfer Currency *`, `Remarks (200 letters)`, `Reporting Account`, `Description`, `Document No.` | identical `HEADERS_EN[]` | ✅ | prefix-match, all 3 locales |
| Header validation failure | `WBE0001` | `WBE2018` | ⚠️ code differs | refactor uses its own code namespace (by design) |
| Columns (count/order) | 9 visible (0–8) | 9 (`columns[]` in dialog) | ✅ | |
| Server-generated template | No (static) | No (static) | ✅ | |

---

## 5. Excel Read & Validation Audit

| Rule | Legacy | Refactor | Match? | Gap |
|---|---|---|---|---|
| Data start row | row 4 | row 4 (`FIRST_DATA_ROW`) | ✅ | |
| Max rows | 500 | 500 (`MAX_ROWS`) | ✅ | |
| Early break | cols 0,1,3,7,8 all blank | same | ✅ | |
| Bank Code network derive | len 3→CITAD, 6→NAPAS, else `WBE1013` | len 3→CITAD, 6→NAPAS, else error | ✅ | |
| Bank Code existence | `getListTbFirmBankCode` count ≥1 | `TbFirmBankCodeRepository` hit | ✅ | |
| Deposit No. required | required, ≤100 | `@NotBlank`, required in parser | ✅ | |
| Deposit No. in `ACCT_MSTR` | `(biz_key, acct_no)` lookup → `RCPT_NM`,`DEPO_CCY_CD`; miss `WBE1194` | `findFirstByBizKeyAndAcctNo` → `RCPT_NM`,`DEPO_CCY_CD`; miss error | ✅ | legacy query has no `bank_cd` predicate — refactor matches |
| Transfer Amount | strictly > 0 | `signum() <= 0` rejected | ✅ | |
| Transfer Currency | required, len 3, **VND/USD only** | required; whitelist via `assertCurrencyWhitelist` | ✅ | legacy errors per-row; refactor folds bad ccy into file-level `WBE2120` (granularity differs, outcome same) |
| Single currency / file | `WBW1160` | `assertSingleCurrency` → `WBE2120` | ✅ | |
| Mixed network / file | `WBE1191` (save-side `validateUploadedPayments`) | `assertSingleNetwork` → `WBE2121` (parse-side) | ✅ | refactor enforces at parse, not save |
| Remarks special chars | `SpecialCharValidator` + VN tone strip | `SpecialCharValidator` (no explicit tone strip) | ⚠️ | VN tone-strip not ported; low impact |
| Remarks length | ≤200 flagged at preview | DTO `@Size(max=200)` at save only | ⚠️ G5 | not flagged at preview |
| Reporting Account | `getChartAcctInfm` active-only | `findByBizKeyAndChartAcctCdAndDelYn(..,'N')` | ✅ | refactor also caps len 20 |
| Description (Note) | ≤200 at preview | DTO `@Size(max=255)`; parser no cap | ⚠️ G5 | cap differs (255 vs 200), not flagged at preview |
| Document No. | ≤50 at preview | DTO `@Size(max=50)`; parser no cap | ⚠️ G5 | not flagged at preview |
| Error/complete counts | `TOT_CNT`,`SUC_CNT`,`ERR_CNT` | `tot_cnt`,`suc_cnt`,`err_cnt` | ✅ | |
| Per-cell error packing | `err_fld`/`err_fld_caus` pipe arrays | same (`buildErrPair`) | ✅ | FE splitter unchanged |
| RCPT_NM special-char | NAPAS branch validates | validates resolved `RCPT_NM` | ✅ | |
| Recipient inquiry (`ckRecName`) | NOT called for internal transfer | NOT called | ✅ | own-account semantics |

---

## 6. Withdrawal Bank / Account No. Audit

| Aspect | Legacy | Refactor | Match? |
|---|---|---|---|
| Source | `comm_1001_001` (`pymn_0009_002.ts:529`) | `GET /withdrawal-accounts` → `WithdrawalAccountListService.list(ccyCd)` | ⚠️ V1 verify |
| Auto-select first | yes | yes (`useEffect` selects `list[0]`) | ✅ |
| Account label | bank + acct + type + ccy | `${bank_nm} ${acct_no} (${acctTypeLabel} ${ccy_cd})` | ✅ |
| Account-type label map | `pymn_0009_002.ts:563-570` (03/04/07→Current, 05/06/08→Savings) | identical `ACCT_TYPE_LABEL` | ✅ |
| Drives parse `bank_cd` | withdrawal bank → cut-off + special-char rule | `selectedAccount.bank_cd` passed to parse | ✅ |
| Save uses account | `BANK_CD`,`ACCT_NO`,`CCY_CD` | `bank_cd`,`acct_no` from selected; **`ccy_cd` overwritten with transfer ccy** | ⚠️ G3 |
| Empty-list behaviour | — | "No accounts available", Save disabled | ✅ |

**V1 — to verify:** Confirm `WithdrawalAccountListService` filters to firm-banking-enrolled current/savings accounts (`ACCT_TYPE_CD` 03–08, `ORG_STAT='1'`) exactly as `comm_1001_001`. Not re-read in this audit.

---

## 7. Upload Save DB Audit

### `SCDUL_PYMN_SUMR` (master)

| Column | Legacy value | Refactor value | Gap |
|---|---|---|---|
| `BIZ_KEY` | loginUser.bizKey | TenantContext.bizKey | ✅ |
| `SCDUL_PYMN_NO` | key gen | `keyGenerator.nextScdulPymnNo` | ✅ |
| `SCDUL_PYMN_TYPE_CD` | `'03'` INTERNAL | `'03'` INTERNAL | ✅ |
| `OUT_BANK_CD` | `NapasBankInfo.napasBankCd` | `outBankCdNormalizer.normalize(bank_cd)` | ✅ (verify normalizer == NAPAS lookup) |
| `WDRL_ACT_NO` | `ACCT_NO` | `acct_no` | ✅ |
| `CCY_CD` | `TRNF_CCY_CD` (transfer ccy) | `req.ccyCd()` (= transfer ccy from FE) | ✅ |
| `WDRL_CCY_CD` | `CCY_CD` (**account ccy**) | `req.ccyCd()` (**transfer ccy**) | ❌ **G3** |
| `TOTAL_TRAN_AMT` | Σ after loop | Σ upfront | ✅ |
| `TOT_TRNS_CNT` | `REC.size` | `lines.size` | ✅ |
| `TRNF_YN` | NOT_YET_TRANFER ('N') | NOT_TRANSFERRED ('N') | ✅ |
| `ERP_PROC_CMPL_YN` | (default) | `'N'` | ✅ |
| `ORG_FILE_NM`/`SAVE_FILE_NM`/`FILE_SIZE` | fileInfo | sanitised name + size | ✅ |
| `REG_*`/`MOD_*` | regUser/regDtm | userId / UTC `yyyyMMddHHmmss` | ✅ |

### `SCDUL_PYMN_SUMR_DETL` (per line)

| Column | Legacy value | Refactor value | Gap |
|---|---|---|---|
| `SCDUL_PYMN_DETL_NO` | seq | `wb_get_next_val` seed + increment | ✅ |
| `TRAN_AMT` | `TRAN_AMT` | `tran_amt` | ✅ |
| `TRNF_CCY_CD` | `TRNF_CCY_CD` | `trnf_ccy_cd` (default req.trnfCcyCd) | ✅ |
| `ANT_DIV_CD` | CITAD/NAPAS | CITAD/NAPAS | ✅ |
| `RCPT_NM` | from `ACCT_MSTR` | from `ACCT_MSTR` (forwarded on wire) | ✅ |
| `TAR_BANK_CD` | **`getBankCode` → 6-digit `BANK_CD`** (CITAD resolved) | **raw typed code** (CITAD = 3-digit) | ❌ **G1** |
| `CITAD_BANK_CD` | CITAD: 3-digit; NAPAS: `"000"` | **null** (always) | ❌ **G2** |
| `FINC_MDA_NO` | `FINC_MDA_NO` | `finc_mda_no` | ✅ |
| `TRAN_USAG` | Remarks | Remarks | ✅ |
| `CHART_ACCT_SEQ_NO`/`CD` | from `getChartAcctInfm` | resolved seq + code | ✅ |
| `DEPO_CCY_CD` | from `ACCT_MSTR` | from `ACCT_MSTR` | ✅ |
| `NOTE`/`DOC_NO` | Note/DocNo | note/doc_no | ✅ |
| `REG_*`/`MOD_*` | regUser/regDtm | userId / UTC | ✅ |

### Other tables
- `ERP_TRNS_HSTR` — both write the uploaded file as an ERP-history row (legacy `INTERNAL_TRANSFER_PAYMENT`; refactor `TRNS_TYPE_CD="16"`). ✅ (verify `"16"` == legacy code).
- `AUDIT_LOGS_INFM` — refactor writes `WSVC_ID="internal-transfer"` (slug, per CLAUDE.md identifier rule). Legacy writes none on this path. Acceptable divergence (refactor enhancement).
- VCB-USD guard — legacy blocks at save; refactor has **no equivalent** (G4).

---

## 8. Legacy vs Refactor Comparison Table

| Area | Legacy behavior | Refactor behavior | Match? | Gap / Risk |
|---|---|---|---|---|
| Template columns | 9 cols, 0–8 | 9 cols | ✅ | — |
| Template file name | `WeCMS_Internal_Transfer_upload_{EN,KO,VI}.xlsx` | same | ✅ | — |
| Header names/order | EN/KO/VI prefix-match, row 3 | identical | ✅ | — |
| Required fields | BankCode, FINC_MDA_NO, TRAN_AMT, TRNF_CCY_CD | same | ✅ | — |
| Bank Code validation | len 3/6 + `TB_FIRM_BANK_CODE` lookup | same | ✅ | — |
| Deposit No. validation | `ACCT_MSTR (biz_key, acct_no)` | same | ✅ | — |
| Bank name handling | display-only `getBankName` | `ENG_ABRVAT_NM`/`BANK_NM`/code | ✅ | — |
| Transfer Amount | strictly > 0 | strictly > 0 | ✅ | — |
| Transfer Currency | VND/USD; single per file | whitelist + single per file | ✅ | per-row vs file-level error granularity |
| Withdrawal Bank/Account condition | `comm_1001_001` enrolled accounts | shared `WithdrawalAccountListService` | ⚠️ | **V1** verify filter parity |
| Row success/error count | TOT/SUC/ERR | tot/suc/err | ✅ | — |
| Upload button state | enabled when file + no errors | `uploadDisabled` when errors / no file / busy / no account | ✅ | — |
| DB save table/columns | `SCDUL_PYMN_SUMR(_DETL)` | same | ⚠️ | **G1, G2, G3** column-value drift |
| Default status values | `TRNF_YN='N'`, type `'03'` | same | ✅ | — |
| Approval status/requestor/approver | not set at upload (draft); approval is a later step | not set at upload (draft) | ✅ | upload creates draft only; approval via `request-approval` later |
| Error message behavior | `err_fld`/`err_fld_caus` red cells | same wire shape, FE unchanged | ✅ | — |
| Popup close/reset behavior | close resets popup; success reload list | shared dialog success → `onSaved` refetch | ✅ | — |
| VCB-USD→other-bank | blocked at save (`WBE0000`) | **not implemented** | ❌ | **G4** |
| Mixed network | save-side `WBE1191` | parse-side `WBE2121` | ✅ | enforced earlier |
| Field length caps | flagged at preview | DTO `@Size` at save; Note 255≠200 | ⚠️ | **G5** |

---

## 9. Gaps / Bugs Found

### G1 — CITAD `TAR_BANK_CD` not resolved to 6-digit `BANK_CD` (HIGH)
- **Root cause:** `InternalTransferExcelParser.parseRow` sets `resolvedBankCd = trimmed` (the raw typed code). For CITAD that is the 3-digit CITAD code. Legacy `getBankCode` resolves CITAD's 3-digit `citad_bank_cd` → the 6-digit `BANK_CD` via `TB_FIRM_BANK_CODE_R001` (`bank_stas='1'`, `bank_branch_div='1'`). NAPAS is passthrough in both, so NAPAS rows match.
- **Impact:** For CITAD internal transfers, `SCDUL_PYMN_SUMR_DETL.TAR_BANK_CD` holds a 3-digit value in the refactor vs a 6-digit `BANK_CD` in legacy. Breaks legacy→refactor **data-migration parity** for the column, and any downstream dispatch/transfer logic keyed on `TAR_BANK_CD` being the 6-digit bank code (the legacy VCB-USD guard explicitly assumes "`TAR_BANK_CD` is already resolved to the 6-digit `BANK_CD` here"). NAPAS-only tenants (the common Vietnam case) are unaffected.

### G2 — `CITAD_BANK_CD` always null (HIGH)
- **Root cause:** `ParsedTransferLine` is constructed with `citadBankCd = null` (positional `null` in `parseRow`); the parser never derives it. The save mapper faithfully writes `detl.setCitadBankCd(null)`. Legacy sets `CITAD_BANK_CD` = the 3-digit code for CITAD, `"000"` for NAPAS.
- **Impact:** `SCDUL_PYMN_SUMR_DETL.CITAD_BANK_CD` is null for every refactor row. Downstream firm-banking dispatch / payment-file generation that reads `CITAD_BANK_CD` (CITAD routing) will not have the value; data-migration parity is broken for the column on **every** row (NAPAS included, which should be `"000"`).

### G3 — `WDRL_CCY_CD` stores transfer currency, not account currency (MEDIUM)
- **Root cause:** Frontend `handleUpload` sets `ccy_cd = trnf_ccy_cd = batchCcyCd` (the transfer currency). Backend `doCreateDraft` sets both `master.CCY_CD` and `master.WDRL_CCY_CD = req.ccyCd()`. Legacy keeps them distinct: `CCY_CD = TRNF_CCY_CD`, `WDRL_CCY_CD = CCY_CD` (the withdrawal account's own currency, available on the wire as `selectedAccount.ccy_cd`).
- **Impact:** When the withdrawal account currency ≠ transfer currency, `WDRL_CCY_CD` is wrong in the refactor. Internal transfer is usually same-currency, so observable impact is small, but it is a structural migration-parity drift and the data is already available client-side (`ApiWithdrawalAccountRow.ccy_cd`).

### G4 — VCB-USD → other-bank block not implemented (MEDIUM)
- **Root cause:** Legacy `uploadPaymentExcel` rejects, per row, `OUT_BANK_CD==VCB && TRNF_CCY_CD=='USD' && TAR_BANK_CD!=VCB` with `WBE0000`. No equivalent exists in `InternalTransferExcelParser` or `doCreateDraft`. (Compounded by G1: even if added, it would need the resolved 6-digit `TAR_BANK_CD` to compare against `VCB_BANK_CD`.)
- **Impact:** A USD internal transfer from a VCB withdrawal account to a non-VCB target is accepted by the refactor but blocked by legacy — a business-rule regression (PO mapping 2026-06-16).

### G5 — Field-length caps not flagged at preview (LOW)
- **Root cause:** The refactor parser does not enforce Remarks/Note/DocNo max lengths; only the DTO `@Size` caps at save, and `TransferLineInput.note` is `@Size(max=255)` vs legacy 200.
- **Impact:** Over-length Remarks/Note/Doc No. are not red-flagged in the preview (legacy flags them). At save, an over-200 Note up to 255 chars is accepted by refactor but would be rejected/truncated by legacy. Minor.

### G6 — Stale "Phase A stub" Javadoc (LOW, documentation)
- **Root cause:** `InternalTransferService` (≈ lines 105-107) and `InternalTransferExcelParser` (≈ line 44) still describe `parseExcel` as a non-parsing local stub returning an empty preview. The code fully parses.
- **Impact:** Misleads audits/maintainers (this very audit's discovery pass was misled). No runtime effect.

### G7 — No save-side re-validation (INFO)
- Refactor `createDraft` trusts the frontend-filtered success rows; legacy re-validates at save (mixed-network + VCB-USD), though it skips deposit/contact/recipient re-checks for internal transfer. Acceptable trust boundary, but note G4's guard lived in that legacy save-side block.

---

## 10. Recommended Fix Plan (NOT yet implemented — awaiting approval)

> Order matters: G1 underpins G2 and G4. All changes are API-side parser/service; the frontend wire already carries `citad_bank_cd` and the withdrawal account's `ccy_cd`, so most fixes are backend-only. Per `feedback_preserve_existing_code.md`, touch only the broken paths.

1. **G1 + G2 (parser) — resolve CITAD bank code + populate `citad_bank_cd`.**
   In `InternalTransferExcelParser.parseRow`, on the resolved `TbFirmBankCode` hit:
   - CITAD: set `resolvedBankCd = hit.bankCd` (the 6-digit `BANK_CD`) and `citadBankCd = trimmed` (the 3-digit input).
   - NAPAS: keep `resolvedBankCd = trimmed`; set `citadBankCd = "000"`.
   Thread `citadBankCd` into the `ParsedTransferLine` (replace the positional `null`). The save mapper already writes `detl.setCitadBankCd(line.citadBankCd())`, so no service change is needed once the parser supplies it. Confirm `TbFirmBankCode` exposes the 6-digit `bankCd` for the CITAD lookup branch.

2. **G4 — VCB-USD → other-bank guard.** After G1 makes `tarBankCd` the 6-digit code, add a parse-side check mirroring legacy: when the withdrawal `bank_cd` resolves to VCB and `trnfCcyCd=='USD'` and the row's resolved `tarBankCd != VCB`, emit a row error (new `WBE` code, e.g. `WBE2122`, added to `messages.properties`). Parse-side is preferable to save-side so the user sees it in preview.

3. **G3 — `WDRL_CCY_CD` = withdrawal-account currency.** Two coordinated options (pick with PO):
   - (a) Frontend: send `ccy_cd = selectedAccount.ccy_cd` (account currency) and keep `trnf_ccy_cd = batchCcyCd` (transfer currency); backend maps `CCY_CD = req.trnfCcyCd()` and `WDRL_CCY_CD = req.ccyCd()` to restore the legacy split. **(Changes the meaning of the existing `ccy_cd` field — needs paired FE+BE MR.)**
   - (b) Add an explicit `wdrl_ccy_cd` field to the request DTO sourced from `selectedAccount.ccy_cd`; backend sets `WDRL_CCY_CD` from it. Lower blast radius.
   Recommend (a) only if no other consumer relies on the current `ccy_cd==trnf_ccy_cd` behaviour; otherwise (b).

4. **G5 — preview length caps.** In the parser, flag Remarks > 200 / Note > 200 / Doc No. > 50 as row errors (legacy parity). Align `TransferLineInput.note` `@Size` from 255 → 200 to match the legacy/DDL cap (confirm `SCDUL_PYMN_SUMR_DETL.NOTE` column length first).

5. **G6 — docs.** Rewrite the stale `parseExcel`/parser Javadoc to state it fully parses + validates.

6. **V1 — verify** `WithdrawalAccountListService` filter parity vs `comm_1001_001` (types 03–08, `ORG_STAT='1'`). If it diverges, raise separately.

7. **Tests.** Add/extend parser unit tests for a CITAD row (assert `TAR_BANK_CD` = 6-digit, `CITAD_BANK_CD` = 3-digit) and a NAPAS row (`CITAD_BANK_CD="000"`); a VCB-USD-to-other-bank row (expect error); a length-overflow row. Add a `doCreateDraft` test asserting persisted detail columns.

**Migration-safety note:** G1/G2/G3 are structural drift from legacy. Per the root-CLAUDE legacy-first rule, these should be corrected toward legacy (not "left cleaner"), and the fix is PO-reviewable because it changes persisted column values. No schema change is required — all affected columns already exist in `SCDUL_PYMN_SUMR_DETL` / `SCDUL_PYMN_SUMR`.

---

## 11. Verification Commands

> Read-only. JEX DEV via `postgres_dev_server_jexframe` MCP; refactor DB via `postgres_dev_server_refactor` MCP. No DML/DDL.

**Confirm legacy CITAD vs refactor save shape (refactor DB):**
```sql
-- After an Excel upload in refactor, inspect the saved detail rows
SELECT scdul_pymn_no, scdul_pymn_detl_no, ant_div_cd,
       tar_bank_cd, citad_bank_cd, trnf_ccy_cd
FROM   scdul_pymn_sumr_detl
WHERE  biz_key = 'VN_VNM0004_000001'
ORDER  BY scdul_pymn_no DESC, scdul_pymn_detl_no
LIMIT  20;
-- Expect (post-fix): CITAD rows tar_bank_cd = 6-digit BANK_CD, citad_bank_cd = 3-digit;
--                    NAPAS rows tar_bank_cd = 6-digit, citad_bank_cd = '000'.
-- Today (pre-fix):   CITAD tar_bank_cd = 3-digit, citad_bank_cd = NULL on every row.
```

**Confirm master currency columns (refactor DB):**
```sql
SELECT scdul_pymn_no, ccy_cd, wdrl_ccy_cd, out_bank_cd, wdrl_act_no
FROM   scdul_pymn_sumr
WHERE  biz_key = 'VN_VNM0004_000001' AND scdul_pymn_type_cd = '03'
ORDER  BY reg_dtm DESC LIMIT 10;
-- G3: wdrl_ccy_cd should be the withdrawal ACCOUNT currency, not the transfer currency.
```

**Legacy CITAD resolution reference (JEX DEV):**
```sql
SELECT bank_cd, citad_bank_cd, ant_div_cd, bank_stas, bank_branch_div
FROM   tb_firm_bank_code
WHERE  ant_div_cd = 'CITAD' AND bank_stas = '1' AND bank_branch_div = '1'
LIMIT  20;
-- Shows the 3-digit citad_bank_cd → 6-digit bank_cd mapping getBankCode relies on.
```

**Column lengths (legacy DDL parity for G5):**
```sql
SELECT column_name, data_type, character_maximum_length
FROM   information_schema.columns
WHERE  table_name = 'scdul_pymn_sumr_detl'
       AND column_name IN ('note','doc_no','tran_usag','tar_bank_cd','citad_bank_cd');
```

**Build / test (refactor API):**
```bash
cd "c:/WECMS PROJECT/wecms-api"
./gradlew compileJava
./gradlew test --tests "*InternalTransfer*"
```

**Grep anchors used in this audit:**
```bash
# Legacy
rg -n "readExcelFromInternalTransfer|uploadPaymentExcel|getBankCode|HEADER_TITLE_INTER" "WECMS DEV/WECMS/src/wabooks/helper/PymnHelper.java"
# Refactor parser + save
rg -n "resolvedBankCd|citadBankCd|setWdrlCcyCd|setTarBankCd|setCitadBankCd" "wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer"
# Frontend save payload
rg -n "batchCcyCd|parsedToWire|citad_bank_cd" "wecms-web/components/payment/internal-transfer/InternalTransferUploadDialog.tsx"
```

---

---

## 12. Resolution (implemented 2026-06-29)

All approved gaps landed. `./gradlew compileJava` clean; `./gradlew test --tests "*InternalTransfer*"` green (`InternalTransferExcelParserTest` 12/12, `InternalTransferCreateDraftTest` 1/1, plus the rest of the suite).

| # | Fix | Where |
|---|---|---|
| **G1** | CITAD `TAR_BANK_CD` now persists the resolved registry `BANK_CD` (`hit.getBankCd()`); NAPAS unchanged. | `InternalTransferExcelParser.parseRow` (bank-resolution block) |
| **G2** | `CITAD_BANK_CD` derived: 3-digit code for CITAD, `"000"` for NAPAS, threaded into `ParsedTransferLine` (was hard-null). | `InternalTransferExcelParser.parseRow` + line construction |
| **G3** | Backend now maps `CCY_CD = req.trnfCcyCd()` and `WDRL_CCY_CD = req.ccyCd()`; frontend sends `ccy_cd = selectedAccount.ccy_cd`, `trnf_ccy_cd = batchCcyCd` (option (a)). | `InternalTransferService.doCreateDraft`; `InternalTransferUploadDialog.tsx` `handleUpload` |
| **G4** | VCB-USD → other-bank guard: withdrawal bank normalized to NAPAS once per file (`OutBankCdNormalizer`), per-row error on `TRNF_CCY_CD` when withdrawal is VCB + USD + target ≠ VCB. | `InternalTransferExcelParser` (new dep + `parse`/`parseRow`) |
| **G5** | Preview length caps: Remarks 200 / Description 200 / Document No. 50 flagged at parse. `TransferLineInput.note` `@Size` 255 → 200 (column is `VARCHAR(200)`, confirmed on DEV). | `InternalTransferExcelParser.parseRow`; `TransferLineInput` |
| **G6** | Stale "Phase A stub" Javadoc removed/corrected. | `InternalTransferService`, `ParseExcelResponse`, `ParsedTransferLine`, parser class Javadoc |

**V1 — VERIFIED MATCH (no change).** `WithdrawalAccountListService.list(...)` enforces, per its Javadoc + code: tenant `biz_key`; `ACTV_YN='Y'` + `acct_type_cd IN ('03'..'08')` (current/savings); mandatory `TB_FIRM_CONF` `ORG_STAT='1'` firm-banking enrolment (G3 leg; the relaxation is a local-only `firm-banking-fallback` flag, `false` in SIT/UAT/PROD). It also adds the legacy G1 bank-set (`bank_cd IN ('11704088','11704020')`) and G2 V/W authorization intersection — all `comm_1001_001` legacy behaviour. No gap.

**Tests added/updated.** `InternalTransferExcelParserTest`: CITAD row now asserts resolved `TAR_BANK_CD` (`01202001`) + 3-digit `CITAD_BANK_CD` (`202`); NAPAS row asserts `CITAD_BANK_CD = "000"`; `flags_vcb_usd_transfer_to_other_bank` + `allows_vcb_usd_transfer_to_vcb`; `flags_overlength_remarks_note_and_doc_no`. New `InternalTransferCreateDraftTest`: asserts master `CCY_CD`/`WDRL_CCY_CD` split (G3) + detail `TAR_BANK_CD`/`CITAD_BANK_CD`/amount/type persistence.

**Migration-safety note.** All fixes move the refactor *toward* legacy column values (no new schema; all columns pre-exist). Legacy→refactor migration parity for `SCDUL_PYMN_SUMR_DETL.TAR_BANK_CD` / `CITAD_BANK_CD` and `SCDUL_PYMN_SUMR.WDRL_CCY_CD` is restored.

---

## Recipient Name / Lookup Recipient Legacy Parity Audit

> Added 2026-06-30. Focused parity audit of how **Recipient Name** (`RCPT_NM`) is populated for Internal Transfer and whether the **Lookup Recipient** API (legacy `ckRecName`) participates. Internal Transfer is audited **separately** because — unlike the other four types — it keeps **Excel Upload for HAENGSUNG too** (no ERP Refresh). Evidence-based; **no runtime logic changed**.

### 1. Legacy behavior summary

Internal Transfer targets the **company's own accounts**, so the "recipient" is the owner name of the *deposit account* itself, read from the account master — **not** a third party, a contact, or a live bank inquiry.

- Resolution happens during parse via `validateBankAccountInAcctMstr(...)` ([`PymnHelper.java:614-647`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)): the deposit no. is looked up in `ACCT_MSTR` by `(biz_key, acct_no)` and `RCPT_NM` is set from `ACCT_MSTR.ACCT_NM` (`:637`), with `DEPO_CCY_CD` cross-filled. Miss → `WBE1194`.
- `readExcelFromInternalTransfer` ([`PymnHelper.java:649-857`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java)) calls it for each row (≈`:740`).
- The **Lookup Recipient API `ckRecName`** ([`PymnHelper.java:860-1040`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java) → `FIRM_PROC.inquiryAccount`, `:965`) is **NOT** invoked for Internal Transfer — own-account semantics make a NAPAS/CITAD depositor inquiry meaningless.

### 2. Refactor behavior summary

`InternalTransferExcelParser` resolves `rcptNm` from `ACCT_MSTR` via `acctMstrRepository.findFirstByBizKeyAndAcctNo(...)` ([`InternalTransferExcelParser.java:457-489`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferExcelParser.java); set at `:483` from `getAcctNm()`), then forwards it on the wire so `doCreateDraft` persists `SCDUL_PYMN_SUMR_DETL.RCPT_NM` ([`InternalTransferService.java` detail mapper](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferService.java)). The recipient-inquiry (`ckRecName`) is **intentionally NOT wired** — see the explicit code note at `InternalTransferExcelParser.java:503-511` ("Internal Transfer always targets one of the company's OWN accounts … not a third-party recipient"). This matches legacy exactly (the §5 row "Recipient inquiry (`ckRecName`) — NOT called — own-account semantics" was confirmed ✅ in this audit).

### 3. Recipient Name source comparison

| Source | Legacy | Refactor | Match |
|---|---|---|---|
| Contact / saved recipient (`TRCO_*`) | ❌ never | ❌ never | ✅ |
| Bank Account / account master (`ACCT_MSTR.ACCT_NM`) | ✅ **the source** (`:637`) | ✅ **the source** (`:483`) | ✅ |
| Lookup Recipient API (`ckRecName` / NAPAS inquiry) | ❌ never | ❌ never | ✅ |
| EFS / VAN / WeCMS Batch | ❌ not in this path | ❌ not in this path | ✅ |
| Manual Excel cell | ❌ no Recipient Name column | ❌ no Recipient Name column | ✅ |

### 4. Lookup Recipient condition

| Network | Legacy inquiry? | Refactor inquiry? |
|---|---|---|
| NAPAS | ❌ never (own-account) | ❌ never |
| CITAD | ❌ never (own-account) | ❌ never |

Internal Transfer has **no Lookup Recipient condition** — the API is never reached on either side.

### 5. Contact vs Bank Account source rules

**Bank Account only.** The recipient name is always the *withdrawal/target own-account* owner name from `ACCT_MSTR`. No contact (`TRCO_INFM` / `TRCO_ACCT_INFO`) is consulted and no manual value is accepted. Both systems agree.

### 6. EFS / VAN / WeCMS Batch dependency status

**None for recipient name.** Internal Transfer is **excluded from the HAENGSUNG ERP Refresh integration** (it uses Excel Upload for *all* product types incl. HAENGSUNG — see the note below and [`haengsung-payment-refresh.md`](../../features/integrations/haengsung/payment-refresh.md)). The recipient name is fully resolved from `ACCT_MSTR` at parse/save, before any firm-banking dispatch. The dispatch engine (EFS/VAN/WeCMS Batch — code-complete, all flags OFF, `MockEfsApiClient`/`MockSftpClient` throw when disabled) only carries the already-resolved name forward; it does not source it. So recipient-name behavior does **not** depend on EFS/VAN/Batch connectivity.

### 7. Gap list

- **None** for Recipient Name / Lookup Recipient. `ACCT_MSTR.ACCT_NM` resolution is at full parity; the deliberate absence of `ckRecName` matches legacy own-account semantics. (The Excel-upload gaps G1–G6 in §9 are unrelated to recipient name and were already resolved in §12.)

### 8. Final verdict: **PASS**

Recipient Name source and Lookup Recipient behavior are at full legacy parity. No code change recommended.

### 9. Environment / verifiability note

Fully verifiable in the current environment. Recipient Name resolution has **no EFS/VAN/WeCMS Batch or HAENGSUNG Oracle dependency** — `ACCT_MSTR` is a local refactor-DB table. The flag-gated dispatch integration being disconnected does **not** block verification of this behavior. Internal Transfer never participates in ERP Refresh, so the HAENGSUNG Oracle connection (also OFF) is irrelevant here.

---

### 10. Lead Engineer disposition (2026-06-30, documentation-only)

**Decision: no runtime code changed — ✅ no action required.** Code-verified 2026-06-30: `RCPT_NM` resolves from `ACCT_MSTR.ACCT_NM` ([`InternalTransferExcelParser.java:483`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferExcelParser.java)), is placed on the line at `:559`, and is saved exactly once at [`InternalTransferService.java:605`](../../../../wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferService.java) with no later overwrite. `ckRecName`/EFS is intentionally NOT wired (own-account semantics, `:503-511`). **No EFS/VAN/WeCMS Batch dependency** — fully verifiable in DEV/SIT/UAT/PROD today. Internal Transfer keeps Excel Upload for HAENGSUNG too (no ERP Refresh), so the Oracle connection is irrelevant here. **No runtime files were changed in this audit.**

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

> **Internal Transfer is intentionally excluded from the HAENGSUNG ERP Refresh integration. Excel Upload remains the only supported import method for Internal Transfer across all product types.**

See [haengsung-payment-refresh.md](../../features/integrations/haengsung/payment-refresh.md) for the ERP Refresh design + Oracle mapping, and [payment/README.md → "Payment import strategy"](../../features/payment/README.md#payment-import-strategy--standard-wecms-vs-haengsung) for the module-level matrix.

*End of audit.*

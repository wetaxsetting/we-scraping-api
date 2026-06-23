# Flow 1 — Upload / Create Internal Transfer

Source-code-verified 2026-06-23. Backend authority: `com.kosign.wecms.domain.payment.internaltransfer`. Frontend touchpoints summarised; full frontend detail in [wecms-web/.../internal-transfer/upload-create-flow.md](../../../../../wecms-web/docs/features/payment/internal-transfer/upload-create-flow.md).

---

## Purpose

Turn an uploaded Excel of transfer lines into a persisted **draft batch** (`SCDUL_PYMN_SUMR` master + one `SCDUL_PYMN_SUMR_DETL` row per line, `SCDUL_PYMN_TYPE_CD='03'`). The user picks a firm-banking withdrawal account, uploads the populated template, sees a per-cell-validated preview, then saves the draft. The draft is the input to Flow 4 (request approval) and Flow 2 (dispatch). Mirrors legacy `pymn_0009_002`.

---

## User Actions

| Action | How |
|---|---|
| **Account selection** | Pick a withdrawal bank/account from the firm-banking-capable list (`GET /withdrawal-accounts`). |
| **File upload** | Upload the populated `WeCMS_Internal_Transfer_upload_*.xlsx` template (EN/KO/VI). |
| **Manual entry** | **Not supported** — line data comes only from the Excel file. The only manual fields are the batch Title and withdrawal account. |
| **Validation** | Server parses + per-cell validates every line (POST `/uploads/parse`); the preview red-highlights error cells. |
| **Save** | POST the JSON `request` part + the file → draft persisted, original Excel stored on disk + copied into ERP Transaction History. |
| **Submit for approval** | Separate step — see [approval-flow.md](approval-flow.md). |

---

## Backend / API involvement

| Op | Endpoint | Service method | Notes |
|---|---|---|---|
| Withdrawal-account picker | `GET /api/v1/payment/internal-transfer/withdrawal-accounts?ccy_cd=` | `InternalTransferService.listWithdrawalAccounts` | `AcctMstrRepository.findWithdrawalAccounts` (firm-banking-capable); bank name via `BankInfmRepository`. Mirrors legacy `comm_1001_001`. Returns `WithdrawalAccountRow[]`. |
| **Parse / validate Excel** | `POST .../uploads/parse` (multipart `file_data` + `bank_cd`) | `InternalTransferService.parseExcel` → `InternalTransferExcelParser.parse` | **Real Apache POI parser** (not a stub). Validates extension (`.xls/.xlsx`) + non-empty + `bank_cd` present, then walks the workbook. Returns `ParseExcelResponse(totCnt, sucCnt, errCnt, lines[])`. |
| **Create draft** | `POST .../` (multipart `request` JSON + `file_data`) | `InternalTransferService.createDraft` | `@Transactional`. Persists master + lines, stores the file, writes ERP-history + audit rows. Returns `CreateScheduledTransferResponse(scdulPymnNo)`. |

All endpoints gated by `@PreAuthorize("@menuAuthz.canAccess('payment-internal-transfer-scheduled')")`; tenant from `TenantContext.currentBizKey()`.

### Excel parse (`InternalTransferExcelParser`)
- Row layout: title (row 0), blank (1), descriptions (2), **header (3, validated per-locale EN/KO/VI** → `WBE2018` on mismatch), data from row 4 (cap 500 + early-break on all-critical-blank row).
- Column order: `0` Bank Code (`ANT_DIV_CD`; 3-char→CITAD, 6-char→NAPAS, resolved via `TbFirmBankCodeRepository`), `1` Deposit No. (`FINC_MDA_NO`), `2` Bank (derived), `3` Transfer Amount (`TRAN_AMT`), `4` Transfer Currency (`TRNF_CCY_CD`), `5` Remarks (`TRAN_USAG`), `6` Reporting Account (`CHART_ACCT_CD`), `7` Description (`NOTE`), `8` Document No. (`DOC_NO`).
- Per-cell validation collects `FieldError(fieldId, cause)` → emitted as paired `err_fld` / `err_fld_caus` pipe-delimited strings (legacy `pymn_0009_002.ts:296-298` parity). Required-field checks (`FINC_MDA_NO`, `ANT_DIV_CD`, `TRAN_AMT`, `TRNF_CCY_CD`), bank-code lookup, amount parse/non-negative, `RCPT_NM`+`DEPO_CCY_CD` resolved from `ACCT_MSTR` (`findFirstByBizKeyAndAcctNo` — Internal Transfer always targets a company-own account), `SpecialCharValidator` on Remarks + recipient name (`BLOCKED_CHARS_RULE`), `CHART_ACCT_CD` resolved against `CHART_ACCT_INFM` (`findByBizKeyAndChartAcctCdAndDelYn`).
- Cross-row consistency (`UploadConsistencyValidator`): single currency (`WBE2120`), VND/USD whitelist, single network CITAD-vs-NAPAS (`WBE2121`).

### Create draft (`createDraft`)
1. `keyGenerator.nextScdulPymnNo(bizKey)` → per-tenant plain-int key via the `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no',biz_key)` funnel (`InternalTransferKeyGenerator` → `LegacyFunctionRepository`, `@Transactional(MANDATORY)`). The master INSERT is `saveAndFlush`-ed up front; on a `DataIntegrityViolationException` (the funnel `MAX+1` has no row lock) `createDraft` re-runs the whole transaction once via `TransactionalRetry.runOnce` before surfacing `WBE_SCHEDULED_PAYMENT_KEY_RACE`. **NOTE (2026-06-09 OD-2 reversal):** the earlier refactor-only date-prefixed `<yyyyMMdd><6-seq>` counter-table key was removed — the generator now mints the same value the live JEX DB would.
2. Master: `outBankCd` normalised to canonical NAPAS code (`OutBankCdNormalizer`), `wdrlActNo`, `ccyCd`/`wdrlCcyCd`, `titl`, `trnfYn='N'`, `totTrnsCnt`=line count, `totalTranAmt`=Σ line amounts.
3. If a file is present: sanitise name, `saveFileNm = <scdulPymnNo>_<orgFileNm>`, stream to `<storage-root>/<bizKey>/internal-transfer/`, stamp `orgFileNm`/`saveFileNm`/`fileSize`.
4. One `SCDUL_PYMN_SUMR_DETL` per line. `SCDUL_PYMN_DETL_NO` is seeded once via the `wb_get_next_val('scdul_pymn_sumr_detl','scdul_pymn_detl_no',biz_key)` funnel then incremented in Java across the batch (same pattern as Overseas) — copying all line fields.
5. Copy the Excel into **ERP Transaction History** (`ErpTrnsHistoryWriter.recordUploadedFile`, `TRNS_TYPE_CD='16'`, best-effort `REQUIRES_NEW`).
6. Audit row (`WSVC_ID='internal-transfer'`, detail `audit.internal_transfer.create.detail`).

### Validation errors
- `parseExcel`: blank/empty → `WBE_VALIDATION`; wrong extension → `WBE_UNSUPPORTED_MEDIA_TYPE`; missing `bank_cd` → `WBE_VALIDATION`; header mismatch → `WBE2018`; IO/format → `WBE2017`; mixed currency/network → `WBE2120`/`WBE2121`.
- `createDraft`: bean-validation on `CreateScheduledTransferRequest`/`TransferLineInput` (`finc_mda_no` required per line); file-write failure → `WBE2070`.

---

## Main UI components (frontend touchpoints)

- Page: `components/payment/internal-transfer/ScheduledTransferPage.tsx` toolbar "Excel Upload" → `InternalTransferUploadDialog`.
- `InternalTransferUploadDialog.tsx` wraps the shared `PaymentExcelUploadDialog` → `components/shared/excel-upload/ExcelUploadDialog.tsx` (Download template → Browse → Preview with per-cell error highlights → Confirm). Calls `parseInternalTransferExcel`, `fetchWithdrawalAccounts`, `createScheduledTransfer` (`lib/api/internalTransfer.ts`). Per-locale templates under `public/templates/internal-transfer/`.
- On save success → `onSaved(scdulPymnNo)` → list refetch (page 0, refresh key bump).

---

## Database / persistence

| Table | Write |
|---|---|
| `SCDUL_PYMN_SUMR` | master insert (`SCDUL_PYMN_TYPE_CD='03'`, `TRNF_YN='N'`, file columns, amount/count) |
| `SCDUL_PYMN_SUMR_DETL` | one row per line |
| `ERP_TRNS_HSTR` | best-effort upload-history row (`TRNS_TYPE_CD='16'`) |
| `AUDIT_LOGS_INFM` | create audit row (`WSVC_ID='internal-transfer'`) |
| filesystem | original Excel under `<storage-root>/<bizKey>/internal-transfer/<saveFileNm>` |

Entities: `ScdulPymnSumr` (`@Table EXPT… no` → `@Table("SCDUL_PYMN_SUMR")`, PK `(BIZ_KEY, SCDUL_PYMN_NO)`), `ScdulPymnSumrDetl` (PK `(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)`). Columns of note: `OUT_BANK_CD`, `WDRL_ACT_NO`, `CCY_CD`/`WDRL_CCY_CD`, `TITL`, `TOT_TRNS_CNT`, `TOTAL_TRAN_AMT`, `ORG_FILE_NM`/`SAVE_FILE_NM`/`FILE_SIZE`, `APPR_SEQ_NO` (null until Flow 4), `TRNF_YN`/`TRNF_DTM`. **No `APPR_STTS` column** — the refactor removed it (legacy JEX has none); status is derived on read via `wb_get_appr_stts`.

---

## Legacy mapping

| Legacy (JEX, read-only) | Refactor |
|---|---|
| `pymn_0009_002_r001_act.jsp` + `PymnHelper.importDataFromExcelForInternalTransfer` | `POST .../uploads/parse` → `InternalTransferExcelParser` |
| `pymn_0009_002_c001_act.jsp` + `PymnHelper.uploadPaymentExcel` | `POST .../` → `InternalTransferService.createDraft` |
| `comm_1001_001` (withdrawal account picker) | `GET .../withdrawal-accounts` |
| `PymnHelper.uploadPaymentExcel:1315-1317` (OUT_BANK_CD normalise) | `OutBankCdNormalizer` |
| `ErpTrnsHisHelper.erpTrnsHisUploader` | `ErpTrnsHistoryWriter.recordUploadedFile` |
| `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no')` | `InternalTransferKeyGenerator.nextScdulPymnNo` → `LegacyFunctionRepository` → the **same** `wb_get_next_val` branch (funnelled, not bypassed) |

---

## Legacy parity status

**MATCH** — parse + per-cell validation, save-draft, withdrawal-account picker, file storage, and ERP-history capture are all implemented against the live schema, with `SCDUL_PYMN_NO` minted through the legacy `wb_get_next_val` funnel (DEV-parity values). Deliberate divergence: no manual line entry (legacy is also Excel-only). The `SCDUL_PYMN_NO` entity column is mapped at `length=10` (legacy DEV `VARCHAR(10)`); the path-param regex stays permissive (`1..30` digits) only to accept seed values.

## Refactor status

**Implemented.** (Supersedes the stale frontend client comment that calls parse a "stub".)

## Open gaps (source-evidenced)

- Reporting-account existence is validated in the parser; the line-level `PATCH .../reporting-account` edit (Flow 2) defers the `CHART_ACCT_INFM` existence re-check (`InternalTransferService.updateReportingAccount` comment — entity migrated, so this could be tightened).
- `WDRL_CCY_CD` NULL-fallback to `ACCT_MSTR.CCY_CD` (legacy issue #42761) is documented in the baseline spec; create stamps `wdrlCcyCd = ccyCd` directly from the request.

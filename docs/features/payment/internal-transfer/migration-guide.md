# Internal Transfer — Migration Guide (JEX → Spring)

> **Hub:** [README.md](README.md) · **Gap matrix:** [refactor-gap-analysis.md](refactor-gap-analysis.md) · **Full mapping:** [migration/internal-transfer-mapping.md](../../../references/migration/internal-transfer-mapping.md)

Source-code-verified 2026-06-23. Legacy WSVC / IDO / `_act.jsp` → refactor controller/service, field-by-field, with approved deviations and dead legacy flagged.

---

## 1. Endpoint mapping

| Legacy (JEX, read-only) | Refactor endpoint → service |
|---|---|
| `pymn_0009_001_r001_act.jsp` + `PymnHelper.pymn_0001_001_r001("03")` | `GET /` → `list` → `ScdulPymnSumrRepository.search` |
| `pymn_0009_003_r001_act.jsp` (IDOs `SCDUL_PYMN_SUMR_R012`/`_R013`) | `GET /{id}` → `detail` |
| `comm_1001_001` | `GET /withdrawal-accounts` → `listWithdrawalAccounts` |
| `pymn_0009_002_r001_act.jsp` + `importDataFromExcelForInternalTransfer` | `POST /uploads/parse` → `InternalTransferExcelParser` |
| `pymn_0009_002_c001_act.jsp` + `uploadPaymentExcel` | `POST /` → `createDraft` |
| `pymn_0009_002_u002_act.jsp` + `pymn_0001_002_u002` | `PATCH /{id}/lines/{lineId}/reporting-account` → `updateReportingAccount` |
| `pymn_0009_001_d001_act.jsp` | `DELETE /{id}` → `delete` |
| `pymn_0009_001_c001_act.jsp` + `pymn_0009_001_c001` + `validateTransferTiming` | `POST /{id}/dispatch` → `InternalTransferDispatchService` (+ `InternalTransferDispatchExecutor` + `CutoffValidator`) |
| `comm_0194_001` → `comm_0192_001_c005` | `POST /{id}/request-approval` → `requestApproval` |
| `comm_0195_001` footer → `bltb_0001_003_u001` | `POST /{id}/approval-action` → `actOnApproval` → `ApprovalBoxService` |
| `comm_0195_001` kebab Delete | `DELETE /{id}/approval` → `withdrawApproval` → `ApprovalBoxFanoutService` |
| `comm_0195_001` edit-mode (`comm_0192_013_u002`) | `PUT /{id}/approval-line` → `changeApprovalLine` |
| `pymn_0010_001_r001_act.jsp` + `pymn_0002_001_r001(INTERNAL)` | `GET /results` → `InternalTransferResultService.list` → `searchDispatched` |
| `pymn_0010_002_r001_act.jsp` + `pymn_0002_002_r001(INTERNAL)` (IDO `SCDUL_PYMN_SUMR_R006`) | `GET /results/{id}` → `detail` → `DispatchResultResolver` |
| client-side XLSX | `POST /results/export` → `InternalTransferResultExportProvider` |
| `comm_0197_*` (domestic match) | `/{id}/lines/{lineId}/match*` → `DomesticPaymentLineMatchService` |

---

## 2. Field-by-field highlights

### Save draft (`pymn_0009_002_c001` → `createDraft`)
- `TITL` → `titl` (≤100, required, both sides).
- `BANK_CD`/`ACCT_NO`/`CCY_CD`/`TRNF_CCY_CD` → same wire names; `OUT_BANK_CD` normalised via `OutBankCdNormalizer` (legacy `uploadPaymentExcel:1315-1317`).
- `SCDUL_PYMN_NO` → minted via `wb_get_next_val` funnel (matches live JEX value); column `VARCHAR(10)`.
- `SCDUL_PYMN_DETL_NO` → seeded once via funnel + Java increment across batch.
- Excel copied to `ERP_TRNS_HSTR` (`TRNS_TYPE_CD='16'`) via `ErpTrnsHistoryWriter.recordUploadedFile` (legacy `ErpTrnsHisHelper.erpTrnsHisUploader`).

### Dispatch (`pymn_0009_001_c001` → executor)
- Routing rule preserved verbatim: API path (`TB_PAY_TARGET`) when `(TRNF_CCY_CD='VND' && !isShinhanUsdToVnd) || isWooriUsdToVnd`; else file path (`TB_PAY_FILE_MST` + N `TB_PAY_FILE_DATA`).
- Woori USD→VND + Shinhan USD→VND special cases preserved.
- Cut-off gate `validateTransferTiming` → `CutoffValidator.validate(outBankCd, network)`.
- Amount rounded 6→2 into dispatch tables.

### Request approval (`comm_0192_001_c005` → `requestApproval`)
- `APPR_DV='35'` (Internal Transfer), `APPR_STS='2'` (In Progress), `APPR_TYPE_CD='00'`.
- `DOC_NO` = `VO_YYMMDD_NNN` via `DocNoGenerator` (canonical home `EXPT_RESOL_MSTR.DOC_NO`, **not** back-stamped onto `SCDUL_PYMN_SUMR`).
- `PAY_DATE` = req.payDate (Scheduled Date); `CONT` = postCont.
- Chain: line 1 `APPRVR_STTS='P'`, rest `'W'`.

---

## 3. Approved deviations

| Deviation | Reason |
|---|---|
| **No stored `APPR_STTS` column** — status derived on read via `wb_get_appr_stts` | `SCDUL_PYMN_SUMR` has no such column in legacy DDL; denormalized mirror removed 2026-06-10 (`project_scdul_pymn_sumr_appr_stts_remap.md`) |
| `SCDUL_PYMN_NO` minted via `wb_get_next_val` funnel (not date-prefixed) | OD-2 reversal 2026-06-09 — date-prefixed 14-char counter-table key removed; mints same value as live JEX (`project_wb_get_next_val_funnel.md`) |
| `APPR_SEQ_NO` via **global** `sq_appr_seq_no` sequence | shared across all Payment modules (funnel) |
| Rejected status = `R` (not legacy `X`) | derived status code; FE maps both |
| Result `trnf_stts` route-aware `C`/`P` (not `Y`/`N`) | C2 fix — dispatch-result status filter; unknown/blank degrades to no filter |
| Result date window on `TRNF_DTM` (not `REG_DTM`) | F-1 legacy parity |
| `DOC_NO` not back-stamped onto `SCDUL_PYMN_SUMR` | `ERP_FILE_NUM` is `VARCHAR(8)` — overloading overflowed it |
| Manual line entry not supported | legacy is also Excel-only (intentional) |
| No local bank daemon flips `PROC_STAT` | environmental (R&D local-only) |

---

## 4. DEAD LEGACY

- **Re-run** (`pymn_0010_002.ts:441-472`) — never ported. No backend endpoint/service/error-code, no frontend client. PO-deprecated cross-module (`project_payment_result_sit_ready.md`). Controller carries a placeholder comment block only.
- **Re-submit after reject** — legacy never supported it; refactor reproduces terminal-on-reject. The FE kebab "Re-submit" is an inert refactor-only placeholder (recommend removal). See [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 5. Cross-links

[business-flow.md](business-flow.md) · [interface-specification.md](interface-specification.md) · [database-mapping.md](database-mapping.md) · [refactor-gap-analysis.md](refactor-gap-analysis.md)

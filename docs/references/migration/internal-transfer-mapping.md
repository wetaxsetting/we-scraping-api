# Internal Transfer — JEX → Refactor Mapping

| Field | Value |
|---|---|
| Slug | `internal-transfer` |
| Module | `payment` |
| REST base | `/api/v1/payment/internal-transfer` (Scheduled flow) + `/api/v1/payment/internal-transfer/results` (Result flow — separate `TransferResultController`) |
| Route base | `/payment/internal-transfer` |
| Counterpart docs | [features/payment/internal-transfer.md](../../features/payment/internal-transfer/README.md) · [database/payment/internal-transfer-schema.md](../database/payment/internal-transfer-schema.md) · [backend/internal-transfer.md](../business-rules/internal-transfer.md) · [frontend/internal-transfer.md](../../../../wecms-web/docs/features/payment/internal-transfer/README.md) |
| Reconciled against DEV | 2026-05-13 — see § 4 schema notes |
| Code reconciled | 2026-05-21 — REST endpoints + Java DTO names + Flyway versions verified against `wecms-api` HEAD |

> Legacy `pymn_xxxx_xxx` ids appear ONLY in the **JEX service id** column and in JEX file:line citations — never as Java / TS / route / i18n / log / View Setting `screenId` identifiers in the refactor. REST URIs are kebab-case under `/api/v1/payment/internal-transfer`. The slug `internal-transfer` is the only refactor identifier (see workspace [CLAUDE.md](../../../../CLAUDE.md) § Cross-Tree Rules and [wecms-api/CLAUDE.md](../../../CLAUDE.md) § "Refactor identifier naming").

---

## 1. Service mapping table

One row per JEX `_r/_c/_u/_d` action and per view-only excel-template wrapper in scope.

| JEX service id | Op | Refactor REST endpoint (as-implemented) | Frontend wiring | Notes |
|---|---|---|---|---|
| `pymn_0009_001_r001` | list (Scheduled Transfer) | `GET /api/v1/payment/internal-transfer` → [InternalTransferController.list:113](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L113) | API client [`fetchScheduledTransferList`](../../../../wecms-web/lib/api/internalTransfer.ts) consumed via shared [`useApiData`](../../../../wecms-web/lib/api/useApiData.ts) hook (no per-feature hooks — `wecms-web/CLAUDE.md` explicitly forbids `react-query`/`swr`) inside [`<ScheduledTransferPage>`](../../../../wecms-web/components/payment/internal-transfer/ScheduledTransferPage.tsx). | Paged. Query params: `from_date`, `to_date`, `srch_wd?`, `appr_type?`, `sort_field?`, `sort_direction?`, `page?`, `size?`. Sort via [`InternalTransferSortField`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferSortField.java) whitelist. View Setting `screenId = payment-internal-transfer-scheduled`. |
| `pymn_0009_001_c001` | execute (dispatch) | `POST /api/v1/payment/internal-transfer/{id}/dispatch` → [InternalTransferController.dispatch:280](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L280) | API client function (see [`lib/api/internalTransfer.ts`](../../../../wecms-web/lib/api/internalTransfer.ts)); called from the Detail page's "Transfer" CTA in [`<ScheduledTransferDetailPage>`](../../../../wecms-web/components/payment/internal-transfer/ScheduledTransferDetailPage.tsx) via a `useDialog`-mediated confirmation flow. | Routes through [`InternalTransferDispatchService.dispatch:69`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferDispatchService.java#L69) — cut-off validation via [`CutoffValidator`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/CutoffValidator.java) → stamps `TRNF_YN='Y'` + audit. **Phase A stub** — does NOT insert TB_PAY_TARGET / TB_PAY_FILE_MST rows (deferred per backend §15.6). Permission `T`. Body: `{ trnf_ccy_cd }`. |
| `pymn_0009_001_d001` | delete | `DELETE /api/v1/payment/internal-transfer/{id}` → [InternalTransferController.delete:295](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L295) | API client `deleteScheduledTransfer`; called from row-action dropdown on `<ScheduledTransferPage>`. | Only allowed when `APPR_SEQ_NO` is blank → `WBE2062`. ERP_TRNS_HSTR write deferred to Phase B (storage isn't wired yet). |
| `pymn_0009_002_r001` | parse Excel | `POST /api/v1/payment/internal-transfer/uploads/parse` → [InternalTransferController.parseExcel:215](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L215) | API client [`parseInternalTransferExcel`](../../../../wecms-web/lib/api/internalTransfer.ts) called from [`<InternalTransferUploadDialog>`](../../../../wecms-web/components/payment/internal-transfer/InternalTransferUploadDialog.tsx)'s `handleParse`; the dialog composes the shared [`<PaymentExcelUploadDialog>`](../../../../wecms-web/components/payment/shared/PaymentExcelUploadDialog.tsx). | Multipart `file_data` + `bank_cd`. **Phase A stub** — file-shape validation only; Apache POI walk + per-cell validation in a follow-up MR (see backend §15.6). Response shape: `ParseExcelResponse { tot_cnt, suc_cnt, err_cnt, rows[] }`. |
| `pymn_0009_002_c001` | save draft | `POST /api/v1/payment/internal-transfer` → [InternalTransferController.create:244](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L244) | API client [`createScheduledTransfer`](../../../../wecms-web/lib/api/internalTransfer.ts) → [`<InternalTransferUploadDialog>`](../../../../wecms-web/components/payment/internal-transfer/InternalTransferUploadDialog.tsx)'s `handleUpload`. | Multipart `file_data` + JSON-part `request`. Persists `SCDUL_PYMN_SUMR` + N `SCDUL_PYMN_SUMR_DETL` (transactional) + audit row. **Phase A:** original Excel NOT written to disk (storage wiring deferred). Generates `SCDUL_PYMN_NO` via [`InternalTransferKeyGenerator.nextScdulPymnNo`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferKeyGenerator.java#L77) (`YYYYMMDD<seq:6>` per (tenant, Vietnam-local day)). Returns `{ scdul_pymn_no }`. |
| `pymn_0009_002_u002` | update Reporting Account | `PATCH /api/v1/payment/internal-transfer/{id}/lines/{lineId}/reporting-account` → [InternalTransferController.updateReportingAccount:255](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L255) | API client `updateReportingAccount`; called from the Reporting Account picker on `<ScheduledTransferDetailPage>`. | Body: `{ chart_acct_seq_no, chart_acct_cd }`. **Phase A:** CHART_ACCT_INFM existence guard (`WBE2065`) reserved but not thrown — CHART_ACCT_INFM entity not migrated locally yet. |
| `pymn_0009_003_r001` | detail (draft / pre-dispatch) | `GET /api/v1/payment/internal-transfer/{id}` → [InternalTransferController.detail:150](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L150) | API client `fetchScheduledTransferDetail` → [`<ScheduledTransferDetailPage>`](../../../../wecms-web/components/payment/internal-transfer/ScheduledTransferDetailPage.tsx) via `useApiData`. | Returns `ScheduledTransferDetailResponse` — master (`titl`, `total_tran_amt`, `out_bank_*`, `wdrl_act_no`, file metadata) + `rows: TransferLineDetail[]`. |
| `pymn_0009_003_r002` *(view-only excel template)* | export (Scheduled detail Excel) | **Deferred** — no `POST /api/v1/payment/internal-transfer/{id}/export` endpoint in Phase A; the shared `common/export/` pipeline + a `ScheduledTransferDetailExportProvider` lands in a follow-up MR. | (not wired) | Legacy `r002` is `viewUseYn=Y, actUseYn=N` — refactor consolidates into the standard `POST .../export` shape per [api CLAUDE.md § Excel export](../../../CLAUDE.md#excel-export--commonexport-shared-pipeline--per-screen-exportrowprovider) when the provider lands. |
| `pymn_0010_001_r001` | list (Transfer Result) | `GET /api/v1/payment/internal-transfer/results` → [TransferResultController.list:52](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/TransferResultController.java#L52) | API client `fetchTransferResultList` → [`<TransferResultPage>`](../../../../wecms-web/components/payment/internal-transfer/TransferResultPage.tsx). | Read-only; query: `from_date`, `to_date`, `srch_wd?`, `trnf_stts?`, `sort_field?`, `sort_direction?`, `page?`, `size?`. Sort via [`InternalTransferResultSortField`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferResultSortField.java). View Setting `screenId = payment-internal-transfer-result`. |
| `pymn_0010_002_r001` | detail (Transfer Result) | `GET /api/v1/payment/internal-transfer/results/{id}` → [TransferResultController.detail:69](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/TransferResultController.java#L69) | API client `fetchTransferResultDetail` → [`<TransferResultDetailPage>`](../../../../wecms-web/components/payment/internal-transfer/TransferResultDetailPage.tsx). | Master + `rows: TransferResultLine[]`. **Phase A:** per-line `proc_stat`/`rs_cd`/`rs_msg`/`proc_dttm`/`rslt_dttm` ship as `null` until the dispatch-tables join lands (TB_PAY_TARGET / TB_PAY_FILE_DATA migrated; SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN join). |
| `pymn_0010_002_r002` *(view-only excel template)* | export (Transfer Result detail Excel) | `POST /api/v1/payment/internal-transfer/results/export` → [TransferResultController.exportResults:85](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/TransferResultController.java#L85) | API client (export helper); provider implementation at [`InternalTransferResultExportProvider`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/export/). | XLSX via shared common/export pipeline. |
| (refactor addition — no JEX twin) | list withdrawal accounts | `GET /api/v1/payment/internal-transfer/withdrawal-accounts` → [InternalTransferController.listWithdrawalAccounts:139](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L139) | API client [`fetchWithdrawalAccounts`](../../../../wecms-web/lib/api/internalTransfer.ts) → drives the [`<WithdrawalAccountDropdown>`](../../../../wecms-web/components/payment/shared/WithdrawalAccountDropdown.tsx) inside the upload dialog. | Returns `WithdrawalAccountRow[]` filtered by optional `ccy_cd`. New endpoint vs legacy (legacy `pymn_0009_002` popup reads from `ACCT_MSTR` directly via IDO). |
| (refactor addition — no JEX twin) | file download | `GET /api/v1/payment/internal-transfer/{id}/file` → [InternalTransferController.downloadFile:180](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L180) | (frontend wiring deferred — `FileCard` consumer) | Streams the original uploaded Excel from `wecms.internal-transfer.storage-root`. **Phase A stub** — storage is not wired so endpoint surfaces `WBE2015`-equivalent when invoked. |
| `pymn_0009_001` *(refactor addition)* | request approval | `POST /api/v1/payment/internal-transfer/{id}/request-approval` → [InternalTransferController.requestApproval:320](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L320) | API client [`submitInternalTransferApproval`](../../../../wecms-web/lib/api/internalTransfer.ts); called from the [`<RequestApprovalDialog>`](../../../../wecms-web/components/payment/shared/RequestApprovalDialog.tsx) (shared shell). | Coordinates with the `payment/approvalbox/` cross-module flow — stamps `APPR_SEQ_NO` on the batch when the approval engine returns a resolution id. |
| `pymn_0009_001` *(refactor addition)* | approval action (approve / reject) | `POST /api/v1/payment/internal-transfer/{id}/approval-action` → [InternalTransferController.approvalAction:349](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java#L349) | API client; called from the [`<ApprovalDetailsDialog>`](../../../../wecms-web/components/payment/shared/ApprovalDetailsDialog.tsx) approve / reject CTAs. | Delegates to the shared [`ApprovalBoxService`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/service/ApprovalBoxService.java) (`WSVC_ID = "approval-box"` per Javadoc). |

### Notes on scope deviation from the original brief

The brief's hypothesis listed `pymn_0009_002_c002` and a `pymn_0009_003_r002` action JSP. Discovery confirms (see [WSVC](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/) directory listing in feature doc § 3):

- `pymn_0009_002_c002` **does not exist**. The actual `pymn_0009_002` set is `r001` + `c001` + `u002`. The intent the brief inferred ("confirm upload") is collapsed into the `c001` save-draft action — there is no two-step parse-then-confirm RPC pair on the JEX side; the frontend dialog holds the parsed rows in memory and submits everything in one `c001` call.
- `pymn_0009_003_r002` and `pymn_0010_002_r002` are **view-only Excel template wrappers** (`viewUseYn=Y, actUseYn=N`). They are not history endpoints. The refactor consolidates these into the project-wide XLSX export contract via `POST .../export` per the api CLAUDE.md "Excel export" rule.

This is logged as **OQ-4** in the feature spec.

## 2. Wire field mapping (UPPER_SNAKE WSVC → snake_case wire → camelCase Java DTO)

The global Jackson `SNAKE_CASE` strategy ([JacksonConfig](../../../src/main/java/com/kosign/wecms/config/JacksonConfig.java)) does the camelCase → snake_case translation; the legacy UPPER_SNAKE never escapes the JEX tree. Mapping shown for the four most-touched WSVCs:

### 2.1 `pymn_0009_001_r001` → `GET /api/v1/payment/internal-transfer`

WSVC source: [WSVC.pymn_0009_001_r001.xml:32-92](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_001_r001.xml#L32-L92).

**Request (query params on the refactor side):**

| Legacy WSVC IN | Wire param | Java field | Notes |
|---|---|---|---|
| `FROM_DT` | `fromDt` | `String fromDt` | YYYYMMDD; required |
| `TO_DT` | `toDt` | `String toDt` | YYYYMMDD; required |
| `SRCH_WD` | `srchWd` | `String srchWd` | Free-text |
| `REC_IN.APPR_TYPE_CD` | `apprType` | `String apprType` | Refactor flattens the record to a scalar query param |
| `PAGE_NO` | `pageNo` | `Integer pageNo` | Default 1 |
| `PAGE_PER_CNT` | `pageSize` | `Integer pageSize` | Renamed to match `Pagination` shared rule; default 10 |
| — | `sortField` | `String sortField` | View Setting FIELD_ID per § 13a |
| — | `sortDirection` | `String sortDirection` | `ASC` / `DESC` |

**Response — top level:**

| Legacy WSVC OUT | Wire field | Java DTO record (`ScheduledTransferListResponse`) | Notes |
|---|---|---|---|
| `TOT_CNT` | `total_count` | `Integer totalCount` | |
| `TOT_REC` (record) | `currency_totals` | `List<CurrencyTotal>` | Renamed from JEX `TOT_REC` for clarity |
| `REC` (record) | `rows` | `List<ScheduledTransferRow>` | |

**`TOT_REC` → `currency_totals` element (`CurrencyTotal`):**

| Legacy | Wire | Java |
|---|---|---|
| `TOTL_AMT` | `total_amount` | `BigDecimal totalAmount` |
| `TOT_CNT_CCY_CD` | `total_count` | `Integer totalCount` |
| `CCY_CD` | `ccy_cd` | `String ccyCd` |
| `TOT_TRNS_CNT` | `total_transaction_count` | `Integer totalTransactionCount` |

**`REC` → `rows` element (`ScheduledTransferRow`):**

| Legacy | Wire | Java | Notes |
|---|---|---|---|
| `SCDUL_PYMN_NO` | `scdul_pymn_no` | `String scdulPymnNo` | PK; surfaces as the `id` segment in the detail URL |
| `SCDUL_PYMN_TYPE_CD` | `scdul_pymn_type_cd` | `String scdulPymnTypeCd` | Always `"03"` for Internal Transfer |
| `CCY_CD` | `ccy_cd` | `String ccyCd` | |
| `TITL` | `titl` | `String titl` | Batch title (legacy field name kept on wire for stability) |
| `APPR_SEQ_NO	` | `appr_seq_no` | `String apprSeqNo` | Legacy WSVC has a literal tab in the id; refactor cleans up |
| `TOTAL_TRAN_AMT` | `total_tran_amt` | `BigDecimal totalTranAmt` | |
| `TOT_TRNS_CNT` | `tot_trns_cnt` | `Integer totTrnsCnt` | |
| `REG_DTM` | `reg_dtm` | `OffsetDateTime regDtm` | Wire as ISO-8601 |
| `DRAFT_USER_NM` | `draft_user_nm` | `String draftUserNm` | Requestor display name |
| `APPR_STTS` | `appr_stts` | `String apprStts` | Code value |
| `APPR_STTS` (i18n) | `appr_stts_key` | `String apprSttsKey` | New refactor sibling — `payment.appr_stts.<code>` |
| `TRNF_STTS` | `trnf_stts` | `String trnfStts` | |
| `TRNF_YN` | `trnf_yn` | `String trnfYn` | `Y` / `N` |
| `FILE_URL` | `file_url` | `String fileUrl` | Computed `…/internal-transfer/{id}/file` URL |
| `REC_1` (approvers record) | `approvers` | `List<ApproverRow>` | One per `APPR_LINE_INFM_DETL` entry |
| `APPRVR_TOT_CNT` | `appr_total_count` | `Integer apprTotalCount` | |
| `APPRVR_APPR_TOT_CNT` | `appr_approved_count` | `Integer apprApprovedCount` | |
| `DOC_NO` | `doc_no` | `String docNo` | |
| `APPR_DV` | `appr_dv` | `String apprDv` | Approval workflow discriminator |
| `POST_SEQ_NO` | `post_seq_no` | `String postSeqNo` | |
| `OUT_BANK_CD` | `out_bank_cd` | `String outBankCd` | |
| `WDRL_ACT_NO` | `wdrl_act_no` | `String wdrlActNo` | |
| `OUT_BANK_NM` | `out_bank_nm` | `String outBankNm` | Resolved via `BANK_INFM` lookup; redundant once `BankCdResolver` is consumed but kept for parity with legacy |
| `PYMN_TYPE` | `pymn_type` | `String pymnType` | Always `INTERNAL_TRANSFER` |
| `PAY_DATE` | `pay_date` | `LocalDate payDate` | |
| `FILE_NM` | `file_nm` | `String fileNm` | Saved Excel name |
| `POST_REG_ID` | `post_reg_id` | `String postRegId` | |
| `REG_ID` | `reg_id` | `String regId` | |
| `WDRL_CCY_CD` | `wdrl_ccy_cd` | `String wdrlCcyCd` | Withdrawal account currency — drives routing |

**`REC_1` → `approvers` element (`ApproverRow`):**

| Legacy | Wire | Java |
|---|---|---|
| `APPRVR_STTS` | `apprvr_stts` | `String apprvrStts` |
| `APPRVR_NM` | `apprvr_nm` | `String apprvrNm` |
| `APPRVR_ID` | `apprvr_id` | `String apprvrId` |
| `APPRVR_LINE_NO` | `apprvr_line_no` | `Integer apprvrLineNo` |
| `MOD_DTM` | `mod_dtm` | `OffsetDateTime modDtm` |
| `REG_IMG_URL` | `reg_img_url` | `String regImgUrl` |

### 2.2 `pymn_0009_002_c001` → `POST /api/v1/payment/internal-transfer`

WSVC source: [WSVC.pymn_0009_002_c001.xml:32-65](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_002_c001.xml#L32-L65).

**Request body (`CreateScheduledTransferRequest`):**

| Legacy WSVC IN | Wire | Java | Notes |
|---|---|---|---|
| `TITL` | `titl` | `String titl` | `@NotBlank @Size(max=100)` |
| `FILE_DATA` (multipart) | `file_data` (multipart part) | `MultipartFile fileData` | Excel binary kept on disk |
| `BANK_CD` | `bank_cd` | `String bankCd` | Withdrawal bank |
| `ACCT_NO` | `acct_no` | `String acctNo` | Withdrawal account |
| `CCY_CD` | `ccy_cd` | `String ccyCd` | Withdrawal currency |
| `TRNF_CCY_CD` | `trnf_ccy_cd` | `String trnfCcyCd` | Transfer currency |
| `REC[]` | `lines[]` | `List<TransferLineInput> lines` | |

**`REC` element → `TransferLineInput`:**

| Legacy | Wire | Java |
|---|---|---|
| `TRAN_AMT` | `tran_amt` | `BigDecimal tranAmt` |
| `ANT_DIV_CD` | `ant_div_cd` | `String antDivCd` |
| `RCPT_NM` | `rcpt_nm` | `String rcptNm` |
| `TAR_BANK_CD` | `tar_bank_cd` | `String tarBankCd` |
| `FINC_MDA_NO` | `finc_mda_no` | `String fincMdaNo` |
| `TRAN_USAG` | `tran_usag` | `String tranUsag` |
| `ERR_FLD` | `err_fld` | `String errFld` (echo for round-trip) |
| `ERR_FLD_CAUS` | `err_fld_caus` | `String errFldCaus` (echo) |
| `TRCO_KEY	` | `trco_key` | `String trcoKey` |
| `HAND_TRCO_KEY` | `hand_trco_key` | `String handTrcoKey` |
| `DOC_NO` | `doc_no` | `String docNo` |
| `NOTE` | `note` | `String note` |
| `CITAD_BANK_CD` | `citad_bank_cd` | `String citadBankCd` |
| `TRCO_CMENM` | `trco_cmenm` | `String trcoCmenm` |
| `TRCO_CMNM` | `trco_cmnm` | `String trcoCmnm` |
| `CHART_ACCT_SEQ_NO	` | `chart_acct_seq_no` | `String chartAcctSeqNo` |
| `CHART_ACCT_CD	` | `chart_acct_cd` | `String chartAcctCd` |
| `TRNF_CCY_CD` | `trnf_ccy_cd` | `String trnfCcyCd` |
| `DEPO_CCY_CD` | `depo_ccy_cd` | `String depoCcyCd` |

**Response:** `204 No Content` with no body — the frontend re-fetches the list. (Legacy returns empty `<out>`.)

### 2.3 `pymn_0010_002_r001` → `GET /api/v1/payment/internal-transfer/results/{id}`

WSVC source: [WSVC.pymn_0010_002_r001.xml:32-92](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0010_002_r001.xml#L32-L92).

**Path param:** `id` = `scdul_pymn_no`.

**Response (`TransferResultDetailResponse`):**

Master section: `scdul_pymn_no`, `out_bank_nm`, `out_bank_cd`, `wdrl_act_no`, `titl`, `total_tran_amt`, `tot_trns_cnt`, `file_url`, `draft_user_id`, `draft_user_nm`, `appr_seq_no`, `doc_no`, `org_file_name`, `file_size`, `tot_cnt`, `suc_cnt`, `err_cnt`, `prc_cnt`, `ccy_cd`, `wdrl_ccy_cd`.

`rows[]` element (`TransferResultLine`):

| Legacy | Wire | Java |
|---|---|---|
| `TRAN_AMT` | `tran_amt` | `BigDecimal tranAmt` |
| `SCDUL_PYMN_DETL_NO` | `scdul_pymn_detl_no` | `String scdulPymnDetlNo` |
| `ANT_DIV_CD` | `ant_div_cd` | `String antDivCd` |
| `RCPT_NM` | `rcpt_nm` | `String rcptNm` |
| `TAR_BANK_CD` | `tar_bank_cd` | `String tarBankCd` |
| `FINC_MDA_NO` | `finc_mda_no` | `String fincMdaNo` |
| `TAR_BANK_NM` | `tar_bank_nm` | `String tarBankNm` |
| `TRAN_USAG` | `tran_usag` | `String tranUsag` |
| `PROC_DTTM` | `proc_dttm` | `OffsetDateTime procDttm` |
| `PROC_STAT` | `proc_stat` | `String procStat` |
| `PROC_STAT` (i18n) | `proc_stat_key` | `String procStatKey` — new refactor sibling |
| `RERUN_DIV` | `rerun_div` | `String rerunDiv` |
| `RS_CD` | `rs_cd` | `String rsCd` |
| `RS_MSG` | `rs_msg` | `String rsMsg` |
| `CCY_CD` | `ccy_cd` | `String ccyCd` |
| `CHART_ACCT_SEQ_NO	` | `chart_acct_seq_no` | `String chartAcctSeqNo` |
| `CHART_ACCT_CD	` | `chart_acct_cd` | `String chartAcctCd` |
| `CHART_ACCT_NM_EN	` | `chart_acct_nm_en` | `String chartAcctNmEn` |
| `CHART_ACCT_NM` | `chart_acct_nm` | `String chartAcctNm` |
| `CHART_ACCT_LEVEL_NM` | `chart_acct_level_nm` | `String chartAcctLevelNm` |
| `TRNF_CCY_CD` | `trnf_ccy_cd` | `String trnfCcyCd` |
| `NOTE` | `note` | `String note` |
| `DOC_NO` | `doc_no` | `String docNo` |
| `DEPO_CCY_CD` | `depo_ccy_cd` | `String depoCcyCd` |
| `LINK_MATCH_YN` | `link_match_yn` | `String linkMatchYn` |
| `BANK_TRNS_SHORT_ID` | `bank_trns_short_id` | `String bankTrnsShortId` |
| `ACCT_SEQ_NO` | `acct_seq_no` | `String acctSeqNo` |
| `RSLT_DTTM` | `rslt_dttm` | `OffsetDateTime rsltDttm` |

### 2.4 `pymn_0009_002_u002` → `PATCH /api/v1/payment/internal-transfer/{id}/lines/{lineId}/reporting-account`

WSVC source: [WSVC.pymn_0009_002_u002.xml:32-43](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.pymn_0009_002_u002.xml#L32-L43).

**Request:** path `id` = `scdul_pymn_no`, path `lineId` = `scdul_pymn_detl_no`. Body `UpdateReportingAccountRequest`:

| Legacy | Wire | Java |
|---|---|---|
| `CHART_ACCT_SEQ_NO	` | `chart_acct_seq_no` | `String chartAcctSeqNo` (`@NotBlank`) |
| `CHART_ACCT_CD	` | `chart_acct_cd` | `String chartAcctCd` (`@NotBlank`) |

**Response:** `204 No Content`.

## 3. Behavior delta from JEX

Intentional differences worth recording:

- **Slug-keyed audit row.** Legacy `comm_0190_003_view.jsp` permissioning keys to `pymn_0009_001` etc. Refactor writes audit rows with `WSVC_ID = 'internal-transfer'` (workspace identifier rule, 2026-05-09 override). Legacy queries against the refactor DB will not find them — refactor is local-only R&D so there is no cross-system query path.
- **Excel-download legacy services unified.** `pymn_0009_003_r002` (Scheduled detail) and `pymn_0010_002_r002` (Result detail) — both legacy `viewUseYn=Y, actUseYn=N` Excel templates — replaced by the shared XLSX pipeline `POST …/export`. No JEX-template-style URL contract retained.
- **Permission matrix flag `T` (Transfer)** becomes a dedicated REST verb (`POST .../dispatch`) rather than reusing the JEX `_c001` convention. The refactor controllers do not have `_c001` / `_c002` suffixes — REST verbs carry the semantics.
- **`pageSize` query param.** Legacy `PAGE_PER_CNT` is renamed to `pageSize` to match the cross-feature [`<Pagination>`](../../../../wecms-web/components/shared/Pagination.tsx) contract (§ 4 of `wecms-web/CLAUDE.md`).
- **Sort wire fields.** Legacy `pymn_0009_003_r001` ([pymn_0009_003_r001_act.jsp:120](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_003_r001_act.jsp#L120)) uses `SortHelper.getSortDynamic("pymn_0009_003_r001")` which keys off Jex-Studio-registered sort metadata. Refactor uses the common-sort whitelist enum (`InternalTransferDetailSortField`) wired through the shared `useTableSort` hook per [§ 13a](../../../../wecms-web/CLAUDE.md).
- **`SCDUL_PYMN_NO` generation.** Legacy `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no', BIZ_KEY)` is **not** in the locked refactor branch set. Refactor implements a Java-side generator at [`InternalTransferKeyGenerator.nextScdulPymnNo`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferKeyGenerator.java#L77) emitting `YYYYMMDD<seq:6>` (14 chars) per (tenant, Vietnam-local day), backed by helper table `INTERNAL_TRANSFER_KEY_SEQ` (V131). **DEV column is `VARCHAR(10)`** but the refactor widens to `VARCHAR(20)` in [V131:70](../../../src/main/resources/db/migration/V131__internal_transfer_schema.sql#L70) to fit the generator — refactor-only deviation, OK because refactor is local-only R&D and the audit-key channel uses the slug.
- **`AntDivCd.INTER` coercion preserved.** Legacy auto-sets `ANT_DIV_CD = 'INTER'` when `OUT_BANK_CD == TAR_BANK_CD` ([PymnHelper.java:2525-2528](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/PymnHelper.java#L2525-L2528)). Refactor mirrors this in the dispatch service — no API change.
- **i18n keys ship on the wire** as `appr_stts_key` / `proc_stat_key` siblings alongside the legacy code value (per [api CLAUDE.md § Domain enum keys](../../../CLAUDE.md#13a-bis-domain-enum-keys--backend-ships-codes--i18n-keys-frontend-localises)). Legacy ships an English label inside `RS_MSG` only; refactor adds the key channel so frontend `t(key)` localises consistently across EN/KO/VI.
- **Excel export pipeline shares contract with Banking > Account Inquiry.** Legacy Internal Transfer has no Excel export from the JSP action layer beyond the `r002` template wrapper. Refactor exposes XLSX export at `POST …/export` per the cross-feature rule, with subheader + amount formatting handled by the shared `AmountFormatter` ([wecms-api/CLAUDE.md § Excel export](../../../CLAUDE.md#excel-export--commonexport-shared-pipeline--per-screen-exportrowprovider)).

## 4. Schema reconciliations vs. earlier drafts (verified against DEV `wecmsvndev` on 2026-05-13)

Direct `information_schema` queries via the `postgres` MCP revealed corrections that flow into the controller/service/repo/entity work. The earlier drafts of this mapping doc and the database schema doc assumed JEX WSVC lengths and PROD-active-list table names without checking the live column set; the reconciled findings are:

| Topic | Earlier draft assumed | Verified DEV reality |
|---|---|---|
| `SCDUL_PYMN_NO` length | `VARCHAR(20)` (matching WSVC `length="20"`) | `VARCHAR(10)` in DEV — live data is 1–3 chars. **BLOCKER-A resolved 2026-05-14:** refactor [V131__internal_transfer_schema.sql:70](../../../src/main/resources/db/migration/V131__internal_transfer_schema.sql#L70) declares `VARCHAR(20)` to fit the 14-char Java-side generator output (`YYYYMMDD<seq:6>` per (tenant, day)). Refactor-only widening. |
| Conn table name | `SCDUL_PYMN_PAY_TARGET_CONN` + `SCDUL_PYMN_PAY_FILE_MST_CONN` (two tables) | `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` (one table, note `SCDU_` not `SCDUL_`). PK is `(biz_key, scdul_pymn_no, scdul_pymn_detl_no)` only — at most one TB_PAY_TARGET link per detail line. |
| File-path conn table | Existed as a separate table | **Does not exist.** File-path linkage is encoded directly on `SCDUL_PYMN_SUMR_DETL.pay_file_seq_no + pay_seq` columns + `SCDUL_PYMN_SUMR.pay_file_seq_no` master pointer. Refactor Result-detail join must use these columns. |
| `SCDUL_PYMN_SUMR` file metadata | Single `FILE_INFO` blob column + `FULL_PATH` column | Three separate columns: `org_file_nm VARCHAR(500)`, `save_file_nm VARCHAR(500)`, `file_size VARCHAR(100)`. The legacy entity method `ScdulPymnSumr.getFullPath()` builds the path from `save_file_nm` + a server-side prefix at read time. Refactor follows ERP Transaction History precedent — store individual columns, build path in service. |
| `SCDUL_PYMN_SUMR` denormalised list columns | Included `APPR_STTS`, `TRNF_STTS`, `DRAFT_USER_NM`, `DRAFT_USER_ID` | None of these are stored on the master — the list IDO computes them via JOINs to `EXPT_RESOL_MSTR` and `USER_INFM`. Refactor list service must perform the join explicitly. |
| `total_tran_amt` precision | Generic decimal | `NUMERIC(20, 6)` on `SCDUL_PYMN_SUMR` (and on `SCDUL_PYMN_SUMR_DETL.tran_amt`). `NUMERIC(23, 2)` on dispatch tables. Dispatch service rounds 6 → 2. |
| `note` length on detail | `VARCHAR(255)` | `VARCHAR(200)`. |
| `chart_acct_seq_no` length | `VARCHAR(10)` (per WSVC) | `VARCHAR(20)` on both `chart_acct_infm` and `scdul_pymn_sumr_detl`. Refactor `@Size(max=20)`. |
| `proc_stat` column on dispatch tables | `VARCHAR(2)` (matching enum width) | `CHAR(1)` with default `'9'`. **Drives BLOCKER-B in the feature spec** — `ProcStat.TRANSFER_PENDING.getValue()` is `"50"` (two chars). Need to verify what is actually stored. |
| Dispatch-table PK column order | `(biz_key, …)` first | `(tran_dt, biz_key, …)` first. Affects index design and partition strategy when migrating. |
| `AUDIT_LOGS_INFM.WSVC_ID` | Width not checked | `VARCHAR(20)` NOT NULL. `internal-transfer` (17 chars) fits comfortably. |

Implementation impacts:

- Repository PK definitions must match the verified PK column order (e.g. `TbPayTargetId` has `tranDt, bizKey, tranSeq` in that order).
- The legacy `getFullPath()` behaviour must be reproduced — the `wecms.internal-transfer.storage-root` prefix is read from configuration and concatenated with `save_file_nm` at entity-mapper time.
- The result-detail query must use **two JOIN paths**, one for each routing:
  - API path: `SCDUL_PYMN_SUMR_DETL LEFT JOIN SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN USING (biz_key, scdul_pymn_no, scdul_pymn_detl_no) LEFT JOIN TB_PAY_TARGET USING (tran_dt, biz_key, tran_seq)`
  - File path: `SCDUL_PYMN_SUMR_DETL LEFT JOIN TB_PAY_FILE_DATA ON (SCDUL_PYMN_SUMR_DETL.biz_key, SCDUL_PYMN_SUMR_DETL.pay_file_seq_no, SCDUL_PYMN_SUMR_DETL.pay_seq) = (TB_PAY_FILE_DATA.biz_key, TB_PAY_FILE_DATA.file_seq_no, TB_PAY_FILE_DATA.seq)` (with `tran_dt` from `SCDUL_PYMN_SUMR.pay_tran_dt` carried into the join).

---

Last verified: 2026-05-21

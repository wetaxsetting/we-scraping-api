# Corporate Card — Interface Specification (Card Transactions)

Source of truth: [`CardTransactionsController.java`](../../../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/controller/CardTransactionsController.java) (~30 handlers). Base path `/api/v1/payment/card-transactions`. Wire format lowercase snake_case (global Jackson strategy); envelope `{ status, data, … }`.

**Class-level authorization (every endpoint):** `@PreAuthorize("@menuAuthz.canAccess('payment-corporate-card-transactions')")`. F-3 menu-authz layer — any tenant user whose product tree grants the menu. No role gate.

---

## 1 · Ledger + classify

### `GET /` — paged list + counts + totals
Query: `from_date`*, `to_date`* (`yyyyMMdd`), `srch_wd`, `bank_cd` (repeated), `card_no` (repeated), `link_yn` (`Y`/`N`/blank), `sort_field`, `sort_direction`, `page` (0), `size` (15, max 200).
Response: `CardTransactionListResponse { page, page_size, total_count, totals: CardTransactionTotals, rows: CardTransactionRow[] }`.

### `GET /cards` — All-Cards dropdown
Query: `bank_cd?` (`^[0-9A-Za-z]{0,20}$`). Response: `CardListResponse { cards: ActiveCard[] }`.

### `PATCH /{card_seq_no}` — discriminated single-field patch
Path: `card_seq_no` (`^[0-9]{1,20}$`). Body: `CardTransactionPatchRequest` = `{ field: "description"|"purpose", value }`.
Errors: `WBE_CARD_TRANSACTIONS_NOT_FOUND`, `WBE_VALIDATION`, `WBE_BAD_REQUEST` (unknown discriminator).

### `PUT /{card_seq_no}/reporting-account`
Body: `UpdateReportingAccountRequest { chart_acct_seq_no, chart_acct_cd }`.
Errors: `WBE_CARD_TRANSACTIONS_NOT_FOUND`, `WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID`, `WBE_VALIDATION`.

### `DELETE /{card_seq_no}/reporting-account` — clear (idempotent)
Error: `WBE_CARD_TRANSACTIONS_NOT_FOUND`.

### `POST /export` — XLSX
Body: shared `ExcelExportRequest` (`screenId="card-transactions"`). Streams `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`. Errors: `WBE2010/2011/2013`.

---

## 2 · Card Management

| Method | URI | Body | Errors |
|---|---|---|---|
| GET | `/cards/management` | query `srch_wd`, `sort_field`, `sort_direction`, `size` (max 100) | — |
| PATCH | `/cards/{bank_cd}/{card_no}/active` | `UpdateCardActiveRequest { actv_yn }` | `WBE_CARD_TRANSACTIONS_NOT_FOUND`, `WBE_VALIDATION` |
| PATCH | `/cards/{bank_cd}/{card_no}` | `UpdateCardInfoRequest { card_hldr_nm, card_nick_nm }` | `WBE_CARD_TRANSACTIONS_NOT_FOUND` |
| DELETE | `/cards/{bank_cd}/{card_no}` | — | `WBE1153` (in-use), `WBE_CARD_TRANSACTIONS_NOT_FOUND` |
| POST | `/cards` | `CreateCardsRequest` → `CreateCardsResponse { inserted_count, skipped_count }` | dedupe on PK |

Path validators: `bank_cd` `^[0-9A-Za-z]{1,20}$`, `card_no` `^[0-9A-Za-z*\-]{1,100}$`.

---

## 3 · Update Cards — Coocon scrape

| Method | URI | Body / Path | Notes |
|---|---|---|---|
| POST | `/sync` | `SyncCardTransactionsRequest` → `SyncCardTransactionsResponse` | **@Deprecated** headless; aborts QNA/OTP/CAPTCHA → `WBE_CARD_SYNC_NEEDS_RE_AUTH` |
| POST | `/sync-session` | `SyncCardTransactionsRequest` → `CardSyncSessionResponse` | session/challenge-aware start; returns `*_REQUIRED` + `session_id` |
| POST | `/sync-session/{sessionId}/qna` | `SubmitQnaRequest` | resume after QNA |
| POST | `/sync-session/{sessionId}/otp` | `SubmitChallengeRequest` | resume after OTP |
| POST | `/sync-session/{sessionId}/captcha` | `SubmitChallengeRequest` | resume after CAPTCHA |
| POST | `/period-limits` | `CardTranListPeriodLimitRequest` → `CardTranListPeriodLimitResponse` | per-bank window (`BANK_INFM_DETL` 26/27); default 90 days |

---

## 4 · Request Approval + management

| Method | URI | Body / Path | Notes |
|---|---|---|---|
| POST | `/request-approval` | **multipart** (`request`: `RequestApprovalRequest`, `attachments`: files) → `RequestApprovalResponse` | fan-out `EXPT_RESOL_*` + BLTB, `APPR_DV='33'`, allocate `APPR_SEQ_NO` via funnel |
| GET | `/next-doc-no` | — → `NextDocNoResponse` | preview `VO_YYMMDD_NNN` |
| GET | `/by-approval/{apprSeqNo}` | path `^[0-9]{1,15}$` → `CardTransactionRow[]` | Approval Details grid |
| DELETE | `/by-approval/{apprSeqNo}` | path → `Void` | withdraw pending (delete master + approvers, clear stamps) |
| PUT | `/by-approval/{apprSeqNo}/approval-line` | `ChangeApprovalLineRequest { appr_line_no }` | change line on pending |

---

## 5 · Link + receipts + memos

| Method | URI | Body / Query | Notes |
|---|---|---|---|
| GET | `/links/e-invoices` | `from_date`*, `to_date`*, `search`, `page`, `size` (50) | `DOC_TYPE='B'`, `HAND_REG_YN='N'`, `PROV_STTS NOT IN ('EE','VD')`, not linked |
| GET | `/links/receipts` | same query | `DOC_TYPE='E'`, `HAND_REG_YN='Y'`, by `USE_DT` |
| POST | `/links` | `LinkExpensesRequest` → `LinkExpensesResponse` | `SUM(dedu_amt)==TRSC_AMT` → else `WBE_LINK_AMOUNT_MISMATCH` |
| DELETE | `/links/{card_seq_no}` | path | unlink all (idempotent) |
| POST | `/receipts` | `CreateReceiptRequest` → `CreateReceiptResponse { doc_id }` | `BUSA_DOC_MSTR`, `DOC_TYPE='E'` |
| GET | `/receipts/{doc_id}` | path (max 50) → `ReceiptDetailResponse` | `WBE_NOT_FOUND` on miss/wrong tenant |
| GET | `/purpose-options` | `grp_cd?` (default `6`) → `PurposeOption[]` | `USAG_INFM`, localized `usag_nm` |
| GET | `/memos` | `card_seq_no`* → `MemoListResponse` | newest-first |
| POST | `/memos` | `CreateMemoRequest` → `MemoRow` | `MEMO_INFM` Java MAX+1 key-gen |
| DELETE | `/memos/{seq_no}` | path `BigDecimal` | author-only → `WBE_MEMO_NOT_AUTHOR` |

---

## 6 · Ledger row shape (`CardTransactionRow`)

Record component order (wire = snake_case):

`card_seq_no` · `bank_cd` · `bank_nm` (server-resolved via `BankCdResolver`) · `card_no` · `card_nm` · `card_nick_nm` · `card_hldr_nm` · `merch_nm` · `ccy_cd` · `trsc_amt` (decimal string) · `trsc_dt` · `trsc_tm` · `card_trns_usage_code` (raw code) · `chart_acct_seq_no` · `chart_acct_cd` (raw code) · `cont` (description) · `appr_seq_no` · `appr_doc_no` (`EXPT_RESOL_MSTR.DOC_NO`, null when unrequested) · `stts` · `stts_key` · `linked_docs[]` · `approvers[]` · `reg_id`/`reg_dtm`/`mod_id`/`mod_dtm` · `using_type` · `auth_code` · `city` · `note`.

`linked_docs[]` (`LinkedDocRef`): `doc_id`, `disp_doc_id`, `hand_doc_id` (null), `doc_type`, `einv_yn` (null), `totl_amt` (per-doc `dedu_amt`).

**Do NOT document invented fields.** `card_trns_usage_nm`, `chart_acct_nm`/`_en`/`_level_nm`, `appr_dv`, `post_seq_no`, `post_reg_id` are **NOT** on the wire. **`approvers[]` is ALWAYS empty** on ledger rows (`CardTransactionsService.toRow` ships `List.of()`); the approver chain surfaces only in the Approval Details popup via `GET /by-approval/{apprSeqNo}`. Real extra fields: `using_type`, `auth_code`, `city`, `note` (GAP-CARD-FIELDS). Display names resolved client-side from the chart-of-accounts tree + purpose-options keyed by raw codes (APPROVED DEVIATION; `CardTransactionRow.java:61-67`).

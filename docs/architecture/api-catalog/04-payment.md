# 04 — PAYMENT (`domain/payment`)

**Bảng dùng chung:** `SCDUL_PYMN_SUMR`/`_DETL` (Internal=03, Vendor=01, Expense=02, Payroll=00), `OVRS_PYMN_SUMR`/`_DTLS`/`_MSTR` (Overseas), `EXPT_RESOL_MSTR`/`_APPRVR_INFM`/`_FILE` (workflow phê duyệt), `TB_PAY_TARGET` (dispatch VND/API), `TB_PAY_FILE_MST`/`_DATA` (dispatch non-VND/File), `BUSI_MATCH_LOG` (banking match), `AUDIT_LOGS_INFM`. Phê duyệt routing theo `APPR_DV`: `'34'`→OVRS, `'33'`→card, còn lại→SCDUL.

**Phân quyền (đã BẬT):** mọi controller mang `@PreAuthorize("@menuAuthz.canAccess('<slug>')")` (bean `MenuAuthorizationService`, kiểm `USER_MENU_AUTH_INFM`). Slug từng controller: approval-box→`payment-approval-box`; card-transactions→`payment-corporate-card-transactions`; overseas-payment→`payment-overseas-request` (nhánh result/match→`payment-overseas-result`); internal-transfer→`payment-internal-transfer-scheduled` (result→`payment-internal-transfer-result`); vendor→`payment-vendor-scheduled`/`payment-vendor-result`; expense→`payment-expense-scheduled`/`payment-expense-result`; payroll→`payment-payroll-scheduled`/`payment-payroll-result`. blocked-chars KHÔNG gắn `@PreAuthorize` (chỉ `authenticated()`).

**Dispatch (đường chi tiền) — handoff cho JEX daemon ngoài:** `TransferDispatchExecutor` (Vendor/Expense/Payroll), `InternalTransferDispatchExecutor`, `OverseasDispatchExecutor` GHI hàng dispatch với `PROC_STAT='9'` (pending) rồi DỪNG — VND→1 dòng `TB_PAY_TARGET`/dòng; non-VND→1 `TB_PAY_FILE_MST` header + N `TB_PAY_FILE_DATA` (`PROC_RSLT_DIV='N'`). KHÔNG có HTTP client gửi ngân hàng, KHÔNG có EFS engine: việc gửi NAPAS/CITAD/SWIFT và lật `PROC_STAT`→`'0'`/`'3'` do JEX daemon ngoài làm; tới lúc đó Result list đọc batch là "In Progress". Trước executor có `CutoffValidator` chặn ngoài giờ + stamp `TRNF_YN='Y'`/`TRNF_DT` trên master.

---

## 1. Approval Box `/api/v1/payment/approval-box`
Service `ApprovalBoxService` + `ApprovalBoxFanoutService`. Audit `WSVC_ID="approval-box"`.

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List chờ/đã duyệt + 4 tab count + chuỗi approver (paged) | EXPT_RESOL_MSTR, EXPT_RESOL_APPRVR_INFM, OVRS_PYMN_SUMR, SCDUL_PYMN_SUMR | — |
| GET `/{apprSeqNo}/approvers` | Chuỗi approver thực 1 tài liệu (ordered, gate Approve/Reject theo approver pending) | EXPT_RESOL_MSTR, EXPT_RESOL_APPRVR_INFM | — |
| POST `/{apprSeqNo}/approve` | Duyệt 1 tài liệu | EXPT_RESOL_MSTR, EXPT_RESOL_APPRVR_INFM | EXPT_RESOL_APPRVR_INFM (P→A, promote next W→P), EXPT_RESOL_MSTR (APPR_TYPE_CD 03/01), write-back OVRS/SCDUL.APPR_STTS, AUDIT |
| POST `/{apprSeqNo}/reject` | Từ chối (hủy workflow) | như approve | EXPT_RESOL_APPRVR_INFM (P→R, peer W→S), MSTR (02), write-back APPR_STTS='X', AUDIT |
| DELETE `/{apprSeqNo}/withdraw` | Thu hồi yêu cầu (về Draft) | EXPT_RESOL_MSTR | xóa MSTR+APPRVR_INFM, clear APPR_SEQ_NO trên OVRS/SCDUL, AUDIT |
| PUT `/{apprSeqNo}/approval-line` | Thay chuỗi approver (reset Pending) | EXPT_RESOL_MSTR, APPR_LINE_INFM_DETL | rebuild APPRVR_INFM, AUDIT |
| POST `/bulk-approve` | Approve hàng loạt (max 100, partial) | như approve | như approve + 1 audit bulk |
| POST `/export` | XLSX list (cap 50k) | + View Setting | — |

Lỗi: `WBE2050` (master missing), `WBE2053` (đã final), `WBE2051` (caller không là approver), `WBE2052` (không pending), `WBE2054` (bulk quá lớn).

---

## 2. Card Transactions `/api/v1/payment/card-transactions` — **28 endpoint**
Services: `CardTransactionsService`, `CardTransactionSyncService`, `CardTranListR001Service`. Audit `WSVC_ID="card-transactions"`.

### Đọc — list & lookup
| Endpoint | Chức năng | Đọc |
|---|---|---|
| GET `/` | Sổ giao dịch thẻ (paged 15/max200, lọc bank_cd[]/card_no[]/link_yn/srch_wd, sort `CardTrnsHstrSortField`) + totals | CARD_TRNS_HSTR, CARD_MSTR, BANK_INFM |
| GET `/by-approval/{apprSeqNo}` | Giao dịch gắn 1 APPR_SEQ_NO (popup Approval Details kind=card) | CARD_TRNS_HSTR, CARD_MSTR |
| GET `/cards` | Dropdown "All Cards" (lọc bank_cd) | CARD_MSTR, BANK_INFM |
| GET `/cards/management` | List thẻ cho modal Card Management (search, sort `CardMstrSortField`) | CARD_MSTR |
| GET `/next-doc-no` | Preview số chứng từ VO_YYMMDD_NNN kế tiếp | EXPT_RESOL_MSTR (MAX) |
| GET `/links/e-invoices` | e-Invoice (Bill GDT) đủ điều kiện link (DOC_TYPE='B', HAND_REG_YN='N', PROV_STTS∉('EE','VD'), NOT EXISTS link) | BUSA_DOC_MSTR, BUSA_DOC_RCV_PAY_HIS |
| GET `/links/receipts` | Receipt thủ công đủ điều kiện link (DOC_TYPE='E', HAND_REG_YN='Y', USE_DT) | BUSA_DOC_MSTR, BUSA_DOC_RCV_PAY_HIS |
| GET `/receipts/{doc_id}` | Chi tiết 1 receipt/bill (`WBE_NOT_FOUND`) | BUSA_DOC_MSTR |
| GET `/purpose-options` | Options Purpose (default USAG_GRP_CD='6'), đã i18n | USAG_INFM |
| GET `/memos` | List memo 1 giao dịch (newest-first, kèm tên tác giả) | MEMO_INFM (+user) |

### Ghi — patch & classification
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| PATCH `/{card_seq_no}` | Sửa 1 field (description/purpose; discriminated body) | CARD_TRNS_HSTR | CARD_TRNS_HSTR, AUDIT. Lỗi `WBE_CARD_TRANSACTIONS_NOT_FOUND`/`WBE_BAD_REQUEST` |
| PUT `/{card_seq_no}/reporting-account` | Gán CHART_ACCT_SEQ_NO+CD | CARD_TRNS_HSTR, CHART_ACCT_INFM (validate) | CARD_TRNS_HSTR, AUDIT. Lỗi `..._CHART_ACCT_INVALID` |
| DELETE `/{card_seq_no}/reporting-account` | Xóa (null) chart-acct (idempotent) | CARD_TRNS_HSTR | CARD_TRNS_HSTR |

### Ghi — Card Management (legacy card_0001_003)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| PATCH `/cards/{bank_cd}/{card_no}/active` | Toggle ACTV_YN | CARD_MSTR | CARD_MSTR, AUDIT |
| PATCH `/cards/{bank_cd}/{card_no}` | Inline-rename CARD_HLDR_NM+CARD_NICK_NM | CARD_MSTR | CARD_MSTR, AUDIT |
| DELETE `/cards/{bank_cd}/{card_no}` | Hard-delete thẻ (in-use guard `WBE1153`) | CARD_TRNS_HSTR, CARD_MSTR | CARD_MSTR, AUDIT |
| POST `/cards` | Bulk-create sau scrape (dedupe PK, trả inserted/skipped) — `comm_0218_007_c001` | CARD_MSTR | CARD_MSTR, AUDIT |

### Ghi — sync, approval, link, receipt, memo
| Endpoint | Chức năng | Đọc | Ghi | API ngoài |
|---|---|---|---|---|
| POST `/sync` | **Scrape Coocon real-time** mọi thẻ active có credential | CARD_MSTR, BANK_CERT_INFO | CARD_TRNS_HSTR | **Coocon/NXISAS** |
| POST `/period-limits` | Tra giới hạn kỳ per-bank (popup Update Cards; fallback 90 days) | BANK_INFM_DETL (CD 26/27) ⋈ BANK_CERT_INFO | — | — |
| POST `/request-approval` | Request Approval hàng loạt (APPR_DV='33') | — | CARD_TRNS_HSTR.APPR_SEQ_NO (+EXPT_RESOL_MSTR follow-up), AUDIT | — |
| POST `/export` | XLSX list (`CardTransactionsExportProvider`) | + View Setting | — | — |
| POST `/links` | Link expense/receipt vào giao dịch (Σdedu_amt=TRSC_AMT, else `WBE_LINK_AMOUNT_MISMATCH`) | CARD_TRNS_HSTR, BUSA_DOC_MSTR | CARD_TRNS_RCV_PAY_INFM + N BUSA_DOC_RCV_PAY_HIS | — |
| DELETE `/links/{card_seq_no}` | Unlink mọi doc (idempotent) | — | xóa CARD_TRNS_RCV_PAY_INFM + BUSA_DOC_RCV_PAY_HIS | — |
| POST `/receipts` | Tạo receipt thủ công (trả doc_id) — `prch_0011_001` | — | BUSA_DOC_MSTR (DOC_TYPE='E') | — |
| POST `/memos` | Thêm memo | — | MEMO_INFM | — |
| DELETE `/memos/{seq_no}` | Hard-delete memo (chỉ tác giả, `WBE_MEMO_NOT_AUTHOR`) | MEMO_INFM | xóa MEMO_INFM | — |
| DELETE `/by-approval/{apprSeqNo}` | Withdraw Card Approval chờ | EXPT_RESOL_MSTR | xóa MSTR+APPRVR_INFM, clear CARD_TRNS_HSTR.APPR_SEQ_NO, AUDIT | — |
| PUT `/by-approval/{apprSeqNo}/approval-line` | Đổi approval line (reset Pending) | EXPT_RESOL_MSTR, APPR_LINE_INFM_DETL | rebuild APPRVR_INFM | — |

---

## 3. Overseas Payment `/api/v1/payment/overseas-payment`
Audit `WSVC_ID="overseas-payment"`. Dùng chung `InternalTransferService.listWithdrawalAccounts`.

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/scheduled` | List chờ duyệt (UNION OVRS_PYMN_SUMR E + OVRS_PYMN_MSTR M) + approver + totals | OVRS_PYMN_SUMR/MSTR, EXPT_RESOL_* | — |
| GET `/scheduled/{id}` | 1 dòng (OVRS_PYMN_NO/DOC_NO) | OVRS_PYMN_SUMR/MSTR, EXPT_RESOL_* | — |
| GET `/scheduled/{id}/details` | Summary + beneficiary + dispatch result | OVRS_PYMN_SUMR, OVRS_PYMN_DTLS, TB_PAY_FILE_* | — |
| GET `/scheduled/{id}/file` | Tải file Excel gốc (`WBE2069`) | OVRS_PYMN_SUMR + đĩa | — |
| POST `/{id}/request-approval` | Submit duyệt (allocate apprSeqNo MAX+1) | OVRS_PYMN_SUMR, EXPT_RESOL_*, BUSI_INFM | OVRS_PYMN_SUMR (APPR_SEQ_NO/STTS='P'), EXPT_RESOL_MSTR(apprDv='34')+APPRVR_INFM, AUDIT |
| PATCH `/{id}/approval-line` | Đổi approver (chỉ STTS='P', `WBE2097`) | — | APPRVR_INFM, MSTR, OVRS_PYMN_SUMR |
| DELETE `/{id}/approval-line` | Rút yêu cầu (`WBE2098`) | — | xóa APPRVR_INFM+MSTR, clear OVRS, AUDIT |
| DELETE `/{id}` | Xóa draft (`WBE2099`/`WBE2095`) | — | xóa OVRS_PYMN_DTLS+SUMR |
| POST `/{id}/approval-action` | Approve/Reject | OVRS_PYMN_SUMR, APPRVR_INFM | APPRVR_INFM, OVRS_PYMN_SUMR.APPR_STTS, MSTR, AUDIT |
| POST `/{id}/dispatch` | Chuyển tiền (CutoffValidator SWIFT; no body). GHI dispatch File-path rồi handoff JEX daemon ngoài | OVRS_PYMN_SUMR, OVRS_PYMN_DTLS | TB_PAY_FILE_MST+DATA (PROC_STAT='9'), stamp PAY_*, TRNF_YN='Y', AUDIT |
| GET `/result` · `/result/{id}` | List/detail kết quả + totals (perm `payment-overseas-result`) | OVRS_PYMN_SUMR | — |
| GET `/result/export` · POST `/result/{id}/export` | XLSX (writer riêng / provider; perm `payment-overseas-result`) | OVRS_PYMN_SUMR, OVRS_PYMN_DTLS | — |
| PATCH `/{id}/details/{trnSeq}/reporting-account` | Gán/clear chart-acct 1 dòng (perm `payment-overseas-result`) | OVRS_PYMN_DTLS | OVRS_PYMN_DTLS |
| GET `/withdrawal-accounts` | TK rút firm-banking | ACCT_MSTR ⋈ TB_FIRM_CONF | — |
| POST `/excel-preview` · `/excel-upload` (multipart) | Parse/validate / lưu batch + file | BLOCKED_CHARS_RULE | OVRS_PYMN_SUMR(+DTLS) + file (key `OVERSEAS_PAYMENT_KEY_SEQ`) |

**Banking match — đối soát sau dispatch (OVR-G1, perm `payment-overseas-result`, legacy `comm_0197_*` nhánh `isOversea`):** `OverseasBankingMatchService`, theo từng dòng `OVRS_PYMN_DETL_NO`.

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/{ovrsPymnNo}/lines/{ovrsPymnDetlNo}/match/candidates` | List giao dịch ngân hàng ứng viên (lọc `match_rmrk_yn`/`srch_wd`) | ACCT_TRNS_HSTR, OVRS_PYMN_DTLS | — |
| POST `/{ovrsPymnNo}/lines/{ovrsPymnDetlNo}/match` | Khớp 1 dòng với 1 giao dịch | — | BUSI_MATCH_LOG, OVRS_PYMN_DTLS |
| DELETE `/{ovrsPymnNo}/lines/{ovrsPymnDetlNo}/match` | Bỏ khớp 1 dòng (unlock log) | — | BUSI_MATCH_LOG, OVRS_PYMN_DTLS |
| DELETE `/{ovrsPymnNo}/matches` | Bỏ khớp mọi dòng trong batch | — | BUSI_MATCH_LOG, OVRS_PYMN_DTLS |

---

## 4. Internal Transfer `/api/v1/payment/internal-transfer` (SCDUL_PYMN_TYPE_CD='03')
Services `InternalTransferService`, `InternalTransferDispatchService`. Audit `WSVC_ID="internal-transfer"`.

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List Scheduled (paged, lọc, sort) + totals | SCDUL_PYMN_SUMR | — |
| GET `/withdrawal-accounts` | TK rút firm-banking | ACCT_MSTR ⋈ TB_FIRM_CONF | — |
| GET `/{id}` | Detail (master+dòng) + attachment + breadcrumb | SCDUL_PYMN_SUMR(_DETL), EXPT_RESOL_MSTR/FILE, CHART_ACCT_INFM, BANK_INFM | — |
| GET `/{id}/file` | Tải file Excel (`WBE2015`/`WBE2060`) | SCDUL_PYMN_SUMR + đĩa | — |
| GET `/{id}/approval/{apprSeqNo}/attachments/{fileId}` | Stream attachment | EXPT_RESOL_FILE + đĩa | — |
| GET `/next-doc-no` | Preview VO_YYMMDD_NNN | EXPT_RESOL_MSTR (MAX) | — |
| POST `/uploads/parse` (multipart) | Parse/validate Excel | BLOCKED_CHARS_RULE | — |
| POST `/` (multipart) | Create draft + file + ERP history | — | SCDUL_PYMN_SUMR(_DETL), file, ERP_TRNS_HSTR (TRNS_TYPE_CD='16'), AUDIT |
| PATCH `/{id}/lines/{lineId}/reporting-account` | Cập nhật chart-acct dòng (chặn đã transfer `WBE2064`) | SCDUL_PYMN_SUMR(_DETL) | SCDUL_PYMN_SUMR_DETL, AUDIT |
| POST `/{id}/lines/{lineId}/rerun` | Re-fire dòng lỗi tạm (RS_CD 0001-0005, hôm nay) | SCDUL_PYMN_*, TB_PAY_FILE_* | clear TB_PAY_FILE_DATA.RS_CD, AUDIT. Lỗi `WBE2122/2123/2124` |
| POST `/{id}/dispatch` | Chuyển tiền (CutoffValidator). GHI dispatch rồi handoff JEX daemon ngoài | SCDUL_PYMN_SUMR(_DETL) | VND→TB_PAY_TARGET / non-VND→TB_PAY_FILE_MST+DATA (PROC_STAT='9'), stamp PAY_*, TRNF_YN='Y', AUDIT |
| DELETE `/{id}` | Xóa draft (chặn có APPR_SEQ_NO `WBE2062`) | — | xóa SCDUL_PYMN_SUMR(_DETL), ERP_TRNS_HSTR (ERP_TYPE='D'), AUDIT |
| POST `/{id}/request-approval` (multipart) | Submit duyệt + attachment | SCDUL_PYMN_SUMR, EXPT_RESOL_*, BUSI_INFM | stamp SCDUL_PYMN_SUMR, EXPT_RESOL_MSTR+APPRVR_INFM, file+EXPT_RESOL_FILE, AUDIT |
| DELETE `/{id}/approval` | Rút yêu cầu duyệt | — | xóa MSTR+APPRVR_INFM, clear SCDUL, AUDIT |
| PUT `/{id}/approval-line` | Đổi approver | EXPT_RESOL_MSTR, APPR_LINE_INFM_DETL | rebuild APPRVR_INFM, AUDIT |
| POST `/{id}/approval-action` | Approve/Reject (delegate ApprovalBoxService) | SCDUL_PYMN_SUMR, EXPT_RESOL_* | APPRVR_INFM/MSTR + mirror SCDUL.APPR_STTS, AUDIT. Lỗi `WBE2094/2095` |

**Banking match — per-line (G21b, legacy `comm_0197_*` nhánh domestic, `SCDUL_PYMN_TYPE_CD='03'`):** `DomesticPaymentLineMatchService`.

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/{id}/lines/{lineId}/match/candidates` | List giao dịch ứng viên (lọc `match_rmrk_yn`/`srch_wd`) | ACCT_TRNS_HSTR, SCDUL_PYMN_SUMR_DETL | — |
| POST `/{id}/lines/{lineId}/match` | Khớp 1 dòng với 1 giao dịch | — | BUSI_MATCH_LOG, SCDUL_PYMN_SUMR_DETL |
| DELETE `/{id}/lines/{lineId}/match` | Bỏ khớp 1 dòng | — | BUSI_MATCH_LOG, SCDUL_PYMN_SUMR_DETL |
| DELETE `/{id}/matches` | Bỏ khớp mọi dòng trong batch | — | BUSI_MATCH_LOG, SCDUL_PYMN_SUMR_DETL |

## 5. Internal Transfer Result `/api/v1/payment/internal-transfer/results` (read-only, perm `payment-internal-transfer-result`)
- GET `/` (list + totals), GET `/{id}` (detail per-line PROC_STAT/RS_CD qua `DispatchResultResolver`), POST `/export` (`screenId=payment-internal-transfer-result`). **Đọc**: SCDUL_PYMN_SUMR(_DETL), TB_PAY_FILE_*/TB_PAY_TARGET.

## 6. Vendor (01) / Expense (02) Payment
`/payment/{vendor|expense}-payment` — **bản sao 1:1 Internal Transfer**, chỉ khác `SCDUL_PYMN_TYPE_CD` + audit/permission slug (Vendor `payment-vendor-scheduled`/`-result`; Expense `payment-expense-scheduled`/`-result`). Tập endpoint: GET `/`, `/{id}`; GET `/results`, `/results/{id}`, POST `/results/export`; DELETE `/{id}`, POST `/{id}/request-approval` (JSON, **không** multipart), `/{id}/approval-action`, DELETE `/{id}/approval`, PUT `/{id}/approval-line`, PATCH `/{id}/lines/{lineId}/reporting-account`, POST `/{id}/lines/{lineId}/rerun`, `/{id}/dispatch`; GET `/withdrawal-accounts`, POST `/uploads/parse` (multipart), POST `/` (multipart), GET `/{id}/file`. **Banking match per-line** giống Internal Transfer: GET `/{id}/lines/{lineId}/match/candidates`, POST/DELETE `/{id}/lines/{lineId}/match`, DELETE `/{id}/matches` (`DomesticPaymentLineMatchService`, type-cd Vendor=01/Expense=02). Bảng: SCDUL_PYMN_*, EXPT_RESOL_*, TB_PAY_* (dispatch PROC_STAT='9' → JEX daemon), ERP_TRNS_HSTR, BLOCKED_CHARS_RULE, BUSI_MATCH_LOG, ACCT_MSTR⋈TB_FIRM_CONF, AUDIT_LOGS_INFM.

## 6b. Payroll (00) Payment `/api/v1/payment/payroll-payment` (perm `payment-payroll-scheduled`/`-result`)
Như Vendor/Expense về list/detail/results/export/delete/request-approval/approval-action/approval/approval-line/reporting-account/rerun/dispatch/withdrawal-accounts/uploads-parse/create/file. **Khác biệt: banking match ở mức batch (1:1), KHÔNG per-line, KHÔNG unmatch-all** (`PayrollBankingMatchService`): GET `/{id}/match/candidates` (ứng viên ghim theo ngày trả + tổng tiền batch), POST `/{id}/match`, DELETE `/{id}/match`. Bảng: SCDUL_PYMN_* (type-cd '00'), EXPT_RESOL_*, TB_PAY_*, ERP_TRNS_HSTR, BLOCKED_CHARS_RULE, BUSI_MATCH_LOG, ACCT_MSTR⋈TB_FIRM_CONF, AUDIT_LOGS_INFM.

## 7. Blocked Chars Rule `/api/v1/payment/blocked-chars`
- GET — rule ký tự cấm còn hiệu lực cho `(bank_cd, pymn_type)` (drive tooltip + validation upload, module-neutral). **Đọc**: `BLOCKED_CHARS_RULE` (`findActiveByBankAndPymnType`, STATUS='Y' + EFF_FR_DT/EFF_TO_DT vs hôm nay TZ Asia/Ho_Chi_Minh). Read-only. Controller này KHÔNG gắn `@PreAuthorize` — chỉ `authenticated()` (không gate theo menu).

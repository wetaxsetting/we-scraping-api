# 02 — BANKING CORE (`domain/banking`)

Tenant luôn từ `TenantContext.currentBizKey()`. Tất cả JWT-protected.

> **Phân quyền đã BẬT** (`@EnableMethodSecurity` ON tại `SecurityConfig`). Mọi endpoint banking-core đều gắn `@PreAuthorize("@menuAuthz...")` (`MenuAuthorizationGuard`) — admin qua được product-tree gate nhưng bỏ qua per-user grant; user thường cần grant `USER_MENU_AUTH_INFM` cho permission_cd tương ứng. Đây là gate **màn hình**.
> Ngoài ra `AccountAccessGuard.assertCanView(bizKey, bankCd, acctNo)` là gate **dữ liệu per-account** cho họ Transaction History (`TransactionService` / `MatchService`): non-admin chỉ đọc/tác động TK có grant View (`ACCT_AUTH_INFO.AUTH_TYPE='V'`, `USE_YN='Y'`); admin bypass; thiếu quyền → `AccessDeniedException` → HTTP 403 `WBE1003`. Gate dữ liệu nằm ở service, không phải controller.

## AccountController `/api/v1/banking`

### GET `/accounts`
- **Phân quyền**: `@menuAuthz.canAccessAccountInquiry(#currency)` (gate theo currency VND/FCY).
- **Chức năng**: Account Inquiry (VND/FCY chung 1 endpoint qua `currency`). List TK + tổng theo currency + danh sách bank dropdown. Phân trang + lọc.
- **Table đọc**: `ACCT_MSTR`, `BANK_CERT_INFO` (bank "connected"), `BANK_INFM` (qua `BankCdResolver`)
- **Ghi**: —
- **Xử lý**: tham số `currency`, `bankCd` (exact-match BANK_CD), `keyword` (LIKE TK/nickname/tên), `sortField`/`sortDirection`, `page`/`size` (default size từ `AccountService.DEFAULT_PAGE_SIZE`, clamp `MAX_PAGE_SIZE`); gom vào `AccountFilter`. Resolve currency → điều kiện CCY_CD + whitelist ACCT_TYPE_CD; sort whitelist `AccountSortField` fallback `PRN_SEQ,BANK_CD,ACCT_NO ASC`; `findFiltered` (paged); tổng per-currency + scalar tính trên toàn filtered set (ổn định qua phân trang); nickname qua `@Formula wb_format_acct_nick_nm`.
- **Gọi**: `AccountService.list(filter, sortField, sortDirection, page, size)`

### POST `/accounts/export`
- **Phân quyền**: `@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')`.
- **Chức năng**: Export XLSX Account Inquiry. **Đọc**: ACCT_MSTR + View Setting. **Xử lý**: `ExcelExportService` → `AccountInquiryExportProvider` chạy lại `AccountService.list`.

### POST `/accounts/register`
- **Phân quyền**: `@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')`.
- **Chức năng**: Đăng ký TK user chọn sau scrape NXISAS (upsert) — `fnsh_0101_001_c001`.
- **Đọc**: `ACCT_MSTR`, `BANK_CERT_INFO`, `USER_BUSI_CONN_INFM` — **Ghi**: `ACCT_MSTR` (INSERT/UPDATE), `ACCT_AUTH_INFO` (fan-out VIEW+WITHDRAWAL mọi user, chỉ khi insert mới)
- **Xử lý**: payload rỗng → `WBE_BANK_ACCOUNT_REGISTER_EMPTY`; resolve bankCd server-side từ credential; loop: tồn tại→UPDATE / mới→INSERT (HAND_REG_YN='N') + `fanOutAccountAuth`.
- **Gọi**: `AccountRegistrationService` → `BankCredentialService`

### POST `/accounts/transactions-sync` (+ `/{sessionId}/qna|otp|captcha`)
- **Phân quyền**: cả 4 endpoint `@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')`.
- **Chức năng**: Bank Transaction Sync — **scrape NXISAS Coocon** (LOGIN→challenge?→TRANSACTION→BALANCE).
- **Đọc**: `ACCT_MSTR`, `BANK_CERT_INFO`, `ACCT_TRNS_HSTR` (dedup BANK_TRNS_FULL_ID) — **Ghi**: `ACCT_TRNS_HSTR` (INSERT mới), `ACCT_MSTR` (BAL/REAL_AMT + dtm), `EXT_TRAN_CALL_HIS` (audit mỗi attempt)
- **Xử lý**: range default 30 ngày, cap 92; loại HAND_REG_YN='Y' (rỗng→`WBE2096`); `NxisasScrapeSession` mode SYNC; challenge → lưu session trả `*_REQUIRED`; sạch → persist; `acct_seq_no` qua `wb_get_next_val`. Resume: session từ registry (`WBE2060`), assert đúng loại challenge (`WBE9001`).
- **Gọi**: `BankTransactionSyncService` → **Coocon NXISAS gateway**, `LegacyFunctionRepository`, `SyncHistoryWriter`

### GET `/accounts/by-bank`
- **Phân quyền**: `@menuAuthz.canAccessAny('banking-vnd-account-inquiry','banking-fcy-update-inquire')`.
- **Chức năng**: Autocomplete TK rút cho firm-banking-settings — `comm_1004_001`. **Đọc**: `NAPAS_BANK_INFO`→bankCd, `ACCT_MSTR`. **Xử lý**: `out_bank_cd` (1-15 digits); không resolve được → list rỗng; mask account qua `LogMask.acct`.

### GET `/accounts/{acctSeqNo}/transactions` · `/summary` · `/{trnsSeqNo}`
- **Phân quyền**: `@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')` (gate màn hình) + `AccountAccessGuard.assertCanView` trong service (gate dữ liệu per-account).
- **Chức năng**: Lịch sử / tổng hợp / chi tiết giao dịch — `fnsh_0002_001_r001`.
- **Đọc**: `ACCT_TRNS_HSTR`, `ACCT_TRNS_RCV_PAY_INFM` — **Ghi**: —
- **Xử lý**: parse acctSeqNo=`BANK_CD_ACCT_NO`; fetch date-range/full; lọc in-memory type/keyword/cnfm_yn; tính tổng deposit/withdraw; purpose/vas/trco từ RCV_PAY mới nhất.

### PUT/DELETE `.../{trnsSeqNo}/confirm`
- **Phân quyền**: `@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')` + `AccountAccessGuard` (service).
- **Chức năng**: Xác nhận / hủy xác nhận giao dịch — `trns_0001_001_u001`.
- **Đọc**: `ACCT_TRNS_HSTR`, `ACCT_TRNS_RCV_PAY_INFM` — **Ghi**: `ACCT_TRNS_RCV_PAY_INFM` (CNFM_YN='Y'/'N' + classification + CNFM_BY/DTM)
- **Xử lý**: đã confirmed→`WBE_TRANSACTION_ALREADY_CONFIRMED`; chưa→`WBE_TRANSACTION_NOT_CONFIRMED`; RCV_PAY_SEQ_NO=MAX+1 Java.

> **Phân quyền (chung cho mọi endpoint `.../{trnsSeqNo}/...` còn lại bên dưới — reporting-account, purpose, vas-account, contact, match-candidates/match)**: `@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')` + `AccountAccessGuard.assertCanView` trong service.

### PATCH/DELETE `.../{trnsSeqNo}/reporting-account`
- **Chức năng**: Gán/xóa chart-of-accounts lên giao dịch — `fnsh_0200_001`.
- **Đọc**: `ACCT_TRNS_HSTR`, `CHART_ACCT_INFM` (validate seq+code, STTS='A', DEL_YN='N') — **Ghi**: `ACCT_TRNS_HSTR` (CHART_ACCT_CD/SEQ_NO; DELETE→null idempotent)
- Lỗi: `WBE_TRANSACTION_CHART_ACCOUNT_NOT_FOUND`/`..._CODE_MISMATCH`.

### PATCH/DELETE `.../{trnsSeqNo}/purpose`
- **Đọc**: `ACCT_TRNS_HSTR`, `BUSI_USAG_SET_INFM` (USE_YN='Y'), `ACCT_TRNS_RCV_PAY_INFM` — **Ghi**: `ACCT_TRNS_RCV_PAY_INFM` (RCV_PAY_USAG_CD; ensureRcvPayRow). Lỗi `WBE_TRANSACTION_PURPOSE_NOT_FOUND`.

### PATCH/DELETE `.../{trnsSeqNo}/vas-account`
- **Đọc**: `ACCT_TRNS_HSTR`, `BUSI_VAS_ACCT_INFM` (STTS='A',DEL_YN='N'), `ACCT_TRNS_RCV_PAY_INFM` — **Ghi**: `ACCT_TRNS_RCV_PAY_INFM` (VAS_ACCT_CD). Lỗi `WBE_TRANSACTION_VAS_ACCOUNT_NOT_FOUND`.

### PATCH/DELETE `.../{trnsSeqNo}/contact`
- **Chức năng**: Gán/xóa Contact (snapshot RCV_PAY_TRCO_KEY + RCV_PAY_TRCO_NM) — `fnsh_0200_001_c001`.
- **Đọc**: `ACCT_TRNS_HSTR`, `TRCO_INFM` (non-trash TRCO_STTS≠'C'), `ACCT_TRNS_RCV_PAY_INFM` — **Ghi**: `ACCT_TRNS_RCV_PAY_INFM`. Lỗi `WBE_TRANSACTION_CONTACT_NOT_FOUND`.

### GET `.../match-candidates` · POST/DELETE `.../match`
- **Chức năng**: Smart Organizer match giao dịch ↔ scheduled-payment nội địa — `comm_0196_*`.
- **Đọc**: `ACCT_TRNS_HSTR`, `SCDUL_PYMN_SUMR_DETL`, `SCDUL_PYMN_SUMR`, `BUSI_MATCH_LOG` — **Ghi (match)**: `BUSI_MATCH_LOG` (INSERT/unlock LOCKED_YN='Y'), `ACCT_TRNS_HSTR` + `SCDUL_PYMN_SUMR_DETL` (LINK_MATCH_YN='Y'); **undo**: LOCKED_YN='N' (giữ history), LINK_MATCH_YN='N'.
- **Xử lý**: chỉ payTypeCd='S' (`WBE2002`); guard đã match (`WBE_MATCH_ALREADY_MATCHED`/`..._PAY_ALREADY_MATCHED`); MATCH_LOG_SEQ=MAX+1; 3 bảng trong 1 tx.

### POST `/accounts/transactions/export`
- **Phân quyền**: `@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')`.
- Export XLSX Transaction History (screenId `banking-transaction-history`, bắt buộc `filters.acctSeqNo`).

---

## ReportController `/api/v1/banking/reports`

> **Phân quyền theo product-tree leaf** (F-3 Phase B-1): gate khớp đúng nhánh nav. Daily Cash + Daily Financial (CMC) là leaf F/M → H bị từ chối; Daily Financial HS là leaf H → F/M bị từ chối.

| Endpoint | Chức năng | Đọc | Ghi | Phân quyền | Trạng thái |
|---|---|---|---|---|---|
| GET `/daily-cash` | Daily Cash cũ | ACCT_MSTR (+BANK_INFM) | — | `canAccess('banking-reports-daily-cash')` | **STUB summary** (per-currency = `List.of()`, chờ Sprint 2), ledger thực |
| GET `/daily-financial` | Daily Financial CMC | ACCT_MSTR (+BANK_INFM) | — | `canAccess('banking-reports-daily-financial')` | **Partial** — header/ledger thực, transactions skeleton; cờ `activeAccountsOnly`/`includeInternalTransfers` (default `true`) chưa rẽ nhánh |
| GET `/daily-financial/transactions` | Popup giao dịch per-account — `rept_0015_002_r001` | ACCT_TRNS_HSTR | — | `canAccess('banking-reports-daily-financial')` | running balance theo TRSC_TM; `includeInternalTransfers` no-op; phân trang `page`/`size` (default 1/15) |
| GET `/daily-financial-hs` | HAENGSUNG ledger | — | — | `canAccess('banking-reports-daily-financial-hs')` | **STUB hardcode** — trả dataset cố định deterministic (chờ port `rept_0017_001`) |

## DailyCashReportController `/api/v1/banking/reports/daily-cash-report`
> **Phân quyền**: cả GET + `/export` đều `@menuAuthz.canAccess('banking-reports-daily-cash')` (leaf F/M; H bị từ chối).
- GET — Daily Cash mới (`rept_0009_001_r001`). Tham số `asOfDate` (bắt buộc, `YYYY-MM-DD`) + `currency` (qua `DailyCashCurrencyFilter.parse`). **Đọc**: `DAILY_CASH_REPT_DAILY_SUMR_DETL` (+BANK_INFM). **Ghi**: `AUDIT_LOGS_INFM` (WSVC_ID=`daily-cash-report`). 3 tier: accounts detail / currency summary (ACCT_TYPE 01/11/12/13/21) / currency flow.
- POST `/export` — XLSX (`DailyCashReportExportProvider`).

## CashFlowReportController `/api/v1/banking/reports/cash-flow-report`
> Legacy `cash_0001_001` public, refactor **bắt buộc JWT**.
> **Phân quyền**: cả GET + `/export` đều `@menuAuthz.canAccess('banking-reports-cash-flow')` (leaf F/M; H bị từ chối).
- GET — Cash Flow theo cây chart-of-accounts (Phase 2, không stub). Tham số `year` (bắt buộc), `quarter`, `baseCurrency` (bắt buộc), `usdRate`/`krwRate`, `bankFilters` (JSON; malformed → `WBE_VALIDATION`). **Đọc**: `CHART_ACCT_INFM` + qua `PaymentDataAggregator`: `ACCT_TRNS_HSTR`, `CARD_TRNS_HSTR`, `OVRS_PYMN_MSTR`, `SCDUL_PYMN_SUMR(_DETL)`. **Xử lý**: validate year/quarter/baseCurrency (rate bắt buộc nếu khác base → `WBE_CASH_FLOW_RATE_REQUIRED`); bucket L3 12 tháng; opening→end→sales-profit qua `BalanceCalculator`.
- POST `/export` — writer riêng `CashFlowExcelWriter` (KHÔNG dùng pipeline chung — cần layout merged/subtotal; bỏ qua `columns`/`sort` của shape chung, tự parse `filters`).

## ExchangeRateController `/api/v1/banking`
> Dữ liệu bank toàn cục — KHÔNG lọc biz_key.
> **Phân quyền**: `@PreAuthorize` ở cấp class `@menuAuthz.canAccess('payment-overseas-exchange')` — áp cho cả 3 endpoint bên dưới.
- GET `/exchange-rates` — list tỷ giá theo ngày/bank/round (tham số `ref_date` `YYYYMMDD`, `bank_cd`, `seq_no`, `sort_field`/`sort_direction`). **Đọc**: `BANK_EXCH_RATE_INFM` (+BANK_INFM). **Ghi**: `BANK_EXCH_RATE_INFM` (chỉ khi on-demand: hôm nay rỗng + không có ref_date → `ExchangeRateBatchService.runOnDemand` fetch **Coocon Shinhan** UPSERT). Sort whitelist `ExchangeRateSortField`.
- GET `/exchange-rates/banks` — danh sách bank có data.
- GET `/exchange-rates/currencies` — danh sách currency distinct.

## TransferController `/api/v1/banking/transfers`
> Sprint 1 read-only — không có execution. Endpoint `/transfers/history` (UPPER_SNAKE) đã bị **XOÁ 2026-06-07** (cùng các DTO `TransferRow`/`TransferHistoryResponse` + `TransferHistoryService`); thay bằng `GET /api/v1/banking/internal-transfer/transfer-history` (lowercase snake_case) ở `transferhistory.controller.TransferHistoryController` — frontend đã dùng bản mới.
- GET `/own-accounts` — **STUB** (shape Account Inquiry, không wizard). **Phân quyền**: `@menuAuthz.canAccess('banking-internal-transfer-own')`. **Đọc**: ACCT_MSTR (+BANK_INFM, BANK_CERT_INFO). **Gọi**: `AccountService.ownAccounts`.

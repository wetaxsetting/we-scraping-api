# 03 — BANKING SUB-MODULES


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> **Phân quyền (sit):** `@EnableMethodSecurity` đã **BẬT** (`SecurityConfig`). Một số controller dưới đây mang `@PreAuthorize("@menuAuthz.canAccess(...)")` / `canAccessAny(...)` (bean `MenuAuthorizationGuard`). Khi bị từ chối → `AccessDeniedException` → HTTP 403 / `WBE1003`. Mỗi mục ghi rõ mã phân quyền nếu có.

## 1. Bank Credentials `/api/v1/banking/credentials`
CRUD `BANK_CERT_INFO` (password AES-GCM qua `BankCertCryptoService`, không trả plaintext).

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List credential tenant (username + status, không password) | BANK_CERT_INFO | — |
| POST `/` | Tạo (SEQ_NO=MAX+1, CERT_INFO_DV='01', password encrypt) | BANK_CERT_INFO | BANK_CERT_INFO |
| PUT `/{seqNo}` | Update username/password (`WBE2050` nếu không thấy) | BANK_CERT_INFO | BANK_CERT_INFO |
| DELETE `/{seqNo}` | Hard-delete | BANK_CERT_INFO | BANK_CERT_INFO |

## 2. NXISAS Scraping `/api/v1/banking/scraping` — **gọi Coocon NXISAS gateway**
Session UUID gắn `(bizKey,userId)` trong `NxisasSessionRegistry` (in-memory). Mỗi lần gọi gateway ghi `NXSIAS_MODULE_TRAN_LOG_HIS` (REQUIRES_NEW).

| Endpoint | Chức năng | Đọc | Ghi | API ngoài |
|---|---|---|---|---|
| POST `/account-list` | Scrape danh sách TK từ credential đã lưu (LOGIN→ACCT_LIST) | BANK_CERT_INFO | NXSIAS_MODULE_TRAN_LOG_HIS | Coocon LOGIN/ACCT_LIST |
| POST `/verify` | Verify credential inline trước khi lưu (SETUP/CARD_REGISTER qua `scrape_kind`) | — | NXSIAS_MODULE_TRAN_LOG_HIS | Coocon LOGIN + ACCT_LIST/CARD_INFO |
| POST `/{sid}/otp` | Gửi OTP tiếp tục (`WBE2060`/`WBE9001`) | session | NXSIAS_MODULE_TRAN_LOG_HIS | Coocon OTP |
| POST `/{sid}/qna` | Gửi đáp án security-question | session | NXSIAS_MODULE_TRAN_LOG_HIS | Coocon QNA |
| POST `/{sid}/captcha` | Gửi CAPTCHA | session | NXSIAS_MODULE_TRAN_LOG_HIS | Coocon CAPTCHA |

## 3. Own Account Transfer `/api/v1/banking/own-account-transfer` — **9 endpoint**
Engine NXiSAS chạy trên browser; Spring đứng sau scrape, chỉ lưu kết quả. Audit `AUDIT_LOGS_INFM` (wsvc `own-account-transfer`) cho nhóm domain.
**Phân quyền:** `@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-own')")` ở **cấp class** — áp cho cả 9 endpoint. Bị từ chối → 403 / `WBE1003`.

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/source-accounts` | TK nguồn VND (type 03/05, active) — `trns_0001_001_r001` | ACCT_MSTR, ACCT_AUTH_INFO, BANK_INFM, BANK_CERT_INFO | AUDIT_LOGS_INFM |
| POST `/balance-refresh` | Cập nhật số dư sau scrape — `trns_0001_001_u001` | ACCT_MSTR, ACCT_AUTH_INFO | ACCT_MSTR, AUDIT_LOGS_INFM |
| POST `/engine-connection-check` (204) | Sink audit handshake engine | — | AUDIT_LOGS_INFM |
| GET `/napas-bank-info/{cd}` | Tra từ điển NAPAS — `trns_0001_001_r003` | NAPAS_BANK_INFO | AUDIT_LOGS_INFM |
| GET `/target-accounts` | TK đích own-only VND (loại trừ nguồn) | ACCT_MSTR, ACCT_AUTH_INFO, BANK_INFM, NAPAS_BANK_INFO | AUDIT_LOGS_INFM |
| POST `/scraping-result` | Lưu kết quả chuyển tiền (idempotent) — `trns_0002_001_c001` | NAPAS_REMIT_TRNS_HSTR, NAPAS_BANK_INFO, ACCT_MSTR, ACCT_AUTH_INFO | NAPAS_REMIT_TRNS_HSTR, AUDIT_LOGS_INFM |
| POST `/nxisas/cert-acct-list` | Trả cert payload cho bundle — `nxisas_acctList_r003` | BANK_CERT_INFO | — |
| POST `/nxisas/decrypt` & `/encrypt` | Pass-through (SEED-CBC không bundle) | — | — |
| POST `/nxisas/logging` (204) | Ghi audit terminal job | — | NXSIAS_MODULE_TRAN_LOG_HIS |

**`scraping-result` validate (thứ tự):** idempotency (`WBE_OAT_IDEMPOTENCY_CONFLICT`) → CCY VND → shape NAPAS/SAME_BANK → amount tolerance (`WBE_OAT_AMOUNT_MISMATCH`) → ownership nguồn (W-grant) → shape+ownership đích → INSERT (seq MAX+1, retry 1 lần).

## 4. Smart Organizer — hai controller riêng

### 4a. `SmartOrganizerController` `/api/v1/banking/smart-organizer` — Eligibility/Confirm (`fnsh_0200_001_r001` + `_c001`)
**Phân quyền (mỗi endpoint):** `@PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")`. Bị từ chối → 403 / `WBE1003`.

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/eligible` | Giao dịch khớp rule chưa confirm. Params `bank_cd`, `acct_no`, `from_dt`, `to_dt` (bắt buộc) + `q`/`sort_field`/`sort_dir`/`page`(default 1)/`size`(default 15). Sort qua View Setting `smart-organizer` (V162), unknown → default `trsc_dt DESC`. | `ACCT_TRNS_HSTR`, `ACCT_MSTR` (type 03/04/05/06), `ACCT_TRNS_RCV_PAY_INFM` (loại đã confirm), `RMRK_ORG_INFM` (native LATERAL JOIN khớp RCV_PAY_DV theo INOUT_DV, MATCH_TYPE 'E'/ILIKE) | — |
| POST `/confirm` | Bulk-confirm các row đã review (scope theo TK — R11). Lỗi `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND` / `..._ALREADY_CONFIRMED`. | — | `ACCT_TRNS_RCV_PAY_INFM` (INSERT mỗi row, RCV_PAY_SEQ_NO qua `wb_get_next_val`) |

### 4b. `OrganizerRuleController` `/api/v1/banking/smart-organizer/rules` — CRUD rule (Organizer Settings popup, `fnsh_0200_002_*`)
CRUD `RMRK_ORG_INFM` (key RMRK_SEQ_NO qua `wb_get_next_val` V161). **Phân quyền:** **không** có `@PreAuthorize` — chỉ cần JWT (controller riêng, không kế thừa gate của 4a).

| Endpoint | Chức năng | Ghi |
|---|---|---|
| GET `/rules` | List rule sort DISP_ORD | — |
| POST `/rules` | Tạo (RMRK_SEQ_NO qua legacy, DISP_ORD=MAX+1) | RMRK_ORG_INFM |
| PUT `/rules/{rmrk_seq_no}` | Update (`WBE_SMART_ORGANIZER_RULE_NOT_FOUND`) | RMRK_ORG_INFM |
| DELETE `/rules/{rmrk_seq_no}` | Hard-delete | RMRK_ORG_INFM |
| POST `/rules/reorder` | Bulk DISP_ORD (`..._DUPLICATE_REORDER`) | RMRK_ORG_INFM |

## 5. Sync History `/api/v1/banking/sync-history`
- GET — lịch sử scrape (`fnsh_0001_003`). Params `from_dt`/`to_dt` (bắt buộc, `yyyyMMdd`, filter REF_DATE) + `currency` (`VND`/`FCY`/code/`ALL`/blank) + `bank_cd` (blank=All) + `result` (`success`/`failed`/blank). **Đọc**: `EXT_TRAN_CALL_HIS` (SVC_ID='REAL_TIME_ONLINE_INQUIRY') ⋈ `ACCT_MSTR`, resolve bank/type qua `BankCdResolver`. Read-only, BETWEEN REF_DATE, ORDER BY REG_DTM DESC. **Phân quyền:** không có `@PreAuthorize` — chỉ JWT. Không phân trang (load full window).

## 6. Transfer History `/api/v1/banking/internal-transfer/transfer-history`
- GET — `trns_0003_001_r001`. Params `from_dt`/`to_dt` (bắt buộc, `YYYYMMDD`) + `trns_stts` (blank/`00000000` success/`99999999` failed) + `page_no` (≥1) + `page_per_cnt` (10/15/20/50/100) + `sort_field`/`sort_direction`. **Đọc**: `NAPAS_REMIT_TRNS_HSTR` (+aggregate totals), `USER_INFM` (REG_ID→tên), `BANK_INFM` (out_bank_nm qua resolver), DB fn `wb_get_napas_bank_nm_or_citad_bank_nm` (in_bank_nm). Read-only, không audit (OQ-4). Validate `to≥from` (`WBE_TRANSFER_HISTORY_INVALID_PERIOD`); status filter; sort whitelist `TransferHistorySortField`. **Phân quyền (sit, cấp class):** `@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-history')")` → 403 / `WBE1003` khi bị từ chối. (Doc cũ 2026-06-05 ghi "không `@PreAuthorize`" theo OQ-5 — nay đã wire gate sau khi server-side menu authz lên.)

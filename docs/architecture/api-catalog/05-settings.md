# 05 — SETTINGS (`domain/setting`)

Audit ghi `AUDIT_LOGS_INFM` qua `AuditLogService` (REQUIRES_NEW). Export XLSX qua `ExcelExportService` (giao cột với `SYS_SCR_OPTION_WECMS`/`USER_SCR_OPTION_WECMS`).

**Phân quyền (cập nhật theo `sit`):** hầu hết controller settings nay đã bật `@PreAuthorize` ở mức class với guard `@menuAuthz.canAccess('<menu-code>')` (rollout F-3 menuAuthz). Guard này cộng thêm trên nền JWT `authenticated()` — ADMIN qua vô điều kiện (mã `settings-*` là product-invariant nên `MenuAuthorizationGuard` bỏ qua cây sản phẩm), user thường phải có grant `USE_YN='Y'` trong `USER_MENU_AUTH_INFM`. Bị chặn → HTTP 403 / `WBE1003`. Test build-time: `EndpointAuthorizationCoverageTest`. Mã menu mỗi controller ghi trong từng mục. (ScreenOptionController và EnvironmentSettingsController KHÔNG có `@PreAuthorize` — chỉ JWT.)

---

## 1. View Setting `/api/v1/screen-options` (lazy-INSERT, `comm_0108_001`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/{webSvcId}/areas` | List DYM_AREA_ID + count cột | SYS_SCR_OPTION_WECMS | — |
| GET `/{webSvcId}/{dymAreaId}` | Cấu hình cột merged (sys+user) | SYS_SCR_OPTION_WECMS, USER_SCR_OPTION_WECMS | USER_SCR_OPTION_WECMS (lazy-INSERT lần đầu) |
| PUT `/{webSvcId}/{dymAreaId}` | Lưu override (delete-then-insert) | SYS_SCR_OPTION_WECMS | USER_SCR_OPTION_WECMS |
| DELETE `/{webSvcId}/{dymAreaId}` | Reset về default (delete + re-init) | SYS_SCR_OPTION_WECMS | USER_SCR_OPTION_WECMS |

Rule: `FIX_YN='Y'` & special `CHECKBOX`/`ETC` luôn ép `USE_YN='Y'` + DISP_ORD từ sys; fieldId lạ bị bỏ. Unknown screenId → `WBE2001`.

## 2. Environment Settings `/api/v1/settings/environment-settings` (không audit)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/{env_set_inf}` | Đọc giá trị COALESCE(override, default) | SYS_ENV_SET_INFM, BUSI_ENV_SET_INFM | — |
| PUT `/{env_set_inf}` | Update override (UPDATE-only, phải seed) | SYS_ENV_SET_INFM, BUSI_ENV_SET_INFM | BUSI_ENV_SET_INFM |
Code regex `[A-Za-z0-9]{5}`. Lỗi `WBE2030` (sys missing), `WBE2031` (busi row missing).

## 3. Firm Banking Settings `/api/v1/settings/firm-banking-settings` (PK `BIZ_KEY+OUT_BANK_CD+WDRL_ACT_NO`, gate `settings-firm-banking-settings`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List binding | V_FIRM_BANKING_SETTING | — |
| GET `/{out_bank_cd}/{wdrl_act_no}` | Detail (cleartext wdrl_act_no) | TB_FIRM_CONF | — |
| GET `.../audit` | Lịch sử audit binding | AUDIT_LOGS_INFM | — |
| POST `/` | Tạo (FILE_TYPE server-derive) | TB_FIRM_BANK_CODE, TB_FIRM_CONF | TB_FIRM_CONF, AUDIT (4 locale) |
| PUT `/{...}/{...}` | Update 3 field (BANK_PRTN_ID/VAN_ORG_CD/ORG_STAT) | TB_FIRM_CONF | TB_FIRM_CONF, AUDIT |
| DELETE `/{...}/{...}` | Hard-delete (**không audit** — parity) | TB_FIRM_CONF | TB_FIRM_CONF |
Lỗi: `WBE0001` (bank ngoài set 970424/970436/970457), `WBE0002` (không NAPAS), `WBE2020` (không thấy), `WBE2021` (PK trùng).

## 4. Preferences `/api/v1/settings/preferences` (single-row BUSI_INFM, gate `settings-core-preference`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | Profile công ty (+logo_url) | BUSI_INFM | — |
| PUT `/` | Lưu profile (không động CMPN_LOGO) | BUSI_INFM | BUSI_INFM, AUDIT (wsvc `preferences`) |
| POST `/logo` (multipart) | Upload logo (≤2MB, jpg/png/gif/svg) | BUSI_INFM | BUSI_INFM.CMPN_LOGO + file (không audit) |
| DELETE `/logo` | Xóa logo (idempotent) | BUSI_INFM | BUSI_INFM.CMPN_LOGO=null + xóa file |
| GET `/logo` | Stream raw bytes (binary, không envelope) | BUSI_INFM | — |
Lỗi: `WBE2080` (không thấy company), `WBE2081-2087` (logo IO/validate/missing).

## 5. Bank Account `/api/v1/settings/bank-account` (`comm_0183_*`, gate `settings-core-bank-account`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List TK (active+inactive) + label quyền V/W | ACCT_MSTR, BANK_INFM, ACCT_AUTH_INFO | — |
| PATCH `/{bankCd}/{acctNo}` | Patch nickname/desc/type/maturity/VAS | ACCT_MSTR, BUSI_VAS_ACCT_INFM | ACCT_MSTR, AUDIT (wsvc `bank-account`) |
| POST `/register` | Bulk-INSERT TK sau scrape (skip dup) | ACCT_MSTR | ACCT_MSTR, AUDIT |
| GET `/{bankCd}/{acctNo}/permissions?type=` | Roster user cho dialog quyền (selected/locked) | ACCT_MSTR, ACCT_AUTH_INFO, USER_BUSI_CONN_INFM, BUSI_ADMN_INFM | — |
| PUT `/{bankCd}/{acctNo}/permissions` | Lưu quyền (replace-semantics, soft-revoke) | như trên | ACCT_AUTH_INFO, AUDIT |
Lỗi: `WBE_BANK_ACCOUNT_NOT_FOUND`, `..._INVALID_TYPE/DATE/VAS_ACCT`, `WBE_USER_NOT_IN_TENANT`, `WBE_ADMIN_REQUIRED`, `WBE_CANNOT_REVOKE_SELF`.

## 6. Chart of Accounts `/api/v1/settings/chart-of-accounts` (`comm_0223_001_*`, restricted ACC-01/02/03, gate `settings-core-chart-of-accounts`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List active + search ILIKE | CHART_ACCT_INFM | — |
| GET `/tree` | Cây parent-picker (level 1..4) | CHART_ACCT_INFM | — |
| GET `/{seq_no}` | Detail | CHART_ACCT_INFM | — |
| POST `/` | Tạo/revive (seq qua `wb_get_next_val` V95, retry 1 lần) | CHART_ACCT_INFM | CHART_ACCT_INFM, AUDIT |
| PUT `/{seq_no}` | Update (seq bất biến) | CHART_ACCT_INFM | CHART_ACCT_INFM, AUDIT |
| POST `/bulk-upload` | Bulk-create từ Excel (mỗi row 1 tx) | CHART_ACCT_INFM | CHART_ACCT_INFM, AUDIT |
| POST `/bulk-delete` | Bulk soft-delete (re-parent children) | CHART_ACCT_INFM | CHART_ACCT_INFM, AUDIT |
| POST `/bulk-deactivate` | Bulk STTS='I' (idempotent) | CHART_ACCT_INFM | CHART_ACCT_INFM, AUDIT |
| DELETE `/{seq_no}` | Soft-delete (re-parent + archive-rename UK collision) | CHART_ACCT_INFM | CHART_ACCT_INFM, AUDIT |
Validate qua `ChartOfAccountsValidator`: self-parent / parent-not-found / depth-cap / cycle / restricted / duplicate-code → các `WBE_CHART_OF_ACCOUNTS_*`.

## 7. Approval Line `/api/v1/settings/approval-line` (không audit, max 5 approver, gate `settings-core-approval-line`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List + chuỗi approver inline (q, sort) | APPR_LINE_INFM(_DETL), USER_BUSI_CONN_INFM | — |
| GET `/{appr_line_no}` | Detail | APPR_LINE_INFM(_DETL), USER_BUSI_CONN_INFM | — |
| POST `/` | Tạo (APPR_LINE_NO qua `wb_get_next_val` V91) | USER_BUSI_CONN_INFM | APPR_LINE_INFM(_DETL) |
| PUT `/{appr_line_no}` | Đổi tên + diff-sync chuỗi (INSERT/UPDATE/DELETE) | APPR_LINE_INFM(_DETL), USER_BUSI_CONN_INFM | APPR_LINE_INFM(_DETL) |
| DELETE `/{appr_line_no}` | Hard-delete master+detail | APPR_LINE_INFM | APPR_LINE_INFM(_DETL) |
Lỗi: `WBE_APPROVAL_LINE_NOT_FOUND/NAME_BLANK/EMPTY_APPROVERS/TOO_MANY_APPROVERS/DUP_APPROVER/APPROVER_NOT_IN_TENANT`.

## 8. Contacts `/api/v1/settings/contacts` (soft-delete TRCO_STTS='C', gate `settings-core-contacts`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List (đã loại trash) + filter + sort | TRCO_INFM, TRCO_ADDR/ACCT/OVRS_INFM, TB_FIRM_BANK_CODE | — |
| GET `/{trcoKey}` | Detail | như trên | — |
| POST `/` | Tạo (TRCO_KEY qua `wb_get_next_val`) + seed PGRO | TRCO_INFM | TRCO_INFM, ADDR, ACCT (L), OVRS (O), PGRO_BSAC_LNKN_INFM (B+S) |
| PUT `/{trcoKey}` | Full-update + upsert/delete sub (guard in-use `WBE1190`) | TRCO_INFM, ACCT_TRNS_RCV_PAY_INFM | TRCO_INFM + sub-tables |
| DELETE `/{trcoKey}` | Soft-delete (guard in-use) | TRCO_INFM, ACCT_TRNS_RCV_PAY_INFM | TRCO_INFM |
| POST `/bulk-delete` | Bulk soft-delete | như trên | TRCO_INFM |
| POST `/export` | XLSX list (`ContactsExportProvider`, screenId `settings-contacts-local`/`-overseas`) | + View Setting | — |
| POST `/excel/import` | Bulk import (mỗi row 1 tx) | như POST tạo | TRCO_INFM + sub |
| POST `/excel/preflight` | Check trùng hand_key/tax_id | TRCO_INFM | — |
| POST `/excel/resolve-recipients` | Resolve tên bank→NAPAS/CITAD + holder | TB_FIRM_BANK_CODE | — |
| POST `/by-codes` | Batch fetch theo HAND_TRCO_KEY (overseas preview) | TRCO_INFM + sub + TB_FIRM_BANK_CODE | — |
| GET `/banks` | Autocomplete NAPAS+CITAD (global) | TB_FIRM_BANK_CODE | — |
| POST `/lookup-recipient` | Lookup recipient (stub qua inquiry) | — | — |
| GET `/{trcoKey}/files` | List attachment | TRCO_INFM, TRCO_INFO_FILE | — |
| POST `/{trcoKey}/files` (multipart) | Upload (≤10MB; FILE_ID qua `wb_get_next_val`) | TRCO_INFM, USER_INFM | TRCO_INFO_FILE + file |
| GET `/{trcoKey}/files/{fileId}` | Download (octet-stream) | TRCO_INFM, TRCO_INFO_FILE | — |
| DELETE `/{trcoKey}/files/{fileId}` | Xóa attachment + file | TRCO_INFM, TRCO_INFO_FILE | xóa TRCO_INFO_FILE + file |

## 9. ERP Transaction History `/api/v1/settings/erp-transaction-history` (read-only, gate `settings-erp-transaction-history`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List + 4 tab count (from/to bắt buộc) | ERP_TRNS_HSTR ⋈ USER_INFM | — |
| GET `/{seqNo}/download` | Download file (`WBE2014`/`WBE2015`) | ERP_TRNS_HSTR | — |
| GET `/{seqNo}/thumbnail` | **Stub** → `WBE9001` | — | — |
| POST `/export` | XLSX (`ErpTrnsHistoryExportProvider`) | + View Setting | — |

## 10. Firm Banking History `/api/v1/settings/firm-banking-history` (read-only, view; gate `hasAnyRole('ADMIN','USER') and @menuAuthz.canAccess('settings-firm-banking-history')`)
- GET `/` — list paged (svc_type F/S/G/B). **Đọc**: `V_FIRM_BANKING_HISTORY` (5-leg view). Resolve svc qua `FirmBankingSvcType`+`FirmBankingServiceNameResolver`.
- POST `/export` — XLSX (`FirmBankingHistoryExportProvider`).
- Cả 2 endpoint kế thừa gate class-level (GAP-4: legacy `NORM_USER_ONLY_YN=Y` → map thành "user công ty đã provision", tức ADMIN hoặc USER).

## 11. Login Credentials `/api/v1/login-credentials` (base path KHÔNG có `/settings`, gate `settings-core-login-credentials`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List gộp (group/BANK_CD + GDT) | BANK_CERT_INFO, GDT_CERT_INFO, ACCT_MSTR, BANK_INFM | — |
| GET `/banks` | Bank scraping-capable | BANK_INFM_DETL | — |
| GET `/banks/{bankCd}/form-info` | Form-info per bank | BANK_INFM_DETL | — |
| POST `/bank` | Tạo bank cred (find-or-create dedup) | BANK_CERT_INFO | BANK_CERT_INFO, AUDIT |
| PUT `/bank/{seqNo}` | Update (verify oldPassword `WBE2091`) | BANK_CERT_INFO | BANK_CERT_INFO, AUDIT |
| POST `/bank/{seqNo}/verify-update` | Verify password mới qua **NXISAS LOGIN** (không ghi DB) | BANK_CERT_INFO | — |
| POST `/gdt` · PUT `/gdt/{seqNo}` | Tạo/Update GDT cred (1/tenant) | GDT_CERT_INFO | GDT_CERT_INFO, AUDIT |
| DELETE `/{type}/{seqNo}` | Xóa cred (bank/gdt) | tương ứng | tương ứng, AUDIT |
| POST `/{type}/{seqNo}/reveal` | Xác nhận password → trả plaintext (`WBE2091`) | tương ứng | — |
| GET `/{type}/{seqNo}/audit-logs` | Lịch sử audit | AUDIT_LOGS_INFM | — |
Lỗi: `WBE2090` (not found), `WBE2092` (type lạ), `WBE_LOGIN_CREDENTIAL_GDT_EXISTS`.

## 12. Purpose Settings `/api/v1/settings/purpose-settings` (cây 2 cấp BUSI_USAG_SET_INFM, gate `settings-core-purpose`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List active dạng cây (+in-use flag) | BUSI_USAG_SET_INFM, V_PURPOSE_IN_USE | — |
| GET `/{use_usag_cd}` | Detail | như trên | — |
| POST `/` | Tạo (PK qua `wb_get_next_val`) | BUSI_USAG_SET_INFM | BUSI_USAG_SET_INFM, AUDIT |
| PUT `/{use_usag_cd}` | Update (cấm demote khi có con) | BUSI_USAG_SET_INFM, V_PURPOSE_IN_USE | BUSI_USAG_SET_INFM, AUDIT |
| DELETE `/{use_usag_cd}` | Soft-delete (`WBE1199` nếu có con/in-use) | BUSI_USAG_SET_INFM, V_PURPOSE_IN_USE | BUSI_USAG_SET_INFM, AUDIT |
| PUT `/order` | Bulk reorder | BUSI_USAG_SET_INFM | BUSI_USAG_SET_INFM, AUDIT |
| POST `/excel/preview` (multipart) | Parse/validate 2-pass | BUSI_USAG_SET_INFM | — |
| POST `/excel/import` (JSON) | Commit (append-only, atomic) | BUSI_USAG_SET_INFM | BUSI_USAG_SET_INFM, AUDIT |
Lỗi: `WBW1152` (dup code), `WBW1138` (dup tên), `WBE2041/2042/2043` (excel).

## 13. User Management `/api/v1/settings/users` (không audit, không plan cap; phân quyền SEC-1 split theo action)
Phân quyền per-method (gate menu `settings-core-users` luôn bắt buộc):
- **Read** (`GET /`, `GET /{user_id}`, `GET /{user_id}/image`): `@userMgmtAccess.canViewUsers() and @menuAuthz.canAccess('settings-core-users')` — ADMIN hoặc user có grant menu (xem read-only).
- **Write + quản lý** (`GET /check-id`, `POST /`, `PUT`, `DELETE`, cả 2 `/menu-entitlement`, upload/delete avatar): `hasRole('ADMIN') and @menuAuthz.canAccess('settings-core-users')`. Service giữ guard admin defense-in-depth (`TenantContext.currentIsAdmin()`). `settings-core-users` chỉ cấp quyền read, không cấp quyền quản lý.

| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List user active + count | USER_BUSI_CONN_INFM ⋈ USER_INFM, BUSI_ADMN_INFM | — |
| GET `/check-id` | Check login_id trống (global) | USER_INFM | — |
| GET `/{user_id}` | Detail | USER_BUSI_CONN_INFM, USER_INFM, BUSI_ADMN_INFM | — |
| POST `/` | Tạo user (+admin) | USER_INFM | USER_INFM, USER_BUSI_CONN_INFM, BUSI_ADMN_INFM |
| PUT `/{user_id}` | Update profile/password/admin | 3 bảng trên | USER_INFM, USER_BUSI_CONN_INFM, BUSI_ADMN_INFM |
| DELETE `/{user_id}` | Unlink khỏi tenant (soft) + revoke quyền TK | USER_BUSI_CONN_INFM, BUSI_ADMN_INFM, ACCT_AUTH_INFO | USER_BUSI_CONN_INFM (DEL_YN='Y'), xóa BUSI_ADMN_INFM, revoke ACCT_AUTH_INFO |
| GET/PUT `/{user_id}/menu-entitlement` | Đọc/thay grant menu (delete-then-insert) | USER_BUSI_CONN_INFM, USER_MENU_AUTH_INFM | USER_MENU_AUTH_INFM |
| POST/DELETE/GET `/{user_id}/image` | Upload/xóa/stream avatar (≤2MB) | USER_BUSI_CONN_INFM | USER_BUSI_CONN_INFM.USER_IMG + file |
Lỗi: `WBE_USERS_NOT_FOUND/DUPLICATE_LOGIN_ID/CANNOT_DELETE_SELF/CANNOT_DEMOTE_LAST_ADMIN`, `WBE2111-2118` (image), `WBE1003` (bị chặn gate).

## 14. VAS Account `/api/v1/settings/vas-account` (soft-delete DEL_YN='Y', gate `settings-core-vas-account`)
| Endpoint | Chức năng | Đọc | Ghi |
|---|---|---|---|
| GET `/` | List paged + filter (`all=true` → trả toàn bộ không phân trang) | BUSI_VAS_ACCT_INFM | — |
| GET `/lookup` | Picker active+live (cap 500) | BUSI_VAS_ACCT_INFM | — |
| GET `/{vasAcctSeqNo}` | Detail (`WBE2040`) | BUSI_VAS_ACCT_INFM | — |
| POST `/` | Tạo (seq qua `VasAcctSeqNoGenerator` Java MAX+1) | BUSI_VAS_ACCT_INFM | BUSI_VAS_ACCT_INFM, AUDIT |
| PUT `/{vasAcctSeqNo}` | Update (dup-check khi đổi code `WBE0003`) | BUSI_VAS_ACCT_INFM | BUSI_VAS_ACCT_INFM, AUDIT |
| DELETE `/{vasAcctSeqNo}` | Soft-delete | BUSI_VAS_ACCT_INFM | BUSI_VAS_ACCT_INFM, AUDIT |
| POST `/excel/preview` (multipart) | Parse/validate | BUSI_VAS_ACCT_INFM | — |
| POST `/excel/import` (JSON) | Commit (Append/Replace, atomic) | BUSI_VAS_ACCT_INFM | BUSI_VAS_ACCT_INFM, AUDIT |
| POST `/export` | XLSX (`VasAccountExportProvider`) | + View Setting | — |

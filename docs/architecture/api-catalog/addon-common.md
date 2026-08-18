# 06 — ADD-ON & COMMON

> **Phân quyền (sit):** `@EnableMethodSecurity` đã **BẬT** (`SecurityConfig`). Các controller Add-on (`EInvoiceController`, `OperationStatusController`) nay mang `@PreAuthorize("@menuAuthz.canAccess(...)")` / `canAccessAny(...)` (bean `MenuAuthorizationGuard`). Khi bị từ chối → `AccessDeniedException` → HTTP 403 / `WBE1003`. `CountryController` và `NotificationController` KHÔNG có `@PreAuthorize` ở method (chỉ `authenticated()` cấp `/api/v1/*`); riêng Notification còn scope thêm theo user+tenant trong service. Mỗi mục dưới đây ghi rõ phân quyền nếu có.

## 1. e-Invoice `/api/v1/addon/einvoices` (`BUSA_DOC_MSTR` + scrape/audit)

> Sales (`type=SALES`) và Bill/Expense (`type=EXPENSE`/`BILL`) gộp chung endpoint, phân biệt qua `type`. `EInvoiceService.resolveDocType` map wire→DB `DOC_TYPE`: `SALES`/`I`→`'I'`; `EXPENSE`/`BILL`/`B`→`'B'`; rỗng/khác (gồm cả enum legacy `BusaDocType.EXPENSE='E'`, `PAYROLLBOOK='S'`) → `WBE2002` (rule IR-1). **Lưu ý**: doc trước nghi ngờ `EXPENSE→'E'`; code thực tế trên `sit` chốt `EXPENSE`/`BILL`→`'B'` — KHÔNG có bug, đây là hành vi đúng theo legacy listing service.

### GET `/api/v1/addon/einvoices`
- **Chức năng**: List hóa đơn Sales/Bill (gộp qua `type`), paged + lọc ngày + keyword + sort, kèm tổng theo currency — legacy `einv_0001_001_r001`.
- **Đọc**: `BUSA_DOC_MSTR` — **Ghi**: —
- **Phân quyền**: `@menuAuthz.canAccessEInvoice(#p0)` (gating theo `type`).
- **Xử lý**: `resolveDocType` (IR-1); from/to bắt buộc `YYYYMMDD` `@Pattern` ở controller, calendar-validity + `from≤to` ở service → `WBE_VALIDATION`; clamp size default 10 / max 100; sort whitelist `EInvoiceSortField` (`DATE_TIME`/`INVOICE_NO`/`BUYER`/`CURRENCY`/`TOTAL`/`STATUS`/`TAX`/`PRETAX`, default `ISSU_DT DESC, REG_DTM DESC`). `findFiltered` baseline (IR-2): BIZ_KEY + DOC_TYPE + `PROV_STTS NOT IN ('VD','EE')` + `HAND_REG_YN <> 'Y'` + `ISSU_DT BETWEEN` + keyword LIKE (subset 5 cột `DOC_ID`/`TRCO_CMNM`/`TRCO_TAX_ID`/`EINV_INVOICESYMBOL`/`DISP_DOC_ID`, escape `!`). `aggregateByCurrency` GROUP BY `TRCO_TRSC_CCY_CD`.
- **Caveat**: `EINV_INVOICESTATUS` trả thô (IR-4 — chưa port `wb_get_invoice_status`); lọc/hiển thị status còn ở client (regex FE).

### POST `/api/v1/addon/einvoices/export`
- **Chức năng**: XLSX list Sales (`addon-sales-einvoices`) / Bill (`addon-expenses-einvoices`), unpaged (IR-6).
- **Đọc**: `BUSA_DOC_MSTR` + View Setting — **Ghi**: —
- **Phân quyền**: `@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')`.
- **Xử lý**: `ExcelExportService` → provider chạy lại `EInvoiceService.list`; allow-list `{DATE_TIME,INVOICE_NO,BUYER,CURRENCY,TOTAL,STATUS,TAX,PRETAX}` ∩ View Setting; subheader server-side per currency.

### POST `/api/v1/addon/einvoices/export-details`
- **Chức năng**: "Download Excel with Details" — bung mỗi hóa đơn ra dòng con; legacy `einv_0001_001_r002_excel`.
- **Đọc**: `BUSA_DOC_MSTR` + `BUSA_DOC_DETL` + `EINV_FEE_DETL` + `EINV_TAX_DETL` — **Ghi**: —
- **Phân quyền**: `@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')`.
- **Xử lý**: `EInvoiceDetailExportService` dùng cùng filter `type`/`from`/`to`/`keyword` như list; bỏ qua `columns` của View Setting (sheet Details có bộ cột cố định riêng).

### POST `/api/v1/addon/einvoices/scrape-results` — **dữ liệu nguồn từ NXiSAS/GDT**
- **Chức năng**: "Update" pha 1 (lưu kết quả `INVOICE_LIST` do trình duyệt scrape GDT rồi giải mã client-side) — legacy `nxisas_einvoice_c001`.
- **Đọc**: `BUSA_DOC_MSTR` — **Ghi**: `BUSA_DOC_MSTR` (1 dòng/hóa đơn chấp nhận, batch insert kiểu `BUSA_DOC_MSTR_C002`), `EXT_TRAN_CALL_HIS` (1 dòng audit/lần gọi).
- **Phân quyền**: `@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')`.
- **Xử lý**: `EInvoiceScrapeService.saveScrapedInvoices` re-validate + persist; tenant/user lấy từ JWT (`TenantContext`), body không mang `biz_key`. Lỗi không mong đợi → `WBE9999`.

### POST `/api/v1/addon/einvoices/scrape-detail-results` — **dữ liệu nguồn từ NXiSAS/GDT**
- **Chức năng**: "Update" pha 2 (lưu `INVOICE_DETAIL` từng hóa đơn) — legacy `nxisas_einvoice_c002`.
- **Đọc**: `BUSA_DOC_MSTR` — **Ghi**: `BUSA_DOC_MSTR` (enrich), `BUSA_DOC_DETL` (`productArr`), `EINV_FEE_DETL` (`feeArr`), `EINV_TAX_DETL` (`taxArr`) — (re)write các dòng con.
- **Phân quyền**: `@menuAuthz.canAccessAny('addon-einvoices-sales','addon-einvoices-expenses')`.
- **Xử lý**: `EInvoiceDetailScrapeService.saveScrapedDetails`; tenant/user từ JWT. Lỗi → `WBE9999`.

### GET `/api/v1/addon/einvoices/history`
- **Chức năng**: "Latest Inquiry History" — đọc lại các lần gọi scrape GDT cho popup History; legacy `nxisas_einvhis_r001`.
- **Đọc**: `EXT_TRAN_CALL_HIS` — **Ghi**: —
- **Phân quyền**: `@menuAuthz.canAccessEInvoice(#p0)`.
- **Xử lý**: `type` optional (`SALES`/`EXPENSE`; rỗng = cả hai, mặc định legacy `SVC_ID LIKE 'GDT_INQUIRY%'`); `from`/`to` bắt buộc `YYYYMMDD`; `status` optional (`success`/`fail` ↔ `STTS` 0/1). Tenant từ JWT.

### GET `/api/v1/addon/einvoices/detail-history`
- **Chức năng**: "Latest Inquiry — Detail" — drill 1 batch GDT, list hóa đơn nó sinh ra; legacy `nxisas_einvhis_dt_r001`.
- **Đọc**: `BUSA_DOC_MSTR` (khớp `MOD_DTM` = `mod_dtm`), `EXT_TRAN_CALL_HIS` — **Ghi**: —
- **Phân quyền**: `@menuAuthz.canAccessEInvoice(#p0)`.
- **Xử lý**: `type` bắt buộc (legacy chỉ chấp `SALES`; sai → `WBE2002`); `from`/`to` `YYYYMMDD`; `mod_dtm` bắt buộc `\d{14}` (yyyyMMddHHmmss); sort optional, whitelist `DOC_ID`/`ISSU_DT`/`TRCO_CMNM`/`TOTL_AMT`/`einv_invoiceStatus`, default `ISSU_DT DESC`. Tenant từ JWT.

---

## 2. Operation Status `/api/v1/addon/operation-status` (`OPRT_STTS_INFO`)

> **Phân quyền lớp class**: `OperationStatusController` mang `@PreAuthorize("@menuAuthz.canAccess('addon-operation-status-inquiry')")` ở cấp class → áp cho TẤT CẢ endpoint dưới đây (không lặp lại từng mục). Bị từ chối → 403 / `WBE1003`.

### GET `/api/v1/addon/operation-status`
- **Chức năng**: List snapshot trạng thái DN (cache cục bộ), lọc `q`/`companyStatus`, paged+sort — legacy `aosv_0001_001_r001`.
- **Đọc**: `OPRT_STTS_INFO` — **Ghi**: — (read-only).
- **Xử lý**: `q` ILIKE 2 cột `COMPANYNAME`/`TAXCODE`; `companyStatus` partial trên chuỗi GDT thô; clamp size default 10/max 100; sort whitelist `OperationStatusSortField`, default `SEARCHDATETIME DESC, TAXCODE ASC`. `resolveStatusCd` map chuỗi VN → mã (`active_certificate_issued`/`suspended`/`not_registered`).

### POST `/api/v1/addon/operation-status/export`
- **Chức năng**: XLSX (`addon-operation-status`).
- **Đọc**: `OPRT_STTS_INFO` + View Setting — **Ghi**: —
- **Xử lý**: `ExcelExportService` → `OperationStatusExportProvider` chạy lại `OperationStatusService.list`; allow-list `{COMPANY,ADDRESS,OPERATION_STATUS,LAST_UPDATE}`.

### POST `/api/v1/addon/operation-status` — **MỚI (create/persist)**
- **Chức năng**: Inquire-popup "Save" — persist 1 đăng ký đã tra GDT vào `OPRT_STTS_INFO`; legacy `aosv_0001_002_c001`.
- **Đọc**: `OPRT_STTS_INFO` (`existsById`) — **Ghi**: `OPRT_STTS_INFO` (insert).
- **Xử lý**: create-only; trùng `(BIZ_KEY, TAXCODE)` (qua `existsById` hoặc `DataIntegrityViolationException` khi đua race) → `WBE_OPERATION_STATUS_DUPLICATE` (legacy `WBE0003`). Tenant/user từ JWT; `representativeFirstName` null → `""` (cột NOT NULL). Trả về dòng đã lưu theo shape của list.

### POST `/api/v1/addon/operation-status/inquire` — **gọi API ngoài GDT**
- **Chức năng**: "Get Business Info" tra 1 Tax ID qua OSMC→NXISAS→GDT — legacy `aosv_0001_002_r001`.
- **Đọc**: `OPRT_STTS_INFO`, `EXT_TRAN_CALL_HIS` (MAX SEQ_NO theo `BI09`+refDate) — **Ghi**: `EXT_TRAN_CALL_HIS` (insert pre-call + update outcome), `NXSIAS_MODULE_TRAN_LOG_HIS` (audit).
- **Xử lý**: SEQ_NO = MAX(`BI09`, refDate)+1 → insert audit pre-call → `osmcClient.lookup([taxCode], uniqueKey)` → patch audit (rsltCd/errCont) → ghi NXISAS log → `!isSuccess`→`WBE_OSMC_LOOKUP_FAILED`; không match TAXCODE→`WBE_OSMC_NO_RESULT` (legacy `WBE1152`); trả info DN (tên/địa chỉ/người đại diện).
- **Gọi ngoài**: `OsmcGdtClient.lookup` (Coocon bigdata / masothue-GDT, ApiId=`BI09`); host theo `wecms.osmc.*` (non-PROD phải override khỏi default Coocon live).

### POST `/api/v1/addon/operation-status/update` — **gọi API ngoài GDT**
- **Chức năng**: Toolbar "Update" re-poll cache từ GDT, upsert snapshot — legacy `aosv_0001_001_u001`.
- **Đọc**: `OPRT_STTS_INFO` (`findAllByBizKey`), `EXT_TRAN_CALL_HIS` — **Ghi**: `OPRT_STTS_INFO` (upsert mỗi tax thành công), `EXT_TRAN_CALL_HIS` (1 audit batch — 1 SEQ_NO cho cả request), `NXSIAS_MODULE_TRAN_LOG_HIS` (1/tax).
- **Xử lý**: body optional; `tax_codes` null/rỗng→re-poll TẤT CẢ cache tenant; non-empty→giao với cache (loại mã lạ/cross-tenant im lặng); cache rỗng→no-op `(0,0,0)`; `osmcClient.lookup`; cả-batch fail→1 NXISAS log + `WBE_OSMC_LOOKUP_FAILED`; per-tax: có detail→upsert+NXISAS `00000000`+`updated++`, không→audit lỗi+`failed++`. Trả `(total, updated, failed)`.

---

## 3. Country `/api/v1/common/countries` (reference, không tenant-scope)

### GET `/api/v1/common/countries`
- **Chức năng**: Danh mục quốc gia (phone-country picker), sort theo tên, VN→`default_yn='Y'` — legacy `comm_0110_001_r001`.
- **Đọc**: `NATN_INFM` — **Ghi**: —
- **Phân quyền**: chỉ `authenticated()` (không `@PreAuthorize`); list dùng chung mọi tenant.
- **Xử lý**: `findAllByOrderByNatlNmAsc`; trả `ntnlCd` (ISO-2), `natlNm`, `natnNum` (mã quay số), `default_yn`.

---

## 4. Notification `/api/v1/notifications` (`NOTIFICATION`, scope user+tenant) — **MỚI**

> Module chuông thông báo header (slug `notifications`, tham chiếu legacy `comm_0192_007`). Bảng `NOTIFICATION` là addition của refactor (không có twin trong PROD active list legacy). Mọi read/write scope theo `BIZ_KEY` + `RECIPIENT_ID` từ JWT (`TenantContext`); dòng của user/tenant khác → `WBE_NOT_FOUND` (không leak). Controller KHÔNG có `@PreAuthorize` ở method — chỉ `authenticated()` cấp `/api/v1/*`. Phía ghi (producer) chưa có endpoint: `notifyApprovalRequested`/`notifyApprovalDecision` được các module payment gọi nội bộ (best-effort, `REQUIRES_NEW`, nuốt mọi exception để không rollback approval submit).

### GET `/api/v1/notifications`
- **Chức năng**: List thông báo của caller, mới nhất trước.
- **Đọc**: `NOTIFICATION` — **Ghi**: —
- **Xử lý**: `read_type=U`→chỉ chưa đọc (`READ_AT IS NULL`); `A`/rỗng→tất cả; `page` zero-based; `size` default 20 / max 100; sort `REG_DTM DESC, NOTIFICATION_ID DESC`; baseline `DEL_YN='N'`. Trả kèm tổng + `unread` count.

### GET `/api/v1/notifications/unread-count`
- **Chức năng**: Đếm chưa đọc cho badge chuông.
- **Đọc**: `NOTIFICATION` (`countBy...ReadAtIsNull`) — **Ghi**: —

### PUT `/api/v1/notifications/{notificationId}/read`
- **Chức năng**: Đánh dấu 1 thông báo đã đọc (idempotent — no-op nếu đã đọc).
- **Đọc**: `NOTIFICATION` — **Ghi**: `NOTIFICATION` (set `READ_AT`/`MOD_ID`/`MOD_DTM`).
- **Xử lý**: `notificationId` null→`WBE_VALIDATION`; không thấy/đã xóa/không thuộc user→`WBE_NOT_FOUND`.

### PUT `/api/v1/notifications/read-all`
- **Chức năng**: Đánh dấu tất cả chưa đọc của caller thành đã đọc.
- **Đọc**: `NOTIFICATION` — **Ghi**: `NOTIFICATION` (bulk `markAllRead`, set `READ_AT`).
- **Xử lý**: trả số dòng đã lật (0 nếu không có gì chưa đọc).

# VAT Declaration — API Specification / Danh sách chức năng


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

| | |
|---|---|
| **Trạng thái** | 🟡 Design — API **đề xuất**, chưa có code (branch `f1_v1_vat_declaration` reserved) |
| **Base path** | `/api/v1/addon/vat-declarations` |
| **Feature spec** | [README.md](README.md) · **Data dictionary**: [vat-declaration-db.md](../../../references/database/addon/vat-declaration-db.md) |
| **Open questions** | [vat-declaration-follow-up.md](../../../planning/open-questions/vat-declaration-follow-up.md) |
| **Cập nhật** | 2026-08-22 |

## 0. Quy ước chung (áp dụng cho mọi endpoint)

- **Envelope**: mọi endpoint JSON trả `ApiResponse<T>` = `{ status: { error, code, message }, data }`; lỗi thêm `timestamp`, `path`, `trace_id`. Ngoại lệ duy nhất: endpoint **download binary** (#12, #13, #14) trả `ResponseEntity<Resource>` + `Content-Disposition` RFC 5987.
- **Wire JSON**: lowercase **snake_case** (global Jackson `SNAKE_CASE`); DTO là Java record camelCase.
- **Auth**: JWT Bearer trên mọi endpoint; tenant (`BIZ_KEY`) đọc từ token qua `TenantContext` — **không bao giờ** nhận từ client. Gate: `@PreAuthorize("@menuAuthz.canAccess('addon-vat-declaration')")` (permission code chờ chốt — OQ-VAT-5).
- **Pagination**: `page` (0-based, default 0) + `size` (int, default 20); list DTO trả `total_count`.
- **Query params**: lowercase snake_case (`year`, `status`, `io_dv`, `search`).
- **Lỗi nghiệp vụ**: `BusinessException` → HTTP **200** với `status.error=true` (frontend đọc `status.error`, không đọc HTTP code).
- **Nguyên tắc tính toán**: backend là **calculation authority** — PUT chỉ gửi ô Nhập; server tự tính lại toàn bộ ô Tính rồi mới persist; XML/Excel chỉ serialize giá trị đã persist.

---

## 1. Bảng tổng hợp: Chức năng ↔ API

| # | Chức năng (theo màn hình PO) | Method | Endpoint | Trạng thái cho phép |
|---|---|---|---|---|
| 1 | **Dashboard** — Danh sách tờ khai (lọc năm/trạng thái, phân trang) | GET | `/` | — |
| 2 | **Dashboard** — Tạo tờ khai mới (chọn kỳ, ngành nghề, phụ lục) | POST | `/` | — |
| 3 | **Main** — Xem chi tiết tờ khai (chỉ tiêu + header) | GET | `/{decl_id}` | — |
| 4 | **Main** — Lưu nháp (sửa ô Nhập, server tính lại ô Tính) | PUT | `/{decl_id}` | chỉ `Draft` |
| 5 | **Invoice List** — Đồng bộ lại từ hóa đơn gốc | POST | `/{decl_id}/sync` | chỉ `Draft` |
| 6 | **Dashboard** — Xóa tờ khai | DELETE | `/{decl_id}` | chỉ `Draft` |
| 7 | **Dashboard/Main** — Khóa số liệu (Draft → Confirmed) | PUT | `/{decl_id}/lock` | chỉ `Draft`, kỳ K-1 đã khóa |
| 8 | **Dashboard** — Mở khóa (Confirmed → Draft) | DELETE | `/{decl_id}/lock` | chỉ `Confirmed`, kỳ K+1 chưa khóa |
| 9 | **Invoice List** — Modal hóa đơn phụ lục BKBR/BKMV (1 dòng/hóa đơn/thuế suất, search, phân trang) | GET | `/{decl_id}/annex/invoices` | — |
| 10 | **VAT Reduction** — Thêm dòng phụ lục nhập tay | POST | `/{decl_id}/annex/rows` | chỉ `Draft` |
| 11 | **VAT Reduction** — Xóa dòng phụ lục | DELETE | `/{decl_id}/annex/rows/{annex_seq_no}` | chỉ `Draft` |
| 12 | **Main** — Kết xuất XML (chuẩn HTKK 01/GTGT TT80) | GET | `/{decl_id}/export/xml` | `Confirmed` (OQ-VAT-7) |
| 13 | **Invoice List** — Xuất Excel danh sách hóa đơn phụ lục | POST | `/{decl_id}/annex/export` | — |
| 14 | **Main** — In PDF mẫu tờ khai (A4) | GET | `/{decl_id}/export/pdf` | — (chờ OQ-VAT-8) |

> Lock/Unlock dùng cặp **PUT + DELETE trên sub-resource `/lock`** theo convention "idempotent toggle" (giống idiom `.../confirm`).

---

## 2. Chi tiết endpoint

### 2.1 `GET /` — Danh sách tờ khai

**Query params:**

| Param | Kiểu | Bắt buộc | Ghi chú |
|---|---|:---:|---|
| `year` | int | — | Lọc năm kê khai; bỏ trống = tất cả |
| `status` | string | — | `D` (Draft) / `C` (Confirmed) |
| `page` / `size` | int | — | 0-based / default 20 |

**Response `data` — `VatDeclarationListResponse`:**

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "rows": [
      {
        "decl_id": 12,
        "period_type": "Q",
        "period_value": 2,
        "decl_year": 2026,
        "period_label": "Q2/2026",
        "stts": "D",
        "tax_amount": "15000000",
        "annex_8pct_yn": "Y",
        "last_sync_dtm": "2026-07-10T08:30:00Z",
        "lock_dtm": null,
        "mod_dtm": "2026-07-12T09:00:00Z"
      }
    ],
    "total_count": 8
  }
}
```

`tax_amount` = `CT40` (cột "Số tiền nộp" trên Dashboard). Amount đi wire dạng **string** (đồng bộ pattern e-Invoice: `totl_amt`).

---

### 2.2 `POST /` — Tạo tờ khai

**Request — `VatDeclarationCreateRequest`:**

```json
{
  "period_type": "Q",
  "period_value": 2,
  "decl_year": 2026,
  "bsns_line_cd": "00",
  "annex_8pct_yn": "Y"
}
```

| Field | Validation |
|---|---|
| `period_type` | `@NotBlank`, `M`/`Q` |
| `period_value` | `@NotNull`, 1–12 (M) / 1–4 (Q) |
| `decl_year` | `@NotNull`, 4 chữ số |
| `bsns_line_cd` | **Bắt buộc** — "Ngành nghề kinh doanh *" trên form PO (Dictionary `WEB_VAT_CRE_006`). Kiểm tra ở service, **không** dùng `@NotBlank`: PO có message riêng cho field này (`WEB_VAT_CRE_MSG_004`) nên throw `WBE_VAT_DECL_BSNS_LINE_REQUIRED` thay vì để bean validation gộp vào `WBE_VALIDATION` chung. Guard chạy trước cả dup-check. |
| `annex_8pct_yn` | default `Y` |

**Luồng xử lý:** dup-check (UK kỳ) → check kỳ K-1 đã khóa → aggregate hóa đơn (`BUSA_DOC_MSTR` + `EINV_TAX_DETL`) → tính chỉ tiêu (`VatIndicatorCalculator`) → carry `CT43`(K-1)→`CT22` → persist header + chỉ tiêu + dòng phụ lục → trả detail (như 2.3).

**Lỗi:** `WBE_VAT_DECL_BSNS_LINE_REQUIRED` · `WBE_VAT_DECL_DUPLICATE_PERIOD` · `WBE_VAT_DECL_PREV_NOT_LOCKED` · `WBE_VALIDATION`.

---

### 2.3 `GET /{decl_id}` — Chi tiết tờ khai

**Response `data` — `VatDeclarationDetail`:**

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "decl_id": 12,
    "period_type": "Q",
    "period_value": 2,
    "decl_year": 2026,
    "stts": "D",
    "cmpn_nm": "CÔNG TY TNHH WEBCASH VIỆT NAM",
    "tax_id": "0301234567",
    "annex_8pct_yn": "Y",
    "revision_no": 0,
    "last_sync_dtm": "2026-07-10T08:30:00Z",
    "indicators": {
      "bsns_line_cd": "00",
      "bsns_line_nm": "Hoạt động sản xuất kinh doanh thông thường",
      "acct_sub_item_cd": "1701",
      "ct21": "0", "ct22": "5000000", "ct23": "800000000", "ct24": "80000000",
      "ct25": "80000000", "ct26": "0", "ct27": "1200000000", "ct28": "115000000",
      "ct29": "0", "ct30": "100000000", "ct31": "5000000",
      "ct32": "1100000000", "ct32a": "0", "ct33": "110000000", "ct33a": "0",
      "ct34": "1200000000", "ct35": "115000000", "ct36": "35000000",
      "ct37": "0", "ct38": "0", "ct39a": "0",
      "ct40a": "30000000", "ct40b": "0", "ct40": "30000000",
      "ct41": "0", "ct42": "0", "ct43": "0",
      "signatory_nm": "NGUYỄN VĂN A",
      "signing_dt": "20260715",
      "agent_nm": null, "agent_cert_no": null, "agent_tax_id": null,
      "province": "TP. Hồ Chí Minh", "district": null, "ward": null
    },
    "annex_summary": {
      "row_count": 42,
      "total_sply_amt": "550000000",
      "total_reduced_amt": "11000000"
    }
  }
}
```

Chỉ tiêu đi wire dạng **string** (VND nguyên, không thập phân). `annex_summary.total_reduced_amt` = "TỔNG CỘNG THUẾ ĐƯỢC GIẢM KỲ NÀY".

**Lỗi:** `WBE_VAT_DECL_NOT_FOUND`.

---

### 2.4 `PUT /{decl_id}` — Lưu nháp

**Request — `VatDeclarationUpdateRequest`:** chỉ chứa **ô Nhập** (white cells) + khối chữ ký:

```json
{
  "ct21": "0", "ct23": "800000000", "ct24": "80000000",
  "ct26": "0", "ct29": "0", "ct30": "100000000",
  "ct32": "1100000000", "ct32a": "0", "ct33a": "0",
  "ct37": "0", "ct38": "0", "ct39a": "0", "ct40b": "0", "ct42": "0",
  "signatory_nm": "NGUYỄN VĂN A",
  "signing_dt": "20260715",
  "agent_nm": null, "agent_cert_no": null, "agent_tax_id": null,
  "province": "TP. Hồ Chí Minh", "district": null, "ward": null
}
```

Server **bỏ qua mọi ô Tính nếu client lỡ gửi** — tính lại toàn bộ qua `VatIndicatorCalculator` rồi persist + trả detail mới (như 2.3). Mọi chỉ tiêu phải ≥ 0 và là số nguyên.

**Lỗi:** `WBE_VAT_DECL_NOT_FOUND` · `WBE_VAT_DECL_LOCKED` (đã Confirmed) · `WBE_VAT_DECL_INVALID_INDICATOR` · `WBE_VALIDATION` (signing_dt sai format `YYYYMMDD`, agent_tax_id không phải 10/13 số).

---

### 2.5 `POST /{decl_id}/sync` — Đồng bộ lại hóa đơn

Không có request body. Re-scan `BUSA_DOC_MSTR` + `EINV_TAX_DETL` theo kỳ → ghi đè các chỉ tiêu nguồn-hóa-đơn + regenerate dòng phụ lục **không phải nhập tay** (`HAND_REG_YN='N'`); dòng `'Y'` giữ nguyên. Cập nhật `LAST_SYNC_DTM`. Trả detail mới.

**Lỗi:** `WBE_VAT_DECL_NOT_FOUND` · `WBE_VAT_DECL_LOCKED`.

---

### 2.6 `DELETE /{decl_id}` — Xóa tờ khai

Hard delete (cascade sang chỉ tiêu + phụ lục). Chỉ `Draft`; đề xuất chặn khi tồn tại kỳ sau (bảo vệ chuỗi carry-forward — OQ-VAT-6).

**Response:** `ApiResponse<Void>` — `{ "status": {...}, "data": null }`.
**Lỗi:** `WBE_VAT_DECL_NOT_FOUND` · `WBE_VAT_DECL_LOCKED`.

---

### 2.7 `PUT /{decl_id}/lock` — Khóa số liệu · 2.8 `DELETE /{decl_id}/lock` — Mở khóa

| | PUT (Lock) | DELETE (Unlock) |
|---|---|---|
| Chuyển trạng thái | `D` → `C` | `C` → `D` |
| Guard | Kỳ K-1 phải `C` (hoặc K là kỳ đầu tiên); `signing_dt` phải có | Kỳ K+1 phải `D` hoặc chưa tồn tại |
| Lỗi | `WBE_VAT_DECL_PREV_NOT_LOCKED` · `WBE_VALIDATION` | `WBE_VAT_DECL_NEXT_LOCKED` |
| Side effect | Set `LOCK_DTM` + `LOCK_USER_ID` | Clear `LOCK_DTM` + `LOCK_USER_ID` |

Cả 2 chạy trong **một `@Transactional` + optimistic guard** để 2 user không race lock/unlock. Trả detail mới.

---

### 2.9 `GET /{decl_id}/annex/invoices` — Modal hóa đơn phụ lục (BKBR/BKMV)

**Query params:** `search` (tên hàng / số HĐ, partial match) · `io_dv` (`I`/`O`) · `page` / `size` (default 20 — "Hiển thị 20 dòng trên mỗi trang") · `annex_type` (`BKBR` / `BKMV` / `REDUCE8`).

**Grain (cập nhật 2026-08-22):** **mỗi hóa đơn 1 dòng cho mỗi loại thuế suất** — các dòng sản phẩm cùng thuế suất trong một hóa đơn được gộp lại (`sply_amt` / `vat_amt` / `reduced_amt` = tổng các dòng con). Một hóa đơn có 2 thuế suất xuất hiện 2 dòng, mỗi dòng ở section thuế suất tương ứng. (Trước đây: 1 dòng cho mỗi dòng sản phẩm; accordion nhóm theo `prod_nm` đã bỏ — client tự nhóm theo `prod_nm` nếu cần.)

**Tax Code (`trco_tax_code`):** MST **người mua** (hóa đơn bán ra) / người bán (hóa đơn mua vào) — tương ứng `trco_nm`. Lấy từ contact `TRCO_INFM` qua `trco_key` (JOIN — đúng cả dữ liệu cũ), fallback `BUSA_DOC_MSTR.TRCO_TAX_ID` chỉ khi khác `ISSU_TAX_ID`; **không hiển thị MST của chính mình** → để trống nếu không truy được (HĐ orphan không có contact). Xem README §3.4.

**VAT (`vat_amt`) — cập nhật 2026-08-22:** với **hóa đơn bán ra**, `vat_amt` = **VAT thật đã phát hành** = `SUM(EINV_TAX_DETL.TAX_AMT)` theo (hóa đơn, thuế suất) — KHÔNG phải `floor(sply × rate%)` tính lại từng dòng. Lý do: floor từng dòng lệch vài đồng làm tròn so với VAT thật của hóa đơn, khiến "Total Output (Sales)" ≠ số thuế trên Sales e-Invoice (verified Q1/2026: floor 57.656.833 vs e-Invoice 57.656.821). `tax_groups` subtotal + `grand_total` cũng cộng từ VAT thật này nên khớp e-Invoice tới đồng. Hóa đơn **mua vào** giữ `floor(sply × rate%)`. Base (`sply_amt`) vẫn từ `BUSA_DOC_DETL`.

**Response `data` — `AnnexInvoiceListResponse` (flat rows + section footer):**

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "rows": [
      {
        "id": null,
        "annex_type": "BKBR",
        "io_dv": "O",
        "src_doc_id": "I-2026-000123",
        "inv_no": "1C26TWV-91",
        "inv_symbol": "1C26TWV",
        "inv_dt": "20260302",
        "trco_tax_code": "0106713191",
        "trco_nm": "CÔNG TY TNHH FI GROUP",
        "prod_nm": "Dịch vụ A, Dịch vụ B",
        "sply_amt": "23148148",
        "vat_rate": "8%",
        "vat_amt": "1851851",
        "reduced_amt": "462962",
        "alloc_ratio": null,
        "hand_reg_yn": "N",
        "vat_decl_proc_yn": "N",
        "vat_decl_sid": null,
        "vat_rate_order": 1
      }
    ],
    "total_count": 42,
    "total_pages": 3,
    "page_number": 0,
    "page_size": 20,
    "tax_groups": [
      { "group_cd": "8%", "tax_rate_label": "8%", "row_count": 12,
        "total_sply_amt": "45528914", "total_reduced_amt": "910578", "total_vat_amt": "3642312" }
    ],
    "grand_total": { "group_cd": "TOTAL", "tax_rate_label": null, "row_count": 42,
        "total_sply_amt": "8409366257", "total_reduced_amt": "11000000", "total_vat_amt": "3663554" }
  }
}
```

- **Thứ tự:** theo section thuế suất (KKKNT/0%/5%/8%/10%/KCT/khác) → ngày lập → **số hóa đơn dạng số** (`hand_doc_id` ép kiểu số, không phải chuỗi `doc_id` để tránh 1·10·2·3). Fixed 2026-08-22.
- `total_count` = số **hóa đơn** (distinct `doc_id`) trên toàn bộ tập lọc = tổng số dòng gộp; phân trang theo dòng gộp.
- `tax_groups` = footer subtotal theo từng thuế suất (thứ tự cố định KKKNT / 0% / 5% / 8% / 10% / KCT, khác cuối); `grand_total.group_cd = "TOTAL"`. Section subtotals + rows luôn reconcile tới đồng (VAT = Σ floor mỗi dòng).

---

### 2.10 `POST /{decl_id}/annex/rows` — Thêm dòng phụ lục · 2.11 `DELETE .../rows/{annex_seq_no}` — Xóa dòng

**Request 2.10 — `AnnexRowCreateRequest`:**

```json
{
  "io_dv": "O",
  "inv_no": "00000456",
  "inv_symbol": "1C26TLM",
  "inv_dt": "20260420",
  "prod_nm": "Thiết bị văn phòng",
  "sply_amt": "10000000",
  "vat_rate": "8",
  "vat_amt": "800000",
  "reduced_amt": "200000"
}
```

Server gán `annex_seq_no` (MAX+1), set `HAND_REG_YN='Y'`, `SRC_DOC_ID=null`. DELETE xóa 1 dòng theo `annex_seq_no`. Cả hai chỉ cho `Draft` và trả `annex_summary` mới.

**Lỗi:** `WBE_VAT_DECL_NOT_FOUND` · `WBE_VAT_DECL_LOCKED` · `WBE_VAT_DECL_ANNEX_ROW_NOT_FOUND` (2.11) · `WBE_VALIDATION`.

---

### 2.12 `GET /{decl_id}/export/xml` — Kết xuất XML HTKK

**Response:** HTTP 200, `Content-Type: application/xml`, `Content-Disposition: attachment; filename*=UTF-8''<file>.xml`.

- Tên file: `{MST-pad-13}-01_GTGT_TT80-{Q2|M05}{YYYY}-L{RR}.xml` (VD: `0301234567000-01_GTGT_TT80-Q22026-L00.xml`).
- Nội dung: chỉ tiêu đã persist → `<ct21>..<ct43>` trong `<CTieuTKhaiChinh>`, các node nhóm khớp verbatim template mẫu trong PO package; **không** có block chữ ký số `CKyDTu` (Phase 2).
- MVP: chỉ cho `Confirmed` (OQ-VAT-7) → `Draft` trả `WBE_VAT_DECL_LOCKED`... **không** — Draft chưa khóa thì trả lỗi riêng `WBE_VAT_DECL_NOT_LOCKED` (thêm code nếu PO chốt Confirmed-only).

**Frontend contract:** tiêu thụ qua blob handler (`lib/file.ts`), **không** đi qua `unwrap()`.

---

### 2.13 `POST /{decl_id}/annex/export` — Xuất Excel phụ lục

Đi qua pipeline chung `common/export`: body là `ExcelExportRequest` (`screen_id = "addon-vat-declaration-annex"`, `dym_area_id = "default"`, `columns[]`, `filters {io_dv, search}`). Provider mới `VatAnnexExportProvider implements ExportRowProvider` — dùng lại query của 2.9 (một đường dữ liệu cho list + export). Cần seed `SYS_SCR_OPTION_WECMS` trong cùng migration.

**Response:** HTTP 200, XLSX MIME, RFC 5987 filename.
**Lỗi:** `WBE2010` (screenId lạ) · `WBE2011` (>50k dòng) · `WBE2013` (không còn cột sau intersect View Setting).

---

### 2.14 `GET /{decl_id}/export/pdf` — In PDF mẫu tờ khai

**Chưa commit sprint** — chặn bởi OQ-VAT-8 (chưa có thư viện PDF trong `build.gradle`; cần chọn OpenPDF / flying-saucer + review license). Contract dự kiến: HTTP 200, `application/pdf`, layout A4 theo Mẫu 01/GTGT.

---

## 3. DTO đề xuất (records, camelCase → snake_case tự động)

| DTO | Vai trò |
|---|---|
| `VatDeclarationRow` | 1 dòng Dashboard (2.1) |
| `VatDeclarationListResponse` | `{ rows, total_count }` |
| `VatDeclarationCreateRequest` | Body 2.2 |
| `VatDeclarationUpdateRequest` | Body 2.4 — chỉ ô Nhập + chữ ký |
| `VatDeclarationDetail` | Response 2.2/2.3/2.4/2.5/2.7/2.8 |
| `VatIndicatorSet` | Khối `indicators` lồng trong Detail |
| `AnnexSummary` | Khối `annex_summary` |
| `AnnexRow` / `AnnexTaxGroup` | Modal 2.9 (1 dòng/hóa đơn/thuế suất; `AnnexTaxGroup` = subtotal theo thuế suất + dòng TOTAL) |
| `AnnexInvoiceListResponse` | `{ rows, total_count, total_pages, page_number, page_size, tax_groups, grand_total }` |
| `AnnexRowCreateRequest` | Body 2.10 |

## 4. Error codes mới (thêm vào `messages.properties` trước khi throw)

| Code | Ý nghĩa | Endpoint |
|---|---|---|
| `WBE_VAT_DECL_NOT_FOUND` | Không tìm thấy tờ khai trong tenant (gộp not-found + wrong-tenant) | 2.3–2.14 |
| `WBE_VAT_DECL_DUPLICATE_PERIOD` | Kỳ kê khai đã tồn tại (`WEB_VAT_CRE_MSG_001`) | 2.2 |
| `WBE_VAT_DECL_PREV_NOT_LOCKED` | Kỳ trước chưa khóa số liệu | 2.2, 2.7 |
| `WBE_VAT_DECL_NEXT_LOCKED` | Kỳ sau đã khóa — phải mở khóa kỳ sau trước | 2.8 |
| `WBE_VAT_DECL_LOCKED` | Tờ khai đã khóa — không thể sửa/đồng bộ/xóa | 2.4, 2.5, 2.6, 2.10, 2.11 |
| `WBE_VAT_DECL_NOT_LOCKED` | Tờ khai chưa khóa — chưa được kết xuất XML (nếu PO chốt Confirmed-only, OQ-VAT-7) | 2.12 |
| `WBE_VAT_DECL_INVALID_INDICATOR` | Giá trị chỉ tiêu âm / không phải số nguyên (`WEB_VAT_MAIN_MSG_004`) | 2.4 |
| `WBE_VAT_DECL_ANNEX_ROW_NOT_FOUND` | Không tìm thấy dòng phụ lục | 2.11 |
| `WBE_VAT_DECL_BSNS_LINE_REQUIRED` | Thiếu ngành nghề kinh doanh (`WEB_VAT_CRE_MSG_004`) — thêm 2026-08-04 | 2.2 |
| `WBE_VAT_DECL_PERIOD_TYPE_MISMATCH` | Trong cùng một năm dương lịch đã có tờ khai theo loại kỳ khác (tháng ↔ quý). Căn cứ Điều 9 NĐ 126/2020 — kỳ khai ổn định theo năm; đổi ở ranh giới năm vẫn cho phép, và `[22]` được bắc cầu từ `[43]` của kỳ cuối năm trước. Thêm 2026-08-12, từ QA S-05/S-06 | 2.2 |
| `WBE_VAT_DECL_PERIOD_NOT_ENDED` | Kỳ tính thuế chưa kết thúc — chặn **khóa**, không chặn tạo (tạo sớm để nhập dần vẫn được; nháp thì sửa/xóa được nên không kẹt chỗ). Thêm 2026-08-12, từ QA S-23 | 2.7 |
| `WBE_VAT_DECL_NO_ACTIVITY_CONFLICT` | `[21]` đánh dấu không phát sinh nhưng `[34]`/`[23]` vẫn có số — chặn ở bước khóa, không chặn khi nhập (thêm 2026-08-12, từ QA S-19) | 2.7 |

Message hiển thị (VI/EN) lấy theo bảng `Dictionary` trong file Requirement-Design của PO (`WEB_VAT_*_MSG_*`).

> **Bảng này chưa đủ.** Code hiện throw thêm 4 mã chưa có ở đây — `WBE_VAT_DECL_PREV_MISSING`, `WBE_VAT_DECL_PREV_CREDIT_MISMATCH`, `WBE_VAT_DECL_LATER_PERIOD_EXISTS`, `WBE_VAT_DECL_EXPORT_FAILED` — và `WBE_VAT_DECL_ANNEX_ROW_NOT_FOUND` không còn được throw ở đâu (2 endpoint ở §2.10/§2.11 đã bị bỏ). Chi tiết + đề xuất xử lý: `qa/reports/vat-declaration-doc-gap.md` *(referenced `qa/` tree does not exist in this repository)* §G-05 và §G-01.

## 5. Việc kèm theo khi implement (checklist)

- [ ] Seed `MENU_ITEM` + `PRODUCT_MENU` (F/M; H bỏ qua theo precedent V243) + `MENU_ITEM_TRANSLATION` cho node `addon-vat-declaration` (OQ-VAT-5)
- [ ] Seed `SYS_SCR_OPTION_WECMS` cho `addon-vat-declaration-annex` (gate của 2.13 — thiếu seed thì `WBE2013`)
- [ ] Thêm 8 error code vào `messages.properties` (+ `_en`), message VI/EN từ PO Dictionary
- [ ] Flyway cho 3 bảng mới — **chặn bởi OQ-VAT-1** (duyệt scope Active Table List)
- [ ] Golden-file test XML với file mẫu trong PO package; unit test `VatIndicatorCalculator` (floor/integer math)
- [ ] Frontend wire types trong `wecms-web/lib/types/api.ts` cập nhật cùng MR (full-stack ownership rule)

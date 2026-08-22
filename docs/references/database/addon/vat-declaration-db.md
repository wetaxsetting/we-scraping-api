# VAT Declaration — Data Dictionary (Từ điển dữ liệu)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../flyway-migration-baseline.md) §5.

| | |
|---|---|
| **Trạng thái** | 🟡 Design — các bảng mới ở mức **đề xuất** (chưa có Flyway migration; chờ duyệt OQ-VAT-1) |
| **Feature spec** | [features/addon-service/vat-declaration/README.md](../../../features/addon-service/vat-declaration/README.md) |
| **Tài liệu gốc PO** | [source-requirements/](../../../features/addon-service/vat-declaration/source-requirements/overview.md) |
| **Open questions** | [vat-declaration-follow-up.md](../../../planning/open-questions/vat-declaration-follow-up.md) |
| **Cập nhật** | 2026-07-13 |

Gồm **3 bảng mới** (refactor-owned, không có bảng JEX tương ứng, chưa thuộc PROD Active Table
List) + các bảng nguồn chỉ-đọc. DDL chi tiết xem feature spec §3; file này là từ điển trường.

Ký hiệu cột **Ô** (loại ô trên form, theo Business Rules doc 3 — mô phỏng HTKK): **Nhập** = ô trắng, người dùng nhập tay · **Tính** = ô xám, backend tự tính theo công thức, chỉ đọc · **Nhập/Tính** = tự tính nhưng cho phép ghi đè tay.

---

## 1. `VAT_DECL_INFM` — Header tờ khai (vòng đời, trạng thái)

Mỗi dòng = 1 tờ khai của 1 kỳ tính thuế. Bảng "chủ" của state machine Draft → Confirmed.

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `BIZ_KEY` | VARCHAR(20) | ✅ PK | Mã công ty (tenant) — mọi query đều lọc theo trường này |
| `DECL_ID` | BIGINT IDENTITY | ✅ PK | Khóa tự tăng của tờ khai |
| `PERIOD_TYPE` | VARCHAR(1) | ✅ | Loại kỳ: `'M'` = tháng, `'Q'` = quý |
| `PERIOD_VALUE` | INTEGER | ✅ | Giá trị kỳ: 1–12 (tháng) hoặc 1–4 (quý) |
| `DECL_YEAR` | INTEGER | ✅ | Năm kê khai (VD: 2026) |
| `STTS` | VARCHAR(1) | ✅ | Trạng thái: `'D'` = Draft (Dự thảo), `'C'` = Confirmed (Đã khóa) |
| `CMPN_NM` | VARCHAR(200) | ✅ | Tên công ty — **snapshot** từ `BUSI_INFM` lúc tạo (sửa thông tin cty sau này không làm đổi tờ khai đã nộp) |
| `TAX_ID` | VARCHAR(40) | ✅ | Mã số thuế — snapshot, dùng sinh tên file XML (pad 13 số) |
| `ANNEX_8PCT_YN` | VARCHAR(1) | ✅ | `'Y'/'N'` — có đính kèm Phụ lục giảm 8% (NQ 204/2025) |
| `REVISION_NO` | INTEGER | ✅ | Số lần khai: `0` = chính thức (L00), `1+` = bổ sung (L01…) — MVP chỉ dùng 0 (OQ-VAT-2) |
| `LAST_SYNC_DTM` | TIMESTAMPTZ | | Thời điểm đồng bộ hóa đơn lần cuối |
| `LOCK_DTM` | TIMESTAMPTZ | | Thời điểm khóa số liệu |
| `LOCK_USER_ID` | VARCHAR(100) | | Ai khóa |
| `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` | | ✅ | 4 cột audit chuẩn WeCMS |

**Ràng buộc:**
- PK `(BIZ_KEY, DECL_ID)`
- **UNIQUE** `(BIZ_KEY, PERIOD_TYPE, PERIOD_VALUE, DECL_YEAR, REVISION_NO)` → chặn trùng kỳ ngay tại DB (US-01, `WEB_VAT_CRE_MSG_001`)
- CHECK `PERIOD_TYPE IN ('M','Q')`, `STTS IN ('D','C')`
- Index `(BIZ_KEY, DECL_YEAR, PERIOD_TYPE, PERIOD_VALUE)` phục vụ list Dashboard

> 🆕 **Chờ PO chốt (OQ-VAT-9):** file Requirement-Design của PO (`WEB_VAT_CRE_011/012`) có thêm
> 2 checkbox phụ lục trên form tạo mới — **Bảng kê bán ra (01-1/GTGT)** và **Bảng kê mua vào
> (01-2/GTGT)** — không có trong PRD/TechSpecs. Nếu thuộc scope: thêm `ANNEX_SALE_YN` /
> `ANNEX_PUR_YN` vào bảng này, hoặc tổng quát hóa bằng cột `ANNEX_TYPE` ở bảng phụ lục.

---

## 2. `VAT_DECL_INDC_DETL` — Giá trị chỉ tiêu (1 dòng / 1 tờ khai)

Chứa toàn bộ chỉ tiêu `[21]→[43]` của Mẫu 01/GTGT (TT80) + khối chữ ký/đại lý.
**Mọi chỉ tiêu là BIGINT (VND nguyên)** — tính bằng số nguyên + `floor`, cấm float, để XML khớp HTKK 100%.

| Trường | Kiểu | Ô | Mô tả |
|---|---|:---:|---|
| `BIZ_KEY` + `DECL_ID` | | PK/FK | Trỏ về `VAT_DECL_INFM`, `ON DELETE CASCADE` |
| `BSNS_LINE_CD` | VARCHAR(10) | | Mã ngành nghề (`ma_nganhnghe`, mặc định `'00'`) — **field "Ngành nghề kinh doanh \*" bắt buộc trên form tạo mới của PO** (`WEB_VAT_CRE_006`); cần danh mục cho dropdown |
| `BSNS_LINE_NM` | VARCHAR(255) | | Tên ngành nghề (mặc định "Hoạt động sản xuất kinh doanh thông thường") |
| `ACCT_SUB_ITEM_CD` | VARCHAR(10) | | Tiểu mục hạch toán (`tieumuchachtoan`, mặc định `'1701'`) |
| `CT21` | BIGINT | Nhập | Không phát sinh mua/bán trong kỳ (checkbox 0/1) |
| `CT22` | BIGINT | Tính | Thuế GTGT còn khấu trừ kỳ trước chuyển sang = `CT43` kỳ K-1 đã khóa (kỳ đầu tiên = 0) |
| `CT23` | BIGINT | Nhập | Giá trị HHDV **mua vào** trong kỳ |
| `CT24` | BIGINT | Nhập/Tính | Thuế GTGT mua vào — auto `floor(CT23 × 10%)` khi sửa CT23, cho phép ghi đè tay |
| `CT25` | BIGINT | Tính | Thuế GTGT mua vào **được khấu trừ** = `CT24` |
| `CT26` | BIGINT | Nhập | Doanh thu HHDV bán ra **không chịu thuế** |
| `CT27` | BIGINT | Tính | Tổng DT bán ra chịu thuế = `CT29 + CT30 + CT32 + CT32A` |
| `CT28` | BIGINT | Tính | Tổng thuế GTGT bán ra = `CT31 + CT33 + CT33A` |
| `CT29` | BIGINT | Nhập | Doanh thu chịu thuế suất **0%** |
| `CT30` | BIGINT | Nhập | Doanh thu chịu thuế suất **5%** |
| `CT31` | BIGINT | Tính | Thuế GTGT 5% = `floor(CT30 × 5%)` |
| `CT32` | BIGINT | Nhập | Doanh thu chịu thuế suất **10%** (gồm cả 8% giảm) |
| `CT32A` | BIGINT | Nhập | Doanh thu không phải kê khai, nộp thuế |
| `CT33` | BIGINT | Tính | Thuế GTGT 10%/8% = `floor(CT32 × 10%)` |
| `CT33A` | BIGINT | Nhập | Thuế của CT32A — ⚠️ **prototype DDL thiếu cột này** dù công thức `[28]` cần → đã bổ sung |
| `CT34` | BIGINT | Tính | Tổng doanh thu bán ra = `CT26 + CT27` |
| `CT35` | BIGINT | Tính | Tổng thuế GTGT bán ra = `CT28` |
| `CT36` | BIGINT | Tính | Chênh lệch = `CT35 − CT25` |
| `CT37` | BIGINT | Nhập | Điều chỉnh **giảm** thuế khấu trừ các kỳ trước (từ KHBS) |
| `CT38` | BIGINT | Nhập | Điều chỉnh **tăng** thuế khấu trừ các kỳ trước (từ KHBS) |
| `CT39A` | BIGINT | Nhập | Thuế GTGT nhận bàn giao/chuyển giao dự án |
| `CT40A` | BIGINT | Tính | Thuế phát sinh phải nộp: `hieu_so > 0 ? hieu_so : 0`, với `hieu_so = CT36 − CT22 + CT37 − CT38 − CT39A` |
| `CT40B` | BIGINT | Nhập | Thuế đã nộp ở địa phương khác (vãng lai) |
| `CT40` | BIGINT | Tính | **Thuế còn phải nộp** = `max(0, CT40A − CT40B)` — chính là cột "Số tiền nộp" trên Dashboard |
| `CT41` | BIGINT | Tính | Thuế chưa khấu trừ hết: `hieu_so < 0 ? \|hieu_so\| : 0` |
| `CT42` | BIGINT | Nhập | Thuế đề nghị hoàn |
| `CT43` | BIGINT | Tính | **Thuế còn khấu trừ chuyển kỳ sau** = `max(0, CT41 − CT42)` → thành `CT22` kỳ K+1 |
| `SIGNATORY_NM` | VARCHAR(100) | | Người ký (≤ 100 ký tự — Business Rules §3) |
| `SIGNING_DT` | VARCHAR(8) | | Ngày ký `YYYYMMDD` — **bắt buộc khi khóa số liệu** |
| `AGENT_NM` | VARCHAR(255) | | Tên đại lý thuế |
| `AGENT_CERT_NO` | VARCHAR(100) | | Số chứng chỉ hành nghề đại lý |
| `AGENT_TAX_ID` | VARCHAR(13) | | MST đại lý — 10 hoặc 13 số khi có nhập |
| `PROVINCE` / `DISTRICT` / `WARD` | VARCHAR(100) | | Địa bàn kê khai (tỉnh / huyện / xã) |
| `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` | | | 4 cột audit chuẩn WeCMS |

**Ràng buộc:** PK `(BIZ_KEY, DECL_ID)`; FK → `VAT_DECL_INFM` cascade; mọi chỉ tiêu `>= 0`
(vi phạm → `WBE_VAT_DECL_INVALID_INDICATOR`).

---

## 3. `VAT_DECL_ANNEX_DETL` — Dòng hóa đơn Phụ lục giảm 8%

Mỗi dòng = 1 dòng hóa đơn thuộc diện giảm 2% thuế GTGT (NQ 204/2025/QH15).
**Prototype hoàn toàn không có bảng này**, nhưng nghiệp vụ thêm/xóa dòng trên grid, modal
accordion và import Excel bắt buộc phải persist.

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `BIZ_KEY` + `DECL_ID` | | ✅ PK/FK | Thuộc tờ khai nào — `ON DELETE CASCADE` |
| `ANNEX_SEQ_NO` | INTEGER | ✅ PK | Số thứ tự dòng trong tờ khai (Java MAX+1) |
| `IO_DV` | VARCHAR(1) | ✅ | `'I'` = mua vào / `'O'` = bán ra |
| `SRC_DOC_ID` | VARCHAR(50) | | Trỏ về `BUSA_DOC_MSTR.DOC_ID` (dòng sinh từ đồng bộ); **NULL = dòng nhập tay** |
| `INV_NO` | VARCHAR(50) | | Số hóa đơn ("HĐ số") |
| `INV_SYMBOL` | VARCHAR(20) | | Ký hiệu hóa đơn |
| `INV_DT` | VARCHAR(8) | | Ngày lập `YYYYMMDD` |
| `PROD_NM` | VARCHAR(300) | | Tên hàng hóa/dịch vụ — **khóa nhóm** cho accordion modal (`WEB_VAT_INV_002`) |
| `SPLY_AMT` | BIGINT | ✅ | Doanh thu / trị giá **chưa thuế** |
| `VAT_RATE` | NUMERIC(4,2) | ✅ | Thuế suất hiệu lực (8) |
| `VAT_AMT` | BIGINT | ✅ | Tiền thuế theo suất giảm |
| `REDUCED_AMT` | BIGINT | ✅ | **Số thuế được giảm (2%)** — tổng cột = "TỔNG CỘNG THUẾ ĐƯỢC GIẢM KỲ NÀY" (`WEB_VAT_RED_007`) |
| `HAND_REG_YN` | VARCHAR(1) | ✅ | `'Y'` = dòng thêm tay — re-sync **không được** ghi đè/xóa |
| `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` | | ✅ | 4 cột audit chuẩn WeCMS |

**Ràng buộc:** PK `(BIZ_KEY, DECL_ID, ANNEX_SEQ_NO)`; CHECK `IO_DV IN ('I','O')`;
index `(BIZ_KEY, DECL_ID, PROD_NM)` phục vụ modal nhóm theo tên hàng.

> Nếu OQ-VAT-9 chốt thêm phụ lục 01-1/01-2 → tổng quát hóa bảng này bằng cột phân loại
> `ANNEX_TYPE` (`'8PCT'` / `'SALE'` / `'PUR'`) thay vì tạo thêm 2 bảng mới.

---

## 4. Bảng nguồn (đã có sẵn — chỉ ĐỌC, module không được ghi)

| Bảng | Trường dùng | Vai trò |
|---|---|---|
| `BUSA_DOC_MSTR` (V14) | `BIZ_KEY`, `DOC_ID`, `DOC_TYPE` (`'I'` bán ra / `'B'` mua vào — cùng mapping với `EInvoiceService.resolveDocType`), `ISSU_DT` (lọc kỳ, `YYYYMMDD`), `SPLY_AMT_SUM`, `TAX_AMT_SUM`, `TOTL_AMT`, `EINV_INVOICESTATUS` (lọc HĐ hiệu lực — phrase tiếng Việt, danh sách include = OQ-VAT-3), `EINV_INVOICESYMBOL`, `DISP_DOC_ID`, `TRCO_KEY` (liên kết contact), `TRCO_TAX_ID` (**MST đối tác** = người mua với HĐ bán ra / người bán với HĐ mua vào — cột "Tax Code" của BKBR/BKMV. Master column này KHÔNG tin cậy cho HĐ bán ra (list-scrape stamp nhầm MST mình) → annex JOIN `TRCO_INFM` qua `TRCO_KEY` để lấy MST đúng, xem README §3.4), `TRCO_CMNM` | Master hóa đơn điện tử — thay cho `tbl_sale_mst` / `tbl_pur_mst` (không tồn tại) của prototype |
| **`BUSA_DOC_DETL`** (V21) | `BIZ_KEY`, `DOC_ID`, `DISP_ORD`, `EINV_TAXRATE` (**string** — `'0%'`/`'5%'`/`'8%'`/`'10%'`/`'KCT'`/`'KKKNT'`, OQ-VAT-4 closed), `SPLY_AMT` (chưa thuế), `EINV_PRODUCTNAME`/`ITEM_NM` | **Nguồn BASE** (doanh thu/giá trị chưa thuế) → `CT23/26/29/30/32/32A`. ⚠️ **KHÔNG** dùng `EINV_TAX_DETL.AMT` cho base: = 0 cho mọi HĐ bán ra (verified DEV 2026-08-06). `TAX_AMT` của bảng này = 0 → VAT KHÔNG lấy từ đây |
| **`EINV_TAX_DETL`** (V15) | `BIZ_KEY`, `DOC_ID`, `EINV_TAXRATE`, `TAX_AMT` (VAT thật đã phát hành theo từng thuế suất) | **Nguồn OUTPUT VAT** cho HĐ bán ra: `CT31` (5%) / `CT33` (8%+10%) = `SUM(TAX_AMT)` theo rate (`salesVatByRate`) — **pin VAT thật**, không recompute `floor(base×rate)`. Khớp Sales e-Invoice list (sum `TAX_AMT_SUM`), xem README §3.4. Mua vào `CT24` KHÔNG dùng bảng này (không reconcile) |
| `BUSI_INFM` | `CMPN_NM`, MST | Snapshot vào `VAT_DECL_INFM` lúc tạo tờ khai |
| `VAT_DECL_INDC_DETL` (kỳ K-1) | `CT43` | Nguồn carry-forward → `CT22` kỳ hiện tại |

ERD liên quan: [addon-service-erd.md](../erd/addon-service-erd.md) · coverage:
[database-coverage-matrix.md](../database-coverage-matrix.md)

# Zalo OA — tiến độ setup app test

> Ghi lại các giá trị **không phải secret** đã lấy được, để không phải lục lại hội thoại.
> **App Secret KHÔNG ghi ở đây** — nó chỉ nằm trong `.env` (gitignored).
> Cập nhật: 2026-08-18
> **Hướng dẫn đầy đủ:** [../docs/features/settings/zalo-oa-app-setup.md](../docs/features/settings/zalo-oa-app-setup.md)

## Giá trị đã có

| Mục | Giá trị |
|---|---|
| App name | `WeCMS Test` |
| **App ID** | `30615687819892716` — đã điền vào `.env` |
| App Secret | ⬜ **chưa điền** vào `.env` → `Cài đặt` → icon con mắt ở "Khóa bí mật của ứng dụng" |
| Miền ứng dụng | `wecms.kosign.dev` |
| **OA** | `WABOOKS TA Test` |
| **OA ID** | `1910690480834847825` |
| OA trạng thái | ✅ `Tài khoản xác thực` · ⚠️ gói `Service Test` |
| Callback URL | `https://wecms.kosign.dev/authorized` |
| ZBS account | `Z4UJBZ946ZP4MMMPP` (dùng chung với WABOOKS/WeBill365) |
| **template_id** | ✅ **`625384`** — `TEST - Thông tin hệ thống 1`, Đã duyệt 2026-08-19 |
| Đơn giá | `300 đ/tin` qua SĐT · `210 đ/tin` qua UID → `unit_prc` = **300** |
| Tham số template | `customer_name` (string 30) · `count_invoice` (**number** 20) · `process_status` (string 20) |
| Ứng dụng gắn template | `WeCMS Test` — ✅ khớp app trong `.env`, không phải đi đường tắt |

## Đã xong phía Zalo

- ✅ Tạo app, kích hoạt (`Đang hoạt động`), upload icon
- ✅ `ZNS API` → nhóm `Gửi ZNS` **đã được duyệt**, quota không giới hạn
- ✅ `Official Account API` → đã duyệt sẵn
- ✅ `Xác thực domain` → `wecms.kosign.dev` (1/20). File verifier
  `wecms-web/public/zalo_verifierJEJW4AsQUpjSjzjijxrVJ36Us6lTw08pDJOr.html` live, HTTP 200
- ✅ `Thiết lập chung` → Callback URL đã khai
- ✅ OA cấp quyền cho app → `Danh sách OA đã cấp quyền (Tổng số: 1)`,
  có `Quyền: Gửi tin qua số điện thoại`

## Bước 6 — ✅ ĐÃ THÔNG (2026-08-18)

`WeCMS Test` (`30615687819892716`) **đã liên kết vào ZBS** `Z4UJBZ946ZP4MMMPP`.

Vị trí đã làm: `account.zalo.solutions/Z4UJBZ946ZP4MMMPP/setting/asset/app`
→ `Cài đặt` → `Quản lý tài sản` → tab **`LIÊN KẾT ỨNG DỤNG`** → khối `Liên kết ứng dụng mới`.

Hiện trong `Ứng dụng đã liên kết` cùng 4 app cũ (`TEST WeBill365 send Bill infomation`,
`WeBill365 send Bill infomation`, `WABOOKS TA`, `WABOOKS for Tax Accountant`).

**Yêu cầu quyền:** trang ghi *"cần có quyền **Quản trị viên của ứng dụng** đó"* — là admin của
**APP**, không phải của ZBS. Nên người tạo app tự làm được, không cần nhờ ai.
*(Giả thuyết trước đó "cần admin ZBS" là sai.)*

**Cũng phát hiện:** app thứ hai `WeCMS Test v2` (`417357358136370852`) đã được tạo nhưng
chưa hoàn thiện (chưa điền phone/email/domain, chưa xác thực domain, chưa liên kết ZBS).
Nếu không dùng thì bỏ qua để tránh nhầm — `WeCMS Test` là app đang hoạt động.

**Lưu ý mới:** xác thực domain là **per app**. `wecms.kosign.dev` xác thực xong cho `WeCMS Test`
nhưng `WeCMS Test v2` vẫn `0/20` + popup `Chưa được xác thực`. Zalo cho 3 cách xác thực:
DNS TXT record (có thể mất tới 72h) · tải file HTML (cách đã dùng) · thẻ meta.

## Bước tạo template — ✅ ĐÃ XONG (2026-08-19)

Bị từ chối **1 vòng** với 3 mã, sửa xong duyệt được ngay:

| Mã | Vấn đề | Đã sửa |
|---|---|---|
| `LG_9` | logo `WeBill365` trên OA tên `WABOOKS TA Test` | đổi sang logo `WABOOKS TA` |
| `CT_16` | tên thương hiệu làm biến `<company_name>` | xoá biến, gõ `WABOOKS` cố định |
| `CT_9` | mẫu test thiếu cụm `Nội dung test` | thêm vào nội dung |

Chi tiết + mẫu nội dung đã duyệt: [zalo-oa-app-setup.md §4.5](../docs/features/settings/zalo-oa-app-setup.md).

⚠️ `Khả năng phê duyệt: Cao` + `Khuyến nghị (0 lỗi)` **không** đảm bảo được duyệt — đã xảy ra thật.

## Còn lại
- ⬜ Điền `WECMS_ZALO_APP_SECRET` vào `.env`
- ⬜ Đổi `WECMS_ZALO_ENABLED=true` (chỉ khi sẵn sàng gửi tin thật — mỗi tin tốn tiền)
- ⬜ Bật VPN (DB dev `172.168.5.172` cần VPN), khởi động backend
- ⬜ Lấy `code` OAuth **mới** rồi chạy luồng bên dưới

## Lệnh chạy — đã điền sẵn OA ID

`code` OAuth **dùng một lần và hết hạn nhanh**, nên chỉ lấy code khi backend đã chạy sẵn.
Lấy code mới bằng cách mở lại URL cấp quyền (bạn là admin OA nên làm lại bao nhiêu lần cũng được):

```
https://oauth.zaloapp.com/v4/oa/permission?app_id=30615687819892716&redirect_uri=https%3A%2F%2Fwecms.kosign.dev%2Fauthorized
```

Route `/authorized` chưa tồn tại (trả 307) nên trang sẽ nhảy đi nhanh — mở **F12 → Network →
tích "Preserve log"** trước khi bấm `Cấp quyền`, rồi lấy full URL của request tới
`wecms.kosign.dev/authorized`.

```bash
cd wecms-api/tools

./zalo-setup.sh check
./zalo-setup.sh login <user-ADMIN> '<password>'

./zalo-setup.sh register 1910690480834847825 "WABOOKS TA Test"

# template khong can token (FK chi can hang OA) -> lam TRUOC khi di bat code,
# vi code OAuth chi song 10 phut va dung mot lan
./zalo-setup.sh template 1910690480834847825 625384 NOTICE VI 300

./zalo-setup.sh authorize 1910690480834847825 '<code-vua-lay>'
./zalo-setup.sh activate 1910690480834847825
./zalo-setup.sh status

# 3 khoa nay TRUNG KHIT bang tham so cua template 625384 — khong them, khong thieu.
# `send` KHONG nhan oa_id: send <phone> '<json>' [type] [lang]
./zalo-setup.sh send 84xxxxxxxxx '{"customer_name":"Tran Thi Hoa","count_invoice":"150","process_status":"Thanh cong"}' NOTICE VI
./zalo-setup.sh history
```

## Bẫy đã biết

- Số điện thoại phải E.164: `84xxxxxxxxx`, **không** phải `0xxxxxxxxx`
- Zalo từ chối vẫn trả **HTTP 200** kèm `success:false` — đọc `rslt_cd`, đừng nhìn HTTP status
- Tài khoản đăng nhập WeCMS phải là **ADMIN** (`settings-core-zalo-oa` chưa có trong Menu Entitlement)
- Ví ZBS phải có tiền (lúc kiểm: 1.145.540 đ)
- OA đang ở gói `Service Test` — nếu gửi tin lỗi, kiểm điều kiện *"OA đã xác thực **và nâng cấp gói**"*

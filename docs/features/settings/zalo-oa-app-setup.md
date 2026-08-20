# Zalo OA — Hướng dẫn tạo App & Template trên console Zalo

**Đối tượng:** người setup tài khoản Zalo cho WeCMS (dev hoặc TA), không phải hướng dẫn code.
**Tài liệu liên quan:** [zalo-oa.md](zalo-oa.md) (backend) · [zalo-oa-schema.md](../../references/database/settings/zalo-oa-schema.md) (schema) · [tools/zalo-setup-progress.md](../../../tools/zalo-setup-progress.md) (tiến độ + giá trị thực tế)
**Nguồn:** thao tác thật trên console Zalo ngày **2026-08-18** (app + OA + liên kết ZBS) và **2026-08-19** (tạo template — bị từ chối 1 vòng rồi duyệt lại thành công). Nhãn tiếng Việt **đã xác minh trực tiếp**.
Bản cũ ([ZALO-INTEGRATION-SETUP.md §1](../../../../ZALO-INTEGRATION-SETUP.md)) dựa trên PPTX chụp UI **tiếng Hàn** — dùng file này thay thế.

> **UI Zalo thay đổi theo thời gian.** Nhãn ở đây đúng tại 2026-08-19. Chỗ nào lệch thì tin console.

**Bộ giá trị đã dựng xong và verify được** (dùng làm mốc đối chiếu, không phải secret):

| Thứ | Giá trị |
|---|---|
| App | `WeCMS Test` — App ID `30615687819892716` (Đang hoạt động, đã liên kết ZBS) |
| OA | `WABOOKS TA Test` — OA ID `1910690480834847825` (Tài khoản xác thực, gói `Service Test`) |
| Tài khoản ZBS | `Z4UJBZ946ZP4MMMPP` (dùng chung với WABOOKS / WeBill365) |
| Template | `TEST - Thông tin hệ thống 1` — **ID mẫu ZBS `625384`**, Đã duyệt 2026-08-19 |
| Callback | `https://wecms.kosign.dev/authorized` |

App Secret **không** ghi ở đây và không ghi vào bất kỳ file nào trong repo — chỉ nằm trong `.env` (gitignored).

---

## 1. Vì sao quy trình này gây rối

Nó **không đi thẳng một đường**. Có **3 hệ thống riêng biệt** phải gặp nhau:

```
┌─ developers.zalo.me ────────┐   ┌─ oa.zalo.me ──────────┐
│  Developer App              │   │  Official Account     │
│  · App ID / Secret          │   │  · OA ID              │
│  · Đăng ký API              │   │  · XÁC THỰC OA ←chậm  │
│  · Xác thực domain          │   │  · Nâng cấp gói       │
│  · Callback URL             │   └───────────┬───────────┘
└──────────┬──────────────────┘               │
           │        OA cấp quyền cho App      │
           └──────────────┬───────────────────┘
                          │
           ┌──────────────▼──────────────────────────┐
           │ account.zalo.solutions  (ZBS)           │
           │  · App phải được LIÊN KẾT vào ZBS ←★    │
           │  · Ví phải có tiền                      │
           │  · Tạo template → Zalo duyệt tay        │
           └─────────────────────────────────────────┘
```

**Hai điểm nghẽn không do bạn quyết định:**

| ★ | Nghẽn | Ai xử lý |
|---|---|---|
| 1 | **Xác thực OA** (nộp giấy tờ doanh nghiệp) | Zalo duyệt — chậm nhất, **nộp trước tiên** |
| 2 | **Liên kết App vào tài khoản ZBS** | Cần quyền **Quản trị viên của APP** (không phải của ZBS) + truy cập console ZBS. **Không** làm được từ console developers — xem §3 |

Làm sai thứ tự sẽ hoàn thành 100% việc khác rồi tắc ở bước cuối.

---

## 2. Phần 1 — `developers.zalo.me`

### 2.1 Tạo app — form `Tạo ID ứng dụng mới`

`developers.zalo.me/createapp`. Form tạo **chỉ có 3 trường**:

| Trường | Ghi chú |
|---|---|
| Tên hiển thị `*` | Hiện `Tên hợp lệ` màu xanh khi đạt |
| Danh mục `*` | WeCMS chọn `Tài chính` |
| Mô tả `*` | tối đa 500 ký tự |

Kèm captcha Cloudflare + tích *"Tôi đã đọc và đồng ý với Chính sách nền tảng của Zalo và Thỏa thuận
xử lý dữ liệu"* → bấm **`Tạo ID ứng dụng`**.

### 2.2 Điền tiếp ở `Cài đặt` (trang settings sau khi tạo)

`developers.zalo.me/app/<APP_ID>/settings`. Sau khi tạo app, **`Điện thoại liên hệ` và
`Email liên hệ` còn TRỐNG** dù có dấu `*` — phải vào đây điền:

| Trường | Ghi chú |
|---|---|
| Điện thoại liên hệ `*` · Email liên hệ `*` | trống sau khi tạo, phải điền |
| Miền ứng dụng | **không** có dấu `*` nhưng phải điền — domain dùng cho Callback URL |
| Icon ứng dụng (512×512) | **không** có dấu `*` → thêm sau được. Nhưng nó hiện trên màn hình xin quyền OAuth, và có thể bị bắt buộc khi kích hoạt |

**Lấy 2 giá trị:** `ID ứng dụng` và `Khóa bí mật của ứng dụng` (bấm icon con mắt).

> ⛔ **Secret không bao giờ commit vào git.** WeBill365 hardcode `app_id` + `app_secret` vào
> `application-local.yml` và giờ chúng nằm trong lịch sử git của repo đó. WeCMS đọc từ `.env`,
> và `ZaloProperties` cố ý **không có giá trị mặc định** cho 2 biến này.

### 2.3 Khối "Bảo mật" — đừng bật

> ⛔ **`Yêu cầu kiểm tra app secret proof khi gọi api có sử dụng access token` — GIỮ TẮT.**
> Client WeCMS chỉ gửi header `access_token`, **không tính `appsecret_proof`** (không có HMAC ở
> đâu trong `domain/setting/zalooa/client/`). Bật là **mọi lời gọi gửi tin bị từ chối**, và lỗi
> trả về trông như lỗi token nên rất khó truy nguyên.

### 2.4 Kích hoạt app

Toggle góc phải trên: `Chưa kích hoạt` → **`Đang hoạt động`**. Cần bật để dùng ZNS thật.

### 2.5 `API và Cấp quyền` → `Đăng ký sử dụng API`

Có 4 submenu. **Chỉ cần 2**:

| Submenu | Cần? | Vì sao |
|---|---|---|
| **ZNS API** → nhóm **`Gửi ZNS`** | ✅ **BẮT BUỘC** | Quyền cho `POST business.openapi.zalo.me/message/template` |
| **Official Account API** | ✅ Cần | Liên quan luồng OAuth `oauth.zaloapp.com/v4/oa/access_token` |
| Social API · Article API | ❌ | Không dùng |

Trong `ZNS API` còn 2 nhóm khác — **không cần**: `Truy xuất thông tin` (tra trạng thái/quota),
`Quản lý tài sản ZNS` (tạo template qua API — WeCMS tạo template bằng tay trên console).
Cũng không cần `Gửi ZNS với hệ mã hóa RSA` (client gửi JSON thường) và `Khởi tạo Journey Token`.

Zalo tự cảnh báo trên trang: *"Không xin quyền sử dụng tất cả API cùng một lúc. Việc này sẽ khiến
thời gian xét duyệt tăng lên."*

> **Ghi nhận thực tế 2026-08-18:** cả `ZNS API` (Gửi ZNS) và `Official Account API` đều **đã được
> duyệt sẵn**, quota `không giới hạn`. Có thể vì tài khoản ZBS của công ty đã có lịch sử. Đừng cho
> rằng bạn cũng sẽ được duyệt tức thì.

### 2.6 `Xác thực domain`

> ⚠️ **Xác thực domain là PER APP, không dùng chung.** Domain đã xác thực cho app A **không** tự có
> hiệu lực cho app B — kể cả cùng một domain. Kiểm chứng 2026-08-18: `wecms.kosign.dev` xác thực
> xong cho `WeCMS Test` (`Danh sách domain xác thực (1/20)`), nhưng app `WeCMS Test v2` vẫn hiện
> `(0/20)` kèm popup **`Chưa được xác thực`**. **Tạo app mới = phải xác thực lại domain.**

Nhập domain → modal **`Xác thực quyền sở hữu`** cho **3 cách chọn**:

| Cách | Nội dung | Ghi chú |
|---|---|---|
| **Thêm DNS TXT record vào domain của bạn** | Thêm bản ghi TXT `zalo-platform-site-verification=<token>` | Một số trang quản lý domain cần ký hiệu `@`. Zalo cảnh báo **có thể mất tới 72 giờ** để có hiệu lực — nếu vẫn `Chưa được xác thực` thì bấm `Xác thực` lại. Cần quyền quản lý DNS. |
| **Tải tệp HTML lên trang web của bạn** | File `zalo_verifierXXXX.html` ở root site | ✅ **Cách WeCMS đã dùng** — đặt vào `wecms-web/public/`, deploy |
| **Thêm thẻ meta vào trang chủ trang web của bạn** | Thẻ `<meta>` trên trang chủ | |

Nếu chọn cách file HTML, kiểm bằng lệnh **trước khi** bấm `Xác thực`:

```bash
curl -o /dev/null -w '%{http_code}' https://<domain>/zalo_verifierXXXX.html
```

Phải trả **200**. Xong thì `Danh sách domain xác thực` hiện domain đó.
`Tiền tố URL (0/20)` để trống — không cần.

### 2.7 `Sản phẩm` → `Official Account`

> ⚠️ **Sidebar có HAI mục tên gần giống nhau** — đây là chỗ nhầm nhiều nhất:
> - `API và Cấp quyền` → `Đăng ký sử dụng API` → **Official Account API** = *xin quyền gọi API nào*
> - `Sản phẩm` → **Official Account** = *gắn OA cụ thể vào app* ← mục này

Có 3 submenu: `Thiết lập chung` · `Quản lý OA` · `Thống kê sản phẩm` (không cần).

#### `Thiết lập chung` — làm TRƯỚC

Vì trang `Quản lý OA` ghi: *"Việc cấp quyền sẽ cho phép App được sử dụng các API và nhận Webhook
tương ứng **đã thiết lập tại Thiết lập chung**"*. Cấp quyền trước khi khai xong = cấp một tập rỗng.

**Khối `Thiết lập đường dẫn yêu cầu cấp quyền` — 3 ô:**

| Ô | Làm gì |
|---|---|
| **Official Account Callback Url** | ✅ Điền `https://<domain>/authorized` → bấm **Cập nhật** |
| **Code Challenge** | ⛔ **ĐỂ TRỐNG** — xem cảnh báo dưới |
| **State** | ⚪ Để trống. Code không verify `state` (gap bảo mật đã ghi nhận, vá riêng) |

> ⛔ **`Code Challenge` là PKCE. Điền vào là bước `authorize` chắc chắn fail.**
> `RestClientZaloApiClient.exchangeAuthorizationCode` chỉ gửi `app_id` + `grant_type` + `code`,
> **không có `code_verifier`**. Zalo sẽ đòi `code_verifier` khớp và WeCMS không có gì để gửi.

Sau khi Cập nhật, khối trên (`Đường dẫn yêu cầu cấp quyền`) sẽ có `redirect_uri` đầy đủ.
**Đối chiếu với lệnh:**

```bash
cd wecms-api/tools && ./zalo-setup.sh url "https://<domain>/authorized"
```

Hai URL phải giống nhau. Nếu console vẫn hiện `redirect_uri=` rỗng → Callback URL chưa lưu được.

**Cuộn xuống — khối `Chọn quyền cần yêu cầu được cấp từ OA`**, gồm `Quyền sử dụng API` (13) + `Quyền nhận sự kiện Webhook` (6):

| Tích | Quyền |
|---|---|
| ✅ | **Gửi tin qua số điện thoại** — bắt buộc, đúng API code gọi |
| ✅ | **Quản lý Message Template** — nên giữ (chưa xác minh có bắt buộc không, rủi ro thấp) |
| ❌ | 11 quyền còn lại |
| ❌ | **Cả 6 quyền webhook** — WeCMS chưa có endpoint webhook nào |

**4 quyền cần bỏ vì có rủi ro thật**, nhất là khi OA là tài sản production của công ty:

| Quyền | Cho phép |
|---|---|
| `Mua sản phẩm dịch vụ OA` | **Tạo đơn hàng** + **xác nhận thanh toán** |
| `Sử dụng chức năng gọi thoại` | **Gọi điện outbound** |
| `Quản lý ads` | Quản lý quảng cáo |
| `Quản lý cửa hàng, đơn hàng` | Quản lý đơn hàng |

Ai có `app_secret` là dùng được toàn bộ quyền đã tích. Thêm nữa, admin OA phải bấm đồng ý — một
yêu cầu 2 quyền được duyệt nhanh; 19 quyền có cả "gọi điện outbound" và "xác nhận thanh toán" thì
dễ bị hỏi lại hoặc từ chối.

**Ngoại lệ:** nếu OA là OA test và **bạn là admin của nó**, để nguyên cũng không sao — không có gì
vỡ về mặt kỹ thuật. Sửa quyền sau khi đã cấp thì **khả năng cao phải xin đồng ý lại**.

#### `Quản lý OA` — cấp quyền

> ⚠️ **`Liên kết` ≠ `Cấp quyền`** — hai thứ khác nhau trên cùng một trang:
> - Khối **`Liên kết với Official Account`** (dropdown + nút `Liên kết`): chỉ để bật trường
>   `user_id_by_app` trong webhook. **Không phải thứ bạn cần.**
> - Khối **`Danh sách OA đã cấp quyền (Tổng số: N)`**: đây mới là cái quan trọng.

**Chiều liên kết dễ hiểu ngược:**

```
❌ Không phải:  App  ──thêm──>  OA
✅ Thực tế:     OA   ──cấp quyền──>  App
```

Cấp quyền thực hiện qua **luồng OAuth**, do **admin của OA** bấm:

1. Copy URL ở khối `Đường dẫn yêu cầu cấp quyền` (hoặc chạy `./zalo-setup.sh url <callback>`)
2. Mở trên trình duyệt → chọn OA → tích *"Tôi đã đọc và hoàn toàn đồng ý…"* → nút **`Cấp quyền`** sáng lên → bấm
3. Về `Quản lý OA` kiểm `Tổng số` chuyển **0 → 1**

Trang xin quyền có cảnh báo vàng: *"**OA đã xác thực và nâng cấp gói** sẽ được sử dụng các API sau"*
→ OA phải **verified** *và* ở **gói đã nâng cấp**. Badge trên OA cho biết: `Tài khoản xác thực` là
tốt; gói `Service Test` thì chưa rõ có đủ để gửi ZNS thật hay không.

**Bắt `code` sau khi bấm Cấp quyền:** Zalo redirect về `<callback>?oa_id=…&code=…`. Route
`/authorized` **chưa tồn tại** trong `wecms-web` (trả 307) nên trang nhảy đi rất nhanh.
Mở **F12 → Network → tích `Preserve log`** *trước* khi bấm, rồi lấy full URL của request tới
`/authorized`.

> `code` **dùng một lần, hết hạn nhanh**. Chỉ lấy code khi backend WeCMS **đã chạy sẵn**. Mất code
> không sao — mở lại URL cấp quyền là có code mới (nếu bạn là admin OA).

---

## 3. Phần 2 — `account.zalo.solutions` (ZBS): liên kết App vào tài khoản ZBS

**★ Điểm nghẽn thật — và nó KHÔNG nằm trong console developers.**

Khi tạo template, dropdown `Chọn App` **chỉ hiện app đã được liên kết vào tài khoản ZBS**. Làm đủ
mọi thứ ở Phần 1 — kể cả OA đã cấp quyền — app vẫn **không** tự xuất hiện.

### Vị trí chính xác

```
account.zalo.solutions/<ZBS_ID>/setting/asset/app

Cài đặt → Quản lý tài sản → tab LIÊN KẾT ỨNG DỤNG
   (tab còn lại: LIÊN KẾT OA)
```

Trang có 2 khối:

| Khối | Nội dung |
|---|---|
| **`Ứng dụng đã liên kết`** | Bảng `Tên ứng dụng` / `ID ứng dụng` / `Thao tác` (nút `Hủy liên kết`) |
| **`Liên kết ứng dụng mới`** | Nơi thêm app — kèm ô tìm `Tìm theo tên hoặc ID của ứng dụng` |

### Ai làm được — không phải admin ZBS

Trang ghi rõ:

> *"Tài khoản ZBS cần liên kết với ứng dụng để ủy quyền sử dụng các tính năng trả phí.
> Để liên kết ứng dụng, bạn cần có quyền **Quản trị viên của ứng dụng** đó."*

Yêu cầu là **admin của APP**, không phải admin của tài khoản ZBS. Người tạo app là admin của nó, nên
**người tạo app tự liên kết được** — miễn là truy cập được vào console ZBS.

> ✅ **Kiểm chứng 2026-08-18:** `WeCMS Test` (`30615687819892716`) đã liên kết thành công vào ZBS
> `Z4UJBZ946ZP4MMMPP`, nằm trong `Ứng dụng đã liên kết` cùng 4 app cũ
> (`TEST WeBill365 send Bill infomation`, `WeBill365 send Bill infomation`, `WABOOKS TA`,
> `WABOOKS for Tax Accountant`).

### Đường tắt nếu chỉ để test

Dùng một app **đã có sẵn** trong ZBS để tạo template. Nhưng: **template bị gắn cứng vào app**
(màn `Xem chi tiết` template có trường `Ứng dụng`; danh sách ZBS ghi `Ứng dụng WABOOKS TA`…), và
access token lấy theo cặp **app + OA** → gần như chắc chắn **template thuộc app nào thì phải gửi
bằng token của app đó**. Nghĩa là `.env` phải dùng `app_id`/`app_secret` của app đó, và app riêng
của bạn tạm thời vô dụng. *(Chưa tự kiểm được; cách kiểm là gửi thử.)*

---

## 4. Phần 3 — Tạo template

`Công cụ` → `Dịch vụ gửi tin` → `Xem chi tiết` → `Thiết kế nội dung` → `Quản lý Template` → **`+ Tạo Template`**

### 4.0 Thương hiệu trong tin là của OA, KHÔNG phải của WeCMS

Đọc mục này trước khi soạn nội dung — hiểu sai chỗ này là mất trọn một vòng duyệt.

Zalo kiểm định danh ở **mức OA**, không ở mức công ty:

```
Webcash Vietnam        (cong ty — Zalo KHONG so o tang nay)
├── OA "WABOOKS TA Test"    <- LG_9 so logo voi DUNG dong nay
├── OA khac...
└── App "WeCMS Test"        <- app chi la ong dan, KHONG co thuong hieu
```

Cùng một công ty sở hữu nhiều thương hiệu **không** cho phép đặt logo thương hiệu B lên OA tên A —
người kiểm duyệt không có cách nào biết hai thương hiệu chung chủ, và không được phép đoán.

Điều này khớp với schema: `zalo_tmpl_infm` PK `(BIZ_KEY, OA_ID, TMPL_TYPE, LANG_CD)` —
**template thuộc OA của một tenant**, không thuộc WeCMS. WeCMS chỉ là hệ thống gửi hộ.
Hệ quả cho vận hành đa tenant: mỗi tenant phải tự có OA riêng, tự tạo template mang thương hiệu
của họ, và **`template_id` không dùng chung được giữa các tenant** vì nó gắn cứng vào một OA.
Đây là chi phí onboarding thật, không phải chi tiết kỹ thuật.

| Muốn gì | Phải làm | Chi phí |
|---|---|---|
| Test tích hợp (thứ bạn cần trước) | Dùng OA sẵn có, logo + tên thương hiệu **của OA đó** | 0 — làm ngay |
| Tin mang thương hiệu riêng (vd `WeCMS`) | Tạo OA mới → xác thực doanh nghiệp → nâng cấp gói → cấp quyền cho app → liên kết ZBS lại | vài tuần |
| Giữ logo thương hiệu khác trên OA hiện có | Nộp giấy chứng nhận nhãn hiệu (Cục SHTT) / quyền tác giả / **giấy ủy quyền từ công ty chủ nhãn** qua ô `Ghi chú cho kiểm duyệt` hoặc https://go.zalo.me/zbs-support | thêm ≥1 vòng duyệt tay, **chưa xác minh Zalo có nhận giấy ủy quyền nội bộ tập đoàn** |

> Badge `Tài khoản xác thực` trên OA chứng minh **công ty sở hữu OA**, KHÔNG chứng minh
> **công ty sở hữu nhãn hiệu** dùng làm logo. Hai giấy khác nhau — LG_9 không được miễn nhờ OA đã verified.

Tên mẫu Template là nhãn nội bộ trên console, Zalo không soi như soi nội dung — đó là chỗ duy nhất
chữ `WeCMS` được phép xuất hiện. Ví dụ đã duyệt: `TEST - Thông tin hệ thống 1`.

### Bước 1 — `Thông tin chung`
`Tên mẫu Template *` · `Chọn App bạn muốn dùng để tạo Template *` · `Chọn OA bạn muốn dùng để gửi tin *`

Ô `Chọn OA` **bị xám mờ cho tới khi chọn được App** — đó là hệ quả, không phải lỗi riêng.

### Bước 2 — `Khai báo nội dung`

| Mục | Chọn |
|---|---|
| `Chọn loại Template` | **`Mẫu tuỳ chỉnh`** — *Từ 210đ/tin*. Còn lại: `Mẫu Voucher` *Từ 280đ/tin*, `Mẫu OTP`, `Mẫu đánh giá dịch vụ`, `Mẫu yêu cầu chuyển khoản`, `Mẫu phản hồi nhanh` |
| `Chọn mục đích gửi tin ZBS` | **`Cấp độ 1` — `Giao dịch`** cho tin giao dịch (còn lại: `Chăm sóc khách hàng`). Mục đích quyết định điều kiện gửi + giá |
| `Logo, hình ảnh *` | Tối đa **1 logo HOẶC 3 hình ảnh**. Logo sau khi được duyệt sẽ tự động cập nhật |
| Thành phần nội dung | Khối `Văn bản` + khối `Bảng` (mỗi hàng có Tiêu đề + Nội dung) + `Nút thao tác` — vd `Đến trang thông tin OA (+0đ)` |
| `Thư viện Template` | 4 tab: `Thương mại điện tử` / `Sức khoẻ/y tế` / `Giáo dục` / `Dịch vụ làm đẹp`. **Không có tab Tài chính** → chọn mẫu gần nhất rồi sửa |

**Đơn giá xác nhận trên màn hình** (panel `Xem trước Template`): `Mẫu tuỳ chỉnh` 300 VNĐ · `Nút thao tác 1` **0 VNĐ** ·
`Đơn giá dự kiến`: **Gửi qua SĐT 300 VNĐ/tin · Gửi qua UID 210 VNĐ/tin** → UID rẻ hơn ~30%, thêm nút thao tác miễn phí.

#### Ba quy tắc nội dung Zalo BẮT BUỘC (rút từ vòng bị từ chối 2026-08-19)

Ba quy tắc này không nằm trong `Khả năng phê duyệt` — công cụ đó cho `Cao` / `0 lỗi`
rồi mẫu vẫn bị từ chối. Phải tự kiểm bằng tay:

| # | Quy tắc | Vì sao |
|---|---|---|
| 1 | **Logo phải khớp tên OA** | ZNS gắn 1 OA = 1 thương hiệu. Logo lạ bị coi là mạo danh → `LG_9` |
| 2 | **Tên thương hiệu phải là chữ CỐ ĐỊNH, tuyệt đối không phải biến** | Nếu là biến thì một OA gửi được tin mang tên bất kỳ thương hiệu nào → Zalo chặn tuyệt đối → `CT_16` |
| 3 | Mẫu khai mục đích test phải có **đúng cụm `Nội dung test`** trong nội dung | Để người nhận biết đây là tin test → `CT_9` |

Quy tắc 2 là quy tắc cứng, không phải góp ý: biến `<company_name>` phải **biến mất khỏi bảng tham số**,
không phải "điền giá trị mẫu đẹp hơn". Gõ thẳng tên thương hiệu vào nội dung.

Mẫu nội dung đã được duyệt (thay `WABOOKS` bằng thương hiệu của OA bạn dùng):

```
Noi dung test

Xin chao <customer_name>, WABOOKS da hoan thanh qua trinh xu ly:

  So luong hoa don da xu ly : <count_invoice>
  Trang thai                : <process_status>

Cam on ban da mua hang tai WABOOKS.

[Nut] Den trang thong tin OA        (+0d)
```

Vì sao mẫu này qua: `WABOOKS` là chữ cố định (2 lần) · có cụm `Nội dung test` ·
`đã hoàn thành quá trình xử lý` thể hiện người nhận **đã phát sinh giao dịch** ·
mỗi biến có nhãn đứng trước (`Xin chào`, `Số lượng hóa đơn đã xử lý`, `Trạng thái`).

Ô `Ghi chú cho kiểm duyệt` — **đừng để trống**, đây là chỗ trả lời trước cho người duyệt để chặn `LG_9` quay lại:

```
Mau tin dung cho muc dich TEST tich hop ZNS cua he thong WeCMS. Noi dung test.
Logo su dung la logo thuong hieu WABOOKS, trung voi ten OA WABOOKS TA Test.
Ten thuong hieu da duoc ghi co dinh trong noi dung (WABOOKS), khong dung bien.
Tin chi gui toi so dien thoai noi bo cua doi phat trien, sau khi don hang da phat sinh.
```

#### `Khả năng phê duyệt` — công cụ dự đoán trước khi submit

Console tự đánh giá nội dung và cho điểm (vd **`Cao`**) kèm tiêu chí đã đạt + `Khuyến nghị (n lỗi)`.
Hai tiêu chí ảnh hưởng trực tiếp tới cách đặt tham số và viết nội dung:

- *"Nội dung cần thể hiện người nhận tin đã từng có giao dịch với doanh nghiệp"*
- *"Mỗi tham số cần có **tiền tố mô tả rõ ràng**"* — tham số phải có nhãn giải thích, không để trần

Nhóm `Nâng cao chất lượng mẫu ZBS`: tránh từ ngữ gây khó hiểu · danh xưng đồng nhất · một ngôn ngữ duy nhất.

> **Đưa `Khuyến nghị` về 0 lỗi trước khi submit.** Duyệt là thủ công, không SLA — mỗi lần bị từ chối mất trọn một vòng chờ.

> ⚠️ **`Khả năng phê duyệt: Cao` + `Khuyến nghị (0 lỗi)` KHÔNG đảm bảo được duyệt.** Đã xảy ra thật:
> mẫu đạt `Cao` / `0 lỗi` vẫn bị từ chối 3 mã `LG_9` + `CT_16` + `CT_9` (§4.5). Công cụ này chỉ chấm
> chất lượng diễn đạt, không chấm logo và không chấm quy tắc thương hiệu cố định.

### Bước 3 — `Gửi duyệt`: khai tham số (chỗ khớp với code)

> ⚠️ **Sửa lại so với bản trước của tài liệu này:** tham số khai ở **Bước 3**, KHÔNG phải Bước 2.

Bảng tham số 3 cột, sinh ra từ các biến đã dùng ở Bước 2:

| Cột | Là gì | Lưu ý |
|---|---|---|
| `Tên tham số` | khoá biến — `customer_name`, `product_name`, `order_code`, `payment_status`… | **Đây là khoá trong `template_data`.** Sai một ký tự là Zalo từ chối tin |
| `Cài đặt kỹ thuật` | dropdown loại dữ liệu có sẵn, **kèm độ dài tối đa trong ngoặc** — vd `Tên khách hàng (30)`, `Tên sản phẩm / Thương hiệu (200)` | Chọn loại **khớp dữ liệu thật**. Chọn sai loại = chọn sai luôn giới hạn độ dài |
| `Nội dung tham số *` | giá trị mẫu cho người kiểm duyệt | Bắt buộc. Vd `Nguyễn Lê Minh Khoa`, `Bàn phím Razer` |

Chọn `Cài đặt kỹ thuật` đồng thời chốt luôn **`Loại dữ liệu`** (`string` / `number`) — cột này không
sửa riêng được, và nó chỉ hiện ở màn `Xem chi tiết` sau khi duyệt. Xem cảnh báo `number` ở §4.4.

Dưới bảng: ô `Ghi chú cho kiểm duyệt`, tích `Điều khoản và Chính sách sử dụng của Zalo Business Solutions`, bấm **`Hoàn thành`**.

> ⛔ **Bẫy thấy trong ảnh thực tế (bản nháp bị từ chối):** `order_code` và `payment_status` đều bị để `Cài đặt kỹ thuật` = `Tên khách hàng (30)`.
> Mã đơn hàng và trạng thái thanh toán không phải tên người, và giới hạn 30 ký tự có thể cắt mất dữ liệu.
> Mở dropdown chọn lại loại đúng cho **từng** tham số trước khi bấm Hoàn thành.

**Ghi lại nguyên bảng này cho dev:** cột `Tên tham số` là danh sách khoá `template_data`; cột `Cài đặt kỹ thuật`
cho biết độ dài tối đa mà WeCMS nên validate trước khi gọi Zalo.

### Bước 4 — Lấy `template_id` từ màn `Chi tiết mẫu Template`

Trạng thái template: `Nháp` · `Đang duyệt` · **`Đã duyệt`** · `Bị từ chối` · `Bị khóa`.
Chỉ `Đã duyệt` mới gửi được.

| Trường trên màn hình | Giá trị thực tế 2026-08-19 | Dùng ở đâu |
|---|---|---|
| **`ID mẫu ZBS`** | **`625384`** | `template_id` — tham số lệnh `template`, cột `TMPL_ID` |
| `Loại mẫu` | `Mẫu tuỳ chỉnh` | — |
| `OA gửi` | `WABOOKS TA Test` | phải khớp OA đã `register` trong WeCMS |
| **`Ứng dụng`** | **`WeCMS Test`** | ⭐ xem ghi chú dưới |
| `Đơn giá gửi qua SĐT` | `300 đ/tin` | `UNIT_PRC` |
| `Đơn giá gửi qua UID` | `210 đ/tin` | tham khảo — WeCMS gửi qua SĐT |
| `Mục đích gửi` | `Giao dịch` | — |
| `Ztime (s)` | `7200` | giây — tin hết hạn nếu chưa gửi được. **WeCMS chưa đọc giá trị này** |
| `Đã gửi` | `0 tin SĐT` | đối chiếu với `ZALO_SEND_HIS` sau khi test |
| `Tham số` | bảng 4 cột — xem §4.4 | khoá của `template_data` |

> ⭐ **Trường `Ứng dụng` là dòng đáng giá nhất trên màn này.** Nó chứng minh chuỗi
> app → ZBS → OA đã nối đúng, nên `.env` dùng `app_id`/`app_secret` của app đó là gửi được —
> không phải đi đường tắt qua app khác (§3 `Đường tắt nếu chỉ để test`).
> Đồng thời nó xác nhận **template gắn cứng vào một app**, không phải tài sản dùng chung.

### 4.4 Bảng tham số thật — và bẫy `number`

Màn `Chi tiết mẫu Template` hiện bảng tham số **4 cột** (thêm `Loại dữ liệu` so với lúc khai ở Bước 3):

| `Tên tham số` | `Cài đặt kỹ thuật` | `Loại dữ liệu` | Max | `Nội dung tham số` |
|---|---|---|---|---|
| `customer_name` | Tên khách hàng | `string` | 30 | Trần Thị Hoa |
| `count_invoice` | Số lượng / Số tiền | **`number`** | 20 | 150 |
| `process_status` | Trạng thái giao dịch | `string` | 20 | Thành công |

Ba khoá đó — và **chỉ** ba khoá đó — là `template_data` khi gọi API. Thêm khoá lạ hoặc thiếu khoá là Zalo từ chối tin.

> ⚠️ **Rủi ro chưa xác minh — param `number` gặp `Map<String, String>`.**
> `ZaloSendRequest.templateData` và `ZaloSendCommand.templateData` đều khai `Map<String, String>`,
> nên `count_invoice` được serialize thành `"count_invoice": "150"` — **có ngoặc kép**.
> Zalo thường nhận string cho param `number` miễn nội dung là số thuần, nhưng điều đó **chưa được
> kiểm với ZNS**. Nếu `send` trả lỗi liên quan `count_invoice`, nguyên nhân gần như chắc chắn là chỗ này;
> cách sửa là đổi hai DTO đó sang `Map<String, Object>`. **Đừng sửa trước** — gửi thử đã, có thể không cần.
>
> Và luôn gửi **số thuần**: `150`, không phải `1.500` hay `150 hóa đơn`. Dấu phân cách nghìn
> làm Zalo reject param `number`.

### 4.5 Mẫu bị từ chối — 3 mã lỗi thật và cách sửa

Vòng submit đầu tiên (2026-08-19) bị từ chối với 3 mã. Cả 3 đều sửa được ngay, **không cần chờ Zalo**:

| Mã | Zalo báo gì | Nguyên nhân thật | Cách sửa |
|---|---|---|---|
| **`LG_9`** | *Logo không khớp với tên OA* | Dùng logo `WeBill365` trên OA tên `WABOOKS TA Test` | Đổi sang **logo khớp tên OA**. Hoặc nộp giấy chứng nhận nhãn hiệu / quyền tác giả / giấy ủy quyền (§4.0) |
| **`CT_16`** | *Vui lòng ghi cố định tên doanh nghiệp/thương hiệu vào biến `<company_name>`* | Tên thương hiệu đang là **tham số** | **Xoá hẳn biến `<company_name>`** khỏi bảng tham số, gõ chữ thương hiệu cố định vào nội dung |
| **`CT_9`** | *Mẫu tin dùng cho mục đích test vui lòng bổ sung cụm `"Nội dung test"`* | Khai mục đích test nhưng nội dung không nói | Thêm **đúng cụm `Nội dung test`** vào nội dung |

Cách nộp chứng từ cho `LG_9`: ô `Ghi chú cho kiểm duyệt` ngay trong template (nhanh hơn),
hoặc form https://go.zalo.me/zbs-support.

Mở template bị từ chối → sửa → gửi duyệt lại. Sửa **tất cả** rồi submit một lần —
đừng sửa từng lỗi từng vòng, vì duyệt là thủ công và không có SLA.

> ⚠️ **Khi lên production phải làm template mới:** bỏ cụm `Nội dung test`, và sửa các lỗi
> diễn đạt mà Zalo không chặn nhưng sẽ xuất hiện trong mọi tin gửi ra. Mẫu `625384` hiện còn
> `"quán trình xử lý"` (đúng: *quá trình*), lặp `Xin chào <customer_name>` hai lần, và câu
> `Cảm ơn bạn đã mua hàng` không khớp ngữ cảnh *số lượng hóa đơn đã xử lý*.
> Không chặn test kỹ thuật, nhưng đừng mang sang production.

---

## 5. Phần 4 — Đưa vào WeCMS

```env
WECMS_ZALO_ENABLED=true
WECMS_ZALO_APP_ID=<App ID>
WECMS_ZALO_APP_SECRET=<App Secret>
WECMS_ZALO_TOKEN_REFRESH_CRON=-
```

- `ENABLED=false` giữ `MockZaloApiClient` — mọi lời gọi Zalo ném lỗi, API trả `WBE_ZALO_DISABLED`.
  Chỉ bật `true` khi thật sự sẵn sàng gửi (mỗi tin tốn tiền, tới điện thoại người thật).
- `CRON=-` là marker **không bao giờ chạy**. Để **trống** làm app **chết lúc khởi động**.

Rồi chạy [`tools/zalo-setup.sh`](../../../tools/zalo-setup.sh):

```bash
cd wecms-api/tools
./zalo-setup.sh check          # preflight: backend, .env, phiên, OA hiện có
./zalo-setup.sh help           # ví dụ trọn luồng
```

### 5.1 Ba định danh khác nhau — đừng nhầm

Câu hỏi hay gặp: *"gắn template vào bằng app_id với secret à?"* — **không**.

| Tầng | Giá trị | Lưu ở đâu | Dùng làm gì |
|---|---|---|---|
| **App** | `app_id` + `app_secret` | `.env` — **một bộ cho cả deployment**, không lưu DB | chỉ để đổi `code` → `access_token` và refresh token |
| **OA** | `oa_id` + access/refresh token | `ZALO_OA_INFM` `(BIZ_KEY, OA_ID)` | token đính header `access_token` khi gửi tin |
| **Template** | `tmpl_id` | `ZALO_TMPL_INFM` `(BIZ_KEY, OA_ID, TMPL_TYPE, LANG_CD)` | trường `template_id` trong body gửi tin |

Vì sao tách ba: `app_secret` là của công ty (một app phục vụ mọi tenant), `oa_id` là của **tenant**,
`tmpl_id` là của **cặp (tenant, OA) + loại tin + ngôn ngữ**. "Gắn template" không liên quan tới secret —
nó chỉ là ghi một hàng vào `ZALO_TMPL_INFM` qua `PUT /api/v1/settings/zalo-oa/{oa_id}/templates`.

### 5.2 Thứ tự — và một chỗ nên đi khác `--help`

`zalo-setup.sh --help` ghi: `login` → `register` → `url` → `authorize` → `template` → `activate` → `send`.

Nhưng `upsertTemplate` chỉ gọi `requireConnection` — nó cần **hàng OA**, KHÔNG cần token
(FK `zalo_tmpl_infm_fk01 → zalo_oa_infm(BIZ_KEY, OA_ID)`). Nên **đăng ký template ngay sau `register`**,
trước khi đi bắt `code`: `code` OAuth **dùng một lần và hết hạn 10 phút**, làm xong hết phần không gấp
trước rồi mới lấy code để `authorize` ngay.

```bash
cd wecms-api/tools
./zalo-setup.sh check
./zalo-setup.sh login <email-admin> '<mat-khau>'
./zalo-setup.sh register 1910690480834847825 "WABOOKS TA Test"
./zalo-setup.sh template 1910690480834847825 625384 NOTICE VI 300
./zalo-setup.sh url https://wecms.kosign.dev/authorized
# mo URL -> cap quyen -> F12 Network (bat Preserve log TRUOC khi bam) -> lay code
./zalo-setup.sh authorize 1910690480834847825 '<code>'
./zalo-setup.sh activate 1910690480834847825
./zalo-setup.sh status
```

`status` phải thấy OA `USE_YN=Y` và template `NOTICE`/`VI` với `TMPL_ID=625384`. Rồi mới gửi thử —
**tốn 300đ thật, tới điện thoại người thật**:

```bash
./zalo-setup.sh send 84xxxxxxxxx '{"customer_name":"Tran Thi Hoa","count_invoice":"150","process_status":"Thanh cong"}' NOTICE VI
./zalo-setup.sh history
```

> ⚠️ **`send` KHÔNG nhận `oa_id`.** Chữ ký là `send <phone> '<json>' [type] [lang]` — backend tự resolve
> OA đang `USE_YN='Y'` của tenant. Đúng vậy mới hợp lý, vì `zalo_oa_infm_uk01` đã đảm bảo mỗi tenant
> chỉ có một OA live. Truyền `oa_id` vào đây là số điện thoại bị hiểu sai.

> ⚠️ **`tmpl_type` / `lang_cd` phải khớp y hệt giữa lúc `template` và lúc `send`.** Chúng nằm trong
> primary key, nên register `NOTICE`/`VI` mà gửi `RECEIPT`/`VI` là không tìm thấy hàng — lỗi tra khoá,
> không phải lỗi Zalo. `requireLangCd` cố ý **từ chối** mã lạ thay vì fallback default, chính để chặn kiểu sai này.

`tmpl_id` bị validate `@Pattern("\\d+")` — chỉ nhận chữ số, dán nguyên số, đừng kèm chữ.
`tmpl_type` hiện dùng `NOTICE` (enum có `NOTICE` / `RECEIPT`); cả DB lẫn DTO **cố ý không constraint**
vì catalogue tin nhắn của WeCMS chưa chốt — nên `NOTICE` là chỗ đậu tạm, không phải cam kết.

---

## 6. Bảng giá trị cần thu thập

| # | Giá trị | Lấy ở đâu | Dùng ở đâu |
|---|---|---|---|
| 1 | **App ID** | `developers.zalo.me` → `Cài đặt` → `ID ứng dụng` | `WECMS_ZALO_APP_ID` |
| 2 | **App Secret** | cùng chỗ, bấm icon con mắt | `WECMS_ZALO_APP_SECRET` |
| 3 | **OA ID** | `Quản lý OA` → `Danh sách OA đã cấp quyền` | tham số lệnh `register` |
| 4 | **template_id** | ZBS → `Xem chi tiết` template → `ID mẫu ZBS` | tham số lệnh `template` |
| 5 | Tên các tham số | cùng màn đó | khóa `template_data` |
| 6 | Callback URL | tự đặt, `https://<domain>/authorized` | phải khớp `redirect_uri` |

Chỉ có đủ **4 giá trị đầu** mới chạy được luồng WeCMS.

Bộ đã thu đủ (2026-08-19), trừ App Secret nằm trong `.env`:

| # | Giá trị | Thực tế |
|---|---|---|
| 1 | App ID | `30615687819892716` |
| 2 | App Secret | ✅ đã có — **chỉ** trong `.env`, không ghi ở đâu khác |
| 3 | OA ID | `1910690480834847825` |
| 4 | `template_id` | `625384` |
| 5 | Tên tham số | `customer_name` (string 30) · `count_invoice` (**number** 20) · `process_status` (string 20) |
| 6 | Callback URL | `https://wecms.kosign.dev/authorized` |

---

## 7. Bảng bẫy

| # | Bẫy | Hậu quả |
|---|---|---|
| 1 | OA **chưa xác thực** | Không gửi được template message. Nộp hồ sơ trước tiên. |
| 2 | OA đúng gói? | Trang xin quyền ghi *"OA đã xác thực **và nâng cấp gói**"*. Gói `Service Test` chưa rõ có đủ. |
| 3 | **App chưa liên kết vào ZBS** | Không hiện trong dropdown `Chọn App`. Làm ở `ZBS → Cài đặt → Quản lý tài sản → LIÊN KẾT ỨNG DỤNG` (§3) — cần quyền admin của **app**. |
| 4 | Bật **`app secret proof`** | Mọi lời gọi API bị từ chối, lỗi trông như lỗi token |
| 5 | Điền **`Code Challenge`** | Bước `authorize` fail — code không có `code_verifier` |
| 6 | `redirect_uri` lệch Callback URL | Zalo từ chối. Phải khớp **từng ký tự**. |
| 7 | `redirect_uri` không thuộc domain đã xác thực | localhost thường không dùng được → ngrok/tunnel hoặc domain dev |
| 8 | Nhầm **`Liên kết`** với **`Cấp quyền`** | Số OA đã cấp quyền vẫn 0 |
| 9 | Nhầm 2 mục **Official Account** trong sidebar | Đi lạc |
| 10 | Mất `code` do trang redirect | F12 → Network → `Preserve log` trước khi bấm |
| 11 | Số điện thoại `0xxxxxxxxx` | Zalo cần E.164: `84xxxxxxxxx` |
| 12 | Tên tham số lệch | Zalo từ chối — lỗi nghiệp vụ, không phải lỗi mạng |
| 13 | Ví ZBS hết tiền | Bị từ chối dù template đã duyệt |
| 14 | Nhìn HTTP status để đoán kết quả | Zalo từ chối vẫn trả **200** + `success:false`. Đọc `rslt_cd`. |
| 15 | Login WeCMS bằng user thường | `settings-core-zalo-oa` chưa có trong Menu Entitlement → phải **ADMIN** |
| 16 | Logo không khớp tên OA | Từ chối `LG_9`. Sở hữu cùng công ty **không** miễn — §4.0 |
| 17 | Tên thương hiệu để làm **biến** | Từ chối `CT_16`. Phải là chữ cố định trong nội dung |
| 18 | Mẫu test thiếu cụm `Nội dung test` | Từ chối `CT_9` |
| 19 | Tin `Khả năng phê duyệt: Cao` / `0 lỗi` là chắc duyệt | **Sai.** Đã bị từ chối 3 mã dù đạt `Cao`/`0 lỗi` (§4.5) |
| 20 | Param `number` gửi qua `Map<String, String>` | Chưa xác minh Zalo có nhận. Nghi phạm số 1 nếu `send` fail — §4.4 |
| 21 | Gửi số có dấu phân cách nghìn (`1.500`) cho param `number` | Zalo reject param |
| 22 | `tmpl_type`/`lang_cd` lệch giữa `template` và `send` | Không tìm thấy hàng — chúng nằm trong PK |
| 23 | Truyền `oa_id` cho lệnh `send` | Chữ ký là `send <phone> '<json>' [type] [lang]` — không có `oa_id` |

---

## 8. Những điều đã suy luận SAI (ghi lại để không lặp)

| Suy luận sai | Thực tế |
|---|---|
| "Cấp quyền OA cho app sẽ làm app hiện trong dropdown ZBS" | **Sai.** Cần liên kết app vào tài khoản ZBS — việc riêng, ở console khác, do admin ZBS làm. |
| "Xác thực domain chưa cần cho việc tạo template" | **Sai.** Cần sớm, vì `Danh sách OA đã cấp quyền` được điền qua luồng OAuth, mà luồng đó cần `redirect_uri` thuộc domain đã xác thực. |
| "App `Chưa kích hoạt` là nguyên nhân app không hiện trong ZBS" | **Sai.** Kích hoạt xong app vẫn không hiện. |
| "Liên kết app vào ZBS cần chủ/admin tài khoản ZBS" | **Sai.** Trang yêu cầu quyền **Quản trị viên của APP**, nên người tạo app tự làm được. |
| "Domain đã xác thực dùng chung cho mọi app" | **Sai.** Xác thực domain là **per app** — app mới phải xác thực lại (§2.6). |
| "Tham số khai ở Bước 2" | **Sai.** Khai ở **Bước 3 `Gửi duyệt`** (§4 Bước 3). |
| "`Khả năng phê duyệt: Cao` + `0 lỗi` là gần như chắc được duyệt" | **Sai.** Mẫu đạt `Cao`/`0 lỗi` vẫn bị từ chối `LG_9` + `CT_16` + `CT_9`. Công cụ đó không chấm logo và không chấm quy tắc thương hiệu cố định (§4.5). |
| "Cùng công ty sở hữu cả 2 thương hiệu thì đặt logo nào cũng được" | **Sai.** Zalo so logo với **tên OA**, không với tên công ty (§4.0). |
| "`CT_16` chỉ cần điền giá trị mẫu đẹp hơn cho `<company_name>`" | **Sai.** Phải **xoá hẳn biến**, gõ chữ cố định. Đây là quy tắc cứng chống mạo danh thương hiệu. |
| "Tài liệu này từng ghi `send <oa_id> <phone> ...`" | **Sai.** `send` không nhận `oa_id` — backend tự resolve OA `USE_YN='Y'` (§5.2). |

---

## 9. Đối chiếu tài liệu chính thức Zalo (2026-08-18)

Nguồn: [khoi-tao-ung-dung](https://developers.zalo.me/docs/official-account/bat-dau/khoi-tao-ung-dung) ·
[xac-thuc-va-uy-quyen-cho-ung-dung-new](https://developers.zalo.me/docs/official-account/bat-dau/xac-thuc-va-uy-quyen-cho-ung-dung-new)
(nội dung nằm trong iframe `stc-developers.zdn.vn/docs/v2/...` — WebFetch trang ngoài chỉ lấy được tiêu đề).

### 9.1 Thời hạn chính thức

| Loại mã | Thời hạn | Ràng buộc |
|---|---|---|
| Authorization code | **10 phút** | Dùng **1 lần** |
| Access token | **25 giờ** | `expires_in` trả về **chuỗi** `"90000"` |
| Refresh token | **3 tháng** | Dùng **1 lần**; sau khi dùng, token cũ vô hiệu và Zalo trả refresh token **mới**. Access token cũ **hết hiệu lực ngay** khi access token mới tạo thành công |

Khớp code: `expirySkewSeconds=300` (5 phút trước hạn 25 giờ) · `refreshStaleDays=29` (an toàn trong hạn 3 tháng) ·
`applyGrant` luôn ghi lại refresh token mới — đúng yêu cầu "chỉ dùng 1 lần".
`RestClientZaloApiClient` parse `expires_in` bằng `asLong` nên nhận cả chuỗi và số.

**Giới hạn mới biết:** *"Mỗi OA có giới hạn số lượng App được ủy quyền nhất định."*

### 9.2 PKCE — xác nhận lời khuyên để trống Code Challenge

Công thức: `code_challenge = Base64.encode(SHA-256.hash(ASCII(code_verifier)))`, code verifier là chuỗi
**43 ký tự** đủ chữ hoa/thường/số, **khác nhau cho từng request**.

Bảng tham số API đổi token ghi `code_verifier` là bắt buộc **"yes nếu ở bước 2 có thiết lập code challenge"**
→ không thiết lập thì không cần gửi. Đúng với code hiện tại, và là lý do phải để trống ô đó (§2.7a).

Zalo *khuyến nghị* dùng PKCE. Việc WeCMS chưa hỗ trợ là **khoảng trống đã biết**, cùng nhóm với việc chưa
verify `state` — vá trong MR riêng, không chặn.

### 9.3 Đường tắt: Zalo API Explorer thay cho luồng OAuth

Tài liệu nêu **Cách 2**: lấy token trực tiếp qua `Công cụ & Hỗ trợ → API Explorer`, **không cần** callback URL,
**không cần** bắt authorization code, **không cần** route `/authorized`.

Điều kiện: phải vừa là **Admin của OA** vừa là **Admin của Ứng dụng** — đúng trường hợp WeCMS hiện tại.

Các bước: chọn Ứng dụng → `Loại Access Token` = `OA Access Token` → chọn OA → kiểm quyền → `Cho phép`
→ copy access token, và ở bước cuối copy cả **refresh token**.

**Cách dùng cho WeCMS:** nạp refresh token thẳng vào `ZALO_OA_INFM.REFRESH_TOKEN` của hàng kết nối, rồi để
`ZaloTokenService` tự refresh ra access token ở lần gửi đầu. Bỏ qua hoàn toàn bước `authorize` — tránh được
cả 3 vướng: domain xác thực cho `redirect_uri`, route `/authorized` đang trả 307, và hạn 10 phút của code.

> Đây là đường tắt để **test**. Luồng OAuth vẫn là đường đúng cho vận hành, vì đó là cách duy nhất để một OA
> **không thuộc quyền bạn** cấp quyền cho app.

---

## 10. Việc còn để mở

| # | Chưa xác minh | Cách kiểm |
|---|---|---|
| 1 | `Quản lý Message Template` có bắt buộc để app hiện trong ZBS? | Bỏ tích rồi xem app còn hiện không |
| 2 | Template thuộc app A có gửi được bằng token app B? | Gửi thử. **Cập nhật 2026-08-19:** màn `Chi tiết mẫu Template` có trường `Ứng dụng` → template gắn cứng vào một app. Chưa kiểm token app khác, nhưng không còn cần thiết: template `625384` đã gắn đúng `WeCMS Test` |
| 3 | Gói `Service Test` có đủ để gửi ZNS thật? | Gửi thử |
| ~~4~~ | ~~`Cài đặt → Quản lý tài sản` có phải nơi liên kết app?~~ | ✅ **ĐÃ XÁC MINH 2026-08-18** — đúng, tab `LIÊN KẾT ỨNG DỤNG` (§3) |
| 5 | Ý nghĩa 2 màu toggle (cam/xanh) ở trang quyền OA API | Bấm từng nhóm, đọc cột `Trạng thái` |
| 6 | Zalo có nhận param `number` dưới dạng string (`"150"`)? | Chạy `send` với `count_invoice`. Nếu fail → đổi `templateData` sang `Map<String, Object>` (§4.4) |
| 7 | Zalo có nhận **giấy ủy quyền nội bộ tập đoàn** để đặt logo thương hiệu B lên OA tên A? | Nộp thử qua `Ghi chú cho kiểm duyệt` hoặc go.zalo.me/zbs-support (§4.0). Đường A — đổi logo cho khớp tên OA — tránh hẳn câu hỏi này |
| 8 | `Ztime` (7200s) có nên đưa vào WeCMS làm hạn gửi lại? | Hiện `ZALO_TMPL_INFM` không có cột cho nó; quyết định sau khi có luồng retry |

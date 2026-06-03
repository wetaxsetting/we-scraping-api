# CLAUDE.md — wecms-api-main (Bản tiếng Việt)

> Đây là bản dịch tiếng Việt của [CLAUDE.md](CLAUDE.md). File gốc tiếng Anh là nguồn chuẩn (source of truth); khi có khác biệt, lấy theo bản tiếng Anh. Mọi tên class, tên bảng/cột, mã code, đường dẫn, mã lỗi và đoạn code đều giữ nguyên không dịch.

Hướng dẫn sống cho AI/lập trình viên về phần backend Spring Boot của dự án refactor WeCMS JEX → Spring/Next.js. Cập nhật file này mỗi khi code thực tế thay đổi.

Các quy tắc cấp workspace (cross-tree, ranh giới ghi, nguồn chuẩn về typography, quy tắc BIZ_KEY) nằm ở [CLAUDE.md](../CLAUDE.md) gốc. Frontend Next.js mà app này phục vụ nằm ở [wecms-web-main/](../wecms-web-main/). Đặc tả chức năng JEX là tài liệu chỉ-đọc tại [WECMS DEV/CLAUDE.md](../WECMS%20DEV/CLAUDE.md).

**Đang port một tính năng?** Quy trình thủ tục đầu-cuối (khám phá → tài liệu thiết kế → Flyway → backend → frontend → kiểm chứng) nằm ở [docs/refactor-wecms/MIGRATION_PLAYBOOK.md](../docs/refactor-wecms/MIGRATION_PLAYBOOK.md). Bước 4–5 của playbook là phần backend; các quy tắc mà mỗi bước liên kết tới đều nằm trong *chính file này*.

---

## Mục đích

API backend cho R&D refactor WeCMS. Phục vụ client Next.js, đọc đúng hình dạng schema JEX cũ (theo từng cột một), và bắt buộc cô lập dữ liệu single-tenant.

Trạng thái: **proof-of-concept R&D**, chỉ chạy local, chỉ trên GitLab. Không deploy DEV / UAT / PROD, không tích hợp ngân hàng, không có code coexistence với hệ thống JEX đang chạy thật.

## Scope Guard — Chỉ thay đổi backend khi có yêu cầu rõ ràng

Cây code này **KHÔNG** bị sửa trong các task static-UI / frontend. Repo frontend (`wecms-web-main/`) là cây mặc định được phép sửa; repo này chỉ được đụng tới khi người dùng yêu cầu rõ ràng một thay đổi backend.

**Các điều kiện cho phép sửa cây code này:**

- Người dùng nêu tên một artifact backend (controller, service, repository, entity, DTO, Flyway migration, `application*.yml`).
- Người dùng yêu cầu một endpoint mới, thay đổi hình dạng request/response, thay đổi schema, hoặc thay đổi quy tắc nghiệp vụ.
- Người dùng nói rõ "backend" / "API" / "Spring" / "endpoint" / "DB" / "schema" / "migration".
- Một task frontend không thể tiếp tục nếu thiếu một API contract tương ứng — trong trường hợp đó Claude phải hỏi người dùng trước khi sửa cây code này (quy tắc full-stack ownership), không được tự suy đoán.

**KHÔNG được làm khi đang làm task frontend:**

- Thêm / xóa / sửa mã nguồn Java dưới `src/main/java/`.
- Thêm / sửa Flyway migration dưới `src/main/resources/db/migration/`.
- Đụng tới `application*.yml`, `build.gradle`, hoặc `messages*.properties`.
- "Đấu nối trước" một endpoint mà một mock frontend đã có thể đảm nhiệm.
- Sửa schema DB local, kể cả qua `postgres` MCP — thay đổi schema phải đi qua Flyway, và Flyway migration là artifact backend.

**Dữ liệu mock cho static-UI thuộc sở hữu của frontend** (`wecms-web-main/components/{feature}/.../mockData.ts`). Không tạo "mock data tương ứng" ở phía backend, và không đề xuất chuyển mock của frontend vào repo này. Frontend ship các panel static-UI không phụ thuộc backend cho tới khi có yêu cầu một endpoint thật.

Khi phân vân, hãy hỏi người dùng. Chi phí dừng lại để xác nhận thì thấp; chi phí của một migration ngoài kế hoạch hoặc một endpoint đấu nối dở dang thì cao.

## Quy tắc cấu trúc tài liệu

Toàn bộ tài liệu backend refactor nằm dưới [`docs/refactor-wecms/`](../docs/refactor-wecms/). Không bao giờ ghi tài liệu refactor dưới `docs/jex/` — cây đó là tài liệu tham chiếu chức năng **chỉ-đọc** cho hệ thống JEX đang chạy thật.

| Loại tài liệu | Đường dẫn |
|---|---|
| Thiết kế backend API (controllers, DTOs, services, repositories, validation, errors) | [docs/refactor-wecms/backend/](../docs/refactor-wecms/backend/) |
| Ánh xạ service JEX → Spring (WSVC/IDO nào ánh xạ tới controller/service nào, theo từng trường) | [docs/refactor-wecms/migration/](../docs/refactor-wecms/migration/) |
| Thiết kế schema DB + ghi chú Flyway migration | [docs/refactor-wecms/database/](../docs/refactor-wecms/database/) |
| Audit đối chiếu DB (refactor vs JEX) | [docs/refactor-wecms/audits/](../docs/refactor-wecms/audits/) |
| Đặc tả tính năng refactor (kebab-case phải khớp với đối tác JEX) | [docs/refactor-wecms/features/](../docs/refactor-wecms/features/) |
| Kế hoạch sprint / task | [docs/refactor-wecms/planning/](../docs/refactor-wecms/planning/) |
| Kiến trúc hệ thống (xuyên tính năng) | [docs/refactor-wecms/architecture/](../docs/refactor-wecms/architecture/) |
| Kế hoạch test phía refactor | [docs/refactor-wecms/testing/](../docs/refactor-wecms/testing/) |
| Báo cáo bug (gắn tag `System: Refactor`) | [docs/bug-reports/](../docs/bug-reports/) |

**JEX là tài liệu tham chiếu chỉ-đọc.** Đọc entity, DAO, IDO XML, WSVC XML, và `_act.jsp` của JEX (dưới `WECMS DEV/`) cùng các đặc tả chuẩn dưới [docs/jex/](../docs/jex/) như là đặc tả chức năng cho bất kỳ service nào đang được dịch. **Không bao giờ triển khai trực tiếp các pattern của JEX** — refactor là viết lại sạch bằng Spring Boot dựa trên hành vi JEX, không phải port nguyên xi. Không `JexData`, không `IDODynamic`, không logic kiểu `_act.jsp`, không bắt chước envelope `getInput()/getOutput()` bên trong code Java.

**Truy vết đặt tên:** khi một tính năng tồn tại ở cả hai phía, đặc tả JEX tại `docs/jex/features/<slug>.md` và đặc tả refactor tại `docs/refactor-wecms/features/<slug>.md` phải dùng chung một `<slug>` kebab-case.

## Tổng quan dự án

- **Hiện đang chạy được:** đăng nhập (`POST /api/v1/auth/login`) với phân giải multi-company hai bước (tự chọn cho user một-liên-kết; trả về màn hình chọn công ty cho user có ≥ 2 liên kết active), me (`GET /api/v1/auth/me`), logout stub (`POST /api/v1/auth/logout`), catch-all bảo vệ bằng JWT cho mọi endpoint khác, schema do Flyway quản lý, nâng cấp mật khẩu trong suốt từ SHA-256 cũ → BCrypt ở lần đăng nhập thành công đầu tiên.
- **Module đã triển khai:** chỉ `domain/auth/`. Mọi module JEX khác (`pymn`, `mbmr`, `comm`, `admn`, …) chưa được triển khai.
- **Mô hình vận hành: một user = một tài khoản công ty.** Đây là **luồng nghiệp vụ chính được hỗ trợ** cho vận hành WeCMS hiện tại. Một `USER_ID` được kỳ vọng thuộc về đúng một `BIZ_KEY` active thông qua `USER_BUSI_CONN_INFM`. Đăng nhập phân giải liên kết duy nhất đó, phát một JWT giới hạn theo nó, và user vào thẳng dashboard **không có màn hình chọn công ty**. WeCMS cũ và vận hành WeCMS hiện tại **không** coi một user trải rộng nhiều công ty là trường hợp production bình thường.
- **Liên kết multi-company là ngoại lệ / chỉ để test-demo.** Cơ chế per-user `userBusiRepo.findAllByUserIdAndDelYn(...)` + response chọn công ty (`LoginResponse.chooser(...)` + `CompanyOption[]` + UI `<CompanyChooser>`) của refactor được giữ lại như một **fallback chung an toàn** để user lỡ có hai liên kết active trở lên không bị lỗi — nhưng fallback đó **không** phải là sự xác nhận pattern multi-company như một yêu cầu nghiệp vụ có tài liệu. Hãy coi nhánh chooser là một đường ngoại lệ; không thiết kế lại logic domain quanh nó. Nếu/khi một quy tắc nghiệp vụ tương lai thực sự đưa vào user multi-company, điều khoản này nên được thay bằng yêu cầu rõ ràng.
- **Cơ chế phân giải tenant:** tenant được phân giải theo từng user khi đăng nhập từ `USER_BUSI_CONN_INFM`; JWT mang các claim `bizKey` + `authGrCd` + `prdtType` đã chọn; `TenantContext` đọc chúng theo từng request. Seed local hiện ship hai tenant R&D: `VN_VNM0004_000001` (snapshot DEV V2 + V5 — `CÔNG TY TNHH WEBCASH VIỆT NAM`, `PRDT_TYPE = 'F'`) và `VN_VNM0004_000014` (seed V73 — `HAENGSUNG ELECTRONICS`, `PRDT_TYPE = 'H'`). V73 ban đầu thêm một liên kết hai-công-ty có bảo vệ cho `lamine@gmail.com` để có thể thử UI chooser ở local; **V76 gỡ liên kết test đó** nên ngay cả trên DB đã seed, mô hình vận hành vẫn là một-user-một-công-ty. `wecms.tenant.biz-key` giờ là một slot cấu hình **không hoạt động** — giữ lại chỉ để `TenantProperties` vẫn bind được và để `CompanyBootstrapService.assertConsistent(...)` có mục tiêu so sánh; đường đăng nhập không đi qua nó.
- **Không auto-bootstrap Spring.** Khởi động Production / UAT / local không bao giờ tạo dòng công ty, nhân viên, user, liên kết hay admin. Việc auto-invoke `BootstrapRunner` trước đây đã bị gỡ. Thực hành vận hành là **SQL thủ công do DBA chạy** theo pattern setup cũ (xem `Claude Code (AI)/sql/setup_uat_account_*.sql`): sinh `BIZ_KEY` qua `wb_get_next_val('BUSI_INFM','biz_key',NTNL_CD,CHNL_ID)`, chèn `BUSI_INFM`, sinh `EMPL_NO` qua `wb_get_next_val('EMPL_INFM','empl_no',BIZ_KEY)`, rồi chèn `EMPL_INFM` → `USER_INFM` → `BUSI_ADMN_INFM` → `USER_BUSI_CONN_INFM`. **Script cũ đó nhắm vào schema JEX cũ** (có sẵn hàm DB `encrypt(...)` + `hash(...)`, cột audit kiểu `varchar(14)`) và dành cho DEV / UAT / PROD. Nó **không** phải là migration chạy trực tiếp được trên DB local của refactor — DB local dùng cột audit PostgreSQL native `TIMESTAMPTZ` / `DATE` và không cài `encrypt()` / `hash()` (theo quy tắc "DB Function / Legacy Function Policy" — refactor không thêm hàm PL/pgSQL cũ mới). Việc tạo tài khoản R&D local đi qua Flyway seed, không qua script thủ công. **Tài liệu vận hành chuẩn:** [docs/refactor-wecms/database/manual-tenant-setup.md](../docs/refactor-wecms/database/manual-tenant-setup.md) — luồng SQL từng-bước đầy đủ, cả biến thể legacy-UAT lẫn refactor-LOCAL, các truy vấn kiểm chứng, và các giới hạn đã biết.
- **Các bootstrap service (`CompanyBootstrapService`, `UserBootstrapService`, `BusinessKeyGeneratorService`, `EmployeeNumberGeneratorService`) được giữ nhưng bất hoạt.** Chúng không có điểm auto-invoke; tồn tại như một bộ khung gọi được cho (a) unit test và (b) một admin API Phase 2 khả dĩ trong tương lai. Không gọi từ đường code production.
- **Mô hình thẩm quyền:** `BUSI_ADMN_INFM` là nguồn thẩm quyền theo từng (BIZ_KEY, USER_ID) — có dòng nghĩa là ADMIN của công ty đó, không có dòng nghĩa là USER. `USER_BUSI_CONN_INFM` cũ không mang cột `AUTH_GR_CD` (đã xác nhận trên DEV 2026-05-11), nên bảng admin-master là nguồn chuẩn. Cùng một user có thể là ADMIN ở công ty này và USER ở công ty khác. JWT mang `authGrCd`; `JwtAuthenticationFilter` nạp authorities của Spring Security (`ROLE_ADMIN` / `ROLE_USER`). Việc gate bằng `@PreAuthorize` cho các endpoint chỉ-admin là một MR sau (phần đấu nối đã sẵn sàng; chưa endpoint nào được annotate).
- **Product type:** `BUSI_INFM.PRDT_TYPE` (mã cũ một-ký-tự) được đóng dấu vào JWT khi finalize đăng nhập và lộ ra qua `TenantContext.currentPrdtType()`. Refactor hiện công nhận rõ ba chữ cái — `F` (WeCMS mặc định, dùng bởi `VN_VNM0004_000001`), `M` (WeCMS Lumens), và `H` (WeCMS HAENGSUNG Electronics, dùng bởi `VN_VNM0004_000014`) — qua `common/enums/PrdtType`. 11 chữ cái cũ còn lại (`W` / `R` / `B` / `E` / `U` / `L` / `S` / `P` / `C` / `D` / `T`) được mang nguyên văn trên wire và trong JWT nhưng cố ý không mô hình hóa — một hằng số chỉ vào `PrdtType` khi có một consumer thật cho chữ cái đó. Các gate hành vi theo từng product (bố cục payment-table-2 của HAENGSUNG, masking tài khoản cho bank `11704020`, ẩn cột file expense, gating báo cáo daily-financial-HS; gate e-Invoice cho WeCMS Default; override readonly của Lumens) đều **bị hoãn** và port theo từng MR tính năng. Giai đoạn này chỉ làm hạ tầng.
- **Mục tiêu deploy:** chỉ laptop lập trình viên. Chạy với một instance PostgreSQL local do lập trình viên sở hữu.

## Tech Stack

| Tầng | Lựa chọn |
|---|---|
| Runtime | Java 21 (Eclipse Temurin, Gradle toolchain) |
| Framework | Spring Boot 3.5.13 |
| Build | Gradle (Groovy DSL) — `./gradlew build`, `./gradlew bootRun`, `./gradlew test` |
| Web | spring-boot-starter-web |
| Persistence | spring-boot-starter-data-jpa + Hibernate, PostgreSQL driver |
| Migrations | **Flyway** (`flyway-core` + `flyway-database-postgresql`), `src/main/resources/db/migration/` |
| Security | spring-boot-starter-security + **jjwt 0.12.6** (HS256/HS512) |
| Validation | spring-boot-starter-validation + Jakarta Bean Validation |
| Boilerplate | Lombok (compile-only + annotation processor) |
| Dev UX | spring-boot-devtools |
| Tests | JUnit 5 + spring-boot-starter-test + spring-security-test |
| Excel | **Apache POI 5.x** (`poi-ooxml`) — dùng bởi `common/export/ExcelWorkbookWriter` (SXSSF streaming) cho tính năng export XLSX dùng chung. Frontend không bao giờ mang thư viện XLSX; backend là nơi sản xuất duy nhất. |

**Hiện KHÔNG có trong `build.gradle`** (đừng giả định là có): SpringDoc OpenAPI, Testcontainers. Thêm chúng vào sprint mà tính năng đầu tiên thực sự cần.

**Entry point:** `com.kosign.wecms.WecmsApiMainApplication`
**Group / artifact / version:** `com.kosign.wecms` / `wecms-api-main` / `0.0.1-SNAPSHOT`
**VCS:** Git — `kosign-wecms/wecms-api-main` trên GitLab.

## Chuẩn đặt tên API & Serialization

> **Chỉ thị CTO (2026-05-08):** đặt tên kiểu JEX cũ đã bị loại bỏ. Hình dạng wire là lowercase snake_case; envelope là `status` + `data`; mọi lỗi mang một id truy vết được. Không đưa vào, sao chép, hay khôi phục các pattern cũ.

### Wire JSON

- **Tên property: lowercase snake_case.** `user_id`, `user_nm`, `access_token`, `bank_cd`, `field_id`, `total_count`, `banks_available`, `disp_ord`. Không UPPERCASE, không camelCase, không kebab-case.
- **Envelope: `{ status, data, timestamp?, path?, trace_id? }`.** Thành công chỉ ship `{ status, data }`; lỗi thêm `timestamp`, `path`, `trace_id` qua `@JsonInclude(NON_NULL)`. Không bao giờ dùng `COMMON_HEAD` / `REC` — envelope đó đã bị loại bỏ.

```json
// success
{ "status": { "error": false, "code": "WBI0000", "message": "Success" }, "data": { ... } }

// error
{
  "status":    { "error": true, "code": "WBE1009", "message": "ID or password does not match." },
  "timestamp": "2026-05-08T14:46:49Z",
  "path":      "/api/v1/auth/login",
  "trace_id":  "20260508144649PT06592776769567DEVL"
}
```

### Ánh xạ trường Java → wire

- Trường DTO Java giữ **camelCase** (`expiresIn`, `accountNo`, `userNm`).
- Việc chuyển sang snake_case được áp dụng **toàn cục** bởi [`JacksonConfig`](src/main/java/com/kosign/wecms/config/JacksonConfig.java), thiết lập `PropertyNamingStrategies.SNAKE_CASE` lên `ObjectMapper` được auto-config. Không `@JsonNaming` cho từng DTO. Không `@JsonProperty` cho từng trường "cho chắc" — strategy đã làm đúng việc đó rồi.
- `@JsonProperty` chỉ dành riêng cho tên wire hiếm hoi khác với tên Java đã snake-case hóa. Ví dụ chuẩn duy nhất: `LoginResponse.accessToken` mang `@JsonProperty("access_token")` vì kiểu OAuth được ưu tiên hơn `access_token` snake thuần. Thêm bất kỳ `@JsonProperty` mới nào trong code review đều cần lời biện minh viết ra trong MR.
- DTO dùng Java **record**. Validation nằm trên các record component (`@NotBlank`, `@Size`, `@Pattern`).
- `Map<String, Object>` không được lọt khỏi `service/` vào một controller response — hãy trả về một DTO có kiểu. Các dòng `Map<String,Object>` cố ý của pipeline export (khóa theo FIELD_ID, trong `common/export/`) là nội bộ workbook và không bao giờ được serialize thành JSON.

### Bị cấm (bắt tại review)

- `@JsonProperty("USER_ID")` / `@JsonProperty("ACCESS_TOKEN")` / bất kỳ `@JsonProperty` nào có giá trị UPPERCASE.
- `Map.of("CODE", ..., "MESSAGE", ...)` trả về từ một controller — dùng `BusinessException` + envelope chuẩn.
- Một factory `CommonHead` / `ApiResponse` mới phát ra bất cứ thứ gì khác hình dạng `{ status, data, timestamp?, path?, trace_id? }`.
- Đưa lại envelope `COMMON_HEAD` / `REC` (đã loại bỏ).
- Serializer / deserializer tùy chỉnh thêm để "khớp JEX cũ" — không có bề mặt tương thích JEX nào trong repo này.

### Xử lý lỗi — mọi envelope lỗi PHẢI mang

| Trường | Nguồn | Ghi chú |
|---|---|---|
| `status.error` | `true` | Cờ boolean — tín hiệu thành công/thất bại chính của frontend |
| `status.code` | `WBExxxx` / `WBIxxxx` | Thêm vào [messages.properties](src/main/resources/messages.properties) trước khi throw |
| `status.message` | đã localize | Phân giải qua `MessageResolver` từ `Accept-Language` của user |
| `timestamp` | `Instant.now()` | ISO-8601, UTC |
| `path` | `req.getRequestURI()` | Endpoint đã lỗi |
| `trace_id` | [`TraceId`](src/main/java/com/kosign/wecms/common/exception/TraceId.java) qua MDC | id kiểu JEX grep được trong server log và header response `X-Trace-Id` |

**Không bao giờ:**
- Trả về một mã `UNKNOWN` chung chung không ngữ cảnh. Dùng `WBE9999` từ handler catch-all — nó luôn log full stack trace ở mức `error` cùng `traceId` theo phạm vi request.
- Nuốt một exception trong controller / service. Nếu thật sự cần phục hồi, hãy log ở mức `warn` với exception gốc làm nguyên nhân.
- Trả về stack trace hoặc hostname nội bộ trong `status.message`. Phần body của response là hướng tới người dùng.
- Trả về HTTP 200 cho một lỗi server bất ngờ. Kết quả nghiệp vụ (BusinessException) → 200; lỗi do client → 4xx; lỗi server → 5xx. Bảng đầy đủ ở "Ánh xạ exception → response" bên dưới.

### Quy tắc refactoring — khi port một module JEX

1. **Bỏ mọi `@JsonProperty("UPPER_SNAKE")`.** Tên trường Java + strategy SNAKE_CASE toàn cục là đủ. Chỉ thêm lại `@JsonProperty` khi tên wire thật sự khác với tên trường Java.
2. **Thay mọi cấu trúc kiểu `COMMON_HEAD` / `REC`** bằng `ApiResponse.ok(data)` / `ApiResponse.error(code, message, path, traceId)`. Không định nghĩa một envelope song song.
3. **Thay các controller trả về `Map<String, Object>`** bằng một DTO record Java có kiểu.
4. **Dịch mã ML của JEX (`P00001` v.v.) sang `WBExxxx` / `WBIxxxx`.** Không tái dùng mã JEX nguyên văn — thêm mã mới vào `messages.properties` và tham chiếu mã cũ trong tài liệu migration.
5. **Đối chiếu entity / DAO / IDO XML / WSVC XML / `_act.jsp` của tính năng mới với PROD Active Table List** (mục "PROD DB Table Scope for Refactoring") trước khi viết bất kỳ Flyway nào. Nếu một bảng được tham chiếu không có trong danh sách active, nó không được tạo entity.
6. **Cập nhật wire type + mapper frontend tương ứng trong cùng MR.** Đây là quy tắc full-stack ownership từ [CLAUDE.md](../CLAUDE.md) gốc của workspace. Các wire type ở repo frontend trong [lib/types/api.ts](../wecms-web-main/lib/types/api.ts) phải đổi sang lowercase ngay khi DTO của bạn đổi.

### Ngoại lệ (không phải quy tắc) — schema DB + `@Column` JPA

**Schema database** giữ UPPER_SNAKE mãi mãi — các tên cột như `BIZ_KEY`, `CMPN_NM`, `BUSI_INFM` phản chiếu schema JEX cũ theo từng cột một để dữ liệu copy sạch (xem "Legacy Schema Parity Rule" bên dưới). Các annotation JPA `@Column(name = "UPPER_SNAKE")` trên entity là đúng và giữ nguyên. Quy ước đặt tên ở trên chỉ chi phối **hình dạng wire JSON**, không chi phối SQL.

### Đặt tên định danh refactor — dùng slug, không dùng id WSVC

Các id WSVC / menu của JEX cũ (`comm_0220_001`, `fnsh_0001_001`, `pymn_0010_002`, …) là sản phẩm của kiến trúc JSP. **Trong code Spring, dùng định danh slug kebab-case — không bao giờ dùng id WSVC cũ.** Tóm tắt cross-tree: [CLAUDE.md](../CLAUDE.md) workspace §"Cross-Tree Rules → Refactor work".

| Bề mặt | Dạng slug (đúng) | id WSVC cũ (cấm ở bề mặt này) |
|---|---|---|
| REST URI | `/api/v1/settings/firm-banking-settings/...` | ❌ không bao giờ `/api/v1/comm_0220_001/...` |
| Java package | `com.kosign.wecms.domain.setting.firmbankingsettings` | ❌ không bao giờ `com.kosign.wecms.domain.comm.comm_0220` |
| Tên class | `FirmBankingSettingsController`, `FirmBankingSettingsService`, `FirmBankingSetting`, `FirmBankingSettingId` | ❌ không bao giờ `Comm0220Service`, `TbFirmConfController` |
| DTO record | `FirmBankingSettingRow`, `FirmBankingSettingCreateRequest` | ❌ không bao giờ `Comm0220R001Response` |
| Khóa message i18n | `audit.firm_banking_settings.*`, `settings.firm_banking_settings.*` | ❌ không bao giờ `audit.comm_0220_001.*` |
| Tag Log / MDC | `firm-banking-settings` | ❌ không bao giờ `comm_0220_001` |
| `AUDIT_LOGS_INFM.WSVC_ID` | slug route refactor, ví dụ `firm-banking-settings` (override 2026-05-09) | ❌ không bao giờ `comm_0220_001` cho ghi mới — V67 back-fill các dòng local còn mang giá trị cũ |
| View Setting `WEB_SVC_ID` | slug dựa trên route refactor từ registry trung tâm, ví dụ `banking-account-inquiry-vnd` (xem [wecms-web-main/lib/viewSettingScreens.ts](../wecms-web-main/lib/viewSettingScreens.ts)) | ❌ không bao giờ `fnsh_0001_001` (registry cũ loại bỏ ở V32) |

Id WSVC cũ chỉ được giữ lại trong **hai nhóm** về sau:

1. **Trích dẫn file:line của JEX** trong Javadoc / văn bản migration / ghi chú audit — ví dụ `// Mirrors comm_0220_002_c001_act.jsp:84-88` là chấp nhận được.
2. **Văn bản truy vết migration** trong `docs/refactor-wecms/migration/<slug>-mapping.md` — các đoạn tường thuật giải thích "service refactor này thay thế JEX `comm_0220_001`."

> **Nhóm hằng-số-DB-audit đã loại bỏ 2026-05-09.** Các bản sửa trước dành riêng một nhóm thứ ba để `AUDIT_LOGS_INFM.WSVC_ID` vẫn là `'comm_0220_001'` để truy vấn detail-audit của JEX cũ tại `comm_0220_003_r001_act.jsp:74-86` tìm được các dòng do refactor ghi. Quyết định đó đã bị override — các dòng audit do refactor ghi giờ dùng slug route. JEX không truy vấn DB refactor local ở đâu cả (refactor là R&D chỉ-local), nên động lực tương thích xuyên hệ thống biến mất. Một tính năng tương lai thực sự không thể tách rời nhu cầu xuyên hệ thống sẽ cần một quyết định PO mới trước khi hồi sinh nhóm này. Xem [docs/refactor-wecms/features/settings/firm-banking-settings.md §13 OQ-6](../docs/refactor-wecms/features/settings/firm-banking-settings.md) và migration back-fill V67.

Nếu bạn thấy mình định dùng một id WSVC cũ trong bất kỳ tên trường Java mới, đoạn REST URI, package, class, khóa i18n, tag log, **hoặc cột DB** nào (bao gồm `AUDIT_LOGS_INFM.WSVC_ID`), **hãy dừng lại** — đó là một bug. Dùng slug.

Các quy tắc frontend tương ứng (route Next.js, `screenId` của View Setting) nằm ở [wecms-web-main/CLAUDE.md](../wecms-web-main/CLAUDE.md) §"Routing" + §13.

## Quy tắc kiến trúc

Đóng gói domain-driven nhẹ — **KHÔNG** phải DDD chiến thuật đầy đủ. Không aggregate, value object, hay domain event trừ khi một tính năng cụ thể thực sự cần.

```
com.kosign.wecms
├── WecmsApiMainApplication.java
├── config/
│   ├── CorsConfig.java              — CorsFilter bean; reads wecms.cors.allowed-origins
│   ├── JwtProperties.java           — @ConfigurationProperties("wecms.jwt")
│   ├── SecurityConfig.java          — SecurityFilterChain + PasswordEncoder bean
│   └── TenantProperties.java        — @ConfigurationProperties("wecms.tenant"); rejects % wildcard
├── common/
│   ├── entity/BaseEntity.java       — REG_ID/REG_DTM/MOD_ID/MOD_DTM audit columns (@PrePersist/@PreUpdate)
│   ├── enums/LangCd.java            — enum {EN, KO, KM, VI}; fromAcceptLanguage(), fromCodeOrDefault()
│   ├── exception/
│   │   ├── BusinessException.java   — extends RuntimeException; holds code + Object[] args
│   │   └── GlobalExceptionHandler.java — @RestControllerAdvice; maps BusinessException,
│   │                                     MethodArgumentNotValidException, generic Exception
│   ├── i18n/MessageResolver.java    — MessageSource wrapper; resolves WBE/WBI codes via LocaleContextHolder
│   ├── response/
│   │   ├── ApiResponse.java         — `status` + `data` envelope (lowercase snake_case); static ok(T) / ok(code,T) / error(code,message)
│   │   └── CommonHead.java          — ERROR bool, CODE, MESSAGE; factories for success/error
│   ├── security/
│   │   ├── JwtTokenProvider.java    — HS256/HS512 token issue/parse; fail-fast on missing/short secret
│   │   ├── JwtAuthenticationFilter.java — OncePerRequestFilter; reads Bearer header;
│   │   │                                  sets UsernamePasswordAuthenticationToken;
│   │   │                                  populates TenantContext (userId, bizKey, langCd)
│   │   ├── WecmsPasswordEncoder.java    — hybrid: BCrypt write, BCrypt/legacy SHA-256 read
│   │   └── LegacyHashPasswordEncoder.java — replicates public.hash(txt): SHA-256 → base64
│   └── tenant/TenantContext.java    — ThreadLocal<Principal> holder; current(), currentUserId(),
│                                      currentBizKey(), currentLangCd() static accessors
└── domain/
    └── auth/                        — the only domain module implemented
        ├── bootstrap/DataSeeder.java — @Profile("local") replaces __BCRYPT_PENDING__ sentinel on startup
        ├── controller/AuthController — @RequestMapping("/api/v1/auth")
        ├── dto/
        │   ├── LoginRequest.java    — record; user_id, user_pw; @NotBlank @Size; compact() trims
        │   ├── LoginResponse.java   — record; access_token, token_type, expires_in, user_id, user_nm,
        │   │                          biz_key, lang_cd, cmpn_nm
        │   └── MeResponse.java      — record; user_id, user_nm, biz_key, lang_cd, cmpn_nm (no token)
        ├── entity/
        │   ├── BusiInfm.java        — BUSI_INFM table; PK = BIZ_KEY; 40+ columns; extends BaseEntity
        │   ├── UserInfm.java        — USER_INFM table; PK = USER_ID; isActive/isLocked/isDeleted helpers
        │   ├── UserBusiConnInfm.java — USER_BUSI_CONN_INFM; composite PK (USER_ID, BIZ_KEY)
        │   ├── UserBusiConnInfmId.java — Serializable composite key
        │   ├── UserLoginHis.java    — USER_LOGIN_HIS; composite PK (USER_ID, REG_DTM); no BaseEntity
        │   └── UserLoginHisId.java  — Serializable composite key
        ├── repository/
        │   ├── UserInfmRepository.java           — JpaRepository<UserInfm, String>
        │   ├── BusiInfmRepository.java            — JpaRepository<BusiInfm, String>
        │   ├── UserBusiConnInfmRepository.java    — + findByUserIdAndBizKeyAndDelYn()
        │   └── UserLoginHisRepository.java        — JpaRepository<UserLoginHis, UserLoginHisId>
        └── service/
            ├── AuthService.java          — login(req, ipAddr, langCd) + me()
            └── LoginHistoryService.java  — record(userId, ipAddr, rsltCd, rsltCont) @REQUIRES_NEW
```

**Quy tắc package:**
- Code tính năng mới → package `domain/{jex-module-code}/` mới (ví dụ `pymn`, `mbmr`, `comm`, `admn`).
- Không nâng helper lên `common/` cho tới khi nhiều hơn một domain dùng nó.
- Trong một domain: **controller → service → repository → entity + dto**. Không đi tắt xuyên tầng.
- Không gọi controller-tới-controller. Không truy cập repository xuyên domain.
- Logic nghiệp vụ nằm trong `service/`. Entity là ánh xạ JPA + helper boolean tầm thường, không phải object domain giàu logic.

## Endpoints

| Endpoint | Method | Auth | Trả về |
|---|---|---|---|
| `/api/v1/auth/login` | POST | Public | `ApiResponse<LoginResponse>` |
| `/api/v1/auth/logout` | POST | Public | `ApiResponse<Void>` (stub — client xóa token) |
| `/api/v1/auth/me` | GET | Protected (JWT) | `ApiResponse<MeResponse>` |
| `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | GET | Protected | `ApiResponse<ScreenOptionListResponse>` (gộp sys + user cho một area) |
| `/api/v1/screen-options/{webSvcId}/areas` | GET | Protected | `ApiResponse<ScreenAreaListResponse>` — các DYM_AREA_ID phân biệt + số cột mỗi area |
| `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | PUT | Protected | lưu override của user cho một area → trả về list gộp đã làm mới |
| `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | DELETE | Protected | reset override của user cho một area → trả về mặc định hệ thống |
| `/api/v1/banking/accounts/export` | POST | Protected | stream `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (XLSX) cho Banking > Account Inquiry — body là hình dạng `ExcelExportRequest` dùng chung (xem quy tắc common/export bên dưới) |
| `/actuator/health` | GET | Public | Spring Actuator |
| Mọi thứ khác | bất kỳ | Protected | 401 nếu không có JWT hợp lệ (qua `JwtAuthenticationEntryPoint`) |

## Quy tắc viết code

**Controllers**
- Trả về `ApiResponse<T>`. Thành công: `ApiResponse.ok(rec)`. Lỗi: throw `BusinessException("WBExxxx")`.
- Giữ controller mỏng: parse request, phân giải dữ liệu phụ trợ (IP client, `Accept-Language` → `LANG_CD`), ủy thác cho service, gói kết quả.
- DTO dùng Java **record** với **trường Java camelCase** tự serialize thành **lowercase snake_case** qua naming strategy Jackson toàn cục ([`JacksonConfig`](src/main/java/com/kosign/wecms/config/JacksonConfig.java)). `@JsonProperty` cho từng trường chỉ thêm khi tên wire khác với tên Java snake-case hóa (ví dụ `accessToken` → `access_token`). Validation trên các record component (`@NotBlank`, `@Size`, `@Pattern`).

**Services**
- Annotate các method gây thay đổi bằng `@Transactional`. Các ghi audit cần sống sót qua rollback bên ngoài dùng `@Transactional(propagation = REQUIRES_NEW)`.
- Throw `BusinessException(CODE, args…)` cho các đường lỗi đã biết. Code phân giải qua `messages*.properties`.
- Không bao giờ để lọt exception Hibernate / JDBC thô — để global handler ánh xạ các lỗi bất ngờ thành `WBE9999`.

**Repositories**
- Spring Data JPA. Ưu tiên tên method dẫn xuất hơn `@Query` cho tới khi thật sự cần một truy vấn native/JPQL.
- Một method repository trả về dòng từ một bảng tenant-scoped **phải** ràng buộc trên `BIZ_KEY`. Không bao giờ truyền `BIZ_KEY` wildcard `%`.

**Entities**
- Extend `BaseEntity` cho các bảng cũ để kế thừa cột audit.
- `@Table(name = "UPPER_SNAKE")` và `@Column(name = "UPPER_SNAKE")` — phản chiếu schema JEX cũ theo từng cột một.
- `@IdClass` cho khóa composite.
- Không JPA cascade trên quan hệ xuyên-aggregate trừ khi có nhu cầu cụ thể.

**Config / properties**
- Record `@ConfigurationProperties` có kiểu, không rải rác `@Value`. Bật qua `@EnableConfigurationProperties` trên `SecurityConfig`.
- Mọi giá trị nhạy cảm có một env var (`WECMS_DB_PASSWORD`, `WECMS_JWT_SECRET`). Fail-fast trong constructor nếu thiếu một giá trị bắt buộc.

**Lombok**
- `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j` — chỉ những thứ tầm thường. Không `@Data` trên entity. Không `@Builder` trên DTO.

**Style**
- Một class một file.
- Javadoc trên các method service public triển khai một hành vi JEX — trích dẫn đường dẫn JEX đang được port (ví dụ `SzonHelper.logn_2001_001_r001`).
- Comment giải thích TẠI SAO. Không bao giờ comment CÁI GÌ.

## Trạng thái tính năng

| Module | Endpoints | Trạng thái |
|---|---|---|
| `domain/auth` | `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` | Working |
| `domain/setting` | `GET / PUT / DELETE /api/v1/screen-options/{webSvcId}/{dymAreaId}` + `GET .../areas` (View Setting) | Working — tính năng WeCMS **global / common**, không phụ thuộc màn hình. Các endpoint nhận bất kỳ `WEB_SVC_ID` + `DYM_AREA_ID` và được tái dùng bởi mọi trang list khắp sản phẩm (Banking, Payment, Add-on, Settings, các module tương lai). `WEB_SVC_ID` theo quy ước kebab-case ngữ nghĩa (`banking-account-inquiry-vnd`, v.v.) theo [docs/jex/features/view-setting.md §1.1](../docs/jex/features/view-setting.md). Màn hình seed đầu tiên: `banking-account-inquiry-vnd` / `default` (V32). Màn hình mới thêm dòng seed của chúng trong migration tính năng của riêng chúng — không cần thay đổi controller / service / DTO. |
| `common/export` + `domain/banking/export` | `POST /api/v1/banking/accounts/export` (XLSX) | Working — tính năng WeCMS **global / common**. `ExcelExportService` + `ExportableScreenRegistry` + `ExcelWorkbookWriter` (Apache POI SXSSF) dùng chung, được tiêu thụ bởi các endpoint controller theo từng resource. Màn hình đấu nối đầu tiên: `banking-account-inquiry-vnd` (Account Inquiry VND) qua `AccountInquiryExportProvider`, tái dùng `AccountService.list` để export và list chia sẻ một đường truy vấn. Backend tính số đếm + subheader tổng. Thêm một màn hình mới = một class `ExportRowProvider` mới + một `@PostMapping` trên controller sẵn có của resource. Xem quy tắc **Excel export — `common/export/`** bên dưới. |
| `domain/setting/erptransactionhistory` | `GET /api/v1/settings/erp-transaction-history` (list + 4 tab count), `GET .../{seqNo}/download` (binary), `GET .../{seqNo}/thumbnail` (stub → `WBE9001`) | Working — sổ audit chỉ-đọc các file trao đổi giữa WeCMS và ERP của khách hàng. Phản chiếu `comm_0219_001_r001` + `comm_0219_001_r002` cũ. Tenant-scoped qua `TenantContext.currentBizKey()`; whitelist sort `ErpTrnsHistorySortField` (REG_DTM / ERP_TYPE / TRNS_TYPE_CD / REG_USER); LEFT JOIN `UserInfm` thay cho `WB_GET_USER_NM` cũ. Download stream từ `wecms.erp-transaction-history.storage-root` (mặc định `C:/WECMS PROJECT/File Data`). View Setting **KHÔNG áp dụng** (OQ-FE-2 — panel JEX cũ không có nút list-icon trên toolbar). Các khóa i18n theo tính năng (`settings.erp_transaction_history.direction.*` + `.type.*`) phát trên wire để frontend render qua `t(key)`. Đường ghi upload cố ý KHÔNG port (OQ-1) — mỗi tính năng Payment tương lai tự ghi dòng audit của nó. |
| `domain/setting/contacts` | `GET /api/v1/settings/contacts` (list), `GET /{trcoKey}` (detail), `POST /` (create), `PUT /{trcoKey}` (update), `DELETE /{trcoKey}` (soft-delete đơn), `POST /bulk-delete` (soft-delete hàng loạt) | Working — phản chiếu `comm_0184_001` + `comm_0184_002_c001` + `comm_0184_005_u001` + `comm_0184_001_u001` cũ. CRUD trên `TRCO_INFM` + `TRCO_ADDR_INFM` + `TRCO_ACCT_INFO` + `TRCO_OVRS_INFM` + `PGRO_BSAC_LNKN_INFM`. Tenant-scoped qua `TenantContext.currentBizKey()`; whitelist sort `ContactSortField` (HAND_TRCO_KEY / TRCO_CMNM / TRCO_TYPE / TRCO_TAX_ID); list phân trang + lọc với `search` + `trco_type`. **Cấp phát TRCO_KEY** đi qua `LegacyFunctionRepository.nextTrcoKey` (nhánh V85 `wb_get_next_val` + `sq_trco_key`, OQ-4); retry một lần khi `DataIntegrityViolationException`. **Phân giải tên ngân hàng** đi qua `LegacyFunctionRepository.resolveNapasOrCitadBankName` (hàm V86 đã port trên `tb_firm_bank_code`, OQ-7) — `BankCdResolver` KHÔNG được dùng trong luồng này. **Guard WBE1190 in-use** chạy trên delete đơn, bulk delete, và chuyển đổi `trco_stts → 'C'` lúc edit (OQ-2) qua `AcctTrnsRcvPayInfmRepository.existsByBizKeyAndRcvPayTrcoKey`. **OQ-3** giữ `TRCO_INQ_YN` được ánh xạ + mặc định `'Y'` khi create (nội bộ service; không bao giờ lên wire). **OQ-5** không có Trash UI — truy vấn list hard-code `coalesce(trco_stts,'') <> 'C'`. **OQ-6** slug audit là `contacts`. **OQ-8** bộ phân biệt địa chỉ hiện-tại/trước-đó là `disp_ord` ∈ {1, 2}, KHÔNG phải `trco_addr_key`. **View Setting** áp dụng qua seed V88 (`web_svc_id = 'contacts'`, 8 cột gồm cả special CHECKBOX + ETC). |
| `domain/setting/approvalline` | `GET /api/v1/settings/approval-line` (list + tìm theo tên + sort), `GET /{apprLineNo}` (detail), `POST /` (create), `PUT /{apprLineNo}` (update + diff-sync), `DELETE /{apprLineNo}` (hard delete) | Working — phản chiếu `comm_0222_001_r001` + `comm_0222_002_c001` + `comm_0222_003_r001` + `comm_0222_003_u001` + `comm_0222_001_d001` cũ (slug refactor `approval-line`). CRUD master + con trên `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL` (V90). Tenant-scoped qua `TenantContext.currentBizKey()`; whitelist sort `ApprovalLineSortField` (REG_DTM / APPR_LINE_NM / MOD_DTM) với mặc định `REG_DTM DESC, APPR_LINE_NO DESC`. Không phân trang phía server (OQ-3, 2026-05-13 — load-all khớp legacy). **Cấp phát APPR_LINE_NO** đi qua `LegacyFunctionRepository.nextApprLineNo` (nhánh V91 `wb_get_next_val`, OQ-7). **Giới hạn 5 người duyệt** ghim trong `ApprovalLineConstants.MAX_APPROVERS` + cột DB `NUMERIC(1,0)` + bean validation + guard service → `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`. **Diff-sync khi Update** phản chiếu `comm_0222_003_u001_act.jsp:104-194` cũ — UPDATE các dòng trong `existing ∩ incoming`, INSERT phần mới, DELETE phần bị bỏ, tất cả trong một tx. **`apprvr_disp_ord` được chuẩn hóa lại 1..N ở cả Create LẪN Update** (OQ-4 đề xuất 2026-05-13; chặt hơn legacy Create vốn tin thứ tự client). **Guard phòng vệ:** mọi `apprvr_id` trong request được kiểm chứng với `USER_BUSI_CONN_INFM.findAllByUserIdInAndBizKeyAndDelYn` → `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` (legacy không có guard này). **OQ-5** không có gate admin `@PreAuthorize` — theo hành vi JEX đang chạy (user thường bất kỳ). **OQ-6** không ghi audit-log (parity với legacy — JEX `comm_0222_*` cũng không ghi dòng `AUDIT_LOGS_INFM`; cố ý khác với `firm-banking-settings` / `contacts`). **`user_img_url`** phân giải theo từng người duyệt qua `USER_BUSI_CONN_INFM.USER_IMG`; định dạng slug-based `/api/v1/users/{apprvrId}/image` (bản thân endpoint ngoài phạm vi, OQ-1) — null khi không có ảnh. **View Setting KHÔNG áp dụng** — panel cũ không có nút list-icon trên toolbar (cùng tiền lệ với `erp-transaction-history`). |
| `domain/setting/chartofaccounts` | `GET /api/v1/settings/chart-of-accounts` (list + search + sort), `GET /tree` (projection chọn cha), `GET /{chart_acct_seq_no}` (detail), `POST /` (create / revive), `PUT /{chart_acct_seq_no}` (update), `DELETE /{chart_acct_seq_no}` (soft delete) | Working — phản chiếu `comm_0223_001_r001` / `_r002` / `_c001` / `_u001` / `_d001` cũ (slug refactor `chart-of-accounts`). CRUD đầy đủ trên `CHART_ACCT_INFM` (V94, 15 cột). Tenant-scoped qua `TenantContext.currentBizKey()`; whitelist sort `ChartOfAccountsSortField` (CHART_ACCT_CD / CHART_ACCT_NM_EN / STTS / MOD_DTM) với mặc định `CHART_ACCT_CD ASC` (parity với legacy R001). Không phân trang phía server (load-all khớp legacy). **Cấp phát CHART_ACCT_SEQ_NO** đi qua `LegacyFunctionRepository.nextChartAcctSeqNo` (nhánh V95 `wb_get_next_val`). MAX+1 theo từng tenant; retry một lần khi `DataIntegrityViolationException`. **Các root hạn chế (ACC-01 / ACC-02 / ACC-03)** được seed cho mọi tenant ở V96; bị chặn edit / delete qua `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` (parity với `WBE1178` cũ). **Giới hạn phân cấp 4 mức** bắt buộc lúc ghi bởi `ChartOfAccountsValidator.assertDepthCap` (bổ sung của refactor — legacy chỉ nướng giới hạn vào truy vấn đọc R002). **Guard chu trình + tự-làm-cha** thêm lúc ghi (bổ sung của refactor — seq 104 trên DEV cũ tự tham chiếu; validator bắt các ghi mới, dữ liệu cũ được grandfather hóa render với `chart_acct_level=null`). **Revive-on-create** cho các dòng soft-delete cùng mã giữ nguyên `chart_acct_seq_no` gốc; **archive-rename-on-delete** (`prefix(0..4) + last5(seq_no) + 'D'`) bảo toàn UK `(BIZ_KEY, CHART_ACCT_CD, DEL_YN='Y')` khi va chạm. **Re-parent con khi delete** gộp bản vá parent-null `comm_0223_001_u002` cũ qua một `@Modifying @Query` (`reparentActiveChildren`). **`ChartOfAccountsTreeBuilder`** chuyển projection CASE 100 dòng của R002 cũ sang Java — backend ship `chart_acct_level` / `has_children` / `restricted` / `breadcrumb` trên mỗi dòng. **OQ-1** (gating role USER) — chờ PO; mặc định ship không `@PreAuthorize`. **OQ-3** (nhánh dọn-cha `u001:237-256` cũ) — BỎ (khuyến nghị refactor; phần dọn dẹp trông như instrumentation debug còn sót, chờ PO xác nhận theo memory `project_chart_of_accounts_oq_pending`). **Audit-log** bật: `AUDIT_LOGS_INFM.WSVC_ID = "chart-of-accounts"` (slug, không phải `comm_0223_001`); khớp tiền lệ firm-banking-settings / contacts. **View Setting KHÔNG áp dụng** — panel cũ không có nút list-icon trên toolbar (cùng tiền lệ với approval-line / erp-transaction-history); slug được giữ chỗ trong `SCREEN_IDS` của frontend cho việc đấu nối popover tương lai + nhất quán log/audit. |
| Mọi thứ khác | — | **Chưa bắt đầu.** `domain/payment`, `domain/member`, `domain/banking`, … |

Bị hoãn bên trong `auth` (cố ý ngoài phạm vi Sprint 0): bộ đếm auto-lock 5 lần thất bại, reset lịch sử đổi mật khẩu, các gate sau-auth `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS`, phân giải menu sau-đăng-nhập, refresh token, JWT blacklist / logout phía server.

## Quy tắc Auth

### Luồng login (`AuthService.login`)

Luồng multi-company hai bước. `LoginRequest` mang một `biz_key` tùy chọn; khi bỏ trống, backend có thể tự chọn (user một-liên-kết) hoặc trả về response chọn công ty (user nhiều-liên-kết).

1. Load `USER_INFM` theo `USER_ID`. Thiếu → `WBE1009` + dòng FAIL trong `USER_LOGIN_HIS`.
2. Các gate cờ, theo thứ tự: `DEL_YN=Y` → `WBE1128`, `ACTV_YN=N` → `WBE1010`, `LOCK_YN=Y` → `WBE1011`.
3. `passwordEncoder.matches(rawPassword, storedHash)`. Không khớp → `WBE1009`.
4. Nếu `passwordEncoder.upgradeEncoding(storedHash)` (giá trị lưu không phải BCrypt), mã hóa lại và lưu.
5. Truy vấn **tất cả** liên kết active: `userBusiRepo.findAllByUserIdAndDelYn(userId, "N")`. Không có dòng nào → `WBE1009` (user chưa được cấp phép ở bất kỳ công ty nào).
6. **Phân giải tenant.** Ba nhánh:
   - **Một liên kết.** Tự chọn dòng đó. Nếu request có cung cấp `biz_key` mà không khớp → `WBE1009`.
   - **Nhiều liên kết, không có `biz_key` trong request.** Dựng response chọn công ty (`LoginResponse.chooser(...)`): một `CompanyOption(biz_key, cmpn_nm, auth_gr_cd)` cho mỗi liên kết active, sắp theo tên công ty; thẩm quyền mỗi option phân giải từ `BUSI_ADMN_INFM`. Trả về ngay — **không phát JWT, không ghi `LST_LGN_DATE`, không có dòng audit SUCCESS**.
   - **Nhiều liên kết, có `biz_key` trong request.** Tìm liên kết có `bizKey` khớp. Không biết / không khớp → `WBE1009`.
7. Cập nhật `LST_LGN_DATE` trên liên kết đã phân giải.
8. Ghi một dòng SUCCESS vào `USER_LOGIN_HIS`.
9. Tra `BUSI_INFM` cho công ty đã phân giải — một truy vấn, dùng cho cả tên hiển thị `CMPN_NM` lẫn chữ cái `PRDT_TYPE` nguyên văn.
10. Phân giải thẩm quyền cho công ty đã chọn: `busiAdmnRepo.existsByBizKeyAndUserId(bizKey, userId)` → `"ADMIN"` nếu có, ngược lại `"USER"`.
11. Phát JWT (`access_token`, `token_type`, `expires_in`) mang các claim `{ sub: userId, userNm, bizKey, langCd, authGrCd, prdtType? }` (claim `prdtType` được bỏ khi dòng công ty không mang giá trị — không bao giờ âm thầm mặc định thành `"F"`) và trả về `LoginResponse.authenticated(...)` với các trường tenant chuẩn + `auth_gr_cd` + `prdt_type`.

Response chọn công ty (user nhiều-liên-kết, không có `biz_key` trong request) đóng dấu `prdt_type` theo từng công ty vào mỗi `CompanyOption` để frontend hiển thị nhãn product cạnh mỗi option. `prdt_type` ở cấp cao nhất là null trên hình dạng chooser — product type chỉ bind khi một JWT được phát.

### Luồng me (`AuthService.me`)

1. Đọc `TenantContext.current()` — được nạp bởi `JwtAuthenticationFilter` từ JWT đã kiểm chứng.
2. Xác minh user vẫn đủ điều kiện (không deleted / inactive / locked trong `USER_INFM`).
3. Xác minh liên kết tới tenant **gắn-với-token** vẫn tồn tại (`USER_BUSI_CONN_INFM` với `DEL_YN='N'`).
4. Lấy `BUSI_INFM.CMPN_NM`.
5. Trả về `MeResponse(USER_ID, USER_NM, BIZ_KEY, LANG_CD, CMPN_NM, AUTH_GR_CD, PRDT_TYPE)` — `authGrCd` và `prdtType` lặp lại từ claim JWT (đã ký; không truy vấn lại).

### JWT (`JwtTokenProvider`)
- Secret: `wecms.jwt.secret` (env var `WECMS_JWT_SECRET`). Tối thiểu 32 byte → HS256; ≥ 64 byte → HS512 (ưu tiên). Fail-fast khi thiếu/quá-ngắn secret.
- Claims: `sub = USER_ID`, `userNm`, `bizKey`, `langCd`, `authGrCd` (`"ADMIN"` hoặc `"USER"` — đóng dấu lúc finalize đăng nhập từ `BUSI_ADMN_INFM`), `prdtType` (chữ cái `BUSI_INFM.PRDT_TYPE` nguyên văn — bỏ khi dòng không mang giá trị). Vòng đời lấy từ `wecms.jwt.expires-in-seconds` (mặc định 3600 s).
- **Stateless**: không refresh token, không blacklist phía server. Thay đổi thẩm quyền / product-type (sửa dòng trong `BUSI_ADMN_INFM` hoặc `BUSI_INFM.PRDT_TYPE`) có hiệu lực ở lần đăng nhập kế tiếp — các claim của JWT đang lưu hành đã ký và bất biến.

### JWT filter (`JwtAuthenticationFilter`)
- Chạy trước `UsernamePasswordAuthenticationFilter`. Đọc `Authorization: Bearer <token>`, parse, và:
  - Thiết lập `UsernamePasswordAuthenticationToken` với `principal = userId` và `authorities = [ROLE_ADMIN | ROLE_USER]` dẫn xuất từ claim `authGrCd` (mang tính tư vấn cho tới khi `@PreAuthorize` xuất hiện).
  - Nạp `TenantContext` với `(userId, userNm, bizKey, langCd, authGrCd, prdtType)` trong suốt request. `prdtType` là chữ cái DB nguyên văn và có thể null khi dòng tenant phát hành không có giá trị.
- Token không hợp lệ → xóa context, để `JwtAuthenticationEntryPoint` trả về **401** với envelope `ApiResponse` chuẩn (`status.code = WBE1009`). Axios response interceptor của frontend theo dõi 401 để hiện dialog re-auth phiên-hết-hạn toàn cục.
- **Không bao giờ tin `biz_key` hay `prdt_type` do frontend gửi ngoài endpoint login.** Các service domain đọc công ty active và product type độc quyền từ `TenantContext.currentBizKey()` / `TenantContext.currentPrdtType()` (lấy từ JWT đã ký). Một request body hay query param không thể override tenant hay product type mà một JWT đã bind. `productType` phía frontend trong `StoredUser` chỉ dùng để hiển thị UI + gating — không bao giờ là ranh giới bảo mật.

### Quy tắc product-type

`BUSI_INFM.PRDT_TYPE` là mã cũ một-ký-tự đánh dấu biến thể product mà một tenant chạy (WeCMS Default, WeCMS Lumens, HAENGSUNG Electronics, cộng 11 biến thể WABOOKS bị hoãn — xem "Tổng quan dự án"). Enum `common/enums/PrdtType` của refactor chỉ liệt kê các giá trị có consumer thật trong refactor hôm nay: `F`, `M`, `H`. Các chữ cái khác round-trip nguyên văn qua wire / JWT / `TenantContext` nhưng `PrdtType.fromCode(...)` trả về `Optional.empty()` cho chúng để các gate degrade an toàn.

**Quy tắc cho code mới rẽ nhánh theo product type:**

- **Đọc từ `TenantContext.currentPrdtType()`** — không bao giờ từ request body, query param, header, hay `StoredUser.productType` của frontend. JWT đã ký là nguồn chuẩn duy nhất.
- **Dùng `PrdtType.fromCode(...)` để phân giải enum** — không bao giờ so với chuỗi literal `"F"` / `"M"` / `"H"`. Lỗi gõ và chữ cái lạ sẽ fail-closed thay vì âm thầm kích hoạt sai gate.
- **Null là một giá trị thật.** Một dòng tenant có `PRDT_TYPE = NULL` ship null qua toàn bộ pipeline (không thay thế mặc định). Các gate cần một giá trị phải hoặc fallback rõ ràng hoặc từ chối request với một mã lỗi có tài liệu.
- **Thêm một hằng số cần một consumer thật.** Đừng pre-populate enum với 11 chữ cái WABOOKS bị hoãn một cách suy đoán; thêm chúng trong MR port gate hành vi đầu tiên của chúng.

**Hành vi theo từng product bị hoãn (port theo từng MR tính năng, không phải cấp hạ tầng):**

| Product | Hành vi bị hoãn | Xuất hiện cùng |
|---|---|---|
| `F` | Gate tính năng e-Invoice | MR Add-on e-Invoice |
| `M` | Override trường readonly | Form đầu tiên có quy tắc readonly khác cho Lumens |
| `H` | Bố cục payment table-2, masking tài khoản cho bank `11704020`, ẩn cột file expense, gating báo cáo daily-financial-HS | Mỗi cái port cùng module của nó (Payment, Banking Transfer, Add-on Expense, Reports) |

### Chuỗi security filter (`SecurityConfig`)
- CSRF tắt, CORS bật qua bean `CorsConfig`, session `STATELESS`.
- Public: `/api/v1/auth/login`, `/api/v1/auth/logout`, `/actuator/health`. **Mọi thứ khác đều cần xác thực.**

### Mật khẩu (`WecmsPasswordEncoder` + `LegacyHashPasswordEncoder`)
- Ghi → BCrypt strength 10.
- Đọc → chấp nhận BCrypt (`$2a$`/`$2b$`/`$2y$`) **hoặc** output `public.hash()` JEX cũ (Base64(SHA-256(utf8(raw))), luôn 44 ký tự).
- `upgradeEncoding()` trả về true cho bất kỳ hash không-phải-BCrypt → `AuthService` nâng cấp trong suốt khi đăng nhập thành công.

## Quy tắc i18n

- Spring `MessageSource` (basename `messages`, UTF-8). File: `messages.properties` (EN), `messages_en.properties`. Thêm `messages_ko.properties`, `messages_vi.properties`, `messages_km.properties` khi bản dịch thật xuất hiện.
- Khóa là các mã WBE/WBI (ví dụ `WBE1009=ID or password does not match.`). Phân giải qua `MessageResolver.resolve(code, args…)`.
- Locale phân giải ở controller: `Accept-Language: ko` → `KO`, `vi` → `VI`, `km` → `KM`, mặc định `EN`. Nhúng trong claim JWT để code phía sau đọc nó từ `TenantContext` thay vì parse lại header.
- Dùng enum `LangCd` (`common/enums/LangCd.java`) cho mọi việc parse Accept-Language — `LangCd.fromAcceptLanguage(header)` và `LangCd.fromCodeOrDefault(code)`.
- **KHÔNG** tái dùng các ID mã ML của JEX nguyên văn — dịch sang `WBExxxx`/`WBIxxxx` và ghi vào `messages*.properties`.

## Quy tắc API / DB

### Response envelope — mọi endpoint

```json
// Success
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data":   { ... }
}

// Error
{
  "status":    { "error": true, "code": "WBE1009", "message": "ID or password does not match." },
  "timestamp": "2026-05-08T14:46:49Z",
  "path":      "/api/v1/auth/login",
  "trace_id":  "20260508144649PT06592776769567DEVL"
}
```

- **Quy ước wire: lowercase snake_case** — áp dụng toàn cục bởi [`JacksonConfig`](src/main/java/com/kosign/wecms/config/JacksonConfig.java) thiết lập `PropertyNamingStrategies.SNAKE_CASE` lên `ObjectMapper` auto-config. Trường DTO Java giữ camelCase (`expiresIn`, `accountNo`) và Jackson chuyển sang snake (`expires_in`, `account_no`) khi serialize / deserialize.
- `@JsonProperty` cho từng trường chỉ dành riêng cho tên wire hiếm hoi khác với trường Java — ví dụ `LoginResponse.accessToken` annotate `@JsonProperty("access_token")` (kiểu OAuth) thay vì snake nghiêm ngặt `access_token`. Đừng thêm `@JsonProperty` "cho chắc" khi strategy đã tạo ra tên đúng.
- Thành công: `ApiResponse.ok(data)` → `error=false`, `code=WBI0000`. `@JsonInclude(NON_NULL)` bỏ `data` (và bộ ba truy vết `timestamp` / `path` / `trace_id`) khi null — thành công giữ sạch `{ status, data }`.
- Lỗi: mọi response lỗi mang `timestamp` (ISO-8601), `path` (URI request), và `trace_id` cạnh `status`. Định dạng trace id kiểu JEX — `yyyyMMddHHmmss + "PT" + 14 chữ số ngẫu nhiên + "DEVL"` — sinh bởi [`TraceIdFilter`](src/main/java/com/kosign/wecms/common/exception/TraceIdFilter.java) ở đầu chuỗi filter, đặt trong SLF4J `MDC` dưới khóa `traceId`, và phản hồi lại trong header response `X-Trace-Id`. Cùng id đó xuất hiện trong mọi dòng log của request qua pattern MDC `%X{traceId}` trong [`application.yml`](src/main/resources/application.yml). Một user đọc id từ dialog lỗi có thể đọc lại cho support; ops có thể grep trực tiếp trong server log.
- Các DTO vẫn cần phản chiếu UPPER_SNAKE cũ cho các màn hình chưa migrate (Reports / Transactions / Transfers) giữ annotation `@JsonProperty("UPPER_SNAKE")` rõ ràng của chúng — chúng override strategy toàn cục. Migrate sang lowercase khi tính năng tương ứng vào phạm vi.

### Ánh xạ exception → response

[`GlobalExceptionHandler`](src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) là phễu duy nhất. Mỗi handler gắn `path` + `traceId` qua `ApiResponse.error(code, message, path, traceId)`, log với id MDC theo phạm vi request, và chọn một HTTP status phù hợp với loại lỗi:

| Exception | HTTP | Code | Ghi chú |
|---|---|---|---|
| `BusinessException` | **200** | `ex.getCode()` | Kết quả nghiệp vụ có tài liệu — frontend đọc `status.error`, không phải HTTP code. Log ở `warn`. |
| `MethodArgumentNotValidException` | 200 | `WBE_VALIDATION` | `@Valid @RequestBody` thất bại |
| `ConstraintViolationException` | 200 | `WBE_VALIDATION` | `@Validated` trên path / param |
| `MissingServletRequestParameterException` | 200 | `WBE_VALIDATION` | `@RequestParam` bắt buộc bị bỏ |
| `MethodArgumentTypeMismatchException` | 200 | `WBE_VALIDATION` | param có nhưng sai kiểu |
| `IllegalArgumentException` | 400 | `WBE_VALIDATION` | input xấu lọt qua bean validation |
| `HttpMessageNotReadableException` | 400 | `WBE_BAD_REQUEST` | body JSON dị dạng |
| `HttpRequestMethodNotSupportedException` | 405 | `WBE_METHOD_NOT_ALLOWED` | sai HTTP method |
| `HttpMediaTypeNotSupportedException` | 415 | `WBE_UNSUPPORTED_MEDIA_TYPE` | sai / thiếu `Content-Type` |
| `NoResourceFoundException` | 404 | `WBE_NOT_FOUND` | không handler nào khớp URI |
| `AuthenticationException` | 401 | `WBE1009` | Spring Security pre-auth thất bại (tách khỏi đường JWT 401 nằm ở `JwtAuthenticationEntryPoint`) |
| `AccessDeniedException` | 403 | `WBE1003` | đã xác thực nhưng không được phép |
| `DataAccessException` | 500 | `WBE_DB` | lỗi tầng persistence — log ở `error` kèm stack |
| `TimeoutException` | 504 | `WBE_TIMEOUT` | timeout async / bên ngoài |
| `Exception` (catch-all) | 500 | `WBE9999` | NPE và bất kỳ runtime chưa ánh xạ nào — log ở `error` kèm stack |

JWT 401 vẫn do [`JwtAuthenticationEntryPoint`](src/main/java/com/kosign/wecms/common/security/JwtAuthenticationEntryPoint.java) xử lý (entry point kích hoạt trước chuỗi controller, nên `@RestControllerAdvice` không thấy nó). Nó dùng cùng hình dạng envelope và đọc `traceId` theo phạm vi request từ MDC để body JSON và dòng log tương quan.

### Khi bạn thêm một mã lỗi mới
1. Thêm khóa WBE/WBI + message tiếng Anh vào `messages.properties` (và `messages_en.properties`; các locale tương lai theo sau).
2. Throw `new BusinessException("WBExxxx")` — không bao giờ một message `String` thô. `Object[] args` tùy chọn nội suy qua `MessageResolver`.
3. Nếu lỗi là một loại hoàn toàn mới (auth / DB / hệ thống bên ngoài / v.v.) và `BusinessException` không phù hợp, thêm handler trong `GlobalExceptionHandler` và dòng exception → code vào bảng trên. Đừng đưa vào một envelope lỗi song song.

### Các mã lỗi đang dùng

| Code | Ý nghĩa |
|---|---|
| WBI0000 | Success |
| WBE1009 | ID hoặc mật khẩu không khớp (cũng: user không thuộc tenant đã cấu hình) |
| WBE1010 | Tài khoản chưa kích hoạt |
| WBE1011 | Tài khoản bị khóa |
| WBE1128 | Tài khoản đã xóa |
| WBE2010 | Excel export chưa cấu hình cho màn hình (`screenId` lạ trong `ExcelExportRequest`) |
| WBE2011 | Excel export vượt giới hạn 50.000 dòng |
| WBE2012 | Excel export thất bại (lỗi runtime bất ngờ trước khi flush byte) |
| WBE2013 | Request Excel export không còn cột export được sau khi giao với View Setting / allow-list |
| WBE2014 | Không tìm thấy dòng ERP Transaction History trong tenant (một envelope gộp "không tìm thấy" + "sai tenant") |
| WBE2015 | File ERP Transaction History không có trên storage root đã cấu hình (hoặc dòng batch `FILE_SIZE = 0`) |
| WBE9001 | Endpoint chưa triển khai (consumer hiện tại: stub thumbnail ERP Transaction History) |
| WBE_APPROVAL_LINE_NOT_FOUND | Không tìm thấy dòng Approval Line trong tenant (một envelope gộp "không tìm thấy" + "sai tenant") |
| WBE_APPROVAL_LINE_TOO_MANY_APPROVERS | Approval line không được có quá 5 người duyệt (parity với `WBW1164` cũ) |
| WBE_APPROVAL_LINE_DUP_APPROVER | Cùng `apprvr_id` lặp lại trong mảng request `approvers` |
| WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT | `apprvr_id` không có liên kết `USER_BUSI_CONN_INFM` active cho tenant hiện tại |
| WBE_APPROVAL_LINE_EMPTY_APPROVERS | Mảng request `approvers` rỗng / thiếu |
| WBE_APPROVAL_LINE_NAME_BLANK | `appr_line_nm` thiếu / chỉ có khoảng trắng |
| WBE_CHART_OF_ACCOUNTS_NOT_FOUND | Không tìm thấy dòng chart of accounts trong tenant (một envelope gộp "không tìm thấy" + "sai tenant") |
| WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE | `CHART_ACCT_CD` active trùng trong tenant (dup-check khi Create hoặc va chạm rename khi Update) |
| WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND | `parent_chart_acct_cd` tham chiếu một dòng không tồn tại (hoặc đã soft-delete) trong tenant |
| WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE | Đã thử edit / delete một root hạn chế (`ACC-01` / `ACC-02` / `ACC-03`), hoặc rename một dòng SANG một mã hạn chế |
| WBE_CHART_OF_ACCOUNTS_CYCLE | Đổi cha khi Update sẽ tạo chu trình trong phân cấp (bổ sung refactor — dữ liệu DEV cũ có tự tham chiếu, refactor chặn lúc ghi) |
| WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED | Create / Update sẽ đẩy một lá vượt giới hạn độ sâu 4 mức (bổ sung refactor) |
| WBE_CHART_OF_ACCOUNTS_SELF_PARENT | `parent_chart_acct_cd` bằng `chart_acct_cd` của chính dòng đó (bổ sung refactor) |
| WBE_VALIDATION | Bean-validation thất bại |
| WBE9999 | Lỗi server bất ngờ |

Thêm mã mới vào `messages.properties` trước, rồi throw `new BusinessException("WBExxxx")`. Không bao giờ trả về một message chuỗi thô không có mã.

### Database (LOCAL)

- PostgreSQL `localhost:5434/wecms_dev`, user `postgres`. Mật khẩu qua env `WECMS_DB_PASSWORD` hoặc mặc định R&D nướng trong `application-local.yml` (được `.gitignore` trong setup thật).
- **Flyway sở hữu schema.** `spring.jpa.hibernate.ddl-auto = validate` — không bao giờ `update` hay `create`. Thay đổi schema luôn xuất hiện dưới dạng một `V{N}__{description}.sql` mới trong `src/main/resources/db/migration/`.
- Các migration hiện tại:

  | Version | Tóm tắt |
  |---|---|
  | V1 | Schema auth: BUSI_INFM, USER_INFM, USER_BUSI_CONN_INFM, USER_LOGIN_HIS, index, constraint |
  | V2 | Seed: tenant VN_VNM0004_000001 + testuser (active) + lockeduser (LOCK_YN=Y), mật khẩu = sentinel `__BCRYPT_PENDING__` |
  | V3 | Chuẩn hóa cột YN CHAR(1) → VARCHAR(1) |
  | V5 | Snapshot JEX DEV (2026-04-22): 218 dòng BUSI_INFM + 318 USER_INFM + 382 USER_BUSI_CONN_INFM, tất cả USER_PW = sentinel |
  | V6 | Giới hạn dữ liệu về một tenant |
  | V7 | Căn schema theo cấu trúc JEX cũ chính xác |
  | V8 | Thêm BUSI_INFM.DEL_YN |
  | V9 | Chuẩn hóa cột char BUSI_INFM |
  | V10 | Thêm legacyuser với hash SHA-256 base64 để test đường nâng cấp |
  | V11 | Bootstrap Phase 1: bảng EMPL_INFM + BUSI_ADMN_INFM, sequence `sq_biz_key`, hàm PL/pgSQL cũ `wb_get_next_val` (chỉ nhánh BIZ_KEY + EMPL_NO). Idempotent. |
  | V12 | Căn lại cột ngày EMPL_INFM về hình dạng VARCHAR(8) cũ. |
  | V13 | Schema banking Sprint 1: ACCT_MSTR, ACCT_TRNS_HSTR, ACCT_TRNS_RCV_PAY_INFM, BUSA_DOC_RCV_PAY_HIS, ACCT_TRNS_RCV_PAY_EVID_FILE, NAPAS_REMIT_TRNS_HSTR. |
  | V14 | Schema reports Sprint 1: BUSI_COND_DAILY_SUMR(_DETL), SELL_HIS_DAILY_SUMR(_DETL), BAT_CALL_HIS. |
  | V15 | Schema e-invoice Sprint 1: BUSA_DOC_MSTR (chỉ tập con cột Sprint 1 — ~16 trên 100 cột cũ), EINV_FEE_DETL, EINV_TAX_DETL. |
  | V16 | Schema operation status Sprint 1: OPRT_STTS_INFO, EXT_TRAN_CALL_HIS. |
  | V17 | Dữ liệu seed Sprint 1 — ghim vào `BIZ_KEY = VN_VNM0004_000001`. Idempotent qua `INSERT … ON CONFLICT DO NOTHING`. Cũng nới `EINV_INVOICESYMBOL` lên VARCHAR(50) cho tương thích static-UI. |
  | V18 | Chuẩn hóa CHAR → VARCHAR trên các cột banking/report/e-invoice Sprint 1 để Hibernate `validate` chấp nhận (theo pattern V3 / V9). |
  | V19 | Tiếp nối V18 — chuyển `ACCT_TRNS_RCV_PAY_INFM.CONF_YN` CHAR(1) → VARCHAR(1) (sót ở V18). |
  | V20 | Audit Sprint 1: thêm bảng `BANK_INFM` (registry ngân hàng Việt Nam, 29 dòng seed từ DEV thật) để tra tên ngân hàng đi qua DB thay vì map tĩnh. |
  | V21 | Audit Sprint 1: căn `ACCT_TRNS_RCV_PAY_INFM` theo tên cột cũ — đổi tên `TRCO_*`/`USAG_CD`/`AMT`/`CONF_*` → `RCV_PAY_TRCO_*`/`RCV_PAY_USAG_CD`/`RCV_PAY_AMT`/`CNFM_*`; thêm cột locator cũ BANK_CD/ACCT_NO/TRSC_DT. |
  | V22 | Audit Sprint 1: thay seed demo giả V17 bằng dòng wecmsvndev thật (~25 mới nhất mỗi bảng) cho tenant `VN_VNM0004_000001`. Các bảng rỗng trên DEV giữ stub V17: `BUSI_COND_DAILY_SUMR(_DETL)`, `SELL_HIS_DAILY_SUMR(_DETL)`, `ACCT_TRNS_RCV_PAY_EVID_FILE`. |
  | V23 | Migration sửa parity schema Sprint 1: thêm 144 cột cũ thiếu trên 6 bảng (`ACCT_MSTR` +30, `ACCT_TRNS_HSTR` +10, `ACCT_TRNS_RCV_PAY_INFM` +17, `BUSA_DOC_RCV_PAY_HIS` +10, `ACCT_TRNS_RCV_PAY_EVID_FILE` +7, **`BUSA_DOC_MSTR` +80**). Thuần bổ sung — `ALTER TABLE ADD COLUMN IF NOT EXISTS`. Đưa schema local tương đương wecmsvndev theo từng cột. |
  | V24 | Migration sửa parity schema Sprint 1 vòng 2 (constraint): căn BUSI_INFM.BIZ_KEY VARCHAR(32)→(20); căn lại 5 PRIMARY KEY về hình dạng cũ (`bank_infm`, `bat_call_his`, `acct_trns_rcv_pay_evid_file`, `sell_his_daily_sumr(_detl)`); thêm UNIQUE `busa_doc_mstr_uk01`; thêm 11 CHECK cũ; thêm 5 FOREIGN KEY cũ (phần còn lại bị hoãn — phụ thuộc `cash_repy_mstr`/`mrec_pic_store` chưa migrate, hoặc trigger audit-log). |
  | V25 | ~~Port đầy đủ DEV `wb_get_next_val` (cả 164 nhánh WHEN)~~ — **bị thay thế bởi V26/V27**. Đã áp dụng và sửa ngay; đừng tham chiếu như trạng thái chuẩn. |
  | V26 | Sửa (trung gian): cắt `wb_get_next_val` còn 2 nhánh; bỏ 11 sequence không cần V25 tạo. **Bị thay thế bởi V27** vốn thêm các nhánh bảng-đã-migrate Sprint 1. |
  | V27 | Sửa cuối cho `wb_get_next_val`: nhánh Sprint 0 (2) + nhánh bảng-đã-migrate Sprint 1 có entry DEV (11). Tổng 13 nhánh. Các bảng Sprint 1 không có nhánh DEV ghi chú inline. |
  | V28 | Audit parity cuối Sprint 1 (2026-04-30) — sửa schema: thêm `empl_infm._empl_nm`, thêm `user_login_his.reg_uid` (và dựng lại PK thành (user_id, reg_dtm, reg_uid)); nới NUMERIC lên `(20,6)` cho amount và `(N)` cho cột seq trên 12 bảng; nới VARCHAR(50)→(100) cho `user_id`/`reg_id`/`mod_id` trên các bảng auth và `rcv_pay_trco_nm` 200→250; thêm UNIQUE `acct_trns_hstr_uk01`, `busi_infm_uk01`; thêm CHECK `busi_infm_chk01`; căn lại PK `busi_cond_daily_sumr_detl` theo thứ tự cột DEV. Tất cả bổ sung/an toàn — xem [docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md](../docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md). |
  | V29 | Seed DEV-thật Sprint 1 vòng 2: 226 dòng thật nhập vào 5 bảng trước đó rỗng — `bat_call_his` (84), `busa_doc_rcv_pay_his` (26), `einv_fee_detl` (16), `einv_tax_detl` (50 trên 669 — lấy mẫu mới nhất), `ext_tran_call_his` (50 trên 24K — lấy mẫu). Idempotent qua `INSERT … ON CONFLICT DO NOTHING`. |
  | V30 | Ổn định hóa Sprint 1: `SYS_SCR_OPTION_WECMS` + `USER_SCR_OPTION_WECMS` (View Setting). Seed 6 cột mặc định cho `fnsh_0001_001` (BANK · ACCOUNT_NICKNAME · BALANCE · AVAILABLE_BALANCE · VAS_ACCOUNT (ẩn) · LAST_UPDATE). Idempotent qua `CREATE TABLE IF NOT EXISTS` + `INSERT … ON CONFLICT DO NOTHING`. |
  | V31 | Dọn dẹp PROD Active Table Scope (2026-05-01) — bỏ 6 bảng local KHÔNG có trong danh sách active PROD bên dưới: `busi_cond_daily_sumr(_detl)`, `sell_his_daily_sumr(_detl)`, `bat_call_his`, `acct_trns_rcv_pay_evid_file`. Chúng được mang theo từ JEX DEV nhưng PROD không populate. Phía Java: xóa entity / id / repository `BusiCondDailySumr`; tóm tắt theo từng loại tiền của `ReportService.dailyCash()` giờ stub (sổ tài khoản vẫn đọc `ACCT_MSTR`). Sprint 2 sẽ đấu nối nguồn báo cáo PROD thật (`DAILY_CASH_REPT_DAILY_SUMR_DETL`, `DAILY_FINCL_REPT_DAILY_SUMR_DETL`). |
  | V32 | Chuyển View Setting sang screen-id ngữ nghĩa (2026-05-01) — xóa các dòng `fnsh_0001_001` V30 khỏi cả `SYS_SCR_OPTION_WECMS` và `USER_SCR_OPTION_WECMS`, seed lại Banking > Account Inquiry (VND) dưới `WEB_SVC_ID = 'banking-account-inquiry-vnd'` ngữ nghĩa với `DYM_AREA_ID = 'default'` trên mọi dòng, rồi `ALTER COLUMN DYM_AREA_ID SET NOT NULL` trên cả hai bảng (sau khi backfill các null). Idempotent. Phía Java (cùng MR): thêm ánh xạ `dymAreaId` vào entity `SysScrOptionWecms`/`UserScrOptionWecms`; cập nhật repo/service/controller cho hình dạng path mới (`{webSvcId}/{dymAreaId}` cộng `{webSvcId}/areas`); thêm guard validation `@Pattern`; thêm handler `ConstraintViolationException` trong `GlobalExceptionHandler`. Phạm vi R&D — không bảo toàn override do user lưu ở local. Xem [docs/jex/features/view-setting.md](../docs/jex/features/view-setting.md). |
  | V33 | Seed FIELD_ID special View Setting (2026-05-02) — thêm `CHECKBOX` (DISP_ORD=0, FIX_YN='Y') dưới `banking-account-inquiry-vnd` / `default` để neo quy ước cột chọn-dòng. Các FIELD_ID special (`CHECKBOX`, `ETC`) do admin kiểm soát — popover frontend ẩn chúng, backend `ScreenOptionService.SPECIAL_FIELD_IDS` khóa `USE_YN='Y'` + `DISP_ORD` ghim-theo-sys khi save. ETC KHÔNG seed — Account Inquiry không có cột hành động. |
  | V34 | Parity schema View Setting + seed SIZE (2026-05-02) — căn `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` theo DEV: `SIZE` INTEGER → VARCHAR(10), `DISP_YN` SET NOT NULL, THÊM `SORT_YN CHAR(1) NOT NULL DEFAULT 'Y'` + `HTML_CONT TEXT` trên sys (DEV không có chúng trên user_scr — local phản chiếu). Backfill SIZE cho `banking-account-inquiry-vnd` dùng **độ rộng Figma WB365** (nguồn chuẩn về bố cục — không khớp DEV cũ ở 5 trên 7 cột). Tái khẳng định hai drift giá trị dòng tích tụ sau V32 (`BANK.fix_yn='Y'`, `VAS_ACCOUNT.use_yn='N'/disp_yn='Y'`). Xóa bảng USER cho phạm vi để lazy-init copy lại. Các sai lệch chỉ-của-refactor có tài liệu (giữ): `field_nm` rộng 200/nullable, `disp_ord` INTEGER, cột `fix_yn`, `reg_dtm`/`mod_dtm` TIMESTAMPTZ. SIZE **đã căn schema + seed giá trị nhưng vẫn chưa tiêu thụ đầu-cuối** — entity Java / DTO / TS type không đổi. **Bị thay thế ở tầng dữ liệu bởi V35** vốn đảo quyết định id ngữ nghĩa của V32 và seed lại với id DEV cũ. |
  | V35 | Parity DEV chỉ-schema View Setting (2026-05-02) — siết kiểu/nullability cột mà không đụng nội dung dòng. `field_nm` VARCHAR(200) NULL → VARCHAR(100) NOT NULL trên cả hai bảng; `disp_ord` INTEGER NOT NULL DEFAULT 0 → NUMERIC(10,0) NOT NULL trên cả hai bảng (DROP DEFAULT trước); `size` SET NOT NULL trên sys (V34 đã populate mọi dòng dùng độ rộng Figma WB365). Các dòng `web_svc_id = 'banking-account-inquiry-vnd'` từ V32–V34 giữ nguyên — V35 KHÔNG seed `fnsh_0001_001` cũ (quy ước screenId refactor là route-based ngữ nghĩa theo `wecms-web-main/lib/viewSettingScreens.ts`, KHÔNG phải id JEX cũ). Sai lệch chỉ-của-refactor giữ: cột `fix_yn` (khóa cột, xem [docs/jex/features/view-setting.md §1.8](../docs/jex/features/view-setting.md)) và cột audit `BaseEntity` (`reg_dtm`/`mod_dtm` TIMESTAMPTZ vs VARCHAR(14) của DEV) — cả hai cố ý khác DEV. `user_scr_option_wecms.size` giữ NULLABLE (hoãn — `ScreenOptionService.initUserRowsFromSys` giờ copy sys.size vào user.size, nhưng dòng user local cũ có NULL; chuyển sang NOT NULL trong follow-up sau một vòng dev/QA xác nhận coverage). Phía Java cùng MR: `SysScrOptionWecms` thêm `size`/`sortYn`/`htmlCont`/`dispYn`; DTO `ScreenOptionItem` lộ SIZE/SORT_YN/HTML_CONT/DISP_YN; `initUserRowsFromSys` copy `size`/`dispYn`. **`disp_ord` NUMERIC(10,0) bị đảo ngay bởi V36** (xem dưới) — schema validator của Hibernate từ chối NUMERIC với ánh xạ Java `Long`/`Integer`. Idempotent. |
  | V36 | Đảo `disp_ord` View Setting về INTEGER (2026-05-02) — hotfix cho V35: Hibernate `validate` thất bại vì Java `Long` ánh xạ BIGINT nhưng V35 đặt cột thành NUMERIC. Đảo `disp_ord` NUMERIC(10,0) → INTEGER NN DEFAULT 0 trên cả hai bảng và khôi phục lập trường V34 ("khớp kiểu trường JPA `Integer dispOrd`"). Entity đảo về `Integer dispOrd` trong cùng MR. Sai lệch vĩnh viễn chỉ-của-refactor so với NUMERIC(10,0) của DEV — có tài liệu trong Javadoc entity + header V36. Idempotent (`ALTER COLUMN TYPE` là no-op khi đã INTEGER). |
  | V37 | `sort_yn` View Setting CHAR(1)→VARCHAR(1) trên sys (2026-05-02) — hotfix tạm cho lỗi Hibernate validate thứ hai sau V35 (`found bpchar, expecting char(1) (Types#VARCHAR)` cho trường `String sortYn` mới). **Sai hướng — bị thay thế bởi V38** vốn đảo thay đổi nghiêng về parity DEV (DEV là `bpchar(1)`). V37 để lại trong flyway_schema_history như-đã-áp-dụng; V38 đảo kiểu cột và entity được `@JdbcTypeCode(SqlTypes.CHAR)` để giữ parity phía DB. |
  | V38 | Đảo `sort_yn` View Setting về CHAR(1) trên sys (2026-05-02) — đảo V37, khôi phục `bpchar(1) NOT NULL DEFAULT 'Y'` parity-DEV trên `sys_scr_option_wecms.sort_yn`. Phía Java cùng MR: `SysScrOptionWecms.sortYn` annotate `@JdbcTypeCode(SqlTypes.CHAR)` để Hibernate ánh xạ `String` → kiểu JDBC CHAR và `validate` chấp nhận cột bpchar. **Khóa hướng**: khi DEV dùng `char(1)`, ưu tiên annotation phía entity hơn migration CHAR→VARCHAR — giữ DB local nghiêm ngặt parity-DEV. Idempotent. |
  | V39 | Thêm cột `size` chữ thường vào bảng screen-option (2026-05-02) — V30/V34 tạo `"SIZE"` như định danh chữ hoa có nháy; Hibernate 6 sinh `size` chữ thường không nháy trong SQL, gây `ERROR: column ssow1_0.size does not exist`. Sửa trung gian: thêm cột `size varchar(20)` mới (backfill `'auto'`), giữ `"SIZE"` nguyên vẹn. **Bị thay thế bởi V40** vốn sửa đúng cách. |
  | V40 | Sửa cột SIZE — bỏ cột trùng V39, đổi tên `"SIZE"` cũ → `size` (2026-05-02) — cột `size` của V39 có giá trị `'auto'` sai; giá trị pixel Figma thật (`38px`, `240px`, `280px`, …) nằm ở cột `"SIZE"` cũ. V40: bỏ cột `size` V39, đổi tên `"SIZE"` thành `size`, nới cả hai bảng từ varchar(10) → varchar(20). Sys: NOT NULL tái khẳng định (xác nhận 0 null). User: giữ NULLABLE (lazy-init bởi `initUserRowsFromSys`). Annotation `length` entity cập nhật thành 20 trên cả `SysScrOptionWecms` và `UserScrOptionWecms`. |
  | V41 | View Setting — tắt `SORT_YN` cho `VAS_ACCOUNT` trên `banking-account-inquiry-vnd` (2026-05-02) — chuyển `SYS_SCR_OPTION_WECMS.SORT_YN='N'` và xóa `USER_SCR_OPTION_WECMS` cho màn hình để bước lazy-init copy lại sys default đã sửa. Căn seed với whitelist `AccountSortField` backend (không có hằng `VAS_ACCOUNT` — cột chưa có trường entity hỗ trợ) theo quy tắc "Common table sort" mới rằng cột `SORT_YN='Y'` PHẢI có entry whitelist. Idempotent. Khi `BUSI_VAS_ACCT_INFM` xuất hiện ở sprint sau, cùng migration thêm hằng enum sẽ chuyển lại `'Y'`. |
  | V42 | View Setting — đặt `DISP_YN='N'` cho `VAS_ACCOUNT` trên `banking-account-inquiry-vnd` (2026-05-02) — ẩn `VAS_ACCOUNT` khỏi popup View Setting. `DISP_YN='N'` phản chiếu filter `IDO.USER_SCR_OPTION_WECMS_R002.xml` cũ (`WHERE ... AND USR.DISP_YN = 'Y'`): truy vấn popup chỉ trả về dòng `DISP_YN='Y'`. `ViewSettingPopover.tsx` của frontend bắt buộc cùng quy tắc phía client (thêm trong cùng MR). `VAS_ACCOUNT` đã có `USE_YN='N'` (ẩn khỏi bảng mặc định, V34); `DISP_YN='N'` ngăn user "bỏ-ẩn" một cột vẫn render `"—"`. Xóa `USER_SCR_OPTION_WECMS` cho phạm vi để lazy-init copy lại cờ đã sửa. Khi `BUSI_VAS_ACCT_INFM` được port, chuyển cả `DISP_YN` và `USE_YN` về `'Y'` trong cùng migration. Idempotent. |
  | V43–V70 | Nền tảng module Settings (audit logs, firm-banking sources/view/seed, environment settings, v.v.). Xem các file migration riêng trong `src/main/resources/db/migration/` — không chép vào bảng này theo từng dòng. |
  | V71 | Schema ERP Transaction History (2026-05-11) — `CREATE TABLE IF NOT EXISTS ERP_TRNS_HSTR` theo từng cột với JEX DEV (`biz_key VARCHAR(20)`, `seq_no NUMERIC(10,0)`, `org_file_nm` / `save_file_nm` VARCHAR(500), `file_size NUMERIC(10,0)`, `erp_type CHAR(1)`, `trns_type_cd CHAR(2)`, `reg_id VARCHAR(100)`, `reg_dtm VARCHAR(14)`, PK `(BIZ_KEY, SEQ_NO)`). Thêm hai index hiệu năng phụ trợ ngoài DEV (`IX_*_BIZ_REG_DTM`, `IX_*_BIZ_ERP_TYPE`) — index thêm được Legacy Schema Parity Rule cho phép. Idempotent. Consumer đầu tiên: `domain/setting/erptransactionhistory`. |
  | V72 | Seed demo ERP Transaction History (2026-05-11) — 30 dòng cho tenant `VN_VNM0004_000001` từ JEX DEV thật (top 10 mỗi hướng theo `REG_DTM DESC, SEQ_NO DESC`). Mọi dòng seed có `FILE_SIZE > 0`; file vật lý KHÔNG được seed dưới `wecms.erp-transaction-history.storage-root` — endpoint download trả về `WBE2015` cho bất kỳ dòng nào có file thiếu local, phản chiếu hành vi production. Idempotent qua `INSERT … ON CONFLICT DO NOTHING`. |
  | V73 | Seed multi-tenant (2026-05-11) — thêm tenant R&D thứ hai `VN_VNM0004_000014` (HAENGSUNG ELECTRONICS, `PRDT_TYPE = 'H'`) từ JEX DEV thật, cộng 6 user HAENGSUNG nhập từ DEV, dòng `BUSI_ADMN_INFM` cho `wecmshaengsung@gmail.com`, và ba liên kết multi-company có guard `WHERE EXISTS` (`rndadmin` / `testuser` sáng lên trên DB chỉ-bootstrap — no-op trên DB nhập-DEV; `lamine@gmail.com` sáng lên trên DB nhập-DEV để chooser thử được ở đó). Mọi insert idempotent qua `ON CONFLICT DO NOTHING` + guard `WHERE EXISTS` ngăn vi phạm FK với `USER_INFM` cho user không có trên DB local. |
  | V74 | Backfill `BUSI_INFM.PRDT_TYPE` cho `VN_VNM0004_000001` (2026-05-11) — INSERT của V2 bỏ cột nên dòng là `NULL` ở local; DEV lưu `'F'`. Idempotent — mệnh đề WHERE loại trừ dòng đã mang giá trị non-NULL. Sau V74 hai tenant R&D local căn với tập refactor công nhận: 000001 → `'F'`, 000014 → `'H'`. |
  | V75–V83 | (Nhiều migration ổn định hóa: sửa kiểu numeric erp transaction history, dọn liên kết multi-company, schema + seed + View Setting VAS Account, schema + view + nhánh `wb_get_next_val` Purpose Settings, căn dữ liệu init JEX DEV. Xem header từng file dưới `src/main/resources/db/migration/`.) |
  | V84 | Schema Settings > Contacts (2026-05-13) — `TRCO_INFM` (47 cột, PK chỉ trên `TRCO_KEY` — sequence toàn cục), `TRCO_ADDR_INFM` (11), `TRCO_ACCT_INFO` (20), `TRCO_OVRS_INFM` (13), `PGRO_BSAC_LNKN_INFM` (6). Cả năm bảng trong PROD Active Table List. Parity theo từng cột với DEV đã kiểm chứng qua MCP `postgres_dev`; PK / UK (`trco_infm_uk01` trên `(BIZ_KEY, TRCO_NICK_NM)`, `pgro_bsac_lnkn_infm_uk01` trên `(BIZ_KEY, GRP_CD, TRCO_KEY)`) / CHECK (`trco_infm_chk01` trên `TRSC_CCY_CD IN ('VND','USD')`) / FK (`trco_acct_info_fk01`, `pgro_bsac_lnkn_infm_fk01/fk02`) mang theo nguyên văn. Index phụ trợ thêm ngoài DEV: `ix_trco_infm_biz_handkey`, `ix_trco_infm_biz_taxid`, `ix_trco_infm_biz_moddtm`, `ix_trco_addr_infm_biz_trco`, `ix_trco_acct_info_biz_trco`, `ix_trco_ovrs_infm_biz_trco`. Idempotent. |
  | V85 | Settings > Contacts: mở rộng `wb_get_next_val` (2026-05-13, OQ-4) — thêm nhánh `trco_infm.trco_key` (`SELECT nextval('sq_trco_key')`) port NGUYÊN VĂN từ DEV; tạo sequence local `sq_trco_key` bắt đầu ở 1.000.000 (cao hơn nhiều trần seed V87). Số nhánh: 13 → **14**. Có tài liệu trong "Surviving legacy functions" bên dưới với kế hoạch retire. Tiêu thụ bởi `LegacyFunctionRepository.nextTrcoKey` → `ContactsService.create`. |
  | V86 | Settings > Contacts: port `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang_cd)` (2026-05-13, OQ-7) — thân hàm copy NGUYÊN VĂN từ DEV. Đọc từ `tb_firm_bank_code` (V59) — không phụ thuộc schema mới. Trả về viết tắt EN cho `lang_cd='en'`, tên dài cho `lang_cd in ('vi','vn')`; trả về NULL cho `'ko'` / `'km'` (không có nhánh trên DEV). Có tài liệu trong "Surviving legacy functions" bên dưới. Tiêu thụ bởi `LegacyFunctionRepository.resolveNapasOrCitadBankName` → `ContactsService.list/getOne` (gom theo phạm vi request, một lần gọi phễu mỗi cặp `(code, lang_cd)` duy nhất). `BankCdResolver` KHÔNG dùng trong luồng này theo OQ-7. |
  | V87 | Seed demo Settings > Contacts (2026-05-13) — 30 dòng master + 30 địa chỉ + 28 tài khoản ngân hàng + 9 người thụ hưởng nước ngoài + 30 PGRO group-link (`USER_ID='ALL'`, `GRP_CD ∈ {B,S}`) lấy từ JEX DEV thật cho tenant `VN_VNM0004_000001`. Giá trị TRCO_KEY giữ nguyên văn từ DEV (9212..9322) để giữ chuỗi FK nhất quán; contact mới tạo lấy giá trị từ `sq_trco_key` bắt đầu ở 1.000.000 — không nguy cơ va chạm. Idempotent qua `INSERT … ON CONFLICT DO NOTHING`. |
  | V88 | Seed View Setting Settings > Contacts (2026-05-13) — đăng ký `web_svc_id='contacts'`/`dym_area_id='default'` với 8 cột: CHECKBOX · HAND_TRCO_KEY · TRCO_CMNM · TRCO_TYPE · TRCO_TAX_ID · BANK_NM · TRCO_BSUN_POST_ADRS · ETC. `BANK_NM` và `TRCO_BSUN_POST_ADRS` ship `SORT_YN='N'` vì chúng project từ bảng con (không có hằng `ContactSortField` cho chúng); quy tắc đồng thuận View Setting + whitelist được bảo toàn. Idempotent. |
  | V89 | Hotfix Settings > Contacts (2026-05-13) — chuyển `TRCO_ADDR_INFM.DISP_ORD` và `TRCO_ACCT_INFO.DISP_ORD` từ `NUMERIC(10,0)` sang `INTEGER`. Cùng tiền lệ y hệt hotfix V35 → V36 cho `SysScrOptionWecms.dispOrd` — `ddl-auto: validate` của Hibernate 6 từ chối NUMERIC với ánh xạ Java `Integer`, nên quy ước refactor đã thiết lập là alter cột local sang INTEGER và chấp nhận sai lệch so với NUMERIC của DEV. Có tài liệu inline + trên entity. `USING DISP_ORD::integer` làm cast lossless rõ ràng. An toàn — `DISP_ORD` trên cả hai bảng thực tế chỉ mang 1 hoặc 2. Idempotent. |
  | V90 | Schema Settings > Approval Line (2026-05-13) — `APPR_LINE_INFM` (7 cột, PK trên `(BIZ_KEY, APPR_LINE_NO)`) + `APPR_LINE_INFM_DETL` (11 cột, PK trên `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)`). Cả hai bảng trong PROD Active Table List. Parity theo từng cột với DEV đã kiểm chứng qua MCP `postgres_dev`. `APPRVR_DISP_ORD NUMERIC(1,0)` ghim giới hạn 5 người duyệt ở cấp DB (parity với `WBW1164` cũ). Index phụ trợ thêm ngoài DEV: `ix_appr_line_infm_biz_reg_dtm` (`BIZ_KEY, REG_DTM DESC, APPR_LINE_NO DESC`) hiện thực hóa sort list mặc định. Không FK (DEV cũng không có; xóa cascade nằm ở tầng service qua `@Modifying` DELETE-by-FK trên repo detail). Idempotent. |
  | V91 | Settings > Approval Line: mở rộng `wb_get_next_val` (2026-05-13, OQ-7) — thêm nhánh `appr_line_infm.appr_line_no` port NGUYÊN VĂN từ DEV (`SELECT COALESCE(MAX(appr_line_no::numeric), 0) + 1 FROM appr_line_infm WHERE biz_key = $3`). MAX+1 theo từng tenant. Số nhánh: 14 → **15**. Có tài liệu trong "Surviving legacy functions" bên dưới với kế hoạch retire. Tiêu thụ bởi `LegacyFunctionRepository.nextApprLineNo` → `ApprovalLineService.create`. |
  | V92 | Hotfix Settings > Approval Line (2026-05-13) — chuyển `APPR_LINE_INFM.APPR_LINE_NO` + `APPR_LINE_INFM_DETL.APPR_LINE_NO` từ `NUMERIC(15,0)` sang `BIGINT`, và `APPR_LINE_INFM_DETL.APPRVR_DISP_ORD` từ `NUMERIC(1,0)` sang `INTEGER`. Cùng tiền lệ V35 → V36 (`SysScrOptionWecms.dispOrd`) và V89 (Contacts `disp_ord`) — `ddl-auto: validate` của Hibernate 6 từ chối NUMERIC với ánh xạ Java `Long` / `Integer`. `USING ...::bigint|integer` làm cast lossless rõ ràng. An toàn — trần 15 chữ số của DEV vừa trong BIGINT; giới hạn phòng-thủ-theo-chiều-sâu `NUMERIC(1,0)` trên `APPRVR_DISP_ORD` được thay bằng guard tầng service (`ApprovalLineConstants.MAX_APPROVERS=5` + bean validation + `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`). Idempotent — khối DO kiểm tra `data_type` trước khi alter. |
  | V94 | Schema Settings > Chart of Accounts (2026-05-13) — `CREATE TABLE IF NOT EXISTS CHART_ACCT_INFM` theo từng cột với JEX DEV (15 cột, PK `(BIZ_KEY, CHART_ACCT_SEQ_NO)`, UK `uk_chart_acct_cd` trên `(BIZ_KEY, CHART_ACCT_CD, DEL_YN)` — UK 3-cột là thứ cho phép hành vi revive-on-create / archive-rename-on-delete). Parity theo từng cột với DEV đã kiểm chứng qua MCP `postgres_dev`. `CHART_ACCT_SEQ_NO` giữ `VARCHAR(14)` (parity DEV); entity ánh xạ là `String` đầu-cuối — không cần AttributeConverter. `STTS` / `DEL_YN` / `CAL_RULE` là `CHAR(1)` trên DEV; entity dùng `@JdbcTypeCode(SqlTypes.CHAR)` để `validate` chấp nhận cột bpchar (cùng tiền lệ V38 `sort_yn`). Index phụ trợ thêm ngoài DEV: `ix_chart_acct_infm_biz_parent` (partial `WHERE del_yn='N'`) phục vụ re-parent con + group-count has_children; `ix_chart_acct_infm_biz_moddtm` hỗ trợ sort MOD_DTM tùy chọn. Không CHECK / FK trên DEV → không có ở local (parity). Idempotent. Consumer đầu tiên: `domain/setting/chartofaccounts`. |
  | V95 | Settings > Chart of Accounts: mở rộng `wb_get_next_val` (2026-05-13) — thêm nhánh `chart_acct_infm.chart_acct_seq_no` port NGUYÊN VĂN từ DEV (`SELECT COALESCE(MAX(chart_acct_seq_no::numeric), 0) + 1 FROM chart_acct_infm WHERE biz_key = $3`). MAX+1 theo từng tenant, cùng hình dạng V91 `appr_line_no`. Số nhánh: 15 → **16**. Có tài liệu trong "Surviving legacy functions" bên dưới với kế hoạch retire. Tiêu thụ bởi `LegacyFunctionRepository.nextChartAcctSeqNo` → `ChartOfAccountsService.create`. |
  | V96 | Settings > Chart of Accounts: seed root-hạn-chế (2026-05-13) — seed `ACC-01` (REVENUE / 수입), `ACC-02` (EXPENSES / 지출), `ACC-03` (INVESTMENT / 투자) cho mọi tenant active trong `BUSI_INFM`. Ba mã này bị `ChartOfAccountsService` chặn edit / delete qua `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` (gương cũ: `WBE1178` trong `comm_0223_001_u001` / `d001`). Giải quyết OQ planning về cấp phát dòng root — seed lúc Flyway, không phải auto-tạo theo nhu cầu. `chart_acct_seq_no` cấp phát qua nhánh V95 để giá trị giữ tách rời theo tenant. `reg_id` / `mod_id` = `biz_key` (quy ước cũ — dòng DEV cho tenant 000001 seq 70/74/80 mang cùng pattern). Idempotent qua `ON CONFLICT (BIZ_KEY, CHART_ACCT_CD, DEL_YN) DO NOTHING`. |
  | V97 | Settings > Chart of Accounts: seed demo cho `VN_VNM0004_000001` (2026-05-13) — 9 dòng thật lấy từ JEX DEV, giữ giá trị `CHART_ACCT_SEQ_NO` của DEV (90..112). Loại trừ các root hạn chế đã seed ở V96. Hai hình dạng grandfather giữ nguyên văn: seq 104 mang tự-tham-chiếu (`CHART_ACCT_CD='2001001'`, `PARENT_CHART_ACCT_CD='2001001'`), seq 112 mang cha mồ côi (`'Code009'` thiếu trong snapshot). `ChartOfAccountsTreeBuilder` lộ cả hai với `chart_acct_level = null`; các guard chu-trình / tự-làm-cha / độ-sâu trong `ChartOfAccountsValidator` chỉ áp dụng cho ghi mới (dữ liệu cũ được grandfather hóa). Idempotent. |

- Đặt tên bảng/cột: `UPPER_SNAKE`, constraint FK `FK_<child>_<parent>`. Phản chiếu JEX cũ để dữ liệu copy sạch.

### Database (DEV — hệ thống JEX đang chạy thật)

- Chỉ-đọc qua `postgres` MCP. Danh sách cấm đầy đủ trong [.claude/rules/database.md](../.claude/rules/database.md) — không INSERT / UPDATE / DELETE / DDL / EXPLAIN ANALYZE / DO $$…$$ / CALL.
- Luồng một-chiều: **DEV → SQL script / Flyway → LOCAL**. Không bao giờ ghi ngược về DEV / UAT / PROD.

### Legacy Schema Parity Rule

Khi port một bảng `wecmsvndev` cũ vào Flyway, **schema DB local phải phản chiếu DEV theo từng cột một**. Điều này không thương lượng — schema một-phần là nhà máy đẻ bug tương lai.

**Nên**

- Với mỗi bảng cũ được migrate, schema local phải khớp DEV chính xác: tên cột, kiểu dữ liệu, độ dài / precision / scale, nullability, primary key, các index quan trọng, và giá trị DEFAULT nơi DDL cũ định nghĩa.
- Trước khi viết migration, truy vấn `information_schema.columns` của DEV (chỉ-đọc qua `postgres` MCP) cho bảng; copy mọi cột. Ghi số cột từ DEV vào header migration để reviewer tương lai phát hiện drift.
- Thêm `CREATE INDEX IF NOT EXISTS` cho các index cũ mà workload thật dùng (đặc biệt truy vấn date-range / tenant trên bảng history).
- Nếu phát hiện khoảng trống parity sau khi migration đã áp dụng, ship một migration sửa bổ sung (`ALTER TABLE ADD COLUMN IF NOT EXISTS`). Migration V23 là tiền lệ đang chạy.
- Tiếp tục đánh số Flyway đúng — không bao giờ sửa migration đã áp dụng.

**Không nên**

- Đừng tạo bảng 12-cột giản lược chỉ vì static UI đọc 12 trường. Nếu DEV có 100 cột, local có 100 cột. Khái niệm "tập con" áp dụng cho JPA entity, không cho schema DB.
- Đừng bỏ cột cũ từ DEV khi đưa vào Flyway — kể cả khi bạn nghĩ Sprint 1 không cần. ETL tương lai hoặc tính năng Sprint 2+ sẽ cần.
- Đừng đưa vào cột thay thế tùy tiện (`SEQ_NO` tự thiết kế, `FILE_PATH` thay cho `SAVE_FILE_NM`, v.v.) khi bảng cũ đã mã hóa cùng khái niệm dưới tên của nó.
- Đừng bỏ mệnh đề DEFAULT cho cột NOT-NULL cũ — đường Hibernate / `INSERT … SELECT` sẽ va vào vi phạm constraint khi cột lên production.

**Tách biệt schema vs. entity vs. DTO**

- **Schema DB** — phải khớp DEV chính xác. Không tập con. (Quy tắc này.)
- **JPA entity** — có thể chỉ ánh xạ các trường Sprint 1 đọc/ghi. Hibernate `validate` bỏ qua cột DB mà entity không khai báo. Ghi điều này trong Javadoc entity với ghi chú "Sprint 1 column subset; extend when …".
- **DTO** — chỉ lộ các trường wire mà API contract cần. Độc lập với cả hai.
- **Dữ liệu seed** — có thể chỉ chèn các cột luồng demo cần, miễn là các cột không chọn là nullable hoặc có DEFAULT. Đừng INSERT NULL vào một cột NOT-NULL cũ.

**Checklist trước migration (dán vào mô tả MR)**

```
- [ ] Đã truy vấn DEV `information_schema.columns` cho mọi bảng đụng tới
- [ ] Số cột migration local == số cột DEV
- [ ] Mọi cột NOT-NULL cũ có DEFAULT cũ hoặc nằm trong seed
- [ ] DEFAULT cũ giữ nguyên văn (ví dụ PROV_STTS = 'OP', REG_CHNL = 'PC')
- [ ] PK + index khóa được mang theo
- [ ] Nếu đã áp dụng local, mismatch sửa bằng migration MỚI (chỉ bổ sung)
```

**Constraint, index, và trigger — cũng là một phần của parity**

Quy tắc "theo từng cột" ở trên mở rộng tới constraint và cấu trúc vật lý:

- **PRIMARY KEY**: phải khớp tập cột PK + thứ tự của DEV (PostgreSQL tôn trọng thứ tự cột PK cho btree index bên dưới). Nếu một PK local khai báo sai tập cột, drop và tạo lại qua migration sửa.
- **UNIQUE**: mọi constraint UNIQUE `*_uk*` cũ phải được mang theo. Cột nhiều-NULL được dedup qua mặc định "NULLs distinct" của PostgreSQL — không cần xử lý đặc biệt.
- **CHECK**: mọi constraint `*_chk*` cũ phải được mang theo. Xác minh seed local đã pass constraint *trước khi* thêm nó (một `DELETE` các dòng stub vi phạm trong cùng migration là chấp nhận được cho dữ liệu demo giả, không bao giờ cho dữ liệu nhập-DEV thật).
- **FOREIGN KEY**: chỉ thêm FK nếu bảng đích tồn tại local với kiểu cột khớp. Nếu bảng đích ngoài phạm vi Sprint, ghi FK bị hoãn trong khối comment cuối migration; hoãn tới sprint port phụ thuộc.
- **Trigger + hàm audit `_log`**: DEV cũ dùng trigger BEFORE INSERT/UPDATE (kiểm tra kiểu-FK) và trigger AFTER (ghi audit vào bảng history `*_log_h`). Endpoint Sprint 1 chỉ-đọc, nên trigger thiếu không phá lưu lượng Sprint 1. Hoãn tạo trigger tới sprint port các hàm / bảng history phụ thuộc. Ghi trigger bị hoãn rõ ràng.
- **Index ngoài PK/UK**: index hiệu năng thêm (ví dụ local `ix_*` trên date+tenant) được phép kể cả khi DEV không có — index thêm không hại parity.

**Checklist parity trước migration (mở rộng checklist cột ở trên)**

```
- [ ] Cột PRIMARY KEY + thứ tự khớp DEV
- [ ] Constraint UNIQUE từ chuỗi `*_uk*` được mang theo
- [ ] Constraint CHECK từ chuỗi `*_chk*` được mang theo (kèm dọn seed nếu cần)
- [ ] FOREIGN KEY được mang theo HOẶC ghi là hoãn với bảng đích
- [ ] Kiểu cột BIZ_KEY khớp DEV (VARCHAR(20) — KHÔNG phải 32)
- [ ] Trigger hoặc đã port hoặc ghi là hoãn
- [ ] Đã kiểm chứng nhánh wb_get_next_val (Sprint 1 xác nhận: trả NULL cho cả 17 bảng — không phụ thuộc)
```

**Audit parity schema Sprint 1 (2026-04-29)**

Các migration Sprint 1 ban đầu (V13 / V15) tạo bảng tập con. Audit bắt được khoảng trống và ship hai migration sửa:
- **V23**: parity cấp-cột — thêm 144 cột cũ thiếu trên 6 bảng (quan trọng nhất, BUSA_DOC_MSTR từ 20 lên 100 cột).
- **V24**: parity cấp-constraint — căn BUSI_INFM.BIZ_KEY VARCHAR(32)→(20), căn lại 5 PRIMARY KEY, thêm 1 UNIQUE + 11 CHECK + 5 FOREIGN KEY.

Schema local giờ khớp DEV theo từng cột VÀ từng constraint cho cả 17 bảng Sprint 1, ngoại trừ các mục bị hoãn ghi trong trailer của V24 (FK tới `cash_repy_mstr` / `mrec_pic_store`; cả 28 trigger audit-log + sync) đang chờ port phụ thuộc Sprint 2+. JPA entity tiếp tục chỉ ánh xạ tập con cột Sprint 1 — đó là phạm vi đúng cho chúng.

**Audit parity Sprint 1 cuối (2026-04-30)** — audit đầy đủ trên cả 23 bảng local (auth + Sprint 1) so với DEV. Thêm hai migration sửa:
- **V28**: sửa schema — thêm cột DEV thiếu (`_empl_nm`, `reg_uid`); nới precision NUMERIC để khớp DEV (`(20,6)` cho amount, `(N)` cho số seq, 30+ cột); nới VARCHAR(50)→(100) cho `user_id`/`reg_id`/`mod_id`; thêm UNIQUE `acct_trns_hstr_uk01`, `busi_infm_uk01`; thêm CHECK `busi_infm_chk01`; căn lại PK `busi_cond_daily_sumr_detl` và PK `user_login_his` về hình dạng DEV.
- **V29**: seed DEV-thật vòng 2 — 226 dòng nhập vào 5 bảng trước đó rỗng (`bat_call_his`, `busa_doc_rcv_pay_his`, `einv_fee_detl`, `einv_tax_detl`, `ext_tran_call_his`).

Báo cáo đầy đủ ở [docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md](../docs/refactor-wecms/audits/db-parity-audit-2026-04-30.md).

**Quy tắc audit (thêm vào file này):** **Chạy một audit parity local-vs-DEV đầy đủ ở cuối mỗi sprint** trước khi tuyên bố schema "xong". So sánh từng-bảng: cột, kiểu, độ dài, default, PK/UK/CHECK/FK, index, trigger, số dòng. Ship migration sửa bổ sung khi tìm thấy khoảng trống. Không bao giờ sửa migration đã áp dụng; không bao giờ auto-xóa cột/bảng chỉ-local; không bao giờ mù quáng thêm bảng chỉ-DEV.

### Chính sách DB Function / Legacy Function

Refactor đang rời xa các helper PL/pgSQL kiểu JEX cũ nghiêng về logic service Java. Code mới phải theo các quy tắc này:

**Nên**

- Ưu tiên logic Java/service, repository Spring Data JPA, và hành vi identity / `SERIAL` / `IDENTITY` / `SEQUENCE` native PostgreSQL chuẩn cho sinh ID và phân giải giá trị.
- Với các hình dạng cột cũ đã mã hóa một pattern giá trị sinh (ví dụ `BIZ_KEY = NTNL_CD_CHNL_ID_NNNNNN`), triển khai generator trong một `@Service` và ghi giá trị từ Java — đừng ủy thác cho một hàm stored.
- Ghi mọi phụ thuộc DB-function *không-tránh-được* trong mục này: migration nào tạo nó, service nào tiêu thụ, và Sprint nào retire nó.
- Trước khi thêm migration mới, kiểm tra listing `src/main/resources/db/migration/` hiện có ở §"Database (LOCAL)" trên và tiếp tục đánh số đúng. Migration Sprint 1 là V13–V19 vì V11 + V12 đã bị Phase 1 bootstrap chiếm.
- Làm migration mới idempotent khi có thể:
  - `CREATE TABLE IF NOT EXISTS`
  - `CREATE INDEX IF NOT EXISTS`
  - `ALTER TABLE … ADD COLUMN IF NOT EXISTS`
  - `INSERT … ON CONFLICT DO NOTHING` cho dòng seed
- Với sửa schema (ví dụ căn CHAR → VARCHAR), `ALTER COLUMN … TYPE` với cùng kiểu đích là no-op — an toàn chạy lại.

**Không nên**

- Đừng đưa vào phụ thuộc DB-function cũ mới trong code Spring Boot đã refactor (banking, payment, member, e-invoice, operation, settings, …). Không `wb_get_next_val(...)` mới, không helper PL/pgSQL tùy tiện mới, không `CREATE FUNCTION` trong migration tính năng.
- Đừng gọi hàm DB cũ trực tiếp từ code tính năng Sprint (controller, service, repository, scheduler). Nếu thật sự cần một cái, nó đi qua một phễu duy nhất — xem "Surviving legacy functions" bên dưới — không bao giờ qua `@Query(nativeQuery = true)` rải rác mỗi repository.
- Đừng đưa vào JPA `@Procedure` / `StoredProcedureQuery` / `@NamedNativeQuery` cho stored procedure trong công việc tính năng Sprint 2+.
- Đừng thêm cột `SERIAL` / `BIGSERIAL` / `IDENTITY` khi bảng cũ không có một cái và giá trị có thể lắp ráp trong Java (đa số bảng JEX mã hóa ý nghĩa trong PK của chúng — giữ ý nghĩa trong Java).
- Đừng sửa một Flyway migration đã áp dụng vào DB local của lập trình viên. Nếu cần sửa, ship migration mới (cặp hotfix V18 / V19 là tiền lệ đang chạy).
- **Với `wb_get_next_val`, chỉ duy trì nhánh cho các bảng đã migrate local có nhánh DEV.** Quy tắc: khi một bảng được migrate vào Flyway local VÀ `wb_get_next_val` của DEV có nhánh cho nó, nhánh đó thuộc về local. Đừng port nhánh cho bảng chưa migrate local. Đừng port mù quáng cả hàm DEV. Với bảng Sprint 2+, nhánh được thêm trong cùng sprint migration port bảng — xác nhận với lead trước khi viết.

**Surviving legacy functions (giữ theo ngoại lệ, không phải mặc định)**

| Function / sequence | Tạo bởi | Tiêu thụ bởi | Lý do giữ | Retire khi |
|---|---|---|---|---|
| `wb_get_next_val(table, field, p1..p6)` — **13 nhánh** (Sprint 0: 2 + Sprint 1: 11) | V11 (stub 2-nhánh) → V25 (over-port 164-nhánh, lỗi ngay) → V26 (cắt còn 2) → **V27** (sửa: nhánh bảng-đã-migrate Sprint 0 + Sprint 1) | `common.legacy.LegacyFunctionRepository`, `domain.auth.bootstrap.{BusinessKeyGeneratorService, EmployeeNumberGeneratorService, CompanyBootstrapService}` (đều giữ nhưng bất hoạt — không caller production sau khi `BootstrapRunner` bị gỡ); **SQL setup thủ công của DBA** cũ vẫn gọi cùng hàm trực tiếp với DEV / UAT / PROD | Các nhánh phủ đúng tập bảng đã migrate local có nhánh DEV. Sprint 0: company key + employee number. Sprint 1: 11 bảng có nhánh DEV (xem §"Key-generation rule" bên dưới để có danh sách). Bảng Sprint 2+: nhánh thêm cùng sprint migration của bảng. | Khi Phase 3 retire đường bootstrap. Nhánh Sprint 1 retire khi mỗi tính năng có generator phía Java. |
| sequence `sq_biz_key` | V11 | `wb_get_next_val('busi_infm','biz_key',…)` (qua nhánh BUSI_INFM) | Giữ parity cũ cho generator company-key. | Cùng cửa sổ retire như `wb_get_next_val`. |
| nhánh `wb_get_next_val('trco_infm','trco_key',…)` | V85 (MR Contacts, 2026-05-13) | `LegacyFunctionRepository.nextTrcoKey` → `ContactsService.create` | OQ-4 (2026-05-12): căn với `SeqHelper.getNextVal` cũ để contact do refactor tạo tương tác được với đường inquiry/search JEX đang chạy mà cách dùng chưa kiểm chứng đầy đủ. Thân nhánh copy NGUYÊN VĂN từ DEV — `SELECT nextval('sq_trco_key')`. | Bỏ nhánh (và `sq_trco_key`) và thay cuộc gọi phễu bằng generator Java khi hệ thống WeCMS / WABOOKS cũ không còn gọi `wb_get_next_val` cho `trco_infm`. Không deadline cố định — đánh giá lại lúc cutover coexistence. |
| sequence `sq_trco_key` | V85 | `wb_get_next_val('trco_infm','trco_key',…)` (Contacts) | OQ-4 căn với không gian tên TRCO_KEY toàn cục của DEV. Sequence local bắt đầu ở 1.000.000 — cao hơn nhiều trần ~9300 hiện tại của DEV nên contact mới tạo không bao giờ va chạm seed V87. | Cùng cửa sổ retire như nhánh `wb_get_next_val` ở trên. |
| `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang_cd)` | V86 (MR Contacts, 2026-05-13) | `LegacyFunctionRepository.resolveNapasOrCitadBankName` → `ContactsService.list / getOne` | OQ-7 (2026-05-12): parity với IDO `TRCO_INFM_R016` cũ tại `IDO.TRCO_INFM_R016.xml:63`. Mức phủ của `BANK_INFM` cho không gian mã NAPAS + CITAD (và các quy tắc KH-fallback / biến-thể-ngôn-ngữ của hàm) chưa kiểm chứng cho luồng này; dùng hàm tránh drift parity ẩn. Seed `BANK_INFM` KHÔNG mở rộng. Thân copy NGUYÊN VĂN từ DEV; đọc `tb_firm_bank_code` (V59). | Khi không gian mã NAPAS + CITAD được điều hòa trong `BANK_INFM` (cả hai chiều mã, cả bốn cột ngôn ngữ), bỏ port hàm và thay cuộc gọi phễu bằng cuộc gọi `BankCdResolver`. Không deadline cố định — đánh giá lại khi MR tính năng tương lai audit parity `BANK_INFM`. |
| nhánh `wb_get_next_val('appr_line_infm','appr_line_no',…)` | V91 (MR Approval Line, 2026-05-13) | `LegacyFunctionRepository.nextApprLineNo` → `ApprovalLineService.create` | OQ-7 (2026-05-13): căn với `SeqHelper.getNextVal(util, idoCon, ApprLineInfm.class, "appr_line_no")` cũ và giữ nhất quán với pattern refactor Contacts (V85) đã thiết lập. Thân nhánh copy NGUYÊN VĂN từ DEV — `SELECT COALESCE(MAX(appr_line_no::numeric), 0) + 1 FROM appr_line_infm WHERE biz_key = $3`. MAX+1 theo từng tenant, không phải sequence. | Bỏ nhánh và thay cuộc gọi phễu bằng generator Java theo-từng-tenant khi hệ thống WeCMS / WABOOKS cũ không còn gọi `wb_get_next_val` cho `appr_line_infm`. Không deadline cố định. |
| nhánh `wb_get_next_val('chart_acct_infm','chart_acct_seq_no',…)` | V95 (MR Chart of Accounts, 2026-05-13) | `LegacyFunctionRepository.nextChartAcctSeqNo` → `ChartOfAccountsService.create` | Căn với `SeqHelper.getNextVal(util, idoCon, ChartAcctInfm.class, "chart_acct_seq_no")` cũ ở tầng DAO JEX. Giữ nhất quán với pattern refactor `appr_line_infm` (V91) đã thiết lập — cả hai dùng MAX+1 theo từng tenant, không phải sequence. Thân nhánh copy NGUYÊN VĂN từ DEV — `SELECT COALESCE(MAX(chart_acct_seq_no::numeric), 0) + 1 FROM chart_acct_infm WHERE biz_key = $3`. Concurrency: service gói cuộc gọi + INSERT trong một `@Transactional`; xung đột PK lộ ra dạng `DataIntegrityViolationException` và `ChartOfAccountsService.create` retry một lần trước khi lộ `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE`. | Bỏ nhánh và thay cuộc gọi phễu bằng generator Java theo-từng-tenant khi hệ thống WeCMS / WABOOKS cũ không còn gọi `wb_get_next_val` cho `chart_acct_infm`. Không deadline cố định. |

**Quy tắc sinh-key cho bảng cũ đã migrate**

Trước khi thêm một bảng cũ migrate mới, tác giả Spring Boot PHẢI audit `wb_get_next_val` cho bảng đó:

```sql
SELECT wecmsvndev.wb_get_next_val('<lower_table>', '<lower_field>', NULL, NULL, NULL);
```

Nếu cuộc gọi trả về non-NULL, trường đó dùng key/code sinh kiểu cũ (pattern `{prefix}_{date}_{seq}`, sequence đơn điệu, v.v.).

- **Nếu tính năng Sprint là chỉ-đọc / nhập dòng DEV**: giữ giá trị PK DEV thật nguyên văn trong migration seed (`INSERT … ON CONFLICT DO NOTHING` với giá trị DEV làm conflict target). KHÔNG sinh ID mới. KHÔNG thêm nhánh `wb_get_next_val` local.
- **Nếu tính năng Sprint tạo dòng mới**: triển khai generator trong một `@Service` Java (ưu tiên — xem quy tắc "Chính sách DB Function / Legacy Function" ở trên). Chỉ thêm nhánh `wb_get_next_val` local như cầu nối tương thích tạm nếu một deadline interop cũ cứng buộc; ghi nhánh và kế hoạch retire của nó trong mục này.

**Audit sinh-key Sprint 1 (2026-04-29)**: 11 trên 17 bảng Sprint 1 có nhánh DEV `wb_get_next_val` (`acct_mstr.prn_seq`, `acct_trns_hstr.acct_seq_no`, `acct_trns_rcv_pay_evid_file.file_id`, `acct_trns_rcv_pay_infm.rcv_pay_seq_no`, `busa_doc_mstr.doc_id`, `busa_doc_rcv_pay_his.rcv_pay_his_seq_no`, `busi_cond_daily_sumr_detl.dtl_seq_no`, `einv_fee_detl.disp_ord`, `einv_tax_detl.disp_ord`, `ext_tran_call_his.seq_no`, `napas_remit_trns_hstr.seq_no`). Endpoint Sprint 1 hoàn toàn chỉ-đọc — backend không bao giờ insert vào các bảng này. Migration seed (V17, V22) dùng giá trị PK DEV thật. **Không thêm nhánh `wb_get_next_val` mới; không thêm generator Java.** Các ghi Sprint 2+ tạo dòng mới trong bất kỳ bảng nào trong số này PHẢI triển khai generator phía-service Java (không phải nhánh local mới).

Nếu một tính năng tương lai thật sự cần một hàm kiểu-cũ mới, ghi nó trong bảng này *trước khi* viết migration và nêu kế hoạch retire trong cùng MR. Không hàm nào không-có-tài-liệu được phép xuất hiện trong `src/main/resources/db/migration/`.

**Audit Sprint 1 (2026-04-29)**

Đã kiểm chứng: không code banking / e-invoice / operation Sprint 1 nào gọi `wb_get_next_val` hay bất kỳ helper DB cũ nào khác. Mọi ID Sprint 1 hoặc là PK composite cũ (không surrogate) hoặc là chuỗi service lắp trong Java. `CREATE FUNCTION` duy nhất trong `db/migration/` là hàm bootstrap V11 ở trên.

### Dữ liệu tham chiếu — nguồn chuẩn duy nhất

Dữ liệu tham chiếu phải được tra từ bảng chuẩn của nó, không bao giờ nhân bản thành map tĩnh trong code.

| Tham chiếu | Bảng nguồn | Loader / consumer | Ghi chú |
|---|---|---|---|
| Mã ngân hàng → tên hiển thị (`BANK_CD` → `RPRS_BANK_NM`) | `BANK_INFM` (V20) | `BankInfmRepository` → `BankCdResolver` | Cache làm nóng một lần lúc startup; `BankCdResolver` fallback sang tra DB theo từng-request khi cache miss. **Không bao giờ** hardcode ánh xạ mã-ngân-hàng → tên-ngân-hàng trong service hay DTO. |
| Mã loại tài khoản → nhãn (`ACCT_TYPE_CD`) | (không bảng riêng trong legacy) | `BankCdResolver.resolveAcctTypeKey` → enum `AcctTypeCd` (`common/enums/`); `BankCdResolver.resolveAcctTypeNm` là fallback chỉ-tiếng-Anh cũ. | Wire ship **cả** `acct_type_nm` (nhãn tiếng Anh cũ, giữ một chu kỳ MR) và `acct_type_key` (khóa i18n dưới `bank.acct_type.*`). Frontend render qua `t(acct_type_key)` và fallback về `acct_type_nm` khi key là null. |

Nếu một tính năng tương lai cần một bảng tham chiếu mới, thêm một entity + repository, seed nó từ dữ liệu DEV thật qua Flyway, và định tuyến tra cứu qua repository. Đừng hardcode nội dung bảng vào một `Map.of(...)` trong một `@Component`.

### Domain enums — `common/enums/`

> **Chính sách enum refactor hiện đại hóa (2026-05-08).** Các mã domain cũ
> ổn định (`*_CD`, `*_DV`, `*_TP`, `*_STS`, `*_YN`) tồn tại dưới dạng enum Spring
> trong `com.kosign.wecms.common.enums.*`. Mỗi enum mang một accessor `code()`
> ổn định (giá trị DB cũ nguyên văn) và một accessor `i18nKey()` tùy chọn.
> Refactor không bao giờ port interface `GenericEnum` của JEX, method
> `getLabel()`, các annotation `@JsonValue` / `@JsonCreator`, hay bất kỳ nhãn
> đã-localize nào bên trong thân enum — nhãn nằm trong
> `wecms-web-main/locales/{en,ko,vi}.json` và được phân giải qua `t(key)` ở frontend.

**Hình dạng enum chuẩn (mọi domain enum theo nó):**

```java
public enum InoutDv {
    IN("1", "tx.inout.in"),
    OUT("2", "tx.inout.out"),
    ETC("0", null);

    private final String code;
    private final String i18nKey;

    InoutDv(String code, String i18nKey) { this.code = code; this.i18nKey = i18nKey; }

    public String code()    { return code; }
    public String i18nKey() { return i18nKey; }   // null when no verified translation exists yet

    public static Optional<InoutDv> fromCode(String raw) { … }
}
```

**Quy tắc (không thương lượng):**

- **Không `EnumType.ORDINAL`.** Enum hoặc không-persist (pattern hôm nay — cột DB giữ `VARCHAR(N)` chứa mã cũ, Java đọc chúng dạng `String` và dùng `Enum.fromCode(...)` ở tầng service) hoặc, khi cần ánh xạ persistence, ánh xạ qua một JPA `@Converter` gọi `code()` / `fromCode()`. Không bao giờ `@Enumerated(EnumType.STRING)` trên cột có giá trị DB là mã cũ (tên *constant* và *code* cố ý khác nhau — ví dụ `IN("1")`).
- **Giá trị DB giữ chính xác.** Trường `code` là chuỗi cũ nguyên văn. Constant mới mở rộng tập mã cũ; rename bị cấm (rename một constant enum không đổi gì trên wire vì `code()` là nguồn chuẩn).
- **Không nhãn đã-localize trong enum.** `i18nKey()` trả về một khóa ổn định dưới namespace theo-tính-năng (`bank.acct_type.*`, `tx.inout.*`, `fb_history.service.*`, `settings.firm_banking_history.service_type.*`). Frontend phân giải chuỗi đã-localize. Đặt `i18nKey()` thành `null` là tín hiệu rõ ràng "bản dịch chưa kiểm chứng" — frontend render mã thô hoặc nhãn fallback phía-wire và không bao giờ để trống.
- **Không `@JsonValue` / `@JsonCreator`.** Enum không bao giờ lọt ra wire — API contract mang chuỗi mã cũ thô CỘNG một trường anh em `*_key` (ví dụ `acct_type_cd` + `acct_type_key`). Jackson serialize projection `String` của enum, không phải bản thân enum.
- **Không bulk-port.** Mỗi enum xuất hiện trong MR có một consumer thật. Enum dùng-một-lần giữ nguyên chỗ; consumer thứ hai là điều kiện kích để tách ra.
- **Thêm một mã mới:** một constant enum + một entry locale `bank.<feature>.<code>` mỗi locale hỗ trợ (`en` + `ko` + `vi`; không bao giờ `km`). Khi chưa có bản dịch kiểm chứng, bỏ entry locale — fallback missing-key của frontend xử lý khoảng trống.

**Chỉ mục enum (consumer hiện tại — giữ đồng bộ khi enum xuất hiện):**

| Enum | Nguồn legacy | Cột DB | Consumer | Namespace i18n |
|---|---|---|---|---|
| [`LangCd`](src/main/java/com/kosign/wecms/common/enums/LangCd.java) | `wabooks/enums/LangCd.java` | `BUSI_INFM.LANG_CD`, JWT claim `langCd` | `AuthService`, `AuthController`, `MessageResolver` | (enum cố định — không set khóa) |
| [`YesOrNo`](src/main/java/com/kosign/wecms/common/enums/YesOrNo.java) | `wabooks/enums/YesOrNo.java` | mọi cột `*_YN` | helper cho kiểm tra `Y`/`N` tầng-service | (không — projection boolean) |
| [`TranTp`](src/main/java/com/kosign/wecms/common/enums/TranTp.java) | `wabooks/enums/TranTp.java` (mã 201–204) | `TB_FIRM_TRAN.TRAN_TP` | `FirmBankingServiceNameResolver.resolveF` | `fb_history.service.*` |
| [`InoutDv`](src/main/java/com/kosign/wecms/common/enums/InoutDv.java) | `wabooks/enums/InoutDv.java` (1, 2, 0) | `ACCT_TRNS_HSTR.INOUT_DV` | `TransactionService` (tổng + filter + ánh xạ dòng) | `tx.inout.*` (khóa đã đăng ký, entry locale hoãn) |
| [`AcctTypeCd`](src/main/java/com/kosign/wecms/common/enums/AcctTypeCd.java) | `wabooks/enums/AcctTypeCd.java` (01–08) | `ACCT_MSTR.ACCT_TYPE_CD` | `BankCdResolver.resolveAcctTypeKey`, tập filter `AccountService` | `bank.acct_type.*` (EN + KO kiểm chứng) |
| [`FirmBankingSvcType`](src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) | (không có cặp JEX — discriminator view V60) | `V_FIRM_BANKING_HISTORY.SVC_TYPE` | `FirmBankingHistoryService.toRow` | `settings.firm_banking_history.service_type.*` (EN + KO + VI kiểm chứng) |
| [`PrdtType`](src/main/java/com/kosign/wecms/common/enums/PrdtType.java) | `wabooks/enums/PrdtType.java` (14 chữ cái cũ; refactor chỉ mô hình `F`, `M`, `H`) | `BUSI_INFM.PRDT_TYPE`, JWT claim `prdtType` | `AuthService.login` (đóng dấu), `AuthService.me` (lặp lại) | `auth.prdt_type.*` (EN + KO + VI kiểm chứng cho F/M/H) |

**Enum bị hoãn** — port mỗi cái khi module của nó migrate, không sớm hơn:

- `BusaDocType` (loại doc e-Invoice — `EInvoiceService.resolveDocType` là single-consumer; quy tắc hai-cuộc-gọi).
- `ApprSts`, `ProcStat`, `ProcStatF` — xử lý payment chưa migrate sang Spring.
- `ProvStts` — trạng thái cấp phép e-Invoice; consumer giữ `provStts` thô cho tới khi `ProvStts.fromCode` có call site thật.
- `BalAmtSign` — không consumer.
- ~190 enum cũ khác — bulk-port bị cấm; port khi consumer thật xuất hiện.

**BLOCKERS** (cố ý không nâng lên enum):

- **Trạng thái e-Invoice** — backend hiện lộ `einv_invoicestatus` dạng chuỗi đã-localize từ hàm DB cũ `wb_get_invoice_status(value, LANG_CD)` (IR-4 hoãn). Cho tới khi port backend thay chuỗi đã-localize bằng giá trị có mã, regex frontend trong `lib/api/einvoiceStatus.ts` giữ nguyên.
- **Chính sách per-leg `FBH_SUCCESS_CODES`** — leg F dùng mã VAN 8-ký-tự, NAPAS dùng mã 4-ký-tự, NXISAS gửi free-text. Enum `RsCd` cũ chỉ phủ 3 trên 4 biến thể. Nâng cái này lên một resolver Spring duy nhất cần một quyết định chính sách per-leg rõ ràng; tới khi đó, `Set` frontend tại `mappers.ts:562` giữ nguyên.

**Bản dịch chưa giải quyết** (KHÔNG bịa — thêm khi kiểm chứng):

- `tx.inout.in` / `tx.inout.out` / `tx.inout.etc` — chưa có entry file locale. Thêm khi module Transaction History tái-hiện nhãn phía server.
- `bank.acct_type.*` — entry `vi.json` cố ý bỏ. Bản dịch tiếng Việt cho 7 mã ACCT_TYPE_CD (02, 03, 04, 05, 06, 07, 08) không có trong nguồn dự án kiểm chứng nào. Frontend fallback về `acct_type_nm` (tiếng Anh) dưới locale VI cho tới khi bản dịch kiểm chứng xuất hiện.

### Screen Option (View Setting) — `domain/setting/`

View Setting là một **tính năng chính WeCMS global / common**, không phải deliverable Sprint 1 và không phải endpoint riêng Banking. Mọi trang list trong toàn bộ sản phẩm (Banking, Payment, Add-on, Settings, mọi module tương lai) tái dùng cùng controller, service, DTO, và bảng — chỉ khóa theo `WEB_SVC_ID`. Thêm một trang mới vào View Setting là một thay đổi chỉ-Flyway-seed ở backend; không controller mới, không method service mới, không DTO mới. Popover (Figma 100:13382 / `comm_0108_001` cũ) là tham chiếu chuẩn. Module backend là `domain/setting/`; các bảng là `SYS_SCR_OPTION_WECMS` (mặc định hệ thống, PK `WEB_SVC_ID + FIELD_ID`) và `USER_SCR_OPTION_WECMS` (override theo từng-user, PK `BIZ_KEY + USER_ID + WEB_SVC_ID + FIELD_ID`). Schema gốc xuất hiện ở V30 cùng consumer đầu tiên của Sprint 1; bản thân schema không phụ thuộc màn hình và giữ nguyên — chỉ thêm dòng seed mỗi trang.

**Endpoints** (`domain/setting/controller/ScreenOptionController.java`):

| Method | Path | Trả về | Legacy |
|---|---|---|---|
| GET    | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (gộp cho một area; lazy-INIT lần gọi đầu mỗi phạm vi) | `comm_0108_001_r001_osmc` (lazy-INSERT phản chiếu — xem [docs/jex/features/view-setting.md §1.6](../docs/jex/features/view-setting.md)) |
| GET    | `/api/v1/screen-options/{webSvcId}/areas`       | `ScreenAreaListResponse` (các area phân biệt + count) | `SYS_SCR_OPTION_WECMS_R002` |
| PUT    | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (làm mới) | `comm_0108_001_u002_osmc` |
| DELETE | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (init-lại từ sys) | `comm_0108_001_u001_osmc` (DELETE-rồi-INSERT-từ-sys; bảng user luôn được populate sau reset — xem [docs/jex/features/view-setting.md §1.7](../docs/jex/features/view-setting.md)) |

**Quy tắc (không thương lượng):**

- Mọi trang list mới áp dụng View Setting **PHẢI** seed tập cột của nó vào `SYS_SCR_OPTION_WECMS` trong cùng Flyway migration ship trang. Seed là nguồn chuẩn — `FALLBACK_COLUMNS` của frontend chỉ là vật thay thế lúc-vẽ, không bao giờ là nguồn chuẩn runtime.
- `WEB_SVC_ID` là id trang JEX cũ (snake-case, ví dụ `fnsh_0001_001`). **Đừng** bịa id mới; tái dùng id JEX để frontend giữ nhịp với tài liệu cũ.
- `FIELD_ID` là chữ hoa và ổn định qua các release (ví dụ `BANK`, `ACCOUNT_NICKNAME`). Rename một `FIELD_ID` làm mồ côi mọi override user đã lưu — hãy coi tập FIELD_ID là một public contract.
- Các dòng `FIX_YN='Y'` (cột bị khóa) **luôn** đi qua Save dưới dạng `USE_YN='Y'` bất kể payload. Service bắt buộc điều này — code trang ở frontend render checkbox `disabled`, nhưng quy tắc backend là nguồn chuẩn. Không bao giờ nới lỏng `FIX_YN` phía server mà không căn với legacy.
- Save dùng **delete-then-insert** cho phạm vi (BIZ_KEY, USER_ID, WEB_SVC_ID) — phản chiếu `comm_0108_001_u002` và là atomic qua `@Transactional`. DTO mang TOÀN BỘ danh sách cột gộp (gồm cả dòng USE_YN='N') để thứ tự sống sót qua save.
- Reset xóa dòng user cho phạm vi và **init-lại từ sys ngay** trong cùng transaction (parity legacy với lazy-init `comm_0108_001_r001` + xóa `u001`). Bảng user luôn được populate sau reset; response dựng từ dòng vừa-init trực tiếp. Xem [docs/jex/features/view-setting.md §1.7](../docs/jex/features/view-setting.md).
- Load (GET) làm **lazy-INSERT lần gọi đầu** mỗi phạm vi `(BIZ_KEY, USER_ID, WEB_SVC_ID, DYM_AREA_ID)`: nếu user có 0 dòng cho phạm vi, mọi dòng sys được copy vào `USER_SCR_OPTION_WECMS` (FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, DYM_AREA_ID, REG_ID/MOD_ID = user hiện tại) trước khi dựng response. Các load sau bỏ qua nhánh init. Phản chiếu `comm_0108_001_r001` cũ. Xem [docs/jex/features/view-setting.md §1.6](../docs/jex/features/view-setting.md).
- Các FIELD_ID lạ trong payload save (drift bundle frontend) bị âm thầm bỏ — không bao giờ throw. Response GET của popover khi thành công sẽ điều hòa user với tập cột chuẩn.
- Cột mới thuộc Flyway migration **của riêng** trang đó. Đừng lén chèn dòng `INSERT` vào V30 — file đó đã đóng (chỉ phủ schema gốc + consumer đầu tiên `fnsh_0001_001`). Mỗi trang tương lai thêm dòng của nó trong migration ship trang, với comment kiểu `-- Seed: <page name> column set`. Đánh số file migration là slot trống kế tiếp trong `db/migration/`; seed không liên quan gì tới V30 và không bị V30 chặn.
- Áp dụng một trang mới ở backend là **chỉ-seed**. Không controller mới, không method service mới, không DTO mới, không thay đổi entity. Các endpoint `GET / PUT / DELETE /api/v1/screen-options/{webSvcId}/{dymAreaId}` đã nhận mọi cặp `(WEB_SVC_ID, DYM_AREA_ID)` — chúng được thiết kế cho toàn-sản-phẩm, không cho một Sprint. Nếu một refactor bị cám dỗ fork `ScreenOptionController` mỗi module ("BankingScreenOptionController", "PaymentScreenOptionController", v.v.), refactor đó sai — mở rộng seed, không phải bề mặt API.
- **Quy ước giá trị `WEB_SVC_ID` là kebab-case route-based ngữ nghĩa** — `<module>-<page>[-<type>]` (ví dụ `banking-account-inquiry-vnd`). Registry trung tâm của frontend refactor tại [`wecms-web-main/lib/viewSettingScreens.ts`](../wecms-web-main/lib/viewSettingScreens.ts) là nguồn chuẩn duy nhất — API và DB đều khóa theo cùng chuỗi mà registry export. `@Pattern` backend bắt buộc định dạng (`screenId` cần ≥ 2 đoạn kebab qua `^[a-z][a-z0-9]*(-[a-z0-9]+)+$`; `dymAreaId` chấp nhận một đoạn nên `default` pass). Regex cố ý từ chối id JEX cũ (`fnsh_0001_001`, `fnap_0001_001`, v.v. — không cho gạch dưới) để một frontend cũ không thể mồ côi dòng dưới namespace sai. Token trùng lặp (`banking-banking-account-inquiry-vnd`, `banking-account-inquiry-account-inquiry-vnd`) và id không-module (`account-inquiry-vnd`) pass regex nhưng fail review — reject trong MR. Input path xấu trả về `WBE_VALIDATION` qua envelope chuẩn; giá trị screenId lạ (regex pass nhưng không có dòng `SYS_SCR_OPTION_WECMS`) trả về `WBE2001` từ `ScreenOptionService`.
- **`DYM_AREA_ID` là bắt buộc** trong mọi cuộc gọi API và mọi dòng DB. Trang một-area dùng `default`; trang nhiều-area dùng tên kebab-case có nghĩa (`summary`, `detail`, `current-accounts`). V32 backfill các null và khóa cả hai cột thành `NOT NULL`. Xem [docs/jex/features/view-setting.md §1.5](../docs/jex/features/view-setting.md) để biết quy tắc đặt tên đầy đủ.
- **FIELD_ID special — `CHECKBOX` (`DISP_ORD=0`) và `ETC` (`DISP_ORD=99`)** — đi qua dữ liệu View Setting để thứ tự cột / Save / Reset bảo toàn chúng, nhưng chúng KHÔNG BAO GIỜ user cuối sửa được. Popover ẩn chúng; backend khóa chúng. `ScreenOptionService.SPECIAL_FIELD_IDS` là tập chuẩn phía API: khi Save, các dòng có `FIELD_ID` trong tập đó luôn persist với `USE_YN='Y'` và `DISP_ORD` lấy nguyên văn từ seed hệ thống, bất kể payload — một frontend cũ hay lỗi không thể nới lỏng quy tắc. Với cột không-special, Save tin `DISP_ORD` của frontend vì popover đánh số dày đặc bắt đầu từ 1 sau mỗi reorder. Trang seed `CHECKBOX` chỉ khi bảng của nó render cột chọn-dòng (Account Inquiry VND ở V33); ĐỪNG bulk-seed `ETC` trên các trang không có cột hành động.

**Các trang hiện đã cấu hình:**

| WEB_SVC_ID | Trang | Migration | Cột |
|---|---|---|---|
| `banking-account-inquiry-vnd` (DYM_AREA_ID `default`) | Banking > Account Inquiry (VND) | V30 (seed ban đầu) → V32 (chuyển sang id ngữ nghĩa + DYM_AREA_ID NOT NULL) → V33 (dòng special CHECKBOX) → V34 (giá trị SIZE) → V35 (siết parity schema — kiểu cột/NN, không đổi dòng) | CHECKBOX · BANK · ACCOUNT_NICKNAME · BALANCE · AVAILABLE_BALANCE · VAS_ACCOUNT (ẩn mặc định) · LAST_UPDATE |

Thêm bản sinh đôi FCY (`fnsh_0005_001`), Approval Box, Transaction History, e-Invoices, Operation Status, các trang list Payment, bảng Settings, và mọi trang-list-có-list-icon tương lai xảy ra trong migration seed của riêng các tính năng đó — không bao giờ là một migration "seed tất cả trang" quét sạch, và không bao giờ là một thay đổi code backend. Giữ seed kề bên tính năng sở hữu nó. Danh sách trên lớn dần theo thời gian khi adoption lan ra; controller / service / DTO thì không.

### Sort bảng dùng chung — whitelist `common/web/` + enum theo-từng-domain

Sort theo header bảng dùng cùng các khóa `FIELD_ID` như View Setting và chia sẻ một API contract chung trên mọi endpoint list. Chu kỳ frontend (`default → ASC → DESC → default` — xem [wecms-web-main/CLAUDE.md §13a](../wecms-web-main/CLAUDE.md)) gửi hai query param — `sortField` (`FIELD_ID` View Setting) và `sortDirection` (`ASC` / `DESC`) — và backend phân giải chúng qua một enum whitelist theo-từng-domain, không bao giờ là một chuỗi SQL thô.

**Primitive dùng chung — [`common/web/`](src/main/java/com/kosign/wecms/common/web/):**

- `SortDirection` — enum `{ASC, DESC}` với `parse(String)` khoan dung trả về `null` khi rỗng / giá trị lạ để service fallback về ORDER BY mặc định.
- `SortableField` — interface mọi enum whitelist theo-từng-domain triển khai; lộ một accessor `entityProperty()` trả về tên property JPA entity (ví dụ `"bal"`, `"acctNickNm"`).

**Enum whitelist theo-từng-domain** (một cái mỗi endpoint list):

- Nằm cạnh service tiêu thụ nó (ví dụ `domain/banking/service/AccountSortField.java`).
- Tên constant phản chiếu `FIELD_ID` View Setting (khóa ngữ nghĩa chữ hoa — `BANK`, `BALANCE`, `LAST_UPDATE`).
- Mỗi constant ánh xạ tới property JPA khớp trên entity — nơi duy nhất input wire được dịch thành một cột sort.
- `parse(String)` khoan dung trả về `null` cho giá trị wire lạ / rỗng để service fallback về ORDER BY mặc định (không exception, không vọng input user vào một truy vấn).

**Quy tắc (không thương lượng):**

- **Không bao giờ nối một `sortField` thô vào truy vấn JPQL / native.** Mọi cột sort được đi qua enum whitelist theo-từng-domain — chuỗi wire được khớp với `valueOf(...)` và hoặc phân giải thành một constant enum hoặc trả về `null`. Không có lựa chọn thứ ba, và không chuỗi SQL nào chứa giá trị user cung cấp.
- **Dùng Spring Data `Sort` dựng từ `entityProperty()` của enum.** `Sort.by(Sort.Order.asc(field.entityProperty()))` — property được JPA validate với metamodel entity. Lỗi gõ trong ánh xạ enum fail nhanh lúc truy vấn thay vì tới database.
- **ORDER BY mặc định phản chiếu listing legacy mỗi khi khóa sort legacy ánh xạ được.** Khi `sortField` / `sortDirection` vắng hoặc lạ, service trả về dòng theo thứ tự mặc định legacy. Với Account Inquiry mặc định legacy là `prn_seq:asc` (legacy [`SortHelper.java#86`](../WECMS%20DEV/WECMS/src/wabooks/helper/SortHelper.java)) — `DEFAULT_SORT = Sort.by(asc("prnSeq"), asc("bankCd"), asc("acctNo"))` khớp nó, với `bankCd, acctNo` làm tiebreaker xác định (và một fallback cho dòng `prnSeq` NULL — seed V22 có trước cột schema V23, nên dòng chưa-gán tự nhiên sort cuối dưới mặc định ASC NULLS-LAST của PostgreSQL). Khi khóa sort legacy **không** ánh xạ được an toàn (trường entity thiếu khỏi tập con Sprint, nguy cơ overflow NUMERIC, khoảng trống phủ-giá-trị làm đổi thứ tự quan sát được), giữ mặc định hiện có nhưng ghi hoãn inline bằng một `// TODO:` tham chiếu cột legacy và ràng buộc chặn — không bao giờ âm thầm khác biệt.
- **Luôn nối thứ tự mặc định làm khóa phụ ổn định** khi user yêu cầu một sort chính: `Sort.by(primary).and(DEFAULT_SORT)`. Giá trị bằng nhau trên cột user-chọn khi đó giữ thứ tự xác định qua các request, quan trọng cho phân trang và snapshot test.
- **FIELD_ID special KHÔNG BAO GIỜ trong whitelist.** `CHECKBOX` và `ETC` là dòng do admin kiểm soát đi qua View Setting để xếp thứ tự cột — chúng không sort được ở cả hai phía. Frontend lọc qua `isSpecialViewSettingField(...)`; backend đơn giản không đăng ký chúng trong enum, nên một request client độc hại / cũ phân giải về ORDER BY mặc định.
- **Seed `SORT_YN` và enum whitelist phải đồng thuận.** Mọi cột có `SORT_YN='Y'` trong `SYS_SCR_OPTION_WECMS` PHẢI có một constant trong enum whitelist khớp (`AccountSortField` v.v.). Khi một cột chưa có trường entity hỗ trợ, seed nó với `SORT_YN='N'` — không bao giờ dựa vào fallback `parse()`-trả-null âm thầm để che các glyph sort UI-thấy-được-nhưng-vô-tác-dụng. Fallback phòng vệ tồn tại cho client cũ, không cho cột cấu hình sai vĩnh viễn. Tham chiếu: V41 chuyển `VAS_ACCOUNT.SORT_YN` thành `'N'` chính vì cột nằm trong registry View Setting nhưng (chưa) trong `AccountSortField` — khi dữ liệu nguồn xuất hiện, cùng migration thêm constant enum chuyển `SORT_YN` về `'Y'`.
- **Chữ ký service public mang sort tùy chọn.** Thêm một overload nhận `(String sortField, String sortDirection)` (hoặc refactor call site hiện có) để controller chuyển tiếp giá trị wire nguyên văn. Không bao giờ lộ enum qua ranh giới controller — parse xảy ra trong service.
- **Controller giữ mỏng.** `@RequestParam(value = "sortField", required = false)` + `@RequestParam(value = "sortDirection", required = false)` — chuyển chuỗi thô qua. Không validation regex, không `@Pattern` trên hai param này (bước enum-`parse(...)` trong service là validator).
- **Áp dụng một endpoint mới** thuần bổ sung: viết một enum whitelist mới, thêm hai query param vào controller, dựng `Sort` trong service, và gọi biến thể repository nhận-`Sort` (`findBy...(args, Sort sort)`). Không thay đổi `common/web/`, không controller / DTO / migration mới phía View Setting.

**Triển khai tham chiếu:**

- Common: [`common/web/SortDirection.java`](src/main/java/com/kosign/wecms/common/web/SortDirection.java), [`common/web/SortableField.java`](src/main/java/com/kosign/wecms/common/web/SortableField.java).
- Domain Banking: [`AccountSortField`](src/main/java/com/kosign/wecms/domain/banking/service/AccountSortField.java) — whitelist cho Account Inquiry VND/FCY (`BANK` → `bankCd`, `ACCOUNT_NICKNAME` → `acctNickNm`, `BALANCE` → `bal`, `AVAILABLE_BALANCE` → `realAmt`, `LAST_UPDATE` → `acctBalUpdDtm`).
- Áp dụng service: [`AccountService.list(currency, sortField, sortDirection)`](src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java) — parse cả hai giá trị wire, dựng `Sort`, fallback về `DEFAULT_SORT = Sort.by(asc("prnSeq"), asc("bankCd"), asc("acctNo"))` khi input null (parity legacy `fnsh_0001_001` / `SortHelper#86`; `prnSeq` là cột print-order V23 nullable `NUMERIC(2,0)` trên `AcctMstr`).
- Controller: [`AccountController#listAccounts`](src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java) — ba query param (`currency`, `sortField`, `sortDirection`).
- Repository: [`AcctMstrRepository#findByBizKeyAndActvYn(...)`](src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java) và hai biến thể đã-lọc — mỗi cái nhận một param `Sort` cạnh các finder method `OrderBy*` legacy (giữ cho `AccountService#ownAccounts()`).

**Pattern bị cấm (bắt tại review):**

- `@Query(value = "... ORDER BY " + sortField, nativeQuery = true)` — nối input user vào SQL.
- `Sort.by(sortField)` (truyền chuỗi wire trực tiếp vào `Sort.by`) — bỏ qua whitelist; dù Spring Data validate với metamodel, `sortField` cũng là FIELD_ID wire, không phải tên property entity.
- Throw `IllegalArgumentException` / `BusinessException` trên một `sortField` lạ — degrade duyên dáng về ORDER BY mặc định, không bao giờ lộ 4xx cho một bundle frontend cũ.
- Thêm `sortField` / `sortDirection` vào path: `/accounts/{sortField}` — chúng là query param, không bao giờ là đoạn path.
- Fork `SortDirection` theo từng domain (`AccountSortDirection`, `PaymentSortDirection`, …) — enum là toàn cục và nằm trong `common/web/`.

**Các endpoint đã đấu nối common-sort:**

| Endpoint | Enum whitelist | FIELD_ID sort được | ORDER BY mặc định |
|---|---|---|---|
| `GET /api/v1/banking/accounts` | `AccountSortField` | `BANK`, `ACCOUNT_NICKNAME`, `BALANCE`, `AVAILABLE_BALANCE`, `LAST_UPDATE` | `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` (parity legacy `fnsh_0001_001`; dòng `PRN_SEQ` NULL sort cuối qua mặc định ASC NULLS-LAST của PostgreSQL) |

Các endpoint list khác áp dụng contract trong lượt ổn định hóa kế tiếp của chúng — một enum theo-từng-domain + param controller + dựng `Sort` service mỗi migration. Các primitive chung trong `common/web/` không đổi.

### Hàm DB báo cáo legacy — lập trường Sprint 1

Audit 2026-04-29 nhận diện ba helper PL/pgSQL cũ trong `wecmsvndev` hỗ trợ các báo cáo daily-financial / daily-cash / cash-flow:

| Function | Loại | Dùng bởi legacy | Lập trường Sprint 1 |
|---|---|---|---|
| `wb_insert_daily_fincl_rept_daily_sumr_detl_data(p_biz_key, p_req_date, p_req_date2)` | `void` (ghi) | batch BCS — làm mới bảng tóm tắt Daily Financial CMC | **KHÔNG gọi từ Spring.** Sprint 1 đọc dòng đã-tổng-hợp-trước từ `BUSI_COND_DAILY_SUMR(_DETL)` (hoặc seed stub). Port batch xuất hiện ở Sprint 2/3. |
| `wb_insert_daily_fincl_rept_daily_cash_sumr_data(p_biz_key, p_req_date, p_req_date2)` | `void` (ghi) | batch BCS — làm mới bảng tóm tắt Daily Cash | **KHÔNG gọi từ Spring.** Cùng lý do. |
| `fn_get_haengsung_daily_financial_report(p_biz_key, p_as_of_date, p_account_filter)` | `TABLE(...)` (hàm trả-tập chỉ-đọc) | truy vấn sổ HAENGSUNG `rept_0017_001` | **Stub Sprint 1.** Hàm là nguồn chuẩn hợp pháp cho sổ HS và sẽ là ngoại lệ đúng với quy tắc "không phụ thuộc DB-function mới". Sprint 1 ship một stub xác định backed-by-seed; Sprint 2 có thể hoặc port hàm vào Flyway local (với call site stub đổi sang một method phễu duy nhất, không bao giờ qua `@Query` rải rác khắp repo) hoặc nhân bản truy vấn trong Java. Quyết định trước khi tính năng sổ Sprint 2 xuất hiện. |

Theo chính sách trên, không hàm nào trong số này được gọi từ controller, service, hay repository Sprint 1. Nếu Sprint 2 quyết định giữ `fn_get_haengsung_daily_financial_report` trong-DB, ghi ngoại lệ trong bảng **Surviving legacy functions** ở trên trước khi call site xuất hiện.

### Excel export — pipeline dùng chung `common/export/` + `ExportRowProvider` theo-từng-màn-hình

Export Excel / XLSX là một **tính năng WeCMS global / common** khóa theo cùng registry `WEB_SVC_ID` View Setting vốn điều khiển hiển thị / thứ tự cột và sort bảng. Mọi trang list lộ icon Excel trên toolbar (Banking, Payment, Add-on, Settings, mọi module tương lai) định tuyến qua một service dùng chung, một writer dùng chung, một registry dùng chung, và một endpoint controller mỏng theo-từng-resource. Trang đấu nối đầu tiên là **Banking > Account Inquiry (VND)** tại `POST /api/v1/banking/accounts/export`; các trang phân trang khác áp dụng như một phần lượt ổn định hóa kế tiếp.

**Primitive dùng chung — [`common/export/`](src/main/java/com/kosign/wecms/common/export/):**

- [`ExcelExportRequest`](src/main/java/com/kosign/wecms/common/export/ExcelExportRequest.java) — body wire dùng chung bởi mọi endpoint theo-từng-resource. Mang `screenId` + `dymAreaId` + `fileName` + `filters` + `sort` + `columns` + `subHeader` fallback tùy chọn. Validate qua `@Pattern` trên id screen / area; input xấu → envelope `WBE_VALIDATION`.
- [`ExcelColumnSpec`](src/main/java/com/kosign/wecms/common/export/ExcelColumnSpec.java) — một entry cột; `fieldId` + `header` đã-dịch + `format` + `ccyCd` + `align`. Frontend ship chúng theo thứ tự user thấy trên màn hình; backend giao lại với hiển thị View Setting đã persist trước khi phát.
- [`ExcelColumnFormat`](src/main/java/com/kosign/wecms/common/export/ExcelColumnFormat.java) — `TEXT | AMOUNT | NUMBER | DATE | DATETIME`. Backend sở hữu render cell thực tế — một contract cho định dạng amount / date trên mọi consumer.
- [`ExcelAlignment`](src/main/java/com/kosign/wecms/common/export/ExcelAlignment.java) — `LEFT | RIGHT | CENTER` với `AMOUNT` / `NUMBER` mặc định `RIGHT` khi không chỉ định.
- [`ExcelSortSpec`](src/main/java/com/kosign/wecms/common/export/ExcelSortSpec.java) — cùng contract wire như common-sort của endpoint list (`field` = `FIELD_ID` View Setting, `direction` = `ASC` / `DESC`).
- [`ExcelWorkbookWriter`](src/main/java/com/kosign/wecms/common/export/ExcelWorkbookWriter.java) — writer Apache POI **SXSSF** (streaming). `CellStyle` gộp pool (tổng 9 style — text / number / amount × LEFT / RIGHT / CENTER + title + subheader + header) để export 50k dòng giữ heap có giới hạn. Dòng title → dòng subheader tùy chọn → dòng trống → dòng header → dòng dữ liệu. Lỗi ép định dạng degrade về text — không bao giờ throw.
- [`ExportableScreenRegistry`](src/main/java/com/kosign/wecms/common/export/ExportableScreenRegistry.java) — Spring auto-thu mọi `@Component implements ExportRowProvider`. Từ chối screenId trùng lúc startup. `WBE2010` cho `screenId` lạ.
- [`ExportRowProvider`](src/main/java/com/kosign/wecms/common/export/ExportRowProvider.java) — interface strategy theo-từng-màn-hình. Trả về `ExportFetch` (`rows` khóa theo FIELD_ID + `subHeader` backend-tính).
- [`ExcelExportService`](src/main/java/com/kosign/wecms/common/export/ExcelExportService.java) — orchestrator: phân giải provider → giao cột với allow-list VÀ hiển thị View Setting đã persist (phòng-thủ-theo-chiều-sâu) → `provider.fetch(...)` → giới hạn dòng ở `MAX_ROWS = 50_000` → stream XLSX qua writer với filename `Content-Disposition` UTF-8 RFC 5987.

**Pattern endpoint theo-từng-resource (CLAUDE.md quyết định 3 — không bao giờ dispatcher chung):**

```
POST /api/v1/{module}/{resource}/export
Content-Type: application/json
Body: ExcelExportRequest
→ HTTP 200, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
→ Content-Disposition: attachment; filename*=UTF-8''<percent-encoded>.xlsx
```

Controller cố ý mỏng — một `@PostMapping` nhận `@Valid @RequestBody ExcelExportRequest` + `HttpServletResponse`, chuyển tiếp tới `excelExportService.exportTo(response, request)`, trả về `void`. Auth là cùng đường JWT như endpoint list (mọi thứ ngoài `/api/v1/auth/*` là `authenticated()` theo [`SecurityConfig`](src/main/java/com/kosign/wecms/config/SecurityConfig.java)).

**Quy tắc (không thương lượng):**

- **Không bao giờ viết sinh Excel mỗi trang.** Mọi màn hình định tuyến qua `ExcelExportService`. Cách sửa cho một cột / filter / format mới là mở rộng module dùng chung trước, rồi tiêu thụ nó — fork không được phép.
- **Backend là nguồn dữ liệu.** Endpoint export chạy lại cùng đường filter+sort mà endpoint list dùng (provider của Account Inquiry gọi `AccountService.list(currency, sortField, sortDirection)`). Frontend không bao giờ ship dòng.
- **Backend là nguồn chuẩn về hiển thị.** `columns[]` của frontend là một gợi ý; service giao nó với: (a) allow-list `allowedFieldIds()` của provider và (b) View Setting đã persist (`USE_YN='Y'` từ `ScreenOptionService.load(...)`). FIELD_ID special (`CHECKBOX`, `ETC`) bị `ScreenOptionService.isSpecialFieldId(...)` bỏ — phòng-thủ-theo-chiều-sâu cùng với filter của riêng frontend.
- **Thứ tự cột đến từ request** — tôn trọng reorder popover chưa-lưu (CLAUDE.md quyết định 4) — nhưng chỉ SAU khi giao hiển thị. Cột ẩn không bao giờ lọt kể cả khi frontend yêu cầu.
- **Mọi dòng đã-filter+sort, không bao giờ phân trang.** Giới hạn bởi `MAX_ROWS = 50_000` (CLAUDE.md quyết định 2). Vượt → `WBE2011`.
- **Nhãn i18n nằm ở frontend.** `header` wire là text đã-dịch; backend không bao giờ phân giải lại chuỗi locale-aware cho cột export.
- **Subheader do backend tính** (CLAUDE.md quyết định 5). Provider trả về chuỗi count + tổng chuẩn trong `ExportFetch.subHeader`; `subHeader` của request chỉ là fallback thông tin.
- **Giá trị amount trong workbook PHẢI đi qua formatter hiển thị dùng chung** — không bao giờ chuỗi wire thô và không bao giờ một template số `String.format` tùy tiện. Cụ thể:
  - **Dòng subheader / summary** (ví dụ `"20 account(s) · Balance: 19.886.030.287₫ · Available Balance: 8.417.734.439₫"`) — provider soạn qua [`AmountFormatter.forDisplay(value, ccyCd)`](src/main/java/com/kosign/wecms/domain/banking/service/AmountFormatter.java), phản chiếu frontend [`lib/currency.ts formatAmount`](../wecms-web-main/lib/currency.ts) 1:1 (locale vi-VN + hậu tố `₫` cho VND, en-US + tiền tố cho USD/EUR/KRW/JPY, vi-VN + không ký hiệu cho mọi thứ khác). Thẻ summary trên màn hình, popup in, và subheader Excel render cùng con số y hệt.
  - **Cell dữ liệu số** (Balance, Available Balance, v.v.) — `format: "amount"` trên `ExcelColumnSpec` wire kích hoạt định dạng dữ liệu `#,##0.##` của POI bên trong cell, áp dụng tự động bởi [`ExcelWorkbookWriter`](src/main/java/com/kosign/wecms/common/export/ExcelWorkbookWriter.java). Provider trao cho writer chuỗi `BigDecimal` / wire-decimal thô — không bao giờ một chuỗi đã-định-dạng-sẵn với phân-tách-hàng-nghìn hay ký hiệu nhúng — để Excel tự re-sum cột native.
  - **Cell title** giữ free-form (không cần định dạng amount; phản chiếu title trang trên màn hình).
  - **Trường amount free-text nhúng trong bất kỳ cell nào khác** — cùng quy tắc: đi qua `AmountFormatter.forDisplay` để locale + ký hiệu nhất quán trên mọi consumer export. Không `String.format("%,d ₫", n)`, không `BigDecimal.toString()`, không formatter mỗi-trang.
- **Title trong-sheet vs. filename là hai chuỗi riêng biệt.** Trường `title` wire mang title trang trên màn hình (với khoảng cách tự nhiên — `계좌 조회 (VND)` / `Account Inquiry (VND)`); trường `fileName` wire mang dạng thân-thiện-filesystem cũ (không khoảng trắng trong + hậu tố `_DDMMYYYY` — `계좌조회(VND)_02052026`). [`ExcelExportService`](src/main/java/com/kosign/wecms/common/export/ExcelExportService.java) ưu tiên `title` cho cell gộp dòng 0; caller cũ bỏ `title` fallback về `fileName` (parity legacy). Trang frontend lấy hai cái từ hai khóa i18n riêng (`<screen>.title` cho header trên màn hình / title trong-sheet, `<screen>.export_title` cho base filename không-khoảng-trắng).
- **Tên file do caller cung cấp** (ví dụ `"Banking Account Inquiry (VND)"`); controller nối `.xlsx`. Không timestamp mặc định — khớp legacy. RFC 5987 `filename*=UTF-8''…` để non-ASCII (Hàn / Khmer) round-trip.
- **Hình dạng filter là theo-từng-màn-hình.** `filters: Map<String, Object>` mờ với service; provider quyết định khóa nào nó hiểu. Provider Account Inquiry tiêu thụ `currency` (phía-DB) cộng `bankName` / `keyword` (sau-fetch in-memory, phản chiếu filter phía-client của trang). Khi endpoint list mọc filter DB thật cho các khóa đó, bỏ lượt in-memory — chữ ký controller không đổi.
- **Whitelist sort vẫn bắt buộc.** Provider chuyển tiếp `sort.field` / `sort.direction` thẳng tới service list vốn validate với enum theo-từng-domain (ví dụ `AccountSortField`). FIELD_ID lạ fallback về ORDER BY mặc định — không bao giờ vọng vào một chuỗi SQL.
- **Áp dụng một endpoint mới** thuần bổ sung: viết một `ExportRowProvider` mới (một class, tự đăng ký qua `@Component`), thêm `@PostMapping("/{resource}/export")` vào controller resource hiện có, đấu nối map dòng → FIELD_ID. Không thay đổi `common/export/`, không DTO mới, không hình dạng request mới.
- **Phạm vi tenant** — filter `BIZ_KEY` là bắt buộc; provider kế thừa tenant-scoping của service list (`TenantContext.currentBizKey()`). Export KHÔNG BAO GIỜ được cross tenant.
- **Không `@Transactional` trên orchestrator.** `ExcelExportService.exportTo` cố ý chạy không có transaction bên ngoài để ghi streaming không giữ một DB transaction mở. Mỗi cuộc gọi bên trong (`ScreenOptionService.load`, `AccountService.list`) tự mở phạm vi của nó.

**Pattern bị cấm (bắt tại review):**

- Ghi vào một `Workbook` ngoài `ExcelWorkbookWriter` — có một writer; trang không chọc POI trực tiếp.
- Endpoint dispatcher chung như `POST /api/v1/exports/{screenId}` — mọi endpoint export là theo-từng-resource (CLAUDE.md quyết định 3).
- Thêm một thư viện XLSX mới (EasyExcel, jxls) cạnh POI — POI 5.x là nơi sản xuất duy nhất.
- Một dependency `xlsx` / `exceljs` ở frontend — backend sản xuất file; frontend post JSON và lưu blob.
- Mảng dòng do frontend ship cho export — trang PHẢI gửi ngữ cảnh filter / sort / column-spec, không bao giờ dòng đã-render.
- Nối `screenId` / `fieldId` / `sortField` vào một chuỗi SQL — mọi định danh wire đi qua các validator có kiểu ở trên.
- Giá trị amount render với `String.format("%,d", n)` / `String.format("%,.2f ₫", n)` / `BigDecimal.toString()` / `value + " ₫"` — mọi chuỗi tiền tệ trong workbook (subheader, cell free-text) PHẢI đi qua `AmountFormatter.forDisplay(...)` để locale + ký hiệu nhất quán.
- Soạn title trong-sheet từ `request.fileName()` khi `request.title()` được set — writer luôn phát `title` trước; đừng gộp hai trường về một.

**Triển khai tham chiếu:**

- Controller: [`AccountController#exportAccounts`](src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java).
- Provider: [`AccountInquiryExportProvider`](src/main/java/com/kosign/wecms/domain/banking/export/AccountInquiryExportProvider.java) — đăng ký `screenId = "banking-account-inquiry-vnd"`, allow-list `{BANK, ACCOUNT_NICKNAME, BALANCE, AVAILABLE_BALANCE, VAS_ACCOUNT, LAST_UPDATE}`, tái dùng `AccountService.list`, tính subheader `<count> account(s) · Balance: … · Available Balance: …` phía-server.

**Các endpoint đã đấu nối Excel export:**

| Endpoint | Provider | FIELD_ID cho phép | Nguồn subheader |
|---|---|---|---|
| `POST /api/v1/banking/accounts/export` | `AccountInquiryExportProvider` (`screenId = banking-account-inquiry-vnd`) | `BANK`, `ACCOUNT_NICKNAME`, `BALANCE`, `AVAILABLE_BALANCE`, `VAS_ACCOUNT`, `LAST_UPDATE` | Backend (count + tổng balance + tổng available, tính từ `AccountListResponse`) |

Các trang list khác áp dụng contract từng-cái-một trong lượt ổn định hóa kế tiếp — một class `ExportRowProvider` + một `@PostMapping` mới mỗi migration. Các primitive chung trong `common/export/` không đổi.

## PROD DB Table Scope cho Refactoring

Mục này thiết lập **phạm vi bảng có thẩm quyền** để refactor. WeCMS ban đầu được clone từ WABOOKS (mã nguồn + database), nên DB PROD đang chạy vẫn chứa nhiều bảng KHÔNG thực sự được WeCMS dùng — bảng WABOOKS cũ, snapshot backup, artifact migration, và nội bộ framework JEX. Code JEX cũ thường xuyên tham chiếu các bảng mà PROD không thực sự populate.

Phạm vi bên dưới được suy ra bằng cách chạy truy vấn này **trực tiếp trên PROD** và lọc thủ công kết quả:

```sql
SELECT
    schemaname,
    relname AS table_name,
    n_live_tup AS row_count
FROM pg_stat_user_tables
WHERE n_live_tup > 0
ORDER BY n_live_tup DESC;
```

### Nguồn chuẩn

1. Danh sách bảng PROD bên dưới là nguồn chuẩn **DUY NHẤT** cho cái gì để refactor.
2. Chỉ gồm các bảng thật mà PROD populate với dữ liệu nghiệp vụ sống.
3. **KHÔNG dựa vào DB DEV** cho quyết định phạm vi — DEV thưa thớt và gồm artifact test không phản ánh sử dụng production. DEV vẫn là nguồn chuẩn cho *hình dạng schema* (parity theo từng cột theo quy tắc trên), nhưng **KHÔNG** cho bảng nào tồn tại trong phạm vi.
4. Luôn lọc bỏ bảng không-nghiệp-vụ bằng quy tắc loại trừ bên dưới.

### Quy tắc loại trừ (nghiêm ngặt)

Những thứ sau bị loại khỏi danh sách active, bất kể code JEX cũ tham chiếu gì:

1. **Bảng framework JEX** — tiền tố `jex_*`
2. **Bảng backup** — pattern `%_backup_YYYYMMDD`
3. **Bảng sinh / copy** — pattern `%_i[0-9]+`
4. **Mọi artifact trùng lặp, test, hoặc migration** không đại diện dữ liệu nghiệp vụ sống

### Danh sách bảng active (PROD-xác-nhận, CUỐI CÙNG)

CHỈ dùng các bảng này để refactor. Nếu một bảng không có trong danh sách này, đừng tạo entity, repository, hay Flyway migration cho nó — kể cả khi code JEX cũ tham chiếu nó.

```
ACCT_AUTH_INFO
ACCT_MSTR
ACCT_TRNS_HSTR
ACCT_TRNS_RCV_PAY_INFM
API_TRAN_LOG_HIS
APPR_LINE_INFM
APPR_LINE_INFM_DETL
AUDIT_LOGS_INFM
BANK_CERT_INFO
BANK_EXCH_RATE_INFM
BANK_INFM
BANK_INFM_DETL
BASE_INFM
BLOCKED_CHARS_RULE
BLTB_COMNT_INFM
BLTB_NOTI_CONT_INFM
BLTB_NOTI_INFM
BLTB_POST_INFM
BLTB_POST_INFM_MDPH
BLTB_USER_NEW_POST_INFM
BUSA_DOC_DETL
BUSA_DOC_DETL_HIS
BUSA_DOC_MSTR
BUSA_DOC_MSTR_HIS
BUSA_DOC_RCV_PAY_HIS
BUSI_ADMN_ACTV_HIS
BUSI_ADMN_INFM
BUSI_ADMN_LOGIN_HIS
BUSI_ADMN_MDTR
BUSI_ENV_SET_INFM
BUSI_ENV_SET_MDTR
BUSI_INFM
BUSI_MATCH_LOG
BUSI_MDPH
BUSI_USAG_SET_INFM
BUSI_USAG_SET_INFM_LANG_DETL
BUSI_VAS_ACCT_INFM
CARD_MSTR
CARD_TRNS_HSTR
CARD_TRNS_RCV_PAY_INFM
CCY_INFM
CHART_ACCT_INFM
CHNL_INFM
CITAD_BANK_CD_LOOKUP
DAILY_CASH_REPT_DAILY_SUMR_DETL
DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR
DAILY_FINCL_REPT_DAILY_SUMR_DETL
EINV_FEE_DETL
EINV_FEE_DETL_HIS
EINV_TAX_DETL
EINV_TAX_DETL_HIS
EMPL_INFM
EMPL_MDPH
ERP_TRNS_HSTR
EXPT_RESOL_APPR_LINE
EXPT_RESOL_APPRVR_INFM
EXPT_RESOL_DETL
EXPT_RESOL_FILE
EXPT_RESOL_MSTR
EXPT_RESOL_PAY_EVID_FILE
EXT_TRAN_CALL_HIS
GDT_CERT_INFO
MENU_AUTH_ADMN_SYS
MSG_SND_HIS
NAPAS_BANK_INFO
NAPAS_BANK_REMIT_INFO
NAPAS_REMIT_TRNS_HSTR
NATN_INFM
NON_COLL_COND_DAILY_SUMR
NON_PAY_COND_DAILY_SUMR
NXSIAS_MODULE_TRAN_LOG_HIS
OPRT_STTS_INFO
OVRS_PYMN_MSTR
OVRS_PYMN_MSTR_HIS
OVRS_PYMN_SUMR
OVRS_PYMN_SUMR_DETL
OVRS_PYMN_SUMR_DETL_HIS
OVRS_PYMN_SUMR_HIS
PGRO_BSAC_LNKN_HIS
PGRO_BSAC_LNKN_INFM
PUSH_DEVICE_MSTR
PUSH_DEVICE_MSTR_HIS
RMRK_ORG_INFM
SCDUL_PYMN_SUMR
SCDUL_PYMN_SUMR_DETL
SCDUL_PYMN_SUMR_DETL_HIS
SCDUL_PYMN_SUMR_HIS
SYS_DOC_ID_GEN
SYS_DSHBRD_INFM
SYS_ENV_SET_INFM
SYS_SCR_OPTION_NEW
SYS_SCR_OPTION_WECMS
SYS_USAG_SET_INFM
TB_BATCH_SCHEDULE_DTL
TB_BATCH_SCHEDULE_INFO
TB_FIRM_BANK_CODE
TB_FIRM_BANK_CODE_CMS
TB_FIRM_BAT_API_TRAN
TB_FIRM_CONF
TB_FIRM_FILE_DATA
TB_FIRM_FILE_MST
TB_FIRM_FILE_RSLT
TB_FIRM_TRAN
TB_PAY_FILE_DATA
TB_PAY_FILE_DATA_HIS
TB_PAY_FILE_MST
TB_PAY_FILE_MST_HIS
TB_PAY_TARGET
TB_PAY_TARGET_HIS
TB_VAN_API_INFO
TRCO_ACCT_INFO
TRCO_ACTV_HSTR
TRCO_ADDR_INFM
TRCO_BUSI_INFM_SUMR
TRCO_INFO_FILE
TRCO_GRP_BSC_CSTM
TRCO_GRP_BSC_SYS
TRCO_INFM
TRCO_MDPH
TRCO_OVRS_INFM
USAG_INFM
USAG_INFM_LANG_DETL
USER_BLTB_CONN_INFM
USER_BUSI_CONN_INFM
USER_BUSI_CONN_INFM_HIS
USER_DSHBRD_INFM
USER_INFM
USER_LOGIN_HIS
USER_MDPH
USER_MENU_AUTH_INFM
USER_PW_CHG_HIS
USER_SCR_OPTION_NEW
USER_SCR_OPTION_WECMS
USER_TRCO_USE_HIS
USER_USAG_USE_HIS
```

### Quy tắc di trú dữ liệu (BẮT BUỘC)

Quy tắc này chi phối cách dữ liệu được copy từ DEV → LOCAL cho tập bảng active định nghĩa ở trên. Nó **không thương lượng** và áp dụng cho mọi Flyway seed migration trong `src/main/resources/db/migration/`.

- **Chỉ di trú dữ liệu cho** `biz_key = 'VN_VNM0004_000001'`.
- **Giới hạn khối lượng dữ liệu** → **Tối đa 30 dòng mỗi bảng active đã refactor.** Khi một bảng có hơn 30 dòng trên DEV, lấy các dòng liên quan / mới nhất (thường theo `REG_DTM DESC` hoặc thứ tự tự nhiên của bảng); khi có ít hơn, lấy tất cả.
- **KHÔNG di trú:**
  - Toàn bộ dữ liệu bảng
  - Dữ liệu `biz_key` khác (bất kỳ giá trị `BIZ_KEY` nào khác `VN_VNM0004_000001`)
  - Bảng `jex_*`
  - Bảng `*_backup_*`
  - Bảng `*_iXXXXX`
  - Bảng không có trong **PROD Active Table List** ở trên
- **Giữ toàn vẹn:**
  - Chèn bảng cha trước
  - Tôn trọng quan hệ foreign-key
  - Tránh dữ liệu mồ côi (không dòng con nào có khóa cha vắng mặt local)
- **Mục đích:**
  - Giữ DB local nhẹ
  - Đảm bảo bộ dữ liệu nhất quán trên mọi máy lập trình viên
  - Tránh di trú dữ liệu không cần thiết
  - Tập trung vào phạm vi tính năng đã refactor

Migration seed PHẢI idempotent (`INSERT … ON CONFLICT DO NOTHING`) và PHẢI sắp xếp để cha xuất hiện trước con trong chuỗi FK (ví dụ `BUSI_INFM` → `USER_INFM` → `USER_BUSI_CONN_INFM` → `USER_LOGIN_HIS`).

### Quy tắc bắt buộc refactoring (rất quan trọng)

**Refactoring Enforcement Rule:** nếu JEX cũ dùng nhiều bảng → **CHỈ refactor các bảng tồn tại trong PROD Active Table List**. Không ngoại lệ.

Khi port một endpoint JEX cũ:

- Đọc `_act.jsp`, IDO XML, WSVC XML, DAO, và entity cũ để hiểu hành vi.
- Đối chiếu **mọi** bảng được tham chiếu với danh sách active ở trên.
- Nếu legacy dùng 5 bảng nhưng chỉ 3 trong đó xuất hiện trong danh sách active → **CHỈ refactor 3 bảng đó**.

**KHÔNG:**
- Mù quáng theo cấu trúc legacy khi nó đụng bảng ngoài-phạm-vi.
- Tạo JPA entity, repository, hay Flyway migration cho bảng không có trong danh sách active.
- Di trú bảng WABOOKS không-dùng chỉ vì code legacy nhắc tới.

Nếu một đường code legacy thật sự bất khả thi nếu thiếu một bảng bị loại (ví dụ một join target mà PROD thực sự populate qua một đường audit bỏ sót), nêu khác biệt với lead **trước khi** mở rộng danh sách này — đừng âm thầm mở rộng phạm vi.

Quy tắc này nằm **trên** "Legacy Schema Parity Rule" (phản chiếu DEV theo từng cột): parity chi phối *cách* một bảng được migrate; quy tắc này chi phối *liệu* một bảng có được migrate hay không.

### Truy vấn audit PROD khuyến nghị

Để suy lại danh sách này (hoặc làm mới khi phạm vi được rà soát), chạy trên PROD:

```sql
SELECT
    schemaname,
    relname AS table_name,
    n_live_tup AS row_count
FROM pg_stat_user_tables
WHERE n_live_tup > 0
ORDER BY n_live_tup DESC;
```

Rồi lọc thủ công bỏ:
- `jex_*`
- `%_backup_YYYYMMDD`
- `%_i[0-9]+`
- artifact trùng lặp / test / migration

Cập nhật mục này (và chỉ mục này) khi danh sách đã lọc thay đổi. Không bao giờ làm mới từ DEV.

**Scope override 2026-05-13 — thêm `TRCO_INFO_FILE`.** Popup Add / Edit contacts đính kèm file (legacy `comm_0184_002_c001` + `comm_0184_005_*`). Audit trước loại trừ bảng vì sử dụng PROD chưa kiểm chứng; quyết định PO 2026-05-13 xác nhận refactor phải hỗ trợ đính kèm đầu-cuối. Quy tắc danh-sách-active "nêu khác biệt trước khi mở rộng danh sách này" được tôn trọng — lead cho phép mở rộng rõ ràng, và V94 + V95 ship schema + key generator. Slug thư mục refactor là `contacts` (không phải id WSVC `trco_0004_001_u002` cũ) theo quy tắc đặt-tên-định-danh.

## Application Profiles

| File | Mục đích |
|---|---|
| `application.yml` | Base — đấu nối JPA/Flyway, basename messages, server port 8080, `wecms.jwt.*`, `wecms.cors.*`, `wecms.tenant.biz-key=VN_VNM0004_000001`. Profile active mặc định `local`. |
| `application-local.yml` | Laptop lập trình viên — mặc định URL DB + mật khẩu, mặc định JWT dev secret, log chi tiết. |
| `application-dev.yml` | Dành chỗ. Không dùng trong Sprint 0. |
| `application-prod.yml` | Dành chỗ. Không dùng trong Sprint 0. |
| `application-test.yml` | Profile integration-test. |

## Build & Run

```bash
./gradlew build                                              # compile + unit tests
./gradlew bootRun --args='--spring.profiles.active=local'    # run against localhost:5434
./gradlew test                                               # tests only

./run-local.bat    # Windows wrapper — sets env vars + bootRun
./run-local.sh     # POSIX equivalent
```

Ở lần startup đầu tiên dưới profile `local`, `DataSeeder` thay bất kỳ sentinel `USER_INFM.USER_PW = '__BCRYPT_PENDING__'` nào bằng một hash BCrypt mới của `Password!1`. Idempotent — chạy lại không làm gì.

User test sau khi seed:
- `testuser` / `Password!1` — active, không khóa
- `lockeduser` / `Password!1` — `LOCK_YN=Y` → trả về `WBE1011`
- `legacyuser` / `Password!1` — lưu dạng hash SHA-256 base64 cũ (V10); đăng nhập thành công nâng cấp lên BCrypt

## Do / Don't

**Do**
- Giữ tên trường Java DTO ở camelCase; strategy SNAKE_CASE Jackson toàn cục chuyển sang lowercase snake_case trên wire ([JacksonConfig](src/main/java/com/kosign/wecms/config/JacksonConfig.java)). Chỉ thêm `@JsonProperty` khi tên wire khác với Java snake-case hóa (ví dụ `accessToken` → `access_token`).
- Throw `BusinessException("WBExxxx")` với một entry `messages.properties` khớp cho mọi đường lỗi đã biết.
- Thêm một Flyway migration cho mọi thay đổi schema; đảm bảo JPA entity khớp (`ddl-auto: validate` sẽ fail startup nếu không).
- Ràng buộc mọi truy vấn tenant-scoped trên `BIZ_KEY = <tenantProperties.bizKey>` — không bao giờ một chuỗi literal, không bao giờ một wildcard.
- Đưa các thay đổi hình dạng Next.js-thấy-được vào DTO/type khớp ở cả hai phía trong cùng MR (quy tắc full-stack ownership, CLAUDE.md gốc).
- Dùng `TenantContext` để đọc danh tính user hiện tại bên trong method service — đừng parse lại JWT hay truy vấn lại user mỗi cuộc gọi.
- Dùng enum `LangCd` cho mọi việc parse Accept-Language — đừng triển khai lại so khớp chuỗi.

**Don't**
- Đừng hardcode mật khẩu, JWT secret, hay credential DB. Mọi thứ nhạy cảm đọc từ env / `application-local.yml`.
- Đừng tự chế một hình dạng response thay thế — luôn đi qua `ApiResponse<T>` + `CommonHead`.
- Đừng thêm logic nghiệp vụ trong entity, controller, hay filter. Nó thuộc về `service/`.
- Đừng dùng `ddl-auto: update` hay `create`. Flyway là kênh schema được phép duy nhất.
- Đừng mở rộng filter tenant thành `LIKE 'VN_VNM0004_%'` trong code — regex property từ chối wildcard có lý do.
- Đừng thêm SpringDoc / Testcontainers / Redis / bất kỳ dependency nào khác "phòng hờ". Thêm khi tính năng đầu tiên cần.
- Đừng vượt ranh giới ghi: các cây JEX là tham chiếu chỉ-đọc.

## Vấn đề đã biết / Bất nhất quán

| Vấn đề | Vị trí | Trạng thái |
|---|---|---|
| Không có bộ đếm auto-lock | `AuthService`, `USER_LOGIN_HIS` | Scaffold (dòng RSLT_CD=FAIL) có; logic 5-thất-bại bị hoãn |
| Logout chỉ-client | `AuthController.logout()` | Endpoint stub; không vô hiệu token phía server |
| PW_CHG_REQ_SEQ_NO không dùng | `UserInfm.pwChgReqSeqNo` | Đã ánh xạ nhưng không đọc; luồng hết-hạn-mật-khẩu bị hoãn |
| Thiếu gate NTNL_CD/CHNL_TYPE/BUSI_STTS | `AuthService.login` | Gate sau-auth bị hoãn; không trong phạm vi Sprint 0 |
| Không có SpringDoc / Swagger | — | Hoãn; thêm với module non-auth đầu tiên |
| Không có Testcontainers | `AuthServiceTest` | Test dùng Mockito; H2/in-memory hoãn tới domain thứ hai |
| Drift kiểu `SYS_SCR_OPTION_WECMS.SIZE` so với DEV | [V30__sprint1_screen_option_tables.sql:25](src/main/resources/db/migration/V30__sprint1_screen_option_tables.sql#L25) | DEV là `VARCHAR` (ví dụ `"250px"`); local là `INTEGER`. Hoãn — gói với tính năng View Setting SIZE đầu-cuối (trường entity + DTO + tiêu thụ `<col width>` frontend + backfill px căn-Figma) để sửa kiểu xuất hiện cùng consumer thật đầu tiên. |

## Ghi chú cho triển khai tương lai

- **SpringDoc OpenAPI** — module non-auth đầu tiên nên thêm `org.springdoc:springdoc-openapi-starter-webmvc-ui` và annotate controller với `@Operation`.
- **Refresh token / thu hồi logout** — khi cần, đưa vào một bảng `REFRESH_TOKEN`, xoay vòng khi dùng, và đấu nối thu hồi vào `JwtAuthenticationFilter`.
- **Bộ đếm auto-lock** — 5 thất bại liên tiếp → khóa user. `USER_LOGIN_HIS` có dữ liệu; triển khai bộ đếm trong `AuthService`.
- **Hết hạn mật khẩu** — `USER_INFM.PW_CHG_REQ_SEQ_NO` đã ánh xạ nhưng không dùng. Đấu nối tới một luồng `/auth/change-password` trước khi deploy non-local.
- **Phân giải menu / quyền** — cây menu sau-đăng-nhập bị hoãn. Khi nó xuất hiện, đưa vào `domain/menu/` và trả về tập menu đã phân giải bên trong `LoginResponse` hoặc một cuộc gọi `/api/v1/auth/menu`.
- **Rate-limiting / chống brute-force** — không trong phạm vi R&D nhưng bắt buộc trước bất kỳ deploy non-local nào.
- **Gate sau-auth NTNL_CD / CHNL_TYPE / BUSI_STTS** — JEX cũ kiểm tra các cờ nghiệp vụ bổ sung sau khi validate mật khẩu; triển khai chúng trong `AuthService.login` trước khi dùng production.
- **Tính năng View Setting SIZE** — khi (và chỉ khi) độ rộng cột tinh-chỉnh-được-bởi-admin hoặc kéo-để-resize trở thành yêu cầu sản phẩm thật, ship một migration + thay đổi xuyên-stack: `ALTER COLUMN "SIZE" TYPE VARCHAR(20)` để căn với DEV, ánh xạ trường trên `SysScrOptionWecms` / `UserScrOptionWecms`, thêm `@JsonProperty("SIZE")` vào `ScreenOptionItem`, lộ `SIZE` trong TS `ApiScreenOptionItem`, tiêu thụ nó trong render `<col width>` frontend, và backfill seed với giá trị **Figma WB365** (KHÔNG phải giá trị DEV cũ — hai cái khác nhau ở 5/7 cột). Tới khi đó SIZE cố ý là metadata chỉ-schema; nguồn chuẩn bố cục là Figma → Tailwind token.

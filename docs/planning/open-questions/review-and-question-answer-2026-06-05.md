# WeCMS Refactor — Rà soát đầy đủ & Phản hồi câu hỏi (2026-06-05)

**Gửi:** Team Phát triển Việt Nam · Team KOSIGN · Quản lý dự án
**Chủ đề:** Phản hồi từng mục, dựa trên source code, cho bản rà soát toàn dự án (`review-and-question-2026-06-05`)
**Cơ sở kiểm chứng:** mã nguồn backend `wecms-api` tính tới 2026-06-08, đối chiếu với hệ JEX cũ làm nguồn chân lý nghiệp vụ.
**Định dạng:** Mỗi phát hiện một mục riêng (PR-001 → PR-058). Không gộp, không bỏ sót mục nào.

---

## Tóm tắt điều hành (Executive Summary)

> **Lưu ý phạm vi.** Tài liệu này là bản rà soát mã nguồn + đánh giá hiện trạng triển khai. Quyết định sẵn-sàng-production cuối cùng, phê duyệt bảo mật, phê duyệt hạ tầng, phê duyệt tuân thủ pháp lý, và nghiệm thu nghiệp vụ vẫn thuộc trách nhiệm các bên liên quan (PO, Security, DevOps, Legal, Quản lý). Khi một mục được gắn nhãn "chặn production", nó chỉ ra một việc **phải được xử lý hoặc ký duyệt chính thức** trước khi lên production — không thay thế cho chữ ký của các bên đó.

Backend refactor WeCMS hiện là một **bản build R&D local**. Nó **phù hợp để tiếp tục SIT** (database phát triển, chưa thực thi tích hợp ngân hàng thật) nhưng **chưa sẵn sàng cho Production**.

Kể từ khi bản rà soát được viết ngày 2026-06-05, một số phát hiện đã được đóng hoặc tiến triển đáng kể nhờ công việc hoàn thành ngày 2026-06-06 / 2026-06-07. Hai mục nghiêm trọng nhất trong bản gốc — nghi vấn ánh xạ sai loại chứng từ e-Invoice (PR-036) và các mục tài liệu lỗi thời (PR-037, PR-038) — đã được xác nhận xử lý xong, và việc enforce phân quyền (PR-007) đã được triển khai đáng kể (nay xử lý một phần, xem bên dưới).

**Đã xử lý kể từ bản rà soát gốc:** PR-036, PR-037, PR-038. **Tiến triển đáng kể nhưng chưa đóng hẳn:** PR-007 (phân quyền theo menu nay đã enforce trên các endpoint nghiệp vụ; còn sót một số controller chưa annotate — xem mục của nó).

**Các mục cần xử lý hoặc ký duyệt trước production** (severity trong từng mục):
1. Đường dispatch tiền thật: engine dispatch trong refactor đã tồn tại nhưng các leg live còn ở dạng skeleton/mock/chưa cấp cấu hình, và chưa có đối soát (reconciliation) — PR-019, PR-013, PR-014.
2. Không có optimistic locking; cấp khóa tiền dễ race; idempotency/concurrency của đường callback/result dispatch chưa được đánh giá — PR-021, PR-020, PR-029.
3. Không có thu hồi token phía server — PR-006.
4. Không có auto-lock login / chống brute-force — PR-008.
5. Không có integration test cho các luồng tiền — PR-039.
6. Chưa có môi trường production / topology / CI-CD / secrets được định nghĩa — PR-043, PR-044.
7. Chưa có ký duyệt pháp lý/quy định cho việc lưu credential + scraping — PR-052.

(Theo thang rủi ro, các mục chặn-production nghiêm ngặt (P0) ở trên là PR-019, PR-006, PR-008, PR-039; PR-013/PR-014 là lỗ hổng toàn vẹn P0; PR-044/PR-052 là mục P1/cần-chủ-sở-hữu-ký, liệt kê ở đây vì chúng chặn việc go-live dù severity không phải P0.)

**Nhãn trạng thái dùng:** ĐÃ XỬ LÝ · XỬ LÝ MỘT PHẦN · CÒN MỞ · CẦN PO QUYẾT ĐỊNH · CẦN DEVOPS XÁC NHẬN · CẦN SECURITY XÁC NHẬN · CẦN LEGAL XÁC NHẬN · KHÔNG ÁP DỤNG.
**Mức rủi ro:** P0 (chặn production) · P1 (kiến trúc/an toàn, làm sớm) · P2 (cần làm rõ / không chặn). Mục đã xử lý hiển thị mức gốc dạng `N/A (trước là Pn)`.

---

# PR-001

## Câu hỏi từ team Việt Nam
Mục tiêu cuối của refactor là thay thế hoàn toàn JEX hay chạy song song (coexistence)? CLAUDE.md nói "no coexistence code" nhưng vẫn giữ `wb_get_next_val`, `sq_trco_key` "để tương thích JEX khi cutover" — vậy có cutover hay không?

## Trả lời
Đây là câu hỏi định hướng thực sự mà bản thân code không tự trả lời được. Backend được xây như một bản viết lại sạch (không tái hiện pattern runtime của JEX), nhưng một số ít helper sinh khóa legacy được cố ý giữ lại để các khóa do refactor tạo ra vẫn tương thích với hệ live tại thời điểm cutover sau này. Việc giữ các helper đó chỉ hợp lý dưới một kế hoạch coexistence/cutover; giữ chúng trong khi tuyên bố "no coexistence" là mâu thuẫn ở cấp chiến lược. Quyết định phải do Product Owner đưa ra vì nó chi phối toàn bộ roadmap kỹ thuật (key generator, migration dữ liệu, trình tự cutover).

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P1

## Bằng chứng
* File: common/legacy/LegacyFunctionRepository.java
* Class: LegacyFunctionRepository
* Method: nextTrcoKey / nextApprLineNo / nextChartAcctSeqNo
* Line(s): các nhánh `wb_get_next_val` legacy được giữ lại (xem thêm migration V113 / V91 / V95)

## Vì sao quan trọng
Lựa chọn thay-thế-hay-coexistence quyết định liệu có phải giữ hay thay thế các generator legacy, có chạy song song hai hệ không, và cách cutover dữ liệu. Định hướng chưa chốt sẽ chặn việc sửa cấp khóa (PR-029) và kế hoạch migration (PR-025).

## Hành động đề xuất
PO chốt: thay thế hoàn toàn với một lần cutover, hay coexistence. Ghi lại quyết định và căn chỉnh PR-025 và PR-029 theo đó.

---

# PR-002

## Câu hỏi từ team Việt Nam
Phạm vi MVP để go-live là gì? Có 42 controller nhưng nhiều cái "Backend implemented, frontend not verified". Định nghĩa "Done" của 1 feature là gì (BE+FE+test+doc)?

## Trả lời
Đã xác nhận: nhiều feature đã có backend nhưng frontend tương ứng chưa được verify, và chưa có Definition of Done thống nhất. Đây là khoảng trống về kế hoạch/quản trị, không phải lỗi code. "Backend implemented" không được hiểu là "feature hoàn chỉnh".

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P1 (khoảng trống kế hoạch/quản trị; bản thân không phải lỗi code chặn production)

## Bằng chứng
* File: (bản ghi trạng thái feature / cây controller)
* Class: 46 class `@RestController` dưới src/main/java (đếm độc lập 2026-06-08)
* Method: n/a
* Line(s): n/a

## Vì sao quan trọng
Không có Definition of Done, feature trông như đã xong ở backend nhưng có thể không có UI hoặc test chạy được, nên không thể tin cậy để lập kế hoạch release.

## Hành động đề xuất
PO định nghĩa danh sách feature MVP và một Definition of Done (backend + frontend + test + docs), rồi phân loại lại từng feature theo đó.

---

# PR-003

## Câu hỏi từ team Việt Nam
Multi-company: CLAUDE.md khẳng định "one user = one company" là luồng chính, chooser chỉ là fallback. Vậy có khách hàng thật nào dùng multi-company không? Nếu không, có nên gỡ hẳn để giảm bề mặt lỗi?

## Trả lời
Đã xác nhận trong luồng login: one-user-one-company là đường chính (một link active duy nhất sẽ tự resolve và cấp JWT, không có chooser). Chooser multi-company là một fallback an toàn được giữ lại cho user tình cờ có hai link active trở lên; nó không phải yêu cầu nghiệp vụ chính thức. Có khách hàng thật nào dùng multi-company hay không là dữ kiện nghiệp vụ chỉ PO mới xác nhận được.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P2

## Bằng chứng
* File: domain/auth/service/AuthService.java
* Class: AuthService
* Method: login / buildChooserResponse
* Line(s): 162-189 (auto-pick một link vs chooser nhiều link), 324-361 (builder chooser)

## Vì sao quan trọng
Một nhánh được giữ lại mà không khách nào dùng vẫn tốn chi phí maintenance và có thể che giấu bug.

## Hành động đề xuất
PO xác nhận multi-company có đang được dùng không. Nếu không, lên lịch gỡ nhánh chooser để giảm bề mặt.

---

# PR-004

## Câu hỏi từ team Việt Nam
14 mã PRDT_TYPE legacy nhưng chỉ model 3 (F/M/H). Roadmap các product variant còn lại (Lumens readonly, HAENGSUNG masking…) khi nào port? Ai là khách hàng đang chờ?

## Trả lời
Đã giải quyết một phần ở mặt dữ kiện: tập product-type của WeCMS đúng là F / M / H. Các ký tự legacy khác không phải product type của WeCMS — chúng round-trip nguyên trạng nhưng cố ý không được model. Các hành vi per-product (Lumens read-only overrides, layout bảng thanh toán + masking tài khoản của HAENGSUNG, ẩn cột expense, gating daily-financial-HS) bị defer và sẽ port theo từng feature. Phần còn mở — khách hàng nào đang chờ từng hành vi defer — là câu hỏi lập lịch của PO.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P2

## Bằng chứng
* File: common/enums/PrdtType.java
* Class: PrdtType
* Method: fromCode
* Line(s): chỉ có enum constant F / M / H; các ký tự khác resolve về empty (fail-safe)

## Vì sao quan trọng
Không biết khách hàng nào cần hành vi per-product nào thì không thể lập lịch cho các gate bị defer.

## Hành động đề xuất
PO nêu tên khách hàng đang thúc đẩy từng hành vi per-product bị defer để lên lịch MR feature tương ứng.

---

# PR-005

## Câu hỏi từ team Việt Nam
Có SLA/KPI nào cho từng module không (vd Payment dispatch phải xong trong cutoff)?

## Trả lời
Không có ngưỡng SLA/KPI đo lường được định nghĩa ở bất kỳ đâu trong codebase. Đây là định nghĩa nghiệp vụ phải được cung cấp trước khi có thể đo "đạt/không đạt" cho vận hành.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P2

## Bằng chứng
* File: n/a (không có cấu hình SLA/KPI)
* Class: n/a
* Method: n/a
* Line(s): n/a

## Vì sao quan trọng
Không có ngưỡng đo lường, không thể định nghĩa hay giám sát thành/bại của vận hành một cách khách quan.

## Hành động đề xuất
PO định nghĩa mục tiêu SLA/KPI theo từng module (vd thời gian hoàn tất dispatch trong cutoff).

---

# PR-006

## Câu hỏi từ team Việt Nam
JWT stateless, không refresh token, không blacklist/revocation. Khi sa thải nhân viên / khóa user, token vẫn sống tới hết expires-in (3600s). Với hệ thống chuyển tiền thật, đây có chấp nhận được không? → cần refresh + revocation trước go-live?

## Trả lời
Đã xác nhận và vẫn còn đúng. Token là stateless không có thu hồi phía server, và logout không thực hiện vô hiệu hóa phía server — nó trả về success và dựa vào client tự bỏ token. Token hiện có của một user đã bị khóa hoặc thôi việc do đó vẫn hợp lệ hoàn toàn tới khi hết hạn tự nhiên (mặc định ~3600s). Điều này chấp nhận được cho R&D local nhưng không cho hệ chuyển tiền ở production.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P0

## Bằng chứng
* File: domain/auth/controller/AuthController.java ; common/security/JwtAuthenticationFilter.java
* Class: AuthController ; JwtAuthenticationFilter
* Method: logout() ; doFilterInternal()
* Line(s): AuthController.java:59-62 (logout no-op); JwtAuthenticationFilter.java:34-96 (không có kiểm tra revocation/blacklist)

## Vì sao quan trọng
Một token bị đánh cắp hoặc cũ, hoặc token của một user vừa bị sa thải, vẫn dùng được tới hết vòng đời token, không có cách cắt bỏ.

## Hành động đề xuất
Trước bất kỳ deploy non-local nào, thêm refresh-token + thu hồi phía server (vd một store revocation/blacklist được kiểm trong JWT filter), hoặc rút ngắn vòng đời token kèm danh sách thu hồi rõ ràng. Security/PO đặt yêu cầu.

---

# PR-007

## Câu hỏi từ team Việt Nam
`@PreAuthorize` chưa gắn ở bất kỳ endpoint nào — authGrCd (ADMIN/USER) mới chỉ "advisory". Ai cũng gọi được endpoint admin. Khi nào enforce phân quyền? Đây là lỗ hổng nghiêm trọng.

## Trả lời
XỬ LÝ MỘT PHẦN — và lo ngại ban đầu của người rà soát là chính xác tại thời điểm đó. Một khối lượng phân quyền đáng kể đã landed ngày 2026-06-06, sau bản rà soát: method security nay đã bật (`@EnableMethodSecurity`), và các endpoint nghiệp vụ trước đây không có gate nay được gate bởi một guard phân quyền theo menu fail-closed kết hợp kiểm tra role. Guard từ chối các mã không xác định, mã ngoài cây sản phẩm của tenant, và mã user chưa được cấp; admin bỏ qua được grant per-user nhưng không bao giờ bỏ qua gate cây sản phẩm. Một coverage test bắt buộc annotation trên các handler không nằm trong allow-list.

Tuy nhiên, độ phủ **không phải toàn bộ**. Một lần đếm độc lập ngày 2026-06-08 thấy **46** class `@RestController`, trong đó **14 cái hoàn toàn không có `@PreAuthorize`**. Một số hợp lệ là public/allow-list (login, navigation, country reference, global exception handler). Nhưng một số khác chưa được biện minh trong một allow-list có tài liệu và cần rà soát từng cái — đáng chú ý là các controller **callback** của bank-dispatch `BulkExecutionResultController` và `BulkReceptionResultController` (hiện là skeleton, topology bảo mật đối ngoại — allowlist + IP whitelist — đánh dấu rõ là pending, TODO B7), cộng với `BankCredentialController`, `BankScrapingController`, `SyncHistoryController`, `OrganizerRuleController`, `EnvironmentSettingsController`, `BlockedCharsRuleController`, `ScreenOptionController`, `NotificationController`. Một callback bank/VAN không thể trình một user JWT, nên mô hình auth cuối cùng cho các endpoint callback (hiện nằm sau quy tắc `authenticated()` chung, không có `@menuAuthz`) là một điểm thiết kế còn mở, chưa đóng.

## Trạng thái hiện tại
XỬ LÝ MỘT PHẦN (còn lại: controller chưa annotate + mô hình auth của endpoint callback)

## Mức rủi ro
P1 (lỗ hổng "ai cũng gọi được endpoint admin" được báo cáo trước đây đã đóng cho các endpoint nghiệp vụ; phần còn lại là tập chưa-annotate + mô hình auth callback chưa giải quyết). Mức gốc: N/A (trước là P0).

## Bằng chứng
* File: config/SecurityConfig.java ; domain/navigation/service/MenuAuthorizationGuard.java ; src/test/.../EndpointAuthorizationCoverageTest.java ; domain/payment/dispatch/callback/controller/BulkExecutionResultController.java
* Class: SecurityConfig ; MenuAuthorizationGuard ; EndpointAuthorizationCoverageTest ; BulkExecutionResultController / BulkReceptionResultController
* Method: (annotation @EnableMethodSecurity) ; canAccess / canAccessAny / canAccessAccountInquiry / canAccessEInvoice ; executionResult / receptionResult
* Line(s): SecurityConfig.java:22 (@EnableMethodSecurity), :44-46 (danh sách permitAll = chỉ login/logout/health, mọi thứ khác `authenticated()`); MenuAuthorizationGuard.java:66-106 (quy tắc fail-closed); BulkExecutionResultController.java:20-22 (TODO B7 — security allowlist + IP whitelist pending); đếm độc lập 46 class `@RestController` / 14 không có `@PreAuthorize` (con số "30 trong 43" trước đây lấy từ tài liệu dự án, không phải đếm code, không dùng ở đây)

## Vì sao quan trọng
Lỗ hổng nghiêm trọng người rà soát nêu — user đã xác thực nhưng không phải admin vẫn vào được endpoint admin/nghiệp vụ — đã đóng cho các endpoint nghiệp vụ đã gate. Lo ngại còn lại hẹp hơn nhưng có thật: tập chưa-annotate phải được xác nhận là allow-list có chủ đích, có tài liệu; và các endpoint callback dispatch (sẽ nhận kết quả bank và flip trạng thái thanh toán) cần một mô hình xác thực rõ ràng trước bất kỳ môi trường tiền-thật nào.

## Hành động đề xuất
Xác nhận từng trong 14 controller chưa-annotate hoặc là allow-list có chủ đích (ghi tài liệu) hoặc thêm `@PreAuthorize` còn thiếu. Quyết định và triển khai mô hình xác thực cho các endpoint `/api/v1/payment/dispatch/callbacks/*` (mutual TLS / IP allowlist / signed envelope) trước khi chúng rời trạng thái skeleton. Dọn dẹp: xóa comment "advisory" lỗi thời ở JwtAuthenticationFilter.java:52-55.

---

# PR-008

## Câu hỏi từ team Việt Nam
Auto-lock sau 5 lần sai chưa implement; không rate-limiting/brute-force protection. Với màn login ngân hàng, bắt buộc trước production?

## Trả lời
Đã xác nhận và vẫn còn đúng. Lần đăng nhập thất bại được ghi thành FAIL row, nhưng không có bộ đếm số lần sai, không có auto-lock 5-fail, và không có rate-limiting/chống brute-force. Màn login do đó mở cho việc đoán mật khẩu vô hạn.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P0

## Bằng chứng
* File: domain/auth/service/AuthService.java
* Class: AuthService
* Method: login (ghi FAIL) ; (bộ đếm auto-lock không có)
* Line(s): 82-83 (ghi chú defer), 119-140 (ghi FAIL row nhưng không có counter/lock)

## Vì sao quan trọng
Số lần thử mật khẩu vô hạn trên một màn login ngân hàng là phơi nhiễm bảo mật nghiêm trọng.

## Hành động đề xuất
Triển khai bộ đếm auto-lock 5-fail trong AuthService.login (dữ liệu đã có sẵn trong USER_LOGIN_HIS) và thêm chống brute-force/rate-limiting trước bất kỳ deploy non-local nào.

---

# PR-009

## Câu hỏi từ team Việt Nam
Phân quyền theo account-level (ACCT_AUTH_INFO V/W grant) đã có ở data nhưng endpoint chuyển tiền có thực sự enforce W-grant ở mọi đường không, hay chỉ một số (own-account-transfer có check, các module payment khác thì sao)?

## Trả lời
Xác nhận một phần. Một guard truy cập tài khoản (`AccountAccessGuard`) có mặt trên đường own-account-transfer, và phân quyền cấp menu nay đã enforce trên các endpoint payment (PR-007). Việc guard đó có enforce **W (rút tiền)** grant của ACCT_AUTH_INFO cụ thể hay không — so với grant đọc/xem hoặc chỉ truy cập menu — **chưa được verify theo từng dòng** trong bản rà soát này, và việc enforce W-grant cấp tài khoản trên tài khoản rút cho các module payment khác (internal/vendor/expense/payroll/overseas) cũng chưa được verify. Cả hai nên được audit rõ ràng.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P1

## Bằng chứng
* File: domain/banking/service/AccountAccessGuard.java
* Class: AccountAccessGuard
* Method: (kiểm tra truy cập tài khoản — loại grant chính xác chưa verify theo dòng)
* Line(s): guard có mặt trên own-account transfer; ngữ nghĩa W-grant và các module payment khác chưa verify (suy ra từ vai trò class, chưa xác nhận với source)

## Vì sao quan trọng
Phân quyền menu kiểm soát màn hình nào user mở được; W-grant cấp tài khoản kiểm soát họ có được rút từ một tài khoản cụ thể hay không. Thiếu cái sau trên một đường payment sẽ là một kiểm tra phân quyền bị bỏ sót.

## Hành động đề xuất
Audit mọi module payment về việc enforce W-grant cấp tài khoản trên tài khoản rút, không chỉ phân quyền menu.

---

# PR-010

## Câu hỏi từ team Việt Nam
reveal credential (login-credentials, contacts) trả password plaintext qua wire. Cơ chế bảo vệ là gì (chỉ HTTPS? audit? rate-limit?) — có log ai reveal cái gì không?

## Trả lời
Đã xác nhận rằng hành động reveal giải mã và trả về plaintext, và có một endpoint audit-log cho login credentials. Việc giới hạn transport (chỉ TLS), rate-limiting, và mức độ đầy đủ của audit theo từng lần reveal chưa được xác nhận trong bản rà soát này và cần một xác nhận bảo mật.

## Trạng thái hiện tại
CẦN SECURITY XÁC NHẬN

## Mức rủi ro
P1

## Bằng chứng
* File: domain/setting/logincredentials/controller/LoginCredentialsController.java
* Class: LoginCredentialsController
* Method: reveal ; auditLogs
* Line(s): có endpoint `/reveal` + `/audit-logs` (gate theo menu ở cấp class)

## Vì sao quan trọng
Trả về plaintext nhạy cảm mà không đảm bảo audit và rate-limit khiến việc rò rỉ credential ngân hàng không truy vết được.

## Hành động đề xuất
Security xác nhận mọi lần reveal đều được audit (ai/cái gì/khi nào) và có rate-limit, và endpoint chỉ truy cập được qua TLS.

---

# PR-011

## Câu hỏi từ team Việt Nam
Khóa AES-GCM (BankCertCryptoService) lưu ở đâu, xoay vòng key thế nào? Backup/restore thì credential cũ giải mã được không?

## Trả lời
Việc lưu trữ và xoay vòng khóa không được ghi tài liệu trong codebase, và khả năng giải mã sau backup/restore chưa được verify. Đây là câu hỏi sở hữu vận hành/bảo mật.

## Trạng thái hiện tại
CẦN SECURITY XÁC NHẬN

## Mức rủi ro
P2

## Bằng chứng
* File: (bank certificate crypto service)
* Class: BankCertCryptoService
* Method: encrypt/decrypt
* Line(s): nguồn khóa không có trong repository (cấp từ bên ngoài)

## Vì sao quan trọng
Mất hoặc xoay vòng khóa mã hóa không được quản lý đồng nghĩa mất quyền truy cập toàn bộ credential đã mã hóa.

## Hành động đề xuất
Security/DevOps định nghĩa quyền giữ khóa, xoay vòng, và một hệ quản lý khóa (vd KMS/Vault) trước khi lưu bất kỳ credential thật nào.

---

# PR-012

## Câu hỏi từ team Việt Nam
WECMS_JWT_SECRET quản lý ra sao ở các môi trường? Có vault không hay env var?

## Trả lời
JWT secret được cấp qua biến môi trường với validation fail-fast (ứng dụng từ chối khởi động nếu secret thiếu/quá ngắn). Chưa có hệ quản lý secret. Chấp nhận được cho R&D local nhưng không lý tưởng cho production.

## Trạng thái hiện tại
CẦN DEVOPS XÁC NHẬN

## Mức rủi ro
P2

## Bằng chứng
* File: common/security/JwtTokenProvider.java ; config/JwtProperties.java
* Class: JwtTokenProvider ; JwtProperties
* Method: (constructor fail-fast khi secret thiếu/ngắn)
* Line(s): secret đọc từ env `WECMS_JWT_SECRET`

## Vì sao quan trọng
Một secret dạng biến môi trường thuần khó xoay vòng và dễ rò rỉ hơn một secret được quản lý.

## Hành động đề xuất
DevOps chuyển secret sang hệ quản lý secret theo từng môi trường trước khi deploy.

---

# PR-013

## Câu hỏi từ team Việt Nam
NXiSAS engine chạy trên browser của user (own-account-transfer), Spring chỉ "tin và lưu kết quả". Làm sao chống client giả mạo kết quả chuyển tiền thành công? scraping-result có idempotency nhưng có chữ ký/đối soát ngược với ngân hàng không?

## Trả lời
Đã xác nhận: engine trên browser post kết quả scrape về backend, backend áp idempotency, dung sai số tiền (amount-tolerance), và kiểm tra ownership trước khi lưu — nhưng không có đối soát ngược với sao kê ngân hàng thực và không có chữ ký mật mã cho kết quả. Niềm tin cuối cùng đặt ở client. Đây là một lỗ hổng toàn vẹn kỹ thuật/bảo mật cụ thể (không chỉ là điểm cần quyết định); *biện pháp giảm thiểu chấp nhận được* là quyết định PO/kiến trúc, nhưng bản thân lỗ hổng vẫn mở bất kể.

## Trạng thái hiện tại
CÒN MỞ (+ quyết định PO/kiến trúc)

## Mức rủi ro
P0

## Bằng chứng
* File: domain/banking (xử lý scraping-result) ; domain/banking/nxisas
* Class: (service scraping-result)
* Method: nhận scraping-result (idempotency + amount tolerance + ownership)
* Line(s): có các guard; không có đối soát sao kê

## Vì sao quan trọng
Một client bị chỉnh sửa có thể báo cáo "thành công" giả mà không có đối soát phía server với ngân hàng để phát hiện.

## Hành động đề xuất
PO/kiến trúc yêu cầu đối soát phía server với sao kê ngân hàng (và/hoặc kết quả có chữ ký) trước khi tin một kết quả thành công do client báo.

---

# PR-014

## Câu hỏi từ team Việt Nam
Khi Coocon gateway timeout/đổ giữa chừng (đã LOGIN+TRANSACTION nhưng mất BALANCE), trạng thái DB có nhất quán không? Có cơ chế reconcile?

## Trả lời
Đã xác nhận: không tìm thấy cơ chế reconcile/sửa chữa cho lỗi gateway giữa chừng. Một lần scrape dở dang có thể để lại trạng thái balance/giao dịch không nhất quán mà không có khôi phục tự động.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P0

## Bằng chứng
* File: domain/banking (các đường tích hợp)
* Class: (không có reconcile job)
* Method: n/a
* Line(s): không có routine đối soát

## Vì sao quan trọng
Không có cơ chế reconcile, lỗi một phần để lại dữ liệu không nhất quán mà không tiến trình nào sửa.

## Hành động đề xuất
Thêm routine reconcile/sửa chữa cho các luồng bị gián đoạn; PO định nghĩa SLA về tính nhất quán.

---

# PR-015

## Câu hỏi từ team Việt Nam
Session scraping lưu in-memory NxisasSessionRegistry → multi-instance / restart là mất session. Có chấp nhận chỉ chạy 1 instance? Khi scale ngang thì sao?

## Trả lời
Đã xác nhận. Session challenge scraping được giữ trong một concurrent map in-memory, nên restart hoặc instance thứ hai sẽ mất session OTP/QNA/CAPTCHA đang dở. Không có session store chia sẻ. Ổn cho một instance nhưng vỡ khi restart hoặc scale ngang.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P1

## Bằng chứng
* File: domain/banking/nxisas/session/NxisasSessionRegistry.java
* Class: NxisasSessionRegistry
* Method: (field map session)
* Line(s): 26-28 (`ConcurrentHashMap<UUID, NxisasScrapeSession>`)

## Vì sao quan trọng
Restart hoặc deploy đa pod làm mất session challenge của user giữa chừng, phá vỡ lần scrape.

## Hành động đề xuất
Quyết định topology (PR-044). Nếu dùng nhiều hơn một instance, chuyển session sang store chia sẻ (vd Redis).

---

# PR-016

## Câu hỏi từ team Việt Nam
Credential Coocon, endpoint gateway, môi trường sandbox vs production của NXISAS được cấu hình ở đâu, ai cấp?

## Trả lời
Endpoint gateway và key được cấu hình theo từng môi trường qua application properties (host DEV/SIT và UAT/PROD riêng biệt), nhưng chủ sở hữu cấp phát và việc tách sandbox-vs-production chính thức không được ghi tài liệu. Đây là câu hỏi sở hữu DevOps/PO.

## Trạng thái hiện tại
CẦN DEVOPS XÁC NHẬN

## Mức rủi ro
P1

## Bằng chứng
* File: application*.yml (properties tích hợp gateway)
* Class: (config properties gateway)
* Method: n/a
* Line(s): cấu hình kiểu `wecms.coocon.smart-api-*`

## Vì sao quan trọng
Sở hữu cấu hình không rõ khiến việc tách test khỏi production không an toàn.

## Hành động đề xuất
DevOps/PO ghi tài liệu ai cấp credential gateway và cách giữ sandbox tách biệt với production.

---

# PR-017

## Câu hỏi từ team Việt Nam
OSMC→GDT (inquire/update): rate limit của GDT là bao nhiêu? update re-poll toàn bộ cache có thể bùng request — có throttle/batch-size cap không?

## Trả lời
Đã xác nhận: không tìm thấy throttle hay batch-size cap trên đường re-poll update của GDT. Một lần re-poll không giới hạn có thể vượt rate limit của nhà cung cấp.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: domain/einvoice ; domain/operation (GDT inquire/update)
* Class: (service tích hợp GDT)
* Method: inquire / update
* Line(s): không có throttle/batch cap

## Vì sao quan trọng
Re-poll không giới hạn có thể vượt rate limit của GDT và bị chặn hoặc timeout.

## Hành động đề xuất
Xác nhận rate limit của GDT và thêm batch-size cap / throttle trên đường update.

---

# PR-018

## Câu hỏi từ team Việt Nam
Tỷ giá on-demand fetch Shinhan: nếu API Shinhan down, user thấy gì? Có fallback ngày gần nhất (đã thấy có) nhưng có cảnh báo "dữ liệu cũ" không?

## Trả lời
Đã xác nhận: có fallback ngày-gần-nhất-có-sẵn khi ngày yêu cầu không có dữ liệu, nhưng tỷ giá trả về không được gắn nhãn "cũ". Một user có thể coi tỷ giá cũ là tỷ giá hiện tại.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: domain/banking/service/ExchangeRateService.java
* Class: ExchangeRateService
* Method: (tra cứu tỷ giá với fallback)
* Line(s): 97-100 (fallback về ngày gần nhất có sẵn; không có nhãn cũ)

## Vì sao quan trọng
Một tỷ giá fallback không gắn nhãn có thể bị nhầm là tỷ giá hiện tại.

## Hành động đề xuất
Thêm chỉ báo "tỷ giá cũ" trên frontend (và một cờ trên wire) khi dùng fallback.

---

# PR-019

## Câu hỏi từ team Việt Nam
Đường dispatch thật (TB_PAY_TARGET / TB_PAY_FILE_*) đã kết nối ngân hàng thật chưa, hay mới chỉ ghi bảng shadow? Ai xác nhận tiền thực sự đi?

## Trả lời
Đã đính chính sau khi re-verify trực tiếp source. **Không** chính xác khi nói refactor chỉ ghi bảng shadow hoặc nửa gửi-bank hoàn toàn defer sang daemon JEX legacy. Một engine dispatch đáng kể tồn tại trong refactor dưới `domain/payment/dispatch/`, gồm:

- **VAN/EFS client seam** — `van/client/EfsApiClient` (interface), `van/client/EfsProperties`, `van/crypto/EfsCryptoService`; bean đang active là `MockEfsApiClient`.
- **SFTP batch seam** — `sftp/SftpClient`, `sftp/SftpProperties`; bean active `MockSftpClient`.
- **Realtime dispatch service** — `service/RealtimeTransferService` (đường realtime EFS 202/204).
- **Bulk reception service** — `service/BulkReceptionService`.
- **Result-check scheduler** — `scheduler/ResultCheckJob` + `service/ResultCheckService` (+ `service/DispatchResultResolver`).
- **Codec file fixed-length** — `file/FixedLengthCodec`, `file/DepositHeader`, `DepositData`, `DepositTrailer`.
- **Entity firm-banking dispatch** — `TB_FIRM_TRAN`, `TB_FIRM_FILE_MST/DATA/RSLT`, `TB_FIRM_BAT_API_TRAN`, `TB_BATCH_SCHEDULE_INFO/DTL`, cùng `TB_PAY_TARGET` / `TB_PAY_FILE_*`.
- **Controller callback kết quả bank** — `callback/controller/BulkExecutionResultController` (01302) và `BulkReceptionResultController`, với `callback/service/*` và DTO `EfsCallbackEnvelope`.

Lý do **không lệnh chuyển tiền thật nào được thực thi trong bản build local hiện tại** là vì các leg live còn ở dạng skeleton hoặc mock, không phải vì thiếu kiến trúc: `RealtimeTransferService.transfer()` là skeleton ném "not implemented" (TODO B1/B2); `MockEfsApiClient.post()` đang active ném `UnsupportedOperationException` để đường EFS live "không bao giờ bị kích hoạt nhầm"; các controller callback là skeleton ("No business processing yet"); và base URL gateway EFS + API key per-VAN chưa được cấp (EfsApiClient TODO B1/B2). Riêng đường kiểu legacy `InternalTransferDispatchService.dispatch()` ghi các row `TB_PAY_TARGET` / `TB_PAY_FILE_*` với `PROC_STAT='9'` (pending) và Javadoc của nó nói việc flip trạng thái bank-network "do live JEX dispatch daemon xử lý".

**Mâu thuẫn cần nêu:** hai mô tả này đối nghịch — `InternalTransferDispatchService` defer leg bank sang daemon JEX bên ngoài, trong khi engine `dispatch/` mới hơn xây đường gửi EFS/SFTP trong refactor với callback riêng. Đường nào là chính thống cho go-live cần Backend-Lead làm rõ.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P0

## Bằng chứng
* File: domain/payment/dispatch/service/RealtimeTransferService.java ; domain/payment/dispatch/van/client/MockEfsApiClient.java ; domain/payment/dispatch/van/client/EfsApiClient.java ; domain/payment/dispatch/callback/controller/BulkExecutionResultController.java ; domain/payment/internaltransfer/service/InternalTransferDispatchService.java ; domain/payment/dispatch/service/TransferDispatchExecutor.java
* Class: RealtimeTransferService ; MockEfsApiClient ; EfsApiClient ; BulkExecutionResultController ; InternalTransferDispatchService ; TransferDispatchExecutor
* Method: transfer (skeleton, ném "not implemented") ; post (ném UnsupportedOperationException) ; executionResult (skeleton) ; dispatch
* Line(s): RealtimeTransferService.java:17 + :27-32 (CHỈ SKELETON, TODO B1/B2, chưa implement); MockEfsApiClient.java:23-34 (ném UnsupportedOperationException — EFS live bị chặn bởi B1/B2); EfsApiClient.java:13-16 (base URL + API key chưa cấp); BulkExecutionResultController.java:17-22 (SKELETON, security allowlist/IP-whitelist pending TODO B7); InternalTransferDispatchService.java (Javadoc class — "tích hợp bank-network … do live JEX dispatch daemon xử lý")

## Vì sao quan trọng
Câu chữ gốc sẽ khiến các bên hiểu là khả năng gửi-tới-bank hoàn toàn không tồn tại trong refactor; thực ra nó đang được xây (VAN realtime + SFTP batch + result callback + scheduler) nhưng đang ở mock/skeleton/chưa-cấp-cấu-hình. Rủi ro chính xác là: (a) đường live chưa hoạt động hoặc chưa được chứng minh, và (b) có một câu hỏi kiến trúc chưa giải quyết là dispatch thuộc engine EFS của refactor hay ủy thác cho daemon JEX. Cả hai phải được chốt trước khi tiền thật chảy.

## Hành động đề xuất
Backend Lead xác nhận đường dispatch chính thống (engine EFS của refactor vs daemon JEX) và hòa giải comment mâu thuẫn ở `InternalTransferDispatchService`. Hoàn thiện và cấp cấu hình cho leg live đã chọn (EFS B1/B2 URL+key, hoặc hợp đồng daemon) với một đường xác nhận-thanh-toán/đối-soát trước khi bật tiền thật. Giữ tiền thật tuyệt đối ngoài SIT/UAT cho tới khi đường live hoạt động và được verify.

---

# PR-020

## Câu hỏi từ team Việt Nam
Idempotency của dispatch: nếu user double-click hoặc retry, có chống chuyển tiền 2 lần không? rerun chỉ cho RS_CD transient + "hôm nay" — đủ an toàn?

## Trả lời
Có thêm các guard chưa thấy được tại thời điểm rà soát gốc — dù vẫn còn khoảng trống, và phân tích dưới đây giới hạn phạm vi. Trên đường **internal-transfer**, việc nộp trùng được guard ở cấp trạng thái chứng từ: master đã transfer bị từ chối (WBE2064), master đang trong approval bị từ chối (WBE2062), approval ở trạng thái cuối bị từ chối (WBE2095), và re-run chỉ giới hạn cho các mã kết quả transient trong cùng ngày. Không có idempotency key tường minh theo từng request, nên bảo vệ phụ thuộc vào trạng thái chứng từ cộng với kiểm soát concurrency bàn ở PR-021.

**Giới hạn phạm vi:** đánh giá này chỉ phủ service internal-transfer. Engine `domain/payment/dispatch/` mới hơn — đặc biệt đường nhận kết quả bank (`BulkReceptionService`, `BulkExecutionResultService`, `ResultCheckService`/`ResultCheckJob`, và các controller callback) — **chưa** được đánh giá về idempotency. Callback bank có thể bị VAN retry hoặc trùng, nên đường đó cần rà soát idempotency riêng trước khi rời trạng thái skeleton.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P0

## Bằng chứng
* File: domain/payment/internaltransfer/service/InternalTransferDispatchService.java ; InternalTransferService.java ; (chưa đánh giá: domain/payment/dispatch/callback/service/*, domain/payment/dispatch/service/ResultCheckService.java)
* Class: InternalTransferDispatchService ; InternalTransferService ; (chưa đánh giá: BulkReceptionService / BulkExecutionResultService / ResultCheckService)
* Method: dispatch ; rerun ; requestApproval ; actOnApproval
* Line(s): InternalTransferDispatchService.java:98-100 (WBE2064); InternalTransferService.java:643-658 (rerun hôm nay + RS_CD), 812-814 (WBE2062), 1109-1112 (WBE2095)

## Vì sao quan trọng
Một double-submit concurrent thực sự trên đường internal-transfer bị giới hạn bởi trạng thái chứng từ và race cấp khóa (PR-021), không phải bởi idempotency key — nên dưới concurrency, đảm bảo là chưa đầy đủ. Idempotency của đường callback/result hiện chưa rõ.

## Hành động đề xuất
Backend Lead xác nhận guard theo trạng thái là đủ trên đường internal-transfer, hoặc thêm idempotency key tường minh cho từng dispatch; và đánh giá riêng idempotency trên đường callback/result-check của `domain/payment/dispatch/`. Giải quyết cùng PR-021.

---

# PR-021

## Câu hỏi từ team Việt Nam
Concurrency: 2 người cùng approve/dispatch 1 batch, hoặc cùng confirm 1 transaction — có optimistic lock (@Version) không? Hiện tôi thấy nhiều chỗ MAX+1 allocate key với "retry 1 lần" — đủ chịu tải đồng thời?

## Trả lời
Đã xác nhận và vẫn còn đúng. Re-verify độc lập ngày 2026-06-08: không có optimistic locking JPA ở bất kỳ đâu trong `src/main/java` (tìm toàn cây `@Version` trả về **0**; cũng không có `@Lock` pessimistic). Khóa tiền được cấp bằng MAX+1 với một lần retry. Một đường được serialize an toàn bởi row lock của PostgreSQL (số scheduled-payment dùng ON CONFLICT … DO UPDATE), nhưng transfer sequence và approval sequence vẫn dễ race — caller concurrent thứ hai được retry một lần bắt, caller thứ ba có thể fail hoặc đụng.

**Giới hạn phạm vi:** đánh giá này phủ các đường cấp khóa trong executor internal-transfer/dispatch. Đường callback và result-check của `domain/payment/dispatch/` (`BulkReceptionService`, `ResultCheckService`/`ResultCheckJob`, callback service) **chưa** được đánh giá về concurrency — callback bank đồng thời cập nhật cùng một dispatch row nên được rà soát khi đường đó rời trạng thái skeleton.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P0

## Bằng chứng
* File: domain/payment/dispatch/service/DispatchKeyGenerator.java ; domain/payment/internaltransfer/service/InternalTransferKeyGenerator.java ; InternalTransferService.java
* Class: DispatchKeyGenerator ; InternalTransferKeyGenerator ; InternalTransferService
* Method: nextTranSeq ; nextScdulPymnNo ; allocateApprSeqNo / requestApproval
* Line(s): DispatchKeyGenerator.java:39-53 (MAX+1 dễ race); InternalTransferKeyGenerator.java:76-87 (lock ON CONFLICT an toàn); InternalTransferService.java:828-836 (một lần retry), 935-942 (MAX+1 trên hai bảng); đếm `@Version` = 0 toàn `src/main/java` (re-verify 2026-06-08); đường callback/result chưa đánh giá

## Vì sao quan trọng
Hai request đồng thời có thể double-dispatch, double-confirm, hoặc đụng key — không chấp nhận với chuyển tiền. Hành vi concurrency của đường callback/result hiện chưa rõ.

## Hành động đề xuất
Thêm optimistic locking theo version trên các entity master/summary thanh toán và/hoặc dùng database sequence cho khóa tiền, và đánh giá đường callback/result-check của dispatch về cập nhật đồng thời. Phụ thuộc quyết định topology (PR-044).

---

# PR-022

## Câu hỏi từ team Việt Nam
CutoffValidator chặn ngoài giờ/cuối tuần — quy tắc cutoff theo network (SWIFT/NAPAS) lấy từ đâu, có config được không, có tính holiday VN không?

## Trả lời
Đã xác nhận: validator cutoff chặn cuối tuần và áp các khung giờ theo network, nhưng các quy tắc được hardcode trong Java và không có lịch nghỉ lễ Việt Nam hay cấu hình bên ngoài. Có thể cho phép hoặc chặn sai vào ngày lễ.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P1

## Bằng chứng
* File: domain/payment/internaltransfer/service/CutoffValidator.java
* Class: CutoffValidator
* Method: (validate)
* Line(s): 71 (kiểm tra cuối tuần), 87-99 (khung giờ hardcode); không có lịch lễ / nguồn config

## Vì sao quan trọng
Quy tắc cutoff hardcode không có lịch lễ có thể cho phép hoặc chặn dispatch sai vào ngày lễ VN.

## Hành động đề xuất
PO cung cấp quy tắc cutoff theo network và một lịch lễ VN; làm cho chúng cấu hình được.

---

# PR-023

## Câu hỏi từ team Việt Nam
Workflow phê duyệt (EXPT_RESOL_*): nếu approver bị xóa/khóa giữa chừng, chuỗi duyệt xử lý sao? Re-assign được không?

## Trả lời
Đã xác nhận: không có quy tắc định nghĩa cho approver bị xóa hoặc khóa giữa chừng. Chuỗi phê duyệt có thể kẹt mà không có đường reassignment.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P1

## Bằng chứng
* File: domain/payment (luồng approval-resolution, EXPT_RESOL_*)
* Class: (service approval)
* Method: hành động approval
* Line(s): không có xử lý approver bị vô hiệu

## Vì sao quan trọng
Một approver bị vô hiệu có thể làm deadlock workflow phê duyệt.

## Hành động đề xuất
PO định nghĩa chính sách approver-bị-vô-hiệu (bỏ qua, reassign, hay escalate) và cho triển khai.

---

# PR-024

## Câu hỏi từ team Việt Nam
Vendor/Expense/Payroll là copy-paste 1:1 của Internal Transfer. Tech debt: có nên gộp thành 1 service generic theo SCDUL_PYMN_TYPE_CD không, hay giữ tách để dễ tùy biến per-module?

## Trả lời
Đã xác nhận: các service thanh toán Vendor, Expense, và Payroll gần như trùng lặp, khác nhau chủ yếu ở mã loại thanh toán và audit tag. Việc có gộp hay không là một đánh đổi thiết kế (DRY vs linh hoạt per-module) cho Backend Lead/PO.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P2

## Bằng chứng
* File: domain/payment/{vendorpayment,expensepayment,payrollpayment}/service/*
* Class: VendorPaymentService ; ExpensePaymentService ; PayrollPaymentService
* Method: (các implementation song song)
* Line(s): thân service gần như giống hệt, khác bởi SCDUL_PYMN_TYPE_CD + audit slug

## Vì sao quan trọng
Copy-paste nghĩa là một bug phải sửa ở bốn chỗ; gộp lại giảm linh hoạt per-module — một đánh đổi thật.

## Hành động đề xuất
Backend Lead/PO quyết định: gộp thành một service generic theo loại thanh toán, hay giữ tách.

---

# PR-025

## Câu hỏi từ team Việt Nam
Quy tắc "LOCAL mirror DEV column-for-column" + "max 30 row/bảng" — nhưng đây là R&D local. Khi lên UAT/PROD thật, data thật migrate thế nào? Có plan ETL từ JEX PROD không?

## Trả lời
Đã xác nhận: seed hiện tại chỉ cho phát triển local nhẹ (số row giới hạn, một tenant). Không có kế hoạch/tooling ETL/migration để chuyển data production thật từ hệ live sang UAT/PROD.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P1

## Bằng chứng
* File: src/main/resources/db/migration (seed migration)
* Class: n/a (Flyway seed)
* Method: n/a
* Line(s): seed giới hạn một tenant / số row bị cap

## Vì sao quan trọng
Một seed phát triển không phải là cutover; không có tooling ETL thì không có cách đưa data production thật sang.

## Hành động đề xuất
PO/Backend định nghĩa một kế hoạch + tooling migration data thật trước bất kỳ cutover nào (căn chỉnh với PR-001).

---

# PR-026

## Câu hỏi từ team Việt Nam
Flyway đã tới ~V187 với nhiều migration "renumbered, file header stale", revert qua lại (V35→V36, V37→V38…). Lịch sử migration có sạch để rebuild from scratch trên môi trường mới không? Đã test flyway clean + migrate trên DB rỗng chưa?

## Trả lời
Lo ngại được xác nhận: lịch sử migration dài, với một số bước sửa chữa/đánh số lại/revert. Một lần rebuild sạch từ database rỗng chưa được xác nhận trong bản rà soát này. Đường cấp phát schema chính thống cho một môi trường mới nên được xác nhận và test.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P1

## Bằng chứng
* File: src/main/resources/db/migration (V1 … V243)
* Class: n/a (Flyway migration)
* Method: n/a
* Line(s): nhiều migration sửa chữa/revert (vd V35→V36, V37→V38)

## Vì sao quan trọng
Một chuỗi dài, vá lặp đi lặp lại làm dấy lên nghi ngờ nó replay sạch; một môi trường mới có thể build schema fail.

## Hành động đề xuất
Xác nhận và test đường cấp phát schema chính thống trên một database rỗng; ghi tài liệu quy trình rebuild được hỗ trợ.

---

# PR-027

## Câu hỏi từ team Việt Nam
ddl-auto: validate — entity chỉ map subset cột. Có chắc không có cột NOT NULL nào thiếu DEFAULT khi INSERT từ Java không (rủi ro runtime)?

## Trả lời
Rủi ro được xác nhận: schema validate dựa trên các entity cố ý chỉ map một subset cột. Các đường insert chưa test có thể đụng một cột NOT-NULL không có default ở runtime.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: (entity dưới domain/**/entity ; schema migration)
* Class: nhiều entity (map subset cột)
* Method: n/a
* Line(s): subset field entity vs schema DB đầy đủ

## Vì sao quan trọng
Một cột NOT-NULL không default, bị đụng bởi một insert Java không set nó, gây constraint violation ở runtime.

## Hành động đề xuất
Thêm test cho các đường insert chạy trên đúng engine database production để lộ các vi phạm constraint do thiếu default (xem PR-040).

---

# PR-028

## Câu hỏi từ team Việt Nam
Các bảng *_HIS (audit/history) legacy dùng trigger — refactor "defer triggers". Vậy history/audit trail của các bảng đó hiện trống — có yêu cầu compliance cần không?

## Trả lời
Đã xác nhận: các trigger history/audit legacy bị defer, nên các bảng history theo trigger không được điền bởi các write của refactor. Việc một audit trail cấp-bảng có bắt buộc về mặt pháp lý hay không là câu hỏi compliance.

## Trạng thái hiện tại
CẦN LEGAL XÁC NHẬN

## Mức rủi ro
P2

## Bằng chứng
* File: src/main/resources/db/migration (trigger bị defer)
* Class: n/a
* Method: n/a
* Line(s): trigger history được ghi là defer trong chiến lược schema

## Vì sao quan trọng
Nếu compliance yêu cầu audit trail cấp-bảng, các trigger bị defer nghĩa là hiện đang thiếu.

## Hành động đề xuất
Legal/Compliance xác nhận audit history cấp-bảng có bắt buộc không; nếu có, port các trigger liên quan.

---

# PR-029

## Câu hỏi từ team Việt Nam
`wb_get_next_val` (legacy PL/pgSQL) vẫn được dùng cho TRCO_KEY, APPR_LINE_NO, CHART_ACCT_SEQ_NO, USE_USAG_CD, RMRK_SEQ_NO… per-tenant MAX+1 không phải sequence → race condition khi tải cao. Kế hoạch thay bằng generator Java/sequence khi nào?

## Trả lời
Đã xác nhận: một số khóa vẫn được cấp bởi helper per-tenant MAX+1 legacy, được guard bằng một unique constraint cộng một lần retry. Cái này không atomic dưới concurrency dồn dập và vẫn có thể throw hoặc cấp trùng key dưới tải. Thời điểm thay thế phụ thuộc quyết định coexistence (PR-001).

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P1

## Bằng chứng
* File: common/legacy/LegacyFunctionRepository.java
* Class: LegacyFunctionRepository
* Method: nextTrcoKey / nextApprLineNo / nextChartAcctSeqNo
* Line(s): nhánh MAX+1 qua `wb_get_next_val` (migration V113 / V91 / V95)

## Vì sao quan trọng
Một per-tenant MAX+1 không atomic có thể đụng nhau dưới tải đồng thời, ném lỗi hoặc nhân đôi khóa.

## Hành động đề xuất
Thay bằng generator Java hoặc database sequence dưới cùng track concurrency với PR-021; trình tự phụ thuộc PR-001.

---

# PR-030

## Câu hỏi từ team Việt Nam
Có chỗ dùng native query + LATERAL JOIN (smart-organizer eligible) — đã test với volume thật chưa? Index đủ chưa?

## Trả lời
Đã xác nhận: danh sách eligible của Smart Organizer dùng một native query với lateral join. Nó chưa được benchmark ở volume dữ liệu thật, và mức đủ của index chưa được verify.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P1

## Bằng chứng
* File: domain/banking/smartorganizer/service/SmartOrganizerService.java
* Class: SmartOrganizerService
* Method: listEligible
* Line(s): 106-120 (dựng native query)

## Vì sao quan trọng
Một native query chưa benchmark có thể quét bảng lớn và chậm trên dữ liệu thật.

## Hành động đề xuất
Benchmark query ở volume cỡ production và rà soát/thêm index.

---

# PR-031

## Câu hỏi từ team Việt Nam
Một số filter làm in-memory sau khi fetch (transaction type/keyword, account by-bank). Với tài khoản nhiều giao dịch, có nên đẩy xuống DB?

## Trả lời
Phần lớn đã giải quyết cho đường account list; một đường liên quan chưa verify. Source xác nhận endpoint account list nay thực hiện filter và phân trang **phía server** — bank code và keyword được truyền vào query repository (`AcctMstr.findFiltered`) và tổng theo currency được tính trong database (`aggregateByCcy`), không phải in-memory. Trường hợp account-by-bank/keyword mà bản rà soát nêu do đó đã giải quyết cho trang đó. Việc có trang list nào khác (hoặc provider export Excel) còn filter in-memory hay không **chưa được verify trong bản rà soát này** — không có filter export in-memory nào được xác nhận với source ở đây, nên để là mục chưa-verify rõ ràng thay vì khẳng định.

## Trạng thái hiện tại
ĐÃ XỬ LÝ (đường account list) · CÒN MỞ/chưa verify (các trang list khác + provider export)

## Mức rủi ro
P2

## Bằng chứng
* File: domain/banking/service/AccountService.java
* Class: AccountService
* Method: list (findFiltered + aggregateByCcy)
* Line(s): 199-231 (server-side `acctRepo.findFiltered(...)` với bankCd + keywordPattern; tổng `aggregateByCcy(...)` tính trong DB)

## Vì sao quan trọng
Filter phía server tránh nạp tập kết quả lớn vào bộ nhớ. Trang account list không còn vấn đề này; trạng thái các trang/export khác chỉ đơn giản là chưa rõ tới khi kiểm tra.

## Hành động đề xuất
Không cần hành động cho account list. Riêng việc verify (đừng giả định) liệu các trang list khác hay provider export Excel có filter in-memory không, và đẩy xuống database nếu xác nhận. Không chặn.

---

# PR-032

## Câu hỏi từ team Việt Nam
Chưa có SpringDoc/OpenAPI — frontend dựa vào đâu để biết contract? Có nên thêm để generate type cho wecms-web?

## Trả lời
Đã xác nhận: không có dependency SpringDoc/OpenAPI. Contract frontend-backend chỉ được duy trì bằng kỷ luật full-stack thủ công.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: build.gradle
* Class: n/a
* Method: n/a
* Line(s): không có dependency springdoc/openapi

## Vì sao quan trọng
Một contract chỉ enforce bằng kỷ luật con người để drift DTO/type lọt qua không bị phát hiện tới khi runtime.

## Hành động đề xuất
Thêm SpringDoc và generate TypeScript type cho frontend (đồng thời xử lý PR-055).

---

# PR-033

## Câu hỏi từ team Việt Nam
Báo cáo STUB: /reports/daily-cash (summary rỗng), /reports/daily-financial-hs (hardcode), /transfers/own-accounts (no execution). Những cái này có nằm trong MVP không? Khách hàng có đang xem nhầm là "thật" không?

## Trả lời
Đã xác nhận là stub. Summary theo currency của daily-cash trả về một list rỗng (bản thân ledger tài khoản là thật); báo cáo daily-financial-HS trả về một dataset cố định hardcode; và own-account transfer có một list tài khoản read-only không có endpoint thực thi. Các màn này có thể render số trông như thật nhưng rỗng hoặc cố định.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P1 (P0 có điều kiện — trở thành P0 chỉ khi bất kỳ màn nào trong số này nằm trong MVP go-live)

## Bằng chứng
* File: domain/banking/service/ReportService.java ; domain/banking/controller/TransferController.java
* Class: ReportService ; TransferController
* Method: dailyCash ; dailyFinancialHs ; (own-accounts list)
* Line(s): ReportService.java:97 (summary rỗng), 440-456 (row HS hardcode); TransferController.java:24-25 (thực thi cố ý chưa implement)

## Vì sao quan trọng
Một khách hàng có thể ra quyết định trên dữ liệu rỗng hoặc cố định mà tin là thật. Severity phụ thuộc hoàn toàn vào việc có thuộc MVP: nếu các màn này ship lúc go-live, đây là vấn đề đúng-đắn P0; nếu bị loại, đây là mục dọn-dẹp/gắn-nhãn P1.

## Hành động đề xuất
PO xác nhận các màn này có trong MVP go-live không. Nếu có, coi là P0 và hoàn thiện hoặc gỡ; nếu không, đảm bảo UI gắn nhãn rõ ràng các màn stub là không-thật cho tới khi hoàn thiện.

---

# PR-034

## Câu hỏi từ team Việt Nam
e-Invoice lọc status còn ở client (IR-4, chưa port wb_get_invoice_status). Có đúng nghiệp vụ không, hay sai filter?

## Trả lời
Đã xác nhận: status hóa đơn được trả về raw và lọc ở frontend; hàm resolve-status legacy chưa được port (IR-4 defer). Phân trang và đếm theo status do đó có thể không chính xác.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P1

## Bằng chứng
* File: domain/einvoice/service/EInvoiceService.java
* Class: EInvoiceService
* Method: toRow
* Line(s): 327-328 (trả status raw), 51-53 (ghi chú defer IR-4)

## Vì sao quan trọng
Với status chỉ lọc ở frontend, phân trang và đếm phía server theo status có thể sai.

## Hành động đề xuất
Quyết định việc lọc/đếm theo status có cần ở phía server không; nếu có, port logic resolve-status legacy.

---

# PR-035

## Câu hỏi từ team Việt Nam
Operation-status inquire-dialog Save là stub (chưa có endpoint upsert). FE đang xử lý ra sao?

## Trả lời
Giải quyết một phần. Một endpoint create/persist cho operation status tồn tại và lưu vào database. Việc hành động Save của inquire-dialog trên frontend có được nối tới endpoint này hay không nên được xác nhận; phần persistence backend không phải stub.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: domain/operation/controller/OperationStatusController.java ; domain/operation/service/OperationStatusService.java
* Class: OperationStatusController ; OperationStatusService
* Method: create ; create (repo.save)
* Line(s): OperationStatusController.java:100-104 (POST create); OperationStatusService.java:405 (repo.save)

## Vì sao quan trọng
Nếu Save của inquire-dialog không nối tới endpoint persist, user có thể tin dữ liệu đã lưu trong khi chưa.

## Hành động đề xuất
Xác nhận Save của inquire-dialog frontend được nối tới endpoint persist.

---

# PR-036

## Câu hỏi từ team Việt Nam
e-Invoice resolveDocType: code thực tế map EXPENSE → 'B', CLAUDE.md ghi 'E'. Đâu mới đúng nghiệp vụ? Nếu sai, Bill vs Expense đang query nhầm DOC_TYPE → sai dữ liệu hóa đơn.

## Trả lời
ĐÃ XỬ LÝ. Người rà soát đã xác định đúng một mâu thuẫn code/tài-liệu (code map một kiểu, một ghi chú tài liệu nói kiểu khác) và đặt câu hỏi đúng đắn là cái nào là chính thống — đó là việc đáng để nêu. Verify source hiện tại cho thấy code runtime map EXPENSE/BILL → DOC_TYPE 'B', khớp hành vi legacy: màn e-Invoice Sales/Expense legacy coi DOC_TYPE 'B' là bảng Bill/Expense và validate rằng loại chỉ là INVOICE ('I') hoặc BILL ('B'). Giá trị enum legacy 'E' thuộc một loại chứng từ khác ("Expense" theo nghĩa kế toán) mà màn này không dùng. Vậy code runtime đúng; ghi chú tài liệu lỗi thời là nguồn gây nhầm, và nó (không phải code) cần được sửa. Không có dữ liệu nào bị query nhầm DOC_TYPE.

## Trạng thái hiện tại
ĐÃ XỬ LÝ

## Mức rủi ro
N/A (trước là P0)

## Bằng chứng
* File: domain/einvoice/service/EInvoiceService.java ; WECMS DEV/.../einv_0001_001_r001_act.jsp ; WECMS DEV/.../enums/BusaDocType.java
* Class: EInvoiceService ; (action legacy) ; BusaDocType
* Method: resolveDocType ; (switch validateInput/DOC_TYPE legacy) ; (giá trị enum)
* Line(s): EInvoiceService.java:205-221 (EXPENSE/BILL→'B'); einv_0001_001_r001_act.jsp:270-284 (DOC_TYPE 'B' = table2 Bill/Expense; chỉ chấp nhận INVOICE+BILL); BusaDocType.java:7-10 (INVOICE 'I', BILL 'B', EXPENSE 'E', PAYROLLBOOK 'S')

> **Lưu ý kiểm chứng:** các trích dẫn `WECMS DEV/...` (JSP + enum legacy) trỏ tới hệ JEX gốc — cây này KHÔNG có trong workspace `wecms-api` hiện tại, nên các line-ref legacy cần người có nguồn JEX xác nhận. Phần `EInvoiceService.java` thì verify được trong repo.

## Vì sao quan trọng
Màn Expense query đúng DOC_TYPE. Câu hỏi của người rà soát là hợp lệ và câu trả lời đáng yên tâm: hành vi runtime đúng; chỉ một ghi chú tài liệu lỗi thời cần cập nhật.

## Hành động đề xuất
Chỉ tài liệu: sửa ghi chú lỗi thời "EXPENSE→'E'" trong feature note nội bộ để khớp hành vi runtime đã verify. Không đổi code.

---

# PR-037

## Câu hỏi từ team Việt Nam
Smart Organizer doc vẫn ghi "discovery scaffold" nhưng code đã implement đầy đủ — tài liệu stale, cần đồng bộ.

## Trả lời
ĐÃ XỬ LÝ. Smart Organizer đã implement đầy đủ — endpoint eligible-list và confirm, logic service đầy đủ, native query, và resolve master-data. Câu chữ "discovery scaffold" là tài liệu lỗi thời, không phải trạng thái thật.

## Trạng thái hiện tại
ĐÃ XỬ LÝ

## Mức rủi ro
P2

## Bằng chứng
* File: domain/banking/smartorganizer/controller/SmartOrganizerController.java ; service/SmartOrganizerService.java
* Class: SmartOrganizerController ; SmartOrganizerService
* Method: listEligible ; confirm
* Line(s): SmartOrganizerController.java:41-63 (endpoint); SmartOrganizerService.java:106-120 (query + tra cứu master-data)

## Vì sao quan trọng
Tài liệu lỗi thời khiến người đọc đánh giá sai trạng thái thật của feature.

## Hành động đề xuất
Chỉ tài liệu: cập nhật feature doc Smart Organizer thành "đã implement".

---

# PR-038

## Câu hỏi từ team Việt Nam
/transfers/history deprecated nhưng vẫn còn — FE đã chuyển sang /internal-transfer/transfer-history chưa? Khi nào gỡ?

## Trả lời
ĐÃ XỬ LÝ. Endpoint deprecated `/api/v1/banking/transfers/history` (cùng DTO/service của nó) đã bị xóa ngày 2026-06-07 sau khi frontend cutover. Chỉ endpoint hiện tại `/api/v1/banking/internal-transfer/transfer-history` còn lại.

## Trạng thái hiện tại
ĐÃ XỬ LÝ

## Mức rủi ro
P2

## Bằng chứng
* File: domain/banking/transferhistory/controller/TransferHistoryController.java ; domain/banking/controller/TransferController.java
* Class: TransferHistoryController ; TransferController
* Method: (transfer-history) ; (tài liệu cấp class về việc gỡ)
* Line(s): TransferHistoryController.java:28-29 (endpoint duy nhất; cái cũ đã xóa); TransferController.java:16-19 (ghi chú gỡ bỏ)

## Vì sao quan trọng
Hai endpoint cùng chức năng mời gọi việc gọi nhầm cái (legacy); bản trùng nay đã không còn.

## Hành động đề xuất
Không.

---

# PR-039

## Câu hỏi từ team Việt Nam
Coverage hiện tại bao nhiêu? Các luồng tiền thật (dispatch, approval, match) có integration test không hay chỉ Mockito unit?

## Trả lời
Khoảng trống được xác nhận: độ phủ integration-test trên các luồng tiền thật (dispatch, approval, matching) không xác nhận được; các test tự động chủ yếu ở cấp unit có mock. Độ phủ các luồng tiền thực tế là chưa rõ.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P0

## Bằng chứng
* File: src/test/java (cây test)
* Class: (unit test dựa trên Mockito)
* Method: n/a
* Line(s): không có integration test luồng tiền

## Vì sao quan trọng
Độ phủ chưa rõ của các luồng tiền thật là rủi ro cao cho một hệ chuyển tiền.

## Hành động đề xuất
Thêm integration test cho dispatch, approval, và matching trước khi đưa tiền thật vào.

---

# PR-040

## Câu hỏi từ team Việt Nam
Không có Testcontainers → test chạy trên H2/mock, nhưng prod là PostgreSQL + native query + @Formula + DB function. Test có bắt được lỗi DB-specific không?

## Trả lời
Đã xác nhận: không có dependency Testcontainers. Test chạy với mock/in-memory, trong khi production dùng PostgreSQL với native query, cột tính toán, và DB function, nên một lớp bug chỉ-có-trên-PostgreSQL vô hình với test.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P1

## Bằng chứng
* File: build.gradle
* Class: n/a
* Method: n/a
* Line(s): không có dependency testcontainers

## Vì sao quan trọng
Mock không thể chạy hành vi đặc thù của database, nên các defect chỉ-có-trên-PostgreSQL lọt lưới.

## Hành động đề xuất
Thêm Testcontainers (PostgreSQL) cho các feature dùng native-query / cột-tính-toán / DB-function.

---

# PR-041

## Câu hỏi từ team Việt Nam
Có E2E test xuyên FE↔BE không? Có test contract (FE wire type ↔ DTO)?

## Trả lời
Đã xác nhận: không có bằng chứng về test end-to-end hay contract frontend-backend. Drift contract chỉ lộ ra ở runtime.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: src/test/java (cây test)
* Class: n/a
* Method: n/a
* Line(s): không có bằng chứng test E2E / contract

## Vì sao quan trọng
Không có test contract, drift giữa wire-type và DTO chỉ vỡ ở runtime.

## Hành động đề xuất
Thêm test contract (đi kèm PR-032/PR-055).

---

# PR-042

## Câu hỏi từ team Việt Nam
Test cho i18n (4 locale), cho multi-tenant isolation (đảm bảo không rò biz_key) có không?

## Trả lời
Đã xác nhận: không có bằng chứng về test đa ngôn ngữ hay isolation multi-tenant. Rủi ro rò rỉ cross-tenant không được test phủ.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: src/test/java (cây test)
* Class: n/a
* Method: n/a
* Line(s): không có bằng chứng test i18n / tenant-isolation

## Vì sao quan trọng
Thiếu test isolation nghĩa là một rò rỉ dữ liệu cross-tenant (BIZ_KEY) có thể lọt lưới.

## Hành động đề xuất
Thêm test tenant-isolation đảm bảo không rò BIZ_KEY, cộng độ phủ i18n cho các locale được hỗ trợ.

---

# PR-043

## Câu hỏi từ team Việt Nam
CLAUDE.md nói "local-only, no DEV/UAT/PROD deploy". Vậy kế hoạch lên production thật là gì? Hạ tầng (k8s/VM), CI/CD, secret management?

## Trả lời
Đã xác nhận: dự án chỉ local/phát-triển không có môi trường production, pipeline CI/CD, hay cách tiếp cận quản lý secret được định nghĩa. Không có đường hardened để chạy cho khách hàng thật.

## Trạng thái hiện tại
CẦN DEVOPS XÁC NHẬN

## Mức rủi ro
P0

## Bằng chứng
* File: (phạm vi dự án / application profile)
* Class: n/a
* Method: n/a
* Line(s): profile dev/prod reserved/chưa dùng

## Vì sao quan trọng
Một PoC R&D local không có môi trường ngoài laptop thì không có đường hardened, được định nghĩa, để lên production.

## Hành động đề xuất
PO/DevOps định nghĩa hạ tầng đích (k8s/VM), CI/CD, và quản lý secret.

---

# PR-044

## Câu hỏi từ team Việt Nam
Single instance hay cluster? (ảnh hưởng tới session in-memory scraping + key MAX+1).

## Trả lời
Đã xác nhận: topology triển khai (single instance vs cluster) chưa quyết. Quyết định này chi phối các fix cho session scraping in-memory (PR-015) và các vấn đề concurrency/cấp-khóa (PR-021, PR-029).

## Trạng thái hiện tại
CẦN DEVOPS XÁC NHẬN

## Mức rủi ro
P1

## Bằng chứng
* File: (cấu hình deployment)
* Class: n/a
* Method: n/a
* Line(s): topology chưa quyết

## Vì sao quan trọng
PR-015, PR-021, và PR-029 không thể chọn hướng fix tới khi biết topology.

## Hành động đề xuất
PO/DevOps quyết single-instance vs cluster; điều này gỡ chặn các fix concurrency/session.

---

# PR-045

## Câu hỏi từ team Việt Nam
Backup/restore DB, DR plan, RPO/RTO cho hệ thống tài chính?

## Trả lời
Đã xác nhận: không có quy trình backup/restore, kế hoạch disaster-recovery, hay mục tiêu RPO/RTO được định nghĩa.

## Trạng thái hiện tại
CẦN DEVOPS XÁC NHẬN

## Mức rủi ro
P2

## Bằng chứng
* File: (kế hoạch vận hành)
* Class: n/a
* Method: n/a
* Line(s): không có DR plan

## Vì sao quan trọng
Không có mục tiêu DR, mất data không có đường khôi phục đảm bảo.

## Hành động đề xuất
DevOps định nghĩa backup/restore, DR plan, và RPO/RTO cho hệ tài chính.

---

# PR-046

## Câu hỏi từ team Việt Nam
File storage (logo, attachment, Excel batch) đang ghi local filesystem (C:/WECMS PROJECT/...). Production dùng gì (S3/NFS)? Multi-instance share thế nào?

## Trả lời
Đã xác nhận: upload/export file ghi vào local filesystem qua một storage root cấu hình được mặc định là đường dẫn local. Với nhiều hơn một instance, file ghi bởi một instance không thấy được bởi instance khác.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: domain/payment/internaltransfer/service/InternalTransferStorageProperties.java (và các class storage-properties tương tự)
* Class: InternalTransferStorageProperties ; OverseasPaymentStorageProperties
* Method: (binding storage-root)
* Line(s): `wecms.*.storage-root` mặc định đường dẫn local

## Vì sao quan trọng
File ghi bởi một instance vô hình với instance khác, nên upload/download vỡ với nhiều hơn một pod.

## Hành động đề xuất
Chuyển file storage sang shared storage (object store / NFS) cho deploy multi-instance.

---

# PR-047

## Câu hỏi từ team Việt Nam
Export XLSX cap 50.000 row, dùng POI SXSSF (streaming, tốt). Nhưng update operation-status, scraping sync — có giới hạn thời gian/timeout request không (đồng bộ, vài giây/credential)?

## Trả lời
Lo ngại được xác nhận: các thao tác đồng bộ dài (update operation-status, scraping sync) không có cap timeout request được xác nhận và có thể đụng timeout gateway/load-balancer hoặc giữ connection mở. (Cap streaming 50.000 row của export XLSX được ghi nhận là tốt.)

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: domain/operation (update) ; domain/banking/nxisas (sync)
* Class: (service update operation-status) ; (service scraping sync)
* Method: update ; sync
* Line(s): các call đồng bộ dài; không verify được cap timeout tường minh

## Vì sao quan trọng
Request đồng bộ dài có thể đụng timeout gateway/LB và treo connection.

## Hành động đề xuất
Thêm timeout request; cân nhắc xử lý bất đồng bộ cho các thao tác dài.

---

# PR-048

## Câu hỏi từ team Việt Nam
Số tenant dự kiến? Số user/tenant? Số giao dịch/ngày? → để định cỡ index + connection pool.

## Trả lời
Không có số liệu tải (số tenant, user mỗi tenant, giao dịch mỗi ngày) được định nghĩa. Hạ tầng và index không thể định cỡ nếu thiếu chúng. Đây là input nghiệp vụ/PO.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P2

## Bằng chứng
* File: n/a
* Class: n/a
* Method: n/a
* Line(s): không có số liệu tải

## Vì sao quan trọng
Không có số liệu tải, không thể định cỡ hạ tầng và index.

## Hành động đề xuất
PO cung cấp volume tenant/user/giao-dịch dự kiến.

---

# PR-049

## Câu hỏi từ team Việt Nam
Có trace_id (tốt) nhưng có centralized logging/APM (ELK, Grafana) không? Khi user báo lỗi với trace_id, ops grep ở đâu?

## Trả lời
Đã xác nhận: một định danh trace request được sinh và trả trong error envelope và response header, nhưng không có stack logging/APM tập trung để tìm nó ở production.

## Trạng thái hiện tại
CẦN DEVOPS XÁC NHẬN

## Mức rủi ro
P1

## Bằng chứng
* File: common/exception/TraceIdFilter.java ; application.yml (pattern MDC)
* Class: TraceIdFilter
* Method: (sinh trace id + MDC)
* Line(s): trace id có mặt và được log; không có stack log/APM tập trung

## Vì sao quan trọng
Không có log tập trung, một trace_id được báo cáo khó tra ở production.

## Hành động đề xuất
DevOps dựng logging/APM tập trung (vd ELK/Grafana).

---

# PR-050

## Câu hỏi từ team Việt Nam
Có alerting khi dispatch fail, khi Coocon down, khi tỷ giá không fetch được không?

## Trả lời
Đã xác nhận: không có alerting cho lỗi dispatch, mất gateway, hay lỗi fetch FX. Ops sẽ biết về vấn đề từ user, không phải từ monitor.

## Trạng thái hiện tại
CẦN DEVOPS XÁC NHẬN

## Mức rủi ro
P2

## Bằng chứng
* File: (stack vận hành)
* Class: n/a
* Method: n/a
* Line(s): không có alerting

## Vì sao quan trọng
Không có alert, vận hành biết về lỗi từ user bực bội thay vì từ monitoring.

## Hành động đề xuất
DevOps thêm alerting vận hành về lỗi dispatch, gateway down, và lỗi fetch FX.

---

# PR-051

## Câu hỏi từ team Việt Nam
Audit log (AUDIT_LOGS_INFM) "không bao giờ throw, nuốt lỗi WARN" — vậy nếu audit fail âm thầm, có monitor không? Với compliance tài chính, mất audit là rủi ro pháp lý.

## Trả lời
Đã xác nhận: writer audit-log chạy trong transaction riêng (REQUIRES_NEW), bắt mọi runtime exception, log ở WARN, và không bao giờ re-throw — một đánh đổi cố ý để một audit fail không rollback business write. Hệ quả là một audit row bị bỏ sót là âm thầm trừ khi log cấp WARN được giám sát.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: common/auditlog/service/AuditLogService.java
* Class: AuditLogService
* Method: append
* Line(s): 119 ("never throws"), 125 (REQUIRES_NEW), 137-143 (catch → WARN → nuốt)

## Vì sao quan trọng
Một audit row bị mất âm thầm là một khoảng trống compliance cho hệ tài chính nếu không được giám sát.

## Hành động đề xuất
Thêm monitoring/alerting trên log WARN của audit-append để phát hiện mất audit âm thầm (phụ thuộc PR-049/PR-050).

---

# PR-052

## Câu hỏi từ team Việt Nam
Lưu credential ngân hàng + scrape thay user → có đồng ý pháp lý của khách hàng và tuân thủ quy định ngân hàng VN/State Bank không?

## Trả lời
Phơi nhiễm được xác nhận: credential ngân hàng được lưu mã hóa (AES-GCM) và hệ thống scrape thay user — một hoạt động bị quản lý ở Việt Nam. Đồng ý của khách hàng và ký duyệt tuân thủ State Bank / quy định là vấn đề pháp lý/nghiệp vụ, không phải code, và phải được xác nhận.

## Trạng thái hiện tại
CẦN LEGAL XÁC NHẬN

## Mức rủi ro
P1

## Bằng chứng
* File: domain/setting/logincredentials ; (bank certificate crypto service)
* Class: LoginCredentialsService ; BankCertCryptoService
* Method: (store / encrypt)
* Line(s): credential lưu mã hóa; scraping hành động thay user

## Vì sao quan trọng
Lưu credential và scrape thay user bị quản lý ở Việt Nam; không có đồng ý và ký duyệt tuân thủ thì đây là phơi nhiễm pháp lý.

## Hành động đề xuất
Legal/PO lấy đồng ý của khách hàng và ký duyệt tuân thủ State Bank / quy định trước production.

---

# PR-053

## Câu hỏi từ team Việt Nam
PII (tên, SĐT, tax ID, địa chỉ) — có yêu cầu data retention / quyền xóa không? Soft-delete hiện giữ data mãi.

## Trả lời
Đã xác nhận: soft-delete (DEL_YN) giữ dữ liệu cá nhân vô thời hạn; không có quy tắc retention hay quyền-xóa được định nghĩa. Việc này có vi phạm quy định PII hay không là câu hỏi pháp lý.

## Trạng thái hiện tại
CẦN LEGAL XÁC NHẬN

## Mức rủi ro
P2

## Bằng chứng
* File: (các entity dùng soft-delete DEL_YN xuyên các domain)
* Class: nhiều entity
* Method: soft-delete
* Line(s): row được giữ sau khi xóa

## Vì sao quan trọng
Giữ PII mãi có thể vi phạm yêu cầu quyền-xóa / data-retention.

## Hành động đề xuất
Legal/PO định nghĩa retention PII và cách xử lý quyền-xóa.

---

# PR-054

## Câu hỏi từ team Việt Nam
e-Invoice/GDT có ràng buộc lưu trữ theo luật thuế VN (vd 10 năm) không?

## Trả lời
Không có thời hạn retention e-Invoice/GDT được định nghĩa. Luật thuế Việt Nam có thể áp một mức retention tối thiểu; điều này phải được xác nhận về mặt pháp lý.

## Trạng thái hiện tại
CẦN LEGAL XÁC NHẬN

## Mức rủi ro
P2

## Bằng chứng
* File: n/a (không có chính sách retention)
* Class: n/a
* Method: n/a
* Line(s): n/a

## Vì sao quan trọng
Mức retention tối thiểu không rõ theo luật thuế là một rủi ro compliance.

## Hành động đề xuất
Legal định nghĩa thời hạn retention tối thiểu cho e-Invoice/GDT theo luật thuế VN.

---

# PR-055

## Câu hỏi từ team Việt Nam
Quy tắc "full-stack ownership — DTO đổi thì wire type FE đổi cùng MR". Có cơ chế tự động kiểm tra drift không, hay thủ công? (gợi ý: generate TS type từ OpenAPI).

## Trả lời
Đã xác nhận: tính nhất quán contract chỉ được enforce bằng quy tắc full-stack-ownership thủ công; không có phát hiện drift tự động.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: (quy tắc dự án, không có tooling)
* Class: n/a
* Method: n/a
* Line(s): không có OpenAPI/sinh-type (xem PR-032)

## Vì sao quan trọng
Enforce chỉ thủ công nghĩa là drift contract không được phát hiện tự động.

## Hành động đề xuất
Generate type frontend từ một API spec (OpenAPI) để phát hiện drift tự động (đi kèm PR-032).

---

# PR-056

## Câu hỏi từ team Việt Nam
FE static-UI dùng mockData; làm sao biết feature nào đã thực sự wired vs còn mock? (CLAUDE.md bảng "frontend not verified" khá nhiều).

## Trả lời
Đã xác nhận: nhiều feature đã có backend nhưng frontend chưa verify, và trạng thái wired-vs-mock không được track ở một chỗ. Trạng thái release thật do đó khó biết.

## Trạng thái hiện tại
CÒN MỞ

## Mức rủi ro
P2

## Bằng chứng
* File: (bản ghi trạng thái feature)
* Class: n/a
* Method: n/a
* Line(s): nhiều mục "frontend not verified"

## Vì sao quan trọng
Không có ma trận wired-vs-mock rõ ràng, trạng thái feature thật cho lập kế hoạch release là không chắc chắn.

## Hành động đề xuất
Lập một ma trận wired-vs-mock theo từng feature cho lập kế hoạch release (liên quan PR-002).

---

# PR-057

## Câu hỏi từ team Việt Nam
Ai là PO/owner nghiệp vụ để chốt các OQ (open questions) còn treo trong doc (chart-of-accounts OQ-1/OQ-3, contacts OQ, e-invoice IR-4…)?

## Trả lời
Một số open question không có chủ sở hữu để đóng. Đây là vấn đề quy trình/sở hữu cho PO.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P2

## Bằng chứng
* File: (bản ghi open-question trong feature doc)
* Class: n/a
* Method: n/a
* Line(s): OQ mở hơn một sprint

## Vì sao quan trọng
Open question không có chủ chặn implementation và kéo dài qua nhiều sprint.

## Hành động đề xuất
PO gán một chủ sở hữu để đóng từng open question còn lại.

---

# PR-058

## Câu hỏi từ team Việt Nam
Cadence review code/security? Có người am hiểu nghiệp vụ JEX gốc để verify parity không?

## Trả lời
Không có người verify parity được chỉ định (người am hiểu nghiệp vụ JEX legacy), và cadence review không được định nghĩa. Sai lệch nghiệp vụ với hệ legacy có thể lọt lưới.

## Trạng thái hiện tại
CẦN PO QUYẾT ĐỊNH

## Mức rủi ro
P2

## Bằng chứng
* File: n/a (quy trình/sở hữu)
* Class: n/a
* Method: n/a
* Line(s): không có người verify parity được chỉ định

## Vì sao quan trọng
Không có người verify parity, sai lệch với nghiệp vụ của hệ legacy có thể không bị phát hiện.

## Hành động đề xuất
PO gán một người verify JEX-parity và định nghĩa một cadence review code/security.

---

## Khuyến nghị cuối cùng (Final Recommendation)

**Sẵn sàng tiếp tục SIT.** Backend phù hợp cho System Integration Testing đang diễn ra với database phát triển. Phân quyền menu nay đã enforce trên các endpoint nghiệp vụ (PR-007, xử lý một phần — còn sót controller chưa annotate), và defect loại-chứng-từ e-Invoice nghi ngờ trước đây đã được xác nhận là đúng (PR-036). Không tích hợp ngân hàng thật nào được thực thi trong SIT.

**Chưa sẵn sàng cho Production.** Engine dispatch trong refactor đã tồn tại nhưng các leg live còn skeleton/mock/chưa-cấp-cấu-hình và không có đối soát thanh toán (PR-019); kiểm soát concurrency và idempotency chưa đầy đủ và đường callback/result dispatch chưa được đánh giá (PR-021, PR-020, PR-029); thu hồi token (PR-006) và auto-lock login (PR-008) chưa implement; integration test luồng tiền vắng mặt (PR-039); và không có môi trường production hay tooling vận hành được định nghĩa (PR-043, PR-044, PR-045, PR-049, PR-050).

**Điều kiện cần trước Production:**

1. **Đường tiền-thật** — implement và đối soát leg gửi-bank (PR-019, PR-013, PR-014).
2. **Concurrency & idempotency** — thêm optimistic locking / sinh khóa an toàn và xác nhận mô hình idempotency (PR-021, PR-020, PR-029).
3. **Hardening xác thực** — thu hồi token phía server (PR-006) và auto-lock login / chống brute-force (PR-008).
4. **Testing** — integration test trên các luồng tiền và hành vi đặc thù database (PR-039, PR-040), cộng test contract và tenant-isolation (PR-041, PR-042).
5. **Sẵn sàng triển khai** — môi trường, topology, CI/CD, quản lý secret, logging tập trung, alerting, backup/DR được định nghĩa (PR-043, PR-044, PR-012, PR-049, PR-050, PR-045).
6. **Ký duyệt pháp lý/quy định** — đồng ý và tuân thủ cho lưu credential và scraping (PR-052), cộng quy tắc data-retention (PR-028, PR-053, PR-054).
7. **Xác nhận phạm vi** — Definition of Done và phạm vi MVP (PR-002), và xác nhận mọi màn stub (PR-033) bị loại khỏi release.

Khi các mục 1–7 được xử lý hoặc chấp nhận chính thức, nên tiến hành một lần rà soát lại sẵn-sàng-Production.

**Đã xử lý kể từ bản rà soát gốc (không còn là blocker):** PR-036 (ánh xạ e-Invoice xác nhận đúng), PR-037 (Smart Organizer đã implement), PR-038 (endpoint deprecated đã xóa). **Tiến triển đáng kể (xử lý một phần):** PR-007 (phân quyền menu enforce trên endpoint nghiệp vụ; còn mở controller chưa annotate + mô hình auth endpoint callback).

---

*Hết tài liệu. Bản tiếng Anh gốc: [`review-and-question-answer-2026-06-05.en.md`](review-and-question-answer-2026-06-05.en.md).*

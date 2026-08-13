# Đối chiếu câu trả lời review với nhánh `sit` (2026-06-11)

> So khớp các câu trả lời trong [`review-and-question-answer-2026-06-05.en.md`](review-and-question-answer-2026-06-05.en.md) với **code thực tế trên nhánh `sit`**. Cơ sở mới nhất: commit `ff955b2`, migration cao nhất `V248` (re-check 2026-06-11; lần đầu ở `c4ce227` / `V237`).
>
> **Phát hiện cốt lõi (lần đầu, commit `c4ce227`, V237):** file trả lời được verify trên một nhánh **mới hơn**. Lúc đó `sit` chưa có phần auth/dispatch nên **5 mục lệch**: PR-007, PR-009, PR-019, PR-035, PR-038.
>
> **⟳ CẬP NHẬT SAU KHI `sit` UPDATE (commit `ff955b2`, migration `V248`, 2026-06-11):** đã chạy lại quy trình. **4/5 mục nay đã KHỚP** — PR-007, PR-009, PR-035, PR-038 (xem cột "Sau update"). **Chỉ còn PR-019 vẫn lệch**: "EFS dispatch engine" mà doc mô tả vẫn không tồn tại trên `sit`.
>
> Cột **Phù hợp**: ✅ phù hợp với `sit` · ⚠️ phù hợp về bản chất, lệch chi tiết nhỏ · ❌ lệch — mô tả code không có trên `sit`.

## Bảng đối chiếu

| PR | Nó đang nói cái gì (giải thích ngắn) | Câu trả lời (Status) | Đối chiếu nhánh `sit` | Phù hợp |
|---|---|---|---|---|
| PR-001 | Mục tiêu là thay hẳn JEX hay chạy song song? Giữ `wb_get_next_val` mâu thuẫn với "no coexistence" | PO DECISION | Câu hỏi chiến lược, không thể trả lời từ code; `LegacyFunctionRepository` có thật | ✅ |
| PR-002 | Chưa có Definition of Done; nhiều feature "BE xong, FE chưa verify" | PO DECISION | Đúng bản chất. Doc đếm 46 controller; `sit` (V248) có **44** | ⚠️ |
| PR-003 | Multi-company chỉ là fallback; có khách nào dùng thật không? | PO DECISION | `AuthService.login` đúng là 1-user-1-company + chooser fallback | ✅ |
| PR-004 | 14 mã PRDT_TYPE, chỉ model F/M/H; khi nào port phần còn lại? | PO DECISION | `PrdtType` đúng chỉ có F/M/H | ✅ |
| PR-005 | Có SLA/KPI mỗi module không? | PO DECISION | Không có cấu hình SLA trong code (đúng) | ✅ |
| PR-006 | JWT không revoke; user bị khoá vẫn dùng token tới khi hết hạn | OPEN (P0) | `AuthController.logout` đúng là stub; filter không check blacklist | ✅ |
| PR-007 | `@PreAuthorize` chỉ "advisory" → ai cũng gọi được endpoint admin | PARTIALLY RESOLVED | ⟳ **Sau update (V248): đã KHỚP** — `@EnableMethodSecurity` bật thật trong `SecurityConfig`, có `MenuAuthorizationGuard` + package `navigation` + `EndpointAuthorizationCoverageTest`. Còn **12/44** controller chưa gate (doc ghi 14/46) → đúng "partially resolved" | ✅ |
| PR-008 | Không có auto-lock 5 lần / chống brute-force | OPEN (P0) | `AuthService` ghi FAIL, không có counter (đúng) | ✅ |
| PR-009 | Các module payment có enforce quyền rút (W-grant) trên mọi đường không? | OPEN (P1) | ⟳ **Sau update: đã KHỚP** — `AccountAccessGuard` đã tồn tại trên `sit` (việc W-grant trên mọi module vẫn cần audit như doc nói) | ✅ |
| PR-010 | reveal trả mật khẩu plaintext — có audit/rate-limit/TLS? | SECURITY CONFIRM | `LoginCredentialsController` có `/reveal` + audit-logs (đúng); phần bảo vệ cần security xác nhận | ✅ |
| PR-011 | Khoá AES-GCM lưu/xoay vòng ở đâu? | SECURITY CONFIRM | Không có trong code (đúng), thuộc vận hành | ✅ |
| PR-012 | `WECMS_JWT_SECRET` quản lý qua vault hay env? | DEVOPS CONFIRM | Đọc từ env, fail-fast (đúng) | ✅ |
| PR-013 | Engine NXiSAS chạy ở browser, server "tin" kết quả — chống giả mạo? | OPEN (P0) | nxisas có thật; không có đối soát với sao kê ngân hàng (đúng) | ✅ |
| PR-014 | Coocon chết giữa chừng → DB có nhất quán? có reconcile? | OPEN (P0) | Không tìm thấy cơ chế reconcile (đúng) | ✅ |
| PR-015 | Session scraping lưu in-memory → mất khi restart/multi-instance | OPEN (P1) | `NxisasSessionRegistry` (ConcurrentHashMap) có thật | ✅ |
| PR-016 | Cấu hình/cấp credential Coocon ở đâu, ai cấp? | DEVOPS CONFIRM | Endpoint cấu hình qua application.yml (đúng); chủ sở hữu cần DevOps | ✅ |
| PR-017 | OSMC→GDT update re-poll có throttle/cap không? | OPEN (P2) | Không thấy cap (đúng) | ✅ |
| PR-018 | FX Shinhan down → có cảnh báo "dữ liệu cũ"? | OPEN (P2) | `ExchangeRateService` có fallback ngày gần nhất, không gắn nhãn stale (đúng) | ✅ |
| PR-019 | Đường dispatch đã nối ngân hàng thật chưa hay chỉ ghi bảng shadow? | OPEN (P0) | ⟳ **Sau update (V248): VẪN LỆCH** — Doc mô tả "EFS dispatch engine" (`RealtimeTransferService`, `MockEfsApiClient`, `EfsApiClient`, `BulkExecution/ReceptionResultController`, `ResultCheckService/Job`, `SftpClient`) nhưng **tất cả vẫn THIẾU** trên `sit`. `payment/dispatch/` chỉ có entity/repo + `TransferDispatchExecutor`/`DispatchKeyGenerator` → vẫn là ghi bảng shadow `TB_PAY_*` (PROC_STAT='9'), không có engine gửi bank như doc tả | ❌ |
| PR-020 | Double-click có gây chuyển tiền 2 lần? có idempotency key? | OPEN (P0) | Guard theo trạng thái (WBE2064/2062/2095) trong `InternalTransferService` có thật (đúng); doc tự nói chưa đánh giá đường callback `dispatch/` — đường đó trên `sit` không tồn tại nên không phát sinh mâu thuẫn | ✅ |
| PR-021 | Có optimistic lock (`@Version`)? key MAX+1 retry-once đủ chưa? | OPEN (P0) | `@Version` = **0** (đúng); `DispatchKeyGenerator`/`InternalTransferKeyGenerator` có thật. Lõi đúng (có nhắc đường callback không có trên `sit`) | ✅ |
| PR-022 | Quy tắc cutoff SWIFT/NAPAS lấy từ đâu, có lịch nghỉ lễ VN? | PO DECISION | `CutoffValidator` hardcode time-window, không có lịch lễ (đúng) | ✅ |
| PR-023 | Approver bị khoá giữa chừng thì luồng duyệt xử lý sao? | PO DECISION | Không có quy tắc (đúng) | ✅ |
| PR-024 | Vendor/Expense/Payroll là bản sao Internal Transfer — gộp không? | PO/Lead | 3 service gần như trùng (đúng) | ✅ |
| PR-025 | Lên UAT/PROD migrate dữ liệu thật ra sao? có ETL? | PO/Backend | Seed chỉ là local dev (đúng), chưa có ETL | ✅ |
| PR-026 | Lịch sử Flyway dài, nhiều revert — rebuild từ DB rỗng có sạch không? | OPEN (P1) | Doc ghi "V1…V243"; `sit` nay tới **V248** (228 file) và **KHÔNG còn trùng version** → rào cản trùng-số không còn. Lo ngại "rebuild sạch từ DB rỗng" vẫn hợp lý nhưng **chưa test trên `sit`** (doc cũng nói "chưa xác nhận, nên test") | ⚠️ |
| PR-027 | `ddl-auto: validate`, entity chỉ map tập con — có cột NOT NULL thiếu DEFAULT? | OPEN (P2) | Rủi ro hợp lý, chưa test (đúng) | ✅ |
| PR-028 | Bảng `*_HIS` dùng trigger nhưng refactor hoãn → mất audit trail? | LEGAL CONFIRM | Trigger hoãn (đúng), cần Legal xác nhận | ✅ |
| PR-029 | `wb_get_next_val` MAX+1 không atomic → đua key khi tải cao | OPEN (P1) | `LegacyFunctionRepository` có thật (đúng) | ✅ |
| PR-030 | Native query + LATERAL (smart-organizer) — đã test ở volume thật? | OPEN (P1) | `SmartOrganizerService` có thật, chưa benchmark (đúng) | ✅ |
| PR-031 | Một số filter chạy in-memory sau fetch — đẩy xuống DB không? | RESOLVED (account list) | ✅ Trên `sit` `AccountService.list` đã dùng `findFiltered` + `aggregateByCcy` (server-side) — khớp | ✅ |
| PR-032 | Không có SpringDoc/OpenAPI — FE dựa vào đâu cho contract? | OPEN (P2) | `build.gradle` không có springdoc (đúng) | ✅ |
| PR-033 | Báo cáo stub: daily-cash rỗng, daily-financial-hs hardcode, own-accounts không execute | OPEN (P1/P0) | `ReportService.dailyCash/dailyFinancialHs` có thật (đúng là stub) | ✅ |
| PR-034 | Filter trạng thái e-Invoice còn ở client (IR-4 chưa port) | OPEN (P1) | `EInvoiceService.toRow` trả status thô (đúng) | ✅ |
| PR-035 | Operation-status: nút Save ở dialog là stub (không có upsert) | OPEN (P2) | ⟳ **Sau update: đã KHỚP** — `OperationStatusController` đã có `@PostMapping("/operation-status")` create (persist thật, không còn stub) | ✅ |
| PR-036 | `resolveDocType`: code map EXPENSE→'B' nhưng CLAUDE.md ghi 'E' | RESOLVED | ✅ Trên `sit` code thật map `EXPENSE/BILL→'B'`, `SALES→'I'` — khớp; chỉ cần sửa doc | ✅ |
| PR-037 | Doc Smart Organizer còn ghi "discovery scaffold" dù code đã xong | RESOLVED | `SmartOrganizerController`/`Service` có thật, đã implement (đúng) | ✅ |
| PR-038 | `/transfers/history` deprecated nhưng còn — FE đã chuyển chưa, khi nào xoá? | RESOLVED (đã xoá 2026-06-07) | ⟳ **Sau update: đã KHỚP** — `TransferController` giờ chỉ còn `@GetMapping("/own-accounts")`, `/history` đã xoá | ✅ |
| PR-039 | Money flow (dispatch/approval/match) có integration test không? | OPEN (P0) | Không thấy integration test money-flow (đúng) | ✅ |
| PR-040 | Không có Testcontainers → test trên mock, bỏ sót bug riêng của PostgreSQL | OPEN (P1) | `build.gradle` không có testcontainers (đúng) | ✅ |
| PR-041 | Có E2E / contract test FE↔BE không? | OPEN (P2) | Không thấy (đúng) | ✅ |
| PR-042 | Có test i18n / cô lập multi-tenant (không rò biz_key)? | OPEN (P2) | Không thấy (đúng) | ✅ |
| PR-043 | "Local-only" → kế hoạch lên production (infra/CI-CD/secret)? | DEVOPS CONFIRM | Profile dev/prod đang reserved (đúng) | ✅ |
| PR-044 | Single-instance hay cluster? (ảnh hưởng session + MAX+1 key) | DEVOPS CONFIRM | Topology chưa quyết (đúng) | ✅ |
| PR-045 | Backup/restore, DR, RPO/RTO cho hệ tài chính? | DEVOPS CONFIRM | Không có DR plan (đúng) | ✅ |
| PR-046 | File ghi ra local filesystem — production dùng S3/NFS? chia sẻ giữa pod? | OPEN (P2) | Cấu hình `wecms.*.storage-root` mặc định local (đúng) | ✅ |
| PR-047 | Update operation-status / scraping sync có giới hạn timeout request? | OPEN (P2) | Lời gọi đồng bộ dài, chưa thấy cap timeout (đúng) | ✅ |
| PR-048 | Số tenant / user / giao dịch/ngày dự kiến? (để sizing) | PO DECISION | Không có số liệu tải (đúng) | ✅ |
| PR-049 | Có trace_id nhưng có log tập trung/APM không? | DEVOPS CONFIRM | `TraceIdFilter` có thật; chưa có ELK/APM (đúng) | ✅ |
| PR-050 | Có alert khi dispatch fail / Coocon down / FX lỗi không? | DEVOPS CONFIRM | Không có alerting (đúng) | ✅ |
| PR-051 | Audit log "never throws, swallow WARN" — fail thầm lặng có ai giám sát? | OPEN (P2) | `AuditLogService.append` REQUIRES_NEW, nuốt lỗi → WARN (đúng) | ✅ |
| PR-052 | Lưu credential + scraping thay user — có đồng ý pháp lý / tuân thủ NHNN? | LEGAL CONFIRM | Credential mã hoá AES-GCM, scraping thay user (đúng); cần Legal | ✅ |
| PR-053 | PII soft-delete giữ mãi — có quy định xoá/giữ dữ liệu? | LEGAL CONFIRM | Soft-delete giữ dòng (đúng) | ✅ |
| PR-054 | e-Invoice/GDT có ràng buộc lưu trữ theo luật thuế VN? | LEGAL CONFIRM | Không định nghĩa retention (đúng) | ✅ |
| PR-055 | Quy tắc "đổi DTO ⇒ đổi FE type cùng MR" — có check drift tự động? | OPEN (P2) | Chỉ enforce thủ công (đúng) | ✅ |
| PR-056 | Làm sao biết feature nào đã nối FE thật vs còn mock? | OPEN (P2) | Nhiều mục "frontend not verified" (đúng) | ✅ |
| PR-057 | Ai là PO đóng các open-question còn treo trong docs? | PO DECISION | Vấn đề quy trình (đúng) | ✅ |
| PR-058 | Có ai nắm logic JEX để verify parity? cadence review? | PO DECISION | Không có người verify parity (đúng) | ✅ |

## PR-019 — kiểm tra sâu (2026-06-11, commit `ff955b2`)

Đã đọc trực tiếp các service dispatch trên `sit`. Kết luận đường đi của tiền:

- `InternalTransferDispatchService.dispatch()` chỉ: validate approval + cut-off → gọi executor **ghi DB** → stamp `TRNF_YN='Y'` + audit. **Không gọi ra bank.**
- `InternalTransferDispatchExecutor` và `TransferDispatchExecutor` (dùng cho vendor/expense/payroll) **chỉ `repository.save(...)`** vào `TB_PAY_TARGET` / `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA`.
- **Không có HTTP client nào** (`RestTemplate`/`WebClient`/`HttpClient`/`Feign`) trong `domain/payment`; **không có `@Scheduled`** job; **không có** EFS engine (`RealtimeTransferService`/`MockEfsApiClient`/callback/`ResultCheckJob`).
- Javadoc nói rõ (`InternalTransferDispatchService.java:59-61`): *"bank-network integration (NAPAS/CITAD/SWIFT callbacks flipping PROC_STAT) is handled by the live JEX dispatch daemon — refactor R&D is local-only."*

→ **Trên `sit`, refactor chỉ ghi bảng lệnh chi (`TB_PAY_*`) và GIAO việc gửi-ngân-hàng + lật `PROC_STAT` cho JEX daemon bên ngoài.** Bản build local không gắn daemon → **tiền không thực sự rời tài khoản.**

→ Vì vậy câu trả lời đúng cho PR-019 **trên nhánh này** là cách diễn đạt GỐC của reviewer ("chỉ ghi bảng shadow / hoãn-giao việc gửi bank"). Phần "đính chính" của tài liệu (mô tả EFS engine in-refactor) là của **nhánh khác**, áp lên `sit` là sai. *Lưu ý: kết luận rủi ro cuối cùng — "build hiện tại không thực hiện chuyển tiền thật, đường real-money chưa hoạt động" — vẫn ĐÚNG cho `sit`, chỉ khác cơ chế (delegate JEX daemon thay vì EFS skeleton).*

## Tóm tắt (⟳ đã re-check sau update `sit` → `ff955b2` / V248)

Tổng kết verdict: **55 ✅ · 2 ⚠️ · 1 ❌** (tổng 58).

- **55/58 mục ✅ phù hợp** với `sit`, gồm toàn bộ nhóm câu hỏi quyết định (PO/DevOps/Legal/Security) và các fact code kiểm được. **PR-007, PR-009, PR-020, PR-035, PR-038 — sau update đã KHỚP** (auth/`@EnableMethodSecurity`, `AccountAccessGuard`, guard double-submit, operation-create, xoá `/transfers/history`).
- **2 mục ⚠️ phù hợp về bản chất, chỉ lệch số liệu nhỏ:** PR-002 (doc 46 vs `sit` **44** controller), PR-026 (doc V243 vs `sit` **V248** — `sit` đã vượt, không còn trùng version).
- **1 mục VẪN LỆCH (❌):**
  - **PR-019** — Doc mô tả "EFS dispatch engine" (`RealtimeTransferService`, `MockEfsApiClient`, callback controllers, `ResultCheckService/Job`, `SftpClient`…) nhưng **các class này vẫn không có trên `sit`** kể cả sau update. `payment/dispatch/` trên `sit` chỉ có entity/repo + `TransferDispatchExecutor`/`DispatchKeyGenerator` → vẫn là ghi bảng shadow `TB_PAY_*` (`PROC_STAT='9'`). Engine mà doc tả nằm ở nhánh khác (chưa merge vào `sit`).

### Lịch sử kiểm
| Lần | Commit | Migration | Mục lệch |
|---|---|---|---|
| Lần 1 (sáng) | `c4ce227` | V237 | PR-007, 009, 019, 035, 038 (5 mục) |
| Lần 2 (sau update) | `ff955b2` | V248 | **chỉ còn PR-019** (1 mục) |

## Khuyến nghị

1. **Sau update `sit`, chỉ còn PR-019 cần xử lý.** Doc đang mô tả "EFS dispatch engine" của một nhánh khác chưa merge vào `sit` → cần xác nhận: engine đó sẽ merge vào `sit`, hay `sit` đi hướng "delegate cho JEX daemon"? (đây cũng chính là "conflict to flag" mà doc nêu ở PR-019). Trước khi chốt, trạng thái PR-019 trên `sit` đúng nghĩa vẫn là "chỉ ghi bảng shadow".
2. **Header tài liệu nên ghi commit hash** đã verify, không chỉ ngày, để tránh lệch.
3. **PR-026:** `sit` (V248) hiện **không còn trùng version migration**, nên rào cản trùng-số đã hết. Tuy vậy **rebuild sạch từ DB rỗng vẫn chưa được test trên `sit`** → nên chạy thử `flyway migrate` trên một DB trống để xác nhận (đúng như doc khuyến nghị). *(Ghi chú: lần rebuild thất bại vì trùng version trước đó là quan sát trên nhánh khác — `main`/V150 — không phải `sit`.)*

---

*Người đối chiếu: AI. Lần 1: commit `c4ce227` (V237). Lần 2 (re-check sau update): commit `ff955b2` (V248). Cùng ngày 2026-06-11.*

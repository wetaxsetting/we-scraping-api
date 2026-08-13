# WeCMS API — Catalog chi tiết per-endpoint

Bộ tài liệu chi tiết từng endpoint của `wecms-api`, tách theo domain module. Mỗi entry mô tả: **method+path · auth · chức năng · table đọc · table ghi/update · xử lý · gọi service/API ngoài**.

| File | Nội dung | Số endpoint |
|---|---|---|
| [01-auth.md](01-auth.md) | `domain/auth` — login, me, logout, change-password, user directory **+ `domain/navigation`** (cây menu) | 6 |
| [02-banking-core.md](02-banking-core.md) | `domain/banking` — Account Inquiry, Transaction, Match, Reports, Exchange Rate, Transfer | ~38 |
| [03-banking-submodules.md](03-banking-submodules.md) | Bank Credentials, NXISAS Scraping, Own Account Transfer (9), Smart Organizer, Sync/Transfer History | ~25 |
| [04-payment.md](04-payment.md) | Approval Box, **Card Transactions (28)**, Overseas, Internal/Vendor/Expense/Payroll, Blocked Chars | ~80 |
| [05-settings.md](05-settings.md) | 14 module cấu hình tenant (View Setting, Contacts, Chart of Accounts, Users, VAS, …) | ~70 |
| [06-addon-common.md](06-addon-common.md) | e-Invoice (6), Operation Status (+create), Country **+ Notification** | ~15 |
| [99-erd.md](99-erd.md) | Sơ đồ ERD quan hệ bảng theo cụm (auth / banking / payment / settings / e-invoice / navigation) | — |

> Tổng quan kiến trúc + sơ đồ hệ thống ở [../API_FUNCTION_CATALOG.md](../API_FUNCTION_CATALOG.md).

**Quy ước:** mọi route ngoài `/api/v1/auth/{login,logout}` + `/actuator/health` đều JWT-protected; **phân quyền đã BẬT** (`@EnableMethodSecurity` + `@menuAuthz.canAccess` + `AccountAccessGuard`, chặn → 403/`WBE1003`); tenant lấy từ `TenantContext` (không nhận `biz_key` từ wire); audit ghi `AUDIT_LOGS_INFM` qua `AuditLogService` (REQUIRES_NEW). Dựng 2026-06-05, **đối chiếu lại trên nhánh `sit` (`ff955b2` / V248) 2026-06-11.**

# WeCMS API — Catalog chi tiết per-endpoint


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

Bộ tài liệu chi tiết từng endpoint của `wecms-api`, tách theo domain module. Mỗi entry mô tả: **method+path · auth · chức năng · table đọc · table ghi/update · xử lý · gọi service/API ngoài**.

| File | Nội dung | Số endpoint |
|---|---|---|
| [auth.md](auth.md) | `domain/auth` — login, me, logout, change-password, user directory **+ `domain/navigation`** (cây menu) | 6 |
| [banking-core.md](banking-core.md) | `domain/banking` — Account Inquiry, Transaction, Match, Reports, Exchange Rate, Transfer | ~38 |
| [banking-submodules.md](banking-submodules.md) | Bank Credentials, NXISAS Scraping, Own Account Transfer (9), Smart Organizer, Sync/Transfer History | ~25 |
| [payment.md](payment.md) | Approval Box, **Card Transactions (28)**, Overseas, Internal/Vendor/Expense/Payroll, Blocked Chars | ~80 |
| [settings.md](settings.md) | 14 module cấu hình tenant (View Setting, Contacts, Chart of Accounts, Users, VAS, …) | ~70 |
| [addon-common.md](addon-common.md) | e-Invoice (6), Operation Status (+create), Country **+ Notification** | ~15 |
| [erd.md](erd.md) | Sơ đồ ERD quan hệ bảng theo cụm (auth / banking / payment / settings / e-invoice / navigation) | — |

> Tổng quan kiến trúc + sơ đồ hệ thống ở [../api-function-catalog.md](../api-function-catalog.md).

**Quy ước:** mọi route ngoài `/api/v1/auth/{login,logout}` + `/actuator/health` đều JWT-protected; **phân quyền đã BẬT** (`@EnableMethodSecurity` + `@menuAuthz.canAccess` + `AccountAccessGuard`, chặn → 403/`WBE1003`); tenant lấy từ `TenantContext` (không nhận `biz_key` từ wire); audit ghi `AUDIT_LOGS_INFM` qua `AuditLogService` (REQUIRES_NEW). Dựng 2026-06-05, **đối chiếu lại trên nhánh `sit` (`ff955b2` / V248) 2026-06-11.**

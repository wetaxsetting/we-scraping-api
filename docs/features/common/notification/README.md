# Common → notification (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

The **live** notification module is `domain/payment/notification/`, backed by the legacy
`BLTB_*` tables (`BLTB_NOTI_INFM` + 4 siblings). It powers the global header-bell popup —
list / unread-count / mark-one-read / mark-all-read — and the producer leg that creates
notification rows when an approval is requested / approved / rejected.

> **DEAD LEGACY — do NOT document as live.** The earlier refactor-only `domain/notification/`
> package + the `NOTIFICATION` table (Flyway **V238**) were **removed 2026-06-10** (a refactor-only
> table with no legacy backing — see memory `project_notification_module_removed.md`). Every doc in
> this folder describes the **BLTB-backed** implementation under `domain/payment/notification/`. The
> removed module is referenced only as migration history, never as the current state.

## Endpoint summary

All under `/api/v1/notifications`, JWT-authenticated, **no `@menuAuthz`** (global header surface;
allow-listed in `EndpointAuthorizationCoverageTest`). Tenant + recipient come from `TenantContext`,
never the request. Ports legacy `comm_0192_007` — **BLTB branch only** (General `TYPE='G'` not ported).

| Method | Path | Service call | Legacy WSVC |
|---|---|---|---|
| GET | `/api/v1/notifications?read_type={U\|A}&page&size&search` | `NotificationService.list` | `comm_0192_007_r001` |
| GET | `/api/v1/notifications/unread-count` | `NotificationService.unreadCount` | `comm_0192_007_r002` |
| PUT | `/api/v1/notifications/{notificationId}/read` | `NotificationService.markRead` | `comm_0192_007_u001` (single REC) |
| PUT | `/api/v1/notifications/read-all` | `NotificationService.markAllRead` | `comm_0192_007_u001` (full REC[]) |

`read_type`: `U` = Unread tab (`ACTV_YN='N'` filter), `A` = All (default; anything ≠ `U` ⇒ all).
Unknown / other-tenant id on mark-read ⇒ `WBE_NOTIFICATION_NOT_FOUND`. Producer leg =
`ApprovalNotificationService` (approval request / approve / reject write notification rows).

## Documents index

| Doc | Purpose |
|---|---|
| [business-flow.md](business-flow.md) | The four read/write flows end-to-end + the `ApprovalNotificationService` producer leg. |
| [functional-specification.md](functional-specification.md) | Behavioural contract: read_type tabs, search, paging, tenant/recipient scoping, idempotent mark-read, BLTB-only scope. |
| [database-mapping.md](database-mapping.md) | The 5 `BLTB_*` tables + entity↔column + PKs; the removed `NOTIFICATION` table is NOT used. |
| [interface-specification.md](interface-specification.md) | The 4 endpoints + params + `WBE_NOTIFICATION_NOT_FOUND` + the no-`@menuAuthz` rationale. |
| [migration-guide.md](migration-guide.md) | `comm_0192_007` family → Spring; DEAD LEGACY (removed `domain/notification` + `NOTIFICATION` V238); General `TYPE='G'` not ported. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | MATCH / DEAD LEGACY / PARTIAL / MISSING verdicts (incl. open decisions C1 General branch, C2 no-delete). |
| [source-code-index.md](source-code-index.md) | Verified `domain/payment/notification/` file map + frontend map + tests. |
| [test-scenarios.md](test-scenarios.md) | List tabs, unread badge, mark one/all, unknown-id → `WBE_NOTIFICATION_NOT_FOUND`, tenant isolation. |

## Related links

- Common module hub — [../README.md](../README.md)
- WEB twin (header bell + slide-over) — [../../../../../wecms-web/docs/features/common/notification/README.md](../../../../../wecms-web/docs/features/common/notification/README.md)
- Reference analysis — popup legacy-vs-refactor — [../../../audits/notification-popup-legacy-vs-refactor-analysis.md](../../../audits/legacy-parity/notification-popup-legacy-vs-refactor-analysis.md)
- Reference analysis — approval-detail popup parity — [../../../audits/notification-approval-detail-popup-parity-audit.md](../../../audits/legacy-parity/notification-approval-detail-popup-parity-audit.md)
- Common audit deliverables — [../../../audits/common-source-index.md](../../../audits/modules/common-source-index.md) (§6 notification) · [../../../audits/common-gap-analysis.md](../../../audits/modules/common-gap-analysis.md) · [../../../audits/common-docs-audit.md](../../../audits/modules/common-docs-audit.md)

## At a glance (verified)

| Aspect | Value |
|---|---|
| Live package | `com.kosign.wecms.domain.payment.notification` |
| Controller | `NotificationController` — `@RequestMapping("/api/v1/notifications")`, thin pass-through, no `@menuAuthz` |
| Services | `NotificationService` (read / mark), `ApprovalNotificationService` (producer leg) |
| Entities | `BltbNotiInfm`, `BltbNotiContInfm`, `BltbComntInfm`, `BltbPostInfm`, `BltbUserNewPostInfm` (+ `*Id`) |
| Backing tables | `BLTB_NOTI_INFM`, `BLTB_NOTI_CONT_INFM`, `BLTB_COMNT_INFM`, `BLTB_POST_INFM`, `BLTB_USER_NEW_POST_INFM` — all in the legacy table inventory |
| Removed (DEAD) | `domain/notification/` package + `NOTIFICATION` table (V238) — refactor-only, removed 2026-06-10 |
| Page size | `DEFAULT_PAGE_SIZE = 20`, `MAX_PAGE_SIZE = 100` (`NotificationService`) |
| Error code | `WBE_NOTIFICATION_NOT_FOUND` = "Notification not found." (`messages.properties:795`) |
| Scope | BLTB / approval-bulletin branch only — General `TYPE='G'` (`GENL_NOTI_*`) intentionally NOT ported |

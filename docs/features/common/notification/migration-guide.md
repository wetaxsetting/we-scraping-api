# Notification — Migration Guide (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

How the legacy JEX `comm_0192_007` notification surface maps to the Spring `domain/payment/notification/`
module, and what was deliberately dropped. Legacy trees are read-only functional spec.

## Legacy WSVC family → Spring

| Legacy WSVC | Legacy behaviour | Spring equivalent |
|---|---|---|
| `comm_0192_007_r001` | List notifications (UNION of BLTB + General branches) | `GET /api/v1/notifications` → `NotificationService.list` (**BLTB branch only**) |
| `comm_0192_007_r002` | Unread count | `GET /api/v1/notifications/unread-count` → `NotificationService.unreadCount` |
| `comm_0192_007_u001` (single REC) | Mark one read (`ACTV_YN='Y'` + audit) | `PUT /{notificationId}/read` → `NotificationService.markRead` |
| `comm_0192_007_u001` (full REC[]) | Mark all read | `PUT /read-all` → `NotificationService.markAllRead` |
| `comm_0192_001_c005` / `card_0001_005_c001` | Request-approval bulletin + per-approver notification fan-out | `ApprovalNotificationService.notifyRequestApproval` |
| `comm_0192_010_u001/u002` | Approve / reject comment + notification | `ApprovalNotificationService.notifyApprovalAction` |
| `comm_0192_004_d001` | Withdraw cleanup (hard-delete post/comment/tracker, soft-delete noti) | `ApprovalNotificationService.cleanupApprovalArtifacts` |

Legacy IDO references: `IDO.BLTB_NOTI_INFM_R002.xml` (list + count, BLTB branch) and `BltbHelper.java`
(`createNotificationToApprvr` / `createPublicNotification`) for the write side.

## Table mapping

Legacy `BLTB_NOTI_INFM` / `BLTB_NOTI_CONT_INFM` / `BLTB_POST_INFM` / `BLTB_USER_NEW_POST_INFM` /
`BLTB_COMNT_INFM` map column-for-column to the entities of the same name. All five are in the legacy
table inventory. See `database-mapping.md`.

## DEAD LEGACY — removed refactor-only implementation

> The **first** refactor attempt built a refactor-only `domain/notification/` package backed by a
> **`NOTIFICATION` table created by Flyway V238**. Both were **removed 2026-06-10** because a refactor-only
> table violates the legacy-first rule — there is no legacy `NOTIFICATION` table to migrate data from
> (memory `project_notification_module_removed.md`). The notification surface was then re-implemented on
> the legacy `BLTB_*` tables under `domain/payment/notification/`.

Implications for migration / parity:

- **Do not** document `domain/notification/` or `NOTIFICATION` (V238) as the current state — they no
  longer exist on disk. They are migration history only.
- **Do not** re-create a refactor-only notification table. Future notification needs back onto legacy
  `BLTB_*` tables, mirroring this module.
- The legacy → refactor data path is now trivial: BLTB rows copy 1:1 (same table names, same columns).

## Intentionally NOT ported — General notification branch

Legacy `comm_0192_007_r001` UNIONs a General-notification branch (`TYPE='G'` over `GENL_NOTI_*` tables).
Those tables are **absent from the refactor legacy-table-inventory**, so per the Refactoring Enforcement
Rule they get no entity / repository / endpoint. The refactor scopes to the BLTB / approval-bulletin
branch only — consistent with the producer (`ApprovalNotificationService` never writes `TYPE='G'` rows).
Tracked as open decision **C1** (`refactor-gap-analysis.md`). Confirmed in the controller Javadoc
(`NotificationController.java:18-22`) and service Javadoc (`NotificationService.java:34-40`).

## Naming / identifier notes

- The refactor uses the slug `notification` for docs/folders; legacy WSVC ids (`comm_0192_007`,
  `comm_0193_001.act`) survive only as (a) JEX file:line citations and (b) migration-traceability prose
  here.
- `NOTI_WSVC` stores the **legacy** `comm_0193_001.act` value verbatim on each row — this is a stored data
  value the frontend decodes to route, not a refactor identifier surface, so it stays as the legacy
  string (`ApprovalNotificationService.java:76-79`).
- ML codes translate: legacy `WBI1035` ("Added a new approval.") is the stored title literal; the
  not-found error is `WBE_NOTIFICATION_NOT_FOUND` (new refactor code, `messages.properties:795`).

## Cross-link

Full read-only legacy analysis + the C1/C2 decisions:
[../../../audits/notification-popup-legacy-vs-refactor-analysis.md](../../../audits/legacy-parity/notification-popup-legacy-vs-refactor-analysis.md).
Approval-detail popup parity (the click-through target, partial): 
[../../../audits/notification-approval-detail-popup-parity-audit.md](../../../audits/legacy-parity/notification-approval-detail-popup-parity-audit.md).

# notification — Interface Specification (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

The four REST endpoints. Source of truth: `NotificationController.java`. Base `/api/v1/notifications`,
JWT-authenticated, wire = lowercase snake_case via the global Jackson `SNAKE_CASE` strategy. Envelope is
`{ status, data }` on success; errors add `timestamp`/`path`/`trace_id`.

## Authorization rationale (no `@menuAuthz`)

All four handlers are `/api/v1/*` → `authenticated()` per `SecurityConfig`, but carry **no `@menuAuthz`**
gate. The bell is a global header surface shown to every authenticated user regardless of menu grants —
same rationale as `ScreenOptionController` / `NavigationController`. The omission is **explicit, not
accidental**: the endpoints are allow-listed in `EndpointAuthorizationCoverageTest` so the Phase-F
coverage test does not fail the build (`NotificationController.java:29-35`).

## 1. List — `GET /api/v1/notifications`

`NotificationController.list` (`:56-63`).

| Query param | Wire name | Default | Notes |
|---|---|---|---|
| read type | `read_type` | `A` | `U` = Unread tab (`ACTV_YN='N'`); `A`/anything else = All |
| page | `page` | `0` | zero-based |
| size | `size` | `0` | `0`/≤0 ⇒ `DEFAULT_PAGE_SIZE(20)`; `>100` ⇒ clamped to `MAX_PAGE_SIZE(100)` |
| search | `search` | (none) | partial, case-insensitive match on `snd_name` |

Tenant + recipient are **never** request params — read from `TenantContext`.

Response `data` = `NotificationListResponse`:

| Field (wire) | Type | Meaning |
|---|---|---|
| `list[]` | `NotificationRow[]` | page rows |
| `total_count` | number | total for the current `read_type` |
| `unread_count` | number | unread total (badge) — always `ACTV_YN='N'`, independent of tab |
| `page_number` | number | zero-based page index |
| `page_size` | number | rows per page |
| `total_pages` | number | total page count |

`NotificationRow` (wire): `notification_id`, `bltb_cd`, `noti_seq_no`, `title`, `content`, `snd_id`,
`snd_name`, `noti_json`, `noti_wsvc`, `del_yn`, `read` (bool, `ACTV_YN=='Y'`), `actv_yn`, `reg_dtm`,
`mod_dtm`, `user_img_url` (slug `/api/v1/users/{snd_id}/image` or null). Maps legacy `comm_0192_007_r001`.

## 2. Unread count — `GET /api/v1/notifications/unread-count`

`unreadCount` (`:69-72`). No params. Response `data` = `UnreadCountResponse { total_count }`. Maps legacy
`comm_0192_007_r002` (legacy `TOT_APPRVR_CNT` is dead and intentionally not carried).

## 3. Mark one read — `PUT /api/v1/notifications/{notificationId}/read`

`markRead` (`:80-84`). Path var `notificationId` (long) = `NOTI_SEQ_NO`. Tenant + recipient scoped,
idempotent. Response `data` = null (`ApiResponse.ok((Void) null)`). Maps legacy `comm_0192_007_u001`
(single REC).

**Error:** unknown id, or an id belonging to another tenant/recipient ⇒ `WBE_NOTIFICATION_NOT_FOUND`
(HTTP 200 + `status.error = true` per the project BusinessException contract). The single envelope merges
"not found" + "wrong tenant/recipient" so it never leaks another user's row.

## 4. Mark all read — `PUT /api/v1/notifications/read-all`

`markAllRead` (`:91-95`). No body. Flips every `ACTV_YN='N'` row for the caller. Always success — zero
unread is a no-op. Response `data` = null. Maps legacy `comm_0192_007_u001` (full REC[]).

## Error codes

| Code | Message | Source |
|---|---|---|
| `WBE_NOTIFICATION_NOT_FOUND` | "Notification not found." | `messages.properties:795`; thrown by `NotificationService.markRead` on 0 affected rows |

## Out of scope (not ported)

- **General notification branch** (`TYPE='G'`, `GENL_NOTI_*`) — no endpoint; tables out of inventory
  (open decision C1).
- **No delete endpoint** — legacy has no per-user notification delete on this surface either; soft-delete
  (`DEL_YN='Y'`) is a producer-side withdraw side effect only (open decision C2, MATCH-with-legacy).
- The sender-image endpoint `/api/v1/users/{snd_id}/image` is referenced by `user_img_url` but is itself
  out of scope for this module.

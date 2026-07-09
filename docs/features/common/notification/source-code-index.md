# notification — Source Code Index (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

Verified file map of the live `domain/payment/notification/` module plus its frontend twin and tests.
✔ = opened and verified against the working tree on 2026-06-23.

## Backend — `wecms-api/src/main/java/com/kosign/wecms/domain/payment/notification/`

| Layer | File | Notes |
|---|---|---|
| Controller ✔ | `controller/NotificationController.java` | `@RequestMapping("/api/v1/notifications")`; 4 handlers; thin pass-through; no `@menuAuthz` |
| Service ✔ | `service/NotificationService.java` | list / unreadCount / markRead / markAllRead; `DEFAULT_PAGE_SIZE=20`, `MAX_PAGE_SIZE=100`; keyword escape; row mapping |
| Service ✔ | `service/ApprovalNotificationService.java` | producer leg — `notifyRequestApproval`, `notifyApprovalAction`, `cleanupApprovalArtifacts` |
| Entity ✔ | `entity/BltbNotiInfm.java` (+ `BltbNotiInfmId.java`) | `@Table BLTB_NOTI_INFM`; PK `(BIZ_KEY, BLTB_CD, NOTI_SEQ_NO)` |
| Entity ✔ | `entity/BltbNotiContInfm.java` (+ `BltbNotiContInfmId.java`) | `@Table BLTB_NOTI_CONT_INFM`; PK adds `LANG_CD` |
| Entity ✔ | `entity/BltbComntInfm.java` (+ `BltbComntInfmId.java`) | `@Table BLTB_COMNT_INFM`; PK `(…, POST_SEQ_NO, COMNT_SEQ_NO)` |
| Entity ✔ | `entity/BltbPostInfm.java` (+ `BltbPostInfmId.java`) | `@Table BLTB_POST_INFM`; PK `(…, POST_SEQ_NO)` |
| Entity ✔ | `entity/BltbUserNewPostInfm.java` (+ `BltbUserNewPostInfmId.java`) | `@Table BLTB_USER_NEW_POST_INFM`; PK `(…, USER_ID, POST_SEQ_NO)` |
| Repository ✔ | `repository/BltbNotiInfmRepository.java` | `search`, `countUnread`, `markRead`, `markAllRead`, `softDeleteByPost` |
| Repository ✔ | `repository/BltbNotiContInfmRepository.java` | plain `JpaRepository` (writes via `save`) |
| Repository ✔ | `repository/BltbComntInfmRepository.java` | `deleteByPost` |
| Repository ✔ | `repository/BltbPostInfmRepository.java` | `findByBizKeyAndBltbCdAndApprSeqNo` |
| Repository ✔ | `repository/BltbUserNewPostInfmRepository.java` | `deleteByPost` |
| DTO ✔ | `dto/NotificationListResponse.java` | `list`, `totalCount`, `unreadCount`, `pageNumber`, `pageSize`, `totalPages` |
| DTO ✔ | `dto/NotificationRow.java` | 15 wire fields incl. `notificationId`/`notiSeqNo`, `read`/`actvYn`, `userImgUrl` |
| DTO ✔ | `dto/UnreadCountResponse.java` | `totalCount` |
| DTO ✔ | `dto/NotificationProjection.java` | repository constructor-projection (internal; never on the wire) |

Cross-package collaborators (read by the producer): `ExptResolApprvrInfmRepository.findChain`,
`BusiAdmnInfmRepository.findAllByBizKey`, `ExptResolFileRepository`, `LegacyFunctionRepository.nextVal`
(the `wb_get_next_val` funnel).

## i18n

- `messages.properties:795` ✔ — `WBE_NOTIFICATION_NOT_FOUND=Notification not found.`

## Tests — `wecms-api/src/test/java/com/kosign/wecms/domain/payment/notification/`

| File | Coverage |
|---|---|
| `controller/NotificationControllerTest.java` ✔ | standalone-MockMvc slice: snake_case wire contract, `read_type=A` + paging defaults, param read from snake_case names, unread-count, mark-read path id, `WBE_NOTIFICATION_NOT_FOUND` envelope, read-all |
| `service/NotificationServiceTest.java` ✔ | Mockito: unread/all filters, case-insensitive read_type, default sort, paging clamp, keyword escape, blank keyword, unread count, idempotent mark-read + not-found, mark-all, deleted-row-kept, tenant isolation, empty result, round-trip with the producer's written shape, blank image → null URL |

## Frontend — `wecms-web/`

| File | Notes |
|---|---|
| `components/layout/notifications/NotificationPopup.tsx` ✔ | bell trigger + badge + right-docked slide-over; tabs + search + Read All; row click → mark read → Approval Details |
| `components/layout/notifications/NotificationTabs.tsx` ✔ | Unread / All tab strip |
| `components/layout/notifications/NotificationSearch.tsx` ✔ | wraps shared `<SearchInput>` |
| `components/layout/notifications/NotificationItem.tsx` ✔ | single row — avatar + sender/time + title/content |
| `components/layout/notifications/types.ts` ✔ | in-memory `NotificationRow` (camelCase) + `NotificationTab` |
| `lib/api/notification.ts` ✔ | `fetchNotifications`, `fetchUnreadCount`, `markNotificationRead`, `markAllNotificationsRead` |
| `components/layout/Header.tsx` ✔ | mounts `<NotificationPopup />` (line 151) |

## DEAD LEGACY (removed — NOT on disk)

- `domain/notification/` package — removed 2026-06-10.
- `NOTIFICATION` table + Flyway **V238** — removed 2026-06-10.

Do not cite these as live; they exist only as migration history.

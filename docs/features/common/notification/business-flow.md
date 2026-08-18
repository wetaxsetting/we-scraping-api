# Notification — Business Flow (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

End-to-end flow for the four consumer endpoints plus the producer leg. Source of truth:
`domain/payment/notification/service/NotificationService.java` (read/mark) and
`ApprovalNotificationService.java` (write). All identity comes from `TenantContext` (JWT), never the
request.

## Consumer side — `NotificationService`

### 1. List — `list(readType, search, page, size)` (`NotificationService.java:100-129`)

1. Read identity: `bizKey = TenantContext.currentBizKey()`, `rcvId = TenantContext.currentUserId()`,
   `langCd = currentLangCode()` (2-char `LANG_CD`; defaults `EN` when none bound) (`:102-104,192-195`).
2. Resolve filter: `readType` `"U"` (case-insensitive) ⇒ `actvYnFilter = "N"`; anything else ⇒ `null`
   (all) (`:106`).
3. Build keyword pattern from `search`: lower-case + escape LIKE meta (`% _ !`, escape char `!`) +
   wrap `%…%`; blank ⇒ `null` so the JPQL `IS NULL OR` guard skips the predicate (`:107,205-217`).
4. Clamp paging: `size <= 0 ⇒ DEFAULT_PAGE_SIZE(20)`, else `min(size, MAX_PAGE_SIZE=100)`; `page` clamped
   `>= 0`. Sort `regDtm DESC, notiSeqNo DESC` (`:109-111,76-77`).
5. `repository.search(bizKey, rcvId, langCd, actvYnFilter, keywordPattern, pageable)` — INNER JOIN
   `BLTB_NOTI_CONT_INFM` on the caller's `langCd`, LEFT JOIN `USER_BUSI_CONN_INFM` for the sender image
   (`BltbNotiInfmRepository.java:73-95`).
6. `repository.countUnread(bizKey, rcvId, langCd)` — always `ACTV_YN='N'`, independent of the tab (`:116`).
7. Map projections → `NotificationRow` (`:118-120,227-246`) and return `NotificationListResponse(rows,
   totalElements, unreadCount, number, size, totalPages)` (`:122-128`).

`DEL_YN='Y'` rows are **kept** in the list (legacy parity — frontend shows `WBI1098` on click).

### 2. Unread count — `unreadCount()` (`:136-143`)

`repository.countUnread(bizKey, rcvId, langCd)` → `UnreadCountResponse(count)`. Same content INNER JOIN
as `search`, so badge and Unread tab agree exactly.

### 3. Mark one read — `markRead(notificationId)` (`:157-169`)

1. `nowDtm` = UTC-now `yyyyMMddHHmmss` from the injected `Clock` (`:159`).
2. `repository.markRead(bizKey, rcvId, BigDecimal.valueOf(id), modId=rcvId, nowDtm)` sets `ACTV_YN='Y'`
   + `MOD_ID`/`MOD_DTM`, scoped to `(BIZ_KEY, RCV_ID, NOTI_SEQ_NO)` (`BltbNotiInfmRepository.java:140-150`).
3. `updated == 0` ⇒ `throw new BusinessException("WBE_NOTIFICATION_NOT_FOUND")` (merges "not found" +
   "wrong tenant/recipient" — never leaks another user's row) (`:166-168`).
4. Idempotent: an already-read row (`Y→Y`) still reports 1 affected, so a double-click is not a spurious
   not-found.

### 4. Mark all read — `markAllRead()` (`:175-183`)

`repository.markAllRead(bizKey, rcvId, modId=rcvId, nowDtm)` sets `ACTV_YN='Y'` on every `ACTV_YN='N'` row
for the caller. Always success — zero unread is a no-op, not an error.

## Producer side — `ApprovalNotificationService`

The write leg. Reproduces the legacy request-approval / approve-reject fan-out — **not** a consumer
endpoint, invoked from each Payment module's approval flow inside the caller's transaction.

| Method | Trigger | Rows written |
|---|---|---|
| `notifyRequestApproval(apprSeqNo, postTitle, postCont, postApprDv, apprTypeLabel)` (`:132-236`) | Request approval | 1 `BLTB_POST_INFM` + 1 `BLTB_USER_NEW_POST_INFM` per approver + 1 `BLTB_NOTI_INFM` (+ 3 `BLTB_NOTI_CONT_INFM` EN/KO/VI) per approver |
| `notifyApprovalAction(apprSeqNo, apprDvCode, comntTaskCd)` (`:286-396`) | Approve / reject / complete | 1 `BLTB_COMNT_INFM` remark + 1 `BLTB_NOTI_INFM` (+ 3 content rows) per recipient (acted approvers ∪ admins, distinct, excluding the actor) |
| `cleanupApprovalArtifacts(apprSeqNo)` (`:414-435`) | Withdraw | deletes `EXPT_RESOL_FILE`, `BLTB_USER_NEW_POST_INFM`, `BLTB_COMNT_INFM`, the `BLTB_POST_INFM`; **soft-deletes** `BLTB_NOTI_INFM` (`DEL_YN='Y'`); leaves `BLTB_NOTI_CONT_INFM` — legacy `comm_0192_004_d001` parity |

Key constants (`ApprovalNotificationService.java:71-86`): `BLTB_CD_APPR = "000000000000000"`,
`POST_TYPE_APPROVAL = "02"`, `POST_STTS_PRIVATE = "PV"`, `NOTI_WSVC = "comm_0193_001.act"`,
`NOTI_TITLE = "Added a new approval."` (legacy `WbMessageCode.WBI1035`), `NOTI_LANGS = [EN, KO, VI]`.
`NOTI_SEQ_NO` is allocated through the `wb_get_next_val` funnel (`LegacyFunctionRepository.nextVal`).

> **Not written on the request-approval path:** `PUSH_NOTICE_INFM` / `MSG_SND_HIS` — legacy
> `pushToMobileNoti` returns early (no `dvCd`), so the refactor omits them too (`:53-58`).

## Producer ↔ consumer contract

The consumer read path is pinned to the write shape: the writer seeds EN/KO/VI content for every
notification, so the read INNER JOIN on `langCd` always matches for those locales; `NOTI_TITLE` is the
literal the Unread/All tab renders; `ACTV_YN='N'` at birth is what makes a fresh notification unread.
Verified by `NotificationServiceTest.list_readsBackTheExactShapeApprovalNotificationServiceWrites`.

# notification — Test Scenarios (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

Coverage for the BLTB-backed notification module. Existing automated tests (verified on disk):
`NotificationControllerTest` (standalone MockMvc) and `NotificationServiceTest` (Mockito). The table below
marks which scenarios those cover (✔ automated) vs. recommended SIT additions (◦).

## List

| # | Scenario | Expected | Status |
|---|---|---|---|
| L1 | List All (default `read_type=A`) | no `actv_yn` filter; happy-path snake_case wire fields | ✔ `list_happyPath…`, `list_defaultsReadTypeToAll`, `list_all_passesNullActvYnFilter` |
| L2 | List Unread (`read_type=U`) | `ACTV_YN='N'` filter; case-insensitive (`u` ⇒ unread) | ✔ `list_unread_appliesActvYnFilter…`, `list_unread_isCaseInsensitive` |
| L3 | Search by sender name | keyword lower-cased, LIKE-meta escaped, wrapped `%…%`; blank ⇒ null pattern | ✔ `list_keyword_isLowercasedWrappedAndEscaped`, `list_blankKeyword_passesNullPattern` |
| L4 | Paging defaults + clamp | size≤0 ⇒ 20; size>100 ⇒ 100; page<0 ⇒ 0 | ✔ `list_pagination_defaultsAndClamps` |
| L5 | Default sort | `regDtm DESC, notiSeqNo DESC` | ✔ `list_defaultSort_isRegDtmDescThenNotiSeqNoDesc` |
| L6 | Empty result | success, empty list, counts 0 | ✔ `list_empty_returnsSuccessWithEmptyList` |
| L7 | `DEL_YN='Y'` row | still returned, `del_yn='Y'` flagged (not filtered) | ✔ `list_deletedRow_isStillReturnedAndFlagged` |
| L8 | Producer-shape round-trip | row written by `ApprovalNotificationService` reads back with all fields | ✔ `list_readsBackTheExactShapeApprovalNotificationServiceWrites` |
| L9 | Sender image resolution | non-blank `USER_IMG` ⇒ `/api/v1/users/{snd_id}/image`; blank ⇒ null | ✔ `…blankSenderImage_resolvesUserImgUrlToNull` + L8 |
| L10 | Wire params read from snake_case names | `read_type`/`search`/`page`/`size` → service args | ✔ `list_readsParamsFromSnakeCaseWireNames` |

## Unread badge

| # | Scenario | Expected | Status |
|---|---|---|---|
| U1 | Unread count | `total_count` over `ACTV_YN='N'`, scoped tenant/user/lang | ✔ `unreadCount_returnsTotalCount`, `unreadCount_scopedToTenantUserLang` |
| U2 | Badge independent of tab | `unread_count` in list response stays unread total even on All tab | ✔ `list_all_passesNullActvYnFilter` (asserts `unreadCount=3`) |

## Mark read

| # | Scenario | Expected | Status |
|---|---|---|---|
| M1 | Mark one read | `ACTV_YN='Y'` + `MOD_ID`/`MOD_DTM`, scoped `(BIZ_KEY, RCV_ID, NOTI_SEQ_NO)` | ✔ `markRead_updatesRowScopedToTenantUserWithModAudit`, controller `markRead_invokesServiceWithPathId` |
| M2 | Unknown / other-tenant id | 0 affected ⇒ `WBE_NOTIFICATION_NOT_FOUND` (HTTP 200, `status.error=true`) | ✔ service `…throwsNotFound` + controller `…envelopesAsWbeNotificationNotFound` |
| M3 | Idempotent re-mark | already-read `Y→Y` still 1 affected, no error | ✔ (behaviour asserted via repo contract; SIT smoke ◦) |
| M4 | Mark all read | every unread → read; zero unread = success no-op | ✔ `markAllRead_updatesAllUnread…`, controller `markAllRead_invokesService` |

## Tenant / recipient isolation

| # | Scenario | Expected | Status |
|---|---|---|---|
| T1 | List uses TenantContext only | queries carry context `bizKey`/`userId`/`langCd`, never request input | ✔ `list_alwaysUsesTenantContextBizKeyAndUser_neverRequestInput` |
| T2 | Mark-read scoped to context | other tenant's context → query carries that tenant | ✔ `markRead_scopesToTenantContext` |
| T3 | Cross-tenant id leak guard | another tenant's `NOTI_SEQ_NO` ⇒ not-found, never flips | ✔ via M2 + T2 |

## Recommended SIT additions (◦ not automated)

- ◦ Live HTTP smoke: list Unread vs All with real `BLTB_NOTI_INFM` rows for `VN_VNM0004_000001`; confirm
  badge matches Unread tab count.
- ◦ Localized content: caller with `LANG_CD=KO` sees the KO content row; a notification missing the
  caller's language is absent from list + count.
- ◦ End-to-end producer→consumer: request approval → recipient sees the new unread notification → mark
  read → badge decrements.
- ◦ Withdraw soft-delete: withdraw an approval → the recipient's notification shows `del_yn='Y'` but still
  lists (frontend `WBI1098`).

> No `@DataJpaTest` infra in this project (per `wecms-api/CLAUDE.md`); the JPQL in `BltbNotiInfmRepository`
> is exercised by the controller/integration layer and live SIT, not a slice JPA test.

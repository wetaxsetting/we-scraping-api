# notification — Functional Specification (Backend)

**Part of:** Common → notification
**Last verified:** 2026-06-23

The behavioural contract of the BLTB-backed notification module. Source of truth:
`NotificationController.java`, `NotificationService.java`, `BltbNotiInfmRepository.java`.

## Scope

- Ports legacy `comm_0192_007` WSVC family — **BLTB / approval-bulletin branch only**.
- The General-notification branch (`TYPE='G'`, `GENL_NOTI_*` tables) is **intentionally NOT ported** —
  those tables are absent from the refactor legacy-table-inventory (`NotificationController.java:18-22`,
  `NotificationService.java:34-40`). Open decision **C1** in `refactor-gap-analysis.md`.

## Read-type tabs

| `read_type` | Meaning | Filter applied |
|---|---|---|
| `U` | Unread tab | `ACTV_YN = 'N'` (`NotificationService.java:106`, `READ_TYPE_UNREAD="U"`, `ACTV_YN_UNREAD="N"`) |
| `A` (default) | All tab | no `actv_yn` predicate (`actvYnFilter = null`) |
| anything else | treated as All | `null` filter (`:106` — `equalsIgnoreCase("U")` else null) |

`read_type` matching is case-insensitive (`"u"` ⇒ unread, verified `list_unread_isCaseInsensitive`).

## Search

- `search` matches the **sender display name** (`SND_NAME`) — legacy `T.snd_name ILIKE '%'||?||'%'`
  (`comm_0192_007_r001_act.jsp:75-78`, `BltbNotiInfmRepository.java:87`).
- Pattern built service-side: `trim` → escape LIKE meta (`%`, `_`, `!` → `!`-escaped) → wrap `%…%` →
  lower-case (`NotificationService.java:205-217`). JPQL uses `LOWER(n.sndName) LIKE :pattern ESCAPE '!'`.
- Blank / null search ⇒ `null` pattern ⇒ predicate skipped (mirrors legacy "no `SRCH_WD` ⇒ no `ILIKE`")
  (`:107`, verified `list_blankKeyword_passesNullPattern`).
- Example: `"100%_x!"` → `"%100!%!_x!!%"` (verified `list_keyword_isLowercasedWrappedAndEscaped`).

## Paging

- Zero-based (`page`), default 0; negative clamped to 0 (`:110`).
- `size`: `<= 0` ⇒ `DEFAULT_PAGE_SIZE = 20`; `> MAX_PAGE_SIZE` ⇒ clamped to `MAX_PAGE_SIZE = 100`
  (`:61-65,109`).
- Default sort: `regDtm DESC, notiSeqNo DESC` — `notiSeqNo` is the stable tiebreaker for rows sharing a
  `reg_dtm` second so paging stays deterministic (`:73-77`).
- Response carries `total_count` (for the current `read_type`), `unread_count` (always unread, badge),
  `page_number`, `page_size`, `total_pages` (`NotificationListResponse.java`).

## Tenant + recipient scoping

- `BIZ_KEY` and `RCV_ID` are read from `TenantContext` (signed JWT) — never from the wire
  (`NotificationService.java:102-103`; wire has no `biz_key` / `rcv_id`). Verified
  `list_alwaysUsesTenantContextBizKeyAndUser_neverRequestInput`.
- Every read/mark query constrains `n.bizKey = :bizKey AND n.rcvId = :rcvId` so a caller only ever sees /
  flips their own rows (`BltbNotiInfmRepository.java:84-85,144,164`).

## Localized content

- Title/content come from the per-language `BLTB_NOTI_CONT_INFM` INNER JOIN on the caller's `langCd`
  (`BltbNotiInfmRepository.java:79-81`). A notification with no content row for the caller's language is
  absent from both the list and the count (legacy parity).
- Producer seeds EN / KO / VI for every notification, so EN/KO/VI callers always match.
- `LANG_CD` is the 2-char code (`EN`/`KO`/`KM`/`VI`); defaults `EN` when no context language
  (`NotificationService.java:189-195`).

## Mark-read semantics

- `markRead(id)` scoped to `(BIZ_KEY, RCV_ID, NOTI_SEQ_NO)`; sets `ACTV_YN='Y'` + `MOD_ID`/`MOD_DTM`.
- **Idempotent** — already-read row updates `Y→Y`, still 1 affected, no spurious not-found.
- 0 rows affected ⇒ `WBE_NOTIFICATION_NOT_FOUND` (unknown id or other tenant/recipient — single envelope).
- `markAllRead()` flips every `ACTV_YN='N'` row for the caller; **always success** even at zero rows
  (`NotificationService.java:171-183`).
- `MOD_DTM` (stamped on mark-read) is the closest proxy to a read-at timestamp — legacy has no dedicated
  read-at column (`NotificationRow.java:84-87`).

## Soft-delete visibility

`DEL_YN='Y'` rows are **not** filtered out of the list (`BltbNotiInfmRepository.java:61-64`); the wire
carries `del_yn` so the frontend can show the "notification was deleted" dialog (`WBI1098`) instead of
navigating. Verified `list_deletedRow_isStillReturnedAndFlagged`.

## Authorization

Every endpoint is `/api/v1/*` → `authenticated()` per `SecurityConfig`, but carries **no `@menuAuthz`** —
the bell is a global header surface shown to every authenticated user regardless of menu grants (same
rationale as `ScreenOptionController` / `NavigationController`). The omission is explicit: the endpoints
are allow-listed in `EndpointAuthorizationCoverageTest` (`NotificationController.java:29-35`).

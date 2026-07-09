# Notifications Popup — Legacy (JEX) vs Refactor (Spring) Analysis

> **System:** Refactor (wecms-api + wecms-web) · **Type:** Read-only legacy analysis + implementation plan
> **Feature slug:** `notifications` · **Legacy WSVC family:** `comm_0192_007`
> **Author:** Claude (read-only analysis) · **Date:** 2026-06-14
> **Status:** Analysis complete — **NO source code or DB schema changed.** Implementation pending PO/lead approval of the open decisions in §5 + §9.

This document is a *read-only* analysis. It analyzes the legacy JEX Notifications popup end-to-end, audits the current refactor backend/frontend, identifies the gaps, and proposes a safe implementation plan + Spring API design. It does **not** modify legacy code, refactor code, or any database. Implementation must wait until the open decisions below are resolved.

**Scope guard honoured:** the legacy trees (`WECMS DEV/`) were read only as functional spec. Per the workspace Refactoring Enforcement Rule, only tables present in the authoritative [legacy-table-inventory.md](../references/database/legacy-table-inventory.md) are in refactor scope — see §2.4 for the critical `GENL_NOTI_*` exclusion.

---

## 0. TL;DR — is the refactor API complete?

**NO. The refactor Notifications popup API is MISSING. It must be implemented.**

- The frontend (`wecms-web`) is **fully built** — popup UI, API client ([lib/api/notification.ts](../../../wecms-web/lib/api/notification.ts)), wire types ([lib/types/api.ts:4086-4117](../../../wecms-web/lib/types/api.ts)) — and calls four endpoints that **do not exist on the backend**:
  - `GET /api/v1/notifications?read_type=U|A&page&size`
  - `GET /api/v1/notifications/unread-count`
  - `PUT /api/v1/notifications/{notificationId}/read`
  - `PUT /api/v1/notifications/read-all`
- The refactor backend has **only the write side** — `ApprovalNotificationService` produces `BLTB_NOTI_INFM` / `BLTB_NOTI_CONT_INFM` rows during approval flows. There is **no controller, no read service, no read repository query** for listing / counting / marking-read.
- A grep of `wecms-api/src/main/java/**/controller/*.java` for `notification`/`noti` returns **zero** matching handlers.

**Bottom line:** the read/count/mark-read API is greenfield. Two blocking decisions (§5) must be resolved before coding: (OD-A) the **wire contract** — the frontend invented a clean shape that does not match the legacy data model; (OD-B) the **`GENL_NOTI_*` scope** — the legacy list UNIONs a "general notification" branch whose tables are out of the refactor inventory.

---

## 1. Legacy popup flow

### 1.1 Files analyzed (exact paths)

| Artifact | Path |
|---|---|
| View JSP | [comm_0192_007_view.jsp](../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0192_007_view.jsp) |
| TypeScript | [comm_0192_007.ts](../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0192_007.ts) |
| List API | [comm_0192_007_r001_act.jsp](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_007_r001_act.jsp) |
| Count API | [comm_0192_007_r002_act.jsp](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_007_r002_act.jsp) |
| Mark-read API | [comm_0192_007_u001_act.jsp](../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_007_u001_act.jsp) |
| List IDO | [IDO.BLTB_NOTI_INFM_R002.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BLTB_NOTI_INFM_R002.xml) |
| Count IDO | [IDO.BLTB_NOTI_INFM_R002_CNT.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BLTB_NOTI_INFM_R002_CNT.xml) |
| List WSVC | [WSVC.comm_0192_007_r001.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0192_007_r001.xml) |
| Count WSVC | [WSVC.comm_0192_007_r002.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0192_007_r002.xml) |
| Mark-read WSVC | [WSVC.comm_0192_007_u001.xml](../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0192_007_u001.xml) |

### 1.2 How the popup is opened

`comm_0192_007_view.jsp` is a **shared layer** that injects two right-docked side panels into the global header: a **Notifications** panel (`.notifications-wrap`) and an **Approval** panel (`.approval-wrap-noti`). It is loaded with the page chrome, not a standalone screen.

- The notification bell click handler is bound in the view JSP, `comm_0192_007_view.jsp:216-226`:
  ```js
  $("body").on("click", "#header .util-wrap .infor-area a.notification", function(){ ... toggle .notifications-wrap.active ... });
  ```
  Toggling the bell slides the `.notifications-wrap` panel in/out (CSS transition on `right`).
- The header bell badge displays the **unread count** in `span.coundNotifi` (populated by the count API, see §1.5).
- Tabs inside the panel: **Unread** (`#Unread`, default active) and **All** (`#readAllComm`) — `comm_0192_007_view.jsp:108-109`.
- Controls: a **Read All** button (`#btnReadAll`), a keyword search input (`input.searchNoti`), and a scrollable list `ul.listNoti` — `comm_0192_007_view.jsp:112,119,125`.

### 1.3 Parameters passed from the UI → list API

From `comm_0192_007.ts` `fn_loadDataReadNoti` (`comm_0192_007.ts:299-324`), the list call (`comm_0192_007_r001`) sends:

| Field | Source | Meaning |
|---|---|---|
| `READ_TYPE` | `"U"` (Unread tab) or `"A"` (All tab) | required; chooses the active-row filter |
| `SRCH_WD` | `input.searchNoti` value | optional keyword (matched on sender name) |
| `PAGE_PER_CNT` | `comm_0192_007._PAGE_PER_CNT` (starts 20, grows +10 on scroll) | page size |
| `PAGE_NO` | `1` | page number (the popup always loads from page 1 and grows page size on scroll) |

Note the **paging model is unusual**: instead of incrementing `PAGE_NO`, the scroll handler (`comm_0192_007.ts:264-279`) grows `PAGE_PER_CNT` by 10 and re-requests from page 1. The list is fully re-rendered each time (`comm_0192_007.ts:332` `.empty()` then re-append).

### 1.4 Which API is called first / full flow

On panel open / tab switch / search, the **first and primary call is `comm_0192_007_r001`** (the list). The **count** call `comm_0192_007_r002` is invoked separately to refresh the bell badge — in the TS it is wrapped by the external callback `fn_callback_notifications` (referenced at `comm_0192_007.ts:259, 455, 504, 522, 540`), which is defined in the global header script (outside this feature file) and re-reads the unread count after any read/mark-all action.

End-to-end (list path):

```
Bell click (view.jsp:216) → .notifications-wrap.active
  → comm_0192_007.ts fn_loadDataReadNoti()           [r001, .jct, async=false]
    → JEX filter chain → comm_0192_007_r001_act.jsp
      → IDODynamic dynamic0/1/2 + BLTB_NOTI_INFM_R002 IDO (UNION of BLTB + GENL)
        → PostgreSQL (BLTB_NOTI_INFM ⋈ BLTB_NOTI_CONT_INFM  UNION ALL  GENL_NOTI_INFO ⋈ ...)
      → JexData REC[] → normalize datetime → return
    → render ul.listNoti rows
Bell badge ← fn_callback_notifications() → comm_0192_007_r002 → BLTB_NOTI_INFM_R002_CNT → TOT_CNT
```

Row click flow (`comm_0192_007.ts:419-507` `fn_readOneCommNoti`):
1. Decode the row's base64 JSON payload.
2. Call `comm_0192_007_u001` (mark-read) for that single row.
3. If the row's `DEL_YN === 'Y'` → show `WBI1098` ("notification deleted") dialog and reload.
4. Else navigate by type:
   - `TYPE === 'N'` (board/approval): open `comm_0195_001.act` layer popup with `POST_SEQ_NO` + `APPR_DV` from the row's `NOTI_JSON`, or redirect to `bltb_0001_001.act` for board posts.
   - `TYPE === 'G'` (general): redirect by `WEB_SVC_ID` / `PRDT_TYPE` (TRCO screens).
5. Reload the list + refresh the count.

### 1.5 Approval panel — out of scope

The view JSP also hosts an **Approval** side panel and the TS defines `fn_oneApprove` / `fn_rejectAppro` (`comm_0192_007.ts:508-543`) which call **different services** `comm_0192_010_u001` / `comm_0192_010_u002`. **These are not part of the Notifications popup** and are explicitly out of scope for this analysis (separate WSVC family `comm_0192_010`).

---

## 2. Legacy display data logic

### 2.1 What data is loaded

The list IDO `BLTB_NOTI_INFM_R002` is a **`UNION ALL` of two notification sources** wrapped in an outer `SELECT ... FROM (...) T` so a single dynamic filter + ORDER BY apply across both:

- **Branch N — board/approval notifications** (`IDO.BLTB_NOTI_INFM_R002.xml:61-95`): `BLTB_NOTI_INFM BNI ⋈ BLTB_NOTI_CONT_INFM BNCI` on `(BIZ_KEY, NOTI_SEQ_NO, BLTB_CD)`. `TYPE = 'N'`. Title/content come from the localized child `BNCI`. A correlated subquery resolves `USER_IMG_URL` from `USER_BUSI_CONN_INFM`. `APPRVR_ID` / `APPR_SEQ_NO` are extracted from `BNI.NOTI_JSON::jsonb`.
- **Branch G — general/system notifications** (`IDO.BLTB_NOTI_INFM_R002.xml:97-130`): `GENL_NOTI_INFO GNI ⋈ GENL_NOTI_CONT_INFO GNCI ⋈ GENL_NOTI_SND_RCV_INFO GNSRI`. `TYPE = 'G'`. Sender name via `wb_get_user_nm(...)`.

The outer SELECT (`IDO.BLTB_NOTI_INFM_R002.xml:43-53`) also computes `APPRVR_STTS` via a correlated subquery against **`EXPT_RESOL_APPRVR_INFM`** when both `APPRVR_ID` and `APPR_SEQ_NO` are present.

### 2.2 Tables queried (legacy)

| Table | Role | In refactor inventory? |
|---|---|---|
| `BLTB_NOTI_INFM` | Branch N master (per-recipient row, `ACTV_YN`, `DEL_YN`, `NOTI_JSON`) | ✅ yes |
| `BLTB_NOTI_CONT_INFM` | Branch N localized title/content (per `LANG_CD`) | ✅ yes |
| `USER_BUSI_CONN_INFM` | sender image (`USER_IMG`) subquery | ✅ yes |
| `EXPT_RESOL_APPRVR_INFM` | approver status subquery (`APPRVR_STTS`) | ✅ yes |
| `GENL_NOTI_INFO` | Branch G master | ❌ **NOT in inventory** |
| `GENL_NOTI_CONT_INFO` | Branch G localized content | ❌ **NOT in inventory** |
| `GENL_NOTI_SND_RCV_INFO` | Branch G per-recipient send/receive + `ACTV_YN` | ❌ **NOT in inventory** |

### 2.3 Columns returned (list, per the WSVC `REC` subRule — `WSVC.comm_0192_007_r001.xml:43-68`)

`SND_ID, SND_NAME, NOTI_DT, NOTI_TM, NOTI_URL, ACTV_YN, BLTB_CD, NOTI_SEQ_NO, BLTB_NAME, TITL, CONT, USER_IMG_URL, NOTI_WSVC, NOTI_JSON, DEL_YN, APPRVR_ID, APPR_SEQ_NO, APPRVR_STTS, TYPE, GENL_NOTI_TYPE_CD, WEB_SVC_ID, JSON_REQ_DATA, GENL_NOTI_SEQ_NO, PRDT_TYPE`.

Backend post-processing in `comm_0192_007_r001_act.jsp:151-175`:
- If `USER_IMG_URL` is non-blank → rewritten to `szon_0012_003_r002.act?USER_ID={SND_ID}` (thumbnail endpoint).
- `NOTI_TM` is overwritten with the normalized full datetime `NOTI_DT + NOTI_TM` via `DateTimeUtils.normalizeResult(...)` (UTC → user TZ).
- (Note: the legacy action contains `System.out.println` debug statements at lines 158, 161, 165-168 — debug noise, not behaviour.)

### 2.4 ⚠️ CRITICAL SCOPE FINDING — `GENL_NOTI_*` is out of refactor scope

The legacy list/count UNIONs the **General Notification** branch (`GENL_NOTI_INFO` / `GENL_NOTI_CONT_INFO` / `GENL_NOTI_SND_RCV_INFO`). **None of these three tables appear in the authoritative [legacy-table-inventory.md](../references/database/legacy-table-inventory.md)** (verified 2026-06-14 — the inventory contains `bltb_noti_infm` + `bltb_noti_cont_infm` but no `genl_noti_*`).

Per the workspace **Refactoring Enforcement Rule** ("if legacy uses N tables → ONLY refactor tables in the active list"), the refactor list MUST implement **Branch N only** (`TYPE = 'N'`, board/approval notifications). Branch G is dropped unless the PO explicitly widens the inventory baseline.

This is **consistent with the refactor's write side**: `ApprovalNotificationService` only ever writes `BLTB_NOTI_INFM` / `BLTB_NOTI_CONT_INFM` (Branch N). There is no `GENL_NOTI_*` writer in the refactor, so Branch-G rows would never be produced anyway. → **Recommendation:** scope the refactor list to Branch N; record Branch G as a deferred PO decision (OD-B).

### 2.5 Unread / read status

- `ACTV_YN` is the read flag on `BLTB_NOTI_INFM`. **`ACTV_YN = 'N'` = UNREAD, `ACTV_YN = 'Y'` = READ.**
- Confirmed by the list "Unread" filter (`comm_0192_007_r001_act.jsp:80-82`: `if READ_TYPE == 'U' → and T.actv_yn = 'N'`) and the mark-read action which sets `ACTV_YN = YES` (`comm_0192_007_u001_act.jsp:118`).
- Frontend renders unread rows with the `active` CSS class (`val.ACTV_YN == "N" ? "active" : ""`, `comm_0192_007.ts:164`).
- Rows are **per-recipient** — the list is filtered to `bni.rcv_id = loginUser.getId()` (`comm_0192_007_r001_act.jsp:44-45`), so each user only sees and marks their own rows.

### 2.6 Notification count

- Count service `comm_0192_007_r002` returns `TOT_CNT` = number of **unread** rows (`comm_0192_007_r002_act.jsp:116`).
- The count IDO `BLTB_NOTI_INFM_R002_CNT` is the same UNION wrapped in `count(*)`, and its dynamic2 is hard-coded to **always** filter `T.actv_yn = 'N'` (`comm_0192_007_r002_act.jsp:62`) — i.e. the badge always counts unread, regardless of which tab is open.
- (The count WSVC declares an extra `TOT_APPRVR_CNT` out field — `WSVC.comm_0192_007_r002.xml:36` — but the action never populates it. Dead field; ignore in the refactor.)

### 2.7 Sorting / paging / filtering

- **Sort:** `ORDER BY T.reg_dtm DESC` (`comm_0192_007_r001_act.jsp:84`) — newest first.
- **Filter:** `SRCH_WD` → `T.snd_name ILIKE '%'||?||'%'` (sender-name contains, `comm_0192_007_r001_act.jsp:75-78`); `READ_TYPE='U'` → `T.actv_yn='N'`.
- **Paging:** `PageInfo` built from `PAGE_NO` + `PAGE_PER_CNT` (`comm_0192_007_r001_act.jsp:144-146`), applied via `JexUtils.setIDOPageInfo`. As noted in §1.3, the popup grows page size on scroll rather than incrementing page number.
- **Tenant:** every branch filters `biz_key = loginUser.getBizKey()` and the language join filters `lang_cd = JEX_LANG` cookie.

---

## 3. Legacy APIs — detailed

### 3.1 `comm_0192_007_r001` — list notifications

| Aspect | Detail |
|---|---|
| **Request** | `READ_TYPE` (req, `U`/`A`), `SRCH_WD` (opt), `PAGE_NO`, `PAGE_PER_CNT`, `LANG_CD` (declared but unused — language comes from the `JEX_LANG` cookie) |
| **Response** | `REC[]` with the 24 columns in §2.3 |
| **DB / IDO** | `BLTB_NOTI_INFM_R002` (UNION of Branch N + Branch G), executed via `IdoUtils.executeList` with optional `PageInfo` |
| **Dynamics** | `dynamic0` → Branch-N WHERE (`bni.biz_key`, `bni.rcv_id`, `bnci.lang_cd`); `dynamic1` → Branch-G WHERE (`gni.biz_key`, `gnci.lang_code`, `gnsri.rcv_id`); `dynamic2` → outer WHERE (`SRCH_WD` ILIKE, `actv_yn='N'` when Unread, `ORDER BY reg_dtm DESC`) |
| **Validation** | `ValidationHelper.validateRequired(READ_TYPE)` (`comm_0192_007_r001_act.jsp:33`) |
| **Error handling** | Standard JEX filter chain; `WbException` propagation; frontend checks `jexjs.isJexError` |
| **Side effects** | **None** (read-only). Post-processing rewrites `USER_IMG_URL` and normalizes `NOTI_TM` per-row in Java |

### 3.2 `comm_0192_007_r002` — unread count

| Aspect | Detail |
|---|---|
| **Request** | *(none — input message empty, `WSVC.comm_0192_007_r002.xml:32`)* |
| **Response** | `TOT_CNT` (string). `TOT_APPRVR_CNT` declared but never set — ignore |
| **DB / IDO** | `BLTB_NOTI_INFM_R002_CNT` — `count(*)` over the same UNION |
| **Dynamics** | same `dynamic0`/`dynamic1` as r001; `dynamic2` is fixed to `actv_yn='N'` (always unread) |
| **Validation** | none |
| **Error handling** | standard chain |
| **Side effects** | **None** (read-only) |

### 3.3 `comm_0192_007_u001` — mark read (single or bulk)

| Aspect | Detail |
|---|---|
| **Request** | `REC[]` — each item: `TYPE` (req), and for `N`: `BLTB_CD` + `NOTI_SEQ_NO` (req); for `G`: `GENL_NOTI_SEQ_NO` + `SND_ID` (req) (`WSVC.comm_0192_007_u001.xml:38-44`, validated `comm_0192_007_u001_act.jsp:52-63`) |
| **Response** | empty result (`{}`) — success is implied by no JEX error |
| **DB logic** | per REC item, inside one transaction (`comm_0192_007_u001_act.jsp:101`): for `TYPE='N'` → load `BltbNotiInfm` PK, validate it exists, set `ACTV_YN=YES` + `MOD_USER`, `BltbNotiInfmDAO.patch`; for `TYPE='G'` → same against `GenlNotiSndRcvInfo` (`RCV_ID` forced to the login user) |
| **Validation** | `ValidationHelper.validateRecord` + per-item required-field checks; `validateBltbNotiInfm` / `validateGenlNotiSndRcvInfo` assert the row exists before patching |
| **Error handling** | Multi-step write wrapped in `idoCon.beginTransaction()` → `IdoUtils.commitAndCloseQuietly`. Validation failure rolls back / throws JEX error |
| **Side effects** | **Mutates `ACTV_YN`** (`N`→`Y`). "Read All" passes every row in the list; single-read passes one row. The mark-read does NOT delete; deletion is a separate flow (`DEL_YN` is set elsewhere, e.g. approval cleanup) |

**Important behaviour:** mark-read on a `DEL_YN='Y'` row still succeeds (it just flips `ACTV_YN`); the frontend then shows the "notification was deleted" dialog (`WBI1098`). The list query does **not** exclude `DEL_YN='Y'` rows — deleted notifications remain visible with the deleted-flag, by design.

---

## 4. Refactor project check

### 4.1 What exists (verified on disk)

**Write side (present):**
- Entities `BltbNotiInfm` ([entity/BltbNotiInfm.java](../../src/main/java/com/kosign/wecms/domain/payment/notification/entity/BltbNotiInfm.java)) + `BltbNotiContInfm` + ids — mapped column-for-column to the legacy tables. Note `BltbNotiInfm` has both `ACTV_YN` **and** a `RCV_ID`/`RCV_NM` column (per-recipient model), `DEL_YN`, `NOTI_JSON`, `NOTI_WSVC`, `POST_SEQ_NO`.
- Repositories `BltbNotiInfmRepository` (only `softDeleteByPost(...)`) + `BltbNotiContInfmRepository` (no custom queries).
- `ApprovalNotificationService` ([service/ApprovalNotificationService.java](../../src/main/java/com/kosign/wecms/domain/payment/notification/service/ApprovalNotificationService.java)) — **write-only producer**: `notifyRequestApproval(...)`, `notifyApprovalAction(...)`, `cleanupApprovalArtifacts(...)` (soft-deletes `BLTB_NOTI_INFM` via `DEL_YN='Y'`). Consumed by 8 payment services (card, approval-box, internal-transfer, overseas, payroll, expense, vendor, fanout).

**Read side (MISSING):**
- ❌ No `NotificationController` — grep of `**/controller/*.java` for `notification`/`noti` = 0 hits.
- ❌ No list/count/mark-read service method.
- ❌ No repository query that lists `BLTB_NOTI_INFM` by recipient or counts unread.
- ❌ `GENL_NOTI_*` entities/repositories do not exist (correct — out of inventory, §2.4).

> Context: a *refactor-only* `domain/notification` package + a `NOTIFICATION` table were removed on 2026-06-10 (memory `project_notification_module_removed.md`) because legacy JEX has no such table. That removal is **unrelated** to the legacy `BLTB_NOTI_*` tables, which are correctly mapped and in scope. Do not resurrect a refactor-only notification table.

### 4.2 Frontend (present, calling missing endpoints)

- API client [lib/api/notification.ts](../../../wecms-web/lib/api/notification.ts) — four functions: `fetchNotifications({readType,page,size})`, `fetchUnreadCount()`, `markNotificationRead(id)`, `markAllNotificationsRead()`.
- Wire types [lib/types/api.ts:4086-4117](../../../wecms-web/lib/types/api.ts) — `ApiNotificationRow`, `ApiNotificationListResponse`, `ApiUnreadCountResponse`.
- Popup components under `wecms-web/components/layout/notifications/` (NotificationPopup, NotificationTabs, NotificationItem, NotificationSearch), mounted in the dashboard header.

### 4.3 ⚠️ Contract divergence — frontend invented a clean shape

The frontend wire `ApiNotificationRow` is a **reinvented clean contract** that does NOT match the legacy data model:

| Frontend wire field | Legacy equivalent | Notes |
|---|---|---|
| `notification_id: number` | composite PK `(BIZ_KEY, BLTB_CD, NOTI_SEQ_NO)` | **No single surrogate id exists in legacy.** Needs a synthesized id (e.g. `NOTI_SEQ_NO`) or a contract change |
| `noti_type: string` ("APPROVAL_REQUEST") | `TYPE` (`'N'`/`'G'`) + `BLTB_CD` | different vocabulary |
| `source_id: string\|null` | `APPR_SEQ_NO` (from `NOTI_JSON`) | partial overlap |
| `message_code` + `message_params` (i18n key + params, resolved client-side) | `TITL` + `CONT` (**pre-rendered localized text** in `BLTB_NOTI_CONT_INFM`) | **fundamental mismatch** — legacy stores already-translated strings per language; frontend expects an i18n key + params model |
| `link_target: string\|null` | `NOTI_WSVC` + `NOTI_JSON` / `NOTI_URL` | legacy carries a JSP act + JSON payload; frontend wants a refactor slug route |
| `read: boolean` | `ACTV_YN` (`Y`=read) | derivable (`ACTV_YN=='Y'`) |
| `read_at: string\|null` | *(no column)* | legacy has no read-timestamp column; `MOD_DTM` is the closest proxy |
| `reg_dtm` | `REG_DTM` | matches |

This divergence is the single biggest design decision (OD-A in §5). It cannot be silently reconciled — the team must choose whether the backend adapts to the frontend's clean shape (and how to source `message_code`/`message_params` when legacy only stores rendered text) **or** the frontend wire types + components flip to a legacy-faithful shape in the same MR (full-stack ownership rule).

---

## 5. Gap analysis

| # | Legacy behavior | Current refactor behavior | Gap | Severity | Required action | Files likely affected |
|---|---|---|---|---|---|---|
| G1 | List notifications for the logged-in recipient (`comm_0192_007_r001`) | No list endpoint | **Missing endpoint** | **HIGH** | Implement `GET /api/v1/notifications` over `BLTB_NOTI_INFM ⋈ BLTB_NOTI_CONT_INFM` (Branch N only) | new `NotificationController`, `NotificationService`, repository query, DTOs |
| G2 | Unread count for bell badge (`comm_0192_007_r002`) | No count endpoint | **Missing endpoint** | **HIGH** | Implement `GET /api/v1/notifications/unread-count` (`count WHERE ACTV_YN='N'`) | same service + repo `countByBizKeyAndRcvIdAndActvYn` |
| G3 | Mark one notification read (`comm_0192_007_u001`, single REC) | Only write-on-approval; no mark-read | **Missing endpoint** | **HIGH** | Implement `PUT /api/v1/notifications/{id}/read` → set `ACTV_YN='Y'`, `@Transactional` | service `markRead`, repo `@Modifying` update |
| G4 | "Read All" marks every listed row read (`u001`, REC[]) | None | **Missing endpoint** | **MEDIUM** | Implement `PUT /api/v1/notifications/read-all` → bulk `ACTV_YN='Y'` for recipient's unread rows | service `markAllRead`, repo bulk update |
| G5 | List UNIONs General (`GENL_NOTI_*`, `TYPE='G'`) notifications | No GENL tables; write side only produces Branch N | **Scope gap (out of inventory)** | **LOW (deferred)** | **Do NOT implement** Branch G; record PO decision (OD-B). Refactor list = Branch N only | n/a (escalation) |
| G6 | `TITL`/`CONT` pre-rendered localized text per `LANG_CD` | Frontend wire expects `message_code` + `message_params` (i18n keys) | **Contract mismatch** | **HIGH (blocking)** | Resolve OD-A: pick legacy-faithful (`title`/`content` strings) vs frontend-clean (i18n key) shape; align both sides in one MR | DTO + `wecms-web/lib/types/api.ts`, `lib/api/notification.ts`, popup components |
| G7 | Single surrogate `notification_id` does not exist (composite PK) | Frontend `markNotificationRead(notificationId: number)` | **Contract mismatch** | **HIGH (blocking)** | Resolve OD-A: either expose `NOTI_SEQ_NO` as the id + require `bltb_cd` on the read call, or change the mark-read request to carry the composite key | DTO + FE client/components |
| G8 | `USER_IMG_URL` → `szon_0012_003_r002.act?USER_ID=...` thumbnail; sender image from `USER_BUSI_CONN_INFM` | No refactor user-image endpoint resolved here | **Minor gap** | **LOW** | Resolve sender image via `USER_BUSI_CONN_INFM.USER_IMG`; emit a refactor slug image URL (or null). Mirror the approval-line `user_img_url` precedent | service mapping |
| G9 | `DEL_YN='Y'` rows stay listed; FE shows "deleted" (`WBI1098`) on click | n/a | **Behavior to preserve** | **MEDIUM** | List query must NOT exclude `DEL_YN='Y'`; emit `del_yn`/equivalent so FE can show the deleted dialog (or decide to filter — needs PO confirm) | DTO + service + FE |
| G10 | `READ_TYPE` U/A tab + `SRCH_WD` sender-name ILIKE + `reg_dtm DESC` | n/a | **Missing filter/sort logic** | **MEDIUM** | Implement `read_type` filter, `search` (sender-name contains), default `REG_DTM DESC` | service + repo |
| G11 | Datetime normalization `NOTI_DT+NOTI_TM` → user TZ | n/a | **Minor gap** | **LOW** | Return `reg_dtm` (and notification time) as `yyyyMMddHHmmss` UTC; FE formats (matches refactor convention) | service mapping |
| G12 | Per-recipient isolation: `bni.rcv_id = loginUser.id`, `biz_key` tenant filter | n/a | **Security-critical** | **HIGH** | Every query MUST filter `BIZ_KEY = TenantContext.currentBizKey()` AND `RCV_ID = TenantContext.currentUserId()` | repo + service |

---

## 6. Implementation plan (safe, step-by-step)

> **Pre-condition:** resolve OD-A (wire contract) and OD-B (`GENL_NOTI_*` scope) in §9 with the PO/lead **before** writing code. The steps below assume Branch-N-only scope and a chosen contract.

1. **Confirm scope + contract (no code).** Get PO sign-off on: (a) Branch N only — no `GENL_NOTI_*`; (b) the wire shape (legacy-faithful `title`/`content` vs frontend-clean `message_code`). This document is the input to that decision.
2. **Add error/message codes.** Add any new `WBExxxx` to `messages.properties` (e.g. `WBE_NOTIFICATION_NOT_FOUND` for a mark-read on a missing/other-tenant row). No raw strings.
3. **Repository read methods** on `BltbNotiInfmRepository` (new, all tenant + recipient scoped):
   - paged list by `(BIZ_KEY, RCV_ID)` with optional `ACTV_YN='N'` filter + sender-name `ILIKE`, `ORDER BY REG_DTM DESC` — join to `BLTB_NOTI_CONT_INFM` for the current `LANG_CD` title/content.
   - `countByBizKeyAndRcvIdAndActvYn(bizKey, rcvId, "N")`.
   - `@Modifying` update setting `ACTV_YN='Y'`, `MOD_ID`, `MOD_DTM` for one PK and for the bulk (all unread of the recipient).
   - Prefer a read-only `@Subselect` or a JPQL projection joining content; **do not** create a DB view (follow the `@Subselect`/projection precedent — `project_v_firm_banking_history_subselect.md`).
4. **DTOs** (Java records, camelCase → snake_case via global Jackson). See §7.
5. **`NotificationService`** — `list(readType, page, size, search)`, `unreadCount()`, `markRead(id)`, `markAllRead()`. Reads identity from `TenantContext` only. `@Transactional` on the two writes; reads non-transactional or `@Transactional(readOnly=true)`.
6. **`NotificationController`** — `@RequestMapping("/api/v1/notifications")`, four handlers returning `ApiResponse<T>`, gated by the standard JWT + `@menuAuthz` pattern (or documented allow-list if notifications has no menu node — confirm).
7. **Align the frontend wire types** in the same MR if OD-A picks the legacy-faithful shape (full-stack ownership). If OD-A keeps the frontend-clean shape, the service mapper does the translation and the FE is untouched.
8. **Unit + integration tests** (§8).
9. **No DB schema change. No Flyway migration.** `BLTB_NOTI_INFM` / `BLTB_NOTI_CONT_INFM` / `USER_BUSI_CONN_INFM` already exist in the schema baseline. `ddl-auto=validate` stays. (If the read path needs a `@Subselect` entity, it is schema-validate-exempt — no DDL.)
10. **Verify** against the running app + the live frontend popup; confirm unread count, tab filter, search, mark-read, read-all, and the deleted-row behaviour (G9).

---

## 7. Recommended API design

Endpoints match the existing refactor style (`/api/v1/{slug}`, `ApiResponse<T>` envelope, lowercase snake_case wire, `@Transactional` writes, `TenantContext` for identity).

### 7.1 Endpoints

| Method | Path | Returns | Legacy mirror |
|---|---|---|---|
| GET | `/api/v1/notifications?read_type=U\|A&page&size&search` | `ApiResponse<NotificationListResponse>` | `comm_0192_007_r001` |
| GET | `/api/v1/notifications/unread-count` | `ApiResponse<UnreadCountResponse>` | `comm_0192_007_r002` |
| PUT | `/api/v1/notifications/{notificationId}/read` | `ApiResponse<Void>` | `comm_0192_007_u001` (single) |
| PUT | `/api/v1/notifications/read-all` | `ApiResponse<Void>` | `comm_0192_007_u001` (REC[]) |

> The path/query shape above **matches what the frontend already calls** ([lib/api/notification.ts:15-18, 57-62](../../../wecms-web/lib/api/notification.ts)). The `read_type` default is `A`, `page` default `0` (Spring zero-based), `size` default `50`.

### 7.2 DTOs (camelCase Java → snake_case wire)

Two options, pending OD-A. **Recommended: align to the frontend-clean shape already on the wire** (least churn — FE is built), with the mapper sourcing values from `BLTB_NOTI_*`:

```java
// Matches the existing ApiNotificationRow wire (lib/types/api.ts:4086).
public record NotificationRow(
    Long notificationId,                 // <- NOTI_SEQ_NO (see OD-A/G7: may need bltb_cd too)
    String notiType,                     // <- derived from TYPE/BLTB_CD (e.g. "APPROVAL_REQUEST")
    String sourceId,                     // <- APPR_SEQ_NO (from NOTI_JSON)
    String messageCode,                  // <- see OD-A: legacy stores rendered TITL/CONT, not a code
    Map<String,String> messageParams,    // <- jsonb; null in v1 if title/content are pre-rendered
    String linkTarget,                   // <- refactor slug from NOTI_WSVC/NOTI_JSON, or null
    boolean read,                        // <- ACTV_YN == 'Y'
    String readAt,                       // <- MOD_DTM proxy (legacy has no read_at), or null
    String regDtm                        // <- REG_DTM (yyyyMMddHHmmss UTC)
) {}

public record NotificationListResponse(
    List<NotificationRow> list,
    long totalCount, long unreadCount,
    int pageNumber, int pageSize, int totalPages
) {}

public record UnreadCountResponse(long totalCount) {}
```

> If OD-A picks the **legacy-faithful** shape instead, replace `messageCode`/`messageParams` with `title`/`content` (strings) and add `delYn`/`sndName`/`userImgUrl` — and flip `wecms-web` wire types + components in the same MR.

**`message_code` caveat (G6):** legacy `BLTB_NOTI_CONT_INFM` stores already-translated `TITL`/`CONT` per `LANG_CD`. There is no stored i18n *code*. So a faithful v1 cannot populate a real `message_code`; the honest options are (i) return the rendered `title`/`content` strings (legacy-faithful), or (ii) introduce a synthetic code mapping the producer's `BLTB_CD`/`NOTI_WSVC` to a refactor i18n key (extra work, must cover every producer). **Recommend (i)** for v1 fidelity; revisit codes later.

### 7.3 Service methods + repository queries + transaction boundaries

```java
// NotificationService
@Transactional(readOnly = true)
NotificationListResponse list(String readType, int page, int size, String search);
//   repo: paged BLTB_NOTI_INFM ⋈ BLTB_NOTI_CONT_INFM(lang=currentLangCd)
//         WHERE BIZ_KEY=:biz AND RCV_ID=:user
//         [AND ACTV_YN='N' when readType='U']
//         [AND SND_NAME ILIKE %:search%]
//         ORDER BY REG_DTM DESC   (+ NOTI_SEQ_NO DESC tiebreaker)
//   (DEL_YN='Y' NOT excluded — G9, pending OD confirm)

@Transactional(readOnly = true)
UnreadCountResponse unreadCount();
//   repo: countByBizKeyAndRcvIdAndActvYn(biz, user, "N")

@Transactional
void markRead(Long notificationId);     // + bltbCd if OD-A requires composite key
//   repo @Modifying: SET ACTV_YN='Y', MOD_ID=:user, MOD_DTM=now
//        WHERE BIZ_KEY=:biz AND RCV_ID=:user AND NOTI_SEQ_NO=:id [AND BLTB_CD=:cd]
//   0 rows updated -> WBE_NOTIFICATION_NOT_FOUND (merges not-found + wrong-tenant)

@Transactional
void markAllRead();
//   repo @Modifying: SET ACTV_YN='Y', ... WHERE BIZ_KEY=:biz AND RCV_ID=:user AND ACTV_YN='N'
```

- **Tenant + recipient scoping is mandatory on every query** (G12): `BIZ_KEY = TenantContext.currentBizKey()`, `RCV_ID = TenantContext.currentUserId()`. Never accept recipient/biz_key from the request.
- **Language** from `TenantContext.currentLangCd()` for the content join (replaces the legacy `JEX_LANG` cookie).
- **Key generation:** none — read/update only, no inserts. (`wb_get_next_val` funnel not involved.)

---

## 8. Testing plan

**Unit (service, Mockito):**
1. `list` — Unread tab returns only `ACTV_YN='N'` rows for the current `(biz_key, rcv_id)`, newest first.
2. `list` — All tab returns read + unread; `search` applies sender-name `ILIKE`; paging respects `page`/`size`.
3. `list` — language join picks the `BLTB_NOTI_CONT_INFM` row for `currentLangCd`; missing content row → graceful null/empty title (no crash).
4. `unreadCount` — counts only `ACTV_YN='N'` for the current recipient; returns 0 when none.
5. `markRead` — flips `ACTV_YN` `N`→`Y`, stamps `MOD_ID`/`MOD_DTM`; idempotent (already-read → still success, 1 or 0 rows).
6. `markRead` — unknown id OR row belonging to another `(biz_key, rcv_id)` → `WBE_NOTIFICATION_NOT_FOUND` (no cross-tenant leak).
7. `markAllRead` — flips all unread for the recipient; returns success even when none unread.

**Integration (`@SpringBootTest` + seeded `BLTB_NOTI_*`):**
8. **Load list** — GET returns the envelope with `list`/`total_count`/`unread_count`/paging; ordering `REG_DTM DESC`.
9. **Load count** — GET `/unread-count` matches the seeded unread total.
10. **Mark read** — PUT `/{id}/read` then GET shows `read=true` and `unread_count` decremented by 1.
11. **Empty result** — recipient with zero notifications → `list=[]`, `total_count=0`, `unread_count=0` (no 404, HTTP 200).
12. **Deleted/inactive (G9)** — a `DEL_YN='Y'` row is still listed (and flagged) — confirm it is not silently dropped (or, if PO decides to filter, confirm exclusion).
13. **Legacy table compatibility** — entity maps to the unmodified `BLTB_NOTI_INFM` / `BLTB_NOTI_CONT_INFM` columns; `ddl-auto=validate` boots clean; no schema/DDL change; a row written by `ApprovalNotificationService` is readable by the new list endpoint (write↔read round-trip).
14. **Tenant isolation** — a notification for tenant A / user X is invisible to tenant B / user Y on list, count, and mark-read.

---

## 9. Open decisions (must resolve before implementation)

| ID | Decision | Options | Recommendation | Owner |
|---|---|---|---|---|
| **OD-A** | Wire contract: legacy-faithful vs frontend-clean | (1) keep the built FE shape (`message_code`/`message_params`, single `notification_id`) and map in the service; (2) flip FE wire types + components to a legacy-faithful `title`/`content` + composite-key shape | **Hybrid:** keep the FE endpoint shape, but for v1 return rendered `title`/`content` (rename `message_code`→ accept rendered text) — and decide the `notification_id` story (G7). Needs a short FE+BE sync. | PO + lead (FE+BE) |
| **OD-B** | `GENL_NOTI_*` (Branch G / general notifications) | (1) drop — out of inventory (recommended, matches write side); (2) widen the inventory baseline + port 3 tables (PO approval required) | **Drop for v1.** Refactor produces no Branch-G rows anyway. | PO (Mr. Jung) |
| **OD-C** | `notification_id` identity (G7) | (1) expose `NOTI_SEQ_NO` as id + require `bltb_cd` on mark-read; (2) per-tenant surrogate | (1) — keep composite-key semantics; add `bltb_cd` to the read request | lead (FE+BE) |
| **OD-D** | `DEL_YN='Y'` rows (G9) | (1) keep listed + flag (legacy parity); (2) filter out | (1) — preserve legacy behaviour; emit a `del_yn` flag so FE can show the deleted dialog | PO |
| **OD-E** | `message_code` model (G6) | (1) return rendered text (v1); (2) build a producer→i18n-key map | (1) for v1; revisit when a real code catalogue exists | lead |
| **OD-F** | Authorization gate | (1) `@menuAuthz` if notifications has a menu node; (2) authenticated-only allow-list | confirm whether a notifications menu permission exists; default to authenticated-only | lead |

---

## 10. Conclusion

**The refactor Notifications popup API is NOT complete — it is entirely missing on the backend and must be implemented.**

- The **frontend is done** and is calling four endpoints (`GET /notifications`, `GET /notifications/unread-count`, `PUT /notifications/{id}/read`, `PUT /notifications/read-all`) that **do not exist** in `wecms-api`.
- The backend has only the **write/producer side** (`ApprovalNotificationService` populating `BLTB_NOTI_INFM` / `BLTB_NOTI_CONT_INFM`). The **read/count/mark-read** layer (controller + service + repository queries + DTOs) is greenfield.
- Implementation is **source-code-only** and uses the **existing legacy tables** — `BLTB_NOTI_INFM`, `BLTB_NOTI_CONT_INFM`, `USER_BUSI_CONN_INFM` (all in the inventory). **No DB schema change, no Flyway migration** is needed.
- The legacy General-notification branch (`GENL_NOTI_*`) is **out of refactor scope** (not in the inventory) and should be dropped for v1 (OD-B) — which is consistent with the refactor never producing Branch-G rows.
- Two **blocking contract decisions** (OD-A wire shape, OD-C identity) must be settled with the PO/lead before any code is written, because the frontend invented a clean shape that does not line up with the legacy data model.

No code or schema was changed by this analysis.

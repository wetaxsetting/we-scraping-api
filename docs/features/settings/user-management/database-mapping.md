# User Management — Database Mapping (backend)

Back to [README](README.md) · [functional-specification](functional-specification.md) · [source-code-index](source-code-index.md)

Column shapes below were verified against the live refactor R&D DB
(`172.168.5.172:5432/wecmsdevdb`) on 2026-06-05. Schema mirrors legacy JEX column-for-column
per the Legacy Schema Parity Rule; ERD context: [Settings ERD](../../../references/database/erd/settings-erd.md).

---

## 1. Core tables

| Table | Role | PK | Created by |
|---|---|---|---|
| `USER_INFM` | User master (global account) | `USER_ID` | V1 / V7 (auth schema) |
| `USER_BUSI_CONN_INFM` | User ↔ company link (tenant membership, avatar, soft-delete) | `(USER_ID, BIZ_KEY)` | V1 |
| `BUSI_ADMN_INFM` | Per-(company,user) admin authority — presence = admin | `(BIZ_KEY, USER_ID)` | V11 |
| `USER_MENU_AUTH_INFM` | Per-user menu-entitlement grants (Y-only) | `(BIZ_KEY, USER_ID, WEB_SVC_ID)` | **V131** |
| `ACCT_AUTH_INFO` | Per-user bank-account permission rows (revoked on unlink) | `(BIZ_KEY, USER_ID, BANK_CD, ACCT_NO)` | (banking schema) |

Legacy tables intentionally **not** ported into the refactor user module:
`USER_MENU_ACTION_AUTH_INFM` (action-level CRUD grants), `USER_PW_CHG_HIS` (password reuse history),
`EMPL_INFM`/`BUSI_ADMN_ACTV_HIS`/`BUSI_ADMN_LOGIN_HIS`. See
[refactor-gap-analysis.md](refactor-gap-analysis.md).

## 2. `USER_INFM` (master) — verified columns

| Column | Type | Null | Entity field (`UserInfm`) | Notes |
|---|---|:---:|---|---|
| `USER_ID` | varchar(100) | NO | `userId` | PK / login id |
| `USER_NM` | varchar(100) | NO | `userNm` | |
| `USER_PW` | varchar(100) | NO | `userPw` | Written as legacy SHA-256 base64 (`WecmsPasswordEncoder`); BCrypt tolerated on read only; never serialized |
| `EML` | varchar(200) | YES | `eml` | (DTO/UI cap at 100; column is 200) |
| `CLPH_NO` | varchar(100) | YES | `clphNo` | Plain in refactor; legacy DEV is AES-encrypted |
| `POSI_NM` | varchar(100) | YES | `posiNm` | Position |
| `DEPT_NM` | varchar(100) | YES | `deptNm` | Department |
| `ACTV_YN` | varchar(1) | NO | `actvYn` | `isActive()` |
| `LOCK_YN` | varchar(1) | NO | `lockYn` | `isLocked()` |
| `DEL_YN` | varchar(1) | NO | `delYn` | `isDeleted()` (global delete — not used by per-tenant unlink) |
| `USER_IMG` | varchar(500) | YES | `userImg` | Legacy avatar slot; refactor uses the link table's column instead |
| `LST_LGN_DATE` / `MOB_LST_LGN_DATE` | timestamptz | YES | `lstLgnDate` / `mobLstLgnDate` | |
| `PW_CHG_REQ_SEQ_NO` | numeric | YES | `pwChgReqSeqNo` | Mapped, unused (password-expiry deferred) |
| `BIZ_KEY` | varchar(20) | YES | `bizKey` | Legacy primary-company hint; not used by refactor logic |
| `EMPL_NO` | varchar(20) | YES | `emplNo` | |
| `REG_ID/REG_DTM/MOD_ID/MOD_DTM` | varchar(100)/timestamptz | — | (BaseEntity) | Audit; refactor uses TIMESTAMPTZ (legacy DEV is varchar(14)) |

## 3. `USER_BUSI_CONN_INFM` (tenant link)

| Column | Type | Null | Entity field | Notes |
|---|---|:---:|---|---|
| `USER_ID` | varchar(100) | NO | `userId` | PK part |
| `BIZ_KEY` | varchar(20) | NO | `bizKey` | PK part — the tenant |
| `EMPL_NO` | varchar(30) | YES | `emplNo` | |
| `USER_IMG` | varchar(500) | YES | `userImg` | **Avatar save-filename token (refactor avatar storage)** |
| `LST_LGN_DATE` / `MOB_LST_LGN_DATE` | timestamptz | YES | … | |
| `DEL_YN` | varchar(1) | NO | `delYn` | `'Y'` = unlinked from this tenant (soft delete) |
| `REG_*/MOD_*` | … | — | (BaseEntity) | |

## 4. `BUSI_ADMN_INFM` (admin authority)

| Column | Type | Null | Entity field | Notes |
|---|---|:---:|---|---|
| `BIZ_KEY` | varchar(20) | NO | `bizKey` | PK part |
| `USER_ID` | varchar(100) | NO | `userId` | PK part |
| `USER_NM` | varchar(100) | NO | `userNm` | Denormalized display name |
| `REG_*/MOD_*` | varchar(100)/timestamptz | — | (BaseEntity) | |

Presence of a row = the user is admin of that company. Toggled by create/update; deleted on demote
and on per-tenant unlink (guarded by the last-admin rule).

## 5. `USER_MENU_AUTH_INFM` (menu entitlement, V131)

| Column | Type | Null | Entity field (`UserMenuAuthInfm`) | Notes |
|---|---|:---:|---|---|
| `BIZ_KEY` | varchar(20) | NO | `bizKey` | PK part |
| `USER_ID` | varchar(100) | NO | `userId` | PK part |
| `WEB_SVC_ID` | varchar(50) | NO | `menuAuthCd` | PK part — repurposed to hold the **kebab-case menu slug** (e.g. `settings-core-users`) |
| `USE_YN` | varchar(1) | YES | `useYn` | Y-only storage — only granted rows exist (DDL default `'N'`) |
| `DISP_ORD` | numeric(10) | YES | (unmapped) | Legacy display-order; not mapped by the refactor entity |
| `MOD_ID/MOD_DTM` | varchar(100)/varchar(14) | YES | `modId`/`modDtm` | Mod-only — table has **no** `REG_*` columns; entity does not extend `BaseEntity` |

Index `ix_user_menu_auth_biz_user (BIZ_KEY, USER_ID)` backs load + delete-all-on-save.

> **Slug, not legacy WSVC id.** Legacy `USER_MENU_AUTH_INFM.WEB_SVC_ID` stored JEX ids
> (`comm_0190`, `fnsh_0001`, …). The refactor repurposes that same `WEB_SVC_ID` column to store the
> kebab-case **menu slug** (Java field `menuAuthCd`) per the workspace identifier-naming rule,
> consumed by the
> [Menu Authorization feature](../../../../../docs/features/menu-authorization/README.md).

## 6. `ACCT_AUTH_INFO` (account permissions, revoked on unlink)

PK `(BIZ_KEY, USER_ID, BANK_CD, ACCT_NO)`, plus `AUTH_TYPE` varchar(1), `USE_YN` varchar(1), and
legacy `REG_DTM`/`MOD_DTM` as varchar(14). `delete()` calls
`AcctAuthInfoRepository.revokeAllByBizKeyAndUserId(bizKey, userId)` so an unlinked user loses their
per-account grants.

## 7. Column journey: legacy → entity → DTO → UI

| Concept | Legacy column | Refactor entity field | Wire (DTO) | UI field (`SettingsUser`) |
|---|---|---|---|---|
| User ID / Login | `USER_INFM.USER_ID` | `UserInfm.userId` | `user_id` | `id` / `loginId` |
| User Name | `USER_INFM.USER_NM` | `userNm` | `user_nm` | `name` |
| Email | `USER_INFM.EML` | `eml` | `eml` | `email` |
| Phone | `USER_INFM.CLPH_NO` (`decrypt`) | `clphNo` | `clph_no` | `phoneNumber` (+ hardcoded `phoneCountryCode="84"`) |
| Department | `USER_INFM.DEPT_NM` | `deptNm` | `dept_nm` | `department` |
| Position | `USER_INFM.POSI_NM` | `posiNm` | `posi_nm` | `position` |
| User Status (active) | `USER_INFM.ACTV_YN` | `actvYn` | — (list filtered to active) | — |
| User Status (locked) | `USER_INFM.LOCK_YN` | `lockYn` | — | — |
| User Status (deleted) | `USER_BUSI_CONN_INFM.DEL_YN` | `delYn` | — (filtered) | — |
| Role / Admin Flag | `BUSI_ADMN_INFM` row presence | (resolved set) | `is_admin` | `role` (`admin`/`user`) |
| Avatar | `USER_BUSI_CONN_INFM.USER_IMG` | `userImg` | `user_img_url` | `imageUrl` |
| Password | `USER_INFM.USER_PW` (`hash`) | `userPw` | `user_pw` (write-only) | masked `************` |
| Current user marker | (actor compare) | — | `is_current_user` | `isCurrentUser` |
| Created / Updated | `REG_DTM` / `MOD_DTM` | BaseEntity | — (not on wire) | — |
| Menu grants | `USER_MENU_AUTH_INFM.WEB_SVC_ID` | `UserMenuAuthInfm.menuAuthCd` | `menu_auth_cds[]` | entitlement tree slugs |

## 8. Soft-delete & status semantics

- **Per-tenant unlink** (this screen's delete): `USER_BUSI_CONN_INFM.DEL_YN='Y'`. The user
  disappears from the tenant list and cannot authenticate against it; `USER_INFM` survives.
- **Global delete** (`USER_INFM.DEL_YN='Y'`): not performed from this screen.
- **Active / locked** (`ACTV_YN` / `LOCK_YN`): read by the auth login gates; this screen does not
  toggle them (deferred — see gap analysis).
- The list query hard-filters `USER_BUSI_CONN_INFM.DEL_YN='N'`, so unlinked users never appear and
  there is no "trash"/reactivation UI.

## 9. Migration

| Version | Summary |
|---|---|
| `V131__user_menu_auth_infm.sql` | `CREATE TABLE user_menu_auth_infm` (PK `(BIZ_KEY,USER_ID,WEB_SVC_ID)`) + index `ix_user_menu_auth_biz_user`. No legacy DEV dependency. |

All other tables predate this feature (auth/bootstrap/banking schema). No `ALTER` was needed for the
user CRUD/avatar paths — they reuse existing columns. JEX→Spring mapping narrative:
[migration-guide.md](migration-guide.md).

# User Management — Business Flow (backend)

Back to [README](README.md) · [functional-specification](functional-specification.md) · [interface-specification](interface-specification.md)

Describes the server-side behaviour of each flow. UI sequencing lives in the frontend
[screen-flow](../../../../../wecms-web/docs/features/settings/user-management/screen-flow.md).

---

## 0. Tenant & identity context

Every flow runs inside a JWT-bound request. The service layer reads identity exclusively
from `TenantContext` (populated by `JwtAuthenticationFilter` from the signed token):

- `TenantContext.currentBizKey()` — the company the action is scoped to.
- `TenantContext.currentUserId()` — the acting user (the "actor").
- `TenantContext.currentIsAdmin()` — `authGrCd == "ADMIN"`, stamped at login from `BUSI_ADMN_INFM`.

A request body / query param can never override the tenant. All reads and writes constrain
on `BIZ_KEY = currentBizKey()`.

---

## 1. List users

1. Authorize: `canViewUsers()` (ADMIN or `settings-core-users` grant).
2. `UserManagementService.list()` runs `UserBusiConnInfmRepository.findTenantUsersForManagement(bizKey)` —
   a JPQL join `USER_BUSI_CONN_INFM ⋈ USER_INFM` filtered to `DEL_YN='N'`, ordered
   `REG_DTM DESC, USER_ID ASC` (newest link first).
3. One batch `BusiAdmnInfmRepository` lookup resolves the admin-id set for the tenant (avoids N+1).
4. Each row is projected to `UserRowResponse`, stamping `is_admin` (in the admin set) and
   `is_current_user` (`userId == actor`). `user_img_url` is composed from `USER_BUSI_CONN_INFM.USER_IMG`.
5. Response: `{ users: [...], total_count }`. **`total_count` is display metadata only — there is no plan cap.**

## 2. View a user (detail)

1. Authorize: `canViewUsers()`.
2. `detail(userId)` loads the active tenant link
   (`findByUserIdAndBizKeyAndDelYn(userId, bizKey, "N")`); missing → `WBE_USERS_NOT_FOUND`.
3. Joins `USER_INFM` for profile fields + admin-set check → `UserRowResponse`.

## 3. Create a user

`UserManagementService.create(req)` — `@Transactional`, ADMIN-gated (controller + `requireAdmin()`):

1. **Global uniqueness:** `UserInfmRepository.existsByUserId(userId)` — `USER_INFM.USER_ID` is a
   platform-wide PK; if taken → `WBE_USERS_DUPLICATE_LOGIN_ID`.
2. **Insert `USER_INFM`:** profile fields + password hashed as legacy SHA-256 base64 (`WecmsPasswordEncoder`, for JEX coexistence), `ACTV_YN='Y'`,
   `LOCK_YN='N'`, `DEL_YN='N'`.
3. **Insert `USER_BUSI_CONN_INFM`:** `(userId, bizKey)`, `DEL_YN='N'` — the tenant link.
4. **Optional admin row:** if `set_as_admin=true`, insert `BUSI_ADMN_INFM (bizKey, userId, userNm)`.
5. Return the created `UserRowResponse`.

All four steps are one transaction; a PK race on `USER_INFM` surfaces as
`WBE_USERS_DUPLICATE_LOGIN_ID`.

> **Production note:** in live WeCMS, accounts are normally provisioned by the DBA/ops team
> via SQL (see [login-wecms.md](../../../../../docs/jex/features/login-wecms.md)). This screen
> nonetheless supports full create end-to-end in the refactor.

## 4. Update a user

`update(userId, req)` — `@Transactional`, ADMIN-gated:

1. Load active tenant link → `WBE_USERS_NOT_FOUND` if absent.
2. Update `USER_INFM` profile fields (`USER_NM`, `EML`, `CLPH_NO`, `POSI_NM`, `DEPT_NM`).
3. **Optional password:** if `user_pw` non-blank, re-encode as legacy SHA-256 base64 (`WecmsPasswordEncoder`) and store; blank/absent → keep existing hash.
4. **Admin toggle** (`set_as_admin`):
   - `true`  + no admin row → insert `BUSI_ADMN_INFM`.
   - `false` + admin row present → **last-admin guard** (`countByBizKey > 1`, else
     `WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN`), then delete the admin row.
   - otherwise no-op.
5. `USER_ID` (login id) is immutable — it is the PK and the path variable; it is never updated.

## 5. Delete a user (per-tenant unlink)

`delete(userId)` — `@Transactional`, ADMIN-gated. Soft, tenant-scoped:

1. **Self-delete guard:** `userId == actor` → `WBE_USERS_CANNOT_DELETE_SELF`.
2. If the user is an admin: **last-admin guard** then `DELETE FROM BUSI_ADMN_INFM (bizKey,userId)`.
3. `UPDATE USER_BUSI_CONN_INFM SET DEL_YN='Y'` for `(userId, bizKey)`.
4. `ACCT_AUTH_INFO` rows for `(bizKey, userId)` are revoked (`revokeAllByBizKeyAndUserId`).
5. `USER_INFM` master row is **left intact** — the user no longer appears in this tenant and no
   longer authenticates against it, but the global account survives (multi-tenant safety).

## 6. Menu entitlement (per-user menu grants)

Backed by `MenuEntitlementService` over `USER_MENU_AUTH_INFM`. **Y-only storage** — only granted
rows exist; absence means denied.

**Load** (`GET .../{userId}/menu-entitlement`): return every `USE_YN='Y'`
`WEB_SVC_ID` slug for `(bizKey, userId)` as `{ user_id, menu_auth_cds: [...] }`.

**Save** (`PUT .../{userId}/menu-entitlement`, ADMIN-gated): the body carries the **full desired
set**. `saveInTransaction` does delete-then-insert:
1. Defence-in-depth admin guard.
2. Verify the tenant-user link exists → `WBE_USERS_NOT_FOUND`.
3. `deleteAllByBizKeyAndUserId(...)`, flush, then insert one `USE_YN='Y'` row per deduped slug.
4. A concurrent-save race (`DataIntegrityViolationException`) is caught and retried once in a fresh transaction.

Granted slugs are then consumed at login by the
[Menu Authorization feature](../../../../../docs/features/menu-authorization/README.md) to gate
which menu items a non-admin user sees. Admins bypass entitlement filtering entirely.

## 7. Avatar upload / remove / stream

Backed by `UserImageService`; the file token lives in `USER_BUSI_CONN_INFM.USER_IMG` (per-tenant link).

- **Upload** (`POST .../{userId}/image`, multipart, ADMIN): validate filename / size (≤ 2 MB) /
  extension (jpg, jpeg, png, gif, svg) → write an opaque SHA-256-named file under the storage root →
  update `USER_IMG` → delete the previous file. Returns the refreshed `UserRowResponse`.
- **Delete** (`DELETE .../{userId}/image`, ADMIN): clear `USER_IMG`, delete the disk file (idempotent).
- **Stream** (`GET .../{userId}/image`, `canViewUsers()`): stream the bytes; `WBE2113` when no avatar
  is set (frontend treats this as "render placeholder"), `WBE2114` when the file is missing on disk.

## 8. Check login-id availability

`GET .../check-id?login_id=…` (ADMIN): `WBE_USERS_LOGIN_ID_REQUIRED` if blank, else
`{ available: !existsByUserId(loginId) }` — a **global** check (PK is platform-wide, not per-tenant).

---

## Permission matrix (implemented)

| Capability | Admin | Viewer (`settings-core-users` grant, non-admin) |
|---|:---:|:---:|
| View user list | ✅ | ✅ |
| Open user detail | ✅ | ✅ |
| Stream avatar | ✅ | ✅ |
| Create user | ✅ | ❌ 403 |
| Update user / reset password | ✅ | ❌ 403 |
| Toggle admin flag | ✅ | ❌ 403 |
| Delete (unlink) user | ✅ | ❌ 403 |
| Check id availability | ✅ | ❌ 403 |
| Read/write menu entitlement | ✅ | ❌ 403 |
| Upload/remove avatar | ✅ | ❌ 403 |

A user with **neither** ADMIN nor the `settings-core-users` grant cannot reach the panel at all —
the frontend renders a placeholder and the read endpoints return 403. See the read-only viewer
behaviour in the frontend [ui-specification](../../../../../wecms-web/docs/features/settings/user-management/ui-specification.md).

> **Enforcement note.** Every controller method stacks `@menuAuthz.canAccess('settings-core-users')`
> (the F-3 menu-authorization guard) on top of the read/write gate named in each flow above. The
> `canViewUsers()` / `requireAdmin()` semantics described per-flow are unchanged — the `@menuAuthz`
> term is additive and is satisfied implicitly for an ADMIN (full-tree navigation). Exact SpEL per
> endpoint: [interface-specification §1](interface-specification.md#1-endpoints).

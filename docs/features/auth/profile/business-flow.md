# Auth > Profile — Business Flow (Backend)

End-to-end request flow for the auth-profile surface. Source of truth: the implemented Spring code in
`com.kosign.wecms.domain.auth`
([UserController](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/UserController.java)
→ [UserDirectoryService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/UserDirectoryService.java)).

```
Approval-line / signer picker (frontend)
      │   (every request carries the JWT; UserController is JWT-only — no @menuAuthz)
      ▼
GET /api/v1/users[?q=<name substring>]
      │
      ▼
UserController.list(q)            @Validated; @Size(max=100) on q
      │
      ▼
UserDirectoryService.list(q)      @Transactional(readOnly)
      │  ├─ requireBizKey() ← TenantContext.currentBizKey()   (tenant scope, never a param)
      │  ├─ safeQ = (q blank ? "" : q.trim())
      │  ├─ userBusiRepo.findTenantUserPicker(bizKey, safeQ)   (JOIN USER_INFM, del_yn='N', q ILIKE)
      │  └─ map TenantUserProjection → TenantUserRow
      ▼
ApiResponse<TenantUserListResponse>  { total_count, users[] }   (lowercase snake_case envelope)
```

---

## 1. Tenant user directory — `GET /api/v1/users`

```
GET /api/v1/users[?q=]
  → UserController.list(q)
      validate q ≤ 100 chars (@Size; @Validated on the class) — over-length → WBE_VALIDATION
  → UserDirectoryService.list(q)  @Transactional(readOnly)
      1. bizKey = TenantContext.currentBizKey()   (WBE9999 if null — configuration-bug guard, not a normal path)
      2. safeQ  = q null/blank ? "" : q.trim()
      3. rows   = userBusiRepo.findTenantUserPicker(bizKey, safeQ)
            JPQL: FROM UserBusiConnInfm c JOIN UserInfm u ON u.userId = c.userId
                  WHERE c.bizKey = :bizKey
                    AND c.delYn  = 'N'                         (active links only)
                    AND (:q IS NULL OR :q = '' OR LOWER(u.userNm) LIKE LOWER('%'||:q||'%'))
                  ORDER BY u.userNm ASC, u.userId ASC
            SELECT u.userId, u.userNm, u.posiNm, u.deptNm, c.userImg   (interface projection — 5 cols)
      4. per row → TenantUserRow:
            user_img_url = (c.userImg non-blank) ? "/api/v1/users/{userId}/image" : null
            (USER_IMG value itself NEVER reaches the wire — only the presence flag drives the URL)
  → TenantUserListResponse { total_count = rows.size(), users[] }
```

- Mirrors legacy `comm_0192_001_r001` ("Inquiry Approver") — IDO `USER_BUSI_CONN_INFM_R004`
  (`IDO.USER_BUSI_CONN_INFM_R004.xml:24-62`) joining `USER_INFM`. The legacy `ui.user_nm ilike '%'||?||'%'`
  filter is reproduced verbatim as the `q` semantic.
- **No server-side pagination** — the directory is small; legacy returns the whole result set in one
  shot, and `TenantUserListResponse.total_count` is simply the row count (no "X of Y" windowing).
- **Read-only.** No `AUDIT_LOGS_INFM` write, no mutation. The endpoint is allow-listed for `@menuAuthz`
  coverage and gated only by the JWT.

### Consumers

The first consumer is the Approval Line "Add Approver" dropdown; future approval-execution / signer
pickers reuse the same endpoint + DTO. None of them edit a user — they read the directory to choose an
approver.

---

## 2. Current session identity — `GET /api/v1/auth/me` (cross-link)

The session's own identity (`user_id`, `user_nm`, `biz_key`, `cmpn_nm`, `tax_id`, `auth_gr_cd`,
`prdt_type`, menu grants) is returned by `GET /api/v1/auth/me` via `AuthService.me()`, which reads
`TenantContext` and `BUSI_INFM`. That flow is documented in the **session-management** sibling module —
it is the "who am I" half of the profile surface and is intentionally not duplicated here. `/me` does
**not** return the user's avatar bytes or allow profile edits.

---

## 3. Out of auth-profile scope (cross-reference)

### Avatar image CRUD — APPROVED DEVIATION (Settings > Users)

Avatar upload / get / delete is served by **Settings > Users**
([UserManagementController](../../../../src/main/java/com/kosign/wecms/domain/setting/users/controller/UserManagementController.java)):

```
POST   /api/v1/settings/users/{user_id}/image   (multipart "file") → UserImageService.upload
DELETE /api/v1/settings/users/{user_id}/image                       → UserImageService.delete
GET    /api/v1/settings/users/{user_id}/image                       → streams bytes (WBE2113 no avatar / WBE2114 missing file)
```

These are **admin-gated** (`hasRole('ADMIN') and @menuAuthz.canAccess('settings-core-users')` for
write; the read GET follows the read policy `@userMgmtAccess.canViewUsers() and @menuAuthz...`). They
live in `domain/setting/users`, write `USER_BUSI_CONN_INFM.USER_IMG`, and are **out of auth scope** —
documented under [Settings > Users](../../settings/user-management/README.md). The auth directory only
**reads** the `USER_IMG` presence flag to decide whether to emit `user_img_url`. **Status: APPROVED
DEVIATION** (the management surface, not auth, owns avatar bytes).

### Self-profile edit — MISSING

There is **no** auth endpoint for a user to edit their own profile (no `PUT /api/v1/auth/me`, no
self-service avatar update). The frontend ProfileMenu surfaces an avatar-edit pencil, but it is a
**UI-only placeholder** — no handler, no endpoint. A user's profile / avatar is changed through the
admin-driven Settings > Users panel. **Status: MISSING** in auth scope (the FE pencil should be wired
to a future self-service endpoint or hidden). Tracked in
[refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## Legacy parity (`comm_0192_001_r001`)

| Legacy behaviour | Refactor | Parity |
|---|---|---|
| Inquiry-Approver list, name `ilike` filter, whole result set | `GET /api/v1/users` + `q` filter, no pagination | **MATCH** |
| Order by user name | `ORDER BY USER_NM ASC, USER_ID ASC` (id tiebreaker) | **MATCH** |
| `NORM_USER_ONLY_YN = Y`, no per-menu grant | JWT-only, allow-listed, no `@menuAuthz` | **MATCH** |
| `CLPH_NO` phone (legacy `decrypt()` DB fn) | not surfaced — picker has no phone column | **DEAD LEGACY** (avoids a new legacy-fn dependency) |
| `ADMIN_YN` / `MOB_YN` sub-flags (from R004 IDO) | not surfaced — not consumed by the picker | **DEAD LEGACY** |
| (legacy has no self-profile edit on this WSVC) | self-profile edit absent | **MISSING** (refactor gap, not a legacy regression) |

---

## Tenant + read invariants

- **Tenant** comes only from `TenantContext.currentBizKey()` (signed JWT) — never a request
  body / param. It is the `c.bizKey = :bizKey` predicate of the projection query; the route carries no
  `bizKey`.
- **Active only** — `c.delYn = 'N'` filters soft-deleted tenant links; a user removed from the tenant
  never appears.
- **No write path** — the endpoint never mutates a row and never writes an audit entry. The
  `USER_IMG` value is read for presence only and never echoed onto the wire.

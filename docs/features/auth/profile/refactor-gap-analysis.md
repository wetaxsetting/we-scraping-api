# Auth > Profile — Refactor Gap Analysis (Parity Review)

Legacy JEX (`comm_0192_001_r001` "Inquiry Approver" + IDO `USER_BUSI_CONN_INFM_R004`, tables
`USER_INFM` / `USER_BUSI_CONN_INFM` / `BUSI_INFM`) vs. the refactor (`profile` — `GET /api/v1/users`).
Status vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Tenant user directory** | `comm_0192_001_r001` → `USER_BUSI_CONN_INFM_R004` (join `USER_INFM`) | `GET /api/v1/users` → `UserDirectoryService.list` (JPQL projection) | **MATCH** |
| **Name filter** | `user_nm ilike '%'||?||'%'` | `q` → `LOWER(USER_NM) LIKE LOWER('%'||:q||'%')`, ≤100 chars | **MATCH** |
| **Active-only / soft-delete** | `del_yn = 'N'` | `c.delYn = 'N'` | **MATCH** |
| **Tenant scope** | session `BIZ_KEY` | `TenantContext.currentBizKey()` (JWT, never a param) | **MATCH** |
| **Ordering** | by user name | `USER_NM ASC, USER_ID ASC` (id tiebreaker) | **MATCH** |
| **Pagination** | none (whole set) | none (`total_count` = row count) | **MATCH** |
| **Authorization** | `NORM_USER_ONLY_YN = Y`, menu visibility | JWT-only, `UserController` allow-listed (no `@menuAuthz`) | **MATCH** |
| **Avatar reference** | raw `USER_IMG` on `R004` | presence flag → derived `user_img_url` (`/api/v1/users/{userId}/image`); raw never on wire | **MATCH** (shape adaptation) |
| **Phone `CLPH_NO`** | decrypted via legacy `decrypt()` DB fn | not surfaced (no phone column on picker) | **DEAD LEGACY** (GAP-1) |
| **`ADMIN_YN` / `MOB_YN` sub-flags** | carried on `R004` | not surfaced (not consumed) | **DEAD LEGACY** (GAP-2) |
| **Current session identity (`/me`)** | session identity | `GET /api/v1/auth/me` (`AuthService.me`) | **MATCH** (documented in session-management) |
| **Avatar image up/get/del** | inline JEX avatar handling | relocated to Settings > Users (`UserImageService`) | **APPROVED DEVIATION** (GAP-3) |
| **Self-service "edit my profile"** | *(no such WSVC endpoint on `comm_0192_001`)* | no auth endpoint; FE avatar-edit pencil UI-only | **MISSING** (GAP-4) |
| **Avatar streaming endpoint at the slug URL** | — | `user_img_url` points at `/api/v1/users/{userId}/image`; auth-side streaming endpoint not yet built (Settings > Users path serves bytes today) | **PARTIAL** (GAP-5) |
| **Tests** | — | no dedicated `UserController` / `UserDirectoryService` JUnit on disk | **MISSING** (GAP-6) |

---

## Specially-verified gaps

### GAP-1 — Phone `CLPH_NO` not surfaced (DEAD LEGACY)

Legacy `USER_BUSI_CONN_INFM_R004` carries `CLPH_NO`, decrypted via the legacy `decrypt()` DB function.
The refactor directory projection (`findTenantUserPicker`) deliberately omits it — the approval-line /
signer pickers have no phone column, and porting a new legacy-function dependency for an unused field
is not justified (documented in `UserDirectoryService` / `TenantUserRow` Javadoc). **Status: DEAD
LEGACY** — intentionally excluded. (Settings > Users *does* surface `CLPH_NO` on its management grid via
a separate projection; that is a different consumer.)

### GAP-2 — `ADMIN_YN` / `MOB_YN` sub-flags not surfaced (DEAD LEGACY)

The two sub-flags inherited from the broader `R004` IDO are not consumed by the picker and are not
projected. **Status: DEAD LEGACY** — intentionally excluded.

### GAP-3 — Avatar image CRUD relocated to Settings > Users (APPROVED DEVIATION)

Avatar upload / get / delete is owned by **Settings > Users**
([UserManagementController](../../../../src/main/java/com/kosign/wecms/domain/setting/users/controller/UserManagementController.java)
+ `UserImageService`), admin-gated, writing `USER_BUSI_CONN_INFM.USER_IMG`. It is intentionally **not**
in the auth surface — the auth directory only reads the `USER_IMG` presence flag to emit
`user_img_url`. This matches the workspace boundary (user-management actions are a Settings concern, not
an authentication concern). **Status: APPROVED DEVIATION.** Full docs:
[../../settings/user-management/README.md](../../settings/user-management/README.md).

### GAP-4 — Self-service "edit my profile" auth endpoint (MISSING)

There is **no** backend endpoint for a user to edit their own profile from the auth surface (no
`PUT /api/v1/auth/me`, no self-service avatar update). The frontend ProfileMenu shows an avatar-edit
pencil, but it is a **UI-only placeholder** with no handler / endpoint. Profile / avatar changes route
through the admin-driven Settings > Users panel. **Status: MISSING** — the FE pencil should be wired to
a future self-service endpoint or hidden. Owner: backend + FE + PO (is self-service profile edit a
product requirement?).

### GAP-5 — Auth-side avatar streaming endpoint (PARTIAL)

`user_img_url` emits the slug-based template `/api/v1/users/{userId}/image`, but the streaming endpoint
behind that exact path is an Approval Line follow-up MR (OQ-1). Today the avatar bytes are served by the
Settings > Users path (`/api/v1/settings/users/{user_id}/image`); the frontend `<Avatar>` fallback
tolerates a 404 on the auth slug URL until the auth-side endpoint lands. **Status: PARTIAL** — URL shape
present, the auth `GET /api/v1/users/{userId}/image` handler is not yet on disk.

### GAP-6 — No dedicated tests (MISSING)

No `UserControllerTest` / `UserDirectoryServiceTest` on disk in this pass. The only coverage touching
this surface is `EndpointAuthorizationCoverageTest`, which asserts `UserController` is allow-listed
(JWT-only / no-`@menuAuthz` posture intentional). **Status: MISSING** — see
[test-scenarios.md](test-scenarios.md) for the tests to add (filter, tenant scope, active-only,
`user_img_url` derivation).

---

## Migration status

```
Refactor Status: complete for the in-scope directory surface (GET /api/v1/users);
                 self-profile-edit (GAP-4) MISSING; auth-side avatar stream (GAP-5) PARTIAL;
                 dedicated tests (GAP-6) MISSING.
```

Evidence for closed gaps:

- **Directory endpoint implemented** ([UserController](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/UserController.java)
  + [UserDirectoryService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/UserDirectoryService.java)),
  legacy `comm_0192_001_r001` parity (filter, active-only, tenant scope, ordering, JWT-only).
- **`user_img_url` derivation** — `USER_IMG` presence drives the URL; the raw value never reaches the
  wire (`UserDirectoryService.toRow`).
- **Scope boundary documented** — `/me` (session-management), avatar CRUD (Settings > Users) cross-linked.

Residual (tracked):
1. GAP-4 — decide + build a self-service "edit my profile" endpoint (or hide the FE pencil).
2. GAP-5 — build the auth-side `GET /api/v1/users/{userId}/image` streaming endpoint (Approval Line OQ-1).
3. GAP-6 — add `UserDirectoryServiceTest` + a `@WebMvcTest` `UserControllerTest`.

None of the residual items affect the implemented directory endpoint's parity or correctness.

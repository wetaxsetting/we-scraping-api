# User Management — Source Code Index

Back to [README](README.md) · [migration-guide](migration-guide.md)

Maps legacy JEX sources ↔ refactor backend ↔ refactor frontend. Paths are repo-relative.
Cross-system master index: [`docs/features/user-management/source-code-index.md`](../../../../../docs/features/user-management/source-code-index.md).

---

## 1. Legacy JEX (read-only reference — `WECMS DEV/`)

The legacy screen is **`comm_0190_*`** (common user management), NOT `admn_0021_*` (the latter is
subscriber/business admin in the separate Jex Admin portal, `SOURCE_CODE_ADMIN/`).

| WSVC / file | Path (under `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/`) | Purpose |
|---|---|---|
| `comm_0190_001_r001` | `comm_0190_001_r001_act.jsp` | List users for a business |
| `comm_0190_002_c001` / `_c002` | `comm_0190_002_c001_act.jsp` | Create user (+ business link) |
| `comm_0190_003_r001..r003` | `comm_0190_003_r001_act.jsp` | User detail for edit form |
| `comm_0190_003_u001..u005` | `comm_0190_003_u001_act.jsp` | Update user — profile, password, **admin flag**, employee sync |
| `comm_0190_003_d001` | `comm_0190_003_d001_act.jsp` | Soft-delete user |

IDO query XML (under `WECMS DEV/WECMS_ETC/xml/service/IDO/`):

| IDO | Purpose |
|---|---|
| `IDO.USER_INFM_R001..R011 / C001 / U001..U005 / D001` | User master CRUD (`decrypt(CLPH_NO)`, `hash(USER_PW)`) |
| `IDO.USER_BUSI_CONN_INFM_R001..R004 / C001 / U000..U001` | User-business link CRUD (`USER_IMG`, `EMPL_NO`) |
| `IDO.BUSI_ADMN_INFM_R001 / C001 / U001 / D001` | Admin flag CRUD (upsert / deleteByPk) |
| `IDO.USER_MENU_AUTH_INFM_R001..R002 / C001 / U000..U002 / D002` | Menu-level grants (`WEB_SVC_ID`, `USE_YN`, `DISP_ORD`) |
| `IDO.USER_MENU_ACTION_AUTH_INFM_*` | Action-level (CRUD) grants — **not ported** |

Legacy DAOs/helpers: `wabooks.dao.{UserInfmDAO, UserBusiConnInfmDAO, BusiAdmnInfmDAO,
EmplInfmDAO, UserPwChgHisDAO}`, `wabooks.helper.PasswordHelper`.

## 2. Refactor backend (`wecms-api/`)

Package root: `src/main/java/com/kosign/wecms/domain/setting/users/`.

### Controller
| File | Notes |
|---|---|
| `controller/UserManagementController.java` | 11 endpoints; split `@PreAuthorize` (read vs write) |

### Services
| File | Responsibility |
|---|---|
| `service/UserManagementService.java` | CRUD, admin toggle, self-delete + last-admin guards, BCrypt, phone sanitization, `ACCT_AUTH_INFO` revoke, `requireAdmin()` |
| `service/MenuEntitlementService.java` | Load / delete-then-insert save of `USER_MENU_AUTH_INFM`; race retry |
| `service/UserImageService.java` | Avatar upload/delete/stream; 2 MB cap; opaque SHA-256 filename; extension allow-list |
| `service/UserManagementAccessPolicy.java` | `@Component("userMgmtAccess")` — `canViewUsers()` (ADMIN or `settings-core-users` grant) |

### Repositories
| File | Key methods |
|---|---|
| `domain/auth/repository/UserInfmRepository.java` | `existsByUserId` |
| `domain/auth/repository/UserBusiConnInfmRepository.java` | `findByUserIdAndBizKeyAndDelYn`, `findTenantUsersForManagement` (mgmt grid projection), `countByBizKeyAndDelYn` |
| `domain/auth/repository/BusiAdmnInfmRepository.java` | `existsByBizKeyAndUserId`, `countByBizKey`, `findAllByBizKey` |
| `domain/setting/users/repository/UserMenuAuthInfmRepository.java` | `findAllByBizKeyAndUserId`, `existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn`, `deleteAllByBizKeyAndUserId` |
| `domain/banking/.../AcctAuthInfoRepository.java` | `revokeAllByBizKeyAndUserId` |

### DTOs (`dto/`)
`UserCreateRequest`, `UserUpdateRequest`, `MenuEntitlementUpdateRequest` (requests);
`UserRowResponse`, `UserListResponse`, `CheckLoginIdResponse`, `MenuEntitlementResponse` (responses).
Projections: `TenantUserManagementProjection`, `TenantUserProjection` (on the link repository).

### Entities
| File | Table |
|---|---|
| `domain/auth/entity/UserInfm.java` | `USER_INFM` |
| `domain/auth/entity/UserBusiConnInfm.java` (+ `UserBusiConnInfmId`) | `USER_BUSI_CONN_INFM` |
| `domain/auth/entity/BusiAdmnInfm.java` (+ `BusiAdmnInfmId`) | `BUSI_ADMN_INFM` |
| `domain/setting/users/entity/UserMenuAuthInfm.java` (+ `UserMenuAuthInfmId`) | `USER_MENU_AUTH_INFM` |

### Security components
- `@PreAuthorize` SpEL `@userMgmtAccess.canViewUsers()` / `hasRole('ADMIN')`.
- `TenantContext.currentIsAdmin()` / `currentBizKey()` / `currentUserId()`.
- `JwtAuthenticationFilter` populates `ROLE_ADMIN`/`ROLE_USER` from the `authGrCd` claim.
- `WecmsPasswordEncoder` (BCrypt write, BCrypt/legacy-SHA-256 read).

### Migration & messages
- `src/main/resources/db/migration/V126__user_menu_auth_infm.sql`
- `src/main/resources/messages.properties` (`WBE_USERS_*`, `WBE211x`).

### Tests
- `src/test/java/com/kosign/wecms/domain/setting/users/service/UserManagementAuthorizationTest.java`
- `src/test/java/com/kosign/wecms/domain/setting/users/service/UserManagementAccessPolicyTest.java`

## 3. Refactor frontend (`wecms-web/`)

Detailed in the frontend [component-index](../../../../../wecms-web/docs/features/settings/user-management/component-index.md). Key files:

| Layer | File |
|---|---|
| Mount | `components/settings/SettingsModal.tsx` (`panel === "user"`) |
| Container | `components/settings/users/UsersPanel.tsx` |
| List | `components/settings/users/UsersListPanel.tsx`, `UserListItem.tsx` |
| Detail (read-only) | `components/settings/users/UserDetailView.tsx` |
| Create / Update | `components/settings/users/AddUserInfoForm.tsx`, `EditUserInfoForm.tsx` |
| Entitlement | `components/settings/users/UserMenuEntitlementPanel.tsx`, `mockMenuEntitlements.ts` |
| Avatar | `components/settings/users/UserAvatarUpload.tsx`, `UserAvatar.tsx` |
| API client | `lib/api/userManagement.ts` |
| Wire types | `lib/types/api.ts` (`ApiUserMgmt*`), `components/settings/users/types.ts` (`SettingsUser`) |

## 4. Legacy → refactor mapping (one line each)

```
comm_0190_001_r001_act.jsp        → GET  /api/v1/settings/users           (UserManagementService.list)
comm_0190_002_c001_act.jsp        → POST /api/v1/settings/users           (create)
comm_0190_003_r001_act.jsp        → GET  /api/v1/settings/users/{id}       (detail)
comm_0190_003_u001_act.jsp        → PUT  /api/v1/settings/users/{id}       (update + admin toggle)
comm_0190_003_d001_act.jsp        → DELETE /api/v1/settings/users/{id}     (per-tenant unlink)
IDO.USER_MENU_AUTH_INFM_*         → GET/PUT .../{id}/menu-entitlement      (MenuEntitlementService)
IDO.USER_BUSI_CONN_INFM_U001(IMG) → POST/DELETE/GET .../{id}/image         (UserImageService)
IDO.BUSI_ADMN_INFM_{C001,D001}    → set_as_admin toggle inside create/update
```

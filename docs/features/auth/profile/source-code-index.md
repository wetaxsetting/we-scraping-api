# Auth > Profile — Source Code Index

Legacy ↔ refactor file map for the auth-profile surface. JEX paths are **read-only reference**.
Frontend mirror:
[wecms-web/docs/features/auth/profile/component-index.md](../../../../../wecms-web/docs/features/auth/profile/component-index.md)
*(FE folder pending)*.

---

## Refactor — backend (`wecms-api`)

### Owned by `domain/auth` (auth-profile surface)

| Layer | File |
|---|---|
| Controller | [controller/UserController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/UserController.java) — `GET /api/v1/users`; `@Validated`; JWT-only (no `@menuAuthz`) |
| Service | [service/UserDirectoryService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/UserDirectoryService.java) — `list(q)`; tenant-scoped projection → rows; read-only |
| Response DTO (envelope) | [dto/TenantUserListResponse.java](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/TenantUserListResponse.java) — `{ total_count, users }` |
| Response DTO (row) | [dto/TenantUserRow.java](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/TenantUserRow.java) — `{ user_id, user_nm, posi_nm, dept_nm, user_img_url }` |
| Repository + projection | [repository/UserBusiConnInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserBusiConnInfmRepository.java) — `findTenantUserPicker(bizKey, q)` + `TenantUserProjection` (reused auth repo) |

### Entities read (auth package, reused — not re-implemented)

| Concern | File |
|---|---|
| User master (name / position / department) | [entity/UserInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java) |
| Tenant link + `USER_IMG` flag + soft-delete | [entity/UserBusiConnInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfm.java) + [entity/UserBusiConnInfmId.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfmId.java) |
| Company identity (backs `/me`, cross-link) | [entity/BusiInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiInfm.java) |

### Cross-package collaborators

| Concern | File | Package |
|---|---|---|
| Tenant scope (`currentBizKey`) | [TenantContext.java](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | `common/tenant` |
| Missing-tenant guard (`WBE9999`) | [BusinessException.java](../../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) | `common/exception` |

### Schema / config

- `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM` — legacy tables, in
  [legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md). **No
  auth-profile migration, no key generation** (read-only surface).

### Tests

No dedicated `UserControllerTest` / `UserDirectoryServiceTest` on disk in this pass — see
[test-scenarios.md §2](test-scenarios.md). `UserController` is allow-listed in
[EndpointAuthorizationCoverageTest](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java)
(deny-or-declared coverage), which asserts the JWT-only / no-`@menuAuthz` posture is intentional.

---

## Referenced — Settings > Users image endpoints (NOT auth scope)

These live in `domain/setting/users` and are **out of auth-profile scope** — referenced for the
boundary, not documented here. Full docs:
[../../settings/user-management/README.md](../../settings/user-management/README.md).

| Endpoint | File | Method |
|---|---|---|
| `POST /api/v1/settings/users/{user_id}/image` (upload) | [UserManagementController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/controller/UserManagementController.java) `#uploadImage` | admin-gated |
| `DELETE /api/v1/settings/users/{user_id}/image` (clear) | `UserManagementController#deleteImage` | admin-gated |
| `GET /api/v1/settings/users/{user_id}/image` (stream bytes) | `UserManagementController#streamImage` | read policy (`@userMgmtAccess.canViewUsers()` + `@menuAuthz('settings-core-users')`) |
| Avatar storage / resolve | [service/UserImageService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/service/UserImageService.java) · [UserImageResolver.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/service/UserImageResolver.java) | writes/reads `USER_BUSI_CONN_INFM.USER_IMG` |

The auth directory only **reads** the `USER_IMG` presence flag (to emit `user_img_url`); it never
writes or streams the bytes.

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Path |
|---|---|
| Inquiry-Approver read action | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0192_001_r001_act.jsp` |
| Inquiry-Approver IDO (join `USER_INFM`) | `WECMS_ETC/xml/service/IDO/IDO.USER_BUSI_CONN_INFM_R004.xml:24-62` |
| Approval-line add-flow analysis (design rationale) | `docs/refactor-wecms/migration/approval-line-add-flow-analysis.md` (deprecated tree) |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/profile/`
- Sibling auth modules: [login](../login-auth.md) · [logout](../logout/README.md) ·
  [change-password](../change-password/README.md) · session-management *(see `/me`)*
- Settings > Users (avatar CRUD + self-profile mgmt):
  [../../settings/user-management/README.md](../../settings/user-management/README.md)
- Frontend feature folder:
  [wecms-web/docs/features/auth/profile/README.md](../../../../../wecms-web/docs/features/auth/profile/README.md)
  *(pending)*

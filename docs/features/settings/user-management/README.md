# User Management — Backend Feature (wecms-api)

**Slug:** `user-management`
**Domain:** `settings`
**System:** Refactor — Spring Boot 3.5.13 backend
**Wire base path:** `/api/v1/settings/users`
**Java package:** `com.kosign.wecms.domain.setting.users`
**Status:** ✅ **Implemented — full-stack, legacy parity verified (2026-06-05)**

---

## 1. What this feature is

The admin-facing **Settings → Users** screen: list the users linked to the current
company (tenant), open a user's profile, create users, edit profile / password / admin
flag, soft-delete (per-tenant unlink), manage per-user **menu entitlement** grants, and
upload / remove a user **avatar**.

This folder is the **authoritative backend home** for the feature. It documents the real,
shipped Spring Boot implementation — superseding the earlier flat plan
[`../users.md`](../users.md) (2026-05-14), which described a since-abandoned design
(`/api/v1/users` base path, a hard 5-user plan cap, and a "Menu Entitlement deferred"
scope). Both of those were dropped before release; see
[refactor-gap-analysis.md](refactor-gap-analysis.md) §"Plan-vs-shipped drift".

## 2. Document map

| Doc | Purpose |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flows: list → view → create → update → delete → entitle → avatar; permission matrix |
| [functional-specification.md](functional-specification.md) | Field-level behaviour, validation rules, business rules, error handling |
| [database-mapping.md](database-mapping.md) | Tables, columns, legacy→entity→DTO→UI column map, soft-delete semantics |
| [interface-specification.md](interface-specification.md) | Every REST endpoint: method, path, auth, request/response DTO, error codes |
| [source-code-index.md](source-code-index.md) | Legacy JEX files ↔ refactor controller/service/repository/DTO/entity, with file:line |
| [migration-guide.md](migration-guide.md) | How the JEX `comm_0190_*` behaviour was ported to Spring Boot |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios — CRUD, permission, read-only viewer, validation, security |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Legacy vs refactor parity table + plan-vs-shipped drift + open questions |

## Legacy & Migration Traceability

**JEX functional reference:** legacy `comm_0190_001 / _002 / _003` WSVCs (NOT `admn_0021_*` — that is subscriber/business admin in the Jex Admin portal)

---

## 3. Related docs (cross-system — preserved, not duplicated)

- **Cross-system functional bundle** (authoritative business behaviour, both systems):
  [`docs/features/user-management/`](../../../../../docs/features/user-management/README.md)
  — functional-specification, business-flow, database-mapping, interface-specification,
  source-code-index, migration-guide, test-scenarios, refactor-gap-analysis (SEC-1 fix
  recorded 2026-06-05).
- **Frontend feature folder:** [`wecms-web/docs/features/settings/user-management/`](../../../../../wecms-web/docs/features/settings/user-management/README.md)
- **Menu Authorization (consumption side)** — how granted `USER_MENU_AUTH_INFM` rows gate
  menu visibility: [`docs/features/menu-authorization/`](../../../../../docs/features/menu-authorization/README.md)
- **Permission model (shared):** [`docs/_shared/permission-roles.md`](../../../../../docs/_shared/permission-roles.md)
- **Authorization rules (shared):** [`docs/security/authorization.md`](../../../../../docs/security/authorization.md)
- **JEX as-is account provisioning context:** [`docs/jex/features/login-wecms.md`](../../../../../docs/jex/features/login-wecms.md)
- **Settings index:** [`../README.md`](../README.md)
- **Superseded plan (kept for history):** [`../users.md`](../users.md)

## 4. Implementation at a glance

| Layer | Artifact |
|---|---|
| Controller | `UserManagementController` (`domain/setting/users/controller/`) — 11 endpoints |
| Services | `UserManagementService`, `MenuEntitlementService`, `UserImageService`, `UserManagementAccessPolicy` |
| Repositories | `UserInfmRepository`, `UserBusiConnInfmRepository`, `BusiAdmnInfmRepository`, `UserMenuAuthInfmRepository`, `AcctAuthInfoRepository` |
| Entities | `UserInfm`, `UserBusiConnInfm` (+Id), `BusiAdmnInfm` (+Id), `UserMenuAuthInfm` (+Id) |
| Tables | `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_ADMN_INFM`, `USER_MENU_AUTH_INFM`, `ACCT_AUTH_INFO` |
| Migration | `V131__user_menu_auth_infm.sql` (creates `USER_MENU_AUTH_INFM`) |
| Tests | `UserManagementAuthorizationTest`, `UserManagementAccessPolicyTest` |

## 5. Authorization summary (SEC-1, revised 2026-06-05)

| Action class | Gate |
|---|---|
| **Read** (list, detail, avatar stream) | `@PreAuthorize("@userMgmtAccess.canViewUsers() and @menuAuthz.canAccess('settings-core-users')")` — ADMIN **or** the user holding the `settings-core-users` menu grant |
| **Write** (create, update, delete, check-id, menu-entitlement, avatar upload/delete) | `@PreAuthorize("hasRole('ADMIN') and @menuAuthz.canAccess('settings-core-users')")` + a defence-in-depth service guard (`requireAdmin()` → `AccessDeniedException` → 403 / `WBE1003`) |

> Every controller method additionally stacks `@menuAuthz.canAccess('settings-core-users')` (the F-3
> menu-authorization guard) alongside the read/write gate above. For an ADMIN the navigation tree is
> the full product tree, so that term is satisfied implicitly.

There is **no separate "Manager" role** in the implemented model — see
[functional-specification.md §Permission model](functional-specification.md#permission-model).

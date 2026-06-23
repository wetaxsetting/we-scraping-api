# Settings > User Management — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/user-management/`

## Feature Overview
- **Purpose:** The admin-facing Settings → Users screen — list the users linked to the current company (tenant), open a user profile, create users, edit profile / password / admin flag, soft-delete (per-tenant unlink), manage per-user menu entitlement grants, and upload / remove a user avatar.
- **Business role:** A tenant administrator manages the company's user accounts and their menu access; read access additionally opens to a holder of the `settings-core-users` menu grant.

## Menu / Route Mapping
- **Menu / panel:** Settings → Users
- **Menu permission ID (slug):** `settings-core-users` — stacked on every endpoint via `@menuAuthz.canAccess('settings-core-users')` alongside the read/write role gate (reads: `@userMgmtAccess.canViewUsers()`; writes: `hasRole('ADMIN')` + a defence-in-depth service guard)
- **Wire base path:** `/api/v1/settings/users`
- **Frontend mount / route:** Settings modal panel (`UsersPanel`)
- **Legacy JEX counterpart:** WSVC family `comm_0190_001 / _002 / _003` (NOT `admn_0021_*` — that is subscriber/business admin in the Jex Admin portal) — legacy ids cited for traceability only.

## Current Status
- ✅ **Implemented:** Full-stack, legacy parity verified (2026-06-05). 11 endpoints under `/api/v1/settings/users` — list, check-id, detail, create, update, delete (soft / per-tenant unlink), menu-entitlement GET/PUT, and avatar upload/get/delete. Menu-entitlement persists in `USER_MENU_AUTH_INFM` (migration **V131**), grant slugs stored in the repurposed `WEB_SVC_ID` PK column (e.g. `settings-core-users`), Y-only, delete-then-insert. Passwords use the coexistence **SHA-256** scheme (legacy `public.hash()` format) on every write path, verify-only on login, **no upgrade** to BCrypt. Avatar upload hardened (2 MB cap, extension allow-list, opaque filename). Split authorization enforced at controller and service layers, covered by `UserManagementAuthorizationTest` + `UserManagementAccessPolicyTest`. Entities `UserInfm`, `UserBusiConnInfm`, `BusiAdmnInfm`, `UserMenuAuthInfm` (+ Ids).
- 🟡 **Partially implemented:** No `AUDIT_LOGS_INFM` write on mutations (OQ-DOC-UM-1). Action-level (CRUD-per-screen) entitlement (`USER_MENU_ACTION_AUTH_INFM`) not implemented — menu-level only (OQ-DOC-UM-2). `ACTV_YN` / `LOCK_YN` status not editable from this screen (OQ-DOC-UM-3). No password-reuse history (`USER_PW_CHG_HIS`) or 5-failure auto-lock (OQ-DOC-UM-4). Phone country code rendered by the picker but not round-tripped as a distinct wire field (OQ-DOC-UM-5).
- ⚪ **Deprecated / Legacy / Not ported:** Employee sync (`EMPL_INFM` update on user edit) not implemented (out of scope). User-list pagination intentionally dropped (load-all + client search/sort — small per-tenant sets). No plan / user cap.

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [user-management/README.md](user-management/README.md) |
| Business flow | [user-management/business-flow.md](user-management/business-flow.md) |
| Database mapping | [user-management/database-mapping.md](user-management/database-mapping.md) |
| Functional specification | [user-management/functional-specification.md](user-management/functional-specification.md) |
| Interface specification | [user-management/interface-specification.md](user-management/interface-specification.md) |
| Migration guide | [user-management/migration-guide.md](user-management/migration-guide.md) |
| Refactor gap analysis | [user-management/refactor-gap-analysis.md](user-management/refactor-gap-analysis.md) |
| Source code index | [user-management/source-code-index.md](user-management/source-code-index.md) |
| Test scenarios | [user-management/test-scenarios.md](user-management/test-scenarios.md) |

## Historical Design Notes (preserved)
Omitted — this flat file was the earlier (2026-05-14) implementation *plan* and its design was abandoned before release. Its deltas are already captured in the folder's [refactor-gap-analysis.md](user-management/refactor-gap-analysis.md) "Plan-vs-shipped drift" table. Stale facts now scrubbed: the `/api/v1/users` base path (shipped as `/api/v1/settings/users`); the hard **5-user plan cap** + `WBE_USERS_PLAN_LIMIT_REACHED` (no cap shipped — `total_count` is display only); the `WBE_USERS_PASSWORD_TOO_WEAK` custom code (weak/short passwords surface generic `WBE_VALIDATION`); "Menu Entitlement deferred / static placeholder" and "Avatar upload deferred" (both shipped); the admin-only-everywhere gate (reads were split open to the `settings-core-users` grant under SEC-1); and the flat file's `admn_0021_*` JEX reference (the real legacy counterpart is `comm_0190_*`).

# Auth > Profile — Backend Feature (Refactor)

**Status:** ✅ Backend implemented — 1 controller (`UserController`) + 1 service (`UserDirectoryService`) + 1 response DTO (`TenantUserListResponse` / `TenantUserRow`) over a JPQL projection on the existing auth repository. Read-only; no mutations, no audit writes. A dedicated self-service "edit my profile" endpoint is **MISSING** (see below).
**Module / menu:** Auth → **Profile** (user-identity surface — **no standalone menu / route**; the directory is consumed by approval-line / signer pickers)
**Refactor slug:** `profile` (matches the WEB counterpart; sibling auth modules `login` · `logout` · `change-password` · `session-management`)
**Wire base path:** **`/api/v1/users`** — `GET /api/v1/users` (tenant user directory; optional `q` filter)
**Auth model:** JWT only — **allow-listed** in `EndpointAuthorizationCoverageTest`, **no `@menuAuthz`** gate (parity with legacy `comm_0192_001_r001`, which carries `NORM_USER_ONLY_YN = Y` and no per-menu grant).
**Legacy JEX counterpart:** WSVC `comm_0192_001_r001` ("Inquiry Approver") — IDO `USER_BUSI_CONN_INFM_R004` joining `USER_INFM`. Tables `USER_INFM`, `USER_BUSI_CONN_INFM` (incl. `USER_IMG`), `BUSI_INFM` (for the `/me` identity surface).

> This folder is the documentation home for the **auth-adjacent "profile / user identity"** surface
> on the backend. Scope is deliberately narrow — see [§ Scope boundary](#scope-boundary). The JEX-side
> spec is the read-only source of truth for legacy behaviour; the implemented Spring code is the
> source of truth for current behaviour.

---

## What this feature is

Auth > Profile is the read-only **user-identity** surface that sits next to authentication:

- **Tenant user directory** — `GET /api/v1/users` returns the current tenant's **active** users
  (one row per active `USER_BUSI_CONN_INFM` link, joined to `USER_INFM` for name / position /
  department / avatar flag), with an optional case-insensitive `q` substring filter on `USER_NM`. It
  is the backend port of legacy `comm_0192_001_r001` ("Inquiry Approver") and feeds every
  approver / signer picker (Approval Line "Add Approver" first; future per-payment-type approval flows
  reuse it). JWT-only, no `@menuAuthz`.
- **The current session's own identity** is surfaced by `GET /api/v1/auth/me` — documented in the
  **session-management** sibling module, cross-linked here.

What it is **not**: avatar image CRUD (upload / get / delete) and per-user profile edit live under
**Settings > Users** (`domain/setting/users`), not auth — see [§ Scope boundary](#scope-boundary).

## Scope boundary

| Surface | Where it lives | In auth-profile scope? |
|---|---|---|
| Tenant user directory (`GET /api/v1/users`, `q` filter) | `domain/auth` (`UserController` / `UserDirectoryService`) | **Yes** — this folder |
| Current session identity (`GET /api/v1/auth/me`) | `domain/auth` (`AuthController` / `AuthService.me`) | Cross-ref — documented in **session-management** |
| Avatar image up/get/del (`/api/v1/settings/users/{user_id}/image`) | `domain/setting/users` (`UserManagementController` / `UserImageService`) | **No** — APPROVED DEVIATION, see [Settings > Users](../../settings/user-management/README.md) |
| Self-profile edit ("edit my profile") | — | **MISSING** — no auth endpoint; the FE avatar-edit pencil is UI-only |

The directory endpoint **reads** `USER_BUSI_CONN_INFM.USER_IMG` only to decide whether to emit a
`user_img_url` string (the per-tenant avatar source); the bytes are served by the Settings > Users
streaming endpoint, not by anything in this folder.

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 1 — `GET /api/v1/users` (see [interface-specification.md](interface-specification.md)) |
| Authority | JWT only; **no `@PreAuthorize` / `@menuAuthz`** — `UserController` is in `ALLOWLISTED_CONTROLLERS` (`EndpointAuthorizationCoverageTest`) |
| Tenant scope | `TenantContext.currentBizKey()` — `bizKey` is the join predicate; never a request param |
| Tables | `USER_INFM` (name/position/department), `USER_BUSI_CONN_INFM` (tenant link + soft-delete + `USER_IMG` flag); `BUSI_INFM` backs the `/me` identity (session-management) — all in the legacy inventory |
| Filter | optional `q` — case-insensitive `LOWER(USER_NM) LIKE %q%`, ≤100 chars; blank/null → whole active directory |
| Order | `USER_NM ASC, USER_ID ASC` |
| Mutations / audit | none — read-only, no `AUDIT_LOGS_INFM` writes |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout

```
com.kosign.wecms.domain.auth
├── controller/UserController.java            GET /api/v1/users; @Validated; JWT-only (no @menuAuthz)
├── service/UserDirectoryService.java         list(q) — tenant-scoped projection → rows; read-only
├── dto/
│   ├── TenantUserListResponse.java           { total_count, users: TenantUserRow[] }
│   └── TenantUserRow.java                     { user_id, user_nm, posi_nm, dept_nm, user_img_url }
└── repository/UserBusiConnInfmRepository.java findTenantUserPicker(bizKey, q) + TenantUserProjection (reused auth repo)
```

Cross-package reuse: `common/tenant/TenantContext` (tenant scope), `common/exception/BusinessException`
(`WBE9999` on a missing tenant — a configuration-bug guard, never expected in normal flow).

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | Request flow for `GET /api/v1/users`; legacy parity (`comm_0192_001_r001`); self-profile-edit MISSING; avatar CRUD APPROVED DEVIATION (Settings > Users) |
| [functional-specification.md](functional-specification.md) | Endpoint behaviour (filter, tenant scope, active-users-only), the explicit auth-profile vs Settings > Users vs `/me` scope boundary, field contract, error surface |
| [database-mapping.md](database-mapping.md) | `USER_INFM` / `USER_BUSI_CONN_INFM` (`USER_IMG`) / `BUSI_INFM` (for `/me`) columns → projection alias → DTO field → UI field |
| [interface-specification.md](interface-specification.md) | REST contract — `GET /api/v1/users`, query param `q`, response JSON (lowercase snake_case), error-code registry, authorization |
| [source-code-index.md](source-code-index.md) | Refactor + legacy file map; the Settings > Users image endpoints referenced (not fully documented) |
| [migration-guide.md](migration-guide.md) | Legacy `comm_0192_001_r001` → `UserController` layer-by-layer mapping + intentional divergences |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with status + evidence |
| [test-scenarios.md](test-scenarios.md) | Directory filter / tenant-scope scenarios + coverage notes (tests MISSING) |

---

## Related & cross-referenced docs

- **Sibling auth modules:** [login](../login-auth.md) · [logout](../logout/README.md) ·
  [change-password](../change-password/README.md) · session-management *(see `/me` — module folder
  pending; identity surface lives there)*.
- **Settings > Users** (avatar CRUD + self-profile management — APPROVED DEVIATION, out of auth scope):
  [../../settings/user-management/README.md](../../settings/user-management/README.md).
- **Frontend counterpart:**
  [wecms-web/docs/features/auth/profile/README.md](../../../../../wecms-web/docs/features/auth/profile/README.md)
  *(FE folder pending; the ProfileMenu avatar-edit pencil is a UI-only placeholder — see gap analysis)*.
- **Legacy table inventory (baseline):**
  [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md).

---

## Open / residual items

1. **Self-service "edit my profile" auth endpoint — MISSING.** No `PUT /api/v1/auth/me` or equivalent;
   the frontend ProfileMenu avatar-edit pencil has no handler / endpoint. Profile edits route through
   Settings > Users (admin-driven). Tracked in [refactor-gap-analysis.md](refactor-gap-analysis.md).
2. **Avatar streaming endpoint** `GET /api/v1/users/{userId}/image` referenced by `user_img_url` is the
   Settings > Users path (`/api/v1/settings/users/{user_id}/image`) in the current tree; the directory
   emits the slug-based `/api/v1/users/{userId}/image` template and the FE `<Avatar>` tolerates a 404
   until the auth-side streaming endpoint lands (Approval Line OQ-1).
3. **No dedicated `UserController` / `UserDirectoryService` JUnit on disk** — see
   [test-scenarios.md](test-scenarios.md).

None of the residual items affect the implemented directory endpoint's parity or correctness.

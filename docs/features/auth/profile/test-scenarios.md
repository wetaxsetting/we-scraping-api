# Auth > Profile — Test Scenarios (Backend)

SIT/UAT scenarios + coverage map for the auth-profile surface (`GET /api/v1/users`). Frontend
scenarios: [wecms-web/docs/features/auth/profile/test-scenarios.md](../../../../../wecms-web/docs/features/auth/profile/test-scenarios.md)
*(FE folder pending)*.

---

## 1. SIT / UAT scenarios

### Tenant user directory — filter

| # | Scenario | Expected |
|---|---|---|
| D1 | `GET /api/v1/users` no `q` | every **active** tenant user (`DEL_YN='N'`), ordered `user_nm ASC, user_id ASC`; `total_count` = row count |
| D2 | `GET /api/v1/users?q=an` | only users whose `USER_NM` contains "an" (case-insensitive substring); `total_count` reflects the filtered count |
| D3 | `q` mixed case (`?q=AL`) | matches "Alice", "alan" — `LOWER(USER_NM) LIKE LOWER('%al%')` |
| D4 | `q` with surrounding whitespace (`?q=  an  `) | trimmed before matching ("an") |
| D5 | `q` over 100 chars | `WBE_VALIDATION` (bean validation on the param) |
| D6 | `q` matches nothing | `200`, `{ total_count: 0, users: [] }` (not an error) |

### Tenant scope

| # | Scenario | Expected |
|---|---|---|
| T1 | Two tenants, same DB | a caller bound to tenant A sees only tenant-A active users; tenant-B users never leak |
| T2 | User active in tenant B only | absent from tenant-A caller's directory |
| T3 | Cross-tenant user id "lookup" | no direct lookup exists — a user simply does not appear unless they have an active link in the caller's tenant |
| T4 | Missing tenant on `TenantContext` | `WBE9999` (configuration-bug guard — not a normal path; JWT filter should always populate the context) |

### Active-only

| # | Scenario | Expected |
|---|---|---|
| A1 | User with `USER_BUSI_CONN_INFM.DEL_YN='Y'` (soft-deleted link) | excluded from the directory |
| A2 | User re-added (`DEL_YN` flipped back to `'N'`) | reappears |

### `user_img_url` derivation

| # | Scenario | Expected |
|---|---|---|
| I1 | User with non-blank `USER_BUSI_CONN_INFM.USER_IMG` | `user_img_url = "/api/v1/users/{userId}/image"` |
| I2 | User with null / blank `USER_IMG` | `user_img_url = null` |
| I3 | Any row | raw `USER_IMG` value **never** appears on the wire (only the derived URL) |
| I4 | Same user, different avatar at two tenants | each tenant's directory reflects that tenant's `USER_IMG` (per-tenant source) |

### Authorization

| # | Scenario | Expected |
|---|---|---|
| Z1 | Authenticated normal (non-admin) user | `200` — directory returned (JWT-only, no `@menuAuthz` grant required) |
| Z2 | Authenticated admin user | `200` — same directory |
| Z3 | No / invalid / expired JWT | `401` `WBE1401` (via `JwtAuthenticationEntryPoint`) |
| Z4 | `UserController` allow-list posture | `EndpointAuthorizationCoverageTest` passes — the controller is in `ALLOWLISTED_CONTROLLERS`, so the no-`@menuAuthz` posture is the asserted intent, not a coverage hole |

### Scope boundary (negative — these are NOT this endpoint)

| # | Scenario | Expected |
|---|---|---|
| S1 | Edit own profile via auth surface | **no endpoint** — MISSING; nothing under `/api/v1/auth/*` or `/api/v1/users` mutates a profile |
| S2 | Avatar upload / delete | served by Settings > Users (`/api/v1/settings/users/{user_id}/image`), admin-gated — out of auth scope |
| S3 | Current session identity | `GET /api/v1/auth/me` — session-management module, not this endpoint |

---

## 2. Coverage notes

- **No dedicated `UserControllerTest` / `UserDirectoryServiceTest` on disk** in this pass —
  **Status: MISSING.** The only test touching this surface is
  [EndpointAuthorizationCoverageTest](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java),
  which asserts `UserController` is allow-listed (deny-or-declared coverage). Recommended additions:
  - `UserDirectoryServiceTest` — D1/D2/D3/D4 filter behaviour, A1/A2 active-only, I1/I2/I3
    `user_img_url` derivation, T4 missing-tenant `WBE9999`, ordering (`USER_NM ASC, USER_ID ASC`),
    `total_count` = row count (Mockito over `UserBusiConnInfmRepository.findTenantUserPicker`).
  - `@WebMvcTest UserControllerTest` — D5 `WBE_VALIDATION` on over-length `q`, the lowercase
    snake_case wire shape (`total_count`, `user_id`, `user_nm`, `posi_nm`, `dept_nm`, `user_img_url`),
    and Z3 `WBE1401` on no JWT.
- **Tenant-isolation (T1/T2)** needs a multi-tenant seed (e.g. `VN_VNM0004_000001` +
  `VN_VNM0004_000014` from V73) to exercise cross-tenant exclusion end-to-end; the unit test can pin
  `TenantContext` and assert the `bizKey` predicate is passed to the repository.
- **GAP-4 / GAP-5** (self-profile edit MISSING, auth-side avatar stream PARTIAL — see
  [refactor-gap-analysis.md](refactor-gap-analysis.md)) have no tests because the endpoints do not exist
  yet; add tests when those endpoints land.

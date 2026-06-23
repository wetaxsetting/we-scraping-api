# Session Management — Test Scenarios (Backend)

Coverage map + scenarios. The on-disk test is
[AuthServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/auth/service/AuthServiceTest.java)
(Mockito unit test) — `me()` shares the `AuthService` class with login, so the `/me` cases live there.
Frontend scenarios:
[wecms-web/docs/features/auth/session-management/test-scenarios.md](../../../../../wecms-web/docs/features/auth/session-management/test-scenarios.md).

---

## 1. Implemented — `AuthServiceTest` (`/me` cases)

Mockito unit test; mocks the auth repositories + `MenuAuthorizationService`. Each `/me` case sets
`TenantContext` (as the JWT filter would) and clears it in a `finally`.

| # | Test method (line) | Asserts |
|---|---|---|
| ME1 | `me_readsPrdtTypeFromJwtClaim_notFromBusiInfm` (326–346) | `BUSI_INFM` row carries `F`, JWT claim says `H` → `/me` returns `prdt_type = "H"` (trusts the signed claim, never re-queries `BUSI_INFM` for product type) |
| ME2 | `me_admin_bypassesMenuFiltering_allMenusTrue` (352–372) | admin → `all_menus = true`, `allowed_menu_cds` empty, `MenuAuthorizationService.grantedMenuCds` **never** called |
| ME3 | `me_normalUser_returnsOnlyGrantedMenus` (374–395) | user → `all_menus = false`, `allowed_menu_cds` = exact granted slugs (`addon-einvoices-sales`, …) |

These three prove the happy-path rehydrate: company lookup, the JWT-claim echo of `prdtType`, and the
admin-vs-user menu payload.

---

## 2. SIT / UAT scenarios (manual)

| # | Scenario | Expected |
|---|---|---|
| T1 | `/me` with a valid unexpired token (normal user) | `MeResponse` with `all_menus = false` + granted slugs; `cmpn_nm` + `tax_id` populated; no DB write |
| T2 | `/me` with a valid unexpired token (admin) | `MeResponse` with `all_menus = true` + empty `allowed_menu_cds` |
| T3 | `/me` for a **deleted** user (token still unexpired) | `WBE1401` (HTTP 200 business path) — `USER_INFM.DEL_YN = 'Y'` bounces the session |
| T4 | `/me` for an **inactive** user (`ACTV_YN ≠ 'Y'`) | `WBE1401` — session bounced |
| T5 | `/me` for a **locked** user (`LOCK_YN = 'Y'`) | `WBE1401` — session bounced |
| T6 | `/me` after the token-bound **link is removed** (`USER_BUSI_CONN_INFM` `DEL_YN = 'Y'` / row gone) | `WBE1401` — no longer linked to the tenant the token binds |
| T7 | `/me` with a **missing / malformed / forged** Bearer token | HTTP **401**, `WBE1401` from `JwtAuthenticationEntryPoint`; request never reaches the handler; `X-Trace-Id` header set |
| T8 | `/me` with an **expired** token (past `expires-in-seconds`) | HTTP **401**, `WBE1401`; frontend surfaces the re-auth dialog |
| T9 | `tax_id` corrected by ops between two `/me` calls | the second `/me` reflects the new `tax_id` without a re-login (re-queried per call) |
| T10 | Entitlement edited (Settings > Users) between two `/me` calls | the second `/me` reflects the new `allowed_menu_cds` without a re-login (grants resolved fresh) |
| T11 | Authority / product-type changed in the DB while a token is in flight | `/me` still echoes the **old** `auth_gr_cd` / `prdt_type` (signed claim) until the next login — by design |

---

## 3. Coverage gaps (MISSING)

- **`me()` rejection unit tests — MISSING.** No `AuthServiceTest` case exercises the `WBE1401`
  re-validation gates inside `me()` (T3–T6: null principal, deleted / inactive / locked user, removed
  link). Only the happy-path `prdtType`-from-claim + admin/user menu cases exist. Add unit cases that set
  `TenantContext` and assert `WBE1401` for each gate.
- **`@WebMvcTest` controller test — MISSING.** No `AuthControllerTest` asserting the `/me` wire shape
  (lowercase snake_case, `all_menus` + `allowed_menu_cds` + `tax_id`, `@JsonInclude(NON_NULL)` dropping
  null `prdt_type`/`tax_id`) nor the `JwtAuthenticationEntryPoint` 401 `WBE1401` envelope on an
  unauthenticated request. Add one.
- **Integration test against a real DB — MISSING.** All coverage is Mockito; no Testcontainers / in-memory
  boot test exercises the actual JPA queries or the security-chain 401 path (per `wecms-api/CLAUDE.md`,
  Testcontainers is deferred).
- **Refresh-token flow — MISSING (feature not implemented).** No test because there is no refresh token
  (see [refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-1).
- **Server-side logout revocation — MISSING (feature not implemented).** GAP-2.
- **5-failure auto-lock — MISSING (feature not implemented).** GAP-3 (owned by [login](../login/README.md)).
- **Async `TenantContext` propagation — not tested (by design, not implemented).** GAP-4.

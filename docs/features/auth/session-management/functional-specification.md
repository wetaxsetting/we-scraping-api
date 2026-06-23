# Session Management — Functional Specification (Backend)

The implemented behaviour of the session-management backend. JEX behaviour reference (read-only): the
session bootstrap `logn_0001_001_r002` (post-login session/context load). Source of truth for the
refactor: the implemented Spring code in
[AuthService.me](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java)
+ [SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java)
+ [TenantContext](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java).

Scope = the session lifecycle: `GET /api/v1/auth/me`, the stateless token model, the request-scoped
`TenantContext`, TTL/expiry, and the 401 session-expired path. The login credential exchange, the JWT
token mechanics, and the menu-grant resolution are separate modules (cross-linked); this doc references
them but does not specify them.

---

## 1. Operation

| Operation | HTTP | URI | Maps to JEX | Auth | Audit |
|---|---|---|---|---|---|
| Current-session snapshot (rehydrate) | GET | `/api/v1/auth/me` | session bootstrap `logn_0001_001_r002` | Protected (JWT) | none |

Protected endpoint — `anyRequest().authenticated()` in `SecurityConfig`. A missing / invalid / expired
token never reaches the handler: it is short-circuited by
[JwtAuthenticationEntryPoint](../jwt-authentication/README.md) (§5). `/me` performs no audit write — it is
a read-only rehydrate.

---

## 2. Field contract — `MeResponse`

Returned by `GET /api/v1/auth/me`. Wire names auto-derive from camelCase Java fields via the global
`SNAKE_CASE` strategy. Source:
[MeResponse.java](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/MeResponse.java).

| Wire field | Java field | Source | Notes |
|---|---|---|---|
| `user_id` | `userId` | `USER_INFM.USER_ID` (re-fetched) | the authenticated caller |
| `user_nm` | `userNm` | `USER_INFM.USER_NM` (re-fetched) | display name |
| `biz_key` | `bizKey` | `TenantContext` (JWT-bound) | the token-bound tenant |
| `lang_cd` | `langCd` | `TenantContext.langCd()` (JWT) | falls back to `EN` when the principal carried none |
| `cmpn_nm` | `cmpnNm` | `BUSI_INFM.CMPN_NM` (re-queried) | empty string when null |
| `auth_gr_cd` | `authGrCd` | JWT claim (echoed) | `ADMIN` / `USER`; not re-queried (signed) |
| `prdt_type` | `prdtType` | JWT claim (echoed) | verbatim `BUSI_INFM.PRDT_TYPE` letter; may be null |
| `tax_id` | `taxId` | `BUSI_INFM.TAX_ID` (re-queried) | Lock Screen popup; null when unset; never defaulted |
| `all_menus` | `allMenus` | derived (`authGrCd == ADMIN`) | `true` for admins — they bypass menu filtering |
| `allowed_menu_cds` | `allowedMenuCds` | `MenuAuthorizationService` (fresh) | granted leaf slugs for a normal user; empty list when admin |

There is no token in the `/me` response — `MeResponse` is the authenticated `LoginResponse` identity
fields minus the token envelope, plus the menu payload. The frontend already holds the token; `/me`
re-confirms identity and refreshes the menu set.

---

## 3. `/me` flow (numbered)

From `AuthService.me()` (`@Transactional(readOnly = true)`):

1. **Read the principal.** `TenantContext.current()`. Null, or missing `userId` / `bizKey` → `WBE1401`
   (no valid session bound).
2. **Re-verify user eligibility.** `userRepo.findById(userId)`; null **or** `isDeleted()` **or** not
   `isActive()` **or** `isLocked()` → `WBE1401`. A flag that flipped since the token was issued
   invalidates the in-flight session (the JWT is signed and immutable, so the only place this is caught
   is here).
3. **Re-check the token-bound link.** `userBusiRepo.findByUserIdAndBizKeyAndDelYn(userId, bizKey, "N")`;
   missing → `WBE1401` (the user is no longer linked to the tenant the token is bound to).
4. **Company lookup.** `busiRepo.findById(bizKey)` once → `cmpnNm` (empty string when null) + `taxId`
   (verbatim, null preserved). Re-queried per call so an ops-side `TAX_ID` correction surfaces on the
   next rehydrate without a re-login.
5. **Echo authority + product type from the JWT.** `authGrCd = principal.authGrCd()` (defaults to `USER`
   when null); `prdtType = principal.prdtType()` (null preserved). Not re-queried — the JWT claim is
   signed, and re-hitting `BUSI_ADMN_INFM` / `BUSI_INFM` on every `/me` would be a needless DB hop.
6. **Resolve the menu payload (fresh).**
   - `allMenus = AUTH_GR_ADMIN.equals(authGrCd)`.
   - `allowedMenuCds = allMenus ? List.of() : menuAuthService.grantedMenuCds(bizKey, userId)`.
   Resolved per `/me` (no JWT menu claim) so an entitlement edit applies on the next reload without a
   token re-issue. See [menu-authorization](../menu-authorization/README.md).
7. **Return** `MeResponse(userId, userNm, bizKey, langCd.name(), cmpnNm, authGrCd, prdtType, taxId,
   allMenus, allowedMenuCds)`.

The re-validation gates in steps 2–3 are the substance of the endpoint: `/me` is not a trusting echo of
the token — it re-checks the live DB state so a deactivated / unlinked user is bounced on the next call
even though their unexpired token is otherwise valid.

---

## 4. Session model — stateless

[SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) sets
`SessionCreationPolicy.STATELESS`. Consequences:

- **No `HttpSession`.** Spring Security creates no session and stores no `SecurityContext` server-side.
  Each request stands alone — the `SecurityContext` is rebuilt from the Bearer token by
  `JwtAuthenticationFilter` and discarded at the end of the request.
- **No server-side token store / blacklist.** A token is valid until it expires; the server cannot
  revoke a still-valid token. There is no DB row, no Redis entry, nothing to invalidate.
- **Logout is client-only.** `POST /api/v1/auth/logout` is a stub that returns success; the client clears
  its token. The token remains cryptographically valid until expiry — there is no server revocation. See
  [logout](../logout/README.md).
- **Authority / product-type changes take effect at the next login.** Editing `BUSI_ADMN_INFM` or
  `BUSI_INFM.PRDT_TYPE` does not affect an in-flight JWT — its claims are signed and immutable. (Menu
  grants are the exception: they are resolved fresh on every `/me`, so they apply on the next reload.)

---

## 5. `TenantContext` lifecycle

[TenantContext](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) is a plain
`ThreadLocal<Principal>` holding `(userId, userNm, bizKey, langCd, authGrCd, prdtType)`:

- **Set** by `JwtAuthenticationFilter` at the head of an authenticated request, from the parsed JWT
  claims (see [jwt-authentication](../jwt-authentication/README.md)).
- **Read** by `AuthService.me()` (and every domain service) via the static accessors `currentUserId()`,
  `currentBizKey()`, `currentAuthGrCd()`, `currentPrdtType()`, etc.
- **Cleared** by the filter in a `finally` block at the end of the request.

It is **request-scoped, single-thread.** The class Javadoc warns explicitly against leaking access beyond
the request lifecycle — `@Async` executors / child threads do **not** inherit the value (it is a plain
`ThreadLocal`, not an `InheritableThreadLocal`, and no `TaskDecorator` propagates it). Async propagation
is **not implemented** — by design for the current synchronous request model (see
[refactor-gap-analysis.md](refactor-gap-analysis.md)).

---

## 6. TTL / expiry behaviour

- The token lifetime is `wecms.jwt.expires-in-seconds` — bound by
  [JwtProperties](../../../../src/main/java/com/kosign/wecms/config/JwtProperties.java)
  (`@ConfigurationProperties("wecms.jwt")`, `@Positive`). The configured value (default **3600** s) is
  supplied via `application.yml`; `JwtTokenProvider.expiresInSeconds()` echoes it onto the login
  response's `expires_in` and into the token's `exp` claim.
- **There is no refresh token and no sliding session.** When the token expires the session simply ends —
  the next protected call (including `/me`) fails token validation in the filter and surfaces the 401
  path (§7). The frontend's cookie `Max-Age` mirrors the TTL so the edge guard and the token expire
  together. Refresh tokens are **MISSING** (deferred — see
  [refactor-gap-analysis.md](refactor-gap-analysis.md)).

---

## 7. 401 session-expired path

A missing / malformed / expired / forged Bearer token never reaches `AuthController.me`. The
`JwtAuthenticationFilter` clears the context and lets the request continue; it hits the
`anyRequest().authenticated()` matcher, and Spring Security invokes
[JwtAuthenticationEntryPoint](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationEntryPoint.java),
which returns:

- **HTTP 401** (not 403 — RFC 7235 reserves 401 for "credentials missing or invalid"; the default
  `Http403ForbiddenEntryPoint` is overridden in `SecurityConfig.exceptionHandling`).
- Code **`WBE1401`** (legacy `SES001` parity, per
  `WECMS DEV/.../jex.wabooks.custom_v2.js:107`) — a dedicated session-expired code, deliberately distinct
  from `WBE1009` (bad credentials, raised by `AuthService.login`) so a client can branch on "session no
  longer valid" vs "credentials wrong".
- The standard `ApiResponse` error envelope carrying `path` + `trace_id`, with the same id echoed in the
  `X-Trace-Id` response header.

> The two ways a session ends are distinct in code: (a) the **token is invalid/expired** → caught in the
> filter → `WBE1401` from the entry point (request never reaches the handler); (b) the token is valid but
> the **live user/link state changed** → caught inside `AuthService.me()` → `WBE1401` thrown as a
> `BusinessException` and mapped by `GlobalExceptionHandler`. Both surface code `WBE1401`; the frontend
> reacts to either by surfacing the re-auth dialog and redirecting to `/login`.

---

## 8. `all_menus` / `allowed_menu_cds` payload semantics

Resolved fresh on every `/me` (never from a JWT claim) via
[MenuAuthorizationService](../menu-authorization/README.md):

- **Admin** (`auth_gr_cd = ADMIN`) → `all_menus = true`, `allowed_menu_cds = []`. The admin bypasses
  per-user menu filtering entirely; the frontend ignores the (empty) list and shows the full product
  tree.
- **Normal user** (`auth_gr_cd = USER`) → `all_menus = false`, `allowed_menu_cds` = the user's
  `USE_YN='Y'` granted leaf slugs (kebab codes, e.g. `addon-einvoices-sales`). An empty list means "no
  menus granted".

Because grants are resolved per call, an entitlement change made through Settings > Users takes effect on
the next `/me` (next reload) without forcing a token re-issue — the deliberate counterpart to the signed,
immutable `authGrCd` / `prdtType` claims.

---

## 9. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success — `MeResponse` returned |
| `WBE1401` | 401 (entry point) / 200 (business) | Session expired / unauthenticated. Two origins: token invalid/expired (filter → `JwtAuthenticationEntryPoint`, **HTTP 401**); or token valid but user deleted/inactive/locked or link removed (`AuthService.me()` throws `BusinessException("WBE1401")`, mapped by `GlobalExceptionHandler`). |
| `WBE9999` | 500 | Unexpected server error (shared catch-all) |

Full request/response shapes: [interface-specification.md](interface-specification.md).

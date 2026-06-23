# Session Management — Business Flow (Backend)

End-to-end request flow for the session lifecycle, with legacy JEX parity notes. Source of truth for the
refactor: the implemented Spring code in
[AuthController.me](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java)
→ [AuthService.me](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java),
backed by [SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java)
(`STATELESS`) + [TenantContext](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java).
Legacy reference (read-only): the post-login session bootstrap `logn_0001_001_r002`.

```
Browser (Next.js dashboard layout mount, or after a 401 re-auth)
      │   GET /api/v1/auth/me      Authorization: Bearer <jwt>
      ▼
JwtAuthenticationFilter
      │   parse Bearer → set TenantContext(userId, userNm, bizKey, langCd, authGrCd, prdtType)
      │   (invalid / expired / missing → clear context, continue)
      ▼
SecurityConfig: anyRequest().authenticated()
      │   no SecurityContext  →  JwtAuthenticationEntryPoint  →  401  WBE1401   (never reaches handler)
      ▼
AuthController.me  →  AuthService.me  @Transactional(readOnly)
      │
      ├─ TenantContext.current()                 (null / no userId|bizKey → WBE1401)
      ├─ UserInfmRepository.findById             (deleted | inactive | locked → WBE1401)
      ├─ UserBusiConnInfmRepository.findByUserIdAndBizKeyAndDelYn   (link gone → WBE1401)
      ├─ BusiInfmRepository.findById             (CMPN_NM, TAX_ID — re-queried)
      ├─ echo authGrCd + prdtType from the JWT claim   (not re-queried)
      └─ MenuAuthorizationService.grantedMenuCds (admin → all_menus=true, []; user → granted slugs)
      ▼
ApiResponse<MeResponse>   (lowercase snake_case; no token in the body)
      │   finally: TenantContext.clear()
```

---

## 1. Normal path — session rehydrate

```
GET /api/v1/auth/me   (valid unexpired Bearer token)
  → filter sets TenantContext from the signed claims
  → me(): principal present
  → USER_INFM still active (not deleted/inactive/locked)
  → USER_BUSI_CONN_INFM link still present (DEL_YN='N')
  → BUSI_INFM: cmpn_nm + verbatim tax_id
  → authGrCd + prdtType echoed from the JWT claim
  → menu payload: admin → all_menus=true,[] | user → all_menus=false, granted slugs
  → MeResponse
```

The frontend calls this on app load to confirm identity from the server of record (not just
`localStorage`) and to refresh the menu set, then renders the navigation. No audit row, no DB write — a
read-only rehydrate.

## 2. Session-expired path — token invalid / expired

```
GET /api/v1/auth/me   (missing / malformed / expired / forged token)
  → JwtAuthenticationFilter cannot bind a SecurityContext
  → anyRequest().authenticated() rejects
  → JwtAuthenticationEntryPoint.commence → HTTP 401, WBE1401, X-Trace-Id
  → request never reaches AuthController.me
```

The frontend's Axios response interceptor watches for 401 and surfaces the global session-expired re-auth
dialog. This is the **TTL expiry** outcome — there is no refresh token, so an expired session simply ends.

## 3. Session-revoked-by-state path — token valid but user/link changed

```
GET /api/v1/auth/me   (token still unexpired, but the live DB state changed)
  → filter binds the context fine (the token is signed and valid)
  → me() re-validates against the live DB:
       user deleted / deactivated / locked  → throw WBE1401
       token-bound link removed (DEL_YN='Y' or row gone) → throw WBE1401
  → GlobalExceptionHandler maps the BusinessException → WBE1401 envelope
```

This is the substance of `/me`: because the model is stateless and the JWT cannot be revoked, the
re-validation gates are the only place a still-valid token is bounced when the underlying account state
changes. Both §2 and §3 surface the same `WBE1401` code; the frontend reacts identically.

---

## Legacy JEX parity (session bootstrap `logn_0001_001_r002`)

| Aspect | Legacy session bootstrap (`logn_0001_001_r002` / HttpSession) | Refactor `AuthService.me` (stateless) | Status |
|---|---|---|---|
| Post-login session/context load | server-side `HttpSession` holds the user/company context after login | `GET /api/v1/auth/me` re-derives the context from the signed JWT on each call | **APPROVED DEVIATION** (stateless model) |
| Server-side session object | yes — `HttpSession` per logged-in user | none — `SessionCreationPolicy.STATELESS`, no `HttpSession` | **APPROVED DEVIATION** vs legacy `HttpSession` (the `HttpSession` itself is **DEAD LEGACY** — intentionally not ported) |
| Identity on each request | read from the server-side session | rebuilt into `TenantContext` from the JWT by `JwtAuthenticationFilter`, cleared per request | **APPROVED DEVIATION** |
| Re-validate user still eligible | session checks on access | `me()` re-checks `USER_INFM` deleted/inactive/locked → `WBE1401` | **MATCH** (intent preserved) |
| Re-validate tenant link | session bound to the chosen company | `me()` re-checks `USER_BUSI_CONN_INFM` link (`DEL_YN='N'`) → `WBE1401` | **MATCH** (intent preserved) |
| Company display / tax id | loaded once into the session | `BUSI_INFM.CMPN_NM` + `TAX_ID` re-queried per `/me` (ops correction surfaces without re-login) | **MATCH** |
| Menu / permission set | loaded into the session at bootstrap | resolved fresh per `/me` via `MenuAuthorizationService` (admin bypass) | **MATCH** (intent preserved; see [menu-authorization](../menu-authorization/README.md)) |
| Session expiry | server-side session timeout | JWT TTL `wecms.jwt.expires-in-seconds` (default 3600); no sliding window | **APPROVED DEVIATION** |
| Session invalidation on logout | server invalidates the `HttpSession` | client clears the token; no server revocation | **DEAD LEGACY** (owned by [logout](../logout/README.md)) |
| 401 on lost session | redirect to login | `JwtAuthenticationEntryPoint` → 401 `WBE1401` (legacy `SES001` parity) | **MATCH** (intent preserved; code is the slug-era `WBE1401`) |
| Refresh token (silent renew) | n/a | not implemented — token simply expires | **MISSING** (deferred — future `REFRESH_TOKEN`) |
| Server-side logout revocation | session invalidation | not implemented (stateless) | **MISSING** (deferred) |
| 5-failure auto-lock | legacy counts failures and locks | not implemented (owned by login; `USER_LOGIN_HIS` is the scaffold) | **MISSING** (deferred — see [login](../login/README.md)) |
| Async `TenantContext` propagation | n/a (servlet thread) | plain `ThreadLocal`; no `@Async` / child-thread propagation | **PARTIAL** (by design for the synchronous request model) |
| Response envelope | JEX `COMMON_HEAD` / `REC` | `{ status, data, … }` lowercase snake_case | **APPROVED DEVIATION** (CTO directive) |
| Identifier surface | WSVC id `logn_0001_001_r002` | kebab slug `session-management` everywhere | **APPROVED DEVIATION** (slug rule) |

JWT token mechanics (issue / parse / filter) are **owned by** [jwt-authentication](../jwt-authentication/README.md);
the credential exchange by [login](../login/README.md); the menu-grant resolution by
[menu-authorization](../menu-authorization/README.md). Full classification + evidence:
[refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## Invariants (every `/me` call)

- **Identity is never client-trusted.** `userId` / `bizKey` come from the signed JWT via `TenantContext`,
  never from a request param.
- **Re-validation always runs.** A still-valid token whose underlying user/link state changed is bounced
  with `WBE1401` — the token alone is not sufficient.
- **No token in the response.** `/me` confirms the existing session; it never mints a new token.
- **No DB write.** `/me` is read-only — no audit row, no `LST_LGN_DATE`, nothing.
- **Menu set is fresh; authority/product type are signed.** Grants re-resolve per call; `authGrCd` /
  `prdtType` are trusted from the immutable claim.

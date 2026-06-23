# JWT Authentication — Source Code Index

File map for the refactor's JWT token mechanics. JEX paths are **read-only reference**. Frontend
mirror: [wecms-web/docs/features/auth/jwt-authentication/README.md](../../../../../wecms-web/docs/features/auth/jwt-authentication/README.md)
(token storage + transport).

---

## Refactor — backend (`wecms-api`)

### Core (5 security/config files + TenantContext)

| Concern | File | Key members |
|---|---|---|
| **Token issue + parse** | [common/security/JwtTokenProvider.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java) | constructor (`:26-45`) fail-fast secret + HS256/HS512 pick (`:43`); `issue(userId, userNm, bizKey, langCd, authGrCd, prdtType)` (`:62-92`); `parse(token) → Jws<Claims>` (`:94-99`); `expiresInSeconds()` (`:101-103`) |
| **Per-request filter** | [common/security/JwtAuthenticationFilter.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) | `OncePerRequestFilter`; `doFilterInternal(...)` (`:34-96`) — read Bearer (`:38-41`), `parse` (`:43`), authorities `ROLE_ADMIN`/`ROLE_USER` (`:56-64`), `TenantContext.set(...)` (`:78-84`), `JwtException` → clear context (`:86-89`), `TenantContext.clear()` in `finally` (`:93-95`) |
| **401 entry point** | [common/security/JwtAuthenticationEntryPoint.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationEntryPoint.java) | `AuthenticationEntryPoint`; `CODE_SESSION_EXPIRED = "WBE1401"` (`:52`, parity legacy `SES001`); `commence(...)` (`:57-74`) — HTTP 401, `ApiResponse.error(WBE1401, msg, uri, traceId)` (`:68-72`) |
| **Config properties** | [config/JwtProperties.java](../../../../src/main/java/com/kosign/wecms/config/JwtProperties.java) | `@ConfigurationProperties("wecms.jwt")` record — `@NotBlank secret`, `@Positive expiresInSeconds`, `@NotBlank issuer` |
| **Security filter chain** | [config/SecurityConfig.java](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) | `@EnableMethodSecurity` (`:24`); `filterChain(...)` (`:42-69`) — CSRF off (`:44`), CORS on (`:45`), `STATELESS` (`:46`), permit-all matchers (`:48-53`), `authenticationEntryPoint(jwtAuthenticationEntryPoint)` (`:58`), filter order `CallbackIpWhitelistFilter → jwtFilter → UsernamePasswordAuthenticationFilter` (`:66-67`) |
| **Request-scoped tenant holder** | [common/tenant/TenantContext.java](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | `AUTH_GR_ADMIN`/`AUTH_GR_USER` (`:43-45`); `set(...)` six-arg full setter (`:83-87`); `clear()` (`:89-91`); accessors `currentUserId`/`currentUserNm`/`currentBizKey`/`currentLangCd`/`currentAuthGrCd`/`currentIsAdmin`/`currentPrdtType` (`:97-151`); `record Principal(...)` (`:163-164`) |

### Reused / collaborating (not owned by this slice)

| Concern | File | Package |
|---|---|---|
| Standard response envelope | [common/response/ApiResponse.java](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) | `common/response` — `ApiResponse.error(code, message, path, traceId)` builds the 401 body |
| Localized message resolution | [common/i18n/MessageResolver.java](../../../../src/main/java/com/kosign/wecms/common/i18n/MessageResolver.java) | `common/i18n` — resolves `WBE1401` from `Accept-Language` |
| Trace id (MDC + `X-Trace-Id`) | [common/exception/TraceId.java](../../../../src/main/java/com/kosign/wecms/common/exception/TraceId.java) | `common/exception` — `TraceId.current()` for the 401 body + header |
| Language enum | [common/enums/LangCd.java](../../../../src/main/java/com/kosign/wecms/common/enums/LangCd.java) | `common/enums` — `LangCd.fromCodeOrDefault(claim)` in the filter |
| Product-type enum | [common/enums/PrdtType.java](../../../../src/main/java/com/kosign/wecms/common/enums/PrdtType.java) | `common/enums` — consumers resolve `TenantContext.currentPrdtType()` via `PrdtType.fromCode(...)` |
| Issuance caller | [domain/auth/service/AuthService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java) | `domain/auth` — `login(...)` calls `JwtTokenProvider.issue(...)`; see [../login/](../login/) |
| Callback edge filter (runs before JWT) | [domain/payment/dispatch/callback/CallbackIpWhitelistFilter.java](../../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/callback/CallbackIpWhitelistFilter.java) | `domain/payment/dispatch/callback` — first in the chain (`SecurityConfig:66`) |

### Config / properties

- `wecms.jwt.secret` (env `WECMS_JWT_SECRET`, ≥ 32 bytes) · `wecms.jwt.expires-in-seconds` (default `3600`) · `wecms.jwt.issuer` (`wecms-api`) — `application.yml:96-103`.
- `WBE1401=Your session is no longer valid. Please sign in again.` — `messages.properties:7`.

### Tests

**No dedicated JWT unit test on disk in this pass** — no `JwtTokenProviderTest`,
`JwtAuthenticationFilterTest`, or `JwtAuthenticationEntryPointTest`. The filter is exercised
indirectly by `@SpringBootTest` boot + auth-flow tests, but the token mechanics (HS256/HS512 pick,
claim round-trip, expiry, invalid signature, 401 envelope) are **MISSING** dedicated coverage. See
[test-scenarios.md §2](test-scenarios.md).

---

## Legacy JEX (`WECMS DEV/`) — read-only reference

| Artifact | Path | Note |
|---|---|---|
| Session-expired signal `SES001` | `WECMS DEV/.../jex.wabooks.custom_v2.js:107` | `WBE1401` is the refactor parity code (cited in `JwtAuthenticationEntryPoint:46-52`) |
| JEX `HttpSession` / login-cookie session model | JEX framework (servlet session) | **DEAD LEGACY** — replaced by stateless JWT; no single file maps 1:1 |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/jwt-authentication/`
- Sibling backend modules: [../login/](../login/) · [../authorization/](../authorization/) · [../session-management/](../session-management/)
- Frontend feature folder: [wecms-web/docs/features/auth/jwt-authentication/](../../../../../wecms-web/docs/features/auth/jwt-authentication/README.md)

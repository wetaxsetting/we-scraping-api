# JWT Authentication — Test Scenarios (Backend)

SIT/UAT + unit scenarios for the token mechanics, plus a coverage map. Frontend scenarios (token
storage + transport + 401 dialog): [wecms-web/docs/features/auth/jwt-authentication/test-scenarios.md](../../../../../wecms-web/docs/features/auth/jwt-authentication/README.md).

---

## 1. Scenarios

### Token issuance (`JwtTokenProvider.issue`)

| # | Scenario | Expected |
|---|---|---|
| I1 | Issue with all claims | compact JWT; `sub`=userId; `userNm`/`bizKey`/`langCd`/`authGrCd` present; `iss`=`wecms-api`; `iat`/`exp` set with `exp = iat + expires_in_seconds` |
| I2 | `prdtType` non-null/blank | `prdtType` claim present, **verbatim** DB letter (no enum coercion) |
| I3 | `prdtType` null or blank | `prdtType` claim **omitted entirely** (never defaulted to `"F"`) (`JwtTokenProvider:80-82`) |
| I4 | `authGrCd` = `"ADMIN"` | claim normalized to `ADMIN` |
| I5 | `authGrCd` = anything else / null | claim normalized to `USER` (never silently elevates) (`:73-75`) |
| I6 | Secret 32–63 bytes | header `alg` = `HS256` (`:43`) |
| I7 | Secret ≥ 64 bytes | header `alg` = `HS512` (`:43`) |

### Secret fail-fast (constructor)

| # | Scenario | Expected |
|---|---|---|
| S1 | Secret null / blank | `IllegalStateException` at bean construction (boot fails) (`:29-34`) |
| S2 | Secret < 32 bytes | `IllegalStateException` "must be at least 32 bytes…" (boot fails) (`:36-41`) |
| S3 | Valid secret | bean constructs; `expiresInSeconds()` returns the configured TTL |

### Parse / validate (`JwtTokenProvider.parse` + `JwtAuthenticationFilter`)

| # | Scenario | Expected |
|---|---|---|
| P1 | Valid, unexpired token | `parse` returns `Jws<Claims>`; filter sets `SecurityContext` + `TenantContext` |
| P2 | Expired token (`exp` < now) | `parse` throws `JwtException` (`ExpiredJwtException`); filter clears context; request → 401 `WBE1401` |
| P3 | Invalid signature (tampered payload / wrong secret) | `parse` throws `JwtException`; filter clears context; request → 401 `WBE1401` |
| P4 | Malformed token (not 3 segments) | `JwtException`; context cleared; → 401 |
| P5 | No `Authorization` header on a protected endpoint | filter no-ops; matcher → 401 `WBE1401` |
| P6 | `Authorization` present but not `Bearer ` scheme | filter no-ops (`:40`); → 401 on protected endpoint |
| P7 | Valid token on a permit-all endpoint (`/auth/login`, `/auth/logout`, `/actuator/health`) | passes regardless |

### Tenant + authority binding (`JwtAuthenticationFilter` → `TenantContext`)

| # | Scenario | Expected |
|---|---|---|
| T1 | `authGrCd`=ADMIN token | authority `ROLE_ADMIN`; `TenantContext.currentIsAdmin()` true |
| T2 | `authGrCd`=USER (or missing) token | authority `ROLE_USER`; defensive default never elevates (`:56-58`) |
| T3 | Blank `userNm` claim | `TenantContext.currentUserNm()` falls back to `userId` (`:80`) |
| T4 | `prdtType` absent (pre-cutover token) | `TenantContext.currentPrdtType()` returns null; gates degrade safely via `PrdtType.fromCode(...)` |
| T5 | Tenant read from token only | a body/param `biz_key` cannot override `currentBizKey()` |
| T6 | `TenantContext` cleared after request | `finally` clear runs (`:93-95`); a reused pooled thread sees no stale principal |
| T7 | Exception thrown inside the chain | `TenantContext` still cleared (clear is in `finally`) |

### 401 entry point (`JwtAuthenticationEntryPoint`)

| # | Scenario | Expected |
|---|---|---|
| E1 | Protected request, no valid `SecurityContext` | HTTP **401** (not 403); `status.code`=`WBE1401`; localized `status.message` |
| E2 | Envelope shape | `{ status, timestamp, path, trace_id }`; `path` = request URI; `trace_id` = MDC `traceId` |
| E3 | `X-Trace-Id` response header | present and equal to the body `trace_id` |
| E4 | `Accept-Language: ko/vi/km` | `status.message` localized (when the locale file has the key; EN otherwise) |
| E5 | Code disambiguation | `WBE1401` (session expired) ≠ `WBE1009` (bad credentials, returned by login) |

### Filter chain (`SecurityConfig`)

| # | Scenario | Expected |
|---|---|---|
| F1 | Session policy | no `HttpSession` / `JSESSIONID` created (`STATELESS`) |
| F2 | Filter order | `CallbackIpWhitelistFilter` → `JwtAuthenticationFilter` → `UsernamePasswordAuthenticationFilter` |
| F3 | Callback path | `/api/v1/payment/dispatch/callbacks/**` permit-all (no JWT required) |
| F4 | `@PreAuthorize` live | a `hasRole('ADMIN')` / `@menuAuthz` gate enforces against the JWT authorities (method security on) |

### Statelessness / lifecycle

| # | Scenario | Expected |
|---|---|---|
| ST1 | Logout then reuse the same token | request still **succeeds** until `exp` — logout is client-only, no server revocation (documents MISSING revocation) |
| ST2 | Authority changed in DB mid-session | in-flight token keeps old `authGrCd`; new authority applies only at next login |
| ST3 | Token past TTL | rejected at parse → 401 `WBE1401`; no refresh path (documents MISSING refresh) |

---

## 2. Coverage notes

- **No dedicated JWT unit test on disk in this pass — MISSING.** There is no `JwtTokenProviderTest`, `JwtAuthenticationFilterTest`, or `JwtAuthenticationEntryPointTest`. The filter + entry point are exercised only **indirectly** via `@SpringBootTest` boot and the auth-flow tests; the token mechanics themselves (HS256/HS512 selection, claim round-trip incl. `prdtType` omission, expiry, invalid-signature rejection, the `WBE1401` envelope shape) have **no direct assertions**.
  - **Recommended (MISSING) tests to add:**
    - `JwtTokenProviderTest` — I1–I7 + S1–S3 (issue claim round-trip, `prdtType` omission, HS256/HS512 by secret length, fail-fast secret). Pure Mockito/POJO; needs only a `JwtProperties` stub.
    - `JwtAuthenticationFilterTest` — P1–P6 + T1–T7 (parse success/failure, authority derivation, defensive defaults, `TenantContext` set + `finally` clear). Mock `JwtTokenProvider` + `FilterChain`; assert `SecurityContextHolder` + `TenantContext`.
    - `JwtAuthenticationEntryPointTest` / `@WebMvcTest` slice — E1–E5 + ST3 (401 status, `WBE1401` code, envelope + `X-Trace-Id`, EN/locale message). Use `spring-security-test` + `MockMvc` against a protected matcher with no/expired token.
- **Cross-cutting nature.** Because every protected endpoint depends on this layer, a regression here breaks the whole API — the dedicated tests above are the cheapest guardrail and should land in the next stabilisation pass.
- **SIT note.** Live 401 behaviour (E1–E4) is observable end-to-end against any protected endpoint with a stripped/expired Bearer header; ST1 (logout-then-reuse) is the concrete demonstration that revocation is MISSING.

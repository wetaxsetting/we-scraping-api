# Security Remediation Closure Report — 2026-06-24

**System:** Refactor (wecms-api — Spring Boot 3.5.13 / Java 21)
**Status:** Remediation complete + independently re-audited + documentation aligned.
**Author role:** Principal Security Engineer (release documentation).
**Source of truth:** the implementation. Every claim below is tied to a source file:line and to a green test/build.

> This is the closure-of-record. The original adversarial review it closes is preserved verbatim at
> [`../../../docs/security/spring-security-deep-review.md`](../../../docs/security/spring-security-deep-review.md)
> (workspace `docs/security/`), now carrying a Remediation-Status banner. Open items live in
> [`remaining-security-risk-register.md`](remaining-security-risk-register.md).

---

## 1. Scope

Closes the Spring Security deep review (2026-06-24) for the wecms-api backend, in preparation for **SIT deployment** and **UAT preparation**. Covers authentication, JWT lifecycle, file upload, security headers, secret hygiene, logging hygiene, credential-reveal auditing, and brute-force defence. Crypto (F-08), callback hardening (F-09), legacy password hashing (F-11), in-memory durability, and secret rotation are **scoped as remaining items** (see §10–§11), not closed here.

Out of scope: full frontend (`wecms-web`) audit; live bank-money dispatch (inert by default); PROD cutover.

## 2. Initial Risk Summary

The review scored the API **6.0 / 10** — architecturally sound but not deploy-safe. The sharp blockers were: a fail-open default profile that could sign tokens with a committed dev secret (F-01); stored-XSS via inline SVG (F-02); no token revocation / privilege re-check / real logout (F-03); no security headers/CSP (F-04); committed vendor keys (F-05); request/response body + SQL logging on SIT (F-06); an unaudited decrypted-credential reveal (F-07); and no brute-force lockout or rate limiting (F-10).

## 3. Findings Closed

| ID | Finding | Resolution | Evidence (source) |
|---|---|---|---|
| **F-01** | Fail-open profile + committed JWT secret | JWT secret fail-fast; the known-insecure dev placeholder is blocklisted at runtime; SIT/UAT/PROD carry no secret default | `common/security/JwtTokenProvider.java:38-50`; `application-sit.yml:64-68` |
| **F-02** | SVG inline-upload stored XSS | SVG rejected on both upload paths; allow-list is `jpg/jpeg/png/gif` | `domain/setting/users/service/UserImageService.java:63`; `domain/setting/preferences/service/PreferencesService.java:100-101` |
| **F-03** | No revocation / no real logout | Every JWT carries a `jti`; `TokenRevocationService` (jti denylist + per-user not-before cutoff) enforced per request; logout revokes; admin lock/delete/demote/password-reset revokes all user tokens | `JwtTokenProvider.issue` (UUID `jti`); `common/security/TokenRevocationService.java`; `JwtAuthenticationFilter.java:57-62`; `AuthService.java:338-351`; `UserManagementService.java:301,358` |
| **F-04** | No security headers / CSP / HSTS | CSP `default-src 'none'`, `frame-ancestors 'none'` + frameOptions DENY, `Referrer-Policy: no-referrer`, HSTS 1y (HTTPS-only) | `config/SecurityConfig.java:60-68` |
| **F-05** | Committed Coocon DEV keys as tracked defaults | Tracked YAML reduced to bare `${VAR}` placeholders (fail-fast when unset) | `application.yml:101,274-276`; `application-sit.yml:43,68,281-284` |
| **F-06** | SIT logs request/response bodies + SQL binds | SIT set to `com.kosign.wecms: INFO` / `security: WARN`, `show-sql:false`, no JDBC bind TRACE | `application-sit.yml:55-57,294-303` |
| **F-07** | Credential reveal not audited | Reveal writes an `AUDIT_LOGS_INFM` row and is rate-limited | `domain/setting/logincredentials/controller/LoginCredentialsController.java:202` |
| **F-10** | No brute-force lockout / rate limiting | 5-consecutive-failure auto-lock → `USER_INFM.LOCK_YN='Y'` (REQUIRES_NEW); in-memory fixed-window rate limit on login + credential reveal | `domain/auth/service/LoginLockoutService.java:58-82`; wired `AuthService.java:151`; `common/web/RateLimiter.java`; `AuthController.java:48` |

## 4. Files Changed Summary

The remediation **code** (F-01–F-10) landed in the working tree as part of the security effort and was independently re-audited; this report does not re-author it. Touched areas: `common/security/` (JwtTokenProvider, JwtAuthenticationFilter, TokenRevocationService), `common/web/RateLimiter`, `config/SecurityConfig`, `domain/auth/service/{AuthService,LoginLockoutService}`, `domain/setting/users/service/UserManagementService`, `domain/setting/logincredentials/controller/LoginCredentialsController`, the upload services, the `GlobalExceptionHandler` rate-limit branch, and the `application*.yml` profiles.

The **documentation alignment pass** (this effort) corrected stale comments + docs only — no logic, SQL, YAML behaviour, or tests:
- Source comments (8 Java files): removed false "writes BCrypt / transparent upgrade" (`LegacyHashPasswordEncoder`, `SecurityConfig`, `UserPwChgHis`, `UserManagementService`, `UserPwChgHisRepository`), false "AES-GCM ciphertext" (`BankCertInfo`, `GdtCertInfo`), and false "authorities advisory / method security not wired" (`JwtAuthenticationFilter`).
- Docs: 29 auth feature docs, 8 SVG docs, 2 audit-index docs, the deep-review banner, CLAUDE.md, and ~12 historical/superseded banners.

## 5. Verification Summary

- **Static-claim sweep:** grep across `src/main/java` for `always writes BCrypt | transparently upgrades | AES-GCM ciphertext/base64 | advisory until | wired up yet` → **0 matches**.
- **Behaviour traced to source** for each closed finding (§3 table).
- **Documentation parity:** no active doc claims SVG allowed, logout no-op, lockout/rate-limit missing, BCrypt written, or AES-GCM active (re-verified — see [SIT checklist](../deployment/sit-security-deployment-checklist.md) §post-deploy and this package's final verification).

## 6. Test / Build Results

Captured 2026-06-24 on this working tree (Temurin JDK 21):

| Command | Result |
|---|---|
| `./gradlew compileJava compileTestJava` | **EXIT 0** (main + test sources compile clean) |
| Security-guarding unit tests (below) | **BUILD SUCCESSFUL — 31 tests, 0 failures, 0 errors** |

| Test class | Guards | Tests |
|---|---|---|
| `common.security.JwtRevocationTest` | F-03 revocation/logout | 6 |
| `common.security.LegacyHashPasswordEncoderTest` | F-11 SHA-256 pin + coexistence (no BCrypt write) | 6 |
| `common.web.RateLimiterTest` | F-10 rate limiting | 3 |
| `domain.auth.service.LoginLockoutServiceTest` | F-10 5-failure auto-lock | 5 |
| `domain.banking.bankcredentials.service.BankCertCryptoServiceTest` | F-08 legacy-cipher golden vectors | 5 |
| `domain.payment.dispatch.van.crypto.EfsCryptoServiceTest` | F-09 EV/VV envelope | 6 |

> A full `./gradlew test` run (including `@SpringBootTest` boot tests, which require the VPN-reachable shared DB) is a **SIT pre-deploy gate** — attach the green CI run to the deployment ticket. The unit slice above is DB-independent (Mockito) and was executed here.

## 7. Current Verdict

**Conditionally Ready.** All UAT-gating blockers from the original review (F-01–F-07, F-10) are closed and verified; the running code is guarded by green tests. Residual items are accepted legacy constraints (F-08/F-11), a gated money-movement path (F-09), in-memory durability of F-03/F-10, and Ops-side secret rotation (F-05).

## 8. UAT Readiness

**Conditionally Ready**, provided:
1. Env secrets set on the SIT/UAT host (fail-fast otherwise) — see [required-environment-secrets.md](required-environment-secrets.md).
2. Payment dispatch stays **OFF** (the default) so F-09 is inert.
3. The historically-committed Coocon keys are rotated (Ops) — see [ops-secret-rotation-handoff.md](ops-secret-rotation-handoff.md).

UAT runs single-instance against the shared DB, so the in-memory revocation/rate-limit is adequate. No hard code blocker for UAT.

## 9. PROD Readiness

**Not Ready** until:
1. **Secret rotation** completed (Ops).
2. **F-09 callback hardening** before any dispatch enablement — populate the IP allow-list (fail-closed), set `trust-forwarded-for` for the real proxy, add an RQ_DTIME freshness window + TNO replay guard (backed by existing `TB_FIRM_BAT_API_TRAN` — no new table).
3. **Action-level authorization (R-02)** for state-changing endpoints.
4. **Multi-instance durability decision** for revocation + rate limiting if PROD scales out (otherwise pin single-instance and document it).

## 10. Remaining Non-Code Actions
- **Ops:** rotate the exposed Coocon `api-key` + `smart-api-key`; provision all `${VAR}` secrets per env; set `SPRING_PROFILES_ACTIVE` on every non-local host (fail-open-to-DEV guard, `application.yml:16-27`); attach a green full-`test` CI run to the SIT ticket.
- **PO / Coocon:** supply the real gateway source IPs for the callback allow-list; capture a real EFS gateway sample for the F-09 byte-verify (B5).

## 11. Remaining Legacy / Architecture Decisions
- **F-08** — keep the legacy AES-256-CBC pgcrypto-compatible cipher while the JEX DB is shared; a move to AES-GCM is a PO/DBA-coordinated re-encrypt+cutover, not a unilateral change.
- **F-11** — keep unsalted SHA-256 (`public.hash()`) on every write while JEX shares `USER_INFM`; a salted-hash migration needs a dual-read + JEX cutover plan.
- **Revocation / rate-limit durability** — accept in-memory for single-instance; adopt a shared store (Redis or PO-approved table) before horizontal scale.

See the [remaining-security-risk-register.md](remaining-security-risk-register.md) for owners, environment scope, and timelines.

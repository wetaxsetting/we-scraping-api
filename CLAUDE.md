# CLAUDE.md — wecms-api-main

Living AI/developer guide for the Spring Boot backend of the WeCMS JEX → Spring/Next.js refactor. Update this file whenever the real code moves.

Workspace-level rules (cross-tree, write boundary, typography source of truth, BIZ_KEY rule) live in the root [CLAUDE.md](../CLAUDE.md). The Next.js frontend this app serves is in [wecms-web-main/](../wecms-web-main/). JEX functional spec is read-only in [WECMS DEV/CLAUDE.md](../WECMS%20DEV/CLAUDE.md).

---

## Purpose

Back-end API for the WeCMS refactor R&D. Serves the Next.js client, reads the legacy JEX schema shape (column-for-column), and enforces single-tenant data isolation.

Status: **R&D proof-of-concept**, local-only, GitLab-only. No DEV / UAT / PROD deploy, no bank integration, no coexistence code with the live JEX system.

## Project Overview

- **What works today:** login (`POST /api/v1/auth/login`), me (`GET /api/v1/auth/me`), logout stub (`POST /api/v1/auth/logout`), JWT-protected catch-all for all other endpoints, Flyway-managed schema (V1–V10), legacy SHA-256 → BCrypt transparent password upgrade on first successful login.
- **Implemented module:** `domain/auth/` only. Every other JEX module (`pymn`, `mbmr`, `comm`, `admn`, …) is unimplemented.
- **Tenant scope:** single tenant pinned to `BIZ_KEY = VN_VNM0004_000001` via `wecms.tenant.biz-key`. No wildcards — the config property regex rejects `%`.
- **Deploy target:** developer laptop only. Runs against a local PostgreSQL instance owned by the developer.

## Tech Stack

| Layer | Choice |
|---|---|
| Runtime | Java 21 (Eclipse Temurin, Gradle toolchain) |
| Framework | Spring Boot 3.5.13 |
| Build | Gradle (Groovy DSL) — `./gradlew build`, `./gradlew bootRun`, `./gradlew test` |
| Web | spring-boot-starter-web |
| Persistence | spring-boot-starter-data-jpa + Hibernate, PostgreSQL driver |
| Migrations | **Flyway** (`flyway-core` + `flyway-database-postgresql`), `src/main/resources/db/migration/` |
| Security | spring-boot-starter-security + **jjwt 0.12.6** (HS256/HS512) |
| Validation | spring-boot-starter-validation + Jakarta Bean Validation |
| Boilerplate | Lombok (compile-only + annotation processor) |
| Dev UX | spring-boot-devtools |
| Tests | JUnit 5 + spring-boot-starter-test + spring-security-test |

**Not currently in `build.gradle`** (do not assume they are): SpringDoc OpenAPI, Testcontainers. Add them in the sprint where the first feature actually needs them.

**Entry point:** `com.kosign.wecms.WecmsApiMainApplication`
**Group / artifact / version:** `com.kosign.wecms` / `wecms-api-main` / `0.0.1-SNAPSHOT`
**VCS:** Git — `kosign-wecms/wecms-api-main` on GitLab.

## Architecture Rules

Lightweight domain-driven packaging — **NOT** full tactical DDD. No aggregates, value objects, or domain events unless a specific feature genuinely requires them.

```
com.kosign.wecms
├── WecmsApiMainApplication.java
├── config/
│   ├── CorsConfig.java              — CorsFilter bean; reads wecms.cors.allowed-origins
│   ├── JwtProperties.java           — @ConfigurationProperties("wecms.jwt")
│   ├── SecurityConfig.java          — SecurityFilterChain + PasswordEncoder bean
│   └── TenantProperties.java        — @ConfigurationProperties("wecms.tenant"); rejects % wildcard
├── common/
│   ├── entity/BaseEntity.java       — REG_ID/REG_DTM/MOD_ID/MOD_DTM audit columns (@PrePersist/@PreUpdate)
│   ├── enums/LangCd.java            — enum {EN, KO, KM, VI}; fromAcceptLanguage(), fromCodeOrDefault()
│   ├── exception/
│   │   ├── BusinessException.java   — extends RuntimeException; holds code + Object[] args
│   │   └── GlobalExceptionHandler.java — @RestControllerAdvice; maps BusinessException,
│   │                                     MethodArgumentNotValidException, generic Exception
│   ├── i18n/MessageResolver.java    — MessageSource wrapper; resolves WBE/WBI codes via LocaleContextHolder
│   ├── response/
│   │   ├── ApiResponse.java         — COMMON_HEAD + REC; static ok(T) / ok(code,T) / error(code,message)
│   │   └── CommonHead.java          — ERROR bool, CODE, MESSAGE; factories for success/error
│   ├── security/
│   │   ├── JwtTokenProvider.java    — HS256/HS512 token issue/parse; fail-fast on missing/short secret
│   │   ├── JwtAuthenticationFilter.java — OncePerRequestFilter; reads Bearer header;
│   │   │                                  sets UsernamePasswordAuthenticationToken;
│   │   │                                  populates TenantContext (userId, bizKey, langCd)
│   │   ├── WecmsPasswordEncoder.java    — hybrid: BCrypt write, BCrypt/legacy SHA-256 read
│   │   └── LegacyHashPasswordEncoder.java — replicates public.hash(txt): SHA-256 → base64
│   └── tenant/TenantContext.java    — ThreadLocal<Principal> holder; current(), currentUserId(),
│                                      currentBizKey(), currentLangCd() static accessors
└── domain/
    └── auth/                        — the only domain module implemented
        ├── bootstrap/DataSeeder.java — @Profile("local") replaces __BCRYPT_PENDING__ sentinel on startup
        ├── controller/AuthController — @RequestMapping("/api/v1/auth")
        ├── dto/
        │   ├── LoginRequest.java    — record; USER_ID, USER_PW; @NotBlank @Size; compact() trims
        │   ├── LoginResponse.java   — record; TOKEN, TOKEN_TYPE, EXPIRES_IN, USER_ID, USER_NM,
        │   │                          BIZ_KEY, LANG_CD, CMPN_NM
        │   └── MeResponse.java      — record; USER_ID, USER_NM, BIZ_KEY, LANG_CD, CMPN_NM (no token)
        ├── entity/
        │   ├── BusiInfm.java        — BUSI_INFM table; PK = BIZ_KEY; 40+ columns; extends BaseEntity
        │   ├── UserInfm.java        — USER_INFM table; PK = USER_ID; isActive/isLocked/isDeleted helpers
        │   ├── UserBusiConnInfm.java — USER_BUSI_CONN_INFM; composite PK (USER_ID, BIZ_KEY)
        │   ├── UserBusiConnInfmId.java — Serializable composite key
        │   ├── UserLoginHis.java    — USER_LOGIN_HIS; composite PK (USER_ID, REG_DTM); no BaseEntity
        │   └── UserLoginHisId.java  — Serializable composite key
        ├── repository/
        │   ├── UserInfmRepository.java           — JpaRepository<UserInfm, String>
        │   ├── BusiInfmRepository.java            — JpaRepository<BusiInfm, String>
        │   ├── UserBusiConnInfmRepository.java    — + findByUserIdAndBizKeyAndDelYn()
        │   └── UserLoginHisRepository.java        — JpaRepository<UserLoginHis, UserLoginHisId>
        └── service/
            ├── AuthService.java          — login(req, ipAddr, langCd) + me()
            └── LoginHistoryService.java  — record(userId, ipAddr, rsltCd, rsltCont) @REQUIRES_NEW
```

**Package rules:**
- New feature code → new `domain/{jex-module-code}/` package (e.g. `pymn`, `mbmr`, `comm`, `admn`).
- Do not promote helpers to `common/` until more than one domain consumes them.
- Within a domain: **controller → service → repository → entity + dto**. No cross-layer shortcuts.
- No controller-to-controller calls. No cross-domain repository access.
- Business logic lives in `service/`. Entities are JPA mapping + trivial boolean helpers, not rich domain objects.

## Endpoints

| Endpoint | Method | Auth | Returns |
|---|---|---|---|
| `/api/v1/auth/login` | POST | Public | `ApiResponse<LoginResponse>` |
| `/api/v1/auth/logout` | POST | Public | `ApiResponse<Void>` (stub — client clears token) |
| `/api/v1/auth/me` | GET | Protected (JWT) | `ApiResponse<MeResponse>` |
| `/actuator/health` | GET | Public | Spring Actuator |
| Everything else | any | Protected | 403 if no valid JWT |

## Coding Rules

**Controllers**
- Return `ApiResponse<T>`. Success: `ApiResponse.ok(rec)`. Error: throw `BusinessException("WBExxxx")`.
- Keep controllers thin: parse request, resolve incidental data (client IP, `Accept-Language` → `LANG_CD`), delegate to service, wrap result.
- DTOs use Java **records** with `@JsonProperty("UPPER_SNAKE")`. Validation on record components (`@NotBlank`, `@Size`, `@Pattern`).

**Services**
- Annotate mutating methods with `@Transactional`. Audit writes that must survive outer rollback use `@Transactional(propagation = REQUIRES_NEW)`.
- Throw `BusinessException(CODE, args…)` for known error paths. Code resolves via `messages*.properties`.
- Never leak raw Hibernate / JDBC exceptions — let the global handler map unexpected ones to `WBE9999`.

**Repositories**
- Spring Data JPA. Prefer derived method names over `@Query` until a native/JPQL query is actually needed.
- A repository method returning rows from a tenant-scoped table **must** constrain on `BIZ_KEY`. Never pass a `%` wildcard `BIZ_KEY`.

**Entities**
- Extend `BaseEntity` for legacy tables so audit columns are inherited.
- `@Table(name = "UPPER_SNAKE")` and `@Column(name = "UPPER_SNAKE")` — mirror legacy JEX schema column-for-column.
- `@IdClass` for composite keys.
- No JPA cascade on cross-aggregate relations unless there is a concrete need.

**Config / properties**
- Typed `@ConfigurationProperties` records, not scattered `@Value`. Enabled via `@EnableConfigurationProperties` on `SecurityConfig`.
- Every sensitive value has an env var (`WECMS_DB_PASSWORD`, `WECMS_JWT_SECRET`). Fail-fast in the constructor if a required value is missing.

**Lombok**
- `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`, `@Slf4j` — trivial only. No `@Data` on entities. No `@Builder` on DTOs.

**Style**
- One class per file.
- Javadoc on public service methods that implement a JEX behaviour — cite the JEX path being ported (e.g. `SzonHelper.logn_2001_001_r001`).
- Comments explain WHY. Never comment WHAT.

## Feature Status

| Module | Endpoints | Status |
|---|---|---|
| `domain/auth` | `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` | Working |
| Everything else | — | **Not started.** `domain/payment`, `domain/member`, `domain/settings`, `domain/banking`, … |

Deferred inside `auth` (intentionally out of Sprint 0 scope): 5-failure auto-lock counter, password-change-history reset, `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS` post-auth gates, post-login menu resolution, refresh tokens, JWT blacklist / server-side logout.

## Auth Rules

### Login flow (`AuthService.login`)

1. Load `USER_INFM` by `USER_ID`. Missing → `WBE1009` + FAIL row in `USER_LOGIN_HIS`.
2. Flag gates, in order: `DEL_YN=Y` → `WBE1128`, `ACTV_YN=N` → `WBE1010`, `LOCK_YN=Y` → `WBE1011`.
3. `passwordEncoder.matches(rawPassword, storedHash)`. Mismatch → `WBE1009`.
4. If `passwordEncoder.upgradeEncoding(storedHash)` (stored value is not BCrypt), re-encode and save.
5. Resolve `USER_BUSI_CONN_INFM` row for `(USER_ID, BIZ_KEY = wecms.tenant.biz-key, DEL_YN='N')`. No match → `WBE1009`.
6. Update `LST_LGN_DATE` on the link.
7. Write a SUCCESS `USER_LOGIN_HIS` row.
8. Look up `BUSI_INFM.CMPN_NM`.
9. Issue JWT and return `LoginResponse(TOKEN, TOKEN_TYPE=Bearer, EXPIRES_IN, USER_ID, USER_NM, BIZ_KEY, LANG_CD, CMPN_NM)`.

### Me flow (`AuthService.me`)

1. Read `TenantContext.current()` — populated by `JwtAuthenticationFilter` from the validated JWT.
2. Verify user still eligible (not deleted / inactive / locked in `USER_INFM`).
3. Verify link to configured tenant still exists (`USER_BUSI_CONN_INFM` where `DEL_YN='N'`).
4. Fetch `BUSI_INFM.CMPN_NM`.
5. Return `MeResponse(USER_ID, USER_NM, BIZ_KEY, LANG_CD, CMPN_NM)`.

### JWT (`JwtTokenProvider`)
- Secret: `wecms.jwt.secret` (env var `WECMS_JWT_SECRET`). Minimum 32 bytes → HS256; ≥ 64 bytes → HS512 (preferred). Fail-fast on missing/too-short secret.
- Claims: `sub = USER_ID`, `userNm`, `bizKey`, `langCd`. Lifetime from `wecms.jwt.expires-in-seconds` (default 3600 s).
- **Stateless**: no refresh token, no server-side blacklist.

### JWT filter (`JwtAuthenticationFilter`)
- Runs before `UsernamePasswordAuthenticationFilter`. Reads `Authorization: Bearer <token>`, parses, sets `UsernamePasswordAuthenticationToken` with `principal = userId`, and populates `TenantContext` (userId, bizKey, langCd) for the duration of the request. Invalid token → clear context, let downstream 403 fire.

### Security filter chain (`SecurityConfig`)
- CSRF disabled, CORS enabled via `CorsConfig` bean, session `STATELESS`.
- Public: `/api/v1/auth/login`, `/api/v1/auth/logout`, `/actuator/health`. **Everything else is authenticated.**

### Passwords (`WecmsPasswordEncoder` + `LegacyHashPasswordEncoder`)
- Write → BCrypt strength 10.
- Read → accepts BCrypt (`$2a$`/`$2b$`/`$2y$`) **or** legacy JEX `public.hash()` output (Base64(SHA-256(utf8(raw))), always 44 chars).
- `upgradeEncoding()` returns true for any non-BCrypt hash → `AuthService` transparently upgrades on successful login.

## i18n Rules

- Spring `MessageSource` (basename `messages`, UTF-8). Files: `messages.properties` (EN), `messages_en.properties`. Add `messages_ko.properties`, `messages_vi.properties`, `messages_km.properties` as real translations arrive.
- Keys are WBE/WBI codes (e.g. `WBE1009=ID or password does not match.`). Resolve via `MessageResolver.resolve(code, args…)`.
- Locale resolved at controller: `Accept-Language: ko` → `KO`, `vi` → `VI`, `km` → `KM`, default `EN`. Embedded in the JWT claim so downstream code reads it from `TenantContext` instead of re-parsing the header.
- Use the `LangCd` enum (`common/enums/LangCd.java`) for Accept-Language resolution — `LangCd.fromAcceptLanguage(header)` and `LangCd.fromCodeOrDefault(code)`.
- **Do NOT** reuse JEX ML code IDs verbatim — translate to `WBExxxx`/`WBIxxxx` and record in `messages*.properties`.

## API / DB Rules

### Response envelope — all endpoints

```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": { ... }
}
```

- Success: `ApiResponse.ok(rec)` → `ERROR=false`, `CODE=WBI0000`. `@JsonInclude(NON_NULL)` drops `REC` when null.
- Error: thrown `BusinessException` → `ApiResponse.error(code, message)` with localized message. HTTP status stays 200 for business errors — client inspects `COMMON_HEAD.ERROR`. Unexpected exceptions return HTTP 500 with `WBE9999`.
- DTO fields `@JsonProperty("UPPER_SNAKE")`. Keep consistent on both sides.

### Error codes in use

| Code | Meaning |
|---|---|
| WBI0000 | Success |
| WBE1009 | ID or password does not match (also: user not in configured tenant) |
| WBE1010 | Account not activated |
| WBE1011 | Account locked |
| WBE1128 | Account deleted |
| WBE_VALIDATION | Bean-validation failure |
| WBE9999 | Unexpected server error |

Add new codes to `messages.properties` first, then throw `new BusinessException("WBExxxx")`. Never return a raw string message without a code.

### Database (LOCAL)

- PostgreSQL `localhost:5434/wecms_dev`, user `postgres`. Password via env `WECMS_DB_PASSWORD` or the R&D default baked into `application-local.yml` (`.gitignore`d in real setups).
- **Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto = validate` — never `update` or `create`. Schema changes always land as a new `V{N}__{description}.sql` in `src/main/resources/db/migration/`.
- Current migrations:

  | Version | Summary |
  |---|---|
  | V1 | Auth schema: BUSI_INFM, USER_INFM, USER_BUSI_CONN_INFM, USER_LOGIN_HIS, indexes, constraints |
  | V2 | Seed: tenant VN_VNM0004_000001 + testuser (active) + lockeduser (LOCK_YN=Y), password = `__BCRYPT_PENDING__` sentinel |
  | V3 | Normalize YN columns CHAR(1) → VARCHAR(1) |
  | V5 | JEX DEV snapshot (2026-04-22): 218 BUSI_INFM + 318 USER_INFM + 382 USER_BUSI_CONN_INFM rows, all USER_PW = sentinel |
  | V6 | Scope data to single tenant |
  | V7 | Align schema to legacy JEX exact structure |
  | V8 | Add BUSI_INFM.DEL_YN |
  | V9 | Normalize BUSI_INFM char columns |
  | V10 | Add legacyuser with SHA-256 base64 hash for testing the upgrade path |

- Table/column naming: `UPPER_SNAKE`, FK constraints `FK_<child>_<parent>`. Mirror legacy JEX so data copies cleanly.

### Database (DEV — JEX live system)

- Read-only via the `postgres` MCP. Full deny list in [.claude/rules/database.md](../.claude/rules/database.md) — no INSERT / UPDATE / DELETE / DDL / EXPLAIN ANALYZE / DO $$…$$ / CALL.
- One-way flow: **DEV → SQL script / Flyway → LOCAL**. Never write back to DEV / UAT / PROD.

## Application Profiles

| File | Purpose |
|---|---|
| `application.yml` | Base — JPA/Flyway wiring, messages basename, server port 8080, `wecms.jwt.*`, `wecms.cors.*`, `wecms.tenant.biz-key=VN_VNM0004_000001`. Default active profile `local`. |
| `application-local.yml` | Developer laptop — DB URL + password default, JWT dev secret default, verbose logging. |
| `application-dev.yml` | Reserved. Not used in Sprint 0. |
| `application-prod.yml` | Reserved. Not used in Sprint 0. |
| `application-test.yml` | Integration-test profile. |

## Build & Run

```bash
./gradlew build                                              # compile + unit tests
./gradlew bootRun --args='--spring.profiles.active=local'    # run against localhost:5434
./gradlew test                                               # tests only

./run-local.bat    # Windows wrapper — sets env vars + bootRun
./run-local.sh     # POSIX equivalent
```

On first startup under `local` profile, `DataSeeder` replaces any `USER_INFM.USER_PW = '__BCRYPT_PENDING__'` sentinel with a fresh BCrypt hash of `Password!1`. Idempotent — re-runs do nothing.

Test users after seeding:
- `testuser` / `Password!1` — active, unlocked
- `lockeduser` / `Password!1` — `LOCK_YN=Y` → returns `WBE1011`
- `legacyuser` / `Password!1` — stored as legacy SHA-256 base64 hash (V10); successful login upgrades to BCrypt

## Do / Don't

**Do**
- Keep DTO JSON field names in UPPER_SNAKE matching the legacy JEX / WSVC contract.
- Throw `BusinessException("WBExxxx")` with a matching `messages.properties` entry for every known error path.
- Add a Flyway migration for every schema change; ensure the JPA entity matches (`ddl-auto: validate` will fail startup otherwise).
- Constrain every tenant-scoped query on `BIZ_KEY = <tenantProperties.bizKey>` — never a literal string, never a wildcard.
- Put Next.js-visible shape changes into matching DTO/types on both sides in the same MR (full-stack ownership rule, root CLAUDE.md).
- Use `TenantContext` to read the current user's identity inside service methods — do not re-parse the JWT or re-query the user for every call.
- Use `LangCd` enum for all Accept-Language parsing — do not re-implement string matching.

**Don't**
- Don't hardcode passwords, JWT secrets, or DB credentials. Everything sensitive reads from env / `application-local.yml`.
- Don't hand-roll an alternative response shape — always go through `ApiResponse<T>` + `CommonHead`.
- Don't add business logic in entities, controllers, or filters. It belongs in `service/`.
- Don't use `ddl-auto: update` or `create`. Flyway is the only sanctioned schema channel.
- Don't widen the tenant filter to `LIKE 'VN_VNM0004_%'` in code — the property regex rejects wildcards for a reason.
- Don't add SpringDoc / Testcontainers / Redis / any other dependency "just in case". Add it when the first feature needs it.
- Don't cross the write boundary: JEX trees are read-only references.

## Known Issues / Inconsistencies

| Issue | Location | Status |
|---|---|---|
| No auto-lock counter | `AuthService`, `USER_LOGIN_HIS` | Scaffold (RSLT_CD=FAIL rows) exists; 5-failure logic deferred |
| Logout is client-only | `AuthController.logout()` | Stub endpoint; no server-side token invalidation |
| PW_CHG_REQ_SEQ_NO unused | `UserInfm.pwChgReqSeqNo` | Mapped but not read; password-expiry flow deferred |
| NTNL_CD/CHNL_TYPE/BUSI_STTS gates missing | `AuthService.login` | Post-auth gates deferred; not in Sprint 0 scope |
| No SpringDoc / Swagger | — | Deferred; add with first non-auth module |
| No Testcontainers | `AuthServiceTest` | Tests use Mockito; H2/in-memory deferred until second domain |

## Notes for future implementation

- **SpringDoc OpenAPI** — first non-auth module should add `org.springdoc:springdoc-openapi-starter-webmvc-ui` and annotate controllers with `@Operation`.
- **Refresh tokens / logout revocation** — when needed, introduce a `REFRESH_TOKEN` table, rotate on use, and wire revocation into `JwtAuthenticationFilter`.
- **Auto-lock counter** — 5 consecutive failures → lock user. `USER_LOGIN_HIS` has the data; implement the counter in `AuthService`.
- **Password expiry** — `USER_INFM.PW_CHG_REQ_SEQ_NO` is mapped but unused. Wire to a `/auth/change-password` flow before non-local deploy.
- **Menu / permission resolution** — post-login menu tree is deferred. When it lands, introduce `domain/menu/` and return the resolved menu set inside `LoginResponse` or a `/api/v1/auth/menu` call.
- **Rate-limiting / brute-force protection** — not in scope for R&D but mandatory before any non-local deploy.
- **NTNL_CD / CHNL_TYPE / BUSI_STTS post-auth gates** — legacy JEX checks additional business flags after password validation; implement these in `AuthService.login` before production use.

# Task — Refactor the Login Feature (Local R&D)

**Scope:** Login screen + login flow only. Ports the existing JEX login to Spring Boot (`wecms-api`) + Next.js (`wecms-web`) for local R&D.
**Out of scope:** Signup, forgot-password, BIZ_KEY chooser, post-login menu/permission resolution, DEV/UAT/PROD deploy, bank integration, JEX coexistence.
**Plan date:** 2026-04-21

---

## Context

The WeCMS login currently lives in the JEX framework (`WECMS DEV/`). We are translating **only** the login feature into the new stack as R&D: Spring Boot 3.5.13 on the API side and Next.js 16 + Bootstrap 5 on the web side, against a fresh local PostgreSQL (`localhost:5434/wecms_dev`). The UI matches the Figma/screenshot: split-screen with a marketing panel on the left and an ID/Password/Save-ID/Login form on the right — **no** signup, forgot-password, or BIZ_KEY chooser.

**Decisions already taken (confirmed with user 2026-04-21):**
- **Tenant (BIZ_KEY) resolution:** server-derived. Client never sends BIZ_KEY. **Final position (2026-04-22):** strict single-tenant R&D — the active `BIZ_KEY` is pinned to the literal value **`VN_VNM0004_000001`** (the specific WeCMS business unit). No wildcards, no multi-tenant picker. `wecms.tenant.biz-key` in [application.yml](../../../src/main/resources/application.yml) is the single source of truth, validated by `TenantProperties` to reject anything containing `%`. Users not linked to this exact tenant are rejected with `WBE1009`.
- **Lockout:** `LOCK_YN`/`ACTV_YN`/`DEL_YN` flag checks + full `USER_LOGIN_HIS` logging — but the 5-failure auto-lock counter is **deferred**.
- **Tokens:** access token only, ~60-minute TTL (matches legacy session timeout).
- **Seed data source (added 2026-04-22):** login-related rows (`BUSI_INFM`, `USER_INFM`, `USER_BUSI_CONN_INFM`) are copied from JEX DEV `wecmsvndevdb` via Flyway migration `V5`. Additive, idempotent (`ON CONFLICT DO NOTHING`), never drops or resets local data.

---

## Step 1 — Legacy JEX Login Flow Analysis

### Entry points (source of truth)

| Layer | File | Key lines |
|---|---|---|
| Action (PC) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/logn/logn_0001_001_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/logn/logn_0001_001_r001_act.jsp) | 114–138, 347–376 |
| Action (Mobile) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/api/mrec/WABOOKS_MREC_R002_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/api/mrec/WABOOKS_MREC_R002_act.jsp) | 44–172 |
| WSVC (contract) | [WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.logn_0001_001_r001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.logn_0001_001_r001.xml) | — |
| Orchestrator | [WECMS DEV/WECMS/src/wabooks/helper/SzonHelper.java](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/SzonHelper.java) | `logn_2001_001_r001`, `logn_0001_001_r001` |
| Session + audit | [WECMS DEV/WECMS/src/wabooks/session/UserLoginManager.java](../../../../WECMS%20DEV/WECMS/src/wabooks/session/UserLoginManager.java) | 39–129 |
| User lookup | [WECMS DEV/WECMS/src/wabooks/dao/UserInfmDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/UserInfmDAO.java) + [IDO.USER_INFM_R004.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.USER_INFM_R004.xml) |
| Entities | [UserInfm.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/UserInfm.java), [UserLoginHis.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/UserLoginHis.java), [UserBusiConnInfm.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/UserBusiConnInfm.java) |

### Request / response flow

```
frontend login → POST /logn_0001_001_r001.act
  ↓ normalizeInput() — trims USER_ID/USER_PW
  ↓ validateInput() — required USER_ID, USER_PW, BIZ_KEY; validateUserId()
  ↓ SzonHelper.logn_2001_001_r001(...)
      ↓ UserInfmDAO.getUserWithSpecificCompany()  — JOIN on USER_BUSI_CONN_INFM; password compared in DB via hash()
      ↓ DEL_YN='Y'       → WBE1128
      ↓ not found        → WBE1009 (+ insert USER_LOGIN_HIS RSLT_CD=FAIL)
      ↓ ACTV_YN='N'      → WBE1010
      ↓ LOCK_YN='Y'      → WBE1011 (unless password was reset since lock)
      ↓ 5 consecutive FAILs since last SUCCESS → set LOCK_YN='Y' + WBE1011
  ↓ UserLoginManager.doLogin()
      ↓ HttpSession.setAttribute("WABOOKS_USER_SESSION", loginUser)
      ↓ setMaxInactiveInterval(3600)
      ↓ UPDATE USER_BUSI_CONN_INFM SET LST_LGN_DATE = now()
      ↓ INSERT USER_LOGIN_HIS RSLT_CD=SUCCESS
  ↓ result.put("WEB_SVC_ID", <landing>)  ← post-login menu resolution (OUT OF SCOPE)
  ↓ JWT issued via JwtHelper.getToken(loginUser) — mobile path only
```

### Validation rules (legacy)

| Field | Rule | Source |
|---|---|---|
| `USER_ID` | required, trimmed, `validateUserId` regex | `ValidationHelper.validateRequired/validateUserId` |
| `USER_PW` | required, trimmed, `USER_PW_PATTERN` | `UserInfm.java:54` |
| `BIZ_KEY` | required (mobile client sends it; web form has it hidden) | `logn_0001_001_r001_act.jsp:122` |

### Data model (legacy tables in scope)

| Table | Role | Key columns used at login |
|---|---|---|
| `USER_INFM` | User master | `USER_ID` (PK), `USER_PW` (PostgreSQL `hash()`), `USER_NM`, `ACTV_YN`, `LOCK_YN`, `DEL_YN`, `EML`, `CLPH_NO` (encrypted), `REG_DTM`, `MOD_DTM`, `PW_CHG_REQ_SEQ_NO` |
| `USER_BUSI_CONN_INFM` | User↔company mapping (multi-tenant) | `USER_ID`+`BIZ_KEY` (PK), `EMPL_NO`, `LST_LGN_DATE`, `MOB_LST_LGN_DATE`, `DEL_YN` |
| `USER_LOGIN_HIS` | Audit log | `USER_ID`+`REG_DTM` (PK), `IP_ADDR`, `LGN_CHNL` (PC/MOB), `RSLT_CD` (SUCCESS/FAIL), `RSLT_CONT` |
| `BUSI_INFM` | Company master (needed only to resolve BIZ_KEY → company name for the JWT claim) | `BIZ_KEY` (PK), `BUSI_NM`, `BUSI_STTS`, `NTNL_CD` |

### Legacy ⇄ New mapping

| Legacy concept | New implementation | Notes |
|---|---|---|
| JSP `_act.jsp` + WSVC | `AuthController` + `LoginRequest/LoginResponse` DTOs | — |
| JEX IDO + DAO | Spring Data JPA repositories | Parameter binding replaces `IDODynamic` |
| PG `hash()` password | BCrypt (`PasswordEncoder`) | Fresh DB — re-seed test users with bcrypt hashes |
| HttpSession + JWT | JWT only (stateless) | 60-min TTL mirrors legacy `setMaxInactiveInterval(3600)` |
| `WbMessageCode` + `dictionary_*.properties` | `MessageSource` + Spring i18n (EN-only for sprint; other locales deferred) | Preserve legacy codes `WBE1009/1010/1011/1128`, `WBI0000` |
| `AppLogManager` | SLF4J + Logback (default Spring Boot) | — |
| `CookieUtils` "Save ID" | Browser `localStorage` in Next.js | Purely client-side |
| `LgnChnl.PC` | `LGN_CHNL="PC"` | Web login only — mobile channel deferred |
| Post-login `WEB_SVC_ID` resolution | **OUT OF SCOPE** | Deferred |

---

## Step 2 — Proposed Backend Structure (`wecms-api`)

Follows [wecms-api/CLAUDE.md](../../../CLAUDE.md) — domain-driven, lightweight. `auth` is the JEX `logn` module.

```
src/main/
├── java/com/kosign/wecms/
│   ├── WecmsApiMainApplication.java              — existing
│   ├── config/
│   │   ├── SecurityConfig.java                   — Spring Security, stateless, JWT filter chain
│   │   ├── CorsConfig.java                       — allow http://localhost:3000
│   │   └── JwtProperties.java                    — @ConfigurationProperties(prefix="wecms.jwt")
│   ├── common/
│   │   ├── entity/BaseEntity.java                — REG_ID, REG_DTM, MOD_ID, MOD_DTM (@MappedSuperclass)
│   │   ├── response/ApiResponse.java             — { COMMON_HEAD, REC } wrapper (matches docs/api-spec/common.md)
│   │   ├── response/CommonHead.java              — { ERROR, CODE, MESSAGE }
│   │   ├── exception/BusinessException.java      — carries WBE code + args
│   │   ├── exception/GlobalExceptionHandler.java — @RestControllerAdvice → ApiResponse error
│   │   ├── i18n/MessageResolver.java             — wraps MessageSource; resolves WBE codes
│   │   └── security/
│   │       ├── JwtTokenProvider.java             — sign / parse / validate (jjwt 0.12.x)
│   │       └── JwtAuthenticationFilter.java      — Authorization: Bearer … → SecurityContext
│   └── domain/auth/
│       ├── entity/UserInfm.java                  — @Table("USER_INFM")
│       ├── entity/UserBusiConnInfm.java          — @Table("USER_BUSI_CONN_INFM"), @IdClass
│       ├── entity/UserBusiConnInfmId.java        — composite PK (USER_ID, BIZ_KEY)
│       ├── entity/UserLoginHis.java              — @Table("USER_LOGIN_HIS"), @IdClass
│       ├── entity/UserLoginHisId.java            — composite PK (USER_ID, REG_DTM)
│       ├── repository/UserInfmRepository.java
│       ├── repository/UserBusiConnInfmRepository.java
│       ├── repository/UserLoginHisRepository.java
│       ├── service/AuthService.java              — login orchestration (flag checks + password verify + token issue)
│       ├── service/LoginHistoryService.java      — writes SUCCESS/FAIL rows
│       ├── controller/AuthController.java        — @PostMapping("/api/v1/auth/login")
│       └── dto/LoginRequest.java                 — { USER_ID, USER_PW } + Jakarta validation
│       └── dto/LoginResponse.java                — { TOKEN, TOKEN_TYPE, EXPIRES_IN, USER_ID, USER_NM, BIZ_KEY, LANG_CD }
└── resources/
    ├── application.yml                           — base (active profile: local)
    ├── application-local.yml                     — .gitignored; dev DB + JWT secret via env
    ├── db/migration/
    │   ├── V1__auth_schema.sql                   — BUSI_INFM, USER_INFM, USER_BUSI_CONN_INFM, USER_LOGIN_HIS
    │   └── V2__auth_seed_dev.sql                 — 1 company + 1 test user (bcrypt hash)
    └── messages/
        ├── messages.properties                   — EN defaults (WBI0000, WBE1009/1010/1011/1128)
        └── messages_en.properties                — mirror EN
```

### API contract

`POST /api/v1/auth/login` — public endpoint (permitted in SecurityConfig)

**Request:**
```json
{ "USER_ID": "testuser", "USER_PW": "Password!1" }
```

**Success (200):**
```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "TOKEN": "eyJ…",
    "TOKEN_TYPE": "Bearer",
    "EXPIRES_IN": 3600,
    "USER_ID": "testuser",
    "USER_NM": "Test User",
    "BIZ_KEY": "VN_VNM0004_000001",
    "LANG_CD": "EN"
  }
}
```

**Error (200 with `ERROR: true` — matches legacy pattern):**
```json
{ "COMMON_HEAD": { "ERROR": true, "CODE": "WBE1009", "MESSAGE": "ID or password does not match." } }
```

### Dependencies to add to `build.gradle`

```groovy
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly     'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly     'io.jsonwebtoken:jjwt-jackson:0.12.6'
implementation 'org.flywaydb:flyway-core'
runtimeOnly     'org.flywaydb:flyway-database-postgresql'
```

SpringDoc / Testcontainers are **deferred** for this sprint — the existing Spring Boot Test + H2-or-Postgres local test is sufficient for login.

### Authentication behavior (AuthService.login)

1. Trim and validate `USER_ID` / `USER_PW` (`@NotBlank`, `@Size` on DTO).
2. Load `UserInfm` by `USER_ID` — **not found** → log FAIL, throw `WBE1009`.
3. `DEL_YN='Y'` → log FAIL, throw `WBE1128`.
4. `ACTV_YN='N'` → log FAIL, throw `WBE1010`.
5. `LOCK_YN='Y'` → log FAIL, throw `WBE1011`. *(5-failure auto-lock transition is deferred — Step 6.)*
6. `passwordEncoder.matches(rawPw, userInfm.userPw)` false → log FAIL, throw `WBE1009`.
7. Resolve single tenant: `UserBusiConnInfmRepository.findByUserId` — expect exactly one row with `DEL_YN='N'`. Zero/multiple → log FAIL, throw `WBE1009` (generic error; multi-tenant chooser deferred).
8. Update `USER_BUSI_CONN_INFM.LST_LGN_DATE = now()`.
9. Insert `USER_LOGIN_HIS` `RSLT_CD='SUCCESS'`, `LGN_CHNL='PC'`, `IP_ADDR=` request remote addr.
10. Issue JWT with claims `{ sub=userId, bizKey, userNm, iat, exp=iat+3600s }`, signed HS256 with secret from env.
11. Return `LoginResponse`.

### Security config

- Stateless (`SessionCreationPolicy.STATELESS`).
- Permit `/api/v1/auth/login` unauthenticated; everything else requires Bearer JWT.
- `BCryptPasswordEncoder` bean (strength 10).
- Secret via env `WECMS_JWT_SECRET` (fail-fast on startup if missing).
- CORS: allow `http://localhost:3000` for `POST /api/v1/auth/**`.

---

## Step 3 — Proposed Frontend Structure (`wecms-web`)

Follows [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) — Next.js 16 App Router, Bootstrap 5 + react-bootstrap, mobile-first responsive.

> ⚠️ **Next.js 16 caveat** — per [wecms-web/AGENTS.md](../../../../wecms-web/AGENTS.md), Next.js 16 has breaking changes from earlier versions. The implementer must consult `node_modules/next/dist/docs/` (especially App Router, route handlers, metadata) before coding rather than relying on training-data memory.

```
wecms-web/
├── app/
│   ├── layout.tsx                        — imports bootstrap CSS + SCSS; sets <html lang="en">
│   ├── globals.scss                      — replaces globals.css; imports bootstrap, custom vars
│   ├── (auth)/
│   │   ├── layout.tsx                    — split-screen responsive wrapper (marketing panel + form panel)
│   │   └── login/
│   │       ├── page.tsx                  — server component shell → renders <LoginForm>
│   │       └── loginSchema.ts            — Zod: { USER_ID: required, USER_PW: required }
│   └── page.tsx                          — redirect to /login (and later to /dashboard when authed)
├── components/
│   ├── auth/
│   │   ├── LoginForm.tsx                 — client component; React Hook Form + Zod; Save ID; show/hide password
│   │   └── MarketingPanel.tsx            — left panel: 8 feature icons (from screenshot) + tagline; hidden <md
│   └── shared/Logo.tsx                   — "WeCMS" wordmark SVG
├── lib/
│   ├── axios.ts                          — axios instance; baseURL = NEXT_PUBLIC_API_BASE_URL
│   ├── auth/client.ts                    — login(userId, userPw) → AuthResponse (typed from shared DTO)
│   ├── auth/storage.ts                   — token get/set (localStorage for R&D); Save-ID get/set
│   └── types/api.ts                      — ApiResponse<T>, CommonHead, LoginResponseRec (mirrors backend)
├── public/
│   └── login/                            — 8 feature icons + WeCMS logo (copied from Figma export)
├── .env.example                          — NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
└── (package.json, tsconfig.json, etc.)
```

### Sprint 0 prerequisites (mandated by [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) §"Tailwind → Bootstrap conversion pending")

Before the login UI is written:

1. `npm uninstall tailwindcss @tailwindcss/postcss`
2. `npm install bootstrap react-bootstrap sass axios react-hook-form zod @hookform/resolvers`
3. Delete `postcss.config.mjs` if it only supports Tailwind.
4. Replace Tailwind imports in `app/globals.css` → rename to `app/globals.scss`, import Bootstrap.
5. In `app/layout.tsx`, import `./globals.scss` (which itself imports Bootstrap).

These steps are part of this task because the login page is the first real UI and cannot land with Tailwind still active.

### Responsive strategy (per CLAUDE.md §"Responsive Design")

| Breakpoint | Layout |
|---|---|
| `<md` (mobile) | `MarketingPanel` hidden (`d-none d-md-flex`); form panel fills viewport. |
| `md` (tablet, ≥768) | Two columns via `row` + `col-md-6`/`col-md-6`. |
| `xl` (desktop, ≥1200) | Two columns with marketing on the left taking `col-xl-7`, form on `col-xl-5`; form is max-width 420px, centered. |

Form inputs use `<InputGroup>` (icon prefix) + `<Form.Control>`; password field has a show/hide toggle button. "Save ID" is a `<Form.Check>`. The "LOGIN" button uses `variant="primary"` and is full-width.

### Client-side login flow

1. On submit: React Hook Form validates against Zod schema.
2. Call `lib/auth/client.login(userId, userPw)` → `POST ${NEXT_PUBLIC_API_BASE_URL}/api/v1/auth/login`.
3. If `COMMON_HEAD.ERROR === true` → show inline error message (Bootstrap `<Alert variant="danger">`) from `COMMON_HEAD.MESSAGE`; clear password field.
4. On success → `storage.setToken(rec.TOKEN)`; if "Save ID" checked → `storage.setSavedUserId(rec.USER_ID)` else `storage.clearSavedUserId()`; `router.push("/dashboard")` (dashboard stub will just display the token + user info for R&D).
5. On `LoginForm` mount → `defaultValues.USER_ID = storage.getSavedUserId() ?? ""`; `savedId` checkbox checked accordingly.

---

## Step 4 — Implementation Plan (ordered)

Each step is small enough to verify before the next.

1. **Scaffold backend config** — add Gradle dependencies; create `application.yml` + gitignored `application-local.yml`; create `common/` skeleton (BaseEntity, ApiResponse, CommonHead, BusinessException, GlobalExceptionHandler, MessageResolver); create i18n `messages.properties` with the 5 codes (`WBI0000`, `WBE1009`, `WBE1010`, `WBE1011`, `WBE1128`).
2. **Database schema** — write Flyway `V1__auth_schema.sql` (BUSI_INFM, USER_INFM, USER_BUSI_CONN_INFM, USER_LOGIN_HIS) + `V2__auth_seed_dev.sql` (one company `VN_VNM0004_000001`, one user with a known bcrypt hash for `Password!1`). `./gradlew bootRun` must succeed and Flyway must apply both migrations.
3. **Security + JWT plumbing** — `JwtProperties`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `SecurityConfig`, `CorsConfig`, `BCryptPasswordEncoder` bean. Verify: a dummy unsecured `/actuator/health` or a `/api/v1/auth/ping` returns 200; a secured test endpoint returns 401 without a Bearer token.
4. **Auth domain** — entities, repositories, `LoginHistoryService`, `AuthService`, `AuthController`, DTOs. Integration test: POST `/api/v1/auth/login` with seeded credentials returns a signed token; bad password returns `WBE1009`; locked user returns `WBE1011`.
5. **Frontend Sprint 0 prerequisites** — Tailwind → Bootstrap swap; add axios/react-hook-form/zod; verify `npm run dev` renders the default page with Bootstrap typography.
6. **Frontend login UI** — `(auth)/layout.tsx`, `(auth)/login/page.tsx`, `LoginForm`, `MarketingPanel`, `lib/axios.ts`, `lib/auth/{client,storage}.ts`, `lib/types/api.ts`, `.env.example`. Match the screenshot: split-screen on ≥md, form-only <md.
7. **End-to-end verification** — run backend + frontend locally; submit valid credentials → dashboard stub shows username + token claims; submit wrong password → inline error; deactivated/locked seeded users → correct WBE code; reload page → Save ID restores USER_ID when previously checked.

---

## Step 5 — Files to be Created / Modified

### `wecms-api/` — new files

- `build.gradle` *(edit: add validation, jjwt, Flyway)*
- `.gitignore` *(edit: add `application-local.yml`)*
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml` *(template; real values from env)*
- `src/main/resources/db/migration/V1__auth_schema.sql`
- `src/main/resources/db/migration/V2__auth_seed_dev.sql`
- `src/main/resources/messages.properties`
- `src/main/java/com/kosign/wecms/common/entity/BaseEntity.java`
- `src/main/java/com/kosign/wecms/common/response/ApiResponse.java`
- `src/main/java/com/kosign/wecms/common/response/CommonHead.java`
- `src/main/java/com/kosign/wecms/common/exception/BusinessException.java`
- `src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java`
- `src/main/java/com/kosign/wecms/common/i18n/MessageResolver.java`
- `src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java`
- `src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java`
- `src/main/java/com/kosign/wecms/config/SecurityConfig.java`
- `src/main/java/com/kosign/wecms/config/CorsConfig.java`
- `src/main/java/com/kosign/wecms/config/JwtProperties.java`
- `src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java`
- `src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfm.java`
- `src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfmId.java`
- `src/main/java/com/kosign/wecms/domain/auth/entity/UserLoginHis.java`
- `src/main/java/com/kosign/wecms/domain/auth/entity/UserLoginHisId.java`
- `src/main/java/com/kosign/wecms/domain/auth/repository/UserInfmRepository.java`
- `src/main/java/com/kosign/wecms/domain/auth/repository/UserBusiConnInfmRepository.java`
- `src/main/java/com/kosign/wecms/domain/auth/repository/UserLoginHisRepository.java`
- `src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java`
- `src/main/java/com/kosign/wecms/domain/auth/service/LoginHistoryService.java`
- `src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java`
- `src/main/java/com/kosign/wecms/domain/auth/dto/LoginRequest.java`
- `src/main/java/com/kosign/wecms/domain/auth/dto/LoginResponse.java`
- `src/test/java/com/kosign/wecms/domain/auth/AuthControllerIntegrationTest.java`

### `wecms-web/` — new files + edits

- `package.json` *(edit: remove tailwind, add bootstrap, react-bootstrap, sass, axios, react-hook-form, zod, @hookform/resolvers)*
- `postcss.config.mjs` *(delete if Tailwind-only)*
- `app/layout.tsx` *(edit)*
- `app/globals.css` → rename to `app/globals.scss` *(edit)*
- `app/page.tsx` *(edit: redirect to `/login`)*
- `app/(auth)/layout.tsx`
- `app/(auth)/login/page.tsx`
- `app/(auth)/login/loginSchema.ts`
- `components/auth/LoginForm.tsx`
- `components/auth/MarketingPanel.tsx`
- `components/shared/Logo.tsx`
- `lib/axios.ts`
- `lib/auth/client.ts`
- `lib/auth/storage.ts`
- `lib/types/api.ts`
- `.env.example`
- `public/login/*.svg` *(8 feature icons + logo from Figma)*

---

## Step 6 — Migration Notes, Deferred Items, Assumptions

### Data copied from JEX DEV (added 2026-04-22)

Flyway migration [`V5__seed_from_jex_dev_20260422.sql`](../../../src/main/resources/db/migration/V5__seed_from_jex_dev_20260422.sql) populates the local `wecms_dev` with login-related rows from JEX DEV (`wecmsvndevdb`):

| Table | Rows copied | Filter |
|---|---|---|
| `BUSI_INFM` | 218 | Tenants referenced by at least one active user↔tenant link |
| `USER_INFM` | 318 | `DEL_YN='N'` AND has at least one active link to an existing tenant |
| `USER_BUSI_CONN_INFM` | 382 | `DEL_YN='N'` on both sides |

**Rules this respects (per directive 2026-04-22):**
- **No drop / no truncate / no schema recreation.** Every INSERT has `ON CONFLICT DO NOTHING`, so existing local rows are preserved.
- **No startup wipe.** `spring.jpa.hibernate.ddl-auto: validate` never touches the schema; Flyway versioned migrations run once per version.
- **Login-only scope.** Encrypted phone, user images, `PW_CHG_REQ_SEQ_NO`, `MOB_LST_LGN_DATE`, legacy `BUSI_INFM` address/tax/erp columns are **not** copied.
- **Password translation.** Legacy `USER_PW` (PostgreSQL `hash()` output) is not BCrypt-compatible. Copied rows land with the sentinel `__BCRYPT_PENDING__`; the `DataSeeder` `ApplicationRunner` rewrites it to `bcrypt("Password!1")` on the first `local`-profile startup. Every imported user logs in with `Password!1`.
- **Multi-tenant handling (superseded by V6).** An earlier iteration auto-picked the most-recently-used tenant when a user had multiple active links. This is replaced as of 2026-04-22 by a hard single-tenant filter — see the "Single-tenant scope reduction" section below.
- **Audit fields.** `REG_USER`/`MOD_USER` set to `'SYSTEM'`; `REG_DTM`/`MOD_DTM` converted from legacy `VARCHAR(14)` (`YYYYMMDDHHMMSS`) to `TIMESTAMPTZ`.
- **Legacy flag preservation.** `ACTV_YN`/`LOCK_YN` are copied verbatim. Locked-in-DEV users stay locked locally (verified with `soknyran.dev@gmail.com` → `WBE1011`).

**Regenerating the seed:** re-run the MCP `postgres` queries in the conversation transcript or ask the agent to `/generate V5 migration from JEX DEV`. Because the migration is versioned, the regenerated file must have a new `V{n}__` prefix (e.g., `V7__…`) — never edit V5 or V6 after they've been applied, or Flyway's checksum validation will fail.

### Password handling — legacy verified + hybrid encoder (added 2026-04-22)

**Legacy behaviour (verified via reading JEX code + the live legacy DB):**

- `SzonHelper.java:216/231/251/276/309` all build the same dynamic WHERE clause: `and USER_PW = hash( ? )` with the plaintext bound as parameter.
- `USER_PW` is stored as the output of the custom PostgreSQL function `public.hash(text)`:
  ```sql
  CREATE OR REPLACE FUNCTION public.hash(txt text)
    RETURNS text LANGUAGE sql IMMUTABLE STRICT
    AS $$ select encode(public.digest($1, 'sha256'), 'base64') $$;
  ```
  That is, **Base64(SHA-256(UTF-8 plaintext))** — unsalted, deterministic, 44-char output. (`SELECT hash('Password!1')` returns `vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=` on wecmsvndevdb.)
- Comparison happens inside the SQL WHERE clause — the plaintext never touches Java.

**Explicit R&D decision:** the new stack uses a **hybrid encoder** — strongest-available for writes, legacy-compatible for reads.

| Path | Algorithm | Why |
|---|---|---|
| Write (new/updated passwords) | BCrypt, strength 10 | Salted per-user, adaptive cost, Spring Security default. The legacy unsalted SHA-256 is cryptographically weak; perpetuating it into the new codebase would be a regression. |
| Read (login verify) | BCrypt **or** Legacy SHA-256+base64 | Rows copied directly from wecmsvndevdb still authenticate without a bulk rehash migration. |
| Upgrade-on-match | Legacy hash → BCrypt, transparently | After the first successful login with a legacy-format hash, `AuthService` re-encodes with BCrypt and saves. Old format is phased out organically. |

**Implementation:**

- **[`LegacyHashPasswordEncoder`](../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java)** — Java reimplementation of `public.hash()`. `Base64.encode(SHA-256(UTF-8 bytes))`. Constant-time compare.
- **[`WecmsPasswordEncoder`](../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java)** — composes `BCryptPasswordEncoder(10)` + `LegacyHashPasswordEncoder`. Dispatches on the `$2a/$2b/$2y$` prefix; `upgradeEncoding()` returns `true` for any non-BCrypt stored value.
- **[`SecurityConfig`](../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java)** — exposes `WecmsPasswordEncoder` as the `PasswordEncoder` bean.
- **[`AuthService`](../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java)** — after a successful `matches()`, calls `upgradeEncoding()` and re-encodes + `userRepo.save()` if true. Logs `Upgraded stored password for user X from legacy hash to BCrypt`.
- **[`LegacyHashPasswordEncoderTest`](../../../src/test/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoderTest.java)** — pins `encode("Password!1")` to the exact reference value I pulled from `SELECT hash('Password!1')` on wecmsvndevdb. Fails loudly if either side ever drifts.
- **[`V10__demo_legacy_hash_user.sql`](../../../src/main/resources/db/migration/V10__demo_legacy_hash_user.sql)** — seeds a `legacyuser` row whose `USER_PW` is the raw legacy hash, so the upgrade path is demonstrable end-to-end.

**Live verification (2026-04-22):**

| Scenario | Result |
|---|---|
| `testuser` + `Password!1` (stored as BCrypt) | ✅ `WBI0000`, no upgrade |
| `legacyuser` + `Password!1` (stored as `vm/e2euhU9d0...`) — 1st login | ✅ `WBI0000`, log line: `Upgraded stored password for user legacyuser from legacy hash to BCrypt` |
| `legacyuser` + `Password!1` — 2nd login (now BCrypt) | ✅ `WBI0000`, no upgrade (idempotent) |
| `legacyuser` + wrong password | ✅ `WBE1009` |

**What's deliberately NOT replicated:**

- Legacy performs comparison in the DB WHERE clause — we moved it into Java via the `PasswordEncoder` contract. Same end-state, much more flexible.
- Legacy stored the function inside Postgres (`public.hash()`) and also `public.digest()` (pgcrypto). We do not require the local DB to have either — the Java encoder is self-contained.
- Legacy has no salt. New passwords get BCrypt's per-user salt. This is the explicit simplification.

### Schema alignment to legacy — `V7/V8/V9` (added 2026-04-22)

Directive: local schema must mirror WeCMS DEV exactly. No renames, no removals, all legacy columns present. Types may be adjusted.

- **[`V7__align_schema_to_legacy_exact.sql`](../../../src/main/resources/db/migration/V7__align_schema_to_legacy_exact.sql)** — one migration that:
  - Renames `REG_USER → REG_ID`, `MOD_USER → MOD_ID` on all three tables (matches legacy audit columns)
  - Renames `BUSI_INFM.BUSI_NM → CMPN_NM`
  - Drops `USER_INFM.LANG_CD` — this column was invented by me; legacy never had it. `LANG_CD` is now derived from the `Accept-Language` request header and returned in the login response / JWT claim.
  - Adds 9 missing legacy columns to `USER_INFM` (`CLPH_NO`, `USER_IMG`, `LST_LGN_DATE`, `MOB_LST_LGN_DATE`, `PW_CHG_REQ_SEQ_NO`, `BIZ_KEY`, `EMPL_NO`, `DEPT_NM`, `POSI_NM`)
  - Adds 2 missing legacy columns to `USER_BUSI_CONN_INFM` (`USER_IMG`, `MOB_LST_LGN_DATE`)
  - Adds 36 missing legacy columns to `BUSI_INFM` — the table now has all 45 legacy columns (`CMPN_ENM`, `TAX_ID`, `RPPR_NM`, `TEL_NO`, `FXNO`, `EML`, address block, `JOIN_DT`, `FST_JOIN_*`, `FST_USE_DATE`, date range block, `BUSI_STTS`, `NTNL_CD`, banking block, `CHNL_ID`, `OVERDUE_YN`, `CANC_DATE`, `WIZ_RUN_YN`, `CMPN_STAMP`, `BUSI_NICK_NM`, `ALLI_CMPN_*`, `PRDT_TYPE`, `OWAC_NM`, `MEMO`, `PAID_START_DATE`, `CMPN_QR_CD_IMG`, `ERP_CD`)
- **[`V8__add_busi_infm_del_yn.sql`](../../../src/main/resources/db/migration/V8__add_busi_infm_del_yn.sql)** — `BUSI_INFM.DEL_YN` was missed in V1 and again in V7; this one-line migration adds it and backfills the seeded row to `'N'`.
- **[`V9__normalise_busi_infm_char_columns.sql`](../../../src/main/resources/db/migration/V9__normalise_busi_infm_char_columns.sql)** — widens `WIZ_RUN_YN` and `ERP_CD` from `CHAR(1)` to `VARCHAR(1)` for the same reason V3 normalised the YN columns (Hibernate treats `String @Column(length=1)` as VARCHAR(1)).

**Type adjustments** (within the "types may be adjusted" allowance):
| Legacy | Local | Why |
|---|---|---|
| `VARCHAR(14)` date-time strings (`REG_DTM`, `LST_LGN_DATE`, `JOIN_DT`, `FST_USE_DATE`) | `TIMESTAMPTZ` | Proper type, Hibernate-friendly, still copiable via `TO_TIMESTAMP` |
| `VARCHAR(8)` date strings (`USE_DATE_FROM`, `USE_DATE_TO`, `LST_PAY_DATE`, etc.) | `DATE` | Same reasoning |
| `CHAR(1)` (`WIZ_RUN_YN`, `ERP_CD`) | `VARCHAR(1)` | Hibernate mapping (single-char String → VARCHAR) |
| `text` unlimited (`CMPN_STAMP`) | `TEXT` | PostgreSQL equivalent |

**Nullability relaxed.** Legacy has many NOT NULL columns (e.g. `TAX_ID`, `CMPN_ENM`, `CHNL_ID`). Locally these are nullable so the existing R&D rows (V2 seeds + V5/V6 filtered imports) stay valid without fabricating placeholder values. A future direct COPY from legacy supplies the real values.

**LoginResponse / JWT** still returns `LANG_CD` — now resolved in [`AuthController`](../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java) from the `Accept-Language` header (first tag mapped to `EN/KO/KM/VI`, default `EN`). Verified live:
- `Accept-Language: ko-KR,ko;q=0.9` → `LANG_CD: "KO"`
- no header → `LANG_CD: "EN"`

### Single-tenant scope reduction — `V6` (added 2026-04-22)

Directive: the local DB must hold and use only rows for BIZ_KEY **`VN_VNM0004_000001`**. No wildcards.

- **[`V6__scope_to_single_tenant.sql`](../../../src/main/resources/db/migration/V6__scope_to_single_tenant.sql)** — explicit, versioned, one-shot DELETE:
  1. `USER_BUSI_CONN_INFM` rows where `BIZ_KEY <> 'VN_VNM0004_000001'`
  2. `USER_INFM` rows with no remaining active link (orphans)
  3. `BUSI_INFM` rows other than the scope
  4. `USER_LOGIN_HIS` rows for users that no longer exist
- **Runtime enforcement:** [`AuthService`](../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java) reads `TenantProperties.bizKey` and calls `findByUserIdAndBizKeyAndDelYn(userId, bizKey, "N")`. An `Optional.empty()` → `WBE1009` + `USER_LOGIN_HIS` row with `RSLT_CONT = TENANT_OUT_OF_SCOPE`.
- **Config validation:** `TenantProperties.bizKey` is `@Pattern(regexp = "^[A-Z0-9_]+$")`, which forbids `%` and guarantees the value can never drift into a `LIKE`-style wildcard.
- **Is this a "reset"?** No. The app never drops data by itself — Flyway runs each versioned migration exactly once. `V6` is a deliberate, reviewed scope change, not an automatic wipe.

Post-`V6` data counts (verified 2026-04-22):
- `BUSI_INFM`: 1 (`VN_VNM0004_000001`)
- `USER_INFM`: ~49 (47 imported from DEV with links into the scope + 2 original V2 seeds)
- `USER_BUSI_CONN_INFM`: matches user count (each has exactly one link)

Live curl verification:
- `testuser` / `Password!1` → **WBI0000** (in-scope V2 seed)
- `arthurmorgan@rdr2.com` / `Password!1` → **WBI0000** (in-scope imported user)
- `Tokatabasic@gmail.com.vn` / `Password!1` → **WBE1009** (was linked to 17 `VN_VNM0001_*` tenants; all deleted by V6)
- `testyj@gmail.com` / `Password!1` → **WBE1009** (only had `VN_VNM0001_*` links)

### Preserved verbatim from legacy

- Input contract: `USER_ID`, `USER_PW` both trimmed; `@NotBlank`; same `USER_ID` regex as `ValidationHelper.validateUserId`.
- Account flag gates, in order, with identical error codes:
  `DEL_YN='Y'` → **WBE1128**; `ACTV_YN='N'` → **WBE1010**; `LOCK_YN='Y'` → **WBE1011**; missing row or bad password → **WBE1009**.
- `USER_LOGIN_HIS` row on every attempt (SUCCESS + FAIL) with `USER_ID`, `REG_DTM`, `IP_ADDR`, `LGN_CHNL='PC'`, `RSLT_CD`, `RSLT_CONT`.
- `USER_BUSI_CONN_INFM.LST_LGN_DATE = now()` on success.
- Tenant is derived server-side via the `USER_INFM` ↔ `USER_BUSI_CONN_INFM` linkage (legacy expects exactly one active row for the login path).
- 60-minute session lifetime: legacy `setMaxInactiveInterval(3600)` → JWT `exp = iat + 3600s`.
- Response envelope `{ COMMON_HEAD, REC }` (per [docs/api-spec/common.md](../../../../docs/jex/api-spec/common.md)).
- Table names (`USER_INFM`, `USER_BUSI_CONN_INFM`, `USER_LOGIN_HIS`, `BUSI_INFM`) and UPPER_SNAKE_CASE field IDs.

### Simplified (vs. legacy)

- **Password hashing:** PostgreSQL `hash()` → BCrypt. The fresh local DB is seeded with bcrypt hashes; no cross-scheme migration is needed.
- **Tenant resolution:** no BIZ_KEY input field. Backend resolves the single BIZ_KEY from `USER_BUSI_CONN_INFM` by `USER_ID`.
- **Session:** JWT only, stateless. No `HttpSession`, no `SessionManager`, no `WABOOKS_USER_SESSION` attribute.
- **i18n:** EN-only message bundle for the sprint; bundle structure supports KO/KM/VI as a drop-in later.
- **"Save ID":** client-side `localStorage`, not a server-side cookie.
- **Error envelope:** preserved at `{COMMON_HEAD, REC}` to keep API consumers (future TS code) ergonomically close to the JEX pattern.

### Deferred (explicitly out of scope for this task)

- 5-failure auto-lock counter + `LOCK_YN` auto-transition (decision 2026-04-21).
- `USER_PW_CHG_HIS`-based lock reset (depends on the deferred counter + out-of-scope password-change flow).
- `BUSI_INFM.NTNL_CD ∈ {KH,VN}` check, `ChnlInfm.CHNL_TYPE != 'T'` check, `BUSI_STTS = NORMAL` gate — all post-auth gating, not login itself.
- Refresh token and `/auth/refresh` endpoint.
- Post-login menu resolution — legacy `logn_0001_001_r001_act.jsp` picks an initial `WEB_SVC_ID`; dashboard stub here just shows token/user info.
- `ENV_JSON` bootstrap payload from `AppConfig`.
- Admin-path login (`BusiAdmnLoginHis`), billing setup (`UserBltbConnInfm`), permission initialisation (`UserMenuAuthInfm`) — land with their own modules.
- Logout endpoint — trivial in stateless JWT ("client drops token"), added when a revocation list is introduced.
- Mobile channel (`LGN_CHNL="MOB"`), API gateway auth (`API_CRTS_KEY`), WTA user restrictions.
- Password strength policy and password-change flow.
- Korean / Khmer / Vietnamese message bundles.
- Next.js `middleware.ts` to protect non-login routes — deferred until the first protected page exists.

### Assumptions

- Local PostgreSQL (`localhost:5434/wecms_dev`) is reachable and the `postgres` user password is set via `WECMS_DB_PASSWORD` env var (per [wecms-api/CLAUDE.md](../../../CLAUDE.md)).
- `WECMS_JWT_SECRET` env var will be set for `bootRun` (we will document this in `application-local.yml` comments and fail fast if missing).
- The Next.js dev server on `http://localhost:3000` is the only CORS origin for R&D.
- The 8 feature icons + WeCMS logo can be exported from Figma as SVG; if not yet available, we use placeholder SVGs with the correct labels.

### Open items (ask the team)

- Exact colour/typography tokens from the Figma design system (will fall back to Bootstrap defaults + WeCMS-blue `#0056b3` if not provided before implementation).
- Whether the seeded test user should use a "realistic" ID scheme (e.g., company email) or a throwaway (`testuser`). Defaulting to `testuser` / `Password!1` unless told otherwise.

---

## Verification

After implementation, verify end-to-end:

**Backend (`cd wecms-api`):**
```bash
export WECMS_DB_PASSWORD='…'    # the local postgres password
export WECMS_JWT_SECRET='…'     # any 32+ char secret for local
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test                  # runs AuthControllerIntegrationTest
```

```bash
# Success path
curl -sS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"USER_ID":"testuser","USER_PW":"Password!1"}'
# Expect: COMMON_HEAD.ERROR=false, CODE=WBI0000, REC.TOKEN present

# Failure paths
curl -sS -X POST … -d '{"USER_ID":"testuser","USER_PW":"wrong"}'   # → WBE1009
curl -sS -X POST … -d '{"USER_ID":"nouser","USER_PW":"x"}'          # → WBE1009
# (Seed a second locked user to verify WBE1011.)
```

Confirm DB state: a row in `USER_LOGIN_HIS` per attempt (`RSLT_CD`=SUCCESS/FAIL); on success, `USER_BUSI_CONN_INFM.LST_LGN_DATE` is updated.

**Frontend (`cd wecms-web`):**
```bash
cp .env.example .env.local
npm install
npm run dev                     # http://localhost:3000
npm run lint
```

Manually exercise in Chrome + responsive mode:
- Desktop (≥1200 px): split-screen, marketing panel on left.
- Tablet (768 px): two columns, marketing still visible.
- Mobile (375 px): marketing hidden, form fills the screen.

Flows:
- Valid credentials → redirect to `/dashboard` stub; dev-tools shows `localStorage.wecms_token` set.
- Invalid credentials → red alert with `WBE1009` message; password cleared; USER_ID preserved.
- Tick "Save ID", log in, reload `/login` → USER_ID is prefilled, checkbox is checked. Uncheck + re-login → next reload's field is empty.

The task is complete when both the backend integration test and the three manual flows above pass.

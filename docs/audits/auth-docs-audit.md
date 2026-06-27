# Auth Documentation Audit & Standardization — 2026-06-23

> Full audit + standardization of all Auth documentation against the latest backend (`wecms-api/src`)
> and frontend (`wecms-web`) code, using the same methodology applied to Settings, Payment, and
> Add-on Service. **Latest code = source of truth.**
> Companion files: [auth-source-index.md](auth-source-index.md) · [auth-gap-analysis.md](auth-gap-analysis.md).
> Gap vocabulary used throughout: **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING**.

## What changed

Auth previously had **one flat doc per side** (`auth/README.md` + `auth/login-auth.md`) covering only
login/logout/me at a summary level — and that summary carried several **stale claims** (see below). The
audit decomposed Auth into **8 modules** matching the actual implementation, and standardized each to the
project structure:

- **API** (`wecms-api/docs/features/auth/<module>/`) — 9-file structure
  (`README · business-flow · database-mapping · functional-specification · interface-specification · migration-guide · refactor-gap-analysis · source-code-index · test-scenarios`).
- **WEB** (`wecms-web/docs/features/auth/<module>/`) — 6-file structure
  (`README · screen-flow · ui-specification · component-index · api-consumption · test-scenarios`).

The two root files on each side were converted into **summary/navigation documents** (banner added,
detail delegated to the module folders).

## Module map (both sides)

| Module | Slug | Backend surface | Frontend surface | Doc status (before → after) |
|---|---|---|---|---|
| Login | `login` | `POST /api/v1/auth/login` (multi-company resolution, flag-gates, JWT issue, login history) | `/login` page, two-step + `CompanyChooser` | Flat summary (stale) → **Standardized (9+6)** |
| Logout | `logout` | `POST /api/v1/auth/logout` (server-side `jti` revocation — REMEDIATED via F-03) | `ProfileMenu` logout + confirm | Mentioned only → **Standardized (9+6)** |
| Change Password | `change-password` | `POST /api/v1/auth/change-password` (3-step validation, last-3 reuse, SHA-256) | `ChangePasswordDialog` | Marked "not implemented" (stale) → **Standardized (9+6)** |
| Profile | `profile` | `GET /api/v1/users` (tenant directory, `comm_0192_001_r001`) | `ProfileMenu` identity + language | Undocumented → **Standardized (9+6)** |
| JWT Authentication | `jwt-authentication` | `JwtTokenProvider/Filter/EntryPoint` + `JwtProperties` | token storage (`storage.ts`) + Bearer transport (`axios.ts`) | Inline in login-auth → **Standardized (9+6)** |
| Authorization | `authorization` | `@menuAuthz` guard, `@PreAuthorize`, roles, product-type, coverage test | `menuAuth.ts` framework + route gating | Undocumented → **Standardized (9+6)** |
| Menu Authorization | `menu-authorization` | `NavigationService`, `MenuAuthorizationService`, catalogue tables, entitlement editor | `useNavigation`, Sidebar/Header, entitlement universe | Undocumented → **Standardized (9+6)** |
| Session Management | `session-management` | `GET /api/v1/auth/me`, stateless model, `TenantContext` lifecycle | `fetchMe`, route guard, `proxy.ts` edge guard, 401 re-auth | Inline in login-auth → **Standardized (9+6)** |

Total new docs: **72 API + 48 WEB = 120 module files**, plus 3 audit deliverables, plus 4 converted root files.

---

## Stale facts corrected (NOT propagated into the new docs)

The previous flat Auth docs (and several code comments / the root `CLAUDE.md`) carried claims that the
current code contradicts. Every new doc reflects the **code**; the corrections are:

| # | Stale claim (old docs / comments) | Reality in current code | Class | Where it still lingers (code-MR cleanup) |
|---|---|---|---|---|
| 1 | "Login transparently upgrades SHA-256 → BCrypt on success" | **Verify-only**, no write-back; `upgradeEncoding()` always false; SHA-256 stays (coexistence with live JEX). `AuthServiceTest.successfulLogin_isVerifyOnly_neverWritesUserPw` proves it. | APPROVED DEVIATION (the "upgrade" wording is DEAD LEGACY) | `SecurityConfig.passwordEncoder()` Javadoc, `LegacyHashPasswordEncoder` Javadoc, `UserPwChgHis` comment |
| 2 | "No BIZ_KEY chooser UI on the frontend" | `components/auth/CompanyChooser.tsx` **exists and works** (multi-link fallback). | MATCH (FE↔BE) | old `auth/README.md` line 17, old `login-auth.md` |
| 3 | "Change Password not implemented / handler empty" | **Fully built** both sides: `POST /api/v1/auth/change-password` + `ChangePasswordDialog.tsx` wired into `ProfileMenu`. | MATCH | `wecms-web/CLAUDE.md` Feature Status + Known Issues |
| 4 | "`?preview=1` client route guard" | **Removed**; `proxy.ts` edge middleware is the single source of truth. (A distinct dev-only `?preview=session-expired` rehearsal affordance exists and is unrelated.) | DEAD LEGACY | — |
| 5 | "JWT 401 returns `status.code = WBE1009`" | `JwtAuthenticationEntryPoint` emits **`WBE1401`** (session-expired, parity legacy SES001). | corrected | root `wecms-api/CLAUDE.md` §"JWT filter"; the `login-credentials` template doc also mis-cites WBE1009 |
| 6 | Brief/old note: "13 allow-listed controllers" | Actual `ALLOWLISTED_CONTROLLERS` set = **12 names** (adds `SyncHistoryController`, `BulkReceptionResultController`, `BulkExecutionResultController`; the earlier "9" enumeration was incomplete). | corrected | — |
| 7 | `/me` returns only the login tenant fields | `/me` also returns **`all_menus` + `allowed_menu_cds` + `tax_id`** (menu grants resolved via `MenuAuthorizationService`). | code-exists-doc-missing | old root docs |

Items 1, 3, 5 are **code-comment / CLAUDE.md** staleness — flagged for a future code MR (out of this
docs-only scope). They are documented correctly in the new feature docs.

---

## Per-module audit notes

### Login — Standardized
Authentication flow verified against `AuthService.login`: flag-gate order (DEL_YN→WBE1128, ACTV_YN→WBE1010,
LOCK_YN→WBE1011, pw→WBE1009), multi-company resolution (0 links→WBE1009, 1→auto-pick, ≥2 no biz_key→chooser
[no JWT/no audit/no LST_LGN_DATE], ≥2 +biz_key→match/WBE1009), SUCCESS/FAIL `USER_LOGIN_HIS` audit
(`@Transactional(REQUIRES_NEW)`), authority via `BUSI_ADMN_INFM`, JWT claims `{sub,userNm,bizKey,langCd,authGrCd,prdtType?}`.
Refresh token = **MISSING** (deferred); 5-failure auto-lock = **REMEDIATED via F-10** (`LoginLockoutService` sets `USER_INFM.LOCK_YN='Y'` after the latest 5 attempts are all failures) and login rate-limiting = **REMEDIATED** (in-memory `RateLimiter`, key `login:<ip>`); post-auth gates (`NTNL_CD`/`CHNL_TYPE`/`BUSI_STTS`) = **MISSING** (deferred).

### Logout — Standardized
`POST /api/v1/auth/logout` is a public endpoint that **revokes the presented JWT `jti` server-side**
(`AuthService.logout → TokenRevocationService`) and returns `ApiResponse.ok("Logout successful", null)`.
Server-side revocation/blacklist = **REMEDIATED via F-03** (formerly MISSING — `JwtAuthenticationFilter`
rejects revoked / superseded tokens; open item is the **in-memory** revocation store, durable /
distributed deferred); legacy server-session teardown = **DEAD LEGACY**; client cleanup (token+user
cleared, locale+savedId preserved) = **MATCH**.

### Change Password — Standardized
`ChangePasswordService` validation order confirmed: confirm≠new→`WBE_CHANGE_PASSWORD_MISMATCH` (legacy WBE1052),
current≠stored→`WBE2091` (legacy WBE1053), reuse last 3→`WBE_CHANGE_PASSWORD_REUSED` (legacy WBE1017);
`REUSE_HISTORY_DEPTH=3` via `findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc`; `REG_SEQ_NO` via `wb_get_next_val`
funnel. SHA-256 coexistence = **APPROVED DEVIATION**. Password-expiry/`PW_CHG_REQ_SEQ_NO` flow = **MISSING**;
backend dedicated test = **MISSING**.

### Profile — Standardized
Backend "profile" = `GET /api/v1/users` tenant directory (`comm_0192_001_r001`, allow-listed JWT-only), NOT a
self-profile endpoint. `user_img_url` is derived (`/api/v1/users/{userId}/image`, emitted only when `USER_IMG`
non-blank); `USER_IMG` lives on `USER_BUSI_CONN_INFM` (per-tenant). Avatar CRUD lives in **Settings > Users**
(APPROVED DEVIATION); a dedicated self-service "edit my profile" endpoint + the FE avatar-edit pencil =
**MISSING** (UI-only placeholder).

### JWT Authentication — Standardized
HS256 (secret 32–63B) / HS512 (≥64B); fail-fast secret; claims incl. `jti` + `prdtType` omitted-when-null; filter
parses, **checks revocation** (`TokenRevocationService` jti denylist + per-user not-before cutoff), then sets
`ROLE_ADMIN/ROLE_USER` + populates/clears `TenantContext`; entry point → **WBE1401**. Server-side revocation =
**REMEDIATED via F-03** (formerly MISSING; open item = **in-memory** store, per-node); refresh token =
**MISSING** (deferred). Frontend half = token storage (non-HttpOnly cookie `Max-Age=3600` + localStorage) +
Bearer transport; no FE claim-decode for security.

### Authorization — Standardized
`@menuAuthz.canAccess(...)` 4-step rule (validity → product-tree gate → admin bypass → per-user grant) on 30/43
controllers; `ALLOWLISTED_CONTROLLERS` (12) = **APPROVED DEVIATION**; `EndpointAuthorizationCoverageTest`
deny-or-declared lock-in = **MATCH (F-3 complete)**. Product-type behavioral gates (HAENGSUNG table-2/masking,
Lumens readonly, e-Invoice gate) = **PARTIAL** (deferred per feature). FE `menuAuth.ts` gating is **UX-only**;
backend is authoritative.

### Menu Authorization — Standardized
`GET /api/v1/navigation` resolution = Product Tree ∩ grants (admin = full product tree); parent collapse;
language fallback EN/KO/VI; product map F→WE_CMSVN / M→WE_CMSVN_LUMENS / H→WE_CMSVN_HAENGSUNG; Daily-Financial
split (F/M vs H). Catalogue tables `MENU_ITEM`/`PRODUCT_MENU`/`MENU_ITEM_TRANSLATION` are **refactor-owned infra**
(non-legacy) = APPROVED DEVIATION. `USER_MENU_AUTH_INFM` (legacy) backs grants; entitlement editor in Settings >
Users (delete-then-insert, admin-only WBE1003). `NavigationServiceTest` 8/8 = MATCH. Per-node country gate =
**MISSING/PARTIAL**.

### Session Management — Standardized
`GET /api/v1/auth/me`: re-validate user (not deleted/inactive/locked) + token-bound link, fetch CMPN_NM/TAX_ID,
return `MeResponse(+ all_menus, allowed_menu_cds)`. Stateless model = APPROVED DEVIATION (HttpSession = DEAD
LEGACY). 401 → WBE1401 → FE `SessionExpiredDialog` re-auth + retry (`_isRetry` guard). Edge guard `proxy.ts`
(cookie presence). Server-side revocation = **REMEDIATED via F-03** (in-memory store open) and 5-failure auto-lock
= **REMEDIATED via F-10**; refresh token = **MISSING** (deferred); `/me` rejection-path unit tests = **MISSING**;
async ThreadLocal propagation = **PARTIAL/by-design**.

---

## Process notes
- All new docs mirror the `settings/login-credentials/` reference module structure + tone.
- Source-code references were validated against the real files (paths + key methods); Flyway version numbers
  were **not** guessed — tables are cited by name with a pointer to `src/main/resources/db/migration/`.
- Legacy JEX behavior is kept clearly separated from Spring backend and React frontend behavior.
- **No application code was modified** by this audit (docs-only).

# WeInvoice: Self Sign-up With WeInvoice-Owned User Accounts

**Date:** 2026-11-13
**Type:** FEATURE
**Status:** PARTIAL (works end to end on LOCAL; the lamine move is not applied yet)
**Approval Status:** APPROVED (developer, 2026-11-13: "giúp t check phần đăng ký user. giúp t tạo 1 form đc k?" — chose
"Tự đăng ký ở trang login" and "bảng riêng của Weinvoice, dời user "lamine@gmail.com" này sang luôn giúp t")
**Investigation Status:** COMPLETE · **Implementation Status:** COMPLETE · **Test Status:** PASS (unit + LOCAL end to end)
**Repository:** wecms-api (`we-scraping-api`)
**Frontend record:** `we-scraping-web/docs/features/weinvoice/weinvoice-self-signup-2026-11-13.md` (web repository)

## Requirement

- A "Đăng ký" link on the login page opens a sign-up form; a new person creates an account and can sign in.
- Accounts live in a WeInvoice-owned table (schema `weinvoice` on the engine DB), not in the WeCMS `USER_INFM` tables
  shared with the live JEX system.
- The existing WeCMS user `lamine@gmail.com` moves to the WeInvoice table (same password keeps working).

## Current System Behavior

- No user-creation endpoint exists. `POST /api/v1/auth/login` (`AuthService`) reads `USER_INFM` +
  `USER_BUSI_CONN_INFM` + `BUSI_INFM` on the WeCMS DB; passwords are legacy SHA-256 (`PasswordEncoder` also reads
  BCrypt).
- WeInvoice data is owned per login `USER_ID` (`owner_id` on every `winv_*` table).
- Spec v1.0 has no self sign-up screen (only sub-accounts invited by an owner) — this is a product addition by the
  developer, recorded here.

## Investigation Findings

- `AuthService.login` reads `USER_INFM` → `USER_BUSI_CONN_INFM` (needs ≥ 1 active link) → `BUSI_INFM` / `BUSI_ADMN_INFM`,
  then `JwtTokenProvider.issue(userId, userNm, bizKey, langCd, authGrCd, prdtType)`.
- `JwtAuthenticationFilter` does **no DB lookup**: it trusts the signed claims (subject, `bizKey`, `authGrCd`, …) and
  fills `TenantContext`. Revocation by `jti` only.
- `AuthService.me()` re-reads `USER_INFM` + the `USER_BUSI_CONN_INFM` link → a user without those rows gets `WBE1401`.
- `AuthService.logout()` writes `USER_LOGIN_HIS` for the subject on the WeCMS DB.
- WeInvoice endpoints use only `TenantContext.currentUserId()` (`CompanyAccessGuard.ownerId()`); no `bizKey`, no
  `@PreAuthorize` / `@menuAuthz` (allow-listed). The web stores `allowed_menu_cds` but never gates on it.
- `WecmsPasswordEncoder.matches` verifies both the legacy SHA-256 base64 and BCrypt; `encode` writes SHA-256 only
  (JEX coexistence for `USER_INFM`).
- `USER_INFM` is on the WeCMS DB (`WECMS_DB_URL`); `winv_*` tables are on the engine DB (`WECMS_WEINVOICE_DB_URL`) —
  two databases, so the move of `lamine@gmail.com` cannot be one SQL statement.

## Target Behavior (design)

- **Table** `weinvoice.winv_user_infm` (new, `V4__winv_user.sql`): login id, name, email, phone, company name, password
  hash, language, `actv_yn` / `lock_yn` / `del_yn`, failure counter, last login, source (`SIGNUP` / `WECMS`).
- **Sign-up** `POST /api/v1/auth/signup` (public, rate-limited per IP like login): email (= login id, lower-cased),
  name, password (≥ 8 chars, letters + digits), optional phone + company name. Rejected when the email already exists in
  the WeInvoice table **or in WeCMS `USER_INFM`** (a WeInvoice account would otherwise shadow that WeCMS login). New
  passwords are BCrypt (no JEX coexistence for this table). Success signs the user in (same response as login).
- **Login**: the WeInvoice table is checked first; a match is authenticated there (5 consecutive failures lock the
  account, like WeCMS F-10), otherwise the WeCMS path runs unchanged. JWT: subject = the stored user id (owner of the
  `winv_*` data, so a moved user keeps its data), `bizKey` = sentinel `WEINVOICE`, role `USER`, no product type.
- **`/me`**: a `WEINVOICE` token is answered from the WeInvoice table. **Logout**: no `USER_LOGIN_HIS` row for it.
- **Move `lamine@gmail.com`**: manual two-step script — step 1 on the WeCMS DB builds the `INSERT` (password hash copied,
  so the same password works), step 2 runs it on the engine DB. The WeCMS row is **not** touched (shared with JEX);
  login reaches the WeInvoice row first.

## Out of Scope

Email verification, password reset / change for WeInvoice accounts, unlocking from the UI, sub-accounts.

## Database Impact

**BOTH** — a new table (migration folder, DBA-applied by hand) + a manual data script.

- [`src/main/resources/db/weinvoice/V4__winv_user.sql`](../../../../src/main/resources/db/weinvoice/V4__winv_user.sql) —
  new table `weinvoice.winv_user_infm` on the **WeInvoice (engine) database** + unique index on `lower(user_id)`.
  Apply it before deploying this backend. Without it, login logs a warning and falls back to the WeCMS login (WeCMS users unaffected); sign-up fails until it exists.
- [`docs/sql/2026/move-wecms-user-to-weinvoice-2026-11-13.sql`](../../../sql/2026/move-wecms-user-to-weinvoice-2026-11-13.sql) —
  two steps across the two databases: step 1 (WeCMS DB, read-only) prints the `INSERT`, step 2 runs it on the WeInvoice
  DB. Copies `lamine@gmail.com` with its password hash; the WeCMS row is not modified. Rollback = delete the copied row.

## Database Execution Status

| Environment | Required | Executor | Status | Executed Date | Verification |
|---|---|---|---|---|---|
| LOCAL (shared engine DB 192.168.60.121) — V4 | Yes | DEV TEAM | COMPLETE | 2026-11-13 | `to_regclass` + `winv_user_infm_pk`, `winv_user_infm_lower_uk` present |
| LOCAL — move `lamine@gmail.com` | Yes | DEV TEAM | PENDING | - | 0 rows in `winv_user_infm` after the developer's run |
| SIT (.13) | Yes — V4 before deploying this backend | DBA OR DEV TEAM | NOT STARTED | - | - |

## Implementation

- `domain/weinvoice/account/`: `WeInvoiceAccount` (row), `WeInvoiceAccountRepository` (`find` case-insensitive,
  `insertSignup` `ON CONFLICT DO NOTHING`, `recordSuccess`, `recordFailure` locking at 5), `SignupRequest`,
  `WeInvoiceAccountService` (`login`, `signup`, `me`, sentinel `BIZ_KEY = "WEINVOICE"`; a failed lookup — table missing, WeInvoice DB down — falls back to the WeCMS login). Sign-up passwords: BCrypt(10);
  verification through the shared `PasswordEncoder` (reads BCrypt and the legacy SHA-256 of a moved account).
- `AuthService`: `login` asks `WeInvoiceAccountService.login` first (empty → WeCMS path unchanged); `me` routes a
  `WEINVOICE` token there; `logout` writes no `USER_LOGIN_HIS` row for it.
- `AuthController`: `POST /api/v1/auth/signup` (rate limit 5 / 10 min / IP). `SecurityConfig`: permitAll for it.
- `UserInfmRepository.existsByUserIdIgnoreCase` (read-only, sign-up guard).
- `messages{,_vi,_ko}.properties`: `WBE3038`–`WBE3041`. Interface spec: section "Sign-up and WeInvoice accounts".

## Files Changed

`domain/weinvoice/account/{WeInvoiceAccount,WeInvoiceAccountRepository,SignupRequest,WeInvoiceAccountService}.java`
(new), `domain/auth/service/AuthService.java`, `domain/auth/controller/AuthController.java`,
`domain/auth/repository/UserInfmRepository.java`, `config/SecurityConfig.java`, `messages.properties`,
`messages_vi.properties`, `messages_ko.properties`, `db/weinvoice/V4__winv_user.sql` (new),
`docs/sql/2026/move-wecms-user-to-weinvoice-2026-11-13.sql` (new), tests `WeInvoiceAccountServiceTest` (new),
`AuthServiceTest`, `JwtRevocationTest` (constructor), `interface-specification.md`, this record, the test-result record.

## API Impact

New public `POST /api/v1/auth/signup`. `POST /auth/login` and `GET /auth/me` unchanged for WeCMS accounts; WeInvoice
accounts get `biz_key = "WEINVOICE"`. No WeInvoice endpoint changed (they read only the subject).

## Security Review

- Public endpoint: per-IP throttle. Login does not reveal whether an id exists (same `WBE1009`); sign-up necessarily
  answers "already registered" (`WBE3040`) — accepted, as on any sign-up form, and throttled.
- Sign-up cannot take over a WeCMS login: ids existing in `USER_INFM` (any letter case) are refused.
- No email ownership check (out of scope): someone can register an address they do not own; it opens only an empty
  WeInvoice account (data is per owner). Follow-up below.
- A `WEINVOICE` token reaching a WeCMS endpoint: role `USER`, `bizKey` matches no company, `@menuAuthz` grants nothing.
- Passwords are never logged (`toString` of `SignupRequest` / `WeInvoiceAccount` omit them).

## Verification

### Automated Tests

- auth + security + WeInvoice account tests + `EndpointAuthorizationCoverageTest` + `ComponentBeanNameUniquenessTest`
  — PASS (10 classes, 67 tests; new `WeInvoiceAccountServiceTest` 13/13, 3 new `AuthServiceTest` cases).
- `./gradlew test --offline` (full) — 2997 tests, 1 failure `VatDeclarationLifecycleTest` (pre-existing, fails on
  clean `origin/sit` — project CLAUDE.md), 21 skipped.

### Manual Verification

- Environment: LOCAL — backend on :8080 rebuilt with this change, `V4__winv_user.sql` applied by the developer on the
  shared engine DB (table + both indexes present), web on :3000. Test account `signup.test.57951b@example.com`
  (generated, fictional `example.com`).
- Sign-up through `/register` → signed in, redirected to `/dashboard`; `/auth/me` 200 — PASS.
- `POST /auth/login` with the email upper-cased → `WBI0000`, `biz_key=WEINVOICE`, `auth_gr_cd=USER`, company name kept
  — PASS. `/auth/me` with that token → same account — PASS.
- Wrong password once → `WBE1009`, row `fail_cnt = 1` — PASS. Duplicate email → `WBE3040` — PASS. `lamine@gmail.com`
  (a WeCMS login id) → `WBE3040` — PASS. Weak password → `WBE3039` (VI message) — PASS.
- Logout → `WBI0000`, then `/auth/me` → HTTP 401 — PASS.
- DB row: `reg_src = SIGNUP`, hash `$2a$` (BCrypt), `lst_lgn_dtm` set — PASS.
- Move of `lamine@gmail.com`: NOT DONE — `winv_user_infm` had 0 rows after the developer ran the scripts (step 2 not
  run or not committed). Login of lamine therefore still goes through WeCMS.
- Found: a new account (no tax code yet) lands on a dashboard that shows "GET /email/messages: companyMst must not be
  blank" and keeps spinning — the web calls `email/messages?company_mst=` when the user has no MST. Not caused by this
  change (any user without an MST hits it) but every new sign-up does now. Follow-up.

## Test Evidence

Test result: [WeInvoice Self Sign-up API Test Result](../../../testing/results/weinvoice-self-signup-api-test-result-2026-11-13.md)

## Known Limitations / Follow-up

- No email verification, password reset or change-password for WeInvoice accounts (`/auth/change-password` still
  works on `USER_INFM` only); unlocking a locked account = DBA `UPDATE … SET lock_yn = 'N', fail_cnt = 0`.
- Token revocation and the rate limiter are in-memory (same single-instance limitation as login).
- The moved `lamine@gmail.com` keeps its unsalted SHA-256 hash until a password change exists.

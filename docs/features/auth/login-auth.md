# Auth > Login Authentication — Refactor Feature Spec (Backend)

**Domain:** `auth` (system / cross-cutting — JEX `logn` module)
**Refactor screen ID / slug:** `login-auth`
**Backend package:** `com.kosign.wecms.domain.auth`
**Status:** **Working** — login / logout / me implemented in `wecms-api`.
**JEX service IDs:** `logn_0001_001_r001` (login), `logn_0001_001_r002` (me).
**JEX functional reference (read-only):**
[`../../../../docs/jex/features/login-wecms.md`](../../../../docs/jex/features/login-wecms.md),
[`../../../../docs/jex/features/login-spring-boot-migration.md`](../../../../docs/jex/features/login-spring-boot-migration.md),
[`../../../../docs/jex/api-spec/auth.md`](../../../../docs/jex/api-spec/auth.md);
source-of-truth JSPs cited in [`../../archive/planning/phase-1/task-sprint-0-login-refactor.md`](../../archive/planning/phase-1/task-sprint-0-login-refactor.md).

> Created 2026-06-01 to resolve **OQ-DOC-1**. This doc is the canonical feature spine; the deep
> sprint design lives in the linked plan, security design in
> [`../../references/business-rules/security/README.md`](../../references/business-rules/security/README.md), and the
> JEX→Spring field mapping in [`../../references/migration/service-mapping.md`](../../references/migration/service-mapping.md).
> It documents an **already-implemented** feature — no new scope is introduced.

## Overview

Authentication for the refactor. A user submits ID + password on the Next.js login page; the backend
validates against `USER_INFM`, resolves the single tenant link in `USER_BUSI_CONN_INFM`, writes a
`USER_LOGIN_HIS` audit row, and issues a stateless JWT. All other endpoints (except the public set)
require a valid `Authorization: Bearer <token>`.

**Operational model: one user = one company account.** Login resolves the single active
`USER_BUSI_CONN_INFM` link and lands the user on the dashboard with no chooser. Multi-company linkage
is an exceptional / test-demo fallback (`LoginResponse.chooser(...)`), not a documented business
requirement — see `wecms-api/CLAUDE.md` §"Project Overview".

## Endpoints

| Method | Path | Auth | Returns | JEX counterpart |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | `ApiResponse<LoginResponse>` | `logn_0001_001_r001` |
| POST | `/api/v1/auth/logout` | Public | `ApiResponse<Void>` (stub — client clears token) | (client clears session) |
| GET | `/api/v1/auth/me` | Protected (JWT) | `ApiResponse<MeResponse>` | `logn_0001_001_r002` |

### Request / Response (login)

```jsonc
// POST /api/v1/auth/login   (lowercase snake_case wire — global Jackson SNAKE_CASE strategy)
// request
{ "user_id": "testuser", "user_pw": "Password!1" }   // optional "biz_key" for the multi-link fallback

// success → data
{
  "access_token": "eyJ…", "token_type": "Bearer", "expires_in": 3600,
  "user_id": "testuser", "user_nm": "Test User",
  "biz_key": "VN_VNM0004_000001", "lang_cd": "EN",
  "cmpn_nm": "CÔNG TY TNHH WEBCASH VIỆT NAM", "auth_gr_cd": "ADMIN", "prdt_type": "F"
}
```

Envelope is the standard `{ status, data, timestamp?, path?, trace_id? }` per `wecms-api/CLAUDE.md`
§"API Naming & Serialization Standards". Business errors return HTTP 200 with `status.error = true`.

## Authentication flow (`AuthService.login`)

1. Load `USER_INFM` by `USER_ID`. Missing → `WBE1009` + FAIL row in `USER_LOGIN_HIS`.
2. Flag gates, in order: `DEL_YN=Y` → `WBE1128`, `ACTV_YN=N` → `WBE1010`, `LOCK_YN=Y` → `WBE1011`.
3. `passwordEncoder.matches(rawPw, storedHash)`. Mismatch → `WBE1009`.
4. Transparent upgrade: a non-BCrypt (legacy SHA-256) stored hash is re-encoded to BCrypt on success.
5. Resolve tenant from `USER_BUSI_CONN_INFM` (`DEL_YN='N'`): one link → auto-pick; ≥2 links + no
   `biz_key` → chooser response (no JWT, no audit); ≥2 links + `biz_key` → match or `WBE1009`.
6. Update `LST_LGN_DATE`; write SUCCESS `USER_LOGIN_HIS` row (`LGN_CHNL='PC'`, IP).
7. Resolve authority via `BUSI_ADMN_INFM` → `ADMIN` / `USER`; read `BUSI_INFM` for `CMPN_NM` + `PRDT_TYPE`.
8. Issue JWT (claims `sub=userId`, `userNm`, `bizKey`, `langCd`, `authGrCd`, `prdtType?`) and return.

## JWT / session model

- **Stateless** — no HttpSession, no server-side blacklist, **no refresh token** in current scope.
- Access token only; TTL `wecms.jwt.expires-in-seconds` (default 3600s — mirrors legacy session timeout).
- `JwtAuthenticationFilter` parses the Bearer header, sets Spring authorities
  (`ROLE_ADMIN`/`ROLE_USER` from `authGrCd`), and populates `TenantContext`.
- Domain services read tenant + product type only from `TenantContext` (signed JWT) — never from a
  request body/param. `@PreAuthorize` admin gating is wired but not yet annotated on endpoints.

## Data model (tables in scope — all in the PROD Active Table List)

| Table | Role |
|---|---|
| `USER_INFM` | User master — `USER_ID` PK, `USER_PW`, `ACTV_YN`, `LOCK_YN`, `DEL_YN` |
| `USER_BUSI_CONN_INFM` | User↔company link — composite PK `(USER_ID, BIZ_KEY)`, `LST_LGN_DATE` |
| `USER_LOGIN_HIS` | Login audit — `IP_ADDR`, `LGN_CHNL`, `RSLT_CD` (SUCCESS/FAIL) |
| `BUSI_INFM` | Company master — `CMPN_NM`, `PRDT_TYPE` for the JWT claim |
| `BUSI_ADMN_INFM` | Authority source — presence ⇒ ADMIN of that company |

## Error codes

| Code | Meaning |
|---|---|
| WBI0000 | Success |
| WBE1009 | ID or password does not match (also: user not provisioned at the tenant) |
| WBE1010 | Account not activated |
| WBE1011 | Account locked |
| WBE1128 | Account deleted |

## Deferred (intentionally out of current scope)

5-failure auto-lock counter · password-expiry / `PW_CHG_REQ_SEQ_NO` flow · refresh tokens & server-side
logout revocation · post-login menu / permission-tree resolution · `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS`
post-auth gates · rate-limiting. (See `wecms-api/CLAUDE.md` §"Known Issues" + §"Notes for future implementation".)

## Related

- Frontend spec: [`../../../../wecms-web/docs/features/auth/login-auth.md`](../../../../wecms-web/docs/features/auth/login-auth.md)
- Security design: [`../../references/business-rules/security/README.md`](../../references/business-rules/security/README.md)
- Permission / role catalogue (shared): [`../../../../docs/_shared/permission-roles.md`](../../../../docs/_shared/permission-roles.md)
- Endpoint index: [`../../references/api-spec/api-mapping.md`](../../references/api-spec/api-mapping.md) · JEX→Spring: [`../../references/migration/service-mapping.md`](../../references/migration/service-mapping.md)
- Sprint plan: [`../../archive/planning/phase-1/task-sprint-0-login-refactor.md`](../../archive/planning/phase-1/task-sprint-0-login-refactor.md)

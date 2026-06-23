# Auth > Session Management — Backend Feature (Refactor)

**Status:** ✅ Implemented + unit-tested (`AuthServiceTest` — `me()` prdtType-from-claim + admin/user
menu-resolution cases green). `GET /api/v1/auth/me` re-hydrates the authenticated session from the
server of record: it reads the request-scoped `TenantContext` (populated by the JWT filter), re-verifies
the user is still eligible, re-checks the token-bound tenant link, fetches the company display name + tax
id, and returns the menu-authorization payload. The session model is **stateless** — no `HttpSession`,
no server-side token store, no blacklist. Logout is client-only.
**Module:** Auth (cross-cutting system domain — not a WeCMS top-menu group)
**Refactor slug:** `session-management` (matches the `wecms-web` frontend counterpart)
**Wire base path:** `GET /api/v1/auth/me` (protected — `anyRequest().authenticated()` in `SecurityConfig`)
**Legacy JEX counterpart:** session bootstrap `logn_0001_001_r002` (post-login session/context load).
Tables `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM` — all in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md). No session-store table.

> This folder is the **primary documentation home** for the session lifecycle on the backend — `GET
> /api/v1/auth/me`, the stateless token model, the request-scoped `TenantContext`, TTL/expiry, and the
> 401 session-expired path. The login credential exchange, the logout stub, the JWT token mechanics, and
> the menu-grant resolution are **separate auth modules** — cross-linked below, not duplicated here. The
> JEX-side spec is the read-only source of truth for legacy behaviour; the implemented Spring code is the
> source of truth for the refactor behaviour.

---

## What this feature is

Session management is what keeps an authenticated browser "logged in" after the login exchange minted a
JWT. There is **no server-side session object** — the JWT itself is the session, and the server
re-derives identity from it on every request. The single endpoint this module owns is `GET
/api/v1/auth/me`, which the frontend calls on app load (and after a 401-driven re-auth) to rehydrate
session state from the server of record rather than trusting whatever is sitting in `localStorage`.

On each `/me` call `AuthService.me()`:

- Reads the caller's `userId` / `bizKey` / `authGrCd` / `prdtType` / `langCd` from the request-scoped
  `TenantContext` (the JWT filter put them there from the signed token — no re-parse).
- **Re-verifies the user is still eligible** — not deleted / inactive / locked in `USER_INFM`. A flag that
  flipped since the token was issued produces `WBE1401`.
- **Re-checks the token-bound tenant link** still exists and is active (`USER_BUSI_CONN_INFM`,
  `DEL_YN='N'`). A removed link produces `WBE1401`.
- Fetches `BUSI_INFM.CMPN_NM` (display) + `TAX_ID` (Lock Screen popup) — re-queried each call so an
  ops-side correction surfaces on the next rehydrate without a re-login.
- Echoes `authGrCd` + `prdtType` from the signed JWT claim (not re-queried — the token is signed and
  immutable).
- Resolves the **menu-authorization payload** fresh: admins get `all_menus = true` + empty
  `allowed_menu_cds`; normal users get `all_menus = false` + their `USE_YN='Y'` granted leaf slugs (via
  [MenuAuthorizationService](../menu-authorization/README.md)).

The session **TTL** is `wecms.jwt.expires-in-seconds` (default 3600 s). When the token expires (or is
missing / forged), the protected endpoint never reaches the controller —
[JwtAuthenticationEntryPoint](../jwt-authentication/README.md) returns **401** with code `WBE1401`, and
the frontend surfaces its session-expired re-auth dialog.

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoint | `GET /api/v1/auth/me` — see [interface-specification.md](interface-specification.md) |
| Auth | **Protected** (`anyRequest().authenticated()`); the JWT filter must have bound a valid token |
| Session model | **Stateless** — `SessionCreationPolicy.STATELESS`; no `HttpSession`, no server token store, no blacklist |
| Tables | `USER_INFM` (re-validate flags), `USER_BUSI_CONN_INFM` (link check), `BUSI_INFM` (`CMPN_NM` / `TAX_ID`) — all in the inventory. **No session-store table.** |
| Re-validation gates | user `DEL_YN=Y` / `ACTV_YN≠Y` / `LOCK_YN=Y` / link missing → `WBE1401` |
| Identity source | `TenantContext` (request-scoped `ThreadLocal`), populated by `JwtAuthenticationFilter` from the signed JWT |
| `authGrCd` / `prdtType` | echoed from the signed JWT claim (not re-queried) |
| Menu payload | `all_menus` (admin bypass) + `allowed_menu_cds[]` — resolved fresh per `/me` via `MenuAuthorizationService` (never from a JWT claim) |
| TTL | `wecms.jwt.expires-in-seconds` (default **3600** s) |
| 401 path | `JwtAuthenticationEntryPoint` → HTTP 401, code `WBE1401` (legacy `SES001` parity), `X-Trace-Id` header |
| Logout | client-only token clear (cross-link [logout](../logout/README.md)); no server revocation |
| Deferred | refresh tokens · server-side logout revocation · 5-failure auto-lock = **MISSING** |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout (session slice of `domain/auth`)

```
com.kosign.wecms.domain.auth
├── controller/AuthController.java        GET /me (+ /login, /logout, /change-password — other modules)
├── service/AuthService.java             me() — re-validate, company lookup, menu payload
└── dto/MeResponse.java                  user_id, user_nm, biz_key, lang_cd, cmpn_nm, auth_gr_cd,
                                         prdt_type, tax_id, all_menus, allowed_menu_cds
```

Cross-package collaborators (reused, not re-implemented):
[TenantContext](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) (request-scoped principal) ·
[SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java) (`STATELESS`) ·
[JwtAuthenticationEntryPoint](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationEntryPoint.java) (401 / `WBE1401`) ·
[MenuAuthorizationService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java) (menu grants, used by `me()`) ·
the `USER_INFM` / `USER_BUSI_CONN_INFM` / `BUSI_INFM` entities + repositories owned by [login](../login/README.md).

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | Numbered `/me` flow + re-validation gates; stateless session model; `TenantContext` lifecycle; TTL/expiry; the 401 session-expired path; `all_menus`/`allowed_menu_cds` semantics |
| [business-flow.md](business-flow.md) | End-to-end narrative + legacy session-bootstrap (`logn_0001_001_r002`) parity notes (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) |
| [database-mapping.md](database-mapping.md) | `USER_INFM` re-validate flags · `USER_BUSI_CONN_INFM` link check · `BUSI_INFM` `CMPN_NM`/`TAX_ID`; no session store; future `REFRESH_TOKEN` note |
| [interface-specification.md](interface-specification.md) | `GET /api/v1/auth/me` endpoint table + response JSON (incl. `all_menus` + `allowed_menu_cds` + `tax_id`); 401 envelope (`WBE1401`) + `X-Trace-Id` |
| [source-code-index.md](source-code-index.md) | `AuthController.me`, `AuthService.me`, `MeResponse`, `TenantContext`, `SecurityConfig`, `JwtAuthenticationEntryPoint` (paths + key methods + line citations) |
| [migration-guide.md](migration-guide.md) | Legacy session bootstrap (`logn_0001_001_r002`) → stateless `/me`; layer map; what changed |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix with the exact status vocabulary + deferred/missing items |
| [test-scenarios.md](test-scenarios.md) | `/me` valid / deleted / inactive / locked / link-removed / admin-vs-user scenarios (referencing `AuthServiceTest`); gaps as MISSING |

---

## Sibling auth modules (cross-links, not duplicated here)

- [login](../login/README.md) — `POST /api/v1/auth/login` (credential exchange that mints the JWT)
- [logout](../logout/README.md) — `POST /api/v1/auth/logout` (client-side token clear; stub — session
  ends client-side because the model is stateless)
- [jwt-authentication](../jwt-authentication/README.md) — token issue / parse / filter chain / **401
  `WBE1401` entry point** (the token mechanics this module relies on)
- [menu-authorization](../menu-authorization/README.md) — `MenuAuthorizationService.grantedMenuCds(...)`
  that resolves the `allowed_menu_cds` payload `/me` returns
- [change-password](../change-password/README.md) · [authorization](../authorization/README.md) ·
  [profile](../profile/README.md)

## Counterparts & reference

- **Frontend (Next.js):** [wecms-web/docs/features/auth/session-management/README.md](../../../../../wecms-web/docs/features/auth/session-management/README.md)
- **JEX functional reference (read-only):** [docs/jex/features/login-wecms.md](../../../../../docs/jex/features/login-wecms.md)
- **Legacy table inventory (baseline):** [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)

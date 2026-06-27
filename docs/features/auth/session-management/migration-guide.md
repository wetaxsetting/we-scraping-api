# Session Management — Migration Guide (JEX → Spring)

How the legacy JEX session bootstrap became the stateless `/me` rehydrate. Legacy reference (read-only):
the post-login session bootstrap `logn_0001_001_r002` (loads the user/company context into the
server-side `HttpSession` after login). Parity classification + evidence:
[refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## The model shift in one line

Legacy WeCMS held a **server-side `HttpSession`** that carried the logged-in user's context; the refactor
holds **nothing server-side** — the signed JWT is the session, and `GET /api/v1/auth/me` re-derives the
context (and re-validates the live account state) on each call.

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Session bootstrap action | `logn_0001_001_r002_act.jsp` | `AuthController.me` (`GET /api/v1/auth/me`) |
| Session/context load | `HttpSession` population after login | `AuthService.me()` reads `TenantContext` (rebuilt from the JWT by `JwtAuthenticationFilter`) |
| Identity carrier | server-side `HttpSession` per user | request-scoped `TenantContext` `ThreadLocal`, cleared per request |
| Re-validate user eligibility | session-access checks | `UserInfmRepository.findById` + `isDeleted()/isActive()/isLocked()` → `WBE1401` |
| Re-validate tenant link | session bound to the chosen company | `UserBusiConnInfmRepository.findByUserIdAndBizKeyAndDelYn(...)` → `WBE1401` |
| Company display / tax id | loaded once into the session | `BusiInfmRepository.findById` (`CMPN_NM` + `TAX_ID`) re-queried per `/me` |
| Authority / product type | held in the session | echoed from the signed JWT claim via `TenantContext` (not re-queried) |
| Menu / permission set | loaded into the session at bootstrap | `MenuAuthorizationService.grantedMenuCds(...)` resolved fresh per `/me` (admin bypass) |
| Session expiry | server-side session timeout | JWT TTL `wecms.jwt.expires-in-seconds` (default 3600) |
| Lost-session response | redirect to login | `JwtAuthenticationEntryPoint` → HTTP 401 `WBE1401` (legacy `SES001` parity) |
| Response envelope | JEX `COMMON_HEAD` / `REC` | `ApiResponse.ok(MeResponse)` — `{ status, data }` lowercase snake_case |

---

## Field mapping (legacy → wire)

| Legacy source | Refactor field | `/me` wire field |
|---|---|---|
| session `USER_ID` | `USER_INFM.userId` (re-fetched) | `user_id` |
| session `USER_NM` | `USER_INFM.userNm` (re-fetched) | `user_nm` |
| session company | token-bound `BIZ_KEY` (`TenantContext`) | `biz_key` |
| session locale | JWT `langCd` claim (`TenantContext`) | `lang_cd` |
| `BUSI_INFM.CMPN_NM` | `BusiInfm.cmpnNm` (re-queried) | `cmpn_nm` |
| session authority | JWT `authGrCd` claim (echoed) | `auth_gr_cd` |
| `BUSI_INFM.PRDT_TYPE` | JWT `prdtType` claim (echoed) | `prdt_type` |
| `BUSI_INFM.TAX_ID` | `BusiInfm.taxId` (re-queried) | `tax_id` |
| session menu set | `MenuAuthorizationService` (fresh) | `all_menus` + `allowed_menu_cds` |

---

## Identifier translation (slug, not WSVC id)

The refactor surface carries the kebab slug **`session-management`** — frontend route folder, docs folder.
Legacy WSVC ids (`logn_0001_001_r002`) appear only in (1) JEX file:line citations and (2)
migration-traceability prose (this doc). No session identifier surface emits a WSVC id.

---

## What changed (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| Session storage | server-side `HttpSession` | none — stateless; the signed JWT is the session |
| Identity per request | read from the session | rebuilt into `TenantContext` from the JWT, cleared per request |
| Authority / product type | held in the session | signed JWT claims (immutable until next login) |
| Company name / tax id | loaded once into the session | re-queried per `/me` (ops correction surfaces without re-login) |
| Menu set | loaded into the session at bootstrap | resolved fresh per `/me` (entitlement edit applies on next reload) |
| Session expiry | server session timeout | JWT TTL; no sliding window, no silent renew |
| Lost-session handling | redirect | 401 `WBE1401` → frontend re-auth dialog |

---

## Dead / out-of-scope / deferred legacy flows

| Legacy flow | Disposition |
|---|---|
| Server-side `HttpSession` object | **DEAD LEGACY** — intentionally not ported; replaced by the stateless JWT. (The *deviation* — stateless model vs. `HttpSession` — is an **APPROVED DEVIATION**; the `HttpSession` artifact itself is dead.) |
| Server-side session invalidation on logout | **DEAD LEGACY** (the `HttpSession` step) — replaced by JWT `jti` revocation: logout revokes the token server-side (see [logout](../logout/README.md)) |
| Silent token refresh / refresh token | **MISSING (deferred)** — token simply expires; a future `REFRESH_TOKEN` table would be PO-gated/off-inventory (see [database-mapping.md](database-mapping.md)) |
| Server-side logout revocation / blacklist | **IMPLEMENTED** (formerly MISSING, deferred — implemented 2026 via F-03) — `jti` denylist + per-user cutoff (`TokenRevocationService`); `JwtAuthenticationFilter` rejects revoked tokens. **Open:** in-memory store (durable / distributed deferred) |
| 5-failure auto-lock | **IMPLEMENTED** (formerly MISSING, deferred — implemented 2026 via F-10) — `LoginLockoutService` sets `USER_INFM.LOCK_YN='Y'` after the latest 5 attempts are all failures; owned by [login](../login/README.md) |
| Async `TenantContext` propagation | **PARTIAL (by design)** — plain `ThreadLocal`; no `@Async`/child-thread propagation in the synchronous request model |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification.

---

## Schema / key-gen migrations

| Item | Where |
|---|---|
| `USER_INFM` / `USER_BUSI_CONN_INFM` / `BUSI_INFM` schema | legacy tables, column-for-column DEV parity; auth schema landed across the V1 series + later corrective migrations — see `src/main/resources/db/migration/` (cite the table, not a fixed version number) |
| Session store | none — stateless; no table, no migration |
| Key generation on the session path | none — `/me` writes nothing |

Flyway is **disabled** (`ddl-auto: validate`); the schema is treated as external / pre-migrated.

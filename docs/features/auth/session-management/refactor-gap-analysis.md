# Session Management — Refactor Gap Analysis (Parity Review)

Legacy JEX session bootstrap (`logn_0001_001_r002` → server-side `HttpSession`, tables `USER_INFM` /
`USER_BUSI_CONN_INFM` / `BUSI_INFM`) vs. the refactor (`session-management`, `GET /api/v1/auth/me`,
stateless JWT). Status vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** ·
**MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **`/me` session rehydrate** | session/context load (`logn_0001_001_r002`) | `GET /api/v1/auth/me` re-derives identity from the JWT + re-validates live state | **MATCH** |
| **Re-validate user eligibility** | session-access checks | `me()` re-checks `USER_INFM` deleted/inactive/locked → `WBE1401` | **MATCH** |
| **Re-validate token-bound link** | session bound to the chosen company | `me()` re-checks `USER_BUSI_CONN_INFM` link (`DEL_YN='N'`) → `WBE1401` | **MATCH** |
| **Company name / tax id** | loaded once into the session | `BUSI_INFM.CMPN_NM` + `TAX_ID` re-queried per `/me` | **MATCH** |
| **Authority / product type** | held in the session | echoed from the signed JWT claim (not re-queried) | **MATCH** (intent preserved) |
| **Menu / permission payload** | loaded into the session at bootstrap | `all_menus` + `allowed_menu_cds` resolved fresh per `/me` (admin bypass) via `MenuAuthorizationService` | **MATCH** |
| **401 on lost session** | redirect to login | `JwtAuthenticationEntryPoint` → HTTP 401 `WBE1401` (legacy `SES001` parity) | **MATCH** (code is the slug-era `WBE1401`) |
| **Session model** | server-side `HttpSession` | stateless JWT (`SessionCreationPolicy.STATELESS`, no `HttpSession`, no server store/blacklist) | **APPROVED DEVIATION** |
| **`HttpSession` object** | server-side session per user | not ported — no session object exists | **DEAD LEGACY** |
| **Server-side session invalidation (logout)** | server invalidates the `HttpSession` | client clears the token; no server revocation | **DEAD LEGACY** (owned by [logout](../logout/README.md)) |
| **Session expiry** | server session timeout | JWT TTL `wecms.jwt.expires-in-seconds` (default 3600); no sliding window | **APPROVED DEVIATION** |
| **Response envelope** | `COMMON_HEAD` / `REC` | `{ status, data, … }` lowercase snake_case | **APPROVED DEVIATION** (CTO directive) |
| **Identifier surface** | WSVC id `logn_0001_001_r002` | kebab slug `session-management` everywhere | **APPROVED DEVIATION** (slug rule) |
| **Refresh token (silent renew)** | n/a | not implemented — token simply expires | **MISSING** (GAP-1, deferred) |
| **Server-side logout revocation / blacklist** | session invalidation | not implemented (stateless — nothing to revoke) | **MISSING** (GAP-2, deferred) |
| **5-failure auto-lock counter** | locks after 5 consecutive failures | not implemented (`USER_LOGIN_HIS` FAIL rows are the scaffold) | **MISSING** (GAP-3, deferred — owned by [login](../login/README.md)) |
| **Async `TenantContext` propagation** | n/a (servlet thread) | plain `ThreadLocal`; no `@Async`/child-thread propagation | **PARTIAL** (GAP-4, by design) |

---

## Specially-verified gaps

### GAP-1 — Refresh tokens (MISSING; deferred)

There is no refresh token and no silent-renew. When the JWT TTL (`wecms.jwt.expires-in-seconds`, default
3600 s) elapses, the session ends — the next protected call surfaces `WBE1401` and the frontend forces a
re-auth. **Status: MISSING** — deferred. A future `REFRESH_TOKEN` table is **not** in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md) and would be a
refactor-only construct, so it requires explicit **PO (Mr. Jung) approval** before any entity / repository
/ migration is created (the same gate that retired `NOTIFICATION` / `OAT_IDEMPOTENCY_KEY`). Do not add it
speculatively. See [database-mapping.md](database-mapping.md) §4.

### GAP-2 — Server-side logout revocation (MISSING; deferred)

The model is stateless, so a still-valid token cannot be revoked: there is no server token store and no
blacklist. `POST /api/v1/auth/logout` is a client-coordination stub; the token remains cryptographically
valid until expiry. **Status: MISSING** — deferred (would land with the refresh-token work and a
revocation store). Owned conceptually by [logout](../logout/README.md).

### GAP-3 — 5-failure auto-lock counter (MISSING; deferred)

Legacy locks a user after five consecutive failed logins. The refactor writes a FAIL `USER_LOGIN_HIS` row
on every failed login attempt (the data scaffold, in the [login](../login/README.md) module), but no
counter reads those rows to flip `LOCK_YN`. It is **out of the session-management scope** (the lock would
be enforced at login, not at `/me`), listed here only because it is part of the same auth-session
hardening backlog. **Status: MISSING** — deferred.

### GAP-4 — Async `TenantContext` propagation (PARTIAL; by design)

`TenantContext` is a plain `ThreadLocal`, set by `JwtAuthenticationFilter` and cleared in a `finally`
block. It is **not** an `InheritableThreadLocal`, and no `TaskDecorator` propagates it across `@Async`
executors or manually spawned threads — the class Javadoc warns against leaking it beyond the request
lifecycle. For the current synchronous request model this is sufficient and correct; an async/reactive
boundary would need explicit propagation wiring. **Status: PARTIAL / by-design** — not a defect under the
present architecture; revisit if an async path needs the tenant principal.

---

## Migration status

```
Refactor Status: complete + unit-tested for the in-scope session surface (GET /me rehydrate, re-validate
                 gates, JWT-claim echo of authGrCd/prdtType, fresh menu payload, stateless model, 401
                 WBE1401 path).
                 Deferred (not parity-blocking for R&D): refresh tokens, server-side logout revocation,
                 5-failure auto-lock.
```

Evidence for closed items:

- **`/me` echoes the signed claim, not the DB** — proven by `AuthServiceTest.me_readsPrdtTypeFromJwtClaim_notFromBusiInfm`
  (`BUSI_INFM` row says `F`, JWT says `H`, `/me` returns `H`).
- **Admin menu bypass** — proven by `AuthServiceTest.me_admin_bypassesMenuFiltering_allMenusTrue`
  (`all_menus=true`, empty list, `MenuAuthorizationService` never hit).
- **Normal-user grants** — proven by `AuthServiceTest.me_normalUser_returnsOnlyGrantedMenus`
  (`all_menus=false`, exact granted slugs).
- **Stateless chain** — `SecurityConfig.filterChain` sets `SessionCreationPolicy.STATELESS` (line 46);
  `JwtAuthenticationEntryPoint` returns 401 `WBE1401` (line 52).
- **Slug `session-management`** used end-to-end (route, docs).

Residual (non-blocking, tracked):
1. GAP-1 — refresh tokens (deferred; PO-gated, off-inventory).
2. GAP-2 — server-side logout revocation (deferred).
3. GAP-3 — 5-failure auto-lock (deferred; owned by login).
4. GAP-4 — async `TenantContext` propagation (PARTIAL / by design).
5. **No `me()` rejection test** (deleted/inactive/locked/link-removed → `WBE1401`) and **no `@WebMvcTest`**
   wire-shape test — see [test-scenarios.md](test-scenarios.md).

---

## Code-vs-doc discrepancies found (flag for cleanup — do NOT propagate)

These are stale comments/prose found in the code during this pass; they are **not** behavioural defects,
but they misdescribe the verified behaviour and should be corrected in a future cleanup MR.

1. **`SecurityConfig.passwordEncoder()` Javadoc is STALE.** Lines 35–38 say the encoder "always writes
   BCrypt on the write path, and flags legacy hashes for transparent upgrade via `upgradeEncoding()`."
   The current `WecmsPasswordEncoder` is **verify-only legacy SHA-256, no write-back, `upgradeEncoding()`
   always `false`** (coexistence rule, 2026-06-10). The comment predates the coexistence rewrite — same
   class of stale fact corrected for [login](../login/README.md) / [change-password](../change-password/README.md).
   Not a session-management behaviour, but flagged here because it sits in `SecurityConfig` (a file this
   module cites). **DEAD LEGACY** wording.

2. **Root `wecms-api/CLAUDE.md` "JWT filter" prose mentions `WBE1009` for the 401 path.** One bullet under
   §"JWT filter (`JwtAuthenticationFilter`)" says invalid token → 401 with `status.code = WBE1009`. The
   live `JwtAuthenticationEntryPoint` emits **`WBE1401`** (verified, line 52). `WBE1401` is authoritative;
   the `WBE1009` mention is stale. (The "Me flow" and "JWT" sections of the same CLAUDE.md are correct
   that the response is 401 with the standard envelope.)

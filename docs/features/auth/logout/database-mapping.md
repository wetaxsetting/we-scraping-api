# Logout — Database Mapping (Backend)

---

## 1. No tables are read or written

Logout performs **no database access**. The handler
([AuthController.logout()](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java))
delegates to `AuthService.logout(...) → TokenRevocationService.revokeToken(...)`, which records the
revoked `jti` in an **in-memory** store — not in any table. No repository or entity is involved.

| Concern | Store | Read | Written |
|---|---|---|---|
| Token revocation / denylist | **In-memory `TokenRevocationService`** (jti denylist + per-user not-before cutoff) — not a DB table | — | In-memory only |
| Server session | *(none — `SessionCreationPolicy.STATELESS`)* | — | — |
| Audit | `AUDIT_LOGS_INFM` | No | No |
| Login history | `USER_LOGIN_HIS` | No | No |

There is **no entity, repository, DTO, or column mapping** for this module — revocation state lives in
process memory, by design.

---

## 2. Why there is nothing to map (to a table)

Server-side revocation is implemented, but it is held **in process memory**, not in a table — so the
data layer still has nothing to map. Contrast:

- **Login** writes a SUCCESS `USER_LOGIN_HIS` row and updates `USER_BUSI_CONN_INFM.LST_LGN_DATE`.
- **Logout** writes no row — it adds the token's `jti` to the in-memory `TokenRevocationService`
  denylist.

The legacy server-session teardown remains **DEAD LEGACY** (no `HttpSession` to invalidate); the
JWT-native server-side revocation replaces it — see
[refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 3. Revocation store today — in-memory, not a DB table

> **Formerly "where a future revocation table would live" (GAP-1, deferred) — server-side revocation
> was implemented 2026 via F-03.** Logout now revokes the presented `jti`; the store is the in-memory
> [TokenRevocationService](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java)
> (denylist + per-user not-before cutoff), checked by
> [JwtAuthenticationFilter](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java)
> on every request. **No table backs it.**

**Open limitation (still deferred):** the in-memory store is lost on restart and is not shared across
instances. A **durable / distributed** store (Redis, or a PO-approved DB table) would be needed before
a multi-node deploy. Likewise a **`REFRESH_TOKEN`** table (rotate-on-use) for refresh tokens is still
unbuilt.

> **Inventory note.** Neither a revocation table nor a `REFRESH_TOKEN` table exists in the
> [legacy table inventory](../../../references/database/legacy-table-inventory.md) today, and a
> refactor-only table is not permitted without a PO baseline decision (root CLAUDE.md §"Refactor DB &
> Legacy-Compatibility Rules"). Promoting the in-memory revocation store to a durable table would need
> that decision first. This is recorded as deferred design intent, **not** an approved table.

See [session-management](../session-management/README.md) for the broader stateless / refresh-token
discussion.

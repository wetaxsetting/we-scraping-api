# JWT Authentication — Database Mapping (Backend)

## Summary: **NONE — JWT authentication uses no DB table (revocation state is in-memory).**

The refactor's JWT model holds **no DB-backed session state**. A token is a signed, self-contained JWS
string; on each request the server checks the signature + `exp` claim **and** the in-memory revocation
state. There is:

- **No token table** — the JWT is never persisted.
- **No session table** — `SecurityConfig` sets `SessionCreationPolicy.STATELESS`; there is no `HttpSession`, no `JSESSIONID`, no session row.
- **No refresh-token table** — refresh tokens are MISSING (deferred — see below).
- **No blacklist / revocation table** — server-side revocation **is implemented**, but its store is the **in-memory** `TokenRevocationService` (jti denylist + per-user not-before cutoff), **not** a DB table. *(Formerly "revocation is MISSING / logout is client-only" — implemented 2026 via F-03.)*

The only persistent state involved is **upstream of token issuance** and belongs to the **login**
module, not here:

| Concern | Owner module | Tables |
|---|---|---|
| Credential check + tenant resolution that feed `issue(...)` | [../login/](../login/) | `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM`, `BUSI_ADMN_INFM`, `USER_LOGIN_HIS` |
| `/me` re-verification (reads, no token state) | [../session-management/](../session-management/) | `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM` |

Every value the token carries (`bizKey`, `authGrCd`, `prdtType`, `langCd`, `userNm`) is sourced from
those tables **at login time** and then frozen into the signed claim. The JWT-authentication layer
itself reads and writes **no table**.

---

## 1. Configuration (not DB)

All JWT tuning is config, not schema:

| Property | Default | Source |
|---|---|---|
| `wecms.jwt.secret` | env `WECMS_JWT_SECRET` (no default; ≥ 32 bytes) | `JwtProperties.secret()` |
| `wecms.jwt.expires-in-seconds` | `3600` | `JwtProperties.expiresInSeconds()` / `application.yml:102` |
| `wecms.jwt.issuer` | `wecms-api` | `JwtProperties.issuer()` / `application.yml:103` |

No Flyway migration is associated with JWT authentication — there is no table to create. (`ddl-auto`
is `validate` and Flyway is disabled per project policy, but that is moot here: nothing in this slice
touches the schema.)

---

## 2. In-memory state (not DB)

Two pieces of state live in process memory, never in a row:

- **Per-request identity** — `TenantContext.Principal(userId, userNm, bizKey, langCd, authGrCd, prdtType)`, set by `JwtAuthenticationFilter` from the JWT claims, cleared in a `finally` block at request end. Purely in-memory; never persisted; no async propagation.
- **Revocation state** — `TokenRevocationService` holds a `jti` denylist (logout) + a per-user not-before cutoff (admin lock / delete / demote / password-reset). Consulted by the filter on every request. In-memory, so it is **lost on restart and not shared across instances** — a durable / distributed store is deferred (see §3).

---

## 3. Where a durable revocation / future `REFRESH_TOKEN` table would live (deferred)

Server-side revocation is **implemented** but **in-memory**; refresh tokens are **MISSING** (deferred
R&D). **No table backs either today**, and per the refactor's legacy-first rule a refactor-only table
cannot be created without PO approval and a legacy DDL change (root `CLAUDE.md` §"Refactor DB &
Legacy-Compatibility Rules"). When a durable / multi-instance store (or refresh tokens) is genuinely
needed:

- It would be a **new refactor-owned table** absent from the [legacy table inventory](../../../references/database/legacy-table-inventory.md) — so it requires an **explicit PO (Mr. Jung) baseline change** before any entity/repository/migration is written (same gate that governed `MENU_ITEM` / `SHEDLOCK`). The established alternative — back it with an existing legacy table or a read-only `@Subselect` — does not fit a write-heavy rotating-token / revocation store, so this is a genuine PO decision, not an automatic addition. (A shared cache such as Redis is the other candidate for the revocation denylist specifically.)
- Candidate shape (illustrative only, **not implemented**): `REFRESH_TOKEN(BIZ_KEY, USER_ID, TOKEN_ID, ISSUED_DTM, EXPIRES_DTM, REVOKED_YN, ...)`, keyed on a rotating opaque token id; rotation-on-use; revocation wired into `JwtAuthenticationFilter` (reject an access token whose refresh lineage is revoked). See `wecms-api/CLAUDE.md` §"Notes for future implementation → Refresh tokens / logout revocation".
- The **revocation denylist** is implemented today via the in-memory `TokenRevocationService`; promoting it to a durable / distributed store (so it survives a restart and is shared across nodes) is the open part — it would need its own approved store under the same PO gate.

Until that PO decision lands, revocation stays in-memory and the slice remains table-free.

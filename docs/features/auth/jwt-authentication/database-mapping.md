# JWT Authentication — Database Mapping (Backend)

## Summary: **NONE — JWT authentication is stateless and has no token store.**

The refactor's JWT model holds **no server-side session state**. A token is a signed, self-contained
JWS string; the server consults only the token's signature + `exp` claim on each request. There is:

- **No token table** — the JWT is never persisted.
- **No session table** — `SecurityConfig` sets `SessionCreationPolicy.STATELESS`; there is no `HttpSession`, no `JSESSIONID`, no session row.
- **No refresh-token table** — refresh tokens are MISSING (see below).
- **No blacklist / revocation table** — server-side revocation is MISSING (logout is client-only).

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

## 2. Request-scoped state (in-memory, not DB)

The per-request identity lives in a `ThreadLocal`, not a row:

- `TenantContext.Principal(userId, userNm, bizKey, langCd, authGrCd, prdtType)` — set by `JwtAuthenticationFilter` from the JWT claims, cleared in a `finally` block at request end. Purely in-memory; never persisted; no async propagation.

---

## 3. Where a future `REFRESH_TOKEN` table would live (deferred — MISSING)

Refresh tokens and server-side revocation are **MISSING** (deferred R&D). **No table exists today**,
and per the refactor's legacy-first rule a refactor-only table cannot be created without PO approval and
a legacy DDL change (root `CLAUDE.md` §"Refactor DB & Legacy-Compatibility Rules"). When the feature is
genuinely needed:

- It would be a **new refactor-owned table** absent from the [legacy table inventory](../../../references/database/legacy-table-inventory.md) — so it requires an **explicit PO (Mr. Jung) baseline change** before any entity/repository/migration is written (same gate that governed `MENU_ITEM` / `SHEDLOCK`). The established alternative — back it with an existing legacy table or a read-only `@Subselect` — does not fit a write-heavy rotating-token store, so this is a genuine PO decision, not an automatic addition.
- Candidate shape (illustrative only, **not implemented**): `REFRESH_TOKEN(BIZ_KEY, USER_ID, TOKEN_ID, ISSUED_DTM, EXPIRES_DTM, REVOKED_YN, ...)`, keyed on a rotating opaque token id; rotation-on-use; revocation wired into `JwtAuthenticationFilter` (reject an access token whose refresh lineage is revoked). See `wecms-api/CLAUDE.md` §"Notes for future implementation → Refresh tokens / logout revocation".
- A blacklist/revocation variant (to make logout invalidate an in-flight access token) would similarly need its own approved store — there is none today, which is why **logout is client-only** and a token stays valid until `exp`.

Until that PO decision lands, the slice stays stateless and table-free.

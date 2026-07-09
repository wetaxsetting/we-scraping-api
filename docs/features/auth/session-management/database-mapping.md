# Session Management — Database Mapping (Backend)

Legacy-column → entity-field mapping for the three tables the `/me` session-rehydrate touches, taken from
the real entity code. The stateless model means there is **no session-store table** — the JWT is the
session. Legacy JEX DDL is the schema source of truth; the refactor mirrors it column-for-column (Legacy
Schema Parity Rule). All three tables are in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md):
`USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM`.

The entities are **owned by** the [login](../login/README.md) module (`domain/auth/entity`); session
management re-uses them read-only. The full per-column listing lives in
[login/database-mapping.md](../login/database-mapping.md) — this doc covers only the columns `/me` reads.

| Table | Role in `/me` | Key columns | Access |
|---|---|---|---|
| `USER_INFM` | Re-validate the caller is still eligible | PK `USER_ID` | read |
| `USER_BUSI_CONN_INFM` | Re-check the token-bound tenant link still exists | PK `(USER_ID, BIZ_KEY)` | read |
| `BUSI_INFM` | Company display name + tax id (re-queried each call) | PK `BIZ_KEY` | read |

No row is written by `/me` — it is a read-only rehydrate (no audit, no `LST_LGN_DATE`, no key-generation
funnel).

---

## 1. `USER_INFM` — re-validate eligibility flags

Entity: [UserInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java)
(`@Table("USER_INFM")`, PK `USER_ID`). `me()` re-fetches the row by `userId` and bounces the session with
`WBE1401` if any eligibility flag has flipped since the token was issued.

| DB column | Type (DEV parity) | JPA field | `/me` use |
|---|---|---|---|
| `USER_ID` | VARCHAR(100) | `userId` (`@Id`) | lookup key (from `TenantContext`) |
| `USER_NM` | VARCHAR(100) | `userNm` | response `user_nm` |
| `ACTV_YN` | VARCHAR(1) | `actvYn` | re-validate via `isActive()` — not `Y` → `WBE1401` |
| `LOCK_YN` | VARCHAR(1) | `lockYn` | re-validate via `isLocked()` — `Y` → `WBE1401` |
| `DEL_YN` | VARCHAR(1) | `delYn` | re-validate via `isDeleted()` — `Y` → `WBE1401` |

Helpers `isActive()` / `isLocked()` / `isDeleted()` test `"Y".equals(...)`. The combined gate in `me()` is
`user == null || user.isDeleted() || !user.isActive() || user.isLocked()` → `WBE1401`. `USER_PW` is **not
read** on the session path (no re-authentication; the token already proved the credential at login).

---

## 2. `USER_BUSI_CONN_INFM` — token-bound link check

Entity: [UserBusiConnInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfm.java)
(`@Table("USER_BUSI_CONN_INFM")`, `@IdClass(UserBusiConnInfmId.class)`). `me()` confirms the user is still
actively linked to the **exact** `bizKey` the token is bound to.

| DB column | Type | JPA field | PK | `/me` use |
|---|---|---|---|---|
| `USER_ID` | VARCHAR(100) | `userId` | ✅ | composite key |
| `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | token-bound tenant (from `TenantContext`) |
| `DEL_YN` | VARCHAR(1) | `delYn` | | active filter (`= 'N'`) |

Query: `findByUserIdAndBizKeyAndDelYn(userId, bizKey, "N")`. Empty → `WBE1401` (the user is no longer
linked to that tenant). `LST_LGN_DATE` is **not written** by `/me` (login owns that write).

---

## 3. `BUSI_INFM` — company display name + tax id

Entity: [BusiInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiInfm.java)
(`@Table("BUSI_INFM")`, PK `BIZ_KEY`). `me()` reads two columns from a single `findById` round-trip;
`PRDT_TYPE` is **not** read here (it is echoed from the signed JWT claim instead).

| DB column | Type | JPA field | `/me` use |
|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | `bizKey` (`@Id`) | lookup key |
| `CMPN_NM` | VARCHAR(200) | `cmpnNm` | response `cmpn_nm` (empty string when null) |
| `TAX_ID` | VARCHAR(40) | `taxId` | response `tax_id` (Lock Screen popup; null preserved, never defaulted) |

`CMPN_NM` + `TAX_ID` are **re-queried on every `/me` call** so an ops-side correction surfaces on the next
session rehydrate without forcing a re-login. `PRDT_TYPE` and `AUTH_GR_CD` are intentionally **not**
re-read — they ride in the signed JWT and are echoed from `TenantContext` (a needless DB hop otherwise).

---

## 4. No session-store table (stateless)

The session is the signed JWT — there is **no DB table backing the session**:

- No server-side `HttpSession`, no session table, no **persisted** token-blacklist / revocation table, no
  refresh-token table. (Server-side revocation **is** implemented, but its store is the **in-memory**
  `TokenRevocationService` — not a DB table; see [jwt-authentication](../jwt-authentication/README.md).)
- `auth_gr_cd` and `prdt_type` are **not** persisted per session — they live as signed JWT claims and are
  surfaced through `TenantContext` (a request-scoped `ThreadLocal`, not a DB row).
- The menu payload (`all_menus` / `allowed_menu_cds`) is computed on the fly from
  `USER_MENU_AUTH_INFM` via `MenuAuthorizationService` (owned by
  [menu-authorization](../menu-authorization/README.md)); session management does not store or cache it.

> **Future `REFRESH_TOKEN` / durable revocation store (not in scope).** Refresh tokens are **MISSING**
> (deferred). Server-side logout revocation **is implemented** (formerly MISSING, deferred — implemented
> 2026 via F-03) but its store is **in-memory** — making it durable / distributed (or adding a
> `REFRESH_TOKEN` table) is the deferred part. Such a table is **not** in the
> [legacy table inventory](../../../references/database/legacy-table-inventory.md) and would be a
> refactor-only construct — per the root CLAUDE.md "Refactor DB & Legacy-Compatibility Rules", that
> requires explicit **PO (Mr. Jung) approval** before any entity / repository / migration is created (the
> same gate that retired the refactor-only `NOTIFICATION` and `OAT_IDEMPOTENCY_KEY` objects). Do **not**
> add it speculatively. See [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 5. Relationships & scope

- **Tenant isolation** — `BIZ_KEY` is the (or a) leading PK column on every tenant-scoped table; the
  `bizKey` `/me` checks always comes from the signed JWT (`TenantContext`), never from a request param.
- **Re-validate chain:** `USER_INFM (USER_ID)` → `USER_BUSI_CONN_INFM (USER_ID, BIZ_KEY)` → `BUSI_INFM
  (BIZ_KEY)`. Each step can short-circuit the session with `WBE1401`.
- **No new tables, no key generation.** Every table is a legacy inventory table mapped column-for-column;
  `/me` writes nothing.

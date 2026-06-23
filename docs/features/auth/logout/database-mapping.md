# Logout — Database Mapping (Backend)

---

## 1. No tables are read or written

Logout is **stateless** and performs **no database access**. The handler
([AuthController.logout()](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java))
returns a constant `ApiResponse<Void>` and touches no service, repository, or entity.

| Concern | Table | Read | Written |
|---|---|---|---|
| Token revocation / blacklist | *(none — stateless JWT)* | — | — |
| Server session | *(none — `SessionCreationPolicy.STATELESS`)* | — | — |
| Audit | `AUDIT_LOGS_INFM` | No | No |
| Login history | `USER_LOGIN_HIS` | No | No |

There is **no entity, repository, DTO, or column mapping** for this module — by design.

---

## 2. Why there is nothing to map

The access token is a stateless, signed JWT (`jwt-authentication`). The server keeps no session and no
token store, so logout has nothing to persist or delete. Contrast:

- **Login** writes a SUCCESS `USER_LOGIN_HIS` row and updates `USER_BUSI_CONN_INFM.LST_LGN_DATE`.
- **Logout** writes nothing.

This is the **APPROVED DEVIATION** (stateless model) plus the **DEAD LEGACY** (server-session teardown)
flowing through to the data layer — see [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 3. Where a future revocation table would live

Server-side token revocation is **MISSING** (deferred). If/when it is built, it would introduce a
refactor-side token store — most likely a **`REFRESH_TOKEN`** table (rotate-on-use + a revocation
flag), wired into
[JwtAuthenticationFilter](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java)
so a revoked/rotated token is rejected on the next request. The logout handler is where the revocation
call would attach.

> **Inventory note.** No such table exists in the
> [legacy table inventory](../../../references/database/legacy-table-inventory.md) today, and a
> refactor-only table is not permitted without a PO baseline decision (root CLAUDE.md §"Refactor DB &
> Legacy-Compatibility Rules"). A `REFRESH_TOKEN` / blacklist table would need that decision before any
> entity / migration is created. This is recorded as deferred design intent, **not** an approved table.

See [session-management](../session-management/README.md) for the broader stateless / refresh-token
discussion.

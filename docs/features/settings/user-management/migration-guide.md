# User Management — Migration Guide (JEX → Spring Boot)

Back to [README](README.md) · [source-code-index](source-code-index.md) · [refactor-gap-analysis](refactor-gap-analysis.md)

How the legacy `comm_0190_*` JSP behaviour was reimplemented as a clean Spring Boot slice. The
refactor is informed by JEX behaviour, **not** a port — no `JexData`, no IDO XML, no `_act.jsp`
control flow inside Java.

---

## 1. Service / WSVC mapping

| Legacy WSVC | Legacy behaviour | Refactor endpoint | Refactor owner |
|---|---|---|---|
| `comm_0190_001_r001` | List users for a business | `GET /api/v1/settings/users` | `UserManagementService.list` |
| `comm_0190_002_c001` | Insert `USER_INFM` + `USER_BUSI_CONN_INFM` (+ default menu rows) | `POST /api/v1/settings/users` | `UserManagementService.create` |
| `comm_0190_003_r001` | User detail (`USER_INFM ⋈ USER_BUSI_CONN_INFM`) | `GET /api/v1/settings/users/{id}` | `UserManagementService.detail` |
| `comm_0190_003_u001` | Update profile, password (+history), admin upsert/delete, employee sync | `PUT /api/v1/settings/users/{id}` | `UserManagementService.update` |
| `comm_0190_003_d001` | Soft-delete `USER_INFM`/link, delete admin row | `DELETE /api/v1/settings/users/{id}` | `UserManagementService.delete` |
| `USER_MENU_AUTH_INFM_*` IDOs | Per-user menu grants | `GET/PUT .../{id}/menu-entitlement` | `MenuEntitlementService` |
| `USER_BUSI_CONN_INFM` `USER_IMG` IDO | Avatar file on the link row | `POST/DELETE/GET .../{id}/image` | `UserImageService` |

## 2. Field & semantics translation

| Legacy mechanism | Refactor approach |
|---|---|
| `hash(:USER_PW)` DB function | `WecmsPasswordEncoder` — writes legacy SHA-256 base64 (matches JEX `public.hash()` for coexistence); reads accept legacy SHA-256 **or** BCrypt (dev-only tolerance). **No transparent upgrade / write-back** (`upgradeEncoding()` returns false) |
| `encrypt/decrypt(CLPH_NO)` DB functions | Refactor stores phone **plain**; legacy DEV-imported rows are sanitized on read (drop non-phone glyphs). No `encrypt()`/`decrypt()` PL/pgSQL added (DB-function policy) |
| Admin = `BusiAdmnInfmDAO.upsert` / `deleteByPk` | `set_as_admin` flag drives `BUSI_ADMN_INFM` insert/delete, guarded by the last-admin rule |
| Menu grants keyed by `WEB_SVC_ID` (JEX id) | Same `WEB_SVC_ID` column repurposed to hold a kebab-case **menu slug** (e.g. `settings-core-users`); Java field `menuAuthCd` |
| ML codes (`P00001`, `WBW1151`, `WBE1178`…) | Translated to `WBE_USERS_*` / `WBE211x`; added to `messages.properties` |
| `COMMON_HEAD`/`REC` envelope | `ApiResponse<T>` `{ status, data, … }` |
| Soft-delete = `DEL_YN='Y'` on master + link | **Per-tenant unlink only**: `USER_BUSI_CONN_INFM.DEL_YN='Y'`; master `USER_INFM` preserved |

## 3. PROD-active-table scoping

Cross-checking the legacy table set against the PROD Active Table List, the refactor **ports**:
`USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_ADMN_INFM`, `USER_MENU_AUTH_INFM`, `ACCT_AUTH_INFO`.

It **deliberately omits** (all justified in the gap analysis):
- `USER_MENU_ACTION_AUTH_INFM` — action-level CRUD grants; the refactor models menu-level only.
- `USER_PW_CHG_HIS` — password reuse-history enforcement deferred.
- `EMPL_INFM` sync, `BUSI_ADMN_ACTV_HIS`, `BUSI_ADMN_LOGIN_HIS` — out of scope for this screen.

## 4. Schema work

Only one migration was needed — `V131__user_menu_auth_infm.sql` (creates `USER_MENU_AUTH_INFM`). The
other four tables already existed from the auth/bootstrap/banking schema, so CRUD + avatar reuse
existing columns with **no `ALTER`** and `ddl-auto: validate` intact.

## 5. Porting checklist (what was done)

- [x] List/detail/create/update/delete over `USER_INFM` + `USER_BUSI_CONN_INFM`.
- [x] Admin toggle through `BUSI_ADMN_INFM` with last-admin + self-delete guards.
- [x] Menu entitlement read/write (`USER_MENU_AUTH_INFM`, V131) — delete-then-insert, Y-only.
- [x] Avatar upload/remove/stream on `USER_BUSI_CONN_INFM.USER_IMG`.
- [x] Login-id availability check (global).
- [x] Legacy SHA-256 password write (JEX coexistence) + BCrypt read tolerance; no upgrade/write-back.
- [x] Split authorization (read = ADMIN or `settings-core-users`; write = ADMIN) + service guards.
- [x] Wire shape lowercase snake_case; standard error envelope + trace id.
- [x] Frontend wired to live endpoints (no mock fallback).
- [ ] `AUDIT_LOGS_INFM` write on mutations — **open** (see gap analysis OQ).
- [ ] Admin `LOCK_YN`/`ACTV_YN` toggle, password reuse history, action-level grants — **deferred**.

## 6. Related migration docs

- Cross-system migration narrative: [`docs/features/user-management/migration-guide.md`](../../../../../docs/features/user-management/migration-guide.md).
- Menu-grant consumption at login: [`docs/features/menu-authorization/`](../../../../../docs/features/menu-authorization/README.md).
- The wider JEX→Spring playbook: [`docs/refactor-wecms/MIGRATION_PLAYBOOK.md`](../../../../../docs/refactor-wecms/MIGRATION_PLAYBOOK.md).

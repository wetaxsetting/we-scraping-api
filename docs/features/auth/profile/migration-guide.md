# Auth > Profile — Migration Guide (JEX → Spring)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

How the legacy `comm_0192_001_r001` ("Inquiry Approver") read path became the refactor's
`GET /api/v1/users` tenant-user directory. Legacy is read-only reference; the implemented Spring code
is the source of truth.

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen / consumer | Inquiry-Approver popup (`comm_0192_001`) — approver picker | approval-line / signer pickers (no standalone route) consume `GET /api/v1/users` |
| Read action | `comm_0192_001_r001_act.jsp` | `GET /api/v1/users` → [UserController.list(q)](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/UserController.java) |
| Query | IDO `USER_BUSI_CONN_INFM_R004` (`:24-62`) — join `USER_INFM`, `del_yn='N'`, `user_nm ilike '%'||?||'%'` | `UserBusiConnInfmRepository.findTenantUserPicker(bizKey, q)` JPQL projection |
| Service logic | DAO read | [UserDirectoryService.list(q)](../../../../src/main/java/com/kosign/wecms/domain/auth/service/UserDirectoryService.java) — tenant scope + safeQ + projection → rows |
| Tenant scope | session-bound `BIZ_KEY` | `TenantContext.currentBizKey()` (signed JWT, never a param) |
| Row shape | `REC1` sub-record (`USER_ID`, `USER_NM`, `POSI_NM`, `DEPT_NM`, `USER_IMG`, `CLPH_NO`, `ADMIN_YN`, `MOB_YN`) | `TenantUserRow` — 5 fields (`user_id`, `user_nm`, `posi_nm`, `dept_nm`, `user_img_url`) |
| Avatar | `USER_BUSI_CONN_INFM.USER_IMG` raw | presence flag → derived `user_img_url` (`/api/v1/users/{userId}/image`); raw value never on wire |
| Envelope | JEX `COMMON_HEAD` / `REC` | `ApiResponse<TenantUserListResponse>` (lowercase snake_case) |

---

## Identifier translation (slug, not WSVC id)

The refactor surfaces this under the kebab slug **`profile`** (folder + WEB counterpart) on a
**resource-style** REST base path `/api/v1/users`. Legacy WSVC id `comm_0192_001_r001` appears only in
(1) JEX file:line citations and (2) migration-traceability prose (this doc). There is **no audit
`WSVC_ID`** to translate — the surface is read-only and writes no `AUDIT_LOGS_INFM` rows. There is **no
menu permission slug** — the endpoint is JWT-only / allow-listed (legacy `NORM_USER_ONLY_YN = Y`).

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| Query | IDO native UNION-free join via `USER_BUSI_CONN_INFM_R004` | JPQL interface projection (5 columns, no entity-graph materialisation) |
| Name filter | `user_nm ilike '%'||?||'%'` | `LOWER(USER_NM) LIKE LOWER('%'||:q||'%')` (same semantic) + `@Size(max=100)` guard |
| Authorization | menu visibility, `NORM_USER_ONLY_YN = Y` | JWT-only, `UserController` allow-listed (no `@menuAuthz`) |
| Phone (`CLPH_NO`) | decrypted via legacy `decrypt()` DB fn | **not surfaced** — avoids a new legacy-fn dependency (picker has no phone column) |
| Sub-flags (`ADMIN_YN` / `MOB_YN`) | carried on `R004` | **not surfaced** — not consumed by the picker |
| Pagination | none (whole set) | none (`total_count` = row count) — MATCH |

---

## Dead / out-of-scope legacy flows (not ported)

| Legacy flow | Disposition |
|---|---|
| `CLPH_NO` phone decryption (`decrypt()` DB fn) | **DEAD LEGACY** — dropped; no consumer for a phone column on the picker |
| `ADMIN_YN` / `MOB_YN` sub-flags from `USER_BUSI_CONN_INFM_R004` | **DEAD LEGACY** — not consumed by the picker |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification + evidence.

---

## Adjacent surfaces — where the rest of "profile" went

| Surface | Disposition | Where |
|---|---|---|
| Current session identity (`/me`) | ported | **session-management** module (`AuthService.me`, `BUSI_INFM`) |
| Avatar image up/get/del | ported, relocated | **Settings > Users** (`UserImageService`) — **APPROVED DEVIATION** (out of auth scope), [README](../../settings/user-management/README.md) |
| Self-service "edit my profile" | not implemented | **MISSING** — no auth endpoint; FE avatar-edit pencil is UI-only |

---

## Schema / key-gen migrations

| Item | Where |
|---|---|
| `USER_INFM` / `USER_BUSI_CONN_INFM` / `BUSI_INFM` schema | legacy tables, column-for-column DEV parity (Legacy Schema Parity Rule); in the inventory. Created with the auth schema (V1 series). |
| Key generation | **none** — read-only surface, no new rows, no `wb_get_next_val` branch |

No Flyway is auto-run (`ddl-auto: validate`); the schema is treated as external / pre-migrated. The
auth-profile surface adds **no migration of its own**.

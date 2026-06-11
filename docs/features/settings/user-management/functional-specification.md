# User Management — Functional Specification (backend)

Back to [README](README.md) · [business-flow](business-flow.md) · [database-mapping](database-mapping.md) · [interface-specification](interface-specification.md)

Authoritative cross-system functional behaviour also lives in the shared bundle:
[`docs/features/user-management/functional-specification.md`](../../../../../docs/features/user-management/functional-specification.md).
This file records the **backend** view of that behaviour as shipped.

---

## 1. Scope

Server-side support for the Settings → Users admin screen:

- **List** active users linked to the current tenant (+ count).
- **Detail** read of one user.
- **Create** a user (profile + password + optional admin flag), linked to the tenant.
- **Update** profile, optional password reset, admin toggle.
- **Delete** = per-tenant soft unlink (not a global account delete).
- **Menu entitlement** read/write (per-user `MENU_AUTH_CD` grants).
- **Avatar** upload / remove / stream.
- **Check** login-id availability.

Out of the implemented scope (deferred, with rationale in
[refactor-gap-analysis.md](refactor-gap-analysis.md)): admin-driven `LOCK_YN`/`ACTV_YN` toggle from
this screen, the 5-failure auto-lock counter, password reuse-history enforcement, action-level
(CRUD) entitlement (`USER_MENU_ACTION_AUTH_INFM`), and any plan/seat cap.

## 2. Fields

| Field | Wire (snake) | Create | Update | Notes |
|---|---|:---:|:---:|---|
| Login ID | `user_id` | required | path-only (immutable) | Global PK; uniqueness checked via check-id + create |
| Password | `user_pw` | required | optional | BCrypt(10) on write; blank on update = keep existing |
| Full name | `user_nm` | required | required | |
| Email | `eml` | required | required | `@Email` format |
| Phone | `clph_no` | required | required | Stored plain in refactor (legacy DEV rows are AES-encrypted; read path sanitizes) |
| Position | `posi_nm` | required | required | |
| Department | `dept_nm` | required | required | |
| Set as admin | `set_as_admin` | optional (default false) | optional | Drives `BUSI_ADMN_INFM` row presence |

Read-only response-only fields: `is_admin`, `is_current_user`, `user_img_url`.

## 3. Validation rules (server)

Bean-validation on the request records; failures → `WBE_VALIDATION` (HTTP 200, standard envelope):

- `user_id` — `@NotBlank @Size(max=100)`.
- `user_pw` — create: `@NotBlank @Size(min=8,max=100) @Pattern(".*[^A-Za-z0-9].*")` (≥ 8 chars, ≥ 1
  non-alphanumeric). Update: same pattern/size but **optional** (only validated when supplied).
- `user_nm`, `clph_no`, `posi_nm`, `dept_nm` — `@NotBlank @Size(max=100)`.
- `eml` — `@NotBlank @Size(max=100) @Email`.
- `menu_auth_cds` — `@NotNull List<@Size(min=1,max=100) String>` (empty list = revoke-all is valid).

Business-rule validations (service layer, `BusinessException`):

| Rule | Error code |
|---|---|
| Login id already exists (create) | `WBE_USERS_DUPLICATE_LOGIN_ID` |
| User/link not found in tenant | `WBE_USERS_NOT_FOUND` |
| check-id called with blank id | `WBE_USERS_LOGIN_ID_REQUIRED` |
| Deleting your own account | `WBE_USERS_CANNOT_DELETE_SELF` |
| Demoting/deleting the last admin | `WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN` |

Avatar validation (`UserImageService`): `WBE2115` (bad filename), `WBE2116` (empty), `WBE2117`
(> 2 MB, with cap arg), `WBE2118` (extension not in allow-list, with list arg), `WBE2111` (disk write
failure), `WBE2113` (no avatar set), `WBE2114` (file missing).

## 4. Business rules

1. **Tenant scope is absolute.** Every query constrains on `BIZ_KEY = currentBizKey()`.
2. **No plan cap.** Legacy defined `WBW1151` (max users) but never enforced it; refactor keeps the
   count for display only. (The 5-user cap in the old plan was dropped — see gap analysis.)
3. **Admin authority = `BUSI_ADMN_INFM` row presence** for `(bizKey, userId)`. There is no
   `AUTH_GR_CD` column on the link table.
4. **Last admin protected.** A tenant must always retain ≥ 1 admin — enforced on both the update
   demote path and the delete path (`BusiAdmnInfmRepository.countByBizKey`).
5. **Self-delete blocked** from this screen.
6. **Soft per-tenant delete.** `USER_BUSI_CONN_INFM.DEL_YN='Y'` + admin-row delete + `ACCT_AUTH_INFO`
   revoke; the `USER_INFM` master row is preserved.
7. **Login id is immutable** post-create (PK).
8. **Password write is one-way** (BCrypt); the API never returns a hash. Detail/list never expose
   `user_pw`.
9. **Menu entitlement is Y-only, full-set replace** (delete-then-insert).

## 5. Permission model

The implemented model has **two effective roles** at this screen — there is no distinct "Manager":

- **Admin** — `BUSI_ADMN_INFM` row exists for `(bizKey, user)`; full CRUD + entitlement + avatar
  management. JWT carries `authGrCd="ADMIN"` → Spring authority `ROLE_ADMIN`.
- **Viewer (read-only)** — a non-admin user who holds the `settings-core-users` menu grant. Can list
  and open users (and stream avatars) but every write returns **403 / `WBE1003`**.
- **No access** — a non-admin without the grant cannot open the panel; read endpoints return 403.

Enforcement is two-layered:

| Layer | Mechanism |
|---|---|
| Controller | `@PreAuthorize("@userMgmtAccess.canViewUsers()")` on reads; `@PreAuthorize("hasRole('ADMIN')")` on writes |
| Service (defence-in-depth) | `UserManagementService.requireAdmin()` and `MenuEntitlementService.save()` re-check `TenantContext.currentIsAdmin()` → `AccessDeniedException` **before any DB access** |

`UserManagementAccessPolicy.canViewUsers()` returns `true` immediately for admins, otherwise probes
`USER_MENU_AUTH_INFM` for `settings-core-users` / `USE_YN='Y'`.

> A Viewer's read-only experience (disabled "Set as Admin", masked password, hidden Menu Entitlement
> tab, no Add/Edit/Delete) is enforced authoritatively here on the backend; the frontend mirrors it for
> UX. See [verified read-only behaviour](../../../../../wecms-web/docs/features/settings/user-management/ui-specification.md#read-only-viewer-behaviour).

## 6. Error handling

All errors flow through `GlobalExceptionHandler` and the standard `{ status, timestamp, path,
trace_id }` envelope. Business outcomes (`BusinessException`) return HTTP 200 with `status.error=true`;
the `AccessDeniedException` from the defence-in-depth guards returns HTTP 403 / `WBE1003`. Full code
table in [interface-specification.md §Error codes](interface-specification.md#error-codes).

## 7. Audit

The user-management write paths do **not** currently write `AUDIT_LOGS_INFM` rows (unlike
firm-banking-settings / contacts / chart-of-accounts). This is a known parity item tracked as an open
question — see [refactor-gap-analysis.md](refactor-gap-analysis.md#open-questions). Authentication
events remain captured in `USER_LOGIN_HIS` by the auth domain.

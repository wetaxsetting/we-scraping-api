# User Management — Refactor Gap Analysis

Back to [README](README.md) · [functional-specification](functional-specification.md) · [migration-guide](migration-guide.md)

Parity review of the shipped refactor (Spring Boot + Next.js) against legacy JEX `comm_0190_*`.
The deeper cross-system analysis (incl. the SEC-1 authorization remediation recorded 2026-06-05)
lives in [`docs/features/user-management/refactor-gap-analysis.md`](../../../../../docs/features/user-management/refactor-gap-analysis.md).

**Status date:** 2026-06-05.

---

## 1. Parity table

| Area | Legacy (`comm_0190_*`) | Refactor | Status |
|---|---|---|---|
| User List | `comm_0190_001_r001`, paginated | `GET /settings/users`, load-all + newest-first, client search/sort | **MATCH** (pagination intentionally dropped — small per-tenant sets) |
| User Detail | `comm_0190_003_r001` | `GET /settings/users/{id}` | **MATCH** |
| Create User | `comm_0190_002_c001` | `POST /settings/users` | **MATCH** |
| Update User | `comm_0190_003_u001` | `PUT /settings/users/{id}` | **MATCH** |
| Role / Admin mgmt | `BUSI_ADMN_INFM` upsert/delete | `set_as_admin` toggle + last-admin guard | **MATCH** (refactor adds last-admin guard — stricter) |
| Permission (menu) mgmt | `USER_MENU_AUTH_INFM` (menu-level) | `GET/PUT .../menu-entitlement`, Y-only, delete-then-insert | **MATCH** (menu-level) |
| Menu Entitlement (action-level) | `USER_MENU_ACTION_AUTH_INFM` (CRUD per screen) | not implemented | **MISSING** (OQ-DOC-UM-2) |
| User Status mgmt | `ACTV_YN` / `LOCK_YN` editable in legacy update | not editable from this screen | **PARTIAL** (OQ-DOC-UM-3) |
| Search | server-side filter | client-side (name/login/email) over load-all | **MATCH** (equivalent result) |
| Pagination | yes | none (load-all) | **PARTIAL** (deliberate — documented) |
| Validation | server + JSP | bean-validation + service rules | **MATCH** (refactor stricter: password `@Pattern`) |
| Security (authz) | admin via `BUSI_ADMN_INFM`; menu/action auth | split `@PreAuthorize` + service guards (read=ADMIN/grant, write=ADMIN) | **MATCH** (SEC-1 remediated 2026-06-05) |
| Permission enforcement | session + menu/action auth checks | JWT `ROLE_ADMIN/USER` + `canViewUsers()` + defence-in-depth | **MATCH** |
| Avatar | `USER_IMG` file on link | upload/remove/stream, 2 MB, allow-list, opaque filename | **MATCH** (refactor hardens validation) |
| Password handling | `hash()` + reuse history (`USER_PW_CHG_HIS`) | legacy SHA-256 write (JEX-compatible) + BCrypt read tolerance, no upgrade; **no reuse history** | **PARTIAL** (OQ-DOC-UM-4) |
| Phone storage | `encrypt/decrypt` | plain + read-sanitize | **MATCH** (intentional — no legacy crypto funcs) |
| Audit logging | (legacy `comm_0190` writes minimal audit) | **no `AUDIT_LOGS_INFM` write** on mutations | **PARTIAL** (OQ-DOC-UM-1) |
| Employee sync | `EMPL_INFM` updated on user edit | not implemented | **MISSING** (out of scope — not a PROD-active concern for this screen) |

## 2. Refactor Status

**Core User Management (list, detail, create, update, delete, role/admin, menu-level entitlement,
avatar, validation, security, permission enforcement): ✅ 100% Complete and legacy-parity verified
(2026-06-05).**

Supporting evidence:
- 11 live endpoints under `/api/v1/settings/users` (see [interface-specification](interface-specification.md)).
- Frontend wired to live endpoints with no mock fallback (see [frontend api-consumption](../../../../../wecms-web/docs/features/settings/user-management/api-consumption.md)).
- Split authorization enforced at controller **and** service layer; covered by
  `UserManagementAuthorizationTest` + `UserManagementAccessPolicyTest`.
- Schema in place (`V131`); DB columns verified against the refactor DB 2026-06-05.

The **PARTIAL / MISSING** rows above are all **deliberate scope decisions or deferred enhancements**,
not regressions against the agreed feature scope. They do not block the "complete" status of the core
screen but are tracked as open questions for product confirmation.

## 3. Plan-vs-shipped drift

The earlier flat plan [`../users.md`](../users.md) (2026-05-14) diverges from what shipped — readers of
that file should treat the following as superseded:

| Old plan said | Shipped reality |
|---|---|
| Base path `/api/v1/users` | `/api/v1/settings/users` |
| Hard **5-user plan cap** (`WBE_USERS_PLAN_LIMIT_REACHED`) | **No cap** — `total_count` is display only; code error retired |
| `WBE_USERS_PASSWORD_TOO_WEAK` custom code | Weak/short password → generic `WBE_VALIDATION` (`@Pattern`/`@Size`) |
| Menu Entitlement **deferred / static placeholder** | **Implemented** (V131 + `MenuEntitlementService`) |
| Avatar upload **deferred** | **Implemented** (`UserImageService`) |
| Admin-only screen (`hasRole('ADMIN')` everywhere) | **Split** — reads also open to `settings-core-users` grant (SEC-1) |
| Zod schema in `useUserForm.ts` | Forms are uncontrolled + imperative `collect()`; validation client-side + server bean-validation |

## 4. Open questions (tracked — do not close without PO confirmation)

These are proposed for the backend OQ log
(planning/open-questions/documentation-follow-up.md _(removed — historical `planning/` folder deleted by project decision)_).
None block release; each needs a product call.

| ID | Question | Current behaviour / proposed default |
|---|---|---|
| OQ-DOC-UM-1 | Should user create/update/delete write `AUDIT_LOGS_INFM` rows (slug `user-management`), matching firm-banking-settings / contacts / chart-of-accounts? | Currently **no audit write**. Proposed: add audit on mutations for consistency. |
| OQ-DOC-UM-2 | Is action-level (CRUD) entitlement (`USER_MENU_ACTION_AUTH_INFM`) in scope, or is menu-level sufficient? | Menu-level only today. Proposed: defer until a product requirement appears. |
| OQ-DOC-UM-3 | Should admins toggle `ACTV_YN` / `LOCK_YN` (activate/deactivate, unlock) from this screen? | Not editable here today; status is DBA/auth-driven. Proposed: add if business needs in-screen status control. |
| OQ-DOC-UM-4 | Should password reuse history (`USER_PW_CHG_HIS`) + 5-failure auto-lock be enforced? | **Resolved.** 5-failure auto-lock is **implemented** (F-10, `LoginLockoutService` → `USER_INFM.LOCK_YN`); password-reuse history (last-3) is enforced by `ChangePasswordService` via `USER_PW_CHG_HIS`. |
| OQ-DOC-UM-5 | The wire `clph_no` carries no separate country code, so the inbound `mapUserRow` stamps `phoneCountryCode="84"`. The Add/Edit forms already render an interactive `PhoneCountryPicker` (default `"84"`, KH/KR/VN), but the picked code is not persisted as a distinct wire field. Should the wire carry a real country code? | Cosmetic; the picker exists but the code isn't round-tripped. Revisit when the wire splits country code from number. |

## 5. Migration status

**User Management: 100% complete** for the agreed refactor scope (core CRUD + role + menu-level
entitlement + avatar + security). Remaining items (audit write, action-level grants, in-screen status
toggle, password reuse history) are **deferred enhancements / open questions**, explicitly out of the
shipped scope and pending PO direction — see §4.

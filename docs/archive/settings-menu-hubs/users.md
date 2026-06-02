# Users

> Canonical Settings-menu entry for **Settings → Users**. Detailed plan lives in the linked doc.

## Overview
Users (Settings → Users) manages the tenant's user accounts: list, create, edit, admin-role toggle,
and menu entitlement. Enforces a hard plan cap (5 users for this MR). Slug: `user-management`.
Mirrors live JEX `admn_0021_*`.

- Implementation plan: [../features/admin/user-management.md](../../features/settings/users.md)

## Related APIs
CRUD over the user + tenant-link tables (full endpoint table in the plan doc):
- list / detail
- create user (+ tenant link, admin role, create-time password)
- update user
- enable/disable, admin-role toggle
- `UserDirectoryService` — read-only picker lookup (already exists; used by approval-line approver
  selection, etc.)

## Database Tables
- `USER_INFM` — user master (V1 / V7)
- `USER_BUSI_CONN_INFM` — user ↔ tenant link (V1)
- `BUSI_ADMN_INFM` — tenant-admin role flag (V11)
- `USER_MENU_AUTH_INFM` — menu entitlement (**deferred** — needs a new Flyway migration; tab stays
  static for now)

## Business Rules
- **Hard 5-user plan cap** (service constant for this MR; no plan-tier table yet).
- Admin-role granted/revoked via `BUSI_ADMN_INFM`.
- No new Flyway migrations in the first MR — uses only existing tables; `ddl-auto: validate` stays.
- Menu-entitlement persistence, avatar upload, admin password reset, and `LOCK_YN` auto-lock are
  out of scope for the first MR.

## Validation
- User id / name / e-mail required and format-checked; user id unique per tenant.
- Create blocked when the tenant is at the 5-user cap.

## Error Cases
- Plan-cap exceeded → domain error.
- Duplicate user id → validation error.
- Standard envelope; `WBE_VALIDATION` / `WBE9999`.

## Notes
Self-service "Change Password" is a separate ProfileMenu flow (not this screen). Web/UI:
[../../../wecms-web/docs/settings/users.md](../../../../wecms-web/docs/features/settings/users.md).

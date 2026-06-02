# features/admin/

Feature specs for the **administrative** domain in the refactor.

## Purpose

Admin covers tenant-administrator screens that govern the WeCMS application itself: user management, role assignment, biz-key membership, and (later) tenant-level operational settings that are not part of the per-feature "settings" group.

## Scope

| File | Refactor scope |
|---|---|
| [user-management.md](../../features/settings/users.md) | User CRUD, role assignment, biz-key membership, password reset |

## Document standards

- Frontmatter table: `JEX counterpart`, `Refactor status`, `Owner`, `Sprint`.
- Document any DBA-only carve-outs explicitly (account provisioning is DBA-only — never expose self-signup or self-reset to tenant admins without PO sign-off).
- Cite JEX user-master entities (`USR_INFM`, `USR_BIZ_INFM`, role tables) by file:line.

## Naming convention

- kebab-case slug matching JEX counterpart.
- No spaces, no uppercase.

## Related

- Backend: [`../../backend/`](../../references/business-rules/) (per-feature design files)
- Frontend: [`../../frontend/`](../../../../wecms-web/docs/)
- Security baseline applies across admin features → see [`../../../security/`](../../../../docs/security/) and [`../../../_shared/security-baseline.md`](../../../../docs/_shared/security-baseline.md).

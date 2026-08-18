# features/auth/ — Backend (navigation)

**Cross-cutting system domain** — authentication, authorization, and session management for the Spring
Boot + Next.js refactor. Not a WeCMS top-menu group (Banking / Payment / Add-on / Settings); it is the
system feature group every authenticated endpoint depends on.

Standardized 2026-06-23 into **8 per-module folders** (9-file structure each) matching the actual
implementation. The earlier flat `login-auth.md` is now a [summary](login-auth.md).

## Audit deliverables

| Doc | Purpose |
|---|---|
| [auth-docs-audit.md](../../audits/modules/auth-docs-audit.md) | What the audit changed; stale-fact corrections; per-module notes |
| [auth-gap-analysis.md](../../audits/modules/auth-gap-analysis.md) | Consolidated gap table (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) |
| [auth-source-index.md](../../audits/modules/auth-source-index.md) | Every auth source file (BE + FE), grouped by module |

## Modules

| Module | Scope | Backend surface | Status |
|---|---|---|---|
| [login](login/README.md) | Authentication flow, JWT issuance, login history, multi-company resolution | `POST /api/v1/auth/login` | Working |
| [logout](logout/README.md) | Logout — server-side `jti` revocation + client token clear | `POST /api/v1/auth/logout` | Working |
| [change-password](change-password/README.md) | Self-service password change, history reuse, SHA-256 | `POST /api/v1/auth/change-password` | Working |
| [profile](profile/README.md) | Tenant user directory (`comm_0192_001_r001`) | `GET /api/v1/users` | Working |
| [jwt-authentication](jwt-authentication/README.md) | Token issue/parse/filter/entry-point, claims, TTL | `JwtTokenProvider` + filter chain | Working |
| [authorization](authorization/README.md) | `@menuAuthz` guard, `@PreAuthorize`, roles, product-type, coverage test | enforcement layer | Working (F-3 complete) |
| [menu-authorization](menu-authorization/README.md) | Permission codes, grants, resolved navigation tree, entitlement editor | `GET /api/v1/navigation` | Working |
| [session-management](session-management/README.md) | `/me` bootstrap, stateless model, `TenantContext` lifecycle | `GET /api/v1/auth/me` | Working |

## Error codes (auth surface)

`WBI0000` · `WBE1009` (id/pw) · `WBE1010` (inactive) · `WBE1011` (locked) · `WBE1128` (deleted) ·
`WBE1401` (session expired) · `WBE1003` (access denied) · `WBE2091` · `WBE_CHANGE_PASSWORD_MISMATCH` ·
`WBE_CHANGE_PASSWORD_REUSED` · `WBE_VALIDATION`.

## What does NOT live here

- **Permission / role catalogue (shared)** — [`../../../../docs/_shared/permission-roles.md`](../../../../docs/_shared/permission-roles.md).
- **Security baseline (shared)** — [`../../../../docs/security/`](../../../../docs/security/).
- **User administration CRUD + avatar image** — Settings: [`../settings/user-management/README.md`](../settings/user-management/README.md).
- **Bank / GDT login credentials** (not user auth) — Settings: [`../settings/login-credentials/README.md`](../settings/login-credentials/README.md).
- **JEX menu/product-type spec** — [`../../../../docs/jex/features/menu-product-type-management/`](../../../../docs/jex/features/menu-product-type-management/).

## Naming traceability

Each module slug matches its frontend counterpart at `wecms-web/docs/features/auth/<slug>/`. Refactor uses
kebab-case slugs, never legacy WSVC ids (`logn_0001_001`).

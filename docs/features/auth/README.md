# features/auth/

**Cross-cutting system domain** — user authentication for the Spring Boot + Next.js refactor.

This is not a WeCMS top-menu group (Banking / Payment / Add-on / Settings). It is a **system feature
group** that every authenticated screen depends on. It was formalized 2026-06-01 to resolve
**OQ-DOC-1** (see [`../../planning/open-questions/documentation-follow-up.md`](../../planning/open-questions/documentation-follow-up.md)).

## Docs

| Doc | Scope | Status |
|---|---|---|
| [login-auth.md](login-auth.md) | Login / logout / me — JWT issue, tenant resolution, `USER_LOGIN_HIS` audit, password upgrade | Working (backend implemented) |

## Scope of this domain

Covered by `login-auth.md`:

- **Login** — `POST /api/v1/auth/login` (JEX `logn_0001_001_r001`)
- **Me / session bootstrap** — `GET /api/v1/auth/me` (JEX `logn_0001_001_r002`)
- **Logout** — `POST /api/v1/auth/logout` (client-side token clear; stub)
- **JWT** — stateless access token, claims, TTL, filter chain
- **Authorization** — `authGrCd` (`ROLE_ADMIN` / `ROLE_USER`) derivation, tenant isolation
- **Session model** — stateless (no server session, no refresh token in current scope)

## What does NOT live here

- **Permission / role catalogue** — cross-system, lives once at
  [`../../../../docs/_shared/permission-roles.md`](../../../../docs/_shared/permission-roles.md) (pointer: [`../../permissions.md`](../../permissions.md)).
- **Security design / filter chain detail** — [`../../references/business-rules/security/README.md`](../../references/business-rules/security/README.md)
  and the shared [`../../../../docs/security/`](../../../../docs/security/) baseline.
- **Self-service password change** — a Settings / ProfileMenu item:
  [`../settings/change-password.md`](../settings/change-password.md).
- **User administration (CRUD)** — Settings item: [`../settings/users.md`](../settings/users.md).
- **Bank / GDT login credentials** — *not* user authentication; that is bank-scraping credential
  management under Settings: [`../settings/login-credentials.md`](../settings/login-credentials.md).

## Related reference

- Sprint plan: [`../../archive/planning/phase-1/task-sprint-0-login-refactor.md`](../../archive/planning/phase-1/task-sprint-0-login-refactor.md)
- Bootstrap / password strategy: [`../../planning/roadmap/task-plan-common-backend-functional-seahorse.md`](../../planning/roadmap/task-plan-common-backend-functional-seahorse.md)
- Endpoint index: [`../../references/api-spec/api-mapping.md`](../../references/api-spec/api-mapping.md) · JEX→Spring: [`../../references/migration/service-mapping.md`](../../references/migration/service-mapping.md)
- JEX functional reference (read-only): [`../../../../docs/jex/features/login-wecms.md`](../../../../docs/jex/features/login-wecms.md), [`../../../../docs/jex/features/login-spring-boot-migration.md`](../../../../docs/jex/features/login-spring-boot-migration.md), [`../../../../docs/jex/api-spec/auth.md`](../../../../docs/jex/api-spec/auth.md)
- Frontend counterpart: [`../../../../wecms-web/docs/features/auth/login-auth.md`](../../../../wecms-web/docs/features/auth/login-auth.md)

## Naming traceability

Slug `login-auth` matches the frontend counterpart `wecms-web/docs/features/auth/login-auth.md` and
the `login-auth` references in both mapping files. Per workspace CLAUDE.md, the refactor uses
kebab-case slugs (`login-auth`), never legacy WSVC ids (`logn_0001_001`).

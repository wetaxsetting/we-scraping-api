# Auth > Login Authentication — Summary (Backend)

> ⚠️ **This document is a high-level feature summary.**
> The detailed source of truth is located in:
> `wecms-api/docs/features/auth/{module}/`
>
> Specifically: [login/](login/README.md) · [logout/](logout/README.md) · [session-management/](session-management/README.md)
> (login flow, logout, and `/me` session bootstrap were split into per-module folders in the 2026-06-23
> Auth standardization — see [auth-docs-audit.md](../../audits/auth-docs-audit.md)).

**Domain:** `auth` (system / cross-cutting — JEX `logn` module)
**Backend package:** `com.kosign.wecms.domain.auth`
**Status:** **Working** — login / logout / me / change-password implemented in `wecms-api`.
**JEX service IDs:** `logn_0001_001_r001` (login), `logn_0001_001_r002` (me).

## Overview

A user submits ID + password on the Next.js login page; the backend validates against `USER_INFM`,
resolves the tenant link in `USER_BUSI_CONN_INFM`, writes a `USER_LOGIN_HIS` audit row, and issues a
stateless JWT. All non-public endpoints require `Authorization: Bearer <token>`.

**Operational model: one user = one company account.** Login resolves the single active
`USER_BUSI_CONN_INFM` link and lands the user on the dashboard. Multi-company linkage is an exceptional /
test-demo fallback (`LoginResponse.chooser(...)` + `CompanyOption[]`, and the frontend `CompanyChooser`
**does exist** for it) — not a documented business requirement.

## Endpoints (summary)

| Method | Path | Auth | Module |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | [login/](login/README.md) |
| POST | `/api/v1/auth/logout` | Public (stateless stub) | [logout/](logout/README.md) |
| GET | `/api/v1/auth/me` | Protected | [session-management/](session-management/README.md) |
| POST | `/api/v1/auth/change-password` | Protected | [change-password/](change-password/README.md) |

## Corrections vs. the previous flat doc

- **Verify-only password** — login does **not** upgrade SHA-256 → BCrypt (it never did in the current code).
  SHA-256 (`public.hash()`) stays for coexistence with the live JEX system. See [change-password/](change-password/README.md).
- **Company chooser exists** — both backend (`chooser(...)`) and frontend (`CompanyChooser`).
- **JWT 401** returns `WBE1401` (session-expired), not `WBE1009`.

## Related

- Backend module folders: [login/](login/README.md) · [logout/](logout/README.md) · [change-password/](change-password/README.md) · [profile/](profile/README.md) · [jwt-authentication/](jwt-authentication/README.md) · [authorization/](authorization/README.md) · [menu-authorization/](menu-authorization/README.md) · [session-management/](session-management/README.md)
- Audit deliverables: [auth-docs-audit.md](../../audits/auth-docs-audit.md) · [auth-gap-analysis.md](../../audits/auth-gap-analysis.md) · [auth-source-index.md](../../audits/auth-source-index.md)
- Frontend counterpart: [../../../../wecms-web/docs/features/auth/login/README.md](../../../../wecms-web/docs/features/auth/login/README.md)

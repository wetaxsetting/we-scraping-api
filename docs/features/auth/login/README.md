# Auth > Login — Backend Feature (Refactor)

**Status:** ✅ Implemented + unit-tested (`AuthServiceTest`, 19 scenarios green). `POST /api/v1/auth/login`
serves the production single-link auto-pick path plus a multi-link company-chooser fallback; verify-only
SHA-256 password check (coexistence with the live JEX system); JWT issued at finalize; `USER_LOGIN_HIS`
audit row on every terminal attempt.
**Module:** Auth (cross-cutting system domain — not a WeCMS top-menu group)
**Refactor slug:** `login` (matches the `wecms-web` frontend counterpart)
**Wire base path:** `POST /api/v1/auth/login` (public — permit-all in `SecurityConfig`)
**Legacy JEX counterpart:** `logn_0001_001_r001` screen action → `SzonHelper.logn_2001_001_r001` service.
Tables `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM`, `BUSI_ADMN_INFM`, `USER_LOGIN_HIS` — all in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md).

> This folder is the **primary documentation home** for the Login flow on the backend. It is scoped to
> the login endpoint only. `/me` (session bootstrap), logout, and self-service password change are
> **separate auth modules** — cross-linked below, not duplicated here. The JEX-side spec is the
> read-only source of truth for legacy behaviour; the implemented Spring code is the source of truth for
> the refactor behaviour.

---

## What this feature is

Login is the single entry point that turns a `(user_id, user_pw)` pair (plus an optional `biz_key`) into
a signed JWT bound to one tenant. It:

- Loads `USER_INFM` and runs the legacy flag-gate order (deleted → inactive → locked → password).
- Verifies the password **verify-only** against the legacy `public.hash()` SHA-256 credential — no
  write-back, so the live JEX system keeps authenticating the same row (coexistence).
- Resolves the tenant from `USER_BUSI_CONN_INFM`: **one active link → auto-pick** (the normal
  production path); **≥ 2 links + no `biz_key` → company-chooser response** (exceptional fallback, no
  JWT issued); **≥ 2 links + `biz_key` → match the chosen link**.
- Resolves authority from `BUSI_ADMN_INFM` (presence ⇒ `ADMIN`, absence ⇒ `USER`).
- Reads `BUSI_INFM` once for the display name (`CMPN_NM`), verbatim product type (`PRDT_TYPE`) and tax id
  (`TAX_ID`).
- On finalize: updates `USER_BUSI_CONN_INFM.LST_LGN_DATE`, writes a `SUCCESS` `USER_LOGIN_HIS` row, and
  issues the JWT carrying `{ sub, userNm, bizKey, langCd, authGrCd, prdtType? }`.

Every terminal attempt — success or failure — writes a `USER_LOGIN_HIS` row in a `REQUIRES_NEW`
transaction so a FAIL row survives the outer rollback.

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoint | `POST /api/v1/auth/login` — see [interface-specification.md](interface-specification.md) |
| Auth | **Public** (permit-all) — login is the credential exchange that mints the JWT |
| Response shapes | **authenticated** (single-link / finalize) + **chooser** (multi-link, no `biz_key`) |
| Tables | `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM`, `BUSI_ADMN_INFM`, `USER_LOGIN_HIS` — all in the inventory |
| Flag-gate order | `DEL_YN=Y`→`WBE1128`, `ACTV_YN=N`→`WBE1010`, `LOCK_YN=Y`→`WBE1011`, pw mismatch→`WBE1009` |
| Password | **verify-only** SHA-256 (`public.hash()`); never re-encoded / written on login (coexistence) |
| Authority | `BUSI_ADMN_INFM` presence ⇒ `ADMIN`, else `USER` (legacy `USER_BUSI_CONN_INFM` has no `AUTH_GR_CD`) |
| JWT claims | `sub=userId`, `userNm`, `bizKey`, `langCd`, `authGrCd`, `prdtType?` (omitted when null) |
| Audit | `USER_LOGIN_HIS` row on every terminal attempt (`SUCCESS` / `FAIL`); `LST_LGN_DATE` on finalize |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`); `access_token` is the one `@JsonProperty` override |

---

## Backend package layout (login slice of `domain/auth`)

```
com.kosign.wecms.domain.auth
├── controller/AuthController.java        POST /login (+ /me, /logout, /change-password — other modules)
├── service/AuthService.java             login(req, ipAddr, langCd) — port of SzonHelper.logn_2001_001_r001
├── service/LoginHistoryService.java     record(...) @Transactional(REQUIRES_NEW) — USER_LOGIN_HIS writer
├── dto/LoginRequest.java                user_id, user_pw, biz_key?
├── dto/LoginResponse.java               authenticated(...) + chooser(...) factories
├── dto/CompanyOption.java               one row per active link in the chooser
├── entity/{UserInfm, BusiInfm, UserBusiConnInfm, BusiAdmnInfm, UserLoginHis}.java
└── repository/{UserInfm, BusiInfm, UserBusiConnInfm, BusiAdmnInfm, UserLoginHis}Repository.java
```

Cross-package collaborators (reused, not re-implemented):
[JwtTokenProvider](../../../../src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java) +
the `PasswordEncoder` bean (`WecmsPasswordEncoder`) +
[TenantContext](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) +
[MenuAuthorizationService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java)
(used by `/me`, not login).

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | Numbered authentication flow, validation, flag-gates, multi-company resolution, login-history, JWT claims, error surface |
| [business-flow.md](business-flow.md) | End-to-end narrative + legacy `SzonHelper.logn_2001_001_r001` parity notes (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) |
| [database-mapping.md](database-mapping.md) | `USER_INFM` / `USER_BUSI_CONN_INFM` / `BUSI_INFM` / `BUSI_ADMN_INFM` / `USER_LOGIN_HIS` column → field per table |
| [interface-specification.md](interface-specification.md) | Endpoint table + request / authenticated + chooser response JSON, error envelope, error codes |
| [source-code-index.md](source-code-index.md) | Every controller / service / dto / entity / repository file (full path + role + key methods) |
| [migration-guide.md](migration-guide.md) | JEX → Spring layer map (`logn_0001_001_r001` / `SzonHelper` → `AuthController` / `AuthService`), field mapping, what changed |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix with the exact status vocabulary + corrected stale facts + deferred items |
| [test-scenarios.md](test-scenarios.md) | `AuthServiceTest` scenarios + coverage gaps |

---

## Sibling auth modules (cross-links, not duplicated here)

- [session-management](../session-management/README.md) — `GET /api/v1/auth/me`, stateless TTL, no refresh / blacklist
- [logout](../logout/README.md) — `POST /api/v1/auth/logout` (client-side token clear; stub)
- [change-password](../change-password/README.md) — `POST /api/v1/auth/change-password` (password hashing detail lives here)
- [jwt-authentication](../jwt-authentication/README.md) — token issue / parse / filter chain / 401 entry point
- [authorization](../authorization/README.md) · [menu-authorization](../menu-authorization/README.md) — `@menuAuthz`, product-tree gating
- [profile](../profile/README.md) — tenant user directory (`GET /api/v1/users`)

## Counterparts & reference

- **Frontend (Next.js):** [wecms-web/docs/features/auth/login/README.md](../../../../../wecms-web/docs/features/auth/login/README.md)
- **JEX functional reference (read-only):** [docs/jex/features/login-wecms.md](../../../../../docs/jex/features/login-wecms.md) · [docs/jex/api-spec/auth.md](../../../../../docs/jex/api-spec/auth.md)
- **Legacy table inventory (baseline):** [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)

# Auth Modules — Gap Analysis

> Consolidated gaps after the 2026-06-23 Auth documentation audit. Each gap is classified with the project
> vocabulary — **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING** — plus a *kind*
> (code-exists-doc-missing / doc-exists-feature-removed / backend-missing / frontend-missing / test-missing /
> code-comment-stale / PO-decision-required / by-design).
> Companion files: [auth-docs-audit.md](auth-docs-audit.md) · [auth-source-index.md](auth-source-index.md).

## Consolidated gap table

| ID | Module | Gap | Class | Kind | Owner |
|---|---|---|---|---|---|
| G-LOGIN-1 | login | 5-failure auto-lock counter — `USER_LOGIN_HIS` FAIL rows written, but no lock logic | MISSING | backend-missing | dev |
| G-LOGIN-2 | login | Post-auth gates `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS` (legacy checks after password) not ported | MISSING / PARTIAL | backend-missing | dev / PO |
| G-LOGIN-3 | login | Rate-limiting / brute-force protection absent (mandatory before any non-local deploy) | MISSING | backend-missing | dev |
| G-LOGIN-4 | login | Multi-company `CompanyChooser` exists FE+BE (old docs claimed "no chooser") — exceptional/test-demo fallback, not a documented business flow | APPROVED DEVIATION | doc-corrected | — |
| G-LOGIN-5 | login | Verify-only password (no SHA-256→BCrypt write-back) — old docs claimed an upgrade path | APPROVED DEVIATION | doc-corrected | — |
| G-LOGOUT-1 | logout | Server-side token revocation / blacklist absent (stateless JWT; token valid until TTL) | MISSING | backend-missing | dev |
| G-LOGOUT-2 | logout | Legacy server-side session invalidation not ported | DEAD LEGACY | doc-exists-feature-removed | PO (closed) |
| G-LOGOUT-3 | logout | Best-effort POST + swallowed errors + no error dialog (diverges from login/change-password) | APPROVED DEVIATION | by-design | — |
| G-LOGOUT-4 | logout | No dedicated logout test (BE) / no FE test | MISSING | test-missing | dev |
| G-CPW-1 | change-password | Password-expiry / `USER_INFM.PW_CHG_REQ_SEQ_NO` flow mapped but unread | MISSING | backend-missing | dev |
| G-CPW-2 | change-password | SHA-256 (`public.hash()`) on every write path instead of BCrypt — coexistence with live JEX | APPROVED DEVIATION | by-design | — |
| G-CPW-3 | change-password | No dedicated `ChangePasswordServiceTest` / controller test on disk; no FE test | MISSING | test-missing | dev |
| G-CPW-4 | change-password | Stale BCrypt wording in `LegacyHashPasswordEncoder` / `UserPwChgHis` Javadocs | — | code-comment-stale | dev (code MR) |
| G-PROF-1 | profile | Dedicated self-service "edit my profile" endpoint absent; FE avatar-edit pencil is UI-only (no handler/endpoint) | MISSING | backend-missing + frontend-missing | dev / PO |
| G-PROF-2 | profile | Avatar image CRUD lives in Settings > Users (`domain/setting/users`), not auth | APPROVED DEVIATION | by-design | — |
| G-PROF-3 | profile | `GET /api/v1/users` tenant directory = MATCH with legacy `comm_0192_001_r001`; no dedicated test | PARTIAL | test-missing | dev |
| G-JWT-1 | jwt-authentication | Refresh token rotation absent (stateless access token only) | MISSING | backend-missing | dev / PO |
| G-JWT-2 | jwt-authentication | Server-side revocation / JWT blacklist absent | MISSING | backend-missing | dev |
| G-JWT-3 | jwt-authentication | Stateless JWT vs legacy `HttpSession` | APPROVED DEVIATION (HttpSession = DEAD LEGACY) | by-design | — |
| G-JWT-4 | jwt-authentication | 401 entry point emits `WBE1401`; root `CLAUDE.md` §JWT-filter + template doc still say `WBE1009` | — | code-comment-stale | dev (code MR) |
| G-JWT-5 | jwt-authentication | FE token cookie is non-HttpOnly by design (middleware reads it; Axios reads localStorage) | APPROVED DEVIATION | by-design | — |
| G-AUTHZ-1 | authorization | `@menuAuthz`/`@PreAuthorize` coverage live (F-3 complete) + `EndpointAuthorizationCoverageTest` lock-in | MATCH | — | — |
| G-AUTHZ-2 | authorization | 12 allow-listed controllers carry no `@menuAuthz` (documented exceptions) | APPROVED DEVIATION | by-design | — |
| G-AUTHZ-3 | authorization | Per-product behavioral gates (HAENGSUNG table-2 / masking / hidden columns, Lumens readonly, e-Invoice gate) deferred per feature | PARTIAL | backend-followup | dev (per MR) |
| G-AUTHZ-4 | authorization | FE `menuAuth.ts` gating is UX-only; backend is the security boundary | APPROVED DEVIATION | by-design | — |
| G-AUTHZ-5 | authorization | No FE authorization unit tests (helpers trivially testable; no test runner in `wecms-web`) | MISSING | test-missing | dev |
| G-MENU-1 | menu-authorization | Navigation resolution (product ∩ grants, admin bypass, parent collapse, lang fallback) — `NavigationServiceTest` 8/8 | MATCH | — | — |
| G-MENU-2 | menu-authorization | Catalogue tables `MENU_ITEM`/`PRODUCT_MENU`/`MENU_ITEM_TRANSLATION` are refactor-owned infra (not legacy) — replace lost JexAdmin trees | APPROVED DEVIATION | by-design (PO-approved) | — |
| G-MENU-3 | menu-authorization | Per-node country gate not modelled; `SHOW_YN` designer gate enforced | PARTIAL / MISSING | backend-missing | dev / PO |
| G-MENU-4 | menu-authorization | Menu Entitlement editor endpoint URL/wire owned by Settings > Users (referenced, not re-specified here) | — | doc-boundary | — |
| G-SESS-1 | session-management | `GET /api/v1/auth/me` re-validation + `all_menus`/`allowed_menu_cds`/`tax_id` payload | MATCH | code-exists-doc-missing (now documented) | — |
| G-SESS-2 | session-management | Refresh tokens / server-side logout revocation absent | MISSING | backend-missing | dev / PO |
| G-SESS-3 | session-management | `?preview=1` client route guard removed; `proxy.ts` edge guard is sole source of truth | DEAD LEGACY | doc-exists-feature-removed | PO (closed) |
| G-SESS-4 | session-management | `/me` rejection-path unit tests (deleted/inactive/locked/link-removed) MISSING; only happy-path in `AuthServiceTest` | MISSING | test-missing | dev |
| G-SESS-5 | session-management | `TenantContext` ThreadLocal has no async propagation | PARTIAL | by-design | dev |
| CC-1 | cross-cutting | Stale BCrypt Javadoc in `SecurityConfig.passwordEncoder()` (verify-only SHA-256 in reality) | — | code-comment-stale | dev (code MR) |
| CC-2 | cross-cutting | `wecms-web/CLAUDE.md` Feature Status / Known Issues still list Change Password as "not implemented" | — | doc-stale | wecms-web-docs owner |
| CC-3 | cross-cutting | No frontend test runner / `*.test.*` files anywhere in `wecms-web` — all FE auth tests MISSING | MISSING | test-missing | dev |

---

## Test coverage snapshot

**Backend** has solid coverage on the core paths: `AuthServiceTest` (login single/multi/chooser/finalize +
flag-gates + me happy-paths + verify-only-no-write-back), `LegacyHashPasswordEncoderTest`,
`MenuAuthorizationServiceTest`, `NavigationServiceTest` (8/8), `MenuAuthorizationGuardTest`,
`EndpointAuthorizationCoverageTest` (deny-or-declared), `UserManagementAuthorizationTest`.

**Consistent MISSING gaps:**
- **Frontend tests** — no test runner / no `*.test.*` files in `wecms-web` for any auth screen (login, logout,
  change-password, profile, navigation). Largest single coverage gap.
- **`/me` rejection paths** — deleted / inactive / locked / link-removed → WBE1401 not unit-tested.
- **change-password** — no dedicated backend service/controller test.
- **logout** — no dedicated backend test.

---

## Priority follow-ups

1. **Code-comment / CLAUDE.md cleanup** (G-CPW-4, G-JWT-4, CC-1, CC-2) — reconcile the stale BCrypt-upgrade
   and WBE1009 wording with the verify-only SHA-256 + WBE1401 reality (code MR; out of this docs scope).
2. **Frontend test baseline** (CC-3, G-AUTHZ-5, G-SESS-4) — establish a `wecms-web` test runner and cover the
   pure auth helpers + critical flows (dev).
3. **Refresh token / server-side revocation** (G-JWT-1, G-JWT-2, G-LOGOUT-1, G-SESS-2) — design fresh
   (`REFRESH_TOKEN` table is off-inventory → PO decision) before any non-local deploy.
4. **Pre-deploy hardening** (G-LOGIN-1/2/3) — 5-failure auto-lock, post-auth gates, rate-limiting (dev/PO).
5. **Self-profile editing** (G-PROF-1) — decide whether the avatar-edit pencil + a self-profile endpoint are in
   scope (PO).
6. **Per-product behavioral gates** (G-AUTHZ-3) — continue porting with each feature MR (already the agreed cadence).

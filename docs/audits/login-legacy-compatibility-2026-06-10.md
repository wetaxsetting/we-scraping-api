# Login & Account-Creation Legacy-Compatibility Audit

- **System:** Refactor (Spring Boot `wecms-api`)
- **Date:** 2026-06-10
- **DB verified against:** JEX DEV (`wecmsvndevdb`) via read-only `postgres_dev_server_jexframe` MCP
- **Scope:** confirm the Spring app authenticates existing legacy DEV/UAT/PROD accounts with **no bootstrap/seeder process**, and identify any gap that would block legacy users.
- **Directive:** account setup stays **manual SQL only** (same as legacy JEX); the Spring app must work with data that already exists in the DB. No company/employee/user/admin creation through startup code.

## Executive Summary

The refactor **does not auto-create** any company, employee, user, or admin. The former `BootstrapRunner` was already removed, and **Flyway is disabled in every profile** (`ddl-auto: validate` only), so demo-account seed migrations never run against the live DB. All bootstrap/seeder classes were **inert skeletons** with no production invoker — **they have now been deleted** (see §2). Login already authenticates against legacy data: it reads `USER_INFM`, accepts the legacy SHA-256 password hash verbatim, and resolves the tenant via `USER_BUSI_CONN_INFM` + `BUSI_ADMN_INFM`.

**One critical issue remains open as a follow-up code change:** on first successful Spring login the password is transparently rewritten to BCrypt in `USER_INFM.USER_PW` (G1), and the Settings → Users feature writes BCrypt for new/changed passwords (G2). Against the shared live JEX DB this mutates live data and permanently breaks that user's *legacy JEX* login (JEX only understands SHA-256).

> **Decision (Sonny, 2026-06-10): COEXISTENCE — keep SHA-256.** The Spring app runs alongside the live JEX system against the same DB. The transparent BCrypt upgrade must be made **verify-only** (never re-encode), and Settings → Users must write a `hash()`-compatible SHA-256 value so legacy login keeps working.
>
> **✅ RESOLVED (2026-06-10) — G1 & G2 implemented.** `WecmsPasswordEncoder.encode()` now emits the legacy `public.hash()` SHA-256 value and `upgradeEncoding()` always returns `false`; the login upgrade-and-save block was deleted (verify-only). Because all write paths share the `PasswordEncoder` bean, this also covers `ChangePasswordService` (a third write path the original audit did not list). `matches()` stays hybrid (SHA-256 + BCrypt tolerated for pre-existing dev rows). No password migration. `compileJava` + `compileTestJava` green. CLAUDE.md updated. See the §6/§7 table below (G1/G2 rows annotated DONE).

## 1. Bootstrap / Seeder Inventory (as found)

All account-creation bootstrap code lived in `domain/auth/bootstrap/`:

| Class | Role | Auto-invoked? |
|---|---|---|
| `CompanyBootstrapService` | Orchestrated `BUSI_INFM → BUSI_ENV_SET_INFM → EMPL_INFM → USER_INFM → USER_BUSI_CONN_INFM → BUSI_ADMN_INFM` | No — no caller |
| `UserBootstrapService` | Wrote `USER_INFM` + `USER_BUSI_CONN_INFM` + `BUSI_ADMN_INFM` (first admin) | No — only via `CompanyBootstrapService` |
| `BusinessKeyGeneratorService` | `BIZ_KEY` via `wb_get_next_val` | No — only via `CompanyBootstrapService` |
| `EmployeeNumberGeneratorService` | `EMPL_NO` via `wb_get_next_val` | No — only via `CompanyBootstrapService` |
| `CompanyBootstrapCommand` / `FirstAdminCommand` / `BootstrapResult` | Input/output records | n/a |
| `DataSeeder` | `@Profile("local")` runner — replaced the `__BCRYPT_PENDING__` sentinel with BCrypt | Local profile only (dead against live DB) |
| `PasswordHashService` (`common/security/`) | BCrypt-only write facade | No — only via `UserBootstrapService` |

**Verification of inertness:**
- Only one startup runner existed (`DataSeeder`); no `BootstrapRunner` (referenced only in Javadoc as "removed").
- No production code called `bootstrap(...)`, `bootstrapIfAbsent(...)`, or `createFirstAdmin(...)` — only `TenantProperties` Javadoc + three test classes.
- Flyway disabled/removed in all profiles (`application.yml`, `-local/-test/-sit/-uat/-prod`) → seed migrations (V2/V5/V10/V73) do not run against the live DB.

**Separate, legitimate runtime write path (NOT a seeder, kept):** `UserManagementService` (Settings → Users) creates/updates `USER_INFM` + `USER_BUSI_CONN_INFM` + `BUSI_ADMN_INFM`, gated by `requireAdmin()`. This is the port of the legacy user-management screen — in scope as a product feature, but subject to the G2 password-compat fix below.

## 2. Removal (DONE 2026-06-10)

The entire bootstrap package was self-contained (nothing outside it depended on it except tests + one Javadoc reference). **Deleted:**

- `domain/auth/bootstrap/`: `CompanyBootstrapService`, `UserBootstrapService`, `BusinessKeyGeneratorService`, `EmployeeNumberGeneratorService`, `CompanyBootstrapCommand`, `FirstAdminCommand`, `BootstrapResult`, `DataSeeder`
- `common/security/PasswordHashService` (orphaned once `UserBootstrapService` was gone)
- Tests: `CompanyBootstrapServiceTest`, `UserBootstrapServiceTest`, `KeyGeneratorServicesTest`, `PasswordHashServiceTest`

**Kept:** `TenantEnvironmentSettingsBootstrapService` (copies `SYS_ENV_SET_INFM → BUSI_ENV_SET_INFM`; also used by the live Environment-Settings feature — not account creation). `TenantProperties` retained as dormant bound config.

**Build verified:** `./gradlew compileJava compileTestJava` passes after deletion.

> Follow-up housekeeping (optional): `TenantProperties.assertConsistent(...)` is now unused; the CLAUDE.md "Bootstrap services … retained but inert" bullets should be updated to "removed 2026-06-10."

## 3 & 4. Login Works With Existing Legacy Data — Both Types Verified

Login (`AuthService.login`) reads existing rows only; it creates nothing. Gate order: `USER_NOT_FOUND → DEL_YN → ACTV_YN → LOCK_YN → password → active links`.

- **Business Admin Login:** `USER_INFM` (credentials + flags) → `USER_BUSI_CONN_INFM` (active link, `DEL_YN='N'`) → `BUSI_INFM` (company name + `PRDT_TYPE`) → `BUSI_ADMN_INFM` (presence of `(BIZ_KEY, USER_ID)` row ⇒ `authGrCd="ADMIN"`). ✔
- **Normal User Login:** same path; absence of a `BUSI_ADMN_INFM` row ⇒ `authGrCd="USER"`. **`EMPL_INFM` is not dereferenced at login** — `USER_BUSI_CONN_INFM.EMPL_NO` is stored but not read, so a missing `EMPL_INFM` row does not block login. ✔

**Minimum data contract for an existing legacy user to log in:**
1. `USER_INFM`: `ACTV_YN='Y'`, `LOCK_YN='N'`, `DEL_YN='N'`, `USER_PW` = legacy SHA-256 base64. *(Live column defaults are `'N'` — manual inserts must set `ACTV_YN='Y'` or login returns `WBE1010`.)*
2. ≥ 1 `USER_BUSI_CONN_INFM` row with `DEL_YN='N'`.
3. A `BUSI_INFM` row for that `BIZ_KEY` (display name + product type).
4. Admins additionally: a `BUSI_ADMN_INFM` row.

## 5. Legacy Compatibility — Findings

### Authentication / password storage
- **Legacy hash confirmed on DEV:** `public.hash(txt) = encode(digest($1,'sha256'),'base64')` — unsalted SHA-256, base64, 44 chars. All 421 `USER_INFM.USER_PW` values in DEV are exactly 44 chars (100% SHA-256; zero BCrypt). `LegacyHashPasswordEncoder` replicates this byte-for-byte → legacy passwords verify correctly. ✔
- **Encoder is hybrid** (`WecmsPasswordEncoder`): reads BCrypt *or* legacy SHA-256; writes BCrypt. `USER_PW` is `VARCHAR(100)` on the live DB — BCrypt (60 chars) fits, no overflow. ✔
- `encrypt()/decrypt()` (AES, key `'wabooksvn'`) exist for PII fields but are **not** used for passwords; login does not touch them. (Flagged for later PII-display parity; out of scope here.)

### User-status rules
Legacy `USER_INFM` login-blocking columns are exactly `ACTV_YN`, `LOCK_YN`, `DEL_YN` — no other gate column, no failure-count column. The refactor checks all three in correct precedence. ✔ Exact parity.

### Business / employee / admin linkage
`USER_BUSI_CONN_INFM` carries no `AUTH_GR_CD`; authority is sourced from `BUSI_ADMN_INFM` presence. Tenant, product type, and authority all resolve from existing rows. ✔

## 6 / 7. Gaps & Recommendations

| # | Severity | Gap | Impact | Recommendation |
|---|---|---|---|---|
| **G1** | ✅ DONE | Transparent BCrypt upgrade wrote back to `USER_INFM.USER_PW` on first Spring login (`AuthService.login`) | Mutated live data; broke the user's legacy JEX login (JEX expects SHA-256) | **Resolved 2026-06-10:** upgrade block deleted — login is now verify-only, never writes USER_PW. |
| **G2** | ✅ DONE | Settings → Users (+ change-password) wrote BCrypt for new/changed passwords | New/edited users could not log into legacy JEX | **Resolved 2026-06-10:** `WecmsPasswordEncoder.encode()` now emits legacy `public.hash()` SHA-256, so all write paths (Settings → Users, change-password) stay JEX-compatible. |
| **G3** | 🟡 Medium | Post-auth gates `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS` not implemented | Refactor is more permissive than legacy (does not block legitimate users) | Port for parity before non-local deploy. |
| **G4** | 🟢 Low | 5-failure auto-lock counter not implemented (`USER_LOGIN_HIS` FAIL rows written but not counted) | No lockout | Implement before non-local deploy; not a legacy-user blocker. |
| **G5** | 🟢 Low | Password expiry (`PW_CHG_REQ_SEQ_NO`) mapped but unused | No forced rotation | Wire to a change-password flow before production. |
| **G6** | ℹ️ Info | `ACTV_YN` live column default is `'N'` | Manually-inserted user without explicit `ACTV_YN='Y'` fails `WBE1010` | Manual SQL setup must set `ACTV_YN='Y'` (legacy `setup_*` scripts already do). |

## Bottom line

Existing DEV/UAT/PROD users **can log in today** with no bootstrap process — the read path is fully legacy-compatible. The bootstrap/seeder package has been removed. The remaining work is the **G1/G2 password follow-up** dictated by the coexistence decision: make the login upgrade verify-only and make Settings → Users write SHA-256, so the live JEX system and the refactor can authenticate the same accounts against the same DB.

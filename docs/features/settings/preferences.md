# Preferences

> Canonical Settings-menu entry for **Settings → Preferences** (backend). **Confirmed against source
> 2026-06-01** — legacy `comm_0215_001`, refactor split across `domain/setting/preferences/`
> (company profile + logo) and `domain/setting/environmentsettings/` (feature toggles). Implemented.

## Overview
Preferences (left nav, between **Bank Account** and **Users**) carries the **company profile**
(logo, Vietnamese name, display name, Tax ID, representative e-mail, address) plus the system-wide
**feature toggles** *Use Purpose* and *Use VAS Account*. Slug: `preferences` (replaces legacy WSVC id
`comm_0215_001`).

### Confirmed source files
**JEX (read-only reference)** — `WECMS DEV/.../comm/`
- Views: `comm_0215_001_view.jsp`, `comm_0215_001_r002_view.jsp`
- Actions: `comm_0215_001_r001_act.jsp` (read), `_u001` (save profile), `_u002`/`_u003`/`_u004`
  (toggles), `_c001`/`_c005` (logo upload), `_d001` (logo clear)
- Env data layer: `IDO.BUSI_ENV_SET_INFM_*.xml`, entities `wabooks/entity/BusiEnvSetInfm.java` +
  `SysEnvSetInfm.java`, DAO `BusiEnvSetInfmDAO.java`

**Refactor (`wecms-api`)**
- Company profile + logo: `domain/setting/preferences/` — `PreferencesController`, `PreferencesService`,
  `PreferencesLogoResolver`, `PreferencesConfig`, DTOs `PreferencesResponse` / `PreferencesUpdateRequest`
  / `PreferencesAddress`
- Feature toggles: `domain/setting/environmentsettings/` — `EnvironmentSettingsController`,
  `EnvironmentSettingsService`, `TenantEnvironmentSettingsBootstrapService`, entities `BusiEnvSetInfm`
  + `SysEnvSetInfm`, `SysEnvSetInfmRepository`
- Migrations: `V69__sys_busi_env_set_infm_schema.sql`, `V70__busi_env_set_infm_init_existing_tenant.sql`,
  `V119__preferences_env_set_seed.sql`

- Legacy plan: [../../../docs/about-preferences-in-jaunty-goblet.md](../../../../docs/about-preferences-in-jaunty-goblet.md)
  *(note: that plan names the toggle table `ENV_SETN_INFM` — **incorrect**; see Database Tables)*
- Toggles consumed by: [purpose.md](purpose.md), [vas-account.md](vas-account.md)

## Related APIs
**Company profile** — base `/api/v1/settings/preferences` (JWT, snake_case wire):
- `GET /` — load profile (mirrors `comm_0215_001_r001`, `BUSI_INFM` half)
- `PUT /` — save profile (mirrors `comm_0215_001_u001`)
- `POST /logo` — logo upload (multipart, `@RequestPart("file")`)
- `DELETE /logo` — clear logo
- `GET /logo` — stream the logo

**Feature toggles** — base `/api/v1/settings/environment-settings`:
- `GET /{env_set_inf}` — read one toggle
- `PUT /{env_set_inf}` — upsert one toggle (e.g. *Use Purpose*, *Use VAS Account*)

## Database Tables
> ✅ **CONFIRMED.** The toggle table is **`BUSI_ENV_SET_INFM`**, not `ENV_SETN_INFM`. `ENV_SETN_INFM`
> appears in **zero** source files (Java/XML/SQL) — it was a documentation error. Bare `ENV_SET_INFM`
> never appears standalone either; every occurrence is `BUSI_ENV_SET_INFM` (50 refs) or
> `SYS_ENV_SET_INFM` (22 refs).

- **`BUSI_INFM`** — company profile, one row per tenant (PK `BIZ_KEY`). Logo lives in `BUSI_INFM.CMPN_LOGO`.
- **`BUSI_ENV_SET_INFM`** — per-tenant environment toggles (N rows per tenant, e.g. *Use Purpose*,
  *Use VAS Account*). Legacy `comm_0215_001` returned these as the `REC[]`.
- **`SYS_ENV_SET_INFM`** — system-level environment defaults (seeded; tenant rows initialised from it
  by V70 / the bootstrap service).
- **`AUDIT_LOGS_INFM`** — profile-change audit via `AuditLogService` (`REF_NO = "BUSI_INFM"`).

## Business Rules
- Dual-source read: one `BUSI_INFM` row (profile) + N `BUSI_ENV_SET_INFM` toggle rows.
- Profile save is a pure UPDATE on the single `BUSI_INFM` row scoped to `BIZ_KEY` (mirrors
  `comm_0215_001_u001_act.jsp:48-95`).
- Each toggle is a single-key upsert into `BUSI_ENV_SET_INFM`.
- Logo upload writes a server-generated filename to `BUSI_INFM.CMPN_LOGO`; upload/delete mutate only
  that one column. New tenants are initialised from `SYS_ENV_SET_INFM`.
- Audit `WSVC_ID` uses the slug `preferences`.

## Validation
- Tax ID / representative e-mail format-checked; required company fields non-blank.
- Logo file type/size constrained by `PreferencesLogoStorageProperties` / `PreferencesConfig`.

## Error Cases
- Missing `BUSI_INFM` row for the tenant → handled (logged warn; should resolve at login).
- Invalid logo file type → validation error.
- Standard envelope; `WBE_VALIDATION` / `WBE9999`.

## Notes
Turning *Use Purpose* / *Use VAS Account* off hides those Settings panels and their transaction-setup
fields. Web/UI: [../../../wecms-web/docs/settings/preferences.md](../../../../wecms-web/docs/features/settings/preferences.md).

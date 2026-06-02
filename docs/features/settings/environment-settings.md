# Settings > Environment Settings (Tenant Flag Store) — Refactor Feature Spec (Backend)

**Domain:** `setting` (generic, cross-feature — **not** a standalone Settings-menu screen)
**Refactor slug:** `environment-settings`
**Backend package:** `com.kosign.wecms.domain.setting.environmentsettings`
**Status:** **Working** — generic per-tenant Y/N + free-text flag store.
**Refactor route:** `/api/v1/settings/environment-settings/{env_set_inf}`
**Legacy reference:** `comm_0600_001_r001` (read) + `comm_0215_001_u001` (update); tables `BUSI_ENV_SET_INFM` / `SYS_ENV_SET_INFM`.

> Created 2026-06-01 (documentation audit round 2). Documents an **already-implemented** generic endpoint
> group that had no feature doc. It is shared infrastructure consumed by feature panels — not a menu
> screen of its own. No new scope, no source change.

## Overview

A generic per-tenant flag store. Any module can read or update a single environment-setting code; the
read returns `COALESCE(BUSI override, SYS default)`. Rows are pre-seeded per tenant (legacy
`wb_init_busi_data`; refactor `TenantEnvironmentSettingsBootstrapService`), so the endpoint only reads
or updates — it never inserts.

**First consumer:** the Firm Banking Settings panel — codes `00078` (Auto Transfer) / `00079`
(Auto Match) per legacy `comm_0220_001.ts`. The endpoints are intentionally generic so future modules
reuse the same routes without controller changes.

## Endpoints (`EnvironmentSettingsController`)

| Method | Path | Returns | Legacy |
|---|---|---|---|
| GET | `/api/v1/settings/environment-settings/{env_set_inf}` | `EnvironmentSettingResponse` (effective value, BUSI override over SYS default) | `comm_0600_001_r001` |
| PUT | `/api/v1/settings/environment-settings/{env_set_inf}` | `EnvironmentSettingResponse` (after update) | `comm_0215_001_u001` |

- `env_set_inf` — 5-char alphanumeric code (`^[A-Za-z0-9]{5}$`), validated by `@Pattern`; legacy seeds are all digits. Unknown-code check happens in the service via `SysEnvSetInfmRepository`.
- Update is **UPDATE-only** (row must already exist from bootstrap seed).
- Tenant scope rides on the JWT via `TenantContext`; route carries no `bizKey`. Wire is lowercase snake_case.

## Implementation

| Layer | File |
|---|---|
| Controller | `domain/setting/environmentsettings/controller/EnvironmentSettingsController.java` |
| Service | `domain/setting/environmentsettings/service/EnvironmentSettingsService.java` |
| Bootstrap seed | `domain/setting/environmentsettings/service/TenantEnvironmentSettingsBootstrapService.java` |

## Related

- First consumer: [firm-banking-settings.md](firm-banking-settings.md) (Auto Transfer / Auto Match toggles)
- Endpoint index: [../../references/api-spec/api-mapping.md](../../references/api-spec/api-mapping.md)

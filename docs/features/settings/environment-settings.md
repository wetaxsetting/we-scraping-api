# Settings > Environment Settings (Tenant Flag Store) — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> Environment Settings has **no module folder** — it is generic, cross-feature infrastructure,
> not a Settings-menu screen. The authoritative source of truth is the code:
> backend package `com.kosign.wecms.domain.setting.environmentsettings`. The toggles it backs are
> surfaced through the [preferences.md](preferences.md) and
> [firm-banking-settings.md](firm-banking-settings.md) panels — see those folders/docs for the
> UI side.

## Feature Overview

- **Purpose:** A generic per-tenant Y/N + free-text flag store. Any module reads or updates one
  environment-setting code; the read returns `COALESCE(BUSI override, SYS default)`.
- **Business role:** Shared backend for the settings toggles that the **Preferences** and **Firm
  Banking Settings** panels expose. It is **NOT** replaced by those panels — it is the endpoint
  the panels persist through. Rows are pre-seeded per tenant
  (`TenantEnvironmentSettingsBootstrapService`), so the endpoint only **reads or updates**, never
  inserts.

## Menu / Route Mapping

- **Menu / panel:** No menu entry of its own. Consumed by the Preferences and Firm Banking
  Settings panels (and any future module).
- **Menu permission ID (slug):** the controller is gated by
  `@menuAuthz.canAccessAny('settings-core-preference', 'settings-firm-banking-settings')`
  (verified 2026-06-19) — the single GET/PUT pair serves every code, so it cannot be split
  per-panel and gates on **either** parent entitlement (admin bypasses the per-user grant; a user
  holding neither is denied `WBE1003`). Both are product-invariant `settings-*` codes.
- **Wire base path:** `/api/v1/settings/environment-settings/{env_set_inf}` (verified in
  `EnvironmentSettingsController.java`). `env_set_inf` is a 5-char alphanumeric code
  (`^[A-Za-z0-9]{5}$`); legacy seeds are all digits.
- **Frontend mount / route:** none of its own. The Preferences panel
  (`components/settings/preferences/`) and Firm Banking Settings panel
  (`components/settings/firm-banking-settings/`) call this endpoint to read/persist their toggles.
- **Legacy JEX counterpart:** `comm_0600_001_r001` (read) + `comm_0215_001_u001` (update); tables
  `BUSI_ENV_SET_INFM` / `SYS_ENV_SET_INFM` (traceability only).

## Current Status

⚪ **Not a standalone Settings module** — but the endpoint is fully built and actively shared.

- ✅ **Implemented (backend):**
  - `GET /api/v1/settings/environment-settings/{env_set_inf}` — effective value, BUSI override over
    SYS default. `comm_0600_001_r001` parity.
  - `PUT /api/v1/settings/environment-settings/{env_set_inf}` — **UPDATE-only** (row must already
    exist from the bootstrap seed). `comm_0215_001_u001` parity.
  - Layers: `EnvironmentSettingsController` → `EnvironmentSettingsService` over
    `BUSI_ENV_SET_INFM` (override) + `SYS_ENV_SET_INFM` (default); per-tenant seed via
    `TenantEnvironmentSettingsBootstrapService`. Tenant scope rides the JWT (`TenantContext`);
    route carries no `bizKey`. Wire is lowercase snake_case. Backend unit tests present.
- ✅ **Surfaced via panels (relationship, not replacement):**
  - **Preferences** — Use Purpose code `00077`, Use VAS Account code `00074`
    (verified in `PreferencesService` / `PreferencesController`). The Preferences read assembles
    these toggles; their persistence rides through this Environment Settings endpoint pair.
  - **Firm Banking Settings** — Auto Transfer code `00078`, Auto Match code `00079`
    (per legacy `comm_0220_001.ts`). The Firm Banking Settings panel's automation toggles persist
    through this same endpoint.
- 🟡 **Frontend wiring:** the panels above own the UI; this pass did not separately re-verify each
  panel's end-to-end call to the Environment Settings endpoint. The backend endpoint itself is
  confirmed implemented and shared.

> **On the "was it replaced?" question:** No. Environment Settings is the **shared backend** for
> the Preferences and Firm Banking Settings toggles — the panels render the switches, but the
> values live in `BUSI_ENV_SET_INFM` and are read/written exclusively through this generic
> endpoint pair. Adding a future toggle reuses these routes with no controller change.

## Detailed Documentation

There is no `environment-settings/` folder. Authoritative references that **do** exist:

- Backend package: `com.kosign.wecms.domain.setting.environmentsettings`
  (`EnvironmentSettingsController` · `EnvironmentSettingsService` ·
  `TenantEnvironmentSettingsBootstrapService` · entities `BusiEnvSetInfm` / `SysEnvSetInfm`).
- First/second consumers: [preferences.md](preferences.md) (codes `00074` / `00077`) and
  [firm-banking-settings.md](firm-banking-settings.md) (codes `00078` / `00079`).
- Endpoint index: [../../references/api-spec/api-mapping.md](../../references/api-spec/api-mapping.md).

## Historical Design Notes (preserved)

Created 2026-06-01 (documentation audit round 2) to document an already-implemented generic
endpoint group that had no feature doc. Authorization gating was added 2026-06-19 (the
`@menuAuthz.canAccessAny(...)` pair above) once the second consumer (Preferences) landed alongside
the original Firm Banking Settings consumer.

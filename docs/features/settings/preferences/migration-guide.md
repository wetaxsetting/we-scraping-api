# Preferences — Migration Guide (JEX → Spring)

How each JEX layer became its Spring counterpart for the Preferences company-profile feature. Legacy
reference: live JEX `comm_0215_001`. The two preference toggles split out to the
[Environment Settings](../environment-settings.md) module.

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen | `comm_0215_001_view.jsp` (+ `comm_0215_001_r002_view.jsp`) + `comm_0215_001.ts` | `<PreferencesPanel>` (Settings modal panel — no route) |
| Profile read | `comm_0215_001_r001` (`BUSI_INFM` half) | `GET /preferences` → `PreferencesService.get` → `BusiInfmRepository.findById(bizKey)` |
| Profile save | `comm_0215_001_u001` (UPDATE `BUSI_INFM`) | `PUT /preferences` → `PreferencesService.update` |
| Logo upload | `comm_0215_001_c001` / `_c005` | `POST /preferences/logo` → `PreferencesService.uploadLogo` |
| Logo clear | `comm_0215_001_d001` | `DELETE /preferences/logo` → `PreferencesService.deleteLogo` |
| Logo serve | (legacy served via static/asset path) | `GET /preferences/logo` → `PreferencesService.openLogo` (JWT-safe streaming) |
| Use Purpose / Use VAS toggles | `comm_0215_001_u002` / `_u003` / `_u004` (`REC[]` over `BUSI_ENV_SET_INFM`) | **split out** → [Environment Settings](../environment-settings.md) `PUT /environment-settings/{00077\|00074}` |
| Entity | `BusiInfm` (JEX) | reuses the auth-domain `BusiInfm` (`@Table BUSI_INFM`) — **no new entity** |
| DAO | `BusiInfmDAO` | Spring Data `BusiInfmRepository` (auth domain) |
| Env data layer | `BusiEnvSetInfm` / `SysEnvSetInfm` + `BusiEnvSetInfmDAO` | the Environment Settings module's entities/repos — not this feature |

---

## Identifier translation (slug, not WSVC id)

Every refactor identifier surface carries the kebab slug **`preferences`** — REST URI, Java package
(`…domain.setting.preferences`), class names (`PreferencesController`, `PreferencesService`,
`PreferencesLogoResolver`), DTO records, the audit `WSVC_ID` (`preferences`), and the i18n keys
(`audit.preferences.*`). The menu permission code is **`settings-core-preference`**. Legacy WSVC ids
(`comm_0215_001`, …) appear only in JEX file:line citations and migration prose.

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| Profile + toggles | one screen, one `comm_0215_001` action set, profile (`BUSI_INFM`) + env toggles (`BUSI_ENV_SET_INFM` `REC[]`) saved together | **split**: profile = `preferences` module (`BUSI_INFM` UPDATE); toggles = generic Environment Settings module (per-code upsert) |
| Logo serve | static/asset path | dedicated JWT-protected streaming GET (`/preferences/logo`) so the bytes stay behind auth |
| Logo filename | legacy save name | opaque base64url SHA-256 over `bizKey + millis + 16 random bytes` (unforgeable, collision-free) |
| Logo storage layout | (flat) | per-tenant subtree `<root>/<biz_key>/preferences/<name>`, with read-fallback to the legacy flat root |
| Audit `WSVC_ID` | `comm_0215_001` | slug `preferences` |
| Authorization | menu visibility only | explicit `@menuAuthz.canAccess('settings-core-preference')` (F-3 server-side) |

---

## Dropped / dead legacy flows (not ported)

| Legacy flow | JEX artifact | Disposition |
|---|---|---|
| Use Purpose / Use VAS toggle save | `comm_0215_001_u002`/`_u003`/`_u004` | **Split out** (not dropped) — same behaviour, now on the generic Environment Settings endpoint (`00077`/`00074`). APPROVED DEVIATION. |
| Secondary read view | `comm_0215_001_r002_view.jsp` | Folded into the single read endpoint; no separate route. |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification.

---

## Schema migration (Flyway)

**None required.** `BUSI_INFM` and the `CMPN_LOGO` column already exist from the auth schema (V1 / V7
parity); every Preferences column is part of the legacy `BUSI_INFM` shape. The logo **storage root** is
config-only (`wecms.preferences.storage-root` / `WECMS_PREFERENCES_STORAGE_ROOT`) — no DB column, no
migration. The env-toggle tables (`BUSI_ENV_SET_INFM` / `SYS_ENV_SET_INFM`) ship with the
[Environment Settings](../environment-settings.md) module (V69/V70/V119), not this feature.

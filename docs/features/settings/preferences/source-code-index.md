# Preferences — Source Code Index

Legacy ↔ refactor file map for the Preferences company-profile feature. JEX paths are **read-only
reference**. Frontend mirror:
[wecms-web/docs/features/settings/preferences/component-index.md](../../../../../wecms-web/docs/features/settings/preferences/component-index.md).

---

## Refactor — backend (`wecms-api`)

| Layer | File |
|---|---|
| Controller | [controller/PreferencesController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/controller/PreferencesController.java) |
| Service | [service/PreferencesService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/service/PreferencesService.java) |
| Logo path resolver | [service/PreferencesLogoResolver.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/service/PreferencesLogoResolver.java) |
| Logo storage config props | [service/PreferencesLogoStorageProperties.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/service/PreferencesLogoStorageProperties.java) |
| Config wiring | [service/PreferencesConfig.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/service/PreferencesConfig.java) |
| DTO — response | [dto/PreferencesResponse.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/dto/PreferencesResponse.java) |
| DTO — update request | [dto/PreferencesUpdateRequest.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/dto/PreferencesUpdateRequest.java) |
| DTO — nested address | [dto/PreferencesAddress.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/dto/PreferencesAddress.java) |
| Entity (reused) | [domain/auth/entity/BusiInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiInfm.java) — `@Table(BUSI_INFM)`, PK `BIZ_KEY` |
| Repository (reused) | [domain/auth/repository/BusiInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiInfmRepository.java) |
| Audit writer (shared) | [common/auditlog/service/AuditLogService.java](../../../../src/main/java/com/kosign/wecms/common/auditlog/service/AuditLogService.java) (+ `AuditLogContext`) |
| Messages | [messages.properties](../../../../src/main/resources/messages.properties) — `WBE2080`–`WBE2087` |
| Tests | **none on disk** — no `src/test/.../preferences/`; SIT/UAT only (see [test-scenarios.md](test-scenarios.md)) |

**No `entity/` or `repository/` package** under `domain/setting/preferences/` — the feature reuses the
auth domain's `BusiInfm` / `BusiInfmRepository` (the single shared tenant table). No Flyway migration —
`BUSI_INFM` + `CMPN_LOGO` predate the feature; the logo storage root is config-only.

The two preference toggles route through the separate Environment Settings module — see
[environment-settings.md](../environment-settings.md) for its `EnvironmentSettingsController` /
`EnvironmentSettingsService` files.

---

## Refactor — frontend (`wecms-web`)

| Layer | File |
|---|---|
| Panel | [components/settings/preferences/PreferencesPanel.tsx](../../../../../wecms-web/components/settings/preferences/PreferencesPanel.tsx) (read/edit toggle; logo block; two env-toggle rows) |
| Panel types | [components/settings/preferences/types.ts](../../../../../wecms-web/components/settings/preferences/types.ts) |
| API client (profile + logo) | [lib/api/preferences.ts](../../../../../wecms-web/lib/api/preferences.ts) — `fetchPreferences` · `updatePreferences` · `uploadPreferencesLogo` · `deletePreferencesLogo` · `fetchPreferencesLogoBlob` |
| API client (toggles) | [lib/api/environmentSettings.ts](../../../../../wecms-web/lib/api/environmentSettings.ts) — `fetchEnvironmentSetting` · `updateEnvironmentSetting` (codes `00077` / `00074`) |
| Wire types | [lib/types/api.ts](../../../../../wecms-web/lib/types/api.ts) — `ApiPreferencesResponse` · `ApiPreferencesUpdateRequest` · `ApiPreferencesAddress` |
| Data hook | [lib/api/useApiData.ts](../../../../../wecms-web/lib/api/useApiData.ts) (`useApiData(fetchPreferences, [refreshKey])`) |
| Shared shell | [components/settings/SettingsModal.tsx](../../../../../wecms-web/components/settings/SettingsModal.tsx) + [SettingsNav.tsx](../../../../../wecms-web/components/settings/SettingsNav.tsx) + `useDialog()` |

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Path (under `WECMS DEV/WECMS/.../comm/`) |
|---|---|
| View JSP (main) | `comm_0215_001_view.jsp` |
| View JSP (secondary) | `comm_0215_001_r002_view.jsp` |
| TypeScript | `comm_0215_001.ts` |
| Profile read action | `comm_0215_001_r001_act.jsp` (`BUSI_INFM` half) |
| Profile save action | `comm_0215_001_u001_act.jsp` (UPDATE `BUSI_INFM`) |
| Toggle save actions | `comm_0215_001_u002_act.jsp` / `_u003` / `_u004` (env toggles — now Environment Settings module) |
| Logo upload | `comm_0215_001_c001_act.jsp` / `_c005_act.jsp` |
| Logo clear | `comm_0215_001_d001_act.jsp` |
| Env data layer | `IDO.BUSI_ENV_SET_INFM_*.xml`, entities `wabooks/entity/BusiEnvSetInfm.java` + `SysEnvSetInfm.java`, DAO `BusiEnvSetInfmDAO.java` |
| Company entity | `wabooks/entity/BusiInfm.java` (legacy) |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/settings/preferences/`
- Preserved flat spec: [../preferences.md](../preferences.md)
- Environment Settings (toggles): [../environment-settings.md](../environment-settings.md)
- Frontend feature folder: [wecms-web/docs/features/settings/preferences/](../../../../../wecms-web/docs/features/settings/preferences/README.md)

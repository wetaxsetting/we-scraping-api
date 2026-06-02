# Common > View Setting (Screen Options) — Refactor Feature Spec (Backend)

**Domain:** `common` / `setting` (global, screen-agnostic)
**Refactor screen ID / slug:** `view-setting`
**Backend package:** `com.kosign.wecms.domain.setting` (`ScreenOptionController` / `ScreenOptionService`)
**Status:** **Working** — endpoints implemented; first wired screen `banking-account-inquiry-vnd`.
**JEX service IDs:** `comm_0108_001_r001_osmc` (load), `comm_0108_001_u002_osmc` (save), `comm_0108_001_u001_osmc` (reset), `SYS_SCR_OPTION_WECMS_R002` (areas).
**JEX functional reference (read-only):** [`../../../../docs/jex/features/view-setting.md`](../../../../docs/jex/features/view-setting.md).

> Created 2026-06-01 to resolve **OQ-DOC-2**. This doc is the canonical feature spine; the full
> behavioural design lives in the JEX reference above and the implementation rules in
> `wecms-api/CLAUDE.md` §"Screen Option (View Setting)". It documents an **already-implemented**
> global feature — no new scope is introduced.

## Overview

View Setting (the toolbar "list-view" popover, Figma 100:13382 / legacy `comm_0108_001`) lets a user
toggle column visibility, reorder columns, and reset to system defaults on any list page. It is a
**global feature**: one shared controller, one service, one DTO set, two tables — keyed only by
`WEB_SVC_ID` + `DYM_AREA_ID`. Adopting a new page is a Flyway **seed-only** change; no controller,
service, or DTO change. "Screen Options" is the REST surface name (`/api/v1/screen-options/...`); it
is the same feature.

Per memory `feedback_view_setting_global`, every list/table screen consumes the same shared component +
hook + REST surface + per-page registry — never a per-page fork.

## Endpoints

| Method | Path | Returns | JEX counterpart |
|---|---|---|---|
| GET | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (merged sys + user; lazy-init on first call) | `comm_0108_001_r001_osmc` |
| GET | `/api/v1/screen-options/{webSvcId}/areas` | `ScreenAreaListResponse` (distinct areas + counts) | `SYS_SCR_OPTION_WECMS_R002` |
| PUT | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (refreshed) | `comm_0108_001_u002_osmc` |
| DELETE | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionListResponse` (re-init from sys) | `comm_0108_001_u001_osmc` |

All four are JWT-protected. Bad path input → `WBE_VALIDATION`; unknown `screenId` (regex passes but
no sys row) → `WBE2001`.

## Behaviour (merge / save / reset)

- **Load** — `SELECT` sys rows + user override rows, overlay user `(DISP_ORD, USE_YN)` on each sys
  row, `FIX_YN='Y'` forces `USE_YN='Y'`, sort by effective `DISP_ORD`. Lazy-INSERT copies sys → user
  on first call per `(BIZ_KEY, USER_ID, WEB_SVC_ID, DYM_AREA_ID)` scope.
- **Save** — delete-then-insert the user scope in one transaction; full merged column list in the
  payload preserves order; unknown FIELD_IDs silently dropped; `FIX_YN='Y'` rows persist `USE_YN='Y'`
  regardless of payload.
- **Reset** — delete user rows + immediately re-init from sys in the same transaction; response is the
  pure sys defaults.

## Identifier conventions (locked)

- **`WEB_SVC_ID`** — semantic route-based kebab-case `<module>-<page>[-<type>]`
  (e.g. `banking-account-inquiry-vnd`). Backend `@Pattern` requires ≥2 kebab segments and rejects
  legacy JEX ids (`fnsh_0001_001` — underscores not allowed). Single source of truth:
  [`wecms-web/lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts).
- **`DYM_AREA_ID`** — required in every call/row; single-area pages use `default`.
- **`FIELD_ID`** — uppercase, stable across releases (a public contract; renaming orphans user overrides).
- **Special FIELD_IDs** — `CHECKBOX` (`DISP_ORD=0`) and `ETC` (`DISP_ORD=99`) are admin-controlled:
  hidden in the popover, locked server-side via `ScreenOptionService.SPECIAL_FIELD_IDS`.

## Data model

| Table | Role | PK |
|---|---|---|
| `SYS_SCR_OPTION_WECMS` | System defaults per screen | `WEB_SVC_ID + DYM_AREA_ID + FIELD_ID` |
| `USER_SCR_OPTION_WECMS` | Per-user overrides | `BIZ_KEY + USER_ID + WEB_SVC_ID + DYM_AREA_ID + FIELD_ID` |

Schema landed in V30; semantic-id cutover + `DYM_AREA_ID NOT NULL` in V32; CHECKBOX/SIZE/parity in
V33–V42 (per-screen seeds live in the owning feature's migration). The `SIZE` column is schema-aligned
and value-seeded (Figma WB365 widths) but **not yet consumed end-to-end** — see
[`project_view_setting_size_deferred`] memory and `wecms-api/CLAUDE.md` §"View Setting SIZE feature".

## Audit logging

Writes use the **slug** `view-setting` for `AUDIT_LOGS_INFM.WSVC_ID` (never the legacy `comm_0108_001`),
per the workspace CLAUDE.md identifier rule.

## Adopting a new screen (checklist)

1. Add the page's column set to `SYS_SCR_OPTION_WECMS` in the **same Flyway migration** that ships the page.
2. Register the `screenId` in [`wecms-web/lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts).
3. Seed `SORT_YN='Y'` only for columns that have a backing sort whitelist entry (see CLAUDE.md §"Common table sort").
4. No controller / service / DTO change — the endpoints already accept any `(WEB_SVC_ID, DYM_AREA_ID)`.

**Pages currently configured:** `banking-account-inquiry-vnd`, `contacts`, `firm-banking-history`,
`erp-transaction-history` (see `wecms-api/CLAUDE.md` for the authoritative list).

## Related

- Frontend spec: [`../../../../wecms-web/docs/features/common/view-setting.md`](../../../../wecms-web/docs/features/common/view-setting.md)
- Frontend architecture: [`../../../../wecms-web/docs/architecture/view-setting/README.md`](../../../../wecms-web/docs/architecture/view-setting/README.md)
- Endpoint index: [`../../references/api-spec/api-mapping.md`](../../references/api-spec/api-mapping.md) · JEX→Spring: [`../../references/migration/service-mapping.md`](../../references/migration/service-mapping.md)
- JEX functional reference: [`../../../../docs/jex/features/view-setting.md`](../../../../docs/jex/features/view-setting.md)

# View Setting — Functional Specification (Backend)

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

## Capabilities

| # | Capability | Notes |
|---|---|---|
| 1 | Load merged column config for a screen + area | Sys defaults overlaid with per-user overrides; lazy-init on first call |
| 2 | Save per-user column visibility + order | Delete-then-insert the user scope; full merged list in payload |
| 3 | Reset to system defaults | Delete user rows, re-init from sys in the same transaction |
| 4 | List configured areas + per-area column count | Drives a tabbed popover on multi-area pages |

## Merge rules (`buildMergedResponse`)

For each sys row, the response item is built field-by-field:

| Wire field | Source when user override exists | Source when no override |
|---|---|---|
| `field_id` | sys | sys |
| `field_nm` | sys | sys |
| `disp_ord` | **user** | sys |
| `disp_yn` | sys (always) | sys |
| `use_yn` | **user** | sys |
| `size` | sys (always) | sys |
| `sort_yn` | sys (always) | sys |
| `html_cont` | sys (always) | sys |

Only `DISP_ORD` and `USE_YN` are user-owned. Everything else is system-controlled even after a save.
Final list is sorted by effective `DISP_ORD` ascending (null → `Integer.MAX_VALUE`, sorts last).

## Special-field handling (`SPECIAL_FIELD_IDS = {CHECKBOX, ETC}`)

`ScreenOptionService.isSpecialFieldId(fieldId)` is the authoritative server-side gate.
On Save, a special FIELD_ID always persists with:

- `USE_YN = 'Y'` (cannot be hidden), and
- `DISP_ORD` taken **verbatim from the sys seed** (cannot be reordered),

regardless of the incoming payload — a stale/buggy frontend cannot relax the rule. `CHECKBOX` is the
row-selection column (sys `DISP_ORD=0`); `ETC` is the optional trailing action column (sys
`DISP_ORD=99`). For non-special columns Save trusts the frontend's `DISP_ORD` because the popover
renumbers densely from 1 after every reorder.

## Column-locking (FIX_YN removed)

`FIX_YN` was **removed** from the entity / DTO / service / wire on 2026-06-11. The live JEX
`SYS_SCR_OPTION_WECMS` carries no such column, so mapping it would fail `ddl-auto=validate`.
Column-locking (a column the user cannot hide/reorder, e.g. Account Inquiry's `BANK`) is now a
**frontend registry** concern (`lockedFieldIds` passed to `<ViewSettingPopover>`), not a DB flag.
The structural `CHECKBOX`/`ETC` columns remain locked server-side via `SPECIAL_FIELD_IDS`.

## Validation

- `webSvcId` — `@Pattern("^[a-z][a-z0-9]*(-[a-z0-9]+)+$")` — ≥2 kebab segments; rejects legacy
  underscore ids (`fnsh_0001_001`). Failure → `WBE_VALIDATION`.
- `dymAreaId` — `@Pattern("^[a-z][a-z0-9]*(-[a-z0-9]+)*$")` — single segment allowed so `default`
  passes. Failure → `WBE_VALIDATION`.
- `SaveScreenOptionRequest.items` — `@NotNull @NotEmpty`. Empty list → `WBE_VALIDATION`.

## Edge cases

- **Unknown screenId** (regex passes, no sys row) → `WBE2001` on load / save / reset / areas.
- **Unauthenticated identity** (bizKey/userId null) — load returns pure sys defaults, skips lazy-init;
  reset returns sys defaults without writing user rows. No exception.
- **Unknown FIELD_IDs in a Save payload** — silently dropped (frontend bundle drift); the GET reconciles.
- **`USE_YN='N'` rows in payload** — preserved (with `USE_YN='N'`), not omitted, so column order survives.
- **`DYM_AREA_ID`** — required in every call/row; single-area pages use `default`. Multi-area pages
  disambiguate repeated labels via the area, not via suffixed FIELD_IDs (note: FIELD_ID must still be
  unique across areas of one `WEB_SVC_ID` because it is part of the sys PK — see database-mapping.md).
- **SIZE** — schema-aligned + seeded (Figma WB365 widths) but not consumed end-to-end (deferred).

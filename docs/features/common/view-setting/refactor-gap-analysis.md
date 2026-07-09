# View Setting — Refactor Gap Analysis (vs. JEX)

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

Vocabulary: **MATCH** (refactor reproduces legacy) · **APPROVED DEVIATION** (intentional, signed off)
· **DEAD LEGACY** (legacy construct deliberately not ported) · **PARTIAL** (started, not complete) ·
**MISSING** (legacy behaviour with no refactor coverage).

## Parity matrix

| # | Aspect | Verdict | Detail |
|---|---|---|---|
| 1 | Load — merge sys + user, ordered by DISP_ORD | MATCH | `ScreenOptionService.load` + `buildMergedResponse` |
| 2 | Load — lazy-INSERT sys→user on first call per scope | MATCH | `initUserRowsFromSys`; mirrors `comm_0108_001_r001` |
| 3 | Save — delete-then-insert user scope, atomic | MATCH | `deleteAllForScope` + `saveAll`; mirrors `_u002` |
| 4 | Reset — delete user rows + re-init from sys | MATCH | `reset()` re-inits in same tx; mirrors `_u001` + `r001` |
| 5 | Areas — distinct areas + column count | MATCH | `listAreas` / `countColumnsByDymAreaId`; `SYS_SCR_OPTION_WECMS_R002` |
| 6 | CHECKBOX / ETC special columns locked server-side | MATCH | `SPECIAL_FIELD_IDS`; `USE_YN='Y'` + sys `DISP_ORD` pinned |
| 7 | Popover `DISP_YN='N'` hidden | MATCH | sys `DISP_YN` flows to wire; FE filters; V42 sets `VAS_ACCOUNT='N'` |
| 8 | `WEB_SVC_ID` = legacy page id (`fnsh_0001_001`) | APPROVED DEVIATION | replaced by semantic slug `banking-account-inquiry-vnd`; legacy id wiped (V32) |
| 9 | `FIX_YN` DB column for column-locking | APPROVED DEVIATION | removed 2026-06-11; locking moved to FE `lockedFieldIds`; sys stays 10-column |
| 10 | Audit `WSVC_ID` = `comm_0108_001` | APPROVED DEVIATION | refactor writes slug `view-setting` (and writes no audit rows today) |
| 11 | `SIZE` (column width) end-to-end consumption | PARTIAL | schema-aligned + Figma-WB365-seeded + on the wire, but `<col width>` not driven from it for all paths (deferred) |
| 12 | `HTML_CONT` pre-rendered header HTML | PARTIAL / unused | mapped + on the wire, but NULL across DEV; no consumer renders it yet |
| 13 | `DYM_AREA_ID` in the composite `@IdClass` | PARTIAL ⚠️ | column mapped NN, but `@IdClass` omits it (works only for single-area screens) — see database-mapping.md |
| 14 | Legacy `COMMON_HEAD` / `REC` envelope | DEAD LEGACY | replaced by `{ status, data }`; never ported |
| 15 | Legacy numeric area names (`table1`/`table2`) | DEAD LEGACY | refactor uses kebab area names; single-area pages use `default` |
| 16 | Multi-area tabbed popover (real consumer) | MISSING (no consumer yet) | `listAreas` endpoint exists, but every seeded screen is single-area (`default`); not exercised end-to-end |

## Notable open items

- **#13 `@IdClass` vs DDL PK** — the strongest correctness risk. Single-area screens are safe; a
  multi-area screen reusing a FIELD_ID across areas would collide under JPA. Confirm the physical PK
  and add `dymAreaId` to the `@IdClass` before the first multi-area page ships. `⚠️ UNVERIFIED`
  against live DDL.
- **#11 SIZE** — tracked in `wecms-api/CLAUDE.md` §"View Setting SIZE feature" + memory
  `project_view_setting_size_deferred`. Lands with the first real admin-width consumer.
- **#16 areas** — the aggregator is implemented and unit-testable, but no shipped page renders a
  tabbed multi-area popover, so the path has no SIT coverage with real multi-area data.

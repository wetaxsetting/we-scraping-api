# View Setting — Migration Guide (JEX → Refactor)

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

## Legacy → refactor mapping

| Legacy JEX WSVC | Refactor endpoint | Refactor method |
|---|---|---|
| `comm_0108_001_r001_osmc` (load, lazy-INSERT) | GET `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionService.load` |
| `comm_0108_001_u002_osmc` (save) | PUT `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionService.save` |
| `comm_0108_001_u001_osmc` (reset) | DELETE `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `ScreenOptionService.reset` |
| `SYS_SCR_OPTION_WECMS_R002` (areas) | GET `/api/v1/screen-options/{webSvcId}/areas` | `ScreenOptionService.listAreas` |

Tables are unchanged from legacy (`SYS_SCR_OPTION_WECMS`, `USER_SCR_OPTION_WECMS`). The refactor
preserves the legacy lazy-INSERT-on-read behaviour and the delete-then-insert save (see
[business-flow.md](business-flow.md) for the per-operation detail). JEX functional reference:
[`../../../../../docs/jex/features/view-setting.md`](../../../../../docs/jex/features/view-setting.md).

## What changed

### 1. Semantic slug replaces legacy `WEB_SVC_ID`

The refactor **fully replaces** legacy JEX page ids with semantic kebab-case slugs
(`<module>-<page>[-<type>]`). `fnsh_0001_001` → `banking-account-inquiry-vnd`. No mapping layer, no
aliases — the legacy code value exists nowhere on the refactor side. The V32 cutover wiped the V30
`fnsh_0001_001` seed and reseeded under the slug, then set `DYM_AREA_ID NOT NULL`. The slug roster
is owned by `wecms-web/lib/viewSettingScreens.ts`. Backend `@Pattern` rejects underscore ids so a
stale frontend cannot orphan rows under a legacy namespace.

### 2. `FIX_YN` removed (2026-06-11)

Legacy/early-refactor carried a `FIX_YN` column for column-locking. It was **removed** from the
entity / DTO / service / wire because the live JEX `SYS_SCR_OPTION_WECMS` has no such column and
`ddl-auto=validate` would fail. Column-locking moved to the frontend registry (`lockedFieldIds`).
`SPECIAL_FIELD_IDS = {CHECKBOX, ETC}` remain locked server-side, independent of any DB column. Do not
re-add `FIX_YN` or any column beyond the legacy 10 on the sys table.

### 3. Audit slug

Any audit write for this feature uses `AUDIT_LOGS_INFM.WSVC_ID = "view-setting"` (the slug), never
the legacy `comm_0108_001`, per the workspace identifier rule. (View Setting itself writes no audit
rows today; the slug is reserved for consistency.)

### 4. Envelope + naming

Legacy `COMMON_HEAD`/`REC` envelope → `{ status, data }`. Wire fields are lowercase snake_case via
the global Jackson strategy. No per-field `@JsonProperty` on the View Setting DTOs.

## JEX source-of-truth citations

- Load lazy-INSERT behaviour — `docs/jex/features/view-setting.md §1.6`.
- Reset delete-then-reinit — `docs/jex/features/view-setting.md §1.7`.
- Areas read — legacy `IDO.SYS_SCR_OPTION_WECMS_R002.xml`.
- Popover `DISP_YN='Y'` filter — legacy `IDO.USER_SCR_OPTION_WECMS_R002.xml`
  (`WHERE ... AND USR.DISP_YN = 'Y'`), mirrored by V42 (`VAS_ACCOUNT.DISP_YN='N'`).
- Screen-id convention + cutover risk — `docs/jex/features/view-setting.md §1.1`, §1.5, §5 risk #6.

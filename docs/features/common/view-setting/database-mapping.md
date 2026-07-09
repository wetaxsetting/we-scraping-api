# View Setting — Database Mapping (Backend)

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

## Tables

| Table | Role | PK (DB) |
|---|---|---|
| `SYS_SCR_OPTION_WECMS` | System defaults per screen/area/column | `WEB_SVC_ID + DYM_AREA_ID + FIELD_ID` |
| `USER_SCR_OPTION_WECMS` | Per-user overrides | `BIZ_KEY + USER_ID + WEB_SVC_ID + DYM_AREA_ID + FIELD_ID` |

Both in the PROD Active Table List (and the legacy-table inventory). Per workspace rules these are
copied column-for-column from legacy JEX DDL; `ddl-auto=validate` runs against the unmodified table.

## `SysScrOptionWecms` (entity) — field ↔ column

| Java field | Column | Type / mapping | Notes |
|---|---|---|---|
| `webSvcId` | `WEB_SVC_ID` | VARCHAR(50) NN, `@Id` | |
| `fieldId` | `FIELD_ID` | VARCHAR(100) NN, `@Id` | |
| `fieldNm` | `FIELD_NM` | VARCHAR(100) NN | |
| `dispOrd` | `DISP_ORD` | `Integer`, `@JdbcTypeCode(NUMERIC)` precision 10 scale 0, NN | JEX `NUMERIC(10,0)` (2026-06-09 parity) |
| `dispYn` | `DISP_YN` | VARCHAR(1) NN | |
| `useYn` | `USE_YN` | VARCHAR(1) NN | |
| `size` | `SIZE` | VARCHAR(10) NN | seeded but not consumed e2e |
| `dymAreaId` | `DYM_AREA_ID` | VARCHAR(50) NN | |
| `sortYn` | `SORT_YN` | `String`, `@JdbcTypeCode(CHAR)` length 1 NN | DEV is `bpchar(1)` (V38) |
| `htmlCont` | `HTML_CONT` | `text` | NULL across DEV today |

PK class: `SysScrOptionWecmsId(webSvcId, fieldId)` via `@IdClass`. **Note:** `@Table`-level the entity
maps `DYM_AREA_ID` and declares it part of the natural key in prose, but the `@IdClass`
`SysScrOptionWecmsId` holds only `webSvcId + fieldId` — see the ⚠️ note below. The entity does **not**
extend `BaseEntity` (sys table has no audit columns) and is read-only at runtime.

## `UserScrOptionWecms` (entity) — field ↔ column

| Java field | Column | Type / mapping | Notes |
|---|---|---|---|
| `bizKey` | `BIZ_KEY` | VARCHAR(20) NN, `@Id` | |
| `userId` | `USER_ID` | VARCHAR(100) NN, `@Id` | |
| `webSvcId` | `WEB_SVC_ID` | VARCHAR(50) NN, `@Id` | |
| `fieldId` | `FIELD_ID` | VARCHAR(100) NN, `@Id` | |
| `fieldNm` | `FIELD_NM` | VARCHAR(100) NN | |
| `dispOrd` | `DISP_ORD` | `Integer`, `@JdbcTypeCode(NUMERIC)` precision 10 scale 0, NN | |
| `dispYn` | `DISP_YN` | VARCHAR(1) NN | |
| `useYn` | `USE_YN` | VARCHAR(1) NN | |
| `size` | `SIZE` | VARCHAR(10), **nullable** | deferred NOT-NULL flip |
| `dymAreaId` | `DYM_AREA_ID` | VARCHAR(50) NN | |

Extends `BaseEntity` → inherits `REG_ID/REG_DTM/MOD_ID/MOD_DTM` (populated via `@PrePersist`).
PK class: `UserScrOptionWecmsId(bizKey, userId, webSvcId, fieldId)`.

> ⚠️ **`@IdClass` does not include `DYM_AREA_ID`.** Both `SysScrOptionWecmsId` and
> `UserScrOptionWecmsId` (verified 2026-06-23) omit `dymAreaId` from the composite key, even though
> the column is mapped NOT NULL and CLAUDE.md / the legacy DDL describe the PK as including it. This
> works at runtime because every seeded screen uses a single area (`default`) per `WEB_SVC_ID`, so
> `(WEB_SVC_ID, FIELD_ID)` is already unique. A genuine multi-area screen that reused a FIELD_ID
> across two areas would collide under JPA. Flag for the team — the entity's `@IdClass` may need
> `dymAreaId` added (matching the DDL) before a multi-area page ships. `⚠️ UNVERIFIED` whether the
> physical DEV/legacy PK actually includes `DYM_AREA_ID` — confirm against live DDL before changing.

## Flyway version history (View Setting columns)

| Version | Change |
|---|---|
| V30 | Base schema: both tables + seed for legacy `fnsh_0001_001` (6 columns) |
| V32 | Semantic-id cutover — wipe `fnsh_0001_001`, reseed `banking-account-inquiry-vnd`; `DYM_AREA_ID SET NOT NULL` on both |
| V33 | Seed `CHECKBOX` special row (DISP_ORD=0) for `banking-account-inquiry-vnd` |
| V34 | SIZE `INTEGER→VARCHAR(10)`, `DISP_YN` NN, add `SORT_YN CHAR(1)` + `HTML_CONT TEXT` on sys; backfill SIZE (Figma WB365) |
| V35 | Schema-only DEV parity: `field_nm`→VARCHAR(100) NN; `disp_ord`→NUMERIC(10,0); `size` NN on sys. Java adds `size`/`sortYn`/`htmlCont`/`dispYn` |
| V36 | Revert `disp_ord`→INTEGER (Hibernate validate hotfix) — later re-aligned to NUMERIC via `@JdbcTypeCode` (2026-06-09) |
| V37 | `sort_yn` CHAR→VARCHAR (wrong direction) |
| V38 | Revert `sort_yn`→CHAR(1); entity uses `@JdbcTypeCode(SqlTypes.CHAR)` |
| V39 | Add lowercase `size` column (Hibernate 6 lowercases unquoted identifier) |
| V40 | Drop V39 dup, rename legacy `"SIZE"`→`size`, widen to VARCHAR(20) |
| V41 | `VAS_ACCOUNT.SORT_YN='N'` (no `AccountSortField` constant) |
| V42 | `VAS_ACCOUNT.DISP_YN='N'` (hide from popover, mirrors legacy IDO filter) |
| (per-feature) | V153 firm-banking-history, V154 erp-transaction-history, V88/V187 contacts — each seeds its own column set |

> SIZE note: schema-aligned + value-seeded (Figma WB365 widths) but **not consumed end-to-end** —
> the wire field exists, the frontend does not yet drive `<col width>` from it for all paths
> (memory `project_view_setting_size_deferred`; CLAUDE.md §"View Setting SIZE feature").

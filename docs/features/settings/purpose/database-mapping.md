# Purpose — Database Mapping (Backend)

Authoritative DB design lives at
[references/database/settings/purpose-settings-schema.md](../../../references/database/settings/purpose-settings-schema.md);
this file is the feature-folder view + the full column → entity → DTO → UI mapping. Legacy DB
reference: [docs/jex/features/purpose/database-mapping.md](../../../../../docs/jex/features/purpose/database-mapping.md).

---

## 1. Core table — `BUSI_USAG_SET_INFM`

Entity: [BusiUsagSet.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/entity/BusiUsagSet.java)
(`@Table("BUSI_USAG_SET_INFM")`, `@IdClass(BusiUsagSetId.class)`). 15 columns, V80 migration; PK
verified on DEV 2026-05-12 via the `postgres` MCP. The entity does **not** extend `BaseEntity` — the
legacy table has only `MOD_*` audit columns (no `REG_ID`/`REG_DTM`), so the service writes
`mod_id`/`mod_dtm` explicitly.

| # | DB column | Type (DEV parity) | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | Tenant key; first predicate of every query |
| 2 | `USE_USAG_CD` | VARCHAR(20) | `useUsagCd` | ✅ | System code; PK-gen 10-digit zero-padded |
| 3 | `USE_YN` | VARCHAR(1) | `useYn` | | `'Y'` active / `'N'` soft-deleted |
| 4 | `DISP_YN` | VARCHAR(1) | `dispYn` | | dropdown-visibility toggle |
| 5 | `DISP_ORD` | NUMERIC | `dispOrd` (`@JdbcTypeCode(NUMERIC)`, Java `Integer`) | | per-tenant sort order |
| 6 | `MOD_ID` | VARCHAR(100) | `modId` | | last modifier login id |
| 7 | `MOD_DTM` | VARCHAR(14) | `modDtm` | | `yyyyMMddHHmmss` UTC |
| 8 | `USAG_GRP_CD` | VARCHAR(3) | `usagGrpCd` | | legacy group bucket; **always NULL** in refactor scope; never written |
| 9 | `REPT_DV` | VARCHAR(2) | `reptDv` | | legacy report division; not consumed |
| 10 | `REPT_GRP` | VARCHAR(2) | `reptGrp` | | legacy report group; not consumed |
| 11 | `USAG_TYPE` | CHAR(2) | `usagType` (`@JdbcTypeCode(CHAR)`) | | legacy usage type; not consumed |
| 12 | `HAND_USE_USAG_CD` | VARCHAR(20) | `handUseUsagCd` | | user-visible code; required at app layer |
| 13 | `PARENT_USE_USAG_CD` | VARCHAR(20) | `parentUseUsagCd` | | self-ref to level-1; NULL = level-1 |
| 14 | `USE_USAG_NM` | VARCHAR(255) | `useUsagNm` | | display name; required at app layer |
| 15 | `USE_USAG_CONT` | VARCHAR(500) | `useUsagCont` | | description; nullable |

Columns 8–11 stay mapped for **schema parity** with DEV (Legacy Schema Parity Rule) but the refactor service never writes them.

Helper index (V80, beyond DEV): `busi_usag_set_infm_parent_ix` on `(biz_key, parent_use_usag_cd) WHERE parent_use_usag_cd IS NOT NULL AND use_yn='Y'` — powers the recursive child / in-use lookups.

---

## 2. In-use view — `v_purpose_in_use` (V81)

Read-only projection driving both `used_in_transactions` on the wire and the recursive delete guard.
Entity: [PurposeInUseRow.java](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/entity/PurposeInUseRow.java) (`@Immutable`, 2 columns `biz_key`, `use_usag_cd`).

```sql
CREATE OR REPLACE VIEW v_purpose_in_use AS
SELECT DISTINCT biz_key, use_usag_cd FROM (
  -- (a) another active purpose lists this row as its parent
  SELECT biz_key, parent_use_usag_cd AS use_usag_cd
  FROM busi_usag_set_infm
  WHERE parent_use_usag_cd IS NOT NULL AND use_yn = 'Y'
  -- (b) consumer-table branches (payment / transfer / expense) added by UNION ALL
  --     as those entities port into the refactor
) t;
```

Today branch (a) is live (parent-child references). Branch (b) is the documented extension point for
real transaction consumers (`PYMN_*` / `TRNS_*` / `EXPS_*`) — see
[refactor-gap-analysis.md](refactor-gap-analysis.md) (OQ-5 follow-up).

---

## 3. PK generation — `wb_get_next_val` branch (V82)

`use_usag_cd` is allocated by the ported legacy function (documented exception per OQ-3), called via
`PurposeSettingsRepository.nextUseUsagCd(bizKey)`:

```sql
SELECT wb_get_next_val('busi_usag_set_infm', 'use_usag_cd', :bizKey, '');
-- branch body:
when lower($1)='busi_usag_set_infm' and lower($2)='use_usag_cd'
     and coalesce(trim($3),'') <> '' and coalesce(trim($4),'') = '' then
  newSeqNo := ( select coalesce(lpad((max(use_usag_cd)::numeric + 1)::varchar, 10, '0'), '0000000001')
                from busi_usag_set_infm
                where biz_key = $3 and use_usag_cd ~ '^0[0-9]{9}$' );
```

Range `0000000001..0999999999`; the leading-`0` regex avoids collision with legacy group-prefixed
codes (`1xxxxxxxxx`, `3xxxxxxxxx`, …). No advisory lock — the service wraps the call + INSERT in one
`@Transactional` and retries once on `DataIntegrityViolationException`. `disp_ord` is computed in
Java (`findMaxDispOrd + 1`), not via the function.

---

## 4. Legacy → Refactor → DTO → UI mapping

| Legacy column | Entity field | List/Detail DTO field | Create/Update DTO field | UI label |
|---|---|---|---|---|
| `USE_USAG_CD` | `useUsagCd` | `use_usag_cd` | (path key on update) | — (opaque system code) |
| `HAND_USE_USAG_CD` | `handUseUsagCd` | `hand_use_usag_cd` | `hand_use_usag_cd` | Purpose Code |
| `USE_USAG_NM` | `useUsagNm` | `use_usag_nm` | `use_usag_nm` | Purpose Name |
| `USE_USAG_CONT` | `useUsagCont` | `use_usag_cont` | `use_usag_cont` | Description |
| `PARENT_USE_USAG_CD` | `parentUseUsagCd` | `parent_use_usag_cd` | `parent_use_usag_cd` | Parent Purpose |
| `DISP_ORD` | `dispOrd` | `disp_ord` | (server-derived) | (display order / drag-sort) |
| `DISP_YN` | `dispYn` | `disp_yn` | `disp_yn` | Status / Use flag (toggle) |
| `USE_YN` | `useYn` | (filter only; not on wire) | (server-managed) | — (soft-delete state) |
| `MOD_ID` | `modId` | `mod_id` | (server) | (audit display) |
| `MOD_DTM` | `modDtm` | `mod_dtm` | (server) | (audit display) |
| — (derived) | — | `used_in_transactions` | — | (gates the delete affordance) |

The prompt's requested column list maps as: **Purpose Code** → `HAND_USE_USAG_CD`; **Purpose Name** →
`USE_USAG_NM`; **Parent Purpose** → `PARENT_USE_USAG_CD`; **Display Order** → `DISP_ORD`; **Use Flag**
→ `DISP_YN` (visibility) / `USE_YN` (active vs. soft-deleted); **Created/Updated Date** → the legacy
table has only `MOD_DTM` (no `REG_DTM`), surfaced as `mod_dtm`.

---

## 5. Relationships & scope

- **Self-referencing hierarchy:** `PARENT_USE_USAG_CD → USE_USAG_CD` within the same tenant, max two levels.
- **Tenant isolation:** `BIZ_KEY` is the leading PK column; cross-tenant access cannot succeed by accident.
- **In-scope tables (PROD active list):** `BUSI_USAG_SET_INFM` only. `BUSI_USAG_SET_INFM_LANG_DETL` (single-table model — out of scope) and `BUSI_USAG_ACCT_INFM_DETL` (separate ERP-mapping feature) are **not** entities in this feature.
- See the [Settings ERD](../../../references/database/erd/settings-erd.md) for the cross-feature picture.

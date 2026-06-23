# Menu Authorization — Database Mapping (Backend)

Full column → entity-field → DTO-field mapping, taken from the real entity code. **Two table
families** back this feature:

- **`USER_MENU_AUTH_INFM`** — a **legacy** table (in the PROD Active Table List + the
  [legacy table inventory](../../../references/database/legacy-table-inventory.md)); the per-user
  grant store.
- **`MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION`** — **refactor-owned infrastructure tables,
  NOT legacy.** They are flagged non-legacy in the inventory (alongside `SHEDLOCK`); they replace the
  lost JexAdmin / Jex Studio MenuManager trees. Do not treat them as legacy JEX tables.

> **Schema source.** The three catalogue tables ship as the manually-run schema files
> `V138__menu_item.sql`, `V137__product_menu.sql`, `V139__menu_item_translation.sql`;
> `USER_MENU_AUTH_INFM` ships as `V131__user_menu_auth_infm.sql`. Flyway is disabled
> (`ddl-auto: validate`, schema treated as external / pre-migrated), so the F / M / H **seed rows** for
> the catalogue are loaded as a DBA-run dataset, not an in-tree Flyway INSERT — see
> `src/main/resources/db/migration/` for the schema and the menu-product-type-management export for the
> seed source.

---

## 1. Per-user grant — `USER_MENU_AUTH_INFM` (LEGACY)

Entity: [UserMenuAuthInfm.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/entity/UserMenuAuthInfm.java)
(`@Table("USER_MENU_AUTH_INFM")`, `@IdClass(UserMenuAuthInfmId.class)`). Does **not** extend
`BaseEntity` — the legacy table carries only `MOD_*`, no `REG_*` (2026-06-09 JEX-parity alignment).

| # | DB column | Type (DEV parity) | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | VARCHAR(20) | `bizKey` | ✅ | Tenant key; first predicate of every query |
| 2 | `USER_ID` | VARCHAR(100) | `userId` | ✅ | Grant owner |
| 3 | `WEB_SVC_ID` | VARCHAR(50) | `menuAuthCd` | ✅ | **kebab menuAuthCd slug** — legacy `WEB_SVC_ID` column repurposed to hold the slug-based permission code (identifier-naming rule). Java field stays `menuAuthCd` to keep slug semantics |
| 4 | `USE_YN` | VARCHAR(1) (DEFAULT `'N'`) | `useYn` | | only `'Y'` rows are stored by the editor; absence ⇒ denial |
| 5 | `MOD_ID` | VARCHAR(100) (nullable) | `modId` | | last modifier login id (the granting admin) |
| 6 | `MOD_DTM` | VARCHAR(14) (nullable) | `modDtm` (`OffsetDateTime` via `@Convert(LegacyDtmConverter)`) | | `yyyyMMddHHmmss` |

> DEV's physical table also carries `DISP_ORD NUMERIC(10)` (nullable) and two FK constraints
> (`…_fk01` → `BUSI_INFM`, `…_fk02` → `USER_INFM`). The entity does **not** map `DISP_ORD` — the
> entitlement editor stores no per-user order (Sprint subset; Hibernate `validate` ignores unmapped
> columns). PK is `(BIZ_KEY, USER_ID, WEB_SVC_ID)`.

The delete-then-insert save (`MenuEntitlementService.saveInTransaction`) means only `USE_YN='Y'` rows
ever exist for a (tenant, user) pair; revoking a slug = its row is gone.

---

## 2. Node catalogue — `MENU_ITEM` (REFACTOR INFRA — NOT legacy)

Entity: [MenuItem.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/entity/MenuItem.java)
(`@Table("MENU_ITEM")`, single-column `@Id`). Refactor-owned; seeded by a DBA-run dataset, never
mutated at runtime → no audit columns.

| # | DB column | Type | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `MENU_ITEM_ID` | VARCHAR(100) | `menuItemId` | ✅ | refactor slug — the stable node id |
| 2 | `PARENT_ID` | VARCHAR(100) (nullable) | `parentId` | | self-ref hierarchy; `null` = top menu |
| 3 | `MENU_DEP` | INT (DEFAULT 0) | `menuDep` | | 0 top · 1 group · 2 leaf (legacy `MNU_DEP`) |
| 4 | `ROUTE` | VARCHAR(200) (nullable) | `route` | | target Next.js route; `null` for pure parents |
| 5 | `ICON` | VARCHAR(100) (nullable) | `icon` | | CSS / asset icon hint |
| 6 | `SHOW_YN` | VARCHAR(1) (DEFAULT `'Y'`) | `showYn` | | designer gate — `'N'` hides the node from every session |
| 7 | `PERMISSION_CD` | VARCHAR(100) (nullable) | `permissionCd` | | kebab grant key (∩ key against `USER_MENU_AUTH_INFM.WEB_SVC_ID`); `null` = pure parent (`isParent()`) |
| 8 | `LEGACY_MNU_ID` | VARCHAR(100) (nullable) | `legacyMnuId` | | traceability — JexAdmin `MNU_ID`; never drives routing/gating |
| 9 | `LEGACY_WSVC_ID` | VARCHAR(100) (nullable) | `legacyWsvcId` | | traceability — legacy WSVC id; never drives routing/gating |

Helper index `ix_menu_item_parent` on `PARENT_ID`. `isParent()` = `permissionCd == null`.

---

## 3. Per-product membership — `PRODUCT_MENU` (REFACTOR INFRA — NOT legacy)

Entity: [ProductMenu.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/entity/ProductMenu.java)
(`@Table("PRODUCT_MENU")`, `@IdClass(ProductMenuId.class)`). **Replaces the per-product catalogue role
of the legacy `MENU_AUTH_ADMN_SYS`** — this is where the F / M / H difference is encoded (H omits the
Add-on subtree, Daily Cash, standard Daily Financial, Cash Flow, and carries the HS Daily Financial node
instead; M is structurally identical to F).

| # | DB column | Type | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `PRODUCT_TYPE` | VARCHAR(1) | `productType` | ✅ | `F` / `M` / `H` |
| 2 | `MENU_ITEM_ID` | VARCHAR(100) | `menuItemId` | ✅ | references a `MENU_ITEM` (no JPA relationship — joined in-predicate) |
| 3 | `DISP_ORD` | INT (DEFAULT 0) | `dispOrd` | | legacy `MNU_SEQ` — sibling order within a parent |

Helper index `ix_product_menu_product` on `PRODUCT_TYPE`. The membership set is the **universe** a
product can ever show — a grant outside it is inert (no node).

---

## 4. Per-product per-language label — `MENU_ITEM_TRANSLATION` (REFACTOR INFRA — NOT legacy)

Entity: [MenuItemTranslation.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/entity/MenuItemTranslation.java)
(`@Table("MENU_ITEM_TRANSLATION")`, `@IdClass(MenuItemTranslationId.class)`).

| # | DB column | Type | JPA field | PK | Refactor use |
|---|---|---|---|---|---|
| 1 | `PRODUCT_TYPE` | VARCHAR(1) | `productType` | ✅ | product dimension — required because M's VI/KO labels diverge from F for the same node |
| 2 | `MENU_ITEM_ID` | VARCHAR(100) | `menuItemId` | ✅ | the node |
| 3 | `LANG_CD` | VARCHAR(2) | `langCd` | ✅ | `EN` / `KO` / `VI`. **No KM rows** — the read path falls back to EN |
| 4 | `LABEL` | VARCHAR(200) | `label` | | localized label |

The product-keyed translation deliberately refines the design-doc translation sketch (a
`(MENU_ITEM_ID, LANG_CD)`-only key could not represent F-vs-M label divergence).

---

## 5. Column → DTO → tree mapping (navigation)

| Source column | Entity field | DTO field (`NavMenuNode`) | Notes |
|---|---|---|---|
| `MENU_ITEM.MENU_ITEM_ID` | `menuItemId` | `id` | stable node id (slug) |
| `MENU_ITEM.PERMISSION_CD` | `permissionCd` | `permission_cd` | null for pure parents |
| `MENU_ITEM.PARENT_ID` | `parentId` | `parent_id` | null for top menus |
| `MENU_ITEM.MENU_DEP` | `menuDep` | `depth` | defaults to 0 when null |
| `PRODUCT_MENU.DISP_ORD` | `dispOrd` | `seq` | per-product sibling order |
| `MENU_ITEM_TRANSLATION.LABEL` (caller lang ∥ EN) | `label` | `label` | falls back to `menuItemId` when no translation row |
| `MENU_ITEM.ROUTE` | `route` | `route` | null for pure parents |
| `MENU_ITEM.ICON` | `icon` | `icon` | |
| `MENU_ITEM.LEGACY_MNU_ID` | `legacyMnuId` | `legacy_mnu_id` | traceability |
| `MENU_ITEM.LEGACY_WSVC_ID` | `legacyWsvcId` | `legacy_wsvc_id` | traceability |
| — (computed) | — | `children` | nested filtered+ordered child nodes |
| `BUSI_INFM.PRDT_TYPE` (JWT) | — | `NavigationResponse.product_type` | verbatim letter |
| — (`MENU_GROUP` map) | — | `NavigationResponse.menu_group_id` | `F→WE_CMSVN`, `M→WE_CMSVN_LUMENS`, `H→WE_CMSVN_HAENGSUNG`; null for unrecognised product |

| Source column | Entity field | Entitlement DTO field | Notes |
|---|---|---|---|
| `USER_MENU_AUTH_INFM.USER_ID` | `userId` | `MenuEntitlementResponse.user_id` | echoed for round-trip |
| `USER_MENU_AUTH_INFM.WEB_SVC_ID` (where `USE_YN='Y'`) | `menuAuthCd` | `MenuEntitlementResponse.menu_auth_cds[]` | granted slugs only |

---

## 6. Relationships & scope

- **Tenant isolation:** `BIZ_KEY` is the leading PK column of `USER_MENU_AUTH_INFM`; cross-tenant
  access cannot succeed by accident. The catalogue tables are tenant-independent reference data.
- **Catalogue ↔ grant link:** `MENU_ITEM.PERMISSION_CD` and `USER_MENU_AUTH_INFM.WEB_SVC_ID` share one
  kebab-slug namespace (verified identical), so resolution is a set intersection — there is no FK
  between them (the join is expressed in `ProductMenuRepository.existsInProductTree`'s predicate).
- **No new legacy tables.** Only `USER_MENU_AUTH_INFM` is legacy; the three catalogue tables are
  refactor-owned infra, explicitly sanctioned (non-legacy in the inventory) because the JexAdmin
  MenuManager source was lost. They are **not** subject to the legacy-column-parity rule the way a
  copied legacy table is.

# navigation — Database Mapping (Backend)

**Part of:** Common → navigation
**Last verified:** 2026-06-23

Three **refactor-owned infrastructure tables** back the navigation catalogue. They are **not legacy JEX
tables**, are **not** in the PROD Active Table List, and are flagged non-legacy in the legacy-table
inventory (alongside `menu_item`/`menu_item_translation`/`product_menu`/`shedlock`). They replace the lost
JexAdmin / Jex Studio MenuManager product trees (`WE_CMSVN` / `WE_CMSVN_LUMENS` / `WE_CMSVN_HAENGSUNG`).
Seeded by Flyway and **never mutated at runtime** → no `BaseEntity` audit columns.

- **Schema:** **V137** (`product_menu`) + **V138** (`menu_item`) + **V139** (`menu_item_translation`) —
  each `CREATE TABLE / INDEX IF NOT EXISTS`. *(These were `V242` before the 2026-06-09 migration
  renumber; the pre-renumber file `V242__navigation_menu_catalogue.sql` was in the archived history
  (`db/archive/`), **removed 2026-06-23 (approved decision)**.)*
- **Seed:** 42 menu items, 115 product-menu rows (F=41, H=33, M=41), 345 translations (115 × EN/KO/VI),
  generated from the JexAdmin export. ⚠️ The seed shipped as the pre-renumber `V243__navigation_menu_seed.sql`
  (archived, then **removed with `db/archive/` 2026-06-23**); the live `V137`–`V139` files contain **no** `INSERT`.
  Its removal is **not** missing functionality — UAT/PROD already hold this data in the legacy DB. Clean-environment database provisioning is outside the scope of this repository and must follow DBA-approved procedures. (DC-2)

## `MENU_ITEM` — product-independent node structure

Entity `MenuItem.java`. PK = `MENU_ITEM_ID` (the refactor slug). `@Id` `String`.

| Column | Java field | Type | Null | Notes |
|---|---|---|---|---|
| `MENU_ITEM_ID` | `menuItemId` | varchar(100) | no | PK — stable refactor slug. |
| `PARENT_ID` | `parentId` | varchar(100) | yes | Self-referencing hierarchy; `null` = top menu. |
| `MENU_DEP` | `menuDep` | Integer | no | Depth (0 top · 1 group · 2 leaf) — legacy `MNU_DEP`. |
| `ROUTE` | `route` | varchar(200) | yes | Next.js route; `null` for pure parents. |
| `ICON` | `icon` | varchar(100) | yes | Icon / asset hint; may be `null`. |
| `SHOW_YN` | `showYn` | varchar(1) | no | Designer visibility gate; `'Y'` to render. |
| `PERMISSION_CD` | `permissionCd` | varchar(100) | yes | Kebab menuAuth grant key (`∩` key vs `USER_MENU_AUTH_INFM.MENU_AUTH_CD`); `null` for pure parents. |
| `LEGACY_MNU_ID` | `legacyMnuId` | varchar(100) | yes | Traceability — JexAdmin `MNU_ID`. Never drives routing/gating. |
| `LEGACY_WSVC_ID` | `legacyWsvcId` | varchar(100) | yes | Traceability — legacy WSVC id (URL − `.act`). |

Helper: `isParent()` → `permissionCd == null`. (`MenuItem.java:63-66`)

## `PRODUCT_MENU` — per-product membership + ordering

Entity `ProductMenu.java`, `@IdClass(ProductMenuId.class)`. Replaces the legacy `MENU_AUTH_ADMN_SYS`
per-product default catalogue. This is where the F/M/H difference is encoded (H omits the Add-on subtree,
Daily Cash, standard Daily Financial and Cash Flow, and carries the HS Daily Financial node; M is
structurally identical to F, differing only in VI/KO translation rows).

| Column | Java field | Type | Null | Notes |
|---|---|---|---|---|
| `PRODUCT_TYPE` | `productType` | varchar(1) | no | PK part — `F` / `M` / `H`. |
| `MENU_ITEM_ID` | `menuItemId` | varchar(100) | no | PK part → references a `MENU_ITEM` slug (no JPA relationship; joined in memory / in the guard's JPQL). |
| `DISP_ORD` | `dispOrd` | Integer | no | Sibling order within a parent — legacy `MNU_SEQ` for that product. |

PK: `(PRODUCT_TYPE, MENU_ITEM_ID)`.

## `MENU_ITEM_TRANSLATION` — per-product per-language label

Entity `MenuItemTranslation.java`, `@IdClass(MenuItemTranslationId.class)`.

| Column | Java field | Type | Null | Notes |
|---|---|---|---|---|
| `PRODUCT_TYPE` | `productType` | varchar(1) | no | PK part — product dimension required (M's VI/KO diverge from F). |
| `MENU_ITEM_ID` | `menuItemId` | varchar(100) | no | PK part. |
| `LANG_CD` | `langCd` | varchar(2) | no | PK part — `EN` / `KO` / `VI`. KM has no rows → read path falls back to EN. |
| `LABEL` | `label` | varchar(200) | no | Localized label. |

PK: `(PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)`.

## Read access (no DDL/DML at runtime)

| Repository | Method | Used by |
|---|---|---|
| `MenuItemRepository` | `findAll()` · `existsByPermissionCd(cd)` | service tree load · guard validity gate |
| `ProductMenuRepository` | `findAllByProductType(productType)` · `existsInProductTree(productType, cd)` (JPQL join) | service membership · guard product-tree gate |
| `MenuItemTranslationRepository` | `findAllByProductTypeAndLangCd(productType, langCd)` | service label map (EN + caller lang) |

> **Inventory rule.** These four refactor-owned tables (`menu_item`, `product_menu`,
> `menu_item_translation`, plus `shedlock`) are the documented exception to "no refactor-only tables" —
> approved off-inventory infra. No JEX DDL is the source of truth for them; the V137–V139 migrations are. Do not add a country
> column or audit columns; the Phase-1 country gate (R10) lands without a schema change.

# Authorization — Database Mapping (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

The authorization enforcement layer **owns no table**. It *reads* four tables to make its decision. This
doc maps each table to the step of the `@menuAuthz` rule (and the authority resolution) that consumes
it, and flags legacy vs. refactor-infra. The per-table column-by-column mappings + the grant data model
live in the sibling [menu-authorization](../menu-authorization/database-mapping.md) — cross-linked, not
duplicated here.

Legacy DDL is the schema source of truth (Legacy Schema Parity Rule); the refactor-infra navigation
tables (`MENU_ITEM` / `PRODUCT_MENU`) are flagged **non-legacy** in the
[legacy table inventory](../../../references/database/legacy-table-inventory.md).

---

## Tables read by the authorization layer

| Table | Read by | Step | Legacy / refactor-infra |
|---|---|---|---|
| `BUSI_ADMN_INFM` | `AuthService.login` (jwt-authentication layer) → stamps `authGrCd` into the JWT | authority resolution (feeds steps 3 + role gates) | **Legacy** (PROD Active List) |
| `USER_MENU_AUTH_INFM` | `MenuAuthorizationService.isGranted(bizKey, userId, cd)` | step 4 — per-user grant | **Legacy** (PROD Active List) |
| `MENU_ITEM` | `MenuItemRepository.existsByPermissionCd(cd)` | step 1 — validity (is the cd a product-tree leaf?) | **Refactor-infra** (non-legacy; V242 schema, V243 seed) |
| `PRODUCT_MENU` | `ProductMenuRepository.existsInProductTree(prdtType, cd)` | step 2 — product-tree gate | **Refactor-infra** (non-legacy; V242 schema, V243 seed) |

No `INSERT` / `UPDATE` / `DELETE` is issued by the authorization layer — all four reads are read-only.
`BUSI_ADMN_INFM` is read **at login** (not per request); the rest are read per authorized request.

---

## 1. Authority source — `BUSI_ADMN_INFM`

Authority resolution (presence ⇒ ADMIN) reads `BUSI_ADMN_INFM`. Entity:
[BusiAdmnInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiAdmnInfm.java)
(`@Table("BUSI_ADMN_INFM")`, composite PK `BIZ_KEY + USER_ID`). The repository probe used by the login
finalize:

| Repository method | Purpose |
|---|---|
| `BusiAdmnInfmRepository.existsByBizKeyAndUserId(bizKey, userId)` | the authority decision — `true` ⇒ `authGrCd = "ADMIN"`, else `"USER"` |

The decision is per-(company, user): the composite PK means the same user can be ADMIN at one `BIZ_KEY`
and USER at another. Legacy `USER_BUSI_CONN_INFM` carries **no** `AUTH_GR_CD` column (confirmed on DEV
2026-05-11), so the admin-master table is the *sole* authority source. The resolved value is carried in
the signed JWT `authGrCd` claim — authorization reads the claim, not the table, per request.

> Full column set for `BUSI_ADMN_INFM` is the auth-entity feature's concern (see the jwt-authentication
> / login docs); only the existence probe matters to authorization.

---

## 2. Per-user grant lookup — `USER_MENU_AUTH_INFM`

Step 4 reads `USER_MENU_AUTH_INFM` (legacy table; the Settings > Users > Menu Entitlement screen writes
it). Entity:
[UserMenuAuthInfm.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/entity/UserMenuAuthInfm.java)
(`@Table("USER_MENU_AUTH_INFM")`, composite PK `BIZ_KEY + USER_ID + WEB_SVC_ID`). The guard's probe:

| Repository method | Purpose |
|---|---|
| `UserMenuAuthInfmRepository.existsByBizKeyAndUserIdAndMenuAuthCdAndUseYn(bizKey, userId, menuAuthCd, "Y")` | step 4 — does the user hold the cd grant? |

`WEB_SVC_ID` (the `menuAuthCd` kebab slug, e.g. `addon-einvoices-sales`) is the leaf permission code.
`USE_YN='Y'` means granted. The full column set + the grant write-path (delete-then-insert, admin-only)
are documented in [menu-authorization/database-mapping.md](../menu-authorization/database-mapping.md) —
this module only *reads* one existence probe.

---

## 3. Product-tree validity + membership — `MENU_ITEM` / `PRODUCT_MENU`

Steps 1 + 2 read the refactor navigation catalogue. These are **refactor-owned infrastructure tables**
(not legacy JEX tables, not in the PROD Active Table List; flagged non-legacy in the inventory) that
replace the lost JexAdmin / Jex Studio MenuManager trees.

### 3.1 `MENU_ITEM` (step 1 — validity)

Entity: [MenuItem.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/entity/MenuItem.java)
(`@Table("MENU_ITEM")`, PK `MENU_ITEM_ID` slug). The guard probe:

| Repository method | Purpose |
|---|---|
| `MenuItemRepository.existsByPermissionCd(cd)` | step 1 — is the cd a product-tree leaf in *some* product? (`settings-*` codes are not catalogue leaves, so this returns false for them — they pass step 1 via the prefix instead) |

Relevant column: `PERMISSION_CD` (the kebab leaf code; null on pure-parent nodes). Full schema (PARENT_ID,
MENU_DEP, ROUTE, ICON, SHOW_YN, LEGACY_MNU_ID, LEGACY_WSVC_ID) → menu-authorization.

### 3.2 `PRODUCT_MENU` (step 2 — product-tree gate)

Entity: [ProductMenu.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/entity/ProductMenu.java)
(`@Table("PRODUCT_MENU")`, composite PK `PRODUCT_TYPE + MENU_ITEM_ID`; replaces the per-product catalogue
role of `MENU_AUTH_ADMN_SYS`). The guard probe:

| Repository method | Purpose |
|---|---|
| `ProductMenuRepository.existsInProductTree(productType, permissionCd)` | step 2 — is the leaf in *this* product's tree? Expressed as a predicate join `ProductMenu pm, MenuItem mi WHERE pm.menuItemId = mi.menuItemId AND pm.productType = :productType AND mi.permissionCd = :permissionCd` (the two entities have no JPA relationship) |

`productType` is `TenantContext.currentPrdtType()` (the verbatim `F`/`M`/`H` letter from the JWT). So an
`H` tenant — whose tree omits Cash Flow / Daily Cash / e-Invoices — gets `false` for those cds → 403,
**including** admins (step 2 binds admins too).

---

## 4. Relationships & scope

- **Tenant + user isolation:** the grant probe (`USER_MENU_AUTH_INFM`) and authority probe
  (`BUSI_ADMN_INFM`) both constrain on `BIZ_KEY` (+ `USER_ID`) sourced from the signed JWT — a foreign
  `bizKey` cannot be supplied, so cross-tenant authorization cannot succeed by accident.
- **Product isolation:** `PRODUCT_MENU` membership is keyed on `PRODUCT_TYPE` from the JWT — product
  scope binds admin and user alike.
- **No new tables.** Authorization adds nothing to the schema; `MENU_ITEM` / `PRODUCT_MENU` are the
  refactor-infra navigation tables owned by menu-authorization, and `BUSI_ADMN_INFM` /
  `USER_MENU_AUTH_INFM` are legacy inventory tables.
- **No version numbers asserted here.** Per the docs rule, the refactor-infra navigation tables are
  cited by table name (`MENU_ITEM` / `PRODUCT_MENU`); `BUSI_ADMN_INFM` and `USER_MENU_AUTH_INFM` are
  legacy tables — see [menu-authorization](../menu-authorization/database-mapping.md) and the
  [legacy table inventory](../../../references/database/legacy-table-inventory.md) for their migration
  provenance.

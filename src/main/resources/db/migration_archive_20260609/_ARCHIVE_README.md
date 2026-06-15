# Archived Flyway migrations (V1–V248) — 2026-06-09

These 228 migration files were the refactor's previous Flyway history. They were
**archived (not deleted)** on 2026-06-09 per Sonny's decision to restart the
`db/migration` folder from legacy JEX DDL, table by table.

## Why archived
- The refactored app now runs against the **existing live JEX databases** in
  DEV / UAT / PROD with **zero data migration**. The legacy JEX DB is the schema
  source of truth. Flyway must NOT rebuild/migrate business-data tables.
- New `db/migration/V1__*.sql` files are authored **only** from legacy JEX DDL
  provided by Sonny, verbatim.

## Reversibility / review
- 227 of these were moved with `git mv` (history preserved; appear as renames in
  `git diff`). `V248__van_api_info_table.sql` was untracked and plain-moved.
- Nothing is lost — restore any file by moving it back into `db/migration/`.

## ⚠️ Two cautions (carried from the pre-archive review)
1. **Flyway history collision.** The shared dev/SIT databases still carry a
   `flyway_schema_history` up to V248. Restarting at V1 is safe **only while
   Flyway stays disabled** (it is, in all profiles). Before ever re-enabling
   Flyway against those DBs, clear/baseline that history first or the new V1 will
   fail on checksum/ordering.
2. **Refactor-only objects JEX lacks.** ~75 of these files defined objects/data
   that DO NOT exist in the live JEX schema and have **no replacement migration**
   now: views `v_firm_banking_history` / `v_firm_banking_settings` /
   `v_busi_usag_set_infm_purpose_in_use`; tables `menu_item` / `product_menu` /
   `menu_item_translation` / `shedlock` / `notification`; refactor-added columns
   (V168/V171/V185/V195/V212); and the entire slug-based View Setting registry in
   `SYS_SCR_OPTION_WECMS`. Those features will not run against JEX until the
   objects are provisioned into JEX or reworked (PO decision pending). See the
   migration inventory report (2026-06-09) for the full list.

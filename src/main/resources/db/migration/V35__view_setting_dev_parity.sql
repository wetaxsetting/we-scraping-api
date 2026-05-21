-- ============================================================================
-- V35__view_setting_dev_parity.sql
--
-- Schema-only DEV parity for SYS_SCR_OPTION_WECMS / USER_SCR_OPTION_WECMS.
-- Tightens local schema to match `wecmsvndev` column-for-column for the
-- attributes that previously diverged (V34 had documented these as
-- refactor-only deviations). Sampled from `wecmsvndev` 2026-05-02 — see
-- DEV column listing below.
--
-- Direction (locked with Sonny on 2026-05-02): the View Setting feature
-- continues to use semantic route-based screenIds (e.g.
-- `banking-account-inquiry-vnd`) — NOT the legacy JEX `fnsh_0001_001`.
-- The data layer therefore keeps the V32 `banking-account-inquiry-vnd`
-- rows seeded at V32 and refined by V33/V34 — V35 does NOT touch row
-- contents. Only column types / nullability tighten here.
--
-- Why screenId stays semantic kebab-case (per `lib/viewSettingScreens.ts`
-- registry + wecms-web/CLAUDE.md §13):
--   - Refactor frontend has no `fnsh_0001_001` route segment.
--   - Legacy JEX ids leaked into the refactor would orphan as the JEX
--     namespace evolves.
--   - `<module>-<page>-<type>` matches the Next.js route convention
--     (`/banking/account-inquiry` + VND/FCY) and reads cleanly in URLs,
--     logs, and migrations.
--
-- DEV reference (`information_schema.columns`):
--
--   sys_scr_option_wecms   PK = (web_svc_id, field_id)
--     web_svc_id     varchar(50)  NN
--     field_id       varchar(100) NN
--     field_nm       varchar(100) NN     ← local was varchar(200) NULL
--     disp_ord       numeric(10)  NN     ← local was integer NN DEFAULT 0
--     disp_yn        varchar(1)   NN
--     use_yn         varchar(1)   NN
--     size           varchar(10)  NN     ← local was varchar(10) NULL
--     dym_area_id    varchar(50)  NN
--     sort_yn        char(1)      NN
--     html_cont      text         NULL
--
--   user_scr_option_wecms  PK = (biz_key, user_id, web_svc_id, field_id)
--     biz_key        varchar(20)  NN
--     user_id        varchar(100) NN
--     web_svc_id     varchar(50)  NN
--     field_id       varchar(100) NN
--     field_nm       varchar(100) NN     ← local was varchar(200) NULL
--     disp_ord       numeric(10)  NN     ← local was integer NN DEFAULT 0
--     disp_yn        varchar(1)   NN
--     use_yn         varchar(1)   NN
--     size           varchar(10)  NN     ← deferred (see below)
--     dym_area_id    varchar(50)  NN
--     reg_id         varchar(100) NN     ← local nullable; DEV varchar(14)
--     reg_dtm        varchar(14)  NN     ←   nullability + type both
--     mod_id         varchar(100) NULL   ←   DEFER — BaseEntity standard
--     mod_dtm        varchar(14)  NN     ←   uses TIMESTAMPTZ + @PrePersist
--
-- Decisions per column (locked 2026-05-02):
--
--   field_nm       → ALTER VARCHAR(200) NULL → VARCHAR(100) NOT NULL.
--                    Backfill any null with FIELD_ID before tightening.
--   disp_ord       → ALTER INTEGER → NUMERIC(10,0). DROP DEFAULT first so
--                    the cast doesn't have to coerce the default. Forces
--                    the JPA entity to switch from `Integer` to `Long`
--                    (handled in the same MR).
--   sys.size       → SET NOT NULL. V34 already populated `size` for every
--                    `banking-account-inquiry-vnd` row using Figma WB365
--                    widths; the SET NOT NULL is safe.
--   user.size      → STAYS NULLABLE on local. `ScreenOptionService.
--                    initUserRowsFromSys` now copies `sys.size → user.size`
--                    on first GET, so all NEW user rows will carry size,
--                    but existing local user rows (V32 testing) still
--                    have NULL size. Flip to NOT NULL in a follow-up
--                    migration after one round of dev/QA confirms
--                    coverage of the lazy-init path.
--
-- Refactor-only deviations (KEPT — explicit approved reasons):
--
--   fix_yn         — column-locking feature (`docs/features/view-setting.md`
--                    §1.8) enforced in `ScreenOptionService` — locks
--                    `BANK` for Account Inquiry. KEPT.
--   reg_id /       — audit columns inherited from `BaseEntity`. DEV stores
--   reg_dtm /        reg_dtm/mod_dtm as varchar(14) (legacy YYYYMMDDHHMMSS
--   mod_id /         strings); local uses TIMESTAMPTZ which is intentionally
--   mod_dtm          more correct. `BaseEntity` is the project standard;
--                    KEPT.
--
-- Idempotency:
--   - `UPDATE … WHERE field_nm IS NULL` is a no-op on a re-run.
--   - `ALTER COLUMN TYPE` is a no-op when the column is already at the
--     target type/length.
--   - `SET NOT NULL` is a no-op when the column is already NOT NULL.
--   - `DROP DEFAULT` is a no-op when no default is set.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Schema alignment — SYS_SCR_OPTION_WECMS.
-- ----------------------------------------------------------------------------

-- field_nm: VARCHAR(200) NULL → VARCHAR(100) NOT NULL
UPDATE SYS_SCR_OPTION_WECMS  SET FIELD_NM = FIELD_ID WHERE FIELD_NM IS NULL;
ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN FIELD_NM TYPE VARCHAR(100);
ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN FIELD_NM SET NOT NULL;

-- disp_ord: INTEGER NOT NULL DEFAULT 0 → NUMERIC(10,0) NOT NULL
ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN DISP_ORD DROP DEFAULT;
ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN DISP_ORD TYPE NUMERIC(10,0) USING DISP_ORD::numeric;

-- size: VARCHAR(10) NULL → VARCHAR(10) NOT NULL.
-- Defensive backfill before SET NOT NULL — V34 populated size for every
-- `banking-account-inquiry-vnd` row, so this UPDATE should hit zero rows
-- on a clean V34→V35 path; the WHERE guards against any stray sys row
-- in a future seed forgetting to set size.
UPDATE SYS_SCR_OPTION_WECMS  SET "SIZE" = 'auto' WHERE "SIZE" IS NULL;
ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN "SIZE" SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 2. Schema alignment — USER_SCR_OPTION_WECMS.
--    `size` is intentionally left NULLABLE here — see header comment.
-- ----------------------------------------------------------------------------

-- field_nm: VARCHAR(200) NULL → VARCHAR(100) NOT NULL
UPDATE USER_SCR_OPTION_WECMS SET FIELD_NM = FIELD_ID WHERE FIELD_NM IS NULL;
ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN FIELD_NM TYPE VARCHAR(100);
ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN FIELD_NM SET NOT NULL;

-- disp_ord: INTEGER NOT NULL DEFAULT 0 → NUMERIC(10,0) NOT NULL
ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN DISP_ORD DROP DEFAULT;
ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN DISP_ORD TYPE NUMERIC(10,0) USING DISP_ORD::numeric;

-- ----------------------------------------------------------------------------
-- 3. NO data wipe / reseed.
--    Existing `banking-account-inquiry-vnd` rows from V32 / V33 / V34 are
--    correct under the locked screenId convention and stay in place. The
--    legacy `fnsh_0001_001` namespace is intentionally NOT seeded — see
--    `lib/viewSettingScreens.ts` for the single source of truth on
--    refactor-side screenIds.
-- ----------------------------------------------------------------------------

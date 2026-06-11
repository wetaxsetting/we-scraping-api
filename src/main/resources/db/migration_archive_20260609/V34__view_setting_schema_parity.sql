-- ============================================================================
-- V34__view_setting_schema_parity.sql
--
-- Schema-parity audit for SYS_SCR_OPTION_WECMS / USER_SCR_OPTION_WECMS vs.
-- DEV (`wecmsvndev`, sampled 2026-05-02). Brings local schema column-for-
-- column closer to DEV per the Legacy Schema Parity Rule, backfills SIZE
-- values for `banking-account-inquiry-vnd` (using Figma WB365 widths — see
-- "decisions per column" below), and corrects two row-value drifts that
-- accumulated after V32 was applied.
--
-- The frontend does NOT yet consume SIZE — column widths still come from
-- Figma WB365 → Tailwind tokens (`--w-wb-col-*` in `app/globals.css`).
-- SIZE is seeded as schema-only metadata so that a future Option-C MR
-- (wire SIZE through entity + DTO + frontend `<col width>`) can flip the
-- data layer without a fresh data fix. The backend entity / DTO / TS type
-- are intentionally NOT extended here.
--
-- DEV reference (`information_schema.columns` on `dev.wecms.vn`):
--   sys: web_svc_id, field_id, field_nm(100), disp_ord(numeric(10)),
--        disp_yn, use_yn, size(varchar(10)), dym_area_id, sort_yn(char(1)),
--        html_cont(text)
--   user: biz_key, user_id, web_svc_id, field_id, field_nm(100),
--         disp_ord(numeric(10)), disp_yn, use_yn, size(varchar(10)),
--         dym_area_id, reg_id, reg_dtm(varchar(14)), mod_id, mod_dtm(varchar(14))
--
-- Decisions per column (vs DEV — locked with Sonny 2026-05-02):
--
--   field_nm   → kept VARCHAR(200) NULL (DEV is VARCHAR(100) NOT NULL).
--                Wider/looser is safe-compatible. Aligning to 100 would
--                require an entity-side change (`@Column(length = 200)`)
--                with no functional gain — every current label fits.
--   disp_ord   → kept INTEGER NOT NULL DEFAULT 0 (DEV is NUMERIC(10) NOT NULL).
--                Functionally equivalent for the bounded 0..99 domain;
--                matches the JPA `Integer dispOrd` field type. Aligning to
--                NUMERIC(10) would force the entity field to BigDecimal/Long
--                with no run-time benefit.
--   disp_yn    → tightened to NOT NULL with DEFAULT 'Y' (was nullable on local).
--                Matches DEV's NOT NULL contract.
--   size       → ALTER INTEGER → VARCHAR(10) (DEV parity). Kept nullable on
--                LOCAL until SIZE is wired through end-to-end (Option C);
--                DEV is NOT NULL but our lazy-init in
--                `ScreenOptionService.initUserRowsFromSys` does NOT yet
--                populate user.size, so making user.size NOT NULL here would
--                break the next save. Documented deviation; flips when
--                Option C ships.
--   sort_yn    → ADDED CHAR(1) NOT NULL DEFAULT 'Y' on sys only (DEV legacy
--                parity). DEV does NOT have this column on user_scr — local
--                also keeps it sys-only.
--   html_cont  → ADDED TEXT NULL on sys only (DEV legacy parity). Same as
--                sort_yn — DEV user_scr lacks this column; local mirrors.
--
-- Refactor-only deviations (intentional, kept):
--   fix_yn       — column-locking feature; not in DEV. Documented in
--                  docs/features/view-setting.md §1.8.
--   reg_id/reg_dtm/mod_id/mod_dtm — audit columns via `BaseEntity`. DEV
--                  stores reg_dtm/mod_dtm as varchar(14) (legacy
--                  YYYYMMDDHHMMSS strings); local uses TIMESTAMPTZ which is
--                  intentionally more correct.
--
-- Idempotent: ALTER COLUMN TYPE is a no-op when already varchar(10);
-- ADD COLUMN IF NOT EXISTS skips on re-run; UPDATE … WHERE only touches
-- targeted screen rows; DELETE … WHERE only clears the targeted scope.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Type alignment — SIZE INTEGER → VARCHAR(10) on both tables.
--    All current rows have SIZE = NULL (V30 / V32 / V33 never populated it),
--    so `USING NULL` is a safe transform on sys; user_scr is currently
--    empty for every scope, so the conversion is also a no-op there.
-- ----------------------------------------------------------------------------
ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN "SIZE" TYPE VARCHAR(10) USING NULL;
ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN "SIZE" TYPE VARCHAR(10) USING NULL;

-- ----------------------------------------------------------------------------
-- 2. New columns on sys_scr_option_wecms only — DEV legacy parity.
--    user_scr_option_wecms in DEV does NOT have sort_yn / html_cont;
--    local keeps them sys-only to match.
-- ----------------------------------------------------------------------------
ALTER TABLE SYS_SCR_OPTION_WECMS ADD COLUMN IF NOT EXISTS SORT_YN   CHAR(1) NOT NULL DEFAULT 'Y';
ALTER TABLE SYS_SCR_OPTION_WECMS ADD COLUMN IF NOT EXISTS HTML_CONT TEXT;

-- ----------------------------------------------------------------------------
-- 3. Tighten disp_yn to NOT NULL (DEV parity).
--    Defensive UPDATE first so the SET NOT NULL never fails on stray nulls.
-- ----------------------------------------------------------------------------
UPDATE SYS_SCR_OPTION_WECMS  SET DISP_YN = 'Y' WHERE DISP_YN IS NULL;
UPDATE USER_SCR_OPTION_WECMS SET DISP_YN = 'Y' WHERE DISP_YN IS NULL;
ALTER TABLE SYS_SCR_OPTION_WECMS  ALTER COLUMN DISP_YN SET NOT NULL;
ALTER TABLE USER_SCR_OPTION_WECMS ALTER COLUMN DISP_YN SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 4. Backfill SIZE values for `banking-account-inquiry-vnd` using FIGMA
--    WB365 widths — the layout authority for the refactor (see
--    `wecms-web/app/globals.css` `--w-wb-col-*` tokens). Legacy DEV
--    SIZE values disagree with Figma in 5 of 7 columns; using Figma values
--    keeps the data layer consistent with what the table actually renders.
--
--    Also re-asserts two row-values that drifted from V32-as-written:
--      - BANK         fix_yn  : observed 'N' → V32 says 'Y'
--      - VAS_ACCOUNT  use_yn  : observed 'Y' → V32 says 'N'
--                     disp_yn : observed 'N' → V32 says 'Y'
--    Cause of the drift is unknown (V32 file matches its staged content in
--    git, Flyway recorded V32 as successful). Re-asserting here is
--    additive and safe.
-- ----------------------------------------------------------------------------
UPDATE SYS_SCR_OPTION_WECMS
   SET "SIZE" = '38px'
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd' AND FIELD_ID = 'CHECKBOX';

UPDATE SYS_SCR_OPTION_WECMS
   SET "SIZE" = '240px',
       FIX_YN = 'Y'
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd' AND FIELD_ID = 'BANK';

UPDATE SYS_SCR_OPTION_WECMS
   SET "SIZE" = '280px'
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd' AND FIELD_ID = 'ACCOUNT_NICKNAME';

UPDATE SYS_SCR_OPTION_WECMS
   SET "SIZE" = '160px'
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd' AND FIELD_ID = 'BALANCE';

UPDATE SYS_SCR_OPTION_WECMS
   SET "SIZE" = '180px'
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd' AND FIELD_ID = 'AVAILABLE_BALANCE';

UPDATE SYS_SCR_OPTION_WECMS
   SET "SIZE"  = '200px',
       USE_YN  = 'N',
       DISP_YN = 'Y'
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd' AND FIELD_ID = 'VAS_ACCOUNT';

UPDATE SYS_SCR_OPTION_WECMS
   SET "SIZE" = '160px'
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd' AND FIELD_ID = 'LAST_UPDATE';

-- ----------------------------------------------------------------------------
-- 5. Clear USER_SCR_OPTION_WECMS for this screen so the View Setting
--    lazy-init step in `ScreenOptionService.load()` re-copies the freshly
--    corrected sys defaults on the next page load. The user table is
--    currently empty for this scope; the DELETE is a defensive no-op that
--    also prevents stale rows in any environment that may have populated
--    the user table earlier.
-- ----------------------------------------------------------------------------
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'banking-account-inquiry-vnd';

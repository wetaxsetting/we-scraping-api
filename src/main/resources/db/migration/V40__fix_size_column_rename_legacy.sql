-- V40 — Fix SIZE column: drop V39 duplicate, rename legacy "SIZE" → size.
--
-- V39 added a *new* lowercase `size` column (backfilled to 'auto') alongside
-- the existing `"SIZE"` (quoted uppercase) column that already holds the real
-- Figma-derived pixel values seeded by V34 (38px, 240px, 280px, 160px …).
-- Two problems with that approach:
--   a) The legacy data lives in `"SIZE"`, not in the V39 `size` column.
--   b) `auto` is not a valid pixel value — it silently overwrites Figma data
--      for any row the page reads through the V39 path.
--
-- This migration corrects the situation:
--   1. Drop the V39 `size` column (wrong 'auto' values on all rows).
--   2. Rename the legacy `"SIZE"` column to lowercase `size` (unquoted),
--      preserving all existing Figma px values in place.
--   3. Widen to varchar(20) (previously varchar(10) — no existing value
--      exceeds 6 chars, but varchar(20) gives headroom for future values).
--   4. sys table: re-assert NOT NULL (was already NOT NULL; 0 nulls confirmed).
--      user table: stays NULLABLE — ScreenOptionService.initUserRowsFromSys
--      copies sys.size into user rows on the first lazy GET, so nulls are
--      expected here until then.
--
-- After this migration both tables have exactly one `size` column (lowercase,
-- unquoted) that Hibernate's generated SQL resolves without quoting.

-- Step 1: drop the V39 duplicate (wrong 'auto' values)
ALTER TABLE sys_scr_option_wecms  DROP COLUMN IF EXISTS size;
ALTER TABLE user_scr_option_wecms DROP COLUMN IF EXISTS size;

-- Step 2: rename legacy quoted identifier to plain lowercase
ALTER TABLE sys_scr_option_wecms  RENAME COLUMN "SIZE" TO size;
ALTER TABLE user_scr_option_wecms RENAME COLUMN "SIZE" TO size;

-- Step 3: widen from varchar(10) → varchar(20)
ALTER TABLE sys_scr_option_wecms  ALTER COLUMN size TYPE varchar(20);
ALTER TABLE user_scr_option_wecms ALTER COLUMN size TYPE varchar(20);

-- Step 4: nullability
ALTER TABLE sys_scr_option_wecms  ALTER COLUMN size SET NOT NULL;
-- user.size stays NULLABLE (no SET NOT NULL here)

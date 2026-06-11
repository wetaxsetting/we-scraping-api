-- V39 — Add lowercase `size` column to screen-option tables.
--
-- The "SIZE" column (uppercase, quoted identifier) was created in V30 as INTEGER
-- and converted to VARCHAR(10) in V34. Hibernate 6 generates column references as
-- lowercase unquoted identifiers (`size`), which PostgreSQL treats as a different
-- identifier from the quoted `"SIZE"`. This causes the startup error:
--   ERROR: column ssow1_0.size does not exist
--
-- Fix: add an unquoted lowercase `size varchar(20)` column to both tables so
-- Hibernate's generated SQL can find it under `ddl-auto: validate`.
--
-- sys table: entity maps @Column(name = "SIZE", nullable = false)
--   → backfill existing rows to 'auto', then SET NOT NULL.
-- user table: entity maps @Column(name = "SIZE") with nullable default
--   → stays NULLABLE; initUserRowsFromSys copies sys.size on lazy-init.

ALTER TABLE sys_scr_option_wecms  ADD COLUMN IF NOT EXISTS size varchar(20);
UPDATE       sys_scr_option_wecms SET size = 'auto' WHERE size IS NULL;
ALTER TABLE  sys_scr_option_wecms  ALTER COLUMN size SET NOT NULL;

ALTER TABLE user_scr_option_wecms ADD COLUMN IF NOT EXISTS size varchar(20);

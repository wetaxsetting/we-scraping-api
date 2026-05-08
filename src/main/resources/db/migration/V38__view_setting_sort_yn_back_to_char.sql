-- ============================================================================
-- V38__view_setting_sort_yn_back_to_char.sql
--
-- Reverse V37: convert SYS_SCR_OPTION_WECMS.SORT_YN back from VARCHAR(1)
-- to CHAR(1) so the local schema matches DEV exactly. V37's CHAR→VARCHAR
-- conversion was the wrong direction — Sonny's call (2026-05-02): keep
-- DEV parity on the DB side and teach Hibernate to map `String sortYn`
-- as CHAR via `@JdbcTypeCode(SqlTypes.CHAR)`. The Hibernate-side change
-- ships in the same MR as this migration.
--
-- DEV (`wecmsvndev` 2026-05-02 ): sort_yn is `character(1)` NOT NULL.
-- Local should match.
--
-- Drop default first → cast type → re-set default. PostgreSQL would
-- happily cast the existing `'Y'::character varying` default to bpchar
-- in one statement, but the explicit DROP/SET sequence is the safer
-- pattern (mirrors Sonny's recommended ALTER block) — and `'Y'::bpchar`
-- is the canonical default form on DEV.
--
-- USER_SCR_OPTION_WECMS does NOT carry `sort_yn` locally (DEV doesn't
-- either — V34 added it sys-only); migration touches sys only.
--
-- Idempotent: ALTER COLUMN TYPE is a no-op when already CHAR(1);
-- DROP/SET DEFAULT is a no-op when matching the current default.
-- ============================================================================

ALTER TABLE SYS_SCR_OPTION_WECMS ALTER COLUMN SORT_YN DROP DEFAULT;
ALTER TABLE SYS_SCR_OPTION_WECMS ALTER COLUMN SORT_YN TYPE CHAR(1) USING SORT_YN::char(1);
ALTER TABLE SYS_SCR_OPTION_WECMS ALTER COLUMN SORT_YN SET DEFAULT 'Y';

-- WeCMS Manual Database Change
-- Date: 2026-07-20
-- Source: migrated from Claude Code (AI)/sql/purpose-settings_20260720.sql
-- Related record: TBD
-- Type: DDL+DML
-- Execution: MANUAL
-- Target environments: Refactor DB (172.168.5.172:5432/wecmsdevdb)
-- Executor: DBA TEAM
-- Rollback: Reversible by dropping the table and index; the seed is reference data only
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
-- COLLISION NOTE — the CREATE TABLE below is equivalent to src/main/resources/db/migration/V123__usag_infm_lang_detl.sql (ALREADY GOVERNED). It is idempotent (IF NOT EXISTS); the helper index and the seed procedure are what this script adds. No duplicate migration was created.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =====================================================================
--  Purpose Settings — system usage master name table (USAG_INFM_LANG_DETL)
--  Feature: settings/purpose (legacy comm_0187_001 parity)
--  Date   : 2026-07-20
--  Run by : DBA, BY HAND, against the refactor DB
--           (172.168.5.172:5432/wecmsdevdb — Flyway is disabled in this
--            project; ddl-auto=validate. This is NOT an auto-run migration.)
--
--  WHY
--  ---
--  Legacy comm_0187_001 shows the standard name of a *system-default*
--  purpose (e.g. Banking Withdrawal code 40 -> "Loan Repaid") even when
--  the per-business row BUSI_USAG_SET_INFM.USE_USAG_NM is NULL. The
--  display name is:
--      COALESCE(BUSI_USAG_SET_INFM.USE_USAG_NM,
--               USAG_INFM_LANG_DETL.USAG_NM  for the user's language)
--  The refactor backend now resolves that fallback
--  (SystemUsageNameRepository + PurposeSettingsService.resolveSystemNames),
--  but the master table does not yet exist in the refactor dev DB, so
--  system-default purposes render blank until this script is run.
--
--  BOTH USAG_INFM_LANG_DETL and USAG_INFM are on the PROD Active Table
--  List. This script adds ONLY the localized-name table the Purpose
--  screen needs. Get PO (Mr. Jung) sign-off before running in any shared
--  environment — it is additive (new table) and safe for live-JEX
--  coexistence (the refactor reads it; JEX owns it).
--
--  RUN ORDER
--  ---------
--   1. Section A — CREATE TABLE (idempotent).
--   2. Section B — SEED from the live JEX DB. The master data is
--      tenant-independent (keyed by USE_USAG_CD + LANG_CODE only);
--      copy the whole table so every product/tenant benefits.
-- =====================================================================


-- ---------------------------------------------------------------------
--  Section A — schema (column-for-column with JEX wecmsvndev)
--  JEX shape: use_usag_cd varchar(20) NOT NULL,
--             lang_code   varchar(2)  NOT NULL,
--             usag_nm     varchar(100) NULL,
--             usag_cont   text         NULL
--             PK (use_usag_cd, lang_code)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usag_infm_lang_detl (
    use_usag_cd  VARCHAR(20)  NOT NULL,
    lang_code    VARCHAR(2)   NOT NULL,
    usag_nm      VARCHAR(100),
    usag_cont    TEXT,
    CONSTRAINT usag_infm_lang_detl_pk PRIMARY KEY (use_usag_cd, lang_code)
);

-- Helps the Purpose read path (WHERE lang_code = ? AND use_usag_cd IN (...)).
CREATE INDEX IF NOT EXISTS ix_usag_infm_lang_detl_lang
    ON usag_infm_lang_detl (lang_code, use_usag_cd);


-- ---------------------------------------------------------------------
--  Section B — seed the master rows from the live JEX DB.
--  ~3,052 rows / 763 codes / 4 languages (EN/KO/VI/KM). Reference data,
--  so the 30-rows-per-table tenant cap does NOT apply — copy in full.
--
--  Pick ONE of the two options below (both idempotent):
--
--  Option 1 (recommended) — server-to-server copy, run from a shell
--  that can reach both DBs:
--
--      pg_dump -h 10.254.242.254 -p 54311 -U <jex_ro_user> \
--              -d wecmsvndevdb --data-only --table=usag_infm_lang_detl \
--        | psql -h 172.168.5.172 -p 5432 -U wecmsdev_owner -d wecmsdevdb
--
--    (If duplicate-key errors appear on re-run, load into a TEMP table
--     first and INSERT ... ON CONFLICT DO NOTHING — see Option 2 shape.)
--
--  Option 2 — dblink / postgres_fdw pull (run on the refactor DB if the
--  extension + a JEX foreign server are available):
--
--      INSERT INTO usag_infm_lang_detl (use_usag_cd, lang_code, usag_nm, usag_cont)
--      SELECT use_usag_cd, lang_code, usag_nm, usag_cont
--      FROM   dblink('<jex_conn>',
--                    'SELECT use_usag_cd, lang_code, usag_nm, usag_cont FROM usag_infm_lang_detl')
--             AS t(use_usag_cd varchar(20), lang_code varchar(2),
--                  usag_nm varchar(100), usag_cont text)
--      ON CONFLICT (use_usag_cd, lang_code) DO NOTHING;
--
--  After seeding, the Purpose Deposit/Withdrawal/Deleted tabs display
--  the standard localized names automatically — no application restart
--  or code change required (the backend already reads this table).
-- ---------------------------------------------------------------------

-- Verification (expect ~3,052 rows, 4 distinct lang_code):
-- SELECT count(*) AS rows, count(DISTINCT use_usag_cd) AS codes,
--        count(DISTINCT lang_code) AS langs FROM usag_infm_lang_detl;

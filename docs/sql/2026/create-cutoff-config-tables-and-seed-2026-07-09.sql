-- WeCMS Manual Database Change
-- Date: 2026-07-09
-- Source: migrated from Claude Code (AI)/sql/cutoff-config_20260709.sql
-- Related record: docs/deployment/wecms-batch-schedule-seed-review.md
-- Type: DDL+DML
-- Execution: MANUAL
-- Target environments: TBD — original script does not name the environments
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — no rollback documented in the original script
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
-- MIGRATION REVIEW REQUIRED — this script creates a new table and/or DB function that has no counterpart in src/main/resources/db/migration/. No migration was created and no V-number was assigned during the archive migration; the placement decision is open.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- cutoff-config_20260709.sql
-- DB-backed cut-off configuration — Phase 1: DDL + SEED ONLY (no app code).
-- =============================================================================
--
-- Purpose
--   Create the tables that will back the runtime-editable cut-off configuration
--   (windows + ≥500M threshold) and SEED them with the EXACT current values
--   from wecms-api/src/main/resources/application.yml so the eventual cutover is
--   byte-identical (zero behaviour change on day one).
--
--   Follows: Claude Code (AI)/notes/cutoff-config-architecture-recommendation.md
--            (REVISED) §3 (schema) + §4 (migration Phase 1–2) + §6 (audit spine).
--
-- Scope of THIS script (deliberately narrow)
--   • Creates TB_CUTOFF_CONFIG, TB_CUTOFF_PARAM, TB_CUTOFF_CONFIG_HIS.
--   • Seeds TB_CUTOFF_CONFIG + TB_CUTOFF_PARAM from application.yml.
--   • Seeds an initial 'C' (create) snapshot into TB_CUTOFF_CONFIG_HIS.
--   • Does NOT create any entity / repository / service / endpoint (Phase 3+).
--   • Does NOT change the frontend or CutoffValidator logic.
--   • Does NOT drop or alter any existing object.
--
-- Execution model  (IMPORTANT — READ BEFORE RUNNING)
--   • RUN BY A DBA, BY HAND. This is NOT a Flyway migration and is NOT applied
--     by the application on startup. Flyway is disabled in wecms-api and
--     ddl-auto = validate in every profile (project_flyway_disabled_ddl_validate),
--     so the schema is external / pre-migrated. (Precedent: V156__sys_scr_option
--     — versioned filename, DBA-run, not Flyway-run.)
--   • No entity is mapped to these tables yet, so ddl-auto = validate will IGNORE
--     them (validate only checks mapped entities). Creating them now is safe and
--     does not affect the running app.
--   • Run once per environment (local / sit / uat / prod), same script.
--   • Idempotent — safe to re-run: CREATE TABLE IF NOT EXISTS + INSERT … ON
--     CONFLICT DO NOTHING. A re-run NEVER overwrites a row a DBA/admin later
--     edited (ON CONFLICT DO NOTHING preserves live values).
--   • PostgreSQL. DDL is transactional in PG, so the whole script is wrapped in
--     one transaction — a failure rolls everything back.
--
-- Column conventions
--   • UPPER_SNAKE column names (refactor convention).
--   • Audit columns MATCH BaseEntity so a future @Entity extending BaseEntity
--     passes ddl-auto = validate:
--        REG_ID  VARCHAR(100), REG_DTM VARCHAR(14),
--        MOD_ID  VARCHAR(100), MOD_DTM VARCHAR(14)   (yyyyMMddHHmmss via LegacyDtmConverter)
--   • Times are Vietnam-local wall-clock "HH:mm" text (NOT instants — no TZ).
--
-- PO approval
--   These are REFACTOR-ONLY tables with NO legacy JEX counterpart → they require
--   explicit PO (Mr. Jung) sign-off per the "no refactor-only tables" rule. They
--   are migration-safe (there is no legacy cut-off data to migrate) and a
--   documented, deliberate divergence from the legacy-parity baseline. Do not run
--   this script until that approval is recorded. See the PO-approval checklist at
--   the end of this file.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. TB_CUTOFF_CONFIG — per (bank, network) window. Natural PK (no wb_get_next_val).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_CUTOFF_CONFIG (
    BANK_KEY       VARCHAR(20)  NOT NULL,             -- SHINHAN | WOORI | VCB (brand; matches CutoffValidator.resolveBank)
    NETWORK        VARCHAR(20)  NOT NULL,             -- NAPAS | CITAD | CITAD_API | SWIFT | CITAD_LARGE
    START_TIME     VARCHAR(5)   NOT NULL,             -- "HH:mm" VN-local, window open
    END_TIME       VARCHAR(5)   NOT NULL,             -- "HH:mm" VN-local, cut-off
    WARNING_TIME   VARCHAR(5)   NULL,                 -- "HH:mm" FE soft-confirm zone; NULL where none
    ENABLE_YN      CHAR(1)      NOT NULL DEFAULT 'Y', -- 'N' → provider falls back to the application.yml default for this (bank, network)
    CHANGE_REASON  VARCHAR(500) NULL,                 -- bank-confirmation note / ticket ref (banking auditability)
    REG_ID         VARCHAR(100) NULL,
    REG_DTM        VARCHAR(14)  NULL,                 -- yyyyMMddHHmmss
    MOD_ID         VARCHAR(100) NULL,
    MOD_DTM        VARCHAR(14)  NULL,                 -- yyyyMMddHHmmss
    CONSTRAINT TB_CUTOFF_CONFIG_PK      PRIMARY KEY (BANK_KEY, NETWORK),
    CONSTRAINT TB_CUTOFF_CONFIG_CHK_EN  CHECK (ENABLE_YN IN ('Y', 'N')),
    CONSTRAINT TB_CUTOFF_CONFIG_CHK_ST  CHECK (START_TIME ~ '^[0-2][0-9]:[0-5][0-9]$'),
    CONSTRAINT TB_CUTOFF_CONFIG_CHK_ET  CHECK (END_TIME   ~ '^[0-2][0-9]:[0-5][0-9]$'),
    CONSTRAINT TB_CUTOFF_CONFIG_CHK_WT  CHECK (WARNING_TIME IS NULL OR WARNING_TIME ~ '^[0-2][0-9]:[0-5][0-9]$'),
    -- Same-day windows only: zero-padded 24h "HH:mm" compares chronologically as text.
    CONSTRAINT TB_CUTOFF_CONFIG_CHK_ORD CHECK (START_TIME < END_TIME)
);

COMMENT ON TABLE TB_CUTOFF_CONFIG IS
    'Runtime-editable bank cut-off windows (refactor-only; seeded from application.yml). Source of truth for values; application.yml is the fallback floor. cutoff-config_20260709.sql';

-- -----------------------------------------------------------------------------
-- 2. TB_CUTOFF_PARAM — scalar params (keeps non-window scalars out of the windows table).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_CUTOFF_PARAM (
    PARAM_KEY      VARCHAR(50)  NOT NULL,             -- e.g. large-txn-threshold
    PARAM_VALUE    VARCHAR(50)  NOT NULL,             -- e.g. 500000000
    ENABLE_YN      CHAR(1)      NOT NULL DEFAULT 'Y',
    CHANGE_REASON  VARCHAR(500) NULL,
    REG_ID         VARCHAR(100) NULL,
    REG_DTM        VARCHAR(14)  NULL,
    MOD_ID         VARCHAR(100) NULL,
    MOD_DTM        VARCHAR(14)  NULL,
    CONSTRAINT TB_CUTOFF_PARAM_PK     PRIMARY KEY (PARAM_KEY),
    CONSTRAINT TB_CUTOFF_PARAM_CHK_EN CHECK (ENABLE_YN IN ('Y', 'N'))
);

COMMENT ON TABLE TB_CUTOFF_PARAM IS
    'Runtime-editable scalar cut-off params (e.g. large-txn-threshold). application.yml is the fallback floor. cutoff-config_20260709.sql';

-- -----------------------------------------------------------------------------
-- 3. TB_CUTOFF_CONFIG_HIS — append-only change history (rollback + audit spine).
--    HIS_SEQ_NO is a per-(BANK_KEY, NETWORK) increment; the app computes the next
--    value on each write. CHG_TYPE: C(reate) / U(pdate) / D(isable/delete).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS TB_CUTOFF_CONFIG_HIS (
    BANK_KEY       VARCHAR(20)   NOT NULL,
    NETWORK        VARCHAR(20)   NOT NULL,
    HIS_SEQ_NO     NUMERIC(10,0) NOT NULL,            -- per-key increment (app-managed)
    CHG_TYPE       CHAR(1)       NOT NULL,            -- C | U | D
    START_TIME     VARCHAR(5)    NULL,                -- snapshot (nullable so a 'D' can be recorded)
    END_TIME       VARCHAR(5)    NULL,
    WARNING_TIME   VARCHAR(5)    NULL,
    ENABLE_YN      CHAR(1)       NULL,
    CHANGE_REASON  VARCHAR(500)  NULL,
    REG_ID         VARCHAR(100)  NULL,
    REG_DTM        VARCHAR(14)   NULL,
    MOD_ID         VARCHAR(100)  NULL,                -- who made THIS change
    MOD_DTM        VARCHAR(14)   NULL,                -- when
    CONSTRAINT TB_CUTOFF_CONFIG_HIS_PK      PRIMARY KEY (BANK_KEY, NETWORK, HIS_SEQ_NO),
    CONSTRAINT TB_CUTOFF_CONFIG_HIS_CHK_CT  CHECK (CHG_TYPE IN ('C', 'U', 'D'))
);

COMMENT ON TABLE TB_CUTOFF_CONFIG_HIS IS
    'Append-only history of TB_CUTOFF_CONFIG changes (rollback + audit). cutoff-config_20260709.sql';

-- =============================================================================
-- SEED — exact current application.yml values (Phase 1 parity).
--   REG_ID/MOD_ID = 'SYSTEM'; REG_DTM/MOD_DTM = '20260709000000' (seed marker).
--   ON CONFLICT DO NOTHING → never overwrites a row later edited by DBA/admin.
-- =============================================================================

-- 4. Windows.
INSERT INTO TB_CUTOFF_CONFIG
    (BANK_KEY, NETWORK,       START_TIME, END_TIME, WARNING_TIME, ENABLE_YN, CHANGE_REASON,                                REG_ID,   REG_DTM,          MOD_ID,   MOD_DTM)
VALUES
    -- Shinhan
    ('SHINHAN', 'NAPAS',       '00:05',   '23:45',  '23:00',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('SHINHAN', 'CITAD',       '09:00',   '15:30',  '15:00',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('SHINHAN', 'CITAD_API',   '09:00',   '16:30',  '16:00',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('SHINHAN', 'SWIFT',       '09:00',   '22:00',  '18:30',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    -- Woori
    ('WOORI',   'NAPAS',       '00:15',   '23:55',  '23:00',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('WOORI',   'CITAD',       '08:30',   '17:40',  '17:10',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('WOORI',   'SWIFT',       '08:30',   '19:10',  '18:30',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    -- VCB (no SWIFT; CITAD_LARGE has no warning)
    ('VCB',     'NAPAS',       '00:05',   '23:45',  '23:00',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('VCB',     'CITAD',       '08:00',   '15:30',  '15:00',      'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('VCB',     'CITAD_LARGE', '08:00',   '16:30',  NULL,         'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000')
ON CONFLICT (BANK_KEY, NETWORK) DO NOTHING;

-- 5. Scalar params.
INSERT INTO TB_CUTOFF_PARAM
    (PARAM_KEY,            PARAM_VALUE,  ENABLE_YN, CHANGE_REASON,                                        REG_ID,   REG_DTM,          MOD_ID,   MOD_DTM)
VALUES
    ('large-txn-threshold', '500000000', 'Y',       'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000')
ON CONFLICT (PARAM_KEY) DO NOTHING;

-- 6. Initial history snapshot ('C' = created), HIS_SEQ_NO = 1 per key.
INSERT INTO TB_CUTOFF_CONFIG_HIS
    (BANK_KEY, NETWORK,       HIS_SEQ_NO, CHG_TYPE, START_TIME, END_TIME, WARNING_TIME, ENABLE_YN, CHANGE_REASON,                                        REG_ID,   REG_DTM,          MOD_ID,   MOD_DTM)
VALUES
    ('SHINHAN', 'NAPAS',       1, 'C', '00:05', '23:45', '23:00', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('SHINHAN', 'CITAD',       1, 'C', '09:00', '15:30', '15:00', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('SHINHAN', 'CITAD_API',   1, 'C', '09:00', '16:30', '16:00', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('SHINHAN', 'SWIFT',       1, 'C', '09:00', '22:00', '18:30', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('WOORI',   'NAPAS',       1, 'C', '00:15', '23:55', '23:00', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('WOORI',   'CITAD',       1, 'C', '08:30', '17:40', '17:10', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('WOORI',   'SWIFT',       1, 'C', '08:30', '19:10', '18:30', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('VCB',     'NAPAS',       1, 'C', '00:05', '23:45', '23:00', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('VCB',     'CITAD',       1, 'C', '08:00', '15:30', '15:00', 'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000'),
    ('VCB',     'CITAD_LARGE', 1, 'C', '08:00', '16:30', NULL,    'Y', 'Initial seed from application.yml (Phase 1 parity)', 'SYSTEM', '20260709000000', 'SYSTEM', '20260709000000')
ON CONFLICT (BANK_KEY, NETWORK, HIS_SEQ_NO) DO NOTHING;

COMMIT;

-- =============================================================================
-- VERIFICATION (run after COMMIT; expected counts in comments)
-- =============================================================================
-- SELECT COUNT(*) FROM TB_CUTOFF_CONFIG;      -- expect 10
-- SELECT COUNT(*) FROM TB_CUTOFF_PARAM;       -- expect 1
-- SELECT COUNT(*) FROM TB_CUTOFF_CONFIG_HIS;  -- expect 10
-- SELECT BANK_KEY, NETWORK, START_TIME, END_TIME, WARNING_TIME, ENABLE_YN
--   FROM TB_CUTOFF_CONFIG ORDER BY BANK_KEY, NETWORK;
--   -- Must match application.yml exactly (Shinhan CITAD 09:00-15:30, CITAD_API 09:00-16:30,
--   --  VCB CITAD 08:00-15:30, VCB CITAD_LARGE 08:00-16:30, threshold 500000000).

-- =============================================================================
-- ROLLBACK NOTES
-- =============================================================================
-- This script only ADDS new, unmapped tables — nothing existing is altered, so
-- rolling it back is low-risk and does NOT affect the running app (no entity is
-- mapped yet; the app still reads cut-off values from application.yml).
--
-- Full rollback (drops the tables — ONLY safe while no app code reads them yet,
-- i.e. before Phase 3 lands; and ONLY if you accept losing any DBA/admin edits
-- and the history):
--
--   BEGIN;
--   DROP TABLE IF EXISTS TB_CUTOFF_CONFIG_HIS;
--   DROP TABLE IF EXISTS TB_CUTOFF_PARAM;
--   DROP TABLE IF EXISTS TB_CUTOFF_CONFIG;
--   COMMIT;
--
-- Seed-only rollback (keep the empty tables, remove just the seeded rows):
--
--   BEGIN;
--   DELETE FROM TB_CUTOFF_CONFIG_HIS WHERE REG_ID = 'SYSTEM' AND REG_DTM = '20260709000000';
--   DELETE FROM TB_CUTOFF_PARAM      WHERE REG_ID = 'SYSTEM' AND REG_DTM = '20260709000000';
--   DELETE FROM TB_CUTOFF_CONFIG     WHERE REG_ID = 'SYSTEM' AND REG_DTM = '20260709000000';
--   COMMIT;
--
-- CAUTION: once Phase 3 (entity/provider) ships, DROP-ing TB_CUTOFF_CONFIG while
-- ddl-auto = validate is active will FAIL app startup (validate expects the mapped
-- table). At that point, revert the Phase 3 code FIRST, then drop the tables.
-- The application.yml fallback floor means dropping the tables never removes the
-- baseline values — the app degrades to pure YAML.
--
-- =============================================================================
-- NOT IN THIS SCRIPT (documented follow-ups)
-- =============================================================================
--   • TB_CUTOFF_PARAM_HIS (param change history) — deferred; add when the param
--     write path lands (this script's scope is the 3 named tables only).
--   • Per-tenant support (BIZ_KEY in the PK + GLOBAL sentinel) — future extension
--     per the recommendation §8; design the PK change as additive then.
--   • Entity / repository / CutoffConfigProvider / write endpoint / admin UI —
--     Phase 3+ (separate change; no app code in this script).
-- =============================================================================

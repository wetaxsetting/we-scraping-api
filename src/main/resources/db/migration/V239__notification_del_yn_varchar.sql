-- ============================================================================
-- V239__notification_del_yn_varchar.sql
--
-- Corrective hotfix for NOTIFICATION.DEL_YN type drift (2026-06-02).
--
-- V238 was applied to the shared dev DB with DEL_YN as CHAR(1)/bpchar before
-- the V238 file was edited to VARCHAR(1). Because V238 was already recorded in
-- flyway_schema_history, the edit never re-ran and the live column stayed
-- bpchar — so Hibernate `ddl-auto: validate` fails at startup with:
--   "wrong column type ... [del_yn] ... found [bpchar (Types#CHAR)],
--    but expecting [varchar(1) (Types#VARCHAR)]"
--
-- The entity maps DEL_YN as a plain String (no @JdbcTypeCode(SqlTypes.CHAR)),
-- and the refactor YN-normalisation precedent (V3 / V9 / V18 / V39 → V40) is
-- VARCHAR(1) for *_YN columns. Align the live column to that intent.
--
-- Idempotent: the DO block only ALTERs when the column is still bpchar, so a
-- fresh DB (where V238 already created VARCHAR(1)) and a re-run are both no-ops.
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'notification'
          AND column_name = 'del_yn'
          AND data_type = 'character'
    ) THEN
        ALTER TABLE NOTIFICATION
            ALTER COLUMN DEL_YN TYPE VARCHAR(1) USING DEL_YN::varchar;
    END IF;
END $$;

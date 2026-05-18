-- V152: Corrective — ensure APPR_STTS exists on OVRS_PYMN_SUMR.
--
-- V151 was recorded in flyway_schema_history but the ALTER TABLE did not
-- land on this DB (DevTools restart race: Flyway ran V151 on a classloader
-- that was discarded before the JDBC commit was visible to the next restart,
-- leaving the column absent). This migration is a safe re-application:
-- ADD COLUMN IF NOT EXISTS is idempotent — no-op if V151 did land elsewhere.

-- Guard: same as V151 — table is created in V156.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ovrs_pymn_sumr') THEN
        ALTER TABLE ovrs_pymn_sumr
            ADD COLUMN IF NOT EXISTS appr_stts VARCHAR(1);
    END IF;
END $$;

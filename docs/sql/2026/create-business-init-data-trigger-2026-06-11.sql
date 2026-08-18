-- WeCMS Manual Database Change
-- Date: 2026-06-11
-- Source: migrated from Claude Code (AI)/sql/wb_init_busi_data_autotrigger_20260611.sql
-- Related record: docs/references/database/manual-tenant-setup.md
-- Type: DDL
-- Execution: MANUAL
-- Target environments: TBD — original script does not name the environments
-- Executor: DBA TEAM
-- Rollback: Reversible with DROP TRIGGER; the underlying wb_init_busi_data function is left untouched
-- Verification: VERIFICATION CHECK REQUIRED — the original script carries no executable pre/post-check query
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- wb_init_busi_data_autotrigger_20260611.sql
--
-- PURPOSE
--   Make tenant initialization run AUTOMATICALLY when a new company row is
--   inserted into BUSI_INFM, reproducing the legacy JEX behavior where company
--   registration called  BusiInfm.initData()  ->  SELECT wb_init_busi_data(...)
--   in the same transaction.
--
--   The refactor has NO Spring company-creation flow to host an application-
--   level call (company provisioning is manual DBA SQL). This AFTER INSERT
--   trigger is the no-Java way to get legacy-like automatic execution while
--   keeping wb_init_busi_data() as the SINGLE SOURCE OF TRUTH for the actual
--   SYS -> BUSI copy logic. No initialization logic is duplicated here — the
--   trigger only forwards the row's keys to the existing function.
--
-- EXECUTION POLICY  (read before running)
--   * DBA-RUN, BY HAND. This is NOT a Flyway migration and must NOT be placed
--     in src/main/resources/db/migration/ (Flyway is disabled; ddl-auto=validate).
--   * Triggers are not validated by Hibernate, so this does not affect app
--     startup validation.
--   * Target schema: wecmsdevdb (the refactor DB). Verify you are connected to
--     the intended database before running — see RISKS below re: shared/live DB.
--   * Idempotent: re-running this script is safe (CREATE OR REPLACE function +
--     DROP TRIGGER IF EXISTS before CREATE TRIGGER).
--
-- PRECONDITIONS (verified 2026-06-11 against wecmsdevdb)
--   * Function wecmsdevdb.wb_init_busi_data(varchar,varchar,varchar) EXISTS.
--   * BUSI_INFM.biz_key  varchar(20)  NOT NULL
--     BUSI_INFM.reg_id   varchar(100) NOT NULL
--     BUSI_INFM.reg_dtm  varchar(14)  NOT NULL  (legacy yyyyMMddHHmmss string —
--                        passes straight into p_reg_dtm with no reformatting)
--   * Existing trigger on BUSI_INFM is ONLY  busi_infm_logtrigger  (audit log).
--     The new trigger name below does not collide with it.
--
-- RUN ORDER
--   1. (prereq) Ensure SYS master tables are seeded — at minimum SYS_ENV_SET_INFM
--      (FK parent of BUSI_ENV_SET_INFM). If a SYS table is empty, the matching
--      copy is simply a no-op (see "EMPTY SYS" note below).
--   2. Run this script once (creates the trigger function + trigger).
--   3. From then on every INSERT into BUSI_INFM auto-runs wb_init_busi_data().
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Trigger function — thin forwarder to wb_init_busi_data(). No copy logic.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION wecmsdevdb.busi_infm_init_data_tg()
    RETURNS trigger
    LANGUAGE plpgsql
AS $function$
BEGIN
    -- Runs in the SAME transaction as the BUSI_INFM INSERT (AFTER INSERT,
    -- statement-synchronous). The row already exists at this point, so the
    -- function's internal "select tax_id from busi_infm where biz_key = ..."
    -- resolves correctly. If wb_init_busi_data() raises, the BUSI_INFM insert
    -- rolls back too — atomic, exactly like legacy initData() in one tx.
    PERFORM wecmsdevdb.wb_init_busi_data(
        NEW.biz_key,
        COALESCE(NEW.reg_id, 'SYSTEM'),
        COALESCE(NEW.reg_dtm, to_char(now() AT TIME ZONE 'UTC', 'YYYYMMDDHH24MISS'))
    );
    RETURN NEW;  -- ignored for AFTER triggers, returned by convention
END;
$function$;

-- ----------------------------------------------------------------------------
-- 2. Trigger — AFTER INSERT only, per row. Never on UPDATE/DELETE.
--    Distinct name from busi_infm_logtrigger (audit). DROP-then-CREATE so the
--    script is idempotent (PostgreSQL < 14 has no CREATE OR REPLACE TRIGGER).
-- ----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS busi_infm_init_data_trigger ON wecmsdevdb.busi_infm;

CREATE TRIGGER busi_infm_init_data_trigger
    AFTER INSERT ON wecmsdevdb.busi_infm
    FOR EACH ROW
    EXECUTE FUNCTION wecmsdevdb.busi_infm_init_data_tg();

-- ----------------------------------------------------------------------------
-- 3. Post-install verification (run after creating the trigger)
-- ----------------------------------------------------------------------------
-- (a) Both triggers present, audit + init, no collision:
--   SELECT tgname FROM pg_trigger t
--     JOIN pg_class c ON c.oid=t.tgrelid
--     JOIN pg_namespace n ON n.oid=c.relnamespace
--    WHERE n.nspname='wecmsdevdb' AND c.relname='busi_infm' AND NOT t.tgisinternal;
--   -- expect: busi_infm_logtrigger, busi_infm_init_data_trigger
--
-- (b) Smoke test in a ROLLED-BACK transaction (does not persist):
--   BEGIN;
--     INSERT INTO wecmsdevdb.busi_infm (biz_key, tax_id, reg_id, reg_dtm, ...)
--     VALUES ('VN_VNM0004_999999', '0000000000', 'SYSTEM',
--             to_char(now() AT TIME ZONE 'UTC','YYYYMMDDHH24MISS'), ...);
--     SELECT count(*) FROM wecmsdevdb.busi_env_set_infm  WHERE biz_key='VN_VNM0004_999999';
--     SELECT count(*) FROM wecmsdevdb.busi_usag_set_infm WHERE biz_key='VN_VNM0004_999999';
--     SELECT count(*) FROM wecmsdevdb.trco_grp_bsc_cstm  WHERE biz_key='VN_VNM0004_999999';
--   ROLLBACK;
--   -- counts > 0 prove the trigger fired (assuming SYS tables are seeded).

-- ============================================================================
-- ROLLBACK (uninstall the trigger; leaves wb_init_busi_data() untouched)
-- ============================================================================
--   DROP TRIGGER IF EXISTS busi_infm_init_data_trigger ON wecmsdevdb.busi_infm;
--   DROP FUNCTION IF EXISTS wecmsdevdb.busi_infm_init_data_tg();

-- ============================================================================
-- NOTES / CAVEATS
-- ============================================================================
-- EMPTY SYS: wb_init_busi_data() copies whatever is in SYS_ENV_SET_INFM /
--   SYS_USAG_SET_INFM / TRCO_GRP_BSC_SYS. If a SYS table is empty (on the LOCAL
--   refactor DB, SYS_ENV_SET_INFM currently has 0 rows), the matching copy
--   inserts nothing — no error. Seed the SYS defaults before relying on the
--   trigger, or the tenant will still hit WBE2031 on first settings update.
--
-- FIRES ON EVERY INSERT: this trigger runs for ALL BUSI_INFM inserts, including
--   Flyway seed migrations and any data import — not just an interactive
--   "registration". Safe because wb_init_busi_data() is WHERE-NOT-EXISTS
--   idempotent (re-runs copy only missing rows), but it is broader than the
--   legacy app-level call which fired only from the registration screen.
--
-- LEGACY-PARITY MECHANISM DIFFERENCE: legacy JEX used an explicit application
--   call (BusiInfm.initData), not a DB trigger. This trigger achieves the same
--   OUTCOME (auto-initialized tenant) by a different mechanism. wb_init_busi_data()
--   remains the single source of truth for the copy logic either way.
--
-- FAILURE BLOCKS COMPANY CREATION: because the trigger is synchronous in the
--   insert transaction, an error inside wb_init_busi_data() rolls back the
--   BUSI_INFM insert. This matches legacy (initData ran in the same tx) — a
--   tenant is never half-created. Confirm the inserting DB role has INSERT
--   privilege on BUSI_ENV_SET_INFM / BUSI_USAG_SET_INFM(/_LANG_DETL) /
--   TRCO_GRP_BSC_CSTM / PGRO_BSAC_LNKN_INFM, or every company insert will fail.
--
-- SHARED / LIVE-JEX DB (IMPORTANT): if this trigger is ever applied to a DB
--   shared with the live JEX system (the pending "refactor runs against live
--   JEX DB" direction), then on a legacy registration BOTH the legacy app call
--   AND this trigger run wb_init_busi_data() — harmless (idempotent) but
--   redundant, and the trigger now also fires for the legacy system's inserts.
--   Adding a trigger to a shared BUSI_INFM is a global behavior change for both
--   systems — get PO/DBA sign-off before installing there. On the standalone
--   refactor DB (wecmsdevdb) this concern does not apply.
-- ============================================================================

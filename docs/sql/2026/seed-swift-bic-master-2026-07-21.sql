-- WeCMS Manual Database Change
-- Date: 2026-07-21
-- Source: migrated from Claude Code (AI)/sql/swift_bic_mst_seed_20260721.sql
-- Related record: TBD
-- Type: DML
-- Execution: MANUAL
-- Target environments: Refactor DB 172.168.5.172:5432/wecmsdevdb
-- Executor: DBA TEAM
-- Rollback: Reversible by deleting the loaded reference rows; the table holds global reference data only
-- Verification: pre/post-check queries are present in the original script below
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =====================================================================
-- swift_bic_mst_seed_20260721.sql
-- Feature : Settings > Contacts > Overseas — SWIFT BIC dropdown + Town Name
--           (refactor port of legacy comm_1007_001 / IDO.SWIFT_BIC_MST_R002)
-- Target  : refactor DB  172.168.5.172:5432/wecmsdevdb  (schema wecmsdevdb)
-- Source  : JEX DEV       (schema wecmsvndev.swift_bic_mst, ~99,060 rows)
-- Baseline: swift_bic_mst added to the table inventory 2026-07-21 (PO Mr. Jung).
--
-- ⚠ EXECUTED BY A DBA BY HAND — NOT by the application, NOT by Flyway.
--   Flyway is disabled in wecms-api (ddl-auto=validate); the schema is
--   external / pre-migrated. This script only LOADS reference data — it
--   creates no application-owned object and alters no existing row of any
--   other table.
--
-- Why a full load (not the 30-row migration cap): swift_bic_mst is a global
--   reference dictionary with no BIZ_KEY — the same documented exception
--   applied to bank_infm and sys_trco_infm_dis_nm. The BIC search must be
--   able to resolve any of the 99,060 codes, so all rows are loaded.
--
-- Idempotent: re-running inserts only codes not already present
--   (ON CONFLICT (bic_cd) DO NOTHING). Existing rows are never mutated.
-- =====================================================================

-- Preconditions (verify before running):
--   * Table wecmsdevdb.swift_bic_mst already exists (it does — physically
--     present but empty). This script does NOT create it. If it is somehow
--     missing, obtain the DDL from the JEX DEV table (column-for-column) and
--     apply it first — do not hand-author a divergent shape.
--   * PK exists on (bic_cd) so ON CONFLICT works. Confirm with:
--       \d wecmsdevdb.swift_bic_mst

-- ---------------------------------------------------------------------
-- RUN ORDER
-- ---------------------------------------------------------------------
--   STEP 1 (on the SOURCE, JEX DEV) — export the directory to a CSV file.
--   STEP 2 (on the TARGET, refactor DB) — load the CSV via a staging table
--           and INSERT ... SELECT ... ON CONFLICT DO NOTHING.
--   STEP 3 (on the TARGET) — verify the row count and a sample.
-- The two servers are distinct hosts, so this is an export→import; there is
-- no cross-database INSERT ... SELECT and no postgres_fdw dependency.
-- ---------------------------------------------------------------------


-- =====================================================================
-- STEP 1 — EXPORT (run with psql connected to JEX DEV)
-- =====================================================================
-- All 23 columns are exported (full reference dictionary — not a subset).
-- \copy writes client-side, so the path is on the machine running psql.
--
-- \copy (SELECT bic_cd, pstl_adr_dept, pstl_adr_sub_dept, pstl_adr_strt_nm,
--               pstl_adr_bldg_nb, pstl_adr_bldg_nm, pstl_adr_flr,
--               pstl_adr_pst_bx, pstl_adr_room, pstl_adr_pst_cd,
--               pstl_adr_twn_nm, pstl_adr_twn_lctn_nm, pstl_adr_dstrct_nm,
--               pstl_adr_ctry_sub_dvsn, pstl_adr_ctry, hyb_adr_line1,
--               hyb_adr_line2, inf_regis_dt, inf_regis_time,
--               reg_id, reg_dtm, mod_id, mod_dtm
--          FROM wecmsvndev.swift_bic_mst
--          ORDER BY bic_cd)
--   TO 'swift_bic_mst.csv' WITH (FORMAT csv, HEADER true);


-- =====================================================================
-- STEP 2 — LOAD (run with psql connected to the refactor DB, wecmsdevdb)
-- =====================================================================
BEGIN;

-- 2a. Staging table (session-local; dropped at COMMIT). Holds the raw CSV
--     so the final insert can dedupe against any rows already present.
CREATE TEMP TABLE tmp_swift_bic_mst
    (LIKE wecmsdevdb.swift_bic_mst INCLUDING DEFAULTS)
    ON COMMIT DROP;

-- 2b. Bulk-load the CSV into staging (client-side path — same file as STEP 1).
\copy tmp_swift_bic_mst (bic_cd, pstl_adr_dept, pstl_adr_sub_dept, pstl_adr_strt_nm, pstl_adr_bldg_nb, pstl_adr_bldg_nm, pstl_adr_flr, pstl_adr_pst_bx, pstl_adr_room, pstl_adr_pst_cd, pstl_adr_twn_nm, pstl_adr_twn_lctn_nm, pstl_adr_dstrct_nm, pstl_adr_ctry_sub_dvsn, pstl_adr_ctry, hyb_adr_line1, hyb_adr_line2, inf_regis_dt, inf_regis_time, reg_id, reg_dtm, mod_id, mod_dtm) FROM 'swift_bic_mst.csv' WITH (FORMAT csv, HEADER true);

-- 2c. Insert only codes not already present. NOT-NULL audit columns
--     (reg_id/reg_dtm/mod_id/mod_dtm) carry the table's own DEFAULT
--     ('SYSTEM' / UTC now) when the CSV value is null — the LIKE ...
--     INCLUDING DEFAULTS staging table + COALESCE guard covers both cases.
INSERT INTO wecmsdevdb.swift_bic_mst (
    bic_cd, pstl_adr_dept, pstl_adr_sub_dept, pstl_adr_strt_nm,
    pstl_adr_bldg_nb, pstl_adr_bldg_nm, pstl_adr_flr, pstl_adr_pst_bx,
    pstl_adr_room, pstl_adr_pst_cd, pstl_adr_twn_nm, pstl_adr_twn_lctn_nm,
    pstl_adr_dstrct_nm, pstl_adr_ctry_sub_dvsn, pstl_adr_ctry,
    hyb_adr_line1, hyb_adr_line2, inf_regis_dt, inf_regis_time,
    reg_id, reg_dtm, mod_id, mod_dtm)
SELECT
    bic_cd, pstl_adr_dept, pstl_adr_sub_dept, pstl_adr_strt_nm,
    pstl_adr_bldg_nb, pstl_adr_bldg_nm, pstl_adr_flr, pstl_adr_pst_bx,
    pstl_adr_room, pstl_adr_pst_cd, pstl_adr_twn_nm, pstl_adr_twn_lctn_nm,
    pstl_adr_dstrct_nm, pstl_adr_ctry_sub_dvsn, pstl_adr_ctry,
    hyb_adr_line1, hyb_adr_line2, inf_regis_dt, inf_regis_time,
    COALESCE(reg_id,  'SYSTEM'),
    COALESCE(reg_dtm, to_char(timezone('UTC', now()), 'YYYYMMDDHH24MISS')),
    COALESCE(mod_id,  'SYSTEM'),
    COALESCE(mod_dtm, to_char(timezone('UTC', now()), 'YYYYMMDDHH24MISS'))
FROM tmp_swift_bic_mst
WHERE bic_cd IS NOT NULL
ON CONFLICT (bic_cd) DO NOTHING;

COMMIT;


-- =====================================================================
-- STEP 3 — VERIFY (run on the refactor DB after COMMIT)
-- =====================================================================
-- Expect ~99,060 (matches JEX DEV as of 2026-07-21).
--   SELECT count(*) AS total_codes FROM wecmsdevdb.swift_bic_mst;
--
-- Spot-check the Town Name auto-fill source used by the popup:
--   SELECT bic_cd, pstl_adr_twn_nm
--     FROM wecmsdevdb.swift_bic_mst
--    WHERE bic_cd LIKE 'SHBK%'
--    ORDER BY bic_cd
--    LIMIT 20;
--
-- Confirm the search endpoint's prefix query returns <= 50 rows:
--   SELECT bic_cd, pstl_adr_twn_nm AS twn_nm
--     FROM wecmsdevdb.swift_bic_mst
--    WHERE bic_cd LIKE 'SHBKVN%'
--    ORDER BY bic_cd
--    LIMIT 50;
-- =====================================================================

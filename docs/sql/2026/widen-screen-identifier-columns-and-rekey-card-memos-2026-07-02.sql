-- WeCMS Manual Database Change
-- Date: 2026-07-02
-- Renamed from: docs/sql/web-svc-id-varchar-100-migration.sql (2026-08-17 — folder normalization)
-- Related record: docs/audits/governance/web-svc-id-route-migration-audit.md
-- Type: DDL+DML
-- Execution: MANUAL
-- Executor: DBA TEAM — execution is PO-gated (Mr. Jung); STEP 1 / STEP 2 NOT YET RUN
-- Target environments: LOCAL/DEV refactor (wecmsdevdb) · UAT / PROD live JEX schema
-- Rollback: documented in the ROLLBACK NOTES section at the end of this file
-- Verification: STEP 4 of this script carries the pre/post-check queries
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--   STEP 3 re-keys live MEMO_INFM rows on a database shared with the live JEX system;
--   the STEP 1 / STEP 2 widenings are forward-only in practice.
--
-- SUPERSEDES: Claude Code (AI)/sql/audit_logs_infm_wsvc_id_widen_20260609.sql — STEP 2
--   below performs the same audit_logs_infm.wsvc_id widening. That earlier one-off was
--   deliberately NOT migrated into docs/sql/2026/; this file is the governed deliverable.
-- OVERLAPS: docs/sql/2026/rekey-memo-web-service-id-card-transactions-2026-07-02.sql
--   performs the same operation as STEP 3. Run EITHER that script OR STEP 3 — never both
--   (see the note in STEP 3 below).
--
-- FILENAME NOTE: the original name said "widen ... varchar 100" only. This script also
--   carries a DML step (STEP 3, the Card Transactions memo re-key), so the name records
--   both halves.
--
-- DATE EVIDENCE: explicit "Date: 2026-07-02" in the original header below, corroborated by
--   its "verified 2026-07-02" and STATUS NOTE lines and by the commit that added the file
--   (759717a, 2026-07-02).
--
-- Nothing below this line was edited except three stale cross-reference paths inside
-- comments (this file's own name banner, the audit-doc path, and the sibling-script path).
-- SQL semantics are unchanged.
-- =============================================================================

-- =====================================================================
-- widen-screen-identifier-columns-and-rekey-card-memos-2026-07-02.sql
-- (was: web-svc-id-varchar-100-migration.sql)
-- ---------------------------------------------------------------------
-- Purpose : Widen the screen/route identifier columns to varchar(100) so
--           they can hold the modern route-based identifier the WeCMS
--           refactor now writes (e.g. the Card Transactions memo scope
--           `/payment/corporate-card/card-transactions`, 41 chars), and
--           (STEP 3, PO-GATED) migrate the one legacy memo row that still
--           carries the JEX screen id `card_0001_001`.
--
-- Author  : Claude Code (AI)  ·  Date: 2026-07-02
-- Target  : WeCMS DB (JEX legacy schema). Run connected to the target
--           database with the WeCMS schema on the search_path:
--             LOCAL/DEV refactor : wecmsdevdb   @ 172.168.5.172:5432/wecmsdevdb
--             UAT / PROD (JEX)   : the live JEX schema (e.g. wecmsvnprod)
--
-- >>> EXECUTION MODEL: MANUAL, BY A DBA. NOT a Flyway migration. <<<
--     Flyway is disabled in wecms-api (ddl-auto=validate; schema is
--     external/pre-migrated — see project_flyway_disabled_ddl_validate).
--     Do NOT place this under src/main/resources/db/migration/ as an
--     auto-applied file. Run it by hand, in the order below.
--
-- >>> APPROVAL GATE <<<
--     STEP 1/STEP 2 (DDL widen) and STEP 3 (data migrate) all alter LEGACY
--     JEX tables on a database SHARED with the live JEX system. Per
--     workspace CLAUDE.md ("Refactor DB & Legacy-Compatibility Rules"),
--     schema/data changes to legacy tables require explicit PO (Mr. Jung)
--     approval. STEP 3 additionally has a live-JEX coexistence impact —
--     see the audit doc risk section before running:
--       wecms-api/docs/audits/governance/web-svc-id-route-migration-audit.md
--
-- ---------------------------------------------------------------------
-- This file is organized by IDENTIFIER TYPE. The three identifier
-- families are kept in separate, independent sections — never mixed:
--
--   STEP 1     WEB_SVC_ID  (screen / page / route id)  — DDL
--   STEP 2     WSVC_ID     (audit-log screen id)        — DDL
--   STEP 3     WEB_SVC_ID  data migration               — DML  (PO-gated)
--   STEP 4     Verification
--   APPENDIX   SVC_ID      (excluded — investigation only, no DDL/DML)
--
-- DEPLOYMENT ORDER:
--   1. STEP 1  — WEB_SVC_ID DDL
--   2. STEP 2  — WSVC_ID DDL
--   3. STEP 3  — WEB_SVC_ID data migration (PO-approved only)
--   4. STEP 4  — Verification
-- =====================================================================



-- #####################################################################
-- STEP 1 — WEB_SVC_ID  (screen / page / route identifier)        [DDL]
-- #####################################################################
-- Widen every WEB_SVC_ID column to varchar(100).
--
-- SAFE: PostgreSQL treats an INCREASE of a varchar length limit as a
-- catalog-only change (>= PG 9.2) — NO table rewrite, NO index rebuild,
-- NO data loss. Nullability and DEFAULTs are preserved by ALTER ... TYPE.
-- All target columns participate in PRIMARY KEYs; widening the type does
-- NOT drop or rebuild the PK/unique indexes. There are NO foreign keys
-- referencing these columns (verified 2026-07-02), so no paired change
-- is required on any other table.
--
-- Idempotent: only columns that (a) exist in this schema AND (b) are
-- currently shorter than 100 are altered. Re-running is a no-op.
-- =====================================================================
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT tbl, col FROM (VALUES
            -- Table                     Column        -- Notes
            ('memo_infm',            'web_svc_id'),    -- refactor WRITES route here (MemoScope)
            ('memo_infm_file',       'web_svc_id'),    -- memo attachments (branch present, unwired)
            ('sys_scr_option_wecms', 'web_svc_id'),    -- View Setting system defaults (slugs)
            ('user_scr_option_wecms','web_svc_id'),    -- View Setting user overrides (slugs)
            ('menu_auth_admn_sys',   'web_svc_id'),    -- menu authorization catalogue
            ('user_menu_auth_infm',  'web_svc_id'),    -- per-user menu grants
            ('sys_dshbrd_infm',      'web_svc_id'),    -- dashboard (system)
            ('user_dshbrd_infm',     'web_svc_id'),    -- dashboard (per-user)
            ('ext_tran_call_his',    'web_svc_id')     -- external-call history (calling screen)
        ) AS t(tbl, col)
    LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name   = r.tbl
              AND column_name  = r.col
              AND character_maximum_length < 100
        ) THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE varchar(100)', r.tbl, r.col);
            RAISE NOTICE 'Widened %.% -> varchar(100)', r.tbl, r.col;
        ELSE
            RAISE NOTICE 'Skipped %.% (absent here, or already >= 100)', r.tbl, r.col;
        END IF;
    END LOOP;
END $$;


-- ---------------------------------------------------------------------
-- STEP 1b (OPTIONAL / EXTRA PO SIGN-OFF) — legacy-only JEX WEB_SVC_ID.
-- ---------------------------------------------------------------------
-- These WEB_SVC_ID columns exist on the LIVE JEX schema (UAT/PROD) but
-- are NOT written by the refactor and NOT in the refactor DB. The
-- refactor never stores a route in them today, so widening them is
-- OPTIONAL future-proofing only. Left COMMENTED OUT — enable only with
-- an explicit PO decision to touch pure-legacy columns. (Metadata-only,
-- same safety profile as STEP 1.)
--
-- DO $$
-- DECLARE r record;
-- BEGIN
--     FOR r IN SELECT tbl, col FROM (VALUES
--         ('busi_user_hide_menu_bar',   'web_svc_id'),
--         ('date_filter',               'web_svc_id'),
--         ('genl_noti_info',            'web_svc_id'),
--         ('scrn_field_infm',           'web_svc_id'),
--         ('user_menu_action_auth_infm','web_svc_id')
--     ) AS t(tbl, col)
--     LOOP
--         IF EXISTS (SELECT 1 FROM information_schema.columns
--                    WHERE table_schema=current_schema() AND table_name=r.tbl
--                      AND column_name=r.col AND character_maximum_length < 100) THEN
--             EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE varchar(100)', r.tbl, r.col);
--             RAISE NOTICE 'Widened %.% -> varchar(100)', r.tbl, r.col;
--         END IF;
--     END LOOP;
-- END $$;



-- #####################################################################
-- STEP 2 — WSVC_ID  (audit-log screen identifier)                [DDL]
-- #####################################################################
-- Widen the single WSVC_ID column, audit_logs_infm.wsvc_id, to
-- varchar(100). This column stores the audit-log route SLUG
-- (`firm-banking-settings`, `own-account-transfer`, `card-transactions`, …).
--
-- Motivation: on the legacy JEX schema this column is varchar(20), and
-- `firm-banking-settings` (21 chars) already OVERFLOWS 20. On the refactor
-- DB it is varchar(50). Widening to 100 fixes the overflow everywhere.
--
-- NO data migration and NO route/memo mapping belong here — WSVC_ID holds
-- slugs only (zero `card_0001_001` rows). Same catalog-only safety and
-- idempotency profile as STEP 1 (wsvc_id is part of the PK; widening does
-- not rebuild it).
-- =====================================================================
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT tbl, col FROM (VALUES
            ('audit_logs_infm', 'wsvc_id')            -- audit-log slug (varchar(20) on legacy JEX!)
        ) AS t(tbl, col)
    LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name   = r.tbl
              AND column_name  = r.col
              AND character_maximum_length < 100
        ) THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE varchar(100)', r.tbl, r.col);
            RAISE NOTICE 'Widened %.% -> varchar(100)', r.tbl, r.col;
        ELSE
            RAISE NOTICE 'Skipped %.% (absent here, or already >= 100)', r.tbl, r.col;
        END IF;
    END LOOP;
END $$;



-- #####################################################################
-- STEP 3 — WEB_SVC_ID  data migration                       [DML · PO-GATED]
-- #####################################################################
-- ⚠ PO-GATED + COEXISTENCE IMPACT. Run ONLY after STEP 1, and only once
--   the PO has signed off the memo-scope switch. On a database shared
--   with the LIVE JEX system, changing MEMO_INFM.WEB_SVC_ID away from
--   `card_0001_001` HIDES those memo threads from the still-live JEX
--   Card screen (which queries WEB_SVC_ID = 'card_0001_001'), and JEX
--   will keep creating NEW memos under `card_0001_001` that the refactor
--   (querying the route) will not see. See the audit doc risk section.
--
-- Re-scope Card Transactions memos:
--   card_0001_001  ->  /payment/corporate-card/card-transactions  (41 chars)
--
-- The new value is the SINGLE SOURCE OF TRUTH constant, not a literal:
--   com.kosign.wecms.common.memo.MemoScope.CARD_TRANSACTIONS.webSvcId()
--   == '/payment/corporate-card/card-transactions'
-- Keep this SQL literal in lockstep with that enum. Do NOT hardcode the
-- route anywhere else.
--
-- Data migration belongs ONLY to this section — WEB_SVC_ID is the only
-- family that ever stored the Card Transactions screen id. (WSVC_ID holds
-- slugs; SVC_ID is a different concept — see the appendix.)
--
-- Idempotent: WHERE web_svc_id = 'card_0001_001' — a re-run matches 0
-- rows. Applies only where the legacy value actually exists.
--
-- NOTE: This is the SAME operation as the pre-existing data-only script
--   "docs/sql/2026/rekey-memo-web-service-id-card-transactions-2026-07-02.sql"
--   (migrated 2026-08-17 from "Claude Code (AI)/sql/memo_web_svc_id_card_transactions_20260702.sql")
--   (wrapped in BEGIN/COMMIT + a RAISE EXCEPTION collision guard). Run
--   EITHER that script OR this STEP 3 — not both. STEP 3 is retained here
--   so this file is a self-contained runbook (DDL widen + data re-scope).
-- =====================================================================

-- 3a. COLLISION PRE-CHECK — run this FIRST. It MUST return ZERO rows, or
--     the UPDATE below would hit a PK violation (memo PK = biz_key,
--     web_svc_id, seq_no: a route row already exists with the same
--     biz_key + seq_no).
SELECT o.biz_key, o.seq_no
FROM memo_infm o
JOIN memo_infm n
  ON  n.biz_key = o.biz_key
  AND n.seq_no  = o.seq_no
  AND n.web_svc_id = '/payment/corporate-card/card-transactions'
WHERE o.web_svc_id = 'card_0001_001';

-- 3b. memo_infm  (PK: biz_key, web_svc_id, seq_no)
UPDATE memo_infm
   SET web_svc_id = '/payment/corporate-card/card-transactions'
 WHERE web_svc_id = 'card_0001_001';

-- 3c. memo_infm_file  (PK: biz_key, web_svc_id, seq_no, file_id)
--     Keep the child rows aligned with their parent thread.
UPDATE memo_infm_file
   SET web_svc_id = '/payment/corporate-card/card-transactions'
 WHERE web_svc_id = 'card_0001_001';



-- #####################################################################
-- STEP 4 — VERIFICATION  (read-only)
-- #####################################################################
-- 4a. Length check — run BEFORE (pre-flight) and AFTER STEP 1/STEP 2.
--     After the DDL every present column is varchar(100). Covers BOTH
--     the WEB_SVC_ID family and the WSVC_ID column.
SELECT table_name, column_name, character_maximum_length
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND ( (table_name = 'audit_logs_infm' AND column_name = 'wsvc_id')
     OR (column_name = 'web_svc_id' AND table_name IN (
            'memo_infm','memo_infm_file','sys_scr_option_wecms','user_scr_option_wecms',
            'menu_auth_admn_sys','user_menu_auth_infm','sys_dshbrd_infm','user_dshbrd_infm',
            'ext_tran_call_his')) )
ORDER BY table_name, column_name;

-- 4b. No memo rows still carry the legacy id (expected: 0 after STEP 3).
SELECT 'memo_infm' AS tbl, count(*) AS legacy_rows_remaining
FROM memo_infm WHERE web_svc_id = 'card_0001_001'
UNION ALL
SELECT 'memo_infm_file', count(*)
FROM memo_infm_file WHERE web_svc_id = 'card_0001_001';

-- 4c. Migrated rows are visible under the modern route.
SELECT web_svc_id, count(*) AS rows
FROM memo_infm
WHERE web_svc_id IN ('card_0001_001','/payment/corporate-card/card-transactions')
GROUP BY web_svc_id;



-- #####################################################################
-- APPENDIX — SVC_ID  (EXCLUDED — investigation only, NO DDL / NO DML)
-- #####################################################################
-- SVC_ID is a DIFFERENT concept from WEB_SVC_ID / WSVC_ID. These are
-- external / API / framework service identifiers, NOT screen identifiers.
-- Therefore they are intentionally excluded from this migration — none of
-- the columns below is altered.
--
--   * ext_tran_call_his.svc_id  — EXTERNAL integration service id
--       (values: GDT_INQUIRY_SALES / GDT_INQUIRY_EXPENSES / REAL_TIME_ONLINE_INQUIRY
--        / BI09). Written by EInvoiceScrapeService, filtered by EInvoiceHistoryService
--        (LIKE 'GDT_INQUIRY%'). PK column (svc_id, ref_date, seq_no). The SAME
--        table's web_svc_id holds the route slug 'addon-einvoices-scrape' — that
--        one IS widened in STEP 1; svc_id is NOT a screen/route id.
--   * api_tran_log_his.svc_id   — API-transaction-log service id, PK column
--       (trsc_dt, trsc_tm, user_id, svc_id). Not a screen/route id.
--   * jex_ad_lgin_trsc_log.svc_id / jex_ad_trsc_log.svc_id /
--     jex_ad_trsc_str_log.svc_id — JEX FRAMEWORK admin transaction logs
--       (jex_* tables, excluded by CLAUDE.md exclude-rule 1; not in the refactor
--        DB; not written by the refactor). jex_ad_lgin_trsc_log.svc_id is already
--        varchar(100) anyway.
--
-- Also NOT altered (not SVC_ID, but noted for completeness):
--   * bltb_noti_infm.noti_wsvc      — already varchar(100).
--   * menu_item.legacy_wsvc_id      — already varchar(100); refactor-owned,
--                                     intentionally stores the LEGACY id.
--   * sys_scr_option, sys_scr_option_new, user_scr_option, user_scr_option_new,
--     and every *_i<NNNNN> / *_<NNNNN> table — legacy old-version / IMP
--     backup snapshots, out of refactor scope.
--
-- STATUS NOTE (refactor DB, 2026-07-02): the STEP 3 memo re-scope has ALREADY
--   been applied on 172.168.5.172/wecmsdevdb — memo_infm now holds the route
--   '/payment/corporate-card/card-transactions' and ZERO 'card_0001_001' rows
--   remain in ANY web_svc_id / wsvc_id / svc_id column. STEP 3 below stays for
--   UAT/PROD DBs that may still carry the legacy id; it is a safe no-op where
--   already applied.



-- =====================================================================
-- ROLLBACK NOTES
-- =====================================================================
-- STEP 1 / STEP 2 (widen) — effectively irreversible in practice and NOT
--   worth reverting: narrowing varchar(100) back to varchar(20)/(50)
--   forces a full table rewrite AND fails if any stored value already
--   exceeds the smaller limit (which it will, once routes/slugs are
--   written). Treat the widening as forward-only. If a revert is truly
--   mandated:
--     ALTER TABLE <t> ALTER COLUMN <c> TYPE varchar(50)
--       USING left(<c>, 50);   -- ⚠ truncates; only if no value > 50.
--
-- STEP 3 (data) — reversible while the live JEX system has not yet
--   created new memos under the route. To undo:
--     UPDATE memo_infm      SET web_svc_id='card_0001_001'
--       WHERE web_svc_id='/payment/corporate-card/card-transactions';
--     UPDATE memo_infm_file SET web_svc_id='card_0001_001'
--       WHERE web_svc_id='/payment/corporate-card/card-transactions';
--   (Same PK-collision caveat in reverse; run the mirror of STEP 3a.)
--   NOTE: reverting the DB also requires reverting MemoScope.CARD_TRANSACTIONS
--   back to 'card_0001_001' so the refactor and the data agree.
-- =====================================================================

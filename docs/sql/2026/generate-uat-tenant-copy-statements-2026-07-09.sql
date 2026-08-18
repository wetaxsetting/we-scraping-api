-- WeCMS Manual Database Change
-- Date: 2026-07-09
-- Source: migrated from Claude Code (AI)/sql/uat_tenant_copy_VN_VNM0004_000019_20260709.sql
-- Related record: notes/uat-prod-tenant-copy-plan.md (path as cited by the original script header)
-- Type: QUERY (generator template; emits INSERT...SELECT for DBA review)
-- Execution: MANUAL
-- Target environments: Generates statements for a PROD to UAT tenant copy; gated on written PO / data-owner sign-off
-- Executor: DBA TEAM
-- Rollback: Not applicable to the generator itself; the generated statements are run inside one transaction by the DBA
-- Verification: pre/post-check queries are present in the original script below
-- TEMPLATE — this script carries placeholders that a DBA must fill in before execution. Do not run it as-is.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =====================================================================
-- UAT Tenant Copy — Option A (whole-tenant copy with biz_key remap + masking)
-- Source (PROD):  VN_VNM0004_000002
-- Target (UAT):   VN_VNM0004_000019   (must be UNUSED in UAT)
-- Author: Claude Code (AI)  Date: 2026-07-09
--
-- HOW THIS WORKS:
--   This is a GENERATOR + DBA-run template, NOT a blind runnable script.
--   Step 3 generates the per-table INSERT...SELECT statements from the live
--   schema (all biz_key base tables, minus backups, with remap + masking).
--   The DBA REVIEWS the generated SQL, then runs it inside one transaction.
--
-- RUN BY: DBA, by hand. NOT Flyway, NOT the application.
--
-- ⚠️ GATES (do first — see notes/uat-prod-tenant-copy-plan.md):
--   1. Written PO / data-owner sign-off (real customer data leaving PROD).
--   2. UAT gateways (bank / e-invoice / payment) are SANDBOX, not live.
--   3. VN_VNM0004_000019 confirmed unused in UAT.
--
-- ⚠️ VERIFY BEFORE RUNNING:
--   - Schema name on UAT/PROD (this template assumes 'wecmsvndev' — confirm).
--   - Column names re-verified against PROD (derived from DEV on 2026-07-09).
-- =====================================================================


-- ---------------------------------------------------------------------
-- STEP 0. Confirm the target biz_key is free (run on UAT)
-- ---------------------------------------------------------------------
SELECT count(*) AS must_be_zero
FROM wecmsvndev.busi_infm
WHERE biz_key = 'VN_VNM0004_000019';


-- ---------------------------------------------------------------------
-- STEP 1. Cross-server link: postgres_fdw from UAT -> PROD (read-only)
--   (Alternative: dblink, or pg_dump of the filtered set. fdw shown here.)
--   Run on the UAT database as a privileged role.
-- ---------------------------------------------------------------------
-- CREATE EXTENSION IF NOT EXISTS postgres_fdw;
-- CREATE SERVER prod_src_srv FOREIGN DATA WRAPPER postgres_fdw
--   OPTIONS (host '<PROD_HOST>', port '<PROD_PORT>', dbname '<PROD_DB>');
-- CREATE USER MAPPING FOR CURRENT_USER SERVER prod_src_srv
--   OPTIONS (user '<PROD_READONLY_USER>', password '<...>');   -- read-only creds
-- CREATE SCHEMA IF NOT EXISTS prod_src;
-- IMPORT FOREIGN SCHEMA wecmsvndev FROM SERVER prod_src_srv INTO prod_src;
--   -- foreign tables now readable as prod_src.<table>


-- ---------------------------------------------------------------------
-- STEP 2. (reference) The include-list rule
--   All BASE tables carrying biz_key, EXCLUDING ad-hoc backup/snapshot tables.
--   The generator in STEP 3 applies this filter automatically.
-- ---------------------------------------------------------------------


-- ---------------------------------------------------------------------
-- STEP 3. GENERATE the INSERT...SELECT statements (run on UAT; capture output)
--   Review the generated SQL, then execute it (STEP 4).
--   Masking is baked into the SELECT so real secrets NEVER land in UAT.
-- ---------------------------------------------------------------------
WITH params AS (
  SELECT 'VN_VNM0004_000002'::text AS src_biz,
         'VN_VNM0004_000019'::text AS tgt_biz,
         'wecmsvndev'::text        AS uat_schema,   -- VERIFY
         'prod_src'::text          AS src_schema
),
-- (table, column) -> masking expression. Columns not listed pass through as-is.
-- Expressions reference the source alias "s".  Re-verify column names on PROD.
maskmap(tbl, col, expr) AS (
  VALUES
    -- bank / tax / sns e-certificates: never carry live credentials
    ('bank_cert_info','cert_info_con','NULL'),
    ('bank_cert_info','cert_info_detl_con','NULL'),
    ('bank_cert_info','cert_info_detl_con2','NULL'),
    ('bank_cert_info','cert_info_detl_con3','NULL'),
    ('gdt_cert_info','cert_info_con','NULL'),
    ('gdt_cert_info','cert_info_detl_con','NULL'),
    ('gdt_cert_info','cert_info_detl_con2','NULL'),
    ('gdt_cert_info','cert_info_detl_con3','NULL'),
    ('sns_cert_info','cert_info_con','NULL'),
    ('sns_cert_info','cert_info_detl_con','NULL'),
    ('sns_cert_info','cert_info_detl_con2','NULL'),
    -- account master: passwords + embedded cert + account number
    ('acct_mstr','acct_pwd','NULL'),
    ('acct_mstr','trns_pwd','NULL'),
    ('acct_mstr','cert_info_con','NULL'),
    ('acct_mstr','acct_no',
       'repeat(''*'', greatest(char_length(s.acct_no)-4,0)) || right(s.acct_no,4)'),
    -- account numbers elsewhere (mask all but last 4)
    ('acct_auth_info','acct_no',
       'repeat(''*'', greatest(char_length(s.acct_no)-4,0)) || right(s.acct_no,4)'),
    ('trco_acct_info','acct_no',
       'repeat(''*'', greatest(char_length(s.acct_no)-4,0)) || right(s.acct_no,4)'),
    ('busi_sal_acct_infm','acct_no',
       'repeat(''*'', greatest(char_length(s.acct_no)-4,0)) || right(s.acct_no,4)'),
    ('busi_infm','acct_no',
       'repeat(''*'', greatest(char_length(s.acct_no)-4,0)) || right(s.acct_no,4)'),
    ('empl_infm','acct_no',
       'repeat(''*'', greatest(char_length(s.acct_no)-4,0)) || right(s.acct_no,4)'),
    -- card master
    ('card_mstr','card_no',
       'repeat(''*'', greatest(char_length(s.card_no)-4,0)) || right(s.card_no,4)'),
    ('card_mstr','pymn_acct_no',
       'repeat(''*'', greatest(char_length(s.pymn_acct_no)-4,0)) || right(s.pymn_acct_no,4)'),
    -- API keys
    ('busi_api_key_infm','api_key','NULL'),
    -- PII: phones / DOB -> dummy
    ('busi_infm','tel_no','''0000000000'''),
    ('empl_infm','tel_no','''0000000000'''),
    ('empl_infm','clph_no','''0000000000'''),
    ('empl_infm','emer_clph','''0000000000'''),
    ('empl_infm','birth_date','left(coalesce(s.birth_date,''19900101''),4) || ''0101'''),
    ('user_infm','clph_no','''0000000000'''),
    ('tax_agent_infm','clph_no','''0000000000''')
),
tbls AS (   -- base tables carrying biz_key, minus ad-hoc backup/snapshot tables
  SELECT DISTINCT c.table_name
  FROM information_schema.columns c
  JOIN information_schema.tables t
    ON t.table_schema = c.table_schema AND t.table_name = c.table_name
   AND t.table_type = 'BASE TABLE'
  CROSS JOIN params p
  WHERE c.table_schema = p.uat_schema
    AND c.column_name = 'biz_key'
    AND c.table_name !~ '_(\d{4,}|i\d+|20\d{6})(_\d+)?$'   -- _35135, _i42630, _20230502...
    AND c.table_name !~ '(issue|noobdev|_new)$'
    AND c.table_name <> 'sys_doc_id_gen'                    -- handled separately (STEP 5)
),
cols AS (
  SELECT c.table_name, c.ordinal_position, c.column_name,
    CASE
      WHEN c.column_name IN ('biz_key','wta_biz_key','snd_biz_key','trco_wabooks_biz_key')
        THEN quote_literal(p.tgt_biz)
      WHEN m.expr IS NOT NULL THEN m.expr
      ELSE 's.' || quote_ident(c.column_name)
    END AS sel_expr
  FROM information_schema.columns c
  JOIN tbls USING (table_name)
  CROSS JOIN params p
  LEFT JOIN maskmap m ON m.tbl = c.table_name AND m.col = c.column_name
  WHERE c.table_schema = p.uat_schema
)
SELECT
  'INSERT INTO ' || quote_ident(p.uat_schema) || '.' || quote_ident(table_name) || ' (' ||
  string_agg(quote_ident(column_name), ', ' ORDER BY ordinal_position) ||
  ') SELECT ' ||
  string_agg(sel_expr, ', ' ORDER BY ordinal_position) ||
  ' FROM ' || quote_ident(p.src_schema) || '.' || quote_ident(table_name) ||
  ' s WHERE s.biz_key = ' || quote_literal(p.src_biz) || ';'  AS stmt
FROM cols CROSS JOIN params p
GROUP BY table_name, p.uat_schema, p.src_schema, p.src_biz
ORDER BY table_name;


-- ---------------------------------------------------------------------
-- STEP 4. RUN the generated statements — inside ONE transaction on UAT.
--   FK ordering: either run masters first (busi_infm, user_infm, account
--   masters) then children, OR bulk-load with triggers deferred:
--
--   BEGIN;
--     SET session_replication_role = replica;   -- suspend FK checks for load
--     <paste generated INSERT...SELECT statements here>
--     SET session_replication_role = DEFAULT;   -- re-enable
--     -- sanity check FK integrity before COMMIT
--   COMMIT;
-- ---------------------------------------------------------------------


-- ---------------------------------------------------------------------
-- STEP 5. Copy the key-generation counter table (remapped) so wb_get_next_val
--   continues correctly for the new tenant.
-- ---------------------------------------------------------------------
-- INSERT INTO wecmsvndev.sys_doc_id_gen (<col list>)
-- SELECT <cols, biz_key replaced by 'VN_VNM0004_000019'>
-- FROM prod_src.sys_doc_id_gen s
-- WHERE s.biz_key = 'VN_VNM0004_000002';
--   (use the STEP 3 generator pattern for the exact column list)


-- ---------------------------------------------------------------------
-- STEP 6. Ms. Kim's admin login.
--   The tenant's users were copied in STEP 4 (with clph_no masked).
--   Create ONE new, unique admin user id for her — do NOT reuse a PROD hash.
--   Password uses the legacy hashing function (SHA-256 via public.hash()).
-- ---------------------------------------------------------------------
-- -- 6a. verify chosen user_id is free (globally unique):
-- SELECT count(*) AS must_be_zero FROM wecmsvndev.user_infm WHERE user_id = '<new_admin_id>';
--
-- -- 6b. insert the admin user row (copy the shape of an existing admin in this tenant;
-- --     set biz_key='VN_VNM0004_000019', user_id='<new_admin_id>', a fresh password hash,
-- --     admin role/flags). Then add:
-- --       user_busi_conn_infm   (user <-> company link)
-- --       user_menu_auth_infm / user_menu_action_auth_infm  (full menu access)
-- --     Model these rows on an existing admin of the copied tenant.


-- ---------------------------------------------------------------------
-- STEP 7. Verification (on UAT)
--   - Log in at https://test.wecms.vn/ as <new_admin_id> -> data renders.
--   - Open a bank/cert screen: NO real certificate decrypts (cert cols are NULL).
--   - Confirm firm-banking / payment dispatch cannot reach a live gateway.
--   - Spot-check: acct_no / card_no show masked, phones are dummy.
-- ---------------------------------------------------------------------
SELECT 'acct_mstr' t, count(*) rows FROM wecmsvndev.acct_mstr WHERE biz_key='VN_VNM0004_000019'
UNION ALL SELECT 'user_infm', count(*) FROM wecmsvndev.user_infm WHERE biz_key='VN_VNM0004_000019'
UNION ALL SELECT 'busi_infm', count(*) FROM wecmsvndev.busi_infm WHERE biz_key='VN_VNM0004_000019';

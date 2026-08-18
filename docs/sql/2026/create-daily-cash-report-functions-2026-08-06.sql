-- WeCMS Manual Database Change
-- Date: 2026-08-06
-- Source: migrated from Claude Code (AI)/sql/rept_0018_001_20260806.sql
-- Related record: docs/jex/features/daily-cash-report/phase-1/10-db-functions-sql.md (path as cited by the original script header)
-- Type: DDL
-- Execution: MANUAL
-- Target environments: Per environment (Phase 1.5 DDL request); the original script is revision r2 and instructs re-running where r1 was already applied
-- Executor: DBA TEAM
-- Rollback: ROLLBACK REQUIRES DBA REVIEW — the script DROPs and re-creates functions; the prior definitions are not captured in it
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
-- MIGRATION REVIEW REQUIRED — this script creates a new table and/or DB function that has no counterpart in src/main/resources/db/migration/. No migration was created and no V-number was assigned during the archive migration; the placement decision is open.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =====================================================================================
--  K-1  ·  PHASE 1.5 DDL REQUEST  ·  Daily Cash Report (rept_0018_001)
-- =====================================================================================
--  Requested by : Sonny (Ran Sokny)          Date : 2026-08-06
--  Approver     : Mr. Jung  (PO) / DBA
--  Feature doc  : docs/jex/features/daily-cash-report/phase-1/10-db-functions-sql.md
--
--  REVISION r2 -- 2026-08-06.  IF r1 WAS ALREADY RUN ON AN ENVIRONMENT, RE-RUN THIS.
--    * Account-scoped SEARCH added: rec1/rec2/rec3 gain p_srch_str (rec4 already had it).
--      A term keeps an ACCOUNT when the account itself matches OR any of its
--      transactions in the period matches; the summary keeps that account's row and the
--      details keep ALL of that account's rows.  Keeping only the matching detail rows
--      would break the mandatory RC-01/RC-02 reconciliation.
--    * rec4 gains 3 output columns -- ACCT_TYPE_CD, ACCT_TYPE_NM, BANK_ACCOUNT -- so one
--      function serves both export section 2.2 layouts (PPT slide-7 vs the delivered
--      template) without a further DB change.
--
--  >>> THIS SCRIPT DROPS rec1..rec4 BEFORE RE-CREATING THEM. <<<
--    CREATE OR REPLACE cannot add a parameter (it makes an overload, and the old 4-arg
--    call then fails "function ... is not unique") and cannot change RETURNS TABLE
--    ("cannot change return type of existing function").  rec5 is unchanged, not dropped.
--
--  WHAT THIS ASKS FOR
--    FIVE read-only, set-returning functions:
--        rept_0018_001_rec1   section A  - Cash on Hand
--        rept_0018_001_rec2   section B  - Demand Deposits   (the live Phase-1 section)
--        rept_0018_001_rec3   section C  - Time Deposits
--        rept_0018_001_rec4   Daily Transaction Details (export section 2)
--        rept_0018_001_rec5   report header (company / tax code / address)
--
--  WHAT IT DOES *NOT* ASK FOR
--    * No table, column, index, view, trigger or sequence change.
--    * No INSERT / UPDATE / DELETE anywhere in any body -- these are read-only.
--    * The only DROPs are of our own functions, created 2026-08-06.  Nothing else
--      references them: no IDO, WSVC or _act.jsp is wired up yet.
--
--  DEPENDENCIES (all already present -- verified read-only on JEX DEV 2026-08-06)
--    tables    : ACCT_MSTR, ACCT_TRNS_HSTR, CASH_REPY_MSTR, BUSI_INFM
--    functions : wb_get_amt(numeric, varchar), wb_get_bank_nm(varchar, varchar)
--    NOTE      : ACCT_TRNS_RCV_PAY_INFM is deliberately NOT used.
--
--  ⚠ PARAMETER ORDER IS NOT THE SAME ON ALL FUNCTIONS
--        rec2/rec3 : (biz_key, from_dt, to_dt, ccy_cd, account_filter, srch_str)
--        rec4      : (biz_key, from_dt, to_dt, ccy_cd, srch_str, account_filter)
--    Both are varchar, so a swap fails no type check.  Prefer named notation.
--
--  EXECUTION
--    Run by the DBA, by hand, as ONE transaction (BEGIN/COMMIT are included).
--    Set search_path to the environment schema first, e.g. on DEV:
--        SET search_path TO wecmsvndev, public;
--    Deploy DEV -> UAT -> PROD, paired with the WAR release (risk R-12/R-13).
--    Re-running is safe: the DROPs use IF EXISTS and the CREATEs use OR REPLACE.
--
--  VERIFY AFTER RUNNING
--    Section 6 of the feature doc (V-1 .. V-10).  Test tenant on DEV:
--        VN_VNM0004_000001  (CONG TY TNHH WEBCASH VIET NAM, PRDT_TYPE = F)
--    Use a range that HAS data: 20260101 - 20260630 (236 detail rows).
--    That tenant has NO transactions after 20260630, so a July/August range
--    correctly returns zero detail rows -- that is EXC-01, not a bug.
--    WeCMS is the VN_VNM0004 BIZ_KEY group only -- VN_VNM0001 / VN_VNM0002 are
--    other products sharing the same database.  Do not verify against those.
--
--  ROLLBACK
--    DROP FUNCTION IF EXISTS rept_0018_001_rec1(varchar,varchar,varchar,varchar,varchar);
--    DROP FUNCTION IF EXISTS rept_0018_001_rec2(varchar,varchar,varchar,varchar,varchar,varchar);
--    DROP FUNCTION IF EXISTS rept_0018_001_rec3(varchar,varchar,varchar,varchar,varchar,varchar);
--    DROP FUNCTION IF EXISTS rept_0018_001_rec4(varchar,varchar,varchar,varchar,varchar,varchar);
--    DROP FUNCTION IF EXISTS rept_0018_001_rec5(varchar,varchar,varchar);
-- =====================================================================================


-- =====================================================================================
--  Daily Cash Report  ·  rept_0018_001  ·  read-only report functions
--  Target : JEX  (DEV -> UAT -> PROD, Phase 1.5 DDL flow, DBA-run)
--  Author : Sonny (Ran Sokny)            Date : 2026-08-06
--  Ref    : docs/jex/features/daily-cash-report/phase-1/10-db-functions-sql.md
--
--  DBA NOTE
--    * These are READ-ONLY set-returning functions. No DML anywhere in the bodies.
--    * Tables are referenced UNQUALIFIED, exactly as rept_0900_001_rec1 does.
--      Set search_path to the environment schema before running, e.g. on DEV:
--          SET search_path TO wecmsvndev, public;
--    * Depends on existing helpers: wb_get_amt(numeric, varchar)
--                                   wb_get_bank_nm(varchar, varchar)
--    * Depends on existing tables : ACCT_MSTR, ACCT_TRNS_HSTR, CASH_REPY_MSTR, BUSI_INFM
--      (ACCT_TRNS_RCV_PAY_INFM is NOT used -- removed 2026-08-06, see finding D-1)
--    * Run order: rec1, rec2, rec3, rec4, rec5 (independent; any order works).
--    * Rollback : DROP FUNCTION rept_0018_001_rec1(varchar,varchar,varchar,varchar);
--                 ... see section 8 of the reference document.
-- =====================================================================================

BEGIN;

-- =====================================================================================
-- !! DROP FIRST -- r4 CHANGES SIGNATURES AND RETURN TYPES !!
--
--   CREATE OR REPLACE CANNOT do either of these in PostgreSQL:
--     * add a parameter  -> it creates an OVERLOAD, and a 4-arg call then fails with
--                           "function rept_0018_001_rec1(...) is not unique"
--     * change RETURNS TABLE -> "cannot change return type of existing function"
--
--   rec1/rec2/rec3 gain p_srch_str; rec4 gains 3 output columns. So all four must be
--   dropped first.  rec5 is unchanged and is NOT dropped.
--   Safe: these are our own objects, created 2026-08-06, and nothing else references them.
-- =====================================================================================
DROP FUNCTION IF EXISTS rept_0018_001_rec1(varchar,varchar,varchar,varchar);
DROP FUNCTION IF EXISTS rept_0018_001_rec2(varchar,varchar,varchar,varchar,varchar);
DROP FUNCTION IF EXISTS rept_0018_001_rec3(varchar,varchar,varchar,varchar,varchar);
DROP FUNCTION IF EXISTS rept_0018_001_rec4(varchar,varchar,varchar,varchar,varchar,varchar);

-- =====================================================================================
-- 1) rept_0018_001_rec1  --  SECTION A : CASH ON HAND
--     Source  : CASH_REPY_MSTR (WABOOKS Cash Book ledger) -- verified present, D-3
--     Rule    : Deposit = SUM(Credit) = INOUT_DV '1'   (money in)
--               Expenditure = SUM(Debit) = INOUT_DV '2' (money out)   [OQ-DC-3, slide 4]
--               Prev = signed balance BEFORE p_from_dt   (= Cash Book [Opening Balance])
--               Bal  = signed balance THROUGH p_to_dt    (= Cash Book [Closing Balance])
--     Layout  : one row per currency. NO TOTAL ROW -- F-21 / DC-25b.
--     Phase 1 : c_phase1_blank = TRUE -> rows render, amounts NULL.
-- =====================================================================================
CREATE OR REPLACE FUNCTION rept_0018_001_rec1(
    p_biz_key  varchar,
    p_from_dt  varchar,
    p_to_dt    varchar,
    p_ccy_cd   varchar DEFAULT '',
    p_srch_str varchar DEFAULT ''          -- ACCOUNT-SCOPED SEARCH, see the note in rec2
)
RETURNS TABLE(
    row_type     varchar,
    sect_cd      varchar,
    ccy_cd       varchar,
    content      varchar,
    acct_no      varchar,
    prev_bal     numeric,
    deposit      numeric,
    expenditure  numeric,
    balance      numeric,
    disp_ord     numeric
)
LANGUAGE plpgsql
AS $function$
#variable_conflict use_column                  -- how to resolve conflicts
DECLARE
    -- ---------------------------------------------------------------------------------
    -- PHASE 1 SWITCH.  TRUE  = row skeleton only, all amounts NULL  (shipped default)
    --                  FALSE = live Cash Book figures               (flip at Phase 2a)
    -- Approved by OQ-DC-21 / F-28: sections A and C ship EMPTY in Phase 1.
    -- ---------------------------------------------------------------------------------
    c_phase1_blank CONSTANT boolean := TRUE;
BEGIN
    RETURN QUERY (
        SELECT t.row_type
             , t.sect_cd
             , t.ccy_cd
             , t.content
             , t.acct_no
             , CASE WHEN c_phase1_blank THEN NULL::numeric ELSE t.prev_bal    END
             , CASE WHEN c_phase1_blank THEN NULL::numeric ELSE t.deposit     END
             , CASE WHEN c_phase1_blank THEN NULL::numeric ELSE t.expenditure END
             , CASE WHEN c_phase1_blank THEN NULL::numeric ELSE t.balance     END
             , t.disp_ord
        FROM (
            WITH ccy AS (
                -- Currencies present in this tenant's Cash Book.
                -- NOTE: the legacy rept_0900_001_rec3 omits the biz_key filter here
                --       (cross-tenant leak, finding D-4). Scoped correctly below.
                SELECT DISTINCT c.ccy_cd
                  FROM cash_repy_mstr c
                 WHERE c.biz_key = p_biz_key
                   AND (p_ccy_cd IS NULL OR p_ccy_cd = '' OR c.ccy_cd = p_ccy_cd)
                   -- Search: keep a currency if the currency itself matches, or any of
                   -- its Cash Book entries in the period matches.
                   AND ( p_srch_str IS NULL OR p_srch_str = ''
                      OR c.ccy_cd       ILIKE '%' || p_srch_str || '%'
                      OR 'CASH ON HAND' ILIKE '%' || p_srch_str || '%'
                      OR EXISTS (
                            SELECT 1
                              FROM cash_repy_mstr c2
                             WHERE c2.biz_key = p_biz_key
                               AND c2.ccy_cd  = c.ccy_cd
                               AND c2.trsc_dt BETWEEN p_from_dt AND p_to_dt
                               AND ( COALESCE(c2.cont,'') ILIKE '%' || p_srch_str || '%'
                                  OR c2.trsc_dt           ILIKE '%' || p_srch_str || '%'
                                  OR (c2.trsc_dt ~ '^\d{8}$' AND
                                      to_char(to_date(c2.trsc_dt,'YYYYMMDD'),'DD/MM/YYYY')
                                                          ILIKE '%' || p_srch_str || '%')
                                  OR c2.trsc_amt::text    ILIKE '%' || p_srch_str || '%' )) )
            )
            SELECT 'DATA'::varchar                       AS row_type
                 , 'A'::varchar                          AS sect_cd
                 , trim(ccy.ccy_cd)::varchar             AS ccy_cd
                 , 'CASH ON HAND'::varchar               AS content   -- FS v1.0 3.3.1 C3
                 , NULL::varchar                         AS acct_no   -- section A has none
                 -- [Opening Balance] : signed running balance strictly BEFORE p_from_dt
                 , wb_get_amt(COALESCE((
                       SELECT SUM(CASE WHEN c.inout_dv = '2' THEN -c.trsc_amt ELSE c.trsc_amt END)
                         FROM cash_repy_mstr c
                        WHERE c.biz_key = p_biz_key
                          AND c.ccy_cd  = ccy.ccy_cd
                          AND c.trsc_dt < p_from_dt), 0), ccy.ccy_cd)   AS prev_bal
                 -- Deposit = SUM(Credit) = money IN
                 , wb_get_amt(COALESCE((
                       SELECT SUM(c.trsc_amt)
                         FROM cash_repy_mstr c
                        WHERE c.biz_key = p_biz_key
                          AND c.ccy_cd  = ccy.ccy_cd
                          AND c.inout_dv = '1'
                          AND c.trsc_dt BETWEEN p_from_dt AND p_to_dt), 0), ccy.ccy_cd) AS deposit
                 -- Expenditure = SUM(Debit) = money OUT
                 , wb_get_amt(COALESCE((
                       SELECT SUM(c.trsc_amt)
                         FROM cash_repy_mstr c
                        WHERE c.biz_key = p_biz_key
                          AND c.ccy_cd  = ccy.ccy_cd
                          AND c.inout_dv = '2'
                          AND c.trsc_dt BETWEEN p_from_dt AND p_to_dt), 0), ccy.ccy_cd) AS expenditure
                 -- [Closing Balance] : signed running balance THROUGH p_to_dt
                 , wb_get_amt(COALESCE((
                       SELECT SUM(CASE WHEN c.inout_dv = '2' THEN -c.trsc_amt ELSE c.trsc_amt END)
                         FROM cash_repy_mstr c
                        WHERE c.biz_key = p_biz_key
                          AND c.ccy_cd  = ccy.ccy_cd
                          AND c.trsc_dt <= p_to_dt), 0), ccy.ccy_cd)    AS balance
                 , ROW_NUMBER() OVER (
                       ORDER BY CASE trim(ccy.ccy_cd)
                                    WHEN 'VND' THEN 1 WHEN 'USD' THEN 2
                                    WHEN 'KRW' THEN 3 ELSE 99 END
                              , ccy.ccy_cd )::numeric                   AS disp_ord
              FROM ccy
        ) t
        ORDER BY t.disp_ord
    );
END;
$function$;


-- =====================================================================================
-- 2) rept_0018_001_rec2  --  SECTION B : DEMAND DEPOSITS   *** the live Phase-1 section ***
--     Source  : ACCT_MSTR + ACCT_TRNS_HSTR
--     Balance : AS OF p_to_dt.  The CASE below is lifted VERBATIM from
--               IDO.ACCT_MSTR_R004.xml:91-130 with :START_DATE := p_to_dt.
--               Do NOT re-derive it -- risk R-15.  Three parts must survive intact:
--                 (a) bank 11704004 (Vietcombank) uses the SUBTRACT-FORWARD branch
--                 (b) tie-break is cast(ACCT_SEQ_NO AS NUMERIC) DESC, not a string sort
--                 (c) COALESCE(...,0) -> a dormant account shows 0, not its current BAL
--     Prev    : Balance - Deposit + Expenditure   (derived, never stored)  [FS C5 / N-8]
--     Layout  : grouped by currency, TOTAL row per currency group.
-- =====================================================================================
-- -------------------------------------------------------------------------------------
--  ACCOUNT-SCOPED SEARCH  (decided 2026-08-06, closes conflict C-4)
--
--  The filter bar has only Year + Date Range + Search, and the SCREEN shows only the
--  summary. If the search filtered the details alone, typing would do nothing visible.
--  If it filtered each record set independently, the two would stop agreeing and the
--  MANDATORY reconciliation tests RC-01/RC-02 would fail.
--
--  Rule: a term keeps an ACCOUNT when the account itself matches (bank name, account
--  no, currency) OR ANY of its transactions in the period matches (description, date,
--  amount). The summary keeps that account's row and the details keep ALL of that
--  account's rows -- never just the matching ones, or the detail sums would no longer
--  equal the summary's Deposit/Expenditure.
--
--  Verified on DEV before implementing: term 'INTEREST', 20260101-20260630,
--  VN_VNM0004_000001 -> 2 rows contain the term -> 1 account matches -> 95 detail rows
--  kept -> detail IN sum 1,807,244,160 == rec2 Deposit for that account. RC-01 holds.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION rept_0018_001_rec2(
    p_biz_key         varchar,
    p_from_dt         varchar,
    p_to_dt           varchar,
    p_ccy_cd          varchar DEFAULT '',
    p_account_filter  varchar DEFAULT 'ALL',
    p_srch_str        varchar DEFAULT ''
)
RETURNS TABLE(
    row_type      varchar,
    sect_cd       varchar,
    ccy_cd        varchar,
    content       varchar,
    bank_cd       varchar,
    acct_no       varchar,
    bank_nm       varchar,
    acct_type_cd  varchar,
    prev_bal      numeric,
    deposit       numeric,
    expenditure   numeric,
    balance       numeric,
    prn_seq       numeric,
    disp_ord      numeric
)
LANGUAGE plpgsql
AS $function$
#variable_conflict use_column
BEGIN
    RETURN QUERY (
        SELECT t.row_type, t.sect_cd, t.ccy_cd, t.content, t.bank_cd, t.acct_no
             , t.bank_nm, t.acct_type_cd, t.prev_bal, t.deposit, t.expenditure
             , t.balance, t.prn_seq, t.disp_ord
        FROM (
            WITH acct AS (
                -- ---- accounts in scope --------------------------------------------
                SELECT m.biz_key
                     , m.bank_cd
                     , m.acct_no
                     , trim(m.ccy_cd)::varchar                        AS ccy_cd
                     , m.ccy_cd                                       AS ccy_raw
                     , trim(m.acct_type_cd)::varchar                  AS acct_type_cd
                     , m.prn_seq
                     , m.bal                                          AS mstr_bal
                     -- DC-30: never print null when BANK_INFM has no row
                     , COALESCE( NULLIF(wb_get_bank_nm(m.bank_cd,'s'), '')
                               , NULLIF(wb_get_bank_nm(m.bank_cd,'l'), '')
                               , 'Bank ' || m.bank_cd )::varchar      AS bank_nm
                  FROM acct_mstr m
                 WHERE m.biz_key = p_biz_key
                   AND m.actv_yn = 'Y'                                -- DC-27
                   AND m.acct_type_cd IN ('03','04','05','06','07','08')
                   AND (p_ccy_cd IS NULL OR p_ccy_cd = '' OR m.ccy_cd = p_ccy_cd)
                   -- per-account permission gate, house pattern (finding D-5)
                   AND ( p_account_filter = 'ALL'
                      OR EXISTS ( SELECT 1
                                    FROM jsonb_array_elements(p_account_filter::jsonb) f
                                   WHERE (f->>'BANK_CD')::varchar = m.bank_cd
                                     AND (f->>'ACCT_NO')::varchar = m.acct_no ) )
                   -- ===== ACCOUNT-SCOPED SEARCH (see the block above the signature) =====
                   AND ( p_srch_str IS NULL OR p_srch_str = ''
                      -- (a) the account itself
                      OR COALESCE(NULLIF(wb_get_bank_nm(m.bank_cd,'s'), '')
                                , NULLIF(wb_get_bank_nm(m.bank_cd,'l'), '')
                                , 'Bank ' || m.bank_cd)  ILIKE '%' || p_srch_str || '%'
                      OR m.acct_no        ILIKE '%' || p_srch_str || '%'
                      OR trim(m.ccy_cd)   ILIKE '%' || p_srch_str || '%'
                      -- (b) any of its transactions in the period
                      OR EXISTS (
                            SELECT 1
                              FROM acct_trns_hstr s
                             WHERE s.biz_key = m.biz_key
                               AND s.bank_cd = m.bank_cd
                               AND s.acct_no = m.acct_no
                               AND s.trsc_dt BETWEEN p_from_dt AND p_to_dt
                               AND ( -- #37692 description -- KEEP IDENTICAL to rec4's copy
                                     COALESCE(
                                       CASE
                                         WHEN s.bank_cd = '11704020'
                                              THEN CASE WHEN s.inout_dv = '2' THEN s.rmrk2 ELSE s.rmrk END
                                         WHEN btrim(COALESCE(s.rmrk2,'')) <> ''
                                          AND btrim(COALESCE(s.rmrk ,'')) <> ''
                                              THEN s.rmrk2 || ' - ' || s.rmrk
                                         WHEN btrim(COALESCE(s.rmrk2,'')) <> '' THEN s.rmrk2
                                         ELSE s.rmrk
                                       END, '')          ILIKE '%' || p_srch_str || '%'
                                  OR s.trsc_dt           ILIKE '%' || p_srch_str || '%'
                                  OR (s.trsc_dt ~ '^\d{8}$' AND
                                      to_char(to_date(s.trsc_dt,'YYYYMMDD'),'DD/MM/YYYY')
                                                         ILIKE '%' || p_srch_str || '%')
                                  OR s.trsc_amt::text    ILIKE '%' || p_srch_str || '%' )) )
            ),
            sums AS (
                -- ---- period movement per account ----------------------------------
                -- INOUT_DV: '1' = Deposit, '2' = Withdrawal, '0' = ETC (see OPEN-3)
                SELECT h.bank_cd
                     , h.acct_no
                     , h.ccy_cd
                     , SUM(CASE WHEN h.inout_dv = '1' THEN wb_get_amt(h.trsc_amt, h.ccy_cd) ELSE 0 END) AS deposit
                     , SUM(CASE WHEN h.inout_dv = '2' THEN wb_get_amt(h.trsc_amt, h.ccy_cd) ELSE 0 END) AS expenditure
                  FROM acct_trns_hstr h
                 WHERE h.biz_key = p_biz_key
                   AND h.trsc_dt BETWEEN p_from_dt AND p_to_dt   -- varchar(8) string compare
                 GROUP BY h.bank_cd, h.acct_no, h.ccy_cd
            ),
            rows AS (
                SELECT a.ccy_cd
                     , a.bank_nm                                       AS content
                     , a.bank_cd
                     , a.acct_no
                     , a.bank_nm
                     , a.acct_type_cd
                     , a.prn_seq
                     , COALESCE(s.deposit, 0)                          AS deposit
                     , COALESCE(s.expenditure, 0)                      AS expenditure
                     -- ===== AS-OF BALANCE : lifted from IDO.ACCT_MSTR_R004.xml ======
                     , wb_get_amt(
                         CASE
                             WHEN p_to_dt IS NULL OR p_to_dt = ''
                                  OR p_to_dt >= to_char(CURRENT_DATE, 'YYYYMMDD')
                             THEN COALESCE(a.mstr_bal, 0)                      -- live snapshot
                             ELSE
                               CASE
                                 WHEN a.bank_cd = '11704004' THEN              -- VIETCOMBANK
                                     -- start from current balance, subtract everything AFTER the date
                                     COALESCE(a.mstr_bal, 0) -
                                     COALESCE((
                                         SELECT SUM(CASE WHEN x.inout_dv = '1'
                                                         THEN  x.trsc_amt
                                                         ELSE -x.trsc_amt END)
                                           FROM acct_trns_hstr x
                                          WHERE x.biz_key = a.biz_key
                                            AND x.bank_cd = a.bank_cd
                                            AND x.acct_no = a.acct_no
                                            AND x.ccy_cd  = a.ccy_raw
                                            AND x.trsc_dt > p_to_dt), 0)
                                 ELSE                                          -- EVERY OTHER BANK
                                     -- running balance of the latest row on or before the date
                                     COALESCE((
                                         SELECT x.bal
                                           FROM acct_trns_hstr x
                                          WHERE x.biz_key = a.biz_key
                                            AND x.bank_cd = a.bank_cd
                                            AND x.acct_no = a.acct_no
                                            AND x.ccy_cd  = a.ccy_raw
                                            AND x.trsc_dt <= p_to_dt
                                          ORDER BY x.trsc_dt DESC
                                                 , cast(x.acct_seq_no AS NUMERIC) DESC
                                          LIMIT 1), 0)
                               END
                         END, a.ccy_raw)                               AS balance
                     -- ===============================================================
                  FROM acct a
                  LEFT JOIN sums s
                         ON s.bank_cd = a.bank_cd
                        AND s.acct_no = a.acct_no
                        AND s.ccy_cd  = a.ccy_raw
            ),
            ranked AS (
                SELECT r.*
                     , CASE r.ccy_cd WHEN 'VND' THEN 1 WHEN 'USD' THEN 2
                                     WHEN 'KRW' THEN 3 ELSE 99 END     AS ccy_rank
                  FROM rows r
            ),
            body AS (
                -- ---- data rows -----------------------------------------------------
                SELECT 'DATA'::varchar        AS row_type
                     , 'B'::varchar           AS sect_cd
                     , k.ccy_cd
                     , k.content
                     , k.bank_cd
                     , k.acct_no
                     , k.bank_nm
                     , k.acct_type_cd
                     , (k.balance - k.deposit + k.expenditure)::numeric AS prev_bal
                     , k.deposit
                     , k.expenditure
                     , k.balance
                     , k.prn_seq
                     , k.ccy_rank
                     , 1                      AS kind_rank
                     , k.prn_seq              AS sk_prn
                     , k.bank_cd              AS sk_bank
                     , k.acct_no              AS sk_acct
                  FROM ranked k

                UNION ALL

                -- ---- one TOTAL row per currency group  (F-21 / DC-25) --------------
                SELECT 'TOTAL'::varchar
                     , 'B'::varchar
                     , k.ccy_cd
                     , ('Total (' || k.ccy_cd || ')')::varchar          -- see OPEN-2 (label)
                     , NULL::varchar
                     , NULL::varchar
                     , NULL::varchar
                     , NULL::varchar
                     , SUM(k.balance - k.deposit + k.expenditure)::numeric
                     , SUM(k.deposit)::numeric
                     , SUM(k.expenditure)::numeric
                     , SUM(k.balance)::numeric
                     , NULL::numeric
                     , k.ccy_rank
                     , 2                      AS kind_rank
                     , NULL::numeric          AS sk_prn
                     , NULL::varchar          AS sk_bank
                     , NULL::varchar          AS sk_acct
                  FROM ranked k
                 GROUP BY k.ccy_cd, k.ccy_rank
            )
            SELECT b.row_type, b.sect_cd, b.ccy_cd, b.content, b.bank_cd, b.acct_no
                 , b.bank_nm, b.acct_type_cd, b.prev_bal, b.deposit, b.expenditure
                 , b.balance, b.prn_seq
                 , ROW_NUMBER() OVER (
                       ORDER BY b.ccy_rank, b.kind_rank
                              , b.sk_prn NULLS LAST, b.sk_bank, b.sk_acct )::numeric AS disp_ord
              FROM body b
        ) t
        ORDER BY t.disp_ord
    );
END;
$function$;


-- =====================================================================================
-- 3) rept_0018_001_rec3  --  SECTION C : TIME DEPOSITS
--     Source  : ACCT_MSTR WHERE ACCT_TYPE_CD = '02'  (AcctTypeCd.DEPOSIT)   -- F-32
--               No new table, no data migration.
--     Balance : ACCT_MSTR.BAL -- the Time Deposit list's Balance column, NOT
--               Subscription Amount and NOT Installment Amount.  See OPEN-4 for why
--               this deliberately does NOT reuse section B's as-of CASE.
--     Prev/Dep/Exp : template rule -- F = J, H = 0, I = 0   [OQ-DC-14]
--     Term    : DERIVED from NEW_DT..EXPI_DT.  Format rule is OQ-DC-25 (still open);
--               the implementation below is a documented placeholder.
--     Layout  : grouped by currency, TOTAL row per currency group.
--     Phase 1 : c_phase1_blank = TRUE -> rows render, amounts NULL.
-- =====================================================================================
CREATE OR REPLACE FUNCTION rept_0018_001_rec3(
    p_biz_key         varchar,
    p_from_dt         varchar,
    p_to_dt           varchar,
    p_ccy_cd          varchar DEFAULT '',
    p_account_filter  varchar DEFAULT 'ALL',
    p_srch_str        varchar DEFAULT ''    -- account-only: time deposits have no txns
)
RETURNS TABLE(
    row_type     varchar,
    sect_cd      varchar,
    ccy_cd       varchar,
    content      varchar,
    bank_cd      varchar,
    acct_no      varchar,
    bank_nm      varchar,
    prev_bal     numeric,
    deposit      numeric,
    expenditure  numeric,
    balance      numeric,
    term         varchar,
    dpst_dt      varchar,
    clos_dt      varchar,
    disp_ord     numeric
)
LANGUAGE plpgsql
AS $function$
#variable_conflict use_column
DECLARE
    c_phase1_blank CONSTANT boolean := TRUE;   -- see rec1 header
BEGIN
    RETURN QUERY (
        SELECT t.row_type, t.sect_cd, t.ccy_cd, t.content, t.bank_cd, t.acct_no, t.bank_nm
             , CASE WHEN c_phase1_blank THEN NULL::numeric ELSE t.prev_bal    END
             , CASE WHEN c_phase1_blank THEN NULL::numeric ELSE t.deposit     END
             , CASE WHEN c_phase1_blank THEN NULL::numeric ELSE t.expenditure END
             , CASE WHEN c_phase1_blank THEN NULL::numeric ELSE t.balance     END
             , CASE WHEN c_phase1_blank THEN NULL::varchar ELSE t.term        END
             , CASE WHEN c_phase1_blank THEN NULL::varchar ELSE t.dpst_dt     END
             , CASE WHEN c_phase1_blank THEN NULL::varchar ELSE t.clos_dt     END
             , t.disp_ord
        FROM (
            WITH acct AS (
                SELECT m.bank_cd
                     , m.acct_no
                     , trim(m.ccy_cd)::varchar                        AS ccy_cd
                     , m.ccy_cd                                       AS ccy_raw
                     , m.prn_seq
                     , wb_get_amt(COALESCE(m.bal, 0), m.ccy_cd)       AS balance
                     , NULLIF(m.new_dt , '')                          AS dpst_dt
                     , NULLIF(m.expi_dt, '')                          AS clos_dt
                     , COALESCE( NULLIF(wb_get_bank_nm(m.bank_cd,'s'), '')
                               , NULLIF(wb_get_bank_nm(m.bank_cd,'l'), '')
                               , 'Bank ' || m.bank_cd )::varchar      AS bank_nm
                  FROM acct_mstr m
                 WHERE m.biz_key = p_biz_key
                   AND m.actv_yn = 'Y'
                   AND m.acct_type_cd = '02'                          -- AcctTypeCd.DEPOSIT
                   AND (p_ccy_cd IS NULL OR p_ccy_cd = '' OR m.ccy_cd = p_ccy_cd)
                   AND ( p_account_filter = 'ALL'
                      OR EXISTS ( SELECT 1
                                    FROM jsonb_array_elements(p_account_filter::jsonb) f
                                   WHERE (f->>'BANK_CD')::varchar = m.bank_cd
                                     AND (f->>'ACCT_NO')::varchar = m.acct_no ) )
            ),
            ranked AS (
                SELECT a.*
                     , CASE a.ccy_cd WHEN 'VND' THEN 1 WHEN 'USD' THEN 2
                                     WHEN 'KRW' THEN 3 ELSE 99 END    AS ccy_rank
                     -- ---------------------------------------------------------------
                     -- TERM  --  OQ-DC-25 PLACEHOLDER, confirm the wording with the PO.
                     -- Rule implemented: whole months between NEW_DT and EXPI_DT;
                     --   exact multiple of 12  -> 'N year' / 'N years'
                     --   >= 1 month            -> 'N months'
                     --   otherwise             -> 'N days'
                     -- ---------------------------------------------------------------
                     , CASE
                         WHEN a.dpst_dt IS NULL OR a.clos_dt IS NULL
                              OR a.dpst_dt !~ '^\d{8}$' OR a.clos_dt !~ '^\d{8}$'
                         THEN NULL::varchar
                         ELSE (
                           WITH d AS (
                             SELECT ( (EXTRACT(YEAR  FROM age(to_date(a.clos_dt,'YYYYMMDD'),
                                                              to_date(a.dpst_dt,'YYYYMMDD'))) * 12)
                                    +  EXTRACT(MONTH FROM age(to_date(a.clos_dt,'YYYYMMDD'),
                                                              to_date(a.dpst_dt,'YYYYMMDD'))) )::int AS mm
                                  , ( to_date(a.clos_dt,'YYYYMMDD')
                                    - to_date(a.dpst_dt,'YYYYMMDD') )::int                           AS dd
                           )
                           SELECT CASE
                                    WHEN d.mm >= 12 AND d.mm % 12 = 0 AND d.mm = 12
                                         THEN '1 year'
                                    WHEN d.mm >= 12 AND d.mm % 12 = 0
                                         THEN (d.mm / 12)::text || ' years'
                                    WHEN d.mm >= 1
                                         THEN d.mm::text || ' months'
                                    ELSE d.dd::text || ' days'
                                  END
                             FROM d )::varchar
                       END                                             AS term
                  FROM acct a
            ),
            matched AS (
                -- Account-scoped search. A time deposit has no transactions, so the
                -- term is matched against the deposit's own attributes only.
                SELECT r.*
                  FROM ranked r
                 WHERE p_srch_str IS NULL OR p_srch_str = ''
                    OR r.bank_nm                    ILIKE '%' || p_srch_str || '%'
                    OR r.acct_no                    ILIKE '%' || p_srch_str || '%'
                    OR r.ccy_cd                     ILIKE '%' || p_srch_str || '%'
                    OR COALESCE(r.term    ,'')      ILIKE '%' || p_srch_str || '%'
                    OR COALESCE(r.dpst_dt ,'')      ILIKE '%' || p_srch_str || '%'
                    OR COALESCE(r.clos_dt ,'')      ILIKE '%' || p_srch_str || '%'
                    OR r.balance::text              ILIKE '%' || p_srch_str || '%'
            ),
            body AS (
                SELECT 'DATA'::varchar   AS row_type
                     , 'C'::varchar      AS sect_cd
                     , k.ccy_cd
                     , k.bank_nm         AS content
                     , k.bank_cd
                     , k.acct_no
                     , k.bank_nm
                     , k.balance         AS prev_bal      -- OQ-DC-14 : F = J
                     , 0::numeric        AS deposit       -- OQ-DC-14 : H = 0
                     , 0::numeric        AS expenditure   -- OQ-DC-14 : I = 0
                     , k.balance
                     , k.term
                     , k.dpst_dt::varchar
                     , k.clos_dt::varchar
                     , k.ccy_rank
                     , 1                 AS kind_rank
                     , k.prn_seq         AS sk_prn
                     , k.bank_cd         AS sk_bank
                     , k.acct_no         AS sk_acct
                  FROM matched k

                UNION ALL

                SELECT 'TOTAL'::varchar
                     , 'C'::varchar
                     , k.ccy_cd
                     , ('Total (' || k.ccy_cd || ')')::varchar
                     , NULL::varchar, NULL::varchar, NULL::varchar
                     , SUM(k.balance)::numeric
                     , 0::numeric
                     , 0::numeric
                     , SUM(k.balance)::numeric
                     , NULL::varchar, NULL::varchar, NULL::varchar
                     , k.ccy_rank
                     , 2
                     , NULL::numeric, NULL::varchar, NULL::varchar
                  FROM matched k
                 GROUP BY k.ccy_cd, k.ccy_rank
            )
            SELECT b.row_type, b.sect_cd, b.ccy_cd, b.content, b.bank_cd, b.acct_no, b.bank_nm
                 , b.prev_bal, b.deposit, b.expenditure, b.balance
                 , b.term, b.dpst_dt, b.clos_dt
                 , ROW_NUMBER() OVER (
                       ORDER BY b.ccy_rank, b.kind_rank
                              , b.sk_prn NULLS LAST, b.sk_bank, b.sk_acct )::numeric AS disp_ord
              FROM body b
        ) t
        ORDER BY t.disp_ord
    );
END;
$function$;


-- =====================================================================================
-- 4) rept_0018_001_rec4  --  DAILY TRANSACTION DETAILS  (export section 2)
--     Row order is FIXED server-side (FS v1.0 3.3.2) and carried in ROW_TYPE:
--        GROUP -> DATA... -> ACCT_TOTAL -> ... -> GROUP_TOTAL -> ... -> TOTAL
--     Accounts appear in the SAME order as the summary table.
--
--     *** DESCRIPTION = RMRK / RMRK2 via the #37692 rule  (reversed 2026-08-06) ***
--     NOT ACCT_TRNS_RCV_PAY_INFM.CONT -- that column is populated on only 2.0% of
--     transactions (finding D-1).  RMRK/RMRK2 live on ACCT_TRNS_HSTR itself, so:
--         * NO ACCT_TRNS_RCV_PAY_INFM join anywhere in this function
--         * the SPLIT_YN fan-out risk is gone BY CONSTRUCTION, not worked around
--         * one bank transaction ALWAYS produces exactly one detail row (RC-01/RC-02)
--
--     The SQL CASE below is the exact SQL form of the Java in
--     fnsh_0002_001_r001_act.jsp:70-84 (and the FCY twin fnsh_0006_001_r001_act.jsp).
--     The rule now exists in TWO languages and they MUST agree character-for-character
--     -- OQ-DC-23 is live again; test DS-05b asserts it.  Do not "tidy" the separator:
--     it is exactly space-hyphen-space, from MessageFormat "{0} - {1}".
--
--     CONTACT_NAME is returned as NULL: RCV_PAY_TRCO_NM resolves on only 5.0% of
--     transactions and OQ-DC-15 removed the column from the export.  The join needed
--     to revive it is preserved as a commented block below -- see X-5.
--
--     Phase 1 : section A details are gated by c_incl_sect_a (same switch as rec1).
--               Section C emits no detail rows -- time deposits have no transactions.
-- =====================================================================================
CREATE OR REPLACE FUNCTION rept_0018_001_rec4(
    p_biz_key         varchar,
    p_from_dt         varchar,
    p_to_dt           varchar,
    p_ccy_cd          varchar DEFAULT '',
    p_srch_str        varchar DEFAULT '',
    p_account_filter  varchar DEFAULT 'ALL'
)
RETURNS TABLE(
    row_type      varchar,
    sect_cd       varchar,
    div           varchar,
    bank_cd       varchar,
    acct_no       varchar,
    ccy_cd        varchar,
    in_amt        numeric,
    out_amt       numeric,
    description   varchar,
    trsc_dt       varchar,
    contact_name  varchar,
    disp_ord      numeric,
    -- ---------------------------------------------------------------------------------
    -- ADDED 2026-08-06 so ONE function serves BOTH export §2.2 layouts (conflict X-5):
    --   PPT slide-7 text : Division = "Account Type", plus a separate "Bank Account"
    --                      column formatted Bank Name || (Currency) || Account.
    --   Delivered template: 분류 DIV = "{Bank} - {Account}"  (what DIV above returns).
    -- The export writer picks; no DB change is needed whichever way the PO rules.
    -- Account-type labels follow fn_get_haengsung_daily_financial_report.
    -- ---------------------------------------------------------------------------------
    acct_type_cd  varchar,
    acct_type_nm  varchar,
    bank_account  varchar,
    -- ---------------------------------------------------------------------------------
    -- RAW #37692 INPUTS.  DESCRIPTION above is already computed, so the IDO normally
    -- ignores these.  They are returned because OQ-DC-23 option (b) -- "apply the rule
    -- in _act.jsp instead" -- requires BANK_CD, INOUT_DV, RMRK and RMRK2 on every row.
    -- Returning both forms means whichever way OQ-DC-23 lands, this function is correct
    -- and no DB redeploy is needed.  BANK_CD is already a first-class column above.
    -- ---------------------------------------------------------------------------------
    trsc_tm       varchar,
    acct_seq_no   varchar,
    inout_dv      varchar,
    rmrk          varchar,
    rmrk2         varchar
)
LANGUAGE plpgsql
AS $function$
#variable_conflict use_column
DECLARE
    c_incl_sect_a CONSTANT boolean := FALSE;   -- Phase 2a: flip to TRUE with rec1
BEGIN
    RETURN QUERY (
        SELECT t.row_type, t.sect_cd, t.div, t.bank_cd, t.acct_no, t.ccy_cd
             , t.in_amt, t.out_amt, t.description, t.trsc_dt, t.contact_name, t.disp_ord
             , t.acct_type_cd, t.acct_type_nm, t.bank_account
             , t.trsc_tm, t.acct_seq_no, t.inout_dv, t.rmrk, t.rmrk2
        FROM (
            WITH base AS (
                -- ================= SECTION B : bank transactions =====================
                SELECT 'B'::varchar                                   AS sect_cd
                     , h.bank_cd
                     , h.acct_no
                     , trim(h.ccy_cd)::varchar                        AS ccy_cd
                     , h.trsc_dt
                     , h.trsc_tm
                     , h.acct_seq_no
                     , trim(h.inout_dv)::varchar                      AS inout_dv
                     , h.rmrk
                     , h.rmrk2
                     , m.prn_seq
                     , COALESCE( NULLIF(wb_get_bank_nm(h.bank_cd,'s'), '')
                               , NULLIF(wb_get_bank_nm(h.bank_cd,'l'), '')
                               , 'Bank ' || h.bank_cd )::varchar      AS bank_nm
                     , wb_get_amt(CASE WHEN h.inout_dv = '1' THEN COALESCE(h.trsc_amt,0) ELSE 0 END,
                                  h.ccy_cd)                           AS in_amt
                     , wb_get_amt(CASE WHEN h.inout_dv = '2' THEN COALESCE(h.trsc_amt,0) ELSE 0 END,
                                  h.ccy_cd)                           AS out_amt
                     -- ===== DESCRIPTION : #37692, the SQL twin of the Java rule =====
                     -- fnsh_0002_001_r001_act.jsp:70-84  /  fnsh_0006_001_r001_act.jsp:69-82
                     -- Order of the branches matters. Blank-testing is btrim()-based to
                     -- match Java isNotBlank(); the concatenation uses the RAW values,
                     -- exactly as MessageFormat.format("{0} - {1}", RMRK2, RMRK) does.
                     , CASE
                         WHEN h.bank_cd = '11704020' THEN                  -- Woori VN only
                              CASE WHEN h.inout_dv = '2'                   -- OUT / Withdrawal
                                   THEN h.rmrk2
                                   ELSE h.rmrk                             -- IN / Deposit
                              END
                         WHEN btrim(COALESCE(h.rmrk2,'')) <> ''
                          AND btrim(COALESCE(h.rmrk ,'')) <> ''
                              THEN h.rmrk2 || ' - ' || h.rmrk              -- 2,008 rows on DEV
                         WHEN btrim(COALESCE(h.rmrk2,'')) <> ''
                              THEN h.rmrk2
                         ELSE h.rmrk
                       END::varchar                                    AS description
                     -- ===============================================================
                     -- CONTACT_NAME : OQ-DC-15 removed it from the export, and
                     -- RCV_PAY_TRCO_NM resolves on only 1.0% of WeCMS transactions (D-1).
                     , NULL::varchar                                   AS contact_name
                     -- ---- export §2.2 alternates, see the RETURNS TABLE note --------
                     , trim(m.acct_type_cd)::varchar                   AS acct_type_cd
                     , CASE trim(m.acct_type_cd)
                         WHEN '03' THEN 'Current'  WHEN '04' THEN 'Current'
                         WHEN '07' THEN 'Current'
                         WHEN '05' THEN 'Saving'   WHEN '06' THEN 'Saving'
                         WHEN '08' THEN 'Saving'
                         WHEN '02' THEN 'Fixed Deposit'
                         WHEN '01' THEN 'Loan'
                         ELSE 'Unknown'
                       END::varchar                                    AS acct_type_nm
                     -- "Bank Name || (Currency Code) || Account"  (PPT slide-7 §2.2)
                     , ( COALESCE( NULLIF(wb_get_bank_nm(h.bank_cd,'s'), '')
                                 , NULLIF(wb_get_bank_nm(h.bank_cd,'l'), '')
                                 , 'Bank ' || h.bank_cd)
                         || ' (' || trim(h.ccy_cd) || ') ' || h.acct_no )::varchar
                                                                       AS bank_account
                  FROM acct_trns_hstr h
                  JOIN acct_mstr m
                    ON m.biz_key = h.biz_key
                   AND m.bank_cd = h.bank_cd
                   AND m.acct_no = h.acct_no
                  -- -------------------------------------------------------------------
                  -- ONLY IF X-5 REINSTATES CONTACT NAME: re-enable the block below and
                  -- swap contact_name above for rp.trco_nm.  It MUST stay a LATERAL with
                  -- string_agg -- ACCT_TRNS_RCV_PAY_INFM carries up to 5 rows per
                  -- transaction (SPLIT_YN='Y', finding D-2), so a plain LEFT JOIN would
                  -- duplicate IN_AMT/OUT_AMT and break RC-01/RC-02.
                  -- Predicate is the 3-key form used by IDO.ACCT_TRNS_HSTR_R016 (D-2b).
                  --
                  -- LEFT JOIN LATERAL (
                  --       SELECT string_agg(NULLIF(r.rcv_pay_trco_nm, ''), ' / '
                  --                         ORDER BY r.rcv_pay_seq_no) AS trco_nm
                  --         FROM acct_trns_rcv_pay_infm r
                  --        WHERE r.biz_key     = h.biz_key
                  --          AND r.bank_cd     = h.bank_cd
                  --          AND r.acct_seq_no = h.acct_seq_no
                  -- ) rp ON TRUE
                  -- -------------------------------------------------------------------
                 WHERE h.biz_key = p_biz_key
                   AND h.trsc_dt BETWEEN p_from_dt AND p_to_dt
                   AND m.actv_yn = 'Y'
                   AND m.acct_type_cd IN ('03','04','05','06','07','08')
                   AND (p_ccy_cd IS NULL OR p_ccy_cd = '' OR h.ccy_cd = p_ccy_cd)
                   AND ( p_account_filter = 'ALL'
                      OR EXISTS ( SELECT 1
                                    FROM jsonb_array_elements(p_account_filter::jsonb) f
                                   WHERE (f->>'BANK_CD')::varchar = m.bank_cd
                                     AND (f->>'ACCT_NO')::varchar = m.acct_no ) )

                UNION ALL

                -- ================= SECTION A : Cash Book (Phase 2a) ==================
                SELECT 'A'::varchar
                     , NULL::varchar
                     , NULL::varchar
                     , trim(c.ccy_cd)::varchar
                     , c.trsc_dt
                     , NULL::varchar
                     , c.cash_repy_seq_no::varchar
                     , trim(c.inout_dv)::varchar
                     , NULL::varchar
                     , NULL::varchar
                     , NULL::numeric
                     , trim(c.ccy_cd)::varchar                        AS bank_nm  -- DIV = currency
                     , wb_get_amt(CASE WHEN c.inout_dv = '1' THEN COALESCE(c.trsc_amt,0) ELSE 0 END,
                                  c.ccy_cd)
                     , wb_get_amt(CASE WHEN c.inout_dv = '2' THEN COALESCE(c.trsc_amt,0) ELSE 0 END,
                                  c.ccy_cd)
                     -- NOTE: this CONT is CASH_REPY_MSTR.CONT -- the Cash Book ledger's
                     -- own description field, entered by the user on fnsh_0011_001.
                     -- It is UNRELATED to ACCT_TRNS_RCV_PAY_INFM.CONT and the 2026-08-06
                     -- reversal does NOT apply to it.  Cash Book has no RMRK/RMRK2.
                     , c.cont::varchar
                     , NULL::varchar                                   -- contact_name, see above
                     -- export §2.1: Division = "Cash", Bank Account = the currency
                     , NULL::varchar                                   -- acct_type_cd
                     , 'Cash'::varchar                                 -- acct_type_nm
                     , trim(c.ccy_cd)::varchar                         -- bank_account
                  FROM cash_repy_mstr c
                 WHERE c_incl_sect_a
                   AND c.biz_key = p_biz_key
                   AND c.trsc_dt BETWEEN p_from_dt AND p_to_dt
                   AND (p_ccy_cd IS NULL OR p_ccy_cd = '' OR c.ccy_cd = p_ccy_cd)
                -- Section C emits no detail rows: time deposits carry no transactions.
            ),
            labelled AS (
                SELECT b.*
                     , CASE b.sect_cd
                         WHEN 'A' THEN b.ccy_cd                              -- 'VND' / 'USD'
                         ELSE b.bank_nm || ' - ' || b.acct_no                -- 'Shinhan VN - 111111'
                       END::varchar                                     AS div
                     , CASE b.sect_cd WHEN 'A' THEN 1 WHEN 'B' THEN 2 ELSE 3 END AS sect_rank
                     , CASE b.ccy_cd  WHEN 'VND' THEN 1 WHEN 'USD' THEN 2
                                      WHEN 'KRW' THEN 3 ELSE 99 END          AS ccy_rank
                  FROM base b
            ),
            -- ===== ACCOUNT-SCOPED SEARCH (changed 2026-08-06, closes C-4) ============
            -- Two steps, and the second is the one that keeps RC-01/RC-02 passing.
            --   1. hit      : which (section, bank, account) has ANY matching row, or
            --                 matches on its own identity?
            --   2. filtered : keep EVERY row of those accounts -- NOT only the matching
            --                 rows. If only matching rows survived, the detail sums
            --                 would no longer equal the summary's Deposit/Expenditure
            --                 and the mandatory reconciliation would fail.
            -- p_srch_str is a bound parameter -- never concatenated into SQL (SC-03).
            -- The description matched is the POST-#37692 value, i.e. the exact string
            -- the user sees on screen. The contact_name predicate is retained but inert
            -- (the column is NULL) so it works unchanged if X-5 revives the column.
            hit AS (
                SELECT DISTINCT l.sect_cd, l.bank_cd, l.acct_no, l.ccy_cd
                  FROM labelled l
                 WHERE l.div                        ILIKE '%' || p_srch_str || '%'
                    OR l.bank_nm                    ILIKE '%' || p_srch_str || '%'
                    OR COALESCE(l.acct_no      , '') ILIKE '%' || p_srch_str || '%'
                    OR l.ccy_cd                     ILIKE '%' || p_srch_str || '%'
                    OR COALESCE(l.bank_account , '') ILIKE '%' || p_srch_str || '%'
                    OR COALESCE(l.acct_type_nm , '') ILIKE '%' || p_srch_str || '%'
                    OR COALESCE(l.description  , '') ILIKE '%' || p_srch_str || '%'
                    OR COALESCE(l.contact_name , '') ILIKE '%' || p_srch_str || '%'
                    OR l.trsc_dt                    ILIKE '%' || p_srch_str || '%'
                    OR (l.trsc_dt ~ '^\d{8}$' AND
                        to_char(to_date(l.trsc_dt,'YYYYMMDD'),'DD/MM/YYYY')
                                                    ILIKE '%' || p_srch_str || '%')
                    OR l.in_amt::text               ILIKE '%' || p_srch_str || '%'
                    OR l.out_amt::text              ILIKE '%' || p_srch_str || '%'
            ),
            filtered AS (
                SELECT l.*
                  FROM labelled l
                 WHERE p_srch_str IS NULL
                    OR p_srch_str = ''
                    OR EXISTS ( SELECT 1
                                  FROM hit x
                                 WHERE x.sect_cd = l.sect_cd
                                   AND x.ccy_cd  = l.ccy_cd
                                   AND COALESCE(x.bank_cd,'') = COALESCE(l.bank_cd,'')
                                   AND COALESCE(x.acct_no,'') = COALESCE(l.acct_no,'') )
            ),
            enriched AS (
                -- account ordering must match the summary table exactly
                SELECT f.*
                     , DENSE_RANK() OVER (
                           PARTITION BY f.sect_rank, f.ccy_rank
                           ORDER BY f.prn_seq NULLS LAST, f.bank_cd, f.acct_no ) AS acct_rank
                  FROM filtered f
            ),
            body AS (
                -- ---- 1. group header, one per section present ----------------------
                SELECT DISTINCT
                       'GROUP'::varchar   AS row_type
                     , e.sect_cd
                     , CASE e.sect_cd WHEN 'A' THEN 'Cash'
                                      WHEN 'B' THEN 'Demand Deposit'
                                      ELSE 'Time Deposit' END::varchar AS div
                     , NULL::varchar AS bank_cd, NULL::varchar AS acct_no, NULL::varchar AS ccy_cd
                     , NULL::numeric AS in_amt, NULL::numeric AS out_amt
                     , NULL::varchar AS description, NULL::varchar AS trsc_dt
                     , NULL::varchar AS contact_name
                     , NULL::varchar AS acct_type_cd, NULL::varchar AS acct_type_nm
                     , NULL::varchar AS bank_account
                     , NULL::varchar AS trsc_tm, NULL::varchar AS acct_seq_no
                     , NULL::varchar AS inout_dv, NULL::varchar AS rmrk, NULL::varchar AS rmrk2
                     , e.sect_rank, 0 AS ccy_rank, 0 AS acct_rank, 0 AS kind_rank
                     , NULL::varchar AS sk_dt, NULL::varchar AS sk_tm, NULL::numeric AS sk_seq
                  FROM enriched e

                UNION ALL

                -- ---- 2. detail rows ------------------------------------------------
                SELECT 'DATA'::varchar
                     , e.sect_cd, e.div, e.bank_cd, e.acct_no, e.ccy_cd
                     , e.in_amt, e.out_amt, e.description, e.trsc_dt, e.contact_name
                     , e.acct_type_cd, e.acct_type_nm, e.bank_account
                     , e.trsc_tm, e.acct_seq_no, e.inout_dv, e.rmrk, e.rmrk2
                     , e.sect_rank, e.ccy_rank, e.acct_rank, 1
                     , e.trsc_dt, COALESCE(e.trsc_tm,'000000')
                     , CASE WHEN e.acct_seq_no ~ '^\d+$'
                            THEN e.acct_seq_no::numeric ELSE 0 END
                  FROM enriched e

                UNION ALL

                -- ---- 3. subtotal per account  ("S.Total Bank(...)") ----------------
                SELECT 'ACCT_TOTAL'::varchar
                     , e.sect_cd
                     , CASE e.sect_cd
                         WHEN 'A' THEN ('S.Total Cash (' || e.ccy_cd || ')')
                         ELSE ('S.Total Bank(' || e.bank_nm || ' - ' || e.acct_no || ')')
                       END::varchar
                     , e.bank_cd, e.acct_no, e.ccy_cd
                     , SUM(e.in_amt)::numeric, SUM(e.out_amt)::numeric
                     , NULL::varchar, NULL::varchar, NULL::varchar
                     -- the subtotal row still identifies its account
                     , MAX(e.acct_type_cd)::varchar, MAX(e.acct_type_nm)::varchar
                     , MAX(e.bank_account)::varchar
                     , NULL::varchar, NULL::varchar, NULL::varchar, NULL::varchar, NULL::varchar
                     , e.sect_rank, e.ccy_rank, e.acct_rank, 2
                     , NULL::varchar, NULL::varchar, NULL::numeric
                  FROM enriched e
                 GROUP BY e.sect_cd, e.sect_rank, e.ccy_cd, e.ccy_rank
                        , e.acct_rank, e.bank_cd, e.acct_no, e.bank_nm

                UNION ALL

                -- ---- 4. subtotal per (section x currency)  ("VND ... total") -------
                SELECT 'GROUP_TOTAL'::varchar
                     , e.sect_cd
                     , ('Total (' || e.ccy_cd || ')')::varchar     -- see OPEN-2 (label)
                     , NULL::varchar, NULL::varchar, e.ccy_cd
                     , SUM(e.in_amt)::numeric, SUM(e.out_amt)::numeric
                     , NULL::varchar, NULL::varchar, NULL::varchar
                     , NULL::varchar, NULL::varchar, NULL::varchar
                     , NULL::varchar, NULL::varchar, NULL::varchar, NULL::varchar, NULL::varchar
                     , e.sect_rank, e.ccy_rank, 999999, 3
                     , NULL::varchar, NULL::varchar, NULL::numeric
                  FROM enriched e
                 GROUP BY e.sect_cd, e.sect_rank, e.ccy_cd, e.ccy_rank

                UNION ALL

                -- ---- 5. grand total, PER CURRENCY  (RC-07: never mix currencies) ---
                SELECT 'TOTAL'::varchar
                     , NULL::varchar
                     , ('Total (' || e.ccy_cd || ')')::varchar
                     , NULL::varchar, NULL::varchar, e.ccy_cd
                     , SUM(e.in_amt)::numeric, SUM(e.out_amt)::numeric
                     , NULL::varchar, NULL::varchar, NULL::varchar
                     , NULL::varchar, NULL::varchar, NULL::varchar
                     , NULL::varchar, NULL::varchar, NULL::varchar, NULL::varchar, NULL::varchar
                     , 9, e.ccy_rank, 999999, 4
                     , NULL::varchar, NULL::varchar, NULL::numeric
                  FROM enriched e
                 GROUP BY e.ccy_cd, e.ccy_rank
            )
            SELECT b.row_type, b.sect_cd, b.div, b.bank_cd, b.acct_no, b.ccy_cd
                 , b.in_amt, b.out_amt, b.description, b.trsc_dt, b.contact_name
                 , ROW_NUMBER() OVER (
                       ORDER BY b.sect_rank, b.ccy_rank, b.acct_rank, b.kind_rank
                              , b.sk_dt, b.sk_tm, b.sk_seq )::numeric AS disp_ord
                 , b.acct_type_cd, b.acct_type_nm, b.bank_account
                 , b.trsc_tm, b.acct_seq_no, b.inout_dv, b.rmrk, b.rmrk2
              FROM body b
        ) t
        ORDER BY t.disp_ord
    );
END;
$function$;


-- =====================================================================================
-- 5) rept_0018_001_rec5  --  REPORT HEADER   (serves WSVC record set REC4)
--     Company name / Tax Code come from BUSI_INFM (Preferences).
--     ADDR is composed from the four RPRS_BSUN_* columns -- BUSI_INFM has no single
--     address column (verified).  N-10 / prototype print header.
--     Approval box is STATIC CHROME -- nothing is queried for it (OQ-DC-11).
-- =====================================================================================
CREATE OR REPLACE FUNCTION rept_0018_001_rec5(
    p_biz_key  varchar,
    p_from_dt  varchar,
    p_to_dt    varchar
)
RETURNS TABLE(
    cmpn_nm      varchar,
    tax_id       varchar,
    addr         varchar,
    report_date  varchar,
    from_dt      varchar,
    to_dt        varchar
)
LANGUAGE plpgsql
AS $function$
#variable_conflict use_column
BEGIN
    RETURN QUERY (
        SELECT t.cmpn_nm, t.tax_id, t.addr, t.report_date, t.from_dt, t.to_dt
        FROM (
            SELECT b.cmpn_nm::varchar                                    AS cmpn_nm
                 , b.tax_id::varchar                                     AS tax_id
                 , NULLIF(
                     concat_ws(', ', NULLIF(b.rprs_bsun_dtl_adrs , '')
                                   , NULLIF(b.rprs_bsun_post_adrs, '')
                                   , NULLIF(b.rprs_bsun_city     , '')
                                   , NULLIF(b.rprs_bsun_st       , '')), '')::varchar AS addr
                 , p_to_dt::varchar                                      AS report_date
                 , p_from_dt::varchar                                    AS from_dt
                 , p_to_dt::varchar                                      AS to_dt
              FROM busi_infm b
             WHERE b.biz_key = p_biz_key
        ) t
    );
END;
$function$;

COMMIT;

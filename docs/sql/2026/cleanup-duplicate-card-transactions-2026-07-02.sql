-- WeCMS Manual Database Change
-- Date: 2026-07-02
-- Source: migrated from Claude Code (AI)/sql/card_transactions_dedup_cleanup_20260702.sql
-- Related record: docs/audits/legacy-parity/corporate-card-transaction-scrape-legacy-vs-refactor-audit.md
-- Type: DDL+DML
-- Execution: MANUAL
-- Target environments: TBD — original script does not name the environments
-- Executor: DBA TEAM
-- Rollback: Original script creates a backup table before deleting; recovery is a re-scrape with the fixed code (Strategy A)
-- Verification: pre/post-check queries are present in the original script below
--
-- DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- ============================================================================
-- CARD_TRNS_HSTR — cross-card scrape-duplicate CLEANUP  (PROPOSAL / DO NOT AUTO-RUN)
-- ----------------------------------------------------------------------------
-- Service      : card-transactions (Payment > Corporate Card > Card Transactions)
-- Date         : 2026-07-02
-- Author       : Claude Code (AI)   |   Approver: Mr. Jung (PO)   |   Runner: DBA (by hand)
-- Related fix  : CardTransactionPersister attribution guard (2026-07-02)
-- Audit doc    : wecms-api/docs/audits/corporate-card-transaction-scrape-legacy-vs-refactor-audit.md
--
-- WHAT THIS CLEANS
--   The pre-fix scrape stamped every scraped transaction with the per-card
--   LOOP card number instead of the transaction's own card, so one real
--   merchant transaction (e.g. FACEBK *49D6XUDPJ2) was inserted once per
--   registered card (4372 / 3861 / 8524 / 1167). This script removes those
--   bug-duplicated rows.
--
-- IMPORTANT — UNRECOVERABLE ATTRIBUTION
--   The N copies in a duplicate group are byte-identical except CARD_NO and
--   the PK CARD_SEQ_NO. ONE of them is the correct card; the other N-1 are
--   spurious — but which one is correct CANNOT be determined from the stored
--   data. Therefore the only way to restore CORRECT attribution is to delete
--   the whole affected group and RE-SCRAPE with the fixed code (Strategy A).
--   Strategy B (keep one arbitrary copy) leaves possibly-wrong attribution.
--
-- EXECUTION MODEL
--   * Read-only PREVIEW first (Section 2 + 3).
--   * Wrap the mutating sections in a single transaction; verify counts;
--     COMMIT only after the PO sign-off. ROLLBACK aborts with zero effect.
--   * Run by a DBA by hand. NOT a Flyway migration. NOT run by the app.
--
-- RUN ORDER
--   Section 1  Set filter parameters (edit placeholders)
--   Section 2  PREVIEW  — duplicate-group summary            (read-only)
--   Section 3  PREVIEW  — row-level detail + safety flags    (read-only)
--   Section 4  BACKUP   — snapshot the exact rows to delete   (writes backup only)
--   Section 5  DELETE   — remove the backed-up rows           (DESTRUCTIVE)
--   Section 6  VERIFY   — post-delete checks                  (read-only)
--   Section 7  ROLLBACK — restore from backup if needed
--
-- PLACEHOLDERS TO EDIT (replace every occurrence):
--   <<BIZ_KEY>>   tenant, e.g. 'VN_VNM0004_000001'
--   <<FROM_DT>>   inclusive TRSC_DT lower bound, YYYYMMDD, e.g. '20260601'
--   <<TO_DT>>     inclusive TRSC_DT upper bound, YYYYMMDD, e.g. '20260702'
--
-- KNOWN SCOPE (measured on the SHARED refactor DEV DB 2026-07-02, whole-table):
--   VN_VNM0004_000001 : 400 dupe rows | 0 linked | 0 ERP-sent | 0 child -> 400 safe
--   VN_VNM0004_000002 : 164 dupe rows | 0 linked | 164 ERP-sent(!) | 0 child -> 0 auto-safe
--   => Tenant _000002 is ENTIRELY ERP-sent; those rows were already pushed
--      downstream. Do NOT delete them without a separate PO/ERP decision
--      (cleaning WeCMS alone will not undo the ERP-side duplicate). The safe
--      filter in Section 4 excludes ERP-sent rows for exactly this reason.
--   !! These are the SHARED dev DB and DRIFT as others test (the appr_seq_no /
--      erp_snd_yn flags changed between two verification passes minutes apart).
--      Treat the numbers above as indicative only — the Section 2/3 preview and
--      the Section 4.1 count at EXECUTION TIME are authoritative.
-- ============================================================================


-- ============================================================================
-- SECTION 1 — PARAMETERS
-- ----------------------------------------------------------------------------
-- psql users may uncomment these \set lines and use :'biz_key' etc.
-- Other clients: replace the <<...>> placeholders literally in Sections 2-7.
--   \set biz_key 'VN_VNM0004_000001'
--   \set from_dt '20260601'
--   \set to_dt   '20260702'
-- ============================================================================


-- ============================================================================
-- SECTION 2 — PREVIEW: duplicate-group summary                       (READ-ONLY)
-- ----------------------------------------------------------------------------
-- A "duplicate group" = rows sharing the full legacy natural key EXCEPT card_no,
-- appearing under MORE THAN ONE card_no. AUTH_CODE (bank approval number) is the
-- strongest discriminator: a real authorization belongs to exactly one physical
-- card, so identical auth_code across cards proves the same txn was duplicated.
-- ============================================================================
WITH dupe_groups AS (
    SELECT biz_key, bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
           COALESCE(TRIM(auth_code), '') AS auth_key,
           COUNT(*)                 AS copies,
           COUNT(DISTINCT card_no)  AS distinct_cards
    FROM   card_trns_hstr
    WHERE  biz_key = '<<BIZ_KEY>>'
      AND  trsc_dt BETWEEN '<<FROM_DT>>' AND '<<TO_DT>>'
    GROUP  BY biz_key, bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
              COALESCE(TRIM(auth_code), '')
    HAVING COUNT(DISTINCT card_no) > 1          -- same txn under >1 card = the bug
)
SELECT
    COUNT(*)                                   AS duplicate_groups,
    SUM(copies)                                AS total_rows_in_groups,
    SUM(copies) - COUNT(*)                     AS extra_rows_if_keep_one,   -- Strategy B deletes this many
    SUM(copies)                                AS rows_if_delete_all,       -- Strategy A deletes this many (then re-scrape)
    MIN(trsc_dt)                               AS first_dt,
    MAX(trsc_dt)                               AS last_dt
FROM dupe_groups;


-- ============================================================================
-- SECTION 3 — PREVIEW: row-level detail + safety flags               (READ-ONLY)
-- ----------------------------------------------------------------------------
-- Inspect exactly which rows would be removed and WHY a row is NOT safe:
--   is_linked   : appr_seq_no set OR chart_acct_cd set  (used by an approval /
--                 chart-of-account link — deleting would break that reference)
--   is_erp_sent : erp_snd_yn = 'Y'                      (already exported to ERP)
--   has_child   : referenced by CARD_TRNS_RCV_PAY_INFM  (would orphan a child)
-- Rows flagged NOT-safe are EXCLUDED from the delete set in Section 4 and must
-- be handled manually with the PO.
-- ============================================================================
WITH dupe_groups AS (
    SELECT biz_key, bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
           COALESCE(TRIM(auth_code), '') AS auth_key
    FROM   card_trns_hstr
    WHERE  biz_key = '<<BIZ_KEY>>'
      AND  trsc_dt BETWEEN '<<FROM_DT>>' AND '<<TO_DT>>'
    GROUP  BY biz_key, bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
              COALESCE(TRIM(auth_code), '')
    HAVING COUNT(DISTINCT card_no) > 1
)
SELECT
    h.biz_key, h.card_seq_no, h.bank_cd, h.card_no, h.card_nm,
    h.trsc_dt, h.trsc_tm, h.ccy_cd, h.trsc_amt, h.merch_nm, h.auth_code,
    (h.appr_seq_no IS NOT NULL
        OR (h.chart_acct_cd IS NOT NULL AND h.chart_acct_cd <> ''))          AS is_linked,
    (h.erp_snd_yn = 'Y')                                                     AS is_erp_sent,
    (c.card_seq_no IS NOT NULL)                                             AS has_child,
    -- keep-rank within the group (lowest card_seq_no = the row Strategy B keeps)
    ROW_NUMBER() OVER (
        PARTITION BY h.bank_cd, h.trsc_dt, h.trsc_tm, h.ccy_cd, h.trsc_amt,
                     h.merch_nm, COALESCE(TRIM(h.auth_code), '')
        ORDER BY h.card_seq_no
    ) AS keep_rank
FROM card_trns_hstr h
JOIN dupe_groups g
  ON  h.biz_key  = g.biz_key  AND h.bank_cd = g.bank_cd
  AND h.trsc_dt  = g.trsc_dt  AND h.trsc_tm = g.trsc_tm
  AND h.ccy_cd   = g.ccy_cd   AND h.trsc_amt = g.trsc_amt
  AND h.merch_nm = g.merch_nm AND COALESCE(TRIM(h.auth_code), '') = g.auth_key
LEFT JOIN card_trns_rcv_pay_infm c
  ON  c.biz_key = h.biz_key AND c.bank_cd = h.bank_cd AND c.card_no = h.card_no
  AND c.trsc_dt = h.trsc_dt AND c.card_seq_no = h.card_seq_no
ORDER BY h.merch_nm, h.trsc_dt, h.trsc_tm, h.card_no;


-- ============================================================================
-- SECTION 4 — BACKUP: snapshot the EXACT rows to delete            (writes backup)
-- ----------------------------------------------------------------------------
-- Creates a timestamped backup table holding a FULL copy of every row that the
-- DELETE in Section 5 will remove. The DELETE and ROLLBACK both key off this
-- table, so the backup is the single source of truth for "what changed".
--
-- >>> CHOOSE EXACTLY ONE strategy (4A or 4B) and run only its INSERT. <<<
-- Both INSERTs exclude NOT-safe rows (linked / ERP-sent / has-child).
-- ============================================================================

-- 4.0  Create the backup table (structure identical to the source).
CREATE TABLE IF NOT EXISTS card_trns_hstr_dupe_bkp_20260702
    (LIKE card_trns_hstr INCLUDING ALL);

-- Common CTE reused by 4A and 4B (safe = in a dupe group AND not linked/sent/child).
-- ----------------------------------------------------------------------------
-- ---- STRATEGY 4A (RECOMMENDED): back up ALL safe rows in every dupe group. ---
-- After deleting these, RE-SCRAPE the same window with the fixed code so each
-- transaction is re-inserted under its correct card. Use when the bank history
-- window still covers <<FROM_DT>>..<<TO_DT>>.
INSERT INTO card_trns_hstr_dupe_bkp_20260702
SELECT h.*
FROM card_trns_hstr h
JOIN (
    SELECT biz_key, bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
           COALESCE(TRIM(auth_code), '') AS auth_key
    FROM   card_trns_hstr
    WHERE  biz_key = '<<BIZ_KEY>>'
      AND  trsc_dt BETWEEN '<<FROM_DT>>' AND '<<TO_DT>>'
    GROUP  BY biz_key, bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
              COALESCE(TRIM(auth_code), '')
    HAVING COUNT(DISTINCT card_no) > 1
) g
  ON  h.biz_key  = g.biz_key  AND h.bank_cd = g.bank_cd
  AND h.trsc_dt  = g.trsc_dt  AND h.trsc_tm = g.trsc_tm
  AND h.ccy_cd   = g.ccy_cd   AND h.trsc_amt = g.trsc_amt
  AND h.merch_nm = g.merch_nm AND COALESCE(TRIM(h.auth_code), '') = g.auth_key
WHERE h.appr_seq_no IS NULL                                   -- not linked to an approval
  AND (h.chart_acct_cd IS NULL OR h.chart_acct_cd = '')       -- not linked to a chart account
  AND h.erp_snd_yn = 'N'                                      -- not exported to ERP
  AND NOT EXISTS (                                            -- no child row
        SELECT 1 FROM card_trns_rcv_pay_infm c
        WHERE c.biz_key = h.biz_key AND c.bank_cd = h.bank_cd AND c.card_no = h.card_no
          AND c.trsc_dt = h.trsc_dt AND c.card_seq_no = h.card_seq_no)
-- Guard against double-inserting the backup on re-run:
  AND NOT EXISTS (
        SELECT 1 FROM card_trns_hstr_dupe_bkp_20260702 b
        WHERE b.biz_key = h.biz_key AND b.card_seq_no = h.card_seq_no);

/*  ---- STRATEGY 4B (ALTERNATIVE): keep ONE arbitrary copy per group. ----------
    Backs up every safe copy EXCEPT the lowest-card_seq_no row in each group.
    No re-scrape needed, but the kept row's card attribution may be wrong
    (1-in-N chance correct). Use ONLY if re-scrape is impossible AND the PO
    accepts approximate attribution. Comment out 4A above and run this instead.

INSERT INTO card_trns_hstr_dupe_bkp_20260702
SELECT h.*
FROM (
    SELECT h.*,
           ROW_NUMBER() OVER (
               PARTITION BY h.bank_cd, h.trsc_dt, h.trsc_tm, h.ccy_cd, h.trsc_amt,
                            h.merch_nm, COALESCE(TRIM(h.auth_code), '')
               ORDER BY h.card_seq_no
           ) AS keep_rank
    FROM card_trns_hstr h
    JOIN (
        SELECT biz_key, bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
               COALESCE(TRIM(auth_code), '') AS auth_key
        FROM   card_trns_hstr
        WHERE  biz_key = '<<BIZ_KEY>>'
          AND  trsc_dt BETWEEN '<<FROM_DT>>' AND '<<TO_DT>>'
        GROUP  BY biz_key, bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
                  COALESCE(TRIM(auth_code), '')
        HAVING COUNT(DISTINCT card_no) > 1
    ) g
      ON  h.biz_key  = g.biz_key  AND h.bank_cd = g.bank_cd
      AND h.trsc_dt  = g.trsc_dt  AND h.trsc_tm = g.trsc_tm
      AND h.ccy_cd   = g.ccy_cd   AND h.trsc_amt = g.trsc_amt
      AND h.merch_nm = g.merch_nm AND COALESCE(TRIM(h.auth_code), '') = g.auth_key
    WHERE h.appr_seq_no IS NULL
      AND (h.chart_acct_cd IS NULL OR h.chart_acct_cd = '')
      AND h.erp_snd_yn = 'N'
      AND NOT EXISTS (
            SELECT 1 FROM card_trns_rcv_pay_infm c
            WHERE c.biz_key = h.biz_key AND c.bank_cd = h.bank_cd AND c.card_no = h.card_no
              AND c.trsc_dt = h.trsc_dt AND c.card_seq_no = h.card_seq_no)
) h
WHERE h.keep_rank > 1                                        -- keep rank 1, back up the rest
  AND NOT EXISTS (
        SELECT 1 FROM card_trns_hstr_dupe_bkp_20260702 b
        WHERE b.biz_key = h.biz_key AND b.card_seq_no = h.card_seq_no);
*/

-- 4.1  Confirm the backup holds what you expect BEFORE deleting.
SELECT COUNT(*) AS rows_backed_up,
       COUNT(DISTINCT card_no) AS distinct_cards,
       MIN(trsc_dt) AS first_dt, MAX(trsc_dt) AS last_dt
FROM   card_trns_hstr_dupe_bkp_20260702
WHERE  biz_key = '<<BIZ_KEY>>';


-- ============================================================================
-- SECTION 5 — DELETE                                              *** DESTRUCTIVE ***
-- ----------------------------------------------------------------------------
-- Deletes exactly the rows captured in the backup table (matched on the PK
-- biz_key + card_seq_no). Run inside a transaction; check the row count against
-- Section 4.1 before COMMIT.
--
--   >>> Review Sections 2-4 output and obtain PO sign-off BEFORE running. <<<
-- ============================================================================
BEGIN;

DELETE FROM card_trns_hstr h
USING card_trns_hstr_dupe_bkp_20260702 b
WHERE h.biz_key     = b.biz_key
  AND h.card_seq_no = b.card_seq_no
  AND h.biz_key     = '<<BIZ_KEY>>';        -- extra tenant guard

-- Expect: deleted count == Section 4.1 rows_backed_up.
-- If it matches and the PO has signed off:   COMMIT;
-- Otherwise:                                  ROLLBACK;
-- COMMIT;
-- ROLLBACK;


-- ============================================================================
-- SECTION 6 — VERIFY                                                (READ-ONLY)
-- ----------------------------------------------------------------------------
-- Re-run after COMMIT. Expect ZERO remaining duplicate groups in the window.
-- ============================================================================
WITH dupe_groups AS (
    SELECT bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
           COALESCE(TRIM(auth_code), '') AS auth_key,
           COUNT(DISTINCT card_no) AS distinct_cards
    FROM   card_trns_hstr
    WHERE  biz_key = '<<BIZ_KEY>>'
      AND  trsc_dt BETWEEN '<<FROM_DT>>' AND '<<TO_DT>>'
    GROUP  BY bank_cd, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm,
              COALESCE(TRIM(auth_code), '')
    HAVING COUNT(DISTINCT card_no) > 1
)
SELECT COUNT(*) AS remaining_duplicate_groups   -- must be 0
FROM dupe_groups;

-- After Strategy 4A: RE-SCRAPE the same window from the Card Transactions
-- screen (Update) so the correct, per-card rows are re-inserted by the fixed
-- CardTransactionPersister.


-- ============================================================================
-- SECTION 7 — ROLLBACK (restore from backup)
-- ----------------------------------------------------------------------------
-- Use if a COMMITted delete needs to be undone (and the backup table still
-- exists). Re-inserts every backed-up row verbatim; ON CONFLICT keeps it
-- idempotent if some rows were already restored / never deleted.
-- ============================================================================
BEGIN;

INSERT INTO card_trns_hstr
SELECT * FROM card_trns_hstr_dupe_bkp_20260702
WHERE  biz_key = '<<BIZ_KEY>>'
ON CONFLICT (biz_key, card_seq_no) DO NOTHING;

-- Verify restored count, then:   COMMIT;   (or ROLLBACK; to abort)
-- COMMIT;
-- ROLLBACK;

-- Once the cleanup is confirmed good and no longer needed, drop the backup:
--   DROP TABLE card_trns_hstr_dupe_bkp_20260702;
-- ============================================================================
-- END
-- ============================================================================

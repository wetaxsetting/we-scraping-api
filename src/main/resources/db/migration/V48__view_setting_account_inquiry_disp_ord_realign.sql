-- ============================================================================
-- V48__view_setting_account_inquiry_disp_ord_realign.sql
--
-- Account Inquiry (VND + FCY) View Setting — realign DISP_ORD only.
--
-- V46/V47 seeded the two Account Inquiry screens with PROD's DISP_ORD
-- values verbatim (CHECKBOX=0; BANK_NM=8; ACCT_NICK_NM=9; BAL=10;
-- REAL_AMT=11; ACCT_HIS_LST_INQ_DT=12). The 8/9/10/11/12 gap is a JEX
-- artifact — legacy DEV had other column rows that filled 1–7 and have
-- since been pruned. The refactor expects dense, gap-free DISP_ORD
-- starting at 1 for the regular columns (popover renumbers densely
-- after every reorder per CLAUDE.md). This migration corrects the
-- ordering without re-seeding any other column data.
--
-- Target rules (from Sonny):
--   FIELD_ID                   DISP_ORD
--   ----------------------     --------
--   CHECKBOX                       0    (special row, always first)
--   BANK_NM                        1
--   ACCT_NICK_NM                   2
--   BAL                            3
--   REAL_AMT                       4
--   ACCT_HIS_LST_INQ_DT            5
--   ETC                           99    (special row, always last — if
--                                        present; not seeded today)
--
-- Scope:
--   * Two screens only: `banking-account-inquiry-vnd` (VND) and
--     `banking-account-inquiry-fcy` (FCY).
--   * Two tables: SYS_SCR_OPTION_WECMS (defaults) AND
--     USER_SCR_OPTION_WECMS (user overrides). USER table is updated in
--     place rather than wiped so existing per-user USE_YN toggles are
--     preserved — only the order column is realigned.
--   * Rows whose FIELD_ID does not appear in the CASE list are left
--     untouched (DISP_ORD unchanged). Pure DISP_ORD-only update.
--
-- Note on WEB_SVC_ID: instruction names the legacy JEX ids
-- (`fnsh_0001_001` / `fnsh_0005_001`) as the screen handles. The
-- actual DB rows live under the refactor namespace
-- (`banking-account-inquiry-vnd` / `banking-account-inquiry-fcy`) per
-- V46/V47 — that's what `ScreenOptionController.@Pattern` accepts. The
-- UPDATEs target the refactor namespace; nothing currently lives under
-- the legacy underscored ids.
--
-- Application source code is intentionally NOT touched.
--
-- Idempotent — UPDATE … CASE is naturally idempotent (re-running
-- produces the same row state). Safe to re-run.
-- ============================================================================

-- 1. SYS defaults — VND + FCY in one statement, scoped to the two
--    screens via WHERE … IN (...). Other screens are untouched.
UPDATE SYS_SCR_OPTION_WECMS
   SET DISP_ORD = CASE FIELD_ID
                      WHEN 'CHECKBOX'             THEN 0
                      WHEN 'BANK_NM'              THEN 1
                      WHEN 'ACCT_NICK_NM'         THEN 2
                      WHEN 'BAL'                  THEN 3
                      WHEN 'REAL_AMT'             THEN 4
                      WHEN 'ACCT_HIS_LST_INQ_DT'  THEN 5
                      WHEN 'ETC'                  THEN 99
                      ELSE DISP_ORD
                  END
 WHERE WEB_SVC_ID IN ('banking-account-inquiry-vnd',
                      'banking-account-inquiry-fcy')
   AND FIELD_ID IN ('CHECKBOX',
                    'BANK_NM',
                    'ACCT_NICK_NM',
                    'BAL',
                    'REAL_AMT',
                    'ACCT_HIS_LST_INQ_DT',
                    'ETC');

-- 2. USER overrides — same realignment so existing per-user rows
--    (lazy-init'd by `ScreenOptionService.load(...)` after V46/V47)
--    pick up the corrected order without losing any USE_YN toggles
--    the user may have set.
UPDATE USER_SCR_OPTION_WECMS
   SET DISP_ORD = CASE FIELD_ID
                      WHEN 'CHECKBOX'             THEN 0
                      WHEN 'BANK_NM'              THEN 1
                      WHEN 'ACCT_NICK_NM'         THEN 2
                      WHEN 'BAL'                  THEN 3
                      WHEN 'REAL_AMT'             THEN 4
                      WHEN 'ACCT_HIS_LST_INQ_DT'  THEN 5
                      WHEN 'ETC'                  THEN 99
                      ELSE DISP_ORD
                  END
 WHERE WEB_SVC_ID IN ('banking-account-inquiry-vnd',
                      'banking-account-inquiry-fcy')
   AND FIELD_ID IN ('CHECKBOX',
                    'BANK_NM',
                    'ACCT_NICK_NM',
                    'BAL',
                    'REAL_AMT',
                    'ACCT_HIS_LST_INQ_DT',
                    'ETC');

-- ============================================================================
-- Open risks / notes:
--
--  R1. The instruction lists `fnsh_0001_001` / `fnsh_0005_001` as the
--      target WEB_SVC_IDs, but the live local rows are under the
--      refactor namespace per V46/V47. Targeting the legacy underscored
--      ids would update zero rows. If a future migration restores the
--      legacy ids (would require relaxing the controller @Pattern
--      regex), this migration must be re-pointed.
--
--  R2. ETC is included in the CASE list defensively for future-proofing
--      — neither VND nor FCY currently seeds an ETC row (no action
--      column on Account Inquiry). The CASE branch is a no-op until a
--      later migration introduces ETC. No row is created here.
--
--  R3. Per-user reordering (any DISP_ORD a user saved via the popover
--      Save flow) is overwritten. This is intentional for a corrective
--      migration: the canonical order is wrong, fix it everywhere. If
--      this is undesired in a future case, drop the section 2 block
--      and let lazy-init re-populate from sys after a manual reset.
--
--  R4. No row count assertion at the end. Local R&D — Flyway will
--      report rows-affected in the migration log. Expected counts:
--      sys = 12 (6 VND + 6 FCY); user = 0..N depending on whether
--      a developer has opened the popover since V46/V47.
--
-- ============================================================================

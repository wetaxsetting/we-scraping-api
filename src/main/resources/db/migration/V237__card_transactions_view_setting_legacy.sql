-- ----------------------------------------------------------------------------
-- V237 — Align Payment > Corporate Card > Card Transactions View Setting
--        with the legacy card_0001_001 OSMC column set, widths, and sort
--        flags (2026-05-31)
--
-- V204 was the corrective reseed after the V192/V203 collision. It seeded
-- 15 rows under `dym_area_id='default'` using refactor-tailored FIELD_IDs
-- (CHECKBOX, CHART_ACCT_CD, LINK_REF, APPR_SEQ_NO, MEMO_ICON) with
-- Figma-tailored widths and SORT_YN='Y' on only 4 of the 13 user
-- columns. The legacy JEX page card_0001_001 (queried verbatim via JEX
-- DEV `SYS_SCR_OPTION_WECMS where web_svc_id = 'card_0001_001'` on
-- 2026-05-31) renders 13 user columns + CHECKBOX_1 + MEMO in this
-- canonical order:
--
--   0.   CHECKBOX_1          (selection column)      40px   (fix_yn=Y, special)
--   1.   TRSC_DT             Date/Time              120px
--   2.   CARD_NO             Card Number            200px
--   3.   MERCH_NM            Merchant               250px
--   4.   CARD_NICK_NM        Card Nickname          250px
--   5.   CARD_HLDR_NM        Cardholder             250px
--   6.   CCY_CD              Currency               110px
--   7.   TRSC_AMT            Amount                 150px
--   8.   CHART_ACCT_NM       Reporting Account      220px
--   9.   STTS                Status                 120px
--   10.  CARD_TRNS_USAGE_NM  Purpose                220px
--   11.  DISP_DOC_ID         Link Reference         200px
--   12.  CONT                Description            250px
--   13.  APPR_DOC_NO         Approval Ref.          180px
--   98.  MEMO                (action slot)          38px   (fix_yn=Y, special)
--
-- SORT_YN policy (per the user's task spec):
--   • CHECKBOX_1  SORT_YN='N' — special row-selection column, never
--                                sortable per CLAUDE.md §13a.
--   • MEMO        SORT_YN='N' — special action-slot column.
--   • Every other SORT_YN='Y' — 13 user data columns sortable.
--
-- FIELD_ID renames vs V204:
--   • CHECKBOX     → renamed to CHECKBOX_1 (legacy column name)
--   • CHART_ACCT_CD → renamed to CHART_ACCT_NM (legacy column name —
--                                  joined display name from CHART_ACCT_INFM
--                                  rather than the raw code)
--   • LINK_REF     → renamed to DISP_DOC_ID (legacy column name —
--                                  displayed `doc_id` from the linked
--                                  document)
--   • APPR_SEQ_NO  → renamed to APPR_DOC_NO (consistent with the other
--                                  Payment Result legacy seeds — V219 /
--                                  V223 / V227 / V230 / V235)
--   • MEMO_ICON    → renamed to MEMO (legacy column name)
--
-- Width deltas vs V204:
--   • CHECKBOX_1 (was CHECKBOX)   38 → 40
--   • TRSC_DT                    150 → 120
--   • CARD_NO                    180 → 200
--   • MERCH_NM                   220 → 250
--   • CARD_NICK_NM               160 → 250 (also SORT_YN N → Y)
--   • CARD_HLDR_NM               160 → 250 (also SORT_YN N → Y)
--   • CCY_CD                      90 → 110 (also SORT_YN N → Y)
--   • TRSC_AMT                   140 → 150
--   • CHART_ACCT_NM (was CHART_ACCT_CD)  180 → 220 (also SORT_YN N → Y)
--   • STTS                       120 = 120 (SORT_YN N → Y)
--   • CARD_TRNS_USAGE_NM         140 → 220 (also SORT_YN N → Y)
--   • DISP_DOC_ID (was LINK_REF)         180 → 200 (also SORT_YN N → Y)
--   • CONT                       200 → 250 (also SORT_YN N → Y)
--   • APPR_DOC_NO (was APPR_SEQ_NO)      160 → 180 (also SORT_YN N → Y)
--   • MEMO (was MEMO_ICON)        48 → 38
--
-- All 14 rows live in the seed (13 user + CHECKBOX_1 + MEMO = 15
-- total). Widths replaced with legacy PROD values; SORT_YN flipped to
-- 'Y' across the 13 user data columns ('N' only on the two specials).
--
-- Companion changes:
--   • CardTrnsHstrSortField enum extended with the new FIELD_IDs +
--     proxy entity-property mappings (CHART_ACCT_NM / CARD_TRNS_USAGE_NM /
--     DISP_DOC_ID / APPR_DOC_NO are projection-only columns; proxy to
--     the closest entity property until the backend ports a real LEFT
--     JOIN).
--   • CardTransactionsTable wires `sortable + sortFieldId` on the 13
--     user data column headers (8 newly added).
--   • CardTransactionsPage EXPORT_COLUMNS map updated to V237 FIELD_IDs.
--   • app/globals.css `--w-wb-ct-*` width tokens updated to legacy
--     PROD values.
--
-- Idempotent. Re-running is a no-op:
--   * DELETE removes the V204 row set first.
--   * USER_SCR_OPTION_WECMS row delete is unconditional — lazy-init in
--     ScreenOptionService.load(...) re-copies sys defaults.
-- ----------------------------------------------------------------------------

-- 1. Drop the V204 row set we no longer need (and any pre-existing
--    V237 rows from a re-run).
DELETE FROM SYS_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'card-transactions'
   AND DYM_AREA_ID = 'default'
   AND FIELD_ID IN (
       'CHECKBOX', 'TRSC_DT', 'CARD_NO', 'MERCH_NM', 'CARD_NICK_NM',
       'CARD_HLDR_NM', 'CCY_CD', 'TRSC_AMT', 'CHART_ACCT_CD', 'STTS',
       'CARD_TRNS_USAGE_NM', 'LINK_REF', 'CONT', 'APPR_SEQ_NO', 'MEMO_ICON',
       'CHECKBOX_1', 'CHART_ACCT_NM', 'DISP_DOC_ID', 'APPR_DOC_NO', 'MEMO'
   );

-- 2. Insert the legacy 13-user-column row set + CHECKBOX_1 + MEMO
--    with PROD widths + SORT_YN='Y' on every user data column.
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('card-transactions', 'default', 'CHECKBOX_1',         'Checkbox',           0,  'Y', 'Y', 'Y', 'N', '40px',  'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'TRSC_DT',            'Date/Time',          1,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_NO',            'Card Number',        2,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'MERCH_NM',           'Merchant',           3,  'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_NICK_NM',       'Card Nickname',      4,  'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_HLDR_NM',       'Cardholder',         5,  'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CCY_CD',             'Currency',           6,  'Y', 'N', 'Y', 'Y', '110px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'TRSC_AMT',           'Amount',             7,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CHART_ACCT_NM',      'Reporting Account',  8,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'STTS',               'Status',             9,  'Y', 'N', 'Y', 'Y', '120px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_TRNS_USAGE_NM', 'Purpose',           10,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'DISP_DOC_ID',        'Link Reference',    11,  'Y', 'N', 'Y', 'Y', '200px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CONT',               'Description',       12,  'Y', 'N', 'Y', 'Y', '250px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'APPR_DOC_NO',        'Approval Ref.',     13,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'MEMO',               '',                  98,  'Y', 'Y', 'Y', 'N', '38px',  'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

-- 3. Wipe USER_SCR_OPTION_WECMS overrides so the corrected sys defaults
--    take effect on every user's next GET.
DELETE FROM USER_SCR_OPTION_WECMS
 WHERE WEB_SVC_ID = 'card-transactions'
   AND DYM_AREA_ID = 'default';

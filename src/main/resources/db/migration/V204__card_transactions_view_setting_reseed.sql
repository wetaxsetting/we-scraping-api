-- =============================================================================
-- V204: Card Transactions View Setting reseed — fixes V192/V203 collision.
--
-- ROOT CAUSE
--   V192 (Card Transactions schema migration) inserted 27 rows into
--   SYS_SCR_OPTION_WECMS under `web_svc_id='card-transactions'` using
--   the legacy JEX `dym_area_id='table1'` / `'table2'` layout (with
--   `CHECKBOX_1`, `CARD_NO`, `MERCH_NM`, …, `CHECKBOX_2`, `CARD_NO_2`,
--   …). That seed predates the refactor's "no legacy numeric area ids,
--   slug-based FIELD_IDs only" rule (workspace CLAUDE.md §"Cross-Tree
--   Rules → Refactor work") and the deferred HAengSung `PRDT_TYPE='H'`
--   gating policy (wecms-api-main/CLAUDE.md §"Project Overview").
--
--   V203 then tried to seed the canonical `dym_area_id='default'` shape
--   with `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING`. The PK on
--   SYS_SCR_OPTION_WECMS is `(WEB_SVC_ID, FIELD_ID)` only — it does NOT
--   include DYM_AREA_ID — so every V203 FIELD_ID that already existed
--   under V192 (TRSC_DT, CARD_NO, MERCH_NM, CARD_NICK_NM, CARD_HLDR_NM,
--   CCY_CD, TRSC_AMT, STTS, CONT, CARD_TRNS_USAGE_NM = 10 rows) was
--   silently skipped. Only 5 non-colliding FIELD_IDs landed: CHECKBOX,
--   CHART_ACCT_CD, LINK_REF, APPR_SEQ_NO, MEMO_ICON.
--
--   Observable symptom (2026-05-22):
--     - View Setting popover shows only 4 togglable rows (Reporting
--       Account · Link Reference · Approval Ref. · Memo[locked]).
--     - Excel export drops down to ~2 columns (Reporting Account +
--       Approval Ref.) because backend intersects requested columns
--       with persisted `USE_YN='Y'` rows under `dym_area_id='default'`.
--
-- FIX
--   Mirrors the V32 cutover pattern (legacy `fnsh_0001_001` → semantic
--   `banking-account-inquiry-vnd`):
--     1. DELETE every USER_SCR_OPTION_WECMS row for `card-transactions`
--        so any user's stale lazy-init state is discarded.
--     2. DELETE every SYS_SCR_OPTION_WECMS row for `card-transactions`
--        (clears both V192's `table1`/`table2` AND V203's partial
--        `default` insert).
--     3. Re-INSERT the V203 column set fresh — 15 rows under
--        `dym_area_id='default'`. Identical to V203 but with no
--        ON CONFLICT clause; the prior DELETE guarantees a clean slate.
--
--   HAengSung product-type variants (legacy `table2`) intentionally
--   NOT seeded — that gating is deferred per wecms-api-main/CLAUDE.md.
--   When ported in a later sprint, the HAengSung column set lands as
--   its own `dym_area_id` under a meaningful kebab-case name (NOT
--   `table2`).
-- =============================================================================

DELETE FROM USER_SCR_OPTION_WECMS WHERE WEB_SVC_ID = 'card-transactions';

DELETE FROM SYS_SCR_OPTION_WECMS  WHERE WEB_SVC_ID = 'card-transactions';

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('card-transactions', 'default', 'CHECKBOX',           'Checkbox',          0,  'Y', 'Y', 'Y', 'N', '38px',  'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'TRSC_DT',            'Date / Time',       1,  'Y', 'N', 'Y', 'Y', '150px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_NO',            'Card Number',       2,  'Y', 'N', 'Y', 'Y', '180px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'MERCH_NM',           'Merchant',          3,  'Y', 'N', 'Y', 'Y', '220px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_NICK_NM',       'Card Nickname',     4,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_HLDR_NM',       'Cardholder',        5,  'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CCY_CD',             'Currency',          6,  'Y', 'N', 'Y', 'N', '90px',  'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'TRSC_AMT',           'Amount',            7,  'Y', 'N', 'Y', 'Y', '140px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CHART_ACCT_CD',      'Reporting Account', 8,  'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'STTS',               'Status',            9,  'Y', 'N', 'Y', 'N', '120px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CARD_TRNS_USAGE_NM', 'Purpose',          10, 'Y', 'N', 'Y', 'N', '140px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'LINK_REF',           'Link Reference',   11, 'Y', 'N', 'Y', 'N', '180px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'CONT',               'Description',      12, 'Y', 'N', 'Y', 'N', '200px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'APPR_SEQ_NO',        'Approval Ref.',    13, 'Y', 'N', 'Y', 'N', '160px', 'system', NOW(), 'system', NOW()),
    ('card-transactions', 'default', 'MEMO_ICON',          'Memo',             98, 'Y', 'Y', 'Y', 'N', '48px',  'system', NOW(), 'system', NOW());

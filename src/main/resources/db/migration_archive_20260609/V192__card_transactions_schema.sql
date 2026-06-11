-- =============================================================================
-- V192 — Payment > Card Transactions (slug: card-transactions) schema + seed
-- =============================================================================
-- Two PROD-active-list tables ported column-for-column from DEV
-- (wecmsvndevdb @ 10.254.242.254:54311, verified 2026-05-18 via the read-only
-- postgres_dev MCP):
--
--   CARD_TRNS_HSTR  — 26 cols, PK (BIZ_KEY, CARD_SEQ_NO)
--   CARD_MSTR       — 25 cols, PK (BIZ_KEY, BANK_CD, CARD_NO)
--
-- Both tables are on the PROD Active Table List in `wecms-api-main/CLAUDE.md`.
-- Neither carries any UK / CHECK / FK constraint on DEV — refactor mirrors.
-- Three helper indexes are added beyond DEV (allowed per the Legacy Schema
-- Parity Rule's "Indexes beyond PK/UK" clause) to match the new endpoint's
-- read profile: tenant + date-range ledger query, LEFT JOIN to CARD_MSTR on
-- (BANK_CD, CARD_NO), and the approval-requested / approval-not-requested
-- totals split.
--
-- View Setting seed: two dym_area_id variants under
-- web_svc_id='card-transactions' (slug, NOT the legacy 'card_0001_001'):
--   - table1 — default columns for PRDT_TYPE F / M (16 rows).
--   - table2 — HAENGSUNG variant for PRDT_TYPE H (14 rows; CARD_TRNS_USAGE_NM
--             and CHART_ACCT_NM dropped per DEV's table2 seed).
--
-- DISP_DOC_ID and APPR_DOC_NO seed SORT_YN='N' (refactor delta) because the
-- columns are projection-only in the list response (legacy aggregates these
-- via JSON_AGG and EXPT_RESOL_MSTR sub-select — neither maps to a JPA entity
-- property, so the sort whitelist excludes them per the §"Common table sort"
-- "View Setting + whitelist agreement" rule). MEMO / MEMO_2 / CHECKBOX_1 /
-- CHECKBOX_2 are special FIELD_IDs handled by ScreenOptionService.
--
-- No demo row seed: the shared dev DB (172.168.5.172:5432/wecmsdevdb) already
-- carries the JEX-imported data the local profile reads. No new wb_get_next_val
-- branch (the refactor doesn't write CARD_TRNS_HSTR rows — scraping stays on
-- the JEX side).
--
-- Idempotent — every step is CREATE/INSERT IF NOT EXISTS / ON CONFLICT.
-- Re-running this migration on an already-migrated DB is a no-op.
--
-- Feature spec:    docs/refactor-wecms/features/card-transactions.md
-- Backend design:  docs/refactor-wecms/backend/card-transactions.md
-- Migration map:   docs/refactor-wecms/migration/card-transactions.md
-- Schema notes:    docs/refactor-wecms/database/card-transactions.md
-- =============================================================================

-- ---------------------------------------------------------------------------
-- CARD_TRNS_HSTR — one row per scraped card transaction
-- Column count parity with DEV: 26
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS CARD_TRNS_HSTR (
    BIZ_KEY              VARCHAR(20)   NOT NULL,
    BANK_CD              VARCHAR(20)   NOT NULL,
    CARD_NO              VARCHAR(100)  NOT NULL,
    TRSC_DT              VARCHAR(8)    NOT NULL,
    TRSC_TM              VARCHAR(6),
    CARD_SEQ_NO          VARCHAR(20)   NOT NULL,
    CARD_NM              VARCHAR(50),
    MERCH_NM             VARCHAR(50),
    CCY_CD               CHAR(3)       NOT NULL,
    INOUT_DV             CHAR(1)       NOT NULL DEFAULT '2',
    TRSC_AMT             NUMERIC(20, 6),
    USING_TYPE           VARCHAR(50),
    PRCH_STTS            VARCHAR(50),
    NOTE                 VARCHAR(255),
    CONT                 VARCHAR(200),
    APPR_SEQ_NO          VARCHAR(15),
    CARD_TRNS_USAGE_CODE VARCHAR(20),
    AUTH_CODE            VARCHAR(15),
    CITY                 VARCHAR(100),
    REG_ID               VARCHAR(100)  NOT NULL,
    REG_DTM              VARCHAR(14)   NOT NULL,
    MOD_ID               VARCHAR(100)  NOT NULL,
    MOD_DTM              VARCHAR(14)   NOT NULL,
    CHART_ACCT_SEQ_NO    VARCHAR(10),
    CHART_ACCT_CD        VARCHAR(20),
    ERP_SND_YN           CHAR(1)       NOT NULL DEFAULT 'N',
    CONSTRAINT pk_card_trns_hstr PRIMARY KEY (BIZ_KEY, CARD_SEQ_NO)
);

-- Hot path: per-tenant date-range ledger query (driver of GET /…/card-transactions).
CREATE INDEX IF NOT EXISTS ix_card_trns_hstr_biz_trsc_dt
    ON CARD_TRNS_HSTR (BIZ_KEY, TRSC_DT DESC, TRSC_TM DESC, CARD_SEQ_NO DESC);

-- LEFT JOIN target on CARD_MSTR via (BIZ_KEY, BANK_CD, CARD_NO).
CREATE INDEX IF NOT EXISTS ix_card_trns_hstr_biz_card
    ON CARD_TRNS_HSTR (BIZ_KEY, BANK_CD, CARD_NO);

-- Approval-requested vs not-requested totals split (partial — APPR_SEQ_NO non-null).
CREATE INDEX IF NOT EXISTS ix_card_trns_hstr_biz_appr_seq
    ON CARD_TRNS_HSTR (BIZ_KEY, APPR_SEQ_NO)
    WHERE APPR_SEQ_NO IS NOT NULL;

-- ---------------------------------------------------------------------------
-- CARD_MSTR — card registry; LEFT JOIN target for the ledger query
-- Column count parity with DEV: 25
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS CARD_MSTR (
    BIZ_KEY              VARCHAR(20)   NOT NULL,
    BANK_CD              VARCHAR(20)   NOT NULL,
    CARD_NO              VARCHAR(100)  NOT NULL,
    CARD_TYPE_CD         CHAR(2)       NOT NULL,
    ISSU_DT              VARCHAR(8),
    EXPI_DT              VARCHAR(8),
    CARD_NM              VARCHAR(50),
    CARD_NICK_NM         VARCHAR(50),
    CARD_HLDR_NM         VARCHAR(50)   NOT NULL,
    CARD_HIS_INQ_STTS    VARCHAR(20),
    CARD_HIS_INQ_MSG     VARCHAR(255),
    CARD_HIS_LST_INQ_DT  VARCHAR(14),
    PRN_SEQ              NUMERIC,
    ACTV_YN              CHAR(1)       NOT NULL DEFAULT 'Y',
    REG_ID               VARCHAR(100)  NOT NULL,
    REG_DTM              VARCHAR(14)   NOT NULL,
    MOD_ID               VARCHAR(100)  NOT NULL,
    MOD_DTM              VARCHAR(14)   NOT NULL,
    NXISAS_ACCT_TYPE     VARCHAR(50),
    BANK_CERT_SEQ        NUMERIC,
    TRN_LST_SUC_DTM      VARCHAR(14),
    LST_LGN_STTS         VARCHAR(20),
    LST_LGN_STTS_MSG     VARCHAR(255),
    LST_LGN_SUC_DTM      VARCHAR(14),
    PYMN_ACCT_NO         VARCHAR(50),
    CONSTRAINT pk_card_mstr PRIMARY KEY (BIZ_KEY, BANK_CD, CARD_NO)
);

-- DEFERRED (DEV-only, out of refactor scope for this MR):
--   No FK between CARD_TRNS_HSTR and CARD_MSTR on DEV — preserve that parity.
--   The LEFT JOIN in the ledger query reads CARD_MSTR for display name
--   columns; mismatches render as empty (legacy parity confirmed via the
--   DEV row whose card_no='407465******5559' has no matching CARD_MSTR row).

-- ---------------------------------------------------------------------------
-- View Setting seed — two dym_area_id variants
--   table1 — default (PRDT_TYPE F / M)
--   table2 — HAENGSUNG variant (PRDT_TYPE H)
--
-- Matches the DEV SYS_SCR_OPTION_WECMS dump verbatim (verified 2026-05-18)
-- with two refactor deltas:
--   - SORT_YN flipped to 'N' on DISP_DOC_ID and APPR_DOC_NO (projection-only
--     columns; no CardTrnsHstrSortField constant — sort whitelist agreement).
--   - web_svc_id is the kebab-case slug 'card-transactions', NOT the legacy
--     'card_0001_001' JEX id (per the 2026-05-09 audit-DB-constant retirement).
-- ---------------------------------------------------------------------------

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    -- ----- table1: default columns (PRDT_TYPE F / M) -----
    ('card-transactions', 'table1', 'CHECKBOX_1',          'CHECKBOX_1',          0,   'Y', 'Y', 'Y', 'Y', '40px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'TRSC_DT',             'Date/Time',           1,   'Y', 'N', 'Y', 'Y', '120px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'CARD_NO',             'Card Number',         2,   'Y', 'N', 'Y', 'Y', '200px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'MERCH_NM',            'Merchant',            3,   'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'CARD_NICK_NM',        'Card Nickname',       4,   'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'CARD_HLDR_NM',        'Cardholder',          5,   'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'CCY_CD',              'Currency Code',       6,   'Y', 'N', 'Y', 'Y', '110px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'TRSC_AMT',            'Amount',              7,   'Y', 'N', 'Y', 'Y', '150px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'CHART_ACCT_NM',       'Reporting Account',   8,   'Y', 'N', 'Y', 'Y', '220px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'STTS',                'Status',              9,   'Y', 'N', 'Y', 'Y', '120px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'CARD_TRNS_USAGE_NM',  'Purpose',             10,  'Y', 'N', 'Y', 'Y', '220px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'DISP_DOC_ID',         'Link Reference',      11,  'Y', 'N', 'Y', 'N', '200px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'CONT',                'Description',         12,  'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'APPR_DOC_NO',         'Approval Ref.',       13,  'Y', 'N', 'Y', 'N', '180px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table1', 'MEMO',                'Memo',                100, 'Y', 'Y', 'Y', 'N', '38px',
     'system', NOW(), 'system', NOW()),
    -- ----- table2: HAENGSUNG variant (PRDT_TYPE H) -----
    ('card-transactions', 'table2', 'CHECKBOX_2',          'CHECKBOX_2',          0,   'Y', 'Y', 'Y', 'Y', '40px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'TRSC_DT_2',           'Date/Time',           1,   'Y', 'N', 'Y', 'Y', '120px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'CARD_NO_2',           'Card Number',         2,   'Y', 'N', 'Y', 'Y', '200px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'MERCH_NM_2',          'Merchant',            3,   'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'CARD_NICK_NM_2',      'Card Nickname',       4,   'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'CARD_HLDR_NM_2',      'Cardholder',          5,   'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'CCY_CD_2',            'Currency Code',       6,   'Y', 'N', 'Y', 'Y', '110px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'TRSC_AMT_2',          'Amount',              7,   'Y', 'N', 'Y', 'Y', '150px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'STTS_2',              'Status',              8,   'Y', 'N', 'Y', 'Y', '120px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'DISP_DOC_ID_2',       'Link Reference',      9,   'Y', 'N', 'Y', 'N', '200px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'CONT_2',              'Description',         10,  'Y', 'N', 'Y', 'Y', '250px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'APPR_DOC_NO_2',       'Approval Ref.',       11,  'Y', 'N', 'Y', 'N', '180px',
     'system', NOW(), 'system', NOW()),
    ('card-transactions', 'table2', 'MEMO_2',              'Memo',                100, 'Y', 'Y', 'Y', 'N', '38px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

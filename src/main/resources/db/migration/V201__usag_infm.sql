-- =====================================================================
-- V201 — USAG_INFM + USAG_INFM_LANG_DETL
-- =====================================================================
-- Purpose dictionary used by the Purpose pickers across the product
-- (Card Transactions row-edit, New Receipt popup, future Expense /
-- Payroll flows). Two tables — master + per-language name detail.
--
-- Schema column-for-column with DEV (verified via postgres_dev MCP
-- 2026-05-22). The "purposes" the New Receipt popup needs are filtered
-- to {@code USAG_GRP_CD = '6'} (refactor's first consumer).
--
-- Seed is the 3-row subset of {@code USAG_GRP_CD='6'} from DEV — small
-- enough to bundle inline.
-- =====================================================================

CREATE TABLE IF NOT EXISTS USAG_INFM (
    USE_USAG_CD       VARCHAR(20)  NOT NULL,
    USAG_GRP_CD       VARCHAR(3)   NOT NULL,
    REPT_DV           VARCHAR(2),
    REPT_GRP          VARCHAR(2),
    ACCT_CD           VARCHAR(5),
    ACCT_NM           VARCHAR(50),
    DEL_YN            VARCHAR(1)   DEFAULT 'N',
    USAG_TYPE         VARCHAR(2),
    HAND_USE_USAG_CD  VARCHAR(20),
    MOD_ID            VARCHAR(100),
    MOD_DTM           VARCHAR(14),
    CONSTRAINT pk_usag_infm PRIMARY KEY (USE_USAG_CD)
);

CREATE INDEX IF NOT EXISTS ix_usag_infm_grp_del
    ON USAG_INFM (USAG_GRP_CD, DEL_YN);

CREATE TABLE IF NOT EXISTS USAG_INFM_LANG_DETL (
    USE_USAG_CD       VARCHAR(20)  NOT NULL,
    LANG_CODE         VARCHAR(2)   NOT NULL,
    USAG_NM           VARCHAR(100),
    USAG_CONT         TEXT,
    CONSTRAINT pk_usag_infm_lang_detl PRIMARY KEY (USE_USAG_CD, LANG_CODE)
);

-- Seed USAG_GRP_CD='6' rows from DEV (3 entries).
INSERT INTO USAG_INFM
    (USE_USAG_CD, USAG_GRP_CD, REPT_DV, REPT_GRP, ACCT_CD, ACCT_NM, DEL_YN, USAG_TYPE, HAND_USE_USAG_CD, MOD_ID, MOD_DTM) VALUES
    ('6000000057', '6', '', '',   '',   '',   'N', '52',  '40', 'SYSTEM', '20230421134300'),
    ('6000000300', '6', NULL, NULL, NULL, NULL, 'N', NULL, '41', 'SYSTEM', '20250224064047'),
    ('6000000301', '6', NULL, NULL, NULL, NULL, 'N', NULL, '42', 'SYSTEM', '20250224064047')
ON CONFLICT (USE_USAG_CD) DO NOTHING;

INSERT INTO USAG_INFM_LANG_DETL
    (USE_USAG_CD, LANG_CODE, USAG_NM) VALUES
    ('6000000057', 'EN', 'Loan Repaid'),
    ('6000000057', 'KO', '대출금상환'),
    ('6000000057', 'KM', 'ការបង់កម្ចី'),
    ('6000000057', 'VI', 'Hoàn trả khoản vay'),
    ('6000000300', 'EN', 'Temporary payment for employees'),
    ('6000000300', 'KO', '직원에 대한 임시 지불'),
    ('6000000300', 'KM', 'Temporary payment for employees'),
    ('6000000300', 'VI', 'Temporary payment for employees'),
    ('6000000301', 'EN', 'Other expenses'),
    ('6000000301', 'KO', '기타 지출'),
    ('6000000301', 'KM', 'Other expenses'),
    ('6000000301', 'VI', 'Other expenses')
ON CONFLICT (USE_USAG_CD, LANG_CODE) DO NOTHING;

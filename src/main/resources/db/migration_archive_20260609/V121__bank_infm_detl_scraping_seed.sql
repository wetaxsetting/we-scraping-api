-- ============================================================================
-- V121__bank_infm_detl_scraping_seed.sql
--
-- Login Credentials > Add Bank picker: seed the missing BANK_INFM_DETL
-- rows so the picker can query the legacy "scraping-capable banks"
-- shape verbatim.
--
-- V44 originally seeded only BANK_DETL_INFO_CD = '10' (short bank
-- names) because the nickname-formatting feature was the only consumer.
-- The bank picker uses the legacy R004 query which JOINs three views
-- of the table:
--   '9'  → scraping enabled flag (Y/N)
--   '10' → short name           (already seeded in V44)
--   '11' → icon path
--
-- This migration adds the '9' + '11' rows for every bank that is
-- scraping-capable on DEV (snapshot pulled 2026-05-14 via the read-only
-- postgres_dev MCP — 26 banks). Names already exist in V44; this just
-- extends each bank's record with the two missing metadata flavours so
-- the SELECT … JOIN … JOIN … query returns the same shape it does on
-- DEV.
--
-- Idempotent via INSERT … ON CONFLICT DO NOTHING. The PK is
-- (BANK_CD, BANK_DETL_INFO_CD, START_DATE), so re-running is a no-op.
-- Joins are on (BANK_CD, SEQ_NO); using SEQ_NO=0 matches V44's
-- convention so the legacy R004 picks up these rows cleanly.
-- ============================================================================

-- Scraping-enabled flag (BANK_DETL_INFO_CD = '9')
-- Filters which banks the picker lists. Only 'Y' rows show up; 'N' or
-- missing → bank is invisible to the picker.
INSERT INTO BANK_INFM_DETL
    (BANK_CD, BANK_DETL_INFO_CD, START_DATE, END_DATE, BANK_DETL_INFO_CONT, REG_ID, REG_DTM, MOD_ID, MOD_DTM, SEQ_NO)
VALUES
    ('11704001', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704002', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704003', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704004', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704007', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704008', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704011', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704012', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704013', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704014', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704020', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704080', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704088', '9', '20210801', '99991231', 'Y', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704100', '9', '20220713', '99991231', 'Y', 'ktr2006', '20220713012321', NULL, NULL, 0),
    ('11704101', '9', '20220713', '99991231', 'Y', 'ktr2006', '20220713012403', NULL, NULL, 0),
    ('11704102', '9', '20220728', '99991231', 'Y', 'ktr2006', '20220728080249', NULL, NULL, 0),
    ('11704103', '9', '20220728', '99991231', 'Y', 'ktr2006', '20220728080333', NULL, NULL, 0),
    ('11704104', '9', '20230516', '99991231', 'Y', 'SYSTEM', '20230516000000', NULL, NULL, 0),
    ('11704105', '9', '20240216', '99991231', 'Y', 'SYSTEM', '20240216000000', NULL, NULL, 0),
    ('11704106', '9', '20240216', '99991231', 'Y', 'SYSTEM', '20240216000000', NULL, NULL, 0),
    ('11704108', '9', '20240409', '99991231', 'Y', 'SYSTEM', '20240409000000', NULL, NULL, 0),
    ('11704112', '9', '20250411', '99991231', 'Y', 'SYSTEM', '20250411000000', NULL, NULL, 0)
ON CONFLICT DO NOTHING;

-- Icon path (BANK_DETL_INFO_CD = '11')
-- Provides the SVG/PNG asset path the picker renders next to each bank
-- name. Paths reference assets that exist in
-- WECMS_STATIC/web/assets_v2/images/banking_VN/ (the legacy folder).
-- The refactor frontend can either consume these paths verbatim (if
-- the assets are copied to public/) or use the path as a key into its
-- own asset registry.
INSERT INTO BANK_INFM_DETL
    (BANK_CD, BANK_DETL_INFO_CD, START_DATE, END_DATE, BANK_DETL_INFO_CONT, REG_ID, REG_DTM, MOD_ID, MOD_DTM, SEQ_NO)
VALUES
    ('11704001', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank13.png', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704002', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank10.png', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704003', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank3.png',  'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704004', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank12.png', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704007', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank6.png',  'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704008', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank2.png',  'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704011', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank1.png',  'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704012', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank11.png', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704013', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank4.png',  'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704014', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank7.png',  'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704020', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank14.png', 'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704080', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank5.png',  'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704088', '11', '20210801', '99991231', '/assets_v2/images/banking_VN/ico_bank8.png',  'SYSTEM', '20210803000000', NULL, NULL, 0),
    ('11704100', '11', '20220713', '99991231', '/assets_v2/images/banking_VN/ico_bank15.png', 'ktr2006', '20220713012321', NULL, NULL, 0),
    ('11704101', '11', '20220713', '99991231', '/assets_v2/images/banking_VN/ico_bank17.png', 'ktr2006', '20220713012403', NULL, NULL, 0),
    ('11704102', '11', '20220728', '99991231', '/assets_v2/images/banking_VN/ico_bank19.png', 'ktr2006', '20220728080249', NULL, NULL, 0),
    ('11704103', '11', '20220728', '99991231', '/assets_v2/images/banking_VN/ico_bank18.png', 'ktr2006', '20220728080333', NULL, NULL, 0),
    ('11704104', '11', '20230516', '99991231', '/assets_v2/images/banking_VN/sc_bank_icon.png', 'SYSTEM', '20230516000000', NULL, NULL, 0),
    ('11704105', '11', '20240216', '99991231', '/assets_v2/images/banking_VN/icon_join_stock_SHB.png', 'SYSTEM', '20240216000000', NULL, NULL, 0),
    ('11704106', '11', '20240216', '99991231', '/assets_v2/images/banking_VN/ab_bank_icon.png', 'SYSTEM', '20240216000000', NULL, NULL, 0),
    ('11704108', '11', '20240409', '99991231', '/assets_v2/images/banking_VN/PG_Bank_logo.png', 'SYSTEM', '20240409000000', NULL, NULL, 0),
    ('11704112', '11', '20250411', '99991231', '/assets_v2/images/banking_VN/Eximbank_logo.png', 'SYSTEM', '20250411000000', NULL, NULL, 0)
ON CONFLICT DO NOTHING;

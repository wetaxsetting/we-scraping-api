-- ============================================================================
-- V169 — Settings > Contacts View Setting screen seed (re-apply).
--
-- Renumbered from V127 on 2026-05-18 to resolve a Flyway duplicate-version
-- collision. This file's content duplicates V88__contacts_view_setting_seed.sql
-- (the original Contacts seed); the V88 INSERT already ran on every developer
-- DB with ON CONFLICT DO NOTHING, so this V169 re-run is a no-op there.
-- Kept (not deleted) as a defensive backstop for any DB that somehow received
-- the V127 form and would otherwise see Flyway forget about it.
--
-- View Setting (Settings > Contacts) screen seed. Registers the
-- `contacts` screenId with the shared `SYS_SCR_OPTION_WECMS` infra so
-- the existing global View Setting endpoints
-- (`GET / PUT / DELETE /api/v1/screen-options/{webSvcId}/{dymAreaId}`)
-- can serve the page without code changes (per the workspace rule:
-- "Adopting a new page on the backend is seed-only").
--
-- Seed columns (in display order):
--   CHECKBOX            — DISP_ORD=0,  FIX_YN='Y', SORT_YN='N' (special)
--   HAND_TRCO_KEY       — DISP_ORD=1,  FIX_YN='Y', SORT_YN='Y'
--   TRCO_CMNM           — DISP_ORD=2,  FIX_YN='N', SORT_YN='Y'
--   TRCO_TYPE           — DISP_ORD=3,  FIX_YN='N', SORT_YN='Y'
--   TRCO_TAX_ID         — DISP_ORD=4,  FIX_YN='N', SORT_YN='Y'
--   BANK_NM             — DISP_ORD=5,  FIX_YN='N', SORT_YN='N' (sub-table)
--   TRCO_BSUN_POST_ADRS — DISP_ORD=6,  FIX_YN='N', SORT_YN='N' (sub-table)
--   ETC                 — DISP_ORD=99, FIX_YN='Y', SORT_YN='N' (special)
--
-- BANK_NM and TRCO_BSUN_POST_ADRS ship SORT_YN='N' because they project
-- from child tables — the `ContactSortField` whitelist enum does NOT
-- carry constants for them, and per the workspace rule "seed SORT_YN and
-- the whitelist enum must agree."
--
-- Special FIELD_IDs (CHECKBOX + ETC) follow the existing convention:
-- DISP_ORD=0 + 99 anchor positions, FIX_YN='Y' locks them, frontend
-- popover hides them.
--
-- SIZE values are placeholders pending Figma WB365 widths — the legacy
-- view JSP column widths (50/120/180/150/130/200/300/50) are recorded
-- here as a starting reference (see `comm_0184_001_view.jsp:139-148`).
--
-- Idempotent: ON CONFLICT DO NOTHING.
-- ============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('contacts', 'default', 'CHECKBOX',            'CHECKBOX',           0, 'Y', 'Y', 'N', 'N', '50px',
     'system', NOW(), 'system', NOW()),
    ('contacts', 'default', 'HAND_TRCO_KEY',       'Contact Code',       1, 'Y', 'Y', 'Y', 'Y', '120px',
     'system', NOW(), 'system', NOW()),
    ('contacts', 'default', 'TRCO_CMNM',           'Company',            2, 'Y', 'N', 'Y', 'Y', '180px',
     'system', NOW(), 'system', NOW()),
    ('contacts', 'default', 'TRCO_TYPE',           'Contact Location',   3, 'Y', 'N', 'Y', 'Y', '150px',
     'system', NOW(), 'system', NOW()),
    ('contacts', 'default', 'TRCO_TAX_ID',         'Tax ID',             4, 'Y', 'N', 'Y', 'Y', '130px',
     'system', NOW(), 'system', NOW()),
    ('contacts', 'default', 'BANK_NM',             'Bank',               5, 'Y', 'N', 'Y', 'N', '200px',
     'system', NOW(), 'system', NOW()),
    ('contacts', 'default', 'TRCO_BSUN_POST_ADRS', 'Address',            6, 'Y', 'N', 'Y', 'N', '300px',
     'system', NOW(), 'system', NOW()),
    ('contacts', 'default', 'ETC',                 '',                  99, 'Y', 'Y', 'N', 'N', '50px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

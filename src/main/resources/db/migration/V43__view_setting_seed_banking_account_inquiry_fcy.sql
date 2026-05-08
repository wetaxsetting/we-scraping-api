-- ============================================================================
-- V43__view_setting_seed_banking_account_inquiry_fcy.sql
--
-- Seed View Setting columns for Banking > Account Inquiry (FCY).
--
-- The FCY twin lives at /banking/account-inquiry-fcy and binds
-- WEB_SVC_ID = 'banking-account-inquiry-fcy' / DYM_AREA_ID = 'default'.
-- Until this migration applies, the FCY page mis-keys its popover to the
-- VND scope (because the shared frontend component used to hardcode
-- SCREEN_IDS.ACCOUNT_INQUIRY_VND); this migration plus the matching
-- frontend prop wiring + the per-screen Excel provider give FCY its own
-- column set and per-user overrides.
--
-- Column set mirrors the VND seed (V32 / V33 / V34 / V41 / V42):
--   CHECKBOX (special), BANK, ACCOUNT_NICKNAME, BALANCE, AVAILABLE_BALANCE,
--   VAS_ACCOUNT (DISP_YN='N' + USE_YN='N' / SORT_YN='N' — same as VND
--   after V41/V42), LAST_UPDATE.
--
-- SIZE values copy the Figma WB365 widths used for VND (no separate FCY
-- Figma frame). When the FCY page gets its own widths, ship a follow-up
-- migration with UPDATEs targeting only this WEB_SVC_ID.
--
-- Idempotent via `ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING` —
-- matches the table PK introduced in V30.
-- ============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD,
     USE_YN, FIX_YN, DISP_YN, size, SORT_YN)
VALUES
    ('banking-account-inquiry-fcy', 'default', 'CHECKBOX',          'Checkbox',          0, 'Y', 'Y', 'Y', '38px',  'Y'),
    ('banking-account-inquiry-fcy', 'default', 'BANK',              'Bank',              1, 'Y', 'Y', 'Y', '240px', 'Y'),
    ('banking-account-inquiry-fcy', 'default', 'ACCOUNT_NICKNAME',  'Account Nickname',  2, 'Y', 'N', 'Y', '280px', 'Y'),
    ('banking-account-inquiry-fcy', 'default', 'BALANCE',           'Balance',           3, 'Y', 'N', 'Y', '160px', 'Y'),
    ('banking-account-inquiry-fcy', 'default', 'AVAILABLE_BALANCE', 'Available Balance', 4, 'Y', 'N', 'Y', '180px', 'Y'),
    ('banking-account-inquiry-fcy', 'default', 'VAS_ACCOUNT',       'VAS Account',       5, 'N', 'N', 'N', '200px', 'N'),
    ('banking-account-inquiry-fcy', 'default', 'LAST_UPDATE',       'Last Update',       6, 'Y', 'N', 'Y', '160px', 'Y')
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

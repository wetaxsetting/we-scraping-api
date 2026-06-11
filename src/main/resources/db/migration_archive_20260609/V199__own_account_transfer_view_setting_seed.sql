-- =============================================================================
-- V194 — Banking > Own Account Transfer: View Setting seed
-- =============================================================================
-- Seeds SYS_SCR_OPTION_WECMS with the columns for the Own Account Transfer
-- source-account picker under web_svc_id = 'own-account-transfer',
-- dym_area_id = 'default'.
--
-- Renumber history:
--   - Originally authored as V180; renumbered to V194 on 2026-05-20 after a
--     V180 number collision was discovered against the shared dev DB
--     (`V180__overseas_result_payment_sort_yn_update` was already in
--     `flyway_schema_history`, applied 2026-05-19). Because Flyway tracks
--     by version number, the OAT V180 file was being silently skipped —
--     `SYS_SCR_OPTION_WECMS` had ZERO rows for `web_svc_id =
--     'own-account-transfer'`, which made `ScreenOptionService.load()`
--     throw `WBE2001` for every GET / PUT / DELETE the OAT View Setting
--     popover issued. Save and Reset appeared to do nothing because every
--     round-trip rejected before persisting.
--   - V194 is the next free slot above the local DB's max applied version
--     (193 = `bank_account_enable_vas_name_sort` on 2026-05-20).
--   - The shadowed V180 file was deleted in this commit; the SQL below is
--     byte-identical to the V180 content modulo the renumber header.
--
-- Why this exists:
--   - The Own Account Transfer page exposes the toolbar list-icon (View
--     Setting popover) per CLAUDE.md §"Screen Option (View Setting)". The
--     popover, Save, and Reset all key against this seed.
--   - Without the seed, `ScreenOptionService.load(...)` throws WBE2001.
--     The page would fall back to FALLBACK_COLUMNS (visual only), but
--     Save / Reset PUT / DELETE both 200-with-error because the backend
--     refuses to write user-overrides for an unseeded screen.
--   - Sort wiring (CLAUDE.md §13a) requires `SORT_YN='Y'` on columns the
--     backend whitelist (`OwnAccountTransferSortField`) covers.
--
-- Column set (mirrors the page's FALLBACK_COLUMNS):
--   BANK_CD          (Bank, locked, sortable)
--   ACCT_NICK_NM     (Account, sortable)
--   BAL              (Balance, sortable)
--   AVI_BAL          (Available, sortable)
--   ACCT_BAL_LST_STS (Last Refresh, sortable on ACCT_BAL_UPD_DTM — see
--                     OwnAccountTransferSortField; legacy parity with
--                     trns_0001_001_view.jsp's sortable Last Update header)
--   ETC              (Action, special DISP_ORD=99 — per-row Transfer button)
--
-- CHECKBOX is NOT seeded — the OAT source-account table has no row-
-- selection column (per-row action is the Transfer button only).
--
-- ETC is the special trailing-action column per the CLAUDE.md "Special
-- FIELD_IDs" rule. It rides through View Setting data so column order is
-- preserved across Save / Reset, but the popover hides it and the
-- backend locks USE_YN='Y' + DISP_ORD=99.
--
-- Idempotent via ON CONFLICT DO NOTHING.
-- =============================================================================

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM,
     DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE,
     REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('own-account-transfer', 'default', 'BANK_CD',          'Bank',         1, 'Y', 'Y', 'Y', 'Y', '260px',
     'system', NOW(), 'system', NOW()),
    ('own-account-transfer', 'default', 'ACCT_NICK_NM',     'Account',      2, 'Y', 'N', 'Y', 'Y', '260px',
     'system', NOW(), 'system', NOW()),
    ('own-account-transfer', 'default', 'BAL',              'Balance',      3, 'Y', 'N', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('own-account-transfer', 'default', 'AVI_BAL',          'Available',    4, 'Y', 'N', 'Y', 'Y', '160px',
     'system', NOW(), 'system', NOW()),
    ('own-account-transfer', 'default', 'ACCT_BAL_LST_STS', 'Last Refresh', 5, 'Y', 'N', 'Y', 'Y', '180px',
     'system', NOW(), 'system', NOW()),
    ('own-account-transfer', 'default', 'ETC',              'Action',      99, 'Y', 'Y', 'Y', 'N', '140px',
     'system', NOW(), 'system', NOW())
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

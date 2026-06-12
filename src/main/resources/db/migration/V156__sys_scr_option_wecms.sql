------------------------------------------------------------------
-- Manual DBA Script
-- Flyway disabled/removed.
-- Execute manually in DEV -> UAT -> PROD.
------------------------------------------------------------------
-- File:    V156__sys_scr_option_wecms.sql
-- Table:   public.sys_scr_option_wecms  (live JEX, 10-column schema)
-- Purpose: Seed NEW refactor Spring/Next.js route-slug screen-option rows.
--          Additive coexistence data — refactor route-slug rows live in the
--          same table as the legacy JEX rows (disjoint web_svc_id namespace).
--
-- SCOPE: View Setting popover pages ONLY.
--   A page is seeded here ONLY if the refactor frontend renders the
--   <ViewSettingPopover> / drives runtime column visibility & order via
--   useViewSetting(...). Export-only screenIds are intentionally EXCLUDED:
--   a page that merely passes a screenId to the Excel-export pipeline (and
--   does NOT render the popover) is NOT View Setting adoption and is not
--   seeded here. (If such a page ever needs working Excel export, its seed
--   is a separate decision — see EXCLUDED list below.)
--   Current scope: 28 popover pages / 289 rows.
--
-- NOTE ON VERSION/LOCATION:
--   * The V156 prefix is for file organization / change tracking ONLY.
--     Flyway is removed/disabled (ddl-auto=validate in every profile); this
--     folder is used purely as a SQL repository. Nothing here auto-runs.
--   * This script is executed BY HAND by the DBA, DEV -> UAT -> PROD.
--
-- RULES APPLIED:
--   * 10 live-JEX columns only: web_svc_id, field_id, field_nm, disp_ord,
--     disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont.
--   * NOT included: fix_yn, reg_id, reg_dtm, mod_id, mod_dtm.
--   * Legacy JEX rows are never inserted, updated, or deleted.
--   * Idempotent: ON CONFLICT (web_svc_id, field_id) DO NOTHING.
--   * USER_SCR_OPTION_WECMS is NOT seeded (lazy-init populates it per user
--     on first GET).
--
-- DYM_AREA_ID POLICY (do not assume 'default'):
--   1. DYM_AREA_ID = the table/grid area inside one screen. One WEB_SVC_ID
--      can carry multiple areas (legacy: table1/table2/table3/table4).
--   2. BINDING RULE: the seeded dym_area_id MUST match the refactor frontend's
--      useViewSetting(screenId, dymAreaId) argument for that screen — that is
--      the consumer; the backend lookup keys on dym_area_id, so a mismatch
--      makes the page read nothing.
--   3. PRESERVE the legacy dym_area_id (table1/table2/...) by DEFAULT.
--   4. CONVERT to 'default' ONLY when the refactor frontend explicitly uses
--      DEFAULT_AREA_ID AND renders one consolidated grid (verify the
--      useViewSetting(...) call first). When in doubt, keep the legacy area.
--   5. MULTI-AREA pages: preserve the real area ids (table1/table2/...) AND
--      preserve the suffixed FIELD_IDs (CHECKBOX_2, BANK_NM_2, ...). The PK is
--      (web_svc_id, field_id) with NO area column, so unsuffixed field_ids
--      across two areas would collide and ON CONFLICT DO NOTHING would
--      silently drop the second area's row.
--   6. DOCUMENT every area decision per page: Legacy area(s) / Refactor
--      area(s) / Reason (+ useViewSetting file:line evidence).
--
-- SCOPE: pages included so far —
--   * Account Inquiry (VND)        fnsh_0001_001 -> banking-account-inquiry-vnd
--   * Account Inquiry (FCY)        fnsh_0005_001 -> banking-account-inquiry-fcy
--   * Transaction History (VND)    fnsh_0002_001 -> banking-transaction-history-vnd
--   * Transaction History (FCY)    fnsh_0006_001 -> banking-transaction-history-fcy
--   * Own Account Transfer         trns_0001_001 -> own-account-transfer
--   * Approval Box                 resl_0001_001(table3) -> approval-box
--   * Internal Transfer (4)        pymn_0009_001/_0009_003/_0010_001/_0010_002 -> payment-internal-transfer-*
--   * Vendor Payment (4)           pymn_0001_001/_0001_003/_0002_001/_0002_002 -> payment-vendor-payment-*
--   * Expense Payment (4)          pymn_0003_001/_0003_003/_0004_001/_0004_002 -> payment-expense-payment-*
--   * Payroll Payment (4)          pymn_0005_001/_0005_003/_0006_001/_0006_002 -> payment-payroll-payment-*
--   * Overseas Payment (4)         pymn_0007_001(table2) / _0007_004 / _0008_001(table2) / _0008_002 -> payment-overseas-payment-*
--   * Card Transactions            card_0001_001 -> card-transactions
--   * Bank Account (Settings)      comm_0183_001 -> bank-account  (REDESIGN: legacy->refactor field_id remap; FALLBACK-sourced)
--   Further pages appended as their legacy source data is provided.
--   Payment/card batch: field_id/order/size/sort come from each refactor
--   FALLBACK_*_COLUMNS (FE-authoritative); legacy source areas are per page
--   (mostly table1; Approval Box=table3, Overseas Scheduled+Result=table2),
--   with _2 suffixes stripped and the two Approval-Box renames applied.
--
-- ATTENTION — pymn_0007_001 / payment-overseas-payment-scheduled was seeded
--   from legacy table2 BY EXPLICIT INSTRUCTION, but its (de-suffixed) batch
--   column set does NOT match the refactor ScheduledPaymentPage FALLBACK (FX
--   set). The FE will NOT render these columns until it adopts a batch column
--   set for this screen (the FX set matches legacy table1). See the WARNING in
--   that block. Also carries a duplicate legacy disp_ord (APPR_STTS=5,
--   PAY_DATE=5). Revisit before relying on the page.
--
-- EXCLUDED FROM THIS SEED (intentionally NOT seeded) — no View Setting popover:
--
--   Export-only screenIds (page passes a screenId to the Excel-export
--   pipeline but renders NO popover / no runtime column-setting UI — NOT
--   View Setting adoption; export would need its own seed decision later):
--     * addon-operation-status       (calls useViewSetting only to feed export; no popover)
--     * addon-sales-einvoices         (export screenId only)
--     * addon-expenses-einvoices      (export screenId only)
--     * cash-flow-report              (export screenId only; report page)
--     * firm-banking-history          (backend export provider only; no FE popover)
--     * erp-transaction-history       (backend export provider only; no FE popover)
--
--   Not View Setting at all (no popover, no useViewSetting, registry/audit-slug
--   or route-only):
--     * Smart Organizer / fnsh_0200_001 / smart-organizer (legacy has NO
--       SYS_SCR_OPTION_WECMS rows; standalone page; not in viewSettingScreens.ts)
--     * transfer-history, purpose-settings, approval-line, chart-of-accounts,
--       contacts, vas-account (reserved / audit-slug only)
--
--   Do NOT seed any of the above unless/until the frontend actually renders
--   <ViewSettingPopover> for it (registry entry + useViewSetting call); the
--   dym_area_id would then come from that useViewSetting argument.
------------------------------------------------------------------


-- ----------------------------------------------------------------------------
-- Account Inquiry (VND)
-- Legacy source:   fnsh_0001_001   (legacy areas: table1, table2)
-- Refactor target: banking-account-inquiry-vnd
-- Frontend:        useViewSetting(SCREEN_IDS.ACCOUNT_INQUIRY_VND, DEFAULT_AREA_ID)
--                  field_id alias: BANK_NM->BANK, ACCT_NICK_NM->ACCOUNT_NICKNAME,
--                  BAL->BALANCE, REAL_AMT->AVAILABLE_BALANCE, ACCT_HIS_LST_INQ_DT->LAST_UPDATE
--
-- AREA CONVERSION (verified — NOT an assumption):
--   Legacy area:   table1  (+ table2 second area)
--   Refactor area: default
--   Reason:        The Spring page queries the 'default' area ONLY —
--                  components/shared/AccountInquiryPage.tsx:523-524
--                  `VIEW_SETTING_AREA_ID = DEFAULT_AREA_ID;`
--                  `useViewSetting(screenId, DEFAULT_AREA_ID)`.
--                  The refactor renders one consolidated table (no table2).
--                  Seeding table1/table2 would not be read by the frontend
--                  (would yield WBE2001 / empty).
--   Source:        Claude Code (AI)/sql/_source_sys_scr_option_dump.csv
--                  (legacy fnsh_0001_001). 12 source rows -> 6 carried,
--                  6 dropped:
--                    * carried (table1): CHECKBOX, BANK_NM, ACCT_NICK_NM,
--                      BAL, REAL_AMT, ACCT_HIS_LST_INQ_DT
--                    * dropped  (table2): CHECKBOX_2, BANK_NM_2, ACCT_NICK_NM_2,
--                      BAL_2, REAL_AMT_2, ACCT_HIS_LST_INQ_DT_2
--   Normalized:    dym_area_id table1 -> default;
--                  disp_ord 0,14,15,16,17,19 -> 0..5 (relative order kept);
--                  sizes carried verbatim from legacy table1 — BANK_NM=160px
--                  (table1). NOTE: legacy table2 BANK_NM_2=250px is NOT used;
--                  if the refactor UI intends the wider Figma bank column,
--                  change BANK_NM size to 250px here.
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('banking-account-inquiry-vnd', 'CHECKBOX',            'CHECKBOX',            0, 'Y', 'Y', '35px',  'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'BANK_NM',             'BANK_NM',             1, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'ACCT_NICK_NM',        'ACCT_NICK_NM',        2, 'Y', 'Y', '107px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'BAL',                 'BAL',                 3, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'REAL_AMT',            'REAL_AMT',            4, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-vnd', 'ACCT_HIS_LST_INQ_DT', 'ACCT_HIS_LST_INQ_DT', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- Account Inquiry (FCY)
-- Legacy source:   fnsh_0005_001   (legacy areas: table1, table2)
-- Refactor target: banking-account-inquiry-fcy
-- Frontend:        useViewSetting(SCREEN_IDS.ACCOUNT_INQUIRY_FCY, DEFAULT_AREA_ID)
--                  app/(dashboard)/banking/account-inquiry-fcy/AccountInquiryFcyClient.tsx:40
--                  (shared AccountInquiryPage; field_id alias BANK_NM->BANK,
--                   ACCT_NICK_NM->ACCOUNT_NICKNAME, BAL->BALANCE,
--                   REAL_AMT->AVAILABLE_BALANCE, ACCT_HIS_LST_INQ_DT->LAST_UPDATE)
--
-- AREA CONVERSION (verified):
--   Legacy area:   table1 (+ table2)   ->   Refactor area: default
--   Reason:        FE uses DEFAULT_AREA_ID, single consolidated grid.
--                  table1 carried; table2 (_2 duplicates) dropped.
--                  disp_ord 0,14,15,16,17,19 -> 0..5 (relative order kept);
--                  sizes carried verbatim from legacy fnsh_0005_001 table1.
--
-- vs VND (fnsh_0001_001): same field set / ids / areas; ONLY two widths
--   differ and they are SWAPPED — FCY legacy has BANK_NM=107px /
--   ACCT_NICK_NM=160px, whereas VND has BANK_NM=160px / ACCT_NICK_NM=107px.
--   Likely a legacy data quirk (bank-name column narrower than nickname).
--   Carried verbatim from legacy per the CSV-as-source rule; if FCY should
--   match VND widths, set BANK_NM=160px / ACCT_NICK_NM=107px here.
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('banking-account-inquiry-fcy', 'CHECKBOX',            'CHECKBOX',            0, 'Y', 'Y', '35px',  'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'BANK_NM',             'Bank',             1, 'Y', 'Y', '107px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'ACCT_NICK_NM',        'Account Nickname',        2, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'BAL',                 'Balance',                 3, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'REAL_AMT',            'Available Balance',            4, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('banking-account-inquiry-fcy', 'ACCT_HIS_LST_INQ_DT', 'Last Update', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- Transaction History (VND)
-- Legacy source:   fnsh_0002_001   (legacy areas: table1, table2, table3, table4)
-- Refactor target: banking-transaction-history-vnd
-- Frontend:        useViewSetting(SCREEN_IDS.BANKING_TRANSACTION_HISTORY_VND, DEFAULT_AREA_ID)
--                  (client resolves the id from route scope "VND")
--
-- SPLIT (2026-06-11): Transaction History View Setting was split per scope —
--   VND (banking-transaction-history-vnd, legacy fnsh_0002_001) and
--   FCY (banking-transaction-history-fcy, legacy fnsh_0006_001) — see the FCY
--   block below. The two legacy screens are byte-for-byte identical, and the
--   refactor shares ONE column registry, so the VND and FCY seeds are
--   identical today; they are kept as separate web_svc_ids so they can
--   diverge later. The old single `banking-transaction-history` key is retired
--   (no longer seeded or used).
--
-- AREA CONVERSION (verified):
--   Legacy area:   table1 (+ table2/table3/table4)
--   Refactor area: default
--   Reason:        FE uses DEFAULT_AREA_ID and renders ONE consolidated grid
--                  (CCY is a data-scope param, not an area). Only table1 maps
--                  over; table2/3/4 dropped.
--
-- FIELD_ID REMAP (NOT a 1:1 carry — refactor REDESIGNED the columns; there is
--   NO alias map for this page, so the seed MUST use the refactor field_ids
--   from TRANSACTION_HISTORY_COLUMN_REGISTRY / FALLBACK_VS_COLUMNS, or the
--   frontend filter [TransactionHistoryPage.tsx:258] drops the column):
--     legacy TRNS_ID               -> TRSC_ID
--     legacy TRSC_DT               -> TRSC_DTM
--     legacy IN_AMT                -> DEPOSIT_AMT
--     legacy OUT_AMT               -> WITHDRAW_AMT
--     legacy BAL                   -> BAL            (same)
--     legacy RMRK                  -> RMRK           (same)
--     legacy FILE_NM               -> FILE_NM        (same)
--     legacy MATCH_DT              -> MTCH_DT
--     legacy MATCH_BY              -> MTCH_BY
--     legacy CHART_ACCT_CD         -> CHART_ACCT_CD  (same)
--     legacy RCV_PAY_DISP_USAG_NM  -> PURPOSE
--     legacy RCV_PAY_DISP_VAS_ACCT_NM -> VAS_ACCT_CD
--     legacy RCV_PAY_DISP_TRCO_NM  -> CONTACT_NM
--     legacy CNFM_BY_USER_NM       -> CONFIRMED_BY
--     legacy MEMO                  -> MEMO  (SPECIAL action-slot field, disp_ord 99)
--
-- VALUES SOURCE: field_id / field_nm / disp_ord / size / sort_yn for the 14
--   data columns are taken from the refactor authoritative config
--   (FALLBACK_VS_COLUMNS) — NOT the legacy sizes/sort_yn — because the row must
--   match the refactor field_ids it carries (legacy table1 was all
--   sort_yn='N'; the refactor enables sort on several columns, aligned to its
--   backend sort whitelist).
-- MEMO is carried as a SPECIAL action-slot field — it is in
--   VIEW_SETTING_SPECIAL_FIELD_IDS = {CHECKBOX, CHECKBOX_1, ETC, MEMO}, so the
--   popover hides it and ScreenOptionService.SPECIAL_FIELD_IDS locks it; it is
--   intentionally NOT in the column registry. disp_ord 99 + sort_yn 'N' per
--   legacy. No CHECKBOX / ETC for this screen (legacy table1 has none).
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('banking-transaction-history-vnd', 'TRSC_ID',       'Transaction ID',    1,  'Y', 'Y', '160px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'TRSC_DTM',      'Date/Time',         2,  'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('banking-transaction-history-vnd', 'DEPOSIT_AMT',   'Deposit',           3,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('banking-transaction-history-vnd', 'WITHDRAW_AMT',  'Withdrawal',        4,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('banking-transaction-history-vnd', 'BAL',           'Balance',           5,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('banking-transaction-history-vnd', 'RMRK',          'Remarks',           6,  'Y', 'Y', '240px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'FILE_NM',       'File Name',         7,  'Y', 'Y', '180px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'MTCH_DT',       'Matched Date',      8,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('banking-transaction-history-vnd', 'MTCH_BY',       'Matched By',        9,  'Y', 'Y', '120px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'CHART_ACCT_CD', 'Reporting Account', 10, 'Y', 'Y', '190px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'PURPOSE',       'Purpose',           11, 'Y', 'Y', '140px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'VAS_ACCT_CD',   'VAS Account',       12, 'Y', 'Y', '200px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'CONTACT_NM',    'Contact Name',      13, 'Y', 'Y', '180px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'CONFIRMED_BY',  'Confirmed by',      14, 'Y', 'Y', '160px', 'default', 'N', NULL),
    ('banking-transaction-history-vnd', 'MEMO',          'MEMO',              99, 'Y', 'Y', '120px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- Transaction History (FCY)
-- Legacy source:   fnsh_0006_001   (legacy areas: table1, table2, table3, table4)
-- Refactor target: banking-transaction-history-fcy
-- Frontend:        useViewSetting(SCREEN_IDS.BANKING_TRANSACTION_HISTORY_FCY, DEFAULT_AREA_ID)
--                  (client resolves the id from route scope "FCY")
--
-- Identical to the VND block above: legacy fnsh_0006_001 is byte-for-byte
-- identical to fnsh_0002_001, and the refactor shares one column registry.
-- Kept as a separate web_svc_id (per the split decision) so VND/FCY can
-- diverge later. Same conversion: table1 -> default; table2/3/4 dropped;
-- refactor field_ids from the shared registry; MEMO carried as a SPECIAL
-- action-slot field (disp_ord 99).
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('banking-transaction-history-fcy', 'TRSC_ID',       'Transaction ID',    1,  'Y', 'Y', '160px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'TRSC_DTM',      'Date/Time',         2,  'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('banking-transaction-history-fcy', 'DEPOSIT_AMT',   'Deposit',           3,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('banking-transaction-history-fcy', 'WITHDRAW_AMT',  'Withdrawal',        4,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('banking-transaction-history-fcy', 'BAL',           'Balance',           5,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('banking-transaction-history-fcy', 'RMRK',          'Remarks',           6,  'Y', 'Y', '240px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'FILE_NM',       'File Name',         7,  'Y', 'Y', '180px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'MTCH_DT',       'Matched Date',      8,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('banking-transaction-history-fcy', 'MTCH_BY',       'Matched By',        9,  'Y', 'Y', '120px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'CHART_ACCT_CD', 'Reporting Account', 10, 'Y', 'Y', '190px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'PURPOSE',       'Purpose',           11, 'Y', 'Y', '140px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'VAS_ACCT_CD',   'VAS Account',       12, 'Y', 'Y', '200px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'CONTACT_NM',    'Contact Name',      13, 'Y', 'Y', '180px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'CONFIRMED_BY',  'Confirmed by',      14, 'Y', 'Y', '160px', 'default', 'N', NULL),
    ('banking-transaction-history-fcy', 'MEMO',          'MEMO',              99, 'Y', 'Y', '120px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- Own Account Transfer
-- Legacy source:   trns_0001_001   (single legacy area: table1)
-- Refactor target: own-account-transfer
-- Frontend:        useViewSetting(SCREEN_IDS.OWN_ACCOUNT_TRANSFER, DEFAULT_AREA_ID)
--                  components/banking/own-account-transfer/OwnAccountTransferPage.tsx:199
--                  (popover rendered; SOURCE_ACCOUNTS_COLUMN_REGISTRY is keyed
--                   by field_id directly — the refactor REUSES the legacy
--                   field_ids, no alias, no redesign).
--
-- AREA CONVERSION (verified):
--   Legacy area:   table1   ->   Refactor area: default
--   Reason:        FE uses DEFAULT_AREA_ID, single grid. Legacy already has a
--                  single table1 area; mapped to 'default'. Nothing dropped.
--
-- Field set identical to legacy AND refactor FALLBACK_VS_COLUMNS: BANK_CD,
--   ACCT_NICK_NM, BAL, AVI_BAL, ACCT_BAL_LST_STS, ETC (ETC = SPECIAL
--   action-slot field, disp_ord 99). No CHECKBOX (neither side has one).
--   disp_ord carried as-is (1..5, 99); sizes carried verbatim from legacy
--   trns_0001_001 table1.
--
-- SIZE NOTE: the refactor FALLBACK_VS_COLUMNS uses wider (Figma) widths than
--   legacy across ALL columns — BANK_CD 260 vs 150, ACCT_NICK_NM 260 vs 100,
--   BAL 160 vs 100, AVI_BAL 160 vs 150, ACCT_BAL_LST_STS 180 vs 100,
--   ETC 140 vs 110. Legacy widths are seeded here per the CSV-as-source rule;
--   if the refactor UI intends the wider Figma widths, replace the sizes below
--   with the FALLBACK values.
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('own-account-transfer', 'BANK_CD',          'Bank',          1,  'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('own-account-transfer', 'ACCT_NICK_NM',     'Account Nickname',     2,  'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('own-account-transfer', 'BAL',              'BalanceBAL',              3,  'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('own-account-transfer', 'AVI_BAL',          'Available Balance',          4,  'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('own-account-transfer', 'ACCT_BAL_LST_STS', 'Last Refresh', 5,  'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('own-account-transfer', 'ETC',              'ETC',              99, 'Y', 'Y', '110px', 'default', 'Y', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- approval-box
-- Legacy source:   resl_0001_001   (source area: table3)
-- Refactor target: approval-box   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 11 rows.
-- Note:            remap: de-suffix _2; TRAN_CCY_2->CURRENCY, TOT_PAY_VND_AMT_2->TOT_PAY_AMT; field_ids from APPROVAL_FALLBACK
-- Carried (11): REG_DTM, DOC_NM, DOC_NO, TITL, FILE_NM, CURRENCY, TOT_PAY_AMT, PAY_END_YN, PAY_DATE, DRAFT_USER_NM, APPRER_NM
-- Dropped:         legacy table3 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('approval-box', 'REG_DTM', 'Date', 1, 'Y', 'Y', '90px', 'default', 'Y', NULL),
    ('approval-box', 'DOC_NM', 'Type', 2, 'Y', 'Y', '170px', 'default', 'Y', NULL),
    ('approval-box', 'DOC_NO', 'Document No.', 3, 'Y', 'Y', '135px', 'default', 'Y', NULL),
    ('approval-box', 'TITL', 'Title', 4, 'Y', 'Y', '220px', 'default', 'Y', NULL),
    ('approval-box', 'FILE_NM', 'File Name', 5, 'Y', 'Y', '260px', 'default', 'Y', NULL),
    ('approval-box', 'CURRENCY', 'Transfer Currency', 6, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('approval-box', 'TOT_PAY_AMT', 'Transfer Amount', 7, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('approval-box', 'PAY_END_YN', 'Approval Status', 8, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('approval-box', 'PAY_DATE', 'Scheduled Date', 9, 'Y', 'Y', '145px', 'default', 'Y', NULL),
    ('approval-box', 'DRAFT_USER_NM', 'Requestor', 10, 'Y', 'Y', '180px', 'default', 'Y', NULL),
    ('approval-box', 'APPRER_NM', 'Approver', 11, 'Y', 'Y', '180px', 'default', 'Y', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-internal-transfer-scheduled
-- Legacy source:   pymn_0009_001   (source area: table1)
-- Refactor target: payment-internal-transfer-scheduled   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 12 rows.
-- Carried (12): CHECKBOX, REG_DTM, FILE_NM, TOT_TRNS_CNT, TOTAL_TRAN_AMT, PAY_DATE, APPR_STTS, DRAFT_USER_NM, APPRVR_NM, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-internal-transfer-scheduled', 'CHECKBOX', 'Checkbox', 0, 'Y', 'Y', '40px', 'default', 'N', NULL),
    ('payment-internal-transfer-scheduled', 'REG_DTM', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'TOT_TRNS_CNT', 'No. of Transactions', 3, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'PAY_DATE', 'Scheduled Date', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'APPR_STTS', 'Approval Status', 6, 'Y', 'Y', '170px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'DRAFT_USER_NM', 'Requestor', 7, 'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'APPRVR_NM', 'Approver', 8, 'Y', 'Y', '175px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'APPR_DOC_NO', 'Approval Ref.', 9, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'TITL', 'Approval Title', 10, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled', 'ETC', '', 99, 'N', 'Y', '150px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-internal-transfer-scheduled-detail
-- Legacy source:   pymn_0009_003   (source area: table1)
-- Refactor target: payment-internal-transfer-scheduled-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 9 rows.
-- Carried (9): TAR_BANK_CD, RCPT_NM, TRAN_AMT, TRAN_USAG, CHART_ACCT_CD, NOTE, DOC_NO, BANK_TRAN_ID, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-internal-transfer-scheduled-detail', 'TAR_BANK_CD', 'Deposit Bank', 1, 'Y', 'Y', '170px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled-detail', 'RCPT_NM', 'Recipient Name', 2, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled-detail', 'TRAN_AMT', 'Transfer Amount', 3, 'Y', 'Y', '110px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled-detail', 'TRAN_USAG', 'Remarks', 4, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled-detail', 'CHART_ACCT_CD', 'Reporting Account', 5, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled-detail', 'NOTE', 'Description', 6, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled-detail', 'DOC_NO', 'Document No.', 7, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled-detail', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-internal-transfer-scheduled-detail', 'ETC', '', 99, 'Y', 'Y', '60px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-internal-transfer-result
-- Legacy source:   pymn_0010_001   (source area: table1)
-- Refactor target: payment-internal-transfer-result   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 8 rows.
-- Carried (8): TRAN_DT, FILE_NM, SCDUL_PYMN_DETL_NO, TRAN_AMT, PROC_STAT, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-internal-transfer-result', 'TRAN_DT', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result', 'SCDUL_PYMN_DETL_NO', 'No. of Transactions', 3, 'Y', 'Y', '155px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result', 'TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '235px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result', 'PROC_STAT', 'Transfer Status', 5, 'Y', 'Y', '190px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result', 'APPR_DOC_NO', 'Approval Ref.', 6, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result', 'TITL', 'Approval Title', 7, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result', 'ETC', '', 99, 'Y', 'Y', '50px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-internal-transfer-result-detail
-- Legacy source:   pymn_0010_002   (source area: table1)
-- Refactor target: payment-internal-transfer-result-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 12 rows.
-- Carried (12): PROC_DTTM, TAR_BANK_CD, RCPT_NM, TRAN_AMT, TRAN_USAG, RS_CD, RSLT_DTTM, CHART_ACCT_CD, NOTE, DOC_NO, BANK_TRAN_ID, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-internal-transfer-result-detail', 'PROC_DTTM', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'TAR_BANK_CD', 'Deposit Bank', 2, 'Y', 'Y', '260px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'RCPT_NM', 'Recipient Name', 3, 'Y', 'Y', '350px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'TRAN_AMT', 'Transfer Amount', 4, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'TRAN_USAG', 'Remarks', 5, 'Y', 'Y', '350px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'RS_CD', 'Results', 6, 'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'CHART_ACCT_CD', 'Reporting Account', 8, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'NOTE', 'Description', 9, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'DOC_NO', 'Document No.', 10, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'BANK_TRAN_ID', 'Banking Transaction ID', 11, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-internal-transfer-result-detail', 'ETC', '', 99, 'Y', 'Y', '100px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-vendor-payment-scheduled
-- Legacy source:   pymn_0001_001   (source area: table1)
-- Refactor target: payment-vendor-payment-scheduled   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 12 rows.
-- Carried (12): CHECKBOX, REG_DTM, FILE_NM, TOT_TRNS_CNT, TOTAL_TRAN_AMT, PAY_DATE, APPR_STTS, DRAFT_USER_NM, APPRVR_NM, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-vendor-payment-scheduled', 'CHECKBOX', 'Checkbox', 0, 'Y', 'Y', '40px', 'default', 'N', NULL),
    ('payment-vendor-payment-scheduled', 'REG_DTM', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'TOT_TRNS_CNT', 'No. of Transactions', 3, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'PAY_DATE', 'Scheduled Date', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'APPR_STTS', 'Approval Status', 6, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'DRAFT_USER_NM', 'Requestor', 7, 'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'APPRVR_NM', 'Approver', 8, 'Y', 'Y', '175px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'APPR_DOC_NO', 'Approval Ref.', 9, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'TITL', 'Approval Title', 10, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled', 'ETC', '', 99, 'N', 'Y', '150px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-vendor-payment-scheduled-detail
-- Legacy source:   pymn_0001_003   (source area: table1)
-- Refactor target: payment-vendor-payment-scheduled-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 9 rows.
-- Carried (9): TAR_BANK_CD, RCPT_NM, TRAN_AMT, TRAN_USAG, CHART_ACCT_CD, NOTE, DOC_NO, BANK_TRAN_ID, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-vendor-payment-scheduled-detail', 'TAR_BANK_CD', 'Deposit Bank', 1, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled-detail', 'RCPT_NM', 'Recipient Name', 2, 'Y', 'Y', '350px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled-detail', 'TRAN_AMT', 'Transfer Amount', 3, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled-detail', 'TRAN_USAG', 'Remarks', 4, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled-detail', 'CHART_ACCT_CD', 'Reporting Account', 5, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled-detail', 'NOTE', 'Description', 6, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled-detail', 'DOC_NO', 'Document No.', 7, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled-detail', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-vendor-payment-scheduled-detail', 'ETC', '', 99, 'Y', 'Y', '60px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-vendor-payment-result
-- Legacy source:   pymn_0002_001   (source area: table1)
-- Refactor target: payment-vendor-payment-result   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 8 rows.
-- Carried (8): TRAN_DT, FILE_NM, SCDUL_PYMN_DETL_NO, TRAN_AMT, PROC_STAT, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-vendor-payment-result', 'TRAN_DT', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result', 'SCDUL_PYMN_DETL_NO', 'No. of Transactions', 3, 'Y', 'Y', '155px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result', 'TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '235px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result', 'PROC_STAT', 'Transfer Status', 5, 'Y', 'Y', '190px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result', 'APPR_DOC_NO', 'Approval Ref.', 6, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result', 'TITL', 'Approval Title', 7, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result', 'ETC', '', 99, 'Y', 'Y', '50px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-vendor-payment-result-detail
-- Legacy source:   pymn_0002_002   (source area: table1)
-- Refactor target: payment-vendor-payment-result-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 12 rows.
-- Carried (12): PROC_DTTM, TAR_BANK_CD, RCPT_NM, TRAN_AMT, TRAN_USAG, RS_CD, RSLT_DTTM, CHART_ACCT_CD, NOTE, DOC_NO, BANK_TRAN_ID, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-vendor-payment-result-detail', 'PROC_DTTM', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'TAR_BANK_CD', 'Deposit Bank', 2, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'RCPT_NM', 'Recipient Name', 3, 'Y', 'Y', '350px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'TRAN_AMT', 'Transfer Amount', 4, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'TRAN_USAG', 'Remarks', 5, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'RS_CD', 'Results', 6, 'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'CHART_ACCT_CD', 'Reporting Account', 8, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'NOTE', 'Description', 9, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'DOC_NO', 'Document No.', 10, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'BANK_TRAN_ID', 'Banking Transaction ID', 11, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-vendor-payment-result-detail', 'ETC', '', 99, 'Y', 'Y', '100px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-expense-payment-scheduled
-- Legacy source:   pymn_0003_001   (source area: table1)
-- Refactor target: payment-expense-payment-scheduled   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 12 rows.
-- Carried (12): CHECKBOX, REG_DTM, FILE_NM, TOT_TRNS_CNT, TOTAL_TRAN_AMT, PAY_DATE, APPR_STTS, DRAFT_USER_NM, APPRVR_NM, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-expense-payment-scheduled', 'CHECKBOX', 'Checkbox', 0, 'Y', 'Y', '40px', 'default', 'N', NULL),
    ('payment-expense-payment-scheduled', 'REG_DTM', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'TOT_TRNS_CNT', 'No. of Transactions', 3, 'Y', 'Y', '180px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'PAY_DATE', 'Scheduled Date', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'APPR_STTS', 'Approval Status', 6, 'Y', 'Y', '155px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'DRAFT_USER_NM', 'Requestor', 7, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'APPRVR_NM', 'Approver', 8, 'Y', 'Y', '175px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'APPR_DOC_NO', 'Approval Ref.', 9, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'TITL', 'Approval Title', 10, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled', 'ETC', '', 99, 'N', 'Y', '150px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-expense-payment-scheduled-detail
-- Legacy source:   pymn_0003_003   (source area: table1)
-- Refactor target: payment-expense-payment-scheduled-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 9 rows.
-- Carried (9): TAR_BANK_CD, RCPT_NM, TRAN_AMT, TRAN_USAG, CHART_ACCT_CD, NOTE, DOC_NO, BANK_TRAN_ID, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-expense-payment-scheduled-detail', 'TAR_BANK_CD', 'Deposit Bank', 1, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled-detail', 'RCPT_NM', 'Recipient Name', 2, 'Y', 'Y', '350px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled-detail', 'TRAN_AMT', 'Transfer Amount', 3, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled-detail', 'TRAN_USAG', 'Remark', 4, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled-detail', 'CHART_ACCT_CD', 'Reporting Account', 5, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled-detail', 'NOTE', 'Description', 6, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled-detail', 'DOC_NO', 'Document No.', 7, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled-detail', 'BANK_TRAN_ID', 'Banking Transaction ID', 8, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-expense-payment-scheduled-detail', 'ETC', '', 99, 'Y', 'Y', '60px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-expense-payment-result
-- Legacy source:   pymn_0004_001   (source area: table1)
-- Refactor target: payment-expense-payment-result   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 8 rows.
-- Carried (8): TRAN_DT, FILE_NM, SCDUL_PYMN_DETL_NO, TRAN_AMT, PROC_STAT, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-expense-payment-result', 'TRAN_DT', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-expense-payment-result', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-expense-payment-result', 'SCDUL_PYMN_DETL_NO', 'No. of Transactions', 3, 'Y', 'Y', '155px', 'default', 'Y', NULL),
    ('payment-expense-payment-result', 'TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '235px', 'default', 'Y', NULL),
    ('payment-expense-payment-result', 'PROC_STAT', 'Transfer Status', 5, 'Y', 'Y', '190px', 'default', 'Y', NULL),
    ('payment-expense-payment-result', 'APPR_DOC_NO', 'Approval Ref.', 6, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-expense-payment-result', 'TITL', 'Approval Title', 7, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-expense-payment-result', 'ETC', '', 99, 'Y', 'Y', '50px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-expense-payment-result-detail
-- Legacy source:   pymn_0004_002   (source area: table1)
-- Refactor target: payment-expense-payment-result-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 12 rows.
-- Carried (12): PROC_DTTM, TAR_BANK_CD, RCPT_NM, TRAN_AMT, TRAN_USAG, RS_CD, RSLT_DTTM, CHART_ACCT_CD, NOTE, DOC_NO, BANK_TRAN_ID, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-expense-payment-result-detail', 'PROC_DTTM', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'TAR_BANK_CD', 'Deposit Bank', 2, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'RCPT_NM', 'Recipient Name', 3, 'Y', 'Y', '350px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'TRAN_AMT', 'Transfer Amount', 4, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'TRAN_USAG', 'Remarks', 5, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'RS_CD', 'Results', 6, 'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'CHART_ACCT_CD', 'Reporting Account', 8, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'NOTE', 'Description', 9, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'DOC_NO', 'Document No.', 10, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'BANK_TRAN_ID', 'Banking Transaction ID', 11, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-expense-payment-result-detail', 'ETC', '', 99, 'Y', 'Y', '100px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-payroll-payment-scheduled
-- Legacy source:   pymn_0005_001   (source area: table1)
-- Refactor target: payment-payroll-payment-scheduled   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 13 rows.
-- Carried (13): CHECKBOX, REG_DTM, FILE_NM, TOT_TRNS_CNT, TOTAL_TRAN_AMT, PAY_DATE, APPR_STTS, APPR_FUND_TP, DRAFT_USER_NM, APPRVR_NM, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-payroll-payment-scheduled', 'CHECKBOX', 'Checkbox', 0, 'Y', 'Y', '40px', 'default', 'N', NULL),
    ('payment-payroll-payment-scheduled', 'REG_DTM', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'TOT_TRNS_CNT', 'No. of Transactions', 3, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'PAY_DATE', 'Scheduled Date', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'APPR_STTS', 'Approval Status', 6, 'Y', 'Y', '170px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'APPR_FUND_TP', 'Payroll Type', 7, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'DRAFT_USER_NM', 'Requestor', 8, 'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'APPRVR_NM', 'Approver', 9, 'Y', 'Y', '175px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'APPR_DOC_NO', 'Approval Ref.', 10, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'TITL', 'Approval Title', 11, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled', 'ETC', '', 99, 'N', 'Y', '150px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-payroll-payment-scheduled-detail
-- Legacy source:   pymn_0005_003   (source area: table1)
-- Refactor target: payment-payroll-payment-scheduled-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 6 rows.
-- Carried (6): TAR_BANK_CD, RCPT_NM, TRAN_AMT, TRAN_USAG, CHART_ACCT_CD, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-payroll-payment-scheduled-detail', 'TAR_BANK_CD', 'Deposit Bank', 1, 'Y', 'Y', '180px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled-detail', 'RCPT_NM', 'Recipient Name', 2, 'Y', 'Y', '360px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled-detail', 'TRAN_AMT', 'Transfer Amount', 3, 'Y', 'Y', '110px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled-detail', 'TRAN_USAG', 'Remarks', 4, 'Y', 'Y', '360px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled-detail', 'CHART_ACCT_CD', 'Reporting Account', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-payroll-payment-scheduled-detail', 'ETC', '', 99, 'N', 'Y', '60px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-payroll-payment-result
-- Legacy source:   pymn_0006_001   (source area: table1)
-- Refactor target: payment-payroll-payment-result   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 9 rows.
-- Carried (9): TRAN_DT, FILE_NM, SCDUL_PYMN_DETL_NO, TRAN_AMT, APPR_FUND_TP, PROC_STAT, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-payroll-payment-result', 'TRAN_DT', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result', 'SCDUL_PYMN_DETL_NO', 'No. of Transactions', 3, 'Y', 'Y', '155px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result', 'TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '235px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result', 'APPR_FUND_TP', 'Payroll Type', 5, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result', 'PROC_STAT', 'Transfer Status', 6, 'Y', 'Y', '190px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result', 'APPR_DOC_NO', 'Approval Ref.', 7, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result', 'TITL', 'Approval Title', 8, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result', 'ETC', '', 99, 'Y', 'Y', '50px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-payroll-payment-result-detail
-- Legacy source:   pymn_0006_002   (source area: table1)
-- Refactor target: payment-payroll-payment-result-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 9 rows.
-- Carried (9): PROC_DTTM, TAR_BANK_CD, RCPT_NM, TRAN_AMT, TRAN_USAG, RS_CD, RSLT_DTTM, CHART_ACCT_CD, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-payroll-payment-result-detail', 'PROC_DTTM', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result-detail', 'TAR_BANK_CD', 'Deposit Bank', 2, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result-detail', 'RCPT_NM', 'Recipient Name', 3, 'Y', 'Y', '330px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result-detail', 'TRAN_AMT', 'Transfer Amount', 4, 'Y', 'Y', '110px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result-detail', 'TRAN_USAG', 'Remarks', 5, 'Y', 'Y', '330px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result-detail', 'RS_CD', 'Results', 6, 'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result-detail', 'RSLT_DTTM', 'Result Date/Time', 7, 'Y', 'Y', '130px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result-detail', 'CHART_ACCT_CD', 'Reporting Account', 8, 'Y', 'Y', '130px', 'default', 'Y', NULL),
    ('payment-payroll-payment-result-detail', 'ETC', '', 99, 'Y', 'Y', '60px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-overseas-payment-scheduled-detail
-- Legacy source:   pymn_0007_004   (source area: table1)
-- Refactor target: payment-overseas-payment-scheduled-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 10 rows.
-- Carried (10): BNFC_NM, BNFC_BANK_NM, BNFC_ACCT_NO, BNFC_ADDR, CHART_ACCT_CD, TRNF_CCY_CD, TRNF_AMT, FEE_PAYER_CD, MEMO, BANK_TRAN_ID
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-overseas-payment-scheduled-detail', 'BNFC_NM', 'Beneficiary Name', 1, 'Y', 'Y', '180px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'BNFC_BANK_NM', 'Beneficiary Bank Name', 2, 'Y', 'Y', '220px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'BNFC_ACCT_NO', 'Beneficiary Account No.', 3, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'BNFC_ADDR', 'Beneficiary Address', 4, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'CHART_ACCT_CD', 'Reporting Account', 5, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'TRNF_CCY_CD', 'Transfer Currency', 6, 'Y', 'Y', '120px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'TRNF_AMT', 'Transfer Amount', 7, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'FEE_PAYER_CD', 'Fee Payer', 8, 'Y', 'Y', '80px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'MEMO', 'Memo', 9, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled-detail', 'BANK_TRAN_ID', 'Banking Transaction ID', 10, 'Y', 'Y', '250px', 'default', 'Y', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-overseas-payment-result
-- Legacy source:   pymn_0008_001   (source area: table2)
-- Refactor target: payment-overseas-payment-result   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 8 rows.
-- Note:            source table2 de-suffixed (_2 stripped) — matches FALLBACK
-- Carried (8): TRAN_DT, FILE_NM, SCDUL_PYMN_DETL_NO, TRAN_AMT, PROC_STAT, APPR_DOC_NO, TITL, ETC
-- Dropped:         legacy table2 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-overseas-payment-result', 'TRAN_DT', 'Date/Time', 1, 'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result', 'FILE_NM', 'File Name', 2, 'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result', 'SCDUL_PYMN_DETL_NO', 'No. of Transactions', 3, 'Y', 'Y', '155px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result', 'TRAN_AMT', 'Total Transfer Amount', 4, 'Y', 'Y', '235px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result', 'PROC_STAT', 'Transfer Status', 5, 'Y', 'Y', '190px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result', 'APPR_DOC_NO', 'Approval Ref.', 6, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result', 'TITL', 'Approval Title', 7, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result', 'ETC', '', 99, 'Y', 'Y', '50px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- payment-overseas-payment-result-detail
-- Legacy source:   pymn_0008_002   (source area: table1)
-- Refactor target: payment-overseas-payment-result-detail   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 14 rows.
-- Carried (14): PAY_TRAN_SEQ, BNFC_NM, BNFC_BANK_NM, BNFC_ACCT_NO, BNFC_ADDR, TRNF_CCY_CD, TRNF_AMT, FEE_PAYER, MEMO, CHART_ACCT_CD, PROC_STAT, RSLT_DTTM, BANK_TRAN_ID, ETC
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-overseas-payment-result-detail', 'PAY_TRAN_SEQ', 'Transaction Seq', 1, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'BNFC_NM', 'Beneficiary Name', 2, 'Y', 'Y', '220px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'BNFC_BANK_NM', 'Beneficiary Bank Name', 3, 'Y', 'Y', '225px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'BNFC_ACCT_NO', 'Beneficiary Account No.', 4, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'BNFC_ADDR', 'Beneficiary Address', 5, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'TRNF_CCY_CD', 'Transfer Currency', 6, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'TRNF_AMT', 'Transfer Amount', 7, 'Y', 'Y', '160px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'FEE_PAYER', 'Fee Payer', 8, 'Y', 'Y', '120px', 'default', 'N', NULL),
    ('payment-overseas-payment-result-detail', 'MEMO', 'Memo', 9, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'CHART_ACCT_CD', 'Reporting Account', 10, 'Y', 'Y', '300px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'PROC_STAT', 'Status', 11, 'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'RSLT_DTTM', 'Result Date/Time', 12, 'Y', 'Y', '130px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'BANK_TRAN_ID', 'Banking Transaction ID', 13, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-overseas-payment-result-detail', 'ETC', '', 99, 'Y', 'Y', '100px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- card-transactions
-- Legacy source:   card_0001_001   (source area: table1)
-- Refactor target: card-transactions   (dym_area_id = 'default')
-- Field set:       refactor FALLBACK (authoritative). 15 rows.
-- Note:            field_ids reused from legacy (CHECKBOX_1 / MEMO are specials); sourced from legacy table1 (no inline FALLBACK array)
-- Carried (15): CHECKBOX_1, TRSC_DT, CARD_NO, MERCH_NM, CARD_NICK_NM, CARD_HLDR_NM, CCY_CD, TRSC_AMT, CHART_ACCT_NM, STTS, CARD_TRNS_USAGE_NM, DISP_DOC_ID, CONT, APPR_DOC_NO, MEMO
-- Dropped:         legacy table1 extras + all other legacy areas (refactor = single 'default' grid)
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('card-transactions', 'CHECKBOX', 'CHECKBOX', 0, 'Y', 'Y', '40px', 'default', 'Y', NULL),
    ('card-transactions', 'TRSC_DT', 'Date/Time', 1, 'Y', 'Y', '120px', 'default', 'Y', NULL),
    ('card-transactions', 'CARD_NO', 'Card Number', 2, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('card-transactions', 'MERCH_NM', 'Merchant', 3, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('card-transactions', 'CARD_NICK_NM', 'Card Nickname', 4, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('card-transactions', 'CARD_HLDR_NM', 'Cardholder', 5, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('card-transactions', 'CCY_CD', 'Currency Code', 6, 'Y', 'Y', '110px', 'default', 'Y', NULL),
    ('card-transactions', 'TRSC_AMT', 'Amount', 7, 'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('card-transactions', 'CHART_ACCT_NM', 'Reporting Account', 8, 'Y', 'Y', '220px', 'default', 'Y', NULL),
    ('card-transactions', 'STTS', 'Status', 9, 'Y', 'Y', '120px', 'default', 'Y', NULL),
    ('card-transactions', 'CARD_TRNS_USAGE_NM', 'Purpose', 10, 'Y', 'Y', '220px', 'default', 'Y', NULL),
    ('card-transactions', 'DISP_DOC_ID', 'Link Reference', 11, 'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('card-transactions', 'CONT', 'Description', 12, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('card-transactions', 'APPR_DOC_NO', 'Approval Ref.', 13, 'Y', 'Y', '180px', 'default', 'Y', NULL),
    ('card-transactions', 'MEMO', 'MEMO', 100, 'Y', 'Y', '38px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- Overseas Scheduled Payment
-- Legacy source:   pymn_0007_001   (source area: table2 — per explicit instruction)
-- Refactor target: payment-overseas-payment-scheduled   (dym_area_id = 'default')
-- Conversion:      table2 de-suffixed (_2 stripped). 12 rows.
-- Carried (12):    CHECKBOX, REG_DTM, FILE_NM, TOT_TRNS_CNT, TOTAL_TRAN_AMT,
--                  APPR_STTS, PAY_DATE, DRAFT_USER_NM, APPRVR_NM, APPR_DOC_NO,
--                  TITL, ETC
--
-- *** WARNING — DOES NOT MATCH THE CURRENT REFACTOR FALLBACK ***
--   Seeded from legacy table2 (batch-file column set) by explicit instruction.
--   The refactor ScheduledPaymentPage FALLBACK for this slug is the FX set
--   (TRNF_DT, DBT_ACCT_NO, DBT_CCY_CD, BNFC_*, TRNF_*, FEE_PAYER_CD, MEMO,
--   USER_NM, APPRVR_ID). Only CHECKBOX / APPR_STTS / ETC overlap, so the
--   frontend (which filters by its registry field_ids) will NOT render these
--   batch columns until the FE adopts a batch column set for this screen.
--   The FX set matches legacy table1. Revisit before relying on this page.
-- NOTE: legacy table2 has a DUPLICATE disp_ord (APPR_STTS=5 AND PAY_DATE=5;
--   6 unused) — carried verbatim.
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('payment-overseas-payment-scheduled', 'CHECKBOX',       'CHECKBOX',              0,  'Y', 'Y', '40px',  'default', 'N', NULL),
    ('payment-overseas-payment-scheduled', 'REG_DTM',        'Date/Time',             1,  'Y', 'Y', '105px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'FILE_NM',        'File Name',             2,  'Y', 'Y', '290px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'TOT_TRNS_CNT',   'No. of Transactions',   3,  'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'TOTAL_TRAN_AMT', 'Total Transfer Amount', 4,  'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'APPR_STTS',      'Approval Status',       5,  'Y', 'Y', '170px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'PAY_DATE',       'Schedule Date',         5,  'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'DRAFT_USER_NM',  'Requestor',             7,  'Y', 'Y', '100px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'APPRVR_NM',      'Approver',              8,  'Y', 'N', '175px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'APPR_DOC_NO',    'Approval Ref',          9,  'Y', 'Y', '150px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'TITL',           'Approval Title',        10, 'Y', 'Y', '250px', 'default', 'Y', NULL),
    ('payment-overseas-payment-scheduled', 'ETC',            'ETC',                   99, 'Y', 'Y', '150px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;


-- ----------------------------------------------------------------------------
-- Bank Account (Settings)
-- Legacy source:   comm_0183_001   (source area: table1)
-- Refactor target: bank-account   (dym_area_id = 'default')
-- Frontend:        useViewSetting(SCREEN_IDS.SETTINGS_BANK_ACCOUNT, DEFAULT_AREA_ID)
--                  components/settings/bank-account/BankAccountPanel.tsx:126
-- REDESIGN: the refactor uses its own semantic field_ids (no alias), so seeded
--   from the refactor FALLBACK_COLUMNS (FE-authoritative), NOT the raw legacy
--   ids — those would not render.
-- Legacy -> refactor field_id mapping:
--   ACCT_NO->BANK, ACCT_NICK_NM->ACCOUNT_NICKNAME, CONT->DESCRIPTION,
--   ACCT_TYPE_CD->ACCOUNT_TYPE, EXPI_DT->MATURITY_DATE, VAS_ACCT_CD->VAS_CODE,
--   VAS_ACCT_NM->VAS_NAME, VIEW_PERM->VIEW_PERMISSION,
--   WITH_PERM->WITHDRAWAL_PERMISSION, USER_NM->AUDIT_LOGS;
--   refactor ADDS CHECKBOX (special, disp_ord 0).
-- Carried (11): CHECKBOX, BANK, ACCOUNT_NICKNAME, DESCRIPTION, ACCOUNT_TYPE,
--   MATURITY_DATE, VAS_CODE, VAS_NAME, VIEW_PERMISSION, WITHDRAWAL_PERMISSION,
--   AUDIT_LOGS
-- ----------------------------------------------------------------------------
INSERT INTO sys_scr_option_wecms
    (web_svc_id, field_id, field_nm, disp_ord, disp_yn, use_yn, "size", dym_area_id, sort_yn, html_cont)
VALUES
    ('bank-account', 'CHECKBOX',              'Select row',            0,  'Y', 'Y', '38px',  'default', 'N', NULL),
    ('bank-account', 'BANK',                  'Accounts',              1,  'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('bank-account', 'ACCOUNT_NICKNAME',      'Account Nickname',      2,  'Y', 'Y', '180px', 'default', 'Y', NULL),
    ('bank-account', 'DESCRIPTION',           'Description',           3,  'Y', 'Y', '180px', 'default', 'Y', NULL),
    ('bank-account', 'ACCOUNT_TYPE',          'Account Type',          4,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('bank-account', 'MATURITY_DATE',         'Maturity Date',         5,  'Y', 'Y', '140px', 'default', 'Y', NULL),
    ('bank-account', 'VAS_CODE',              'VAS Code',              6,  'Y', 'Y', '120px', 'default', 'Y', NULL),
    ('bank-account', 'VAS_NAME',              'VAS',                   7,  'Y', 'Y', '200px', 'default', 'Y', NULL),
    ('bank-account', 'VIEW_PERMISSION',       'View Permission',       8,  'Y', 'Y', '180px', 'default', 'N', NULL),
    ('bank-account', 'WITHDRAWAL_PERMISSION', 'Withdrawal Permission', 9,  'Y', 'Y', '180px', 'default', 'N', NULL),
    ('bank-account', 'AUDIT_LOGS',            'Audit Logs',            10, 'Y', 'Y', '180px', 'default', 'N', NULL)
ON CONFLICT (web_svc_id, field_id) DO NOTHING;

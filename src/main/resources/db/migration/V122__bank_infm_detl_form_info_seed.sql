-- ============================================================================
-- V122__bank_infm_detl_form_info_seed.sql
--
-- Login Credentials > Add Bank > Login ID/PW form — seed per-bank
-- form-info rows so the dialog can render bank-specific labels +
-- sub-type radios (Corporate / Personal Banking, VCB iB@nking / DigiBiz,
-- etc.).
--
-- BANK_DETL_INFO_CD reference (Coocon legacy convention):
--   '12' → bank logo path (uses {LANG_CODE} placeholder)
--   '13' → ID-field label
--   '16' → Password-field label
--   '19' → Confirm-Password-field label
--   '22' → Corporate ID / extra-field label (blank when not used)
--   '25' → Sub-type name (e.g. "Corporate Banking", "Personal Banking")
--
-- The legacy nxisas_getCertFormInfo query groups these by (BANK_CD,
-- SEQ_NO) to produce one sub-type record per group. When a bank has
-- multiple SEQ_NOs, the dialog renders radio buttons; when a single
-- SEQ_NO, no radios appear.
--
-- Rows are pulled VERBATIM from DEV (snapshot 2026-05-14 via the
-- read-only postgres_dev MCP). Logo paths point at
-- /banklogo/<filename> — the legacy /assets_v2/images/banking_VN/...
-- prefix was rewritten because the refactor stores those PNG assets
-- under public/banklogo/ in wecms-web-main. The {LANG_CODE} marker
-- stays as-is; the frontend substitutes EN / KO / VI at render time.
--
-- Idempotent via ON CONFLICT DO NOTHING. The PK is
-- (BANK_CD, BANK_DETL_INFO_CD, START_DATE), so re-running is a no-op.
-- ============================================================================

INSERT INTO BANK_INFM_DETL
    (BANK_CD, BANK_DETL_INFO_CD, START_DATE, END_DATE, BANK_DETL_INFO_CONT, REG_ID, REG_DTM, MOD_ID, MOD_DTM, SEQ_NO)
VALUES
    -- VIETIN (11704001) — Corporate + Personal Banking
    ('11704001', '12', '20210801', '99991231', '/banklogo/Vietinbank_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704001', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704001', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704001', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704001', '22', '20210801', '99991231', '',                  'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704001', '25', '20210801', '99991231', 'Corporate Banking', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704001', '12', '20210802', '99991231', '/banklogo/VietinBank_Personal_{LANG_CODE}.png', 'rothnak', '20240802083722', NULL, NULL, 1),
    ('11704001', '13', '20210802', '99991231', 'Tên đăng nhập',     'rothnak', '20240802083722', NULL, NULL, 1),
    ('11704001', '16', '20210802', '99991231', 'Mật khẩu',          'rothnak', '20240802083722', NULL, NULL, 1),
    ('11704001', '19', '20210802', '99991231', 'Xác nhận mật khẩu', 'rothnak', '20240802083722', NULL, NULL, 1),
    ('11704001', '22', '20210802', '99991231', '',                  'rothnak', '20240802083722', NULL, NULL, 1),
    ('11704001', '25', '20210802', '99991231', 'Personal Banking',  'rothnak', '20240802083722', NULL, NULL, 1),

    -- TECHCOM (11704002) — Corporate + Personal
    ('11704002', '12', '20210801', '99991231', '/banklogo/Techcombank_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704002', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704002', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704002', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704002', '22', '20210801', '99991231', '',                  'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704002', '25', '20210801', '99991231', 'Corporate Banking', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704002', '12', '20210802', '99991231', '/banklogo/Techcombank_Personal_{LANG_CODE}.png', 'soknyran', '20241018074451', NULL, NULL, 1),
    ('11704002', '13', '20210802', '99991231', 'Tên truy cập',      'soknyran', '20241018074451', NULL, NULL, 1),
    ('11704002', '16', '20210802', '99991231', 'Mật khẩu',          'soknyran', '20241018074451', NULL, NULL, 1),
    ('11704002', '19', '20210802', '99991231', 'Xác nhận mật khẩu', 'soknyran', '20241018074451', NULL, NULL, 1),
    ('11704002', '22', '20210802', '99991231', '',                  'soknyran', '20241018074451', NULL, NULL, 1),
    ('11704002', '25', '20210802', '99991231', 'Personal Banking',  'soknyran', '20241018074451', NULL, NULL, 1),

    -- IBK VN (11704003) — single Corporate
    ('11704003', '12', '20210801', '99991231', '/banklogo/IBK_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704003', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704003', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704003', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704003', '22', '20210801', '99991231', '',                  'SYSTEM', '20220321204500', NULL, NULL, 0),

    -- Vietcom (11704004) — VCB iB@nking + VCB DigiBiz + Personal Banking (THREE sub-types)
    ('11704004', '12', '20210801', '99991231', '/banklogo/Vietcombank_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704004', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704004', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704004', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704004', '22', '20210801', '99991231', '',                  'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704004', '25', '20210801', '99991231', 'VCB iB@nking',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704004', '12', '20210802', '99991231', '/banklogo/VCB_Dizbiz_login.png', 'ktr2006', '20220714051303', NULL, NULL, 1),
    ('11704004', '13', '20210802', '99991231', 'Tên truy cập',      'ktr2006', '20220714051303', NULL, NULL, 1),
    ('11704004', '16', '20210802', '99991231', 'Mật khẩu',          'ktr2006', '20220714051303', NULL, NULL, 1),
    ('11704004', '19', '20210802', '99991231', 'Xác nhận mật khẩu', 'ktr2006', '20220714051303', NULL, NULL, 1),
    ('11704004', '22', '20210802', '99991231', '',                  'ktr2006', '20220714051303', NULL, NULL, 1),
    ('11704004', '25', '20210802', '99991231', 'VCB DigiBiz',       'ktr2006', '20220714051303', NULL, NULL, 1),
    ('11704004', '12', '20210803', '99991231', '/banklogo/Vietcombank_Personal_{LANG_CODE}.png', 'soknyran', '20240805053135', NULL, NULL, 2),
    ('11704004', '13', '20210803', '99991231', 'Tên truy cập',      'soknyran', '20240805053135', NULL, NULL, 2),
    ('11704004', '16', '20210803', '99991231', 'Mật khẩu',          'soknyran', '20240805053135', NULL, NULL, 2),
    ('11704004', '19', '20210803', '99991231', 'Xác nhận mật khẩu', 'soknyran', '20240805053135', NULL, NULL, 2),
    ('11704004', '22', '20210803', '99991231', '',                  'soknyran', '20240805053135', NULL, NULL, 2),
    ('11704004', '25', '20210803', '99991231', 'Personal Banking',  'soknyran', '20240805053135', NULL, NULL, 2),

    -- MB (11704007) — Corporate (with Corporate ID extra) + Personal
    ('11704007', '12', '20210801', '99991231', '/banklogo/Mb_bank_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704007', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704007', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704007', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704007', '22', '20210801', '99991231', 'Mã công ty',        'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704007', '25', '20210801', '99991231', 'Corporate Banking', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704007', '12', '20210802', '99991231', '/banklogo/MB_Personal_{LANG_CODE}.png', 'soknyran', '20241018084440', NULL, NULL, 1),
    ('11704007', '13', '20210802', '99991231', 'Tên truy cập',      'soknyran', '20241018084440', NULL, NULL, 1),
    ('11704007', '16', '20210802', '99991231', 'Mật khẩu',          'soknyran', '20241018084440', NULL, NULL, 1),
    ('11704007', '19', '20210802', '99991231', 'Xác nhận mật khẩu', 'soknyran', '20241018084440', NULL, NULL, 1),
    ('11704007', '22', '20210802', '99991231', '',                  'soknyran', '20241018084440', NULL, NULL, 1),
    ('11704007', '25', '20210802', '99991231', 'Personal Banking',  'soknyran', '20241018084440', NULL, NULL, 1),

    -- BIDV (11704008) — Corporate + Personal
    ('11704008', '12', '20210801', '99991231', '/banklogo/BIDV_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704008', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704008', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704008', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704008', '22', '20210801', '99991231', '',                  'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704008', '25', '20210801', '99991231', 'Corporate Banking', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704008', '12', '20210802', '99991231', '/banklogo/BIDV_Personal_{LANG_CODE}.png', 'soknyran', '20241018081644', NULL, NULL, 1),
    ('11704008', '13', '20210802', '99991231', 'Tên truy cập',      'soknyran', '20241018081644', NULL, NULL, 1),
    ('11704008', '16', '20210802', '99991231', 'Mật khẩu',          'soknyran', '20241018081644', NULL, NULL, 1),
    ('11704008', '19', '20210802', '99991231', 'Xác nhận mật khẩu', 'soknyran', '20241018081644', NULL, NULL, 1),
    ('11704008', '22', '20210802', '99991231', '',                  'soknyran', '20241018081644', NULL, NULL, 1),
    ('11704008', '25', '20210802', '99991231', 'Personal Banking',  'soknyran', '20241018081644', NULL, NULL, 1),

    -- ACB (11704011) — single Corporate
    ('11704011', '12', '20210801', '99991231', '/banklogo/ACB_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704011', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704011', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704011', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704011', '22', '20210801', '99991231', '',                  'SYSTEM', '20220321204500', NULL, NULL, 0),

    -- VIB (11704012)
    ('11704012', '12', '20210801', '99991231', '/banklogo/VIB_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704012', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704012', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704012', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),

    -- KB VN (11704013)
    ('11704013', '12', '20210801', '99991231', '/banklogo/KB_Bank_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704013', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704013', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704013', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704013', '22', '20210801', '99991231', '',                  'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704013', '25', '20210801', '99991231', '국민',              'SYSTEM', '20220321204500', NULL, NULL, 0),

    -- MSB (11704014)
    ('11704014', '12', '20210801', '99991231', '/banklogo/Msb_Bank_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704014', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704014', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704014', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),

    -- Woori VN (11704020)
    ('11704020', '12', '20210801', '99991231', '/banklogo/Woori_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704020', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704020', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704020', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),

    -- KEBHana (11704080)
    ('11704080', '12', '20210801', '99991231', '/banklogo/Keb_Hana_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704080', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704080', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704080', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),

    -- Shinhan VN (11704088) — Corporate + Personal
    ('11704088', '12', '20210801', '99991231', '/banklogo/Shinhan_{LANG_CODE}.png', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704088', '13', '20210801', '99991231', 'Tên truy cập',      'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704088', '16', '20210801', '99991231', 'Mật khẩu',          'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704088', '19', '20210801', '99991231', 'Xác nhận mật khẩu', 'SYSTEM', '20220321205700', NULL, NULL, 0),
    ('11704088', '22', '20210801', '99991231', '',                  'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704088', '25', '20210801', '99991231', 'Corporate Banking', 'SYSTEM', '20220321204500', NULL, NULL, 0),
    ('11704088', '12', '20210802', '99991231', '/banklogo/Shinhan_Personal_{LANG_CODE}.png', 'soknyran', '20240805064411', NULL, NULL, 1),
    ('11704088', '13', '20210802', '99991231', 'Tên truy cập',      'soknyran', '20240805064411', NULL, NULL, 1),
    ('11704088', '16', '20210802', '99991231', 'Mật khẩu',          'soknyran', '20240805064411', NULL, NULL, 1),
    ('11704088', '19', '20210802', '99991231', 'Xác nhận mật khẩu', 'soknyran', '20240805064411', NULL, NULL, 1),
    ('11704088', '22', '20210802', '99991231', '',                  'soknyran', '20240805064411', NULL, NULL, 1),
    ('11704088', '25', '20210802', '99991231', 'Personal Banking',  'soknyran', '20240805064411', NULL, NULL, 1),

    -- Agribank (11704100) — Corporate (with Corporate ID extra) + Personal
    ('11704100', '12', '20220713', '99991231', '/banklogo/Agribank_{LANG_CODE}.png', 'ktr2006', '20220713043219', NULL, NULL, 0),
    ('11704100', '13', '20220713', '99991231', 'User name',           'ktr2006', '20220713043219', NULL, NULL, 0),
    ('11704100', '16', '20220713', '99991231', 'Login password',      'ktr2006', '20220713043219', NULL, NULL, 0),
    ('11704100', '19', '20220713', '99991231', 'Password Confirmation', 'ktr2006', '20220713043219', NULL, NULL, 0),
    ('11704100', '22', '20220713', '99991231', 'Corporate ID',        'ktr2006', '20220713043219', NULL, NULL, 0),
    ('11704100', '25', '20220713', '99991231', 'Corporate Banking',   'soknyran', '20240507061035', NULL, NULL, 0),
    ('11704100', '12', '20220714', '99991231', '/banklogo/Agribank_Personal_{LANG_CODE}.png', 'soknyran', '20241018090207', NULL, NULL, 1),
    ('11704100', '13', '20220714', '99991231', 'User name',           'soknyran', '20241018090207', NULL, NULL, 1),
    ('11704100', '16', '20220714', '99991231', 'Login password',      'soknyran', '20241018090207', NULL, NULL, 1),
    ('11704100', '19', '20220714', '99991231', 'Password Confirmation', 'soknyran', '20241018090207', NULL, NULL, 1),
    ('11704100', '22', '20220714', '99991231', '',                    'soknyran', '20241018090207', NULL, NULL, 1),
    ('11704100', '25', '20220714', '99991231', 'Personal Banking',    'soknyran', '20241018090207', NULL, NULL, 1),

    -- HD (11704101) — Corporate + Personal
    ('11704101', '12', '20220713', '99991231', '/banklogo/HDBank_login.png', 'ktr2006', '20220713043309', NULL, NULL, 0),
    ('11704101', '13', '20220713', '99991231', 'User ID',              'ktr2006', '20220713043309', NULL, NULL, 0),
    ('11704101', '16', '20220713', '99991231', 'Password',             'ktr2006', '20220713043309', NULL, NULL, 0),
    ('11704101', '19', '20220713', '99991231', 'Password Confirmation','ktr2006', '20220713043309', NULL, NULL, 0),
    ('11704101', '22', '20220713', '99991231', '',                     'ktr2006', '20220713043309', NULL, NULL, 0),
    ('11704101', '25', '20220713', '99991231', 'Corporate Banking',    'soknyran', '20241018082935', NULL, NULL, 0),
    ('11704101', '12', '20220714', '99991231', '/banklogo/HDbank_Personal_{LANG_CODE}.png', 'soknyran', '20241018082913', NULL, NULL, 1),
    ('11704101', '13', '20220714', '99991231', 'User ID',              'soknyran', '20241018082913', NULL, NULL, 1),
    ('11704101', '16', '20220714', '99991231', 'Password',             'soknyran', '20241018082913', NULL, NULL, 1),
    ('11704101', '19', '20220714', '99991231', 'Password Confirmation','soknyran', '20241018082913', NULL, NULL, 1),
    ('11704101', '22', '20220714', '99991231', '',                     'soknyran', '20241018082913', NULL, NULL, 1),
    ('11704101', '25', '20220714', '99991231', 'Personal Banking',     'soknyran', '20241018082913', NULL, NULL, 1),

    -- VP (11704102) — Corporate + Personal
    ('11704102', '12', '20220728', '99991231', '/banklogo/VP_EN.png',  'ktr2006', '20220914013429', NULL, NULL, 0),
    ('11704102', '13', '20220728', '99991231', 'Username',             'ktr2006', '20220914013429', NULL, NULL, 0),
    ('11704102', '16', '20220728', '99991231', 'Password',             'ktr2006', '20220914013429', NULL, NULL, 0),
    ('11704102', '19', '20220728', '99991231', 'Password Confirmation','ktr2006', '20220914013429', NULL, NULL, 0),
    ('11704102', '22', '20220728', '99991231', '',                     'ktr2006', '20220914013429', NULL, NULL, 0),
    ('11704102', '25', '20220728', '99991231', 'Corporate Banking',    'ktr2006', '20220914012713', NULL, NULL, 0),
    ('11704102', '12', '20220729', '99991231', '/banklogo/VP_Personal_{LANG_CODE}.png', 'soknyran', '20240805055458', NULL, NULL, 1),
    ('11704102', '13', '20220729', '99991231', 'Tên đăng nhập',        'soknyran', '20240805055458', NULL, NULL, 1),
    ('11704102', '16', '20220729', '99991231', 'Mật khẩu',             'soknyran', '20240805055458', NULL, NULL, 1),
    ('11704102', '19', '20220729', '99991231', 'Xác nhận mật khẩu',    'soknyran', '20240805055458', NULL, NULL, 1),
    ('11704102', '22', '20220729', '99991231', '',                     'soknyran', '20240805055458', NULL, NULL, 1),
    ('11704102', '25', '20220729', '99991231', 'Personal Banking',     'soknyran', '20240805055458', NULL, NULL, 1),

    -- TP (11704103) — Corporate + Personal
    ('11704103', '12', '20220728', '99991231', '/banklogo/TP_EN.png',  'ktr2006', '20220914013526', NULL, NULL, 0),
    ('11704103', '13', '20220728', '99991231', 'Username',             'ktr2006', '20220914013526', NULL, NULL, 0),
    ('11704103', '16', '20220728', '99991231', 'Password',             'ktr2006', '20220914013526', NULL, NULL, 0),
    ('11704103', '19', '20220728', '99991231', 'Password Confirmation','ktr2006', '20220914013526', NULL, NULL, 0),
    ('11704103', '22', '20220728', '99991231', '',                     'ktr2006', '20220914013526', NULL, NULL, 0),
    ('11704103', '25', '20220728', '99991231', 'Corporate Banking',    'ktr2006', '20220914012740', NULL, NULL, 0),
    ('11704103', '12', '20220729', '99991231', '/banklogo/TP_Personal_{LANG_CODE}.png', 'soknyran', '20240805063031', NULL, NULL, 1),
    ('11704103', '13', '20220729', '99991231', 'Username',             'soknyran', '20240805063031', NULL, NULL, 1),
    ('11704103', '16', '20220729', '99991231', 'Password',             'soknyran', '20240805063031', NULL, NULL, 1),
    ('11704103', '19', '20220729', '99991231', 'Password Confirmation','soknyran', '20240805063031', NULL, NULL, 1),
    ('11704103', '22', '20220729', '99991231', '',                     'soknyran', '20240805063031', NULL, NULL, 1),
    ('11704103', '25', '20220729', '99991231', 'Personal Banking',     'soknyran', '20240805063031', NULL, NULL, 1),

    -- SC VN (11704104) — single
    ('11704104', '12', '20230516', '99991231', '/banklogo/SC_BANK_{LANG_CODE}.png', 'orltokata', '20240212033522', NULL, NULL, 0),
    ('11704104', '13', '20230516', '99991231', 'Tên truy cập',          'orltokata', '20240212033522', NULL, NULL, 0),
    ('11704104', '16', '20230516', '99991231', 'Group ID',              'orltokata', '20240212033522', NULL, NULL, 0),
    ('11704104', '19', '20230516', '99991231', 'Group ID Confirmation', 'orltokata', '20240212033522', NULL, NULL, 0),
    ('11704104', '22', '20230516', '99991231', '',                      'orltokata', '20240212033522', NULL, NULL, 0),

    -- SHB (11704105) — single
    ('11704105', '12', '20240216', '99991231', '/banklogo/SHB_JOIN_STOCK_{LANG_CODE}.png', 'orltokata', '20240216100416', NULL, NULL, 0),
    ('11704105', '13', '20240216', '99991231', 'Tên truy cập',           'orltokata', '20240216100416', NULL, NULL, 0),
    ('11704105', '16', '20240216', '99991231', 'Mật khẩu',               'orltokata', '20240216100416', NULL, NULL, 0),
    ('11704105', '19', '20240216', '99991231', 'Xác nhận mật khẩu',      'orltokata', '20240216100416', NULL, NULL, 0),
    ('11704105', '22', '20240216', '99991231', '',                       'orltokata', '20240216100416', NULL, NULL, 0),

    -- ABBANK (11704106) — single (Login Type dropdown via hardcoded UI hint)
    ('11704106', '12', '20240216', '99991231', '/banklogo/AB_BANK_{LANG_CODE}.png', 'rothnak', '20240216023423', NULL, NULL, 0),
    ('11704106', '13', '20240216', '99991231', 'Tên truy cập',           'rothnak', '20240216023423', NULL, NULL, 0),
    ('11704106', '16', '20240216', '99991231', 'Mật khẩu',               'rothnak', '20240216023423', NULL, NULL, 0),
    ('11704106', '19', '20240216', '99991231', 'Xác nhận mật khẩu',      'rothnak', '20240216023423', NULL, NULL, 0),
    ('11704106', '22', '20240216', '99991231', '',                       'rothnak', '20240216023423', NULL, NULL, 0),

    -- PG Bank (11704108) — single
    ('11704108', '12', '20240409', '99991231', '/banklogo/PG_Bank_{LANG_CODE}.png', 'orltokata', '20240429041505', NULL, NULL, 0),
    ('11704108', '13', '20240409', '99991231', 'Tên truy cập',           'orltokata', '20240429041505', NULL, NULL, 0),
    ('11704108', '16', '20240409', '99991231', 'Mật khẩu',               'orltokata', '20240429041505', NULL, NULL, 0),
    ('11704108', '19', '20240409', '99991231', 'Xác nhận mật khẩu',      'orltokata', '20240429041505', NULL, NULL, 0),
    ('11704108', '22', '20240409', '99991231', '',                       'orltokata', '20240429041505', NULL, NULL, 0),

    -- Eximbank (11704112) — single
    ('11704112', '12', '20250411', '99991231', '/banklogo/Eximbank_{LANG_CODE}.png', 'Channach', '20250411111156', NULL, NULL, 0),
    ('11704112', '13', '20250411', '99991231', 'Tên đăng nhập',          'Channach', '20250411111156', NULL, NULL, 0),
    ('11704112', '16', '20250411', '99991231', 'Mật khẩu',               'Channach', '20250411111156', NULL, NULL, 0),
    ('11704112', '19', '20250411', '99991231', 'Xác nhận mật khẩu',      'Channach', '20250411111156', NULL, NULL, 0),
    ('11704112', '22', '20250411', '99991231', '',                       'Channach', '20250411111156', NULL, NULL, 0)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- V201 — NATN_INFM seed
--
-- Generic country directory. Mirrors the project's existing flag-asset
-- set under `wecms-web/public/assets/flag/` so any future consumer of
-- `GET /api/v1/common/countries` has a real glyph available. The
-- Settings > Users phone picker restricts the visible set to KH / KR /
-- VN on the FRONTEND (per-screen allow-list inside `PhoneCountryPicker.tsx`),
-- not at the API layer — keeping the directory full lets other screens
-- (overseas payment beneficiary country, contacts overseas info, etc.)
-- consume the same endpoint with a different subset.
--
-- NTNL_CD: ISO 3166-1 alpha-2. NATN_NUM: ITU-T dial code without `+`.
-- Idempotent via `INSERT ... ON CONFLICT (NTNL_CD) DO NOTHING`.
-- ---------------------------------------------------------------------------

INSERT INTO NATN_INFM (NTNL_CD, NATL_NM,          NATN_NUM, TIME_ZONE)         VALUES
    ('AU',           'Australia',      '61',     'Australia/Sydney'),
    ('KH',           'Cambodia',       '855',    'Asia/Phnom_Penh'),
    ('CA',           'Canada',         '1',      'America/Toronto'),
    ('CN',           'China',          '86',     'Asia/Shanghai'),
    ('HK',           'Hong Kong',      '852',    'Asia/Hong_Kong'),
    ('JP',           'Japan',          '81',     'Asia/Tokyo'),
    ('NZ',           'New Zealand',    '64',     'Pacific/Auckland'),
    ('SG',           'Singapore',      '65',     'Asia/Singapore'),
    ('KR',           'South Korea',    '82',     'Asia/Seoul'),
    ('CH',           'Switzerland',    '41',     'Europe/Zurich'),
    ('TH',           'Thailand',       '66',     'Asia/Bangkok'),
    ('GB',           'United Kingdom', '44',     'Europe/London'),
    ('US',           'United States',  '1',      'America/New_York'),
    ('VN',           'Vietnam',        '84',     'Asia/Ho_Chi_Minh')
ON CONFLICT (NTNL_CD) DO NOTHING;

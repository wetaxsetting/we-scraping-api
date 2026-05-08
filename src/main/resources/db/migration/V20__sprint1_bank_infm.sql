-- ============================================================================
-- V20__sprint1_bank_infm.sql
--
-- Adds BANK_INFM — the Vietnamese bank registry — and seeds it with the real
-- legacy DEV data. Replaces the hardcoded BANK_CD → display name map in
-- BankCdResolver. Per the audit: all banking-related bank code/name data must
-- come from BANK_INFM, not from a static map in code.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS + INSERT … ON CONFLICT DO NOTHING.
-- Source rows pulled from wecmsvndev.bank_infm (full registry, 29 rows as of
-- 2026-04-29). BIZ_KEY is intentionally not part of this table — bank codes
-- are global, not tenant-scoped.
-- ============================================================================

CREATE TABLE IF NOT EXISTS BANK_INFM (
    BANK_CD         VARCHAR(20)  NOT NULL,
    START_DATE      VARCHAR(8)   NOT NULL,
    END_DATE        VARCHAR(8)   NOT NULL,
    RPRS_BANK_NM    VARCHAR(200) NOT NULL,
    REG_ID          VARCHAR(100) NOT NULL,
    REG_DTM         VARCHAR(14)  NOT NULL,
    MOD_ID          VARCHAR(100),
    MOD_DTM         VARCHAR(14),
    CONSTRAINT pk_bank_infm PRIMARY KEY (BANK_CD)
);

INSERT INTO BANK_INFM (BANK_CD, START_DATE, END_DATE, RPRS_BANK_NM, REG_ID, REG_DTM, MOD_ID, MOD_DTM) VALUES
    ('11704001', '20210801', '99991231', 'Vietin Bank',                                 'SYSTEM',     '20210803000000', 'soknyran', '20240805042350'),
    ('11704002', '20210801', '99991231', 'Techcom Bank',                                'SYSTEM',     '20210803000000', 'soknyran', '20241018080510'),
    ('11704003', '20210801', '99991231', 'IBK(Vietnam)',                                'SYSTEM',     '20210803000000', 'ktr2006',  '20220721083358'),
    ('11704004', '20210801', '99991231', 'Vietcom Bank',                                'SYSTEM',     '20210803000000', 'soknyran', '20240814062457'),
    ('11704007', '20210801', '99991231', 'MBBank',                                      'SYSTEM',     '20210803000000', 'soknyran', '20241018084634'),
    ('11704008', '20210801', '99991231', 'BIDV Bank',                                   'SYSTEM',     '20210803000000', 'soknyran', '20241018081906'),
    ('11704011', '20210801', '99991231', 'ACB Bank',                                    'SYSTEM',     '20210803000000', 'ktr2006',  '20230202081007'),
    ('11704012', '20210801', '99991231', 'VIB Bank',                                    'SYSTEM',     '20210803000000', 'SYSTEM',   '20210803000000'),
    ('11704013', '20210801', '99991231', 'Kookmin(Vietnam)',                            'SYSTEM',     '20210803000000', 'ktr2006',  '20220721083459'),
    ('11704014', '20210801', '99991231', 'MSB Bank',                                    'SYSTEM',     '20210803000000', 'SYSTEM',   '20210803000000'),
    ('11704020', '20210801', '99991231', 'Woori(Vietnam)',                              'SYSTEM',     '20210803000000', 'SYSTEM',   '20210803000000'),
    ('11704080', '20210801', '99991231', 'KEBHana',                                     'SYSTEM',     '20210803000000', 'SYSTEM',   '20210803000000'),
    ('11704088', '20210801', '99991231', 'Shinhan(Vietnam)',                            'SYSTEM',     '20210803000000', 'soknyran', '20240805064738'),
    ('11704100', '20220713', '99991231', 'Agribank',                                    'ktr2006',    '20220713012321', 'soknyran', '20241018090520'),
    ('11704101', '20220713', '99991231', 'HD Bank',                                     'ktr2006',    '20220713012403', 'soknyran', '20241018083752'),
    ('11704102', '20220728', '99991231', 'VP Bank',                                     'ktr2006',    '20220728080249', 'soknyran', '20240805062002'),
    ('11704103', '20220728', '99991231', 'Tienphong Bank',                              'ktr2006',    '20220728080333', 'soknyran', '20240805063623'),
    ('11704104', '20230516', '99991231', 'Standard Chartered Vietnam',                  'ktr2006',    '20230516060827', 'orltokata','20240626064947'),
    ('11704105', '20240216', '99991231', 'Saigon-Hanoi Commercial Joint Stock Bank',    'orltokata',  '20240216100207', 'orltokata','20240216102651'),
    ('11704106', '20240216', '99991231', 'An Binh Commercial Joint Stock Bank',         'rothnak',    '20240216023208', 'rothnak',  '20240216023423'),
    ('11704107', '20231019', '99991231', 'NongHyup Bank(Vietnam)',                      'rothnak',    '20231019102507', 'rothnak',  '20231108014232'),
    ('11704108', '20240409', '99991231', 'Prosperity and Growth Commercial Joint Stock Bank', 'rothnak', '20240409031549', 'orltokata','20240429041505'),
    ('11704109', '20240716', '99991231', 'Sacombank',                                   'orltokata',  '20240716065650', 'orltokata','20240716065650'),
    ('11704110', '20240716', '99991231', 'OCB Bank',                                    'orltokata',  '20240716065843', 'orltokata','20240716065843'),
    ('11704111', '20240716', '99991231', 'KienLongBank',                                'orltokata',  '20240716065923', 'orltokata','20240716065923'),
    ('11704112', '20250411', '99991231', 'Vietnam Eximbank',                            'Channach',   '20250411105758', 'Channach', '20250411111156'),
    ('11704218', '20230601', '99991231', 'Fortune Vietnam Joint Stock Commercial Bank', 'ktr2006',    '20230601060915', 'Channach', '20250407074911'),
    ('11704220', '20230601', '99991231', 'Mirae Asset Finance Company Vietnam',         'ktr2006',    '20230601060958', 'soknyran', '20240809025824'),
    ('11740099', '20210801', '99991231', 'SMBC',                                        'SYSTEM',     '20210803000000', 'SYSTEM',   '20210803000000')
ON CONFLICT (BANK_CD) DO NOTHING;

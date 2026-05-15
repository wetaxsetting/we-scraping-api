-- V149: BLOCKED_CHARS_RULE — per-bank / per-payment-type / per-field
--       blocked-character ruleset consumed by SpecialCharValidator.
--
-- Used by Excel-upload parsing in all four Payment modules:
--   • Internal Transfer (pymn_0009_002)
--   • Vendor Payment    (pymn_0001_002)
--   • Expense Payment   (pymn_0003_002)
--   • Payroll Payment   (pymn_0005_002)
-- Plus Overseas Payment (pymn_0008_*) which lands in a later sprint —
-- OVER rules are seeded preemptively so the schema is feature-complete.
--
-- Schema is column-for-column parity with DEV (verified via postgres_dev
-- MCP). The legacy DEV column name `pymn_tpye` (typo on the original
-- schema) is preserved — JPA entity maps it to the camelCase
-- `pymnType` field, so refactor code never types the typo by hand.
--
-- CREATE TABLE IF NOT EXISTS — idempotent on the shared DEV DB
-- (no-op; table already present) and creates it on a fresh local DB.
-- Seed rows use ON CONFLICT DO NOTHING for the same reason.

CREATE TABLE IF NOT EXISTS BLOCKED_CHARS_RULE (
    RULE_ID         VARCHAR(4)      NOT NULL,
    BANK_CD         VARCHAR(15)     NOT NULL,
    BANK_NM         VARCHAR(100)    NOT NULL,
    PYMN_TPYE       VARCHAR(4)      NOT NULL,
    FIELD_CD        VARCHAR(4)      NOT NULL,
    BLOCKED_CHARS   VARCHAR(100)    NOT NULL,
    EFF_FR_DT       VARCHAR(8),
    EFF_TO_DT       VARCHAR(8),
    VERSION         VARCHAR(2)      NOT NULL,
    STATUS          CHAR(1)         NOT NULL,
    REG_ID          VARCHAR(100)    NOT NULL,
    REG_DTM         VARCHAR(14)     NOT NULL,
    MOD_ID          VARCHAR(100)    NOT NULL,
    MOD_DTM         VARCHAR(14)     NOT NULL,
    CONSTRAINT PK_BLOCKED_CHARS_RULE PRIMARY KEY (RULE_ID)
);

-- Lookup-path index. The validator queries by
-- (BANK_CD, PYMN_TPYE, FIELD_CD) filtered to active rows
-- (STATUS = 'Y' AND today between EFF_FR_DT and EFF_TO_DT).
CREATE INDEX IF NOT EXISTS IDX_BLOCKED_CHARS_RULE_LOOKUP
    ON BLOCKED_CHARS_RULE (BANK_CD, PYMN_TPYE, FIELD_CD, STATUS);

-- Seed: every active + historical rule on DEV as of 2026-05-15. Captured
-- verbatim so the local validator behaves identically to the live system.
-- Use single quotes doubled for SQL-escaped quote chars inside the
-- BLOCKED_CHARS column. Each row's BLOCKED_CHARS is a raw character set
-- (NOT a regex) — validator does substring containment, not pattern match.

INSERT INTO BLOCKED_CHARS_RULE
    (RULE_ID, BANK_CD,    BANK_NM,         PYMN_TPYE, FIELD_CD, BLOCKED_CHARS,                           EFF_FR_DT,   EFF_TO_DT,   VERSION, STATUS, REG_ID,   REG_DTM,          MOD_ID,   MOD_DTM)
VALUES
    -- VCB (11704004)
    ('0024', '11704004', 'VCB',           'INTR',    'RMKS',   '`~!@#$%^_={}[]\|''";><',                '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133726', 'SYSTEM', '20260422133726'),
    ('0025', '11704004', 'VCB',           'VEND',    'RMKS',   '`~!@#$%^_={}[]\|''";><',                '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133726', 'SYSTEM', '20260422133726'),
    ('0026', '11704004', 'VCB',           'EXPN',    'RMKS',   '`~!@#$%^_={}[]\|''";><',                '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133726', 'SYSTEM', '20260422133726'),
    ('0027', '11704004', 'VCB',           'PAYR',    'RMKS',   '`~!@#$%^_={}[]\|''";><',                '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133726', 'SYSTEM', '20260422133726'),
    ('0028', '11704004', 'VCB',           'INTR',    'RCPT',   '`~!@#$%^_={}[]\|''";><',                '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133726', 'SYSTEM', '20260422133726'),
    ('0029', '11704004', 'VCB',           'VEND',    'RCPT',   '`~!@#$%^_={}[]\|''";><',                '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133726', 'SYSTEM', '20260422133726'),
    ('0030', '11704004', 'VCB',           'EXPN',    'RCPT',   '`~!@#$%^_={}[]\|''";><',                '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133726', 'SYSTEM', '20260422133726'),
    ('0031', '11704004', 'VCB',           'PAYR',    'RCPT',   '`~!@#$%^_={}[]\|''";><',                '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133726', 'SYSTEM', '20260422133726'),
    -- BIDV (11704008)
    ('0032', '11704008', 'BIDV',          'INTR',    'RMKS',   '`~!@#$%^&*()_=+{}[]\|/?''";:',          '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133731', 'SYSTEM', '20260422133731'),
    ('0033', '11704008', 'BIDV',          'VEND',    'RMKS',   '`~!@#$%^&*()_=+{}[]\|/?''";:',          '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133731', 'SYSTEM', '20260422133731'),
    ('0034', '11704008', 'BIDV',          'EXPN',    'RMKS',   '`~!@#$%^&*()_=+{}[]\|/?''";:',          '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133731', 'SYSTEM', '20260422133731'),
    ('0035', '11704008', 'BIDV',          'PAYR',    'RMKS',   '`~!@#$%^&*()_=+{}[]\|/?''";:',          '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133731', 'SYSTEM', '20260422133731'),
    ('0036', '11704008', 'BIDV',          'INTR',    'RCPT',   '`~!@#$%^*()_=+{}[]\|/?''";:,>',         '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133731', 'SYSTEM', '20260422133731'),
    ('0037', '11704008', 'BIDV',          'VEND',    'RCPT',   '`~!@#$%^*()_=+{}[]\|/?''";:,>',         '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133731', 'SYSTEM', '20260422133731'),
    ('0038', '11704008', 'BIDV',          'EXPN',    'RCPT',   '`~!@#$%^*()_=+{}[]\|/?''";:,>',         '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133731', 'SYSTEM', '20260422133731'),
    ('0039', '11704008', 'BIDV',          'PAYR',    'RCPT',   '`~!@#$%^*()_=+{}[]\|/?''";:,>',         '20260414',  NULL,        '1',     'Y',    'SYSTEM', '20260422133731', 'SYSTEM', '20260422133731'),
    -- Woori VN (11704020)
    ('0011', '11704020', 'Woori VN',      'INTR',    'RMKS',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0012', '11704020', 'Woori VN',      'VEND',    'RMKS',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0013', '11704020', 'Woori VN',      'EXPN',    'RMKS',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0014', '11704020', 'Woori VN',      'PAYR',    'RMKS',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0015', '11704020', 'Woori VN',      'OVER',    'MEMO',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0016', '11704020', 'Woori VN',      'VEND',    'RCPT',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0017', '11704020', 'Woori VN',      'EXPN',    'RCPT',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0018', '11704020', 'Woori VN',      'PAYR',    'RCPT',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0019', '11704020', 'Woori VN',      'OVER',    'BENA',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    ('0020', '11704020', 'Woori VN',      'OVER',    'BEAD',   '<>''"@$&',                              '20251216',  NULL,        '1',     'Y',    'SYSTEM', '20260112172750', 'SYSTEM', '20260112172750'),
    -- Shinhan VN (11704088)
    ('0001', '11704088', 'Shinhan VN',    'INTR',    'RMKS',   '~!@#$%^*={}[]<>',                       '20251118',  NULL,        '1',     'Y',    'SYSTEM', '20260112164930', 'SYSTEM', '20260112164930'),
    ('0002', '11704088', 'Shinhan VN',    'VEND',    'RMKS',   '~!@#$%^*={}[]<>',                       '20251118',  NULL,        '1',     'Y',    'SYSTEM', '20260112171455', 'SYSTEM', '20260112171455'),
    ('0003', '11704088', 'Shinhan VN',    'EXPN',    'RMKS',   '~!@#$%^*={}[]<>',                       '20251118',  NULL,        '1',     'Y',    'SYSTEM', '20260112171458', 'SYSTEM', '20260112171458'),
    ('0004', '11704088', 'Shinhan VN',    'PAYR',    'RMKS',   '~!@#$%^*={}[]<>',                       '20251118',  NULL,        '1',     'Y',    'SYSTEM', '20260112171500', 'SYSTEM', '20260112171500'),
    ('0005', '11704088', 'Shinhan VN',    'VEND',    'RCPT',   '~!@^*={}[]''"<>',                       '20251118',  NULL,        '1',     'Y',    'SYSTEM', '20260112172049', 'SYSTEM', '20260112172049'),
    ('0006', '11704088', 'Shinhan VN',    'EXPN',    'RCPT',   '~!@^*={}[]''"<>',                       '20251118',  NULL,        '1',     'Y',    'SYSTEM', '20260112172056', 'SYSTEM', '20260112172056'),
    ('0007', '11704088', 'Shinhan VN',    'PAYR',    'RCPT',   '~!@^*={}[]''"<>',                       '20251118',  NULL,        '1',     'Y',    'SYSTEM', '20260112172104', 'SYSTEM', '20260112172104'),
    -- Shinhan VN — Overseas (v1 retired, v2 active)
    ('0008', '11704088', 'Shinhan VN',    'OVER',    'MEMO',   '`$''"',                                 '20251218',  '20260224',  '1',     'N',    'SYSTEM', '20260112172613', 'SYSTEM', '20260225104109'),
    ('0009', '11704088', 'Shinhan VN',    'OVER',    'BENA',   '`~!@#$%^&*()_=+{}[]\|?''":><',          '20251218',  '20260224',  '1',     'N',    'SYSTEM', '20260112172655', 'SYSTEM', '20260225104109'),
    ('0010', '11704088', 'Shinhan VN',    'OVER',    'BEAD',   '`~!@#$%^&*()_=+{}[]\|?''":><',          '20251218',  '20260224',  '1',     'N',    'SYSTEM', '20260112172656', 'SYSTEM', '20260225104109'),
    ('0021', '11704088', 'Shinhan VN',    'OVER',    'MEMO',   '`@$''"<>',                              '20260225',  NULL,        '2',     'Y',    'SYSTEM', '20260225104150', 'SYSTEM', '20260225104150'),
    ('0022', '11704088', 'Shinhan VN',    'OVER',    'BENA',   '`@$''"<>',                              '20260225',  NULL,        '2',     'Y',    'SYSTEM', '20260225104150', 'SYSTEM', '20260225104150'),
    ('0023', '11704088', 'Shinhan VN',    'OVER',    'BEAD',   '`@$''"<>',                              '20260225',  NULL,        '2',     'Y',    'SYSTEM', '20260225104150', 'SYSTEM', '20260225104150')
ON CONFLICT (RULE_ID) DO NOTHING;

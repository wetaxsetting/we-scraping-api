-- =====================================================================
-- V44: BANK_INFM_DETL + nickname-formatting DB functions
-- =====================================================================
-- Goal: byte-for-byte parity with JEX legacy nickname display on
--       Banking > Account Inquiry (VND/FCY shared via AcctMstr).
--
-- Strategy: rather than translate the legacy PL/pgSQL into Java, we
-- migrate the two functions verbatim (copied from DEV pg_proc) plus
-- the BANK_INFM_DETL table they depend on. Hibernate calls them via
-- @Formula on AcctMstr.formattedAcctNickNm. See refactor strategy
-- decision: read-only legacy helpers stay as DB functions; write logic
-- and new logic stay in Java.
--
-- Source of truth: wecmsvndev.{wb_format_acct_nick_nm,wb_get_bank_nm}
-- + wecmsvndev.bank_infm_detl (rows where bank_detl_info_cd = '10' and
-- the active date range covers today). Pulled 2026-05-03.
--
-- Touched by Account Inquiry only at the @Formula call site, but the
-- functions themselves are stable building blocks usable by any future
-- screen that ports a legacy IDO referencing them.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. BANK_INFM_DETL table (legacy short bank name table)
-- ---------------------------------------------------------------------
-- Schema mirrors wecmsvndev.bank_infm_detl exactly. PK is
-- (BANK_CD, BANK_DETL_INFO_CD, START_DATE) — same row may have
-- successive validity windows so START_DATE participates in the key.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS BANK_INFM_DETL (
    BANK_CD              VARCHAR(20)   NOT NULL,
    BANK_DETL_INFO_CD    VARCHAR(3)    NOT NULL,
    START_DATE           VARCHAR(8)    NOT NULL,
    END_DATE             VARCHAR(8)    NOT NULL,
    BANK_DETL_INFO_CONT  VARCHAR(2000) NOT NULL,
    REG_ID               VARCHAR(100)  NOT NULL,
    REG_DTM              VARCHAR(14)   NOT NULL,
    MOD_ID               VARCHAR(100),
    MOD_DTM              VARCHAR(14),
    SEQ_NO               NUMERIC,
    CONSTRAINT pk_bank_infm_detl PRIMARY KEY (BANK_CD, BANK_DETL_INFO_CD, START_DATE)
);

-- ---------------------------------------------------------------------
-- 2. Seed rows for BANK_DETL_INFO_CD = '10' (short bank names)
-- ---------------------------------------------------------------------
-- 68 active rows from DEV as of 2026-05-03. Idempotent via ON CONFLICT
-- so re-applying the migration on a partially-seeded DB is safe.
-- ---------------------------------------------------------------------
INSERT INTO BANK_INFM_DETL
    (BANK_CD, BANK_DETL_INFO_CD, START_DATE, END_DATE, BANK_DETL_INFO_CONT, REG_ID, REG_DTM, MOD_ID, MOD_DTM, SEQ_NO)
VALUES
    ('11704001', '10', '20210801', '99991231', 'VIETIN',     'SYSTEM', '20210803000000', 'soknyran',  '20240805042350', 0),
    ('11704002', '10', '20210801', '99991231', 'TECHCOM',    'SYSTEM', '20210803000000', 'soknyran',  '20241018080510', 0),
    ('11704003', '10', '20210801', '99991231', 'IBK VN',     'SYSTEM', '20210803000000', 'ktr2006',   '20220721083358', 0),
    ('11704004', '10', '20210801', '99991231', 'Vietcom',    'SYSTEM', '20210803000000', 'soknyran',  '20240814062457', 0),
    ('11704007', '10', '20210801', '99991231', 'MB',         'SYSTEM', '20210803000000', 'soknyran',  '20241018084634', 0),
    ('11704008', '10', '20210801', '99991231', 'BIDV',       'SYSTEM', '20210803000000', 'soknyran',  '20241018081906', 0),
    ('11704011', '10', '20210801', '99991231', 'ACB',        'SYSTEM', '20210803000000', 'ktr2006',   '20230130013425', 0),
    ('11704012', '10', '20210801', '99991231', 'VIB',        'SYSTEM', '20210803000000', 'SYSTEM',    '20210803000000', 0),
    ('11704013', '10', '20210801', '99991231', 'KB VN',      'SYSTEM', '20210803000000', 'ktr2006',   '20220721083459', 0),
    ('11704014', '10', '20210801', '99991231', 'MSB',        'SYSTEM', '20210803000000', 'SYSTEM',    '20210803000000', 0),
    ('11704020', '10', '20210801', '99991231', 'Woori VN',   'SYSTEM', '20210803000000', 'SYSTEM',    '20210803000000', 0),
    ('11704080', '10', '20210801', '99991231', 'KEBHana',    'SYSTEM', '20210803000000', 'SYSTEM',    '20210803000000', 0),
    ('11704088', '10', '20210801', '99991231', 'Shinhan VN', 'SYSTEM', '20210803000000', 'soknyran',  '20240805064738', 0),
    ('11704100', '10', '20220713', '99991231', 'Agri',       'ktr2006',  '20220713012321', 'soknyran', '20241018090520', 0),
    ('11704101', '10', '20220713', '99991231', 'HD',         'ktr2006',  '20220713012403', 'soknyran', '20241018083752', 0),
    ('11704102', '10', '20220728', '99991231', 'VP',         'ktr2006',  '20220728080249', 'soknyran', '20240805062002', 0),
    ('11704103', '10', '20220728', '99991231', 'TP',         'ktr2006',  '20220728080333', 'soknyran', '20240805063623', 0),
    ('11704104', '10', '20230516', '99991231', 'SC VN',      'ktr2006',  '20230516060622', 'orltokata', '20240626064947', 0),
    ('11704105', '10', '20240216', '99991231', 'SHB',        'orltokata', '20240216100207', 'orltokata', '20240216102651', 0),
    ('11704106', '10', '20240216', '99991231', 'ABBANK',     'rothnak',   '20240216023208', 'rothnak',   '20240216023423', 0),
    ('11704107', '10', '20231019', '99991231', 'NH VN',      'rothnak',   '20231019102507', 'rothnak',   '20231108014232', 0),
    ('11704108', '10', '20240409', '99991231', 'PG Bank',    'rothnak',   '20240409031549', 'orltokata', '20240429041505', 0),
    ('11704109', '10', '20240716', '99991231', 'Sacombank',  'orltokata', '20240716065650', 'orltokata', '20240716065650', 0),
    ('11704110', '10', '20240716', '99991231', 'OCB',        'orltokata', '20240716065843', 'orltokata', '20240716065843', 0),
    ('11704111', '10', '20240716', '99991231', 'KLB',        'orltokata', '20240716065923', 'orltokata', '20240716065923', 0),
    ('11704112', '10', '20250411', '99991231', 'Eximbank ',  'Channach',  '20250411105758', 'Channach',  '20250411111156', 0),
    ('11704202', '10', '20230601', '99991231', 'BacABank',           'ktr2006', '20230601043103', 'ktr2006', '20230601043103', 0),
    ('11704203', '10', '20230601', '99991231', 'BaoVietBank',        'ktr2006', '20230601043124', 'ktr2006', '20230601043124', 0),
    ('11704204', '10', '20230601', '99991231', 'CAKE by VPBank',     'ktr2006', '20230601043321', 'ktr2006', '20230601043321', 0),
    ('11704205', '10', '20230601', '99991231', 'CIMB VN',            'ktr2006', '20230601043417', 'ktr2006', '20230601043417', 0),
    ('11704206', '10', '20230601', '99991231', 'CoopBank',           'ktr2006', '20230601060145', 'ktr2006', '20230601060145', 0),
    ('11704207', '10', '20230601', '99991231', 'DBS Bank',           'ktr2006', '20230601060230', 'ktr2006', '20230601060230', 0),
    ('11704208', '10', '20230601', '99991231', 'Dong A Bank',        'ktr2006', '20230601060248', 'ktr2006', '20230601060248', 0),
    ('11704209', '10', '20230601', '99991231', 'Eximbank',           'ktr2006', '20230601060336', 'ktr2006', '20230601060336', 0),
    ('11704210', '10', '20230601', '99991231', 'FCCOM',              'ktr2006', '20230601060353', 'ktr2006', '20230601060353', 0),
    ('11704211', '10', '20230601', '99991231', 'GPBank',             'ktr2006', '20230601060411', 'ktr2006', '20230601060411', 0),
    ('11704212', '10', '20230601', '99991231', 'Hong Leong Bank',    'ktr2006', '20230601060513', 'ktr2006', '20230601060513', 0),
    ('11704213', '10', '20230601', '99991231', 'HSBC VN',            'ktr2006', '20230601060529', 'ktr2006', '20230601060529', 0),
    ('11704214', '10', '20230601', '99991231', 'IVB',                'ktr2006', '20230601060711', 'ktr2006', '20230601060711', 0),
    ('11704215', '10', '20230601', '99991231', 'Kasikornbank HCM',   'ktr2006', '20230601060732', 'ktr2006', '20230601060732', 0),
    ('11704216', '10', '20230601', '99991231', 'UMEE by Kienlongbank', 'ktr2006', '20230601060819', 'ktr2006', '20230601060819', 0),
    ('11704217', '10', '20230601', '99991231', 'Kienlongbank',       'ktr2006', '20230601060836', 'ktr2006', '20230601060836', 0),
    ('11704218', '10', '20230601', '99991231', 'LPBank ',            'ktr2006', '20230601060915', 'Channach', '20250407074911', 0),
    ('11704219', '10', '20230601', '99991231', 'Liobank',            'ktr2006', '20230601060935', 'ktr2006', '20230601060935', 0),
    ('11704220', '10', '20230601', '99991231', 'MIRAE ASSET FINANCE COMPANY VIT NAM LIMITED', 'ktr2006', '20230601060958', 'soknyran', '20240809025825', 0),
    ('11704221', '10', '20230601', '99991231', 'Nam A Bank',         'ktr2006', '20230601061205', 'ktr2006', '20230601061205', 0),
    ('11704222', '10', '20230601', '99991231', 'NCB',                'ktr2006', '20230601061220', 'ktr2006', '20230601061220', 0),
    ('11704223', '10', '20230601', '99991231', 'NongHyup CN Ha Noi', 'ktr2006', '20230601061551', 'ktr2006', '20230601061551', 0),
    ('11704224', '10', '20230601', '99991231', 'OCB',                'ktr2006', '20230601061605', 'ktr2006', '20230601061605', 0),
    ('11704225', '10', '20230601', '99991231', 'Oceanbank',          'ktr2006', '20230601061621', 'ktr2006', '20230601061621', 0),
    ('11704226', '10', '20230601', '99991231', 'PG Bank',            'ktr2006', '20230601061637', 'ktr2006', '20230601061637', 0),
    ('11704227', '10', '20230601', '99991231', 'Public bank Vietnam Limited', 'ktr2006', '20230601061655', 'ktr2006', '20230601061655', 0),
    ('11704228', '10', '20230601', '99991231', 'PVcomBank',          'ktr2006', '20230601061720', 'ktr2006', '20230601061720', 0),
    ('11704229', '10', '20230601', '99991231', 'Sacombank',          'ktr2006', '20230601061908', 'ktr2006', '20230601061908', 0),
    ('11704230', '10', '20230601', '99991231', 'Saigonbank',         'ktr2006', '20230601061928', 'ktr2006', '20230601061928', 0),
    ('11704231', '10', '20230601', '99991231', 'SCB',                'ktr2006', '20230601061958', 'ktr2006', '20230601061958', 0),
    ('11704232', '10', '20230601', '99991231', 'SeABank',            'ktr2006', '20230601062012', 'ktr2006', '20230601062012', 0),
    ('11704234', '10', '20230601', '99991231', 'TIMO by Viet Capital Bank', 'ktr2006', '20230601062705', 'ktr2006', '20230601062705', 0),
    ('11704235', '10', '20230601', '99991231', 'UOB Viet Nam',       'ktr2006', '20230601062742', 'ktr2006', '20230601062742', 0),
    ('11704236', '10', '20230601', '99991231', 'Viet A Bank',        'ktr2006', '20230601062802', 'ktr2006', '20230601062802', 0),
    ('11704237', '10', '20230601', '99991231', 'Viet Capital Bank',  'ktr2006', '20230601062821', 'ktr2006', '20230601062821', 0),
    ('11704238', '10', '20230601', '99991231', 'VietBank',           'ktr2006', '20230601062848', 'ktr2006', '20230601062848', 0),
    ('11704239', '10', '20230601', '99991231', 'VBSP',               'ktr2006', '20230601062951', 'ktr2006', '20230601062951', 0),
    ('11704240', '10', '20230601', '99991231', 'CBBank',             'ktr2006', '20230601063021', 'ktr2006', '20230601063021', 0),
    ('11704241', '10', '20230601', '99991231', 'Viettel Money by Viettel Digital Services', 'ktr2006', '20230601063054', 'ktr2006', '20230601063054', 0),
    ('11704242', '10', '20230601', '99991231', 'VNPT Money',         'ktr2006', '20230601063108', 'ktr2006', '20230601063108', 0),
    ('11704243', '10', '20230601', '99991231', 'VRB',                'ktr2006', '20230601063153', 'ktr2006', '20230601063153', 0),
    ('11740099', '10', '20210801', '99991231', 'SMBC',               'SYSTEM',  '20210803000000', 'SYSTEM',  '20210803000000', 0)
ON CONFLICT (BANK_CD, BANK_DETL_INFO_CD, START_DATE) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Function: wb_get_bank_nm(varchar, varchar) — short / long lookup
-- ---------------------------------------------------------------------
-- Copied verbatim from wecmsvndev pg_proc. _type_nm = 's' returns the
-- short bank name from BANK_INFM_DETL where BANK_DETL_INFO_CD = '10'
-- and today is in the [START_DATE, END_DATE] window. _type_nm = 'l'
-- returns the full RPRS_BANK_NM from BANK_INFM. Anything else → NULL.
--
-- We only install the two-arg overload; the single-arg overload is
-- not on the Account Inquiry critical path. Add it later if a future
-- IDO needs it.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION wb_get_bank_nm(_bank_cd character varying, _type_nm character varying)
    RETURNS character varying
    LANGUAGE plpgsql
AS $function$
DECLARE
    bankNm varchar;
    --type name S -Short name -L Full name
BEGIN
    CASE
        WHEN lower($1) <> '' AND lower($2) = 'l' THEN
            SELECT rprs_bank_nm INTO bankNm
              FROM bank_infm
             WHERE bank_cd = _bank_cd;
        WHEN lower($1) <> '' AND lower($2) = 's' THEN
            SELECT bank_detl_info_cont INTO bankNm
              FROM bank_infm_detl
             WHERE bank_detl_info_cd = '10'
               AND to_char(now(), 'YYYYMMDD') BETWEEN start_date AND end_date
               AND bank_cd = _bank_cd;
        ELSE
            bankNm := NULL;
    END CASE;
    RETURN bankNm;
END;
$function$;

-- ---------------------------------------------------------------------
-- 4. Function: wb_format_acct_nick_nm(text, text, text, text)
-- ---------------------------------------------------------------------
-- Copied verbatim from wecmsvndev pg_proc. Rule:
--   * Empty / null nickname → "<short bank name> <last 4 of acct no>"
--   * HAND_REG_YN='Y' AND nick matches "Letters4DigitsExtra" → split letters/digits
--   * "PG BankXXXX..." → split "PG Bank" and the 4-digit run
--   * Letters4DigitsExtra (without the Y gate) → split letters/digits
--   * Anywhere "VNxxxx..." → split "VN" and the 4-digit run
--   * Otherwise → return as-is
--
-- The HAND_REG_YN branch is structurally redundant with the no-gate
-- branch below it (CASE matches the first WHEN, but the regex+output
-- are identical) — preserved for byte-for-byte parity with DEV.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION wb_format_acct_nick_nm(
    acct_nick_nm text, bank_cd text, acct_no text, hand_reg_yn text)
    RETURNS text
    LANGUAGE plpgsql
AS $function$
BEGIN
    RETURN COALESCE(
        CASE
            -- Condition 1: blank nickname → bank short name + last 4
            WHEN acct_nick_nm IS NULL OR acct_nick_nm = ''
            THEN CONCAT(wb_get_bank_nm(bank_cd, 's'), ' ', RIGHT(acct_no, 4))
            -- Condition 2: hand-registered + Letters4Digits pattern
            WHEN hand_reg_yn = 'Y' AND acct_nick_nm ~ '^[A-Za-z]+[0-9]{4}.*$'
            THEN REGEXP_REPLACE(acct_nick_nm, '([A-Za-z]+)([0-9]{4})', '\1 \2')
            -- Condition 3: special "PG Bank" join
            WHEN acct_nick_nm ~ '^PG Bank[0-9]{4}.*$'
            THEN REGEXP_REPLACE(acct_nick_nm, '(PG Bank)([0-9]{4})', '\1 \2')
            -- Condition 4: same regex as Condition 2 but ungated
            WHEN acct_nick_nm ~ '^[A-Za-z]+[0-9]{4}.*$'
            THEN REGEXP_REPLACE(acct_nick_nm, '([A-Za-z]+)([0-9]{4})', '\1 \2')
            -- Condition 5: split "VN" from a glued 4-digit run
            WHEN acct_nick_nm ~ 'VN[0-9]{4}.*$'
            THEN REGEXP_REPLACE(acct_nick_nm, '(VN)([0-9]{4})', '\1 \2')
            ELSE acct_nick_nm
        END
    );
END;
$function$;

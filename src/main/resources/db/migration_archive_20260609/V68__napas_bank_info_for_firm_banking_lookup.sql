-- ============================================================================
-- V68__napas_bank_info_for_firm_banking_lookup.sql
--
-- Adds the NAPAS_BANK_INFO dictionary so the firm-banking-settings
-- Create dialog's Withdrawal Account autocomplete can resolve the
-- selected NAPAS bank code (e.g. 970424 Shinhan) to the internal
-- ACCT_MSTR.BANK_CD value (e.g. 11704088) — exactly the bridge JEX
-- comm_1004_001_act.jsp does at runtime:
--
--   NAPAS_BANK_INFO PK = napas_bank_cd  →  bank_cd (internal)
--                                           ↓
--   ACCT_MSTR rows are filtered by (biz_key, bank_cd)
--
-- Schema is column-for-column with DEV (`information_schema.columns`
-- on wecmsvndev.napas_bank_info, queried 2026-05-09 via the postgres
-- MCP):
--
--   napas_bank_cd  VARCHAR(10) NOT NULL  PK
--   short_name     VARCHAR(100) NOT NULL
--   long_name_en   VARCHAR(500)
--   long_name_vn   VARCHAR(500)
--   bank_cd        VARCHAR(20)
--
-- Seed: only the 3 banks supported under OQ-8a Option (c) — Shinhan
-- (970424), Vietcombank (970436), Woori (970457). Internal `bank_cd`
-- values copied verbatim from DEV's napas_bank_info on 2026-05-09:
--
--   970424 SHINHAN BANK   → 11704088
--   970436 Vietcombank    → 11704004
--   970457 Wooribank VN   → 11704020
--
-- Seed-coverage rule: when OQ-8a's bounded-bank set is extended
-- (TODO(future-bank-onboarding) in FirmBankingSettingsService /
-- NewFirmBankingDialog), add the matching NAPAS_BANK_INFO row in
-- the same migration so the lookup resolves the new code.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS + INSERT … ON CONFLICT
-- DO NOTHING.
-- ============================================================================

CREATE TABLE IF NOT EXISTS NAPAS_BANK_INFO (
    NAPAS_BANK_CD  VARCHAR(10)  NOT NULL,
    SHORT_NAME     VARCHAR(100) NOT NULL,
    LONG_NAME_EN   VARCHAR(500),
    LONG_NAME_VN   VARCHAR(500),
    BANK_CD        VARCHAR(20),
    CONSTRAINT pk_napas_bank_info PRIMARY KEY (NAPAS_BANK_CD)
);

INSERT INTO NAPAS_BANK_INFO
    (NAPAS_BANK_CD, SHORT_NAME, LONG_NAME_EN, LONG_NAME_VN, BANK_CD)
VALUES
    ('970424', 'SHINHAN BANK', 'Shinhan Bank Vietnam Limited',
     N'Ngân hàng TNHH MTV Shinhan Việt Nam',                          '11704088'),
    ('970436', 'Vietcombank',  'Joint Stock Commercial Bank for Foreign Trade of Vietnam',
     N'Ngân hàng TMCP Ngoại Thương Việt Nam',                         '11704004'),
    ('970457', 'Wooribank VN', 'Woori Bank Vietnam Limited',
     N'Ngân hàng TNHH MTV Woori Việt Nam',                            '11704020')
ON CONFLICT (NAPAS_BANK_CD) DO NOTHING;

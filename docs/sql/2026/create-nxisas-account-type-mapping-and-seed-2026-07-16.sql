-- WeCMS Manual Database Change
-- Date: 2026-07-16
-- Source: migrated from Claude Code (AI)/sql/nxisas_acct_type_mapping_20260716.sql
-- Related record: docs/audits/legacy-parity/select-bank-accounts-account-type-autofill-parity-audit.md
-- Type: DDL+DML
-- Execution: MANUAL
-- Target environments: Refactor R&D DB (172.168.5.172:5432/wecmsdevdb) — the original script states NOT JEX PROD
-- Executor: DBA TEAM
-- Rollback: Reversible by dropping the table; the refactor degrades gracefully when it is absent
-- Verification: pre/post-check queries are present in the original script below
-- MIGRATION REVIEW REQUIRED — this script creates a new table and/or DB function that has no counterpart in src/main/resources/db/migration/. No migration was created and no V-number was assigned during the archive migration; the placement decision is open.
--
-- Migrated 2026-08-17 into the governed wecms-api SQL location. The original file
-- remains unchanged in the workspace archive; nothing below this line was edited.
-- =============================================================================

-- =============================================================================
-- nxisas_acct_type_mapping_20260716.sql
-- =============================================================================
-- Purpose : Seed the NXISAS_ACCT_TYPE_MAPPING reference table so the "Select
--           bank accounts" popup auto-fills Account Type from the bank's raw
--           scraped account-type string (Tier-1 of the legacy two-tier
--           auto-fill). Mirrors live JEX fnsh_0101_001_r001_act.jsp:164-176.
--
-- Target  : REFACTOR R&D DB (172.168.5.172:5432/wecmsdevdb) — NOT JEX PROD.
--           Rows below are copied VERBATIM from the live JEX DEV table
--           (10.254.242.254:54311/wecmsvndevdb), 18 rows as of 2026-07-16.
--
-- Execution: DBA-run BY HAND. This is NOT a Flyway migration — Flyway is
--            disabled in wecms-api (ddl-auto: validate). Do not place under
--            src/main/resources/db/migration/.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS + anti-join INSERT (PK-agnostic).
--             Safe to re-run; existing rows are left untouched.
--
-- ⚠️ PO SIGN-OFF REQUIRED (Mr. Jung): NXISAS_ACCT_TYPE_MAPPING is currently
--    OFF-INVENTORY — absent from docs/references/database/legacy-table-inventory.md
--    and the PROD Active Table List. Per the root CLAUDE.md legacy-table rule,
--    a table gets no migration/seed until the baseline is explicitly changed
--    with PO approval. Obtain that approval + add the table to the inventory
--    BEFORE running this script in any shared env.
--
-- Consumers (already implemented, graceful when the table is absent):
--   - NxisasAcctTypeMappingRepository.loadBankMappings (to_regclass-guarded)
--   - BankScrapingService.runAcctList -> ScrapedAccount.acctTypeCd
--   - wire: acct_type_cd -> FE mapScrapedToBankRow (Tier-1) with
--           defaultCurrentAcctTypeCd (Tier-2) fallback.
--
-- Run order: no dependencies. Run standalone. No legacy rows are modified.
-- =============================================================================

-- 1. Structure — column-for-column with JEX DEV (verified 2026-07-16).
--    PK (bank_cd, ccy_cd, nxisas_acct_type). No audit columns on legacy.
CREATE TABLE IF NOT EXISTS nxisas_acct_type_mapping (
    bank_cd          VARCHAR(20) NOT NULL,
    ccy_cd           CHAR(3)     NOT NULL,
    nxisas_acct_type VARCHAR(50) NOT NULL,
    acct_type_cd     CHAR(2),
    CONSTRAINT nxisas_acct_type_mapping_pk
        PRIMARY KEY (bank_cd, ccy_cd, nxisas_acct_type)
);

-- 2. Seed — 18 rows from live JEX DEV. Codes are 02 (Fixed) / 03 (Current VND) /
--    04 (Current USD) only; Savings (05/06) + KRW (07/08) have no rows on DEV.
--    Anti-join keeps it idempotent without depending on the PK/ON CONFLICT name.
INSERT INTO nxisas_acct_type_mapping (bank_cd, ccy_cd, nxisas_acct_type, acct_type_cd)
SELECT v.bank_cd, v.ccy_cd, v.nxisas_acct_type, v.acct_type_cd
FROM (VALUES
    ('11704001', 'VND', 'Tài khoản tiền gửi',            '02'),  -- Vietinbank — term deposit
    ('11704002', 'VND', 'Tài khoản thanh toán',          '03'),  -- current
    ('11704004', 'VND', 'Tài khoản thanh toán',          '03'),  -- Vietcombank — current (VCB USD has NO row -> Tier-2 Current)
    ('11704007', 'VND', 'Tài khoản tiền gửi online',     '02'),  -- MB — term deposit
    ('11704007', 'VND', 'Tài khoản thanh toán',          '03'),  -- MB — current
    ('11704011', 'VND', 'TGTT KHTN SO CHON (TC) VND',    '03'),  -- current
    ('11704013', 'VND', 'TIME DEPOSIT',                  '02'),  -- term deposit
    ('11704020', 'VND', 'Tiền gửi có kỳ hạn',            '02'),  -- Woori — term deposit
    ('11704080', 'VND', 'Checking Account',              '03'),  -- current
    ('11704088', 'USD', 'DEMAND DEPOSIT-USD',            '04'),  -- Shinhan — current USD
    ('11704088', 'VND', 'Tiền gửi có kỳ hạn (TT) - VND', '02'),  -- Shinhan — term deposit
    ('11704088', 'VND', 'TIME DEPOSIT-VND',              '02'),  -- Shinhan — term deposit
    ('11704088', 'VND', 'DEMAND DEPOSIT-VND',            '03'),  -- Shinhan — current VND
    ('11704101', 'VND', 'TK TIỀN GỬI',                   '02'),  -- term deposit
    ('11704101', 'VND', 'TK TIEN GUI',                   '02'),  -- term deposit (no-diacritic variant)
    ('11704101', 'VND', 'TK THANH TOAN',                 '03'),  -- current
    ('11704103', 'VND', 'Tài khoản thanh toán',          '03'),  -- current
    ('11704108', 'VND', 'Tài khoản thanh toán',          '03')   -- current
) AS v(bank_cd, ccy_cd, nxisas_acct_type, acct_type_cd)
WHERE NOT EXISTS (
    SELECT 1 FROM nxisas_acct_type_mapping m
    WHERE m.bank_cd = v.bank_cd
      AND m.ccy_cd = v.ccy_cd
      AND m.nxisas_acct_type = v.nxisas_acct_type
);

-- 3. Verify (expect 18 rows for the banks above; codes in {02,03,04}).
-- SELECT bank_cd, ccy_cd, nxisas_acct_type, acct_type_cd
-- FROM nxisas_acct_type_mapping ORDER BY bank_cd, ccy_cd, acct_type_cd;

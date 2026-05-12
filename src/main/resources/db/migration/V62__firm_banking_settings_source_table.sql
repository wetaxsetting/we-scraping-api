-- ============================================================================
-- V62__firm_banking_settings_source_table.sql
--
-- Settings > Firm Banking Settings — adds the owning table TB_FIRM_CONF.
--
-- Mirrors DEV (wecmsvndev) column-for-column:
--   - 14 columns
--   - PK (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)
--   - DEFAULTs preserved verbatim (ORG_STAT='0', BAT_TRAN_BRANCH_DIV='Y',
--     BAT_TRAN_BRANCH_LIMIT=2000)
--
-- Refactor-only deviation (intentional):
--   - DEV has CHAR(3) for FILE_TYPE and CHAR(1) for BAT_TRAN_BRANCH_DIV.
--     Local mirrors DEV exactly; the entity carries
--     @JdbcTypeCode(SqlTypes.CHAR) on the matching String fields so
--     Hibernate `validate` accepts the bpchar columns. This follows the
--     V38 "direction locked" rule (entity-side annotation > CHAR→VARCHAR
--     migration) per wecms-api-main/CLAUDE.md.
--
-- Encryption-at-rest: NONE — wdrl_act_no stays plain text per OQ-4
-- (resolved 2026-05-09). DEV verification confirmed plain numeric on
-- both tb_firm_conf.wdrl_act_no and acct_mstr.acct_no (the LEFT JOIN
-- target). API-layer masking in responses is the v1 protection.
--
-- Ownership: this migration is owned by the firm-banking-settings
-- feature. The TB_FIRM_BANK_CODE bank-code dictionary used by the list
-- view is already provisioned by V59 (firm-banking-history).
--
-- Idempotent: CREATE TABLE IF NOT EXISTS.
--
-- Reference: docs/refactor-wecms/database/firm-banking-settings-schema.md §2
-- ============================================================================

CREATE TABLE IF NOT EXISTS TB_FIRM_CONF (
    BIZ_KEY                 VARCHAR(20)   NOT NULL,
    OUT_BANK_CD             VARCHAR(15)   NOT NULL,
    BANK_PRTN_ID            VARCHAR(20)   NOT NULL,
    VAN_ORG_CD              VARCHAR(20)   NOT NULL,
    ORG_STAT                VARCHAR(1)    NOT NULL DEFAULT '0',
    WDRL_ACT_NO             VARCHAR(100)  NOT NULL,
    FILE_TYPE               CHAR(3)       NOT NULL,
    BAT_TRAN_BRANCH_DIV     CHAR(1)       NOT NULL DEFAULT 'Y',
    BAT_TRAN_BRANCH_LIMIT   NUMERIC(7,0)  NOT NULL DEFAULT 2000,
    REG_DTTM                VARCHAR(14)   NOT NULL,
    START_DTTM              VARCHAR(14),
    END_DTTM                VARCHAR(14),
    MOD_DTTM                VARCHAR(14),
    LAST_EDTR               VARCHAR(50),
    CONSTRAINT pk_tb_firm_conf PRIMARY KEY (BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)
);

COMMENT ON TABLE TB_FIRM_CONF IS
    'Settings > Firm Banking Settings — outbound firm-banking channel bindings (Shinhan/Vietcombank/Woori). Mirrors legacy JEX wecmsvndev.tb_firm_conf column-for-column. Owned by the firm-banking-settings refactor feature. Encryption: none (OQ-4 plain-text decision); API-layer masking applies to wdrl_act_no in list/detail responses.';

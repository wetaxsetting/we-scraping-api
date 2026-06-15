-- ============================================================================
-- V248__van_api_info_table.sql
--
-- Bulk-transfer (firm-banking dispatch) foundation — creates TB_VAN_API_INFO,
-- the only batch table that was missing on the refactor DB (schema-parity
-- audit 2026-06-08, §6 "Missing Tables"; B3 infra item).
--
-- Column-for-column parity with the legacy JEX Frame DEV table
-- wecmsvndev.tb_van_api_info (verified read-only 2026-06-08):
--   van_org_cd  VARCHAR(20)  NOT NULL  PRIMARY KEY (pk_tb_van_api_info)
--   api_key     VARCHAR(100) NOT NULL
-- Legacy has no other columns, no UNIQUE/FK/CHECK, no extra index.
--
-- SECURITY: API_KEY is the per-VAN AES/HMAC shared secret. This migration
-- creates the SCHEMA ONLY and seeds NO value (workspace rule: never commit a
-- DB credential/secret). The real key is provisioned out-of-band (B2).
--
-- Consumed (once added) by: domain/payment/dispatch — TbVanApiInfo entity +
-- TbVanApiInfoRepository.findByVanOrgCd, fronting VanApiKeyService. Until this
-- DDL is physically applied, the TbVanApiInfo entity must NOT be added: with
-- Flyway disabled + ddl-auto=validate, mapping a missing table breaks boot.
--
-- NOTE ON SEQUENCES (deliberately NOT created here): FIRM_SEQ_NO,
-- FIRM_FILE_SEQ_NO, FIRM_SEQ_NO_R001, FIRM_FILE_SEQ_NO_R001 are intentionally
-- omitted — the refactor allocates dispatch keys via DispatchKeyGenerator
-- (Java MAX+1), not NEXTVAL (see analysis/sequence-migration-decision.md;
-- the *_R001 names are legacy IDO operation ids, not DB sequences). V26 already
-- dropped firm_seq_no / firm_file_seq_no; do not revive them.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS. Additive only — no DROP, no ALTER.
-- ============================================================================

CREATE TABLE IF NOT EXISTS TB_VAN_API_INFO (
    VAN_ORG_CD  VARCHAR(20)  NOT NULL,
    API_KEY     VARCHAR(100) NOT NULL,
    CONSTRAINT pk_tb_van_api_info PRIMARY KEY (VAN_ORG_CD)
);

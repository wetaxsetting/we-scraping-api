-- ============================================================================
-- V98__trco_info_file_schema.sql
--
-- Settings > Contacts > Add / Edit attachments — `TRCO_INFO_FILE` schema.
--
-- PROD active-list scope expanded 2026-05-13 to include `TRCO_INFO_FILE`
-- (see `wecms-api/CLAUDE.md` § "PROD DB Table Scope for
-- Refactoring" → "Scope override 2026-05-13"). Mirrors legacy
-- `comm_0184_002_c001_act.jsp:208-223` + `TrcoInfoFileDAO.create(...)`.
--
-- Column-for-column parity with JEX DEV (`wecmsvndev.trco_info_file`,
-- verified via `postgres_dev` MCP on 2026-05-13). 11 columns total:
--
--   trco_key       VARCHAR(20)  NOT NULL  — composite PK; FK → trco_infm
--   file_id        VARCHAR(100) NOT NULL  — per-TRCO_KEY MAX+1 counter
--   org_file_nm    VARCHAR(500) NOT NULL  — original filename from upload
--   save_file_nm   VARCHAR(500) NOT NULL  — server-renamed file (UUID + ext)
--   reg_id         VARCHAR(100) NOT NULL  — uploader USER_ID
--   reg_dtm        VARCHAR(14)  NOT NULL  — yyyyMMddHHmmss UTC
--   reg_nm         VARCHAR(100) NOT NULL  — uploader display name (materialised)
--   file_size      VARCHAR(100)           — byte count stored as STRING (legacy quirk)
--   trco_infm_titl VARCHAR(50)  NOT NULL  — default '기타문서' (DEV); service
--                                            also applies legacy FILE_ID-based
--                                            defaults on insert
--   mod_id         VARCHAR(100) NOT NULL
--   mod_dtm        VARCHAR(14)  NOT NULL
--
-- PK (trco_key, file_id) + FK trco_key → trco_infm(trco_key) — verbatim
-- from DEV. Extra helper index `ix_trco_info_file_trco_file` materialises
-- the per-contact listing read.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS.
-- ============================================================================

CREATE TABLE IF NOT EXISTS TRCO_INFO_FILE (
    TRCO_KEY        VARCHAR(20)   NOT NULL,
    FILE_ID         VARCHAR(100)  NOT NULL,
    ORG_FILE_NM     VARCHAR(500)  NOT NULL,
    SAVE_FILE_NM    VARCHAR(500)  NOT NULL,
    REG_ID          VARCHAR(100)  NOT NULL,
    REG_DTM         VARCHAR(14)   NOT NULL,
    REG_NM          VARCHAR(100)  NOT NULL,
    FILE_SIZE       VARCHAR(100),
    TRCO_INFM_TITL  VARCHAR(50)   NOT NULL DEFAULT '기타문서',
    MOD_ID          VARCHAR(100)  NOT NULL,
    MOD_DTM         VARCHAR(14)   NOT NULL,
    CONSTRAINT trco_info_file_pkey PRIMARY KEY (TRCO_KEY, FILE_ID),
    CONSTRAINT trco_info_file_fk01 FOREIGN KEY (TRCO_KEY)
        REFERENCES TRCO_INFM(TRCO_KEY)
);

CREATE INDEX IF NOT EXISTS ix_trco_info_file_trco_file
    ON TRCO_INFO_FILE (TRCO_KEY, FILE_ID);

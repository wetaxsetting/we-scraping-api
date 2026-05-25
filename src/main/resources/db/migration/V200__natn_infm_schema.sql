-- ---------------------------------------------------------------------------
-- V200 — NATN_INFM (country master) schema
--
-- Backs the global phone-country picker consumed by Settings > Users
-- Add/Edit form (Figma 142:17631 / 5300:31189). Mirrors the legacy JEX
-- table queried by `comm_0110_001_r001_act.jsp` via the IDO
-- `NATN_INFM_R002` (see WECMS_ETC/xml/service/IDO/IDO.NATN_INFM_R002.xml).
--
-- Schema follows the Legacy Schema Parity Rule — columns, lengths, and PK
-- match the legacy `wabooks.entity.NatnInfm` (NATL_NM 100, NATN_NUM 5,
-- NOTE 255). Refactor-only deviation: audit columns use TIMESTAMPTZ
-- instead of legacy VARCHAR(14), consistent with the established pattern
-- on every other refactor-side table.
--
-- Idempotent: `CREATE TABLE IF NOT EXISTS`.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS NATN_INFM (
    NTNL_CD   VARCHAR(5)   NOT NULL,
    NATL_NM   VARCHAR(100),
    NATN_NUM  VARCHAR(5),
    TIME_ZONE VARCHAR(20),
    NOTE      VARCHAR(255),
    REG_ID    VARCHAR(100),
    REG_DTM   TIMESTAMPTZ,
    MOD_ID    VARCHAR(100),
    MOD_DTM   TIMESTAMPTZ,
    CONSTRAINT natn_infm_pk PRIMARY KEY (NTNL_CD)
);

-- Helper index for ordering by NATL_NM (the picker shows an
-- alphabetical list). Adding it once at table-create time so it survives
-- across seed reloads.
CREATE INDEX IF NOT EXISTS ix_natn_infm_natl_nm ON NATN_INFM (NATL_NM);

-- ============================================================================
-- V101__nxsias_module_tran_log_his.sql
--
-- Per-call audit log for the NXISAS gateway client. One row per outbound
-- HTTPS POST to https://sgw.webcashvietnam.com/.../smart_gateway.jsp.
--
-- Mirrors JEX legacy `NXSIAS_MODULE_TRAN_LOG_HIS` column-for-column
-- (entity reference:
-- `WECMS DEV/WECMS/src/wabooks/entity/NxsiasModuleTranLogHis.java` +
-- `NxsiasModuleTranLogHisPk.java`).
--
-- PK: (UUID, REG_DTM) — composite, identical to legacy.
--   * UUID is the per-call correlation id (32-char hex / Coocon-style trSeq).
--   * REG_DTM is yyyyMMddHHmmss as VARCHAR(14), matching the legacy
--     `DateTime14Serializer`.
--
-- REQ_DATA holds the serialized JSON request body — bounded by the
-- gateway's max payload, but TEXT (unlimited) matches the legacy column
-- type and avoids a future widening.
--
-- BIZ_KEY = VARCHAR(20) per workspace convention.
--
-- NXISAS_TYPE is a legacy free-text discriminator (login / acct_list /
-- otp / captcha / qna). Kept VARCHAR(20) — long enough for the longest
-- legacy value ("transaction_fixed_deposit" is 25 chars; widen to (40)
-- here so the longest current job name fits).
-- ============================================================================

CREATE TABLE IF NOT EXISTS NXSIAS_MODULE_TRAN_LOG_HIS (
    UUID            VARCHAR(32)  NOT NULL,
    REG_DTM         VARCHAR(14)  NOT NULL,
    BIZ_KEY         VARCHAR(20),
    NXISAS_CLASS    VARCHAR(100) NOT NULL,
    NXISAS_MODULE   VARCHAR(100) NOT NULL,
    NXISAS_JOB      VARCHAR(40)  NOT NULL,
    NXISAS_ERR_CODE VARCHAR(40)  NOT NULL,
    NXISAS_ERR_CONT TEXT,
    REQ_DATA        TEXT,
    REG_ID          VARCHAR(100),
    NXISAS_TYPE     VARCHAR(40),
    CONSTRAINT NXSIAS_MODULE_TRAN_LOG_HIS_PK PRIMARY KEY (UUID, REG_DTM)
);

-- Audit queries always filter by tenant + recency.
CREATE INDEX IF NOT EXISTS NXSIAS_MODULE_TRAN_LOG_HIS_BIZ_REG_IX
    ON NXSIAS_MODULE_TRAN_LOG_HIS (BIZ_KEY, REG_DTM DESC);

-- Drilldown by job (e.g. "show me all OTP failures last 24h").
CREATE INDEX IF NOT EXISTS NXSIAS_MODULE_TRAN_LOG_HIS_JOB_IX
    ON NXSIAS_MODULE_TRAN_LOG_HIS (BIZ_KEY, NXISAS_JOB, REG_DTM DESC);

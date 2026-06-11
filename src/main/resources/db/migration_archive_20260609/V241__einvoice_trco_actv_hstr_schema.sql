-- V239 — Settings/Contacts family table TRCO_ACTV_HSTR (e-Invoice Scope 4)
--
-- Counterparty activity-history ledger. Legacy nxisas_einvoice_c002_act.jsp
-- writes one row per scraped invoice when a counterparty is auto-registered
-- (lines 883-909). First (and only) refactor consumer: the e-Invoice
-- detail-save auto-registrar (EInvoiceTrcoAutoRegistrar). TRCO_ACTV_HSTR is
-- in the PROD Active Table List.
--
-- Schema source: legacy JEX entity wabooks/entity/TrcoActvHstr(Pk).java +
-- the activity enums (TrcoActvDvCd / TrcoActvTaskCd / TrcoActvPcsnStts /
-- RegStts / TrcoActvCnfmYn). The `postgres` DEV MCP is VPN-gated and was
-- NOT reachable this session, so column types / lengths / nullability were
-- derived from the legacy entity's @Size / @JsonProperty / serializer
-- annotations rather than from a live information_schema query.
-- ►► DEV column-for-column parity (PK column set + exact types) MUST be
--    re-confirmed via the postgres MCP before this lands on any shared DB.
--
-- PK: refactor uses composite (BIZ_KEY, ACTV_SEQ) for tenant safety +
-- per-tenant ACTV_SEQ generation in Java (MAX+1). The legacy JEX Pk class
-- carries ACTV_SEQ alone (BIZ_KEY comes from session); confirm whether DEV's
-- physical PK is (ACTV_SEQ) or (BIZ_KEY, ACTV_SEQ) during the parity pass.
--
-- Idempotent: CREATE TABLE / INDEX IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS TRCO_ACTV_HSTR (
    BIZ_KEY        VARCHAR(20)   NOT NULL,
    ACTV_SEQ       VARCHAR(40)   NOT NULL,
    USER_ID        VARCHAR(100)  NOT NULL,
    TRCO_KEY       VARCHAR(20)   NOT NULL,
    TRCO_CMNM      VARCHAR(200)  NOT NULL,
    TRCO_CMENM     VARCHAR(200),
    TRCO_FLNM      VARCHAR(100),
    API_APV_KEY    VARCHAR(50),
    DV_CD          VARCHAR(10),
    TASK_CD        VARCHAR(10),
    BANK_CD        VARCHAR(20),
    ACCT_NO        VARCHAR(100),
    CCY_CD         VARCHAR(3),
    ACTV_AMT       NUMERIC(20,6),
    CONT           VARCHAR(200),
    NOTE           VARCHAR(255),
    PCSN_STTS      VARCHAR(1),
    REG_DTM        VARCHAR(14)   NOT NULL,
    RCV_NM         VARCHAR(100),
    RSLT_CD        VARCHAR(1),
    RSLT_CONT      VARCHAR(200),
    MOD_ID         VARCHAR(100),
    MOD_DTM        VARCHAR(14),
    TRCO_CNFM_YN   VARCHAR(1),
    TRSC_DT        VARCHAR(8),
    CONSTRAINT trco_actv_hstr_pk PRIMARY KEY (BIZ_KEY, ACTV_SEQ)
);

-- Dedup lookup used by the auto-registrar (legacy c002:895-899):
-- one activity row per (BIZ_KEY, DV_CD, TASK_CD, API_APV_KEY).
CREATE INDEX IF NOT EXISTS ix_trco_actv_hstr_dedup
    ON TRCO_ACTV_HSTR (BIZ_KEY, DV_CD, TASK_CD, API_APV_KEY);

-- Per-tenant ACTV_SEQ MAX+1 generation support.
CREATE INDEX IF NOT EXISTS ix_trco_actv_hstr_biz_seq
    ON TRCO_ACTV_HSTR (BIZ_KEY, ACTV_SEQ);

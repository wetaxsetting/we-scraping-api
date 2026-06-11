-- ============================================================================
-- V238__einvoice_detail_schema.sql
--
-- e-Invoice phase-2 (INVOICE_DETAIL) persistence — closes the legacy
-- `nxisas_einvoice_c002` parity gap. Phase 1 (INVOICE_LIST →
-- BUSA_DOC_MSTR) already exists (V15 + V23). This migration adds the
-- BUSA_DOC_DETL line-item table (not previously migrated locally) and
-- aligns the DISP_ORD type on the existing EINV_FEE_DETL / EINV_TAX_DETL
-- tables so the new JPA `Integer dispOrd` mappings pass Hibernate
-- `validate` (same precedent as V36 / V117).
--
-- Column set for BUSA_DOC_DETL mirrors the legacy entity
-- `wabooks/entity/BusaDocDetl.java` (the active-list table BUSA_DOC_DETL).
-- A future DEV-parity audit may widen it; this covers every column the
-- legacy entity declares.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS + guarded ALTER COLUMN.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BUSA_DOC_DETL — per-invoice line items (legacy BusaDocDetl entity).
--    PK (BIZ_KEY, DOC_ID, DISP_ORD); FK-by-convention to BUSA_DOC_MSTR.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS BUSA_DOC_DETL (
    BIZ_KEY           VARCHAR(20)  NOT NULL,
    DOC_ID            VARCHAR(50)  NOT NULL,
    DISP_ORD          INTEGER      NOT NULL,
    USER_ID           VARCHAR(100),
    ITEM_CD           VARCHAR(50),
    ITEM_NM           VARCHAR(500),
    ITEM_SPEC         VARCHAR(4000),
    ITEM_DETL         VARCHAR(4000),
    ITEM_QUNT         NUMERIC,
    ITEM_UNIT_AMT     NUMERIC,
    SPLY_AMT          NUMERIC,
    TAX_AMT           NUMERIC,
    RCV_PAY_AMT       NUMERIC,
    DC_AMT            NUMERIC,
    BEF_DIS_AMT       NUMERIC,
    NOTE              VARCHAR(255),
    TXTN_PTRN         VARCHAR(50),
    UOM               VARCHAR(90),
    CCY_CD            VARCHAR(3),
    EINV_PRODUCTNAME  VARCHAR(500),
    EINV_TAXRATE      VARCHAR(10),
    REG_ID            VARCHAR(100),
    REG_DTM           VARCHAR(14)  NOT NULL,
    MOD_ID            VARCHAR(100),
    MOD_DTM           VARCHAR(14)  NOT NULL,
    CONSTRAINT pk_busa_doc_detl PRIMARY KEY (BIZ_KEY, DOC_ID, DISP_ORD)
);

CREATE INDEX IF NOT EXISTS ix_busa_doc_detl_biz_doc
    ON BUSA_DOC_DETL (BIZ_KEY, DOC_ID);

-- ----------------------------------------------------------------------------
-- 2. Align EINV_FEE_DETL / EINV_TAX_DETL DISP_ORD NUMERIC -> INTEGER so the
--    JPA `Integer dispOrd` mapping passes `ddl-auto: validate`. V15 created
--    these columns as NUMERIC; same fix pattern as V36 / V117. USING makes
--    the lossless cast explicit. Guarded so re-runs are no-ops.
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'einv_fee_detl' AND column_name = 'disp_ord'
                 AND data_type = 'numeric') THEN
        ALTER TABLE EINV_FEE_DETL ALTER COLUMN DISP_ORD TYPE INTEGER USING DISP_ORD::integer;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'einv_tax_detl' AND column_name = 'disp_ord'
                 AND data_type = 'numeric') THEN
        ALTER TABLE EINV_TAX_DETL ALTER COLUMN DISP_ORD TYPE INTEGER USING DISP_ORD::integer;
    END IF;
END $$;

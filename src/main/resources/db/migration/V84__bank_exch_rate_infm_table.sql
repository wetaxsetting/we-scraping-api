-- V84: Create BANK_EXCH_RATE_INFM table
-- Source: wabooks.entity.BankExchRateInfm (JEX DEV)
-- Populated by batch EXCH_0001_001 (Coocon Shinhan foreign exchange rates)
-- Read by: GET /api/v1/banking/exchange-rates (Exchange Rate Inquiry page)

CREATE TABLE BANK_EXCH_RATE_INFM (
    -- Primary key (matches BankExchRateInfmPk in JEX)
    BIZ_KEY             VARCHAR(50)     NOT NULL,
    BANK_CD             VARCHAR(20)     NOT NULL,
    REF_DATE            VARCHAR(8)      NOT NULL,
    CCY_CD              VARCHAR(10)     NOT NULL,
    EXCH_SEQ_NO         VARCHAR(10)     NOT NULL,

    -- Base currency that rates are quoted against (e.g. KRW)
    REF_CCY_CD          VARCHAR(10)     NOT NULL,

    -- Transfer buy/sell rates
    TRAN_SELL_AMT       NUMERIC(20, 6),
    TRAN_BUY_AMT        NUMERIC(20, 6),

    -- Spot buy/sell rates
    BUY_AMT             NUMERIC(20, 6),
    SELL_AMT            NUMERIC(20, 6),

    -- Search/inquiry date (VARCHAR(8) parity with JEX SRCH_DT)
    SRCH_DT             VARCHAR(8),

    -- Basic rate and basic transfer rate
    BASIC_RATE          NUMERIC(20, 6),
    BASIC_TRAN          NUMERIC(20, 6),

    -- Classification and module from Coocon API response
    CLASS_EXCH          VARCHAR(50),
    MODULE              VARCHAR(100),

    -- Datetime the exchange rate was registered in source system (VARCHAR(14) parity)
    EXCH_RATE_REG_DTM   VARCHAR(14),

    -- Daily sequence number (1 = morning, 2 = afternoon, etc.)
    SEQ_NO              VARCHAR(10),

    -- Stand point from Coocon resultData
    STAND_POINT         VARCHAR(100),

    -- Last update date/time from Coocon response fields
    LAST_UPDATE_DATE    VARCHAR(8),
    LAST_UPDATE_TIME    VARCHAR(6),

    -- ERP sync flag
    ERP_SND_YN          VARCHAR(1)      NOT NULL DEFAULT 'N',

    -- Audit columns
    REG_DTM             VARCHAR(14),
    REG_USER            VARCHAR(100),
    MOD_DTM             VARCHAR(14),
    MOD_ID              VARCHAR(100),

    CONSTRAINT PK_BANK_EXCH_RATE_INFM
        PRIMARY KEY (BIZ_KEY, BANK_CD, REF_DATE, CCY_CD, EXCH_SEQ_NO)
);

-- Index to support the most common query pattern:
-- look up all rates for a tenant on a given date
CREATE INDEX IDX_BANK_EXCH_RATE_INFM_BIZ_DATE
    ON BANK_EXCH_RATE_INFM (BIZ_KEY, REF_DATE, BANK_CD);

-- V138: Create OVRS_PYMN_DTLS — individual transaction rows per Excel-upload batch
-- Each row corresponds to one data line parsed from WeCMS_Overseas_Payment_upload_*.xlsx.
-- Beneficiary detail fields (name, bank, BIC, account, address) are resolved client-side
-- from the contacts endpoint and forwarded by the frontend at submit time.

CREATE TABLE IF NOT EXISTS OVRS_PYMN_DTLS (
    BIZ_KEY             VARCHAR(20)     NOT NULL,
    OVRS_PYMN_NO        VARCHAR(20)     NOT NULL,
    TRN_SEQ             INTEGER         NOT NULL,   -- 1-based sequence within the batch

    -- Excel columns
    BNFC_CODE           VARCHAR(100),               -- Beneficiary Code (contacts key)
    CCY_CD              VARCHAR(10),                -- Transfer Currency
    TRNF_AMT            NUMERIC(20, 2),             -- Transfer Amount
    FEE_PAYER_CD        VARCHAR(10),                -- Fee Payer (O/S/B)
    MEMO                VARCHAR(500),               -- Memo / remittance info
    RPRT_ACCT_CD        VARCHAR(100),               -- Reporting Account (optional)

    -- Resolved from contacts at parse time
    BNFC_NM             VARCHAR(200),               -- Beneficiary Name
    BNFC_BANK_NM        VARCHAR(200),               -- Beneficiary Bank Name
    BIC_CD              VARCHAR(20),                -- Bank Identifier Code
    BNFC_ACCT_NO        VARCHAR(100),               -- Beneficiary Account No
    BNFC_ADDR           VARCHAR(500),               -- Beneficiary Address

    -- Transfer result (flipped to 'Y' by the firm-banking batch)
    TRNF_YN             VARCHAR(1)      NOT NULL DEFAULT 'N',

    REG_DTM             VARCHAR(14)     NOT NULL,
    REG_ID              VARCHAR(100),

    CONSTRAINT PK_OVRS_PYMN_DTLS
        PRIMARY KEY (BIZ_KEY, OVRS_PYMN_NO, TRN_SEQ)
);

CREATE INDEX IF NOT EXISTS IDX_OVRS_PYMN_DTLS_BATCH
    ON OVRS_PYMN_DTLS (BIZ_KEY, OVRS_PYMN_NO);

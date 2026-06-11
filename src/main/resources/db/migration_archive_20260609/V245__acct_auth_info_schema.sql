-- V245 — Account Inquiry per-user authority: ACCT_AUTH_INFO schema (HR-3)
--
-- Legacy fnsh_0001_001 restricts non-admin users to the accounts they
-- are authorised to view. FnshHelper.fnsh_0001_001_r001 merges
-- QueryHelper.getPermittedBankAccountDynamic(...) for non-admins, which
-- intersects ACCT_MSTR against:
--   ( biz_key, bank_cd, acct_no ) IN (
--       SELECT biz_key, bank_cd, acct_no FROM acct_auth_info
--        WHERE biz_key = ? AND user_id = ? AND use_yn = 'Y' AND auth_type = 'V' )
-- Admins bypass the filter entirely. ACCT_AUTH_INFO is in the PROD
-- Active Table List, so it is in scope for the refactor.
--
-- Column-for-column parity with JEX DEV (wecmsvndev.acct_auth_info),
-- verified via postgres_dev_server_jexframe MCP on 2026-06-06:
--   biz_key   VARCHAR(20)  NOT NULL
--   bank_cd   VARCHAR(20)  NOT NULL
--   acct_no   VARCHAR(100) NOT NULL
--   user_id   VARCHAR(100) NOT NULL
--   use_yn    CHAR(1)      NOT NULL DEFAULT 'N'
--   reg_id    VARCHAR(100) NOT NULL
--   reg_dtm   VARCHAR(14)  NOT NULL
--   mod_id    VARCHAR(100)
--   mod_dtm   VARCHAR(14)
--   auth_type CHAR(1)      NOT NULL DEFAULT 'V'
--   PK (biz_key, bank_cd, acct_no, user_id, auth_type)
--   FK (biz_key, bank_cd, acct_no) -> acct_mstr(biz_key, bank_cd, acct_no)
--
-- DEV column count: 10. Local migration column count: 10.
-- CHAR(1) columns (use_yn, auth_type) keep the DEV bpchar type; the
-- AcctAuthInfo entity maps them with @JdbcTypeCode(SqlTypes.CHAR) so
-- Hibernate `validate` accepts the bpchar columns (V38 sort_yn precedent).
-- Idempotent.

CREATE TABLE IF NOT EXISTS ACCT_AUTH_INFO (
    BIZ_KEY   VARCHAR(20)  NOT NULL,
    BANK_CD   VARCHAR(20)  NOT NULL,
    ACCT_NO   VARCHAR(100) NOT NULL,
    USER_ID   VARCHAR(100) NOT NULL,
    USE_YN    CHAR(1)      NOT NULL DEFAULT 'N',
    REG_ID    VARCHAR(100) NOT NULL,
    REG_DTM   VARCHAR(14)  NOT NULL,
    MOD_ID    VARCHAR(100),
    MOD_DTM   VARCHAR(14),
    AUTH_TYPE CHAR(1)      NOT NULL DEFAULT 'V',
    CONSTRAINT ACCT_AUTH_INFO_PK PRIMARY KEY (BIZ_KEY, BANK_CD, ACCT_NO, USER_ID, AUTH_TYPE)
);

-- FK to ACCT_MSTR — mirrors DEV acct_auth_info_fk01. ACCT_MSTR's PK is
-- (BIZ_KEY, BANK_CD, ACCT_NO), so the composite FK target is unique.
-- Wrapped in a guard so re-runs don't error on the already-present
-- constraint (Flyway runs each migration once, but keep idempotent for
-- local re-baselines).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'acct_auth_info_fk01'
    ) THEN
        ALTER TABLE ACCT_AUTH_INFO
            ADD CONSTRAINT ACCT_AUTH_INFO_FK01
            FOREIGN KEY (BIZ_KEY, BANK_CD, ACCT_NO)
            REFERENCES ACCT_MSTR (BIZ_KEY, BANK_CD, ACCT_NO);
    END IF;
END $$;

-- Helper index beyond DEV (allowed by the Legacy Schema Parity Rule) —
-- materialises the per-user permitted-account lookup the Account Inquiry
-- list runs for every non-admin request.
CREATE INDEX IF NOT EXISTS IX_ACCT_AUTH_INFO_BIZ_USER
    ON ACCT_AUTH_INFO (BIZ_KEY, USER_ID, USE_YN, AUTH_TYPE);

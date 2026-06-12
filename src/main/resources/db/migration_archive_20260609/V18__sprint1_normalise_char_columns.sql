-- ============================================================================
-- V18__sprint1_normalise_char_columns.sql
--
-- Hotfix follow-up to V13–V16: convert legacy CHAR columns to VARCHAR so
-- Hibernate `ddl-auto: validate` accepts them. Mirrors the V3 precedent
-- (`V3__normalise_yn_columns.sql`) which did the same for auth tables.
--
-- Background: V13–V16 declared columns with CHAR(N) to match the legacy
-- wecmsvndev shape, but Hibernate's default JDBC mapping for `String`
-- fields is VARCHAR. The mismatch (`bpchar` vs `varchar`) trips
-- `SchemaValidator.validateColumnType` at startup.
--
-- Idempotent: every ALTER uses `TYPE VARCHAR(N)` which is a no-op when
-- the column is already VARCHAR(N), so V18 is safe to re-run and safe
-- on a fresh DB as well (where V13–V16 land first then V18 normalises).
-- ============================================================================

ALTER TABLE ACCT_MSTR
    ALTER COLUMN ACCT_TYPE_CD TYPE VARCHAR(2),
    ALTER COLUMN CCY_CD       TYPE VARCHAR(3),
    ALTER COLUMN ACTV_YN      TYPE VARCHAR(1);

ALTER TABLE ACCT_TRNS_HSTR
    ALTER COLUMN CCY_CD        TYPE VARCHAR(3),
    ALTER COLUMN INOUT_DV      TYPE VARCHAR(1),
    ALTER COLUMN LINK_MATCH_YN TYPE VARCHAR(1);

ALTER TABLE NAPAS_REMIT_TRNS_HSTR
    ALTER COLUMN TRANS_TYPE  TYPE VARCHAR(1),
    ALTER COLUMN CCY_CD      TYPE VARCHAR(3),
    ALTER COLUMN FEE_DFR_SBJ TYPE VARCHAR(1);

ALTER TABLE BUSI_COND_DAILY_SUMR
    ALTER COLUMN CCY_CD TYPE VARCHAR(3);

ALTER TABLE BUSI_COND_DAILY_SUMR_DETL
    ALTER COLUMN CCY_CD   TYPE VARCHAR(3),
    ALTER COLUMN DOC_TYPE TYPE VARCHAR(1);

ALTER TABLE SELL_HIS_DAILY_SUMR
    ALTER COLUMN CCY_CD TYPE VARCHAR(3);

ALTER TABLE SELL_HIS_DAILY_SUMR_DETL
    ALTER COLUMN CCY_CD TYPE VARCHAR(3);

ALTER TABLE BUSA_DOC_MSTR
    ALTER COLUMN DOC_TYPE         TYPE VARCHAR(1),
    ALTER COLUMN TRCO_TRSC_CCY_CD TYPE VARCHAR(3);

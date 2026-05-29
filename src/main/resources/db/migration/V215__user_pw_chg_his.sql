-- ============================================================================
-- V215__user_pw_chg_his.sql
--
-- Change Password feature schema. One PROD-active-list table ported from
-- legacy JEX (USER_PW_CHG_HIS — the password-change-history ledger that backs
-- the "cannot reuse your last 3 passwords" rule at
-- comm_0400_001_u001_act.jsp:110-130 + IDO.USER_PW_CHG_HIS_R003.xml).
--
-- Legacy column shape (wabooks.entity.UserPwChgHis + UserPwChgHisPk):
--   USER_ID     varchar(100)  PK part 1 (global — no BIZ_KEY on this table)
--   REG_SEQ_NO  numeric       PK part 2 (per-user MAX+1)
--   USER_PW     varchar(100)  hashed password (legacy stored hash(:USER_PW);
--                             the refactor stores a BCrypt hash, mirroring
--                             USER_INFM.USER_PW — WecmsPasswordEncoder)
--   MOD_DTM, REG_DTM          timestamps
--   IP_ADDR     varchar(100)
--
-- Refactor deviations from the legacy DDL (documented, intentional — same
-- precedent as USER_LOGIN_HIS, the sibling per-user history table in
-- domain/auth):
--   * REG_DTM / MOD_DTM are TIMESTAMPTZ (OffsetDateTime), not the legacy
--     VARCHAR(14). Matches the USER_LOGIN_HIS shape created in V1 so the two
--     auth-domain history tables stay consistent and ORDER BY REG_DTM DESC
--     uses a real timestamp.
--   * REG_SEQ_NO is BIGINT (mapped to Java Long), not NUMERIC — Hibernate 6
--     `ddl-auto: validate` rejects NUMERIC against a Long/Integer mapping
--     (same V36 / V89 / V92 precedent). Lossless: per-user MAX+1 never
--     overflows BIGINT.
-- No legacy wb_get_next_val branch is added — REG_SEQ_NO is generated in Java
-- (ChangePasswordService, per-user MAX+1) per the "DB Function / Legacy
-- Function Policy" rule that prefers a service-side generator for new writes.
--
-- Idempotent — CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS.
--
-- Feature spec:   docs/refactor-wecms/features/change-password.md
-- ============================================================================

CREATE TABLE IF NOT EXISTS USER_PW_CHG_HIS (
    USER_ID     VARCHAR(100) NOT NULL,
    REG_SEQ_NO  BIGINT       NOT NULL,
    USER_PW     VARCHAR(100) NOT NULL,
    MOD_DTM     TIMESTAMPTZ,
    IP_ADDR     VARCHAR(100),
    REG_DTM     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_user_pw_chg_his PRIMARY KEY (USER_ID, REG_SEQ_NO)
);

-- Materialises both the reuse-history read (last 3 rows per user, REG_DTM
-- DESC) and the next-REG_SEQ_NO MAX+1 lookup. Allowed beyond-DEV per the
-- Legacy Schema Parity Rule (extra performance index).
CREATE INDEX IF NOT EXISTS ix_user_pw_chg_his_user_reg_dtm
    ON USER_PW_CHG_HIS (USER_ID, REG_DTM DESC, REG_SEQ_NO DESC);

-- ============================================================================
-- V4__winv_user.sql — WeInvoice-owned user accounts (new table)
--
-- TARGET DATABASE: the WeInvoice database (the PostgreSQL database the
-- we-scraping engine writes), reached by the backend through
-- WECMS_WEINVOICE_DB_URL. NOT the WeCMS database.
--
-- Execution: MANUAL (DBA), like V1–V3 — spring.flyway.enabled=false and
-- nothing applies DDL at startup. Run AFTER V1__winv_schema.sql. The schema
-- name `weinvoice` is the default of WECMS_WEINVOICE_APP_SCHEMA; replace it if
-- an environment uses another name. Must be applied BEFORE the backend that
-- serves POST /api/v1/auth/signup is deployed (without it login falls back to
-- WeCMS only and sign-up fails).
--
-- Related record:
--   docs/features/addon-service/weinvoice/weinvoice-self-signup-2026-11-13.md
-- Companion data script (moves one existing WeCMS user here):
--   docs/sql/2026/move-wecms-user-to-weinvoice-2026-11-13.sql
--
-- Privileges: when the backend and the engine use different roles (see
-- docs/sql/2026/grant-weinvoice-schema-access-2026-11-02.sql), after this file:
--   GRANT SELECT, INSERT, UPDATE ON weinvoice.winv_user_infm TO <app_role>;
-- The engine never reads or writes it. Skip on a single-role setup.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- WINV_USER_INFM — accounts that sign in to WeInvoice without a WeCMS USER_INFM row
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weinvoice.winv_user_infm (
	user_id      varchar(100)  NOT NULL,
	user_nm      varchar(100)  NOT NULL,
	eml          varchar(100)  NOT NULL,
	clph_no      varchar(30),
	cmpn_nm      varchar(200),
	user_pw      varchar(100)  NOT NULL,
	lang_cd      varchar(2),
	actv_yn      char(1)       NOT NULL DEFAULT 'Y',
	lock_yn      char(1)       NOT NULL DEFAULT 'N',
	del_yn       char(1)       NOT NULL DEFAULT 'N',
	fail_cnt     integer       NOT NULL DEFAULT 0,
	lst_lgn_dtm  varchar(14),
	reg_src      varchar(10)   NOT NULL DEFAULT 'SIGNUP',
	reg_dtm      varchar(14)   NOT NULL,
	mod_dtm      varchar(14)   NOT NULL,
	CONSTRAINT winv_user_infm_pk PRIMARY KEY (user_id),
	CONSTRAINT winv_user_infm_actv_ck CHECK (actv_yn IN ('Y', 'N')),
	CONSTRAINT winv_user_infm_lock_ck CHECK (lock_yn IN ('Y', 'N')),
	CONSTRAINT winv_user_infm_del_ck  CHECK (del_yn IN ('Y', 'N')),
	CONSTRAINT winv_user_infm_src_ck  CHECK (reg_src IN ('SIGNUP', 'WECMS'))
);

-- One account per login id regardless of case (sign-up lower-cases; a moved WeCMS id keeps its spelling).
CREATE UNIQUE INDEX IF NOT EXISTS winv_user_infm_lower_uk ON weinvoice.winv_user_infm (lower(user_id));

COMMENT ON TABLE  weinvoice.winv_user_infm IS 'WeInvoice-owned login accounts (self sign-up, or moved from WeCMS USER_INFM). Login checks this table before WeCMS; user_id is the owner_id of the user''s winv_* rows.';
COMMENT ON COLUMN weinvoice.winv_user_infm.user_id IS 'Login id = email (lower-cased on sign-up). Becomes owner_id on winv_cmpn_infm and the other winv_* tables.';
COMMENT ON COLUMN weinvoice.winv_user_infm.user_pw IS 'BCrypt for sign-up accounts; a moved WeCMS account keeps its legacy SHA-256 base64 hash (both verify).';
COMMENT ON COLUMN weinvoice.winv_user_infm.fail_cnt IS 'Consecutive wrong passwords; 5 sets lock_yn = Y. Reset on a successful login.';
COMMENT ON COLUMN weinvoice.winv_user_infm.reg_src IS 'SIGNUP = created by POST /api/v1/auth/signup; WECMS = copied from WeCMS USER_INFM.';
COMMENT ON COLUMN weinvoice.winv_user_infm.reg_dtm IS 'UTC yyyyMMddHHmmss.';

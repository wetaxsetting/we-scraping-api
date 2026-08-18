-- ============================================================================
-- V254__zalo_oa_integration.sql
--
-- Zalo Official Account (OA) messaging — schema skeleton.
--
-- NOT a JEX port. Zalo has no legacy JEX counterpart, so the "Legacy Schema
-- Parity Rule" (column-for-column DEV mirroring) does not apply here — there is
-- no DEV table to mirror. These are refactor-native tables. Naming still follows
-- the house legacy conventions so the schema reads uniformly:
--   * UPPER_SNAKE identifiers, `_INFM` for master rows, `_HIS` for history
--   * audit quartet REG_ID / REG_DTM / MOD_ID / MOD_DTM, datetimes as
--     varchar(14) `yyyyMMddHHmmss` (BaseEntity + LegacyDtmConverter)
--   * Y/N flags as bpchar(1) with a CHECK constraint
--   * LANG_CD varchar(3), matching AUDIT_LOGS_INFM
--
-- Functional reference: the WeBill365 VN implementation, audited 2026-08-17 —
-- webill365-vn-api/docs/features/zalo-oa-integration.md. Informed by, not
-- copied from: three defects found in that audit are designed out here.
--   1. WeBill365 splits msg_auth (tenant<->OA link) from msg_auth_token (tokens
--      keyed by a globally-UNIQUE oa_id). Two tenants connecting the same OA
--      therefore share one token row and overwrite each other. Here the link and
--      its tokens live in ONE row keyed (BIZ_KEY, OA_ID), so tenants cannot
--      collide.
--   2. WeBill365 enforces "one active OA per tenant" in Java only. Here a
--      partial UNIQUE index enforces it in the database.
--   3. WeBill365's template lookup falls back to findFirst() when several
--      language rows exist, so the wrong language can be picked. Here language
--      is part of the template primary key, making the lookup exact.
--
-- Deliberately NOT carried over from WeBill365:
--   * OPR_STS — a third status flag that had no effect on send behaviour.
--   * client.zalo.template.* config keys — dead config; template ids live in
--     ZALO_TMPL_INFM.
--
-- Open decisions (do not treat this migration as final):
--   * ZALO_TMPL_INFM.TMPL_TYPE is an open code column. The WeCMS message
--     catalogue is not decided yet, so no CHECK constraint is placed on it.
--     Add one in a follow-up migration once the catalogue is agreed.
--   * ZALO_OA_INFM.ACCESS_TOKEN / REFRESH_TOKEN are plaintext, matching the
--     WeBill365 behaviour. Encryption at rest is a separate decision.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ZALO_OA_INFM — one row per (tenant, Official Account) connection
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zalo_oa_infm (
	biz_key         varchar(20)   NOT NULL,
	oa_id           varchar(50)   NOT NULL,
	oa_nm           varchar(100)  NULL,
	access_token    varchar(1000) NULL,
	refresh_token   varchar(1000) NULL,
	token_expi_dtm  varchar(14)   NULL,
	auth_cd         varchar(255)  NULL,
	use_yn          bpchar(1)     NOT NULL DEFAULT 'N',
	del_yn          bpchar(1)     NOT NULL DEFAULT 'N',
	reg_id          varchar(100)  NOT NULL,
	reg_dtm         varchar(14)   NOT NULL,
	mod_id          varchar(100)  NULL,
	mod_dtm         varchar(14)   NULL,
	CONSTRAINT zalo_oa_infm_pk   PRIMARY KEY (biz_key, oa_id),
	CONSTRAINT zalo_oa_infm_chk01 CHECK ((use_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT zalo_oa_infm_chk02 CHECK ((del_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT zalo_oa_infm_fk01 FOREIGN KEY (biz_key) REFERENCES busi_infm(biz_key)
);

COMMENT ON TABLE  zalo_oa_infm IS 'Zalo Official Account connected by a tenant, with its OAuth tokens.';
COMMENT ON COLUMN zalo_oa_infm.oa_id          IS 'Zalo Official Account ID. Supplied by the tenant, then confirmed by the OAuth callback.';
COMMENT ON COLUMN zalo_oa_infm.token_expi_dtm IS 'Absolute access-token expiry, yyyyMMddHHmmss. Derived from the expires_in Zalo returns.';
COMMENT ON COLUMN zalo_oa_infm.auth_cd        IS 'Most recent OAuth authorization code, kept for troubleshooting only.';
COMMENT ON COLUMN zalo_oa_infm.use_yn         IS 'Y = this OA is the one used for outbound sends. At most one per tenant (zalo_oa_infm_uk01).';
COMMENT ON COLUMN zalo_oa_infm.del_yn         IS 'Y = disconnected (soft delete). Excluded from every lookup.';

-- One live OA per tenant. WeBill365 enforced this in service code only, which
-- leaves the invariant open to a concurrent update; a partial unique index
-- closes it.
CREATE UNIQUE INDEX IF NOT EXISTS zalo_oa_infm_uk01
	ON zalo_oa_infm (biz_key)
	WHERE use_yn = 'Y' AND del_yn = 'N';

-- Token-refresh job scans by staleness, not by tenant.
CREATE INDEX IF NOT EXISTS zalo_oa_infm_ix01
	ON zalo_oa_infm (mod_dtm)
	WHERE use_yn = 'Y' AND del_yn = 'N';

-- ---------------------------------------------------------------------------
-- ZALO_TMPL_INFM — approved Zalo template ids, per OA / type / language
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS zalo_tmpl_infm (
	biz_key    varchar(20)   NOT NULL,
	oa_id      varchar(50)   NOT NULL,
	tmpl_type  varchar(20)   NOT NULL,
	lang_cd    varchar(3)    NOT NULL,
	tmpl_id    varchar(50)   NOT NULL,
	unit_prc   numeric(20,6) NULL,
	use_yn     bpchar(1)     NOT NULL DEFAULT 'Y',
	reg_id     varchar(100)  NOT NULL,
	reg_dtm    varchar(14)   NOT NULL,
	mod_id     varchar(100)  NULL,
	mod_dtm    varchar(14)   NULL,
	CONSTRAINT zalo_tmpl_infm_pk    PRIMARY KEY (biz_key, oa_id, tmpl_type, lang_cd),
	CONSTRAINT zalo_tmpl_infm_chk01 CHECK ((use_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT zalo_tmpl_infm_fk01  FOREIGN KEY (biz_key, oa_id) REFERENCES zalo_oa_infm(biz_key, oa_id)
);

COMMENT ON TABLE  zalo_tmpl_infm IS 'Zalo-approved template ids bound to an OA. Language is part of the key so template resolution is exact.';
COMMENT ON COLUMN zalo_tmpl_infm.tmpl_type IS 'Message-type code. Open column — the WeCMS message catalogue is not finalised, so no CHECK constraint yet.';
COMMENT ON COLUMN zalo_tmpl_infm.lang_cd   IS 'EN / KO / KM / VI, matching common.enums.LangCd.';
COMMENT ON COLUMN zalo_tmpl_infm.tmpl_id   IS 'Template id issued by Zalo after manual approval. Numeric string, e.g. 358731.';
COMMENT ON COLUMN zalo_tmpl_infm.unit_prc  IS 'Per-message price Zalo charges for this template. Reference only; no billing logic reads it yet.';

-- ---------------------------------------------------------------------------
-- ZALO_SEND_HIS — outbound send log, one row per Zalo API call
-- ---------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS sq_zalo_send_seq
	INCREMENT BY 1
	MINVALUE 1
	MAXVALUE 9223372036854775807
	START 1
	CACHE 1
	NO CYCLE;

CREATE TABLE IF NOT EXISTS zalo_send_his (
	send_seq    numeric(18)  NOT NULL,
	biz_key     varchar(20)  NOT NULL,
	oa_id       varchar(50)  NULL,
	tmpl_id     varchar(50)  NULL,
	tmpl_type   varchar(20)  NULL,
	lang_cd     varchar(3)   NULL,
	rcvr_no     varchar(50)  NULL,
	tracking_id varchar(50)  NULL,
	req_data    text         NULL,
	req_dtm     varchar(14)  NULL,
	res_data    text         NULL,
	res_dtm     varchar(14)  NULL,
	rslt_cd     varchar(20)  NULL,
	rslt_msg    varchar(500) NULL,
	reg_id      varchar(100) NOT NULL,
	reg_dtm     varchar(14)  NOT NULL,
	mod_id      varchar(100) NULL,
	mod_dtm     varchar(14)  NULL,
	CONSTRAINT zalo_send_his_pk PRIMARY KEY (send_seq)
);

COMMENT ON TABLE  zalo_send_his IS 'One row per outbound Zalo template-message call, written on both success and failure.';
COMMENT ON COLUMN zalo_send_his.rcvr_no     IS 'Recipient phone in E.164 digits, as sent to Zalo.';
COMMENT ON COLUMN zalo_send_his.tracking_id IS 'UUID passed to Zalo as tracking_id. Stored so a future delivery webhook can correlate.';
COMMENT ON COLUMN zalo_send_his.rslt_cd     IS 'Zalo error code as text (0 = success), or an HTTP status / local failure marker.';

-- No FK to zalo_oa_infm: the log must survive an OA being deleted, and a send
-- that failed before an OA was resolved still deserves a row.
CREATE INDEX IF NOT EXISTS zalo_send_his_ix01 ON zalo_send_his (biz_key, reg_dtm DESC);
CREATE INDEX IF NOT EXISTS zalo_send_his_ix02 ON zalo_send_his (tracking_id);

-- ============================================================================
-- V1__winv_schema.sql — WeInvoice schema-of-record
--
-- TARGET DATABASE: the WeInvoice database (the PostgreSQL database the
-- we-scraping engine writes), reached by the backend through
-- WECMS_WEINVOICE_DB_URL. NOT the WeCMS database: nothing here may be run
-- against the database configured in spring.datasource.
--
-- Execution: MANUAL (DBA), like every schema file of this repository —
-- spring.flyway.enabled=false and nothing applies DDL at startup. The schema
-- name `weinvoice` is the default of WECMS_WEINVOICE_APP_SCHEMA; if an
-- environment uses another name, replace it before running.
--
-- Related record:
--   docs/features/addon-service/weinvoice/weinvoice-api-implementation-2026-11-02.md
--
-- Refactor-native tables (no JEX counterpart). Naming follows the house
-- conventions of V254: lower-case identifiers, `_infm` master rows, audit
-- quartet REG_ID / REG_DTM / MOD_ID / MOD_DTM with datetimes as varchar(14)
-- yyyyMMddHHmmss (UTC), Y/N flags as bpchar(1) with a CHECK constraint.
-- Payloads exchanged with the Python engine are jsonb.
--
-- Ownership: the backend writes every table here; the engine reads requests,
-- connections, lookup codes and uploads, and writes request progress, upload
-- results and sync coverage. Engine tables (invoices, invoice_xml, …) live in
-- their own schema and are only read by the backend.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS weinvoice;

-- ---------------------------------------------------------------------------
-- WINV_CMPN_INFM — tax codes (MST) a tenant manages, with its GDT account
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weinvoice.winv_cmpn_infm (
	biz_key       varchar(20)   NOT NULL,
	cmpn_mst      varchar(14)   NOT NULL,
	cmpn_nm       varchar(500)  NULL,
	lbl_nm        varchar(100)  NULL,
	gdt_user_id   varchar(64)   NULL,
	gdt_pw_enc    varchar(1000) NULL,
	conn_stts     varchar(20)   NOT NULL DEFAULT 'PENDING',
	conn_msg      varchar(500)  NULL,
	conn_chk_dtm  varchar(14)   NULL,
	dflt_yn       bpchar(1)     NOT NULL DEFAULT 'N',
	del_yn        bpchar(1)     NOT NULL DEFAULT 'N',
	reg_id        varchar(100)  NOT NULL,
	reg_dtm       varchar(14)   NOT NULL,
	mod_id        varchar(100)  NULL,
	mod_dtm       varchar(14)   NULL,
	CONSTRAINT winv_cmpn_infm_pk    PRIMARY KEY (biz_key, cmpn_mst),
	CONSTRAINT winv_cmpn_infm_chk01 CHECK ((conn_stts = ANY (ARRAY['PENDING', 'CONNECTED', 'ERROR', 'EXPIRED', 'NEED_PASSWORD']))),
	CONSTRAINT winv_cmpn_infm_chk02 CHECK ((dflt_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT winv_cmpn_infm_chk03 CHECK ((del_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar])))
);

COMMENT ON TABLE  weinvoice.winv_cmpn_infm IS 'Tax code (MST) managed by a WeCMS tenant in WeInvoice, with the GDT account the engine logs in with.';
COMMENT ON COLUMN weinvoice.winv_cmpn_infm.gdt_user_id IS 'GDT login. May differ from the MST (sub-account form 1201496252-U002).';
COMMENT ON COLUMN weinvoice.winv_cmpn_infm.gdt_pw_enc  IS 'GDT password: "v1:" + base64(iv(12) || AES-256-GCM ciphertext+tag) under WECMS_WEINVOICE_SECRET_KEY. Never returned by the API.';
COMMENT ON COLUMN weinvoice.winv_cmpn_infm.conn_stts   IS 'PENDING = waiting for the engine login test; CONNECTED; ERROR = login failed; EXPIRED = password no longer accepted; NEED_PASSWORD = added from an upload, no password yet.';
COMMENT ON COLUMN weinvoice.winv_cmpn_infm.del_yn      IS 'Y = removed by the tenant (soft delete). Invoices already collected stay in the engine tables.';

CREATE INDEX IF NOT EXISTS winv_cmpn_infm_ix01
	ON weinvoice.winv_cmpn_infm (cmpn_mst)
	WHERE del_yn = 'N';

-- ---------------------------------------------------------------------------
-- WINV_ENGN_RQST — work the backend asks the engine to do
-- ---------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS weinvoice.sq_winv_rqst_seq
	INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS weinvoice.winv_engn_rqst (
	rqst_seq    numeric(18)   NOT NULL,
	biz_key     varchar(20)   NOT NULL,
	cmpn_mst    varchar(14)   NULL,
	rqst_type   varchar(20)   NOT NULL,
	trgr_cd     varchar(20)   NOT NULL DEFAULT 'MANUAL',
	rqst_prmt   jsonb         NOT NULL DEFAULT '{}'::jsonb,
	rqst_stts   varchar(20)   NOT NULL DEFAULT 'QUEUED',
	step_cd     varchar(20)   NULL,
	prgs_data   jsonb         NULL,
	rslt_data   jsonb         NULL,
	err_msg     varchar(1000) NULL,
	cncl_yn     bpchar(1)     NOT NULL DEFAULT 'N',
	job_run_id  bigint        NULL,
	strt_dtm    varchar(14)   NULL,
	end_dtm     varchar(14)   NULL,
	reg_id      varchar(100)  NOT NULL,
	reg_dtm     varchar(14)   NOT NULL,
	mod_id      varchar(100)  NULL,
	mod_dtm     varchar(14)   NULL,
	CONSTRAINT winv_engn_rqst_pk    PRIMARY KEY (rqst_seq),
	CONSTRAINT winv_engn_rqst_chk01 CHECK ((rqst_type = ANY (ARRAY['SYNC', 'FETCH_XML', 'FETCH_PDF', 'LOGIN_TEST', 'IMPORT', 'TEST_DATA']))),
	CONSTRAINT winv_engn_rqst_chk02 CHECK ((trgr_cd = ANY (ARRAY['MANUAL', 'BULK', 'SCHEDULE']))),
	CONSTRAINT winv_engn_rqst_chk03 CHECK ((rqst_stts = ANY (ARRAY['QUEUED', 'RUNNING', 'DONE', 'PARTIAL', 'FAILED', 'CANCELLED']))),
	CONSTRAINT winv_engn_rqst_chk04 CHECK ((cncl_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar])))
);

COMMENT ON TABLE  weinvoice.winv_engn_rqst IS 'Request queue backend -> engine. The backend inserts QUEUED rows; engine workers claim them (FOR UPDATE SKIP LOCKED) and write status, step, progress and result.';
COMMENT ON COLUMN weinvoice.winv_engn_rqst.rqst_prmt IS 'Request parameters. SYNC: {direction, from_month, to_month}; FETCH_XML/FETCH_PDF: {invoice_ids, lookup_code?, company_code?}; LOGIN_TEST: {}; IMPORT: {upload_id}; TEST_DATA: {}.';
COMMENT ON COLUMN weinvoice.winv_engn_rqst.step_cd   IS 'Engine step shown by the UI. SYNC: login | captcha | list | xml | done. FETCH_PDF: open | submit | download | done.';
COMMENT ON COLUMN weinvoice.winv_engn_rqst.prgs_data IS 'Engine progress, e.g. SYNC {month, listed, xml_done, xml_total}.';
COMMENT ON COLUMN weinvoice.winv_engn_rqst.rslt_data IS 'Engine result, e.g. SYNC {new, updated, xml}.';
COMMENT ON COLUMN weinvoice.winv_engn_rqst.cncl_yn   IS 'Y = the user asked to stop. A QUEUED row is cancelled directly by the backend; a RUNNING row is stopped by the engine at its next checkpoint.';
COMMENT ON COLUMN weinvoice.winv_engn_rqst.job_run_id IS 'Engine job_runs.id of the run that served this request, when the engine links it.';

CREATE INDEX IF NOT EXISTS winv_engn_rqst_ix01
	ON weinvoice.winv_engn_rqst (rqst_seq)
	WHERE rqst_stts IN ('QUEUED', 'RUNNING');

CREATE INDEX IF NOT EXISTS winv_engn_rqst_ix02
	ON weinvoice.winv_engn_rqst (biz_key, rqst_type, cmpn_mst, rqst_seq DESC);

-- ---------------------------------------------------------------------------
-- WINV_TAG_INFM / WINV_INVC_TAG — tenant tags and their invoices
-- ---------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS weinvoice.sq_winv_tag_seq
	INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS weinvoice.winv_tag_infm (
	tag_seq  numeric(18)  NOT NULL,
	biz_key  varchar(20)  NOT NULL,
	tag_nm   varchar(50)  NOT NULL,
	colr_cd  varchar(20)  NULL,
	reg_id   varchar(100) NOT NULL,
	reg_dtm  varchar(14)  NOT NULL,
	mod_id   varchar(100) NULL,
	mod_dtm  varchar(14)  NULL,
	CONSTRAINT winv_tag_infm_pk   PRIMARY KEY (tag_seq),
	CONSTRAINT winv_tag_infm_uk01 UNIQUE (biz_key, tag_nm)
);

COMMENT ON TABLE weinvoice.winv_tag_infm IS 'Invoice tag defined by a tenant (name + colour).';

CREATE TABLE IF NOT EXISTS weinvoice.winv_invc_tag (
	biz_key  varchar(20)  NOT NULL,
	tag_seq  numeric(18)  NOT NULL,
	invc_id  bigint       NOT NULL,
	reg_id   varchar(100) NOT NULL,
	reg_dtm  varchar(14)  NOT NULL,
	CONSTRAINT winv_invc_tag_pk   PRIMARY KEY (biz_key, tag_seq, invc_id),
	CONSTRAINT winv_invc_tag_fk01 FOREIGN KEY (tag_seq) REFERENCES weinvoice.winv_tag_infm(tag_seq) ON DELETE CASCADE
);

COMMENT ON TABLE  weinvoice.winv_invc_tag IS 'Tag assigned to an invoice. invc_id = engine invoices.id (no FK: other schema, engine-owned).';

CREATE INDEX IF NOT EXISTS winv_invc_tag_ix01
	ON weinvoice.winv_invc_tag (biz_key, invc_id);

-- ---------------------------------------------------------------------------
-- WINV_INVC_FILE — files a user attached to an invoice
-- ---------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS weinvoice.sq_winv_file_seq
	INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS weinvoice.winv_invc_file (
	file_seq     numeric(18)  NOT NULL,
	biz_key      varchar(20)  NOT NULL,
	cmpn_mst     varchar(14)  NOT NULL,
	invc_id      bigint       NOT NULL,
	file_kind    varchar(20)  NOT NULL,
	file_orgn    varchar(20)  NOT NULL DEFAULT 'UPLOAD',
	file_nm      varchar(255) NOT NULL,
	file_size    bigint       NOT NULL,
	stor_path    varchar(500) NOT NULL,
	file_sha256  bpchar(64)   NOT NULL,
	cnfm_yn      bpchar(1)    NOT NULL DEFAULT 'N',
	del_yn       bpchar(1)    NOT NULL DEFAULT 'N',
	reg_id       varchar(100) NOT NULL,
	reg_dtm      varchar(14)  NOT NULL,
	mod_id       varchar(100) NULL,
	mod_dtm      varchar(14)  NULL,
	CONSTRAINT winv_invc_file_pk    PRIMARY KEY (file_seq),
	CONSTRAINT winv_invc_file_chk01 CHECK ((file_kind = ANY (ARRAY['XML', 'PDF', 'ATTACHMENT']))),
	CONSTRAINT winv_invc_file_chk02 CHECK ((file_orgn = ANY (ARRAY['UPLOAD']))),
	CONSTRAINT winv_invc_file_chk03 CHECK ((cnfm_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT winv_invc_file_chk04 CHECK ((del_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar])))
);

COMMENT ON TABLE  weinvoice.winv_invc_file IS 'File a user uploaded into an invoice record (UI Spec 3.5). Files from GDT, the provider portal and mail stay in the engine tables.';
COMMENT ON COLUMN weinvoice.winv_invc_file.stor_path IS 'Path relative to WECMS_WEINVOICE_STORAGE_ROOT.';
COMMENT ON COLUMN weinvoice.winv_invc_file.cnfm_yn   IS 'Y = attached although the backend could not prove the PDF belongs to the invoice ("Vẫn đính kèm").';

CREATE INDEX IF NOT EXISTS winv_invc_file_ix01
	ON weinvoice.winv_invc_file (biz_key, invc_id)
	WHERE del_yn = 'N';

-- ---------------------------------------------------------------------------
-- WINV_LKUP_CD — lookup code typed by a user (UI Spec 3.4 "!" pill)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weinvoice.winv_lkup_cd (
	biz_key   varchar(20)   NOT NULL,
	invc_id   bigint        NOT NULL,
	cmpn_mst  varchar(14)   NOT NULL,
	lkup_cd   varchar(200)  NULL,
	cmpn_cd   varchar(100)  NULL,
	lkup_url  varchar(1000) NULL,
	reg_id    varchar(100)  NOT NULL,
	reg_dtm   varchar(14)   NOT NULL,
	mod_id    varchar(100)  NULL,
	mod_dtm   varchar(14)   NULL,
	CONSTRAINT winv_lkup_cd_pk    PRIMARY KEY (biz_key, invc_id),
	CONSTRAINT winv_lkup_cd_chk01 CHECK ((lkup_cd IS NOT NULL OR lkup_url IS NOT NULL))
);

COMMENT ON TABLE  weinvoice.winv_lkup_cd IS 'Lookup code entered by hand. Takes precedence over the code guessed from the XML.';
COMMENT ON COLUMN weinvoice.winv_lkup_cd.cmpn_cd  IS 'Second lookup parameter some providers need (WinInvoice "Mã công ty").';
COMMENT ON COLUMN weinvoice.winv_lkup_cd.lkup_url IS 'Full lookup link when the user pasted a link instead of a code.';

-- ---------------------------------------------------------------------------
-- WINV_UPLD_INFM / WINV_UPLD_FILE — manual uploads and their outcome
-- ---------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS weinvoice.sq_winv_upld_seq
	INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE;

CREATE TABLE IF NOT EXISTS weinvoice.winv_upld_infm (
	upld_seq  numeric(18)  NOT NULL,
	biz_key   varchar(20)  NOT NULL,
	cmpn_mst  varchar(14)  NOT NULL,
	rqst_seq  numeric(18)  NULL,
	file_cnt  integer      NOT NULL DEFAULT 0,
	del_yn    bpchar(1)    NOT NULL DEFAULT 'N',
	reg_id    varchar(100) NOT NULL,
	reg_dtm   varchar(14)  NOT NULL,
	mod_id    varchar(100) NULL,
	mod_dtm   varchar(14)  NULL,
	CONSTRAINT winv_upld_infm_pk    PRIMARY KEY (upld_seq),
	CONSTRAINT winv_upld_infm_chk01 CHECK ((del_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar])))
);

COMMENT ON TABLE  weinvoice.winv_upld_infm IS 'One upload batch (screen 09). The engine imports it through the IMPORT request rqst_seq.';

CREATE TABLE IF NOT EXISTS weinvoice.winv_upld_file (
	upld_seq     numeric(18)   NOT NULL,
	file_no      integer       NOT NULL,
	file_nm      varchar(255)  NOT NULL,
	file_size    bigint        NOT NULL,
	stor_path    varchar(500)  NOT NULL,
	file_sha256  bpchar(64)    NOT NULL,
	rslt_cd      varchar(20)   NULL,
	rslt_msg     varchar(1000) NULL,
	invc_id      bigint        NULL,
	nbmst        varchar(14)   NULL,
	nmmst        varchar(14)   NULL,
	ptnr_nm      varchar(500)  NULL,
	totl_amt     numeric(18,2) NULL,
	prcs_dtm     varchar(14)   NULL,
	CONSTRAINT winv_upld_file_pk    PRIMARY KEY (upld_seq, file_no),
	CONSTRAINT winv_upld_file_fk01  FOREIGN KEY (upld_seq) REFERENCES weinvoice.winv_upld_infm(upld_seq) ON DELETE CASCADE,
	CONSTRAINT winv_upld_file_chk01 CHECK ((rslt_cd IS NULL OR rslt_cd = ANY (ARRAY['NEW', 'MERGED', 'DUPLICATE', 'FOREIGN', 'ERROR', 'PDF_PENDING'])))
);

COMMENT ON TABLE  weinvoice.winv_upld_file IS 'One file of an upload batch. rslt_* columns are written by the engine (UI Spec 3.2 outcomes).';
COMMENT ON COLUMN weinvoice.winv_upld_file.rslt_cd IS 'NULL = not processed yet. NEW | MERGED (XML added to an existing record) | DUPLICATE | FOREIGN (MST not managed) | ERROR | PDF_PENDING (PDF waiting for its XML).';

-- ---------------------------------------------------------------------------
-- WINV_PRVD_INFM — catalogue of e-invoice providers (UI Spec 3.4, screen 16)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weinvoice.winv_prvd_infm (
	prvd_mst       varchar(14)  NOT NULL,
	disp_ord       integer      NOT NULL,
	prvd_nm        varchar(200) NOT NULL,
	prvd_full_nm   varchar(500) NULL,
	web_url        varchar(500) NULL,
	lkup_url       varchar(500) NULL,
	cptch_url      varchar(500) NULL,
	alt_lkup_urls  jsonb        NULL,
	vrfy_note      varchar(500) NULL,
	use_yn         bpchar(1)    NOT NULL DEFAULT 'Y',
	reg_id         varchar(100) NOT NULL,
	reg_dtm        varchar(14)  NOT NULL,
	mod_id         varchar(100) NULL,
	mod_dtm        varchar(14)  NULL,
	CONSTRAINT winv_prvd_infm_pk    PRIMARY KEY (prvd_mst),
	CONSTRAINT winv_prvd_infm_chk01 CHECK ((use_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar])))
);

COMMENT ON TABLE  weinvoice.winv_prvd_infm IS 'E-invoice provider catalogue keyed by MSTTCGP. Names are resolved at read time, so adding a provider renames invoices already stored.';
COMMENT ON COLUMN weinvoice.winv_prvd_infm.vrfy_note IS 'Note from the catalogue review (shared link, login page, ...).';

-- ---------------------------------------------------------------------------
-- WINV_USER_PREF — per-user WeInvoice preferences
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weinvoice.winv_user_pref (
	biz_key        varchar(20)  NOT NULL,
	user_id        varchar(100) NOT NULL,
	lang_cd        varchar(3)   NULL,
	dflt_cmpn_mst  varchar(14)  NULL,
	shct_data      jsonb        NULL,
	reg_id         varchar(100) NOT NULL,
	reg_dtm        varchar(14)  NOT NULL,
	mod_id         varchar(100) NULL,
	mod_dtm        varchar(14)  NULL,
	CONSTRAINT winv_user_pref_pk PRIMARY KEY (biz_key, user_id)
);

COMMENT ON TABLE weinvoice.winv_user_pref IS 'WeInvoice UI language, default working MST and keyboard shortcuts of one user in one tenant.';

-- ---------------------------------------------------------------------------
-- WINV_SYNC_CVRG — how complete the GDT list is per MST / list type / month
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weinvoice.winv_sync_cvrg (
	cmpn_mst       varchar(14) NOT NULL,
	sub_type       varchar(20) NOT NULL,
	prd_ym         varchar(6)  NOT NULL,
	gdt_cnt        integer     NULL,
	got_cnt        integer     NULL,
	cmpl_yn        bpchar(1)   NOT NULL DEFAULT 'N',
	last_sync_dtm  varchar(14) NULL,
	CONSTRAINT winv_sync_cvrg_pk    PRIMARY KEY (cmpn_mst, sub_type, prd_ym),
	CONSTRAINT winv_sync_cvrg_chk01 CHECK ((cmpl_yn = ANY (ARRAY['Y'::bpchar, 'N'::bpchar])))
);

COMMENT ON TABLE  weinvoice.winv_sync_cvrg IS 'Written by the engine after each list sync. Drives the "period not fully fetched" banner (UI Spec 3.6).';
COMMENT ON COLUMN weinvoice.winv_sync_cvrg.sub_type IS 'Engine list type: ban | ban-khma | ban-mtt | mua | mua-khma | mua-mtt.';
COMMENT ON COLUMN weinvoice.winv_sync_cvrg.gdt_cnt  IS 'Invoices GDT listed for the month.';
COMMENT ON COLUMN weinvoice.winv_sync_cvrg.got_cnt  IS 'Invoices of that month stored in the engine tables.';
COMMENT ON COLUMN weinvoice.winv_sync_cvrg.cmpl_yn  IS 'Y = every month day was listed without error and got_cnt >= gdt_cnt.';

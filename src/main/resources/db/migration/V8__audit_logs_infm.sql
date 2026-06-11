-- audit_logs_infm definition

-- Drop table

-- DROP TABLE audit_logs_infm;

-- NOTE — approved refactor divergence (2026-06-09): this base DDL keeps the
-- JEX-original `wsvc_id varchar(20)`. In the live JEX DBs the column is widened
-- to varchar(100) to hold Next.js route/screen slugs, via the DBA-required
-- script `Claude Code (AI)/sql/audit_logs_infm_wsvc_id_widen_20260609.sql`.
-- The JPA entity AuditLogEntry maps WSVC_ID as length = 100. Do NOT change the
-- varchar(20) below — this file is the JEX base DDL of record.

CREATE TABLE audit_logs_infm (
	biz_key varchar(20) NOT NULL,
	audit_logs_seq_no numeric(10) NOT NULL,
	wsvc_id varchar(50) NOT NULL,
	lang_cd varchar(3) NOT NULL,
	ref_no varchar(50) NOT NULL,
	ref_no1 varchar(50) NOT NULL,
	ref_no2 varchar(50) NULL,
	ref_no3 varchar(50) NULL,
	ref_no4 varchar(50) NULL,
	ref_no5 varchar(50) NULL,
	audit_logs_actv_dtl text NOT NULL,
	reg_id varchar(100) NOT NULL,
	reg_nm varchar(100) NOT NULL,
	reg_dtm varchar(14) NOT NULL,
	audit_logs_actv varchar(50) NULL,
	CONSTRAINT pk_audit_logs_infm PRIMARY KEY (biz_key, audit_logs_seq_no, wsvc_id, lang_cd)
);

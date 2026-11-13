-- ============================================================================
-- V3__winv_notification_read.sql — WeInvoice notification read state (new table)
--
-- TARGET DATABASE: the WeInvoice database (the PostgreSQL database the
-- we-scraping engine writes), reached by the backend through
-- WECMS_WEINVOICE_DB_URL. NOT the WeCMS database.
--
-- Execution: MANUAL (DBA), like V1 / V2 — spring.flyway.enabled=false and
-- nothing applies DDL at startup. Run AFTER V1__winv_schema.sql (V2 is not a
-- prerequisite). The schema name `weinvoice` is the default of
-- WECMS_WEINVOICE_APP_SCHEMA; replace it if an environment uses another name.
-- Must be applied BEFORE the backend that serves /api/v1/weinvoice/notifications
-- is deployed: every notification endpoint reads this table.
--
-- Optional companion script (indexes on the engine tables, existing tables so
-- not in this file):
--   docs/sql/2026/add-notification-time-indexes-2026-11-12.sql
--
-- Related record:
--   docs/features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md
--
-- Privileges: when the backend and the engine use different roles (see
-- docs/sql/2026/grant-weinvoice-schema-access-2026-11-02.sql), the new table
-- needs, after this file:
--   GRANT SELECT, INSERT, UPDATE, DELETE ON weinvoice.winv_ntfc_read TO <app_role>;
-- The engine never reads or writes it. Skip on a single-role setup.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- WINV_NTFC_READ — which notifications of the bell a user has read
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weinvoice.winv_ntfc_read (
	owner_id   varchar(100)  NOT NULL,
	ntfc_key   varchar(160)  NOT NULL,
	read_dtm   varchar(14)   NOT NULL,
	CONSTRAINT winv_ntfc_read_pk PRIMARY KEY (owner_id, ntfc_key)
);

COMMENT ON TABLE  weinvoice.winv_ntfc_read IS 'Read state of the WeInvoice notification bell, per user (WeCMS USER_ID). Notifications themselves are not stored: the backend aggregates them from the engine tables (invoices, invoice_xml, invoice_pdf) and the connection tables on every request.';
COMMENT ON COLUMN weinvoice.winv_ntfc_read.owner_id IS 'WeCMS USER_ID of the reader (same owner as winv_cmpn_infm).';
COMMENT ON COLUMN weinvoice.winv_ntfc_read.ntfc_key IS 'Notification id <TYPE>|<mst or ->|<purchase|sales or ->|<bucket> (bucket = UTC yyyyMMddHH for counts, the source dtm for errors) marked read one by one; or a "read all" marker: * (every MST) or *|<mst> (one MST).';
COMMENT ON COLUMN weinvoice.winv_ntfc_read.read_dtm IS 'When it was marked read, yyyyMMddHHmmss (UTC, database clock). For a marker: every notification whose latest event is at or before this time counts as read.';

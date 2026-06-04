-- ============================================================================
-- V238__notification_schema.sql
--
-- Notification module (slug: notifications) — Phase 1 backend foundation.
-- Refactor-native unified notification store. There is NO legacy table this
-- ports column-for-column: the legacy JEX bell (comm_0192_007) reads a UNION
-- of two heterogeneous stores (BLTB_NOTI_INFM + GENL_NOTI_* with different
-- read-flag columns) plus a mobile-push fan-out. The refactor collapses that
-- into one per-recipient table, per the PO-locked decision recorded in
-- docs/refactor-wecms/features/notifications.md (2026-06-02):
--   * unified single table, one row per recipient (fan-out)
--   * message_code + message_params (jsonb) — text resolved by the frontend
--     i18n layer, NOT stored localized per language
--   * link_target (refactor slug route + params), read_at (NULL = unread)
--   * scoped by (biz_key, recipient_id)
--   * v1 producer = approval-request notifications only
--
-- Deliberate divergences from the workspace Legacy Schema Parity Rule —
-- all justified because this is a new refactor-native table with no DEV
-- counterpart to mirror:
--   1. NOTIFICATION_ID is a global BIGINT IDENTITY surrogate, not a
--      per-tenant MAX(seq)+1 / wb_get_next_val key. The fan-out writes N
--      rows per event from a best-effort REQUIRES_NEW transaction; an
--      IDENTITY column removes the allocation race entirely (a dropped
--      notification on PK collision would be a silent data-loss bug). All
--      reads still scope on (BIZ_KEY, RECIPIENT_ID) so tenant isolation is
--      unaffected by the global key.
--   2. MESSAGE_PARAMS is JSONB — the first jsonb column in the refactor
--      schema. Holds the i18n interpolation params (sender_name, doc_no,
--      title, type) as a flat string map; the frontend renders the visible
--      text from MESSAGE_CODE + these params.
--   3. READ_AT / REG_DTM / MOD_DTM keep the VARCHAR(14) yyyyMMddHHmmss shape
--      used by every other refactor table (memo_infm V202, approval-line V90)
--      so string ORDER BY REG_DTM DESC == chronological and the service
--      stamps them with the same DTM14 formatter the approval fan-out uses.
--
-- Idempotent — CREATE TABLE / INDEX IF NOT EXISTS. Re-running is a no-op.
--
-- Feature spec:   docs/refactor-wecms/features/notifications.md
-- ============================================================================

CREATE TABLE IF NOT EXISTS NOTIFICATION (
    NOTIFICATION_ID  BIGINT        GENERATED ALWAYS AS IDENTITY,
    BIZ_KEY          VARCHAR(20)   NOT NULL,
    RECIPIENT_ID     VARCHAR(100)  NOT NULL,
    -- Producer discriminator. v1 writes only 'APPROVAL_REQUEST'; the column
    -- is wide enough for the later producers (APPROVAL_RESULT / COMMENT /
    -- GENERAL) without a follow-up migration.
    NOTI_TYPE        VARCHAR(30)   NOT NULL,
    -- Referenced source entity id. For APPROVAL_REQUEST this is the
    -- EXPT_RESOL_MSTR.APPR_SEQ_NO the notification points at. Nullable so
    -- future system notifications with no source row can omit it.
    SOURCE_ID        VARCHAR(50),
    -- i18n key the frontend resolves (e.g. 'notification.approval.requested').
    MESSAGE_CODE     VARCHAR(100)  NOT NULL,
    -- Flat string map of interpolation params for MESSAGE_CODE. Keys are
    -- stored verbatim snake_case (Jackson naming strategy does not touch
    -- Map keys) so the wire contract is stable.
    MESSAGE_PARAMS   JSONB,
    -- Refactor slug route (+ query params) the bell navigates to on click.
    LINK_TARGET      VARCHAR(500),
    -- NULL = unread. Set to the read timestamp (yyyyMMddHHmmss) on mark-read.
    READ_AT          VARCHAR(14),
    -- Soft-delete / archive flag. v1 never sets 'Y' (no delete endpoint yet)
    -- but every read filters DEL_YN = 'N' so the Phase-4 archive feature
    -- drops in without a schema change.
    --
    -- VARCHAR(1), NOT CHAR(1): the entity maps DEL_YN as a plain String, which
    -- Hibernate binds to VARCHAR. A CHAR(1)/bpchar column would fail
    -- `ddl-auto: validate` ("found bpchar, expecting varchar") — the exact
    -- trap the project hit in V37→V38. The refactor's YN-normalisation
    -- precedent (V3 / V9 / V18) is VARCHAR(1) for *_YN columns; follow it so
    -- no @JdbcTypeCode(SqlTypes.CHAR) is needed on the field.
    DEL_YN           VARCHAR(1)    NOT NULL DEFAULT 'N',
    -- Audit quad — REG_* = created_by / created_at (the requesting user who
    -- triggered the fan-out); MOD_* updated on mark-read.
    REG_ID           VARCHAR(100)  NOT NULL,
    REG_DTM          VARCHAR(14)   NOT NULL,
    MOD_ID           VARCHAR(100)  NOT NULL,
    MOD_DTM          VARCHAR(14)   NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (NOTIFICATION_ID)
);

-- Unread-count + Unread-tab list: WHERE biz_key=? AND recipient_id=?
-- AND read_at IS NULL AND del_yn='N'. Partial index keeps it tiny — only
-- unread, non-deleted rows are indexed (the hot path the header bell polls).
CREATE INDEX IF NOT EXISTS ix_notification_unread
    ON NOTIFICATION (BIZ_KEY, RECIPIENT_ID)
    WHERE READ_AT IS NULL AND DEL_YN = 'N';

-- All-tab list ordering: WHERE biz_key=? AND recipient_id=? AND del_yn='N'
-- ORDER BY reg_dtm DESC, notification_id DESC. Materialises the sort so the
-- list page avoids a sort step.
CREATE INDEX IF NOT EXISTS ix_notification_recipient_created
    ON NOTIFICATION (BIZ_KEY, RECIPIENT_ID, REG_DTM DESC, NOTIFICATION_ID DESC);

-- Source lookup — find every notification raised for one approval document
-- (used by later phases that update/expire a notification when its source
-- is approved/rejected/withdrawn).
CREATE INDEX IF NOT EXISTS ix_notification_source
    ON NOTIFICATION (BIZ_KEY, SOURCE_ID);

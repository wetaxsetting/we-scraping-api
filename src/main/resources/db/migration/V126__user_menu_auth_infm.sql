-- =============================================================================
-- V126__user_menu_auth_infm.sql
--
-- Settings > Users — Menu Entitlement persistence (refactor slug:
-- user-management). One row per granted (BIZ_KEY, USER_ID, MENU_AUTH_CD)
-- triple recording which menu slugs a user is allowed to access inside
-- a given tenant.
--
-- Storage convention: Y-only. The refactor service uses
-- delete-then-insert on save, and only inserts USE_YN='Y' rows; the
-- absence of a row implies denial. Keeps the table sparse and lets the
-- panel default every menu to "off".
--
-- MENU_AUTH_CD values are kebab-case slugs that mirror the frontend's
-- static `ENTITLEMENT_SECTIONS` tree (e.g. `banking.account_inquiry`,
-- `payment.approval_box`). VARCHAR(100) comfortably fits the longest
-- slug.
--
-- Local note: dev / uat / prod profiles all keep ddl-auto=validate so
-- this migration is the only sanctioned channel for the schema. The
-- earlier attempt to use ddl-auto=update locally tripped an
-- unintended ALTER cascade on the shared dev DB and was reverted.
--
-- Idempotent — CREATE TABLE IF NOT EXISTS.
-- =============================================================================

CREATE TABLE IF NOT EXISTS USER_MENU_AUTH_INFM (
    BIZ_KEY      VARCHAR(20)   NOT NULL,
    USER_ID      VARCHAR(100)  NOT NULL,
    MENU_AUTH_CD VARCHAR(100)  NOT NULL,
    USE_YN       VARCHAR(1)    NOT NULL,
    REG_ID       VARCHAR(100),
    REG_DTM      TIMESTAMPTZ,
    MOD_ID       VARCHAR(100),
    MOD_DTM      TIMESTAMPTZ,
    PRIMARY KEY (BIZ_KEY, USER_ID, MENU_AUTH_CD)
);

-- Per-(tenant, user) lookup index — backs the load() / delete-all on
-- save flow inside MenuEntitlementService. Drops query cost on the
-- PK's two leading columns even though the PK already covers it.
CREATE INDEX IF NOT EXISTS ix_user_menu_auth_biz_user
    ON USER_MENU_AUTH_INFM (BIZ_KEY, USER_ID);

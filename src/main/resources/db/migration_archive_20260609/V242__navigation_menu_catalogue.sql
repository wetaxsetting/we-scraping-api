-- V242 — Navigation menu catalogue (refactor-owned product-tree tables).
--
-- Replaces the lost JexAdmin / Jex Studio MenuManager trees
-- (WE_CMSVN / WE_CMSVN_LUMENS / WE_CMSVN_HAENGSUNG) with tables the refactor
-- owns outright. Backs GET /api/v1/navigation: the per-product menu tree the
-- session's prdt_type selects, intersected with USER_MENU_AUTH_INFM grants.
--
-- These are NEW refactor infrastructure tables (not legacy JEX tables, not in
-- the PROD Active Table List). They are seeded by Flyway (V243) from the
-- JexAdmin export and never mutated at runtime — no audit columns by design.
-- Design: docs/jex/features/menu-product-type-management/
--   backend-navigation-implementation-design.md §5 + implementation-plan.md §4.1.
--
-- Idempotent: CREATE TABLE / INDEX IF NOT EXISTS. Safe to re-run.

-- MENU_ITEM — product-independent node definition (one row per distinct slug).
-- Hierarchy via self-referencing PARENT_ID (null = top menu). ROUTE / PERMISSION_CD
-- are null for pure parents. PERMISSION_CD is the kebab menuAuth grant key (the
-- intersection key against USER_MENU_AUTH_INFM.MENU_AUTH_CD). LEGACY_* are
-- traceability-only (never used for routing or gating).
CREATE TABLE IF NOT EXISTS MENU_ITEM (
    MENU_ITEM_ID    VARCHAR(100) NOT NULL,
    PARENT_ID       VARCHAR(100),
    MENU_DEP        INTEGER      NOT NULL DEFAULT 0,
    ROUTE           VARCHAR(200),
    ICON            VARCHAR(100),
    SHOW_YN         VARCHAR(1)   NOT NULL DEFAULT 'Y',
    PERMISSION_CD   VARCHAR(100),
    LEGACY_MNU_ID   VARCHAR(100),
    LEGACY_WSVC_ID  VARCHAR(100),
    CONSTRAINT PK_MENU_ITEM PRIMARY KEY (MENU_ITEM_ID)
);

CREATE INDEX IF NOT EXISTS IX_MENU_ITEM_PARENT
    ON MENU_ITEM (PARENT_ID);

-- PRODUCT_MENU — per-product membership + ordering (the F/M/H catalogue
-- difference: H omits the Add-on subtree + Daily Cash / standard Daily Financial
-- / Cash Flow and includes the HS Daily Financial; M = F structurally).
-- Replaces legacy MENU_AUTH_ADMN_SYS. DISP_ORD = legacy MNU_SEQ for that product.
CREATE TABLE IF NOT EXISTS PRODUCT_MENU (
    PRODUCT_TYPE    VARCHAR(1)   NOT NULL,
    MENU_ITEM_ID    VARCHAR(100) NOT NULL,
    DISP_ORD        INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT PK_PRODUCT_MENU PRIMARY KEY (PRODUCT_TYPE, MENU_ITEM_ID)
);

CREATE INDEX IF NOT EXISTS IX_PRODUCT_MENU_PRODUCT
    ON PRODUCT_MENU (PRODUCT_TYPE);

-- MENU_ITEM_TRANSLATION — per-product per-language label. Keyed by
-- (PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD) because M's VI/KO labels diverge from F
-- for the same node (verified in the export); a (MENU_ITEM_ID, LANG_CD)-only key
-- could not represent that. LANG_CD is EN / KO / VI (KM falls back to EN at read).
CREATE TABLE IF NOT EXISTS MENU_ITEM_TRANSLATION (
    PRODUCT_TYPE    VARCHAR(1)   NOT NULL,
    MENU_ITEM_ID    VARCHAR(100) NOT NULL,
    LANG_CD         VARCHAR(2)   NOT NULL,
    LABEL           VARCHAR(200) NOT NULL,
    CONSTRAINT PK_MENU_ITEM_TRANSLATION PRIMARY KEY (PRODUCT_TYPE, MENU_ITEM_ID, LANG_CD)
);

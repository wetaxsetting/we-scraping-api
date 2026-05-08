-- V30 — Sprint 1 stabilization: screen-option (View Setting) tables.
--
-- Mirrors legacy SYS_SCR_OPTION_WECMS / USER_SCR_OPTION_WECMS structure.
-- The two tables back the View Setting popup (Figma 100:13382 / legacy
-- comm_0108_001_*): per-page column visibility + ordering, with system
-- defaults overlaid by per-user overrides.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS, INSERT … ON CONFLICT DO NOTHING.
-- Safe to re-run.
--
-- Sprint 1 scope intentionally maps the column set the View Setting flow
-- actually consumes (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN,
-- FIX_YN). Legacy "size / disp_yn / dym_area_id" columns are added for
-- shape parity but unused by Spring Boot today — extend the entity when
-- a future feature reads them.

CREATE TABLE IF NOT EXISTS SYS_SCR_OPTION_WECMS (
    WEB_SVC_ID    VARCHAR(50)  NOT NULL,
    FIELD_ID      VARCHAR(100) NOT NULL,
    FIELD_NM      VARCHAR(200),
    DISP_ORD      INTEGER      NOT NULL DEFAULT 0,
    USE_YN        VARCHAR(1)   NOT NULL DEFAULT 'Y',
    FIX_YN        VARCHAR(1)   NOT NULL DEFAULT 'N',
    DISP_YN       VARCHAR(1)   DEFAULT 'Y',
    "SIZE"        INTEGER,
    DYM_AREA_ID   VARCHAR(50),
    REG_ID        VARCHAR(100),
    REG_DTM       TIMESTAMPTZ,
    MOD_ID        VARCHAR(100),
    MOD_DTM       TIMESTAMPTZ,
    CONSTRAINT PK_SYS_SCR_OPTION_WECMS PRIMARY KEY (WEB_SVC_ID, FIELD_ID)
);

CREATE TABLE IF NOT EXISTS USER_SCR_OPTION_WECMS (
    BIZ_KEY       VARCHAR(20)  NOT NULL,
    USER_ID       VARCHAR(100) NOT NULL,
    WEB_SVC_ID    VARCHAR(50)  NOT NULL,
    FIELD_ID      VARCHAR(100) NOT NULL,
    FIELD_NM      VARCHAR(200),
    DISP_ORD      INTEGER      NOT NULL DEFAULT 0,
    USE_YN        VARCHAR(1)   NOT NULL DEFAULT 'Y',
    DISP_YN       VARCHAR(1)   DEFAULT 'Y',
    "SIZE"        INTEGER,
    DYM_AREA_ID   VARCHAR(50),
    REG_ID        VARCHAR(100),
    REG_DTM       TIMESTAMPTZ,
    MOD_ID        VARCHAR(100),
    MOD_DTM       TIMESTAMPTZ,
    CONSTRAINT PK_USER_SCR_OPTION_WECMS PRIMARY KEY (BIZ_KEY, USER_ID, WEB_SVC_ID, FIELD_ID)
);

CREATE INDEX IF NOT EXISTS IX_USER_SCR_OPTION_WECMS_USER
    ON USER_SCR_OPTION_WECMS (BIZ_KEY, USER_ID);

-- Seed system defaults for Account Inquiry VND (legacy WEB_SVC_ID = fnsh_0001_001).
-- Order + visibility match the live system: Bank, Account Nickname, Balance,
-- Available Balance, VAS Account (hidden by default), Last Update.
-- Bank stays FIX_YN='Y' so the user can never reorder/hide the primary
-- identifier column (legacy parity).
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, FIX_YN, DISP_YN)
VALUES
    ('fnsh_0001_001', 'BANK',              'Bank',              1, 'Y', 'Y', 'Y'),
    ('fnsh_0001_001', 'ACCOUNT_NICKNAME',  'Account Nickname',  2, 'Y', 'N', 'Y'),
    ('fnsh_0001_001', 'BALANCE',           'Balance',           3, 'Y', 'N', 'Y'),
    ('fnsh_0001_001', 'AVAILABLE_BALANCE', 'Available Balance', 4, 'Y', 'N', 'Y'),
    ('fnsh_0001_001', 'VAS_ACCOUNT',       'VAS Account',       5, 'N', 'N', 'Y'),
    ('fnsh_0001_001', 'LAST_UPDATE',       'Last Update',       6, 'Y', 'N', 'Y')
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

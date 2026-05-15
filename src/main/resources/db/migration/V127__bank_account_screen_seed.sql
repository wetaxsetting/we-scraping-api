-- =====================================================================
-- V127 — Settings > Bank Account screen seeds (View Setting registry).
--
-- Settings > Bank Account is the live JEX list at
-- /settings → Bank Account (screenshot 2026-05-14) that lists every
-- registered ACCT_MSTR row for the tenant with bank-name link,
-- nickname, description, account type, maturity date, VAS code, VAS
-- account name, view/withdrawal permission links and audit-log link.
--
-- Refactor slug = 'bank-account' (matches Batch A's existing
-- SettingsBankAccountService.WSVC_ID + the controller route segment
-- /api/v1/settings/bank-account). Per workspace CLAUDE.md
-- §"Refactor identifier naming → slugs, not WSVC ids". The legacy
-- JEX entry point is comm_0220_* (firm-banking-related popups) and
-- the comm_0218_nxisas modal chain (account registration via
-- scraping); neither is reused as a screenId — both are JSP-
-- architecture artifacts.
--
-- This migration ships:
--   1. ACCT_AUTH_INFO table (per-user-per-account permissions) —
--      first time the table is provisioned in the refactor DB. Column-
--      for-column parity with DEV per "Legacy Schema Parity Rule".
--   2. Two performance indexes on existing + new tables that support
--      the per-row sub-selects the list endpoint runs.
--   3. View Setting seed rows for screenId 'settings-bank-account'
--      / dymAreaId 'default'. Ten columns — one CHECKBOX special
--      (DISP_ORD=0), 7 user-toggleable columns, no ETC special
--      (no row-action column in this table; row interactions go
--      through the per-cell links).
--
-- ACCT_MSTR / NAPAS_BANK_INFO / BUSI_VAS_ACCT_INFM /
-- AUDIT_LOGS_INFM tables already exist (V13, V63, etc.).
--
-- Idempotent.
-- =====================================================================

-- ----------------------------------------------------------------------
-- (1) ACCT_AUTH_INFO — per-user-per-account permission row.
-- ----------------------------------------------------------------------
-- Column-for-column parity with JEX DEV (verified via the postgres
-- MCP — table has 9 columns + composite PK).
--
-- PK = (BIZ_KEY, USER_ID, BANK_CD, ACCT_NO, AUTH_TYPE) — see legacy
-- AcctAuthInfoPk.java line 19-29. AUTH_TYPE in the key means the
-- same user can hold both VIEW ('V') and WITHDRAW ('W') rows for
-- one account independently. USE_YN='N' soft-revokes without
-- losing audit history.

CREATE TABLE IF NOT EXISTS ACCT_AUTH_INFO (
    BIZ_KEY    VARCHAR(20)  NOT NULL,
    USER_ID    VARCHAR(100) NOT NULL,
    BANK_CD    VARCHAR(20)  NOT NULL,
    ACCT_NO    VARCHAR(100) NOT NULL,
    AUTH_TYPE  VARCHAR(1)   NOT NULL,  -- 'V' view | 'W' withdraw | 'F' full
    USE_YN     VARCHAR(1)   NOT NULL DEFAULT 'Y',
    REG_ID     VARCHAR(100) NOT NULL,
    REG_DTM    VARCHAR(14)  NOT NULL,
    MOD_ID     VARCHAR(100),
    MOD_DTM    VARCHAR(14),
    CONSTRAINT PK_ACCT_AUTH_INFO PRIMARY KEY (BIZ_KEY, USER_ID, BANK_CD, ACCT_NO, AUTH_TYPE),
    CONSTRAINT CHK_ACCT_AUTH_INFO_AUTH_TYPE CHECK (AUTH_TYPE IN ('V','W','F')),
    CONSTRAINT CHK_ACCT_AUTH_INFO_USE_YN    CHECK (USE_YN    IN ('Y','N'))
);

-- ----------------------------------------------------------------------
-- (2) Performance indexes
-- ----------------------------------------------------------------------

-- ACCT_AUTH_INFO: the list endpoint runs two per-row correlated
-- sub-selects (count + first-name) filtered by
--   (BIZ_KEY, BANK_CD, ACCT_NO, AUTH_TYPE) AND USE_YN='Y'.
-- The PK index already covers (BIZ_KEY, USER_ID, BANK_CD, ACCT_NO,
-- AUTH_TYPE) but its leading column is USER_ID — wrong order for
-- this access pattern. Add a dedicated index keyed by account.
CREATE INDEX IF NOT EXISTS ix_acct_auth_info__acct
    ON ACCT_AUTH_INFO (BIZ_KEY, BANK_CD, ACCT_NO, AUTH_TYPE);

-- AUDIT_LOGS_INFM: the list endpoint reads the latest audit row per
-- account via
--   WHERE BIZ_KEY=? AND WSVC_ID='settings-bank-account'
--     AND REF_NO1=? AND REF_NO2=?
--   ORDER BY REG_DTM DESC LIMIT 1.
-- V63 already adds an index keyed by (BIZ_KEY, REF_NO, REF_NO1,
-- REF_NO2, WSVC_ID, LANG_CD) for the detail-audit lookup; that index
-- can serve this read too, but a tighter wsvc-first index keeps the
-- per-row sub-select cheap as audit volume grows.
CREATE INDEX IF NOT EXISTS ix_audit_logs_infm__bank_acct
    ON AUDIT_LOGS_INFM (BIZ_KEY, WSVC_ID, REF_NO1, REF_NO2, REG_DTM DESC);

-- ----------------------------------------------------------------------
-- (2) View Setting seed — screenId 'settings-bank-account' / 'default'
-- ----------------------------------------------------------------------
--
-- Column set mirrors the live screenshot:
--   DISP_ORD 0 : CHECKBOX        (special — row selection; locked)
--   DISP_ORD 1 : BANK             (locked — anchor column; sortable)
--   DISP_ORD 2 : ACCOUNT_NICKNAME (sortable)
--   DISP_ORD 3 : DESCRIPTION      (sortable)
--   DISP_ORD 4 : ACCOUNT_TYPE     (sortable)
--   DISP_ORD 5 : MATURITY_DATE    (sortable; null for current accounts)
--   DISP_ORD 6 : VAS_CODE         (sortable)
--   DISP_ORD 7 : VAS_NAME         (NOT sortable — projected from sibling table)
--   DISP_ORD 8 : VIEW_PERMISSION  (NOT sortable — derived count + label)
--   DISP_ORD 9 : WITHDRAWAL_PERMISSION (NOT sortable — derived)
--   DISP_ORD 10: AUDIT_LOGS       (NOT sortable — derived)
--
-- SORT_YN='N' for the 4 derived columns mirrors the rule in
-- wecms-api-main/CLAUDE.md §"Common table sort": every SORT_YN='Y'
-- column MUST have a backing whitelist enum constant in
-- BankAccountSortField; derived columns intentionally do not.

INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, FIELD_ID, DYM_AREA_ID, FIELD_NM, DISP_ORD,
     USE_YN, DISP_YN, FIX_YN, SORT_YN, SIZE)
VALUES
    -- Special: row-selection checkbox column. DISP_ORD=0 keeps it
    -- pinned left of every user column. USE_YN='Y' is locked
    -- server-side by ScreenOptionService.SPECIAL_FIELD_IDS.
    ('bank-account', 'CHECKBOX',             'default', 'Select row',           0,  'Y', 'Y', 'Y', 'N', '38px'),

    -- Anchor: bank name + account number sub-line. FIX_YN='Y' locks
    -- it as the always-visible first content column.
    ('bank-account', 'BANK',                 'default', 'Accounts',             1,  'Y', 'Y', 'Y', 'Y', '200px'),

    -- User-toggleable columns.
    ('bank-account', 'ACCOUNT_NICKNAME',     'default', 'Account Nickname',     2,  'Y', 'Y', 'N', 'Y', '180px'),
    ('bank-account', 'DESCRIPTION',          'default', 'Description',          3,  'Y', 'Y', 'N', 'Y', '200px'),
    ('bank-account', 'ACCOUNT_TYPE',         'default', 'Account Type',         4,  'Y', 'Y', 'N', 'Y', '140px'),
    ('bank-account', 'MATURITY_DATE',        'default', 'Maturity Date',        5,  'Y', 'Y', 'N', 'Y', '140px'),
    ('bank-account', 'VAS_CODE',             'default', 'VAS Code',             6,  'Y', 'Y', 'N', 'Y', '120px'),

    -- VAS Account Name projects from BUSI_VAS_ACCT_INFM via the
    -- VAS_ACCT_SEQ_NO foreign key. No backing entity field on
    -- AcctMstr → SORT_YN='N'.
    ('bank-account', 'VAS_NAME',             'default', 'VAS',                  7,  'Y', 'Y', 'N', 'N', '200px'),

    -- Derived columns — composed server-side from ACCT_AUTH_INFO
    -- aggregates ("<first user> 외 N명") and the latest audit row.
    -- SORT_YN='N' for all three: no backing entity property.
    ('bank-account', 'VIEW_PERMISSION',      'default', 'View Permission',      8,  'Y', 'Y', 'N', 'N', '180px'),
    ('bank-account', 'WITHDRAWAL_PERMISSION','default', 'Withdrawal Permission',9,  'Y', 'Y', 'N', 'N', '180px'),
    ('bank-account', 'AUDIT_LOGS',           'default', 'Audit Logs',          10,  'Y', 'Y', 'N', 'N', '180px')
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;

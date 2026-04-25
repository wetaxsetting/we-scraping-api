-- =======================================================================
-- V10__demo_legacy_hash_user.sql
--
-- Inserts a user whose USER_PW is a raw legacy JEX SHA-256 base64 hash
-- (the output of wecmsvndevdb's public.hash()) so we can verify the
-- hybrid password encoder's legacy-verify + upgrade-on-match path.
--
-- Hash below is the Postgres legacy hash of 'Password!1':
--   SELECT hash('Password!1') = vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=
--
-- After first successful login, AuthService upgrades this row's USER_PW
-- to BCrypt; this migration writes the legacy form only so the upgrade
-- path is demonstrable.
-- =======================================================================

INSERT INTO USER_INFM (
    USER_ID, USER_NM, USER_PW, ACTV_YN, LOCK_YN, DEL_YN, REG_ID, MOD_ID
) VALUES (
    'legacyuser',
    'Legacy Hash Demo',
    'vm/e2euhU9d0JFSQ+PQSDOviptOlRnYD7KM0PekNYnU=',
    'Y', 'N', 'N', 'SYSTEM', 'SYSTEM'
)
ON CONFLICT (USER_ID) DO NOTHING;

INSERT INTO USER_BUSI_CONN_INFM (
    USER_ID, BIZ_KEY, DEL_YN, REG_ID, MOD_ID
) VALUES (
    'legacyuser', 'VN_VNM0004_000001', 'N', 'SYSTEM', 'SYSTEM'
)
ON CONFLICT (USER_ID, BIZ_KEY) DO NOTHING;

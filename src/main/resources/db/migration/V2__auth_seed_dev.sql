-- ============================================================================
-- V2__auth_seed_dev.sql
-- Seeds a single company + two users for local R&D.
--
-- USER_PW is intentionally left as the sentinel '__BCRYPT_PENDING__'.
-- On application startup (local profile), the DataSeeder ApplicationRunner
-- detects the sentinel and replaces it with a real BCrypt hash of
-- "Password!1" via the configured PasswordEncoder. This keeps the migration
-- free of hard-coded hashes and keeps the encoder as the single source of truth.
--
-- Manual test credentials (after first startup):
--   USER_ID  = testuser     USER_PW = Password!1   (active, unlocked)
--   USER_ID  = lockeduser   USER_PW = Password!1   (LOCK_YN = 'Y' → WBE1011)
-- ============================================================================

INSERT INTO BUSI_INFM (BIZ_KEY, BUSI_NM, NTNL_CD, BUSI_STTS, REG_USER, MOD_USER)
VALUES ('VN_VNM0004_000001', 'WeCMS R&D Company', 'VN', 'N', 'SYSTEM', 'SYSTEM');

INSERT INTO USER_INFM (USER_ID, USER_NM, USER_PW, EML, ACTV_YN, LOCK_YN, DEL_YN, LANG_CD, REG_USER, MOD_USER)
VALUES
    ('testuser',   'Test User',   '__BCRYPT_PENDING__', 'testuser@wecms.local', 'Y', 'N', 'N', 'EN', 'SYSTEM', 'SYSTEM'),
    ('lockeduser', 'Locked User', '__BCRYPT_PENDING__', 'locked@wecms.local',   'Y', 'Y', 'N', 'EN', 'SYSTEM', 'SYSTEM');

INSERT INTO USER_BUSI_CONN_INFM (USER_ID, BIZ_KEY, EMPL_NO, DEL_YN, REG_USER, MOD_USER)
VALUES
    ('testuser',   'VN_VNM0004_000001', 'EMP0001', 'N', 'SYSTEM', 'SYSTEM'),
    ('lockeduser', 'VN_VNM0004_000001', 'EMP0002', 'N', 'SYSTEM', 'SYSTEM');

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

-- ----------------------------------------------------------------------------
-- Schema-aware INSERTs.
--
-- V7__align_schema_to_legacy_exact.sql renames:
--   BUSI_INFM.BUSI_NM    -> CMPN_NM
--   *.REG_USER           -> REG_ID
--   *.MOD_USER           -> MOD_ID
--
-- On a fresh DB applied in order (V1 -> V2 -> ... -> V7) V2 sees the pre-V7
-- shape. On the shared dev DB V2's history row was cleared at some point and
-- V2 is now being re-applied against the POST-V7 shape. The DO blocks below
-- detect which column set exists and INSERT accordingly so this seed runs
-- correctly in either timeline. ON CONFLICT DO NOTHING keeps re-runs idempotent.
-- ----------------------------------------------------------------------------

-- Helper: column existence is checked via pg_attribute against the EXACT
-- relation the INSERT will resolve via search_path (to_regclass). This
-- avoids false-negatives when the table lives outside the 'public' schema
-- (shared dev DBs, custom schemas, etc.).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = to_regclass('busi_infm')
          AND attname  = 'cmpn_nm'
          AND NOT attisdropped
          AND attnum > 0
    ) THEN
        INSERT INTO BUSI_INFM (BIZ_KEY, CMPN_NM, NTNL_CD, BUSI_STTS, REG_ID, MOD_ID)
        VALUES ('VN_VNM0004_000001', 'WeCMS R&D Company', 'VN', 'N', 'SYSTEM', 'SYSTEM')
        ON CONFLICT (BIZ_KEY) DO NOTHING;
    ELSE
        INSERT INTO BUSI_INFM (BIZ_KEY, BUSI_NM, NTNL_CD, BUSI_STTS, REG_USER, MOD_USER)
        VALUES ('VN_VNM0004_000001', 'WeCMS R&D Company', 'VN', 'N', 'SYSTEM', 'SYSTEM')
        ON CONFLICT (BIZ_KEY) DO NOTHING;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = to_regclass('user_infm')
          AND attname  = 'reg_id'
          AND NOT attisdropped
          AND attnum > 0
    ) THEN
        -- LANG_CD intentionally omitted: V7 DROPs it from USER_INFM
        -- (LANG_CD is derived from the Accept-Language header at request time).
        INSERT INTO USER_INFM (USER_ID, USER_NM, USER_PW, EML, ACTV_YN, LOCK_YN, DEL_YN, REG_ID, MOD_ID)
        VALUES
            ('testuser',   'Test User',   '__BCRYPT_PENDING__', 'testuser@wecms.local', 'Y', 'N', 'N', 'SYSTEM', 'SYSTEM'),
            ('lockeduser', 'Locked User', '__BCRYPT_PENDING__', 'locked@wecms.local',   'Y', 'Y', 'N', 'SYSTEM', 'SYSTEM')
        ON CONFLICT (USER_ID) DO NOTHING;
    ELSE
        INSERT INTO USER_INFM (USER_ID, USER_NM, USER_PW, EML, ACTV_YN, LOCK_YN, DEL_YN, LANG_CD, REG_USER, MOD_USER)
        VALUES
            ('testuser',   'Test User',   '__BCRYPT_PENDING__', 'testuser@wecms.local', 'Y', 'N', 'N', 'EN', 'SYSTEM', 'SYSTEM'),
            ('lockeduser', 'Locked User', '__BCRYPT_PENDING__', 'locked@wecms.local',   'Y', 'Y', 'N', 'EN', 'SYSTEM', 'SYSTEM')
        ON CONFLICT (USER_ID) DO NOTHING;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = to_regclass('user_busi_conn_infm')
          AND attname  = 'reg_id'
          AND NOT attisdropped
          AND attnum > 0
    ) THEN
        INSERT INTO USER_BUSI_CONN_INFM (USER_ID, BIZ_KEY, EMPL_NO, DEL_YN, REG_ID, MOD_ID)
        VALUES
            ('testuser',   'VN_VNM0004_000001', 'EMP0001', 'N', 'SYSTEM', 'SYSTEM'),
            ('lockeduser', 'VN_VNM0004_000001', 'EMP0002', 'N', 'SYSTEM', 'SYSTEM')
        ON CONFLICT (USER_ID, BIZ_KEY) DO NOTHING;
    ELSE
        INSERT INTO USER_BUSI_CONN_INFM (USER_ID, BIZ_KEY, EMPL_NO, DEL_YN, REG_USER, MOD_USER)
        VALUES
            ('testuser',   'VN_VNM0004_000001', 'EMP0001', 'N', 'SYSTEM', 'SYSTEM'),
            ('lockeduser', 'VN_VNM0004_000001', 'EMP0002', 'N', 'SYSTEM', 'SYSTEM')
        ON CONFLICT (USER_ID, BIZ_KEY) DO NOTHING;
    END IF;
END $$;

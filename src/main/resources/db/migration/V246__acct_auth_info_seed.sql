-- V246 — Account Inquiry per-user authority seed (HR-3)
--
-- Provisions View authority (USE_YN='Y', AUTH_TYPE='V') for every active
-- ACCT_MSTR account under the R&D tenant VN_VNM0004_000001 to every user
-- that already has an active USER_BUSI_CONN_INFM link to that tenant.
--
-- Why provision-for-all rather than copy DEV rows:
--   * The DEV acct_auth_info rows reference DEV user_ids / accounts that
--     do not all exist locally — copying them would violate the new
--     FK to ACCT_MSTR. This seed instead derives grants from the LOCAL
--     ACCT_MSTR x USER_BUSI_CONN_INFM, so every row is FK-safe.
--   * After V245 the Account Inquiry list filters non-admin users to
--     their granted accounts. Without a grant a non-admin local login
--     (DataSeeder activates every imported user with Password!1) would
--     suddenly see zero accounts. Granting every existing tenant user
--     keeps current logins working while the enforcement logic in
--     AcctMstrRepository is live for any FUTURE user added without a
--     grant (and for admins, who bypass the filter regardless).
--   * Row count is bounded by (active 000001 accounts) x (000001 users)
--     for a single R&D tenant — this is local authority provisioning,
--     not a DEV data copy, so the 30-row-per-table DEV-copy guideline
--     does not apply.
--
-- REG_ID = the granted user (legacy self-grant convention); REG_DTM is a
-- DB-side compact timestamp. Idempotent via ON CONFLICT DO NOTHING on the
-- 5-column PK.

INSERT INTO ACCT_AUTH_INFO (BIZ_KEY, BANK_CD, ACCT_NO, USER_ID, USE_YN, AUTH_TYPE, REG_ID, REG_DTM)
SELECT m.BIZ_KEY,
       m.BANK_CD,
       m.ACCT_NO,
       c.USER_ID,
       'Y',
       'V',
       c.USER_ID,
       to_char(CURRENT_TIMESTAMP, 'YYYYMMDDHH24MISS')
  FROM ACCT_MSTR m
  JOIN USER_BUSI_CONN_INFM c
    ON c.BIZ_KEY = m.BIZ_KEY
   AND c.DEL_YN  = 'N'
 WHERE m.BIZ_KEY = 'VN_VNM0004_000001'
   AND m.ACTV_YN = 'Y'
ON CONFLICT (BIZ_KEY, BANK_CD, ACCT_NO, USER_ID, AUTH_TYPE) DO NOTHING;

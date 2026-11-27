-- WeCMS Manual Database Change
-- Date: 2026-11-27
-- Related record: docs/bugs/weinvoice-tax24-lookup-url-fix-2026-11-27.md
-- Type: DML
-- Execution: MANUAL
-- Target environments: the WeInvoice database (the we-scraping engine database) of each environment
--                      where WeInvoice is enabled. NOT the WeCMS database.
-- Executor: DBA OR DEV TEAM - environment dependent
-- Transaction requirement: one transaction (BEGIN ... COMMIT below). Re-runnable: the UPDATE only sets
--                          fixed values on one row.
-- Rollback: section 4 restores the three values the 2026-11-02 seed wrote.
--
-- Tax24 (MST 0102454468): the seed pointed the lookup link at the product marketing page. The public
-- verify page is /thuedientu/xac-minh-hoa-don ("Mã xác minh hóa đơn" = XML TTKhac "Mã Kiểm tra" + captcha).

-- 1. PRE-CHECK
-- Expected: 1 row, lkup_url = https://tax24.com.vn/thuedientu/san-pham-dich-vu/hoa-don-dien-tu
SELECT prvd_mst, web_url, lkup_url, cptch_url, mod_id, mod_dtm
  FROM weinvoice.winv_prvd_infm
 WHERE prvd_mst = '0102454468';

-- 2. CHANGE
BEGIN;

UPDATE weinvoice.winv_prvd_infm
   SET web_url   = 'https://tax24.com.vn/thuedientu/trang-chu',
       lkup_url  = 'https://tax24.com.vn/thuedientu/xac-minh-hoa-don',
       cptch_url = 'https://tax24.com.vn/thuedientu/servlet/ImageServlet',
       mod_id    = 'FIX_TAX24_LKUP_20261127',
       mod_dtm   = to_char(now() AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYYMMDDHH24MISS')
 WHERE prvd_mst = '0102454468';
-- Expected: UPDATE 1

COMMIT;

-- 3. POST-CHECK
-- Expected: 1 row, lkup_url = https://tax24.com.vn/thuedientu/xac-minh-hoa-don, mod_id = FIX_TAX24_LKUP_20261127
SELECT prvd_mst, web_url, lkup_url, cptch_url, mod_id, mod_dtm
  FROM weinvoice.winv_prvd_infm
 WHERE prvd_mst = '0102454468';

-- 4. ROLLBACK / RECOVERY NOTES
-- Restores the values of seed-weinvoice-provider-catalog-2026-11-02.sql (no data is lost by this change).
-- UPDATE weinvoice.winv_prvd_infm
--    SET web_url   = 'https://tax24.com.vn/thuedientu/san-pham-dich-vu/hoa-don-dien-tu.',
--        lkup_url  = 'https://tax24.com.vn/thuedientu/san-pham-dich-vu/hoa-don-dien-tu',
--        cptch_url = 'https://tax24.com.vn/einvoice/servlet/ImageServlet',
--        mod_id    = 'SEED_PROVIDER_20261102',
--        mod_dtm   = '20261102000000'
--  WHERE prvd_mst = '0102454468';

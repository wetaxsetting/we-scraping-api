# WeInvoice — Tax24 Lookup Link Points to a Product Page — Fix

**Date:** 2026-11-27
**Type:** BUG FIX
**Current Work Classification:** BUG
**Status:** READY FOR DBA
**Repository:** we-scraping-api
**Approval Status:** APPROVED (user, 2026-11-27: "Update link")
**Implementation Status:** COMPLETE (script prepared, not executed)
**Test Status:** PARTIAL (script checked with EXPLAIN; execution pending)

Web counterpart: `we-scraping-web/docs/bugs/weinvoice-tax24-lookup-url-fix-2026-11-27.md`.

## Problem

The provider catalogue row for Tax24 (MST 0102454468, "Công ty Cổ phần Công nghệ thông tin Đông Nam Á") has
`lkup_url = https://tax24.com.vn/thuedientu/san-pham-dich-vu/hoa-don-dien-tu`. That page is the marketing page of the
Tax24 E-invoice product — no lookup form. Tax24 XML carries no link of its own, so the "Tra cứu" button for every Tax24
invoice (49 in the engine DB, 3 sellers, 2026-11-27) opens that marketing page.

## Current Behavior (verified 2026-11-27)

- `weinvoice.winv_prvd_infm` (read-only query): `lkup_url` as above, `web_url` the same with a trailing `.`,
  `cptch_url = https://tax24.com.vn/einvoice/servlet/ImageServlet` (the captcha of the issuer log-in portal).
- Source of that row: `docs/sql/2026/seed-weinvoice-provider-catalog-2026-11-02.sql` line 55 (copied from UI Spec v1.0
  `lib/providers.ts`).

## Expected Behavior

`lkup_url = https://tax24.com.vn/thuedientu/xac-minh-hoa-don` — the public "Xác minh hóa đơn" page (menu "Xác minh").
Read in the built-in browser 2026-11-27: form `formXacMinh` → `POST /thuedientu/xac-minh-hoa-don` with
`maKTra` ("Mã xác minh hóa đơn") + `_verifyCode` ("Mã bảo mật", 180x50 image from `/thuedientu/servlet/ImageServlet`);
the result offers `thaoTac('view', …)` / `downloadXml(…)`. `maKTra` is the TTKhac field "Mã Kiểm tra" of Tax24 XML,
which the engine stores as `invoice_xml.ma_tra_cuu` since engine commit `a54ab55` (49/49 Tax24 invoices have it).
`cptch_url` → `https://tax24.com.vn/thuedientu/servlet/ImageServlet`; `web_url` → `https://tax24.com.vn/thuedientu/trang-chu`.

## Scope

- Manual DBA script `docs/sql/2026/update-tax24-lookup-url-2026-11-27.sql` (one row).
- The seed script line 55, so a fresh environment gets the right row.

## Out of Scope

Automatic PDF/XML fetch from the Tax24 verify page (needs the image captcha; not requested). No Java change: the API
already serves `winv_prvd_infm.lkup_url` as the provider link.

## Database Impact

MANUAL DBA SCRIPT — DML, `UPDATE weinvoice.winv_prvd_infm … WHERE prvd_mst = '0102454468'` (1 row). Not executed by
tooling.

## Root Cause

The catalogue was generated from the UI Spec provider list, which recorded Tax24's product page as its lookup URL.

## Implementation

- [update-tax24-lookup-url-2026-11-27.sql](../sql/2026/update-tax24-lookup-url-2026-11-27.sql): pre-check → `UPDATE`
  of `web_url` / `lkup_url` / `cptch_url` (+ `mod_id = FIX_TAX24_LKUP_20261127`) → post-check → rollback to the seed values.
- [seed-weinvoice-provider-catalog-2026-11-02.sql](../sql/2026/seed-weinvoice-provider-catalog-2026-11-02.sql): Tax24 row
  corrected + header note, so a new environment is seeded right.

## Files Changed

- `docs/sql/2026/update-tax24-lookup-url-2026-11-27.sql` (new)
- `docs/sql/2026/seed-weinvoice-provider-catalog-2026-11-02.sql` (Tax24 row, header note)
- this record, the test-result record

## API Impact

None in code. `GET /providers` and the invoice "Tra cứu" link return the new `lkup_url` once the script has run.

## UI Impact

The "Tra cứu" button of Tax24 invoices opens the verify page (user types the "Mã Kiểm tra" shown on the invoice and the
captcha).

## Database Execution Status

| Environment | Required | Executor | Status | Executed Date | Verification |
|---|---|---|---|---|---|
| Shared engine DB (SIT) | Yes | DBA OR DEV TEAM | PENDING | - | - |

## Verification

- Read-only: current row matches the bug; column types fit; `EXPLAIN` of the `UPDATE` plans 1 row.
- Not executed (manual DBA step).

## Test Evidence

Test result:
[WeInvoice Tax24 Lookup Link (API / DB) — Test Result](../testing/results/weinvoice-tax24-lookup-url-test-result-2026-11-27.md)

Final test result: PARTIAL · Regression coverage: data row pinned by the script's pre/post-check; web copy has a test.

## Result

Script ready; status READY FOR DBA.

## Known Limitations / Follow-up

- Automatic PDF/XML fetch from the Tax24 verify page would need the image captcha — not done.
- A real lookup with a real code has not been tried (captcha typed by a person).

# WeInvoice Tax24 Lookup Link (API / DB) — Test Result

**Date:** 2026-11-27
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** we-scraping-api
**Related Record:** [WeInvoice — Tax24 Lookup Link Points to a Product Page — Fix](../../bugs/weinvoice-tax24-lookup-url-fix-2026-11-27.md)

## Scope

Manual DBA script `docs/sql/2026/update-tax24-lookup-url-2026-11-27.sql` and the corrected row in
`docs/sql/2026/seed-weinvoice-provider-catalog-2026-11-02.sql`. No Java change.

## Environment

LOCAL tooling, read-only connection to the shared engine DB (schema `weinvoice`).

## Code / Build Under Test

Branch: `sit` · Commit: NOT COMMITTED.

## Database Verification

| Check | Result | Evidence |
|---|---|---|
| Current row (read-only `SELECT`) | PASS | `lkup_url` = product page, `cptch_url` = `/einvoice/servlet/ImageServlet` — matches the bug |
| Column types fit the new values | PASS | `web_url` / `lkup_url` / `cptch_url` varchar(500), `mod_id` varchar(100), `mod_dtm` varchar(14) (`YYYYMMDDHH24MISS` = 14) |
| `EXPLAIN` of the script's `UPDATE` (plans, does not execute) | PASS | `Update on winv_prvd_infm` → 1 row estimated |
| Script executed | NOT RUN | manual DBA step — not run by tooling |

## Page Verification

Tax24 verify page read in the built-in browser 2026-11-27: form `POST /thuedientu/xac-minh-hoa-don`, fields `maKTra` +
`_verifyCode`, captcha `/thuedientu/servlet/ImageServlet` (180x50). The old URL renders the product marketing text with
no form. A real lookup was NOT RUN (needs the captcha, typed by a person).

## Automated Tests

NOT APPLICABLE — no backend code changed; the API serves `winv_prvd_infm.lkup_url` as stored.

## Regression Tests

Did a regression test cover the original defect? **NO** on this side — the defect is one data row; the script's
pre-check / post-check pin it. The web catalogue copy has a regression test (web test-result record).

## Final Result

PARTIAL — script prepared and checked; execution pending.

## Recommendation

READY FOR DBA

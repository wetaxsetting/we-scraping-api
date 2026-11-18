# WeInvoice GDT PDF Copy — Test Result

**Date:** 2026-11-10
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api
**Related Record:** [weinvoice-gdt-pdf-fallback-2026-11-10.md](../../issues/weinvoice-gdt-pdf-fallback-2026-11-10.md)

## Environment

LOCAL (developer laptop). Code NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*"` | PASS | 105 tests, 0 failures (`InvoiceFileServiceTest` 16, 5 new) |
| engine `pytest tests/test_gdt_pdf.py` | PASS | 8 passed, incl. real Chromium render |
| engine `pytest tests` | PASS for this change | 1162 passed, 13 skipped, 4 failed — all 4 pre-existing (below) |
| web `vitest run components/invoices lib/api/weinvoice` | PASS | 120 tests, 1 new |

## Existing Failures

`tests/test_config.py` (3) fail on a clean HEAD checkout as well (captcha group config); `tests/test_overfit.py` (training). Unrelated.

## New Failures

None.

## Not Tested

- Screen 05 end to end: NOT RUN — backend on the new code not restarted; needs login.

## Database Verification

Column added 2026-11-10 (user approved): `information_schema.columns` → `gdt_pdf_path text`. Backfill 20: 17 rendered, 3 ZIP elsewhere, 0 errors.

## Final Result

PARTIAL — code verified by unit tests, DB column added, backfill works; screen check pending.

## Recommendation

READY FOR MANUAL COMMIT after the screen check

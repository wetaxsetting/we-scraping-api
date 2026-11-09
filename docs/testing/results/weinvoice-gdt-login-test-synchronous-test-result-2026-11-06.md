# WeInvoice Synchronous GDT Login Test — Test Result

**Date:** 2026-11-06
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api (checked out as `we-scraping-api`); engine side in the we-scraping repository
**Related Record:** [../../issues/weinvoice-gdt-login-test-synchronous-2026-11-06.md](../../issues/weinvoice-gdt-login-test-synchronous-2026-11-06.md)

## Scope

`ScrapingClient`, `CompanyService.create` / `changePassword` (verify first, store only when accepted),
settings `scraping.url` / `scraping.api-key`, messages `WBE3027` / `WBE3028`; and the engine side it calls
(`web/internal_api.py`, `weinvoice_bridge.py`, `invoice_job` password fallback).

## Environment

LOCAL — Windows 11, JDK 21.0.11, Gradle 8.14.4 `--offline`; Python 3.13 (engine); engine PostgreSQL used by
the developer's job; real GDT portal for the engine probe.

## Code / Build Under Test

Branch: local `sit` (after `fc4fab6`) + working tree · Commit: NOT COMMITTED (both repositories).

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --tests 'com.kosign.wecms.domain.weinvoice.*' --tests '*EndpointAuthorizationCoverageTest' --tests '*SchedulerConfigurationContractTest'` | PASS | 99 WeInvoice tests (incl. new `ScrapingClientTest` 7 and rewritten `CompanyServiceTest` 12), coverage + config-contract tests green (the two new `WECMS_WEINVOICE_SCRAPING_*` keys are in `application.yml` and all five templates, values blank) |
| Engine: `python -m pytest tests/test_weinvoice_bridge.py` | PASS | 11 tests (decrypt format, key checks, login-test endpoint: ok / rejected / unavailable / 401 / 503 / 400, token never returned) |
| Engine: `python -m pytest tests -k "invoice_job or main_auto"` | PASS | 67 tests, no regression from the bridge wiring |

## Regression Tests

- `CompanyServiceTest.rejectedAccountIsWbe3027WithTheGdtMessageAndNothingIsSaved`,
  `uncheckableAccountIsWbe3028AndNothingIsSaved`, `rejectedPasswordChangeKeepsTheOldPassword` — the
  "only save what GDT accepted" rule.
- `ScrapingClientTest.engineDownIsUnavailableNotAnException` — the screen can no longer wait forever.

## Integration Checks (live)

| Check | Result | Evidence |
|---|---|---|
| Java `WeInvoiceSecretCipher.encrypt` (jshell on the built classes, local key) → Python `weinvoice_bridge.decrypt` | PASS | decrypted text equals the original |
| `register_connected_companies` / `password_for` on the engine DB | PASS | SQL runs; 0 `CONNECTED` rows yet → 0 added, `companies` 7 → 7 |
| `web/internal_api.py`: health, missing key | PASS | `{"ok":true}`; `401` |
| `web/internal_api.py`: login-test with a non-existent account `0000000000` against the real GDT portal | PASS | captcha solved first try (`conf=1.000`), GDT refused the account → `{"ok":false,"reason":"rejected","message":"<portal text>"}`, ~8 s incl. model load; no real account used |

## Not Tested

- A successful login with a real account (needs the developer's GDT password; deliberately not used).
- The running backend calling the engine end to end, and the web screen (backend not started:
  `WECMS_WEINVOICE_DB_PASSWORD` is still blank on this machine).

## Final Result

PARTIAL — unit and contract tests pass on both sides, the engine path is proven against the real portal
with a fake account; the end-to-end success path waits for the developer.

## Recommendation

READY FOR MANUAL COMMIT (api + engine + web). Developer check: fill the DB password, start
`web/internal_api.py`, add an MST with its real GDT account on screen "Kết nối Cục Thuế".

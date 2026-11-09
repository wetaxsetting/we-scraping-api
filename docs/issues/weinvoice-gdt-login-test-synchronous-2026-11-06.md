# WeInvoice: Verify the GDT Account Synchronously Before Saving a Tax Code

**Date:** 2026-11-06
**Type:** ISSUE
**Current Work Classification:** ISSUE
**Status:** READY FOR MANUAL COMMIT (end-to-end success path to be checked by the developer)
**Investigation Status:** COMPLETE ([audit](../audits/modules/weinvoice-add-tax-code-flow-audit-2026-11-06.md)) · **Approval Status:** APPROVED (developer, 2026-11-06: "làm giúp t") · **Implementation Status:** COMPLETE · **Test Status:** PASS (unit + engine live probe); end-to-end NOT RUN
**Repository:** wecms-api (checked out as `we-scraping-api`); engine side in the we-scraping repository
**Issue:** WeInvoice P1 — screen 06 "Kết nối Cục Thuế"

## Problem

Adding a tax code (`POST /api/v1/weinvoice/companies`) stored it as `PENDING` and only queued a
`LOGIN_TEST` engine request; nothing consumes that queue, so the front end polled forever ("quay hoài")
and an unverified password was saved — contrary to UI Spec "đúng thì mới lưu".

## Expected Behavior (agreed with the developer)

```
web → api POST /companies {mst, username, password}
api → scraping POST /internal/gdt/login-test (internal HTTP, X-Api-Key, waits ≤ ~90 s)
scraping: GDT captcha → model g_600x120 → GDT login (api_client.authenticate_auto, retries)
api: ok → encrypt + save winv_cmpn_infm CONNECTED → 200 to web
     rejected → nothing saved → WBE error with the GDT message
     unavailable / timeout / scraping down → nothing saved → WBE error, web stops waiting
```

Same for `PUT /companies/{mst}/password` (the old password stays when the new one is rejected).
The three projects (web, api, scraping) stay independent codebases sharing one database; the password
stays in the database, encrypted (`winv_cmpn_infm.gdt_pw_enc`). The engine reads it from there for its
scheduled syncs and registers CONNECTED tax codes in its own `companies` table.

## Scope

- api: `ScrapingClient` (HTTP), `CompanyService.create` / `changePassword` synchronous; two settings
  `WECMS_WEINVOICE_SCRAPING_URL`, `WECMS_WEINVOICE_SCRAPING_API_KEY`; messages `WBE3027`, `WBE3028`;
  tests; docs.
- scraping (other repository): `web/internal_api.py` (Flask, `POST /internal/gdt/login-test`),
  `weinvoice_bridge.py` (decrypt + register CONNECTED tax codes), `invoice_job` password fallback.

## Out of Scope

Other engine requests (`SYNC`, `FETCH_XML`, `FETCH_PDF`, `IMPORT`, `TEST_DATA`) stay queued in
`winv_engn_rqst`; WeInvoice login (identity stays the WeCMS JWT).

## Database Impact

NONE (no DDL; rows written are the same table/columns as before).

## API Impact

`POST /companies` returns the saved connection (`CompanyItem`, status `connected`) instead of a
`request_id`; `PUT /companies/{mst}/password` likewise. New errors: `WBE3027` (GDT rejected the
account, message from GDT), `WBE3028` (could not verify now: captcha / network / scraping unavailable).

## Implementation

**api (this repository)**
- `service/ScrapingClient` — `java.net.http` POST `{mst, username, password}` to
  `<scraping.url>/internal/gdt/login-test`, header `X-Api-Key`, connect 5 s / request 90 s. Outcome
  `OK` · `REJECTED` (GDT text) · `UNAVAILABLE` (not configured, HTTP ≠ 200, transport error, captcha
  exhausted). Never throws, never logs the password.
- `CompanyService.create` — format / duplicate / quota checks first (no GDT call when they fail), then
  login test; `OK` → encrypt + `upsert` `CONNECTED` (`conn_chk_dtm` = now) → returns `CompanyItem`.
  `changePassword` — same; username falls back to the stored `gdt_user_id`; `updatePassword` now writes
  `CONNECTED`. `REJECTED` → `WBE3027`, `UNAVAILABLE` → `WBE3028`; nothing written.
  The `LOGIN_TEST` queue entry is no longer created; `EngineRequestService`/`WeInvoiceDb` dropped from
  `CompanyService`.
- `WeInvoiceProperties.scraping` (`Scraping(url, apiKey)`, key masked in `toString`); yml
  `wecms.weinvoice.scraping.*`; keys `WECMS_WEINVOICE_SCRAPING_URL` / `_API_KEY` blank in the five
  templates; `WeInvoiceCompanyController` returns `CompanyItem`.
- Messages `WBE3027`, `WBE3028` (EN, VI, KO).

**engine (we-scraping repository)**
- `web/internal_api.py` — Flask, `POST /internal/gdt/login-test` → `api_client.authenticate_auto`
  (one login at a time: shared model + GDT session), result `ok` / `rejected` / `unavailable`; key
  `SCRAPING_INTERNAL_API_KEY` (constant-time compare; unset → 503); token never returned; host/port
  `SCRAPING_INTERNAL_HOST` / `_PORT` (default 127.0.0.1:8090).
- `weinvoice_bridge.py` — decrypt `v1:` values (`WEINVOICE_SECRET_KEY`), `register_connected_companies`
  (insert `CONNECTED` MSTs into `companies`, never overwriting), `password_for`.
- `web/invoice_job.py` — `_active_companies` registers first (failure only logged); `TokenProvider._login`
  falls back to the WeInvoice password when `HDT_PASSWORD_<MST>` is absent. `web/main_auto.py` uses it.
  `.env.example` documents the new keys.

**web (we-scraping-web)** — `addConnection` / `changePassword` take the `CompanyItem` answer, no polling;
`WBE3027` → `wrong_password`. Record: `we-scraping-web/docs/issues/weinvoice-gdt-login-test-synchronous-2026-11-06.md`.

## Verification

- api: 99/99 WeInvoice tests, coverage + config-contract tests PASS.
- engine: 11 new + 67 existing job tests PASS; live: Java-encrypted value decrypted by Python; bridge SQL
  on the engine DB; internal API against the real GDT portal with a fake account → `rejected` in ~8 s.
- web: 112/112 data-layer tests, `tsc` (source) 0 errors, eslint clean.
- End-to-end success with a real account: NOT RUN (developer's GDT password; backend DB password blank).

## Test Evidence

[weinvoice-gdt-login-test-synchronous-test-result-2026-11-06.md](../testing/results/weinvoice-gdt-login-test-synchronous-test-result-2026-11-06.md)

## Result

```
Code: COMPLETE (api + engine + web) · Tests: PASS · Engine live probe: PASS
End-to-end with a real GDT account: PENDING (developer)
Commit / push: PENDING (developer)
```

## Known Limitations / Follow-up

- The engine's internal API must run as a service next to the job (`python -X utf8 web/internal_api.py`);
  without it every add / password change answers `WBE3028`.
- GDT locks an account after repeated wrong passwords; each failed attempt in WeInvoice counts.
- UI Spec §3.4 (captcha handling) remains a PO decision; the captcha is solved by the engine's model, as
  for the daily sync.

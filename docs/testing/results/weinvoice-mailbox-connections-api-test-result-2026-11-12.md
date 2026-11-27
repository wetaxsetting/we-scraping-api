# WeInvoice Mailbox Connections (API) — Test Result

**Date:** 2026-11-12
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Related Record:** [../../features/addon-service/weinvoice/weinvoice-mailbox-connections-2026-11-12.md](../../features/addon-service/weinvoice/weinvoice-mailbox-connections-2026-11-12.md)

## Scope

Backend half of the mailbox connections feature: `/api/v1/weinvoice/mailboxes/**` (list, connect, edit,
disconnect, scan now, Microsoft authorize + callback), `MailboxService`, `MailboxRepository`,
`MailboxOAuthState`, `MicrosoftOAuthClient`, `ScrapingClient.mailLoginTest`, optional Microsoft settings,
`SecurityConfig` permitAll for the callback, codes `WBE3029`–`WBE3036`, `V2__winv_mailbox.sql` and the manual
script `add-mail-scan-request-type-2026-11-12.sql`.

## Environment

LOCAL — Windows 11, JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`), Gradle 8.14.4 `--offline`.
No database, no engine, no Microsoft tenant: the engine and the Microsoft token endpoint are local
`com.sun.net.httpserver` stubs on 127.0.0.1.

## Code / Build Under Test

Branch: `sit` + working tree · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*" --tests "com.kosign.wecms.config.EndpointAuthorizationCoverageTest"` | PASS | 153 tests, 0 failures, 0 errors, 0 skipped (20 classes) |
| — new `MailboxServiceTest` | PASS | 18 |
| — new `MailboxOAuthStateTest` | PASS | 8 |
| — new `MicrosoftOAuthClientTest` | PASS | 5 |
| — `ScrapingClientTest` (+5 mail login-test cases) | PASS | 12 |
| — `WeInvoicePropertiesTest` (+1: Microsoft keys optional, `${VAR}` = unset) | PASS | 8 |
| — `WeInvoiceBeanConstructionTest` (new beans have a constructor Spring can choose) | PASS | 2 |
| `./gradlew test --offline --tests "com.kosign.wecms.config.SchedulerConfigurationContractTest" --tests "com.kosign.wecms.config.ActuatorEndpointSecurityTest" --tests "com.kosign.wecms.config.EndpointAuthorizationCoverageTest"` | PASS | 27 + 18 + 2 (config-key contract with the four new keys in `application.yml` and all five templates; security chain test after the new permitAll matcher) |

## Test Scenarios

| Scenario | Expected | Actual | Result |
|---|---|---|---|
| Gmail app password `abcd efgh ijkl mnop` | compacted, engine body `APP_PASSWORD imap.gmail.com 993 SSL`, saved encrypted | as expected | PASS |
| Gmail normal password / 12 letters / dashes | `WBE3033`, engine not called | as expected | PASS |
| IMAP without host; with host | `WBE3034`; port 993 + `SSL` defaults | as expected | PASS |
| MST not owned / deleted / missing for ONE | `WBE3002` / `BusinessException` / validation; engine not called | as expected | PASS |
| `ALL` scope | stored with no MST | as expected | PASS |
| Refused / unavailable / missing folder | `WBE3029` (address + server text) / `WBE3030` / `WBE3036`; nothing saved, nothing encrypted | as expected | PASS |
| `MailboxItem` / `MailboxRow` components, request `toString` | no password / secret / token | as expected | PASS |
| PUT with blank password | stored secret decrypted for the re-test, `secret_enc` param null (keep) | as expected | PASS |
| PUT on Microsoft mailbox | re-test with `OAUTH_MS` + stored refresh token | as expected | PASS |
| Unknown id on PUT / scan / DELETE | `WBE3031` | as expected | PASS |
| Scan now | `queue(mst, "MAIL_SCAN", "MANUAL", {mailbox_id: 7})` → `{request_id, subject}` | as expected | PASS |
| Authorize not configured; configured | `WBE3032`; URL state verifies to the owner's choice | as expected | PASS |
| Callback: `error`, forged state, MST gone, not configured, exchange refused, login refused, engine down | 302 `?ms=error&reason=denied · state · company · not_configured · exchange · refused · unavailable`; nothing saved | as expected | PASS |
| Callback success | encrypted refresh token saved for the state's owner, 302 `?ms=ok`, no code / token in URL | as expected | PASS |
| State: round trip, nonce, altered payload, altered signature, garbage, expiry at 10 min, other key, no key | only the untouched, unexpired, same-key state verifies | as expected | PASS |
| Authorize URL / token exchange form | contract parameters; secret only in the POST form; `email` then `preferred_username`; reasons `exchange` / `no_refresh_token` / `no_address` | as expected | PASS |
| Engine `mail/login-test` body | `auth, host, port, tls_mode, login_id, password? / refresh_token?, folders[]`, `X-Api-Key` | as expected | PASS |

## API Verification

Wire names follow the global SNAKE_CASE strategy (records with camelCase components). Endpoint list and
field names: [interface-specification.md §Mailbox connections](../../features/addon-service/weinvoice/interface-specification.md#mailbox-connections-screen-08).
No `@WebMvcTest` was added; the controller is a thin pass-through (same as the other WeInvoice controllers).

## Database Verification

NOT RUN — no SQL was executed on any database (by instruction). `MailboxRepository` SQL (incl.
`ON CONFLICT (owner_id, lower(mail_addr)) WHERE del_yn = 'N'`, which needs the partial unique index of V2)
is unverified against PostgreSQL until V2 is applied.

## Security / Permission Verification

- Callback permitAll is exact path + GET only; the owner comes from the HMAC-signed state (tamper / expiry tests PASS).
- No secret in any DTO, row record, `toString`, log line or redirect URL (tests above; log lines reviewed by hand:
  owner / host / HTTP status / exception class only).
- Every other endpoint: JWT + `CompanyAccessGuard.ownerId()`; controller added to the documented allow-list of
  `EndpointAuthorizationCoverageTest` (WeInvoice has no menu permission code).

## Existing Failures

None observed in the scope run. The 13 known failures of the full suite (missing `application-local.yml` on
other machines) were not re-run here — the full suite was not executed.

## New Failures

None.

## Not Tested

- Full `./gradlew test` — NOT RUN (scope run only).
- `bootRun` / Spring context with real configuration — NOT RUN (bean construction is covered by
  `WeInvoiceBeanConstructionTest`; property binding of the new keys by `SchedulerConfigurationContractTest` + unit test).
- V2 + manual script on a database — NOT RUN (DBA step).
- Real Microsoft sign-in (Azure app, consent, refresh token over IMAP) — NOT RUN (no app registration configured).
- Real engine `POST /internal/mail/login-test` — NOT RUN (engine half implemented in parallel by another engineer).

## Final Result

PARTIAL — automated scope PASS; DB execution and live Microsoft / engine verification pending.

## Recommendation

READY FOR DBA (V2 + manual script) · then READY FOR MANUAL COMMIT after the live checks.

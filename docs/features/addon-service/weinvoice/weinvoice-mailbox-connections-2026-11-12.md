# WeInvoice: Mailbox Connections (Gmail · Microsoft · IMAP · forwarding)

**Date:** 2026-11-12
**Type:** FEATURE
**Current Work Classification:** FEATURE
**Status:** IN PROGRESS — backend (wecms-api): READY FOR DBA; engine / web halves tracked in their repositories
**Approval Status:** APPROVED (developer, 2026-11-12: "ok làm luôn theo layout này") · **Implementation Status:** IN PROGRESS
**Repository:** wecms-api (`we-scraping-api`) — shared contract for the web and the engine
**Evidence:** web audit `we-scraping-web/docs/audits/weinvoice-email-connection-setup-audit-2026-11-12.md`

## Purpose

Let a WeInvoice user connect their own mailboxes so the engine collects invoice mails (XML / PDF / links) for their MSTs.
Spec screen 08 has Gmail + Outlook; the approved layout adds a company-mail (IMAP) card and folds the forwarding address
(screen 07) in as a fourth card. **The IMAP card is outside spec v1.0** — developer-approved, to be reported to the PO.

## Target Behavior (approved layout)

Four tiles: Gmail · Outlook / Microsoft 365 · Company mail (IMAP) · Forwarding. Each form has "Applies to MST" (the working
MST or all my MSTs) and "Folders to scan" (default `INBOX`). "Check and connect" logs in once through the engine; only a
successful login is saved. List of connected mailboxes: kind icon, address, status (Connected / Error / Expired + reason),
last scan time + invoices found, actions Scan now · Edit · Disconnect. Secrets are never returned.

## Contract

### Table `weinvoice.winv_mlbx_infm` (new — `src/main/resources/db/weinvoice/V2__winv_mailbox.sql`)

| Column | Type | Notes |
|---|---|---|
| `mlbx_seq` | bigint identity PK | API `id` (string) |
| `owner_id` | varchar(100) NOT NULL | WeCMS USER_ID, as every `winv_*` table |
| `mlbx_kind` | varchar(10) NOT NULL | `GMAIL` · `MICROSOFT` · `IMAP` |
| `mail_addr` | varchar(320) NOT NULL | |
| `auth_type` | varchar(20) NOT NULL | `APP_PASSWORD` (Gmail) · `PASSWORD` (IMAP) · `OAUTH_MS` |
| `imap_host` / `imap_port` / `tls_mode` | varchar(255) / int / varchar(10) | `tls_mode` `SSL` · `STARTTLS`. Gmail = `imap.gmail.com:993 SSL`, Microsoft = `outlook.office365.com:993 SSL` (filled by the backend) |
| `login_id` | varchar(320) NOT NULL | default = `mail_addr` |
| `secret_enc` | text NOT NULL | password / app password / Microsoft refresh token, `v1:` AES-256-GCM under `WECMS_WEINVOICE_SECRET_KEY` (same cipher as `gdt_pw_enc`). Never returned |
| `fldr_list` | varchar(500) NOT NULL DEFAULT 'INBOX' | comma separated |
| `cmpn_scope` | varchar(5) NOT NULL DEFAULT 'ONE' | `ONE` · `ALL` (all non-deleted MSTs of the owner) |
| `cmpn_mst` | varchar(14) NULL | required when `ONE` |
| `conn_stts` | varchar(20) NOT NULL DEFAULT 'CONNECTED' | `CONNECTED` · `ERROR` · `EXPIRED` (Microsoft token revoked / expired) |
| `conn_msg` | varchar(500) NULL | last error, human readable |
| `last_scan_dtm` | varchar(14) NULL | yyyyMMddHHmmss, Asia/Ho_Chi_Minh, like `conn_chk_dtm` |
| `last_scan_cnt` | int NULL | invoices that got a file from the last scan |
| `del_yn`, `reg_id`, `reg_dtm`, `mod_id`, `mod_dtm` | as `winv_cmpn_infm` | soft delete |

Unique: `(owner_id, lower(mail_addr)) WHERE del_yn = 'N'` — re-connecting the same address updates the row.
`winv_engn_rqst.rqst_type` CHECK gains `MAIL_SCAN` (existing table → manual DBA script under `docs/sql/2026/`).

### Backend endpoints (`/api/v1/weinvoice`, JWT, owner-scoped; wire snake_case)

- `GET /mailboxes` → `MailboxItem[]`: `id, kind, address, auth_type, imap_host, imap_port, tls_mode, login_id, folders[],
  company_scope, company_mst, status, status_message, connected_at, last_scan_at, last_scan_invoices`. No secret.
- `POST /mailboxes` `{kind: GMAIL|IMAP, address, password, imap_host?, imap_port?, tls_mode?, login_id?, folders?[],
  company_scope, company_mst?}` → login test through the engine → save (insert, or update the same address) →
  `MailboxItem`. Gmail: password = 16 letters after removing spaces; host/port/TLS fixed. IMAP: host required, port default
  993, TLS default SSL. Login refused → business error carrying the server's message; engine unreachable → its own error.
  Nothing saved on failure. `company_mst` must be one of the owner's MSTs.
- `PUT /mailboxes/{id}` same body; `password` optional (blank → keep the stored secret, still re-tested).
- `DELETE /mailboxes/{id}` → soft delete.
- `POST /mailboxes/{id}/scan` → engine request `MAIL_SCAN` (`trgr_cd` MANUAL, param `{"mailbox_id": <id>}`) →
  the existing `RequestAccepted` shape; progress via the existing engine-request status endpoint.
- `GET /mailboxes/microsoft/authorize?company_scope=&company_mst=&folders=` → `{url}`; `state` = signed, 10-minute,
  stateless (owner, options, expiry, nonce; HMAC-SHA256 with the secret key). Not configured → business error
  "Microsoft sign-in is not configured".
- `GET /mailboxes/microsoft/callback?code=&state=` (**permitAll**, no JWT — the browser comes back from Microsoft) →
  verify state → exchange the code at `https://login.microsoftonline.com/common/oauth2/v2.0/token`, scope
  `offline_access https://outlook.office.com/IMAP.AccessAsUser.All openid email` → address from the id_token
  (`email`, else `preferred_username`) → engine login test (OAUTH_MS) → save → 302 to
  `<web base>/sources/mailbox?ms=ok` or `?ms=error&reason=<short code>`.
- New optional config (blank = Microsoft card disabled, app still starts): `WECMS_WEINVOICE_MS_CLIENT_ID`,
  `WECMS_WEINVOICE_MS_CLIENT_SECRET`, `WECMS_WEINVOICE_MS_REDIRECT_URI`, `WECMS_WEINVOICE_WEB_BASE_URL`.

### Engine

- `web/internal_api.py`: `POST /internal/mail/login-test` (same `X-Api-Key`) body
  `{auth: PASSWORD|APP_PASSWORD|OAUTH_MS, host, port, tls_mode, login_id, password?, refresh_token?, folders[]}` →
  `{ok: bool, message: str, missing_folders: []}`; OAuth: the engine exchanges the refresh token itself
  (`WEINVOICE_MS_CLIENT_ID` / `WEINVOICE_MS_CLIENT_SECRET`, same values as the backend). Never logs secrets.
- `web/weinvoice_worker.py`: request `MAIL_SCAN` (one mailbox now) + a periodic scan of every `CONNECTED` mailbox
  (`WEINVOICE_MAIL_SCAN_MINUTES`, default 30). Per scan: decrypt (`weinvoice_bridge`), open IMAP read-only per folder,
  run the existing mail ingest **restricted to the mailbox's MSTs**, update `last_scan_dtm`, `last_scan_cnt`; login refused
  → `ERROR` + message; Microsoft `invalid_grant` → `EXPIRED`; a rotated refresh token is re-encrypted and saved
  (`weinvoice_bridge.encrypt`). `email_messages.mailbox` = `<address>/<folder>` as today.
- The `.env` mailbox (`INVOICE_MAIL_*`, Webcash) keeps working unchanged.

### Web

`components/upload/MailboxConnect.tsx` per the approved layout; `lib/api/weinvoice/mailbox.ts` api mode wired to the
endpoints above; mock mode kept; forwarding tile uses the existing `GET /email/address`; VI / EN / KO.

## Database Impact

**BOTH** — FLYWAY MIGRATION-folder file (new table, DBA-applied by hand like V1) + MANUAL DBA SCRIPT (CHECK constraint of an
existing table). Target: the WeInvoice database (engine database), never the WeCMS database.

| Artifact | Change | Order |
|---|---|---|
| [`src/main/resources/db/weinvoice/V2__winv_mailbox.sql`](../../../../src/main/resources/db/weinvoice/V2__winv_mailbox.sql) | `CREATE TABLE weinvoice.winv_mlbx_infm` (columns as the contract; `mlbx_seq bigint GENERATED BY DEFAULT AS IDENTITY`; CHECKs on kind / auth / TLS / scope / scope-needs-MST / status / del_yn / port), partial unique index `winv_mlbx_infm_uk01 (owner_id, lower(mail_addr)) WHERE del_yn = 'N'` (also the backend's `ON CONFLICT` target), index `winv_mlbx_infm_ix01 (owner_id) WHERE del_yn = 'N'`. Header lists the GRANTs a two-role setup needs (engine: `SELECT, UPDATE`) | 1 |
| [`docs/sql/2026/add-mail-scan-request-type-2026-11-12.sql`](../../../sql/2026/add-mail-scan-request-type-2026-11-12.sql) | `winv_engn_rqst_chk01` dropped + recreated with `'MAIL_SCAN'`; `rqst_prmt` comment updated; pre / post-check on `pg_constraint`; rollback only while no `MAIL_SCAN` row exists | 2 |

`V1__winv_schema.sql` is **not** edited (already applied; applied migrations are never changed) — a new environment
runs V1, V2, then the manual script. Rollback of V2: `DROP TABLE weinvoice.winv_mlbx_infm` — **DESTRUCTIVE / DATA-RISK
CHANGE — DBA REVIEW REQUIRED** once mailboxes exist (deletes connected mailboxes and their encrypted secrets).

## Database Execution Status

| Environment | Required | Executor | Status | Executed Date | Verification |
|---|---|---|---|---|---|
| LOCAL (engine DB) | Yes | DEV TEAM | PENDING | - | - |
| SIT | Yes | DBA OR DEV TEAM | PENDING | - | - |
| UAT | Yes | DBA TEAM | NOT STARTED | - | - |
| PROD | Yes | DBA TEAM | NOT STARTED | - | - |

## Implementation

### Backend (wecms-api, 2026-11-12)

- `WeInvoiceMailboxController` (`/api/v1/weinvoice/mailboxes`): `GET`, `POST`, `PUT /{id}`, `DELETE /{id}`,
  `POST /{id}/scan`, `GET /microsoft/authorize`, `GET /microsoft/callback` (302, `Cache-Control: no-store`).
- `MailboxService`: validation before the engine is asked (kind, address, Gmail app password = 16 letters after
  removing whitespace → `WBE3033`, IMAP host required → `WBE3034`, port 1–65535 default 993, TLS `SSL` default /
  `STARTTLS`, folders trimmed / de-duplicated / no `,` / ≤ 500 chars, default `INBOX`; `company_scope` `ONE` needs one of
  the owner's active MSTs via `CompanyConnectionRepository` → `WBE3001` / `WBE3002`, `ALL` stores no MST); engine login
  test; save only on `OK` with no missing folder. `PUT`: kind cannot change; blank password → stored secret decrypted
  for the re-test and kept (`secret_enc = COALESCE(NULL, secret_enc)`); a `MICROSOFT` mailbox only changes folders /
  scope and is re-tested with its refresh token; moving to an address already connected → `WBE3035`. `DELETE`: soft
  delete, `secret_enc = ''` (column is NOT NULL). Scan: `EngineRequestService.queue(cmpn_mst, "MAIL_SCAN", "MANUAL",
  {mailbox_id: <bigint>})` → `RequestAccepted(request_id, subject = mailbox id)`.
- `MailboxRepository` (JDBC through `WeInvoiceDb`): `MailboxRow` has no secret column; `findSecret` is the only read of
  `secret_enc`; `save` = `INSERT … ON CONFLICT (owner_id, lower(mail_addr)) WHERE del_yn = 'N' DO UPDATE … RETURNING mlbx_seq`.
- `ScrapingClient.mailLoginTest(MailLogin)` → `POST /internal/mail/login-test` with `{auth, host, port, tls_mode,
  login_id, password?, refresh_token?, folders[]}`, header `X-Api-Key`, timeout 90 s. Reads `{ok, message,
  missing_folders[]}`: `ok=true` → OK; `ok=false` → REJECTED (server message) unless `reason: "unavailable"`; non-200 /
  transport failure / not configured → UNAVAILABLE. Never throws, never logs secrets.
- `MailboxOAuthState`: stateless state `base64url(json{o, s, m?, f[], e, n}) + "." + base64url(HMAC-SHA256)`, MAC key =
  `HMAC-SHA256(WECMS_WEINVOICE_SECRET_KEY bytes, "weinvoice-ms-oauth-state")` (the AES key is not used directly as a MAC
  key), 10-minute expiry, 16-byte random nonce, constant-time compare.
- `MicrosoftOAuthClient`: authorize URL on `https://login.microsoftonline.com/common/oauth2/v2.0/authorize`
  (`client_id, response_type=code, redirect_uri, response_mode=query, scope, state, prompt=select_account`); code
  exchange `POST …/token` (form, `java.net.http`, 20 s); address = ID-token `email`, else `preferred_username` (claims
  read without signature check — token received directly from the token endpoint over TLS, OIDC Core §3.1.3.7).
- Callback: verify state → re-check the MST still belongs to the owner → exchange → engine login test (`OAUTH_MS`,
  `outlook.office365.com:993 SSL`, login = address) → save (kind `MICROSOFT`, secret = encrypted refresh token, audit id =
  state owner) → 302 `<web base>/sources/mailbox?ms=ok` or `?ms=error&reason=<code>` (`not_configured · denied · state ·
  company · exchange · no_refresh_token · no_address · refused · missing_folders · unavailable · error`).
- `WeInvoiceProperties`: optional `microsoft {client-id, client-secret, redirect-uri}` + `web-base-url`; unset = blank or an
  unresolved `${VAR}`; `microsoftSignInConfigured()` needs all four. Bare `${VAR}` in `application.yml` (house rule); a
  missing line binds as the literal placeholder (String, no bind error), so startup never depends on them.
- `SecurityConfig`: `requestMatchers(HttpMethod.GET, MICROSOFT_CALLBACK_PATH).permitAll()` (exact path). Controller added
  to the allow-list of `EndpointAuthorizationCoverageTest`.

### Deviations from the contract (backend)

- `last_scan_dtm` is documented as **UTC** (V2 column comment), not Asia/Ho_Chi_Minh: `conn_chk_dtm` and every other
  `*_dtm` of these tables are UTC (`WeInvoiceTime.nowDtm()`, engine `now_dtm()`), and the API converts them as UTC.
- Response codes are **upper case** as stored (`GMAIL`, `CONNECTED`, `ONE`…), same vocabulary as the request (accepted
  case-insensitively). Other WeInvoice DTOs lower-case `status`; this one follows the contract tables literally.
- Extra codes beyond "refused" / "unreachable": `WBE3031` not found, `WBE3032` Microsoft not configured, `WBE3033` Gmail app
  password shape, `WBE3034` IMAP host, `WBE3035` address taken (edit), `WBE3036` missing folders (also refused when the
  engine says `ok=true` with `missing_folders`).
- `connected_at` = row creation (`reg_dtm`); re-connecting the same active address keeps it.
- Engine answer may carry `reason: "unavailable"` (same shape as the GDT login test) to be classified as unreachable.

## Files Changed

Main: `controller/WeInvoiceMailboxController.java` (new), `service/MailboxService.java` (new),
`service/MailboxOAuthState.java` (new), `service/MicrosoftOAuthClient.java` (new), `repository/MailboxRepository.java`
(new), `dto/MailboxItem.java`, `dto/MailboxSaveRequest.java`, `dto/MicrosoftAuthorizeResponse.java` (new),
`service/ScrapingClient.java`, `config/WeInvoiceProperties.java`, `config/SecurityConfig.java`, `application.yml`,
`messages.properties` / `_vi` / `_ko`, five `.env*.example`, `db/weinvoice/V2__winv_mailbox.sql` (new),
`docs/sql/2026/add-mail-scan-request-type-2026-11-12.sql` (new).
Tests: `MailboxServiceTest`, `MailboxOAuthStateTest`, `MicrosoftOAuthClientTest` (new); `ScrapingClientTest`,
`WeInvoicePropertiesTest`, `EndpointAuthorizationCoverageTest`, constructor call sites in `WeInvoiceTestProps`,
`StoredFilesInvoiceLayoutTest`, `PartnerApiKeyFilterTest`.
Docs: this record, [interface-specification.md](interface-specification.md), [README.md](README.md),
[test-scenarios.md](test-scenarios.md), test result below.

## API Impact

Seven new endpoints and the `MAIL_SCAN` engine request — see
[interface-specification.md §Mailbox connections](interface-specification.md#mailbox-connections-screen-08). New
codes `WBE3029`–`WBE3036` (EN / VI / KO). New internal call to the engine `POST /internal/mail/login-test`. Existing
endpoints unchanged.

## Verification

### Automated Tests

- Test: `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*" --tests "com.kosign.wecms.config.EndpointAuthorizationCoverageTest"`
- Result: PASS — 153 tests, 0 failures (incl. new `MailboxServiceTest` 18, `MailboxOAuthStateTest` 8,
  `MicrosoftOAuthClientTest` 5, `ScrapingClientTest` +5).
- Test: `SchedulerConfigurationContractTest` + `ActuatorEndpointSecurityTest` + `EndpointAuthorizationCoverageTest`
- Result: PASS — 27 + 18 + 2.

### Regression Review

- Areas checked: `CompanyService` / GDT login test (unchanged code path, tests green), `WeInvoiceProperties` binding
  (new trailing components; all constructor callers updated; blank / `${VAR}` Microsoft keys do not fail), security
  chain (one exact-path GET matcher added before `anyRequest().authenticated()`).
- Result: no regression in the scope run.

### Manual Verification

- Environment: LOCAL
- Steps: none — DB scripts, real engine and real Microsoft sign-in not available / not authorized in this session.
- Result: NOT RUN

## Test Evidence

Test result:
[WeInvoice Mailbox Connections (API) — Test Result](../../../testing/results/weinvoice-mailbox-connections-api-test-result-2026-11-12.md)

Final test result: PARTIAL (automated PASS; DB / live NOT RUN)

## Result

Backend code + tests + docs: COMPLETE · V2 + manual script: PREPARED, not executed · Overall (backend): READY FOR DBA,
then READY FOR MANUAL COMMIT. Nothing committed or pushed.

## Known Limitations / Follow-up

- Run V2 then the manual script on each engine database (and the GRANTs in the V2 header on two-role setups) before
  `POST /mailboxes/{id}/scan` can insert `MAIL_SCAN` rows.
- Azure app registration (multi-tenant, delegated `IMAP.AccessAsUser.All`, redirect URI = the callback) and the four
  `WECMS_WEINVOICE_MS_*` / `WEB_BASE_URL` values; the engine needs the same client id / secret.
- `POST /mailboxes/{id}/scan` does not de-duplicate an open `MAIL_SCAN` for the same mailbox (the request table has no
  per-mailbox key); the engine may skip a mailbox already being scanned.
- Same address connected by two different users → two rows (uniqueness is per owner, as the contract).
- IMAP card is outside spec v1.0 — report to the PO (unchanged from the approval note).

## Cross-Repository Check (2026-11-12, after the three halves)

| Point | Backend | Engine | Web | Result |
|---|---|---|---|---|
| Endpoints + wire fields | as listed above | — | consumes the same paths / fields | MATCH |
| Engine login test | sends `{auth, host, port, tls_mode, login_id, password?, refresh_token?, folders[]}`, reads `{ok, message, missing_folders}` (+ `reason=unavailable`) | `POST /internal/mail/login-test` returns `{ok, message, missing_folders, reason}` | — | MATCH (`reason` extra, used for "unavailable") |
| `*_dtm` time zone | UTC (`WeInvoiceTime`), sent as ISO | writes UTC | passes ISO through; raw 14-digit fallback **fixed** from +07:00 to UTC | FIXED |
| Microsoft `?ms=error&reason=` | `not_configured, denied, state, company, exchange, no_refresh_token, no_address, refused, missing_folders, unavailable, error` | — | web had `access_denied, consent_required, invalid_state, login_failed` → **replaced** by the backend codes (VI/EN/KO) | FIXED |
| "Microsoft not configured" | `WBE3032` | — | now checks `WBE3032` (message match kept as fallback) | FIXED |
| `MAIL_SCAN` request | queued with `{"mailbox_id"}` | handled; result in `rslt_data` | polls `GET /requests/{id}` | MATCH — needs the CHECK script applied |
| `.env` (Webcash) mail flow | — | retried unmatched files / links of **user mailboxes** too → could attach them to any company. **Fixed**: `MailIngest` excludes every `winv_mlbx_infm` address when unrestricted (`weinvoice_bridge.user_mailbox_accounts`, `invoice_store` `exclude=`); test `test_env_flow_skips_files_and_links_of_user_mailboxes` | — | FIXED |

Re-run after the fixes: engine `pytest -k "invoice or xml_parser or fetch_pdf or pdf_ or mail or main_auto or weinvoice or bridge or internal_api"`
1162 passed / 16 skipped; backend weinvoice package + `EndpointAuthorizationCoverageTest` 153 tests, 0 failures; web
`vitest components/upload lib/api/weinvoice` 143 passed, `tsc` 0 source errors, eslint clean.

Known limits: seen mails are tracked per address — two owners (or an owner and the `.env` box) connecting the same
address share that memory, and the `.env` flow then skips that address's files; "Scan now" does not dedupe an open
`MAIL_SCAN`; a long periodic scan delays queued requests; nothing was tried against a real Gmail / IMAP / Microsoft account.

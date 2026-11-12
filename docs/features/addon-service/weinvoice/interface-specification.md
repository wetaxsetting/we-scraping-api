# WeInvoice — Interface Specification

Backend contract of WeInvoice (UI Spec v1.0, P1) and of the Partner API WeCMS calls instead of the
Coocon engine. Source: `domain/weinvoice/**`. Why it is built this way:
[weinvoice-api-implementation-2026-11-02.md](weinvoice-api-implementation-2026-11-02.md).

## Conventions

| Topic | Rule |
|---|---|
| Base path | `/api/v1/weinvoice` (JWT; owner = the token's user id — WeInvoice rows are per user, not per WeCMS `BIZ_KEY`). Partner API: `/api/partner/v1` (header `X-Api-Key`, no JWT). |
| Envelope | House envelope `{ status: { error, code, message }, data }`. Business errors are HTTP 200 with `status.error = true` and a `WBE30xx` code; read `status.error`, not the HTTP code. Binary answers (Excel, ZIP, a file) are the raw body with `Content-Disposition`. |
| Field names | snake_case on the wire (global Jackson strategy). |
| Ids | Strings (`"77"`), as in the UI Spec `lib/types.ts`. Sync-run ids are `R<n>` (requested run) or `J<n>` (engine-scheduled run). Upload files are `u<n>`; the engine copies of an invoice's files are `xml` / `pdf`. |
| Working MST | Every screen call names it (`company_mst`) or reaches it through an invoice id. It must be an active connection of the signed-in user, else `WBE3002` (`WBE3001` when malformed). Invoices of other MSTs answer `WBE3005`. |
| Dates | `yyyy-MM-dd`, months `yyyy-MM`, instants ISO-8601 UTC (`2026-11-02T08:00:00Z`). Partner API: `yyyyMMdd`. |
| Paging | `page` zero-based (negative → 0), `size` default 50, max 200 (Partner API: default 100, max 500). Page responses: `{ rows, total_count, page, size }`. |
| Engine work | Endpoints that need the engine answer `{ request_id, subject }` at once; poll `GET /requests/{request_id}`. Requests stay `queued` until the engine worker (we-scraping repo) picks them up. |
| Feature switch | `WECMS_WEINVOICE_ENABLED=false` → every endpoint answers `WBE3000`. |

## App and dashboard (screens 01–03)

| Method | Path | Request | `data` |
|---|---|---|---|
| GET | `/me` | — | `user_id, user_name, lang_cd, admin, companies[CompanyItem], working_company_mst, max_companies, test_data_enabled, shortcuts{}` |
| PUT | `/me/preferences` | `{ lang_cd?: vi\|en\|ko, default_company_mst?, shortcuts?: {} }` — null keeps the value | same as `GET /me` |
| GET | `/requests/{request_id}` | — | `request_id, type, company_mst, trigger, status, step, params{}, progress{}, result{}, error, cancel_requested, created_at, started_at, finished_at` — `type` ∈ sync · fetch_xml · fetch_pdf · login_test · import · test_data; `status` ∈ queued · running · done · partial · failed · cancelled |
| GET | `/dashboard?company_mst=&year=` | `year` default current | `kpis{count, sales_count, purchase_count, sales_total, purchase_total, out_vat, in_vat, vat_payable}`, `months[12]{month, sales, purchase, sales_count, purchase_count}`, `data_status{missing_xml, no_code_no_xml, missing_pdf, incomplete_periods[]}`, `top_suppliers[5]{tax_id, name, count, total}`, `last_sync{finished_at, status}` |

Dashboard amounts are VND (foreign-currency invoices × their `TGia`) and leave out cancelled and
replaced invoices.

## Invoices (screens 04, 05)

| Method | Path | Request | `data` |
|---|---|---|---|
| GET | `/invoices` | `company_mst`, `direction` (purchase · sales · blank = both), `from`, `to`, `keyword`, `xml` / `pdf` (has · missing), `statuses` (original · replacement · replaced · adjustment · adjusted · cancelled; repeat or comma list), `kind` (normal · cash_register · no_code), `tag_ids`, `provider` (provider MST · `no_code` · `unknown`), `exclude_tax_ids`, `sort_field` (issue_date · number · partner · pre_tax · tax · total), `sort_direction`, `page`, `size` | `rows[InvoiceRow]`, `total_count`, `page`, `size`, `kpis`, `incomplete_periods[{month, got, total}]` |
| GET | `/invoices/facets` | `company_mst`, `direction`, `from`, `to` | `providers[{key, name, known, count}]`, `statuses[{key, count}]`, `kinds[{key, count}]` |
| GET | `/invoices/ids` | same filters as `/invoices` | `["77", …]` — `WBE3017` above 20 000 |
| POST | `/invoices/fetch-missing-xml` | `{ company_mst, ids[] }` | `{ queued, skipped_no_code, skipped_other, request_id }` |
| POST | `/invoices/export/excel` | body = the `/invoices` filter (snake_case JSON; `ids` exports a selection) | XLSX — `WBE3021` above 50 000 rows |
| POST | `/invoices/export/zip` | `{ company_mst, ids[] (≤ 2000), mode, include[] }` — `mode` ∈ flat · by_month · by_partner · by_month_partner · per_invoice; `include` ⊆ xml · pdf · summary (`rendered` skipped) | ZIP, streamed |
| PUT | `/invoices/tags` | `{ ids[], tag_id, on }` | `{ count }` |
| GET | `/invoices/{id}` | — | `InvoiceDetail` (below) |
| GET | `/invoices/{id}/files` | — | `[InvoiceFileItem]` |
| GET | `/invoices/{id}/files/{file_id}` | — | the file |
| POST | `/invoices/{id}/files` | multipart `kind` (xml · pdf · attachment), `file`, `confirm` (attach_anyway · replace; repeatable) | `{ status: ok · needs_confirm · duplicate, reasons[], file, request_id }` |
| DELETE | `/invoices/{id}/files/{file_id}` | — | `null`; `WBE3007` for `xml` / `pdf` |
| POST | `/invoices/{id}/original-pdf` | — | `{ request_id, subject }` |
| PUT | `/invoices/{id}/lookup-code` | `{ code (code or whole link), company_code? }` | `{ request_id, subject }` |

**`InvoiceRow`** — `id, direction, issue_date, template_no, symbol, number, seller_tax_id,
seller_name, buyer_tax_id, buyer_name, partner_tax_id, partner_name, currency, exchange_rate,
pre_tax, tax, total, total_vnd, status, kind, cqt_code, provider, provider_tax_id, provider_known,
has_xml, has_pdf, needs_lookup_code, lookup_code, lookup_code_company, lookup_url, tag_ids[], source, synced_at`.
`lookup_*` (2026-11-11): as the detail's `lookup` — a hand-typed code / link wins over the XML's; `lookup_url` is the
invoice's own link only (no provider fallback).

**`kpis`** — `count` (every match), `pre_tax, tax, total` (VND, cancelled and replaced left out),
`missing_xml` (coded invoices without XML — offer "fetch missing XML" before Excel, UI Spec 3.6),
`no_code_missing_xml`, `missing_pdf`, `sales_count, purchase_count, sales_total, purchase_total`.

**`InvoiceDetail`** — the row fields plus `company_mst, invoice_title, seller{tax_id, name, address,
phone, email, bank_account, bank_name, citizen_id}, buyer{…}, discount, total_in_words,
payment_method, related_id, related{relation, id, template_no, symbol, number, issue_date, reason},
lookup{code, company_code, url, direct, manual, needs_code}, items[{line_no, line_kind, code, name,
unit, qty, price, discount, amount, vat_rate, vat_rate_label, tax_amount}], taxes[{vat_rate_label,
vat_rate, pre_tax, tax}], fees[{name, amount}], signatures{seller, buyer, cqt}, files[], tag_ids[],
xml_task, pdf_task {status, attempts, error_kind, error, next_run_at, updated_at}, warnings[]`.
`vat_rate`: 0 / 5 / 8 / 10, −1 = KCT, −2 = KKKNT, other percentages as their number.
`related.relation`: `replaces` · `adjusts` (this invoice names the other) · `replaced_by` ·
`adjusted_by` (the other names this one).

**`InvoiceFileItem`** — `id, kind (xml · pdf · pdf_gdt · attachment), name, size, origin (gdt · vendor · email ·
upload), added_at, deletable, url`.

File list (2026-11-11): one PDF slot — an uploaded PDF replaces the provider PDF (`pdf`) in the list. `size: null`
= the record points at an engine file this server's disk does not have (fetched by another machine): the item
stays listed, the web shows "chưa có", downloading it answers `WBE3010`. `pdf_gdt` (id `pdf-gdt`, GDT copy) is
listed while there is no readable original: no uploaded PDF and no provider PDF file on this server. Uploaded
invoice XML / PDF are named `<mẫu>_<ký hiệu>_<số>.xml|pdf` and stored as
`<owner>/invoices/<company MST>/<yyyy>/<MM>/<seller MST>/<name>` (invoice date; `_2`, `_3` … for later uploads).

Upload rules (UI Spec 3.5): a PDF must start with `%PDF-`; when neither the file name (symbol +
number) nor the text of the first pages (number + seller MST, CQT code, lookup code) proves it
belongs to the invoice → `needs_confirm` `pdf_not_matched` (resend with `confirm=attach_anyway`);
an existing PDF → `pdf_exists` (`confirm=replace`). An XML whose key differs from the invoice →
`xml_key_mismatch`; a matching XML is handed to the engine (`request_id`) to fill the record. The
record already has an XML → `duplicate` — unless the engine XML file is not on this server (`size: null`): then the
upload is accepted, takes the XML slot and is not re-parsed (the record already has its data).

## GDT connections (screen 06)

| Method | Path | Request | `data` |
|---|---|---|---|
| GET | `/companies` | — | `{ companies[CompanyItem], quota{used, max} }` |
| POST | `/companies` | `{ company_mst, gdt_username?, password, label? }` | `CompanyItem` (`connected`) — GDT login verified first through the scraping engine (synchronous, ~3–10 s, ≤ 90 s); nothing stored on `WBE3027` / `WBE3028` |
| PUT | `/companies/{mst}/password` | `{ password, gdt_username? }` | `CompanyItem` — same verification; the old password stays on `WBE3027` / `WBE3028` |
| PATCH | `/companies/{mst}` | `{ label?, default_company? }` | `CompanyItem` |
| DELETE | `/companies/{mst}` | — | `null` |

**`CompanyItem`** — `tax_id, company_name, username, label, status (pending · connected · error ·
expired · need_password), status_message, has_password, default_company, last_sync_at, created_at`.
The password is never returned.

## Mailbox connections (screen 08)

Added 2026-11-12 — [feature record](weinvoice-mailbox-connections-2026-11-12.md). Codes travel upper case,
exactly as stored and as sent (`kind`, `auth_type`, `tls_mode`, `company_scope`, `status`).

| Method | Path | Request | `data` |
|---|---|---|---|
| GET | `/mailboxes` | — | `MailboxItem[]` (the user's active mailboxes, oldest first) |
| POST | `/mailboxes` | `{ kind (GMAIL · IMAP), address, password, imap_host?, imap_port?, tls_mode?, login_id?, folders?[], company_scope?, company_mst? }` | `MailboxItem` (`CONNECTED`) — one login through the engine first (`POST /internal/mail/login-test`, synchronous, ≤ 90 s); nothing stored on `WBE3029` / `WBE3030` / `WBE3036`. The same address again (case-insensitive) updates its row |
| PUT | `/mailboxes/{id}` | same body; `password` optional | `MailboxItem` — blank `password` keeps the stored secret and re-tests with it. `kind` must stay the mailbox's kind; a `MICROSOFT` mailbox only changes `folders` / `company_scope` / `company_mst` (re-tested with its refresh token) |
| DELETE | `/mailboxes/{id}` | — | `null` — soft delete, secret erased |
| POST | `/mailboxes/{id}/scan` | — | `{ request_id, subject: <mailbox id> }` — engine request `MAIL_SCAN {mailbox_id}`, trigger `MANUAL`; poll `GET /requests/{request_id}` |
| GET | `/mailboxes/microsoft/authorize?company_scope=&company_mst=&folders=` | `folders` repeated or comma separated | `{ url }` — Microsoft sign-in URL (`common` endpoint, `response_type=code`, `response_mode=query`, `prompt=select_account`, scope `offline_access https://outlook.office.com/IMAP.AccessAsUser.All openid email`) with a signed 10-minute `state`; `WBE3032` when not configured |
| GET | `/mailboxes/microsoft/callback?code=&state=` | **no JWT** (permitAll) | HTTP **302** to `<WECMS_WEINVOICE_WEB_BASE_URL>/sources/mailbox?ms=ok` or `?ms=error&reason=<code>` |

Request rules: `address` must look like a mail address. **Gmail** — `password` is the app password, 16 letters
once spaces are removed (`WBE3033`), host / port / TLS fixed to `imap.gmail.com:993 SSL`, `auth_type = APP_PASSWORD`.
**IMAP** — `imap_host` required (`WBE3034`), `imap_port` default 993 (1–65535), `tls_mode` `SSL` (default) or
`STARTTLS`, `login_id` default = `address`, `auth_type = PASSWORD`. **Microsoft** — host `outlook.office365.com:993 SSL`,
`auth_type = OAUTH_MS`, address from the ID token (`email`, else `preferred_username`). `folders` — trimmed,
de-duplicated, no `,`, ≤ 500 characters joined; empty = `["INBOX"]`. `company_scope` `ONE` (default) needs
`company_mst` = one of the user's active tax codes (`WBE3001` / `WBE3002`); `ALL` stores no tax code.
Changing the address of a mailbox to one already connected → `WBE3035`.

**`MailboxItem`** — `id, kind (GMAIL · MICROSOFT · IMAP), address, auth_type (APP_PASSWORD · PASSWORD · OAUTH_MS),
imap_host, imap_port, tls_mode (SSL · STARTTLS), login_id, folders[], company_scope (ONE · ALL), company_mst,
status (CONNECTED · ERROR · EXPIRED), status_message, connected_at, last_scan_at, last_scan_invoices`.
`connected_at` = when the address was first connected (row creation); `last_scan_at` / `last_scan_invoices` are
written by the engine. No password, app password or token is ever returned.

Callback `reason` codes: `not_configured` (Microsoft keys or WeInvoice off) · `denied` (user cancelled, or no
`code`) · `state` (forged / altered / expired state) · `company` (the chosen tax code is no longer the user's) ·
`exchange` (code exchange refused) · `no_refresh_token` · `no_address` · `refused` (IMAP login refused) ·
`missing_folders` · `unavailable` (engine down) · `error` (unexpected). The URL never carries a code, token or the state.

## Sync runs (screen 10)

| Method | Path | Request | `data` |
|---|---|---|---|
| POST | `/sync-runs` | `{ company_msts[] (≤ 50), direction (both · purchase · sales), from_month, to_month, trigger (manual · bulk) }` | `[{ request_id: "R<n>", subject: mst }]` |
| GET | `/sync-runs/{run_id}` | — | `{ run_id, tax_id, status, step, month, listed, xml_done, xml_total, error, run }` |
| POST | `/sync-runs/{run_id}/cancel` | — | engine request (queued → cancelled; running → `cancel_requested`) |
| GET | `/sync-runs?company_mst=&status=&page=&size=` | `status` ∈ queued · running · success · partial · failed · cancelled | page of `{ id, request_id, tax_id, company_name, direction, from_month, to_month, started_at, duration_sec, new_count, updated_count, xml_count, status, message, trigger (manual · bulk · schedule) }` |

A run covers at most 36 months (`WBE3023`) and cannot end in the future (`WBE3016`); an MST without a
stored password is refused (`WBE3024`).

## Mail, uploads (screens 07, 09, 15)

| Method | Path | Request | `data` |
|---|---|---|---|
| GET | `/email/address?company_mst=` | — | `{ company_mst, address: "hd-<mst>@<domain>", configured }` |
| GET | `/email/messages?company_mst=&from=&to=&result=&page=&size=` | `result` ∈ matched · duplicate · unmatched · no_invoice · error | page of `{ id, received_at, from, subject, attachments[], result, engine_status, invoice_ref }` |
| GET | `/email/messages/{id}` | — | `{ message, files[{id, name, kind, status, invoice_id, note}], links[{id, host, url, code, status, invoice_id, attempts, last_error}] }` |
| POST | `/uploads` | multipart `company_mst`, `files` (repeat; .xml · .zip · .pdf; ≤ 200) | `UploadResponse` |
| GET | `/uploads/{upload_id}` | — | `{ upload_id, company_mst, request_id, status, error, created_at, files[{file_no, name, size, outcome, message, invoice_id, seller_tax_id, buyer_tax_id, partner_name, total, pdf_status, processed_at}] }` — `outcome` ∈ new · merged · duplicate · foreign · error · pdf_pending, `null` until processed |
| POST | `/uploads/{upload_id}/adopt` | `{ company_mst }` | `{ request_id, subject }` — adds the MST as `need_password` and imports its `foreign` files |
| DELETE | `/uploads/{upload_id}` | — | `null` |
| POST | `/test-data/load?company_mst=` | — | `{ request_id, subject }`; `WBE3018` unless `WECMS_WEINVOICE_TEST_DATA_ENABLED=true` |

A mail is listed under an MST when one of its files or links landed on an invoice of that MST, or when
an unmatched XML names the MST as buyer or seller.

## Catalogue and tags (screens 16, 18)

| Method | Path | Request | `data` |
|---|---|---|---|
| GET | `/providers?keyword=&link_status=` | `link_status` ∈ ok · check · missing | `[{ stt, tax_id, name, full_name, web, lookup_url, captcha_url, alt_lookup_urls[], verify, link_status, invoice_count, auto_pdf }]` |
| GET | `/tags` | — | `[{ id, name, color, invoice_count }]` |
| POST | `/tags` | `{ name, color? }` | tag |
| PATCH | `/tags/{id}` | `{ name?, color? }` | tag |
| DELETE | `/tags/{id}` | — | `null` |

## Partner API (WeCMS)

| Method | Path | Request | `data` |
|---|---|---|---|
| GET | `/api/partner/v1/einvoices` | `tax_id`, `type` (SALES · EXPENSE · BILL), `from`, `to` (`yyyyMMdd`, ≤ 366 days), `page`, `size` | page of `ScrapedEInvoiceRow` |
| POST | `/api/partner/v1/einvoices/details` | `{ tax_id, type, keys[{ numeric_symbol, invoice_symbol, invoice_no, seller_tax_code }] (≤ 500) }` | `{ rows[ScrapedEInvoiceDetailRow], not_found[keys] }` |

Rows reuse the records WeCMS already ingests (`domain/einvoice/dto`), so WeCMS feeds them to its
existing intake unchanged. `EXPENSE` covers the three GDT purchase lists; `einv_test_results` is
`""` (coded), `"1"` (without code), `"2"` (cash register). `check_result` and `invoice_status` use the
exact phrases WeCMS accepts; values are clipped to the `@Size` of the records. Missing key or wrong
key → HTTP 401, `WBE3019`.

## Error codes

| Code | Meaning |
|---|---|
| WBE3000 | WeInvoice disabled on the server |
| WBE3001 | Tax code malformed (10 or 10-3 digits) |
| WBE3002 | Tax code not managed by the signed-in user (Partner API: not known to the engine) |
| WBE3003 | Tax code already connected |
| WBE3004 | Tenant quota of tax codes reached |
| WBE3005 | Invoice not found (or not visible to the signed-in user) |
| WBE3006 | File not found |
| WBE3007 | Engine file (GDT, provider, mail) cannot be deleted |
| WBE3008 | Not a PDF |
| WBE3009 | Not a readable e-invoice XML |
| WBE3010 | File missing on disk |
| WBE3011 | Request / run / mail not found |
| WBE3012 | Request already finished — cannot be cancelled |
| WBE3013 | Tag not found |
| WBE3014 | Tag name taken |
| WBE3015 | Upload not found |
| WBE3016 | Invalid period |
| WBE3017 | Too many items |
| WBE3018 | Test data disabled |
| WBE3019 | Partner API key missing or invalid (HTTP 401) |
| WBE3020 | Invalid lookup code or link |
| WBE3021 | Export above the row limit |
| WBE3022 | No file received |
| WBE3023 | Sync period too long |
| WBE3024 | MST has no GDT password yet |
| WBE3025 | Upload file type not supported |
| WBE3026 | No upload file belongs to the MST being adopted |
| WBE3027 | The GDT portal rejected the account (`{0}` MST, `{1}` the portal's message) — nothing saved |
| WBE3028 | The GDT account could not be checked now (captcha, network, scraping engine down / not configured) — nothing saved |
| WBE3029 | The mail server refused the login (`{0}` address, `{1}` the server's message) — nothing saved |
| WBE3030 | The mailbox could not be checked now (engine down / not configured, timeout) — nothing saved |
| WBE3031 | Mailbox not found (or not the user's) |
| WBE3032 | Microsoft sign-in is not configured on this server |
| WBE3033 | Gmail password is not a 16-letter app password |
| WBE3034 | IMAP server required for a company mailbox |
| WBE3035 | The address is already connected as another mailbox |
| WBE3036 | Requested folders do not exist in the mailbox (`{1}` the list) — nothing saved |

House codes still apply: `WBE_VALIDATION` (bad / missing parameter), `WBE_DB` (database fault),
`WBE1003` (access denied — e.g. a non-admin changing connections), `WBE1401` (session expired).

# WeInvoice — Test Scenarios

Permanent scenarios for the WeInvoice API ([interface-specification.md](interface-specification.md)).
Evidence of a given run lives in `docs/testing/results/`, not here. "Unit" = covered by the unit tests
under `src/test/java/com/kosign/wecms/domain/weinvoice/`; "DB" = needs the WeInvoice database
(schema applied, engine data present).

## Tenant and access

| # | Scenario | Expected | Level |
|---|---|---|---|
| T1 | Call with a malformed `company_mst` | `WBE3001`, no database call | Unit |
| T2 | `company_mst` not connected to the signed-in user, or soft-deleted | `WBE3002` | Unit |
| T3 | Invoice id of an MST the user does not manage | `WBE3005` (not "forbidden") | DB |
| T4 | Two users of the same WeCMS company: each sees and manages only their own tax codes; any signed-in user (no admin role) adds / changes / removes their own | separate lists; `WBE3002` for the other user's MST | Unit |
| T5 | `WECMS_WEINVOICE_ENABLED=false`, any endpoint | `WBE3000`; no connection attempted | Unit |
| T6 | Every `WECMS_WEINVOICE_*` key present but blank (host `.env` copied from the keys-only template) | Application binds; module off (`enabled=false`, `test_data_enabled=false`, quota 5); no connection; `WBE3000` | Unit |

## Connections (screen 06)

| # | Scenario | Expected | Level |
|---|---|---|---|
| C1 | Add MST with a password GDT accepts | engine login-test first; row `CONNECTED`, password stored as `v1:…`, `CompanyItem` returned in the same call | Unit + live engine |
| C1b | Add MST with a password GDT rejects | `WBE3027` with the portal's message; nothing stored | Unit + live engine (fake account) |
| C1c | Engine down / not configured / timeout | `WBE3028`; nothing stored; the screen stops waiting | Unit |
| C2 | Add an MST already connected | `WBE3003` | Unit |
| C3 | Add when the quota is reached | `WBE3004` | Unit |
| C4 | Re-add a removed MST | verified again, row revived, new password, `CONNECTED` | Unit |
| C5 | Any response, any log line | never contains the password or its ciphertext | Unit |

## Mailbox connections (screen 08, 2026-11-12)

| # | Scenario | Expected | Level |
|---|---|---|---|
| M1 | Connect Gmail with an app password (spaces allowed) | spaces dropped; engine `mail/login-test` with `imap.gmail.com:993 SSL`; row `CONNECTED`, secret `v1:…`; `MailboxItem` in the same call | Unit + live engine |
| M2 | Gmail with a normal password / wrong length | `WBE3033`; engine not called | Unit |
| M3 | Company IMAP without host | `WBE3034`; engine not called; with host: port 993 / `SSL` by default | Unit |
| M4 | `company_mst` not one of the user's MSTs, or missing for `ONE` | `WBE3002` / `WBE_VALIDATION`; engine not called | Unit |
| M5 | Mail server refuses / engine down / folder missing | `WBE3029` (server text) / `WBE3030` / `WBE3036`; nothing stored | Unit + live engine |
| M6 | Same address connected again | the existing row is updated (no duplicate) | DB (manual) |
| M7 | Edit with blank password | stored secret decrypted for the re-test and kept | Unit |
| M8 | Scan now | engine request `MAIL_SCAN {mailbox_id}`, `RequestAccepted` | Unit + live engine |
| M9 | Microsoft authorize when not configured | `WBE3032` | Unit |
| M10 | Microsoft callback: cancelled / forged or expired state / exchange refused / login refused | 302 `?ms=error&reason=denied · state · exchange · refused`; nothing stored | Unit |
| M11 | Microsoft callback success | refresh token stored encrypted, kind `MICROSOFT`, 302 `?ms=ok`; no code / token in the URL | Unit + live Microsoft (manual) |
| M12 | Any response, any log line | never contains a password, app password, refresh token or its ciphertext | Unit |

## Invoices (screens 04, 05)

| # | Scenario | Expected | Level |
|---|---|---|---|
| I1 | List with filters | filters bound as parameters; keyword `%` / `_` escaped; unknown sort → newest first | Unit |
| I2 | KPIs with USD / KRW invoices | totals in VND (× `TGia`); cancelled and replaced left out of amounts | DB |
| I3 | Invoice with `tthai` NULL and Excel text "Hóa đơn đã bị xóa bỏ/hủy bỏ" | status `cancelled` | Unit |
| I4 | Provider added to the catalogue | old invoices show the new name (read-time join) | DB |
| I5 | Coded invoice without XML in a selection, "fetch missing XML" | queued; invoices `K…` counted in `skipped_no_code` | DB |
| I6 | Replacement invoice | `related.relation = replaces`, `related_id` set when the original is stored | DB |

## Files (UI Spec 3.5)

| # | Scenario | Expected | Level |
|---|---|---|---|
| F1 | Upload a non-PDF as `pdf` | `WBE3008` | Unit |
| F2 | PDF named after the invoice | `ok`, stored as `u<n>` | Unit |
| F3 | Unproven PDF on a record that has a PDF | `needs_confirm` [`pdf_exists`, `pdf_not_matched`], nothing stored | Unit |
| F4 | Same with `confirm=replace&confirm=attach_anyway` | stored, flagged confirmed, earlier upload retired | Unit |
| F5 | XML of another invoice | `needs_confirm` [`xml_key_mismatch`] | Unit |
| F6 | Matching XML | stored, `IMPORT` queued with `{invoice_id, file_id}` | Unit |
| F7 | XML with an external entity (XXE) | `WBE3009`, entity not resolved | Unit |
| F8 | Delete `xml` / `pdf` | `WBE3007`; delete `u<n>` soft-deletes | Unit |
| F9 | Stored path containing `../` | refused (`WBE3010`) | Unit (guard) |

## Sync, mail, uploads

| # | Scenario | Expected | Level |
|---|---|---|---|
| S1 | Sync two MSTs | one `SYNC` request each, ids `R<n>` | DB |
| S2 | Period longer than 36 months / ending in the future | `WBE3023` / `WBE3016` | DB |
| S3 | Cancel a queued run / a finished run | `cancelled` / `WBE3012` | DB |
| S4 | History | requested runs and engine-scheduled runs (`J<n>`, trigger `schedule`), an engine run serving a request shown once | DB |
| M1 | Mail of another MST | not listed | DB |
| U1 | Upload `.exe` | `WBE3025` | DB |
| U2 | Adopt a foreign MST | MST added `need_password`, `IMPORT` queued for its files only | DB |

## Partner API

| # | Scenario | Expected | Level |
|---|---|---|---|
| P1 | Missing / wrong `X-Api-Key`, or no key configured | HTTP 401, `WBE3019` | Unit |
| P2 | Valid key | passes to the controller | Unit |
| P3 | Row of an Excel-imported cancelled invoice without code | `invoice_status` "Hóa đơn đã bị hủy", `check_result` "Cục thuế đã nhận không mã", `einv_test_results` "1", `currency_cd` "VND" | Unit |
| P4 | Detail row with a 2 100-character address | clipped to 2 000; whole row passes Bean Validation of the WeCMS record | Unit |
| P5 | WeCMS posts the rows to its own `scrape-results` | accepted, no row skipped for its check-result phrase | DB (WeCMS) |

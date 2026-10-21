# Mobile Receipt Channel — Client Integration Guide

**Date:** 2026-09-03
**Type:** INTEGRATION GUIDE
**Repository:** wecms-api
**Audience:** the client team building the rebuilt WeCMS mobile app (iOS / Android)
**Contract of record:** [../../../references/api-spec/payment/mobile-receipt-api.md](../../../references/api-spec/payment/mobile-receipt-api.md)
**Feature record:** [mobile-receipt-channel-2026-09-03.md](mobile-receipt-channel-2026-09-03.md)

This is the how-to. The api-spec doc above is the contract — when the two disagree, the contract wins
and this file is the one to fix.

`wecms-web` needs nothing from this guide, with one exception: `PUT …/receipts/{doc_id}` is a general
endpoint, so the web client may adopt it if a receipt-edit action is ever wanted there.

---

## 1 · What changed for the client

The old app spoke a single-gateway envelope: one URL for everything, the operation selected by
`API_SVC_ID` in the body, `UPPER_SNAKE` fields, `RSLT_CD` / `RSLT_MSG` / `REC` in the response.

**That is gone.** The rebuilt client calls normal REST endpoints. Nothing in `wecms-api` understands
`API_SVC_ID`; sending it does nothing. Concretely:

| Old app | New client |
|---|---|
| `POST {c_base_url}` with `API_SVC_ID: "WABOOKS_MREC_R006"` | `GET /api/v1/payment/card-transactions/receipts?from_date=…&to_date=…` |
| `UPPER_SNAKE` fields (`TOTL_AMT`, `TRCO_CMNM`) | lowercase `snake_case` (`totl_amt`, `merch_nm`) |
| `RSLT_CD` / `RSLT_MSG` at the top level | `status.code` / `status.message` inside the envelope |
| Lists under `REC` / `REC2` | a normal JSON array under `data.rows` |
| Base URL resolved from the MG config service | your own configuration — MG is not part of the refactor |

The legacy field-level spec is still useful as a map of *what the old app read*:
[../../../references/migration/mobile-app/receipt.md](../../../references/migration/mobile-app/receipt.md).
Do not use it as the wire shape.

## 2 · Before you start

| You need | Where it comes from |
|---|---|
| Base URL | your environment configuration. No production deployment exists yet — see the feature record. |
| A user account | provisioned by DBA/ops on the target tenant |
| The `payment-corporate-card-transactions` menu grant on that account | Settings → Users → Menu Entitlement. **Without it every receipt call returns HTTP 403.** See §8. |

## 3 · Authentication

### 3.1 Log in

```
POST /api/v1/auth/login
Content-Type: application/json
Accept-Language: vi
```

```json
{ "user_id": "someone@example.com", "user_pw": "…" }
```

Normal response — the user belongs to exactly one company:

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "access_token": "eyJhbGciOi…",
    "token_type":   "Bearer",
    "expires_in":   3600,
    "user_id":      "someone@example.com",
    "user_nm":      "Nguyen Van A",
    "biz_key":      "VN_VNM0004_000001",
    "lang_cd":      "VI",
    "cmpn_nm":      "CÔNG TY TNHH WEBCASH VIỆT NAM",
    "auth_gr_cd":   "USER",
    "prdt_type":    "F",
    "tax_id":       "…"
  }
}
```

**The company-chooser branch.** If the account is linked to two or more companies, the same call
returns **no token** and instead:

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "access_token": null,
    "user_id": "someone@example.com",
    "user_nm": "Nguyen Van A",
    "companies_available": [
      { "biz_key": "VN_VNM0004_000001", "cmpn_nm": "…", "auth_gr_cd": "USER",  "prdt_type": "F" },
      { "biz_key": "VN_VNM0004_000014", "cmpn_nm": "…", "auth_gr_cd": "ADMIN", "prdt_type": "H" }
    ]
  }
}
```

Show a picker, then call login again with the chosen `biz_key` in the body to get a token. Branch on
`access_token == null`, not on the array being present.

One-user-one-company is the normal operational case, so most accounts never hit the chooser — but
handle it, or a multi-linked user cannot sign in at all.

### 3.2 Use the token

```
Authorization: Bearer <access_token>
Accept-Language: vi
```

The tenant is baked into the token. **Never send `biz_key` on a receipt call** — it is ignored, and
attempting to pass a different one changes nothing.

### 3.3 Session expiry — there is no refresh token

`expires_in` is seconds (3600 by default). When the token expires, calls return **HTTP 401**. There is
no refresh endpoint: re-login with stored credentials, exactly as the old app did. `POST
/api/v1/auth/logout` revokes the current token server-side, so call it on explicit sign-out (after
logout, the token is rejected even if it has not expired).

Treat **401** as "re-authenticate", never as a business error.

## 4 · Envelope and conventions — read this before writing the client

### 4.1 A business error arrives as HTTP 200

This is the single biggest difference from a conventional REST API, and the one that will bite:

```
HTTP 200 OK
{
  "status":    { "error": true, "code": "WBE_RECEIPT_NOT_EDITABLE", "message": "This receipt can no longer be edited…" },
  "timestamp": "2026-09-03T04:15:22Z",
  "path":      "/api/v1/payment/card-transactions/receipts/R-20260903001",
  "trace_id":  "20260903041522PT06592776769567DEVL"
}
```

So the client's success test is **`status.error === false`**, not `response.ok` / `statusCode == 200`.

Recommended single unwrap helper at the network layer:

```
if (httpStatus == 401)            -> re-authenticate
if (httpStatus == 403)            -> permission problem (see §8)
if (httpStatus >= 400)            -> transport/server fault; show generic error, log trace_id
if (body.status.error == true)    -> business outcome; show body.status.message
else                              -> use body.data
```

`status.message` is already localized from your `Accept-Language` header — display it directly rather
than mapping codes to your own strings. Log `trace_id` on every failure; support can grep it in the
server logs.

### 4.2 Field naming, dates, amounts

- **Wire is lowercase `snake_case`** everywhere, both directions.
- **Dates are `yyyyMMdd` strings** (`"20260903"`), not ISO timestamps. This applies to `use_dt`,
  `from_date`, `to_date`.
- **Amounts are JSON numbers** backed by `NUMERIC(20,6)` — up to 14 integer digits and 6 decimals.
  Use a decimal type on the client, never a 32-bit float. Amount must be **strictly positive**;
  `0` is rejected with `WBE_VALIDATION`.
- `ccy_cd` defaults to `"VND"` when omitted or blank.

### 4.3 Paging

`page` is **zero-based**, `size` defaults to 50 and is clamped to 200 server-side. `data.total_count`
is the unpaged total for the current filter — use it for "load more" logic; do not infer the end of the
list from a short page alone (a full page can still be the last one).

## 5 · The receipt endpoints

All paths below are relative to `/api/v1/payment/card-transactions`.

### 5.1 List my receipts — `GET /receipts`

```
GET /receipts?from_date=20260801&to_date=20260930&search=coffee&page=0&size=20
Authorization: Bearer …
```

| Param | Required | Notes |
|---|---|---|
| `from_date`, `to_date` | **yes** | `yyyyMMdd`, inclusive, matched on the receipt date |
| `search` | no | matches contact name (VI + EN), display doc id, description; case-insensitive |
| `reg_chnl` | no | `MOB` or `PC`. **Omit it** unless you deliberately want one channel — see the note below |
| `page`, `size` | no | `0` / `50` |

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "total_count": 37,
    "rows": [
      {
        "doc_id":       "R-20260903001",
        "disp_doc_id":  "R-20260903001",
        "use_dt":       "20260902",
        "merch_nm":     "Highlands Coffee",
        "merch_tax_id": "0301234567",
        "totl_amt":     85000,
        "ccy_cd":       "VND",
        "purpose":      "6001",
        "purpose_nm":   "Chi phí tiếp khách",
        "description":  "team coffee",
        "reg_chnl":     "MOB",
        "appr_seq_no":  null,
        "file_count":   2,
        "editable":     true
      }
    ]
  }
}
```

**Only the signed-in user's own receipts are returned.** There is no parameter to widen that.

**On `reg_chnl`:** the old `R006` hard-filtered `REG_CHNL='MOB'`, so the app only ever saw what the app
created. The new endpoint spans both channels by default, which means a receipt a user keyed on the web
also shows up in the app — better for the user, and it avoids "my receipt disappeared" reports. Pass
`?reg_chnl=MOB` only if you specifically want the old behaviour.

**`editable` and `appr_seq_no` drive your UI.** `editable: false` means `PUT` would be refused — hide
or disable the Edit action instead of letting the user type a correction and then fail on submit. Today
`editable` turns false once the receipt is attached to an expense approval (`appr_seq_no` non-null) or
if the row was rejected/voided. Do not re-derive the rule client-side; read the flag.

### 5.2 Read one receipt — `GET /receipts/{doc_id}`

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "doc_id":       "R-20260903001",
    "disp_doc_id":  "R-20260903001",
    "doc_type":     "E",
    "date":         "20260902",
    "contact_name": "Highlands Coffee",
    "tax_id":       "0301234567",
    "amount":       85000,
    "ccy_cd":       "VND",
    "purpose_cd":   "6001",
    "purpose_nm":   "Chi phí tiếp khách",
    "description":  "team coffee"
  }
}
```

Note the field names differ from the list row (`amount` vs `totl_amt`, `contact_name` vs `merch_nm`,
`purpose_cd` vs `purpose`) — this endpoint predates the mobile channel and its shape was left
untouched so the web client would not break. Map both shapes explicitly; do not assume one model.

This response carries **no** `editable` / `appr_seq_no`. If your detail screen needs the Edit button,
carry the flag over from the list row, or call the list with a narrow date range.

### 5.3 Create a receipt — `POST /receipts`

```
POST /receipts
Authorization: Bearer …
Content-Type: application/json
X-Client-Channel: MOB
```

```json
{
  "use_dt":       "20260903",
  "trco_key":     null,
  "contact_name": "Highlands Coffee",
  "tax_id":       "0301234567",
  "amount":       85000,
  "ccy_cd":       "VND",
  "purpose_cd":   "6001",
  "description":  "team coffee"
}
```

→ `{ "status": {…}, "data": { "doc_id": "R-20260903001" } }`

- **Send `X-Client-Channel: MOB`.** It records that the capture came from mobile
  (`BUSA_DOC_MSTR.REG_CHNL`). Omit it and the row is recorded as web (`PC`), which makes the data
  wrong for reporting even though the call still succeeds. It has no effect on permissions.
- `use_dt`, `contact_name` and `amount` are required. `contact_name` must be non-blank.
- `trco_key` is the contact id when the user picked from the contact list; leave it `null` for a
  free-typed merchant and the server fills in the legacy manual-mode sentinel.
- `purpose_cd` comes from the purpose list (`GET /api/v1/settings/purpose-settings`).
- Keep the returned `doc_id` — you need it to attach photos.

### 5.4 Update a receipt — `PUT /receipts/{doc_id}`

Same body as create (minus the identity fields), and it is a **full replace**: send every field you
own, because a field sent as `null` is cleared.

```
PUT /receipts/R-20260903001
```

→ `{ "status": {…}, "data": null }`

Refusals (all arrive as HTTP 200 with `status.error: true`):

| `status.code` | Meaning | What the client should do |
|---|---|---|
| `WBE_NOT_FOUND` | no such receipt in this tenant | refresh the list — it was deleted or never existed |
| `WBE_RECEIPT_NOT_EDITABLE` | not the caller's receipt · not a manually-entered receipt · rejected/voided · **already attached to an approval** | refresh the row and honour `editable`; show `status.message` |
| `WBE_VALIDATION` | body failed validation (bad date format, amount ≤ 0, blank contact) | field-level error |

`DOC_TYPE`, `HAND_REG_YN` and `REG_CHNL` are immutable — no need to send them, and `X-Client-Channel`
is not read here.

### 5.5 Photos — `…/receipts/{doc_id}/files`

**Upload** (the document must already exist):

```
POST /receipts/R-20260903001/files
Authorization: Bearer …
Content-Type: multipart/form-data

file=<binary>          # the part name is exactly "file"
```

→ `{ "status": {…}, "data": { "file_id": "…", "org_file_nm": "receipt.jpg", "file_size": "182340" } }`

- Max **10 MB** per file.
- Allowed extensions: `jpg` `jpeg` `png` `gif` `pdf` `doc` `docx` `xls` `xlsx` `ppt` `pptx`. Anything
  else — including `svg` — is rejected.
- Several files per receipt are fine; call it once per file.
- `file_size` comes back as a **string**.

**List** — `GET /receipts/{doc_id}/files` → `data` is a plain array of the same rows.

**Download** — `GET /receipts/{doc_id}/files/{file_id}` returns the **raw bytes**, not the JSON
envelope (`Content-Disposition: attachment`, RFC 5987 filename). Send the bearer token and read the
body as binary/blob. Do not run this response through your unwrap helper.

**Delete** — `DELETE /receipts/{doc_id}/files/{file_id}` → `data: null`.

## 6 · The capture flow, end to end

```
1. POST /receipts  (X-Client-Channel: MOB)      -> doc_id
2. POST /receipts/{doc_id}/files                -> once per photo
3. GET  /receipts?from_date&to_date             -> list, with editable + file_count
4. PUT  /receipts/{doc_id}                      -> corrections, while editable == true
5. (existing expense-approval flow attaches the receipt; editable then goes false)
```

**Order matters: the receipt must exist before a photo can be uploaded.** The old app worked the other
way round — it could upload a photo first and key the fields later, even from another device, because
the photo lived in a staging table. That staging leg is **not implemented**; it is blocked on a
table-scope decision recorded in
[mobile-receipt-api.md §5](../../../references/api-spec/payment/mobile-receipt-api.md).

Practical consequence for the client design: if the user photographs a receipt with no connectivity,
**queue it locally** and replay steps 1–2 when the network returns. Do not design a UI that promises
server-side "photo captured, not yet keyed" — that group does not exist in the list response today.
If the cross-device flow is a product requirement, raise it so the scope decision gets made.

## 7 · Supporting endpoints you will need

Not part of this feature, but the receipt screens depend on them:

| Purpose | Endpoint | Status |
|---|---|---|
| Purpose (expense-usage) list | `GET /api/v1/settings/purpose-settings` | available; the legacy imprest sub-list (`REC2`) is not served |
| Contact list | `GET /api/v1/settings/contacts` | available; legacy `TRCO_NICK_NM` is not in the response |
| Country list | `GET /api/v1/common/countries` | available, incl. dial code (`natn_num`) + `default_yn` |
| Business environment setting | `GET /api/v1/settings/environment-settings/{env_set_inf}` | available |
| Exchange rate | `GET /api/v1/banking/exchange-rates` | available; legacy `UNIT_AMT` is not in the response |
| Next Expense document number | `GET /api/v1/payment/card-transactions/next-doc-no` | available |
| Notification list / mark read | `GET /api/v1/notifications`, `PUT /api/v1/notifications/{id}/read`, `PUT /api/v1/notifications/read-all` | available; board notifications are not included |
| e-Invoice list for linking | `GET /api/v1/payment/card-transactions/links/e-invoices` | available |

Per-service coverage against the old app, including what is missing, is in
[../../../references/migration/mobile-app/README.md](../../../references/migration/mobile-app/README.md)
§3.

## 8 · Errors you will actually hit

| Signal | Cause | Handling |
|---|---|---|
| HTTP **401** | token missing / expired / revoked by logout | re-authenticate (§3.3) |
| HTTP **403** | the account lacks the `payment-corporate-card-transactions` menu grant | not a client bug — ask ops to grant it. Every receipt endpoint is gated by it today (see the feature record's follow-up on a dedicated mobile grant) |
| 200 + `WBE_VALIDATION` | bad body / param — wrong date format, amount ≤ 0, blank contact name, `reg_chnl` outside `PC\|MOB` | field-level error |
| 200 + `WBE_NOT_FOUND` | receipt not in this tenant | refresh the list |
| 200 + `WBE_RECEIPT_NOT_EDITABLE` | see §5.4 | refresh + honour `editable` |
| 200 + `WBE_RECEIPT_DOCID_UNAVAILABLE` | server could not allocate a `DOC_ID` | transient — offer retry |
| 200 + `WBE_RECEIPT_FILE_NOT_FOUND` / `WBE_RECEIPT_FILE_DOC_NOT_FOUND` | attachment or its parent document is gone | refresh the detail screen |
| HTTP **5xx** | server fault | generic error + log `trace_id` |

## 9 · Not available yet — do not design around it

| Old-app capability | State |
|---|---|
| Photo staging before the receipt exists | **not implemented** — scope decision pending (§6) |
| The "photo captured, not yet keyed" list group | **not implemented** — same decision |
| Imprest / advance-payment receipts (`ADV_PAYM_*`) | out of refactor scope |
| Submit receipt (`C005`) | dropped in the source spec; do not rebuild |
| Link a receipt to an e-invoice (`U003`) | separate block, not started |
| Withdrawal matching by amount + date (`R007`) | separate block, not started |
| Push notification / device registration | not implemented |
| SMS-OTP find-id / reset-password | not implemented |
| Board / bulletin-board module | not implemented |

## 10 · Checklist before the client team calls it done

```
[ ] one unwrap helper: status.error checked BEFORE http status (§4.1)
[ ] 401 triggers silent re-login; logout calls POST /auth/logout
[ ] chooser branch handled (access_token == null -> company picker)
[ ] X-Client-Channel: MOB on POST /receipts
[ ] dates sent as yyyyMMdd; amounts as decimal, strictly positive
[ ] paging is zero-based, uses total_count
[ ] Edit action driven by the row's `editable` flag, not a client-side rule
[ ] list vs detail field names mapped separately (§5.2)
[ ] multipart part name is exactly "file"; 10 MB + extension allow-list enforced client-side too
[ ] download read as binary, NOT through the unwrap helper
[ ] offline capture queued locally, replayed as create-then-upload (§6)
[ ] trace_id logged on every failure
```

## 11 · Notes

- This guide is written in English per `docs/README.md` ("All documentation is written in English").
- Nothing here is deployed yet — there is no production or UAT environment for the mobile channel.
  Verification so far is unit-level; see
  [../../../testing/results/mobile-receipt-channel-test-result-2026-09-03.md](../../../testing/results/mobile-receipt-channel-test-result-2026-09-03.md).
- Questions about the contract go to the backend owner of `card-transactions`; questions about scope
  (staging, board, push) are product decisions listed in the feature record's
  § Known Limitations / Follow-up.

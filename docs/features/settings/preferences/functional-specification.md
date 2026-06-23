# Preferences — Functional Specification (Backend)

The implemented behaviour of the Preferences company-profile backend. JEX behaviour reference: live
JEX `comm_0215_001` (`_r001` read, `_u001` save, `_c001`/`_c005` logo upload, `_d001` logo clear).

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Maps to JEX | Audit |
|---|---|---|---|---|---|
| 1 | Read profile | GET | `/api/v1/settings/preferences` | `comm_0215_001_r001` (`BUSI_INFM` half) | No |
| 2 | Save profile | PUT | `/api/v1/settings/preferences` | `comm_0215_001_u001` | Yes |
| 3 | Upload logo | POST | `/api/v1/settings/preferences/logo` | `comm_0215_001_c001` / `_c005` | No |
| 4 | Clear logo | DELETE | `/api/v1/settings/preferences/logo` | `comm_0215_001_d001` | No |
| 5 | Stream logo bytes | GET | `/api/v1/settings/preferences/logo` | (refactor addition — JWT-safe `<img>` source) | No |

**Not in this module (split out / dead):** the *Use Purpose* / *Use VAS Account* toggles
(`comm_0215_001_u002`/`_u003`/`_u004`) live on the generic [Environment Settings](../environment-settings.md)
endpoint pair (codes `00077` / `00074`). See [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 2. Field contract

### 2.1 Read / Update echo (`PreferencesResponse`, output)

| Wire field (snake) | Type | DB column | Notes |
|---|---|---|---|
| `vietnamese_name` | string(≤200) | `CMPN_NM` | Company name; required |
| `display_name` | string(≤200) \| null | `CMPN_ENM` | English/display name; nullable |
| `tax_id` | string(≤40) | `TAX_ID` | Tax ID; required |
| `representative_name` | string(≤100) \| null | `RPPR_NM` | Nullable |
| `representative_email` | string(≤100) | `EML` | Required; valid email |
| `tel_no` | string(≤30) \| null | `TEL_NO` | Nullable |
| `fax_no` | string(≤30) \| null | `FXNO` | Nullable |
| `address` | object | `RPRS_BSUN_*` | `{ post_address, detail_address, city, state }` |
| `logo_url` | string \| null | derived (`CMPN_LOGO`) | streaming GET URI + `?v=<filename>` cache-buster; `null` when no logo |
| `modified_by` | string \| null | `MOD_ID` | Last editor |
| `modified_at` | string \| null (ISO-8601 offset) | `MOD_DTM` | Last modified |

### 2.2 Update request (`PreferencesUpdateRequest`, input)

| Field | Required | Length | Rule |
|---|---|---|---|
| `vietnamese_name` | yes | 1–200 | `@NotBlank @Size(200)` |
| `display_name` | no | 0–200 | `@Size(200)` |
| `tax_id` | yes | 1–40 | `@NotBlank @Size(40)` |
| `representative_name` | no | 0–100 | `@Size(100)` |
| `representative_email` | yes | 1–100 | `@NotBlank @Email @Size(100)` |
| `tel_no` | no | 0–30 | `@Size(30)` |
| `fax_no` | no | 0–30 | `@Size(30)` |
| `address` | yes | — | `@NotNull @Valid` (nested record) — service re-guards with `WBE_VALIDATION` "address: is required" |

Nested `PreferencesAddress`:

| Field | Required | Length | Rule |
|---|---|---|---|
| `post_address` | yes | 1–200 | `@NotBlank @Size(200)` |
| `detail_address` | no | 0–200 | `@Size(200)` |
| `city` | yes | 1–200 | `@NotBlank @Size(200)` |
| `state` | yes | 1–200 | `@NotBlank @Size(200)` |

> The nested record uses `@NotNull` (not `@NotBlank`) at the parent level — Bean Validation applies
> `@NotBlank` only to `CharSequence`, so `@NotBlank` on a record component throws
> `UnexpectedTypeException`. `@Valid` cascades into the address so its inner `@NotBlank` fields are
> still enforced.

Server-managed on save: `MOD_ID`, `MOD_DTM`. `CMPN_LOGO` is **never** touched by the profile save.

### 2.3 Logo upload (multipart)

- Multipart field name: **`file`** (`@RequestPart("file")`).
- Validations, in order: filename present (`WBE2084`) → size > 0 (`WBE2085`) → size ≤ 2 MB
  (`WBE2086`, `{0}` = "2 MB") → extension in allow-list (`WBE2087`, `{0}` = allowed list).
- Allow-list: `jpg`, `jpeg`, `png`, `gif`, `svg` (lower-cased, no leading dot).
- `MAX_LOGO_SIZE_BYTES = 2 MB`. The cap is checked in-service to surface a friendly envelope before the
  container's multipart limit rejects.
- On success the new bytes are written to a fresh opaque filename, `CMPN_LOGO` is repointed, and the
  previous file is best-effort deleted (write-new-before-delete-old).

---

## 3. Business rules

### 3.1 Single-row, UPDATE-only

The `BUSI_INFM` row is created at company setup (DBA manual SQL / login fan-out). Preferences only
**reads or updates** it — it never INSERTs and never physically deletes. A missing row surfaces
`WBE2080` (logged warn; should never happen post-login because `AuthService` fails earlier with
`WBE1009`).

### 3.2 Trim semantics

Required string fields are trimmed and stored as-is. Optional fields go through `trimToNull` — an
empty/whitespace value is persisted as `NULL`, not `""`, so the read side renders a clean blank rather
than a stray space.

### 3.3 Logo lifecycle

- **Opaque filename**: see [database-mapping.md §3](database-mapping.md). Two tenants cannot predict or
  collide on each other's filenames.
- **Write-new-before-delete-old**: a transient IO failure leaves the previous logo intact rather than
  blanking the company display with an inconsistent DB pointer. Cleanup of the old file is best-effort
  and never fails the user call.
- **Delete is idempotent**: clearing a logo on a tenant with no logo returns the unchanged profile.
- **Stream errors**: no logo set → `WBE2082` (frontend treats as "show placeholder"); DB pointer at a
  missing physical file → `WBE2083`.

### 3.4 Scope boundary — toggles are not here

*Use Purpose* (`00077`) and *Use VAS Account* (`00074`) are **environment-setting flags**, persisted in
`BUSI_ENV_SET_INFM` via the generic [Environment Settings](../environment-settings.md) endpoint. They
appear on the same UI panel but are a different backend module with a different persistence path. This
module never reads or writes them; mixing them in would fork two persistence paths into one save, which
the Environment Settings MR (V69/V70) intentionally avoided.

---

## 4. Authorization

- Controller class-level: `@PreAuthorize("@menuAuthz.canAccess('settings-core-preference')")` — every
  endpoint (read and write) requires the `settings-core-preference` menu grant resolved from
  `USER_MENU_AUTH_INFM` (admins bypass the per-user ∩ but not the product scope). F-3 server-side
  menu-authorization model.
- No path/query params to validate (the resource is a single tenant row; the logo endpoints carry no
  path variable). The multipart part name is fixed (`file`).

---

## 5. Audit-log expectations

The profile **Update** writes one row to `AUDIT_LOGS_INFM` via the shared `AuditLogService`:

| Column | Value |
|---|---|
| `WSVC_ID` | `preferences` (slug — never `comm_0215_001`) |
| `REF_NO` | `BUSI_INFM` (table marker) |
| `REF_NO1` | `bizKey` (the tenant company key) |
| activity | `audit.preferences.activity` |
| detail | `audit.preferences.update.detail` |

Read, logo upload, and logo delete write **no** audit row — legacy parity (JEX `comm_0215_001` audits
neither the read nor the logo flows). Append runs `@Transactional(REQUIRES_NEW)` in the shared writer so
an audit failure does not roll back the business transaction.

---

## 6. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBE_VALIDATION` | 200/400 | Bean validation (required/length/email) or the service `address` null guard |
| `WBE2080` | 200 | `BUSI_INFM` row missing for the current tenant |
| `WBE2081` | 200 | Logo disk write failed |
| `WBE2082` | 200 | No logo set (stream) — frontend treats as "show placeholder" |
| `WBE2083` | 200 | Logo DB pointer at a missing/unreadable physical file |
| `WBE2084` | 200 | Uploaded file has no usable filename |
| `WBE2085` | 200 | Uploaded file is empty (zero bytes) |
| `WBE2086` | 200 | Upload exceeds the 2 MB cap (`{0}` = cap) |
| `WBE2087` | 200 | Upload extension not in the image allow-list (`{0}` = allowed list) |
| `WBE1009` / `WBE1003` | 401 / 403 | JWT missing-invalid / authenticated-but-no-grant (shared) |
| `WBE9999` | 500 | Missing tenant context or unexpected server error |

Full request/response shapes: [interface-specification.md](interface-specification.md).

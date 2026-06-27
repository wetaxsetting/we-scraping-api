# Preferences — Test Scenarios (Backend)

SIT/UAT scenarios for the Preferences company-profile backend. Frontend scenarios:
[wecms-web/docs/features/settings/preferences/test-scenarios.md](../../../../../wecms-web/docs/features/settings/preferences/test-scenarios.md).

> **No JUnit suite exists on disk** for this module (no `PreferencesServiceTest` /
> `PreferencesControllerTest`). The scenarios below are the SIT/UAT coverage; §3 lists the recommended
> automated tests to add.

---

## 1. SIT / UAT scenarios

### Read profile

| # | Scenario | Expected |
|---|---|---|
| R1 | GET with a valid session | 200; all profile fields for the current `BIZ_KEY`; `logo_url` non-null only when a logo is set |
| R2 | GET when the tenant has no logo | `logo_url = null` |
| R3 | GET when `BUSI_INFM` row is missing (out-of-band delete) | `WBE2080` |
| R4 | `modified_at` format | ISO-8601 with offset (from `MOD_DTM`) |

### Save profile

| # | Scenario | Expected |
|---|---|---|
| S1 | Valid save (all fields) | 200; `BUSI_INFM` columns updated; `MOD_ID`/`MOD_DTM` stamped; audit row written (`WSVC_ID='preferences'`) |
| S2 | Save with optional fields blank | 200; optional columns stored as `NULL` (trimToNull), not `""` |
| S3 | Missing `vietnamese_name` | `WBE_VALIDATION` |
| S4 | Missing `tax_id` | `WBE_VALIDATION` |
| S5 | Invalid `representative_email` | `WBE_VALIDATION` (@Email) |
| S6 | `address` null | `WBE_VALIDATION` ("address: is required") |
| S7 | Missing `address.post_address` / `city` / `state` | `WBE_VALIDATION` (nested @NotBlank) |
| S8 | Over-length field (e.g. tax_id > 40) | `WBE_VALIDATION` (@Size) |
| S9 | Save does NOT touch the logo | `CMPN_LOGO` unchanged after a profile save |
| S10 | Save when `BUSI_INFM` missing | `WBE2080` |

### Logo — upload

| # | Scenario | Expected |
|---|---|---|
| L1 | Valid PNG ≤ 2 MB | 200; new opaque filename written to `CMPN_LOGO`; `logo_url` non-null with a fresh `?v=`; file present under `<root>/<biz_key>/preferences/` |
| L2 | Replace an existing logo | new file written, `CMPN_LOGO` repointed, previous file best-effort deleted |
| L3 | Empty (zero-byte) file | `WBE2085` |
| L4 | File > 2 MB | `WBE2086` (`{0}` = "2 MB") |
| L5 | Disallowed extension (e.g. `.bmp` / `.pdf` / `.svg`) | `WBE2087` (`{0}` = "jpg, jpeg, png, gif") |
| L6 | Upload with no filename | `WBE2084` |
| L7 | Allowed extensions | `.jpg` / `.jpeg` / `.png` / `.gif` all accepted |
| L7b | `.svg` upload (F-02) | **rejected** → `WBE2087` (`{0}` = "jpg, jpeg, png, gif") — an inline-served SVG can carry `<script>` (stored XSS) |
| L8 | Upload writes NO audit row | no `AUDIT_LOGS_INFM` row for the upload |
| L9 | IO failure on write (simulate read-only root) | `WBE2081`; previous logo + DB pointer unchanged (write-new-before-delete-old) |

### Logo — clear

| # | Scenario | Expected |
|---|---|---|
| C1 | Clear an existing logo | 200; `CMPN_LOGO` null; disk file best-effort deleted; `logo_url` null |
| C2 | Clear when no logo set | 200; idempotent no-op; unchanged profile returned |
| C3 | Clear writes NO audit row | no `AUDIT_LOGS_INFM` row |

### Logo — stream

| # | Scenario | Expected |
|---|---|---|
| T1 | Stream an existing logo | 200; correct `Content-Type` per extension; `Content-Length`; `X-Content-Type-Options: nosniff`; `Cache-Control: private, max-age=300`; raw bytes (not JSON) |
| T2 | Stream when no logo set | `WBE2082` (JSON error envelope) — frontend treats as "show placeholder" |
| T3 | Stream when DB points at a missing file | `WBE2083` |
| T4 | Stream resolves a legacy flat-root logo | bytes returned via `resolveForRead` fallback |
| T5 | SVG upload / `image/svg+xml` (F-02) | **rejected at upload** → `WBE2087`; no `image/svg+xml` is ever stored or streamed (an inline-served SVG can carry `<script>` — stored XSS) |

### Authorization

| # | Scenario | Expected |
|---|---|---|
| A1 | User with `settings-core-preference` | all 5 endpoints succeed |
| A2 | Authenticated user without the grant | 403 `WBE1003` |
| A3 | No / invalid JWT | 401 `WBE1009` |

### Tenant isolation

| # | Scenario | Expected |
|---|---|---|
| TI1 | Read/save always scoped to the JWT `BIZ_KEY` | a request can never read or write another tenant's `BUSI_INFM` row |
| TI2 | Logo path is per-tenant | stream/upload/delete only ever touch `<root>/<biz_key>/preferences/…`; path-escape guard rejects traversal |

### Toggles (out of this module — cross-check)

| # | Scenario | Expected |
|---|---|---|
| TG1 | Use Purpose / Use VAS toggle | persisted via `PUT /environment-settings/{00077\|00074}` — see [environment-settings.md](../environment-settings.md), not this module |

### Error handling

| # | Scenario | Expected |
|---|---|---|
| E1 | Bean-validation failure | `WBE_VALIDATION` envelope, lowercase snake_case |
| E2 | Missing tenant context | `WBE9999` |

---

## 2. Persistence / audit assertions

- Profile save updates **only** the 11 profile columns + `MOD_ID`/`MOD_DTM`; never `CMPN_LOGO`, never
  any other `BUSI_INFM` column (product type, channel, status, …).
- Exactly **one** `AUDIT_LOGS_INFM` row per profile save (`WSVC_ID='preferences'`,
  `REF_NO='BUSI_INFM'`, `REF_NO1=bizKey`). Read / logo upload / logo clear write **zero** audit rows.
- Logo save filename is opaque (base64url SHA-256) and unique per upload even within the same
  millisecond (128 bits of random seed).

---

## 3. Recommended JUnit coverage (not yet on disk)

`PreferencesServiceTest`:
- `get_returnsProfile_withDerivedLogoUrl` · `get_noLogo_logoUrlNull` · `get_rowMissing_throwsWBE2080`
- `update_persistsProfile_andAppendsAuditWithSlugWsvcId` · `update_optionalBlank_storesNull`
- `update_nullAddress_throwsWBE_VALIDATION` · `update_doesNotTouchLogoColumn`
- `uploadLogo_writesOpaqueFile_andRepoints` · `uploadLogo_empty_throwsWBE2085` ·
  `uploadLogo_tooLarge_throwsWBE2086` · `uploadLogo_badExtension_throwsWBE2087` ·
  `uploadLogo_noFilename_throwsWBE2084` · `uploadLogo_replacesAndDeletesPrevious` ·
  `uploadLogo_ioFailure_throwsWBE2081_leavesPreviousIntact`
- `deleteLogo_clearsColumn_andDeletesFile` · `deleteLogo_noLogo_idempotent`
- `openLogo_noLogo_throwsWBE2082` · `openLogo_missingFile_throwsWBE2083` ·
  `openLogo_legacyFlatFallback_resolves`

`PreferencesControllerTest`:
- `get_returnsSnakeCaseFields` · `update_acceptsSnakeCaseBody` ·
  `update_missingRequired_envelopesAsValidation` · `streamLogo_returnsBinaryNotEnvelope` ·
  `streamLogo_noLogo_envelopesAsWBE2082`

`PreferencesLogoResolverTest`:
- `resolve_buildsPerTenantPath` · `resolveForRead_fallsBackToLegacyFlat` ·
  `composeTenant_rejectsPathEscape`

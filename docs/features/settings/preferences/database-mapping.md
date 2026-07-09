# Preferences — Database Mapping (Backend)

This feature has **no dedicated entity** — it maps onto the existing `BUSI_INFM` tenant row through the
auth domain's [`BusiInfm`](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiInfm.java)
entity + [`BusiInfmRepository`](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiInfmRepository.java).
`BUSI_INFM` is in the authoritative legacy-table inventory (`busi_infm`). Column names/types/lengths
below are verified against the entity's `@Column` annotations.

---

## 1. Core table — `BUSI_INFM` (single tenant row)

Entity: `BusiInfm` (`@Table("BUSI_INFM")`, PK = `BIZ_KEY`, 40+ columns, extends `BaseEntity`). The
Preferences feature reads/writes only the **company-profile subset** below; every other `BUSI_INFM`
column (product type, channel, status flags, alliance company, …) is untouched by this module.

| DB column | Type (entity) | JPA field | Preferences use |
|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) PK | `bizKey` | Tenant key; from `TenantContext` — the row's identity |
| `CMPN_NM` | VARCHAR(200) NOT NULL | `cmpnNm` | **Vietnamese company name** — required; read + written |
| `CMPN_ENM` | VARCHAR(200) | `cmpnEnm` | **Display / English name** — optional; read + written (`trimToNull`) |
| `TAX_ID` | VARCHAR(40) | `taxId` | **Tax ID** — required; read + written |
| `RPPR_NM` | VARCHAR(100) | `rpprNm` | **Representative name** — optional; read + written (`trimToNull`) |
| `EML` | VARCHAR(100) | `eml` | **Representative email** — required + `@Email`; read + written |
| `TEL_NO` | VARCHAR(30) | `telNo` | **Tel No.** — optional; read + written (`trimToNull`) |
| `FXNO` | VARCHAR(30) | `fxno` | **Fax No.** — optional; read + written (`trimToNull`) |
| `RPRS_BSUN_POST_ADRS` | VARCHAR(200) | `rprsBsunPostAdrs` | **Address — post address** — required; read + written |
| `RPRS_BSUN_DTL_ADRS` | VARCHAR(200) | `rprsBsunDtlAdrs` | **Address — detail** — optional; read + written (`trimToNull`) |
| `RPRS_BSUN_CITY` | VARCHAR(200) | `rprsBsunCity` | **Address — city** — required; read + written |
| `RPRS_BSUN_ST` | VARCHAR(200) | `rprsBsunSt` | **Address — state** — required; read + written |
| `CMPN_LOGO` | VARCHAR(500) | `cmpnLogo` | **Company logo** — opaque server-generated save filename; written by upload/delete only |
| `MOD_ID` | (BaseEntity) | `modId` | Last modifier login id — stamped on every write |
| `MOD_DTM` | (BaseEntity) | `modDtm` | Last modified timestamp — bumped by `BaseEntity.@PreUpdate`; surfaced as `modified_at` |

No new index, no new constraint, no new Flyway migration — `BUSI_INFM` and all columns above already
exist (auth schema). `CMPN_LOGO` stores a filename, **not** the bytes; the physical file lives on the
filesystem (see §3).

---

## 2. Legacy → Refactor → DTO → UI mapping

| Legacy column | Entity field | Response DTO field (wire) | Update DTO field (wire) | UI label |
|---|---|---|---|---|
| `CMPN_NM` | `cmpnNm` | `vietnamese_name` | `vietnamese_name` | Vietnamese Name |
| `CMPN_ENM` | `cmpnEnm` | `display_name` | `display_name` | Display Name |
| `TAX_ID` | `taxId` | `tax_id` | `tax_id` | Tax ID |
| `RPPR_NM` | `rpprNm` | `representative_name` | `representative_name` | Representative Name |
| `EML` | `eml` | `representative_email` | `representative_email` | Representative Email |
| `TEL_NO` | `telNo` | `tel_no` | `tel_no` | Tel No. |
| `FXNO` | `fxno` | `fax_no` | `fax_no` | Fax No. |
| `RPRS_BSUN_POST_ADRS` | `rprsBsunPostAdrs` | `address.post_address` | `address.post_address` | Address (post) |
| `RPRS_BSUN_DTL_ADRS` | `rprsBsunDtlAdrs` | `address.detail_address` | `address.detail_address` | Address (detail) |
| `RPRS_BSUN_CITY` | `rprsBsunCity` | `address.city` | `address.city` | Address (city) |
| `RPRS_BSUN_ST` | `rprsBsunSt` | `address.state` | `address.state` | Address (state) |
| `CMPN_LOGO` | `cmpnLogo` | `logo_url` (derived) | (server-managed — upload/delete) | Logo |
| `MOD_ID` | `modId` | `modified_by` | (server) | (audit display) |
| `MOD_DTM` | `modDtm` | `modified_at` (ISO-8601) | (server) | (audit display) |

- The four `RPRS_BSUN_*` columns map 1:1 onto the nested
  [`PreferencesAddress`](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/dto/PreferencesAddress.java)
  record (`post_address`, `detail_address`, `city`, `state`). The frontend composes them into one
  read-mode line (joined by `", "`) and four edit-mode fields; the backend just round-trips the four
  columns.
- `logo_url` is **derived**, not a column: `null` when `CMPN_LOGO` is blank, else
  `/api/v1/settings/preferences/logo?v=<CMPN_LOGO>` (the streaming GET URI + cache-buster). The frontend
  never sees the raw disk filename or the storage layout.

### Columns the panel shows but this module does NOT persist

| Panel control | Real owner | Storage |
|---|---|---|
| **Use Purpose** toggle | [Environment Settings](../environment-settings.md) module, code `00077` | `BUSI_ENV_SET_INFM` (override) / `SYS_ENV_SET_INFM` (default) |
| **Use VAS Account** toggle | [Environment Settings](../environment-settings.md) module, code `00074` | `BUSI_ENV_SET_INFM` / `SYS_ENV_SET_INFM` |

These never touch `BUSI_INFM` — see [refactor-gap-analysis.md](refactor-gap-analysis.md) (Toggle split).

---

## 3. Logo storage — `BUSI_INFM.CMPN_LOGO` + filesystem

`CMPN_LOGO` stores an **opaque save filename**, not the image bytes. The physical file lives under a
per-tenant subtree resolved by
[`PreferencesLogoResolver`](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/service/PreferencesLogoResolver.java):

```
<storage-root>/<biz_key>/preferences/<save_file_nm>
```

- **Save filename** = `base64url( SHA-256( bizKey + System.currentTimeMillis() + 16 SecureRandom bytes ) ) + "." + ext`.
  The 16 random bytes dominate the entropy budget (128 bits) so two uploads in the same millisecond
  diverge; the digest is one-way (the original filename was never sensitive). Base64-url (no padding)
  keeps it filesystem-safe on Windows / POSIX.
- **Storage root** is config-only: `wecms.preferences.storage-root`
  ([`PreferencesLogoStorageProperties`](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/service/PreferencesLogoStorageProperties.java),
  prefix `wecms.preferences`, default a CI temp dir; env override `WECMS_PREFERENCES_STORAGE_ROOT`). No
  DB column for the root, no migration.
- **Read fallback**: `resolveForRead(bizKey, name)` returns the per-tenant path when the file exists
  there, else the legacy **flat** `<storage-root>/<save_file_nm>` (logos written before the 2026-06-12
  per-tenant split). Writes always go to the per-tenant path via `resolve(...)`.
- **Path-escape guard**: both `composeTenant` and `composeFlat` normalize and assert the resolved path
  stays inside the tenant subtree / storage root respectively.

---

## 4. Relationships & scope

- **Single-row resource**: `BIZ_KEY` is both the tenant key and the row identity — there is no list and
  no per-row PK beyond the company key.
- **Tenant isolation**: `BIZ_KEY` is the leading (only) PK component; cross-tenant access cannot succeed
  by accident, and the logo subtree is keyed by `BIZ_KEY` as well.
- **In-scope tables**: `BUSI_INFM` only (+ `AUDIT_LOGS_INFM` via the shared writer on Update). The
  `BUSI_ENV_SET_INFM` / `SYS_ENV_SET_INFM` env tables belong to the [Environment Settings](../environment-settings.md)
  module, not this feature.

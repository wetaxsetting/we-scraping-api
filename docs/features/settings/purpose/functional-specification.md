# Purpose — Functional Specification (Backend)

The implemented behaviour of the Purpose Settings backend. JEX behaviour reference (read-only):
[docs/jex/features/purpose/functional-specification.md](../../../../../docs/jex/features/purpose/functional-specification.md).
Deep design narrative + OQ decision log: [../purpose.md](../purpose.md).

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Maps to JEX | Audit |
|---|---|---|---|---|---|
| 1 | List + search | GET | `/api/v1/settings/purpose-settings` | `comm_0187_001_r001` (+ `_r002` keyword merge) | No |
| 2 | Detail | GET | `/api/v1/settings/purpose-settings/{use_usag_cd}` | `comm_0187_001_r001` (single-PK) | No |
| 3 | Create | POST | `/api/v1/settings/purpose-settings` | `comm_0187_001_c001` | Yes |
| 4 | Update | PUT | `/api/v1/settings/purpose-settings/{use_usag_cd}` | `comm_0187_001_u002` (REC) | Yes |
| 5 | Soft-delete | DELETE | `/api/v1/settings/purpose-settings/{use_usag_cd}` | `comm_0187_001_u002` (REC2) + OQ-5 | Yes |
| 6 | Reorder | PUT | `/api/v1/settings/purpose-settings/order` | `comm_0187_001_u004` | Yes (1 summary row) |
| 7a | Excel preview | POST | `/api/v1/settings/purpose-settings/excel/preview` | `comm_0187_004_r001` (parse+validate, no writes) | No |
| 7b | Excel import | POST | `/api/v1/settings/purpose-settings/excel/import` | `comm_0187_004_c001` | Yes (1 summary row) |

**Not ported (DEAD LEGACY / out of scope):** reset-to-system-defaults (`comm_0187_001_u003`), unused
list + restore + permanent delete (`comm_0187_003_*`), ERP account-code mapping (`comm_0187_002`).
See [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 2. Field contract

### 2.1 List / Detail row (output)

| Wire field (snake) | Type | DB column | Notes |
|---|---|---|---|
| `use_usag_cd` | string(≤20) | `USE_USAG_CD` | System PK; opaque; 10-digit zero-padded for refactor-created rows |
| `hand_use_usag_cd` | string(≤20) | `HAND_USE_USAG_CD` | User-visible code (e.g. `BD01`, `BD01-01`) |
| `use_usag_nm` | string(≤255) | `USE_USAG_NM` | Display name |
| `use_usag_cont` | string(≤500) | `USE_USAG_CONT` | Description; nullable |
| `parent_use_usag_cd` | string(≤20) | `PARENT_USE_USAG_CD` | `null` = level-1; else parent's `use_usag_cd` |
| `disp_yn` | `Y`/`N` | `DISP_YN` | On/off toggle for payment dropdown visibility |
| `disp_ord` | integer | `DISP_ORD` | Per-tenant sort order |
| `used_in_transactions` | boolean | derived (`VPurposeInUse` `@Subselect`) | recursive: true if the row or any active child is referenced |
| `mod_id` | string | `MOD_ID` | Last editor login id (display only) |
| `mod_dtm` | string `yyyyMMddHHmmss` | `MOD_DTM` | UTC timestamp, legacy format |
| `children` | row[] | — | nested level-2 rows (list only; null for level-2) |

### 2.2 Create request

| Field | Required | Length | Rule |
|---|---|---|---|
| `hand_use_usag_cd` | yes | 1–20 | `@NotBlank @Size(20)`; unique among active rows → `WBW1152` |
| `use_usag_nm` | yes | 1–255 | `@NotBlank @Size(255)`; unique among active rows → `WBW1138` |
| `use_usag_cont` | no | 0–500 | `@Size(500)` |
| `parent_use_usag_cd` | no | 0–20 | must be active level-1; not self; no 3-level nesting → `WBE_VALIDATION` |
| `disp_yn` | no | 1 | `@Pattern(^[YN]$)`; defaults `Y` |

Server-derived on create: `use_usag_cd` (PK-gen), `biz_key`, `usag_grp_cd = NULL`, `disp_ord = MAX+1`, `mod_id`, `mod_dtm`, `use_yn = 'Y'`.

### 2.3 Update request

Same five fields as Create. Differences:
- `use_usag_cd` is the **immutable path key**.
- `hand_use_usag_cd` is **not** duplicate-checked on update (legacy parity, OQ-4).
- `use_usag_nm` uniqueness excludes self.
- Re-parenting between level-1 parents is allowed; promoting a level-2 to level-1 (`parent → null`) is allowed; **demoting a level-1 that has active children is forbidden**.

### 2.4 Reorder request

```json
{ "items": [ { "use_usag_cd": "0000000001", "disp_ord": 1 },
             { "use_usag_cd": "0000000005", "disp_ord": 2 } ] }
```
Each item: `use_usag_cd` `@NotBlank @Size(20)`, `disp_ord` `@NotNull @Positive`. Service rejects duplicate codes, duplicate orders, and codes that don't resolve to an active refactor row.

### 2.5 Excel preview / import

Preview returns every parsed row (valid + invalid) with per-cell `PurposeExcelFieldError { field, code, message }` (message pre-localized server-side) plus `total_count` / `completed_count` / `error_count`. Per-cell error codes: `required`, `length`, `dup_file`, `dup_db`, `parent_not_found`, `parent_not_root`. File-level: `WBE2041` (>500 rows). Import re-validates against the live DB and inserts roots-first inside one transaction; `inserted_count` returned.

---

## 3. Business rules

### 3.1 Uniqueness

| Field | Scope | Enforcement |
|---|---|---|
| `hand_use_usag_cd` | `(biz_key, usag_grp_cd IS NULL, use_yn='Y')` | service pre-check on **Create only** (legacy parity) → `WBW1152` |
| `use_usag_nm` | same | service pre-check on Create **and** Update (excludes self) → `WBW1138` |

No DB unique constraint on either column (only the PK on `(biz_key, use_usag_cd)`); uniqueness is a service-layer guard because the legacy `usag_grp_cd IS NULL AND use_yn='Y'` scope is a partial-index pattern the legacy schema never materialized. An optional partial unique index was raised as OQ-7 (deferred).

### 3.2 Hierarchy (exactly two levels)

- Level-1: `parent_use_usag_cd IS NULL`. Level-2: `parent_use_usag_cd` = an active level-1 row's `use_usag_cd`.
- Parent must exist, be `use_yn='Y'`, and itself be level-1 (`validateParent`).
- Self-parent (`parent == self`) forbidden.
- Demote-with-children forbidden (`validateNoDemoteWithChildren`) — would create grandchildren.
- All violations raise `BusinessException("WBE_VALIDATION", args)` — no `WBE_PURPOSE_*` family (OQ-8).

### 3.3 Soft-delete + recursive in-use guard (OQ-5)

Delete sets `USE_YN='N'` only — never a physical row delete. Blocked with `WBE1199` when **either**:
1. the row has at least one active child (`hasActiveChildren`), **or**
2. the row itself OR any active child is referenced by an active transaction — resolved through the `VPurposeInUse` `@Subselect` (`existsAnyByBizKeyAndUseUsagCdIn` over `{ code } ∪ findActiveChildCodes(code)`).

One wire code (`WBE1199`) covers both situations; the localized message reads "cannot delete — this purpose or one of its sub-purposes is in use."

### 3.4 Reserved system codes

Excluded from every list response (same set as legacy `comm_0187_001_r001_act.jsp:58-67`), codified as `PurposeSettingsService.RESERVED_SYSTEM_CODES`:

```
1000000301, 3000000401, 3000000402, 3000000403, 3000000404,
3000000405, 3000000406, 3000000407, 3000000408
```

### 3.5 Refactor scope filter

The list/active queries scope to `usag_grp_cd IS NULL` — the legacy "new level-2 hierarchy" bucket
(`comm_0187_001_r001_act.jsp:50`). The legacy per-group `USAG_GRP_CD` flow and the
`BUSI_USAG_SET_INFM_LANG_DETL` language-detail table are out of scope (single-table model). The
`BUSI_USAG_ACCT_INFM_DETL` ERP account-mapping table is a separate feature, not part of Purpose Settings.

---

## 4. Authorization

- Controller class-level: `@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")` — every endpoint (read and write) requires the `settings-core-purpose` menu grant resolved from `USER_MENU_AUTH_INFM` (admins bypass the per-user ∩ but not the product scope). This is the F-3 server-side menu-authorization model (2026-06-06).
- The class Javadoc still says "no `@PreAuthorize` per OQ-6" — **stale comment**, superseded by the annotation actually present. Logged for cleanup in [refactor-gap-analysis.md](refactor-gap-analysis.md).
- `@Validated` (class-level) enables the `@Pattern`/`@Size` guards on the path param (`use_usag_cd`, alphanumeric ≤20) and the `keyword` query param (printable Unicode ≤100).

---

## 5. Audit-log expectations

Every Create / Update / Delete / Reorder writes a row to `AUDIT_LOGS_INFM` via the shared `AuditLogService`:

| Column | Value |
|---|---|
| `WSVC_ID` | `purpose-settings` (slug — never `comm_0187_001`) |
| `REF_NO` | `BUSI_USAG_SET_INFM` (table marker) |
| `REF_NO1` | `use_usag_cd` (system code) |
| `REF_NO2` | `hand_use_usag_cd` (user-visible code) |
| activity | `audit.purpose_settings.activity` |
| detail | `audit.purpose_settings.{create,update,delete,reorder}.detail` (reorder carries the row count) |

Reads and Excel preview write no audit row. Append runs `@Transactional(REQUIRES_NEW)` so an audit failure doesn't roll back the business transaction.

---

## 6. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBW1152` | 200 (business) | Duplicate `hand_use_usag_cd` (Create) |
| `WBW1138` | 200 | Duplicate `use_usag_nm` (Create + Update excl. self) |
| `WBE1199` | 200 | Delete blocked — active children OR row/child in use (recursive) |
| `WBE_VALIDATION` | 200/400 | Bean-validation or service validation (parent invalid/self/demote, reorder dup, Excel import row failure) |
| `WBE2041` | 200 | Excel preview exceeds 500 rows |
| `WBE1009` / `WBE9999` | 401 / 500 | JWT missing-invalid / unexpected server error (shared) |

Per OQ-8, the feature reuses existing/generic codes only — no new `WBE_PURPOSE_*` family. Full request/response shapes: [interface-specification.md](interface-specification.md).

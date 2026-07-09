# ERP Transaction History — Functional Specification (Backend)

The implemented behaviour of the ERP Transaction History backend. JEX behaviour reference (read-only):
the `comm_0219_001` family + the preserved flat spec [../transaction-history.md](../transaction-history.md).

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Maps to JEX | Audit |
|---|---|---|---|---|---|
| 1 | List + 4 tab counts | GET | `/api/v1/settings/erp-transaction-history` | `comm_0219_001_r001` (list) + `_R001_CNT` (counts) | No |
| 2 | Download attached file | GET | `/api/v1/settings/erp-transaction-history/{seqNo}/download` | `comm_0219_001_r002` (full file) | No |
| 3 | Thumbnail (stub) | GET | `/api/v1/settings/erp-transaction-history/{seqNo}/thumbnail` | `comm_0219_001_r002?cmd=getthumb` | No |
| 4 | Excel export | POST | `/api/v1/settings/erp-transaction-history/export` | none (refactor-only addition) | No |

**Read-only feature — no create / update / delete.** The legacy upload (`comm_0219_001_c001`) and the
soft-delete write path (`ERP_TRNS_HSTR_U001` / `_D001`) are **not** ported here (OQ-1 / OQ-8). See
[refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 2. Field contract

### 2.1 List request (query params, all snake_case)

| Wire param | Required | Type | Rule |
|---|---|---|---|
| `from_date` | **yes** | `YYYYMMDD` (8) | required + 8-digit + parseable → `WBE_VALIDATION` otherwise (legacy `validateDate8`) |
| `to_date` | **yes** | `YYYYMMDD` (8) | same; plus `from_date <= to_date` → `WBE_VALIDATION` (legacy `validatePeriod`) |
| `erp_type` | no | `W`/`E`/`D` | optional; uppercased + validated against `{W,E,D}` → `WBE_VALIDATION` (refactor add) |
| `trns_type_cd` | no | `"01".."17"` | optional; 2-digit numeric within 1..17 → `WBE_VALIDATION` (refactor add) |
| `keyword` | no | string | partial, case-insensitive match on `reg_id` only; LIKE meta escaped with `!` |
| `sort_field` | no | FIELD_ID | whitelisted via `ErpTrnsHistorySortField`; unknown → default sort (no error) |
| `sort_direction` | no | `ASC`/`DESC` | parsed leniently; unknown → default sort |
| `page` | no | int | zero-based; negative clamped to `0` |
| `size` | no | int | `<= 0` → `DEFAULT_PAGE_SIZE` (15); clamped to `MAX_PAGE_SIZE` (100) |

### 2.2 List row (output — `ErpTrnsHistoryRow`)

| Wire field (snake) | Type | DB column | Notes |
|---|---|---|---|
| `seq_no` | number | `SEQ_NO` | numeric; row id for the download endpoint |
| `org_file_nm` | string | `ORG_FILE_NM` | original filename (File-cell label) |
| `save_file_nm` | string | `SAVE_FILE_NM` | NAS name (forensic parity; the actual download filename) |
| `file_size` | number | `FILE_SIZE` | bytes; `0` → FE hides the download link |
| `erp_type` | `W`/`E`/`D` | `ERP_TYPE` | raw direction code |
| `erp_type_key` | string \| null | derived | i18n key `settings.erp_transaction_history.direction.*`; null if code unknown |
| `trns_type_cd` | string | `TRNS_TYPE_CD` | raw 2-digit code |
| `trns_type_key` | string \| null | derived | i18n key `settings.erp_transaction_history.type.NN`; null if out of range |
| `reg_dtm` | string `yyyyMMddHHmmss` | `REG_DTM` | UTC timestamp; FE splits into date + time |
| `reg_id` | string | `REG_ID` | login id (or `SYSTEM`); User-cell fallback |
| `user_nm` | string \| null | LEFT JOIN `UserInfm` | display name; null for `SYSTEM` / removed users |

### 2.3 List response envelope (`ErpTrnsHistoryListResponse`)

```json
{ "list": [ … ],
  "total_count": 759, "total_count_wecms": 533, "total_count_erp": 205, "total_count_deleted": 21,
  "page_number": 0, "page_size": 15, "total_pages": 51 }
```

The four counts come from one aggregate query that drops the `ERP_TYPE` predicate (see §3.1). Paging
metadata is zero-based (`page_number`), echoes the clamped `page_size`, and carries server-computed
`total_pages`.

---

## 3. Business rules

### 3.1 Tab counts — `ERP_TYPE` predicate dropped

`countAllDirections` runs the same WHERE as `search` **minus** the `erp_type` predicate, so the four
tab chips (All / WeCMS / ERP / Deleted) all respond to the same date / type / keyword scope at once —
the user does not switch tabs to see another direction's count. Mirrors legacy `getDynamicCNT`
(`comm_0219_001_r001_act.jsp:77-108`). `SUM(CASE WHEN …)` over an empty set is `COALESCE(…, 0L)`-guarded
so the response always carries concrete zero counts.

### 3.2 Validation (service-side, mirrors legacy `ValidationHelper`)

- `from_date` / `to_date`: required, 8-digit, parseable as a calendar date; `from <= to`.
- `erp_type`: optional; uppercased; must be one of `{W,E,D}` (refactor strengthening — legacy accepted
  any 1-char).
- `trns_type_cd`: optional; 2-digit numeric `01..17` (refactor strengthening — legacy passed through).
- `seqNo` (download path): `> 0` (defensive `WBE_VALIDATION`; the path-converter already rejects
  non-numeric).

### 3.3 Sort whitelist

`ErpTrnsHistorySortField` maps wire FIELD_IDs → entity properties: `REG_DTM → regDtm`,
`ERP_TYPE → erpType`, `TRNS_TYPE_CD → trnsTypeCd`, `REG_USER → regId`. `FILE` is **not** in the
whitelist (OQ-5 — the legacy header shows a sort glyph but no IDO column maps to it). Unknown / blank
`sort_field` or `sort_direction` → `DEFAULT_SORT = REG_DTM DESC, SEQ_NO DESC` (OQ-7). A user-chosen
primary sort always appends `SEQ_NO DESC` as a stable tiebreaker.

### 3.4 Download guards

- Row absent **or** not in tenant → `WBE2014` (single envelope — never leak cross-tenant existence).
- `FILE_SIZE = 0` / `null` (batch row with no physical file) → `WBE2015`.
- File missing / unreadable on the storage root → `WBE2015`.
- `Content-Disposition` filename = `SAVE_FILE_NM` (RFC 5987 UTF-8), not `ORG_FILE_NM` (audit parity).

### 3.5 Product-agnostic (no `prdt_type` branching) — OQ-2

The refactor drops all legacy HAENGSUNG-only behaviour: every tab is always visible; every
`trns_type_cd` (incl. `15` Exchange Rate) is valid for every tenant; the File column is always present.
The only legacy per-tab filter preserved is the **Deleted-tab dropdown subset** (`02`,`03`,`12`,`13`,`16`),
and it is enforced **frontend-side** only — the backend accepts any valid code on any tab.

---

## 4. Authorization

- Controller class-level: `@PreAuthorize("@menuAuthz.canAccess('settings-erp-transaction-history')")`
  — every endpoint (list / download / thumbnail / export) requires a valid JWT **and** the
  `settings-erp-transaction-history` menu grant (F-3 server-side menu-authorization model).
- Missing/invalid JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`.
- **No per-row ACL** — any normal user in the tenant can list/download any row in that tenant (mirrors
  the legacy WSVC contract). The legacy `NORM_USER_ONLY_YN=Y` WTA-user rejection is not separately
  modelled in the current single-user R&D environment.

---

## 5. Audit-log expectations

This read-only feature writes **no** `AUDIT_LOGS_INFM` row. Mirrors the `firm-banking-history`
precedent — the refactor restricts audit writes to mutating CRUD (e.g. `firm-banking-settings`
Create/Update); the legacy `mntLogYn=Y` per-request filter that fires on every read is intentionally
not ported for read paths.

When a future Payment feature wires `ErpTrnsHistoryWriter` to record an ERP-exchange file, that write
is an `ERP_TRNS_HSTR` INSERT (the audit ledger row itself) — it is not an `AUDIT_LOGS_INFM` entry, and
it runs in the caller's own `REQUIRES_NEW` scope, failing silently (WARN log) so the caller's primary
mutation is never rolled back.

---

## 6. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_VALIDATION` | 200/400 | Bad date / period / `erp_type` / `trns_type_cd` / non-positive `seqNo` |
| `WBE2014` | 200 | Download — row not found OR not in tenant (single envelope) |
| `WBE2015` | 200 | Download — file unavailable (`FILE_SIZE = 0` or missing/unreadable on storage root) |
| `WBE9001` | 200 | Thumbnail endpoint not implemented (stub) |
| `WBE2010`/`WBE2011`/`WBE2013` | 200 | Export — unknown screenId / >50k rows / no exportable columns (the last is the live seed-gap risk, GAP-4) |
| `WBE1009` / `WBE1003` | 401 / 403 | JWT missing-invalid / lacks `settings-erp-transaction-history` |
| `WBE9999` | 500 | Unexpected server error |

No new `WBE_ERP_*` family — the feature reuses existing/generic codes (`WBE2014`/`WBE2015`/`WBE9001`
were added to `messages.properties` for this feature). Full request/response shapes:
[interface-specification.md](interface-specification.md).

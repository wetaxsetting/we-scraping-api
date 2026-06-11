# Chart of Accounts — Interface Specification (REST)

**Base path:** `/api/v1/settings/chart-of-accounts`
**Controller:** `ChartOfAccountsController` · **Wire format:** lowercase snake_case (global Jackson `SNAKE_CASE`).
**Auth:** every endpoint gated by `@PreAuthorize("@menuAuthz.canAccess('settings-core-chart-of-accounts')")` + JWT.
**Envelope:** standard `{ status, data, timestamp?, path?, trace_id? }`.

See [source-code-index.md](source-code-index.md) for file:line cites and [database-mapping.md](database-mapping.md) for column lineage.

---

## 1. Endpoint summary (9 endpoints)

| # | Method | Path | Purpose | Request | Response data | Legacy |
|---|---|---|---|---|---|---|
| 1 | GET | `/` | List active rows (search + sort) | query: `search`, `sort_field`, `sort_dir` | `ChartOfAccountListResponse` | `comm_0223_001_r001` |
| 2 | GET | `/tree` | Parent-picker tree | — | `ChartOfAccountTreeResponse` | `comm_0223_001_r002` |
| 3 | GET | `/{chart_acct_seq_no}` | Single-row detail | path: seq (`\d{1,14}`) | `ChartOfAccountRow` | — (deep-link helper) |
| 4 | POST | `/` | Create / revive | `ChartOfAccountCreateRequest` | `ChartOfAccountRow` | `comm_0223_001_c001` |
| 5 | PUT | `/{chart_acct_seq_no}` | Update | `ChartOfAccountUpdateRequest` | `ChartOfAccountRow` | `comm_0223_001_u001` |
| 6 | POST | `/bulk-upload` | Bulk create from Excel rows | `ChartOfAccountBulkUploadRequest` | `ChartOfAccountBulkUploadResponse` | `comm_0223_002_c001` |
| 7 | POST | `/bulk-delete` | Bulk soft-delete | `ChartOfAccountBulkActionRequest` | `ChartOfAccountBulkActionResponse` | — |
| 8 | POST | `/bulk-deactivate` | Bulk deactivate (`STTS='I'`) | `ChartOfAccountBulkActionRequest` | `ChartOfAccountBulkActionResponse` | `comm_0223_001_u002` (status leg) |
| 9 | DELETE | `/{chart_acct_seq_no}` | Single soft-delete | path: seq | `Void` | `comm_0223_001_d001` |

> **Not present:** there is **no** `bulk-activate` endpoint and **no** export endpoint. Re-activation is single-row via endpoint 5 (`stts:"A"`). Excel export does not exist for this feature (only a static FE template download).

---

## 2. DTOs

### 2.1 `ChartOfAccountRow` (list / detail / create / update response)

```jsonc
{
  "chart_acct_seq_no": "99",          // string, server-allocated surrogate
  "chart_acct_cd": "1007009",
  "chart_acct_nm_en": "Collection 4",
  "chart_acct_nm_vn": null,
  "chart_acct_nm_ko": null,
  "chart_acct_type": null,            // unused in DEV
  "parent_chart_acct_cd": "ACC-01",
  "cal_rule": "P",                    // "P" | "M" | null
  "stts": "A",                        // "A" | "I"
  "chart_acct_level": 2,              // 1..3 or null (orphan/cycle)
  "has_children": false,
  "restricted": false,                // true for ACC-01/02/03
  "reg_id": "VN_VNM0004_000001",
  "reg_dtm": "20260513094501",        // yyyyMMddHHmmss
  "mod_id": "alice",
  "mod_dtm": "20260601120000"
}
```

### 2.2 `ChartOfAccountListResponse`

```jsonc
{
  "items": [ /* ChartOfAccountRow[] */ ],
  "total_count": 12,
  "restricted_codes": ["ACC-01", "ACC-02", "ACC-03"]
}
```

### 2.3 `ChartOfAccountTreeNode` / `ChartOfAccountTreeResponse`

```jsonc
{ "nodes": [
  {
    "chart_acct_seq_no": "70",
    "chart_acct_cd": "ACC-01",
    "chart_acct_nm_en": "REVENUE",
    "parent_chart_acct_cd": null,
    "chart_acct_level": 1,
    "has_children": true,
    "restricted": true,
    "breadcrumb": "ACC-01 - REVENUE"           // "<cd> - <nm> / … / <cd> - <nm>"
  }
] }
```

Tree admits levels 1..4 defensively; orphans / over-cap rows are filtered. Sorted by `CHART_ACCT_CD ASC` (legacy R002 trailer).

### 2.4 `ChartOfAccountCreateRequest` / `ChartOfAccountUpdateRequest` (identical shape)

```jsonc
{
  "chart_acct_cd": "1007010",        // @NotBlank @Size(max=20) @Pattern([A-Za-z0-9_\-]+)
  "chart_acct_nm_en": "Collection 5",// @NotBlank @Size(max=255)
  "chart_acct_nm_vn": "Thu 5",       // @Size(max=255), optional
  "chart_acct_nm_ko": null,          // @Size(max=255), optional
  "chart_acct_type": null,           // @Size(max=100), optional
  "parent_chart_acct_cd": "ACC-01",  // @Size(max=20); required by service (PO 2026-05-25)
  "cal_rule": null,                  // @Pattern([PM]); level-3 rows only
  "stts": "A"                        // @NotBlank @Pattern([AI])
}
```

- **Update:** seq comes from the path (`\d{1,14}`); body MUST NOT echo `chart_acct_seq_no` or `biz_key`. Code is immutable in the UI (CA-G7).

### 2.5 Bulk DTOs

```jsonc
// ChartOfAccountBulkUploadRequest  (POST /bulk-upload)
{ "items": [ /* ChartOfAccountCreateRequest[], @NotEmpty @Size(max=500), each @Valid */ ] }

// ChartOfAccountBulkUploadResponse
{ "created": 18, "failed": 2 }

// ChartOfAccountBulkActionRequest  (POST /bulk-delete, POST /bulk-deactivate)
{ "chart_acct_seq_nos": ["99","100"] }   // @NotEmpty @Size(max=500), each @Pattern(\d{1,14})

// ChartOfAccountBulkActionResponse
{ "processed": 2, "failed": 0 }
```

---

## 3. Error codes

| Code | When |
|---|---|
| `WBE_CHART_OF_ACCOUNTS_NOT_FOUND` | seq not found / wrong tenant (update, delete, detail) |
| `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` | duplicate active code (create dup-check, update rename collision) |
| `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND` | `parent_chart_acct_cd` missing/soft-deleted in tenant |
| `WBE_CHART_OF_ACCOUNTS_PARENT_REQUIRED` | parent blank/null (level 1 reserved) |
| `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` | edit/delete a restricted root, or rename TO a restricted code |
| `WBE_CHART_OF_ACCOUNTS_SELF_PARENT` | parent equals the row's own code |
| `WBE_CHART_OF_ACCOUNTS_CYCLE` | parent change would create a cycle (update) |
| `WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED` | create/update would push a leaf past depth **3** |
| `WBE_CHART_OF_ACCOUNTS_CAL_RULE_LEVEL3_ONLY` | `cal_rule` set on a non-level-3 row |
| `WBE_VALIDATION` | bean-validation failure (`@NotBlank`/`@Size`/`@Pattern`) |
| `WBE9999` | unexpected server error |

Legacy → refactor code mapping (`WBE0001`→PARENT_NOT_FOUND, `WBE0003`→DUPLICATE_CODE, `WBE0404`→NOT_FOUND, `WBE1178`→RESTRICTED_CODE) is in [migration-guide.md](migration-guide.md) §3.

> Bulk endpoints process per-row independently: a per-row `BusinessException` is tallied in `failed` and does **not** roll back already-committed rows. The envelope itself returns `WBI0000`.

---

## 4. Request/response examples

**Create**

```
POST /api/v1/settings/chart-of-accounts
{ "chart_acct_cd":"1007010","chart_acct_nm_en":"Collection 5",
  "parent_chart_acct_cd":"ACC-01","stts":"A" }
→ 200 { "status":{"error":false,"code":"WBI0000"}, "data": { /* ChartOfAccountRow */ } }
```

**Duplicate code**

```
→ 200 { "status":{ "error":true, "code":"WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE",
        "message":"Account code is already in use." }, "timestamp":..., "path":..., "trace_id":... }
```

**Bulk deactivate**

```
POST /api/v1/settings/chart-of-accounts/bulk-deactivate
{ "chart_acct_seq_nos": ["99","100","101"] }
→ 200 { "data": { "processed": 2, "failed": 1 } }   // 1 failed = restricted root
```

# Transfer History — Functional Specification (Backend)

> Endpoint: `GET /api/v1/banking/internal-transfer/transfer-history`
> Controller: `TransferHistoryController` · Service: `TransferHistoryQueryService`

## 1. Request parameters

All parameters are query-string. Validation is enforced per-`@RequestParam` (class-level `@Validated` → `MethodValidationPostProcessor` raises `ConstraintViolationException` → `WBE_VALIDATION`).

| Wire param | Java arg | Required | Validation | Notes |
|---|---|---|---|---|
| `from_dt` | `fromDt` | ✅ | `@NotBlank` + `@Pattern("\\d{8}")` | `YYYYMMDD` |
| `to_dt` | `toDt` | ✅ | `@NotBlank` + `@Pattern("\\d{8}")` | `YYYYMMDD` |
| `trns_stts` | `trnsStts` | ❌ | `@Pattern("\|00000000\|99999999")` | empty / `00000000` (success) / `99999999` (failed) |
| `page_no` | `pageNo` (Integer) | ❌ | `@Min(1)` | 1-based; default 1 |
| `page_per_cnt` | `pagePerCnt` (String) | ❌ | `@Pattern("10\|15\|20\|50\|100")` | default `15` (`DEFAULT_PAGE_PER_CNT`) |
| `sort_field` | `sortField` | ❌ | none at controller | validated by whitelist in service |
| `sort_direction` | `sortDirection` | ❌ | none at controller | `ASC` / `DESC` (lenient parse) |

`TransferHistoryListRequest` is a record assembled in the controller and forwarded to the service; its components carry the identical Bean Validation annotations. Constants: `PAGE_PER_CNT_PATTERN = "10|15|20|50|100"`, `DEFAULT_PAGE_PER_CNT = 15`.

## 2. Status filter semantics

| `trns_stts` input | Resolves to | DB predicate |
|---|---|---|
| `00000000` | Success only | `trns_stts = '00000000'` |
| `99999999` | Failed only | `trns_stts IS NULL OR trns_stts <> '00000000'` |
| `` (empty) / absent | All | no status predicate |

The `99999999` value is a **UI sentinel** — it is never stored. It maps to "anything that is not the success code", treating NULL as failed (null-safe).

## 3. Sort whitelist

`TransferHistorySortField` (implements `common/web/SortableField`). The wire `sort_field` must match one of these `FIELD_ID`s; unknown / blank values fall back to the default sort (never echoed into SQL — SQL-injection-safe).

| `sort_field` (FIELD_ID) | Entity property | Sorts by |
|---|---|---|
| `TRNS_DT` | `regDtm` | transfer date/time |
| `OUT_BANK_NM` | `outBankCode` | from-bank (by code) |
| `IN_BANK_NM` | `inBankCode` | to-bank (by code) |
| `TRSC_AMT` | `trscAmt` | amount |
| `RMRK` | `rmrk` | remarks |

- `sort_direction` parsed leniently via `common/web/SortDirection.parse(...)` → `ASC` / `DESC`; blank/unknown → fall back.
- **Default sort:** `regDtm DESC, seqNo DESC` (`DEFAULT_SORT`).
- **Stable tie-break:** any whitelisted sort is always suffixed with `seqNo DESC` so equal values keep a deterministic order across pages.
- The frontend "Results" column is **not** sortable (no backend mapping).

## 4. Pagination

- `page_per_cnt` whitelist: `10, 15, 20, 50, 100`. Default `15`.
- `page_no` is 1-based on the wire; the service clamps to `max(1, pageNo)` then converts to a 0-based Spring `Pageable` index.
- `total_count` in the response reflects the **full filtered set**, not the page slice.

## 5. Error codes

| Code | HTTP | When |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_TRANSFER_HISTORY_INVALID_PERIOD` | 200 (business) | `to_dt < from_dt` |
| `WBE_VALIDATION` | 200 | Missing/malformed `from_dt`/`to_dt`, bad `trns_stts`, `page_per_cnt` not in whitelist, `page_no < 1` |
| `WBE9999` | 500 | Unexpected server fault |

`WBE_TRANSFER_HISTORY_INVALID_PERIOD` message: `"to_dt must be greater than or equal to from_dt."`

> ⚠️ **i18n gap (flagged):** the key exists in `messages.properties` (line 683) but **not** in `messages_en.properties`. Because base `messages.properties` is the EN fallback it still resolves, but it diverges from the documented "add to both" convention. Tracked in [refactor-gap-analysis.md](refactor-gap-analysis.md) §"Open questions".

## 6. Response

`ApiResponse<TransferHistoryListResponse>` — see [interface-specification.md](interface-specification.md) for the full field list and JSON examples. Summary:

- `total_count`, `total_vnd_amount`, `total_usd_amount`, `total_vnd_count`, `total_usd_count`
- `rows[]` — current page only, 18 fields each (`seq_no`, `trns_dt`, `trns_tm`, `out_bank_*`, `in_bank_*`, `owan_nm`, `ccy_cd`, `trsc_amt`, `fee`, `trans_type`, `rmrk`, `trns_stts`, `trns_msg`, `user_nm`).

## 7. Permissions

- **JWT required** (everything outside `/api/v1/auth/*` is authenticated).
- **Menu-authorization gated** — `TransferHistoryController` carries `@PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-history')")`, like the other banking controllers (F-3 menu-authorization rollout). There is no ADMIN-only role gate (no `hasRole(...)`), consistent with legacy `NORM_USER_ONLY_YN=Y` which excludes only WTA users, not normal users. An unauthenticated request → 401 via `JwtAuthenticationEntryPoint`; an authenticated user without the menu permission → 403.

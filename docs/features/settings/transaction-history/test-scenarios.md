# ERP Transaction History — Test Scenarios (Backend)

SIT/UAT scenarios for the read-only ERP Transaction History backend. Frontend scenarios:
[wecms-web/docs/features/settings/transaction-history/test-scenarios.md](../../../../../wecms-web/docs/features/settings/transaction-history/test-scenarios.md).

> **No create / update / delete scenarios** — this is a read-only audit ledger; the feature has no
> mutating endpoint. **No upload scenarios** — the legacy upload write-path is intentionally not ported
> (OQ-1); files arrive via the live JEX system / future Payment-feature writers, not this feature.

---

## 1. SIT / UAT scenarios

### List + tab counts

| # | Scenario | Expected |
|---|---|---|
| L1 | List with required date range only | rows in `REG_DTM DESC, SEQ_NO DESC` order for the tenant; `total_count` + 3 direction counts populated; page 0, size 15 |
| L2 | Per-tab filter — `erp_type=W` | only `W` rows in `list`; **all four counts unchanged** (counts drop the `erp_type` predicate) |
| L3 | Per-tab filter — `erp_type=E` / `erp_type=D` | same — direction-filtered `list`, full counts |
| L4 | `trns_type_cd=09` filter | only `Account Inquiry (VND)` rows; counts also scoped to `09` (counts keep `trns_type_cd`) |
| L5 | Keyword search | only rows whose `reg_id` contains the keyword (case-insensitive); `org_file_nm` / `save_file_nm` NOT matched |
| L6 | Missing `from_date` / `to_date` | `WBE_VALIDATION` |
| L7 | `from_date > to_date` | `WBE_VALIDATION` (period inverted) |
| L8 | Invalid `erp_type` (e.g. `X`) | `WBE_VALIDATION` |
| L9 | Invalid `trns_type_cd` (e.g. `99` / `1` / `ab`) | `WBE_VALIDATION` |
| L10 | `size > 100` | clamped to 100; `page_size` echoes the clamped value |
| L11 | `user_nm` resolution | populated from LEFT JOIN; `null` (→ FE shows `reg_id`) for `SYSTEM` / removed users |

### Detail

| # | Scenario | Expected |
|---|---|---|
| DT1 | (no detail endpoint) | **N/A** — there is no `GET /{seqNo}` detail read; the list row carries every field, and `{seqNo}/download` is the only per-row read. State explicitly in any test plan. |

### Download

| # | Scenario | Expected |
|---|---|---|
| D1 | Download a row with `FILE_SIZE > 0` and the file present | 200; `application/octet-stream`; `Content-Disposition` filename = `SAVE_FILE_NM` (RFC 5987); `Content-Length` = file size |
| D2 | Download a missing-file row (`FILE_SIZE = 0`, batch row) | `WBE2015` |
| D3 | Download a `FILE_SIZE > 0` row whose physical file is absent/unreadable on the storage root | `WBE2015` |
| D4 | Download a `seqNo` not in the tenant (wrong tenant) | `WBE2014` (same envelope as not-found — no cross-tenant leak) |
| D5 | Download a non-existent `seqNo` | `WBE2014` |
| D6 | Download with `seqNo <= 0` | `WBE_VALIDATION` |
| D7 | Path-traversal `save_file_nm` (defensive) | resolver rejects (`IllegalArgumentException`) → mapped server error; never escapes the tenant subtree |

### Thumbnail

| # | Scenario | Expected |
|---|---|---|
| TH1 | `GET /{seqNo}/thumbnail` | `WBE9001` ("not yet implemented") — stub |

### Export (XLSX)

| # | Scenario | Expected |
|---|---|---|
| X1 | Export with a seeded `erp-transaction-history` View Setting + date range | XLSX stream; subheader `N record(s)`; columns = request ∩ allow-list ∩ View Setting visibility; same filtered+sorted rows as the list (unpaged) |
| X2 | Export **without** the View Setting seed (current live state, GAP-4) | `WBE2013` (no exportable columns) — export is unverified until the seed lands |
| X3 | Export exceeding 50,000 rows | `WBE2011` |
| X4 | Export with unknown `screenId` | `WBE2010` |
| X5 | `ERP_TYPE` / `TRNS_TYPE_CD` cells | raw legacy codes in the spreadsheet (i18n `*_key` resolution applies to the JSON list, not the workbook); `FILE_SIZE` raw number (Excel-summable) |

### Sort

| # | Scenario | Expected |
|---|---|---|
| ST1 | `sort_field=REG_DTM&sort_direction=ASC` | rows ascending by `reg_dtm`, `SEQ_NO DESC` tiebreaker |
| ST2 | `sort_field=ERP_TYPE` / `TRNS_TYPE_CD` / `REG_USER` | sorted by the mapped entity property |
| ST3 | `sort_field=FILE` | falls back to default sort (FILE is not whitelisted — OQ-5; no error) |
| ST4 | unknown / blank `sort_field` | `REG_DTM DESC, SEQ_NO DESC` default (no error) |

### Search

| # | Scenario | Expected |
|---|---|---|
| SR1 | keyword with `%` / `_` / `!` | escaped via `!`; literal match, not a wildcard |
| SR2 | keyword matching only `org_file_nm` | **no rows** — keyword matches `reg_id` only (legacy parity) |

### Authorization

| # | Scenario | Expected |
|---|---|---|
| A1 | User with `settings-erp-transaction-history` grant | list / download / export succeed |
| A2 | Authenticated user without the grant | 403 `WBE1003` |
| A3 | No / invalid JWT | 401 `WBE1009` |

### Error handling

| # | Scenario | Expected |
|---|---|---|
| E1 | Bean / service validation failure | `WBE_VALIDATION` envelope, lowercase snake_case |
| E2 | Missing tenant context | `WBE9999` |

---

## 2. JUnit coverage map (existing tests)

> **JUnit classes now exist** in `src/test/` (confirmed this pass): `ErpTrnsHistoryServiceTest`
> (33 `@Test`), `ErpTrnsHistoryControllerTest` (9 `@Test`), `ErpTrnsHistoryExportProviderTest`
> (6 `@Test`) — 48 tests total, mirroring the `purpose` / `contacts` test shape. The lists below describe
> the **existing** coverage; the actual method names follow the same scenario set (e.g.
> `list_missingFromDate_throwsValidation`, `resolveDownload_rowNotFound_throwsWbe2014`,
> `thumbnail_notYetImplemented_envelopesAsWbe9001`).

### `ErpTrnsHistoryServiceTest`

`list_returnsRowsAndFourCounts_countsDropErpTypePredicate` ·
`list_filtersByErpType_listScoped_countsUnchanged` · `list_filtersByTrnsTypeCd` ·
`list_keywordMatchesRegIdOnly_escapesLikeMeta` · `list_missingDate_throwsWBE_VALIDATION` ·
`list_periodInverted_throwsWBE_VALIDATION` · `list_invalidErpType_throwsWBE_VALIDATION` ·
`list_invalidTrnsTypeCd_throwsWBE_VALIDATION` · `list_sizeClampedTo100` ·
`list_resolvesUserNmViaLeftJoin_nullForSystem` · `resolveDownload_rowMissing_throwsWBE2014` ·
`resolveDownload_wrongTenant_throwsWBE2014` · `resolveDownload_fileSizeZero_throwsWBE2015` ·
`resolveDownload_fileMissing_throwsWBE2015` · `resolveDownload_success_returnsResource` ·
`buildSort_unknownField_fallsBackToDefault` · `buildSort_fileField_notWhitelisted_fallsBack`

### `ErpTrnsHistoryControllerTest`

`list_returnsSnakeCaseRowsAndCounts` · `list_forwardsAllQueryParams` ·
`download_streamsOctetStream_withSaveFileNmDisposition` ·
`download_nonPositiveSeqNo_envelopesAsValidation` · `download_notFound_envelopesAsWBE2014` ·
`download_fileUnavailable_envelopesAsWBE2015` · `thumbnail_envelopesAsWBE9001` ·
`export_delegatesToExcelExportService`

### `ErpTrnsHistoryExportProviderTest`

`screenIdMatchesRegistryConvention` · `allowedFieldIdsCoverTheSevenSeededColumns` ·
`fetchPassesFilterAndSortThroughToService` · `fetchMapsRowsToFieldIdKeyedMaps` ·
`fetchEmitsRecordCountSubHeader` · `fetchHandlesNullFiltersMap`

### Recommended (not yet authored) `ErpTrnsHistoryFileResolverTest`

`resolve_composesTenantSlugPath` · `resolve_rejectsPathTraversal` · `resolve_blankInput_throws` —
the resolver's path-composition / traversal guard is currently exercised indirectly through the
service test's `resolveDownload_*` cases; a dedicated resolver test class was not located this pass.

---

## 3. Coverage notes

- The four-count fan-out (counts drop `erp_type`) is the highest-value assertion — verify the `list`
  scopes but the counts stay full for one filter combo.
- The download tenant guard (`WBE2014` for wrong tenant) and the `FILE_SIZE = 0` → `WBE2015` branch are
  the security/correctness-critical paths.
- **Export (X1) cannot pass until GAP-4 is resolved** (View Setting seed). X2 documents the current
  live behaviour (`WBE2013`).
- The `ErpTrnsHistoryWriter` is inert (no caller); if/when a Payment feature wires it, add
  `recordUploaded/Deleted/ExportedFile` tests (seq_no funnel, file copy, PK-race retry, swallow-on-failure).

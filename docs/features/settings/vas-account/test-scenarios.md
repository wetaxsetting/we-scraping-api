# Settings > VAS Account — Test Scenarios (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

SIT/UAT + automated coverage. Legend: ✅ verified (automated or SIT/UAT) · ⏳ pending. Companion test
plan (preserved, 4-layer): [testing/vas-account.md](../../../testing/vas-account.md). JEX source of
truth: [docs/jex/features/vas-account/test-scenarios.md](../../../../../docs/jex/features/vas-account/test-scenarios.md).

> Automated coverage lives in `src/test/java/com/kosign/wecms/domain/setting/vasaccount/` —
> `service/VasAccountServiceTest` (27), `controller/VasAccountControllerTest` (12, standalone MockMvc),
> `service/VasAccountExcelServiceTest` (18), `export/VasAccountExportProviderTest` (8),
> `service/VasAccountSortFieldTest` (4). `VasAccountIntegrationTest` (21 cases) is
> **`@Disabled` (F1)** — its cases are listed as ⏳ below.

## Search

| # | Scenario | Expected | Status |
|---|---|---|---|
| S1 | Search by code (`search_wd` matches `vas_acct_cd` prefix/substring) | Matching rows; `LIKE` is case-insensitive, `% _ !` escaped | ✅ |
| S2 | Search by name (`search_wd` matches `vas_acct_nm`) | Matching rows | ✅ |
| S3 | Status filter = `A` only | Only Active rows | ✅ |
| S4 | Status filter = `I` only | Only Inactive rows | ✅ |
| S5 | Status filter = both / none | No status filter (all rows) | ✅ |
| S6 | Invalid single status (`stts=X`) | `WBE_VALIDATION` ("must be A or I") | ✅ |
| S7 | `all=true` | Full filtered set, `page_size=count`, `total_pages` 0/1 | ✅ |
| S8 | Sort by whitelisted field + direction | Rows ordered; `vasAcctSeqNo` tiebreaker appended | ✅ |
| S9 | Sort by unknown field | Falls back to default sort (no error) | ✅ |
| S10 | Page clamp (`size>100`, `size≤0`, negative `page`) | size→100/20, page→0 | ✅ |
| S11 | Lookup picker (`/lookup`, optional keyword) | Active+live rows only, ≤500, ordered by code | ✅ |

## Create

| # | Scenario | Expected | Status |
|---|---|---|---|
| C1 | Valid create | Row persisted; seqNo = MAX+1; type defaults `0` when blank; audit `create.detail` written | ✅ |
| C2 | Duplicate code (live) | `WBE0003` | ✅ |
| C3 | Code too short / too long / bad chars | `WBE_VALIDATION` (`@Size`/`@Pattern`) | ✅ |
| C4 | Name blank / > 50 | `WBE_VALIDATION` | ✅ |
| C5 | Invalid `stts` (not A/I) | `WBE_VALIDATION` | ✅ |
| C6 | snake_case round-trip (request/response casing) | `vas_acct_*` keys honoured | ✅ |

## Update

| # | Scenario | Expected | Status |
|---|---|---|---|
| U1 | Valid update (no code change) | Saved; **no** dup-check; audit `update.detail` | ✅ |
| U2 | Code change to a free code | Saved; dup-check runs and passes | ✅ |
| U3 | Code change to an existing live code | `WBE0003` | ✅ |
| U4 | Status change A↔I (no separate endpoint) | `stts` updated via PUT | ✅ |
| U5 | Blank `vas_acct_enm` / `vas_acct_type` / `cont` | Existing values preserved | ✅ |
| U6 | Unknown / wrong-tenant seqNo | `WBE2040` | ✅ |
| U7 | Non-positive / non-numeric seqNo | `WBE_VALIDATION` | ✅ |

## Status (single-record — no bulk)

| # | Scenario | Expected | Status |
|---|---|---|---|
| ST1 | Single activation (PUT `stts=A`) | Row Active | ✅ |
| ST2 | Single deactivation (PUT `stts=I`) | Row Inactive | ✅ |
| ST3 | Bulk activation | **N/A — feature absent** (no endpoint; documented in gap analysis) | ✅ (verified absent) |
| ST4 | Bulk deactivation | **N/A — feature absent** | ✅ (verified absent) |

## Delete

| # | Scenario | Expected | Status |
|---|---|---|---|
| D1 | Soft delete live row | `DEL_YN='Y'`; audit `delete.detail`; downstream pointers untouched | ✅ |
| D2 | Delete unknown / wrong-tenant | `WBE2040` | ✅ |
| D3 | Bulk delete | **N/A — feature absent** (DN-8; `CHECKBOX` not seeded) | ✅ (verified absent) |

## Import (Excel)

| # | Scenario | Expected | Status |
|---|---|---|---|
| I1 | Preview valid file | rows + `completed_count`/`error_count`; all `valid:true` | ✅ |
| I2 | Required-field empty | row error `{field, code:"required"}` | ✅ |
| I3 | Code length < 3 / > 15 | error `length` | ✅ |
| I4 | Code bad chars | error `format` | ✅ |
| I5 | Duplicate code within file | error `dup_file` | ✅ |
| I6 | Duplicate vs live DB (Append) | error `dup_db` | ✅ |
| I7 | Bad header row | `WBE2042` | ✅ |
| I8 | > 500 rows | `WBE2041` | ✅ |
| I9 | Empty file / empty rows | `WBE_VALIDATION` | ✅ |
| I10 | Corrupt / non-Excel binary | `WBE2043` | ✅ |
| I11 | Numeric code cell coercion | `111` not `111.0` | ✅ |
| I12 | Import Append happy path | inserts non-dup rows; `inserted_count` correct; one audit row | ✅ |
| I13 | Import Append with DB dup | first failure → `WBE_VALIDATION` `"row N: …"`, atomic abort | ✅ |
| I14 | Import Replace happy path | soft-deletes all live, then inserts all; `replaced_count`/`inserted_count`; skips dup_db | ✅ |
| I15 | Invalid parent / hierarchy depth | **N/A — no hierarchy** (no parent column, no depth rule) | ✅ (verified absent) |

## Export

| # | Scenario | Expected | Status |
|---|---|---|---|
| E1 | Export with data | XLSX streamed; Type/Status localized labels; subheader `<count> …(s)` | ✅ |
| E2 | Export with filters/sort | mirrors the list query (`listAllForExport`) | ✅ |
| E3 | Export without data | XLSX with header + 0 data rows | ✅ |
| E4 | Comma / array / single status coercion | `coerceStatuses` handles all three | ✅ |

## Validation & error handling

| # | Scenario | Expected | Status |
|---|---|---|---|
| V1 | Detail 404 envelope | `WBE2040`, `trace_id` + `path` present | ✅ |
| V2 | Concurrent create (sequence race) | one succeeds; the loser → `WBE_DB` (no retry) | ⏳ (integration — F1) |
| V3 | Tenant isolation (wrong-tenant seqNo) | `WBE2040` (never leaks existence) | ⏳ (integration — F1) |
| V4 | View Setting not adopted for `vas-account` | no `SYS_SCR_OPTION_WECMS` seed (V156 lists it audit-slug-only); no popover, no lazy-init path | ✅ (verified absent) |
| V5 | Audit ledger row written per action | one `AUDIT_LOGS_INFM` row, `wsvc_id='vas-account'` | ✅ (unit, append verified) / ⏳ (end-to-end — F1) |

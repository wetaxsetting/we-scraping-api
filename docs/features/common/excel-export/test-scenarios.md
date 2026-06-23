# Excel Export — Test Scenarios

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

Behaviour-level scenarios derived from the code. Existing tests are listed in
[source-code-index.md](source-code-index.md); the scenarios below are the
contract each provider + the orchestrator must satisfy.

## Orchestrator (`ExcelExportService`)

| # | Scenario | Expected | Source |
|---|---|---|---|
| T1 | `screenId` has no registered provider | `WBE2010` (BusinessException, standard envelope) | `ExportableScreenRegistry.java:52-58` |
| T2 | Provider returns > 50,000 rows | `WBE2011`, args `(rows.size(), 50000)` | `ExcelExportService.java:88-90` |
| T3 | Request columns ∩ allow-list ∩ visibility = ∅ | `WBE2013` | `ExcelExportService.java:82-84` |
| T4 | Unexpected runtime before bytes flushed | `WBE2012` (response still uncommitted) | `ExcelExportService.java:139-141` |
| T5 | `IOException` mid-stream | `UncheckedIOException` (no JSON envelope once committed) | `ExcelExportService.java:127-134` |
| T6 | Request asks for a hidden column (`USE_YN='N'`) on a seeded screen | column dropped — **never leaks** into the workbook | `ExcelExportService.java:280` |
| T7 | Request includes `CHECKBOX` / `ETC` | dropped via `isSpecialFieldId` | `ExcelExportService.java:276` |
| T8 | Request includes a fieldId not in the allow-list | dropped silently | `ExcelExportService.java:277` |
| T9 | Duplicate fieldId in request | first kept, rest deduped | `ExcelExportService.java:275` |
| T10 | Unsaved popover reorder (request order ≠ persisted order) | emit in **request** order, after visibility filter | `ExcelExportService.java:270-282` |
| T11 | Export-only screen not seeded (`load` throws WBE2001) | visibility skipped; allow-list is the only bound | `ExcelExportService.java:251-257, :278-280` |
| T12 | DB seed uses legacy `BANK_NM` while allow-list has `BANK` | matched via `FieldIdAlias.resolve` at intersection | `ExcelExportService.java:262`; `FieldIdAlias.java` |
| T13 | `title` omitted | falls back to `fileName`, then `screenId` | `ExcelExportService.java:104-111` |
| T14 | filename with slashes / control chars | sanitized to space, capped at 120, `.xlsx` not double-appended | `ExcelExportService.java:370-382` |

## Writer (`ExcelWorkbookWriter`)

| # | Scenario | Expected | Source |
|---|---|---|---|
| T15 | `format: amount` | numeric cell, POI `#,##0.##`, right-aligned by default | `ExcelWorkbookWriter.java:264, :277-286` |
| T16 | `format: number` | numeric cell, POI `#,##0` | `:265, :288-296` |
| T17 | `format: date`, value `20260623` | rendered `2026-06-23` | `:298-301, :308-325` |
| T18 | `format: datetime`, value `20260623120000` | rendered `2026-06-23 12:00:00` | `:303-306` |
| T19 | unparseable amount string | degrades to text, never throws | `:277-282` |
| T20 | unknown / blank `format` | falls back to `TEXT` | `ExcelColumnFormat.java:31-38` |
| T21 | `AMOUNT` header with `ccyCd="VND"` | header suffixed ` (VND)` | `:242-252` |
| T22 | provider supplies `subHeaderRight` | row splits into left+right merged halves | `:131-144` |
| T23 | single `subHeader` only | one centered merged cell | `:145-151` |
| T24 | 50k-row export | bounded heap (SXSSF 100-row window, pooled styles) | `:99-101, :493` |

## Provider contract (per registered provider)

| # | Scenario | Expected |
|---|---|---|
| T25 | `fetch(request)` reuses the list service | export rows == list rows for the same filter+sort+tenant |
| T26 | unknown `sort.field` | falls back to legacy default ORDER BY, no SQL injection |
| T27 | result tenant scope | rows bounded by `TenantContext.currentBizKey()` |
| T28 | payment-result provider (`auditsErpExport=true`) | `recordErpExport` writes `ERP_TRNS_HSTR` `ERP_TYPE='W'` BEFORE the stream; failure is swallowed (download still succeeds) — covered by `ExcelExportServiceErpAuditTest` |
| T29 | `OverseasPaymentResultDetailExportProvider` without `filters.ovrsPymnNo` | `WBE_VALIDATION` (`OverseasPaymentResultDetailExportProvider.java:60-62`) |

## Notes

- T1–T14 / T22–T24 are orchestrator+writer behaviours; a dedicated orchestrator
  unit test was **not** found this pass (only `ExcelExportServiceErpAuditTest` for
  the tee path) — treat T1–T14 as the spec to add coverage against.
- Hidden-column-leak prevention (T6) is the highest-value security test — the
  request `columns[]` is attacker-controllable, and the only guard is the
  server-side intersection.

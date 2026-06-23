# Excel Import — Test Scenarios (Backend)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

Scenarios cover the **canonical** pattern (Purpose Settings, VAS Account). Existing test classes: `PurposeExcelServiceTest`, `VasAccountExcelServiceTest`, `VasAccountIntegrationTest`, `PurposeSettingsControllerTest` (see [source-code-index.md](source-code-index.md)). CoA / Contacts variant scenarios live in those features' test docs.

## Preview — reject paths

| # | Scenario | Expectation |
|---|---|---|
| P1 | Upload workbook with > 500 data rows | preview returns `WBE2041` (message has `{0}`=500) |
| P2 | Upload workbook whose header row does not match the template | `WBE2042` |
| P3 | Upload a corrupt / non-Excel / encrypted file | `WBE2043` |
| P4 | Upload a workbook with no sheets | `WBE2043` (or `no_sheet`-equivalent surfaced as unreadable) — ⚠️ confirm exact mapping |
| P5 | Header present but in KO / VI locale labels | accepted (locale-aware, whitespace-tolerant matching) |

## Preview — per-cell error reporting

| # | Scenario | Expectation |
|---|---|---|
| P6 | Row missing required Code | row `valid=false`, `errors[]` carries field=code; `errorCount` incremented |
| P7 | Code exceeds max length (Purpose 20 / VAS 15) | row error; cell flagged |
| P8 | VAS code violates `^[A-Za-z0-9_]+$` | row error |
| P9 | Two rows share the same Code (in-file duplicate) | both flagged duplicate (compared case-insensitively) |
| P10 | Code already exists in DB for the tenant | row flagged DB duplicate |
| P11 | Purpose parent not an in-file root and not a DB level-1 row | `parent_not_found` / `parent_not_root` |
| P12 | Mixed file (some valid, some invalid) | `completedCount` + `errorCount` sum to `totalCount` |

## Import — success + audit

| # | Scenario | Expectation |
|---|---|---|
| I1 | Import all-valid rows (Purpose) | `insertedCount` = row count; PKs via `wb_get_next_val`; **one** `AUDIT_LOGS_INFM` row, slug `purpose-settings` |
| I2 | Import all-valid rows (VAS Append `A`) | `insertedCount` set; seq via Java MAX+1; audit `refNo2="APPEND"` |
| I3 | Import VAS Replace (`R`) | all live tenant rows soft-deleted first, then inserted; `replacedCount` reflects removed; audit `refNo2="REPLACE"` |
| I4 | Import with an invalid `upldOptn` (not A/R) | bean-validation rejection (`@Pattern "^[AR]$"`) |
| I5 | Purpose parent/child ordering | two-pass commit: roots first, children resolve generated parent code |

## Import — re-validation & atomicity

| # | Scenario | Expectation |
|---|---|---|
| I6 | A row that passed preview now duplicates a row inserted between preview and import | import re-validates → `WBE_VALIDATION` with 1-based row index; **whole batch rolls back** (atomic) |
| I7 | Any single row fails mid-import | no partial commit (Purpose/VAS atomic) |
| I8 | Re-submit the exact same valid workbook (Append) | second submit fails on DB-duplicate (no idempotency key) |
| I9 | Re-submit the same Replace workbook | same end-state for the active set (Replace is effectively idempotent) |

## Authorization

| # | Scenario | Expectation |
|---|---|---|
| A1 | Caller lacking `settings-core-purpose` hits Purpose preview/import | denied (`@PreAuthorize @menuAuthz.canAccess(...)`) |
| A2 | Caller lacking `settings-core-vas-account` hits VAS endpoints | denied |

## Open / UNVERIFIED

- ⚠️ Exact `@Test` method names inside the listed test classes were not enumerated — confirm coverage of each scenario above against the real test source.
- ⚠️ P4 (no-sheet) → exact code mapping not re-verified at service level.
- CoA / Contacts (variant) scenarios: see those features' `test-scenarios.md`.

## Related

- [functional-specification.md](functional-specification.md) · [interface-specification.md](interface-specification.md) · [source-code-index.md](source-code-index.md) · [README.md](README.md)
- WEB twin: [`../../../../../wecms-web/docs/features/common/excel-import/test-scenarios.md`](../../../../../wecms-web/docs/features/common/excel-import/test-scenarios.md)

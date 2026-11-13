# WeInvoice Notification Bell (API) — Test Result

**Date:** 2026-11-12
**Type:** TEST RESULT
**Status:** PARTIAL
**Repository:** wecms-api (checked out as `we-scraping-api`)
**Related Record:** [../../features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md](../../features/addon-service/weinvoice/weinvoice-notifications-2026-11-12.md)

## Scope

Backend half of the header bell: `/api/v1/weinvoice/notifications` (list, unread count, read, read all),
`WeInvoiceNotificationService`, `WeInvoiceNotificationRepository` (aggregation SQL, read-state writes, V2 detection),
`NotificationKey`, `NotificationReadState`, code `WBE3037`, `V3__winv_notification_read.sql` and the optional
manual script `add-notification-time-indexes-2026-11-12.sql`.

## Environment

LOCAL — Windows 11, JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`), Gradle 8.14.4 `--offline`.
No database: SQL is verified as a query contract (fragments asserted on the generated text), JDBC is mocked.

## Code / Build Under Test

Branch: `sit` + working tree · Commit: NOT COMMITTED.

## Automated Tests

| Test / Command | Result | Evidence |
|---|---|---|
| `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*" --tests "com.kosign.wecms.config.EndpointAuthorizationCoverageTest"` | PASS | 190 tests, 0 failures, 0 errors, 0 skipped (25 classes) |
| — new `NotificationKeyTest` | PASS | 5 |
| — new `NotificationReadStateTest` | PASS | 5 |
| — new `WeInvoiceNotificationRepositoryTest` | PASS | 11 |
| — new `WeInvoiceNotificationServiceTest` | PASS | 10 |
| — new `WeInvoiceNotificationControllerTest` | PASS | 6 |
| — `EndpointAuthorizationCoverageTest` (allow-list + `WeInvoiceNotificationController`) | PASS | 2 |
| — `WeInvoiceBeanConstructionTest` | PASS | 2 — **failed on the first run** (`WeInvoiceNotificationRepository` had two constructors, none `@Autowired`: the app would not have started); fixed by marking the public constructor `@Autowired`, then green |

## Regression Tests

`firstBranchOfEveryVariantNamesEveryMappedColumn` (`WeInvoiceNotificationRepositoryTest`) pins a defect found in the diff
self-audit after the first green run (no test caught it): with `filter=action` the first `UNION ALL` branch was the GDT one, without column aliases, so the
by-name mapper would have failed on a real database. All 8 SQL variants are now checked for the 13 mapped names.

## Test Scenarios

| Scenario | Expected | Actual | Result |
|---|---|---|---|
| Id format per type, round trip of every server id | `<TYPE>\|<mst or ->\|<dir or ->\|<bucket>` | as expected | PASS |
| Client ids: markers, bad direction / MST / bucket, injection text, > 160 chars | rejected (never stored) | as expected | PASS |
| Read rule: own id; `*`; `*\|mst`; equal time; newer event; other MST; item without MST; newer of two markers | per record | as expected | PASS |
| SQL: hour buckets in UTC, owner + active MST scope, joins, `GROUP BY 2, 3, 4`, month list | fragments present, no `[1:3]` slice | as expected | PASS |
| SQL: upload / test-data exclusions, `email` / `email:*` split | fragments present | as expected | PASS |
| SQL: action scope | only error branches, no engine table | as expected | PASS |
| SQL: one MST | `:mst` bound, mailboxes without MST left out | as expected | PASS |
| SQL: unread path | `GREATEST(window, marker + 1 s)`, no `array_agg` | as expected | PASS |
| V2 missing | no `winv_mlbx_infm` in SQL; cached 5 min, re-checked, then remembered | as expected | PASS |
| Mapper | `+07:00` event → UTC dtm; 4 months → newest 3; error row takes stored dtm | as expected | PASS |
| Writes | DB clock; marker upsert `DO UPDATE`; id insert `DO NOTHING`; prune only per-id rows of the owner, > 31 days; empty list → no DB call | as expected | PASS |
| Filters | default `mst` without MST → `WBE3037`; foreign MST → `WBE3002` before any query; bad filter → IAE; `all` / `action` → every MST | as expected | PASS |
| Item shape | per type; `action=true` only for the two errors; `from_provider = count − from_email` | as expected | PASS |
| Paging | newest first, equal times by id, size 0 → 20, size 500 → 100, page past end → empty | as expected | PASS |
| `markRead` | duplicates, markers, foreign MST, garbage, `null` dropped | as expected | PASS |
| `markAllRead` | `*\|mst` after ownership check, `*` for null / blank, foreign → `WBE3002`, no write | as expected | PASS |
| Wire | snake_case envelope; 204 empty body for both writes; read-all without body; > 100 ids → `WBE_VALIDATION` | as expected | PASS |

## Database Verification

| Check | Result |
|---|---|
| V3 applied on any database | NOT RUN — manual DBA step (§14f); no SQL run by this work |
| Aggregation SQL executed / `EXPLAIN` on real data | NOT RUN — no database access in this task; covered only by the query-contract tests |
| Optional index script | NOT RUN — prepared only |

## Security / Permission Verification

Owner = JWT user (`CompanyAccessGuard.ownerId`); every query is bound to `:ownerId`; MST filter and read-all go
through `requireCompany` (`WBE3001` / `WBE3002`) before any query (unit-tested); client ids are parsed strictly and
must name one of the user's MSTs before they are stored. Controller is in the `EndpointAuthorizationCoverageTest`
allow-list with the other WeInvoice controllers.

## Performance / Concurrency Verification

NOT RUN against data. By design: one aggregation statement per call; the 60-s poll (`unread-count`) scans only
events newer than each MST's read-all marker; optional time indexes prepared.

## Existing Failures

None in the scope run.

## New Failures

None (the bean-construction failure of the first run was fixed in this work and is green).

## Not Tested

- Real PostgreSQL execution of the aggregation (type resolution of the `UNION ALL`, `to_timestamp` with the session
  time zone, plan with / without the indexes) — needs V3 on a database.
- Web integration (bell, panel, polling) — web repository, separate record.
- Full `./gradlew test` — not run; scope run only (13 pre-existing failures documented for the full suite).

## Final Result

PARTIAL — unit / contract scope PASS; database and end-to-end verification pending V3.

## Recommendation

READY FOR DBA (V3, optional index script) · READY FOR MANUAL COMMIT (code).

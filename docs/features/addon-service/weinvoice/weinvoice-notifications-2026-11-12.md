# WeInvoice: Notification Bell per MST

**Date:** 2026-11-12
**Type:** FEATURE
**Current Work Classification:** FEATURE
**Status:** READY FOR DBA
**Approval Status:** APPROVED (developer, 2026-11-12: "Build to this layout") · **Implementation Status:** COMPLETE (API) · **Test Status:** PASS (unit / query contract; DB not run)
**Repository:** wecms-api (`we-scraping-api`) — shared contract for the web

## Purpose

The header bell (spec 01 "Khung ứng dụng": "chuông thông báo"; behaviour left to Dev) shows what the engine collected per
MST — new invoices from GDT, XML downloaded, original PDFs obtained — and what needs action (GDT password refused,
mailbox login failed).

## Target Behavior (approved layout)

Bell with the unread count (all of the user's MSTs). Panel: title "Thông báo", "Đánh dấu đã đọc tất cả", filters
**MST đang làm việc** (default) · **Tất cả MST** · **Cần xử lý**; items with icon, text, MST + detail line, relative time,
unread highlight; click → mark read + go to the target; footer "Xem tất cả thông báo" (same list, larger page). Web polls
every 60 s. One item per (MST, type, direction, UTC hour) — the PDF worker writes hundreds of runs a day, so counts
are summed per hour instead of one item per run. Window: last 30 days. The engine is not changed.

## Contract

### Sources (engine tables, read only; `{eng}` schema)

| Type | Rows | Bucket time | Detail |
|---|---|---|---|
| `NEW_INVOICES` | `invoices` by `first_seen_at`, per `direction` (`mua`/`ban`); **exclude** `list_raw->>'nguon' IN ('upload','test_data')` (the user did those) | `first_seen_at` | `count`, `direction`, `months[]` (distinct yyyy-MM of `ngay_lap`, max 3) |
| `XML_READY` | `invoice_xml` by `fetched_at` joined to `invoices` | `fetched_at` | `count` |
| `PDF_READY` | `invoice_pdf` by `fetched_at` joined to `invoices`; `source_id` `email` / `email:*` = from email, `upload`/`test_data` excluded, else provider | `fetched_at` | `count`, `from_provider`, `from_email` |
| `GDT_LOGIN_ERROR` | `{app}.winv_cmpn_infm` of the owner with `conn_stts IN ('ERROR','EXPIRED')`, `del_yn='N'` | `conn_chk_dtm` (UTC 14-digit) | `message` (`conn_msg`) — action item |
| `MAILBOX_ERROR` | `{app}.winv_mlbx_infm` of the owner with `conn_stts IN ('ERROR','EXPIRED')`, `del_yn='N'` (table may not exist yet → none) | `mod_dtm` else `reg_dtm` | `mailbox_id`, `address`, `status`, `message` — action item, `company_mst` = `cmpn_mst` (null for scope ALL) |

Only MSTs the owner holds in `winv_cmpn_infm` (`del_yn='N'`).

### Item key and read state

- `id` = `<TYPE>|<mst or ->|<direction or ->|<bucket>`; bucket = UTC `yyyyMMddHH` for counts, the source dtm for errors
  (a new failure gets a new id and is unread again).
- New table `{app}.winv_ntfc_read` (`V3__winv_notification_read.sql`): `owner_id`, `ntfc_key` varchar(160), `read_dtm`
  varchar(14) UTC; PK (owner_id, ntfc_key). "Read all" writes marker keys `*` (all MSTs) or `*|<mst>` with the time:
  an item is read if its own key exists or its time ≤ the applicable marker.

### Endpoints (`/api/v1/weinvoice`, JWT, owner-scoped, snake_case wire)

- `GET /notifications?company_mst=&filter=mst|all|action&page=&size=` (default size 20, max 100) →
  `{items: NotificationItem[], unread_count, total_count, page, size}`; `filter=mst` needs `company_mst`;
  `unread_count` = unread of the same filter.
  `NotificationItem`: `id, type, company_mst, company_name, direction, count, from_provider, from_email, months[],
  mailbox_id, address, message, at (ISO UTC, latest event in the bucket), read, action (true for the two error types)`.
- `GET /notifications/unread-count` → `{unread_count}` across all the owner's MSTs (cheap; the bell polls it).
- `POST /notifications/read` `{ids: []}` → 204 (max 100 ids; unknown ids ignored).
- `POST /notifications/read-all` `{company_mst?}` → 204 (marker `*|<mst>` or `*`).

### Web

Bell in `components/layout/Header.tsx` (replace the static red dot); panel per the layout; targets:
`NEW_INVOICES` → `/invoices/purchase` or `/invoices/sales` with the working MST switched to the item's MST (and the first
month as filter if the list supports it); `XML_READY` / `PDF_READY` → the same list for that MST; `GDT_LOGIN_ERROR` →
`/sources/gdt`; `MAILBOX_ERROR` → `/sources/mailbox`. Mock mode: deterministic fictional items. VI / EN / KO.

## Database Impact

BOTH — FLYWAY MIGRATION (required) + optional MANUAL DBA SCRIPT.

| Artifact | What | Executor | Order |
|---|---|---|---|
| [`V3__winv_notification_read.sql`](../../../../src/main/resources/db/weinvoice/V3__winv_notification_read.sql) | New table `weinvoice.winv_ntfc_read (owner_id varchar(100), ntfc_key varchar(160), read_dtm varchar(14))`, PK `(owner_id, ntfc_key)`, comments; GRANT note for split roles (`SELECT, INSERT, UPDATE, DELETE` to the app role; the engine never uses it). DDL, `CREATE TABLE IF NOT EXISTS` | DBA OR DEV TEAM | After V1; **before** deploying this API (every notification endpoint reads it — without it they answer `WBE_DB`) |
| [`add-notification-time-indexes-2026-11-12.sql`](../../../sql/2026/add-notification-time-indexes-2026-11-12.sql) | Optional: `invoices (company_mst, first_seen_at)`, `invoice_xml (fetched_at)`, `invoice_pdf (fetched_at)` with `CREATE INDEX CONCURRENTLY IF NOT EXISTS` — **outside any transaction**; pre/post-check on `pg_indexes` / `pg_index.indisvalid`; rollback `DROP INDEX CONCURRENTLY` | DBA OR DEV TEAM | Any time; the API works without it |

Rollback of V3: dropping the table loses only read state (everything turns unread) — ROLLBACK REQUIRES DBA REVIEW all
the same. Verification: `SELECT to_regclass('weinvoice.winv_ntfc_read')` is not null. Writes the API makes to the
table: insert ids / upsert markers, and a prune `DELETE` of the owner's own per-id rows older than 31 days (never
markers, never another owner's rows).

## Database Execution Status

| Environment | Required | Executor | Status | Executed Date | Verification |
|---|---|---|---|---|---|
| LOCAL (engine DB) | V3 yes, indexes optional | DEV TEAM | PENDING | - | - |
| SIT | V3 yes, indexes optional | DBA OR DEV TEAM | PENDING | - | - |
| UAT | V3 yes, indexes optional | DBA TEAM | NOT STARTED | - | - |
| PROD | V3 yes, indexes optional | DBA TEAM | NOT STARTED | - | - |

## Implementation

API (2026-11-12). Files (under `src/main/java/com/kosign/wecms/domain/weinvoice/` unless stated):

| File | Role |
|---|---|
| `controller/WeInvoiceNotificationController.java` | 4 endpoints; the two writes return `ResponseEntity.noContent()` |
| `service/NotificationService.java` | filters + ownership, read state, item shape, sort (newest first, then id), paging (20 / max 100) |
| `repository/NotificationRepository.java` | one aggregation statement (`bucketSql`), read rows, `markRead` / `markAll` (+ prune), `to_regclass` check of V2 |
| `common/NotificationKey.java` | id format, strict parse of client ids, markers, `mua`/`ban` → `purchase`/`sales`, type → `action` |
| `common/NotificationReadState.java` | the read rule (own id, or `at` ≤ the newer of the two markers) |
| `dto/NotificationItem`, `NotificationListResponse`, `UnreadCountResponse`, `NotificationReadRequest`, `NotificationReadAllRequest` | wire records |
| `src/main/resources/messages*.properties` (EN / VI / KO) | `WBE3037` |
| `src/test/.../config/EndpointAuthorizationCoverageTest.java` | allow-list entry `WeInvoiceNotificationController` |
| `V3__winv_notification_read.sql`, `docs/sql/2026/add-notification-time-indexes-2026-11-12.sql` | see Database Impact |

How it works:

- **Aggregation in SQL.** CTE `own` = the owner's active rows of `winv_cmpn_infm` (optionally `= :mst`). Branches
  joined with `UNION ALL`: `NEW_INVOICES` (`invoices.first_seen_at`), `XML_READY` (`invoice_xml.fetched_at` joined to
  `invoices`), `PDF_READY` (`invoice_pdf.fetched_at` joined to `invoices`) — each grouped by MST, direction and
  `to_char(date_trunc('hour', t AT TIME ZONE 'UTC'), 'YYYYMMDDHH24')`, `at = max(t)`; `GDT_LOGIN_ERROR` from `own`;
  `MAILBOX_ERROR` from `winv_mlbx_infm` only when `to_regclass` finds it (positive answer cached for the process,
  negative re-checked every 5 min). Window `now() - 30 days` (errors: the same bound as a 14-digit dtm).
  `filter=action` builds only the two error branches.
- **Read state in Java** (`NotificationReadState`) over the owner's rows of `winv_ntfc_read` — one rule for the list
  and the badge, unit-tested.
- **Cheap badge.** `unread-count` uses the "unread-only" variant: each MST's event window starts at
  `GREATEST(now() - 30 days, marker + 1 s)` (marker = the newer of `*` and the MST marker), months are not aggregated.
  Buckets left out are exactly those the read rule calls read (all their events ≤ marker), so after the first
  "read all" a poll scans only newer events. 2 queries per poll (+1 `to_regclass` while V2 is missing, at most every
  5 min).
- **Clocks.** Markers and read times are written with the database clock (`to_char(now() AT TIME ZONE 'UTC', …)`),
  the clock of the engine's `first_seen_at` / `fetched_at`. Comparison in whole seconds.
- **Client ids** are parsed strictly (`NotificationKey.parse`) and kept only when the MST is the user's (or `-` for a
  mailbox without MST); markers can only be written through read-all.

### Deviations from the contract (decided during implementation)

1. **`MAILBOX_ERROR` bucket = `<dtm>.<mailbox id>`**, not the bare dtm: two mailboxes of one owner (often both with
   MST `-`) failing within the same second would otherwise share an id. Ids stay opaque for the web.
2. **`status` added to `NotificationItem`** (`ERROR` / `EXPIRED`, error types): the Sources table lists it for
   `MAILBOX_ERROR`, the item field list did not; filled for both error types.
3. **`XML_READY` also leaves out invoices created by an upload / test data** (`list_raw.nguon`), same rule as
   `NEW_INVOICES`: their XML came from the user's own upload. An XML uploaded for an invoice that came from GDT
   cannot be told apart cheaply (only per-line `invoice_details.nguon`) and still counts.
4. **`WBE3037` travels as a house business error** (HTTP 200, `status.error = true`), like every `WBE30xx` — not
   HTTP 400. An unknown `filter` value is HTTP 400 `WBE_VALIDATION` (house mapping of `IllegalArgumentException`).
5. **`filter=mst` does not list mailboxes serving every MST** (`company_mst = null`): "read all" of one MST writes the
   MST marker, which cannot cover an item without MST, so it would stay unread in that view. They show under
   `all` / `action`.
6. **`months[]` newest first** (the contract does not say; the web takes the first as filter → the latest month).
7. **`company_mst` is ignored for `filter=all` / `action`** (not validated).
8. **Per-id read rows older than 31 days are pruned** on each write (their buckets have left the window).

## Verification

### Automated Tests

- Test: `./gradlew test --offline --tests "com.kosign.wecms.domain.weinvoice.*" --tests "com.kosign.wecms.config.EndpointAuthorizationCoverageTest"`
- Result: PASS — 190 tests, 0 failures (37 new in 5 classes). `WeInvoiceBeanConstructionTest` caught a startup
  defect on the first run (two constructors, none `@Autowired`), fixed.

### Test Scenarios

- [test-scenarios.md](test-scenarios.md) N1–N14 added. Unit / contract level PASS; DB-level parts NOT RUN.

### Regression Review

- Areas checked: no existing class changed except the coverage-test allow-list and the three message bundles (one
  line each). Self-audit fixed the missing column aliases of the GDT branch (the first branch for `filter=action`),
  pinned by `firstBranchOfEveryVariantNamesEveryMappedColumn`.

### Manual Verification

- Environment: none. Result: NOT RUN — no database access in this task; V3 not applied anywhere.

## Test Evidence

Test result:
[WeInvoice Notification Bell (API) — Test Result](../../../testing/results/weinvoice-notifications-api-test-result-2026-11-12.md)

Final test result:    PARTIAL (unit / contract PASS; DB NOT RUN)
Regression coverage:  PASS

## Result

Code: COMPLETE · Tests: PASS (scope) · V3: PREPARED, not executed · index script: PREPARED (optional)
Overall: READY FOR DBA / READY FOR MANUAL COMMIT (API). Web half: separate repository.

## Known Limitations / Follow-up

- The aggregation SQL has never run on PostgreSQL — after V3, check one `filter=all` call and one `unread-count` on
  real engine data (and the plan with / without the optional indexes).
- A mailbox in `ERROR` that the engine re-scans gets a new `mod_dtm` each time (`update_mailbox_scan` writes it even
  when the status is unchanged) → a new id, unread again after each failing scan.
- An event committed with a timestamp earlier than a marker written meanwhile (long engine transaction) counts as read.
- The 30-day window applies to errors too: a GDT error older than 30 days that is still unresolved drops out of the
  bell (the Connections screen still shows it).

## Cross-Repository Check (2026-11-12)

- Wire `direction`: backend sends `purchase` / `sales`; the web expected `mua` / `ban` and would have shown every sales
  item as generic and routed it to the purchase list → **fixed in the web** (`toDirection` accepts both; test added).
- `read` / `read-all` answer 204 without an envelope — the web's `wiRequest` accepts 204 (changed in the web half).
- Extra `status` field and the `MAILBOX_ERROR` id format: the web ignores `status` and treats ids as opaque — MATCH.
- **Aggregation SQL run on the real engine DB, read only** (all 8 `bucketSql` variants: one MST / all, all types / action
  only, with / without unread filter; `winv_ntfc_read` stood in by a session TEMP table because V3 is not applied yet):
  all OK, 0.00–0.19 s each; e.g. one owner, MST 1201496252: 249 buckets in 30 days; all MSTs: 557; action-only: 0 (no MST
  or mailbox in error for that owner).
- Re-run after the web fix: backend weinvoice package + `EndpointAuthorizationCoverageTest` 190 tests, 0 failures; web
  `vitest components lib/api/weinvoice` 174 passed, `tsc` 0 source errors, eslint clean.

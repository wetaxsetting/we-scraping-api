# VAS Account — Test Plan

Slug: `vas-account`. Spans backend (Spring) + frontend (Next.js) + DB. The plan
mirrors the four-layer pyramid the refactor already uses for Auth and Banking:
**Unit → Integration → Contract → Frontend**.

---

## 1. Unit tests (backend — JUnit 5 + Mockito)

### `VasAccountServiceTest`

| # | Scenario | Setup | Expect |
|---|---|---|---|
| 1 | List returns mapped rows | Mock repo to return 3 entities | Returns `VasAccountListResponse` with 3 `VasAccountRow`s; each carries `vasAcctTypeKey` + `sttsKey` |
| 2 | List empty when no statuses | `statuses = Set.of()` | Short-circuits — repo never called; returns empty list |
| 3 | Detail throws `WBE2014` on missing row | Mock repo returns empty Optional | `BusinessException("WBE2014")` |
| 4 | Detail enforces tenant | Existing row but different `biz_key` in tenant context | `BusinessException("WBE2014")` (single envelope merges not-found + wrong-tenant) |
| 5 | Create succeeds | Valid request, no duplicate | Calls `seqGen.next(bizKey)`; persists with `delYn='N'`; emits one `CREATE` audit row |
| 6 | Create throws on duplicate code | `existsByBizKeyAndVasAcctCdAndDelYn` returns true | `BusinessException("WBE0003")`; no persistence, no audit row |
| 7 | Update with same code | Existing row, `vas_acct_cd` unchanged in request | No dedup check; persists changes; emits `UPDATE` audit with old-vs-new diff |
| 8 | Update with changed code — no clash | Existing row, new `vas_acct_cd` not in use | Dedup check runs against new code; persists |
| 9 | Update with changed code — clash | New `vas_acct_cd` already in use | `BusinessException("WBE0003")` |
| 10 | Soft delete | Existing row | Sets `delYn='Y'`; emits `DELETE` audit row |
| 11 | Sort field whitelist | `sortField="VAS_ACCT_CD"` | `Sort.by(asc("vasAcctCd"))` passed to repo |
| 12 | Sort field unknown | `sortField="MALICIOUS_SQL"` | Falls back to default sort; no exception thrown (defence-in-depth) |
| 13 | Map type enum | DB row `vas_acct_type='1'` | DTO `vasAcctType="1"`, `vasAcctTypeKey="settings.vas_account.type.credit"` |
| 14 | Map type enum — unknown code | DB row `vas_acct_type='9'` (corrupt) | DTO `vasAcctTypeKey=null`; row still returned |
| 15 | Pagination — page 0 default | `page=0, size=20`, repo has 25 rows | Returns 20 rows; `pageNumber=0`, `pageSize=20`, `totalCount=25`, `totalPages=2` |
| 16 | Pagination — second page | `page=1, size=20`, repo has 25 rows | Returns 5 rows; `pageNumber=1`, `totalPages=2` |
| 17 | Pagination — size clamp | `size=500` | Clamped to `MAX_PAGE_SIZE=100`; `pageSize=100` echoed back |
| 18 | Pagination — size ≤ 0 | `size=0` | Falls back to `DEFAULT_PAGE_SIZE=20`; `pageSize=20` echoed back |
| 19 | Pagination — negative page | `page=-3` | Clamped to `0`; no exception |
| 20 | Pagination — empty result | filter matches no rows | Returns `list=[]`, `totalCount=0`, `pageNumber=0`, `totalPages=0` |

### `VasAccountExcelServiceTest`

| # | Scenario | Expect |
|---|---|---|
| 1 | Preview rejects > 500 rows | `WBE_VAS_EXCEL_TOO_MANY_ROWS` |
| 2 | Preview rejects bad header | `WBE_VAS_EXCEL_HEADER_INVALID` with offending cell name |
| 3 | Preview returns per-row errors map | 2 valid + 1 invalid → `tot_cnt=3, suc_cnt=2, err_cnt=1`; errors map populated for row 3 |
| 4 | Preview duplicate against live row | Row with `vas_acct_cd` matching a `del_yn='N'` row → error `WBE0003` in row's `errors.vas_acct_cd` |
| 5 | Preview does NOT trigger DB writes | Mock repo verifies only reads | No `save()` / `update*()` calls |
| 6 | Import `A` append happy path | 5 valid rows, none duplicate | Inserts 5; emits ONE `EXCEL_IMPORT_APPEND` audit row with `inserted_count=5` |
| 7 | Import `A` race-safe dedup | Preview passed, but a race-inserted row appeared before commit | First conflict throws `WBE0003`; transaction rolls back; no rows inserted |
| 8 | Import `R` replace happy path | 5 rows + 3 existing live rows for tenant | All 3 existing soft-deleted; 5 new inserted; one `EXCEL_IMPORT_REPLACE` audit row |
| 9 | Import `R` rolls back on insert failure | Replace mode; one row violates constraint mid-batch | Soft-deletes rolled back; tenant data unchanged |
| 10 | Import rejects unknown `upld_optn` | `upld_optn="X"` | `WBE_VALIDATION` (caught by `@Pattern`) |

### `VasAccountSortFieldTest`

| # | Scenario | Expect |
|---|---|---|
| 1 | `parse("VAS_ACCT_CD")` | `VAS_ACCT_CD.entityProperty() == "vasAcctCd"` |
| 2 | `parse("")` | `null` |
| 3 | `parse(null)` | `null` |
| 4 | `parse("foo")` | `null` (lenient — no throw) |
| 5 | Every `SORT_YN='Y'` seed FIELD_ID has a constant | enumerate `SYS_SCR_OPTION_WECMS` seed rows for `web_svc_id='vas-account'` and assert each appears as an enum value — guard against drift |

### `VasAcctSeqNoGeneratorTest`

| # | Scenario | Expect |
|---|---|---|
| 1 | First call for new tenant | Returns 1 |
| 2 | Subsequent call | Returns `MAX + 1` |
| 3 | Concurrent writers (same tenant) | One transaction succeeds, the other retries cleanly under the existing isolation level (or follow-up: pessimistic lock) — documented behaviour |

### `VasAccountControllerTest` (slice — `@WebMvcTest`)

| # | Scenario | Expect |
|---|---|---|
| 1 | GET list unauthenticated | 401 + `WBE1009` envelope |
| 2 | GET list authenticated | 200 + `data.rec` + `data.total_count` |
| 3 | POST create with missing required field | 200 + `WBE_VALIDATION` envelope |
| 4 | DELETE non-numeric path | 200 + `WBE_VALIDATION` (type mismatch) |
| 5 | Path param mapping | `vasAcctSeqNo` round-trips through controller correctly |
| 6 | Tenant scope | Controller never reads `biz_key` from the request body or query — service derives from `TenantContext` |

---

## 2. Integration tests (Spring `@SpringBootTest` against the local DB)

> **Status — deferred to a follow-up MR (audit F1).** The skeleton
> class [`VasAccountIntegrationTest`](../../src/test/java/com/kosign/wecms/domain/setting/vasaccount/VasAccountIntegrationTest.java)
> ships in the feature MR with every scenario in the matrix below
> declared as a `@Disabled` `@Test` method. The class header explains
> the blocking infrastructure (no Testcontainers dependency in
> `build.gradle`; `application-test.yml` uses `ddl-auto=create-drop`
> with Flyway disabled; no JWT mock profile) and the activation plan
> (one infra MR: add `testcontainers:postgresql` + `:junit-jupiter` to
> `build.gradle`, add a shared `@PostgresIntegrationTest` meta-annotation
> with Flyway-migration bring-up, add a `TestSecurityConfig` or
> `@WithMockUser(authorities="ROLE_ADMIN")` so MockMvc calls don't 401,
> then drop the per-method `@Disabled` markers).
>
> Until that infra lands, the unit-level coverage in §1 plus
> `VasAccountExportProviderTest` carry the bulk of the behavioural
> contract. The matrix below remains the definition-of-done for the
> activation MR — every scenario must have a green `@Test` method.

The refactor does NOT use Testcontainers (per CLAUDE.md "Not currently in `build.gradle`"). Integration tests run against the developer's local PostgreSQL — `application-test.yml` profile.

### Happy-path matrix

| # | Test | Verifies |
|---|---|---|
| 1 | `list_returns_seeded_rows` | After Flyway `V{N+1}` seed, GET list for `VN_VNM0004_000001` returns ≥ 1 row mapped correctly |
| 2 | `list_filters_by_status` | Inserts 1 ACTIVE + 1 INACTIVE; `?stts=A` returns 1, `?stts=I` returns 1, `?stts=A&stts=I` returns both, no `stts` returns both |
| 3 | `list_filters_by_search_wd` | Inserts row with `vas_acct_nm="Tiền mặt"`; `?search_wd=tiền` returns it (ILIKE) |
| 4 | `crud_round_trip` | Create → detail → update → delete → detail-404. Each step round-trips through real Hibernate + JDBC |
| 5 | `tenant_isolation` | Tenant A creates a row; tenant B's JWT cannot read / update / delete it (returns `WBE2014`) |
| 6 | `soft_delete_does_not_cascade` | Manually insert an `acct_mstr` row referencing the VAS seq_no, then delete the VAS row. `acct_mstr` still readable (no FK in DB; legacy parity verified) — would be a regression if a future migration adds a CASCADE FK |
| 7 | `excel_preview_happy_path` | Upload a 10-row valid sheet → `tot_cnt=10, suc_cnt=10, err_cnt=0` |
| 8 | `excel_preview_500_cap` | Sheet with 501 rows → `WBE_VAS_EXCEL_TOO_MANY_ROWS` |
| 9 | `excel_preview_bad_header` | Sheet with garbage header row 2 → `WBE_VAS_EXCEL_HEADER_INVALID` |
| 10 | `excel_import_append_happy` | Preview-validated rows posted; rows appear via GET list |
| 11 | `excel_import_replace_replaces` | Existing 3 rows; import `R` with 5 new rows; old 3 are `del_yn='Y'`, new 5 are `del_yn='N'` |
| 12 | `audit_emits_one_row_per_action` | For each of CREATE / UPDATE / DELETE / EXCEL_IMPORT_APPEND / EXCEL_IMPORT_REPLACE, exactly one `AUDIT_LOGS_INFM` row with `wsvc_id='vas-account'` |

### Tenant negatives

| # | Test | Verifies |
|---|---|---|
| 13 | `list_does_not_leak_cross_tenant` | Tenant A's list never includes tenant B's rows |
| 14 | `export_does_not_leak_cross_tenant` | Excel export run for tenant A contains only tenant A rows |
| 15 | `update_with_wrong_tenant_jwt` | Returns `WBE2014` (not a cross-tenant disclosure) |

### View Setting integration

| # | Test | Verifies |
|---|---|---|
| 16 | `view_setting_lazy_init` | First GET `/api/v1/screen-options/vas-account/default` for a fresh user inserts user rows mirroring the sys seed |
| 17 | `view_setting_save_round_trip` | PUT with reordered + toggled columns; subsequent GET reflects the changes |
| 18 | `view_setting_reset` | DELETE resets the user rows back to the sys defaults |
| 19 | `export_intersects_view_setting` | Hide `STTS` via View Setting; export response does not include the `STTS` column even when the request asks for it |

---

## 3. Contract tests (wire shape)

The refactor doesn't currently ship Spring Cloud Contract — wire-shape stability is verified via `@JsonTest`-style serialization round-trips plus the cross-stack TS↔Java type alignment check.

| # | Test | Verifies |
|---|---|---|
| 1 | Wire JSON for `VasAccountRow` ships every field in lowercase snake_case | `vas_acct_seq_no`, `vas_acct_cd`, `vas_acct_nm`, `vas_acct_enm`, `vas_acct_type`, `vas_acct_type_key`, `stts`, `stts_key`, `cont`, `mod_id`, `mod_dtm` all present, no UPPERCASE |
| 2 | `vas_acct_seq_no` is a string on the wire | JSON serialization produces `"123"`, not `123` (Jackson `@JsonFormat(shape=STRING)` on the entity OR a service-side toString in the mapper) |
| 3 | Error envelope carries `timestamp`, `path`, `trace_id` | Throw `WBE0003`; response body matches the standard error envelope shape |
| 4 | TS wire types match Java DTOs | A small TS-side script enumerates `ApiVasAccountRow` properties and compares against the Java record components — fails CI if either side drifts (existing tooling for Banking already does this; extend to VAS) |

---

## 4. Frontend tests (Next.js — `@testing-library/react` + Vitest)

### `useVasAccountList` (hook)

| # | Test | Verifies |
|---|---|---|
| 1 | Empty statuses set → no fetch | Mock Axios; assert no call when `statuses.size === 0` |
| 2 | Fetch on mount | Calls Axios with the right query string (including `page=0&size=20` defaults) |
| 3 | Map snake_case → camelCase | Mocked response `{ list: [{ vas_acct_seq_no: "1", … }], total_count: 1, page_number: 0, page_size: 20, total_pages: 1 }` → hook returns `{ rows: [{ vasAcctSeqNo: "1", … }], totalCount: 1, pageNumber: 0, pageSize: 20, totalPages: 1 }` |
| 4 | Status enum mapping | `stts: "A"` → `status: "ACTIVE"`; `stts: "I"` → `status: "INACTIVE"` |
| 5 | Error path | Axios rejects → hook surfaces `ApiCallError` with code + traceId |
| 6 | Page change triggers refetch | Change `page` from 0 → 1; assert one new Axios call with `page=1` |
| 7 | Page size change resets to first page | Change `size` from 20 → 50; assert page resets to 0 |

### `VasAccountTable` (component)

| # | Test | Verifies |
|---|---|---|
| 1 | Renders flat rows when no parents share prefixes | 3 rows with distinct prefixes → 3 top-level rows |
| 2 | Renders nested rows when prefix matches | rows `["111", "1111", "1112"]` → "111" parent with "1111" + "1112" indented children |
| 3 | Empty state | rows=[] → renders the "No Data Found" placeholder using `t("settings.vas_account.empty")` |
| 4 | Status badge color | ACTIVE → `wb-status-approved-*`; INACTIVE → `wb-status-rejected-*` (pending DN-7) |
| 5 | Edit/Delete menu opens | Clicking the action icon shows Edit + Delete in a popover; outside-click dismisses |

### `VasAccountAddDialog` / `VasAccountEditDialog` (component)

| # | Test | Verifies |
|---|---|---|
| 1 | Required fields enforced | Submitting empty form shows Zod errors for `vasAcctCd`, `vasAcctNm`, `vasAcctType`, `stts` |
| 2 | `vasAcctCd` regex | Submitting `"ab"` fails (`min=3`); `"ab!"` fails (`@Pattern`) |
| 3 | Server `WBE0003` shows in `useDialog` error | Mock POST rejection with `WBE0003` → assert `dialog.open({ type:"error", errorCode:"WBE0003" })` was called |
| 4 | Successful create closes dialog and refetches list | Mock 200 → dialog closes; `refetch` hook fired |
| 5 | Edit loads detail then prefills form | Open Edit with `seqNo="123"` → GET detail called once → form values populated |

### `VasAccountExcelUploadDialog` (component)

| # | Test | Verifies |
|---|---|---|
| 1 | File picker → preview step | Upload a mocked 3-row valid file → preview grid renders 3 rows |
| 2 | Preview row error renders | Mocked preview row with `errors: { vas_acct_cd: { code:"WBE0003", … } }` → row highlighted; cell shows the message |
| 3 | Replace mode shows confirmation dialog | Click Save with `upldOptn="R"` → `useDialog({ type:"confirmation" })` fired; only on confirm does the import POST run |
| 4 | Import success refreshes list | Mock 200 → close upload dialog; assert list `refetch` called |

### View Setting integration

| # | Test | Verifies |
|---|---|---|
| 1 | Popover opens from toolbar list-icon | Click → popover visible; columns rendered match the GET response |
| 2 | Hiding `STTS` removes the column from the table | Toggle `STTS.useYn='N'` → Save → table re-renders without STTS column |
| 3 | Reset restores defaults | Click Reset → DELETE called → popover columns match the sys seed |

### Accessibility

| # | Test | Verifies |
|---|---|---|
| 1 | Tab order through filter row | Search input → Active → Inactive → toolbar buttons |
| 2 | Dialog focus trap | Opening Add dialog moves focus inside; Tab cycles within |
| 3 | Status checkboxes have labels | `getByLabelText("Active")` finds the input |

---

## 5. Manual smoke tests (developer laptop, post-MR)

Before declaring the MR ready for review:

1. Log in as `testuser` against tenant `VN_VNM0004_000001` (the only seeded tenant with real VAS data).
2. Navigate to `/settings/vas-account`. Confirm:
   - Table loads with the V{N+1} seeded rows.
   - "Active" + "Inactive" checkboxes both ticked → all rows visible.
   - Untick both → empty table with "No Data Found".
3. Click "Add VAS Account" → fill in a new code + name → Save. New row appears in the table.
4. Try to add a duplicate `vas_acct_cd` → error dialog shows `WBE0003`.
5. Click the action menu on a row → Edit → change the name → Save. Row updates.
6. Click the action menu → Delete → Confirm. Row disappears.
7. Click "Excel Upload" → upload a valid sheet → preview shows rows → Append → confirm count.
8. Try Replace mode → confirmation dialog appears → confirm → table now contains only the uploaded rows.
9. Click the toolbar list-icon (View Setting) → hide `STTS` → Save → table re-renders without `STTS`.
10. Click Excel Export → XLSX downloads → confirm only visible columns are in the workbook.
11. Confirm in DBeaver that every action emitted exactly one row in `AUDIT_LOGS_INFM` with `wsvc_id = 'vas-account'`.

---

## 6. Out-of-scope tests (deferred)

- Load / performance tests beyond the 500-row Excel cap — not in R&D scope.
- Penetration tests / SAST / DAST — refactor is local-only; security review runs at the project-wide gate, not per-feature.
- Locale `km` translation coverage — out of scope per the refactor enum policy.
- Bank-account-master regression suite — that suite owns the FK-style `acct_mstr.vas_acct_seq_no` linkage and must be updated when the bank-account refactor lands; not blocked on VAS Account.

# Purpose — Test Scenarios (Backend)

SIT/UAT scenarios + the existing JUnit coverage map. Frontend scenarios:
[wecms-web/docs/features/settings/purpose/test-scenarios.md](../../../../../wecms-web/docs/features/settings/purpose/test-scenarios.md).

---

## 1. SIT / UAT scenarios

### Search

| # | Scenario | Expected |
|---|---|---|
| S1 | List with no keyword | Active level-1 rows (with children nested), ordered by `disp_ord`; reserved system codes absent |
| S2 | Search by name keyword | Only rows whose `use_usag_nm` contains the keyword (case-insensitive) |
| S3 | Search by code | (No dedicated code filter — keyword matches name only; code lookup is the detail endpoint) |
| S4 | Search by parent | Parent row returned with its matching children nested |

### Create

| # | Scenario | Expected |
|---|---|---|
| C1 | Valid create (level-1) | 200; row persisted with generated `use_usag_cd`, `disp_ord = MAX+1`, `use_yn='Y'`; audit row written |
| C2 | Valid create (level-2 under active parent) | 200; `parent_use_usag_cd` set; appears nested under the parent |
| C3 | Duplicate `hand_use_usag_cd` | `WBW1152` |
| C4 | Duplicate `use_usag_nm` | `WBW1138` |
| C5 | Invalid parent (not found / soft-deleted) | `WBE_VALIDATION` (`parent_not_found` semantics) |
| C6 | Parent is itself a level-2 row | `WBE_VALIDATION` (no 3-level nesting) |
| C7 | Missing required field | `WBE_VALIDATION` |

### Update

| # | Scenario | Expected |
|---|---|---|
| U1 | Valid rename / re-describe | 200; audit row written |
| U2 | Change parent between level-1 parents | 200 |
| U3 | Duplicate name (another row) | `WBW1138` |
| U4 | Duplicate name == self | allowed (excludes self) |
| U5 | Reuse an existing `hand_use_usag_cd` | allowed — no dup check on update (legacy parity, OQ-4) |
| U6 | Assign a parent to a row that has active children (demote) | `WBE_VALIDATION` |
| U7 | Self-parent | `WBE_VALIDATION` |

### Delete

| # | Scenario | Expected |
|---|---|---|
| D1 | Delete leaf with no children, not in use | 200; `USE_YN` flips to `'N'`; no physical delete; audit row written |
| D2 | Delete parent that has active children | `WBE1199` |
| D3 | Delete row referenced by an active transaction | `WBE1199` |
| D4 | Delete parent whose **child** is referenced by an active transaction (recursive) | `WBE1199` |
| D5 | Verify soft-delete | row still present in DB with `USE_YN='N'`; absent from list responses |

### Reorder

| # | Scenario | Expected |
|---|---|---|
| R1 | Valid reorder | 200; refreshed tree in new `disp_ord` order; audit row written |
| R2 | Duplicate `use_usag_cd` in payload | `WBE_VALIDATION` |
| R3 | Duplicate `disp_ord` in payload | `WBE_VALIDATION` |
| R4 | Unknown / cross-tenant code | `WBE_VALIDATION` |

### Import / Export

| # | Scenario | Expected |
|---|---|---|
| I1 | Valid import (roots + children) | preview all-completed; import inserts roots-first; `inserted_count` matches |
| I2 | Duplicate code in file | preview flags `dup_file` |
| I3 | Duplicate code vs. DB | preview flags `dup_db` |
| I4 | Child with missing parent | preview flags `parent_not_found` |
| I5 | Child whose parent is level-2 | preview flags `parent_not_root` |
| I6 | Empty required fields | preview flags `required` / over-length flags `length` |
| I7 | >500 rows | `WBE2041` (file-level) |
| I8 | Corrupt workbook / wrong extension | `WBE2043` |
| I9 | Header mismatch | `WBE2042` |
| I10 | Import a batch with a duplicate code | whole batch rolls back → `WBE_VALIDATION` |
| Export | "Export" — there is **no** Excel export endpoint for Purpose; the only download is the static **template** XLSX served from `wecms-web/public/templates/purpose/` (EN/KO/VI). Import is the only data-load path; export-with-data / export-without-data scenarios are N/A. |

### Authorization

| # | Scenario | Expected |
|---|---|---|
| A1 | Authorized user (has `settings-core-purpose`) | all operations succeed |
| A2 | Authenticated user without the grant | 403 `WBE1003` |
| A3 | No / invalid JWT | 401 `WBE1009` |

### Error handling

| # | Scenario | Expected |
|---|---|---|
| E1 | Bean-validation failure (length, blank) | `WBE_VALIDATION` envelope, lowercase snake_case |
| E2 | Missing tenant context | `WBE9999` |
| E3 | PK collision on create | one-shot retry; second collision → `WBE9999` |

---

## 2. Existing JUnit coverage map

### `PurposeSettingsServiceTest`

`list_assemblesLevel2RowsUnderParents` · `list_appliesKeywordTrim` · `list_missingTenant_throwsWBE9999`
· `detail_returnsRow_withUsedInTransactionsFromView` · `detail_rowMissing_throwsWBE_VALIDATION` ·
`detail_blankPath_throwsWBE_VALIDATION` · `create_persistsRow_andAppendsAuditWithSlugWsvcId` ·
`create_duplicateHandCd_throwsWBW1152` · `create_duplicateName_throwsWBW1138` ·
`create_parentNotFound_throwsWBE_VALIDATION` · `create_parentIsLevel2_throwsWBE_VALIDATION` ·
`create_retriesOncePastPkCollision` · `create_pkCollisionTwice_throwsWBE9999` ·
`update_persistsChanges_andDoesNotCheckHandCdDup_legacyParity` · `update_duplicateName_throwsWBW1138`
· `update_demoteWithChildren_throwsWBE_VALIDATION` · `update_selfParent_throwsWBE_VALIDATION` ·
`delete_blockedByActiveChildren_throwsWBE1199` · `delete_blockedByOwnTransactionReference_throwsWBE1199`
· `delete_blockedByChildTransactionReference_recursive_throwsWBE1199` ·
`delete_softDeletesAndAuditsWhenAllowed` · `reorder_duplicateUseUsagCd_throwsWBE_VALIDATION` ·
`reorder_duplicateDispOrd_throwsWBE_VALIDATION` · `reorder_unknownCode_throwsWBE_VALIDATION`

### `PurposeExcelServiceTest`

`preview_emptyFile_throwsWBE_VALIDATION` · `preview_corruptFile_throwsWBE2043` ·
`preview_invalidHeader_throwsWBE2042` · `preview_validRoots_allCompleted` ·
`preview_duplicateCodeInFile_flagsDupFile` · `preview_duplicateCodeAgainstDb_flagsDupDb` ·
`preview_childWithInFileParent_resolves` · `preview_childWithDbParent_resolves` ·
`preview_childWithMissingParent_flagsParentNotFound` · `preview_childWithLevel2Parent_flagsParentNotRoot`
· `preview_emptyRequiredFields_flagged` · `import_rootsFirstThenChildren_succeeds` ·
`import_emptyBatch_throwsWBE_VALIDATION` · `import_dupCodeInBatch_throwsWBE_VALIDATION`

### `PurposeSettingsControllerTest`

`list_returnsSnakeCaseNestedRows` · `list_forwardsKeywordQueryParam` · `detail_returnsSnakeCaseFields`
· `detail_businessException_envelopesAsCode` · `create_acceptsSnakeCaseBody_andReturnsSnakeCaseDetail`
· `create_missingRequiredField_envelopesAsValidation` · `create_duplicateHandCd_envelopesAsWBW1152`
· `update_passesPathPkToService` · `delete_blockedByChildren_envelopesAsWBE1199` ·
`delete_success_returnsEmptyEnvelope` · `reorder_acceptsItemsArray`

---

## 3. Coverage notes

- The recursive in-use delete guard (OQ-5) is covered by three service tests (own-ref, child-ref-recursive, allowed). The view branch (b) for real transaction consumer tables is not yet wired, so D3/D4 currently exercise the parent-child branch (a) — extend the tests when consumer UNION branches land.
- Excel file-level codes `WBE2042` (header) and `WBE2043` (corrupt) are asserted in `PurposeExcelServiceTest`; `WBE2041` (>500 rows) is enforced in `PurposeExcelService` (`MAX_ROWS`) — add an explicit row-cap test if not already present.
- Controller tests assert the lowercase snake_case wire shape and the `BusinessException → status.code` envelope mapping.

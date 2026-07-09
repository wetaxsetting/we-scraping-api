# Approval Line — Test Scenarios (SIT / UAT) + Coverage Map

SIT/UAT scenario matrix plus the existing automated coverage. Backend unit test:
[ApprovalLineServiceTest](../../../../src/test/java/com/kosign/wecms/domain/setting/approvalline/service/ApprovalLineServiceTest.java)
(Mockito; `TenantContext` set per test). Frontend manual plan:
[wecms-web/docs/features/settings/approval-line/test-scenarios.md](../../../../../wecms-web/docs/features/settings/approval-line/test-scenarios.md).

---

## Automated coverage map (ApprovalLineServiceTest — 12 cases, all green)

| Test method | Asserts | Closes |
|---|---|---|
| `create_insertsApprovers_assignsSequentialDispOrd_stampsTenantAndAudit` | master bizKey/name/no; approver `disp_ord` exactly `1,2`; tenant/parent/regId stamped from JWT; `reg_dtm` non-blank | Create happy path + disp_ord normalisation |
| `create_optionalPosiDept_persistEmptyStringNeverNull` | `apprvr_posi`/`apprvr_dept` persisted as `""` | snapshot null→"" rule |
| `create_moreThanFiveApprovers_throwsTooMany_doesNotSave` | `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`; no save | 5-approver cap |
| `create_emptyApprovers_throwsEmpty` | `WBE_APPROVAL_LINE_EMPTY_APPROVERS` | empty-list guard |
| `create_duplicateApprover_throwsDuplicate` | `WBE_APPROVAL_LINE_DUP_APPROVER` | dup guard |
| `create_blankName_throwsNameBlank` | `WBE_APPROVAL_LINE_NAME_BLANK` | name guard |
| `create_approverNotLinkedToTenant_throwsNotInTenant` | `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` | tenant-link guard |
| `update_diffSync_insertsNew_updatesExisting_deletesRemoved_resequences` | incoming `[C,A]` over existing `[A,B]`: C inserted disp_ord 1, A updated disp_ord 2, B deleted by PK; master renamed + modId set | **diff-sync + reorder persistence** |
| `update_missingMaster_throwsNotFound` | `WBE_APPROVAL_LINE_NOT_FOUND` | update not-found |
| `update_moreThanFiveApprovers_throwsTooMany_beforeAnyWrite` | cap enforced before any DB write | cap on update |
| `delete_removesAllDetailRowsThenMaster` | `deleteAllByBizKeyAndApprLineNo` then `deleteById` | delete order |
| `delete_missingMaster_throwsNotFound_doesNotDelete` | `WBE_APPROVAL_LINE_NOT_FOUND`; no delete | delete not-found |

This is the test set that closed **AL-GAP-02** (test coverage). No controller/integration test exists
yet (gap noted in [refactor-gap-analysis.md](refactor-gap-analysis.md#test-coverage)).

---

## SIT / UAT scenario matrix

### Search

| # | Scenario | Steps | Expected |
|---|---|---|---|
| S1 | Search by name | type a substring of a line name | grid filters case-insensitively (client-side over the loaded set) |
| S2 | Search no match | type a non-matching string | empty grid / "Approval Line Not Found" |
| S3 | Clear search | clear the box | full tenant set returns |
| S4 | Sort by name / create date | click sortable headers | order cycles default → ASC → DESC (client-side over the loaded set) |

### Create

| # | Scenario | Expected |
|---|---|---|
| C1 | Create valid line, 1 approver | success dialog; row appears; primary approver shown |
| C2 | Create with multiple approvers | row shows primary name + "+N" count chip; chain ordered `1..N` |
| C3 | Create, blank name | rejected (Zod first; backend `WBE_APPROVAL_LINE_NAME_BLANK` as backstop) |
| C4 | Create, 0 approvers | rejected (Zod; backend `WBE_APPROVAL_LINE_EMPTY_APPROVERS`) |
| C5 | Create, 6th approver | popover allows selection up to capacity; Save rejected `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` (Zod max 5 first) |
| C6 | Create, duplicate approver | rejected `WBE_APPROVAL_LINE_DUP_APPROVER` (Zod `superRefine` first) |
| C7 | Create, approver not in tenant | backend `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` |

### Read / Detail

| # | Scenario | Expected |
|---|---|---|
| R1 | Open Edit | dialog fetches full chain (not just the row's primary) ordered `1..N` |
| R2 | Detail unknown id | `WBE_APPROVAL_LINE_NOT_FOUND` |

### Update

| # | Scenario | Expected |
|---|---|---|
| U1 | Rename line | name updates; row reflects it |
| U2 | Add approver | new row INSERTed; chain re-numbered |
| U3 | Remove approver | row DELETEd; remaining re-numbered |
| U4 | Replace approver set | survivors UPDATEd, new INSERTed, dropped DELETEd (diff-sync) |
| U5 | Stale update (deleted by another user) | `WBE_APPROVAL_LINE_NOT_FOUND` |

### Reordering (AL-GAP-01)

| # | Scenario | Expected |
|---|---|---|
| O1 | Drag approver up | row moves; `#` column re-numbers live |
| O2 | Drag approver down | row moves; `#` re-numbers |
| O3 | Save reordered list | persists; `apprvr_disp_ord` re-derived `1..N` from array order |
| O4 | Reopen after save | chain shows the saved order (read path orders `APPRVR_DISP_ORD ASC`) → **order persists** |

### Validation

| # | Scenario | Expected |
|---|---|---|
| V1 | Duplicate approver | rejected (`WBE_APPROVAL_LINE_DUP_APPROVER` / Zod) |
| V2 | Empty line | rejected (`WBE_APPROVAL_LINE_EMPTY_APPROVERS` / Zod) |
| V3 | Exceed 5 approvers | rejected (`WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` / Zod) |
| V4 | Blank name | rejected (`WBE_APPROVAL_LINE_NAME_BLANK` / Zod) |

### Delete

| # | Scenario | Expected |
|---|---|---|
| D1 | Delete a line | confirmation dialog → success; row gone; master + detail rows hard-deleted |
| D2 | Delete unknown / race | `WBE_APPROVAL_LINE_NOT_FOUND` |
| D3 | Delete a line referenced by a pending payment | **succeeds** — no in-use guard (consumer features snapshot approvers; deletion does not break in-flight requests) |

### Integration (reuse across modules)

Approval Line reuse in consumer modules is verified at the **consumer feature** level (each snapshots
the approver list at request-creation time), not here. The cases below confirm the line is *selectable*
once created:

| # | Scenario | Expected |
|---|---|---|
| I1 | Use line in Vendor Payment | line appears in the payment's approval-line chooser |
| I2 | Use line in Expense Payment | selectable |
| I3 | Use line in Payroll Payment | selectable |
| I4 | Use line in Internal Transfer | selectable |

> These I-series scenarios depend on the consumer features' own approval-line consumption (out of this
> feature's scope); they are listed for traceability with the PRD's reuse requirement.

### Error handling

| # | Scenario | Expected |
|---|---|---|
| E1 | Backend validation failure | system `error` dialog with the mapped `WBE_APPROVAL_LINE_*` message |
| E2 | Network failure mid-save | system `error` dialog with `trace_id` |
| E3 | 401 (session expired) | global re-auth interceptor (not feature-specific) |

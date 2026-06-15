# Quarantined WIP test scaffolds

These three test files were **untracked working-tree WIP** (not in git, not in any
commit or branch) that **never compiled** against the current production code. They
were quarantined here on 2026-06-12 so the test source set (`src/test/java`) compiles
and the Approval Request **Phase 1** legacy-parity work can be verified cleanly.

- `VendorPaymentServiceTest.java`
- `ExpensePaymentServiceTest.java`
- `PayrollPaymentServiceTest.java`

## Why they don't compile (pre-existing, unrelated to Phase 1)

They reference APIs that no longer exist on the current code:

| Stale reference | Current reality |
|---|---|
| `ScheduledTransferDetailResponse.apprRegDtm()` | No such accessor on the DTO (an unbuilt "Approval-Content / Requested-on / G1" field). |
| `ScdulPymnSumrRepository.findMaxApprSeqNoByBizKey(...)` / `ExptResolMstrRepository.findMaxApprSeqNoByBizKey(...)` | Removed — apprSeqNo is now allocated via `LegacyFunctionRepository.nextApprSeqNo(...)` (the `wb_get_next_val` funnel). |
| `com.kosign.wecms.domain.notification.service.NotificationService` | Class does not exist anywhere in the codebase. |

Repairing them requires unrelated feature work (the missing `apprRegDtm` field) and
rewriting their apprSeqNo stubs — both outside Phase 1 scope. They are preserved here
(not deleted) so the team can salvage/rebuild them against the current API in a
separate task, then move them back under
`src/test/java/com/kosign/wecms/domain/payment/{vendor,expense,payroll}payment/service/`.

## Phase 1 test coverage is unaffected

F-1 / F-2 / F-4 evidence lives in
`src/test/java/com/kosign/wecms/domain/payment/approvalbox/service/ApprovalBoxFanoutServiceTest.java`
(in the active source set), plus the green `compileJava`.

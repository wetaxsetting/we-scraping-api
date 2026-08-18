# A1 — Implementation Readiness Review

**System:** Refactor (wecms-api)
**Date:** 2026-06-24
**Item:** A1 — Tenant-scope correction in `BankAccountPermissionService`
**Source plan:** [database-phase-a-implementation-plan.md](../governance/database-phase-a-implementation-plan.md) → A1
**Roadmap:** [database-remediation-roadmap.md](../governance/database-remediation-roadmap.md) → Phase A
**Status:** Implementation **planning only** — no code modified by this document.

## Verdict

**A1 is safe to implement immediately.** It is a two-line swap to an **already-existing** repository method, the change has a **proven in-repo precedent** that even carries the exact guidance comment for this fix, the functional output is unchanged, and the only test impact is updating two Mockito stubs. No schema, wire, API, or DTO change.

---

## Verification

### 1. Exact repository method — ✅ already exists, no change needed
[`BusiAdmnInfmRepository.findAllByBizKey(String bizKey)`](../../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiAdmnInfmRepository.java#L41) is declared and documented (lines 36–41). Return type `List<BusiAdmnInfm>`. The repository is `JpaRepository<BusiAdmnInfm, BusiAdmnInfmId>`. **A1 adds no repository code.**

### 2. Exact service methods — ✅ two sites, both byte-identical
Both are in [`BankAccountPermissionService`](../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionService.java):

| Site | Method | Lines | Purpose of the admin set |
|---|---|---|---|
| 1 | `list(bankCd, acctNo, kindWire)` (`@Transactional(readOnly = true)`) | [98–102](../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionService.java#L98) | Exclude admins from the selectable grantee roster (legacy `comm_0183_002_r001` `ADMIN_YN != 'Y'`). |
| 2 | `replace(bankCd, acctNo, req)` (`@Transactional`) | [194–198](../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionService.java#L194) | Feed the non-admin caller self-revoke guard only. |

Both currently read:
```java
busiAdmnRepo.findAll().forEach(row -> {
    if (bizKey.equals(row.getBizKey())) { <set>.add(row.getUserId()); }
});
```
`bizKey` is sourced from `TenantContext.currentBizKey()` at the top of each method. `busiAdmnRepo` is the injected `BusiAdmnInfmRepository` ([field line 62](../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionService.java#L62)). Entity accessors `getBizKey()` / `getUserId()` exist on `BusiAdmnInfm` (`@Column BIZ_KEY` len 20, `USER_ID` len 100).

### 3. Proven precedent — ✅ identical pattern already shipped
[`UserManagementService.adminIdSet(bizKey)`](../../../src/main/java/com/kosign/wecms/domain/setting/users/service/UserManagementService.java#L359) is the exact target form, **and its comment is the canonical guidance for this fix**:
```java
private Set<String> adminIdSet(String bizKey) {
    // Tenant-scoped query — never findAll() across every tenant's
    // admin rows then filter in Java.
    Set<String> out = new HashSet<>();
    for (BusiAdmnInfm row : busiAdmnRepo.findAllByBizKey(bizKey)) {
        out.add(row.getUserId());
    }
    return out;
}
```
A second consumer — [`ApprovalNotificationService.java:337`](../../../src/main/java/com/kosign/wecms/domain/payment/notification/service/ApprovalNotificationService.java#L337) — also uses `findAllByBizKey(bizKey)`. A1 simply brings `BankAccountPermissionService` in line with the established convention; it is the **only** remaining `busiAdmnRepo.findAll()` caller in the tree.

### 4. Expected before/after behavior — ✅ output identical
- **Before:** load **all tenants'** `BUSI_ADMN_INFM` rows, then keep only those where `bizKey.equals(row.getBizKey())`.
- **After:** load only the current tenant's rows via `findAllByBizKey(bizKey)`; the `bizKey.equals(...)` guard becomes redundant and is dropped.
- The resulting admin-userId **set is identical** on a correctly-isolated DB (the old code already filtered to the same tenant). No change to the roster, the self-revoke guard outcome, the response DTOs, or any error code.

### 5. Tenant isolation impact — ✅ strictly improved
The pre-filter list stops containing other tenants' admin rows. This removes the latent cross-tenant exposure shape (any future read of the unfiltered list would have leaked across tenants) and pushes the `BIZ_KEY` constraint into the query — consistent with the repository rule "a tenant-scoped query must constrain on `BIZ_KEY`." `BUSI_ADMN_INFM` PK is `(BIZ_KEY, USER_ID)`, so the scoped query is index-supported.

### 6. Performance impact — ✅ improved, no regression
Replaces a full-table scan + in-Java filter with a `biz_key`-leading indexed lookup returning only the tenant's admin rows (typically a handful). Lower I/O and allocation; scales with tenant count instead of total admin rows across all tenants. No added round-trips.

### 7. Test impact — ✅ two stub updates, no logic change
- [`BankAccountPermissionServiceRosterTest`](../../../src/test/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionServiceRosterTest.java) stubs `busiAdmnRepo.findAll()` at **lines 76 and 99**. These must change to `when(busiAdmnRepo.findAllByBizKey(BIZ)).thenReturn(List.of(admin(ADMIN)))`. Without the update the stub is orphaned and `findAllByBizKey` returns Mockito's default empty list → the admin-exclusion assertions would fail. Pure stub rename; assertions unchanged.
- [`BankAccountPermissionServiceWithdrawalWhitelistTest`](../../../src/test/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionServiceWithdrawalWhitelistTest.java) does **not** stub `busiAdmnRepo` (relies on the default empty return), so it is unaffected — `findAll()` and `findAllByBizKey()` both default to empty under Mockito, yielding the same empty admin set.

---

## Implementation summary (for the implementing MR — not done here)

### Files to modify
1. [`BankAccountPermissionService.java`](../../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionService.java) — two call sites.
2. [`BankAccountPermissionServiceRosterTest.java`](../../../src/test/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionServiceRosterTest.java) — two stub lines.

No repository, entity, DTO, controller, migration, or config change.

### Lines likely to change
- Service: ~`98–102` and ~`194–198` (each 4-line lambda → a tenant-scoped loop; net ≈ ±1 line per site).
- Roster test: lines `76` and `99` (`findAll()` → `findAllByBizKey(BIZ)`).

### Suggested replacement (both sites)
```java
busiAdmnRepo.findAllByBizKey(bizKey)
        .forEach(row -> <set>.add(row.getUserId()));
```
(`<set>` = `adminUserIds` at site 1, `adminIds` at site 2.) Optionally mirror the `adminIdSet` comment for consistency.

### Required tests
- Update the two roster-test stubs; both existing tests must stay green.
- Add a regression assertion: `verify(busiAdmnRepo, never()).findAll();` and (optionally) `verify(busiAdmnRepo).findAllByBizKey(BIZ);`.
- Multi-tenant guard: with admins seeded for tenant A and tenant B, assert the roster/guard for tenant A sees only A's admins.
- Existing `BankAccountPermissionServiceWithdrawalWhitelistTest` should pass unchanged.

### Rollback strategy
Pure code revert of the two service sites (and the two test stubs) back to the `findAll().forEach` form. No schema/data/wire/contract change, so rollback is a single revert commit with zero migration or data impact. The pre-existing `findAllByBizKey` method can remain regardless (additive, used elsewhere).

---

## Risk assessment

| Dimension | Rating | Note |
|---|---|---|
| Functional regression | Very low | Output set unchanged; precedent proven in `adminIdSet`. |
| Tenant isolation | Improved | Removes cross-tenant scan/exposure shape. |
| Performance | Improved | Indexed `biz_key` lookup vs full scan. |
| Schema / migration | None | No DB change. |
| API / wire / DTO | None | Internal query only. |
| Test breakage | Contained | Two stub renames in one test file. |

**Conclusion:** A1 meets the bar for immediate implementation. It is the lowest-risk Phase A item — an isolated, precedent-backed, output-preserving correction with a trivial rollback.

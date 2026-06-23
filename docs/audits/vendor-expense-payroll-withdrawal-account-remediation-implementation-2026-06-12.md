# Withdrawal-Account Listing — Option A Implementation Report

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](README.md)._

**System:** Refactor (`wecms-api`) · **Baseline:** JEX legacy `comm_1001_001` (source of truth)
**Date:** 2026-06-12 · **Plan:** [vendor-expense-payroll-withdrawal-account-remediation-plan-2026-06-10.md](vendor-expense-payroll-withdrawal-account-remediation-plan-2026-06-10.md) — **Option A** (single shared service for all five consumers)
**Scope guardrails honored:** no frontend change · no endpoint-URL change · no DTO response-shape change · no controller `@PreAuthorize`/`@menuAuthz` change · no G4/G5 · no unrelated refactoring · Flyway G3 seed (plan step 9) **not** in scope of this request.

---

## 1. Final result: ✅ PASS

- `./gradlew compileJava` → **BUILD SUCCESSFUL** (exit 0).
- Targeted tests → **7/7 pass, 0 failures, 0 errors**
  (`WithdrawalAccountListServiceTest` 2/2, `WithdrawalAccountDelegationTest` 5/5).
- All five payment consumers (Internal Transfer, Vendor, Expense, Payroll, Overseas) now resolve their withdrawal-account dropdown through **one** strict (G1/G2/G3) shared service, mapped through the **unchanged** `WithdrawalAccountRow` DTO.

---

## 2. Files changed

### New (main)
| File | Purpose |
|---|---|
| `domain/payment/common/WithdrawalAccountConstants.java` | G1 bank set `('11704088','11704020')` + G2 `AUTH_TYPE` `V`/`W`. Moved out of `InternalTransferConstants`. |
| `domain/payment/common/WithdrawalAccountListService.java` | The single shared service: currency normalization → strict repo call (G1 banks + G3 flag) → G2 V/W intersection (no admin bypass) → `WithdrawalAccountRow` mapping. |

### New (test)
| File | Coverage |
|---|---|
| `test/.../payment/common/WithdrawalAccountListServiceTest.java` | G1 bank set passed to query · G2 V/W intersection · no-admin-bypass · G3 fallback flag = `false` by default. (2 tests) |
| `test/.../payment/common/WithdrawalAccountDelegationTest.java` | All five consumers delegate to the shared service (IT / Vendor / Expense / Payroll services + Overseas controller). (5 tests) |

### Modified (main)
| File | Change |
|---|---|
| `domain/banking/repository/AcctMstrRepository.java` | Renamed + strengthened the withdrawal query (see §4). |
| `domain/payment/internaltransfer/service/InternalTransferService.java` | `listWithdrawalAccounts` → one-line delegate; removed local `toWithdrawalAccountRow` + the now-dead `acctMstrRepository`/`bankInfmRepository` fields; added `WithdrawalAccountListService` dependency. |
| `domain/payment/vendorpayment/service/VendorPaymentService.java` | Same delegation; removed dead `acctMstrRepository`/`bankInfmRepository` fields + local mapper. |
| `domain/payment/expensepayment/service/ExpensePaymentService.java` | Same delegation; removed dead fields + local mapper. |
| `domain/payment/payrollpayment/service/PayrollPaymentService.java` | Same delegation; removed dead fields + local mapper. |
| `domain/payment/controller/OverseasPaymentController.java` | Replaced `InternalTransferService` dependency with `WithdrawalAccountListService`; the `/withdrawal-accounts` handler now delegates directly to the shared service. Removed the inbound coupling to `InternalTransferService`. |
| `src/main/resources/application-local.yml` | Moved the G3 fallback key `wecms.internal-transfer.firm-banking-fallback` → `wecms.payment.withdrawal.firm-banking-fallback` (shared key; value still `true` for the local R&D DB). |

### Deleted
| File | Reason |
|---|---|
| `domain/payment/internaltransfer/InternalTransferConstants.java` | Constants relocated to `payment/common/WithdrawalAccountConstants`; only referrer was the old test. |
| `test/.../internaltransfer/service/InternalTransferWithdrawalAccountTest.java` | Logic moved to the shared service; re-pointed as `WithdrawalAccountListServiceTest`. |

---

## 3. Consumer migration summary

| Consumer | Before | After | Endpoint URL | Auth annotation | DTO |
|---|---|---|---|---|---|
| Internal Transfer | service ran lenient query inline | delegates to `WithdrawalAccountListService.list(ccyCd)` | unchanged | unchanged | unchanged |
| Vendor | lenient inline | delegates | unchanged | unchanged | unchanged |
| Expense | lenient inline | delegates | unchanged | unchanged | unchanged |
| Payroll | lenient inline | delegates | unchanged | unchanged | unchanged |
| Overseas | delegated to `InternalTransferService` | delegates to `WithdrawalAccountListService` directly | unchanged | unchanged | unchanged |

**Preserved for every consumer (verified):** `WithdrawalAccountRow` shape, wire JSON, endpoint signatures, frontend label formatting (backend still ships `bank_cd/bank_nm/acct_no/acct_type_cd/ccy_cd/acct_nick_nm`), default selection (frontend `list[0]`), sorting (`ORDER BY bank_cd, acct_no`), and currency handling (`null/blank → null else trim().toUpperCase()`).

---

## 4. Repository changes

`AcctMstrRepository` — the 2-arg lenient `findWithdrawalAccounts(bizKey, ccyCd)` was **replaced** by the strict, neutrally-named:

```java
List<AcctMstr> findFirmBankingWithdrawalAccounts(
        String bizKey, String ccyCd,
        java.util.Collection<String> bankCds,        // G1 bank set
        boolean allowNoFirmConfigFallback);          // G3 fallback flag
```

JPQL deltas vs. the old lenient query:
- **G1 added:** `AND a.bankCd IN :bankCds`.
- **G3 parameterized:** the `OR NOT EXISTS (...)` firm-banking escape is now gated by `:allowNoFirmConfigFallback = TRUE` (previously unconditional). Defaults to `false` (strict) everywhere; `true` only in `application-local.yml`.
- **Preserved verbatim:** tenant scope, `ACTV_YN='Y'`, `acct_type_cd IN ('03'..'08')`, optional currency, the firm-banking `EXISTS (TB_FIRM_CONF … ORG_STAT='1')` join, and `ORDER BY a.bankCd, a.acctNo`.

**G2** (`ACCT_AUTH_INFO` `AUTH_TYPE IN ('V','W')`, `USE_YN='Y'`, no admin bypass) is applied in `WithdrawalAccountListService` via `AcctAuthInfoRepository.findByBizKeyAndUserIdAndUseYn(bizKey, userId, "Y")`, mirroring the plan.

**Zero-caller census (before delete):** `grep -rn "findWithdrawalAccounts" src` → **0 remaining callers** of the old lenient method after rewiring; `findFirmBankingWithdrawalAccounts` has exactly one executable caller (`WithdrawalAccountListService`). The lenient method no longer exists.

---

## 5. Tests executed

| Suite | Tests | Result |
|---|---|---|
| `WithdrawalAccountListServiceTest` | 2 | ✅ pass — G1 bank set captor, G2 V/W intersection, no-admin-bypass, G3 default-off |
| `WithdrawalAccountDelegationTest` | 5 | ✅ pass — IT / Vendor / Expense / Payroll services + Overseas controller all delegate to the shared service |

Command: `./gradlew test --tests "*WithdrawalAccountListServiceTest" --tests "*WithdrawalAccountDelegationTest"` → BUILD SUCCESSFUL (JUnit XML: 7 tests, 0 failures, 0 errors).

Mapping to the plan's requested scenarios:
- Internal Transfer / Overseas / Vendor / Expense / Payroll **strict** → covered by the delegation suite (all five funnel through the one strict service) + the behavior suite.
- G1 bank whitelist, G2 V/W authorization, no admin bypass, G3 fallback disabled by default → behavior suite.
- Zero-caller verification before deleting lenient method → §4.

---

## 6. Compile result

- `./gradlew compileJava` → **exit 0** (main sources clean).
- The two new test classes compile clean (only pre-existing unchecked-captor / null-safety **warnings**, identical to the prior test).

---

## 7. Unexpected findings (important — read before SIT)

### 7.1 The codebase state contradicted the plan's premise — Internal Transfer & Overseas were **not** already strict
The plan ("Verification findings (current state)") assumes Internal Transfer was already fixed to strict via a repository method `findInternalTransferWithdrawalAccounts(bizKey, ccyCd, banks, fallback)` and that only Vendor/Expense/Payroll were lenient. **In this branch that was false:**
- The repository had **only** the lenient 2-arg `findWithdrawalAccounts`; the strict method **did not exist**.
- **All five** consumers — including Internal Transfer and (transitively) Overseas — ran the lenient query: **no G1**, **no G2**, and an **always-on** G3 fallback (the `OR NOT EXISTS` was hardcoded in the JPQL).
- The `wecms.internal-transfer.firm-banking-fallback` config key was **dead** — no Java code read it; the fallback was effectively always on.
- An orphaned test (`InternalTransferWithdrawalAccountTest`) referenced the non-existent strict method, so **test sources did not compile** on this branch even before this change.

**Consequence:** to satisfy Option A ("consolidate all five onto one strict shared service") and the directive "legacy is the source of truth," Internal Transfer and Overseas also moved **lenient → strict** here. This is legacy-correct (legacy `comm_1001_001` is module-agnostic and strict for every payment type, and the pre-existing test's intent was strict), **but it is a behavior change the plan labeled "byte-identical."** The plan's "byte-identical IT/Overseas" guardrail was written against the false premise that they were already strict.

→ **Needs confirmation:** acknowledge that IT and Overseas withdrawal dropdowns now narrow to the legacy set (G1 banks ∩ G2 V/W grants ∩ G3 firm-banking) on a provisioned tenant. This is the intended legacy parity, not a regression — but it differs from the "no change to IT/Overseas" expectation.

### 7.2 G3 firm-banking Flyway seed (plan step 9) was not performed
This request enumerated requirements 1–7 (shared service, constants, repo rename, rewiring, behavior preservation, verification, report) and did not include the plan's step 9 (seed `TB_FIRM_CONF`/`ORG_STAT='1'` rows for the SIT tenant). With the strict path now default and the fallback off outside `local`, **an un-provisioned tenant will get an empty dropdown in SIT/UAT/PROD** until firm-banking is seeded. The local R&D DB is unaffected (`wecms.payment.withdrawal.firm-banking-fallback: true`). → **Must-fix-before-SIT** if SIT tenants are not firm-banking-provisioned.

### 7.3 Pre-existing, unrelated broken WIP tests block the full `./gradlew test`
Three **untracked** WIP test files — `VendorPaymentServiceTest`, `ExpensePaymentServiceTest`, `PayrollPaymentServiceTest` — import `com.kosign.wecms.domain.notification.service.NotificationService`, a class that does not exist on this branch. This makes `compileTestJava` fail for the whole module and is **unrelated to this change** (the files are untracked, predate this work, and reference no withdrawal-account code). To run the targeted tests, those three files were temporarily moved aside and **restored unchanged** afterward (verified: tree identical, no backup dir left). They remain broken; running the full test suite requires fixing or removing them (out of scope here). → flagged for the owner of those WIP tests.

---

## 8. Scope confirmation

- ✅ Audit/remediation only on the withdrawal-account list path; no other behavior touched.
- ✅ No frontend files changed; no endpoint URLs, DTO shapes, or controller authorization annotations changed.
- ✅ G4/G5 not implemented.
- ✅ No unrelated refactoring (the only structural change is the shared-service extraction; the three untracked broken tests were left untouched apart from a fully-reverted temporary move for verification).

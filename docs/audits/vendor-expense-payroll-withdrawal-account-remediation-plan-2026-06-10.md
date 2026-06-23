# Vendor / Expense / Payroll Excel Upload — Withdrawal-Account Listing Remediation Plan (G1 / G2 / G3)

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](README.md)._

**System:** Refactor (wecms-api) · **Baseline:** JEX legacy `comm_1001_001` (source of truth)
**Date:** 2026-06-10 · **Author:** Fix-planning review (Claude)
**Status:** Plan only — **no code/tests/schema changed.** Implementation NOT started.
**Related:** `internal-transfer-withdrawal-account-remediation-plan-2026-06-10.md` (Internal Transfer, already implemented + verified) · `internal-transfer-upload-withdrawal-account-listing-audit-2026-06-10.md`

Internal Transfer's withdrawal dropdown was fixed for G1/G2/G3 and verified (PARTIAL PASS — these three sibling modules were flagged as the remaining follow-up). This plan covers them — and, per the Shared-Service Safety Audit below, consolidates **all five** withdrawal-account consumers onto one shared service.

---

## Shared-Service Safety Audit (2026-06-10) — decision: ✅ SAFE TO SHARE

A pre-extraction safety audit compared every behavior the proposed shared `WithdrawalAccountListService` would own across all consumers. **All owned behavior is identical**; the only difference is the strict-vs-lenient *filtering*, which is exactly what this remediation unifies.

| Behavior (shared-service owned) | Internal | Vendor | Expense | Payroll | Overseas | Same? |
|---|---|---|---|---|---|---|
| Endpoint shape (`GET /withdrawal-accounts?ccy_cd=`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✅ |
| Service method signature (`listWithdrawalAccounts(String)`) | ✓ | ✓ | ✓ | ✓ | ✓ (delegates) | ✅ |
| Response DTO (`internaltransfer.dto.WithdrawalAccountRow`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✅ |
| Mapping (`toWithdrawalAccountRow` — `RPRS_BANK_NM`→`BANK_CD`, 6 args) | ✓ | ✓ | ✓ | ✓ | ✓ | ✅ byte-identical |
| Currency filter (`null/blank → null else trim().toUpperCase()`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✅ |
| Sorting (`ORDER BY bankCd, acctNo`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✅ |
| Default selection (frontend auto-select `list[0]`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✅ |
| Account label format (frontend, from shared DTO fields) | ✓ | ✓ | ✓ | ✓ | ✓ | ✅ |
| **Account filtering (G1/G2/G3)** | strict | lenient | lenient | lenient | strict | ❌ *intended remediation target — not a divergence to preserve* |

**Four sub-checks — all clear:** no module-specific account filtering (legacy `comm_1001_001` is module-agnostic); no module-specific DTO field (all five return the same `WithdrawalAccountRow`); no module-specific frontend dependency (shared `ApiWithdrawalAccountRow` wire shape; endpoint *authorization* is per-module class-level `@menuAuthz`, which stays on the controller — not the service); no module-specific label formatting (built frontend-side from shared fields).

### Overseas Payment is the 5th consumer (already on the strict path)

- `OverseasPaymentController.java:319-322` exposes `GET …/overseas-payment/withdrawal-accounts?ccy_cd=` and **delegates to `internalTransferService.listWithdrawalAccounts(ccyCd)`** directly.
- Therefore **Overseas already rides the strict, post-remediation path** — the Internal Transfer G1/G2/G3 fix transitively applied to Overseas's dropdown the moment it landed.
- This is **legacy-correct**: Overseas also uses the module-agnostic legacy `comm_1001_001` withdrawal-account logic, so strict behavior is the right parity for it (the controller Javadoc already notes the lookup is module-agnostic).
- Overseas being a live cross-module consumer of the Internal Transfer service is **direct evidence the consolidation is safe** — cross-module reuse of this exact code path is already in production.
- **The shared service must treat Overseas as a first-class consumer** (5 total, not 4). At the repository caller level the strict method has one caller today (`InternalTransferService`); Overseas consumes it transitively via that service — the consolidation makes the dependency explicit and removes Overseas's inbound coupling to `InternalTransferService`.

**Conclusion:** consolidating the list+map+filter pipeline introduces **no unintended behavior change** — Internal Transfer and Overseas keep their current strict behavior byte-for-byte; Vendor/Expense/Payroll change *only* in the intended G1/G2/G3 dimension.

---

## 0. Verification findings (current state)

**Caller paths — all three modules expose the same endpoint shape, all backed by the lenient 2-arg query:**

| Module | Controller endpoint | Service method | Repository method | DTO |
|---|---|---|---|---|
| Vendor | `VendorPaymentController.java:322` `GET /withdrawal-accounts` | `VendorPaymentService.listWithdrawalAccounts` `:411-418` | `findWithdrawalAccounts(bizKey, filter)` `:415` | `internaltransfer.dto.WithdrawalAccountRow` `:34` |
| Expense | `ExpensePaymentController.java:320` `GET /withdrawal-accounts` | `ExpensePaymentService.listWithdrawalAccounts` `:408-415` | `findWithdrawalAccounts(bizKey, filter)` `:412` | `internaltransfer.dto.WithdrawalAccountRow` `:35` |
| Payroll | `PayrollPaymentController.java:311` `GET /withdrawal-accounts` | `PayrollPaymentService.listWithdrawalAccounts` `:381-388` | `findWithdrawalAccounts(bizKey, filter)` `:385` | `internaltransfer.dto.WithdrawalAccountRow` `:34` |

**Key facts that shape the plan:**
- **Shared dropdown contract.** All three (and Internal Transfer) return the *same* DTO — `com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow(bank_cd, bank_nm, acct_no, acct_type_cd, ccy_cd, acct_nick_nm)` — and each service's private `toWithdrawalAccountRow(AcctMstr)` mapping is byte-identical (bank-name from `BankInfm.RPRS_BANK_NM`, fallback `BANK_CD`). The response shape is already unified and **must remain unchanged**.
- **Shared query.** All three call the lenient `AcctMstrRepository.findWithdrawalAccounts(bizKey, ccyCd)` (`AcctMstrRepository.java:436-438`) — no bank restriction, no `ACCT_AUTH_INFO`, unconditional `OR NOT EXISTS` fallback. This is exactly the pre-remediation Internal Transfer behavior.
- **No module-specific legacy difference.** Legacy `comm_1001_001_act.jsp` is module-agnostic — it does not branch by payment type. Vendor, Expense, Payroll, and Internal Transfer all called the *same* `comm_1001_001` with the *same* filters. Therefore **one strict implementation serves all four**; no per-module variant is needed.
- **Strict method is already reusable.** `AcctMstrRepository.findInternalTransferWithdrawalAccounts(bizKey, ccyCd, withdrawalBankCds, allowNoFirmConfigFallback)` (`:394`) has G1 (bank list) and G3 (fallback) as *parameters* and no module branch — it is module-agnostic in substance; only its **name** is Internal-Transfer-specific. G2 is applied in the service layer.
- **`AcctAuthInfoRepository` is not yet injected** into the three sibling services (only `AcctMstrRepository` + `BankInfmRepository`).

## 1. Legacy vs refactor comparison

Identical for all three modules (legacy `comm_1001_001` is shared). Same table as the Internal Transfer audit.

| Filter | Legacy (`comm_1001_001_act.jsp`) | Refactor now (Vendor/Expense/Payroll) | Match |
|---|---|---|---|
| Company / biz key | `biz_key = bizKey` | `a.bizKey = :bizKey` | ✅ |
| Account status | `ACTV_YN='Y'` | `a.actvYn='Y'` | ✅ |
| Account type | `IN ('03'..'08')` | `IN ('03'..'08')` | ✅ |
| **G1 bank** | `bank_cd IN ('11704088','11704020')` (`:31`) | **none** | ❌ |
| **G2 authorization** | `EXISTS ACCT_AUTH_INFO … AUTH_TYPE IN ('V','W') AND USE_YN='Y'`, no admin branch (`:43-52`) | **none** | ❌ |
| **G3 firm-banking** | mandatory `EXISTS TB_FIRM_CONF ORG_STAT='1'` (`:54-61`) | `EXISTS(...) OR NOT EXISTS(...)` (unconditional fallback) | ❌ |
| Currency | not filtered (commented) | optional `:ccyCd` (dialog sends none) | ✅ net |
| Label / value / default / sort | — | unchanged (shared DTO + mapping) | ✅ |

## 2. Gap classification

Same severities as Internal Transfer — and the same gaps exist in **all three** modules:

| Gap | Severity | Type |
|---|---|---|
| **G2** authorization (`ACCT_AUTH_INFO` V/W, `USE_YN='Y'`, no admin bypass) | **P1 — security** (user can pick a debit-source account they hold no grant on) | Migration gap |
| **G1** bank restriction (Shinhan `11704088` + Woori `11704020`) | **P1 — functional parity** (dropdown lists banks legacy never shows; non-firm-banking bank breaks execution) | Migration gap |
| **G3** firm-banking fallback-to-all | **P2** (only fires when tenant has zero active `TB_FIRM_CONF` rows) | Documented infra deviation (shared lenient method) |

These are **pre-existing** (never fixed), not regressions from the Internal Transfer change.

## 3. Safe implementation plan

> Backend-only. Wire contract + frontend untouched. Response DTO (`WithdrawalAccountRow`) unchanged. G4/G5 not in scope.

Because the contract, DTO, mapping, query, and legacy behavior are **identical across all five consumers** (per the Shared-Service Safety Audit above), the correct fix is to **converge on one shared implementation** rather than copy the Internal Transfer logic per module (the CLAUDE "two-call" rule — there would otherwise be ~6 copies of the G2 intersection across own-account-transfer + the five payment consumers).

### ✅ RECOMMENDED — Option A: shared `WithdrawalAccountListService` for ALL FIVE consumers

Consolidate every withdrawal-account consumer onto one shared service:

- `InternalTransferService` (currently strict — migrate to the shared service, behavior byte-identical)
- `VendorPaymentService` (lenient → strict via shared service)
- `ExpensePaymentService` (lenient → strict via shared service)
- `PayrollPaymentService` (lenient → strict via shared service)
- **Overseas payment flow** — `OverseasPaymentController`, currently delegating to `internalTransferService.listWithdrawalAccounts(...)` → repoint to the shared service (removes its inbound coupling to `InternalTransferService`; behavior stays strict / byte-identical)

**Implementation direction:**

1. **Create a shared `WithdrawalAccountListService`** (in `domain/payment/common/`) that owns the whole pipeline: call the strict repo method with the bank set (G1) + fallback flag (G3), apply the G2 V/W intersection from `AcctAuthInfoRepository.findByBizKeyAndUserIdAndUseYn(bizKey, userId, "Y")` (**no admin bypass**), and map to the shared `WithdrawalAccountRow`.
2. **Move the strict list + permission intersection into the shared service** — the G1/G3 strict-query call and the G2 V/W filter currently inline in `InternalTransferService.listWithdrawalAccounts` move here (one copy).
3. **Move the shared mapper into the shared service** — lift the byte-identical `toWithdrawalAccountRow(AcctMstr)` (4 copies today: IT, Vendor, Expense, Payroll) into one private mapper.
4. **Move constants to a payment/common location** — `WITHDRAWAL_BANK_CDS = ['11704088','11704020']` (G1) + `AUTH_TYPE_VIEW/WITHDRAW = 'V'/'W'` (G2) out of `InternalTransferConstants` into e.g. `domain/payment/common/WithdrawalAccountConstants`. Promote the fallback flag to a single shared key `@Value("${wecms.payment.withdrawal.firm-banking-fallback:false}")` (default `false` = strict in SIT/UAT/PROD; `true` only in `application-local.yml`).
5. **Rename the strict repository method to neutral naming** — `findInternalTransferWithdrawalAccounts` → `findFirmBankingWithdrawalAccounts` (`AcctMstrRepository`); update the (single) call site.
6. **Replace the Overseas dependency on `InternalTransferService`** — `OverseasPaymentController` calls the shared `WithdrawalAccountListService` instead of `internalTransferService.listWithdrawalAccounts(...)`.
7. **Each consumer's `listWithdrawalAccounts(ccyCd)` becomes a one-line delegate** to the shared service (IT, Vendor, Expense, Payroll services; Overseas controller).
8. **Delete the lenient 2-arg `findWithdrawalAccounts(bizKey, ccyCd)`** ONLY after a zero-caller census confirms Vendor/Expense/Payroll have migrated (Overseas was never on it).
9. **G3 data**: add a Flyway seed of `FirmBankingSetting` (`TB_FIRM_CONF`, `ORG_STAT='1'`) rows for the SIT/R&D tenant's Shinhan/Woori withdrawal accounts so the mandatory `EXISTS` returns rows (one seed covers all five consumers).

**Tables/entities:** none new — `AcctAuthInfo`/`AcctAuthInfoRepository` (V245/V246), `FirmBankingSetting`/`NapasBankInfo` already exist. **API impact:** none (per-module controllers, endpoints, `@menuAuthz` annotations, and the `WithdrawalAccountRow` DTO unchanged). **Frontend impact:** none (Vendor/Expense/Payroll lists narrow to the legacy-correct set, same as Internal Transfer; IT + Overseas unchanged).

### ❌ REJECTED — Option B: minimal per-module replication

Leave Internal Transfer + the strict method untouched and, in each sibling service, inject `AcctAuthInfoRepository`, call `findInternalTransferWithdrawalAccounts(...)` (accepting the misleading name) with the bank list + fallback flag, and inline the G2 intersection. **Rejected** — it duplicates the G2 intersection three more times (≈6 copies total), keeps a misnamed "InternalTransfer" method consumed by other modules, and leaves Overseas coupled to `InternalTransferService`. It violates the two-call rule and does not address the cross-module coupling the safety audit surfaced. Retained here only as the documented fallback if the team forbids any Internal Transfer change.

## 4. Test plan

- **Shared service strict-filtering test** (Option A): one focused Mockito test of `WithdrawalAccountListService` mirroring `InternalTransferWithdrawalAccountTest` — covering:
  - **G1 bank filter** — the legacy bank set (`11704088`, `11704020`) is passed to the repo (argument captor).
  - **G2 V/W permission filter** — keeps V- and W-granted accounts, drops non-V/W grants and ungranted accounts.
  - **No admin bypass** — an ADMIN principal with no grants → empty list.
  - **G3 firm-config fallback disabled by default** — the fallback flag handed to the repo is `false`.
- **Internal Transfer still strict** — `InternalTransferWithdrawalAccountTest` stays green after IT migrates to the shared service (proves byte-identical behavior / reuse safety).
- **Overseas still strict** — a test (or the existing Overseas withdrawal coverage) confirms the Overseas endpoint returns the strict, permission-filtered set after repointing to the shared service.
- **Vendor / Expense / Payroll now strict** — per-module wiring tests confirm each `listWithdrawalAccounts` delegates to the shared service, applies G1/G2/G3, and returns the unchanged `WithdrawalAccountRow` shape.
- **Zero-caller check before deletion** — verify (grep/caller census + clean compile) that the lenient 2-arg `findWithdrawalAccounts(bizKey, ccyCd)` has no remaining callers before removing it.
- **Acceptance (manual / SIT)**: live diff of legacy `comm_1001_001` vs each consumer's `GET /withdrawal-accounts` (IT, Vendor, Expense, Payroll, Overseas) for the same tenant + user — confirm identical account sets.
- **Build**: `./gradlew compileJava` + targeted `./gradlew test --tests "*Withdrawal*" --tests "*InternalTransfer*" --tests "*VendorPayment*" --tests "*ExpensePayment*" --tests "*Payroll*" --tests "*Overseas*"`.

## 5. Risk assessment

- **Doing the fix (Option A):** Low–medium. All entities/repos exist; the contract/DTO/frontend are untouched; the list only *narrows* to the legacy-correct set. Main risks: (a) the shared refactor touches Internal Transfer + Overseas — mitigate by keeping their behavior byte-identical and their tests green; (b) deleting the lenient method — gate on a zero-caller census; (c) renaming the IT-local fallback property to a shared key — update `application-local.yml` in the same change so local R&D keeps working; (d) repointing Overseas off `InternalTransferService` — verify the Overseas endpoint output is unchanged.
- **Not doing the fix:** High — three modules leave the same authorization hole (G2, debit source) and functional-parity break (G1) Internal Transfer just closed; G3 can over-expose accounts on un-provisioned tenants.
- **Data-state risk (G3):** identical to Internal Transfer — without the firm-banking seed, the strict `EXISTS` returns empty for an un-provisioned tenant. The seed (step 9) plus the local-only fallback property mitigate this.

### Guardrails (non-negotiable during implementation)

- **Internal Transfer and Overseas must remain byte-identical in behavior** after extraction — they already run the strict path; their existing tests are the regression guard, and any output change is a defect.
- **Vendor / Expense / Payroll change ONLY in the intended G1/G2/G3 dimensions** — bank set, V/W authorization, mandatory firm-banking. No other behavior shift.
- **Must remain unchanged for every consumer:** per-module controllers, endpoints, `@menuAuthz` auth annotations, the `WithdrawalAccountRow` DTO, the wire/response shape, frontend behavior, default selection, sorting, and account label format.
- **No G4/G5** (label bank-name source, currency mechanism) — out of scope.
- **No unrelated refactoring** — touch only the withdrawal-account list path; the shared-service extraction is the sole structural change.

### Needs Confirmation (resolve before coding)
1. **G2 admin bypass:** match legacy (none — recommended, consistent with the Internal Transfer fix) vs the refactor-wide admin convention. Legacy `comm_1001_001` has no admin branch.
2. **G1 bank set:** confirm `('11704088','11704020')` is still the exact current list (shared with Internal Transfer's constant).
3. **G3 SIT data:** will SIT tenants always have firm-banking config seeded? If not, the fallback-removal/gate is must-fix-before-SIT for these modules too.
4. **Frontend usage:** confirm each module's upload dialog actually renders the withdrawal dropdown (the endpoints exist; the shared `PaymentExcelUploadDialog` extra-step is the likely consumer) so the narrowed list is exercised in SIT.

## 6. Classification & recommended implementation order

| Gap (×3 modules) | Classification |
|---|---|
| **G2** authorization | **Must fix before SIT** (security) |
| **G1** bank restriction | **Must fix before SIT** (parity + execution) |
| **G3** seed + gate | Seed **before SIT**; fallback already gated by the shared property (must stay `false` outside local) |

**Order (Option A — the recommended direction):**
1. **Shared infra** — create `WithdrawalAccountListService` (`domain/payment/common/`) owning the strict list + G2 intersection + shared mapper; rename the strict repo method to neutral `findFirmBankingWithdrawalAccounts`; promote constants + fallback property to `payment/common`; migrate **Internal Transfer** to delegate (behavior-identical) and confirm its test stays green.
2. **Repoint Overseas** — `OverseasPaymentController` → shared service (removes its `InternalTransferService` coupling; output byte-identical).
3. **Vendor** → delegate to the shared service.
4. **Expense** → delegate.
5. **Payroll** → delegate.
6. **Delete** the lenient 2-arg `findWithdrawalAccounts` once the caller census is zero.
7. **G3 firm-banking seed** for the SIT/R&D tenant (one seed covers all five consumers) — before SIT data setup.
8. **Acceptance** — per-consumer live diff vs legacy `comm_1001_001` (IT, Vendor, Expense, Payroll, Overseas).

## Final recommendation

**Adopt Option A — a single shared `WithdrawalAccountListService` consumed by all five withdrawal-account flows** (`InternalTransferService`, `VendorPaymentService`, `ExpensePaymentService`, `PayrollPaymentService`, and the Overseas payment flow). The Shared-Service Safety Audit confirms every owned behavior is identical across all five, and Overseas already proves cross-module reuse of this code path is safe in production. This closes the G1/G2/G3 parity + security gaps in Vendor/Expense/Payroll, removes Overseas's coupling to `InternalTransferService`, and collapses ~6 duplicated implementations to one source of truth — with Internal Transfer and Overseas held byte-identical.

Option B (per-module replication) is **rejected**; it is retained above only as a documented fallback if the team forbids any Internal Transfer change.

> Do **not** change the frontend, the `WithdrawalAccountRow` shape, the per-module endpoints/auth, or implement G4/G5. Internal Transfer and Overseas are touched **only** for shared reuse safety (delegate migration + Overseas repoint), with their behavior held byte-identical and their tests as the guard.

This document is documentation only — no application source, tests, or schema were changed.

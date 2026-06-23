# Expense Payment — Refactor Gap Analysis (API)

> **Backend doc.** Source of truth = latest code. Verified 2026-06-23.
> Gap vocabulary: **MATCH** (refactor = legacy) · **APPROVED DEVIATION** (intentional, signed-off) · **PARTIAL** (implemented but incomplete) · **DEAD LEGACY** (legacy-only, no refactor equivalent / stale artifact) · **MISSING** (not implemented).

The code is implemented **full-stack** (backend controller + both services + dispatch executor + export provider; Next.js pages/components/client). The "STUB / Phase A / backfill pending / deferred Phase D / not wired / design phase" framing in older docs was **stale** and has been removed.

## MATCH

| Area | Detail |
|---|---|
| REST surface | 20 controller mappings ↔ ~18 FE client functions, one-for-one |
| Status derivation | `APPR_STTS` not stored — `wb_get_appr_stts` over `EXPT_RESOL_MSTR.APPR_TYPE_CD`; rejected = `R` |
| Dispatch approval gate | `APPR_TYPE_CD = "01"` required → `WBE2063` (legacy `PymnHelper.pymn_0001_001_c001:2146`) |
| Result status filter | route-aware PROC_STAT Completed `C` / In Progress `P` |
| Footer totals (F1) | full dispatched set, status filter NOT applied (legacy R008) |
| Approval Ref (F4a/F4b) | `EXPT_RESOL_MSTR.DOC_NO` (not `ERP_FILE_NUM`); clickable in Result list |
| Approval Title (F3) | `EXPT_RESOL_MSTR.TITL`, blank-fallback to `SCDUL_PYMN_SUMR.TITL` |
| Header tab counts (G1) | `countByStatus` over resolved per-line statuses |
| Key generation | all via `wb_get_next_val` funnel |

## APPROVED DEVIATION

| Item | Rationale |
|---|---|
| Single `ExpensePaymentController` (vs Internal Transfer's two) | refactor-internal organization |
| Dedicated `ExpenseDispatchExecutor` | isolated G2-parity copy; shared executor untouched for Vendor/Payroll |
| Reporting-account late-bind PATCH | not mandated at create (same as Vendor; open question resolved) |
| Dispatch bank hand-offs flag-gated OFF by default | enablement-ready, not absent — see dispatch checklist |
| `SCDUL_PYMN_NO` key reuse via `InternalTransferKeyGenerator` | shared physical table |

## PARTIAL

| Item | State | Owner |
|---|---|---|
| Dispatch end-to-end bank transmission | DB-row path real + runs by default; EFS/SFTP/00301/202 hand-offs `@ConditionalOnProperty` OFF in every profile | dev/infra — flags + B1/B2/SFTP provisioning (PO) |
| Backend test coverage | dispatch + result tests present; **no** test for Scheduled create/parse, request-approval, approval-action, withdraw/change-line, reporting-account PATCH, or banking-match | dev |
| Frontend test coverage | none for the Expense pages | dev |

## DEAD LEGACY (stale artifacts — code-comment only, DO NOT edit in docs pass)

| Item | Note |
|---|---|
| `ExpensePaymentResultService` Javadoc `SCDUL_PYMN_TYPE_CD = '01'` + "Phase A read-only" | code uses `"02"`; comment stale → flag for code-comment cleanup MR |
| `lib/api/expensePayment.ts` "Vendor / Phase A / `'01'` vs `'03'`" docstrings | code correct; comment stale |
| Legacy `ERP_FILE_NUM` as Approval-Ref source | superseded by `EXPT_RESOL_MSTR.DOC_NO` |
| Legacy `Y`/`N` result status filter | superseded by `C`/`P` route-aware PROC_STAT |

## MISSING

None at the implementation level — the surface is complete. The PARTIAL items above (test coverage, dispatch flag enablement) are the only open work.

---

## Decisions needed (PO / dev)

1. **Code-comment cleanup MR** (PO/dev) — remove the stale `'01'`/`'03'`/"Phase A"/"Vendor" artifacts from `ExpensePaymentResultService` + `expensePayment.ts`. Comments only; no behavior change.
2. **Dispatch SIT/UAT enablement** (PO + infra) — set the four dispatch flags + provision EFS base URL (B1), VAN API key (B2), SFTP, and `TB_FIRM_CONF` rows per the dispatch checklist before any real bank transmission test.
3. **Test backfill** (dev) — add BE coverage for create/parse/approval/match endpoints and FE tests for the Expense pages.

---

Last verified: 2026-06-23.

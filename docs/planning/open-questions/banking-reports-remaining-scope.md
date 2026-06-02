# Banking Reports — Remaining Scope Decisions

**Date:** 2026-05-15
**Status:** Pending PO / lead sign-off
**Audience:** Sonny (lead), Mr. Jung (PO)
**Related:** [docs/refactor-wecms/features/](../../features/), [wecms-api/CLAUDE.md](../../../CLAUDE.md)

---

## Context

The Banking module ships four reports. Status after the 2026-05-15 Daily Cash
wiring pass:

| Report | Status | Endpoint |
|---|---|---|
| Daily Cash | ✅ Done | `GET /api/v1/banking/reports/daily-cash-report` — reads V125 `DAILY_CASH_REPT_DAILY_SUMR_DETL` |
| Daily Financial (CMC) | ⏳ Partial | `GET /api/v1/banking/reports/daily-financial` — `ACCT_MSTR` balances real; per-account txns + flow rollup are empty skeletons |
| Daily Financial (HS) | ⏳ Partial | `GET /api/v1/banking/reports/daily-financial-hs` — hardcoded 6-row fixture |
| Cash Flow | ⏳ Partial | `GET /api/v1/banking/reports/cash-flow-report` — deterministic seed in `CashFlowReportService` |

To bring the remaining three to "Done" parity with Daily Cash, the work is
genuinely backend-first (Flyway port + new entities + new services), not a
frontend rewire. Three open scope questions block starting.

---

## Open question 1 — Daily Financial CMC backend port

**Source tables (per legacy `rept_0002_001`):**

- `DAILY_FINCL_REPT_DAILY_SUMR_DETL`
- `DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR`

**PROD Active Table List membership:** ✅ Both tables ARE in the list
(verified against `wecms-api/CLAUDE.md` §"Active table list").

**Scope question:** None. This work is straightforwardly in scope. The
question is sequencing — should it ship next, or wait behind another
sprint priority?

**Estimated effort:** ~1 sprint slice
- Flyway migration: 2 `CREATE TABLE` (column-for-column DEV parity) + seed
  ≤ 30 rows per table for `VN_VNM0004_000001` per the Data Migration Rule.
- Backend: `DailyFinancialReportService` + 2 entities + 2 repositories +
  new `DailyFinancialReportController` with endpoint
  `/api/v1/banking/reports/daily-financial-report` (mirrors Daily Cash
  naming).
- Frontend: new wire types (lowercase snake_case), repoint
  `fetchDailyFinancial`, update mapper + `DailyFinancialClient`. Same
  pattern as the Daily Cash 2026-05-15 pass.

**Decision needed from Sonny / Mr. Jung:** Approve sequencing as the
next sprint slice, OR defer.

---

## Open question 2 — Daily Financial HS backend port

**Source per legacy `rept_0017_001`:**

- `fn_get_haengsung_daily_financial_report(p_biz_key, p_as_of_date,
  p_account_filter)` — PL/SQL set-returning function.
- HAENGSUNG ledger tables that function reads from (unverified —
  not visible in the local active-list audit).

**PROD Active Table List membership:** ⚠ The function's underlying
ledger tables are NOT visible in the active list. Function port itself
is documented as a "right exception to the 'no new DB-function
dependencies' rule" pending a Sprint 2 decision (per
`wecms-api/CLAUDE.md` §"Legacy report DB functions — Sprint 1
stance").

**Documented blocker:** CLAUDE.md §"Project Overview" explicitly
states:

> Per-product behavioural gates (HAENGSUNG payment-table-2 layout,
> account masking for bank `11704020`, hidden expense file column,
> **daily-financial-HS report gating** ...) are **deferred** and port
> with each feature MR. Infrastructure only this phase.

The HS report is one of the per-product gates that's deferred behind
the broader product-gate framework. Tenant `VN_VNM0004_000014`
(HAENGSUNG, `PRDT_TYPE = 'H'`) is seeded locally (V73) but no
behavioural gate consumes the product type yet.

**Scope questions for PO:**

1. Is daily-financial-HS still deferred behind the product-gate
   framework, or has its priority risen?
2. If we port it now, which path — port `fn_get_haengsung_daily_financial_report`
   verbatim to Flyway (documented exception), or replicate in Java?
3. Which HAENGSUNG ledger tables does the function actually read, and
   are they in (or to be added to) the PROD Active Table List?
4. Does the endpoint guard on `TenantContext.currentPrdtType() == 'H'`,
   or stay open?

**Estimated effort if unblocked:** ~1.5 sprint slices
- Schema audit of legacy `fn_get_haengsung_daily_financial_report`
  function body against DEV to identify dependent tables.
- 1+ Flyway migrations to port the tables + the function (or replicate
  query in Java).
- Backend service + controller + DTO already exist as stubs.
- Frontend mapper needs to flatten `DETAIL/SUBTOTAL/TOTAL` rows into
  grouped sections (current `mockLedgerGroups` shape).

---

## Open question 3 — Cash Flow backend port

**Source per legacy `rept_0003_001 / cash_0001_001`:**

- BCS batch port — quarterly aggregation by Revenue / Expense /
  Financing / Other groups.
- Underlying cash-flow plan tables (unverified — not visible in the
  local active-list audit).

**PROD Active Table List membership:** ⚠ No `CASH_FLOW` table or
cash-flow-plan tables visible in the active list.

**Scope question:** Cash Flow tables are out of the documented PROD
Active Table List. CLAUDE.md §"Refactoring enforcement rule"
explicitly states:

> Refactoring Enforcement Rule: if legacy JEX uses multiple tables →
> **ONLY refactor tables that exist in the PROD Active Table List**.
> No exceptions.
>
> ...
>
> If a legacy code path is genuinely impossible without an excluded
> table (e.g. a join target that PROD truly does populate via a path
> the audit missed), raise the discrepancy with the lead **before**
> widening this list — do not silently expand the scope.

The active-list precedent for widening was the V94 Contacts case,
where Mr. Jung explicitly authorised adding `TRCO_INFO_FILE`
(documented inline at "Scope override 2026-05-13").

**Decision needed from Sonny / Mr. Jung:**

1. Are the cash-flow plan / BCS source tables actually populated in
   PROD? If yes, what are they called (likely `CASH_FLOW_PLAN`,
   `CASH_FLOW_PLAN_DETL`, or similar)?
2. If yes, authorise the active-list widening explicitly so an MR
   can port them.
3. Alternative: is the deterministic seed `CashFlowReportService`
   already serving today acceptable as the static-UI stand-in until
   the BCS batch port lands?

**Estimated effort if unblocked:** ~1.5 sprint slices
- Active-list widening + DEV audit.
- 2–3 Flyway migrations.
- Backend `CashFlowReportService` rewrite (currently a deterministic
  seed; needs real quarterly aggregation).
- Frontend already wired to the existing endpoint — only the response
  shape may change.

---

## Recommended sequencing (if all three are approved)

1. **Daily Financial CMC** — clean in-scope work, mirrors the
   2026-05-15 Daily Cash pattern. Ship this first as a single sprint
   slice.
2. **Cash Flow** — pending active-list widening + DEV table audit.
   Mid-priority.
3. **Daily Financial HS** — pending product-gate framework + function
   port decision. Lowest priority; ships with the HAENGSUNG product
   gates.

---

## Why this note exists

The 2026-05-15 frontend pass discovered that "wire the remaining three
to the real backend" reduces to "port substantial backend
infrastructure first" — not a frontend rewire. The work is real and
the path is documented, but the scope decisions sit above Claude's
authority per CLAUDE.md §"Scope Guard — Backend Changes Only on
Explicit Request":

> A frontend task cannot proceed without a corresponding API contract
> — in which case Claude must ask the user before editing this tree
> (full-stack ownership rule), not assume.

Tagging Sonny and Mr. Jung for the three decisions above.

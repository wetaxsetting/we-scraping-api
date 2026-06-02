# Cash Flow Report — Refactor Feature Spec

| Field | Value |
|---|---|
| Slug | `cash-flow-report` (runtime id; file renamed to `cash-flow.md` to match the menu) |
| Module | Banking → Reports |
| Status | Phase 0 — design (data path resolved; backend implementation pending) |
| JEX twin | `cash_0001_001` (view) + `cash_0001_001_r001` (read API) |
| Refactor backend route | `GET /api/v1/banking/reports/cash-flow-report` + `POST .../export` (already shipped — stub seed data) |
| Refactor frontend route | `/banking/reports/cash-flow` |
| Audit `WSVC_ID` | `cash-flow-report` (slug — never `cash_0001_001`) |
| View Setting `WEB_SVC_ID` | n/a — report page has no list-icon toolbar button, so no popover wiring (mirrors Daily Cash / Daily Financial) |
| i18n key prefix | `report.cash_flow.*` |
| Doc revision | 2026-05-18 (rev 2) — updated after decoding the legacy function's CTEs |

> **Doc-revision note (2026-05-18 rev 2).** The first revision of this doc
> documented the data path from a structural query of the function (which
> tables it references). The second pass actually decoded the function's
> 12 CTEs and uncovered several behaviours that were wrong or missing in
> rev 1: (a) the bank-side classifier is `acct_trns_hstr.chart_acct_cd`
> directly, NOT a join through `acct_trns_rcv_pay_infm.rcv_pay_usag_cd`;
> (b) `EXPT_RESOL_MSTR` is the **approval-status gate** for the other
> sources, not a separate aggregation source; (c) opening balance carries
> forward from `p_year - 1` via a re-aggregation of all sources for the
> previous year (not zero as rev 1 claimed); (d) `chart_acct_infm.cal_rule`
> (`'P'` / `'M'`) gates a hardcoded list of INVESTMENT codes (`'2001026'`,
> `'2001027'`, `'2002003'`, `'2002002'`) into the balance arithmetic;
> (e) the function depends on two legacy PL/pgSQL helpers
> (`wb_get_appr_stts(...)` for approval status, `wb_get_chart_acct_level_text(...)`
> for hierarchy level) that the refactor reimplements in Java rather than
> porting. Sections 3.0–3.12 below reflect the verified shape; sections 5–7
> retain the structural-only design from rev 1 and will be revised in
> Phase 2 as implementation details emerge.

---

## 1. Summary

Annual cash-flow report — a P&L-style monthly matrix (12 columns Jan–Dec + annual total) of line items grouped under three Level-1 root categories (`수입 REVENUE`, `지출 EXPENSES`, `투자 INVESTMENT`). Each row carries a display code, display name, row type (Opening Balance / Level 1 / Level 2 / Level 3 / Level 1 Subtotal / Sales Profit / End Balance), and monthly amounts in a chosen display currency, computed by converting USD- and KRW-denominated lines through user-supplied exchange rates. The selector also accepts a list of bank/account filters so the user can scope the report to specific accounts.

The report is computed **on the fly** from transaction history (not pre-aggregated). The legacy implementation is a single PostgreSQL function `get_cashflow_report(biz_key, year, quarter, base_ccy, usd_rate, krw_rate, bank_filt)` (~97 KB of PL/pgSQL) that walks the Chart of Accounts hierarchy for the tenant, aggregates monthly inflow/outflow from 5 transaction sources, applies FX conversion, and emits hierarchy-aware rows.

### In scope (this MR)

- `GET /api/v1/banking/reports/cash-flow-report` returns the live report computed by Spring service (no DB function call, no legacy PL/pgSQL helper).
- Five source aggregations: `ACCT_TRNS_HSTR + ACCT_TRNS_RCV_PAY_INFM`, `CARD_TRNS_HSTR`, `OVRS_PYMN_MSTR`, `EXPT_RESOL_MSTR`, `SCDUL_PYMN_SUMR(_DETL)`.
- Chart of Accounts hierarchy walk (`CHART_ACCT_INFM` — three roots `ACC-01/02/03`, plus L2 children, plus L3 grandchildren).
- Currency conversion at read time using request-supplied `usdRate` / `krwRate` (no FX table lookup on the server — matches legacy contract).
- Opening Balance / End Balance carry-forward per month.
- Sales Profit (= revenue total − expense total).
- Quarter filtering (column hide is client-side; server still returns 12 months).
- Bank/account filter via `bankFilters` JSON.
- Excel export (already wired through `ExcelExportService`).

### Out of scope

- Editing / saving cash-flow plan amounts (no plan-entry screen on the legacy — the report aggregates actuals).
- Multi-year roll-forward (the function's opening balance only walks back to year start).
- Server-side FX rate lookup (legacy passes rates from UI; we keep that contract).
- View Setting popover (the report page has no list-icon toolbar — same as Daily Cash / Daily Financial).
- HAENGSUNG tenant (`VN_VNM0004_000014`) — DEV has no Chart of Accounts for that tenant, so the report comes back empty; surface gracefully.

---

## 2. JEX source-of-truth citations

### View / Action JSP

- `WECMS DEV/WECMS/web/view/jex/wabooks/cash/cash_0001_001_view.jsp` — title "Cash Flow", year + quarter selector, base-currency picker (VND/USD/KRW), USD/KRW rate inputs, bank-filter popover, search/export buttons.
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/cash/cash_0001_001_r001_act.jsp:19-36` — validation: `DATA_YEAR`, `BASE_CCY_CD`, `USD_RATE_AMT`, `KRW_RATE_AMT` required. (No validation that `USD_RATE_AMT` is omitted when base = USD — refactor adds this defensively.)
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/cash/cash_0001_001_r001_act.jsp:26-36` — calls `CASH_FLOW_R001` IDO with the assembled input record.

### WSVC contract

- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.cash_0001_001_r001.xml:32-72`
  - IN: `DATA_YEAR` (4, YYYY), `YEAR_QUAT` (2), `BASE_CCY_CD` (3), `USD_RATE_AMT` (number), `KRW_RATE_AMT` (number), `BANK_FILT` (nested REC: `BANK_CD`, `ACCT_NO`).
  - OUT `REC`: `DISP_ORD` (5), `DISP_CD` (255), `DISP_NM` (255), `ITEM_CD` (10), `ITEM_NM` (255), `ROW_TYPE` (255 — see §3.2 for actual values), `GRP_CD` (500), `JAN_AMT` .. `DEC_AMT` (number), `TOTAL_AMT` (number), `DISP_CCY_CD` (3), `PARENT_L2_CD` (255).
  - `WSVC.login = N` — **refactor overrides to require auth** (Sonny override 2026-05-13, documented inline in `CashFlowReportController` Javadoc).

### IDO query

- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.CASH_FLOW_R001.xml` — single `SELECT ... FROM get_cashflow_report(...)` against the database function. The IDO is a thin pass-through; all the work is inside the function.

### Database function (legacy implementation)

- `get_cashflow_report(p_biz_key, p_year, p_quarter, p_base_currency, p_usd_rate, p_krw_rate, p_bank_filters)`
- Source: `wecmsvndev.public.get_cashflow_report` (DEV). Body size ≈ 97 KB.
- **The refactor does NOT port this function.** Per the api-side rule `DB Function / Legacy Function Policy`, refactor feature code does not add new legacy PL/pgSQL helpers. The function is referenced here for parity-test reference only.

### Frontend (legacy reference)

- `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/cash/cash_0001_001.ts` — year dropdown, quarter cycle, bank filter, ExcelJS export, matrix render.

---

## 3. Data path — verified

### 3.0 Function structure (decoded from `get_cashflow_report` body)

The legacy function is built from 12 CTEs that fire in this order:

| # | CTE | Purpose |
|---|---|---|
| 1 | `quarter_months` | Static `(quarter, month_num)` lookup, 1→{1,2,3}, 2→{4,5,6}, etc. |
| 2 | `fixed_level1_categories` | Static list of the three roots `ACC-01`, `ACC-02`, `ACC-03`. |
| 3 | `all_payment_data` | **The source-table UNION** — 4 transaction sources merged into a common shape (see §3.1). |
| 4 | `filtered_payment_data` | Applies year filter + quarter filter + bank filter + FX conversion to `all_payment_data`. |
| 5 | `bank_filter_json` | Helper used inline by `filtered_payment_data` to parse the `p_bank_filters` JSON. |
| 6 | `monthly_summary` | Groups `filtered_payment_data` by `(chart_acct_seq_no, chart_acct_cd, payment_month)` and sums `converted_amount`. |
| 7 | `level3_with_hierarchy` | Joins `chart_acct_infm` to itself (L3 → L2 → L1) and to `monthly_summary` to produce per-Level-3 rows with 12 monthly amounts + L1/L2 parent labels. Filters out non-Level-3 codes, soft-deleted ancestors, and chart entries outside `ACC-01/02/03`. |
| 8 | `filtered_hierarchy` | Additional filter — drops rows whose L1 or L2 ancestor has `stts != 'A'`. |
| 9 | `opening_balance_calc` | Single-row scalar — the **previous-year December End Balance** that becomes January's Opening Balance. Re-runs the entire payment-data union for `p_year - 1` and computes the carry-forward. |
| 10 | `prev_year_data` | Inner CTE of `opening_balance_calc`. Same shape as `level3_with_hierarchy` but for `p_year - 1`. |
| 11 | `balance_calculations` | Emits the Opening Balance row (one record), with 12 monthly amounts computed cumulatively from `jan_opening_balance` + cumulative revenue − cumulative expenses + cumulative investment-with-cal_rule. |
| 12 | `report_union` | Emits all remaining rows — Level 1 / Level 2 / Level 3 / Level 1 Subtotal / Sales Profit / End Balance — and UNIONs them with the Opening Balance from `balance_calculations`. Final ORDER BY clause produces the canonical row order. |

### 3.0.1 Hidden helper-function dependencies (legacy PL/pgSQL)

The function calls two other PL/pgSQL helpers. The refactor **does NOT port these to the local DB** (per the api-side rule); it reimplements them in Java service code:

| Helper | Used by | Replacement in refactor |
|---|---|---|
| `wb_get_appr_stts(biz_key, reg_id, reg_id, appr_seq_no, appr_type_cd)` returning `'C'` for completed | Approval-status gate on the 3 non-bank sources (`scdul_pymn_sumr`, `ovrs_pymn_mstr`, `card_trns_hstr`). | Java method `ApprovalStatusResolver.isCompleted(...)` walking the approval-line tables. May land as a simple lookup against `APPR_LINE_INFM_DETL.APPR_STTS_CD` aggregated to `'C'` if all approvers have signed; verify in Phase 2. |
| `wb_get_chart_acct_level_text(biz_key, chart_acct_seq_no, chart_acct_cd)` returning `'Level 1'` / `'Level 2'` / `'Level 3'` | Hierarchy-level classification for filtering and emitting `row_type`. | Java method `ChartOfAccountsHierarchy.levelOf(code)` walking `parent_chart_acct_cd` chains to depth (0 → L1, 1 → L2, 2 → L3). |

### 3.1 Source tables (all in PROD Active Table List)

| Table | Role in the report | Local Flyway status |
|---|---|---|
| `CHART_ACCT_INFM` | The **row backbone**. Tenant-scoped Chart of Accounts with 3-level hierarchy. Restricted root rows `ACC-01` / `ACC-02` / `ACC-03` are seeded for every tenant (V96) as the three Level-1 categories. The function reads `chart_acct_cd`, `chart_acct_nm_ko`, `chart_acct_nm_en`, `parent_chart_acct_cd`, `cal_rule`, `stts`, `del_yn`. | ✅ V94 schema + V96 restricted roots + V97 demo data (9 of DEV's 29 active rows; top-up needed for parity). |
| `ACCT_TRNS_HSTR` | Bank transaction history. The function reads `chart_acct_seq_no`, `chart_acct_cd` **directly off this table** (a column on the row, not via a join). Transaction date / amount / currency / bank_cd / acct_no for the source row. The function joins to `acct_trns_rcv_pay_infm` only to enforce the confirmation filter (`cnfm_yn = 'Y'`). | ✅ V13 + V23 column parity. |
| `ACCT_TRNS_RCV_PAY_INFM` | **Confirmation gate** for bank transactions — only `cnfm_yn = 'Y'` rows pass through. Joined via `(biz_key, acct_seq_no)` — **NOT** the composite `(biz_key, bank_cd, acct_no, trsc_dt)` PK. The `rcv_pay_usag_cd` / `usag_detl_cd` / `vas_acct_cd` columns on this table are NOT consulted by the cash-flow report. (Rev 1 incorrectly documented them as the bank-side classifier.) | ✅ V13 + V21. |
| `CARD_TRNS_HSTR` | Corporate card transactions. Reads `chart_acct_seq_no`, `chart_acct_cd` directly off the row. Joined to `expt_resol_mstr` for the approval-status gate. | ❌ **NOT migrated locally.** Lives in DEV's `wecmsvndev` schema (not `public`). Must ship in Phase 1 as a new Flyway migration (`V166__card_trns_hstr_schema.sql`). |
| `OVRS_PYMN_MSTR` | Overseas payments. Reads `chart_acct_seq_no`, `chart_acct_cd`, `trnf_amt`, `trnf_ccy_cd`, `dbt_acct_cd`, `dbt_acct_no`, `appr_seq_no`, `reg_dtm`. Joined to `expt_resol_mstr` for the approval-status gate. | ✅ V130 (overseas payment schema MR). |
| `SCDUL_PYMN_SUMR` (header) | Scheduled payment summary header. Reads `scdul_pymn_no`, `out_bank_cd`, `wdrl_act_no`, `ccy_cd`, `appr_seq_no`, `reg_dtm`. | ✅ V131 (internal transfer schema MR). |
| `SCDUL_PYMN_SUMR_DETL` (detail) | Scheduled payment line items. Reads `chart_acct_seq_no`, `chart_acct_cd`, `tran_amt`. Joined to the header by `(biz_key, scdul_pymn_no)`, then to `expt_resol_mstr` for approval status. | ✅ V131. |
| `EXPT_RESOL_MSTR` | **NOT a row source.** Used only as the join target for `wb_get_appr_stts(...)` to confirm the source transaction's approval is in state `'C'` (Completed). The function joins the 3 non-bank sources to this table on `appr_seq_no` and gates on the helper's return value. | ✅ V109 (approval box schema MR). |

**Sibling table not on the report path:** `CARD_TRNS_RCV_PAY_INFM` exists on DEV (4 rows tenant-wide) but is NOT joined by `get_cashflow_report`. Card-side classification is via `card_trns_hstr.chart_acct_cd` directly (same shape as bank). The sibling exists for a different feature (card-payment reconciliation) — we may still want to migrate it for completeness, but it's not on this report's critical path.

### 3.2 Row vocabulary (verbatim from DEV function output)

Every emitted row carries `row_type` ∈ one of seven verbatim string values. The refactor preserves these literal strings on the wire so the frontend's existing `classifyRowType` switch keeps working.

| `row_type` | Emitted for | Frontend variant |
|---|---|---|
| `"Opening Balance"` | Single row at the top — month N's opening = month N-1's End Balance. Total column = 0 (the column carries the opening of January, which has no value). | `openingBalance` (sticky-highlight) |
| `"Level 1"` | One per restricted root (`ACC-01` REVENUE, `ACC-02` EXPENSES, `ACC-03` INVESTMENT). `display_name` is the concatenated `chart_acct_nm_ko + ' ' + chart_acct_nm_en` (e.g. `"수입 REVENUE"`). | `header` |
| `"Level 2"` | Direct children of an `ACC-*` root. `display_code` = `chart_acct_cd`. `display_name` concatenates KO + EN. `item_code` is **empty** (only Level 3 rows carry one). | `subgroup` |
| `"Level 3"` | Grandchildren (`parent_l2_code` set to the parent L2's `chart_acct_cd`). `display_code` = `chart_acct_cd`. `item_code` = `chart_acct_cd`. `item_name` = English-only label. | `detail` |
| `"Level 1 Subtotal"` | Three per report — one per Level-1 root. Two carry `display_code = 'S. TOTAL'` (`수입 부분합` for ACC-01 / `지출 부분합` for ACC-02). The third for ACC-03 carries `display_code = 'NON SALES PROFIT'`. | `subtotal` |
| `"Sales Profit"` | Single row near the bottom — sum across months of `S.TOTAL(REVENUE) − S.TOTAL(EXPENSES)`. `display_code = 'SALES PROFIT'`, `display_name = 'SALES PROFIT (총수입-총지출)'`. **Note: title-case `"Sales Profit"`, not `"SALES PROFIT"`** — the current stub `CashFlowReportService.ROW_TYPE_SALES_PROFIT = "SALES PROFIT"` is a parity bug to fix in Phase 2. | `salesProfit` |
| `"End Balance"` | Final row. Month N's End Balance = month N's Opening Balance + month N's (revenue − expense). | `endBalance` |

**Removed from the previous doc:** `"NON SALES PROFIT"` is **not** its own `row_type`. The function emits it as a `"Level 1 Subtotal"` whose `display_code` happens to be `'NON SALES PROFIT'`. The stub's `ROW_TYPE_NON_SALES_PROFIT = "NON SALES PROFIT"` constant is a parity bug to remove in Phase 2.

### 3.3 Group key (`group_key` / `GRP_CD`)

The function attaches a `group_key` to every row used as a stable secondary sort. Verbatim values observed:

- `"AAAAA-OPENING-BALANCE"` — Opening Balance row.
- `"ACC-01"`, `"ACC-02"`, `"ACC-03"` — every Level 1 / Level 2 / Level 3 / Level 1 Subtotal row stamped with its root.
- `"ZZZZZ-SALES-PROFIT"` — Sales Profit row.
- `"ZZZZZ-END-BALANCE"` — End Balance row.

The `AAAAA-` / `ZZZZZ-` prefixes are how the function ensures the row order is:
1. Opening Balance (AAAAA sorts first)
2. ACC-01 group: L1, L2*, L3*, L1 Subtotal
3. ACC-02 group: L1, L2*, L3*, L1 Subtotal
4. ACC-03 group: L1, L2*, L3*, L1 Subtotal
5. Sales Profit (ZZZZZ sorts after ACC-*)
6. End Balance

The refactor preserves this exact group-key vocabulary so the frontend's display order stays identical without an extra sort layer.

### 3.4 Sort order (`sort_order` / `DISP_ORD`)

`sort_order` is **local to a group**, not global. Observed cycle within each ACC-* group: `1` (L1) → `2` (L2 rows) → `3` (L3 rows) → `4` (L1 Subtotal). Opening Balance = `0`; Sales Profit = `5`; End Balance = `6`.

The refactor emits rows in their natural assembled order (Opening Balance → each ACC-* group in turn → Sales Profit → End Balance). Clients **must not** re-sort by `sort_order` alone.

### 3.5 Monetary fields

- All twelve `*_AMT` fields (`JAN_AMT` .. `DEC_AMT`) plus `TOTAL_AMT` are `NUMERIC` on the wire. Currency-aware formatting is the frontend's responsibility via `formatAmount(value, ccyCd, includeSymbol)` per the workspace typography rule.
- `display_currency` carries the resolved base currency for the row (always equal to the request's `baseCurrency`).
- The function returns string-encoded numerics (`"55000.000000"`); Spring serialises as JSON number after `BigDecimal` parsing.

### 3.6 Opening / End Balance arithmetic (verified)

Verified against DEV (tenant `VN_VNM0004_000001`, year 2025) AND the function body:

```
S.TOTAL REVENUE  Jan: 55_000    Feb: 55_000    Mar: 220_000   ... Aug: 1_910_000   Sep: 130_000
S.TOTAL EXPENSE  Jan: 0         Feb: 0         Mar: 0         ... Aug: 0           Sep: 0

Opening Balance  Jan: 0         Feb: 55_000    Mar: 110_000   ... Aug: 470_000     Sep: 2_380_000   Dec: 2_510_000
End Balance      Jan: 55_000    Feb: 110_000   Mar: 330_000   ... Aug: 2_380_000   Sep: 2_510_000   Dec: 2_510_000
```

In the verified 2025 sample `OpeningBalance[Jan] = 0` because tenant 000001 had no 2024 transactions feeding any chart code, so the carry-forward computes to zero. **It is NOT a hardcoded zero** (rev 1 of this doc was wrong).

The actual `OpeningBalance[Jan]` formula (from the `opening_balance_calc` CTE):

```
OpeningBalance[Jan] = 0
                    + Σ over all Level-3 codes under ACC-01 of (sum of monthly amounts for p_year - 1)
                    − Σ over all Level-3 codes under ACC-02 of (sum of monthly amounts for p_year - 1)
                    + Σ over the hardcoded codes {'2001026','2001027','2002003','2002002'} under ACC-03 of
                         (if cal_rule='P': +sum / if cal_rule='M': −sum / else: 0)
```

The prior-year aggregation re-runs the entire payment-data union with `payment_year = p_year - 1`. Only ONE year back — the function does NOT walk further (so for `p_year = 2025`, the function looks at 2024; it does NOT roll up 2023 + 2024 into the opening).

Per-month formulas (for N = 1..12, derived from `balance_calculations` CTE):

```
OpeningBalance[N] = jan_opening_balance
                  + Σ months 1..N-1 of RevenueTotal
                  − Σ months 1..N-1 of ExpenseTotal
                  + Σ months 1..N-1 of InvestmentWithCalRule

EndBalance[N] = jan_opening_balance
              + Σ months 1..N of RevenueTotal
              − Σ months 1..N of ExpenseTotal
              + Σ months 1..N of InvestmentWithCalRule

InvestmentWithCalRule[N] = Σ over rows where chart_acct_cd ∈ {'2001026','2001027','2002003','2002002'}:
                              (cal_rule='P' → +monthly_amount,
                               cal_rule='M' → −monthly_amount,
                               else        →  0)
```

**Investment contribution clarification.** Rev 1 of this doc said "ACC-03 INVESTMENT does NOT contribute to End Balance arithmetic". That's **partly wrong**:

- Only the **four hardcoded chart codes** (`'2001026'`, `'2001027'`, `'2002003'`, `'2002002'`) contribute.
- Each contributes signed by its `chart_acct_infm.cal_rule` (`'P'` → add, `'M'` → subtract, anything else → zero).
- For tenant 000001 (none of those four codes exist in its CoA), the investment contribution is always zero, which is why the rev 1 observation held for that tenant.

The hardcoded code list is a **production tenant-specific carve-out** baked into the function. The refactor preserves it verbatim in `BalanceCalculator` as a `Set<String>` constant with a clear name (e.g. `INVESTMENT_BALANCE_CONTRIBUTING_CODES`) and a `// Carved over from get_cashflow_report() — production parity` comment.

- `OpeningBalance.totalColumn = 0` (hardcoded zero in the function — see `balance_calculations.total_amount`).
- `EndBalance.totalColumn = EndBalance[12]` (year-end balance).

### 3.7 Sales Profit

`SalesProfit[N] = RevenueTotal[N] − ExpenseTotal[N]` per month — confirmed in `report_union` (not yet quoted here; can be cited in Phase 2). `SalesProfit.totalColumn = Σ over months of (RevenueTotal[N] − ExpenseTotal[N])`. ACC-03 (INVESTMENT) does NOT contribute to Sales Profit — only ACC-01 and ACC-02 do. This is different from the balance calculation, which DOES include the cal_rule-tagged INVESTMENT codes.

### 3.8 Level 1 / Level 2 / Level 3 amounts

- **Level 3** amounts are the raw monthly aggregation of transactions classified into that `chart_acct_cd`.
- **Level 2** amounts = sum of all Level 3 amounts whose `parent_l2_code` = this L2's `chart_acct_cd`. (Empty L2s — those without any Level 3 children — surface as zero rows; they still render so the user sees the structure.)
- **Level 1** amounts = sum of all direct L2 children's amounts (which transitively roll up the L3s).
- **Level 1 Subtotal** amounts = same as Level 1 amounts for that root. (The function emits both because the legacy UI puts the section header at the top of each group and a footer subtotal at the bottom.)

This rollup is computed in Java in Phase 2 — the service runs one transaction aggregation per source table, groups by `(chart_acct_cd, year_month)`, then walks the hierarchy to roll children up to parents.

### 3.9 Currency conversion (verified)

The `filtered_payment_data` CTE applies a per-row CASE with `ROUND()` precision varying by base currency:

| Source currency | Base = VND | Base = USD | Base = KRW |
|---|---|---|---|
| VND | × 1 (no-op) | ÷ usdRate **ROUND 4 dp** | ÷ krwRate **ROUND 2 dp** |
| USD | × usdRate **ROUND 2 dp** | × 1 (no-op) | (× usdRate ÷ krwRate) **ROUND 2 dp** |
| KRW | × krwRate **ROUND 2 dp** | (× krwRate ÷ usdRate) **ROUND 4 dp** | × 1 (no-op) |
| Other | × 1 (passthrough) | ÷ usdRate **ROUND 4 dp** | ÷ krwRate **ROUND 2 dp** |

**Refactor implementation note.** Use `BigDecimal.setScale(scale, RoundingMode.HALF_UP)` matching the precisions above (the function uses Postgres `ROUND()` which is half-up). The "Other" passthrough branch exists for safety — DEV data should not contain other currencies, but the function tolerates it.

**Rate column never consulted.** The `EXCH_RATE` column on `ACCT_TRNS_HSTR` (bank-supplied execution rate for that specific transaction) is NOT used by the cash-flow report. The function converts every amount through the user-supplied flat rate so users can do "what if today's rate applied to the whole year" projections. Refactor mirrors this exactly.

**Validation divergence.** The legacy `_act.jsp` unconditionally validates `USD_RATE_AMT` and `KRW_RATE_AMT` even when base = USD or KRW respectively. The refactor's `CashFlowReportService.validate` requires only the conditional rates (USD rate iff base ≠ USD, KRW rate iff base ≠ KRW) — see deliberate divergence D2 in §6.

### 3.10 Bank filter (verified — wider than rev 1 claimed)

The filter is permissive — a row passes if ANY of three sub-conditions matches for ANY entry in the filter array:

1. `bank_filter->>'BANK_CD' IS NOT NULL AND row.bank_cd = bank_filter->>'BANK_CD'`
2. `bank_filter->>'ACCT_NO' IS NOT NULL AND row.acct_no = bank_filter->>'ACCT_NO'`
3. `bank_filter->>'BANK_CD' IS NOT NULL AND bank_filter->>'ACCT_NO' IS NOT NULL AND row.bank_cd = bank_filter->>'BANK_CD' AND row.acct_no = bank_filter->>'ACCT_NO'`

The third condition is redundant — conditions 1 and 2 already cover it. The net effect is: a filter entry with only `BANK_CD` matches any account at that bank; a filter entry with only `ACCT_NO` matches any bank that has that account number; a filter entry with both matches the precise account (via condition 1 alone, which is `bank_cd=X` AND ignores `acct_no`). **The refactor preserves this exact behaviour** so downstream Excel/print exports stay byte-identical.

**Filter scope:** ALL FOUR sources are filtered through `filtered_payment_data`. Rev 1 incorrectly said `OVRS_PYMN_MSTR` / `EXPT_RESOL_MSTR` / `SCDUL_PYMN_SUMR` were unfiltered — they are filtered by `(opm.dbt_acct_cd, opm.dbt_acct_no)` / `(sps.out_bank_cd, sps.wdrl_act_no)` / `(cth.bank_cd, cth.card_no)` respectively. The `apd.bank_cd` / `apd.acct_no` columns are mapped in each UNION branch's SELECT list — they're NOT the bank/account on the original table, they're the source-table-specific column that semantically plays that role. Refactor mirrors the per-source column mapping.

Filter applies to ALL FOUR sources because the FX-conversion + filter is at the `all_payment_data` level (downstream of the UNION). All 4 source rows are first normalized to a common `(bank_cd, acct_no)` projection before the filter runs.

`EXPT_RESOL_MSTR` does NOT appear in this filter because it's not a row source — it's the approval-status join target.

### 3.11 Quarter handling (verified — server DOES filter)

`quarter` ∈ {1, 2, 3, 4} or `null`. When non-null, `filtered_payment_data` excludes transaction rows whose `payment_month` is not in that quarter's months. **However**, the function still emits all 12 month columns — months outside the quarter just resolve to zero amounts via `COALESCE(SUM(...), 0)`.

This means:

- The wire shape always has 12 months — frontend column-hide remains the cosmetic layer.
- The Opening/End Balance carry-forward for months OUTSIDE the requested quarter is computed against ZERO source amounts for those months. For Q2 (months 4-6), `OpeningBalance[Jul] = jan_opening_balance + sum(rev[1..6]) − sum(exp[1..6])` where months 1-3 contribute zero (filtered out) and only months 4-6 carry their real values. This is **arguably surprising** for the user but it's the legacy behaviour — refactor mirrors it.
- A QA test should verify a Q2 query returns Q1 months at zero AND that End Balance at Dec carries forward through that zero-padding. **Document this in the parity-audit harness.**

The previous-year carry-forward (`opening_balance_calc`) is NOT quarter-filtered — it always sums the entire 12 months of the previous year. So opening Jan stays the same regardless of which quarter is requested for the current year.

---

## 4. REST endpoint (already shipped — re-document for completeness)

### `GET /api/v1/banking/reports/cash-flow-report`

Implemented at [CashFlowReportController.java:36](../../../../src/main/java/com/kosign/wecms/domain/banking/controller/CashFlowReportController.java#L36).

| Param | Type | Required | Notes |
|---|---|---|---|
| `year` | `int` (YYYY) | yes | 1900–2999. |
| `quarter` | `int` 1..4 | no | Forwarded to service but does not shrink the response. |
| `baseCurrency` | enum `VND` / `USD` / `KRW` | yes | Display currency. |
| `usdRate` | `BigDecimal` | conditional | Required when `baseCurrency != USD`. |
| `krwRate` | `BigDecimal` | conditional | Required when `baseCurrency != KRW`. |
| `bankFilters` | URL-encoded JSON array of `{ bank_cd, acct_no }` | no | Empty / omitted = all accounts. |

### `POST /api/v1/banking/reports/cash-flow-report/export`

Implemented at [CashFlowReportController.java:57](../../../../src/main/java/com/kosign/wecms/domain/banking/controller/CashFlowReportController.java#L57). Standard `ExcelExportRequest` envelope routed through the shared `ExcelExportService`. The provider class at [CashFlowReportExportProvider.java](../../../../src/main/java/com/kosign/wecms/domain/banking/export/CashFlowReportExportProvider.java) re-runs the same service call to produce the same row stream — no parallel data path.

### Response shape

Already implemented at [CashFlowReportResponse.java](../../../../src/main/java/com/kosign/wecms/domain/banking/dto/CashFlowReportResponse.java). Wire shape (snake_case via global Jackson `SNAKE_CASE` strategy):

```json
{
  "year": 2025,
  "quarter": null,
  "base_currency": "VND",
  "usd_rate": "26000",
  "krw_rate": "19",
  "rows": [
    {
      "display_order": "00010",
      "display_cd": "Opening Balance",
      "display_nm": "Opening Balance",
      "item_cd": null,
      "item_nm": null,
      "row_type": "Opening Balance",
      "group_cd": "AAAAA-OPENING-BALANCE",
      "parent_l2_cd": null,
      "display_currency": "VND",
      "monthly": { "jan": "0", "feb": "55000", "...": "..." },
      "annual_total": "0"
    },
    {
      "display_order": "00100",
      "display_cd": "ACC-01",
      "display_nm": "수입 REVENUE",
      "item_cd": null,
      "item_nm": null,
      "row_type": "Level 1",
      "group_cd": "ACC-01",
      "parent_l2_cd": null,
      "display_currency": "VND",
      "monthly": { "jan": "55000", "...": "..." },
      "annual_total": "2510000"
    }
  ]
}
```

> **Compatibility check.** The currently-deployed DTO field names are
> `displayOrder` / `displayCd` / `displayNm` / `itemCd` / `itemNm` / `rowType`
> / `groupCd` / `parentL2Cd` / `displayCurrency` / `monthly` / `annualTotal`,
> auto-snake-cased on the wire to `display_order` / `display_cd` / etc. The
> frontend mapper at [lib/api/mappers.ts:561](../../../../../wecms-web/lib/api/mappers.ts#L561)
> reads those keys and translates back to camelCase. **No DTO shape change is
> needed in Phase 2.**

### Errors

- `200 + WBE_VALIDATION` — missing rate for a non-base currency, year out of range, bad quarter.
- `200 + WBE_CASH_FLOW_RATE_REQUIRED` — explicit "rate required" message with currency arg. Already in `messages.properties`.
- `401` — JWT missing/invalid (handled at `JwtAuthenticationEntryPoint`, not the service).
- `200 + WBE9999` — unexpected fault (global handler).

---

## 5. Frontend page (already shipped — re-document for completeness)

- Route: [app/(dashboard)/banking/reports/cash-flow/page.tsx](../../../../../wecms-web/app/(dashboard)/banking/reports/cash-flow/page.tsx) (thin shell, composes `<CashFlowClient />`).
- Client wrapper: [CashFlowClient.tsx](../../../../../wecms-web/components/banking/reports/cash-flow/CashFlowClient.tsx) — owns filter state, calls `fetchCashFlow()` via `useApiData(loader, [searchKey])`, wires Excel export through `exportExcel()`.
- Page component: [CashFlowPage.tsx](../../../../../wecms-web/components/banking/reports/cash-flow/CashFlowPage.tsx) — empty-state placeholder until first Search (per CLAUDE.md §7b), then `<ReportLayout>` + `<CashFlowMatrix>`. Quarter selection hides month columns client-side. Row variant styling matches the seven `row_type` values verbatim.
- Types: [types.ts](../../../../../wecms-web/components/banking/reports/cash-flow/types.ts) — camelCase frontend rows, `MONTH_KEYS`, `monthIndicesForQuarter`.
- Wire types: [lib/types/api.ts:827 `ApiCashFlowReport`](../../../../../wecms-web/lib/types/api.ts#L827).
- Mapper: [lib/api/mappers.ts:561 `mapCashFlowReport`](../../../../../wecms-web/lib/api/mappers.ts#L561).
- Excel export: [lib/excel.ts](../../../../../wecms-web/lib/excel.ts) + per-column registry built in `CashFlowClient.handleExport`.
- Print: [lib/print.ts](../../../../../wecms-web/lib/print.ts) `printReport(...)`, fed by `CashFlowPage.handlePrint`.
- i18n: `report.cash_flow.*` keys in `locales/{en,ko,vi}.json`.

**No frontend changes required for Phase 2 backend work.** The page is fully wired and renders correctly against the deterministic stub; once the service returns real numbers, the same UI displays them without code change.

---

## 6. Deliberate divergences from legacy

Documented inline in the `CashFlowReportController` / `CashFlowReportService` Javadoc plus this section so any reviewer can find the override audit trail.

| # | Legacy behaviour | Refactor behaviour | Reason |
|---|---|---|---|
| D1 | `WSVC.login = N` (public endpoint) | Authentication required (JWT) | Public access to a tenant-scoped report is a data leak. Confirmed override 2026-05-13 (Sonny). |
| D2 | `_act.jsp` requires both `USD_RATE_AMT` and `KRW_RATE_AMT` even when base currency = USD or KRW | Refactor requires `usdRate` only when `baseCurrency != USD`, `krwRate` only when `baseCurrency != KRW` | Legacy validation is over-strict and confusing for the user (why supply a USD→VND rate when display is USD?). Refactor's conditional check is documented at `CashFlowReportService.validate:95-100`. |
| D3 | Current stub emits `"SALES PROFIT"` and `"NON SALES PROFIT"` as `row_type` values | Refactor emits the verbatim function output: `"Sales Profit"` (title-case) and `"Level 1 Subtotal"` (with `NON SALES PROFIT` as the `display_code`) | The stub's constants `ROW_TYPE_SALES_PROFIT = "SALES PROFIT"` and `ROW_TYPE_NON_SALES_PROFIT = "NON SALES PROFIT"` are parity bugs. Phase 2 fixes both: rename the first constant, delete the second. Frontend `classifyRowType` already accepts the legacy strings via `case "SALES PROFIT":` and falls through to `detail` for unknown values — so the bug is invisible in the UI but breaks parity at the JSON boundary. |
| D4 | `get_cashflow_report()` (~97 KB) is a single PL/pgSQL function consuming `wb_get_appr_stts(...)` and `wb_get_chart_acct_level_text(...)` | Refactor implements the aggregation in Java; replaces both helper functions with Java equivalents (`ApprovalStatusResolver`, `ChartOfAccountsHierarchy.levelOf`) | Aligns with the api-side `DB Function / Legacy Function Policy` — no new legacy PL/pgSQL helpers added to feature code. |
| D5 | Legacy `_view.jsp` uses Korean/English concatenated `display_name` (e.g. `"수입 REVENUE"`) | Same — refactor preserves the concatenation in the service mapper | Frontend already renders this string verbatim; no change. |
| D6 | Hardcoded INVESTMENT code list `{'2001026','2001027','2002003','2002002'}` in the balance arithmetic | Same — refactor preserves the list as `INVESTMENT_BALANCE_CONTRIBUTING_CODES` in `BalanceCalculator` | Production-tenant carve-out baked into the legacy function. Without it, prior-year carry-forward will diverge for tenants using these codes. |

---

## 7. Phase 2 plan (next MR, not this one)

This Phase 0 doc establishes the design; Phase 2 implements it. Sketch only — code lands in a follow-up MR after sign-off:

### 7.1 Source table audit (completed 2026-05-18)

| Source | Local status |
|---|---|
| `CHART_ACCT_INFM` | ✅ V94 schema + V96 restricted roots + V97 demo data (9 rows for tenant 000001 — DEV has 29; top-up planned in Phase 1) |
| `ACCT_TRNS_HSTR` | ✅ V13 + V23 |
| `ACCT_TRNS_RCV_PAY_INFM` | ✅ V13 + V21 |
| `CARD_TRNS_HSTR` | ❌ **NOT migrated locally.** Phase 1 ships V166 with schema + JPA entity. |
| `OVRS_PYMN_MSTR` | ✅ V130 (overseas payment schema MR) |
| `EXPT_RESOL_MSTR` | ✅ V109 (approval box schema MR) — used only as approval-status join target |
| `SCDUL_PYMN_SUMR` | ✅ V131 (internal transfer schema MR) |
| `SCDUL_PYMN_SUMR_DETL` | ✅ V131 |

### 7.2 Top-up seed for `CHART_ACCT_INFM`

V97 has 9 rows. The legacy report for tenant 000001 references 29 active rows (every L1/L2/L3 in the function's `report_union` emit). Top up to the full 29 in a new SQL seed (manual exec) so the report shape matches DEV row-for-row.

### 7.3 Surgical transaction seed (per Phase 0 finding)

Earlier seed plans assumed "30 latest rows per table". The decoded function shows that only specific rows feed the verified 2025 non-zero output — most rows on DEV are irrelevant for parity testing. The Phase 1 seed plan is now **surgical**: query DEV for exactly the rows that produce non-zero cells in the verified output, seed exactly those.

**Identifying the rows:** for each source table, the SELECT that ships in Phase 1 mirrors the function's `all_payment_data` UNION branch for that table, plus filters by `payment_year IN (2024, 2025)` (need 2024 too for the prior-year opening-balance calc).

### 7.4 Phase 1 deliverables (revised)

- `wecms-api/src/main/resources/db/migration/V166__card_trns_hstr_schema.sql` — table schema + helper indexes (idempotent). Includes sibling `CARD_TRNS_RCV_PAY_INFM` for completeness even though the report doesn't use it.
- `wecms-api/src/main/java/com/kosign/wecms/domain/banking/entity/CardTrnsHstr.java` — JPA entity for the new table. Composite PK `(BIZ_KEY, CARD_SEQ_NO)`. Extends `BaseEntity` for audit columns. (Sibling `CardTrnsRcvPayInfm` entity NOT created in Phase 1 — no consumer.)
- `wecms-api/src/main/java/com/kosign/wecms/domain/banking/repository/CardTrnsHstrRepository.java` — `JpaRepository<CardTrnsHstr, CardTrnsHstrId>` with one method: `aggregateMonthlyByChartCode(bizKey, year)` for the Phase 2 aggregator.
- `Claude Code (AI)/sql/cash_flow_report_parity_seed_20260518.sql` — INSERTs for all four source tables (`ACCT_TRNS_HSTR`, `CARD_TRNS_HSTR`, `OVRS_PYMN_MSTR`, `SCDUL_PYMN_SUMR(_DETL)`) plus the `CHART_ACCT_INFM` top-up. Filtered to tenant `VN_VNM0004_000001`, years 2024 + 2025, preserving DEV PKs. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. **Developer executes manually** (NOT Flyway, per user request).

### 7.4 Backend services

```
domain/banking/
├── service/
│   └── cashflow/
│       ├── CashFlowReportService.java       (replace stub buildRows())
│       ├── ChartOfAccountsHierarchy.java    (walks CHART_ACCT_INFM, builds L1→L2→L3 tree)
│       ├── BankTransactionAggregator.java   (ACCT_TRNS_HSTR + ACCT_TRNS_RCV_PAY_INFM, monthly per item_code)
│       ├── CardTransactionAggregator.java   (CARD_TRNS_HSTR)
│       ├── OverseasPaymentAggregator.java   (OVRS_PYMN_MSTR)
│       ├── ExpenseResolutionAggregator.java (EXPT_RESOL_MSTR)
│       ├── ScheduledPaymentAggregator.java  (SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL)
│       ├── FxConverter.java                 (the rate-table logic from §3.9)
│       └── BalanceCalculator.java           (Opening/End/Sales Profit per §3.6/3.7)
└── repository/
    ├── ChartAcctInfmRepository.java         (extend existing; add findHierarchyByBizKey)
    ├── AcctTrnsHstrRepository.java          (add aggregateMonthlyByItemCode)
    ├── ... per-source repos
```

Service composes: hierarchy walk → 5 aggregations → FX conversion → rollup (L3 → L2 → L1 → L1 Subtotal) → balance calculator → emit rows in the verbatim order from §3.3.

### 7.5 Parity test

`CashFlowReportParityTest` (JUnit + Testcontainers OR same Spring profile against the local seeded DB):

1. Snapshot DEV's `get_cashflow_report('VN_VNM0004_000001', 2025, NULL, 'VND', 26000, 19, NULL)` output into `src/test/resources/cash-flow/parity-golden-2025-vnd.json`.
2. Call `CashFlowReportService.search(2025, null, "VND", 26000, 19, [])` against the seeded local DB.
3. Diff: row count must match exactly; every row's `(row_type, display_cd, item_cd, parent_l2_cd, group_cd)` must match; every cell must match within `0.01` rounding tolerance.
4. Add the same test for FX variants (`baseCurrency = USD`, `baseCurrency = KRW`) using transactions of mixed currencies — confirms §3.9.
5. Add the same test for bank-filtered queries — confirms §3.10.

### 7.6 Cleanup

- Remove the stub `buildRows()` plus the seven `ROW_TYPE_*` constants from `CashFlowReportService`.
- Remove the now-superseded Javadoc explaining the stub approach.
- Update `CashFlowReportService` Javadoc to point at this doc.

---

## 8. Open questions

### 8.1 Resolved by Phase 0 function decode (2026-05-18)

- ✅ **Q: How is each `CARD_TRNS_HSTR` classified?** → Via `chart_acct_cd` column directly on the row, NOT through a sibling `RCV_PAY_INFM` join. Same pattern as bank transactions (which also use `chart_acct_cd` directly, not via the rcv_pay table).
- ✅ **Q: Does the report include future-dated `SCDUL_PYMN_SUMR` rows?** → No — the report uses `payment_month` derived from `sps.reg_dtm` (registration date), not the scheduled execution date. A payment scheduled in May for August execution shows in the May column.
- ✅ **Q: What `EXPT_RESOL_MSTR` status filter applies?** → Approval gate via `wb_get_appr_stts(...) = 'C'`. Only rows whose approval chain is Completed pass through; in-progress and rejected rows are excluded from the three non-bank sources. Bank transactions (`acct_trns_hstr`) gate on `cnfm_yn = 'Y'` instead.

### 8.2 Still open (raise before Phase 2 starts)

- **`wb_get_appr_stts(...)` decode.** The function signature `(biz_key, reg_id, reg_id, appr_seq_no, appr_type_cd)` is unusual — passing `reg_id` twice. Phase 2 should read this helper's body to understand the approval-chain walk, then port the logic to `ApprovalStatusResolver` in Java. The `appr_type_cd` parameter suggests different approval-line types behave differently — check.
- **Hardcoded INVESTMENT codes.** The list `{'2001026','2001027','2002003','2002002'}` is in the function body verbatim. Phase 2 should confirm with Sonny / Mr. Jung whether this is documented business logic (and thus stays hardcoded in `BalanceCalculator`) or a bug that should be data-driven (e.g. via a new column on `chart_acct_infm`).
- **Tenant data confirmation.** Tenant `VN_VNM0004_000001` is the only DEV tenant with a Chart of Accounts. Phase 2 parity tests use this tenant only. For cross-tenant isolation tests, the Phase 2 harness should seed a minimal CoA for tenant 000014 (HAENGSUNG) and confirm the report returns empty for tenants without any CoA rows.
- **Quarter-filter zero-padding semantics.** §3.11 documents a surprising behaviour: requesting Q2 zeros out months 1, 3, 7-12 in the source aggregation but still emits 12 columns with Opening/End Balance carrying the year-to-date math against zeros. Phase 2 parity test must cover this; UX may want to revisit whether the legacy semantics are actually desired (a follow-up product question, not blocking).
- **Item-code labelling for screenshot.** The screenshot's item codes (`1025001`, `7001001`, `6018001/2`, `2001001-2001009`) are from a different production tenant (matching the hardcoded INVESTMENT carve-out — `2001026/27` align with the `2001XXX` numeric family). **No Phase 0/1/2 action required** for the refactor; the report will render those codes correctly once HAENGSUNG's CoA lands locally.

These questions do not block Phase 0 / 1 sign-off. The first two are resolved in Phase 2 by reading the helper functions and consulting Sonny.

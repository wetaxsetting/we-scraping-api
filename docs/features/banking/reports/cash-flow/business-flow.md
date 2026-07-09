# Cash Flow — Business Flow & Calculation Rules

All logic here is traced to source. The legacy single source of truth was the PostgreSQL function
**`get_cashflow_report(...)`** (schema `wecmsvndev` on JEX DEV), invoked by IDO `CASH_FLOW_R001`. The
refactor's **live path now calls `get_cashflow_report` directly** (`CashFlowReportService.search` →
`CashFlowReportRepository`, decision 2026-06-13). The CTE-by-CTE Java pipeline under
`domain/banking/service/cashflow/*` is **retained but off the active path as of 2026-06-13**. §2 documents
the legacy CTE anatomy (the spec); §3–§4 document the financial rules; §5 documents the retained Java
pipeline (off the active path); §6 documents the frontend re-shaping.

---

## 1. Request flow

### Legacy (JEX)

```
User opens  cash_0001_001.act
   ├─ onload (cash_0001_001.ts)
   │     ├─ fn_getExchRate()  → AJAX cash_0001_001_r002 → IDO BANK_EXCH_RATE_INFM_R002 → BANK_EXCH_RATE_INFM
   │     │       (hardcoded BANK_CD='11704088' Shinhan, SEQ_NO='2') → USD/KRW rates, prefill #exchange-rate
   │     ├─ fn_listBank()     → AJAX cash_0001_001_r003 → AcctMstrDAO → ACCT_MSTR → bank checkbox list
   │     └─ fn_buildYearDropdown()  (current year .. current-8)
   └─ User clicks #search → renderTable()
         └─ fn_loadData() → AJAX cash_0001_001_r001 → cash_0001_001_r001_act.jsp
               → IDO CASH_FLOW_R001 → SELECT … FROM get_cashflow_report(:BIZ_KEY,:DATA_YEAR,
                     :YEAR_QUAT,:BASE_CCY_CD,:USD_RATE_AMT,:KRW_RATE_AMT,:BANK_FILT)
         → renderTableBody()  (hierarchical L1/L2/L3 + subtotals + profit rows)
         → renderTableFooter() (End Balance → tfoot)  →  updateOpeningBalance() (Opening → thead)
         → applyQuarterVisibility() (client-side month show/hide; server always called YEAR_QUAT=null)
```

Backend handler ([cash_0001_001_r001_act.jsp:19-36](../../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/cash/cash_0001_001_r001_act.jsp)):
validates `DATA_YEAR`, `BASE_CCY_CD`, `USD_RATE_AMT`, `KRW_RATE_AMT` present, then forwards all seven
inputs to IDO `CASH_FLOW_R001` and returns the rows under key `REC`.

### Refactor (Spring + Next.js)

```
Next.js  /banking/reports/cash-flow  (CashFlowClient)
   ├─ fetchExchangeRates()  → GET /api/v1/banking/exchange-rates → seed usdRate/krwRate inputs
   ├─ fetchAccounts()       → GET /api/v1/banking/accounts        → BankFilterPopover options
   └─ Search → fetchCashFlow({year, baseCurrency, usdRate, krwRate, bankFilters})   ← NO quarter sent
         → GET /api/v1/banking/reports/cash-flow-report
               → CashFlowReportController.search()
                  → CashFlowReportService.search(year, quarter, baseCurrency, usdRate, krwRate, bankFilters)
         → mapCashFlowReport() → groupReport() (rebuild L1/L2/L3 hierarchy) → <CashFlowMatrix>
   └─ Export → POST /api/v1/banking/reports/cash-flow-report/export → CashFlowExcelWriter → .xlsx blob
```

---

## 2. The legacy function `get_cashflow_report` (the spec the refactor reproduces)

```
get_cashflow_report(
    p_biz_key       varchar,   -- tenant
    p_year          integer,   -- report year
    p_quarter       varchar,   -- 'Q1'..'Q4' or NULL = all  (UI always sends NULL)
    p_base_currency varchar,   -- 'VND' | 'USD' | 'KRW'
    p_usd_rate      numeric,   -- VND per 1 USD
    p_krw_rate      numeric,   -- VND per 1 KRW
    p_bank_filters  text DEFAULT NULL  -- JSON array [{BANK_CD,ACCT_NO},...]
) RETURNS TABLE(sort_order, display_code, display_name, item_code, item_name,
    row_type, group_key, parent_l2_code, jan_amount..dec_amount, total_amount, display_currency)
```

A chain of 12 CTEs, in execution order:

| # | CTE | Purpose |
|---|---|---|
| 1 | `quarter_months` | Static `(quarter,month_num)` map: Q1→{1,2,3} … Q4→{10,11,12}. |
| 2 | `fixed_level1_categories` | The three roots `ACC-01`/`02`/`03` (`del_yn='N' AND stts='A'`); display_name = `ko ' ' en`. |
| 3 | `all_payment_data` | **The 4-source UNION ALL** (see §2.3). |
| 4 | `filtered_payment_data` | Year + quarter + bank filter + FX conversion applied to `all_payment_data`. |
| 5 | `monthly_summary` | Groups by `(chart_acct_seq_no, chart_acct_cd, payment_month)`, sums `converted_amount`. |
| 6 | `level3_with_hierarchy` | Self-joins `chart_acct_infm` L3→L2→L1, pivots to `jan..dec`, keeps only `Level 3` leaves under `ACC-01/02/03`. |
| 7 | `filtered_hierarchy` | Drops rows whose L1/L2 ancestor is not `stts='A'`. |
| 8–10 | `opening_balance_calc` / `prev_year_data` | Re-runs the entire union for `p_year-1` (FX only) → Jan opening = prior-year Dec end balance. |
| 11 | `balance_calculations` | Emits the Opening Balance row (12 cumulative months). |
| 12 | `report_union` | Emits L1 / L2 / L3 / L1-Subtotal / Sales Profit / End Balance, UNIONed with Opening; final `ORDER BY group_order, sort_order, display_code`. |

### 2.0 Hidden helper-function dependencies (legacy PL/pgSQL)

| Helper | Used by | Refactor replacement (Java) |
|---|---|---|
| `wb_get_appr_stts(biz_key, reg_id, reg_id, appr_seq_no, appr_type_cd)` → `'C'` for confirmed | approval gate on sources 1–3 | `ApprovalStatusResolver` + `ExptResolMstrRepository.findCompletedApprSeqNos` (filters `appr_type_cd='01'`). **Provably equivalent** — see §4 N0-A in the parity audit. |
| `wb_get_chart_acct_level_text(biz_key, seq_no, cd)` → `'Level 1'..'Level 5'` | hierarchy classification | `ChartOfAccountsHierarchy` (walks `parent_chart_acct_cd` chains; depth 0→L1, 1→L2, 2→L3). |

### 2.3 `all_payment_data` — the 4 transaction sources (UNION ALL)

Every source contributes `chart_acct_seq_no, chart_acct_cd, original_amount, original_currency,
bank_cd, acct_no, payment_year, payment_month`. All require `chart_acct_cd <> ''`.

| # | Source table(s) | Amount | Currency | Bank / Acct | Date column | Approval / confirm gate |
|---|---|---|---|---|---|---|
| 1 | `scdul_pymn_sumr_detl` ⋈ `scdul_pymn_sumr` ⋈ `expt_resol_mstr` | `spsd.tran_amt` | `sps.ccy_cd` | `sps.out_bank_cd` / `sps.wdrl_act_no` | `sps.reg_dtm` (`YYYYMMDDHH24MISS`) | `wb_get_appr_stts(...) = 'C'` |
| 2 | `ovrs_pymn_mstr` ⋈ `expt_resol_mstr` | `opm.trnf_amt` | `opm.trnf_ccy_cd` | `opm.dbt_acct_cd` / `opm.dbt_acct_no` | `opm.reg_dtm` | `wb_get_appr_stts(...) = 'C'` |
| 3 | `card_trns_hstr` ⋈ `expt_resol_mstr` | `cth.trsc_amt` | `cth.ccy_cd` | `cth.bank_cd` / `cth.card_no` | `cth.trsc_dt` (`YYYYMMDD`) | `wb_get_appr_stts(...) = 'C'` |
| 4 | `acct_trns_hstr` ⋈ `acct_trns_rcv_pay_infm` | `ath.trsc_amt` | `ath.ccy_cd` | `ath.bank_cd` / `ath.acct_no` | `ath.trsc_dt` (`YYYYMMDD`) | `atrpi.cnfm_yn = 'Y'` |

> **Business rule:** Cash Flow only reflects **confirmed / approved** money movement — pending or
> rejected payments are excluded (sources 1–3 via the approval gate, source 4 via `cnfm_yn='Y'`).

### 2.4 `filtered_payment_data` — currency conversion + year/quarter/bank filters

Currency conversion is per `p_base_currency`, with a **single** `ROUND(...)` per conversion (Postgres
`ROUND` = half-away-from-zero). Verbatim scales — these are the refactor's `FxConverter` contract too:

| Base | VND source | USD source | KRW source | Other |
|---|---|---|---|---|
| **VND** | as-is | `× usd_rate` **(2 dp)** | `× krw_rate` **(2 dp)** | passthrough |
| **USD** | `÷ usd_rate` **(4 dp)** | as-is | `× krw_rate ÷ usd_rate` **(4 dp)** | `÷ usd_rate` (4 dp) |
| **KRW** | `÷ krw_rate` **(2 dp)** | `× usd_rate ÷ krw_rate` **(2 dp)** | as-is | `÷ krw_rate` (2 dp) |

Filters: **Year** (`payment_year = p_year`); **Quarter** (`payment_month IN quarter_months`) — *dormant,
UI sends NULL*; **Bank** — JSON array expanded with `json_array_elements`, a row matches if its
`bank_cd` **or** `acct_no` matches any filter element.

---

## 3. Opening Balance & End Balance — the core financial rule

### 3.1 `cal_rule` semantics

`cal_rule` is a 1-char flag on each chart account: **`P` = add (+)**, **`M` = subtract (−)**. Confirmed
DEV values are `{P, M}` (NULL at the top-level ACC-01/02/03). It is applied **only to ACC-03
(INVESTMENT)** lines; ACC-01 and ACC-02 are always plain `SUM`.

> **`cal_rule` ELSE-branch asymmetry (parity audit N1).** In the function, ACC-03 *display* rows
> (Level 1, Subtotal) treat an unknown/NULL `cal_rule` as **add** (`ELSE +amount`), while the *balance*
> rows treat it as **exclude** (`ELSE 0`). The refactor matches **both** sides:
> `CashFlowRowAssembler.calRuleSign` returns `+1` for any non-`M` (display = adds), while
> `BalanceCalculator.monthlyInvestmentCarveOut` contributes `0` for any non-`P`/non-`M` (balance =
> excludes). Do not "fix" one side to match the other — the asymmetry is intentional parity.

### 3.2 Monthly net-activity formula (drives the balance rows)

```
activity(m) = Σ ACC-01 amount(m)                              -- revenue, +
            − Σ ACC-02 amount(m)                              -- expenses, −
            + Σ ACC-03 investment(m) where chart_acct_cd ∈
                 ('2001026','2001027','2002003','2002002')    -- INVESTMENT subset only
                 signed by cal_rule (P:+amount, M:−amount, else 0)
```

> ⚠️ **`Needs Verification` (G2).** The four INVESTMENT codes are **hardcoded** in the function and the
> refactor (`BalanceCalculator.INVESTMENT_BALANCE_CONTRIBUTING_CODES` — the **identical** set), and they
> return **0 rows** in DEV tenant 000001 (none of its chart codes match). So ACC-03 contributes **0** to
> Opening/End balance there — even though the ACC-03 *display* subtotal sums **all** ACC-03 leaves
> (parity audit D-4). Confirm the codes exist for live tenants on UAT/PROD.

### 3.3 Jan Opening Balance (`opening_balance_calc`)

`jan_opening_balance` = the **prior year's (`p_year − 1`) cumulative December End Balance**. The function
re-runs the full 4-source UNION + FX conversion for the previous year and computes the same `activity`
sum across all 12 months.

> The prior-year recompute applies **FX only** — **no** quarter filter and **no** bank filter (parity
> audit N2). The refactor mirrors this via `aggregator.applyFxOnly(prevRaw, …)`. Test guard: R9.

### 3.4 / 3.5 Opening & End Balance rows

```
Opening(month n) = jan_opening_balance + Σ activity(months 1..n-1)   -- prior-year-Dec-end + cumulative through prev month
End(month n)     = jan_opening_balance + Σ activity(months 1..n)     -- running cash balance
```

`Opening.total_amount = 0` (the opening row has no annual total). `End.total_amount = End(Dec)`
(year-end balance). End Balance is rendered in the sticky footer; Opening Balance in the header.

### 3.6 Sales Profit

`SalesProfit(m) = Σ ACC-01(m) − Σ ACC-02(m)` per month (ACC-03 does **not** contribute to Sales Profit).
Display code `SALES PROFIT`, display name `SALES PROFIT (총수입-총지출)`.

---

## 4. Row assembly (`report_union`) and ordering

Output is a `UNION ALL` of seven row sets, `ORDER BY group_order, sort_order, display_code`:

| Set | `row_type` | sort | group | Amount rule |
|---|---|---|---|---|
| Opening Balance | `Opening Balance` | 0 | 0 (`AAAAA-OPENING-BALANCE`) | §3.4; total = 0 |
| Level 1 | `Level 1` | 1 | 1/2/3 by ACC | ACC-03: `cal_rule`-signed sum of leaves; ACC-01/02: plain sum |
| Level 2 | `Level 2` | 2 | by L1 | plain `SUM` of child L3 (never `cal_rule`-signed) |
| Level 3 | `Level 3` | 3 | by L1 | leaf `jan..dec` + `total` |
| L1 Subtotal | `Level 1 Subtotal` | 4 | by L1 | ACC-01→`S. TOTAL (수입 부분합)`, ACC-02→`S. TOTAL (지출 부분합)`, **ACC-03→display_code `NON SALES PROFIT`** |
| Sales Profit | `Sales Profit` | 5 | 9998 (`ZZZZZ-SALES-PROFIT`) | per month = ΣACC-01 − ΣACC-02 |
| End Balance | `End Balance` | 6 | 9999 (`ZZZZZ-END-BALANCE`) | §3.5; total = End(Dec) |

### 4.1 Important emission details

- **NON SALES PROFIT** is *not* a separate `row_type` — it is the ACC-03 **subtotal** row
  (`row_type='Level 1 Subtotal'`, `display_code='NON SALES PROFIT'`). The refactor renders it **twice**
  on the frontend (a blue Level-1-Subtotal band **and** a green NON SALES PROFIT band), reproducing the
  legacy duplicate on purpose (PO 2026-06-04; parity audit §8b Defect A).
- **`Grand Total`** is **never emitted** by the function (grep count = 0). The legacy TS, the refactor
  DTO doc, the Excel grouper, and the FE classifier all reference it as inert dead handling (G8).
- `display_currency` on every row = `p_base_currency`.

---

## 5. The retained refactor Java pipeline (off the active path as of 2026-06-13)

> **Off the active path (2026-06-13).** The live path of `CashFlowReportService.search(...)` now delegates
> to `CashFlowReportRepository` (`CashFlowReportRepository.java:67`), which calls `get_cashflow_report`
> directly. The Java pipeline described below is **retained but off the active path** — kept as the
> historical reimplementation record, not the running code.

The retained `CashFlowReportService` Java pipeline orchestrated `search(year, quarter, baseCurrency, usdRate, krwRate, bankFilters)`
([CashFlowReportService.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/service/CashFlowReportService.java)):

```
1. bizKey = TenantContext.currentBizKey()                              -- tenant from JWT, never the wire
2. validate(...)                                                       -- §"validation" below
3. chartRepo.findByBizKeyAndDelYn(bizKey,"N") → ChartOfAccountsHierarchy   (replaces wb_get_chart_acct_level_text)
4. current year:  aggregator.collect(bizKey, year)
                  → applyFilters(rows, bankFilters, baseCurrency, usdRate, krwRate, fx)   -- approval gate + bank + FX
                  → bucketByChartAcctCd(...)                            -- drop non-L3 / unknown codes; 12-month accumulators
5. prior year:    aggregator.collect(bizKey, year-1) → applyFxOnly(...) -- FX ONLY, no bank/quarter filter
6. balanceCalc.janOpeningBalance → openingBalance → endBalance → salesProfit
7. assembler.assemble(...)                                             -- ordered List<CashFlowRow>
8. return CashFlowReportResponse(year, quarter, baseCurrency, usdRate, krwRate, rows)
```

**Helper components** (`domain/banking/service/cashflow/`):

| Class | Legacy counterpart | Responsibility |
|---|---|---|
| `PaymentDataAggregator` | CTEs `all_payment_data` + `filtered_payment_data` | Pull the 4 sources, approval-gate non-bank rows (`apprSeqNo ∈ completedSeqNos`), bank-filter, FX. `applyFilters` (current year) vs `applyFxOnly` (prior year). **No `quarter` parameter.** |
| `ApprovalStatusResolver` | `wb_get_appr_stts` | `completedSeqNos(bizKey, candidates)` → `ExptResolMstrRepository.findCompletedApprSeqNos` (`appr_type_cd='01'`). |
| `FxConverter` | `filtered_payment_data` CASE | Per-row conversion, single `setScale/divide(scale, HALF_UP)`; scales per §2.4. Null amount → null; null source ccy → treated as VND. |
| `ChartOfAccountsHierarchy` | `wb_get_chart_acct_level_text` + the L3→L2→L1 self-joins | L1 roots `{ACC-01,ACC-02,ACC-03}`; builds active L2/L3 sets (with the legacy self-reference guard for DEV seq 104). |
| `BalanceCalculator` | `opening_balance_calc` + `balance_calculations` | `janOpeningBalance`, `openingBalance[]`, `endBalance[]`, `salesProfit[]`, `monthlyInvestmentCarveOut` (the hardcoded 4-code set, `cal_rule`-signed). |
| `CashFlowRowAssembler` | `report_union` | Builds the ordered flat row list with verbatim `row_type` / `group_cd` / display strings; ACC-03 subtotal → `NON SALES PROFIT`; End Balance `annual_total` = December End. |
| `MonthlyAmountAccumulator` / `PaymentDataRow` | (per-row scratch) | 12-`BigDecimal` accumulator; normalized source-row record (bank rows carry `apprSeqNo = null`). |

**Validation (`CashFlowReportService.validate`):** year 1900–2999; quarter 1–4 if present;
`baseCurrency` ∈ {VND,USD,KRW}; **conditional rate** — `usdRate` required (non-null, > 0) only when
`baseCurrency != "USD"`, `krwRate` only when `baseCurrency != "KRW"` → else
`WBE_CASH_FLOW_RATE_REQUIRED` with the currency arg (deliberate divergence **D2** from legacy's
always-required validation). Malformed `bankFilters` JSON → `WBE_VALIDATION` (parsed in the controller).

### 5.1 Quarter — full Jan–Dec always (legacy parity, PO 2026-06-04)

`quarter` is accepted and validated (1–4) and **echoed** in the response, but it **never touches the
calculation** — `PaymentDataAggregator.applyFilters` has no `quarter` parameter and the service always
aggregates the full year. This matches the *live* legacy behaviour (the function's server-side quarter
filter is dormant; the UI hides columns client-side). An earlier refactor build filtered server-side
(the Critical gap **D-1 / G5**); it was removed and is guarded by `CashFlowReportQuarterParityTest`
(This Year vs Q1–Q4 → byte-identical rows). The prior-year carry-forward was never quarter-filtered on
either side, so Opening(Jan) is invariant to quarter. Full history:
[parity-audit/gap-report.md §4 G5](../../../../../../docs/features/cash-flow/parity-audit/gap-report.md).

---

## 6. How the frontend re-shapes the flat rows

The service returns a **flat, ordered list**. `groupReport(report)` in `CashFlowPage.tsx` rebuilds the
visual hierarchy, bucketing by `row_type` (with `display_cd === "NON SALES PROFIT"` as the ACC-03
discriminator): `{ opening, groups[], salesProfit, nonSalesProfit, endBalance }`. L1/L2 cells render
with `rowSpan`; Opening Balance goes into the `<thead>`; End Balance into a sticky-bottom sibling
`<table>` (solid green `#16a34a`). Quarter selection hides month columns via
`monthIndicesForQuarter(quarter)` — Q1→`[0,1,2]` … Q4→`[9,10,11]`, null→all 12. See the frontend
[ui-specification.md](../../../../../../wecms-web/docs/features/banking/reports/cash-flow/ui-specification.md).

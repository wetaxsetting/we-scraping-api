# Cash Flow — Test Scenarios (Backend)

Functional, regression (legacy vs refactor parity), and edge-case scenarios, plus the automated test
inventory already on disk. Oracle: the JEX live system (`dev.wecms.vn/cash_0001_001.act`) and
`get_cashflow_report` for numeric truth. Frontend SIT/UAT scenarios:
[wecms-web/docs/features/banking/reports/cash-flow/test-scenarios.md](../../../../../../wecms-web/docs/features/banking/reports/cash-flow/test-scenarios.md).

---

## 1. Automated tests on disk

> **Note (2026-06-13):** the `BalanceCalculatorTest` / `FxConverterTest` / `CashFlowReportQuarterParityTest`
> suites below exercise the **retained Java pipeline, which is now off the active path** — the live path
> calls `get_cashflow_report` directly via `CashFlowReportRepository`. The tests remain valid as the
> regression record of the (off-path) Java reimplementation; live-path numeric truth is the DB function itself.

| Test | Asserts |
|---|---|
| `CashFlowReportQuarterParityTest` | (a) **This Year vs Q1–Q4 return byte-identical rows** (`usingRecursiveComparison`) for Year 2026 · VND · All Banks — Opening / End / all 12 months equal; pinned End Balance Dec = 780, Mar = 240 (proves full year, not one quarter). (b) `applyFilters_keepsAllTwelveMonths_noQuarterTrim` — `applyFilters` never drops a month. The permanent regression guard against re-introducing server-side quarter filtering (**G5/D-1**). |
| `BalanceCalculatorTest` | Golden 2025 sample (tenant 000001 chart): `janOpeningBalance` zero with no prior data + carry-forward of prior-year revenue; Opening/End/SalesProfit month-by-month (Dec end 2,510,000); SalesProfit excludes investment; End includes the carve-out for `cal_rule` P (+) and M (−); ignores codes **not** in the set; pins `INVESTMENT_BALANCE_CONTRIBUTING_CODES` to exactly the 4 legacy codes. |
| `FxConverterTest` | All 9 source/base permutations + rounding scales (USD-base 4 dp; VND/KRW-base 2 dp); null amount; null source ccy → VND; unknown ccy passthrough. |

> **Coverage gap:** there is **no controller-level or export-level** automated test for Cash Flow. The
> controller param validation and the `CashFlowExcelWriter` layout are exercised manually / in SIT.

---

## 2. Functional tests

| ID | Scenario | Expected |
|---|---|---|
| F1 | Default load | Rate prefilled (Shinhan USD/KRW sell, or static 26000/19 fallback); year=current; base=VND; quarter=This Year; banks=All; matrix renders Opening / L1–L3 / Subtotals / SALES PROFIT / End Balance |
| F2 | Search VND | Amounts in VND = raw VND + (USD×usdRate) + (KRW×krwRate), 2 dp |
| F3 | Currency = USD | VND÷usdRate (4 dp), KRW×krwRate÷usdRate (4 dp); `display_currency=USD`; `usdRate` not required |
| F4 | Currency = KRW | Amounts in KRW (2 dp); KRW formatted correctly (do **not** repeat the legacy `"KWR"` typo — G9); `krwRate` not required |
| F5 | Rate override | edited rate flows into conversion |
| F6 | Quarter Q2 | only Apr–Jun columns visible; **Opening/End/monthly identical to This Year** (full-year math — G5) |
| F7 | Bank filter (1 acct) | only that account's transactions aggregated across all 4 sources |
| F8 | Bank filter (multi) | union of selected accounts |
| F9 | All Banks | empty filter = all |
| F10 | Opening Balance | header row = prior-year Dec cumulative end balance carry-forward |
| F11 | End Balance | footer: End(m) = Opening(Jan) + Σ(ACC-01 − ACC-02 + ACC-03 carve-out) through month m |
| F12 | SALES PROFIT | per month = ΣACC-01 − ΣACC-02 (no investment) |
| F13 | Excel export | `.xlsx`; title/subtitle/2-row header; merged L1/L2; blue subtotal + green Sales-Profit + green NON SALES PROFIT bands; solid-green End Balance; 17 cols, no Total column |
| F14 | Approval gate | pending/rejected payment excluded; only `appr_type_cd='01'` (non-bank) / `cnfm_yn='Y'` (bank) counted |

---

## 3. Regression / parity (refactor vs legacy)

Run identical inputs against legacy `_r001` and `GET .../cash-flow-report`; diff rows + every monthly amount.

| ID | Check | Pass |
|---|---|---|
| R1 | Same row set & order | identical `row_type`/`display_cd` sequence |
| R2/R3/R4 | VND / USD / KRW amounts | equal at 2 / 4 / 2 dp (single-round HALF_UP — N0-B); include a **negative** (refund) case |
| R5 | Opening balance | Java `janOpeningBalance` == prior-year Dec end from the function |
| R6 | End balance | month-by-month running balance matches |
| R7 | ACC-03 `cal_rule` | P=+, M=−, else 0; identical INVESTMENT subset |
| R8 | Bank filter | same filtered totals (single + multi) |
| R9 | Prior-year FX-only | opening balance invariant to quarter **and** bank filter (both sides — N2) |
| R10/R11 | Approval gate + multi-source | `appr_type_cd ∈ {00,02,03}` excluded, `01` included; scheduled+overseas+card+bank all contribute |
| R12 | Excel content | same labels/groupings/amounts (allow number-format diff per G7) |
| R13 | Quarter | This Year vs Q1–Q4 → identical rows (**done** — `CashFlowReportQuarterParityTest`) |

> Build a live-vs-Java diff harness for R1–R12 in SIT: for each tenant×year×currency, deep-equal the two responses.

---

## 4. Edge cases

| ID | Case | Expected |
|---|---|---|
| E1 | Empty year | months blank/0; Opening from prior year only; End = Opening; no error |
| E2 | Tenant with no chart accounts | empty L1/L2/L3; graceful empty report |
| E3 | Inactive L1/L2 ancestor (`stts≠'A'`) | L3 leaves excluded |
| E4 | Soft-deleted accounts (`del_yn='Y'`) | excluded |
| E5 | Large tenant, full year, all banks | acceptable latency; **measure** — prior-year recompute doubles the scan (G10) |
| E6 | Multi-currency mix in one account | each source row converted by its own currency, then summed |
| E7 | Missing FX rate | refactor FE catches + falls back to static 26000/19 (graceful — N5); legacy reads `.sell` unguarded (fragile) |
| E8 | Missing chart mapping (`chart_acct_cd=''` / not in chart) | row excluded |
| E9 | INVESTMENT subset codes absent (DEV reality) | ACC-03 contributes 0 to End/Opening; ACC-03 subtotal still shows leaf sums (G2) |
| E11 | Year boundary | Jan opening pulls `year-1` Dec; verify across year change |
| E13 | Malformed `bankFilters` JSON | `WBE_VALIDATION`; legacy treats `'null'`/`''`/null as no filter |

---

## 5. Suggested SIT/UAT data setup

On a non-prod tenant: chart accounts under ACC-01/02/03 (incl. the INVESTMENT subset codes if they
should exist); confirmed rows in each of the 4 sources across multiple months and ≥ 2 currencies; at
least one pending/rejected payment (approval gate); prior-year December activity (carry-forward); a
`bank_exch_rate_infm` weekday row for USD + KRW.

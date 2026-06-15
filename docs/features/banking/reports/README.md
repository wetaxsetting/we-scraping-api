# Banking → Reports

WeCMS menu: **Banking → Reports**. Tenant cash-position reporting derived from the banking ledger.

## Screens in this group

| Screen | Doc | Status (updated 2026-06-14) |
|---|---|---|
| Daily Cash | [daily-cash.md](daily-cash.md) | **Calls `rept_0900_001_rec1..4` directly** — rec1/rec2 (accounts + currency summary) active; rec3/rec4 (cash flow + total cash) need `CASH_REPY_MSTR` data. Live HTTP/JPA smoke recommended. |
| Daily Financial | [daily-financial.md](daily-financial.md) · **full package:** [daily-financial/](daily-financial/README.md) | **CMC: now reads `rept_0015_001_r001_rec1` directly** (2026-06-14 — DB-side source of truth; old Java live-compute bypassed; 07/08 kept; DF-3 + DF-4 fixed; no `WB_INSERT_*`, no summary-table read). **HS: now calls `fn_get_haengsung_daily_financial_report`** (stub removed; `REPT_TRNS_HSTR` + fn added to baseline). Remaining (both): live verification data-blocked in R&D; HS filter UI + seed rows; CMC `rec3`/cash + inline transactions out of scope. |
| Cash Flow | [cash-flow.md](cash-flow.md) | **Calls `get_cashflow_report` directly** (Java reimpl bypassed; `wb_get_chart_acct_level[_text]` added). Remaining: numeric live verification blocked by missing `chart_acct_infm` / classified payment data. |

## Related reference docs

| Layer | Location |
|---|---|
| JEX → Spring mapping | [references/migration/](../../../references/migration/) |
| Open questions | [banking-reports-remaining-scope.md](../../../planning/open-questions/banking-reports-remaining-scope.md) |
| Frontend (Next.js) | [wecms-web/docs/features/banking/](../../../../../wecms-web/docs/features/banking/) |

> Report aggregation tables (`DAILY_CASH_REPT_DAILY_SUMR_DETL`,
> `DAILY_FINCL_REPT_DAILY_SUMR_DETL`) and the BCS batch that refreshes them are tracked under
> [planning/open-questions/banking-reports-remaining-scope.md](../../../planning/open-questions/banking-reports-remaining-scope.md).

# Banking → Reports

WeCMS menu: **Banking → Reports**. Tenant cash-position reporting derived from the banking ledger.

## Screens in this group

Each submenu page is a leaf folder with the standard 9-doc set; the older one-page specs are preserved inside each leaf as `legacy-spec.md`.

| Screen | Leaf folder | Status (updated 2026-06-14) |
|---|---|---|
| Daily Cash | [daily-cash/](daily-cash/README.md) | **Calls `rept_0900_001_rec1..4` directly** — rec1/rec2 (accounts + currency summary) active; rec3/rec4 (cash flow + total cash) need `CASH_REPY_MSTR` data. Live HTTP/JPA smoke recommended. |
| Daily Financial | [daily-financial/](daily-financial/README.md) | **CMC: now reads `rept_0015_001_r001_rec1` directly** (2026-06-14 — DB-side source of truth; old Java live-compute bypassed; 07/08 kept; DF-3 + DF-4 fixed; no `WB_INSERT_*`, no summary-table read). **HS: now calls `fn_get_haengsung_daily_financial_report`** (stub removed; `REPT_TRNS_HSTR` + fn added to baseline). Remaining (both): live verification data-blocked in R&D; HS filter UI + seed rows; CMC `rec3`/cash + inline transactions out of scope. |
| Cash Flow | [cash-flow/](cash-flow/README.md) | **Calls `get_cashflow_report` directly** (Java reimpl bypassed; `wb_get_chart_acct_level[_text]` added). Remaining: numeric live verification blocked by missing `chart_acct_infm` / classified payment data. |
| Daily Financial (HS) | [daily-financial-hs/](daily-financial-hs/README.md) | ✅ **PASS — code-complete (2026-07-02).** `H`-product variant (`rept_0017_001`): read via `fn_get_haengsung_daily_financial_report` (single funnel) + per-field save (`PATCH /entries/{acctSeqNo}` on `REPT_TRNS_HSTR`) + template-populate Excel (EN/KO/VI) + DB-backed View Setting (manual seed V249). Closed by [audits/daily-financial-hs-final-audit.md](../../../audits/daily-financial-hs-final-audit.md); remaining = manual QA only. |

> **Daily Financial (HAENGSUNG)** — ~~folded into the [daily-financial/](daily-financial/README.md) package; standalone doc scope pending OQ-DOC-8~~ **superseded 2026-07-02:** the HS variant now has its own standalone package at [daily-financial-hs/](daily-financial-hs/README.md) (OQ-DOC-8 answered — pending confirmation; the tracking file was removed with the historical `planning/` folder). The [daily-financial/](daily-financial/README.md) package keeps the CMC variant only; its §4 "HAENGSUNG variant" row reflects the 2026-06-14 snapshot.

## Related reference docs

| Layer | Location |
|---|---|
| JEX → Spring mapping | [references/migration/](../../../references/migration/) |
| Open questions | banking-reports-remaining-scope.md _(removed — historical `planning/` folder deleted by project decision)_ |
| Frontend (Next.js) | [wecms-web/docs/features/banking/](../../../../../wecms-web/docs/features/banking/) |

> Report aggregation tables (`DAILY_CASH_REPT_DAILY_SUMR_DETL`,
> `DAILY_FINCL_REPT_DAILY_SUMR_DETL`) and the BCS batch that refreshes them are tracked under
> planning/open-questions/banking-reports-remaining-scope.md _(removed — historical `planning/` folder deleted by project decision)_.

# Banking → Reports

WeCMS menu: **Banking → Reports**. Tenant cash-position reporting derived from the banking ledger.

## Screens in this group

| Screen | Doc | Status |
|---|---|---|
| Daily Cash | [daily-cash.md](daily-cash.md) | Design / partial |
| Daily Financial | [daily-financial.md](daily-financial.md) · **full package:** [daily-financial/](daily-financial/README.md) | **READY FOR SIT** (in-PROD scope; ~80% of in-scope surface, ~40% of full legacy — HS variant, server-side Excel, REC2_1 inline deferred) |
| Cash Flow | [cash-flow.md](cash-flow.md) | Design / partial |

## Related reference docs

| Layer | Location |
|---|---|
| JEX → Spring mapping | [references/migration/](../../../references/migration/) |
| Open questions | [banking-reports-remaining-scope.md](../../../planning/open-questions/banking-reports-remaining-scope.md) |
| Frontend (Next.js) | [wecms-web/docs/features/banking/](../../../../../wecms-web/docs/features/banking/) |

> Report aggregation tables (`DAILY_CASH_REPT_DAILY_SUMR_DETL`,
> `DAILY_FINCL_REPT_DAILY_SUMR_DETL`) and the BCS batch that refreshes them are tracked under
> [planning/open-questions/banking-reports-remaining-scope.md](../../../planning/open-questions/banking-reports-remaining-scope.md).

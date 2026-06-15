# Cash Flow Report — Refactor Feature Spec (pointer)

| Field | Value |
|---|---|
| Slug | `cash-flow-report` (runtime id; this file named `cash-flow.md` to match the menu) |
| Module | Banking → Reports |
| Status | **Calls `get_cashflow_report` directly (2026-06-14).** The earlier Java reimplementation is **bypassed**; the service now invokes the legacy DB function (helpers `wb_get_chart_acct_level` + `wb_get_chart_acct_level_text` added to the refactor DB). Remaining: numeric live verification, **blocked by missing `chart_acct_infm` / classified payment data** in the R&D DB. |
| JEX twin | `cash_0001_001` (view) + `cash_0001_001_r001` (read API) + fn `get_cashflow_report` |
| Refactor backend | `GET /api/v1/banking/reports/cash-flow-report` + `POST .../export` |
| Refactor frontend | `/banking/reports/cash-flow` |
| Audit `WSVC_ID` | `cash-flow-report` |

> **This one-page stub is superseded in detail by the full feature package** at
> [`cash-flow/`](cash-flow/) — created 2026-06-04 during the Cash Flow documentation consolidation
> (which migrated in the former workspace-root `docs/features/cash-flow/` analysis package). Read the
> package for everything: legacy spec, calc rules, the refactor Java pipeline, DB mapping, REST contract,
> migration, test scenarios, and the final parity matrix.

## Full package

| Doc | Scope |
|---|---|
| [cash-flow/README.md](cash-flow/README.md) | Identity, index, **executive summary** + completion table |
| [cash-flow/business-flow.md](cash-flow/business-flow.md) | Request flow, calc rules, legacy `get_cashflow_report` CTE anatomy, refactor Java pipeline |
| [cash-flow/functional-specification.md](cash-flow/functional-specification.md) | Screen behaviour, row vocabulary, validation, permissions |
| [cash-flow/database-mapping.md](cash-flow/database-mapping.md) | Tables / functions, Legacy→Entity→DTO→UI column map, FX, quarter handling |
| [cash-flow/interface-specification.md](cash-flow/interface-specification.md) | Legacy WSVC + refactor REST contract |
| [cash-flow/source-code-index.md](cash-flow/source-code-index.md) | Full legacy ↔ refactor traceability |
| [cash-flow/migration-guide.md](cash-flow/migration-guide.md) | Architecture translation + deliberate divergences |
| [cash-flow/test-scenarios.md](cash-flow/test-scenarios.md) | Functional / parity / edge cases + automated-test inventory |
| [cash-flow/refactor-gap-analysis.md](cash-flow/refactor-gap-analysis.md) | **Final parity matrix** (MATCH/PARTIAL) + open PO calls |

Parity audit (cross-linked, kept at workspace root):
[`docs/features/cash-flow/parity-audit/gap-report.md`](../../../../../docs/features/cash-flow/parity-audit/gap-report.md)
· [`final-confirmation.md`](../../../../../docs/features/cash-flow/parity-audit/final-confirmation.md).
Frontend: [`wecms-web/docs/features/banking/reports/cash-flow/`](../../../../../wecms-web/docs/features/banking/reports/cash-flow/).

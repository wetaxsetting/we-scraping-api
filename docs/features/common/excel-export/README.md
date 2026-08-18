# Excel Export — Backend (Common)

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

Shared XLSX export pipeline for every WeCMS list page. One service
(`ExcelExportService`), one writer (`ExcelWorkbookWriter`, Apache POI
SXSSF), one registry (`ExportableScreenRegistry`), and a thin per-resource
controller endpoint per screen. Keyed by the View Setting `WEB_SVC_ID`
slug. The backend is the **single XLSX producer**; the frontend ships
JSON and saves the returned blob.

> Source of truth = code under
> `wecms-api/src/main/java/com/kosign/wecms/common/export/`. Authoritative
> rules: [`wecms-api/CLAUDE.md`](../../../../CLAUDE.md) §"Excel export —
> `common/export/`" (CLAUDE.md:969-1043).

## At a glance

| Fact | Value | Source |
|---|---|---|
| Endpoint shape | `POST /api/v1/{module}/{resource}/export` (per-resource, never a dispatcher) | CLAUDE.md:985-995 |
| Response | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (XLSX), RFC 5987 UTF-8 filename | `ExcelExportService.java:347-354` |
| Row cap | `MAX_ROWS = 50_000` | `ExcelExportService.java:61` |
| Writer | Apache POI **SXSSF** streaming, 100-row window, pooled cell styles | `ExcelWorkbookWriter.java:99-101` |
| Transaction | **No** outer `@Transactional` on the orchestrator | `ExcelExportService.java:71-78` |
| Error codes | `WBE2010` / `WBE2011` / `WBE2012` / `WBE2013` | see interface-specification |
| Confirmed providers | **20** registered (2 abstract base classes are not registered) | see source-code-index |

## Documents

| Doc | Purpose |
|---|---|
| [business-flow.md](business-flow.md) | Request → registry → intersect → fetch → writer → stream |
| [functional-specification.md](functional-specification.md) | Visibility / allow-list intersection, special-field drop, 50k cap, subheader, amount formatting, title/fileName split |
| [database-mapping.md](database-mapping.md) | No own table — keyed on `SYS_SCR_OPTION_WECMS` visibility; provider reuses each list service's tenant query |
| [interface-specification.md](interface-specification.md) | `ExcelExportRequest` body, per-resource endpoint contract, WBE2010-2013, confirmed provider/screenId table |
| [migration-guide.md](migration-guide.md) | Legacy per-page JEX Excel export → shared pipeline |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING |
| [source-code-index.md](source-code-index.md) | `common/export/` file map + confirmed providers + frontend + tests |
| [test-scenarios.md](test-scenarios.md) | Unknown screenId → WBE2010, >50k → WBE2011, hidden-column-leak prevention, special-field drop, amount formatting |

## Related

- Common backend hub: [`../README.md`](../README.md)
- Frontend twin: [`../../../../../wecms-web/docs/features/common/excel-export/README.md`](../../../../../wecms-web/docs/features/common/excel-export/README.md)
- View Setting (visibility / order source): [`../view-setting/README.md`](../view-setting/README.md)
- Audit deliverables:
  [`../../../audits/common-source-index.md`](../../../audits/modules/common-source-index.md) ·
  [`../../../audits/common-gap-analysis.md`](../../../audits/modules/common-gap-analysis.md) ·
  [`../../../audits/common-docs-audit.md`](../../../audits/modules/common-docs-audit.md)

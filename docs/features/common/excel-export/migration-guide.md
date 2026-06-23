# Excel Export — Migration Guide (JEX → Refactor)

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

How the legacy JEX per-page Excel/export pattern maps onto the shared refactor
pipeline.

## Legacy JEX shape (source of truth)

In JEX, Excel/print export was wired **per page** in the JSP layer. Each list
screen carried its own download routine — the canonical example cited in code
is the Approval Box export `resl_0001_001_osmc.download_excel`
(`ApprovalBoxExportProvider.java:152` Javadoc) which composed a
`{left_title, right_title}` two-cell subheader, and the payment-result page
`pymn_*_r003` which copied a server file + inserted an `ERP_TRNS_HSTR`
`ERP_TYPE='W'` row before serving the file (`ExcelExportService.java:117-118,
:147-158`). Account Inquiry's filename convention (`계좌조회(VND)_DDMMYYYY`,
no inner space) is preserved as the `fileName` field while the on-screen title
(`계좌 조회 (VND)`) becomes the separate `title` field
(`ExcelExportRequest.java:60-69`, CLAUDE.md:1011).

> Legacy WSVC ids appear here only as **migration-traceability prose / file:line
> citations** — never as Java identifiers, REST URIs, or DB column values (root
> CLAUDE.md §"Refactor identifier naming").

## Refactor mapping

| Legacy JEX | Refactor | Notes |
|---|---|---|
| Per-page JSP download routine | One shared `ExcelExportService` + per-resource `@PostMapping` | Adding a screen = 1 provider class + 1 controller endpoint |
| Per-page workbook generation | Single `ExcelWorkbookWriter` (POI SXSSF) | Backend is the only producer; no per-page POI |
| `{left_title, right_title}` subheader | `ExportFetch.subHeaderRight` split layout | `ExcelWorkbookWriter.java:91-164` |
| `pymn_*_r003` file-copy + `ERP_TRNS_HSTR` insert | `auditsErpExport()` + `recordErpExport(...)` temp-file tee | `ExportRowProvider.java:50-74` |
| Legacy WSVC menu id as screen key | Semantic kebab `WEB_SVC_ID` slug | e.g. `comm_0220_001` → `firm-banking-settings` style slugs |
| Client-side CSV download (Account Inquiry) | Removed — XLSX-only | see refactor-gap-analysis (DEAD LEGACY) |
| Filename `_DDMMYYYY`, no timestamp prefix | Same convention via `fileName` | RFC 5987 UTF-8 round-trips Korean/Khmer |

## What is intentionally NOT ported

- **Generic export dispatcher** — forbidden by design. Each resource keeps its
  own `POST /{module}/{resource}/export` (CLAUDE.md:1022, decision 3).
- **Frontend workbook generation / CSV** — retired. The frontend posts JSON and
  saves the blob (CLAUDE.md:1024).
- **JEX `getInput()/getOutput()` envelope mimicry** — none; the wire is the
  refactor `{ status, data }` envelope.

## Per-screen migration recipe

1. Confirm the screen's table(s) against the legacy-table-inventory and read its
   list service + the legacy JSP download routine as the functional spec.
2. Write one `ExportRowProvider` (`@Component`): `screenId()`,
   `allowedFieldIds()`, `fetch(request)` delegating to the existing list service,
   and an optional `auditsErpExport()`/`recordErpExport(...)` for result exports.
3. Add `@PostMapping("/{resource}/export")` to the existing resource controller
   forwarding to `excelExportService.exportTo(...)`.
4. Seed `SYS_SCR_OPTION_WECMS` (if the screen also has a View Setting popover) or
   leave unseeded (export-only screens).
5. No change to `common/export/` — adoption is purely additive (CLAUDE.md:1015).

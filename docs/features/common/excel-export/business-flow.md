# Excel Export — Business Flow

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

End-to-end flow of a single export request, from the per-resource POST to
the streamed XLSX bytes. All citations are to
`wecms-api/src/main/java/com/kosign/wecms/common/export/`.

## Sequence

```
Frontend (useExcelExport hook)
   │  POST /api/v1/{module}/{resource}/export   (JSON ExcelExportRequest)
   ▼
{Resource}Controller  (thin shim — @Valid @RequestBody, HttpServletResponse, returns void)
   │  excelExportService.exportTo(response, request)
   ▼
ExcelExportService.exportTo                                     (ExcelExportService.java:78)
   1. registry.require(request.screenId())                      (:79)  → WBE2010 if unknown
   2. resolveColumns(provider, request)                         (:81)
        a. requested = request.columns()  (empty → [])          (:220-223)
        b. allowed   = provider.allowedFieldIds()               (:225)
        c. view = screenOptionService.load(screenId, dymAreaId) (:253)
             - catch WBE2001 → treat as "no persisted view"     (:254-256)
               (export-only screens not seeded in SYS_SCR_OPTION_WECMS)
        d. for each requested col, in REQUEST ORDER:            (:272-282)
             drop blank fieldId · dedupe · drop special (CHECKBOX/ETC)
             · drop if not in allow-list
             · drop if enforceVisibility && not USE_YN='Y'
             · enrich widthChars from View Setting SIZE (px→chars)
      → empty list → WBE2013 (no exportable columns)            (:82-84)
   3. fetch = provider.fetch(request)                           (:86)
        - re-runs the SAME filtered+sorted query the list endpoint uses
        - returns ExportFetch(rows[], subHeader, subHeaderRight)
   4. rows.size() > MAX_ROWS (50_000) → WBE2011                 (:88-90)
   5. resolve fileName / title / subHeader                      (:92-111)
        - title  = request.title() ?? request.fileName() ?? screenId()
        - subHeader = fetch.subHeader() ?? request.subHeader()
   6. configureResponseHeaders(response, fileName)              (:113, :345-362)
        - 200 OK · XLSX content-type · RFC 5987 UTF-8 filename
        - X-Content-Type-Options: nosniff · expose Content-Disposition
   7a. provider.auditsErpExport()? → writeWithErpAudit          (:119-123, :159)
        - tee workbook to temp file → recordErpExport (ERP 'W' row)
          → stream temp file → delete temp file
   7b. else → writer.write(out, …) straight to response stream  (:125-126)
   ▼
ExcelWorkbookWriter.write   (SXSSF: title row → subheader → blank → header → data rows)
   │  wb.write(out); out.flush(); wb.dispose()                  (ExcelWorkbookWriter.java:236-238)
   ▼
Browser saves the XLSX blob
```

## Key behaviours

- **Provider resolution is fail-fast.** `ExportableScreenRegistry` collects
  every `@Component` provider at startup and rejects duplicate / blank
  `screenId`s (`ExportableScreenRegistry.java:29-45`). Unknown id at request
  time → `WBE2010` (`:52-58`).
- **Column order comes from the request** (honours an unsaved popover
  reorder), but only **after** the visibility intersection — a hidden column
  can never leak even if the request asks for it (`ExcelExportService.java:270-283`).
- **Export and list share one query path.** Each provider's `fetch(...)`
  delegates to the screen's existing list service (e.g. `AccountService.list`,
  `CardTransactionsService.listAllRows`) so filter + sort + tenant scoping are
  identical to the on-screen table (`ExportRowProvider.java:34-48`).
- **No outer transaction.** `exportTo` deliberately runs without
  `@Transactional` so the streaming write never holds a DB transaction open;
  each inner call (`ScreenOptionService.load`, `provider.fetch`) opens its own
  scope (`ExcelExportService.java:71-78`).
- **Write-failure handling.** Once bytes are partially flushed there is no
  clean switch back to a JSON error envelope; `IOException` is rethrown as
  `UncheckedIOException`, and a `RuntimeException` becomes `WBE2012` only while
  the response is still uncommitted (`ExcelExportService.java:127-143`).
- **ERP audit tee (opt-in).** Payment *result* providers set
  `auditsErpExport() = true`; the service writes the workbook to a temp file,
  lets the provider persist a WeCMS→ERP `ERP_TRNS_HSTR` `ERP_TYPE='W'` row, then
  streams the temp file and deletes it. Every non-opting provider keeps the
  byte-identical direct stream (`ExportRowProvider.java:50-74`,
  `ExcelExportService.java:159-192`).

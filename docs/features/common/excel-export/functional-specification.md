# Excel Export — Functional Specification

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

Behavioural contract of the shared pipeline. Citations are to
`wecms-api/src/main/java/com/kosign/wecms/common/export/` unless noted.

## 1. Column resolution (`resolveColumns`, ExcelExportService.java:218-284)

The final emit list = the request's `columns[]` filtered through, **in this
order**, then kept in request order:

| Step | Rule | Source |
|---|---|---|
| blank guard | drop a column with null/blank `fieldId` | :273-274 |
| dedupe | first occurrence of a `fieldId` wins | :275 |
| special drop | drop `CHECKBOX` / `ETC` via `ScreenOptionService.isSpecialFieldId(fid)` | :276 |
| allow-list | drop if `fid ∉ provider.allowedFieldIds()` | :277 |
| visibility | drop if the screen is seeded AND `USE_YN != 'Y'` for the caller | :280 |
| width enrich | derive `widthChars` from View Setting `SIZE` (px) when the wire spec omits it | :281, :293-306 |

- Empty result → `WBE2013` (`EXPORT_NO_COLUMNS`) (:82-84, :65).
- **Order is the request's order** — an unsaved popover reorder is honoured
  because the loop iterates `requested`, not the persisted view (:270-282).
- **Export-only screens** (not seeded in `SYS_SCR_OPTION_WECMS`) have no
  persisted view: `screenOptionService.load(...)` throws `WBE2001`, which is
  caught and treated as "no persisted view" — visibility is **not** enforced
  and the provider allow-list is the only bound (:251-257, :278-280). The
  documented export-only screens are addon e-Invoices, operation-status,
  cash-flow, firm-banking-history, erp-transaction-history (:244-250).

## 2. FIELD_ID aliasing (`FieldIdAlias`, FieldIdAlias.java)

The DB seed may carry legacy JEX FIELD_IDs (`BANK_NM`, `ACCT_NICK_NM`, …) or
PROD `_2` / `_4` suffixed forms while the provider allow-list / row-map / FE
registry speak the canonical form (`BANK`, `ACCOUNT_NICKNAME`, …). `FieldIdAlias.resolve`
is applied **only at the visibility-intersection step** so the two key spaces
intersect (`ExcelExportService.java:262`, `FieldIdAlias.java:99-108`).
Identity passthrough for unknown ids; Save/Reset round-trips on DB FIELD_IDs
stay untouched.

## 3. Row cap

`MAX_ROWS = 50_000` (`ExcelExportService.java:61`). `rows.size() > MAX_ROWS`
→ `WBE2011` (`EXPORT_TOO_LARGE`), args `(rows.size(), MAX_ROWS)` (:88-90).

## 4. Subheader (count + totals)

- **Backend-computed.** The provider's `ExportFetch.subHeader()` is canonical;
  `request.subHeader()` is informational fallback only
  (`ExcelExportService.java:93`, `ExcelExportRequest.java:59`).
- Optional `subHeaderRight` splits the row into left+right merged halves
  (legacy `{left_title, right_title}` — e.g. Approval Box). `null` = single
  centered merged cell (`ExportFetch.java:19-26`, `ExcelWorkbookWriter.java:91-164`).
- Amount strings in a subheader MUST go through
  `AmountFormatter.forDisplay(value, ccyCd)` — never `String.format` / raw
  decimal (CLAUDE.md:1006-1010, :1027).

## 5. Workbook layout & amount formatting (`ExcelWorkbookWriter`)

```
Row 0  TITLE              (merged, bold 14pt, centered)             :117-124
Row 1  subHeader summary  (merged 11pt, centered OR split L/R)      :126-164  (only when present)
Row 2  (blank spacer)                                               :166-168
Row 3  Header A | Header B | …  (bold, grey-25 bg, thin borders)    :170-179
Row 4+ data rows, one per source row, formatted per-column         :193-207
```

Format coercion (`ExcelColumnFormat`, ExcelWorkbookWriter.java:260-325):

| Format | Cell behaviour |
|---|---|
| `TEXT` | string passthrough |
| `AMOUNT` | wire decimal → numeric cell, POI data format `#,##0.##`, right-aligned by default |
| `NUMBER` | wire decimal → numeric cell, POI data format `#,##0` |
| `DATE` | legacy `YYYYMMDD` → `yyyy-MM-dd` |
| `DATETIME` | legacy `YYYYMMDDHHmmss` → `yyyy-MM-dd HH:mm:ss` |

- Unknown/blank format → `TEXT`; coercion failures degrade to text, never throw
  (`ExcelColumnFormat.java:31-38`, ExcelWorkbookWriter.java:277-325).
- **Data cells stay raw numeric** so Excel can re-sum (`#,##0.##` is a cell
  format, not a pre-formatted string) (CLAUDE.md:1008).
- Cell styles are pooled (9 total: text/number/amount × LEFT/RIGHT/CENTER +
  title + subheader + header) so a 50k export stays bounded
  (ExcelWorkbookWriter.java:493, :513-579).
- `AMOUNT` headers get a ` (CCY)` suffix when `ccyCd` is supplied
  (ExcelWorkbookWriter.java:242-252).
- Column width precedence: wire `widthChars` → View Setting `SIZE` (px→chars,
  `(px-5)/7`) → format-default min (ExcelExportService.java:194-217, :340-343;
  ExcelWorkbookWriter.java:222-234, :345-353).

## 6. Title vs fileName (two distinct wire strings)

- `title` = on-screen page title with natural spacing (`계좌 조회 (VND)`) → row 0.
- `fileName` = filesystem form (no inner space + `_DDMMYYYY` suffix) → download
  filename; the service appends `.xlsx` and RFC 5987 percent-encodes it.
- `title` falls back to `fileName` then `screenId` when null
  (`ExcelExportService.java:104-111`; `ExcelExportRequest.java:60-69`;
  CLAUDE.md:1011). `sanitizeFileName` strips control/slash/quote chars and caps
  at 120 (`ExcelExportService.java:370-382`).

## 7. Alignment

`ExcelAlignment` = `LEFT | RIGHT | CENTER`; unknown/blank → `LEFT`, except
`AMOUNT` / `NUMBER` default to `RIGHT` (`ExcelAlignment.java:14-21`,
`ExcelColumnSpec.java:53-63`).

## 8. Tenant scope

`BIZ_KEY` is mandatory and inherited from the list service the provider reuses
(`TenantContext.currentBizKey()`); export never crosses tenants (CLAUDE.md:1016).

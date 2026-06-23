# Excel Export — Refactor Gap Analysis

**Part of:** Common → excel-export
**Last verified:** 2026-06-23

Vocabulary: **MATCH** (refactor reproduces legacy) · **APPROVED DEVIATION**
(intentional, ruled) · **DEAD LEGACY** (legacy behaviour deliberately dropped) ·
**PARTIAL** (in progress / partly wired) · **MISSING** (not built — note whether
by design).

| Area | Status | Detail | Source |
|---|---|---|---|
| Per-resource endpoint shape | MATCH | `POST /api/v1/{module}/{resource}/export` mirrors per-page legacy download routines | CLAUDE.md:985-995; `AccountController.java:124` |
| Single shared writer (POI SXSSF) | APPROVED DEVIATION | Legacy generated per-page; refactor centralises into one `ExcelWorkbookWriter` | `ExcelWorkbookWriter.java:99` |
| 50k row cap | APPROVED DEVIATION | Refactor-added safety bound (`MAX_ROWS=50_000` → WBE2011); legacy had no explicit cap | `ExcelExportService.java:61, :88-90` |
| Backend-computed subheader | MATCH | Provider returns canonical count+totals; request `subHeader` is fallback only | `ExcelExportService.java:93`; CLAUDE.md:1005 |
| `{left_title, right_title}` subheader split | MATCH | `subHeaderRight` reproduces legacy Approval Box / `resl_0001_001_osmc` layout | `ExportFetch.java:21-26`; `ExcelWorkbookWriter.java:131-144` |
| ERP `ERP_TYPE='W'` audit on result export | MATCH | `auditsErpExport()` + temp-file tee reproduces `pymn_*_r003` file-copy + `ERP_TRNS_HSTR` insert | `ExportRowProvider.java:50-74`; `ExcelExportService.java:147-192` |
| Amount formatting via shared formatter | MATCH | Subheader via `AmountFormatter.forDisplay`; data cells raw + POI `#,##0.##` | CLAUDE.md:1006-1010 |
| title vs fileName split | MATCH | On-screen title (row 0) vs `_DDMMYYYY` filename preserved | `ExcelExportRequest.java:60-69`; CLAUDE.md:1011 |
| RFC 5987 UTF-8 filename | MATCH | Korean/Khmer round-trip via `filename*=UTF-8''…` | `ExcelExportService.java:350-354` |
| Visibility / allow-list defence-in-depth | APPROVED DEVIATION | Backend re-intersects request columns with allow-list AND persisted `USE_YN='Y'`; legacy trusted the page | `ExcelExportService.java:218-284`; CLAUDE.md:1001 |
| Special-field (CHECKBOX/ETC) drop | MATCH | Dropped via `isSpecialFieldId` — special columns never export | `ExcelExportService.java:276` |
| CSV export path | **DEAD LEGACY** | Account Inquiry's client-side CSV download removed when shared XLSX landed; new screens must NOT add CSV | CLAUDE.md (frontend §13b); `wecms-web/CLAUDE.md` "CSV export is not sanctioned" |
| Generic export dispatcher (`POST /api/v1/exports/{screenId}`) | **MISSING — by design (forbidden)** | Every endpoint is per-resource; a dispatcher is a review-caught anti-pattern | CLAUDE.md:1022 |
| Frontend workbook generation (`xlsx`/`exceljs`) | **MISSING — by design (forbidden)** | Backend is the single producer; FE ships JSON + saves blob | CLAUDE.md:1023-1024 |
| Provider coverage | PARTIAL | 20 screens wired today; remaining list pages adopt one-at-a-time in their next stabilisation pass | `ExportableScreenRegistry`; CLAUDE.md:1043 |
| Overseas result-detail ERP audit | PARTIAL / UNVERIFIED | `OverseasPaymentResultDetailExportProvider` implements the interface directly; an ERP-audit opt-in was **not observed** in the read of its `screenId()`/`allowedFieldIds()` — confirm against the full class before asserting parity with the other 4 result providers | `OverseasPaymentResultDetailExportProvider.java:36-105` |

## Notes

- The earlier "audit DB constant" bucket (keeping `AUDIT_LOGS_INFM.WSVC_ID` as a
  legacy WSVC id) was retired 2026-05-09 — not relevant to export, which writes
  no audit-log rows itself (the ERP audit writes `ERP_TRNS_HSTR`, not
  `AUDIT_LOGS_INFM`).
- `⚠️ UNVERIFIED`: the exact ERP `ERP_TYPE` code and `recordErpExport` body for
  each payment-result provider were confirmed only at the `screenId()` /
  `titleLabel()` / `erpTypeCd()` level (e.g. `17`/`04`/`05`/`06`); the full
  `recordErpExport` write path was not line-by-line read in this pass.

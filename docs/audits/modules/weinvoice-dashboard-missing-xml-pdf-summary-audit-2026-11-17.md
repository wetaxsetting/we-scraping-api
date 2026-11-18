# WeInvoice Dashboard: "Thiếu XML" / "Thiếu PDF" Summary — Audit

**Date:** 2026-11-17
**Type:** AUDIT / INVESTIGATION
**Current Work Classification:** AUDIT
**Finding Classification:** BUG (count rule differs between the dashboard and the invoice list; dashboard rows lead to a list that does not show the counted invoices)
**Status:** COMPLETE
**Investigation Status:** COMPLETE
**Approval Status:** APPROVED 2026-11-17 — list rule ("Sửa luôn"); dashboard option (a) per direction ("Tách mua/bán")
**Implementation Status:** COMPLETE (READY FOR MANUAL COMMIT) — list rule: `we-scraping-web/docs/bugs/weinvoice-list-missing-xml-count-fix-2026-11-17.md` (web repository); per direction: [weinvoice-dashboard-data-status-by-direction-2026-11-17](../../issues/weinvoice-dashboard-data-status-by-direction-2026-11-17.md) + its web counterpart. Engine CQT-code mapping NOT STARTED.
**Repository:** wecms-api (`we-scraping-api`) — primary; consumer `we-scraping-web` (`components/dashboard/Dashboard.tsx`, `lib/api/weinvoice/invoices.ts`)

## Request

Developer, 2026-11-17: "trong phần dashboard trong phần thông tin Thiếu XML vs PDF trong summary giúp t check xem"
(check the "missing XML" / "missing PDF" figures in the Dashboard summary). Investigation only — no implementation authorized.

## Expected Behavior

Spec v1.0 `01_Dac_ta_UI.html`, screen "Tổng quan": the data-status block shows *HĐ có mã thiếu XML, HĐ không mã chưa
có XML, HĐ chưa có PDF gốc, kỳ chưa lấy đủ*, and **"Bấm từng dòng Tình trạng dữ liệu → danh sách hoá đơn đã lọc sẵn"**
(each row opens the invoice list already filtered to those invoices). The spec reference UI
(`weinvoice-web/components/dashboard/Dashboard.tsx`) links only the first two rows; ours is identical.

## Backend Trace — how the dashboard counts

`DashboardRepository.dataStatus` (over `InvoiceQueryRepository.cte`, invoices of the MST with `ngay_lap` in the selected
year, **both directions**, `tthai_eff NOT IN (4, 6)`):

| Row | Rule |
|---|---|
| `missing_xml` (HĐ có mã thiếu XML) | `NOT has_xml AND upper(left(khhdon,1)) <> 'K'` |
| `no_code_no_xml` | `NOT has_xml AND upper(left(khhdon,1)) = 'K'` |
| `missing_pdf` (HĐ chưa có PDF gốc) | `NOT has_pdf` — `has_pdf` = `invoice_pdf` row or a WeInvoice upload; the GDT stand-in PDF (`invoice_xml.gdt_pdf_path`) is deliberately **not** counted |

## Frontend Trace — where the rows lead and how the list counts

- `Dashboard.tsx:142`: "Thiếu XML" → `/invoices/purchase?missing=xml` (**purchase list only**); "không mã" → `/sources/email`;
  "Thiếu PDF" → **no link**.
- The list filters client-side over the year's rows (`loadInvoices`). `?missing=xml` sets `missingXmlOnly`, rule
  `invoices.ts:124`: hidden when `hasKind(xml) || !i.cqtCode` — i.e. **"coded" = has a CQT code**, not "symbol starts
  with C". The list KPI uses the same rule (`invoices.ts:182` `i.cqtCode && !hasKind(i,"xml")`) and does **not** exclude
  status 4/6. Its "missing PDF" (`invoices.ts:183`) also keeps 4/6.
- `cqtCode` = `parsed.ma_cqt` (only once the XML is in) or `list_raw.mhdon` (only the old JSON list). The engine reads
  the GDT list from **Excel** since 20/10 (JSON fallback removed 09/11), and `invoice_list_excel.py` maps no `mhdon`.

## Database Trace (read-only, 2026-11-17, invoices dated 2026)

| MST | dashboard missing XML | of which purchase | list rule (purchase) | C-symbol without XML **and** without CQT code | dashboard missing PDF | of which sales | of which has GDT stand-in PDF |
|---|---:|---:|---:|---:|---:|---:|---:|
| 0318793907 | 60,232 | 0 | 0 | 60,259 | 60,563 | 60,326 | 302 |
| 3603379922 | 283 | 56 | **0** | 283 | 471 | 227 | 59 |
| 3700860461 | 0 | 0 | 0 | 0 | 5,452 | 3,323 | 5,362 |
| 0108515678-001 | 0 | 0 | 0 | 0 | 2,742 | 2,657 | 2,731 |
| 1201496252 | 0 | 0 | 0 | 0 | 464 | 235 | 246 |

Every coded invoice without XML in 2026 came from the Excel list and has **no CQT code** (0318793907: 60,259/60,259;
3603379922: 276/276 C-symbol rows without an `invoice_xml` row), so the list rule can never match them.

**Developer screenshot (same day), purchase list of 3603379922, "Năm nay":** KPI card "THIẾU XML **0** · Thiếu PDF: 273",
"Số hoá đơn 394". Recount right after (read-only): purchase 394, **38 coded (C) without XML** (all with a `pending` XML
task — the workers are working the backlog again, 12 tasks `running`), 93 no-code (K) without XML, 212 without PDF (the
PDF figure fell between the screenshot and the recount as the PDF worker caught up). The card shows 0 because of
finding 1 below — the list rule matches none of the 38.

## Root Cause / Findings

1. **"Thiếu XML" click lands on an empty or wrong list.** The dashboard counts coded invoices by symbol (`C…`); the list
   counts them by CQT code, which Excel-sourced invoices do not have until the XML arrives. 3603379922 shows 283 on the
   dashboard and 56 of them are purchases, but the purchase list filtered by "missing XML" shows **0**. The list KPI
   "Thiếu XML" is therefore 0 for exactly the invoices it is meant to flag.
2. **Direction mismatch.** The dashboard counts purchase + sales; the link opens the purchase list only. 0318793907:
   60,232 missing XML, **all sales** (MTT sales still in the XML queue) → the link shows nothing.
3. **"Thiếu PDF" is not clickable**, contrary to the spec's "each row → filtered list" (the list already supports a PDF
   filter, but not from the URL). It is also dominated by **sales** invoices (the company's own issuance, rarely
   fetchable from a provider portal): 0318793907 60,326/60,563, 0108515678-001 2,657/2,742.
4. **4/6 status handling differs**: the dashboard excludes replaced/cancelled invoices, the list KPI does not (small:
   0318793907 has 34 such invoices in 2026).
5. The large current figures for 3603379922 (283 XML / 471 PDF) are mainly the backlog from the 16/11 worker outage
   (engine record: workers died on the DB restart); they fall as the XML/PDF workers catch up. Not a counting error.
6. Not a defect: the GDT stand-in PDF is not counted as "has PDF" — matches the engine rule (CLAUDE.md §2.4, "không
   tính là có PDF"). It explains why e.g. 3700860461 shows 5,452 missing PDF while 5,362 of them display a GDT PDF.

## Recommended Implementation (for approval — not started)

- **WEB (required):** use one "coded" rule everywhere — the symbol (`!isNoCode(i)`, already used by the mock dashboard and
  mailbox code) instead of `cqtCode` in `invoices.ts:124` and `:182`; exclude 4/6 in the list's missing counts so the
  list and dashboard agree.
- **Dashboard links (needs a product decision):** either (a) show the missing-XML / missing-PDF rows **per direction**
  (backend adds `missing_xml_purchase/sales`, `missing_pdf_purchase/sales` to `data_status`; each row links to its list),
  or (b) count **purchase only** (the actionable side for input VAT) and keep the current link. Make "Thiếu PDF" link to
  the list with the PDF filter (`?missing=pdf`, read from the URL like `missing=xml`).
- **Engine (optional follow-up):** map the CQT-code column from the GDT Excel list if the export carries one, so
  `cqtCode` is known before the XML arrives.

## Affected Repositories

`we-scraping-web` (list rule, dashboard links); `we-scraping-api` only for option (a). Engine optional.

## DB Impact

NONE.

## API Impact

Option (a) adds fields to `data_status` of `GET /dashboard` (additive). Otherwise none.

## Required Regression Tests

- Web: an Excel-sourced coded purchase invoice without XML and without CQT code is counted by the list KPI and shown by
  `?missing=xml`; a replaced/cancelled invoice is not counted.
- API (option a): `DashboardRepository` per-direction counts.

## Risks

Changing the list rule raises the visible "Thiếu XML" numbers for MSTs with an XML backlog (they were silently 0).

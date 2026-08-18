# Vendor Payment Excel Download — Pre-SIT Smoke Test Checklist

- **Feature:** Payment → Vendor Payment → Excel **Download** (backend-generated, template-populated **22-column** report)
- **Slug:** `vendor-payment` · **Legacy SCDUL_PYMN_TYPE_CD:** `01`
- **System:** Refactor (`wecms-api` + `wecms-web`)
- **Status:** Implemented; pre-SIT manual smoke verification pending (live DB / data dependent)
- **References:** [payment-excel-download-refactor-audit.md](payment-excel-download-refactor-audit.md) (master) · [../../../docs/audits/vendor-payment-excel-download-audit.md](../../../../docs/audits/vendor-payment-excel-download-audit.md) (parity audit)

> **Scope — IN:** the generated **report** download on the **4 per-batch surfaces** only.
> **Scope — OUT (do NOT test here):** Excel **upload**; the **original uploaded-file** download (`GET /{id}/file`); the Result **toolbar** export; any generic Excel export. §8 only confirms those remain separate.

---

## 1. Scope

Verify all 4 per-batch surfaces download the generated Vendor Payment report:

1. Scheduled Payment **list** — row download glyph
2. Scheduled Payment **detail** — file-card download
3. Payment Result **list** — row download glyph
4. Payment Result **detail** — file-card download

Expected: filename `WeCMS_Vendor_Payment_download_{LANG}.xlsx`, generated from the backend locale template, **not** the uploaded Excel, opens clean, EN/KO/VI with KM→EN, **22 columns A–V** (with a Vendor-specific **Contact Name** column at J), header on **Excel row 3**, data from **Excel row 4**, one row per detail, both Scheduled + Result work, uploaded-file endpoint stays separate.

---

## 2. Preconditions

| # | Precondition | Confirm |
|---|---|---|
| P1 | Backend running; templates on classpath | `build/resources/main/templates/vendor-payment/WeCMS_Vendor_Payment_download_{EN,KO,VI}.xlsx` present |
| P2 | Frontend running, pointed at backend | App loads, login works |
| P3 | Logged in; tenant has Vendor Payment data | `GET /api/v1/auth/me` returns expected `biz_key` |
| P4 | Menu auth for both scopes | Can open Vendor Scheduled **and** Result pages without 403 |
| P5 | **Scheduled test batch (`type 01`)** with ≥ 2 detail lines (at least one with a **Contact / TRCO** so col J populates) + an approval attached; prefer one VND + one USD batch | Detail shows ≥ 2 rows |
| P6 | **Dispatched Result batch** (`TRNF_YN='Y'`) | Appears on Result list |
| P7 | Browser: "ask where to save" on; clear prior `WeCMS_Vendor_Payment_download_*.xlsx` | Browser settings |
| P8 | Excel viewer; DevTools Network open | — |
| P9 | UI language switch (EN/KO/VI; KM if present) | Profile menu → Languages |
| P10 | Note each batch's `SCDUL_PYMN_NO` | From row / detail URL |

---

## 3. Test Matrix

Endpoints: Scheduled → `GET /api/v1/payment/vendor-payment/{id}/report?lang=<…>` · Result → `GET /api/v1/payment/vendor-payment/results/{id}/report?lang=<…>` (**plural `/results/`**).
Filename (all): `WeCMS_Vendor_Payment_download_{LANG}.xlsx`.

| Test ID | Page | Action | Expected endpoint | Expected file name | Expected result | PASS/FAIL |
|---|---|---|---|---|---|---|
| T1 | Scheduled **list** | row glyph | `GET …/vendor-payment/{id}/report?lang=EN` | `WeCMS_Vendor_Payment_download_EN.xlsx` | Generated 22-col report; opens clean; NOT uploaded file | ☐ |
| T2 | Scheduled **detail** | file-card | `GET …/vendor-payment/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean | ☐ |
| T3 | Result **list** | row glyph | `GET …/vendor-payment/results/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean | ☐ |
| T4 | Result **detail** | file-card | `GET …/vendor-payment/results/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean (Result-detail glyph wired) | ☐ |
| T5 | Scheduled list (KO) | switch KO, repeat T1 | `…/{id}/report?lang=ko` | `…_KO.xlsx` | KO template + KO headers | ☐ |
| T6 | Result detail (VI) | switch VI, repeat T4 | `…/results/{id}/report?lang=vi` | `…_VI.xlsx` | VI template + VI headers | ☐ |
| T7 | Any (KM fallback) | switch KM, repeat any | `…?lang=km` | `…_EN.xlsx` | EN template + EN filename | ☐ |
| T8 | Scheduled detail (Contact line) | download a batch whose line has a contact | scheduled report | `…_{LANG}.xlsx` | Col J **Contact Name** populated | ☐ |
| T9 | Result detail (dispatched) | download `TRNF_YN='Y'` | result report | `…_{LANG}.xlsx` | Col A (Transfer Date Time) + Col B (Status) populated | ☐ |
| T10 | Scheduled detail (not dispatched) | download `TRNF_YN='N'` | scheduled report | `…_{LANG}.xlsx` | Col A + Col B blank | ☐ |

---

## 4. Manual UI Tests (all 4 surfaces)

### 4.1 Scheduled list — row download (T1)
- ☐ Row glyph triggers `GET …/vendor-payment/{id}/report?lang=…`; does not navigate.
- ☐ Saved name `WeCMS_Vendor_Payment_download_{LANG}.xlsx`; opens clean.

### 4.2 Scheduled detail — file-card download (T2)
- ☐ File-card triggers **scheduled** endpoint; file is the generated 22-col report, not the uploaded `.xlsx`.

### 4.3 Result list — row download (T3)
- ☐ Row glyph triggers `…/vendor-payment/**results**/{id}/report?lang=…`; opens clean.

### 4.4 Result detail — file-card download (T4)
- ☐ File-card triggers the **result** endpoint (not the toolbar export); opens clean.

---

## 5. Workbook Verification

Open a file from a **dispatched** batch with approval + ≥ 2 lines. Vendor layout (22 cols **A–V**), header **Excel row 3**, data from **Excel row 4**, title merged across row 1–2, **no total row**.

| # | Item | Expected | PASS/FAIL |
|---|---|---|---|
| W1 | Sheet | Single sheet | ☐ |
| W2 | Title (merged row 1–2) | EN `VENDOR PAYMENTS` (+ KO/VI equivalents from the template) | ☐ |
| W3 | Header row = **Excel row 3** | 22 labels A–V (W5) | ☐ |
| W4 | Data starts **Excel row 4** | first detail on row 4 | ☐ |
| W5 | **22 columns A–V** | A Transfer Date Time · B Status · C Request Date/Time · D File Name · E Withdrawal Bank · F Account No. · G Deposit Bank Code · H Deposit Bank · I Deposit No. · **J Contact Name** · K Recipient Name · L Transfer Amount · M Transfer Currency · N Remarks · O Reporting Account · P Description · Q Document No. · R Requestor · S Scheduled Date · T Approval Status · U Approval Ref. · V Approval Title | ☐ |
| W6 | One row per detail | N detail lines → N data rows | ☐ |
| W7 | **Contact Name (J)** | Vendor-specific column populated from the line's contact (`TRCO_CMENM`); blank when no contact | ☐ |
| W8 | Transfer Amount (L) | Numeric; rendered per the template's baked number format | ☐ |
| W9 | Status (B) | Dispatched → `Success`/`Failed`/`In Progress`; not dispatched → blank (EN-only, R1) | ☐ |
| W10 | Deposit Bank (H) / Withdrawal Bank (E) | Resolved bank names; G = Deposit Bank Code (raw) | ☐ |
| W11 | Recipient (K) / Remarks (N) / Reporting Account (O) / Description (P) / Document No. (Q) | Map to the line's values | ☐ |
| W12 | Requestor (R) / Approval Status (T) / Approval Ref. (U) / Approval Title (V) | From the approval; blank when none | ☐ |
| W13 | No corruption | Opens clean; styling/widths/title preserved | ☐ |

---

## 6. Locale Verification

| # | Locale | Template | Title cell | Filename | PASS/FAIL |
|---|---|---|---|---|---|
| L1 | EN | `…_EN.xlsx` | `VENDOR PAYMENTS` | `WeCMS_Vendor_Payment_download_EN.xlsx` | ☐ |
| L2 | KO | `…_KO.xlsx` | KO title from template | `…_KO.xlsx` | ☐ |
| L3 | VI | `…_VI.xlsx` | VI title from template | `…_VI.xlsx` | ☐ |
| L4 | KM / unknown → EN | `…_EN.xlsx` | `VENDOR PAYMENTS` | `…_EN.xlsx` | ☐ |

- ☐ L5 — Header labels (W5) translated by template per locale.
- ☐ L6 — Status text **EN-only** across locales (R1); dates English-style (R2).

---

## 7. Backend / Network Verification

### 7.1 Scheduled — `GET /api/v1/payment/vendor-payment/{id}/report?lang=EN`
- ☐ 200 · XLSX MIME · `Content-Disposition` `…WeCMS_Vendor_Payment_download_EN.xlsx` · `X-Content-Type-Options: nosniff` · `Access-Control-Expose-Headers: Content-Disposition`

### 7.2 Result — `GET /api/v1/payment/vendor-payment/results/{id}/report?lang=EN`
- ☐ 200 · XLSX MIME · `…_EN.xlsx` · `nosniff`

### 7.3 Locale on the wire
- ☐ `?lang=ko` → `…_KO.xlsx` · `?lang=vi` → `…_VI.xlsx` · `?lang=km`/omitted/unknown → `…_EN.xlsx`

### 7.4 Frontend mapping (DevTools)
- ☐ Scheduled list/detail → `downloadVendorScheduledReport` → `…/{id}/report`
- ☐ Result list/detail → `downloadVendorResultReport` → `…/results/{id}/report`
- ☐ Current UI `locale` passed as `lang` on every call

---

## 8. Regression Tests

| # | Check | Expected | PASS/FAIL |
|---|---|---|---|
| N1 | Excel Download ≠ uploaded file | 4 surfaces return the generated 22-col report, not the uploaded `.xlsx` | ☐ |
| N2 | Upload flow unaffected | Vendor Excel upload still works | ☐ |
| N3 | Uploaded-file endpoint separate | `GET …/vendor-payment/{id}/file` returns the original file; not wired to Download buttons | ☐ |
| N4 | Result-list toolbar export separate | Toolbar export unchanged | ☐ |
| N5 | Generic export separate | Shared `ExcelExportService` export remains separate | ☐ |
| N6 | No cross-scope leakage | Scheduled → `/{id}/report`; Result → `/results/{id}/report` | ☐ |
| N7 | Authorization | Missing result/scheduled scope → 403 on the respective report (if role data available) | ☐ |
| N8 | Tenant isolation / unknown id | Another tenant's or unknown id → not-found, no cross-tenant data | ☐ |

---

## 9. PASS Criteria

PASSES when: (1) all 4 surfaces (T1–T4) download the generated report via the correct scoped endpoint and open clean; (2) filename `WeCMS_Vendor_Payment_download_{LANG}.xlsx` everywhere; (3) EN/KO/VI own template + KM→EN; (4) structure = 22 cols A–V incl. **Contact Name col J**, header Excel row 3, data row 4, one row per detail (W1–W7); (5) Status/approval fields correct, blank when not dispatched; (6) regression N1–N5 hold; (7) headers correct (§7). Items 1–6 are blockers.

---

## 10. Known Risks

| # | Risk | Detail | Severity |
|---|---|---|---|
| R1 | **Status / Approval-Status text EN-only** | Cols B / T render English regardless of locale (documented follow-up; headers ARE localized). | Low |
| R2 | **Dates English-style** | Cols A/C/S use `dd-MM-yyyy[ HH:mm:ss]`; no locale format, no timezone shift. | Low |
| R3 | **Contact Name source** | Col J = `TRCO_CMENM` (Vendor-specific addition over the Internal Transfer layout); blank for lines without a registered contact. | Info |
| R4 | **Amount uses template number format** | No runtime currency-aware format (Overseas-specific). | Info |
| R5 | **Status needs dispatch data** | Pre-dispatch / fresh DB → Status blank; use a dispatched batch (P6). | Medium |
| R6 | **Live DB / data dependent** | Needs representative data (multi-line batch + contact + approval, dispatched result, VND/USD). | Medium |

*Checklist only — no code, template, build, or test changes. Capture DevTools headers + a sample workbook per locale as evidence.*

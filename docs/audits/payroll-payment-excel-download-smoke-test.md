# Payroll Payment Excel Download — Pre-SIT Smoke Test Checklist

- **Feature:** Payment → Payroll → Excel **Download** (backend-generated, template-populated **20-column** report)
- **Slug:** `payroll-payment` · **Legacy SCDUL_PYMN_TYPE_CD:** `00`
- **System:** Refactor (`wecms-api` + `wecms-web`)
- **Status:** Implemented; pre-SIT manual smoke verification pending (live DB / data dependent)
- **References:** [payment-excel-download-refactor-audit.md](payment-excel-download-refactor-audit.md) (master) · [../../../docs/audits/payroll-payment-excel-download-audit.md](../../../docs/audits/payroll-payment-excel-download-audit.md) (parity audit)

> **Scope — IN:** the generated **report** download on the **4 per-batch surfaces** only.
> **Scope — OUT (do NOT test here):** Excel **upload**; the **original uploaded-file** download (`GET /{id}/file`); the Result **toolbar** export; any generic Excel export. §8 only confirms those remain separate.

---

## 1. Scope

Verify all 4 per-batch surfaces download the generated Payroll report:

1. Scheduled Payment **list** — row download glyph
2. Scheduled Payment **detail** — file-card download
3. Payment Result **list** — row download glyph
4. Payment Result **detail** — file-card download

Expected: filename `WeCMS_Payroll_download_{LANG}.xlsx` (note: **no "Payment"** in the name), generated from the backend locale template, **not** the uploaded Excel, opens clean, EN/KO/VI with KM→EN, **20 columns A–T** (Payroll-specific layout: a **Payroll Type** column at O; **no** Contact Name / Description / per-row Document No.), header on **Excel row 3**, data from **Excel row 4**, one row per detail, both Scheduled + Result work, uploaded-file endpoint stays separate.

---

## 2. Preconditions

| # | Precondition | Confirm |
|---|---|---|
| P1 | Backend running; templates on classpath | `build/resources/main/templates/payroll-payment/WeCMS_Payroll_download_{EN,KO,VI}.xlsx` present |
| P2 | Frontend running, pointed at backend | App loads, login works |
| P3 | Logged in; tenant has Payroll data | `GET /api/v1/auth/me` returns expected `biz_key` |
| P4 | Menu auth for both scopes | Can open Payroll Scheduled **and** Result pages without 403 |
| P5 | **Scheduled test batch (`type 00`)** with ≥ 2 detail lines + an approval attached whose `APPR_FUND_TP` is set (0 = Salary / 1 = Bonus) so col O populates; prefer one VND + one USD batch | Detail shows ≥ 2 rows |
| P6 | **Dispatched Result batch** (`TRNF_YN='Y'`) | Appears on Result list |
| P7 | Browser: "ask where to save" on; clear prior `WeCMS_Payroll_download_*.xlsx` | Browser settings |
| P8 | Excel viewer; DevTools Network open | — |
| P9 | UI language switch (EN/KO/VI; KM if present) | Profile menu → Languages |
| P10 | Note each batch's `SCDUL_PYMN_NO` | From row / detail URL |

---

## 3. Test Matrix

Endpoints: Scheduled → `GET /api/v1/payment/payroll-payment/{id}/report?lang=<…>` · Result → `GET /api/v1/payment/payroll-payment/results/{id}/report?lang=<…>` (**plural `/results/`**).
Filename (all): `WeCMS_Payroll_download_{LANG}.xlsx`.

| Test ID | Page | Action | Expected endpoint | Expected file name | Expected result | PASS/FAIL |
|---|---|---|---|---|---|---|
| T1 | Scheduled **list** | row glyph | `GET …/payroll-payment/{id}/report?lang=EN` | `WeCMS_Payroll_download_EN.xlsx` | Generated 20-col report; opens clean; NOT uploaded file | ☐ |
| T2 | Scheduled **detail** | file-card | `GET …/payroll-payment/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean | ☐ |
| T3 | Result **list** | row glyph | `GET …/payroll-payment/results/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean | ☐ |
| T4 | Result **detail** | file-card | `GET …/payroll-payment/results/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean | ☐ |
| T5 | Scheduled list (KO) | switch KO, repeat T1 | `…/{id}/report?lang=ko` | `…_KO.xlsx` | KO template + KO headers | ☐ |
| T6 | Result detail (VI) | switch VI, repeat T4 | `…/results/{id}/report?lang=vi` | `…_VI.xlsx` | VI template + VI headers | ☐ |
| T7 | Any (KM fallback) | switch KM, repeat any | `…?lang=km` | `…_EN.xlsx` | EN template + EN filename | ☐ |
| T8 | Scheduled detail (Salary/Bonus) | download a batch with `APPR_FUND_TP` set | scheduled report | `…_{LANG}.xlsx` | Col O **Payroll Type** = Salary (0) / Bonus (1); blank otherwise | ☐ |
| T9 | Result detail (dispatched) | download `TRNF_YN='Y'` | result report | `…_{LANG}.xlsx` | Col A (Transfer Date Time) + Col B (Status) populated | ☐ |
| T10 | Scheduled detail (not dispatched) | download `TRNF_YN='N'` | scheduled report | `…_{LANG}.xlsx` | Col A + Col B blank | ☐ |

---

## 4. Manual UI Tests (all 4 surfaces)

### 4.1 Scheduled list — row download (T1)
- ☐ Row glyph triggers `GET …/payroll-payment/{id}/report?lang=…`; does not navigate.
- ☐ Saved name `WeCMS_Payroll_download_{LANG}.xlsx`; opens clean.

### 4.2 Scheduled detail — file-card download (T2)
- ☐ File-card triggers **scheduled** endpoint; file is the generated 20-col report, not the uploaded `.xlsx`.

### 4.3 Result list — row download (T3)
- ☐ Row glyph triggers `…/payroll-payment/**results**/{id}/report?lang=…`; opens clean.

### 4.4 Result detail — file-card download (T4)
- ☐ File-card triggers the **result** endpoint (not the toolbar export); opens clean.

---

## 5. Workbook Verification

Open a file from a **dispatched** batch with approval + ≥ 2 lines. Payroll layout (20 cols **A–T**), header **Excel row 3**, data from **Excel row 4**, title merged across row 1–2, **no total row**. Note the Payroll-specific differences vs Vendor/Expense: **no Contact Name, no Description, no per-row Document No.**; col I is **Deposit Account No.**; col M Remarks is the withdrawal-account remark; col O is **Payroll Type**.

| # | Item | Expected | PASS/FAIL |
|---|---|---|---|
| W1 | Sheet | Single sheet | ☐ |
| W2 | Title (merged row 1–2) | EN `Payroll Payments` (+ KO/VI equivalents from the template) | ☐ |
| W3 | Header row = **Excel row 3** | 20 labels A–T (W5) | ☐ |
| W4 | Data starts **Excel row 4** | first detail on row 4 | ☐ |
| W5 | **20 columns A–T** | A Transfer Date Time · B Status · C Request Date/Time · D File Name · E Withdrawal Bank · F Account No. · G Deposit Bank Code · H Deposit Bank · **I Deposit Account No.** · J Recipient Name · K Transfer Amount · L Transfer Currency · M Remarks · N Reporting Account · **O Payroll Type** · P Requestor · Q Scheduled Date · R Approval Status · S Approval Ref. · T Approval Title | ☐ |
| W6 | One row per detail | N detail lines → N data rows | ☐ |
| W7 | **Payroll Type (O)** | `Salary` (APPR_FUND_TP=0) / `Bonus` (=1) / blank otherwise | ☐ |
| W8 | **No Contact Name / Description / per-row Document No.** | Those Vendor/Expense columns are absent (20-col layout, not 22) | ☐ |
| W9 | Transfer Amount (K) | Numeric; rendered per the template's baked number format | ☐ |
| W10 | Status (B) | Dispatched → `Success`/`Failed`/`In Progress`; not dispatched → blank (EN-only, R1) | ☐ |
| W11 | Deposit Bank (H) / Withdrawal Bank (E) | Resolved bank names; G = Deposit Bank Code (raw); I = Deposit Account No. | ☐ |
| W12 | Recipient (J) / Remarks (M) / Reporting Account (N) | Map to the line's recipient / withdrawal-account remark / chart-account code | ☐ |
| W13 | Requestor (P) / Approval Status (R) / Approval Ref. (S) / Approval Title (T) | From the approval; blank when none | ☐ |
| W14 | No corruption | Opens clean; styling/widths/title preserved | ☐ |

---

## 6. Locale Verification

| # | Locale | Template | Title cell | Filename | PASS/FAIL |
|---|---|---|---|---|---|
| L1 | EN | `…_EN.xlsx` | `Payroll Payments` | `WeCMS_Payroll_download_EN.xlsx` | ☐ |
| L2 | KO | `…_KO.xlsx` | KO title from template | `…_KO.xlsx` | ☐ |
| L3 | VI | `…_VI.xlsx` | VI title from template | `…_VI.xlsx` | ☐ |
| L4 | KM / unknown → EN | `…_EN.xlsx` | `Payroll Payments` | `…_EN.xlsx` | ☐ |

- ☐ L5 — Header labels (W5) translated by template per locale.
- ☐ L6 — Status text + Payroll Type (`Salary`/`Bonus`) are **EN-only** across locales (R1); dates English-style (R2).

---

## 7. Backend / Network Verification

### 7.1 Scheduled — `GET /api/v1/payment/payroll-payment/{id}/report?lang=EN`
- ☐ 200 · XLSX MIME · `Content-Disposition` `…WeCMS_Payroll_download_EN.xlsx` · `X-Content-Type-Options: nosniff` · `Access-Control-Expose-Headers: Content-Disposition`

### 7.2 Result — `GET /api/v1/payment/payroll-payment/results/{id}/report?lang=EN`
- ☐ 200 · XLSX MIME · `…_EN.xlsx` · `nosniff`

### 7.3 Locale on the wire
- ☐ `?lang=ko` → `…_KO.xlsx` · `?lang=vi` → `…_VI.xlsx` · `?lang=km`/omitted/unknown → `…_EN.xlsx`

### 7.4 Frontend mapping (DevTools)
- ☐ Scheduled list/detail → `downloadPayrollScheduledReport` → `…/{id}/report`
- ☐ Result list/detail → `downloadPayrollResultReport` → `…/results/{id}/report`
- ☐ Current UI `locale` passed as `lang` on every call

---

## 8. Regression Tests

| # | Check | Expected | PASS/FAIL |
|---|---|---|---|
| N1 | Excel Download ≠ uploaded file | 4 surfaces return the generated 20-col report, not the uploaded `.xlsx` | ☐ |
| N2 | Upload flow unaffected | Payroll Excel upload still works | ☐ |
| N3 | Uploaded-file endpoint separate | `GET …/payroll-payment/{id}/file` returns the original file; not wired to Download buttons | ☐ |
| N4 | Result-list toolbar export separate | Toolbar export unchanged | ☐ |
| N5 | Generic export separate | Shared `ExcelExportService` export remains separate | ☐ |
| N6 | No cross-scope leakage | Scheduled → `/{id}/report`; Result → `/results/{id}/report` | ☐ |
| N7 | Authorization | Missing result/scheduled scope → 403 on the respective report (if role data available) | ☐ |
| N8 | Tenant isolation / unknown id | Another tenant's or unknown id → not-found, no cross-tenant data | ☐ |

---

## 9. PASS Criteria

PASSES when: (1) all 4 surfaces (T1–T4) download the generated report via the correct scoped endpoint and open clean; (2) filename `WeCMS_Payroll_download_{LANG}.xlsx` everywhere; (3) EN/KO/VI own template + KM→EN; (4) structure = **20 cols A–T** incl. **Payroll Type col O**, **Deposit Account No. col I**, no Contact Name/Description/per-row Doc No., header Excel row 3, data row 4, one row per detail (W1–W8); (5) Status/Payroll-Type/approval fields correct, blank when not dispatched; (6) regression N1–N5 hold; (7) headers correct (§7). Items 1–6 are blockers.

---

## 10. Known Risks

| # | Risk | Detail | Severity |
|---|---|---|---|
| R1 | **Status / Approval-Status / Payroll Type text EN-only** | Cols B / R / O render English regardless of locale (documented follow-up; headers ARE localized). | Low |
| R2 | **Dates English-style** | Cols A/C/Q use `dd-MM-yyyy[ HH:mm:ss]`; no locale format, no timezone shift. | Low |
| R3 | **Payroll-specific layout** | 20 cols (not 22): no Contact Name / Description / per-row Document No.; col O = Payroll Type (`APPR_FUND_TP`), col M Remarks = withdrawal-account remark, col I = Deposit Account No. Confirm against the Payroll template, not the Vendor/Expense layout. | Info |
| R4 | **Filename has no "Payment"** | `WeCMS_Payroll_download_{LANG}.xlsx` (template + service prefix agree) — intentional, not a mismatch. | Info |
| R5 | **Amount uses template number format** | No runtime currency-aware format (Overseas-specific). | Info |
| R6 | **Status needs dispatch data** | Pre-dispatch / fresh DB → Status blank; use a dispatched batch (P6). | Medium |
| R7 | **Live DB / data dependent** | Needs representative data (multi-line batch + approval with `APPR_FUND_TP`, dispatched result, VND/USD). | Medium |

*Checklist only — no code, template, build, or test changes. Capture DevTools headers + a sample workbook per locale as evidence.*

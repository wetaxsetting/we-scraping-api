# Internal Transfer Excel Download — Pre-SIT Smoke Test Checklist

- **Feature:** Payment → Internal Transfer → Excel **Download** (backend-generated, template-populated **21-column** report — legacy `pymn_0009_003_r002` / `pymn_0010_002_r002`)
- **Slug:** `internal-transfer` · **Legacy SCDUL_PYMN_TYPE_CD:** `03`
- **System:** Refactor (`wecms-api` + `wecms-web`)
- **Status:** Implemented; pre-SIT manual smoke verification pending (live DB / data dependent)
- **References:** [payment-excel-download-refactor-audit.md](payment-excel-download-refactor-audit.md) (master) · [../../../docs/audits/internal-transfer-excel-download-audit.md](../../../../docs/audits/internal-transfer-excel-download-audit.md) (legacy spec)

> **Scope — IN:** the generated **report** download on the **4 per-batch surfaces** only.
> **Scope — OUT (do NOT test here):** Excel **upload**; the **original uploaded-file** download (`GET /{id}/file`); the Transfer-Result **toolbar** export; any generic Excel export. §8 only confirms those remain separate.

---

## 1. Scope

Verify all 4 per-batch surfaces download the generated Internal Transfer report:

1. Scheduled Transfer **list** — row download glyph
2. Scheduled Transfer **detail** — file-card download
3. Transfer Result **list** — row download glyph
4. Transfer Result **detail** — file-card download

Expected: filename `WeCMS_Internal_Transfer_download_{LANG}.xlsx`, generated from the backend locale template, **not** the uploaded Excel, opens clean, EN/KO/VI with KM→EN, **21 columns A–U**, header on **Excel row 3**, data from **Excel row 4**, one row per transfer detail, both Scheduled + Result work, uploaded-file endpoint stays separate.

---

## 2. Preconditions

| # | Precondition | Confirm |
|---|---|---|
| P1 | Backend running; templates on classpath | `build/resources/main/templates/internal-transfer/WeCMS_Internal_Transfer_download_{EN,KO,VI}.xlsx` present |
| P2 | Frontend running, pointed at backend | App loads, login works |
| P3 | Logged in; tenant has Internal Transfer data | `GET /api/v1/auth/me` returns expected `biz_key` |
| P4 | Menu auth for both scopes | Can open Scheduled (`payment-internal-transfer-scheduled`) **and** Result (`payment-internal-transfer-result`) pages without 403 |
| P5 | **Scheduled test batch (`type 03`)** with ≥ 2 detail lines + an approval (DOC_NO / approval line) attached; prefer one VND + one USD batch | Visible on Scheduled list; detail shows ≥ 2 rows |
| P6 | **Dispatched Result batch** (`TRNF_YN='Y'`) so Status + Transfer Date Time populate | Appears on Transfer Result list |
| P7 | Browser: "ask where to save" on; downloads folder cleared of prior `WeCMS_Internal_Transfer_download_*.xlsx` | Browser settings |
| P8 | Excel viewer available; DevTools Network open ("Preserve log") | — |
| P9 | UI language switch available (EN/KO/VI; KM if present) | Profile menu → Languages |
| P10 | Note each batch's `SCDUL_PYMN_NO` for §6 | From row / detail URL |

---

## 3. Test Matrix

Endpoints: Scheduled → `GET /api/v1/payment/internal-transfer/{id}/report?lang=<EN|KO|VI>` · Result → `GET /api/v1/payment/internal-transfer/results/{id}/report?lang=<…>` (**plural `/results/`**).
Filename (all): `WeCMS_Internal_Transfer_download_{LANG}.xlsx`.

| Test ID | Page | Action | Expected endpoint | Expected file name | Expected result | PASS/FAIL |
|---|---|---|---|---|---|---|
| T1 | Scheduled **list** | row download glyph | `GET …/internal-transfer/{id}/report?lang=EN` | `WeCMS_Internal_Transfer_download_EN.xlsx` | Generated 21-col report; opens clean; NOT uploaded file | ☐ |
| T2 | Scheduled **detail** | file-card download | `GET …/internal-transfer/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean | ☐ |
| T3 | Result **list** | row download glyph | `GET …/internal-transfer/results/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean | ☐ |
| T4 | Result **detail** | file-card download | `GET …/internal-transfer/results/{id}/report?lang=EN` | `…_EN.xlsx` | Same report; opens clean (Result-detail glyph wired) | ☐ |
| T5 | Scheduled list (KO) | switch KO, repeat T1 | `…/{id}/report?lang=ko` | `…_KO.xlsx` | KO template + KO headers | ☐ |
| T6 | Result detail (VI) | switch VI, repeat T4 | `…/results/{id}/report?lang=vi` | `…_VI.xlsx` | VI template + VI headers | ☐ |
| T7 | Any (KM fallback) | switch KM, repeat any | `…?lang=km` | `…_EN.xlsx` | EN template + EN filename | ☐ |
| T8 | Scheduled detail (VND) | download VND batch | scheduled report | `…_{LANG}.xlsx` | Amount renders per template number format | ☐ |
| T9 | Result detail (dispatched) | download `TRNF_YN='Y'` batch | result report | `…_{LANG}.xlsx` | Col A (Transfer Date Time) + Col B (Status) populated | ☐ |
| T10 | Scheduled detail (not dispatched) | download `TRNF_YN='N'` batch | scheduled report | `…_{LANG}.xlsx` | Col A + Col B blank (legacy parity) | ☐ |

---

## 4. Manual UI Tests (all 4 surfaces)

### 4.1 Scheduled list — row download (T1)
- ☐ Per-row download glyph visible; click triggers `GET …/internal-transfer/{id}/report?lang=…`.
- ☐ Click does **not** navigate to the detail page.
- ☐ Saved name `WeCMS_Internal_Transfer_download_{LANG}.xlsx`; opens without repair prompt.

### 4.2 Scheduled detail — file-card download (T2)
- ☐ File-card download triggers the **scheduled** endpoint.
- ☐ Downloaded file is the **generated 21-col report** (see §5), not the uploaded `.xlsx`.

### 4.3 Result list — row download (T3)
- ☐ Row glyph triggers `GET …/internal-transfer/**results**/{id}/report?lang=…`.
- ☐ Report opens clean.

### 4.4 Result detail — file-card download (T4)
- ☐ File-card download triggers the **result** endpoint (not the toolbar export).
- ☐ Report opens clean.

---

## 5. Workbook Verification

Open a file from a **dispatched** batch with approval + ≥ 2 lines. Internal Transfer layout (21 cols, **A–U**), header **Excel row 3**, data from **Excel row 4**, title merged across row 1–2 (`A1:U2`), **no total row**.

| # | Item | Expected | PASS/FAIL |
|---|---|---|---|
| W1 | Sheet | Single sheet | ☐ |
| W2 | Title (merged row 1–2) | EN `INTERNAL TRANSFER` · KO `내부 전송` · VI `CHUYỂN KHOẢN NỘI BỘ` | ☐ |
| W3 | Header row = **Excel row 3** | 21 labels A–U (W5); no separate total row above it | ☐ |
| W4 | Data starts **Excel row 4** | first detail on row 4 | ☐ |
| W5 | **21 columns A–U** | A Transfer Date Time · B Status · C Request Date/Time · D File Name · E Withdrawal Bank · F Account No. · G Deposit Bank Code · H Deposit Bank · I Deposit No. · J Recipient Name · K Transfer Amount · L Transfer Currency · M Remarks · N Reporting Account · O Description · P Document No. · Q Requestor · R Scheduled Date · S Approval Status · T Approval Ref. · U Approval Title | ☐ |
| W6 | One row per transfer detail | N detail lines → N data rows | ☐ |
| W7 | Transfer Amount (K) | Numeric (not text); rendered per the template's baked number format | ☐ |
| W8 | Status (B) | Dispatched → `Success`/`Failed`/`In Progress`; not dispatched → blank (EN-only, see R1) | ☐ |
| W9 | Deposit Bank (H) / Withdrawal Bank (E) | Resolved bank **names** (not raw codes); G = Deposit Bank Code (raw) | ☐ |
| W10 | Recipient Name (J) / Remarks (M) / Reporting Account (N) | Map to the line's recipient / usage / chart-account code | ☐ |
| W11 | Requestor (Q) / Approval Status (S) / Approval Ref. (T) / Approval Title (U) | From the attached approval; blank when no approval | ☐ |
| W12 | No corruption | Opens clean; template styling/widths/title preserved | ☐ |

---

## 6. Locale Verification

| # | Locale | Template | Title cell | Filename | PASS/FAIL |
|---|---|---|---|---|---|
| L1 | EN | `…_EN.xlsx` | `INTERNAL TRANSFER` | `WeCMS_Internal_Transfer_download_EN.xlsx` | ☐ |
| L2 | KO | `…_KO.xlsx` | `내부 전송` | `…_KO.xlsx` | ☐ |
| L3 | VI | `…_VI.xlsx` | `CHUYỂN KHOẢN NỘI BỘ` | `…_VI.xlsx` | ☐ |
| L4 | KM / unknown → EN | `…_EN.xlsx` | `INTERNAL TRANSFER` | `…_EN.xlsx` | ☐ |

- ☐ L5 — Header labels (W5) are translated by the template per locale.
- ☐ L6 — Status text is **EN-only** across locales (known follow-up R1); dates English-style (R2).

---

## 7. Backend / Network Verification

### 7.1 Scheduled — `GET /api/v1/payment/internal-transfer/{id}/report?lang=EN`
- ☐ HTTP 200
- ☐ `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- ☐ `Content-Disposition` contains `filename*=UTF-8''WeCMS_Internal_Transfer_download_EN.xlsx`
- ☐ `X-Content-Type-Options: nosniff`
- ☐ `Access-Control-Expose-Headers: Content-Disposition`

### 7.2 Result — `GET /api/v1/payment/internal-transfer/results/{id}/report?lang=EN`
- ☐ HTTP 200; XLSX MIME; `…_EN.xlsx` in `Content-Disposition`; `nosniff` present

### 7.3 Locale on the wire
- ☐ `?lang=ko` → `…_KO.xlsx` · `?lang=vi` → `…_VI.xlsx` · `?lang=km`/omitted/unknown → `…_EN.xlsx`

### 7.4 Frontend mapping (confirm in DevTools)
- ☐ Scheduled list/detail → `downloadScheduledTransferReport` → `…/{id}/report`
- ☐ Result list/detail → `downloadTransferResultReport` → `…/results/{id}/report`
- ☐ Current UI `locale` passed as `lang` on every call

---

## 8. Regression Tests

| # | Check | Expected | PASS/FAIL |
|---|---|---|---|
| N1 | Excel Download ≠ uploaded file | 4 surfaces return the generated 21-col report, not the user's uploaded `.xlsx` | ☐ |
| N2 | Upload flow unaffected | Internal Transfer Excel upload (template → preview → confirm) still works | ☐ |
| N3 | Uploaded-file endpoint separate | `GET …/internal-transfer/{id}/file` still returns the original uploaded file; not wired to any Download button | ☐ |
| N4 | Result-list toolbar export separate | Toolbar export still calls its own endpoint; unchanged | ☐ |
| N5 | Generic export separate | Any shared `ExcelExportService` export remains separate | ☐ |
| N6 | No cross-scope leakage | Scheduled → `/{id}/report`; Result → `/results/{id}/report` | ☐ |
| N7 | Authorization | Missing `payment-internal-transfer-result` → 403 on result report; missing scheduled scope → 403 on scheduled report (if role data available) | ☐ |
| N8 | Tenant isolation / unknown id | Another tenant's or unknown `SCDUL_PYMN_NO` → not-found error, no cross-tenant data | ☐ |

---

## 9. PASS Criteria

PASSES when: (1) all 4 surfaces (T1–T4) download the generated report via the correct scoped endpoint and open clean; (2) filename `WeCMS_Internal_Transfer_download_{LANG}.xlsx` on every surface/locale; (3) EN/KO/VI load own template + KM→EN (L1–L4); (4) structure = 21 cols A–U, header Excel row 3, data row 4, one row per detail (W1–W6); (5) Status/approval fields correct, blank when not dispatched (W8, T9–T10); (6) regression items N1–N5 hold; (7) response headers correct (§7). Items 1–6 are blockers.

---

## 10. Known Risks

| # | Risk | Detail | Severity |
|---|---|---|---|
| R1 | **Status / Approval-Status text is EN-only** | Cols B / S render `Success`/`Failed`/`In Progress` / `Pending`/`Approved`/`Rejected`/`In Progress` in English regardless of locale (documented follow-up; headers ARE localized via the template). | Low |
| R2 | **Dates English-style** | Cols A/C/R use `dd-MM-yyyy[ HH:mm:ss]`; not locale-specific; no timezone shift. | Low |
| R3 | **Amount uses template number format** | Internal Transfer relies on the template's baked numeric format (`0_);[Red]\(0\)`); it does not apply a runtime currency-aware format (that is Overseas-specific). | Info |
| R4 | **Status needs dispatch data** | Per-line Status comes from dispatch results; on a fresh DB / pre-dispatch batch Status is blank. Use a dispatched batch (P6) for W8/T9. | Medium |
| R5 | **Live DB / data dependent** | Content checks need representative data (multi-line batch + approval, dispatched result, VND/USD). Unit tests cover layout/locale/mapping; this smoke test validates real data. | Medium |

*Checklist only — no code, template, build, or test changes. Capture DevTools headers + a sample workbook per locale as evidence.*

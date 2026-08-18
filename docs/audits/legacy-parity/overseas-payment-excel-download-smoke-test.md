# Overseas Payment Excel Download — Pre-SIT Smoke Test Checklist

- **Feature:** Payment → Overseas Payment → Excel **Download** (the backend-generated, template-populated 22-column report — legacy `pymn_0008_002_r002`)
- **Slug:** `overseas-payment`
- **System:** Refactor (`wecms-api` + `wecms-web`)
- **Date:** 2026-06-30
- **Status:** Implemented; pre-SIT manual smoke verification pending (live DB / data dependent)
- **References:**
  - [payment-excel-download-refactor-audit.md](payment-excel-download-refactor-audit.md) (master refactor audit)
  - [../../../docs/audits/overseas-payment-excel-download-audit.md](../../../../docs/audits/overseas-payment-excel-download-audit.md) (legacy spec + parity gaps)
  - [../../../docs/audits/internal-transfer-excel-download-audit.md](../../../../docs/audits/internal-transfer-excel-download-audit.md) (reference pattern)
  - Implementation report + memory `project_overseas_payment_excel_download_parity_gap.md`

> **Scope — IN:** the generated **report** download on the **4 per-batch surfaces** only.
> **Scope — OUT (do NOT test here):** Excel **upload**; the **original uploaded-file** download (`GET /scheduled/{id}/file`); the Result-**list toolbar** export (`GET /result/export`); the Result-**detail toolbar** generic export (`POST /result/{id}/export`). These are separate features and have their own contracts; §5 only confirms they remain *untouched / separate*, it does not exercise them as the subject.

---

## 0. Preconditions

| # | Precondition | How to confirm |
|---|---|---|
| P1 | **Backend running** with the Overseas templates on the classpath | `build/resources/main/templates/overseas-payment/WeCMS_Overseas_Payment_download_{EN,KO,VI}.xlsx` present after build; backend started without error |
| P2 | **Frontend running** and pointed at the backend (`NEXT_PUBLIC_API_BASE_URL`) | App loads, login works |
| P3 | **Logged in** as a user whose tenant has Overseas Payment data | `GET /api/v1/auth/me` returns the expected `biz_key` |
| P4 | **Menu authorization** present for both scopes | User can open Payment → Overseas Payment **Scheduled** (`payment-overseas-request`) **and** Payment **Result** (`payment-overseas-result`) pages without 403 |
| P5 | **Test data — Scheduled (E-mode / Excel batch):** at least one Overseas Payment batch with ≥ 2 beneficiary detail lines, an **approval** (DOC_NO / approval line) attached, and a known currency (prefer one VND batch + one USD batch) | Visible on the Scheduled list; detail page shows ≥ 2 rows |
| P6 | **Test data — Result (dispatched):** at least one batch with `TRNF_YN = 'Y'` (transferred), so Status / Transfer Date Time / per-line PROC_STAT are populated | Batch appears on the Payment Result list |
| P7 | *(Optional)* **M-mode batch** (manual/individual `OVRS_PYMN_MSTR`) for the M-mode spot-check (T12) | Available only if the tenant has a manual overseas payment |
| P8 | **Browser download setup:** auto-open disabled, "Ask where to save" **on** (so the server filename is visible), downloads folder cleared of prior `WeCMS_Overseas_Payment_download_*.xlsx` | Browser settings |
| P9 | **Excel viewer** available (Excel / LibreOffice / Google Sheets) to open and inspect the workbook | — |
| P10 | **Dev tools** open → Network tab, "Preserve log" on (to capture request URL, response headers, payload size) | Browser DevTools |
| P11 | **UI language switch** available (EN / KO / VI; KM if present) to drive locale tests | Profile menu → Languages |
| P12 | Note the **OVRS_PYMN_NO** (batch id) of each test batch for the backend/network checks in §6 | From the row / detail URL |

---

## 1. Test Matrix

Endpoints (verified in implementation):
- Scheduled scope → `GET /api/v1/payment/overseas-payment/{id}/report?lang=<EN|KO|VI>`
- Result scope → `GET /api/v1/payment/overseas-payment/result/{id}/report?lang=<EN|KO|VI>`

Expected filename for all: **`WeCMS_Overseas_Payment_download_{LANG}.xlsx`** (LANG = EN/KO/VI; KM/other → EN).

| Test ID | Page | Action | Expected endpoint | Expected file name | Expected result | PASS/FAIL |
|---|---|---|---|---|---|---|
| T1 | Scheduled Payment **list** | Click the per-row download glyph | `GET …/overseas-payment/{id}/report?lang=EN` | `WeCMS_Overseas_Payment_download_EN.xlsx` | Generated 22-col report downloads; opens clean; NOT the uploaded file | ☐ |
| T2 | Scheduled Payment **detail** | Click the file-card download button | `GET …/overseas-payment/{id}/report?lang=EN` | `WeCMS_Overseas_Payment_download_EN.xlsx` | Same generated report; opens clean | ☐ |
| T3 | Payment Result **list** | Click the per-row download glyph | `GET …/overseas-payment/result/{id}/report?lang=EN` | `WeCMS_Overseas_Payment_download_EN.xlsx` | Same generated report; opens clean | ☐ |
| T4 | Payment Result **detail** | Click the file-card download button | `GET …/overseas-payment/result/{id}/report?lang=EN` | `WeCMS_Overseas_Payment_download_EN.xlsx` | Same generated report; opens clean | ☐ |
| T5 | Scheduled list (KO) | Switch UI to KO, repeat T1 | `…/{id}/report?lang=ko` | `WeCMS_Overseas_Payment_download_KO.xlsx` | KO template; KO headers + localized Status/Fee/Approval | ☐ |
| T6 | Result detail (VI) | Switch UI to VI, repeat T4 | `…/result/{id}/report?lang=vi` | `WeCMS_Overseas_Payment_download_VI.xlsx` | VI template; VI headers + localized Status/Fee/Approval | ☐ |
| T7 | Any surface (KM fallback) | Switch UI to KM (if present), repeat any download | `…/report?lang=km` | `WeCMS_Overseas_Payment_download_EN.xlsx` | **Falls back to EN** template + EN filename | ☐ |
| T8 | Scheduled detail (VND batch) | Download a VND batch | scheduled report | `…_{LANG}.xlsx` | Amount cells + total have **no decimals**; total `Total Amount (VND) : …` | ☐ |
| T9 | Scheduled detail (USD batch) | Download a USD batch | scheduled report | `…_{LANG}.xlsx` | Amount cells + total have **2 decimals**; total `Total Amount (USD) : …` | ☐ |
| T10 | Result detail (dispatched) | Download a `TRNF_YN='Y'` batch | result report | `…_{LANG}.xlsx` | Col A (Transfer Date Time) + Col C (Status) populated | ☐ |
| T11 | Scheduled detail (not dispatched) | Download a `TRNF_YN='N'` batch | scheduled report | `…_{LANG}.xlsx` | Col A + Col C **blank** (legacy parity) | ☐ |
| T12 | *(Optional)* M-mode batch | Download a manual/individual batch | scheduled or result report | `…_{LANG}.xlsx` | Single populated row; opens clean (see Known Risks R3) | ☐ |

---

## 2. Manual UI Tests (all 4 surfaces)

For **each** surface, perform the steps and tick every sub-check.

### 2.1 Scheduled Payment list — row download (T1)
- ☐ The Scheduled list shows a per-row download glyph (16px, right end of the row).
- ☐ Clicking it triggers a download (DevTools shows `GET /api/v1/payment/overseas-payment/{id}/report?lang=…`).
- ☐ Clicking the glyph does **not** navigate to the detail page (row link not fired).
- ☐ Saved file name is `WeCMS_Overseas_Payment_download_{LANG}.xlsx`.
- ☐ File opens in Excel without a "repair / corrupt" prompt.

### 2.2 Scheduled Payment detail — file-card download (T2)
- ☐ Open a batch detail; the file card shows the download button.
- ☐ Clicking triggers `GET …/overseas-payment/{id}/report?lang=…` (scheduled scope).
- ☐ Saved file name is `WeCMS_Overseas_Payment_download_{LANG}.xlsx`.
- ☐ The downloaded file is the **generated report** (22 columns, see §3) — **not** the originally uploaded `.xlsx`.
- ☐ File opens clean.

### 2.3 Payment Result list — row download (T3)
- ☐ The Result list shows a per-row download glyph.
- ☐ Clicking triggers `GET …/overseas-payment/result/{id}/report?lang=…` (**result** scope).
- ☐ Saved file name is `WeCMS_Overseas_Payment_download_{LANG}.xlsx`.
- ☐ File opens clean; is the generated report.

### 2.4 Payment Result detail — file-card download (T4)
- ☐ Open a Result detail; the file card shows the download button.
- ☐ Clicking triggers `GET …/overseas-payment/result/{id}/report?lang=…` (**result** scope).
- ☐ Saved file name is `WeCMS_Overseas_Payment_download_{LANG}.xlsx`.
- ☐ File is the generated report; opens clean.
- ☐ The Result-detail **toolbar Excel** button (separate generic export) is **not** the button under test — confirm the **file card** button is the one exercised.

---

## 3. Workbook Content Tests

Open a downloaded file from a **dispatched E-mode batch** with a known approval and ≥ 2 detail lines. Verify against the legacy 22-column layout (audit §4/§10).

| # | Item | Expected | PASS/FAIL |
|---|---|---|---|
| W1 | **Sheet** | Single sheet named `sheet1` | ☐ |
| W2 | **Title (merged `A1:V2`)** | EN `Overseas Payment` · KO `해외 송금신청` · VI `Thanh toán quốc tế` | ☐ |
| W3 | **Total row (merged `A3:T3`)** | Overwritten with `Total Amount (<CCY>) : <formatted amount>` (single currency, not the 3-currency template placeholder) | ☐ |
| W4 | **Header row = Excel row 4** | 22 header labels A–V (see W6); row 4, not row 3 | ☐ |
| W5 | **Data starts at Excel row 5** | First detail line on row 5; no data on rows 1–4 except title/total/header | ☐ |
| W6 | **22 columns A–V present** | A Transfer Date Time · B Transaction Seq · C Status · D Request Date/Time · E File Name · F Withdrawal Bank · G Account No. · H Beneficiary · I Beneficiary Bank Name · J Bank Identifier Code (BIC) · K Beneficiary Account Number · L Beneficiary Address · M Transfer Currency · N Transfer Amount · O Fee Payer · P Reporting Account · Q Memo · R Requestor · S Scheduled Date · T Approval Status · U Approval Ref. · V Approval Title | ☐ |
| W7 | **One row per transaction detail** | N detail lines → N data rows (row 5 … row 5+N−1); no extra/duplicate rows | ☐ |
| W8 | **Transfer Amount (N)** | Numeric (not text); VND = no decimals, USD/KRW/other = 2 decimals; right-aligned per template | ☐ |
| W9 | **Status (C)** | Dispatched: `Success` / `Failed` / `In Progress` (localized per §4). Not dispatched: blank | ☐ |
| W10 | **Fee Payer (O)** | `Beneficiary Charge` / `Ordering Charge` / `Shared` (localized per §4); blank if unset | ☐ |
| W11 | **Approval Status (T)** | `Pending` / `Approved` / `Rejected` / `In Progress` (localized; **never "Cancelled"**) | ☐ |
| W12 | **Reporting Account (P)** | Populated for lines with a chart account (see Known Risk R1 — currently the **code**, not the name) | ☐ |
| W13 | **Memo (Q) / Remarks** | Matches the line's memo | ☐ |
| W14 | **Requestor (R)** | The approval draft user name (falls back to blank when no approval) | ☐ |
| W15 | **Approval Ref. (U) / Approval Title (V)** | DOC_NO and approval title from the attached approval; blank when no approval | ☐ |
| W16 | **Beneficiary block (H–L)** | Beneficiary name, bank name, BIC (col J = bank code on E-mode), account number, address all map to the correct columns | ☐ |
| W17 | **Withdrawal Bank (F) / Account No. (G)** | F = resolved bank **name** (not the raw code); G = withdrawal account number | ☐ |
| W18 | **No corruption** | Excel opens with no repair prompt; column widths, borders, header fill, and title styling preserved from the template | ☐ |

---

## 4. Locale Tests

Repeat a download (any surface) under each UI language; confirm template, headers, filename, and the localized derived strings (Status / Fee Payer / Approval Status).

| # | Locale | Template loaded | Title cell | Localized examples (Status / Fee / Approval) | Filename | PASS/FAIL |
|---|---|---|---|---|---|---|
| L1 | **EN** | `…_EN.xlsx` | `Overseas Payment` | `Success` · `Beneficiary Charge` · `Approved` | `WeCMS_Overseas_Payment_download_EN.xlsx` | ☐ |
| L2 | **KO** | `…_KO.xlsx` | `해외 송금신청` | `성공` · `수취인 부담` · `승인` | `WeCMS_Overseas_Payment_download_KO.xlsx` | ☐ |
| L3 | **VI** | `…_VI.xlsx` | `Thanh toán quốc tế` | `Đã thành công` · `Người thụ hưởng chịu phí` · `Đã phê duyệt` | `WeCMS_Overseas_Payment_download_VI.xlsx` | ☐ |
| L4 | **KM / unknown → EN** | `…_EN.xlsx` (fallback) | `Overseas Payment` | EN strings | `WeCMS_Overseas_Payment_download_EN.xlsx` | ☐ |

- ☐ L5 — Header **labels** in W6 are translated by the template per locale (KO/VI headers differ from EN).
- ☐ L6 — Dates remain English-style (`dd-MMM-yyyy`) across all locales — see Known Risk R2 (this is expected, not a defect).

---

## 5. Negative / Regression Checks

| # | Check | Expected | PASS/FAIL |
|---|---|---|---|
| N1 | **Excel Download ≠ uploaded file** | The file from all 4 surfaces is the generated 22-column report; it is **not** the originally uploaded Excel (different content, 22-col layout, generated filename `WeCMS_Overseas_Payment_download_{LANG}.xlsx`, not the user's upload name) | ☐ |
| N2 | **Upload template unaffected** | The Overseas Excel **upload** flow (download blank template → browse → preview → confirm) is unchanged and still works | ☐ |
| N3 | **Uploaded-file endpoint still separate** | `GET /api/v1/payment/overseas-payment/scheduled/{id}/file` still exists and, if exercised directly, returns the **original uploaded** Excel (import-source) — it is **not** wired to any Excel-Download button | ☐ |
| N4 | **Result-list toolbar export separate** | The Result list **toolbar** Excel button still calls `GET …/overseas-payment/result/export` (all filtered batches) and is unchanged — not the per-batch report | ☐ |
| N5 | **Result-detail generic export separate** | The Result detail **toolbar** Excel button still calls `POST …/overseas-payment/result/{id}/export` (shared `ExcelExportService`, View-Setting columns) and is unchanged — not the file-card report | ☐ |
| N6 | **No cross-scope leakage** | Scheduled surfaces hit `…/{id}/report`; Result surfaces hit `…/result/{id}/report` (confirm in DevTools) | ☐ |
| N7 | **Authorization** | A user without `payment-overseas-result` cannot pull `…/result/{id}/report` (403); a user without `payment-overseas-request` cannot pull `…/{id}/report` (403) — if role test data is available | ☐ |
| N8 | **Tenant isolation** | Requesting another tenant's `OVRS_PYMN_NO` returns `WBE2045` (not found), not another tenant's data | ☐ |
| N9 | **Unknown batch id** | A non-existent `OVRS_PYMN_NO` returns `WBE2045` | ☐ |

---

## 6. Backend / Network Verification

Capture the request + response headers in DevTools (or `curl` with the session JWT). Use a real `{id}` (OVRS_PYMN_NO) from P12.

### 6.1 Scheduled scope
- Request: `GET /api/v1/payment/overseas-payment/{id}/report?lang=EN`
- ☐ HTTP **200**
- ☐ `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- ☐ `Content-Disposition` contains `filename*=UTF-8''WeCMS_Overseas_Payment_download_EN.xlsx`
- ☐ `X-Content-Type-Options: nosniff`
- ☐ `Access-Control-Expose-Headers: Content-Disposition`
- ☐ Response body is a non-trivial binary (size > template baseline; opens as XLSX)

### 6.2 Result scope
- Request: `GET /api/v1/payment/overseas-payment/result/{id}/report?lang=EN`
- ☐ HTTP **200**
- ☐ `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- ☐ `Content-Disposition` contains `WeCMS_Overseas_Payment_download_EN.xlsx`
- ☐ `X-Content-Type-Options: nosniff`
- ☐ `Access-Control-Expose-Headers: Content-Disposition`

### 6.3 Locale on the wire
- ☐ `?lang=ko` → `Content-Disposition` filename = `…_KO.xlsx`
- ☐ `?lang=vi` → `…_VI.xlsx`
- ☐ `?lang=km` (or omitted / unknown) → `…_EN.xlsx`

### 6.4 Frontend call mapping (confirm in DevTools, not by reading code)
- ☐ Scheduled list/detail buttons → `downloadOverseasPaymentReport` → `…/{id}/report`
- ☐ Result list/detail buttons → `downloadOverseasPaymentResultReport` → `…/result/{id}/report`
- ☐ The current UI `locale` is passed as the `lang` query param on every call

---

## 7. PASS Criteria

The feature **PASSES** pre-SIT smoke when **all** of the following hold:

1. **All 4 surfaces** (T1–T4) download the generated report via the correct scoped endpoint (Scheduled → `…/{id}/report`; Result → `…/result/{id}/report`), and the file opens without corruption.
2. **Filename** is `WeCMS_Overseas_Payment_download_{LANG}.xlsx` on every surface and every locale (T1–T7, §6).
3. **Locale** behaves correctly: EN/KO/VI load their own template (title + headers + localized Status/Fee/Approval), and **KM/unknown falls back to EN** (L1–L4, T5–T7).
4. **Workbook structure** matches the legacy spec: single `sheet1`, merged `A1:V2` title, `A3:T3` single-currency total, header on **Excel row 4**, data from **Excel row 5**, **22 columns A–V**, **one row per detail** (W1–W7).
5. **Amount format** is currency-correct: VND no decimals, USD/KRW/other 2 decimals, numeric cells (T8–T9, W8).
6. **Derived strings** correct: Status (incl. blank when not dispatched), Fee Payer, Approval Status (never "Cancelled") — localized per locale (W9–W11, T10–T11).
7. **Regression**: Excel Download is **not** the uploaded file; upload flow, the uploaded-file endpoint, the Result-list toolbar export, and the Result-detail generic export all remain separate and unchanged (N1–N5).
8. **Response headers** correct on both endpoints (XLSX MIME, RFC 5987 filename, `nosniff`) (§6).

Any FAIL on items 1–7 is a **blocker**. Header/anomaly FAILs in §6 are blockers if they break the download or filename.

---

## 8. Known Risks / Items to Watch

| # | Risk / Watch item | Detail | Severity |
|---|---|---|---|
| R1 | **Reporting Account (col P) shows `CHART_ACCT_CD` code, not the chart-account name** | Legacy resolves the name via `wb_get_chart_acct_nm`; the refactor currently emits the code (consistent with the existing result-export, avoids an extra per-line lookup). Audit classified this ⚠️ minor. Confirm the value present is the code and decide if SIT requires the name. | Low |
| R2 | **Dates are English-style (`dd-MMM-yyyy` / `dd-MMM-yyyy HH:mm:ss`)** | Cols A (Transfer Date Time), D (Request Date/Time), S (Scheduled Date) are not localized by language (only Status/Fee/Approval are). This matches the task scope; flag only if SIT expects locale-specific date formats. No timezone shift is applied. | Low |
| R3 | **M-mode (manual `OVRS_PYMN_MSTR`) small divergences** | For M-mode, col B (Transaction Seq) is blank and col J (BIC) carries `BNFC_BANK_BIC` (vs `BNFC_BANK_CD` on E-mode), sourced from `findMmodeDetailRows`. M-mode is the edge case (most batches are E-mode); spot-check T12 only if M-mode data exists. | Low |
| R4 | **Status derivation depends on dispatch data** | Per-line Status (col C) comes from `PROC_STAT`/`RS_CD` via `resolveOverseas` (E) / `findMmodeDetailRows` (M). On a fresh DB or pre-dispatch batch the dispatch tables may be empty → Status blank. Use a genuinely dispatched batch (P6) for W9/T10. | Medium |
| R5 | **Total cell `A3:T3` formatting** | Total uses `DecimalFormat` (`#,##0` VND / `#,##0.00` other) with default grouping; the per-cell amount format is template-driven. Confirm the total reads naturally for VND and USD batches (T8/T9). | Low |
| R6 | **Live DB / data-dependent** | All content checks require representative tenant data (multi-line E-mode batch with approval, dispatched result, VND + USD currencies, optionally M-mode). Unit tests cover layout/locale/mapping; this smoke test validates real data end-to-end. | Medium |
| R7 | **Frontend feature docs slightly stale** | `wecms-web/docs/features/payment/overseas-payment/*` still describe the file-card as downloading the uploaded file. Documentation-only drift; does not affect runtime. Update opportunistically. | Info |

---

*Checklist only — no code changes, no destructive tests. Tick PASS/FAIL during the pre-SIT smoke session and attach the captured DevTools headers + a sample downloaded workbook per locale as evidence.*

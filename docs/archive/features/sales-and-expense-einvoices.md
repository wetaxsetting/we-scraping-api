# Add-on Service > Sales and Expense e-Invoices — Refactor Feature Spec

**Module:** Add-on Service (`ADNS`)
**Refactor slug (shared base):** `sales-and-expense-einvoices`
**Per-screen slug (split, for routes / packages / View Setting / audit):**
- `sales-einvoices` — outbound (issued to customers), `BusaDocType.INVOICE = 'I'`
- `expense-einvoices` — inbound (received from suppliers), `BusaDocType.BILL = 'B'`
**Spring REST base paths:** `/api/v1/addon/sales-einvoices/**` + `/api/v1/addon/expense-einvoices/**` (NXISAS update endpoint is shared at `/api/v1/addon/einvoices/scrape/**`).
**Next.js routes:** [/addon/sales-einvoices](../../../../wecms-web/app/(dashboard)/addon/sales-einvoices/page.tsx) + [/addon/expense-einvoices](../../../../wecms-web/app/(dashboard)/addon/expenses-einvoices/page.tsx).
**JEX counterpart (legacy):** `einv_0001_000` (Sales view shim) + `einv_0001_001` (shared list view & action) + `einv_0001_001_r002_excel` (Excel) + `nxisas_einvoice` / `nxisas_einvoice_c001` / `nxisas_einvoice_c002` (scrape) + `nxisas_einvhis` (call history) + the `nxisas_*` action chain (`getCertFormInfo`, `getModuleInfo`, `encrypt`, `decrypt`, `logging`).
**Status:** Design only. Two static-UI siblings (`addon-sales-einvoices`, `addon-expenses-einvoices`) already exist as feature specs ([sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md), [expenses-e-invoices.md](../../features/addon-service/expenses-e-invoices.md)). This document **supersedes** the architectural sections of those two for refactor naming consistency (kebab-case slug + NXISAS gateway reuse) and consolidates the scraping engine flow that both screens share.

JEX is **read-only reference**. The refactor is a clean Spring Boot + Next.js rewrite informed by JEX behaviour, not a port. The two e-Invoice screens are documented together because:

1. They share the same JSP view (`einv_0001_001_view.jsp`), the same TypeScript controller (`einv_0001_001.ts`), the same list action (`einv_0001_001_r001_act.jsp`), the same Excel exporter (`einv_0001_001_r002_excel_view.jsp`), and the same scraping pipeline (`nxisas_einvoice_view.jsp` + `nxisas_einvoice_c001_act.jsp` + the `nxisas_*` action chain).
2. They differ only by `BusaDocType` (`'I'` vs `'B'`) and `einvType` (`'1'` vs `'2'`), which select the IDO branch + screen-id + dashboard variant.

We therefore design **one backend slice** (controllers / services / repositories / DTOs / gateway) and **two thin frontend feature folders** that consume it. The split slugs (`sales-einvoices` / `expense-einvoices`) are used for the **per-screen** surfaces: the Next.js route, the frontend feature folder, the REST list/read/update/export URI, the i18n key prefix, the View Setting `WEB_SVC_ID`, the log/MDC tag, and `AUDIT_LOGS_INFM.WSVC_ID`. Two surfaces deliberately deviate from one-slug-per-screen — the **shared Java package** (`…domain.addon.einvoice`) and the **shared scrape REST base** (`/api/v1/addon/einvoices/scrape/**`) — because the backend is a single slice; both deviations are stated and justified in §3.1.

---

## 1. Feature Overview

### 1.1 Business purpose

Vietnamese tax law (Decree 123/2020 + Circular 78/2021) requires every B2B sale and every B2B purchase to be backed by a digitally-signed e-Invoice transmitted through the **General Department of Taxation (GDT)** portal `hoadondientu.gdt.gov.vn`. WeCMS is the operational hub where the finance team:

| Side | What it does |
|---|---|
| **Sales** (outbound, `DOC_TYPE='I'`) | Inspects every e-Invoice the company has issued to customers — date, serial number, buyer's MST (tax ID), GDT-side status, currency, gross / net / tax breakdown. Reconciles invoice rows against bank-side receipts. Exports / prints for monthly tax filings. |
| **Expenses** (inbound, `DOC_TYPE='B'`) | Inspects every e-Invoice the company has received from suppliers. Same columns; supplier-side MST status drives compliance because input-VAT credit is **denied** when the supplier's tax registration is suspended — a direct P&L hit. Same reconciliation + export workflow. |

The two screens sit under the **Add-on Service** module (legacy `gubun = ADNS`), reachable from the LNB. Sales also has a parallel entry point under the WABOOKS sales module (`focusMenu = sale_einv_001` from `einv_0001_001_view.jsp:25`); Expenses sits under purchase (`prch_einv_001`).

The page is the **read-side** of the e-Invoice domain. Issuing / replacing / adjusting / cancelling an invoice is handled in the legacy detail screens (`einv_0001_001_u001`, `einv_0002_001_u001`, …) and is **out of scope** for the refactor's listing page.

### 1.2 Who uses it

- Finance staff (`USER`) — list, filter, search, drill-down to detail, export, print.
- Finance manager (`ADMIN`) — same, plus admin-only View Setting & "Setting" icons (`einv_0001_001_view.jsp:373-374`, `:420-422`).

The two screens are **not** product-type-gated in the JEX live system (every `PRDT_TYPE` can see them today), but in the refactor we honour the global product-type gate: the e-Invoice add-on is enabled per tenant via `BUSI_INFM.PRDT_TYPE` plus an environment-setting flag. See OQ-1.

### 1.3 What the screen does (functional summary)

1. Loads a paginated list of `BUSA_DOC_MSTR` rows for the current tenant where `DOC_TYPE = 'I'` (Sales) or `DOC_TYPE = 'B'` (Expenses).
2. Filters: invoice-status dropdown, date range (DDMMYYYY), keyword search, currency filter, symbol type (C / K / POS — Sales only).
3. Renders each row with issue date, e-Invoice symbol/number, buyer/supplier company name + MST + GDT status, currency, total/tax/net amount, invoice status, payment status, due date, project, memo, approval seq, etc.
4. Multi-currency footer totals — one `<tr>` per currency (legacy assumes VND + USD).
5. Toolbar:
   - **Update** — opens the NXISAS scraping popup (`nxisas_einvoice_view.jsp`), which drives the Coocon engine to log into the GDT portal and pull fresh invoices.
   - **Excel** dropdown — two modes: list-only (current view) or with-details (joined with `BUSA_DOC_DETL` line items, fees, taxes).
   - **Print** — prints the table via the shared print helper.
   - **View Setting** (admin only) — column visibility + order (the popup is `comm_0108_001`).
   - **Setting** (admin only) — invoice-level configuration (out of scope here; lives under `comm_0192_007`).
6. Optional dashboard band (`PRDT_TYPE in ('C','D')`) showing ALL / OPEN / OVERDUE / PAID invoice counts and amounts. The dashboard band is **deferred** to a follow-up sprint (see OQ-2).

### 1.4 GDT (Vietnam tax portal) flow

JEX uses Webcash's **Coocon NXiSAS** scraping engine to log into the GDT portal on the user's behalf and pull invoices in batch:

```
User clicks "Update" in browser
        ↓
nxisas_einvoice popup opens (date range + 받지않은 invoice 체크박스)
        ↓
Browser ⇆ NXiSAS.exe (local WebSocket on wss://ibasews.coocon.co.kr:14102/)
        ↓
NXiSAS.exe ⇆ Coocon smart-gateway (sgw.webcashvietnam.com)
        ↓
Coocon gateway ⇆ GDT portal (hoadondientu.gdt.gov.vn)
        ↓
Invoices flow back: tax portal → Coocon → NXiSAS.exe → browser (encrypted)
        ↓
Browser POSTs encrypted batch to /jex/wabooks/einv/nxisas_einvoice_c001.act
        ↓
Server decrypts (com.coocon.securty.util.ISASSeedCBC), writes BUSA_DOC_MSTR rows
        ↓
nxisas_einvoice_c002 enriches: TRCO_INFM, TRCO_ADDR_INFM, TRCO_ACCT_INFO,
PGRO_BSAC_LNKN_INFM, BUSA_DOC_DETL, EINV_FEE_DETL, EINV_TAX_DETL
        ↓
nxisas_logging writes NXSIAS_MODULE_TRAN_LOG_HIS + EXT_TRAN_CALL_HIS for audit
```

The `.exe` is Windows-only and must be installed on each user's PC. There is no headless / server-side equivalent — the user's certificate and OTP / CAPTCHA must be solved at their own browser.

---

## 2. Current JEX Behavior (functional spec extracted from code)

### 2.1 View / shim files

#### 2.1.1 `einv_0001_000_view.jsp` — Sales shim

[einv_0001_000_view.jsp:25-28](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_000_view.jsp#L25-L28)

```jsp
request.setAttribute("einvType", "1");
<jsp:include page="/view/jex/wabooks/einv/einv_0001_001_view.jsp" />
```

The Sales URL `https://dev.wecms.vn/einv_0001_000.act` is a thin wrapper that pre-sets `einvType=1` and delegates to the shared view. The Expenses URL `einv_0001_001.act` reaches the shared view directly (defaulting `einvType=2`).

#### 2.1.2 `einv_0001_001_view.jsp` — shared list view

Key behaviours:

| Line | Behaviour |
|---|---|
| [:14-16](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L14-L16) | `einvType` resolved from request attr (set by `_000` shim) or query param; default `"2"` (Expenses). |
| [:18-23](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L18-L23) | `gubun = (PrdtType.U → "PRCH") : "SALE"`; `PrdtType.C / D → "PRCH"` (HAENGSUNG-style). Drives LNB highlight. |
| [:52-54](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L52-L54) | Loads `inc_wabooks_nxisas_v2.jsp` (which pulls `nxisas_v2.js`, `isasscaping.js`, `web_socket.js`, `swfobject.js`) — every page that uses NXISAS includes this. |
| [:329-357](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L329-L357) | LNB include branches on `einvType`: Sales → `SALE` / `sale_einv_001`, Expenses → `gubun` / `prch_einv_001`. |
| [:362-466](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L362-L466) | Toolbar: Dashboard (PrdtType C/D only), Setting + ViewSetting (admin only), Excel dropdown (`table1` for Sales E/U/R/L; `table2` for Expenses E/U/R/L; `table3` for C/D), Print, Update (`#btnUpBills`). |
| [:467-670](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L467-L670) | Dashboard band (ALL / OPEN / OVERDUE / PAID) — only for `PrdtType.C / D`. |
| [:673-780](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L673-L780) | Filter row: Apr-request button (Expenses only), date-range (with manual picker `MAN_DT_PICKER`), `INV_STTS` (7 states), `filter_env` (symbol type, dynamic from `einv_0001_001.ts:fn_symbolType`), `INV_CURRENCY` (5 states, Expenses only), keyword search. |
| [:808-911](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L808-L911) | Table shell (`#table-list-wrap`), pagination, append-popups for `RCV_YN` / `SND_YN` / `kSerial` / `busa_usag_cd`. |
| [:919-1221](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L919-L1221) | **Simple Credit/Debit Confirmation** layer popup (bank-matching workflow trigger). Out of scope for this refactor screen — covered by `bank-matching-receipt` feature. |

The 7 invoice-status options ([:472-485](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L472-L485)):

1. All Invoice Status
2. Original
3. Replacement
4. Adjustment
5. Replaced
6. Adjusted
7. Cancel

Date-range options ([:508-516](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L508-L516)): Last 30 / 90 / 180 days + Enter Manually.

#### 2.1.3 `einv_0001_001_u000_view.jsp` — alt view (Sales focus_menu override)

[einv_0001_001_u000_view.jsp:25](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_u000_view.jsp#L25) sets `focusMenu = "sale_einv_001"` (Sales side under the ADNS gubun) — a legacy variant that hits the Sales path through `gubun = ADNS`. Same downstream behaviour as `_view.jsp`. Refactor folds this into the Sales screen only.

#### 2.1.4 `einv_0001_001_r002_excel_view.jsp` — Excel export

[einv_0001_001_r002_excel_view.jsp:109-116](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_r002_excel_view.jsp#L109-L116) validates `DOC_TYPE`, `FROM_DT`, `TO_DT`, `BusaDocType`, dates.

[einv_0001_001_r002_excel_view.jsp:118-184](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_r002_excel_view.jsp#L118-L184) builds `IDODynamic` for `BUSA_DOC_MSTR_R024` / `_R025` — server-side rendering of an XLSX template (`/file/excel/VN/WABOOKS_Invoice_e_Invoice_SIMPLE_v2_<LANG>.xlsx` for Sales, `WABOOKS_Bill_e_Invoice_SIMPLE_v2_<LANG>.xlsx` for Expenses). Loads template via Apache POI, fills rows 6.. with 63 columns per invoice line ([:368-748](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_r002_excel_view.jsp#L368-L748)).

The 63 column ordering for Sales (rows starting at column 0):

| Col | Field | Source |
|---|---|---|
| 0 | EINV_INVOICESTATUS | `BUSA_DOC_MSTR.einv_invoicestatus` |
| 1 | EINV_TITLE | (constant from template) |
| 2 | EINV_NUMERICSYMBOL | `BUSA_DOC_MSTR.einv_numericsymbol` |
| 3 | EINV_INVOICESYMBOL | `BUSA_DOC_MSTR.einv_invoicesymbol` |
| 4 | HAND_DOC_ID | `BUSA_DOC_MSTR.hand_doc_id` |
| 5 | ISSU_DT | `BUSA_DOC_MSTR.issu_dt` (formatted yyyy-MM-dd) |
| 6 | ISSU_CMNM / TRCO_CMNM | Sales: issuer = our company; Expenses: trade company |
| 7 | ISSU_TAX_ID / TRCO_TAX_ID | same flip |
| 8 | ISSU/TRCO address concat | postal + detail + city + state |
| 9-11 | seller bank acc / name / phone | `EINV_SELLERBANKACC`, `EINV_SELLERBANKNAME`, `ISSU_RPRS_TLPH` |
| 12 | EINV_BUYERFULLNAME | |
| 13 | counterparty company name | TRCO_CMNM (Sales: buyer) or ISSU_CMNM (Expenses: ours) |
| 14-17 | counterparty address / tax / bank | mirror of 8-11 |
| 18-19 | EINV_LISTNUMBER, EINV_LISTDATE | |
| 20 | TRCO_TRSC_CCY_CD | |
| 21 | EINV_PAYMENTTYPE | |
| 22 | EINV_RATE | normalized via WABOOKSOSMCNumberUtil |
| 23-27 | item-line columns | DISP_ORD, ITEM_NM, ITEM_CD, EINV_PRODUCTNAME, ITEM_SPEC |
| 28-30 | ITEM_QUNT, ITEM_UNIT_AMT, discount | |
| 31-33 | tax-rate + supply amounts | EINV_TAXRATE, SPLY_AMT, discount SPLY_AMT |
| 34-36 | totals by tax rate | VAT rate %, total by VAT, tax amount by VAT |
| 37-38 | totals SPLY_AMT_SUM, TAX_AMT_SUM | |
| 39-43 | fees + discounts + totals | EINV_FEENAME, EINV_FEE, TOTL_DIS_AMT, TOTL_AMT, EINV_TOTALAMTINLETTER |
| 44 | EINV_MCCQT (tax authority confirmation code) | |
| 45-47 | seller/buyer sign date+time | |
| 48-53 | invoice nature + relation (original/replacement/adjustment) | parsed from EINV_ORIGINALINVOICE |
| 54-56 | USAG_NM, CONT, PRJT_NM | |
| 58 | PAY_DUE_CD | (currently commented) |
| 59 | DUE_DT | |
| 61 | APPR_SEQ_NO | |

The Expenses ordering is identical except col 6/7/13/15 swap (issuer vs trade company) ([:765-1330](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_r002_excel_view.jsp#L765-L1330) approx.).

Rounding rule for tax-by-VAT (col 36): `EINV_TYPE='1'` → CEILING, `'3'` → FLOOR, `'2'` → HALF_UP ([:570-575](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_r002_excel_view.jsp#L570-L575)).

### 2.2 Action files

#### 2.2.1 `einv_0001_001_r001_act.jsp` — list read

WSVC id: `einv_0001_001_r001`. Type: WSVC.

[einv_0001_001_r001_act.jsp:53-71](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp#L53-L71) validates `FROM_DT`, `TO_DT`, `DOC_TYPE`; date8 format; period.

[:75-167](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp#L75-L167) builds `IDODynamic` for the list query (`BUSA_DOC_MSTR_R022`):

```sql
-- conceptual SQL assembled in getDynamic0()
AND issu_dt BETWEEN :FROM_DT AND :TO_DT
AND doc_type = :DOC_TYPE                            -- 'I' (Sales) or 'B' (Expenses)
[AND einv_invoiceStatus = :STATUS]                  -- filter dropdown
AND biz_key = :BIZ_KEY                              -- tenant
AND hand_reg_yn = 'N'                               -- exclude hand-registered (refactor: keep this guard)
AND (doc_id, biz_key) IN (
    SELECT doc_id, biz_key FROM busa_doc_detl detl
    WHERE detl.biz_key = biz_key AND detl.doc_id = doc_id
)                                                   -- only invoices that have at least one line item
AND prov_stts NOT IN ('EE','VD')                    -- exclude EE (errored) + VD (voided)
[AND einv_testResults = :TYPE]                      -- 'all' or specific symbol code
[AND TRCO_TRSC_CCY_CD = :CCY]                       -- currency filter
[AND prov_stts IN ('OP','PP','OD')]                 -- PROV_STTS='OP' branch: OP+PP+OD
                                                    -- otherwise single value
-- SRCH_WD: merged via SearchHelper.getSearchColumnDynamic
-- ORDER BY: SortHelper.getSortDynamic("einv_0001_001_r001")
```

[:171-198](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp#L171-L198) — `getDynamic1` for `EXT_TRAN_CALL_HIS_R004` (the "last scrape end date" lookup, keyed by `SVC_ID = 'GDT_INQUIRY_SALES'` or `'GDT_INQUIRY_EXPENSES'`, `WEB_SVC_ID = 'nxisas_einvoice_c001'`, `RSLT_CD = '00000000'`).

[:209-234](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp#L209-L234) — execute IDOs:

- `BUSA_DOC_MSTR_R022` → list rows (paged)
- `BUSA_DOC_MSTR_R022_CNT` → total count + currency totals
- `BusaDocMstrTotInfo` (DAO) → multi-currency totals (`TOT_VND_AMT/CNT`, `TOT_USD_AMT/CNT`, `TOT_VND_SPLY_AMT`, `TOT_VND_TAX_AMT`, etc.)
- `EXT_TRAN_CALL_HIS_R004` → most recent successful GDT scrape end date
- `GdtCertInfoDAO.findByDynamic` → resolves `GDT_CERT_SEQ` (the user's saved GDT certificate seq for this `BIZ_KEY`)

[:286-329](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp#L286-L329) — copies row fields, normalises `einv_invoiceStatus` / `einv_numericSymbol` / `einv_invoiceSymbol` / `einv_invoicestatus_bef` for the wire.

WSVC output (truncated, full list in `WSVC.einv_0001_001_r001.xml`):

```
END_DATE, TOT_CNT, GDT_CERT_SEQ
TOT_VND_AMT, TOT_VND_CNT, TOT_USD_AMT, TOT_USD_CNT
TOT_VND_SPLY_AMT, TOT_VND_TAX_AMT, TOT_USD_SPLY_AMT, TOT_USD_TAX_AMT
TOT_INV_CNT, TOT_OP_INV_CNT, TOT_OD_INV_CNT, TOT_PA_INV_CNT
TOT_INV_LOC_CCY_CD_AMT, TOT_INV_USD_AMT
TOT_OP_INV_LOC_CCY_CD_AMT, TOT_OP_INV_USD_AMT
TOT_OD_INV_LOC_CCY_CD_AMT, TOT_OD_INV_USD_AMT
TOT_PA_INV_LOC_CCY_CD_AMT, TOT_PA_INV_USD_AMT
REC: {
    DOC_ID, ISSU_DT, TRCO_CMNM, TRCO_CMENM, TRCO_TRSC_CCY_CD, TOTL_AMT,
    BUSA_USAG_CD, USAG_NM, PROV_STTS, PAY_DUE_CD, DUE_DT, PRJT_NO, REF_NO,
    REG_ID, REG_DTM, EML_TRF_DTM, RCV_YN, NOTE, COLOR, MEMO, TRCO_KEY,
    RCV_PAY_WAY_CD, DOC_TITLE, REG_NM, MOD_DTM, MOD_NM,
    KHR_SPLY_AMT, KHR_TAX_AMT, TOT_KHR_AMT, BAL,
    APLY_EXCH_RATE, INV_TYPE, PRJT_NM, APPR_SEQ_NO, CONT,
    einv_invoiceStatus, einv_numericSymbol, einv_invoiceSymbol,
    einv_invoicestatus_bef,
    HAND_DOC_ID, TRCO_TAX_ID, DOC_TYPE, COMPANYSTATUS,
    SPLY_AMT_SUM, TAX_AMT_SUM, TOTL_DIS_AMT, RCV_SND_YN, READ_YN,
    OVER_DUE_DAYS, DISP_DOC_ID
}
```

#### 2.2.2 `einv_0001_001_u001_act.jsp` — currency code update

WSVC id: `einv_0001_001_u001`. Updates `BUSA_DOC_MSTR.TRCO_TRSC_CCY_CD` for a given `DOC_ID`.

[einv_0001_001_u001_act.jsp:28-33](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_u001_act.jsp#L28-L33) validates `DOC_ID`, `TRCO_TRSC_CCY_CD`, CcyCd format.

[:74-94](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_u001_act.jsp#L74-L94) loads via `ValidationHelper.validateBusaDocMstr`, sets `trscCcyCd`, persists via `BusaDocMstrDAO.update`. **Refactor scope:** Yes — small inline edit needed by the row's currency selector (`kSerial` dropdown in [einv_0001_001.ts:457-499](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts#L457-L499)).

#### 2.2.3 `nxisas_einvoice_c001_act.jsp` — scrape ingest

WSVC id: `nxisas_einvoice_c001`. Type: WSVC. Receives the encrypted batch from the browser AFTER the NXiSAS engine finishes the GDT scrape.

[nxisas_einvoice_c001_act.jsp:32-46](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c001_act.jsp#L32-L46) validates `EINV_TYPE`, `NXISAS_UID`, `NXISAS_ACTION`, `FROM_DT`, `TO_DT`, `REC_IN[].ENC_STR`.

[:100-323](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c001_act.jsp#L100-L323) — for each encrypted record:

1. Decrypts via `ISASSeedCBC.decrypt(encResult, Uid, Action)` (Coocon SEED-CBC).
2. Parses the decrypted JSON to extract `invoiceList`.
3. Filters by `checkResult` — Sales accepts `"Đã cấp mã hóa đơn"` (code issued) and `"Hóa đơn không đủ điều kiện cấp mã"` (ineligible for code) and `"Tổng cục Thuế đã nhận"` (GDT received). Expenses ALSO accepts `"Cục thuế đã nhận không mã"`, `"Cục Thuế đã nhận hóa đơn có mã khởi tạo từ máy tính tiền"`, `"Cục Thuế đã nhận"`.
4. Composes `DOC_ID`:
   - Sales (`EINV_TYPE='1'`): `numericSymbol + invoiceSymbol + "-" + invoiceNo`
   - Expenses (`EINV_TYPE='2'`): same + `"-" + sellerTaxCode` (so multi-supplier uniqueness holds)
5. Builds `BUSA_DOC_MSTR_C002` IDO row with: `BIZ_KEY`, `USER_ID`, `DOC_ID`, `DISP_DOC_ID`, `ISSU_DT` (invoiceDate), `TOTL_AMT` (totalAmt), `SPLY_AMT_SUM` (totalAmtWithoutTax), `TAX_AMT_SUM` (totalTaxAmt), `REG_*` / `MOD_*` audit cols, `HAND_DOC_ID` (invoiceNo), `EINV_INVOICEINFO`, `TOTL_DIS_AMT` (totalDiscount), `EINV_INVOICESTATUS`, `EINV_CHECKRESULTS`, `EINV_NUMERICSYMBOL`, `EINV_INVOICESYMBOL`, `EINV_INVOICESTATUS_BEF`, `EINV_TESTRESULTS`, `ISSU_TAX_ID`, `DOC_TYPE`.
6. `PROV_STTS` rule: if `invoiceStatus IN ('Hóa đơn đã bị thay thế', 'Hóa đơn đã bị hủy')` (replaced or cancelled) → `'VD'`; else `'OP'`.
7. Batch-inserts via `idoCon.executeBatch(ido_list)`.
8. Writes `ExtTranCallHis` row with `SVC_ID = 'GDT_INQUIRY_SALES'` or `'GDT_INQUIRY_EXPENSES'`, `WEB_SVC_ID = 'nxisas_einvoice_c001'`, `RSLT_CD = ErrorCode`, `ERR_CONT = ErrorMessage`, `START_DATE / END_DATE` from request.

Output: `MOD_DTM` (timestamp the scrape was committed), `NXISAS_ERR_CODE`, `NXISAS_ERR_CONT`, `REC[]` containing per-invoice metadata (`DOC_ID`, `HAND_DOC_ID`, `einv_invoiceSymbol`, `einv_testResults`, `einv_sellerTaxCode`, `einv_sellerName`).

#### 2.2.4 `nxisas_einvoice_c002_act.jsp` — enrichment

WSVC id: `nxisas_einvoice_c002`. Type: WSVC. Called by the browser **after** `c001` completes successfully, per each `REC` item. Enriches the just-inserted `BUSA_DOC_MSTR` row with:

- `TRCO_INFM` (trade company master) — looked up by buyer/seller tax-code; created if missing.
- `TRCO_ADDR_INFM` — counterparty address.
- `TRCO_ACCT_INFO` — counterparty bank account.
- `PGRO_BSAC_LNKN_INFM` — `GRP_CD` link (Sales = `'B'` buyer, Expenses = `'S'` seller).
- `TRCO_RSPB_INFM` — PIC name.
- `BUSA_DOC_DETL` — invoice line items (one per `productList[]` entry from the decrypted JSON).
- `EINV_FEE_DETL` — fees.
- `EINV_TAX_DETL` — tax breakdown by rate.

Validation: `NXISAS_UID`, `NXISAS_ACTION`, `MOD_DTM` ([nxisas_einvoice_c002_act.jsp:81-85](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c002_act.jsp#L81-L85)). Also normalises account numbers (strips VND/VNĐ labels, parens, dashes) and resolves company nickname via `SysTrcoInfmDisNmDAO.getNormalizeDisplayNameBySystem`.

### 2.3 NXiSAS scraping handshake (browser ⇆ Coocon ⇆ GDT)

#### 2.3.1 Engine setup (`nxisas_v2.js` + `isasscaping.js`)

[isasscaping.js:4](../../../../WECMS%20DEV/WECMS_STATIC/web/nxisas/js/isasscaping.js#L4) — Coocon license key (hardcoded):

```js
var CooconLicense = { "MstCompNo": "5568800909", "SubCompNo": "5568800909", "ProductNo": "00000014" };
```

[isasscaping.js:9-30](../../../../WECMS%20DEV/WECMS_STATIC/web/nxisas/js/isasscaping.js#L9-L30) — `iBASENXModule` constructor:

```js
this.CurrentVersion = "2023.4.7.0";        // expected installed module version
this.WebProtocol  = "https";
this.HostName     = "ibasews.coocon.co.kr";
this.LocalPort    = "14101";               // HTTPS to local .exe
this.ServiceURL   = "https://ibasews.coocon.co.kr:14101/";
this.SocketProtocol = "wss";
this.SocketHostName = "ibasews.coocon.co.kr";
this.SocketPort    = "14102";
this.SocketURL    = "wss://ibasews.coocon.co.kr:14102/";
```

> **CRITICAL:** the `HostName` is `ibasews.coocon.co.kr` but the **port** is local (`14101` / `14102`). The DNS resolves to localhost (Coocon ships a hosts-file override or the .exe binds those names locally) so the browser connects to **the user's own PC**, not to Coocon's server. The `.exe` is the bridge that talks to Coocon's real gateway over the public internet.

[isasscaping.js:95-160](../../../../WECMS%20DEV/WECMS_STATIC/web/nxisas/js/isasscaping.js#L95-L160) — `connect()` opens the WebSocket to the local `.exe`. Callback handlers: `OPEN`, `RUN`, `CANCEL`, `CANCELALL`, `CERTLIST`, `ONSTATUSCHANGED`, `ONCOMPLETED`.

[isasscaping.js:225-300](../../../../WECMS%20DEV/WECMS_STATIC/web/nxisas/js/isasscaping.js#L225-L300) — `ONCOMPLETED` collects results per thread, fires `ResultCallback[idx](../../features/resultArray, idx)` once `_super.resultArray.length === _super.totalInput`.

#### 2.3.2 Wrapper UI (`nxisas_v2.js`)

The OSMC-aware wrapper. Drives the popup dialogs for OTP / CAPTCHA / QNA, manages the "save password" prompt, and posts the encrypted batch to the server:

- Modal popups: `.layerCont.otp`, `.layerCont.captcha`, `.layerCont.security-question`, `.layerCont.account-management` (password entry), `.layerCont.transfer_confirmation` (transfer-only).
- On `LOGIN_CAN` (cancel) → server-side `WBC1152` message; for transfers → `WBC1140`.
- Posts encrypted result via `nxisas_encrypt` / `nxisas_decrypt` server endpoints (server has the Coocon SEED-CBC key).

#### 2.3.3 Server-side support actions

| Action | WSVC id | Purpose |
|---|---|---|
| [nxisas_getCertFormInfo_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_getCertFormInfo_act.jsp) | `nxisas_getCertFormInfo` | Reads `BANK_INFM_DETL` (R006 + R005) — returns per-bank field metadata (which input boxes to show: ID / password / cert PIN / etc.). Refactor: still needed, but only for **GDT** in the e-Invoice flow; banking flows live in `domain/banking/nxisas`. |
| [nxisas_getModuleInfo_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_getModuleInfo_act.jsp) | `nxisas_getModuleInfo` | Calls `WABOOKSOSMCAPIUtil.callApi(API_ID)` to fetch `MODULE` + `CLASS` for a given API id (e.g. `GDT_INQUIRY_SALES` → `(module="VIETNAM_GDT", class="...")`). Refactor: replace with a config table lookup (no live API call). |
| [nxisas_encrypt_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_encrypt_act.jsp) | `nxisas_encrypt` | Re-encrypts via `ISASSeedCBC.encrypt(result, Uid, Action)`. Used when the browser needs to forward a previously-decrypted blob back. |
| [nxisas_decrypt_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_decrypt_act.jsp) | `nxisas_decrypt` | `ISASSeedCBC.decrypt(encResult, Uid, Action)` — server-side decryption of the engine's response. Logs every step. |
| [nxisas_logging_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_logging_act.jsp) | `nxisas_logging` | Walks the result tree, derives `_svcId` from `job`: `ACCT_LIST` → `BANKING_CREDENTIAL`; `INVOICE_LIST` → `E-INVOICE_LIST_INQUIRY`; `INVOICE_DETAIL` + `invoiceType=1` → `E-INVOICE_SALES_INQUIRY`; `invoiceType=2` → `E-INVOICE_EXPENSE_INQUIRY`; `BALANCE` → `REAL_TIME_ONLINE_INQUIRY`; `CARD_USING` → `CARD_INQUIRY`. Writes `EXT_TRAN_CALL_HIS` + `NXSIAS_MODULE_TRAN_LOG_HIS` rows. |
| [nxisas_einvhis_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvhis_r001_act.jsp) | `nxisas_einvhis_r001` | Reads `EXT_TRAN_CALL_HIS` filtered to `SVC_ID LIKE 'GDT_INQUIRY%'` for the "Latest Inquiry History" popup. |
| [nxisas_einvhis_dt_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvhis_dt_r001_act.jsp) | `nxisas_einvhis_dt_r001` | Per-row detail of an `EXT_TRAN_CALL_HIS` entry. |

### 2.4 Database tables touched

Cross-checked against the **PROD Active Table List** in `wecms-api/CLAUDE.md`. ✅ = on the list (refactor scope); ❌ = out of scope.

| Table | Read | Write (scrape) | PROD list | Notes |
|---|---|---|---|---|
| `BUSA_DOC_MSTR` | ✅ | ✅ (C002 batch) | ✅ | 100-col master. List query joins with subtable for the "has line items" guard. Schema already in V15 + V23. |
| `BUSA_DOC_MSTR_HIS` | — | ✅ (audit triggers) | ✅ | History table; refactor adopts service-layer audit instead of triggers. |
| `BUSA_DOC_DETL` | join | ✅ (c002 enrich) | ✅ | Invoice line items. |
| `BUSA_DOC_DETL_HIS` | — | ✅ (audit) | ✅ | History. |
| `BUSA_DOC_RCV_PAY_HIS` | — | — | ✅ | Receipt/payment history (used by bank-matching feature; out of scope here). |
| `EINV_FEE_DETL` | join (excel detail) | ✅ (c002) | ✅ | Per-invoice fee breakdown. |
| `EINV_FEE_DETL_HIS` | — | ✅ | ✅ | History. |
| `EINV_TAX_DETL` | join (excel detail) | ✅ (c002) | ✅ | Per-invoice VAT breakdown. |
| `EINV_TAX_DETL_HIS` | — | ✅ | ✅ | History. |
| `EXT_TRAN_CALL_HIS` | ✅ (END_DATE lookup + einvhis popup) | ✅ (c001 + logging) | ✅ | Cross-system call audit. |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | — | ✅ (nxisas_logging) | ✅ | Per-job NXISAS audit. Already used by the banking refactor. |
| `GDT_CERT_INFO` | ✅ | (out of scope) | ✅ | GDT certificate metadata per tenant. |
| `TRCO_INFM` | join | ✅ (c002) | ✅ | Trade-company master. Shared with `contacts`. |
| `TRCO_ADDR_INFM` | — | ✅ (c002) | ✅ | Counterparty address. |
| `TRCO_ACCT_INFO` | — | ✅ (c002) | ✅ | Counterparty bank account. |
| `TRCO_RSPB_INFM` | — | ✅ (c002) | ❌ (not on list) | Refactor decision: defer — `contacts` feature has not yet wired this. |
| `PGRO_BSAC_LNKN_INFM` | — | ✅ (c002) | ✅ | Group link (`GRP_CD = 'B'` buyer / `'S'` seller). |
| `BANK_INFM_DETL` | ✅ (getCertFormInfo) | — | ✅ | NXISAS cert form fields (per bank/portal). |
| `AUDIT_LOGS_INFM` | — | ✅ (refactor only) | ✅ | Standard refactor audit. `WSVC_ID = "sales-einvoices"` or `"expense-einvoices"`. |
| `SYS_SCR_OPTION_WECMS` | ✅ (view setting + export gate) | ✅ (seed) | ✅ | Required for column visibility + Excel export. |
| `USER_SCR_OPTION_WECMS` | ✅ | ✅ | ✅ | Per-user overrides. |
| `SYS_USAG_SET_INFM`, `USAG_INFM`, `USAG_INFM_LANG_DETL` | ✅ (USAG_NM lookup) | — | ✅ | Usage code resolution. |
| `BUSI_ENV_SET_INFM` | ✅ (env_setting_007) | — | ✅ | Tenant settings (e.g. show/hide buyer status). |

Tables that legacy code touches but **PROD does not populate** (per the PROD Active Table List rule in `wecms-api/CLAUDE.md`): none of the einv tables are excluded — every table the screen reads/writes is on the active list. `TRCO_RSPB_INFM` is the only borderline case (off the list); the refactor defers it.

---

## 3. Target Architecture (Spring Boot + Next.js)

### 3.1 Naming — slug decision, deviations flagged

**The original task slug rule.** The workspace identifier-naming rule (root [CLAUDE.md](../../../../CLAUDE.md) §"Cross-Tree Rules → Refactor identifier naming") instructed: *use the slug `sales-and-expense-einvoices`, OR split into `sales-einvoices` / `expense-einvoices` if functional analysis shows two distinct screens, for: Next.js routes, Spring REST URIs, Java packages/classes/DTOs, frontend feature folder, i18n key prefix, log tags, View Setting screenId, and `AUDIT_LOGS_INFM.WSVC_ID`.*

**Decision: split into `sales-einvoices` + `expense-einvoices`, NOT the combined `sales-and-expense-einvoices`.** Justification (the functional-analysis test the rule asks for):

1. **Two distinct screens in the live system.** Sales and Expenses are two separate LNB menu items reachable at two separate URLs (`einv_0001_000.act` / `sale_einv_001` vs `einv_0001_001.act?einvType=2` / `prch_einv_001` — see [einv_0001_001_view.jsp:329-357](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L329-L357)). A user navigates to one or the other; there is no combined "Sales and Expense" page in the product.
2. **Two distinct compliance domains.** Sales = output VAT; Expenses = input-VAT credit. The counterparty column flips Buyer↔Supplier, and the scrape `checkResult` acceptance set differs (Expenses accepts three extra GDT statuses — [nxisas_einvoice_c001_act.jsp:207-209](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c001_act.jsp#L207-L209)).
3. **The existing refactor static-UI specs already split** — [addon/sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md) and [addon/expenses-e-invoices.md](../../features/addon-service/expenses-e-invoices.md) ship as two screens with `screenId`s `addon-sales-einvoices` / `addon-expenses-einvoices`. Keeping the split preserves traceability with that existing work.

The combined slug `sales-and-expense-einvoices` is retained **only** as the shared kebab identifier for these two design docs (this feature doc + the migration doc), so the doc pair is greppable as one unit. It is **never** used as a route, package, URI, View Setting id, or audit value.

| Surface | Sales | Expenses |
|---|---|---|
| Next.js route | `/addon/sales-einvoices` | `/addon/expense-einvoices` |
| Frontend feature folder | shared `wecms-web/src/features/einvoices/` (see §3.5 + deviation D-3 below) | same |
| Java package | shared `com.kosign.wecms.domain.addon.einvoice` (see deviation D-1 below) | same |
| Java controller class | `SalesEinvoiceController` | `ExpenseEinvoiceController` |
| REST list / read / update / export | `/api/v1/addon/sales-einvoices/**` | `/api/v1/addon/expense-einvoices/**` |
| REST scrape (shared) | `/api/v1/addon/einvoices/scrape/**` (see deviation D-2 below) | same |
| View Setting `WEB_SVC_ID` | `sales-einvoices` | `expense-einvoices` |
| Audit `AUDIT_LOGS_INFM.WSVC_ID` | `sales-einvoices` | `expense-einvoices` |
| Log / MDC tag | `sales-einvoices` | `expense-einvoices` |
| i18n key prefix | `sales_einvoices.*` | `expense_einvoices.*` |

Legacy WSVC ids (`einv_0001_001_r001`, `nxisas_einvoice_c001`, …) appear only as JEX file:line citations in migration prose and Javadoc, never as new identifiers.

**Deviations from one-slug-per-screen (explicitly flagged per the rule's "flag any deviation explicitly" clause):**

| ID | Surface | Deviation | Why |
|---|---|---|---|
| **D-1** | Java package | shared `com.kosign.wecms.domain.addon.einvoice` instead of two per-slug packages | Java package names cannot contain hyphens, and the two screens share 100% of their backend logic (one query parameterised by `DOC_TYPE`, one scrape pipeline). A single package with two controllers (`SalesEinvoiceController` / `ExpenseEinvoiceController`) is the minimal, non-duplicated expression. Per-screen distinction is carried in the **class name + REST URI + audit slug**, which is where it is observable. Same pattern the rule already tolerates for shared common modules. |
| **D-2** | REST scrape base | shared `/api/v1/addon/einvoices/scrape/**` (segment `einvoices`, neither slug) instead of duplicating ingest/enrich/cert-form/module-info/history under both slugs | The NXiSAS scrape pipeline is genuinely shared (one Coocon engine session pulls both invoice types; `einv_type` in the request body discriminates — [nxisas_einvoice_c001_act.jsp:154-160](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c001_act.jsp#L154-L160)). Duplicating five endpoints under each slug would be redundant. The per-screen distinction is carried in the `einv_type` request field and the audit `WSVC_ID` written by the ingest service. |
| **D-3** | Frontend feature folder | shared `features/einvoices/` instead of two per-slug folders | One shared `<EInvoicesPage>` shell with two 1-line route wrappers (`type="SALES"` / `type="EXPENSE"`). Forking the shell is forbidden by the wecms-web shared-shell rule; the slug distinction lives in the **route folder** (`app/(dashboard)/addon/sales-einvoices/` vs `…/expense-einvoices/`), which IS per-slug. |

These three deviations affect **internal/shared** surfaces only. Every **externally observable** per-screen surface (route, REST resource URI, View Setting id, audit `WSVC_ID`, i18n prefix, log tag) uses the split slug exactly as the rule requires.

### 3.2 Java package layout

```
com.kosign.wecms.domain.addon.einvoice/
├── controller/
│   ├── SalesEinvoiceController.java     — /api/v1/addon/sales-einvoices
│   ├── ExpenseEinvoiceController.java   — /api/v1/addon/expense-einvoices
│   └── EinvoiceScrapeController.java    — /api/v1/addon/einvoices/scrape (shared)
├── service/
│   ├── EinvoiceListService.java         — list / search / filter / paginate
│   ├── EinvoiceTotalsService.java       — multi-currency totals + status buckets
│   ├── EinvoiceCurrencyUpdateService.java — TRCO_TRSC_CCY_CD inline edit
│   ├── EinvoiceScrapeIngestService.java — decrypts c001 batch + writes BUSA_DOC_MSTR
│   ├── EinvoiceScrapeEnrichService.java — c002 enrichment (TRCO_*, BUSA_DOC_DETL, EINV_*_DETL)
│   ├── EinvoiceExcelExportProvider.java — implements ExportRowProvider
│   ├── EinvoiceSortField.java           — common-table-sort whitelist enum
│   └── EinvoiceFilterValidator.java     — date range + invoice status + currency
├── gateway/
│   ├── GdtScrapeGatewayClient.java      — thin ADAPTER over the REUSED domain/banking/nxisas
│   │                                       NxisasGatewayClient; owns NO gateway transport itself.
│   │                                       Calls NxisasGatewayClient.invoiceList(...) / .invoiceDetail(...).
│   ├── IsasSeedCbcDecoder.java          — wraps com.coocon.securty.util.ISASSeedCBC (see §8.2)
│   ├── GdtCertFormInfoService.java      — replaces nxisas_getCertFormInfo
│   └── GdtModuleInfoService.java        — replaces nxisas_getModuleInfo
│   # NOTE — the gateway job vocabulary is REUSED/EXTENDED in domain/banking/nxisas, NOT duplicated here:
│   #   - NxisasJob gains INVOICE_LIST + INVOICE_DETAIL (BE-1)
│   #   - new DTOs NxisasInvoiceListJobInput/Result + NxisasInvoiceDetailJobInput/Result
│   #     land under domain/banking/nxisas/gateway/dto/ (BE-2)
│   #   - NxisasGatewayClient gains invoiceList(...) / invoiceDetail(...) (BE-3)
│   # This addon package adds NO second gateway client and NO parallel job enum (see §3.3).
├── repository/
│   ├── BusaDocMstrRepository.java
│   ├── BusaDocDetlRepository.java
│   ├── EinvFeeDetlRepository.java
│   ├── EinvTaxDetlRepository.java
│   ├── GdtCertInfoRepository.java
│   └── ExtTranCallHisRepository.java
├── entity/
│   ├── BusaDocMstr.java
│   ├── BusaDocDetl.java
│   ├── EinvFeeDetl.java
│   ├── EinvTaxDetl.java
│   ├── GdtCertInfo.java
│   └── ExtTranCallHis.java
└── dto/
    ├── EinvoiceListRequest.java         — query params
    ├── EinvoiceListResponse.java        — { records, totals, end_date, gdt_cert_seq }
    ├── EinvoiceRow.java
    ├── EinvoiceTotals.java
    ├── EinvoiceCurrencyUpdateRequest.java
    ├── EinvoiceScrapeIngestRequest.java
    ├── EinvoiceScrapeIngestResponse.java
    ├── EinvoiceScrapeEnrichRequest.java
    └── EinvoiceExportRequest.java
```

### 3.3 NXiSAS gateway — reuse, do not duplicate

The wecms-api refactor already has a fully-fledged NXiSAS gateway under [wecms-api/src/main/java/com/kosign/wecms/domain/banking/nxisas/](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/). It ships:

- [`NxisasGatewayProperties`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasGatewayProperties.java) — bound to `wecms.nxisas.*` config.
- [`NxisasGatewayClient`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasGatewayClient.java) — Spring `RestClient` over the Coocon smart-gateway envelope (Common + Thread + Input/Output). Supports `LOGIN`, `OTP`, `CAPTCHA`, `QNA`, `ACCT_LIST`, `CARD_INFO`, `CARD_USING`, `TRANSACTION`, `BALANCE`. Carries `GatewayContinuation` (SAS_SessionID + Gw_sys_no + Lbs_no + Ss_sys_no + Thread_no) between calls.
- [`NxisasSessionRegistry`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/session/NxisasSessionRegistry.java) — in-memory holder of in-flight scrape sessions with TTL eviction.
- [`NxisasAuditLogger`](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/audit/NxisasAuditLogger.java) — `REQUIRES_NEW` audit writer for `NXSIAS_MODULE_TRAN_LOG_HIS`.
- Gateway DTOs under `gateway/dto/` (NxisasApiIn / Out / Common / Thread / Result / Output / etc.).

**The e-Invoice feature MUST reuse this gateway**. It adds:

1. **Two new NXisasJob enum values:** `INVOICE_LIST` (per `nxisas_logging:55` job-name detection) and `INVOICE_DETAIL` (per `:57`, with `Input.invoiceType` discriminating sales vs expense — see `nxisas_logging:81-85`). These are added to `NxisasJob` in the same MR.
2. **Two new gateway-input DTOs:** `NxisasInvoiceListJobInput` (no `invoiceType` — pure list query) and `NxisasInvoiceDetailJobInput` (carries `invoiceType: "1" | "2"`).
3. **Two new gateway-result DTOs:** `NxisasInvoiceListJobResult` (the encrypted `Result` blob the engine returns — the server-side decryption is the inverse of `ISASSeedCBC.encrypt`).
4. **Two new methods on `NxisasGatewayClient`:** `invoiceList(...)` + `invoiceDetail(...)`.

The e-Invoice feature does **NOT** introduce a second `NxisasGatewayClient`. It does **NOT** open its own WebSocket. The Coocon `.exe` stays on the user's PC; the Spring server receives the **encrypted batch** the browser posts after the scrape, decrypts it with the same `ISASSeedCBC` key already used in `nxisas_encrypt_act.jsp` / `nxisas_decrypt_act.jsp`, and writes the resulting rows. See §4.4.

### 3.4 Wire format

Global Jackson SNAKE_CASE strategy applies. Java fields stay camelCase; wire is lowercase snake_case. Per the CTO directive (memory `project_api_naming_lowercase.md`), legacy UPPER_SNAKE JSON keys are **NOT** carried into this refactor. The wire field names below are the lowercase snake_case rendering of the legacy `BUSA_DOC_MSTR` columns (e.g. `doc_id`, `issu_dt`, `trco_cmnm`, `einv_invoicestatus`, `einv_numericsymbol`, `einv_invoicesymbol`).

### 3.5 Frontend feature folder (one shell, two routes)

Per the existing addon docs ([sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md) §"Current status"), the static UI already uses one shared shell (`<EInvoicesPage>`) and two thin wrappers (`<SalesEInvoicesClient type="SALES">` and `<ExpensesEInvoicesClient type="EXPENSE">`). The refactor preserves this layout:

```
wecms-web/src/features/einvoices/                  ← shared shell
├── components/
│   ├── EInvoicesPage.tsx                          ← layout, table, filters, footer
│   ├── EInvoiceToolbar.tsx
│   ├── EInvoiceTable.tsx
│   ├── EInvoiceFooter.tsx                         ← multi-currency totals
│   └── nxisas/
│       ├── NxisasScrapeDialog.tsx                 ← the "Update" popup
│       ├── useNxisasBridge.ts                     ← local WS bridge to NXiSAS.exe
│       └── NxisasOtpDialog.tsx, NxisasCaptchaDialog.tsx, NxisasQnaDialog.tsx
├── hooks/
│   ├── useEInvoiceList.ts                         ← React Query against /api/v1/addon/{slug}-einvoices
│   ├── useScrapeBatch.ts                          ← orchestrates the .exe ⇆ server flow
│   └── useEInvoiceTotals.ts
├── lib/
│   ├── api.ts                                     ← fetchEinvoiceList / fetchTotals / postScrape
│   └── nxisasBridge.ts                            ← WebSocket adapter (no UI)
└── i18n/                                          ← TS-side namespace registry
wecms-web/src/app/(dashboard)/addon/
├── sales-einvoices/page.tsx                       ← imports <EInvoicesPage type="SALES" translationPrefix="sales_einvoices" />
└── expense-einvoices/page.tsx                     ← imports <EInvoicesPage type="EXPENSE" translationPrefix="expense_einvoices" />
```

The View Setting popup is **not rendered** on either screen (legacy parity per [sales-e-invoices.md §Scope](../../features/addon-service/sales-e-invoices.md) — the legacy toolbar has Print / Excel / Update only). The View Setting data is still consumed internally by the Excel export pipeline (column intersection).

### 3.6 How the browser launches NXiSAS.exe

The browser does NOT spawn the `.exe` directly. Instead:

1. The user installs Coocon's NXiSAS once (admin-distributed MSI).
2. On install, the `.exe` registers as a Windows service that:
   - Adds `ibasews.coocon.co.kr` to the local hosts file pointing at `127.0.0.1`.
   - Installs a self-signed cert for `ibasews.coocon.co.kr` into the Windows trust store.
   - Listens on `127.0.0.1:14101` (HTTPS, status pings) and `127.0.0.1:14102` (WSS, message channel).
3. The Next.js page imports `lib/nxisasBridge.ts` which on demand opens `new WebSocket("wss://ibasews.coocon.co.kr:14102/")`.
4. If the connection fails (engine not installed or not running), the bridge surfaces a recovery banner with the install link.
5. After scrape completion, the bridge calls `useScrapeBatch().postBatch(encryptedRecords)` which POSTs to `/api/v1/addon/einvoices/scrape/ingest`.

> The Spring server itself **NEVER** opens a WebSocket to the `.exe`. The `.exe` runs on each user's machine, and only the encrypted batch crosses the server boundary.

---

## 4. API Contract

All endpoints under `/api/v1/`. Wire is lowercase snake_case. Standard `{ status, data }` envelope. Errors carry `timestamp`, `path`, `trace_id`. JWT-protected (everything except `/api/v1/auth/*` requires auth per `SecurityConfig`).

### 4.1 GET `/api/v1/addon/sales-einvoices` — list Sales

Replaces JEX `einv_0001_001_r001` with `DOC_TYPE='I'`.

**Query params:**

| Name | Type | Required | Notes |
|---|---|---|---|
| `from_dt` | string `yyyyMMdd` | ✅ | matches legacy `FROM_DT` |
| `to_dt` | string `yyyyMMdd` | ✅ | matches legacy `TO_DT` |
| `invoice_status` | string | ❌ | matches legacy `einv_invoiceStatus` ("Original" / "Replacement" / …; blank = all). Backend maps localized labels via a coded enum (see OQ-3). |
| `test_results` | string | ❌ | matches legacy `einv_testResults`: `all` / `''` (C-Symbol) / `1` (K-Symbol) / `2` (POS). Default `all`. |
| `currency` | string (`VND` / `USD` / `998` / `999`) | ❌ | matches legacy `TRCO_TRSC_CCY_CD`. |
| `prov_stts` | string | ❌ | dashboard band: `OP` (open+partial+overdue) / `OD` / `PA` / specific code. |
| `search` | string | ❌ | matches legacy `SRCH_WD`. Matched against issu_dt, doc_id, trco_cmnm, cont, plus numeric match on totl_amt / tax_amt_sum / sply_amt_sum. |
| `sort_field` | string | ❌ | View Setting `FIELD_ID` (e.g. `ISSU_DT`, `EINV_INVOICESYMBOL`, `TRCO_CMNM`, `TOTL_AMT`). |
| `sort_direction` | `ASC` / `DESC` | ❌ | default `DESC` on `issu_dt`. |
| `page_no` | integer | ❌ | 1-indexed. |
| `page_per_cnt` | integer | ❌ | default 15; allowed values 15/20/50/100. |

**Response — `EinvoiceListResponse`:**

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "end_date": "20260524",
    "gdt_cert_seq": "1",
    "totals": {
      "tot_cnt": 124,
      "by_currency": {
        "VND": { "count": 100, "totl_amt": "120000000", "sply_amt_sum": "108000000", "tax_amt_sum": "12000000" },
        "USD": { "count":  24, "totl_amt":    "55000",   "sply_amt_sum":    "50000",   "tax_amt_sum":    "5000" }
      },
      "buckets": {
        "all":     { "cnt": 124, "loc_amt": "120000000", "usd_amt": "55000" },
        "open":    { "cnt":  80, "loc_amt":  "80000000", "usd_amt": "20000" },
        "overdue": { "cnt":  10, "loc_amt":  "10000000", "usd_amt":  "3000" },
        "paid":    { "cnt":  34, "loc_amt":  "30000000", "usd_amt": "32000" }
      }
    },
    "page": { "page_no": 1, "page_per_cnt": 15, "tot_page_cnt": 9 },
    "records": [
      {
        "doc_id": "12C25TYY-000001234",
        "issu_dt": "20260520",
        "trco_cmnm": "ABC Trading Co., Ltd",
        "trco_tax_id": "0123456789",
        "trco_trsc_ccy_cd": "VND",
        "totl_amt": "1200000",
        "sply_amt_sum": "1080000",
        "tax_amt_sum": "120000",
        "einv_invoicestatus": "Đã cấp mã hóa đơn",
        "einv_invoicesymbol": "1C25TYY",
        "einv_numericsymbol": "1",
        "hand_doc_id": "0001234",
        "doc_type": "I",
        "prov_stts": "OP",
        "due_dt": "20260620",
        "over_due_days": 0,
        "read_yn": "N",
        "companystatus": "NNT đang hoạt động (đã được cấp GCN ĐKT)",
        "appr_seq_no": null,
        "memo": null,
        "cont": null,
        "rcv_yn": "N",
        "color": null
      }
    ]
  }
}
```

**Error codes:**

| Code | Meaning |
|---|---|
| `WBE_VALIDATION` | bean validation on `from_dt` / `to_dt` / `page_no` / `page_per_cnt` |
| `WBE_EINV_INVALID_PERIOD` | `to_dt < from_dt`, or range > 1 year (legacy `ValidationHelper.validatePeriod`) |
| `WBE_EINV_UNSUPPORTED_DOC_TYPE` | (internal — should not reach this controller; controller pins doc_type='I') |
| `WBE9999` | unexpected |

**Audit:** writes 1 `AUDIT_LOGS_INFM` row per call with `WSVC_ID = "sales-einvoices"`, `ACT = "LIST"`.

**JEX file being replaced:** `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp` (with `DOC_TYPE='I'`).

### 4.2 GET `/api/v1/addon/expense-einvoices` — list Expenses

Same shape as §4.1 but `DOC_TYPE='B'`. Wire-distinct because routes / packages / audit / View Setting all use the `expense-einvoices` slug.

Differences from Sales:

- `einv_testResults` not used in the Expenses default (legacy passes `'all'`; refactor: same default).
- Currency filter is present (legacy Expenses-only — see [einv_0001_001_view.jsp:747-769](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp#L747-L769)).
- Counterparty label flips Buyer → Supplier on the frontend (`expense_einvoices.*` translations).
- The "check_result" set accepted by the scrape ingest is **larger** for Expenses (additional `Cục Thuế đã nhận` variants — see `nxisas_einvoice_c001_act.jsp:207-209`).

### 4.3 PUT `/api/v1/addon/{slug-einvoices}/{doc_id}/currency` — inline currency edit

Replaces JEX `einv_0001_001_u001`.

**Path params:** `slug-einvoices` (`sales-einvoices` | `expense-einvoices`), `doc_id`.

**Request:**

```json
{ "trco_trsc_ccy_cd": "USD" }
```

**Validation:** `trco_trsc_ccy_cd` must be in {`VND`, `USD`, `998`} (legacy `CcyCd.fromValue != CcyCd.NONE`).

**Response:** `204 No Content` (or `200` with `{ doc_id, trco_trsc_ccy_cd }`).

**Error codes:**

| Code | Meaning |
|---|---|
| `WBE_VALIDATION` | missing or empty `trco_trsc_ccy_cd` |
| `WBE_EINV_INVALID_CURRENCY` | unknown currency code |
| `WBE_EINV_NOT_FOUND` | `BUSA_DOC_MSTR` row not in tenant |

**Audit:** `AUDIT_LOGS_INFM` row with `ACT = "UPDATE"`, `BEFORE / AFTER` JSON.

### 4.4 POST `/api/v1/addon/einvoices/scrape/ingest` — accept encrypted batch

Replaces JEX `nxisas_einvoice_c001`.

**Request:**

```json
{
  "einv_type": "1",
  "nxisas_uid": "<35-char Coocon Uid>",
  "nxisas_action": "<35-char Coocon Action>",
  "from_dt": "20260101",
  "to_dt":   "20260131",
  "rec_in": [
    {
      "enc_str": "<Coocon-encrypted JSON string starting with ENC=>",
      "einv_test_results": "all"
    }
  ]
}
```

**Behaviour:**

1. Validates `einv_type ∈ {"1","2"}`, `nxisas_uid` + `nxisas_action` non-blank, `from_dt`/`to_dt` valid, `rec_in[].enc_str` non-blank.
2. For each `rec_in[i]`:
   - Decrypt `enc_str` via `IsasSeedCbcDecoder.decrypt(enc, uid, action)` (new component wrapping `com.coocon.securty.util.ISASSeedCBC` — the same library the legacy JEX uses; ships as a JAR dependency. See OQ-5).
   - Parse `invoiceList[]`.
   - Filter by `checkResult` (different set for Sales vs Expenses — see §2.2.3 step 3).
   - Compose `DOC_ID` per `EINV_TYPE`.
   - Insert one `BUSA_DOC_MSTR` row per accepted invoice. Use `JPA persist` (not native batch); rely on Hibernate batch insert + statement reordering for throughput. Mark all rows `PROV_STTS = 'OP'` unless `EINV_INVOICESTATUS ∈ {"Hóa đơn đã bị thay thế", "Hóa đơn đã bị hủy"}` → `'VD'`.
3. Write **one** `EXT_TRAN_CALL_HIS` row per `rec_in[]` element with `SVC_ID = "GDT_INQUIRY_SALES"` or `"GDT_INQUIRY_EXPENSES"`, `WEB_SVC_ID = "sales-einvoices"` (or expense), `RSLT_CD = <Coocon ErrorCode>`, `ERR_CONT`, `START_DATE = from_dt`, `END_DATE = to_dt`, `BIZ_KEY`, `USER_ID`.
4. Write `NXSIAS_MODULE_TRAN_LOG_HIS` rows via the reused `NxisasAuditLogger`.

**Response:**

```json
{
  "data": {
    "mod_dtm": "20260524150035",
    "nxisas_err_code": "00000000",
    "nxisas_err_cont": null,
    "records": [
      {
        "doc_id":           "12C25TYY-000001234",
        "hand_doc_id":      "0001234",
        "einv_invoicesymbol": "1C25TYY",
        "einv_test_results":  "all",
        "einv_seller_tax_code": "0123456789",
        "einv_seller_name":     null
      }
    ]
  }
}
```

The frontend uses the returned `records[]` to drive per-row `enrich` calls.

**Audit:** one slug-bound `AUDIT_LOGS_INFM` row (`ACT = "SCRAPE_INGEST"`) — refactor addition. Legacy NXISAS already writes `EXT_TRAN_CALL_HIS` + `NXSIAS_MODULE_TRAN_LOG_HIS`; the refactor adds a third row keyed by slug for parity with `firm-banking-settings` / `contacts`.

**JEX file being replaced:** `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c001_act.jsp`.

### 4.5 POST `/api/v1/addon/einvoices/scrape/enrich` — per-invoice enrichment

Replaces JEX `nxisas_einvoice_c002`.

**Request:**

```json
{
  "doc_id": "12C25TYY-000001234",
  "nxisas_uid": "<35-char>",
  "nxisas_action": "<35-char>",
  "mod_dtm": "20260524150035",
  "einv_type": "1",
  "detail_enc_str": "<encrypted JSON containing productList[], feeList[], taxList[], buyer/seller info>"
}
```

**Behaviour:** mirrors `nxisas_einvoice_c002_act.jsp:140+`:

1. Decrypt.
2. Resolve / create `TRCO_INFM` row by `(BIZ_KEY, TRCO_TAX_ID)`; normalise `TRCO_NICK_NM` via the `getNormalizeDisplayNameBySystem` equivalent (`SysTrcoInfmDisNm` table is already in the refactor scope).
3. Insert `TRCO_ADDR_INFM`, `TRCO_ACCT_INFO`, `PGRO_BSAC_LNKN_INFM` (`GRP_CD = 'B'` Sales, `'S'` Expenses).
4. Insert `BUSA_DOC_DETL` rows (one per `productList[i]`).
5. Insert `EINV_FEE_DETL` rows (one per `feeList[i]`).
6. Insert `EINV_TAX_DETL` rows (one per VAT-rate group).

**Response:** `204 No Content`.

**Error codes:**

| Code | Meaning |
|---|---|
| `WBE_VALIDATION` | missing required fields |
| `WBE_EINV_NOT_FOUND` | `doc_id` not in tenant |
| `WBE_EINV_DECRYPT_FAILED` | `ISASSeedCBC.decrypt` returned null |
| `WBE_EINV_ENRICH_PARSE` | JSON shape unexpected |

**JEX file being replaced:** `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/nxisas_einvoice_c002_act.jsp`.

### 4.6 GET `/api/v1/addon/einvoices/scrape/cert-form/{bank_cd}` — cert form metadata

Replaces JEX `nxisas_getCertFormInfo`. Reads `BANK_INFM_DETL` per bank code to drive the cert / login form (which input boxes to show).

For e-Invoice this is fixed to `bank_cd = "VIETNAM_GDT"`. The endpoint stays generic so banking flows can reuse it.

**Response:** array of input-field metadata.

**JEX file being replaced:** `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/nxisas/nxisas_getCertFormInfo_act.jsp`.

### 4.7 GET `/api/v1/addon/einvoices/scrape/module-info/{api_id}` — module/class lookup

Replaces JEX `nxisas_getModuleInfo`. Returns `{ module, category }` for a given `api_id` (e.g. `GDT_INQUIRY_SALES` → `{ module: "VIETNAM_GDT", category: "COMMON" }`).

Implementation: lookup table seeded via Flyway (refactor replaces the legacy `WABOOKSOSMCAPIUtil.callApi` round trip — see OQ-5).

### 4.8 GET `/api/v1/addon/einvoices/scrape/history` — call history popup

Replaces JEX `nxisas_einvhis_r001`. Lists `EXT_TRAN_CALL_HIS` rows with `SVC_ID LIKE 'GDT_INQUIRY%'` for the tenant.

**Query params:** `from_dt`, `to_dt`, `stts` (`""` / `"0"` success / `"1"` fail).

**Response:** `{ records: [{ reg_dtm, svc_id, rslt_cd, err_cont, start_date, end_date, web_svc_id }] }`.

### 4.9 POST `/api/v1/addon/sales-einvoices/export` and `/expense-einvoices/export` — Excel

Implements `ExportRowProvider` per the shared `common/export/` rule in `wecms-api/CLAUDE.md`.

**Request body:** `ExcelExportRequest` (shared shape).

**Response:** streams XLSX bytes (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`).

**Two export modes** (legacy parity — list vs details):

- `mode = "list"` — current view only (one row per invoice).
- `mode = "details"` — joined with `BUSA_DOC_DETL` + `EINV_FEE_DETL` + `EINV_TAX_DETL` (one row per line item — 63 columns per the legacy template).

The `mode` field is a refactor-specific add to `ExcelExportRequest` (extended via `filters.mode`).

`screenId` is `sales-einvoices` or `expense-einvoices`. Allow-list field IDs match the View Setting seed (§5.2).

**JEX file being replaced:** `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_r002_excel_view.jsp`.

---

## 5. Database Impact

### 5.1 Existing tables — read/write summary

See §2.4. All required tables are already on the **PROD Active Table List**. The schema for `BUSA_DOC_MSTR` (100 cols), `BUSA_DOC_DETL`, `EINV_FEE_DETL`, `EINV_TAX_DETL`, `EXT_TRAN_CALL_HIS`, `NXSIAS_MODULE_TRAN_LOG_HIS`, `GDT_CERT_INFO`, `TRCO_INFM`, `TRCO_ADDR_INFM`, `TRCO_ACCT_INFO`, `PGRO_BSAC_LNKN_INFM`, `BANK_INFM_DETL` is already in Flyway (V13–V97) per the migration history.

### 5.2 New Flyway migrations required

The refactor adds:

| Version | Purpose |
|---|---|
| `V{N}__einv_audit_logs_slug_backfill.sql` | (optional) back-fill any prior local rows in `AUDIT_LOGS_INFM` that were written with a legacy WSVC id to the new slug. Probably zero rows in local. |
| `V{N+1}__addon_einvoices_view_setting_seed.sql` | Seed `SYS_SCR_OPTION_WECMS` with two screen rows: `sales-einvoices` / `default` and `expense-einvoices` / `default`. Column set per §5.3. |
| `V{N+2}__nxisas_api_module_info.sql` | Replace the `WABOOKSOSMCAPIUtil.callApi` round-trip in `nxisas_getModuleInfo` with a static lookup table `NXISAS_API_MODULE` (`api_id`, `module`, `category`). Seed `GDT_INQUIRY_SALES` / `GDT_INQUIRY_EXPENSES` rows. **OQ-5** — confirm with Sonny whether this table is acceptable as a refactor addition (it's a new table; PROD Active Table List rule prohibits adding new tables, so this might land as a Java `Map<String,String>` config instead). |

No `BUSA_DOC_MSTR` / `BUSA_DOC_DETL` / `EINV_*_DETL` schema changes — the column set is already complete.

### 5.3 View Setting seed (default column set per screen)

Per the global View Setting rule in `wecms-api/CLAUDE.md`. Both screens seed identical columns (different `WEB_SVC_ID`).

Default column order (legacy `table1` / `table2` parity, narrowed to wire fields the refactor will populate):

| disp_ord | field_id | field_nm | use_yn | fix_yn | sort_yn | size |
|---|---|---|---|---|---|---|
| 0  | `CHECKBOX`            | (special) | Y | Y | N | 38px |
| 1  | `ISSU_DT`             | Issue Date | Y | Y | Y | 120px |
| 2  | `EINV_INVOICESYMBOL`  | Symbol/No. | Y | N | Y | 160px |
| 3  | `TRCO_CMNM`           | Buyer/Supplier | Y | N | Y | 240px |
| 4  | `TRCO_TAX_ID`         | MST | Y | N | N | 140px |
| 5  | `COMPANYSTATUS`       | Buyer Status | Y | N | N | 180px |
| 6  | `TRCO_TRSC_CCY_CD`    | Currency | Y | N | Y | 80px |
| 7  | `TOTL_AMT`            | Total Amount | Y | N | Y | 160px |
| 8  | `EINV_INVOICESTATUS`  | Status | Y | N | Y | 160px |
| 9  | `TAX_AMT_SUM`         | Tax Amount | Y | N | Y | 160px |
| 10 | `SPLY_AMT_SUM`        | Amount before Tax | Y | N | Y | 160px |
| 11 | `DUE_DT`              | Due Date | N | N | Y | 120px |
| 12 | `USAG_NM`             | Purpose | N | N | N | 160px |
| 13 | `MEMO`                | Memo | N | N | N | 200px |
| 14 | `PRJT_NM`             | Project | N | N | N | 160px |
| 15 | `APPR_SEQ_NO`         | Approval No. | N | N | N | 140px |
| 99 | `ETC`                 | (special — action column) | Y | Y | N | 80px |

`SORT_YN='Y'` columns must each have a matching constant in `EinvoiceSortField`.

### 5.4 Audit logging

Every list / update / scrape-ingest / scrape-enrich / export call writes one `AUDIT_LOGS_INFM` row via the shared `AuditLogger`:

- `WSVC_ID = "sales-einvoices"` or `"expense-einvoices"` (slug, not legacy `einv_0001_001`).
- `ACT` ∈ `{ "LIST", "UPDATE", "SCRAPE_INGEST", "SCRAPE_ENRICH", "EXPORT" }`.
- `REQ_DATA` carries the lowercase snake_case request (sanitised — no `enc_str` blobs > 32k).

This follows the `firm-banking-settings` / `contacts` / `chart-of-accounts` precedent (slug-based WSVC_ID, retired 2026-05-09 by the workspace CLAUDE.md override).

---

## 6. Validation Rules

### 6.1 Field-level

| Field | Rule | Source |
|---|---|---|
| `from_dt`, `to_dt` | `@Pattern("^\\d{8}$")`, parseable LocalDate | `ValidationHelper.validateDate8` legacy parity |
| period `from_dt..to_dt` | `to_dt >= from_dt`, range ≤ 365 days | `ValidationHelper.validatePeriod` |
| `page_no` | optional integer ≥ 1 | `ValidationHelper.validatePageNo` |
| `page_per_cnt` | optional integer ∈ {15, 20, 50, 100} | `ValidationHelper.validatePagePerCnt` (refactor narrows to the legacy dropdown values) |
| `invoice_status` | optional; one of the 7 GDT-side strings (kept verbatim Vietnamese) or empty | new |
| `currency` | optional; one of {VND, USD, 998, 999} | new |
| `doc_id` | `@Pattern("^[A-Z0-9-]{1,40}$")` | composes from numericSymbol + invoiceSymbol + invoiceNo |
| `trco_trsc_ccy_cd` | `CcyCd.fromValue != CcyCd.NONE` | legacy `einv_0001_001_u001:74` |
| `nxisas_uid`, `nxisas_action` | length ≥ 30 (legacy precondition for the SEED-CBC parameterised path) | `ISASSeedCBC.decrypt(enc, uid, action)` falls back to default key when < 30 |
| `einv_type` | `"1"` or `"2"` | `nxisas_einvoice_c001_act.jsp:154+` |
| `rec_in[].enc_str` | non-blank | legacy `ValidationHelper.validateRequired` |

### 6.2 Business rules

1. **Tenant scope** — every query and every write constrains on `BIZ_KEY = TenantContext.currentBizKey()`. Never `LIKE '%'`. Multi-tenant test deferred to V73's `wecmshaengsung@gmail.com` setup.
2. **Doc-type pinning** — `SalesEinvoiceController` hard-pins `DOC_TYPE='I'`; `ExpenseEinvoiceController` pins `'B'`. Frontend never sends a `doc_type`. Refactor rejects any attempt to override via query string.
3. **Hand-registered exclusion** — list always carries `WHERE hand_reg_yn = 'N'` (legacy parity). Hand-registered invoices live on a different screen (out of scope).
4. **Line-item guard** — list always joins to `BUSA_DOC_DETL` (only show invoices that have at least one line item — legacy `:181` semi-join).
5. **Status filter — exclude void/error** — list always carries `WHERE prov_stts NOT IN ('EE', 'VD')` (legacy `:107-111`).
6. **Currency code change idempotency** — `PUT /currency` validates the new code is not `NONE`; same code as before → 204 no-op.
7. **Duplicate guard on scrape** — `BUSA_DOC_MSTR` PK is `(BIZ_KEY, DOC_ID)`. `c001` uses `INSERT … ON CONFLICT DO NOTHING` to handle the "user re-runs scrape over an already-imported range" case. Returned `records[]` includes both freshly inserted and previously seen rows so the frontend can decide whether to re-enrich.
8. **Period cap** — refactor caps the scrape date range at 90 days (legacy doesn't cap; introduced here to bound Coocon load). OQ-7.

### 6.3 Integration-level (Coocon gateway)

1. `dataObj.ErrorCode == "00000000"` → success; persist invoices.
2. `dataObj.ErrorCode == "42110000"` → "no data in range" — treated as success (no rows to import), but `ExtTranCallHis` still records the call.
3. Any other `ErrorCode` → surface via `nxisas_err_code` / `nxisas_err_cont` on the response; **do not** rollback the audit row (it's wrapped in `REQUIRES_NEW`).
4. `LOGIN_CAN` (user cancelled OTP / CAPTCHA / QNA) → no `BUSA_DOC_MSTR` writes, audit row carries error.

---

## 7. Frontend Architecture

### 7.1 Page composition

One shared shell, two thin route wrappers (§3.5). Both routes use the same `<EInvoicesPage>` component.

`<EInvoicesPage>` composition:

```
<EInvoicesPage type="SALES" translationPrefix="sales_einvoices">
  <PageHeader title={t("sales_einvoices.title")}>
    <EInvoiceToolbar>
      <ExcelDropdown />    {/* "List" + "With Details" */}
      <PrintButton />
      <UpdateButton onClick={openScrapeDialog} />
    </EInvoiceToolbar>
  </PageHeader>
  <EInvoiceFilters>
    <InvoiceStatusSelect />
    <DateRangePicker />
    <CurrencySelect />     {/* Expenses only */}
    <SymbolTypeSelect />   {/* Sales only */}
    <SearchInput />
  </EInvoiceFilters>
  <EInvoiceTable columns={visibleColumns} rows={records} />
  <EInvoiceFooter totals={totals} />
  <Pagination />
  {scrapeDialogOpen && <NxisasScrapeDialog onClose={closeScrapeDialog} />}
</EInvoicesPage>
```

### 7.2 Shared components reused

- `<DataTable>` from `wecms-web/src/components/ui/table/` (the one Banking / Settings use).
- `<DateRangePicker>` from `components/ui/date/` (covers the legacy `MAN_DT_PICKER` + presets).
- `<ExcelDropdown>` from `components/ui/toolbar/` — already used by Banking. Adds a `mode` enum to its API for list / with-details.
- `<PrintButton>` + `printReport()` helper from `lib/print.ts`.
- `<PageHeader>` from `components/ui/layout/`.
- View Setting hook — `useViewSetting(screenId, dymAreaId)` — used internally by the export flow but no popover rendered on this page.

### 7.3 Typography

All text renders through the global Tailwind tokens defined in the workspace root CLAUDE.md "Global Typography" section:

- Page title — `text-page-title` (18 / 500).
- Table header — `text-table-header` (13 / 600).
- Table body — `text-table-cell` (13 / 400).
- Filter inputs — `text-input` (13 / 400).
- Buttons — `text-button` (13 / 500).
- Multi-currency footer totals — `text-base font-medium` (13 / 500) for labels, `text-base font-semibold` for amounts.

No ad-hoc `text-[14.5px]` / `font-[533]` / inline `style={{ fontFamily }}`.

### 7.4 Desktop-first

Per memory `feedback_desktop_first_ui.md` — the layout is desktop-first (Figma WB365 frames are laptop-sized). Mobile layouts are not authored. Min table width is whatever the Figma frame dictates (typically 1366px); table overflows horizontally with sticky header.

### 7.5 i18n keys

Sales prefix `sales_einvoices.*`; Expenses prefix `expense_einvoices.*`. Keys land in `wecms-web/locales/{en,ko,vi}.json` (KH deferred — legacy doesn't have KH translations for the GDT-status strings either).

Sample keys:

```jsonc
{
  "sales_einvoices": {
    "title": "Sales e-Invoices",
    "toolbar.update": "Update",
    "toolbar.print": "Print",
    "toolbar.excel.list": "Download Excel with List",
    "toolbar.excel.details": "Download Excel with Details",
    "filter.invoice_status.all": "All Invoice Status",
    "filter.invoice_status.original": "Original",
    "filter.invoice_status.replacement": "Replacement",
    "filter.invoice_status.adjustment": "Adjustment",
    "filter.invoice_status.replaced": "Replaced",
    "filter.invoice_status.adjusted": "Adjusted",
    "filter.invoice_status.cancel": "Cancel",
    "filter.date.last_30": "Last 30 Days",
    "filter.date.last_90": "Last 90 Days",
    "filter.date.last_180": "Last 180 Days",
    "filter.date.manual": "Enter Manually",
    "column.issu_dt": "Issue Date",
    "column.symbol_no": "Symbol/No.",
    "column.buyer": "Buyer",
    "column.mst": "MST",
    "column.buyer_status": "Buyer Status",
    "column.currency": "Currency",
    "column.totl_amt": "Total Amount",
    "column.einv_invoicestatus": "Status",
    "column.tax_amt_sum": "Tax Amount",
    "column.sply_amt_sum": "Amount before Tax",
    "scrape.dialog.title": "Real-time Online Inquiry",
    "scrape.dialog.range": "Range",
    "scrape.dialog.unreceived_only": "Include only invoices that haven't been received yet",
    "scrape.dialog.cancel": "Cancel",
    "scrape.dialog.confirm": "Confirm",
    "scrape.engine.not_installed": "NXiSAS engine is not installed. Please install it to continue.",
    "scrape.engine.install": "Install NXiSAS"
  }
}
```

Expenses overrides only the labels: `title` → "Expenses e-Invoices", `column.buyer` → "Supplier", `column.buyer_status` → "Supplier Status".

### 7.6 NXiSAS bridge (browser-side)

The bridge lives in `wecms-web/src/features/einvoices/lib/nxisasBridge.ts`. Responsibilities:

1. Open `new WebSocket("wss://ibasews.coocon.co.kr:14102/")`.
2. Handle the 8 message types from the engine (`OPEN`, `RUN`, `CANCEL`, `CANCELALL`, `CERTLIST`, `ONSTATUSCHANGED`, `ONCOMPLETED`, error).
3. Surface auth challenges (`OTP`, `CAPTCHA`, `QNA`) by mounting the respective `<Nxisas*Dialog>`.
4. On `ONCOMPLETED`, package the engine's encrypted `Result` blobs into a `POST /api/v1/addon/einvoices/scrape/ingest` body.
5. After ingest succeeds, iterate `records[]` and call `POST /scrape/enrich` for each.

**legacy bits dropped:** the SWF / Flash bridge (`web_socket.js` + `swfobject.js` + `websocket.swf`) is not ported — modern browsers have native WebSocket; the legacy `web_socket.js` only mattered for IE8-era fallback. The `jquery-3.3.1.min.js` dependency is dropped — React/Next.js owns DOM updates. The legacy `nxisas_v2_u000.js` (older u000-suffixed variant) is not ported either.

---

## 8. Reusable Logic & Dependencies

### 8.1 Cross-cutting reuse

| Concern | Already in wecms-api | Reuse path |
|---|---|---|
| Auth + JWT + Tenant context | `common/security/` + `common/tenant/TenantContext` | Standard JWT filter. `prdtType` claim drives the add-on gate. |
| Audit logger | `common/auditlog/service/AuditLogService` | Wire with slug `sales-einvoices` / `expense-einvoices`. |
| NXISAS gateway client + audit + session registry | `domain/banking/nxisas/` | Add 2 new job enums + 2 new DTO pairs (§3.3). Reuse `NxisasGatewayClient.call(...)` envelope. |
| `NxisasAuditLogger` (NXSIAS_MODULE_TRAN_LOG_HIS writer) | `domain/banking/nxisas/audit/` | Reused unchanged. |
| Common-table-sort whitelist + `SortDirection` | `common/web/` | New `EinvoiceSortField` enum implements `SortableField`. |
| Excel export pipeline | `common/export/` | New `EinvoiceExportProvider` (one class) per legacy table mode. |
| View Setting | `domain/setting/` + `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` | Seed-only adoption (no controller changes). |
| AmountFormatter | `domain/banking/service/AmountFormatter` | Reused for Excel subheader. |
| i18n + MessageResolver | `common/i18n/` | New error code keys go to `messages*.properties`. |
| Tenant key sequence | `LegacyFunctionRepository` (V11..) | Not used — `BUSA_DOC_MSTR.DOC_ID` is composed in Java per §2.2.3 step 4. |
| Currency code enum | `common/enums/` (CcyCd via shared lookup) | Validate `trco_trsc_ccy_cd` via the enum's `fromCode`. |

### 8.2 New components introduced

| Component | Purpose |
|---|---|
| `IsasSeedCbcDecoder` | Java wrapper around the existing `com.coocon.securty.util.ISASSeedCBC` (legacy JEX uses it directly). Provided by Coocon as a JAR; the refactor adopts it as-is (no rewrite — see OQ-5). |
| `NxisasGatewayClient.invoiceList(...)` / `.invoiceDetail(...)` | New gateway methods for the two GDT jobs. |
| `EinvoiceListService` | Implements the list query (pagination + filters + sort). |
| `EinvoiceTotalsService` | Per-currency totals + dashboard buckets. |
| `EinvoiceScrapeIngestService` | Decrypts + persists `c001` batch. |
| `EinvoiceScrapeEnrichService` | Decrypts + persists `c002` batch. |
| `EinvoiceSortField` | View Setting `FIELD_ID` → JPA property whitelist. |
| `EinvoiceExportProvider` | Implements `ExportRowProvider` for both Sales + Expenses (registered twice, one per `screenId`). |

### 8.3 Dependencies to add to `wecms-api/build.gradle`

- The Coocon `ISASSeedCBC` JAR. JEX bundles it locally; the refactor needs the same JAR mounted as a `flatDir` repo or copied into `wecms-api/libs/`. **OQ-5.**

No new web dependency. Apache POI is already on the classpath.

### 8.4 Dependencies on other refactor work

| Dependency | Owner | Status |
|---|---|---|
| `contacts` feature (`TRCO_INFM` + `TRCO_ADDR_INFM` + `TRCO_ACCT_INFO` + `PGRO_BSAC_LNKN_INFM` CRUD) | landed (V84-V89) | ✅ — `EinvoiceScrapeEnrichService` reuses the contacts repositories |
| `firm-banking-settings` audit logging pattern | landed | ✅ — pattern to follow |
| `view-setting` end-to-end | landed | ✅ — seed-only adoption |
| `common/export/` shared pipeline | landed | ✅ |
| `common/web/` sort whitelist | landed | ✅ |
| `domain/banking/nxisas/` gateway | landed | ✅ — extend with 2 jobs |
| `bank-matching-receipt` (Simple Credit/Debit Confirmation layer) | not landed | ⏸ — the layer popup in `einv_0001_001_view.jsp:919+` is part of bank-matching, not e-Invoice listing; out of scope here. |

---

## 9. Risks & Open Questions

### 9.1 Risks

| ID | Risk | Mitigation |
|---|---|---|
| R-1 | NXiSAS.exe distribution — Windows-only, admin install, hosts-file override + self-signed cert. macOS / Linux users cannot use Update. | Refactor surfaces a clear engine-not-installed banner with the install link. macOS / Linux + headless support is out of scope (Coocon doesn't ship a non-Windows engine). |
| R-2 | Coocon engine version pin (`2023.4.7.0` per `isasscaping.js:13`). A newer GDT portal change can break the engine; the refactor doesn't control the .exe. | Audit `NXSIAS_MODULE_TRAN_LOG_HIS.NXISAS_ERR_CODE` rates and forward to ops when error rate > threshold. Out-of-scope for the feature MR. |
| R-3 | The SEED-CBC `Uid` / `Action` strings (≥ 30 chars) come from the engine. If the engine returns shorter strings, `ISASSeedCBC.decrypt` falls back to a default key which may not match what the engine encrypted with. | Refactor validates both ≥ 30 in `validateInput` and falls through to the engine's default key path (legacy parity). Audit any 22XXXXXX-class errors. |
| R-4 | GDT throttling — too-frequent scrapes may get the user temporarily locked out. | Refactor caps `to_dt - from_dt ≤ 90 days` (OQ-7) and surfaces last-scrape time on the toolbar via the `END_DATE` field on the list response so users see when they last refreshed. |
| R-5 | Coocon license is hardcoded (`MstCompNo=5568800909` per `isasscaping.js:4`) — if Webcash changes the license, the refactor needs to update the bridge constant. | Move the constant into a Spring config exposed to the bridge via a `/api/v1/addon/einvoices/scrape/license` endpoint (read-only). |
| R-6 | Multi-currency footer assumes only VND + USD. A row with a third currency (e.g. EUR via `998`) won't get a footer row. | Refactor computes totals per distinct currency in the result set — no VND/USD assumption. |
| R-7 | Legacy `prov_stts` rule treats "Hóa đơn đã bị điều chỉnh" + "Hóa đơn đã bị hủy" as `'VD'` in the **Expenses** branch but "Hóa đơn đã bị thay thế" + "Hóa đơn đã bị hủy" as `'VD'` in the **Sales** branch. This is a JEX inconsistency. | Refactor preserves the asymmetry verbatim (`nxisas_einvoice_c001_act.jsp:187 vs :249`). OQ-6 raises this with the PO. |

### 9.2 Open Questions

| OQ | Question | Owner |
|---|---|---|
| OQ-1 | Should the e-Invoice screens be product-type-gated? Legacy shows them for every `PRDT_TYPE`, but the refactor's `PrdtType` enum only models `F` / `M` / `H`. Confirm the gating list. | Mr. Jung |
| OQ-2 | Defer the dashboard band (`PRDT_TYPE.C / D` only)? It's ~120 lines of JSP + 4 totals buckets + 4 date pickers per bucket. | Mr. Jung |
| OQ-3 | The 7 `EINV_INVOICESTATUS` values are localized Vietnamese strings ("Hóa đơn đã bị thay thế" etc.). The refactor should introduce a coded enum (`OP` / `REPL` / `ADJ` / …) and translate on render. The legacy DB function `wb_get_invoice_status(value, LANG_CD)` is the current source of truth. Confirm we can introduce the coded enum (refactor only) without changing the legacy schema. | Mr. Jung + Sonny |
| OQ-4 | Should the View Setting popover be rendered for these screens? Legacy says NO (toolbar shows Print/Excel/Update only). The existing addon docs explicitly defer it. | Mr. Jung |
| OQ-5 | Coocon `ISASSeedCBC` JAR — confirm with Sonny how the refactor depends on the JAR (vendored JAR in `wecms-api/libs/` vs Maven coordinate vs reimplement). Also confirm whether `NXISAS_API_MODULE` lookup table is acceptable vs a Java `Map<String,String>` config. | Sonny |
| OQ-6 | The Sales / Expenses `PROV_STTS = 'VD'` rule asymmetry between `nxisas_einvoice_c001:187` and `:249` — preserve verbatim or normalize to one rule? | Mr. Jung |
| OQ-7 | Cap the scrape date range at 90 days to bound Coocon load? Or honour any range the user enters? | Mr. Jung |
| OQ-8 | `nxisas_einvhis` (Latest Inquiry History) popup — should it live under the e-Invoice screen as a sub-route or as a standalone admin tool? | Mr. Jung |
| OQ-9 | The legacy `nxisas_einvhis_dt_r001` (per-call detail) — keep? It exposes `EXT_TRAN_CALL_HIS.ERR_CONT` JSON, which is operator-facing diagnostic detail. | Mr. Jung |
| OQ-10 | Sales-side `TRCO_INFM` upsert in `c002` uses `numericSymbol` as the seller-name proxy (`recObj.put("einv_sellerName", listObj.getString("numericSymbol"))` at `c001:267`). That looks wrong (numericSymbol is a 1-2 char code, not a name). Confirm intended behaviour. | Sonny |

---

## 10. Migration / Refactoring Plan (step-by-step)

Order matters: foundation → backend list → backend scrape ingest → backend scrape enrich → backend export → frontend shell → frontend bridge → integration.

### 10.1 Backend tasks

| Task | Description | Files (under `wecms-api/src/main/java/com/kosign/wecms/`) | Depends on |
|---|---|---|---|
| **BE-1** | Add `NxisasJob.INVOICE_LIST` + `NxisasJob.INVOICE_DETAIL` enum values | `domain/banking/nxisas/gateway/dto/NxisasJob.java` | — |
| **BE-2** | Add new gateway DTOs | `domain/banking/nxisas/gateway/dto/NxisasInvoiceListJobInput.java`, `NxisasInvoiceListJobResult.java`, `NxisasInvoiceDetailJobInput.java`, `NxisasInvoiceDetailJobResult.java` | BE-1 |
| **BE-3** | Add 2 methods on `NxisasGatewayClient` | `domain/banking/nxisas/NxisasGatewayClient.java` | BE-2 |
| **BE-4** | Vendor / wire the `ISASSeedCBC` JAR | `wecms-api/libs/`, `build.gradle` | OQ-5 resolved |
| **BE-5** | Create `IsasSeedCbcDecoder` wrapper | `domain/addon/einvoice/gateway/IsasSeedCbcDecoder.java` | BE-4 |
| **BE-6** | JPA entities | `domain/addon/einvoice/entity/{BusaDocMstr, BusaDocDetl, EinvFeeDetl, EinvTaxDetl, GdtCertInfo, ExtTranCallHis}.java` — extend existing skeleton from current static-UI MR | — |
| **BE-7** | Repositories | `domain/addon/einvoice/repository/{BusaDocMstrRepository, BusaDocDetlRepository, EinvFeeDetlRepository, EinvTaxDetlRepository, GdtCertInfoRepository, ExtTranCallHisRepository}.java` | BE-6 |
| **BE-8** | `EinvoiceFilterValidator` | `domain/addon/einvoice/service/EinvoiceFilterValidator.java` | — |
| **BE-9** | `EinvoiceSortField` whitelist | `domain/addon/einvoice/service/EinvoiceSortField.java` | — |
| **BE-10** | `EinvoiceListService` + DTOs | `domain/addon/einvoice/service/EinvoiceListService.java`, `domain/addon/einvoice/dto/{EinvoiceListRequest, EinvoiceListResponse, EinvoiceRow, EinvoiceTotals}.java` | BE-6, BE-7, BE-8, BE-9 |
| **BE-11** | `EinvoiceTotalsService` | `domain/addon/einvoice/service/EinvoiceTotalsService.java` | BE-10 |
| **BE-12** | `EinvoiceCurrencyUpdateService` + DTO | `domain/addon/einvoice/service/EinvoiceCurrencyUpdateService.java`, `dto/EinvoiceCurrencyUpdateRequest.java` | BE-6, BE-7 |
| **BE-13** | `SalesEinvoiceController` | `domain/addon/einvoice/controller/SalesEinvoiceController.java` | BE-10, BE-11, BE-12 |
| **BE-14** | `ExpenseEinvoiceController` | `domain/addon/einvoice/controller/ExpenseEinvoiceController.java` | BE-10, BE-11, BE-12 |
| **BE-15** | `EinvoiceScrapeIngestService` + DTOs | `domain/addon/einvoice/service/EinvoiceScrapeIngestService.java`, `dto/{EinvoiceScrapeIngestRequest, EinvoiceScrapeIngestResponse}.java` | BE-5, BE-6, BE-7 |
| **BE-16** | `EinvoiceScrapeEnrichService` + DTO | `domain/addon/einvoice/service/EinvoiceScrapeEnrichService.java`, `dto/EinvoiceScrapeEnrichRequest.java` | BE-5, BE-6, BE-7, contacts repositories |
| **BE-17** | `EinvoiceScrapeController` | `domain/addon/einvoice/controller/EinvoiceScrapeController.java` (handles ingest + enrich + cert-form + module-info + history) | BE-15, BE-16, BE-3 |
| **BE-18** | `GdtCertFormInfoService` + `GdtModuleInfoService` | `domain/addon/einvoice/gateway/{GdtCertFormInfoService, GdtModuleInfoService}.java` | BE-7 |
| **BE-19** | `EinvoiceExportProvider` (registered for both screen IDs) | `domain/addon/einvoice/export/EinvoiceExportProvider.java` | BE-10 |
| **BE-20** | Error codes | add `WBE_EINV_*` keys to `messages*.properties` | — |
| **BE-21** | Audit logger integration | wire `AuditLogService.log("sales-einvoices" / "expense-einvoices", ACT, ...)` into each controller | BE-13, BE-14, BE-17 |
| **BE-22** | Unit + integration tests | `src/test/java/com/kosign/wecms/domain/addon/einvoice/**` | every BE-* above |

### 10.2 Frontend tasks

| Task | Description | Files (under `wecms-web/src/`) | Depends on |
|---|---|---|---|
| **FE-1** | Create shared feature folder | `features/einvoices/` skeleton | — |
| **FE-2** | API client | `features/einvoices/lib/api.ts` — fetchSalesList, fetchExpenseList, postScrapeIngest, postScrapeEnrich, fetchScrapeHistory, fetchCertFormInfo, fetchModuleInfo, postExport, putCurrency | wire types from `lib/types/api.ts` |
| **FE-3** | TS wire types | `lib/types/api.ts` extensions for EinvoiceListResponse, EinvoiceRow, EinvoiceTotals, scrape DTOs | BE-10, BE-15 |
| **FE-4** | React Query hooks | `features/einvoices/hooks/{useEInvoiceList, useEInvoiceTotals, useScrapeBatch, useScrapeHistory, useCurrencyUpdate}.ts` | FE-2, FE-3 |
| **FE-5** | NXiSAS bridge | `features/einvoices/lib/nxisasBridge.ts` (WebSocket adapter, no UI) | — |
| **FE-6** | NXiSAS scrape dialog | `features/einvoices/components/nxisas/NxisasScrapeDialog.tsx` + child dialogs `NxisasOtpDialog`, `NxisasCaptchaDialog`, `NxisasQnaDialog`, `NxisasInstallBanner` | FE-5 |
| **FE-7** | `<EInvoicesPage>` shared shell | `features/einvoices/components/EInvoicesPage.tsx`, `EInvoiceToolbar.tsx`, `EInvoiceFilters.tsx`, `EInvoiceTable.tsx`, `EInvoiceFooter.tsx` | FE-3, FE-4 |
| **FE-8** | Sales route | `app/(dashboard)/addon/sales-einvoices/page.tsx` (1 line — `<EInvoicesPage type="SALES" translationPrefix="sales_einvoices" />`) | FE-7 |
| **FE-9** | Expenses route | `app/(dashboard)/addon/expenses-einvoices/page.tsx` | FE-7 |
| **FE-10** | i18n keys | `locales/{en,ko,vi}.json` for `sales_einvoices.*` + `expense_einvoices.*` | — |
| **FE-11** | View Setting screenId registry entry | `lib/viewSettingScreens.ts` | — |
| **FE-12** | Excel download UX | wire the toolbar's two-mode dropdown to `postExport(mode)` + save the blob | FE-2, BE-19 |
| **FE-13** | Sidebar / menu wiring | `components/layout/Sidebar.tsx` — add Sales + Expenses entries under "Add-on Service" | — |
| **FE-14** | Manual / Playwright tests | basic smoke for both routes, mocked NXiSAS bridge for the scrape flow | every FE-* above |

### 10.3 Database tasks

| Task | Description | File |
|---|---|---|
| **DB-1** | View Setting seed for `sales-einvoices` + `expense-einvoices` (table in §5.3) | `wecms-api/src/main/resources/db/migration/V{N}__addon_einvoices_view_setting_seed.sql` |
| **DB-2** | (Optional, OQ-5) `NXISAS_API_MODULE` lookup table + seed for `GDT_INQUIRY_SALES` + `GDT_INQUIRY_EXPENSES` | `V{N+1}__nxisas_api_module_info.sql` |
| **DB-3** | Demo seed for local DB — 30 rows of `BUSA_DOC_MSTR` per screen filtered to `BIZ_KEY = VN_VNM0004_000001`, plus matching `BUSA_DOC_DETL` / `EINV_FEE_DETL` / `EINV_TAX_DETL` (most-recent by `REG_DTM DESC`) | `V{N+2}__addon_einvoices_demo_seed.sql` |

No schema additions to the legacy tables. The 100-column `BUSA_DOC_MSTR` schema is already in place.

### 10.4 Test tasks

| Task | Description | Coverage |
|---|---|---|
| **T-1** | `EinvoiceListServiceTest` — period validation, default sort, currency filter, status filter, pagination | unit |
| **T-2** | `EinvoiceSortFieldTest` — every `SORT_YN='Y'` View Setting field maps to a real `BusaDocMstr` JPA property | unit |
| **T-3** | `EinvoiceScrapeIngestServiceTest` — happy path + `42110000` no-data + duplicate `DOC_ID` (ON CONFLICT) + `Hóa đơn đã bị thay thế` → `PROV_STTS='VD'` | unit + slice |
| **T-4** | `EinvoiceScrapeEnrichServiceTest` — TRCO_INFM upsert, BUSA_DOC_DETL line items, EINV_FEE_DETL, EINV_TAX_DETL row counts | unit + slice |
| **T-5** | `IsasSeedCbcDecoderTest` — round-trip with fixture encrypted strings (from the JEX test corpus) | unit |
| **T-6** | `EinvoiceExportProviderTest` — list mode + details mode, column intersection with View Setting | unit |
| **T-7** | `SalesEinvoiceControllerTest` + `ExpenseEinvoiceControllerTest` — JWT required, tenant scope, status code mapping | MockMvc |
| **T-8** | `EinvoiceScrapeControllerTest` — ingest happy path, enrich happy path, cert-form-info, module-info, history | MockMvc |
| **T-9** | Frontend Playwright — both routes render the static UI with seed data, the Update button opens the dialog | smoke |

### 10.5 Phase order (sprint planning hint)

```
Sprint N:
  Day 1 : BE-1, BE-2, BE-3, BE-9, BE-20
  Day 2 : BE-6, BE-7, BE-8
  Day 3 : BE-10, BE-11, BE-12  +  DB-1, DB-3
  Day 4 : BE-13, BE-14, BE-19, BE-21  +  T-1, T-2, T-6, T-7
  Day 5 : BE-4, BE-5, BE-15, BE-18  +  T-3, T-5
  Day 6 : BE-16, BE-17  +  T-4, T-8
  Day 7 : FE-1..FE-5
  Day 8 : FE-6, FE-7
  Day 9 : FE-8, FE-9, FE-10, FE-11, FE-12, FE-13  +  T-9
  Day 10: Stabilisation, OQ resolution, parity audit
```

Subject to OQ-5 resolution unblocking BE-4 / BE-5.

---

## Appendix A — Cross-references

- Workspace root rules: [workspace `CLAUDE.md`](../../../../CLAUDE.md) — typography, identifier-naming, AI output folders.
- Spring Boot rules: [`wecms-api/CLAUDE.md`](../../../CLAUDE.md) — wire format, View Setting, Excel export, common sort, audit, NXISAS gateway.
- Next.js rules: [`wecms-web/CLAUDE.md`](../../../../wecms-web/CLAUDE.md) — routing, typography, View Setting frontend.
- JEX framework rules: [`WECMS DEV/CLAUDE.md`](../../../../WECMS%20DEV/CLAUDE.md) — JEX is READ-ONLY.
- DB read rules: [`.claude/rules/database.md`](../../../../.claude/rules/database.md).
- Existing addon static-UI feature docs: [sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md), [expenses-e-invoices.md](../../features/addon-service/expenses-e-invoices.md).
- Migration mapping (companion doc): [sales-and-expense-einvoices.md](../../references/migration/sales-and-expense-einvoices.md).
- NXISAS gateway reference (banking side, fully built): [wecms-api/src/main/java/com/kosign/wecms/domain/banking/nxisas/](../../../src/main/java/com/kosign/wecms/domain/banking/nxisas/).
- Live JEX URLs:
  - Sales: `https://dev.wecms.vn/einv_0001_000.act` (or `einv_0001_001.act?einvType=1`)
  - Expenses: `https://dev.wecms.vn/einv_0001_001.act?einvType=2`

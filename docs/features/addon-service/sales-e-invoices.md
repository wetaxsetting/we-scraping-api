# Add-on Service > Sales e-Invoices — Refactor Feature Spec

**Module:** Add-on Service
**Menu:** Sales e-Invoices (outbound customer invoices)
**Route:** [/addon/sales-einvoices](../../../../wecms-web/app/(dashboard)/addon/sales-einvoices/page.tsx)
**Refactor screen ID (proposed):** `addon-sales-einvoices`
**JEX counterpart (legacy):** `einv_0001_001` (no JEX feature doc exists yet under [docs/jex/features/](../../../../docs/jex/features/))
**Status:** **Static UI only.** Page renders through [`<EInvoicesPage>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesPage.tsx) with mock data via [`<EInvoicesClient>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesClient.tsx); a Spring Boot skeleton (`EInvoiceController` / `EInvoiceService` / `BusaDocMstr` entity) exists but is **not yet wired end-to-end** to deliver the column set the UI shows.

JEX is **read-only reference**. This spec captures the refactor design; JEX behaviour is the functional source of truth.

> **Phase 2 implementation rules are locked.** See [planning § Phase 2 implementation rules (locked decisions)](../../planning/addon/sales-e-invoices-plan.md#phase-2-implementation-rules-locked-decisions). Backend / frontend implementation is approved to start subject to the deferred PO/designer items in the same planning doc. All references in this spec to "open question" or "TBD" should be read against IR-1 through IR-7 in the planning doc.

---

## Overview

Lists every e-Invoice the company has issued to its customers (outbound / sales side of `BUSA_DOC_MSTR`). Each row carries the invoice issue date, e-invoice serial, buyer (company name + Vietnamese tax-code MST + buyer status), currency, total amount, invoice status (`Original` / `Replacement` / `Adjustment` / `Replaced` / `Adjusted` / `Cancelled`), tax amount, and amount-before-tax. The table renders multi-currency totals in the footer (one row per currency seen in the result set, e.g. VND + USD simultaneously) and ships with print + Excel + Update controls in the header toolbar.

Sales (outbound, `DOC_TYPE='I'` ⇒ `BusaDocType.INVOICE`) and Expenses/Bill (inbound, `DOC_TYPE='B'` ⇒ `BusaDocType.BILL`) share one page shell — [`<EInvoicesPage>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesPage.tsx) — and only differ by `translationPrefix`, the `type` query param, and the data they render. The legacy listing service explicitly **rejects** `'E'` (`EXPENSE`) and `'S'` (`PAYROLLBOOK`) — those constants exist on `BusaDocType` but belong to other flows. Both refactor routes are configured today; this doc covers the Sales variant.

---

## Business purpose

Vietnamese tax law (Decree 123/2020 / Circular 78/2021) requires every legitimate B2B sale to issue a digitally-signed e-invoice transmitted to the GDT (General Department of Taxation). WeCMS is the operational hub where the finance team:

1. Inspects every issued e-invoice — date, serial number, buyer's tax-code (MST), GDT-side status, currency, gross / net / tax breakdown.
2. Reconciles invoice-level data against the bank-side receipt (the screen feeds the bank-matching workflow downstream).
3. Spots compliance issues — buyer's MST status (`Active`, `On closing`, `NNT ngừng hoạt động …`) flags a buyer whose tax registration is suspended, which can invalidate input-VAT credit on the buyer side.
4. Exports / prints the list for monthly tax filings and audit trails.

The page is the **read-side** of the e-invoice domain — issuing, replacing, adjusting, or cancelling an invoice is handled in the legacy JEX detail screens (`einv_0001_001_u001`, `einv_0002_001_u001`, etc.) and is **out of scope for the refactor's Sales listing page**.

---

## Current status — static UI only

- **UI:** complete and approved. The page is one of the four canonical baselines listed in the [wecms-web CLAUDE.md § Approved reference set](../../../../wecms-web/CLAUDE.md) (sibling-footer table with multi-currency totals).
- **Data:** the page calls [`fetchEInvoices(type)`](../../../../wecms-web/lib/api/addon.ts) → `GET /api/v1/addon/einvoices?type=SALES` from [`<EInvoicesClient>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesClient.tsx); when the backend response is empty, the table renders empty (Sprint 1 removed the mock fallback). The static look the team uses for screenshots / Figma comparison comes from `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced).
- **Backend:** a thin Spring Boot skeleton exists ([`EInvoiceController`](../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) + [`EInvoiceService`](../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java) + [`BusaDocMstr`](../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstr.java) entity + `BusaDocMstrRepository`). It returns rows from `BUSA_DOC_MSTR` filtered by `BIZ_KEY` + `DOC_TYPE='I'`, with optional `from` / `to` date range. Sprint 1 stops there — the response does **NOT yet** populate every column the UI shows (notably `MST` flag colours, `BUYER_STATUS` text, multi-currency totals are computed on a one-pass loop without GDT join, no GDT cert info, no `READ_YN` / `OVER_DUE_DAYS` etc. — see TODO).
- **Filters / pagination / Update / Print / Excel:** the chrome is rendered, but only the in-memory keyword filter is functional. Status dropdown is fixed to "All Invoice Status". Date-range pickers are display-only. Update / Print / Excel buttons have no handlers. Pagination state is computed client-side from row count; no server-side `page` / `size` params.
- **i18n:** Sales-side keys exist under the `sales_einvoices.*` prefix in `locales/{en,ko,vi}.json`. KM is missing.

---

## Scope

**In scope (refactor goal):**
- Read-only list of Sales e-Invoices (`DOC_TYPE='I'`) for the current tenant
- Issue date (legacy `ISSU_DT`); the static UI's "time" sub-line is a **refactor-only proxy** sliced from `REG_DTM[8..14]` — legacy does NOT expose an issue-time field
- Invoice serial (`EINV_INVOICESYMBOL`) and the status sub-line (`EINV_INVOICESTATUS` — Original / Replacement / Adjustment / Replaced / Adjusted / Cancelled). The static UI's `invoiceType` field is **misnamed** — it carries an `EINV_INVOICESTATUS` value, NOT the legacy `INV_TYPE` ('Commercial' / 'Tax' classifier).
- Buyer block: company name, MST code, buyer status (free-form text returned by GDT)
- Currency · Total amount · Invoice status · Tax amount · Amount-before-tax
- Multi-currency footer totals — one `<tr>` per currency seen in the page-level result set, sharing the table colgroup
- Filter row: invoice-status dropdown · date-range picker · keyword search
- Pagination row (page-size dropdown + arrow nav + `{shown} of {total}` count)
- Toolbar actions: Print · Excel (with chevron dropdown) · Update (refresh / reload). **No View Setting / list-view icon** — see IR-9 in the planning doc.
- View Setting **column metadata + Excel export integration** — global feature per [§13](../../../../wecms-web/CLAUDE.md), keyed by `WEB_SVC_ID = addon-sales-einvoices` (seed pending). **The user-facing `<ViewSettingPopover>` and its toolbar trigger are NOT rendered on this page** (Figma 9194:6859 toolbar is Print + Excel + Update only). The Update / Reset endpoints are not exposed by any UI on this page.
- Excel export — global feature per [§13b](../../../../wecms-web/CLAUDE.md), via the per-resource endpoint `POST /api/v1/addon/einvoices/export` (not yet implemented). The export pipeline still consumes the View Setting data internally for column intersection.

**Out of scope (handled elsewhere or later):**
- Issuing / replacing / adjusting / cancelling invoices — the legacy `einv_0001_001_u001` flow is **not** ported as part of this listing page
- Per-invoice detail page (line-items, tax breakdown by `EINV_TAX_DETL`, fee detail by `EINV_FEE_DETL`) — separate sprint
- GDT live status sync (the live `GDT_CERT_INFO` polling that drives `einv_invoiceStatus`) — relies on the legacy NXISAS bridge
- Bank-matching workflow — separate feature (`bank-matching-receipt`)
- Expenses e-Invoices — separate feature spec (`addon-expenses-einvoices`); shares the page shell but lives at `/addon/expenses-einvoices` and ships its own data + i18n
- Smart Organizer / OCR upload — Add-on Service has separate entry points
- Print template (legacy popup HTML) — refactor uses the shared [`printReport`](../../../../wecms-web/lib/print.ts) helper per [§6d](../../../../wecms-web/CLAUDE.md), not the legacy print path

---

## Legacy reference (JEX)

| Asset | Path |
|---|---|
| JEX list service ID (read) | `einv_0001_001_r001` |
| JEX page service ID (view) | `einv_0001_001` (the same view drives Sales when `einvType=1` and Expenses when `einvType=2`) |
| WSVC (read) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml` |
| WSVC (page) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001.xml` |
| WSVC (excel export) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r002_excel.xml` |
| WSVC (write) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_u001.xml` *(out of refactor scope — write path not refactored)* |
| Action JSP (read) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp` (inspected — drives the validation, baseline filters, and IDO dispatch) |
| IDO (paged read) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml` (the page-level paginated query — exposes ~80 columns from `BUSA_DOC_MSTR` plus joined CONT/USER/MEMO/COLOR/COMPANYSTATUS) |
| Search helper | `WECMS DEV/WECMS/src/wabooks/helper/SearchHelper.java#L89` (View-Setting-driven OR-clause assembly for `SRCH_WD`) |
| Enums | `WECMS DEV/WECMS/src/wabooks/enums/BusaDocType.java`, `WECMS DEV/WECMS/src/wabooks/enums/ProvStts.java` |
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts` |
| IDO XMLs | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.EINV_TAX_DETL_*.xml`, `IDO.EINV_FEE_DETL_*.xml` (CRUD set on the detail tables; `BUSA_DOC_MSTR` IDO is custom and not present under `IDO.EINV_*`) |
| Live DEV URL | `https://dev.wecms.vn/einv_0001_001.act?einvType=1` (Sales) / `?einvType=2` (Expenses) |

JEX `UPPER_SNAKE` field IDs from `WSVC.einv_0001_001_r001.xml` are the canonical contract — preserve verbatim in the refactor wire shape so traceability stays unbroken.

**Key JEX wire fields preserved 1:1 in the refactor (per `WSVC.einv_0001_001_r001.xml`):**

- Input: `BIZ_KEY`, `SRCH_WD`, `DOC_TYPE` (`'I'` Sales / `'B'` Bill), `FROM_DT`, `TO_DT`, `einv_invoiceStatus`, `PAGE_NO`, `PAGE_PER_CNT`, `einv_testResults`, `TRCO_TRSC_CCY_CD`, `ENV_SET_INF`, `PROV_STTS`
- Output (top-level): `END_DATE`, `TOT_CNT`, `GDT_CERT_SEQ`, `TOT_VND_CNT`, `TOT_USD_CNT`, `TOT_VND_AMT`, `TOT_USD_AMT`, `TOT_VND_SPLY_AMT`, `TOT_USD_SPLY_AMT`, `TOT_VND_TAX_AMT`, `TOT_USD_TAX_AMT`, plus per-status totals (`TOT_OP_INV_*`, `TOT_OD_INV_*`, `TOT_PA_INV_*`, `TOT_INV_*`)
- Output (`REC` row): `DOC_ID`, `ISSU_DT`, `TRCO_CMNM`, `TRCO_TRSC_CCY_CD`, `TOTL_AMT`, `BUSA_USAG_CD`, `USAG_NM`, `einv_invoiceStatus`, `COLOR`, `SPLY_AMT_SUM`, `TAX_AMT_SUM`, `TOTL_DIS_AMT`, `einv_numericSymbol`, `einv_invoiceSymbol`, `HAND_DOC_ID`, `TRCO_TAX_ID`, `DOC_TYPE`, `MEMO`, `PROV_STTS`, `NOTE`, `USER_NM`, `MOD_NM`, `APPR_SEQ_NO`, `RCV_SND_YN`, `DOC_NO`, `CON_DOC_ID`, `PRJT_NO`, `PRJT_NM`, `einv_invoicestatus_bef`, `CONT`, `TRCO_KEY`, `TRCO_NICK_NM`, `RCV_PAY_NOTE`, `BLTB_CD`, `POST_SEQ_NO`, `HAND_REG_YN`, `TRCO_PAYM_TERM`, `DUE_DT`, `OVER_DUE_DAYS`, `DISP_DOC_ID`, `READ_YN`, `COMPANYSTATUS`

The legacy WSVC does **NOT** declare `INV_TYPE` or `ISSU_TM` on the wire. `INV_TYPE` lives on the IDO output but is dropped at the WSVC contract; `ISSU_TM` simply does not exist on `BUSA_DOC_MSTR`. The refactor's `ISSU_TM` (sliced from `REG_DTM[8..14]`) is a **refactor-only proxy**, not legacy parity.

The current refactor `EInvoiceListResponse.EInvoiceRow` exposes only ~12 of those — the rest are TODO.

**DOC_TYPE — verified mapping** (per [`einv_0001_001_r001_act.jsp:268-284`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp) and [`BusaDocType.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/BusaDocType.java)):

| Wire enum | Legacy `DOC_TYPE` | `BusaDocType` constant | Internal `einvType` | Behaviour in `einv_0001_001_r001` |
|---|---|---|---|---|
| `SALES` | `'I'` | `INVOICE` | `"table1"` | ✅ accepted |
| `EXPENSE` (a.k.a. Bill) | **`'B'`** | `BILL` | `"table2"` | ✅ accepted |
| (n/a) | `'E'` | `EXPENSE` | — | ❌ rejected (`WBE0001`) |
| (n/a) | `'S'` | `PAYROLLBOOK` | — | ❌ rejected (`WBE0001`) |

The Spring service `EInvoiceService.resolveDocType` currently maps `EXPENSE → 'E'` — **incorrect**. Must be `EXPENSE → 'B'` for legacy parity. Same applies to the `BusaDocMstr.docType` Javadoc which says "'S' from 'E'". Code-side fix tracked in TODO.

---

## Table columns

Default columns and order (the eight columns the static UI ships today; `FIELD_ID`s proposed for the View Setting seed):

| `FIELD_ID` | Column | Source field (legacy `REC.*`) | Visible by default |
|---|---|---|---|
| `CHECKBOX` | row-select checkbox | client-side selection state | yes (sys-pinned, hidden in popover) |
| `DATE_TIME` | Date / Time | `ISSU_DT` (`YYYYMMDD`) — date only is legacy parity. The "time" sub-line shown today is a **refactor-only proxy** sliced from `REG_DTM[8..14]` (registration time, not issue time). | yes |
| `INVOICE_NO` | e-Invoice No. + status sub-line | `einv_invoiceSymbol` (e.g. `1C24TLM-0405`) + the localised `EINV_INVOICESTATUS` (e.g. `Original`). The static UI's `invoiceType` field is **misnamed** — it carries `EINV_INVOICESTATUS`, NOT the legacy `INV_TYPE` ('Commercial' / 'Tax'). | yes |
| `BUYER` | Buyer + MST + buyer status | `TRCO_CMNM` (or `TRCO_CMENM`) + `TRCO_TAX_ID` + `COMPANYSTATUS` | yes |
| `CURRENCY` | Currency | `TRCO_TRSC_CCY_CD` (VND / USD / KRW) | yes |
| `TOTAL` | Total amount | `TOTL_AMT` (formatted via shared `formatAmount`) | yes |
| `STATUS` | Invoice status | `EINV_INVOICESTATUS`. Badge tint comes from regex pattern matching on the localised status string (legacy `loadTextColorInvBillOut`) — **NOT** from `COLOR`. | yes |
| `TAX` | Tax amount | `TAX_AMT_SUM` | yes |
| `PRETAX` | Amount before tax | `SPLY_AMT_SUM` | yes |
| `ETC` | (not seeded — page has no per-row action column) | — | n/a |

**`COLOR` clarification** (verified against [`IDO.BUSA_DOC_MSTR_R022.xml:190`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml) and [`einv_0001_001.ts:1926-1927`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts)): `COLOR` is a per-document **memo sticker CSS class** resolved via `wb_get_memo_color(BIZ_KEY, 'einv_0001_001', DOC_ID, …)`. Length ≤ 5 characters; default value is `"white"`. It tags a separate sticker UI element (not the status column) and is **not** a hex color, **not** a status code, **not** GDT-supplied. The refactor listing-page scope can omit `COLOR` and `MEMO` entirely until memo functionality is in scope.

**`INV_TYPE` clarification** (verified against [`IDO.BUSA_DOC_MSTR_R022.xml:200`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml)): the legacy IDO computes `(CASE WHEN BDM.INV_TYPE = 'C' THEN 'Commercial' ELSE 'Tax' END) INV_TYPE` — a Commercial-vs-Tax classifier, NOT the Original/Replacement/Adjustment list. The legacy WSVC `<out>` does NOT expose `INV_TYPE` and the legacy TS does not render it. Refactor MUST NOT label any field "Original/Replacement/Adjustment" as `INV_TYPE` — those values live on `EINV_INVOICESTATUS`.

Special `FIELD_ID`s `CHECKBOX` / `ETC` are admin-controlled per [§13](../../../../wecms-web/CLAUDE.md) and locked server-side regardless of payload. Sales e-Invoices renders `CHECKBOX` (row selection is part of the future bank-matching workflow) but does **not** render `ETC`.

Column widths are governed by the existing `--w-wb-si-*` CSS tokens in [app/globals.css](../../../../wecms-web/app/globals.css) (`--w-wb-si-datetime`, `--w-wb-si-invoice-no`, `--w-wb-si-buyer`, `--w-wb-si-currency`, `--w-wb-si-total`, `--w-wb-si-status`, `--w-wb-si-tax`, `--w-wb-si-pretax`) and the table's `min-w-[var(--w-wb-si-table-min)]`. The shared colgroup lives in [`<EInvoiceTableColumns>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoiceTableColumns.tsx) and is reused by both the body table and the footer table per [§7a](../../../../wecms-web/CLAUDE.md).

---

## Filter behaviour

| Filter | UI today | Legacy contract (`einv_0001_001_r001`) | Refactor target |
|---|---|---|---|
| **Invoice status** | [`<InvoiceStatusFilter>`](../../../../wecms-web/components/addon/sales-einvoices/InvoiceStatusFilter.tsx) — dropdown trigger; only "All Invoice Status" is rendered today (no popover wired). The filter option list lists `all / Original / Replacement / Adjustment / Cancelled` (the static UI omits `Replaced` / `Adjusted` even though legacy supports them). | Wire field `einv_invoiceStatus` (length 500) — exact match against the **localised** status string. Legacy frontend always sends the **Vietnamese** form (`Hóa đơn mới`, `Hóa đơn thay thế`, …); the DB stores values that the IDO has resolved via `wb_get_invoice_status(value, LANG_CD)`. Empty / unset = no filter. | Server-side `invoiceStatus` query param. **Open**: refactor must decide whether to (a) port `wb_get_invoice_status` so the value is locale-resolved server-side, (b) translate frontend-side before the wire, or (c) match across all known synonyms. The popover commits the chosen logical status; whichever resolution path is taken applies on both filter and display. |
| **Date range** | [`<DateRangePicker>`](../../../../wecms-web/components/shared/DateRangePicker.tsx) shows a fixed `05-Jan-2025 → 30-Jan-2025` range — **display-only**, no real picker wired. | Wire fields `FROM_DT` / `TO_DT` (`YYYYMMDD`) — applied as `WHERE ISSU_DT BETWEEN ? AND ?`. **Both `FROM_DT` and `TO_DT` are required** by `validateInput` — the legacy listing rejects requests without a date range. | Server-side `from` / `to` query params (already accepted by [`EInvoiceController#list`](../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) but optional in the current Spring service). Add legacy-parity required-validation at the Spring service. Frontend wires the shared picker once the product-wide date range picker becomes interactive. |
| **Keyword search** | [`<SearchInput>`](../../../../wecms-web/components/shared/SearchInput.tsx) on the right side of the filter row — **client-side only**. Current implementation filters the loaded result set on `invoiceNo`, `buyerName`, `mstCode`, `invoiceStatus`, `invoiceType` (case-insensitive partial match, in [`<EInvoicesPage>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesPage.tsx) `useMemo`). | **View-Setting-driven** — `SearchHelper.getSearchColumnDynamic` walks the user's currently-visible View Setting columns and adds an OR-clause per visible column whose `FIELD_ID` is on the per-WSVC whitelist. See "Keyword search semantics" below. | Phase 2 ships a fixed Phase-2 subset of the legacy whitelist; full View-Setting coupling is a follow-up. Decide in Phase 2: (a) implement the legacy walk verbatim, (b) ship a fixed subset as the refactor's first iteration, or (c) hybrid. |
| **Invoice type** (was the static UI's "type" filter) | Folded into the status dropdown today — they read the same field. | Single field (`einv_invoiceStatus`). | Same. There is no separate "INV_TYPE" filter on legacy — `INV_TYPE` is `'Commercial'` / `'Tax'` and is not user-filterable on this listing. |

### Always-applied baseline filters (legacy parity — currently MISSING from the Spring skeleton)

The legacy `einv_0001_001_r001_act.jsp` adds these `WHERE` clauses on every list query, regardless of the user's filter input. The refactor MUST replicate them:

1. **`PROV_STTS NOT IN ('VD', 'EE')`** — drafts (`EE`) and cancelled/void (`VD`) rows are never visible in the listing. ([line 107-111 of the action JSP](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp))
2. **`HAND_REG_YN = 'N'`** — hand-registered (manually entered, not synced from GDT) rows are excluded. ([line 102-103](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp))
3. **`(DOC_ID, BIZ_KEY) IN (SELECT DOC_ID, BIZ_KEY FROM BUSA_DOC_DETL …)`** — invoices without any line item in `BUSA_DOC_DETL` are filtered out. ([line 105](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp))
4. **`PROV_STTS = 'OP'` filter (when explicitly requested)** — when `PROV_STTS=OP` is passed, the WHERE expands to `IN ('OP', 'PP', 'OD')` (open + partially paid + overdue). ([line 137-147](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp))

Today's `EInvoiceService` skips all four. **Implementation blocker** for legacy parity.

### Keyword search semantics (verified)

`SRCH_WD` on `einv_0001_001_r001` is **View-Setting-driven**, not a fixed column composite. Implemented in [`SearchHelper.java#L89-L193`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/SearchHelper.java). Steps:

1. The action JSP looks up the user's `WEB_SVC_ID`/`DYM_AREA_ID` View Setting rows (Sales = `'einv_0001_001'` or `'einv_0001_000'` depending on `PrdtType`; `dymId = 'table1'`).
2. Only rows with `USE_YN='Y'` participate.
3. For each visible column whose `FIELD_ID` matches the per-WSVC whitelist, the helper appends one OR-clause.

**Whitelist for Sales (`dymId = 'table1'`)** — 16 candidate `FIELD_ID`s:

| `FIELD_ID` | Predicate | Notes |
|---|---|---|
| `DOC_ID` | `DOC_ID ilike '%'||?||'%'` | Internal document id |
| `TRCO_CMNM` | `wb_get_translate_text(TRCO_CMNM) ilike '%'||wb_get_translate_text(?)||'%'` | Buyer name — locale-folded |
| `TRCO_NICK_NM` | `wb_get_translate_text(TRCO_NICK_NM) ilike '%'||wb_get_translate_text(?)||'%'` | Buyer nickname — locale-folded |
| `TOTL_AMT` | `TOTL_AMT = cast(? as NUMERIC)` | Numeric equality only when input is numeric |
| `DOC_TITLE` | `wb_get_translate_text(cont) ilike '%'||wb_get_translate_text(?)||'%'` | Note: queries `cont`, not `doc_title` — legacy quirk |
| `REG_NM` | `wb_get_translate_text(USER_NM) ilike '%'||wb_get_translate_text(?)||'%'` | Note: queries `USER_NM`, not `reg_nm` — legacy quirk |
| `CONT` | `wb_get_translate_text(CONT) ilike '%'||wb_get_translate_text(?)||'%'` | Description text |
| `NOTE` | `wb_get_translate_text(APPR_DOC_NO) ilike '%'||wb_get_translate_text(?)||'%'` | Note: queries `APPR_DOC_NO`, not `note` — legacy quirk |
| `PRJT_NM` | `wb_get_translate_text(PRJT_NM) ilike '%'||wb_get_translate_text(?)||'%'` | Project name |
| `USAG_NM` | `USAG_NM ilike '%'||?||'%'` | Usage name (no translate fold) |
| `DOC_NO` | `DOC_NO ilike '%'||?||'%'` | Display document number |
| `USER_NM` | `wb_get_translate_text(USER_NM) ilike '%'||wb_get_translate_text(?)||'%'` | User name |
| `TRCO_TAX_ID` | `TRCO_TAX_ID ilike ?` | **Exact match — no `%` wildcards** |
| `ISSU_DT` | `ISSU_DT = ?` | Date equality (after `StringUtils.forSearcDate` normalisation) |
| `TAX_AMT_SUM` | `TAX_AMT_SUM = cast(? as NUMERIC)` | Numeric equality only |
| `SPLY_AMT_SUM` | `SPLY_AMT_SUM = cast(? as NUMERIC)` | Numeric equality only |

**`EINV_INVOICESTATUS`, `EINV_INVOICESYMBOL`, and `INV_TYPE` are NOT searchable** in legacy. The static UI's "search by status / serial / invoice type" assumption is wrong.

`wb_get_translate_text` is a DB function for accent + case folding (Vietnamese-aware). It is **NOT in the local Flyway state**. Refactor must (a) port the function, (b) replicate folding logic in Java with `Collator` / `Normalizer`, or (c) accept different match results.

---

## Pagination behaviour

**Today (static UI):**

- `<EInvoicesClient>` computes `pageCount = ceil(totalCount / pageSize)` with `pageSize = 5` hardcoded and starts on `page = 1`.
- `totalCount` comes from `data.TOTAL_COUNT` (when the backend response carries it) or falls back to `rows.length`.
- The shared [`<Pagination>`](../../../../wecms-web/components/shared/Pagination.tsx) renders the chrome (page-size dropdown, prev/next arrows, page buttons, `{shown} of {total}` label) — but state is **read-only**; clicking page-size or arrows does not refetch / re-slice anything.
- No `usePagination()` hook adoption yet — the Sales e-Invoices page predates the [§4](../../../../wecms-web/CLAUDE.md) hook rule and stays on the un-migrated path. CLAUDE.md flags this for migration in the next stabilisation pass.

**Refactor target (mirror Account Inquiry VND server-side pagination):**

- Server-side: `page` (zero-based) and `size` query params on `/api/v1/addon/einvoices`.
- Default `size = 10` (canonical `PAGE_SIZE_OPTIONS[0]`), options `10 / 20 / 50 / 100`, backend clamps to `MAX_PAGE_SIZE = 100`. Match the legacy `PAGE_PER_CNT` semantics.
- Spring Data `Page<EInvoiceRow>` — `TOTAL_COUNT`, `TOTAL_PAGES`, `PAGE_NUMBER`, `PAGE_SIZE` flow back to the client.
- Per-currency totals stay stable across pagination — they are computed against the same `WHERE` chain unpaged (legacy `einv_0001_001_r001` returns `TOT_VND_*` / `TOT_USD_*` alongside paged `REC` rows).
- Page resets to 0 on every filter change (mirror Account Inquiry).
- Excel export bypasses pagination via `Pageable.unpaged()` so the spreadsheet contains every matching row.
- Client wrapper adopts `usePagination()` per [§4](../../../../wecms-web/CLAUDE.md) when migrated.

---

## Invoice status meaning

`EINV_INVOICESTATUS` is a **localised** string. The IDO ([`IDO.BUSA_DOC_MSTR_R022.xml:238`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml)) resolves it server-side via:

```sql
wb_get_invoice_status(coalesce(BDM.EINV_INVOICESTATUS, ''), :LANG_CD)
```

The same logical state therefore returns different strings depending on the user's `LANG_CD`. The frontend ([`einv_0001_001.ts:2788-2801`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts)) uses **regex pattern matching** that recognises the same logical state across vi / en / ko, applying a CSS class for badge tint.

**Six known logical statuses** (verified against the legacy TS `loadTextColorInvBillOut`):

| Logical status | Vietnamese (vi) | English (en) | Korean (ko) | Legacy TS class | Refactor token (proposed) |
|---|---|---|---|---|---|
| Original | `Hóa đơn mới` | `Original` | `세금계산서` | `cBlue` | `wb-status-progress` |
| Replacement (new replacement issued) | `Hóa đơn thay thế` | `Replacement` | `대체세금계산서` | `cGreen` | `wb-status-pending` |
| Adjustment (new adjustment issued) | `Hóa đơn điều chỉnh` | `Adjustment` | `수정세금계산서` | `cGreen` | `wb-status-pending` |
| Replaced (this row was replaced) | `Hóa đơn đã bị thay thế` | `Replaced` | `대체된 세금계산서` | `cGreen` | `wb-status-pending` |
| Adjusted (this row was adjusted) | `Hóa đơn đã bị điều chỉnh` | `Adjusted` | `수정 (전)세금계산서` | `cGreen` | `wb-status-pending` |
| Cancelled | `Hóa đơn đã bị hủy` | `Cancelled` | `취소된 세금계산서` | `cRed` | `wb-status-rejected` |

The static UI's invoice-status filter dropdown lists only 4 of these (`Original` / `Replacement` / `Adjustment` / `Cancelled`) — `Replaced` and `Adjusted` are missing from the static UI but exist on legacy.

**Implementation blocker — `wb_get_invoice_status` is NOT in the local Flyway state.** The refactor `EInvoiceService` reads `BUSA_DOC_MSTR.einvInvoicestatus` directly. Without the localisation function, the wire returns the raw stored value (in whatever language it was written). Phase 2 must pick one path:

1. Port `wb_get_invoice_status` to a Flyway migration so the value is locale-resolved server-side (legacy parity).
2. Replicate the regex pattern matching in Java and emit a stable canonical key on the wire (e.g. `ORIGINAL` / `REPLACEMENT` / `ADJUSTMENT` / `REPLACED` / `ADJUSTED` / `CANCELLED`); frontend translates via i18n.
3. Replicate the regex matching in the frontend (mirror legacy TS) and accept that the wire string varies by locale.

Sibling per-row fields:

- `EINV_INVOICESTATUS_BEF` — prior status when the row was replaced / adjusted; useful for tooltip / detail diff.
- `COLOR` — **NOT** the status badge colour. It is a per-document **memo sticker CSS class** (length ≤ 5; default `"white"`) resolved via `wb_get_memo_color(BIZ_KEY, 'einv_0001_001', DOC_ID, …)`. The status badge tint comes from regex matching of the localised `EINV_INVOICESTATUS` (legacy TS `loadTextColorInvBillOut`). For the refactor listing-page scope, `COLOR` and `MEMO` can be omitted entirely until memo functionality is in scope.

**Buyer-status tag** (separate field, rendered next to the MST code on the Buyer column):

| Value | Meaning |
|---|---|
| `Active` | Buyer's tax registration is in good standing. |
| `On closing` | Buyer is winding down; tax certificate close-out in progress. |
| `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục chấm dứt hiệu lực MST` | Buyer is suspended without having completed MST close-out. **Compliance flag** — input-VAT credit may be invalid. |
| (free-form Vietnamese text) | The GDT returns the live status string; the UI shows it verbatim and styles it `text-brand-hover` per the Figma spec. |

The MST status comes from `COMPANYSTATUS` on the legacy `REC` (joined from `OPRT_STTS_INFO` per the IDO). Refactor preserves the field name; the `<BuyerCell>` composition lives in [`<EInvoiceTable>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoiceTable.tsx).

---

## `PROV_STTS` vocabulary (verified)

`PROV_STTS` is the **local payment / settlement status** of the invoice (Korean "증빙상태" — voucher status). Tracked locally by WeCMS, NOT GDT-supplied. Verified against [`ProvStts.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/ProvStts.java) and [`einv_0001_001.ts:2806-2823`](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts):

| Code | Korean label | English meaning | Behaviour in `einv_0001_001_r001` |
|---|---|---|---|
| `OP` | 납기내 | Open (within term) | Visible · sub-tab "B" maps here · `PROV_STTS=OP` filter expands to `IN ('OP', 'PP', 'OD')` |
| `PP` | 부분수납 | Partially paid | Visible (folded into `OP` via the expansion above; no dedicated tab) |
| `PA` | 수납완료 | Paid in full | Visible · sub-tab "D" maps here |
| `OD` | 연체 | Overdue | Visible · sub-tab "C" maps here |
| `VD` | 취소 | Cancelled / void | **Excluded from list always** (baseline filter `NOT IN ('VD', 'EE')`) |
| `EE` | 임시저장 | Draft / temporary | **Excluded from list always** (baseline filter) |

The Phase 2 backend extension MUST add the always-applied `WHERE PROV_STTS NOT IN ('VD', 'EE')` baseline filter to `EInvoiceService.list`. Today's Spring service skips it. The `OP` filter expansion (from `=OP` to `IN ('OP','PP','OD')`) only kicks in when `PROV_STTS=OP` is explicitly passed AND the aggregation pass is being computed.

---

## Excel / Print / Update button behaviour

The header toolbar is the page-specific [`<SalesEInvoiceActionBar>`](../../../../wecms-web/components/addon/sales-einvoices/SalesEInvoiceActionBar.tsx) (Figma 9194:6859) — it diverges from the shared `<PageActionButtons>` because the design adds a chevron-dropdown on Excel and a raised "Update" CTA. CLAUDE.md notes the divergence is intentional.

| Button | Today (static UI) | Refactor target |
|---|---|---|
| **Print** | Icon button `/assets/common/ico-print.svg` at `size-5`. `onPrint` prop accepted; **no handler wired**. | Route through the shared [`printReport`](../../../../wecms-web/lib/print.ts) helper per [§6d](../../../../wecms-web/CLAUDE.md). Print source = the page's filtered, in-memory dataset (search applied) BEFORE pagination. Columns mirror the on-screen table minus checkbox + status badge (or render the badge as `<span>` text). Default landscape, mirrors legacy `einv_0001_001` print popup. |
| **Excel** (with chevron dropdown) | Icon `/assets/common/ico-excel.svg` + `<ChevronDown>` in a `bg-wb-bg-sidebar` chip. `onExport` prop accepted; **no handler wired**. The chevron dropdown content is not designed yet — defer until Figma confirms options (likely "Download visible columns" vs "Download all columns" mirroring the legacy `_r002_excel` service). | Route through the shared [`exportExcel`](../../../../wecms-web/lib/excel.ts) helper via [`useExcelExport()`](../../../../wecms-web/hooks/useExcelExport.ts) per [§13b](../../../../wecms-web/CLAUDE.md). New per-resource endpoint `POST /api/v1/addon/einvoices/export` (TODO — write `EInvoiceExportProvider implements ExportRowProvider`). `screenId = "addon-sales-einvoices"`. The chevron's "all columns" branch is a separate menu item that posts the same endpoint with `columns = []` to indicate "all allowed". |
| **Update** | Raised button with `<RotateCw>` icon + "Update" label, `bg-white` + `shadow-wb-btn`. `onUpdate` prop accepted; **no handler wired**. | "Update" = re-pull the live e-invoice list from the GDT (legacy NXISAS — `nxisas_einvoice_c001` / `c002` / `nxisas_einvhis_r001`). For the refactor R&D scope this is **deferred**; the local Spring Boot side has no GDT bridge. First step is to wire the button to a refresh of the local data fetch (`refreshKey++` → refetch `GET /api/v1/addon/einvoices`). Real GDT pull lands when (and if) the refactor adopts the NXISAS module. |

The chevron dropdown on Excel must NOT be confused with the row-action chevrons used elsewhere. The "Update" button is raised + brand-coloured and is the most visually prominent action — it implies a write-side server roundtrip (legacy fetches new e-invoices from GDT). Designs that drop the prominence are a regression.

---

## TODO — backend / API / DB / integration

### Backend (Spring Boot) — corrections + extensions

- [ ] **Fix `EInvoiceService.resolveDocType`** — change `EXPENSE → 'E'` to `EXPENSE → 'B'` (legacy parity per [`BusaDocType.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/BusaDocType.java) and [`einv_0001_001_r001_act.jsp:268-284`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp)). Legacy listing rejects `'E'` and `'S'`; Sales = `'I'`, Bill/Expense = `'B'`. Same fix for `BusaDocMstr.docType` Javadoc.
- [ ] **Add legacy-parity baseline filters** to `EInvoiceService.list` — currently MISSING:
   - `WHERE PROV_STTS NOT IN ('VD', 'EE')` (always; hides drafts + cancelled)
   - `WHERE HAND_REG_YN = 'N'` (always; hides hand-registered rows)
   - `WHERE (DOC_ID, BIZ_KEY) IN (SELECT DOC_ID, BIZ_KEY FROM BUSA_DOC_DETL)` (always; hides invoices without line items)
   - When `PROV_STTS=OP` is passed, expand to `IN ('OP', 'PP', 'OD')`
- [ ] **Make `from` / `to` required** on `EInvoiceController#list` — legacy `validateInput` rejects requests without a date range. Frontend must always supply.
- [ ] Extend `EInvoiceListResponse.EInvoiceRow` to carry every column the UI shows + the columns the View Setting popover may toggle on later. Today's row has 12 fields; the legacy `WSVC.einv_0001_001_r001.xml` `REC` carries 41. Prioritise: `COMPANYSTATUS`, `EINV_INVOICESTATUS_BEF`, `READ_YN`, `OVER_DUE_DAYS`, `DUE_DT`, `NOTE`, `PROV_STTS`, `RCV_SND_YN`, `HAND_REG_YN`, `DISP_DOC_ID`. **Drop** `COLOR` and `MEMO` from the priority list — they are memo-sticker concerns, not status concerns; defer until memo functionality is in scope.
- [ ] Add server-side `invoiceStatus` (legacy `einv_invoiceStatus`) and `keyword` (legacy `SRCH_WD`) query params on `EInvoiceController#list`. `from` / `to` already accepted.
- [ ] Decide on the localised `EINV_INVOICESTATUS` resolution — port `wb_get_invoice_status` to local Flyway, replicate regex in Java emitting canonical keys, or replicate regex frontend-side (see "Invoice status meaning" section).
- [ ] Add server-side pagination (`page` + `size`) — switch the repository call from `findByBizKeyAndDocTypeOrderByIssuDtDescRegDtmDesc` to a `Pageable` variant. Return `TOTAL_COUNT` / `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE` in `EInvoiceListResponse`.
- [ ] Add server-side sort (per [§13a](../../../../wecms-web/CLAUDE.md)) — write `EInvoiceSortField` whitelist enum (whitelist `DATE_TIME → issuDt+regDtm`, `INVOICE_NO → einvInvoicesymbol`, `BUYER → trcoCmnm`, `CURRENCY → trcoTrscCcyCd`, `TOTAL → totlAmt`, `STATUS → einvInvoicestatus`, `TAX → taxAmtSum`, `PRETAX → splyAmtSum`). Default sort stays `ISSU_DT DESC, REG_DTM DESC` (legacy parity).
- [ ] Compute the legacy `TOT_VND_*` / `TOT_USD_*` / per-status totals (`TOT_OP_INV_*`, `TOT_OD_INV_*`, `TOT_PA_INV_*`, `TOT_INV_*`) via a sibling aggregation query so they stay stable across pagination. Today the totals loop runs on the page slice only — wrong as soon as pagination lands.
- [ ] Decide the keyword-search strategy: (a) replicate legacy View-Setting-driven OR-clause walk, (b) ship a fixed-subset Phase-2 implementation, or (c) hybrid. Whichever lands, document `wb_get_translate_text` parity decision (port the function vs Java `Collator/Normalizer` vs accept divergence).
- [ ] Write the per-resource Excel export endpoint `POST /api/v1/addon/einvoices/export` + `EInvoiceExportProvider implements ExportRowProvider` per [§ Excel export](../../../CLAUDE.md). Allowed FIELD_IDs match the table column set. Subheader: `<count> invoice(s) · Total (VND): … · Total (USD): …`.
- [ ] Decide on the GDT-side "Update" semantics. R&D-only scope: refresh local data. Future: port `nxisas_einvoice_c001` / `c002` to a Spring Boot service (large surface — defer past the listing-page sprint).

### API contract

- [ ] Wire `EInvoiceListResponse` field names to UPPER_SNAKE per [API rules](../../../CLAUDE.md) and the legacy `WSVC.einv_0001_001_r001.xml` field IDs. Today the response uses lower-case-snake (`docId`, `issuDt`) — must flip to `DOC_ID`, `ISSU_DT`, etc. The frontend mapper [`mapEInvoiceRow`](../../../../wecms-web/lib/api/mappers.ts) absorbs that today; remove the lower→upper translation when the contract aligns.
- [ ] Document the API contract under [`docs/refactor-wecms/backend/addon/sales-e-invoices.md`](../../references/api-spec/addon/) (new file — TBD).
- [ ] Document the JEX → Spring service mapping under [`docs/refactor-wecms/migration/sales-e-invoices.md`](../../references/migration/) (new file — TBD; covers `einv_0001_001_r001` IDO → JPA repository, field-by-field with type / nullability deltas).

### Frontend (Next.js)

- [ ] Wire the invoice-status dropdown to a real popover (likely a shared `<FilterPopover>` component) that emits `einv_invoiceStatus` to the wrapper. Today only the trigger renders.
- [ ] Wire the date-range picker — shared product-wide, defer until [`<DateRangePicker>`](../../../../wecms-web/components/shared/DateRangePicker.tsx) becomes interactive (Approval Box / Transaction History track this).
- [ ] Adopt `usePagination()` per [§4](../../../../wecms-web/CLAUDE.md) on `<EInvoicesClient>` and remove the hardcoded `pageSize = 5`. Switch to server-side `page` + `size`.
- [ ] Adopt `useTableSort()` per [§13a](../../../../wecms-web/CLAUDE.md) on the wrapper; pass `sortField` / `sortDirection` / `toggleSort` to [`<EInvoiceTable>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoiceTable.tsx) and migrate the headers to `<TableHeaderCell sortFieldId=...>`.
- [ ] Adopt View Setting per [§13](../../../../wecms-web/CLAUDE.md) — declare a `COLUMN_REGISTRY` keyed by `FIELD_ID`, register `SCREEN_IDS.ADDON_SALES_EINVOICES = "addon-sales-einvoices"` in [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts), wire `useViewSetting(SCREEN_IDS.ADDON_SALES_EINVOICES, DEFAULT_AREA_ID)`, render the popover behind the (new) list-view toolbar icon.
- [ ] Wire the **Print** handler to the shared `printReport(...)` helper.
- [ ] Wire the **Excel** handler to the shared `useExcelExport()` hook (depends on the backend endpoint above).
- [ ] Wire the **Update** handler to a `refreshKey++` refetch (Phase 1) and later to the GDT-pull endpoint when it lands (Phase 4).
- [ ] Add KM (Khmer) translations to `locales/km.json` once the locale ships product-wide.
- [ ] Replace lower-case-snake mapper translation in [`lib/api/mappers.ts`](../../../../wecms-web/lib/api/mappers.ts) once the backend ships UPPER_SNAKE.

### DB / Flyway

- [ ] Seed `SYS_SCR_OPTION_WECMS` with the column set for `WEB_SVC_ID = 'addon-sales-einvoices'`, `DYM_AREA_ID = 'default'`. Same migration adds the `CHECKBOX` (`DISP_ORD = 0`, `FIX_YN = 'Y'`) special row. Do NOT seed `ETC` — the page has no per-row action column.
- [ ] Seed `SYS_SCR_OPTION_WECMS` with the **Figma WB365** column widths (the `SIZE` values) so the popover-driven width feature, when wired, matches the design tokens. Mirror the V34 pattern from Account Inquiry.
- [ ] Confirm `BUSA_DOC_MSTR` is in the [PROD Active Table List](../../../CLAUDE.md) (it is — confirmed in the Sprint 1 audit) and that the V15/V23 Flyway state matches DEV column-for-column (it does — V23 added 80 missing columns to bring `BUSA_DOC_MSTR` from 20 → 100 columns).
- [ ] No new tables required for the listing page. `EINV_TAX_DETL` / `EINV_FEE_DETL` are needed for the **detail page** (separate sprint).

### Integration

- [ ] **GDT live status** — the legacy `einv_invoiceStatus` and `COLOR` values come from polling the GDT via the NXISAS bridge (`nxisas_einvhis_r001`, `nxisas_einvhis_dt_r001`). The refactor R&D scope is **local-only** per project_refactoring_scope.md (memory: project_refactoring_scope) — no bank or tax-authority integration. The UI must render whatever status `BUSA_DOC_MSTR.EINV_INVOICESTATUS` carries today (snapshot from the last DEV NXISAS poll). Do **not** introduce a refactor-side GDT bridge for this listing page.
- [ ] **Bank-matching link** — selecting rows on this page is the entry point to the legacy bank-matching workflow (`busa-doc-rcv-pay-his`). Out of scope for the listing page — defer to the bank-matching feature spec.

### Open follow-ups (cross-cutting)

- [ ] Confirm with PO whether the wire enum should be `SALES` / `EXPENSE` (current Spring Boot) or the legacy `1` / `2` (`einvType`). The controller currently accepts both `SALES` / `I` and `EXPENSE` / `E` aliases; the `E` alias is **wrong** and should be replaced with `B` while the cleanup happens.
- [ ] Confirm whether the page should display **per-status totals** in the footer (legacy returns `TOT_OP_INV_*`, `TOT_OD_INV_*`, `TOT_PA_INV_*`, `TOT_INV_*`). Today's footer shows per-currency only.

**Resolved (no longer open):**

- ✅ `DOC_TYPE` value for Sales: `'I'` (verified in `einv_0001_001_r001_act.jsp` and `BusaDocType.java`). Bill/Expense: `'B'`. Drop the `'S'` and `'E'` candidates.
- ✅ `INV_TYPE` semantics: `'C'` = `Commercial`, otherwise `Tax` (verified in `IDO.BUSA_DOC_MSTR_R022.xml:200`). Original/Replacement/Adjustment are `EINV_INVOICESTATUS` values, not `INV_TYPE`.
- ✅ `PROV_STTS` vocabulary: 6 fixed codes (`OP`, `PP`, `PA`, `OD`, `VD`, `EE`) — verified in `ProvStts.java`.
- ✅ `ISSU_TM` legacy presence: legacy WSVC + IDO + TS do NOT expose any issue-time field. The refactor's `REG_DTM[8..14]` slice is a refactor-only proxy.
- ✅ `COLOR` semantics: per-doc memo sticker CSS class (default `"white"`) — NOT a status badge tint. Status badge tint comes from regex matching of `EINV_INVOICESTATUS`.
- ✅ Search column composite: View-Setting-driven, 16 candidate `FIELD_ID`s with per-column predicate variation. `EINV_INVOICESTATUS` is NOT searchable in legacy.

---

## Cross-references

- API contract (endpoints/DTOs): [`../../api-spec/addon/sales-e-invoices-api.md`](../../references/api-spec/addon/sales-e-invoices-api.md)
- DB schema (tables/columns): [`../../database/addon/sales-e-invoices-db.md`](../../references/database/addon/sales-e-invoices-db.md)
- Frontend page design (UI): [`../../../../wecms-web/docs/features/addon-service/sales-e-invoices.md`](../../../../wecms-web/docs/features/addon-service/sales-e-invoices.md)
- Task plan: [`../../planning/addon/sales-e-invoices-plan.md`](../../planning/addon/sales-e-invoices-plan.md)
- Migration mapping (service-level): [`../../migration/sales-and-expense-einvoices.md`](../../references/migration/sales-and-expense-einvoices.md)
- Sister feature: [`expenses-e-invoices.md`](expenses-e-invoices.md) — shares the page shell, differs by `type=EXPENSE` and the i18n prefix
- JEX feature spec (functional source of truth): not yet written — see `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml` and `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` directly

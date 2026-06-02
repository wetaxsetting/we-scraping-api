# Add-on Service > Expenses e-Invoices — Refactor Feature Spec

**Module:** Add-on Service
**Menu:** Expenses e-Invoices (inbound supplier invoices)
**Route:** [/addon/expenses-einvoices](../../../../wecms-web/app/(dashboard)/addon/expenses-einvoices/page.tsx)
**Refactor screen ID (proposed):** `addon-expenses-einvoices`
**JEX counterpart (legacy):** `einv_0001_001` with `einvType=2` (the same view+service drives Sales when `einvType=1` and Expenses when `einvType=2`). No JEX feature doc exists yet under [docs/jex/features/](../../../../docs/jex/features/).
**Status:** **Wired.** Page renders through [`<ExpensesEInvoicesPage>`](../../../../wecms-web/components/addon/expenses-einvoices/ExpensesEInvoicesPage.tsx) → [`<EInvoicesClient type="EXPENSE">`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesClient.tsx), which calls the same wired `GET /api/v1/addon/einvoices` (server-side `keyword` / `page` / `size` / `sort`) + `POST /api/v1/addon/einvoices/export` as Sales (verified in `wecms-web` 2026-06-02). The Spring Boot controller / service / `BusaDocMstr` entity are **shared with Sales and implemented end-to-end**. The one feature-specific open item is the backend `EInvoiceService.resolveDocType` `EXPENSE → 'E'` code bug (should be `'B'` — see Legacy reference below); the invoice-**status** filter remains client-side on both pages (backend IR-4 status filter not yet implemented).

JEX is **read-only reference**. This spec captures the refactor design; JEX behaviour is the functional source of truth.

> **Phase 2 implementation rules are inherited from the Sales spec** — see [Sales planning § Phase 2 implementation rules (locked decisions)](../../planning/addon/sales-e-invoices-plan.md#phase-2-implementation-rules-locked-decisions). Expenses adopts the same IR-1 through IR-9 rules; the only Expenses-specific deltas are listed under "Relationship with Sales" and "Filter behaviour" below. Backend / frontend implementation for Expenses lands behind Sales — once Sales is wired, the Expenses page is a thin variant flip via `type="EXPENSE"` + `translationPrefix="expenses_einvoices"`.

---

## Overview

Lists every e-Invoice the company has **received from its suppliers** (inbound / expenses side of `BUSA_DOC_MSTR`). Each row carries the invoice issue date, e-invoice serial, **supplier** (company name + Vietnamese tax-code MST + supplier status), currency, total amount, invoice status (`Original` / `Replacement` / `Adjustment` / `Replaced` / `Adjusted` / `Cancelled`), tax amount, and amount-before-tax. The table renders multi-currency totals in the footer (one row per currency seen in the result set, e.g. VND + USD simultaneously) and ships with print + Excel + Update controls in the header toolbar.

Sales (outbound, `DOC_TYPE='I'` ⇒ `BusaDocType.INVOICE`) and Expenses/Bill (inbound, `DOC_TYPE='B'` ⇒ `BusaDocType.BILL`) share one page shell — [`<EInvoicesPage>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesPage.tsx) — and only differ by `translationPrefix`, the `type` query param, and the data they render. The legacy listing service explicitly **rejects** `'E'` (`EXPENSE`) and `'S'` (`PAYROLLBOOK`) — those constants exist on `BusaDocType` but belong to other flows. Expenses is `'B'`, never `'E'`.

---

## Business purpose

Vietnamese tax law (Decree 123/2020 / Circular 78/2021) requires every legitimate B2B purchase to be supported by a digitally-signed e-invoice from the supplier, transmitted through the GDT (General Department of Taxation). On the **input-VAT** side, WeCMS is where the finance team:

1. Inspects every received e-invoice — date, serial number, supplier's tax-code (MST), GDT-side status, currency, gross / net / tax breakdown.
2. Reconciles supplier invoices against the bank-side payment (the screen feeds the bank-matching workflow downstream).
3. Spots compliance issues — supplier's MST status (`Active`, `On closing`, `NNT ngừng hoạt động …`) flags a supplier whose tax registration is suspended, which can **invalidate input-VAT credit** for the buyer (the company viewing this screen). This is the dominant compliance driver for the Expenses page — input-VAT credit denial is a direct P&L hit.
4. Exports / prints the list for monthly tax filings and audit trails.

The page is the **read-side** of the inbound e-invoice domain — receiving / replacing / adjusting / cancelling an invoice is handled by the supplier's system on the issuing side, and any local workflow (linking, hand-registering) is handled in the legacy JEX detail screens (`einv_0001_001_u001` etc.) — **out of scope for the refactor's Expenses listing page**.

---

## Current status — static UI only

- **UI:** complete and approved. The page is the Expenses sibling of one of the four canonical baselines listed in [wecms-web CLAUDE.md § Approved reference set](../../../../wecms-web/CLAUDE.md) — same shell as Sales, only `type="EXPENSE"` + `translationPrefix="expenses_einvoices"` differs.
- **Data:** the page calls [`fetchEInvoices("EXPENSE")`](../../../../wecms-web/lib/api/addon.ts) → `GET /api/v1/addon/einvoices?type=EXPENSE` from [`<EInvoicesClient>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesClient.tsx); when the backend response is empty, the table renders empty (no mock fallback). The static look the team uses for screenshots / Figma comparison comes from `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced).
- **Backend:** the same skeleton as Sales ([`EInvoiceController`](../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) + [`EInvoiceService`](../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java) + [`BusaDocMstr`](../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstr.java) entity + `BusaDocMstrRepository`). `EInvoiceService.resolveDocType` currently maps `EXPENSE → 'E'` — **WRONG**. Legacy parity requires `EXPENSE → 'B'` (`BusaDocType.BILL`). Until the bug is fixed, the Expenses page returns zero rows from any DEV database with realistic data, because the listing-table seeds carry `DOC_TYPE='B'`, never `'E'`. See TODO.
- **Filters / pagination / Update / Print / Excel:** identical to Sales and **wired** — `<EInvoicesClient>` sends server-side `keyword` / `page` / `size` / `sortField` / `sortDirection` to `GET /api/v1/addon/einvoices`; pagination metadata comes from the server (verified 2026-06-02). The **one** remaining client-side piece is the invoice-**status** filter (`visibleRows` post-filter, backend IR-4 status filter not yet implemented). Excel export **is** wired (`exportXlsx` → `POST /api/v1/addon/einvoices/export`, real blob download); the separate "Excel with Details" menu item is an intentional stub pending a detail endpoint.
- **i18n:** Expenses-side keys exist under the `expenses_einvoices.*` prefix in `locales/{en,ko,vi}.json`. KM is missing.

---

## Relationship with Sales e-Invoices

Sales and Expenses are **the same page shell** with two differences and one compliance bug:

| Concern | Sales | Expenses |
|---|---|---|
| Page route | `/addon/sales-einvoices` | `/addon/expenses-einvoices` |
| Wire `type` | `SALES` | `EXPENSE` |
| Backend `DOC_TYPE` | `'I'` (Invoice) | `'B'` (Bill) — **NOT `'E'`**; current Spring code is wrong |
| `BusaDocType` constant | `INVOICE` | `BILL` |
| Legacy `einvType` | `1` (`"table1"` in IDO) | `2` (`"table2"` in IDO) |
| Translation prefix | `sales_einvoices.*` | `expenses_einvoices.*` |
| Counterparty column label | **Buyer** (`TRCO_CMNM` of the buyer) | **Supplier** (`TRCO_CMNM` of the supplier) |
| Compliance angle | Output VAT, GDT issuance to buyer | Input VAT credit, GDT MST status of supplier |
| Refactor screen ID | `addon-sales-einvoices` | `addon-expenses-einvoices` |

Everything else — page chrome, table colgroup, footer, filter row, toolbar, multi-currency totals, status localisation, View Setting integration, Excel export — is **identical** by construction (the shared [`<EInvoicesPage>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesPage.tsx) component owns every layout decision; Sales/Expenses cannot drift apart unless the shared shell is forked, which is forbidden per [§7a](../../../../wecms-web/CLAUDE.md)).

The Phase 2 implementation rules locked for Sales (IR-1 through IR-9 in the [Sales planning doc](../../planning/addon/sales-e-invoices-plan.md#phase-2-implementation-rules-locked-decisions)) apply verbatim to Expenses, with these column-label substitutions:

- `BUYER` `FIELD_ID` → seeded as `BUYER` for Sales, **also as `BUYER`** for Expenses' View Setting seed (the underlying entity field `TRCO_CMNM` is the same; the screen-level localisation flips the rendered header from "Buyer" to "Supplier"). Frontend `headerKey` resolves via the `expenses_einvoices.*` prefix. Open question: PO may prefer `SUPPLIER` as the `FIELD_ID` for clarity in the seed migration — see TODO.

---

## Scope

**In scope (refactor goal):**
- Read-only list of Expenses e-Invoices (`DOC_TYPE='B'`) for the current tenant
- Issue date (legacy `ISSU_DT`); the static UI's "time" sub-line is a **refactor-only proxy** sliced from `REG_DTM[8..14]` — same caveat as Sales
- Invoice serial (`EINV_INVOICESYMBOL`) and the status sub-line (`EINV_INVOICESTATUS`)
- **Supplier** block: company name (`TRCO_CMNM`), MST code (`TRCO_TAX_ID`), supplier status (free-form text returned by GDT, sourced from `COMPANYSTATUS`)
- Currency · Total amount · Invoice status · Tax amount · Amount-before-tax
- Multi-currency footer totals — same sibling-table pattern as Sales
- Filter row: invoice-status dropdown · date-range picker · keyword search
- Pagination row (mirrors Sales)
- Toolbar actions: Print · Excel (with chevron dropdown) · Update. **No View Setting / list-view icon** — see IR-9 in the Sales planning doc; the same rule applies to Expenses (Figma toolbar is Print + Excel + Update only).
- View Setting **column metadata + Excel export integration** — global feature per [§13](../../../../wecms-web/CLAUDE.md), keyed by `WEB_SVC_ID = addon-expenses-einvoices` (seed pending). The user-facing `<ViewSettingPopover>` is NOT rendered (per IR-9).
- Excel export — global feature per [§13b](../../../../wecms-web/CLAUDE.md), via the per-resource endpoint `POST /api/v1/addon/einvoices/export` (shared with Sales — the request body's `type` discriminator selects the Sales / Expenses slice on the backend; **wired** — frontend `exportXlsx` → real blob download, verified 2026-06-02). The separate "Excel with Details" menu item remains a stub pending a detail endpoint.

**Out of scope (handled elsewhere or later):**
- Hand-registering / linking inbound invoices — the legacy `einv_0001_001_u001` Expenses flow is **not** ported as part of this listing page
- Per-invoice detail page (line-items, tax breakdown by `EINV_TAX_DETL`, fee detail by `EINV_FEE_DETL`) — separate sprint
- GDT live status sync (the live `GDT_CERT_INFO` polling that drives `einv_invoiceStatus`) — relies on the legacy NXISAS bridge
- Bank-matching workflow — separate feature (`bank-matching-payment` for the inbound side)
- Sales e-Invoices — separate feature spec ([sales-e-invoices.md](sales-e-invoices.md))
- Smart Organizer / OCR upload — Add-on Service has separate entry points
- Print template (legacy popup HTML) — refactor uses the shared [`printReport`](../../../../wecms-web/lib/print.ts) helper per [§6d](../../../../wecms-web/CLAUDE.md), not the legacy print path

---

## Legacy reference (JEX)

| Asset | Path |
|---|---|
| JEX list service ID (read) | `einv_0001_001_r001` (shared with Sales — branches on `einvType=1\|2` → `DOC_TYPE='I'\|'B'`) |
| JEX page service ID (view) | `einv_0001_001` (the same view drives Sales when `einvType=1` and Expenses when `einvType=2`) |
| WSVC (read) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml` |
| WSVC (page) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001.xml` |
| WSVC (excel export) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r002_excel.xml` |
| Action JSP (read) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp` (validates and dispatches; branches on `einvType` — Expenses = `"table2"` → `DOC_TYPE='B'`) |
| IDO (paged read) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml` |
| Search helper | `WECMS DEV/WECMS/src/wabooks/helper/SearchHelper.java#L89` (whitelist for `dymId='table2'` is the Expenses search whitelist — see Filter behaviour) |
| Enums | `WECMS DEV/WECMS/src/wabooks/enums/BusaDocType.java`, `WECMS DEV/WECMS/src/wabooks/enums/ProvStts.java` |
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts` |
| Live DEV URL | `https://dev.wecms.vn/einv_0001_001.act?einvType=2` |

JEX `UPPER_SNAKE` field IDs from `WSVC.einv_0001_001_r001.xml` are the canonical contract for both Sales and Expenses — the wire shape is identical, only the `DOC_TYPE` filter differs.

**DOC_TYPE — verified mapping** (per `einv_0001_001_r001_act.jsp:268-284` and `BusaDocType.java`):

| Wire enum | Legacy `DOC_TYPE` | `BusaDocType` constant | Internal `einvType` | Behaviour in `einv_0001_001_r001` |
|---|---|---|---|---|
| `SALES` | `'I'` | `INVOICE` | `"table1"` | ✅ accepted |
| **`EXPENSE`** (a.k.a. Bill) | **`'B'`** | **`BILL`** | **`"table2"`** | **✅ accepted** |
| (n/a) | `'E'` | `EXPENSE` | — | ❌ rejected (`WBE0001`) |
| (n/a) | `'S'` | `PAYROLLBOOK` | — | ❌ rejected (`WBE0001`) |

The Spring service `EInvoiceService.resolveDocType` currently maps `EXPENSE → 'E'` — **incorrect, blocks the Expenses page**. Must be `EXPENSE → 'B'` for legacy parity. Same applies to the `BusaDocMstr.docType` Javadoc which says "'S' from 'E'". Code-side fix tracked in TODO and in the Sales planning doc (IR-1) — the fix lands once and unblocks both pages.

---

## Table columns

Default columns and order — identical to Sales except the **Buyer column is relabelled "Supplier"** at the localisation layer. Underlying field stays `TRCO_CMNM` (the counterparty's company name).

| `FIELD_ID` | Column (Expenses label) | Source field (legacy `REC.*`) | Visible by default |
|---|---|---|---|
| `CHECKBOX` | row-select checkbox | client-side selection state | yes (sys-pinned, hidden in popover) |
| `DATE_TIME` | Date / Time | `ISSU_DT` (`YYYYMMDD`) — refactor-only "time" proxy from `REG_DTM[8..14]` | yes |
| `INVOICE_NO` | e-Invoice No. + status sub-line | `einv_invoiceSymbol` + localised `EINV_INVOICESTATUS` | yes |
| `BUYER` | **Supplier** + MST + supplier status | `TRCO_CMNM` + `TRCO_TAX_ID` + `COMPANYSTATUS` | yes |
| `CURRENCY` | Currency | `TRCO_TRSC_CCY_CD` | yes |
| `TOTAL` | Total amount | `TOTL_AMT` | yes |
| `STATUS` | Invoice status | `EINV_INVOICESTATUS` (regex-derived badge tint) | yes |
| `TAX` | Tax amount | `TAX_AMT_SUM` | yes |
| `PRETAX` | Amount before tax | `SPLY_AMT_SUM` | yes |
| `ETC` | (not seeded — page has no per-row action column) | — | n/a |

The `BUYER` `FIELD_ID` is intentionally reused (rather than introducing `SUPPLIER`) because the underlying entity column is the same and the seed PK is `(WEB_SVC_ID, FIELD_ID)` — collisions across screens are not a problem. Header text comes from `t("expenses_einvoices.col.supplier")`. **Open question** for the seed migration MR: PO may prefer `SUPPLIER` for clarity — see TODO. If PO chooses `SUPPLIER`, the seed migration uses that string and the column registry constant flips correspondingly; the entity field stays `TRCO_CMNM`.

`COLOR` / `INV_TYPE` / `CHECKBOX` / `ETC` semantics are identical to Sales — see the [Sales spec § Table columns](sales-e-invoices.md#table-columns) for the full clarification (memo-sticker class vs. status badge tint, Commercial/Tax classifier vs. EINV_INVOICESTATUS, admin-controlled special FIELD_IDs).

Column widths reuse the same `--w-wb-si-*` CSS tokens and shared `<EInvoiceTableColumns>` colgroup as Sales — Expenses cannot fork the colgroup per [§7a](../../../../wecms-web/CLAUDE.md).

---

## Filter behaviour

Identical wire contract to Sales — see [Sales spec § Filter behaviour](sales-e-invoices.md#filter-behaviour) for the full breakdown. Expenses-specific deltas:

| Filter | Today (static UI) | Refactor target | Expenses delta vs. Sales |
|---|---|---|---|
| **Invoice status** | `<InvoiceStatusFilter>` trigger only | Server-side `invoiceStatus` query param; same six logical states (Original / Replacement / Adjustment / Replaced / Adjusted / Cancelled); same localisation strategy (IR-4 — frontend regex maps stored value to canonical key + badge tint) | None — same vocabulary on both sides |
| **Date range** | Display-only `<DateRangePicker>` | Server-side `from` / `to` query params, **required** per IR-3 (`YYYYMMDD`, `from ≤ to`) | None |
| **Keyword search** | Client-side `useMemo` filter on `invoiceNo`, `buyerName`, `mstCode`, `invoiceStatus`, `invoiceType` | Server-side `keyword` query param. **Phase 2 ships the same fixed 5-column subset as Sales** per IR-5: `DOC_ID`, `TRCO_CMNM`, `TRCO_TAX_ID`, `EINV_INVOICESYMBOL`, `DOC_NO`. (Legacy whitelist for `dymId='table2'` is the same shape as `'table1'`; if it diverges in any column the difference is documented in the same IR-5 note as the Sales gap.) | None expected — but VERIFY against `SearchHelper.java` for `'table2'` before the seed migration lands |
| **Invoice type** (legacy `INV_TYPE`) | Folded into status dropdown | Same | None |

### Always-applied baseline filters (legacy parity — same four as Sales, per IR-2)

`EInvoiceService.list` MUST add **all four** legacy-parity filters before any user-supplied filter is appended (the underlying `_act.jsp` is shared with Sales — only `DOC_TYPE` differs):

```sql
WHERE BIZ_KEY = :tenantBizKey
  AND DOC_TYPE = 'B'                         -- per IR-1; NOT 'E'
  AND PROV_STTS NOT IN ('VD', 'EE')          -- always; hides drafts + cancelled
  AND HAND_REG_YN <> 'Y'                     -- always; hides hand-registered rows
  AND EXISTS (
        SELECT 1 FROM BUSA_DOC_DETL d
        WHERE d.DOC_ID = m.DOC_ID
          AND d.BIZ_KEY = m.BIZ_KEY
      )                                       -- always; hides invoices with no line items
  AND ISSU_DT BETWEEN :fromDt AND :toDt      -- required (IR-3)
```

The Spring service today skips all four — **same blocker as Sales**, same fix MR.

### Keyword search semantics

The legacy whitelist is keyed by `dymId` — `'table1'` for Sales, `'table2'` for Expenses. If the two whitelists differ in any column, that difference is the only Expenses-specific filter behaviour deviation. Verify against [`SearchHelper.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/SearchHelper.java) before the Phase 2 implementation MR. Sales spec assumes parity (16 candidate FIELD_IDs); document the actual Expenses set when verified.

---

## Pagination behaviour

Identical to Sales — see [Sales spec § Pagination behaviour](sales-e-invoices.md#pagination-behaviour). Server-side `page` + `size`, default `size=10`, options `10/20/50/100`, max `100`. Per-currency totals computed against the full filtered dataset, not the page slice (IR-6). Page resets to 0 on every filter change.

---

## Invoice status meaning

Identical to Sales. `EINV_INVOICESTATUS` carries a localised string with six logical states (Original / Replacement / Adjustment / Replaced / Adjusted / Cancelled), resolved via `wb_get_invoice_status(value, LANG_CD)` server-side in legacy. Phase 2 returns the raw stored value; frontend regex maps to canonical key + badge tint (IR-4). See [Sales spec § Invoice status meaning](sales-e-invoices.md#invoice-status-meaning) for the full vocabulary table.

**Supplier-status tag** (analogous to Sales' Buyer-status tag, rendered next to the MST code on the Supplier column):

| Value | Meaning |
|---|---|
| `Active` | Supplier's tax registration is in good standing. |
| `On closing` | Supplier is winding down; tax certificate close-out in progress. |
| `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục chấm dứt hiệu lực MST` | Supplier is suspended without having completed MST close-out. **Compliance flag — input-VAT credit may be invalid for invoices issued by this supplier.** This is the dominant Expenses-side compliance signal. |
| (free-form Vietnamese text) | The GDT returns the live status string; the UI shows it verbatim. |

The MST status comes from `COMPANYSTATUS` on the legacy `REC` (joined from `OPRT_STTS_INFO` per the IDO). Refactor preserves the field name; the supplier-cell composition reuses the shared `<BuyerCell>` component in [`<EInvoiceTable>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoiceTable.tsx) — only the column header text differs between Sales and Expenses.

---

## `PROV_STTS` vocabulary

Identical to Sales — six fixed codes (`OP`/`PP`/`PA`/`OD`/`VD`/`EE`); listing always excludes `VD` and `EE`; `PROV_STTS=OP` filter expands to `IN ('OP','PP','OD')`. See [Sales spec § PROV_STTS vocabulary](sales-e-invoices.md#prov_stts-vocabulary-verified).

---

## Excel / Print / Update button behaviour

Identical to Sales. The toolbar uses the same page-specific `<...EInvoiceActionBar>` pattern (Figma 9194:6859 / sibling node) — Print · Excel-with-chevron · Update. None of the handlers are wired today.

| Button | Today | Refactor target | Expenses delta |
|---|---|---|---|
| **Print** | No handler | Shared `printReport(...)` per [§6d](../../../../wecms-web/CLAUDE.md). Source = page's filtered, in-memory dataset BEFORE pagination. Columns = on-screen table minus checkbox; `BUYER` header rendered as "Supplier". | Header label only |
| **Excel** | **Wired** — `exportXlsx` → real blob download (verified 2026-06-02); "Excel with Details" menu item is a stub | Shared `useExcelExport()` per [§13b](../../../../wecms-web/CLAUDE.md). Endpoint `POST /api/v1/addon/einvoices/export` shared with Sales; request body carries `type=EXPENSE`. `screenId="addon-expenses-einvoices"`. | `type` + `screenId` differ |
| **Update** | No handler | Phase 1: `refreshKey++` refetch. Phase 2: deferred GDT-pull endpoint (legacy `nxisas_einvoice_c001` / `c002`). | None |

---

## TODO — backend / API / DB / integration

### Backend (Spring Boot) — corrections + extensions

- [ ] **Fix `EInvoiceService.resolveDocType`** — change `EXPENSE → 'E'` to `EXPENSE → 'B'` (per IR-1 in the Sales planning doc). **This is the single most important blocker for the Expenses page** — without the fix, the listing returns zero rows from any DEV database with realistic data. Same fix unblocks Sales (the Sales mapping `SALES → 'I'` is already correct, but the controller's `'E'` alias must also be dropped while cleaning up). Add a unit test on `resolveDocType` asserting `EXPENSE → 'B'` and `'E' → reject`.
- [ ] **Verify the Expenses keyword-search whitelist** against `SearchHelper.java` for `dymId='table2'`. Phase 2 ships the 5-column subset (per IR-5) — confirm the columns chosen still make sense for Expenses (in particular, `TRCO_CMNM` carries the supplier name on Expenses rows; the column choice does not change).
- [ ] All other Phase 2 backend tasks are inherited from the Sales planning doc (baseline filters, required date range, response shape extension, server-side pagination + sort, multi-currency aggregation query, Excel export endpoint). The Expenses page does not need its own backend work — the shared `GET /api/v1/addon/einvoices` (server-side) + `POST /api/v1/addon/einvoices/export` are **implemented** and the Expenses frontend already consumes the same wired `<EInvoicesClient>` (verified 2026-06-02), sharing one controller + service + repository + export provider. The only feature-specific open item is the backend `EInvoiceService.resolveDocType` `EXPENSE → 'E'` code bug (should be `'B'`, see below).

### API contract

- [ ] Wire `EInvoiceListResponse` field names to UPPER_SNAKE per the Sales spec — same flip; one MR covers both pages.
- [ ] Document the API contract under [`docs/refactor-wecms/backend/addon/expenses-e-invoices.md`](../../references/api-spec/addon/) — mirror the Sales doc with the `type=EXPENSE` / `DOC_TYPE='B'` substitutions; do not duplicate verbatim. Reference the Sales doc for the full wire shape and link only the Expenses-specific deltas (the same handful documented above: column label, `type` discriminator, supplier compliance angle).

### Frontend (Next.js)

- [ ] Wire the invoice-status dropdown — same shared `<FilterPopover>` as Sales. No per-page work for Expenses beyond confirming the dropdown options carry the Expenses i18n keys (`expenses_einvoices.status.*`).
- [ ] Wire the date-range picker — depends on the product-wide picker, no Expenses-specific work.
- [ ] Adopt `usePagination()` per [§4](../../../../wecms-web/CLAUDE.md). Shared with Sales.
- [ ] Adopt `useTableSort()` per [§13a](../../../../wecms-web/CLAUDE.md). Shared with Sales.
- [ ] Adopt View Setting per [§13](../../../../wecms-web/CLAUDE.md) and IR-9: declare a `COLUMN_REGISTRY` at the page level (Expenses uses the same columns as Sales — the registry can be exported once and reused by both pages, threaded with the `expenses_einvoices.*` `headerKey` prefix), register `SCREEN_IDS.ADDON_EXPENSES_EINVOICES = "addon-expenses-einvoices"` in [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts), wire `useViewSetting(SCREEN_IDS.ADDON_EXPENSES_EINVOICES, DEFAULT_AREA_ID)`. **Do NOT render `<ViewSettingPopover>`** — the toolbar stays Print + Excel + Update only.
- [ ] Wire the Print / Excel / Update handlers — same shared helpers as Sales; only `screenId` and the column header for `BUYER` differ.
- [ ] Add KM (Khmer) translations to `locales/km.json` once the locale ships product-wide.
- [ ] Replace lower-case-snake mapper translation in [`lib/api/mappers.ts`](../../../../wecms-web/lib/api/mappers.ts) once the backend ships UPPER_SNAKE — same flip as Sales.
- [ ] Decide PO question on `BUYER` vs `SUPPLIER` `FIELD_ID` (see TODO under "Table columns" above).

### DB / Flyway

- [ ] **Validate Expenses data exists in DEV.** Confirm `BUSA_DOC_MSTR` for `BIZ_KEY=VN_VNM0004_000001` carries rows with `DOC_TYPE='B'`. The local seed today is heavily Sales-biased; if Expenses rows are sparse or absent, demos and stakeholder reviews will show an empty table. If sparse, follow the [Data Migration Rule](../../../CLAUDE.md) and copy real DEV rows for `DOC_TYPE='B'` into a `V{N}__addon_expenses_einvoices_demo_seed.sql` migration (max 30 rows). Never seed fake data.
- [ ] Seed `SYS_SCR_OPTION_WECMS` with the column set for `WEB_SVC_ID = 'addon-expenses-einvoices'`, `DYM_AREA_ID = 'default'`. Same column set as Sales — `CHECKBOX` (`DISP_ORD=0`, `FIX_YN='Y'`), `DATE_TIME`, `INVOICE_NO`, `BUYER` (or `SUPPLIER` per PO decision), `CURRENCY`, `TOTAL`, `STATUS`, `TAX`, `PRETAX`. Use Figma WB365 widths in `SIZE`. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. Bundle in the same migration as the Sales seed when possible.
- [ ] No new tables required for the listing page. `EINV_TAX_DETL` / `EINV_FEE_DETL` are needed for the **detail page** (separate sprint).

### Integration

- [ ] **GDT live status** — same constraint as Sales. The refactor R&D scope is local-only — do not introduce a refactor-side GDT bridge.
- [ ] **Bank-matching link** — selecting rows on this page is the entry point to the legacy bank-matching workflow on the **payment** side (`busa-doc-rcv-pay-his` for outbound payments to suppliers). Out of scope for the Expenses listing page — defer to the bank-matching feature spec.

### Open follow-ups (Expenses-specific)

- [ ] Confirm with PO whether the `BUYER` `FIELD_ID` should be renamed to `SUPPLIER` for the Expenses View Setting seed. Affects the seed PK row, the column registry constant, the Excel export `columnConfig` key, and any cross-screen analytics that group by `FIELD_ID`. Not a technical blocker — both spellings work — but a clarity call worth making before the seed migration lands.
- [ ] Confirm whether the Expenses page should ever expose **per-supplier-status totals** in the footer (a compliance-driven aggregation, e.g. "Total of invoices from suspended-MST suppliers: VND X"). Legacy does not return this; Sales does not show it; but the Expenses compliance angle is stronger so PO may want it. Today's footer shows per-currency only.
- [ ] Verify the **Expenses keyword-search whitelist** in `SearchHelper.java` for `dymId='table2'`. If the legacy whitelist for Expenses differs from Sales (`'table1'`), document the delta here and in the Sales planning doc IR-5 note.

**Resolved (no longer open):**

- ✅ `DOC_TYPE` value for Expenses: `'B'` (`BusaDocType.BILL`), NOT `'E'`. Verified in `einv_0001_001_r001_act.jsp` and `BusaDocType.java`.
- ✅ Page shell sharing with Sales — confirmed via `<EInvoicesClient>` reuse.
- ✅ Compliance angle (input-VAT credit, supplier MST status) — documented above; same `COMPANYSTATUS` field as Sales.

---

## Cross-references

- API contract (endpoints/DTOs): [`../../api-spec/addon/expenses-e-invoices-api.md`](../../references/api-spec/addon/expenses-e-invoices-api.md)
- DB schema (tables/columns): [`../../database/addon/expenses-e-invoices-db.md`](../../references/database/addon/expenses-e-invoices-db.md)
- Frontend page design (UI): [`../../../../wecms-web/docs/features/addon-service/expenses-e-invoices.md`](../../../../wecms-web/docs/features/addon-service/expenses-e-invoices.md)
- Task plan: [`../../planning/addon/expenses-e-invoices-plan.md`](../../planning/addon/expenses-e-invoices-plan.md)
- Migration mapping (service-level): [`../../migration/sales-and-expense-einvoices.md`](../../references/migration/sales-and-expense-einvoices.md)
- Sister feature: [sales-e-invoices.md](sales-e-invoices.md) — shares the page shell, the backend skeleton, and most Phase 2 implementation rules
- JEX feature spec (functional source of truth): not yet written — see `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml` and `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` directly (legacy `einvType=2` slice)

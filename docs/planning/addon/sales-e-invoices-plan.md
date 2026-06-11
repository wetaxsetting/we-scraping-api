# Add-on Service > Sales e-Invoices — Refactor Implementation Plan

**Feature spec:** [../../features/addon-service/sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md)
**Module:** Add-on Service
**Route:** `/addon/sales-einvoices`
**JEX counterpart:** `einv_0001_001` (view) / `einv_0001_001_r001` (read service) — `DOC_TYPE='I'` slice of `BUSA_DOC_MSTR`
**Owner:** TBD (Refactor team — Sonny + Arthur)
**Sprint:** TBD (post-Sprint 1 stabilisation)

---

## Phase 1 — UI completed (current state)

**Status:** ✅ Done (static UI; one of the four canonical baselines per the wecms-web CLAUDE.md "Approved reference set").

**What landed:**

- Page route at [`app/(dashboard)/addon/sales-einvoices/page.tsx`](../../../../wecms-web/app/(dashboard)/addon/sales-einvoices/page.tsx).
- Page shell [`<EInvoicesPage>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesPage.tsx) shared with the Expenses sibling — only differs by `type` + `translationPrefix`.
- Sales-specific entry [`<SalesEInvoicesPage>`](../../../../wecms-web/components/addon/sales-einvoices/SalesEInvoicesPage.tsx) → wraps [`<EInvoicesClient>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesClient.tsx) with `type="SALES"`.
- 8-column data table [`<EInvoiceTable>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoiceTable.tsx) + sibling-table multi-currency footer [`<EInvoiceTableFooter>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoiceTableFooter.tsx) sharing the colgroup [`<EInvoiceTableColumns>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoiceTableColumns.tsx) — pattern from CLAUDE.md §7a (`paginationWithTotals`).
- Filter row: [`<InvoiceStatusFilter>`](../../../../wecms-web/components/addon/sales-einvoices/InvoiceStatusFilter.tsx) (trigger-only, no popover) + [`<DateRangePicker>`](../../../../wecms-web/components/shared/DateRangePicker.tsx) (display-only) + [`<SearchInput>`](../../../../wecms-web/components/shared/SearchInput.tsx) (client-side filter).
- Toolbar [`<SalesEInvoiceActionBar>`](../../../../wecms-web/components/addon/sales-einvoices/SalesEInvoiceActionBar.tsx) (Figma 9194:6859) with Print + Excel-with-chevron + Update CTA.
- Static seed data in `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced).
- i18n keys under `sales_einvoices.*` prefix in `locales/{en,ko,vi}.json`. KM missing.
- HTTP fetch path wired to `GET /api/v1/addon/einvoices?type=SALES` via [`fetchEInvoices`](../../../../wecms-web/lib/api/addon.ts) + [`mapEInvoiceRow`](../../../../wecms-web/lib/api/mappers.ts) — Sprint 1 stabilisation removed the mock fallback so the table renders empty when the backend response is empty.

**What's still missing on the UI side** (carry into Phase 4):

- Invoice-status filter popover (the trigger renders, but the option list does nothing).
- Date-range picker is display-only — product-wide; tracked alongside Approval Box / Transaction History.
- Pagination state is presentational — page-size dropdown and arrows do nothing.
- Sorting is presentational — header glyph renders, click does nothing.
- View Setting popover — page is **not** registered in [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts) yet; the toolbar has no list-view icon.
- Print / Excel / Update buttons accept handlers but none are wired.

---

## Phase 2 — Backend API design

**Status:** 🟡 Skeleton only.

**What exists:**

- [`EInvoiceController#list`](../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) — `GET /api/v1/addon/einvoices` accepts `type` (`SALES`/`EXPENSE`), `from` (`YYYYMMDD`), `to` (`YYYYMMDD`).
- [`EInvoiceService#list`](../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java) — reads `BUSA_DOC_MSTR` filtered by `BIZ_KEY` + `DOC_TYPE` (`'I'` for Sales — correct), optional `ISSU_DT` range, sort `ISSU_DT DESC, REG_DTM DESC`. **Bug:** the service maps `EXPENSE → 'E'` — should be `EXPENSE → 'B'` (`BusaDocType.BILL`) per the legacy. The Javadoc and `BusaDocMstr.docType` Javadoc both repeat the same wrong assumption.
- DTOs `EInvoiceListResponse` + `EInvoiceRow` + `CurrencyTotal` — mismatched to the legacy `WSVC.einv_0001_001_r001.xml` `REC` (41 fields) and to the static UI's expected column set. **Wire shape is lowercase `snake_case`** — these records carry **no** `@JsonProperty` and serialize via the global Jackson `SNAKE_CASE` strategy; the frontend in-memory shape is camelCase, translated once in `mapEInvoiceRow`. *(Corrected 2026-06-06: the earlier "wire is UPPER_SNAKE via `@JsonProperty` (verified)" line was wrong — `EInvoiceRow.java` / `EInvoiceListResponse.java` have no such annotations. See [field-casing-parity-audit-2026-06-06.md](../../audits/field-casing-parity-audit-2026-06-06.md).)*
- JPA entity `BusaDocMstr` + composite key `BusaDocMstrId` + `BusaDocMstrRepository`.
- Schema parity: V15 created the table; V23 brought it to DEV column-for-column (100 columns total).
- **Missing legacy-parity behaviour:** the Spring service skips four behaviours the legacy `_act.jsp` enforces — see Phase 2 task list below.

**Phase 2 deliverables:**

| # | Task | Where |
|---|---|---|
| 1 | Document the REST contract — endpoints, request shape, response shape, error codes, validation. | New file: `docs/refactor-wecms/backend/addon/sales-e-invoices.md` (already drafted; pending corrections from this round) |
| 2 | Document the JEX → Spring service mapping (legacy `einv_0001_001_r001` IDO query → JPA repository, field-by-field with type / nullability deltas). | New file: `docs/refactor-wecms/migration/sales-e-invoices.md` |
| 3 | **Fix `EInvoiceService.resolveDocType`** — change `EXPENSE → 'E'` to `EXPENSE → 'B'` (`BusaDocType.BILL`). Drop the `'E'` alias. Same fix to `BusaDocMstr.docType` Javadoc and the controller Javadoc. | `EInvoiceController`, `EInvoiceService`, `BusaDocMstr` |
| 4 | **Add legacy-parity baseline filters** to `EInvoiceService.list` — currently MISSING and will return drafts/cancelled/hand-registered/empty-detail rows the legacy hides: `WHERE PROV_STTS NOT IN ('VD','EE')`, `WHERE HAND_REG_YN = 'N'`, `WHERE (DOC_ID, BIZ_KEY) IN (SELECT DOC_ID, BIZ_KEY FROM BUSA_DOC_DETL)`. When `PROV_STTS=OP` is passed explicitly, expand to `IN ('OP','PP','OD')`. | `EInvoiceService`, `BusaDocMstrRepository` |
| 5 | **Make `from` / `to` required** on `EInvoiceController#list` — legacy `validateInput` rejects requests without a date range. Frontend must always supply. | `EInvoiceController` |
| 6 | Extend `EInvoiceRow` to surface every column the UI needs + the columns View Setting may toggle on later. Priority: `COMPANYSTATUS`, `EINV_INVOICESTATUS_BEF`, `READ_YN`, `OVER_DUE_DAYS`, `DUE_DT`, `NOTE`, `PROV_STTS`, `RCV_SND_YN`, `HAND_REG_YN`, `DISP_DOC_ID`. **Drop** `COLOR` and `MEMO` from priority list — they are memo-sticker concerns, not status concerns. | `BusaDocMstr` (mappings), `EInvoiceRow`, `EInvoiceService#toRow` |
| 7 | Add server-side filter params: `invoiceStatus` (legacy `einv_invoiceStatus`) and `keyword` (legacy `SRCH_WD`). For `invoiceStatus`, settle the localisation strategy first (port `wb_get_invoice_status` to Flyway, replicate regex in Java emitting canonical keys, or replicate regex frontend-side). For `keyword`, decide between (a) replicating the legacy View-Setting-driven OR walk verbatim, (b) shipping a fixed Phase-2 subset, or (c) hybrid; document `wb_get_translate_text` parity decision. **The static UI's `invoiceNo + buyerName + mstCode + invoiceStatus + invoiceType` composite is wrong** — `EINV_INVOICESTATUS` is NOT a legacy search target. | `EInvoiceController`, `EInvoiceService`, `BusaDocMstrRepository` |
| 8 | Add server-side pagination — `page` + `size` query params, `Pageable`-driven repository methods, response carries `TOTAL_COUNT` / `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE`. Default `size=10`, max `100`. | `EInvoiceController`, `EInvoiceService`, `BusaDocMstrRepository` |
| 9 | Add server-side sort per [§13a](../../../../wecms-web/CLAUDE.md) — write `EInvoiceSortField` whitelist enum (constants `DATE_TIME` / `INVOICE_NO` / `BUYER` / `CURRENCY` / `TOTAL` / `STATUS` / `TAX` / `PRETAX` mapping to entity properties). Default sort stays legacy parity. | New: `domain/einvoice/service/EInvoiceSortField.java` |
| 10 | Compute multi-currency totals (legacy `TOT_VND_*` / `TOT_USD_*`) via a sibling aggregation query so they stay stable across pagination. The current loop totals only the page slice. | `EInvoiceService`, `BusaDocMstrRepository` |
| 11 | Decide the wire enum: `SALES` / `EXPENSE` only (drop the `I` / `E` aliases — and remember the `E` alias is wrong; replace with `B` while cleaning up). | `EInvoiceController`, `EInvoiceService#resolveDocType` |
| 12 | Excel export endpoint — `POST /api/v1/addon/einvoices/export` + `EInvoiceExportProvider implements ExportRowProvider` per the [Excel export rule](../../../CLAUDE.md). Allowed FIELD_IDs match the table column set; subheader = `<count> invoice(s) · Total (VND): … · Total (USD): …`. | New: `domain/einvoice/export/EInvoiceExportProvider.java` + `EInvoiceController#export` |

**Wire shape (target):**

```http
GET /api/v1/addon/einvoices?type=SALES
                          &from=20250101&to=20250131
                          &invoiceStatus=Original          (optional)
                          &keyword=1C24TLM                 (optional)
                          &page=0&size=10                  (optional)
                          &sortField=DATE_TIME&sortDirection=DESC   (optional)
```

```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "ROWS": [ { "DOC_ID": "...", "ISSU_DT": "20241205", "REG_DTM": "20241205113950", "TRCO_CMNM": "...", "TRCO_TAX_ID": "...", "COMPANYSTATUS": "Active", "TRCO_TRSC_CCY_CD": "VND", "TOTL_AMT": "1000000", "EINV_INVOICESTATUS": "Original", "EINV_INVOICESTATUS_BEF": "", "TAX_AMT_SUM": "100000", "SPLY_AMT_SUM": "900000", "EINV_INVOICESYMBOL": "1C24TLM-0405", "PROV_STTS": "OP", "READ_YN": "N", "OVER_DUE_DAYS": null, "DUE_DT": null, "HAND_REG_YN": "N", "..." } ],
    "TOTAL_COUNT": 7,
    "TOTAL_PAGES": 1,
    "PAGE_NUMBER": 0,
    "PAGE_SIZE": 10,
    "TOTALS": [
      { "CURRENCY": "VND", "COUNT": 7, "TOTAL": "200000000", "TAX": "20000000", "PRETAX": "180000000" },
      { "CURRENCY": "USD", "COUNT": 0, "TOTAL": "0",          "TAX": "0",         "PRETAX": "0" }
    ]
  }
}
```

---

## Phase 3 — Database mapping

**Status:** ✅ Schema is in place; only seed migrations remain.

**What exists:**

- `BUSA_DOC_MSTR` ported to local Flyway via V15 (Sprint 1 column subset) → V17 (seed) → V22 (real DEV seed) → V23 (added 80 missing columns to reach DEV's 100). Schema is column-for-column DEV per the [Legacy Schema Parity Rule](../../../CLAUDE.md).
- `EINV_FEE_DETL` + `EINV_TAX_DETL` ported in V15; real seed in V29.
- `BUSA_DOC_MSTR` is in the [PROD Active Table List](../../../CLAUDE.md). `EINV_FEE_DETL` and `EINV_TAX_DETL` are in the active list too — used by the future detail page.
- View Setting tables `SYS_SCR_OPTION_WECMS` + `USER_SCR_OPTION_WECMS` exist (V30 base schema, V32 / V34 / V35 / V40 etc. iterations) and are screen-agnostic — adding Sales e-Invoices is a seed-only change.

**Phase 3 deliverables:**

| # | Task | Migration |
|---|---|---|
| 1 | Seed `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-sales-einvoices'`, `DYM_AREA_ID = 'default'`. Columns: `CHECKBOX` (DISP_ORD=0, FIX_YN='Y', USE_YN='Y'), `DATE_TIME`, `INVOICE_NO`, `BUYER`, `CURRENCY`, `TOTAL`, `STATUS`, `TAX`, `PRETAX`. Use Figma WB365 column widths in `SIZE`. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. | New: `V{N}__addon_sales_einvoices_screen_options.sql` (number TBD — next free slot at write time) |
| 2 | If the Expenses sibling lands in the same MR, seed `addon-expenses-einvoices` in the same migration with the `BUYER` column relabelled `SUPPLIER` (or seed a separate migration — coordinate with the Expenses spec). | Same migration or sibling |
| 3 | Register `SCREEN_IDS.ADDON_SALES_EINVOICES = "addon-sales-einvoices"` in [`wecms-web/lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts). | Frontend (paired with the migration MR) |
| 4 | If the export endpoint adds an allow-list lookup table, no DB change — the allow-list lives in the `EInvoiceExportProvider` Java class per [§ Excel export](../../../CLAUDE.md). | — |

**No new tables required.** Detail-level joins (`EINV_TAX_DETL`, `EINV_FEE_DETL`, line items) are out of scope for the listing page — they belong to the per-invoice detail feature in a later sprint.

---

## Phase 4 — Integration (frontend ↔ backend wire-up)

**Status:** ⏳ Not started.

**Phase 4 deliverables (in roughly the order they unblock each other):**

1. **Wire empty-row guard.** When `data.rows` is empty, render an empty-state placeholder (mirrors Approval Box). Today the table just shows zero rows with no message.
2. ~~**UPPER_SNAKE wire flip.**~~ **VOID (corrected 2026-06-06).** No flip exists or is needed — the backend already ships **lowercase `snake_case`** (`EInvoiceRow` has no `@JsonProperty`), and `mapEInvoiceRow` already does the standard wire-snake → camelCase translation at the boundary. This deliverable was based on the wrong "wire is UPPER_SNAKE" assumption. See [field-casing-parity-audit-2026-06-06.md](../../audits/field-casing-parity-audit-2026-06-06.md).
3. **Adopt `usePagination()` per [§4](../../../../wecms-web/CLAUDE.md).** Wrapper sends `page` / `size` to the backend; remove the hardcoded `pageSize = 5`. Filter `onChange` calls `resetPage()`.
4. **Adopt `useTableSort()` per [§13a](../../../../wecms-web/CLAUDE.md).** Wrapper passes `sortField` / `sortDirection` to the backend and `toggleSort` down to `<EInvoiceTable>`. Migrate headers to `<TableHeaderCell sortFieldId="DATE_TIME" …>`.
5. **Wire the keyword search.** Replace the in-memory `useMemo` filter with a debounced (300 ms) `keyword` query param. Reset to `page=0` on change.
6. **Wire the invoice-status filter.** Build (or reuse) a shared `<FilterPopover>` triggered by [`<InvoiceStatusFilter>`](../../../../wecms-web/components/addon/sales-einvoices/InvoiceStatusFilter.tsx). Options come from `invoiceStatusFilterOptions` (currently in `mockData.ts` — relocate to a config module or fetch from a small reference API). Selection emits the legacy `einv_invoiceStatus` value to the wrapper, which forwards it as the `invoiceStatus` query param.
7. **Wire the date-range picker** when [`<DateRangePicker>`](../../../../wecms-web/components/shared/DateRangePicker.tsx) becomes interactive product-wide. Until then, leave the wire param defaulted to a sensible range (current behaviour).
8. **Consume View Setting data per IR-9 (NOT the management UI).** Add a `COLUMN_REGISTRY` keyed by `FIELD_ID` next to `<EInvoicesPage>`; thread `useViewSetting(SCREEN_IDS.ADDON_SALES_EINVOICES, DEFAULT_AREA_ID)` through the wrapper to drive `<colgroup>` widths, column visibility/order, and the Excel-export column set. **Do NOT render `<ViewSettingPopover>` and do NOT add a list-view icon to `<SalesEInvoiceActionBar>`** — the toolbar stays Print + Excel + Update only per Figma 9194:6859.
9. **Wire the Print button.** Use [`printReport(...)`](../../../../wecms-web/lib/print.ts) per [§6d](../../../../wecms-web/CLAUDE.md) — landscape, columns mirror the on-screen table (minus checkbox), source = filtered in-memory dataset BEFORE pagination.
10. **Wire the Excel button.** Use [`useExcelExport()`](../../../../wecms-web/hooks/useExcelExport.ts) per [§13b](../../../../wecms-web/CLAUDE.md) — `endpoint = "/api/v1/addon/einvoices/export"`, `screenId = "addon-sales-einvoices"`, `dymAreaId = "default"`. Decide the chevron-dropdown semantics (default "visible columns" + alternate "all columns").
11. **Wire the Update button.** Phase 1 (R&D scope): `refreshKey++` + refetch. Phase 2 (deferred): wire to a future GDT-pull endpoint.
12. **i18n.** Add KM (Khmer) translations to `locales/km.json` once the locale ships product-wide (track at the root `wecms-web` level, not per-feature).

---

## Phase 2 implementation rules (locked decisions)

This section converts the verified legacy findings into explicit implementation rules. **These are the rules backend/frontend implementation must follow.** Anything not listed here either (a) follows the existing CLAUDE.md rules unchanged, or (b) is in the "Deferred / PO decision" section below.

The cross-doc summary lives here; per-doc files (feature spec / frontend / backend / database / migration) carry the matching detail.

### IR-1. `DOC_TYPE` mapping — final rule

| Wire enum | Internal `DOC_TYPE` | `BusaDocType` constant | Status |
|---|---|---|---|
| `SALES` | `'I'` | `INVOICE` | ✅ accepted |
| `EXPENSE` | `'B'` | `BILL` | ✅ accepted (the wire enum can also be spelled `BILL` for clarity) |
| (anything else) | — | — | ❌ rejected with `WBE2002` |

**Bug to fix in `EInvoiceService.resolveDocType`** — the current mapping `EXPENSE → 'E'` is wrong. Change to `EXPENSE → 'B'`. Drop the legacy `'I'` / `'E'` aliases on the controller (the wire enum `SALES` / `EXPENSE` is the single sanctioned form). Same correction needed in `BusaDocMstr.docType` Javadoc and the `EInvoiceController` Javadoc.

### IR-2. Always-applied baseline filters — required Phase 2

`EInvoiceService.list` MUST add **all four** legacy-parity filters before any user-supplied filter is appended:

```sql
WHERE BIZ_KEY = :tenantBizKey
  AND DOC_TYPE = :resolvedDocType            -- 'I' or 'B' per IR-1
  AND PROV_STTS NOT IN ('VD', 'EE')          -- always; hides drafts + cancelled
  AND HAND_REG_YN <> 'Y'                     -- always; hides hand-registered rows
  AND EXISTS (
        SELECT 1
        FROM BUSA_DOC_DETL d
        WHERE d.DOC_ID = m.DOC_ID
          AND d.BIZ_KEY = m.BIZ_KEY
      )                                       -- always; hides invoices with no line items
  AND ISSU_DT BETWEEN :fromDt AND :toDt      -- required (see IR-3)
  -- optional user filters appended below
```

When the user passes `PROV_STTS=OP` explicitly, expand the predicate to `PROV_STTS IN ('OP','PP','OD')` (legacy parity per [`einv_0001_001_r001_act.jsp:137-147`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp)). Any other passed `PROV_STTS` value uses exact match; blank means "no additional filter beyond the baseline".

These four filters are **non-negotiable** for legacy parity. A unit test on `EInvoiceService.list` MUST assert each one is applied.

### IR-3. Date range policy — final rule

**Chosen rule: strict legacy parity — `from` and `to` are REQUIRED.**

- Both `from` and `to` query params are required on `GET /api/v1/addon/einvoices`. Missing either → `WBE_VALIDATION`.
- Both must be `YYYYMMDD` format, eight-digit numeric, parseable as a valid date. Malformed → `WBE_VALIDATION`.
- `from` ≤ `to`. Inverted → `WBE_VALIDATION`.
- Wire predicate: `WHERE ISSU_DT BETWEEN ? AND ?` (inclusive bounds, mirrors legacy).
- The frontend MUST always supply a non-empty range. The default range shown in the UI (today's hardcoded `05-Jan-2025 → 30-Jan-2025`) becomes a UI concern only — the `from`/`to` query params will always be populated.

The frontend default range value (last 30 days vs current month vs fiscal year) remains a **PO/designer decision** — see "Deferred / PO decision" below. Whichever default the UI picks, the wire contract above does not change.

### IR-4. `EINV_INVOICESTATUS` localisation — Phase 2 rule

**Chosen rule: do NOT port `wb_get_invoice_status` in Phase 2. Return the raw stored value; frontend resolves display via regex.**

- Backend `EInvoiceService` reads `BUSA_DOC_MSTR.EINV_INVOICESTATUS` directly. No DB function, no Java translation.
- Wire field `EINV_INVOICESTATUS` carries the stored value verbatim — typically Vietnamese on a real DEV row, since legacy reads via `wb_get_invoice_status(value, LANG_CD)` and the underlying stored values are usually the Vietnamese form.
- Frontend implements the legacy `loadTextColorInvBillOut` regex (vi/en/ko alternation) to map any of the six logical states to a canonical key + badge tint. This work lands in `mapEInvoiceRow` as a derived display field; the wire `EINV_INVOICESTATUS` stays raw for traceability.
- **Filter exact-match impact**: the wire filter `?invoiceStatus=…` translates to `WHERE EINV_INVOICESTATUS = ?` on the backend. Because no localisation function runs, the user's filter value MUST be **exactly** what the row stores (typically the Vietnamese form). Frontend strategy:
  1. The status filter dropdown sends the **stored** value (typically Vietnamese — mirror legacy frontend which always sent the Vietnamese form).
  2. Display labels in the dropdown go through i18n, but the wire payload is the stored Vietnamese string.
  3. If the local DEV seed has any non-Vietnamese stored values, they will not match the dropdown filter — document this as a known limitation of the Phase 2 simplification.
- **Full legacy parity is DEFERRED** — porting `wb_get_invoice_status` to local Flyway, or replicating the canonical-key strategy server-side, is tracked as a Sprint 3+ item. Until then, refactor users see the same status string for the same logical state regardless of `LANG_CD`. Sort by `STATUS` becomes a sort by stored-value string, not by logical state — acceptable for Phase 2.

### IR-5. `SRCH_WD` search scope — Phase 2 simplification

**Chosen rule: ship a fixed 5-column subset; full View-Setting-driven parity is deferred.**

Phase 2 backend implements `SRCH_WD` as an OR-clause across exactly these five `BUSA_DOC_MSTR` columns:

| Column | Predicate | Notes vs legacy |
|---|---|---|
| `DOC_ID` | `DOC_ID ILIKE '%' || ? || '%'` | Matches legacy whitelist |
| `TRCO_CMNM` | `LOWER(TRCO_CMNM) ILIKE '%' || LOWER(?) || '%'` | Legacy uses `wb_get_translate_text(...)` for accent folding; Phase 2 ships plain `LOWER(...)` ILIKE. Document the accent-folding gap. |
| `TRCO_TAX_ID` | `TRCO_TAX_ID ILIKE '%' || ? || '%'` | **Diverges from legacy** (legacy is `ILIKE ?` exact). Partial match here matches user expectation from the static UI mock. Document the divergence. |
| `EINV_INVOICESYMBOL` | `EINV_INVOICESYMBOL ILIKE '%' || ? || '%'` | **NEW vs legacy** (legacy whitelist does NOT include this column). Added because it's the user-visible invoice serial; not searching it would surprise users. Document the addition. |
| `DOC_NO` | `DOC_NO ILIKE '%' || ? || '%'` | Matches legacy whitelist |

Frontend forwards the user's keyword as the `keyword` query param after a 300 ms debounce. Reset to `page=0` on change. Backend escapes `%` / `_` / `\` in the user input via `ESCAPE '\'` clause to prevent unintended wildcard expansion.

**Deferred / not in Phase 2:**
- View-Setting-driven dynamic column walk (16 candidate columns). **Note vs IR-9**: legacy search uses the user's currently-visible View Setting columns to assemble the WHERE clause. Sales e-Invoices doesn't expose the View Setting popover (IR-9), so users can't toggle which columns participate in search. Phase 2 search is a fixed 5-column subset and is intentionally decoupled from View Setting state — this is the right call for a page without a column-management UI. If/when the popover is added (post-Phase-2), the search source can be re-evaluated then.
- The remaining 11 legacy whitelist columns (`TRCO_NICK_NM`, `TOTL_AMT`, `DOC_TITLE`, `REG_NM`, `CONT`, `NOTE`, `PRJT_NM`, `USAG_NM`, `USER_NM`, `ISSU_DT`, `TAX_AMT_SUM`, `SPLY_AMT_SUM`)
- `wb_get_translate_text` accent + case folding for Vietnamese
- Per-field predicate variation (numeric equality, date equality)

Tracked as a Sprint 3+ feature.

### IR-6. Totals — final rule

**Multi-currency totals (`TOTALS[]` array on the wire) MUST be computed from the full filtered dataset, NOT the current page slice.**

- The list endpoint runs two queries inside one `@Transactional(readOnly = true)`:
  1. The paged list query — `Page<BusaDocMstr>` with `Pageable` applied.
  2. The aggregation query — same `WHERE` chain (tenant + doc-type + baseline filters + user filters + date range), GROUP BY `TRCO_TRSC_CCY_CD`, returning `(CCY_CD, COUNT, SUM(TOTL_AMT), SUM(TAX_AMT_SUM), SUM(SPLY_AMT_SUM))`. No `Pageable`.
- The two queries MUST share one helper that builds the WHERE chain so user filters cannot drift between them.
- Frontend renders one footer `<tr>` per currency in the `TOTALS[]` array, mirroring today's `<EInvoiceTableFooter>`.

**Per-status totals** (`TOT_OP_INV_*`, `TOT_OD_INV_*`, `TOT_PA_INV_*`, `TOT_INV_*` on the legacy WSVC) — **deferred**. Refactor footer renders per-currency only until PO confirms a per-status footer is required.

### IR-7. Static UI mock fix — `invoiceType` rename

The static UI mock seeds `invoiceType: "Original"` / `"Replacement"` / etc. — those values are `EINV_INVOICESTATUS`, NOT the legacy `INV_TYPE` ('Commercial'/'Tax') classifier. Implementation rules:

- **Backend**: drop `INV_TYPE` from `EInvoiceRow` / `EInvoiceListResponse`. Legacy WSVC doesn't expose it either. The DB column stays as-is (no schema change); the entity field stays mapped (Hibernate doesn't care if the DTO ignores it).
- **Frontend**: drop `invoiceType` from `EInvoiceRow` (`components/addon/sales-einvoices/types.ts`) and from `mapEInvoiceRow`. Source the status sub-line shown under `EINV_INVOICESYMBOL` from `invoiceStatus` (i.e. `EINV_INVOICESTATUS`) — the table cell already renders the status text twice (sub-line + status column); the sub-line just reads from the same field.
- **Mock data**: when the mock fixture is regenerated next, drop `invoiceType` and let the table cell render `invoiceStatus` in both slots.

This rename ships in the same MR as the Phase 2 wire-extension (IR-2 + the response shape extension) so the contract change happens once.

### IR-9. View Setting scope — data in, management UI out

**Chosen rule: View Setting *data + frontend column-metadata consumption* are in Phase 2 scope. The user-facing View Setting *management UI* (popover, toolbar list-view icon, Update / Reset controls) is OUT of scope for this page.**

The Sales e-Invoices Figma frame (node 9194:6696 / 9194:6859) does not include a list-view icon or any column-customisation control. The toolbar is Print + Excel + Update only (Figma 9194:6859) and stays that way for Phase 2. The page's column set is treated as fixed per the seed — users cannot toggle column visibility, reorder columns, or reset overrides from this page.

**In scope (Phase 2):**

- **Database** — Flyway seed migration for `WEB_SVC_ID = 'addon-sales-einvoices'`, `DYM_AREA_ID = 'default'` in `SYS_SCR_OPTION_WECMS`. Seeds the column metadata (`FIELD_ID`, `FIELD_NM`, `DISP_ORD`, `USE_YN`, `FIX_YN`, `DISP_YN`, `SORT_YN`, `SIZE`) plus the `CHECKBOX` special row at `DISP_ORD=0`. No `ETC` row (no per-row action column).
- **Backend** — no change to the controller/service surface. The existing global `/api/v1/screen-options/{webSvcId}/{dymAreaId}` endpoints already accept any `WEB_SVC_ID`. The `EInvoiceExportProvider` intersects the requested export columns against the persisted View Setting (`USE_YN='Y'`, drop `CHECKBOX` / `ETC`) — defence in depth per the existing CLAUDE.md Excel-export rule.
- **Frontend** — `<EInvoicesPage>` declares a `COLUMN_REGISTRY` keyed by `FIELD_ID` mirroring the seed. The page calls `useViewSetting(SCREEN_IDS.ADDON_SALES_EINVOICES, DEFAULT_AREA_ID)` to fetch the merged sys+user View Setting. The hook drives:
  - `<colgroup>` widths (when product-wide `SIZE` consumption lands).
  - Column visibility / order — `<EInvoiceTable>` walks `vs.items` filtered to `USE_YN='Y'` instead of hardcoding the column list.
  - Excel export column set — `useExcelExport({ items: vs.items, … })` per [§13b](../../../../wecms-web/CLAUDE.md).
  - The lazy-init flow runs once per `(BIZ_KEY, USER_ID, WEB_SVC_ID, DYM_AREA_ID)` scope on first GET (existing global behaviour). No additional per-user save / reset traffic is generated by this page.

**Out of scope (deliberately, Phase 2):**

- `<ViewSettingPopover>` is **NOT** rendered on the Sales e-Invoices page.
- The list-view icon is **NOT** added to `<SalesEInvoiceActionBar>` — the toolbar stays Print + Excel + Update per Figma 9194:6859.
- The View Setting **Update / Reset** flow (`PUT` / `DELETE` on `/api/v1/screen-options/...`) is not exposed by any UI on this page. The endpoints remain available globally for other pages that DO render the popover (Account Inquiry); they are simply not called from here.

**Future expansion (post-Phase-2):**

If PO later requests user-tunable column visibility/order on this page, the work is purely frontend — drop the existing `<ViewSettingPopover>` into the page and add a list-view icon to `<SalesEInvoiceActionBar>`. The backend surface is already global and identical to Account Inquiry; no schema, service, or DTO changes would be required.

### IR-8. Implementation status — backend / frontend approval

With IR-1 through IR-7 locked, **backend and frontend implementation is approved to start** subject to the deferred-item table below. The blockers identified in the corrections-round report are now resolved as follows:

| Blocker | Status |
|---|---|
| `EInvoiceService.resolveDocType` `EXPENSE → 'E'` bug | ✅ rule IR-1 — fix in implementation MR |
| Missing baseline filters (`PROV_STTS NOT IN`, `HAND_REG_YN`, `BUSA_DOC_DETL` join) | ✅ rule IR-2 — required in implementation |
| `from`/`to` not required | ✅ rule IR-3 — required in implementation |
| `wb_get_invoice_status` not in local Flyway | ✅ rule IR-4 — defer; frontend regex handles display |
| `wb_get_translate_text` not in local Flyway | ✅ rule IR-5 — defer; ship 5-column simplified subset |
| `wb_get_memo_color` / `wb_get_memo_cont` not in local Flyway | ✅ already documented as out-of-scope; `COLOR` / `MEMO` dropped from Phase 2 priorities |
| Aggregation query for multi-currency totals | ✅ rule IR-6 — required in same MR as pagination |
| Static UI `invoiceType` misnaming | ✅ rule IR-7 — drop the field |
| View Setting scope (data vs management UI) | ✅ rule IR-9 — data + column-metadata consumption in scope; popover / toolbar icon / Update / Reset UI out of scope |

The remaining items below are PO / designer decisions and **do not block implementation**.

---

## Deferred / PO decision

These items are NOT blockers for the Phase 2 implementation defined by IR-1 through IR-7. They are tracked here so reviewers can flag them when the relevant artefact lands:

| Item | Why deferred | Owner | Trigger to revisit |
|---|---|---|---|
| **View Setting column widths (`SIZE` seed values)** | Need Figma WB365 widths for the Sales e-Invoices frame. The seed migration ships with placeholder widths; replace before user-tunable column widths land. | Designer + Arthur | Designer publishes the WB365 width set OR the user-tunable width feature lands. Mirror V34 pattern from Account Inquiry. |
| **Excel chevron-dropdown menu items** | Figma 9194:6859 shows the chevron but doesn't define menu options. Likely "visible columns" vs "all columns". | PO + Designer | Figma update with the menu spec. |
| **STATUS column rendering (badge vs plain text)** | Today's table renders the status as plain text. Figma may want a tinted badge driven by the regex match. Affects `<StatusBadge>` adoption. | PO + Designer | Designer confirms badge styling OR PO accepts plain text for Phase 2. |
| **Buyer-status (`COMPANYSTATUS`) controlled vocabulary** | Free-form Vietnamese text from GDT. Render verbatim or categorise into a controlled enum? Affects bank-matching dashboard alerts downstream. | PO + Compliance | Compliance review of GDT-returned values. |
| **Sales / Expenses bundling — same MR or separate?** | They share the page shell. Landing both at once amortises wire-flip + View Setting + Excel work. Landing separately keeps MRs small. **Note:** IR-1 (`EXPENSE → 'B'` fix) must be in whichever MR ships first. | Sonny | When Phase 2 backend MR is scoped. |
| **JEX feature doc** at `docs/jex/features/sales-e-invoices.md` | Not authored. WSVC XML + view JSP serve as the legacy spec today. Optional but improves traceability. | Sonny | When Sonny has cycles for JEX-side documentation. |
| **Frontend date-range default value** | UI default (last 30 days · current month · fiscal year). IR-3 makes the wire param required, so the UI must always populate it — but which default? | PO + Designer | Designer publishes the default. |
| **`ISSU_TM` policy** | Legacy doesn't expose any issue-time field. Pick: drop the time row, relabel "Registered at", or source from another column. | PO | When the static UI's date/time column is reviewed. |
| **Update-button GDT-pull semantics** | Legacy "Update" pulls from GDT NXISAS; refactor R&D scope is local-only. Phase 1 wires `refreshKey++`; full GDT bridge is post-refactor. | Sonny | If/when WeCMS adopts a GDT bridge in the refactor. |
| **Per-status totals** (`TOT_OP_INV_*` etc.) | Legacy returns them; refactor footer is per-currency only. | PO | PO reviews live data and confirms whether per-status breakdown is needed. |
| **Wire enum naming and aliases** | Final spelling on the controller — `SALES` / `EXPENSE` only, no alias accepting. IR-1 locks the mapping; this is a cosmetic tidy-up. | PO + Sonny | When Phase 2 controller MR lands — drop `'I'` / `'E'` aliases at that point. |
| **View Setting `FIELD_ID` vocabulary** | `STATUS` / `TOTAL` / `TAX` / `PRETAX` are generic; cross-page analytics could prefer `EINV_*` prefixed IDs. Seed PK is `(WEB_SVC_ID, FIELD_ID)`, so collisions aren't a technical problem. | Arthur | Before the seed migration lands. |
| **Local DEV seed coverage for Sales** | Local seed for `BIZ_KEY=VN_VNM0004_000001` may have very few `DOC_TYPE='I'` rows. Stakeholder demos may show an empty screen. | Arthur | Run the audit query against the local seed; if sparse, add a demo-seed migration per the Data Migration Rule. |

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **`DOC_TYPE='E'` mapping in `EInvoiceService` returns zero rows for Expenses.** Legacy uses `'B'` (`BusaDocType.BILL`); `EInvoiceService.resolveDocType` maps `EXPENSE → 'E'`. Today's local seed for `DOC_TYPE='E'` is empty/scarce, so the bug is hidden. | High (today) | High when Expenses page goes live | Phase 2 task #3 — fix the mapping before any Expenses MR lands. Add a unit test on `resolveDocType`. |
| **Missing baseline filters return drafts/cancelled/hand-registered/empty rows the legacy hides.** The Spring service today does NOT apply `PROV_STTS NOT IN ('VD','EE')`, `HAND_REG_YN='N'`, or the `BUSA_DOC_DETL` join. Once production data flows, the listing shows rows legacy users never see. | High | High — compliance / business-logic divergence | Phase 2 task #4 — add all four legacy-parity filters in one MR. |
| **`EINV_INVOICESTATUS` localisation parity.** Legacy resolves the value via `wb_get_invoice_status(value, LANG_CD)` server-side; refactor reads the raw stored value. Same logical state → different displayed string per locale. Filter (exact match) and sort behave inconsistently. | High | High — broken filter / display drift | Phase 2 task #7 — pick one of the three resolution strategies (port DB function, replicate in Java, replicate frontend-side) and land before user-visible status filtering. |
| **Multi-currency totals drift across pagination.** Today's `EInvoiceService#list` totals the page slice only. Once server-side pagination lands, the footer numbers change between pages. | High | High — wrong totals in finance UI | Ship Phase 2 task #10 (sibling aggregation query) **in the same MR** as server-side pagination (Phase 2 task #8). Never enable pagination without the aggregation. |
| **Static UI screenshots regress when the live data lands.** The mock seed shows polished VND amounts and Vietnamese buyer names; the local DEV seed (`BIZ_KEY=VN_VNM0004_000001`) may have very few `BUSA_DOC_MSTR` rows of `DOC_TYPE='I'`, or none at all. Stakeholder demos may show an empty screen. | Medium | Low–Medium — perception risk only | Confirm seed coverage in Phase 3; if the local seed is sparse, add a `V{N}__addon_sales_einvoices_demo_seed.sql` migration limited to `BIZ_KEY=VN_VNM0004_000001` per the [Data Migration Rule](../../../CLAUDE.md) (max 30 rows). Never seed fake data — copy real DEV rows. |
| **GDT `Update` button semantics.** Legacy "Update" pulls from the GDT NXISAS bridge — outside the refactor R&D scope. If stakeholders demo the button expecting the legacy behaviour, the refactor disappoints. | Medium | Low — scope is documented | Make the Phase 1 wire (just refresh local data) explicit in the button tooltip. Document the deferred GDT-pull in the feature spec. |
| **View Setting `FIELD_ID` collisions.** `STATUS` / `TOTAL` / `TAX` are common names — if Approval Box, e-Invoices, and Operation Status all use `STATUS` for different things, future cross-screen analytics could be confusing. | Low | Low | Doesn't actually collide — `(WEB_SVC_ID, FIELD_ID)` is the seed PK. But document the meaning of `STATUS` / `TOTAL` per screen so a reader of the seed migration can tell which page they're looking at. |
| **`SalesEInvoiceActionBar` divergence locks the page out of shared toolbar improvements.** Print / Excel / list-view changes that land on `<PageActionButtons>` would need a parallel change here. | Low | Low | Carry the divergence intentionally per CLAUDE.md note. Revisit if a third page needs a chevron-dropdown Excel — at that point lift it into `<PageActionButtons>` properly. |
| **Per-status totals (`TOT_OP_INV_*`, `TOT_OD_INV_*`, `TOT_PA_INV_*`, `TOT_INV_*`) might be required.** Legacy returns them; today's footer renders only per-currency totals. PO may expect per-status totals once they look at the live data. | Medium | Low | Confirm with PO before Phase 2 task #8. If required, fold into the same aggregation query. |
| **Sales / Expenses shell drift.** The two pages share `<EInvoicesPage>` deliberately. A column-set change for Sales that doesn't apply to Expenses (or vice versa) tempts a fork. | Medium | High — two pages becoming three components | Keep the column set, sort whitelist, and View Setting registry parameterised by `translationPrefix` / `screenId`. Forking the shell requires explicit MR sign-off. |
| **Schema inconsistency surface.** `BUSA_DOC_MSTR` already carries 100 columns; `EInvoiceRow` exposes 12. Adding 30 more columns one-at-a-time across multiple MRs invites churn. | Low | Medium | Land all column additions in ONE Phase 2 MR (task #4) so the wire contract stabilises in one go. |

---

## Open questions

The decision-blocking questions have been resolved as IR-1 through IR-7 above (see "Phase 2 implementation rules"). The remaining items live in the **Deferred / PO decision** table above and do not block implementation.

**Resolved by IR-1 through IR-7 (Phase 2 rules locked):**

- ✅ `DOC_TYPE` mapping (`SALES → 'I'`, `EXPENSE → 'B'`) — IR-1.
- ✅ Always-applied baseline filters (`PROV_STTS NOT IN ('VD','EE')`, `HAND_REG_YN <> 'Y'`, `BUSA_DOC_DETL` exists, `OP` expansion) — IR-2.
- ✅ Date range required (legacy parity, `from`+`to` mandatory + `YYYYMMDD` validation) — IR-3.
- ✅ `EINV_INVOICESTATUS` localisation (Phase 2 returns raw stored value; frontend regex handles display) — IR-4.
- ✅ `SRCH_WD` Phase 2 simplified scope (5 columns: `DOC_ID`, `TRCO_CMNM`, `TRCO_TAX_ID`, `EINV_INVOICESYMBOL`, `DOC_NO`) — IR-5.
- ✅ Multi-currency totals from full filtered dataset, not page slice — IR-6.
- ✅ Static UI mock `invoiceType` rename — IR-7.

**Resolved against legacy code (verified findings):**

- ✅ `DOC_TYPE='I'` for Sales, `'B'` for Bill/Expense (NOT `'S'`, NOT `'E'`). `'E'` and `'S'` are rejected by the listing.
- ✅ `INV_TYPE` is `Commercial`/`Tax` — NOT Original/Replacement/Adjustment. Those values belong to `EINV_INVOICESTATUS`. `INV_TYPE` is dropped from the legacy WSVC and not user-filterable.
- ✅ `EINV_INVOICESTATUS` has 6 logical values (Original, Replacement, Adjustment, Replaced, Adjusted, Cancelled) localised vi/en/ko via `wb_get_invoice_status`.
- ✅ `COLOR` is a memo-sticker CSS class (default `"white"`), NOT a status badge tint. Status tint comes from regex matching `EINV_INVOICESTATUS`.
- ✅ `PROV_STTS` is a fixed 6-value enum (`OP`/`PP`/`PA`/`OD`/`VD`/`EE`). Listing always excludes `VD` + `EE`. `PROV_STTS=OP` filter expands to `IN ('OP','PP','OD')`.
- ✅ Search whitelist for Sales (`table1`) is 16 `FIELD_ID`s; `EINV_INVOICESTATUS` is NOT searchable.
- ✅ `ISSU_TM` is not a legacy field; refactor proxy is `REG_DTM[8..14]`.

**Still open (PO / designer decisions — non-blocking):** see the **Deferred / PO decision** table above.

---

## Cross-references

- Feature spec: [../../features/addon-service/sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md)
- Frontend page (today): [`wecms-web/components/addon/sales-einvoices/`](../../../../wecms-web/components/addon/sales-einvoices/)
- Backend (today): [`wecms-api/src/main/java/com/kosign/wecms/domain/einvoice/`](../../../src/main/java/com/kosign/wecms/domain/einvoice/)
- Refactor frontend rules: [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) §7a · §13 · §13a · §13b · §6d
- Refactor backend rules: [wecms-api/CLAUDE.md](../../../CLAUDE.md) — Excel export · View Setting · Common table sort · Refactoring enforcement rule
- Legacy WSVC: `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml`
- Legacy view: `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp`

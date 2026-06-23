# Add-on Service > Expenses e-Invoices — Backend API Design

**Module:** Add-on Service
**Spring domain package:** `com.kosign.wecms.domain.einvoice` (shared with Sales — one controller / service / entity / repository / DTO set serves both pages)
**Refactor screen ID (proposed):** `addon-expenses-einvoices`
**JEX counterpart:** `einv_0001_001_r001` with `einvType=2` — `DOC_TYPE='B'` slice of `BUSA_DOC_MSTR`
**Feature spec:** [../../features/addon-service/expenses-e-invoices.md](../../../features/addon-service/expenses-e-invoices.md)
**Sister API (primary doc — wire shape, search behaviour, error codes, sort enum, aggregation query are inherited):** [sales-e-invoices-api.md](sales-e-invoices-api.md)
**Status:** Skeleton implemented (controller + service + entity + repository + DTOs — shared with Sales). The `EXPENSE → 'E'` mapping bug is the **single primary blocker** for this page; without the fix, every Expenses request returns zero rows.

JEX is **read-only reference**.

> **Inherits Phase 2 implementation rules from the Sales planning doc** — see Sales § Phase 2 implementation rules (locked decisions) _(removed — historical `planning/` folder deleted by project decision)_. IR-1 through IR-9 apply verbatim. The single rule that bites Expenses harder than Sales is **IR-1**: `EXPENSE → 'B'` (NOT `'E'`).

---

## Note: same API as Sales — only `type` differs

The Expenses page does NOT have its own controller, service, repository, or DTO. It shares the same `EInvoiceController` / `EInvoiceService` / `BusaDocMstr` / `BusaDocMstrRepository` / `EInvoiceListResponse` / `EInvoiceRow` as Sales. The wire discriminator is the `type` query param (`SALES` ⇒ `'I'`, `EXPENSE` ⇒ `'B'`).

This means **every Phase 2 backend MR for Sales lights up Expenses for free** (baseline filters, server-side pagination, sort, aggregation query, Excel export endpoint). The only Expenses-specific backend work is:

1. **Fix the `resolveDocType` bug** — `EXPENSE → 'B'` (per IR-1).
2. **Verify the `dymId='table2'` keyword-search whitelist** in `SearchHelper.java` (per Phase 2 task — see Search behaviour below).
3. **Optionally seed real DEV `DOC_TYPE='B'` rows** into local Flyway if the local seed is sparse (see Database doc).

Everything else — endpoint signatures, response shape, error codes, sort whitelist, BIZ_KEY enforcement, Excel export pipeline — is inherited from the Sales API design.

---

## Endpoints

### Implemented today

| Method | Path | Returns | Notes |
|---|---|---|---|
| GET | `/api/v1/addon/einvoices` | `ApiResponse<EInvoiceListResponse>` | One endpoint serves both pages. Today: optional `from` / `to`. Same handler resolves `type=EXPENSE` to `DOC_TYPE='E'` — **WRONG**, must be `'B'` per IR-1. |

### Planned (not yet implemented — shared with Sales)

| Method | Path | Returns | Purpose |
|---|---|---|---|
| GET | `/api/v1/addon/einvoices` | `ApiResponse<EInvoiceListResponse>` *(extended)* | Add `invoiceStatus`, `keyword`, `page`, `size`, `sortField`, `sortDirection`. |
| POST | `/api/v1/addon/einvoices/export` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (XLSX) | Per-resource Excel export. **Same endpoint, two screen ids** — request body's `screenId` selects between `addon-sales-einvoices` and `addon-expenses-einvoices`; the request body's `filters.type` discriminates `SALES` vs `EXPENSE`. |

Auth: same JWT-protected catch-all as Sales — see [Sales API § Endpoints](sales-e-invoices-api.md#endpoints).

---

## Request parameters

### `GET /api/v1/addon/einvoices?type=EXPENSE`

| Param | In | Type / shape | Required | Today | Planned semantics |
|---|---|---|---|---|---|
| `type` | query | `"EXPENSE"` (controller currently also accepts the legacy `"E"` alias — drop it; the wire enum may also be spelled `"BILL"` for clarity, see open question) | ✅ | **Today (BUG):** maps to `DOC_TYPE='E'`. **Legacy correct mapping:** `'B'` (`BusaDocType.BILL`). Legacy listing rejects `'E'` and `'S'` outright. | Fix mapping to `EXPENSE → 'B'` per IR-1. Same validation. |
| `from` | query | `String` (`YYYYMMDD`) | ❌ today / ✅ legacy (IR-3) | When non-blank with `to`, applies `WHERE ISSU_DT BETWEEN ? AND ?`. | **Required** per IR-3 — same as Sales. |
| `to` | query | `String` (`YYYYMMDD`) | ❌ today / ✅ legacy (IR-3) | See `from`. | **Required**. |
| `invoiceStatus` | query | `String` — value of `EINV_INVOICESTATUS` | — | **Not implemented.** | `WHERE EINV_INVOICESTATUS = ?` exact match. Per IR-4: backend reads raw stored value, frontend regex resolves display. The wire string the frontend sends is the **stored Vietnamese form** (mirrors legacy frontend behaviour). |
| `keyword` | query | `String`, length ≤ 100 | — | **Not implemented.** | OR-clause across the Phase 2 5-column subset (per IR-5). For Expenses the **whitelist `dymId='table2'`** must be re-verified against `SearchHelper.java`; the 5 columns chosen for Phase 2 (`DOC_ID`, `TRCO_CMNM`, `TRCO_TAX_ID`, `EINV_INVOICESYMBOL`, `DOC_NO`) match the underlying entity fields used on Expenses rows the same way they do on Sales rows. |
| `page` | query | `Integer ≥ 0` | — | **Not implemented.** | Zero-based page index. Default `0`. |
| `size` | query | `Integer ≥ 1` | — | **Not implemented.** | Default `10`, max `100`. |
| `sortField` | query | `EInvoiceSortField` — `DATE_TIME` / `INVOICE_NO` / `BUYER` / `CURRENCY` / `TOTAL` / `STATUS` / `TAX` / `PRETAX` | — | **Not implemented.** | Same whitelist enum as Sales. Note: even though the Expenses column header reads "Supplier", the sort wire `FIELD_ID` is **`BUYER`** (the entity property is `trcoCmnm` for both pages). |
| `sortDirection` | query | `"ASC" \| "DESC"` | — | **Not implemented.** | `SortDirection.parse(...)`. |

**Default sort** (when `sortField` / `sortDirection` blank): `ISSU_DT DESC, REG_DTM DESC` — same as Sales, legacy parity.

### Always-applied baseline filters (per IR-2 — currently MISSING)

The same four legacy-parity filters apply to both pages — the underlying `_act.jsp` is shared, only `DOC_TYPE` differs:

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
  AND ISSU_DT BETWEEN :fromDt AND :toDt      -- required per IR-3
```

When the user passes `PROV_STTS=OP` explicitly, expand to `IN ('OP','PP','OD')` (same OP-expansion rule as Sales).

Today's `EInvoiceService` skips all four — same Phase 2 task as Sales; the fix unblocks both pages at once.

### Search behaviour (`SRCH_WD` / `keyword`) — Expenses-specific verification

Legacy `SearchHelper.getSearchColumnDynamic` branches on `dymId`. Sales uses `'table1'`; **Expenses uses `'table2'`**. The whitelist sets may differ. Phase 2 task — verify `'table2'` against [`SearchHelper.java#L89-L193`](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/SearchHelper.java) and:

- If the `'table2'` whitelist is **identical** to `'table1'` (16 candidate `FIELD_ID`s with the same per-field predicate variation), the Phase 2 5-column subset (per IR-5) applies verbatim to Expenses.
- If the `'table2'` whitelist **differs** in any column (legacy quirks like the `dymId='table1'` `DOC_TITLE → cont`, `REG_NM → USER_NM`, `NOTE → APPR_DOC_NO` redirects may not apply), document the delta in the feature spec and decide whether to mirror the legacy difference or accept divergence for Phase 2.

Phase 2 ships the **same 5-column subset** for both pages either way — `DOC_ID`, `TRCO_CMNM`, `TRCO_TAX_ID`, `EINV_INVOICESYMBOL`, `DOC_NO`. The verification is to confirm the chosen subset doesn't accidentally surprise legacy users.

`wb_get_translate_text` parity decision is shared with Sales (port to Flyway / replicate in Java / accept divergence). Whichever path Sales takes, Expenses gets the same behaviour for free.

### `POST /api/v1/addon/einvoices/export` (planned)

Request body: shared `ExcelExportRequest` shape. **`screenId="addon-expenses-einvoices"`** for Expenses. The provider re-uses the Sales filter set (`type`, `from`, `to`, `invoiceStatus`, `keyword`); the `type` value selects the `DOC_TYPE` predicate.

**Critical:** if the request body omits `filters.type` (or sets it to `SALES`), the export returns Sales data even when the request was issued from the Expenses page. Frontend hook contract MUST always thread `type` from `<EInvoicesClient>` props through to the export call. Phase 4 testing must verify this against both pages.

---

## DOC_TYPE mapping — final rule (IR-1)

| Wire enum | Legacy `DOC_TYPE` | `BusaDocType` constant | `einvType` | Status |
|---|---|---|---|---|
| `SALES` | `'I'` | `INVOICE` | `1` | ✅ accepted |
| **`EXPENSE`** (or `BILL`) | **`'B'`** | **`BILL`** | `2` | ✅ accepted |
| (anything else) | — | — | — | ❌ rejected with `WBE2002` |

**Bug to fix:** `EInvoiceService.resolveDocType` currently maps `EXPENSE → 'E'`. Must be `'B'`. Drop the legacy `'I'` / `'E'` aliases on the controller (`SALES` / `EXPENSE` is the single sanctioned wire enum). Fix the `BusaDocMstr.docType` Javadoc and the `EInvoiceController` Javadoc in the same MR. **Add a unit test** asserting `resolveDocType("EXPENSE") == 'B'` and `resolveDocType("E")` is rejected.

Verified against:
- [`einv_0001_001_r001_act.jsp:268-284`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp) — branches on `einvType` to `"table1"` / `"table2"` and dispatches `DOC_TYPE='I'` / `'B'`. Rejects `'E'` and `'S'` with `WBE0001`.
- [`BusaDocType.java`](../../../../../WECMS%20DEV/WECMS/src/wabooks/enums/BusaDocType.java) — `INVOICE='I'`, `BILL='B'`, `EXPENSE='E'`, `PAYROLLBOOK='S'`. The legacy `EXPENSE` constant is **NOT used by the listing** — it belongs to a different flow.

---

## Response structure

Identical shape to Sales. See [Sales API § Response structure](sales-e-invoices-api.md#response-structure) for the full JSON example, the planned shape (with `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE` / extended row fields), and the wire-field mapping table from `WSVC.einv_0001_001_r001.xml`.

Expenses-specific notes on the response:

- The same 41 legacy `REC` fields apply — there is no Expenses-specific subset.
- `TRCO_CMNM` carries the supplier's company name on `DOC_TYPE='B'` rows; the wire field name does NOT change. Frontend renders the column header as "Supplier" via i18n.
- `COMPANYSTATUS` (planned in the Phase 2 wire extension) carries the supplier's GDT MST status. The compliance flag value `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục chấm dứt hiệu lực MST` is the dominant Expenses-side compliance signal — the field MUST be on the Phase 2 priority list (it already is in the Sales planning doc).
- `TOTALS[]` per-currency aggregation works identically — multi-currency Expenses are uncommon in the local seed but supported by the same sibling unpaged query (per IR-6).

---

## Invoice status mapping (shared logic)

Identical to Sales — same six logical states (Original / Replacement / Adjustment / Replaced / Adjusted / Cancelled), localised per `LANG_CD` in legacy via `wb_get_invoice_status`. Phase 2 returns the raw stored value per IR-4; frontend regex resolves display + badge tint.

See [Sales API § Invoice status mapping](sales-e-invoices-api.md#invoice-status-mapping) for the full vocabulary table and the three resolution-strategy options.

`COLOR` semantics, `PROV_STTS` vocabulary, `INV_TYPE` Commercial/Tax classifier, `EINV_INVOICESTATUS_BEF` prior-status field — **all identical** to Sales. No Expenses-specific deviation.

The supplier-status field (`COMPANYSTATUS`) is the Expenses analogue of Sales' buyer-status — same field, same denormalisation, same compliance-flag rendering rule. Free-form Vietnamese text from GDT, rendered verbatim — never normalised into a controlled enum.

---

## `PROV_STTS` vocabulary

Identical to Sales — same 6-value enum (`OP`/`PP`/`PA`/`OD`/`VD`/`EE`); listing always excludes `VD` + `EE`; `PROV_STTS=OP` filter expands to `IN ('OP','PP','OD')`. See [Sales API § PROV_STTS vocabulary](sales-e-invoices-api.md#prov_stts-vocabulary).

---

## Error handling

Identical to Sales. Standard `ApiResponse<T>` envelope, HTTP 200 for known business errors. See [Sales API § Error handling](sales-e-invoices-api.md#error-handling) for the full code table.

| Code | When (Expenses-specific notes) |
|---|---|
| `WBI0000` | Success — `ERROR=false`, `REC` populated |
| `WBE2002` | `type` is null / blank / not in `{SALES, EXPENSE}` (drop the `'E'` alias). Currently thrown by `EInvoiceService#resolveDocType`. After the IR-1 fix, raw `'B'` / `'I'` aliases are also dropped. |
| `WBE_VALIDATION` | Bean-validation failure — required `from` / `to`, `YYYYMMDD` format, `from ≤ to`, `screenId` regex on the export request, `size > 100` |
| `WBE2010` | Unknown `screenId` in `ExcelExportRequest` — note that **`addon-expenses-einvoices` is the second screen-id served by the same provider type** (or a sibling provider with the same allowedFieldIds and a fetch path filtered by `DOC_TYPE='B'`). If the registry only has Sales registered, the Expenses export raises this. |
| `WBE2011` | Excel export exceeds the 50,000-row cap |
| `WBE2013` | Excel request has no exportable columns after intersection with View Setting |
| `WBE9999` | Unexpected runtime error |

Same "no 4xx for invalid sort / filter values" rules as Sales — invalid sort silently falls back to default ORDER BY; invalid filter values are treated as "no filter".

---

## Tenant filtering — `BIZ_KEY`

Identical to Sales. Every query is scoped by `TenantContext.currentBizKey()` (sourced from the validated JWT). Local R&D pin: `wecms.tenant.biz-key = VN_VNM0004_000001`. Wildcards rejected by `TenantProperties` regex.

The same hard rules apply:

- Repository finders take `bizKey` as the first argument.
- Service-side: `final String bizKey = TenantContext.currentBizKey();`.
- Composite primary key `(BIZ_KEY, DOC_ID)` enforced via `@IdClass`.
- Tenancy MUST NOT be widened.
- Export endpoint inherits the scope by going through `EInvoiceService.list(...)`.
- `BIZ_KEY` is NOT echoed on the wire.

See [Sales API § BIZ_KEY / tenant filtering](sales-e-invoices-api.md#biz_key--tenant-filtering) for the full rule set.

---

## Shared service — `EInvoiceService`

The same `EInvoiceService` serves both Sales and Expenses. The dispatch is a one-line branch on `DOC_TYPE`:

```java
final String docType = resolveDocType(type);   // 'I' for SALES, 'B' for EXPENSE (after IR-1 fix)
List<BusaDocMstr> rows = repo.findByBizKeyAndDocTypeAnd…(bizKey, docType, …);
```

After the IR-1 fix lands, both pages use the identical service path. The `EInvoiceSortField` whitelist enum, the multi-currency aggregation query, and the response composition are all shared.

**`EInvoiceExportProvider`** — the same provider serves both screen ids (or a thin sibling registers `addon-expenses-einvoices` with the same `allowedFieldIds()` and the same `fetch(...)` path that delegates to `EInvoiceService.list(...)`). The implementation choice is cosmetic — both shapes are sanctioned. Pick whichever keeps the registry symbol count low.

```java
@Component
public class EInvoiceExportProvider implements ExportRowProvider {
    public List<String> screenIds() {
        return List.of("addon-sales-einvoices", "addon-expenses-einvoices");
    }
    public Set<String> allowedFieldIds() {
        return Set.of("DATE_TIME", "INVOICE_NO", "BUYER", "CURRENCY", "TOTAL", "STATUS", "TAX", "PRETAX");
    }
    public ExportFetch fetch(ExcelExportRequest req) {
        // Re-runs EInvoiceService.list(...) with Pageable.unpaged()
        // Reads filters.type to discriminate SALES vs EXPENSE → DOC_TYPE
        // Subheader: <count> invoice(s) · Total (VND): … · Total (USD): …
    }
}
```

If `ExportRowProvider` only allows one `screenId()` per bean, register two beans of the same class with different `screenId()` returns — `EInvoiceSalesExportProvider` + `EInvoiceExpensesExportProvider` — sharing a base class. The fetch path is identical; only the `screenId()` value differs.

---

## Related backend files

**Implemented today (shared with Sales — no Expenses-only backend file exists):**

- See [Sales API § Related backend files](sales-e-invoices-api.md#related-backend-files).

**To be added (shared with Sales unless noted):**

- `service/EInvoiceSortField.java` — sort whitelist enum (shared)
- `export/EInvoiceExportProvider.java` (Sales) — and Expenses sibling (or one bean covering both screen ids)
- Repository methods accepting `Pageable` + `Sort` + `invoiceStatus` + `keyword` — shared
- `aggregateByCurrency(...)` — shared

**Shared infrastructure consumed:** identical to Sales — `ApiResponse`, `BusinessException`, `MessageResolver`, `JwtAuthenticationFilter` + `TenantContext`, `SortDirection` + `SortableField`, `ExcelExportService` + `ExportableScreenRegistry` + `ExcelWorkbookWriter` + `ExcelExportRequest` + `ExcelColumnSpec` + `AmountFormatter`, `ScreenOptionService`.

---

## Cross-references

- Feature spec: [../../features/addon-service/expenses-e-invoices.md](../../../features/addon-service/expenses-e-invoices.md)
- Sister API (primary doc): [sales-e-invoices-api.md](sales-e-invoices-api.md)
- Frontend page: [../../frontend/addon/expenses-e-invoices-page.md](../../../../../wecms-web/docs/features/addon-service/expenses-e-invoices.md)
- Database: [../../database/addon/expenses-e-invoices-db.md](../../database/addon/expenses-e-invoices-db.md)
- Migration mapping: [../../migration/service-mapping.md](../../migration/service-mapping.md)
- Implementation plan: ../../planning/addon/expenses-e-invoices-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- Sales planning rules (IR-1 through IR-9 inherited): ../../planning/addon/sales-e-invoices-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- Legacy WSVC: `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml`
- Legacy view: `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` (with `einvType=2`)

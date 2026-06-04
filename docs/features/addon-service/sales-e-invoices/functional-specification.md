# Sales e-Invoices — Backend Functional Specification

Business rules implemented by `com.kosign.wecms.domain.einvoice`. Source of truth is the code; JEX (`einv_0001_001` / `einvType=1`) is the functional reference. The same rules with `DOC_TYPE='B'` back [Expenses e-Invoices](../expenses-e-invoices/functional-specification.md).

---

## 1. Purpose & scope

- **In scope (implemented):** read-only listing of outbound e-invoices (`BUSA_DOC_MSTR`, `DOC_TYPE='I'`) for the current tenant, with keyword filter, server-side pagination + sort, and multi-currency footer totals; two XLSX exports (list view + line-item detail expansion); scrape-intake of GDT data (phase-1 invoice list, phase-2 invoice detail + counterparty auto-registration); GDT inquiry-history and detail-history read endpoints.
- **Tenant isolation:** every operation scoped to `TenantContext.currentBizKey()`. The wire never accepts `BIZ_KEY`; an unauthenticated request is 401 before the controller runs.
- **Out of scope:** issuing / replacing / adjusting / cancelling invoices (legacy `einv_0001_001_u001` write flow — not ported); the per-invoice detail *screen* (line items render only inside the detail export today); the live NXISAS → GDT polling bridge (the scrape endpoints accept already-fetched data; they do not call the GDT); the card-link / bank-matching workflow (consumes `BusaDocMstrRepository.findLinkable*` but is a separate feature).

---

## 2. `DOC_TYPE` resolution (the old "code bug" is fixed)

`EInvoiceService.resolveDocType(type)` (L205-221):

| Wire `type` | `DOC_TYPE` | `BusaDocType` | Internal `einvType` | Result |
|---|---|---|---|---|
| `SALES` | `'I'` | `INVOICE` | `table1` | ✅ accepted |
| `EXPENSE` / `BILL` | `'B'` | `BILL` | `table2` | ✅ accepted |
| `E` (`EXPENSE` legacy enum) | — | — | — | ❌ `WBE2002` |
| `S` (`PAYROLLBOOK`) | — | — | — | ❌ `WBE2002` |

> **Status correction:** the older [single-file spec](../sales-e-invoices.md) reports `resolveDocType` mapping `EXPENSE → 'E'` as an open code bug. That is **stale** — the implemented mapping is `EXPENSE → 'B'`, and `'E'`/`'S'` are explicitly rejected with `WBE2002`. No fix is outstanding.

---

## 3. List — filters, keyword, sort, pagination

### Always-applied baseline filters (IR-2 / IR-3)

| Filter | Rule |
|---|---|
| tenant | `BIZ_KEY = currentBizKey()` |
| document type | `DOC_TYPE = :docType` (`'I'` Sales) |
| visibility | `PROV_STTS NOT IN ('VD','EE')` — cancelled/void + drafts always hidden |
| sync origin | `HAND_REG_YN <> 'Y'` — hand-registered rows always hidden |
| date range | `ISSU_DT BETWEEN :fromDt AND :toDt` — **both required** (`@Pattern` `YYYYMMDD` on the controller + `validateDateRange` enforces `from ≤ to`) |

### Keyword (`keyword` → `SRCH_WD`)

Fixed **5-column** OR (IR-5): `DOC_ID`, `TRCO_CMNM`, `TRCO_TAX_ID`, `EINV_INVOICESYMBOL`, `DISP_DOC_ID`. `buildKeywordPattern` trims → null if blank → escapes `%`/`_`/`!` with `ESCAPE '!'` → wraps `%…%`. The legacy View-Setting-driven 16-column whitelist with `wb_get_translate_text` accent folding is **not** ported — see [refactor-gap-analysis.md §D](refactor-gap-analysis.md).

### Sort (common-sort contract)

Whitelist `EInvoiceSortField`: `DATE_TIME→issuDt`, `INVOICE_NO→einvInvoicesymbol`, `BUYER→trcoCmnm`, `CURRENCY→trcoTrscCcyCd`, `TOTAL→totlAmt`, `STATUS→einvInvoicestatus`, `TAX→taxAmtSum`, `PRETAX→splyAmtSum`. `parse()` returns `null` on unknown/blank → silent fallback to **`DEFAULT_SORT = ISSU_DT DESC, REG_DTM DESC`** (legacy parity). A user sort is always followed by `DEFAULT_SORT` as a deterministic tiebreaker.

### Pagination

`page` zero-based; `size` defaults to `DEFAULT_PAGE_SIZE = 10` when `<= 0`, clamped to `MAX_PAGE_SIZE = 100`. Response carries `total_count`, `total_pages`, `page_number`, `page_size`. Export uses `Pageable.unpaged()`.

### Multi-currency totals (IR-6)

`aggregateByCurrency` runs the **same WHERE chain unpaged**, `GROUP BY TRCO_TRSC_CCY_CD`, returning per-currency `count` + `SUM(TOTL_AMT)` + `SUM(TAX_AMT_SUM)` + `SUM(SPLY_AMT_SUM)`. One `CurrencyTotal` per currency seen in the full filtered set, so the footer is stable across pages. Legacy per-status totals (`TOT_OP_INV_*`, `TOT_OD_INV_*`, …) are **not** computed.

---

## 4. Invoice status (`EINV_INVOICESTATUS`)

`EINV_INVOICESTATUS` is a **localised free-form string** the GDT returns (vi/en/ko). The refactor returns the **raw stored value verbatim** on the wire (`einv_invoicestatus`) plus the prior status (`einv_invoicestatus_bef`) — it does **not** port the legacy `wb_get_invoice_status(value, LANG_CD)` localisation function (IR-4). The frontend derives the badge tint + logical key by regex (`lib/api/einvoiceStatus.ts`). Six logical states are recognised downstream: Original / Replacement / Adjustment / Replaced / Adjusted / Cancelled.

The `PROV_STTS` column (settlement status, `OP`/`PP`/`PA`/`OD`/`VD`/`EE`) is returned raw; only the `VD`/`EE` exclusion is applied as a filter.

---

## 5. Scrape intake (phase 1 + phase 2)

| Phase | Endpoint | Writes | Legacy branch rules |
|---|---|---|---|
| 1 — invoice list | `POST .../einvoices/scrape-results` | upsert `BUSA_DOC_MSTR` + `EXT_TRAN_CALL_HIS` audit | `CHECK_RESULT_BRANCH_A/B` derive `EINV_CHECKRESULTS`; `VOID_STATUS_BRANCH_A/B` derive replaced/cancelled `EINV_INVOICESTATUS` (mirrors `nxisas_einvoice_c001:145-250`) |
| 2 — invoice detail | `POST .../einvoices/scrape-detail-results` | enrich master, rewrite `BUSA_DOC_DETL`/`EINV_FEE_DETL`/`EINV_TAX_DETL`, auto-register counterparty | `EInvoiceTrcoAutoRegistrar` (TRCO create/reuse, `PGRO_BSAC_LNKN_INFM`, `TRCO_ACCT_INFO`, `TRCO_ACTV_HSTR`) |

- Phase-2 counterparty resolution: SALES → **buyer** block; EXPENSE → **seller** block. No tax id → pseudo-contact `ETC_TRCO_KEY = "99999999999999999999"`.
- Both phases return `{ saved_count, updated_count, skipped_count, mod_dtm }`. `mod_dtm` (the batch timestamp) is the key the detail-history endpoint filters on.
- These endpoints accept **already-scraped** data; the engine that fetches it from the GDT (NXISAS) is out of refactor R&D scope.

---

## 6. Error codes

| Code | Meaning |
|---|---|
| `WBE2002` | Invalid request parameter — unsupported `type` / rejected `DOC_TYPE` (`E`/`S`), or Expense passed to detail-history |
| `WBE_VALIDATION` | Bean / `@Pattern` validation failure (missing or malformed `type` / `from` / `to` / `mod_dtm`) |
| `WBE2010` | Excel export — unknown `screenId` |
| `WBE2011` | Excel export — exceeds 50,000-row cap |
| `WBE2013` | Excel export — no exportable columns after View Setting intersection |
| `WBE9999` | Unexpected server fault (catch-all) |

All business outcomes return HTTP 200 with `status.error = true`; the frontend reads `status.error` / `status.code`. Add new codes to `messages.properties` before throwing.

---

## 7. Product-type & permissions

- No `@PreAuthorize` gate on these endpoints today — any authenticated tenant user (matches live JEX).
- **Product-type gate deferred.** Legacy WeCMS gates e-Invoice behind `PRDT_TYPE='F'` (WeCMS Default). The refactor models `F`/`M`/`H` in `PrdtType` but the e-Invoice product gate is **not yet applied** — it lands with the e-Invoice product-gate MR (see [wecms-api/CLAUDE.md → Product-type rule](../../../../CLAUDE.md)).

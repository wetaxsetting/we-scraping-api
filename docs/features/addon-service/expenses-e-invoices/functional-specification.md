# Expenses e-Invoices — Backend Functional Specification

All business rules are **shared with Sales** — see the canonical [Sales functional-specification.md](../sales-e-invoices/functional-specification.md). Only the Expenses deltas are below.

---

## 1. Purpose & scope

Read-only listing of inbound e-invoices (`BUSA_DOC_MSTR`, `DOC_TYPE='B'`) received from suppliers, plus the same export / scrape-intake / inquiry-history operations as Sales — all through the shared `com.kosign.wecms.domain.einvoice` package with `type=EXPENSE`.

**Compliance angle (Expenses-specific):** the supplier's MST status (`COMPANYSTATUS` on the joined operation-status record) is the dominant signal — a suspended/ceased supplier can invalidate the buyer's **input-VAT credit**. The list surfaces the supplier tax id (`trco_tax_id`) so the finance team can cross-check against Operation Status Inquiry.

## 2. `DOC_TYPE` resolution

`resolveDocType("EXPENSE")` → `'B'` (`BusaDocType.BILL`). The legacy `'E'` (`EXPENSE` enum) and `'S'` (`PAYROLLBOOK`) are rejected with `WBE2002`.

> **Status correction:** the older [single-file spec](../expenses-e-invoices.md) calls the `EXPENSE → 'E'` mapping "the single most important blocker for the Expenses page." That is **stale** — the implemented mapping is `'B'`; the Expenses list returns rows correctly. No blocker remains.

## 3. Filters / keyword / sort / pagination / totals

Identical to [Sales §3](../sales-e-invoices/functional-specification.md#3-list--filters-keyword-sort-pagination), with `DOC_TYPE='B'`:
- Baseline: `PROV_STTS NOT IN ('VD','EE')`, `HAND_REG_YN <> 'Y'`, `ISSU_DT BETWEEN` (required).
- Keyword: fixed 5-column OR (`DOC_ID`, `TRCO_CMNM`, `TRCO_TAX_ID`, `EINV_INVOICESYMBOL`, `DISP_DOC_ID`) — for Expenses `TRCO_CMNM`/`TRCO_TAX_ID` carry the **supplier** name/MST.
- Sort: `EInvoiceSortField` whitelist; default `ISSU_DT DESC, REG_DTM DESC`.
- Pagination: `page`/`size` (clamp 100); multi-currency totals via `aggregateByCurrency`.

## 4. Invoice status & scrape intake

Identical to Sales ([§4](../sales-e-invoices/functional-specification.md#4-invoice-status-einv_invoicestatus) / [§5](../sales-e-invoices/functional-specification.md#5-scrape-intake-phase-1--phase-2)). Phase-2 counterparty resolution uses the **seller** block; the auto-registrar writes `TRCO_ACTV_HSTR` with `DV_CD='B'` / `TASK_CD='B003'` and `PGRO_BSAC_LNKN_INFM` with `GRP_CD='B'`.

## 5. Detail-history is Sales-only

`GET /einvoices/detail-history?type=EXPENSE` → **`WBE2002`**. The re-poll detail-history endpoint exists for Sales only (legacy `nxisas_einvhis_dt_r001` parity). GDT **inquiry**-history (`GET /einvoices/history?type=EXPENSE`) **is** available.

## 6. Error codes & permissions

Same as [Sales §6 / §7](../sales-e-invoices/functional-specification.md#6-error-codes) — `WBE2002`, `WBE_VALIDATION`, `WBE2010/2011/2013`, `WBE9999`.

> **Corrected 2026-06-06 (F-3 verification).** `@PreAuthorize` **is** live (the "no gate / deferred" note was stale). `type=EXPENSE` resolves to `addon-einvoices-expenses` via `@menuAuthz.canAccessEInvoice(#p0)`; export/scrape use `canAccessAny(...)`. Product-type gate is live via the navigation product tree: **F and M carry e-Invoice; H does not.** Full matrix: [authorization-and-product-type.md](../authorization-and-product-type.md).

## 7. Open question (seed naming)

The View Setting / column `FIELD_ID` for the counterparty column is seeded as **`BUYER`** (reused from Sales — the entity field is `TRCO_CMNM` either way; the header text localises to "Supplier"). PO may prefer `SUPPLIER` for clarity — see [refactor-gap-analysis §4](refactor-gap-analysis.md#4-open-questions). Not a technical blocker.

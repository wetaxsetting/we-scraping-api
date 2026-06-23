# Add-on Service — Gap Analysis

**Audit date:** 2026-06-23
**Companions:** [addon-service-docs-audit.md](addon-service-docs-audit.md) · [addon-service-source-index.md](addon-service-source-index.md)
**Convention:** every row is verified against the working tree. "✅ Implemented" means code confirmed present this pass.

---

## A. Code exists but documentation was missing / understated

| # | Item | Evidence | Status |
|---|---|---|---|
| A1 | `/einvoices/export-details` endpoint (line-item-expansion XLSX, legacy `einv_0001_001_r002_excel`) | [EInvoiceController.java:169-175](../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java#L169-L175) + `EInvoiceDetailExportService` | Documented in folder docs; the **single-file design-history spec** predates it (now superseded by the corrected Status line). |
| A2 | Operation-Status **Save** (`POST /operation-status` → `#create`) | [OperationStatusController.java:100-104](../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java#L100-L104) | Was described as "no backend upsert endpoint yet" in two single-file specs + CLAUDE.md — **corrected** in the docs; CLAUDE.md still stale. |
| A3 | Live OSMC→GDT bridge for `inquire`/`bulkUpdate` (audit rows in `EXT_TRAN_CALL_HIS` + `NXSIAS_MODULE_TRAN_LOG_HIS`) | `OperationStatusService` L297-547; `OsmcGdtClient` | Folder docs accurate; single-file specs said "no NXISAS bridge / deferred" — superseded by corrected banners + Status line. |
| A4 | e-Invoice scrape two-phase intake + counterparty auto-register | `EInvoiceScrapeService`, `EInvoiceDetailScrapeService`, `EInvoiceTrcoAutoRegistrar` | Folder docs accurate. |

## B. Documentation exists but feature is absent / different in code

| # | Doc claim | Reality | Action taken |
|---|---|---|---|
| B1 | `EInvoiceService.resolveDocType` maps `EXPENSE → 'E'` (a bug) | Code maps `EXPENSE`/`BILL` → `'B'`; rejects `'E'`/`'S'` with `WBE2002` ([L205-221](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java#L205-L221)) | Corrected sales + expenses single-file specs. |
| B2 | docType rejects throw `WBE0001` | Code throws `WBE2002` (`messages.properties:9`) | Corrected docType tables. |
| B3 | Operation-Status Save is inert / no endpoint | `#create` persists with dup guard | Corrected API + WEB single-file Status lines. |
| B4 | Operation-Status Inquire/Update/Status-filter "NOT wired" (WEB) | All wired via `lib/api/addon.ts` | Corrected WEB single-file Status line. |
| B5 | (CLAUDE.md, not a `docs/` file) e-invoice "EXPENSE→'E' open code bug"; operation "Save is a stub" | Both stale per B1/B3 | **Flagged only** — CLAUDE.md is out of `docs/` audit scope; fix in next CLAUDE.md pass. |

## C. PO decision required

| # | Item | Why it needs a decision |
|---|---|---|
| C1 | IR-4 invoice-status localisation strategy | Backend returns raw `EINV_INVOICESTATUS`; frontend regex-matches client-side ([einvoiceStatus.ts](../../../wecms-web/lib/api/einvoiceStatus.ts), a documented BLOCKER). Choosing a coded value + `wb_get_invoice_status` port vs. keeping client-side is a product/PO call. |
| C2 | "Has-line-items" baseline filter for the e-invoice list | Legacy filters out invoices with no `BUSA_DOC_DETL` rows; refactor omits it. `BUSA_DOC_DETL` now exists locally, so the predicate is portable — confirm whether to add it (affects which rows appear). |
| C3 | GDT status-string localisation (`wb_get_operat_stts`) | Legacy helper is commented out; refactor maps to a 3-state code (`resolveStatusCd`) in Java. Confirm the 3-state mapping is the intended product behavior vs. a full localised string. |

## D. Backend implementation missing / partial

| # | Item | Status |
|---|---|---|
| D1 | Server-side `invoiceStatus` filter param on `/einvoices` | **Not implemented** (IR-4). Frontend post-filters the page slice. |
| D2 | Has-line-items baseline predicate (`BUSA_DOC_DETL` EXISTS) | **Not ported** — see C2. |
| D3 | Per-row delete for Operation Status (`aosv_0001_002_d001`) | **Intentionally not ported** (refactor page exposes no delete). Matches JEX page behavior. |

## E. Frontend implementation missing / partial

| # | Item | Status |
|---|---|---|
| E1 | In-page View Setting popover for the addon screens | **Not present** — addon screenIds (`addon-sales-einvoices`, `addon-expenses-einvoices`, `addon-operation-status`) are seeded as **export screenIds only** (no column-visibility popover on these pages). This is by design per V156 comments; confirm if a popover is ever wanted. |
| E2 | KM (Khmer) i18n | Not present — EN/KO/VI only across all three screens. Low priority. |

## F. Test coverage gaps

| # | Area | Status |
|---|---|---|
| F1 | `EInvoiceHistoryService` (`/history`, `/detail-history`) | No dedicated test. |
| F2 | `EInvoiceDetailScrapeService` (phase-2 intake) | No dedicated test. |
| F3 | `EInvoiceDetailExportService` (`/export-details`) | No dedicated test. |
| F4 | Operation-Status controller/integration (`create`/`inquire`/`update` endpoints) | Only `OperationStatusServiceTest` (service unit) exists; no endpoint-level test. |
| F5 | Endpoint authorization coverage | `EndpointAuthorizationCoverageTest` exists at the project level and asserts every business handler is annotated — addon handlers are covered by it. |

## G. Authorization namespace hazard (documentation)

| # | Item | Status |
|---|---|---|
| G1 | permission_cd vs. screenId word-order flip (`addon-einvoices-sales` ↔ `addon-sales-einvoices`) | Folder docs correct. **Any new doc must not swap them.** Captured in [authorization-and-product-type.md](../features/addon-service/authorization-and-product-type.md) and the source index §3. |
| G2 | Product gating — H omits Add-on | V243 seeds F + M only; guard enforces product-tree gate. Correctly documented. |

---

## Priority summary

- **Done this pass (docs):** B1–B4 corrected in the 4 single-file specs.
- **Needs PO:** C1 (IR-4 status localisation), C2 (has-line-items filter), C3 (operation-status localisation).
- **Backend follow-ups (no PO needed if scoped):** D1/D2 once C1/C2 are decided.
- **Test debt:** F1–F4.
- **Out of `docs/` scope but stale:** CLAUDE.md Feature-Status rows (B5).

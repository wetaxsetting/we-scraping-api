# Add-on Service — Documentation Re-Audit (Docs vs. Code)

**Audit date:** 2026-06-23
**Auditor:** Claude (code-as-source-of-truth re-audit)
**Trigger:** implementation changed after the previous documentation pass; verify all Add-on Service docs still reflect the code.
**Scope:** `wecms-api/docs/features/addon-service/**`, `wecms-web/docs/features/addon-service/**`, `wecms-api/src/**`, `wecms-web/src/**` (Next.js lives under `wecms-web/app|components|lib`, not `src/`).
**Companion files:** [addon-service-source-index.md](addon-service-source-index.md) (validated source references) · [addon-service-gap-analysis.md](addon-service-gap-analysis.md) (gaps + open decisions).

---

## Executive summary

The **consolidated per-module folder docs** (both API `…/<module>/` and WEB `…/<module>/`) are **accurate and current** — they correctly describe the wired list/export/scrape/history paths, `EXPENSE→'B'`, `WBE2002`, the live Operation-Status `inquire`/`create`/`update` bridge, and the IR-4 client-side status filter. They already carry "Status correction" notes.

The drift was confined to the older **single-file design-history specs** at each module root. Those files had top-of-file stale banners added in a prior pass, but their inline **Status** lines and the e-invoice **docType tables** still asserted the pre-fix state (`EXPENSE→'E'` "blocker", `WBE0001`, "no backend upsert endpoint yet for Save"). Those specific lines were corrected this pass; the design-rationale bodies were preserved.

**No backend or frontend code was changed.** This was a documentation-only audit.

---

## Per-module findings

### Sales e-Invoices

**Documentation Status:** **Accurate** (folder) · **Partially Outdated** (single-file design-history spec — now corrected).

**Files Updated**
- [wecms-api/docs/features/addon-service/sales-e-invoices.md](../features/addon-service/sales-e-invoices.md) — Status line, docType table, docType paragraph.

**Corrections Made**
- Status line: "Static UI only … not yet wired end-to-end" → **wired end-to-end** (list + totals + pagination/sort + `/export` + `/export-details` + two-phase scrape + `/history` + `/detail-history`).
- docType reject rows: `WBE0001` → **`WBE2002`** (matches `EInvoiceService` + `messages.properties`).
- docType paragraph: "currently maps `EXPENSE → 'E'` — incorrect … fix tracked in TODO" → corrected to the implemented `SALES→'I'` / `EXPENSE`/`BILL`→`'B'` mapping (verified [EInvoiceService.java:205-221](../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java#L205-L221)).

**Remaining Gaps**
- Invoice-**status** filter is client-side (IR-4) — backend exposes raw `EINV_INVOICESTATUS`; no `wb_get_invoice_status` port and no server `invoiceStatus` param. *(Already documented in the folder docs; carried into the gap analysis.)*
- "Has-line-items" baseline filter not ported (folder docs note it; `BUSA_DOC_DETL` now exists, so the predicate could be added).
- No dedicated tests for `EInvoiceHistoryService` / `EInvoiceDetailScrapeService` / `EInvoiceDetailExportService`.

**Consistency Report**
| Axis | Result |
|---|---|
| Doc ↔ API | ✅ after fix — 7 endpoints, params, `@PreAuthorize`, error codes match the controller |
| Doc ↔ Database | ✅ `BUSA_DOC_MSTR` + child tables match entities/migrations |
| Doc ↔ Frontend | ✅ `EInvoicesClient` consumes all paths; status filter client-side as documented |
| Doc ↔ Authorization | ✅ once both namespaces are kept distinct (see Cross-cutting) |

---

### Expenses e-Invoices

**Documentation Status:** **Accurate** (folder) · **Outdated** (single-file design-history spec — now corrected; it had an internal contradiction).

**Files Updated**
- [wecms-api/docs/features/addon-service/expenses-e-invoices.md](../features/addon-service/expenses-e-invoices.md) — Status line, docType table, docType paragraph.

**Corrections Made**
- Status line **self-contradiction removed**: the file's top banner said the `EXPENSE→'E'` bug was resolved, but the inline Status line still listed it as "the one feature-specific open item." Corrected to the implemented `EXPENSE`/`BILL`→`'B'` (`'E'`/`'S'`→`WBE2002`); remaining gap reframed as the client-side status filter only.
- docType reject rows: `WBE0001` → **`WBE2002`**.
- docType paragraph: "currently maps `EXPENSE → 'E'` — incorrect, blocks the Expenses page" → corrected (page is not blocked; mapping is `'B'`).

**Remaining Gaps** — same as Sales (shared backend): client-side status filter (IR-4); has-line-items predicate; history/detail-scrape/detail-export tests.

**Consistency Report**
| Axis | Result |
|---|---|
| Doc ↔ API | ✅ after fix — Expenses uses the shared `/einvoices` endpoints with `type=EXPENSE` |
| Doc ↔ Database | ✅ shares `BUSA_DOC_MSTR` (`DOC_TYPE='B'`) |
| Doc ↔ Frontend | ✅ `ExpensesEInvoicesPage` → `EInvoicesClient type="EXPENSE"` |
| Doc ↔ Authorization | ✅ permission_cd `addon-einvoices-expenses` / screenId `addon-expenses-einvoices` |

---

### Operation Status Inquiry

**Documentation Status:** **Accurate** (folder, both API + WEB) · **Outdated** (both single-file design-history specs — now corrected).

**Files Updated**
- [wecms-api/docs/features/addon-service/operation-status-inquiry.md](../features/addon-service/operation-status-inquiry.md) — Status line.
- [wecms-web/docs/features/addon-service/operation-status-inquiry.md](../../../wecms-web/docs/features/addon-service/operation-status-inquiry.md) — Status line.

**Corrections Made**
- API single-file Status: "the ONLY inert piece is the inquire-dialog **Save/persist** — there is no backend upsert endpoint yet" → **stale**. `POST /api/v1/addon/operation-status` (`OperationStatusService#create`, [controller L100-104](../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java#L100-L104)) persists to `OPRT_STTS_INFO` with a `(BIZ_KEY,TAXCODE)` duplicate guard. `inquire` and `bulkUpdate` call the live `OsmcGdtClient` GDT bridge.
- WEB single-file Status: "status filter / pagination / sort / Print / Excel / Update / Inquire handlers NOT wired" → **wired** (verified in `lib/api/addon.ts` + components).

**Remaining Gaps**
- Per-row delete (`aosv_0001_002_d001`) intentionally not ported (matches the refactor page — folder docs note it).
- GDT status-string localisation (`wb_get_operat_stts`) not ported — `resolveStatusCd` maps to a 3-state code in Java instead.
- No controller/integration test for `create`/`inquire`/`update` beyond the service unit test.

**Consistency Report**
| Axis | Result |
|---|---|
| Doc ↔ API | ✅ after fix — 5 endpoints (list/export/create/inquire/update) match the controller |
| Doc ↔ Database | ✅ `OPRT_STTS_INFO` + audit tables (`EXT_TRAN_CALL_HIS`, `NXSIAS_MODULE_TRAN_LOG_HIS`) |
| Doc ↔ Frontend | ✅ Inquire dialog Save + bulk Update + export all wired |
| Doc ↔ Authorization | ✅ class-level `@menuAuthz.canAccess('addon-operation-status-inquiry')` |

---

## Cross-cutting findings

### 1. Two identifier namespaces — keep distinct (highest-risk documentation hazard)

| Concern | Sales | Expenses | Operation Status |
|---|---|---|---|
| **Menu permission_cd** (`@menuAuthz`, `MENU_ITEM.PERMISSION_CD`, nav tree) | `addon-einvoices-sales` | `addon-einvoices-expenses` | `addon-operation-status-inquiry` |
| **View Setting / export screenId** | `addon-sales-einvoices` | `addon-expenses-einvoices` | `addon-operation-status` |

The word order flips (`einvoices-sales` ↔ `sales-einvoices`). The folder docs already use each correctly; any **new** doc must not substitute one for the other. The runtime guard checks the permission_cd; the export/View-Setting layer checks the screenId.

### 2. CLAUDE.md Feature-Status table is stale (not a `docs/` file, flagged for the next CLAUDE.md pass)

[wecms-api/CLAUDE.md](../../CLAUDE.md) "Feature Status" rows still say:
- `domain/einvoice` — "backend `EInvoiceService.resolveDocType` maps EXPENSE→`'E'` … **open code bug to file**" → **stale**, code maps `'B'`.
- `domain/operation` — "the inquire-dialog **Save/persist** action is a stub (no backend upsert endpoint yet)" → **stale**, `create()` exists.

These were **not** edited (CLAUDE.md is project-instruction config, outside the `docs/` audit scope). Recommend correcting them in the next CLAUDE.md maintenance pass.

### 3. Authorization is product-gated — H tenants get no Add-on

V243 seeds Add-on permission_cds for **F and M only**; HAENGSUNG (H) omits the Add-on tree. `MenuAuthorizationGuard` enforces the product-tree gate before the per-user grant, so an H caller is denied every addon endpoint. Folder docs are consistent with this; no change needed.

---

## Files updated this audit (complete list)

1. `wecms-api/docs/features/addon-service/sales-e-invoices.md`
2. `wecms-api/docs/features/addon-service/expenses-e-invoices.md`
3. `wecms-api/docs/features/addon-service/operation-status-inquiry.md`
4. `wecms-web/docs/features/addon-service/operation-status-inquiry.md`
5. `wecms-api/docs/audits/addon-service-docs-audit.md` *(this file — new)*
6. `wecms-api/docs/audits/addon-service-gap-analysis.md` *(new)*
7. `wecms-api/docs/audits/addon-service-source-index.md` *(new)*

**Not modified (verified accurate):** every file under `…/addon-service/<module>/` on both API and WEB sides, and `…/addon-service/README.md` / `authorization-and-product-type.md` / `menu-route-authorization.md`.

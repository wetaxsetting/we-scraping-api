# Service Mapping — JEX → Spring Boot

Cross-feature index of JEX services translated (or planned for translation) into Spring Boot REST endpoints in `wecms-api`.

JEX is **read-only reference**. This file exists for traceability only — never modify JEX side.

## Mapping table

| JEX WSVC ID | JEX `_act.jsp` | Spring Endpoint | Method | Frontend Route | Domain | Status | Refactor Doc |
|---|---|---|---|---|---|---|---|
| `logn_0001_001_r001` | `logn_0001_001_r001_act.jsp` | `/api/v1/auth/login` | POST | `/login` | `auth` | Working | [../../features/auth/login-auth.md](../../features/auth/login-auth.md) |
| `logn_0001_001_r002` | `logn_0001_001_r002_act.jsp` | `/api/v1/auth/me` | GET | `(dashboard layout)` | `auth` | Working | [../../features/auth/login-auth.md](../../features/auth/login-auth.md) |
| `comm_0108_001_r001_osmc` | `comm_0108_001_r001_osmc_act.jsp` | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | GET | (any list page popover) | `setting` | Working | [../../features/common/view-setting.md](../../features/common/view-setting.md) |
| `comm_0108_001_u002_osmc` | `comm_0108_001_u002_osmc_act.jsp` | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | PUT | (any list page popover) | `setting` | Working | [../../features/common/view-setting.md](../../features/common/view-setting.md) |
| `comm_0108_001_u001_osmc` | `comm_0108_001_u001_osmc_act.jsp` | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | DELETE | (any list page popover) | `setting` | Working | [../../features/common/view-setting.md](../../features/common/view-setting.md) |
| `fnsh_0001_001_r001` | `fnsh_0001_001_r001_act.jsp` | `/api/v1/banking/accounts` | GET | `/banking/account-inquiry` | `banking` | Working | [../api-spec/banking/account-inquiry-vnd-api.md](../api-spec/banking/account-inquiry-vnd-api.md) |
| `fnsh_0001_001` (export) | (manual export path in JEX) | `/api/v1/banking/accounts/export` | POST | `/banking/account-inquiry` | `banking` | Working | [../api-spec/banking/account-inquiry-vnd-api.md](../api-spec/banking/account-inquiry-vnd-api.md) |
| `fnsh_0005_001_r001` *(USD-only in legacy)* | `fnsh_0005_001_r001_act.jsp` *(hardcoded `CcyCd.USD`)* | `/api/v1/banking/accounts` *(query: `currency=FCY` (all non-VND) or `currency=<CCY>` (concrete narrow))* | GET | `/banking/account-inquiry-fcy` | `banking` | Working — multi-CCY scope is a **refactor enhancement** | [../api-spec/banking/account-inquiry-fcy-api.md](../api-spec/banking/account-inquiry-fcy-api.md) |
| `fnsh_0005_001` (export, FCY mode) | (manual export path in JEX) | `/api/v1/banking/accounts/export` *(body: `screenId="banking-account-inquiry-fcy"`)* | POST | `/banking/account-inquiry-fcy` | `banking` | Working — `AccountInquiryFcyExportProvider` | [../api-spec/banking/account-inquiry-fcy-api.md](../api-spec/banking/account-inquiry-fcy-api.md) |
| `einv_0001_001_r001` *(`einvType=1` ⇒ Sales — same JSP also drives Expenses when `einvType=2`)* | `einv_0001_001_r001_act.jsp` | `/api/v1/addon/einvoices` *(query: `type=SALES`)* | GET | `/addon/sales-einvoices` | `einvoice` | Planned — skeleton implemented (controller + service + entity + repo + DTOs); filters / pagination / sort / Excel export pending | [../features/addon-service/sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md) · [../backend/addon/sales-e-invoices-api.md](../api-spec/addon/sales-e-invoices-api.md) |
| `einv_0001_001_r001` *(`einvType=2` ⇒ Expenses — shared service / shared JSP / shared backend with Sales)* | `einv_0001_001_r001_act.jsp` | `/api/v1/addon/einvoices` *(query: `type=EXPENSE`)* | GET | `/addon/expenses-einvoices` | `einvoice` | Planned — shares the Sales backend; **blocked on the `EXPENSE → 'B'` mapping fix** (IR-1) | [../features/addon-service/expenses-e-invoices.md](../../features/addon-service/expenses-e-invoices.md) · [../backend/addon/expenses-e-invoices-api.md](../api-spec/addon/expenses-e-invoices-api.md) |
| `einv_0001_001_r002_excel` *(Sales export)* | (legacy export action) | `/api/v1/addon/einvoices/export` *(body: `screenId="addon-sales-einvoices"`)* | POST | `/addon/sales-einvoices` | `einvoice` | Planned — `EInvoiceExportProvider` to be added per [§ Excel export](../../../CLAUDE.md) | [../backend/addon/sales-e-invoices-api.md](../api-spec/addon/sales-e-invoices-api.md) |
| `einv_0001_001_r002_excel` *(Expenses export — shared service)* | (legacy export action) | `/api/v1/addon/einvoices/export` *(body: `screenId="addon-expenses-einvoices"`)* | POST | `/addon/expenses-einvoices` | `einvoice` | Planned — second `screenId` on the same `EInvoiceExportProvider` (or sibling registration); request body's `filters.type=EXPENSE` discriminates `DOC_TYPE` | [../backend/addon/expenses-e-invoices-api.md](../api-spec/addon/expenses-e-invoices-api.md) |
| `aosv_0001_001_r001` | `aosv_0001_001_r001_act.jsp` | `/api/v1/addon/operation-status` | GET | `/addon/operation-status` | `operation` | Planned — read endpoint working end-to-end; `companyStatus` filter / pagination / sort / Excel export pending | [../features/addon-service/operation-status-inquiry.md](../../features/addon-service/operation-status-inquiry.md) · [../backend/addon/operation-status-inquiry-api.md](../api-spec/addon/operation-status-inquiry-api.md) |
| `aosv_0001_001` (Excel export — legacy inline TS path) | (no separate WSVC; legacy exports inline via `aosv_0001_001.ts`) | `/api/v1/addon/operation-status/export` *(body: `screenId="addon-operation-status"`)* | POST | `/addon/operation-status` | `operation` | Planned — `OperationStatusExportProvider` to be added per [§ Excel export](../../../CLAUDE.md) | [../backend/addon/operation-status-inquiry-api.md](../api-spec/addon/operation-status-inquiry-api.md) |
| `nxisas_oprtstts_*` *(legacy GDT-pull bridge via OSMC; see `OperationStatusService.java:21` TODO referencing OSMC `TRCO_TRCO001_00003`)* | (NXISAS / OSMC handler — not in `wabooks` action tree) | `/api/v1/addon/operation-status/inquire` | POST | `/addon/operation-status` | `operation` | Deferred — refactor R&D scope is local-only (no NXISAS / no GDT integration); Phase 4 ships a stub returning `WBE_NOT_IMPLEMENTED` | [../features/addon-service/operation-status-inquiry.md](../../features/addon-service/operation-status-inquiry.md) |
| `aosv_0001_001_u001` *(manual operator edit)* | `aosv_0001_001_u001_act.jsp` | `/api/v1/addon/operation-status/{taxcode}` (PUT) | PUT | `/addon/operation-status` | `operation` | Deferred — refactor is read-only on this page; no PO ask for manual edits | [../features/addon-service/operation-status-inquiry.md](../../features/addon-service/operation-status-inquiry.md) |
| `aosv_0001_002_*` *(Add Operation Status modal — `_r001` / `_c001`)* | `aosv_0001_002_*_act.jsp` | (no refactor endpoint) | — | (no route) | `operation` | Deferred — manual-add modal out of refactor scope | — |
| `comm_0221_001_r001` *(read — five-leg unified history)* | `comm_0221_001_r001_act.jsp` | `/api/v1/settings/firm-banking-history` | GET | (Settings modal panel — `useSettingsModal().open("firm_banking_history")`; no standalone route per [feature spec § 4.4](../../features/settings/firm-banking-history.md)) | `setting` (sub-package `setting/firmbankinghistory`) | Working — Phase 2 backend (controller + service + repository + `FirmBankingHistoryView` over Flyway V60 view), Phase 3 frontend wiring (filters / table / popover via shared `<InteractiveDateRangePicker>` + `<TableHeaderCell>` + `useApiData`), Phase 3b/c/d resolvers (`dictionaryVan` / `scrapingResultCodes` / `TranTp` / `FirmBankingSvcType` / `FirmBankingServiceNameResolver`) | [../features/settings/firm-banking-history/](../../features/settings/firm-banking-history/README.md) · [./firm-banking-history-mapping.md](firm-banking-history-mapping.md) · [../database/settings/firm-banking-history-schema.md](../database/settings/firm-banking-history-schema.md) · [../audits/firm-banking-history-parity-checklist.md](../audits/firm-banking-history-parity-checklist.md) |

> Add a row here when starting work on a new JEX service. Keep the kebab-case slug consistent across `features/`, `backend/`, `frontend/`, `database/`, and `migration/`.

## Status legend

- **Working** — refactor endpoint implemented and exercised in local R&D
- **In progress** — design started; partial implementation
- **Planned** — slated for an upcoming sprint, not yet started
- **Deferred** — intentionally not in scope for the current refactor phase

## Field-level mapping convention

Per-feature mapping files (e.g. [account-inquiry-vnd.md](../../features/banking/current-savings-vnd/account-inquiry.md)) carry the field-by-field translation:

```
JEX WSVC field (UPPER_SNAKE)  →  Java property (camelCase)  →  JSON @JsonProperty (UPPER_SNAKE)
ACCT_SEQ_NO                   →  acctSeqNo                  →  ACCT_SEQ_NO
```

## FCY-specific notes for `account-inquiry-fcy`

The legacy live FCY page is **`fnsh_0005_001` (USD-only)** — it hardcodes `CcyCd.USD` in `fnsh_0005_001_r001_act.jsp` and `CCY_CD: "USD"` in `fnsh_0005_001.ts`. There is no legacy KRW or "all FCY" account-list page.

The refactor consolidates VND (`fnsh_0001_001_r001`) and USD (`fnsh_0005_001_r001`) onto **one Spring endpoint** (`GET /api/v1/banking/accounts`) and **extends** the FCY scope to cover every non-VND CCY (USD, KRW, EUR, JPY, …). This multi-CCY behaviour is a **deliberate refactor enhancement** with no JEX equivalent.

**Wire `currency` resolution:**

| Wire `currency` | Backend filter | Page that sends it |
|---|---|---|
| `VND` | `WHERE CCY_CD = 'VND'` | VND page (always) — mirrors legacy `fnsh_0001_001_r001` |
| `FCY` | `WHERE CCY_CD <> 'VND'` | FCY page, "All Currency" mode (default) — **refactor enhancement**, no JEX parity |
| `<CCY>` (`USD`, `KRW`, `EUR`, …) | `WHERE CCY_CD = <CCY>` | FCY page, narrowed via the in-page currency dropdown — `USD` mirrors legacy `fnsh_0005_001_r001` |
| `ALL` | (no CCY predicate) | (not used by either page) |

**Account-type filter (legacy):** the legacy helper additionally applies `acct_type_cd IN (...)` per CCY: `('03','05','02')` for VND, `('04','06','02')` for USD. The refactor currently relies solely on the `ccy_cd` predicate; adding the `acct_type_cd` filter is tracked as Plan 3 and will land in a follow-up MR. See [`AcctTypeCd.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/AcctTypeCd.java) for the canonical code → label mapping (mirrored by `BankCdResolver.ACCT_TYPES`).

**USD/KRW summary card behaviour:** the FCY page renders **one summary tile per CCY group** sourced from `AccountListResponse.TOTALS_BY_CCY[]`. With `currency=FCY` the array carries one entry per non-VND CCY in the result (typically USD + KRW + possibly EUR/JPY); with a concrete CCY narrow it carries a single entry. Mixed-currency arithmetic is never rendered.

**FCY amount formatting:** every monetary string (per-row `BAL`/`REAL_AMT` in the table, per-tile total in the summary card, subheader line in the Excel export) is formatted via `formatAmount(value, row.CCY_CD, true)` on the frontend or `AmountFormatter.forDisplay(value, ccyCd)` on the backend.

**Backend (`AmountFormatter.forDisplay`) — canonical rule set:**

| CCY | Locale | Symbol position | Decimals |
|---|---|---|---|
| `USD` | en-US | prefix `$` | 2 |
| `EUR` | en-US | prefix `€` | 2 |
| `KRW` | en-US | prefix `₩` | 0 |
| `JPY` | en-US | prefix `¥` | 0 |
| `VND` | vi-VN | suffix `₫` | 0 |
| other | vi-VN | none | 0 |

**Frontend (`lib/currency.ts formatAmount`) — current implementation diverges:** today the frontend only special-cases `USD` (`en-US`, 2 dp) and falls through to vi-VN/0-dp for everything else. EUR therefore rounds to integer, KRW/JPY use vi-VN dot separators instead of en-US commas. The on-screen tile and the Excel subheader print different numbers for these CCYs. **Aligning the frontend table to match the backend table above is tracked as Plan 1.**

**Currency filter behaviour:** the in-page currency dropdown (FCY-only) flips the wire `currency` between `FCY` and a concrete CCY. Page resets to 0 on every change. Options are cached from the first unfiltered FCY response so they persist when the user narrows.

**Excel export:** the FCY screen's export is dispatched by `screenId="banking-account-inquiry-fcy"` to `AccountInquiryFcyExportProvider`, which composes a multi-line subheader (one line per CCY group) when wire `currency=FCY` and a single line when wire is a concrete CCY. The single-line case reads identically to a VND export.

## Add-on Service > Sales e-Invoices

Outbound (customer-side) e-Invoice listing under the Add-on Service module. Sales (`type=SALES` ⇒ `DOC_TYPE='I'`) and Expenses (`type=EXPENSE` ⇒ `DOC_TYPE='E'`) share **one Spring endpoint** (`GET /api/v1/addon/einvoices`) and **one Next.js page shell** (`<EInvoicesPage>`); the two routes only differ by the `type` query param and the i18n prefix.

### Legacy JEX service IDs

| JEX asset | Path |
|---|---|
| WSVC (read — Sales + Expenses share the same service shape) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml` |
| WSVC (page) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001.xml` |
| WSVC (Excel export) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r002_excel.xml` |
| WSVC (write — invoice CRUD; **out of refactor scope**) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_u001.xml` |
| Action JSP (read) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp` |
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` (`einvType=1` ⇒ Sales, `einvType=2` ⇒ Expenses) |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts` |
| Live DEV URL | `https://dev.wecms.vn/einv_0001_001.act?einvType=1` |

The Expenses sibling uses the **same** physical JSP / TypeScript / WSVC. The legacy `einv_0002_001_r001` listed under `WSVC.einv_0002_001_r001.xml` is a separate BCS-side service (likely the bank-matching ingestion path) — **not** the Expenses listing page.

**Sales counterpart on the wire:** `DOC_TYPE='I'` (per the 2026-04-29 DEV seed audit — see open question in the planning doc; controller currently accepts both `SALES`/`I` aliases).

### Proposed Spring Boot endpoints

| Method | Path | Status | Notes |
|---|---|---|---|
| `GET` | `/api/v1/addon/einvoices?type=SALES` | Skeleton implemented | Optional `from` / `to` (`YYYYMMDD`); `invoiceStatus`, `keyword`, `page`, `size`, `sortField`, `sortDirection` planned |
| `POST` | `/api/v1/addon/einvoices/export` | Planned | Per-resource Excel export per [§ Excel export](../../../CLAUDE.md) — `screenId="addon-sales-einvoices"` |

Backend handler today: [`EInvoiceController#list`](../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) → [`EInvoiceService#list`](../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java) → [`BusaDocMstrRepository`](../../../src/main/java/com/kosign/wecms/domain/einvoice/repository/BusaDocMstrRepository.java) → entity [`BusaDocMstr`](../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstr.java) (Sprint 1 column subset; DB schema is full DEV parity per V15 + V23).

### Frontend route

```
/addon/sales-einvoices
```

Page file: [`wecms-web/app/(dashboard)/addon/sales-einvoices/page.tsx`](../../../../wecms-web/app/(dashboard)/addon/sales-einvoices/page.tsx). Page shell shared with Expenses via `<EInvoicesPage translationPrefix="sales_einvoices">`; client wrapper [`<EInvoicesClient type="SALES">`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesClient.tsx) calls `fetchEInvoices(type)`.

### Status: **Planned — Phase 2 implementation approved**

Static UI is complete and approved (one of the four canonical UI baselines per [wecms-web/CLAUDE.md § Approved reference set](../../../../wecms-web/CLAUDE.md)). Backend skeleton returns rows but the wire shape is not yet aligned with the full UI column set. Filters / sort / pagination / Excel / Print / Update buttons are all unwired. View Setting + table sort + Excel export adoption is pending — same shape as Account Inquiry's adoption.

**Implementation rules are locked.** See [planning § Phase 2 implementation rules (locked decisions)](../../planning/addon/sales-e-invoices-plan.md#phase-2-implementation-rules-locked-decisions) — IR-1 through IR-7 cover `DOC_TYPE` mapping, baseline filters, date-range validation, status localisation strategy, simplified search scope, totals computation, and the `INV_TYPE` cleanup. **IR-9** scopes View Setting: data + column-metadata consumption is in scope, but the user-facing popover / list-view toolbar icon / Update / Reset UI is out of scope for the Sales e-Invoices page (toolbar stays Print + Excel + Update only). PO/designer items in the same doc's "Deferred / PO decision" table do NOT block implementation.

### Field-by-field mapping (subset wired today)

```
JEX WSVC field (UPPER_SNAKE)  →  Java property (camelCase)  →  JSON @JsonProperty (UPPER_SNAKE)
DOC_ID                        →  docId                       →  DOC_ID
ISSU_DT                       →  issuDt                      →  ISSU_DT
(REG_DTM[8..14])              →  issuTm  (refactor proxy)     →  ISSU_TM   ← NOT in legacy
INV_TYPE                      →  invType                     →  INV_TYPE  ← legacy WSVC drops it; mock value mislabels it
EINV_INVOICESYMBOL            →  einvInvoicesymbol           →  EINV_INVOICESYMBOL
TRCO_CMNM                     →  trcoCmnm                    →  TRCO_CMNM
TRCO_TAX_ID                   →  trcoTaxId                   →  TRCO_TAX_ID
TRCO_TRSC_CCY_CD              →  trcoTrscCcyCd               →  TRCO_TRSC_CCY_CD
TOTL_AMT                      →  totlAmt  (BigDecimal)        →  TOTL_AMT  (string, 2dp, trailing zeros stripped)
SPLY_AMT_SUM                  →  splyAmtSum                  →  SPLY_AMT_SUM
TAX_AMT_SUM                   →  taxAmtSum                   →  TAX_AMT_SUM
EINV_INVOICESTATUS            →  einvInvoicestatus           →  EINV_INVOICESTATUS  ← raw stored value today; needs wb_get_invoice_status parity
```

12 of 41 legacy `REC` fields exposed today. **Phase 2 priority adds:** `COMPANYSTATUS`, `EINV_INVOICESTATUS_BEF`, `READ_YN`, `OVER_DUE_DAYS`, `DUE_DT`, `NOTE`, `PROV_STTS`, `RCV_SND_YN`, `HAND_REG_YN`, `DISP_DOC_ID`. **Drop from priority:** `COLOR` and `MEMO` (memo-sticker concerns, not status — out of listing-page scope). Recommend dropping `INV_TYPE` from the wire altogether (legacy WSVC doesn't expose it; the static UI's mock value is a misnaming of `EINV_INVOICESTATUS`).

**Wire-level pagination + totals shape change** (planned): `EInvoiceListResponse` gains `TOTAL_PAGES` / `PAGE_NUMBER` / `PAGE_SIZE`; `TOTALS[]` rows gain `TAX` + `PRETAX` per-currency aggregates. `TOTALS[]` is computed by a sibling unpaged aggregation query so footer numbers stay stable across pagination.

### Notes / open questions

**Verified-and-locked mappings (resolved against legacy code):**

- ✅ `DOC_TYPE` for Sales = `'I'` (`BusaDocType.INVOICE`); for Bill/Expense = `'B'` (`BusaDocType.BILL`). `'E'` and `'S'` are rejected by the listing service — they belong to `EXPENSE` / `PAYROLLBOOK` constants used in different flows.
- ✅ `INV_TYPE` is a Commercial-vs-Tax classifier (`'C'` → `Commercial`, otherwise → `Tax`). Legacy WSVC drops it. NOT to be confused with `EINV_INVOICESTATUS`.
- ✅ `EINV_INVOICESTATUS` is localised via `wb_get_invoice_status(value, LANG_CD)`. Six logical states: Original, Replacement, Adjustment, Replaced, Adjusted, Cancelled — each surfaces in vi/en/ko per the user's locale.
- ✅ `COLOR` is a per-doc memo sticker CSS class (default `"white"`) — NOT a status badge field. Status tint comes from regex matching of the localised `EINV_INVOICESTATUS`.
- ✅ `PROV_STTS` is a 6-value enum (`OP`/`PP`/`PA`/`OD`/`VD`/`EE`); the listing baseline always filters out `VD` + `EE`.
- ✅ Legacy `SRCH_WD` is View-Setting-driven across 16 candidate `FIELD_ID`s with per-field predicate variation; `EINV_INVOICESTATUS` is NOT searchable.
- ✅ Legacy doesn't expose `ISSU_TM` — the refactor's `REG_DTM[8..14]` proxy is a refactor-only invention.
- ✅ Always-applied legacy baseline filters: `PROV_STTS NOT IN ('VD', 'EE')`, `HAND_REG_YN = 'N'`, `(DOC_ID, BIZ_KEY) IN (SELECT … FROM BUSA_DOC_DETL)`. `from`/`to` are required.

**Open follow-ups (still need decisions):**

1. **Wire enum** — settle on `SALES`/`EXPENSE` only. The current controller's `EXPENSE → 'E'` mapping is a bug; legacy is `'B'`. *Owner: PO + Sonny.*
2. **`EINV_INVOICESTATUS` localisation strategy** — port `wb_get_invoice_status` to local Flyway, replicate regex in Java emitting canonical keys, or replicate regex frontend-side. *Owner: Sonny + Arthur.*
3. **`SRCH_WD` parity scope** — replicate legacy View-Setting-driven walk verbatim, ship a fixed Phase-2 subset, or hybrid. Decide whether to port `wb_get_translate_text` for accent folding. *Owner: Sonny.*
4. **Per-status totals** — legacy returns `TOT_OP_INV_*`, `TOT_OD_INV_*`, `TOT_PA_INV_*`, `TOT_INV_*`. Refactor footer renders per-currency only today; confirm whether per-status totals are needed. *Owner: PO.*
5. **`EINV_INVOICESYMBOL` length** — entity says 10, V17 widened to 50; verify the entity reflects the right length to avoid load-time truncation. *Owner: Arthur.*
6. **`ISSU_TM` policy** — drop the time row, relabel it "Registered at", or source from another column. Legacy doesn't expose this. *Owner: PO.*
7. **GDT live-pull (`Update` button)** — the legacy "Update" button pulls fresh status from the GDT via the NXISAS bridge. Refactor R&D scope is local-only; first implementation is a `refreshKey++` local refetch. *Owner: Sonny.*
8. **View Setting widths** — extract Figma WB365 widths for the Sales e-Invoices frame (Figma node 9194:6696) and use them in the seed migration's `SIZE` column. Mirror the V34 pattern from Account Inquiry. *Owner: Designer + Arthur.*
9. **Sister feature (Expenses) bundling** — `addon-expenses-einvoices` shares the page shell. Land both in one MR vs two? **Note:** the `EXPENSE → 'E'` bug must be fixed before any Expenses-side wiring. *Owner: Sonny.*
10. **JEX feature doc gap** — no `docs/jex/features/sales-e-invoices.md` exists. The WSVC XML + view JSP are the only legacy spec. Authoring the JEX-side doc is optional but improves traceability. *Owner: Sonny.*

### Refactor doc cross-references

- Feature spec: [../features/addon-service/sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md)
- Frontend page: [../frontend/addon/sales-e-invoices-page.md](../../../../wecms-web/docs/features/addon-service/sales-e-invoices.md)
- Backend API: [../backend/addon/sales-e-invoices-api.md](../api-spec/addon/sales-e-invoices-api.md)
- Database: [../database/addon/sales-e-invoices-db.md](../database/addon/sales-e-invoices-db.md)
- Implementation plan: [../planning/addon/sales-e-invoices-plan.md](../../planning/addon/sales-e-invoices-plan.md)

## Add-on Service > Expenses e-Invoices

Inbound (supplier-side) e-Invoice listing under the Add-on Service module. **Sister page to Sales** — shares the Spring endpoint, the page shell, the entity, the repository, the DTOs, and (at the database level) the same physical table. The two routes only differ by `type` query param + `translationPrefix` + the `BUYER` column header label.

### Legacy JEX service IDs

Same JEX assets as Sales — the legacy view, action JSP, WSVC, IDO, and TypeScript files all branch internally on `einvType` (`1` ⇒ Sales / `2` ⇒ Expenses). See [Sales § Legacy JEX service IDs](#legacy-jex-service-ids) for the full path table.

| JEX asset (Expenses-relevant deltas) | Path |
|---|---|
| Live DEV URL | `https://dev.wecms.vn/einv_0001_001.act?einvType=2` |
| Action-JSP branch | `einv_0001_001_r001_act.jsp:268-284` resolves `einvType=2` → `dymId='table2'` → `DOC_TYPE='B'` |
| Search-helper whitelist | `SearchHelper.java#L89` keyed by `dymId='table2'` (verify against `'table1'` for divergence — see Phase 2 task #7 in the planning doc) |

The legacy `WSVC.einv_0002_001_r001.xml` file is a **separate BCS-side service** (likely the bank-matching ingestion path), NOT the Expenses listing page. Do NOT mirror it.

**Expenses counterpart on the wire:** `DOC_TYPE='B'` (verified against `BusaDocType.java` and `einv_0001_001_r001_act.jsp`). The current `EInvoiceService.resolveDocType` mapping `EXPENSE → 'E'` is a **bug** — must be `'B'`. See [Sales planning IR-1](../../planning/addon/sales-e-invoices-plan.md#ir-1-doc_type-mapping--final-rule).

### Proposed Spring Boot endpoints

| Method | Path | Status | Notes |
|---|---|---|---|
| `GET` | `/api/v1/addon/einvoices?type=EXPENSE` | Skeleton implemented (shared with Sales) | Same params as Sales (`from`, `to` required; `invoiceStatus`, `keyword`, `page`, `size`, `sortField`, `sortDirection` planned). **Returns zero rows today** because `resolveDocType` maps `EXPENSE → 'E'` — the local seed carries `'B'`. |
| `POST` | `/api/v1/addon/einvoices/export` | Planned (shared endpoint, Expenses-specific `screenId`) | Same export pipeline; `screenId="addon-expenses-einvoices"`; request body's `filters.type=EXPENSE` discriminates `DOC_TYPE` |

Backend handler: same `EInvoiceController#list` → `EInvoiceService#list` → `BusaDocMstrRepository` → `BusaDocMstr` chain as Sales. **No Expenses-specific Java class exists or should be added** — the discrimination is a one-line branch on `DOC_TYPE` inside the shared service.

### Frontend route

```
/addon/expenses-einvoices
```

Page file: [`wecms-web/app/(dashboard)/addon/expenses-einvoices/page.tsx`](../../../../wecms-web/app/(dashboard)/addon/expenses-einvoices/page.tsx). Entry wrapper: [`<ExpensesEInvoicesPage>`](../../../../wecms-web/components/addon/expenses-einvoices/ExpensesEInvoicesPage.tsx) — two lines of meaningful code, composes the shared `<EInvoicesClient type="EXPENSE" translationPrefix="expenses_einvoices" />`.

### Status: **Planned — shared implementation with Sales**

Static UI is complete (sibling of one of the four canonical UI baselines per [wecms-web/CLAUDE.md § Approved reference set](../../../../wecms-web/CLAUDE.md)). Backend skeleton is shared with Sales. Filters / sort / pagination / Excel / Print / Update buttons are unwired — same as Sales. Lands **behind Sales** — every shared component / hook / helper / endpoint that lights up for Sales lights up for Expenses on the same MR.

**Implementation rules are inherited from the Sales planning doc — IR-1 through IR-9 apply verbatim.** The Expenses-specific Phase 2 work is:

1. Fix `EInvoiceService.resolveDocType` (IR-1) — `EXPENSE → 'B'`. Primary blocker for the page.
2. Verify the `dymId='table2'` keyword-search whitelist against `SearchHelper.java`.
3. Audit `BUSA_DOC_MSTR` for `DOC_TYPE='B'` row coverage under `BIZ_KEY=VN_VNM0004_000001`. Add a demo-seed migration if sparse (max 30 real DEV rows).
4. Seed `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-expenses-einvoices'` — same column shape as Sales; bundle in the same migration when possible.
5. Add `SCREEN_IDS.ADDON_EXPENSES_EINVOICES = "addon-expenses-einvoices"` to `lib/viewSettingScreens.ts`.
6. Confirm Expenses i18n keys (`expenses_einvoices.*`) cover every column header, status string, and toolbar label.

Per IR-9, the View Setting management UI (popover, list-view toolbar icon, Update / Reset controls) is **out of scope** for the Expenses page just as it is for Sales — the toolbar stays Print + Excel + Update only.

### UI label differences only

The only user-visible difference between Sales and Expenses (post-IR-1 fix) is **column header labels**:

| Concern | Sales | Expenses |
|---|---|---|
| Counterparty column header | "Buyer" | **"Supplier"** |
| Counterparty cell `MST :` prefix | `sales_einvoices.mst_label` | `expenses_einvoices.mst_label` |
| Page title | "Sales e-Invoices" | "Expenses e-Invoices" |
| Excel filename | `Sales_e-Invoices_<scope>.xlsx` | `Expenses_e-Invoices_<scope>.xlsx` |

Underlying entity field, wire field name, View Setting `FIELD_ID` (subject to PO confirmation — see open question #3 below), and column composition are all identical. The compliance angle (input-VAT credit on suspended-MST suppliers) shifts the **business meaning** of `COMPANYSTATUS` for Expenses readers, but the field, wire shape, and rendering rule do not change.

### Field-by-field mapping

Identical to Sales — see [Sales § Field-by-field mapping](#field-by-field-mapping-subset-wired-today). Same 12 fields wired today; same 41 legacy `REC` fields available; same Phase 2 priority extension list (`COMPANYSTATUS`, `EINV_INVOICESTATUS_BEF`, `READ_YN`, `OVER_DUE_DAYS`, `DUE_DT`, `NOTE`, `PROV_STTS`, `RCV_SND_YN`, `HAND_REG_YN`, `DISP_DOC_ID`).

For Expenses, **`COMPANYSTATUS` is the highest-value Phase 2 addition** — it carries the supplier's GDT MST status, which is the page's primary compliance signal.

### Notes / open questions

**Verified-and-locked mappings (resolved against legacy code, inherited from Sales):**

- ✅ `DOC_TYPE='B'` for Expenses (NOT `'E'`). Verified against `einv_0001_001_r001_act.jsp` and `BusaDocType.java`.
- ✅ Same physical table as Sales — `BUSA_DOC_MSTR`.
- ✅ Same `PROV_STTS` baseline filters, same `HAND_REG_YN`, same `BUSA_DOC_DETL` join.
- ✅ Same six `EINV_INVOICESTATUS` logical states.
- ✅ Same `INV_TYPE` Commercial-vs-Tax classifier (drop from refactor wire per IR-7).
- ✅ Same `COLOR` memo-sticker semantics (out of listing-page scope).

**Open follow-ups (Expenses-specific):**

1. **`EXPENSE → 'B'` fix (IR-1)** — primary blocker. Drop the legacy `'E'` alias from the controller. *Owner: Arthur.*
2. **Verify `dymId='table2'` keyword-search whitelist** in `SearchHelper.java`. If it diverges from `'table1'`, document the delta. Phase 2 still ships the same fixed 5-column subset (per IR-5). *Owner: Arthur.*
3. **`BUYER` vs `SUPPLIER` `FIELD_ID`** for the Expenses View Setting seed. Reusing `BUYER` keeps the seed PK aligned with Sales and the column registry constant unchanged; renaming to `SUPPLIER` is clearer for cross-screen analytics. Both work technically. *Owner: PO.*
4. **Local DEV seed coverage for `DOC_TYPE='B'`** under `BIZ_KEY=VN_VNM0004_000001`. Audit before the IR-1 fix lands; add a demo-seed migration if sparse. *Owner: Arthur.*
5. **Stored language for `EINV_INVOICESTATUS` on `'B'` rows.** If different from `'I'` rows, the dropdown filter behaves differently across pages. Audit and decide whether to tighten IR-4. *Owner: Arthur.*
6. **`COMPANYSTATUS` data quality on `DOC_TYPE='B'` rows.** The page's primary compliance feature depends on this field being populated. Audit and document. *Owner: Arthur.*
7. **Action-bar component name.** `<SalesEInvoiceActionBar>` is shared by both pages despite the "Sales" prefix. Rename to `<EInvoiceActionBar>` in a follow-up MR (cosmetic; not blocking). *Owner: Arthur.*
8. **Per-supplier-status totals in the footer.** Compliance-driven aggregation ("Total of invoices from suspended-MST suppliers: VND X") is meaningful on Expenses but not on Sales. Legacy doesn't return this. PO decision. *Owner: PO + Compliance.*

### Refactor doc cross-references

- Feature spec: [../features/addon-service/expenses-e-invoices.md](../../features/addon-service/expenses-e-invoices.md)
- Frontend page: [../frontend/addon/expenses-e-invoices-page.md](../../../../wecms-web/docs/features/addon-service/expenses-e-invoices.md)
- Backend API: [../backend/addon/expenses-e-invoices-api.md](../api-spec/addon/expenses-e-invoices-api.md)
- Database: [../database/addon/expenses-e-invoices-db.md](../database/addon/expenses-e-invoices-db.md)
- Implementation plan: [../planning/addon/expenses-e-invoices-plan.md](../../planning/addon/expenses-e-invoices-plan.md)
- Sister docs (Sales — primary source for shared rules): [../features/addon-service/sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md), [../planning/addon/sales-e-invoices-plan.md](../../planning/addon/sales-e-invoices-plan.md)

## Add-on Service > Operation Status Inquiry

Read-only listing of every counterparty (customer or supplier) whose **Vietnamese tax-authority operating status** has been polled into WeCMS. Refactor read endpoint is working end-to-end; everything else (filter / pagination / sort / Excel / Inquire / Update) is pending. **No sister page** — the Figma frame (12346:109158) is unique to this feature; this is a new layout baseline within the Add-on module.

### Legacy JEX service IDs

| JEX asset | Path |
|---|---|
| WSVC (read) | [`WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml) |
| WSVC (page) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001.xml` |
| WSVC (update — out of scope) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_u001.xml` |
| WSVC (add-modal — out of scope) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_002.xml`, `_r001.xml`, `_c001.xml` |
| Action JSP (read) | [`WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp) — verified `wb_get_operat_stts(...)` is **commented out** in legacy; `BIZ_KEY` is the only always-applied filter |
| Action JSP (write — out of scope) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_u001_act.jsp` |
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/aosv/aosv_0001_001_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/aosv/aosv_0001_001.ts` (carries the 12-string Vietnamese status legend the refactor collapses to 3 canonical states) |
| IDO (paged read) | [`WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001.xml) |
| IDO (count) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.OPRT_STTS_INFO_R001_CNT.xml` |
| IDO (write — out of scope) | `IDO.OPRT_STTS_INFO_C001.xml`, `_U001.xml`, `_D001.xml` |
| Legacy entity / DAO | `WECMS DEV/WECMS/src/wabooks/entity/OprtSttsInfo.java` + `dao/OprtSttsInfoDAO.java` |
| GDT integration (out of scope) | `nxisas_oprtstts_*` services + OSMC bridge — not ported. Inline TODO in [`OperationStatusService.java:21`](../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) references the OSMC service ID `TRCO_TRCO001_00003` for the future refresh path. |
| Live DEV URL | `https://dev.wecms.vn/aosv_0001_001.act` |

### Proposed Spring Boot endpoints

| Method | Path | Status | Notes |
|---|---|---|---|
| `GET` | `/api/v1/addon/operation-status` | **Working (Sprint 1)** — read path returns rows for the current tenant; only `q` accepted today | Phase 4: add `companyStatus`, `page`, `size`, `sortField`, `sortDirection` |
| `POST` | `/api/v1/addon/operation-status/export` | Planned | Per-resource Excel export per [§ Excel export](../../../CLAUDE.md) — `screenId="addon-operation-status"`. Add `OperationStatusExportProvider implements ExportRowProvider`. |
| `POST` | `/api/v1/addon/operation-status/inquire` | Deferred (stub in Phase 4) | Bulk lookup-by-MST against GDT — refactor R&D scope is local-only; ship as `WBE_NOT_IMPLEMENTED` stub until / unless NXISAS bridge is ported |
| `PUT` | `/api/v1/addon/operation-status/{taxcode}` | Deferred | Manual operator edit — refactor is read-only on this page; listed for completeness |

Backend handler today: [`OperationStatusController#list`](../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java) → [`OperationStatusService#list`](../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java) → [`OprtSttsInfoRepository`](../../../src/main/java/com/kosign/wecms/domain/operation/repository/OprtSttsInfoRepository.java) → entity [`OprtSttsInfo`](../../../src/main/java/com/kosign/wecms/domain/operation/entity/OprtSttsInfo.java) (composite PK `(BIZ_KEY, TAXCODE)`) → table `OPRT_STTS_INFO` (created in V16, seeded in V17).

### Frontend route

```
/addon/operation-status
```

Page file: [`wecms-web/app/(dashboard)/addon/operation-status/page.tsx`](../../../../wecms-web/app/(dashboard)/addon/operation-status/page.tsx). Client wrapper [`<OperationStatusClient>`](../../../../wecms-web/components/addon/operation-status/OperationStatusClient.tsx) calls `fetchOperationStatus(q?)` via `useApiData`; rows pass through [`mapOperationCompanyRow`](../../../../wecms-web/lib/api/mappers.ts) which coerces the GDT free-text Vietnamese status into the canonical 3-state enum (`active_certificate_issued | suspended | not_registered`). Page shell [`<OperationStatusPage>`](../../../../wecms-web/components/addon/operation-status/OperationStatusPage.tsx) (Figma 12346:109158) renders a 5-column table via `<DataTableShell footerMode="paginationOnly">` (no totals row).

### Status: **Planned**

Static UI is complete (Figma 12346:109158). Backend read endpoint is working end-to-end. **Filters / sort / pagination / Excel / Print / Update / Inquire buttons are unwired.** View Setting + table sort + Excel export adoption is pending — same shape as Sales e-Invoices' pending adoption.

### Field-by-field mapping (subset wired today)

```
JEX WSVC field (UPPER_SNAKE)  →  Java property (camelCase)  →  JSON @JsonProperty (UPPER_SNAKE)
TAXCODE                       →  taxcode                    →  TAXCODE
COMPANYNAME                   →  companyname                →  COMPANY_NM       ⚠️ diverges (Phase 4 task #6)
COMPANYSTATUS                 →  companystatus              →  COMPANY_STATUS   ⚠️ diverges
(refactor-only — derived)     →  (resolved server-side)     →  STATUS_CD        ← canonical 3-state enum
OFFICEADD                     →  officeadd                  →  OFFICE_ADD       ⚠️ diverges
SEARCHDATETIME                →  searchdatetime             →  LAST_UPDATE      ⚠️ diverges (renamed)
REPRESENTATIVEFIRSTNAME       →  representativefirstname    →  (not surfaced today; Phase 4 task #6)
```

6 of 7 user-visible legacy `REC` fields exposed (refactor adds `STATUS_CD`; drops `REPRESENTATIVEFIRSTNAME` for now). Legacy `BIZ_KEY` / `REG_*` / `MOD_*` are not exposed by the WSVC `<out>` and are not on the refactor wire either — legacy parity.

**Phase 4 priority decision:** keep the refactor wire names (current state) or revert to legacy WSVC names verbatim (`COMPANYNAME` / `COMPANYSTATUS` / `OFFICEADD` / `SEARCHDATETIME`). Sales e-Invoices precedent (IR-7) is to revert; this page should mirror.

### Notes

**Verified-and-locked mappings (resolved against legacy code):**

- ✅ Tenant scope: `BIZ_KEY` is the only always-applied baseline filter (verified in [`aosv_0001_001_r001_act.jsp:42-45`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/aosv/aosv_0001_001_r001_act.jsp)). No "hide drafts / hand-registered / empty-detail" rules apply (unlike Sales e-Invoices).
- ✅ `COMPANYSTATUS` filter: partial match (`ILIKE '%X%'`), wire token is the **raw stored Vietnamese substring**. The legacy localisation helper `wb_get_operat_stts(value)` is commented out (verified at line 48-49) — refactor follows suit.
- ✅ `SRCH_WD` filter: OR over (`taxcode`, `companyname`) — fixed in legacy code, NOT View-Setting-driven (unlike Sales e-Invoices).
- ✅ No required date range (legacy parity — only `PAGE_NO` / `PAGE_PER_CNT` are validated).
- ✅ Sort: `SortHelper.getSortDynamic("aosv_0001_001_r001")` — View-Setting-driven; default surfaces `SEARCHDATETIME DESC` in practice.
- ✅ Audit-log table `EXT_TRAN_CALL_HIS` created in V16 but not yet read/written; `BIZ_KEY` deliberately nullable per legacy.

**Open follow-ups (non-blocking — see [planning doc](../../planning/addon/operation-status-inquiry-plan.md) Open Questions table for full list):**

1. **Wire field-name parity** — keep refactor names (`COMPANY_NM` / `OFFICE_ADD` / `LAST_UPDATE`) or revert to legacy WSVC names (`COMPANYNAME` / `OFFICEADD` / `SEARCHDATETIME`)? *Owner: Sonny — Phase 4 task #6.*
2. **Keyword query param name** — `q` (current) or `SRCH_WD` (legacy WSVC)? *Owner: Sonny — Phase 4 task #1.*
3. **Frontend search column scope** — wire is 2 columns (name + tax code); frontend filter is currently 3 columns (adds address). Collapse to wire parity OR document the address-column divergence? *Owner: PO + Arthur.*
4. **`REPRESENTATIVEFIRSTNAME` exposure** — legacy WSVC has it; refactor doesn't. Add to wire with `USE_YN='N'` in View Setting seed? *Owner: Arthur.*
5. **`STATUS_CD` 3-state collapse** — legacy TS legend has 12 strings; refactor coerces to 3. Preserve raw string as tooltip / sub-line? *Owner: PO.*
6. **Status display refinement** — today every state renders the same green-tick treatment. Phase 4 should branch icon + colour per canonical state (warning amber for `suspended`, neutral for `not_registered`). *Owner: Designer.*
7. **GDT live-pull (Inquire / Update buttons)** — legacy "Update" pulls from GDT via NXISAS. Refactor R&D scope is local-only; Phase 1 wires Update as `refreshKey++` and Inquire as inert + tooltip. Real GDT pull deferred. *Owner: Sonny.*
8. **History tracking** — no history of prior `COMPANYSTATUS` values today. PO has not asked for a "status changed from X to Y on date Z" view; deferred unless asked. *Owner: PO.*
9. **Bulk Inquire async design** — when the GDT bridge eventually lands, sequential per-row polls block the UI for minutes on 100-row pages. Right design is enqueue-job + UI poll. *Owner: PO + Arthur (when NXISAS port lands).*
10. **Schema parity audit** — V16 declares `COMPANYNAME VARCHAR(100)`; legacy WSVC declares 255. Run column-for-column DEV audit before Phase 4 closes; ship corrective `ALTER COLUMN` if needed. *Owner: Arthur (Phase 3 task #2).*
11. **Demo seed coverage** — V17 has 5 rows; status-filter dropdown demos may need richer coverage. Add up to 30 real DEV rows per the [Data Migration Rule](../../../CLAUDE.md). *Owner: Arthur (Phase 3 task #3).*
12. **JEX feature doc gap** — no `docs/jex/features/operation-status-inquiry.md` exists. WSVC + IDO + view JSP are the legacy spec. Authoring optional but improves traceability. *Owner: Sonny.*

### Performance considerations

- **Today's volume:** ≤ 1000 rows per tenant typical. Default `OFFSET / LIMIT` pagination is fine. PK `(BIZ_KEY, TAXCODE)` + index `ix_oprt_stts_biz_dtm (BIZ_KEY, SEARCHDATETIME)` cover the listing query.
- **Keyword search:** `ILIKE '%X%'` cannot use a btree index. Phase 4 should profile against real DEV-size data before adding a `pg_trgm` GIN index — almost certainly not needed at this volume.
- **Bulk Inquire (deferred):** sequential GDT polls for 100 MSTs can take minutes. When the bridge lands, implement as async job — enqueue work, return job-id, poll for completion. Per-MST audit-log row in `EXT_TRAN_CALL_HIS` (mirrors legacy NXISAS contract).
- **Excel export:** routes through the shared `ExcelExportService` with `Pageable.unpaged()`, capped at `MAX_ROWS = 50_000` per the global rule. For tenant `VN_VNM0004_000001` well under cap.
- **Cross-tenant scope:** `BIZ_KEY` enforced by `TenantContext.currentBizKey()` on every repository call; integration tests assert isolation.

### External API dependency

- **Legacy:** Inquire / Update buttons call the GDT (Vietnamese General Department of Taxation — Tổng cục Thuế) lookup endpoint via the NXISAS bridge through OSMC (`nxisas_oprtstts_*` services; OSMC handler `TRCO_TRCO001_00003` per the inline TODO at [`OperationStatusService.java:21`](../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java)).
- **Refactor R&D scope:** **NO external integration.** Local-only R&D (per the project refactoring-scope decision — tracked in project memory `project_refactoring_scope.md`, which is not part of this repository). Inquire / Update buttons stay locally-bound until / unless NXISAS is ported. The `refreshKey++` Update behaviour and the `WBE_NOT_IMPLEMENTED` Inquire stub are explicit Phase-1 placeholders.

### Refactor doc cross-references

- Feature spec: [../features/addon-service/operation-status-inquiry.md](../../features/addon-service/operation-status-inquiry.md)
- Frontend page: [../frontend/addon/operation-status-inquiry-page.md](../../../../wecms-web/docs/features/addon-service/operation-status-inquiry.md)
- Backend API: [../backend/addon/operation-status-inquiry-api.md](../api-spec/addon/operation-status-inquiry-api.md)
- Database: [../database/addon/operation-status-inquiry-db.md](../database/addon/operation-status-inquiry-db.md)
- Implementation plan: [../planning/addon/operation-status-inquiry-plan.md](../../planning/addon/operation-status-inquiry-plan.md)
- Sister features that consume `OPRT_STTS_INFO.COMPANYSTATUS` on their Buyer / Supplier cell: [Sales e-Invoices](#add-on-service--sales-e-invoices), [Expenses e-Invoices](#add-on-service--expenses-e-invoices)

## Reference

- JEX WSVC XMLs: `WECMS DEV/.../WECMS_ETC/xml/service/WSVC/`
- JEX IDO XMLs: `WECMS DEV/.../WECMS_ETC/xml/service/IDO/`
- JEX action JSPs: `WECMS DEV/.../WECMS/web/WEB-INF/action/jex/wabooks/<module>/`
- JEX feature specs: [../../jex/features/](../../../../docs/jex/features/)
- JEX business rules: [../../jex/business-rules/](../../../../docs/jex/business-rules/)

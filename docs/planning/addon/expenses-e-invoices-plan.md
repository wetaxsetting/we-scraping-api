# Add-on Service > Expenses e-Invoices — Refactor Implementation Plan

**Feature spec:** [../../features/addon-service/expenses-e-invoices.md](../../features/addon-service/expenses-e-invoices.md)
**Sister plan (Sales):** [sales-e-invoices-plan.md](sales-e-invoices-plan.md) — most rules are inherited; this doc only tracks the Expenses deltas
**Module:** Add-on Service
**Route:** `/addon/expenses-einvoices`
**JEX counterpart:** `einv_0001_001` (view) / `einv_0001_001_r001` (read service) with `einvType=2` — `DOC_TYPE='B'` slice of `BUSA_DOC_MSTR`
**Owner:** TBD (Refactor team — Sonny + Arthur)
**Sprint:** TBD — lands behind Sales (the shared backend MR fixes both pages at once)

> **Phase 2 implementation rules** are inherited verbatim from the Sales planning doc — see [Sales § Phase 2 implementation rules (locked decisions)](sales-e-invoices-plan.md#phase-2-implementation-rules-locked-decisions). IR-1 through IR-9 apply to Expenses with these substitutions: `type=EXPENSE`, `DOC_TYPE='B'`, `dymId='table2'`, `WEB_SVC_ID='addon-expenses-einvoices'`, `translationPrefix='expenses_einvoices'`, column header for `BUYER` rendered as "Supplier". The bug fix in IR-1 (`EXPENSE → 'B'`, not `'E'`) is the primary unblocker for this page.

---

## Phase 1 — UI completed (current state)

**Status:** ✅ Done (static UI; sibling of one of the four canonical baselines per the wecms-web CLAUDE.md "Approved reference set").

**What landed:**

- Page route at [`app/(dashboard)/addon/expenses-einvoices/page.tsx`](../../../../wecms-web/app/(dashboard)/addon/expenses-einvoices/page.tsx).
- Thin variant wrapper [`<ExpensesEInvoicesPage>`](../../../../wecms-web/components/addon/expenses-einvoices/ExpensesEInvoicesPage.tsx) → composes the shared [`<EInvoicesClient>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesClient.tsx) with `type="EXPENSE"` + `translationPrefix="expenses_einvoices"`. **No Expenses-specific layout code** — the shell, table, footer, filter row, toolbar, colgroup, and pagination chrome are all inherited from the Sales components. This is by design per [§7a](../../../../wecms-web/CLAUDE.md) and is non-negotiable: forking the shell is forbidden.
- Static seed data in `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced) (Expenses-specific row fixtures — Vietnamese supplier names, `2C24SUPP-*` invoice serials).
- i18n keys under `expenses_einvoices.*` prefix in `locales/{en,ko,vi}.json`. KM missing.
- HTTP fetch path wired to `GET /api/v1/addon/einvoices?type=EXPENSE` via [`fetchEInvoices`](../../../../wecms-web/lib/api/addon.ts) + [`mapEInvoiceRow`](../../../../wecms-web/lib/api/mappers.ts) — same path as Sales, only the `type` discriminator differs. **Today's response is empty for any DEV database** because `EInvoiceService.resolveDocType` maps `EXPENSE → 'E'` (wrong) and the listing seed carries `DOC_TYPE='B'` (correct legacy parity); see Risks.

**What's still missing on the UI side** — identical to Sales, since the page is a thin variant. Carry into Phase 2:

- Invoice-status filter popover (trigger renders, options inert).
- Date-range picker is display-only.
- Pagination is presentational.
- Sorting is presentational.
- View Setting popover — page is **not** registered in [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts) yet. Per IR-9 the popover stays unrendered on this page even after registration.
- Print / Excel / Update buttons accept handlers but none are wired.

---

## Phase 2 — Frontend integration (reuse Sales components)

**Status:** ⏳ Not started. Lands **behind Sales** — every shared component (popover, hook, mapper, helper) lights up for Expenses on the same MR that wires it for Sales.

**Reuse strategy — minimise duplication:**

Per [§7a](../../../../wecms-web/CLAUDE.md), Sales and Expenses share one page shell ([`<EInvoicesPage>`](../../../../wecms-web/components/addon/sales-einvoices/EInvoicesPage.tsx)). The Expenses page contributes only:

1. **One thin entry component** ([`<ExpensesEInvoicesPage>`](../../../../wecms-web/components/addon/expenses-einvoices/ExpensesEInvoicesPage.tsx)) — already exists; nothing more to write.
2. **One `translationPrefix`** (`expenses_einvoices`) — already configured.
3. **One `type` discriminator** (`EXPENSE`) — already configured.
4. **Expenses-side i18n keys** (`expenses_einvoices.*`) — already exist for EN/KO/VI; KM pending.
5. **One column-header relabel** — `BUYER` `FIELD_ID` rendered as "Supplier" via the `expenses_einvoices.col.supplier` translation key.
6. **One screen-id constant** (`SCREEN_IDS.ADDON_EXPENSES_EINVOICES = "addon-expenses-einvoices"`) added to [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts).
7. **One Flyway seed row set** in `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-expenses-einvoices'` — same column shape as Sales.

Everything else — the shared `<EInvoiceTable>`, `<EInvoiceTableFooter>`, `<EInvoiceTableColumns>`, `<InvoiceStatusFilter>`, `<SalesEInvoiceActionBar>` (the action-bar component is shared by both pages despite the "Sales" name; it is parameterised via `translationPrefix` and renders for Expenses too — rename in a follow-up MR if it helps clarity), `useViewSetting`, `useTableSort`, `usePagination`, `useExcelExport`, `printReport`, `mapEInvoiceRow` — is consumed verbatim. **No Expenses-folder copy of any of these is allowed.** Per [§ Common Reusability Rule](../../../../wecms-web/CLAUDE.md), the second consumer is the trigger to extract — Sales and Expenses are exactly that pair, and the extraction already happened when the Sales page landed.

**Phase 2 deliverables (Expenses-side, layered onto the Sales MR):**

| # | Task | Where |
|---|---|---|
| 1 | Add `SCREEN_IDS.ADDON_EXPENSES_EINVOICES = "addon-expenses-einvoices"` to [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts). | Frontend |
| 2 | Add `DEFAULT_AREA_ID` mapping for the new screen id (`"default"`). | Same file |
| 3 | Wire `useViewSetting(SCREEN_IDS.ADDON_EXPENSES_EINVOICES, DEFAULT_AREA_ID)` inside the shared `<EInvoicesClient>` — already keyed by `translationPrefix`; switch the screen-id resolution to a small lookup so Sales gets `ADDON_SALES_EINVOICES` and Expenses gets `ADDON_EXPENSES_EINVOICES`. | `<EInvoicesClient>` |
| 4 | Confirm the Expenses i18n keys cover every status string, every column header, and every action-button label. Add missing keys to `locales/{en,ko,vi}.json` — never inline strings. | `locales/*.json` |
| 5 | Pass the Expenses `screenId` to `useExcelExport()` so the Excel export request body carries `screenId="addon-expenses-einvoices"` + `type="EXPENSE"`. | `<EInvoicesClient>` |
| 6 | Verify the `BUYER` column's `headerKey` resolves to `expenses_einvoices.col.supplier` when the page is the Expenses variant. | Column registry / `<EInvoiceTable>` |

**No new components, no new hooks, no new helpers.** If a reviewer sees an `expenses-einvoices/` folder gaining anything beyond the entry wrapper + mock data, that is a regression.

---

## Phase 3 — Backend validation (`DOC_TYPE='B'`, filters)

**Status:** 🟡 Skeleton exists (shared with Sales) but contains the **`EXPENSE → 'E'` bug** that blocks the Expenses page.

**What exists:**

- [`EInvoiceController#list`](../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) accepts `type=EXPENSE`. ✅
- [`EInvoiceService#list`](../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java) filters `BIZ_KEY` + `DOC_TYPE`. ❌ — the `DOC_TYPE` resolution maps `EXPENSE → 'E'`. Must be `'B'` per IR-1.
- DTOs `EInvoiceListResponse` + `EInvoiceRow` + `CurrencyTotal` — shared with Sales; same 12-column subset; same legacy-parity gaps.
- Schema parity: V15 / V23 brought `BUSA_DOC_MSTR` to 100 columns. Same DEV parity for both `DOC_TYPE='I'` and `'B'`.

**Phase 3 deliverables — all shared with Sales except validation:**

| # | Task | Notes |
|---|---|---|
| 1 | **Fix `EInvoiceService.resolveDocType`** — `EXPENSE → 'B'`. Drop the `'E'` alias. Update `BusaDocMstr.docType` Javadoc and `EInvoiceController` Javadoc. **Add a unit test** asserting `resolveDocType("EXPENSE")` returns `'B'` and `resolveDocType("E")` is rejected with `WBE2002`. | IR-1 — primary blocker for Expenses |
| 2 | Add the four legacy-parity baseline filters per IR-2 (`PROV_STTS NOT IN ('VD','EE')`, `HAND_REG_YN <> 'Y'`, `BUSA_DOC_DETL` exists, `OP` expansion). One MR, both pages benefit. | IR-2 |
| 3 | Make `from` / `to` required per IR-3. | IR-3 |
| 4 | Wire `invoiceStatus` + `keyword` query params per IR-4 + IR-5. | IR-4 + IR-5 |
| 5 | Add server-side pagination + sort + the multi-currency aggregation query per IR-6. | IR-6 |
| 6 | Excel export endpoint `POST /api/v1/addon/einvoices/export` — request body's `type` discriminator selects Sales (`I`) or Expenses (`B`). One endpoint, one provider, two screen ids. | Shared with Sales |
| 7 | **Verify the keyword-search whitelist for `dymId='table2'`** in [`SearchHelper.java`](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/SearchHelper.java). If it differs from `'table1'`, document the delta — Phase 2 still ships the same fixed 5-column subset (per IR-5), but the legacy reference table needs to be accurate. | Expenses-specific verification |
| 8 | **DB validation for Expenses data** — confirm `BUSA_DOC_MSTR` for `BIZ_KEY=VN_VNM0004_000001` carries enough `DOC_TYPE='B'` rows for stakeholder demos. If sparse, copy real DEV rows into a `V{N}__addon_expenses_einvoices_demo_seed.sql` migration (max 30 rows, real data only — never fabricated). | Expenses-specific seed audit |
| 9 | Seed `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-expenses-einvoices'`. Same column set as Sales; bundle in the same migration when possible. | Flyway |

---

## Phase 4 — Integration testing

**Status:** ⏳ Not started.

**Test surface — Expenses-specific scenarios** (the shared shell tests cover everything else, run once for both pages):

| # | Test | Asserts |
|---|---|---|
| 1 | `GET /api/v1/addon/einvoices?type=EXPENSE&from=…&to=…` returns only `DOC_TYPE='B'` rows | IR-1 fix applied |
| 2 | The same call rejects `type=E` (legacy alias) with a clean `WBE2002` | Alias drop |
| 3 | The Expenses page renders the column header as **"Supplier"** in EN, the equivalent string in KO/VI | i18n + variant flip |
| 4 | The Expenses Excel export filename matches `Expenses_e-Invoices_<scope>.xlsx` (or the localised equivalent), and the workbook's row 0 title matches `t("expenses_einvoices.title")` | Wire vs. file vs. row 0 distinction (IR-9 / Sales § 13b) |
| 5 | The Expenses View Setting state is independent from Sales — saving an override on `addon-expenses-einvoices` does not affect `addon-sales-einvoices` | Screen-id isolation |
| 6 | A row whose supplier carries `COMPANYSTATUS='NNT ngừng hoạt động …'` renders the suspended-MST string verbatim in the supplier cell | Compliance signal preserved |
| 7 | A demo seed (if added per Phase 3 task #8) populates the Expenses table for stakeholder review | Demo-seed coverage |
| 8 | Hand-registered Expenses rows (`HAND_REG_YN='Y'`) are excluded from the Expenses listing | IR-2 baseline filter |
| 9 | An Expenses invoice with no `BUSA_DOC_DETL` line items is excluded from the Expenses listing | IR-2 baseline filter |
| 10 | Cancelled / draft Expenses rows (`PROV_STTS IN ('VD','EE')`) are excluded | IR-2 baseline filter |

Everything else (multi-currency totals, pagination, sort cycle, Excel column intersection, status localisation, View Setting CHECKBOX/ETC locking, Print column set, error envelope, JWT) is shared infrastructure tested on Sales and inherited.

---

## Reuse strategy — explicit recap

| Shared concern | Owner | Expenses contribution |
|---|---|---|
| Page shell (`<EInvoicesPage>`) | `components/addon/sales-einvoices/` (despite the folder name) | None — consumed via `<EInvoicesClient>` |
| Table (`<EInvoiceTable>`, `<EInvoiceTableColumns>`, `<EInvoiceTableFooter>`) | `components/addon/sales-einvoices/` | None |
| Filter components (`<InvoiceStatusFilter>`, `<DateRangePicker>`, `<SearchInput>`) | shared / Sales folder | None |
| Toolbar (`<SalesEInvoiceActionBar>` — name lies, used by both) | `components/addon/sales-einvoices/` | None — rename in a follow-up |
| `useViewSetting`, `useTableSort`, `usePagination`, `useExcelExport`, `printReport`, `mapEInvoiceRow` | shared (`components/shared/`, `hooks/`, `lib/`) | None |
| `EInvoiceController`, `EInvoiceService`, `BusaDocMstr`, `BusaDocMstrRepository`, `EInvoiceExportProvider` | `wecms-api` | None — branches on `DOC_TYPE` |
| `BUSA_DOC_MSTR`, `EINV_TAX_DETL`, `EINV_FEE_DETL` schema | shared Flyway state | None — same table |
| `SYS_SCR_OPTION_WECMS` seed | shared Flyway pattern | One row set keyed by `WEB_SVC_ID='addon-expenses-einvoices'` |

The only Expenses-folder code is the entry wrapper (`ExpensesEInvoicesPage.tsx`) and the static seed (`mockData.ts`). That is the limit. Anything more is a regression.

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **`DOC_TYPE` mismatch (`'E'` vs `'B'`) — Expenses returns zero rows.** `EInvoiceService.resolveDocType` maps `EXPENSE → 'E'`; legacy and the local DEV seed use `'B'`. **This is the single most important Expenses risk** — without the fix, every Expenses fetch returns empty against any realistic dataset, regardless of how the rest of the page is wired. Reviewers may chalk the empty page up to "no seed yet" and miss the bug. | High (today) | High — blocks the entire feature | Phase 3 task #1 — fix `EXPENSE → 'B'` in the same MR as the IR-2 baseline filters. Add a unit test on `resolveDocType`. Verify by running the page against the real DEV DB after the fix. |
| **No seed data for Expenses.** Even after the `DOC_TYPE='B'` fix lands, the local DEV seed for `BIZ_KEY=VN_VNM0004_000001` may carry few or zero `DOC_TYPE='B'` rows. Stakeholder demos and Figma comparison shots will show an empty screen — visually indistinguishable from the IR-1 bug — and the team may suspect the fix didn't take. | High | Medium — perception risk + makes IR-1 verification ambiguous | Phase 3 task #8 — audit the seed coverage in DEV. If sparse, copy real DEV rows into a demo-seed migration (max 30 rows, real data per the Data Migration Rule). Run the audit BEFORE the IR-1 fix lands so post-fix verification has a known-non-empty target. |
| **Filter mismatch vs Sales.** The legacy keyword-search whitelist is keyed by `dymId` — `'table1'` for Sales, `'table2'` for Expenses. If the two whitelists diverge in any column, Phase 2's fixed 5-column subset (per IR-5) may search the wrong columns for Expenses without anyone noticing. | Medium | Medium — silent search behaviour drift | Phase 3 task #7 — verify the `'table2'` whitelist in `SearchHelper.java`. Document any delta in the IR-5 note in the Sales planning doc and in the Expenses feature spec. The 5-column subset can stay (`DOC_ID`, `TRCO_CMNM`, `TRCO_TAX_ID`, `EINV_INVOICESYMBOL`, `DOC_NO`) but the rationale needs to match the Expenses legacy reference. |
| **Status localisation drift between Sales and Expenses.** IR-4 returns the raw stored `EINV_INVOICESTATUS`; the frontend regex resolves vi/en/ko to a canonical key. If real DEV Expenses rows have stored values in a different language than Sales rows (e.g. KO for Sales but VI for Expenses), the dropdown filter (which sends the stored Vietnamese string per IR-4) would behave differently across the two pages. | Medium | Medium — broken filter on one page only | Run a smoke test as part of Phase 4 task #5: select the same logical status (e.g. `Original`) on both pages, confirm rows return on both. If language stored differs, tighten IR-4 (port `wb_get_invoice_status`, replicate in Java emitting canonical keys, or normalise on save). |
| **Page-shell drift.** Sales and Expenses share one component tree. A column-set change for Sales (e.g. adding `DUE_DT`) that doesn't apply to Expenses tempts a fork. Reverse direction is equally tempting (Expenses-only "Suspended-MST" badge). | Medium | High — two pages becoming three components | Per [§7a](../../../../wecms-web/CLAUDE.md), forking the shell requires explicit MR sign-off. Keep all variation parameterised by `translationPrefix` / `screenId` / `type`. Reviewers must reject any PR that adds Expenses-specific JSX to the Sales folder, or vice versa. |
| **`BUYER` vs `SUPPLIER` `FIELD_ID` mismatch.** If Sales seeds `BUYER` and Expenses seeds `SUPPLIER`, two screens carry two different `FIELD_ID`s for the same underlying entity field — confusing for cross-screen analytics, awkward for the column registry. If both seed `BUYER`, the seed PK is fine but the Expenses header text comes only from i18n (`expenses_einvoices.col.supplier`), which works but reads oddly in the seed. | Low (technical) / Medium (clarity) | Low | Decide once via the PO question in TODO (feature spec). Whichever is chosen, document in the seed migration comment so the next reader doesn't assume the other. |
| **Action-bar component name lies.** [`<SalesEInvoiceActionBar>`](../../../../wecms-web/components/addon/sales-einvoices/SalesEInvoiceActionBar.tsx) is used by both pages despite the "Sales" prefix. A future contributor may copy it for an Expenses-specific variant, breaking the shared-shell contract. | Low | Medium | Rename to `<EInvoiceActionBar>` in a follow-up MR (out of Phase 2 scope to keep the Sales/Expenses MR small). Add a one-line comment at the top of the file noting it is shared. |
| **Excel export `type` discriminator missed on the wire.** The shared endpoint `POST /api/v1/addon/einvoices/export` selects Sales vs. Expenses via the request body's `type`. If the frontend hook doesn't forward `type`, both pages export the same (Sales) data. | Low | High | Phase 2 task #5 — explicitly thread `type` from `<EInvoicesClient>` props through `useExcelExport`'s extra-filter parameter. Phase 4 task #4 verifies the workbook contents differ between Sales and Expenses exports. |
| **Backend Expenses-side data parity gap.** Even after the `DOC_TYPE` fix, real DEV rows for `'B'` may carry NULLs in fields the UI assumes are populated (e.g. `EINV_INVOICESYMBOL`, `TRCO_CMNM`). The Sales-side seed has been ironed out; the Expenses seed has not been exercised. | Medium | Low–Medium — empty cells in the table | Run the audit in Phase 3 task #8. If specific fields are systematically NULL on Expenses rows, document in the feature spec and decide whether to render them as `—` placeholder or hide the column for Expenses. |
| **Compliance signal lost.** The Expenses page's primary value is the supplier-MST status flag (`COMPANYSTATUS='NNT ngừng hoạt động …'` → potential input-VAT denial). If `COMPANYSTATUS` is dropped from the `EInvoiceRow` DTO during Phase 2 cleanup (it's already on the priority list, but priorities can shift), the Expenses page loses its core compliance feature. | Low | High | Sales planning doc Phase 2 task #6 explicitly lists `COMPANYSTATUS` in the priority response-shape extension. Phase 4 task #6 in this doc verifies the suspended-MST string survives the wire round-trip. |

---

## Open questions

These items are NOT blockers for Phase 2 implementation. They are tracked here so reviewers can flag them when the relevant artefact lands:

| Item | Why it's open | Owner | Trigger to resolve |
|---|---|---|---|
| **Does Expenses require different filters from Sales?** | The static UI ships the same filter row (status / date / keyword); the legacy `dymId='table2'` keyword whitelist may differ from `'table1'`. PO may want an Expenses-specific filter (e.g. supplier-MST-status filter for compliance). | PO + Arthur (after `SearchHelper.java` audit) | Phase 3 task #7 completes; PO reviews the result. |
| **Any difference in status meaning between Sales and Expenses?** | The six logical states (Original / Replacement / Adjustment / Replaced / Adjusted / Cancelled) come from the supplier on the Expenses side and from the company itself on the Sales side. Same vocabulary, but the operational meaning differs (e.g. an Expenses "Cancelled" means the supplier voided their invoice — affects input-VAT credit; a Sales "Cancelled" means the company voided its own invoice — affects output-VAT). The displayed string is the same; the user's interpretation is not. | PO + Designer | Tooltip / help-text decision before the page goes live to users. |
| **Excel detail export required for Expenses?** | Sales has a chevron-dropdown on the Excel button hinting at "visible columns" vs "all columns" (legacy `_r002_excel`). Expenses inherits the same toolbar; if PO wants an Expenses-only "by supplier" detail export, that would diverge. | PO | Designer publishes the chevron-dropdown menu spec for either page. |
| **Supplier vs Buyer mapping consistency.** | The View Setting `FIELD_ID` is `BUYER` on the Sales seed; for Expenses the choice is between reusing `BUYER` (PK is `(WEB_SVC_ID, FIELD_ID)`, no collision) or seeding `SUPPLIER` (clearer for cross-screen analytics, but requires the column registry constant to flip). The entity field is `TRCO_CMNM` either way; only the seed string + registry constant differ. | PO + Arthur | Before the Phase 3 task #9 seed migration MR. |
| **Per-supplier-status totals in the footer.** | A compliance-driven aggregation ("Total of invoices from suspended-MST suppliers: VND X") is meaningful on Expenses but not on Sales. Legacy returns per-status (`TOT_OP_INV_*` etc.) totals; not per-supplier-status. PO may request a sibling aggregation. | PO + Compliance | PO reviews live data and confirms whether the breakdown is needed. |
| **Demo-seed coverage for Expenses.** | Local DEV seed for `BIZ_KEY=VN_VNM0004_000001` may carry few `DOC_TYPE='B'` rows. Stakeholder demos may show an empty screen even after IR-1 lands. | Arthur | Phase 3 task #8 audit. |
| **Action-bar rename (`<SalesEInvoiceActionBar>` → `<EInvoiceActionBar>`).** | The component is shared; the name lies. Cosmetic but worth fixing before more pages copy the Sales naming pattern. | Arthur | Follow-up MR after Phase 2 lands. |
| **`type=BILL` alias on the wire.** | The current controller accepts `EXPENSE` (and the wrong `E` alias). The `BusaDocType` constant is `BILL`; some clients may prefer the wire enum to read `BILL`. Cosmetic — pick one spelling and lock it. | PO + Sonny | Same MR as the IR-1 fix. |

**Resolved by IR-1 through IR-9 (Phase 2 rules locked, inherited from Sales):**

- ✅ `DOC_TYPE='B'` for Expenses (NOT `'E'`) — IR-1.
- ✅ Always-applied baseline filters — IR-2.
- ✅ Date range required — IR-3.
- ✅ `EINV_INVOICESTATUS` localisation strategy — IR-4.
- ✅ Phase 2 keyword-search subset (5 columns) — IR-5.
- ✅ Multi-currency totals from full filtered dataset — IR-6.
- ✅ `invoiceType` rename (drop `INV_TYPE` from the DTO) — IR-7.
- ✅ Implementation approval — IR-8.
- ✅ View Setting scope (data + column metadata in; popover / toolbar icon out) — IR-9.

**Resolved against legacy code (verified findings, inherited from Sales):**

- ✅ `INV_TYPE` is `Commercial`/`Tax`, not Original/Replacement/Adjustment.
- ✅ `EINV_INVOICESTATUS` has 6 logical values, localised vi/en/ko.
- ✅ `COLOR` is a memo-sticker CSS class, not a status badge tint.
- ✅ `PROV_STTS` is a fixed 6-value enum; listing always excludes `VD` + `EE`.
- ✅ `ISSU_TM` is not a legacy field; refactor proxy is `REG_DTM[8..14]`.

---

## Cross-references

- Feature spec: [../../features/addon-service/expenses-e-invoices.md](../../features/addon-service/expenses-e-invoices.md)
- Sister plan (Sales — primary source of Phase 2 rules): [sales-e-invoices-plan.md](sales-e-invoices-plan.md)
- Sister feature (Sales): [../../features/addon-service/sales-e-invoices.md](../../features/addon-service/sales-e-invoices.md)
- Frontend page (today): [`wecms-web/components/addon/expenses-einvoices/`](../../../../wecms-web/components/addon/expenses-einvoices/) (entry wrapper + mock seed only)
- Shared shell (today): [`wecms-web/components/addon/sales-einvoices/`](../../../../wecms-web/components/addon/sales-einvoices/)
- Backend (today): [`wecms-api/src/main/java/com/kosign/wecms/domain/einvoice/`](../../../src/main/java/com/kosign/wecms/domain/einvoice/) (shared with Sales)
- Refactor frontend rules: [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) §7a · §13 · §13a · §13b · §6d
- Refactor backend rules: [wecms-api/CLAUDE.md](../../../CLAUDE.md) — Excel export · View Setting · Common table sort · Refactoring enforcement rule
- Legacy WSVC: `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml`
- Legacy view: `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp` (with `einvType=2`)

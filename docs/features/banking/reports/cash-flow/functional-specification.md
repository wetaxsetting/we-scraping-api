# Cash Flow — Functional Specification

Screen-level behaviour of `cash_0001_001`, derived from the legacy
[cash_0001_001_view.jsp](../../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/cash/cash_0001_001_view.jsp) /
[cash_0001_001.ts](../../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/cash/cash_0001_001.ts),
plus the refactor's REST/validation contract. UI rendering detail is in the frontend
[ui-specification.md](../../../../../../wecms-web/docs/features/banking/reports/cash-flow/ui-specification.md).

---

## 1. Entry point & access

| Item | Legacy | Refactor |
|---|---|---|
| URL / route | `cash_0001_001.act` | `/banking/reports/cash-flow` |
| Menu label | "Cash Flow" (ML `inc_wabook_fnsh_lnb_m013`), FNSH (Financial) module | Banking → Reports → Cash Flow |
| Menu permission | `loginUser.isAdmin() OR OSMCMenuHelper.isPermittedMenu(child(3,2))` | JWT-authenticated (see §8) |
| View service flags | `WSVC.cash_0001_001.xml`: `login=Y`, `NORM_USER_ONLY_YN=Y` | n/a |
| Data API auth | `WSVC.cash_0001_001_r001.xml`: **`login=N`** (action still resolves the session user for `BIZ_KEY`) | **JWT required** — deliberate tightening (D1) |

`NORM_USER_ONLY_YN=Y` ⇒ intended for **normal company users** (not WTA admins).

---

## 2. Screen layout

```
┌ Title row ──────────────────────────────── [list]? [Download Excel] ┐   ← report has Excel only, no list popover
│ Cash Flow                                                            │
├ Filter row ──────────────────────────────────────────────────────────┤
│ [Exchange Rate ____] [Currency ▾] [Year ▾] [Quarter ▾] [Banks ▾] [Search]
├ Matrix ───────────────────────────────────────────────────────────────┤
│ thead: [ Opening Balance (3 cols) | Item code | Item name | Jan..Dec ] │
│        (Opening Balance amounts shown per month in the header)         │
│ tbody: L1 / L2 / L3 rows (5 sticky-left columns) + subtotal/profit bands│
│ footer (sticky bottom): END BALANCE — 12 month values, solid green     │
└────────────────────────────────────────────────────────────────────────┘
```

Sticky: header is sticky-top; the five left columns (L1 / L2 / L3-name / Item code / Item name) are
sticky-left; the End Balance footer is sticky-bottom. The page is a **report** (`<ReportLayout>`,
no pagination — wecms-web §7b).

> **Refactor filter-bar note:** the live page exposes a **single combined "Exchange Rate" input** (not
> separate USD and KRW boxes). It edits `krwRate` when base = USD, otherwise `usdRate`. The unused
> rate keeps its prefetched/default value. There is **no Print** action for this report (legacy had
> none either).

---

## 3. Search conditions (inputs)

| Control | Legacy DOM → field | Refactor query param | Notes |
|---|---|---|---|
| Exchange Rate | `#exchange-rate` → `USD_RATE_AMT` | `usdRate` / `krwRate` | VND per 1 unit; prefilled from `GET /exchange-rates` (Shinhan USD/KRW sell), user-editable |
| Currency | `#currency` `data-ccy` → `BASE_CCY_CD` | `baseCurrency` | VND / USD / KRW |
| Year | `.date-year` → `DATA_YEAR` | `year` | dropdown current-year … current-8 (9 entries) |
| Quarter | `[data-quarter]` (**not sent** to server) | (**not sent** to calc endpoint) | All / Q1–Q4 → client-side month-column show/hide only |
| Banks | `.list-bank` checkboxes → `BANK_FILT` (JSON) | `bankFilters` (JSON `[{bankCd,accountNo}]`) | multi-select; "All Banks" = empty = all |
| — | `KRW_RATE_AMT` (always required by legacy) | `krwRate` (conditional — D2) | refactor requires it only when base ≠ KRW |
| — | `YEAR_QUAT` (always `null` from the live UI) | — | server-side quarter filter is dormant / removed |

---

## 4. Buttons & events

| Event | Legacy | Refactor |
|---|---|---|
| Search | `renderTable()` → reload `_r001` | `handleSearch` bumps `searchKey` → `useApiData` refetch (`fetchCashFlow`) |
| Download Excel | `exportCashflowToExcel(_data)` (client ExcelJS) | `handleExport` → `exportExcel(...)` → `POST .../export` → `CashFlowExcelWriter` (server POI) |
| Currency change | set base + flag, then `renderTable()` | `setFilters` (no auto-fetch until Search) |
| Year change | set `data-year` (no auto reload) | `setFilters` |
| Quarter change | show/hide `.month-N` columns (no reload) | `setFilters` → `monthIndicesForQuarter` hides columns client-side (no refetch) |
| Bank change | rebuild `selectedBanks` (no auto reload) | `BankFilterPopover` updates `filters.bankFilters` |

---

## 5. Row vocabulary & grid rules

Every row carries one of **seven** verbatim `row_type` values (preserved on the wire so the frontend
classifier and the Excel grouper keep working):

| Row | `row_type` / `display_cd` | Visual |
|---|---|---|
| Opening Balance | `Opening Balance` | written into the thead second row (per month); not a tbody row |
| Level 1 header | `Level 1` | merged sticky L1 cell (rowspan over its tree); name `ko en` |
| Level 2 group | `Level 2` | merged sticky L2 cell (rowspan over L3 children); name `ko en` |
| Level 3 leaf | `Level 3` | L3 name (`ko`) + Item code + Item name (`en`) + 12 month amounts |
| L1 Subtotal | `Level 1 Subtotal` | ACC-01/02 → blue band `S. TOTAL`; **ACC-03 → `NON SALES PROFIT`** |
| Sales Profit | `Sales Profit` / `SALES PROFIT` | green band, before the first ACC-03 group |
| End Balance | `End Balance` | solid-green sticky footer (`#16a34a`, white) |

- **`Grand Total`** is never emitted (G8 dead code) — handled defensively as a synonym for End Balance.
- **NON SALES PROFIT** renders **twice** (blue subtotal + green band) — intentional legacy duplicate,
  PO 2026-06-04 (parity audit §8b).
- **Amount formatting:** zero renders blank (no "0"/"–"); otherwise `formatAmount(value, ccy, true)`.
- **No on-screen annual-total column** — `total_amount` is exported to Excel only.

---

## 6. Excel export

Server-side via Apache POI (`CashFlowExcelWriter`), `POST .../export`. Layout preserves the legacy
ExcelJS structure: title "Cash Flow"; subtitle `Year: <y> - Currency: <ccy> - Exchange Rate: <rate>`;
two-row header (Opening Balance merged across cols 0–2, Item code / Item name, months Jan–Dec; Opening
Balance amounts in row 4); merged L1 (col 0) / L2 (col 1) cells; blue subtotal bands, green Sales-Profit
& NON SALES PROFIT bands, solid-green End Balance. 17 columns, **no Total column**. Full detail +
parity in [interface-specification.md §3](interface-specification.md) and the frontend
[ui-specification.md](../../../../../../wecms-web/docs/features/banking/reports/cash-flow/ui-specification.md).

> **Number format (G7):** refactor applies a generic `#,##0.##` to every amount cell; legacy used
> per-currency `[$-vi-VN]#,##0` (VND/KRW, no decimals) and `[$-de-DE]#,##0.00` (USD). Values unaffected;
> display differs. PO call — see [refactor-gap-analysis.md G7](refactor-gap-analysis.md).

---

## 7. Print / upload / approval

- **Print:** none (no print action on either side).
- **Upload:** none.
- **Approval workflow:** none inside this screen — Cash Flow is read-only. The data is *filtered* by
  approval status (confirmed payments `wb_get_appr_stts='C'` / confirmed bank txns `cnfm_yn='Y'`); no
  create/update/delete.

---

## 8. Permissions

| Aspect | Behaviour |
|---|---|
| Legacy menu gate | admin OR `OSMCMenuHelper.isPermittedMenu(FNSH child (3,2))` |
| Legacy data API | `WSVC.cash_0001_001_r001.xml login=N` (anonymous-allowed; action resolves session user for `BIZ_KEY`) |
| Refactor | every endpoint JWT-authenticated (`SecurityConfig.anyRequest().authenticated()`) — deliberate tightening **D1** (Sonny override 2026-05-13). No `@PreAuthorize` role gate. |
| Tenant isolation | `bizKey = TenantContext.currentBizKey()` from the signed JWT — never from a request param. |

---

## 9. Localisation

Legacy: Web-Service-View ML codes `cash_0001_001_mlNNN` (EN/KO/KM/VI in Jex Studio). Notable:
`_ml001` Cash Flow, `_ml002` Download Excel, `_ml003` Exchange Rate, `_ml005` All Banks, `_ml006` Search,
`_ml007..ml018` Jan…Dec, `_ml019/_ml020` Item code / Item name, `_ml021` END BALANCE, `_ml023` Opening
Balance, `_mly000..mly004` All Year / Quarter 1–4.

Refactor: frontend `report.cash_flow.*` keys in `locales/{en,ko,vi}.json` (40 keys each). The single
backend message is `WBE_CASH_FLOW_RATE_REQUIRED` in `messages.properties` (EN only;
`{0}` = `"USD"`/`"KRW"`).

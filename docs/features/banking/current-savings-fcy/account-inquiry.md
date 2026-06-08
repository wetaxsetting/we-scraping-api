# Banking > Account Inquiry (FCY) — Refactor Feature Spec

> **Preserved single-file spec.** The **primary documentation home** for this feature is now the
> 9-doc folder [account-inquiry-fcy/](../account-inquiry-fcy/README.md). This file is kept as a quick
> reference and remains valid; new detail lands in the folder.

**Module:** Banking
**Menu:** Account Inquiry (FCY)
**Route:** `/banking/account-inquiry-fcy`
**Refactor screen ID:** `banking-account-inquiry-fcy`
**JEX counterpart:** [docs/jex/features/banking-account-inquiry-fcy.md](../../../../../docs/jex/features) *(create if missing)*
**Status:** Working — backend live (shared with VND via `currency=FCY`), frontend wired (`AccountInquiryFcyClient`), Excel export wired (`AccountInquiryFcyExportProvider`). View Setting + table sort use the FCY screen ID. Server-side filter / pagination plumbing (Sprint 2) reuses the VND wrapper.

JEX is **read-only reference**. This spec captures the refactor design.

## Refactor enhancement (no JEX equivalent)

The legacy live system has a **USD-only** non-VND account-list page at `fnsh_0005_001` — it hardcodes `CcyCd.USD` in the action JSP and `CCY_CD: "USD"` in the TypeScript driver, has no in-page currency dropdown, and has no per-currency summary breakdown. There is no legacy KRW or "all FCY" account-list page.

The refactor's `/banking/account-inquiry-fcy` is therefore a **deliberate enhancement**:

- "FCY" semantically means *all non-VND* (`ccy_cd <> 'VND'`) — covering USD, KRW, EUR, JPY, and any other CCY the tenant holds.
- A second filter dropdown lets the user narrow to a single concrete CCY (`USD`, `KRW`, …); when narrowed, the wire param flips to that CCY and the backend filter becomes `ccy_cd = <CCY>`.
- The summary card area renders **one tile per currency group** (sourced from `TOTALS_BY_CCY`); mixed-currency arithmetic is intentionally not computed.

These three behaviours have no JEX parity and should not be characterised as "ports". Where the docs reference JEX (filter shape, field IDs, search semantics, sort order, status codes), JEX remains the source of truth.

---

## Overview

Lists every **non-VND** bank account for the current tenant in a single page. Default scope is "All Currency" (wire `currency=FCY` → backend filter `ccy_cd <> 'VND'`); a second filter dropdown narrows to a concrete CCY (`USD`, `KRW`, `EUR`, …) and flips the wire to `currency=<CCY>` (backend filter `ccy_cd = <CCY>`).

The summary card area renders **one tile per currency group** (multi-currency mode) — mixed-currency totals are never rendered because they have no business meaning. Each tile uses its own group's CCY for amount formatting (`$12,931.38`, `₩0`, `€1.234,56`, …) via the shared `formatAmount(value, ccy, true)` helper.

---

## Scope

**In scope:**
- Non-VND account list — default "All Currency" (`ccy_cd <> 'VND'`); narrowable to a single CCY via the in-page currency dropdown.
- Per-account row: bank, account no, nickname, balance, available balance, last update, status flag — same FIELD_ID set as the VND screen.
- Summary card area: **one tile per CCY group** (USD, KRW, EUR, …) sourced from `TOTALS_BY_CCY`. Each tile shows count + total balance + total available balance in the group's CCY.
- Filters: bank dropdown · **currency dropdown (FCY-only)** · keyword search · refresh.
- Sort: column header click — same whitelisted FIELD_IDs as VND (`AccountSortField` enum).
- Pagination: server-side (Sprint 2 plumbing inherited from the shared `AccountInquiryClient`).
- View Setting popover: toggle / reorder columns per user (`WEB_SVC_ID = banking-account-inquiry-fcy`).
- Excel export: server-rendered XLSX with the same filter + sort applied; subheader shows one line per CCY group when wire `currency=FCY`, single line when wire is a concrete CCY.

**Out of scope (handled by other features):**
- VND account inquiry — separate page `banking-account-inquiry-vnd`.
- Per-account transaction history — separate route + endpoint (`/banking/transaction-history-fcy/[slug]`, `GET /api/v1/banking/accounts/{acctSeqNo}/transactions`).
- Adding / editing accounts — settings-side feature (Bank Account Setup).
- FCY rate / FX conversion — not part of this screen.

---

## Legacy reference (JEX)

| Asset | Path |
|---|---|
| **JEX FCY page (USD-only)** | `fnsh_0005_001` — separate WSVC, separate view JSP, separate TS driver |
| Action JSP (USD list) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0005_001_r001_act.jsp` — calls `FnshHelper.fnsh_0001_001_r001(..., CcyCd.USD)` |
| View JSP (USD-only) | `WECMS DEV/WECMS/web/view/jex/wabooks/fnsh/fnsh_0005_001_view_osmc.jsp` |
| TypeScript (USD-only driver) | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0005_001.ts` — hardcodes `CCY_CD: "USD"`, `CCY_CD_SYM: "$"` |
| Shared list helper | `WECMS DEV/WECMS/src/wabooks/helper/FnshHelper.java#fnsh_0001_001_r001(..., CcyCd ccyCd, ...)` — invoked with `CcyCd.VND` for the VND page and `CcyCd.USD` for the USD page |
| Account-type enum (canonical) | `WECMS DEV/WECMS/src/wabooks/enums/AcctTypeCd.java` — `01` LOAN, `02` DEPOSIT, `03` CURRENT_KHR, `04` CURRENT_USD, `05` SAVINGS_KHR, `06` SAVINGS_USD, `07` CURRENT_KRW, `08` SAVINGS_KRW |
| IDO (totals, per-currency) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_MSTR_R001_CNT.xml` |
| FCY-related popup (view-only, `actUseYn=N`) | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.fnsh_0001_002.xml` — sub-modal, not the list page |
| Live DEV URL (USD-only twin) | `https://dev.wecms.vn/fnsh_0005_001.act` |

JEX UPPER_SNAKE field IDs and `BIZ_KEY` semantics are preserved 1:1 in the refactor. The legacy USD page uses an `acct_type_cd IN ('04','06','02')` predicate **on top of** the `ccy_cd = 'USD'` filter — the refactor extends that set to cover KRW types as well (see "Account types on the FCY page" below).

---

## UI behaviour

1. On mount, page issues `GET /api/v1/banking/accounts?currency=FCY&sortField=...&sortDirection=...&page=0&size=10`.
2. Backend resolves `currency=FCY` to `ccy_cd <> 'VND'` and returns every non-VND account for the tenant.
3. The first response under "All Currency" caches the distinct CCYs from `TOTALS_BY_CCY` into `currencyOptions` so the dropdown shows every CCY found in the unfiltered FCY result. Narrow responses (`currency=USD/KRW/…`) do **not** refresh the cache — the dropdown stays populated when the user picks a single CCY.
4. Summary tiles render **one per CCY group** from `TOTALS_BY_CCY`. With "All Currency" selected the tile list shows every non-VND group; with a concrete CCY selected the list collapses to a single tile in that CCY. The legacy single-tile fallback fires only when the backend returns no `TOTALS_BY_CCY` (offline / error path).
5. Currency dropdown change: wrapper sets `selectedCurrency`, resets page to 0, re-fires the API call with the new wire `currency`. Selecting the "All Currency" option resets `selectedCurrency = null` (wire = `FCY`).
6. Sort, refresh, View Setting, Excel export, pagination, bank filter, keyword search: identical to VND, just keyed against the FCY screen ID and fed by `wireCurrency` instead of a hardcoded `"VND"`.

---

## Filters

All filters are **server-side** (parity with JEX `fnsh_0001_001_r001`).

| Filter | UI | Backend handling |
|---|---|---|
| Bank | Dropdown options come from the response's `BANKS_AVAILABLE` field — distinct `BANK_CD`s scoped to tenant + the current wire `currency` only. Never narrowed by current bank/keyword filter. | `bankCd` query param → `WHERE BANK_CD = ?` exact match. |
| Currency (FCY-only) | Second dropdown chip rendered when `multiCurrencyMode=true`. Default value renders as "All Currency". Options sourced from `currencyOptions` (cached from the first unfiltered FCY response). | `null` selection ⇒ wire `currency=FCY` ⇒ backend `WHERE CCY_CD <> 'VND'`. Concrete value ⇒ wire `currency=<CCY>` ⇒ backend `WHERE CCY_CD = <CCY>`. |
| Keyword | Text input (live render) — debounced 300 ms before the wire roundtrip. | Case-insensitive partial match against the **formatted** account nickname (output of `wb_format_acct_nick_nm`). Mirrors JEX `SRCH_WD` exactly. `%`/`_`/`!` escaped server-side. |
| Date | Not applicable — current balances, not history. | — |
| Refresh | Button — increments `refreshKey`, refetches list. | None |

The bank dropdown and the currency dropdown compose: `WHERE BIZ_KEY=? AND ACTV_YN='Y' AND <ccy predicate> AND BANK_CD=? AND keyword-match`.

---

## Table columns

Default columns and order — identical to VND, registered in `SYS_SCR_OPTION_WECMS` for `banking-account-inquiry-fcy` / `default`:

| FIELD_ID | Column | Source field | Visible by default |
|---|---|---|---|
| `CHECKBOX` | row-select checkbox | client-side selection state | yes (sys-pinned, hidden in popover) |
| `BANK` | Bank | `BANK_NM` (fallback `BANK_CD`) | yes |
| `ACCOUNT_NICKNAME` | Account / Nickname | `ACCT_NO` + `ACCT_NICK_NM` | yes |
| `BALANCE` | Balance | `BAL` | yes |
| `AVAILABLE_BALANCE` | Available Balance | `REAL_AMT` | yes |
| `VAS_ACCOUNT` | VAS Account | `VAS_ACCT_NM` (legacy) | hidden by default |
| `LAST_UPDATE` | Last Update | `ACCT_BAL_UPD_DTM` (YYYYMMDDHHmmss) | yes |

Special FIELD_IDs (`CHECKBOX`, `ETC`) are admin-controlled — frontend popover hides them, backend `ScreenOptionService.SPECIAL_FIELD_IDS` locks `USE_YN='Y'` and pins `DISP_ORD` regardless of payload.

The FCY screen seeds the **same column set as VND** (V43, mirroring V32/V33/V34/V41/V42) — the two screens diverge only in the View Setting / Excel scope key (`banking-account-inquiry-fcy` vs. `banking-account-inquiry-vnd`), not in the rendered column shape.

---

## Account types on the FCY page

`ACCT_TYPE_CD` resolution mirrors the legacy `wabooks.enums.AcctTypeCd` enum verbatim. The refactor's `BankCdResolver.ACCT_TYPES` map now exposes all seven non-LOAN codes:

| Code | Legacy enum | Refactor label | Typical CCY | Visible on this page? |
|---|---|---|---|---|
| `01` | `LOAN` | *(unmapped — loans never appear on Account Inquiry)* | — | No |
| `02` | `DEPOSIT` | `Fixed` | VND, USD, KRW (mixed) | Yes (when ccy ≠ VND) |
| `03` | `CURRENT_KHR` | `Current` | VND/KHR | No (VND-only) |
| `04` | `CURRENT_USD` | `Foreign Current` | USD | Yes |
| `05` | `SAVINGS_KHR` | `Savings` | VND/KHR | No (VND-only) |
| `06` | `SAVINGS_USD` | `Foreign Savings` | USD | Yes (forward-looking — no rows in active tenant yet) |
| `07` | `CURRENT_KRW` | `Current Accounts` | KRW | Yes |
| `08` | `SAVINGS_KRW` | `Saving Accounts` | KRW | Yes (forward-looking — no rows in active tenant yet) |

The asymmetric labelling (`Foreign X` for USD codes vs. `X Accounts` for KRW codes) is intentional and mirrors the legacy enum's own `getLabel()` exactly — `04`/`06` had Korean labels (`외화입출식예금`/`외화보통예금`) which we translate to "Foreign Current"/"Foreign Savings"; `07`/`08` already had English labels in the legacy enum and we keep them verbatim.

**Frontend chip rendering.** The FCY chip displays `${ccy} • ${ACCT_TYPE_NM}` using the **full backend label** (e.g. "USD • Foreign Current", "KRW • Current Accounts") rather than the narrow `bankType` enum, so the legacy distinction stays visible.

**Active tenant data (2026-05-03 snapshot, `VN_VNM0004_000001`):** USD rows use `02` and `04`; KRW rows use `07`. Neither `06` (Foreign Savings) nor `08` (Saving Accounts) is currently used — the labels exist for completeness when the data appears.

---

## Pagination

**Server-side** (parity with JEX `fnsh_0001_001_r001` `PAGE_NO` / `PAGE_PER_CNT`). Inherited from the shared `AccountInquiryClient`:

- `page` (zero-based) and `size` query params on `/api/v1/banking/accounts`.
- Default `size = 10`; options offered: `10 / 20 / 50 / 100`; backend clamps to `MAX_PAGE_SIZE = 100`.
- Per-currency totals + scalar `BALANCE`/`AVAILABLE_BAL` come from a sibling aggregation query running the same WHERE chain — they stay stable across pagination.
- Page resets to 0 on every filter change (bank, currency, keyword, page size); preserved on sort change.
- Excel export bypasses pagination via `Pageable.unpaged()` so the spreadsheet contains every matching row.

---

## Amount format (FCY)

Per-currency formatting via `formatAmount(value, ccyCd, true)` from `lib/currency.ts` — locale + symbol determined by `ccyCd`:

| CCY | Formatter | Sample |
|---|---|---|
| `USD` | en-US, prefix `$`, 2 decimals | `$12,931.38` |
| `KRW` | en-US, prefix `₩`, 0 decimals | `₩0` |
| `EUR` | en-US, prefix `€`, 2 decimals | `€1,234.56` |
| `JPY` | en-US, prefix `¥`, 0 decimals | `¥1,234` |
| Other non-VND | vi-VN, no symbol | `1.234,56` |

Rules:
- Each summary tile uses **its own group's CCY** — never the page's static currency. The legacy "all-FCY" mixed-currency total is intentionally not rendered.
- Each table row's `BAL` / `REAL_AMT` is formatted using the row's `CCY_CD` (per-row formatter — the table contains rows in multiple CCYs simultaneously when wire `currency=FCY`).
- Empty / null `BAL` or `REAL_AMT` renders as `-` not `0`.
- Negative balances render with a leading minus, never parentheses.
- The summary tiles, the table cells, the print popup, and the Excel subheader all share the same formatter — never reformat downstream.

Backend mirror: `AmountFormatter.forDisplay(value, ccyCd)` (Java) matches the frontend `formatAmount(...)` 1:1 and is used for the Excel subheader and any free-text cells. POI numeric data cells (`format: "amount"`) ship raw decimal — POI applies `#,##0.##` at cell level so Excel can re-sum the column natively.

---

## View Setting / Print / Excel

- **View Setting** — global feature. Popover bound to `WEB_SVC_ID = banking-account-inquiry-fcy`, `DYM_AREA_ID = default`. Allowed FIELD_IDs match the table above. Per-user overrides in `USER_SCR_OPTION_WECMS` are scoped to this screen ID — they do **not** mix with the VND scope.
- **Print** — not yet implemented in the refactor (same status as VND).
- **Excel export** — `POST /api/v1/banking/accounts/export` with body `{ screenId: "banking-account-inquiry-fcy", currency: <wire>, filters: { bankName, keyword }, sort, columns, fileName }`. The `AccountInquiryFcyExportProvider` registers the screen ID, defaults `currency` to `"FCY"`, reuses `AccountService.list(...)` so list and export share one query path, and composes a multi-line subheader (one line per CCY group) sourced from `AccountListResponse.totalsByCcy()`. Wire `currency=FCY` ⇒ multi-line subheader; concrete CCY ⇒ single line that reads identically to a VND-style export.

---

## TODO / open questions

- [x] **FCY screen ID + View Setting seed** — `banking-account-inquiry-fcy` registered in `SCREEN_IDS` and seeded by V43 (CHECKBOX + 6 columns mirroring VND).
- [x] **Server-side `currency=FCY` resolution** — implemented via `AccountService` + `AcctMstrRepository.findByBizKeyAndCcyCdNotAndActvYn(...)`.
- [x] **Multi-currency summary tiles** — implemented via `TOTALS_BY_CCY` + per-CCY `SummaryTile` render.
- [x] **In-page currency dropdown** — implemented (cached options, page-reset on change).
- [x] **FCY Excel export provider** — implemented (`AccountInquiryFcyExportProvider`).
- [x] **JEX-side FCY service ID confirmed** — the legacy live page is `fnsh_0005_001` (USD-only). Documented as a refactor enhancement (multi-CCY scope has no JEX parity).
- [x] **Account-type label mapping** — `BankCdResolver.ACCT_TYPES` now covers `02–08` per the legacy `AcctTypeCd` enum (Plan 2 / 2026-05-03).
- [ ] **Plan 1 — Frontend formatter parity** — `lib/currency.ts` still treats only USD as 2 dp en-US; EUR/KRW/JPY render with vi-VN separators while the backend uses en-US. On-screen tile and Excel subheader print different numbers for these CCYs.
- [ ] **Plan 3 — `ACCT_TYPE_CD` predicate** — refactor relies solely on `ccy_cd <> 'VND'`; legacy applies an additional `acct_type_cd IN (...)` filter to exclude loans and mismatched data. Add the predicate after this MR lands.
- [ ] **Plan 5 — Currency-options cache invalidation** — `currencyOptions` cached on first unfiltered response; refresh button does not refresh the cache.
- [ ] **Plan 6 — `mapAccountRow` default** — defaults `currencyCd` to `"VND"` when `api.CCY_CD` is missing. Should default to the page's `wireCurrency`.
- [ ] **Q3 — Hide Update button on FCY** — Update currently refreshes only; legacy semantic is live bank scraping. Hide on FCY until scraping lands.
- [ ] **Server-side FCY pagination plumbing** — VND wrapper (`AccountInquiryClient`) has `serverPaging` controlled mode; the FCY page inherits it via `multiCurrencyMode=true`. Confirm wire deps (`wireCurrency`, page reset on currency change) hold under load.
- [ ] **`HAND_REG_YN` / `ACCT_CUST_TYPE` filter** — held pending PO confirmation (mirrors the VND TODO).
- [ ] **Cross-CCY totals card** — confirm with PO whether to render a "Total accounts (all FCY)" pill at all, or leave the tile area as one-tile-per-CCY only. Mixed-currency arithmetic is intentionally not rendered.
- [ ] **JEX feature doc** — `docs/jex/features/banking-account-inquiry-fcy.md` does not yet exist. Create the read-only counterpart for the legacy `fnsh_0005_001` page so the kebab-case slug matches the refactor doc.
- [ ] **Decide Print strategy** — port JEX print or replace with browser print stylesheet (same TODO as VND).
- [ ] **Cross-check `IDO.ACCT_MSTR_R001_CNT.xml`** per-currency totals (`TOT_USD_*`, `TOT_KHR_*`, `TOT_USD_REAL_AMT`, `TOT_KHR_REAL_AMT`) against the refactor's `TOTALS_BY_CCY` semantics — confirm rounding + inclusion rules under FCY mode.

---

## Cross-references

- Backend API design: [../../backend/banking/account-inquiry-fcy-api.md](../../../references/api-spec/banking/account-inquiry-fcy-api.md)
- Frontend page design: [../../frontend/banking/account-inquiry-fcy-page.md](../../../../../wecms-web/docs/features/banking/current-savings-fcy/account-inquiry.md)
- DB design: [../../database/banking/account-inquiry-fcy-db.md](../../../references/database/banking/account-inquiry-fcy-db.md)
- VND twin (read-only reference): [./account-inquiry-vnd.md](../current-savings-vnd/account-inquiry.md)
- Migration mapping (service-level): [../../migration/service-mapping.md](../../../references/migration/service-mapping.md)
- API mapping (endpoints): [../../backend/api-mapping.md](../../../references/api-spec/api-mapping.md)
- Page mapping (routes): [../../frontend/page-mapping.md](../../../../../wecms-web/docs/references/page-mapping/page-mapping.md)
- DB mapping (tables): [../../database/db-mapping.md](../../../references/database/table-mapping/db-mapping.md)

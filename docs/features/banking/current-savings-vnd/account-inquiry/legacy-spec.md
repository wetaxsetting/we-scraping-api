# Banking > Account Inquiry (VND) — Refactor Feature Spec

> **📁 Primary documentation home moved.** The consolidated, current backend feature documentation now
> lives in the dedicated folder [../account-inquiry-vnd/](../account-inquiry/README.md) (README ·
> functional-specification · business-flow · database-mapping · interface-specification · source-code-index ·
> migration-guide · test-scenarios · refactor-gap-analysis). This single-file spec is **preserved** as a
> quick reference and remains valid; new detail is maintained in the folder.

**Module:** Banking
**Menu:** Account Inquiry (VND)
**Route:** `/banking/account-inquiry`
**Refactor screen ID:** `banking-account-inquiry-vnd`
**JEX counterpart:** [docs/jex/features/banking-account-inquiry-vnd.md](../../../../../../docs/jex/features) *(create if missing)*
**Status:** Working — backend live, frontend wired, View Setting + Excel export integrated. Mock fallback retained for offline dev.

JEX is **read-only reference**. This spec captures the refactor design; JEX behavior is the functional source of truth.

---

## Overview

Lists all VND-denominated bank accounts for the current tenant, with per-account balance and available-balance figures, refresh timestamp, and per-account inquiry status. Users can filter by bank and keyword, sort by any visible column, change visible columns via the global View Setting popover, and export the visible rows to Excel.

---

## Scope

**In scope:**
- VND-only account list (page is locked to `currency=VND` at the route level)
- Per-account row: bank, account no, nickname, balance, available balance, last update, status flag
- Top summary card: total accounts, total balance (VND), total available balance (VND)
- Filters: bank dropdown, keyword search, refresh
- Sort: column header click (whitelisted FIELD_IDs)
- Pagination: client-side over the full list returned
- View Setting popover: toggle/reorder columns per user (`WEB_SVC_ID = banking-account-inquiry-vnd`)
- Excel export: server-rendered XLSX with the same filter + sort applied

**Out of scope (handled by other features):**
- FCY (foreign currency) account inquiry — separate page `banking-account-inquiry-fcy`
- Per-account transaction history — separate route + endpoint (`/banking/transaction-history`, `GET /api/v1/banking/accounts/{acctSeqNo}/transactions`)
- Adding / editing accounts — settings-side feature (Bank Account Setup)

---

## Legacy reference (JEX)

| Asset | Path |
|---|---|
| JEX service ID (read) | `fnsh_0001_001_r001` |
| WSVC XML | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.fnsh_0001_001_r001.xml` |
| Action JSP | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0001_001_r001_act.jsp` |
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/fnsh/fnsh_0001_001_view.jsp` |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0001_001.ts` |
| IDO (totals) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_MSTR_R002.xml` |
| IDO (currency totals) | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.ACCT_MSTR_R001_CNT.xml` |
| Live DEV URL | `https://dev.wecms.vn/fnsh_0001_001.act` |

JEX UPPER_SNAKE field IDs and `BIZ_KEY` semantics are preserved 1:1 in the refactor.

---

## UI behavior

1. On mount, page issues `GET /api/v1/banking/accounts?currency=VND&sortField=...&sortDirection=...`.
2. Empty / errored response falls back to the mock seed (offline dev affordance only — never shown when the API responds).
3. Summary card aggregates `TOTALS_BY_CCY` (VND row) returned by the backend; falls back to client-side sum if absent.
4. Sort click rotates `sortField` / `sortDirection`; stable-data latching prevents pagination flicker between requests.
5. View Setting popover updates local column visibility immediately; on save, columns persist server-side via `PUT /api/v1/screen-options/banking-account-inquiry-vnd/default`.
6. Excel button posts to `POST /api/v1/banking/accounts/export` with the current `currency`, filter (bankName, keyword), and sort. Server returns an XLSX stream named `account_inquiry_{YYYY-MM-DD}.xlsx`.
7. Refresh button increments `refreshKey` → triggers a refetch with the current sort + filter.

---

## Filters

All filters are **server-side** as of Sprint 2 (parity with JEX `fnsh_0001_001_r001`).

| Filter | UI | Backend handling |
|---|---|---|
| Bank | Dropdown options come from the response's `BANKS_AVAILABLE` field (distinct `BANK_CD`s scoped to tenant + currency, never narrowed by current filter). Option key is the legacy `BANK_CD`; label is the bank name from `BANK_INFM.RPRS_BANK_NM`. | `bankCd` query param → `WHERE BANK_CD = ?` exact match |
| Keyword | Text input (live render) — debounced 300 ms before the wire roundtrip | `keyword` query param → case-insensitive partial match against the **formatted** account nickname (output of `wb_format_acct_nick_nm` — same string shown on screen). Mirrors JEX `SRCH_WD` exactly. `%`/`_`/`!` escaped server-side. |
| Date | Not applicable — this screen shows **current balance**, not history. Date filters live on Transaction History | — |
| Refresh | Button — increments `refreshKey`, refetches list | None |

---

## Table columns

Default columns and order (FIELD_IDs registered in `SYS_SCR_OPTION_WECMS` for `banking-account-inquiry-vnd` / `default`):

| FIELD_ID | Column | Source field | Visible by default |
|---|---|---|---|
| `CHECKBOX` | row-select checkbox | client-side selection state | yes (sys-pinned, hidden in popover) |
| `BANK` | Bank | `BANK_NM` (fallback `BANK_CD`) | yes |
| `ACCOUNT_NICKNAME` | Account / Nickname | `ACCT_NO` + `ACCT_NICK_NM` | yes |
| `BALANCE` | Balance | `BAL` | yes |
| `AVAILABLE_BALANCE` | Available Balance | `REAL_AMT` | yes |
| `VAS_ACCOUNT` | VAS Account | `VAS_ACCT_NM` (legacy) | hidden by default |
| `LAST_UPDATE` | Last Update | `ACCT_BAL_UPD_DTM` (YYYYMMDDHHmmss) | yes |

Special FIELD_IDs `CHECKBOX` and `ETC` are admin-controlled — their `USE_YN='Y'` and `DISP_ORD` are locked server-side regardless of client payload (see `view-setting` *(refactor doc TBD — Needs Confirmation; see OQ-DOC-2)* when written).

---

## Pagination

**Server-side** as of Sprint 2.

- `page` (zero-based) and `size` query params on `/api/v1/banking/accounts`.
- Default `size = 10` (matches the canonical `PAGE_SIZE_OPTIONS[0]`); options offered: `10 / 20 / 50 / 100`; backend clamps to `MAX_PAGE_SIZE = 100`.
- Spring Data `Page<T>` drives the response — `TOTAL_COUNT` (unpaged filtered total), `TOTAL_PAGES`, `PAGE_NUMBER`, `PAGE_SIZE` all flow back to the client.
- Per-currency totals + scalar `BALANCE`/`AVAILABLE_BAL` come from a sibling aggregation query running the same WHERE chain — they stay stable across pagination.
- Frontend wrapper (`AccountInquiryClient`) owns `page`/`pageSize` state and sends them with every request. Page resets to 0 on every filter change.
- Excel export bypasses pagination via `Pageable.unpaged()` so the spreadsheet contains every matching row regardless of which page the user is currently viewing.

---

## Amount format (VND)

- VND amounts use `formatAmount(value, 'VND')` — locale-aware thousands separators, no decimals (VND has no minor unit).
- Negative balances render with a leading minus, never parentheses.
- Empty / null `BAL` or `REAL_AMT` renders as `-` not `0`.
- The summary card and table cells share the same formatter — never reformat downstream.

---

## View Setting / Print / Excel

- **View Setting** — global feature. Popover bound to `WEB_SVC_ID = banking-account-inquiry-vnd`, `DYM_AREA_ID = default`. Allowed FIELD_IDs match the table above.
- **Print** — not yet implemented in the refactor. JEX has a print path; defer until business confirms scope.
- **Excel export** — `POST /api/v1/banking/accounts/export`. Body carries `screenId`, `currency`, filters, sort, and the column config from `ACCOUNT_INQUIRY_EXPORT_COLUMNS`. Backend re-applies the same `AccountService.list()` query so list and export share one source.

---

## TODO

- [x] **Server-side pagination** — implemented (Sprint 2, page/size params).
- [x] **Server-side `bankCd` filter** — implemented.
- [x] **Server-side keyword search (`SRCH_WD`)** — implemented.
- [x] **`ACCT_NICK_NM` formatter parity** — implemented via DB function migration. V44 installs `wb_format_acct_nick_nm` and `wb_get_bank_nm` verbatim from JEX DEV plus the `BANK_INFM_DETL` table + 68-row seed for `BANK_DETL_INFO_CD = '10'`. `AcctMstr.formattedAcctNickNm` carries the formatted value via Hibernate `@Formula`. `AccountService.toRow()` reads it. Excel export inherits the same formatted value because list + export share `AccountService.list(...)`. **Byte-for-byte parity with JEX, including the PG Bank / VN-prefix / Letters4Digits regex cases.**
- [x] **`BANK_NM` resolution** — full name via `BankCdResolver` (reads `BANK_INFM.RPRS_BANK_NM`); short name via the new `wb_get_bank_nm(BANK_CD, 's')` function inside the formatter.
- [x] **`BANK_INFM_DETL` migrated** — V44 (table + seed for `cd='10'`).
- [ ] **`HAND_REG_YN` filter** — held pending PO confirmation.
- [ ] **`ACCT_CUST_TYPE` filter** — held pending PO confirmation.
- [ ] **Date range filter** — held pending PO confirmation (verify whether the JEX VND screen actually accepts a date range).
- [ ] **Additional JEX REC fields** (`NXISAS_ACCT_TYPE`, `CORC_BF_BAL`, `CERT_INFO_*`, `BANK_NM2`, `LST_LGN_*`, `TRSC_DT`, `TRN_LST_SUC_DTM`, `BANK_CERT_SEQ`, `ACCT_NM`, `ACCT_CUST_TYPE`, `VAS_ACCT_CD`, `VAS_ACCT_NM`, `HAND_REG_YN`, `ACCT_HIS_LST_INQ_DT`/`…_INQ_STTS`/`…_INQ_MSG`) — held pending PO triage.
- [ ] **FCY client migration** — `AccountInquiryFcyClient` still uses uncontrolled mode (client-side filter + pagination). Mirror the VND wrapper's `serverPaging` plumbing in a follow-up MR so the FCY page also benefits from server-side filter + pagination.
- [ ] Decide **Print** strategy — port JEX print or replace with browser print stylesheet.
- [ ] Confirm **VAS_ACCOUNT** column source field — JEX uses `VAS_ACCT_NM`; refactor entity does not yet expose it. Add to `AcctMstr` + `AccountRow` if business confirms it must be visible.
- [ ] Verify `ACCT_BAL_INQ_STTS` success codes (`00000000`, `42110000`) match production; if PROD adds new success codes, update `mapAccountRow.error` logic.
- [ ] Decide whether to load the **bank dropdown** server-side (current behavior derives it from loaded rows; will miss banks if a tenant has zero VND accounts at a given bank).
- [ ] Cross-check JEX's `REC_TOT.TOT_BAL` semantics vs refactor's summary `BALANCE`/`AVAILABLE_BAL` — confirm rounding and inclusion rules under FCY mode.

## Cross-references

- Backend API design: [../../backend/banking/account-inquiry-vnd-api.md](../../../../references/api-spec/banking/account-inquiry-vnd-api.md)
- Frontend page design: [../../frontend/banking/account-inquiry-vnd-page.md](../../../../../../wecms-web/docs/features/banking/current-savings-vnd/account-inquiry/README.md)
- DB design: [../../database/banking/account-inquiry-vnd-db.md](../../../../references/database/banking/account-inquiry-vnd-db.md)
- Migration mapping (service-level): [../../migration/service-mapping.md](../../../../references/migration/service-mapping.md)
- API mapping (endpoints): [../../backend/api-mapping.md](../../../../references/api-spec/api-mapping.md)
- Page mapping (routes): [../../frontend/page-mapping.md](../../../../../../wecms-web/docs/references/page-mapping/page-mapping.md)
- DB mapping (tables): [../../database/db-mapping.md](../../../../references/database/table-mapping/db-mapping.md)

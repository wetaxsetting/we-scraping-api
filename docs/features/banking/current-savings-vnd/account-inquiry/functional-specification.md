# Account Inquiry (VND) — Functional Specification (Refactor Backend)

**Screen ID:** `banking-account-inquiry-vnd` · **JEX:** `fnsh_0001_001` · **Hub:** [README.md](README.md)

Describes *what* the refactored backend delivers. The *how* (flows) is in
[business-flow.md](business-flow.md); the wire contract is in [interface-specification.md](interface-specification.md).

---

## 1. Scope

**In scope (implemented):**

- VND-only account list — the page is locked to `currency=VND` at the route level; the service maps
  `VND` to `ACCT_TYPE_CD IN ('02','03','05')` (deposit + current + savings) and `CCY_CD = 'VND'`.
- Per-account row: bank, account no, account type, nickname (formatted), balance, available balance,
  per-account inquiry status, last-update timestamp, manual-registration flag, cert-connection flag.
- Top summary: total account count, total balance, total available balance — plus a per-currency
  breakdown (`totals_by_ccy`) that stays the single VND row on this screen.
- Server-side filters: bank (`bankCd`), keyword (formatted nickname).
- Server-side sort: whitelisted `FIELD_ID` → entity property via `AccountSortField`.
- Server-side pagination: `page` (0-based) + `size` (default 10, max 100).
- **Update balance / account sync / transaction sync** against the bank via the NXiSAS / Coocon
  smart-gateway (`POST .../transactions-sync` + the OTP/QnA/CAPTCHA challenge-resume endpoints).
- **Sync history** read (`GET /api/v1/banking/sync-history`) — past real-time inquiry attempts.
- **Account detail / transaction drill-down** — sibling endpoints `GET .../{acctSeqNo}/transactions*`.
- View Setting popover (column visibility/order) bound to `banking-account-inquiry-vnd`.
- Excel export (`POST .../accounts/export`) — same filter + sort path as the list.

**Out of scope (other features / deferred):**

- FCY account inquiry — sibling page `banking-account-inquiry-fcy` (shares `AccountService`/`AccountController`;
  the FCY *frontend* client still uses uncontrolled client-side paging — tracked, not a VND gap).
- Adding / editing / deleting accounts — Settings → Bank Account.
- Print — JEX has a print path; refactor frontend ships a browser-print helper, backend has no print endpoint.
- Smart Organizer (auto-categorisation) — separate `domain/banking/smartorganizer`.

---

## 2. Filters

All filters are **server-side** (parity with JEX `fnsh_0001_001_r001`).

| Filter | Wire param | Backend handling |
|---|---|---|
| Currency scope | `currency` | `VND` → `CCY_CD = 'VND'` + `ACCT_TYPE_CD IN ('02','03','05')`. (`FCY` = `CCY_CD <> 'VND'`; `ALL` = no currency predicate. Loan type `01` is excluded in every scope.) |
| Bank | `bankCd` | Exact `WHERE BANK_CD = :bankCd` (legacy `BANK_CD`). Mirrors JEX `BANK_CD` input. |
| Keyword | `keyword` | Case-insensitive partial `LIKE … ESCAPE '!'` against the **formatted** nickname (`wb_format_acct_nick_nm(...)` — the exact string shown in the cell). Service lower-cases + escapes `%`/`_`/`!` + wraps `%…%`. Mirrors JEX `SRCH_WD`. |
| Date | — | Not applicable — this screen shows current balance, not history. Date filters live on Transaction History. |

Bank-dropdown options (`banks_available`) are the distinct `BANK_CD`s scoped to tenant + currency
**only** — never narrowed by the active `bankCd`/`keyword`, so the option set stays stable while filtering.

---

## 3. Table columns

View Setting `FIELD_ID`s registered in `SYS_SCR_OPTION_WECMS` for `banking-account-inquiry-vnd` / `default`:

| FIELD_ID | Column | Source | Default visible | Sortable (`SORT_YN`) |
|---|---|---|---|---|
| `CHECKBOX` | row-select | client selection | yes (sys-pinned, hidden in popover) | no (special) |
| `BANK` | Bank | `BANK_NM` (+ ccy/type chip + `ACCT_NO`) | yes (`FIX_YN='Y'`) | yes → `bankCd` |
| `ACCOUNT_NICKNAME` | Account / Nickname | `wb_format_acct_nick_nm(...)` | yes | yes → `acctNickNm` |
| `BALANCE` | Balance | `BAL` | yes | yes → `bal` |
| `AVAILABLE_BALANCE` | Available Balance | `REAL_AMT` | yes | yes → `realAmt` |
| `VAS_ACCOUNT` | VAS Account | (placeholder — no backing field yet) | hidden (`USE_YN='N'`, `DISP_YN='N'`) | no (`SORT_YN='N'`, V41) |
| `LAST_UPDATE` | Last Update | account-type-aware (see below) + status columns | yes | yes → `acctHisLstInqDt` (FCY-A02) |

`CHECKBOX`/`ETC` are admin-controlled specials — server locks `USE_YN='Y'` + sys `DISP_ORD` on save
regardless of payload. **Sort key `LAST_UPDATE` maps to `acctHisLstInqDt`** (FCY-A02, PO decision
2026-06-06 — supersedes the old HR-1 `acctBalLstInqDtm` mapping). The **displayed** "Last Update" value
is resolved account-type-aware by `AccountService.resolveLastUpdate(...)`: fixed-deposit-only banks
(`ACCT_TYPE_CD='02'` other than Sacombank `11704004`) display `ACCT_BAL_LST_INQ_DTM`; all other (online)
accounts display `ACCT_HIS_LST_INQ_DT`. The wire field name stays `acct_bal_upd_dtm` for
frontend-contract stability, but its value is the resolved one (not the raw `ACCT_BAL_LST_INQ_DTM`).

---

## 4. Sorting

- Whitelist `AccountSortField`: `BANK→bankCd`, `ACCOUNT_NICKNAME→acctNickNm`, `BALANCE→bal`,
  `AVAILABLE_BALANCE→realAmt`, `LAST_UPDATE→acctHisLstInqDt` (FCY-A02).
- Default ORDER BY (no/unknown sort): `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` — legacy `SortHelper#86`
  parity; NULL `PRN_SEQ` rows sort last (PostgreSQL default ASC NULLS LAST).
- A user-chosen sort is applied as `Sort.by(primary).and(DEFAULT_SORT)` so equal values keep a
  deterministic order across pages.
- Unknown / blank wire `sortField` falls back to `DEFAULT_SORT` — never echoed into SQL.

---

## 5. Pagination & totals

- `page` (0-based) + `size` (default `DEFAULT_PAGE_SIZE = 10`, clamped to `MAX_PAGE_SIZE = 100`; `size ≤ 0`
  falls back to default). Spring Data `Page<T>` drives `total_count` / `total_pages` / `page_number` / `page_size`.
- Scalar `balance` / `available_bal` + `totals_by_ccy` come from a **sibling aggregation query**
  (`AcctMstrRepository.aggregateByCcy`) running the *same* WHERE chain unpaged — so totals stay stable
  across pagination and always agree with the filtered set (they share parameter bindings, not rows).
- Export uses `Pageable.unpaged()` so the spreadsheet carries every matching row.

---

## 6. Amount formatting (VND)

- Wire ships raw decimal strings (`AmountFormatter.wire` — trailing zeros stripped, `"0"` for null) to
  preserve precision; display formatting is the frontend's job (`lib/currency.ts formatAmount`).
- Server-side display strings (Excel subheader, totals line) go through `AmountFormatter.forDisplay(value, ccyCd)`:
  VND → vi-VN locale, 0 fraction digits, `₫` suffix. Mirrors the frontend formatter 1:1.

---

## 7. Permissions

- Tenant scope: `BIZ_KEY` from `TenantContext.currentBizKey()` (signed JWT), first column of the
  composite PK — never accepted from request input.
- **Per-user account authority:** non-admin users only see accounts with a matching `ACCT_AUTH_INFO`
  row (`AUTH_TYPE='V'`, `USE_YN='Y'`) via an `EXISTS` predicate; admins (`TenantContext.currentIsAdmin()`)
  see all tenant accounts (no `EXISTS` term). Mirrors JEX `QueryHelper.getPermittedBankAccountDynamic`.
- Endpoint authorization: `AccountController` list/export is gated by `@menuAuthz.canAccessAccountInquiry()`
  / `canAccessAny('banking-vnd-account-inquiry', …)`; transaction endpoints by the transaction-inquiry
  menu codes. See [endpoint-authorization-matrix.md](../../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md).

---

## 8. Excel export

- `POST /api/v1/banking/accounts/export` via the shared `common/export` pipeline.
- Provider `AccountInquiryVndExportProvider` — `screenId = banking-account-inquiry-vnd`, `defaultCurrency = "VND"`,
  allow-list `{BANK, ACCOUNT_NICKNAME, BALANCE, AVAILABLE_BALANCE, VAS_ACCOUNT, LAST_UPDATE}`.
- Re-invokes `AccountService.list(...)` (one query path for list + export). Backend computes the
  `N account(s) · Balance: … · Available Balance: …` subheader server-side via `AmountFormatter.forDisplay`.
- Visibility is re-intersected with the persisted View Setting + allow-list before columns are emitted
  (defence in depth — hidden columns never leak).

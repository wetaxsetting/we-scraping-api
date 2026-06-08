# Transaction History (FCY) — Functional Specification (Backend)

The *what* of the refactored FCY transaction inquiry. The *how* (flow, in-memory filtering, currency
resolution) is in [business-flow.md](business-flow.md); the wire contract is in
[interface-specification.md](interface-specification.md). JEX source of truth: `fnsh_0006_001` —
[docs/jex/features/transaction-history-fcy/functional-specification.md](../../../../../docs/jex/features/transaction-history-fcy/README.md).

> FCY and VND share one controller + one service + one repository pair. This document calls out
> only where FCY behaviour differs from the [VND twin](../transaction-history-vnd/functional-specification.md);
> everything not called out is identical.

---

## 1. Scope

Per-account, tenant-scoped foreign-currency transaction ledger. The account is identified by the
synthetic `acctSeqNo = {bankCd}_{acctNo}` path variable. The account's currency
(`ACCT_MSTR.CCY_CD`, a non-VND code such as `USD`/`KRW`/`JPY`/`EUR`/`THB`) is recovered server-side
and used to restrict the returned rows to that currency (FCY-T01, [README §FCY-T01](README.md#approved-modernization--fcy-t01-multi-currency)).

Out of scope of this feature: bank synchronization (owned by Account Inquiry FCY —
`BankTransactionSyncService`), and manual transaction entry (legacy `fnsh_0006_002` — not ported).

---

## 2. Filters (request parameters)

All on `GET /api/v1/banking/accounts/{acctSeqNo}/transactions`. Identical to the VND twin.

| Filter | Query param | Values | Where applied |
|---|---|---|---|
| Date range | `from` / `to` | `yyyyMMdd` (both optional; both-or-neither) | **DB-side** — `TRSC_DT BETWEEN` in the derived finder |
| Currency | *(none — derived)* | account's `ACCT_MSTR.CCY_CD` | **In-memory** — `fetchRows(...)` filters `h.getCcyCd().equals(r.ccyCd)` |
| Transaction type | `type` | `DEPOSIT` / `WITHDRAW` / `ALL` (blank = all) | In-memory — `matchesTypeFilter` (`DEPOSIT`→`INOUT_DV='1'`, `WITHDRAW`→`'2'`) |
| Confirmation status | `cnfm_yn` | `Y` / `N` / `ALL` | In-memory — `matchesCnfmFilter` against the confirmed rcv_pay set |
| Keyword | `keyword` | free text, case-insensitive | In-memory — `matchesKeyword` (see §2.1) |

### 2.1 Keyword match scope (BUG-3 Option A — CLOSED)

`matchesKeyword` matches case-insensitively across: `RMRK`, `RMRK2`, `BANK_REF_NO`,
`BANK_TRNS_SHORT_ID`, `TRSC_AMT` (substring on the amount), plus the classification fields
(contact name `RCV_PAY_TRCO_NM`, purpose code `RCV_PAY_USAG_CD`, VAS code `VAS_ACCT_CD`). This widened
scope closes BUG-3 (placeholder "Search for amount, contacts name, purpose" now matches what it
promises).

---

## 3. Grid columns (View Setting `banking-transaction-history`)

The grid is **View-Setting-driven** — the visible/ordered column set comes from the shared
`banking-transaction-history` seed (same screenId for VND + FCY). The backend `TransactionRow` DTO
supplies these fields (wire names are UPPER_SNAKE — see the wire-casing note in
[interface-specification.md](interface-specification.md)):

| FIELD_ID | DTO field | Meaning |
|---|---|---|
| `TRSC_ID` | `BANK_TRNS_SHORT_ID` | Bank transaction reference id (from scraping) |
| `TRSC_DTM` | `TRSC_DT` + `TRSC_TM` | Transaction date + time (composed in UI) |
| `DEPOSIT_AMT` | `DEPOSIT_AMT` | Credit amount (in the account's FCY) when `INOUT_DV='1'` |
| `WITHDRAW_AMT` | `WITHDRAW_AMT` | Debit amount (in the account's FCY) when `INOUT_DV='2'` |
| `BAL` | `BAL` | Running balance after the transaction (passed through verbatim) |
| `RMRK` | `RMRK` | Remark / narrative |
| `FILE_NM` | `PAY_ORG_FILE_NM` | Matched payment file name (BUG-2 — CLOSED) |
| `MTCH_DT` | `MATCHED_AT` | Match datetime (BUG-2 — CLOSED) |
| `MTCH_BY` | `MATCHED_BY` | Matched-by user name (BUG-2 — CLOSED) |
| `CHART_ACCT_CD` | `CHART_ACCT_CD` / `CHART_ACCT_SEQ_NO` | Reporting (chart-of-accounts) code |
| `PURPOSE` | `USE_USAG_CD` | Purpose / usage code |
| `VAS_ACCT_CD` | `VAS_ACCT_CD` | VAS account code |
| `CONTACT_NM` | `RCV_PAY_TRCO_NM` (+ `RCV_PAY_TRCO_KEY`) | Counterparty name |
| `CONFIRMED_BY` | `CONFIRMED_BY` (+ `CONFIRMED_DTM`) | Confirmer name + datetime (BUG-1 — CLOSED) |
| *(row meta)* | `CONF_YN`, `LINK_MATCH_YN` | Confirm flag, match flag (drive UI tinting / actions) |

The legacy `fnsh_0006_001` grid carries additional columns (memo/color, project, auto-classification,
master balance, maturity date) that are **out of refactor scope** — see
[refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 4. Pagination

**Load-all + client-side pagination** (modernization, accepted pending PO). The list endpoint returns
the full filtered set with `TOTAL_COUNT`; the frontend paginates client-side (10/20/50/100). The list
query has no DB `LIMIT/OFFSET`. Legacy `fnsh_0006_001` paged DB-side via `PAGE_NO` / `PAGE_PER_CNT`;
the refactor's modernization is documented as PARTIAL/accepted in the gap analysis.

---

## 5. Sorting

**Fixed default sort, no per-column whitelist.** The derived finder bakes in
`ORDER BY TRSC_DT DESC, TRSC_TM DESC` (most-recent-first). Unlike Account Inquiry there is no
`AccountSortField`-style sort enum for transactions — per-column server-side sorting is seed-ready but
not yet wired (PARTIAL). Legacy injected sort via `SortHelper.getSortDynamic("fnsh_0002_001_r001")`
but exposed only `TRSC_DT` as sortable in the grid.

---

## 6. Totals

`TransactionListResponse` carries `DEPOSIT_TOTAL` and `WITHDRAW_TOTAL` (sum of `TRSC_AMT` over the
filtered set, split by `INOUT_DV`, wire-formatted via `AmountFormatter.wire`). `summary` additionally
returns `TOTAL_COUNT` / `CONFIRMED_COUNT` / `UNCONFIRMED_COUNT`. Totals are computed over the FCY
account's own-currency rows only (the currency filter runs before totalling).

---

## 7. Confirmation & classification workflow

Identical to the VND twin:

- **Confirm / undo** — `PUT/DELETE .../{trnsSeqNo}/confirm`. "Confirmed" = existence of a sibling
  `ACCT_TRNS_RCV_PAY_INFM` row with `CNFM_YN='Y'`. Confirm rejects if already confirmed
  (`WBE_TRANSACTION_ALREADY_CONFIRMED`); undo rejects if not confirmed
  (`WBE_TRANSACTION_NOT_CONFIRMED`). Confirm stamps `CNFM_BY_USER_ID/NM` + `CNFM_DTM`.
- **Reporting account** — `PATCH/DELETE .../{trnsSeqNo}/reporting-account`. Validates the
  `(CHART_ACCT_SEQ_NO, CHART_ACCT_CD)` pair against `CHART_ACCT_INFM` (`STTS='A'`, `DEL_YN='N'`).
- **Purpose** — `PATCH/DELETE .../{trnsSeqNo}/purpose`. Validates against `BUSI_USAG_SET_INFM`
  (`USE_YN='Y'`).
- **VAS account** — `PATCH/DELETE .../{trnsSeqNo}/vas-account`. Validates against `BUSI_VAS_ACCT_INFM`
  (`STTS='A'`, `DEL_YN='N'`).
- **Contact** — `PATCH/DELETE .../{trnsSeqNo}/contact`. Validates against `TRCO_INFM`
  (`TRCO_STTS != 'C'`); writes the `RCV_PAY_TRCO_KEY` + `RCV_PAY_TRCO_NM` snapshot pair.
- **Match / unmatch** — `GET .../{trnsSeqNo}/match-candidates`, `POST .../{trnsSeqNo}/match`,
  `DELETE .../{trnsSeqNo}/match` (delegated to `MatchService`). Smart Organizer hand-off.

All write paths are `@Transactional`; classification setters use `ensureRcvPayRow(...)` to create or
reuse the rcv_pay row independent of confirm.

---

## 8. Permissions

- **Menu authorization** — every transaction endpoint:
  `@PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")`.
  The FCY-specific code is `banking-fcy-inquire-transactions`.
- **Tenant isolation** — `bizKey` from `TenantContext.currentBizKey()` (signed JWT, never a request
  param); every finder constrains on `BIZ_KEY`.
- **Per-account data scope (#29 — CLOSED)** — `AccountAccessGuard.assertCanView(bizKey, bankCd, acctNo)`
  runs in `TransactionService.resolve()` on every list/summary/detail/confirm/classify/export path.
  Admins (`TenantContext.currentIsAdmin()`) bypass; non-admins require an active View grant
  (`ACCT_AUTH_INFO` `AUTH_TYPE='V'`, `USE_YN='Y'`). Denial → `AccessDeniedException` → HTTP **403**,
  code **`WBE1003`**.

---

## 9. Export

`POST /api/v1/banking/accounts/transactions/export` → streams XLSX via the shared `common/export`
pipeline. The provider is `TransactionHistoryExportProvider` (`screenId = banking-transaction-history`,
**shared with VND**). It reuses `transactionService.list(...)` so export and list share one query path
(including the FCY currency filter and the `AccountAccessGuard` check). The `acctSeqNo` filter is
mandatory (scopes export to one account); `from`/`to`/`type`/`cnfm_yn`/`keyword` flow through. Allowed
columns (V157+V159 seed): `TRSC_ID`, `TRSC_DTM`, `DEPOSIT_AMT`, `WITHDRAW_AMT`, `BAL`, `RMRK`,
`FILE_NM`, `MTCH_DT`, `MTCH_BY`, `PURPOSE`, `CONTACT_NM`, `CONFIRMED_BY`.

> **FCY export currency note.** The numeric data cells (`DEPOSIT_AMT`/`WITHDRAW_AMT`/`BAL`) ship as
> raw wire strings; POI applies `#,##0.##`. The subheader totals are computed via
> `AmountFormatter.forDisplay(..., null)` (no ccy symbol on the totals line) — so an FCY export does
> not prefix `$`/`₩` on the subheader, only per-cell amounts differ by account. Flagged for UAT
> sign-off in [test-scenarios.md](test-scenarios.md).

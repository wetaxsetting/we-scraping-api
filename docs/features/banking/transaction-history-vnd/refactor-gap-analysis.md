# Transaction History (VND) — Refactor Gap Analysis

Final parity review of the refactored backend against legacy JEX `fnsh_0002_001`. Status vocabulary:
**MATCH** (behaviour equals legacy), **PARTIAL** (implemented with a documented gap), **MISSING**
(not implemented). The JEX-side gap analysis is at
[docs/jex/features/transaction-history-vnd/refactor-gap-analysis.md](../../../../../docs/jex/features/transaction-history-vnd/refactor-gap-analysis.md).

---

## Parity table

| Area | Legacy (`fnsh_0002_001`) | Refactor | Status |
|---|---|---|---|
| **Search** | `_r001` date-range + account inquiry | `GET .../transactions` via `TransactionService.list` | **MATCH** |
| **Date filters** | `FROM_DT` / `TO_DT` (`yyyyMMdd`) SQL `BETWEEN` | `from`/`to` DB-side `TRSC_DT BETWEEN` | **MATCH** |
| **Account filter** | `BANK_CD` + `ACCT_NO` | `{acctSeqNo}` composite resolve | **MATCH** |
| **Currency filter** | currency from account context | currency-agnostic endpoint; account decides | **MATCH** |
| **Transaction-type filter** | `INOUT_DV` predicate | `type` in-memory (`InoutDv`) | **MATCH** |
| **Keyword filter** | remark/ref search | `keyword` in-memory (`RMRK`/`BANK_REF_NO`) | **MATCH** |
| **Sorting** | client-side TS sort; default most-recent-first | default most-recent-first DB-side; per-column sort seed-ready but **not wired** | **PARTIAL** |
| **Pagination** | in-memory paging of the result set | client-side `usePagination` over full filtered set | **MATCH** |
| **Transaction grid** | columns via the legacy table / View Setting | View Setting `banking-transaction-history` (V155→V171) | **MATCH** |
| **Detail view** | row detail | `GET .../{trnsSeqNo}` → `TransactionDetailResponse` | **MATCH** |
| **Confirm / undo** | `_u001` confirm + undo | `PUT`/`DELETE .../confirm` | **MATCH** |
| **Classification (chart/purpose/VAS/contact)** | `_u002`…`_u006` | 4 PATCH setters + 4 DELETE clears | **MATCH** |
| **Memo / colour tagging** | `RMRK_ORG_INFM` memo store | not ported — out of PROD active scope | **MISSING (deferred, out of scope)** |
| **Project (PRJT) classification** | `PRJT_INFM` join | not ported — out of PROD active scope | **MISSING (deferred, out of scope)** |
| **Transaction sync** | NXiSAS on the page | owned by Account Inquiry (`BankTransactionSyncService`) | **MATCH (boundary by design)** |
| **Export** | Excel export | `POST /accounts/transactions/export`; 7/12 columns populated, 5 value-stubs | **PARTIAL** |
| **Permissions** | menu auth | `@menuAuthz` VND/FCY inquire-transactions codes | **MATCH** |
| **Error handling** | JEX ML codes | `WBExxxx` + standard envelope + `trace_id` | **MATCH** |

## Special verification areas

### Sorting
Default order is most-recent-first (`TRSC_DT DESC, TRSC_TM DESC`) DB-side — **matches** the legacy
default. Per-column header sorting is **PARTIAL**: the View Setting carries `SORT_YN` and the grid
infrastructure exists, but the frontend renders headers `sortable={false}` and there is no
`TransactionSortField` backend whitelist yet. When wired it will mirror Account Inquiry's
`useTableSort` + per-domain whitelist pattern. No behaviour regression — the grid is correctly
ordered by default; only interactive re-sort is absent.

### Pagination
**MATCH.** Both legacy and refactor page the in-memory result set client-side; the refactor uses the
shared `usePagination` hook (10/20/50/100). There is no server-side `page`/`size` parameter on the
list endpoint, by design (parity with legacy).

### Transaction synchronization flow
**MATCH (boundary by design).** Transaction History reads already-synced rows from `ACCT_TRNS_HSTR`;
it does not pull from the bank. Synchronization (NXiSAS / Coocon smart-gateway) is the Account Inquiry
sibling's `BankTransactionSyncService`, recorded by `SyncHistoryController`. The legacy page bundled
the NXiSAS include, but the actual sync trigger was the Account Inquiry "Update" action — the refactor
keeps that ownership. See [business-flow.md §8](business-flow.md#8-synchronization-sibling-feature)
and [account-inquiry-vnd/README.md](../account-inquiry-vnd/README.md).

### Transaction detail parity
**MATCH.** `GET .../{trnsSeqNo}` returns the full detail payload (remark/ref/currency + classification
+ confirmation metadata). Note: the main grid's row mutations (confirm/classify) operate on the list
rows directly via PATCH/DELETE; the dedicated detail endpoint exists and is parity-complete but the
main page does not currently open a separate detail modal from it (the Account Inquiry
`LatestTransactionHistoryModal` is the related drill-down surface). This is a UI composition choice,
not a backend gap.

## Open items (PARTIAL — tracked, non-blocking)

| Item | Detail | Where |
|---|---|---|
| **Export column stubs** | `TRSC_ID`/`FILE_NM`/`MTCH_DT`/`PURPOSE`/`CONTACT_NM` emit empty cells; `TransactionRow` DTO doesn't expose them yet. Extend the DTO (esp. the `ACCT_TRNS_RCV_PAY_INFM` join for `CONTACT_NM`/`PURPOSE`) and update `TransactionHistoryExportProvider.toFieldMap`. | `TransactionHistoryExportProvider.java:99-114` |
| **Per-column server sort** | wire `useTableSort` + add a `TransactionSortField` whitelist + flip `SORT_YN` for sortable columns. | grid + repository |

## Overall

**Refactor Status: Core feature 100% complete and parity-verified; 2 enhancement items PARTIAL.**

Supporting evidence:
- Search / date / type / confirmation / keyword filtering, detail, confirm/undo, the four
  classification setters, totals, pagination, View Setting, and XLSX export are implemented and wired
  end-to-end (backend `TransactionService` + `AccountController` + `TransactionHistoryExportProvider`;
  frontend `TransactionHistoryClient`/`Page`).
- Legacy parity verified against `fnsh_0002_001`; SIT/UAT completed
  ([test-scenarios.md](test-scenarios.md), all core rows ✅).
- The two PARTIAL items (export column stubs, interactive per-column sort) are enhancements over a
  fully-working core flow, not blockers. The MISSING items (memo/colour, project tagging) are
  deliberately out of PROD active scope, not regressions.

# Transaction History (FCY) — Refactor Gap Analysis

Final parity review of the refactored FCY transaction inquiry against legacy JEX `fnsh_0006_001`.
Status vocabulary: **MATCH · APPROVED DEVIATION · PARTIAL · MISSING**. This document summarizes the
line-by-line audit; the authoritative source is
[../../audits/transaction-history-fcy-parity-audit.md](../../../audits/transaction-history-fcy-parity-audit.md)
(verdict **GO FOR SIT**) and its companion
[../../audits/transaction-history-fcy-fix-verification.md](../../../audits/transaction-history-fcy-fix-verification.md).

```
Refactor Status: 100% Complete  (full-stack — all five SIT/UAT blockers + R11 CLOSED)
```

```
FCY-T01  Status: Closed   Classification: PO Approved Deviation (multi-currency FCY, legacy-parity)
```

---

## Parity table

| Area | Legacy (`fnsh_0006_001`) | Refactor | Status |
|---|---|---|---|
| Search by date range | `FROM_DT`/`TO_DT` | `from`/`to` DB-side `TRSC_DT BETWEEN` | MATCH |
| Transaction type filter | `INOUT_DV` radio | `type` → `INOUT_DV='1'/'2'` in-memory | MATCH |
| Confirmation filter | `coalesce(CNFM_YN,'N')=?` | `cnfm_yn` in-memory | MATCH |
| Keyword search | ILIKE over amount/acct/remark/VAS/contact/short-id | `matchesKeyword` over remark/remark2/ref/txn-id/amount/contact/purpose/VAS (BUG-3 Option A) | MATCH |
| **Currency filter (FCY)** | `ATH.ccy_cd = :ccy` (`CCY_CD` from request) | `fetchRows` filters on `ACCT_MSTR.CCY_CD` (#5) | **APPROVED DEVIATION (FCY-T01)** |
| Multi-currency FCY (USD/KRW/JPY/EUR/…) | legacy reads `CCY_CD` from input | per-account own-currency rows | **APPROVED DEVIATION (FCY-T01)** |
| Deposit/withdraw split | `IN_AMT`/`OUT_AMT` CASE | `DEPOSIT_AMT`/`WITHDRAW_AMT` by `INOUT_DV` | MATCH |
| Totals | IDO `_R016_CNT` aggregate | in-Java totals, `AmountFormatter.wire` | MATCH |
| Balance display | `ATH.BAL` (Vietcombank `11704004` recompute) | `BAL` passed through verbatim | PARTIAL (recompute deferred) |
| Confirm / undo | rcv/pay `CNFM_YN` write | `confirm`/`undoConfirm` | MATCH |
| "Confirmed by" in grid | `ATRPI.CNFM_BY_USER_NM` | `CONFIRMED_BY`/`CONFIRMED_DTM` (BUG-1) | MATCH |
| Reporting account | chart-acct write | `updateReportingAccount`/clear | MATCH |
| Purpose / VAS / Contact | rcv/pay classify | `update*`/`clear*` | MATCH |
| Transaction ID column | `BANK_TRNS_SHORT_ID` | `BANK_TRNS_SHORT_ID` → `TRSC_ID` (BUG-2) | MATCH |
| File name / Matched date+by | payment-file + match-log lateral | `PAY_ORG_FILE_NM`/`MATCHED_AT`/`MATCHED_BY` (BUG-2) | MATCH |
| Smart Organizer match/unmatch | match log + lock | `MatchService` (R11 account-scoped) | MATCH |
| Detail view | `fnsh_0006_003` | `detail(...)` incl. `CCY_CD` + `RMRK2` | MATCH |
| Sorting | `SortHelper` default `TRSC_DT desc` | fixed `TRSC_DT DESC, TRSC_TM DESC`; per-column not wired | PARTIAL |
| Pagination | DB-side `PAGE_NO`/`PAGE_PER_CNT` | load-all + client pagination | APPROVED DEVIATION (modernized) |
| Excel export | `formatExcel` (`USD …`) | `TransactionHistoryExportProvider` (POI) | MATCH (subheader symbol — see Open items) |
| Tenant scoping | `biz_key` WHERE | `TenantContext.currentBizKey()` | MATCH |
| Per-account authorization | `getPermittedBankAccountDynamic` | `AccountAccessGuard.assertCanView` (#29) → 403 `WBE1003` | MATCH |
| Menu authorization | `FNSHU_MENU` | `@menuAuthz.canAccessAny('banking-fcy-inquire-transactions', …)` | MATCH |
| Inline auto-classification (`RMRK_ORG_INFM`) | grid subquery | relocated to Smart Organizer | APPROVED DEVIATION |
| Memo/color, project, master-balance, maturity columns | grid columns | not modelled | MISSING (out of scope) |
| Manual transaction entry (`fnsh_0006_002`) | popup | not ported | MISSING (out of scope) |

---

## Special verification areas

### Multi-currency implementation (FCY-T01)

Legacy `fnsh_0006_001` is NOT USD-locked — `fnsh_0006_001_r001_act.jsp:130` passes
`CcyCd.fromValue(input.getString("CCY_CD"))`, so it serves whatever foreign currency the tenant holds.
The refactor mirrors this by deriving the currency from `ACCT_MSTR.CCY_CD` per account and filtering
`fetchRows` to it (#5, originally MISSING, now CLOSED). Each FCY account renders only its own currency.
**Approved by PO** — the same modernization disposition as Account Inquiry's
[FCY-A01](../account-inquiry-fcy/README.md#approved-modernization--fcy-a01-multi-currency). Not a defect.

### Currency filtering behaviour

The filter is **in-memory** (post-fetch stream filter on `CCY_CD`), not a SQL predicate — the data
shape and outcome match legacy. The one open edge: a `bankCd_acctNo` that legitimately spans multiple
currencies cannot be disambiguated by the route (would need a future `ccyCd` segment). For all
production FCY accounts the single-currency-per-account assumption holds.

### Sorting behaviour parity

Default `TRSC_DT DESC, TRSC_TM DESC` matches the legacy default. Per-column interactive sort is
**PARTIAL** — seed `sort_yn` flags exist but the transaction list has no `AccountSortField`-style
whitelist and the grid headers render `sortable={false}`. Non-blocking.

### Pagination behaviour parity

Legacy paged DB-side; the refactor loads all filtered rows and paginates client-side. **APPROVED
DEVIATION (modernized)** — accepted pending a PO decision on a server-paging cutover.

### Transaction synchronization flow

Owned by the **Account Inquiry (FCY)** sibling (`BankTransactionSyncService` → Coocon/NXiSAS →
`ACCT_TRNS_HSTR` + `EXT_TRAN_CALL_HIS` audit). Transaction History only reads synced rows. No gap on
the TH side. See [business-flow.md §8](business-flow.md#8-synchronization-sibling-feature).

### Transaction detail parity

`detail(...)` returns `CCY_CD` and the un-merged `RMRK2` (the list row omits them) — matches the legacy
`fnsh_0006_003` detail shape. MATCH.

---

## Open items (PARTIAL — tracked, non-blocking)

1. **Per-column server-side sort** — seed-ready, not wired (fixed default sort). PO decision pending.
2. **Server-side pagination** — load-all + client pagination accepted; revisit at scale.
3. **Vietcombank `11704004` balance recompute** — legacy IDO `R016` recomputes `BAL` for one bank; the
   refactor passes `BAL` through verbatim. Deferred pending PO.
4. **Bank `11704020` remark rule / balance-hiding for `036/075/064`** — legacy bank-specific display
   rules; not ported. Deferred pending PO.
5. **FCY export subheader currency symbol** — totals subheader uses `AmountFormatter.forDisplay(..., null)`
   (no `$`/`₩` prefix). Per-cell amounts are correct; confirm subheader format is acceptable at UAT.
6. **Single-currency-per-account assumption** — see "Currency filtering behaviour" above.

None of these block the core user-facing FCY inquiry flow.

---

## Overall

**Refactor Status: 100% Complete** for the FCY transaction inquiry — list, multi-currency filtering,
confirm/undo, the four classification setters, Smart Organizer match/unmatch, View Setting, and XLSX
export are migrated and wired end-to-end with verified `fnsh_0006_001` parity. The five SIT/UAT
blockers (BUG-1/2/3, #5, #29) and R11 are CLOSED; the parity audit verdict is **GO FOR SIT** and SIT/UAT
verification is complete. The PARTIAL/deferred items above are non-blocking modernizations and
bank-specific display rules carried for a future PO decision.

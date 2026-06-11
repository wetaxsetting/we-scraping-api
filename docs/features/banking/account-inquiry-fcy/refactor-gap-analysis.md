# Account Inquiry (FCY) — Refactor Gap Analysis

> Final parity review: legacy `fnsh_0005_001` (USD account list + Update Balance) vs. the refactor.
> Pairs with the source-verified audit
> [audits/account-inquiry-fcy-parity-audit.md](../../../audits/account-inquiry-fcy-parity-audit.md) (2026-06-06, PO: Mr. Jung)
> and the agent-verified legacy investigation
> [docs/features/account-inquiry-fcy/refactor-gap-analysis.md](../../../../../docs/features/account-inquiry-fcy/refactor-gap-analysis.md).

## Summary verdict

```
Refactor Status: 100% Complete   (read path — list, currency filter, sort, pagination, per-CCY totals,
                                  update-balance/sync, last-update + status display, transaction
                                  drill-down, View Setting, Excel export)

READY FOR SIT (read path).  Update-Balance scraping carried as a known, accepted, scope-deferred item.
```

Both blocking divergences were dispositioned on **2026-06-06**:

```
FCY-A01  Status: Closed   Classification: PO Approved Deviation        (multi-currency FCY)
FCY-A02  Status: Closed   Classification: Implemented (legacy parity)  ("Last Update" source)
```

The refactor reimplements FCY as a prop-variant of the shared VND Account Inquiry screen
(`currency="FCY"`, `multiCurrencyMode`) — exactly as legacy reuses `FnshHelper.fnsh_0001_001_r001(…USD)`.

## Parity table

| Area | Legacy (`fnsh_0005_001`) | Refactor | Status |
|---|---|---|---|
| **Search** | bank / keyword(nickname) / currency(blank+USD), event-driven | bank / keyword(formatted nickname) / currency dropdown, event-driven | **MATCH** (keyword now matches the displayed nickname) |
| **Account Grid** | View-Setting-driven; BANK · NICKNAME · BALANCE · AVAILABLE · (VAS) · LAST_UPDATE | same registry under `banking-account-inquiry-fcy` | **MATCH** |
| **Sorting** | server-side `SortHelper`, default `PRN_SEQ ASC` | `AccountSortField` whitelist, default `PRN_SEQ ASC, BANK_CD, ACCT_NO` | **MATCH** |
| **Pagination** | `PAGE_NO`/`PAGE_PER_CNT` | server-side `Pageable`, clamp 100 | **MATCH** |
| **Update Balance** | client-side NXiSAS engine → Coocon | server-side `RestClient` → Coocon (LOGIN/TRANSACTION/BALANCE + OTP/QNA/CAPTCHA resume) | **APPROVED DEVIATION** (FCY-A03 topology; not validated vs live banks — R&D scope) |
| **Account Sync** | scrape → `fnsh_0001_001_c001` save + auto-match | `BankTransactionSyncService` → `ACCT_MSTR`/`ACCT_TRNS_HSTR` + audit | **MATCH** (structural) |
| **Transaction Sync** | NXiSAS TRANSACTION job → `ACCT_TRNS_HSTR` | `processOneAccount` INSERT dedup on `BANK_TRNS_FULL_ID` | **MATCH** |
| **Last Update Display** | `ACCT_HIS_LST_INQ_DT` (online) / `ACCT_BAL_LST_INQ_DTM` (FD-only) | `resolveLastUpdate` — same conditional | **MATCH** (FCY-A02 CLOSED) |
| **Status Display** | inquiry-status codes drive per-row msg/red | `acct_bal_inq_stts` drives row error; >72h stale-red not reproduced | **PARTIAL** (FCY-A05) |
| **Currency Filtering** | blank(all) + USD only | All-Currency + concrete CCY dropdown | **APPROVED DEVIATION** (FCY-A01) |
| **Multi-Currency Support** | USD-only | all non-VND (`ccy_cd <> 'VND'`) + per-CCY tiles | **APPROVED DEVIATION** (FCY-A01) |
| **Detail View** | history layer popup (max 10 rows) + "View Details" | `BalancePopover` (`from=to=date`) + deep-link | **MATCH** (peek + deep-link; FCY-G09 confirm single-date) |
| **Permissions** | menu auth + per-account `ACCT_AUTH_INFO`; admin-only scraping UI | `@menuAuthz` → `banking-fcy-update-inquire` + `ACCT_AUTH_INFO`; no frontend role gate | **MATCH** (backend); **PARTIAL** frontend (FCY-A06) |
| **Error Handling** | JEX message dialog | `{status}` envelope + sync dialog status | **MATCH** |

## Special verification areas

### FCY-A01 — Multi-currency support
```
FCY-A01   Status: Closed   Classification: PO Approved Deviation
```
Legacy `CCY_CD='USD'` (`FnshHelper.java:417-418`, types `:425-430`) vs. refactor `ccy_cd<>'VND'` +
`FCY_TYPE_CODES` (`AccountService.java:450-453`) + in-page currency dropdown + per-CCY tiles. PO
(Mr. Jung) approved the modernization on 2026-06-06; legacy USD-only scope is intentionally retired.
**No code change.** The two screens return different row sets for any tenant holding non-USD foreign
accounts — that is the intended behaviour.

### FCY-A02 — Last Update source
```
FCY-A02   Status: Closed   Classification: Implemented (legacy parity)
```
Refactor now sources `ACCT_HIS_LST_INQ_DT` (online) with the FD-only `ACCT_BAL_LST_INQ_DTM` fallback via
`AccountService.resolveLastUpdate(...) + toRow`; `AccountSortField.LAST_UPDATE → acctHisLstInqDt`; export
inherits via the DTO; wire field name `acct_bal_upd_dtm` retained (frontend/grid contract unchanged).
Unit-tested + compiles green. Shared with VND (same `toRow`).

### Multi-currency implementation
Per-CCY totals via `aggregateByCcy` (same WHERE chain as the list), one `CurrencyTotal` per CCY, one
`SummaryTile` per entry on the frontend. Amounts formatted per the row's own CCY.

### Sync status behaviour
`processOneAccount` writes balance + `ACCT_HIS_LST_INQ_DT`; `markScrapeFailure` (REQUIRES_NEW) preserves
failure state. Challenge/resume via session registry. R&D: not validated vs. live banks (FCY-A03).

### Account balance refresh logic + historical-balance (OQ-FCY-2)
Refactor renders the live `ACCT_MSTR.BAL`/`REAL_AMT` snapshot; refreshed on sync.

> **Correction (2026-06-06): the earlier "FCY-G02 FALSE POSITIVE" was WRONG and is retracted.**
> Direct source re-verification proved legacy `fnsh_0005_001` historical/as-of-date balance is **real
> and functional**: the FE always sends `START_DATE` (`fnsh_0005_001_api_osmc.ts:606-615`),
> `FnshHelper.fnsh_0001_001_r001_readList:246` binds it into the IDO, and
> `IDO.ACCT_MSTR_R004.xml:90-217` reconstructs as-of-date `BAL`/`REAL_AMT` from `ACCT_TRNS_HSTR`,
> **including** the Vietcombank `11704004` special case (`:96-98,140-142,182-184`). The prior audit only
> inspected the WHERE-clause builder and missed that `:START_DATE` is consumed in the SELECT projection.
>
> The refactor intentionally ships **current-snapshot balances only** — no `START_DATE` param
> (`AccountController.java:94-101`; `AccountFilter` `currency/bankCd/keyword` only, javadoc "date range
> — gated on PO confirmation"), no `ACCT_TRNS_HSTR` reconstruction, no `11704004` handling.
>
> **OQ-FCY-2 → CLOSED — PO Approved Deviation (Option B, 2026-06-06).** Snapshot-only balances accepted;
> full legacy parity (Option A) deferred to a post-SIT MR, gated on realistic seeded transaction history
> (current R&D seed ≤30 `ACCT_TRNS_HSTR` rows cannot validate as-of-date math). The date control is
> **retained** because it scopes the per-account balance/transaction popup
> (`AccountInquiryPage.tsx:1301`); it does **not** re-base list balances. See
> [audits/account-inquiry-fcy-parity-audit.md](../../../audits/account-inquiry-fcy-parity-audit.md) §3.

## Verified-present (NOT gaps)

| Capability | Evidence |
|---|---|
| Multi-tenant `biz_key` scoping | `AccountService.java:176` on every query |
| Per-currency + grand totals over full filtered set | `AcctMstrRepository.aggregateByCcy:235-272` |
| Non-admin permitted-account filter | `AcctMstrRepository:197-204` (`ACCT_AUTH_INFO`) |
| FCY-specific menu permission + param-aware guard | `MenuAuthorizationGuard:141-145`; seed V243 |
| FCY Excel export (per-CCY subheader) | `AccountInquiryFcyExportProvider:44-71` |
| FCY View-Setting screenId + seeds | `viewSettingScreens.ts` (`ACCOUNT_INQUIRY_FCY`); V43/V47 |
| Default sort parity | `AccountService.java:124-125` |

## Open / deferred items (do not block SIT)

| ID | Title | Severity | Status |
|---|---|---|---|
| FCY-A03 | Server-side Coocon scraping not validated vs. live banks (R&D scope) | MED | OPEN (scope-deferred) — OQ-FCY-3 |
| FCY-A04 | Haengsung (`PrdtType.H`) maturity/balance exclusion on totals not ported | MED | OPEN |
| FCY-A05 | ">72h stale = red" Last-Update indicator not reproduced | MED | OPEN |
| FCY-A06 | No frontend role gate on Update / Excel (backend enforces) | MED | OPEN |
| FCY-A07 | `BUSI_STTS='C'` (closed business) hides register button — no refactor equivalent | LOW | OPEN |
| FCY-A08 | Date-change refetch: legacy refetches, refactor resets page only | LOW | OPEN |
| FCY-A10 | "Register account when list empty" affordance | LOW | OPEN |
| OQ-FCY-2 (was FCY-G02) | As-of-date historical balance (legacy real + functional; refactor snapshot-only) | HIGH (effort) | **CLOSED — PO Approved Deviation (Option B, 2026-06-06)** — prior "FALSE POSITIVE" retracted |

## Open questions for PO (Mr. Jung)

1. ~~**OQ-FCY-1 (FCY-A01):** FCY = USD-only or multi-currency non-VND?~~ **RESOLVED 2026-06-06 —
   multi-currency non-VND (PO Approved Deviation).**
2. ~~**OQ-FCY-2 (was FCY-G02):** As-of-date historical balance in scope?~~ **RESOLVED 2026-06-06 — Option B
   (PO Approved Deviation): snapshot-only balances accepted; Option A (full legacy parity incl. Vietcombank
   `11704004`) deferred to a post-SIT MR gated on realistic transaction history. Date control retained for
   the per-account popup only (not a list re-base). Prior "FALSE POSITIVE" classification retracted.**
   PO sign-off: Mr. Jung (pending formal confirmation).
3. **OQ-FCY-3 (FCY-A03):** Expected Update-Balance behaviour in the local R&D env (mock vs. disabled) for
   the test plan.

## NOT VERIFIED caveats

- `V43`/`V47` FCY column-seed row-level definitions assumed to match the VND `FALLBACK_COLUMNS`.
- `TransactionSyncDialog.tsx` / `SyncHistoryDialog.tsx` internals verified at entry-point wiring only.
- Legacy Vietcombank `11704004` date-aware-balance special case is **source-confirmed present** in
  `ACCT_MSTR_R004.xml:96-98,140-142,182-184` (no longer "inferred"); it is intentionally **absent** in the
  refactor under the OQ-FCY-2 Option-B deviation.

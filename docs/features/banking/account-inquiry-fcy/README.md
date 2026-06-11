# Banking > Account Inquiry (FCY) — Refactor Backend Feature Hub

**Module:** Banking → Current & Savings (FCY)
**Menu:** Account Inquiry (FCY)
**Refactor screen ID / View Setting `WEB_SVC_ID`:** `banking-account-inquiry-fcy`
**Menu permission:** `banking-fcy-update-inquire`
**REST base:** `GET /api/v1/banking/accounts?currency=FCY` (+ export, sync, transactions)
**JEX counterpart:** `fnsh_0005_001` — foreign-currency account list (USD), reuses the VND helper with `CcyCd.USD`
**Frontend counterpart:** [wecms-web/docs/features/banking/account-inquiry-fcy/](../../../../../wecms-web/docs/features/banking/account-inquiry-fcy/README.md)

> **Status — Refactor complete (full-stack).** Foreign-currency account balance inquiry, account
> synchronization, transaction synchronization, update-balance, last-update display, account-status
> display, account-detail (transaction drill-down), per-currency totals, View Setting and XLSX export
> are migrated and wired end-to-end. Legacy parity verified; the two weighted divergences (FCY-A01
> currency scope, FCY-A02 "Last Update" source) were dispositioned on **2026-06-06** — see
> [refactor-gap-analysis.md](refactor-gap-analysis.md). SIT/UAT verification completed (read path).
> This folder is the **primary documentation home** for the refactored FCY feature.

JEX is **read-only functional reference**. This folder documents the refactored Spring Boot
implementation; the JEX behaviour at `fnsh_0005_001` is the functional source of truth.

---

## What this feature does

Lists every **non-VND (foreign-currency)** bank account for the current tenant with current balance,
available balance, per-account inquiry status and last-update timestamp. From the same screen the user
can:

- **Filter** by bank, keyword (account nickname) and **currency** (an in-page currency dropdown — "All
  Currency" = every non-VND CCY, or narrow to a concrete `USD` / `KRW` / `EUR` / `JPY` / …).
- **Sort** by any whitelisted column (server-side ORDER BY).
- **Paginate** server-side (10 / 20 / 50 / 100).
- **Update balance / sync** the selected accounts in real time against the bank (server-side Coocon
  smart-gateway), driving balance, available balance and the last-update / status columns.
- **Drill into transactions** for an account (balance-click popup + deep-link to per-account FCY
  Transaction History).
- **See per-currency totals** — one summary tile per CCY (count + balance + available balance).
- **Change visible columns** via the global View Setting popover.
- **Export** the filtered+sorted rows to XLSX (shared `common/export` pipeline; per-CCY subheader).

The single most important structural fact: the refactor **collapses VND + FCY onto one endpoint + one
shared frontend component** — exactly mirroring how legacy `fnsh_0005_001` reuses the VND helper
`FnshHelper.fnsh_0001_001_r001(..., CcyCd.USD)`. FCY is the same code path with `currency="FCY"` and
`multiCurrencyMode` passed.

### Approved modernization — FCY-A01 (multi-currency)

```
FCY-A01
Status: CLOSED
Classification: PO Approved Deviation (2026-06-06, PO: Mr. Jung)
```

Legacy FCY is hard-locked to `CCY_CD = 'USD'`. The refactor returns **all non-VND currencies**
(`ccy_cd <> 'VND'`) and exposes an in-page currency dropdown + per-CCY summary tiles. The PO confirmed
multi-currency FCY is the intended modernization; the legacy USD-only restriction is intentionally
retired. **Not a defect.** Details: [refactor-gap-analysis.md](refactor-gap-analysis.md) · audit
[../../../audits/account-inquiry-fcy-parity-audit.md](../../../audits/account-inquiry-fcy-parity-audit.md).

---

## Document map (this folder)

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | Scope, filters, currency dropdown, columns, pagination, sorting, totals, permissions, export — the *what*. |
| [business-flow.md](business-flow.md) | FCY retrieval, balance-inquiry, account-sync, transaction-sync, currency filtering, last-update + status calculation, bank-integration flows — the *how*. |
| [database-mapping.md](database-mapping.md) | Tables + legacy-column → entity-field → DTO-field → UI-column mapping; status-code mapping; FCY type-code set. |
| [interface-specification.md](interface-specification.md) | Every REST endpoint, request/response shapes, DTO field lists, currency resolution table, error codes. |
| [source-code-index.md](source-code-index.md) | Legacy JEX ↔ refactor backend ↔ refactor frontend file index. |
| [migration-guide.md](migration-guide.md) | JEX WSVC/IDO/`_act.jsp` → Spring controller/service/repository mapping. |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (search, multi-currency, update balance, sync, sorting, pagination, permissions, errors). |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / APPROVED DEVIATION / PARTIAL / MISSING) + special verification areas + FCY-A01/A02 closure. |

## Companion references (preserved — cross-linked, not duplicated)

| Doc | Location |
|---|---|
| **Parity audit (2026-06-06)** | [audits/account-inquiry-fcy-parity-audit.md](../../../audits/account-inquiry-fcy-parity-audit.md) |
| Backend API design (original) | [references/api-spec/banking/account-inquiry-fcy-api.md](../../../references/api-spec/banking/account-inquiry-fcy-api.md) |
| DB design (original) | [references/database/banking/account-inquiry-fcy-db.md](../../../references/database/banking/account-inquiry-fcy-db.md) |
| Older single-file feature spec | [features/banking/current-savings-fcy/account-inquiry.md](../current-savings-fcy/account-inquiry.md) |
| **Legacy investigation package (agent-verified, `fnsh_0005_001`)** | [docs/features/account-inquiry-fcy/](../../../../../docs/features/account-inquiry-fcy/README.md) |
| VND twin (shared code path) | [account-inquiry-vnd/](../account-inquiry-vnd/README.md) |
| Banking ERD | [references/database/erd/banking-erd.md](../../../references/database/erd/banking-erd.md) |
| NXiSAS / Coocon gateway spike | [references/audits/nxisas-vcb-spike.md](../../../references/audits/nxisas-vcb-spike.md) |

> **Two FCY screens — do not confuse them.** Account Inquiry (FCY) = legacy `fnsh_0005_001`
> (foreign-currency account list + Update Balance, this folder). The FCY **Transaction History**
> screen is a *different* legacy screen (`fnsh_0002_010`, "입출식예금 입출내역조회") documented under
> `current-savings-fcy/transaction-history.md`. The "View Details" link on this screen deep-links to
> the FCY Transaction History (`fnsh_0006_001` in legacy).

---

## Key backend artifacts

| Layer | Class | Path |
|---|---|---|
| Controller | `AccountController` | `src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java` |
| Service (list/filter/totals) | `AccountService` | `src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java` |
| Repository | `AcctMstrRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java` |
| Entity | `AcctMstr` (`@IdClass AcctMstrId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java` |
| Sort whitelist | `AccountSortField` | `src/main/java/com/kosign/wecms/domain/banking/service/AccountSortField.java` |
| Amount formatter | `AmountFormatter` | `src/main/java/com/kosign/wecms/domain/banking/service/AmountFormatter.java` |
| Filter carrier | `AccountFilter` | `src/main/java/com/kosign/wecms/domain/banking/service/AccountFilter.java` |
| FCY Excel provider | `AccountInquiryFcyExportProvider` | `src/main/java/com/kosign/wecms/domain/banking/export/AccountInquiryFcyExportProvider.java` |
| Sync orchestrator | `BankTransactionSyncService` | `src/main/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncService.java` |
| Coocon gateway client | `NxisasGatewayClient` | `src/main/java/com/kosign/wecms/domain/banking/nxisas/NxisasGatewayClient.java` |
| Per-user authority entity | `AcctAuthInfo` | `src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfo.java` |

---

## Migration status

```
Refactor Status: 100% Complete  (read path — list / filter / currency / sort / pagination /
                                 totals / update-balance + sync / last-update + status display /
                                 transaction drill-down / View Setting / Excel export)
```

```
FCY-A01  Status: Closed   Classification: PO Approved Deviation     (multi-currency FCY)
FCY-A02  Status: Closed   Classification: Implemented (legacy parity) ("Last Update" source)
```

Carried (do not block SIT, tracked for the next stabilisation pass): **FCY-A03** (server-side scraping
not validated against live banks — explicit R&D-scope deferral), **FCY-A04** (Haengsung maturity totals
filter), **FCY-A05** (>72h stale-red indicator), **FCY-A06/A07** (frontend role / closed-business
gating — backend already enforces auth). See [refactor-gap-analysis.md](refactor-gap-analysis.md) for
the line-by-line table. **OQ-FCY-2** (as-of-date historical balance) is **CLOSED — PO Approved Deviation
(Option B, 2026-06-06):** legacy reconstructs as-of-date balances from `ACCT_TRNS_HSTR` (incl. Vietcombank
`11704004`), the refactor ships snapshot-only, and full legacy parity (Option A) is deferred to a post-SIT
MR gated on realistic transaction history. (The earlier "FALSE POSITIVE" classification was wrong and has
been retracted across the gap-analysis and parity audit.)

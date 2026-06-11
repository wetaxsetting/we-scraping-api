# Banking > Transaction History (FCY) — Refactor Backend Feature Hub

**Module:** Banking → Current & Savings (FCY)
**Menu:** Transaction History (FCY)
**Refactor screen ID / View Setting `WEB_SVC_ID`:** `banking-transaction-history` (one id shared by VND + FCY)
**Menu permission:** `banking-fcy-inquire-transactions` (FCY-specific; the endpoints `canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')`)
**Feature-folder slug (matches JEX + frontend):** `transaction-history-fcy`
**REST base:** `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` (+ summary, detail, confirm/undo, classification PATCH/DELETE, export) — `acctSeqNo` = `{bankCd}_{acctNo}`
**JEX counterpart:** `fnsh_0006_001` (VND twin `fnsh_0002_001`) — [docs/jex/features/transaction-history-fcy/](../../../../../docs/jex/features/transaction-history-fcy/README.md)
**Frontend counterpart:** [wecms-web/docs/features/banking/transaction-history-fcy/](../../../../../wecms-web/docs/features/banking/transaction-history-fcy/README.md)

> **Status — Refactor complete (full-stack).** Per-account foreign-currency transaction inquiry
> (date-range search, type / confirmation / keyword filtering), transaction-detail read, the
> confirm / undo workflow, the four classification setters (reporting account · purpose · VAS
> account · contact), Smart Organizer match/unmatch, client-side pagination, and XLSX export are
> migrated and wired end-to-end. **Multi-currency FCY support** (USD/KRW/JPY/EUR/… — every non-VND
> currency, scoped per account) is implemented and **PO-approved** (FCY-T01, see below). Legacy
> parity verified against `fnsh_0006_001`; the five SIT/UAT blockers (BUG-1/2/3, #5 currency filter,
> #29 per-account authorization) and the R11 Smart Organizer scope blocker are **all CLOSED**.
> SIT/UAT verification completed — final verdict **GO FOR SIT**. See
> [refactor-gap-analysis.md](refactor-gap-analysis.md) and the parity audit
> [../../../audits/transaction-history-fcy-parity-audit.md](../../../audits/transaction-history-fcy-parity-audit.md).
>
> This folder is the **primary documentation home** for the refactored FCY feature. The older
> single-file spec under [current-savings-fcy/transaction-history.md](../current-savings-fcy/transaction-history.md)
> predates this folder and is **preserved + cross-linked** but superseded by this folder.

JEX is **read-only functional reference**. This folder documents the refactored Spring Boot
implementation; the JEX behaviour at `fnsh_0006_001` is the functional source of truth.

> **Two FCY screens — do not confuse them.** **Account Inquiry (FCY)** = legacy `fnsh_0005_001`
> (foreign-currency account list + Update Balance) — documented under
> [account-inquiry-fcy/](../account-inquiry-fcy/README.md). **Transaction History (FCY)** = legacy
> `fnsh_0006_001` (per-account FCY transaction ledger, this folder). The "View Details" / balance
> drill-down on the Account Inquiry (FCY) screen deep-links into this screen.

---

## What this feature does

Lists the credit/debit transactions of a single **foreign-currency** bank account for the current
tenant over a date range, reached by drilling in from Account Inquiry (FCY) (or the Transaction
History sidebar's per-account FCY list — WOORI, BIDV, SHINHAN, … accounts). On top of the plain
transaction list it adds the same **operational classification / confirmation workflow** as the VND
twin — each line can be confirmed/unconfirmed and tagged with a Reporting (chart-of-accounts) code,
a Purpose, a VAS account, and a Contact (counterparty). From the same screen the user can:

- **Filter** by date range (`from`/`to`), transaction type (deposit / withdraw / all), confirmation
  status (`cnfm_yn` = Y / N / all), and a free-text keyword (matched on remark / remark2 / bank ref /
  transaction id / amount / contact name / purpose code / VAS code).
- **View** deposit / withdrawal / balance amounts (in the account's foreign currency), remarks,
  confirmation metadata, match metadata, and per-line classification on the grid; read a single
  transaction's full detail (which additionally returns `CCY_CD` and the un-merged `RMRK2`).
- **Confirm / undo-confirm** a transaction (writes/flips `ACCT_TRNS_RCV_PAY_INFM.CNFM_YN`).
- **Classify** a transaction — assign/clear Reporting Account, Purpose, VAS Account, Contact.
- **Match / unmatch** a transaction to a payment file via Smart Organizer.
- **Paginate** client-side (10 / 20 / 50 / 100).
- **Change visible columns** via the global View Setting popover (`banking-transaction-history`).
- **Export** the filtered rows to XLSX (shared `common/export` pipeline).

**FCY vs VND is not a code fork.** VND and FCY Transaction History share **one** controller, **one**
service, **one** repository pair, and **one** View Setting screenId. The endpoint is
currency-agnostic — `{acctSeqNo}` resolves the account and its currency, and the service derives the
currency filter from `ACCT_MSTR.CCY_CD` (so an FCY account returns only its own-currency rows). See
[business-flow.md §1](business-flow.md#1-retrieval-flow-list) and the FCY-T01 note below.

**Transaction synchronization is NOT owned by this feature.** Pulling fresh transactions from the
bank (NXiSAS / Coocon smart-gateway) is the sibling **Account Inquiry (FCY)** concern
(`BankTransactionSyncService`); Transaction History reads the already-synced rows from
`ACCT_TRNS_HSTR`. See [business-flow.md §8](business-flow.md#8-synchronization-sibling-feature) for
the boundary.

### Approved modernization — FCY-T01 (multi-currency FCY)

```
FCY-T01
Status: CLOSED
Classification: PO Approved Deviation (legacy-parity-aligned)
```

Legacy `fnsh_0006_001` takes `CCY_CD` from the request input (`fnsh_0006_001_r001_act.jsp:130` →
`CcyCd.fromValue(input.getString("CCY_CD"))`), so it already serves USD **and any other foreign
currency** the tenant holds — it is NOT hard-locked to USD like the FCY manual-entry popup
(`fnsh_0006_002`). The refactor mirrors this: the FCY account's own `ACCT_MSTR.CCY_CD` drives the
in-memory currency filter in `TransactionService.fetchRows(...)`, so each FCY account renders its own
currency's rows (USD/KRW/JPY/EUR/THB/…). Multi-currency FCY is therefore the intended behaviour, not
a defect. (This mirrors Account Inquiry's [FCY-A01](../account-inquiry-fcy/README.md#approved-modernization--fcy-a01-multi-currency)
multi-currency modernization.) Details: [refactor-gap-analysis.md](refactor-gap-analysis.md) · audit
[../../../audits/transaction-history-fcy-parity-audit.md](../../../audits/transaction-history-fcy-parity-audit.md).

---

## Document map (this folder)

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | Scope, filters, currency derivation, grid columns, pagination, sorting, totals, status mapping, permissions, export — the *what*. |
| [business-flow.md](business-flow.md) | Retrieval flow, in-memory filter handling, currency filter, amount/balance projection, confirm/classify flows, export generation, and the sync boundary — the *how*. |
| [database-mapping.md](database-mapping.md) | Tables + legacy-column → entity-field → DTO-field → UI-column mapping; the `CCY_CD` currency dimension; confirmation/classification columns. |
| [interface-specification.md](interface-specification.md) | Every REST endpoint, request/response shapes, DTO field lists, wire-casing note, error codes. |
| [source-code-index.md](source-code-index.md) | Legacy JEX (`fnsh_0006_001`) ↔ refactor backend ↔ refactor frontend file index. |
| [migration-guide.md](migration-guide.md) | JEX WSVC / IDO / `_act.jsp` → Spring controller/service/repository mapping; FCY discriminators. |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (search, multi-currency, type/date/keyword filters, confirm, classify, sort, pagination, export, permissions, errors). |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / APPROVED DEVIATION / PARTIAL / MISSING) + special verification areas (multi-currency, sort, pagination, sync, detail) + FCY-T01 closure. |

## Companion references (preserved — cross-linked, not duplicated)

| Doc | Location |
|---|---|
| **Parity audit (2026-06-06)** | [audits/transaction-history-fcy-parity-audit.md](../../../audits/transaction-history-fcy-parity-audit.md) |
| **SIT-readiness fix verification (2026-06-06)** | [audits/transaction-history-fcy-fix-verification.md](../../../audits/transaction-history-fcy-fix-verification.md) |
| Smart Organizer R11 account-scope verification | [audits/smart-organizer-r11-verification.md](../../../audits/smart-organizer-r11-verification.md) |
| VND twin (shared code path) | [transaction-history-vnd/README.md](../transaction-history-vnd/README.md) |
| Account Inquiry (FCY) — upstream entry screen + sync owner | [account-inquiry-fcy/README.md](../account-inquiry-fcy/README.md) |
| Older single-file feature spec (backend) | [features/banking/current-savings-fcy/transaction-history.md](../current-savings-fcy/transaction-history.md) |
| Sprint plan (residual = TH frontend wiring, VND + FCY) | [planning/phase-1/account-inquiry-and-transaction-history-plan.md](../../../planning/phase-1/account-inquiry-and-transaction-history-plan.md) |
| JEX → Spring service mapping | [references/migration/service-mapping.md](../../../references/migration/service-mapping.md) |
| Banking ERD | [references/database/erd/banking-erd.md](../../../references/database/erd/banking-erd.md) |
| Account / transaction DB design | [references/database/banking/account-inquiry-vnd-db.md](../../../references/database/banking/account-inquiry-vnd-db.md) |
| JEX functional spec (source of truth, `fnsh_0006_001`) | [docs/jex/features/transaction-history-fcy/](../../../../../docs/jex/features/transaction-history-fcy/README.md) |

---

## Key backend artifacts

| Layer | Class | Path |
|---|---|---|
| Controller | `AccountController` | `src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java` |
| Service | `TransactionService` | `src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java` |
| Per-account authz guard | `AccountAccessGuard` | `src/main/java/com/kosign/wecms/domain/banking/service/AccountAccessGuard.java` |
| Repository (transactions) | `AcctTrnsHstrRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctTrnsHstrRepository.java` |
| Repository (receipt/pay, confirm + classify) | `AcctTrnsRcvPayInfmRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctTrnsRcvPayInfmRepository.java` |
| Repository (currency recovery) | `AcctMstrRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java` |
| Repository (match metadata) | `BusiMatchLogRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/BusiMatchLogRepository.java` |
| Entity (transaction) | `AcctTrnsHstr` (`@IdClass AcctTrnsHstrId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctTrnsHstr.java` |
| Entity (receipt/pay) | `AcctTrnsRcvPayInfm` (`@IdClass AcctTrnsRcvPayInfmId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctTrnsRcvPayInfm.java` |
| Entity (account master — currency source) | `AcctMstr` (`@IdClass AcctMstrId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java` |
| Amount formatter | `AmountFormatter` | `src/main/java/com/kosign/wecms/domain/banking/service/AmountFormatter.java` |
| Inout enum | `InoutDv` | `src/main/java/com/kosign/wecms/common/enums/InoutDv.java` |
| Excel provider (shared VND+FCY) | `TransactionHistoryExportProvider` (`screenId = banking-transaction-history`) | `src/main/java/com/kosign/wecms/domain/banking/export/TransactionHistoryExportProvider.java` |
| View Setting seed | V155 → V169 / V171 / V167 / V181 (shared with VND) | `src/main/resources/db/migration/V155__transaction_history_view_setting_seed.sql` |

DTOs (`src/main/java/com/kosign/wecms/domain/banking/dto/`): `TransactionListResponse`, `TransactionRow`,
`TransactionDetailResponse`, `TransactionSummaryResponse`, and the request records
`TransactionConfirmRequest`, `TransactionReportingAccountRequest`, `TransactionPurposeRequest`,
`TransactionVasAccountRequest`, `TransactionContactRequest`.

---

## FCY vs VND — shared, not forked

VND and FCY Transaction History share **one** controller (`AccountController`), **one** service
(`TransactionService`), **one** repository pair (`AcctTrnsHstrRepository` + `AcctTrnsRcvPayInfmRepository`),
and **one** View Setting screenId (`banking-transaction-history`). The endpoint is currency-agnostic:
`{acctSeqNo}` (composite `BANK_CD_ACCT_NO`) resolves the account and its currency. There is **no
`if (FCY)` branch** in `TransactionService` — the only divergence is the value of `r.ccyCd` (read from
`ACCT_MSTR.CCY_CD`) that flows into the in-memory currency filter, plus the frontend `scope="FCY"`
prop, the FCY route folder, and the FCY menu slug `banking-fcy-inquire-transactions`. The FCY twin is
JEX `fnsh_0006_001`; see [migration-guide.md](migration-guide.md).

---

## Migration status

```
Refactor Status: 100% Complete  (full-stack — list / filter / multi-currency FCY / confirm-undo /
                                 classification (reporting account · purpose · VAS · contact) /
                                 Smart Organizer match-unmatch / View Setting / XLSX export)
```

```
FCY-T01  Status: Closed   Classification: PO Approved Deviation (multi-currency FCY, legacy-parity)
```

Date-range search, type / confirmation / keyword filtering, transaction detail, confirm/undo, the
four classification setters, Smart Organizer matching, pagination, totals, View Setting, and XLSX
export are migrated and wired end-to-end with verified `fnsh_0006_001` parity. The five SIT/UAT
blockers (BUG-1 confirmed-by, BUG-2 transaction-id/file/match columns, BUG-3 keyword breadth, #5
currency filter, #29 per-account authorization) and R11 (Smart Organizer account scope) are **all
CLOSED** — see [refactor-gap-analysis.md](refactor-gap-analysis.md). Non-blocking PO decisions
(paging strategy, per-column sort, bank-specific Vietcombank balance recompute / bank `11704020`
remark rule) are carried in the gap analysis §"Open items".

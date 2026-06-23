# Banking > Transaction History (VND) — Refactor Backend Feature Hub

**Module:** Banking → Current & Savings (VND)
**Menu:** Transaction History (VND)
**Refactor screen ID / View Setting `WEB_SVC_ID`:** `banking-transaction-history` (one id shared by VND + FCY)
**Feature-folder slug (matches JEX + frontend):** `transaction-history-vnd`
**REST base:** `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` (+ summary, detail, confirm/undo, classification PATCH/DELETE, export)
**JEX counterpart:** `fnsh_0002_001` (FCY twin `fnsh_0006_001`) — [docs/jex/features/transaction-history-vnd/](../../../../../../docs/jex/features/transaction-history-vnd/README.md)
**Frontend counterpart:** [wecms-web/docs/features/banking/current-savings-vnd/transaction-history/](../../../../../../wecms-web/docs/features/banking/current-savings-vnd/transaction-history/README.md)

> **Status — Refactor complete (full-stack), with documented enhancement stubs.**
> Per-account transaction inquiry (date-range search, type / confirmation / keyword filtering),
> transaction-detail read, the confirm / undo workflow, the four classification setters
> (reporting account · purpose · VAS account · contact), client-side pagination, and XLSX export
> are migrated and wired end-to-end. Legacy parity verified against `fnsh_0002_001`; SIT/UAT
> completed. Two enhancement items remain **PARTIAL** and are tracked in
> [refactor-gap-analysis.md](refactor-gap-analysis.md): (1) five export columns
> (`TRSC_ID` / `FILE_NM` / `MTCH_DT` / `PURPOSE` / `CONTACT_NM`) emit empty strings pending a
> `TransactionRow` DTO extension; (2) per-column server-side sorting is seed-ready but not yet
> wired (the grid renders unsorted-by-default, most-recent-first). Neither blocks the core
> user-facing flow.
>
> This folder is the **primary documentation home** for the refactored feature. The older
> single-file specs under [current-savings-vnd/transaction-history.md](../transaction-history/legacy-spec.md)
> (backend) and its frontend twin predate this folder and are **preserved + cross-linked** — but
> note they carry a stale `~25% frontend` status line and a stale `banking-transaction-history-vnd`
> screenId; this folder supersedes both.

JEX is **read-only functional reference**. This folder documents the refactored Spring Boot
implementation; the JEX behaviour at `fnsh_0002_001` is the functional source of truth.

---

## What this feature does

Lists the credit/debit transactions of a single VND bank account for the current tenant over a
date range, reached by drilling in from Account Inquiry (VND) (or the Transaction History sidebar's
per-account list). On top of the plain transaction list it adds an **operational
classification / confirmation workflow** — each line can be confirmed/unconfirmed and tagged with a
Reporting (chart-of-accounts) code, a Purpose, a VAS account, and a Contact (counterparty). From the
same screen the user can:

- **Filter** by date range (`from`/`to`), transaction type (deposit / withdraw / all), confirmation
  status (`cnfm_yn` = Y / N / all), and a free-text keyword (matched on remark / bank reference).
- **View** deposit / withdrawal / balance amounts, remarks, confirmation metadata, and per-line
  classification on the grid; read a single transaction's full detail.
- **Confirm / undo-confirm** a transaction (writes/flips `ACCT_TRNS_RCV_PAY_INFM.CNFM_YN`).
- **Classify** a transaction — assign/clear Reporting Account, Purpose, VAS Account, Contact.
- **Paginate** client-side (10 / 20 / 50 / 100).
- **Change visible columns** via the global View Setting popover (`banking-transaction-history`).
- **Export** the filtered rows to XLSX (shared `common/export` pipeline).

**Transaction synchronization is NOT owned by this feature.** Pulling fresh transactions from the
bank (NXiSAS / Coocon smart-gateway) is the sibling **Account Inquiry (VND)** concern
(`BankTransactionSyncService`); Transaction History reads the already-synced rows from
`ACCT_TRNS_HSTR`. See [business-flow.md §Synchronization](business-flow.md#8-synchronization-sibling-feature)
for the boundary.

---

## Document map (this folder)

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | Scope, filters, grid columns, pagination, sorting, totals, status mapping, permissions, export — the *what*. |
| [business-flow.md](business-flow.md) | Retrieval flow, in-memory filter handling, amount/balance projection, confirm/classify flows, export generation, and the sync boundary — the *how*. |
| [database-mapping.md](database-mapping.md) | Tables + legacy-column → entity-field → DTO-field → UI-column mapping; confirmation/classification columns. |
| [interface-specification.md](interface-specification.md) | Every REST endpoint, request/response shapes, DTO field lists, error codes. |
| [source-code-index.md](source-code-index.md) | Legacy JEX ↔ refactor backend ↔ refactor frontend file index. |
| [migration-guide.md](migration-guide.md) | JEX WSVC / IDO / `_act.jsp` → Spring controller/service/repository mapping. |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (search, type/date/currency/keyword filters, confirm, classify, sort, pagination, export, permissions, errors). |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / PARTIAL / MISSING) + special verification areas (sort, pagination, sync, detail). |

## Companion references (preserved — cross-linked, not duplicated)

| Doc | Location |
|---|---|
| Older single-file feature spec (backend) | [features/banking/current-savings-vnd/transaction-history.md](../transaction-history/legacy-spec.md) |
| Account Inquiry (VND) — upstream entry screen + sync owner | [features/banking/current-savings-vnd/account-inquiry/README.md](../account-inquiry/README.md) |
| Smart Organizer (matching hand-off) | [features/banking/current-savings-vnd/smart-organizer.md](../../smart-organizer/README.md) |
| Sprint plan | planning/phase-1/account-inquiry-and-transaction-history-plan.md _(removed — historical `planning/` folder deleted by project decision)_ |
| Parity audit (preserved) | [audits/transaction-history-vnd-parity-audit.md](../../../../audits/transaction-history-vnd-parity-audit.md) |
| Banking ERD | [references/database/erd/banking-erd.md](../../../../references/database/erd/banking-erd.md) |
| Account / transaction DB design | [references/database/banking/account-inquiry-vnd-db.md](../../../../references/database/banking/account-inquiry-vnd-db.md) |
| JEX functional spec (source of truth) | [docs/jex/features/transaction-history-vnd/](../../../../../../docs/jex/features/transaction-history-vnd/README.md) |

---

## Key backend artifacts

| Layer | Class | Path |
|---|---|---|
| Controller | `AccountController` | `src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java` |
| Service | `TransactionService` | `src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java` |
| Repository (transactions) | `AcctTrnsHstrRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctTrnsHstrRepository.java` |
| Repository (receipt/pay, confirm + classify) | `AcctTrnsRcvPayInfmRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctTrnsRcvPayInfmRepository.java` |
| Entity (transaction) | `AcctTrnsHstr` (`@IdClass AcctTrnsHstrId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctTrnsHstr.java` |
| Entity (receipt/pay) | `AcctTrnsRcvPayInfm` (`@IdClass AcctTrnsRcvPayInfmId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctTrnsRcvPayInfm.java` |
| Amount formatter | `AmountFormatter` | `src/main/java/com/kosign/wecms/domain/banking/service/AmountFormatter.java` |
| Inout enum | `InoutDv` | `src/main/java/com/kosign/wecms/common/enums/InoutDv.java` |
| Excel provider | `TransactionHistoryExportProvider` | `src/main/java/com/kosign/wecms/domain/banking/export/TransactionHistoryExportProvider.java` |
| View Setting seed | V155 → V169 / V171 / V167 / V181 | `src/main/resources/db/migration/V155__transaction_history_view_setting_seed.sql` (+ realign/contact/unlock migrations) |

DTOs (`src/main/java/com/kosign/wecms/domain/banking/dto/`): `TransactionListResponse`, `TransactionRow`,
`TransactionDetailResponse`, `TransactionSummaryResponse`, and the request records
`TransactionConfirmRequest`, `TransactionReportingAccountRequest`, `TransactionPurposeRequest`,
`TransactionVasAccountRequest`, `TransactionContactRequest`.

---

## VND vs FCY — shared, not forked

VND and FCY Transaction History share **one** controller, **one** service, **one** repository pair,
and **one** View Setting screenId (`banking-transaction-history`). The endpoint is
currency-agnostic: `{acctSeqNo}` (composite `BANK_CD_ACCT_NO`) already resolves the account and its
currency, so no currency branch exists in the transaction path. Currency scoping happens upstream in
Account Inquiry's account list (`?currency=VND|FCY`). The FCY twin is JEX `fnsh_0006_001`; see
[docs/jex/features/transaction-history-vnd/README.md §1](../../../../../../docs/jex/features/transaction-history-vnd/README.md).

---

## Migration status

**Refactor Status: Core feature complete (full-stack); 2 enhancement items PARTIAL.** Date-range
search, type / confirmation / keyword filtering, transaction detail, confirm/undo, the four
classification setters, pagination, totals, View Setting, and XLSX export are migrated and wired
end-to-end with verified `fnsh_0002_001` parity. The two PARTIAL items (export column stubs for
`TRSC_ID`/`FILE_NM`/`MTCH_DT`/`PURPOSE`/`CONTACT_NM`; per-column server-side sort) are documented in
[refactor-gap-analysis.md](refactor-gap-analysis.md) and do not affect the core inquiry flow.

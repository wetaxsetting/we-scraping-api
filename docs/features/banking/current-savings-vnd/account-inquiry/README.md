# Banking > Account Inquiry (VND) — Refactor Backend Feature Hub

**Module:** Banking → Current & Savings (VND)
**Menu:** Account Inquiry (VND)
**Refactor screen ID / View Setting `WEB_SVC_ID`:** `banking-account-inquiry-vnd`
**REST base:** `GET /api/v1/banking/accounts` (+ export, sync, transactions)
**JEX counterpart:** `fnsh_0001_001` — [docs/jex/features/account-inquiry-vnd/](../../../../../../docs/jex/features/account-inquiry-vnd/README.md)
**Frontend counterpart:** [wecms-web/docs/features/banking/current-savings-vnd/account-inquiry/](../../../../../../wecms-web/docs/features/banking/current-savings-vnd/account-inquiry/README.md)

> **Status — Refactor complete (full-stack).** Account balance inquiry, account synchronization,
> transaction synchronization, update-balance, last-refresh display, account-status display and
> account-detail (transaction drill-down) are migrated and wired end-to-end. Legacy parity verified;
> SIT/UAT verification completed. This folder is the **primary documentation home** for the refactored
> feature; the older single-file spec under [current-savings-vnd/account-inquiry.md](../account-inquiry/legacy-spec.md)
> is preserved and cross-linked (it predates this folder and remains a valid quick reference).

JEX is **read-only functional reference**. This folder documents the refactored Spring Boot
implementation; the JEX behaviour at `fnsh_0001_001` is the functional source of truth.

---

## What this feature does

Lists every VND-denominated bank account for the current tenant with current balance, available
balance, per-account inquiry status and last-refresh timestamp. From the same screen the user can:

- **Filter** by bank, keyword (account nickname) and currency scope (VND-locked here).
- **Sort** by any whitelisted column (server-side ORDER BY).
- **Paginate** server-side (10 / 20 / 50 / 100).
- **Update balance / sync** the selected accounts in real time against the bank (NXiSAS / Coocon
  smart-gateway), driving balance, available balance and the last-update / status columns.
- **Drill into transactions** for an account (sibling Transaction History endpoint set).
- **Change visible columns** via the global View Setting popover.
- **Export** the filtered+sorted rows to XLSX (shared `common/export` pipeline).

---

## Document map (this folder)

| Doc | Covers |
|---|---|
| [functional-specification.md](functional-specification.md) | Scope, filters, columns, pagination, sorting, totals, permissions, export — the *what*. |
| [business-flow.md](business-flow.md) | Account-retrieval, balance-inquiry, account-sync, transaction-sync, last-update + status calculation, bank-integration flows — the *how*. |
| [database-mapping.md](database-mapping.md) | Tables + legacy-column → entity-field → DTO-field → UI-column mapping; status-code mapping. |
| [interface-specification.md](interface-specification.md) | Every REST endpoint, request/response shapes, DTO field lists, error codes. |
| [source-code-index.md](source-code-index.md) | Legacy JEX ↔ refactor backend ↔ refactor frontend file index. |
| [migration-guide.md](migration-guide.md) | JEX WSVC/IDO/`_act.jsp` → Spring controller/service/repository mapping. |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (search, update balance, sync, sorting, pagination, permissions, errors). |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / PARTIAL / MISSING) + special verification areas. |

## Companion references (preserved — cross-linked, not duplicated)

| Doc | Location |
|---|---|
| Backend API design (original) | [references/api-spec/banking/account-inquiry-vnd-api.md](../../../../references/api-spec/banking/account-inquiry-vnd-api.md) |
| DB design (original) | [references/database/banking/account-inquiry-vnd-db.md](../../../../references/database/banking/account-inquiry-vnd-db.md) |
| Sprint plan | planning/phase-1/account-inquiry-and-transaction-history-plan.md _(removed — historical `planning/` folder deleted by project decision)_ |
| Service mapping (cross-feature) | [references/migration/service-mapping.md](../../../../references/migration/service-mapping.md) |
| Older single-file feature spec | [features/banking/current-savings-vnd/account-inquiry.md](../account-inquiry/legacy-spec.md) |
| Banking ERD | [references/database/erd/banking-erd.md](../../../../references/database/erd/banking-erd.md) |
| NXiSAS / Coocon gateway spike | [audits/nxisas-vcb-spike.md](../../../../audits/historical/nxisas-vcb-spike.md) |
| JEX functional spec (source of truth) | [docs/jex/features/account-inquiry-vnd/](../../../../../../docs/jex/features/account-inquiry-vnd/README.md) |

---

## Key backend artifacts

| Layer | Class | Path |
|---|---|---|
| Controller | `AccountController` | `src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java` |
| Service (list/detail) | `AccountService` | `src/main/java/com/kosign/wecms/domain/banking/service/AccountService.java` |
| Repository | `AcctMstrRepository` | `src/main/java/com/kosign/wecms/domain/banking/repository/AcctMstrRepository.java` |
| Entity | `AcctMstr` (`@IdClass AcctMstrId`) | `src/main/java/com/kosign/wecms/domain/banking/entity/AcctMstr.java` |
| Sort whitelist | `AccountSortField` | `src/main/java/com/kosign/wecms/domain/banking/service/AccountSortField.java` |
| Amount formatter | `AmountFormatter` | `src/main/java/com/kosign/wecms/domain/banking/service/AmountFormatter.java` |
| Bank-name / acct-type resolver | `BankCdResolver` | `src/main/java/com/kosign/wecms/domain/banking/service/BankCdResolver.java` |
| Excel provider | `AccountInquiryVndExportProvider` | `src/main/java/com/kosign/wecms/domain/banking/export/` |
| Sync orchestrator | `BankTransactionSyncService` | `src/main/java/com/kosign/wecms/domain/banking/nxisas/BankTransactionSyncService.java` |
| Sync-history read | `SyncHistoryController` | `src/main/java/com/kosign/wecms/domain/banking/synchistory/controller/SyncHistoryController.java` |
| Per-user authority entity | `AcctAuthInfo` | `src/main/java/com/kosign/wecms/domain/setting/bankaccount/entity/AcctAuthInfo.java` |

---

## Migration status

**Refactor Status: 100% Complete** for the in-scope Account Inquiry (VND) surface (list, filter, sort,
pagination, update-balance/sync, last-update + status display, transaction drill-down, View Setting,
Excel export). See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the line-by-line parity
table and the small set of deferred-by-PO items (FCY client server-paging migration, Print port, a
handful of legacy REC fields held pending PO triage) — none of which block the VND feature.

# Transaction History (FCY) — Migration Guide (JEX → Spring)

How legacy `fnsh_0006_001` maps onto the refactored Spring Boot implementation. FCY shares the
mapping with the [VND twin](../transaction-history/migration-guide.md); this guide highlights the
FCY-specific translation decisions.

---

## 1. Service → controller/service mapping

| Legacy JEX | Refactor |
|---|---|
| `fnsh_0006_001_r001` (grid read) → `FnshHelper.fnsh_0002_001_r001(..., CcyCd.fromValue(input.getString("CCY_CD")), "fnsh_0006_001")` | `AccountController.listTransactions(acctSeqNo, …)` → `TransactionService.list(...)` |
| `fnsh_0006_001` totals (IDO `…_R016_CNT`) | `TransactionService.summary(...)` → `TransactionSummaryResponse` (plus `DEPOSIT_TOTAL`/`WITHDRAW_TOTAL` on the list response) |
| `fnsh_0006_003` detail/edit popup | `AccountController.getTransaction(...)` → `TransactionService.detail(...)` + the classification PATCH/DELETE endpoints |
| Confirm/classify (rcv/pay writes) | `TransactionService.confirm/undoConfirm` + `updateReportingAccount/Purpose/VasAccount/Contact` (+ clears) |
| Smart Organizer match (legacy match log) | `MatchService.listCandidates/create/undo` (R11 account-scoped) |
| `fnsh_0006_002` manual entry | **NOT ported** — manual transaction creation is out of refactor scope |
| Excel (`#btn_excel` → `formatExcel`) | `POST /accounts/transactions/export` → `TransactionHistoryExportProvider` (shared) |
| Sync (`#btn_refresh` → `getNxisasMulti`) | **Account Inquiry FCY** — `BankTransactionSyncService` (not this feature) |

---

## 2. IDO → repository mapping

| Legacy IDO / SQL | Refactor |
|---|---|
| `ACCT_TRNS_HSTR_R016` (rows; LEFT JOINs to `ACCT_MSTR`, `ACCT_TRNS_RCV_PAY_INFM`, `RMRK_ORG_INFM`, `BUSI_MATCH_LOG`, payment-summary tables) | `AcctTrnsHstrRepository.findByBizKeyAndBankCdAndAcctNo[AndTrscDtBetween]OrderByTrscDtDescTrscTmDesc(...)` + service-side enrichment (`confirmedRowsBySeq`, `rcvPayClassificationBySeq`, `matchInfoBySeq`) |
| `fnsh_0002_001_r001_dynamic0` WHERE — `ATH.ccy_cd = ?` (the FCY currency predicate) | **In-memory** `fetchRows(...)` currency filter on `ACCT_MSTR.CCY_CD` (the refactor keeps the data shape but moves the predicate to Java) |
| `fnsh_0002_001_r001_dynamic0` WHERE — date / inout / bank / acct / cnfm / keyword | Mix of the DB date-range finder + in-memory `matchesType/Cnfm/Keyword` filters |
| `ACCT_TRNS_HSTR_R016_CNT` (aggregate totals) | `TransactionService.list/summary` in-Java totals (`AmountFormatter.wire`) |
| Permission WHERE (`getPermittedBankAccountDynamic`) | `AccountAccessGuard.assertCanView` (per-account View grant in `ACCT_AUTH_INFO`) |

---

## 3. Envelope / naming migration

- Legacy `COMMON_HEAD`/`REC` → `ApiResponse<T>` (`{ status, data }`).
- **Transactions DTOs follow the global Jackson SNAKE_CASE strategy** with no per-DTO `@JsonProperty`
  exceptions — the UPPER_SNAKE holdovers were removed 2026-06-07, so wire is lowercase snake_case.
  Mutation request bodies are lowercase snake_case too. See
  [interface-specification.md §wire-casing](interface-specification.md).
- Legacy `WEB_SVC_ID = "fnsh_0006_001"` → refactor View Setting `WEB_SVC_ID = "banking-transaction-history"`
  (slug, shared VND + FCY), menu permission `banking-fcy-inquire-transactions`.
- ML codes / `WBExxxx` mapping for the transaction confirm/classify errors lands in
  `messages.properties` (`WBE_TRANSACTION_*`).

---

## 4. Key-generation migration

`ACCT_TRNS_RCV_PAY_INFM.RCV_PAY_SEQ_NO` (created on Confirm / classify) is allocated **in Java**
(`nextRcvPaySeqNo` = current MAX+1 for the account), not via a `wb_get_next_val` branch — the Java
generator is the preferred refactor pattern. Transaction rows themselves (`ACCT_TRNS_HSTR.ACCT_SEQ_NO`)
are created only by the sync path (Account Inquiry sibling), which uses
`LegacyFunctionRepository.nextVal("acct_trns_hstr","acct_seq_no",...)`.

---

## 5. Behaviour deliberately diverged / deferred

| Legacy behaviour | Refactor stance |
|---|---|
| DB-side paging (`PAGE_NO`/`PAGE_PER_CNT`) | **Modernized** — load-all + client pagination (accepted pending PO) |
| Per-column sort via `SortHelper` | **PARTIAL** — fixed `TRSC_DT DESC, TRSC_TM DESC`; per-column sort seed-ready not wired |
| Vietcombank `11704004` balance recompute (IDO `R016` `BAL` CASE) | **Absent** — `BAL` passed through verbatim; deferred pending PO (gap analysis §Open items) |
| Bank `11704020` remark rule / balance-hiding for `036/075/064` | **Absent** — deferred pending PO |
| Inline auto-classification (`RMRK_ORG_INFM` → auto trco/usage/project) | **Relocated** to Smart Organizer (`domain/banking/smartorganizer`), not on the list path |
| Memo / color (`wb_get_memo_*`), project, master-balance, maturity columns | **Out of scope** — not modelled on `TransactionRow` |
| Manual transaction entry (`fnsh_0006_002`) | **Not ported** |
| FCY currency from request `CCY_CD` | **Equivalent** — derived from `ACCT_MSTR.CCY_CD` (the account already scopes the currency); FCY-T01 approved |

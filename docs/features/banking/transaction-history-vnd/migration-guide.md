# Transaction History (VND) — Migration Guide (JEX → Spring Boot)

How the legacy `fnsh_0002_001` JSP/WSVC/IDO architecture maps onto the refactored Spring Boot
backend. This is migration-traceability prose — legacy WSVC ids appear only as `file:line` citations
and "migrated from …" narrative, per the identifier-naming rule. The full JEX → Spring + Next.js
mapping with kebab slugs is in
[docs/jex/features/transaction-history-vnd/migration-guide.md](../../../../../docs/jex/features/transaction-history-vnd/migration-guide.md).

---

## 1. Service → controller/service mapping

| Legacy WSVC | Behaviour | Refactor endpoint | Refactor service method |
|---|---|---|---|
| `fnsh_0002_001_r001` | list + totals + counts | `GET .../{acctSeqNo}/transactions` (+ `/summary`) | `TransactionService.list` / `.summary` |
| (detail read) | single-row detail | `GET .../{acctSeqNo}/transactions/{trnsSeqNo}` | `TransactionService.detail` |
| `fnsh_0002_001_u001` | confirm | `PUT .../{trnsSeqNo}/confirm` | `TransactionService.confirm` |
| `fnsh_0002_001_u001` (undo) | un-confirm | `DELETE .../{trnsSeqNo}/confirm` | `TransactionService.undoConfirm` |
| `fnsh_0002_001_u002`…`_u006` | classify (chart / purpose / VAS / contact / memo) | `PATCH/DELETE .../{reporting-account\|purpose\|vas-account\|contact}` | `TransactionService.update*/clear*` |
| (export) | Excel | `POST /accounts/transactions/export` | `TransactionHistoryExportProvider` |

> Legacy memo (`_u00x` colour/memo) is **not** ported — `RMRK_ORG_INFM` is out of PROD active scope.
> The four classification setters cover the in-scope axes (reporting account, purpose, VAS, contact).

## 2. IDO → repository mapping

| Legacy IDO | SQL shape | Refactor repository method |
|---|---|---|
| `ACCT_TRNS_HSTR_R016` | date-range + account, ~14 joins for memo/project/match | `AcctTrnsHstrRepository.findByBizKeyAndBankCdAndAcctNoAndTrscDtBetweenOrderByTrscDtDescTrscTmDesc` (+ unbounded variant) |
| `ACCT_TRNS_HSTR_R016_CNT` | count + sum(IN)/sum(OUT) | computed in `TransactionService` from the fetched set (no separate count query) |
| (confirm status / classify) | join `ACCT_TRNS_RCV_PAY_INFM` | `AcctTrnsRcvPayInfmRepository.findByBizKeyAndAcctSeqNoIn` (batch) + `existsByBizKeyAndAcctSeqNoAndCnfmYn` |

**Join reduction.** The legacy R016 joined ~14 lookup tables (memo store, project, match log, payment
summaries) to compose its 87-output-column REC. The refactor keeps the date-range + account predicate
in the DB and projects the rest from the active subset (`ACCT_TRNS_RCV_PAY_INFM`) + Java. Out-of-scope
join targets (`RMRK_ORG_INFM`, `BUSI_MATCH_LOG`, `SCDUL_PYMN_SUMR`, `OVRS_PYMN_SUMR`, `PRJT_INFM`) are
not entities — per the PROD-Active-Table-List enforcement rule, only active tables are refactored.

## 3. Envelope / naming migration

- Legacy `COMMON_HEAD` / `REC` envelope → `ApiResponse<T>` `{ status, data }`.
- Legacy `getInput()/getOutput()` field bags → typed Java record DTOs.
- Wire casing: this feature's response DTOs retain UPPER_SNAKE (`@JsonProperty`) as documented
  Sprint-1 holdovers; they flip to lowercase snake_case when the wire type migrates on both sides.
- Legacy ML codes → `WBExxxx`/`WBIxxxx` in `messages.properties`.

## 4. Key-generation migration

`RCV_PAY_SEQ_NO` (legacy `wb_get_next_val('acct_trns_rcv_pay_infm','rcv_pay_seq_no',…)`) →
Java per-(BIZ_KEY, ACCT_SEQ_NO) MAX+1 in `TransactionService.nextRcvPaySeqNo`. No local
`wb_get_next_val` branch was added (the Sprint-1 audit confirmed the feature reads only; the
confirmation write-path uses the Java generator — see backend CLAUDE.md §"Key-generation rule").

## 5. Behaviour deliberately diverged / deferred

| Item | Legacy | Refactor |
|---|---|---|
| Memo / colour tagging | `_u00x` + `RMRK_ORG_INFM` | not ported (out of active scope) |
| Project (PRJT) classification | joined `PRJT_INFM` | not ported (out of active scope) |
| Matched-by composition | `BUSI_MATCH_LOG` join | `LINK_MATCH_YN` flag only; match execution is Smart Organizer |
| Running balance | bank-supplied `BAL` | identical — no recompute |
| Per-column sort | client-side TS sort | seed-ready, not yet wired (PARTIAL) |
| Synchronization | NXiSAS on the page | owned by Account Inquiry sibling |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the parity table.

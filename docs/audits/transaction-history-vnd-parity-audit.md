# Transaction History (VND) — Legacy vs Refactor Parity Audit

> **System:** JEX `fnsh_0002_001` (+ sibling `fnsh_0006_001`) → Refactor `transaction-history` (slug `banking-transaction-history`)
> **Audited:** 2026-06-06 · **Method:** Read-only analysis of legacy JEX doc set (`docs/jex/features/transaction-history-vnd/`) cross-checked against live JEX source citations, **verified against actual refactor source** in `wecms-api/` (Spring Boot) and `wecms-web/` (Next.js).
> **Evidence rule:** Every verdict below carries `file:line` evidence read directly from source. Items derived from a sub-agent sweep but not personally re-opened by the auditor are tagged **`AGENT-VERIFIED`** (source code was read by a delegate, not docs); items that could not be confirmed are tagged **`NOT VERIFIED`**.
>
> **Primary feature home (2026-06-06):** consolidated into [features/banking/transaction-history-vnd/](../features/banking/current-savings-vnd/transaction-history/README.md) — see its [refactor-gap-analysis.md](../features/banking/current-savings-vnd/transaction-history/refactor-gap-analysis.md) for the current parity table. This audit is preserved as the detailed evidence trail.

---

## 0. Scope & architecture summary

| Layer | Legacy (JEX) | Refactor |
|---|---|---|
| Read | `fnsh_0002_001_r001` → `FnshHelper.fnsh_0002_001_r001` → IDO `ACCT_TRNS_HSTR_R016` + `_R016_CNT` (65-col multi-join SQL, `wb_get_*` functions, per-bank balance recompute, AUT_CLS subquery) | `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` → `TransactionService.list()` — JPA derived-finder by date range, **in-memory** type/keyword/confirm filtering ([TransactionService.java:84-138](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L84-L138)) |
| Writes (9 services) | `_c001/_u001..u006/_d001` | 12 controller endpoints on `AccountController` ([AccountController.java:293-455](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L293-L455)) |
| Match | `comm_0196_001/002` popups | `MatchService` ([AccountController.java:463-501](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L463-L501)) |
| Smart Organizer | full-page nav to `fnsh_0200_001.act` | `SmartOrganizerService` (modal + bulk confirm) **AGENT-VERIFIED** |
| Frontend | `fnsh_0002_001_osmc_VN.ts` (1755-line client) | `components/banking/transaction-history/*` + `lib/api/banking.ts` |

**Identifier:** the synthetic `acctSeqNo` path param is `BANK_CD + "_" + ACCT_NO`; the per-row id (`trnsSeqNo`) is the real `ACCT_SEQ_NO`. Tenant is resolved server-side from the JWT via `TenantContext.currentBizKey()` ([TransactionService.java:650-658](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L650-L658)). Entity `@IdClass` PK = `(BIZ_KEY, ACCT_SEQ_NO)` — matches the physical PK documented in [database-mapping.md §2](../../../docs/jex/features/transaction-history-vnd/database-mapping.md).

**Wire format:** Transaction History DTOs intentionally keep **UPPER_SNAKE** `@JsonProperty` ([TransactionRow.java:5-41](../../src/main/java/com/kosign/wecms/domain/banking/dto/TransactionRow.java#L5-L41)) — documented Sprint-1 holdover (root `wecms-api/CLAUDE.md` § "Holdovers"; frontend `ApiTransactionRow` matches). Not a bug; flip is scheduled when the feature's backend fully migrates.

---

## 1. Search Filters (account / keyword / transaction type)

### Legacy
- **Account:** `BANK_CD` + `ACCT_NO` selected from session/sidebar; one account at a time.
- **Keyword (`SRCH_WD`):** server matches across **8 dimensions** — `TRSC_AMT`, `ACCT_NO`, `RMRK`, translated remark (`wb_get_translate_text`, VN-only), `VAS_ACCT_CD`, `VAS_ACCT_NM`, `RCV_PAY_TRCO_NM`, `BANK_TRNS_SHORT_ID`, plus a `MEMO_INFM` EXISTS subquery. Evidence: `FnshHelper.java:704, 757-760`; [functional-specification.md §2](../../../docs/jex/features/transaction-history-vnd/functional-specification.md).
- **Type:** radio All/Deposit/Withdraw → `INOUT_DV` `""`/`1`/`2`.

### Refactor
- **Account:** one account per route (`[accountId]` segment); `resolve()` splits `bankCd_acctNo` ([TransactionService.java:650-658](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L650-L658)). Frontend has no multi-account picker on the page — selection is via the sidebar nested list / index redirect **AGENT-VERIFIED**.
- **Keyword:** matches **only 3 columns** — `RMRK`, `RMRK2`, `BANK_REF_NO` — case-insensitive `contains`, in-memory ([TransactionService.java:681-691](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L681-L691)). Wire param `keyword` ([banking.ts:193-202](../../../wecms-web/lib/api/banking.ts#L193-L202)).
- **Type:** `type` param → `DEPOSIT`/`WITHDRAW`/`ALL` mapped to `InoutDv.IN/OUT.code()` ([TransactionService.java:667-672](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L667-L672)). UI radio/toggle present **AGENT-VERIFIED**.

### Verdict
| Sub-area | Verdict | Risk |
|---|---|---|
| Account filtering | **MATCH** | LOW |
| Transaction-type filtering | **MATCH** | LOW |
| Keyword filtering | **PARTIAL** — 3 of 8 legacy search dimensions. Misses `TRSC_AMT`, `ACCT_NO`, translated remark, `VAS_ACCT_CD/NM`, `RCV_PAY_TRCO_NM`, `BANK_TRNS_SHORT_ID`, `MEMO_INFM`. A user searching by amount, counterparty name, purpose, or memo gets no hits in the refactor. | **MEDIUM** |

---

## 2. Date Range Behavior

### Legacy
- `FROM_DT`/`TO_DT` **required**; `validateInput` + `validateDate8` reject missing/malformed (`fnsh_0002_001_r001_act.jsp:34-44`). Calendar picker `comm_0106_001` offers presets (today/year/quarter/month). `MATCHED_AT` is converted UTC→user TZ on output (`r001_act:68`).

### Refactor
- `from`/`to` are **optional** ([AccountController.java:248-249](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L248-L249)); when either is blank the service returns **all history** ([TransactionService.java:660-665](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L660-L665)). **No date-format validation, no max-range cap.**
- `TRSC_DT` stored/compared as `VARCHAR(8)` `yyyyMMdd` — lexical `BETWEEN`, no timezone math. Frontend defaults to a 30-day range and converts ISO→`yyyyMMdd` **AGENT-VERIFIED**; no preset buttons (today/quarter/year) found **AGENT-VERIFIED**.
- `MATCHED_AT` UTC→TZ conversion: not modeled on the list row (`MATCHED_AT` is not in `TransactionRow`); confirm timestamp `CNFM_DTM` is written as UTC `yyyyMMddHHmmss` ([TransactionService.java:243](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L243)).

### Verdict
- **PARTIAL** — functional default differs (legacy requires dates; refactor defaults to a frontend 30-day window but backend accepts none → unbounded scan). No server-side date validation, no range cap, no presets, no UTC→TZ conversion for matched timestamps. **Risk: MEDIUM** (correctness: unbounded fetch on accounts with deep history feeds the no-pagination gap in §5; missing presets is a UX gap).

---

## 3. Deposit / Withdraw Filtering

### Legacy
`INOUT_DV` `""`/`1`/`2` applied in the SQL WHERE; `IN_AMT`/`OUT_AMT` derived in IDO (`TRSC_AMT` when `INOUT_DV='1'`/`'2'` else 0).

### Refactor
`matchesTypeFilter` in-memory; row mapping splits `depositAmt`/`withdrawAmt` by `InoutDv` ([TransactionService.java:667-672](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L667-L672), [:750-775](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L750-L775)). Same `1`=deposit / `2`=withdraw semantics.

### Verdict
- **MATCH** — query mapping equivalent (filter applied in-memory vs SQL, but identical result set). **Risk: LOW.**

---

## 4. Sorting

### Legacy
Server-side sort: client appends `paramSort` to the service id; `SortHelper.getSortDynamic` builds the ORDER BY (`ts:863-870`; `FnshHelper`). Multiple sortable columns.

### Refactor
- **No server-side sort.** The read endpoint accepts no `sortField`/`sortDirection` ([AccountController.java:244-254](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L244-L254)); the frontend client sends none ([banking.ts:185-202](../../../wecms-web/lib/api/banking.ts#L185-L202)). Rows always come back `TRSC_DT DESC, TRSC_TM DESC` (repository finder name).
- Frontend columns are flagged sortable via View Setting `SORT_YN` and sorting is **client-side over the fetched page** **AGENT-VERIFIED** (the §13a shared server-sort contract is *not* wired for this screen — the API client carries no sort params).

### Verdict
- **PARTIAL** — fixed date-descending order is reasonable as a default and matches the common legacy default, but the legacy multi-column server sort is absent; any frontend sort is client-side over an unpaginated set. **Risk: LOW** (functional results correct; ordering flexibility reduced).

---

## 5. Pagination

### Legacy
`PAGE_NO`/`PAGE_PER_CNT` sent on every read; page sizes 15/20/50/100 (default 15). Excel sends `0`/`0` to fetch all (`view:489-496`, `ts:836-840`).

### Refactor
- **No server-side pagination.** Read endpoint has no page params ([AccountController.java:244-254](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L244-L254)); service returns the full filtered list — `TransactionListResponse(mapped, mapped.size(), depositTotal, withdrawTotal)` ([TransactionService.java:132-138](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L132-L138)). `TOTAL_COUNT` = row count, not a paged total.
- Frontend paginates **client-side** via shared `usePagination` (`PAGE_SIZE_OPTIONS [10,20,50,100]`, default 10) **AGENT-VERIFIED**.

### Verdict
- **PARTIAL** — the user-visible result (a paginated table) is preserved client-side, but every request transfers the **entire** matching set. Combined with the optional/unbounded date range (§2) and no `wb_get_*` server-side trimming, this is a performance/scalability regression for deep-history accounts. **Risk: MEDIUM.**

---

## 6. Totals

### Legacy
IDO `ACCT_TRNS_HSTR_R016_CNT` returns `TOT_CNT`, `TOT_IN_AMT`, `TOT_OUT_AMT`, `TOT_CNFM_CNT`, `TOT_UN_CNFM_CNT`, `TOT_ALL_IN_CNT`. Unconfirmed counts `CNFM_YN='N' OR CNFM_YN IS NULL` ([database-mapping.md §6](../../../docs/jex/features/transaction-history-vnd/database-mapping.md)).

### Refactor
- List endpoint returns `DEPOSIT_TOTAL` / `WITHDRAW_TOTAL` / `TOTAL_COUNT` ([TransactionService.java:112-137](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L112-L137)).
- Confirmed/unconfirmed counts come from a **separate** `summary` endpoint ([AccountController.java:261-271](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L261-L271)) → `TransactionSummaryResponse(total, confirmed, total-confirmed, deposit, withdraw)` ([TransactionService.java:144-174](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L144-L174)).
- **Unconfirmed semantics MATCH:** `confirmed` = a `ACCT_TRNS_RCV_PAY_INFM` row with `CNFM_YN='Y'` exists ([TransactionService.java:693-701](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L693-L701)); unconfirmed = everything else (`'N'`, null, or no rcv/pay row) = `total − confirmed`. This reproduces the legacy "NULL counts as unconfirmed" rule (gap H5) by construction.

### Verdict
- **MATCH** — amount + confirmed/unconfirmed/all counts equivalent; the legacy NULL-is-unconfirmed semantics is preserved. (`TOT_AMT_USD`/`TOT_ALL_IN_CNT` legacy fields are unused in the VN page, so not porting them is correct.) **Risk: LOW.**

---

## 7. Balance Calculation — SPECIAL FOCUS

### Legacy
- General: `BAL` = `ATH.BAL` (stored running balance).
- **Vietcombank bank `11704004`:** running `BAL` is **recomputed** inside IDO `ACCT_TRNS_HSTR_R016` — derived from `ACCT_MSTR.BAL` minus later transactions (JEX issue `#42771`). High-risk gap H1. Evidence: [database-mapping.md §5](../../../docs/jex/features/transaction-history-vnd/database-mapping.md), [refactor-gap-analysis.md H1](../../../docs/jex/features/transaction-history-vnd/refactor-gap-analysis.md).

### Refactor
- `BAL` is read **directly** from the stored column: `AmountFormatter.wire(r.getBal())` in `toRow` and `detail` ([TransactionService.java:762](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L762), [:201](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L201)).
- **No bank-code branch anywhere** — no `11704004` special case, no `ACCT_MSTR.BAL`-minus-later-txns recomputation. Verified absent by reading the full service (no reference to that bank code; `ACCT_MSTR` is not joined in the read path).

### Verdict
- **MISSING** — the Vietcombank `11704004` running-balance reconstruction is not implemented. For that bank, displayed balances will be whatever was stored at sync time, which the legacy explicitly overrides. **Risk: HIGH** (wrong financial figures for an affected bank — the canonical reason gap H1 was flagged parity-critical).

---

## 8. AUT_CLS Logic (auto-classification / remark rules)

### Legacy
The read IDO `ACCT_TRNS_HSTR_R016` carries an `RMRK_ORG_INFM` LEFT JOIN (`ROI` subquery, `LIMIT 1`, `MATCH_TYPE` exact/`ilike`) producing `AUT_CLS_*` fallback fields shown when no manual classification exists. Gap H4. Evidence: [refactor-gap-analysis.md H4](../../../docs/jex/features/transaction-history-vnd/refactor-gap-analysis.md), `functional-specification.md §3` (Purpose/Contact cells fall back to `AUT_CLS_*`).

### Refactor
- The **list/detail path has no `AUT_CLS` fallback** — `toRow` passes only the manually-picked classification (`useUsagCd`, `vasAcctCd`, `rcvPayTrcoKey/Nm`) from `ACCT_TRNS_RCV_PAY_INFM`; positions for confirmer name/date are hard-`null` and no `RMRK_ORG_INFM` lookup exists ([TransactionService.java:711-775](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L711-L775)).
- The remark-rule engine **does** exist, but only inside **Smart Organizer** eligibility: `SmartOrganizerService` joins `RMRK_ORG_INFM` via `LATERAL` with `MATCH_TYPE='E'` exact / `ilike` + `DISP_ORD` ordering to surface rule-matched unconfirmed rows **AGENT-VERIFIED**.

### Verdict
- **PARTIAL / MISSING** — the rule engine is ported into Smart Organizer, but the **inline auto-classification fallback on the transaction grid** (Purpose/Contact cells showing `AUT_CLS_*` when manual classification is absent) is **not** reproduced. Users lose the "suggested" classification that legacy shows on every unclassified row. **Risk: MEDIUM.**

---

## 9. Classification Workflow (create/update/save)

### Legacy
`_c001` upserts `ACCT_TRNS_RCV_PAY_INFM` (create if `RCV_PAY_SEQ_NO` blank, else update) for Purpose (`USE_USAG_CD`), VAS (`VAS_ACCT_SEQ_NO`), Contact (`RCV_PAY_TRCO_KEY`); validates VAS/TRCO existence (`WBE0404`).

### Refactor
- Three PATCH endpoints — purpose / vas-account / contact ([AccountController.java:369-442](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L369-L442)).
- `ensureRcvPayRow` upserts: returns the existing rcv/pay row or creates a fresh one with `CNFM_YN='N'` and a Java-side `nextRcvPaySeqNo` (per-(bizKey,acctSeqNo) MAX+1) ([TransactionService.java:606-648](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L606-L648)).
- Validation: Purpose against `BUSI_USAG_SET_INFM` `USE_YN='Y'` ([:406-411](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L406-L411)); VAS against `BUSI_VAS_ACCT_INFM` `DEL_YN='N'`+`STTS='A'` ([:477-482](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L477-L482)); Contact against `TRCO_INFM` `TRCO_STTS != 'C'` ([:547-552](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L547-L552)). All `@Transactional`.

### Verdict
- **MATCH** — upsert-into-`ACCT_TRNS_RCV_PAY_INFM` semantics and existence validation preserved (refactor validates against active master rows, slightly stricter than legacy but parity-aligned). Refactor keys classification on `VAS_ACCT_CD` rather than `VAS_ACCT_SEQ_NO`; functionally equivalent for the picker. **Risk: LOW.**

---

## 10. Confirm Workflow

### Legacy
`_u002` sets `CNFM_YN='Y'` + confirmer id/name/timestamp; upserts the rcv/pay row (create if absent). **Bug L1:** stores `CnfmByUserId = loginUser.getBizKey()` (company key, not user id) — `u002_act:90,103`.

### Refactor
- `PUT .../confirm` → `confirm()` ([AccountController.java:293-300](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L293-L300), [TransactionService.java:223-274](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L223-L274)).
- Upserts (creates rcv/pay row if absent), guards against double-confirm (`WBE_TRANSACTION_ALREADY_CONFIRMED`), sets `CNFM_YN='Y'`, `cnfmByUserId = TenantContext.currentUserId()`, name, UTC timestamp.
- **Legacy bug L1 is FIXED** — stores the real user id, not the biz_key ([TransactionService.java:266](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L266)).

### Verdict
- **MATCH (improved)** — behaviorally equivalent and the documented confirmer-id bug is intentionally corrected per the migration guide §6. **Risk: LOW.**

---

## 11. Unconfirm Workflow

### Legacy
`_u003` patches `CNFM_YN='N'`, clears confirmer (`ACCT_TRNS_RCV_PAY_INFM_U000` patch). Client shows confirm dialog `WBC1164` → success `WBI6002`.

### Refactor
- `DELETE .../confirm` → `undoConfirm()` ([AccountController.java:303-310](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L303-L310), [TransactionService.java:277-295](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L277-L295)). Finds the `CNFM_YN='Y'` row (else `WBE_TRANSACTION_NOT_CONFIRMED`), flips to `'N'`, nulls confirmer id/name/dtm. `@Transactional`. Frontend shows a success dialog + refetch **AGENT-VERIFIED**.

### Verdict
- **MATCH** — rollback semantics equivalent. (Refactor does not pre-prompt a `WBC1164`-style confirm dialog before unconfirm by default; the frontend shows a success dialog after. Minor UX divergence, not a data-parity issue.) **Risk: LOW.**

---

## 12. Memo Workflow

### Legacy
VN page edits memos via the common memo popup (`getMemoPopup2`, `ts:1217-1227`) writing `MEMO_INFM` with `WEB_SVC_ID="fnsh_0002_001"`. Separate `_u001` service updates `ACCT_TRNS_HSTR.MEMO` (legacy/alternate path; not called from the VN client). OQ-1 left open which is canonical.

### Refactor
- **NOT FOUND.** No memo endpoint on `AccountController`; `TransactionService` has no memo method; `AcctTrnsRcvPayInfm` has no memo column; the `MEMO_INFM` table is used only by Card Transactions **AGENT-VERIFIED**. The `RMRK` column renders read-only on the grid **AGENT-VERIFIED**.

### Verdict
- **MISSING** — the per-transaction memo workflow is not implemented on either tier. **Risk: MEDIUM** (an operational classification affordance present in legacy is absent; note OQ-1 was never resolved, so this may be deliberate deferral — confirm with PO).

---

## 13. Reporting Account Workflow (set / update / clear)

### Legacy
`_u004` sets `CHART_ACCT_SEQ_NO`+`CHART_ACCT_CD` on `ACCT_TRNS_HSTR` after validating the chart account exists/active via `CHART_ACCT_INFM_R001` (`del_yn='N'`, `stts='A'`); `_u006` patches both to null. (Legacy bug L2: wrong `@JexDataInfo` id on `_u004`.)

### Refactor
- `PATCH .../reporting-account` → `updateReportingAccount()`: validates `CHART_ACCT_INFM` by `(bizKey, chartAcctCd, delYn='N')`, asserts `(seq_no, code)` pair matches and `STTS='A'`, then writes both columns on `ACCT_TRNS_HSTR` ([TransactionService.java:314-349](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L314-L349)).
- `DELETE .../reporting-account` → `clearReportingAccount()` nulls both, idempotent ([:357-379](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L357-L379)).

### Verdict
- **MATCH (improved)** — set/clear semantics + active-chart validation preserved; refactor adds a seq/code consistency guard and corrects the legacy `@JexDataInfo` mis-binding by construction. **Risk: LOW.**

---

## 14. Counterparty (Contact) Workflow (search / selection / persistence)

### Legacy
Typeahead `comm_1005_001` (IDO `TRCO_INFM_R019`) filters `biz_key`, `TRCO_STTS<>'C'`, `HAND_TRCO_KEY IS NOT NULL`; selection persisted via `_c001` (`RCV_PAY_TRCO_KEY` + name snapshot).

### Refactor
- Search via `GET /api/v1/settings/contacts` (list + search) **AGENT-VERIFIED**.
- Persistence via `PATCH .../contact` → `updateContact()`: validates `TRCO_INFM` (`TRCO_STTS != 'C'`), snapshots `RCV_PAY_TRCO_KEY` + `RCV_PAY_TRCO_NM` at pick time ([TransactionService.java:535-565](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L535-L565)). Frontend `ContactSelect` + recent-list cache **AGENT-VERIFIED**.

### Verdict
- **MATCH** — search filter + name-snapshot persistence equivalent. (Refactor reuses the general contacts list endpoint rather than a dedicated typeahead; functionally equivalent. The `HAND_TRCO_KEY IS NOT NULL` legacy filter is not re-verified on the refactor list query — **NOT VERIFIED** at that predicate level.) **Risk: LOW.**

---

## 15. VAS Workflow (lookup / assignment / persistence)

### Legacy
Typeahead `comm_1002_001` (IDO `BUSI_VAS_ACCT_INFM_R002`) filters `biz_key`, `del_yn='N'`, `stts='A'`; persisted via `_c001` (`VAS_ACCT_SEQ_NO`).

### Refactor
- Lookup `GET /api/v1/settings/vas-account/lookup` (active+live) **AGENT-VERIFIED**.
- Persistence via `PATCH .../vas-account` → `updateVasAccount()`: validates `BUSI_VAS_ACCT_INFM` (`DEL_YN='N'`, `STTS='A'`), writes `VAS_ACCT_CD` ([TransactionService.java:465-494](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L465-L494)).

### Verdict
- **MATCH** — lookup filter + assignment equivalent. Refactor persists `VAS_ACCT_CD` rather than `VAS_ACCT_SEQ_NO` (the legacy key) — functionally equivalent for display/picker but a key-shape divergence worth noting for any downstream join. **Risk: LOW.**

---

## 16. Smart Organizer

### Legacy
`#smartOrganizerVND` stores current filter into `sessionStorage` and **full-page navigates** to `fnsh_0200_001.act` (`ts:649-668`). No AJAX.

### Refactor
- A **modal-based** Smart Organizer with real endpoints: `GET /api/v1/banking/smart-organizer/eligible` (rule-matched unconfirmed rows via `RMRK_ORG_INFM` LATERAL join, acct types `03/04/05/06`, `DISP_ORD` ordering) and `POST .../confirm` (bulk confirm with classification, allocates `RCV_PAY_SEQ_NO` via `wb_get_next_val`) **AGENT-VERIFIED**.

### Verdict
- **PARTIAL (re-architected, arguably improved)** — the legacy was a navigation hand-off to a separate page; the refactor implements a self-contained eligible/confirm flow that subsumes the AUT_CLS rule engine. Behaviorally richer, but **not a 1:1 port** of the `fnsh_0200_001` destination page, which is outside this audit's source scope. **Risk: MEDIUM** — parity vs the full `fnsh_0200_001` Smart Organizer page is **NOT VERIFIED** (that page's spec was not in the audited doc set). Minor internal inconsistency noted: Smart Organizer's bulk-confirm uses `wb_get_next_val` for `RCV_PAY_SEQ_NO` while the per-row `confirm()` uses Java MAX+1 ([TransactionService.java:636-648](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L636-L648)) — two seq generators on the same table.

---

## 17. Match Candidate Search

### Legacy
Payment match popup `comm_0196_001/002` retrieves candidates for selected banks on withdraw rows.

### Refactor
- `GET .../{trnsSeqNo}/match-candidates` → `MatchService.listCandidates()`: returns unmatched domestic `SCDUL_PYMN_SUMR_DETL` lines, filtered by date range + keyword (RcptNm/TranUsag/TrcoCmnm/Title), tenant-scoped ([AccountController.java:463-472](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L463-L472)) **AGENT-VERIFIED**.

### Verdict
- **PARTIAL** — candidate retrieval + date/keyword filters present, but **no ranking** beyond the filter (legacy `DISP_ORD`/rule ranking not reproduced for candidates) and **overseas candidates are deferred** (domestic only). **Risk: MEDIUM.**

---

## 18. Match

### Legacy
For withdraw rows on banks `["11704020","11704088","970457","970424"]` only, opens match popup; persists to `BUSI_MATCH_LOG` + `LINK_MATCH_YN` on `ACCT_TRNS_HSTR`. Gap H8 (bank-restricted).

### Refactor
- `POST .../match` → `MatchService.create()`: validates not-already-matched on both sides, writes/locks `BUSI_MATCH_LOG`, flips `LINK_MATCH_YN='Y'` on `ACCT_TRNS_HSTR` + `SCDUL_PYMN_SUMR_DETL`, atomic `@Transactional` ([AccountController.java:481-488](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L481-L488)) **AGENT-VERIFIED**.
- **No 4-bank restriction** — match can be created for any bank's withdraw row (legacy gap H8 not enforced) **AGENT-VERIFIED**. Domestic only (overseas `payTypeCd` rejected).

### Verdict
- **PARTIAL** — persistence + validation + dual-side `LINK_MATCH_YN` flip are equivalent and correct, but the **bank-code allowlist (`11704020/11704088/970457/970424`) is not enforced**, and overseas matching is deferred. The missing allowlist means the Match affordance can appear on banks where legacy hides it. **Risk: MEDIUM.**

---

## 19. Unmatch

### Legacy
Undo via `comm_0196_002`; clears the match link.

### Refactor
- `DELETE .../match` → `MatchService.undo()`: flips `LINK_MATCH_YN='N'` on both sides, sets `LOCKED_YN='N'` on `BUSI_MATCH_LOG` (preserves audit history), atomic ([AccountController.java:495-501](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L495-L501)) **AGENT-VERIFIED**.

### Verdict
- **MATCH** — undo flow + dual-side data updates equivalent; audit-row preservation is a sound design. **Risk: LOW.**

> **Note — `_d001` (delete confirmed rcv/pay row):** **MISSING** in refactor (no delete-when-confirmed endpoint). This matches legacy reality — `_d001` is **not called from the VN client** either (OQ-4 unresolved). Treated as out of scope, not a parity gap.

---

## 20. Permissions — SPECIAL FOCUS

### Legacy
- Read service `WTA_USER_ALLOW_YN=Y`; write services `NORM_USER_ONLY_YN=Y`.
- **Row-level filter:** non-admin users get `QueryHelper.getPermittedBankAccountDynamic` appended to the read SQL (`FnshHelper.java:764-767`), restricting which bank accounts' transactions are visible. Exact backing tables were **NOT VERIFIED** in the legacy doc set (helper not opened). Gap H7.

### Refactor
- **Endpoint gating:** every transaction endpoint carries `@PreAuthorize("@menuAuthz.canAccessAny('banking-vnd-inquire-transactions','banking-fcy-inquire-transactions')")` ([AccountController.java:245](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L245) and repeated on all 12 handlers) — F-3 menu authorization is live.
- **Tenant isolation:** every query constrains `BIZ_KEY = TenantContext.currentBizKey()` ([TransactionService.java:650-658](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L650-L658)). The `acctSeqNo` is resolved server-side from the path, not a client `biz_key`.
- **Row-level permitted-account filter:** **NOT IMPLEMENTED.** The transaction endpoint does not re-apply a `getPermittedBankAccountDynamic`-equivalent per-account permission filter — it assumes the caller reached a permitted account via the upstream account-list gate. Verified by reading `resolve()`/`fetchRows()` — no permitted-account join.

### Verdict
- **PARTIAL** — tenant isolation (`BIZ_KEY`) and menu-level authorization are solid and arguably stronger than legacy. But the **non-admin row-level permitted-bank-account filter (`getPermittedBankAccountDynamic`) is missing** at the transaction-read tier. If a non-admin user can address an account id they are not permitted (e.g. by guessing/altering `bankCd_acctNo` within their own tenant), the endpoint would return that account's transactions, since only `BIZ_KEY` is enforced, not the per-user account grant. **Risk: HIGH** (potential intra-tenant data exposure — must confirm whether `banking-vnd-inquire-transactions` menu auth + account-list gating fully cover this, or whether a per-account permission check is required before sign-off).

---

## 21. Error Handling

### Legacy
`WBE0404` (not found), `WBE0001` (validation/seq mismatch), `WBW0001` (warning on existing row), `WBC1164`/`WBI6002`/`WBI6003` (client dialogs). Several acts call `beginTransaction()` with no explicit commit (relies on filter auto-commit — NOT VERIFIED).

### Refactor
- Domain error codes via `BusinessException`: `WBE_TRANSACTION_NOT_FOUND`, `_ALREADY_CONFIRMED`, `_NOT_CONFIRMED`, `_CHART_ACCOUNT_NOT_FOUND`, `_CHART_ACCOUNT_CODE_MISMATCH`, `_PURPOSE_NOT_FOUND`, `_VAS_ACCOUNT_NOT_FOUND`, `_CONTACT_NOT_FOUND`, `WBE2002` ([TransactionService.java:59-72](../../src/main/java/com/kosign/wecms/domain/banking/service/TransactionService.java#L59-L72)); match codes on `MatchService` **AGENT-VERIFIED**.
- All errors flow through the standard `{ status, timestamp, path, trace_id }` envelope (root `wecms-api/CLAUDE.md` § Exception mapping); bean validation via `@Valid`/`@NotBlank`. All writes `@Transactional` — commit is explicit, removing the legacy auto-commit ambiguity.

### Verdict
- **PARTIAL (re-coded, improved)** — error coverage is equivalent or better and commit semantics are now explicit, but the **codes are new slugs, not the legacy `WBExxxx` values** (per the CTO directive — intentional). Functional parity holds; literal code parity does not (by design). **Risk: LOW.**

---

## 22. Multi-language (EN / KO / VI)

### Legacy
ML codes `fnsh_0002_001_j043`–`j070`, `ml_001`–`ml_005`, `m010`–`m013`, plus non-standard `js01`/`js02` (Unmatch / Match to Payment) which break the `_j###` pattern (gap L6).

### Refactor
- Frontend i18n keys `txn_history.*` present in **all three** locale files (`locales/{en,ko,vi}.json`) — columns, status tabs, actions, search placeholder, success messages **AGENT-VERIFIED**. No missing-language gaps reported.
- Backend domain enums carry i18n keys but `tx.inout.*` locale entries are deferred (no consumer needs server-side labels yet — root `wecms-api/CLAUDE.md`).

### Verdict
- **MATCH** — EN/KO/VI all present for the screen. (KM not supported by the web app — out of scope. The legacy `js01`/`js02` non-standard ids are mooted since the refactor uses a clean key namespace.) **Risk: LOW.**

---

## 23. Export

### Legacy
Excel icon → `loadData({TO_EXCEL:true})` fetches all rows (paging `0/0`), builds a JGM grid client-side, downloads. Header `Transaction History (… VND)`.

### Refactor
- **Implemented.** `POST /api/v1/banking/accounts/transactions/export` → shared `ExcelExportService` (Apache POI), `screenId="banking-transaction-history"` (V128 seed), `filters.acctSeqNo` required ([AccountController.java:510-516](../../src/main/java/com/kosign/wecms/domain/banking/controller/AccountController.java#L510-L516)). Frontend routes through the shared `useExcelExport` hook with a per-screen column registry **AGENT-VERIFIED**.

### Verdict
- **MATCH** — export present, column set driven by View Setting, server-side XLSX generation (architecturally cleaner than the legacy client-side grid). Column-for-column parity vs the legacy Excel layout is **NOT VERIFIED** (legacy `formatExcel`/`fn_makeGrid` column order not diffed against the refactor registry). **Risk: LOW.**

---

## Final Summary

### Refactor Completion

**≈ 80%** (functional coverage of the audited VN behavior).

Rationale: 9 of the 23 audit areas are full **MATCH** (deposit/withdraw, totals, classification, confirm, unconfirm, reporting account, contact, VAS, unmatch, multi-language, export — the write-side core is strong and the confirmer-id bug is fixed). The shortfall is concentrated in the **read query fidelity** (balance, AUT_CLS, keyword breadth, no server pagination/sort, date validation) and **row-level permissions**.

### Findings Summary

| Verdict | Count | Areas |
|---|---|---|
| **MATCH** | 11 | Account/type filter (1 partial-with-match split), Deposit/Withdraw (3), Totals (6), Classification (9), Confirm (10), Unconfirm (11), Reporting Account (13), Contact (14), VAS (15), Unmatch (19), Multi-language (22), Export (23) |
| **PARTIAL** | 9 | Keyword search (1), Date range (2), Sorting (4), Pagination (5), AUT_CLS (8), Smart Organizer (16), Match candidate (17), Match (18), Permissions (20), Error handling (21) |
| **MISSING** | 3 | Vietcombank balance recompute (7), Memo workflow (12), inline AUT_CLS fallback (8 — overlaps PARTIAL) |

(Counts treat area 1 as MATCH-with-a-PARTIAL-sub-item and list the dominant verdict; the keyword sub-item, AUT_CLS, and Smart Organizer span PARTIAL/MISSING.)

### High-Risk Gaps

1. **Vietcombank `11704004` running-balance reconstruction — MISSING (§7).** Legacy IDO recomputes `BAL` from `ACCT_MSTR.BAL` minus later txns (issue #42771); refactor reads the stored `BAL` verbatim. **Wrong balances for the affected bank.**
2. **Row-level permitted-bank-account filter — MISSING (§20).** Refactor enforces tenant (`BIZ_KEY`) + menu auth, but not the legacy non-admin `getPermittedBankAccountDynamic` per-account grant at the transaction tier. **Potential intra-tenant data exposure** if account-list gating does not fully cover direct id addressing.

### Medium-Risk Gaps

3. **Keyword search 3-of-8 dimensions (§1)** — no amount/account/counterparty/purpose/memo/short-id search.
4. **No server-side pagination + optional/unbounded date range (§2, §5)** — full result set transferred per request; scalability regression on deep-history accounts.
5. **Inline AUT_CLS auto-classification fallback missing on the grid (§8)** — rule engine exists only in Smart Organizer.
6. **Memo workflow not implemented (§12)** — confirm with PO (OQ-1 was never resolved).
7. **Match: no 4-bank allowlist; candidate ranking + overseas deferred (§17, §18).**

### Blocking Issues (must resolve before parity sign-off)

- **B1 — Vietcombank balance recompute (§7).** Financial-figure correctness. Either port the IDO balance logic (recommended: keep as native query/DB view per migration-guide §3) or reimplement in Java with mandatory parity tests.
- **B2 — Row-level account permission filter (§20).** Security. Confirm whether menu-auth + account-list gating is sufficient, or implement a per-account permission check in `resolve()`/`fetchRows()`. Until proven, treat as a data-exposure risk.
- **B3 — PO decisions still open:** OQ-1 (memo path / whether memo is in scope), OQ-3 (`wb_get_*`/R016 port strategy — affects balance + AUT_CLS), OQ-5 (USD totals — confirmed not needed), OQ-6 (merge sibling `fnsh_0006_001`). Per [refactor-gap-analysis.md §4](../../../docs/jex/features/transaction-history-vnd/refactor-gap-analysis.md), these should be resolved before declaring read parity.

### SIT Readiness

**YES (with caveats)** — the write workflows (classify/confirm/unconfirm/reporting-account/contact/vas/match/unmatch), totals, export, and i18n are coherent and testable end-to-end for the common, non-Vietcombank, admin-scoped case. SIT can proceed on the happy path while B1/B2 are tracked. Smart Organizer parity vs `fnsh_0200_001` should be exercised but is not blocking SIT.

### UAT Readiness

**NO** — B1 (Vietcombank balance) produces visibly wrong balances for an affected bank, and the keyword/pagination/AUT_CLS gaps change observable behavior versus the live system UAT testers will compare against. Resolve B1, and decide the keyword-breadth + memo + AUT_CLS-fallback scope with the PO, before UAT.

### PROD Readiness

**NO** — B2 (missing row-level account permission filter) is a security blocker for multi-user tenants; B1 is a correctness blocker. Neither may ship to PROD. (The refactor is in any case local-only R&D per project scope — PROD readiness is assessed for completeness only.)

### Final Verdict

```
NOT READY FOR PARITY SIGN-OFF
```

**Justification (evidence-based):** The refactor achieves strong write-side and totals/confirm parity (verified directly in `TransactionService.java` / `AccountController.java`), fixes a legacy confirmer-id bug, and ships export + full EN/KO/VI i18n. However two parity-critical behaviors are verifiably absent — the Vietcombank `11704004` balance reconstruction (§7, `TransactionService.java:762` reads stored `BAL` with no bank branch) and the non-admin row-level permitted-account filter (§20, `resolve()`/`fetchRows()` enforce only `BIZ_KEY`) — and the main-grid auto-classification fallback, full-breadth keyword search, server pagination/sort, and memo workflow are missing or partial. These are correctness and security gaps, not cosmetic, and three PO open questions (OQ-1/3/6) remain unresolved. Sign-off requires closing B1 + B2 and a PO decision on the scope of the keyword/AUT_CLS/memo gaps.

---

### Appendix — Verification ledger

| Evidence | How verified |
|---|---|
| Read endpoint params, list/summary/detail/confirm/unconfirm/classification/reporting-account/contact/vas logic, totals semantics, balance, AUT_CLS absence, keyword 3-col, currency not forced, entity @Id, tenant resolve, no pagination/sort | **Auditor read source directly** — `TransactionService.java` (full), `AccountController.java:230-517`, `TransactionRow.java` |
| Frontend read params (no sort/page), wire param mapping | **Auditor read source directly** — `wecms-web/lib/api/banking.ts:185-214` |
| Legacy behavior (all areas) | Legacy doc set read in full + its `file:line` citations into live JEX source (`fnsh_0002_001_*`, IDO XMLs, `FnshHelper.java`) |
| MatchService (candidate/create/undo, no bank allowlist), SmartOrganizerService (RMRK_ORG_INFM LATERAL, bulk confirm), chart-of-accounts/vas/contact search endpoints, frontend components/columns/dialogs/i18n/View-Setting/export wiring | **AGENT-VERIFIED** — delegate read actual source under `wecms-api/` + `wecms-web/`; not re-opened line-by-line by the auditor |
| `fnsh_0200_001` Smart Organizer destination-page parity; legacy Excel column order; `getPermittedBankAccountDynamic` backing tables; refactor contact list `HAND_TRCO_KEY` predicate | **NOT VERIFIED** — out of audited source scope |

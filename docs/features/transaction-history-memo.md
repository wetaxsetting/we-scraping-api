# Transaction History Memo — Backend (wecms-api)

**Feature slug:** `transaction-history-memo` (paired with `wecms-web/docs/features/transaction-history-memo.md`)
**Screens:** Banking → Transaction History **VND** and **FCY**
**Legacy source of truth:** JEX `fnsh_0002_001` (VND), `fnsh_0006_001` (FCY)
**Status:** PLAN ONLY — not implemented. Awaiting PO confirmations (see Risks).
**Date:** 2026-07-07

## Related documents

- Runtime root-cause audit: [`docs/audits/transaction-history-memo-runtime-audit.md`](../../../docs/audits/transaction-history-memo-runtime-audit.md)
- Legacy memo API/WEB audit: [`wecms-web/docs/audits/memo-legacy-api-web-audit.md`](../../../wecms-web/docs/audits/memo-legacy-api-web-audit.md)
- Reuse reference (Card Transactions memo): [`wecms-api/docs/features/payment/card-transactions.md`](payment/card-transactions.md)
- Frontend counterpart: [`wecms-web/docs/features/transaction-history-memo.md`](../../../wecms-web/docs/features/transaction-history-memo.md)

---

## Goal

Add Memo support to Transaction History (VND & FCY) with the **same behavior and data model as Card Transactions memo**, storing in the shared legacy `MEMO_INFM` table. Reuse existing memo infrastructure; do not duplicate logic.

## Locked decisions

| Topic | Decision |
|---|---|
| Icon visibility | Memo icon shown **only on confirmed transactions** (matches live `_osmc_VN` runtime; see runtime audit). Backend returns `memo_color`/`memo_count` for all rows; the **frontend** gates rendering by confirmed status. |
| `WEB_SVC_ID` | Modern route-style values — VND `/banking/transaction-history/vnd`, FCY `/banking/transaction-history/fcy`. **Not** legacy `fnsh_0002_001`/`fnsh_0006_001`. VND and FCY kept distinct so threads never collide. |
| Memo thread key | 4-key: `REF_NO=BANK_CD`, `REF_NO2=ACCT_NO`, `REF_NO3=TRSC_DT`, `REF_NO4=ACCT_SEQ_NO` (legacy parity). |
| Legacy data | **No migration** of existing `fnsh_0002_001`/`fnsh_0006_001` memos unless PO/DBA approves (see Risk R2). |
| Card reuse | Extract shared `common/memo/MemoService`; migrate Card to delegate, **keeping Card behavior 100% unchanged** (Risk R1, approved approach). |

---

## Backend design

### Reuse-first inventory (already generic — no change to structure)

- `MemoInfm` entity — has `REF_NO`…`REF_NO9`, `MEMO`(200), `COLOR`(6), `REG_ID/REG_DTM`, `MOD_ID/MOD_DTM`; PK `(BIZ_KEY, WEB_SVC_ID, SEQ_NO)`.
- `MemoInfmRepository` — existing single-`REF_NO` finders + `updateColorByRef`.
- `LegacyFunctionRepository.nextMemoInfmSeqNo(bizKey, webSvcId)` — SEQ_NO via `wb_get_next_val('memo_infm','seq_no',biz_key,web_svc_id)`; works for any `web_svc_id`.
- `MemoScope` enum — already reserves `TRANSACTION_HISTORY_VND` / `_FCY` placeholders.
- Wire DTOs `MemoRow`, `MemoListResponse` — reusable.

### 1. Shared `common/memo/MemoService` (NEW) — R1 approved approach

Extract memo business logic into a reusable service parameterized by scope + ref key:

```
common/memo/
  MemoService.java        // list / create / update / updateColor / delete / aggregate
  MemoRefKey.java         // value object: refNo, refNo2, refNo3, refNo4
```

Proposed surface:

| Method | Behavior |
|---|---|
| `list(MemoScope scope, MemoRefKey key)` | memos for one thread, `MOD_DTM DESC, SEQ_NO DESC`; resolves author names; sets `canEdit = userId.equals(regId)` |
| `create(MemoScope scope, MemoRefKey key, String memo, String color)` | insert (SEQ_NO via `nextMemoInfmSeqNo`, one-shot retry on PK collision), then propagate color across the thread (legacy `c002`) |
| `update(MemoScope scope, BigDecimal seqNo, String memo)` | author-only (`WBE_MEMO_NOT_AUTHOR`); touch `MOD_ID/MOD_DTM` |
| `updateColor(MemoScope scope, MemoRefKey key, String color)` | bulk color across thread; **no** author gate |
| `delete(MemoScope scope, BigDecimal seqNo)` | author-only; hard delete |
| `aggregate(MemoScope scope, List<String> refNo4s)` | batch → `Map<acctSeqNo, MemoAgg(count, newestColor)>` for grid enrichment |

**Card migration (behavior-preserving):** `CardTransactionsService` memo methods delegate to `MemoService` with `MemoRefKey.of(cardSeqNo)` (only `refNo` set) and `MemoScope.CARD_TRANSACTIONS`. Wire endpoints, DTOs, validation, author rule, color propagation, seq funnel, and `resolveMemoAggregates` output stay identical. Card = single-key aggregate (by `refNo`), Transaction History = 4-key aggregate (by `refNo4`); the shared service exposes both aggregate paths via `MemoRefKey`.

### 2. `MemoScope.java` (EDIT)

```java
TRANSACTION_HISTORY_VND("/banking/transaction-history/vnd"),  // was "fnsh_0002_001"
TRANSACTION_HISTORY_FCY("/banking/transaction-history/fcy"),  // was "fnsh_0006_001"
```

Both 32 chars — fit `WEB_SVC_ID varchar(50)`. `CARD_TRANSACTIONS` unchanged.

### 3. `MemoInfmRepository` (EDIT — add 4-key methods)

- `findByBizKeyAndWebSvcIdAndRefNoAndRefNo2AndRefNo3AndRefNo4OrderByModDtmDescSeqNoDesc(...)` — one transaction's memos.
- `findByBizKeyAndWebSvcIdAndRefNo4In(bizKey, webSvcId, acctSeqNos)` — batch grid aggregate (keyed on `ACCT_SEQ_NO`, the unique discriminator).
- `updateColorByRefKeys(bizKey, webSvcId, refNo, refNo2, refNo3, refNo4, color)` — 4-key thread color propagation (legacy `u003`).

> Optional cleanup (not required): relocate `MemoInfm` + `MemoInfmRepository` + `MemoRow`/`MemoListResponse` from `domain/payment/cardtransactions` to `common/memo` now that they are shared. Deferred to avoid Card import churn; call out in PR if done.

### 4. `AccountController` (EDIT — add memo endpoints)

Base `/api/v1/banking`. Keys derived **server-side** — never trusted from client:

| Method | Path | Body | Rule |
|---|---|---|---|
| GET | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos` | — | list |
| POST | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos` | `{memo, color?}` | create |
| PATCH | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos/{seq_no}` | `{memo}` | author-only |
| PATCH | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos/color` | `{color}` | no author gate |
| DELETE | `/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/memos/{seq_no}` | — | author-only |

- `trnsSeqNo` = the transaction's `ACCT_SEQ_NO`. Server loads the `AcctTrnsHstr` row by `(bizKey, acctSeqNo = trnsSeqNo)` → derives `REF_NO=BANK_CD`, `REF_NO2=ACCT_NO`, `REF_NO3=TRSC_DT`, `REF_NO4=ACCT_SEQ_NO`.
- **Scope (VND vs FCY):** frontend sends an explicit `scope` param (`VND`/`FCY`) → maps to `MemoScope.TRANSACTION_HISTORY_VND/_FCY` (fallback: derive from account `CCY_CD`). See Risk R3.
- Authorization reuses the existing transaction guard (`banking-vnd-inquire-transactions` / `banking-fcy-inquire-transactions`).
- Request/response wire format lowercase snake_case (`memo_color`, `memo_count`, `seq_no`, …).

### 5. List enrichment — `TransactionService.toRow` (EDIT)

- Add `memo_color` / `memo_count` to `TransactionRow` DTO.
- In `TransactionService.list(...)`, batch-fetch memo aggregates via `MemoService.aggregate(scope, acctSeqNos)` keyed on `ACCT_SEQ_NO`; pass into `toRow(...)` (newest memo color + count).
- **Returned for every row** regardless of confirmation; the frontend performs the confirmed-gate. This matches legacy IDO `ACCT_TRNS_HSTR_R016`, which returns `wb_get_memo_cont`/`wb_get_memo_color` for all rows.
- Export/report paths pass an empty aggregate map (parity with Card).

### Legacy behavior preserved

- Author-only edit/delete (`WBE_MEMO_NOT_AUTHOR`); color has no author gate.
- Color is a **per-thread** attribute propagated across all memos on the 4-key thread (one transaction row).
- 200-char cap; list order `MOD_DTM DESC`; tenant isolation by server-side `BIZ_KEY`.
- SEQ_NO strictly via `wb_get_next_val` funnel; no new ID scheme.

---

## Reuse strategy from Card Transactions

| Card asset | Reuse |
|---|---|
| `MemoInfm`, `MemoInfmRepository`, seq funnel, `MemoScope` | Reused; repo gains 4-key finders |
| Card memo service logic | Generalized into shared `common/memo/MemoService`; Card delegates, behavior unchanged |
| `MemoRow`, `MemoListResponse` | Reused |
| Author rule, color-thread propagation, 200-char cap, `MOD_DTM DESC`, `fileW` default, aggregate model | Reused via shared service |

---

## Expected modified / created files (backend)

- **NEW** `common/memo/MemoService.java`, `common/memo/MemoRefKey.java`
- **EDIT** `common/memo/MemoScope.java` — finalize VND/FCY route values
- **EDIT** `domain/payment/cardtransactions/repository/MemoInfmRepository.java` — 4-key finders, `refNo4In` batch, 4-key color update
- **EDIT** `domain/banking/controller/AccountController.java` — 5 memo endpoints
- **EDIT** `domain/banking/service/TransactionService.java` — `toRow` memo enrichment + scope handling
- **EDIT** `domain/banking/dto/TransactionRow.java` — `memoColor`, `memoCount`
- **NEW** Transaction-History memo request DTO(s) (create/color — no `ref_no`, path-derived)
- **EDIT** `domain/payment/cardtransactions/service/CardTransactionsService.java` — delegate to `MemoService` (behavior-preserving)

**No schema change, no Flyway, no new table.** `MEMO_INFM` already exists; `ddl-auto=validate` respected.

---

## Risks & required PO confirmations

- **R1 — shared service touches working Card code (approved approach).** Extracting `MemoService` and migrating `CardTransactionsService` to delegate modifies a completed feature. Mitigation: delegation must be **strictly behavior-preserving** — identical endpoints, DTOs, validation, author rule, color propagation, aggregate output; verified against Card before merge. Per `feedback_preserve_existing_code`, no adjacent Card behavior changes.
- **R2 — modern `WEB_SVC_ID` hides existing legacy memos.** Refactor writes/reads `/banking/transaction-history/vnd|fcy`, but the existing **~3,342 (VND) / ~3,341 (FCY) legacy memos are stored under `fnsh_0002_001`/`fnsh_0006_001`**. Consequence: **existing legacy memos will NOT appear on the refactor screen**, and refactor-created memos will not appear on the live legacy screen. **No migration will be performed unless PO/DBA explicitly approves it.** If approved later, a one-time DBA `UPDATE MEMO_INFM SET WEB_SVC_ID='/banking/transaction-history/vnd' WHERE WEB_SVC_ID='fnsh_0002_001'` (+ FCY) under `Claude Code (AI)/sql/`, with PK-collision pre-check, DBA-run (not Flyway).
- **R3 — scope resolution.** Confirm frontend passes explicit `scope` (recommended) rather than backend inferring from `CCY_CD`.
- **R4 — TRSC_DT derivation.** All 4 REF keys derived from the `AcctTrnsHstr` row via `acct_seq_no` (PK partition key — reliable unique lookup).
- **R5 — no regression.** Memo enrichment is additive to `toRow`; existing list/summary/confirm/undo/match/export paths must remain unchanged; verify on implementation.

---

## Final implementation checklist (backend)

- [ ] PO sign-off obtained on R2 (modern `WEB_SVC_ID`, no legacy migration now).
- [ ] `MemoScope` VND/FCY set to modern routes.
- [ ] `common/memo/MemoService` + `MemoRefKey` created; list/create/update/color/delete/aggregate implemented.
- [ ] `MemoInfmRepository` 4-key finders + `refNo4In` batch + 4-key color update added.
- [ ] `CardTransactionsService` delegates to `MemoService`; Card behavior verified 100% unchanged.
- [ ] `AccountController` 5 memo endpoints added; REF keys derived server-side; scope mapped from param.
- [ ] `TransactionRow` DTO gains `memo_color`/`memo_count`; `TransactionService.toRow` enriched (all rows).
- [ ] Author-only edit/delete + color-thread propagation + 200-char + `MOD_DTM DESC` verified.
- [ ] SEQ_NO via `wb_get_next_val` funnel confirmed for new `web_svc_id` values.
- [ ] Wire format lowercase snake_case; VND & FCY threads confirmed distinct.
- [ ] No schema/Flyway change; existing transaction endpoints regression-free.
- [ ] Paired with frontend checklist in `wecms-web/docs/features/transaction-history-memo.md`.

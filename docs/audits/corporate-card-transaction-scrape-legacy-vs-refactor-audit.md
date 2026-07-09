# Corporate Card — Transaction Scrape/Insert: Legacy vs Refactor Audit

- **System:** Refactor (`wecms-api`) vs Legacy JEX (source of truth)
- **Feature:** Payment > Corporate Card > Card Transactions (`card-transactions`)
- **Date:** 2026-07-02
- **Author:** Claude Code (AI), reviewed by Sonny
- **Legacy source of truth:** `fnsh_0001_001_c001_act.jsp` (`IsasJob.CARD_USING` branch), `IDO.CARD_TRNS_HSTR_C001.xml`, `CardTrnsHstrDAO`, `CardMstrDAO`
- **Bug report reference:** one scraped merchant transaction (e.g. `FACEBK *49D6XUDPJ2`) inserted 4× — once per registered card (4372 / 3861 / 8524 / 1167).

---

## 1. Legacy flow summary

The corporate-card scrape is **client-orchestrated**, not a batch:

1. The scrape popup (`nxisas_cardTranList.ts`) builds one `Job = "CARD_USING"` request **per registered card**, grouped by `BANK_CERT_SEQ`.
2. `callNxisas` → gateway `nxisas_fnsh_r001_act.jsp` forwards to the external Coocon/NXiSAS API and returns raw scraped JSON to the browser (no DB write).
3. The browser POSTs all thread results as `JSON_STR` to `fnsh_0001_001_c001_act.jsp`.
4. In the `IsasJob.CARD_USING` branch, legacy loops the **scraped transaction list** (`cardArr[0].getCardUsing()`) and, for **each scraped transaction**:
   - Looks up the card master **by the scraped card number**:
     ```java
     // fnsh_0001_001_c001_act.jsp:1327-1330
     CardMstr cardMstr = CardMstrDAO.findByDynamic(
         "and biz_key = ? and bank_cd = ? and card_no = ? and actv_yn = 'Y'",
         bizKey, bankCd, cardUsing.getCardNum());
     ```
   - If `cardMstr == null` → **`continue` (skip)** — an unmatched scraped card produces no row (`:1428`).
   - Runs a duplicate existence probe on the composite natural key `biz_key, card_no, trsc_dt, trsc_tm, ccy_cd, trsc_amt, merch_nm, trim(auth_code)` (`:1335-1357`).
   - If a duplicate exists → skip; else INSERT one `CARD_TRNS_HSTR` row **stamped with `cardUsing.getCardNum()`** (the scraped card number), `CARD_SEQ_NO` freshly generated (`:1365-1416`).

**Key legacy property:** attribution is **transaction-driven** — the stored `CARD_NO` is the transaction's **own scraped card number**, never "the card we happened to be requesting." The registered-card list is consulted only as a per-transaction point lookup (match → insert, no match → skip). There is no loop that fans one transaction across all registered cards.

Card-number format on both sides is the masked form `4696********NNNN`; legacy matches by exact equality.

## 2. Refactor flow summary

Two orchestrators share one persister:

- `CardTransactionScrapeService` (`POST /sync-session`, live/challenge path) → `processOneCard(...)`
- `CardTransactionSyncService` (`POST /sync`, deprecated headless path) → inner `for (CardMstr card : group)` loop
- Both call **`CardTransactionPersister.persist(bizKey, userId, now, card, scraped)`** — the single writer (extracted 2026-06-19 so dedup lives in one place).

Refactor flow: load active cards → group by credential → LOGIN per credential → **for each card**, call `CARD_USING` with `setCardNum(card.getCardNo())`, read `res.getCardArr().get(0).getCardUsing()`, and persist.

## 3. Difference found

| Aspect | Legacy JEX | Refactor (before fix) |
|---|---|---|
| Attribution driver | Scraped `cardUsing.getCardNum()` per transaction | Loop card `card.getCardNo()` — the scraped `cardNum` was **never read** |
| Stored `CARD_NO` | Transaction's own scraped card | Whatever card the per-card loop was on |
| Unmatched scraped card | `continue` (skip) | N/A — every tx force-attributed to the loop card |
| One tx across N cards | Impossible (point lookup) | **Inserted N times**, once per registered card |
| Dedup key | includes `card_no` | includes `card_no` (same) — so the same merchant under a *different* card_no is **not** a duplicate |

`NxisasCardUsingEntry.cardNum` was mapped from the Coocon wire but had **zero read references** in the whole codebase — the documented "cross-check against the requested card" was never implemented.

## 4. Root cause

`CardTransactionPersister.persist(...)` stamped `row.setCardNo(card.getCardNo())` (the per-card loop's card) for **every** scraped transaction, and never compared the transaction's own `tx.getCardNum()`.

Because a Coocon `CARD_USING` response for a corporate credential returns the **whole credential's activity** in `cardArr[0]` (the per-card loop re-reads the same shared list for each registered card), each transaction was persisted once per card. The dedup probe keys on `card_no`, so the same merchant/date/time/amount under a **different** `card_no` is not seen as a duplicate → 4 registered cards ⇒ 4 inserted rows for one real transaction.

**Confirmed in the refactor DB (2026-07-02):** for tenant `VN_VNM0004_000002`, all 22 distinct `FACEBK *…` merchants appear **identically under all 4 masked cards** (`4696********{4372,3861,8524,1167}`).

Not the cause: masked-number comparison (both sides store the same masked form), missing scrape data (legacy proves Coocon returns `cardNum`), or the dedup key itself.

## 5. Fix implemented

Single, localized change in the shared writer so **both** orchestrators are fixed at once — no controller/UI/modal changes.

**`CardTransactionPersister.persist(...)`** — attribution guard at the top of the per-transaction loop:

```java
// A CARD_USING response can carry transactions for MORE than the requested
// card (Coocon returns the whole credential in cardArr[0]). Attribute each
// scraped row to the card it actually belongs to; drop rows for a different
// card — they land during THAT card's own loop iteration.
// Legacy parity: fnsh_0001_001_c001_act.jsp:1327-1330 (+ :1428 skip).
if (!cardNoMatches(tx.getCardNum(), card.getCardNo())) {
    skipped++;
    continue;
}
```

New helper `cardNoMatches(scraped, registered)`:
- Compares the scraped `cardNum` against the registered `CARD_MSTR.card_no`, ignoring whitespace / hyphens / case (mask asterisks kept — both sides store `4696********NNNN`).
- **Blank scraped `cardNum` → match (fallback):** we explicitly requested this card, so an unattributed row is assumed to be its own. This is the one intentional divergence from legacy's skip-on-null lookup — chosen so a Coocon response that ever omits `cardNum` degrades to the prior behaviour instead of silently dropping every row.

The insert still stamps the canonical registry `card.getCardNo()` (equal to the scraped value in the matched case), keeping stored data in the masked registry format.

**Net behaviour:** because the orchestrators iterate **all** active cards, each card's transactions are inserted exactly once (during that card's own loop), and transactions for a card that isn't registered/active are skipped in every loop — identical end-state to legacy.

**Also updated:** `NxisasCardUsingEntry` javadoc (`cardNum` is now a consumed field, not "defence-in-depth only").

### Files changed

| File | Change |
|---|---|
| `wecms-api/src/main/java/com/kosign/wecms/domain/payment/cardtransactions/service/CardTransactionPersister.java` | Attribution guard + `cardNoMatches` / `normCardNo` helpers |
| `wecms-api/src/main/java/com/kosign/wecms/domain/banking/nxisas/gateway/dto/NxisasCardUsingEntry.java` | `cardNum` javadoc updated (now consumed) |
| `wecms-api/src/test/java/com/kosign/wecms/domain/payment/cardtransactions/service/CardTransactionPersisterTest.java` | **New** — regression tests |

No schema, DTO wire-shape, endpoint, entity, or frontend change. Existing card registration flow and Manage Cards behaviour untouched.

## 6. Test cases added

`CardTransactionPersisterTest` (real persister over mocked repos):

1. **4 cards, 1 scraped tx (belongs to 4372), same response returned for every card** → exactly 1 insert, under 4372; the other 3 per-card calls attribute-skip. (`[1,0]`, `[0,1]`, `[0,1]`, `[0,1]`).
2. **Same tx scraped again** (dedup probe true) → 0 inserted, 1 skipped, `saveAndFlush` never called.
3. **Mixed batch (one tx each for 4372/3861/8524/1167)** → each card's persist inserts only its own tx and skips the other 3; 4 rows total, one per card.
4. **Blank scraped `cardNum`** → falls back to the requested card (defensive; documented divergence).
5. **`cardNoMatches` unit** → spacing/case tolerance, mask kept, non-match on different last-4, blank ⇒ match.

Existing `CardTransactionSyncServiceTest` / `CardTransactionScrapeServiceTest` are unaffected — they already set `entry.cardNum == card.cardNo`, so the guard passes.

## 7. Remaining risks

- **Existing corrupted rows are not auto-cleaned.** The already-duplicated rows cannot be de-duplicated with correct attribution automatically, because the copies are byte-identical except `card_no`/`card_seq_no` — the real owning card is unrecoverable from the stored data. Recommend a **PO-approved re-scrape** of the affected window after a targeted delete, run by a DBA. A cleanup **proposal script** (preview + backup + rollback + guarded delete, not executed) is drafted at `Claude Code (AI)/sql/card_transactions_dedup_cleanup_20260702.sql`. Measured scope on the shared dev DB 2026-07-02 (numbers drift as the shared DB is used): tenant `…_000001` = 400 dupe rows, all safe (0 linked/ERP-sent/child); tenant `…_000002` = 164 dupe rows, **all ERP-sent** → 0 auto-safe, needs a separate PO/ERP decision because the duplicates were already pushed downstream.
- **`cardArr[0]`-only read is retained.** Both orchestrators still read only `res.getCardArr().get(0)`. If Coocon ever splits a single card's transactions across multiple `cardArr` entries, entries beyond `[0]` are ignored — but each card is queried separately, so its own call's `[0]` is expected to hold its data. Left as-is (changing it risks the working single-card path); flagged for monitoring.
- **Blank-`cardNum` fallback** intentionally diverges from legacy skip-on-null. If a future Coocon response returns a blank `cardNum` **and** the over-return behaviour, the fallback would reintroduce per-card duplication for those rows. Not observed in practice (legacy relies on `cardNum` being populated); revisit if a blank-`cardNum` response is ever seen in logs.

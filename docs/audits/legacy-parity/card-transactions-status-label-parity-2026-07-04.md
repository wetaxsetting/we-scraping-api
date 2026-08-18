# Audit — Corporate Card Transactions "Status" column parity (refactor vs JEX)

- **System:** Refactor (wecms-api + wecms-web) vs legacy JEX
- **Feature slug:** `card-transactions`
- **Screen:** Payment > Corporate Card > Card Transactions — `/payment/corporate-card/card-transactions`
- **Column audited:** `Status` (legacy WSVC field `STTS` / `상태`)
- **Date:** 2026-07-04
- **Verdict:** **Refactor mislabeled the Status column.** The status **codes** are correct legacy parity; the **display labels** (frontend i18n table) were wrong — code `2` (completed) rendered as "Cancelled" and code `9` (cancelled) as "Error". Root cause: a hand-maintained label table that drifted from the legacy DB function `wb_get_prch_stts_nm`.
- **Resolution (2026-07-04, PO-directed — highest legacy parity):** the status label is now **resolved server-side by calling the legacy DB function `wb_get_prch_stts_nm(PRCH_STTS, LANG_CD)`** through `LegacyFunctionRepository`, exactly like legacy `IDO.CARD_MSTR_R002.xml:73`. The backend ships the localized text as `stts_nm`; the frontend renders it directly. The hand-maintained i18n table (`payment.card_transactions.status.*`) is **removed** so it can never drift again. See §8 (Implementation) below. UAT/PROD run on the live JEX DB where this function is authoritative, so this is byte-for-byte legacy parity including blank-for-unmapped-codes.

---

## 1. What "Status" means

The Card Transactions Status is the **card purchase status** stored on each transaction row in `CARD_TRNS_HSTR.PRCH_STTS`. It is **not** the approval status, not a cancel flag, not the card approval/request status. It is a single-character code scraped from the bank and converted to a localized name at read time.

Two *other* status-like concepts on this screen are unrelated to the Status column and were confirmed out of scope:
- The dashboard counters **Approval Requested / Not Requested** are derived from `APPR_SEQ_NO IS [NOT] NULL` (`IDO.CARD_TRNS_COUNT_R001.xml:27-30`), and the grid row lock/highlight also keys off `APPR_SEQ_NO` (`card_0001_001.js:741-743`). These do not feed the Status cell.

---

## 2. Legacy files checked

| File | Role | Status logic |
|---|---|---|
| `WECMS DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp` | List page view | Renders empty table; rows built client-side |
| `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp` | List read action | Calls IDO `CARD_MSTR_R002`; binds `LANG_CD` (line 43/47). Does **not** compute status in Java |
| `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.CARD_MSTR_R002.xml` | List query | **`wb_get_prch_stts_nm(CTH.PRCH_STTS, :LANG_CD) AS STTS`** (line 73) → output `상태 / STTS` (line 196) |
| `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.card_0001_001_r001.xml` | Service contract | Declares output `STTS` (`상태`) at line 100 |
| `WECMS DEV/WECMS_STATIC/web/js/jex/wabooks/card/card_0001_001.js` | List controller | Status cell just echoes the server value — `case 'STTS': … null2void(val.STTS)` (lines 777-780). No client-side derivation |
| `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/fnsh/fnsh_0001_001_c001_act.jsp` | Scrape/insert action | Maps scraped Vietnamese status text → numeric code before insert (lines 1379-1402) |
| DB function `wb_get_prch_stts_nm(p_prch_stts, p_lang_code)` | JEX DEV DB | Code → localized name (queried live via `postgres_dev_server_jexframe`) |

### 2.1 Exact legacy status logic

**Insert side** — `fnsh_0001_001_c001_act.jsp:1379-1402` normalizes the scraped bank status string into a code, per bank:

```java
if ("11704088".equals(bankCd)) {          // Shinhan VN
    switch (purchaseStatus.trim()) {
        case "Chấp nhận":  purchaseStatus = "1"; break;  // Accepted
        case "Hoàn thành": purchaseStatus = "2"; break;  // Completed
        case "Đã hủy":     purchaseStatus = "9"; break;  // Cancelled
        default: /* unchanged */
    }
} else if ("11704020".equals(bankCd)) {    // Woori VN
    switch (purchaseStatus.trim()) {
        case "Giao dịch được duyệt": purchaseStatus = "1"; break;  // Approved
        default: /* unchanged */
    }
}
```

**Read/display side** — `IDO.CARD_MSTR_R002.xml:73` calls the DB function; its verbatim body (from JEX DEV):

```sql
CASE
  WHEN '1' = p_prch_stts THEN VI 'Chấp nhận'  / EN 'Approved'  / KO '승인됨'
  WHEN '2' = p_prch_stts THEN VI 'Hoàn thành' / EN 'Acquired'  / KO '완료됨'
  WHEN '9' = p_prch_stts THEN VI 'Đã hủy'     / EN 'Cancelled' / KO '취소됨'
  ELSE NULL
END
```

So the authoritative code → label map is:

| Code | Meaning | VI | EN | KO |
|---|---|---|---|---|
| `1` | Accepted/Approved | Chấp nhận | **Approved** | 승인됨 |
| `2` | Completed | Hoàn thành | **Acquired** | 완료됨 |
| `9` | Cancelled | Đã hủy | **Cancelled** | 취소됨 |
| other | (unmapped) | NULL (blank) | NULL | NULL |

- **No SQL CASE in the list query, no cancel flag column, no approval-status function feeds the Status cell.** The only mechanism is the `wb_get_prch_stts_nm(PRCH_STTS, LANG_CD)` DB function.

### 2.2 Live JEX DEV data distribution

`SELECT PRCH_STTS, COUNT(*) FROM CARD_TRNS_HSTR GROUP BY PRCH_STTS`:

| `PRCH_STTS` | Rows | Legacy label (EN) |
|---|---|---|
| `2` | **216** | Acquired (completed) |
| `1` | 43 | Approved |
| `9` | 3 | Cancelled |
| `Giao dịch chưa hoàn tất` | 6 | (unmapped → blank) |

The overwhelming majority (216) are code `2` = **completed** transactions.

---

## 3. Refactor files checked

### Backend (wecms-api) — CORRECT, no change needed
| File | Behavior |
|---|---|
| `domain/payment/cardtransactions/service/CardTransactionPersister.java:231-247` (`mapPurchaseStatus`) | Maps scraped VI text → code `1`/`2`/`9` — **matches legacy `fnsh_0001_001_c001_act.jsp` exactly** |
| `domain/payment/cardtransactions/entity/CardTrnsHstr.java:110` | `PRCH_STTS VARCHAR(50)` — raw code |
| `domain/payment/cardtransactions/service/CardTransactionsService.java:2424-2425, 2557-2562` | `toRow` returns `stts` (raw code) + `sttsKey` = `"payment.card_transactions.status." + code`. No interpretation — correct |
| `domain/payment/cardtransactions/dto/CardTransactionRow.java:53-54` | Wire fields `stts` + `stts_key` |

The backend faithfully reproduces legacy: it stores the same codes and hands the frontend the code + an i18n key. The refactor's design choice was to move the `wb_get_prch_stts_nm` code→label resolution out of the DB function and into the frontend i18n table (`payment.card_transactions.status.<code>`).

### Frontend (wecms-web) — WRONG (root cause)
| File | Behavior |
|---|---|
| `components/payment/corporate-card/cardTransactionMappers.ts:151-152` | Maps wire `stts` → `status`, `stts_key` → `statusKey` |
| `components/payment/corporate-card/CardTransactionsTable.tsx:532-542` | Renders `t(row.statusKey)`; falls back to raw `status` when the key has no locale entry |
| `locales/en.json:2075-2077` | **`2` → "Cancelled", `9` → "Error"** ❌ |
| `locales/ko.json:1897-1899` | **`2` → "취소", `9` → "오류"** ❌ |
| `locales/vi.json:1888-1890` | **`2` → "Đã hủy", `9` → "Lỗi"** ❌ |

---

## 4. Mismatch / root cause

The refactor moved code→label resolution to the frontend i18n table but **transcribed the map wrong** — it shifted the meanings:

| Code | Legacy (`wb_get_prch_stts_nm`) EN | Refactor `en.json` (before fix) | Correct? |
|---|---|---|---|
| `1` | Approved | Approved | ✅ |
| `2` | **Acquired** (completed) | **Cancelled** | ❌ |
| `9` | **Cancelled** | **Error** | ❌ |

Because 216 of ~268 live rows carry code `2` (completed), the grid shows "Cancelled" on almost every row — exactly the reported symptom. Real cancellations (code `9`, 3 rows) show "Error". The label table and the writer (`mapPurchaseStatus`, which correctly treats `2`=completed / `9`=cancelled per legacy) disagree on what `2` and `9` mean; nothing reconciled them, so the two drifted.

---

## 5. Fix options considered

Two options were on the table (see §6 for the trade-offs):

- **A. Backend calls `wb_get_prch_stts_nm` (chosen).** Highest parity, single source of truth, drift-proof. Reintroduces a legacy DB-function dependency (acceptable — same precedent as Contacts `wb_get_napas_bank_nm_or_citad_bank_nm` and the report functions; routed through the funnel).
- B. Keep the corrected frontend i18n table. No contract change, but leaves a hand-maintained duplicate that already drifted once.

Given UAT/PROD run on the live JEX DB (where the function is authoritative), the PO chose **Option A**.

---

## 6. Risk notes

- **DB-function dependency:** Option A reintroduces a legacy PL/pgSQL call, against the refactor's default "prefer Java/frontend" stance. Mitigated by routing through the single funnel (`LegacyFunctionRepository`) and the established precedent (Contacts bank-name resolver, report functions). Retire when the purchase-status code space is modelled as a refactor enum.
- **Wire-contract change:** `stts_key` → `stts_nm` on the wire. Paired frontend change shipped in the same set (full-stack ownership). Raw `stts` code retained.
- **Unmapped codes** (e.g. the 6 live `Giao dịch chưa hoàn tất` rows): the DB function returns NULL → the cell renders blank, exactly like legacy. This now **matches** legacy (the old i18n fallback showed the raw string — that divergence is closed).
- **Language:** bound from `TenantContext.currentLangCd().name()` (`EN`/`KO`/`KM`/`VI`), same as legacy `LocaleHelper.getLangCd(util)`. `KM` returns NULL from the function (no branch) → blank, legacy parity.
- **Performance:** one funnel call per **distinct** code per request (≤ ~4), request-scoped — not per row. No N+1.
- No data migration impact — codes unchanged; only the resolution site moved (frontend i18n table → DB function).

---

## 7. Implementation (Option A)

**Backend (wecms-api):**
- `LegacyFunctionRepository.resolvePurchaseStatusName(prchStts, langCd)` — new funnel method → `SELECT wb_get_prch_stts_nm(?, ?)`. Null-safe; blank result → null.
- `CardTransactionsService.resolvePurchaseStatusNames(rows)` — resolves `Map<code, name>` once per distinct code using `TenantContext.currentLangCd()`; passed into `toRow`. Removed `resolveSttsKey` + `STTS_KEY_PREFIX`.
- `CardTransactionRow` — field `sttsKey` → `sttsNm` (localized text; wire `stts_nm`). Raw `stts` retained.
- Tests: `CardTransactionsServiceTest` (mock `LegacyFunctionRepository`, assert `sttsNm()`), `CardTransactionsControllerTest` (assert `stts_nm`).

**Frontend (wecms-web):**
- Wire type `ApiCardTransactionRow.stts_key` → `stts_nm`; in-memory `CardTransaction.statusKey` → `statusName`; mapper reads `api.stts_nm`.
- `CardTransactionsTable` + `CardTransactionsPage` (print) render `row.statusName ?? row.status ?? ""` (no `t()`).
- Removed the 3 `payment.card_transactions.status.*` keys from `en.json` / `ko.json` / `vi.json` (dead — server resolves the label now).

---

## 8. Test plan

1. **Backend** — `./gradlew test --tests "*CardTransactions*"` (service + controller).
2. **Frontend** — `tsc --noEmit` + `npm run lint`.
3. **Live smoke** at `/payment/corporate-card/card-transactions`:
   - Rows with `stts=2` (majority) show **Acquired** (EN) / **완료됨** (KO) / **Hoàn thành** (VI).
   - Rows with `stts=9` show **Cancelled**; `stts=1` show **Approved** / **승인됨** / **Chấp nhận**.
   - The 6 `Giao dịch chưa hoàn tất` rows render **blank** (legacy parity).
4. **Locale switch** — toggle EN/KO/VI; confirm the server-resolved label changes with `Accept-Language`.
5. **Regression** — Approval Requested / Not Requested counters (driven by `APPR_SEQ_NO`) unaffected.

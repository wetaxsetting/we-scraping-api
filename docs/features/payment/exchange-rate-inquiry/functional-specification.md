# Exchange Rate Inquiry — Functional Specification

What the feature does, the business rules behind each control, and how the read endpoint
behaves. Backend view; the visual/UX spec lives in the WEB
[`ui-specification.md`](../../../../../wecms-web/docs/features/payment/exchange-rate-inquiry/ui-specification.md).

---

## 1. Filters

| Filter | Wire param | Behaviour |
|---|---|---|
| Reference date | `ref_date` | YYYYMMDD; blank → today in `Asia/Ho_Chi_Minh`. Selected via the shared `<DateField>` calendar. |
| Bank | `bank_cd` | source bank; blank → `11704088` (Shinhan). Driven by the `/banks` endpoint, falls back to the static Shinhan/Woori list. |
| Sequence No. / Notification Round | `seq_no` | batch round; "1" morning, "2" afternoon (Shinhan) or HH:MM:SS [Nth] (Woori); omit → latest per currency. |
| Currency search | (client-side) | filters the rendered matrix by currency code substring; never sent to the API. |

## 2. Bank selector

- Real dropdown (not a cycle toggle — the earlier cycle-toggle gap is resolved).
- Options come from `GET /api/v1/banking/exchange-rates/banks` → `[{ bank_cd, bank_nm }]`,
  tenant-scoped. Frontend falls back to the static `{Shinhan 11704088, Woori 11704020}` list
  when the tenant has no rows yet (legacy `ALLOW_BANKS` parity).
- Selecting a bank re-fetches rates for that `bank_cd`.

## 3. Currency selector / display set

- `GET /api/v1/banking/exchange-rates/currencies` returns the distinct **display** currency
  codes (the foreign side, normalised by a CASE so Shinhan `ccy_cd` and Woori `ref_ccy_cd`
  both resolve to the foreign currency).
- The frontend renders a **fixed legacy 12-currency grid** (USD, KRW, JPY, EUR, AUD, GBP,
  SGD, HKD, CAD, CHF, THB, NZD), showing `-` where a rate is missing — it never omits a
  supported currency, matching legacy `exch_0001_001` `renderTbody`. The API may return extra
  codes (MYR, TWD, VND, CNY) which the page filters out.

## 4. Compare-bank

- A "Compare with" checkbox enables a second bank dropdown.
- When enabled, the frontend fires a **second** `fetchExchangeRates({ bankCd: compareBankCd, seqNo })`
  call for the **same** selected round, and renders a 13-column matrix:
  `[Bank1 | Bank2 | Diff]` per rate type (4 rate types × 3 + 1 currency column).
- The Diff cell shows `Bank1 − Bank2`, formatted positive / `(parenthesised)` negative /
  `0.00` zero / `-` when either side is null/zero.
- Backend has no compare-specific endpoint — compare is two independent calls to the same
  `GET /exchange-rates`.

## 5. Sort

- `sort_field` ∈ `CURRENCY | CASH_BUY | CASH_SELL | TT_BUY | TT_SELL`; `sort_direction` ∈ `ASC | DESC`.
- Backend whitelist: `ExchangeRateSortField` — unknown/blank `sort_field` → default order
  (alphabetical by currency from the native query's `ORDER BY ccy_cd ASC`), never a 4xx.
- Sorting is applied **in-memory** on the mapped DTO list (`applySort`) because the read uses
  `DISTINCT ON` native queries that can't take a Spring Data `Sort`.
- Null rate values **always sort last**, regardless of direction — matching the frontend's
  `-` placeholder rendering.
- `CURRENCY` sorts by the resolved **display** currency (`refCcyCd` when `ccyCd='VND'`, else
  `ccyCd`), not the raw `ccyCd`.
- The frontend also runs a client-side sort over the merged matrix so primary + compare
  columns stay aligned (rate columns sort by the primary bank value).

## 6. Validation

| Param | Constraint | Code on failure |
|---|---|---|
| `ref_date` | `@Pattern("^[0-9]{8}$")` (YYYYMMDD) | `WBE_VALIDATION` (200, envelope) |
| `bank_cd` | `@Size(max = 20)` | `WBE_VALIDATION` |
| `seq_no` | `@Pattern("^[0-9]{1,2}$")` | `WBE_VALIDATION` |
| `sort_field` / `sort_direction` | no `@Pattern` (validated by enum `parse` → graceful fallback) | — |

Controller is `@Validated`; `ConstraintViolationException` maps to `WBE_VALIDATION` via the
global handler.

## 7. Business rules

- **Tenant isolation** — every read filters on `BIZ_KEY = TenantContext.currentBizKey()`. A
  tenant with no rows must see an empty result, never another company's rates.
- **Base-currency filter** — read queries pin `ccy_cd = 'VND'`; the foreign currency is the
  `ref_ccy_cd` discriminator. This matches the Shinhan/Woori VN data shape.
- **Latest-round semantics** — omitted `seq_no` returns the highest `seq_no` per currency pair
  (`DISTINCT ON ... CAST(seq_no AS integer) DESC`). This is the refactor's equivalent of the
  legacy IDO's `SEQ_NO='2'`-prefer / `'1'`-fallback rule.
- **On-demand fetch** — only fires when `rows` is empty AND no explicit `ref_date` was passed.
  Runs the batch in a fresh transaction (REQUIRES_NEW), re-fetches, then falls back to the
  most-recent available date. An explicit `ref_date` with no data returns an empty list (no
  fallback, no fetch).
- **Read vs scrape** — the read endpoint serves the stored snapshot; live scraping is the
  batch's job. The displayed rate is as current as the last successful batch round.
- **Scheduling** — batch is OFF by default; in coexistence the legacy batch owns collection.

## 8. Response shape (functional)

`ExchangeRateListResponse`:
- `ref_date`, `bank_cd`, `bank_nm` — the resolved (possibly fallback) context. Never null.
- `rates[]` — one row per counter-currency; all amount fields nullable.
- `rounds[]` — `(seq_no, last_update_time)` pairs for the round dropdown.

Full field list + JSON examples in [interface-specification.md](interface-specification.md).

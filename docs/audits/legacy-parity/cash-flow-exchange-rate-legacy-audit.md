# Cash Flow — Exchange Rate Legacy Audit (Legacy vs Refactor)

- **Date:** 2026-07-02
- **Author:** Claude Code (audit requested by Sonny)
- **System:** Refactor parity audit (legacy JEX `cash_0001_001` vs `wecms-web` `/banking/reports/cash-flow` + `wecms-api` `cash-flow-report`)
- **Symptom:** Refactor Exchange Rate input shows **26.000** (static) while legacy shows **26.455** (live Shinhan USD sell rate). Verified 2026-07-02 against JEX DEV DB: today's Shinhan row is `TRAN_SELL_AMT = 26455` — the legacy screenshot value exactly.
- **Verdict:** Refactor **never successfully loads the rate from DB**. The seeding effect exists but its currency match is wrong (checks `ccy_cd` instead of `ref_ccy_cd`), so the hardcoded default `26000` always survives. Three further parity divergences found (rate column, display/override semantics, permission gate).
- **Status:** §8 steps 1–2 (frontend) **IMPLEMENTED 2026-07-02** (approved by Sonny) — see §8a for the implementation + verification record. Step 3 (authz widening, Divergence D) and step 4 doc-comment cleanups remain **OPEN**.

---

## 1. Legacy file map (`cash_0001_001`)

| Artifact | Path | Role |
|---|---|---|
| View JSP | `WECMS DEV/WECMS/web/view/jex/wabooks/cash/cash_0001_001_view.jsp` | Page markup — currency dropdown default `VND` (`_view.jsp:321-337`), editable `#exchange-rate` input (`_view.jsp:313-319`) |
| TypeScript | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/cash/cash_0001_001.ts` | All page logic — `fn_getExchRate` (`:247-285`), `fn_loadData` (`:314-360`), currency-change handler (`:114-146`) |
| Report action | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/cash/cash_0001_001_r001_act.jsp` | Cash Flow data — calls IDO `CASH_FLOW_R001` |
| Exchange-rate action | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/cash/cash_0001_001_r002_act.jsp` | Rate lookup — calls IDO `BANK_EXCH_RATE_INFM_R002` |
| Bank-list action | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/cash/cash_0001_001_r003_act.jsp` | Bank/account checkbox list |
| Rate IDO | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BANK_EXCH_RATE_INFM_R002.xml` | The exchange-rate SQL (full text in §3) |
| Report IDO | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.CASH_FLOW_R001.xml` | `SELECT … FROM get_cashflow_report(:BIZ_KEY, :DATA_YEAR::int, :YEAR_QUAT, :BASE_CCY_CD, :USD_RATE_AMT::numeric, :KRW_RATE_AMT::numeric, :BANK_FILT)` |

## 2. Legacy request/response flow

### 2.1 On page load (`onload`, `cash_0001_001.ts:51-59`)

1. `fn_getExchRate()` → POST `cash_0001_001_r002.jct` with `{ BANK_CD: '11704088', SEQ_NO: '2' }`.
   - **`BANK_CD` is hardcoded to Shinhan Bank (`11704088`)** — comment `//shinhan bank` at `cash_0001_001.ts:249`. The bank filter dropdown has **no effect** on the rate lookup.
   - `SEQ_NO: '2'` is sent and validated as required by the act (`_r002_act.jsp:27`) but is **NOT bound into the SQL** — the IDO computes SEQ_NO itself (§3). The parameter is effectively decorative.
2. Response `REC_OUT` = up to 2 rows (USD + KRW). TS stores (`cash_0001_001.ts:264-281`):
   - `USD_RATE = { buy: TRAN_BUY_AMT, sell: TRAN_SELL_AMT }` from the row where `REF_CCY_CD === "USD"`
   - `KRW_RATE = { buy: TRAN_BUY_AMT, sell: TRAN_SELL_AMT }` from the row where `REF_CCY_CD === "KRW"`
3. Input populated: `$("#exchange-rate").val(USD_RATE.sell)` (`cash_0001_001.ts:283`) — **always the USD TRAN_SELL_AMT**, regardless of the currency dropdown. This is the `26455` → displayed `26.455` (id-ID dot-thousand mask).
4. `fn_listBank()` (r003), year dropdown build, then `renderTable()` → `fn_loadData()` (r001) after a 1 s delay.

### 2.2 On currency dropdown change (`cash_0001_001.ts:114-146`)

- Updates `#currency` value + `data-ccy` and the flag icon; **does NOT refetch the exchange rate and does NOT change the `#exchange-rate` input value**.
- Sets a display-format flag `selectedCcy` (VND/KRW → `"VND"` grouping, else `"USD"`; note the `"KWR"` typo at `:125` — harmless, falls into the same branch as intended only for VND).
- Calls `renderTable()` → refetches the **report** (r001) with the new `BASE_CCY_CD`.

### 2.3 On Search click / year change

- `#search` click → `renderTable()` → `fn_loadData()` (r001). **The exchange rate is never refetched after page load** — r002 fires exactly once per page view.

### 2.4 Report request parameters (`fn_loadData`, `cash_0001_001.ts:314-360`)

| Param | Source |
|---|---|
| `DATA_YEAR` | year dropdown (`data-year`) |
| `YEAR_QUAT` | always `null` (quarter is client-side column hiding only) |
| `BASE_CCY_CD` | currency dropdown (`data-ccy`, default `VND`) |
| `USD_RATE_AMT` | **`#exchange-rate` input value if non-empty** (`formatAmtForsave(value, "VND")` strips the mask), else `USD_RATE.sell` from r002. → The input is a **user override of the USD rate only**, whatever base currency is selected. |
| `KRW_RATE_AMT` | **always `KRW_RATE.sell` from r002** — never user-editable in the UI. |
| `BANK_FILT` | JSON array `[{BANK_CD, ACCT_NO},…]` of checked banks, or `null` |

### 2.5 Input editability

- `#exchange-rate` is a plain editable text input with `mask_amt` (`_view.jsp:313-319`, placeholder "VND"). User-entered value **overrides the DB USD rate** at search time (§2.4). Legacy never writes the user value back to the DB.

## 3. Legacy DB logic (`IDO.BANK_EXCH_RATE_INFM_R002.xml`)

```sql
SELECT BANK_CD, REF_DATE, REF_CCY_CD, CCY_CD,
       TRAN_SELL_AMT, TRAN_BUY_AMT, BUY_AMT, SELL_AMT
FROM BANK_EXCH_RATE_INFM
WHERE BIZ_KEY = :BIZ_KEY
  AND BANK_CD = :BANK_CD                        -- always '11704088' (Shinhan) from TS
  AND REF_CCY_CD IN ('USD','KRW')
  AND REF_DATE = (
        SELECT MAX(REF_DATE) FROM BANK_EXCH_RATE_INFM
        WHERE BIZ_KEY = :BIZ_KEY AND BANK_CD = :BANK_CD
          AND REF_CCY_CD IN ('USD','KRW')
          AND TO_DATE(REF_DATE,'YYYYMMDD') <= CURRENT_DATE
          AND EXTRACT(DOW FROM TO_DATE(REF_DATE,'YYYYMMDD')) BETWEEN 1 AND 5 )  -- weekdays only
  AND SEQ_NO = COALESCE(
        (SELECT '2' FROM BANK_EXCH_RATE_INFM
         WHERE …same date… AND SEQ_NO = '2' LIMIT 1),
        '1')                                    -- prefer round 2 (afternoon), fallback round 1
```

**Confirmed answers to the audit questions:**

| Question | Answer (from code + JEX DEV DB verification 2026-07-02) |
|---|---|
| Table | `BANK_EXCH_RATE_INFM` |
| Currency direction | Foreign currency in **`REF_CCY_CD`** (`USD` / `KRW`); **`CCY_CD` = `VND`** (quote currency) on every row. Rates are VND-per-unit (USD ≈ 26 455, KRW ≈ 17.21). |
| Column displayed | **`TRAN_SELL_AMT` of the USD row** (26455 → "26.455"). NOT `SELL_AMT` (26465). Both USD and KRW use `TRAN_SELL_AMT` (sell) for the report call; `TRAN_BUY_AMT` is captured but unused. |
| Bank | **Hardcoded Shinhan `11704088`** — never the selected bank(s), never Woori. |
| SEQ_NO priority | **'2' if a round-2 row exists for the resolved date, else '1'.** (The TS-sent `SEQ_NO=2` is not used by the SQL.) |
| Date rule | **Latest `REF_DATE` ≤ today that falls on a weekday (DOW 1–5)** — weekend/holiday-gap safe. Not "today or fail". |
| Formatting | Input mask = id-ID style grouping (dot thousands): `26455` → `26.455`. VND/KRW amounts render 0 dp, USD 2 dp in the grid (`_osmc.ui.formatAmount` + `selectedCcy` flag). |
| Refetch triggers | Rate fetched **once on page load only**. Currency change / year / bank / Search never refetch it. |
| Report function | `get_cashflow_report(p_biz_key, p_year, p_quarter, p_base_currency, p_usd_rate, p_krw_rate, p_bank_filters)` — receives BOTH rates on every call; `BASE_CCY_CD` only selects the conversion target. |

**DB verification (JEX DEV, `BANK_CD='11704088'`, `REF_DATE=20260702`, `SEQ_NO='2'`):**

| REF_CCY_CD | CCY_CD | TRAN_SELL_AMT | TRAN_BUY_AMT | SELL_AMT | BUY_AMT |
|---|---|---|---|---|---|
| USD | VND | **26455** ← legacy screen "26.455" | 26105 | 26465 | 26095 |
| KRW | VND | **17.21** ← legacy `KRW_RATE_AMT` | 16.75 | 17.48 | 16.13 |

The refactor dev DB (`172.168.5.172/wecmsdevdb`) holds the same shape and values for `VN_VNM0004_000001` (plus Woori `11704020` rows with multi-round `SEQ_NO` up to 8), so the data needed for parity is already present.

## 4. Legacy defaults on page load

| Element | Default |
|---|---|
| Currency dropdown | **VND** (`_view.jsp:331/335`, `selectedCcy: "VND"` at `cash_0001_001.ts:47`) |
| Exchange Rate input | **USD `TRAN_SELL_AMT`** of latest weekday Shinhan round (today: `26455` → "26.455") |
| Year | Current year; Period "This Year"; Bank "All Banks" |

## 5. Refactor current behavior

### 5.1 Frontend

| Concern | Current refactor behavior | File |
|---|---|---|
| Default filters | `baseCurrency: "VND"`, `usdRate: "26000"`, `krwRate: "19"` — **hardcoded sample values** | [CashFlowPage.tsx:1210-1219](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowPage.tsx#L1210-L1219) |
| Live-rate seeding | `useEffect` on mount calls `fetchExchangeRates()` then `find((r) => r.ccyCd === "USD")` / `"KRW"` and reads `sellAmt` | [CashFlowClient.tsx:109-134](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowClient.tsx#L109-L134) |
| Input semantics | One input mapped by `combinedRate` / `applyCombinedRate`: base=VND → usdRate, base=USD → **krwRate**, base=KRW → usdRate | [CashFlowPage.tsx:296-303](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowPage.tsx#L296-L303) |
| Display format | `formatExchangeRateForDisplay` — strips dots, regroups with dot thousands | [CashFlowPage.tsx:311-326](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowPage.tsx#L311-L326) |
| Report fetch | `fetchCashFlow({year, baseCurrency, usdRate, krwRate, bankFilters})` → `GET /api/v1/banking/reports/cash-flow-report?…usdRate=&krwRate=` | [CashFlowClient.tsx:47-64](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowClient.tsx#L47-L64) |

### 5.2 Backend

- `GET /api/v1/banking/reports/cash-flow-report` (`CashFlowReportController`) — params `year, quarter, baseCurrency, usdRate, krwRate, bankFilters`; `CashFlowReportService.validate` requires positive `usdRate`/`krwRate` (except the base currency's own) mirroring the legacy `validateRequired`. No server-side rate defaulting — **the rate values are entirely frontend-supplied**, same as legacy.
- `CashFlowReportRepository` calls `get_cashflow_report(...)` directly (single funnel) — report math parity is out of scope here and already covered by the 2026-06-13 decision.
- `GET /api/v1/banking/exchange-rates` (`ExchangeRateController` → `ExchangeRateService`) — the rate source the frontend seeds from. Defaults: bank `11704088` (Shinhan, matches legacy), date = today (VN) with on-demand Coocon fetch + latest-date fallback, round = highest `SEQ_NO` per pair (`DISTINCT ON … CAST(seq_no AS integer) DESC`), rows filtered `ccy_cd = 'VND'` so **every returned row carries `ccy_cd='VND'` and the foreign code in `ref_ccy_cd`**.

## 6. Root cause of "26.000"

**Bug A (primary, deterministic).** [CashFlowClient.tsx:115-116](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowClient.tsx#L115-L116):

```ts
const usdRow = res.rates?.find((r) => r.ccyCd === "USD");
const krwRow = res.rates?.find((r) => r.ccyCd === "KRW");
```

The backend query returns only `ccy_cd = 'VND'` rows — the foreign currency is in `ref_ccy_cd`. Both `find`s always return `undefined`, the seeding is a silent no-op, and the hardcoded `usdRate: "26000"` renders as **26.000**. (The Exchange Rate Inquiry page already handles this correctly via its `displayCurrency` helper — `row.ccyCd === "VND" ? row.refCcyCd : row.ccyCd` — at [ExchangeRateInquiryPage.tsx:116-120](../../../../wecms-web/components/payment/overseas-payment/ExchangeRateInquiryPage.tsx#L116-L120). The `ApiExchangeRateRow` doc comment in `lib/types/api.ts:3189-3193` describes the opposite direction and is wrong for the VND-base rows this endpoint returns.)

**Bug B (masked by A).** The effect reads `sellAmt` (`SELL_AMT` = 26465) but legacy displays/sends `TRAN_SELL_AMT` (26455). Once Bug A is fixed, the page would show **26.465** — still ≠ legacy 26.455.

**Divergence C (input semantics).** Legacy's input is *always the USD-rate override* (`fn_loadData` reads it into `USD_RATE_AMT` only; `KRW_RATE_AMT` always comes from DB). The refactor's `combinedRate` maps the input to **krwRate when base=USD**. Besides diverging from legacy, this corrupts decimal KRW rates: `krwRate = "17.48"` passes through `formatExchangeRateForDisplay`, which strips the `.` as a thousands separator → displays "1.748" and searches with `krw_rate=1748` (~100× error).

**Divergence D (permission coupling).** `ExchangeRateController` is gated `@PreAuthorize("@menuAuthz.canAccess('payment-overseas-exchange')")`. A user with Cash Flow access but without the Payment > Exchange Rate Inquiry menu grant gets 403 on the seeding call → silent fallback to `26000` even after Bugs A/B are fixed. Legacy `cash_0001_001_r002` has no such cross-menu gate.

**Note on the "KRW selected" screenshot.** The refactor's default base currency **is** `VND` ([CashFlowPage.tsx:1214](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowPage.tsx#L1214)) — legacy parity. The screenshot shows the dropdown mid-interaction (KRW clicked); there is no wrong-default-currency bug. The wrong part is the rate value, which stays `26.000` for VND and KRW bases alike because of Bugs A/B.

## 7. Gap table — Legacy vs Refactor

| # | Concern | Legacy | Refactor today | Gap |
|---|---|---|---|---|
| G1 | Rate source | `BANK_EXCH_RATE_INFM` via r002 on page load | `GET /banking/exchange-rates` seeding effect — **broken match (`ccyCd` vs `refCcyCd`)** → hardcoded 26000/19 always used | **FIX (Bug A)** |
| G2 | Rate column | `TRAN_SELL_AMT` (USD 26455 / KRW 17.21) | `sellAmt` = `SELL_AMT` (26465 / 17.48) | **FIX (Bug B)** — use `tranSellAmt` |
| G3 | Input meaning | Always USD-rate override; KRW rate never editable, always DB value | Input flips between usdRate/krwRate by base currency; KRW decimal corrupted by dot-grouping parser | **FIX (C)** — input = USD rate always |
| G4 | Bank for rate | Hardcoded Shinhan `11704088` | Backend default is also Shinhan `11704088` ✅ (frontend passes no `bank_cd`) | OK |
| G5 | Round (SEQ_NO) | `'2'` if exists else `'1'` for the resolved date | Highest `SEQ_NO` per pair (`DISTINCT ON`) | Minor — identical for Shinhan (rounds 1–2 only); Woori has rounds up to 8 but legacy never reads Woori. Accept. |
| G6 | Date rule | `MAX(REF_DATE) ≤ today` **weekdays only** | Today (VN) → on-demand Coocon fetch → fallback latest date (no weekday filter) | Minor — refactor's on-demand fetch makes today's row exist in practice; weekend rows (if the batch ever writes them) would be used where legacy skips them. Accept; note for SIT. |
| G7 | Refetch on currency change / Search | Never (once per page view) | Never (mount-only effect) ✅ | OK |
| G8 | Default currency | VND | VND ✅ | OK |
| G9 | Display format | id-ID dot-thousand mask (26.455) | Same dot-grouping (integers only — see G3 for decimals) | OK after G3 |
| G10 | Rate editable + override | Editable; overrides `USD_RATE_AMT` at search | Editable; override wired through filters ✅ (target field wrong per G3) | OK after G3 |
| G11 | Report call params | `get_cashflow_report(bizKey, year, null, baseCcy, usdRate, krwRate, bankFilt)` | Identical funnel via `CashFlowReportRepository` ✅ | OK |
| G12 | Rate-endpoint authz | r002 has no cross-menu gate | `payment-overseas-exchange` menu gate on `/banking/exchange-rates` → 403 for Cash-Flow-only users | **DECIDE (D)** — PO/lead call |

## 8. Fix plan (pending approval — not implemented)

All frontend, one file each; no schema change, no new endpoint, no backend logic change required for the core fix.

1. **[CashFlowClient.tsx](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowClient.tsx) — fix the seeding effect (Bugs A + B):**
   - Match the foreign currency the way Exchange Rate Inquiry does: `const cur = (r) => r.ccyCd === "VND" ? r.refCcyCd : r.ccyCd;` then `find((r) => cur(r) === "USD")` / `"KRW"`.
   - Read **`tranSellAmt`** (fallback `sellAmt` only if `tranSellAmt` is null) → seeds `usdRate = "26455"`, `krwRate = "17.21"` (today's values).
2. **[CashFlowPage.tsx](../../../../wecms-web/components/banking/reports/cash-flow/CashFlowPage.tsx) — restore legacy input semantics (Divergence C):**
   - Delete `combinedRate` / `applyCombinedRate`; bind the input to `filters.usdRate` for every base currency (legacy: the input is the USD-rate override; the KRW rate always rides along from DB).
   - This also removes the decimal-corruption path (the USD rate is a ≥5-digit integer, safe for the dot-grouping formatter). Keep `formatExchangeRateForDisplay` / `parseExchangeRateInput` as-is.
   - Optionally drop the now-unused `"26000"`/`"19"` sample defaults to `""` so a failed seed shows an empty input (legacy behavior when r002 returns nothing) instead of a fake rate — recommend yes.
3. **Decision needed (Divergence D, backend, one line):** either add `banking-reports-cash-flow` to the accepted slugs for the exchange-rate list endpoint (e.g. `canAccess('payment-overseas-exchange') or canAccess('banking-reports-cash-flow')`) or accept that Cash-Flow-only users fall back to an empty rate input. Recommend the widened gate — legacy has no such restriction. **PO/lead approval required** since it touches an authz annotation.
4. **Doc-comment corrections (no behavior):** fix `ApiExchangeRateRow` JSDoc in [lib/types/api.ts:3189-3193](../../../../wecms-web/lib/types/api.ts#L3189-L3193) (ccy_cd/ref_ccy_cd direction) and the stale `CashFlowClient` comment claiming "legacy reads the same source for its rate field … uses sell_amt" (legacy uses `TRAN_SELL_AMT`).

### 8a. Implementation record (2026-07-02 — steps 1–2 done)

Approved and implemented the same day, frontend only:

- **CashFlowClient.tsx** — seeding effect now resolves the foreign currency via `r.ccyCd === "VND" ? r.refCcyCd : r.ccyCd` and reads **`tranSellAmt` only** (Bugs A + B). The `?? sellAmt` fallback initially proposed in step 1 was **removed on Sonny's 2026-07-02 directive** ("Do NOT use SELL_AMT" — legacy never falls back; a missing transfer rate leaves the value unseeded). The rate fetch also **pins `bank_cd = '11704088'` client-side** (`LEGACY_RATE_BANK_CD`), mirroring the legacy hardcode at `cash_0001_001.ts:249` instead of relying on the backend default, and the Excel export now sends the same effective rates as Search (`filters.usdRate || seeded`). Two additions beyond the literal plan, both legacy-parity:
  - **Rate-first sequencing:** the first report load now waits for the rate seed to settle (`ratesReady` flag gating the `useApiData` loader). Without this, the empty-string defaults (step 2) would have made the mount-time report call fail `WBE_VALIDATION` — legacy sequences `fn_getExchRate` before the delayed first `fn_loadData` (`cash_0001_001.ts:51-64`).
  - **Cleared-input fallback:** seeded rates are kept in `seededRatesRef`; at fetch time the wire carries `filters.usdRate || seeded.usd` / `filters.krwRate || seeded.krw`, mirroring legacy `fn_loadData` (`cash_0001_001.ts:329-333` — empty input → DB rate).
- **CashFlowPage.tsx** — input binds to `filters.usdRate` for every base currency; `combinedRate` / `applyCombinedRate` deleted (Divergence C, incl. the decimal-KRW corruption path); sample defaults `26000`/`19` → empty strings.
- **Not changed:** backend / authz (Divergence D still open, G12), `formatExchangeRateForDisplay` / `parseExchangeRateInput`, everything else.

**Verification (2026-07-02):** `npx tsc --noEmit` clean; eslint clean on `CashFlowClient.tsx` (remaining `CashFlowPage.tsx` findings are pre-existing untouched code). Live check against the local backend + shared dev DB with a minted dev JWT (`VN_VNM0004_000001`, ADMIN):

- `GET /api/v1/banking/exchange-rates?bank_cd=11704088` → `ref_date=20260702`, `bank_cd=11704088`, USD row `{ccy_cd:"VND", ref_ccy_cd:"USD", tran_sell_amt:26455, sell_amt:26465}` — the old `ccy_cd==="USD"` match finds **nothing** (bug reproduced), the new match seeds `usdRate=26455`, `krwRate=17.21` from `tran_sell_amt` only.
- Display formatter renders the seeded value as **"26.455"** — legacy parity.
- `GET /api/v1/banking/reports/cash-flow-report?year=2026&baseCurrency=VND&usdRate=26455&krwRate=17.21` (the exact Search wire) → HTTP 200, `WBI0000`, 27 rows.
- Currency-switch stability is structural: the currency `FilterSelect` `onChange` sets only `baseCurrency`, and the input value depends only on `filters.usdRate` — no `baseCurrency` term remains in the binding. (Browser click-through still worth an eyeball in the next manual pass.)

### Tests to add/update

- **Frontend:** none exist for this page today; at minimum add a unit test for the row-matching helper (VND-base row → USD/KRW resolution + `tranSellAmt` preference) if a test harness is introduced. Otherwise verify manually (see below).
- **Backend:** no change to `CashFlowReportService` — existing validation tests stand. If the authz widening (step 3) is approved, extend `EndpointAuthorizationCoverageTest` expectations accordingly.
- **Manual verification script:** load `/banking/reports/cash-flow` against the shared dev DB and confirm the input shows **26.455** with VND selected (matching `dev.wecms.vn/cash_0001_001.act` same-day), that switching to USD/KRW does not change the input value, and that Search sends `usd_rate=26455&krw_rate=17.21`.

## 9. Related docs

- Feature docs: [wecms-web/docs/features/banking/reports/cash-flow/](../../../../wecms-web/docs/features/banking/reports/cash-flow/) (legacy-spec.md, api-consumption.md)
- Report-function decision: `CashFlowReportRepository` Javadoc (2026-06-13 — DB function is source of truth); memory `project_cash_flow_report_logic`
- Sibling audit precedent: [daily-financial-hs-legacy-audit.md](daily-financial-hs-legacy-audit.md)

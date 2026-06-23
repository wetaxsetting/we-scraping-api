# Exchange Rate Inquiry — Business Flow

End-to-end behaviour across the three layers. The **write path** populates
`BANK_EXCH_RATE_INFM` from the Coocon / NXISAS gateway; the **read path** serves the stored
snapshot to the page. Rates are **read** from a daily-collected snapshot, not **scraped**
live on page load.

---

## 1. Legacy (JEX)

**Write — batch jobs (BCS):**

- `bcs.jex.wabooks.exch.EXCH_0001_001` — Shinhan first-time, once/day ~08:30 ICT, writes `SEQ_NO=1`.
- `EXCH_0010_001` — Shinhan last-time, hourly 08:30–18:30 ICT; preserves `SEQ_NO=1`, UPSERTs `SEQ_NO=2`.
- `EXCH_0020_001` — Woori, every 30 min 08:30–20:30 ICT; INSERTs a fresh round `SEQ_NO = MAX+1`.

Each job calls the Coocon smart-gateway (`Job = FOREIGN_EXCHANGE`, per-bank Module/Class),
then writes one row per (tenant × counter currency) into `BANK_EXCH_RATE_INFM`.

**Read — `pymn_0010_002`:**

- The page reads stored rows via IDO `BANK_EXCH_RATE_INFM_R002`.
- The IDO two-tier sequence rule: find the most recent weekday `REF_DATE` ≤ today, then
  prefer `SEQ_NO='2'` (afternoon) and fall back to `SEQ_NO='1'`. It restricts
  `REF_CCY_CD IN ('USD','KRW')` and excludes weekends.
- There is **no JEX view service** for the read path — the page binds the IDO directly.

## 2. Spring (refactor backend)

### Read path — `ExchangeRateService.list(refDate, bankCd, seqNo, sortField, sortDirection)`

1. **Tenant scope** — `bizKey = TenantContext.currentBizKey()` (from the signed JWT).
   `BANK_EXCH_RATE_INFM` is partitioned per tenant (`BIZ_KEY` is part of the PK); the batch
   fans the same Coocon rates to every active company and each reads only its own copy. A
   tenant with no rows sees an empty result, never another company's rates.
2. **Resolve date** — `resolveDate(refDate)`: blank → `LocalDate.now(Asia/Ho_Chi_Minh)` in
   `yyyyMMdd`. The Vietnam timezone matches the calendar day the batch stamps into
   `REF_DATE` (the earlier "JVM default TZ" gap is **resolved**).
3. **Resolve bank** — blank → `DEFAULT_BANK_CD = "11704088"` (Shinhan).
4. **Fetch rows** — `fetchRows(...)` picks the repository query:
   - `seqNo` supplied → `findLatestPerCurrencyByBizKeyAndBankCdAndRefDateAndSeqNo` (exact round).
   - `seqNo` omitted → `findLatestPerCurrencyByBizKeyAndBankCdAndRefDate` (DISTINCT ON, highest `seq_no` per currency pair = latest round).
   - Both filter `ccy_cd = 'VND'` (the foreign currency lives in `ref_ccy_cd`).
5. **On-demand catch-up** — when `rows` is empty AND no explicit `refDate` was passed,
   `exchangeRateBatchService.runOnDemand()` is invoked (REQUIRES_NEW) to fetch from Coocon,
   then the rows are re-fetched. If still empty, fall back to the most recent available
   `REF_DATE` via `findLatestRefDateByBizKeyAndBankCd`. (The fallback does **not** fire when
   an explicit `ref_date` is supplied — that path returns an empty list.)
6. **Bank name** — `bankCdResolver.resolveBankNm(resolvedBank)` from `BANK_INFM`.
7. **Map + sort** — rows → `ExchangeRateRow` records via `toRow`; `applySort` sorts in-memory
   through the `ExchangeRateSortField` whitelist (nulls always last; unknown field → DB order).
8. **Rounds** — `findDistinctRoundsByBizKeyAndBankCdAndRefDate` returns the
   `(seq_no, last_update_time)` pairs that drive the round dropdown.
9. Return `ExchangeRateListResponse(refDate, bankCd, bankNm, rates, rounds)`.

### Bank / currency selectors

- `listBanks()` → `findDistinctBankCdByBizKey` → `[{ bank_cd, bank_nm }]`.
- `listCurrencies()` → `findDistinctDisplayCurrencyByBizKey` (CASE normalises foreign side).

### Write path — `ExchangeRateBatchService`

Three trigger methods + one on-demand entry point, each preserving its legacy persistence mode:

| Trigger | Legacy job | Persistence mode |
|---|---|---|
| `runShinhanFirstTime()` | EXCH_0001_001 | UPSERT (`SEQ_NO=1`) |
| `runShinhanLastTime()` | EXCH_0010_001 | SKIP_IF_EXISTS (`SEQ_NO=1`) + UPSERT (`SEQ_NO=2`) |
| `runWoori()` | EXCH_0020_001 | INSERT_NEW (`SEQ_NO = MAX+1`, probed on `ccy_cd='VND'`) |
| `runOnDemand()` | all three, sequentially | REQUIRES_NEW; called by the read path on an empty day |

- Tenant fan-out filter (legacy `BUSI_INFM_R001` parity): `del_yn='N'` AND `busi_stts='N'`
  (NORMAL) AND `prdt_type IN ('F','M','H')` via `findByDelYnAndBusiSttsAndPrdtTypeIn`.
- The gateway adapter success code is strict `"00000000"` (no `"0000"`/`"OK"` short forms).
- `EXCH_SEQ_NO` (the PK auto-increment column) is allocated through the single
  `wb_get_next_val` funnel (`LegacyFunctionRepository.nextBankExchRateExchSeqNo`,
  per `(biz_key, bank_cd, ref_date, ccy_cd)` MAX+1). The data-column `SEQ_NO` ("1"/"2"/round) is
  set from the batch round, **not** funnelled.
- **Scheduling is OFF by default** — `ExchangeRateBatchService` holds no `@Scheduled`
  annotations; the gated `ExchangeRateBatchScheduler` only registers when
  `wecms.exchange-rate.batch.enabled=true`. In the live-JEX coexistence model the legacy
  batch owns FX collection, so the refactor must not auto-collect by default.

### Read vs scrape

The **read** endpoint never calls the gateway directly on a normal load — it serves the
stored snapshot. The only exception is the **on-demand catch-up** (empty day, no explicit
date), which delegates to the batch service in a fresh transaction. Live scraping is the
batch's job, not the read endpoint's.

## 3. React (frontend)

1. `ExchangeRateInquiryPage` boots with `refDate = todayLocalIso()`, `bankCd = Shinhan`,
   `seqNo = null` (latest).
2. `useApiData(() => fetchExchangeRates({ refDate, bankCd, seqNo, ...sortParams }), [...])`
   fetches on mount and whenever filters / sort change.
3. `fetchExchangeRateBanks()` populates the bank dropdown (falls back to the static
   Shinhan/Woori list when the tenant has no rows).
4. Wire rows are mapped at the boundary (`lib/api/mappers.ts → toExchangeRateListResult`)
   to camelCase; the component builds a **fixed 12-currency** matrix, rendering `-` where a
   rate is missing.
5. When **compare** is enabled, a second `fetchExchangeRates({ bankCd: compareBankCd, seqNo })`
   call builds a 13-column matrix (`Bank1 | Bank2 | Diff` per rate type, same round).
6. Errors surface via `useDialog().open({ type: "error" })`. No silent mock fallback.

See [`../../../../../wecms-web/docs/features/payment/exchange-rate-inquiry/screen-flow.md`](../../../../../wecms-web/docs/features/payment/exchange-rate-inquiry/screen-flow.md).

---

## Data-flow diagram

```
Coocon / NXISAS gateway (FOREIGN_EXCHANGE)
        │  ExchangeRateBatchService  (Shinhan first/last + Woori; REQUIRES_NEW on-demand)
        ▼
BANK_EXCH_RATE_INFM  (PK: BIZ_KEY+BANK_CD+REF_DATE+CCY_CD+EXCH_SEQ_NO; round in SEQ_NO)
        │  BankExchRateInfmRepository  (DISTINCT ON latest round per currency pair)
        ▼
ExchangeRateService.list  →  ExchangeRateListResponse  (rates + rounds)
        │  ApiResponse<...>  (JWT, @PreAuthorize payment-overseas-exchange)
        ▼
GET /api/v1/banking/exchange-rates  →  fetchExchangeRates → toExchangeRateListResult
        ▼
ExchangeRateInquiryPage  (12-currency matrix; optional compare 13-col; "Inquired on" footer)
```

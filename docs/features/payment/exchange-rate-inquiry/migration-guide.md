# Exchange Rate Inquiry — Migration Guide (JEX → Spring)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

How the legacy JEX surfaces map to the refactor, what gaps are resolved, and which
deviations are approved.

---

## 1. Surface-by-surface mapping

| JEX surface | Refactor artifact |
|---|---|
| Menu / WSVC `pymn_0010_002` | authz slug `payment-overseas-exchange`; feature slug `exchange-rate-inquiry`; route `/payment/overseas-payment/exchange-rate-inquiry` |
| Read IDO `BANK_EXCH_RATE_INFM_R002` | `ExchangeRateService.list` → `BankExchRateInfmRepository.findLatestPerCurrencyByBizKeyAndBankCdAndRefDate[AndSeqNo]` |
| Read page `pymn_0010_002` (binds IDO directly; no view service) | `ExchangeRateController.list` + the React `ExchangeRateInquiryPage` |
| Batch `EXCH_0001_001` (Shinhan first) | `ExchangeRateBatchService.runShinhanFirstTime` (UPSERT seq=1) |
| Batch `EXCH_0010_001` (Shinhan last) | `ExchangeRateBatchService.runShinhanLastTime` (skip-if-exists seq=1 + UPSERT seq=2) |
| Batch `EXCH_0020_001` (Woori) | `ExchangeRateBatchService.runWoori` (INSERT_NEW seq=MAX+1) |
| Batch scheduling | `ExchangeRateBatchScheduler` (gated; OFF by default in coexistence) |
| Table `BANK_EXCH_RATE_INFM` | same table (V127 schema, V130 seed) |
| Bank-name lookup | `BankCdResolver` over `BANK_INFM` |
| `wb_get_next_val` for `exch_seq_no` | `LegacyFunctionRepository.nextBankExchRateExchSeqNo` (single funnel) |

## 2. IDO `BANK_EXCH_RATE_INFM_R002` → repository

The legacy IDO's two-tier sequence rule (prefer `SEQ_NO='2'`, fall back to `'1'`) is
reproduced by the refactor's **latest-round** semantics: the DISTINCT ON query orders by
`CAST(seq_no AS integer) DESC` per currency pair, so the highest round wins automatically.
The explicit `seq_no` param lets the page request a specific round (the dropdown). The
legacy IDO's `REF_CCY_CD IN ('USD','KRW')` / weekday restrictions are not reproduced 1:1 —
the refactor returns all currency pairs for the resolved date and lets the frontend filter
to the fixed 12-currency display set.

## 3. Batch `EXCH_*_001` → `ExchangeRateBatchService`

Each legacy job's distinct persistence semantics are preserved as a `PersistMode`:

| Legacy job | Mode | Refactor method |
|---|---|---|
| EXCH_0001_001 | `UPSERT` (seq=1) | `runShinhanFirstTime` |
| EXCH_0010_001 | `SKIP_IF_EXISTS` (seq=1) + `UPSERT` (seq=2) | `runShinhanLastTime` |
| EXCH_0020_001 | `INSERT_NEW` (seq=MAX+1, probed on ccy='VND') | `runWoori` |

Shared gateway-call + persist loop (`fetchAndPersist`) fans rows out per (tenant × counter
currency), keyed on the legacy existence key `biz_key + bank_cd + ref_date + ref_ccy_cd +
ccy_cd + seq_no` (the `ref_ccy_cd` discriminator is mandatory — without it all currencies of
a round collapse onto one row). On-demand catch-up (`runOnDemand`, REQUIRES_NEW) runs all
three sequentially so an empty-day page load returns a populated snapshot in one round-trip.

## 4. Resolved gaps (were open against an earlier build)

| Gap | Earlier state | Now |
|---|---|---|
| Input validation | `ref_date` passed raw | ✅ `@Pattern`/`@Size` on every param + `@Validated` |
| Timezone (`resolveDate`) | `LocalDate.now()` (JVM TZ) | ✅ `LocalDate.now(Asia/Ho_Chi_Minh)` — confirmed in code (`ExchangeRateService` line 43/205) |
| Seq-no preference | repo returned all seq rows | ✅ `seq_no` param + DISTINCT ON latest round; data integrity restored |
| Backend-driven bank list | hardcoded `BANK_OPTIONS` in FE | ✅ `GET /exchange-rates/banks` |
| Backend-driven currency list | hardcoded FE | ✅ `GET /exchange-rates/currencies` |
| Sort | none | ✅ `sort_field` / `sort_direction` + `ExchangeRateSortField` whitelist |
| Compare-bank | UI-only | ✅ real second `fetchExchangeRates` call → 13-column matrix |
| Bank dropdown | cycle toggle | ✅ real popover dropdown |
| Date wiring | hardcoded chip | ✅ shared `<DateField>`, value sent to API |
| FE mapper / formatRate location | in component | ✅ `lib/api/mappers.ts → toExchangeRateListResult`, `lib/currency.ts → formatExchangeRate` |
| FE error handling | silent mock fallback | ✅ `useDialog().open({ type: "error" })`; no mock fallback |

> The root summary doc's §6/§7 "Gap Analysis" subsections were written against the earlier
> build and are superseded. Treat the **Now** column above as authoritative.

## 5. Approved deviations

- **B-4 OpenAPI/Springdoc annotations absent** — `@Tag` / `@Operation` not present.
  **APPROVED DEVIATION**: Springdoc is not wired project-wide; every banking controller
  follows the same convention. Documented in the controller Javadoc. Add when/if Springdoc
  is enabled product-wide.
- **No JEX view service for the read path** — legacy binds the IDO directly; the refactor
  exposes a REST endpoint instead. Expected, not a gap.
- **Most-recent-date fallback** (`findLatestRefDateByBizKeyAndBankCd`) and **on-demand
  catch-up** are refactor additions with no legacy equivalent — they keep dev/UAT usable when
  the batch hasn't run. Documented behaviour, not drift.

## 6. ⚠️ Re-verify (uncertain — needs confirmation)

These should be confirmed against the live build before being treated as closed; the code
read here suggests they are resolved, but the gap register still flags them:

- **B-2 timezone** — code uses `Asia/Ho_Chi_Minh`; confirm no other code path resolves the
  date with the JVM default TZ.
- **Mapper / formatRate location** — confirmed in `mappers.ts` / `currency.ts` from the code
  read; re-verify no stray in-component snake_case reads remain.
- **Error dialog** — wired via `useDialog`; re-verify on a forced 500 / network failure.

## 7. Migration-traceability note

Legacy WSVC id `pymn_0010_002` and IDO/batch ids (`BANK_EXCH_RATE_INFM_R002`,
`EXCH_0001_001` / `EXCH_0010_001` / `EXCH_0020_001`) are preserved here as source-of-truth
pointers and migration prose only. Every new identifier surface (route, REST URI, package,
class, DTO, log tag) uses the kebab-case slug `exchange-rate-inquiry` / authz
`payment-overseas-exchange` per the refactor identifier-naming rule.

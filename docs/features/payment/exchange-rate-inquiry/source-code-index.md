# Exchange Rate Inquiry — Source Code Index

Every file that implements the feature, with line citations validated against the working
tree. Paths are repo-relative; line numbers reflect the build read on 2026-06-23.

---

## Backend (wecms-api)

### Controller

- [`domain/banking/controller/ExchangeRateController.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/controller/ExchangeRateController.java)
  - class `@Validated @RestController @RequestMapping("/api/v1/banking") @PreAuthorize("@menuAuthz.canAccess('payment-overseas-exchange')")` — lines 36–41
  - `list(...)` `GET /exchange-rates` with `@Pattern`/`@Size` params — lines 57–79
  - `listBanks()` `GET /exchange-rates/banks` — lines 88–91
  - `listCurrencies()` `GET /exchange-rates/currencies` — lines 113–116

### Service (read)

- [`domain/banking/service/ExchangeRateService.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ExchangeRateService.java)
  - `DEFAULT_BANK_CD = "11704088"`, `VIETNAM_TZ` — lines 40–45
  - `list(...)` — lines 80–134 (tenant scope, fetch, on-demand catch-up + fallback, map, sort, rounds)
  - `listBanks()` — lines 142–148
  - `listCurrencies()` — lines 154–158
  - `toRow(...)` mapper — lines 164–179
  - `fetchRows(...)` query selector — lines 188–193
  - `resolveDate(...)` (UTC+7) — lines 201–206
  - `applySort(...)` — lines 213–223

### Service (batch write)

- [`domain/banking/service/ExchangeRateBatchService.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ExchangeRateBatchService.java)
  - bank metadata + success code `"00000000"` — lines 110–127
  - tenant fan-out filter (`del_yn='N'`, `busi_stts='N'`, `prdt_type IN (F,M,H)`) — lines 129–139
  - `PersistMode` enum — lines 142–152
  - `runShinhanFirstTime()` — lines 163–167
  - `runShinhanLastTime()` — lines 183–187
  - `runWoori()` (MAX+1 probe; G2 open decision in Javadoc) — lines 234–238
  - `runOnDemand()` (REQUIRES_NEW) — lines 258–266
  - `fetchAndPersist(...)` shared loop (existence key incl. `ref_ccy_cd`) — lines 328–436
  - `EXCH_SEQ_NO` via funnel `nextBankExchRateExchSeqNo` — lines 416–417
  - `activeTenants()` — lines 501–504
- [`domain/banking/service/ExchangeRateBatchScheduler.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ExchangeRateBatchScheduler.java) — gated `@Scheduled`/`@SchedulerLock` wrapper (registers only when `wecms.exchange-rate.batch.enabled=true`)

### Sort whitelist

- [`domain/banking/service/ExchangeRateSortField.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/service/ExchangeRateSortField.java)
  - constants `CURRENCY / CASH_BUY / CASH_SELL / TT_BUY / TT_SELL` — lines 27–33
  - `comparator()` (nulls last) — lines 46–55
  - `parse(...)` (lenient) — lines 62–71
  - `displayCcy(...)` — lines 76–78

### Repository

- [`domain/banking/repository/BankExchRateInfmRepository.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/repository/BankExchRateInfmRepository.java)
  - `findLatestPerCurrencyByBizKeyAndBankCdAndRefDate` (DISTINCT ON) — lines 31–40
  - `findLatestPerCurrencyByBizKeyAndBankCdAndRefDateAndSeqNo` — lines 48–58
  - `findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo` (batch existence key) — lines 71–72
  - `findLatestRefDateByBizKeyAndBankCd` (fallback) — lines 79–81
  - `findDistinctBankCdByBizKey` — lines 89–90
  - `findDistinctDisplayCurrencyByBizKey` (CASE) — lines 102–108
  - `findDistinctRoundsByBizKeyAndBankCdAndRefDate` — lines 116–126
  - `findHighestSeqNo` (Woori round-probe) — lines 154–162

### Entity

- [`domain/banking/entity/BankExchRateInfm.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/entity/BankExchRateInfm.java) — `@Table("BANK_EXCH_RATE_INFM")`, composite `@IdClass`, columns + audit (lines 22–134)
- [`domain/banking/entity/BankExchRateInfmId.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/entity/BankExchRateInfmId.java) — serializable composite key

### DTOs

- [`domain/banking/dto/ExchangeRateListResponse.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/dto/ExchangeRateListResponse.java) — `refDate, bankCd, bankNm, rates, rounds`
- [`domain/banking/dto/ExchangeRateRow.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/dto/ExchangeRateRow.java) — 12 fields (lines 15–52)
- [`domain/banking/dto/ExchangeRateBankItem.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/dto/ExchangeRateBankItem.java) — `bankCd, bankNm`
- [`domain/banking/dto/ExchangeRateRoundItem.java`](../../../../src/main/java/com/kosign/wecms/domain/banking/dto/ExchangeRateRoundItem.java) — `seqNo, lastUpdateTime`

### Key generation

- `common/legacy/LegacyFunctionRepository.nextBankExchRateExchSeqNo(bizKey, bankCd, refDate, ccyCd)` — single `wb_get_next_val` funnel for `EXCH_SEQ_NO`

### Migrations

- `db/migration/V127__bank_exch_rate_infm_table.sql` (header shows draft `V84`) — schema + `IDX_BANK_EXCH_RATE_INFM_BIZ_DATE`
- `db/migration/V130__bank_exch_rate_infm_seed.sql` (header shows draft `V87`) — 15-row demo seed
- `db/migration/V155__wb_get_next_val_centralize.sql` — `bank_exch_rate_infm.exch_seq_no` branch

### Tests

- [`src/test/.../service/ExchangeRateServiceTest.java`](../../../../src/test/java/com/kosign/wecms/domain/banking/service/ExchangeRateServiceTest.java)
- [`src/test/.../service/ExchangeRateBatchServiceTest.java`](../../../../src/test/java/com/kosign/wecms/domain/banking/service/ExchangeRateBatchServiceTest.java)
- [`src/test/.../service/ExchangeRateBatchPersistenceTest.java`](../../../../src/test/java/com/kosign/wecms/domain/banking/service/ExchangeRateBatchPersistenceTest.java)
- [`src/test/.../service/ExchangeRateBatchSchedulerTest.java`](../../../../src/test/java/com/kosign/wecms/domain/banking/service/ExchangeRateBatchSchedulerTest.java)

---

## Frontend (wecms-web)

- Route: `app/(dashboard)/payment/overseas-payment/exchange-rate-inquiry/page.tsx` — thin wrapper around `<ExchangeRateInquiryPage>`
- Component: `components/payment/overseas-payment/ExchangeRateInquiryPage.tsx` — matrix table, filters, compare, sort, "Inquired on" footer
- Types: `components/payment/overseas-payment/types.ts` — `ExchangeRateRow`, `ExchangeRateDisplayRow`, `ExchangeRateListResult`, `ExchangeRateBankOption`
- API client: `lib/api/banking.ts` — `fetchExchangeRates` (line 913), `fetchExchangeRateBanks` (line 940), `fetchExchangeRateCurrencies` (line 956)
- Wire types: `lib/types/api.ts` — `ApiExchangeRateListResponse`, `ApiExchangeRateRow`, `ApiExchangeRateBankItem`
- Mapper: `lib/api/mappers.ts` — `toExchangeRateListResult`
- Formatter: `lib/currency.ts` — `formatExchangeRate`
- Shared: `lib/api/useApiData.ts`, `hooks/useTableSort.ts`, `lib/datetime.ts` (`todayLocalIso`), `components/shared/DateField.tsx`, `components/shared/FilterDropdown.tsx`, `components/shared/dialog` (`useDialog`)
- Frontend tests: **none** (MISSING)

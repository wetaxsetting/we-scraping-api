# HAENGSUNG Exchange-Rate Sync — Backend Feature Spec (Separate ERP feature)

**Slug:** `haengsung-exchange-rate-sync` · **Domain:** Banking/ERP-sync · **Status:** ✅ Implemented (Step 6, backend + unit tests; flag-gated OFF by default) · **Product type:** `H` only · **Last updated:** 2026-06-28

> **Implemented 2026-06-28.** Backend + unit tests landed (see [§8 Implementation](#8-implementation-2026-06-28)). Reuses the Step-1 Oracle datasource + the existing WeCMS `BANK_EXCH_RATE_INFM` source table/entity — no new FX business flow. Legacy source of truth: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_001.java`.

## 1. Purpose

Push WeCMS-collected **foreign-exchange rates** out to the Haengsung Oracle ERP so the ERP has current FX rates. Pure outbound sync; no payment data, no `TB_TRNS_*`.

## 2. Direction

**WeCMS → Oracle** (one-way push).

## 3. Tables

- **Oracle target:** `TB_FCY_EXRATE` (legacy IDO `TB_FCY_EXRATE_U001` update / `TB_FCY_EXRATE_C002` insert).
- **WeCMS source:** `BANK_EXCH_RATE_INFM` (read via `BANK_EXCH_RATE_INFM_R003`; sent-flag updated via `BANK_EXCH_RATE_INFM_U003`).

## 4. Flow

1. Select tenants `BUSI_INFM WHERE prdt_type='H' AND del_yn='N'`.
2. For each, read today's `BANK_EXCH_RATE_INFM` rows (`ref_date = today`, `coalesce(erp_snd_yn,'N') in ('Y','N')`).
3. Per rate row (own transaction): **upsert** `TB_FCY_EXRATE` — `UPDATE` (`TB_FCY_EXRATE_U001`) when the WeCMS row's `erp_snd_yn='Y'` (already sent before), else `INSERT` (`TB_FCY_EXRATE_C002`). Maps `REF_DT`/`REF_SEQ`/`REF_TM`/`BASIC_RATE`/`SELL_AMT`/`BUY_AMT`/`TRAN_SELL_AMT`/`TRAN_BUY_AMT`/`LAST_INQ_DTTM`/`CMS_PUT_DTTM`/`ERP_GET_ST='0'`. Bank-code reverse-map applies (CITAD code → original: `01663001→11704020`, `79616001→11704088`).
4. Set WeCMS `erp_snd_yn='Y'` for that rate row.
5. Write one `ERP_TRNS_HSTR` audit row (`ERP_TYPE='W'`/WeCMS→ERP, `TRNS_TYPE_CD=EXCHANGE_RATE` (15)), even on 0 records.

## 5. Schedule / frequency

JEX batch (`ERP_0100_001`, `@JexDataInfo(type=BAT)`). No cron in source — configured in Jex Studio batch scheduler / `TB_BATCH_SCHEDULE_*`. Frequency not stated in source; confirm in Jex Studio.

## 6. Why this is separate from payment Refresh

- Touches **no** `TB_TRNS_MAST`/`TB_TRNS_DETAIL` and **no** WeCMS payment tables (`SCDUL_*`/`OVRS_*`).
- Opposite direction (WeCMS→Oracle push, vs Refresh's Oracle→WeCMS import).
- Different domain (FX rates), different source/target tables, independent schedule.

**Impact if not built with Phase 1:** none on payment Refresh — FX rates simply aren't pushed to ERP.

## 8. Implementation (2026-06-28)

Backend implemented as a flag-gated scheduled push, faithful to legacy `ERP_0100_001`. Reuses the existing WeCMS FX source table/entity and the Step-1 Oracle datasource; creates no new FX business flow and touches no payment tables.

**Files changed.**
- `domain/payment/haengsung/exchangerate/HaengsungExchangeRateSyncService.java` *(new)* — the sync logic (`sync()` returns a `Summary`). Gated `@ConditionalOnProperty(haengsung.oracle.enabled=true)`.
- `domain/payment/haengsung/exchangerate/HaengsungFcyExRateOracleRepository.java` *(new)* — Oracle `TB_FCY_EXRATE` writes via the Step-1 secondary Oracle `JdbcTemplate` (`@Qualifier(ORACLE_JDBC_TEMPLATE)`); separate from the `TB_TRNS_*` repo so each Oracle table has a focused access layer.
- `domain/payment/haengsung/exchangerate/OracleFcyExRate.java` *(new)* — mapped-row record (the field-by-field legacy mapping).
- `domain/payment/haengsung/exchangerate/HaengsungExchangeRateSyncJob.java` + `HaengsungExchangeRateSyncProperties.java` *(new)* — disabled-by-default scheduler + config.
- `domain/banking/repository/BankExchRateInfmRepository.java` — `+findByBizKeyAndRefDate(...)` (the legacy `BANK_EXCH_RATE_INFM_R003` read: all of a tenant's rows for the date) + `+markErpSent(...)` (legacy `_U003` send-flag flip, keyed on the row's natural PK).
- `domain/setting/erptransactionhistory/service/ErpTrnsHistoryWriter.java` — `+recordBatchPush(bizKey, trnsTypeCd, orgFileNm)` (fileless `ERP_TYPE='W'`, `REG_ID='SYSTEM'`, explicit `bizKey` so it works in a scheduler thread with no `TenantContext`).
- `application.yml` — `haengsung.exchange-rate-sync.{enabled:false, cron:0 */5 * * * *}` block.

**Source read reused.** WeCMS `BANK_EXCH_RATE_INFM` via the existing `BankExchRateInfm` entity + repository (new finder only). HAENGSUNG company fan-out reuses the existing `BusiInfmRepository.findByPrdtTypeAndDelYn("H","N")` (same filter as legacy `prdt_type='H' AND del_yn='N'`).

**Oracle SQL added (verbatim legacy IDOs).**
- INSERT (`TB_FCY_EXRATE_C002`): idempotent `INSERT … SELECT … FROM DUAL WHERE NOT EXISTS` on the PK `(BIZ_NO, BANK_CD, REF_DT, CCY_CD, REF_SEQ)` — a re-run never throws a duplicate-key error. `ERP_GET_DTTM` left null, `ERP_GET_ST='0'`.
- UPDATE (`TB_FCY_EXRATE_U001`): refreshes `REF_TM`/amounts/`BASIC_RATE`/`LAST_INQ_DTTM`/`CMS_PUT_DTTM`/`ERP_GET_ST='0'` keyed on the PK.

**Mapping (verbatim `ERP_0100_001.UpsertTbFcyExRateData`).** `BIZ_NO`=`BUSI_INFM.TAX_ID`; `BANK_CD`=CITAD code; `REF_DT`=`LAST_UPDATE_DATE`; **`CCY_CD`=`REF_CCY_CD`** (base currency — kept exactly as legacy maps it, not the counter currency); `REF_SEQ`=`SEQ_NO`; `REF_TM`=`LAST_UPDATE_TIME`; `LAST_INQ_DTTM`=`LAST_UPDATE_DATE`+`LAST_UPDATE_TIME`; `CMS_PUT_DTTM`=now(VN); `BUY_AMT`/`SELL_AMT`/`TRAN_SELL_AMT`/`TRAN_BUY_AMT`/`BASIC_RATE` verbatim.

**Bank-code mapping.** WeCMS stores the original code; Oracle expects the CITAD code. Legacy resolves original→CITAD via `CITAD_BANK_CD_LOOKUP` (in `R003`) and reverse-maps CITAD→original with a hardcoded 2-entry map for the send-flag write-back. The refactor reads the entity directly (original code = the row's true PK, used for the send-flag write-back — no reverse map needed) and forward-maps original→CITAD via the same 2 entries (`11704020→01663001`, `11704088→79616001`). A rate row on any other bank is **skipped** (logged) rather than pushed with an unmappable code — legacy could not flip its send-flag either, so this is the safe equivalent. *(Open: if more banks ever need FX sync, extend the map or wire `CITAD_BANK_CD_LOOKUP`.)*

**Oracle write strategy (insert vs update).** Per legacy: source row `ERP_SND_YN='Y'` (already sent) → UPDATE; else → idempotent INSERT. After a successful Oracle write the WeCMS row is flipped `ERP_SND_YN='Y'`.

**Scheduling.** `HaengsungExchangeRateSyncJob` — `@Scheduled(cron = ${haengsung.exchange-rate-sync.cron:0 */5 * * * *}, zone=Asia/Ho_Chi_Minh)` + `@SchedulerLock`. Bean exists only when `haengsung.exchange-rate-sync.enabled=true` (default false); it also no-ops when `haengsung.oracle.enabled=false` (service bean absent → `ObjectProvider` returns null). Legacy cadence is Jex-Studio-defined (not in source); the cron is configurable with a 5-minute default.

**Transaction / retry (no XA).** WeCMS Postgres and Oracle are separate datasources. Per rate row: the Oracle write commits on its own; the WeCMS `ERP_SND_YN` flip happens only after the Oracle write succeeds (its own tx). On Oracle failure the WeCMS row is left unchanged, the error is logged, and the loop continues — the row retries next run (the idempotent INSERT makes an un-flipped flag a no-op, never a duplicate-key error). One company's failure never blocks the others. One `ERP_TRNS_HSTR` audit row (`ERP_TYPE='W'`, `TRNS_TYPE_CD='15'`) is written per company, even on 0 records (legacy parity), and never aborts the sync.

**Tests.** `HaengsungExchangeRateSyncServiceTest` — verbatim mapping (`CCY_CD=REF_CCY_CD`, CITAD bank code); insert-vs-update by `ERP_SND_YN`; both CITAD mappings; one-row Oracle failure continues the next + leaves the failed row's flag unset; unmapped bank skipped; per-company audit row even with 0 rates; tax-id guard; correct HAENGSUNG fan-out; summary counts; no payment tables touched (no such repos injected). `./gradlew compileJava` + the exchange-rate-sync suite pass.

**Remaining (Step 7 — NOT in scope here):** account-history sync + card-approval sync (`ERP_0100_003` → `TB_ACCT_HISTORY` / `TB_CARD_APPROVAL`). See [haengsung-card-account-sync.md](haengsung-card-account-sync.md).

## 7. Cross-references
- Scope boundary: [haengsung-payment-refresh.md §25](haengsung-payment-refresh.md#25-scope-decision-for-first-implementation).
- Sibling ERP sync: [haengsung-card-account-sync.md](haengsung-card-account-sync.md).
- Legacy source: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_001.java`.

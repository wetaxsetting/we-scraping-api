# HAENGSUNG Exchange-Rate Sync — Backend Feature Spec (Separate ERP feature)

**Slug:** `haengsung-exchange-rate-sync` · **Domain:** Banking/ERP-sync · **Status:** Out of payment-Refresh scope — separate ERP sync feature · **Product type:** `H` only · **Last updated:** 2026-06-28

> **Docs only — no Java written.** This is **not** part of the payment Refresh feature ([haengsung-payment-refresh.md](haengsung-payment-refresh.md)) and **not** a Phase 1 blocker. It is a distinct ERP sync flow, documented here so the scope boundary is explicit. Legacy source of truth: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_001.java`.

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

## 7. Cross-references
- Scope boundary: [haengsung-payment-refresh.md §25](haengsung-payment-refresh.md#25-scope-decision-for-first-implementation).
- Sibling ERP sync: [haengsung-card-account-sync.md](haengsung-card-account-sync.md).
- Legacy source: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_001.java`.

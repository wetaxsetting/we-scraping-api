# Exchange Rate Inquiry — Database Mapping


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

Single table backs this feature: **`BANK_EXCH_RATE_INFM`** (PROD Active Table List;
[legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)).
Read-side bank-name resolution joins `BANK_INFM` via `BankCdResolver`; no other table is
read or written by the feature.

Entity: [`BankExchRateInfm`](../../../../src/main/java/com/kosign/wecms/domain/banking/entity/BankExchRateInfm.java)
+ [`BankExchRateInfmId`](../../../../src/main/java/com/kosign/wecms/domain/banking/entity/BankExchRateInfmId.java).

---

## 1. Entity ↔ table ↔ column

| Java field | DB column | Type (entity) | Notes |
|---|---|---|---|
| `bizKey` | `BIZ_KEY` | VARCHAR(20) | **PK** — tenant |
| `bankCd` | `BANK_CD` | VARCHAR(14) | **PK** — source bank (`11704088` Shinhan, `11704020` Woori) |
| `refDate` | `REF_DATE` | VARCHAR(8) | **PK** — reference date YYYYMMDD |
| `ccyCd` | `CCY_CD` | CHAR(3) `@JdbcTypeCode(CHAR)` | **PK** — counter currency (always `VND` for Shinhan/Woori VN data) |
| `exchSeqNo` | `EXCH_SEQ_NO` | VARCHAR(20) | **PK** — auto-increment via `wb_get_next_val` funnel |
| `refCcyCd` | `REF_CCY_CD` | VARCHAR(3) | base / foreign currency the rate is quoted against (USD/KRW/JPY/…) — the **display discriminator** |
| `tranSellAmt` | `TRAN_SELL_AMT` | NUMERIC(20,6) | T/T Sell |
| `tranBuyAmt` | `TRAN_BUY_AMT` | NUMERIC(20,6) | T/T Buy |
| `buyAmt` | `BUY_AMT` | NUMERIC(20,6) | Cash Buy |
| `sellAmt` | `SELL_AMT` | NUMERIC(20,6) | Cash Sell |
| `srchDt` | `SRCH_DT` | VARCHAR(8) | date rates were retrieved |
| `basicRate` | `BASIC_RATE` | NUMERIC(20,6) | basic / middle rate |
| `basicTran` | `BASIC_TRAN` | NUMERIC(20,6) | basic transfer rate |
| `classExch` | `` `class` `` | VARCHAR(10) | Coocon category (`COMMON`/`CORPORATE`) — JEX column is the reserved word `class`, quoted; Java field is `classExch` |
| `module` | `MODULE` | VARCHAR(100) | Coocon module string (`GLOBAL/GBL/BANK_Shinhan`) |
| `exchRateRegDtm` | `EXCH_RATE_REG_DT` | VARCHAR(14) | source registration datetime YYYYMMDDHHMMSS — JEX column is `exch_rate_reg_dt`, Java field is `exchRateRegDtm` |
| `seqNo` | `SEQ_NO` | VARCHAR(2) | **batch round** ("1" morning / "2" afternoon / Woori MAX+1) — distinct from the PK `EXCH_SEQ_NO` |
| `standPoint` | `STAND_POINT` | CHAR(1) `@JdbcTypeCode(CHAR)` | stand point from Coocon resultData |
| `lastUpdateDate` | `LAST_UPDATE_DATE` | VARCHAR(8) | source last-update date YYYYMMDD |
| `lastUpdateTime` | `LAST_UPDATE_TIME` | VARCHAR(6) | source last-update time HHMMSS |
| `erpSndYn` | `ERP_SND_YN` | CHAR(1) `@JdbcTypeCode(CHAR)` NOT NULL default `'N'` | ERP send flag |
| `regDtm` | `REG_DTM` | VARCHAR(14) | audit |
| `regUser` | `REG_ID` | VARCHAR(100) | audit — JEX column is `reg_id`, Java field is `regUser` |
| `modDtm` | `MOD_DTM` | VARCHAR(14) | audit |
| `modId` | `MOD_ID` | VARCHAR(100) | audit |

> The entity maps the columns the feature reads/writes; `@Column(name=...)` mirrors the
> legacy JEX column names verbatim per the Legacy Schema Parity Rule. Wire casing
> (snake_case) is a Jackson/DTO concern only and never changes these column names.

## 2. Primary key

Composite PK (`@IdClass(BankExchRateInfmId.class)`):

```
BIZ_KEY + BANK_CD + REF_DATE + CCY_CD + EXCH_SEQ_NO
```

- `BIZ_KEY` is in the PK → physical per-tenant partitioning. Every read query filters on it.
- `EXCH_SEQ_NO` is the auto-increment uniqueness leg, allocated via the funnel
  (`LegacyFunctionRepository.nextBankExchRateExchSeqNo`, per `(biz_key, bank_cd, ref_date, ccy_cd)` MAX+1).

## 3. `SEQ_NO` vs `EXCH_SEQ_NO` — the round-probe exception

There are **two** sequence concepts on this table; keep them distinct:

- **`EXCH_SEQ_NO`** — PK part, a generated uniqueness id. Never compared in the read query's
  round logic.
- **`SEQ_NO`** — a *data column* carrying the **batch round**: `"1"` (morning),
  `"2"` (afternoon), Woori `MAX+1`. This is what the read query's "latest round" logic uses
  (`DISTINCT ON ... ORDER BY CAST(seq_no AS integer) DESC`) and what the round dropdown shows.

**Round-probe exception (documented Java-side generator):** the Woori batch
(`runWoori`) computes `nextSeqNo = MAX(seq_no where bank=Woori, ref_date=today, ccy_cd='VND') + 1`
via `findHighestSeqNo` — a *read for round computation*, not a row-key allocator, so it is
one of the documented exceptions to the "all key gen through `wb_get_next_val`" rule (the
row PK `EXCH_SEQ_NO` IS funnelled). See
`wecms-api/CLAUDE.md` → DB Function / Legacy Function Policy, exception #4.

## 4. FK / supporting tables

- **No FK** is declared from `BANK_EXCH_RATE_INFM` (legacy parity — JEX has none).
- **`BANK_INFM`** — read-only bank-name lookup via `BankCdResolver.resolveBankNm`. Not joined
  in SQL; resolved per-code after the fetch.
- **`CCY_INFM`** — currency reference table exists in the inventory but is **not consulted** by
  this feature; the display currency is derived directly from `CCY_CD`/`REF_CCY_CD`.
- **`tb_firm_bank_code`** — not used by this feature (it backs the Contacts NAPAS/CITAD lookup).

## 5. Read queries (native, DISTINCT ON)

| Query | Purpose |
|---|---|
| `findLatestPerCurrencyByBizKeyAndBankCdAndRefDate` | latest round per currency pair (`DISTINCT ON (ccy_cd, ref_ccy_cd) ... ORDER BY ... CAST(seq_no AS integer) DESC`), `ccy_cd='VND'` |
| `findLatestPerCurrencyByBizKeyAndBankCdAndRefDateAndSeqNo` | exact round selected by the dropdown |
| `findByBizKeyAndBankCdAndRefDateAndRefCcyCdAndCcyCdAndSeqNo` | batch upsert/skip existence key (includes `ref_ccy_cd` discriminator) |
| `findLatestRefDateByBizKeyAndBankCd` | most-recent date fallback (refactor addition; legacy has none) |
| `findDistinctBankCdByBizKey` | `/banks` selector |
| `findDistinctDisplayCurrencyByBizKey` | `/currencies` selector (CASE normalises foreign side) |
| `findDistinctRoundsByBizKeyAndBankCdAndRefDate` | `(seq_no, MAX(last_update_time))` for the round dropdown |
| `findHighestSeqNo` | Woori round-probe (`MAX(seq_no)+1`) |

## 6. Migrations

| Migration | Purpose |
|---|---|
| V127 (`V10__bank_exch_rate_infm.sql`) | creates `BANK_EXCH_RATE_INFM` (composite PK, rate columns, ERP flag, audit columns, `IDX_BANK_EXCH_RATE_INFM_BIZ_DATE` on `(BIZ_KEY, REF_DATE, BANK_CD)`) |
| V130 (`V130__bank_exch_rate_infm_seed.sql`) | seeds 15 currency rows for `VN_VNM0004_000001` / `11704088` / `20250425` / `EXCH_SEQ_NO=1` (KRW-denominated demo values) | *(historical — DROPPED; demo seeds were removed in the 2026-06-09 rebaseline)*
| V140 / V155 | `wb_get_next_val` branch for `bank_exch_rate_infm.exch_seq_no` (per `(biz_key, bank_cd, ref_date, ccy_cd)` MAX+1) — funnelled via `LegacyFunctionRepository.nextBankExchRateExchSeqNo` |

> The V127/V130 file headers still show draft numbers (`V84`/`V87`) — renumbered on commit.
> No new DDL is owned by this folder; schema changes go through the manual-DDL policy in
> `wecms-api/CLAUDE.md`.

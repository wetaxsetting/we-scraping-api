# Transfer History — Database Mapping (Backend)

> Primary table: `NAPAS_REMIT_TRNS_HSTR`
> Entity: `com.kosign.wecms.domain.banking.entity.NapasRemitTrnsHstr` (`@IdClass NapasRemitTrnsHstrId`)

## 1. Core tables used

| Table | Role | In scope? |
|---|---|---|
| `NAPAS_REMIT_TRNS_HSTR` | The transfer-history records (the only data source for the list) | ✅ Primary |
| `USER_INFM` | LEFT-JOIN target to resolve `user_nm` from `REG_ID` (Java-side batched lookup, replaces legacy `WB_GET_USER_NM`) | ✅ Read-only join |
| `BANK_INFM` | Out-bank name resolution via `BankCdResolver` | ✅ Reference |
| `tb_firm_bank_code` | In-bank (NAPAS/CITAD) name resolution via legacy fn `wb_get_napas_bank_nm_or_citad_bank_nm` | ✅ Reference (legacy fn) |

**Not used by this feature** (belong to other features): `ACCT_TRNS_RCV_PAY_INFM` (bank matching), `SCDUL_PYMN_SUMR(_DETL)` (scheduled payment), `BUSI_MATCH_LOG` (match log), `ACCT_TRNS_HSTR` (transaction history). They exist in DEV but are never referenced by `trns_0003_001` or the refactor `transferhistory` package.

## 2. `NAPAS_REMIT_TRNS_HSTR` — DEV schema (21 columns)

Verified against JEX DEV via the `postgres_dev_server_jexframe` MCP.

| # | Column | Type | Nullable | Notes |
|---|---|---|---|---|
| 1 | `BIZ_KEY` | varchar(20) | NO | PK part 1 (tenant) |
| 2 | `SEQ_NO` | numeric | NO | PK part 2 (per-tenant seq) |
| 3 | `OUT_BANK_CODE` | varchar | NO | sender bank code |
| 4 | `OUT_ACCT_NO` | varchar | NO | sender account |
| 5 | `TRANS_TYPE` | char(1) | YES | transfer type |
| 6 | `IN_NAPAS_BANK_CODE` | varchar(10) | YES | NAPAS code of recipient bank — **the one used for name resolution** |
| 7 | `IN_ACCT_NO` | varchar | NO | recipient account |
| 8 | `CCY_CD` | char(3) | NO | `VND` / `USD` |
| 9 | `TRSC_AMT` | numeric | NO | transfer amount |
| 10 | `FEE_DFR_SBJ` | char(1) | YES | fee-bearer subject |
| 11 | `FEE` | numeric | YES | fee |
| 12 | `RMRK` | varchar | YES | remarks (shown) |
| 13 | `RMRK2` | varchar | YES | secondary remark (not shown) |
| 14 | `OWAC_NM` | varchar | YES | recipient holder name (aliased `OWAN_NM`) |
| 15 | `REG_DTM` | varchar(14) | NO | UTC `yyyyMMddHHmmss` — date+time source |
| 16 | `REG_ID` | varchar | NO | registrant user id → `user_nm` |
| 17 | `TRNS_STTS` | varchar | YES | engine status code (`00000000`=success) |
| 18 | `TRNS_MSG` | varchar | YES | engine message (failed-row tooltip) |
| 19 | `IN_BANK_CODE` | varchar | YES | internal in-bank code (dual column — see note) |
| 20 | `TRNS_STLM_ID` | varchar | YES | settlement id (not shown) |
| 21 | `TRNS_STLM_YN` | varchar | YES | settlement flag (not shown) |

**PK:** `napas_remit_trns_hstr_pk = PRIMARY KEY (BIZ_KEY, SEQ_NO)`.
**Indexes (DEV):** PK only — no other index, no UNIQUE/CHECK/FK.
**Index (refactor):** none. The base table ships as [`V69__napas_remit_trns_hstr.sql`](../../../../../src/main/resources/db/migration/V69__napas_remit_trns_hstr.sql) with **PK only**, matching DEV.

> ⚠️ **Superseded.** Earlier revisions documented a refactor-added helper index
> `ix_napas_remit_trns_hstr_biz_regdtm` on `(BIZ_KEY, substr(REG_DTM,1,8) DESC, SEQ_NO DESC)`, shipped as
> `V196__transfer_history_helper_index.sql`. The [2026-06-09 rebaseline](../../../../references/database/flyway-migration-baseline.md)
> dropped all helper-index migrations — `V69` is base DDL only. If the index is still wanted for query
> performance it must be re-proposed as a manual DBA script; it is **not** in the schema today.

> ⚠️ **Dual in-bank columns (legacy quirk, R6):** the table carries both `IN_BANK_CODE` (internal code, e.g. `11704088`) and `IN_NAPAS_BANK_CODE` (NAPAS code, e.g. `970424`). The refactor resolves the recipient bank **name** from `IN_NAPAS_BANK_CODE` and ships that NAPAS code as the wire `in_bank_code`. A row with no NAPAS code yields empty `in_bank_code`/`in_bank_nm` (the resolver is not called). The entity field `inBankCode` (mapped to `IN_BANK_CODE`) exists but is not what populates the wire `in_bank_code`.

## 3. Entity mapping (`NapasRemitTrnsHstr`)

`@Entity @Table(name="NAPAS_REMIT_TRNS_HSTR") @IdClass(NapasRemitTrnsHstrId.class)`. Maps the Sprint-1 column subset (Hibernate `validate` ignores unmapped DB columns). Does **not** extend `BaseEntity` (audit columns mapped directly).

| Java field | `@Column` | Type |
|---|---|---|
| `bizKey` (@Id) | `BIZ_KEY` | String(20) |
| `seqNo` (@Id) | `SEQ_NO` | BigDecimal |
| `outBankCode` | `OUT_BANK_CODE` | String(20) |
| `outAcctNo` | `OUT_ACCT_NO` | String(100) |
| `transType` | `TRANS_TYPE` | String(1) |
| `inBankCode` | `IN_BANK_CODE` | String(20) |
| `inNapasBankCode` | `IN_NAPAS_BANK_CODE` | String(10) |
| `inAcctNo` | `IN_ACCT_NO` | String(100) |
| `owacNm` | `OWAC_NM` | String(200) |
| `ccyCd` | `CCY_CD` | String(3) |
| `trscAmt` | `TRSC_AMT` | BigDecimal |
| `fee` | `FEE` | BigDecimal |
| `feeDfrSbj` | `FEE_DFR_SBJ` | String(1) |
| `rmrk` | `RMRK` | String(500) |
| `rmrk2` | `RMRK2` | String(500) |
| `trnsStts` | `TRNS_STTS` | String(20) |
| `trnsMsg` | `TRNS_MSG` | String(255) |
| `trnsStlmId` | `TRNS_STLM_ID` | String(100) |
| `trnsStlmYn` | `TRNS_STLM_YN` | String(1) |
| `regId` | `REG_ID` | String(100) |
| `regDtm` | `REG_DTM` | String(14) |
| `oatIdempotencyKey` | `OAT_IDEMPOTENCY_KEY` | String(64) (added V179, for Own Account Transfer write path — not read by this feature) |

`NapasRemitTrnsHstrId`: `Serializable` composite key `{ String bizKey, BigDecimal seqNo }`.

## 4. Full column-mapping chain (legacy → entity → DTO → UI)

| Legacy column / alias | Entity field | DTO field (`TransferHistoryRow`) | Wire (snake) | UI column |
|---|---|---|---|---|
| `SEQ_NO` | `seqNo` | `seqNo` | `seq_no` | (row key) |
| `REG_DTM` (UTC) → split | `regDtm` | `trnsDt` | `trns_dt` | Date/Time (date) |
| `REG_DTM` (UTC) → split | `regDtm` | `trnsTm` | `trns_tm` | Date/Time (time) |
| `OUT_BANK_CODE` | `outBankCode` | `outBankCode` | `out_bank_code` | From Bank (code) |
| `wb_get_bank_nm(OUT_BANK_CODE)` | (resolver) | `outBankNm` | `out_bank_nm` | From Bank (name) |
| `OUT_ACCT_NO` | `outAcctNo` | `outAcctNo` | `out_acct_no` | From Bank (account) |
| `IN_NAPAS_BANK_CODE` | `inNapasBankCode` | `inBankCode` | `in_bank_code` | To Bank (code) |
| `WB_GET_NAPAS_BANK_NM(IN_NAPAS_BANK_CODE)` | (legacy fn) | `inBankNm` | `in_bank_nm` | To Bank (name) |
| `IN_ACCT_NO` | `inAcctNo` | `inAcctNo` | `in_acct_no` | To Bank (account) |
| `OWAC_NM` (alias `OWAN_NM`) | `owacNm` | `owanNm` | `owan_nm` | (recipient holder) |
| `CCY_CD` | `ccyCd` | `ccyCd` | `ccy_cd` | (currency on amount) |
| `TRSC_AMT` | `trscAmt` | `trscAmt` | `trsc_amt` | Transfer Amount |
| `FEE` | `fee` | `fee` | `fee` | (not displayed) |
| `TRANS_TYPE` | `transType` | `transType` | `trans_type` | (not displayed) |
| `RMRK` | `rmrk` | `rmrk` | `rmrk` | Remarks |
| `TRNS_STTS` | `trnsStts` | `trnsStts` | `trns_stts` | Results (status) |
| `TRNS_MSG` | `trnsMsg` | `trnsMsg` | `trns_msg` | Results (tooltip) |
| `WB_GET_USER_NM(REG_ID)` | (USER_INFM join) | `userNm` | `user_nm` | (registrant) |

**DTO fields intentionally omitted** (OQ-8): `RMRK2`, `FEE_DFR_SBJ`, `TRNS_STLM_ID`, `TRNS_STLM_YN`.

## 5. Data migration / seed

Per the workspace Data Migration Rule, local seed is scoped to `BIZ_KEY = VN_VNM0004_000001`, ≤ 30 rows, DEV PK values preserved verbatim. `NAPAS_REMIT_TRNS_HSTR` is in the PROD Active Table List (`napas_remit_trns_hstr`). Real DEV rows show `TRNS_STTS` values such as `LOGIN_CAN` (operator-cancelled) and `8000FFFF` (FI error) — all non-`00000000` render as "Failed".

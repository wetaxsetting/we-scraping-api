# Own Account Transfer — Database Mapping (Refactor Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../../references/database/flyway-migration-baseline.md) §5.

Tables read/written by the feature, and the full **Legacy column → Refactor entity field → DTO field →
UI field** chain for the key concepts. Schema design detail (gaps, migrations, idempotent SQL) lives in the
reference doc [own-account-transfer-db.md](../../../../references/database/banking/own-account-transfer-db.md);
ERD in [banking-erd.md](../../../../references/database/erd/banking-erd.md); ownership in
[table-ownership.md](../../../../references/database/table-ownership.md). Legacy source-of-truth:
[docs/jex/features/own-account-transfer/database-mapping.md](../../../../../../docs/jex/features/own-account-transfer/database-mapping.md).

> All tables below are in the **PROD Active Table List**. `NAPAS_BANK_REMIT_INFO` (legacy recipient-bank
> remittability) is **not migrated** — the cross-tenant recipient search it backed is out of scope (OQ-A).

---

## 1. Tables used

| Table | Access | Entity | Role |
|---|---|---|---|
| `ACCT_MSTR` | READ + UPDATE | `AcctMstr` | Source/target accounts; balance + inquiry status patched on refresh. |
| `ACCT_AUTH_INFO` | READ | `AcctAuthInfo` | Per-user `V`/`W` authority filter for non-admins. |
| `NAPAS_REMIT_TRNS_HSTR` | INSERT + READ | `NapasRemitTrnsHstr` | One immutable row per executed transfer (+ idempotency replay). |
| `NAPAS_BANK_INFO` | READ | `NapasBankInfo` | NAPAS code → internal bank_cd + display names. |
| `BANK_CERT_INFO` | READ | (cert projection) | Saved bank credentials (cert SEQ_NO) for the engine bundle. |
| `EXT_TRAN_CALL_HIS` | INSERT | (native write) | Firm-Banking-History scrape audit (BALANCE_INQUIRY etc. — not TRANSFER). |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | INSERT | (native write) | Per-engine-job forensic audit. |
| `AUDIT_LOGS_INFM` | INSERT | shared writer | App-level audit (`WSVC_ID = 'own-account-transfer'`). |

Migrations: `V195__napas_remit_trns_hstr_oat_idempotency.sql` (idempotency key + partial unique index), *(historical — DROPPED; the OAT idempotency guard was removed entirely. Base table: `V69__napas_remit_trns_hstr.sql`)*
`V156__sys_scr_option_wecms.sql` (View Setting column seed). `BANK_CERT_INFO` /
`NXSIAS_MODULE_TRAN_LOG_HIS` schema landed in V100/V101 (draft-numbered V86/V87 — OQ-DOC-3 drift note).

## 2. Column mapping — key concepts

**Legacy column → Refactor entity field → DTO field (wire snake_case) → UI field**

| Concept | Legacy column (table) | Entity field (`AcctMstr` / `NapasRemitTrnsHstr` / `NapasBankInfo`) | DTO field (wire) | UI field |
|---|---|---|---|---|
| Transfer number | `NAPAS_REMIT_TRNS_HSTR.SEQ_NO` | `NapasRemitTrnsHstr.seqNo` | `seq_no` | (history row id) |
| From account (bank) | `OUT_BANK_CODE` | `outBankCode` | `source_bank_cd` | source chip |
| From account (no.) | `OUT_ACCT_NO` | `outAcctNo` | `source_acct_no` | source chip |
| To account (bank) | `IN_BANK_CODE` | `inBankCode` | `target_bank_cd` | target dropdown |
| To account (NAPAS) | `IN_NAPAS_BANK_CODE` | `inNapasBankCode` | `target_napas_bank_cd` | target dropdown |
| To account (no.) | `IN_ACCT_NO` | `inAcctNo` | `target_acct_no` | target dropdown |
| Transfer type | `TRANS_TYPE` (`1`=same,`2`=NAPAS) | `transType` | `transfer_type` (`SAME_BANK`/`NAPAS`) | derived |
| Currency | `CCY_CD` | `ccyCd` | `ccy_cd` | "VND" |
| Amount | `TRSC_AMT` | `trscAmt` | `requested_amt` / `actual_amt` / `recorded_amt` | amount input / grid |
| Fee | `FEE` / `FEE_DFR_SBJ` | `fee` / `feeDfrSbj` | `fee` / `fee_dfr_sbj` (`A`/`B`/`S`) | fee selector |
| Remarks | `RMRK` / `RMRK2` | `rmrk` / `rmrk2` | `rmrk` / `rmrk2` | remark inputs |
| Transfer status | `TRNS_STTS` (`00000000`=ok) | `trnsStts` | `trns_stts` → `status` | grid badge |
| Transfer message | `TRNS_MSG` | `trnsMsg` | `trns_msg` | tooltip |
| Bank tx id | `TRNS_STLM_ID` | `trnsStlmId` | `bank_tx_id` | — |
| Settlement flag | `TRNS_STLM_YN` (`N` on insert) | `trnsStlmYn` | — | — |
| Idempotency key | `OAT_IDEMPOTENCY_KEY` (V195) | `oatIdempotencyKey` | `idempotency_key` | (client UUID) |
| **Approval status** | — | — | — | **N/A — no in-app approval state** (see [business-flow.md §6](business-flow.md)) |
| **Inquiry status** (balance) | `ACCT_MSTR.ACCT_BAL_INQ_STTS` | `AcctMstr.acctBalInqStts` | `acct_bal_inq_stts` | Last-Refresh badge |
| Inquiry message | `ACCT_MSTR.ACCT_BAL_INQ_MSG` | `acctBalInqMsg` | `acct_bal_inq_msg` | Last-Refresh tooltip |
| Balance | `ACCT_MSTR.BAL` | `bal` | `bal` | BAL column |
| Available amount | `ACCT_MSTR.REAL_AMT` | `realAmt` | `real_amt` | AVI_BAL column |
| Previous balance | `ACCT_MSTR.CORC_BF_BAL` | `corcBfBal` | — | — |
| **Last update datetime** | `ACCT_MSTR.ACCT_BAL_UPD_DTM` | `acctBalUpdDtm` | `acct_bal_upd_dtm` | — |
| **Balance inquiry datetime** | `ACCT_MSTR.ACCT_BAL_LST_INQ_DTM` | `acctBalLstInqDtm` | `acct_bal_lst_inq_dtm` | Last-Refresh time |
| NAPAS short name | `NAPAS_BANK_INFO.SHORT_NAME` | `NapasBankInfo.shortName` | `short_name` | recipient label |
| NAPAS long name (EN/VN) | `LONG_NAME_EN` / `LONG_NAME_VN` | `longNameEn` / `longNameVn` | `long_name_en` / `long_name_vn` | recipient label |
| NAPAS → internal | `NAPAS_BANK_INFO.BANK_CD` | `bankCd` | `bank_cd` | routing |

All datetimes are stored as `VARCHAR(14)` UTC `yyyyMMddHHmmss` (legacy shape). Wire snake_case is enforced
globally by `JacksonConfig`; DB columns stay UPPER_SNAKE.

## 3. Balance-update sequence (`applyOwnTransferBalanceRefresh`)

Mirrors legacy `trns_0001_001_u001` / `ACCT_MSTR_U000`:

1. Always patch `ACCT_BAL_INQ_STTS`, `ACCT_BAL_INQ_MSG`, `ACCT_BAL_LST_INQ_DTM`, `MOD_ID`, `MOD_DTM`.
2. If status `= 00000000` and new `BAL == old BAL` → set `REAL_AMT` only.
3. If `BAL` changed → `CORC_BF_BAL = old BAL`, `BAL = new`, `ACCT_BAL_UPD_DTM = now`.

## 4. Transfer-history insert (`NAPAS_REMIT_TRNS_HSTR`)

- PK `(BIZ_KEY, SEQ_NO)`; `SEQ_NO` allocated per-tenant via `allocateNextSeqNo` (`COALESCE(MAX(seqNo),0)+1`).
- `TRNS_STLM_YN = 'N'` on insert; settlement batch flips to `'Y'` later (not in this feature's scope).
- `OAT_IDEMPOTENCY_KEY` carries the client UUID; the partial unique index `(BIZ_KEY, OAT_IDEMPOTENCY_KEY) WHERE NOT NULL` (V195) prevents double-submits while letting legacy rows with NULL keys coexist.

## 5. External-transaction-call history

`EXT_TRAN_CALL_HIS` (written by `OatNxisasService.recordLogging`, `OatNxisasService.java:272`):

| Column | Value |
|---|---|
| `SVC_ID` | scrape bucket — `BALANCE_INQUIRY` / `CARD_INQUIRY` / … (**never** TRANSFER) |
| `REF_DATE` | `yyyyMMdd` UTC (per-day SEQ_NO partition) |
| `SEQ_NO` | `MAX+1` per `(SVC_ID, REF_DATE)` |
| `WEB_SVC_ID` | = `SVC_ID` |
| `RSLT_CD` / `ERR_CONT` | engine `Output.ErrorCode` / `ErrorMessage` |
| `BANK_CD` / `ACCT_NO` | bank + `Input.acctNum` |
| `BIZ_KEY` / `USER_ID` / `REG_DTM` | tenant / user / UTC timestamp |

`NXSIAS_MODULE_TRAN_LOG_HIS` (one row per engine job): `UUID`, `NXISAS_CLASS/MODULE/JOB`,
`NXISAS_ERR_CODE/CONT`, `REQ_DATA` (serialized `Input`), `NXISAS_TYPE` (SVC_ID bucket — e.g.
`OWN_ACCOUNT_TRANSFER` for TRANSFER jobs), `BIZ_KEY`, `REG_ID/REG_DTM`.

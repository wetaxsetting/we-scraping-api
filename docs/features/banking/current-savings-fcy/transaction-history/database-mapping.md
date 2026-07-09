# Transaction History (FCY) — Database Mapping (Backend)

Legacy-column → entity-field → DTO-field → UI-column chains for the refactored FCY transaction
inquiry. The DB schema mirrors legacy JEX column-for-column (per the Legacy Schema Parity Rule).
JEX source of truth: `fnsh_0006_001` → IDO `ACCT_TRNS_HSTR_R016`.

> FCY uses the **same tables and entities** as the [VND twin](../transaction-history/database-mapping.md).
> The only FCY-specific column is `ACCT_TRNS_HSTR.CCY_CD` (the currency dimension that
> `TransactionService.fetchRows` filters on). No FCY-specific table exists.

---

## 1. Core tables

| Entity (file) | `@Table` | PK (`@IdClass`) | Role |
|---|---|---|---|
| `AcctTrnsHstr` (`entity/AcctTrnsHstr.java`) | `ACCT_TRNS_HSTR` | `AcctTrnsHstrId(bizKey, acctSeqNo)` | Transaction ledger (the rows shown) |
| `AcctTrnsRcvPayInfm` (`entity/AcctTrnsRcvPayInfm.java`) | `ACCT_TRNS_RCV_PAY_INFM` | `AcctTrnsRcvPayInfmId(bizKey, acctSeqNo, rcvPaySeqNo)` | Confirmation + classification (counterparty/purpose/VAS) |
| `AcctMstr` (`entity/AcctMstr.java`) | `ACCT_MSTR` | `AcctMstrId(bizKey, bankCd, acctNo)` | Account master — **supplies `CCY_CD` for the FCY filter** |
| `BusiMatchLog` (`entity/BusiMatchLog.java`) | `BUSI_MATCH_LOG` | `BusiMatchLogId(bizKey, matchLogSeq)` | Match metadata (matched-by/at + payment file) |
| `ExtTranCallHis` (`synchistory/entity/ExtTranCallHis.java`) | `EXT_TRAN_CALL_HIS` | `ExtTranCallHisId(svcId, refDate, seqNo)` | Sync audit log (sibling — Account Inquiry sync) |

> **Table-name note.** The real table is `EXT_TRAN_CALL_HIS` (entity `@Table(name="EXT_TRAN_CALL_HIS")`),
> **not** `EXTN_TRAN_CALL_HSTR` (a name used loosely in some task notes). All five tables are in the
> PROD Active Table List.

All five are also used by Account Inquiry / reports — they are not FCY-transaction-history-specific.

---

## 2. ACCT_TRNS_HSTR → AcctTrnsHstr entity

Columns relevant to FCY transaction display (with `@Column` names):

| Column | Entity field | Type | Notes |
|---|---|---|---|
| `BIZ_KEY` | `bizKey` | VARCHAR(20) | tenant; always in the WHERE clause |
| `ACCT_SEQ_NO` | `acctSeqNo` | VARCHAR(20) | per-row surrogate = the `trnsSeqNo` in the detail path |
| `BANK_CD` | `bankCd` | VARCHAR(20) | from the composite `acctSeqNo` |
| `ACCT_NO` | `acctNo` | VARCHAR(100) | from the composite `acctSeqNo` |
| `TRSC_DT` | `trscDt` | VARCHAR(8) `yyyyMMdd` | date-range filter + default sort key |
| `TRSC_TM` | `trscTm` | VARCHAR(6) `HHmmss` | secondary sort key |
| **`CCY_CD`** | **`ccyCd`** | CHAR(3) | **the FCY currency dimension — `fetchRows` filters on it** |
| `INOUT_DV` | `inoutDv` | CHAR(1) | `1`=deposit, `2`=withdraw |
| `TRSC_AMT` | `trscAmt` | NUMERIC | deposit/withdraw amount (in the account's FCY) |
| `BAL` | `bal` | NUMERIC | running balance (passed through verbatim) |
| `RMRK` | `rmrk` | VARCHAR(500) | remark |
| `RMRK2` | `rmrk2` | VARCHAR(500) | remark 2 (detail only) |
| `BANK_REF_NO` | `bankRefNo` | VARCHAR(100) | bank reference (detail + keyword) |
| `BANK_TRNS_SHORT_ID` | `bankTrnsShortId` | VARCHAR(20) | human-readable txn id (→ `TRSC_ID` column) |
| `BANK_TRNS_FULL_ID` | `bankTrnsFullId` | VARCHAR(100) | SHA-256 sync idempotency key |
| `LINK_MATCH_YN` | `linkMatchYn` | CHAR(1) | matched-to-payment flag (UI tinting) |
| `CHART_ACCT_CD` | `chartAcctCd` | VARCHAR(20) | reporting account code |
| `CHART_ACCT_SEQ_NO` | `chartAcctSeqNo` | VARCHAR(10/14) | reporting account seq |
| `REG_DTM`/`MOD_DTM` | audit | VARCHAR(14) | audit columns |

---

## 3. ACCT_TRNS_RCV_PAY_INFM → AcctTrnsRcvPayInfm entity

Confirmation + classification, keyed `(BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO)`:

| Column | Entity field | Role |
|---|---|---|
| `CNFM_YN` | `cnfmYn` | confirm flag (`Y` = confirmed) |
| `CNFM_BY_USER_ID` / `CNFM_BY_USER_NM` | `cnfmByUserId` / `cnfmByUserNm` | confirmer (→ `CONFIRMED_BY`) |
| `CNFM_DTM` | `cnfmDtm` | confirm datetime (→ `CONFIRMED_DTM`) |
| `RCV_PAY_TRCO_KEY` / `RCV_PAY_TRCO_NM` | `rcvPayTrcoKey` / `rcvPayTrcoNm` | counterparty snapshot (→ `CONTACT_NM`) |
| `RCV_PAY_USAG_CD` | `rcvPayUsagCd` | purpose code (→ `PURPOSE`) |
| `VAS_ACCT_CD` | `vasAcctCd` | VAS account code (→ `VAS_ACCT_CD`) |

---

## 4. Full column-mapping chain (legacy → entity → DTO → UI)

| Legacy (IDO `ACCT_TRNS_HSTR_R016` / column) | Refactor entity field | DTO field (wire) | UI column (FIELD_ID) |
|---|---|---|---|
| `ATH.TRSC_DT` | `AcctTrnsHstr.trscDt` | `TRSC_DT` | `TRSC_DTM` (date) |
| `ATH.TRSC_TM` | `AcctTrnsHstr.trscTm` | `TRSC_TM` | `TRSC_DTM` (time) |
| `ATH.BANK_CD` (+ `wb_get_bank_nm`) | `AcctTrnsHstr.bankCd` | *(via acctSeqNo)* | account context (breadcrumb) |
| `AM.ACCT_NICK_NM` | `AcctMstr.acctNickNm` | *(account resolve)* | account context (breadcrumb) |
| `ATH.CCY_CD` | `AcctTrnsHstr.ccyCd` | `CCY_CD` *(detail only)* | currency context (per account) |
| `IN_AMT` (`CASE INOUT_DV='1'`) | `AcctTrnsHstr.trscAmt` + `inoutDv` | `DEPOSIT_AMT` | `DEPOSIT_AMT` |
| `OUT_AMT` (`CASE INOUT_DV='2'`) | `AcctTrnsHstr.trscAmt` + `inoutDv` | `WITHDRAW_AMT` | `WITHDRAW_AMT` |
| `ATH.BAL` | `AcctTrnsHstr.bal` | `BAL` | `BAL` |
| `ATH.RMRK` | `AcctTrnsHstr.rmrk` | `RMRK` | `RMRK` |
| `ATH.RMRK2` | `AcctTrnsHstr.rmrk2` | `RMRK2` *(detail)* | — |
| `ATH.BANK_TRNS_SHORT_ID` | `AcctTrnsHstr.bankTrnsShortId` | `BANK_TRNS_SHORT_ID` | `TRSC_ID` |
| `ATRPI.RCV_PAY_TRCO_NM` | `AcctTrnsRcvPayInfm.rcvPayTrcoNm` | `RCV_PAY_TRCO_NM` | `CONTACT_NM` |
| `ATRPI.RCV_PAY_USAG_CD` | `AcctTrnsRcvPayInfm.rcvPayUsagCd` | `USE_USAG_CD` | `PURPOSE` |
| `ATRPI.VAS_ACCT_CD` | `AcctTrnsRcvPayInfm.vasAcctCd` | `VAS_ACCT_CD` | `VAS_ACCT_CD` |
| `ATH.CHART_ACCT_CD` | `AcctTrnsHstr.chartAcctCd` | `CHART_ACCT_CD` | `CHART_ACCT_CD` |
| `BL.MATCHED_AT` (lateral) | `BusiMatchLog.matchedAt` | `MATCHED_AT` | `MTCH_DT` |
| `BL.MATCHED_BY` → `USER_INFM.USER_NM` | `BusiMatchLog.matchedBy` (resolved) | `MATCHED_BY` | `MTCH_BY` |
| `SPS.ORG_FILE_NM` (payment file) | resolved via `PAY_TRNS_ID` | `PAY_ORG_FILE_NM` | `FILE_NM` |
| `coalesce(ATRPI.CNFM_YN,'N')` | `AcctTrnsRcvPayInfm.cnfmYn` | `CONF_YN` | confirm flag / action cell |
| `ATRPI.CNFM_BY_USER_NM` | `AcctTrnsRcvPayInfm.cnfmByUserNm` | `CONFIRMED_BY` | `CONFIRMED_BY` |
| `ATRPI.CNFM_DTM` | `AcctTrnsRcvPayInfm.cnfmDtm` | `CONFIRMED_DTM` | `CONFIRMED_BY` (sub-line) |
| `ATH.LINK_MATCH_YN` | `AcctTrnsHstr.linkMatchYn` | `LINK_MATCH_YN` | row tinting |

> The prompt's generic field list (Transaction Date, Time, Account Number, Account Name, Currency,
> Withdrawal, Deposit, Balance, Description, Counterparty, Transaction Type, Sync Datetime) maps to:
> `TRSC_DT` / `TRSC_TM` / `ACCT_NO` (in `acctSeqNo`) / `ACCT_NICK_NM` (account resolve) / `CCY_CD` /
> `WITHDRAW_AMT` / `DEPOSIT_AMT` / `BAL` / `RMRK` / `RCV_PAY_TRCO_NM` / `INOUT_DV` /
> `EXT_TRAN_CALL_HIS.REG_DTM` (sync log — Account Inquiry sibling, not a TH grid column).

---

## 5. Status & classification columns

- **Confirmed?** — not a column on `ACCT_TRNS_HSTR`; it is the presence of a sibling
  `ACCT_TRNS_RCV_PAY_INFM` row with `CNFM_YN='Y'` (see [business-flow.md §3](business-flow.md#3-transaction-status-mapping)).
- **Matched?** — `ACCT_TRNS_HSTR.LINK_MATCH_YN='Y'` + a `BUSI_MATCH_LOG` row with `LOCKED_YN='Y'`
  (Smart Organizer).
- **Classified?** — purpose/VAS/contact live on the rcv_pay row; reporting account on `ACCT_TRNS_HSTR`
  directly.

---

## 6. Migrations

No FCY-specific schema migration. FCY transaction history rides on the **same banking schema** as VND:

- **V13** — banking schema (`ACCT_MSTR`, `ACCT_TRNS_HSTR`, `ACCT_TRNS_RCV_PAY_INFM`).
- **V16** — `EXT_TRAN_CALL_HIS` (sync audit).
- **V21–V23** — column-parity corrections incl. `ACCT_TRNS_RCV_PAY_INFM` legacy column rename + the
  `RCV_PAY_*`/`CNFM_*` columns and `BANK_CD`/`ACCT_NO`/`TRSC_DT` locators.
- **V155 → V169 / V171 / V167 / V181** — View Setting seed for `banking-transaction-history` (shared
  VND + FCY) + the BUG-2 column additions / contact / unlock follow-ups.

`CCY_CD` is part of the V13 `ACCT_TRNS_HSTR` shape (column-for-column DEV parity) — no extra migration
was needed to enable the FCY currency filter.

# Transaction History (VND) — Database Mapping (Backend)

Legacy column → refactor entity field → DTO field → UI column, for the refactored Transaction
History (VND) backend. The canonical schema reference is
[references/database/banking/account-inquiry-vnd-db.md](../../../../references/database/banking/account-inquiry-vnd-db.md)
and the [banking ERD](../../../../references/database/erd/banking-erd.md); this doc maps only the
columns the feature reads/writes. DB-verified column shapes are in the JEX source of truth:
[docs/jex/features/transaction-history-vnd/database-mapping.md](../../../../../../docs/jex/features/transaction-history-vnd/database-mapping.md).

All tables are PROD-active-list tables and mirror the legacy `wecmsvndev` schema column-for-column
(per the backend CLAUDE.md Legacy Schema Parity Rule); the JPA entities map only the subset this
feature reads/writes.

---

## 1. Core tables

| Table | Role | PK |
|---|---|---|
| `ACCT_TRNS_HSTR` | bank credit/debit transaction rows (the grid) | `(BIZ_KEY, ACCT_SEQ_NO)` |
| `ACCT_TRNS_RCV_PAY_INFM` | per-transaction confirmation + classification (receipt/payment info) | `(BIZ_KEY, ACCT_SEQ_NO, RCV_PAY_SEQ_NO)` |
| `ACCT_MSTR` | account master (resolves `{acctSeqNo}` → account; currency, nickname) | `(BIZ_KEY, BANK_CD, ACCT_NO, …)` |
| `CHART_ACCT_INFM` | reporting-account picker validation | `(BIZ_KEY, CHART_ACCT_SEQ_NO)` |
| `BUSI_USAG_SET_INFM` | purpose picker validation | tenant-scoped |
| `BUSI_VAS_ACCT_INFM` | VAS-account picker validation | tenant-scoped |
| `TRCO_INFM` | contact picker validation + name snapshot | `TRCO_KEY` |

> **Tables referenced by the legacy IDO but NOT entities in the refactor** (out of PROD active scope
> or deferred): `RMRK_ORG_INFM`, `BUSI_MATCH_LOG`, `SCDUL_PYMN_SUMR`, `OVRS_PYMN_SUMR`,
> `PRJT_INFM`. The legacy `ACCT_TRNS_HSTR_R016` joined ~14 lookup tables to compose memo/colour,
> project name, and matched-by; the refactor reads only the active subset and projects the rest via
> the receipt/pay row + Java. The `NAPAS_REMIT_TRNS_HSTR` table belongs to the **transfer-history**
> feature, not this one — do not confuse the legacy NAPAS transfer screen (`trns_0003_001`) with
> account transaction history (`fnsh_0002_001`).

## 2. `ACCT_TRNS_HSTR` → `AcctTrnsHstr` entity

| Legacy column | Type (DEV) | Entity field | Notes |
|---|---|---|---|
| `BIZ_KEY` | varchar(20) | `bizKey` (@Id) | tenant |
| `ACCT_SEQ_NO` | varchar(20) | `acctSeqNo` (@Id) | transaction surrogate key |
| `BANK_CD` | varchar(20) | `bankCd` | loose link to `ACCT_MSTR` |
| `ACCT_NO` | varchar(100) | `acctNo` | loose link to `ACCT_MSTR` |
| `TRSC_DT` | varchar(8) | `trscDt` | `yyyyMMdd` |
| `TRSC_TM` | varchar(6) | `trscTm` | `HHmmss` |
| `CCY_CD` | varchar(3) | `ccyCd` | VND / USD / … |
| `INOUT_DV` | varchar(1) | `inoutDv` | `1`=IN, `2`=OUT, `0`=ETC (resolved via `InoutDv`) |
| `TRSC_AMT` | numeric(20,6) | `trscAmt` | transaction amount |
| `BAL` | numeric(20,6) | `bal` | running balance (bank-supplied; not recomputed) |
| `RMRK` | varchar(500) | `rmrk` | primary remark/description |
| `RMRK2` | varchar(500) | `rmrk2` | secondary remark |
| `BANK_REF_NO` | varchar(100) | `bankRefNo` | bank reference |
| `LINK_MATCH_YN` | varchar(1) | `linkMatchYn` | Smart Organizer match flag |
| `CHART_ACCT_CD` | varchar(20) | `chartAcctCd` | reporting-account code (nullable) |
| `CHART_ACCT_SEQ_NO` | varchar(20) | `chartAcctSeqNo` | reporting-account seq (nullable) |
| `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM` | audit | (via `BaseEntity`) | |

> **Legacy contract/DB mismatch carried forward (documented in JEX):** `RMRK` is `varchar(500)` in
> the DB but the legacy `_r001` WSVC REC declared length `50`. The refactor maps the true DB length
> (500) and the wire carries the full value. See
> [docs/jex/features/transaction-history-vnd/refactor-gap-analysis.md](../../../../../../docs/jex/features/transaction-history-vnd/refactor-gap-analysis.md).

## 3. `ACCT_TRNS_RCV_PAY_INFM` → `AcctTrnsRcvPayInfm` entity

| Legacy column | Type | Entity field | Notes |
|---|---|---|---|
| `BIZ_KEY` | varchar(20) | `bizKey` (@Id) | |
| `ACCT_SEQ_NO` | varchar(20) | `acctSeqNo` (@Id) | FK-by-value to `ACCT_TRNS_HSTR` |
| `RCV_PAY_SEQ_NO` | varchar(20) | `rcvPaySeqNo` (@Id) | per-(BIZ_KEY,ACCT_SEQ_NO) MAX+1 (Java) |
| `RCV_PAY_TRCO_KEY` | varchar(20) | `rcvPayTrcoKey` | contact key (nullable) |
| `RCV_PAY_TRCO_NM` | varchar(250) | `rcvPayTrcoNm` | contact name snapshot (nullable) |
| `RCV_PAY_USAG_CD` | varchar(20) | `rcvPayUsagCd` | purpose code (nullable) |
| `VAS_ACCT_CD` | varchar(50) | `vasAcctCd` | VAS account code (nullable) |
| `RCV_PAY_AMT` | numeric(20,6) | `rcvPayAmt` | amount copy |
| `CNFM_YN` | varchar(1) | `cnfmYn` | confirmation flag — `Y`/`N` |
| `CNFM_BY_USER_ID` | varchar(100) | `cnfmByUserId` | confirmer id (nullable) |
| `CNFM_BY_USER_NM` | varchar(50) | `cnfmByUserNm` | confirmer name (nullable) |
| `CNFM_DTM` | varchar(14) | `cnfmDtm` | `yyyyMMddHHmmss` (nullable) |

## 4. Full column-mapping chain (legacy → entity → DTO → UI)

| Concept | Legacy column | Entity field | DTO field (wire) | UI column |
|---|---|---|---|---|
| Transaction Date | `ACCT_TRNS_HSTR.TRSC_DT` | `trscDt` | `TRSC_DT` | TRSC_DTM (date part) |
| Transaction Time | `TRSC_TM` | `trscTm` | `TRSC_TM` | TRSC_DTM (time part) |
| Account Number | `ACCT_NO` (+ `BANK_CD`) | `acctNo` / `bankCd` | (in `acctSeqNo` composite) | resolved upstream |
| Account Name | `ACCT_MSTR.ACCT_NICK_NM` | (Account Inquiry) | breadcrumb | account header |
| Bank Name | `BANK_INFM.RPRS_BANK_NM` via `BankCdResolver` | — | breadcrumb | account header |
| Currency | `CCY_CD` | `ccyCd` | `CCY_CD` (detail) | account context |
| Withdrawal Amount | `TRSC_AMT` when `INOUT_DV='2'` | `trscAmt`+`inoutDv` | `WITHDRAW_AMT` | WITHDRAW_AMT |
| Deposit Amount | `TRSC_AMT` when `INOUT_DV='1'` | `trscAmt`+`inoutDv` | `DEPOSIT_AMT` | DEPOSIT_AMT |
| Balance | `BAL` | `bal` | `BAL` | BAL |
| Description / Remark | `RMRK` (+ `RMRK2`) | `rmrk` / `rmrk2` | `RMRK` / `RMRK2` | RMRK |
| Bank reference | `BANK_REF_NO` | `bankRefNo` | `BANK_REF_NO` | BANK_REF_NO |
| Counterparty (Contact) | `RCV_PAY_TRCO_NM` | `rcvPayTrcoNm` | `RCV_PAY_TRCO_NM` | CONTACT_NM |
| Purpose | `RCV_PAY_USAG_CD` | `rcvPayUsagCd` | `USE_USAG_CD`/`RCV_PAY_USAG_CD` | PURPOSE |
| VAS account | `VAS_ACCT_CD` | `vasAcctCd` | `VAS_ACCT_CD` | VAS_ACCT_CD |
| Reporting account | `CHART_ACCT_CD`/`CHART_ACCT_SEQ_NO` | `chartAcctCd`/`chartAcctSeqNo` | `CHART_ACCT_CD`/`CHART_ACCT_SEQ_NO` | CHART_ACCT_CD |
| Transaction Type | `INOUT_DV` | `inoutDv` | `INOUT_DV` | (drives deposit/withdraw split) |
| Confirmation | `CNFM_YN` | `cnfmYn` | `CONF_YN` | CONF_YN / status tab |
| Confirmed-by | `CNFM_BY_USER_NM` | `cnfmByUserNm` | `CONFIRMED_BY`/`CONFIRMED_BY_NM` | CONFIRMED_BY |
| Sync datetime | (Account Inquiry `ACCT_BAL_LST_INQ_DTM`) | — | — | "Last Update" on Account Inquiry, not this grid |

> **Sync Datetime** is an Account-Inquiry concern (the per-account last-inquiry timestamp), not a
> per-transaction column. Transaction rows carry `TRSC_DT`/`TRSC_TM` (when the transaction occurred),
> not a sync timestamp.

## 5. Status & classification columns

| Column | Table | Meaning |
|---|---|---|
| `CNFM_YN` | `ACCT_TRNS_RCV_PAY_INFM` | confirmation: `Y` confirmed / `N` (or no row) unconfirmed |
| `LINK_MATCH_YN` | `ACCT_TRNS_HSTR` | Smart Organizer payment-match: `Y` matched / `N` |
| `CHART_ACCT_CD` | `ACCT_TRNS_HSTR` | assigned reporting (chart-of-accounts) code |
| `RCV_PAY_USAG_CD` / `VAS_ACCT_CD` / `RCV_PAY_TRCO_KEY` | `ACCT_TRNS_RCV_PAY_INFM` | purpose / VAS / contact classification |

## 6. Migrations

| Version | Change |
|---|---|
| V13 | Sprint-1 banking schema — creates `ACCT_TRNS_HSTR`, `ACCT_TRNS_RCV_PAY_INFM` (+ later column-parity fixes V18/V19/V21/V23/V28). |
| V155 | View Setting seed for `banking-transaction-history` / `default` (initial 12-field set). |
| V167 | Unlock `TRSC_DTM` (`FIX_YN='N'`) for the screen. |
| V169 | Realign View Setting columns with the legacy on-screen table. |
| V171 | Add `CONTACT_NM` + `CONFIRMED_BY` View Setting columns. |
| V181 | Defensive `TRSC_DTM` unlock in `USER_SCR_OPTION_WECMS`. |

No schema change is owned solely by this feature beyond the View Setting seed — the transaction tables
were created in the Sprint-1 banking schema and are shared with Account Inquiry, Reports, and Smart
Organizer.

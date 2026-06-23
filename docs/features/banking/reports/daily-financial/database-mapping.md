# Daily Financial — Database Mapping

Column types verified live against the JEX DEV `wecmsvndev` schema (read-only `postgres` MCP,
2026-06-04). SQL bodies quoted from `WECMS_ETC/xml/service/IDO/IDO.*.xml`. No assumptions.

---

## 1. PROD-scope verdict (refactor)

Per `wecms-api/CLAUDE.md` → "PROD DB Table Scope for Refactoring", only these tables touched by this
feature are in the **PROD Active Table List**:

| Table | Role in feature | In PROD scope? |
|---|---|---|
| `ACCT_MSTR` | account master (active filter, type, balance) | ✅ yes |
| `ACCT_TRNS_HSTR` | raw transactions (REC2_1 source, txn popup) | ✅ yes |
| `ACCT_TRNS_RCV_PAY_INFM` | receipt/payment info (purpose, counterparty) | ✅ yes |
| `DAILY_FINCL_REPT_DAILY_SUMR_DETL` | per-account daily summary (REC/REC1/REC2) | ✅ yes |
| `DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR` | per-account daily txn summary (REC2_1) | ✅ yes |
| `DAILY_FINCL_REPT_DAILY_CASH_SUMR` | cash daily summary (REC3) | ❌ **not in list** |
| `DAILY_FINCL_REPT_DAILY_CASH_SUMR_DETL` | cash detail | ❌ **not in list** |
| `DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR` | rcv/pay daily summary (REC2_1 purpose) | ❌ **not in list** |
| `CASH_REPY_MSTR` | cash receipt/payment master | ❌ **not in list** |

**Consequence:** the cash book (`REC3`), the cash-book detail popup (`rept_0015_004`), and the
receipt/payment-purpose drilldown (`rept_0015_003`) are **out of refactor scope** — their backing
tables are not refactored. This is the documented reason REC3 was dropped from the refactor DTO. The
purpose-name column on inline transactions degrades (no `DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR`)
unless re-derived from the in-scope `ACCT_TRNS_RCV_PAY_INFM`.

---

## 2. Tables — purpose, key columns, CRUD

CRUD legend (this feature): **R**ead, **C**reate (insert via C002), **D**elete (D002). Operational
source tables are read-only here (written by other features / scraping / batch).

### 2.1 `DAILY_FINCL_REPT_DAILY_SUMR_DETL` — per-account daily summary

Purpose: one row per (biz_key, ref_date, currency, account) carrying the day's aggregated
debit/credit counts+amounts and end-of-day balance. Primary source for REC, REC1, REC2.
CRUD here: **R** (R001/R003/R004), **D** (D002), **C** (C002 → `wb_insert_daily_fincl_rept_daily_sumr_detl_data`).

| Column | Type (live) | Null | Meaning |
|---|---|---|---|
| biz_key | varchar(20) | NO | tenant |
| ref_date | varchar(8) | NO | as-of date YYYYMMDD |
| ccy_cd | char(3) | NO | currency |
| acct_type_cd | char(2) | NO | 01 loan…06 savings-USD |
| bank_cd | varchar(20) | NO | bank code |
| acct_no | varchar(100) | NO | account number |
| bal | numeric(20,6) | YES | end-of-day balance |
| acct_nick_nm | varchar(50) | YES | nickname (R001 falls back to bank+last4) |
| loan_nm | varchar(100) | YES | loan name |
| expi_dt | varchar(8) | YES | maturity date |
| agmt_amt | numeric(20,6) | YES | agreement / limit amount |
| snd_cnt / snd_amt | numeric(20,6) | YES | debit count / amount |
| rcv_cnt / rcv_amt | numeric(20,6) | YES | credit count / amount |
| intl_snd_cnt / intl_snd_amt | numeric(10,0)/(20,6) | YES | internal debit count / amount |
| intl_rcv_cnt / intl_rcv_amt | numeric(10,0)/(20,6) | YES | internal credit count / amount |
| bank_nm | varchar(100) | YES | bank display name |

### 2.2 `DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR` — per-account daily transaction summary

Purpose: the day's individual transactions per account (feeds REC2_1 `json_agg`).
CRUD here: **R** (inside R004 subquery), **D** (D002), **C** (C002 → `INSERT…SELECT FROM ACCT_TRNS_HSTR`).

| Column | Type (live) | Null | Meaning |
|---|---|---|---|
| biz_key | varchar(20) | NO | tenant |
| ref_date | varchar(8) | NO | = ACCT_TRNS_HSTR.TRSC_DT |
| bank_cd | varchar(20) | NO | bank |
| acct_no | varchar(100) | NO | account |
| acct_seq_no | varchar(20) | NO | transaction sequence |
| trsc_tm | varchar(6) | YES | time HHMMSS |
| ccy_cd | char(3) | NO | currency |
| trsc_amt | numeric(20,6) | YES | amount |
| bal | numeric(20,6) | YES | running balance |
| inout_dv | char(1) | NO | 1 = credit/in, 2 = debit/out |
| rmrk | varchar(500) | YES | remark |

### 2.3 `DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR` — daily receipt/payment summary *(out of PROD scope)*

Purpose: receipt/payment usage codes + counterparty per transaction; LEFT-joined in R004 to label
each REC2_1 transaction with a purpose name (`wb_get_usag_nm`). CRUD: **R/D/C** (C002 from
`ACCT_TRNS_RCV_PAY_INFM`). Key columns: biz_key, ref_date, bank_cd, acct_no, acct_seq_no,
rcv_pay_seq_no numeric(5,0), rcv_pay_amt, usag_detl_cd, rcv_pay_usag_cd, rcv_pay_trco_key/nm/enm,
cont, dc_amt, fee, prjt_no, split_yn char(1), ref_no1/2/3.

### 2.4 `DAILY_FINCL_REPT_DAILY_CASH_SUMR` — cash daily summary *(out of PROD scope)*

Purpose: per-currency cash opening/closing balance (REC3). CRUD: **R** (R001/R003), **D**, **C**
(→ `wb_insert_daily_fincl_rept_daily_cash_sumr_data`).

| Column | Type (live) | Null | Meaning |
|---|---|---|---|
| biz_key | varchar(20) | NO | tenant |
| ref_date | varchar(8) | NO | as-of date |
| ccy_cd | char(3) | NO | currency |
| bef_date_bal | numeric(20,6) | NO | opening balance |
| bal | numeric(20,6) | YES | closing balance |

### 2.5 `DAILY_FINCL_REPT_DAILY_CASH_SUMR_DETL` — cash detail *(out of PROD scope)*

Purpose: per-transaction cash receipts/payments with re-derived running balance (REC3 OBJECT
counts; cash-book popup). CRUD: **R/D/C** (C002 from `CASH_REPY_MSTR`, correlated running balance).
Key columns: biz_key, ref_date, ccy_cd, cash_repy_seq_no numeric(10,0), inout_dv char(1),
trsc_amt, bal, trco_key/nm/enm, use_usag_cd, prjt_no, cont, hand_reg_yn, ref_no1/2/3.

### 2.6 Operational source tables (read-only in this feature)

| Table | Used for | Key columns referenced |
|---|---|---|
| `ACCT_MSTR` | active-account subquery; refactor balance source | biz_key, bank_cd, acct_no, acct_type_cd, ccy_cd, bal, actv_yn, acct_nick_nm, expi_dt, agmt_amt, prn_seq |
| `ACCT_TRNS_HSTR` | C002 source for ACCT_TRNS_HSTR_SUMR; refactor txn-popup source | biz_key, trsc_dt, bank_cd, acct_no, acct_seq_no, trsc_tm, ccy_cd, trsc_amt, bal, inout_dv, rmrk |
| `ACCT_TRNS_RCV_PAY_INFM` | C002 source for TRNS_RCV_PAY_HSTR_SUMR | biz_key, trsc_dt, bank_cd, acct_no, acct_seq_no, rcv_pay_seq_no, rcv_pay_amt, usag_detl_cd, rcv_pay_usag_cd, rcv_pay_trco_*, cont, prjt_no, dc_amt, fee, split_yn |
| `CASH_REPY_MSTR` *(out of scope)* | C002 source for CASH_SUMR(_DETL) | biz_key, trsc_dt, cash_repy_seq_no, inout_dv, trsc_amt, use_usag_cd, ccy_cd, trco_*, prjt_no, cont, adv_paym_seq, adv_paym_trns_seq, attach_doc_id |

> **`ACCT_TRNS_HSTR.TRSC_TM` may be empty (verified 2026-06).** `TRSC_TM` is `VARCHAR(6)` (`HHmmss`)
> and the bank-scrape source does not always populate it — on the dev DB the 5 transactions on
> `2026-06-04` for `VN_VNM0004_000001` all carry `trsc_tm = ''` (empty string, length 0, **not
> null**). The Transaction History Details popup therefore renders a **blank Time cell by design** for
> those rows (backend → mapper → UI bind `TRSC_TM` correctly; legacy `rept_0015_001.ts` is identical).
> This is **not a mapping defect** — see [README.md §6](README.md#6-time-column-verification-transaction-history-details-popup)
> and [parity-verification-report.md §13](parity-verification-report.md#13-time-column-verification-2026-06).

---

## 3. Functions / procedures (live `wecmsvndev`, confirmed)

| Function | Signature | Returns | Purpose | Refactor stance |
|---|---|---|---|---|
| `wb_insert_daily_fincl_rept_daily_sumr_detl_data` | `(p_biz_key varchar, p_req_date varchar, p_req_date2 varchar)` | `void` | Rebuilds `DAILY_FINCL_REPT_DAILY_SUMR_DETL` for a date range. Called by C002. | **Not ported** — batch owns writes (CLAUDE.md §"Legacy report DB functions"). |
| `wb_insert_daily_fincl_rept_daily_cash_sumr_data` | `(p_biz_key, p_req_date, p_req_date2)` | `void` | Rebuilds `DAILY_FINCL_REPT_DAILY_CASH_SUMR`. | **Not ported** — out of scope + batch-owned. |
| `rept_0015_001_r001_rec1` | `(p_biz_key, p_req_date, p_req_date2)` | `record` (set-returning) | Live per-account projection over `ACCT_MSTR`/`ACCT_TRNS_HSTR` (as-of balance + same-day SND/RCV + internal split); legacy also used it in the `SUMR_DETL_R002` `EXCEPT` drift check. | **Called directly (2026-06-14)** via `DailyFinancialCmcFunctionRepository` → `ReportService.dailyFinancial` (`ReportService.java:147-225`). Documented set-returning exception (CLAUDE.md §"Legacy report DB functions"); **not** re-expressed in Java. |
| `rept_0015_001_r001_rec3` | `(p_biz_key, p_req_date, p_req_date2)` | `record` (set-returning) | Live cash projection; used in `CASH_SUMR_R002` `EXCEPT` drift check. | Out of scope (cash). |
| `wb_get_bank_nm` | `(_bank_cd varchar [, _type_nm varchar])` | `varchar` | Bank display / short name. `'s'` = short. Used in R001 nickname fallback. | Refactor uses `BANK_INFM` → `BankCdResolver`; do **not** port the function. |
| `wb_get_usag_nm` | `(_biz_key varchar, _usag_cd varchar, _lang_code varchar)` | `varchar` | Receipt/payment purpose name (localized). Used in R004 for REC2_1. | Out of scope (purpose summary table); if needed, resolve via `USAG_INFM`/`USAG_INFM_LANG_DETL`. |

No views or sequences are unique to this feature (the summary tables are plain tables; the report
uses no `*_seq` of its own).

---

## 4. IDO inventory (CRUD per table)

| IDO | Type | Table | Role |
|---|---|---|---|
| `DAILY_FINCL_REPT_DAILY_SUMR_DETL_R001` | SELECT | SUMR_DETL | REC1 flat per-account rows |
| `…_SUMR_DETL_R002` | SELECT (COUNT) | SUMR_DETL vs `rept_0015_001_r001_rec1()` | freshness drift check |
| `…_SUMR_DETL_R003` | SELECT | SUMR_DETL + CASH_SUMR subquery | REC currency totals |
| `…_SUMR_DETL_R004` | SELECT | SUMR_DETL + ACCT_TRNS_HSTR_SUMR + TRNS_RCV_PAY_HSTR_SUMR | REC2 + nested REC2_1 json |
| `…_SUMR_DETL_D002` | DELETE | SUMR_DETL | delete day before recompute |
| `…_SUMR_DETL_C002` | SELECT (fn) | → `wb_insert_daily_fincl_rept_daily_sumr_detl_data` | recompute insert |
| `…_CASH_SUMR_R001` | SELECT | CASH_SUMR | cash totals |
| `…_CASH_SUMR_R002` | SELECT (COUNT) | CASH_SUMR vs `rept_0015_001_r001_rec3()` | cash drift check |
| `…_CASH_SUMR_R003` | SELECT | CASH_SUMR + CASH_SUMR_DETL json | REC3 |
| `…_CASH_SUMR_D002` | DELETE | CASH_SUMR | delete day |
| `…_CASH_SUMR_C002` | SELECT (fn) | → `wb_insert_daily_fincl_rept_daily_cash_sumr_data` | recompute insert |
| `…_ACCT_TRNS_HSTR_SUMR_R002` | SELECT (COUNT) | vs ACCT_TRNS_HSTR | drift check |
| `…_ACCT_TRNS_HSTR_SUMR_D002` | DELETE | ACCT_TRNS_HSTR_SUMR | delete day |
| `…_ACCT_TRNS_HSTR_SUMR_C002` | INSERT | ← ACCT_TRNS_HSTR | recompute insert |
| `…_CASH_SUMR_DETL_R002` | SELECT (COUNT) | vs CASH_REPY_MSTR | drift check |
| `…_CASH_SUMR_DETL_D002` | DELETE | CASH_SUMR_DETL | delete day |
| `…_CASH_SUMR_DETL_C002` | INSERT | ← CASH_REPY_MSTR (running bal) | recompute insert |
| `…_TRNS_RCV_PAY_HSTR_SUMR_R002` | SELECT (COUNT) | vs ACCT_TRNS_RCV_PAY_INFM | drift check |
| `…_TRNS_RCV_PAY_HSTR_SUMR_D002` | DELETE | TRNS_RCV_PAY_HSTR_SUMR | delete day |
| `…_TRNS_RCV_PAY_HSTR_SUMR_C002` | INSERT | ← ACCT_TRNS_RCV_PAY_INFM | recompute insert |

Full SQL bodies are quoted in [interface-specification.md §5](interface-specification.md#5-key-sql-verbatim)
and [source-code-index.md](source-code-index.md).

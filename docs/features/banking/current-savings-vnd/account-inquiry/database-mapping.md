# Account Inquiry (VND) — Database Mapping (Refactor Backend)

**Screen ID:** `banking-account-inquiry-vnd` · **JEX:** `fnsh_0001_001` · **Hub:** [README.md](README.md)

Tables, the legacy-column → entity-field → DTO-field → UI-column chain, and status-code mapping.
Column types below were verified against JEX DEV (`postgres_dev_server_jexframe`, 2026-06-06); the
refactor mirrors them column-for-column. Companion: [references/database/banking/account-inquiry-vnd-db.md](../../../../references/database/banking/account-inquiry-vnd-db.md).

---

## 1. Core tables (all in the PROD active list)

| Table | Cols (DEV) | Role | Refactor entity |
|---|---|---|---|
| `ACCT_MSTR` | (full) | Account master — one row per (BIZ_KEY × BANK_CD × ACCT_NO). Balance, available, status, last-inq. | `AcctMstr` (`@IdClass AcctMstrId`) |
| `ACCT_AUTH_INFO` | 10 | Per-user account authority (view/withdraw grant). Drives the non-admin `EXISTS` filter. | `AcctAuthInfo` |
| `EXT_TRAN_CALL_HIS` | 14 | External real-time call log (`SVC_ID=REAL_TIME_ONLINE_INQUIRY`). Source for Sync History. | (sync-history read entity) |
| `BANK_CERT_INFO` | 16 | Bank credential/cert per (BIZ_KEY, BANK_CD). `cert_conn_yn` derives from ≥1 non-blank credential. | (resolver/repo) |
| `BANK_INFM` | 8 | Bank registry — `BANK_CD → RPRS_BANK_NM`. Bank display-name resolution. | `BankInfm` → `BankCdResolver` |
| `BANK_INFM_DETL` | — | Short bank names (`BANK_DETL_INFO_CD='10'`) — input to `wb_get_bank_nm`/`wb_format_acct_nick_nm`. | (V44 seed) |
| `ACCT_TRNS_HSTR` | (full) | Transaction history (account detail / drill-down + sync writes). | `AcctTrnsHstr` |

> **Refactoring enforcement:** every table above is in the PROD Active Table List. The screen does not
> create its own Flyway table — it consumes `ACCT_MSTR` (carried over Sprint 1) plus `BANK_INFM` (V20),
> `BANK_INFM_DETL` + nickname functions (V44), and the View Setting seed (V30→V35, V41/V42 for `VAS_ACCOUNT`).

---

## 2. `ACCT_MSTR` key columns (DEV-verified) → entity → DTO → UI

| Legacy column | Type (DEV) | Entity field (`AcctMstr`) | Wire field (`AccountRow`) | UI column |
|---|---|---|---|---|
| `BIZ_KEY` | varchar(20) NN | `bizKey` `@Id` | — (tenant scope) | — |
| `BANK_CD` | varchar(20) NN | `bankCd` `@Id` | `bank_cd` | Bank (chip) |
| `ACCT_NO` | varchar(100) NN | `acctNo` `@Id` | `acct_no` | Bank cell sub-line |
| (synthetic) | — | — | `acct_seq_no` = `BANK_CD_ACCT_NO` | client row key |
| `ACCT_TYPE_CD` | char(2) NN | `acctTypeCd` | `acct_type_cd` (+ `acct_type_nm`, `acct_type_key`) | Bank cell chip |
| `CCY_CD` | char(3) NN | `ccyCd` (trimmed) | `ccy_cd` | currency chip (`VND`) |
| `ACCT_NICK_NM` | varchar(50) | `acctNickNm` (+ `formattedAcctNickNm` `@Formula`) | `acct_nick_nm` (formatted) | Account / Nickname |
| `ACCT_NM` | varchar(100) | `acctNm` | — | (lookup only) |
| `BAL` | numeric | `bal` | `bal` (wire decimal) | **Balance** |
| `REAL_AMT` | numeric | `realAmt` | `real_amt` (wire decimal) | **Available Balance** |
| `CORC_BF_BAL` | numeric | `corcBfBal` | — | prior balance (refresh logic) |
| `ACCT_BAL_INQ_STTS` | varchar(20) | `acctBalInqStts` | `acct_bal_inq_stts` | status → row error flag |
| `ACCT_BAL_INQ_MSG` | varchar(255) | `acctBalInqMsg` | — | Last-Update tooltip (failure) |
| `ACCT_BAL_UPD_DTM` | varchar(14) | `acctBalUpdDtm` | — | (when BAL last corrected) |
| `ACCT_BAL_LST_INQ_DTM` | varchar(14) | `acctBalLstInqDtm` | `acct_bal_upd_dtm` *(FD-only fallback source — FCY-A02)* | **Last Update** (fixed-deposit-only) |
| `ACCT_HIS_INQ_STTS` | varchar(20) | `acctHisInqStts` | — | Last-Update branch (history) |
| `ACCT_HIS_INQ_MSG` | varchar(255) | `acctHisInqMsg` | — | Last-Update text (history fail) |
| `ACCT_HIS_LST_INQ_DT` | varchar(14) | `acctHisLstInqDt` | `acct_bal_upd_dtm` *(primary source for online accts; also the `LAST_UPDATE` sort key — FCY-A02)* | **Last Update** (online accts) + sort key |
| `LST_LGN_STTS` / `_MSG` / `LST_LGN_SUC_DTM` | varchar(20)/(255)/(14) | `lstLgnStts` / `lstLgnSttsMsg` / `lstLgnSucDtm` | — | login-job status (HR-2) |
| `HAND_REG_YN` | varchar(1) | `handRegYn` | `hand_reg_yn` (coerced Y/N) | selectable / "manual" disable |
| `BANK_CERT_SEQ` | numeric | `bankCertSeq` | — | cert FK |
| `PRN_SEQ` | numeric | `prnSeq` (`@JdbcTypeCode(NUMERIC)`) | — | default sort key |
| `ACTV_YN` | char(1) NN | `actvYn` | — | repo filters `'Y'` |
| `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM` | — | `BaseEntity` audit | — | — |
| (derived from `BANK_CERT_INFO`) | — | — | `cert_conn_yn` (Y/N) | row selectability / "disconnected" |
| (resolved via `BankCdResolver`) | — | — | `bank_nm` | Bank name |

> **`acct_type_key`** routes through `AcctTypeCd.fromCode(code).i18nKey()` (`bank.acct_type.NN`); `null` for
> unknown codes and `01` (LOAN). The legacy English label rides on `acct_type_nm` as a one-cycle fallback.

---

## 3. Requested column mapping (prompt checklist)

| Concept | Legacy column → Entity field → DTO field → UI |
|---|---|
| Account Number | `ACCT_NO` → `acctNo` → `acct_no` → Bank cell sub-line |
| Account Name | `ACCT_NICK_NM` (formatted) → `acctNickNm`/`formattedAcctNickNm` → `acct_nick_nm` → Account / Nickname |
| Currency | `CCY_CD` → `ccyCd` → `ccy_cd` → currency chip |
| Balance | `BAL` → `bal` → `bal` → Balance |
| Available Balance | `REAL_AMT` → `realAmt` → `real_amt` → Available Balance |
| Last Update Datetime | account-type-aware (`AccountService.resolveLastUpdate`): online accts `ACCT_HIS_LST_INQ_DT` → `acctHisLstInqDt`; FD-only banks `ACCT_BAL_LST_INQ_DTM` → `acctBalLstInqDtm` → wire `acct_bal_upd_dtm` → Last Update (FCY-A02) |
| Account Status | `ACCT_BAL_INQ_STTS` → `acctBalInqStts` → `acct_bal_inq_stts` → status / Failed flag |
| Sync Status | `LST_LGN_STTS` / `ACCT_HIS_INQ_STTS` → `lstLgnStts` / `acctHisInqStts` → (status calc) → Last Update branch |
| Bank Code | `BANK_CD` → `bankCd` → `bank_cd` → Bank chip |
| Bank Name | `BANK_INFM.RPRS_BANK_NM` (via `BankCdResolver`) → — → `bank_nm` → Bank |

---

## 4. Status mapping {#status-mapping}

| Concept | DB value (`ACCT_MSTR`) | Service handling | Wire | UI display |
|---|---|---|---|---|
| Success (balance) | `ACCT_BAL_INQ_STTS = '00000000'` | shipped verbatim | `acct_bal_inq_stts: "00000000"` | datetime from `ACCT_BAL_LST_INQ_DTM` |
| Success (history, no new tx) | `ACCT_HIS_INQ_STTS = '42110000'` | terminal success | (history branch) | datetime |
| Failed | any other / error code | shipped verbatim | `acct_bal_inq_stts: "<code>"` | red **Failed** + `..._INQ_MSG` |
| In progress (sync) | — (transient, sync session) | `TransactionsSyncResponse.status = OTP/CAPTCHA/QNA_REQUIRED` | challenge response | challenge dialog |
| No data / never inquired | status null | — | empty | "No update information" / blank |

The legacy `42110000` "no new transactions" code is treated as **success**, not failure — this is the
single most important parity nuance for the Last-Update column.

---

## 5. Migrations (relevant)

| Migration | Relevance |
|---|---|
| V13 / V23 / V28 | `ACCT_MSTR` carried over + parity columns + NUMERIC precision |
| V20 | `BANK_INFM` table + seed (bank names) |
| V30→V35 | View Setting tables + `banking-account-inquiry-vnd` column seed + schema parity |
| V33 | `CHECKBOX` special FIELD_ID seed |
| V34 | `SIZE` (Figma widths) for the screen's columns |
| V41 / V42 | `VAS_ACCOUNT` → `SORT_YN='N'` + `DISP_YN='N'` (no backing field yet) |
| V44 | `BANK_INFM_DETL` + `wb_get_bank_nm` / `wb_format_acct_nick_nm` functions; `AcctMstr.formattedAcctNickNm` `@Formula` |

---

## Cross-references

- DB design (original): [references/database/banking/account-inquiry-vnd-db.md](../../../../references/database/banking/account-inquiry-vnd-db.md)
- Banking ERD: [references/database/erd/banking-erd.md](../../../../references/database/erd/banking-erd.md)
- JEX DB mapping: [docs/jex/features/account-inquiry-vnd/database-mapping.md](../../../../../../docs/jex/features/account-inquiry-vnd/database-mapping.md)
- Interface spec: [interface-specification.md](interface-specification.md)

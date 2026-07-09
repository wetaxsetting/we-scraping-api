# Account Inquiry (FCY) — Database Mapping (Backend)

> Tables used, legacy-column → entity-field → DTO-field → UI-column mapping, status codes, and the FCY
> type-code set. Companion: [interface-specification.md](interface-specification.md) ·
> DB design reference: [references/database/banking/account-inquiry-fcy-db.md](../../../../references/database/banking/account-inquiry-fcy-db.md).

## 1. Core tables

| Table | Role | Read/Write on this feature |
|---|---|---|
| `ACCT_MSTR` | Account master — one row per `(BIZ_KEY, BANK_CD, ACCT_NO)`. Holds balance, status, last-update, currency. | **Read** (list/totals); **Write** by sync (`refreshBalance`, `markScrapeFailure`). |
| `ACCT_TRNS_HSTR` | Transaction history. | **Write** by sync; **Read** by the per-account transaction popup / drill-down (not on the list path). |
| `BANK_CERT_INFO` | Per-bank scraping credential. | **Read** to derive `cert_conn_yn` (`loadConnectedBanks`) and to resolve credentials during sync. |
| `BANK_INFM` | Bank registry (`BANK_CD` → display name). | **Read** via `BankCdResolver` for `bank_nm`. |
| `ACCT_AUTH_INFO` | Per-user account authority. | **Read** (EXISTS) to scope non-admin users. |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | Scrape audit log. | **Write** by the sync path. |

> **All in the PROD Active Table List.** There is **no** `EXT_TRNS_HSTR` / dedicated call-history table
> on this feature (grep-verified in the legacy investigation package). Legacy IDOs that touched the
> account list — `ACCT_MSTR_R004` (paged list, date-aware balance incl. the Vietcombank `11704004`
> special case), `_R004_CNT` (per-CCY totals), `_R001_CNT` (grand totals), `ACCT_TRNS_HSTR_R010`
> (history popup) — map to JPA queries on `AcctMstr` / `AcctTrnsHstr`; the date-aware-balance branch is
> **not** ported (FCY-G02, confirmed no-op on both sides).

## 2. `ACCT_MSTR` — entity / DTO / UI column mapping

Entity `AcctMstr` (`@Table("ACCT_MSTR")`, `@IdClass AcctMstrId`, composite PK `BIZ_KEY, BANK_CD,
ACCT_NO`). The wire is lowercase snake_case (global Jackson `SNAKE_CASE`).

| Concept | Legacy column | Entity field | Wire / DTO field (`AccountRow`) | UI column |
|---|---|---|---|---|
| Tenant | `BIZ_KEY` | `bizKey` (@Id) | — (never on wire) | — |
| Bank code | `BANK_CD` | `bankCd` (@Id) | `bank_cd` | BANK (sub) |
| Account number | `ACCT_NO` | `acctNo` (@Id) | `acct_no` | BANK (sub) |
| Synthetic account id | (none) | — | `acct_seq_no` (= `BANK_CD + "_" + ACCT_NO`) | row key |
| Bank name | (via `BANK_INFM`) | resolved | `bank_nm` | BANK |
| Account type | `ACCT_TYPE_CD` | `acctTypeCd` | `acct_type_cd` + `acct_type_nm` + `acct_type_key` | BANK (label) |
| **Currency** | `CCY_CD` | `ccyCd` | `ccy_cd` | BANK (label) / filter |
| Nickname | `ACCT_NICK_NM` | `acctNickNm` (+ `@Formula wb_format_acct_nick_nm`) | `acct_nick_nm` | ACCOUNT_NICKNAME |
| Current balance | `BAL` | `bal` | `bal` (string) | BALANCE |
| Available balance | `REAL_AMT` | `realAmt` | `real_amt` (string) | AVAILABLE_BALANCE |
| Balance inquiry status | `ACCT_BAL_INQ_STTS` | `acctBalInqStts` | `acct_bal_inq_stts` | (drives row error) |
| Balance inquiry msg | `ACCT_BAL_INQ_MSG` | `acctBalInqMsg` | — | (tooltip) |
| **Last update** | `ACCT_HIS_LST_INQ_DT` (online) / `ACCT_BAL_LST_INQ_DTM` (FD-only) | `acctHisLstInqDt` / `acctBalLstInqDtm` | `acct_bal_upd_dtm` (value from `resolveLastUpdate`) | LAST_UPDATE |
| History inquiry status | `ACCT_HIS_INQ_STTS` / `ACCT_HIS_INQ_MSG` | `acctHisInqStts` / `acctHisInqMsg` | — | (sync status) |
| Login status | `LST_LGN_STTS` / `LST_LGN_STTS_MSG` | `lstLgnStts` / `lstLgnSttsMsg` | — | (sync status) |
| Manual-reg flag | `HAND_REG_YN` | `handRegYn` | `hand_reg_yn` | (checkbox disable) |
| Cert connection | (derived from `BANK_CERT_INFO`) | — | `cert_conn_yn` | (checkbox disable) |
| Print order | `PRN_SEQ` | `prnSeq` | — | default sort key |
| Active flag | `ACTV_YN` | `actvYn` | — | (query filter) |

### Requested column mapping (per the audit brief)

```
Account Number       ACCT_NO              → acctNo            → acct_no            → BANK (sub-line)
Account Name         ACCT_NICK_NM         → acctNickNm        → acct_nick_nm       → ACCOUNT_NICKNAME
Currency             CCY_CD               → ccyCd             → ccy_cd             → BANK (label) / filter
Current Balance      BAL                  → bal              → bal               → BALANCE
Available Balance    REAL_AMT             → realAmt          → real_amt          → AVAILABLE_BALANCE
Last Update Datetime ACCT_HIS_LST_INQ_DT  → acctHisLstInqDt   → acct_bal_upd_dtm   → LAST_UPDATE
Account Status       ACCT_BAL_INQ_STTS    → acctBalInqStts    → acct_bal_inq_stts  → (row error indicator)
Sync Status          ACCT_HIS_INQ_STTS /  → acctHisInqStts /  → (internal)         → (sync dialog)
                     LST_LGN_STTS            lstLgnStts
Bank Code            BANK_CD              → bankCd            → bank_cd            → BANK
Bank Name            (BANK_INFM lookup)   → (resolved)        → bank_nm            → BANK
```

## 3. FCY type-code set

| Code | Meaning | VND | USD | KRW | **FCY** |
|---|---|:--:|:--:|:--:|:--:|
| 01 | LOAN | — | — | — | — |
| 02 | DEPOSIT (fixed) | ✔ | ✔ | ✔ | ✔ |
| 03 | CURRENT (VND) | ✔ | | | |
| 04 | CURRENT (USD) | | ✔ | | ✔ |
| 05 | SAVINGS (VND) | ✔ | | | |
| 06 | SAVINGS (USD) | | ✔ | | ✔ |
| 07 | CURRENT (KRW) | | | ✔ | ✔ |
| 08 | SAVINGS (KRW) | | | ✔ | ✔ |

`FCY_TYPE_CODES = {02, 04, 06, 07, 08}` (`AccountService.java:109-114`). Legacy USD branch = `{02,04,06}`
(`FnshHelper.java:425-430`); the KRW types (07/08) are the FCY-A01 multi-currency addition.

## 4. Status codes

| `ACCT_BAL_INQ_STTS` | Meaning | UI |
|---|---|---|
| `00000000` | Success | normal |
| `42110000` | No new transactions | normal (treated as success) |
| other | Error code | red "Error" tag; `ACCT_BAL_INQ_MSG` as tooltip |

## 5. `ACCT_TRNS_HSTR` (sync target / drill-down)

Composite PK `(BIZ_KEY, ACCT_SEQ_NO)`. Key columns: `BANK_CD`, `ACCT_NO`, `TRSC_DT`, `CCY_CD`,
`INOUT_DV` (IN/OUT), `TRSC_AMT`, `BAL`, `RMRK`/`RMRK2`, `BANK_REF_NO`, `BANK_TRNS_FULL_ID` (SHA-256
idempotency key), `BANK_TRNS_SHORT_ID`, `LOCKED_YN`, `CHART_ACCT_CD`/`CHART_ACCT_SEQ_NO` (Reporting
Account assignment). Written by sync (dedup on `BANK_TRNS_FULL_ID`); read by the per-account transaction
endpoint, not by the FCY list path.

## 6. Flyway / seeds

- View Setting seed for `banking-account-inquiry-fcy` (column set mirrors VND) — see migration headers
  (`V43`/`V47` per the gap analysis; column-seed contents assumed to match the VND `FALLBACK_COLUMNS`,
  NOT VERIFIED at row level).
- Menu permission `banking-fcy-update-inquire` seeded with the navigation catalogue (`V243`).
- Schema for `ACCT_MSTR` / `ACCT_TRNS_HSTR` landed in V13 + the V23 column-parity corrective; column-for-column
  DEV parity per the Legacy Schema Parity Rule.

> See [references/database/banking/account-inquiry-fcy-db.md](../../../../references/database/banking/account-inquiry-fcy-db.md)
> for the original DB design note (currency value distribution snapshot, `wb_format_acct_nick_nm` parity,
> tenant filtering).

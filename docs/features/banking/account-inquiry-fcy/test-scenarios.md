# Account Inquiry (FCY) — Test Scenarios (SIT/UAT)

> Backend-oriented scenarios for the refactored FCY feature. Frontend-oriented mirror:
> [wecms-web/docs/features/banking/account-inquiry-fcy/test-scenarios.md](../../../../../wecms-web/docs/features/banking/account-inquiry-fcy/test-scenarios.md).
> R&D scope: live-bank scraping is **not** validated locally (FCY-A03) — sync scenarios test the
> flow/contract, not real bank round-trips.

## A. Search / list

| # | Scenario | Expected |
|---|---|---|
| A1 | `GET /accounts?currency=FCY` | Only non-VND accounts (`ccy_cd <> 'VND'`), types ∈ `{02,04,06,07,08}`, tenant-scoped, active only. |
| A2 | Search by bank (`bankCd=11704001`) | Rows filtered to that bank; `banks_available` unchanged (full option set). |
| A3 | Search by currency (`currency=USD`) | Only `ccy_cd='USD'`; `totals_by_ccy` has a single USD entry. |
| A4 | Keyword on nickname | Matches the **formatted** nickname (case-insensitive `LIKE`); `%`,`_`,`!` treated literally. |
| A5 | No matching rows | `accounts:[]`, `total_count:0`, `totals_by_ccy:[]`; frontend shows `account_inquiry.empty_fcy`. |
| A6 | Tenant isolation | A row from another `BIZ_KEY` is never returned. |

## B. Multi-currency (FCY-A01)

| # | Scenario | Expected |
|---|---|---|
| B1 | Tenant holds USD + KRW accounts, `currency=FCY` | Both currencies returned; two `totals_by_ccy` tiles; each row's amounts formatted per its own CCY. |
| B2 | USD account | `ccy_cd='USD'`; balance formatted `$…`. |
| B3 | KRW account | `ccy_cd='KRW'`; balance formatted `₩…`. |
| B4 | JPY / EUR account | Returned under `currency=FCY` (no type restriction for non-USD/KRW); concrete narrow works. |
| B5 | VND account | **Never** appears on the FCY screen. |
| B6 | Per-CCY totals stable across pages | `totals_by_ccy` computed over the full filtered set, identical on page 1 and page 2. |

## C. Update balance / sync

| # | Scenario | Expected |
|---|---|---|
| C1 | Update one connected account (local env) | Flow reaches `BankTransactionSyncService`; in R&D the live scrape may be unavailable (document actual). Contract: `TransactionsSyncResponse` returned. |
| C2 | Successful sync (mock/SIT) | `status=COMPLETED` with counts/totals; `ACCT_MSTR.BAL/REAL_AMT` + `ACCT_HIS_LST_INQ_DT` updated; list refetch moves the Last Update column. |
| C3 | Challenge (OTP/QNA/CAPTCHA) | First call returns `*_REQUIRED` + `session_id`; resume via the matching verb continues. |
| C4 | Wrong resume verb | `WBE9001`. |
| C5 | No credential for bank | `WBE2050`; row keeps a failure status. |
| C6 | Account not in tenant | `WBE_BANK_ACCOUNT_NOT_FOUND`. |
| C7 | Failed scrape | `markScrapeFailure` stamps `LST_LGN_STTS` / inquiry-status columns (survives rollback); row renders red. |

## D. Last Update display (FCY-A02)

| # | Scenario | Expected |
|---|---|---|
| D1 | Online account with `ACCT_HIS_LST_INQ_DT` | Last Update shows that timestamp (humanized + tooltip). |
| D2 | FD-only bank (`type=02`, not Sacombank `11704004`) | Falls back to `ACCT_BAL_LST_INQ_DTM`. |
| D3 | `ACCT_HIS_LST_INQ_DT` null, online | Blank cell. |
| D4 | Inquiry-status error | Red "Error" tag; tooltip = `ACCT_BAL_INQ_MSG`. |
| D5 | Sort by `LAST_UPDATE` | ORDER BY `acctHisLstInqDt` (FCY-A02), then default secondary key. |

## E. Sorting

| # | Scenario | Expected |
|---|---|---|
| E1 | Sort by `BANK` ASC/DESC | ORDER BY `bankCd`; secondary default key appended. |
| E2 | Sort by `BALANCE` DESC | ORDER BY `bal` DESC. |
| E3 | Unknown / blank `sortField` | Falls back to `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` (no error, no SQL injection). |
| E4 | `VAS_ACCOUNT` (no backing field) | Not sortable (`SORT_YN='N'`); no glyph. |

## F. Pagination

| # | Scenario | Expected |
|---|---|---|
| F1 | `size=10`, navigate pages | `page_number` zero-based, `total_pages` correct, rows stable across navigation. |
| F2 | `size>100` | Clamped to `MAX_PAGE_SIZE=100`. |
| F3 | Totals vs. page | `total_count` + `totals_by_ccy` reflect the full filtered set, not the page slice. |

## G. Export

| # | Scenario | Expected |
|---|---|---|
| G1 | Export `currency=FCY` multi-CCY | XLSX with one subheader line per CCY; allowed columns only. |
| G2 | Export concrete CCY | Single subheader line (VND-style). |
| G3 | Export with no exportable columns | `WBE2013`. |
| G4 | Export >50k rows | `WBE2011`. |

## H. Permissions

| # | Scenario | Expected |
|---|---|---|
| H1 | Authorized user with `banking-fcy-update-inquire` | List/export/sync succeed. |
| H2 | Unauthorized user | 403 (`WBE1003`) on the gated endpoints. |
| H3 | Non-admin, partial account authority | Only `ACCT_AUTH_INFO`-permitted accounts returned; admin sees all. |

## I. Error handling

| # | Scenario | Expected |
|---|---|---|
| I1 | Unknown export `screenId` | `WBE2010`. |
| I2 | Sync session expired | `WBE2060`. |
| I3 | Bank/gateway rejection | `WBE2095` (raw bank code in args). |
| I4 | Empty result | Clean empty payload; no exception. |

## J. Refactor parity (cross-check)

| # | Scenario | Expected |
|---|---|---|
| J1 | Default ORDER BY | Matches legacy `fnsh_0001_001` (`PRN_SEQ ASC, …`). |
| J2 | FCY type-code set | `{02,04,06,07,08}` (KRW added per FCY-A01). |
| J3 | Last-update source | `ACCT_HIS_LST_INQ_DT` / FD-fallback (FCY-A02) — matches legacy conditional byte-for-byte. |
| J4 | Date control | Confirmed no-op on the list (FCY-G02) — list rows identical with/without a date value. |

> Unit tests confirming FCY-A02: `AccountServiceAuthorityTest.lastUpdateColumn_readsAcctHisLstInqDt_forOnlineAccount`
> and `…_fallsBackToAcctBalLstInqDtm_forFixedDepositOnlyBank` (green). Full SIT/UAT case bank:
> [legacy investigation package test-scenarios](../../../../../docs/features/account-inquiry-fcy/test-scenarios.md).

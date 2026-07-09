# Account Inquiry (VND) — Test Scenarios (Backend, SIT/UAT)

**Screen ID:** `banking-account-inquiry-vnd` · **JEX:** `fnsh_0001_001` · **Hub:** [README.md](README.md)

Backend-facing SIT/UAT scenarios. Frontend scenarios: [wecms-web/docs/features/banking/current-savings-vnd/account-inquiry/test-scenarios.md](../../../../../../wecms-web/docs/features/banking/current-savings-vnd/account-inquiry/test-scenarios.md).
Tenant: `VN_VNM0004_000001`.

---

## 1. Search / list

| # | Scenario | Expected |
|---|---|---|
| S1 | `GET /accounts?currency=VND` | Only `CCY_CD='VND'` + `ACCT_TYPE_CD IN (02,03,05)` + `ACTV_YN='Y'` rows; loan (`01`) excluded |
| S2 | `bankCd=<code>` | Only that bank; `banks_available` still lists every bank (not narrowed) |
| S3 | `keyword=<nickname fragment>` | Case-insensitive partial match on the **formatted** nickname; `%`/`_`/`!` escaped |
| S4 | `keyword` matching a synthesized nickname (empty raw `ACCT_NICK_NM`) | Matches the `<bank-short> <last4>` formatted value shown on screen |
| S5 | totals | `balance`/`available_bal`/`totals_by_ccy` aggregate the full filtered set, not the page slice |
| S6 | non-admin user without grants | Empty list (no `ACCT_AUTH_INFO V/Y` rows); admin sees all |

## 2. Update balance / sync

| # | Scenario | Expected |
|---|---|---|
| U1 | `POST .../transactions-sync` happy path | `status=COMPLETED`; `BAL`/`REAL_AMT` updated when changed; `ACCT_BAL_LST_INQ_DTM` stamped; `acct_bal_upd_dtm` set only if balance changed; `CORC_BF_BAL` = prior `BAL` |
| U2 | Balance success, balance unchanged | `ACCT_BAL_LST_INQ_DTM` stamped; `BAL`/`ACCT_BAL_UPD_DTM` unchanged (COALESCE) |
| U3 | Bank returns `42110000` (no new tx) | Treated as success (history branch) — Last Update shows datetime, not Failed |
| U4 | Failed scrape | `LST_LGN_STTS`/`ACCT_BAL_INQ_STTS`/`ACCT_HIS_INQ_STTS` + messages stamped; `*_LST_INQ_DTM` NOT stamped; `BAL` retained (not zeroed) |
| U5 | No credential for bank | `WBE2050` |
| U6 | OTP/CAPTCHA/QnA required | `status=*_REQUIRED` + `session_id`; resume via `/{sessionId}/{otp\|captcha\|qna}` completes |
| U7 | Expired session resume | `WBE2060` |
| U8 | Verify Last Update display after U1/U4 | U1 → datetime; U4 → error message from `ACCT_BAL_INQ_MSG` (parity with JEX `fnsh_0001_001.ts`) |

## 3. Synchronization history

| # | Scenario | Expected |
|---|---|---|
| H1 | `GET /sync-history?from_dt=&to_dt=` | Returns `EXT_TRAN_CALL_HIS` rows (`SVC_ID=REAL_TIME_ONLINE_INQUIRY`) for tenant + window |
| H2 | `result=success` / `failed` filter | Filters by call result |
| H3 | After a sync (U1/U4) | A new history row appears for the synced account |

## 4. Sorting

| # | Scenario | Expected |
|---|---|---|
| O1 | no sort | `PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC` (NULL `PRN_SEQ` last) |
| O2 | `sortField=BALANCE&sortDirection=DESC` | `bal DESC`, then default tiebreak |
| O3 | `sortField=LAST_UPDATE` | sorts by `acctHisLstInqDt` (FCY-A02 — single-column sort key; displayed cell is account-type-aware via `resolveLastUpdate`) |
| O4 | `sortField=BANK` / `ACCOUNT_NICKNAME` / `AVAILABLE_BALANCE` | maps to `bankCd` / `acctNickNm` / `realAmt` |
| O5 | unknown `sortField` (stale client) | falls back to default sort, no error |
| O6 | `sortField=VAS_ACCOUNT` | falls back to default (not whitelisted) |

## 5. Pagination

| # | Scenario | Expected |
|---|---|---|
| P1 | `page=0&size=10` | ≤10 rows; `total_count`/`total_pages`/`page_number`/`page_size` correct |
| P2 | `size=200` | clamped to 100 |
| P3 | `size=0` | default 10 |
| P4 | totals stable across pages | `balance`/`available_bal` identical for page 0 and page N of the same filter |

## 6. Permissions

| # | Scenario | Expected |
|---|---|---|
| A1 | Authorized admin | full tenant list |
| A2 | Authorized user with `ACCT_AUTH_INFO` grants | only granted accounts |
| A3 | Unauthorized (no menu grant) | `@menuAuthz` denies → 403 envelope (`WBE1003`) |
| A4 | No JWT / expired | 401 via `JwtAuthenticationEntryPoint` |
| A5 | Cross-tenant attempt (body `biz_key`) | ignored — `BIZ_KEY` from JWT only |

## 7. Error handling

| # | Scenario | Expected |
|---|---|---|
| E1 | Export unknown `screenId` | `WBE2010` |
| E2 | Export > 50k rows | `WBE2011` |
| E3 | Export after all columns hidden | `WBE2013` |
| E4 | Bank/gateway rejection | `WBE2095` (raw code echoed) |
| E5 | Empty result | `accounts: []`, `total_count: 0`, totals `"0"` — not an error |
| E6 | Malformed sync body | `WBE_VALIDATION` |

---

Existing automated tests live alongside the banking domain in `wecms-api/src/test/` (service + controller +
export). Cross-feature parity audit: [refactor-gap-analysis.md](refactor-gap-analysis.md).

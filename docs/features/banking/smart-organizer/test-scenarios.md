# Smart Organizer — Test Scenarios (Backend SIT/UAT)

Backend-oriented SIT/UAT scenarios for the refactored Smart Organizer. Parity oracle = JEX
`fnsh_0200_001` / `fnsh_0200_002`
([docs/jex/features/smart-organizer/test-scenarios.md](../../../../../docs/jex/features/smart-organizer/test-scenarios.md)).
Frontend scenarios: [wecms-web/docs/features/banking/smart-organizer/test-scenarios.md](../../../../../wecms-web/docs/features/banking/smart-organizer/test-scenarios.md).

Legend: **R01** matching · **R03** receive/pay + confirm validation · **R05** persistence ·
**R06** FCY · **R07** display names · **R11** account scope.

---

## 1. Search / list (`GET /eligible`)

| # | Scenario | Expected |
|---|---|---|
| S-01 | VND account, valid date range, rules exist | Returns only unconfirmed, rule-matched VND transactions; amounts carry `ccy_cd="VND"` (R06) |
| S-02 | FCY (USD/KRW) account | Same shape; `ccy_cd` = account currency; amounts unformatted on wire (R06) |
| S-03 | Account filter (`bank_cd`+`acct_no`) | Only that account's rows; another account's rows never appear (R11) |
| S-04 | Date filter narrows window | Rows outside `[from_dt,to_dt]` excluded |
| S-05 | Keyword `q` | Only rows whose `RMRK ILIKE '%q%'` |
| S-06 | Already-confirmed transaction | Excluded (in `ACCT_TRNS_RCV_PAY_INFM`) |
| S-07 | Account type not in `('03','04','05','06')` | Excluded |
| S-08 | Display names resolved | `bank_nm`, `acct_nick_nm`, `chart_acct_nm`, `disp_usag_nm`, `disp_vas_acct_nm`, `disp_trco_nm`, `bal` populated (R07) |
| S-09 | Sort by `IN_AMT`/`OUT_AMT`/`RMRK`/`ACCT_NO` ASC/DESC | Ordered accordingly |
| S-10 | Unknown `sort_field` | Falls back to `TRSC_DT DESC` |
| S-11 | Pagination (`page`,`size`) | Correct slice + `total_count`; `size>100` clamped |

## 2. Matching (R01)

| # | Scenario | Expected |
|---|---|---|
| M-01 | Exact rule (`E`), identical remark, matching direction | Matches; classification proposed |
| M-02 | Exact rule, wrong direction | No match |
| M-03 | Contains rule (`C`), substring present, opposite direction | **Matches** (direction-agnostic, preserved legacy behavior) |
| M-04 | No rule matches | Transaction not listed |
| M-05 | Multiple rules match | Lowest `DISP_ORD` wins (`matched_rmrk_seq_no` is that rule); `RMRK_SEQ_NO` breaks ties |
| M-06 | Rule in another tenant matches the remark | **No** cross-tenant match (`BIZ_KEY` enforced) |

## 3. Receive/Pay classification (R03)

| # | Scenario | Expected |
|---|---|---|
| C-01 | Deposit (`INOUT_DV='1'`) matched by RCV rule | Listed as Receive |
| C-02 | Withdrawal (`INOUT_DV='2'`) matched by PAY rule | Listed as Pay |
| C-03 | Create a PAY rule via `POST /rules` | Persists `rcv_pay_dv='PAY'`; subsequent PAY transactions match (regression for the early RCV-only bug) |

## 4. Confirmation (`POST /confirm`)

| # | Scenario | Expected |
|---|---|---|
| F-01 | Confirm selected rows | `confirmed_count` = #rows; `ACCT_TRNS_RCV_PAY_INFM` rows written with `CNFM_YN='Y'` |
| F-02 | Persistence fidelity (R05) | `VAS_ACCT_CD` = real code, `VAS_ACCT_NM` populated, `RCV_PAY_TRCO_NM`/`_ENM` populated |
| F-03 | Confirmed row re-listed | Absent from next `/eligible` |
| F-04 | Re-confirm same transaction | `WBE_SMART_ORGANIZER_ALREADY_CONFIRMED` (409) |
| F-05 | Confirm with non-existent purpose | `WBE_SMART_ORGANIZER_USAGE_NOT_FOUND` (404) |
| F-06 | Confirm with deleted/absent VAS | `WBE_SMART_ORGANIZER_VAS_NOT_FOUND` (404) |
| F-07 | Confirm with absent contact | `WBE_SMART_ORGANIZER_CONTACT_NOT_FOUND` (404) |
| F-08 | Confirm with no classification fields | Allowed (all optional); row written with nulls |
| F-09 | Batch with one failing row | Whole batch rolled back (transactional) |

## 5. Security (R11)

| # | Scenario | Expected |
|---|---|---|
| SEC-01 | Non-admin with `V` grant on the account | Allowed |
| SEC-02 | Non-admin **without** grant | `403 AccessDenied` |
| SEC-03 | Missing `bank_cd`/`acct_no` | Bean-validation 400 — never a tenant-wide list |
| SEC-04 | Confirm a row whose `acct_seq_no` belongs to another account | `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND` (anti-smuggling) |
| SEC-05 | Admin | Bypasses per-account grant |

## 6. VND / FCY (R06)

| # | Scenario | Expected |
|---|---|---|
| CCY-01 | VND account | `ccy_cd="VND"`; client renders `₫` |
| CCY-02 | USD account | `ccy_cd="USD"`; client renders `$` |
| CCY-03 | KRW account | `ccy_cd="KRW"`; client renders `₩` |
| CCY-04 | Same controller/service/query for all | No `if(FCY)` branch; identical response shape |

## 7. Organizer Settings rules

| # | Scenario | Expected |
|---|---|---|
| R-01 | Create rule (no `disp_ord`) | `DISP_ORD = MAX+1` |
| R-02 | Update rule | Fields overwritten; `REG_*` preserved; no `MOD_*` columns |
| R-03 | Delete rule | Hard-deleted; missing → `WBE_SMART_ORGANIZER_RULE_NOT_FOUND` |
| R-04 | Reorder rules | `DISP_ORD` rewritten in order |
| R-05 | Reorder payload with duplicate id | `WBE_SMART_ORGANIZER_RULE_DUPLICATE_REORDER` (400) |
| R-06 | List rules | Ordered by `DISP_ORD, RMRK_SEQ_NO` |

## 8. Error handling / edge

| # | Scenario | Expected |
|---|---|---|
| E-01 | No tenant in context | `WBE1009` |
| E-02 | Empty result set | `rows=[]`, `total_count=0` (not an error) |
| E-03 | Sequence allocation returns blank | `WBE9999` |
| E-04 | Invalid `match_type`/`rcv_pay_dv` on create | Bean-validation 400 (`@Pattern`) |

---

## Automated coverage

- Endpoint authorization is asserted by `EndpointAuthorizationCoverageTest` (config test).
- The matching golden-parity test was run manually against the live PostgreSQL engine; porting it into
  a `@SpringBootTest` is a tracked residual (see
  [docs/jex/audits/smart-organizer-remediation-plan.md](../../../../../docs/jex/audits/smart-organizer-remediation-plan.md) §8, "Remaining risks").

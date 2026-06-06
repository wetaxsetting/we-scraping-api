# Firm Banking History — Test Scenarios (Backend)

SIT/UAT scenarios plus the existing automated coverage map. Parent: [README.md](README.md).
Frontend test scenarios: [wecms-web .../test-scenarios.md](../../../../../wecms-web/docs/features/settings/firm-banking-history/test-scenarios.md).
Legacy test cases (read-only): [docs/jex/.../test-scenarios.md](../../../../../docs/jex/features/firm-banking-history/test-scenarios.md).

---

## 1. Search

| # | Scenario | Steps | Expected |
|---|---|---|---|
| S-1 | Valid search | `from_date`+`to_date` within seeded range | 200; rows returned, default sort `TARN_DTM DESC, SEQ_NO DESC`; paging metadata present |
| S-2 | No-result search | date range with no rows | 200; `list: []`, `total_count: 0` |
| S-3 | Multi-filter search | date range + `svc_type=F` + `keyword=<partial seq>` | 200; only F-leg rows whose `seq_no` matches (case-insensitive) |
| S-4 | Missing `from_date` / `to_date` | omit either | `WBE_VALIDATION` |
| S-5 | Malformed date | `from_date=2026-05-01` / `20260230` | `WBE_VALIDATION` (length/parse) |
| S-6 | Inverted range | `from_date > to_date` | `WBE_VALIDATION` |
| S-7 | Same-day range | `from_date == to_date` | 200 (allowed) |
| S-8 | Unknown `svc_type` | `svc_type=X` | `WBE_VALIDATION`; repository never hit |
| S-9 | Lowercase `svc_type` | `svc_type=g` | 200; upper-cased to `G` before query |
| S-10 | Keyword meta-chars | `keyword=100%_x!` | 200; LIKE meta-chars escaped (`!` prefix); no SQL injection / pattern leak |
| S-11 | Sort by each whitelisted field | `sort_field` ∈ {TARN_DTM, SVC_TYPE, SVC_NM, SEQ_NO, DEP_BANK_CD, RSLT_CD} × ASC/DESC | 200; ordered accordingly |
| S-12 | Unknown sort field | `sort_field=BOGUS` | 200; silent fallback to default sort (no 4xx) |
| S-13 | Page clamps | `page=-5` / `size=0` / `size=999` | `page→0`, `size→15`, `size→100` |

## 2. Transaction history by leg

| # | Scenario | Expected |
|---|---|---|
| T-1 | F-leg (firm banking) row | `svc_type=F`, `svc_type_key=…firm_banking_transaction`; `svc_nm` ∈ {201,202,203}; `svc_name_key=fb_history.service.<code>`; `van_org_cd` populated; `req_data=null` |
| T-2 | F-leg code 204 | `svc_name_key=null` → UI renders raw `204` |
| T-3 | S-leg (NAPAS / scraping) row | `svc_type=S`; `svc_name_key` ∈ funds_transfer_scraping / scraping_balance / scraping_transaction / scraping_card_transaction; `dep_bank_cd` = "`<bank> - <code>`" |
| T-4 | G-leg (GDT e-Invoice) row | `svc_type=G`; `svc_name_key` ∈ sale/expense einvoice; `req_data` carries the e-Invoice JSON |
| T-5 | B-leg (Vietnam business) row | `svc_type=B`; `svc_name_key=…vietnam_business_inquiry`; `req_data.search` carries the Tax ID |
| T-6 | Success result | `rslt_cd ∈ {00000000, 0000}` → UI result `success` (green) |
| T-7 | Failed result | other `rslt_cd` (incl. blank/null) → UI result `failed` (red) |

## 3. Detail view

| # | Scenario | Expected |
|---|---|---|
| D-1 | Open detail | click View Details → client-side popover, **no extra HTTP call** |
| D-2 | F-leg message | response message resolved via `DICTIONARY_VAN`; VAN code shown |
| D-3 | S/G/B-leg message | response message resolved via the 18-code scraping registry |
| D-4 | Field mapping per leg | F shows VAN code + inst code; S shows inst code; G shows e-Invoice no.; B shows Tax ID; all show response code + message |
| D-5 | Never blank | unknown code → falls back through `rslt_msg` → raw code → localized "Failed" → "—" |

## 4. Export (refactor-only)

| # | Scenario | Expected |
|---|---|---|
| E-1 | Export with data | `POST /export` → XLSX stream; subheader `"N record(s)"`; columns = allow-list ∩ View Setting |
| E-2 | Export with no data | XLSX with header only; subheader `"0 record(s)"` |
| E-3 | Column verification | only `REG_DTM, SVC_TYPE, SVC_NM, SEQ_NO, DEP_BANK_CD, RS_CD, RS_MSG`; `job`/`req_data`/`van_org_cd`/`*_key` excluded |
| E-4 | Filter/sort honoured | export reflects the same `from_date`/`to_date`/`svc_type`/`keyword`/sort as the list |
| E-5 | Row cap | > 50,000 rows → `WBE2011` |

## 5. Permissions

| # | Scenario | Expected |
|---|---|---|
| P-1 | Authorized (ROLE_USER) | 200 |
| P-2 | Authorized (ROLE_ADMIN) | 200 |
| P-3 | Unauthenticated (no/invalid JWT) | 401 via `JwtAuthenticationEntryPoint` |
| P-4 | Tenant isolation | rows for tenant A never include tenant B (every leg filters `BIZ_KEY`) |
| P-5 | Product-type H | frontend hides `G`/`B` filter options; backend returns whatever the tenant's rows contain |

## 6. Error handling

| # | Scenario | Expected |
|---|---|---|
| ER-1 | Invalid search conditions | `WBE_VALIDATION` (HTTP 200 business envelope) with `trace_id` |
| ER-2 | Backend/DB fault | `WBE_DB` (500) / `WBE9999` (500) with `trace_id`; frontend shows global error dialog |
| ER-3 | Empty result | clean `list: []` (not an error) |

---

## 7. Existing automated coverage (JUnit)

| Test class | Scenarios covered |
|---|---|
| `FirmBankingHistoryServiceTest` | S-4…S-13, P-4 (tenant scope), keyword escaping, paging clamps, sort whitelist resolution |
| `FirmBankingServiceNameResolverTest` | T-1…T-5 service-name resolution; `TranTp` code set (incl. 204→null) |
| `FirmBankingHistoryControllerTest` | snake_case wire shape end-to-end, param binding, S-4 validation enveloping (standalone MockMvc) |
| `FirmBankingHistoryExportProviderTest` | E-3, E-4 (registry contract, filter/sort forwarding, FIELD_ID row mapping) |

> Gaps in automated coverage worth a SIT pass: D-1…D-5 (frontend popover behaviour), E-1/E-2/E-5
> (live XLSX bytes + row cap), P-1…P-3 (live `@PreAuthorize` against a real JWT), and timezone
> rendering of `tarn_dtm` against a known DEV row.

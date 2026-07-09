# Transfer History — Test Scenarios (Backend)

> Automated tests + SIT/UAT scenarios for `GET /api/v1/banking/internal-transfer/transfer-history`.

## 1. Existing automated tests

### `TransferHistoryControllerTest` (MockMvc, 7 tests)

Standalone MockMvc with a manually `SNAKE_CASE`-configured Jackson + `MethodValidationPostProcessor` so `@Validated`/`@Pattern` fire; `TenantContext` set to `VN_VNM0004_000001`.

| Test | Asserts |
|---|---|
| `list_happyPathSerialisesSnakeCase` | 200; `status.error=false`, `code=WBI0000`; `total_count=1`, `total_vnd_amount=50000.0`, `total_vnd_count=1`, `total_usd_count=0`; row fields serialize snake_case (`seq_no`, `trns_dt`, `trns_tm`, `out_bank_code`, `out_bank_nm`, `in_bank_nm`, `ccy_cd`, `trns_stts=00000000`, `user_nm`) |
| `list_missingFromDtReturnsValidationEnvelope` | omit `from_dt` → `WBE_VALIDATION`; service never called |
| `list_malformedFromDtReturnsValidationEnvelope` | `from_dt=2026-05-01` (dashes) → `WBE_VALIDATION`; service never called |
| `list_invalidStatusSentinelReturnsValidationEnvelope` | `trns_stts=ABCDEFGH` → `WBE_VALIDATION`; service never called |
| `list_pagePerCntOutOfWhitelistReturnsValidationEnvelope` | `page_per_cnt=7` → `WBE_VALIDATION`; service never called |
| `list_pageNoZeroReturnsValidationEnvelope` | `page_no=0` → `WBE_VALIDATION`; service never called |
| `list_invertedPeriodSurfacesBusinessError` | service throws `WBE_TRANSFER_HISTORY_INVALID_PERIOD` → HTTP 200, `status.error=true`, that code surfaced |

### `TransferHistoryQueryServiceTest` (Mockito, 15 tests)

| Test | Asserts |
|---|---|
| `list_returnsProjectedRowsAndTotals` | totals + row projection; `20260515103045` UTC → `trns_dt=20260515`, `trns_tm=1730` (UTC+7) |
| `list_normalizesRegDtmUtcToDisplayZoneCrossingMidnight` | `20260515200000` UTC → `trns_dt=20260516`, `trns_tm=0300` |
| `list_inBankNameResolvesViaNapasCodeAndWireCarriesNapasCode` | `in_bank_code=970424` (NAPAS); name resolved via NAPAS code; internal `11704088` never hits the resolver |
| `list_inBankNameIsBlankWhenNapasCodeAbsent` | null NAPAS code → `in_bank_code=""`, `in_bank_nm=""`, resolver not called |
| `list_rejectsInvertedPeriodWithInvalidPeriodCode` | inverted period → `WBE_TRANSFER_HISTORY_INVALID_PERIOD`; repo never touched |
| `list_successFilterPassesStatusEqOnly` | `trns_stts=00000000` → `aggregateTotals(...,"00000000", null)` |
| `list_failedSentinelPassesStatusNeqOnly` | `trns_stts=99999999` → `aggregateTotals(..., null, "00000000")` |
| `list_blankStatusFiltersNeitherStatus` | `trns_stts=""` → both null |
| `list_defaultSortIsRegDtmDescTieBrokenBySeqNoDesc` | default sort = regDtm DESC, seqNo DESC |
| `list_whitelistedSortFieldDescAlwaysTieBreaksBySeqNoDesc` | `TRSC_AMT/ASC` → trscAmt ASC + seqNo DESC |
| `list_unknownSortFieldFallsBackToDefault` | `sort_field="DROP TABLE"` → default sort (injection-safe) |
| `list_defaultPageSizeIs15WhenPagePerCntIsNull` | size 15, index 0 |
| `list_pageNoIsConvertedFromOneBasedToZeroBased` | `page_no=3, page_per_cnt=20` → size 20, index 2 |
| `list_resolvesEachBankCodeAtMostOncePerPage` | resolvers called once each despite shared codes (cache) |
| `list_korenAndKhmerLocalesCollapseToEnglishForLegacyFn` | KM locale → legacy fn called with `"en"` |

## 2. SIT / UAT scenarios

### Search

| # | Scenario | Steps | Expected |
|---|---|---|---|
| S1 | Valid search | from_dt/to_dt within a populated range, status All | Paged rows + correct `total_count` and VND/USD totals |
| S2 | No-result search | date range with no transfers | `total_count=0`, empty `rows`, zero totals; UI shows empty state |
| S3 | Multi-filter | date range + `trns_stts=00000000` + `page_per_cnt=50` + `sort_field=TRSC_AMT&sort_direction=DESC` | Only success rows; sorted by amount desc, tie-broken by seq desc; 50/page |

### Transaction history (status display)

| # | Scenario | Expected |
|---|---|---|
| T1 | Success transaction | `trns_stts=00000000`; UI shows timestamp only (no green label); `trns_msg` null |
| T2 | Failed transaction | `trns_stts` ≠ `00000000` (e.g. `8000FFFF`); UI shows red "Failed" + timestamp; `trns_msg` available as tooltip |
| T3 | "Pending/Processing/Cancelled" | **N/A** — no such status exists in the data model; all non-success collapse to Failed |

### Detail view

| # | Scenario | Expected |
|---|---|---|
| D1 | Open detail | **N/A** — no detail endpoint/route exists. All fields are shown inline in the grid row. |

### Matching / Unmatch / Candidate search

| # | Scenario | Expected |
|---|---|---|
| M1–M5 | Match vendor/expense, unmatch, unmatch-all, candidate search | **N/A for this feature** — matching is a separate feature on `AccountController` (`/api/v1/banking/accounts/{}/transactions/{}/match*`). Test those under the Transaction-History matching feature, not here. |

### Export

| # | Scenario | Expected |
|---|---|---|
| E1 | Export with/without data, column verification | **N/A** — no export endpoint exists for Transfer History. |

### Permissions

| # | Scenario | Expected |
|---|---|---|
| P1 | Authorized user (valid JWT) | 200 + data |
| P2 | Unauthenticated (no/expired JWT) | 401 via `JwtAuthenticationEntryPoint`, `WBE1009` envelope |
| P3 | Normal vs admin user | Same result — no ADMIN-only role gate; both pass the `@menuAuthz.canAccess('banking-internal-transfer-history')` check (OQ-5) |
| P4 | Cross-tenant isolation | A user in tenant A never sees tenant B rows (query constrained on `BIZ_KEY` from JWT) |

### Error handling

| # | Scenario | Expected |
|---|---|---|
| ER1 | Inverted period (`to_dt < from_dt`) | `WBE_TRANSFER_HISTORY_INVALID_PERIOD` (HTTP 200, business) |
| ER2 | Missing/malformed `from_dt`/`to_dt` | `WBE_VALIDATION` |
| ER3 | `trns_stts` not in `{"",00000000,99999999}` | `WBE_VALIDATION` |
| ER4 | `page_per_cnt` not in whitelist / `page_no < 1` | `WBE_VALIDATION` |
| ER5 | Unknown `sort_field` | No error — falls back to default sort |
| ER6 | Day-boundary UTC/display asymmetry | Documented behaviour; verify a near-midnight transfer (see business-flow §4) |

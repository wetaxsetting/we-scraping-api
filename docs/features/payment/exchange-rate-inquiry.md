# Exchange Rate Inquiry

**Feature slug:** `exchange-rate-inquiry`
**Route:** `/payment/overseas-payment/exchange-rate-inquiry`
**Module:** Payment > Overseas Payment
**System:** Refactor (Spring Boot + Next.js)
**JEX counterpart:** `pymn_0010_002` area (Overseas Payment module)

> **Relocated 2026-06-01** from `features/banking/` → `features/payment/` (menu authority: Payment → Add-on).
> **Cross-links:** [overseas-payment.md](overseas-payment/README.md) (the FX consumer) · [../banking/account-inquiry-fcy.md](../banking/current-savings-fcy/account-inquiry.md) (FCY balances) · web UI [`../../../../wecms-web/docs/features/payment/exchange-rate-inquiry.md`](../../../../wecms-web/docs/features/payment/exchange-rate-inquiry.md).

---

## 1. Overview

### Business Description

Exchange Rate Inquiry lets authenticated WeCMS users view the current foreign-currency
exchange rates published by their connected bank (currently Shinhan Bank via the Coocon
aggregator). The page renders a currency-matrix table — one row per counter-currency
(USD, JPY, EUR, etc.), four rate columns (Cash Buy, Cash Sell, T/T Buy, T/T Sell) — and
displays the data timestamp at the bottom right. The user can select which bank's rates
to display, choose a reference date, and optionally enable a side-by-side comparison with
a second bank's rates.

Rates are not fetched in real time from the bank API during page load. Instead, a
scheduled batch (`EXCH_0001_001`) pulls rates from Coocon (Shinhan's API gateway) each
day and stores them in `BANK_EXCH_RATE_INFM`. The frontend reads this stored snapshot via
the `GET /api/v1/banking/exchange-rates` endpoint. This design means the displayed rates
are as current as the last successful batch run, not real-time.

### JEX Source References

| Layer | File |
|---|---|
| Entity | `WECMS DEV/WECMS/src/wabooks/entity/BankExchRateInfm.java` |
| Entity PK | `WECMS DEV/WECMS/src/wabooks/entity/BankExchRateInfmPk.java` |
| Secondary entity | `WECMS DEV/WECMS/src/wabooks/entity/ExchRateInfm.java` |
| DAO | `WECMS DEV/WECMS/src/wabooks/dao/BankExchRateInfmDAO.java` |
| IDO read query | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.BANK_EXCH_RATE_INFM_R002.xml` |
| Batch definition (BAT) | `WECMS DEV/WECMS_ETC/xml/service/BAT/BAT.EXCH_0001_001.xml` |
| Batch definition (BCS) | `WECMS DEV/WECMS_ETC/xml/service/BCS/BCS.EXCH_0001_001.xml` |
| Batch implementation | `WECMS DEV/WECMS/src/bcs/jex/wabooks/exch/EXCH_0001_001.java` |

There is **no JEX view service** for the read path. The JEX legacy system reads from
`BANK_EXCH_RATE_INFM` directly using IDO `BANK_EXCH_RATE_INFM_R002`; the refactor maps
this query to the Spring repository.

### Refactor Status

| Layer | Status | Notes |
|---|---|---|
| Database DDL | ✅ Done | V127 migration creates `BANK_EXCH_RATE_INFM` |
| Database seed | ✅ Done | V130 seeds 15 currency rows (historical date 20250425) |
| Spring Boot controller | ✅ Done | `ExchangeRateController` wired, basic Javadoc |
| Spring Boot service | ✅ Done | `ExchangeRateService.list()` with fallback logic |
| Spring Boot repository | ✅ Done | `BankExchRateInfmRepository` with JPQL queries |
| Spring Boot entity | ✅ Done | `BankExchRateInfm` JPA entity |
| Spring Boot DTOs | ✅ Done | `ExchangeRateRow` record + `ExchangeRateListResponse` record |
| Next.js wire types | ✅ Done | `ApiExchangeRateRow` + `ApiExchangeRateListResponse` in `lib/types/api.ts` |
| Next.js API fetcher | ✅ Done | `fetchExchangeRates()` in `lib/api/banking.ts` |
| Next.js page component | ✅ Done | `ExchangeRateInquiryPage.tsx` renders the matrix table |
| Next.js route | ✅ Done | `app/(dashboard)/payment/overseas-payment/exchange-rate-inquiry/page.tsx` |
| Next.js i18n | ✅ Done | Keys present in all three locales (EN/KO/VI) |
| Input validation (backend) | ❌ Gap | No `@Pattern` on `ref_date`; no format check |
| Timezone correctness (backend) | ❌ Gap | `resolveDate()` uses JVM default, not UTC+7 |
| Seq-no preference (backend) | ❌ Gap | Repository does not honor `EXCH_SEQ_NO='2'` preference |
| OpenAPI annotations | ❌ Gap | No `@Tag` / `@Operation` on controller |
| Wire/in-memory translation | ❌ Gap | Mapper lives inside the component, not `lib/api/mappers.ts` |
| Error handling (frontend) | ❌ Gap | API failure silently falls back to mock; no error dialog |
| Date chip (frontend) | ❌ Gap | Hardcoded "05-Jan-2025"; not wired to `DateField` |
| Compare-bank feature | ❌ Gap | UI-only; no second API call is made |
| formatRate() location | ❌ Gap | Page-local function; belongs in `lib/currency.ts` |
| Bank list source | ❌ Gap | Hardcoded `BANK_OPTIONS` constant; not driven by backend |
| Bank dropdown UX | ❌ Gap | Cycle toggle, not a real dropdown |
| Mock vs. seed currency unit | ⚠️ Mismatch | Mock rows use VND-denominated rates; seed rows use KRW-denominated rates |

---

## 2. Data Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│ Batch: EXCH_0001_001 (daily, Coocon/Shinhan API)                     │
│  - BAT.EXCH_0001_001.xml — batch descriptor (pkg=exch, trTp=U)       │
│  - BCS.EXCH_0001_001.xml — service descriptor                        │
│  - EXCH_0001_001.java    — pulls rates from Coocon for BIZ_KEY +     │
│                             REF_DATE, writes to BANK_EXCH_RATE_INFM  │
│  Input:  BIZ_KEY (VARCHAR 20), REF_DATE (VARCHAR 9)                  │
│  Run 1 → EXCH_SEQ_NO = '1'                                           │
│  Run 2 same day → EXCH_SEQ_NO = '2' (preferred by JEX IDO R002)     │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ INSERT / UPDATE
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│ Table: BANK_EXCH_RATE_INFM (PostgreSQL, wecms_dev)                   │
│  PK: BIZ_KEY + BANK_CD + REF_DATE + CCY_CD + EXCH_SEQ_NO            │
│  Rate columns: TRAN_SELL_AMT, TRAN_BUY_AMT, BUY_AMT, SELL_AMT,     │
│                BASIC_RATE, BASIC_TRAN                                │
│  Metadata: LAST_UPDATE_DATE, LAST_UPDATE_TIME, EXCH_RATE_REG_DTM,   │
│            STAND_POINT, SEQ_NO, REF_CCY_CD                           │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ JPA query (BankExchRateInfmRepository)
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│ ExchangeRateService.list(refDate, bankCd)                            │
│                                                                      │
│  1. resolveDate(refDate):                                            │
│     └─ if refDate blank → LocalDate.now().format("yyyyMMdd")        │
│        [BUG: uses JVM default TZ, not Asia/Ho_Chi_Minh]             │
│                                                                      │
│  2. findByBizKeyAndBankCdAndRefDateOrderByCcyCdAsc(...)             │
│     └─ returns ALL seq_no rows for the date (1 AND 2 if both exist) │
│        [BUG: should prefer seq_no=2; see §3 JEX IDO R002 behavior]  │
│                                                                      │
│  3. Fallback path (only when refDate == null and rows empty):        │
│     └─ findLatestRefDate(bizKey, bankCd)                            │
│     └─ retry with latestDate                                        │
│                                                                      │
│  4. bankCdResolver.resolveBankNm(resolvedBank) → bank display name  │
│                                                                      │
│  5. Stream rows → toRow() → ExchangeRateRow records                 │
│     └─ Returns ExchangeRateListResponse(refDate, bankCd, bankNm,    │
│                                         List<ExchangeRateRow>)       │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ JSON via ApiResponse<ExchangeRateListResponse>
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│ GET /api/v1/banking/exchange-rates                                   │
│  Query params:                                                       │
│    ref_date  (optional, string — no server-side format validation)   │
│    bank_cd   (optional, string — defaults to "11704088" Shinhan)     │
│  Response envelope: ApiResponse<ExchangeRateListResponse>            │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ HTTP GET (Axios + JWT interceptor)
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│ fetchExchangeRates({ bankCd?, refDate? }) — lib/api/banking.ts       │
│  - Builds params: { ref_date, bank_cd } (only if provided)          │
│  - Calls unwrap() → returns ApiExchangeRateListResponse on success   │
│  - Throws ApiCallError on status.error === true                      │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ apiData (ApiExchangeRateListResponse | undefined)
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│ useApiData() hook — lib/api/useApiData.ts                            │
│  - Fires on mount and when selectedBank.bankCd changes               │
│  - Does NOT expose error state to the component                      │
│  - On error: apiData remains undefined → mock fallback               │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│ ExchangeRateInquiryPage.tsx                                          │
│  - If apiData.rates.length > 0: maps via apiRowToMatrix() [in-file]  │
│  - Otherwise: uses exchangeRateMatrixRows from mockData.ts            │
│  - Renders currency-matrix table + "Inquired on" timestamp footer    │
└──────────────────────────────────────────────────────────────────────┘
```

**Fallback-to-latest-date path** (backend only): When `refDate` is omitted and today's
date has no rows in the database (batch hasn't run yet), `ExchangeRateService` calls
`findLatestRefDate()` to find the most recent date with data and retries. This path
activates automatically in dev/UAT where the V130 seed uses a fixed historical date
(`20250425`). The path does NOT activate when an explicit `ref_date` is supplied — in
that case an empty rate list is returned without fallback.

---

## 3. Database

### Table: BANK_EXCH_RATE_INFM

Created by Flyway migration V127. PostgreSQL DDL:

```
BANK_EXCH_RATE_INFM
├── Primary Key (composite)
│   ├── BIZ_KEY          VARCHAR(50)  NOT NULL   — tenant identifier
│   ├── BANK_CD          VARCHAR(20)  NOT NULL   — source bank code (e.g. "11704088")
│   ├── REF_DATE         VARCHAR(8)   NOT NULL   — reference date, YYYYMMDD format
│   ├── CCY_CD           VARCHAR(10)  NOT NULL   — counter-currency (USD, EUR, JPY, …)
│   └── EXCH_SEQ_NO      VARCHAR(10)  NOT NULL   — daily run sequence ("1", "2", …)
│
├── Rate Data
│   ├── REF_CCY_CD       VARCHAR(10)  NOT NULL   — base currency quoted against (KRW)
│   ├── BUY_AMT          NUMERIC(20,6)           — Cash Buy: rate to buy foreign currency for cash
│   ├── SELL_AMT         NUMERIC(20,6)           — Cash Sell: rate to sell foreign currency for cash
│   ├── TRAN_BUY_AMT     NUMERIC(20,6)           — T/T Buy: telegraphic transfer receive rate
│   ├── TRAN_SELL_AMT    NUMERIC(20,6)           — T/T Sell: telegraphic transfer send rate
│   ├── BASIC_RATE       NUMERIC(20,6)           — Basic (middle) rate
│   ├── BASIC_TRAN       NUMERIC(20,6)           — Basic transfer rate
│   ├── SRCH_DT          VARCHAR(8)              — date rates were searched/retrieved, YYYYMMDD
│   ├── CLASS_EXCH       VARCHAR(50)             — Coocon category string (e.g. "COMMON")
│   ├── MODULE           VARCHAR(100)            — Coocon module string
│   ├── EXCH_RATE_REG_DTM VARCHAR(14)            — datetime rate registered in source, YYYYMMDDHHMMSS
│   ├── SEQ_NO           VARCHAR(10)             — JEX-level seq_no (mirrors EXCH_SEQ_NO for IDO)
│   ├── STAND_POINT      VARCHAR(100)            — stand point from Coocon resultData
│   ├── LAST_UPDATE_DATE VARCHAR(8)              — last update date from Coocon, YYYYMMDD
│   └── LAST_UPDATE_TIME VARCHAR(6)              — last update time from Coocon, HHMMSS
│
├── ERP
│   └── ERP_SND_YN       VARCHAR(1)  NOT NULL DEFAULT 'N' — ERP sync flag
│
└── Audit
    ├── REG_DTM          VARCHAR(14)             — registration datetime, YYYYMMDDHHMMSS
    ├── REG_USER         VARCHAR(100)            — registering user
    ├── MOD_DTM          VARCHAR(14)             — last modification datetime
    └── MOD_ID           VARCHAR(100)            — last modifying user
```

**Index:** `IDX_BANK_EXCH_RATE_INFM_BIZ_DATE` on `(BIZ_KEY, REF_DATE, BANK_CD)` —
supports the primary query pattern of looking up all rates for a tenant on a given date.

**Note on `SEQ_NO` vs. `EXCH_SEQ_NO`:** The JEX entity uses `EXCH_SEQ_NO` as the PK
column and `SEQ_NO` as a separate data column sourced from the Coocon response. The V127
DDL preserves both. The JEX IDO R002 queries by the data-column `SEQ_NO`, not by
`EXCH_SEQ_NO` (the PK part). The Spring entity maps both correctly.

### JEX IDO R002 — EXCH_SEQ_NO / SEQ_NO Preference Behavior

The JEX IDO `BANK_EXCH_RATE_INFM_R002` implements a two-tier fallback for the daily
sequence number. Condensed from the XML SQL:

```sql
-- 1. Find the most recent REF_DATE with rows for the tenant+bank
--    (weekday only, ≤ current date)
-- 2. Prefer SEQ_NO = '2' (afternoon/second run) for that date
-- 3. Fall back to SEQ_NO = '1' if no run-2 row exists
SEQ_NO = COALESCE(
    (SELECT '2' FROM BANK_EXCH_RATE_INFM
     WHERE BIZ_KEY = :BIZ_KEY AND BANK_CD = :BANK_CD
       AND REF_DATE = <max_date> AND SEQ_NO = '2' LIMIT 1),
    '1'
)
```

**What this means in practice:** On most days the batch runs once (morning), producing
`SEQ_NO='1'` rows. If the batch is re-run later in the day (e.g. to pick up an updated
afternoon rate), it inserts `SEQ_NO='2'` rows for the same date. The JEX query always
returns the run-2 rows when they exist, discarding run-1 duplicates. The IDO also
restricts `REF_CCY_CD IN ('USD', 'KRW')` and excludes weekends.

**Current Spring repository behavior:** `findByBizKeyAndBankCdAndRefDateOrderByCcyCdAsc`
returns ALL rows for a date regardless of `EXCH_SEQ_NO` / `SEQ_NO`. If two batch runs
occurred on the same day, the response contains duplicate currency rows (one per run),
which the frontend renders as duplicate table rows — a data integrity regression relative
to JEX. See Gap B-3 for the fix.

The V130 seed inserts only `EXCH_SEQ_NO='1'` rows (single historical date, single run),
so this duplication cannot be observed with the current seed data alone.

### Flyway Migrations

| Version | File | Purpose |
|---|---|---|
| V127 | `V127__bank_exch_rate_infm_table.sql` | Creates `BANK_EXCH_RATE_INFM` with composite PK, rate columns, ERP flag, audit columns, and the `IDX_BANK_EXCH_RATE_INFM_BIZ_DATE` index |
| V130 | `V130__bank_exch_rate_infm_seed.sql` | Inserts 15 seed rows (USD, JPY, EUR, CNY, GBP, HKD, SGD, CHF, AUD, CAD, NZD, THB, MYR, VND, TWD) for BIZ_KEY=`VN_VNM0004_000001`, BANK_CD=`11704088`, REF_DATE=`20250425`, EXCH_SEQ_NO=`1`. Rate values are KRW-denominated (e.g. USD = 1360.00 KRW basic rate). |

> Note: these files' internal `-- Vxx` header comments still show the draft numbers V84/V87 (renumbered on commit).

**Seed vs. mock currency unit mismatch:** The V130 seed stores KRW-denominated rates
(USD `BASIC_RATE = 1360.00`, i.e. 1 USD ≈ 1360 KRW). The `exchangeRateMatrixRows` mock
in `mockData.ts` contains VND-denominated rates (USD `cashBuy = "25,890.00"`, i.e. 1 USD
≈ 25,890 VND). When the API returns seed data, `apiRowToMatrix()` renders the KRW rates;
when the API is unreachable, the mock renders VND rates. The two paths produce visually
incompatible tables (order-of-magnitude difference). This mismatch was introduced when
mock data was authored independently from the seed.

---

## 4. API Contract

### Endpoint

```
GET /api/v1/banking/exchange-rates
```

**Authentication:** Bearer JWT required (enforced by Spring Security; standard
`Authorization: Bearer <token>` header via `lib/axios.ts` interceptor).

**Controller:** `com.kosign.wecms.domain.banking.controller.ExchangeRateController`

### Query Parameters

| Parameter | Type | Required | Default | Constraint |
|---|---|---|---|---|
| `ref_date` | String | No | today (server resolves) | Should be YYYYMMDD — not currently validated |
| `bank_cd` | String | No | `"11704088"` (Shinhan Bank) | Arbitrary string; not validated against known bank codes |

### Response Envelope

All responses use the project-standard `ApiResponse<T>` envelope:

```jsonc
// Success (HTTP 200, status.error = false)
{
    "status": {
        "error": false,
        "code": "WBI0000",
        "message": "Success"
    },
    "data": {
        "ref_date": "20250702",           // YYYYMMDD — resolved or fallback date
        "bank_cd": "11704088",            // bank code used for the query
        "bank_nm": "Shinhan Bank",        // display name from BANK_INFM
        "rates": [
            {
                "ccy_cd": "AUD",          // counter-currency code
                "ref_ccy_cd": "KRW",      // base currency rates are quoted against
                "basic_rate": 871.63,     // basic (middle) rate — nullable
                "basic_tran": 871.63,     // basic transfer rate — nullable
                "buy_amt": 858.00,        // Cash Buy rate — nullable
                "sell_amt": 885.00,       // Cash Sell rate — nullable
                "tran_buy_amt": 860.00,   // T/T Buy rate — nullable
                "tran_sell_amt": 882.50,  // T/T Sell rate — nullable
                "stand_point": "1",       // stand point from Coocon — nullable
                "last_update_date": "20250425",  // YYYYMMDD — nullable
                "last_update_time": "105100",    // HHMMSS — nullable
                "exch_rate_reg_dtm": "20250425105100"  // YYYYMMDDHHMMSS — nullable
            }
            // … one entry per counter-currency, ordered by ccy_cd ASC
        ]
    }
}
```

```jsonc
// Empty result (batch hasn't run; no fallback date found)
{
    "status": { "error": false, "code": "WBI0000", "message": "Success" },
    "data": {
        "ref_date": "20250513",
        "bank_cd": "11704088",
        "bank_nm": "Shinhan Bank",
        "rates": []
    }
}
```

### Field Null-Safety Rules

- `ref_date`, `bank_cd`, `bank_nm` are never null in the response (service guarantees
  a non-null resolved date; `BankCdResolver` returns a fallback name on unknown codes).
- All amount fields (`basic_rate`, `basic_tran`, `buy_amt`, `sell_amt`, `tran_buy_amt`,
  `tran_sell_amt`) may be `null` when the source API did not provide a value.
- `stand_point`, `last_update_date`, `last_update_time`, `exch_rate_reg_dtm` may be
  `null` when the source API did not populate these fields.
- The `rates` list is never null but may be empty.

### Wire Type Names (Global Jackson `SNAKE_CASE` Strategy)

The Spring Boot controller is annotated `@RestController`; the global `JacksonConfig`
applies `PropertyNamingStrategies.SNAKE_CASE` project-wide. Java record field `ccyCd`
serialises as `ccy_cd`, `basicRate` as `basic_rate`, etc. — no per-field `@JsonProperty`
annotations are needed or present.

---

## 5. Frontend Architecture

### Route

```
app/(dashboard)/payment/overseas-payment/exchange-rate-inquiry/page.tsx
```

Thin page file that sets `metadata` and renders `<ExchangeRateInquiryPage />`.

### Component Tree

```
ExchangeRateInquiryPage                  (components/payment/overseas-payment/)
├── <PageLayout>
│   ├── <PageHeader title="Exchange Rate Inquiry">
│   │   └── <PageActionButtons showList />
│   │
│   ├── <FilterRow right={<SearchInput />}>
│   │   ├── Bank selector (label + <FilterDropdown>)   ← cycle toggle, not real dropdown
│   │   ├── Sequence No. (label + <FilterDropdown>)    ← static "Last time", no handler
│   │   ├── Date chip (hardcoded "05-Jan-2025") + <CalendarIconButton>
│   │   └── Compare-with checkbox + second bank <FilterDropdown>  ← UI-only
│   │
│   └── Currency-matrix table block
│       ├── <table> (bare, not shadcn <Table>)
│       │   ├── <colgroup>: --w-wb-er-currency + 4× --w-wb-er-rate tokens
│       │   ├── <TableHeader> sticky
│       │   │   └── <TableRow>
│       │   │       ├── <TableHeaderCell> "Currency"
│       │   │       └── 4× <TableHead> (two-line: bank name / rate type)
│       │   └── <TableBody>
│       │       └── filteredRows.map(row => <TableRow>)
│       │           ├── Currency cell: emoji flag + currency code
│       │           └── 4× rate cells: right-aligned formatted number
│       │
│       └── <p> "Inquired on {date}" timestamp footer
```

### Wire Types vs. In-Memory Types

The project mandates three layers:

| Layer | Convention | Status |
|---|---|---|
| Wire JSON (`ApiExchangeRateRow` in `lib/types/api.ts`) | `snake_case` property names | ✅ Correct |
| Frontend in-memory (`ExchangeRateMatrixRow` in `mockData.ts`) | `camelCase` semantic names | ✅ Correct |
| Translation boundary | Must live in `lib/api/mappers.ts` | ❌ Lives in component file |

**Current translation path (non-compliant):**

```ts
// In ExchangeRateInquiryPage.tsx — violates the "boundary" rule
function apiRowToMatrix(row: ApiExchangeRateRow): ExchangeRateMatrixRow {
    return {
        currency: row.ccy_cd,         // snake_case accessed inside component
        flag: FLAG_MAP[row.ccy_cd] ?? "🏳️",
        cashBuy:  formatRate(row.buy_amt),
        cashSell: formatRate(row.sell_amt),
        ttBuy:    formatRate(row.tran_buy_amt),
        ttSell:   formatRate(row.tran_sell_amt),
    };
}
```

The component additionally reads `row.last_update_date` and `row.last_update_time`
directly (snake_case) inside the `inquiredOn` `useMemo` — another boundary violation.

**Compliant path (required):** `apiRowToMatrix` must move to `lib/api/mappers.ts` and
`formatRate` must move to (or call) `lib/currency.ts`. Components must only import
the camelCase in-memory type.

### Filter State

| State | Variable | Driven by API? | Notes |
|---|---|---|---|
| Selected bank | `selectedBankIdx: number` (0 or 1) | No — hardcoded `BANK_OPTIONS` | Cycle toggle, not a dropdown |
| Compare enabled | `compareEnabled: boolean` | No | Checkbox state; second bank dropdown is UI-only |
| Search | `search: string` | No (client-side filter) | Filters `matrixRows` by `r.currency.toLowerCase().includes(search)` |
| Date | Hardcoded `"05-Jan-2025"` | No | Not wired to `DateField`; never sent to the API |

The `selectedBank.bankCd` is passed to `fetchExchangeRates({ bankCd })` as a real API
parameter. Search and date are client-side only; compare is entirely UI-only.

### Mock Data Fallback

`useApiData` does not expose an error flag. When the API call throws (network failure,
auth error, empty response), `apiData` remains `undefined`. The component's `useMemo`
checks `apiData && apiData.rates.length > 0` — if false for any reason, it falls back
to `exchangeRateMatrixRows` from `mockData.ts`. No error dialog is shown. The user
sees stale VND-denominated mock data silently, with no indication the live fetch failed.

---

## 6. Gap Analysis — Backend

### B-1 · Input Validation: `ref_date` format not checked

**What it is:** `ExchangeRateService.list()` receives `refDate` as a raw `String` and
passes it directly to the repository query without validating it as a valid `yyyyMMdd`
date. A caller supplying `ref_date=abc` or `ref_date=2025-07-02` will receive an empty
result or a database-level error rather than a `400 Bad Request`.

**Rule / file violated:** `wecms-api/CLAUDE.md` §"Input Validation" — "Validate
at system boundaries (user input, external APIs)." Also inconsistent with `AccountController`
which uses `@Pattern(regexp = "^[0-9]{1,15}$")` on query parameters.

**Fix required:**
- Add `@Validated` to `ExchangeRateController`.
- Annotate the `refDate` parameter: `@Pattern(regexp = "^[0-9]{8}$", message = "ref_date must be YYYYMMDD") @RequestParam(required = false) String refDate`.
- Add a corresponding `@DateTimeFormat` parse check or a manual `LocalDate.parse(refDate, DATE8)` inside `resolveDate()` that throws `WbValidationException` on failure.

**Effort:** S

---

### B-2 · Timezone: `resolveDate()` does not use Asia/Ho_Chi_Minh

**What it is:** `ExchangeRateService.resolveDate()` calls `LocalDate.now()` with no
`ZoneId` argument. `LocalDate.now()` uses the JVM's default timezone, which in a
Docker-based deployment defaults to UTC. At UTC 17:00, UTC+7 Vietnam is already the
next calendar day (`t+1`). If the batch populates rates for Vietnam's "today" (`t+1`)
and the API resolves `refDate` to UTC's "today" (`t`), the endpoint returns no rows
for the current day until the fallback-to-latest-date logic kicks in — hiding the
intended behavior behind a fallback rather than returning the correct primary result.

**Rule / file violated:** `wecms-api/CLAUDE.md` — timezone policy; the batch
reference date is Vietnam business-day aligned (`REF_DATE` reflects the Vietnam date
the rates were published). The read path must use the same anchor.

**Fix required:**
```java
private static final ZoneId VIETNAM_TZ = ZoneId.of("Asia/Ho_Chi_Minh");

private String resolveDate(String refDate) {
    if (refDate != null && !refDate.isBlank()) {
        return refDate;
    }
    return LocalDate.now(VIETNAM_TZ).format(DATE8);
}
```

**Effort:** S

---

### B-3 · EXCH_SEQ_NO: Repository returns all sequence rows; JEX prefers run-2

**What it is:** `findByBizKeyAndBankCdAndRefDateOrderByCcyCdAsc` returns every row
matching `(biz_key, bank_cd, ref_date)`, including both `EXCH_SEQ_NO='1'` and
`EXCH_SEQ_NO='2'` when a second batch run occurred on the same date. The JEX IDO
`BANK_EXCH_RATE_INFM_R002` explicitly prefers the run-2 row via a `COALESCE` subquery,
returning only the preferred sequence's rows. Without this filtering, the frontend
receives duplicate currency rows and renders them as duplicate table entries.

**Rule / file violated:** Functional parity with JEX IDO `BANK_EXCH_RATE_INFM_R002`.
Documented in §3 of this document.

**Fix required:** Add a JPQL query that selects only the latest `EXCH_SEQ_NO` per
currency for the requested date:

```java
@Query("""
    SELECT e FROM BankExchRateInfm e
    WHERE e.bizKey = :bizKey
      AND e.bankCd = :bankCd
      AND e.refDate = :refDate
      AND e.exchSeqNo = (
          SELECT MAX(e2.exchSeqNo) FROM BankExchRateInfm e2
          WHERE e2.bizKey = e.bizKey
            AND e2.bankCd = e.bankCd
            AND e2.refDate = e.refDate
            AND e2.ccyCd = e.ccyCd
      )
    ORDER BY e.ccyCd ASC
""")
List<BankExchRateInfm> findLatestSeqByBizKeyAndBankCdAndRefDate(
    @Param("bizKey") String bizKey,
    @Param("bankCd") String bankCd,
    @Param("refDate") String refDate);
```

Call this method from `ExchangeRateService.list()` in place of the current
`findByBizKeyAndBankCdAndRefDateOrderByCcyCdAsc` call. The fallback path uses the
same replacement method.

**Effort:** M

---

### B-4 · OpenAPI Annotations: `@Tag` and `@Operation` absent

**What it is:** `ExchangeRateController` has no `@Tag` (class level) or `@Operation`
(method level) Swagger/OpenAPI annotations. Other controllers in the project follow
the same convention — none have these annotations either — but the project's own
`CLAUDE.md` API documentation standards and the Swagger UI (if enabled) expect them
for self-documenting APIs.

**Rule / file violated:** `wecms-api/CLAUDE.md` §"API Documentation" — controllers
should carry `@Tag` and `@Operation` annotations so Swagger UI renders accurate endpoint
descriptions.

**Fix required:**
```java
@Tag(name = "Banking — Exchange Rate", description = "Read exchange rate data populated by batch EXCH_0001_001")
@RestController
@RequestMapping("/api/v1/banking")
public class ExchangeRateController {

    @Operation(summary = "List exchange rates for the authenticated tenant",
               description = "Returns all counter-currency rates for the given bank and reference date. " +
                             "Defaults to today (Asia/Ho_Chi_Minh) and Shinhan Bank when params are omitted. " +
                             "Falls back to the most recent available date when today has no data.")
    @GetMapping("/exchange-rates")
    public ApiResponse<ExchangeRateListResponse> list(...) { ... }
}
```

Add `springdoc-openapi-starter-webmvc-ui` to `pom.xml` if not already present.

**Effort:** S

---

### B-5 · Audit Logging: Read-only endpoint — exempt

**What it is:** `ExchangeRateController.list()` performs no data modification. The
project's audit logging policy (as implemented via `AuditLogService`) targets write
operations (create, update, delete) and sensitive reads. A read-only rate inquiry does
not mutate business data and does not access personally identifiable information —
it is appropriately exempt from audit logging.

**Rule / file violated:** None. No gap exists here.

**Status:** No action required.

---

## 7. Gap Analysis — Frontend

### F-1 · `apiRowToMatrix()` lives inside the component file

**What it is:** `apiRowToMatrix(row: ApiExchangeRateRow): ExchangeRateMatrixRow` is
defined at module scope inside `ExchangeRateInquiryPage.tsx`. It reads snake_case
properties (`row.ccy_cd`, `row.buy_amt`, `row.sell_amt`, `row.tran_buy_amt`,
`row.tran_sell_amt`) directly and calls the page-local `formatRate()`.

**Rule violated:** `wecms-web/CLAUDE.md` §"API Naming & Serialization Standards" —
"Pages, hooks, components, storage helpers MUST NOT directly read snake_case property
names off a response … If you find yourself writing `response.data.user_id` in a
component, that means a mapper is missing — extend `lib/api/mappers.ts` first."

**Fix required:** Move `apiRowToMatrix` to `lib/api/mappers.ts` (after fixing F-3 so it
can call `lib/currency.ts`). Import and call it from `ExchangeRateInquiryPage.tsx`.
The `inquiredOn` timestamp computation (F-2) must also move or consume a mapped type.

**Files to edit:** `ExchangeRateInquiryPage.tsx`, `lib/api/mappers.ts`
**Effort:** S

---

### F-2 · Component reads snake_case fields directly in `inquiredOn` `useMemo`

**What it is:** Inside `ExchangeRateInquiryPage.tsx`:

```ts
if (apiData?.rates[0]?.last_update_date && apiData.rates[0].last_update_time) {
    const d  = apiData.rates[0].last_update_date;
    const tm = apiData.rates[0].last_update_time;
    ...
}
```

`apiData` is of type `ApiExchangeRateListResponse` — a wire type with snake_case
property names. The component reads `last_update_date` and `last_update_time` directly.

**Rule violated:** Same as F-1 — "components never see snake_case."

**Fix required:** The `inquiredOn` computation must use the camelCase in-memory type
produced by the mapper. Once F-1 is implemented, expose `lastUpdateDate` and
`lastUpdateTime` on `ExchangeRateMatrixRow` (or a wrapper type) from the mapper, and
compute `inquiredOn` from the mapped data, not from the raw `apiData` wire type.

Alternatively, add an `inquiredOn: string` field to the mapped response object returned
from a `lib/api/mappers.ts` response-level mapper, so the component only consumes
pre-formatted strings.

**Files to edit:** `ExchangeRateInquiryPage.tsx`, `lib/api/mappers.ts`, `mockData.ts`
(to add `lastUpdateDate`/`lastUpdateTime` or `inquiredOn` to `ExchangeRateMatrixRow`)
**Effort:** S

---

### F-3 · `formatRate()` is a page-local function; belongs in `lib/currency.ts`

**What it is:**

```ts
function formatRate(value: number | null): string {
    if (value === null) return "—";
    const decimals = value < 1 ? 4 : 2;
    return value.toLocaleString("en-US", {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
    });
}
```

This function is defined inside `ExchangeRateInquiryPage.tsx`. It uses the same
locale-aware numeric formatting pattern that `lib/currency.ts`'s `formatAmount` uses,
but with different precision rules suited to exchange rates.

**Rule violated:** `wecms-web/CLAUDE.md` §"Common Reusability Rule" — "Currency /
amount formatting" belongs in `lib/currency.ts`. §"Print / Export Rule" — "Amount values
rendered … MUST go through the shared format function." Exchange rates are monetary
values; their formatting must be consistent and centralized.

**Fix required:** Add `formatRate(value: number | null, decimals?: number): string` to
`lib/currency.ts`. The auto-precision logic (`value < 1 ? 4 : 2`) may remain as the
default; callers that need a fixed decimal count can pass `decimals` explicitly. Remove
the local definition from `ExchangeRateInquiryPage.tsx` and import from `lib/currency.ts`.

**Files to edit:** `lib/currency.ts`, `ExchangeRateInquiryPage.tsx`
**Effort:** S

---

### F-4 · Date chip is a hardcoded static string; not wired to `DateField`

**What it is:**

```tsx
<div className="flex h-[var(--h-wb-filter-btn)] items-center rounded-md bg-white px-2 shadow-wb-btn">
    <span className="text-wb-14-tight font-medium text-wb-ntrl-8">
        05-Jan-2025
    </span>
</div>
<CalendarIconButton />
```

The date is hardcoded to `"05-Jan-2025"` as a literal string. The `<CalendarIconButton />`
renders but has no `onClick` handler and opens no calendar popup. The date is never
sent to the API.

**Rule violated:** `wecms-web/CLAUDE.md` §6b — "Single-date input + calendar popup.
When a filter row needs a date chip … render the shared `<DateField>`." Also §"Rules for
New Menu Pages" §5 — "No new hardcoded mock arrays [or strings]."

**Fix required:** Replace the hardcoded chip + bare `<CalendarIconButton />` with the
shared `<DateField>` component. Wire `onChange` to update a `refDate` state variable.
Pass `refDate` to `fetchExchangeRates({ bankCd, refDate })` so the API is called with
the selected date.

**Files to edit:** `ExchangeRateInquiryPage.tsx`
**Effort:** M

---

### F-5 · No error handling; API failure silently falls back to mock data

**What it is:** `useApiData` does not expose an error state. When `fetchExchangeRates`
throws `ApiCallError` (network failure, 401, server error), `apiData` stays `undefined`
and the component silently renders `exchangeRateMatrixRows` — the mock fallback. The
user sees plausible-looking rates with no indication data could not be loaded.

**Rule violated:** `wecms-web/CLAUDE.md` §"Error handling (frontend side)" —
"Errors surface as `LoginError` / `ApiCallError` carrying `{ code, message, traceId }`.
Read those … Never display 'UNKNOWN' / 'Unexpected error' without a code." Also
§"Dialogs & modals" — API failures must go through `useDialog().open({ type: "error" })`.

**Fix required:** Either extend `useApiData` to expose an `error` state, or wrap the
`fetchExchangeRates` call in a `try/catch` pattern (using a manual `useState` + `useEffect`).
On failure, call:

```ts
dialog.open({
    type: "error",
    title: t("dialog.error"),
    description: error.message,
    errorCode: error.code,
});
```

The mock fallback should be removed or replaced with an empty state (an empty `rates`
array after the error dialog is dismissed).

**Files to edit:** `ExchangeRateInquiryPage.tsx`, possibly `lib/api/useApiData.ts`
**Effort:** M

---

### F-6 · `BANK_OPTIONS` is hardcoded; not driven by a backend or config endpoint

**What it is:**

```ts
const BANK_OPTIONS: { label: string; bankCd: string }[] = [
    { label: "Shinhan Bank", bankCd: "11704088" },
    { label: "Woori Bank",   bankCd: "11704020" },
];
```

The list of banks available for exchange rate inquiry is hardcoded at the top of the
component. Adding a new bank source requires a frontend code change.

**Rule violated:** `wecms-web/CLAUDE.md` §"Data rules" — "Company/user data from
`userStorage` or `/api/v1/auth/me` — never inline." More broadly, bank lists are
business configuration that belongs in backend-driven data.

**Fix required (short term):** Extract `BANK_OPTIONS` to a shared constant in
`lib/api/banking.ts` or a dedicated `lib/config/bankOptions.ts` so the list is not
buried inside the component file. This is the minimum fix for static UI.

**Fix required (long term):** Drive the list from an API endpoint (e.g.
`GET /api/v1/banking/exchange-rate-banks`) that returns the banks for which the
batch has populated data. This endpoint does not exist yet; it would require a new
backend query (`SELECT DISTINCT BANK_CD FROM BANK_EXCH_RATE_INFM WHERE BIZ_KEY = ?`).

**Files to edit:** `ExchangeRateInquiryPage.tsx`, `lib/api/banking.ts`
**Effort short-term:** S · **Effort long-term:** M (requires backend work)

---

### F-7 · Bank dropdown uses a cycle toggle, not a real dropdown

**What it is:**

```tsx
<FilterDropdown
    value={selectedBank.label}
    onClick={() =>
        setSelectedBankIdx(
            (selectedBankIdx + 1) % BANK_OPTIONS.length,
        )
    }
    className="w-[154px]"
/>
```

Clicking the bank dropdown cycles through `BANK_OPTIONS` by index rather than opening a
dropdown list. With two options this works functionally; with three or more it becomes
unusable (the user must cycle past unwanted options). The `FilterDropdown` component
supports a popover list but is being used as a cycle button.

**Rule violated:** `wecms-web/CLAUDE.md` §"Refactor Rule" — UI must match Figma.
The Figma 38:13376 frame shows a standard dropdown, not a cycle toggle. The cycle-toggle
pattern was identified as an antipattern in CLAUDE.md §12a.

**Fix required:** Replace the `onClick` cycle toggle with a proper dropdown — either a
`<FilterDropdown>` popover list (pass `options` and `onSelect`) or a `<select>` element
styled with Tailwind tokens, depending on how `FilterDropdown` is implemented. The
selected value must be driven by state, not by index arithmetic.

**Files to edit:** `ExchangeRateInquiryPage.tsx`
**Effort:** S (if `FilterDropdown` supports options; M if the component needs extension)

---

### F-8 · Compare-bank feature is entirely UI-only

**What it is:** The "Compare with" checkbox enables a second bank dropdown, but no
second API call is made. When `compareEnabled` is `true` and the user selects a second
bank, the table still shows only the primary bank's rates.

**Rule violated:** `wecms-web/CLAUDE.md` §"Data rules" — "If an endpoint exists,
wire it." The compare feature promises rate comparison but delivers none.

**Fix required:** This is a scope gap, not a code quality violation. The fix has two
phases:
1. When `compareEnabled` is `false`, leave the UI as-is (checkbox present, dropdown
   disabled — already correct).
2. When `compareEnabled` becomes `true`, call `fetchExchangeRates({ bankCd: compareBank.bankCd })`
   for the second bank and extend the table columns to show both banks' rates side-by-side
   (or a delta column). This requires a second set of `RATE_COLS` driven by the comparison
   bank's label and a merged row shape.

Until the compare API call is wired, the checkbox should be disabled or labelled
"(coming soon)" — showing an enabled-but-inert UI is misleading.

**Files to edit:** `ExchangeRateInquiryPage.tsx`, possibly `lib/api/banking.ts`
**Effort:** L (table column extension is substantial)

---

## 8. Implementation Plan

### Backend Tasks

| ID | Description | Files to Edit | Effort | CLAUDE.md Section |
|---|---|---|---|---|
| B-1 | Add `@Validated` to controller + `@Pattern(regexp="^[0-9]{8}$")` on `refDate` param; add format check in `resolveDate()` throwing `WbValidationException` on invalid input | `ExchangeRateController.java`, `ExchangeRateService.java` | S | wecms-api §"Input Validation" |
| B-2 | Fix `resolveDate()` to use `LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))` instead of `LocalDate.now()` | `ExchangeRateService.java` | S | wecms-api §Timezone |
| B-3 | Replace `findByBizKeyAndBankCdAndRefDateOrderByCcyCdAsc` with a JPQL query that selects only the MAX `EXCH_SEQ_NO` per currency per date; update both the primary call and the fallback-date retry in `ExchangeRateService` | `BankExchRateInfmRepository.java`, `ExchangeRateService.java` | M | JEX IDO R002 parity |
| B-4 | Add `@Tag` (class level) and `@Operation` (method level) OpenAPI annotations to `ExchangeRateController`; verify `springdoc-openapi` dependency is in `pom.xml` | `ExchangeRateController.java`, `pom.xml` | S | wecms-api §"API Documentation" |

**Recommended backend order:** B-2 → B-1 → B-3 → B-4. Fix the timezone first (affects
correctness on every environment), then validation (safety), then seq_no parity
(data integrity), then Swagger (documentation).

---

### Frontend Tasks

| ID | Description | Files to Edit | Effort | CLAUDE.md Section |
|---|---|---|---|---|
| F-3 | Add `formatRate(value: number \| null, decimals?: number): string` to `lib/currency.ts`; remove local definition from component | `lib/currency.ts`, `ExchangeRateInquiryPage.tsx` | S | §"Print / Export Rule", §"Common Reusability Rule" |
| F-1 | Move `apiRowToMatrix` to `lib/api/mappers.ts`; update its signature to use the camelCase in-memory type; import from component | `lib/api/mappers.ts`, `ExchangeRateInquiryPage.tsx` | S | §"API Naming & Serialization Standards" |
| F-2 | Remove direct `apiData.rates[0].last_update_date` / `.last_update_time` reads from component; expose `inquiredOn` (or raw date/time fields) from the mapper or from a mapped response type | `lib/api/mappers.ts`, `ExchangeRateInquiryPage.tsx`, `mockData.ts` | S | §"API Naming & Serialization Standards" |
| F-5 | Add error handling: expose `error` from `useApiData` or use manual `useState`+`useEffect`; on failure call `useDialog().open({ type: "error", ... })`; remove silent mock fallback | `ExchangeRateInquiryPage.tsx`, `lib/api/useApiData.ts` | M | §"Error handling (frontend side)", §"Dialogs & modals" |
| F-7 | Replace cycle-toggle bank `FilterDropdown` with a real options-list dropdown; drive options from `BANK_OPTIONS`; remove index arithmetic | `ExchangeRateInquiryPage.tsx` | S | §12a (antipattern), §"Refactor Rule" |
| F-4 | Replace hardcoded date chip + bare `<CalendarIconButton>` with shared `<DateField>`; wire `onChange` to `refDate` state; pass `refDate` to `fetchExchangeRates` | `ExchangeRateInquiryPage.tsx` | M | §6b Single-date input |
| F-6 | Extract `BANK_OPTIONS` from component to `lib/api/banking.ts` (short-term); later drive from `GET /api/v1/banking/exchange-rate-banks` (long-term, requires new backend endpoint) | `ExchangeRateInquiryPage.tsx`, `lib/api/banking.ts` | S/M | §"Data rules" |
| F-8 | Wire compare-bank feature: second `fetchExchangeRates` call when `compareEnabled`; extend table columns to show both banks; OR disable the checkbox with a "coming soon" label until the feature is scoped | `ExchangeRateInquiryPage.tsx` | L | §"Refactor Rule" |

**Recommended frontend order:** F-3 → F-1 → F-2 (these three are tightly coupled and
form one atomic refactor of the mapper/currency boundary) → F-5 (error handling,
independent) → F-7 (bank dropdown fix, independent) → F-4 (date wiring, requires B-2
to be landed first so timezone is correct) → F-6 (bank list extraction) → F-8 (compare
feature, scope-dependent).

---

## 9. Locale Coverage

### Keys in `payment.exchange_rate.*` namespace

| Key | EN | KO | VI | Notes |
|---|---|---|---|---|
| `payment.exchange_rate.title` | "Exchange Rate Inquiry" | "환율 조회" | "Tra cứu tỷ giá" | ✅ All three present |
| `payment.exchange_rate.choose_bank` | "Choose bank" | "은행 선택" | "Chọn ngân hàng" | ✅ All three present |
| `payment.exchange_rate.sequence_no` | "Sequence No." | "순서" | "Số thứ tự" | ✅ All three present |
| `payment.exchange_rate.last_time` | "Last time" | "최근" | "Lần cuối" | ✅ All three present |
| `payment.exchange_rate.compare_with` | "Compare with" | "비교 은행" | "So sánh với" | ✅ All three present |
| `payment.exchange_rate.inquired_on` | "Inquired on {date}" | "조회일시 {date}" | "Tra cứu lúc {date}" | ✅ All three present; uses `{date}` interpolation |
| `payment.exchange_rate.col.cash_buy` | "Cash Buy" | "현찰매입" | "Mua tiền mặt" | ✅ All three present |
| `payment.exchange_rate.col.cash_sell` | "Cash Sell" | "현찰매도" | "Bán tiền mặt" | ✅ All three present |
| `payment.exchange_rate.col.tt_buy` | "T/T Buy" | "전신환매입" | "Mua chuyển khoản" | ✅ All three present |
| `payment.exchange_rate.col.tt_sell` | "T/T Sell" | "전신환매도" | "Bán chuyển khoản" | ✅ All three present |

### Related keys found in other namespaces

| Key | Where used | EN | Status |
|---|---|---|---|
| `settings.users.menu.payment.exchange_rate_inquiry` | Settings > Users menu | "Exchange Rate Inquiry" | ✅ All three locales |
| `report.cash_flow.exchange_rate` | Cash Flow report | "Exchange Rate" | ✅ All three locales |

### Missing Keys (gaps)

The following keys are used or will be needed but do not yet exist in any locale file:

| Missing Key | Needed for | Suggested EN Value |
|---|---|---|
| `payment.exchange_rate.error.fetch_failed` | F-5 error dialog description | "Failed to load exchange rates. Please try again." |
| `payment.exchange_rate.compare_bank` | Compare-bank dropdown aria-label / UI label (when F-8 is implemented) | "Compare bank" |
| `payment.exchange_rate.no_data` | Empty-state message when rates list is empty after a successful API call | "No exchange rate data available for the selected date." |

### Translation Accuracy Notes

- **KO `payment.exchange_rate.sequence_no`:** "순서" is "order" / "sequence" — acceptable
  for the Sequence No. filter label but slightly informal. The live JEX system uses
  "회차" (meaning "run number" / "batch number"). Either is acceptable; align with the
  PO's preference.
- **KO `payment.exchange_rate.compare_with`:** "비교 은행" means "comparison bank" (the
  noun, not the verb phrase "Compare with"). EN says "Compare with" (a verb phrase).
  The KO renders as a noun label — acceptable for a checkbox-adjacent label but
  semantically different from EN.
- **VI translations** are accurate relative to the business context (banking,
  exchange rate, telegraphic transfer). No verified discrepancies found.
- All locale files include the `{date}` interpolation placeholder in
  `payment.exchange_rate.inquired_on` — correct for the `t("payment.exchange_rate.inquired_on", { date: inquiredOn })` call.

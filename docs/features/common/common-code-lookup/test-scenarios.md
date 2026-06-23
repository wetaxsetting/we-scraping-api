# Common Code Lookup — Test Scenarios (Backend)

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

## Country list (`GET /api/v1/common/countries`)

| # | Scenario | Expected |
|---|---|---|
| C-1 | Authenticated GET | 200; `data.countries` non-empty; `total_count == countries.length` |
| C-2 | **No tenant leakage** — call as tenant A then tenant B (different JWT) | identical row set both times (no `BIZ_KEY` filter; shared reference data) |
| C-3 | Default flag | exactly the Vietnam row (`ntnl_cd == "VN"`) has `default_yn == "Y"`; all others `"N"` |
| C-4 | Sort order | rows ascending by `natl_nm` |
| C-5 | Localized columns | wire ships English `natl_nm`; KO/VI/KM name columns NOT present (current PARTIAL contract) |
| C-6 | Wire casing | all keys lowercase snake_case (`ntnl_cd`, `natl_nm`, `natn_num`, `default_yn`, `total_count`) |
| C-7 | No JWT | 401 standard envelope via `JwtAuthenticationEntryPoint` |
| C-8 | Empty `NATN_INFM` | 200; `countries: []`, `total_count: 0` (no error) |

## Bank-name resolver (domain-embedded; tested via owning feature)

| # | Scenario | Expected |
|---|---|---|
| B-1 | Known bank code | `BankCdResolver.resolveBankNm` returns the cached legacy short name |
| B-2 | **Cache miss → DB fallback** | resolves via `resolveBankNmShort`; on null falls to `BANK_INFM.RPRS_BANK_NM`; on null returns raw code (never throws, not cached) |
| B-3 | Unknown bank code | returns the raw `bankCd` verbatim |
| B-4 | NAPAS-or-CITAD path | `resolveNapasOrCitadBankName("970424","en")` → legacy abbreviation; blank/null code → `null` (short-circuit, no DB call) |
| B-5 | Divergence check | `970424` resolves differently via `resolveNapasOrCitadBankName` vs `resolveBankNameAnyCode` (documented; per-leg correctness) |

> B-1..B-5 belong to the owning Account Inquiry / Payment / Contacts test suites — this slug has
> no standalone bank endpoint to test.

## Not testable here (owned elsewhere)

- Reporting-account list / CRUD → Settings `chart-of-accounts` test plan.
- Purpose-code list / CRUD → Settings `purpose-settings` test plan.

## Coverage status

⚠️ **UNVERIFIED** — no dedicated JUnit test for `CountryService` / `CountryController` was located
this pass. C-1..C-8 are the scenarios to add; confirm/author under
`src/test/.../domain/common/country/`.

## Related

- Functional spec: [`functional-specification.md`](functional-specification.md)
- Frontend tests: [`../../../../../wecms-web/docs/features/common/common-code-lookup/test-scenarios.md`](../../../../../wecms-web/docs/features/common/common-code-lookup/test-scenarios.md)

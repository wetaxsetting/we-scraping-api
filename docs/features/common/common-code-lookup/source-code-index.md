# Common Code Lookup — Source Code Index (Backend)

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

## Country (the one Common endpoint) — `domain/common/country/`

| File | Role |
|---|---|
| `controller/CountryController.java` | `GET /api/v1/common/countries` → `CountryService.list()` (`:31`) |
| `service/CountryService.java` | `list()` (`:36`), `toRow()` computes `default_yn` (`:42-48`); `DEFAULT_NTNL_CD="VN"` (`:28`) |
| `entity/NatnInfm.java` | `@Table NATN_INFM`; `@Id ntnlCd`, `natlNm`, `natnNum`, `timeZone`, `note`; extends `BaseEntity` |
| `repository/NatnInfmRepository.java` | `findAllByOrderByNatlNmAsc()` — no `BIZ_KEY` (shared) |
| `dto/CountryListResponse.java` | `{ countries: CountryRow[], total_count }` |
| `dto/CountryRow.java` | `{ ntnl_cd, natl_nm, natn_num, default_yn }` |

## Bank-name reference (domain-embedded resolvers)

| File | Role |
|---|---|
| `domain/banking/service/BankCdResolver.java` | startup cache (`warmCache` `:71-78`) + `resolveBankNm` (`:90-110`); acct-type label/key (`:119-137`) |
| `domain/banking/entity/BankInfm.java` | `@Table BANK_INFM` registry (global, V20 seed) |
| `domain/banking/repository/BankInfmRepository.java` | `JpaRepository<BankInfm, String>` |
| `common/legacy/LegacyFunctionRepository.java` | `resolveNapasOrCitadBankName(code, langCd)` (`:494-503`, V114 verbatim, over `tb_firm_bank_code`); `resolveBankNmShort/All`; `resolveBankNameAnyCode` (`:665`) |

## Currency

| File | Role |
|---|---|
| (none) | ⚠️ No `CcyInfm` entity exists. `CCY_INFM` is in the inventory but unmapped (no consumer). `domain/banking/repository/CcyAggregate.java` is a projection over transaction data, **not** a `CCY_INFM` entity — do not mistake it for currency reference data. |

## Owned-elsewhere (cross-link, not indexed here)

| Reference | Owning package |
|---|---|
| Reporting account | `domain/setting/chartofaccounts/` (see `../../settings/chart-of-accounts/source-code-index.md`) |
| Purpose codes | `domain/setting/purposesettings/` (see `../../settings/purpose/`) |

## Frontend (shared selects + fetchers)

| File | Role |
|---|---|
| `wecms-web/lib/api/countries.ts` | `fetchCountries()` → `GET /api/v1/common/countries`; `ApiCountryRow`/`Country` types + `toCountry` mapper |
| `wecms-web/components/shared/SearchSelect.tsx` | generic foundation — portal popover, pinned search, RECENT + main sections, ellipsis tooltip |
| `wecms-web/components/shared/PurposeSelect.tsx` | adapter over `SearchSelect`; options from Purpose Settings / Card Transactions |
| `wecms-web/components/shared/ReportingAccountSelect.tsx` | adapter over `SearchSelect`; options from `chart-of-accounts` |
| `wecms-web/components/shared/FilterSelect.tsx` | filter-row single-select popover |
| `wecms-web/components/shared/FilterDropdown.tsx` | filter-row click-to-cycle dropdown trigger |
| `wecms-web/components/settings/users/PhoneCountryPicker.tsx` | consumes `fetchCountries()`, applies per-screen allow-list `{kh,kr,vn}` |

> There is **no** shared `wecms-web/lib/api/lookup.ts` — verified absent. Options are sourced
> per-domain (countries from `lib/api/countries.ts`; purpose / chart-account from their owning
> feature API wrappers).

## Tests

⚠️ **UNVERIFIED** — no dedicated `CountryService` / `CountryController` test was located this pass.
Confirm under `wecms-api/src/test/.../domain/common/country/` before claiming coverage. See
`test-scenarios.md` for the intended scenarios.

## Related

- Hub: [`README.md`](README.md) · Audit source map: [`../../../audits/common-source-index.md`](../../../audits/common-source-index.md) §8

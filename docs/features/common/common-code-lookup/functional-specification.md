# Common Code Lookup — Functional Specification (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

## 1. Country list (`GET /api/v1/common/countries`)

| Capability | Behaviour | Source |
|---|---|---|
| Scope | **Shared across all tenants** — no `BIZ_KEY` filter | `CountryService.java:36`, `NatnInfmRepository.java:17` |
| Auth | JWT-authenticated (under `/api/v1/*`, authenticated per `SecurityConfig`); no `@menuAuthz` | `CountryController.java:18-20` |
| Read-only | List only; no create / update / delete | `CountryController.java:30-33` |
| Sort | By display name ascending (`findAllByOrderByNatlNmAsc`) | `CountryService.java:37` |
| Default flag | `default_yn = "Y"` for Vietnam (`NTNL_CD == "VN"`), else `"N"` — **computed**, not stored | `CountryService.java:27-28,42-48` |
| Wire casing | lowercase snake_case via global Jackson `SNAKE_CASE` (no per-field `@JsonProperty`) | `CountryRow.java:12-14` |
| Row count | ~238 rows seeded in `NATN_INFM` | inventory |

### Row shape (`CountryRow`)

| Wire field | Java field | Meaning | Source column |
|---|---|---|---|
| `ntnl_cd` | `ntnlCd` | ISO 3166-1 alpha-2 (e.g. `"VN"`) — stable key | `NATN_INFM.NTNL_CD` (PK) |
| `natl_nm` | `natlNm` | Display name in English (e.g. `"Vietnam"`) | `NATN_INFM.NATL_NM` |
| `natn_num` | `natnNum` | Dial code **without** leading `+` (e.g. `"84"`) | `NATN_INFM.NATN_NUM` |
| `default_yn` | `defaultYn` | `"Y"` for default country, else `"N"` — server-computed | (none — derived) |

Envelope `CountryListResponse` = `{ countries: CountryRow[], total_count }` (`CountryListResponse.java:10-17`).

> **Note on localized name columns.** The legacy `NATN_INFM` carries EN/KO/VI/KM name columns,
> but the refactor entity `NatnInfm` maps only `NATL_NM` (English) plus `NATN_NUM` / `TIME_ZONE` /
> `NOTE` (`NatnInfm.java:36-49`). The current wire ships English `natl_nm` only; localized
> per-language name columns are **not** surfaced today (frontend localises via its own allow-list
> + flag assets). Marked PARTIAL — see `refactor-gap-analysis.md`.

## 2. Bank-name resolution (domain-embedded — NOT a common endpoint)

| Capability | Behaviour | Source |
|---|---|---|
| Resolver | `BankCdResolver.resolveBankNm(bankCd)` returns legacy **short** name, falling back short → formal → raw code | `BankCdResolver.java:90-110` |
| Cache | `nameCache` warmed once at startup via `resolveBankNmShortAll()`; per-request DB fallback on miss | `BankCdResolver.java:69-78,94-109` |
| Account-type label/key | `resolveAcctTypeNm` (legacy English) + `resolveAcctTypeKey` (`bank.acct_type.<code>` i18n key via `AcctTypeCd`) | `BankCdResolver.java:119-137` |
| Contacts / NAPAS-or-CITAD | `LegacyFunctionRepository.resolveNapasOrCitadBankName(code, langCd)` over `tb_firm_bank_code` (V114, verbatim) | `LegacyFunctionRepository.java:494-503` |
| No static map | Bank-code → name MUST resolve through the table / funnel — never a hardcoded `Map.of(...)` | `wecms-api/CLAUDE.md` §"Reference data" |

These resolvers are invoked while building Account Inquiry / Payment responses. There is **no**
`GET /api/v1/common/banks`.

## 3. Currency (`CCY_INFM`)

- `CCY_INFM` exists in the legacy-table inventory but has **no JPA entity** in the refactor today
  (no `CcyInfm` class — verified absent). Currency codes (`"VND"`, `"USD"`, …) travel inside
  domain DTOs as plain strings; amount/symbol formatting is a display concern
  (`AmountFormatter` / frontend `lib/currency.ts`).
- There is **no** `GET /api/v1/common/currencies` endpoint. Marked MISSING — see `refactor-gap-analysis.md`.

## 4. What is NOT a common endpoint

| Reference | Why not here |
|---|---|
| Bank names | resolved inside Account Inquiry / Payment responses (§2) |
| Currency | no entity, no endpoint; plain string codes in domain DTOs (§3) |
| Reporting account (chart-of-accounts) | data + CRUD owned by Settings `chart-of-accounts`; only the FE select is shared |
| Purpose codes | owned by Settings `purpose-settings`; only the FE select is shared |

## Edge cases

- Empty `NATN_INFM` → `countries: []`, `total_count: 0` (no error).
- Unknown bank code → `resolveBankNm` returns the raw code (never null, never throws),
  not cached (retried next request) (`BankCdResolver.java:105-107`).
- Blank / null code to `resolveNapasOrCitadBankName` → short-circuits to `null` before the DB
  round-trip (`LegacyFunctionRepository.java:495-497`).

## Related

- Interface: [`interface-specification.md`](interface-specification.md) · DB: [`database-mapping.md`](database-mapping.md)
- Gap vocab: [`refactor-gap-analysis.md`](refactor-gap-analysis.md)

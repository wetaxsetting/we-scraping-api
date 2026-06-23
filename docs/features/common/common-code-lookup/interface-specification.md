# Common Code Lookup — Interface Specification (Backend)

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

## `GET /api/v1/common/countries`

The **only** Common reference-data endpoint. Read-only country master.

| Property | Value |
|---|---|
| Method / path | `GET /api/v1/common/countries` |
| Controller | `CountryController.list()` (`CountryController.java:31`) |
| Auth | JWT-authenticated (`/api/v1/*` → authenticated per `SecurityConfig`); **no `@menuAuthz`**, **no tenant filter** |
| Query params | none |
| Legacy | `comm_0110_001_r001` |
| Wire casing | lowercase snake_case (global Jackson `SNAKE_CASE`) |

### Response — `ApiResponse<CountryListResponse>`

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "countries": [
      { "ntnl_cd": "VN", "natl_nm": "Vietnam", "natn_num": "84", "default_yn": "Y" },
      { "ntnl_cd": "KH", "natl_nm": "Cambodia", "natn_num": "855", "default_yn": "N" }
    ],
    "total_count": 238
  }
}
```

`CountryRow` (`CountryRow.java:16-32`):

| Field | Type | Meaning |
|---|---|---|
| `ntnl_cd` | string | ISO 3166-1 alpha-2 — stable key |
| `natl_nm` | string | English display name |
| `natn_num` | string | dial code without `+` |
| `default_yn` | string | `"Y"` for default country (Vietnam), else `"N"` — server-computed |

`CountryListResponse`: `{ countries: CountryRow[], total_count: number }`.

### Errors

- 401 (JWT missing/invalid) via `JwtAuthenticationEntryPoint` (standard envelope).
- No feature-specific business error codes — the list path has no validation and no
  business-rule failure (returns empty list when the table is empty).

## Endpoints that do NOT exist today

| Hypothetical | Status | Where the data is instead |
|---|---|---|
| `GET /api/v1/common/banks` | ❌ not implemented | bank names resolved inside Account Inquiry / Payment responses (`BankCdResolver`) |
| `GET /api/v1/common/currencies` | ❌ not implemented | no `CcyInfm` entity; currency codes are plain strings in domain DTOs |
| `GET /api/v1/common/lookup` (generic) | ❌ not implemented | each reference resolves per-domain or per-owning-feature |
| `GET /api/v1/common/purpose-codes` | ❌ not here | owned by Settings `purpose-settings` |
| `GET /api/v1/common/reporting-accounts` | ❌ not here | owned by Settings `chart-of-accounts` (`GET /api/v1/settings/chart-of-accounts`) |

Promoting a shared `/api/v1/common/*` lookup surface (banks / currencies / usage codes) is an
**open decision (C6)** — see `refactor-gap-analysis.md`.

## Related

- Functional spec: [`functional-specification.md`](functional-specification.md)
- Frontend consumption: [`../../../../../wecms-web/docs/features/common/common-code-lookup/api-consumption.md`](../../../../../wecms-web/docs/features/common/common-code-lookup/api-consumption.md)

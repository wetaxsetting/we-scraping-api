# Common Code Lookup (reference data + shared selects) — Backend Feature Hub

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

**Status:** PARTIAL — only **Country** has a dedicated Common REST endpoint today.
Bank / Currency names are resolved **inside owning-domain responses**, not via a Common
endpoint. The reporting-account picker is a shared frontend select whose **data + CRUD belong
to Settings `chart-of-accounts`** (cross-linked, not duplicated here).
**Domain / slug:** `common` / `common-code-lookup` (global, screen-agnostic).
**Backend package:** `com.kosign.wecms.domain.common.country` (Country only).

> **Key framing — read first.** "Common code lookup" is the *concept* of shared reference
> data behind the product's pickers. In the refactor it is **not** one unified `/api/v1/common/*`
> lookup surface. It is exactly **one** Common endpoint (`GET /api/v1/common/countries`) plus a
> set of **domain-embedded resolvers** (bank names inside Account Inquiry / Payment responses)
> and a frontend select whose options come from **other features' endpoints** (Purpose Settings,
> Chart of Accounts). This hub documents what truly exists and marks the rest PARTIAL /
> owned-elsewhere.

## What exists today

| Reference data | Where it lives | Surface |
|---|---|---|
| **Country master** (`NATN_INFM`) | `domain/common/country/` | `GET /api/v1/common/countries` — the **only** Common reference endpoint |
| **Bank code → name** (`BANK_INFM` + `tb_firm_bank_code`) | `domain/banking/service/BankCdResolver` + `common/legacy/LegacyFunctionRepository` | resolved **inside** domain responses (Account Inquiry, Payment) — **no** standalone Common endpoint |
| **Currency** (`CCY_INFM`) | (no entity) | not exposed via any Common endpoint; currency codes ride inside domain DTOs as plain strings |
| **Reporting account** (chart-of-accounts) | Settings `chart-of-accounts` | `GET /api/v1/settings/chart-of-accounts` — data + CRUD owned there; surfaced in a shared FE select |
| **Purpose codes** (`BUSI_USAG_SET_INFM`) | Settings `purpose-settings` | owned there; surfaced in a shared FE select |

## Endpoints (one-line summary)

| Method | Path | Purpose | Legacy |
|---|---|---|---|
| GET | `/api/v1/common/countries` | Read-only country master — **no tenant filter** (shared across tenants), JWT-authenticated | `comm_0110_001_r001` |

There is **no** `GET /api/v1/common/banks`, `/api/v1/common/currencies`, or generic
`/api/v1/common/lookup` endpoint today — see `refactor-gap-analysis.md` (gap **C6**).

## Documents

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | Country fetch flow; bank-name resolution inside domain responses; reference-cache warm |
| [functional-specification.md](functional-specification.md) | Country list semantics (no tenant filter, shared, localized name columns); bank resolver cache + legacy fn; what is NOT a common endpoint |
| [database-mapping.md](database-mapping.md) | `NATN_INFM` columns/PK/entity map; `BANK_INFM` / `CCY_INFM` as domain-owned reference tables; `tb_firm_bank_code` for the legacy fn |
| [interface-specification.md](interface-specification.md) | `GET /api/v1/common/countries` contract + `CountryRow` shape; explicitly: no `/banks`, `/currencies`, `/lookup` endpoints today |
| [migration-guide.md](migration-guide.md) | `comm_0110_001_r001` → `CountryController`; legacy bank-name fn ported V114; "look up from canonical table, never a static map" |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Vocab: Country endpoint = MATCH; shared `/banks` `/currencies` lookup surface = MISSING/PARTIAL (open decision **C6**); reporting-account data = owned by chart-of-accounts |
| [source-code-index.md](source-code-index.md) | Country backend map + `BankCdResolver` / `LegacyFunctionRepository` + frontend selects + tests |
| [test-scenarios.md](test-scenarios.md) | Countries list returns shared rows, no tenant leakage, localized columns; bank resolver cache-miss fallback |

## Related

- Category hub: [`../README.md`](../README.md)
- Frontend twin: [`../../../../../wecms-web/docs/features/common/common-code-lookup/README.md`](../../../../../wecms-web/docs/features/common/common-code-lookup/README.md)
- Owning feature — reporting-account data + CRUD: [`../../settings/chart-of-accounts/README.md`](../../settings/chart-of-accounts/README.md)
- Owning feature — purpose codes: [`../../settings/purpose/README.md`](../../settings/purpose/README.md)
- Audit deliverables: [`../../../audits/common-source-index.md`](../../../audits/common-source-index.md) · [`common-gap-analysis.md`](../../../audits/common-gap-analysis.md) · [`common-docs-audit.md`](../../../audits/common-docs-audit.md)
- Reference-data rule: `wecms-api/CLAUDE.md` §"Reference data — single sources of truth"

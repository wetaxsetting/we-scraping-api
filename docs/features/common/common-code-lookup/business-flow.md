# Common Code Lookup — Business Flow (Backend)

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

Three distinct flows live under this slug. Only the first is a Common endpoint; the second is
domain-embedded; the third is owned elsewhere and cross-linked.

## Flow 1 — Country list fetch (the one Common endpoint)

```
GET /api/v1/common/countries  (JWT)
  → CountryController.list()                       CountryController.java:31
  → CountryService.list()  @Transactional(readOnly)  CountryService.java:36
      → NatnInfmRepository.findAllByOrderByNatlNmAsc()   (no BIZ_KEY filter — shared)
      → map each NatnInfm → CountryRow                  CountryService.java:42
          ntnl_cd, natl_nm, natn_num,
          default_yn = "Y" when NTNL_CD == "VN" else "N"
  → CountryListResponse(rows, rows.size())
  → ApiResponse.ok(...)
```

- **No tenant filter.** The country master is reference data shared across every tenant — the
  repository method has no `BIZ_KEY` argument (`CountryService.java:36-40`,
  `NatnInfmRepository.java:17`).
- **Default flag is computed server-side**, not stored: Vietnam (`NTNL_CD = "VN"`) → `default_yn = "Y"`
  (`CountryService.java:27-28,47`). Mirrors legacy IDO `CASE WHEN NTNL_CD='VN' THEN 'Y'`.
- **Sorted by display name** ascending (`findAllByOrderByNatlNmAsc`).
- Read-only; no create / update / delete surface.

## Flow 2 — Bank-name resolution INSIDE a domain response (not a Common endpoint)

Bank names are never fetched standalone. They are resolved while building an owning-domain
response (Account Inquiry account rows, Payment detail headers / per-line deposit banks):

```
AccountService.toRow(...) / payment mappers
  → BankCdResolver.resolveBankNm(bankCd)            BankCdResolver.java:90
      cache hit  → return cached short name
      cache miss → LegacyFunctionRepository.resolveBankNmShort(bankCd)  (funnel)
                   → fallback BankInfmRepository.findById(bankCd).RPRS_BANK_NM
                   → final fallback: raw bankCd
```

- **Startup cache warm.** `BankCdResolver.warmCache()` (`@PostConstruct`) loads every registry
  bank's short name once via `LegacyFunctionRepository.resolveBankNmShortAll()`
  (`BankCdResolver.java:71-78`). The Vietnamese registry is < 30 rows.
- **Per-request DB fallback** on cache miss so a newly-registered bank resolves without a restart
  (`BankCdResolver.java:94-109`).
- **Contacts / NAPAS-or-CITAD path** uses a different resolver:
  `LegacyFunctionRepository.resolveNapasOrCitadBankName(code, langCd)` over `tb_firm_bank_code`
  (`LegacyFunctionRepository.java:494-503`, ported verbatim V114). `BankCdResolver` is **not**
  consulted in that flow. The two diverge for some codes (e.g. `970424 → "Shinhan VN"` vs
  `"ShinhanBank"`) — see `LegacyFunctionRepository.java:572-580`.

These resolvers run inside the owning domain's mapper — there is **no** `GET /api/v1/common/banks`.

## Flow 3 — Reporting-account / purpose options (owned elsewhere)

The shared frontend `ReportingAccountSelect` and `PurposeSelect` are adapters over the generic
`SearchSelect`. Their **options** are fetched from the owning feature's endpoints, not a Common
lookup:

- Reporting account → `GET /api/v1/settings/chart-of-accounts` (Settings `chart-of-accounts`).
- Purpose → `GET /api/v1/settings/purpose-settings` (Settings `purpose-settings`) or the Card
  Transactions purpose-options endpoint.

This slug documents the **shared select foundation** (frontend twin) and cross-links the data
owners; it does **not** re-document chart-of-accounts / purpose CRUD.

## Reference cache warm (lifecycle)

| Cache | Loaded | Invalidation |
|---|---|---|
| `BankCdResolver.nameCache` | once at startup (`@PostConstruct warmCache`) | none (Sprint 1 has no bank write path); cache miss falls back to per-request DB lookup |
| Country list | not cached — read per request (small table, JWT-protected) | n/a |

## Related

- Hub: [`README.md`](README.md) · Functional spec: [`functional-specification.md`](functional-specification.md)
- Bank reference rule: `wecms-api/CLAUDE.md` §"Reference data — single sources of truth"

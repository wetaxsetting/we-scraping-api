# Common Code Lookup — Migration Guide (Backend)

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

## Country list — `comm_0110_001_r001` → `CountryController`

| Legacy (JEX) | Refactor | Notes |
|---|---|---|
| `comm_0110_001_r001_act.jsp` + `IDO.NATN_INFM_R002` | `CountryController` + `CountryService` + `NatnInfmRepository` | read-only list |
| `NATN_INFM` table | `NatnInfm` entity (`@Table NATN_INFM`) | English `NATL_NM` + `NATN_NUM` mapped; KO/VI/KM name columns not mapped (PARTIAL) |
| IDO `CASE WHEN NTNL_CD='VN' THEN 'Y' ELSE 'N'` | `CountryService.toRow` computes `default_yn` server-side (`CountryService.java:27-28,47`) | not a stored column |
| subRule fields `NTNL_CD` / `NATL_NM` / `NATN_NUM` + default flag | `CountryRow` `{ ntnl_cd, natl_nm, natn_num, default_yn }` | wire lowercase snake_case |
| UPPER_SNAKE wire | lowercase snake_case via global Jackson strategy | CTO directive 2026-05-08 |
| per-screen JSP country filter (`comm_0110_001_r001_act.jsp:80-85`, commented out) | endpoint returns the **full** directory; per-screen allow-list lives on the frontend picker | e.g. `PhoneCountryPicker.ALLOWED_ISO2 = {kh,kr,vn}` |

The legacy screen returned every country; the per-screen allow-list (Cambodia / Korea / Vietnam
for the Users phone picker) moved to the frontend, not the API. The API stays a generic directory.

## Bank-name resolution — legacy functions ported

| Legacy | Refactor | Migration |
|---|---|---|
| `wb_get_bank_nm(bank_cd, 's')` short name | `BankCdResolver` (cache + funnel via `LegacyFunctionRepository.resolveBankNmShort/All`) | warmed at startup; resolves inside domain responses |
| `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang_cd)` (`IDO.TRCO_INFM_R016`) | `LegacyFunctionRepository.resolveNapasOrCitadBankName` — body copied **verbatim** from DEV (V114), reads `tb_firm_bank_code` | OQ-7; one funnel call per unique `(code, lang_cd)` |

These were **not** promoted to a Common endpoint — they stay embedded in the owning domain's
mappers, matching the legacy behaviour where the name was resolved inside each IDO query.

## Reporting-account / purpose options — owned elsewhere

Not migrated under this slug. The shared frontend selects source their options from the owning
features' endpoints:

- Reporting account → Settings `chart-of-accounts` (`GET /api/v1/settings/chart-of-accounts`).
- Purpose → Settings `purpose-settings`.

## Migration principle (non-negotiable)

> **Reference data must be looked up from its canonical table, never a static map.**

The 2026-04-29 audit established this for bank codes: `BankCdResolver` reads `BANK_INFM` / the
legacy funnel, never a hardcoded `Map.of(...)`. The same rule governs every reference here —
Country comes from `NATN_INFM`, bank names from `BANK_INFM` + `tb_firm_bank_code`, never an
in-code constant map. See `wecms-api/CLAUDE.md` §"Reference data — single sources of truth".

## Identifier naming

Refactor surfaces use the slug `common-code-lookup` (and the REST URI `/api/v1/common/countries`),
never legacy WSVC ids. `comm_0110_001_r001` survives only as a JEX file:line citation / migration
prose, per `wecms-api/CLAUDE.md` §"Refactor identifier naming".

## Related

- Interface: [`interface-specification.md`](interface-specification.md) · DB: [`database-mapping.md`](database-mapping.md)
- Gap vocab: [`refactor-gap-analysis.md`](refactor-gap-analysis.md)

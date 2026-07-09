# Common Code Lookup — Database Mapping (Backend)

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

## 1. `NATN_INFM` — country master (the one Common reference table)

Entity `NatnInfm` (`@Table NATN_INFM`), repository `NatnInfmRepository`. Reference data, **no
`BIZ_KEY`** column — shared across all tenants. ~238 rows seeded.

| Column | Type (legacy) | Entity field | Wire | Notes |
|---|---|---|---|---|
| `NTNL_CD` | `VARCHAR(2)` NOT NULL | `ntnlCd` (`@Id`) | `ntnl_cd` | ISO 3166-1 alpha-2; PK |
| `NATL_NM` | `VARCHAR(100)` | `natlNm` | `natl_nm` | English display name |
| `NATN_NUM` | `VARCHAR(5)` | `natnNum` | `natn_num` | dial code without `+` |
| `TIME_ZONE` | `VARCHAR(50)` | `timeZone` | (not on wire) | Olson tz id; mapped but unused on wire |
| `NOTE` | `VARCHAR(255)` | `note` | (not on wire) | mapped, unused on wire |
| (audit) | — | inherited `BaseEntity` | — | `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM` (TIMESTAMPTZ — documented project-wide deviation from DEV `VARCHAR(14)`) |

Source: `NatnInfm.java:24-50`, `NatnInfmRepository.java:15-18`.

> **Localized name columns not mapped.** Legacy `NATN_INFM` carries per-language name columns
> (EN/KO/VI/KM). The refactor entity maps only `NATL_NM` (English) — the others are **not** mapped
> and **not** on the wire. ⚠️ The exact legacy column names for KO/VI/KM are **UNVERIFIED** in this
> doc (the live JEX DDL was not read this pass — confirm via the `postgres_dev_server_jexframe`
> MCP before relying on them). Per the Legacy Schema Parity Rule the DB schema must still carry all
> legacy columns column-for-column; the *entity* subset is the right scope.

The `default_yn` flag is **not** a column — it is computed in `CountryService.toRow` (Vietnam → `"Y"`).

## 2. `BANK_INFM` — bank registry (domain-owned reference table)

Entity `BankInfm` (`@Table BANK_INFM`), repository `BankInfmRepository`. **Global, not
tenant-scoped**; seeded by V20 from the real DEV registry (~29 rows).

| Column | Type | Entity field | Notes |
|---|---|---|---|
| `BANK_CD` | `VARCHAR(20)` NOT NULL | `bankCd` (`@Id`) | bank code, PK |
| `START_DATE` / `END_DATE` | `VARCHAR(8)` NOT NULL | `startDate` / `endDate` | date-effective window |
| `RPRS_BANK_NM` | `VARCHAR(200)` NOT NULL | `rprsBankNm` | formal representative name |
| (audit) | — | `regId`/`regDtm`/`modId`/`modDtm` (`VARCHAR`) | non-`BaseEntity` (own audit fields) |

Source: `BankInfm.java:25-51`, `BankInfmRepository.java:6-7`.

`BANK_INFM` is consumed by `BankCdResolver` as the final fallback for a bank's display name; the
primary name comes from the legacy `wb_get_bank_nm` short-name funnel. The display short name is
not a column on `BANK_INFM` — it derives from `BANK_INFM_DETL` via the legacy function.

## 3. `tb_firm_bank_code` — NAPAS / CITAD code lookup (legacy fn backing table)

Read by the ported legacy function `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang_cd)`
(V114) via `LegacyFunctionRepository.resolveNapasOrCitadBankName`. No JPA entity — accessed only
through the function. Returns the EN abbreviation for `lang_cd='en'`, the long name for
`vi`/`vn`; `null` for `ko`/`km`.

## 4. `CCY_INFM` — currency (in inventory, NOT mapped)

`CCY_INFM` is in the legacy-table inventory and the PROD active list, but the refactor has **no
`CcyInfm` entity** (verified absent this pass). Currency codes travel as plain strings inside
domain DTOs. ⚠️ Column structure **UNVERIFIED** — no entity to cite; do not create one without PO
approval (it is a baseline table that simply has no refactor consumer yet).

## Inventory check

| Table | In inventory / PROD active list | Mapped in refactor |
|---|---|---|
| `NATN_INFM` | ✅ | ✅ `NatnInfm` |
| `BANK_INFM` | ✅ | ✅ `BankInfm` |
| `CCY_INFM` | ✅ | ❌ no entity (no consumer yet) |
| `tb_firm_bank_code` | ✅ (firm-banking) | ❌ (accessed via legacy fn only) |

## Related

- Functional spec: [`functional-specification.md`](functional-specification.md)
- Reference-data rule: `wecms-api/CLAUDE.md` §"Reference data — single sources of truth"
- Legacy fn funnel: `wecms-api/CLAUDE.md` §"Surviving legacy functions" (V114 row)

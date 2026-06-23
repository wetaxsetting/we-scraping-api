# Common Code Lookup — Refactor Gap Analysis (Backend)

**Part of:** Common → common-code-lookup
**Last verified:** 2026-06-23

Gap vocabulary: **MATCH** · **APPROVED DEVIATION** · **PARTIAL** · **MISSING** · **OWNED-ELSEWHERE**.

## Parity matrix

| # | Surface | Legacy | Refactor | Verdict |
|---|---|---|---|---|
| G1 | Country list endpoint | `comm_0110_001_r001` over `NATN_INFM` | `GET /api/v1/common/countries` (`CountryController`) | **MATCH** — read-only, shared, `default_yn` computed server-side |
| G2 | Country localized name columns | `NATN_INFM` carries EN/KO/VI/KM names | entity maps `NATL_NM` (English) only; wire ships `natl_nm` only | **PARTIAL** — localized name columns not surfaced; FE localises via its own allow-list |
| G3 | Bank-name reference | IDO-embedded `wb_get_bank_nm` / `wb_get_napas_bank_nm_or_citad_bank_nm` | `BankCdResolver` (cache + funnel) + `resolveNapasOrCitadBankName` (V114 verbatim), resolved **inside** domain responses | **MATCH (domain-embedded)** — behaviour preserved; intentionally not a Common endpoint |
| G4 | Shared `/api/v1/common/banks` endpoint | — | does not exist | **MISSING / PARTIAL** — bank names only resolve inside owning-domain responses (open decision **C6**) |
| G5 | Currency reference (`CCY_INFM`) | legacy `CCY_INFM` | no `CcyInfm` entity; no `/api/v1/common/currencies`; codes are plain strings | **MISSING** — no consumer yet; table is in inventory but unmapped (open decision **C6**) |
| G6 | Reporting-account data + CRUD | legacy chart-of-accounts (`comm_0223_*` / `fnsh_0200_001` picker) | owned by Settings `chart-of-accounts` (`GET /api/v1/settings/chart-of-accounts`); shared FE select only | **OWNED-ELSEWHERE** — cross-link, not a gap |
| G7 | Purpose codes | legacy purpose (`BUSI_USAG_SET_INFM`) | owned by Settings `purpose-settings`; shared FE select only | **OWNED-ELSEWHERE** — cross-link, not a gap |
| G8 | Generic `/api/v1/common/lookup` dispatcher | — | does not exist (deliberately) | **MISSING (by design)** — each reference resolves per-domain / per-owning-feature |

## Open decision — C6 (lookup surface breadth)

> Only **Country** has a Common endpoint; Bank / Currency resolve inside domain responses and
> reporting-account / purpose options come from their owning features. **Decide whether to promote
> a shared `/api/v1/common/*` lookup surface (banks, currencies, usage codes) or keep them
> domain-embedded.**

Status: recorded, **not actioned** — needs PO (Mr. Jung). Tracked in
[`../../../audits/common-gap-analysis.md`](../../../audits/common-gap-analysis.md) rows A6 / C6 / D5.

Until C6 is decided:
- Keep bank-name resolution inside the owning domain mapper (do not extract a `/common/banks`).
- Do not create a `CcyInfm` entity speculatively (no consumer — would add structural drift).
- Do not duplicate chart-of-accounts / purpose data here — cross-link the owning feature.

## Approved deviations / notes

- `default_yn` computed (not stored) — APPROVED DEVIATION, matches legacy IDO `CASE WHEN`.
- Country audit columns `TIMESTAMPTZ` vs DEV `VARCHAR(14)` — documented project-wide deviation
  (`NatnInfm.java:16-18`).
- Per-screen country allow-list on the **frontend**, endpoint stays generic — intentional
  (matches legacy where the JSP filter was commented out).

## Related

- Hub: [`README.md`](README.md) · Functional: [`functional-specification.md`](functional-specification.md)
- Audit: [`../../../audits/common-gap-analysis.md`](../../../audits/common-gap-analysis.md)

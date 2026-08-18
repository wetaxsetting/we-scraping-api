# Daily Financial (HS) — Refactor Gap Analysis


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../../references/database/flyway-migration-baseline.md) §5.

> Per-feature Legacy-vs-Refactor audit. **PASS** is used only where the behaviour is verified in
> source and/or pinned by a test; **PASS (intentional divergence)** marks PO-approved deliberate
> differences. Historical gaps G1/G2 and parity items P1–P11 from
> [audits/daily-financial-hs-legacy-audit.md](../../../../audits/legacy-parity/daily-financial-hs-legacy-audit.md)
> are all closed by the [final audit](../../../../audits/legacy-parity/daily-financial-hs-final-audit.md); the
> table below is the current, per-surface state.

## 1. Gap table

| Feature | Legacy (`rept_0017_001`) | Refactor | Status | Gap | Risk | Resolution |
|---|---|---|---|---|---|---|
| Read via DB function | IDO `DAILY_FINCL_REPT_R001` → `FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT` | `DailyFinancialHsReportRepository.fetchReport` (single funnel) | **PASS** | none | low | — |
| Default load (today + ALL, eager) | TS page-load `fn_getData()` | mount fetch, `asOf=today`, `accountFilter=ALL` | **PASS** | none | low | — |
| Search-only refetch | auto-search commented out | `searchKey` bump on Search only | **PASS** | none | low | — |
| Bank/account multi-select | options from `cash_0001_001_r003` | options derived from `ALL` report HEADER rows | **PASS (intentional divergence)** | different option source (equivalent data) | low — an account with no ledger rows for the seed date would not appear as an option; *edge not verified from implementation* | accepted; revisit only if HAENGSUNG reports a missing account in the dropdown |
| Row grouping + SUB TOTAL + TOTAL | function row stream, currency-first IDO order | identical stream + mirrored ORDER BY; FE `projectHsRows` | **PASS** | none | low | pinned by fixes 8–11 + tests |
| Currency coverage | `CURRENCY IN ('VND','USD')` | VND/USD/KRW/any | **PASS (intentional divergence)** | refactor shows MORE than legacy | low — cross-system report comparison during coexistence will differ for KRW accounts | PO-approved 2026-07-02; documented in repository Javadoc |
| Per-field editable save (5 fields) | `rept_0017_001_u001` (`FIELD_ID`/`FIELD_VAL`) | `PATCH /entries/{acctSeqNo}` partial DTO | **PASS** | request shape differs (slug REST vs JexData) — behaviourally identical | low | — |
| `SUPP_RMRK` editability | entity setter exists; u001 **never dispatches it** (not editable on screen) | not in the DTO | **PASS** | none (legacy screen surface matched) | low | add only if a real legacy screen path is found |
| Customer/Supplier read-only | function-derived display | same (`customer_supplier`) | **PASS** | none | low | — |
| Due date / BRANCH header text | embedded in `BANK_ACCOUNT`, client splits on `"Due date:"` | same parse in FE `projectRows.ts` | **PASS** | none | low — HAENGSUNG seed accounts are all Current, so due-date rows are rare; *live rendering with a saving account: not verified from implementation* | verify when a saving account exists in data |
| Excel download | template-populate POI, saved DB data, 12 cols | same (classpath templates) | **PASS** | merge bound corrected vs legacy (`endRow-1` overlap) — refactor is stricter | low | regression-pinned |
| Excel titles | Korean A1 in ALL locales (templates byte-identical) | EN/VI localized, KO unchanged | **PASS (intentional divergence)** | cross-system title mismatch during coexistence | low–medium (user-visible if both systems used side-by-side) | raised in final audit §8.4 — align legacy separately if the business wants; do NOT revert refactor |
| View Setting | legacy `SYS_SCR_OPTION_WECMS` `rept_0017_001`/`table1` | shared screen-options API, slug/`default`, V249 manual seed | **PASS** | seed must be run per environment by hand | medium (operational) | deployment checklist step 2 in [migration-guide.md](migration-guide.md#3-deployment-checklist-per-environment); degraded-not-broken fallback until then |
| Sorting / pagination | none | none (`sort_yn='N'`, report page) | **PASS** | none | low | — |
| Authorization | JEX menu/product gating (H tree) | `@menuAuthz('banking-reports-daily-financial-hs')` ×3 + H-tree menu split | **PASS** | legacy WSVC-level flags (e.g. normal-user-only) *not re-verified from implementation* | low | covered by the refactor's own model (menu grant + product tree) |
| Audit logging | none documented | none (`MOD_ID`/`MOD_DTM` only on save) | **PASS** | none | low | — |
| Error codes | `WBE0404` on missing row | `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND` | **PASS** | code id differs by policy (never reuse JEX ML ids) | low | — |
| Frontend automated tests | n/a | **ABSENT** | **GAP (quality, not parity)** | no FE unit/E2E coverage for projection/save/download wiring | medium — regressions in `projectRows.ts` (grouping/totals mapping) would only surface visually | add FE tests when the project adopts a FE test harness; until then rely on the manual scenarios in [test-scenarios.md](test-scenarios.md) |
| `asOf` format guard | JEX validates required-ness only | no Java-side format guard (function raises → `WBE9999`) | **GAP (minor)** | malformed date yields 500 instead of a 4xx/validation envelope | low (internal tool; FE always sends `yyyyMMdd`) | optional hardening: `@Pattern` on the query param |
| DB function correctness | BUG-001: account-type `'02'` labeled as Loan (function CASE) | refactor calls **the same function** — whether the refactor DB's copy carries the fix is *not verified from implementation* | **OPEN (inherited)** | possible wrong account-type label in headers | low–medium | verify the function body on the shared dev DB against [BUG-001](../../../../../../docs/bug-reports/BUG-001_haengsung_daily_financial_wrong_account_type_label.md); fix is DB-side, cross-system |

## 2. Open items (current)

1. **Manual QA** (final audit §8): authenticated browser smoke (search → edit → persist →
   View Setting → download EN/KO/VI) and a human Excel open per locale.
2. **Per-environment V249 seed run** — operational prerequisite, idempotent.
3. **BUG-001 verification on the refactor DB copy of the function** (table row above).
4. **FE automated tests** — quality gap, tracked above.

No functional parity gaps remain — the final audit's verdict (**PASS — code-complete**) stands.

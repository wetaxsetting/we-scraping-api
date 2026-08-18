# Settings > Firm Banking Settings — Feature Summary (Refactor)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/firm-banking-settings/`

## Feature Overview
- **Purpose:** Per-tenant master list of firm-banking channel bindings. Each row binds a withdrawal account (`WDRL_ACT_NO`) to an outbound bank channel (`OUT_BANK_CD`, a NAPAS code) under a partner id + VAN org code, authorising WeCMS to dispatch firm-banking transfer instructions for that account/bank pair.
- **Business role:** A tenant administrator lists, views (with audit trail), creates, edits, and deletes firm-banking bindings.

## Menu / Route Mapping
- **Menu / panel:** Settings → Firm Banking → Firm Banking Settings (Settings modal panel — no standalone route)
- **Menu permission ID (slug):** `settings-firm-banking-settings` (class-level `@PreAuthorize("@menuAuthz.canAccess('settings-firm-banking-settings')")` on the controller)
- **Wire base path:** `/api/v1/settings/firm-banking-settings`
- **Frontend mount / route:** Settings modal panel (`FirmBankingSettingsPanel`)
- **Legacy JEX counterpart:** WSVC family `comm_0220_001` (list `_r001` / delete `_d001`), `comm_0220_002` (create `_c001`), `comm_0220_003` (detail+audit `_r001` / update `_u001`); table `TB_FIRM_CONF` — legacy ids cited for traceability only.

## Current Status
- ✅ **Implemented:** Full-stack. 6 endpoints — `GET` (list), `GET /{out_bank_cd}/{wdrl_act_no}` (detail), `GET /{out_bank_cd}/{wdrl_act_no}/audit` (audit), `POST` (create), `PUT /{out_bank_cd}/{wdrl_act_no}` (update), `DELETE /{out_bank_cd}/{wdrl_act_no}`. Create + Update write `AUDIT_LOGS_INFM` rows with `WSVC_ID = 'firm-banking-settings'` (slug). Delete is a **hard delete** and writes **no** audit row. List source is the Hibernate `@Subselect` entity `FirmBankingSettingView` (no `V_FIRM_BANKING_SETTING` DB view). Entities `TbFirmConf` / `TbFirmConfId` over `TB_FIRM_CONF`. `wdrl_act_no` ships **cleartext** on the list + detail wire (it is the PK component the frontend addresses for writes; display masking is frontend-only). Backed by unit + controller tests.
- 🟡 **Partially implemented:** Bank scope is provisionally bounded to `{970424 Shinhan, 970436 Vietcombank, 970457 Woori}` on Create (OQ-8a Option c — PO may widen). `V9` `NAPAS_BANK_INFO` referential check deferred (table not migrated locally; the 3-bank reject is strictly stronger). `wdrl_act_no` stays plain-text at rest (encryption is a deferred project-wide migration).
- ⚪ **Deprecated / Legacy / Not ported:** The two **Automation toggles** (Auto Match `00079` / Auto Transfer `00078`) on the frontend panel belong to **Environment Settings** (`BUSI_ENV_SET_INFM`), **not** this feature. `V11` `wdrl_act_no ∈ ACCT_MSTR` existence check stays disabled (legacy `#42004` parity). Approval-line integration absent on both sides.

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [firm-banking-settings/README.md](firm-banking-settings/README.md) |
| Business flow | [firm-banking-settings/business-flow.md](firm-banking-settings/business-flow.md) |
| Database mapping | [firm-banking-settings/database-mapping.md](firm-banking-settings/database-mapping.md) |
| Functional specification | [firm-banking-settings/functional-specification.md](firm-banking-settings/functional-specification.md) |
| Interface specification | [firm-banking-settings/interface-specification.md](firm-banking-settings/interface-specification.md) |
| Migration guide | [firm-banking-settings/migration-guide.md](firm-banking-settings/migration-guide.md) |
| Refactor gap analysis | [firm-banking-settings/refactor-gap-analysis.md](firm-banking-settings/refactor-gap-analysis.md) |
| Source code index | [firm-banking-settings/source-code-index.md](firm-banking-settings/source-code-index.md) |
| Test scenarios | [firm-banking-settings/test-scenarios.md](firm-banking-settings/test-scenarios.md) |

## Historical Design Notes (preserved)
Omitted — all unique design rationale (the OQ-1…OQ-8a decision log, the audit-DB-constant retirement, the `@Subselect` substitution, the bounded-bank provisional decision) is already preserved in the folder's [refactor-gap-analysis.md](firm-banking-settings/refactor-gap-analysis.md) "OQ decision log". The old flat narrative additionally carried stale facts now scrubbed: the entity was wrongly named `FirmBankingSetting`/`FirmBankingSettingId` (real names are `TbFirmConf`/`TbFirmConfId`); and references to a `V67` back-fill migration are stale — the refactor writes the slug from the start and no such migration ships.

# Firm Banking Settings

> Canonical Settings-menu entry for **Settings → Firm Banking → Firm Banking Settings**. Detailed
> design lives in the linked feature spec below.

## Overview
Firm Banking Settings is the per-tenant configuration of firm-banking (전자금융) registrations:
which withdrawal account is bound to which bank/service, with full list / detail / create / update /
delete and a per-row audit-log read. Slug: `firm-banking-settings`. **Fully implemented 2026-05-09**
(Path A). Mirrors live JEX `comm_0220_001/_002/_003` (+ API services `_r001/_d001/_c001/_r001/_u001`).

- Feature spec (full implementation references): [../features/settings/firm-banking-settings.md](../../features/settings/firm-banking-settings.md)
- DB schema: [../database/settings/firm-banking-settings-schema.md](../../references/database/settings/firm-banking-settings-schema.md)
- JEX → Spring mapping: [../migration/firm-banking-settings-mapping.md](../../references/migration/firm-banking-settings-mapping.md)
- Parity audit: [../audits/firm-banking-settings-parity-checklist.md](../../references/audits/firm-banking-settings-parity-checklist.md) ·
  [../audits/firm-banking-settings-review-2026-05-09.md](../../references/audits/firm-banking-settings-review-2026-05-09.md)

## Related APIs
Base URI `/api/v1/settings/firm-banking-settings` (JWT, snake_case wire): list / detail / create /
update / delete + audit-read. Writes go through the shared `AuditLogService`.
- `GET /api/v1/banking/accounts/by-bank` — withdrawal-account autocomplete (NAPAS→internal `BANK_CD`
  bridge; mirrors legacy `comm_1004_001`).

## Database Tables
- `FIRM_BANKING_SETTING` — write/detail source table (Flyway V62); `V_FIRM_BANKING_SETTINGS` list
  view (V64).
- `TB_FIRM_BANK_CODE` — referential check (`existsByAntDivCdAndBankCd`).
- `NAPAS_BANK_INFO` — NAPAS bank directory for the by-bank bridge (V68).
- `AUDIT_LOGS_INFM` — write-event audit rows (prerequisite V63); `WSVC_ID` stores the slug
  `firm-banking-settings` (audit-DB-constant bucket retired 2026-05-09).

## Business Rules
- Tenant-scoped by `BIZ_KEY`.
- Create implemented under **OQ-8a Option (c)** — provisional/bounded-bank set (Mr. Jung may
  override).
- Withdrawal account submitted as raw `acct_no`; displayed masked + currency-suffixed.
- Account numbers masked in logs via shared `LogMask.acct(...)`.

## Validation
- Five required wire fields on create (`FirmBankingSettingCreateRequest`); no `file_type`.
- Bank/account referential check against `TB_FIRM_BANK_CODE`.

## Error Cases
- `WBE0001`, `WBE0002`, `WBE2020`, `WBE2021` (firm-banking-specific messages) + `WBE_VALIDATION` /
  `WBE9999`.

## Notes
Frontend ships as a Settings modal panel (not a standalone route). Web/UI:
[../../../wecms-web/docs/settings/firm-banking-settings.md](../../../../wecms-web/docs/features/settings/firm-banking-settings.md).

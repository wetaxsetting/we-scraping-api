# Contacts

> Canonical Settings-menu entry for **Settings → Contacts**. Detailed design lives in the
> linked docs below.

## Overview
Contacts (trading partners, `TRCO_*`) is the per-tenant master of counterparties used as
sender/receiver in transfers and payments, including their addresses, bank accounts, and overseas
detail. Supports list, detail, create, update, delete. Slug: `contacts`. Mirrors live JEX
`comm_0184_*`.

- Backend design: [../business-rules/contacts-design.md](../../references/business-rules/contacts-design.md)
- DB schema: [../database/contacts-schema.md](../../references/database/contacts-schema.md)
- Feature spec: [../features/contacts/contacts.md](../../features/settings/contacts.md)
- JEX → Spring mapping: [../migration/contacts-mapping.md](../../references/migration/contacts-mapping.md) ·
  [../migration/api-mapping/contacts-mapping.md](../../references/migration/contacts-mapping.md)
- Task plan: [../planning/contacts-tasks.md](../../planning/phase-1/contacts-tasks.md)

## Related APIs
Base URI `/api/v1/settings/contacts` (JWT, snake_case wire): list / detail / create / update /
delete, with nested address + account + overseas sub-resources handled in the same aggregate.

## Database Tables
- `TRCO_INFM` — partner master (key includes `TRCO_KEY`)
- `TRCO_ADDR_INFM` — addresses
- `TRCO_ACCT_INFO` — partner bank accounts
- `TRCO_OVRS_INFM` — overseas detail
- `PGRO_BSAC_LNKN_INFM` — partner-group / business-account linkage (shared if already owned)
- `TRCO_KEY` allocated Java-side (`TrcoKeyGeneratorService`) — no `wb_get_next_val`.

## Business Rules
- Tenant-scoped by `BIZ_KEY`.
- Duplicate + business-rule checks in `ContactsValidator`.
- **In-use guard:** a partner referenced by transactions (`AcctTrnsRcvPayInfm.existsByBizKeyAndTrcoKey`)
  cannot be deleted.

## Validation
- Partner name + key fields required; account numbers format-checked.
- Sort fields restricted by `ContactSortField` whitelist.

## Error Cases
- Duplicate partner → validation error.
- Delete of in-use partner → blocked.
- Standard envelope; `WBE_VALIDATION` / `WBE9999`.

## Notes
Web/UI: [../../../wecms-web/docs/settings/contacts.md](../../../../wecms-web/docs/archive/settings-contacts.md).
An archived earlier draft exists at [../archive/features/contacts.md](../features/contacts.md).

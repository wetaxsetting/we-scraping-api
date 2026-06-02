# features/contacts/

Feature specs for the **contacts (거래처 / TRCO)** domain in the refactor.

## Purpose

Contacts manages tenant trading partners (`TRCO_INFM`) — the third-party accounts used by transfers, e-invoices, and AR/AP screens. The refactor replaces JEX `comm_*` contact services with a Spring REST service backed by the same `TRCO_*` tables.

## Scope

| File | Refactor scope |
|---|---|
| [contacts.md](../../features/settings/contacts.md) | Contact CRUD, `TRCO_KEY` generator, in-use guards, i18n key mapping |

## Document standards

- Frontmatter table: `JEX counterpart`, `Refactor status`, `Owner`, `Sprint`.
- Document the `TRCO_KEY` generator decision (Java service vs `wb_get_next_val` function) — see OQ-4.
- Cite legacy ML codes only as cross-reference; refactor i18n uses slug-namespaced keys.

## Naming convention

- kebab-case slug matching JEX counterpart.
- No spaces, no uppercase.

## Related

- Backend design: [`../../backend/contacts-design.md`](../../references/business-rules/contacts-design.md)
- Frontend design: [`../../frontend/contacts-design.md`](../../../../wecms-web/docs/features/settings/contacts.md)
- Schema: [`../../database/contacts-schema.md`](../../references/database/contacts-schema.md)
- Migration map: [`../../migration/api-mapping/contacts-mapping.md`](../../references/migration/contacts-mapping.md)
- Plan: [`../../planning/phase-1/contacts-tasks.md`](../../planning/phase-1/contacts-tasks.md)

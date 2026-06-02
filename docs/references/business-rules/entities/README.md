# backend/entities/

JPA entity definitions for `wecms-api`.

## Purpose

Per-entity documentation: field list, JPA annotations, lifecycle hooks, composite keys, soft-delete flag (`del_yn`), audit columns (`reg_*`, `mod_*`), and tenant column (`biz_key`).

## What goes here

- Per-entity markdown (`<entity-name>-entity.md` — e.g. `appr-line-infm.md`)
- Entity inheritance / mapped-superclass rules
- Audit & tenant-column conventions

## Naming convention

- kebab-case filenames mirroring the JEX entity name (lowercased + dashed). Example: `ApprLineInfm` → `appr-line-infm.md`.
- Stay close to the JEX table / entity name so JEX → refactor traceability is unambiguous.

## Related

- Schema design: [`../../database/`](../../database/)
- Repositories: [`../repositories/`](../repositories/)
- JEX → Spring field mapping: [`../../migration/`](../../migration/)

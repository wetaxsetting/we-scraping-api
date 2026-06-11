# database/

Schema design and migration notes for the refactor PostgreSQL DB.

## Purpose

Per-feature DB design and Flyway migration intent. The actual SQL files live in `wecms-api/src/main/resources/db/migration/V<N>__<topic>.sql` — this folder documents the **why** and the **shape**, not the SQL itself.

## What goes here

- Per-feature schema design (new tables, new columns, indexes, constraints)
- Migration intent notes (what `V<N>__<topic>.sql` does and why)
- Entity ↔ table mapping notes
- Naming, audit-column, and `BIZ_KEY` conventions specific to a feature
- Cross-feature DB indexes / `db-mapping.md` index

## Sub-folders

- [erd/](erd/) — **Entity-Relationship Diagrams** for the whole schema (80 tables). Start at [erd/full-project-erd.md](erd/full-project-erd.md) for the cross-module map, then the per-module docs ([banking](erd/banking-erd.md) · [payment](erd/payment-erd.md) · [addon-service](erd/addon-service-erd.md) · [settings](erd/settings-erd.md)). Mermaid sources live in [erd/diagrams/](erd/diagrams/).
- [flyway/](flyway/) — migration notes · [indexing/](indexing/) — index strategy · [table-mapping/](table-mapping/) — table inventory.

## Key reference docs

- [db-function-classification.md](db-function-classification.md) — inventory & migration-priority classification of all ~168 legacy PL/pgSQL functions (companion to the policy doc [../../architecture/database/db-function-strategy.md](../../architecture/database/db-function-strategy.md)).
- [legacy-table-inventory.md](legacy-table-inventory.md) — flat snapshot of all 141 tables physically present in the refactor DB (legacy-copied + refactor-owned infra), with the few non-legacy / out-of-scope tables flagged.
- [database-coverage-matrix.md](database-coverage-matrix.md) · [table-ownership.md](table-ownership.md) · [manual-tenant-setup.md](manual-tenant-setup.md)

## What does NOT go here

- Raw SQL Flyway files — those live in `wecms-api/src/main/resources/db/migration/`
- Parity audit reports — those go in [../audits/](../audits/)
- JEX → Spring field mapping — that goes in [../migration/](../migration/)
- JEX schema documentation — read-only at [../../jex/architecture/database-design.md](../../../../docs/jex/architecture/database-design.md) and [../../jex/architecture/ERD.md](../../../../docs/jex/architecture/ERD.md)

## Example file names

- `account-inquiry-vnd.md`
- `view-setting-tables.md`
- `V32-screen-option-cutover.md`
- `db-mapping.md` — cross-feature index of tables touched by the refactor

## Migration rules

- **Additive only** — never edit applied migrations
- Every new tenant-scoped table must include `BIZ_KEY` (multi-tenant isolation)
- Every table must include audit columns: `REG_ID`, `REG_DTM`, `MOD_ID`, `MOD_DTM`
- Mirror legacy JEX column names column-for-column (`@Table(name = "UPPER_SNAKE")`)

## Reference

JEX schema is the **read-only source of truth**. Refactor entities mirror JEX tables column-for-column unless a documented divergence is approved.

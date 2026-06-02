# architecture/database/

Cross-feature **database architecture** for the refactor: the legacy-schema parity stance, the
Flyway-owns-the-schema rule, and the DB-function / legacy-PL-pgSQL migration strategy.

## Contents

| Doc | Scope |
|---|---|
| [db-function-strategy.md](db-function-strategy.md) | Policy + plan for the ~165 legacy PL/pgSQL functions — keep-in-DB vs wrap vs reimplement-in-Java |

## What goes here

- Schema-wide architecture decisions (parity rule, `ddl-auto: validate`, Flyway-only channel)
- DB-function classification + migration priority
- Tenant-isolation at the data layer (`BIZ_KEY` scoping)

## What does NOT go here

- Per-table / per-feature schema → [references/database/](../../references/database/)
- Per-function inventory detail → [references/crystalline-honking-sky.md](../../references/crystalline-honking-sky.md) (DB-function classification & migration priority)
- Flyway migration list — see [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"Database (LOCAL)"

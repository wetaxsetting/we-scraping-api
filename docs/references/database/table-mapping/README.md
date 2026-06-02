# database/table-mapping/

JEX → refactor table mapping reference.

## Purpose

Per-table mapping notes: which JEX table the refactor uses, which columns are renamed / dropped / added, and how legacy audit / soft-delete columns translate to the refactor entity model.

## What goes here

- Per-table mapping markdown (`<table-name>-mapping.md`)
- Column-level diff: JEX column → refactor column, with type changes called out
- Drop / add columns flagged with Flyway version reference

## Related

- Feature-level migration mapping: [`../../migration/`](../../migration/)
- Flyway versions: [`../flyway/`](../flyway/)
- JEX schema reference: [`../../../jex/architecture/database.md`](../../../../../docs/jex/architecture/)

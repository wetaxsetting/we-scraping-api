# database/indexing/

Index design notes for the refactor database.

## Purpose

Document index choices per table: PK, composite UK, supporting indexes for hot queries, partial indexes (e.g. `WHERE del_yn = 'N'`), and the rationale for each.

## What goes here

- Per-table or per-feature index design (`<feature>-indexes.md`)
- Hot-query inventory (which screens drive which indexes)
- Index naming convention (`uk_*`, `ix_*`, `pk_*`)
- Tenant-isolation considerations (leading `biz_key` column in composite indexes)

## Related

- Schema: [`../`](../)
- Query analysis: [`../query-analysis/`](../query-analysis/)
